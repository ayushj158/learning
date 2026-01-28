# Service Boundaries & Microservice Decomposition: Complete Guide

## Executive Summary

**Microservices are a scaling strategy, not a starting architecture.**

The core decision: **When do you split services?** Only when multiple signals align—never on a single signal.

### Common Failure Mode

```
Distributed Monolith:
├─ Looks like microservices (multiple services)
├─ Acts like monolith (tightly coupled at runtime)
└─ Pays all costs without benefits
```

### The Right Approach

```
✅ Start with modular monolith
✅ Enforce boundaries technically
✅ Split when ≥3 signals are STRONG
✅ Use strangler pattern (incremental)
✅ Avoid distributed monolith trap
```

---

## 🏗️ Modular Monolith: The Underrated Starting Point

### What a Modular Monolith Really Is

```
One deployable unit with microservice-grade internal boundaries
```

**Key Characteristics:**

```
✅ Clear domain modules (bounded contexts)
✅ Explicit dependency rules (enforced)
✅ No cross-module data access
✅ Modules can evolve independently
✅ In-process communication (fast)
```

### What It Is NOT (Critical Distinction)

```
❌ NOT package-by-layer architecture
   └─ Wrong: controllers → services → repositories
   └─ Right: domain modules with clear boundaries

❌ NOT shared domain models
   └─ Wrong: same "Account" class everywhere
   └─ Right: each module has its own model

❌ NOT a stepping stone to ignore
   └─ Wrong: "This is just temporary"
   └─ Right: Modular monolith is a valid architecture
```

### Typical Banking Modules

```
Modular Monolith Structure:

[Onboarding]    [Savings]       [Payments]    [Ledger]    [Notifications]
├─ KYC          ├─ Rates         ├─ Initiate   ├─ Post     ├─ Email
├─ Approval     ├─ Interest      ├─ Validate   ├─ Settle   ├─ SMS
└─ Registration └─ Statements    └─ Confirm    └─ Reconcile└─ Push

Each module:
✅ Owns its data (no shared tables)
✅ Owns its business logic
✅ Exposes APIs internally
✅ Can change independently
```

### 🔥 Key Insight

```
"Architecture quality comes from boundary enforcement,
not from deployment count."

Monolith with clear boundaries > Microservices with loose coupling
```

---

## ⚠️ When Monolith BECOMES a Problem

### Monolith Is BAD When

```
❌ Everything depends on everything
   └─ No clear ownership
   └─ No module boundaries
   └─ "Shared utils" everywhere

❌ Impossible to understand
   └─ Tangled dependencies
   └─ Side effects everywhere
   └─ Changes break unrelated code

❌ Release cycles blocked
   └─ Team A waits for Team B's change
   └─ Small change requires full regression
   └─ Fearful deployments
```

### Red Flags (Not Yet Microservices)

```
🔴 Unclear ownership
   └─ "Who owns this code?" → No clear answer

🔴 Shared databases / tables
   └─ Multiple modules query same tables
   └─ Cross-module data dependencies

🔴 No technical enforcement
   └─ Boundaries are documented but not enforced
   └─ Easy to break rules

🔴 Monolithic testing
   └─ Can't test one module in isolation
   └─ Full system must come up
```

---

## 🔧 Enforce Boundaries Technically (CRITICAL)

### Why Enforcement Matters

```
"Without enforcement, a modular monolith slowly decays
into a big ball of mud."
```

### Build-Time Enforcement Techniques

| Technique | Tool/Language | How It Works | Cost |
|-----------|--------------|-------------|------|
| **Architecture rules** | ArchUnit (Java) | Checks compile-time dependencies | Low |
| **Package visibility** | Java/C# access modifiers | Private classes prevent imports | None |
| **Separate modules** | Gradle/Maven/Cargo | Physical module separation | Medium |
| **Import constraints** | ESLint (Node) | Prevent forbidden imports | Low |
| **Dependency graph analysis** | SonarQube/Checkstyle | Detect cycles & violations | Low |

### Example: ArchUnit (Java)

```java
// Enforce boundaries at build-time
classes()
  .that().resideInAPackage("..payments..")
  .should().notDependOnClassesThat()
  .resideInAPackage("..ledger..")
  .because("Payments module cannot depend on Ledger")
  .check(importedClasses);
```

### Example: ESLint (Node)

```json
{
  "rules": {
    "import/no-internal-modules": ["error", {
      "allow": [
        "onboarding/*",
        "savings/*"
      ]
    }]
  }
}
```

### What NOT to Do

```
❌ No "utils" dumping ground
   └─ Creates implicit coupling
   └─ Everything imports utils

❌ No "shared" module
   └─ Becomes the monolith itself
   └─ All modules depend on it

❌ Relying on documentation only
   └─ "Please don't call this"
   └─ Ignored by developers under deadline
```

### 🔥 Interview-Ready Line

```
"We use ArchUnit to enforce boundaries at build time.
If code violates module boundaries, the build fails—
we don't rely on developer discipline."
```

---

## ✅ When Modular Monolith Is the RIGHT Choice

### Apply Modular Monolith When

```
✅ Team size < ~40 engineers
   └─ Coordination overhead minimal
   └─ Everyone understands system

✅ Domain still evolving
   └─ Boundaries will change
   └─ Splitting is expensive, refactoring is cheap

✅ No independent scaling needs yet
   └─ Performance acceptable
   └─ Vertical scaling sufficient

✅ Heavy business rules / correctness requirements
   └─ ACID transactions needed
   └─ In-process calls are atomic

✅ Observability & debugging ease matters
   └─ Single deployment = simple debugging
   └─ Stack traces are local
```

### Banking Examples (Good Fit)

```
✅ Early-stage fintech
   └─ Still learning domain
   └─ Frequent pivots
   └─ Team < 20 engineers

✅ High-risk products
   └─ Payments, settlements
   └─ Need strong transactional consistency
   └─ Prefer local ACID over distributed consensus

✅ Heavily regulated services
   └─ Compliance changes often
   └─ Business rules complex
   └─ Easy rollback needed (single deployment)
```

### 🔥 Interview-Ready Line

```
"We started as a modular monolith because the domain was
evolving rapidly. We didn't pay microservices operational
costs until boundaries stabilized and scaling became necessary."
```

---

## 🚪 Strangler Pattern: Safe Incremental Migration

### What the Strangler Pattern Really Is

```
Incrementally routing new functionality to new services
while the old system continues running in parallel.
```

**Key Idea:**

```
├─ No big-bang rewrite
├─ Old monolith + new services coexist
├─ Traffic routing decides who handles what
├─ Old system is slowly "strangled" out
└─ Never risky switchover moment
```

### Flow Diagram

```
Client Requests
  ↓
[Strangler/Proxy]
  ├─ Old requests → Monolith (legacy)
  ├─ New requests → New Service
  └─ Gradually shift traffic

Over time:
├─ More traffic → New Service
├─ Less traffic → Monolith
└─ Eventually: Monolith can be shut down
```

### Strangler Safety Rules

```
✅ Proxy/router sits in front
   └─ Decides who handles request
   └─ Can rollback instantly

✅ Monitor both paths
   └─ Track errors in each
   └─ Compare latencies

✅ Gradual traffic shift
   └─ 5% new → 25% → 50% → 100%
   └─ Never risky switchover

✅ Maintain compatibility
   └─ New service must match old API
   └─ Clients unaware of change
```

---

## 📋 What to Strangle FIRST (Priority Order)

### Excellent Candidates (Extract Early)

```
✅ Read-heavy APIs
   └─ Easier to build independently
   └─ No transaction coordination needed
   └─ Example: Balance inquiry, statements

✅ New product features
   └─ Not tied to legacy code
   └─ Can build from scratch
   └─ Example: New savings product

✅ Non-core capabilities
   └─ Nice to have, not critical
   └─ Failure doesn't stop business
   └─ Example: Notifications, analytics

✅ Edge integrations
   └─ Isolated from core business
   └─ Third-party APIs
   └─ Example: AML checks, external reporting
```

### Poor Candidates (Strangle Later)

```
❌ Ledger core
   └─ Needs strong consistency
   └─ Distributed ledger is complex
   └─ Extract only after high confidence

❌ Settlement engines
   └─ Regulatory-critical
   └─ Strong audit requirements
   └─ Timing-sensitive

❌ Regulatory reporting
   └─ Compliance-critical
   └─ Must be 100% correct
   └─ Easy to update in monolith

❌ Account opening/KYC
   └─ Many cross-module dependencies
   └─ Coordination with many services
   └─ Wait until domain is stable
```

### 🔥 Rule

```
Extract read-heavy / non-core features first.
Leave transaction-heavy / core logic until later.
```

---

## 🚨 The #1 Microservices Failure: Distributed Monolith

### What Distributed Monolith Really Is

```
A system that LOOKS like microservices
but ACTS like a monolith.

You pay all costs of distribution
but get none of the benefits.
```

### Symptoms (MEMORIZE THESE)

| Symptom | What It Means |
|---------|--------------|
| **Services must be deployed together** | Can't deploy Service A independently |
| **One service change breaks many others** | Tight runtime coupling |
| **Synchronous call chains** | Service A → B → C → D in single request |
| **Shared database or tables** | Multiple services query same tables |
| **Shared domain models** | "Account" class imported everywhere |
| **End-to-end failures common** | One service down breaks entire flow |
| **Complex inter-service coordination** | Locks, distributed transactions |

### Why Distributed Monolith Is WORSE Than Monolith

| Aspect | Monolith | Distributed Monolith | Winner |
|--------|----------|----------------------|--------|
| **Latency** | In-process | Network delays | Monolith |
| **Debugging** | Local stack traces | Across many services | Monolith |
| **Consistency** | ACID is easy | Eventual consistency is hard | Monolith |
| **Reliability** | Predictable | Fragile to network issues | Monolith |
| **Operations** | Simple | Complex (deployments, monitoring) | Monolith |
| **Data consistency** | Strong (local) | Weak (eventual) | Monolith |

### 🔥 Damning Line

```
"A distributed monolith has all the complexity of microservices
with none of the benefits. If you don't get autonomy from
microservices, you should not pay the distribution tax."
```

### How Distributed Monoliths Happen

```
Team builds "microservices" without:
├─ Clear service boundaries (aligned to bounded contexts)
├─ Data ownership (services own their data)
├─ Async communication (prefer events, not sync calls)
├─ Independent deployability (still coordinated)

Result:
└─ Tight runtime coupling
    ├─ Service A calls B → B calls C → ...
    ├─ Can't deploy A without testing B,C,D
    └─ Any part fails → whole flow fails
```

---

## 🔗 Chatty Services: The Silent Killer

### What "Chatty" Means

```
Too many synchronous calls between services
to complete ONE business action.
```

**Example (Antipattern):**

```
Client Request for "Get Customer Financial Summary"

Service A (Customer)
  ↓ sync call
Service B (Accounts)
  ↓ sync call
Service C (Ledger)
  ↓ sync call
Service D (Limits)
  ↓ sync call
Service E (Preferences)

Result:
├─ 5 network round trips
├─ If ANY service slow → entire request slow
├─ If ANY service down → entire request fails
├─ This is NOT a microservice, it's a distributed monolith
```

### Root Causes of Chatty Services

```
❌ Splitting by technical layer
   └─ One service per layer (wrong)
   └─ Results in layered call chains

❌ Ignoring bounded contexts
   └─ Services too fine-grained
   └─ Need many to complete workflow

❌ Using sync calls for workflows
   └─ "Easy" to implement
   └─ Creates coupling

❌ Treating services like remote classes
   └─ RPC thinking (wrong)
   └─ Service calls should be sparse
```

### How to FIX Chatty Services

#### 1. API Composition at the Edge

```
WRONG (Chatty):
Client
  ├─ call Service A
  ├─ call Service B
  ├─ call Service C
  └─ aggregate results

RIGHT (API Composition):
Client
  ↓
[API Gateway / BFF]
  ├─ Internal call Service A
  ├─ Internal call Service B
  ├─ Internal call Service C
  ├─ Aggregate results
  └─ Return to client

Result:
✅ One external call
✅ Aggregation happens at edge (behind firewall)
✅ Clients don't see chatty implementation
```

#### 2. CQRS for Read Aggregation

```
WRONG (Multiple sync calls):
Service A (Customer) → contains: name, email
Service B (Accounts) → contains: account list
Service C (Ledger) → contains: recent transactions

Client needs all 3:
  └─ Makes 3 sync calls OR Service A calls B,C

RIGHT (CQRS):
[Read Model]
  ├─ Denormalized view
  ├─ Updated by events from Services A, B, C
  └─ One query returns everything

Client needs summary:
  └─ One query to read model
```

#### 3. Coarse-Grained APIs

```
WRONG (CRUD, chatty):
GET /customers/{id}           → name, email
GET /customers/{id}/accounts  → list of accounts
GET /accounts/{id}/balance    → balance
GET /accounts/{id}/limits     → limits

Result: Client makes 4 calls

RIGHT (Business-oriented):
GET /customers/{id}/financial-summary
  ↓ Response:
  {
    name: "...",
    email: "...",
    accounts: [...],
    totalBalance: 500000,
    limits: {...}
  }

Result: Client makes 1 call
```

#### 4. Saga Pattern for Workflows

```
WRONG (Sync coordination):
Service A
  ├─ sync call → Service B (must succeed)
  ├─ sync call → Service C (must succeed)
  └─ if any fails → rollback A

Problem:
├─ Tightly coupled (A waits for B,C)
├─ Timeout cascades
├─ Hard to debug

RIGHT (Saga pattern):
Service A publishes event → Accounts_Updated
  ↓
Service B receives event → processes asynchronously
  ├─ Success: publishes Balance_Updated
  └─ Failure: publishes Failure event (compensation)
Service C receives event → processes asynchronously
  └─ Similar pattern

Result:
✅ Loosely coupled (no waiting)
✅ Can handle timeouts gracefully
✅ Clear failure paths
```

### 🔥 Interview Gold

```
"Microservice APIs should be business-oriented, not CRUD-oriented.
A single API should represent a complete business action, not
require the client to orchestrate multiple services."
```

---

## 🎯 The 5 Signals Framework: When to Split

### First Principle (MEMORIZE THIS)

```
"Microservices are a SCALING STRATEGY, not a starting architecture.

You split a service to solve a problem—not to look modern."
```

### The 5 Signals Framework (Core Decision Model)

#### 🟢 SIGNAL 1: Business Volatility

**Question:** Does this part of the domain change frequently?

```
✅ HIGH volatility
   ├─ Rules change often
   ├─ Blocks other team releases
   └─ Split candidate

❌ LOW volatility
   ├─ Rules are stable
   ├─ Change rarely
   └─ Keep together
```

**Banking Examples:**

```
✅ Split: Interest rate calculation
   └─ Rules change seasonally
   └─ Different products have different rates
   └─ Frequent A/B testing

❌ Keep together: Ledger posting rules
   └─ Accounting rules are stable
   └─ Regulatory requirements don't change often
   └─ Core domain logic
```

#### 🟢 SIGNAL 2: Team Ownership & Autonomy

**Question:** Is there a clear team that owns this end-to-end?

```
✅ CLEAR ownership
   ├─ Team owns feature from API to database
   ├─ Teams don't step on toes
   └─ Split improves autonomy

❌ SHARED ownership
   ├─ Multiple teams involved
   ├─ Coordination overhead
   └─ Splitting creates chaos
```

### 🔥 Interview-Ready Line

```
"We don't split services without clear ownership.
If splitting requires coordination overhead, keep it together."
```

#### 🟢 SIGNAL 3: Independent Scaling Needs

**Question:** Does this component scale differently from others?

```
✅ Different scaling profiles
   ├─ Balance Inquiry: 10,000 req/sec
   ├─ Account Setup: 10 req/sec
   └─ CPU vs I/O differences
   └─ Split candidate

❌ Same scaling profile
   ├─ All components scale together
   ├─ No independent scaling need
   └─ Keep together
```

**Banking Examples:**

```
✅ Split: Statements (read-heavy)
   └─ 1000s req/sec, only reads
   └─ Can scale independently

❌ Keep together: Account configuration (write-heavy)
   └─ Few req/sec
   └─ Shares data model with core account
```

#### 🟢 SIGNAL 4: Failure Isolation Requirement

**Question:** Can this fail independently without cascading?

```
✅ Can fail independently
   ├─ Notification service down ≠ blocks payments
   ├─ Risk service down ≠ blocks normal transfer
   └─ Split candidate

❌ Cannot fail independently
   ├─ Ledger failure = critical
   ├─ Payment processing failure = catastrophic
   └─ Keep together (or extract very carefully)
```

**Banking Examples:**

```
✅ Split: Notifications
   └─ Can be unavailable briefly without breaking core

✅ Split: Reporting
   └─ Can be slow without affecting transfers

❌ Keep together: Ledger
   └─ Ledger failure = complete system outage
```

#### 🟢 SIGNAL 5: Data Ownership Clarity

**Question:** Can this service fully own its data?

```
✅ Clear data ownership
   ├─ Service owns all its tables
   ├─ No cross-service queries
   └─ Safe to split

❌ Unclear ownership
   ├─ Multiple services query same table
   ├─ Shared database
   ├─ Joint ownership of data
   └─ DO NOT SPLIT (distributed monolith trap)
```

### 🔥 Strong Rule

```
"No clear data ownership = no microservice.
If you can't cleanly separate data, keep the service together."
```

---

## 📊 Decision Matrix (PRINT THIS IN YOUR HEAD)

| Signal | Weak | Medium | Strong |
|--------|------|--------|--------|
| **Business volatility** | Rules stable | Some changes | Frequent changes |
| **Team ownership** | Shared | Partial | Clear end-to-end |
| **Scaling needs** | Same | Slightly different | Very different |
| **Failure isolation** | Critical (must stay up) | Important | Nice to have |
| **Data ownership** | Shared tables | Some shared | Fully owned |

### Decision Rule

```
🟢 Split when ≥3 signals are STRONG
⚠️  Consider when 2-3 STRONG signals
❌ Don't split when <2 STRONG signals

OR

Split ONLY if you can clearly answer:
├─ Who owns this end-to-end?
├─ What data does it own?
├─ When does it change?
├─ Can it fail independently?
└─ If ANY answer is unclear → Keep together
```

---

## ❌ The DO NOT SPLIT Conditions (CRITICAL)

### Never Split When

```
❌ Domain is still evolving
   └─ Boundaries will change
   └─ Splitting is expensive, refactoring is cheap

❌ Team is small or new
   └─ Coordination overhead too high
   └─ Microservices require 40+ engineers to justify

❌ Strong transactional consistency needed
   └─ Distributed transactions are hard
   └─ Easier to keep together

❌ Heavy synchronous coordination required
   └─ Many services calling each other
   └─ Would create distributed monolith

❌ Observability is weak
   └─ Can't see across services
   └─ Debugging is nightmare
   └─ Fix observability FIRST

❌ Organizational structure blocks autonomy
   └─ No clear team ownership
   └─ Still matrix-managed
   └─ Splitting without autonomy creates overhead
```

### 🔥 Interview Line

```
"We've seen teams split services prematurely, then spend months
on distributed transactions and observability. We now wait
until boundaries are clear and teams can own them."
```

---

## ✨ Best Practices for Service Boundaries

| Practice | Why |
|----------|-----|
| **Align to bounded contexts** | Clear business domains = clear services |
| **Each service owns its data** | Prevents distributed monolith |
| **Enforce boundaries technically** | ArchUnit, import rules, modules |
| **Start with modular monolith** | Prove boundaries before splitting |
| **Use strangler pattern** | Incremental, safe migration |
| **Prefer async communication** | Decouples services |
| **Coarse-grained APIs** | One call per business action |
| **Use CQRS for reads** | Avoid cross-service joins |
| **Monitor both old & new** | A/B comparison during strangler |

---

## 🚨 Service Boundary Anti-Patterns

| Anti-Pattern | Why It's Wrong | Fix |
|--------------|----------------|-----|
| ❌ Split by technical layer | Creates chatty services | Split by business domain |
| ❌ Services too small | Excessive coordination | Coarse-grained boundaries |
| ❌ Shared database | Distributed monolith | Each service owns data |
| ❌ Shared domain models | Tight coupling | Each service has own models |
| ❌ Many sync calls | Chatty, tightly coupled | Use events, CQRS, coarse APIs |
| ❌ No data ownership plan | Distributed monolith | Clarify data ownership first |
| ❌ Split without ownership | Coordination overhead | Clear team ownership required |
| ❌ Big bang migration | High risk | Use strangler pattern |
| ❌ No boundary enforcement | Slow decay to chaos | Use ArchUnit, modules, rules |
| ❌ Extract core logic first | High risk, complexity | Extract edge cases first |

---

## 🎤 Interview-Ready One-Paragraph Summary

```
"Service boundaries should align to bounded contexts, with clear
team ownership and data ownership. We start with a modular
monolith with technically-enforced boundaries, then use the
5-signals framework to decide when to split. We never split on
a single signal—we require ≥3 strong signals. Crucially, we
migrate incrementally using the strangler pattern, never with a
big-bang rewrite. The biggest trap is the distributed monolith—
services that look independent but are tightly coupled at
runtime. We avoid this by ensuring each service owns its data
and prefers async communication over sync call chains."
```

**That paragraph demonstrates deep architectural thinking.**

---

## 📊 Modular Monolith vs Microservices

| Aspect | Modular Monolith | Microservices |
|--------|------------------|--------------|
| **Deployment** | Single unit | Multiple units |
| **Boundaries** | In-process | Network |
| **Latency** | Low (in-memory) | Network cost |
| **Transactions** | Simple (ACID) | Distributed (Saga) |
| **Consistency** | Strong | Eventual |
| **Operations** | Simple | Complex |
| **Debugging** | Easy (local) | Hard (distributed) |
| **Scaling** | Vertical | Horizontal |
| **Team size** | Small (< 40) | Large (40+) |
| **When to use** | Early/evolving domains | Stable/scaled domains |

---

## ✅ 10-Minute Pre-Interview Checklist

- ✅ Can I explain modular monolith vs microservices?
- ✅ Do I know the 5 signals framework?
- ✅ Can I explain distributed monolith trap?
- ✅ Do I understand chatty services problem?
- ✅ Can I explain strangler pattern?
- ✅ Do I know what to extract first (read-heavy)?
- ✅ Can I list 5+ boundary anti-patterns?
- ✅ Do I understand data ownership clarity rule?

**If yes to all → SERVICE BOUNDARIES MASTERY ✅**

---

## 📝 Summary

### Start Here

```
✅ Modular monolith (enforced boundaries)
✅ Technical enforcement (ArchUnit, modules)
✅ Small, cohesive teams
```

### When to Split

```
✅ ≥3 signals are STRONG
✅ Clear team ownership
✅ Clear data ownership
✅ Stable domain boundaries
```

### How to Split Safely

```
✅ Use strangler pattern
✅ Gradual traffic shift
✅ Continuous monitoring
✅ Easy rollback
```

### Avoid This Trap

```
❌ Distributed monolith
   └─ Looks like microservices
   └─ Acts like monolith
   └─ Worst of both worlds
```

### Final Interview Line

```
"We don't optimize for microservices count—we optimize for
clear boundaries, team autonomy, and independent deployability.
Sometimes that's a monolith, sometimes it's services. The
boundaries matter more than the deployment count."
```


