# 🧱 OOP Principles — EM-Level Deep Dive with FS Practicality

*These four principles — Encapsulation, Abstraction, Inheritance, Polymorphism — are the foundation everything else sits on. At EM level, the question isn't "what is encapsulation" — it's "where did violating it cost you in production?"*

---

# The Mental Model First

Before diving in — here's how the four relate to each other:

```
Encapsulation  →  Protects state. Controls what changes and who can change it.
Abstraction    →  Hides complexity. Shows only what the caller needs to know.
Inheritance    →  Shares behaviour. Establishes IS-A relationships.
Polymorphism   →  Varies behaviour. Same interface, different execution.
```

They build on each other:
- **Encapsulation** protects the internals that **Abstraction** hides
- **Inheritance** enables one form of **Polymorphism**
- But the modern view: favour **composition** over inheritance for polymorphism

---

# 1️⃣ ENCAPSULATION

## Core Concept

> *"Bundle data and the methods that operate on it together. Control access to internal state."*

But the deeper principle is:
> *"An object owns its state. No outsider should be able to put that object into an invalid state."*

This is not just about `private` fields and getters/setters. That's the **mechanism**. The **principle** is **invariant protection** — ensuring an object is always in a valid, consistent state.

---

## The Violation — Anemic Domain Model

```java
// ❌ VIOLATION — data bag with no behaviour
// This is the most common OOP violation in enterprise Java
public class Payment {
    private String paymentId;
    private BigDecimal amount;
    private String status;       // String — anyone can set "BLAH"
    private String debitAccount;
    private String creditAccount;
    private LocalDateTime createdAt;

    // 20 getters and setters — all public
    // Anyone can call setStatus("SETTLED") directly
    // Anyone can call setAmount(new BigDecimal("-500"))
    // Object has NO control over its own state
    public void setStatus(String status) { this.status = status; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    // ...
}

// Service layer compensates — logic scattered everywhere
@Service
public class PaymentService {
    public void settlePayment(String paymentId) {
        Payment payment = repository.findById(paymentId);

        // Validation scattered in service — not in the object
        if (!payment.getStatus().equals("SUBMITTED")) {
            throw new InvalidStateException("Can only settle SUBMITTED payments");
        }
        if (payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException("Invalid amount");
        }

        // Setting state directly from outside — no protection
        payment.setStatus("SETTLED");
        payment.setSettledAt(LocalDateTime.now());
        repository.save(payment);
    }
}

// PROBLEM: Another developer in another service does this
payment.setStatus("SETTLED");  // No validation, bypasses all rules
payment.setAmount(new BigDecimal("-999")); // Negative amount — no guard
// Object has no opinion on whether this is valid
```

**What breaks in production:**
- Duplicate settlement — two threads both see SUBMITTED, both set SETTLED
- Negative amount payments reaching CBS
- Status set to arbitrary strings — reporting breaks
- Business rules duplicated across 6 service classes

---

## The Fix — Rich Domain Model

```java
// ✅ Payment owns and protects its own state

public final class Payment {

    private final String paymentId;
    private final BigDecimal amount;      // final — never changes after creation
    private final Currency currency;
    private final String debitAccountId;
    private final String creditAccountId;
    private PaymentStatus status;         // Controlled enum — not raw String
    private final LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime settledAt;
    private String failureReason;

    // Private constructor — only factory methods can create
    private Payment(String paymentId, BigDecimal amount,
                    Currency currency, String debitAccountId,
                    String creditAccountId) {
        // Invariant enforcement at construction
        Objects.requireNonNull(paymentId, "paymentId required");
        Objects.requireNonNull(amount, "amount required");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException(
                "Payment amount must be positive, got: " + amount);
        }
        if (debitAccountId.equals(creditAccountId)) {
            throw new InvalidPaymentException(
                "Debit and credit accounts cannot be the same");
        }
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.debitAccountId = debitAccountId;
        this.creditAccountId = creditAccountId;
        this.status = PaymentStatus.INITIATED;   // Always starts here
        this.createdAt = LocalDateTime.now();
    }

    // ✅ Factory method — named, intention-revealing
    public static Payment initiate(String paymentId,
                                    BigDecimal amount,
                                    Currency currency,
                                    String debitAccountId,
                                    String creditAccountId) {
        return new Payment(paymentId, amount, currency,
                           debitAccountId, creditAccountId);
    }

    // ✅ Behaviour methods — object controls its own transitions
    public void submit() {
        if (this.status != PaymentStatus.INITIATED) {
            throw new InvalidStateTransitionException(
                String.format("Cannot submit payment in %s state. " +
                              "Must be INITIATED.", this.status));
        }
        this.status = PaymentStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
    }

    public void settle() {
        if (this.status != PaymentStatus.FRAUD_CLEARED) {
            throw new InvalidStateTransitionException(
                String.format("Cannot settle payment in %s state. " +
                              "Must be FRAUD_CLEARED.", this.status));
        }
        this.status = PaymentStatus.SETTLED;
        this.settledAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        if (this.status == PaymentStatus.SETTLED) {
            throw new InvalidStateTransitionException(
                "Cannot fail an already settled payment");
        }
        this.failureReason = Objects.requireNonNull(reason,
            "Failure reason required for audit trail");
        this.status = PaymentStatus.FAILED;
    }

    public void clearFraud() {
        if (this.status != PaymentStatus.SUBMITTED) {
            throw new InvalidStateTransitionException(
                "Cannot clear fraud on payment not in SUBMITTED state");
        }
        this.status = PaymentStatus.FRAUD_CLEARED;
    }

    // ✅ Query methods — read-only, no state leakage
    public boolean isSettleable() {
        return this.status == PaymentStatus.FRAUD_CLEARED;
    }

    public boolean isTerminal() {
        return this.status == PaymentStatus.SETTLED
            || this.status == PaymentStatus.FAILED;
    }

    // ✅ Getters — no setters exposed
    public String getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    // No setStatus(), no setAmount()
}

// Service becomes thin — just orchestrates
@Service
public class PaymentService {
    public void settlePayment(String paymentId) {
        Payment payment = repository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // Payment enforces its own rules — service doesn't need to know
        payment.settle();

        repository.save(payment);
        eventPublisher.publish(new PaymentSettledEvent(payment));
    }
}
```

---

## EM-Level Framing

**The production incident:**
> *"We had an anemic Payment domain model with public setters. A batch job for reconciliation called setStatus('SETTLED') directly to mark payments as reconciled — bypassing the fraud clearance check. 847 payments were marked SETTLED without fraud screening. Under FCA SYSC 6, that's a reportable control failure. Root cause: encapsulation violation. The fix was to make all state transitions go through behaviour methods on the Payment object, with every transition logged as a domain event."*

**Rich vs Anemic — the EM team decision:**
> *"Anemic domain models feel faster to write — just POJOs with getters/setters. But the business logic ends up duplicated across service classes. I've seen the same payment validation logic in PaymentService, BatchPaymentProcessor, RefundService, and ReconciliationJob — four copies, four times to fix when FCA updates the rules. Rich domain model puts the logic where it belongs — in the object that owns the data. One place to fix, one place to test."*

---

# 2️⃣ ABSTRACTION

## Core Concept

> *"Show only what's necessary. Hide complexity behind a clean interface."*

Two levels:
- **Data abstraction** — hide internal representation (encapsulation supports this)
- **Procedural abstraction** — hide complex logic behind a clean method/interface

The test: *"Can the caller use this without understanding how it works?"*

---

## The Violation — Leaking Implementation Details

```java
// ❌ VIOLATION — abstraction leaks implementation details
// Callers are coupled to HOW, not WHAT

public class PaymentRepository {

    // Leaks: PostgreSQL, PreparedStatement, ResultSet
    // Callers now depend on SQL infrastructure
    public PreparedStatement findPaymentSQL(
            Connection conn, String paymentId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT p.id, p.amount, p.status, p.debit_account_id, " +
            "p.credit_account_id, p.created_at " +
            "FROM payments p WHERE p.id = ? AND p.deleted = false");
        ps.setString(1, paymentId);
        return ps;
    }

    // Leaks: Kafka TopicPartition, ConsumerRecord
    public ConsumerRecord<String, PaymentEvent> consumeNextEvent(
            KafkaConsumer<String, PaymentEvent> consumer,
            TopicPartition partition) {
        consumer.assign(List.of(partition));
        ConsumerRecords<String, PaymentEvent> records =
            consumer.poll(Duration.ofMillis(100));
        return records.isEmpty() ? null :
               records.records(partition).get(0);
    }
}

// Caller — forced to know about SQL and Kafka internals
@Service
public class PaymentService {
    public Payment findPayment(String paymentId) {
        // Has to manage SQL connection — not payment business
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement ps =
                repository.findPaymentSQL(conn, paymentId);
            ResultSet rs = ps.executeQuery();
            // Has to map ResultSet manually
            if (rs.next()) {
                return mapResultSet(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
```

---

## The Fix — Clean Abstraction Layers

```java
// ✅ Repository abstracts storage completely
// Caller speaks domain language, not SQL language

public interface PaymentRepository {
    // Domain language — no SQL, no JDBC
    Optional<Payment> findById(String paymentId);
    List<Payment> findByAccountId(String accountId,
                                   PaymentStatus status);
    List<Payment> findPendingSettlement(LocalDate valueDate);
    void save(Payment payment);
}

// Implementation hides PostgreSQL details completely
@Repository
public class PostgreSQLPaymentRepository
        implements PaymentRepository {

    private final JdbcTemplate jdbc;
    private final PaymentRowMapper rowMapper;

    @Override
    public Optional<Payment> findById(String paymentId) {
        // SQL is an implementation detail — completely hidden
        List<Payment> results = jdbc.query(
            "SELECT * FROM payments WHERE id = ? AND deleted = false",
            rowMapper, paymentId);
        return results.isEmpty() ?
               Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<Payment> findPendingSettlement(LocalDate valueDate) {
        return jdbc.query(
            "SELECT * FROM payments " +
            "WHERE status = 'FRAUD_CLEARED' " +
            "AND value_date = ? " +
            "ORDER BY created_at ASC",
            rowMapper, valueDate);
    }
}

// ✅ Service speaks pure domain language
// No SQL, no Kafka, no HTTP — just business concepts
@Service
public class SettlementService {

    private final PaymentRepository paymentRepository;
    private final CoreBankingGateway cbsGateway;
    private final PaymentEventPublisher eventPublisher;

    public SettlementResult settlePaymentsForDate(LocalDate valueDate) {
        // Pure domain language — no infrastructure leakage
        List<Payment> pendingPayments =
            paymentRepository.findPendingSettlement(valueDate);

        List<Payment> settled = new ArrayList<>();
        List<Payment> failed = new ArrayList<>();

        for (Payment payment : pendingPayments) {
            try {
                cbsGateway.executeTransfer(payment);
                payment.settle();
                settled.add(payment);
            } catch (CBSException e) {
                payment.fail(e.getMessage());
                failed.add(payment);
            }
            paymentRepository.save(payment);
        }

        eventPublisher.publishBatch(
            settled.stream()
                   .map(PaymentSettledEvent::new)
                   .toList());

        return SettlementResult.of(settled.size(), failed.size());
    }
}
```

---

## Abstraction in FS — The Money Value Object

One of the most important abstractions in any FS system:

```java
// ❌ WRONG — leaking primitive representation
// BigDecimal alone has no concept of currency
// What currency is this? GBP? USD? EUR?
BigDecimal amount = new BigDecimal("1000.00");

// Arithmetic on different currencies — silent bug
BigDecimal gbp = new BigDecimal("1000");
BigDecimal usd = new BigDecimal("1000");
BigDecimal total = gbp.add(usd); // £1000 + $1000 = £2000?? Wrong.

// ✅ Money abstraction — encapsulates amount + currency
// Makes illegal state unrepresentable
public final class Money {

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount required");
        Objects.requireNonNull(currency, "currency required");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "Money amount cannot be negative");
        }
        // Always store with standard scale for currency
        this.amount = amount.setScale(
            currency.getDefaultFractionDigits(),
            RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money ofGBP(BigDecimal amount) {
        return new Money(amount, Currency.getInstance("GBP"));
    }

    public Money add(Money other) {
        // Currency mismatch = exception — not silent wrong result
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(
                String.format("Cannot add %s and %s",
                    this.currency, other.currency));
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(
                String.format("Cannot subtract %s from %s",
                    other.currency, this.currency));
        }
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(
                String.format("Cannot subtract %s from %s",
                    other, this));
        }
        return new Money(result, this.currency);
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    // Value object equality — by value, not reference
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0
            && currency.equals(money.currency);
    }

    @Override
    public String toString() {
        return currency.getSymbol() + amount.toPlainString();
    }
}

// Usage — reads like the business domain
Money paymentAmount = Money.ofGBP(new BigDecimal("50000.00"));
Money fee = Money.ofGBP(new BigDecimal("25.00"));
Money netAmount = paymentAmount.subtract(fee);

// This now fails at compile/runtime — not silently wrong
Money gbpAmount = Money.ofGBP(new BigDecimal("1000"));
Money usdAmount = Money.of(new BigDecimal("1000"),
                             Currency.getInstance("USD"));
Money wrong = gbpAmount.add(usdAmount); // Throws CurrencyMismatchException
```

---

## EM-Level Framing

**The production bug that abstraction prevents:**
> *"We had a currency conversion bug where a developer added a GBP amount and a USD amount stored as raw BigDecimal — silent arithmetic, no error, £1000 became £2000 on a commercial customer's statement. Under FCA BCOBS (Banking Conduct of Business Sourcebook), that's a customer harm event requiring remediation. Money value object with currency-aware arithmetic makes that class of bug impossible."*

**Abstraction at API level:**
> *"Abstraction applies to your API surface too. Your Accounts API should speak account domain language — getBalance(), initiateTransfer() — not expose your database schema. I've seen APIs that return column names as JSON keys — account_id, debit_credit_indicator, posting_date. That's a DB schema leaked into an API contract. When the DB schema changes, the API breaks. Abstraction means the API speaks business language, implementation details stay hidden."*

---

# 3️⃣ INHERITANCE

## Core Concept

> *"A class acquires properties and behaviour of another class. Establishes IS-A relationship."*

**The honest EM view:** Inheritance is the most **misused** OOP principle in enterprise Java. The rule of thumb:

> *"Use inheritance only when the IS-A relationship is genuinely true and stable over time. If you're using inheritance for code reuse — stop. Use composition."*

---

## When Inheritance Is Right vs Wrong

```java
// ✅ CORRECT inheritance — genuine IS-A, stable hierarchy
// All payment instructions ARE payment messages
// Liskov holds — subtype can replace supertype

public abstract class PaymentMessage {
    protected final String messageId;
    protected final Instant createdAt;
    protected final String sendingBIC;

    protected PaymentMessage(String messageId, String sendingBIC) {
        this.messageId = Objects.requireNonNull(messageId);
        this.sendingBIC = Objects.requireNonNull(sendingBIC);
        this.createdAt = Instant.now();
    }

    // Common behaviour all payment messages share
    public abstract String toISO20022Xml();
    public abstract MessageType getMessageType();

    public String getMessageId() { return messageId; }
    public Instant getCreatedAt() { return createdAt; }

    // Template method — skeleton same, details vary
    public final ValidationResult validate() {
        ValidationResult base = validateBaseFields();
        if (!base.isValid()) return base;
        return validateSpecificFields(); // Subclass implements
    }

    protected abstract ValidationResult validateSpecificFields();

    private ValidationResult validateBaseFields() {
        if (messageId == null || messageId.isBlank()) {
            return ValidationResult.invalid("messageId required");
        }
        if (!BICValidator.isValid(sendingBIC)) {
            return ValidationResult.invalid("Invalid BIC: " + sendingBIC);
        }
        return ValidationResult.valid();
    }
}

// PACS.008 — Customer Credit Transfer
public class CustomerCreditTransfer extends PaymentMessage {
    private final Money amount;
    private final String debtorIBAN;
    private final String creditorIBAN;
    private final String remittanceInfo;

    public CustomerCreditTransfer(String messageId,
                                   String sendingBIC,
                                   Money amount,
                                   String debtorIBAN,
                                   String creditorIBAN) {
        super(messageId, sendingBIC);
        this.amount = Objects.requireNonNull(amount);
        this.debtorIBAN = Objects.requireNonNull(debtorIBAN);
        this.creditorIBAN = Objects.requireNonNull(creditorIBAN);
    }

    @Override
    public String toISO20022Xml() {
        // Generate PACS.008 XML
        return ISO20022Generator.generatePACS008(this);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.PACS_008;
    }

    @Override
    protected ValidationResult validateSpecificFields() {
        if (!IBANValidator.isValid(debtorIBAN)) {
            return ValidationResult.invalid(
                "Invalid debtor IBAN: " + debtorIBAN);
        }
        if (!IBANValidator.isValid(creditorIBAN)) {
            return ValidationResult.invalid(
                "Invalid creditor IBAN: " + creditorIBAN);
        }
        return ValidationResult.valid();
    }
}
```

---

## When NOT to Use Inheritance — Use Composition Instead

```java
// ❌ WRONG — inheritance for code reuse, not IS-A
// "AdminPaymentService IS-A PaymentService" — is it really?
// Or does it just want to reuse some payment methods?

public class PaymentService {
    public PaymentResult submitPayment(PaymentRequest request) { ... }
    public PaymentStatus getStatus(String paymentId) { ... }
    protected void auditPayment(Payment payment) { ... }
}

// Admin service inherits to get audit + submit methods
// But also inherits methods it doesn't want exposed
public class AdminPaymentService extends PaymentService {
    // Now AdminPaymentService can call getStatus()
    // which might not be appropriate for admin context
    // Inherits ALL of PaymentService — can't pick and choose
    public void reversePayment(String paymentId) {
        Payment payment = findPayment(paymentId);
        auditPayment(payment);  // Reusing parent method
        // ...
    }
}

// ✅ RIGHT — composition for code reuse
public class AdminPaymentService {

    // Composes what it needs — doesn't inherit everything
    private final PaymentRepository paymentRepository;
    private final PaymentAuditService auditService;
    private final CBSGateway cbsGateway;

    public ReversalResult reversePayment(String paymentId,
                                          String reason,
                                          String authorisedBy) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // Use composed services — not inherited methods
        auditService.recordReversal(payment, reason, authorisedBy);
        cbsGateway.reverseTransaction(payment);

        return ReversalResult.success(paymentId);
    }
}
```

---

## The Fragile Base Class Problem

```java
// ❌ Base class changes break all subclasses
// This is why deep inheritance hierarchies are dangerous

public class BasePaymentProcessor {
    public PaymentResult process(PaymentRequest request) {
        validate(request);
        PaymentResult result = executePayment(request);
        audit(result);  // Added in v2 — breaks subclasses
        return result;
    }

    protected PaymentResult executePayment(PaymentRequest request) {
        // default impl
    }
}

// Works fine in v1
// In v2, base class adds audit() call
// CHAPSProcessor overrides process() and forgets to call audit()
// Now CHAPS payments aren't audited — FCA compliance failure
public class CHAPSProcessor extends BasePaymentProcessor {
    @Override
    public PaymentResult process(PaymentRequest request) {
        // Developer overrode entirely — audit() never called
        validateCHAPSCutoff(request);
        return chapsGateway.submit(request);
        // audit() missing — silent compliance failure
    }
}
```

**The fix:** Use Template Method with `final` on the skeleton method — subclasses can't bypass the audit step.

---

## EM-Level Framing

**The guideline I enforce:**
> *"My team follows the rule: if your inheritance hierarchy is more than 2 levels deep, it needs a design review. The fragile base class problem compounds with depth — a change at level 1 can break 15 subclasses at level 3 in ways that only show up at runtime. We had a BaseRepository class with 4 levels of inheritance where a transaction management change at the base caused inconsistent behaviour in 6 different repositories. After flattening to composition, each repository is independently testable and a change in one never affects another."*

**The composition vs inheritance decision framework:**
> *"I ask my team three questions: Is the IS-A relationship genuinely true? Will it be true in 2 years? Does the subtype honour the Liskov contract? If any answer is 'no' or 'maybe' — composition. In FS systems the domain evolves constantly — a SavingsAccount today might need to become a hybrid product tomorrow. Composition handles that; deep inheritance doesn't."*

---

# 4️⃣ POLYMORPHISM

## Core Concept

> *"One interface, many implementations. Same message, different behaviour."*

Two types:
- **Compile-time (static)** — method overloading
- **Runtime (dynamic)** — method overriding via inheritance or interfaces

In modern Java + Spring: **interface-based polymorphism** is the dominant and preferred form.

---

## The Power of Polymorphism in FS

```java
// Interface-based polymorphism — most powerful form in enterprise Java

public interface FraudSignalProcessor {
    FraudSignal process(TransactionContext context);
    String getSignalName();
    int getPriority(); // Higher = checked first
}

// Each fraud signal — different algorithm, same interface
@Component
public class VelocityCheckProcessor implements FraudSignalProcessor {

    private static final int MAX_TRANSACTIONS_PER_HOUR = 10;

    @Override
    public FraudSignal process(TransactionContext context) {
        int txnCount = transactionStore
            .countInLastHour(context.getAccountId());

        if (txnCount > MAX_TRANSACTIONS_PER_HOUR) {
            return FraudSignal.highRisk(
                getSignalName(),
                String.format("Velocity breach: %d txns/hr " +
                              "(max %d). Account: %s",
                    txnCount, MAX_TRANSACTIONS_PER_HOUR,
                    context.getAccountId()),
                0.85  // confidence score
            );
        }
        return FraudSignal.lowRisk(getSignalName(), 0.05);
    }

    @Override
    public String getSignalName() { return "VELOCITY_CHECK"; }

    @Override
    public int getPriority() { return 10; } // Run first — cheap
}

@Component
public class GeographicAnomalyProcessor implements FraudSignalProcessor {

    @Override
    public FraudSignal process(TransactionContext context) {
        GeoLocation lastKnownLocation =
            locationStore.getLastKnown(context.getAccountId());
        GeoLocation currentLocation = context.getLocation();

        double distanceKm = GeoCalculator
            .distanceBetween(lastKnownLocation, currentLocation);
        double hoursElapsed = ChronoUnit.HOURS.between(
            context.getLastTransactionTime(), context.getTransactionTime());

        // Physically impossible travel
        double maxPossibleDistanceKm = hoursElapsed * 900; // max flight speed

        if (distanceKm > maxPossibleDistanceKm) {
            return FraudSignal.highRisk(
                getSignalName(),
                String.format("Impossible travel: %.0fkm in %.1f hours",
                    distanceKm, hoursElapsed),
                0.95
            );
        }
        return FraudSignal.lowRisk(getSignalName(), 0.02);
    }

    @Override
    public String getSignalName() { return "GEOGRAPHIC_ANOMALY"; }

    @Override
    public int getPriority() { return 5; } // More expensive — run later
}

@Component
public class BehaviouralBiometricsProcessor implements FraudSignalProcessor {

    private final MLModelClient mlClient;

    @Override
    public FraudSignal process(TransactionContext context) {
        // ML model — most expensive, lowest priority
        BiometricScore score = mlClient.score(
            context.getDeviceFingerprint(),
            context.getTypingPattern(),
            context.getSessionBehaviour());

        if (score.getAnomalyScore() > 0.80) {
            return FraudSignal.highRisk(
                getSignalName(),
                "Behavioural anomaly detected. Score: " +
                score.getAnomalyScore(),
                score.getAnomalyScore()
            );
        }
        return FraudSignal.lowRisk(getSignalName(),
                                    score.getAnomalyScore());
    }

    @Override
    public String getSignalName() { return "BEHAVIOURAL_BIOMETRICS"; }

    @Override
    public int getPriority() { return 1; } // Most expensive — run last
}

// Orchestrator — polymorphism makes this completely generic
// It doesn't know or care which processors are registered
@Service
public class FraudDetectionService {

    private final List<FraudSignalProcessor> processors;

    // Spring injects ALL FraudSignalProcessor beans in priority order
    public FraudDetectionService(
            List<FraudSignalProcessor> processors) {
        // Sort by priority descending — highest priority first
        this.processors = processors.stream()
            .sorted(Comparator.comparingInt(
                FraudSignalProcessor::getPriority).reversed())
            .toList();
    }

    public FraudAssessment assess(TransactionContext context) {
        List<FraudSignal> signals = new ArrayList<>();

        for (FraudSignalProcessor processor : processors) {
            FraudSignal signal = processor.process(context); // Polymorphic call
            signals.add(signal);

            // Short-circuit on very high confidence fraud
            if (signal.isHighRisk() &&
                signal.getConfidence() > 0.95) {
                return FraudAssessment.blocked(signals,
                    "High confidence fraud signal: " +
                    signal.getSignalName());
            }
        }

        double compositeScore = calculateCompositeScore(signals);
        return compositeScore > 0.75
            ? FraudAssessment.flagForReview(signals, compositeScore)
            : FraudAssessment.allow(signals, compositeScore);
    }
}
```

---

## Method Overloading — Compile-time Polymorphism in FS

```java
// ✅ Overloading — same intent, different input signatures
public class MoneyTransferService {

    // Same operation, different ways to specify the amount
    public TransferResult transfer(String fromAccount,
                                    String toAccount,
                                    Money amount) {
        return executeTransfer(fromAccount, toAccount, amount);
    }

    // Convenience overload — GBP assumed
    public TransferResult transfer(String fromAccount,
                                    String toAccount,
                                    BigDecimal amount) {
        return transfer(fromAccount, toAccount,
                        Money.ofGBP(amount));
    }

    // Batch overload — different processing path
    public List<TransferResult> transfer(
            List<TransferInstruction> instructions) {
        return instructions.stream()
            .map(i -> transfer(i.getFrom(), i.getTo(), i.getAmount()))
            .toList();
    }
}
```

---

## EM-Level Framing

**Polymorphism and team scalability:**
> *"The FraudSignalProcessor interface is how my team ships fraud signals independently. The ML team owns BehaviouralBiometricsProcessor. The rules team owns VelocityCheckProcessor. They work in separate modules, deploy independently, and the FraudDetectionService never needs to change when a new signal is added. That's the team-level value of polymorphism — it's not just a code pattern, it's an organisational boundary."*

**Polymorphism vs switch — the production argument:**
> *"We retired a 340-line switch statement in the fraud engine that had been touched by 12 developers over 3 years. Every merge conflict in that file was two teams accidentally treading on each other. After extracting to FraudSignalProcessor implementations, each team owns exactly one class. Zero merge conflicts in 8 months. Polymorphism is a team coordination tool, not just a code quality tool."*

---

# ✅ OOP Principles — Summary

| Principle | Core idea | FS Violation | Production consequence |
|---|---|---|---|
| **Encapsulation** | Object owns its state. No outsider puts it in invalid state. | Anemic Payment with public setters | Fraud screening bypassed, FCA reportable |
| **Abstraction** | Hide complexity, show only what caller needs | Leaking SQL/Kafka internals into service layer | Schema change breaks API consumers |
| **Inheritance** | IS-A relationship. Stable. Subtype honours contract. | AdminService extends PaymentService for code reuse | Fragile base class breaks 6 repos on one change |
| **Polymorphism** | Same interface, different behaviour, varies at runtime | switch on PaymentType / FraudSignalType | Merge conflicts, regression risk on every new type |

---

# 🎯 DRILL TIME

**Drill A — Encapsulation:**
> *"A developer on your team has a Payment class with public setters for status and amount. They argue: 'Spring JPA needs setters to hydrate the entity from the database.' How do you respond? How do you have an encapsulated domain object that JPA can still persist and load?"*

**Drill B — Abstraction:**
> *"Your PaymentService currently accepts a JdbcTemplate directly and writes SQL inline. The bank is evaluating moving to CockroachDB. What's the abstraction you introduce, what does the refactor look like, and how do you make the database a swappable implementation detail?"*

**Drill C — Inheritance vs Composition:**
> *"A junior engineer proposes: PremiumPaymentService extends StandardPaymentService to inherit the process() method and override fee calculation. Walk me through your code review feedback and the composition-based alternative."*

**Drill D — Polymorphism:**
> *"Your payment notification system has a switch on CustomerSegment with 4 cases. Product tells you 2 new segments are coming next quarter and one every 6 months after that. How do you restructure using polymorphism, what does adding a new segment look like, and how does this connect to your team's deployment independence?"*

**Or say "move on"** → Java Concurrency (CompletableFuture, Thread pools, Race conditions, Virtual threads)

# 🏗️ SOLID Principles — EM-Level Deep Dive

*Why this matters at JPMC/GS: SOLID isn't a junior topic at this level. They expect you to connect violations to production incidents, team decisions, and regulatory consequences. Every principle should come with a "what breaks when you violate it" story.*

---

# S — Single Responsibility Principle

## 1. Core Concept

> *"A class should have only one reason to change."*

The word **reason** is doing all the work here. A reason to change = a stakeholder or actor whose requirements could force a modification.

The deeper framing Robert Martin intended:
> *"Gather together things that change for the same reason. Separate things that change for different reasons."*

---

## 2. The Violation — What it looks like in FS

```java
// ❌ VIOLATION — PaymentService doing too much
// Who owns this class? Payments team? Fraud team? 
// Compliance team? Reporting team?
@Service
public class PaymentService {

    public PaymentResult processPayment(PaymentRequest request) {
        // Responsibility 1: Validation logic
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException("Amount must be positive");
        }
        if (!isValidSortCode(request.getSortCode())) {
            throw new InvalidPaymentException("Invalid sort code");
        }

        // Responsibility 2: Fraud scoring
        int fraudScore = calculateFraudScore(request);
        if (fraudScore > 75) {
            blockAccount(request.getAccountId());
            sendFraudAlert(request.getCustomerId());
        }

        // Responsibility 3: FX conversion
        BigDecimal convertedAmount = request.getAmount()
            .multiply(getExchangeRate(request.getCurrency(), "GBP"));

        // Responsibility 4: CBS interaction
        debitAccount(request.getDebitAccountId(), convertedAmount);
        creditAccount(request.getCreditAccountId(), convertedAmount);

        // Responsibility 5: Regulatory reporting
        if (convertedAmount.compareTo(new BigDecimal("10000")) > 0) {
            submitSARReport(request);  // POCA 2002
        }

        // Responsibility 6: Audit logging
        auditLog.write(new AuditEvent(request, "PAYMENT_PROCESSED",
            LocalDateTime.now(), getCurrentUser()));

        // Responsibility 7: Notification
        notificationService.sendPaymentConfirmation(
            request.getCustomerId(), convertedAmount);

        return new PaymentResult(SUCCESS, generateReference());
    }
}
```

**How many reasons to change?**
1. Validation rules change (FCA updates sort code format)
2. Fraud model changes (new ML model replaces rule-based scoring)
3. FX logic changes (new currency pairs, new rate provider)
4. CBS API changes (T24 upgrade)
5. SAR threshold changes (regulatory update)
6. Audit format changes (new compliance requirement)
7. Notification template changes (UX team request)

**Seven reasons to change = seven violations of SRP.**

---

## 3. The Fix

```java
// ✅ Each class has ONE reason to change

@Service
public class PaymentService {
    private final PaymentValidationService validationService;
    private final FraudScoringService fraudService;
    private final FXConversionService fxService;
    private final CBSGateway cbsGateway;
    private final RegulatoryReportingService reportingService;
    private final PaymentAuditService auditService;
    private final NotificationService notificationService;

    public PaymentResult processPayment(PaymentRequest request) {
        validationService.validate(request);
        fraudService.assess(request);
        MonetaryAmount settled = fxService.convert(request);
        cbsGateway.executeTransfer(request, settled);
        reportingService.reportIfRequired(request, settled);
        auditService.record(request, settled);
        notificationService.notify(request, settled);
        return PaymentResult.success(generateReference());
    }
}

// PaymentValidationService — changes only when 
// validation rules change
@Service
public class PaymentValidationService {
    public void validate(PaymentRequest request) {
        validateAmount(request.getAmount());
        validateSortCode(request.getSortCode());
        validateAccountFormat(request.getDebitAccountId());
        // FCA sort code validation, 
        // modulus check per Vocalink spec
    }
}

// FraudScoringService — changes only when 
// fraud model changes
@Service
public class FraudScoringService {
    private final FraudModelClient modelClient;

    public FraudAssessment assess(PaymentRequest request) {
        FraudScore score = modelClient.score(request);
        if (score.isHighRisk()) {
            return FraudAssessment.block(score);
        }
        return FraudAssessment.allow(score);
    }
}
```

---

## 4. EM-Level Framing

**The production consequence of violation:**
> *"We had a class called PaymentProcessor with 1,400 lines. Fraud team needed to update the scoring threshold — they had to touch the same class as the CBS integration team who were upgrading T24. Both teams were editing the same file simultaneously, got merge conflicts, and we shipped a broken build to UAT. SRP violation directly caused a delayed release. After decomposition into 7 focused services, each team owned their class — no more merge conflicts on unrelated changes."*

**The team metric I use:**
> *"If I ask two different people on my team — 'who owns this class?' — and they give different answers, that's a SRP violation. Ownership ambiguity is the smell. At Lloyds, every service class has a named owning team in the file header. If a class has two team names, it needs to be split."*

**SRP at service level — the microservices connection:**
> *"SRP doesn't just apply to classes — it applies to microservices. A Payments service should have one reason to change: payment processing rules. If it also handles fraud scoring AND customer notifications, it's a SRP violation at the service level. Each of those is a separate bounded context with a separate team and a separate rate of change."*

---

# O — Open/Closed Principle

## 1. Core Concept

> *"Software entities should be open for extension but closed for modification."*

**Open for extension** — you can add new behaviour.
**Closed for modification** — adding new behaviour doesn't require changing existing, tested code.

The mechanism: **abstractions + polymorphism.** You extend by adding new implementations, not by editing existing ones.

---

## 2. The Violation

```java
// ❌ VIOLATION — adding new payment rail = 
// modify this method every time
public class PaymentRouter {

    public PaymentResult route(PaymentRequest request) {
        if (request.getType() == PaymentType.CHAPS) {
            // CHAPS-specific logic — 47 lines
            validateCHAPSCutoff(request);
            return chapsGateway.submit(request);

        } else if (request.getType() == PaymentType.BACS) {
            // BACS-specific logic — 38 lines
            validateBACSSortCode(request);
            return bacsGateway.submit(request);

        } else if (request.getType() == PaymentType.FASTER_PAYMENTS) {
            // FPS-specific logic — 29 lines
            return fpsGateway.submit(request);

        } else if (request.getType() == PaymentType.SWIFT) {
            // SWIFT-specific logic — 61 lines
            validateBIC(request);
            addCorrespondentBank(request);
            return swiftGateway.submit(request);

        // 6 months later — add SEPA
        } else if (request.getType() == PaymentType.SEPA) {
            // New code in OLD class — violation
            validateIBAN(request);
            return sepaGateway.submit(request);
        }

        throw new UnsupportedPaymentTypeException(request.getType());
    }
}
```

**Problems:**
- Every new payment rail = modify existing, tested code
- Regression risk on all existing rails when adding SEPA
- 200-line method after 5 payment types
- Unit tests for CHAPS break when SEPA is added incorrectly

---

## 3. The Fix — OCP via Strategy + Factory

```java
// ✅ OPEN for extension (new rail = new class)
//    CLOSED for modification (existing classes untouched)

public interface PaymentRailHandler {
    boolean supports(PaymentType type);
    PaymentResult handle(PaymentRequest request);
}

@Component
public class CHAPSHandler implements PaymentRailHandler {
    @Override
    public boolean supports(PaymentType type) {
        return type == PaymentType.CHAPS;
    }

    @Override
    public PaymentResult handle(PaymentRequest request) {
        validateCHAPSCutoff(request);
        return chapsGateway.submit(request);
    }
}

@Component
public class SWIFTHandler implements PaymentRailHandler {
    @Override
    public boolean supports(PaymentType type) {
        return type == PaymentType.SWIFT;
    }

    @Override
    public PaymentResult handle(PaymentRequest request) {
        validateBIC(request);
        addCorrespondentBank(request);
        return swiftGateway.submit(request);
    }
}

// Adding SEPA — NEW CLASS ONLY.
// Zero changes to CHAPS, BACS, FPS, SWIFT handlers.
// Zero regression risk.
@Component
public class SEPAHandler implements PaymentRailHandler {
    @Override
    public boolean supports(PaymentType type) {
        return type == PaymentType.SEPA;
    }

    @Override
    public PaymentResult handle(PaymentRequest request) {
        validateIBAN(request);
        return sepaGateway.submit(request);
    }
}

// Router — CLOSED for modification
// Spring injects all PaymentRailHandler beans automatically
@Service
public class PaymentRouter {
    private final List<PaymentRailHandler> handlers;

    public PaymentRouter(List<PaymentRailHandler> handlers) {
        this.handlers = handlers;
    }

    public PaymentResult route(PaymentRequest request) {
        return handlers.stream()
            .filter(h -> h.supports(request.getType()))
            .findFirst()
            .orElseThrow(() -> new UnsupportedPaymentTypeException(
                request.getType()))
            .handle(request);
    }
}
```

---

## 4. EM-Level Framing

**The quantified production example:**
> *"Payment routing switch statement had 847 lines covering 8 payment types. When the bank onboarded a new EU subsidiary needing SEPA, the developer modified the existing method, introduced a regression in CHAPS cutoff validation that wasn't caught in testing because the CHAPS test coverage relied on path-through logic. Went to production, CHAPS payments after 15:45 were silently accepted but rejected by the scheme. After OCP refactor — 9 handler classes, each independently tested, SEPA addition touched zero existing files."*

**OCP at the architecture level:**
> *"OCP applies at the microservices level too. My Fraud Detection service is closed for modification — it exposes a stable API. When we add a new fraud signal — say, device fingerprinting — we don't modify the fraud service internals; we add a new FraudSignalProcessor implementation that plugs into the scoring pipeline. The API contract doesn't change. Consumers don't retest."*

---

# L — Liskov Substitution Principle

## 1. Core Concept

> *"Objects of a superclass should be replaceable with objects of a subclass without breaking the program."*

More precisely — Barbara Liskov's original formulation:
> *"If S is a subtype of T, then objects of type T may be replaced with objects of type S without altering the desirable properties of the program."*

**In practice:** A subclass must honour the **contract** of its parent — same preconditions, same postconditions, same invariants. It can do more, never less.

**LSP violations are the sneakiest** — the code compiles, tests might pass, but behaviour is wrong in subtle ways.

---

## 2. The Classic Violation — Square extends Rectangle

```java
// The textbook example — but let's do the FS version

// ❌ VIOLATION
public class BankAccount {
    protected BigDecimal balance;

    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        this.balance = this.balance.subtract(amount);
    }
}

// Looks reasonable — SavingsAccount IS-A BankAccount
public class SavingsAccount extends BankAccount {

    private int withdrawalCount = 0;
    private static final int MAX_WITHDRAWALS = 6; // Regulatory limit

    @Override
    public void withdraw(BigDecimal amount) {
        // STRENGTHENED PRECONDITION — LSP VIOLATION
        // Parent contract: withdraw works if balance >= amount
        // Child contract: withdraw ALSO requires withdrawalCount < 6
        // Subtype added a NEW precondition parent didn't have
        if (withdrawalCount >= MAX_WITHDRAWALS) {
            throw new WithdrawalLimitException(
                "Monthly withdrawal limit reached");
        }
        super.withdraw(amount);
        withdrawalCount++;
    }
}

// The LSP violation in action
public class AccountService {
    public void processRefund(BankAccount account,
                               BigDecimal refundAmount) {
        // Caller expects: if balance >= amount, withdraw works
        // Works fine for BankAccount
        // Randomly throws WithdrawalLimitException for SavingsAccount
        // Caller can't substitute SavingsAccount for BankAccount safely
        account.withdraw(refundAmount);
    }
}
```

---

## 3. The Fix — Favour Composition + Interfaces

```java
// ✅ Model the actual domain — not all accounts are withdrawable
// Use interfaces to express capability

public interface Depositable {
    void deposit(BigDecimal amount);
    BigDecimal getBalance();
}

public interface Withdrawable {
    void withdraw(BigDecimal amount);
}

public interface TransactionalAccount 
        extends Depositable, Withdrawable {}

// Current account — full capabilities
public class CurrentAccount implements TransactionalAccount {

    private BigDecimal balance = BigDecimal.ZERO;

    @Override
    public void deposit(BigDecimal amount) {
        validatePositive(amount);
        this.balance = balance.add(amount);
    }

    @Override
    public void withdraw(BigDecimal amount) {
        validatePositive(amount);
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException();
        }
        this.balance = balance.subtract(amount);
    }
}

// Savings account — same withdrawal contract,
// but limit is a separate concern handled differently
public class SavingsAccount implements TransactionalAccount {

    private BigDecimal balance = BigDecimal.ZERO;
    private final WithdrawalLimitPolicy limitPolicy;

    @Override
    public void withdraw(BigDecimal amount) {
        validatePositive(amount);
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException();
        }
        // Limit policy is checked but NOT by strengthening 
        // the precondition — it's a separate concern
        limitPolicy.recordWithdrawal(this.accountId, amount);
        this.balance = balance.subtract(amount);
    }
}

// Fixed-term deposit — cannot be withdrawn (by design)
// Does NOT implement Withdrawable — capability expressed via type
public class FixedTermDeposit implements Depositable {

    @Override
    public void deposit(BigDecimal amount) {
        // Only at account opening
    }

    // No withdraw() — not in the contract
    // No need to throw UnsupportedOperationException
}

// Service now expresses intent clearly
public class AccountService {

    // Only accepts accounts that CAN be withdrawn from
    public void processRefund(Withdrawable account,
                               BigDecimal refundAmount) {
        account.withdraw(refundAmount);
        // Safe — any Withdrawable honours the contract
    }
}
```

---

## 4. EM-Level Framing

**The production smell that indicates LSP violation:**

> *"If you see `instanceof` checks or casting in service code — `if (account instanceof SavingsAccount)` — that's almost always an LSP violation. The caller is compensating for the fact that the subtype doesn't honour the parent's contract. I treat `instanceof` in business logic as a code smell that triggers a design review."*

**LSP violation in FS that caused a real problem:**
> *"We had a ReadOnlyAccount class that extended Account and overrode withdraw() to throw UnsupportedOperationException. Code that worked fine with Account silently broke with ReadOnlyAccount at runtime — account reconciliation jobs that called withdraw() for internal transfers started throwing at 2am batch runs. The fix was to separate the capability via interfaces — ReadOnlyAccount implemented Depositable only, never Withdrawable. The compiler then prevented us from passing it to code that expected Withdrawable."*

**LSP and microservices contracts:**
> *"LSP applies at the API level too. If v2 of your Payments API is supposed to be backwards-compatible with v1, it must honour LSP — it can accept more inputs, return more data, but cannot reject inputs v1 accepted or remove fields v1 guaranteed. API versioning without LSP = breaking change. Under FCA operational resilience requirements, breaking API changes in production payment systems require a change advisory board approval and rollback plan."*

---

# I — Interface Segregation Principle

## 1. Core Concept

> *"Clients should not be forced to depend on interfaces they do not use."*

Fat interfaces force implementing classes to have methods they don't need — and force callers to know about methods irrelevant to them.

**The violation signal:** A class implements an interface and throws `UnsupportedOperationException` on some methods. That's ISP violation made visible.

---

## 2. The Violation

```java
// ❌ FAT INTERFACE — one interface for all account operations
public interface AccountOperations {
    // Basic operations
    void deposit(BigDecimal amount);
    void withdraw(BigDecimal amount);
    BigDecimal getBalance();

    // Loan-specific
    BigDecimal getOutstandingBalance();
    void makeRepayment(BigDecimal amount);
    LocalDate getNextRepaymentDate();

    // Investment-specific
    List<Position> getPositions();
    void executeTrade(TradeOrder order);
    BigDecimal getPortfolioValue();

    // Card-specific
    void blockCard(BlockReason reason);
    void setSpendingLimit(BigDecimal limit);
    List<Transaction> getRecentTransactions(int count);
}

// CurrentAccount forced to implement loan + investment methods
public class CurrentAccount implements AccountOperations {
    // OK — these make sense
    public void deposit(BigDecimal amount) { ... }
    public void withdraw(BigDecimal amount) { ... }
    public BigDecimal getBalance() { ... }

    // ❌ Forced to implement — doesn't apply
    public BigDecimal getOutstandingBalance() {
        throw new UnsupportedOperationException(
            "Not a loan account");
    }

    public List<Position> getPositions() {
        throw new UnsupportedOperationException(
            "Not an investment account");
    }

    public void executeTrade(TradeOrder order) {
        throw new UnsupportedOperationException(
            "Not an investment account");
    }
    // ... 6 more UnsupportedOperationExceptions
}
```

---

## 3. The Fix — Role Interfaces

```java
// ✅ SEGREGATED interfaces — each is a role

public interface Depositable {
    void deposit(BigDecimal amount);
    BigDecimal getBalance();
}

public interface Withdrawable {
    void withdraw(BigDecimal amount);
}

public interface LoanAccount {
    BigDecimal getOutstandingBalance();
    void makeRepayment(BigDecimal amount);
    LocalDate getNextRepaymentDate();
    BigDecimal getMonthlyInstalment();
}

public interface InvestmentAccount {
    List<Position> getPositions();
    void executeTrade(TradeOrder order);
    BigDecimal getPortfolioValue();
}

public interface CardAccount {
    void blockCard(BlockReason reason);
    void setSpendingLimit(BigDecimal limit);
    List<Transaction> getRecentTransactions(int count);
}

// CurrentAccount implements only what it IS
public class CurrentAccount 
        implements Depositable, Withdrawable, CardAccount {
    // Only the methods that make sense
    // No UnsupportedOperationException anywhere
}

// MortgageAccount implements only what it IS
public class MortgageAccount implements LoanAccount, Depositable {
    // Depositable for repayments, LoanAccount for terms
    // No trading, no card operations
}

// ISA (Investment) Account
public class ISAAccount 
        implements InvestmentAccount, Depositable {
    // Can receive cash, can trade
    // Cannot withdraw (ISA rules)
}

// Services depend on minimum needed interface
public class LoanRepaymentService {
    // Depends on LoanAccount only — 
    // doesn't know about trading or card operations
    public void processRepayment(LoanAccount account,
                                  BigDecimal amount) {
        account.makeRepayment(amount);
    }
}

public class FraudService {
    // Depends on CardAccount only
    public void blockSuspiciousCard(CardAccount account) {
        account.blockCard(BlockReason.SUSPECTED_FRAUD);
    }
}
```

---

## 4. EM-Level Framing

**Why ISP matters for testing:**
> *"A fat interface means every test that needs to mock AccountOperations has to stub 15 methods even if it only uses 2. With ISP, FraudService tests mock CardAccount — 3 methods. LoanRepaymentService tests mock LoanAccount — 4 methods. Test setup goes from 30 lines to 5. At Lloyds scale, this compounds across hundreds of test classes."*

**ISP at the microservice API level:**
> *"ISP applies to REST APIs too. A fat API that returns everything in one response — account details, loan details, card details, investment positions — forces every consumer to parse everything even if they need 2 fields. Mobile app only needs balance and recent transactions. If it has to deserialise the entire response including investment positions it doesn't use, that's ISP violation at the API level. Solution: BFF pattern — each client gets a segregated API tailored to its needs."*

**The Accounts API you described:**
> *"The coarse-grained Accounts API with business context filtering is exactly ISP in practice. SERVICING context gets servicing instructions enrichment. PAYMENTS context gets balances and limits. VIEW context gets minimal data. Each consumer gets exactly what they need — no client is forced to deal with data it doesn't use. That's ISP applied to API design."*

---

# D — Dependency Inversion Principle

## 1. Core Concept

> *"High-level modules should not depend on low-level modules. Both should depend on abstractions."*
> *"Abstractions should not depend on details. Details should depend on abstractions."*

**What it means in practice:**
- Your business logic (high-level) should not know about your database technology, your HTTP library, or your message broker (low-level)
- Both should depend on an interface (abstraction)
- The concrete implementation is injected — not instantiated inside the high-level class

---

## 2. The Violation

```java
// ❌ VIOLATION — PaymentService directly depends on 
// concrete implementations

@Service
public class PaymentService {

    // Direct dependency on concrete PostgreSQL repository
    private final PostgreSQLPaymentRepository paymentRepository =
        new PostgreSQLPaymentRepository();

    // Direct dependency on concrete Kafka producer
    private final KafkaPaymentEventPublisher eventPublisher =
        new KafkaPaymentEventPublisher("localhost:9092");

    // Direct dependency on concrete T24 client
    private final TemenosT24Client t24Client =
        new TemenosT24Client("https://t24.lloyds.internal");

    public PaymentResult processPayment(PaymentRequest request) {
        Payment payment = Payment.from(request);

        // Tightly coupled to PostgreSQL
        paymentRepository.save(payment);

        // Tightly coupled to Kafka
        eventPublisher.publish(new PaymentEvent(payment));

        // Tightly coupled to T24
        t24Client.executeTransaction(request);

        return PaymentResult.success();
    }
}
```

**Problems:**
- Cannot unit test PaymentService without a real PostgreSQL + Kafka + T24
- Cannot swap PostgreSQL for a different DB without changing PaymentService
- Cannot test with an in-memory store
- PaymentService knows too much about infrastructure

---

## 3. The Fix — Depend on Abstractions

```java
// ✅ Abstractions defined at the domain level
// Implementations are infrastructure details

// Domain-level abstraction — PaymentService owns this interface
public interface PaymentRepository {
    void save(Payment payment);
    Optional<Payment> findById(String paymentId);
    List<Payment> findByAccountId(String accountId);
}

// Domain-level abstraction
public interface PaymentEventPublisher {
    void publish(PaymentEvent event);
}

// Domain-level abstraction (Anti-Corruption Layer interface)
public interface CoreBankingGateway {
    TransactionResult executeTransaction(PaymentRequest request);
    AccountBalance getBalance(String accountId);
}

// HIGH-LEVEL module — depends on abstractions only
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;
    private final CoreBankingGateway cbsGateway;

    // Spring injects the concrete implementation
    // PaymentService never knows which implementation it gets
    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentEventPublisher eventPublisher,
            CoreBankingGateway cbsGateway) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.cbsGateway = cbsGateway;
    }

    public PaymentResult processPayment(PaymentRequest request) {
        Payment payment = Payment.from(request);
        paymentRepository.save(payment);
        eventPublisher.publish(new PaymentEvent(payment));
        cbsGateway.executeTransaction(request);
        return PaymentResult.success();
    }
}

// LOW-LEVEL detail — PostgreSQL implementation
// Depends on the abstraction (implements it)
@Repository
public class PostgreSQLPaymentRepository 
        implements PaymentRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(Payment payment) {
        jdbcTemplate.update(
            "INSERT INTO payments(id, amount, status) VALUES (?,?,?)",
            payment.getId(), payment.getAmount(), 
            payment.getStatus().name());
    }
}

// LOW-LEVEL detail — Kafka implementation
@Component
public class KafkaPaymentEventPublisher 
        implements PaymentEventPublisher {
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Override
    public void publish(PaymentEvent event) {
        kafkaTemplate.send("payment-events", 
            event.getPaymentId(), event);
    }
}

// LOW-LEVEL detail — T24 implementation
@Component
public class TemenosT24Gateway implements CoreBankingGateway {
    private final TemenosT24Client t24Client;

    @Override
    public TransactionResult executeTransaction(
            PaymentRequest request) {
        T24TransactionRequest t24Request = 
            T24RequestMapper.map(request);
        return T24ResponseMapper.map(
            t24Client.executeTransaction(t24Request));
    }
}

// TESTING — swap to in-memory implementation
// PaymentService code doesn't change at all
public class InMemoryPaymentRepository 
        implements PaymentRepository {
    private final Map<String, Payment> store = new HashMap<>();

    @Override
    public void save(Payment payment) {
        store.put(payment.getId(), payment);
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return Optional.ofNullable(store.get(paymentId));
    }
}
```

---

## 4. EM-Level Framing

**DIP and testability — the direct connection:**
> *"Before DIP refactor, running PaymentService unit tests required Docker Compose with PostgreSQL, Kafka, and a T24 stub — 45 seconds to start. After DIP refactor, tests use InMemoryPaymentRepository and a mock EventPublisher — 0.3 seconds. For a team of 30 engineers running tests 20 times a day each, that's 5 hours of developer time saved daily. DIP is not an academic principle — it has a number."*

**DIP and infrastructure swap:**
> *"When Lloyds decided to migrate from PostgreSQL to CockroachDB for geo-distributed payments, PaymentService was untouched. We wrote a CockroachDBPaymentRepository that implemented the same PaymentRepository interface, ran the existing test suite against it, and swapped the Spring bean. Zero changes to business logic. That's the production value of DIP."*

**DIP and Spring IoC:**
> *"Spring's entire IoC container is an implementation of DIP at framework level. @Autowired, constructor injection, @Component scanning — all of it exists to wire abstractions to implementations without the high-level module knowing. When I explain DIP to juniors I start with Spring — they use it every day without realising they're applying DIP."*

**Constructor injection vs field injection — EM stance:**
> *"I enforce constructor injection in my team via Checkstyle rules that fail the build on @Autowired field injection. Reasons: constructor injection makes dependencies explicit and visible, allows the class to be instantiated outside Spring (in tests), makes circular dependencies a compile-time error instead of a runtime error, and makes immutability possible — all injected fields can be final. Field injection is lazy DIP — it hides dependencies."*

---

# ✅ SOLID — Full Summary

| Principle | One-liner | FS Violation Example | Fix |
|---|---|---|---|
| **SRP** | One reason to change | PaymentService doing validation + fraud + FX + CBS + audit | Split into focused services, each team-owned |
| **OCP** | Open for extension, closed for modification | Payment routing switch adding new rail = edit existing code | Strategy pattern + new class per rail |
| **LSP** | Subtypes must honour parent contract | SavingsAccount.withdraw() throws extra exception parent doesn't | Interface segregation — capability via type |
| **ISP** | Don't force unused interface methods | Fat AccountOperations forces CurrentAccount to stub loan/investment methods | Role interfaces — Depositable, Withdrawable, LoanAccount |
| **DIP** | Depend on abstractions, not concretions | PaymentService new-ing PostgreSQLRepository directly | Inject PaymentRepository interface, swap implementations |

---

# 🎯 DRILL TIME — Pick One

**Drill A — SRP:**
> *"You're reviewing a PR. A PaymentNotificationService has 3 methods: formatSMSMessage(), formatEmailMessage(), and sendNotification(). Your junior says 'it's all notification-related, so it's SRP compliant.' What's your response and how do you explain the violation?"*

**Drill B — OCP:**
> *"Your fraud engine has an if/else chain covering 6 fraud signals — velocity check, geo anomaly, device fingerprint, amount anomaly, counterparty risk, time-of-day. The ML team wants to add a new behavioural biometrics signal every sprint. How do you restructure this to be OCP-compliant, and what's the team process for adding a new signal?"*

**Drill C — LSP:**
> *"A ReadOnlyAccount class in your codebase extends BankAccount and throws UnsupportedOperationException on withdraw() and transfer(). Walk me through why this is an LSP violation, what breaks in production, and how you fix it without breaking existing callers."*

**Drill D — DIP:**
> *"Your PaymentService directly instantiates a KafkaEventPublisher. The compliance team wants to run regression tests against a mock event publisher that records all events for assertion. Walk me through the DIP refactor, and what architectural rule you'd enforce in your team to prevent this class of problem recurring."*

**Or say "move on"** → Java Concurrency (CompletableFuture, Thread pools, Race conditions, Virtual threads)
