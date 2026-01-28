# CQRS (Command Query Responsibility Segregation): Complete Guide

## Executive Summary

**CQRS** separates state-changing commands from read-only queries, using different models optimized for each operation.

**Core Problem It Solves:** In banking systems, writes and reads have fundamentally different characteristics:
- **Writes:** Critical, rule-heavy, low-volume, strongly consistent
- **Reads:** High-volume, UI/report-driven, performance-critical, eventually consistent

Using one model for both leads to complex code, broken invariants, and poor performance.

---

## 🎯 Why CQRS Exists (Real Problem)

### The Problem in One Picture

```
Traditional System (One Model):
┌──────────────────────────┐
│   Account Aggregate      │
├──────────────────────────┤
│ Commands:                │
│  ├─ WithdrawMoney        │ (enforces rules)
│  └─ Deposit              │
├──────────────────────────┤
│ Queries:                 │
│  ├─ GetBalance           │ (just reads)
│  ├─ GetTransactionList   │
│  └─ GetAccount Summary   │
└──────────────────────────┘
    Problem:
    ❌ One model must do everything
    ❌ Complex code (rules + performance)
    ❌ Invariants mixed with queries
    ❌ Poor performance (normalized data)
```

### Banking System Realities

| Aspect | Writes | Reads |
|--------|--------|-------|
| **Volume** | Low (100s/sec) | High (1000s/sec) |
| **Consistency** | Strong (critical) | Eventual (acceptable) |
| **Rules** | Many (invariants) | None |
| **Data** | Normalized | Denormalized |
| **Model** | Aggregate-based | Simplified |

### Result

Using one model for both leads to:
```
❌ Complex code
❌ Broken invariants
❌ Poor performance
❌ Difficult testing
❌ Tight coupling
```

**👉 CQRS solves this by separation.**

---

## 💡 Definition

```
CQRS separates:
├─ Write Model (Command): Enforces rules, ensures consistency
└─ Read Model (Query): Optimized for performance, denormalized
```

---

## ✍️ Command Side (Write Model)

### Responsibilities

```
Commands are responsible for:
✅ Handling state-changing operations
✅ Enforcing business rules (invariants)
✅ Using DDD aggregates
✅ Raising domain events
✅ Maintaining strong consistency
```

### Example: WithdrawMoney from Savings

#### Flow

```
1. Receive: WithdrawMoneyCommand
   └─ accountId: "acc_123"
   └─ amount: 100
       ↓
2. Load: SavingsAccount Aggregate
       ↓
3. Enforce Invariants:
   ✅ Account is ACTIVE?
   ✅ Balance ≥ amount?
   ✅ Amount > 0?
       ↓
4. Update State (atomic):
   └─ Deduct amount from balance
   └─ Record transaction
       ↓
5. Raise Domain Event:
   └─ MoneyWithdrawn event
   └─ Contains: accountId, amount, timestamp
```

#### Code Pattern

```java
// Command
public class WithdrawMoneyCommand {
  String accountId;
  BigDecimal amount;
}

// Aggregate (Command side only)
public class SavingsAccount {
  String accountId;
  BigDecimal balance;
  String status;
  
  public void withdrawMoney(BigDecimal amount) {
    // Enforce invariants
    if (!isActive()) 
      throw new AccountNotActiveException();
    if (balance.compareTo(amount) < 0)
      throw new InsufficientFundsException();
    
    // State change
    balance = balance.subtract(amount);
    
    // Raise event
    raiseEvent(new MoneyWithdrawnEvent(accountId, amount));
  }
}
```

### 🔑 Key Principle

```
Command side PRIORITIZES CORRECTNESS over speed.
```

---

## 📖 Query Side (Read Model)

### Responsibilities

```
Queries are responsible for:
✅ Handling read operations
✅ Returning denormalized, UI-ready data
✅ NO business rules
✅ NO state mutation
✅ Fast performance
```

### Example: GetSavingsAccountSummary

#### Flow

```
1. Receive: GetSavingsAccountSummaryQuery
   └─ accountId: "acc_123"
       ↓
2. Query Read Model:
   └─ SELECT balance, status, lastInterestDate FROM account_summary
       ↓
3. Return (flat, denormalized):
   ├─ Balance: 5000
   ├─ Status: ACTIVE
   ├─ Last Interest Date: 2026-01-15
   └─ Interest Rate: 2.5%
```

#### Code Pattern

```java
// Query
public class GetSavingsAccountSummaryQuery {
  String accountId;
}

// Result (flat structure, no logic)
public class SavingsAccountSummary {
  String accountId;
  BigDecimal balance;
  String status;
  LocalDate lastInterestDate;
  BigDecimal interestRate;
  
  // NO methods, just data
}

// Query Handler (just reads, no rules)
public class GetSavingsAccountSummaryHandler {
  public SavingsAccountSummary handle(GetSavingsAccountSummaryQuery query) {
    return db.queryOne(
      "SELECT * FROM account_summary WHERE accountId = ?",
      query.accountId
    );
  }
}
```

### 🔑 Key Principle

```
Query side PRIORITIZES SPEED & SIMPLICITY.
```

---

## 🚫 Golden Rules (Never Break These)

| Rule | Why |
|------|-----|
| ❌ Queries must NOT modify state | Breaks auditability, consistency |
| ❌ Commands must NOT return complex read views | Couples write to read concerns |
| ✅ Aggregates exist ONLY on command side | Read side has no state to enforce |

---

## 🔄 How Command & Query Stay in Sync

### The Synchronization Flow

```
Write Request
  ↓
[Command Handler]
  ├─ Load Aggregate
  ├─ Enforce rules
  ├─ Update state
  └─ Raise Domain Event: MoneyWithdrawn
      ↓
[Event Publisher]
  └─ Convert to Integration Event: BalanceChanged
      ↓
[Read Model Updater]
  └─ Update Read Model:
      └─ account_summary.balance = new_balance
  └─ Mark as eventually consistent
      ↓
[Query Handler]
  └─ Serves from Read Model
```

### Consistency Guarantees

| Operation | Consistency | When Used |
|-----------|-------------|-----------|
| **Command (Write)** | Strong | Always, non-negotiable |
| **Query (Read)** | Eventual | Acceptable for customer views |

```
Acceptable for banking reads:
✅ Account balance (eventual consistency fine)
✅ Transaction list (eventual consistency fine)
✅ Statements (eventual consistency fine)

NOT acceptable:
❌ Invariant checks (must be strong)
❌ Money movement (must be strong)
```

---

## 📊 CQRS + DDD Mapping

How CQRS fits with Domain-Driven Design:

| DDD Concept | CQRS Placement | Why |
|-------------|----------------|-----|
| **Aggregate** | Command side only | Enforces invariants |
| **Invariants** | Command side only | Critical for correctness |
| **Domain Events** | Command side | Raised by state changes |
| **Read Model** | Query side | Optimized for reads |
| **Integration Events** | Bridge between both | Updates read from writes |

---

## 📚 CQRS ≠ Event Sourcing

### Important Clarification

**CQRS and Event Sourcing are independent patterns:**

```
CQRS alone:
├─ Normal database for writes (command side)
├─ Separate database for reads (read model)
├─ Events used to sync read model
└─ No event store requirement

Event Sourcing (optional enhancement):
├─ All state changes stored as events
├─ Current state derived from events
├─ Can be combined with CQRS or used alone
└─ More complex, more auditable
```

### Banking Practice

```
Most banks use:
✅ CQRS (write side + read side separation)
❌ NOT full event sourcing (too complex for most flows)

Instead:
├─ Traditional DB for command side
├─ Separate read model DB
├─ Integration events to keep in sync
└─ Event logs for audit, not state reconstruction
```

---

## ✅ When CQRS Makes Sense

### Apply CQRS When:

```
✅ High read/write asymmetry (10:1 or more)
✅ Complex business rules (many invariants)
✅ Need for auditability (regulatory)
✅ Independent scaling (reads ≠ writes)
✅ Multiple read views needed
```

### Typical FS Use Cases

```
Perfect for:
├─ Savings & current accounts
├─ Ledger-impacting flows
├─ Transaction history
├─ Dashboards
├─ Reporting systems
```

---

## ❌ When NOT to Use CQRS

### Skip CQRS When:

```
❌ Simple CRUD systems
   └─ Overhead not justified

❌ Small teams
   └─ Adds operational complexity

❌ Identical read/write models
   └─ No benefit from separation

❌ Low-volume systems
   └─ Performance optimization not needed
```

### Interview-Ready Line

```
"CQRS adds complexity, so we applied it selectively to
high-value domains where the separation was justified."
```

---

## 📋 CQRS vs CRUD Comparison

| Aspect | CRUD | CQRS |
|--------|------|------|
| **Models** | One unified model | Separate write + read |
| **Rule enforcement** | Mixed everywhere | Command side only |
| **Data structure** | Normalized | Denormalized read model |
| **Read performance** | Joins, lookups | Direct, flat |
| **Scaling** | Difficult | Easier (read/write independent) |
| **Complexity** | Low | Medium |
| **Team cost** | Low | Higher |
| **Auditability** | Implicit | Explicit |

---

## 🎬 End-to-End Savings Flow (Concrete)

### Complete Example: Customer Withdraws Money

#### Step 1: User Action
```
UI: "Withdraw $100 from Savings"
```

#### Step 2: Command API
```
POST /savings/commands/withdraw-money
{
  "accountId": "acc_123",
  "amount": 100
}
```

#### Step 3: Command Handler
```
1. Load SavingsAccount aggregate from DB
2. Call: aggregate.withdrawMoney(100)
   └─ Checks invariants
   └─ Updates balance
   └─ Raises MoneyWithdrawn event
```

#### Step 4: Invariant Checks
```
✅ Account is ACTIVE
✅ Balance ≥ $100
✅ Amount > 0
└─ All pass → continue
```

#### Step 5: Raise Domain Event
```
Domain Event:
{
  "type": "MoneyWithdrawn",
  "accountId": "acc_123",
  "amount": 100,
  "timestamp": "2026-01-28T10:00:00Z"
}
```

#### Step 6: Convert to Integration Event
```
Integration Event:
{
  "type": "BalanceChanged",
  "accountId": "acc_123",
  "newBalance": 4900,
  "change": -100,
  "timestamp": "2026-01-28T10:00:00Z"
}
```

#### Step 7: Update Read Model (Asynchronously)
```
UPDATE account_summary 
SET balance = 4900, 
    lastUpdated = NOW()
WHERE accountId = 'acc_123'
```

#### Step 8: UI Shows New Balance
```
UI: "Successfully withdrawn $100"
UI: "New Balance: $4,900"
```

---

## 🎤 Interview One-Liners (Very Useful)

### Q: What is CQRS?

```
"Separating write models that enforce invariants from read
models optimized for queries."
```

### Q: Why CQRS?

```
"Reads and writes have fundamentally different needs in
banking systems—writes need strong consistency and rule
enforcement, while reads need performance and flexibility."
```

### Q: Consistency?

```
"Strong consistency for writes (non-negotiable), eventual
consistency for reads (acceptable for customer views)."
```

### Q: When to use?

```
"Apply CQRS selectively to high-value domains with complex
business rules and high read/write asymmetry."
```

### Q: CQRS vs Event Sourcing?

```
"Independent patterns. CQRS uses separate models; event
sourcing stores all changes. Most banks use CQRS without
full event sourcing."
```

---

## 📝 5-Minute Pre-Interview Checklist

- ✅ Are invariants enforced only on command side?
- ✅ Are aggregates absent from query side?
- ✅ Are read models denormalized?
- ✅ Is eventual consistency acceptable for reads?
- ✅ Can I explain synchronization (events → read model)?
- ✅ Do I know when NOT to use CQRS?

---

## 🎓 Common Interview Questions & Answers

### Q1: What problem does CQRS solve?

**Answer:**
```
"CQRS solves the problem that reads and writes have very
different characteristics in banking systems. Writes need:
├─ Strong consistency
├─ Rule enforcement
├─ Invariant protection
└─ Low volume tolerance

Reads need:
├─ High performance
├─ Flexible structure
├─ Denormalized data
└─ High volume support

Combining them in one model creates complexity."
```

### Q2: Where do business rules live in CQRS?

**Answer:**
```
"All business rules and invariants live ONLY on the command
side inside aggregates. The query side has NO business logic—
it's just denormalized data stored for performance."
```

### Q3: Can queries update data?

**Answer:**
```
"No. Queries are strictly read-only. Allowing them to update
state breaks consistency, auditability, and makes reasoning
about the system impossible."
```

### Q4: Are read models strongly consistent?

**Answer:**
```
"No. Read models are eventually consistent. This means there's
a small window where the read model lags behind the write
model. This is acceptable for customer views and reports but
not for invariant checks or money movement."
```

### Q5: Does CQRS require Event Sourcing?

**Answer:**
```
"No. CQRS and Event Sourcing are independent. Most banking
systems use CQRS without full event sourcing. We use:
├─ Traditional DB for command side
├─ Separate read model DB
├─ Events to keep them in sync
└─ No event store requirement"
```

### Q6: Why are aggregates only on the command side?

**Answer:**
```
"Aggregates exist to enforce invariants and maintain consistency.
The query side doesn't change state, so aggregates add no value
there. Read models are just denormalized data, optimized for
access patterns."
```

### Q7: When did you NOT use CQRS?

**Answer:**
```
"We avoided CQRS for:
├─ Simple reference data (lookup tables)
├─ Low-volume CRUD services
└─ Systems with identical read/write patterns

The complexity overhead wasn't justified."
```

### Q8: How do read models get updated?

**Answer:**
```
"Flow:
1. Command executes on aggregate
2. Aggregate raises domain event
3. Event is converted to integration event
4. Async handler picks up integration event
5. Read model updated

This keeps write model strongly consistent while allowing
read model to be eventually consistent."
```

---

## 🚨 Common Anti-Patterns

| Anti-Pattern | Why It's Wrong | Fix |
|--------------|----------------|-----|
| ❌ Business logic on query side | Breaks consistency, duplicates rules | Keep query side simple |
| ❌ Queries that modify state | Makes reasoning impossible | Strict read-only |
| ❌ Aggregates on both sides | Tight coupling, defeats purpose | Aggregates only on write |
| ❌ Synchronous read model updates | Defeats eventual consistency benefit | Use async events |
| ❌ Complex read model relationships | Defeats denormalization benefit | Flatten the data |
| ❌ Coupling read to write schema | Breaks separation | Separate schemas completely |

---

## ✨ Advanced Patterns

### Multiple Read Models for Different Use Cases

```
Write Model:
└─ SavingsAccount Aggregate

Read Models (all updated from same events):
├─ account_summary (for UI)
├─ account_history (for statements)
├─ account_analytics (for reporting)
└─ account_risk (for risk team)

All updated from BalanceChanged events
but optimized for different access patterns.
```

### Materialized Views

```
Instead of complex queries, pre-compute and store results:

Query (without CQRS):
  SELECT account.id, account.balance, 
         COUNT(transactions.id) as tx_count,
         SUM(transactions.amount) as total_withdrawn
  FROM accounts
  JOIN transactions ON accounts.id = transactions.accountId
  GROUP BY account.id
  └─ Slow, complex join

Read Model (with CQRS):
  SELECT * FROM account_summary
  └─ Fast, denormalized
```

---

## 📝 Summary

### Core Principles

```
✅ Separate write and read models
✅ Enforce rules only on write side
✅ Optimize read model for performance
✅ Use events to keep in sync
✅ Accept eventual consistency for reads
✅ Apply selectively, not everywhere
```

### Key Rules

```
❌ No business logic on read side
❌ Queries never modify state
❌ No aggregates on read side
✅ Denormalized read models
✅ Async synchronization
✅ Strong write consistency
```

### When It Shines

```
CQRS is perfect for:
✅ High read/write asymmetry
✅ Complex business rules
✅ Need for independent scaling
✅ Multiple read views
✅ Regulatory requirements
```

### Final Line (Interview Gold)

```
"CQRS isn't a default pattern—we apply it to domains where the
separation pays for its complexity. We use strong consistency
on writes, eventual consistency on reads, and events to keep
them in sync."
```
