# Saga Pattern: Complete Guide

## Executive Summary

**Saga** is a sequence of local transactions coordinated using events, with compensating actions on failure.

It solves the problem: **How do we handle business workflows that span multiple bounded contexts, services, or aggregates without ACID transactions?**

---

## 🎯 Why Saga Exists (The Core Problem)

### The Problem

You **cannot** use [ACID transactions](https://github.com/ayushj158/learning/blob/main/Microservices/Architecture/saga.md#acid-transactions--revision-notes) across microservices.

**In banking systems:**
- Each service owns its data
- Failures are normal
- Partial success is dangerous

### Example Transaction

```
Customer withdraws money from Savings Account

Steps:
1. Savings Service: Deduct $100 from account
2. Ledger Service: Post entry to accounting ledger
3. Notification Service: Send confirmation to customer

These are:
✅ Different aggregates
✅ Different bounded contexts
✅ Different services
❌ Cannot use single DB transaction
❌ Must expect independent failures
```

### How Saga Solves It

```
Saga coordinates by:
1. Breaking transaction into local transactions
2. Each service commits independently
3. Failure triggers compensating actions
4. Not a rollback — a business correction
```

---

## ❌ What a Saga Is NOT

| Item | Why |
|------|-----|
| ❌ Distributed transaction (2PC) | No global lock, not atomic across services |
| ❌ Workflow engine (necessarily) | Can be simple choreography or custom orchestration |
| ❌ Synchronous | Asynchronous coordination |
| ❌ Rollback-based | Compensation ≠ rollback |
| ❌ Thread-based waiting | Event and timeout driven |

---

## 🔑 Key Concepts (Must Know)

### Local Transaction

```
A single service's DB commit
• Must be atomic WITHIN the service
• Independent from other services
• Example: Savings service debits account
```

### Compensation

```
A new business action to undo a previous step
• NOT a technical rollback
• A legitimate business action
• Example: Credit money back after ledger fails
```

### Eventual Consistency

```
System converges to correct state over time
• Acceptable for reads
• NOT acceptable for critical invariants
• Requires reconciliation processes
```

---

## 📊 Choreography vs Orchestration

### 1️⃣ Choreography Saga (Event-Driven)

**Definition:** Services react to events without a central coordinator.

#### Example Flow: Savings → Ledger → Notification

```
1. Savings Service
   └─ MoneyWithdrawn event → Database commit
       ↓
2. Ledger Service (listening)
   └─ Consumes MoneyWithdrawn
   └─ LedgerPosted event → Database commit
       ↓
3. Notification Service (listening)
   └─ Consumes LedgerPosted
   └─ NotificationSent event → Database commit
```

#### Pros ✅
- Loose coupling
- No central component
- Simple for 2–3 steps

#### Cons ❌ (Critical)

| Con | Impact |
|-----|--------|
| **No clear owner** | Who is responsible for the workflow? |
| **Hard to debug** | Hidden flow, scattered logic |
| **Hidden coupling** | Services tightly coupled via events |
| **Poor auditability** | No single place to trace the flow |
| **Flow is invisible** | Team doesn't know full story |
| **Failure handling fragmented** | Each service handles failures differently |

#### Failure Handling Problem

```
Scenario: Ledger fails
├─ Savings withdraws money
├─ Ledger fails
├─ Notification never runs

Questions:
├─ Who triggers compensation?
├─ Who decides when to stop retrying?
├─ Who knows saga failed?

In choreography:
├─ Each service guesses
├─ Logic spreads everywhere
├─ Compensations become inconsistent
└─ → CHAOS
```

#### Hidden Coupling Problem

```
Even though services don't call each other:
├─ They depend on event order
├─ They depend on event semantics
├─ They break if events change

Result: Distributed monolith via events
```

#### Event Explosion

```
As flows grow:
├─ More events
├─ More listeners
├─ More unintended side effects

Example:
├─ AML starts listening to MoneyWithdrawn
├─ Risk starts listening
├─ Analytics starts listening
│
└─ One event change → Many services break
```

#### When to Use Choreography

```
✅ No money movement
✅ No compensation required
✅ Fire-and-forget side effects

Examples:
├─ Notifications
├─ Analytics
├─ Cache updates
```

---

### 2️⃣ Orchestration Saga (Centralized)

**Definition:** A Saga Orchestrator explicitly controls the workflow.

#### Architecture

```
Orchestrator Service
├─ Receives: WithdrawMoney
│   ├─ Sends: PostLedgerEntryCommand
│   │   └─ Listens for: LedgerPosted | LedgerFailed
│   ├─ Sends: SendNotificationCommand
│   │   └─ Listens for: NotificationSent | NotificationFailed
│   └─ Marks saga: COMPLETED | FAILED
└─ Triggers compensation on failure
```

#### Orchestrator Responsibilities

- ✅ **Sends commands** → Expects responses
- ✅ **Persists saga state** → Survives crashes
- ✅ **Sets deadlines** → Detects silence
- ✅ **Triggers compensation** → On explicit failure or timeout

- ❌ **Never blocks threads** → Deadlines in DB, not memory
- ❌ **Never assumes services alive** → Expects failures
- ❌ **Never waits indefinitely** → Always has timeout


#### Pros ✅

| Pro | Benefit |
|-----|---------|
| **Clear ownership** | One place owns business workflow |
| **Centralized retries** | All logic in one component |
| **Centralized compensation** | Consistent failure handling |
| **Easy observability** | Single place to trace |
| **Strong audit trail** | "What failed? Why? What was done?" |

#### Cons ❌

| Con | Note |
|-----|------|
| **Extra component** | Orchestrator must be built |
| **Orchestrator can grow complex** | As flows grow, complexity increases |
| **Slightly tighter coupling** | But for critical money flows, acceptable |

#### When to Use Orchestration

```
✅ Money movement
✅ Regulatory impact
✅ Multi-step workflows
✅ Compensation required
✅ Clear SLA needed

Examples:
├─ Payments
├─ Account opening
├─ Funds transfer
├─ Ledger posting
```

---

## 📋 Choreography vs Orchestration: Side-by-Side

| Dimension | Choreography | Orchestration |
|-----------|--------------|---------------|
| **Control** | Distributed | Central |
| **Visibility** | Low | High |
| **Failure handling** | Fragmented | Centralized |
| **Coupling** | Hidden (dangerous) | Explicit (acceptable) |
| **Auditability** | Weak | Strong |
| **FS suitability** | ⚠️ Limited | ✅ Preferred |
| **Use for money** | ❌ No | ✅ Yes |

---

## 🔄 Saga + CQRS + DDD Integration

How Saga fits into the larger architectural picture:

| Concept | Role |
|---------|------|
| **Aggregate** | Executes local transaction |
| **Command** | Step in saga (e.g., PostLedgerEntryCommand) |
| **Event** | Triggers next step (e.g., LedgerPosted event) |
| **Compensation** | Reverse command (e.g., CreditAccountCommand) |
| **Saga** | Coordinates the entire flow |

---

## 🚨 Failure Scenarios (Where Most Fail)

### Scenario 1️⃣: Ledger Service is DOWN (Never Consumes Command)

#### Timeline
```
T0  Orchestrator sends PostLedgerEntryCommand
T1  Ledger is down, does not consume
T2  No success event
T3  No failure event
T∞  Silence
```

#### Orchestrator Behavior

```
1. Saga state = WAITING_FOR_LEDGER
2. Deadline expires (e.g., 30 seconds)
3. Orchestrator treats SILENCE as FAILURE
4. Triggers compensation: CompensateWithdrawal
```

#### Detection Mechanism

```
Timeout can be implemented via:
├─ Delayed messages (message sent, processed later)
├─ Schedulers (poll DB for expired sagas)
└─ Both (delays for short, schedulers for long)
```

#### Key Insight

```
🔑 SILENCE AFTER TIMEOUT = FAILURE
This is the most important Saga concept.
```

---

### Scenario 2️⃣: Ledger Service Emits FAILURE Event

#### Timeline
```
T0  Orchestrator sends PostLedgerEntryCommand
T1  Ledger consumes command
T2  Validation error / DB error
T3  Ledger emits LedgerPostFailed event
```

#### Orchestrator Behavior

```
1. Immediately receives failure event
2. Skips waiting (no timeout needed)
3. Triggers compensation: CompensateWithdrawal
4. Marks saga as FAILED
```

#### Why This Is Clean

```
✅ Explicit business failure
✅ No ambiguity
✅ Fast recovery
✅ Clear causality
```

---

### Scenario 3️⃣: Ledger Crashes BEFORE Responding

#### Timeline (Very Realistic)
```
T0  Orchestrator → PostLedgerEntryCommand
T1  Ledger consumes command
T2  Ledger writes to DB ✅ (SUCCESS)
T3  Ledger crashes BEFORE emitting event ❌
T4  Orchestrator hears nothing
T5  Timeout expires
```

#### The Dilemma

```
At T5, what does the orchestrator know?
✅ Command was sent
❌ No response received
❌ Deadline exceeded

The truth is UNKNOWN:
  → Did ledger post succeed?
  → Did it fail?
  → Does it matter?
```

#### How Orchestrator Reacts

```
🔑 RULE: Orchestrator does NOT guess.
           It moves forward DETERMINISTICALLY.

Actions:
1. Set SagaState = FAILED
2. Trigger Compensation: CompensateWithdrawalCommand
3. Savings credits money back
4. Emit WithdrawalCompensated event
5. Customer balance is now CORRECT (from customer perspective)
```

#### The Semantic Inconsistency Problem

```
Possible reality:
├─ Ledger DB write DID succeed
├─ Orchestrator never heard back
└─ Now we have:
    ├─ Savings refunded ✅
    ├─ Ledger entry exists ✅
    └─ System is consistent, but saga state differs

This is called SEMANTIC INCONSISTENCY.
```

#### How Banks Handle This

**Strategy A — Ledger is Eventually Reconciled (MOST COMMON)**
```
1. Ledger remains source of accounting truth
2. Reconciliation jobs detect orphan entries
3. Correction entries posted later
4. Audit trail preserved

→ Ledger is NOT immediately compensated
→ Very common in banking
```

**Strategy B — Explicit Ledger Compensation (More Complex)**
```
1. Orchestrator also sends: ReverseLedgerEntryCommand
2. Ledger posts reversal entry
3. Both debits and credits recorded

→ More complex
→ Requires strong idempotency
→ Requires strong ordering
→ Used only when real-time consistency mandatory
```

#### Interview-Ready Line

```
"Compensation is business-specific. We don't assume technical
rollback across services. In many cases, ledger consistency is
restored via reconciliation rather than immediate compensation."
```

---

### Scenario 4️⃣: Ledger Emits SUCCESS AFTER Orchestrator Deadline

#### Timeline (Most Subtle Case)
```
T0  Orchestrator → PostLedgerEntryCommand
T1  Ledger is slow / partitioned
T2  Orchestrator timeout expires
T3  Orchestrator compensates Savings
T4  Ledger finally emits LedgerEntryPosted event
```

#### What Orchestrator Does

```
1. Loads saga state from DB
2. Finds: SagaState = COMPENSATED / FAILED
3. Applies rule: Saga state is IMMUTABLE once terminal
4. Therefore: Ignores the late success event
5. Optional: Log warning or raise alert
```

#### Why Ignore?

```
❌ Reopening saga would violate determinism
❌ Customer balance already corrected
❌ Audit trail must be stable
```

#### Should Orchestrator Now Compensate Ledger?

```
Common FS Practice:
❌ Do NOT auto-compensate immediately
✅ Let reconciliation handle it

Why:
├─ Ledger systems prefer explicit correction flows
├─ Financial audit requires traceability
├─ Late reversals can be dangerous
```

#### Very Important Rule (Memorize)

```
🔑 Sagas converge by DESIGN, not by perfect coordination.

Late events are:
├─ Ignored by saga state machine
├─ Handled by reconciliation if needed
└─ Never cause saga to re-open
```

---

## 📊 Orchestration Summary Table

| Failure | Detection | Action |
|---------|-----------|--------|
| **Ledger down** | Timeout | Compensate |
| **Ledger emits failure** | Event | Compensate |
| **Ledger silent (crash)** | Timeout | Compensate |
| **Late success** | State check | Ignore |
| **Duplicate events** | Idempotency key | Ignore |
| **Orchestrator crash** | Persistent state | Resume |

---

## 🔀 Hybrid Pattern (Mature Systems)

Very mature systems use **both** choreography and orchestration:

```
Core Flow (Orchestrated):
├─ Withdraw from Savings ✅
├─ Post to Ledger ✅
└─ Clear ownership, full auditability

Side Effects (Choreographed):
├─ Send notification (event-driven)
├─ Update analytics (event-driven)
├─ Update cache (event-driven)
└─ Loose coupling, simple

Benefits:
✅ Critical path orchestrated
✅ Side effects decoupled
✅ Best of both worlds
```

---

## ✅ How to Choose: Decision Matrix

### Use Choreography When:

```
✅ Flow is simple (2–3 steps)
✅ No money movement
✅ No compensation required
✅ Fire-and-forget use cases

Examples:
├─ Notifications
├─ Analytics
├─ Cache updates
```

### Use Orchestration When:

```
✅ Money is involved
✅ Regulatory impact
✅ Multi-step workflow
✅ Compensation required
✅ Clear SLA needed

Examples:
├─ Payments
├─ Account opening
├─ Funds transfer
├─ Ledger posting
```

---

## 🔑 Distributed Systems Principle (Critical)

```
Distributed systems do NOT wait forever.
They progress based on:
├─ Time (timeouts)
├─ State (saga state in DB)
└─ Events (explicit feedback)

In distributed systems, WAITING FOREVER IS A BUG.

Progress is driven by:
├─ Events + Timeouts + Persisted State
└─ NOT by blocking or health checks
```

---

## 🚨 Common Traps (Anti-Patterns)

| Trap | Why It's Wrong | Correct Approach |
|------|----------------|------------------|
| ❌ "Choreography always better (loose coupling)" | Hides failures, doesn't scale | Choose based on criticality |
| ❌ "Orchestration always bad (centralized)" | False binary | Orchestrate critical path |
| ❌ "Use 2PC for safety" | Doesn't tolerate failures | Use Saga with compensation |
| ❌ "Health checks tell us what succeeded" | They don't; they check infrastructure | Use business events + timeouts |
| ❌ "Block/sleep in Saga logic" | Loses state on crash, doesn't scale | Use events & deadlines in DB |
| ❌ "Undo everything on failure" | Corrupts state, violates audit | Selective, business-aware compensation |
| ❌ "No timeouts needed (async handles it)" | Async without deadlines = hang forever | Always set timeouts |
| ❌ "No need for idempotency" | Duplicate messages cause corruption | All Saga steps must be idempotent |

---

## 🧪 Saga Mastery Self-Assessment

### Core Understanding (Non-Negotiable)
- ✅ Why we need Saga instead of database transactions?
- ✅ What is a Saga in one sentence?
- ✅ What is a local transaction?
- ✅ What is compensation vs rollback?
- ✅ When is eventual consistency acceptable?

### Saga Mechanics
- ✅ Why can't we update two aggregates in one transaction?
- ✅ Why must Saga steps be idempotent?
- ✅ Why is "exactly-once delivery" an illusion?
- ✅ Why do distributed systems never wait forever?
- ✅ How does Saga progress without blocking threads?

### Orchestration vs Choreography
- ✅ Key difference between both?
- ✅ Why choreography risky as flows grow?
- ✅ Why banks prefer orchestration for money?
- ✅ Hidden coupling risks in choreography?
- ✅ When is choreography actually better?

### Failure Scenarios
- ✅ Ledger completely down → what happens?
- ✅ Ledger crashes before responding → orchestrator reaction?
- ✅ Ledger emits explicit failure → different from silence?
- ✅ Ledger responds AFTER timeout → why ignored?
- ✅ Why is saga state more important than message timing?

### Compensation & Business Correctness
- ✅ Why compensation usually only on customer-facing systems?
- ✅ Why isn't Ledger immediately compensated?
- ✅ Role of reconciliation in Saga systems?
- ✅ How prevent double compensation?
- ✅ How compliance requirements influence Saga design?

### Timeouts & Detection
- ✅ How orchestrator detects service "down"?
- ✅ How timeouts work without threads?
- ✅ What are delayed messages?
- ✅ Why timeout messages not physically cancelled?
- ✅ What if timeout and success race?

### Hybrid & Advanced
- ✅ What is hybrid Saga? Why often best?
- ✅ Which parts orchestrated vs choreographed?
- ✅ How design Saga boundaries?
- ✅ When use workflow engine instead?

---

## 🎓 Advanced Deep Dives

### Why 2PC Is Bad for Microservices

#### Short Answer
```
2PC assumes reliable, tightly-coupled systems.
Microservices assume failures and autonomy.
```

#### 2PC Assumptions (All False in Microservices)
```
✗ All participants are available
✗ They can lock resources
✗ Coordinator never fails mid-protocol
✗ Low latency & stable network
```

#### What Goes Wrong

**❌ Blocking & Resource Locking**
```
├─ DB rows locked while waiting
├─ One slow service blocks all others
└─ Throughput collapses
```

**❌ Failure Amplification**
```
├─ Coordinator crash leaves participants "in doubt"
├─ Manual recovery needed
└─ Cascading outages
```

**❌ Operational Nightmare**
```
├─ Hard to debug
├─ Hard to scale
└─ Poor cloud fit (pods restart freely)
```

#### Banking Perspective
```
Banks prefer auditability & recovery over atomicity.

Instead of: "Everything commits or nothing does"
Prefer: "Each service commits, we reconcile if needed"
```

---

### Why Health Checks Insufficient

#### Short Answer
```
Health checks tell you if service is ALIVE.
They don't tell you if business action SUCCEEDED.
```

#### Example
```
Ledger health endpoint: /health → UP

But internally:
├─ DB is read-only
├─ Disk is full
├─ Transaction queue blocked
├─ Memory exhausted

Result: Health says UP, business is DOWN
```

#### Saga Needs Business Truth, Not Infrastructure

```
Saga decisions depend on:
├─ "Was ledger entry posted?" (business truth)
└─ "Was balance updated?" (business truth)

Health checks cannot answer these questions.
```

#### Interview-Ready Line
```
"Saga decisions are driven by explicit business responses
and timeouts, not health checks."
```

---

### Why Blocking/Sleeping is Dangerous

#### Short Answer
```
Blocking assumes time is reliable.
Distributed systems assume it isn't.
```

#### What Goes Wrong

**❌ Crashes Kill Waiting Threads**
```
├─ Pod restarts → threads disappear
├─ Node failures → threads lost
├─ Saga stuck forever
```

**❌ Doesn't Scale**
```
├─ Each waiting saga consumes resources
├─ High load → thread exhaustion
└─ System collapses
```

**❌ Not Cloud-Native**
```
├─ Kubernetes kills pods freely
├─ Blocking logic loses state
└─ Impossible to recover
```

#### Correct Model
```
1. Persist state in DB
2. Forget about it
3. React later (via events/scheduler)
```

#### Timeout Implementation (Non-Blocking)
```
Deadlines stored in DB:
├─ Delayed messages (event sent, processed later)
├─ Schedulers (poll DB for expired sagas)
└─ Both combined
```

#### Interview-Ready Line
```
"Sagas must be event-driven and durable. Blocking threads
makes them fragile and non-recoverable."
```

---

### Why "Undo Everything" Is Dangerous

#### Short Answer
```
Undoing blindly corrupts business state and violates audit rules.
```

#### Why It Sounds Good
```
✗ Feels like rollback
✗ Feels safe
✗ But it's wrong in business systems
```

#### What Goes Wrong

**❌ Double Reversals**
```
├─ Step actually succeeded
├─ Compensation reverses it
└─ Net effect is incorrect
```

**❌ Different Domains, Different Semantics**
```
├─ Savings = customer balance (needs immediate fix)
├─ Ledger = accounting truth (needs correction entries)
├─ Risk = historical facts (cannot change)
└─ Cannot undo all equally
```

#### Banking Reality
```
Ledger prefers:
├─ Correction entries (debit + credit)
├─ Not reversals
├─ Preserves audit trail

Risk prefers:
├─ Historical accuracy
├─ No changes to past facts

Customer balance needs:
├─ Immediate correctness
```

#### Correct Approach
```
Compensation must be:
├─ Targeted (only necessary steps)
├─ Business-aware (understands domain rules)
└─ Selective (not blanket undo)
```

#### Interview-Ready Line
```
"Compensation is not rollback; it's a business correction
applied selectively based on domain semantics."
```

---

### What Makes Saga Brittle

#### A Saga Becomes Brittle When:

**❌ No Clear Ownership**
```
├─ Choreography everywhere
├─ No one owns end-to-end flow
└─ Failures scattered
```

**❌ No Timeouts**
```
├─ Waiting forever
├─ Silent failures
└─ No progress
```

**❌ Non-Idempotent Steps**
```
├─ Duplicate messages cause corruption
└─ Saga cannot safely retry
```

**❌ Over-Compensation**
```
├─ Reversing things blindly
└─ Corrupts business state
```

**❌ Tight Event Coupling**
```
├─ Event shape changes break many services
└─ Brittle evolution
```

**❌ No Observability**
```
├─ No saga state tracking
├─ No correlation IDs
└─ Debugging impossible
```

#### What Makes Saga Robust

```
✅ Clear orchestrator ownership
✅ Explicit deadlines (timeouts)
✅ Idempotent commands
✅ Business-aware compensation
✅ Reconciliation processes
✅ Strong observability (state, traces, logs)
```

---

## 🟢 Final Architect-Level Summary

```
"We avoid 2PC because it doesn't tolerate partial failures.
Saga decisions are driven by business outcomes and timeouts,
not health checks. Sagas must never block; they persist state
and react later. Compensation is selective business correction,
not rollback. Sagas become brittle when ownership, idempotency,
timeouts, or observability are missing."
```

**That paragraph alone would pass a senior architecture interview.**

---

## 📝 One-Sentence Proof Questions

If you can answer these clearly, you're solid:

1. **Explain Saga to a junior engineer in one sentence.**
   - "A Saga coordinates multi-service workflows by breaking them into independent steps with compensating actions on failure."

2. **Explain Saga failure handling to a product manager in one sentence.**
   - "If any step fails, we undo previous steps and report the failure; customer balance is always safe, reconciliation fixes the books."

3. **Explain Saga choice (orchestration vs choreography) to an auditor.**
   - "For money, we use orchestration so we have one place that can prove what happened, when, and why; for side effects, we use events."

---

## Summary Table: Implementation Patterns

| Pattern | Use Case | Pros | Cons |
|---------|----------|------|------|
| **Choreography** | Simple flows, side effects | Loose coupling | Hidden flow, fragmented failure |
| **Orchestration** | Money, critical paths | Clear ownership, auditable | Extra component, more code |
| **Hybrid** | Mature systems | Best of both | More complex |

**Choose based on business criticality, not architectural purity.**



Here's your markdown — just copy and paste it:


# ACID Transactions — Revision Notes

## At a Glance

| Property    | Core Idea                              | Problem it Solves                  |
|-------------|----------------------------------------|------------------------------------|
| Atomicity   | All or nothing                         | Incomplete writes on crash/failure |
| Consistency | DB moves valid state → valid state     | Constraint violations mid-txn      |
| Isolation   | Concurrent txns don't interfere        | Race conditions & dirty reads      |
| Durability  | Committed data survives crashes        | Data loss after system failure     |

---

## A — Atomicity
All operations in a transaction succeed together, or none are applied. On failure, DB rolls back completely — no partial state ever written.

**Example:**
```sql
BEGIN TRANSACTION;
  UPDATE accounts SET balance = balance - 500 WHERE id = 'Alice';
  UPDATE accounts SET balance = balance + 500 WHERE id = 'Bob';
COMMIT;
-- If second UPDATE fails → both rolled back. Alice keeps her $500.
```

---

## C — Consistency
Every transaction takes the DB from one valid state to another. No constraint is ever violated mid-way.

- Consistency is the **goal** — Atomicity, Isolation, Durability are the tools
- The DB enforces rules YOU define: CHECK, FOREIGN KEY, UNIQUE, NOT NULL

**Example:**
```sql
ALTER TABLE accounts ADD CONSTRAINT no_overdraft CHECK (balance >= 0);
-- Any txn that violates this is fully rolled back
```

---

## I — Isolation
Concurrent transactions execute as if sequential. Intermediate states are invisible to other transactions.

### 3 Classic Problems Without Isolation

**1. Dirty Read** — reading uncommitted data that may roll back
```
T1: UPDATE balance = 1000  (not committed)
T2: SELECT balance  →  reads 1000  ← WRONG
T1: ROLLBACK  →  balance never changed
```

**2. Non-Repeatable Read** — same query returns different values within one txn
```
T1: SELECT balance  →  500
T2: UPDATE balance = 800; COMMIT
T1: SELECT balance  →  800  ← Different result, same txn!
```

**3. Phantom Read** — re-running a query returns different rows
```
T1: SELECT * WHERE amount > 100  →  5 rows
T2: INSERT row with amount=200; COMMIT
T1: SELECT * WHERE amount > 100  →  6 rows  ← Phantom!
```

### Isolation Levels

| Level            | Dirty Read | Non-Rep. Read | Phantom Read | Default In          |
|------------------|------------|---------------|--------------|---------------------|
| Read Uncommitted | ✅ Possible | ✅ Possible    | ✅ Possible   | —                   |
| Read Committed   | ❌ Prevented| ✅ Possible    | ✅ Possible   | PostgreSQL, Oracle  |
| Repeatable Read  | ❌ Prevented| ❌ Prevented   | ✅ Possible   | MySQL               |
| Serializable     | ❌ Prevented| ❌ Prevented   | ❌ Prevented  | Strictest, slowest  |

---

## D — Durability
Once committed, data is permanent — even if system crashes immediately after.

- Achieved via **Write-Ahead Logs (WAL)** — changes logged to disk before confirming
- On crash recovery, DB replays the log to restore committed state
- COMMIT = a contract that data is safe forever

---

## Consistency vs Isolation — Key Difference

|                   | Consistency                      | Isolation                          |
|-------------------|----------------------------------|------------------------------------|
| Concerned with    | Data rules & constraints         | Concurrent transactions            |
| Question          | Is the data valid?               | Can transactions see each other?   |
| Enforced by       | Constraints, triggers, app logic | Locks, MVCC, concurrency control   |
| Prevents          | Invalid/corrupt data states      | Race conditions between txns       |

> **One-liner:** Consistency = *what* data looks like. Isolation = *when* changes become visible to others.

---

## Memory Hooks
- 💣 **Atomicity** — all goes off, or nothing does
- 📏 **Consistency** — rules respected before AND after
- 🔒 **Isolation** — each txn in its own bubble
- 🪨 **Durability** — COMMIT means written forever

---

## ACID vs NoSQL
NoSQL DBs (Cassandra, DynamoDB) often relax ACID for scalability — described by the **CAP Theorem** (pick 2 of: Consistency, Availability, Partition Tolerance).

- **ACID:** PostgreSQL, MySQL, Oracle, SQL Server
- **Eventual consistency:** Cassandra, DynamoDB, CouchDB
- **NewSQL (ACID + scale):** CockroachDB, Google Spanner
```

# CAP Theorem

CAP stands for **Consistency, Availability, and Partition Tolerance** — and the theorem states:

> **A distributed system can only guarantee 2 out of these 3 properties at any given time.**

---

## The 3 Properties

**Consistency (C)**
Every read receives the most recent write — or an error. All nodes in the system see the same data at the same time. If you write `x = 5` on node A, any read from node B must also return `5`.

> Note: This is *different* from the C in ACID. ACID consistency = data rules. CAP consistency = all nodes agree on the same data.

**Availability (A)**
Every request gets a response — always. The system never returns an error or times out, even if some nodes are down. However, the response may not be the most recent data.

**Partition Tolerance (P)**
The system keeps working even when network partitions occur — i.e., when nodes can't communicate with each other due to network failures.

---

## Why You Can Only Pick 2

In any real distributed system, **network partitions are unavoidable** — hardware fails, packets drop, datacenters get split. So in practice, **P is non-negotiable**. You always need partition tolerance.

That leaves you choosing between **C and A** when a partition happens:

```
Node A  ----network split----  Node B

User writes x=5 to Node A.
Node B hasn't received the update yet.

Now a user reads from Node B. What do you do?
```

- **Choose Consistency** → return an error / block until nodes sync. User gets correct data or nothing.
- **Choose Availability** → return Node B's stale data. User always gets a response, but it might be outdated.

---

## The 3 System Types

**CP — Consistent + Partition Tolerant** (sacrifices Availability)
During a partition, the system refuses to respond rather than return stale data. You'd rather go down than lie.

Examples: HBase, Zookeeper, MongoDB (in certain configs)

**AP — Available + Partition Tolerant** (sacrifices Consistency)
During a partition, all nodes stay up and accept reads/writes, but data may be stale or diverge temporarily. Nodes sync up *eventually* — this is called **eventual consistency**.

Examples: Cassandra, DynamoDB, CouchDB

**CA — Consistent + Available** (sacrifices Partition Tolerance)
Only possible if you assume the network never fails — which is only realistic on a single machine. In practice, no real distributed system is CA.

Examples: Traditional RDBMS (PostgreSQL, MySQL) — but only when running on a single node.

---

## Real World Analogy

Think of a Google Doc being edited by two people with bad internet.

- **CP behaviour** → "You're offline, you can't edit right now." Locks you out to prevent conflict.
- **AP behaviour** → "Go ahead and edit!" Both people edit independently, conflicts are resolved later when connection restores.

---

## CP vs AP — When to Choose Which

| Situation | Choose |
|---|---|
| Banking, payments, inventory | **CP** — stale data = real money problems |
| Social media feeds, likes, views | **AP** — slightly stale count is fine |
| Flight seat booking | **CP** — can't double-sell a seat |
| Shopping cart, product catalog | **AP** — availability matters more |
| Healthcare records | **CP** — correctness is critical |
| DNS, CDN, caching | **AP** — always-on matters more |

---

## CAP vs ACID

| | CAP | ACID |
|---|---|---|
| **Domain** | Distributed systems | Single database transactions |
| **C means** | All nodes see same data | Data rules/constraints hold |
| **Core tension** | Consistency vs Availability | Correctness vs Performance |
| **When relevant** | When you have multiple nodes | Within a single DB transaction |

---

## One-liner Summary

> In a distributed system, when the network breaks, you must choose: **do you want correct data, or do you want a response?** You can't always have both.

