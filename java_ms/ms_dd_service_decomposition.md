# 📐 Day 1 — DDD + Service Decomposition

*The most important day for EM interviews at tier-1 FS GCCs. Every system design question at JPMC/GS starts here — "how did you decompose this?" is the opening move in almost every architecture discussion.*

---

# TOPIC 1: Bounded Contexts

## 1. Core Concept + Internals

A Bounded Context is the **boundary within which a domain model is valid, consistent, and unambiguous**.

The key word is **unambiguous**. Outside that boundary, the same term can mean something completely different — and that's fine, as long as each context owns its own model.

This is not just a technical boundary. It is simultaneously:
- A **model boundary** — one ubiquitous language, one consistent model
- A **team boundary** — one team owns one context (Conway's Law)
- A **deployment boundary** — one context = one or more services deployed independently
- A **data boundary** — one context owns its data, no one else reads it directly

---

## The FS Reality — "Account" Across Four Contexts

This is the example that lands in every interview:

```
┌─────────────────────────────────────────────────────────┐
│                  The word "Account"                      │
├─────────────┬──────────────┬────────────┬───────────────┤
│  Payments   │   Lending    │   Fraud    │  Reporting    │
│  Context    │   Context    │   Context  │  Context      │
├─────────────┼──────────────┼────────────┼───────────────┤
│ Sort code + │ Loan with    │ Risk       │ GL account    │
│ account no  │ outstanding  │ profile +  │ with debit/   │
│ + balance   │ balance +    │ behavioural│ credit        │
│ + overdraft │ repayment    │ patterns + │ postings +    │
│ limit       │ schedule     │ device     │ period        │
│             │              │ fingerprint│ balances      │
├─────────────┼──────────────┼────────────┼───────────────┤
│ Owned by:   │ Owned by:    │ Owned by:  │ Owned by:     │
│ Payments    │ Lending      │ Fraud      │ Finance       │
│ team        │ team         │ team       │ team          │
└─────────────┴──────────────┴────────────┴───────────────┘
```

These are **four different objects** that share a name. The most common architectural mistake: creating one `Account` service and trying to serve all four contexts. You end up with:
- A God service with 200 fields on the Account object
- Every team deploying together
- A change for Fraud breaks Payments
- No team owns it, every team blames each other

---

## Context Map — The Relationships Between BCs

The Context Map is the most EM-relevant DDD artefact. It shows not just what your contexts are, but **how they relate** — and that has direct organisational and regulatory implications.

The eight relationships you must know:

| Pattern | Relationship | FS Example | EM Implication |
|---|---|---|---|
| **Partnership** | Teams plan together, evolve together | Payments + Notifications — tight coordination acceptable | Shared sprint planning, joint releases |
| **Customer-Supplier** | Upstream sets contract, downstream consumes | CBS (Temenos) is upstream, Payments is downstream | Downstream must negotiate, upstream must honour SLA |
| **Conformist** | Downstream accepts upstream model with no negotiation | Your service consuming SWIFT network | You adapt to SWIFT, SWIFT doesn't adapt to you |
| **Anti-Corruption Layer** | Downstream translates upstream model into its own | Payments wrapping Temenos T24 | Adapter pattern — T24 model never leaks into domain |
| **Open Host Service** | Upstream publishes well-defined protocol for all consumers | Internal API Platform / API Gateway | Versioned, documented, stable contract |
| **Published Language** | Shared formal language between contexts | ISO 20022 for payment messaging | Formal schema, both sides validate against it |
| **Shared Kernel** | Two teams share a subset of the model | Shared `Money` value object library | Shared ownership — changes require both teams |
| **Big Ball of Mud** | No clear model — acknowledge it, contain it | Legacy monolith you're strangling | Don't build on top of it, put ACL around it |

---

## Your Lloyds Context Map — What This Looks Like in Practice

```
                    ┌─────────────────────┐
                    │   Temenos T24 CBS   │
                    │  (Big Ball of Mud)  │
                    └──────────┬──────────┘
                               │ Customer-Supplier
                               │ (T24 sets the contract)
                    ┌──────────▼──────────┐
                    │   Payments BC       │◄──── ACL wraps T24
                    │  (your domain)      │      T24 model never
                    │                     │      leaks in
                    └──┬──────────────┬───┘
                       │              │
          Partnership  │              │  Open Host Service
          (joint       │              │  (Payments publishes
          planning)    │              │   stable API)
                    ┌──▼───┐    ┌─────▼──────────┐
                    │Notif.│    │  Fraud BC       │
                    │  BC  │    │  (Conformist —  │
                    └──────┘    │  adapts to      │
                                │  Payments model)│
                                └─────────────────┘
                    ┌─────────────────────┐
                    │   SWIFT Network     │
                    │  (Conformist —      │
                    │   you adapt to      │
                    │   their model)      │
                    └─────────────────────┘
```

---

## 2. FS-Specific Use Cases

**The ACL at Lloyds — your real example:**

> *"Temenos T24 has its own object model — T24TransactionRequest, T24AccountResponse — with field names like `RECORD.DATE`, `COMPANY.CODE`, `CATEGORY`. These are T24-specific concepts that mean nothing in our payment domain. The Anti-Corruption Layer — TemenosPaymentGatewayAdapter — translates between T24's model and our domain model at the boundary. When T24 was upgraded from R19 to R20, the only thing that changed was the adapter. Our PaymentService, our aggregates, our domain events — untouched."*

**The Conformist relationship with SWIFT:**

> *"We don't negotiate with SWIFT. They publish ISO 20022 message schemas — PACS.008 for credit transfers, CAMT.053 for account statements. We conform to their model for cross-border payments. This is a Conformist relationship — we adapt, they don't. The Published Language is ISO 20022 XML schema. Both sides validate against it. This is also a regulatory requirement — SWIFT mandated ISO 20022 migration by November 2025 for all cross-border payments."*

---

## 3. EM-Level Framing

**Conway's Law — the unavoidable truth:**

> *"Conway's Law states that organisations design systems that mirror their communication structures. In practice: if your Payments team and Fraud team sit in the same squad and have daily standups together, you'll end up with tightly coupled services that deploy together. If they're separate teams with a defined API contract between them, you'll end up with independently deployable services. I've used DDD Context Mapping as a tool to make Conway's Law work for us rather than against us — we design the team structure and the architecture together, not separately."*

**The organisational smell that signals a missing context boundary:**

> *"If two teams are constantly in each other's Jira board, if their sprint reviews always reference each other's work, if a deployment by Team A always requires a deployment by Team B — that's a context boundary violation. The technical symptom is a shared database or direct service-to-service coupling without a contract. The fix is to define the boundary, define the contract, and let each team own their side."*

**Regulatory angle — GDPR data isolation:**

> *"Bounded contexts are also a GDPR control. Under GDPR Article 25, data minimisation by design means each context should only hold the personal data it needs. The Fraud context needs behavioural patterns and device data — it does not need the customer's home address. The Payments context needs account numbers — it does not need the customer's credit score. Context boundaries enforce this separation architecturally, not just by policy."*

---

# TOPIC 2: Aggregates + Aggregate Root

## 1. Core Concept + Internals

An **Aggregate** is a cluster of domain objects treated as a single unit for the purpose of data changes. The **Aggregate Root** is the single entry point — the only object you reference from outside, the only object that ensures the consistency of the whole cluster.

**Three rules that define an Aggregate:**

1. **External objects hold only the root's identity** — never a reference to an inner entity
2. **All changes go through the root** — inner entities cannot be modified directly from outside
3. **One transaction = one aggregate** — you never modify two aggregates in the same transaction. If you think you need to, use a domain event instead.

---

## The PaymentOrder Aggregate — Full FS Example

```java
// PaymentOrder is the Aggregate Root
// Everything inside is only accessible through PaymentOrder
public class PaymentOrder {  // Aggregate Root

    private final PaymentOrderId id;           // Identity
    private final Money amount;                 // Value Object
    private final AccountId debitAccountId;     // External ref — ID only
    private final AccountId creditAccountId;    // External ref — ID only
    private PaymentOrderStatus status;
    private final List<PaymentLeg> legs;        // Inner Entity
    private FraudAssessment fraudAssessment;    // Inner Entity
    private final AuditTrail auditTrail;        // Inner Entity
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // ✅ Private constructor — only factory method creates
    private PaymentOrder(PaymentOrderId id, Money amount,
                          AccountId debitAccountId,
                          AccountId creditAccountId) {
        validateInvariants(amount, debitAccountId, creditAccountId);
        this.id = id;
        this.amount = amount;
        this.debitAccountId = debitAccountId;
        this.creditAccountId = creditAccountId;
        this.status = PaymentOrderStatus.INITIATED;
        this.legs = new ArrayList<>();
        this.auditTrail = new AuditTrail();
    }

    // ✅ Factory method — intention revealing
    public static PaymentOrder initiate(Money amount,
                                         AccountId debitAccountId,
                                         AccountId creditAccountId) {
        PaymentOrderId id = PaymentOrderId.generate();
        PaymentOrder order = new PaymentOrder(
            id, amount, debitAccountId, creditAccountId);

        // Domain event — something happened
        order.domainEvents.add(
            new PaymentOrderInitiatedEvent(id, amount));

        return order;
    }

    // ✅ Business behaviour — not setters
    public void submitForProcessing(PaymentScheme scheme) {
        // Aggregate enforces its own invariants
        if (this.status != PaymentOrderStatus.INITIATED) {
            throw new InvalidPaymentStateException(
                "Cannot submit payment in state: " + this.status);
        }

        // Inner entity created through root — not directly
        PaymentLeg leg = PaymentLeg.create(
            this.id, this.amount, scheme);
        this.legs.add(leg);

        this.status = PaymentOrderStatus.SUBMITTED;
        this.auditTrail.record("SUBMITTED", scheme.name());

        domainEvents.add(new PaymentOrderSubmittedEvent(
            this.id, scheme));
    }

    public void recordFraudClearance(FraudScore score,
                                      String assessedBy) {
        if (this.status != PaymentOrderStatus.SUBMITTED) {
            throw new InvalidPaymentStateException(
                "Can only clear fraud on SUBMITTED payments");
        }
        // FraudAssessment is an inner entity
        // Modified through the root, never directly
        this.fraudAssessment = FraudAssessment.cleared(
            score, assessedBy);
        this.status = PaymentOrderStatus.FRAUD_CLEARED;

        domainEvents.add(new PaymentFraudClearedEvent(
            this.id, score));
    }

    public void settle(SchemeReference schemeRef) {
        if (this.status != PaymentOrderStatus.FRAUD_CLEARED) {
            throw new InvalidPaymentStateException(
                "Cannot settle payment not fraud cleared. " +
                "Current status: " + this.status);
        }
        this.status = PaymentOrderStatus.SETTLED;
        this.auditTrail.record("SETTLED", schemeRef.getValue());

        domainEvents.add(new PaymentSettledEvent(
            this.id, this.amount, schemeRef));
    }

    // ✅ Invariant check — aggregate protects consistency
    private void validateInvariants(Money amount,
                                     AccountId debitAccountId,
                                     AccountId creditAccountId) {
        Objects.requireNonNull(amount);
        if (amount.isZeroOrNegative()) {
            throw new InvalidPaymentException(
                "Payment amount must be positive");
        }
        if (debitAccountId.equals(creditAccountId)) {
            throw new InvalidPaymentException(
                "Debit and credit accounts cannot be the same");
        }
    }

    // ✅ Domain events collected, published after commit
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // ✅ References to other aggregates — ID only, never object
    // If you hold the AccountId, you don't load the Account
    // You fetch it separately via its own repository
    public AccountId getDebitAccountId() { return debitAccountId; }
    public AccountId getCreditAccountId() { return creditAccountId; }
}

// ✅ Inner Entity — only accessible via PaymentOrder
// No public constructor — package-private or inner class
class PaymentLeg {
    private final PaymentOrderId parentOrderId;  // Back-ref to root
    private final Money amount;
    private final PaymentScheme scheme;
    private final LocalDateTime createdAt;

    // Package-private — cannot be created from outside the aggregate
    static PaymentLeg create(PaymentOrderId orderId,
                               Money amount,
                               PaymentScheme scheme) {
        return new PaymentLeg(orderId, amount, scheme);
    }

    private PaymentLeg(PaymentOrderId orderId,
                        Money amount,
                        PaymentScheme scheme) {
        this.parentOrderId = orderId;
        this.amount = amount;
        this.scheme = scheme;
        this.createdAt = LocalDateTime.now();
    }
}

// ✅ Repository — one per aggregate root
// Loads and saves the entire aggregate as a unit
public interface PaymentOrderRepository {
    void save(PaymentOrder order);
    Optional<PaymentOrder> findById(PaymentOrderId id);
    List<PaymentOrder> findPendingSettlement(LocalDate valueDate);
}
```

---

## The Cross-Aggregate Invariant Problem

This is the EM-level question that separates architects from developers:

> *"What if you need a business rule that spans two aggregates?"*

**Example:** "The total of all pending payments for an account cannot exceed the account's daily limit."

This touches two aggregates — `PaymentOrder` and `Account`.

**Wrong answer:** Put both in the same transaction, modify both together.

**Right answer — three options:**

```java
// Option 1: Eventual consistency via domain events
// PaymentOrder raises PaymentInitiatedEvent
// Account listens, checks limit, raises LimitBreachEvent if exceeded
// Payment is rolled back via compensation
// → Eventual consistency. Acceptable if limit breach can be caught
//   within seconds.

// Option 2: Pre-check before aggregate creation (not a true invariant)
@Service
public class PaymentInitiationService {
    public PaymentOrder initiatePayment(PaymentRequest request) {
        // Check limit BEFORE creating the aggregate
        // This is a pre-condition check, not a true invariant
        AccountLimits limits = accountService
            .getLimits(request.getDebitAccountId());
        if (!limits.canAccommodate(request.getAmount())) {
            throw new DailyLimitExceededException(
                "Daily limit of " + limits.getDailyLimit() +
                " would be exceeded");
        }
        // Now safe to create
        return PaymentOrder.initiate(
            request.getAmount(),
            request.getDebitAccountId(),
            request.getCreditAccountId());
    }
}

// Option 3: Redesign — is daily limit actually part of Account
// or part of a PaymentLimit aggregate?
// Sometimes the cross-aggregate invariant signals a missing aggregate
```

**The EM framing:**
> *"In a payment system, I accept eventual consistency for limit checks. A payment is initiated, a domain event fires, the Account context validates the limit asynchronously. If it's breached, a compensating transaction reverses the payment. This is acceptable because: (a) the window of inconsistency is milliseconds, (b) fraud detection runs in parallel anyway, (c) FCA doesn't require synchronous limit enforcement — it requires breach to be detected and acted on. The alternative — synchronous cross-aggregate transaction — creates distributed transaction complexity that's far more dangerous in production."*

---

## 3. EM-Level Framing

**Aggregate size — the EM decision:**

> *"The hardest DDD decision is aggregate size. Too large: you have contention — 100 threads trying to modify the same PaymentOrder aggregate. Too small: you need lots of cross-aggregate coordination. My rule: start small, one aggregate = one transaction. Add to the aggregate only when the invariant genuinely cannot be maintained across a domain event. In a payment system, PaymentOrder + PaymentLegs + FraudAssessment belong together — they're always modified together, consistency is immediate. Account balance and payment limits belong in separate aggregates — consistency can be eventual."*

**The transaction boundary = the aggregate boundary:**

> *"This is the principle I use to explain aggregates to my team: wherever you need an ACID transaction, that's your aggregate boundary. If you need two DB rows in the same transaction, they belong to the same aggregate. If they can be eventually consistent, they're separate aggregates with a domain event between them. This makes the aggregate boundary a deployable, testable, lockable unit."*

---

# TOPIC 3: Ubiquitous Language

## 1. Core Concept

> *"A shared language between developers and domain experts that is used in all communication — code, tests, documentation, conversations."*

The critical word is **shared**. It's not the business's language that developers translate. It's not developer jargon that business has to learn. It's a **jointly owned** vocabulary that appears in:
- Domain object names
- Method names
- Event names
- API endpoint names
- Test descriptions
- Jira tickets

---

## The FS Language Trap

Commercial banking is full of terms that sound similar but mean completely different things:

```
Term            What dev thinks it means       What business means
──────────────────────────────────────────────────────────────────
"Settlement"    Writing to DB                  Net transfer of funds
                                               between banks via
                                               CHAPS/RTGS at end of day

"Clearing"      Data validation                Exchanging payment
                                               instructions between
                                               banks before settlement

"Posting"       Sending a message              Creating a debit/credit
                                               entry in the general
                                               ledger

"Netting"       Making code cleaner            Offsetting multiple
                                               payments to calculate
                                               a single net position

"Nostro"        Not in any dev's vocabulary    Our account at another
                                               bank (foreign currency)

"Vostro"        Not in any dev's vocabulary    Another bank's account
                                               at our bank

"Value date"    Created date                   The date funds are
                                               actually available to
                                               the recipient

"Cut-off"       Some timeout                   The deadline after which
                                               payments go next day
                                               (CHAPS cut-off: 17:00)
```

---

## The Production Consequence of Language Drift

```java
// ❌ Developer used their own language
// "Confirm" to developer = mark as done
// "Confirm" to business = send confirmation to customer
// These are TWO DIFFERENT THINGS

public class PaymentService {
    public void confirmPayment(String paymentId) {
        // Developer meant: mark payment as processed in DB
        payment.setStatus("CONFIRMED");
        repository.save(payment);
        // Forgot: business expected customer confirmation
        // to be sent here
    }
}

// Six months later — auditors find payments marked CONFIRMED
// but customers never received confirmation messages
// FCA BCOBS 5.1.6 — firms must notify customers of payment execution
// Reportable customer harm event

// ✅ Ubiquitous language — code matches business vocabulary
public class PaymentService {

    public void settlePayment(String paymentId) {
        // "Settle" = what the business calls it
        // = net transfer completed, funds available
        payment.settle();
        repository.save(payment);
        eventPublisher.publish(new PaymentSettledEvent(payment));
        // PaymentSettledEvent triggers CustomerNotificationHandler
        // in the Notifications BC — two separate things, clearly named
    }

    public void postToLedger(Payment payment) {
        // "Post" = create GL entry
        // Separate from settlement — different business concept
        ledgerService.createEntry(
            LedgerEntry.debit(payment.getDebitAccountId(),
                              payment.getAmount()),
            LedgerEntry.credit(payment.getCreditAccountId(),
                               payment.getAmount())
        );
    }
}
```

---

## 2. EM-Level Framing

**How I enforce ubiquitous language in my team:**

> *"I run a monthly Domain Language Review with the business analyst and a senior developer from each team. We maintain a living glossary in Confluence — every domain term, its precise definition in each bounded context, and its code representation. When a developer uses 'confirm' where the business says 'settle', it's a red flag in code review. The class names, method names, and event names in our domain model must match the glossary. It sounds ceremonial but it's caught three production incidents where developers implemented the wrong thing because they misunderstood a business term."*

**Lloyds-specific language:**

> *"In Commercial Banking at Lloyds, the critical language alignment was around 'payment instruction' vs 'payment order' vs 'payment transaction'. A payment instruction is what the customer submits — their intent. A payment order is what we create in our system after validation. A payment transaction is what gets posted to the CBS ledger. Three different objects, three different lifecycles, three different regulatory obligations. Using them interchangeably in code caused two teams to build the wrong thing in the same sprint."*

---

# TOPIC 4: Service Decomposition

## 1. Core Concept

Two primary strategies — and knowing when to use each is EM-level:

**Strategy 1: Decompose by Business Capability**
- A business capability is something the business does — a stable function of the organisation
- Independent of how the org is currently structured
- Examples: Payment Processing, Account Management, Fraud Detection, Customer Onboarding

**Strategy 2: Decompose by Subdomain (DDD)**
- Core subdomain: your competitive advantage — invest here
- Supporting subdomain: necessary but not differentiating — build simply
- Generic subdomain: commodity — buy or use open source

---

## The Lloyds Commercial Banking Decomposition

```
BUSINESS CAPABILITIES → SERVICE BOUNDARIES

Payment Processing BC
├── Payment Initiation Service     (Core — competitive advantage)
├── Payment Routing Service        (Core — routing logic is IP)
├── Payment Status Service         (Supporting — necessary but simple)
└── Payment Archive Service        (Generic — regulatory retention)

Account Management BC
├── Account Query Service          (Core — real-time balances)
├── Account Servicing Service      (Supporting — instructions, mandates)
└── Statement Service              (Generic — PDF generation)

Fraud & Financial Crime BC
├── Transaction Monitoring Service (Core — ML models = competitive)
├── Sanctions Screening Service    (Supporting — rule-based checks)
└── Case Management Service        (Generic — workflow tool)

Commercial Savings BC              (YOUR DOMAIN — Lloyds engagement)
├── Savings Rate Service           (Core — commercial savings products)
├── Savings Initiation Service     (Core — onboarding to savings)
├── Interest Calculation Service   (Supporting)
└── Maturity Management Service    (Supporting)
```

---

## Service Granularity — The EM Decision

This is a classic interview question. Too fine = nanoservices. Too coarse = distributed monolith.

```
Too fine (nanoservices)          Too coarse (distributed monolith)
────────────────────────         ──────────────────────────────────
PaymentValidationService         PaymentService
PaymentAmountService             (handles initiation, routing,
PaymentAccountService            fraud, settlement, archival,
PaymentCurrencyService           notifications — all in one)

Problems:                        Problems:
- 4 network hops for             - Teams can't deploy independently
  one validation                 - One team blocks all others
- Latency compounds              - DB becomes a bottleneck
- Operational overhead           - Hard to scale specific functions
- Distributed tracing            - Monolith patterns creep in
  nightmare
```

**The right level — heuristics:**

```java
// Signal 1: Can this be deployed independently?
// If deploying ServiceA always requires deploying ServiceB
// → They should be one service

// Signal 2: Does this have a single team owner?
// If two teams both "own" a service → boundary is wrong

// Signal 3: Does this have a single database?
// If two services share a database → they're actually one service

// Signal 4: Does this scale independently?
// Payment initiation (spiky — morning rush) vs
// Payment archival (steady — low volume) → good candidates to split

// Signal 5: Does it have a single reason to change?
// (SRP at service level)
// Fraud scoring model changes independently of payment routing
// → Good boundary
```

---

## 2. FS-Specific Decomposition Example — Commercial Savings

Your context — Commercial Savings at Lloyds:

```
Commercial Savings Domain
│
├── Core Subdomains (build, invest, differentiate)
│   ├── Savings Product Configuration Service
│   │   └── Rate tiers, product rules, eligibility criteria
│   │       Changes when products change — owned by Product team
│   │
│   ├── Savings Initiation Service
│   │   └── Onboarding a commercial customer to a savings product
│   │       Orchestrates: eligibility check → KYC → CBS account creation
│   │       → rate assignment → confirmation
│   │
│   └── Commercial Savings Optimisation Service
│       └── YOUR INITIATIVE — matching customers to best rate tier
│           based on balance behaviour → drives commercial savings
│
├── Supporting Subdomains (build simply)
│   ├── Interest Calculation Service
│   │   └── ACT/365 day-count, tiered rates, compound vs simple
│   │
│   └── Savings Reporting Service
│       └── Regulatory reporting — FCA CASS, PRA liquidity reporting
│
└── Generic Subdomains (buy or open source)
    ├── Document Generation (PDF statements)
    └── Notification Delivery (email/SMS)
```

---

## 3. EM-Level Framing

**The decomposition conversation with stakeholders:**

> *"When I joined the Lloyds engagement, the Commercial Savings domain was a set of features inside a monolithic banking platform. The first thing I did was run a 3-day Event Storming workshop with the business stakeholders, domain experts, and technical leads. We mapped out every domain event — 'SavingsAccountOpened', 'RateChanged', 'MaturityReached', 'InterestPosted' — and the aggregates that owned them. The service boundaries emerged from where domain events crossed team ownership lines. That's the correct way to decompose — not by technical layers, not by what's convenient, but by where the business responsibilities genuinely separate."*

**The commercial savings initiative connection:**

> *"The commercial savings initiative I'm driving is essentially a Core subdomain problem — we're building differentiated capability to optimise which savings tier each commercial customer is in, based on balance behaviour and rate sensitivity. That's our competitive IP. I've kept that in a dedicated Savings Optimisation Service that the product team can evolve independently. The Interest Calculation Service is Supporting — it's necessary but a commodity calculation. If I bundled them, changes to interest calculation logic would require testing the optimisation model and vice versa. Keeping them separate means faster iteration on the IP."*

**Conway's Law applied:**

> *"My 30-person team is structured around these service boundaries — not the other way around. I have a 6-person squad owning the Savings Initiation Service, a 5-person squad owning Savings Optimisation, and a 4-person squad owning Savings Reporting. Each squad has full stack ownership — they design, build, deploy, and operate their service. No cross-squad dependency in the critical path. That's the organisational manifestation of bounded contexts."*

---

# ✅ Day 1 — All Topics Covered

| Topic | Key EM Insight | Your FS Anchor |
|---|---|---|
| **Bounded Contexts** | Context = model + team + deployment + data boundary | T24 ACL, SWIFT Conformist, Payments-Fraud boundary |
| **Context Map** | 8 relationship patterns with organisational consequences | ACL around T24, Partnership with Notifications |
| **Aggregates** | Transaction boundary = aggregate boundary. Cross-aggregate = eventual consistency | PaymentOrder aggregate root, cross-aggregate limit check |
| **Ubiquitous Language** | Code must match business vocabulary — Settlement ≠ Confirm | Settlement vs Clearing vs Posting in Commercial Banking |
| **Service Decomposition** | Core vs Supporting vs Generic subdomain guides investment | Commercial Savings Optimisation = Core, Doc generation = Generic |

---

# what about things like entities , invariants , value objets ....mutable vs immutable etc and other concepts important for DDD ! Also explain me decomposition better ...for ex: Take Commercial Savings on channels side it has Onbaording, Servicing (maturity instruction, add funds, withdraw funds, close account), view and then core domains to maange accounts liefecyelc, operatons processes colleague driven, reporting etc..

# DDD — Tactical Building Blocks + Commercial Savings Decomposition
> EM interview prep — Lloyds Banking Group / Tier-1 FS GCC context

---

## 1. The DDD Taxonomy

```
Domain Model
│
├── Aggregates (consistency boundary)
│   ├── Aggregate Root   — entity, gatekeeper, enforces all invariants
│   └── Inner Entities   — identity, mutable, only reachable via root
│
├── Value Objects        — immutable, equality by value, no identity
├── Domain Events        — past tense, something happened, immutable facts
├── Domain Services      — behaviour spanning multiple aggregates
└── Repositories         — one per aggregate root, persistence abstraction
```

---

## 2. Entities

**Definition:** Object defined by identity, not attributes. Mutable over time. Identity never changes.

**Test:** "Two objects with same attributes — are they the same thing?" → If NO = Entity

**FS Examples:**
| Entity | Why |
|---|---|
| `SavingsAccount` | Same account even if balance changes |
| `Customer` | Same person even if address changes |
| `PaymentOrder` | Tracked through INITIATED → SETTLED lifecycle |
| `SavingsApplication` | Tracked through SUBMITTED → APPROVED → REJECTED |

```java
public class SavingsAccount {
    private final SavingsAccountId id;     // Identity — NEVER changes
    private final CustomerId customerId;    // Owner — NEVER changes
    private Money currentBalance;           // Mutable — changes on deposits
    private InterestRate currentRate;       // Mutable — changes on review
    private SavingsAccountStatus status;    // Mutable — lifecycle changes

    // Identity-based equality — NOT attribute-based
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SavingsAccount)) return false;
        return this.id.equals(((SavingsAccount) o).id);
    }
}
```

---

## 3. Value Objects

**Definition:** Object defined entirely by its attributes. No identity. Immutable. Equality by value.

**Test:** "If I replace this with another object with identical attributes, does anything break?" → If NO = Value Object

**Three rules:**
1. All fields `final` — no setters
2. Equality by value — override `equals()` and `hashCode()`
3. Replaced, never modified — to "change" it, create a new one

**FS Examples:**
| Value Object | Why |
|---|---|
| `Money` | £100 is £100 — no identity needed |
| `InterestRate` | 4.25% ACT/365 is 4.25% ACT/365 |
| `AccountNumber` | The number IS the thing |
| `MaturityInstruction` | Replaced when customer changes instruction |
| `DateRange` | A range is just its start + end |
| `Address` | Replaced entirely when changed |

```java
public final class Money {
    private final BigDecimal amount;   // final
    private final Currency currency;   // final

    private Money(BigDecimal amount, Currency currency) {
        // Invariant enforced at construction
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Money cannot be negative");
        this.amount = amount.setScale(
            currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money ofGBP(String amount) {
        return new Money(new BigDecimal(amount), Currency.getInstance("GBP"));
    }

    // Returns NEW Money — never mutates self
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0)
            throw new InsufficientFundsException(...);
        return new Money(result, this.currency);
    }

    // Equality by VALUE
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Money)) return false;
        Money other = (Money) o;
        return this.amount.compareTo(other.amount) == 0
            && this.currency.equals(other.currency);
    }
}

public final class InterestRate {
    private final BigDecimal rate;              // e.g. 0.0425 = 4.25%
    private final RateType type;                // FIXED, VARIABLE, TRACKER
    private final DayCountConvention dayCount;  // ACT/365, ACT/360

    // ACT/365 day-count — standard UK savings
    public Money calculateInterest(Money principal, int days) {
        BigDecimal dailyRate = this.rate.divide(
            new BigDecimal("365"), 10, RoundingMode.HALF_UP);
        return principal.multiply(dailyRate.multiply(new BigDecimal(days)));
    }
}
```

---

## 4. Entity vs Value Object — Decision Table

| Question | Entity | Value Object |
|---|---|---|
| Unique identity? | Yes | No |
| Same attributes = same object? | No | Yes |
| State changes over time? | Yes | No |
| Track through lifecycle? | Yes | No |
| Replaced rather than modified? | No | Yes |
| Thread-safe by default? | No | Yes (immutable) |

---

## 5. Invariants

**Definition:** Business rule that must ALWAYS be true for an aggregate to be valid. Aggregate root enforces ALL invariants. No exceptions.

**Why they matter:** Invariants are compliance by construction, not by convention. A service layer can forget to check a rule. The aggregate cannot.

```java
public class SavingsAccount {

    // INVARIANT 1: Balance never below product minimum
    public void withdraw(Money amount, String requestedBy) {
        assertAccountIsActive();
        Money resultingBalance = this.currentBalance.subtract(amount);
        if (resultingBalance.isLessThan(this.minimumBalance)) {
            throw new MinimumBalanceBreachException(
                String.format("Withdrawal of %s breaches minimum %s. Current: %s",
                    amount, minimumBalance, currentBalance));
        }
        this.currentBalance = resultingBalance;
        recordEvent(new FundsWithdrawnEvent(id, amount, currentBalance));
    }

    // INVARIANT 2: Balance never exceeds product maximum (FCA FSCS cap)
    public void deposit(Money amount, String requestedBy) {
        assertAccountIsActive();
        Money resultingBalance = this.currentBalance.add(amount);
        if (resultingBalance.isGreaterThan(this.maximumBalance)) {
            throw new MaximumBalanceExceededException(...);
        }
        this.currentBalance = resultingBalance;
        recordEvent(new FundsDepositedEvent(id, amount, currentBalance));
    }

    // INVARIANT 3: Maturity instruction deadline (2 business days before maturity)
    public void setMaturityInstruction(MaturityInstruction instruction) {
        assertAccountIsActive();
        if (LocalDate.now().isAfter(maturityDate.minusDays(2))) {
            throw new MaturityInstructionDeadlinePassedException(...);
        }
        this.maturityInstruction = instruction;
        recordEvent(new MaturityInstructionSetEvent(id, instruction));
    }

    // INVARIANT 4: Fixed Term Deposit — no withdrawals before maturity
    public void withdraw(Money amount, String requestedBy) {
        if (this.productType == ProductType.FIXED_TERM_DEPOSIT) {
            throw new EarlyWithdrawalNotPermittedException(
                "Fixed Term Deposit does not permit early withdrawal. " +
                "Maturity date: " + maturityDate);
        }
        // ... rest of withdrawal logic for liquid accounts
    }

    // INVARIANT 5: Closed account cannot transact
    private void assertAccountIsActive() {
        if (this.status != SavingsAccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                "Account " + id + " is in status " + status);
        }
    }

    // INVARIANT 6: Cannot close with non-zero balance
    public void initiateClosureProcess(String requestedBy) {
        assertAccountIsActive();
        if (!this.currentBalance.isZero()) {
            throw new AccountClosureException(
                "Cannot close account with balance: " + currentBalance);
        }
        this.status = SavingsAccountStatus.CLOSURE_PENDING;
        recordEvent(new AccountClosureInitiatedEvent(id, requestedBy));
    }
}
```

---

## 6. Domain Events

**Definition:** Something that happened in the domain. Past tense. Immutable. Cannot be undone.

**Named in past tense always:** `SavingsAccountOpened`, `FundsDeposited`, `InterestPosted`, `MaturityReached`

**Domain Event vs Integration Event:**
| | Domain Event | Integration Event |
|---|---|---|
| Scope | Within a bounded context | Crosses BC boundary |
| Transport | In-process (Spring ApplicationEvents) | Kafka topic |
| Transaction | Same transaction | Separate transactions |
| Language | Specific domain terms | Shared integration schema |

```java
// Collect inside aggregate, publish after commit
public class SavingsAccount {
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public void deposit(Money amount, String requestedBy) {
        // ... business logic ...
        // Collect — don't publish yet
        domainEvents.add(new FundsDepositedEvent(
            id, amount, currentBalance, requestedBy, channel));
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}

// Publish AFTER transaction commits — never before
// Use @TransactionalEventListener(phase = AFTER_COMMIT)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleFundsDeposited(FundsDepositedEvent event) {
    kafkaTemplate.send("savings.funds.deposited", event);
}
```

**Key events in Commercial Savings:**
- `SavingsAccountOpenedEvent` → triggers View BC read model, Reporting BC
- `FundsDepositedEvent` → triggers View update, Reporting, Operations
- `InterestPostedEvent` → triggers Statement BC, Reporting BC, FCA reporting
- `MaturityInstructionSetEvent` → Operations BC queues maturity processing
- `MaturityReachedEvent` → Operations BC executes renew/transfer/close
- `AccountClosedEvent` → Archive, final statement, regulatory notification

---

## 7. Domain Services

**Definition:** Behaviour that belongs to the domain but doesn't fit on a single entity or value object. Typically spans multiple aggregates.

**Test:** "Does this operation involve multiple aggregates or require knowledge from outside any single aggregate?" → If YES = Domain Service

```java
// Interest Calculation — spans SavingsAccount + ProductRules + HolidayCalendar
@DomainService
public class InterestCalculationDomainService {
    public InterestAccrual calculateInterest(
            SavingsAccount account, DateRange period) {
        ProductRule rule = productRules.findFor(account.getProductCode());
        // ACT/365 calculation across balance periods and rate changes
        List<BalancePeriod> periods = account.getBalanceHistory()
            .splitByRateChanges(period);
        Money total = Money.zero(GBP);
        for (BalancePeriod bp : periods) {
            int days = holidayCalendar.countBusinessDays(
                bp.getStart(), bp.getEnd());
            total = total.add(bp.getRate().calculateInterest(
                bp.getBalance(), days));
        }
        return InterestAccrual.of(account.getId(), period, total);
    }
}

// Rate Assignment — commercial savings initiative
// Spans SavingsAccount + CustomerProfile + ProductRules
@DomainService
public class RateAssignmentDomainService {
    public InterestRate assignRate(SavingsAccount account,
                                    CustomerProfile customer) {
        Money balance = account.getCurrentBalance();
        if (customer.isCommercialPremium() &&
            balance.isGreaterThan(Money.ofGBP("500000")))
            return InterestRate.fixed(new BigDecimal("0.0475")); // 4.75%
        else if (balance.isGreaterThan(Money.ofGBP("100000")))
            return InterestRate.fixed(new BigDecimal("0.0425")); // 4.25%
        else if (balance.isGreaterThan(Money.ofGBP("10000")))
            return InterestRate.fixed(new BigDecimal("0.0350")); // 3.50%
        else
            return InterestRate.fixed(new BigDecimal("0.0250")); // 2.50%
    }
}
```

---

## 8. Mutable vs Immutable — Decision Framework

| | Immutable | Mutable |
|---|---|---|
| What | Value Objects, Domain Events, DTOs | Entities, Aggregate internals |
| Identity | Equality by value | Equality by ID |
| Thread safety | Free — share anywhere | Must use aggregate boundary |
| Change | Replace entirely | Modify in place via behaviour method |
| In code | All fields `final`, no setters | Final ID, mutable state |
| FS examples | Money, InterestRate, AccountNumber, MaturityInstruction | SavingsAccount, Customer, PaymentOrder |

---

## 9. Commercial Savings — Bounded Context Map

### Two hemispheres

```
CHANNEL SIDE (customer/colleague facing)     CORE DOMAIN SIDE (business logic)
─────────────────────────────────────        ──────────────────────────────────
Onboarding BC                                Account Lifecycle BC  ← CORE
Servicing BC                                 Operations Processing BC
View BC                                      Reporting & Compliance BC
Colleague Tools BC
```

### Channel Side BCs

**Onboarding BC**
- Eligibility Check Service `[core]`
- KYC Orchestration Service `[supporting]`
- Product Selection Service `[core]`
- Account Opening Service `[core]`
- Welcome / Onboarding Comms `[generic]`

**Servicing BC**
- Maturity Instruction Service `[core]`
- Add Funds Service `[supporting]`
- Withdrawal Service `[supporting]`
- Account Closure Service `[supporting]`
- Rate Change Notification `[generic]`

**View BC**
- Account Summary Service `[supporting]`
- Balance + Interest View `[supporting]`
- Transaction History Service `[supporting]`
- Document Download Service `[generic]`

**Colleague Tools BC**
- RM Dashboard Service `[supporting]`
- Override + Exception Service `[core]`
- Colleague-Initiated Operations `[supporting]`
- Audit Trail Viewer `[supporting]`

### Core Domain Side BCs

**Account Lifecycle BC** ← invest here, competitive advantage
- Account State Machine `[core]`
- Interest Accrual Engine `[core]`
- Rate Assignment Service `[core]` ← commercial savings initiative
- Maturity Processing `[core]`
- Balance Management `[core]`

**Operations BC**
- Interest Posting Batch `[supporting]`
- Maturity Sweep Processing `[supporting]`
- Fund Transfer Orchestration `[supporting]`
- CBS Reconciliation `[generic]`

**Reporting BC**
- FCA Regulatory Reporting `[supporting]`
- FSCS Reporting `[supporting]`
- Management MI `[supporting]`
- Customer Statements `[generic]`
- Audit Log Service `[supporting]`

### Context Map Relationships

| From | Relationship | To | Why |
|---|---|---|---|
| Onboarding BC | Partnership | Account Lifecycle BC | Application approval opens account — joint planning |
| Servicing BC | Customer-Supplier | Account Lifecycle BC | Lifecycle sets the contract, Servicing conforms |
| View BC | Conformist | Account Lifecycle BC | Reads read models — no negotiation |
| Account Lifecycle BC | ACL | Temenos T24 / TM Vault | CBS model never leaks into domain |
| Operations BC | Conformist | Account Lifecycle BC | Reacts to events — adapts to upstream |
| Reporting BC | Conformist | All other BCs | Pure downstream consumer of events |
| Account Lifecycle BC | Open Host Service | All Channel BCs | Stable versioned API, all channels consume |

---

## 10. Aggregates Per Bounded Context

### Account Lifecycle BC — the most important

| Building Block | Name | Role |
|---|---|---|
| Aggregate Root | `SavingsAccount` | Owns all invariants, all state transitions |
| Inner Entity | `BalanceLedger` | History of all balance movements |
| Inner Entity | `InterestAccrual` | Running accrual since last posting |
| Value Object | `Money` | Amount + currency, immutable |
| Value Object | `InterestRate` | Rate + type + day-count, immutable |
| Value Object | `MaturityInstruction` | Renew/transfer/close, immutable once set |
| Domain Service | `InterestCalculationDomainService` | Spans account + product rules + calendar |
| Domain Service | `RateAssignmentDomainService` | Spans account + customer profile |
| Domain Event | `SavingsAccountOpenedEvent` | → View BC, Reporting BC, Onboarding BC |
| Domain Event | `FundsDepositedEvent` | → View BC, Operations BC |
| Domain Event | `InterestPostedEvent` | → Reporting BC, Statement generation |
| Domain Event | `MaturityReachedEvent` | → Operations BC executes instruction |
| Domain Event | `AccountClosedEvent` | → Archive, Reporting, Notification |

### Onboarding BC

| Building Block | Name | Role |
|---|---|---|
| Aggregate Root | `SavingsApplication` | Drives eligibility → KYC → open |
| Inner Entity | `KYCCheck` | Tracks verification status |
| Value Object | `EligibilityCriteria` | Product rules, immutable |
| Value Object | `ProductSelection` | Chosen product + rate, immutable |
| Domain Event | `SavingsApplicationApprovedEvent` | → triggers Account Lifecycle BC to open account |

### Servicing BC

| Building Block | Name | Role |
|---|---|---|
| Aggregate Root | `ServicingRequest` | Owns lifecycle of one customer action |
| Inner Entity | `ServicingInstruction` | Add funds / withdraw / close / maturity |
| Value Object | `MaturityInstruction` | Renew / transfer / close |
| Domain Event | `WithdrawalRequestedEvent` | → Account Lifecycle BC validates + executes |
| Domain Event | `MaturityInstructionSetEvent` | → Operations BC queues maturity processing |

---

## 11. Event Flow Across Contexts

```
Customer submits application
        ↓
Onboarding BC → SavingsApplicationApprovedEvent (Kafka)
        ↓
Account Lifecycle BC → opens SavingsAccount → SavingsAccountOpenedEvent
        ↓ (fan-out via Kafka)
├── View BC → builds read model
├── Reporting BC → records for FCA reporting
└── Operations BC → triggers CBS account creation in TM Vault

Customer adds funds (Servicing BC)
        ↓ FundsDepositedEvent (Kafka)
├── View BC → updates balance display
├── Reporting BC → records movement
└── Account Lifecycle BC → updates balance, recalculates accrual

Nightly batch (Operations BC)
        ↓ InterestPostedEvent (Kafka)
├── View BC → updates interest display
├── Reporting BC → FCA regulatory position
└── Statement BC → adds line item

Maturity date reached (Operations BC detects)
        ↓ MaturityReachedEvent (Kafka)
Account Lifecycle BC reads MaturityInstruction
        ↓ executes: renew / transfer / close
        ↓ AccountClosedEvent or SavingsAccountRenewedEvent (Kafka)
All downstream BCs react accordingly
```

---

## 12. EM Interview Anchors

**On decomposition decision:**
> "I separate by rate of change and team ownership. Channel side changes when customer journeys change — UX research, FCA disclosure rules. Core domain changes when product rules change — Treasury, Finance. Different reasons to change = different bounded contexts."

**On Account Lifecycle as core subdomain:**
> "This is where competitive advantage lives. SavingsAccount aggregate owns all invariants — minimum balance, FCA FSCS cap, maturity instruction deadline. Compliance by construction — the aggregate enforces rules regardless of who calls it. Can't be bypassed. Under FCA BCOBS, incorrect balance enforcement is a reportable failure."

**On cross-aggregate invariants:**
> "When a business rule spans two aggregates — like 'daily withdrawal limit across all accounts' — I accept eventual consistency. Withdrawal initiated → domain event → Account context validates limit → compensating transaction if breached. The window is milliseconds. FCA doesn't require synchronous enforcement — it requires breach detection and remediation. Synchronous cross-aggregate transaction creates distributed transaction complexity far more dangerous in production."

**On TM Vault / T24 integration:**
> "The ACL at the boundary means the CBS model never leaks into our domain. TM Vault has its own contract model — we translate at the adapter. If Vault is upgraded or replaced, only the adapter changes. Domain model untouched. Also a GDPR Article 25 control — we strip Vault response fields we don't need before they enter our domain."

---

*Legend: `[core]` = competitive advantage — invest here | `[supporting]` = build simply | `[generic]` = buy or use open source*








---

# Enterprise UK Bank — Savings DDD Bounded Context Evaluation
> Products: Liquid Savings Account (LSA) + Fixed Term Deposit (FTD)
> CBS: TM Vault | Channels: Self-service + Colleague | Regulatory: FCA BCOBS, FSCS, PRA

---

## Product Rules Drive Aggregate Design — Start Here

| Rule | LSA | FTD |
|---|---|---|
| Additional deposits | Anytime, up to FSCS cap | NOT permitted after opening |
| Withdrawals | Anytime (subject to notice period) | NOT permitted before maturity — ALWAYS throws |
| Rate | Variable — changes with BoE base rate | Fixed for full term — NEVER changes |
| Maturity | None — evergreen | Hard date — instruction required 2 days before |
| TM Vault | Posting orders for debit/credit | Smart contract enforces term, maturity scheduler |
| Key FCA rule | BCOBS — prompt payment of withdrawals | BCOBS + COBS for structured deposits |

> **Critical insight:** LSA and FTD are NOT the same aggregate with a product-type flag.
> They have incompatible invariants — model as separate aggregate classes in the same BC.

---

## Bounded Context Map

### Channel Side — Customer and Colleague Facing

**Onboarding BC** `[Partnership → Account Lifecycle BC]`
- Product eligibility service `[core]`
- KYC orchestration service `[supporting]` — wraps Jumio, Experian, sanctions via ACL
- Product selection service `[core]` — LSA vs FTD, term selection, rate display
- Account opening service `[core]` — initiates account in Lifecycle BC + Vault
- Welcome comms `[generic]`

**Servicing BC** `[Customer-Supplier → Account Lifecycle BC]`
- Maturity instruction service `[core]` — FTD only, renew/transfer/close
- Add funds service `[supporting]` — LSA only, routes to Lifecycle BC
- Withdrawal service `[supporting]` — LSA only, notice period submission
- Notice period management `[core]` — tracks active notices, expiry dates
- Account closure service `[supporting]` — both products, different flows
- Rate change notification `[generic]` — LSA rate change comms

> **Key point:** Servicing BC knows product type for UX routing (don't show 'Add Funds' for FTD).
> But it does NOT enforce invariants — Account Lifecycle BC does.
> "Servicing is the first line of defence for UX. Account Lifecycle is the last line of defence for correctness."

**View BC** `[Conformist → Account Lifecycle BC]`
- Account summary service `[supporting]` — balance, rate, status
- Balance + interest view `[supporting]` — accrued interest display
- Transaction history `[supporting]` — all movements, paginated
- Maturity countdown view `[supporting]` — FTD only — days to maturity
- Document download `[generic]` — statements, certificates of deposit

> **GDPR Note:** View BC exposes only what each channel context needs.
> Colleague view ≠ customer view — different fields, different masking.

**Colleague BC** `[Customer-Supplier → Account Lifecycle BC]`
- RM portfolio view `[supporting]` — all customer savings positions
- Override + exception service `[core]` — modelled explicitly, not a bypass
- Colleague-initiated operations `[supporting]` — same Lifecycle invariants apply
- Complaint handling `[supporting]`
- Audit trail viewer `[supporting]`

---

### Core Domain Side — Business Logic and Operations

**Account Lifecycle BC** ★ Core Subdomain — invest here
`[Open Host Service → all Channel BCs]`
`[ACL + Conformist → TM Vault]`

- LSA state machine `[core]`
- FTD state machine `[core]`
- Interest accrual engine `[core]` — ACT/365, variable vs fixed rate
- Rate assignment service `[core]` — commercial savings rate tiers
- Notice period enforcement `[core]` — 0/30/60/90 day notice tracking
- Maturity processing `[core]` — FTD maturity execution
- Product policy engine `[core]` — invariant rules per product type

**Operations BC** `[Conformist → Account Lifecycle BC]`

- Interest posting batch `[supporting]` — nightly accrual → posting
- Maturity sweep processing `[supporting]` — FTD maturity execution in CBS
- Notice period expiry batch `[supporting]` — LSA withdrawal release
- TM Vault reconciliation `[generic]` — domain vs CBS balance check
- Exception queue management `[supporting]` — failed postings, manual resolution
- BACS / CHAPS instruction `[supporting]` — payment rail instructions for transfers

**Reporting BC** `[Conformist → all BCs via Kafka]`

- FCA BCOBS reporting `[supporting]` — withdrawal prompt payment metrics
- FSCS eligibility reporting `[supporting]` — eligible deposits by customer
- PRA liquidity reporting `[supporting]` — stable funding ratio inputs
- Management MI `[supporting]` — product performance, rate tier distribution
- Customer statements `[generic]` — monthly/annual, PDF
- Audit log service `[supporting]` — immutable audit trail, 7-year retention

---

### External Systems

**TM Vault** — upstream Open Host Service + ACL at boundary
- Posting Orders API — debit/credit entries
- Account Lifecycle API — open, close, update
- Scheduler — maturity date triggers for FTD
- Smart Contracts — FTD term enforcement at CBS level (belt-and-braces with domain)
- Vault webhooks → your ACL → Operations BC

> **Vault is not T24:** Vault is API-first and event-driven.
> ACL translates model. Vault webhooks also feed Operations BC for reconciliation.
> FTD invariant enforced at BOTH domain level AND Vault smart contract level.

**KYC / AML Providers** — all behind ACL in Onboarding BC
- Jumio / Onfido — identity verification
- Experian — credit / KYB for commercial
- OFAC / HMT — sanctions screening
- PEP screening provider

**Payment Rails** — wrapped in Operations BC
- BACS — standard add funds / withdrawal transfers
- Faster Payments — instant transfers (LSA withdrawals)
- CHAPS — high-value FTD maturity sweeps
- Internal book transfer — intrabank movements

---

## Aggregates — Account Lifecycle BC

### LiquidSavingsAccount Aggregate

| Type | Name | Purpose |
|---|---|---|
| Aggregate Root | `LiquidSavingsAccount` | Owns all LSA invariants, all state transitions |
| Inner Entity | `BalanceLedger` | All balance movements — auditable, immutable entries |
| Inner Entity | `NoticePeriod` | Active notice: amount, given-at, expiry-at |
| Inner Entity | `InterestAccrual` | Running accrual since last interest posting date |
| Value Object | `Money` | Amount + GBP, immutable, currency-aware arithmetic |
| Value Object | `VariableInterestRate` | Rate + effective date — replaced on rate change |
| Value Object | `NoticePeriodTerms` | 0 / 30 / 60 / 90 days — immutable product config |
| Domain Event | `FundsDepositedEvent` | → View BC, Reporting BC, Vault ACL posting |
| Domain Event | `NoticeGivenEvent` | → Operations BC schedules release |
| Domain Event | `FundsWithdrawnEvent` | → View BC, Reporting BC, Vault ACL posting |
| Domain Event | `InterestPostedEvent` | → Statement BC, FCA BCOBS reporting |
| Domain Event | `RateChangedEvent` | → View BC, Notification BC |

**Key invariants:**
1. Balance never falls below minimum balance (product rule)
2. Balance never exceeds maximum (FSCS cap — £85k retail, higher commercial)
3. Notice period must be served before funds released
4. Only ACTIVE accounts can transact
5. Cannot close with non-zero balance — funds must be withdrawn first

---

### FixedTermDeposit Aggregate

| Type | Name | Purpose |
|---|---|---|
| Aggregate Root | `FixedTermDeposit` | Owns all FTD invariants — completely different from LSA |
| Inner Entity | `TermContract` | Rate, term length, maturity date — locked at opening |
| Inner Entity | `InterestAccrual` | Fixed rate accrual — no rate changes ever |
| Value Object | `Money` | Opening balance — set once at open |
| Value Object | `FixedInterestRate` | Rate + ACT/365 day-count — immutable |
| Value Object | `MaturityInstruction` | Renew / Transfer / Close — replaceable until deadline |
| Value Object | `TermLength` | 30d / 90d / 180d / 1yr / 2yr — immutable |
| Domain Event | `FTDOpenedEvent` | → View BC, Reporting BC, Vault contract creation |
| Domain Event | `MaturityInstructionSetEvent` | → Operations BC queues maturity processing |
| Domain Event | `MaturityReachedEvent` | → Operations BC executes instruction |
| Domain Event | `FTDClosedEvent` | → Archive, Reporting, Notification |

**Key invariants:**
1. `withdraw()` ALWAYS throws before maturity — no exceptions, no overrides
2. `deposit()` ALWAYS throws after opening — no top-ups
3. Maturity instruction must be set ≥2 business days before maturity date
4. Term and rate NEVER change after opening
5. Cannot be cancelled — only closed at maturity (or penalty closure if product allows)

---

## Context Map Relationships

| From | Pattern | To | EM Implication |
|---|---|---|---|
| Onboarding BC | Partnership | Account Lifecycle BC | Joint sprint planning, coordinated releases |
| Servicing BC | Customer-Supplier | Account Lifecycle BC | Lifecycle sets contract, Servicing submits intent |
| View BC | Conformist | Account Lifecycle BC | Consumes read models, no negotiation |
| Colleague BC | Customer-Supplier | Account Lifecycle BC | Same invariants apply — override is modelled, not bypassed |
| Account Lifecycle BC | ACL + Conformist | TM Vault | Translates Vault model, subscribes to Vault webhooks |
| Onboarding BC | ACL | KYC / AML Providers | Provider swap = adapter change only |
| Operations BC | Conformist | Account Lifecycle BC | Reacts to events, executes CBS instructions |
| Reporting BC | Conformist | All BCs via Kafka | Pure downstream, never influences upstream |
| Account Lifecycle BC | Open Host Service | All Channel BCs | Stable versioned API, breaking changes = deprecation period |

---

## The Three Decisions That Separate This From a Junior Answer

### Decision 1 — Two aggregates, not one

> "LSA and FTD are not the same aggregate with a product-type flag. They have incompatible invariants. FTD.withdraw() always throws. LSA.withdraw() enforces notice period and minimum balance. If I model these as one aggregate with a switch on product type, I violate Tell Don't Ask and create a class with two reasons to change — one per product. Separate aggregate classes in the same bounded context, different repositories, different domain events."

### Decision 2 — TM Vault is not just a system to wrap

> "Vault is API-first and event-driven — different from T24. For FTD, Vault's smart contract enforces the term at CBS level — it won't accept a withdrawal posting order before maturity. My domain aggregate and Vault's contract are belt-and-braces: invariant enforced at domain level AND CBS level. The ACL translates models. Vault's webhook events also feed my Operations BC for reconciliation. This dual enforcement matters for PRA operational resilience requirements."

### Decision 3 — Invariants live in Account Lifecycle BC, not Servicing BC

> "Servicing BC knows not to show 'Add Funds' for an FTD — that's UX routing. But if an FTD withdrawal request somehow reaches Account Lifecycle BC, the FTD aggregate throws. Never rely on the channel layer alone for invariant enforcement. This is defence in depth: Servicing BC for user experience, Account Lifecycle BC for correctness, TM Vault for CBS-level enforcement. Three layers — any one of them catches the violation."

---

## Regulatory Hooks Per Context

| BC | Regulation | Obligation |
|---|---|---|
| Onboarding | FCA KYC / AML | Customer due diligence before account opening |
| Onboarding | GDPR Art 6 | Lawful basis for processing personal data |
| Servicing (LSA) | FCA BCOBS 5 | Prompt payment of withdrawal requests |
| Servicing (FTD) | FCA BCOBS | Clear disclosure that funds locked until maturity |
| Account Lifecycle | FCA BCOBS | Interest credited correctly and promptly |
| Account Lifecycle | PRA CRR | FSCS-eligible deposit tracking per customer |
| Reporting | FCA SUP | Regulatory returns — savings volumes, rates |
| Reporting | PRA | Liquidity coverage ratio — stable funding |
| Reporting | FSCS | Eligible depositor reporting — £85k limit |
| Operations | FCA | Maturity processing within contractual timeframe |

---

*LSA = Liquid Savings Account | FTD = Fixed Term Deposit | TM = Thought Machine Vault*
*BCOBS = Banking Conduct of Business Sourcebook | FSCS = Financial Services Compensation Scheme*
*PRA = Prudential Regulation Authority | ACL = Anti-Corruption Layer*
