Excellent — strong answer covering the key dimensions. Let me validate, fill the gaps, and add the technical depth that makes this complete.

---

## What You Nailed ✅

- REST over HTTP, gRPC over HTTP/2 with multiplexing
- Persistent TCP connection — reduced latency in gRPC
- gRPC feels like local method call — abstraction benefit
- gRPC more complex to manage and troubleshoot
- North-South → REST, East-West → REST or gRPC based on SLO
- Kafka decouples producer and consumer completely
- Sync when response needed to define next steps
- Async for fan-out, fan-in patterns

---

## The Complete Picture

---

## 1. REST — Deep Dive

```
REST over HTTP/1.1:

Client                          Server
  │                               │
  │ ──── TCP Handshake ─────────► │  ~100ms
  │ ──── TLS Handshake ─────────► │  ~100ms
  │ ──── HTTP Request ──────────► │
  │ ◄─── HTTP Response ────────── │
  │ ──── Connection closed ──────► │
  │                               │
  │ ──── TCP Handshake ─────────► │  ~100ms AGAIN ❌
  │ ──── TLS Handshake ─────────► │  ~100ms AGAIN ❌
  │ ──── HTTP Request ──────────► │
  │ ◄─── HTTP Response ────────── │

HTTP/1.1 Keep-Alive helps but:
├── Still one request at a time per connection
├── Head-of-line blocking — request 2 waits for request 1
└── Text-based headers — verbose, repeated every request
```

**REST strengths:**
```
✅ Universal — every language, framework, tool supports it
✅ Human readable — JSON, easy to debug with curl/Postman
✅ Browser native — fetch(), XMLHttpRequest
✅ Stateless — scales horizontally easily
✅ CDN cacheable — GET responses cacheable at edge
✅ Firewall friendly — standard HTTP ports
✅ Well understood — every developer knows it
✅ OpenAPI/Swagger — contract documentation
```

**REST weaknesses:**
```
❌ Verbose headers — repeated every request
❌ Text serialisation — JSON parsing overhead
❌ HTTP/1.1 — head-of-line blocking
❌ No streaming — request-response only
❌ No server push — client must poll
❌ Loose contract — breaking changes hard to detect
```

---

## 2. gRPC — Deep Dive

```
gRPC over HTTP/2:

Client                          Server
  │                               │
  │ ──── TCP + TLS (once) ──────► │  one-time cost
  │                               │
  │ ══ Multiplexed streams ══════ │
  │ ──── Request 1 (stream 1) ──► │
  │ ──── Request 2 (stream 3) ──► │  simultaneous! ✅
  │ ──── Request 3 (stream 5) ──► │
  │ ◄─── Response 2 (stream 3) ── │  out of order fine ✅
  │ ◄─── Response 1 (stream 1) ── │
  │ ◄─── Response 3 (stream 5) ── │

HTTP/2 benefits:
├── Multiplexing — multiple requests over single connection
├── Header compression (HPACK) — repeated headers compressed
├── Binary framing — more efficient than text
├── Server push — server can send data proactively
└── Stream prioritisation — important requests first
```

**Protocol Buffers — the serialisation layer:**
```protobuf
// Define contract in .proto file
// Strongly typed, language agnostic ✅

syntax = "proto3";

service AccountService {
    rpc GetAccount (GetAccountRequest) returns (Account);
    rpc CreateAccount (CreateAccountRequest) returns (Account);
    rpc StreamTransactions (AccountId) returns (stream Transaction);
    rpc BatchProcess (stream PaymentRequest) returns (BatchResult);
}

message Account {
    string id = 1;
    string owner_name = 2;
    double balance = 3;
    AccountStatus status = 4;
    google.protobuf.Timestamp created_at = 5;
}

message GetAccountRequest {
    string account_id = 1;
}
```

```java
// Generated stub — feels like local method call ✅
@GrpcClient("account-service")
private AccountServiceGrpc.AccountServiceBlockingStub stub;

public Account getAccount(String accountId) {
    // Looks like local method call
    // gRPC handles serialisation, network, deserialisation
    return stub.getAccount(
        GetAccountRequest.newBuilder()
            .setAccountId(accountId)
            .build()
    );
}
```

**gRPC communication patterns:**
```java
// 1. Unary — request/response (like REST)
rpc GetAccount (GetAccountRequest) returns (Account);

// 2. Server streaming — server sends multiple responses
rpc StreamTransactions (AccountId) returns (stream Transaction);
// Use case: real-time transaction feed, live updates

// 3. Client streaming — client sends multiple requests
rpc BatchProcess (stream PaymentRequest) returns (BatchResult);
// Use case: bulk payment upload

// 4. Bidirectional streaming — both sides stream
rpc LiveChat (stream Message) returns (stream Message);
// Use case: real-time collaboration, trading platforms
```

**gRPC strengths:**
```
✅ Performance — binary, compressed, multiplexed
✅ Strong typing — proto contract enforced at compile time
✅ Code generation — client/server stubs auto-generated
✅ Streaming — 4 communication patterns
✅ Language agnostic — generate clients in any language
✅ Built-in deadlines — timeout propagation across services
✅ Bi-directional streaming — REST can't do this
```

**gRPC weaknesses:**
```
❌ Not browser native — needs gRPC-Web proxy for browsers
❌ Hard to debug — binary, not human readable
❌ Learning curve — proto files, generated code
❌ Less tooling — fewer API gateways support it natively
❌ Schema evolution — careful with field numbering
❌ Firewall issues — some firewalls block HTTP/2
```

---

## 3. Kafka — Deep Dive

```
Kafka vs REST/gRPC fundamental difference:

REST/gRPC — SYNCHRONOUS, COUPLED:
Producer knows consumer exists
Producer waits for consumer response
Producer fails if consumer is down
Tight temporal coupling ❌

Kafka — ASYNCHRONOUS, DECOUPLED:
Producer writes to log
Producer doesn't know who consumes
Producer succeeds even if consumer is down ✅
Consumers process at their own pace ✅
```

**Kafka strengths:**
```
✅ Temporal decoupling — producer/consumer independent
✅ Replay — consumers can reprocess historical events
✅ Fan-out — multiple consumers, same event
✅ Buffering — absorbs traffic spikes
✅ Durability — events persisted, survive crashes
✅ Ordering — per partition ordering guarantee
✅ Backpressure — consumers control their own rate
✅ Audit trail — immutable log of all events
```

**Kafka weaknesses:**
```
❌ No immediate response — async by nature
❌ Eventual consistency — not for real-time decisions
❌ Operational complexity — brokers, zookeeper/KRaft
❌ Debugging harder — trace across topics
❌ Ordering only within partition — not global
❌ Message size limits — large payloads need care
```

---

## 4. Decision Framework — When to Use What

```
Decision tree:

Does consumer need immediate response to proceed?
│
├── YES → Synchronous
│         │
│         ├── External/browser client?
│         │   └── REST ✅
│         │       Human readable, universal support
│         │
│         ├── Internal service, performance critical?
│         │   ├── Latency SLO < 50ms → gRPC ✅
│         │   ├── Streaming needed → gRPC ✅
│         │   └── Simpler ops preferred → REST ✅
│         │
│         └── Internal service, standard latency?
│             └── REST ✅ (simpler, sufficient)
│
└── NO → Asynchronous → Kafka ✅
          │
          ├── Fan-out (one event, many consumers) → Kafka ✅
          ├── Spike buffering needed → Kafka ✅
          ├── Replay needed → Kafka ✅
          ├── Fire and forget → Kafka ✅
          └── Audit trail needed → Kafka ✅
```

---

## 5. Real World Patterns — Your Lloyds Platform

```
North-South (Client → API):
Browser/Mobile → API Gateway → REST ✅
├── Universal browser support
├── CDN cacheable
└── Human readable for debugging

East-West (Service → Service):
Synchronous calls:
AccountService → CoreBankingService → REST ✅
├── Simple, sufficient latency
└── Team familiarity

PaymentService → FraudService → gRPC ✅
├── Sub-10ms SLO required
├── High frequency calls
└── Strong contract needed

Asynchronous flows:
AccountService → [Kafka] → ReadStoreConsumer ✅
├── CQRS event publishing
├── No response needed
└── Multiple consumers (read store, audit, analytics)

PaymentService → [Kafka] → NotificationService ✅
├── Fire and forget
└── Notification failure doesn't affect payment
```

---

## 6. Fan-Out and Fan-In — Your Point Expanded

```
Fan-Out — one event, multiple independent consumers:

PaymentCompleted event
        │
        ├──────────────────────────────────────┐
        │              │                       │
        ▼              ▼                       ▼
NotificationService  AuditService      AnalyticsService
(sends email/SMS)    (records log)     (updates dashboard)

All three consume same event independently ✅
Each at own pace ✅
Failure of one doesn't affect others ✅
Add new consumer = zero changes to producer ✅

Fan-In — multiple producers, one consumer:

AccountService    ──┐
PaymentService    ──┼──► [aggregation-topic] ──► ReportingService
TransferService   ──┘

All events flow to single consumer for aggregation ✅

Fan-Out + Fan-In together:
PaymentCompleted
    │
    ├──► FraudAnalysis ──────┐
    ├──► RiskScoring  ──────►├──► [results-topic] ──► DecisionEngine
    └──► LimitCheck   ──────┘
```

---

## 7. Hybrid Pattern — Sync + Async Combined

```java
// Real pattern — sync for critical path, async for side effects

@Service
public class PaymentService {

    @Transactional
    public PaymentResult processPayment(PaymentRequest request) {

        // SYNC — critical path, needs immediate response
        Account account = accountService.getAccount(  // REST/gRPC
            request.getAccountId());

        FraudResult fraud = fraudService.check(request); // gRPC — low latency

        if (fraud.isBlocked()) {
            return PaymentResult.rejected("FRAUD_DETECTED");
        }

        PaymentResult result = executePayment(request);

        // ASYNC — side effects, no response needed
        kafkaTemplate.send("payment-completed",        // Kafka
            PaymentCompletedEvent.from(result));
        // Notification, audit, analytics — all async ✅

        return result;  // return immediately ✅
        // Don't wait for notification to be sent
    }
}
```

---

## Performance Comparison

```
Benchmark — 10,000 requests, internal service call:

REST (HTTP/1.1):
├── Avg latency: 45ms
├── P99 latency: 120ms
├── Throughput: 2,000 RPS
└── CPU: moderate (JSON parsing)

REST (HTTP/2):
├── Avg latency: 30ms
├── P99 latency: 80ms
├── Throughput: 5,000 RPS
└── CPU: moderate

gRPC (HTTP/2 + Protobuf):
├── Avg latency: 8ms       ← 5x faster than REST/1.1
├── P99 latency: 25ms
├── Throughput: 15,000 RPS ← 7x higher throughput
└── CPU: lower (binary serialisation)

Kafka (async):
├── Producer latency: <5ms (fire and forget)
├── End-to-end: 50-500ms  (depends on consumer)
├── Throughput: millions of events/sec
└── Not comparable — different pattern entirely
```

---

## Polished 90-Second Interview Answer

*"REST, gRPC, and Kafka represent three fundamentally different communication styles — each with distinct trade-offs.*

*REST over HTTP is the universal default — human readable, browser native, CDN cacheable, understood by every developer and tool. The weakness is HTTP/1.1 overhead — TCP and TLS handshake per connection, head-of-line blocking, verbose text headers. It's the right choice for North-South traffic — browser to API, public-facing endpoints — and for East-West where latency requirements are standard.*

*gRPC over HTTP/2 addresses REST's performance limitations — persistent multiplexed connections eliminate per-request handshake overhead, binary Protobuf serialisation reduces payload size, and header compression removes repetition. In benchmarks it's typically 5-7x faster throughput than REST. The trade-off is complexity — proto files, code generation, harder debugging, and no native browser support. I'd use gRPC for East-West internal calls with strict sub-50ms SLOs, high-frequency calls between services, or where you need streaming — which REST fundamentally can't do.*

*Kafka is a completely different paradigm — asynchronous, log-based, temporally decoupled. Producer succeeds regardless of whether consumer is running. Events are durable and replayable. It's the right choice when the producer doesn't need an immediate response to proceed — fan-out to multiple consumers, spike buffering, audit trails, CQRS event publishing.*

*In practice I combine all three. At Lloyds: REST for public APIs, gRPC for latency-sensitive internal calls like fraud checking, Kafka for event-driven flows — CQRS read store updates, notifications, analytics. The critical path was synchronous, side effects were async fire-and-forget over Kafka."*

---

Ready for **Question 2 — How do you ensure exactly-once delivery in Kafka?**

Exactly right — and a very mature perspective that shows real production experience. Let me validate, fill the gaps, and add the complete technical picture.

---

## What You Nailed ✅

- Exactly-once at broker level is largely a myth in practice
- Real-world scenarios break guarantees outside broker control
- Application-level idempotency is the real solution
- Cache/DB of processed events to detect duplicates
- Covers duplicate delivery, retry/replay, crash before ack

---

## The Complete Picture

---

## 1. Why Exactly-Once is Hard — The Scenarios

```
Scenario 1 — Consumer crashes AFTER processing, BEFORE ack:

T=0  Consumer reads message from Kafka
T=1  Consumer processes message ✅ (DB write successful)
T=2  Consumer crashes ❌ (before committing offset)
T=3  Consumer restarts
T=4  Kafka redelivers message (offset not committed)
T=5  Consumer processes AGAIN ❌ — DUPLICATE

Scenario 2 — Consumer crashes AFTER ack, BEFORE processing:

T=0  Consumer reads message
T=1  Consumer commits offset to Kafka ✅
T=2  Consumer crashes ❌ (before DB write)
T=3  Kafka thinks message consumed — never redelivers
T=4  MESSAGE LOST ❌

Scenario 3 — Producer retry creates duplicate:

T=0  Producer sends message to Kafka
T=1  Kafka writes message, sends ack
T=2  Network partition — ack lost ❌
T=3  Producer times out — retries
T=4  Kafka receives DUPLICATE message
T=5  Consumer processes same message TWICE ❌

Scenario 4 — Replay for recovery:

T=0  Bug found in consumer logic
T=1  Fix deployed
T=2  Reset consumer offset to replay messages
T=3  All messages processed AGAIN
T=4  Must handle duplicates ❌

Scenario 5 — Consumer slow — rebalance:

T=0  Consumer polls messages, starts processing
T=1  Processing takes too long (> max.poll.interval.ms)
T=2  Kafka triggers rebalance — reassigns partition
T=3  Another consumer gets same partition
T=4  Processes same messages ❌ — DUPLICATE
```

---

## 2. Kafka's Built-in Exactly-Once — What It Actually Covers

```
Kafka does provide exactly-once semantics — but ONLY within Kafka itself:

Idempotent Producer (enable.idempotence=true):
├── Each producer gets a Producer ID (PID)
├── Each message gets a sequence number
├── Broker deduplicates retries from same producer ✅
└── Covers: Scenario 3 (producer retry duplicate) ✅
   Does NOT cover: consumer-side processing ❌

Transactional API:
├── Atomic write across multiple topics/partitions
├── Read-process-write in single Kafka transaction
└── Covers: Kafka → Kafka pipelines (Kafka Streams) ✅
   Does NOT cover: Kafka → DB → external system ❌

// Kafka Streams exactly-once (processing.guarantee=exactly_once_v2)
// Works perfectly when:
// Input: Kafka topic
// Processing: in-memory transformation
// Output: Kafka topic
// Everything within Kafka ecosystem ✅

// Breaks when:
// Output: Database write
// Output: REST API call
// Output: Email notification
// Any external system ❌
```

---

## 3. Application-Level Idempotency — The Real Solution

### Pattern 1 — Processed Event Store (your solution)

```java
@Service
public class PaymentEventConsumer {

    @Transactional
    public void processEvent(ConsumerRecord<String, String> record,
                             Acknowledgment ack) {

        PaymentEvent event = deserialize(record.value());

        // IDEMPOTENCY CHECK — already processed?
        if (processedEventRepo.existsByEventId(event.getEventId())) {
            log.info("Duplicate event ignored: {}", event.getEventId());
            ack.acknowledge();  // ack to move past it
            return;             // safe to ignore ✅
        }

        // Process event
        PaymentResult result = processPayment(event);

        // ATOMIC — both in same transaction ✅
        // 1. Record as processed
        processedEventRepo.save(
            ProcessedEvent.builder()
                .eventId(event.getEventId())
                .processedAt(Instant.now())
                .result(serialize(result))
                .build()
        );

        // 2. Business write
        paymentRepo.save(result);

        // Commit offset AFTER successful DB write
        ack.acknowledge();  // explicit ack ✅
    }
}

// ProcessedEvent table
@Entity
@Table(name = "processed_events",
       indexes = @Index(columnList = "event_id", unique = true))
public class ProcessedEvent {
    @Id
    private String eventId;        // unique constraint ✅
    private Instant processedAt;
    private String result;         // cache result for duplicate response
}
```

### Pattern 2 — Monotonic Versioning (your Lloyds implementation)

```java
@Transactional
public void processAccountEvent(AccountEvent event) {

    AccountState current = accountStateRepo
        .findById(event.getAccountId())
        .orElse(AccountState.empty(event.getAccountId()));

    // Version check — reject stale or duplicate events
    if (event.getVersion() <= current.getLastProcessedVersion()) {
        log.info("Stale/duplicate event ignored: " +
                 "accountId={}, eventVersion={}, currentVersion={}",
            event.getAccountId(),
            event.getVersion(),
            current.getLastProcessedVersion());
        return;  // idempotent ✅
    }

    // Ensure no version gaps — events must be sequential
    if (event.getVersion() != current.getLastProcessedVersion() + 1) {
        log.warn("Version gap detected: expected={}, got={}",
            current.getLastProcessedVersion() + 1,
            event.getVersion());
        throw new OutOfOrderEventException(event);
        // Will be retried — wait for missing version
    }

    // Apply event
    AccountState updated = applyEvent(current, event);
    updated.setLastProcessedVersion(event.getVersion());
    accountStateRepo.save(updated);
}
```

### Pattern 3 — Natural Business Idempotency

```java
// Some operations are naturally idempotent — design for it

// SET operation — idempotent by nature ✅
// Setting account status to ACTIVE twice = still ACTIVE
@Transactional
public void activateAccount(String accountId) {
    accountRepo.updateStatus(accountId, AccountStatus.ACTIVE);
    // Duplicate call = same result ✅
}

// UPSERT — idempotent ✅
@Transactional
public void upsertAccountBalance(String accountId, double balance) {
    accountRepo.upsert(accountId, balance);
    // INSERT or UPDATE — safe to call multiple times ✅
}

// NOT idempotent — needs explicit handling ❌
@Transactional
public void incrementBalance(String accountId, double amount) {
    Account account = accountRepo.findById(accountId);
    account.setBalance(account.getBalance() + amount);  // ❌
    // Duplicate = double increment
}

// Fix — make it idempotent with transactionId ✅
@Transactional
public void creditBalance(String accountId,
                          double amount,
                          String transactionId) {
    // Check if this transaction already applied
    if (transactionRepo.existsById(transactionId)) {
        return;  // duplicate — safe to ignore ✅
    }
    Account account = accountRepo.findById(accountId);
    account.setBalance(account.getBalance() + amount);
    accountRepo.save(account);
    transactionRepo.save(new Transaction(transactionId, amount));
}
```

### Pattern 4 — Conditional Updates (Database-level idempotency)

```java
// Use database constraints to enforce idempotency

// Unique constraint on business key
@Table(
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"transaction_id", "account_id"}
    )
)
public class Payment { ... }

// Insert fails on duplicate — catch and ignore ✅
@Transactional
public void processPayment(PaymentEvent event) {
    try {
        Payment payment = Payment.builder()
            .transactionId(event.getTransactionId())  // unique
            .accountId(event.getAccountId())
            .amount(event.getAmount())
            .build();

        paymentRepo.save(payment);  // fails on duplicate ✅

    } catch (DataIntegrityViolationException e) {
        // Unique constraint violated — duplicate event
        log.info("Duplicate payment ignored: {}",
            event.getTransactionId());
        // Idempotent — safe to ignore ✅
    }
}
```

---

## 4. Ack Strategy — Critical for Delivery Guarantee

```java
// Three ack strategies — each has implications

// AUTO COMMIT — at-most-once ❌
// Offset committed before processing completes
// Consumer crashes during processing → message lost
config.put(ENABLE_AUTO_COMMIT_CONFIG, true);
config.put(AUTO_COMMIT_INTERVAL_MS_CONFIG, 5000);
// Committed every 5 seconds regardless of processing state ❌

// MANUAL ACK AFTER PROCESSING — at-least-once ✅
// Your approach — correct for most use cases
@KafkaListener(topics = "payment-events")
public void consume(ConsumerRecord<String, String> record,
                    Acknowledgment ack) {
    try {
        processEvent(deserialize(record.value()));
        ack.acknowledge();  // ack AFTER successful processing ✅
    } catch (RetryableException e) {
        // Don't ack — Kafka will redeliver ✅
        throw e;
    } catch (NonRetryableException e) {
        // Ack to move past it — send to DLQ
        sendToDLQ(record, e);
        ack.acknowledge();  // ack to move past ✅
    }
}

// MANUAL ACK BEFORE PROCESSING — at-most-once ❌
// Only use when losing messages is acceptable
ack.acknowledge();          // ack first
processEvent(record);       // then process — dangerous ❌
```

---

## 5. Producer Idempotency Configuration

```java
// Producer side — prevent duplicate messages from retries
@Bean
public ProducerFactory<String, String> producerFactory() {
    Map<String, Object> config = new HashMap<>();

    // Idempotent producer — Kafka deduplicates retries ✅
    config.put(ENABLE_IDEMPOTENCE_CONFIG, true);
    // Requires:
    config.put(ACKS_CONFIG, "all");         // wait for all replicas
    config.put(RETRIES_CONFIG, 3);          // retry on failure
    config.put(MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
    // Only 1 unacked request at a time — preserves ordering

    // Idempotency key in message headers
    // Lets consumer deduplicate at application level too
    return new DefaultKafkaProducerFactory<>(config);
}

// Always include idempotency key in message
public void publishEvent(DomainEvent event) {
    ProducerRecord<String, String> record =
        new ProducerRecord<>("payment-events",
            event.getAggregateId(),  // partition key
            serialize(event));

    // Add idempotency key as header
    record.headers().add(
        "idempotency-key",
        event.getEventId().getBytes()
    );

    kafkaTemplate.send(record);
}
```

---

## 6. Handling the Crash-Before-Ack Scenario

```java
// Your Scenario 1 — crash after processing, before ack
// Consumer restarts → Kafka redelivers → must handle duplicate

// Solution — atomic processing + idempotency check
@Transactional
public void processEvent(PaymentEvent event, Acknowledgment ack) {

    // This entire block is atomic in DB
    // If crash here → transaction rolls back
    // Kafka redelivers → we try again ✅

    boolean alreadyProcessed = processedEventRepo
        .existsByEventId(event.getEventId());

    if (!alreadyProcessed) {
        // Process
        PaymentResult result = executePayment(event);

        // Save result + mark processed — atomic ✅
        processedEventRepo.save(
            ProcessedEvent.of(event.getEventId(), result));
        paymentRepo.save(result);
    }

    // Ack AFTER transaction commits
    ack.acknowledge();  // ✅

    // Crash scenarios:
    // Crash before transaction commits:
    //   → rollback, Kafka redelivers, try again ✅
    // Crash after transaction commits, before ack:
    //   → Kafka redelivers, idempotency check catches duplicate ✅
    // Crash after ack:
    //   → normal, already processed ✅
}
```

---

## 7. Processed Event Store — Operational Concerns

```java
// Processed event store grows forever — needs management

// Option 1 — TTL based cleanup
@Scheduled(cron = "0 0 2 * * ?")  // 2am daily
public void cleanupOldEvents() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(7));
    // Keep 7 days — covers any realistic retry window
    processedEventRepo.deleteByProcessedAtBefore(cutoff);
}

// Option 2 — Redis with TTL (faster lookups, auto expiry)
@Service
public class RedisIdempotencyStore {

    private final RedisTemplate<String, String> redis;
    private static final Duration TTL = Duration.ofDays(7);

    public boolean isProcessed(String eventId) {
        return redis.hasKey("processed:" + eventId);
    }

    public void markProcessed(String eventId) {
        redis.opsForValue().set(
            "processed:" + eventId,
            "1",
            TTL  // auto-expires ✅
        );
    }
}

// Trade-off:
// DB store  → durable, survives Redis restart, slower lookup
// Redis     → fast O(1) lookup, auto TTL, but volatile
// Combined  → Redis as fast check, DB as fallback ✅
```

---

## Complete Summary

```
Exactly-Once Delivery — Layered Approach:

PRODUCER SIDE:
├── enable.idempotence=true    → broker deduplicates retries
├── acks=all                   → durable before ack
└── Include eventId in message → enables consumer deduplication

BROKER SIDE:
├── Replication factor ≥ 3     → durability
├── min.insync.replicas = 2    → quorum writes
└── Kafka transactions         → atomic within Kafka only

CONSUMER SIDE:
├── Manual ack after processing → at-least-once delivery
├── Idempotency check           → deduplicate at application level
│   ├── ProcessedEvent store (DB or Redis)
│   ├── Monotonic versioning
│   ├── Natural business idempotency (upserts, sets)
│   └── DB unique constraints
├── Atomic: process + mark processed in same transaction
└── Non-blocking retry          → retry topics + DLQ

RESULT:
Producer retries    → broker deduplicates ✅
Kafka redelivery    → consumer idempotency check ✅
Crash before ack    → redeliver, idempotency catches it ✅
Replay for recovery → idempotency catches all duplicates ✅
```

---

## Polished 90-Second Interview Answer

*"Exactly-once delivery at the broker level is largely a myth in practice — there are too many failure scenarios outside the broker's control. Kafka's idempotent producer and transactional API provide exactly-once within the Kafka ecosystem, but the moment you write to a database or call an external service, those guarantees break.*

*The real scenarios to handle are: consumer crashes after processing but before acking — Kafka redelivers and you process twice. Producer retries on network timeout — broker may have already written the message. Consumer rebalance during slow processing — another consumer picks up the same partition. Replay for bug fixes — entire offset range reprocessed.*

*The solution is application-level idempotency. Every event carries a unique eventId. Before processing, check a processed event store — either a DB table with unique constraint on eventId, or Redis with a TTL. If already processed, acknowledge and skip. The critical detail is atomicity — the business write and the mark-as-processed must be in the same database transaction. If the consumer crashes between processing and acking, Kafka redelivers, the idempotency check catches the duplicate, we ack and move on.*

*For monotonic versioning — which we used at Lloyds — events carry a version number per aggregate. If incoming version is less than or equal to current version in the store, it's a duplicate or stale event, silently ignored.*

*Combined with manual acknowledgement — ack only after successful processing and transaction commit — this gives you effective exactly-once semantics at the application level regardless of what Kafka guarantees at the broker level."*

---

Ready for **Question 3 — How do you secure inter-service communication? mTLS, JWT, OAuth2?**

Excellent — genuinely strong answer showing real production security architecture. Let me validate, fill the gaps, and add the depth that completes the picture.

---

## What You Nailed ✅

- Zero trust — never trust, always verify
- Defense in depth — multiple security layers
- Istio service mesh — automatic mTLS within mesh
- K8s network policies — control ingress/egress
- Egress gateway — controls outbound traffic
- API Gateway — central enforcement point
- mTLS from client to API Gateway, terminate and re-initiate
- Client credentials — OAuth2 machine-to-machine
- Rate limiting, blocking, tracing at gateway

---

## The Complete Picture

---

## 1. Zero Trust — The Core Principle

```
Traditional perimeter security (castle and moat):
├── Trust everything inside the network ❌
├── Block everything outside
└── Once inside → free to move laterally ❌

Zero Trust:
├── Never trust, always verify — regardless of location
├── Every request authenticated and authorised
├── Every service proves identity
├── Least privilege — only access what's needed
└── Assume breach — limit blast radius

Your implementation layers:
Layer 1: Network    → K8s Network Policies
Layer 2: Transport  → mTLS (Istio)
Layer 3: Application→ OAuth2 / JWT
Layer 4: Gateway    → API Gateway enforcement
Layer 5: Audit      → Distributed tracing + logging
```

---

## 2. mTLS — How It Works in Detail

```
Standard TLS (one-way):
Client → Server
├── Server presents certificate
├── Client verifies server identity ✅
└── Server does NOT verify client identity ❌
    (anyone can connect)

mTLS (mutual TLS):
Client ↔ Server
├── Server presents certificate
├── Client verifies server identity ✅
├── Client presents certificate ✅
├── Server verifies client identity ✅
└── Both sides proven — mutual authentication ✅

TLS Handshake with mTLS:

Client                          Server
  │                               │
  │ ── ClientHello ─────────────► │
  │ ◄─ ServerHello ────────────── │
  │ ◄─ Server Certificate ─────── │  "I am payment-service"
  │ ◄─ CertificateRequest ─────── │  "Prove who you are"
  │ ── Client Certificate ──────► │  "I am account-service"
  │ ── ClientKeyExchange ────────► │
  │ ── Finished ─────────────────► │
  │ ◄─ Finished ────────────────── │
  │                               │
  │ ══ Encrypted channel ════════ │
  Both sides verified ✅
```

---

## 3. Istio Service Mesh — How mTLS Works

```
Without Istio — manual mTLS:
├── Every service manages its own certificates ❌
├── Certificate rotation = touching every service ❌
├── Developer must implement mTLS in code ❌
└── Operational nightmare at scale ❌

With Istio — automatic mTLS:
├── Sidecar proxy (Envoy) injected into every pod
├── Certificates issued by Istio CA (Citadel)
├── Auto-rotated every 24 hours
├── mTLS handled by sidecar — app code unaware ✅
└── Zero code changes needed ✅
```

```yaml
# Enforce strict mTLS across entire mesh
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: banking-services
spec:
  mtls:
    mode: STRICT  # reject non-mTLS traffic ✅
---
# Per-service override if needed
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: payment-service-mtls
  namespace: banking-services
spec:
  selector:
    matchLabels:
      app: payment-service
  mtls:
    mode: STRICT
```

```yaml
# Authorisation policy — what can talk to what
# Even within mesh — least privilege ✅
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: payment-service-policy
  namespace: banking-services
spec:
  selector:
    matchLabels:
      app: payment-service
  rules:
    - from:
        - source:
            # Only these services can call payment-service
            principals:
              - "cluster.local/ns/banking/sa/account-service"
              - "cluster.local/ns/banking/sa/api-gateway"
      to:
        - operation:
            methods: ["POST"]
            paths: ["/api/v1/payments/*"]
    # All other traffic rejected ✅
```

---

## 4. Kubernetes Network Policies

```yaml
# Default deny all — start with nothing allowed
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: banking-services
spec:
  podSelector: {}  # applies to all pods
  policyTypes:
    - Ingress
    - Egress
  # No rules = deny everything ✅
---
# Explicitly allow payment-service → account-service only
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: payment-to-account
  namespace: banking-services
spec:
  podSelector:
    matchLabels:
      app: account-service
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: payment-service  # only payment-service ✅
      ports:
        - protocol: TCP
          port: 8080
---
# Egress policy — control outbound traffic
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: payment-service-egress
  namespace: banking-services
spec:
  podSelector:
    matchLabels:
      app: payment-service
  policyTypes:
    - Egress
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: account-service  # can talk to account-service ✅
    - to:
        - namespaceSelector:
            matchLabels:
              name: istio-system     # can talk to Istio control plane ✅
    - to:
        - ipBlock:
            cidr: 10.0.0.0/8       # internal network only
            except:
              - 169.254.0.0/16     # block metadata endpoints ✅
```

---

## 5. Istio Egress Gateway — Controlling Outbound

```yaml
# All external traffic MUST go through egress gateway
# No direct external calls from pods ✅

# ServiceEntry — declare allowed external services
apiVersion: networking.istio.io/v1alpha3
kind: ServiceEntry
metadata:
  name: payment-gateway-external
spec:
  hosts:
    - api.stripe.com              # only this external host allowed ✅
  ports:
    - number: 443
      name: https
      protocol: HTTPS
  resolution: DNS
  location: MESH_EXTERNAL
---
# Force traffic through egress gateway
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: stripe-via-egress
spec:
  hosts:
    - api.stripe.com
  gateways:
    - mesh
    - istio-egressgateway
  http:
    - match:
        - gateways:
            - mesh
      route:
        - destination:
            host: istio-egressgateway  # via gateway ✅
    - match:
        - gateways:
            - istio-egressgateway
      route:
        - destination:
            host: api.stripe.com       # then to external ✅
```

---

## 6. API Gateway Security — North-South

```
Client → API Gateway → Services

Full security stack at gateway:

┌─────────────────────────────────────────────┐
│              API Gateway (Apigee)            │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │ TLS Termination                      │   │
│  │ mTLS from client → terminate here   │   │
│  │ Re-initiate mTLS to backend ✅        │   │
│  └──────────────────────────────────────┘   │
│  ┌──────────────────────────────────────┐   │
│  │ Authentication                       │   │
│  │ Validate JWT / OAuth2 token          │   │
│  │ Verify signature, expiry, claims     │   │
│  └──────────────────────────────────────┘   │
│  ┌──────────────────────────────────────┐   │
│  │ Authorisation                        │   │
│  │ Check client has permission          │   │
│  │ for this resource and method         │   │
│  └──────────────────────────────────────┘   │
│  ┌──────────────────────────────────────┐   │
│  │ Rate Limiting                        │   │
│  │ Per client, per endpoint             │   │
│  │ Burst + sustained limits             │   │
│  └──────────────────────────────────────┘   │
│  ┌──────────────────────────────────────┐   │
│  │ Threat Protection                    │   │
│  │ SQL injection, XSS, payload size     │   │
│  └──────────────────────────────────────┘   │
│  ┌──────────────────────────────────────┐   │
│  │ Observability                        │   │
│  │ Correlation ID, trace ID injection   │   │
│  │ Request/response logging             │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

---

## 7. OAuth2 Client Credentials — Machine-to-Machine

```
Client Credentials flow — for service-to-service:
No user involved — pure machine authentication

Service A                    Auth Server            Service B
    │                             │                     │
    │ POST /token                 │                     │
    │ client_id=svc-a             │                     │
    │ client_secret=xxx           │                     │
    │ grant_type=client_credentials│                    │
    │ scope=payments:write        │                     │
    │ ──────────────────────────► │                     │
    │                             │                     │
    │ ◄── access_token (JWT) ──── │                     │
    │     expires_in: 3600        │                     │
    │                             │                     │
    │ GET /accounts/123           │                     │
    │ Authorization: Bearer <jwt> │                     │
    │ ──────────────────────────────────────────────► │
    │                             │                     │
    │                             │  validate token     │
    │                             │ ◄─────────────────  │
    │                             │  token valid ✅      │
    │                             │ ──────────────────► │
    │ ◄── 200 OK ──────────────────────────────────── │
```

```java
// Service A — obtain and cache token
@Service
public class TokenService {

    private final OAuth2AuthorizedClientManager clientManager;

    // Spring Security handles token caching and refresh
    public String getAccessToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
            .withClientRegistrationId("payment-service")
            .principal("payment-service")
            .build();

        OAuth2AuthorizedClient client =
            clientManager.authorize(request);

        return client.getAccessToken().getTokenValue();
        // Token cached until expiry ✅
        // Auto-refreshed before expiry ✅
    }
}

// Service B — validate incoming token
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(
                        jwtAuthConverter())
                )
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/payments/**")
                    .hasAuthority("SCOPE_payments:write")
                .requestMatchers("/api/v1/accounts/**")
                    .hasAuthority("SCOPE_accounts:read")
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

---

## 8. JWT — Structure and Validation

```
JWT Structure:
Header.Payload.Signature

Header:
{
  "alg": "RS256",  // RSA signature algorithm
  "typ": "JWT",
  "kid": "key-id-123"  // which key to use for verification
}

Payload (claims):
{
  "iss": "https://auth.lloyds.internal",  // issuer
  "sub": "payment-service",               // subject (who)
  "aud": "account-service",               // audience (for whom)
  "exp": 1735689600,                      // expiry timestamp
  "iat": 1735686000,                      // issued at
  "jti": "unique-token-id",              // prevent replay
  "scope": "accounts:read payments:write",// permissions
  "client_id": "payment-service-client"  // client identifier
}

Signature:
RS256(base64(header) + "." + base64(payload), privateKey)

Validation steps:
1. Verify signature using public key ✅
2. Check expiry (exp) ✅
3. Check issuer (iss) matches expected ✅
4. Check audience (aud) matches this service ✅
5. Check jti not in replay cache ✅
6. Check required scopes present ✅
```

---

## 9. Defense in Depth — Complete Security Stack

```
LAYER 1 — Network (K8s Network Policies)
├── Default deny all ingress/egress
├── Whitelist only required service-to-service paths
└── Block direct external access

LAYER 2 — Transport (Istio mTLS)
├── All pod-to-pod traffic encrypted ✅
├── Both sides present certificates ✅
├── Certificates auto-rotated ✅
└── Non-mTLS traffic rejected (STRICT mode) ✅

LAYER 3 — Authorisation (Istio AuthPolicy)
├── Per-service allowlist of callers ✅
├── Method and path level controls ✅
└── Based on service account identity ✅

LAYER 4 — Application (OAuth2 + JWT)
├── Token required for every request ✅
├── Scope-based authorisation ✅
├── Short-lived tokens (1 hour) ✅
└── Token introspection / JWKS validation ✅

LAYER 5 — Gateway (API Gateway)
├── TLS termination + re-initiation ✅
├── Rate limiting per client ✅
├── Threat protection ✅
└── Correlation ID injection ✅

LAYER 6 — Egress (Egress Gateway)
├── All external traffic via egress gateway ✅
├── ServiceEntry whitelist for external hosts ✅
└── No direct external calls from pods ✅

LAYER 7 — Audit (Observability)
├── All requests logged with correlation ID ✅
├── Distributed traces across services ✅
├── Anomaly detection on access patterns ✅
└── Alert on policy violations ✅
```

---

## Polished 90-Second Interview Answer

*"Our security model follows zero trust — never trust, always verify, regardless of network location — with defense in depth across multiple layers.*

*At the network layer, Kubernetes network policies default-deny everything and explicitly whitelist only required service-to-service paths. This prevents lateral movement even if a service is compromised.*

*At the transport layer, Istio service mesh enforces strict mTLS across all pod-to-pod communication. Envoy sidecars handle certificate issuance, rotation, and mutual verification automatically — services are unaware, no code changes needed. Istio AuthorizationPolicy adds a second layer — even within the mesh, each service has an allowlist of which service accounts can call it and on which paths and methods.*

*For North-South traffic — client to API gateway — we terminate mTLS at the gateway and re-initiate to the backend. This allows the gateway to inspect, validate, and enforce policies before forwarding. We use OAuth2 client credentials for machine-to-machine authentication — services obtain short-lived JWTs from the auth server, and the gateway validates signature, expiry, issuer, audience, and required scopes on every request.*

*For outbound traffic, all external calls route through an Istio egress gateway with ServiceEntry whitelisting — no pod can make direct external calls. Only explicitly declared external hosts are reachable.*

*The result is layered — compromising one layer doesn't compromise the system. Network policies limit blast radius, mTLS ensures encrypted and authenticated channels, OAuth2 scopes enforce least-privilege access, and the egress gateway prevents data exfiltration even from a compromised internal service."*

---

Ready for **Question 4 — How have you implemented an API Gateway and what governance did you enforce?**

Great — let me build this drawing directly from your Lloyds Apigee experience.

---

## API Gateway in Enterprise Banking — Apigee Deep Dive

---

## 1. Why API Gateway — The Core Problem

```
Without API Gateway — chaos at enterprise scale:

Service A ──────────────────────────────► External Partner
Service B ──────────────────────────────► Mobile App
Service C ──────────────────────────────► Internal Service
Service D ──────────────────────────────► Third Party

Problems:
├── Every service implements auth independently ❌
├── Every service implements rate limiting independently ❌
├── No central visibility of API traffic ❌
├── No consistent error format ❌
├── No versioning strategy ❌
├── Security enforced inconsistently ❌
├── Onboarding new consumers = touch every service ❌
└── Audit trail fragmented across services ❌

With API Gateway — centralised control plane:

All traffic ──► API Gateway ──► Services

├── Auth enforced once — centrally ✅
├── Rate limiting — centrally ✅
├── Single observability point ✅
├── Consistent error format ✅
├── Versioning managed centrally ✅
└── Consumer onboarding = register once ✅
```

---

## 2. Apigee Architecture — Enterprise Banking Context

```
Lloyds Banking — Apigee Topology:

External Zone:
┌─────────────────────────────────────────────────┐
│  Internet                                        │
│  ├── Mobile Apps (iOS/Android)                   │
│  ├── Web Browser                                 │
│  ├── B2B Partners (corporates, fintechs)         │
│  └── Open Banking TPPs (third party providers)   │
└──────────────────────┬──────────────────────────┘
                       │ HTTPS/mTLS
                       ▼
┌─────────────────────────────────────────────────┐
│  DMZ — Apigee Edge (Runtime Plane)              │
│  ┌───────────────────────────────────────────┐  │
│  │  Router / Message Processor               │  │
│  │  ├── TLS Termination                      │  │
│  │  ├── Policy Execution                     │  │
│  │  ├── Traffic Management                   │  │
│  │  └── Analytics capture                    │  │
│  └───────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────┘
                       │ mTLS (re-initiated)
                       ▼
┌─────────────────────────────────────────────────┐
│  Internal Zone — Backend Services               │
│  ├── Account Service                            │
│  ├── Payment Service                            │
│  ├── Savings Service                            │
│  └── Core Banking (legacy)                      │
└─────────────────────────────────────────────────┘

Apigee Control Plane (separate):
├── API proxy management
├── Developer portal
├── Analytics dashboard
├── Policy configuration
└── Key/secret management
```

---

## 3. Proxy Archetypes — Your 4-5 Patterns

```
You mentioned 4-5 reusable proxy archetypes.
Here's what they look like in banking:

ARCHETYPE 1 — Public B2C (Browser/Mobile)
Target: End customers via mobile app / web
Auth: OAuth2 Authorization Code flow
      (user logs in, gets token)
Security: Rate limiting per user, OWASP threat protection
Example: /api/v1/accounts, /api/v1/payments

ARCHETYPE 2 — B2B Partner (Corporate)
Target: Corporate clients, fintechs, aggregators
Auth: OAuth2 Client Credentials + mTLS
      (machine-to-machine, certificate pinned)
Security: IP allowlist, strict rate limits per client
Example: /b2b/v1/bulk-payments, /b2b/v1/account-info

ARCHETYPE 3 — Open Banking (TPP)
Target: FCA-regulated Third Party Providers
Auth: OAuth2 + FAPI (Financial API) profile
      eIDAS certificates for client auth
Security: Regulatory compliance controls
          Consent validation
Example: /open-banking/v3/accounts, /open-banking/v3/payments

ARCHETYPE 4 — Internal Service (East-West)
Target: Internal microservices
Auth: Client Credentials (service accounts)
      Istio mTLS (transport level)
Security: Service allowlist, no internet exposure
Example: /internal/v1/account-enrichment

ARCHETYPE 5 — Legacy Adapter (Strangler Fig)
Target: Legacy monolith backends
Auth: Inherited from calling archetype
Transform: Modern JSON ↔ Legacy XML/SOAP translation
Example: /api/v1/core-banking/* → legacy SOAP endpoint
```

---

## 4. Policy Execution Pipeline — How Apigee Works

```
Every request flows through policy pipeline:

Request ──► ProxyEndpoint ──► TargetEndpoint ──► Backend
           (inbound)         (outbound)

PreFlow → Conditional Flows → PostFlow

┌─────────────────────────────────────────────┐
│  ProxyEndpoint — Request PreFlow            │
│                                             │
│  1. Spike Arrest                            │
│     └── Hard limit: 1000 TPS               │
│         Protect backend from burst ✅        │
│                                             │
│  2. Verify API Key / OAuth Token            │
│     └── Validate JWT signature              │
│         Check expiry, issuer, audience ✅   │
│                                             │
│  3. Extract JWT Claims                      │
│     └── clientId, scopes, userId           │
│         into flow variables for later ✅    │
│                                             │
│  4. Quota / Rate Limit                      │
│     └── 1000 calls/hour per clientId ✅     │
│                                             │
│  5. Threat Protection                       │
│     └── JSON/XML threat protection          │
│         Max payload size: 1MB               │
│         SQL injection patterns blocked ✅   │
│                                             │
│  6. Request Validation                      │
│     └── Required headers present            │
│         Content-Type correct ✅             │
│                                             │
│  7. Correlation ID Injection                │
│     └── X-Correlation-ID header            │
│         X-Client-ID from token             │
│         X-Request-ID generated ✅           │
└─────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────┐
│  Conditional Flows — Route by Path          │
│                                             │
│  /accounts → Account Service               │
│  /payments → Payment Service               │
│  /legacy/* → Core Banking (transform)      │
└─────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────┐
│  TargetEndpoint — Request PostFlow          │
│                                             │
│  8. mTLS to Backend                         │
│     └── Re-initiate mTLS ✅                 │
│                                             │
│  9. Strip External Headers                  │
│     └── Remove client headers              │
│         not safe for backend ✅             │
│                                             │
│  10. Add Internal Headers                   │
│      └── X-Internal-Client-ID              │
│          X-Correlation-ID                  │
│          X-Forwarded-For ✅                 │
└─────────────────────────────────────────────┘
          │
          ▼ (Backend processes)
          │
┌─────────────────────────────────────────────┐
│  TargetEndpoint — Response PreFlow          │
│                                             │
│  11. Error Normalisation                    │
│      └── Map backend errors to             │
│          standard error schema ✅           │
│                                             │
│  12. Response Transformation                │
│      └── Add/remove fields                 │
│          Legacy XML → JSON ✅               │
└─────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────┐
│  ProxyEndpoint — Response PostFlow          │
│                                             │
│  13. Security Headers                       │
│      └── CORS headers                      │
│          HSTS, X-Frame-Options ✅           │
│                                             │
│  14. Analytics                              │
│      └── Capture metrics                   │
│          Response time, status code ✅      │
│                                             │
│  15. Cache Response (if cacheable)          │
│      └── GET responses cached at edge ✅    │
└─────────────────────────────────────────────┘
          │
          ▼
       Client ✅
```

---

## 5. Governance Standards — What You Enforced

### API Design Standards
```yaml
# Enforced via Apigee + OpenAPI linting in CI/CD

URL Standards:
├── Lowercase kebab-case: /account-holders ✅
├── Plural nouns: /accounts not /account ✅
├── No verbs in path: /accounts/123 not /getAccount ✅
├── Versioned: /api/v1/accounts ✅
└── Resource hierarchy: /accounts/{id}/transactions ✅

HTTP Method Standards:
├── GET    → read, idempotent, cacheable
├── POST   → create, non-idempotent
├── PUT    → full update, idempotent
├── PATCH  → partial update
└── DELETE → remove, idempotent

Response Standards:
├── 200 → success with body
├── 201 → created (POST)
├── 204 → success no body (DELETE)
├── 400 → client error (validation)
├── 401 → unauthenticated
├── 403 → unauthorised (valid token, wrong permission)
├── 404 → not found
├── 409 → conflict (duplicate)
├── 422 → unprocessable (business rule)
├── 429 → rate limited
└── 500 → server error

Standard Error Schema (enforced at gateway):
{
  "errorCode": "ACCOUNT_NOT_FOUND",
  "message": "Account not found",
  "correlationId": "abc-123",
  "timestamp": "2025-05-06T10:30:00Z",
  "path": "/api/v1/accounts/ACC-001"
}
```

### API Lifecycle Governance
```
API Lifecycle:
DRAFT → REVIEW → APPROVED → PUBLISHED → DEPRECATED → RETIRED

┌─────────────────────────────────────────────────────┐
│  Governance Gates                                    │
│                                                      │
│  DRAFT:                                              │
│  ├── OpenAPI spec created ✅                         │
│  ├── Design review by API CoE ✅                     │
│  └── Security review ✅                              │
│                                                      │
│  REVIEW:                                             │
│  ├── Automated linting (Spectral rules) ✅           │
│  ├── Breaking change detection ✅                    │
│  ├── Consumer impact assessment ✅                   │
│  └── Penetration test for new endpoints ✅           │
│                                                      │
│  PUBLISHED:                                          │
│  ├── Registered in API catalogue ✅                  │
│  ├── Developer portal docs updated ✅                │
│  └── SLA defined and monitored ✅                    │
│                                                      │
│  DEPRECATED:                                         │
│  ├── Consumers notified (min 6 months notice) ✅     │
│  ├── Migration guide published ✅                    │
│  └── Sunset date in response header ✅               │
└─────────────────────────────────────────────────────┘
```

### Consumer Onboarding Governance
```java
// Consumer registers in developer portal
// Self-service but governed

// App registration creates:
AppRegistration {
    appId: "corporate-payments-app",
    clientId: "auto-generated-uuid",
    clientSecret: "auto-generated-secret",
    allowedAPIs: [
        "accounts-api:read",
        "payments-api:write"
    ],
    rateLimits: {
        "tier": "GOLD",         // Bronze/Silver/Gold tiers
        "requestsPerHour": 10000,
        "burstLimit": 500
    },
    ipAllowlist: ["203.0.113.0/24"],
    environment: "PRODUCTION",
    contactEmail: "team@corporate.com"
}

// Rate limit tiers
BRONZE: 1,000 req/hour  → startups, internal low-priority
SILVER: 10,000 req/hour → established partners
GOLD:   100,000 req/hour → strategic partners, internal critical
```

---

## 6. Versioning Strategy

```
API Versioning — URI versioning (your approach):

/api/v1/accounts  → current stable version
/api/v2/accounts  → new version (breaking changes)

// Both versions live simultaneously
// v1 deprecated with sunset header
// Consumers migrate at own pace

// Apigee routes by version prefix:
Conditional Flow:
  path starts with /api/v1 → AccountServiceV1 target
  path starts with /api/v2 → AccountServiceV2 target

// Sunset header on deprecated version
response.headers["Sunset"] = "Sat, 31 Dec 2025 23:59:59 GMT"
response.headers["Deprecation"] = "true"
response.headers["Link"] = '</api/v2/accounts>; rel="successor-version"'

// Non-breaking changes — same version ✅
// New optional fields in response
// New optional query parameters
// New endpoints

// Breaking changes — new version ❌→✅
// Removing fields
// Changing field types
// Changing URL structure
// Changing auth mechanism
```

---

## 7. Observability at Gateway Level

```java
// Every request captured in Apigee Analytics

Metrics captured per request:
├── clientId          → who called
├── apiProxy          → which proxy
├── targetEndpoint    → which backend
├── requestPath       → endpoint called
├── httpMethod        → GET/POST etc
├── responseCode      → 200/400/500
├── responseTime      → end-to-end latency
├── requestSize       → payload size
├── responseSize      → response size
├── correlationId     → trace across services
└── gatewayTime       → time spent in gateway

// Dashboards built on this:
├── API usage by consumer — who's calling most
├── Error rate by endpoint — which APIs failing
├── Latency percentiles — P50/P95/P99
├── Rate limit hits — consumers hitting quota
├── Top consumers by volume
└── Anomaly detection — unusual traffic patterns

// Alerts:
├── Error rate > 1% → page on-call ✅
├── Latency P99 > SLO → alert ✅
├── Rate limit hit rate > 10% → alert consumer ✅
└── 401/403 spike → potential security incident ✅
```

---

## 8. CI/CD Integration — API as Code

```yaml
# API proxy managed as code in Git
# Deployed via CI/CD pipeline ✅

# Pipeline stages:
stages:
  - lint          # OpenAPI spec validation
  - security-scan # OWASP checks on proxy config
  - test          # Unit test proxy policies
  - deploy-dev    # Deploy to dev environment
  - integration   # Run API integration tests
  - deploy-staging
  - contract-test # Consumer contract tests (Pact)
  - deploy-prod   # Blue-green deployment ✅
  - smoke-test    # Verify production deployment

# Apigee proxy deployment
- name: Deploy API Proxy
  run: |
    apigeetool deployproxy \
      --org lloyds-banking \
      --env production \
      --name accounts-api-v2 \
      --directory ./proxies/accounts-v2 \
      --token $APIGEE_TOKEN

# Automated rollback if smoke tests fail
- name: Rollback if failed
  if: failure()
  run: |
    apigeetool undeploy \
      --org lloyds-banking \
      --env production \
      --name accounts-api-v2 \
      --revision $PREVIOUS_REVISION
```

---

## Complete Summary

```
Apigee Enterprise Banking — Key Components:

1. PROXY ARCHETYPES (4-5 patterns)
   └── B2C, B2B, Open Banking, Internal, Legacy Adapter
   └── Reusable — teams use templates, not build from scratch

2. POLICY PIPELINE
   └── Spike arrest → Auth → Rate limit → Threat protection
   └── Correlation ID injection → mTLS to backend
   └── Error normalisation → Security headers → Analytics

3. GOVERNANCE
   └── API design standards (URL, methods, errors)
   └── Lifecycle: Draft → Review → Published → Deprecated
   └── Consumer onboarding — self-service but governed
   └── Versioning strategy — URI versioning, breaking changes = new version

4. SECURITY
   └── TLS termination + re-initiation
   └── OAuth2 + JWT validation
   └── mTLS for B2B partners
   └── IP allowlisting
   └── OWASP threat protection

5. OBSERVABILITY
   └── Per-request analytics capture
   └── Dashboards: usage, errors, latency, rate limits
   └── Alerts on SLO breach

6. CI/CD
   └── Proxy as code in Git
   └── Automated lint, test, deploy, rollback
```

---

## Polished 90-Second Interview Answer

*"At Lloyds we built a greenfield enterprise API platform on Apigee — federated model where teams delivered independently within centralised governance and security controls.*

*The foundation was five reusable proxy archetypes — B2C for mobile and web consumers using OAuth2 authorization code flow, B2B for corporate partners with client credentials and mTLS certificate pinning, Open Banking for FCA-regulated TPPs following FAPI profile, internal service-to-service proxies, and a legacy adapter archetype that translated modern JSON to legacy SOAP for core banking integration. Teams used these templates rather than building from scratch — consistent security and governance baked in.*

*The policy pipeline enforced a standard stack on every request — spike arrest to protect backends from burst traffic, OAuth2/JWT validation with claim extraction, quota enforcement per consumer tier, OWASP threat protection, and correlation ID injection. On the way out, TLS was re-initiated to backends, standard error schemas normalised inconsistent backend responses, and security headers added.*

*Governance covered the full API lifecycle — design standards enforced through automated OpenAPI linting in CI/CD, breaking change detection preventing silent contract violations, and a mandatory review gate before production. Versioning followed URI versioning — breaking changes meant a new version, with sunset headers on deprecated versions and minimum six months migration notice to consumers.*

*Consumer onboarding was self-service through the developer portal but governed — app registration created a clientId with explicitly scoped API access, rate limit tier assignment, and IP allowlisting for B2B partners. All gateway traffic fed into analytics dashboards giving us per-consumer usage, error rates, latency percentiles, and rate limit utilisation — with alerts on SLO breaches and anomalous traffic patterns."*

---

