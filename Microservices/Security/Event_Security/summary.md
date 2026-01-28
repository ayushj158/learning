# Event & Messaging Security: Complete Guide

## Executive Summary

**Event and messaging security** treats events as internal APIs with strict producer/consumer controls:

- Producers must be strongly authenticated and explicitly authorized to publish to specific topics
- Consumers must be least-privileged and validate events defensively
- Sensitive data should be avoided in event payloads due to durability and replay risks
- Encryption beyond transport is used selectively for high-risk data
- Replay is a powerful feature but must be controlled through idempotency, authorization, auditing, and governance

**Core Principle:** Events are internal APIs—they must be secured like APIs.

---

## 🎯 Why Event & Messaging Security Is Special

Messaging systems break the traditional request–response mental model, creating new risks.

### Key Differences from HTTP APIs

| Aspect | HTTP APIs | Messaging |
|--------|-----------|-----------|
| **Caller** | Direct (synchronous) | Indirect (asynchronous) |
| **Execution** | Immediate | Deferred |
| **Replays** | Rare | Easy (by design) |
| **Fan-out** | One response | Many consumers |
| **Visibility** | Request-response logs | Topic/queue access logs |

### New Risks Created

```
❌ No direct caller identity (asynchronous)
❌ No immediate feedback (deferred execution)
❌ Easy replay (feature becomes liability)
❌ Easy fan-out (unintended consumers)
❌ Often weak visibility (hard to trace)
❌ Durable storage (long-term exposure)
```

### Key Mindset

```
Events are internal APIs — they must be secured like APIs.
```

---

## 🔐 Platform-Level Authentication & Authorization

### Kafka
```
Authentication:
├─ mTLS (certificate-based)
└─ SASL (SCRAM / OAuth)

Authorization:
├─ ACLs per principal per topic
├─ Broker-level enforcement
└─ Producer vs Consumer permissions
```

### Cloud Pub/Sub / SNS
```
Authentication:
├─ IAM service identities
└─ Workload identity (GCP) / IRSA (AWS)

Authorization:
├─ IAM policies
├─ Role-based access
└─ Project-scoped permissions
```

---

## 🚨 Example Attack: Why Authentication Matters

### Scenario: Rogue Producer

```
Without Producer Authentication:
┌──────────────────────────────────────┐
│ Any service (or attacker) can:       │
│ ├─ Publish fake AccountCredited      │
│ ├─ Trigger payment processing        │
│ ├─ Update balances                   │
│ └─ Cause fraud (undetected)          │
└──────────────────────────────────────┘

With Producer Authentication:
┌──────────────────────────────────────┐
│ Only AccountService can publish      │
│ AccountCredited events               │
│ ├─ Identity verified (cert/token)    │
│ ├─ Authorization checked             │
│ └─ All events auditable              │
└──────────────────────────────────────┘
```

### Golden Rule

```
❌ "If you can't prove who produced the event, you can't trust it."
✅ Event authentication = proof of origin
```

---

## 📊 Master Reference: Event Security Controls

| Control | Purpose | How Implemented | Key Rules | Platform/App | Common Anti-Patterns |
|---------|---------|-----------------|-----------|-------------|----------------------|
| **Event Authentication** | Prove who produced event | mTLS, SASL, IAM service identities | Every producer has unique identity | Platform | ❌ Shared accounts, ❌ Anonymous |
| **Event Authorization** | Control publish/consume access | Topic-level ACLs, IAM policies | Auth ≠ AuthZ, least privilege | Platform | ❌ "All services read all topics" |
| **Topic-Level ACLs** | Fine-grained permissions | Per-topic publish/consume rights | Separate producer vs consumer | Platform | ❌ Wildcard topic access |
| **Producer Permissions** | Restrict who publishes | Write-only to specific topics | Producers publish facts only | Platform | ❌ Producer also consuming |
| **Consumer Permissions** | Restrict who consumes | Read-only topic access | Consumers should not write | Platform | ❌ Over-privileged consumers |
| **Sensitive Data Avoidance** | Prevent PII in events | Event design (IDs, not raw data) | Events ≠ data transport | Application | ❌ PAN/PII in payloads |
| **Event Encryption (Payload)** | Encrypt event content | Field/envelope encryption | Encrypt only when necessary | App + KMS | ❌ Encrypt everything |
| **Encryption in Transit** | Protect data on wire | TLS / mTLS | Mandatory baseline | Platform | ❌ Plaintext internal traffic |
| **Encryption at Rest** | Protect stored events | Broker-level encryption | Required for regulated data | Platform | ❌ Doesn't protect against misuse |
| **Replay Security** | Prevent malicious replays | Idempotency keys, eventId, timestamps | Replay must be controlled | Application | ❌ Blindly reprocessing |
| **Idempotent Consumers** | Handle safe duplicates | Deduplication via eventId | Accept "at least once" semantics | Application | ❌ Assuming "exactly once" |
| **Temporal Validity** | Check event freshness | Event time windows, age checks | Old events may be invalid | Application | ❌ Processing stale events |
| **Schema Validation** | Ensure event structure | Schema registry, validation | Validate before acting | App + Platform | ❌ Trusting producer blindly |
| **Defensive Consumer Checks** | Validate state transitions | Domain rules on consume | Events don't bypass invariants | Application | ❌ "Event says so, so do it" |
| **Replay Governance** | Control who can replay | Restricted tools, audit logs | Replay is privileged | Platform + Ops | ❌ Anyone can replay |
| **Auditability** | Trace event history | Producer identity in metadata | Every event attributable | Platform + App | ❌ No producer metadata |

---

## 🔐 Control Deep Dives

### 1️⃣ Event Authentication

**Purpose:** Prove the producer's identity and prevent fake events.

#### Implementation

**Kafka:**
```
SASL_SSL with SCRAM:
┌─────────────────────────────────┐
│ Producer                        │
│ ├─ Principal: account-service   │
│ ├─ Credential: SCRAM hash       │
│ └─ Transport: TLS               │
│       ↓                         │
│ Broker validates principal      │
│ ├─ Check credentials            │
│ ├─ Verify TLS cert              │
│ └─ Allow/deny                   │
└─────────────────────────────────┘
```

**Cloud Pub/Sub (GCP):**
```
Workload Identity:
┌─────────────────────────────────┐
│ Pod (Kubernetes)                │
│ ├─ Service Account: account-svc │
│ └─ IRSA annotation              │
│       ↓                         │
│ GCP Metadata Service            │
│ ├─ Verify pod identity          │
│ ├─ Issue identity token         │
│ └─ Token valid for scopes       │
│       ↓                         │
│ Pub/Sub validates token         │
│ ├─ Check service account        │
│ ├─ Check permissions (IAM)      │
│ └─ Allow publish                │
└─────────────────────────────────┘
```

#### Key Rules

- ✅ Every producer has unique identity
- ✅ Identity verified on every publish
- ✅ Credentials rotated regularly
- ✅ Audit logs track all publishes
- ❌ NO shared service accounts
- ❌ NO anonymous producers

---

### 2️⃣ Event Authorization (Topic-Level ACLs)

**Purpose:** Control which service can publish/consume specific topics.

#### Example ACLs

```
Topic: payments.AccountCredited
├─ Producer: account-service (WRITE)
├─ Consumer: billing-service (READ)
├─ Consumer: reporting-service (READ)
└─ NO: fraud-service (denied)

Topic: payments.TransactionCreated
├─ Producer: payment-service (WRITE)
├─ Consumer: ledger-service (READ)
├─ Consumer: analytics-service (READ)
└─ NO: accounting-service (denied, separate topics)

Topic: internal.SystemError
├─ Producer: ANY (WRITE)
├─ Consumer: monitoring-service (READ)
├─ Consumer: ops-team (READ)
└─ NO: business services
```

#### Principle: Least Privilege

```
❌ Over-privileged (wrong)
  payment-service can:
    ├─ Write to payments.*
    ├─ Read from payments.*
    ├─ Read from billing.*
    ├─ Read from analytics.*
    └─ Access all topics

✅ Least privilege (right)
  payment-service can:
    ├─ Write to payments.TransactionCreated
    ├─ Write to payments.TransactionFailed
    └─ Nothing else
```

---

### 3️⃣ Producer Permissions

**Purpose:** Restrict publishers to specific topics, preventing injection of false facts.

#### Design Pattern

```
Each domain publishes its own events:

AccountService publishes:
├─ accounts.AccountCreated
├─ accounts.AccountClosed
└─ accounts.BalanceUpdated

PaymentService publishes:
├─ payments.TransactionCreated
├─ payments.TransactionCompleted
└─ payments.TransactionFailed
```

#### ACL Configuration

```
account-service:
  Principal: account-service
  Permissions:
    - Topic: accounts.* (WRITE)
    - Topic: (nothing else)

payment-service:
  Principal: payment-service
  Permissions:
    - Topic: payments.* (WRITE)
    - Topic: (nothing else)
```

#### Anti-Pattern

```
❌ Cross-domain publishing
payment-service writes to accounts.BalanceUpdated
  └─ Violates domain isolation
  └─ Hides true source of fact
  └─ Makes auditing harder
  └─ Causes subtle bugs

✅ Domain event ownership
payment-service publishes payments.TransactionCompleted
account-service subscribes and updates balance
  └─ Clear causality
  └─ Auditable
  └─ Correct domain owner
```

---

### 4️⃣ Consumer Permissions

**Purpose:** Ensure consumers only read data they're authorized for, preventing lateral movement.

#### Example: Billing Service

```
billing-service needs:
├─ accounts.AccountCreated (trigger billing setup)
├─ accounts.BalanceUpdated (detect insufficient funds)
├─ payments.TransactionCompleted (generate invoice)
└─ (nothing else)

ACL:
billing-service:
  - Topic: accounts.AccountCreated (READ)
  - Topic: accounts.BalanceUpdated (READ)
  - Topic: payments.TransactionCompleted (READ)
```

#### Why Consumers Can't Write

```
❌ Wrong:
payment-service reads from payments.TransactionCompleted
payment-service can write to payments.TransactionFailed
  └─ Service can override its own events
  └─ Allows covering up fraud

✅ Right:
payment-service only WRITES to payment topics
billing-service only READS what it needs
  └─ Clear separation
  └─ Prevents tampering
```

---

### 5️⃣ Sensitive Data in Events (Avoid!)

**Purpose:** Events are durable and replayable; avoid PII in payloads.

#### The Problem

```
Account Created Event (BAD):
{
  "accountId": "a123",
  "email": "john.doe@example.com",          ❌ PII
  "ssn": "123-45-6789",                     ❌ PII
  "fullName": "John Doe",                   ❌ PII
  "address": "123 Main St",                 ❌ PII
  "creditCard": "4532 1234 5678 9010"       ❌ PII
}

Problems:
├─ Event stored in Kafka for 7 days
├─ Thousands of consumers may see it
├─ Audit logs contain it
├─ Backups contain it
└─ Security team must review all of it
```

#### The Solution

```
Account Created Event (GOOD):
{
  "accountId": "a123",
  "email": "john.doe@example.com"           ✅ OK (needed for routing)
}

Consumers needing details:
├─ Call account-service API
├─ Verify permissions
├─ Get full profile with proper auth
└─ No data leakage through events
```

#### Rule: Events Carry Identity, Not Data

```
✅ Good events:
{
  "accountId": "a123",
  "action": "created",
  "timestamp": "2026-01-28T10:00:00Z"
}

❌ Bad events (data transport):
{
  "accountId": "a123",
  "fullData": { ...entire customer object... }
}
```

---

### 6️⃣ Event Encryption (Payload)

**Purpose:** Encrypt sensitive event content when data must be in event payload.

#### When to Encrypt

```
Encrypt if:
✅ Event must contain sensitive data (rare)
✅ High regulatory/compliance requirement
✅ Multiple external systems subscribe

Don't encrypt if:
❌ Can avoid putting data in event (use ID instead)
❌ Only internal services subscribe
❌ Encryption adds little value
```

#### Implementation

```
Field-Level Encryption:
┌──────────────────────────────┐
│ Event                        │
│ {                            │
│   "accountId": "a123",       │
│   "email": "user@ex.com",    │
│   "encrypted_ssn": "..."     │ ← Encrypted
│   "encrypted_pan": "..."     │ ← Encrypted
│ }                            │
│       ↓                      │
│ Consumer decrypt with KMS    │
│ ├─ Have permission?          │
│ ├─ Have key?                 │
│ └─ Yes → decrypt             │
└──────────────────────────────┘
```

#### Key Management

```
Event Encryption Keys:
├─ One data key per event type
├─ Rotated quarterly
├─ Managed in KMS
├─ Access logged
└─ Only authorized consumers get key
```

---

### 7️⃣ Replay Security

**Purpose:** Control replay risks since events are replayable by design.

#### The Replay Problem

```
Event: Payment Completed
{
  "transactionId": "tx_123",
  "amount": 100,
  "accountId": "a456",
  "timestamp": "2026-01-28T10:00:00Z"
}

Risk: Consumer processes same event twice
├─ Ledger system doubles the amount
├─ Customer charged twice
├─ Fraud possible
└─ Operator replays old event
```

#### Defense 1: Idempotency Keys

```
Event Enhanced:
{
  "transactionId": "tx_123",
  "eventId": "evt_abc123def",    ← Unique per event
  "amount": 100,
  "timestamp": "2026-01-28T10:00:00Z"
}

Consumer (Ledger Service):
1. Receive event
2. Check: have we processed evt_abc123def?
3. If yes → skip (already processed)
4. If no → process & save eventId
```

#### Defense 2: Temporal Validity

```
Event with timestamp:
{
  "eventId": "evt_abc123",
  "amount": 100,
  "timestamp": "2026-01-28T10:00:00Z",
  "validUntil": "2026-02-28T10:00:00Z"  ← Expiry
}

Consumer validation:
if (now > event.validUntil) {
  reject();  // Too old, don't process
}
```

#### Defense 3: Event Sequence

```
Ledger events:
1. Transaction Started (seq=1)
2. Amount Deducted (seq=2)
3. Fee Applied (seq=3)
4. Transaction Completed (seq=4)

Consumer validates:
├─ seq(n-1) processed before seq(n)
├─ No gaps in sequence
└─ No out-of-order processing
```

---

### 8️⃣ Idempotent Consumers

**Purpose:** Safely handle duplicate event processing (at-least-once semantics).

#### The Contract

```
Messaging Guarantee: At-Least-Once
├─ Event delivered minimum once
├─ Event may be delivered multiple times
├─ Consumer must handle duplicates
└─ NOT exactly-once (don't assume)
```

#### Implementation

```
Ledger Service (Idempotent Consumer):

1. Receive Payment.Completed event
   └─ eventId: evt_abc123

2. Database: Store processed events
   CREATE TABLE processed_events (
     eventId VARCHAR PRIMARY KEY,
     timestamp TIMESTAMP
   );

3. Before processing:
   SELECT * FROM processed_events WHERE eventId = 'evt_abc123';
   
   If found:
     └─ Skip (already processed)
   
   If not found:
     └─ Process & INSERT into processed_events

4. Update ledger:
   INSERT INTO ledger (...) VALUES (...);
   INSERT INTO processed_events (eventId) VALUES ('evt_abc123');
   COMMIT;  ← Atomic
```

#### Anti-Pattern

```
❌ Assuming exactly-once
payment-service sends Payment.Completed
consumer assumes no duplicates
  └─ Double charges possible
  └─ Ledger gets out of sync
  └─ Bug manifests 6 months later

✅ Always idempotent
every consumer deduplicates
  └─ Safe under replay
  └─ Safe under failure
  └─ Predictable behavior
```

---

### 9️⃣ Schema Validation

**Purpose:** Ensure events conform to expected structure before processing.

#### Schema Registry

```
Event schema (schema-registry):

{
  "type": "record",
  "name": "PaymentCompleted",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "transactionId", "type": "string" },
    { "name": "amount", "type": "double" },
    { "name": "timestamp", "type": "string" }
  ]
}
```

#### Validation Points

```
1. Producer publishes:
   Schema registry validates before send
   ├─ Check all required fields
   ├─ Check types correct
   └─ Reject if invalid

2. Consumer receives:
   Consumer validates before processing
   ├─ Check schema version
   ├─ Handle backward compatibility
   └─ Reject if malformed
```

#### Backward Compatibility

```
Schema v1:
{
  "eventId": "...",
  "amount": 100
}

Schema v2 (added field):
{
  "eventId": "...",
  "amount": 100,
  "currency": "USD"  ← New field
}

Consumer (v2 code) reads v1 event:
├─ Missing currency field
├─ Use default: "USD"
└─ Continue (backward compatible)

Consumer (v1 code) reads v2 event:
├─ Extra currency field
├─ Ignore it
└─ Continue (forward compatible)
```

---

### 🔟 Defensive Consumer Checks

**Purpose:** Validate event logic, not blindly trust producer.

#### Pattern: State Validation

```
Bad (trusts event):
┌─────────────────────────────────┐
│ Event: Account.BalanceUpdated   │
│ { accountId, newBalance }       │
│                                 │
│ Consumer logic:                 │
│ UPDATE accounts                 │
│ SET balance = event.newBalance  │ ← Blind trust!
│ WHERE accountId = ...           │
└─────────────────────────────────┘

Good (validates state):
┌─────────────────────────────────┐
│ Event: Account.BalanceUpdated   │
│ { accountId, newBalance }       │
│                                 │
│ Consumer logic:                 │
│ 1. Fetch current balance        │
│ 2. Validate newBalance:         │
│    - > 0 (can't be negative)    │
│    - Reasonable delta           │
│    - Within daily limits        │
│ 3. If valid: UPDATE            │
│ 4. If invalid: REJECT & ALERT   │
└─────────────────────────────────┘
```

#### Invariant Checks

```
Account Transfer Event:
{
  "fromAccount": "a123",
  "toAccount": "a456",
  "amount": 1000
}

Defensive checks:
├─ fromAccount exists?
├─ toAccount exists?
├─ fromAccount has balance >= amount?
├─ Transfer doesn't violate limits?
├─ Accounts in valid states?
└─ If any fail: reject + alert
```

---

### 1️⃣1️⃣ Replay Governance

**Purpose:** Control who can manually replay events (privileged operation).

#### Replay Use Cases

```
Valid reasons to replay:
✅ Disaster recovery (restore from backup)
✅ Fix broken consumer (upgrade code, reprocess)
✅ Operational correction (data fix)

Invalid reasons:
❌ Anyone requests it
❌ No audit trail
❌ No approval required
```

#### Governance Controls

```
Replay Access:
├─ Restricted to ops/SRE only
├─ Requires approval ticket
├─ Audit logs everything
├─ Limited to specific date ranges
├─ Cannot replay future events
└─ Recorded with WHO/WHEN/WHY
```

#### Implementation

```
Kafka: Admin commands restricted
├─ Topic offset reset (requires admin cert)
├─ Consumer group reset
└─ All operations audited

Cloud Pub/Sub: Pub/Sub IAM
├─ pubsub.subscriptions.update (restricted)
├─ Seek operations logged
└─ Only service accounts with role
```

---

### 1️⃣2️⃣ Auditability

**Purpose:** Trace events to source (producer identity) for forensics and compliance.

#### Event Metadata

```
Complete Event:
{
  "eventId": "evt_abc123",
  "eventType": "accounts.AccountCreated",
  "producerId": "account-service",      ← WHO
  "timestamp": "2026-01-28T10:00:00Z",  ← WHEN
  "source": "production",                ← WHERE
  "version": "2",                        ← WHAT VERSION
  "payload": { ... }                     ← DATA
}
```

#### Audit Trail

```
Audit log entry:
{
  "eventId": "evt_abc123",
  "producer": "account-service",
  "brokerReceived": "2026-01-28T10:00:00.001Z",
  "consumerGroup": "billing-service",
  "consumerProcessed": "2026-01-28T10:00:02.500Z",
  "consumerHost": "billing-pod-123",
  "success": true
}
```

#### Forensics Enabled

```
Question: "Who created this account?"
Answer: Look at eventId → producer: account-service

Question: "When did billing-service process it?"
Answer: Look at audit log → timestamp: 10:00:02.500Z

Question: "Did fraud happen?"
Answer: Trace eventId through all systems → full causality
```

---

## 🎯 Security Principles Summary

### 1. Treat Events Like APIs

```
HTTP API:
├─ Authenticate caller
├─ Authorize action
├─ Validate input
└─ Log & audit

Event API:
├─ Authenticate producer
├─ Authorize publish
├─ Validate schema
└─ Log & audit

Same principles, different medium.
```

### 2. Least Privilege Access

```
❌ All services read all topics
✅ Each service reads only what it needs

❌ Service can write to any topic
✅ Service writes only to owned topics
```

### 3. Never Assume Internal = Safe

```
❌ "Events are internal, no auth needed"
✅ Internal = still untrusted, apply same rigor
```

### 4. Make Replay Safe

```
❌ Assume one-time processing
✅ Assume events processed multiple times
```

---

## ✅ Best Practices Checklist

### Authentication & Authorization
- ✅ Every producer has unique identity (cert/token)
- ✅ Topic-level ACLs enforced
- ✅ Producers write-only to owned topics
- ✅ Consumers read-only to required topics
- ✅ Cross-domain publishing prevented
- ✅ Audit logs track all access

### Event Design
- ✅ No PII in event payloads (use IDs instead)
- ✅ Events carry identity, not full data
- ✅ Schema defined & enforced
- ✅ Backward/forward compatibility checked
- ✅ Event versioning in place

### Consumer Safety
- ✅ Idempotent consumers (deduplication)
- ✅ Defensive validation (don't blindly trust)
- ✅ State transition checks
- ✅ Temporal validity checks
- ✅ Error handling & compensation

### Infrastructure
- ✅ TLS/mTLS for event traffic
- ✅ Encryption at rest for broker
- ✅ Field-level encryption for sensitive events
- ✅ Backups encrypted & access-controlled
- ✅ Key rotation policies in place

### Governance
- ✅ Replay only by ops (with approval)
- ✅ Full audit trail (producer → consumer)
- ✅ Monitoring for duplicate processing
- ✅ Alerts for unusual event patterns
- ✅ Incident response runbooks

---

## 🚨 Common Anti-Patterns

| Anti-Pattern | Why It's Wrong | Correct Approach |
|--------------|----------------|------------------|
| ❌ Unauthenticated producers | Any service can publish fake events | Require certificate/token per producer |
| ❌ All services read all topics | Data leakage, lateral movement | Least-privilege topic ACLs |
| ❌ Shared service accounts | Can't trace who published | Unique identity per producer |
| ❌ PII in event payloads | Long-term exposure, audit nightmare | Use IDs, fetch details via API |
| ❌ Trusting events blindly | Events become a vulnerability vector | Validate defensively in consumers |
| ❌ Assuming exactly-once | Duplicates will happen, cause bugs | Always idempotent |
| ❌ No replay control | Anyone can replay → fraud | Restrict & audit replay |
| ❌ No schema validation | Malformed events crash systems | Enforce schemas, validate always |
| ❌ Producer can also consume | Enables data hiding, tampering | Separate producer/consumer perms |
| ❌ No event ID / timestamp | Can't detect replay or duplicates | Include unique eventId + timestamp |
| ❌ Plaintext internal events | Insider access = data breach | TLS/mTLS + encryption as needed |
| ❌ No audit trail | Can't debug or investigate | Producer identity in every event |

---

## 📝 Summary: The Event Security Pyramid

```
┌───────────────────────────────┐
│     Auditability              │  (Traceability)
├───────────────────────────────┤
│   Consumer Governance         │  (Replay control, approved ops)
├───────────────────────────────┤
│  Defensive Validation         │  (Schema, state, invariants)
├───────────────────────────────┤
│  Replay Safety                │  (Idempotency, timestamps)
├───────────────────────────────┤
│  Data Protection              │  (Avoid PII, encrypt sensitive)
├───────────────────────────────┤
│  Authorization (ACLs)         │  (Topic-level permissions)
├───────────────────────────────┤
│  Authentication               │  (Producer identity)
├───────────────────────────────┤
│  Transport Security           │  (TLS/mTLS)
└───────────────────────────────┘

Each layer assumes breach of lower layers.
All layers required for FS-grade security.
```

**Final Rule:** Events are internal APIs. Secure them like APIs. Every producer, every consumer, every topic.

