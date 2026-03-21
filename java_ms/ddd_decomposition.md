# Strategic vs Tactical DDD + Service Decomposition
> EM interview prep — crystal clear version

---

## 1. Strategic vs Tactical DDD — The One-Line Distinction

| | Strategic DDD | Tactical DDD |
|---|---|---|
| **Question** | WHERE do you draw the lines? | WHAT do you put inside the lines? |
| **Level** | Architecture + organisation | Code + domain model |
| **Output** | Bounded contexts, context map | Aggregates, entities, value objects, events |
| **Who** | EM, architect, domain expert | Developer, tech lead |
| **When** | Before writing code | When writing code for a context |

**One sentence:** Strategic = the map. Tactical = what's on the map.

> "Strategic DDD tells you the Payments BC exists. Tactical DDD tells you PaymentOrder is an aggregate inside it."

### Strategic DDD concepts
- Bounded Context — boundary where model is valid and unambiguous
- Ubiquitous Language — shared vocabulary within that boundary
- Context Map — relationships between contexts (ACL, Conformist, Partnership, etc.)
- Core / Supporting / Generic — subdomain classification

### Tactical DDD concepts
- Aggregate + Root — consistency boundary, single entry point
- Entity — identity-based, mutable
- Value Object — value-based, immutable
- Domain Event — something happened, past tense, immutable
- Domain Service — cross-aggregate behaviour
- Repository — one per aggregate root

---

## 2. Three Decomposition Strategies

### Strategy 1: By Business Capability

A business capability = something the business DOES. Stable. Independent of org structure.

Ask: "What do you do?" not "How do you do it?"

```
Enterprise UK Bank — Business Capabilities

├── Customer Management
│   ├── Customer Onboarding
│   ├── Identity Verification
│   └── Profile Management
│
├── Savings Management (YOUR DOMAIN)
│   ├── Savings Account Opening
│   ├── Balance Management
│   ├── Interest Calculation
│   └── Maturity Processing
│
├── Payment Processing
│   ├── Payment Initiation
│   ├── Payment Routing
│   ├── Settlement
│   └── Reconciliation
│
└── Risk & Compliance
    ├── Fraud Detection
    ├── AML Screening
    └── Regulatory Reporting
```

**When to use:** Greenfield, domain well understood, business can articulate what they do.

---

### Strategy 2: By Subdomain (DDD)

Classify every part of the domain:

| Subdomain Type | Definition | Action |
|---|---|---|
| **Core** | Your competitive advantage. Changes often. | Build it. Best engineers. Full DDD. |
| **Supporting** | Necessary but not differentiating. | Build simply. |
| **Generic** | Commodity. Everyone needs it. | Buy it. Open source. SaaS. |

**Applied to Savings:**

```
CORE (build, invest, differentiate)
├── Savings Account Lifecycle    — LSA + FTD state machines
├── Interest Accrual Engine      — ACT/365, tiered rates
├── Rate Assignment Service      — tier matching = competitive IP
└── Maturity Processing          — FTD execution logic

SUPPORTING (build simply)
├── KYC Orchestration            — necessary, orchestrates 3rd parties
├── Notice Period Management     — LSA notice tracking
├── Regulatory Reporting         — FCA / PRA returns
└── Operations / Batch           — interest posting, reconciliation

GENERIC (buy or use open source)
├── Document Generation          — iText / Apache PDFBox
├── Email / SMS Notifications    — SendGrid / Twilio
├── Authentication               — Keycloak / Azure AD B2C
└── Audit Logging                — Elasticsearch / Datadog
```

**EM quote:** "I put my 3 best engineers on Rate Assignment Service — that's core IP. If we get rate assignment wrong we lose commercial customers. If we get PDF statements wrong, we use a different library."

**When to use:** Complex domain, running Event Storming, migrating a monolith.

---

### Strategy 3: Single Responsibility (SRP at Service Level)

One service = one reason to change = one team owns it.

**Test:** "Who would demand a change to this service?"
- One stakeholder/team → Good boundary
- Multiple teams → SRP violation

---

## 3. The Event Storming → Service Boundary Process

```
1. EVENT STORMING
   Write every domain event (past tense, sticky notes)
   PaymentInitiated, AccountOpened, FraudAlertRaised, BalanceUpdated...
        ↓
2. CLUSTER into aggregates
   Which events belong to the same "thing"?
   PaymentOrder / Account / FraudCase
        ↓
3. FIND ownership boundaries
   Where does one team's responsibility end?
        ↓
4. CHECK language consistency
   Does "Transaction" mean the same thing across this boundary?
   If NO → different bounded contexts
        ↓
5. CLASSIFY subdomains
   Core / Supporting / Generic
        ↓
6. APPLY granularity heuristics (see below)
        ↓
7. DRAW bounded contexts (Strategic DDD output)
        ↓
8. MODEL inside each context (Tactical DDD)
   Aggregates, value objects, domain events
        ↓
9. SERVICES emerge from contexts
   One context = one or more services
   Never shared between contexts
```

---

## 4. Service Granularity — The Spectrum

```
TOO FINE                    JUST RIGHT                  TOO COARSE
────────                    ──────────                  ──────────
Nanoservices                One team                    Distributed monolith
1 class = 1 service         One capability              All teams deploy together
Network for everything      One reason to change        One giant DB
                            Own database
                            Deploys independently
```

### Too Fine — Nanoservices (avoid)
```
SavingsBalanceService      ← just balance reads/writes
SavingsInterestService     ← just interest calc
SavingsStatusService       ← just status updates
SavingsRateService         ← just rate assignment
```
Problems: 4 network hops for one withdrawal · Distributed transaction needed · Tracing nightmare · Latency compounds · 4 teams for one business feature · No team owns savings invariants

### Too Coarse — Distributed Monolith (avoid)
```
SavingsService handles: LSA + FTD + interest + statements +
reporting + ops + KYC + onboarding + colleague tools
```
Problems: 6 teams deploy together · PDF bug blocks interest posting release · DB is bottleneck · Can't scale interest engine independently · One team's change breaks another's feature

### Just Right
```
SavingsAccountLifecycleService
├── Owns: LSA + FTD aggregates, all invariants, all state transitions
├── Team: 5-6 engineers, one product owner
├── Database: savings_lifecycle_db (PostgreSQL) — nobody else touches it
├── Reason to change: savings product rules change
└── Deploys: independently, 2-week sprint cadence
```

---

## 5. Six Granularity Heuristics

| # | Heuristic | Test | Fail signal |
|---|---|---|---|
| 1 | **Deployment test** | Can it deploy without deploying another service? | If NO → boundary wrong |
| 2 | **Team ownership test** | "Who owns this service?" | If 2 teams answer → split it |
| 3 | **Database test** | Does it have its own database? | If shared DB → fake boundary |
| 4 | **Change rate test** | Does each part change for the same reason? | Different reasons → split |
| 5 | **Scale test** | Do parts need to scale independently? | If yes → strong split signal |
| 6 | **Network hop test** | How many sync calls for one user action? | More than 3 → too fine |

---

## 6. FS Example — Payments / Accounts / Fraud Boundaries

### Why these are three separate bounded contexts

**"Transaction" language test:**
- In Payments: instruction moving money A→B, has scheme/reference/status
- In Fraud: data point to assess risk, has device/location/velocity
- Same word, different models → different contexts

### Payments BC
```
Owns:      PaymentOrder aggregate, routing, scheme submission, settlement
Database:  payments_db (PostgreSQL) — ACID transactions
API:       POST /payments, GET /payments/{id}
Publishes: PaymentInitiatedEvent → Fraud BC screens it
           PaymentSettledEvent → Account BC posts balance
           PaymentFailedEvent → Notification BC alerts customer
Consumes:  FraudScreenPassedEvent → proceed to submit
           FraudAlertRaisedEvent → block payment
```

### Accounts BC
```
Owns:      Account aggregate, balance, mandates, standing orders
Database:  accounts_db (PostgreSQL)
API:       GET /accounts/{id}/balance, POST /accounts/{id}/freeze
Publishes: BalanceUpdatedEvent → View BC, Reporting BC
           AccountFrozenEvent → Payments BC (block new payments)
Consumes:  PaymentSettledEvent → updates balance
           DirectDebitExecutedEvent → posts debit
```

### Fraud BC
```
Owns:      FraudCase aggregate, transaction monitoring, ML model serving
Database:  Cassandra (time-series transaction data)
           + Redis (real-time velocity counters)
           + Feature store (ML feature vectors)
API:       POST /fraud/assess (sub-200ms SLA), GET /fraud/cases/{id}
Publishes: FraudScreenPassedEvent → Payments BC
           FraudAlertRaisedEvent → Payments BC + Case Mgmt BC
           SuspiciousPatternEvent → AML BC (SAR consideration)
Consumes:  PaymentInitiatedEvent → screens every payment
```

### Why Fraud is NOT inside Payments
> "Fraud has a completely different model, different technology stack (Cassandra + Redis + feature store vs PostgreSQL), different team, and different rate of change. The team that updates the fraud ML model should never need to touch payment routing code. They share events, not models."

### Why Balance is NOT inside Payments
> "Payments doesn't own balances. A payment settles, and the Account BC responds by updating the balance. If Payments owned balance it would need to know about direct debits, standing orders, fees, interest — that's a monolith waiting to happen."

### Why different databases (polyglot persistence)
> "Polyglot persistence is a consequence of bounded context separation, not an architectural choice for its own sake. Fraud needs time-series queries across millions of transactions — Cassandra is right for that. Payments needs ACID transactions for payment state — PostgreSQL is right for that. When they shared a DB we had to choose one — and neither was right for both."

---

## 7. EM Interview Anchor Answer

> "I use Event Storming as the starting point — get domain experts, developers, and product in a room for 2-3 days, map every domain event. Events cluster naturally around aggregates, aggregates cluster around team ownership lines. Where ownership changes and language changes — that's your bounded context boundary.
>
> I then classify each subdomain: core IP, supporting, or generic. Core gets senior engineers, full DDD tactical patterns, rich domain models. Generic gets a library or SaaS tool.
>
> For granularity I apply six heuristics: can it deploy independently, does one team own it, does it have its own DB, does it have one reason to change, does it scale independently, and how many synchronous hops does one user operation require. If a service fails more than two of those — the boundary is wrong."

---

*Strategic DDD = WHERE to draw lines | Tactical DDD = WHAT to put inside*
*Core = build + invest | Supporting = build simply | Generic = buy*
