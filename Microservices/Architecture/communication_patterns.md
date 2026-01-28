# Inter-Service Communication Patterns: Complete Guide

## Executive Summary

**Inter-service communication defines coupling, failure propagation, and system evolution.**

### Core Principle

```
✅ Request-response for queries (REST/gRPC)
✅ Pub/Sub for workflows (async)
✅ Async fan-out (one → many)
✅ CQRS / BFFs for fan-in (many → one)
✅ Bulkhead isolation (limit blast radius)

❌ Never synchronous fan-out
❌ Never event-based RPC (async simulation)
❌ Never unprotected critical paths
```

### Communication Pattern Choice

```
Pattern          When to Use              Coupling
─────────────────────────────────────────────────
Request-response Queries, immediate       Tight
Pub/Sub          Workflows, async         Loose
Event fan-out    Fire-and-forget          Loose
CQRS/BFF         Aggregated reads         Medium
```

---

## 🔄 REST vs gRPC (Not JSON vs Protobuf)

### What REST Really Optimizes For

```
REST optimizes for EVOLVABILITY and EXTERNAL CONSUMPTION
```

**Design Principles:**

```
✅ Loose coupling
✅ Human-readable contracts
✅ Backward compatibility friendly
✅ Proxy / gateway / CDN friendly
✅ Browser-accessible
```

**System Shape (REST):**

```
Easy external exposure
├─ Public APIs
├─ Partner integrations
├─ Mobile/web clients

Easier versioning
├─ /v1/, /v2/ endpoints
├─ Additive field changes work
├─ Deprecated endpoints stay

More tolerant of partial failures
├─ Clients can retry
├─ Timeout behavior clear
├─ Fallback responses possible
```

### What gRPC Really Optimizes For

```
gRPC optimizes for PERFORMANCE, not FLEXIBILITY
```

**Design Principles:**

```
✅ Strongly typed contracts (Protobuf)
✅ Binary, efficient
✅ Lower latency
✅ HTTP/2 multiplexing
✅ Strongly coupled (by design)
```

**System Shape (gRPC):**

```
Lower latency
├─ Binary serialization (faster than JSON)
├─ HTTP/2 multiplexing (fewer round trips)
├─ Connection reuse

Tighter coupling
├─ Strong types = strong contracts
├─ Breaking changes are caught at compile time
├─ Clients and servers must stay in sync

Harder evolution
├─ Adding fields requires careful planning
├─ Backward compatibility requires discipline
├─ Removing fields breaks clients
```

### 🔥 Key Insight

```
REST vs gRPC is NOT about JSON vs Protobuf.

It's about COUPLING, EVOLUTION, and OPERABILITY.

Choose based on:
✅ How tightly coupled can you be?
✅ How quickly will contracts change?
✅ Do you need external/browser access?
```

---

## 📊 REST vs gRPC Decision Matrix

| Context | Prefer | Why |
|---------|--------|-----|
| **External / client-facing** | REST | Browser-friendly, caching, proxies |
| **Internal, stable APIs** | gRPC | Performance, strong types |
| **Internal, evolving APIs** | REST | Easier versioning, compatibility |
| **Performance-critical** | gRPC | Lower latency, binary |
| **High-churn domain** | REST | Handle breaking changes better |
| **Mobile clients** | REST | Better mobile support, bandwidth |
| **Real-time streams** | gRPC | HTTP/2 bidirectional streaming |
| **Partner integrations** | REST | Easier for external teams |
| **Microservices mesh** | gRPC | Performance, can use mutual TLS easily |

### Interview-Ready Line

```
"We use REST for external APIs and APIs that evolve frequently.
We use gRPC internally for performance-critical paths where the
contract is stable and we control both client and server."
```

---

## 🔗 Request-Response Communication

### When to Use Request-Response

```
✅ You need an immediate answer
✅ One service calls another
✅ Failure is clear (success or timeout)
✅ Response is used immediately
```

**Examples:**

```
✅ Get account balance
✅ Validate transaction
✅ Check authorization
✅ Query read model
```

### Request-Response Pattern

```
Client Service
  ↓
[REST / gRPC Call]
  ↓
Server Service
  ├─ Process
  ├─ Response (success or error)
  └─ Return immediately
  ↓
Client Service (continues)
```

### Characteristics

| Aspect | Detail |
|--------|--------|
| **Coupling** | Tight (caller waits) |
| **Latency** | Network latency (ms) |
| **Failure** | Timeout or error |
| **Scalability** | Caller blocked until response |
| **Observability** | Simple (single round trip) |

### Dangers of Request-Response

```
❌ Synchronous chains
   └─ Service A → B → C → D → Response
   └─ Latency multiplied
   └─ Any service slow = entire flow slow

❌ No bulkhead isolation
   └─ Dependency failure = caller fails
   └─ Cascading failures

❌ Tight temporal coupling
   └─ All services must be up simultaneously
   └─ No graceful degradation
```

---

## 📡 Pub/Sub Communication (Event-Based)

### When to Use Pub/Sub

```
✅ Workflow coordination (multiple steps)
✅ Decoupling is important
✅ Response not needed immediately
✅ Fire-and-forget pattern
✅ Many consumers
```

**Examples:**

```
✅ AccountCreated event → multiple systems react
✅ PaymentProcessed event → ledger, notifications, analytics
✅ TransactionCompleted event → risk checks, reporting
```

### Pub/Sub Pattern

```
Publisher (Service A)
  ├─ Publishes: PaymentProcessed event
  ├─ Doesn't wait for responses
  └─ Returns immediately

[Event Broker (Kafka, Pub/Sub)]

Subscribers (Services B, C, D, E)
  ├─ Service B: Update ledger
  ├─ Service C: Send notification
  ├─ Service D: Risk check
  └─ Service E: Analytics
```

### Characteristics

| Aspect | Detail |
|--------|--------|
| **Coupling** | Loose (publisher doesn't know subscribers) |
| **Latency** | Event queue latency (ms to seconds) |
| **Failure** | Subscriber failure doesn't affect publisher |
| **Scalability** | Publisher never blocked |
| **Observability** | Complex (many consumers) |

### Advantages Over Request-Response

```
✅ Loose coupling (publisher unaware of subscribers)
✅ Scalable (publisher not blocked)
✅ Resilient (failure in one consumer doesn't affect others)
✅ Easy to add consumers (no code change in publisher)
✅ Clear audit trail (event log)
```

---

## 🌀 Fan-Out / Fan-In Patterns (Critical to Master)

### Fan-Out: One → Many

```
One service triggers many downstream actions
```

#### Safe Form: Async Fan-Out

```
Publisher publishes event
  ↓
[Event Broker]
  ├→ Consumer A (async)
  ├→ Consumer B (async)
  ├→ Consumer C (async)
  └→ Consumer D (async)

Characteristics:
✅ Decoupled (broker mediates)
✅ Scalable (publisher not blocked)
✅ Resilient (failure isolation)
```

**Example:**

```
Account Created event
  ├→ Notification Service: Send welcome email
  ├→ Analytics Service: Log signup
  ├→ Compliance Service: Check AML
  └→ Ledger Service: Initialize account
```

#### Dangerous Form: Sync Fan-Out

```
Service A calls B, then C, then D, then E synchronously

Client
  ↓
Service A
  ├ sync call → Service B (blocks)
  ├ sync call → Service C (blocks)
  ├ sync call → Service D (blocks)
  └ sync call → Service E (blocks)

Characteristics:
❌ Latency explosion (sum of all calls)
❌ Failure amplification (any failure blocks all)
❌ Tight coupling
❌ Cascading failures
```

### Fan-In: Many → One

```
Many services contribute to one result
```

#### Bad Approach: Synchronous Aggregation

```
❌ Aggregating Service
  ├ sync call → Service A
  ├ sync call → Service B
  ├ sync call → Service C
  └ wait for all responses

Problems:
├─ Latency: max(A, B, C)
├─ Failure: if any fails, aggregation fails
├─ Scalability: blocking operation
```

#### Good Approaches

**Option 1: CQRS Read Model**

```
Services publish events
  ├→ Event: Account Created
  ├→ Event: Balance Changed
  └→ Event: Limits Updated

[Read Model Projector]
  ├ Subscribes to all events
  └ Builds denormalized view

Aggregating Service
  └ One query to read model → everything

Characteristics:
✅ No blocking
✅ Highly available (pre-computed)
✅ Scalable (eventual consistency)
```

**Option 2: BFF (Backend for Frontend)**

```
Mobile Client
  ↓
[BFF Service]
  ├ call → Account Service
  ├ call → Ledger Service
  ├ call → Limits Service
  └ Aggregate + transform response

Client sees:
{
  account: {...},
  balance: {...},
  limits: {...}
}

Characteristics:
✅ Calls happen in BFF (internal network)
✅ Client makes one call
✅ Can retry failed calls safely
```

**Option 3: Event-Built Projection**

```
Multiple services publish events
  ├→ Payment Service: PaymentInitiated
  ├→ Ledger Service: LedgerPosted
  ├→ Risk Service: RiskCheckPassed
  └→ Settlement Service: SettlementConfirmed

[Projection Builder]
  ├ Subscribes to all events
  └ Builds view of "TransactionStatus"

Query returns:
{
  paymentId: "...",
  status: "settled",
  ledgerPosted: true,
  riskChecked: true
}

Characteristics:
✅ Highly available
✅ Eventual consistency
✅ All data pre-computed
```

### 🔥 Architect Rule

```
NEVER do fan-out + fan-in synchronously across services.

That's a distributed monolith in disguise.

Pattern:
❌ Service A → calls B, C, D synchronously
❌ Each call returns
❌ A aggregates results
❌ Returns to client

This violates:
├─ Loose coupling (A depends on all)
├─ Failure isolation (failure amplification)
├─ Scalability (blocking chains)

Instead:
✅ Fan-out asynchronously (events)
✅ Pre-compute aggregations (CQRS, BFF)
✅ Never sync chains
```

---

## 🛡️ Bulkhead Isolation (Critical for Resilience)

### What Bulkhead Isolation Is

```
Failures in one interaction path must NOT sink the entire service
```

**Analogy:**

```
Ship's bulkheads:
├─ Watertight compartments
├─ One compartment floods
├─ Other compartments stay dry
├─ Ship survives
```

**In microservices:**

```
Service Isolation:
├─ Different dependency groups
├─ Separate resources (threads, connections)
├─ Failure in one group ≠ affects others
├─ Service survives
```

### Bulkhead Dimensions

#### 1. Thread Pool Bulkheads

```
Savings Service handles:
├─ Ledger calls (critical)
├─ Notification calls (non-critical)

Configuration:
├─ Ledger pool: 20 threads
├─ Notification pool: 5 threads

Scenario:
└─ Notification service hangs
    ├─ Notification threads exhausted
    ├─ Ledger threads unaffected
    ├─ Ledger operations continue
    └─ Notifications degrade gracefully
```

#### 2. Connection Pool Bulkheads

```
Database connections:
├─ Read-only pool: 50 connections
├─ Write pool: 20 connections
├─ Analytics pool: 10 connections

Scenario:
└─ Analytics query runs amok
    ├─ Consumes only 10 connections
    ├─ Read/write pools unaffected
    ├─ Core operations continue
    └─ Analytics degrades
```

#### 3. Resource Quota Bulkheads

```
Memory / CPU:
├─ Critical operations: 70% available
├─ Batch jobs: 20% available
├─ Monitoring: 10% available

Scenario:
└─ Batch job runs hot
    ├─ Limited to 20% CPU
    ├─ Can't starve critical ops
    ├─ System remains responsive
```

### Bulkhead Strategy for Banking Example

```
Savings Service calls:

Critical Path (Ledger):
├─ Dedicated thread pool (30 threads)
├─ Dedicated connection pool (20 connections)
├─ Timeout: 5 seconds
├─ Circuit breaker: aggressive (fail fast)

Non-Critical Path (Notifications):
├─ Shared thread pool (10 threads)
├─ Shared connection pool (5 connections)
├─ Timeout: 30 seconds
├─ Circuit breaker: lenient (allow retries)

Result:
✅ Ledger failure doesn't block balance updates
✅ Notification failure doesn't affect balance
✅ Resources properly isolated
```

### 🔥 Key Insight

```
Bulkheads define which dependencies can fail together
and which must be isolated.

This SHAPES:
├─ Thread pool configuration
├─ Connection pool configuration
├─ Resource allocation
├─ Circuit breaker strategy
└─ Failure domains

Without bulkheads = cascading failures = system down
```

---

## ✨ Good Architecture Shape (The Ideal)

### Layers of Communication

```
External Clients
  ↓
[API Gateway] (REST)
  ├─ Auth
  ├─ Rate limiting
  └─ Request routing

Internal Clients
  ↓
[Services] 
  ├─ REST/gRPC for queries
  ├─ Pub/Sub for workflows
  ├─ Async fan-out for decoupling
  ├─ CQRS for aggregations
  └─ Bulkheads on all critical paths

Result:
✅ Resilient (failures isolated)
✅ Scalable (no blocking chains)
✅ Evolvable (loose coupling)
✅ Observable (clear patterns)
```

### Checklist for Good Shape

```
✅ REST/gRPC for queries
✅ Pub/Sub for workflows
✅ Async fan-out (no sync chains)
✅ CQRS for fan-in (no cross-service joins)
✅ Bulkhead isolation everywhere
✅ Circuit breakers on critical paths
✅ Timeouts enforced
✅ Retries with backoff
```

---

## 📊 Communication Pattern Decision Matrix (INTERVIEW GOLD)

| Requirement | Pattern | Why |
|-------------|---------|-----|
| **Immediate answer** | REST / gRPC | Built for request-response |
| **Workflow coordination** | Pub/Sub (events) | Clear ownership, decoupling |
| **Many consumers** | Event fan-out | Loose coupling, scalable |
| **Aggregated read** | CQRS / BFF | Pre-computed, no blocking |
| **Protect critical path** | Bulkheads | Isolation, failure containment |
| **One-way notification** | Pub/Sub | Fire-and-forget |
| **Performance-critical** | gRPC | Binary, HTTP/2 |
| **External APIs** | REST | Browser-friendly, standardized |

### Interview Line

```
"We match communication patterns to requirements. Queries use
REST/gRPC. Workflows use events. Aggregations use CQRS or BFFs.
Everything critical gets bulkhead isolation. This prevents
distributed monoliths and cascading failures."
```

---

## 🚨 Common Anti-Patterns (MEMORIZE THESE)

| Anti-Pattern | What Goes Wrong | Fix |
|--------------|-----------------|-----|
| ❌ Synchronous fan-out | Latency explosion, failure cascade | Use async events |
| ❌ Event-based RPC | Keeps coupling, adds async complexity | Use REST/gRPC for queries |
| ❌ No bulkhead isolation | One failure = cascade | Isolate resources |
| ❌ gRPC everywhere | Tighter coupling than necessary | Use REST for evolving APIs |
| ❌ Request-response for workflows | Sync chains across services | Use Saga + events |
| ❌ Cross-service joins | Tight coupling, tight timing | Use CQRS read models |
| ❌ Unprotected critical paths | Fragile under load | Add circuit breakers |
| ❌ No timeouts | Hanging requests | Enforce timeouts |

---

## 🚫 Event-Based RPC (Anti-Pattern Explained)

### What Event-Based RPC Is

```
Using messaging/events to simulate synchronous request-response
```

**Example (Wrong):**

```
Service A publishes: GetAccountBalance event
Service B receives it
Service B computes balance
Service B publishes: AccountBalanceResult event
Service A receives it
Service A processes result

Problems:
├─ Keeps temporal coupling (A waits for result)
├─ Adds async complexity (messaging overhead)
├─ Harder to debug (distributed RPC)
├─ Harder to reason about (unclear flow)
└─ Event broker becomes RPC middleware
```

### Why Event-Based RPC Is Bad

| Aspect | Issue |
|--------|-------|
| **Coupling** | Still tightly coupled (waiting for response) |
| **Complexity** | Async machinery + sync semantics |
| **Observability** | Hard to trace correlation |
| **Performance** | Worse than direct REST/gRPC |
| **Semantics** | Events aren't meant for request-response |

### Proper Alternatives

| Need | Pattern | Why |
|------|---------|-----|
| **Immediate answer** | REST / gRPC | Built for this |
| **Async workflow** | Saga + events | Clear ownership |
| **Fire-and-forget** | Pub/Sub | No waiting |
| **Aggregation** | CQRS / BFF | Pre-computed |

### 🔥 Rule

```
Don't use events as RPC.

Events are for:
✅ Asynchronous workflows (Saga)
✅ Multi-consumer notifications (Pub/Sub)
✅ State changes (CQRS)

Events are NOT for:
❌ Simulating synchronous calls
❌ Request-response patterns
❌ RPC over messaging
```

---

## 🎤 Interview-Ready One-Paragraph Summary

```
"We choose communication patterns based on requirements, not
preferences. Queries use REST for external/evolving APIs or gRPC
for internal/stable APIs. Workflows use async Saga + events for
decoupling. Fan-out uses async event broadcasts. Fan-in uses
CQRS read models or BFFs—never sync aggregation across services.
Every critical path has bulkhead isolation to prevent cascading
failures. We avoid distributed monoliths by never doing
synchronous call chains, and we avoid event-based RPC by using
the right tool for each pattern."
```

**That demonstrates communication mastery.**

---

## ✅ 10-Minute Pre-Interview Checklist

- ✅ Can I explain REST vs gRPC decision?
- ✅ Do I know when to use Pub/Sub vs request-response?
- ✅ Can I explain safe vs dangerous fan-out?
- ✅ Can I explain CQRS and BFF fan-in patterns?
- ✅ Do I understand bulkhead isolation?
- ✅ Can I list 5+ communication anti-patterns?
- ✅ Do I know why event-based RPC is wrong?
- ✅ Can I identify distributed monoliths in communication?

**If yes to all → COMMUNICATION PATTERNS MASTERY ✅**

---

## 📝 Summary

### Choose the Right Pattern

```
REST:
✅ External, evolving APIs
✅ Browser/mobile clients
✅ Easy versioning

gRPC:
✅ Internal, stable APIs
✅ Performance-critical
✅ Strong typing

Pub/Sub:
✅ Workflows, decoupling
✅ Multiple consumers
✅ Fire-and-forget

CQRS / BFF:
✅ Aggregated reads
✅ No cross-service joins
✅ Pre-computed data
```

### Avoid These Traps

```
❌ Sync fan-out (call many services)
❌ Event-based RPC (events for requests)
❌ No bulkhead isolation
❌ Unprotected critical paths
❌ Distributed monoliths
```

### Ensure Resilience

```
✅ Bulkhead isolation
✅ Circuit breakers
✅ Timeouts
✅ Retries with backoff
✅ Async communication
```

### Final Interview Line

```
"Communication patterns aren't about technology—they're about
coupling, resilience, and scalability. We match patterns to
requirements and always ask: 'Can we fail gracefully here?'"
```


