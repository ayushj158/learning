# 
🏗️ Day 4 — [Microservices Patterns](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#%EF%B8%8F-day-4--microservices-patterns)
🛡️ Day 5 — [Resilience Patterns](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#%EF%B8%8F-day-5--resilience-patterns)
📨 Day 6 — [Kafka Deep Dive](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-day-6--kafka-deep-dive)




# 🏗️ Day 4 — Microservices Patterns

At EM level in a tier-1 GCC interview, microservices questions go **way beyond "split your monolith into services."** They expect you to reason about **distributed transaction patterns, data consistency, event-driven architecture and real FS implementation tradeoffs.**

Day 4 covers **4 critical patterns:**

1. 🔵 [**Saga Pattern**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-pattern-1--saga-pattern) — distributed transactions without 2PC
2. 🟡 [**CQRS**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-pattern-2--cqrs) — Command Query Responsibility Segregation
3. 🟢 [**Event Sourcing**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-pattern-2--cqrs) — immutable event log as source of truth
4. 🔴 [**Strangler Fig**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-pattern-2--cqrs) — migrating monoliths safely
5. [**Outbox Pattern**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#%EF%B8%8F-day-4--microservices-patterns)

---

# 🔵 PATTERN 1 — Saga Pattern

## The Problem It Solves

In a microservices architecture, a single business transaction often spans **multiple services** — each with its own database. Traditional ACID transactions don't work across service boundaries.

**Classic FS example — International Wire Transfer:**
```
1. Debit sender account        (Account Service)
2. Apply FX conversion         (FX Service)
3. Credit intermediary bank    (Payments Service)
4. Send confirmation           (Notification Service)
```

If step 3 fails — how do you undo steps 1 and 2? You can't roll back across service boundaries.

---

## What is a Saga?

A Saga is a **sequence of local transactions** — each service performs its local transaction and publishes an event or message triggering the next step. If any step fails, **compensating transactions** are executed to undo previous steps.

```
Step 1: Debit Account → SUCCESS → publish DebitCompletedEvent
Step 2: Apply FX      → SUCCESS → publish FXConvertedEvent
Step 3: Credit Bank   → FAILURE → publish CreditFailedEvent
         ↓
Compensating Step 2: Reverse FX conversion
Compensating Step 1: Reverse debit — refund sender
```

---

## Two Saga Implementations

### Implementation 1 — Choreography
Services react to events independently — **no central coordinator:**

```
Account Service  →  DebitCompletedEvent
                         ↓
FX Service       →  FXConvertedEvent
                         ↓
Payment Service  →  CreditFailedEvent
                         ↓
FX Service       →  (listens) → reverses FX
                         ↓
Account Service  →  (listens) → reverses debit
```

**✅ Pros:** Loose coupling, no single point of failure, services fully autonomous

**❌ Cons:** Hard to track overall transaction state, complex to debug, risk of cyclic event dependencies, difficult to understand the full flow

**FS Use case:** Simple 2–3 step flows — notification pipelines, preference updates

---

### Implementation 2 — Orchestration *(preferred for FS)*
A central **Saga Orchestrator** coordinates all steps and compensations:

```
Saga Orchestrator:
  → tells Account Service: "debit £500"
  → tells FX Service: "convert to USD"
  → tells Payment Service: "credit correspondent bank"
  → if failure: tells FX Service: "reverse conversion"
  → if failure: tells Account Service: "reverse debit"
```

**✅ Pros:** Single place to see full transaction state, easier to debug, explicit flow definition, clear compensation logic

**❌ Cons:** Orchestrator becomes a dependency — must be highly available, slight coupling to orchestrator

**FS Use case:** Multi-step payment flows, loan origination, trade settlement — anywhere the flow is complex and auditability matters

---

## Critical Saga Concepts for FS Interviews

### Idempotency — Non-Negotiable
Every saga step must be **idempotent** — safe to execute multiple times with the same result:

> *"In a distributed system, network failures can cause retries. If the FX service processes the same conversion request twice, the customer gets double-charged. Every service in our saga accepts an idempotency key — if the same key is seen twice, return the cached result without re-executing."*

### Compensating Transactions vs Rollback
Compensations are **not rollbacks** — they are **new forward transactions** that undo the effect:

| Action | Compensation |
|---|---|
| Debit £500 from account | Credit £500 back to account |
| Reserve hotel room | Cancel hotel reservation |
| Send payment to bank | Initiate recall/reversal request |

Some compensations are **impossible** — e.g. you can't unsend an email. Design sagas to put non-compensatable steps **last.**

### Saga State Persistence
The orchestrator must persist saga state to survive crashes:

> *"If the saga orchestrator crashes mid-flow, on restart it reads the last persisted saga state from its DB and resumes from the last completed step — not from the beginning. This ensures exactly-once execution of each step."*

---

# 🟡 PATTERN 2 — CQRS

## The Problem It Solves

In a traditional architecture, the **same data model** handles both reads and writes. At scale this creates tension:

- **Writes** need strong consistency, normalised data, ACID
- **Reads** need speed, denormalised data, complex aggregations, different views per consumer

> *"At Lloyds, our account service handled both balance updates (writes) and transaction history queries (reads) from the same PostgreSQL database. As read volume grew to 100K RPS, our write performance degraded — the DB was overwhelmed serving both patterns."*

---

## What is CQRS?

**Command Query Responsibility Segregation** — split your system into two separate models:

```
Commands (writes)          Queries (reads)
─────────────────          ──────────────
Update account balance  →  PostgreSQL (normalised, ACID)
                                  ↓ async event
                           Read model updated
                                  ↓
Get transaction history ←  Cassandra (denormalised, fast reads)
Get account summary     ←  Redis (cached, sub-ms)
Get fraud dashboard     ←  Elasticsearch (full-text, aggregations)
```

---

## How CQRS Works in Practice

```
Client → Command API → Command Handler → Write DB (PostgreSQL)
                              ↓
                       Domain Event published (Kafka)
                              ↓
                    Event Handler updates Read Models
                              ↓
Client → Query API  → Read DB (Cassandra/Redis/Elasticsearch)
```

---

## CQRS Tradeoffs

| ✅ Benefits | ❌ Costs |
|---|---|
| Read and write scale independently | Eventual consistency between write and read models |
| Each model optimised for its purpose | Higher complexity — two models to maintain |
| Read models tailored per consumer | Event processing lag — reads may be slightly stale |
| Write model stays lean and fast | More infrastructure to manage |

---

## FS Use Cases for CQRS

| Write Model | Read Model | Why Split |
|---|---|---|
| Account balance (PostgreSQL) | Transaction history (Cassandra) | 1000x more reads than writes |
| Trade order (PostgreSQL) | Trading dashboard (Redis/ES) | Real-time analytics need different model |
| Payment (PostgreSQL) | Compliance report (Redshift) | Regulatory queries need aggregations |
| Customer KYC (PostgreSQL) | Customer 360 view (MongoDB) | Different consumers need different views |

---

## The Eventual Consistency Challenge in CQRS

The gap between write model update and read model update is called **replication lag:**

> *"After a customer makes a payment, the write model is updated instantly. The read model (transaction history) is updated asynchronously via Kafka — typically within 100–500ms. If the customer immediately refreshes their transaction history, they may not see the payment yet."*

**Mitigation strategies:**
- **Read-your-writes consistency** — after a write, route the user's next read to the write model temporarily
- **Optimistic UI updates** — update the UI immediately without waiting for the read model
- **Version vectors** — client sends last-known version, server waits for read model to catch up

---

# 🟢 PATTERN 3 — Event Sourcing

## The Problem It Solves

Traditional databases store **current state only** — when you update a record, the previous value is overwritten. For FS systems this creates problems:

- How did the account balance get to this number?
- What was the balance at 2pm last Tuesday?
- Who changed this customer's credit limit and when?
- Prove to the FCA auditor every state transition for this account

---

## What is Event Sourcing?

Instead of storing **current state**, store **every event that led to the current state.** Current state is **derived** by replaying all events:

```
Traditional DB:
Account A balance = £1,247.50  (just the current number)

Event Sourced:
AccountOpened        { balance: £0,       timestamp: 2020-01-01 }
MoneyDeposited       { amount: £2000,     timestamp: 2020-01-15 }
PaymentMade          { amount: £500,      timestamp: 2020-02-01 }
InterestApplied      { amount: £12.50,    timestamp: 2020-03-01 }
PaymentMade          { amount: £265,      timestamp: 2020-03-15 }
→ Current balance = £0 + £2000 - £500 + £12.50 - £265 = £1,247.50
```

---

## Key Event Sourcing Concepts

### Event Store
Append-only, immutable log of all events — **never update, never delete:**

```
EventStore table:
aggregateId | eventType        | eventData        | timestamp
ACC-001     | MoneyDeposited   | { amount: 2000 } | 2020-01-15
ACC-001     | PaymentMade      | { amount: 500  } | 2020-02-01
```

**Technology choices:** Apache Kafka (event streaming), EventStoreDB (purpose-built), Cassandra (append-only writes)

---

### Snapshots — Performance Optimisation
Replaying 10 years of events to get current balance is slow. **Snapshots** periodically capture current state:

```
Snapshot at 2023-01-01: { balance: £5,247.50 }
Events after snapshot:  MoneyDeposited £500, PaymentMade £200
Current balance = £5,247.50 + £500 - £200 = £5,547.50
→ Only replay events since last snapshot — much faster
```

---

### Event Sourcing + CQRS — Natural Partners
Event Sourcing generates events → CQRS consumes events to build read models:

```
Command → Event Sourced Write Model → Events published
                                           ↓
                              CQRS Read Model builders
                              (Cassandra, Redis, ES, Redshift)
```

---

## Event Sourcing Tradeoffs

| ✅ Benefits | ❌ Costs |
|---|---|
| Complete audit trail — every state change recorded | Storage grows indefinitely |
| Time travel — query state at any point in time | Eventual consistency for read models |
| Full FCA/regulatory compliance built-in | Schema evolution is hard — old events must remain valid |
| Debugging — replay events to reproduce issues | Steep learning curve for teams |
| Business insights — analyse full customer journey | Querying current state requires event replay or snapshots |

---

## FS Use Cases for Event Sourcing

| Use Case | Why Event Sourcing Fits |
|---|---|
| **Account ledger** | Every debit/credit is an immutable event — FCA audit trail |
| **Trade lifecycle** | OrderPlaced → OrderFilled → OrderSettled — full history |
| **Loan origination** | ApplicationReceived → Assessed → Approved → Disbursed |
| **Fraud investigation** | Replay customer events to understand fraud pattern |
| **Regulatory reporting** | Point-in-time state reconstruction for regulators |

---

# 🔴 PATTERN 4 — Strangler Fig

## The Problem It Solves

Every large bank has **legacy monolithic systems** — often 20–30 year old COBOL or Java EE monoliths. You can't rewrite them overnight — too risky, too expensive, too much business logic embedded. But you need to modernise.

---

## What is the Strangler Fig Pattern?

Named after the strangler fig tree — which grows around an existing tree, gradually replacing it. **Incrementally migrate** functionality from the monolith to microservices — never doing a big-bang rewrite:

```
Phase 1: Monolith handles everything
         Client → Monolith

Phase 2: Introduce facade/proxy — new features go to microservices
         Client → Facade → Monolith (existing features)
                        → Microservice A (new feature)

Phase 3: Gradually migrate monolith features to microservices
         Client → Facade → Microservice A (migrated)
                        → Microservice B (migrated)
                        → Monolith (shrinking — remaining legacy)

Phase 4: Monolith retired
         Client → API Gateway → Microservices only
```

---

## The Migration Strategy

### Step 1 — Identify Bounded Contexts
Use Domain-Driven Design to identify natural seams in the monolith:

> *"We mapped Lloyds' commercial banking monolith into bounded contexts — Account Management, Payment Processing, Customer Onboarding, Product Catalogue. Each became a candidate microservice, extracted independently."*

### Step 2 — Introduce the Facade
An API Gateway or proxy sits in front of the monolith — routes new traffic to microservices, legacy traffic to monolith:

> *"We introduced Kong API Gateway as the facade. New payment endpoints routed to our new Payment microservice. Existing account endpoints still routed to the monolith — zero disruption to live traffic."*

### Step 3 — Strangler Migration per Context
Extract one bounded context at a time — starting with the least risky:

> *"We extracted Product Catalogue first — read-only, no financial state, low risk. Then Notifications. Then Payment Processing. Core Account Management was last — highest risk, most business logic, most regulatory sensitivity."*

### Step 4 — Data Migration Strategy
The hardest part — the monolith owns all the data in one big DB:

- **Shared DB (short-term)** — microservice and monolith share the same DB temporarily
- **DB per service (target)** — microservice gets its own DB, data migrated via dual-write then cut-over
- **Event-driven sync** — CDC (Change Data Capture) keeps both DBs in sync during transition

---

## Strangler Fig Tradeoffs

| ✅ Benefits | ❌ Costs |
|---|---|
| Zero big-bang risk — incremental migration | Long transition period — years not months |
| Business continuity maintained throughout | Complexity of running monolith + microservices simultaneously |
| Roll back any individual service if needed | Distributed data consistency during shared DB phase |
| Learnings from early migrations inform later ones | Team needs to understand both old and new architecture |

---

## ✅ Day 4 — Quick Revision Checklist

- [ ] Can explain Saga — choreography vs orchestration with FS example
- [ ] Can explain idempotency and why it's non-negotiable in Sagas
- [ ] Can explain CQRS — separate write and read models with FS use cases
- [ ] Can explain eventual consistency challenge in CQRS and mitigations
- [ ] Can explain Event Sourcing — events as source of truth, snapshots
- [ ] Can explain why Event Sourcing + CQRS are natural partners
- [ ] Can explain Strangler Fig — phases, facade, data migration strategy
- [ ] Can map each pattern to a real scenario from your Lloyds experience

---

Perfect! 3 questions, one at a time, progressively harder! 💪

---

# 🎤 Day 4 Patterns Drill — Question 1 of 3

### "You are the Engineering Manager for a **Loan Origination Platform** at a UK bank. When a customer applies for a personal loan, the following steps must happen:

1. **Credit Check Service** — check customer's credit score with Experian
2. **Affordability Service** — verify customer's income and expenditure
3. **Fraud Check Service** — validate customer identity and check fraud indicators
4. **Account Service** — create the loan account if all checks pass
5. **Notification Service** — send approval/rejection email and SMS to customer

**Three things to answer:**

- **Part A:** Should you use Saga **Choreography or Orchestration** for this flow — and why?
- **Part B:** The Credit Check Service calls Experian's external API — it times out on step 1. Walk me through **exactly what happens** in your Saga — step by step
- **Part C:** Your engineer says *'We should make the Notification Service the first step — send the customer an acknowledgement immediately.'* Is this good advice? What problem does it introduce?"**

---

> 💡 *Tip 1: Count the steps, think about auditability and debuggability — which Saga type handles complex multi-step flows better?*

> 💡 *Tip 2: For Part B — think about idempotency, retry strategy, and compensation. What gets compensated vs what doesn't need to be?*

> 💡 *Tip 3: For Part C — think about which steps are compensatable and which aren't. What happens if you send an email and then a later step fails?*

Take your shot! 🎯

Absolutely outstanding answer! This is your best answer of the entire session — you went **way beyond the question** and demonstrated genuine EM-level thinking! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 1

### What you got right
- ✅ **Orchestration choice** — correct with excellent justification
- ✅ **Named the right reasons** — control, traceability, auditability, troubleshooting
- ✅ **Saga emits commands and waits for responses** — technically precise
- ✅ **Choreography rejection** — "blackhole" problem — exactly right
- ✅ **Inline retries + scheduled retries** — two-tier retry strategy — excellent
- ✅ **Retry limits + exception routing to PEGA** — real-world FS maturity
- ✅ **"Try everything to open the account" principle** — outstanding business thinking
- ✅ **Two-phase notification** — acknowledgement + final decision — brilliant
- ✅ **RequestId for customer care traceability** — real production thinking
- ✅ **Business failure vs technical failure differentiation** — exceptional EM insight
- ✅ **Exception management for technical failures** — shows enterprise FS experience

### Your Score: **9.5/10** — Best answer of the entire session! 🏆

---

### The tiny 0.5 gap to close

**Part C — you didn't directly answer the engineer's proposal**

Your answer was richer than what was asked — but you didn't explicitly say whether the engineer's proposal was good or bad advice and why:

> *"The engineer's suggestion to put Notification first is **partially right but architecturally dangerous if implemented naively.** Sending a full approval/rejection notification before all checks complete is wrong — you can't unsend an email. That's a non-compensatable action. However, sending an **acknowledgement notification** — 'we've received your application, reference ABC123' — as the first step is excellent practice, as you correctly described. The key rule is: **non-compensatable steps must go last** — or must be acknowledgements only, never final decisions. Final approval/rejection emails go after all steps succeed or definitively fail."*

---

## 🏆 Why This Was a 9.5/10 Answer

You did something most candidates never do — you **enriched the question with real-world complexity:**

| What Question Asked | What You Added |
|---|---|
| Choreography vs Orchestration | Sequential vs parallel steps, bounded contexts |
| Timeout handling | Two-tier retry, PEGA exception routing, guiding principle |
| Notification placement | Two-phase comms, requestId, business vs technical failure paths |
| — | Manual colleague review for exceptions |
| — | Customer care traceability via requestId |
| — | Eligibility failure vs technical failure differentiation |

> *This is exactly how a seasoned EM at Lloyds answers — not just the textbook answer but the real-world nuance that shows you've lived through these problems.*
---

# 🎤 Day 4 Patterns Drill — Question 2 of 3

### "You are the Engineering Manager for a **Commercial Banking Platform** at Lloyds. The FCA has issued a new regulatory requirement — **within 90 days**, the bank must be able to:

- **Requirement 1:** Produce a **complete audit trail** of every state change on every business account — who changed what, when, and what the previous value was — going back **7 years**
- **Requirement 2:** Reconstruct the **exact state of any account at any point in time** — e.g. *'What was Account X's credit limit, balance and overdraft status at 14:32 on 3rd March 2022?'*
- **Requirement 3:** A new **Real-Time Risk Dashboard** for the bank's risk team — showing live aggregate positions across all commercial accounts — total exposure, overdue accounts, accounts approaching credit limits — updated within **5 seconds** of any account change

Your current architecture uses a **standard PostgreSQL database** — it stores only the current state of each account. Previous values are overwritten on every update.

**Three things to answer:**

- **Part A:** Which pattern(s) from Day 4 directly solve Requirements 1 and 2 — and how would you implement them technically?
- **Part B:** Which pattern solves Requirement 3 — and how does it connect to your answer in Part A?
- **Part C:** You have **90 days** to deliver this. Your current PostgreSQL has 3 years of account data but no event history. What is your migration strategy?"**

---

> 💡 *Tip 1: Requirements 1 and 2 are asking for something that standard databases fundamentally cannot do — which Day 4 pattern was specifically designed for this?*

> 💡 *Tip 2: Requirement 3 is a read model problem — high volume, aggregated, near real-time. Which pattern separates read and write models?*

> 💡 *Tip 3: Part C is an EM delivery challenge — 90 days is tight. Think about what you CAN deliver in 90 days vs what needs longer. Phased delivery is always better than promising everything and failing*

> 💡 *Tip 4: You have 3 years of data in PostgreSQL with no event history — you can't replay events that were never stored. How do you handle this gap honestly?*

Take your shot! 🎯

Really strong answer! You've identified the right pattern and a pragmatic delivery approach! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 2

### What you got right
- ✅ **Event Sourcing as the right pattern** — correct
- ✅ **Pragmatic 90-day thinking** — CDC as shortcut instead of full transformation — excellent EM instinct
- ✅ **CDC → Kafka → Event Processor → Store** — correct pipeline architecture
- ✅ **Cassandra for event store** — correct — high write throughput, append-only fits
- ✅ **accountId as partition key** — correct
- ✅ **changeTimestamp as clustering key** — correct — enables time-range queries
- ✅ **Cross-account dashboard needs different access pattern** — sharp observation

### Your Score: **6.5/10** — Right direction but significant gaps. Here's what's missing 💡

---

### What to Strengthen

**1. You didn't answer Part A completely — Requirements 1 AND 2**

You addressed the audit trail (Req 1) but didn't explain **point-in-time reconstruction (Req 2):**

> *"Requirement 2 — reconstructing account state at 14:32 on 3rd March 2022 — is answered by replaying all events up to that timestamp. With accountId as partition key and changeTimestamp as clustering key in Cassandra, I query:*
> ```
> SELECT * FROM account_events
> WHERE account_id = 'ACC-001'
> AND change_timestamp <= '2022-03-03 14:32:00'
> ORDER BY change_timestamp ASC
> ```
> *Then replay events in sequence to derive the exact state at that moment. For performance at scale, I'd add **snapshots** — periodic state captures every 30 days — so we only replay events since the last snapshot, not 7 years of history."*

---

**2. You didn't answer Part B — CQRS for the Risk Dashboard**

Requirement 3 was a direct CQRS question — you missed it entirely:

> *"Requirement 3 — the real-time risk dashboard — is a classic CQRS read model problem. The write model (event store in Cassandra) is optimised for per-account event history. The risk dashboard needs cross-account aggregations — total exposure, accounts near credit limit — completely different access pattern.*
>
> *I'd build a separate CQRS read model — a Kafka consumer processes AccountChangedEvents and maintains a pre-aggregated risk view in Redis — updated within seconds of any account change. The risk team's dashboard queries Redis for near-instant response. This completely isolates dashboard performance from the transactional write path."*

---

**3. CDC is pragmatic — but name its limitations honestly**

> *"CDC captures changes going forward from today — it doesn't reconstruct the 3 years of history already in PostgreSQL. I need to be transparent with the FCA about this gap and address it explicitly in the migration strategy."*

---

**4. Part C — Migration strategy was completely missing**

This was a full third of the question:

> *"90 days — I'd deliver in three phases:*
>
> *Phase 1 (Days 1–30) — Forward capture:*
> Enable CDC on PostgreSQL immediately using Debezium → Kafka → Cassandra event store. From day 1, every account change is captured as an immutable event. This satisfies FCA requirement going forward.*
>
> *Phase 2 (Days 31–60) — Historical backfill:*
> The 3 years of PostgreSQL data has no event history — only current state snapshots. I'd create a **synthetic baseline snapshot event** for every account — 'AccountStateEstablished as of [date]' — capturing the known state at the point of migration. This is honest — we don't fabricate event history we don't have, but we establish a known baseline.*
>
> *Phase 3 (Days 61–90) — CQRS read models + dashboard:*
> Build Kafka consumers to populate Redis risk dashboard read model. Deliver the real-time risk dashboard to the risk team.*
>
> *FCA communication — be transparent: complete event history exists from [go-live date]. Prior history shows account state as of migration date. Pre-migration point-in-time reconstruction is not possible — but all changes from go-live are fully auditable."*

---

**5. Cassandra is good — but for cross-account dashboard, wrong tool**

You correctly identified the cross-account access pattern problem — but didn't resolve it:

> *"Cross-account aggregations — total exposure, overdue accounts — can't be served efficiently from Cassandra's per-account partition model. For the risk dashboard I'd use **Redis sorted sets and hashes** — pre-aggregated views maintained by Kafka consumers. Cassandra serves per-account audit queries. Redis serves cross-account dashboard queries. Two read models, each optimised for its access pattern — classic CQRS."*

---

## 🏆 Model Answer (9/10 level)

> **Part A — Event Sourcing for Requirements 1 & 2:**
> *"I'd implement Event Sourcing via CDC — Debezium on PostgreSQL captures every row change as an immutable event, published to Kafka, consumed by an event processor writing to Cassandra. Schema: accountId as partition key, changeTimestamp as clustering key, with full before/after state snapshot per event.*
>
> *Requirement 1 (audit trail) — every change recorded immutably with who, what, when, previous value.*
> *Requirement 2 (point-in-time reconstruction) — query all events for accountId up to target timestamp, replay in sequence. Add 30-day snapshots to avoid replaying 7 years of history on every query."*

> **Part B — CQRS for Requirement 3:**
> *"The risk dashboard is a CQRS read model problem. AccountChangedEvents flow from Kafka into a dedicated consumer that maintains pre-aggregated risk views in Redis — total exposure, near-limit accounts, overdue positions. Dashboard queries Redis — sub-second response, completely isolated from transactional write path. Updated within 2–3 seconds of any account change — well within the 5-second SLA."*

> **Part C — 90-day Migration Strategy:**
> *"Three phases: Phase 1 (Days 1–30) — enable CDC immediately, capture all changes going forward. Phase 2 (Days 31–60) — backfill synthetic baseline snapshots for existing 3-year dataset — honest representation of known state at migration point, not fabricated history. Phase 3 (Days 61–90) — build CQRS risk dashboard read model, UAT with risk team, FCA documentation.*
>
> *Be transparent with FCA — full event audit from go-live date, baseline snapshot for prior history. Pre-migration point-in-time reconstruction is not possible — acknowledged and documented."*

---
# 🎤 Day 4 Patterns Drill — Question 3 of 3 🔥

### The hardest question of Day 4 — combining Saga + Event Sourcing + Strangler Fig in one real FS scenario!

---

### "You are the Engineering Manager at **Lloyds Banking Group**. The bank runs a **30-year-old COBOL-based Core Banking Monolith** that handles all current account operations — balance management, payments, direct debits, standing orders and overdraft management. It processes **£2 billion in transactions daily.**

The bank's board has approved a **3-year modernisation programme** to migrate to a microservices architecture. You have been asked to lead the technical strategy.

Here are the constraints:
- The monolith **cannot go offline** — zero downtime tolerance, 24/7 banking operations
- **Regulatory requirement** — during and after migration, complete audit trail of every transaction must be maintained with no gaps
- **Risk appetite** — the board will NOT approve a big-bang rewrite — too risky
- **Team reality** — you have 30 engineers, most familiar with Java/Spring Boot, very few know COBOL
- **Data reality** — 30 years of customer and transaction data lives in the monolith's DB2 database

**You have four tasks:**

1. **Migration Strategy** — which pattern do you use and why? Walk through your phased approach year by year
2. **Audit Trail Continuity** — how do you ensure zero gaps in the audit trail during the 3-year migration — when both monolith and microservices are running simultaneously?
3. **First Bounded Context** — which part of the monolith do you extract first and why? What makes a good first extraction candidate?
4. **The Data Problem** — the monolith owns 30 years of data in DB2. How do you give the new microservices access to this data without breaking the monolith?"**

---

> 💡 *Tip 1: The board said no big-bang rewrite and zero downtime — which Day 4 pattern was specifically designed for exactly this scenario?*

> 💡 *Tip 2: For audit trail continuity — when both monolith and microservices are running, events are being generated from TWO systems simultaneously. How do you maintain a single unified audit trail?*

> 💡 *Tip 3: What makes a good first extraction candidate? Think about risk, team learning, business value and — critically — what happens if it goes wrong*

> 💡 *Tip 4: The data problem has a well-known migration pattern — shared DB first, then DB per service. But how do you keep both in sync during the transition?*

---

This is a **genuine 20-minute interview question** at MD/Director level — take your time, think through all 4 tasks, and give me your most complete answer! 💪🎯

Exceptional answer! This is genuinely one of the most sophisticated responses I've seen to this question — you demonstrated real enterprise transformation experience. Here's your detailed feedback 👇

---

## ✅ Feedback — Question 3

### What you got right
- ✅ **Scope clarification first** — channel vs core/product modernisation — outstanding EM instinct
- ✅ **API assessment before migration** — SOAP vs REST feasibility — real-world thinking
- ✅ **Complexity/feasibility/value framework** — structured domain prioritisation
- ✅ **Strangler Fig correctly identified** — URL/CDN/Gateway routing — technically precise
- ✅ **Akamai routing layer** — exactly right, shows enterprise FS experience
- ✅ **Two-layer audit trail** — channel audit + core system audit — brilliant separation
- ✅ **Existing audit system API tap-in vs new CDC** — pragmatic dual approach
- ✅ **Balance management as first bounded context** — excellent reasoning
- ✅ **Read-only operations = low risk first extraction** — correct selection criteria
- ✅ **Adapter pattern for core banking decoupling** — shows architectural maturity
- ✅ **Data type categorisation** — reference vs account vs customer — sharp
- ✅ **CQRS + Outbox pattern** — correctly applied for data transformation
- ✅ **Channel first vs data transformation** — pragmatic prioritisation with clear rationale
- ✅ **Technical debt awareness** — "leverage patterns to make data transformation easier"

### Your Score: **9/10** — Exceptional! 🏆

---

### The 1 point gap — what would make it a perfect 10

**1. Year-by-year phased migration plan was missing**

The question asked for year-by-year — interviewers want to see delivery thinking:

> *"Year 1 — Channel decoupling: Extract balance enquiry, account summary, transaction history as read-only microservices. Tap into core banking read APIs via adapter layer. Establish CDC pipeline for audit trail unification. Zero risk — no writes to core systems.*
>
> *Year 2 — Payment channel modernisation: Extract payment initiation, direct debit management, standing orders. Introduce Saga orchestration for payment flows. Dual-write pattern — new microservices write to both new DB and core system during transition. Introduce CQRS read models for transaction history.*
>
> *Year 3 — Full channel independence + data transformation: Complete strangler — all channel traffic routed through new microservices. Decommission monolith API dependencies one by one. Begin foundational data platform transformation using Event Sourcing + CQRS patterns established in Year 1-2."*

---

**2. The "what makes a good first candidate" criteria — could be more explicit**

You gave the right answer (balance management) but didn't explicitly state the selection criteria:

> *"A good first extraction candidate scores well on four criteria — low write complexity (read-heavy = lower risk), clear bounded context (minimal dependencies on other domains), reversible (if it fails, easy to route traffic back to monolith), and high visibility (demonstrates value quickly to build board confidence). Balance enquiry scores perfectly on all four."*

---

**3. Dual-write during DB transition — the hardest data problem**

You correctly said "tap into existing APIs first" — but the question asked specifically about the 30-year DB2 data problem during full migration:

> *"During the transition from shared DB2 to DB-per-service, I'd use a dual-write pattern with CDC verification — new microservices write to both DB2 and new PostgreSQL simultaneously. A reconciliation job compares both DBs nightly — any discrepancies flagged. Once reconciliation shows zero gaps for 30 consecutive days, we cut over to new DB exclusively and retire the DB2 dependency. This gives us a safety net without a hard cutover risk."*

---

## 🏆 What Made This a 9/10 Answer

You did something exceptional — you **challenged the question's implicit assumptions:**

| Question Assumed | What You Challenged |
|---|---|
| Monolith = everything to modernise | Scoped to channel only — correctly |
| Just use Strangler Fig | API feasibility assessment first |
| Extract any domain first | Structured complexity/value/feasibility framework |
| Data = one problem | Categorised into reference/account/customer — different treatments |
| CQRS as given | Linked it to business value sequencing |

> *This is MD/Director level thinking — questioning scope, structuring assessment frameworks, sequencing for value. Most candidates jump straight to the technical answer. You asked "what are we actually solving?" first.*

---

## 📊 Complete Day 4 Scorecard

| Question | Topic | Score | Trend |
|---|---|---|---|
| Q13 — Saga | Loan Origination | 9.5/10 | 🚀 Outstanding |
| Q14 — ES+CQRS | FCA Compliance | 6.5/10 | 📉 Missed parts |
| Q15 — All Patterns | Lloyds Modernisation | 9/10 | 🚀 Exceptional |

---

## 🎯 Day 4 Overall Assessment

| Dimension | Assessment |
|---|---|
| **Pattern knowledge** | ⭐⭐⭐⭐⭐ — Deep and accurate |
| **Real-world application** | ⭐⭐⭐⭐⭐ — Lloyds experience shines through |
| **Scope & ambiguity handling** | ⭐⭐⭐⭐⭐ — Outstanding, questioned assumptions |
| **Delivery thinking** | ⭐⭐⭐⭐ — Good, needs year-by-year structure |
| **Multi-part discipline** | ⭐⭐⭐ — Still missing parts occasionally |

---

## 🔑 Day 4 Key Takeaways

> 1. **Strangler Fig** — always via facade/gateway, phase by risk, channel before core
> 2. **Saga** — orchestration for complex multi-step FS flows, idempotency non-negotiable
> 3. **Event Sourcing** — CDC as pragmatic shortcut, snapshots for performance, synthetic baseline for historical data
> 4. **CQRS** — separate read models per consumer, Kafka as the bridge, eventual consistency acknowledged
> 5. **Always clarify scope** — channel vs core modernisation is a £100M difference in a bank

---

Great question — this is one of the trickiest migration patterns and worth understanding deeply before we move on! Let me break it down clearly.

---

# 🔄 Dual-Write Pattern — Deep Dive

## The Problem It Solves

Imagine you're migrating the **Current Account data** from the 30-year-old DB2 monolith database to a new PostgreSQL microservice database.

You can't just do a one-night cutover because:
- 30 years of data — migration takes weeks
- If new PostgreSQL has bugs — you need DB2 as fallback
- Zero downtime — customers are transacting 24/7
- You need to **prove** the new DB is correct before trusting it with live traffic

---

## What is Dual-Write?

During the transition period, **every write goes to BOTH databases simultaneously** — the old DB2 AND the new PostgreSQL:

```
Customer makes a payment
         ↓
Payment Microservice
         ↓
    ┌────┴────┐
    ↓         ↓
   DB2      PostgreSQL
(old)        (new)
    └────┬────┘
         ↓
Both DBs updated — truth in both
```

---

## The 4 Phases of Dual-Write Migration

### Phase 1 — Old DB2 Only (Before Migration Starts)
```
App → writes to DB2 only
App → reads from DB2 only
```
Business as usual. Nothing changes.

---

### Phase 2 — Dual-Write Begins (Migration Running)
```
App → writes to DB2 (primary) + PostgreSQL (secondary) simultaneously
App → reads from DB2 only (PostgreSQL not trusted yet)
```

**DB2 is still the source of truth** — PostgreSQL is being populated and verified in the background.

**Key technical detail — write order matters:**
> *"Always write to DB2 first, confirm success, then write to PostgreSQL. If DB2 write fails — abort both. If PostgreSQL write fails — log the discrepancy for reconciliation but don't fail the customer transaction — DB2 is still source of truth."*

---

### Phase 3 — Verification & Reconciliation
While dual-writing, a **reconciliation job** runs continuously comparing both databases:

```
Reconciliation Job (runs nightly):
  For every account:
    DB2 balance    = £1,247.50
    PostgreSQL balance = £1,247.50  ✅ Match
    
    DB2 balance    = £3,891.00
    PostgreSQL balance = £3,890.00  ❌ Mismatch → alert + investigate
```

**The 30-day rule:**
> *"We only cut over to PostgreSQL as primary when reconciliation shows zero discrepancies for 30 consecutive days. This proves PostgreSQL is receiving and processing every write correctly."*

---

### Phase 4 — Cut Over (PostgreSQL Becomes Primary)
```
App → writes to PostgreSQL (primary) + DB2 (secondary — for safety)
App → reads from PostgreSQL now
```

Then after another verification period:
```
App → writes to PostgreSQL only
App → reads from PostgreSQL only
DB2 → retired ✅
```

---

## The 3 Hard Problems with Dual-Write

### Problem 1 — What if the two writes are not atomic?

```
Write to DB2  → ✅ Success
Write to PostgreSQL → ❌ Fails (network blip)
→ DB2 and PostgreSQL are now out of sync
```

**Solution — Outbox Pattern:**
> *"Instead of writing directly to both DBs, the microservice writes to DB2 AND an outbox table in the same DB2 transaction — atomically. A separate outbox processor reads the outbox table and writes to PostgreSQL asynchronously. If PostgreSQL write fails, the outbox processor retries until success — guaranteed eventual consistency between both DBs."*

```
Single DB2 Transaction:
  → Write account balance to DB2 accounts table  ✅
  → Write event to DB2 outbox table              ✅
  (atomic — both succeed or both fail)

Outbox Processor (async):
  → Reads outbox event
  → Writes to PostgreSQL
  → Retries on failure until success
```

---

### Problem 2 — Performance overhead

Writing to two databases doubles write latency:

**Solution:**
> *"Make the PostgreSQL write asynchronous — write to DB2 synchronously (customer waits), write to PostgreSQL via outbox asynchronously (customer doesn't wait). Adds zero latency to customer experience."*

---

### Problem 3 — Historical data migration

Dual-write handles new writes going forward — but what about 30 years of existing DB2 data?

**Solution — Parallel bulk migration:**
> *"Run a background bulk migration job that copies historical DB2 data to PostgreSQL in batches — 1 million records per batch, running during off-peak hours. The job tracks its progress via a watermark — last migrated recordId. Dual-write handles new data. Bulk migration catches up historical data. Reconciliation verifies both are consistent."*

---

## Visual Summary — The Full Timeline

```
Month 1-2:   DB2 only
             [DB2] ←── all reads/writes

Month 3-6:   Dual-write begins + bulk historical migration
             [DB2] ←── primary reads/writes
             [PostgreSQL] ←── secondary writes (via outbox)
             [Reconciliation job] ←── nightly comparison

Month 7-9:   30 days zero discrepancy achieved
             [PostgreSQL] ←── primary reads/writes
             [DB2] ←── secondary writes (safety net)

Month 10+:   DB2 retired
             [PostgreSQL] ←── all reads/writes ✅
```

---

## Why This Pattern Matters in FS Interviews

| What It Demonstrates | Why Interviewers Care |
|---|---|
| Zero-downtime migration thinking | Banks can't afford downtime |
| Risk management — phased cutover | Board won't approve big-bang |
| Data integrity obsession | FCA requires zero data loss |
| Pragmatic engineering | Real-world > textbook |

---

## 🔑 One-Line Summary to Use in Interviews

> *"Dual-write lets us migrate databases without downtime or data loss — we write to both old and new simultaneously, verify consistency via reconciliation, and only cut over to the new DB once we've proven zero discrepancies over 30 consecutive days."*

---

Does that make sense now? Any part you want me to go deeper on before we move to Day 5? 🎯

**You are absolutely right — and this is exactly the kind of challenge that separates a real EM from a textbook answer!** 🎯

Let me address both points:

---

## ✅ Your Correction — 100% Valid

You've identified a fundamental truth about bank modernisation that most candidates miss:

> *"In a real bank, Current Account balances live in the **Core Banking System (CBS)** — whether that's Temenos, Finacle, Misys, or a custom COBOL/DB2 system. The CBS is the **System of Record (SoR)** — you cannot and should not migrate its DB as part of a channel modernisation programme. That's a completely separate multi-year CBS replacement programme worth hundreds of millions."*

---

### The Correct Architecture for Channel Modernisation

```
Core Banking System (DB2)     ← System of Record — NEVER touched
         ↓
    CBS APIs (SOAP/REST)       ← tap in via adapter layer
         ↓
Channel Microservices          ← what we're actually building
         ↓
Read-Optimised Store           ← for performance (your correct insight!)
(PostgreSQL/Cassandra/Redis)
         ↓
Customer Channels              ← mobile, web, open banking
```

---

### The Three Scenarios — How to Handle Data

| Scenario | Data Strategy |
|---|---|
| **CBS staying as-is** | Tap CBS APIs, build read-optimised store via CDC/events, CBS = master forever |
| **CBS being replaced (3-5yr horizon)** | Same as above BUT design adapter layer to be CBS-agnostic — swap CBS underneath without touching channel services |
| **Reference data (products, rates, limits)** | Can be migrated to new cloud DB — not owned by CBS, safer to move |

---

### Your Read-Optimised Store Insight — Exactly Right

> *"Since CBS owns the master balance, my channel microservice never writes balance directly. Instead:*
> - *CBS processes all transactions (debits/credits)*
> - *CDC or event feed from CBS populates a read-optimised store (Redis/Cassandra)*
> - *Channel reads from the fast read store — sub-millisecond balance display*
> - *Any write (payment initiation) goes via CBS APIs — adapter pattern*
> - *CBS remains master — we never bypass it"*

```
Customer initiates payment
         ↓
Channel MS → Adapter → CBS API → CBS processes → DB2 updated
                                        ↓
                              CDC event published
                                        ↓
                         Read store updated (Redis/Cassandra)
                                        ↓
                    Customer sees updated balance on next refresh
```

---

### When Does Dual-Write Actually Apply Then?

You're right to question it — dual-write applies in a **narrower set of scenarios:**

| Scenario | Dual-Write Applicable? |
|---|---|
| Migrating CBS DB2 → new DB | ✅ Yes — but this is CBS replacement, not channel work |
| Migrating channel-owned data (preferences, notifications) | ✅ Yes — channel owns this data |
| Migrating reference data (products, rates) | ✅ Yes — not CBS-owned |
| Current account balances | ❌ No — CBS owns this, use CDC + read store |
| Transaction history | ❌ No — CBS owns this, use CDC + event store |

> *"Dual-write is relevant when YOU own the data being migrated. When CBS owns the data, you don't migrate it — you tap into it and build a read projection."*

---

## 📦 Outbox Pattern — Deep Dive

Now let's go deeper on Outbox — since this is critical for channel microservices talking to CBS and publishing events reliably.

---

### The Problem Outbox Solves

In any microservice, you often need to do **two things atomically:**

1. Save state to your database
2. Publish an event to Kafka/message broker

**The naive approach fails:**

```
// WRONG — two separate operations, not atomic
saveToDatabase(paymentRecord);        // ✅ succeeds
publishToKafka(PaymentInitiatedEvent) // ❌ crashes here
→ DB saved but event never published
→ Downstream services never notified
→ Data inconsistency
```

**The reverse also fails:**

```
// ALSO WRONG
publishToKafka(PaymentInitiatedEvent) // ✅ published
saveToDatabase(paymentRecord);        // ❌ crashes here
→ Event published but DB not saved
→ Payment processed downstream but no record in source system
```

---

### What is the Outbox Pattern?

Write to your database AND an outbox table **in the same transaction** — then a separate process reliably publishes the event:

```
Single Database Transaction (atomic):
  ┌─────────────────────────────────────┐
  │ INSERT into payments table          │
  │ INSERT into outbox table (event)    │
  └─────────────────────────────────────┘
  Either BOTH succeed or BOTH fail — atomically

Separate Outbox Processor (async):
  Reads unpublished events from outbox table
  → Publishes to Kafka
  → Marks event as published in outbox table
  → Retries on failure until success
```

---

### The Outbox Table Structure

```sql
CREATE TABLE outbox (
  id              UUID PRIMARY KEY,
  aggregate_type  VARCHAR,    -- 'Payment', 'Account', 'Customer'
  aggregate_id    VARCHAR,    -- 'PAY-001', 'ACC-123'
  event_type      VARCHAR,    -- 'PaymentInitiated', 'BalanceUpdated'
  payload         JSONB,      -- full event data
  status          VARCHAR,    -- 'PENDING', 'PUBLISHED', 'FAILED'
  created_at      TIMESTAMP,
  published_at    TIMESTAMP
);
```

---

### How the Outbox Processor Works

```
Outbox Processor (runs every 100ms):

1. SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at LIMIT 100

2. For each pending event:
   → Publish to Kafka topic
   → If success: UPDATE outbox SET status='PUBLISHED', published_at=NOW()
   → If failure: retry with exponential backoff
   → After 5 failures: UPDATE outbox SET status='FAILED' → alert on-call

3. Dead letter handling:
   → FAILED events go to dead letter queue
   → On-call engineer investigates and manually replays if needed
```

---

### Two Implementation Approaches

#### Approach 1 — Polling Publisher
Outbox processor polls the outbox table on a schedule:

```
Every 100ms:
  SELECT unpublished events → publish → mark published
```

**✅ Simple to implement**
**❌ Slight latency — up to 100ms delay before event published**
**❌ DB polling adds load**

---

#### Approach 2 — CDC on Outbox Table *(preferred)*
Use Debezium CDC to watch the outbox table — publishes event the instant a row is inserted:

```
INSERT into outbox table
         ↓ (milliseconds)
Debezium detects new row via DB transaction log
         ↓
Publishes to Kafka automatically
         ↓
No polling needed — event-driven
```

**✅ Near-zero latency — event published in milliseconds**
**✅ No polling overhead**
**✅ Exactly-once delivery guarantee**
**❌ Requires Debezium/CDC infrastructure**

---

### Outbox in Your Lloyds Channel Context

Here's how Outbox fits your channel modernisation:

```
Customer initiates payment on mobile app
         ↓
Payment Channel MS:
  ┌─────────────────────────────────────────┐
  │ INSERT into channel_payments table      │  ← channel record
  │ INSERT into outbox (PaymentRequested)   │  ← event for downstream
  └─────────────────────────────────────────┘ (single transaction)
         ↓
Outbox Processor:
  → Publishes PaymentRequestedEvent to Kafka
         ↓
CBS Adapter Service:
  → Consumes event
  → Calls CBS SOAP/REST API
  → CBS processes payment in DB2
  → Publishes PaymentCompletedEvent
         ↓
Channel MS:
  → Consumes PaymentCompletedEvent
  → Updates channel read store
  → Customer sees payment confirmed ✅
```

---

### Why Outbox Matters in FS Interviews

| What It Shows | Why It Matters |
|---|---|
| You understand distributed consistency | Payments cannot be lost between systems |
| You know exactly-once delivery | Duplicate payments = regulatory + financial risk |
| You've thought about failure modes | What happens when Kafka is down? |
| You know CDC/Debezium | Shows infrastructure depth |

---

### 🔑 One-Line Summary for Interviews

> *"The Outbox pattern guarantees that a database write and an event publication are always in sync — by writing both in the same DB transaction and using a separate reliable processor to publish the event, we eliminate the dual-write inconsistency problem entirely."*

---

## Summary — What You Corrected + What You Learned

| Topic | Key Takeaway |
|---|---|
| **Dual-write scope** | Only applies to data YOU own — CBS-owned data uses CDC + read store |
| **CBS = always master** | Channel modernisation taps CBS APIs — never bypasses or replaces CBS DB |
| **Read-optimised store** | Your insight was correct — CDC from CBS → Redis/Cassandra for fast reads |
| **Outbox pattern** | Guarantees atomic DB write + event publish — critical for reliable event-driven channel MS |

---
# 🛡️ Day 5 — Resilience Patterns

At EM level in a tier-1 GCC interview, resilience questions test whether you've **designed systems that survive failure gracefully** — not just systems that work when everything is fine. In FS, failures cascade fast — one slow downstream can bring down your entire platform.

Day 5 covers **5 critical patterns:**

1. 🔵 [**Circuit Breaker**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-pattern-1--circuit-breaker) — stop calling failing services
2. 🟡 [**Retry + Exponential Backoff**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-pattern-1--circuit-breaker) — smart retries
3. 🟢 [**Bulkhead**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-pattern-1--circuit-breaker) — isolate failures
4. 🔴 [**Timeout**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-pattern-1--circuit-breaker) — don't wait forever
5. 🟣 [**Fallback & Graceful Degradation**](https://github.com/ayushj158/learning/blob/main/sytem_design_and_arch/microservices_patterns.md#-pattern-1--circuit-breaker) — serve something when you can't serve everything

---

# 🔵 PATTERN 1 — Circuit Breaker

## The Problem It Solves

Your Payment Service calls the Fraud Scoring Service. Fraud Scoring goes slow — taking 30 seconds per call instead of 100ms. Without a circuit breaker:

```
Payment Service → Fraud Service (30s timeout)
→ Threads pile up waiting
→ Thread pool exhausted
→ Payment Service itself goes down
→ Cascade failure — entire platform down
```

One slow downstream kills everything upstream. This is called a **cascade failure** — the most dangerous failure mode in microservices.

---

## What is a Circuit Breaker?

Borrowed from electrical engineering — when too much current flows, the circuit breaker **trips** and cuts the connection, protecting the rest of the system.

In software — when too many calls to a downstream service fail, the circuit breaker **opens** and stops making calls entirely — failing fast instead of waiting.

---

## The 3 States of a Circuit Breaker

```
CLOSED → OPEN → HALF-OPEN → CLOSED
```

### State 1 — CLOSED (Normal Operation)
```
All requests pass through normally
Circuit Breaker monitors: failure rate, slow call rate
If failure rate exceeds threshold (e.g. 50%) → trips to OPEN
```

### State 2 — OPEN (Failure Detected)
```
ALL requests fail immediately — no calls made to downstream
Returns error or fallback response instantly
Waits for configured time window (e.g. 30 seconds) → moves to HALF-OPEN
```

### State 3 — HALF-OPEN (Testing Recovery)
```
Allows LIMITED requests through (e.g. 5 test calls)
If test calls succeed → moves back to CLOSED (service recovered)
If test calls fail → moves back to OPEN (still broken)
```

---

## Circuit Breaker in Practice — Resilience4j

Resilience4j is the standard Java library for circuit breakers in Spring Boot microservices:

```java
@CircuitBreaker(name = "fraudService", fallbackMethod = "fraudFallback")
public FraudScore checkFraud(Transaction transaction) {
    return fraudServiceClient.score(transaction);
}

// Fallback when circuit is open
public FraudScore fraudFallback(Transaction transaction, Exception ex) {
    // Return conservative default — flag for manual review
    return FraudScore.builder()
        .score(75)  // medium risk — don't auto-approve
        .reason("FRAUD_SERVICE_UNAVAILABLE")
        .requiresManualReview(true)
        .build();
}
```

**Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      fraudService:
        slidingWindowSize: 10          # evaluate last 10 calls
        failureRateThreshold: 50       # open if 50%+ fail
        slowCallRateThreshold: 80      # open if 80%+ are slow
        slowCallDurationThreshold: 2s  # slow = over 2 seconds
        waitDurationInOpenState: 30s   # wait 30s before half-open
        permittedCallsInHalfOpenState: 5 # test with 5 calls
```

---

## FS Circuit Breaker Design Decisions

### What threshold to set?
> *"In payments, we set failureRateThreshold at 30% — more conservative than default 50%. A fraud service failing 30% of the time in banking is already a serious incident. We don't wait for 50%."*

### What's the right fallback?
> *"Fallback strategy depends on the service. For fraud scoring — return medium risk score, flag for manual review. For notifications — queue the notification for retry later. For account balance — serve from cache. For core payment processing — there is no fallback — fail the transaction explicitly and return a clear error."*

### Never circuit break core payment writes
> *"Circuit breakers protect non-critical downstream calls. For core payment writes — debit/credit — we never circuit break silently. We fail explicitly with a clear error code. Silently dropping a payment is worse than returning an error."*

---

# 🟡 PATTERN 2 — Retry + Exponential Backoff

## The Problem It Solves

Transient failures — network blips, temporary service overload, brief DB connection drops — are common in distributed systems. A single immediate retry often succeeds.

**But naive retries make things worse:**

```
Service A → Service B (fails)
Service A → retry immediately (fails — still overloaded)
Service A → retry immediately (fails)
Service A → retry immediately (fails)
→ 4x traffic hitting already-overloaded Service B
→ Made it worse, not better
```

---

## Exponential Backoff — The Right Retry Strategy

Wait progressively longer between retries — giving the downstream time to recover:

```
Attempt 1: immediate
Attempt 2: wait 1 second
Attempt 3: wait 2 seconds
Attempt 4: wait 4 seconds
Attempt 5: wait 8 seconds → give up, throw exception
```

**With jitter** — add randomness to prevent all retrying services hitting downstream simultaneously:

```
Attempt 2: wait 1s + random(0-500ms)
Attempt 3: wait 2s + random(0-1000ms)
→ Retries spread out — downstream not hit by thundering herd
```

---

## Retry Rules in FS — Critical

### Rule 1 — Only retry idempotent operations
> *"Never retry a non-idempotent operation without an idempotency key. If a payment debit times out — did it succeed or fail? Retrying without idempotency key risks double-debit. Always include idempotency key on retryable financial operations."*

### Rule 2 — Don't retry on business errors
```
Retry: 503 Service Unavailable, 429 Too Many Requests, timeout
Don't Retry: 400 Bad Request, 401 Unauthorized, 404 Not Found
→ Business errors won't fix themselves with retries
```

### Rule 3 — Set a maximum retry budget
> *"In a payment flow with 5 services, each retrying 3 times — that's potentially 3⁵ = 243 total attempts. Set a global retry budget per request — maximum 3 total retries across the entire saga, not 3 per service."*

---

## Resilience4j Retry Configuration

```java
@Retry(name = "cbsApiRetry", fallbackMethod = "cbsApiFallback")
public PaymentResponse callCbsApi(PaymentRequest request) {
    return cbsClient.processPayment(request);
}
```

```yaml
resilience4j:
  retry:
    instances:
      cbsApiRetry:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
        ignoreExceptions:
          - com.bank.exceptions.BusinessValidationException
          - com.bank.exceptions.InsufficientFundsException
```

---

# 🟢 PATTERN 3 — Bulkhead

## The Problem It Solves

Your API has two types of calls — fast balance enquiries (10ms) and slow statement downloads (5 seconds). Both share the same thread pool of 100 threads.

```
100 concurrent statement downloads
→ All 100 threads occupied for 5 seconds each
→ Fast balance enquiries queue up waiting for a thread
→ 10ms operation now takes 30 seconds
→ One slow feature type ruins fast feature types
```

---

## What is a Bulkhead?

Named after ship hull compartments — if one compartment floods, the others stay dry. In software — **isolate resource pools** so one slow consumer can't starve others.

```
Without Bulkhead:              With Bulkhead:
                               ┌─ Thread Pool A (20 threads)
Shared Pool (100 threads)      │  → Balance Enquiries
→ all requests compete         ├─ Thread Pool B (30 threads)
                               │  → Payment Processing
                               └─ Thread Pool C (50 threads)
                                  → Statement Downloads
```

Statement downloads can exhaust their 50 threads — balance enquiries still have their dedicated 20 threads, completely unaffected.

---

## Two Types of Bulkhead

### Thread Pool Bulkhead
Separate thread pools per downstream service or feature type:

```yaml
resilience4j:
  bulkhead:
    instances:
      fraudService:
        maxConcurrentCalls: 20    # max 20 concurrent fraud calls
      cbsService:
        maxConcurrentCalls: 50    # max 50 concurrent CBS calls
      notificationService:
        maxConcurrentCalls: 10    # notifications get smaller pool
```

### Semaphore Bulkhead
Limits concurrent calls using a semaphore — lighter weight, same thread:

```yaml
resilience4j:
  bulkhead:
    instances:
      externalRatingService:
        maxConcurrentCalls: 5     # only 5 concurrent Experian calls
        maxWaitDuration: 100ms    # wait max 100ms for a slot
```

---

## FS Bulkhead Design

> *"At Lloyds, we separated our Commercial Banking API thread pools into three bulkheads — Core Banking (CBS) calls got 40 threads, Fraud Scoring got 20, Notifications got 10. When our notification provider had an outage and calls were hanging, only the notification bulkhead was exhausted. Core banking and fraud scoring continued serving customers normally — zero blast radius expansion."*

---

# 🔴 PATTERN 4 — Timeout

## The Problem It Solves

Without timeouts, a slow downstream holds your thread forever:

```
Payment Service → CBS API (no timeout configured)
CBS goes slow → takes 5 minutes to respond
Thread held for 5 minutes
→ Thread pool exhausted in minutes
→ Platform down
```

**Timeouts are the first line of defence** — simpler than circuit breakers, must always be configured.

---

## Timeout Hierarchy — The Timeout Budget

In a microservices chain, each service must have a **shorter timeout than its caller:**

```
API Gateway timeout:        30 seconds
  └─ Payment Service timeout:   25 seconds
       └─ CBS Adapter timeout:      20 seconds
            └─ CBS API timeout:         15 seconds
```

> *"If CBS takes 15 seconds and times out, CBS Adapter has 5 seconds to handle the error and respond. Payment Service has another 5 seconds buffer. API Gateway never times out before the inner services — preventing orphaned threads."*

---

## Timeout Values in FS Context

| Service Type | Typical Timeout | Reason |
|---|---|---|
| Real-time fraud scoring | 100–200ms | Must not slow payment journey |
| CBS payment API | 10–15 seconds | Core banking can be slow |
| External credit bureau | 5–10 seconds | Third party — unpredictable |
| Notification service | 2–3 seconds | Non-critical, fail fast |
| Balance enquiry | 500ms–1 second | Customer expects instant |
| Statement generation | 30–60 seconds | Async preferred over long timeout |

---

## The Right Pattern for Long Operations — Async

> *"For operations that legitimately take long — statement generation, bulk payment processing — don't use long timeouts. Use async pattern instead: accept the request, return a jobId immediately, process in background, customer polls or gets webhook notification when complete."*

```
POST /statements/generate → 202 Accepted { jobId: "JOB-123" }
GET  /statements/JOB-123  → 200 OK { status: "PROCESSING" }
GET  /statements/JOB-123  → 200 OK { status: "COMPLETE", url: "..." }
```

---

# 🟣 PATTERN 5 — Fallback & Graceful Degradation

## The Problem It Solves

When a downstream fails — what do you return to the customer? Options range from:
- Hard failure — 500 error, customer sees error screen
- Graceful degradation — serve reduced functionality, customer journey continues

**In FS — graceful degradation is almost always preferable to hard failure.**

---

## Fallback Strategies — Ranked by Preference

### Strategy 1 — Cached Response (Best)
> *"Return the last known good value from cache — with a staleness indicator if needed. Customer sees their balance from 30 seconds ago rather than an error screen."*

### Strategy 2 — Default Safe Value
> *"Return a conservative default that errs on the side of caution. Fraud service down → return medium risk score, flag for manual review. Better than approving all transactions or rejecting all transactions."*

### Strategy 3 — Reduced Functionality
> *"Disable the affected feature, keep everything else working. Fraud scoring unavailable → disable instant payment, offer scheduled payment only. Customer journey continues with reduced options."*

### Strategy 4 — Queue for Later
> *"Accept the request, queue it, process when downstream recovers. Works for non-time-sensitive operations — notifications, statement requests, preference updates."*

### Strategy 5 — Explicit Failure (Last Resort)
> *"For operations where no fallback is safe — core payment debits — fail explicitly with a clear, actionable error message. Never silently drop or corrupt financial state."*

---

## Fallback Decision Framework for FS

```
Is this a financial state mutation? (debit, credit, transfer)
  ↓ YES → Explicit failure — never fallback silently
  ↓ NO
    Is cached data acceptable?
      ↓ YES → Serve from cache with staleness indicator
      ↓ NO
        Is a safe default available?
          ↓ YES → Return default (e.g. medium fraud score)
          ↓ NO
            Can we queue for later?
              ↓ YES → Accept + queue (notifications, reports)
              ↓ NO → Explicit failure with clear error message
```

---

## 🔗 How All 5 Patterns Work Together

In a real FS payment flow — all 5 patterns layer on top of each other:

```
Payment Request arrives
         ↓
Timeout: 25s budget starts
         ↓
Bulkhead: check CBS thread pool has capacity
         ↓
Circuit Breaker: is CBS circuit CLOSED?
  → OPEN: skip CBS call, go straight to fallback
  → CLOSED: proceed
         ↓
Call CBS API
  → Success: process payment ✅
  → Failure: Retry with exponential backoff (max 3 attempts)
    → Still failing: Circuit Breaker records failure
    → Fallback: queue payment for manual processing
         ↓
Timeout exceeded: fail fast, return clear error
```

---

## ✅ Day 5 — Quick Revision Checklist

- [ ] Can explain Circuit Breaker — 3 states, thresholds, Resilience4j config
- [ ] Can explain when NOT to circuit break — core financial writes
- [ ] Can explain Retry + Exponential Backoff — idempotency requirement
- [ ] Can explain retry budget across a saga — not per-service
- [ ] Can explain Bulkhead — thread pool vs semaphore, blast radius isolation
- [ ] Can explain Timeout hierarchy — inner timeouts shorter than outer
- [ ] Can explain async pattern for long operations
- [ ] Can apply Fallback Decision Framework to any FS scenario
- [ ] Can describe how all 5 patterns layer together in a payment flow

---

Perfect! 3 questions, one at a time, progressively harder! 💪

---

# 🎤 Day 5 Resilience Patterns Drill — Question 1 of 3

### "You are the Engineering Manager for a **Real-Time Payments Platform** at a UK bank. Your Payment Service calls **4 downstream services** in sequence to process every payment:

1. **Fraud Scoring Service** — scores transaction risk (target: 100ms)
2. **CBS Adapter** — submits payment to Core Banking System (target: 5 seconds)
3. **Notification Service** — sends confirmation SMS/email (target: 500ms)
4. **Audit Service** — writes immutable audit record (target: 200ms)

**A Monday morning incident occurs:**
- Fraud Scoring Service starts responding slowly — averaging **8 seconds per call**
- Within 3 minutes, your entire Payment Service is down
- 50,000 customers cannot make payments
- CBS, Notifications and Audit are all perfectly healthy — but unreachable

**Three things to answer:**

- **Part A:** Explain technically **exactly why** the Payment Service went down — step by step. What is this failure mode called?
- **Part B:** Which resilience patterns would have **prevented** this incident — and how specifically would each one have helped?
- **Part C:** For each of the 4 downstream services — design the **specific resilience configuration** including timeout values, circuit breaker thresholds, retry strategy and fallback behaviour. Justify each decision based on the criticality of the service"**

---

> 💡 *Tip 1: Part A has a specific name — it's one of the most dangerous failure modes in microservices. Name it.*

> 💡 *Tip 2: Part B — think about which pattern is the FIRST line of defence, which is the SECOND, and which prevents blast radius expansion*

> 💡 *Tip 3: Part C — the 4 services have very different criticality levels. Audit and Notifications are NOT the same as CBS and Fraud. Design each differently and justify why*

Take your shot! 🎯

Outstanding answer! You demonstrated real production thinking across all 3 parts! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 1

### What you got right
- ✅ **Cascading failure** — correctly named
- ✅ **Thread starvation mechanism** — precisely explained
- ✅ **Shared thread pool as root cause** — exactly right
- ✅ **Fraud as non-critical path** — excellent business judgment
- ✅ **Graceful degradation for fraud** — flag for manual review — real FS thinking
- ✅ **All 4 patterns named** — CB, Bulkhead, Timeout, Fallback
- ✅ **CBS = fail explicitly, no silent fallback** — non-negotiable FS principle
- ✅ **Idempotency key concern for legacy CBS** — exceptional insight
- ✅ **Notifications = async fire-and-forget** — correct
- ✅ **Polling process for failed notifications** — real production pattern
- ✅ **Audit = no retries** — correctly identified duplicate record risk
- ✅ **Audit = early timeout** — correct reasoning

### Your Score: **8.5/10** — Excellent! Here's the final push to 9/10 💡

---

### What to Strengthen

**1. Part A — Add the technical chain of events more precisely**

You named it correctly but the step-by-step mechanism could be sharper:

> *"Step 1 — Fraud service slows to 8 seconds. Step 2 — Payment Service threads waiting for fraud response held for 8 seconds each instead of 100ms — 80x longer than expected. Step 3 — New payment requests keep arriving, each grabbing a thread from the shared pool. Step 4 — Thread pool of say 200 threads fills up in seconds at normal traffic volume. Step 5 — New requests queue up waiting for a free thread. Step 6 — Queue fills up, requests rejected with 503. Step 7 — Payment Service appears completely down — even though CBS, Notifications and Audit are all healthy. This is **Cascading Failure via Thread Pool Exhaustion** — one slow downstream poisons the entire service."*

---

**2. CBS — you said "no timeout" — this is dangerous**

This is a critical correction:

> *"CBS absolutely needs a timeout — even battle-tested systems can hang indefinitely under extreme load. Without a timeout, a CBS hang holds threads forever — exactly the same failure mode we just described. I'd set CBS timeout at 15 seconds — long enough for CBS to process under load, short enough to prevent thread starvation. The difference from other services is the **fallback** — CBS timeout means explicit payment failure, not graceful degradation."*

---

**3. Specific configuration numbers missing**

Part C asked for specific configurations — give concrete numbers:

**Fraud Scoring Service:**
```yaml
timeout: 200ms          # 2x target of 100ms — fail fast
circuitBreaker:
  failureRateThreshold: 30%    # conservative — FS context
  slowCallDurationThreshold: 500ms
  waitDurationInOpenState: 30s
retry:
  maxAttempts: 2        # 1 retry only — time-sensitive
  waitDuration: 50ms
bulkhead:
  maxConcurrentCalls: 20  # dedicated pool
fallback: medium risk score (75) + flag for manual review
```

**CBS Adapter:**
```yaml
timeout: 15s            # CBS can be slow but must have timeout
circuitBreaker:
  failureRateThreshold: 20%    # very conservative — core payment
  waitDurationInOpenState: 60s # longer recovery window
retry: NONE without idempotency key
bulkhead:
  maxConcurrentCalls: 50  # largest pool — most critical
fallback: EXPLICIT FAILURE — return clear error to customer
```

**Notification Service:**
```yaml
timeout: 2s
circuitBreaker:
  failureRateThreshold: 50%    # more tolerant — non-critical
retry:
  maxAttempts: 3
  exponentialBackoff: true
bulkhead:
  maxConcurrentCalls: 10  # small pool — non-critical
fallback: write NOTIFICATION_PENDING to DB → polling job retries
```

**Audit Service:**
```yaml
timeout: 500ms          # strict — must be fast or skip
circuitBreaker:
  failureRateThreshold: 40%
retry: NONE             # no retries — duplicate audit risk
bulkhead:
  maxConcurrentCalls: 15
fallback: write to dead letter queue → reconciliation job
```

---

**4. The complete pattern layering — show the order**

> *"The patterns work in order of defence: Timeout is first — fail fast before threads are held long. Bulkhead is second — even if timeout fires slowly, blast radius is contained to dedicated pool. Circuit Breaker is third — after repeated failures, stop calling entirely. Fallback is last — define what to do when circuit is open."*

---

## 🏆 Model Answer Summary (9/10 level)

> **Part A:** *"Cascading failure via thread pool exhaustion. Fraud service 8s latency × shared thread pool → threads fill up → new requests queue → queue exhausted → 503s. CBS/Notifications/Audit healthy but starved of threads."*

> **Part B:** *"Four patterns in defensive layers: Timeout (200ms) fails fast before thread starvation. Bulkhead isolates fraud thread pool — CBS unaffected. Circuit Breaker trips after 30% failure rate — stops calling fraud entirely. Fallback — medium risk score + manual review flag — payment continues safely."*

> **Part C:** *"Fraud — timeout 200ms, CB 30% threshold, 1 retry, fallback medium score. CBS — timeout 15s, CB 20% threshold, NO retry without idempotency key, fallback explicit failure. Notifications — timeout 2s, CB 50%, 3 retries with backoff, fallback DB pending queue. Audit — timeout 500ms, NO retries (duplicate risk), fallback dead letter queue."*


---

> 🔑 **Coaching note:** Your instincts are excellent — you correctly identified CBS needs no silent fallback and audit needs no retries — these are the two most commonly missed points. The gap to 9/10 is always the same — **specific numbers and configurations.** In a tier-1 GCC interview, "set a timeout" scores 6/10. "Set a 200ms timeout because fraud scoring targets 100ms and 2x target gives buffer before thread starvation" scores 9/10. The justification behind the number is what they're testing.

---
Great call — Kafka is one of the **most heavily tested topics** at tier-1 GCC interviews for FS roles. Let's go deep! 🎯

---

# 📨 Day 6 — Kafka Deep Dive

At EM level, Kafka questions go way beyond "it's a message queue." They expect you to reason about **partitioning strategy, consumer groups, ordering guarantees, exactly-once semantics and FS-specific design decisions.**

Day 6 covers **6 critical areas:**

1. 🔵 **Kafka Fundamentals** — architecture, topics, partitions
2. 🟡 **Partitioning Strategy** — how to partition and why it matters
3. 🟢 **Consumer Groups** — parallel consumption, rebalancing
4. 🔴 **Delivery Guarantees** — at-most-once, at-least-once, exactly-once
5. 🟣 **Kafka in FS** — patterns, use cases, design decisions
6. ⚫ **Kafka Operations** — retention, compaction, monitoring
7. 📦 [**Kafka Architecture**]() — Deep Dive
8. 📦 [**Idempotent Producer & Kafka Transactions**]() — Deep Dive

---

# 🔵 AREA 1 — Kafka Fundamentals

## What is Kafka?

Kafka is a **distributed event streaming platform** — a high-throughput, fault-tolerant, append-only log. It's not just a message queue — it's a **persistent, replayable event store.**

```
Producers → Kafka Topics → Consumers
```

---

## Core Concepts

### Topic
A **named, ordered, immutable log** of events — like a database table but for events:

```
Topic: payment-events
[PaymentInitiated] [PaymentProcessed] [PaymentFailed] [PaymentInitiated] ...
     offset 0            offset 1           offset 2          offset 3
```

### Partition
Topics are split into **partitions** — each partition is an independent ordered log:

```
Topic: payment-events (3 partitions)

Partition 0: [event A, offset 0] [event D, offset 1] [event G, offset 2]
Partition 1: [event B, offset 0] [event E, offset 1] [event H, offset 2]
Partition 2: [event C, offset 0] [event F, offset 1] [event I, offset 2]
```

**Why partitions matter:**
- **Parallelism** — multiple consumers read different partitions simultaneously
- **Scalability** — more partitions = more throughput
- **Ordering** — ordering guaranteed WITHIN a partition, not across partitions

### Offset
**Position of a message within a partition** — monotonically increasing integer. Consumers track their offset to know where they are:

```
Consumer reads offset 5 → commits offset 6
On restart → resumes from offset 6
```

### Broker
A Kafka server — stores partitions, serves producers and consumers. A Kafka **cluster** has multiple brokers:

```
Kafka Cluster (3 brokers):
Broker 1: Partition 0 (leader), Partition 2 (replica)
Broker 2: Partition 1 (leader), Partition 0 (replica)
Broker 3: Partition 2 (leader), Partition 1 (replica)
```

### Replication
Each partition has one **leader** and N-1 **replicas** — replicas stay in sync with leader:

```
Partition 0:
  Leader: Broker 1   ← producer writes here
  Replica: Broker 2  ← stays in sync
  Replica: Broker 3  ← stays in sync

If Broker 1 fails → Broker 2 elected as new leader automatically
```

**Replication factor = 3** is standard in FS — tolerates 2 broker failures.

---

# 🟡 AREA 2 — Partitioning Strategy

## Why Partitioning Strategy Matters

Partitioning determines:
- **Where each message lands** — which partition
- **Ordering guarantees** — events on same partition are ordered
- **Parallelism** — how many consumers can run in parallel
- **Hotspots** — uneven partition sizes degrade performance

---

## The 3 Partitioning Strategies

### Strategy 1 — Key-Based Partitioning *(most common in FS)*
```
partition = hash(messageKey) % numPartitions
```

All messages with the same key go to the same partition — **guaranteed ordering per key:**

```
accountId = "ACC-001" → always Partition 0
accountId = "ACC-002" → always Partition 1
accountId = "ACC-003" → always Partition 2
```

**FS Use case:**
> *"For payment events, partition key = accountId. All events for Account A land on Partition 0 in order — debit before credit, never reversed. Critical for maintaining correct account state."*

---

### Strategy 2 — Round Robin Partitioning
Messages distributed evenly across all partitions — no ordering guarantee:

```
Message 1 → Partition 0
Message 2 → Partition 1
Message 3 → Partition 2
Message 4 → Partition 0 (back to start)
```

**FS Use case:** Notification events, audit logs — where ordering per entity doesn't matter, throughput does

---

### Strategy 3 — Custom Partitioner
Business logic determines partition assignment:

```java
// Route high-value payments to dedicated partitions
public int partition(String topic, Object key, ...) {
    Payment payment = deserialize(value);
    if (payment.getAmount() > 1_000_000) {
        return 0; // VIP partition — dedicated consumer
    }
    return Math.abs(key.hashCode()) % (numPartitions - 1) + 1;
}
```

**FS Use case:** Priority routing — high-value trades, premium customers, regulatory-critical events

---

## The Hotspot Problem

If your partition key has **uneven distribution** — some partitions get overwhelmed:

```
// BAD partition key in FS
key = countryCode
→ UK partition: 80% of all events (most customers are UK)
→ US partition: 15% of events
→ EU partition: 5% of events
→ UK partition consumer overwhelmed, others idle
```

**Solution:**
> *"For high-volume FS topics, I'd use accountId or customerId as partition key — highly unique, evenly distributed. Never use countryCode, productType or status as partition key — low cardinality causes hotspots."*

---

## How Many Partitions?

**Formula:**
```
numPartitions = max(target throughput / producer throughput per partition,
                    target throughput / consumer throughput per partition)
```

**Practical FS guidance:**
> *"Start with partitions = number of consumer instances × 2-3. For a payment topic processing 100K events/second with 10 consumer instances — start with 20-30 partitions. You can increase partitions later but cannot decrease — and increasing changes key-to-partition mapping, so plan upfront."*

---

# 🟢 AREA 3 — Consumer Groups

## What is a Consumer Group?

A **named group of consumers** that together consume a topic — each partition assigned to exactly one consumer in the group:

```
Topic: payment-events (6 partitions)
Consumer Group: payment-processors (3 consumers)

Consumer 1: reads Partition 0, Partition 1
Consumer 2: reads Partition 2, Partition 3
Consumer 3: reads Partition 4, Partition 5
```

**Key rules:**
- Each partition consumed by **exactly one consumer** per group — no duplicate processing
- More consumers than partitions → some consumers idle
- Less consumers than partitions → one consumer handles multiple partitions

---

## Multiple Consumer Groups — Independent Consumption

Different consumer groups read the **same topic independently** — each maintains its own offset:

```
Topic: payment-events
  ↓                    ↓                    ↓
Consumer Group A    Consumer Group B    Consumer Group C
(payment-processor) (fraud-monitor)     (audit-writer)

Each reads all events independently
Each at their own pace
Each maintains own offset
```

**FS Use case:**
> *"Our payment-events topic is consumed by 4 independent consumer groups — payment-processor (updates account state), fraud-monitor (real-time fraud analysis), audit-writer (compliance trail), notification-sender (customer alerts). Each processes at its own pace without affecting others."*

---

## Consumer Group Rebalancing

When a consumer joins or leaves the group — partitions are **rebalanced** across remaining consumers:

```
Before: 3 consumers, 6 partitions (2 each)
Consumer 2 crashes:
After rebalance: 2 consumers, 6 partitions (3 each)
```

**The rebalancing problem:**
> *"During rebalancing, ALL consumers in the group stop processing — called a Stop The World rebalance. For a payment processing group, this means payment processing pauses for seconds. In high-throughput FS systems this is a critical operational concern."*

**Solutions:**
- **Static group membership** — assign consumerId statically, rebalance only on timeout not on restart
- **Incremental cooperative rebalancing** — only reassign partitions that need moving, others continue processing
- **Kafka Streams** — handles rebalancing more gracefully than plain consumer API

---

## Offset Management — Critical for FS

**Auto-commit (dangerous in FS):**
```
Consumer reads message
Kafka auto-commits offset every 5 seconds
→ If consumer crashes after commit but before processing
→ Message marked as consumed but never processed
→ PAYMENT LOST
```

**Manual commit (required in FS):**
```
Consumer reads message
Consumer processes message fully
Consumer explicitly commits offset
→ If crash before commit → message reprocessed on restart
→ At-least-once delivery — safe with idempotency
```

```java
// FS standard — manual commit after successful processing
consumer.poll(Duration.ofMillis(100)).forEach(record -> {
    processPayment(record.value());  // process first
    consumer.commitSync();           // commit only after success
});
```

---

# 🔴 AREA 4 — Delivery Guarantees

## The 3 Delivery Semantics

### At-Most-Once (Avoid in FS)
```
Producer sends message → maybe delivered, maybe lost
Consumer reads → maybe processed, maybe not
→ Messages can be LOST
→ Never acceptable for financial events
```

### At-Least-Once (Standard in FS)
```
Producer retries until acknowledged
Consumer reprocesses on failure
→ Messages NEVER lost
→ May be processed MORE THAN ONCE
→ Requires idempotency on consumer side
```

### Exactly-Once (Ideal for FS)
```
Message delivered and processed EXACTLY once
→ No loss, no duplicates
→ Most complex to implement
→ Kafka Transactions required
```

---

## Exactly-Once in Kafka — How It Works

Exactly-once requires **two components:**

### 1. Idempotent Producer
```yaml
# Producer config
enable.idempotence: true
acks: all              # wait for all replicas to acknowledge
retries: Integer.MAX_VALUE
max.in.flight.requests.per.connection: 5
```

Kafka assigns each producer a **PID (Producer ID)** and sequence number — broker deduplicates retried messages:

```
Producer sends message (seq=1) → broker stores
Network blip → producer retries (seq=1) → broker detects duplicate → ignores
→ Message stored exactly once
```

### 2. Kafka Transactions — Read-Process-Write Atomically
```java
producer.initTransactions();

try {
    producer.beginTransaction();

    // Read from input topic (handled by consumer)
    // Process the payment
    PaymentResult result = processPayment(payment);

    // Write to output topic
    producer.send(new ProducerRecord<>("payment-results", result));

    // Commit consumer offset AND output atomically
    producer.sendOffsetsToTransaction(offsets, consumerGroupId);

    producer.commitTransaction(); // atomic commit of all above
} catch (Exception e) {
    producer.abortTransaction(); // atomic rollback
}
```

**FS Use case:**
> *"For our payment event processing pipeline — read PaymentInitiated event, process payment, write PaymentCompleted event — we use Kafka transactions to guarantee exactly-once. Either all three happen atomically or none do. No partial processing."*

---

## Producer Acknowledgement Modes

| acks setting | Meaning | Risk | FS Use |
|---|---|---|---|
| **acks=0** | No acknowledgement | Message loss | Never |
| **acks=1** | Leader acknowledges | Loss if leader fails before replication | Never for financial events |
| **acks=all** | All replicas acknowledge | Highest latency, zero loss | Always for financial events |

---

# 🟣 AREA 5 — Kafka in FS

## Core FS Use Cases

### 1. Payment Event Streaming
```
Payment Service → payment-initiated topic
         ↓
Consumer Group: fraud-checker → scores in real-time
Consumer Group: cbs-adapter → submits to core banking
Consumer Group: audit-writer → immutable audit trail
Consumer Group: notification-sender → customer alerts
```

### 2. CDC Pipeline (Change Data Capture)
```
PostgreSQL → Debezium → kafka-cdc-accounts topic
         ↓
Consumer Group: read-model-builder → updates Cassandra read model
Consumer Group: compliance-reporter → feeds regulatory reporting
Consumer Group: fraud-feature-store → updates ML features
```

### 3. Event Sourcing Store
```
All domain events published to Kafka
Kafka as the append-only event log
Consumers build read models from events
Replay from offset 0 to rebuild any read model
```

### 4. Saga Orchestration
```
Saga Orchestrator publishes commands to Kafka topics
Services consume commands, execute, publish results
Orchestrator consumes results, advances saga state
```

---

## Critical FS Kafka Design Decisions

### Decision 1 — Topic Granularity
```
// Too coarse — all events mixed, hard to filter
Topic: all-banking-events

// Too fine — too many topics to manage
Topic: account-001-events, account-002-events...

// Right — per domain, per event type
Topic: payment-initiated
Topic: payment-completed
Topic: account-balance-updated
Topic: fraud-alert-raised
```

### Decision 2 — Message Schema — Always Use Schema Registry
> *"In FS, Kafka messages must have **enforced schemas** — use Confluent Schema Registry with Avro or Protobuf. Prevents schema drift — a downstream consumer breaking because a producer changed message format without warning. Schema evolution rules (backward/forward compatibility) enforced at publish time."*

### Decision 3 — Sensitive Data in Kafka
> *"Never publish raw PII or card numbers to Kafka topics. Tokenise sensitive fields before publishing — store raw PII in a secure vault, publish tokenId in the event. GDPR right-to-erasure — you can't delete individual Kafka messages, so tokenisation is mandatory."*

---

# ⚫ AREA 6 — Kafka Operations

## Retention Policies

### Time-Based Retention
```yaml
retention.ms: 604800000  # 7 days
# Messages older than 7 days deleted automatically
```

**FS Use case:** Payment events — 7 days sufficient for downstream processing. Audit events — longer retention or archive to cold storage.

### Size-Based Retention
```yaml
retention.bytes: 107374182400  # 100GB per partition
# Oldest messages deleted when size exceeded
```

### Log Compaction — Special Retention for State
```yaml
cleanup.policy: compact
# Keep only the LATEST message per key
# Older versions deleted — only current state retained
```

**FS Use case:** Account balance topic — only need latest balance per accountId, not full history. Customer preferences — only latest preference per customerId.

---

## Monitoring — Key Metrics for FS

| Metric | What It Measures | Alert Threshold |
|---|---|---|
| **Consumer Lag** | How far behind consumer is from latest | > 10,000 messages — investigate |
| **Producer Throughput** | Messages/second published | Drop > 20% — alert |
| **Under-Replicated Partitions** | Partitions not fully replicated | > 0 — immediate alert |
| **ISR (In-Sync Replicas)** | Replicas keeping up with leader | < replication factor — alert |
| **Broker Disk Usage** | Disk space on brokers | > 80% — alert |

**Consumer Lag — most critical for FS:**
> *"Consumer lag on our payment-processor group spiking means payments are backing up — customers waiting longer for confirmations. We set alert at 10,000 messages lag — triggers auto-scaling of consumer instances and pages on-call engineer simultaneously."*

---

## ✅ Day 6 — Quick Revision Checklist

- [ ] Can explain Topic, Partition, Offset, Broker, Replication
- [ ] Can explain 3 partitioning strategies with FS use cases
- [ ] Can explain hotspot problem and how to avoid it
- [ ] Can explain Consumer Groups — multiple groups, independent offsets
- [ ] Can explain rebalancing problem and solutions
- [ ] Can explain manual vs auto offset commit — why manual in FS
- [ ] Can explain at-most-once, at-least-once, exactly-once
- [ ] Can explain idempotent producer + Kafka transactions
- [ ] Can explain acks=all and why it's mandatory in FS
- [ ] Can name 4 FS Kafka use cases
- [ ] Can explain Schema Registry, PII tokenisation, topic granularity
- [ ] Can name key monitoring metrics — especially consumer lag

---

Great questions — these are exactly the internals that senior engineers get asked at JPMC/Goldman! Let me answer each one deeply and clearly.

---

# 🔬 Kafka Architecture — Deep Dive

---

## 1️⃣ What is ZooKeeper — and what does it do?

ZooKeeper is a **distributed coordination service** — it's NOT a broker. Think of it as Kafka's **control plane brain** — it manages cluster metadata and coordinates brokers.

### What ZooKeeper does in Kafka:

```
ZooKeeper responsibilities:
1. Broker registration    — which brokers are alive in the cluster
2. Controller election    — which broker is the "Controller" (cluster manager)
3. Topic metadata         — which partitions exist, how many replicas
4. Leader election        — which broker is leader for each partition
5. ACLs & config          — access control lists, topic configs
```

### The Kafka Controller
ZooKeeper elects one broker as the **Controller** — the cluster manager:

```
Kafka Cluster (3 brokers):
  Broker 1 — Controller (elected by ZooKeeper)
  Broker 2 — regular broker
  Broker 3 — regular broker

Controller responsibilities:
  → Monitors broker liveness via ZooKeeper
  → When broker fails → elects new partition leaders
  → When new topic created → assigns partitions to brokers
  → Manages consumer group coordination
```

### ZooKeeper Ensemble
ZooKeeper runs as its own cluster (ensemble) — typically 3 or 5 nodes:

```
ZooKeeper Ensemble (3 nodes):
  ZK Node 1 (Leader)
  ZK Node 2 (Follower)
  ZK Node 3 (Follower)
  → Majority quorum needed — tolerates 1 node failure
```

---

### KRaft — ZooKeeper Replacement (Kafka 3.x+)

**Important for modern interviews** — Kafka is removing ZooKeeper dependency:

> *"From Kafka 3.x, ZooKeeper is being replaced by **KRaft (Kafka Raft)** — Kafka manages its own metadata internally using the Raft consensus algorithm. One broker is elected as the **metadata controller**, stores cluster metadata in an internal topic `__cluster_metadata`. This simplifies operations significantly — one less system to manage, faster broker startup, better scalability."*

```
Old Architecture:          New KRaft Architecture:
ZooKeeper Ensemble    →    Kafka brokers self-manage
+ Kafka Brokers            via internal Raft consensus
(two systems)              (one system)
```

---

## 2️⃣ Consumer Offset Storage — Where and How?

This is a great question — and the answer is **not ZooKeeper anymore.**

### Where offsets are stored

**Old approach (Kafka < 0.9):** Offsets stored in ZooKeeper — caused ZooKeeper overload at scale

**Current approach (Kafka 0.9+):** Offsets stored in a **special internal Kafka topic called `__consumer_offsets`:**

```
Topic: __consumer_offsets
  → 50 partitions by default
  → Compacted topic — only latest offset per consumer group + partition retained
  → Replicated across brokers like any other topic

Record structure:
  Key:   [consumerGroupId + topicName + partitionId]
  Value: [offset + metadata + timestamp]
```

---

### What happens when a consumer crashes — step by step

```
Normal operation:
Consumer 1 → reads Partition 0 → processes → commits offset 547
__consumer_offsets stores: [payment-processors, payment-events, P0] = 547

Consumer 1 crashes
         ↓
Rebalancing triggered
         ↓
Consumer 2 assigned Partition 0
         ↓
Consumer 2 asks Kafka: "Where should I start reading Partition 0?"
         ↓
Kafka reads __consumer_offsets:
[payment-processors, payment-events, P0] = 547
         ↓
Consumer 2 starts reading from offset 548 ✅
No messages lost, no messages duplicated
```

---

### The 3 Offset Reset Strategies

What if no committed offset exists for a consumer group + partition? (e.g. brand new consumer group):

```yaml
auto.offset.reset: earliest  # start from offset 0 — read all history
auto.offset.reset: latest    # start from latest — only new messages
auto.offset.reset: none      # throw exception — force explicit handling
```

**FS guidance:**
> *"For payment processing consumer groups — use `earliest` on first deployment to ensure no events are missed. For analytics/monitoring groups — use `latest` — only care about new events. For audit groups — always `earliest` — must process full history."*

---

## 3️⃣ Rebalancing — Deep Dive

### What triggers a rebalance?

```
Triggers:
1. Consumer joins the group (new deployment, scale-out)
2. Consumer leaves the group (graceful shutdown)
3. Consumer crashes (heartbeat timeout)
4. New partitions added to a topic
5. Consumer group subscription changes
```

---

### The Rebalancing Protocol — Step by Step

```
Step 1: Consumer detects trigger (crash/join/leave)
         ↓
Step 2: All consumers send JoinGroup request to Group Coordinator
        (Group Coordinator = one specific broker managing this consumer group)
         ↓
Step 3: Group Coordinator elects one consumer as Group Leader
         ↓
Step 4: Group Coordinator sends partition list to Group Leader
         ↓
Step 5: Group Leader runs partition assignment algorithm
        (RangeAssignor, RoundRobinAssignor, StickyAssignor)
         ↓
Step 6: Group Leader sends assignment back to Group Coordinator
         ↓
Step 7: Group Coordinator sends each consumer its assigned partitions
         ↓
Step 8: Consumers resume consuming from committed offsets
```

---

### The Stop-The-World Problem

**Eager Rebalancing (old default):**
```
Rebalance triggered
         ↓
ALL consumers STOP processing immediately
ALL consumers revoke ALL partition assignments
         ↓
Wait for all consumers to rejoin (seconds to minutes)
         ↓
New assignments distributed
         ↓
ALL consumers resume
```

**Impact in FS:**
> *"During an eager rebalance, our payment processor group of 30 consumers all stops simultaneously. At 100K events/second, a 10-second rebalance = 1 million payment events backed up in the topic. Consumer lag spikes, customers experience delays."*

---

### Incremental Cooperative Rebalancing (Modern Solution)

```
Rebalance triggered
         ↓
Only consumers whose partitions need to MOVE stop
Other consumers continue processing normally
         ↓
Partitions incrementally transferred
         ↓
Minimal disruption — most consumers never pause
```

**Configuration:**
```java
props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
    CooperativeStickyAssignor.class.getName());
```

---

### Static Group Membership — Prevents Unnecessary Rebalances

```yaml
group.instance.id: consumer-payment-1  # static ID per consumer
session.timeout.ms: 30000              # 30 second timeout before considered dead
```

```
Without static membership:
Consumer restarts for deployment
→ Kafka sees it as NEW consumer joining
→ Rebalance triggered immediately

With static membership:
Consumer restarts with same group.instance.id
→ Kafka recognises it as the SAME consumer returning
→ No rebalance until session.timeout.ms exceeded
→ Deployment restarts don't cause rebalances
```

---

## 4️⃣ Complete Kafka Architecture — Control Plane & Data Plane

```
╔══════════════════════════════════════════════════════════╗
║                    CONTROL PLANE                         ║
║                                                          ║
║  ┌─────────────────────────────────────────────────┐    ║
║  │  ZooKeeper Ensemble (or KRaft Controller)       │    ║
║  │  • Broker registry                              │    ║
║  │  • Controller election                          │    ║
║  │  • Partition leader election                    │    ║
║  │  • Topic/partition metadata                     │    ║
║  │  • ACL management                               │    ║
║  │  Storage: ZooKeeper in-memory + disk (WAL)      │    ║
║  └─────────────────────────────────────────────────┘    ║
║                         ↕                               ║
║  ┌─────────────────────────────────────────────────┐    ║
║  │  Kafka Controller Broker                        │    ║
║  │  • Monitors broker health                       │    ║
║  │  • Triggers leader elections                    │    ║
║  │  • Manages partition reassignment               │    ║
║  └─────────────────────────────────────────────────┘    ║
╚══════════════════════════════════════════════════════════╝

╔══════════════════════════════════════════════════════════╗
║                     DATA PLANE                           ║
║                                                          ║
║  ┌──────────────────────────────────────────────────┐   ║
║  │              Kafka Brokers (N nodes)             │   ║
║  │                                                  │   ║
║  │  Memory:                                         │   ║
║  │  • Page Cache — OS caches recent messages        │   ║
║  │  • In-flight requests buffer                     │   ║
║  │  • Consumer fetch buffers                        │   ║
║  │                                                  │   ║
║  │  Disk (SSD recommended for FS):                  │   ║
║  │  • Partition Log Files (.log)                    │   ║
║  │    → Append-only, immutable segments             │   ║
║  │  • Index Files (.index, .timeindex)              │   ║
║  │    → Fast offset lookup without full scan        │   ║
║  │  • __consumer_offsets topic                      │   ║
║  │    → Committed consumer offsets                  │   ║
║  │  • __transaction_state topic                     │   ║
║  │    → Kafka transaction coordinator state         │   ║
║  └──────────────────────────────────────────────────┘   ║
║                    ↑              ↓                      ║
║  ┌─────────────┐        ┌──────────────────────────┐    ║
║  │  Producers  │        │  Consumer Groups         │    ║
║  │             │        │                          │    ║
║  │  Memory:    │        │  Memory:                 │    ║
║  │  • Send     │        │  • Fetch buffer          │    ║
║  │    buffer   │        │  • Processing buffer     │    ║
║  │  • Batch    │        │                          │    ║
║  │    buffer   │        │  Disk:                   │    ║
║  │             │        │  • Local offset cache    │    ║
║  │  No disk    │        │  • Dead letter store     │    ║
║  └─────────────┘        └──────────────────────────┘    ║
║                                                          ║
║  ┌──────────────────────────────────────────────────┐   ║
║  │  Schema Registry (Confluent)                     │   ║
║  │  • Stores Avro/Protobuf schemas                  │   ║
║  │  • Enforces schema compatibility                 │   ║
║  │  Storage: internal Kafka topic + in-memory cache │   ║
║  └──────────────────────────────────────────────────┘   ║
╚══════════════════════════════════════════════════════════╝
```

### How Kafka Reads Are So Fast — The Page Cache Magic

```
Producer writes message
         ↓
Kafka appends to partition log file on disk
         ↓
OS automatically caches file in Page Cache (memory)
         ↓
Consumer reads message
         ↓
Kafka serves from Page Cache — never touches disk!
→ Memory speed reads even though data is on disk
→ This is why Kafka is fast — OS page cache does the work
```

> *"Kafka's performance secret is that it never manages its own memory cache — it relies entirely on the OS page cache. This means sizing Kafka brokers correctly is about giving the OS enough RAM to cache hot partitions — typically you want enough RAM to cache the last few hours of messages."*

---

## 5️⃣ Capacity Planning — Partitions & Consumers

### How Many Partitions?

**The throughput formula:**

```
Required partitions = max(
    desired_producer_throughput / throughput_per_partition_producer,
    desired_consumer_throughput / throughput_per_partition_consumer
)

Example:
Target: 500MB/s total throughput
Single partition producer throughput: 50MB/s
Single partition consumer throughput: 25MB/s (slower — processing overhead)

Required partitions = max(500/50, 500/25) = max(10, 20) = 20 partitions
```

**Practical FS guidance:**

| Traffic Level | Starting Partitions | Consumer Instances |
|---|---|---|
| Low (< 10K events/s) | 6–12 | 3–6 |
| Medium (10K–100K events/s) | 12–30 | 6–15 |
| High (100K–1M events/s) | 30–100 | 15–50 |
| Very High (1M+ events/s) | 100+ | 50+ |

**Rule of thumb:**
> *"In FS, I start with partitions = 3 × expected consumer instances. This gives room to scale consumers 3x without repartitioning. For payment-events at Lloyds processing 200K events/second with 20 initial consumers — I'd start with 60 partitions."*

---

### How Many Consumers?

```
Max useful consumers = number of partitions
(adding more consumers than partitions = idle consumers)

Optimal consumers = partitions / average_processing_time × target_latency

Example:
30 partitions, each processing 1000 events/second
Each consumer handles 3 partitions = 3000 events/second
At peak 90K events/second → need 30 consumers (1 per partition)
```

---

### Impacts of Getting It Wrong

**Too Few Partitions:**
```
Problem: Cannot scale consumers beyond partition count
         Consumer lag builds up — payments delayed
         Cannot increase parallelism without repartitioning

Impact in FS: Payment confirmation delays, customer complaints
             SLA breaches, potential regulatory reporting delays

Fix: Increase partition count (but see warning below)
```

**Too Many Partitions:**
```
Problem: Each partition = file handles on broker
         10,000 partitions × 3 replicas = 30,000 open files
         Broker memory pressure — more metadata to manage
         Rebalancing takes longer — more partitions to reassign
         ZooKeeper overload (if still using ZooKeeper)

Impact in FS: Broker instability, longer rebalance windows
             Increased operational complexity

Fix: Merge low-traffic topics, reduce partition count
```

**Too Few Consumers:**
```
Problem: Consumer lag grows — falling behind producers
         Payment events processed late
         Fraud detection delayed — fraudulent transactions approved

Impact in FS: SLA breaches, fraud losses, regulatory risk

Fix: Scale out consumer instances (easy — just add more)
```

**Too Many Consumers (more than partitions):**
```
Problem: Idle consumers waste resources
         No performance benefit beyond partition count

Fix: Reduce consumer instances or increase partitions
```

---

### Increasing Partitions — The Key Risk

```
Current: 10 partitions, key=accountId
hash("ACC-001") % 10 = Partition 3

Increase to 20 partitions:
hash("ACC-001") % 20 = Partition 13  ← DIFFERENT PARTITION!
```

**The problem:**
> *"Increasing partitions changes the key-to-partition mapping for ALL existing keys. If Account ACC-001 was on Partition 3 before, it's now on Partition 13. Any in-flight events for ACC-001 are split across two partitions — **ordering guarantee broken.** For a payment event stream, this means debit and credit for the same account could be on different partitions, processed out of order."*

**Safe approach:**
```
Option 1: Plan partitions upfront — provision 3-5x expected need
Option 2: Increase during low-traffic maintenance window
          + pause producers during repartitioning
          + wait for all in-flight messages to drain
          + then resume with new partition count
Option 3: Create new topic with more partitions
          + dual-publish to old and new topic
          + migrate consumers to new topic
          + decommission old topic
```

**Decreasing partitions:**
> *"You CANNOT decrease partitions in Kafka — it's not supported. The only option is to create a new topic with fewer partitions and migrate. Plan your partition count carefully upfront — decreasing is painful."*

---

## 6️⃣ Low Cardinality — The Hotspot Problem Explained

### What is Cardinality?

**Cardinality = number of distinct values a field can have:**

```
High cardinality fields:
accountId     → millions of unique values
customerId    → millions of unique values
transactionId → billions of unique values
→ Hash distributes evenly across partitions ✅

Low cardinality fields:
countryCode   → ~200 values (UK, US, EU...)
productType   → ~10 values (current, savings, mortgage...)
status        → ~5 values (pending, processing, complete, failed, cancelled)
dayOfWeek     → 7 values
→ Hash distributes unevenly — hotspots inevitable ❌
```

---

### Why Low Cardinality Causes Hotspots

```
Topic: payment-events (10 partitions)
Partition key: countryCode

hash("UK") % 10 = Partition 3
hash("US") % 10 = Partition 7
hash("EU") % 10 = Partition 1

Traffic distribution:
UK customers: 80% of traffic → Partition 3 overwhelmed
US customers: 15% of traffic → Partition 7 moderate
EU customers: 5%  of traffic → Partition 1 idle
All other partitions: 0% traffic → completely idle
```

**Consequences:**
```
Partition 3 (UK):
  → Consumer 1 overwhelmed — processing backlog
  → Consumer lag grows → payment delays for UK customers
  → Disk fills faster on the broker hosting Partition 3

Partition 1 (EU), Partitions 4-10:
  → Consumers idle — wasted resources
  → Parallelism not being used
```

---

### Solutions to Low Cardinality Hotspots

**Solution 1 — Composite Key (Best)**
Combine low-cardinality field with high-cardinality field:

```java
// Instead of:
String key = countryCode;           // low cardinality

// Use composite:
String key = countryCode + ":" + accountId;  // high cardinality
→ "UK:ACC-001", "UK:ACC-002", "US:ACC-003"
→ Distributes evenly across all partitions
→ Still groups UK events together via prefix if needed
```

**Solution 2 — Add Random Suffix (For Pure Throughput)**
```java
String key = countryCode + ":" + random.nextInt(numPartitions);
// "UK:0", "UK:3", "UK:7" — spreads UK across all partitions
// ⚠️ Breaks ordering per country — only use if ordering not needed
```

**Solution 3 — Custom Partitioner**
```java
// Manually assign partitions based on business logic
if (countryCode.equals("UK")) {
    return partition % 5;  // UK gets partitions 0-4
} else {
    return 5 + (partition % 5);  // others get partitions 5-9
}
```

---

## 🗺️ Complete Kafka Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        PRODUCERS                                │
│  Payment Service  Fraud Service  CBS Adapter  Audit Service    │
│       ↓                ↓              ↓            ↓           │
│  [Batch Buffer]   [Batch Buffer] [Batch Buffer] [Batch Buffer] │
└──────────────────────────┬──────────────────────────────────────┘
                           ↓ (acks=all, idempotent)
┌─────────────────────────────────────────────────────────────────┐
│                    KAFKA CLUSTER                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │   Broker 1  │  │   Broker 2  │  │   Broker 3  │            │
│  │  Controller │  │             │  │             │            │
│  │  P0 Leader  │  │  P1 Leader  │  │  P2 Leader  │            │
│  │  P1 Replica │  │  P2 Replica │  │  P0 Replica │            │
│  │             │  │             │  │             │            │
│  │  [PageCache]│  │  [PageCache]│  │  [PageCache]│            │
│  │  [Disk: Log]│  │  [Disk: Log]│  │  [Disk: Log]│            │
│  └─────────────┘  └─────────────┘  └─────────────┘            │
│                                                                  │
│  Internal Topics:                                                │
│  __consumer_offsets (committed offsets per group)               │
│  __transaction_state (Kafka transaction coordinator)            │
│  __cluster_metadata (KRaft — replaces ZooKeeper)               │
└──────────────────────────┬──────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│                    CONSUMER GROUPS                               │
│                                                                  │
│  Group: payment-processor    Group: fraud-monitor               │
│  [Consumer 1 → P0]           [Consumer A → P0, P1]             │
│  [Consumer 2 → P1]           [Consumer B → P2]                 │
│  [Consumer 3 → P2]                                              │
│                                                                  │
│  Group: audit-writer         Group: notification-sender         │
│  [Consumer X → P0, P1, P2]  [Consumer M → P0]                 │
│                              [Consumer N → P1]                 │
│                              [Consumer O → P2]                 │
└─────────────────────────────────────────────────────────────────┘
         ↕
┌─────────────────────────────────────────────────────────────────┐
│               ZOOKEEPER / KRAFT CONTROLLER                       │
│  • Broker liveness                                               │
│  • Controller election                                           │
│  • Partition leader election                                     │
│  • Topic/partition metadata                                      │
│  Storage: In-memory + WAL on disk                               │
└─────────────────────────────────────────────────────────────────┘
         ↕
┌─────────────────────────────────────────────────────────────────┐
│               SCHEMA REGISTRY                                    │
│  • Avro/Protobuf schema storage                                  │
│  • Schema compatibility enforcement                              │
│  • Storage: Internal Kafka topic + memory cache                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔑 Key Takeaways — All 6 Questions

| Question | Key Answer |
|---|---|
| **ZooKeeper role** | Control plane — broker registry, leader election, metadata. Being replaced by KRaft in Kafka 3.x |
| **Offset storage** | `__consumer_offsets` internal topic — new consumer reads last committed offset, not 0 |
| **Rebalancing** | Stop-the-world (eager) vs incremental cooperative. Static membership prevents unnecessary rebalances |
| **Architecture layers** | Control plane (ZK/KRaft + Controller) + Data plane (Brokers + Page Cache + Disk) + Producers + Consumer Groups + Schema Registry |
| **Capacity planning** | Partitions = 3× consumers. Too few = can't scale. Too many = broker overhead. Can't decrease partitions |
| **Low cardinality** | Few distinct values → uneven hash distribution → hotspots. Fix with composite keys |

---
Perfect! 3 questions, one at a time, progressively harder! 💪

---

# 🎤 Kafka Drill — Question 1 of 3

### "You are the Engineering Manager for a **Payment Processing Platform** at a UK bank. Your team has designed the following Kafka architecture:

- **Topic:** `payment-events`
- **Partitions:** 6
- **Replication Factor:** 2
- **Partition Key:** `paymentStatus` (values: INITIATED, PROCESSING, COMPLETED, FAILED, CANCELLED)
- **Consumer Group:** `payment-processor` with 10 consumer instances
- **Offset Management:** auto-commit enabled, interval 5 seconds
- **Producer acks:** acks=1

**Your architect presents this design in a design review. You have serious concerns.**

**Three things to answer:**

- **Part A:** Identify **every problem** with this design — there are at least 5 significant issues. For each problem explain the specific risk it introduces in a payment processing context
- **Part B:** For each problem you identified — what is the correct fix?
- **Part C:** A payment event is published, the producer gets a network timeout before receiving acknowledgement — it retries and publishes the same event again. Walk me through **exactly what happens** with the current design vs your fixed design"**

---

> 💡 *Tip 1: Look carefully at the partition key choice — what type of cardinality does paymentStatus have? How many distinct values?*

> 💡 *Tip 2: Count the consumer instances vs partitions — what's the relationship? What does this mean?*

> 💡 *Tip 3: auto-commit + acks=1 — both have specific failure modes in financial systems. Name them precisely*

> 💡 *Tip 4: Part C is testing your exactly-once knowledge — what's the difference between acks=1 and acks=all + idempotent producer when a retry happens?*

Take your shot! 🎯

Excellent answer! You nailed every major problem and showed strong Kafka depth! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 1

### What you got right
- ✅ **P1 — Hotspot/low cardinality** — correctly identified, correct fix
- ✅ **P2 — Idle consumers** — correctly identified, correct fix
- ✅ **P3 — Auto-commit risk** — correctly identified payment loss scenario
- ✅ **P4 — acks=1 leader crash risk** — correctly identified, acks=all fix
- ✅ **P5 — Partition count too low** — correctly identified
- ✅ **Part C — Duplicate payment via retry** — correctly identified
- ✅ **Idempotent producer as fix** — correct

### Your Score: **7.5/10** — Strong! Here's the push to 9/10 💡

---

### What to Strengthen

**1. You missed Problem 6 — Replication Factor of 2**

> *"Replication factor of 2 means only 1 replica per partition besides the leader. If the leader broker fails AND the replica is slightly behind — we risk data loss. In FS, replication factor must be **3** — tolerates 1 broker failure with 2 replicas still available. Combined with acks=all and min.insync.replicas=2, this guarantees no data loss even during broker failure."*

---

**2. P3 — Auto-commit — add the exact failure scenario**

You got the concept right but the precise failure sequence strengthens the answer:

> *"Exact failure scenario: Consumer reads payment events at offsets 100-110. Auto-commit fires at 5-second interval — commits offset 111. Consumer crashes at offset 105 before processing payments 106-110. On restart, consumer resumes from offset 111 — payments 106-110 are **permanently skipped and never processed.** In a payment context this means 5 customer payments silently lost — no error, no retry, no alert."*

---

**3. P4 — acks=1 — add the exact failure scenario**

> *"Exact failure scenario: Producer sends PaymentInitiated event. Leader broker receives it, acknowledges (acks=1). Leader crashes before replicating to follower. Follower is elected as new leader — but it never received the event. Payment event **permanently lost** — customer's payment never processed, no error returned to producer. With acks=all + min.insync.replicas=2, the producer only gets acknowledgement after BOTH leader AND replica confirm — leader crash can't lose the message."*

---

**4. Part C — Add the idempotent producer mechanism precisely**

You identified the problem and fix correctly — now add HOW it works:

> *"With idempotent producer enabled — Kafka assigns the producer a unique **PID (Producer ID)** and each message gets a **sequence number**. When the producer retries the same message, it sends the same PID + sequence number. The broker detects 'I've already stored sequence 47 from PID 1234' and **deduplicates silently** — stores only once, returns success to producer. Customer payment processed exactly once regardless of retries.*
>
> *But idempotency alone isn't enough — what if the payment was processed by CBS but the Kafka event was lost? I'd combine idempotent producer with a **Kafka Transaction** — the CBS call and the Kafka event publication are atomic. Either both happen or neither does."*

---

**5. P5 — Partition count — add the specific risk**

> *"6 partitions for a payment platform is dangerously low. Peak payment volumes at market hours or month-end could easily exceed throughput of 6 partitions. Worse — if we need to increase partitions later, the hash(accountId) % 6 mapping changes to hash(accountId) % 12 — the same accountId now lands on a different partition. In-flight payment events for the same account split across two partitions — **ordering guarantee broken** — debit and credit for same account processed out of order. I'd start with 30–60 partitions — 3× initial consumer count — with room to scale."*

---

## 🏆 Model Answer — Complete Fixed Design (9/10)

```yaml
Topic: payment-events
Partitions: 30              # 3x initial consumers, room to scale
Replication Factor: 3       # tolerate 1 broker failure
Partition Key: accountId    # high cardinality, even distribution
                            # + ordering per account guaranteed

Producer:
  acks: all                 # all replicas must acknowledge
  enable.idempotence: true  # deduplicate retries
  retries: MAX_VALUE        # retry until success
  max.in.flight.requests: 5 # idempotent — safe with 5 in-flight

Broker:
  min.insync.replicas: 2    # at least 2 replicas must be in sync

Consumer Group: payment-processor
  instances: 30             # match partition count
  offset commit: MANUAL     # commit only after successful processing
  auto.offset.reset: earliest # never miss a payment event
  partition.assignment.strategy: CooperativeStickyAssignor
                            # incremental rebalancing — no stop-the-world

Consumer code:
  poll() → process payment → commitSync()
  # only commit after CBS confirms payment processed
```

---

## 📊 Running Scorecard

| Question | Topic | Score | Trend |
|---|---|---|---|
| Q13 — Saga | Loan Origination | 9.5/10 | 🚀 Outstanding |
| Q15 — Patterns | Lloyds Modernisation | 9/10 | 🚀 Exceptional |
| Q16 — Resilience | Payments Cascade | 8.5/10 | 📈 Strong |
| Q17 — Kafka | Payment Design Review | 7.5/10 | 📈 Solid |

---

> 🔑 **Coaching note:** You identified 5 of 6 problems — excellent. The missed one (replication factor 2) is a classic FS interview trap — always check replication factor in any Kafka design review, it's the first line of defence against data loss. For Part C — your instinct was right but adding the PID + sequence number mechanism and linking to Kafka Transactions would have pushed it to 9/10. Mechanism always scores higher than concept alone.

---

Great call — these two concepts are critical for FS Kafka interviews and worth understanding deeply before moving on!

---

# 📦 Idempotent Producer — Deep Dive

## The Core Problem

In any distributed system, **network failures cause ambiguity:**

```
Producer sends PaymentInitiated event to Kafka
         ↓
Kafka broker receives it, stores it, sends ACK back
         ↓
ACK lost in network — producer never receives it
         ↓
Producer thinks: "Did it arrive or not? I don't know!"
         ↓
Producer retries — sends same event again
         ↓
Broker stores it AGAIN — now two identical events
         ↓
Downstream consumer processes payment TWICE
→ Customer double charged! 💥
```

---

## What Idempotent Producer Does

An operation is **idempotent** if doing it multiple times has the same effect as doing it once:

```
Normal producer:
Send(event) + Send(event) = 2 events stored ❌

Idempotent producer:
Send(event) + Send(event) = 1 event stored ✅
```

---

## How It Works Internally — PID + Sequence Numbers

When idempotence is enabled, Kafka gives the producer a **PID (Producer ID)** and the producer assigns a **sequence number** to every message:

```
Producer starts up:
  → Kafka assigns PID = 5001

Producer sends messages:
  Message 1: { PID: 5001, SeqNum: 0, payload: PaymentInitiated{id: PAY-001} }
  Message 2: { PID: 5001, SeqNum: 1, payload: PaymentInitiated{id: PAY-002} }
  Message 3: { PID: 5001, SeqNum: 2, payload: PaymentInitiated{id: PAY-003} }
```

**Broker maintains a table per producer per partition:**

```
Broker deduplication table:
PID   | Partition | LastSeqNum
5001  | P0        | 2
5001  | P1        | 7
5002  | P0        | 14
```

---

## The Retry Scenario — Step by Step

```
Step 1: Producer sends { PID:5001, SeqNum:5, PAY-001 } to Partition 0
Step 2: Broker stores it, updates table: PID 5001 / P0 = SeqNum 5
Step 3: Broker sends ACK
Step 4: ACK lost in network — producer times out
Step 5: Producer retries: { PID:5001, SeqNum:5, PAY-001 }
Step 6: Broker checks table: "PID 5001 / P0 already at SeqNum 5"
Step 7: Broker IGNORES the duplicate silently
Step 8: Broker sends ACK to producer — "yes received"
Step 9: Producer happy — message stored exactly once ✅
```

**What if sequence number is unexpected?**

```
Expected SeqNum: 6
Received SeqNum: 8 (gap — message 7 lost somewhere)
→ Broker throws OutOfOrderSequenceException
→ Producer knows something went wrong — investigates
```

---

## Idempotent Producer Configuration

```java
Properties props = new Properties();

// Enable idempotence
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

// These are automatically set when idempotence enabled
// but good to be explicit:
props.put(ProducerConfig.ACKS_CONFIG, "all");
props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
// Max 5 in-flight — Kafka can still deduplicate up to 5 
// unacknowledged messages per partition
```

---

## Critical Limitation of Idempotent Producer Alone

Idempotent producer guarantees **exactly-once within a single topic partition.** But it does NOT solve:

```
Producer reads from Topic A
Processes the event
Writes result to Topic B
Commits offset on Topic A

What if crash happens between writing to Topic B and committing offset?
→ On restart: reads same event again from Topic A
→ Writes DUPLICATE to Topic B
→ Commits offset
→ Topic B has duplicate! ❌
```

**This is exactly where Kafka Transactions come in.**

---

# 🔄 Kafka Transactions — Deep Dive

## The Problem Kafka Transactions Solve

In event-driven payment systems, a common pattern is **Read → Process → Write:**

```
Read:    consume PaymentInitiated from payment-events topic
Process: call CBS to execute payment
Write:   publish PaymentCompleted to payment-results topic
Commit:  mark PaymentInitiated as consumed (commit offset)
```

**The problem — these 4 steps are NOT atomic:**

```
Scenario 1 — Crash after Write, before Commit:
Read PaymentInitiated ✅
Call CBS — payment executed ✅
Publish PaymentCompleted ✅
CRASH 💥
Restart → reads PaymentInitiated AGAIN
Calls CBS → payment executed AGAIN → double charge! ❌
Publishes PaymentCompleted AGAIN → duplicate downstream! ❌

Scenario 2 — Crash after Commit, before Write:
Read PaymentInitiated ✅
Call CBS — payment executed ✅
CRASH 💥
Restart → offset already committed → skips this event
PaymentCompleted NEVER published → downstream never notified! ❌
```

---

## What Kafka Transactions Guarantee

> **All writes to output topics AND the offset commit happen atomically — either ALL succeed or NONE do.**

```
Transaction:
  ┌────────────────────────────────────────────────┐
  │ Publish PaymentCompleted to payment-results    │
  │ Commit offset on payment-events                │
  └────────────────────────────────────────────────┘
  Either BOTH happen or NEITHER happens
  No partial state possible
```

---

## How Kafka Transactions Work Internally

### The Transaction Coordinator
Kafka has a special internal broker component — the **Transaction Coordinator** — one per producer, manages transaction state:

```
Transaction state stored in:
__transaction_state internal topic (50 partitions, replicated)

States:
BEGIN → PREPARE_COMMIT → COMMITTED
     → PREPARE_ABORT  → ABORTED
```

### Transaction ID — The Key Concept
Every transactional producer has a **transactional.id** — a stable identifier that survives producer restarts:

```java
props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "payment-processor-1");
```

**Why this matters:**
> *"When producer restarts with the same transactional.id, Kafka automatically fences (kills) any zombie instance of the old producer. This prevents the scenario where an old producer comes back after a crash and continues writing stale events — the 'zombie producer' problem."*

---

## Kafka Transactions — Complete Code Example

### Payment Processing with Exactly-Once

```java
// Setup
Properties props = new Properties();
props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "payment-processor-1");
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
props.put(ProducerConfig.ACKS_CONFIG, "all");

KafkaProducer<String, String> producer = new KafkaProducer<>(props);
KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);

// One-time initialisation
producer.initTransactions();

// Main processing loop
while (true) {
    // Step 1: Read payment events
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

    if (records.isEmpty()) continue;

    try {
        // Step 2: Begin transaction
        producer.beginTransaction();

        for (ConsumerRecord<String, String> record : records) {
            PaymentEvent payment = deserialize(record.value());

            // Step 3: Process payment (call CBS)
            PaymentResult result = cbsAdapter.processPayment(payment);

            // Step 4: Write result to output topic
            // This is inside the transaction — not visible to consumers yet
            producer.send(new ProducerRecord<>(
                "payment-results",
                payment.getAccountId(),        // partition key
                serialize(result)              // payload
            ));

            // Step 5: Write audit event (also inside transaction)
            producer.send(new ProducerRecord<>(
                "payment-audit",
                payment.getAccountId(),
                serialize(new AuditEvent(payment, result))
            ));
        }

        // Step 6: Atomically commit offsets + publish output messages
        // THIS IS THE MAGIC — offset commit is part of the transaction
        Map<TopicPartition, OffsetAndMetadata> offsets = getOffsets(records);
        producer.sendOffsetsToTransaction(offsets, "payment-processor-group");

        // Step 7: Commit transaction — now output messages become visible
        producer.commitTransaction();
        // ✅ PaymentCompleted published + offset committed — atomically

    } catch (Exception e) {
        // Step 8: Abort — rollback everything
        producer.abortTransaction();
        // ✅ Output messages NOT published + offset NOT committed
        // Consumer will re-read and retry the same events
        log.error("Transaction aborted, will retry", e);
    }
}
```

---

## What Consumers See During a Transaction

This is the subtle but critical part — **consumers only see committed transaction messages:**

```
Producer begins transaction
Producer writes PaymentCompleted to payment-results (offset 100)
Producer writes AuditEvent to payment-audit (offset 50)
→ These are in Kafka but NOT YET VISIBLE to consumers

Producer commits transaction
→ NOW PaymentCompleted (offset 100) becomes visible
→ NOW AuditEvent (offset 50) becomes visible

Consumer reads payment-results → sees PaymentCompleted ✅
Consumer reads payment-audit  → sees AuditEvent ✅
```

**What if producer aborts?**
```
Producer begins transaction
Producer writes PaymentCompleted (offset 100)
Producer aborts transaction
→ Kafka writes a special "abort marker" at offset 101
→ Consumer skips offsets 100-101 entirely — never sees aborted message
→ Consumer isolation: read_committed
```

**Consumer configuration for transactions:**
```java
// CRITICAL — consumers must set isolation level
props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
// Default is read_uncommitted — would see aborted messages! ❌
```

---

## Real FS Example — Payment Saga with Exactly-Once

```
Topic: payment-initiated    (input)
Topic: payment-completed    (output)
Topic: payment-audit        (output)

Transactional flow:

T1: BEGIN TRANSACTION
    Read: PaymentInitiated { PAY-001, ACC-123, £500 }
    Process: CBS debit ACC-123 by £500
    Write: PaymentCompleted { PAY-001, SUCCESS } → payment-completed
    Write: AuditRecord { PAY-001, DEBIT, £500, timestamp } → payment-audit
    Commit offset: payment-initiated / P3 = offset 1247
    COMMIT TRANSACTION
    → All 3 things happened atomically ✅

T2 (crash scenario):
    BEGIN TRANSACTION
    Read: PaymentInitiated { PAY-002, ACC-456, £200 }
    Process: CBS debit ACC-456 by £200
    Write: PaymentCompleted { PAY-002, SUCCESS } → payment-completed
    CRASH 💥
    → Transaction coordinator marks T2 as ABORTED
    → PaymentCompleted for PAY-002 never visible to consumers
    → Offset for PAY-002 not committed
    → On restart: PAY-002 reprocessed from beginning
    → CBS call: idempotency key PAY-002 already processed → returns cached result
    → Transaction commits successfully this time ✅
```

---

## Idempotent Producer vs Kafka Transactions — When to Use Which

| Scenario | Use |
|---|---|
| Simple event publishing — fire and forget | Idempotent Producer only |
| Publish to one topic, no offset management | Idempotent Producer only |
| Read from one topic, write to another | Kafka Transactions |
| Read + process + write + commit offset atomically | Kafka Transactions |
| Saga step — consume command, publish result | Kafka Transactions |
| Audit trail — must never miss or duplicate | Kafka Transactions |

---

## Performance Considerations

> *"Kafka Transactions add latency — typically 10–20ms overhead per transaction due to coordinator round-trips. For payment processing at 100K TPS, this is acceptable. For ultra-low latency HFT tick data at microsecond scale — transactions are too expensive. Use idempotent producer only and accept at-least-once with application-level deduplication."*

---

## 🔑 One-Line Summaries for Interviews

> **Idempotent Producer:** *"Assigns PID + sequence numbers to every message — broker deduplicates retries automatically, guaranteeing exactly-once within a single producer session per partition."*

> **Kafka Transactions:** *"Atomically groups multiple writes across topics AND offset commits — either all succeed or all are rolled back, solving the read-process-write consistency problem in event-driven payment systems."*

---

## Summary — The Three Layers of Exactly-Once in Kafka

```
Layer 1 — Idempotent Producer
  → Prevents duplicate messages from producer retries
  → PID + sequence number deduplication at broker

Layer 2 — Kafka Transactions
  → Atomic read + process + write + offset commit
  → Zombie producer fencing via transactional.id

Layer 3 — Consumer isolation.level = read_committed
  → Consumers only see committed transaction messages
  → Aborted transaction messages invisible to consumers

All three layers together = true end-to-end exactly-once ✅
```
---

# 🎤 Kafka Drill — Question 2 of 3

### "You are the Engineering Manager for a **Trade Settlement Platform** at an investment bank. The platform processes trade settlement events — when institutional clients' trades are matched and settled.

**Current Kafka setup:**
- Topic: `trade-settlement-events`
- Partitions: 12
- Replication Factor: 3
- Partition Key: `tradeId`
- Consumer Group: `settlement-processor` — 12 consumer instances
- Processing: each consumer reads a trade event, calls an external **Central Securities Depository (CSD) API** to settle the trade, then publishes a `TradeSettled` event to `trade-settled-events` topic

**Three incidents happen in one week:**

**Incident 1 — Monday:**
The CSD API goes down for 45 minutes. During this time, 50,000 trade settlement events pile up in Kafka. When CSD recovers, your consumers start processing the backlog. However, 3,000 trades are settled **twice** — causing significant financial reconciliation issues with institutional clients.

**Incident 2 — Wednesday:**
A new version of the settlement consumer is deployed. During deployment, a **rebalancing** occurs. For 8 minutes, no trade settlement events are processed. The trading desk is furious — they have SLA commitments to institutional clients.

**Incident 3 — Friday:**
Your monitoring team notices consumer lag on `settlement-processor` group is growing — currently at **2 million events behind.** At current processing rate it will take **6 hours** to catch up. End-of-day settlement cutoff is in 2 hours.

**Four things to answer:**

1. **Incident 1 Root Cause & Fix** — why did 3,000 trades settle twice and how do you prevent it permanently?
2. **Incident 2 Root Cause & Fix** — why did rebalancing take 8 minutes and how do you reduce it to under 30 seconds?
3. **Incident 3 — Immediate Action** — what do you do RIGHT NOW to catch up before the 2-hour cutoff?
4. **Incident 3 — Permanent Fix** — how do you redesign the architecture so this lag situation never happens again?"**

---

> 💡 *Tip 1: Incident 1 — the CSD API was down, consumers were retrying. When CSD recovered, what happened to those retried messages? Think about what was committed and what wasn't during the outage*

> 💡 *Tip 2: Incident 2 — 8 minutes for rebalancing is extremely long. What type of rebalancing causes stop-the-world? What configuration prevents it?*

> 💡 *Tip 3: Incident 3 immediate action — you have 12 consumers and 12 partitions. What's the fastest lever to pull right now?*

> 💡 *Tip 4: Incident 3 permanent fix — think about what causes lag to build up. Is it a consumer throughput problem, a partition problem, or an architecture problem?*

Take your shot! 🎯

Really strong answer! You demonstrated solid Kafka depth across all 3 incidents! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 2

### What you got right
- ✅ **Incident 1** — RPW atomicity correctly identified as root cause
- ✅ **Kafka transactions as fix** — correct
- ✅ **Incident 2** — Stop-the-world rebalancing correctly identified
- ✅ **Cooperative rebalancing fix** — correct
- ✅ **Rolling deployment** — excellent operational thinking
- ✅ **Incident 3** — Consumer throughput correctly identified
- ✅ **Partition + consumer count increase** — correct permanent fix

### Your Score: **7/10** — Good across all incidents! Here's the push to 9/10 💡

---

### What to Strengthen

**Incident 1 — Your confusion on "how transactions stop CSD being called twice"**

This is the most important gap — let me clarify this precisely:

> *"Kafka transactions alone DON'T prevent the CSD API being called twice — that's a separate concern. Here's the full solution:*
>
> *Part 1 — Kafka Transactions prevent duplicate Kafka events:*
> Read trade-settlement-events + Write TradeSettled + Commit offset = atomic. If crash happens, offset not committed → event reprocessed → but Kafka transaction ensures TradeSettled is only published ONCE.*
>
> *Part 2 — Idempotency key prevents CSD API being called twice:*
> The CSD API call is OUTSIDE Kafka — transactions don't cover it. Solution: pass tradeId as idempotency key to CSD API on every call. CSD API: 'I've already settled tradeId T-001 — here's the cached result, not settling again.' This is exactly the pattern from Saga idempotency — external API calls must be idempotent separately.*
>
> *Combined: Kafka transactions handle message-layer exactly-once. Idempotency key handles external API exactly-once. Together = no duplicate settlements."*

```java
try {
    producer.beginTransaction();

    // Idempotency key = tradeId — CSD deduplicates on its side
    CsdResult result = csdApi.settle(trade.getTradeId(), trade);

    producer.send(new ProducerRecord<>(
        "trade-settled-events",
        trade.getTradeId(),
        serialize(result)
    ));

    producer.sendOffsetsToTransaction(offsets, "settlement-processor");
    producer.commitTransaction(); // ✅ atomic

} catch (Exception e) {
    producer.abortTransaction(); // ✅ rollback
}
```

---

**Incident 2 — Add specific configuration values**

You correctly identified cooperative rebalancing — now make it specific:

> *"Three specific fixes:*
>
> *Fix 1 — Switch to CooperativeStickyAssignor:*
> ```java
> props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
>     CooperativeStickyAssignor.class.getName());
> ```
> Only partitions being moved stop — others continue processing during rebalance.*
>
> *Fix 2 — Static group membership:*
> ```yaml
> group.instance.id: settlement-consumer-1  # stable per pod
> session.timeout.ms: 30000                 # 30s before considered dead
> ```
> Consumer restart during deployment → Kafka recognises same consumer returning → no rebalance triggered until session timeout exceeded.*
>
> *Fix 3 — Rolling deployment:*
> Deploy one pod at a time. With static membership + cooperative rebalancing — only 1/12 partitions briefly paused per pod restart. Total disruption: milliseconds per pod, not 8 minutes for entire group."*

---

**Incident 3 — Immediate action was incomplete**

You identified the problem but didn't answer **what to do RIGHT NOW** in the next 2 hours:

> *"Immediate actions — next 30 minutes:*
>
> *Step 1 — Diagnose root cause first:*
> Is CSD API slow/down? Check CSD response times. Is each event taking longer to process than normal? Check consumer processing time metrics.*
>
> *Step 2 — Scale consumers immediately:*
> Current: 12 consumers, 12 partitions — already at max parallelism. To add more consumers I need more partitions first. Emergency partition increase: create new topic `trade-settlement-events-v2` with 30 partitions, dual-publish to both topics, migrate consumers to new topic — 30 consumers now processing.*
>
> *Step 3 — If CSD is the bottleneck:*
> Parallelise CSD calls within each consumer — process multiple trades concurrently per consumer using CompletableFuture. If CSD has rate limits — negotiate emergency limit increase for end-of-day settlement run.*
>
> *Step 4 — Prioritise high-value trades:*
> Not all 2 million events are equal. Triage — process high-value institutional trades first. Implement priority consumer that reads from a separate high-priority partition first.*
>
> *Step 5 — Communicate proactively:*
> Immediately notify trading desk and institutional client relationship managers — give honest ETA, identify which clients are at risk of missing settlement cutoff, escalate to operations for manual settlement of highest-value trades if needed."*

---

**Incident 3 — Permanent fix needs more depth**

> *"Three permanent fixes:*
>
> *Fix 1 — Right-size partitions and consumers:*
> Capacity plan based on peak volume × processing time. If each event takes 50ms to process and peak is 10K events/second — need minimum 500 consumer threads. With 30 partitions and async processing per consumer — achievable.*
>
> *Fix 2 — Async CSD API calls:*
> Current design is synchronous — one CSD call per consumer thread. Switch to async batch processing — each consumer batches 100 trades, calls CSD batch API, publishes results together. Throughput increases 100x.*
>
> *Fix 3 — Consumer lag alerting:*
> Alert when lag exceeds 100K events — not 2 million. At 100K we have time to react. At 2 million we're already in crisis. Set auto-scaling triggers — lag > 50K → add consumer instances automatically.*
>
> ```yaml
> # Kafka consumer lag alerting
> alert: ConsumerLagHigh
> condition: consumer_lag > 100000
> action: page on-call + trigger auto-scale
> ```"*

---

## 🏆 Model Answer Summary (9/10)

> **Incident 1:**
> *"Two-part fix: Kafka transactions make RPW atomic — crash mid-flow rolls back, event reprocessed cleanly. Idempotency key (tradeId) on CSD API prevents double settlement — CSD returns cached result on duplicate call. Transactions handle Kafka layer, idempotency key handles external API layer."*

> **Incident 2:**
> *"Stop-the-world eager rebalancing with 12 consumers all pausing simultaneously. Fix: CooperativeStickyAssignor (only affected partitions pause), static group membership (restarts don't trigger rebalance), rolling deployment (one pod at a time). Result: rebalance impact drops from 8 minutes to seconds."*

> **Incident 3 Immediate:**
> *"Diagnose: CSD bottleneck or consumer throughput? Scale: emergency new topic with 30 partitions + 30 consumers. Prioritise: high-value trades processed first. Communicate: proactive client notification, manual settlement escalation for highest-value trades at risk."*

> **Incident 3 Permanent:**
> *"Right-size: capacity plan partitions × consumers for peak volume. Async CSD: batch API calls — 100x throughput improvement. Alert earlier: lag > 100K triggers auto-scale, not 2 million when already in crisis."*

---

## 📊 Running Scorecard

| Question | Topic | Score | Trend |
|---|---|---|---|
| Q13 — Saga | Loan Origination | 9.5/10 | 🚀 Outstanding |
| Q15 — Patterns | Lloyds Modernisation | 9/10 | 🚀 Exceptional |
| Q16 — Resilience | Payments Cascade | 8.5/10 | 📈 Strong |
| Q17 — Kafka | Payment Design Review | 7.5/10 | 📈 Solid |
| Q18 — Kafka | Trade Settlement | 7/10 | 📈 Good |

---

> 🔑 **Coaching note:** Your biggest gap this question was the **immediate action for Incident 3** — you identified the permanent fix correctly but didn't answer what to do in the next 2 hours before the cutoff. In FS interviews, incident questions always have two parts — the firefight (now) and the fix (permanent). Always answer both explicitly. The other gap — how transactions + idempotency key work together for external APIs — is now clear from the explanation above. These two concepts together are the complete exactly-once story in FS.

---
**You are absolutely right to challenge Step 2 — it was impractical!** Let me own that and give you the real answer. Then I'll go deep on transaction internals.

---

## ✅ Your Correction — 100% Valid

> *"Creating a new topic with 30 partitions doesn't help the 2 million events already sitting on the existing 12-partition topic. Those events are already there — new consumers on a new topic don't touch them."*

You're right. Let me give the **actually practical** immediate actions:

---

## 🔥 Incident 3 — Practical Immediate Actions

### The Real Problem
```
trade-settlement-events: 12 partitions, 12 consumers
2 million events backed up
Each event = 1 CSD API call = ~50ms
12 consumers × 20 events/sec = 240 events/sec
2,000,000 ÷ 240 = 8,333 seconds = ~2.3 hours
Cutoff: 2 hours → you WILL miss it
```

### What Actually Works RIGHT NOW

**Action 1 — Parallelise processing WITHIN each consumer**

Each consumer is processing one event at a time — synchronous. Switch to async concurrent processing within each consumer:

```java
// CURRENT — synchronous, 1 event at a time per consumer
while (true) {
    ConsumerRecords records = consumer.poll(100ms);
    for (record : records) {
        csdApi.settle(record);  // blocks — 50ms each
        commitOffset(record);
    }
}
// Throughput: 20 events/sec per consumer × 12 = 240/sec

// FIXED — async concurrent, 50 events at a time per consumer
while (true) {
    ConsumerRecords records = consumer.poll(100ms);
    List<CompletableFuture> futures = records.stream()
        .map(record -> CompletableFuture.supplyAsync(
            () -> csdApi.settle(record), executorPool))
        .collect(toList());
    CompletableFuture.allOf(futures).join(); // wait for batch
    commitOffsetForBatch(records);
}
// Throughput: 50 concurrent × 12 consumers = 600 events/sec
// 2M ÷ 600 = ~55 minutes ✅ catchable before cutoff
```

---

**Action 2 — Increase partitions on EXISTING topic + add consumers**

> *"You can increase partitions on the existing topic — new partitions are empty initially, but Kafka rebalances existing partition assignments. The key is that existing 12 partitions now have fewer consumers each, freeing threads. But more importantly — adding partitions allows adding more consumer instances beyond 12."*

**Practical caveat you correctly spotted:**

> *"Increasing partitions changes hash(tradeId) % N mapping — existing in-flight events stay on old partitions. New events go to new partitions. During the catch-up window, old partitions still have 2M events — new partitions are empty. So new consumer instances assigned to new partitions sit idle. This is the flaw — it only helps AFTER the backlog is cleared."*

**So the honest answer for immediate action:**
> *"For the 2-hour window — async concurrent processing within existing consumers is the ONLY lever that works immediately without infrastructure changes. New partitions help future throughput, not the existing backlog."*

---

**Action 3 — Triage and manual settlement for highest-value trades**

> *"At investment bank scale, 2 million events are NOT all equal. Top 100 trades by value might represent 80% of the financial exposure. I'd immediately extract the highest-value pending trades from the topic using a dedicated triage consumer, escalate to operations team for manual CSD submission in parallel — bypassing the automated pipeline entirely for the most critical trades."*

---

**Action 4 — Negotiate CSD batch API**

> *"CSD APIs often have batch endpoints — submit 100 trades in one call instead of 1. If the CSD supports batch settlement, switching from single to batch calls gives 10-50x throughput improvement without any infrastructure changes."*

---

## 🔬 Kafka Transactions — Internal Deep Dive

Now the really good stuff — how transactions actually work under the hood.

---

### The 4 Components Involved

```
1. Producer (client)              — initiates and manages transaction
2. Transaction Coordinator        — broker-side component, manages state
3. __transaction_state topic      — persists transaction state
4. Partition Leaders              — actual brokers holding the data
```

---

### The IDs Involved

**PID — Producer ID:**
```
Assigned by Kafka cluster on producer startup
Unique per producer instance
Ephemeral — changes on restart (unless transactional.id set)
Used for: idempotent deduplication (sequence numbers)
```

**Transactional ID:**
```
Set by application developer — stable string
props.put(TRANSACTIONAL_ID_CONFIG, "payment-processor-1")
Survives restarts — same ID across producer restarts
Used for: zombie fencing, transaction continuity
```

**Epoch:**
```
Incremented every time producer with same transactional.id restarts
Old producer epoch < New producer epoch
Broker rejects writes from old epoch → zombie fencing
```

**Transaction Coordinator:**
```
Determined by: hash(transactional.id) % __transaction_state.partitions
Every transactional.id maps to exactly one coordinator broker
That broker manages all transactions for that producer
```

---

### Transaction Flow — Step by Step Internally

```
Step 1: initTransactions()
─────────────────────────
Producer → Transaction Coordinator:
  "I am transactional.id=payment-processor-1, give me PID"

Coordinator:
  Looks up payment-processor-1 in __transaction_state
  Increments epoch: epoch 4 → epoch 5 (fences old producer)
  Assigns new PID: 8821
  Responds: { PID: 8821, epoch: 5 }

Producer now has: PID=8821, epoch=5
Any old producer with epoch 4 → rejected by brokers ✅


Step 2: beginTransaction()
──────────────────────────
Client-side only — no network call to broker
Producer internally sets flag: inTransaction = true
Nothing written to Kafka yet


Step 3: producer.send() — writing messages
──────────────────────────────────────────
Producer → Partition Leader (trade-settled-events / P3):
  "Register: PID 8821, epoch 5 is writing to this partition"

Partition Leader → Transaction Coordinator:
  "FYI: PID 8821 is now writing to trade-settled-events/P3"

Coordinator updates __transaction_state:
  { txnId: payment-processor-1,
    state: ONGOING,
    partitions: [trade-settled-events/P3, payment-audit/P1] }

Messages written to partition log — marked with PID + epoch
BUT marked as UNCOMMITTED — invisible to read_committed consumers


Step 4: sendOffsetsToTransaction()
───────────────────────────────────
Producer → Transaction Coordinator:
  "Also include consumer offset commit in this transaction:
   trade-settlement-events/P7 = offset 14523"

Coordinator writes offset to __consumer_offsets
BUT marks it as part of transaction — not yet effective


Step 5: commitTransaction()
────────────────────────────
Producer → Transaction Coordinator:
  "Commit transaction for PID 8821"

Coordinator writes to __transaction_state:
  { state: PREPARE_COMMIT } ← durable checkpoint

Coordinator → Each Partition Leader involved:
  "Write COMMIT marker to trade-settled-events/P3"
  "Write COMMIT marker to payment-audit/P1"
  "Write COMMIT marker to __consumer_offsets"

Partition Leaders write commit markers
Messages now VISIBLE to read_committed consumers ✅
Offset now EFFECTIVE — consumers resume from here ✅

Coordinator writes to __transaction_state:
  { state: COMMITTED } ← transaction complete


Step 6: abortTransaction()
────────────────────────────
Same as commit but writes ABORT markers
Messages remain in log but SKIPPED by read_committed consumers
Offset NOT committed — consumers reprocess from last committed offset
```

---

### Visual Summary of Transaction State Machine

```
initTransactions()
       ↓
   [EMPTY] ──beginTransaction()──→ [ONGOING]
                                       ↓
                              messages written
                              offsets registered
                                       ↓
                          ┌────────────┴────────────┐
                   commitTransaction()         abortTransaction()
                          ↓                         ↓
                  [PREPARE_COMMIT]          [PREPARE_ABORT]
                          ↓                         ↓
                  write COMMIT markers      write ABORT markers
                          ↓                         ↓
                     [COMMITTED]              [ABORTED]
                          ↓                         ↓
                  messages visible          messages invisible
                  offsets effective         offsets unchanged
```

---

### Who Manages What — Client vs Broker

| Responsibility | Managed By |
|---|---|
| beginTransaction() | **Client only** — local flag, no network call |
| PID + epoch assignment | **Transaction Coordinator (broker)** |
| Transaction state machine | **Transaction Coordinator (broker)** |
| Persisting transaction state | **__transaction_state topic (broker)** |
| Writing uncommitted messages | **Partition Leaders (broker)** |
| Writing commit/abort markers | **Transaction Coordinator → Partition Leaders** |
| Filtering uncommitted messages | **Partition Leaders** (for read_committed consumers) |
| Sequence number tracking | **Both** — producer assigns, broker verifies |

---

## 🔥 What Happens if Kafka Goes Down?

This is the critical question — let's cover every scenario:

---

### Scenario 1 — Single Broker Goes Down (Normal — Handled Automatically)

```
Broker 2 (hosts P1 leader) crashes
         ↓
ZooKeeper / KRaft detects: Broker 2 heartbeat lost
         ↓
Controller triggers leader election for P1
         ↓
Broker 3 (has P1 replica, in sync) elected as new P1 leader
         ↓
Producers + consumers reconnect to Broker 3 for P1
         ↓
Zero data loss — replica was in sync (ISR)
Time to recover: seconds
```

**In-flight transaction during broker failure:**
```
Producer mid-transaction, P1 leader (Broker 2) crashes
         ↓
Producer gets connection error
         ↓
Producer aborts transaction
         ↓
New leader (Broker 3) has no uncommitted messages
         ↓
Producer retries — begins new transaction on Broker 3
→ Exactly-once maintained ✅
```

---

### Scenario 2 — Transaction Coordinator Broker Goes Down

```
Transaction Coordinator for payment-processor-1 crashes
         ↓
New Transaction Coordinator elected
(hash(transactional.id) now maps to surviving broker)
         ↓
New Coordinator reads __transaction_state topic
(replicated — survives broker failure)
         ↓
Finds transaction in PREPARE_COMMIT state:
  → Completes the commit (writes commit markers)
  → Transaction committed even though coordinator crashed ✅

Finds transaction in ONGOING state:
  → Times out after transaction.timeout.ms (default 1 min)
  → Aborts the transaction
  → Producer retries on new coordinator ✅
```

---

### Scenario 3 — Majority of Brokers Down (Cluster Failure)

```
Cluster: 3 brokers, replication factor 3, min.insync.replicas=2
Brokers 2 + 3 crash → only Broker 1 alive
         ↓
Partition leaders on Broker 1:
  min.insync.replicas=2 not met (only 1 broker alive)
         ↓
Producers: NotEnoughReplicasException thrown
  → Writes rejected — cannot guarantee durability ❌
  → Producer retries but can't proceed
         ↓
Consumers: can still read existing committed messages ✅
  → Reads continue from Broker 1

Recovery:
  Brokers 2 + 3 restart
  → Replicate from Broker 1 (catch up)
  → ISR restored → writes resume
  → Zero data loss ✅
```

---

### Scenario 4 — Complete Cluster Down (All Brokers)

```
All brokers crash simultaneously
         ↓
Producers: cannot connect — buffer messages locally
  → If buffer fills (buffer.memory exhausted) → block or drop
  → Transactions cannot proceed — abort after timeout

Consumers: cannot connect — stop processing
  → Last committed offset preserved in __consumer_offsets
  → On recovery: resume from last committed offset ✅

Data already committed to disk:
  → Partition log files on each broker's disk
  → Survive broker restart
  → On restart: data available again ✅

In-flight uncommitted transactions:
  → Transaction timeout fires
  → Coordinator aborts on recovery
  → Producer retries ✅

Potential data loss:
  → Messages in OS page cache NOT yet flushed to disk
  → Hard power failure (not graceful shutdown) could lose these
  → Mitigation: flush.messages=1 (flush every message to disk)
  → Tradeoff: significant performance cost
  → FS standard: rely on replication (3 brokers) rather than
    disk flush — losing all 3 brokers simultaneously is
    extremely unlikely in a well-designed multi-AZ cluster
```

---

### Scenario 5 — ZooKeeper / KRaft Goes Down

```
ZooKeeper ensemble down (all 3 ZK nodes crash)
         ↓
Existing Kafka brokers continue serving
  → Producers: continue writing to existing partition leaders ✅
  → Consumers: continue reading ✅
  → No new leader elections possible
  → No new topic creation possible
  → No broker additions possible
         ↓
If a partition LEADER crashes while ZK is down:
  → No new leader can be elected
  → That partition becomes unavailable ❌
  → Other partitions unaffected ✅

Recovery:
  ZooKeeper restarts → brokers reconnect
  Controller re-elected → leader elections resume
  Normal operations restored
```

**KRaft advantage here:**
> *"With KRaft, metadata is managed within Kafka itself — no ZooKeeper dependency. Metadata quorum (3 controller nodes) uses Raft consensus. Losing 1 of 3 controller nodes → still functional. This is why Kafka is migrating to KRaft — eliminates ZooKeeper as a separate failure point."*

---

### FS Kafka High Availability Design — Summary

```
To survive any single failure:
  ✅ Replication factor: 3
  ✅ min.insync.replicas: 2
  ✅ acks: all
  ✅ Multi-AZ broker deployment (Broker 1 in AZ-a, 2 in AZ-b, 3 in AZ-c)
  ✅ ZooKeeper ensemble: 3 or 5 nodes (or KRaft with 3 controllers)
  ✅ Idempotent producer + Kafka transactions
  ✅ Manual offset commit on consumers

This configuration:
  → Tolerates 1 broker failure: zero data loss, automatic recovery
  → Tolerates 1 AZ failure: zero data loss, automatic recovery
  → Tolerates complete cluster failure: zero data loss on restart
    (potential loss of page-cache-only data without disk flush)
```

---

## 🔑 Key Takeaways

| Question | Real Answer |
|---|---|
| **Immediate scaling for backlog** | Async concurrent processing within consumers — not new topics |
| **Transaction managed by** | Coordinator (broker) for state, client framework for flow control |
| **IDs used** | PID (ephemeral), Transactional ID (stable), Epoch (fence zombies) |
| **Single broker failure** | Automatic leader election, zero data loss with ISR |
| **Coordinator failure** | New coordinator reads __transaction_state, completes or aborts |
| **Full cluster failure** | Data safe on disk, resume from committed offsets on restart |
| **ZooKeeper failure** | Existing operations continue, new elections blocked |

---

Great instinct — you're right, dual-write to hot and warm simultaneously creates unnecessary coupling. Let me re-ask the full question with enriched sub-questions based on everything we've discussed!

---

# 🎤 Real-Time Account Statement Service — Full Design Question

### "You are the Engineering Manager at a UK Retail Bank. Design a **Real-Time Account Statement Service** from scratch.

**Functional Requirements:**
- Every debit and credit on every customer account captured as an event
- Events processed in **strict chronological order per account**
- Processed events build a **real-time account statement** readable on mobile app
- Every event stored for **7 years** for FCA compliance — immutable, tamper-proof
- GDPR — customer PII must be **tokenised** before storing anywhere

**Non-Functional Requirements:**
- **5 million active accounts**
- Peak: **500,000 events per second**
- Statement read latency: **under 50ms**
- **99.999% availability**
- UK customer data stays in UK, EU customer data stays in EU
- UK customer travelling to EU must still be served correctly

---

## The board asks you 8 specific questions:

**Q1 — Jurisdiction & Routing:**
How do you separate UK and EU stacks? How does a UK customer travelling to Frankfurt get routed correctly? What happens if the entire London region goes down — can you failover to EU?

**Q2 — Kafka Architecture:**
How many partitions, what partition key, how many consumers? How do you guarantee strict ordering per account at 500K events/second? How is your Kafka cluster deployed for 99.999% within each jurisdiction?

**Q3 — Exactly-Once Guarantee:**
Walk me through your three-layer exactly-once strategy. What happens if the consumer crashes between processing an event and committing the offset?

**Q4 — Tokenisation:**
Which fields do you tokenise, which approach (FPE vs vault), which service, and where in the flow does tokenisation happen? What happens if the token vault goes down?

**Q5 — Storage Architecture:**
You have 500K events/second and need 7-year retention. You cannot store everything in Cassandra forever. How do you design your storage tiers — what goes where, how does data move between tiers, and how do you ensure the 50ms read SLA is maintained? What is WORM and how does it work?

**Q6 — GDPR Erasure:**
A customer requests deletion of all their data. You have events in Kafka, Cassandra and S3 — all append-only or immutable. How do you honour the right to erasure without violating FCA 7-year retention?

**Q7 — Consumer Architecture & Read SLA:**
Design the full consumer group architecture — how many groups, what does each do? How do you achieve under 50ms for statement reads? How does pagination work?

**Q8 — 99.999% Availability:**
99.999% = 5.26 minutes downtime per year. Walk through your failure scenarios — single broker, single AZ, single region — with detection time, recovery time and data loss for each. How do you handle scheduled deployments without breaching the SLA?"**

---

> 💡 *This is a 30-minute Director/Principal level question. Take your time, answer each part thoroughly. Use your Lloyds experience where relevant. Think practically — not just theoretically.*

Really strong answer! You've clearly absorbed everything from our sessions. Here's your detailed feedback 👇

---

## ✅ Feedback — Full Design Question

### What you got right
- ✅ **Multi-regional foundation** — UK (London+Belgium), EU (Ireland+Frankfurt) — correct
- ✅ **Full stack per jurisdiction** — CDN → ALB → API GW → ILB → Compute → DB — complete
- ✅ **Akamai jurisdiction-based routing** — JWT claim for authenticated, geo for login — excellent
- ✅ **Option 1 simplicity** — direct consumer from payment-settled-topic — pragmatic
- ✅ **Three-layer exactly-once** — idempotent producer + consumer dedup by eventId — correct
- ✅ **AccountId partition key** — ordering per account — correct
- ✅ **Crash before offset commit** — idempotency check saves us — precisely correct
- ✅ **Encryption before publishing** — PII never in plaintext in Kafka — correct
- ✅ **Parallel consumer threads** — ordering via timestamp sort on read — smart insight
- ✅ **Cassandra as operational DB** — correct choice
- ✅ **Offline archival process** — hot → warm → cold — correct approach
- ✅ **Two consumer groups** — Cassandra writer + S3 writer — excellent
- ✅ **GDPR crypto shredding** — delete key → data unreadable — correct
- ✅ **Paginated API** — 25 records, timestamp-based cursor — correct
- ✅ **Redis cache** — correct introduction
- ✅ **Blue-green/canary deployments** — correct zero-downtime approach

### Your Score: **8/10** — Excellent! Here's the push to 9/10 💡

---

## Gaps to Close

### Gap 1 — Consumer Throughput Maths Was Wrong

You correctly identified something was off:

> *"10 threads × 1 record per 50ms = 200 events/second per consumer. 500K ÷ 200 = 2,500 consumers — that's too many."*

**The correct calculation:**

```
The 50ms processing time assumption is wrong for this use case.

What each consumer thread actually does:
1. Deserialise event:           ~1ms
2. Idempotency check (Cassandra): ~5ms (async, non-blocking)
3. Cassandra write:              ~5ms (async, non-blocking)
4. Total per event:             ~11ms

With async non-blocking I/O (not blocking thread per operation):
→ Thread doesn't wait for Cassandra — fires async, moves to next
→ 10 async threads can process 10 events concurrently
→ Each event ~11ms → 10 threads = 10 ÷ 0.011 = ~900 events/second

Revised calculation:
500,000 ÷ 900 = ~556 consumers needed
Round up with buffer: 600 consumers
Partitions: 600 (1 consumer per partition)

That's still a lot — is there a better way?

Better approach — increase threads per consumer:
→ 50 async threads per consumer
→ 50 ÷ 0.011 = ~4,500 events/second per consumer
→ 500,000 ÷ 4,500 = ~112 consumers
→ Round up: 150 partitions, 150 consumers ✅

Key insight:
→ With async non-blocking processing, thread count is not
  the bottleneck — Cassandra throughput is
→ Cassandra can handle 500K writes/second across cluster
→ Right-size by load testing, not pure maths
→ Start: 150 partitions, 150 consumers, 50 async threads each
→ Monitor consumer lag → scale if needed
```

---

### Gap 2 — Write-Through Cache — Wrong Pattern Here

You suggested write-through for Redis — this needs correction:

> *"Write-Through means: write to Redis first, then DB. Problem — if Redis write succeeds but DB write fails, we have data in cache but not in DB. For financial statement data this is dangerous — customer sees transaction in app but it's not in DB."*

**Correct pattern:**

```
Write path (consumer):
→ Write to Cassandra FIRST (source of truth)
→ On successful Cassandra write → publish invalidation event
→ Redis cache invalidated (NOT written to directly)

Read path (Statement API):
→ Check Redis first (Cache-Aside)
→ Cache hit: return from Redis (~1ms)
→ Cache miss: read from Cassandra → populate Redis → return

Why Cache-Aside not Write-Through:
→ Cassandra is source of truth — always
→ Redis is a read acceleration layer — never source of truth
→ If Redis fails → reads still work from Cassandra
→ If Redis has stale data → worst case: customer sees
  slightly old statement → acceptable
→ If DB fails → we fail the write — never silently succeed
```

---

### Gap 3 — Hot → Warm Archival Process — Needs More Detail

You correctly identified offline archival process — now make it specific:

```
Hot → Warm archival:

Option A: TTL-based (simplest)
→ Hot Cassandra table: TTL = 6 months
→ Warm Cassandra table: TTL = 3 years
→ Consumer writes to HOT table only
→ Separate archival job runs nightly:
   SELECT events WHERE event_ts < NOW() - 6 months
   FROM hot_statements
   INSERT INTO warm_statements
   DELETE FROM hot_statements
→ Problem: archival job reads + writes at scale — expensive

Option B: Consumer dual-writes hot + warm (your concern — coupling)
→ You correctly rejected this ❌

Option C: Separate archival consumer (BEST — your insight!)
→ Third consumer group: archival-mover
→ Reads from payment-settled-topic (independent offset)
→ Writes directly to warm table (different TTL)
→ Hot consumer writes hot only
→ No coupling between hot and warm writes ✅
→ If warm fails → hot write unaffected ✅
→ This is exactly your two-consumer-group insight applied to tiers

Hot consumer group:    writes to Cassandra hot (TTL 6 months)
Warm consumer group:   writes to Cassandra warm (TTL 3 years)
Archive consumer group: writes to S3 WORM (7 years)
→ All three read from same Kafka topic independently
→ Each at their own pace
→ Each failure completely isolated ✅
```

---

### Gap 4 — Q1 Partially Answered — London Region Down Scenario

You mentioned active-active but didn't explicitly answer:

> *"Can UK failover to EU if London goes down? Answer: NO — UK data cannot go to EU jurisdiction. This is the critical constraint.*
>
> *If ALL of London goes down AND Belgium goes down simultaneously — UK service is unavailable. We accept this as an extreme edge case. The SLA calculation: London + Belgium both failing simultaneously is a probability of P(London fails) × P(Belgium fails) ≈ 0.001% × 0.001% = essentially zero. The 99.999% SLA is maintained by active-active London + Belgium — not by cross-jurisdiction failover."*

---

### Gap 5 — 99.999% Failure Scenarios Not Explicitly Walked Through

The board asked for specific detection + recovery times:

```
Failure scenario walk-through:

1. Single Kafka broker fails:
   Detection: ZooKeeper/KRaft heartbeat timeout: 5 seconds
   Recovery:  Leader election: 10 seconds
   Data loss: None (ISR replicas take over)
   Total:     15 seconds ✅

2. Single AZ fails (London AZ-a):
   Detection: Route 53 health check: 20 seconds
   Recovery:  Traffic shifts to AZ-b, AZ-c: 30 seconds
   Data loss: None (multi-AZ replication)
   Total:     ~50 seconds ✅

3. Entire London region fails:
   Detection: Route 53 health check: 20 seconds
   Recovery:  DNS failover to Belgium: 30 seconds
   Data loss: Near-zero (Belgium replicas slightly behind)
   Total:     ~50 seconds ✅ within 5-minute budget

4. Consumer pod crashes:
   Detection: K8s liveness probe: 10 seconds
   Recovery:  Pod restart: 20 seconds
   Data loss: None (uncommitted offset replayed)
   Total:     ~30 seconds ✅

5. Scheduled deployment (blue-green):
   Detection: N/A — planned
   Recovery:  Zero downtime — blue-green per region
   Data loss: None
   Total:     0 seconds downtime ✅
   Strategy:  Deploy Belgium first → validate → deploy London
              → If London deployment fails → Belgium serving all UK traffic
              → Roll back London → investigate
```

---

### Gap 6 — Token Vault High Availability Not Addressed

> *"If token vault goes down — what happens? Every API response requires detokenisation. Every event publish requires tokenisation. Vault is now a critical path dependency.*
>
> *Solution:*
> *1. Vault HA cluster: 3 nodes per region, Raft consensus — tolerates 1 node failure*
> *2. Token cache: detokenised values cached in Redis with short TTL (1 hour) — if vault is temporarily down, cached tokens serve recent requests*
> *3. Circuit breaker on vault: if vault down, API returns last cached statement with staleness indicator — never a hard error for reads*
> *4. For writes: if vault down during tokenisation — payment service cannot publish to Kafka — payment fails explicitly — never publishes unencrypted PII as fallback"*

---

## 🏆 Your Corrected Complete Answer (9/10 level)

Let me summarise what your answer looks like with all gaps filled:

---

### Q1 — Jurisdiction & Routing ✅
> *"UK: London (primary) + Belgium (secondary) — active-active, no EU fallback. EU: Ireland (primary) + Frankfurt (secondary) — active-active, no UK crossover. Akamai routes by JWT jurisdiction claim for authenticated users. Login page routes by IP geolocation. London complete failure → Belgium serves all UK traffic. UK cannot failover to EU — data residency hard boundary."*

### Q2 — Kafka Architecture ✅
> *"150 partitions, accountId partition key, 150 consumers with 50 async threads each. Kafka cluster: multi-AZ within jurisdiction. UK: London + Belgium observers. EU: Ireland + Frankfurt active-active in same cluster. RF=3, min.insync.replicas=2, acks=all."*

### Q3 — Exactly-Once ✅
> *"Layer 1: idempotent producer. Layer 2: consumer-side eventId check in Cassandra before write. Layer 3: if crash before offset commit — event redelivered — idempotency check catches duplicate — discard. DLQ for events failing after 3 retries."*

### Q4 — Tokenisation ✅
> *"FPE for account numbers + sort codes (format-preserving). Random tokenisation for names + addresses. HashiCorp Vault. Tokenisation happens in Payment Service before publishing. Vault HA cluster + Redis token cache as fallback. Vault down → circuit breaker → cached tokens serve reads → writes fail explicitly."*

### Q5 — Storage Architecture ✅
> *"Three independent consumer groups reading same Kafka topic: hot consumer → Cassandra hot (6 months), warm consumer → Cassandra warm (3 years), archive consumer → S3 WORM (7 years). Each group independent — failure isolated. No coupling between tiers. S3 WORM: Object Lock COMPLIANCE mode — 7-year immutable retention, tamper-proof, Athena queryable."*

### Q6 — GDPR Erasure ✅
> *"Crypto shredding: delete customer's encryption key from Vault. All encrypted tokens in Kafka, Cassandra, S3 become unreadable without key. Data physically present but effectively erased. FCA retention honoured — anonymised records remain. Respond to customer within 30 days."*

### Q7 — Consumer Architecture & Read SLA ✅
> *"Three consumer groups: hot-writer, warm-writer, archive-writer — all independent. Read API: Redis L1 (Cache-Aside, 1ms) → Hot Cassandra L2 (10ms) → Warm Cassandra L3 (20ms) → S3 Athena L4 (2-5 seconds). Paginated: 25 records, timestamp cursor. Total read latency: ~44ms ✅"*

### Q8 — 99.999% Availability ✅
> *"Single broker: 15s recovery. Single AZ: 50s. Single region: 50s. Consumer crash: 30s. Deployment: zero downtime via blue-green, Belgium first then London. All within 5-minute annual budget."*

---

## 📊 Final Scorecard

| Question | Score | Notes |
|---|---|---|
| Q1 — Jurisdiction & Routing | 8/10 | Good — failover boundary needed explicit statement |
| Q2 — Kafka Architecture | 8/10 | Maths off but approach right |
| Q3 — Exactly-Once | 9/10 | Very strong |
| Q4 — Tokenisation | 7/10 | Vault HA not addressed |
| Q5 — Storage Architecture | 8.5/10 | Three independent consumers — excellent insight |
| Q6 — GDPR Erasure | 9/10 | Crypto shredding — correct |
| Q7 — Consumer + Read SLA | 8/10 | Cache pattern wrong (write-through vs Cache-Aside) |
| Q8 — 99.999% | 7/10 | Mentioned but not walked through explicitly |
| **Overall** | **8/10** | **Strong Director-level answer** |

---

> 🔑 **Key habit to build for interviews:** When the board asks "walk me through failure scenarios" — always give **detection time + recovery time + data loss** for each scenario explicitly. Numbers signal that you've operated these systems in production, not just designed them on paper.

---
