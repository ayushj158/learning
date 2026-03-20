Got it — **GoF Design Patterns** first. Given your FS/banking context, I'll make every example real — payments, fraud, CBS integration, trade settlement.

# GoF Design Patterns — EM Interview Cheat Sheet
> Prepared for: EM/Senior EM interviews at tier-1 FS GCCs (JPMC, Goldman Sachs, Citi, HSBC)
> Context: 12 years Java/Microservices, Lloyds Banking Group, Commercial Banking domain

---

## 🧭 Category Mental Model

| Category | One-liner | Think of it as |
|---|---|---|
| **Creational** | How objects are born — who creates, how many, in what state | Birth control for objects |
| **Structural** | How objects connect and hide complexity | Skeleton + connective tissue |
| **Behavioural** | How objects communicate and distribute responsibility | Nervous system — signals + responses |

**Interview anchor:** *"Creational patterns control object birth. Structural patterns control composition and connection. Behavioural patterns control communication and responsibility distribution. In an FS microservices system I see all three — Builders for immutable payment objects, Adapters at CBS integration boundaries, Strategy for payment routing logic."*

---

## 🟦 CREATIONAL PATTERNS

### Singleton
**What:** One instance per JVM with a global access point.

**Implementations (know all four):**
- **Enum** — serialisation-safe, reflection-safe. Best for true singletons.
- **Holder (static inner class)** — lazy + thread-safe via JVM class loading. Recommended for most cases.
- **Double-Checked Locking + volatile** — lazy, needs `volatile` to prevent instruction reordering. `volatile` adds memory barrier — without it, Thread B can see non-null reference to uninitialised object.
- **Eager** — thread-safe by JVM, but created even if unused.

**Key risks:**
- Singleton ≠ singleton in distributed systems — 20 pods = 20 instances. Use Redis for distributed shared state.
- Mutable singleton state = non-deterministic parallel tests. Use `@MockBean` or reset in `@BeforeEach`.
- Classloader isolation in app servers — multiple WARs get separate instances.

**Your real example:**
> *"FX Rate Cache — one HikariCP pool per datasource per JVM. For distributed FX rate consistency across 20 pods we use Redis with a TTL, not a local Singleton. Under PCI-DSS, stale rate data in a payment instruction could cause a compliance breach — Redis with TTL solves both the distributed state problem and the regulatory one."*

---

### Builder
**What:** Separates complex object construction from representation. Immutable after build.

**Key insight:** Construction-time validation means the object can never exist in an invalid state. With a setter-based approach, a thread could read a half-constructed payment object. Builder + immutability = thread safety for free on reads.

**When to write explicit vs Lombok `@Builder`:**
- Lombok `@Builder` → DTOs, simple value objects
- Explicit Builder → domain objects needing cross-field validation or business rules

**Your real example:**
> *"PaymentRequest Builder — 15+ fields, CHAPS vs FPS threshold check at build time (CHAPS not appropriate below £250k), mandatory debit/credit account validation. Immutable after construction — no setters means no partially constructed payment object leaking into multi-threaded payment processing. Also AuditEvent Builder to ensure compliance logs are never partially constructed."*

---

### Factory Method
**What:** Interface for creating an object; subclasses decide which concrete class. One product.

**Key rule:** Adding a new type = new subclass only. Never modify the factory itself. That's the Open/Closed Principle.

**Your real example:**
> *"UKPaymentProcessorFactory — switch on PaymentType returns CHAPS / BACS / FPS / SWIFT processor. When we needed to add SEPA for an EU subsidiary, it was one new SWIFTProcessor subclass and one new factory case — zero regression risk to existing payment rails."*

---

### Abstract Factory
**What:** Creates a *family* of related objects. All products are consistent with each other.

**vs Factory Method:** Factory = one product. Abstract Factory = whole family that must be compatible.

**Your real example:**
> *"UKPaymentInfrastructureFactory returns FCACompliantValidator + FasterPaymentsProcessor + FCAMandatedAuditLogger as a consistent family. EUPaymentInfrastructureFactory returns GDPRCompliantValidator + SEPAProcessor + EBACompliantAuditLogger. Mixing a UK processor with an EU validator would be a compliance failure — Abstract Factory prevents that at design time."*

---

## 🟩 STRUCTURAL PATTERNS

### Adapter 🔌
**What:** Makes incompatible interfaces work together. The Anti-Corruption Layer (ACL) made concrete.

**Object vs Class Adapter:**
- Object Adapter (composition) — preferred. Mockable, not locked into hierarchy.
- Class Adapter (inheritance) — avoid in Java due to single-inheritance constraint.

**What the Adapter does at Lloyds-scale:**
1. Protocol translation — SOAP to REST, ISO 8583 to internal JSON
2. Model translation — T24 field names to domain field names
3. Error translation — T24 error codes to domain exceptions
4. Logging boundary — PII masking before logging (GDPR Article 25 — data minimisation by design)

**Your real example:**
> *"TemenosPaymentGatewayAdapter — translates PaymentRequest → T24TransactionRequest internally. When T24 was upgraded from R19 to R20, it was a 2-week change confined to one adapter module. Teams that called T24 directly had a 3-month refactor. Under GDPR Article 25, the adapter is also a data filtering boundary — T24 response fields we don't need are stripped before the data enters our domain."*

---

### Facade 🏛️
**What:** Simplified, unified interface to a complex subsystem. Hides many classes behind one.

**vs Adapter:** Adapter makes incompatible things work. Facade simplifies many things into one.

**vs API Gateway:** API Gateway = infrastructure-level facade (routing, auth, rate limiting). Application Facade = business orchestration service you write. Never put payment orchestration logic in Kong/Apigee plugins.

**Conway's Law connection:** Facade = team boundary. Channels team calls PaymentProcessingFacade. They never need to know how many internal services are involved.

**Your real example:**
> *"PaymentProcessingFacade — Validate → FraudScreen → FXConvert → CBSDebit → SchemeSubmit → Audit → Notify. Controller makes one call. Target sub-200ms SLA for payment submission confirmation. AccountOpeningFacade wraps KYC + Credit check + CBS account creation + Card provisioning + Welcome comms — onboarding service doesn't orchestrate all of those directly."*

---

### Decorator 🎨
**What:** Adds behaviour dynamically without subclassing. Decorator and wrapped object implement the same interface.

**Why not subclassing:** 4 payment types × 3 cross-cutting concerns = 12 subclasses. Add a new concern = 16. Decorator gives you 4 + 3 = 7 classes, infinitely composable.

**Decorator vs Spring AOP:**
- AOP for true infrastructure cross-cutting (logging, metrics, transactions)
- Explicit Decorator when compliance team needs to see, review, and test the wrapping — SanctionsCheckDecorator should be visible in a code review, not hidden in an aspect

**Order matters:** Metrics (outermost — always measures) → Auditing (always audits) → RateLimiting (checks limit) → Base (innermost — core logic)

**Your real example:**
> *"SanctionsCheckDecorator wrapping all three payment processors (CHAPS, BACS, FPS) — added OFAC/HMT sanctions screening to every outbound payment without modifying any processor. AuditingDecorator for FCA-mandated 5-year audit retention. RateLimitingDecorator for PCI-DSS transaction velocity controls. Spring's @Transactional, @Cacheable, @Async are all Decorators at the proxy level — I use this to explain the pattern to my team since they already use it daily."*

---

### Proxy 🔒
**What:** Controls access to an object. Three types: Virtual (lazy), Protection (auth), Remote (network).

**vs Decorator:** Decorator adds/enhances. Proxy controls access. Both implement same interface — distinction is intent.

**Spring trap — @Transactional on private method:**
> *"Spring's @Transactional works via JDK dynamic proxy or CGLIB proxy. The proxy wraps your bean and intercepts method calls. Private methods are not accessible to the proxy — so @Transactional on a private method silently does nothing. No error, no rollback. I've caught this in code reviews three times. The rule: @Transactional only on public methods of Spring-managed beans."*

**Your real example:**
> *"SecuredPaymentProcessor — protection proxy checks PAYMENT_INITIATOR role and validates amount against user's authorisation limit before delegating to real processor. gRPC stubs to Lloyds CBS = remote proxy — your code calls them as if local, the stub handles network, serialisation, timeout, retry. LazyTransactionReportProxy = virtual proxy for expensive account history queries — deferred until first access."*

---

### Composite 🌳
**What:** Tree structure where individual objects and groups are treated uniformly.

**Trigger:** Part-whole hierarchy where caller shouldn't care if it's dealing with one or many.

**Your real example:**
> *"Payment approval hierarchy — IndividualApprover (leaf) and ApprovalGroup (composite, ANY_ONE or ALL_REQUIRED policy). Board group contains SeniorManagers group, which contains individual approvers. Caller calls approve() on whatever node it has — doesn't know if it's one person or a group. FCA dual-control requirement for high-value CHAPS payments: amounts above £250k require ApprovalGroup with ALL_REQUIRED — maker-checker compliance built into the structure."*

---

### Bridge 🌉
**What:** Decouples abstraction from implementation so both can vary independently. Upfront design decision.

**vs Adapter:** Adapter = after-the-fact fix for incompatibility. Bridge = upfront design when you know two dimensions will vary independently.

**Class explosion prevention:** 5 notification types × 4 channels = 20 classes without Bridge. With Bridge = 9 classes. Add WhatsApp = 1 new class, zero changes to notification types.

**Your real example:**
> *"Notification system — abstraction hierarchy (PaymentConfirmation, FraudAlert, StatementReady, etc.) bridged to channel hierarchy (Email via SendGrid, SMS via Twilio, Push via FCM, WhatsApp via Meta API). When we added WhatsApp for Commercial Banking clients, it was one new NotificationChannel class — the five notification types required zero changes. Open/Closed Principle in practice."*

---

## 🟥 BEHAVIOURAL PATTERNS

### Strategy 🎯
**What:** Define a family of algorithms, encapsulate each, make them interchangeable. Swap at runtime.

**Trigger:** `switch/if` on a business concept = Strategy waiting to be extracted.

**Team rule:** Any method with more than 3 conditions on a business type (PaymentType, CustomerSegment, Currency) goes in a code review with Strategy as the recommendation.

**Your real examples:**

*Payment routing:*
> *"PaymentRouter with CHAPSRoutingStrategy (urgent + ≥£10k), SWIFTRoutingStrategy (cross-border or non-GBP), FasterPaymentsRoutingStrategy (default, sub-£1M). Spring injects strategies in priority order. Adding a new payment rail — say SEPA — is one new class, zero modifications to existing strategies. The 847-line switch statement I inherited became 4 classes."*

*Customer segment notification:*
> *"NotificationStrategy per segment — Retail (SMS only, casual tone), Premium (formal email + RM alert for payments >£50k), Commercial (structured email + webhook for ERP + FCA CASS audit log), Private Banking (push + email + RM always notified regardless of amount). Each strategy owns its own template and delivery mechanism. Adding ULTRA_HIGH_NET_WORTH = one new class, zero existing changes."*

*Accounts API enrichment:*
> *"AccountEnrichmentStrategy per business context — SERVICING (fetch existing servicing instructions + open cases, batch fetched to avoid N+1), PAYMENTS (fetch available balances + payment limits batch), VIEW (minimal — no extra data fetch, GDPR data minimisation). Strategy selection after filter chain runs."*

---

### Chain of Responsibility ⛓️
**What:** Request passes through a chain of handlers. Each handler decides: process, pass on, or stop.

**vs Strategy:** Strategy = pick ONE algorithm. CoR = run ALL handlers in sequence until one stops it.

**vs Decorator:** Decorator always runs all wrappers (enhancement). CoR stops on failure (gate/decision).

**Ordering principle:** Cheapest check first (in-memory, nanoseconds) → most expensive last (external API, 50-200ms). At 50M payments/month, every unnecessary external call is real money.

**Everyday example to explain to team:** Spring Security filter chain — authentication filter, CORS filter, logging filter, rate limiting filter. They already use CoR daily without realising it.

**Your real examples:**

*Payment validation:*
> *"PaymentValidationChain — Schema validation (in-memory, ns) → Account existence (CBS lookup, ms) → Balance check (CBS call) → Sanctions screening OFAC/HMT (external, 50-200ms) → Payment limit check (DB). Fail-fast on cheapest first. At Lloyds scale, skipping unnecessary sanctions calls on schema-invalid payments saves significant cost."*

*Eligibility checks:*
> *"EligibilityChain for product eligibility — AgeHandler (U18 hard block, in-memory) → SegmentEligibilityHandler (segments X/Y/Z only, in-memory) → RingFencedBankHandler (FSMA 2000 s142A — ring-fenced bank cannot serve certain large corporates for investment products) → ProductRulesHandler (DB lookup). These are gates not enhancements — first failure stops the chain. Decorator would have been wrong here: Decorator runs all wrappers always."*

*Accounts API filtering:*
> *"AccountFilterChain — BusinessContextFilter (SERVICING/PAYMENTS/VIEW rules) → AccountIdFilter (optional dimension, skips if no IDs specified) → ProductTypeFilter (optional dimension). All three always run — progressively narrow the list. Distinct from enrichment which is Strategy (one strategy selected based on context)."*

---

### Observer 👁️
**What:** One-to-many dependency. Subject changes state → all registered observers notified automatically.

**In-process Observer vs Kafka:**
- In-process (Spring ApplicationEvents) — same transaction context, immediate, synchronous within service
- Kafka — cross-service, durable, async, survives restarts
- Never use in-process where you need durability. Never use Kafka where you need same-transaction consistency.

**Critical production trap:**
> *"Observer notifications fired BEFORE the database commit completed. Fraud service received PaymentSubmittedEvent, queried the payment, got a 404. Fix: @TransactionalEventListener(phase = AFTER_COMMIT) — fires only after transaction commits successfully."*

**Your real example:**
> *"PaymentSubmittedEvent → FraudMonitoringObserver (async fraud assessment) + RegulatoryReportingObserver (SAR evaluation for payments >£10k — POCA 2002 requirement) + CustomerNotificationObserver + AnalyticsObserver. PaymentService publishes the event and knows none of its observers. Adding a new observer = new @EventListener bean, zero changes to PaymentService. SAR threshold check is in the observer — business rule lives close to the regulatory concern."*

---

### Command 📋
**What:** Encapsulates a request as an object. Enables queuing, audit trail, undo/reversal, approval workflows.

**Key insight:** The Command object IS the audit trail. Immutable record of who instructed what and when. This directly satisfies FCA requirements for dual control on high-value payments.

**Your real example:**
> *"TransferFundsCommand — maker creates the command object (encapsulating all payment details + initiatorId + timestamp). Command is queued. Approver executes it. undo() = reversal by swapping debit and credit accounts. For CHAPS payments above £250k under Lloyds commercial banking policy, FCA requires dual control — Command pattern is the structural implementation of that regulatory requirement."*

---

### State 🔄
**What:** Object behaviour changes as its internal state changes. Invalid transitions become impossible — they throw exceptions.

**Key insight:** With an enum + switch approach, a developer could accidentally add a case that bypasses fraud screening. With State pattern, that transition literally cannot be coded — the InitiatedState doesn't have a settle() method that works.

**Spring State Machine:** Use for 8+ states with audit requirements and state persistence. Explicit State classes for simpler flows (3-4 states).

**Your real example:**
> *"PaymentLifecycle — INITIATED → VALIDATED → FRAUD_CLEARED → SUBMITTED → SETTLED/FAILED. Production incident: a payment moved INITIATED → SETTLED directly, bypassing fraud screening. Root cause: developer added a shortcut in a switch statement. Regulatory exposure: FCA SYSC 6 — firms must have robust fraud controls. State pattern makes that shortcut impossible at design time — InitiatedState.settle() throws InvalidStateTransitionException."*

---

### Template Method 📐
**What:** Algorithm skeleton fixed in base class. Subclasses implement specific steps. Final template method ensures skeleton is never bypassed.

**vs Strategy:** Template Method = inheritance, skeleton fixed. Strategy = composition, whole algorithm swappable. Prefer Strategy + composition in modern Java for testability. Template Method valid when skeleton is truly invariant (e.g. FCA audit step must always run).

**Your real example:**
> *"PaymentProcessingTemplate — validate → buildInstruction → enrichWithSchemeData → submitToScheme → buildResult → auditPayment. Steps 1, 5, 6 are common and final — FCA-mandated audit runs for all payment schemes, cannot be bypassed. CHAPSProcessor implements steps 2-4: adds settlement cycle + RTGS reference. SWIFTProcessor adds BIC + correspondent bank chain. The final template method prevents a subclass from accidentally skipping the audit step."*

---

## ⚡ Critical Confusions — Interview Pairs

| Pair | The Distinction |
|---|---|
| **Adapter vs Facade** | Adapter = make one incompatible thing compatible. Facade = hide many things behind one simple interface. |
| **Decorator vs Proxy** | Decorator adds/enhances behaviour. Proxy controls access (guards, defers, remotes). Both wrap same interface — intent differs. |
| **Strategy vs Chain** | Strategy = pick ONE algorithm to run. CoR = run ALL handlers in sequence, stop on failure. |
| **Decorator vs CoR** | Decorator: all wrappers always run (enhancement). CoR: stops on failure (gate). Use CoR for eligibility/validation. Use Decorator for audit/metrics/sanctions layering. |
| **Observer vs Kafka** | Observer = in-process, same JVM, same transaction. Kafka = cross-service, durable, survives restarts. Never use in-process where you need durability. |
| **Factory vs Abstract Factory** | Factory = one product. Abstract Factory = consistent family of products. Mixing UK processor + EU validator = compliance failure. |
| **Template Method vs Strategy** | Template = inheritance, skeleton fixed, steps vary. Strategy = composition, whole algorithm swappable. Prefer Strategy in modern Java. |

---

## 🎯 EM Framing Anchors

Every pattern answer should connect to one of these:

| Principle | Patterns |
|---|---|
| **Open/Closed Principle** | Strategy, Decorator, CoR, Factory — add new behaviour without modifying existing code |
| **Testability** | Builder (construct-time validation), Strategy (test each algorithm independently), Decorator (test each wrapper) |
| **Compliance by design** | State (invalid transitions impossible), Command (immutable audit trail), Decorator (sanctions/audit wrapping), Abstract Factory (consistent regulatory family) |
| **Integration boundary protection** | Adapter (ACL, GDPR data minimisation), Facade (hide CBS complexity) |

**Regulatory contexts to name-drop per pattern:**
- Builder → PCI-DSS (immutable payment objects)
- Adapter → GDPR Article 25 (data minimisation at boundary)
- Decorator → FCA (audit retention), PCI-DSS (velocity controls), OFAC/HMT (sanctions)
- Command → FCA (dual control, SYSC rules)
- State → FCA SYSC 6 (fraud controls), regulatory compliance by design
- CoR → OFAC/HMT sanctions, POCA 2002 (SAR thresholds), FSMA 2000 (ring-fencing)
- Observer → POCA 2002 (SAR reporting, >£10k threshold)
- Abstract Factory → FCA vs EBA vs GDPR compliance families

---

*Topics covered: Singleton, Builder, Factory Method, Abstract Factory, Adapter, Facade, Decorator, Proxy, Composite, Bridge, Strategy, Observer, Chain of Responsibility, Command, State, Template Method*


---
---

# TOPIC 1: Creational Patterns

## 🔴 Singleton Pattern

### 1. Core Concepts + Internals

Singleton ensures **one instance per JVM**, with a global access point. Simple concept — but the implementation details are where interviews separate juniors from seniors.

**Four implementations — know all four and their tradeoffs:**

### Implementation 1: Eager Initialisation
```java
public class FraudRuleEngine {
    // Created at class loading time — thread safe by JVM spec
    private static final FraudRuleEngine INSTANCE = new FraudRuleEngine();
    
    private FraudRuleEngine() {}
    
    public static FraudRuleEngine getInstance() {
        return INSTANCE;
    }
}
```
✅ Thread-safe (JVM class loading is atomic)
❌ Created even if never used — wasteful if expensive to initialise

---

### Implementation 2: Double-Checked Locking (DCL)
```java
public class PaymentConfigCache {
    // volatile is NON-NEGOTIABLE here
    private static volatile PaymentConfigCache INSTANCE;
    
    private PaymentConfigCache() {}
    
    public static PaymentConfigCache getInstance() {
        if (INSTANCE == null) {                    // First check — no lock
            synchronized (PaymentConfigCache.class) {
                if (INSTANCE == null) {            // Second check — with lock
                    INSTANCE = new PaymentConfigCache();
                }
            }
        }
        return INSTANCE;
    }
}
```
**Why `volatile` is mandatory here — this is the deep internals bit:**

Without `volatile`, the JVM/CPU can reorder instructions. Object creation is actually 3 steps:
1. Allocate memory
2. Initialise the object
3. Assign reference to `INSTANCE`

Without `volatile`, steps 2 and 3 can be **reordered**. Thread B could see a non-null `INSTANCE` that points to **uninitialised memory** — causing silent data corruption in a payment config read.

`volatile` adds a **memory barrier** — forces happens-before between write and read.

---

### Implementation 3: Holder Pattern (Best for lazy + thread-safe)
```java
public class ExchangeRateService {
    
    private ExchangeRateService() {}
    
    // Inner class loaded only when getInstance() is called
    private static class Holder {
        static final ExchangeRateService INSTANCE = new ExchangeRateService();
    }
    
    public static ExchangeRateService getInstance() {
        return Holder.INSTANCE;
    }
}
```
✅ Lazy — inner class loaded only on first access
✅ Thread-safe — JVM class loading guarantee
✅ No synchronisation overhead
**This is the recommended implementation for most production use.**

---

### Implementation 4: Enum Singleton (Serialisation-safe)
```java
public enum FXRateProvider {
    INSTANCE;
    
    public BigDecimal getRate(String currencyPair) {
        // fetch from cache or provider
    }
}
```
✅ Serialisation-safe — enum deserialisation never creates a new instance
✅ Reflection-safe — cannot break via `Constructor.setAccessible(true)`
✅ Thread-safe by JVM spec
❌ Cannot extend another class (enum limitation)

---

### 2. FS-Specific Use Cases

| Singleton Use Case | Why Singleton | Risk if not |
|---|---|---|
| **FX Rate Cache** | One cache per JVM, expensive to initialise | Multiple caches = stale rate inconsistency |
| **Fraud Rule Engine** | Rules loaded from DB at startup, shared read-only | Multiple instances = memory bloat + rule divergence |
| **Payment Config Cache** | BIC codes, sort code validation tables | Duplicate loads = GC pressure |
| **Connection Pool (HikariCP)** | One pool per datasource per JVM | Multiple pools = connection exhaustion |
| **Audit Log Writer** | Serialised write to compliance log | Multiple writers = ordering violations |

---

### 3. EM-Level Framing

**The tradeoff conversation an EM must have:**

> *"Singleton couples your code to a global state — it's the enemy of testability. In a Spring Boot app, you almost never implement Singleton manually. Spring manages bean lifecycle — default scope IS singleton. The question is whether you need Singleton outside the Spring context, and whether you've accounted for classloader isolation in app servers."*

**Production gotchas to mention in interviews:**

1. **Singleton ≠ Singleton in distributed systems.** One instance per JVM means in a 20-pod Kubernetes deployment, you have 20 instances. Your FX Rate Cache needs a distributed cache (Redis) for consistency — not a local Singleton.

2. **Classloader problem in app servers.** In Tomcat with multiple WARs, each classloader gets its own Singleton instance. Caused production bugs when teams assumed shared state.

3. **Testing problem.** A Singleton with mutable state makes parallel test execution non-deterministic. At Lloyds-scale test suites, this matters. Solution: use Spring's `@MockBean` or reset state in `@BeforeEach`.

**EM decision framing:**
> *"In my team, I discourage manual Singleton implementations. Spring's IoC container handles lifecycle. Where we genuinely need shared state across services — like FX rates — we use Redis with a TTL, not a local Singleton. This also solves the distributed cache consistency problem under PCI-DSS where stale rate data in a payment could cause a compliance breach."*

---

## 🔴 Builder Pattern

### 1. Core Concepts + Internals

Builder separates the **construction of a complex object** from its representation. Critical in FS for **immutable domain objects** — you build once, then the object is read-only.

```java
// Immutable Payment Request — built via Builder
public final class PaymentRequest {
    private final String paymentId;
    private final BigDecimal amount;
    private final Currency currency;
    private final String debitAccountId;
    private final String creditAccountId;
    private final PaymentType paymentType;      // CHAPS, BACS, Faster Payments
    private final String endToEndReference;
    private final Instant valueDate;
    private final String purposeCode;           // ISO 20022 purpose code

    private PaymentRequest(Builder builder) {
        this.paymentId = builder.paymentId;
        this.amount = builder.amount;
        this.currency = builder.currency;
        this.debitAccountId = builder.debitAccountId;
        this.creditAccountId = builder.creditAccountId;
        this.paymentType = builder.paymentType;
        this.endToEndReference = builder.endToEndReference;
        this.valueDate = builder.valueDate;
        this.purposeCode = builder.purposeCode;
    }

    // No setters — immutable after construction

    public static class Builder {
        // Required fields
        private final String paymentId;
        private final BigDecimal amount;
        private final Currency currency;
        
        // Optional fields with defaults
        private PaymentType paymentType = PaymentType.FASTER_PAYMENTS;
        private String endToEndReference;
        private Instant valueDate = Instant.now();
        private String purposeCode;
        private String debitAccountId;
        private String creditAccountId;

        public Builder(String paymentId, BigDecimal amount, Currency currency) {
            // Validate mandatory fields at construction time
            Objects.requireNonNull(paymentId, "paymentId is mandatory");
            Objects.requireNonNull(amount, "amount is mandatory");
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            this.paymentId = paymentId;
            this.amount = amount;
            this.currency = currency;
        }

        public Builder debitAccount(String accountId) {
            this.debitAccountId = accountId;
            return this;
        }

        public Builder creditAccount(String accountId) {
            this.creditAccountId = accountId;
            return this;
        }

        public Builder paymentType(PaymentType type) {
            this.paymentType = type;
            return this;
        }

        public Builder purposeCode(String code) {
            this.purposeCode = code;
            return this;
        }

        public PaymentRequest build() {
            // Cross-field validation before building
            if (debitAccountId == null || creditAccountId == null) {
                throw new IllegalStateException(
                    "Both debit and credit accounts required");
            }
            if (paymentType == PaymentType.CHAPS && 
                amount.compareTo(new BigDecimal("250000")) < 0) {
                // CHAPS typically for high-value — warn or reject
                throw new IllegalArgumentException(
                    "CHAPS not appropriate for amounts below £250,000");
            }
            return new PaymentRequest(this);
        }
    }
}

// Usage — readable, self-documenting
PaymentRequest payment = new PaymentRequest.Builder(
        UUID.randomUUID().toString(),
        new BigDecimal("50000.00"),
        Currency.getInstance("GBP"))
    .debitAccount("GB29NWBK60161331926819")
    .creditAccount("GB82WEST12345698765432")
    .paymentType(PaymentType.CHAPS)
    .purposeCode("SALA")           // ISO 20022: Salary payment
    .build();
```

### 2. FS-Specific Use Cases

| Builder Use Case | Why Builder |
|---|---|
| `PaymentRequest` | 15+ fields, some optional, must be immutable for audit |
| `FraudAssessmentRequest` | Complex input — device fingerprint, geo, amount, merchant |
| `TradeOrder` | ISIN, quantity, side, orderType, TIF — many combos |
| `LoanApplicationRequest` | Applicant details, collateral, purpose — partial builds during multi-step form |
| `AuditEvent` | Consistent structure for compliance log — never partially constructed |

### 3. EM-Level Framing

**The key EM insight:**
> *"Builder isn't just about readability. In a regulated environment, it enforces **construction-time validation** — your PaymentRequest can never exist in an invalid state. Compare that to a setter-based approach where a thread could read a half-constructed payment object. With Builder + immutability, you get thread safety for free — no synchronisation needed on reads."*

**Lombok @Builder — the team decision:**
```java
@Builder
@Value  // Makes all fields final, no setters
public class PaymentRequest {
    String paymentId;
    BigDecimal amount;
    // ...
}
```
> *"In my team, we use Lombok `@Builder` with `@Value` for internal DTOs. But for domain objects — PaymentRequest, TradeOrder — we write explicit Builders with validation logic. Lombok's generated builder has no place for cross-field validation or business rules. That's an EM-level decision: developer convenience vs domain integrity."*

---

## 🔴 Factory Method & Abstract Factory

### Factory Method — Core Concept

Define an **interface for creating an object**, but let subclasses decide which class to instantiate.

```java
// FS Example: Payment Processor Factory
public interface PaymentProcessor {
    PaymentResult process(PaymentRequest request);
}

public class CHAPSProcessor implements PaymentProcessor { ... }
public class BACSProcessor implements PaymentProcessor { ... }
public class FasterPaymentsProcessor implements PaymentProcessor { ... }
public class SWIFTProcessor implements PaymentProcessor { ... }

// Factory Method
public abstract class PaymentProcessorFactory {
    // Factory method — subclasses override this
    public abstract PaymentProcessor createProcessor(PaymentRequest request);
    
    // Template method uses the factory method
    public PaymentResult processPayment(PaymentRequest request) {
        PaymentProcessor processor = createProcessor(request);
        validateRequest(request);
        return processor.process(request);
    }
}

// Concrete Factory
public class UKPaymentProcessorFactory extends PaymentProcessorFactory {
    @Override
    public PaymentProcessor createProcessor(PaymentRequest request) {
        return switch (request.getPaymentType()) {
            case CHAPS -> new CHAPSProcessor();
            case BACS -> new BACSProcessor();
            case FASTER_PAYMENTS -> new FasterPaymentsProcessor();
            default -> throw new UnsupportedPaymentTypeException(
                request.getPaymentType());
        };
    }
}
```

### Abstract Factory — The Distinction

Abstract Factory creates **families of related objects**. The key difference: Factory Method creates **one product**; Abstract Factory creates **multiple related products**.

```java
// FS Example: Payment infrastructure per region
// Each region needs: Processor + Validator + AuditLogger (a family)

public interface PaymentInfrastructureFactory {
    PaymentProcessor createProcessor();
    PaymentValidator createValidator();
    AuditLogger createAuditLogger();
}

public class UKPaymentInfrastructureFactory 
        implements PaymentInfrastructureFactory {
    public PaymentProcessor createProcessor() {
        return new FasterPaymentsProcessor();  // UK-specific
    }
    public PaymentValidator createValidator() {
        return new FCACompliantValidator();     // FCA rules
    }
    public AuditLogger createAuditLogger() {
        return new FCAMandatedAuditLogger();   // FCA audit trail
    }
}

public class EUPaymentInfrastructureFactory 
        implements PaymentInfrastructureFactory {
    public PaymentProcessor createProcessor() {
        return new SEPAProcessor();            // EU-specific
    }
    public PaymentValidator createValidator() {
        return new GDPRCompliantValidator();   // GDPR rules
    }
    public AuditLogger createAuditLogger() {
        return new EBACompliantAuditLogger(); // EBA audit trail
    }
}
```

**The interview distinction to nail:**
> *"Factory Method is about one object — 'give me the right processor'. Abstract Factory is about a consistent family — 'give me the entire UK payment infrastructure where all components are FCA-compliant and consistent with each other'. Mixing a UK processor with an EU validator would be a compliance failure — Abstract Factory prevents that."*

---

# ✅ Creational Patterns — Covered

**Singleton** | **Builder** | **Factory Method** | **Abstract Factory** | **Prototype** (less critical for EM)

---

# 🎯 DRILL TIME

Pick **one** question to answer — I'll score you out of 10:

---

**Drill A — Singleton:**
> *"In your Lloyds Banking Group engagement, where have you used or seen Singleton pattern? What was the risk, and how did you ensure thread safety in a containerised environment with 15 pods?"*

**Drill B — Builder:**
> *"Your team is building a PaymentRequest object with 20 fields. A junior engineer suggests using a no-arg constructor with setters. Walk me through your code review feedback and the production risk they're introducing in a multi-threaded payment processing service."*

**Drill C — Factory:**
> *"You're designing a payment routing engine that needs to support CHAPS, BACS, Faster Payments, and SWIFT — with a new payment rail added every 6 months. How do you use Factory pattern to make this extensible without modifying existing code? What's the Open/Closed Principle connection?"*

**Or say "move on"** to jump to Structural Patterns.

Great call — let me set the conceptual framing first, then dive into Structural.

---

# 🧭 Pattern Category Primer — The Mental Model

Before we go pattern by pattern, here's how to **think about each category** — and how to articulate this in an interview:

---

## Creational Patterns — *"How do I create objects?"*
> **Problem they solve:** Object creation logic is scattered, complex, or tightly coupled to concrete classes. You want to **control and abstract the construction process.**

Think of it as: **the birth of objects**
- Who creates it?
- How is it created?
- How many can exist?
- In what state does it arrive?

Patterns: Singleton, Builder, Factory Method, Abstract Factory, Prototype

---

## Structural Patterns — *"How do I compose objects and classes together?"*
> **Problem they solve:** You have objects/classes that don't fit together naturally — wrong interface, too complex, need wrapping, need layering. Structural patterns are about **wiring and composition** — making things work together that weren't designed to.

Think of it as: **the skeleton and connective tissue of your system**
- How do components connect?
- How do I hide complexity?
- How do I make incompatible things compatible?
- How do I add responsibility without changing the original?

Patterns: Adapter, Decorator, Facade, Proxy, Composite, Bridge, Flyweight

**The EM framing:** In a microservices FS system, structural patterns appear most at **integration boundaries** — wrapping legacy CBS, adapting third-party APIs, hiding subsystem complexity from callers.

---

## Behavioural Patterns — *"How do objects communicate and distribute responsibility?"*
> **Problem they solve:** Too much logic in one place, objects too tightly coupled in how they talk to each other, algorithms need to vary at runtime. Behavioural patterns are about **who does what, and how control flows.**

Think of it as: **the nervous system — how signals travel and who responds**
- Who handles this request?
- How do I vary behaviour at runtime?
- How do I decouple sender from receiver?
- How do I encapsulate a workflow?

Patterns: Strategy, Observer, Chain of Responsibility, Command, State, Template Method, Mediator, Iterator

---

## One-Line Summary to Use in Interviews:
> *"Creational patterns control object birth. Structural patterns control how objects are composed and connected. Behavioural patterns control how objects communicate and share responsibility. In an FS microservices system, I see all three — Builders for immutable payment objects, Adapters at CBS integration boundaries, and Strategy for payment routing logic."*

---

Now — Structural Patterns. These are **extremely relevant** for your Lloyds Banking Group context given you're integrating with a Core Banking System.

---

# 🏗️ STRUCTURAL PATTERNS — EM-Level Deep Dive

---

# PATTERN 1: Adapter 🔌

## 1. Core Concept + Internals

**The problem:** You have an existing interface your code expects. You have a third-party or legacy class with a **different interface**. You can't change either. Adapter sits in between and **translates**.

Two flavours:

### Object Adapter (composition — preferred)
```java
// What your Payment Service expects
public interface PaymentGateway {
    PaymentResult submitPayment(PaymentRequest request);
    PaymentStatus getStatus(String paymentId);
}

// What the legacy CBS (e.g. Temenos T24) actually exposes
// You cannot modify this — it's a vendor library
public class TemenosT24Client {
    public T24Response executeTransaction(T24TransactionRequest req) { ... }
    public T24StatusResponse queryTransaction(String txnRef) { ... }
}

// Adapter — translates between the two
public class TemenosPaymentGatewayAdapter implements PaymentGateway {
    
    private final TemenosT24Client t24Client;        // Adaptee — composed in
    private final T24RequestMapper requestMapper;
    private final PaymentResultMapper resultMapper;
    
    public TemenosPaymentGatewayAdapter(TemenosT24Client t24Client,
                                         T24RequestMapper requestMapper,
                                         PaymentResultMapper resultMapper) {
        this.t24Client = t24Client;
        this.requestMapper = requestMapper;
        this.resultMapper = resultMapper;
    }

    @Override
    public PaymentResult submitPayment(PaymentRequest request) {
        // Translate PaymentRequest → T24TransactionRequest
        T24TransactionRequest t24Request = requestMapper.toT24(request);
        
        // Call legacy system
        T24Response t24Response = t24Client.executeTransaction(t24Request);
        
        // Translate T24Response → PaymentResult
        return resultMapper.fromT24(t24Response);
    }

    @Override
    public PaymentStatus getStatus(String paymentId) {
        T24StatusResponse status = t24Client.queryTransaction(paymentId);
        return resultMapper.statusFromT24(status);
    }
}
```

### Class Adapter (inheritance — rarely used in Java)
```java
// Extends the adaptee AND implements the target interface
// Problem: Java single inheritance — you're locked in
public class TemenosAdapter 
        extends TemenosT24Client      // Adaptee
        implements PaymentGateway {   // Target
    
    @Override
    public PaymentResult submitPayment(PaymentRequest request) {
        // Call inherited T24 methods directly
        T24Response response = executeTransaction(toT24Request(request));
        return fromT24Response(response);
    }
}
```

**Why Object Adapter is preferred:**
- Composition over inheritance
- Can adapt multiple adaptees
- Easier to test — mock the composed client
- Not locked into adaptee's class hierarchy

---

## 2. FS-Specific Use Cases

| Adapter Scenario | Adaptee (Legacy/Third-party) | Target Interface |
|---|---|---|
| **CBS Integration** | Temenos T24 / Finacle SOAP API | Your internal `PaymentGateway` |
| **SWIFT Adapter** | SWIFT MX/MT message format | Internal `PaymentMessage` model |
| **FX Rate Provider** | Bloomberg / Reuters API | Internal `FXRateProvider` interface |
| **KYC Provider** | Experian / Equifax proprietary SDK | Internal `KYCVerificationService` |
| **Payment Scheme** | Faster Payments / CHAPS ISO 8583 | Internal `PaymentSchemeClient` |
| **Fraud Provider** | FICO Falcon proprietary API | Internal `FraudScoringService` |

---

## 3. EM-Level Framing

**The architectural insight:**

> *"Adapter is the pattern that makes the Anti-Corruption Layer (ACL) in DDD concrete. When your Payments bounded context integrates with Temenos T24, you don't let T24's model leak into your domain. The Adapter sits at the boundary — your domain speaks PaymentRequest, the adapter translates to T24TransactionRequest internally. If T24 is replaced by Thought Machine Vault tomorrow, you swap the adapter, not your entire domain model."*

**Production reality from your context:**

At Lloyds scale — integrating with a UK Core Banking System — the Adapter pattern is doing several things simultaneously:
1. **Protocol translation** — SOAP to REST, ISO 8583 to internal JSON
2. **Model translation** — T24 field names to domain field names
3. **Error translation** — T24 error codes to domain exceptions
4. **Logging boundary** — PII masking happens at the adapter before logging (GDPR requirement)

**Team decision an EM makes:**
> *"I've seen teams skip the Adapter and call T24 directly from their service layer. Six months later, T24 model objects are scattered across 40 classes. When the bank upgraded T24 from R19 to R20, it was a 3-month refactor. With a proper Adapter, it's a 2-week change confined to one module. I enforce Adapter at every external integration boundary as a non-negotiable architectural standard."*

**Regulatory angle:**
> *"Under GDPR Article 25 — data minimisation by design — the Adapter is also a data filtering boundary. The T24 response may contain fields we don't need and shouldn't log. The mapper strips those fields before the data enters our domain. This is a design-time compliance control, not a runtime one."*

---

# PATTERN 2: Facade 🏛️

## 1. Core Concept + Internals

**The problem:** A subsystem has many complex classes with many methods. Callers shouldn't need to know the internals. Facade provides a **simplified, unified interface** to a complex subsystem.

**Key distinction from Adapter:**
- **Adapter** — makes incompatible interfaces compatible. Existing interface, wrap it.
- **Facade** — simplifies a complex subsystem. Many classes, hide them.

```java
// Complex subsystem — Payment processing has many moving parts
// Callers shouldn't orchestrate all of these directly

public class PaymentValidationService {
    public ValidationResult validate(PaymentRequest request) { ... }
}

public class FraudScreeningService {
    public FraudAssessment screen(PaymentRequest request) { ... }
}

public class FXConversionService {
    public ConvertedAmount convert(BigDecimal amount, 
                                   Currency from, Currency to) { ... }
}

public class CBSDebitService {
    public DebitResult debitAccount(String accountId, 
                                     BigDecimal amount) { ... }
}

public class PaymentSchemeSubmissionService {
    public SchemeReference submit(PaymentInstruction instruction) { ... }
}

public class AuditService {
    public void recordPaymentEvent(AuditEvent event) { ... }
}

public class NotificationService {
    public void notifyPaymentSubmitted(String customerId, 
                                        PaymentResult result) { ... }
}

// ✅ FACADE — hides all of this complexity
// The API layer or orchestration layer calls ONLY this
@Service
public class PaymentProcessingFacade {

    private final PaymentValidationService validationService;
    private final FraudScreeningService fraudService;
    private final FXConversionService fxService;
    private final CBSDebitService cbsDebitService;
    private final PaymentSchemeSubmissionService schemeService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // Constructor injection...

    public PaymentResult processPayment(PaymentRequest request) {
        // Step 1: Validate
        ValidationResult validation = validationService.validate(request);
        if (!validation.isValid()) {
            return PaymentResult.rejected(validation.getReasons());
        }

        // Step 2: Fraud screen
        FraudAssessment fraud = fraudService.screen(request);
        if (fraud.isHighRisk()) {
            auditService.recordPaymentEvent(
                AuditEvent.fraudBlocked(request, fraud));
            return PaymentResult.blocked(fraud.getReason());
        }

        // Step 3: FX if cross-currency
        BigDecimal settledAmount = request.getAmount();
        if (!request.getCurrency().equals(Currency.getInstance("GBP"))) {
            settledAmount = fxService.convert(
                request.getAmount(), 
                request.getCurrency(), 
                Currency.getInstance("GBP")).getAmount();
        }

        // Step 4: Debit CBS
        DebitResult debit = cbsDebitService.debitAccount(
            request.getDebitAccountId(), settledAmount);

        // Step 5: Submit to payment scheme
        SchemeReference ref = schemeService.submit(
            toInstruction(request, debit, settledAmount));

        // Step 6: Audit + Notify
        PaymentResult result = PaymentResult.submitted(ref);
        auditService.recordPaymentEvent(
            AuditEvent.paymentSubmitted(request, result));
        notificationService.notifyPaymentSubmitted(
            request.getCustomerId(), result);

        return result;
    }
}
```

---

## 2. FS-Specific Use Cases

| Facade | What it hides |
|---|---|
| **PaymentProcessingFacade** | Validation + Fraud + FX + CBS + Scheme + Audit + Notify |
| **AccountOpeningFacade** | KYC + Credit check + CBS account creation + Card provisioning + Welcome comms |
| **TradeSettlementFacade** | Trade validation + Position check + Custodian instruction + CSD submission + Reconciliation |
| **CustomerOnboardingFacade** | Identity verification + AML screening + Sanctions check + Profile creation |
| **ReportingFacade** | Data aggregation across 5 services + formatting + delivery |

---

## 3. EM-Level Framing

**The deeper insight — Facade as an orchestration boundary:**

> *"In a microservices architecture, Facade at the service level becomes your **orchestration service** or **BFF (Backend for Frontend)**. The mobile banking app shouldn't be making 7 API calls to show a payment confirmation screen. A Facade service aggregates — one call in, one response out. This also reduces chattiness over the network, which matters for latency SLAs — we target sub-200ms for payment submission confirmation at Lloyds."*

**The Facade vs API Gateway distinction:**
> *"An API Gateway is infrastructure-level Facade — routing, auth, rate limiting. An application-level Facade is a service you write — it contains business orchestration logic. Don't confuse the two. I've seen teams try to put payment orchestration logic into Kong/Apigee plugins — it's a maintainability disaster."*

**Team decision:**
> *"Facade is also a team boundary decision. If the Payments team exposes a `PaymentProcessingFacade`, the Channels team (mobile app, internet banking) never needs to know how many internal services are involved. This is Conway's Law in practice — the architecture mirrors the team structure. Each team owns a Facade that is their public contract."*

---

# PATTERN 3: Decorator 🎨

## 1. Core Concept + Internals

**The problem:** You want to add behaviour to an object **dynamically, at runtime**, without modifying the class and without creating an explosion of subclasses.

Key insight: **Decorator wraps the same interface.** Both the decorator and the wrapped object implement the same interface — so they're interchangeable to the caller.

```java
// Core interface
public interface PaymentProcessor {
    PaymentResult process(PaymentRequest request);
}

// Core implementation
public class BasePaymentProcessor implements PaymentProcessor {
    @Override
    public PaymentResult process(PaymentRequest request) {
        // Core payment processing logic
        return submitToPaymentScheme(request);
    }
}

// Abstract Decorator — holds a reference to the wrapped processor
public abstract class PaymentProcessorDecorator implements PaymentProcessor {
    protected final PaymentProcessor wrapped;
    
    protected PaymentProcessorDecorator(PaymentProcessor wrapped) {
        this.wrapped = wrapped;
    }
    
    @Override
    public PaymentResult process(PaymentRequest request) {
        return wrapped.process(request);  // Default — delegate
    }
}

// Decorator 1: Audit logging
public class AuditingPaymentProcessor extends PaymentProcessorDecorator {
    private final AuditService auditService;
    
    public AuditingPaymentProcessor(PaymentProcessor wrapped,
                                     AuditService auditService) {
        super(wrapped);
        this.auditService = auditService;
    }
    
    @Override
    public PaymentResult process(PaymentRequest request) {
        auditService.logPaymentAttempt(request);      // Before
        PaymentResult result = wrapped.process(request);
        auditService.logPaymentResult(request, result); // After
        return result;
    }
}

// Decorator 2: Rate limiting (PCI-DSS — max transactions per account)
public class RateLimitingPaymentProcessor extends PaymentProcessorDecorator {
    private final RateLimiter rateLimiter;
    
    @Override
    public PaymentResult process(PaymentRequest request) {
        if (!rateLimiter.tryAcquire(request.getDebitAccountId())) {
            return PaymentResult.rateLimited(
                "Transaction limit exceeded — PCI-DSS control");
        }
        return wrapped.process(request);
    }
}

// Decorator 3: Metrics / Latency tracking
public class MetricsPaymentProcessor extends PaymentProcessorDecorator {
    private final MeterRegistry meterRegistry;
    
    @Override
    public PaymentResult process(PaymentRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            PaymentResult result = wrapped.process(request);
            sample.stop(Timer.builder("payment.processing.time")
                .tag("type", request.getPaymentType().name())
                .tag("status", result.getStatus().name())
                .register(meterRegistry));
            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder("payment.processing.time")
                .tag("status", "ERROR")
                .register(meterRegistry));
            throw e;
        }
    }
}

// Composing decorators — ORDER MATTERS
PaymentProcessor processor = 
    new MetricsPaymentProcessor(          // Outermost — always measures
        new AuditingPaymentProcessor(     // Middle — always audits  
            new RateLimitingPaymentProcessor(  // Inner — checks limit first
                new BasePaymentProcessor(),    // Core logic
                rateLimiter),
            auditService),
        meterRegistry);
```

**In Spring — this is exactly how AOP works internally:**
Spring's `@Transactional`, `@Cacheable`, `@Async` are all Decorators at the proxy level. The Spring proxy wraps your bean and adds behaviour.

---

## 2. FS-Specific Use Cases

| Decorator | Behaviour Added |
|---|---|
| **AuditingDecorator** | Compliance audit trail — FCA requirement, 5-year retention |
| **RateLimitingDecorator** | PCI-DSS transaction velocity controls |
| **EncryptionDecorator** | Encrypt sensitive fields before persistence — GDPR |
| **RetryDecorator** | Retry transient CBS failures (before Resilience4j was standard) |
| **CachingDecorator** | Cache FX rates, BIC lookups — reduce CBS load |
| **SanctionsCheckDecorator** | OFAC/HMT sanctions screening on every payment |

---

## 3. EM-Level Framing

**Decorator vs Inheritance — the critical distinction:**

> *"The classic mistake is to subclass — `AuditingCHAPSProcessor`, `AuditingBACSProcessor`, `AuditingFasterPaymentsProcessor`. With 4 payment types and 3 cross-cutting concerns, you get 12 classes. Add a new concern and it's 16. Decorator gives you 4 + 3 = 7 classes, infinitely composable. This is the Open/Closed Principle made concrete."*

**Decorator vs Spring AOP — when to use which:**

> *"In a Spring Boot service, I prefer AOP for cross-cutting concerns that are truly infrastructure — logging, metrics, transactions. I use explicit Decorator when the wrapping logic is **business-significant** — like a SanctionsCheckDecorator where the business needs to understand the wrapping order, test it independently, and the compliance team needs to see it in a code review. AOP is invisible — Decorator is explicit and reviewable."*

---

# PATTERN 4: Proxy 🔒

## 1. Core Concept + Internals

Proxy provides a **surrogate or placeholder** for another object to control access to it. Looks identical to the real object from the outside.

**Three types you must know:**

### Virtual Proxy — lazy initialisation
```java
// Expensive object — report with 10,000 transactions
public interface TransactionReport {
    List<Transaction> getTransactions();
    BigDecimal getTotalValue();
}

public class RealTransactionReport implements TransactionReport {
    private final List<Transaction> transactions;
    
    public RealTransactionReport(String accountId, DateRange range) {
        // Expensive DB query — don't do this until needed
        this.transactions = loadFromDatabase(accountId, range);
    }
    // ...
}

// Virtual Proxy — defer expensive load until actually needed
public class LazyTransactionReportProxy implements TransactionReport {
    private final String accountId;
    private final DateRange range;
    private TransactionReport realReport;  // null until first access
    
    @Override
    public List<Transaction> getTransactions() {
        if (realReport == null) {
            realReport = new RealTransactionReport(accountId, range);
        }
        return realReport.getTransactions();
    }
}
```

### Protection Proxy — access control
```java
public class SecuredPaymentProcessor implements PaymentProcessor {
    private final PaymentProcessor realProcessor;
    private final SecurityContext securityContext;
    
    @Override
    public PaymentResult process(PaymentRequest request) {
        // Check authorisation before delegating
        if (!securityContext.hasRole("PAYMENT_INITIATOR")) {
            throw new UnauthorisedException(
                "Insufficient privileges for payment initiation");
        }
        // Check payment limit against user's authorisation level
        if (request.getAmount().compareTo(
                securityContext.getPaymentLimit()) > 0) {
            throw new PaymentLimitExceededException(
                "Amount exceeds authorised limit for this user");
        }
        return realProcessor.process(request);
    }
}
```

### Remote Proxy — local interface to remote resource
```java
// gRPC stub IS a remote proxy
// Your code calls it as if it's local
// Proxy handles network, serialisation, timeout, retry
PaymentServiceGrpc.PaymentServiceBlockingStub stub = 
    PaymentServiceGrpc.newBlockingStub(channel);

// Caller doesn't know this is a network call
PaymentResponse response = stub.processPayment(request);
```

---

## 2. EM-Level Framing

**Proxy vs Decorator — the subtle distinction:**

> *"Both wrap an object implementing the same interface. The difference is **intent and control**. Decorator adds behaviour — it enhances. Proxy controls access — it guards, defers, or remotes. A SanctionsCheckDecorator adds a screening step. A SecuredPaymentProcessor proxy decides whether you're even allowed to reach the processor. In practice, Spring's AOP proxy is both — it decorates with transaction management AND proxies for security."*

**Spring's proxy mechanism — EM must know this:**
> *"Spring's `@Transactional` works via JDK dynamic proxy or CGLIB proxy depending on whether the bean implements an interface. This is why `@Transactional` on a private method silently does nothing — the proxy can't intercept it. I've had production incidents where developers marked private methods `@Transactional` and wondered why their data wasn't rolling back. Understanding Spring's proxy model is an EM-level expectation."*

---

# PATTERN 5: Composite 🌳

## 1. Core Concept + Internals

Compose objects into **tree structures** to represent part-whole hierarchies. Treat individual objects and compositions uniformly.

```java
// FS Example: Approval workflow hierarchy
// A payment approval can be: a single approver, OR a group

public interface ApprovalNode {
    ApprovalResult approve(PaymentRequest request);
    boolean canApprove(BigDecimal amount);
}

// Leaf — individual approver
public class IndividualApprover implements ApprovalNode {
    private final String approverId;
    private final BigDecimal approvalLimit;
    
    @Override
    public ApprovalResult approve(PaymentRequest request) {
        if (request.getAmount().compareTo(approvalLimit) > 0) {
            return ApprovalResult.insufficientLimit(approverId);
        }
        return ApprovalResult.approved(approverId);
    }
    
    @Override
    public boolean canApprove(BigDecimal amount) {
        return amount.compareTo(approvalLimit) <= 0;
    }
}

// Composite — a group that contains other nodes (including sub-groups)
public class ApprovalGroup implements ApprovalNode {
    private final String groupName;
    private final List<ApprovalNode> members = new ArrayList<>();
    private final ApprovalPolicy policy; // ANY_ONE or ALL_REQUIRED
    
    public void addMember(ApprovalNode node) {
        members.add(node);
    }
    
    @Override
    public ApprovalResult approve(PaymentRequest request) {
        if (policy == ApprovalPolicy.ANY_ONE) {
            return members.stream()
                .filter(m -> m.canApprove(request.getAmount()))
                .findFirst()
                .map(m -> m.approve(request))
                .orElse(ApprovalResult.noApproverAvailable(groupName));
        } else { // ALL_REQUIRED
            return members.stream()
                .map(m -> m.approve(request))
                .reduce(ApprovalResult::combine)
                .orElse(ApprovalResult.noApproverAvailable(groupName));
        }
    }
    
    @Override
    public boolean canApprove(BigDecimal amount) {
        return members.stream().anyMatch(m -> m.canApprove(amount));
    }
}

// Building a hierarchy:
// Senior Manager group (any one can approve up to £1M)
ApprovalGroup seniorManagers = new ApprovalGroup("Senior Managers", ANY_ONE);
seniorManagers.addMember(new IndividualApprover("SM001", 
    new BigDecimal("1000000")));
seniorManagers.addMember(new IndividualApprover("SM002", 
    new BigDecimal("1000000")));

// Board group (all required for > £10M)
ApprovalGroup board = new ApprovalGroup("Board", ALL_REQUIRED);
board.addMember(new IndividualApprover("DIR001", 
    new BigDecimal("10000000")));
board.addMember(seniorManagers); // Composite contains composite!

// Caller treats individual and group identically
ApprovalNode approver = getApproverForAmount(request.getAmount());
ApprovalResult result = approver.approve(request);
```

---

## 2. FS Use Cases

| Composite Use Case | Tree Structure |
|---|---|
| **Payment approval hierarchy** | Individual → Team → Senior Manager → Board |
| **Account hierarchy** | Parent company → Subsidiaries → Sub-accounts (Cash pooling) |
| **Portfolio structure** | Portfolio → Asset class → Individual positions |
| **Compliance rule tree** | RuleSet → SubRuleSet → Individual rules |
| **Organisational fee structure** | Relationship → Product group → Individual product fees |

---

# PATTERN 6: Bridge 🌉

## 1. Core Concept + Internals

**Decouple an abstraction from its implementation** so the two can vary independently.

**Where this differs from Adapter:** Adapter makes incompatible things work together (after the fact). Bridge is designed **upfront** to allow independent variation.

```java
// FS Example: Notification system
// Abstraction varies: Payment notification, Fraud alert, Statement
// Implementation varies: Email, SMS, Push, In-app

// Implementation hierarchy
public interface NotificationChannel {
    void send(String recipient, String subject, String body);
}

public class EmailChannel implements NotificationChannel {
    public void send(String recipient, String subject, String body) {
        // SMTP / SendGrid
    }
}

public class SMSChannel implements NotificationChannel {
    public void send(String recipient, String subject, String body) {
        // Twilio / AWS SNS
    }
}

public class PushNotificationChannel implements NotificationChannel {
    public void send(String recipient, String subject, String body) {
        // Firebase FCM
    }
}

// Abstraction hierarchy — BRIDGE holds reference to implementation
public abstract class BankingNotification {
    // Bridge to implementation
    protected final NotificationChannel channel;
    
    protected BankingNotification(NotificationChannel channel) {
        this.channel = channel;
    }
    
    public abstract void notify(Customer customer, Map<String, Object> data);
}

// Refined abstraction — Payment notification (varies independently)
public class PaymentConfirmationNotification extends BankingNotification {
    
    public PaymentConfirmationNotification(NotificationChannel channel) {
        super(channel);
    }
    
    @Override
    public void notify(Customer customer, Map<String, Object> data) {
        String body = String.format(
            "Payment of %s %s to %s has been processed. Ref: %s",
            data.get("currency"), data.get("amount"),
            data.get("beneficiary"), data.get("reference"));
        channel.send(customer.getContactPoint(), 
                     "Payment Confirmation", body);
    }
}

// Fraud alert notification
public class FraudAlertNotification extends BankingNotification {
    @Override
    public void notify(Customer customer, Map<String, Object> data) {
        String body = String.format(
            "URGENT: Suspicious transaction of %s detected on your account. " +
            "If not you, call 0800-XXX-XXX immediately.",
            data.get("amount"));
        // Fraud alerts go via SMS for urgency — 
        // but the abstraction doesn't need to know this
        channel.send(customer.getMobileNumber(), "Fraud Alert", body);
    }
}

// Mix and match freely — 2 abstractions × 3 channels = 6 combinations
// Without Bridge: you'd need 6 subclasses
BankingNotification emailPayment = 
    new PaymentConfirmationNotification(new EmailChannel());
    
BankingNotification smsAlert = 
    new FraudAlertNotification(new SMSChannel());
    
BankingNotification pushStatement = 
    new StatementReadyNotification(new PushNotificationChannel());
```

**Without Bridge:** 2 notification types × 3 channels = 6 classes. Add a WhatsApp channel = 2 more classes. Bridge means you add 1 class instead.

---

## 3. EM-Level Framing

> *"Bridge is an upfront design decision. If I know two dimensions will vary independently — like notification type and delivery channel — I use Bridge to prevent a class explosion. In the Lloyds engagement, we had 5 notification types and 4 channels. Without Bridge, 20 classes. With Bridge, 9. More importantly, when we added a WhatsApp channel for commercial banking clients, it was a single new class — zero changes to existing notification types. That's the Open/Closed Principle in practice."*

---

# ✅ Structural Patterns — All Covered

| Pattern | One-line Purpose | Key FS Use Case |
|---|---|---|
| **Adapter** | Make incompatible interfaces work together | Temenos T24 / SWIFT integration |
| **Facade** | Simplify complex subsystem | Payment orchestration, Account opening |
| **Decorator** | Add behaviour dynamically without subclassing | Audit, Rate limiting, Metrics |
| **Proxy** | Control access to an object | Auth proxy, Lazy load, Remote stub |
| **Composite** | Treat individual + group uniformly | Approval hierarchy, Account hierarchy |
| **Bridge** | Decouple abstraction from implementation | Notification type × channel |

---

# 🎯 DRILL TIME — Pick One

**Drill A — Adapter:**
> *"Your team is integrating with a new KYC provider — Jumio — which has a proprietary Java SDK with its own request/response model. You also have an existing Experian adapter. Walk me through how you design the Adapter layer, what goes in it, and how you ensure the rest of your onboarding service never knows which KYC provider it's talking to."*

**Drill B — Facade:**
> *"A junior engineer on your team says — 'Why do we need a PaymentProcessingFacade? The controller can just call the 6 services directly.' How do you respond? What production problems has this approach caused and what are the testability implications?"*

**Drill C — Decorator:**
> *"You need to add OFAC sanctions screening to every outbound payment. You have 3 existing payment processors — CHAPS, BACS, Faster Payments. Walk me through using Decorator to add this without modifying any of the three processors. What's the alternative and why is it worse?"*

**Drill D — Proxy:**
> *"Explain why Spring's `@Transactional` silently fails when placed on a private method. What is the underlying proxy mechanism? How have you caught this in a code review?"*

**Or say "move on"** → Behavioural Patterns

# 🧠 BEHAVIOURAL PATTERNS — EM-Level Deep Dive

---

## Category Recap — What Behavioural Patterns Are About

> **"How do objects communicate, distribute responsibility, and encapsulate algorithms?"**

If Structural patterns are the **skeleton** — how things connect — Behavioural patterns are the **nervous system** — how signals flow, who responds, and how work gets done.

Three core themes:
1. **Vary algorithms at runtime** — Strategy, Template Method
2. **Decouple sender from receiver** — Observer, Command, Chain of Responsibility, Mediator
3. **Manage state and workflow** — State, Iterator

In FS, behavioural patterns appear most in:
- Payment routing logic
- Fraud scoring pipelines
- Approval workflows
- Audit and event systems
- State machines (payment lifecycle, loan lifecycle)

---

# PATTERN 1: Strategy 🎯

## 1. Core Concept + Internals

**The problem:** You have an algorithm that needs to vary at runtime. The naive solution — a giant `if/else` or `switch` — is brittle, untestable, and violates Open/Closed Principle.

**Strategy:** Define a family of algorithms, encapsulate each one, make them interchangeable.

```java
// FS Example: Payment routing strategy
// Route a payment to the right scheme based on amount, 
// urgency, currency, counterparty

public interface PaymentRoutingStrategy {
    boolean canHandle(PaymentRequest request);
    RouteDecision route(PaymentRequest request);
}

// Strategy 1: CHAPS — same-day high value
public class CHAPSRoutingStrategy implements PaymentRoutingStrategy {
    
    private static final BigDecimal CHAPS_THRESHOLD = 
        new BigDecimal("10000");
    
    @Override
    public boolean canHandle(PaymentRequest request) {
        return request.getCurrency().equals(Currency.getInstance("GBP"))
            && request.getAmount().compareTo(CHAPS_THRESHOLD) >= 0
            && request.isUrgent();
    }
    
    @Override
    public RouteDecision route(PaymentRequest request) {
        return RouteDecision.builder()
            .scheme(PaymentScheme.CHAPS)
            .settlementWindow("Same day, before 17:00")
            .estimatedFee(new BigDecimal("25.00"))
            .build();
    }
}

// Strategy 2: Faster Payments — sub £1M, near-instant
public class FasterPaymentsRoutingStrategy implements PaymentRoutingStrategy {
    
    private static final BigDecimal FPS_LIMIT = 
        new BigDecimal("1000000");
    
    @Override
    public boolean canHandle(PaymentRequest request) {
        return request.getCurrency().equals(Currency.getInstance("GBP"))
            && request.getAmount().compareTo(FPS_LIMIT) < 0;
    }
    
    @Override
    public RouteDecision route(PaymentRequest request) {
        return RouteDecision.builder()
            .scheme(PaymentScheme.FASTER_PAYMENTS)
            .settlementWindow("Seconds")
            .estimatedFee(BigDecimal.ZERO)
            .build();
    }
}

// Strategy 3: SWIFT — cross-border
public class SWIFTRoutingStrategy implements PaymentRoutingStrategy {
    
    @Override
    public boolean canHandle(PaymentRequest request) {
        return !request.getCurrency().equals(Currency.getInstance("GBP"))
            || !isUKAccount(request.getCreditAccountId());
    }
    
    @Override
    public RouteDecision route(PaymentRequest request) {
        return RouteDecision.builder()
            .scheme(PaymentScheme.SWIFT)
            .settlementWindow("1-3 business days")
            .estimatedFee(new BigDecimal("15.00"))
            .build();
    }
}

// Context — holds and executes the strategy
@Service
public class PaymentRouter {
    
    // Injected in priority order — ORDER MATTERS
    private final List<PaymentRoutingStrategy> strategies;
    
    public PaymentRouter(List<PaymentRoutingStrategy> strategies) {
        this.strategies = strategies;
    }
    
    public RouteDecision route(PaymentRequest request) {
        return strategies.stream()
            .filter(s -> s.canHandle(request))
            .findFirst()
            .map(s -> s.route(request))
            .orElseThrow(() -> new NoRoutingStrategyException(
                "No routing strategy found for payment: " 
                + request.getPaymentId()));
    }
}

// Spring wires strategies in order via @Order or @Primary
@Configuration
public class RoutingConfig {
    @Bean
    public PaymentRouter paymentRouter() {
        return new PaymentRouter(List.of(
            new CHAPSRoutingStrategy(),        // Check CHAPS first
            new SWIFTRoutingStrategy(),        // Then cross-border
            new FasterPaymentsRoutingStrategy() // FPS as default
        ));
    }
}
```

---

## 2. FS-Specific Use Cases

| Strategy Use Case | Strategies |
|---|---|
| **Payment routing** | CHAPS / BACS / FPS / SWIFT |
| **Fraud scoring** | Rules-based / ML model / Hybrid |
| **Fee calculation** | Retail / Commercial / Premium / Staff |
| **Interest calculation** | Simple / Compound / Day-count conventions (ACT/365, ACT/360) |
| **FX pricing** | Spot rate / Forward rate / Internal transfer pricing |
| **Statement generation** | PDF / CSV / MT940 / CAMT.053 |
| **Limit checking** | Single transaction / Daily / Velocity / Counterparty |

---

## 3. EM-Level Framing

**Open/Closed Principle connection:**
> *"The payment routing `switch` statement I inherited had 847 lines and 23 conditions. Adding a new payment rail — say, we needed to add SEPA when onboarding an EU subsidiary — meant modifying that method, re-testing all 23 paths, and a high regression risk. With Strategy, adding SEPA is a new class, a new Spring bean, zero modifications to existing code. That's the Open/Closed Principle — open for extension, closed for modification."*

**Strategy vs Chain of Responsibility distinction:**
> *"Strategy selects ONE algorithm to execute. Chain of Responsibility passes a request through MULTIPLE handlers in sequence. Payment routing is Strategy — pick one scheme. Fraud pipeline is Chain of Responsibility — run all checks in sequence. Confusing the two is a common interview mistake."*

**Team decision:**
> *"I enforce Strategy pattern for any business logic with more than 3 conditions. The rule in my team: if you're writing a `switch` on a business concept — payment type, customer segment, currency — that's a Strategy waiting to be extracted. Code reviews catch this."*

---

# PATTERN 2: Observer 👁️

## 1. Core Concept + Internals

**The problem:** When one object changes state, others need to know — but you don't want tight coupling between them.

**Observer:** Define a one-to-many dependency. When the subject changes, all registered observers are notified automatically.

```java
// FS Example: Payment state changes trigger multiple downstream actions
// When a payment is submitted — notify fraud, notify audit, 
// notify customer, update analytics

// Event (what changed)
public record PaymentSubmittedEvent(
    String paymentId,
    String accountId,
    BigDecimal amount,
    Currency currency,
    PaymentScheme scheme,
    Instant occurredAt
) implements DomainEvent {}

// Observer interface
public interface PaymentEventObserver {
    void onPaymentSubmitted(PaymentSubmittedEvent event);
}

// Observer 1: Fraud monitoring
@Component
public class FraudMonitoringObserver implements PaymentEventObserver {
    private final FraudDetectionService fraudService;
    
    @Override
    public void onPaymentSubmitted(PaymentSubmittedEvent event) {
        // Async — don't block payment flow
        fraudService.assessAsync(FraudAssessmentRequest.from(event));
    }
}

// Observer 2: Regulatory reporting
@Component
public class RegulatoryReportingObserver implements PaymentEventObserver {
    private final SARReportingService sarService;
    
    @Override
    public void onPaymentSubmitted(PaymentSubmittedEvent event) {
        // Check if SAR (Suspicious Activity Report) threshold breached
        // FCA requirement — POCA 2002
        if (event.amount().compareTo(new BigDecimal("10000")) > 0) {
            sarService.evaluateForReporting(event);
        }
    }
}

// Observer 3: Customer notification
@Component
public class CustomerNotificationObserver implements PaymentEventObserver {
    private final NotificationService notificationService;
    
    @Override
    public void onPaymentSubmitted(PaymentSubmittedEvent event) {
        notificationService.sendPaymentConfirmation(
            event.accountId(), event.paymentId());
    }
}

// Subject — the Payment Service
@Service
public class PaymentService {
    
    private final List<PaymentEventObserver> observers;
    private final PaymentRepository repository;
    
    // Spring injects all PaymentEventObserver beans automatically
    public PaymentService(List<PaymentEventObserver> observers,
                          PaymentRepository repository) {
        this.observers = observers;
        this.repository = repository;
    }
    
    public PaymentResult submitPayment(PaymentRequest request) {
        // Core logic
        Payment payment = Payment.from(request);
        repository.save(payment);
        
        // Notify all observers — PaymentService doesn't 
        // know who's listening
        PaymentSubmittedEvent event = new PaymentSubmittedEvent(
            payment.getId(), request.getDebitAccountId(),
            request.getAmount(), request.getCurrency(),
            payment.getScheme(), Instant.now());
            
        observers.forEach(o -> o.onPaymentSubmitted(event));
        
        return PaymentResult.submitted(payment.getId());
    }
}
```

**In Spring — this is `ApplicationEventPublisher`:**
```java
// Spring's built-in Observer mechanism
@Service
public class PaymentService {
    private final ApplicationEventPublisher eventPublisher;
    
    public PaymentResult submitPayment(PaymentRequest request) {
        // ... core logic ...
        eventPublisher.publishEvent(new PaymentSubmittedEvent(...));
        return PaymentResult.submitted(payment.getId());
    }
}

@EventListener
@Async  // Non-blocking
public void handlePaymentSubmitted(PaymentSubmittedEvent event) {
    fraudService.assessAsync(event);
}
```

---

## 2. FS Use Cases

| Observer Use Case | Observers Reacting |
|---|---|
| **Payment submitted** | Fraud, Audit, Notification, Analytics, Regulatory |
| **Account balance threshold** | Overdraft alert, Fee trigger, Relationship manager alert |
| **Trade executed** | Position update, P&L calc, Risk limit check, Settlement instruction |
| **Suspicious activity flagged** | Case management, Compliance team alert, SAR queue |
| **Card declined** | Fraud review, Customer notification, CBS update |

---

## 3. EM-Level Framing

**Observer vs Kafka — the critical EM distinction:**

> *"In-process Observer (Spring ApplicationEvents) is for things that must happen in the same transaction context — or where you need immediate, synchronous notification within a single service. Kafka is Observer at the distributed systems level — for cross-service events with durability guarantees. I use in-process Observer for domain events within a bounded context, and Kafka for events that cross service boundaries. Mixing them up — putting Kafka where in-process works, or using in-process where you need durability — is a common architectural mistake."*

**The ordering problem:**
> *"One production issue I've seen: Observer notifications fired BEFORE the database commit completed. The fraud service received the event, queried the payment, and got a 404. Fix: use Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)` — fires only after the transaction commits successfully."*

---

# PATTERN 3: Chain of Responsibility ⛓️

## 1. Core Concept + Internals

**The problem:** Multiple objects may handle a request. You don't want the sender to know which one handles it, and you want to decouple sender from receivers.

**Chain:** Pass the request along a chain of handlers. Each handler decides — process it, pass it on, or stop the chain.

```java
// FS Example: Payment validation pipeline
// Each validator handles one concern — chain them

public abstract class PaymentValidationHandler {
    
    private PaymentValidationHandler next;
    
    public PaymentValidationHandler setNext(PaymentValidationHandler next) {
        this.next = next;
        return next; // Fluent — enables chaining
    }
    
    // Template method — subclasses implement validate()
    public final ValidationResult handle(PaymentRequest request) {
        ValidationResult result = validate(request);
        
        if (!result.isValid()) {
            return result; // Stop chain on failure
        }
        
        if (next != null) {
            return next.handle(request); // Pass to next
        }
        
        return ValidationResult.valid(); // End of chain — all passed
    }
    
    protected abstract ValidationResult validate(PaymentRequest request);
}

// Handler 1: Schema validation
public class SchemaValidationHandler extends PaymentValidationHandler {
    @Override
    protected ValidationResult validate(PaymentRequest request) {
        if (request.getPaymentId() == null || 
            request.getAmount() == null) {
            return ValidationResult.invalid(
                "SCHEMA_ERROR", "Mandatory fields missing");
        }
        return ValidationResult.valid();
    }
}

// Handler 2: Account existence check
public class AccountValidationHandler extends PaymentValidationHandler {
    private final AccountService accountService;
    
    @Override
    protected ValidationResult validate(PaymentRequest request) {
        if (!accountService.exists(request.getDebitAccountId())) {
            return ValidationResult.invalid(
                "ACCOUNT_NOT_FOUND", 
                "Debit account not found: " + request.getDebitAccountId());
        }
        return ValidationResult.valid();
    }
}

// Handler 3: Balance check
public class BalanceValidationHandler extends PaymentValidationHandler {
    private final BalanceService balanceService;
    
    @Override
    protected ValidationResult validate(PaymentRequest request) {
        BigDecimal available = balanceService
            .getAvailableBalance(request.getDebitAccountId());
        if (available.compareTo(request.getAmount()) < 0) {
            return ValidationResult.invalid(
                "INSUFFICIENT_FUNDS",
                String.format("Available: %s, Required: %s", 
                    available, request.getAmount()));
        }
        return ValidationResult.valid();
    }
}

// Handler 4: Sanctions screening
public class SanctionsValidationHandler extends PaymentValidationHandler {
    private final SanctionsScreeningService sanctionsService;
    
    @Override
    protected ValidationResult validate(PaymentRequest request) {
        // OFAC / HMT / EU sanctions lists
        SanctionsResult result = sanctionsService
            .screen(request.getCreditAccountId(), 
                    request.getBeneficiaryName());
        if (result.isMatch()) {
            return ValidationResult.invalid(
                "SANCTIONS_HIT",
                "Beneficiary matches sanctions list: " + result.getListName());
        }
        return ValidationResult.valid();
    }
}

// Handler 5: Payment limit check
public class LimitValidationHandler extends PaymentValidationHandler {
    private final LimitService limitService;
    
    @Override
    protected ValidationResult validate(PaymentRequest request) {
        LimitCheckResult limit = limitService.check(
            request.getDebitAccountId(),
            request.getAmount(),
            request.getPaymentType());
        if (!limit.isWithinLimit()) {
            return ValidationResult.invalid(
                "LIMIT_EXCEEDED",
                "Daily limit exceeded. Remaining: " + limit.getRemaining());
        }
        return ValidationResult.valid();
    }
}

// Building and using the chain
@Configuration
public class ValidationChainConfig {
    
    @Bean
    public PaymentValidationHandler validationChain(
            AccountValidationHandler accountHandler,
            BalanceValidationHandler balanceHandler,
            SanctionsValidationHandler sanctionsHandler,
            LimitValidationHandler limitHandler) {
        
        // Order matters — fail fast on cheapest checks first
        PaymentValidationHandler chain = new SchemaValidationHandler();
        chain.setNext(accountHandler)
             .setNext(balanceHandler)
             .setNext(sanctionsHandler)  // DB call — after cheap checks
             .setNext(limitHandler);     // Expensive last
        
        return chain;
    }
}

// Usage
ValidationResult result = validationChain.handle(paymentRequest);
```

---

## 2. FS Use Cases

| Chain Use Case | Handlers in Chain |
|---|---|
| **Payment validation** | Schema → Account → Balance → Sanctions → Limits |
| **Loan approval** | Credit score → Income verification → LTV → Policy rules → Manual review |
| **Trade pre-trade checks** | Position limit → Counterparty limit → Market hours → Fat finger |
| **AML transaction monitoring** | Velocity → Pattern → Geography → Structuring → Watchlist |
| **Onboarding** | Identity → PEP check → Sanctions → Credit → Affordability |

---

## 3. EM-Level Framing

**Ordering strategy — EM must think about this:**
> *"In a payment validation chain, I order handlers by: cheapest first, most expensive last. Schema validation is in-memory — microseconds. Balance check hits the CBS — milliseconds. Sanctions screening hits an external provider — 50-200ms. If schema is invalid, you never pay for the CBS call. This isn't just performance — it's cost management. At Lloyds scale, 50 million payments a month, every unnecessary external call is real money."*

**Chain vs Strategy again:**
> *"Chain runs ALL handlers in sequence until one fails or the chain ends. Strategy picks ONE handler. Payment validation is Chain — you want ALL checks to run in order. Payment routing is Strategy — you want ONE scheme. Getting this wrong architecturally means either skipping validations or running routing logic multiple times."*

**Servlet Filter Chain — the everyday Chain of Responsibility:**
> *"Every Spring Boot developer uses Chain of Responsibility daily — the Servlet Filter chain. Authentication filter, CORS filter, logging filter, rate limiting filter — each handles its concern and passes to the next. Spring Security's filter chain is exactly this pattern. When I explain Chain of Responsibility to my team, I start there because they already know it — they just don't realise it."*

---

# PATTERN 4: Command 📋

## 1. Core Concept + Internals

**Encapsulate a request as an object.** This gives you: queueing, logging, undoable operations, and macro commands.

```java
// FS Example: Payment instruction as a Command
// Enables: audit trail, retry, undo (reversal), 
//          async queuing, approval workflow

public interface PaymentCommand {
    PaymentResult execute();
    PaymentResult undo();       // Reversal
    String getCommandId();
    Instant getCreatedAt();
    String getInitiatedBy();
}

// Concrete Command
public class TransferFundsCommand implements PaymentCommand {
    
    private final String commandId;
    private final String debitAccountId;
    private final String creditAccountId;
    private final BigDecimal amount;
    private final Currency currency;
    private final String initiatedBy;
    private final Instant createdAt;
    
    // The receiver — does the actual work
    private final FundsTransferService transferService;
    
    @Override
    public PaymentResult execute() {
        return transferService.transfer(
            debitAccountId, creditAccountId, amount, currency);
    }
    
    @Override
    public PaymentResult undo() {
        // Reversal — swap debit/credit
        return transferService.transfer(
            creditAccountId, debitAccountId, amount, currency);
    }
    
    @Override
    public String getCommandId() { return commandId; }
    
    // ... other getters
}

// Command Queue — for async processing + audit
@Service
public class PaymentCommandQueue {
    
    private final Queue<PaymentCommand> pendingCommands = 
        new ConcurrentLinkedQueue<>();
    private final List<PaymentCommand> executedCommands = 
        new CopyOnWriteArrayList<>();
    private final AuditRepository auditRepository;
    
    public String enqueue(PaymentCommand command) {
        pendingCommands.offer(command);
        auditRepository.recordCommandReceived(command);
        return command.getCommandId();
    }
    
    public PaymentResult executeNext() {
        PaymentCommand command = pendingCommands.poll();
        if (command == null) return null;
        
        PaymentResult result = command.execute();
        executedCommands.add(command);
        auditRepository.recordCommandExecuted(command, result);
        return result;
    }
    
    public PaymentResult reverse(String commandId) {
        return executedCommands.stream()
            .filter(c -> c.getCommandId().equals(commandId))
            .findFirst()
            .map(PaymentCommand::undo)
            .orElseThrow(() -> new CommandNotFoundException(commandId));
    }
}
```

---

## 2. FS Use Cases

| Command Use Case | Why Command |
|---|---|
| **Payment instruction** | Audit trail, approval workflow, retry, reversal |
| **Trade order** | Queue for market hours, amend/cancel/undo |
| **Batch payment file** | BACS bulk — queue, validate, execute, rollback |
| **Config change** | Maker-checker — command queued, second approver executes |
| **Limit update** | Audit who changed what limit, when, rollback if error |

---

## 3. EM-Level Framing

> *"Command pattern is the foundation of maker-checker workflows — a regulatory requirement in commercial banking for payments above a threshold. The initiator creates the command object — encapsulating all payment details and their identity. The approver executes it. The command object IS the audit trail. This satisfies FCA's requirement for dual control on high-value payments and gives you a complete, immutable record of who instructed what and when."*

---

# PATTERN 5: State 🔄

## 1. Core Concept + Internals

**Allow an object to alter its behaviour when its internal state changes.** The object will appear to change its class.

```java
// FS Example: Payment lifecycle state machine
// INITIATED → VALIDATED → FRAUD_CLEARED → SUBMITTED → SETTLED
//                       ↘ REJECTED      ↘ FAILED

public interface PaymentState {
    void validate(PaymentContext context);
    void clearFraud(PaymentContext context);
    void submit(PaymentContext context);
    void settle(PaymentContext context);
    void reject(PaymentContext context, String reason);
    String getStateName();
}

// Context — holds current state
public class PaymentContext {
    private PaymentState currentState;
    private final String paymentId;
    private final List<StateTransition> history = new ArrayList<>();
    
    public PaymentContext(String paymentId) {
        this.paymentId = paymentId;
        this.currentState = new InitiatedState(); // Initial state
    }
    
    public void setState(PaymentState state) {
        history.add(new StateTransition(
            currentState.getStateName(),
            state.getStateName(),
            Instant.now()));
        this.currentState = state;
    }
    
    // Delegate to current state
    public void validate() { currentState.validate(this); }
    public void clearFraud() { currentState.clearFraud(this); }
    public void submit() { currentState.submit(this); }
    public void settle() { currentState.settle(this); }
    public void reject(String reason) { 
        currentState.reject(this, reason); 
    }
    
    public String getCurrentState() { 
        return currentState.getStateName(); 
    }
}

// Concrete State: INITIATED
public class InitiatedState implements PaymentState {
    
    @Override
    public void validate(PaymentContext context) {
        // Valid transition
        System.out.println("Validating payment " + context.getPaymentId());
        context.setState(new ValidatedState());
    }
    
    @Override
    public void submit(PaymentContext context) {
        // Invalid transition — can't submit without validating
        throw new InvalidStateTransitionException(
            "Cannot submit payment in INITIATED state. " +
            "Must validate first.");
    }
    
    @Override
    public void settle(PaymentContext context) {
        throw new InvalidStateTransitionException(
            "Cannot settle payment in INITIATED state.");
    }
    
    // ... other invalid transitions throw exceptions
    
    @Override
    public String getStateName() { return "INITIATED"; }
}

// Concrete State: VALIDATED
public class ValidatedState implements PaymentState {
    
    @Override
    public void clearFraud(PaymentContext context) {
        context.setState(new FraudClearedState());
    }
    
    @Override
    public void reject(PaymentContext context, String reason) {
        context.setState(new RejectedState(reason));
    }
    
    @Override
    public void validate(PaymentContext context) {
        throw new InvalidStateTransitionException(
            "Payment already validated.");
    }
    
    @Override
    public String getStateName() { return "VALIDATED"; }
}
```

---

## 2. FS Use Cases

| State Machine | States |
|---|---|
| **Payment lifecycle** | INITIATED → VALIDATED → FRAUD_CLEARED → SUBMITTED → SETTLED / FAILED |
| **Loan application** | DRAFT → SUBMITTED → UNDERWRITING → APPROVED → DISBURSED / DECLINED |
| **Card lifecycle** | ISSUED → ACTIVE → BLOCKED → CANCELLED |
| **Trade lifecycle** | NEW → PARTIALLY_FILLED → FILLED → SETTLED / CANCELLED |
| **Account opening** | APPLIED → KYC_PENDING → KYC_PASSED → ACTIVE / REJECTED |

---

## 3. EM-Level Framing

> *"State pattern enforces valid transitions at compile time — invalid transitions throw exceptions, not silent bugs. In a payment system, allowing a payment to go from INITIATED directly to SETTLED without fraud screening is a compliance failure. With State pattern, that transition literally cannot happen — the code won't allow it. Compare that to an `enum` + `switch` approach where a developer could accidentally add a case and bypass fraud screening. State pattern makes invalid states unrepresentable."*

**Spring State Machine:**
> *"For complex state machines in production — payment lifecycle, loan workflow — I've used Spring State Machine. It gives you state persistence, event-driven transitions, guards, actions, and history. The overhead is worth it for workflows with 8+ states and audit requirements. For simpler cases — 3-4 states — explicit State pattern classes are cleaner and more testable."*

---

# PATTERN 6: Template Method 📐

## 1. Core Concept + Internals

**Define the skeleton of an algorithm in a base class. Defer specific steps to subclasses.**

```java
// FS Example: Payment processing has a fixed skeleton
// but specific steps vary by payment scheme

public abstract class PaymentProcessingTemplate {
    
    // THE TEMPLATE METHOD — final, not overridable
    public final PaymentResult processPayment(PaymentRequest request) {
        validateRequest(request);           // Step 1 — common
        PaymentInstruction instruction = 
            buildInstruction(request);      // Step 2 — varies
        enrichWithSchemeData(instruction);  // Step 3 — varies
        SchemeResponse response = 
            submitToScheme(instruction);    // Step 4 — varies
        PaymentResult result = 
            buildResult(response);          // Step 5 — common
        auditPayment(request, result);      // Step 6 — common
        return result;
    }
    
    // Common steps — implemented in base class
    private void validateRequest(PaymentRequest request) {
        // Common validation for all schemes
    }
    
    private PaymentResult buildResult(SchemeResponse response) {
        // Common result mapping
        return PaymentResult.from(response);
    }
    
    private void auditPayment(PaymentRequest request, 
                               PaymentResult result) {
        // FCA-mandated audit — same for all schemes
    }
    
    // Hook methods — subclasses override these
    protected abstract PaymentInstruction buildInstruction(
        PaymentRequest request);
    
    protected abstract void enrichWithSchemeData(
        PaymentInstruction instruction);
    
    protected abstract SchemeResponse submitToScheme(
        PaymentInstruction instruction);
}

// CHAPS-specific implementation
public class CHAPSPaymentProcessor extends PaymentProcessingTemplate {
    
    @Override
    protected PaymentInstruction buildInstruction(PaymentRequest request) {
        return CHAPSInstruction.builder()
            .sortCode(extractSortCode(request.getCreditAccountId()))
            .accountNumber(extractAccountNumber(request.getCreditAccountId()))
            .amount(request.getAmount())
            .reference(request.getEndToEndReference())
            .build();
    }
    
    @Override
    protected void enrichWithSchemeData(PaymentInstruction instruction) {
        // CHAPS-specific: add settlement cycle, RTGS reference
        CHAPSInstruction chaps = (CHAPSInstruction) instruction;
        chaps.setSettlementCycle(getCurrentCHAPSCycle());
    }
    
    @Override
    protected SchemeResponse submitToScheme(PaymentInstruction instruction) {
        return chapsGateway.submit((CHAPSInstruction) instruction);
    }
}
```

---

## 2. EM-Level Framing

**Template Method vs Strategy — the important distinction:**

> *"Template Method uses inheritance — the algorithm skeleton is fixed in the base class, subclasses fill in steps. Strategy uses composition — the entire algorithm is swappable. Template Method is appropriate when the overall flow is fixed and only specific steps vary — like all payment schemes follow validate → build → enrich → submit → audit. Strategy is appropriate when you need to swap the entire algorithm at runtime. In modern Java, I prefer Strategy + composition over Template Method + inheritance — composition is more flexible and testable. But Template Method is valid when the skeleton is genuinely invariant, like regulatory reporting formats."*

---

# ✅ Behavioural Patterns — All Covered

| Pattern | One-line Purpose | Key FS Use Case |
|---|---|---|
| **Strategy** | Swap algorithms at runtime | Payment routing, Fraud scoring |
| **Observer** | Notify dependents of state change | Payment events → Fraud/Audit/Notify |
| **Chain of Responsibility** | Pass request through handler chain | Payment validation pipeline |
| **Command** | Encapsulate request as object | Maker-checker, Audit trail, Reversal |
| **State** | Behaviour changes with state | Payment/Loan/Trade lifecycle |
| **Template Method** | Fix skeleton, vary steps | Payment scheme processing |
| **Mediator** | Centralise object communication | (Less critical for EM — covered if needed) |

---

# 🎯 DRILL TIME — Pick One

**Drill A — Strategy:**
> *"You're reviewing a PR. A developer has written a 200-line method with a `switch` on `PaymentType` covering CHAPS, BACS, FPS, SWIFT — and a comment saying 'add new case here for new rails'. What's your code review feedback, what pattern do you recommend, and how does it connect to Open/Closed Principle?"*

**Drill B — Chain of Responsibility:**
> *"Design a payment validation chain for outbound international payments at Lloyds. What handlers do you include, in what order, and why? Name at least one regulatory requirement each handler satisfies."*

**Drill C — State:**
> *"A payment in your system somehow moved from INITIATED directly to SETTLED, bypassing fraud screening. Walk me through: how did this happen architecturally, how does State pattern prevent it, and what regulatory exposure did this create?"*

**Drill D — Observer:**
> *"Your PaymentService fires domain events using Spring's ApplicationEventPublisher. A junior engineer asks: why not just call FraudService, AuditService, and NotificationService directly from PaymentService? Walk them through your reasoning — coupling, testing, and what happens when a new observer needs to be added."*

**Or say "move on"** → Enterprise / FS Patterns (Repository, Value Object, Aggregate Root, Specification)

___
##Say i have a use case where based on a customer segment my notification template and handling of notifcation changes ...is it strategy
**Yes — and that's a textbook Strategy use case. Let me show you exactly how.**

---

# Strategy Pattern — Customer Segment Notification

The varying algorithm here is: **"how do I handle a notification for this customer?"**

Both the **template** and the **behaviour** change based on segment — that's exactly what Strategy encapsulates.

---

## The Wrong Way First — what junior devs write

```java
public void sendPaymentNotification(Customer customer, 
                                     PaymentResult payment) {
    if (customer.getSegment() == RETAIL) {
        String template = "Hi {name}, your payment of {amount} was sent.";
        smsService.send(customer.getMobile(), 
                        fillTemplate(template, payment));
                        
    } else if (customer.getSegment() == PREMIUM) {
        String template = "Dear {name}, we confirm transfer of " +
                          "{amount} to {beneficiary}. Ref: {ref}";
        emailService.sendFormatted(customer.getEmail(), 
                                    fillTemplate(template, payment));
        relationshipManagerService.alert(customer.getRmId(), payment);
        
    } else if (customer.getSegment() == COMMERCIAL) {
        String template = "[COMMERCIAL BANKING]\n" +
                          "Payment Ref: {ref}\n" +
                          "Amount: {amount}\n" +
                          "Beneficiary: {beneficiary}\n" +
                          "Value Date: {valueDate}";
        emailService.sendFormatted(customer.getEmail(), 
                                    fillTemplate(template, payment));
        apiWebhookService.post(customer.getWebhookUrl(), payment);
        auditService.logCommercialPayment(payment);  // FCA requirement
        
    } else if (customer.getSegment() == PRIVATE_BANKING) {
        // ... more code
    }
}
```

**Problems with this:**
- Add a new segment → modify this method → regression risk
- Impossible to unit test one segment in isolation
- Template strings buried in logic
- Multiple responsibilities in one method
- Violates Open/Closed Principle

---

## The Right Way — Strategy

```java
// Strategy interface — the varying algorithm
public interface NotificationStrategy {
    
    boolean supports(CustomerSegment segment);  // Which segment owns this
    
    void notify(Customer customer, 
                NotificationEvent event);
    
    String buildMessage(Customer customer, 
                        NotificationEvent event); // Template logic
}

// ── RETAIL ──────────────────────────────────────────────
public class RetailNotificationStrategy implements NotificationStrategy {

    private final SMSService smsService;

    @Override
    public boolean supports(CustomerSegment segment) {
        return segment == CustomerSegment.RETAIL;
    }

    @Override
    public void notify(Customer customer, NotificationEvent event) {
        String message = buildMessage(customer, event);
        smsService.send(customer.getMobileNumber(), message);
        // Retail: SMS only, no email, no RM alert
    }

    @Override
    public String buildMessage(Customer customer, NotificationEvent event) {
        return String.format(
            "Hi %s, your payment of %s %s to %s is on its way. Ref: %s",
            customer.getFirstName(),
            event.getCurrency(),
            event.getAmount(),
            event.getBeneficiaryName(),
            event.getPaymentReference()
        );
        // Short, casual, mobile-friendly
    }
}

// ── PREMIUM ─────────────────────────────────────────────
public class PremiumNotificationStrategy implements NotificationStrategy {

    private final EmailService emailService;
    private final RelationshipManagerService rmService;

    @Override
    public boolean supports(CustomerSegment segment) {
        return segment == CustomerSegment.PREMIUM;
    }

    @Override
    public void notify(Customer customer, NotificationEvent event) {
        // Premium: formal email + RM gets a heads up
        String message = buildMessage(customer, event);
        emailService.sendFormatted(
            customer.getEmailAddress(),
            "Payment Confirmation — " + event.getPaymentReference(),
            message
        );
        // RM alert for payments above £50,000
        if (event.getAmount().compareTo(new BigDecimal("50000")) > 0) {
            rmService.alertRelationshipManager(
                customer.getRmId(), event);
        }
    }

    @Override
    public String buildMessage(Customer customer, NotificationEvent event) {
        return String.format(
            "Dear %s %s,\n\n" +
            "We confirm your payment of %s %s to %s has been processed.\n" +
            "Payment Reference: %s\n" +
            "Value Date: %s\n\n" +
            "If you have any queries, please contact your " +
            "relationship manager.",
            customer.getTitle(),
            customer.getLastName(),
            event.getCurrency(),
            event.getAmount(),
            event.getBeneficiaryName(),
            event.getPaymentReference(),
            event.getValueDate()
        );
        // Formal tone, full details, title + surname
    }
}

// ── COMMERCIAL ──────────────────────────────────────────
public class CommercialNotificationStrategy implements NotificationStrategy {

    private final EmailService emailService;
    private final WebhookService webhookService;
    private final AuditService auditService;

    @Override
    public boolean supports(CustomerSegment segment) {
        return segment == CustomerSegment.COMMERCIAL;
    }

    @Override
    public void notify(Customer customer, NotificationEvent event) {
        // Commercial: structured email + API webhook + FCA audit log
        emailService.sendFormatted(
            customer.getEmailAddress(),
            "Payment Notification",
            buildMessage(customer, event)
        );
        // Commercial clients get machine-readable webhook
        // for their ERP / treasury system
        if (customer.hasWebhookConfigured()) {
            webhookService.post(
                customer.getWebhookUrl(),
                PaymentWebhookPayload.from(event)
            );
        }
        // FCA requirement: commercial payment audit trail
        // CASS rules for client money payments
        auditService.logCommercialPaymentNotification(
            customer.getId(), event);
    }

    @Override
    public String buildMessage(Customer customer, NotificationEvent event) {
        return String.format(
            "[COMMERCIAL BANKING — PAYMENT NOTIFICATION]\n" +
            "Company: %s\n" +
            "Payment Reference: %s\n" +
            "Amount: %s %s\n" +
            "Beneficiary: %s\n" +
            "Beneficiary Account: %s\n" +
            "Value Date: %s\n" +
            "Scheme: %s\n" +
            "Initiated By: %s",
            customer.getCompanyName(),
            event.getPaymentReference(),
            event.getCurrency(), event.getAmount(),
            event.getBeneficiaryName(),
            event.getBeneficiaryAccount(),
            event.getValueDate(),
            event.getPaymentScheme(),
            event.getInitiatedBy()
        );
        // Structured, machine-parseable, full audit fields
    }
}

// ── PRIVATE BANKING ─────────────────────────────────────
public class PrivateBankingNotificationStrategy 
        implements NotificationStrategy {

    private final EmailService emailService;
    private final PushNotificationService pushService;
    private final RelationshipManagerService rmService;

    @Override
    public boolean supports(CustomerSegment segment) {
        return segment == CustomerSegment.PRIVATE_BANKING;
    }

    @Override
    public void notify(Customer customer, NotificationEvent event) {
        // Private banking: push + email + RM always notified
        pushService.send(customer.getDeviceToken(),
            "Payment Confirmed", buildMessage(customer, event));
        emailService.sendFormatted(
            customer.getEmailAddress(),
            "Private Banking — Payment Confirmation",
            buildMessage(customer, event)
        );
        // RM always gets notified for Private Banking — no threshold
        rmService.alertRelationshipManager(customer.getRmId(), event);
    }

    @Override
    public String buildMessage(Customer customer, NotificationEvent event) {
        return String.format(
            "Dear %s,\n\n" +
            "Your instruction to transfer %s %s to %s " +
            "has been executed.\n\n" +
            "Your dedicated team remains available should " +
            "you require assistance.",
            customer.getPreferredSalutation(),  // Very personalised
            event.getCurrency(),
            event.getAmount(),
            event.getBeneficiaryName()
        );
    }
}
```

---

## The Context — wires it all together

```java
@Service
public class NotificationService {

    // Spring injects ALL NotificationStrategy beans automatically
    private final List<NotificationStrategy> strategies;

    public NotificationService(List<NotificationStrategy> strategies) {
        this.strategies = strategies;
    }

    public void sendPaymentNotification(Customer customer,
                                         NotificationEvent event) {
        strategies.stream()
            .filter(s -> s.supports(customer.getSegment()))
            .findFirst()
            .orElseThrow(() -> new NoStrategyException(
                "No notification strategy for segment: "
                + customer.getSegment()))
            .notify(customer, event);
    }
}
```

---

## Now — Where Strategy Ends and Other Patterns Begin

This is the nuance worth knowing for interviews:

| If the requirement is... | Pattern |
|---|---|
| Template changes, delivery channel changes per segment | **Strategy** ✅ — entire algorithm swaps |
| Same template, but add audit logging for Commercial on top | **Decorator** — add behaviour without changing strategy |
| All segments go through validate → build → send skeleton | **Template Method** — fix skeleton, vary steps |
| New segment triggers notification AND fraud AND RM alert | **Observer** — one event, multiple independent reactions |

**Your exact use case** — template AND handling both change per segment — is **pure Strategy.** The moment you find yourself saying *"for this type, do this completely different thing"* — that's Strategy.

---

# 🎯 Quick Drill

> *"A new segment is introduced — `ULTRA_HIGH_NET_WORTH`. They need: WhatsApp message + encrypted email + RM phone call trigger + a PDF payment advice generated and emailed. How do you add this to your existing Strategy-based notification system? How many existing classes do you modify?"*

Answer in 3-4 sentences — I'll score you. Or say **move on**.

##i need to check customer eligbility and i have differnt eligibilituy checks which are incompatible like kapU18, segments only xyz, under ring fenced bank ...is it a chain of responsibility or decorator pattern !! at same time i have a corase grained accounts API which does filteration based on three dimensions, current business context, accountId, productTypes and also it does data encirchement basd on busienss context for ex: if business context is servicing add existing servicing isntrucitons vs if business context is view then do not !! what patterns are fit here..

Great pair of questions. Let me answer both precisely — because the pattern choice here has real architectural consequences.

---

# Question 1: Eligibility Checks — Chain or Decorator?

## The Answer: **Chain of Responsibility**

But let me show you **exactly why** — because the reasoning matters more than the answer.

---

## The Deciding Question

Ask yourself:

> *"Are these checks **additive enhancements** to the same object — or are they **independent gates** that can individually stop the flow?"*

| Characteristic | Decorator | Chain of Responsibility |
|---|---|---|
| Purpose | **Add behaviour** to an object | **Handle/reject** a request |
| Each handler... | Wraps and enhances | Decides pass/fail/stop |
| All handlers run? | **Yes — always** | **No — stops on failure** |
| Order matters for... | Layering behaviour | Fail-fast optimisation |
| Result of chain | Enhanced object | Pass or Reject decision |

---

**Your eligibility checks:**
- `isUnder18` → **hard block** — stop immediately
- `isSegmentEligible` → **hard block** — stop if wrong segment
- `isUnderRingFencedBank` → **hard block** — stop if regulatory constraint

These are **gates, not enhancements.** One failure = stop. That's Chain of Responsibility.

---

## The Code

```java
// The request travelling through the chain
public class EligibilityRequest {
    private final Customer customer;
    private final Product product;
    private final String businessContext;
    // immutable — handlers read, never modify
}

// Result — rich, not just boolean
public class EligibilityResult {
    private final boolean eligible;
    private final String failureCode;
    private final String failureReason;
    private final String failedHandler;

    public static EligibilityResult eligible() {
        return new EligibilityResult(true, null, null, null);
    }

    public static EligibilityResult ineligible(
            String code, String reason, String handler) {
        return new EligibilityResult(false, code, reason, handler);
    }
}

// Abstract handler
public abstract class EligibilityHandler {

    private EligibilityHandler next;

    public EligibilityHandler setNext(EligibilityHandler next) {
        this.next = next;
        return next;
    }

    // Template method — final skeleton
    public final EligibilityResult check(EligibilityRequest request) {
        EligibilityResult result = evaluate(request);

        if (!result.isEligible()) {
            return result; // Stop chain — hard block
        }

        if (next != null) {
            return next.check(request); // Pass to next handler
        }

        return EligibilityResult.eligible(); // All checks passed
    }

    protected abstract EligibilityResult evaluate(
        EligibilityRequest request);
}

// ── HANDLER 1: Age check ─────────────────────────────────
public class AgeEligibilityHandler extends EligibilityHandler {

    private static final int MINIMUM_AGE = 18;

    @Override
    protected EligibilityResult evaluate(EligibilityRequest request) {
        int age = request.getCustomer().getAge();

        if (age < MINIMUM_AGE) {
            return EligibilityResult.ineligible(
                "UNDER_AGE",
                String.format("Customer age %d below minimum %d",
                    age, MINIMUM_AGE),
                "AgeEligibilityHandler"
            );
        }
        return EligibilityResult.eligible();
    }
}

// ── HANDLER 2: Segment check ─────────────────────────────
public class SegmentEligibilityHandler extends EligibilityHandler {

    // Eligible segments injected — configurable per product
    private final Set<CustomerSegment> eligibleSegments;

    public SegmentEligibilityHandler(
            Set<CustomerSegment> eligibleSegments) {
        this.eligibleSegments = eligibleSegments;
    }

    @Override
    protected EligibilityResult evaluate(EligibilityRequest request) {
        CustomerSegment segment = request.getCustomer().getSegment();

        if (!eligibleSegments.contains(segment)) {
            return EligibilityResult.ineligible(
                "SEGMENT_INELIGIBLE",
                String.format(
                    "Segment %s not eligible. Eligible segments: %s",
                    segment, eligibleSegments),
                "SegmentEligibilityHandler"
            );
        }
        return EligibilityResult.eligible();
    }
}

// ── HANDLER 3: Ring-fenced bank check ───────────────────
// UK regulatory context — ring-fencing under FSMA 2000
// Lloyds ring-fenced bank (LBG) cannot offer certain 
// products to large corporates
public class RingFencedBankEligibilityHandler 
        extends EligibilityHandler {

    private final RingFencingRulesService ringFencingService;

    @Override
    protected EligibilityResult evaluate(EligibilityRequest request) {
        Customer customer = request.getCustomer();
        Product product = request.getProduct();

        // Ring-fenced banks cannot serve large corporates
        // for certain investment/complex products
        // PRA rulebook — Ring-fenced Bodies 
        if (ringFencingService.isProhibited(customer, product)) {
            return EligibilityResult.ineligible(
                "RING_FENCE_RESTRICTION",
                String.format(
                    "Product %s cannot be offered to customer " +
                    "type %s under ring-fencing rules (FSMA 2000 s142A)",
                    product.getProductCode(),
                    customer.getEntityType()),
                "RingFencedBankEligibilityHandler"
            );
        }
        return EligibilityResult.eligible();
    }
}

// ── HANDLER 4: Product-specific eligibility ──────────────
public class ProductEligibilityHandler extends EligibilityHandler {

    private final ProductEligibilityRepository productRepo;

    @Override
    protected EligibilityResult evaluate(EligibilityRequest request) {
        ProductEligibilityRules rules = productRepo
            .getRules(request.getProduct().getProductCode());

        if (!rules.isSatisfiedBy(request.getCustomer())) {
            return EligibilityResult.ineligible(
                "PRODUCT_RULES_FAILED",
                rules.getFailureReason(request.getCustomer()),
                "ProductEligibilityHandler"
            );
        }
        return EligibilityResult.eligible();
    }
}

// ── WIRING ───────────────────────────────────────────────
@Configuration
public class EligibilityChainConfig {

    @Bean
    public EligibilityHandler eligibilityChain(
            RingFencingRulesService ringFencingService,
            ProductEligibilityRepository productRepo) {

        // Order: cheapest → most expensive
        // Age: in-memory — nanoseconds
        // Segment: in-memory — nanoseconds
        // Ring-fence: business rules — microseconds
        // Product rules: DB lookup — milliseconds (last)

        EligibilityHandler chain = new AgeEligibilityHandler();
        chain.setNext(new SegmentEligibilityHandler(
                    Set.of(RETAIL, PREMIUM, COMMERCIAL)))
             .setNext(new RingFencedBankEligibilityHandler(
                    ringFencingService))
             .setNext(new ProductEligibilityHandler(productRepo));

        return chain;
    }
}

// ── USAGE ────────────────────────────────────────────────
@Service
public class ProductEligibilityService {

    private final EligibilityHandler eligibilityChain;

    public EligibilityResult checkEligibility(
            Customer customer, Product product,
            String businessContext) {

        EligibilityRequest request = new EligibilityRequest(
            customer, product, businessContext);

        return eligibilityChain.check(request);
    }
}
```

---

## Why Not Decorator Here?

```java
// ❌ WRONG framing with Decorator
// This adds behaviour — it doesn't gate
EligibilityChecker checker =
    new RingFenceDecorator(
        new SegmentDecorator(
            new AgeDecorator(
                new BaseEligibilityChecker())));
```

**The problem:** Decorator runs ALL wrappers — you can't short-circuit. Under-18 customer still goes through ring-fence check, segment check, product check. Wasteful and semantically wrong — you're not *enhancing* the customer object, you're *rejecting* a request.

---

# Question 2: Coarse-Grained Accounts API

## Two Patterns in One API

You've described **two distinct concerns**:

| Concern | What it does | Pattern |
|---|---|---|
| **Filtering** | 3-dimensional filter — context + accountId + productType | **Chain of Responsibility** |
| **Enrichment** | Business-context-specific data added | **Strategy** (or Decorator) |

Let me show both — and then how they compose.

---

## Part A: Filtering — Chain of Responsibility

```java
// The request
public class AccountQueryRequest {
    private final String businessContext;  // SERVICING, VIEW, PAYMENTS
    private final String customerId;
    private final Set<String> accountIds;  // Optional filter
    private final Set<ProductType> productTypes; // Optional filter
}

// The context object that flows through the chain
// Chain progressively FILTERS the account list down
public class AccountFilterContext {
    private List<Account> accounts; // Starts full, gets filtered
    private final AccountQueryRequest request;

    public void filterTo(List<Account> filtered) {
        this.accounts = filtered;
    }

    public List<Account> getAccounts() { return accounts; }
}

// Abstract filter handler
public abstract class AccountFilterHandler {
    private AccountFilterHandler next;

    public AccountFilterHandler setNext(AccountFilterHandler next) {
        this.next = next;
        return next;
    }

    public final void filter(AccountFilterContext context) {
        applyFilter(context);         // Each handler filters
        if (next != null) {
            next.filter(context);     // Always passes on — all filters run
        }
    }

    protected abstract void applyFilter(AccountFilterContext context);
}

// ── FILTER 1: Business context filter ────────────────────
// SERVICING context: only show accounts in servicing state
// PAYMENTS context: only show accounts eligible for payments
// VIEW context: show all active accounts
public class BusinessContextFilter extends AccountFilterHandler {

    @Override
    protected void applyFilter(AccountFilterContext context) {
        String bizContext = context.getRequest().getBusinessContext();

        List<Account> filtered = switch (bizContext) {
            case "SERVICING" -> context.getAccounts().stream()
                .filter(Account::isInServicingState)
                .toList();
            case "PAYMENTS" -> context.getAccounts().stream()
                .filter(Account::isPaymentsEligible)
                .filter(a -> !a.isBlocked())
                .toList();
            case "VIEW" -> context.getAccounts().stream()
                .filter(Account::isActive)
                .toList();
            default -> context.getAccounts();
        };

        context.filterTo(filtered);
    }
}

// ── FILTER 2: Account ID filter ──────────────────────────
public class AccountIdFilter extends AccountFilterHandler {

    @Override
    protected void applyFilter(AccountFilterContext context) {
        Set<String> requestedIds = context.getRequest().getAccountIds();

        // If no IDs specified — no filter, pass all through
        if (requestedIds == null || requestedIds.isEmpty()) return;

        List<Account> filtered = context.getAccounts().stream()
            .filter(a -> requestedIds.contains(a.getAccountId()))
            .toList();

        context.filterTo(filtered);
    }
}

// ── FILTER 3: Product type filter ────────────────────────
public class ProductTypeFilter extends AccountFilterHandler {

    @Override
    protected void applyFilter(AccountFilterContext context) {
        Set<ProductType> requestedTypes = 
            context.getRequest().getProductTypes();

        if (requestedTypes == null || requestedTypes.isEmpty()) return;

        List<Account> filtered = context.getAccounts().stream()
            .filter(a -> requestedTypes.contains(a.getProductType()))
            .toList();

        context.filterTo(filtered);
    }
}
```

---

## Part B: Enrichment — Strategy

```java
// Enrichment strategy — varies entirely by business context
public interface AccountEnrichmentStrategy {

    boolean supports(String businessContext);

    List<EnrichedAccount> enrich(List<Account> accounts,
                                  Customer customer);
}

// ── SERVICING enrichment ─────────────────────────────────
// Business context = SERVICING
// Add: existing servicing instructions, open cases, 
//      pending tasks, last interaction
public class ServicingEnrichmentStrategy 
        implements AccountEnrichmentStrategy {

    private final ServicingInstructionsRepository servicingRepo;
    private final CaseManagementService caseService;

    @Override
    public boolean supports(String businessContext) {
        return "SERVICING".equals(businessContext);
    }

    @Override
    public List<EnrichedAccount> enrich(List<Account> accounts,
                                         Customer customer) {
        // Batch fetch — avoid N+1
        Map<String, List<ServicingInstruction>> instructionsByAccount =
            servicingRepo.findByAccountIds(
                accounts.stream()
                    .map(Account::getAccountId)
                    .collect(toSet()));

        Map<String, List<OpenCase>> casesByAccount =
            caseService.getOpenCasesByAccountIds(
                accounts.stream()
                    .map(Account::getAccountId)
                    .collect(toSet()));

        return accounts.stream()
            .map(account -> EnrichedAccount.builder()
                .account(account)
                .servicingInstructions(
                    instructionsByAccount.getOrDefault(
                        account.getAccountId(), emptyList()))
                .openCases(
                    casesByAccount.getOrDefault(
                        account.getAccountId(), emptyList()))
                .build())
            .toList();
    }
}

// ── PAYMENTS enrichment ──────────────────────────────────
// Business context = PAYMENTS
// Add: available balance, payment limits, 
//      last payment date, scheme eligibility
public class PaymentsEnrichmentStrategy 
        implements AccountEnrichmentStrategy {

    private final BalanceService balanceService;
    private final LimitService limitService;

    @Override
    public boolean supports(String businessContext) {
        return "PAYMENTS".equals(businessContext);
    }

    @Override
    public List<EnrichedAccount> enrich(List<Account> accounts,
                                         Customer customer) {
        // Batch fetch balances — single CBS call
        Map<String, Balance> balances = balanceService
            .getBalancesBatch(
                accounts.stream()
                    .map(Account::getAccountId)
                    .collect(toSet()));

        Map<String, PaymentLimits> limits = limitService
            .getLimitsBatch(customer.getCustomerId(),
                accounts.stream()
                    .map(Account::getAccountId)
                    .collect(toSet()));

        return accounts.stream()
            .map(account -> EnrichedAccount.builder()
                .account(account)
                .availableBalance(
                    balances.get(account.getAccountId()))
                .paymentLimits(
                    limits.get(account.getAccountId()))
                .schemeEligibility(
                    resolveSchemeEligibility(account, balances))
                .build())
            .toList();
    }
}

// ── VIEW enrichment ──────────────────────────────────────
// Business context = VIEW
// Minimal enrichment — just account summary, 
// no servicing data, no balance details
// GDPR data minimisation — don't fetch what you don't show
public class ViewEnrichmentStrategy 
        implements AccountEnrichmentStrategy {

    @Override
    public boolean supports(String businessContext) {
        return "VIEW".equals(businessContext);
    }

    @Override
    public List<EnrichedAccount> enrich(List<Account> accounts,
                                         Customer customer) {
        // No additional data fetched — GDPR data minimisation
        return accounts.stream()
            .map(account -> EnrichedAccount.builder()
                .account(account)
                // No servicing instructions
                // No balance details
                // No payment limits
                .build())
            .toList();
    }
}
```

---

## Composing Both — The API Orchestrator

```java
@Service
public class AccountsApiService {

    private final AccountRepository accountRepository;
    private final AccountFilterHandler filterChain;
    private final List<AccountEnrichmentStrategy> enrichmentStrategies;

    public List<EnrichedAccount> getAccounts(
            Customer customer,
            AccountQueryRequest request) {

        // Step 1: Load all accounts for customer
        List<Account> allAccounts = accountRepository
            .findByCustomerId(customer.getCustomerId());

        // Step 2: Filter — Chain of Responsibility
        AccountFilterContext filterContext =
            new AccountFilterContext(allAccounts, request);
        filterChain.filter(filterContext);
        List<Account> filteredAccounts = filterContext.getAccounts();

        // Step 3: Enrich — Strategy
        return enrichmentStrategies.stream()
            .filter(s -> s.supports(request.getBusinessContext()))
            .findFirst()
            .orElseThrow(() -> new NoEnrichmentStrategyException(
                request.getBusinessContext()))
            .enrich(filteredAccounts, customer);
    }
}
```

---

## The Full Picture

```
AccountQueryRequest
        │
        ▼
┌─────────────────────────────────────────┐
│         FILTER CHAIN (CoR)              │
│                                         │
│  BusinessContextFilter                  │
│       → AccountIdFilter                 │
│           → ProductTypeFilter           │
│                                         │
│  Each filter narrows the list           │
│  All three always run                   │
└──────────────────┬──────────────────────┘
                   │ Filtered List
                   ▼
┌─────────────────────────────────────────┐
│       ENRICHMENT STRATEGY               │
│                                         │
│  SERVICING → + instructions + cases     │
│  PAYMENTS  → + balances + limits        │
│  VIEW      → minimal, GDPR-safe         │
│                                         │
│  ONE strategy selected and executed     │
└──────────────────┬──────────────────────┘
                   │
                   ▼
          List<EnrichedAccount>
```

---

## The EM-Level Insight to Land in Interviews

> *"Filtering and enrichment are different concerns with different mechanics — I don't conflate them into one pattern. Filtering is Chain of Responsibility because all three dimensions always apply and progressively narrow the result set. Enrichment is Strategy because the entire enrichment algorithm swaps based on business context — SERVICING context fetches servicing instructions, PAYMENTS context fetches balances, VIEW context fetches nothing extra for GDPR data minimisation compliance. Separating these two concerns also means I can add a new filter dimension — say, currency — by adding one handler to the chain, and add a new business context — say, ONBOARDING — by adding one Strategy class. Zero changes to existing code in both cases."*

---

# 🎯 Drill

> *"A new business context — `ACCOUNT_SWITCHING` — needs to be added to your Accounts API. It needs to: filter to only current accounts that are eligible for CASS switching, and enrich with: the CASS switching window dates, any pending switch instructions, and a flag indicating if the account has a Direct Debit. How many new classes do you write? How many existing classes do you modify? Walk me through it."*

Answer or say **move on** → Enterprise/FS Patterns (Repository, Value Object, Aggregate Root, Specification)



