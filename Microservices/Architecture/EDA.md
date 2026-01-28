# Event-Driven Architecture (EDA): Complete Guide

## Executive Summary

**Event-Driven Architecture** publishes immutable business facts and allows systems to react asynchronously.

**Core Principle:** Services communicate by publishing events (things that happened), not by issuing commands (things to do).

### What EDA Is

```
✅ Decoupling mechanism (services don't know about each other)
✅ Scalability enabler (async processing)
✅ Audit-friendly architecture (immutable event log)
```

### What EDA Is NOT

```
❌ Async RPC (events aren't remote procedure calls)
❌ Messaging everywhere (use selectively)
❌ Replacement for transactions (use Saga for that)
```

---

## 🎯 Events vs Commands (Critical Distinction)

### Events

```
Immutable FACTS about something that already happened
```

**Characteristics:**
- Past tense ("MoneyWithdrawn", "AccountActivated")
- Declarative ("what happened")
- Broadcast to many listeners
- No expected response
- Zero, one, or many consumers

**Examples:**
```
PaymentProcessed
AccountCreated
BalanceUpdated
TransactionFailed
```

### Commands

```
Requests to PERFORM an action
```

**Characteristics:**
- Imperative ("do something")
- Targeted to one specific consumer
- Expect success or failure response
- May be rejected
- Always exactly one handler

**Examples:**
```
WithdrawMoneyCommand
PostLedgerEntryCommand
SendNotificationCommand
UpdateAccountStatusCommand
```

### 🔥 Key Insight

```
Commands and events may share a broker, but their semantics differ.

Latency? Commands (synchronous, immediate)
Audit trail? Events (asynchronous, recorded)
Consistency? Commands (must succeed/fail)
Flexibility? Events (any number of consumers)
```

| Aspect | Commands | Events |
|--------|----------|--------|
| **Direction** | Request to act | Announcement of fact |
| **Consumers** | Exactly 1 | 0, 1, or many |
| **Response** | Expected (yes/no) | None |
| **Tense** | Imperative | Past |
| **Rollback** | Possible | No (immutable) |

---

## 📰 Domain Events vs Integration Events

### Domain Events

```
Internal to a bounded context
```

**Characteristics:**
- Raised by aggregates
- Reflect domain state changes
- Not stable contracts
- Only meaningful within context
- Can change freely

**Example:**
```
Within AccountContext:
  MoneyWithdrawn (domain event)
  └─ Not meant for external consumption
```

### Integration Events

```
Published outside bounded context
```

**Characteristics:**
- Stable, versioned contracts
- Safe for cross-service use
- Long-term stability required
- Breaking changes must be explicit
- Consumers depend on them

**Example:**
```
To other services:
  BalanceChanged (integration event)
  └─ ├─ Ledger Service listens
  └─ ├─ Notification Service listens
  └─ └─ Risk Service listens
```

### 🔥 Rule

```
NEVER expose domain events directly to other services.

Instead:
1. Raise domain event internally
2. Translate to integration event
3. Publish integration event
4. Other services depend on stable contract
```

### Flow

```
Aggregate
  └─ Raises: MoneyWithdrawn (domain event)
      └─ Translated to: BalanceChanged (integration event)
          └─ Published to: all subscribers
              ├─ Ledger Service
              ├─ Notification Service
              └─ Risk Service
```

---

## 📋 Event Structure & Metadata (Best Practice)

### Typical Event Structure

```json
{
  "eventId": "evt_abc123def",
  "eventType": "balanceChanged",
  "version": 1,
  "timestamp": "2026-01-28T10:00:00Z",
  "correlationId": "req_xyz789",
  "aggregateId": "acc_123",
  "aggregateType": "SavingsAccount",
  "payload": {
    "accountId": "acc_123",
    "previousBalance": 5000,
    "newBalance": 4900,
    "change": -100,
    "reason": "Withdrawal"
  }
}
```

### Metadata Purpose

| Field | Purpose |
|-------|---------|
| **eventId** | Deduplication (detect duplicates) |
| **eventType** | Routing (which handler?) |
| **version** | Schema evolution (backwards compatibility) |
| **timestamp** | Ordering & auditing |
| **correlationId** | Tracing (connect across services) |
| **aggregateId** | Partitioning (Kafka partition key) |

### Metadata Enables

```
✅ Tracing (correlationId across services)
✅ Replay (know what happened and when)
✅ Deduplication (detect duplicate events)
✅ Debugging (full event history available)
✅ Audit compliance (immutable log)
```

---

## 🔄 Event Ordering & Delivery Semantics

### Ordering Guarantees

```
Per-partition ordering:
├─ Events with same key are ordered
├─ Example: Account ID = partition key
└─ All events for account_123 arrive in order

Global ordering:
├─ NOT guaranteed
├─ Events from different accounts may be out of order
└─ Acceptable in most systems
```

### Design Rule

```
Partition by business key (e.g., accountId)

Example (Kafka):
├─ Topic: balance-changed
├─ Partition key: accountId
├─ Result: All events for same account in same partition
└─ → Ordering preserved per account
```

### Delivery Semantics (The Reality)

```
EDA works with:
├─ At-least-once delivery (events may repeat)
├─ Possible duplicates (handle via idempotency)
├─ Possible delays (events arrive late)
└─ NOT exactly-once (illusion in distributed systems)
```

### 🔥 Truth

```
Exactly-once delivery DOES NOT EXIST in distributed systems.

Stop looking for it. Instead:
├─ Design for at-least-once delivery
├─ Make consumers idempotent
├─ Handle duplicates gracefully
└─ Accept eventual consistency
```

---

## ✅ Idempotency (Non-Negotiable)

### Why It Matters

```
Duplicate events happen:
├─ Network timeouts → retry → duplicate event
├─ Consumer crashes → reprocessed event
├─ Kafka offset reset → older events reprocessed
└─ Consumers MUST handle duplicates gracefully
```

### Requirements for Idempotent Consumers

```
✅ Detect duplicates (via eventId)
✅ Ignore reprocessing (store processed eventIds)
✅ Be state-aware (check current state before updating)
```

### Common Techniques

#### 1. Event ID Deduplication

```java
// Track processed events
CREATE TABLE processed_events (
  eventId VARCHAR PRIMARY KEY,
  processedAt TIMESTAMP
);

// Consumer logic
public void handleBalanceChanged(BalanceChangedEvent event) {
  if (alreadyProcessed(event.eventId)) {
    logger.info("Duplicate event ignored: {}", event.eventId);
    return;
  }
  
  // Process event
  updateBalance(event);
  
  // Mark as processed (atomic with business logic)
  recordProcessed(event.eventId);
}
```

#### 2. Version/Timestamp Checks

```java
// Only process if newer than current state
public void handleBalanceChanged(BalanceChangedEvent event) {
  Account current = loadAccount(event.accountId);
  
  if (current.lastUpdateTime >= event.timestamp) {
    // Event is older than current state, ignore
    return;
  }
  
  // Safe to process
  current.balance = event.newBalance;
  current.lastUpdateTime = event.timestamp;
  save(current);
}
```

#### 3. State-Based Idempotency

```java
// Only apply change if it makes sense for current state
public void handleAccountActivated(AccountActivatedEvent event) {
  Account account = loadAccount(event.accountId);
  
  if (account.status == "ACTIVE") {
    // Already active, skip
    return;
  }
  
  account.status = "ACTIVE";
  save(account);
}
```

---

## 📝 Event Schema Versioning (VERY IMPORTANT)

### Non-Breaking Changes (Safe)

```
✅ Add optional fields (new consumers ignore)
✅ Never remove fields (old consumers break)
✅ Never change meaning (consumers confused)

Example (Safe):
{
  "accountId": "acc_123",
  "balance": 5000,
  "newField": "value"  ← OK, consumers ignore
}
```

### Breaking Changes (Must Be Explicit)

```
❌ Renaming fields (consumers expect old name)
❌ Removing fields (old consumers fail)
❌ Changing data types (deserialization fails)
❌ Changing semantics (wrong interpretation)

Example (Breaks):
Before: { "accountId": "acc_123", "amount": 100 }
After:  { "accountId": "acc_123", "value": 100 }
        └─ Renamed "amount" to "value" → BREAKING
```

### Versioning Strategies

| Strategy | When Used | Example |
|----------|-----------|---------|
| **Versioned event names** | Banking-preferred | `BalanceChanged_V1`, `BalanceChanged_V2` |
| **Version field in payload** | Mature teams | `{ "version": 2, ...payload }` |
| **Parallel topics** | External consumers | `balance-changed-v1`, `balance-changed-v2` |

### Recommended Approach (Banking)

```
1. Use versioned event names
2. Publish only latest version
3. Maintain backward compatibility window
4. Explicit deprecation periods (often 1-2 years)
```

---

## 🚀 Safe Rollout of Breaking Changes (Step-By-Step)

### Example: Change "amount" to "value"

#### Step 1: Introduce New Version

```
Create new event:
├─ BalanceChanged_V2 (with "value" field)
└─ Keep BalanceChanged_V1 (with "amount" field)
```

#### Step 2: Publish Both in Parallel

```java
public void withdrawMoney(Account account, BigDecimal amount) {
  // Business logic
  account.withdraw(amount);
  
  // Publish BOTH versions temporarily
  publishEvent(new BalanceChanged_V1(account, amount));
  publishEvent(new BalanceChanged_V2(account, amount));
}
```

#### Step 3: Migrate Consumers Gradually

```
Timeline:
├─ Week 1: Deploy new event
├─ Week 2-4: New consumers use V2, old use V1
├─ Week 5-8: Migrate remaining consumers to V2
└─ Week 9: Stop publishing V1
```

#### Step 4: Monitor & Validate

```
✅ All consumers on V2?
✅ Old consumers fully migrated?
✅ No remaining V1 subscribers?
└─ THEN stop publishing V1
```

#### Step 5: Deprecation Window

```
❌ Never immediately retire V1
✅ Keep V1 support for extended period (1-2 years)
✅ Communicate deprecation clearly
✅ Slow, explicit phase-out
```

### 🔥 Golden Rule

```
Breaking changes should be rare.
When they happen:
├─ Publish new version in parallel
├─ Migrate consumers gradually
├─ Keep old version for years
└─ Never surprise consumers
```

---

## 📚 Schema Registry (Practical Role)

### What It Does

```
✅ Enforces schema compatibility
✅ Prevents breaking publishes
✅ Documents contracts
✅ Tracks schema evolution
```

### How It Works

```
Publish Event
  ↓
[Schema Registry Check]
  ├─ Is it compatible with subscribers?
  ├─ Backward compatible? ✅
  ├─ Breaking change? ❌ REJECT
  └─ Enforce contracts
```

### Banking Preference

```
✅ Backward compatibility only
  └─ New schema reads old data, old schema reads new data
✅ Long deprecation windows
  └─ 1-2 years for breaking changes
✅ Explicit versioning
  └─ Consumers know what they're getting
```

---

## 🔄 Event Replay (Why & How)

### Why Replay Exists

```
✅ New consumer (catch up on history)
✅ Bug fix (reprocess events correctly)
✅ Read-model rebuild (recreate from scratch)
✅ Analytics backfill (populate new dataset)
```

### 🔥 Golden Rule

```
Replay EVENTS, never COMMANDS.

Wrong: Replay commands → might do wrong things
Right: Replay events → replaying facts is idempotent
```

### Replay Types

#### Full Replay (Cold Start)

```
Used when:
├─ New service
├─ New read model
├─ Complete rebuild needed

How it works:
├─ Consumer starts from offset 0
├─ Processes ALL historical events
├─ Builds state from scratch

Requirements:
✅ Events immutable (never change)
✅ Consumers idempotent (can replay safely)
```

#### Selective/Partial Replay (Most Common)

```
Used when:
├─ Fixing a bug
├─ Reprocessing time window
├─ Recalculating specific aggregate

How it works:
├─ Copy events to replay topic, OR
├─ Reset consumer offset for time range
├─ Replay only affected keys/window

Benefits:
✅ Faster (not full history)
✅ Safer (targeted)
✅ Auditable (clear what was replayed)
```

### Replay Implementation Patterns

| Pattern | Approach | Safety | Notes |
|---------|----------|--------|-------|
| **Broker offset reset** | Kafka offset change | Low risk | Simple but less control |
| **Replay topic** | Copy events to new topic | High risk → High safety | More auditable, safer |
| **Event store** | Query and replay from store | Highest | Advanced, ledger-grade |

### Replay Safety Rules (Non-Negotiable)

```
✅ Consumers are idempotent
✅ Side effects are controlled
✅ Replay mode is supported
✅ Strong audit logging
```

### Example: Replay Topic Pattern

```
Original Topic: balance-changed

Replay Scenario (bug fix):
1. Identify affected events
2. Copy to: balance-changed-replay
3. Consumer subscribes to replay topic
4. Processes with fixed logic
5. Validates results
6. Only THEN updates production consumer offset
```

---

## ⚠️ Error Handling in EDA

### When Consumers Fail

```
Consumer fails to process event:
├─ Retry with backoff (exponential)
├─ After N retries → send to DLQ
├─ Alert operations team
├─ Manual or automated replay
└─ Root cause fix
```

### Strategy

```
Immediate retry:
└─ 3 quick retries (1s, 5s, 30s)

Escalating retry:
└─ Then slower retries (5m, 30m, 2h)

Dead Letter Queue (DLQ):
└─ After all retries exhausted
└─ Requires manual intervention

Automated Recovery:
└─ Fix code
└─ Replay from DLQ
└─ Back to normal
```

### 🔥 Important

```
Events are NOT rolled back—they are:
├─ Replayed (re-process with fixed code)
├─ Compensated (business correction)
└─ Never technically "undone"
```

---

## 🔗 EDA + CQRS + Saga (How It Fits)

### Integration Pattern

```
Command → Aggregate → Domain Event
              ↓
         Integration Event
              ↓
    ┌────────┴────────┬─────────────┐
    ↓                 ↓             ↓
Read Model      Saga Step      Other Services
Update         Advancement      React
(CQRS)         (Saga)          (EDA)
```

### Example: Money Transfer

```
1. Command: TransferMoneyCommand
   └─ orchestrator receives

2. Aggregate state changes
   └─ Raises: MoneyTransferred event

3. Integration event published
   └─ Triggers multiple consumers:
      ├─ BalanceService updates read model (CQRS)
      ├─ Saga advances to next step
      └─ NotificationService sends email

Result:
✅ Decoupled flow
✅ Auditable via events
✅ Scalable processing
```

---

## ✅ When EDA Is a GOOD Fit

### Apply EDA When:

```
✅ High decoupling needed (services don't need to know each other)
✅ Multiple consumers (many services care about same event)
✅ Async workflows (don't need immediate response)
✅ Audit & traceability (regulatory requirements)
✅ Scalability (process independently)
```

### Typical Financial Services Use Cases

```
✅ Ledger posting (multiple systems listen)
✅ Notifications (email, SMS, push)
✅ AML / Risk (check transactions)
✅ Reporting & Analytics (historical data)
✅ Read model updates (CQRS)
```

---

## ❌ When EDA Is a BAD Fit

### Skip EDA When:

```
❌ Simple CRUD systems (overhead not justified)
❌ Strong cross-service consistency (use Saga instead)
❌ Tight latency SLAs (async adds delay)
❌ Small teams / simple domains (operational burden)
❌ Immediate response required (sync is better)
```

### Interview-Ready Line

```
"EDA adds operational complexity, so we apply it selectively
to domains where the decoupling and auditability are justified."
```

---

## ✨ Best Practices (Memorize These)

| Practice | Why |
|----------|-----|
| **Events are facts, not instructions** | Immutable, broadcast-safe |
| **Design for at-least-once delivery** | Exactly-once doesn't exist |
| **Use explicit versioning** | Prevents silent breaking changes |
| **Make consumers idempotent** | Handle duplicates safely |
| **Prefer parallel version rollout** | Gradual migration, no surprises |
| **Treat replay as first-class feature** | Rebuilding is normal, should be easy |
| **Invest in observability** | Debug complex async flows |

---

## 🚨 EDA Anti-Patterns (INTERVIEW GOLD)

| Anti-Pattern | Why It's Wrong | Fix |
|--------------|----------------|-----|
| ❌ Treating events as RPC | Events aren't requests | Events are facts, not instructions |
| ❌ Publishing internal domain events | Creates coupling | Translate to integration events |
| ❌ Silent schema breaking changes | Breaks consumers silently | Explicit versioning + parallel publish |
| ❌ Non-idempotent consumers | Duplicates cause errors | Deduplication + state checks |
| ❌ Using events for strict workflows | Can't enforce ordering | Use Saga for critical workflows |
| ❌ No replay or DLQ strategy | Can't recover from bugs | Replay and DLQ are mandatory |
| ❌ Too many event types | Event explosion, coupling | Design event schema carefully |
| ❌ No schema registry | Breaking changes slip through | Enforce compatibility checks |

---

## 🎤 Interview-Ready One-Paragraph Summary

```
"Event-Driven Architecture publishes immutable business facts
and allows systems to react asynchronously. We treat events as
long-lived contracts, enforce strict schema evolution rules, and
design for at-least-once delivery with idempotent consumers.
Breaking changes are handled via versioned events published in
parallel, and recovery is achieved through controlled replay
rather than rollback. This enables decoupling, scalability, and
auditability—exactly what financial systems need."
```

**That paragraph alone demonstrates strong EDA mastery.**

---

## 📊 EDA vs Sync Communication

| Dimension | Sync (REST/gRPC) | Async (EDA) |
|-----------|------------------|------------|
| **Latency** | Immediate | Delayed |
| **Coupling** | Tight (caller knows callee) | Loose (via events) |
| **Scalability** | Limited (blocked waiting) | High (independent) |
| **Reliability** | Failure cascades | Isolated failures |
| **Auditability** | Implicit | Explicit (event log) |
| **Use for** | Queries, immediate responses | Facts, workflows, notifications |

---

## ✅ 10-Minute Pre-Interview Checklist

- ✅ Can I explain events vs commands clearly?
- ✅ Do I know safe vs breaking schema changes?
- ✅ Can I explain replay implementation?
- ✅ Can I list 5+ EDA anti-patterns?
- ✅ Do I understand at-least-once delivery?
- ✅ Can I explain idempotency mechanisms?
- ✅ Do I know when to use vs not use EDA?
- ✅ Can I describe full integration with CQRS + Saga?

**If yes to all → EDA COMPLETE ✅**

---

## 📝 Summary

### Core Principles

```
✅ Events are immutable facts
✅ Services decouple via events
✅ Eventual consistency is acceptable for reads
✅ Consumers must be idempotent
✅ Schema versioning is critical
✅ Replay is a first-class feature
```

### Key Rules

```
❌ Don't use events for strict consistency
❌ Don't expose domain events externally
❌ Don't break schema silently
❌ Don't make non-idempotent consumers
✅ Do design for at-least-once delivery
✅ Do version events explicitly
✅ Do support replay operations
```

### When It Shines

```
EDA is perfect for:
✅ Decoupled services
✅ Multiple consumers
✅ Audit trails
✅ Scalable processing
✅ Financial workflows
```

### Final Interview Line

```
"EDA isn't a default—we apply it where the decoupling and
auditability justify the operational complexity. We design for
at-least-once delivery, enforce schema compatibility, make all
consumers idempotent, and treat replay as a mandatory feature."
```
