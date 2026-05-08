Great — Area 2 begins. One question at a time, just like the real interview.

Question 1:
"Walk me through how you'd design a real-time event-driven system for high throughput and low latency. Use a concrete example from your experience."

Outstanding answer — this is genuinely senior-level thinking. You started with business requirements, made technology choices with clear rationale, and covered the key engineering concerns. Let me validate, fill the gaps, and add the depth that makes this a complete answer.

---

## What You Nailed ✅

- Started with business requirements before technology — exactly right
- CAP theorem reasoning — AP for read store
- Cloud Spanner choice with clear rationale
- Kafka partitioning by accountId — ordering + scalability
- Horizontal consumer scaling bounded by partition count
- Idempotency with monotonic versioning
- Non-blocking retries — retry topic + DLQ
- Explicit ack — no message loss
- Spring Kafka vs KStreams — conscious choice with rationale

---

## The Complete Picture — Filling the Gaps

---

## 1. Requirements Framework — How to Start Every Design

```
Before any technology choice, answer these questions:

CONSISTENCY
├── Strong consistency required?     → affects DB choice
├── Eventual consistency acceptable? → enables higher availability
└── Read-your-own-writes required?   → affects caching strategy

DELIVERY SEMANTICS
├── At-most-once  → possible message loss, fastest
├── At-least-once → possible duplicates, most common ✅
└── Exactly-once  → no loss, no duplicates, most complex/expensive

ORDERING
├── Global ordering    → single partition, no scalability
├── Entity-level order → partition by entity ID ✅ (your choice)
└── No ordering        → maximum parallelism

AVAILABILITY vs CONSISTENCY (CAP)
├── CP → consistency + partition tolerance (sacrifice availability)
└── AP → availability + partition tolerance (sacrifice consistency) ✅

LATENCY SLA
├── Sub-100ms  → in-memory, minimal hops
├── Sub-second → Kafka + optimised consumers ✅
└── Near-real-time (seconds) → batch micro-batching acceptable

THROUGHPUT
├── Events per second
├── Peak vs average load
└── Burst handling strategy
```

---

## 2. Your Architecture — Fleshed Out

```
Commercial Banking Read Store — CQRS Pipeline:

Write Side (Command):
┌─────────────┐    ┌──────────────┐    ┌─────────────────┐
│   Banking   │───►│  Outbox      │───►│   Kafka Topic   │
│   Service   │    │  Table       │    │ account-events  │
│  (Spanner)  │    │  (Spanner)   │    │ partitioned by  │
└─────────────┘    └──────────────┘    │   accountId     │
                                        └────────┬────────┘
                                                 │
                        ┌────────────────────────┼──────────────────────┐
                        │                        │                      │
                   Partition 0              Partition 1           Partition N
                        │                        │                      │
Read Side (Query):       ▼                        ▼                      ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    Consumer Group (K8s pods)                          │
│  ┌─────────────┐  ┌─────────────┐              ┌─────────────┐       │
│  │ Consumer 0  │  │ Consumer 1  │   . . .       │ Consumer N  │       │
│  │ (pod)       │  │ (pod)       │              │ (pod)       │       │
│  └──────┬──────┘  └──────┬──────┘              └──────┬──────┘       │
└─────────┼───────────────┼────────────────────────────┼───────────────┘
          │               │                            │
          ▼               ▼                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Read Store (Spanner)                               │
│              account_balances, transactions, positions                │
└─────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────┐
│   Query APIs    │
│  (read-only)    │
└─────────────────┘
```

---

## 3. Outbox Pattern — The Missing Piece

You mentioned Kafka but the critical question is — **how do events get onto Kafka reliably from the write side?**

```java
// PROBLEM — dual write is not atomic ❌
@Transactional
public void processTransaction(Transaction txn) {
    // Write 1 — save to database
    transactionRepo.save(txn);

    // Write 2 — publish to Kafka
    kafkaTemplate.send("account-events", txn);
    // What if service crashes between these two? ❌
    // DB saved but Kafka never got the event
    // Read store permanently out of sync
}

// SOLUTION — Outbox Pattern ✅
// Write event to outbox table IN SAME TRANSACTION as business data
@Transactional
public void processTransaction(Transaction txn) {
    // Business data
    transactionRepo.save(txn);

    // Outbox entry — same transaction, same commit ✅
    OutboxEvent event = OutboxEvent.builder()
        .aggregateId(txn.getAccountId())
        .eventType("TRANSACTION_CREATED")
        .payload(serialize(txn))
        .createdAt(Instant.now())
        .status(OutboxStatus.PENDING)
        .build();
    outboxRepo.save(event);
    // Both committed atomically — no dual write problem ✅
}

// Separate outbox publisher — reads outbox and publishes to Kafka
@Component
public class OutboxPublisher {

    @Scheduled(fixedDelay = 100)  // every 100ms
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepo
            .findByStatusOrderByCreatedAt(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(
                    "account-events",
                    event.getAggregateId(),  // partition key = accountId ✅
                    event.getPayload()
                ).get();  // wait for ack

                outboxRepo.updateStatus(event.getId(), OutboxStatus.PUBLISHED);

            } catch (Exception e) {
                log.error("Failed to publish event: {}", event.getId(), e);
                // Will retry on next scheduled run ✅
            }
        }
    }
}
```

---

## 4. Kafka Configuration — Production Settings

```java
// Producer config — at-least-once delivery
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Durability — wait for all replicas to ack
        config.put(ProducerConfig.ACKS_CONFIG, "all");  // ✅

        // Retry on transient failures
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        // Idempotent producer — prevents duplicate messages on retry
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // ✅

        // Batching — improves throughput
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);   // 16KB
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);        // wait 5ms to batch

        // Compression — reduces network/storage
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        return new DefaultKafkaProducerFactory<>(config);
    }
}
```

```java
// Consumer config — explicit ack, at-least-once
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "account-read-store-consumer");

        // Manual ack — explicit control over offset commit ✅
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // How many records to fetch per poll
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);

        // Max time between polls before rebalance
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        // Start from beginning if no offset — for replay
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
            kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Concurrency = number of partitions
        factory.setConcurrency(12);  // matches partition count

        // Manual ack mode ✅
        factory.getContainerProperties()
            .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Error handler — non-blocking retry
        factory.setCommonErrorHandler(errorHandler());

        return factory;
    }
}
```

---

## 5. Non-Blocking Retry — Your Pattern with Code

```java
// Topic structure
// account-events          → main topic
// account-events-retry-1  → first retry (delay 1min)
// account-events-retry-2  → second retry (delay 5min)
// account-events-retry-3  → third retry (delay 30min)
// account-events-DLQ      → dead letter after all retries exhausted

@Component
public class AccountEventConsumer {

    @KafkaListener(
        topics = "account-events",
        groupId = "account-read-store-consumer"
    )
    public void consume(
            ConsumerRecord<String, String> record,
            Acknowledgment ack) {

        try {
            AccountEvent event = deserialize(record.value());

            // Idempotency check — already processed? ✅
            if (eventProcessingRepo.isAlreadyProcessed(
                    event.getEventId())) {
                log.info("Duplicate event ignored: {}", event.getEventId());
                ack.acknowledge();
                return;
            }

            processEvent(event);

            // Mark as processed — monotonic version check
            eventProcessingRepo.markProcessed(
                event.getEventId(),
                event.getVersion()
            );

            ack.acknowledge();  // explicit ack ✅

        } catch (NonRetryableException e) {
            // Business error — don't retry, send to DLQ
            log.error("Non-retryable error, sending to DLQ: {}",
                record.key(), e);
            sendToDLQ(record, e);
            ack.acknowledge();  // ack to move past this record

        } catch (RetryableException e) {
            // Transient error — don't ack, let retry handle it
            log.warn("Retryable error for record: {}, retry count: {}",
                record.key(),
                getRetryCount(record),
                e);
            // Don't ack — will be retried
            throw e;
        }
    }
}

// Non-blocking retry error handler
@Bean
public DefaultErrorHandler errorHandler() {
    // Retry topic routing
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> {
                int retryCount = getRetryCount(record);

                if (retryCount < 1) {
                    return new TopicPartition(
                        "account-events-retry-1", -1);
                } else if (retryCount < 2) {
                    return new TopicPartition(
                        "account-events-retry-2", -1);
                } else if (retryCount < 3) {
                    return new TopicPartition(
                        "account-events-retry-3", -1);
                } else {
                    return new TopicPartition(
                        "account-events-DLQ", -1);  // exhausted
                }
            });

    // Backoff between retries
    ExponentialBackOffWithMaxRetries backOff =
        new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(1000);   // 1s
    backOff.setMultiplier(5.0);         // 1s, 5s, 25s
    backOff.setMaxInterval(30000);      // cap at 30s

    DefaultErrorHandler handler =
        new DefaultErrorHandler(recoverer, backOff);

    // These exceptions go straight to DLQ — no retry
    handler.addNotRetryableExceptions(
        InvalidEventException.class,
        DeserializationException.class
    );

    return handler;
}
```

---

## 6. Idempotency — Monotonic Versioning Detail

```java
// Every event carries a monotonically increasing version
public class AccountEvent {
    private String eventId;        // UUID — unique per event
    private String accountId;      // partition key
    private long version;          // monotonically increasing
    private String eventType;
    private Instant occurredAt;
    private Object payload;
}

// Read store processing — version-based idempotency
@Service
public class AccountReadStoreUpdater {

    @Transactional
    public void processEvent(AccountEvent event) {
        AccountReadModel current = readStore
            .findById(event.getAccountId())
            .orElse(AccountReadModel.empty(event.getAccountId()));

        // Version check — reject stale/duplicate events ✅
        if (event.getVersion() <= current.getLastProcessedVersion()) {
            log.info("Stale event ignored: accountId={}, " +
                     "eventVersion={}, currentVersion={}",
                event.getAccountId(),
                event.getVersion(),
                current.getLastProcessedVersion());
            return;  // idempotent — safe to ignore ✅
        }

        // Process event
        AccountReadModel updated = applyEvent(current, event);
        updated.setLastProcessedVersion(event.getVersion());

        readStore.save(updated);
    }

    private AccountReadModel applyEvent(
            AccountReadModel current, AccountEvent event) {
        return switch (event.getEventType()) {
            case "TRANSACTION_CREATED" -> applyTransaction(current, event);
            case "ACCOUNT_UPDATED"     -> applyAccountUpdate(current, event);
            case "ACCOUNT_CLOSED"      -> applyAccountClosure(current, event);
            default -> {
                log.warn("Unknown event type: {}", event.getEventType());
                yield current;
            }
        };
    }
}
```

---

## 7. Consumer Scaling on Kubernetes

```yaml
# K8s HPA — scale consumers based on Kafka lag
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: account-consumer-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: account-event-consumer
  minReplicas: 3
  maxReplicas: 12        # matches partition count — hard ceiling
  metrics:
    - type: External
      external:
        metric:
          name: kafka_consumer_lag
          selector:
            matchLabels:
              topic: account-events
        target:
          type: AverageValue
          averageValue: 1000   # scale up if lag > 1000 messages per pod
```

```
Scaling behaviour:
3 partitions  → max 3 consumer pods (1:1 mapping)
12 partitions → max 12 consumer pods

Beyond partition count:
14 pods for 12 partitions → 2 pods idle ❌
Always: max consumers = partition count
```

---

## 8. Observability — What to Monitor

```java
// Key metrics for event-driven system health

// Consumer lag — most important metric
// High lag = consumers falling behind producers
// Alert: lag > 10000 messages for > 5 minutes

// Processing rate — events per second
// Sudden drop = consumer issue

// Error rate — DLQ growth rate
// Any DLQ growth = investigate immediately

// End-to-end latency — event produced to read store updated
// Your SLA was sub-second — measure this explicitly

@Component
public class KafkaMetricsPublisher {

    private final MeterRegistry registry;

    @Scheduled(fixedRate = 10000)
    public void publishLagMetrics() {
        Map<TopicPartition, Long> lag = calculateConsumerLag();

        lag.forEach((partition, lagValue) ->
            registry.gauge(
                "kafka.consumer.lag",
                Tags.of(
                    "topic", partition.topic(),
                    "partition", String.valueOf(partition.partition())
                ),
                lagValue
            )
        );
    }
}
```

---

## Complete Architecture Summary

```
Event-Driven Read Store — Key Design Decisions:

1. REQUIREMENTS FIRST
   └── AP consistency, eventual read store,
       entity-level ordering, sub-second latency

2. OUTBOX PATTERN
   └── Atomic write to DB + outbox table
   └── Separate publisher → Kafka
   └── Eliminates dual-write problem

3. KAFKA
   └── Partition by accountId → ordering + parallelism
   └── Idempotent producer + acks=all → durability
   └── Snappy compression → throughput

4. CONSUMERS
   └── Horizontal scaling → bounded by partition count
   └── Explicit ack → no message loss
   └── Idempotency → monotonic version check
   └── Non-blocking retry → retry topics + DLQ

5. READ STORE (Spanner)
   └── AP, eventual consistency
   └── Auto-failover, global scale
   └── Version-based updates

6. KUBERNETES
   └── HPA based on consumer lag
   └── Max replicas = partition count

7. OBSERVABILITY
   └── Consumer lag, DLQ growth,
       end-to-end latency, processing rate
```

---

## Polished 90-Second Interview Answer

*"Designing a real-time event-driven system starts with business requirements — consistency model, delivery semantics, ordering requirements, latency SLA, and CAP trade-offs. These drive every technology choice.*

*For our CQRS read store at Lloyds, we chose AP — eventual consistency for the read store was acceptable as long as data was always correct once propagated. Cloud Spanner gave us global scalability, automatic replication, and tunable consistency.*

*Events flow from the write side via the Outbox pattern — we write business data and an outbox event atomically in the same Spanner transaction, eliminating the dual-write problem. A separate publisher polls the outbox and delivers to Kafka.*

*Kafka is partitioned by accountId — this gives us account-level ordering while allowing horizontal parallelism across partitions. Consumers are stateless pods on Kubernetes, scaled by consumer lag via HPA, with a hard ceiling matching partition count.*

*Consumers use explicit acknowledgement — no auto-commit, so no message is lost if a pod crashes mid-processing. Idempotency is enforced through monotonic versioning — if an event version is less than or equal to what's already in the read store, it's silently ignored.*

*For error handling, we use non-blocking retries — retryable errors like transient network failures go to retry topics with exponential backoff, while non-retryable business errors go straight to DLQ. This decongests the main pipeline and prevents one bad message from blocking others.*

*Key metrics we monitor: consumer lag, DLQ growth rate, end-to-end event latency from production to read store update, and processing rate per partition."*

---

Ready for **Question 2 — How did you apply CQRS and Event Sourcing? What trade-offs did you make?**

Excellent — you've described a sophisticated Event Sourcing implementation. Let me validate, connect the dots clearly, and add the depth that makes this a complete answer.

---

## What You Nailed ✅

- Immutable event log as source of truth
- Snapshots to avoid replaying full history
- Event log for auditability — how did record reach state X
- At-least-once + idempotency — no duplicate log entries
- Outbox + main table in same DB — ACID atomicity
- Optimistic locking for concurrency control
- Ability to rebuild state by replaying events

---

## Clarifying the Architecture First

Let me make sure we're describing this precisely — because CQRS and Event Sourcing are often confused:

```
CQRS — separates read and write models
├── Command side  → handles writes, owns source of truth
└── Query side    → handles reads, optimised read store

Event Sourcing — how state is stored on command side
├── Instead of storing CURRENT state only
└── Store EVERY EVENT that led to current state
    State is derived by replaying events

Your architecture combined both:

COMMAND SIDE (Event Sourcing):
┌─────────────────────────────────────────┐
│  AccountAggregate                        │
│  ┌─────────────────────────────────┐    │
│  │  Event Log (immutable)          │    │
│  │  AccountOpened    v1            │    │
│  │  MoneyDeposited   v2            │    │
│  │  MoneyWithdrawn   v3            │    │
│  │  InterestApplied  v4            │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │  Current State (derived)        │    │
│  │  balance: 750, status: ACTIVE   │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │  Snapshot (periodic)            │    │
│  │  At v100: balance=1000          │    │
│  │  Replay only v101 onwards       │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
          │
          │ events published via Kafka
          ▼
QUERY SIDE (CQRS):
┌─────────────────────────────────────────┐
│  Read Store (Spanner)                    │
│  Optimised for queries                   │
│  Eventually consistent                   │
└─────────────────────────────────────────┘
```

---

## 1. Event Log — Immutable Append-Only Store

```java
// Event log table — append only, never update or delete
@Entity
@Table(name = "account_event_log")
public class AccountEventLog {

    @Id
    private String eventId;           // UUID — globally unique

    private String accountId;         // aggregate ID

    @Column(nullable = false)
    private long version;             // monotonically increasing per account

    private String eventType;         // ACCOUNT_OPENED, MONEY_DEPOSITED etc

    @Column(columnDefinition = "TEXT")
    private String payload;           // serialised event data (JSON)

    private Instant occurredAt;       // when event happened

    private String correlationId;     // request that triggered it

    private boolean processed;        // idempotency flag ✅

    // NEVER update existing rows
    // NEVER delete rows
    // ONLY append new rows ✅
}

// Repository — append only
public interface AccountEventLogRepository
        extends JpaRepository<AccountEventLog, String> {

    // Find events for account after version (for replay)
    List<AccountEventLog> findByAccountIdAndVersionGreaterThan(
        String accountId, long version);

    // Find latest processed version
    Optional<Long> findMaxVersionByAccountId(String accountId);

    // Check if event already processed — idempotency
    boolean existsByEventIdAndProcessedTrue(String eventId);
}
```

---

## 2. Idempotent Event Processing — Your Key Point

```java
@Service
public class AccountEventProcessor {

    @Transactional  // ACID — both operations atomic ✅
    public void processEvent(AccountEvent event) {

        // IDEMPOTENCY CHECK — already processed?
        if (eventLogRepo.existsByEventIdAndProcessedTrue(event.getEventId())) {
            log.info("Duplicate event ignored: {}", event.getEventId());
            return;  // silent ignore — safe ✅
        }

        // OPTIMISTIC LOCKING — concurrent update protection
        AccountState current = accountStateRepo
            .findById(event.getAccountId())
            .orElse(AccountState.empty(event.getAccountId()));

        // Version check — reject stale concurrent updates
        if (event.getVersion() != current.getVersion() + 1) {
            throw new OptimisticLockingException(
                String.format(
                    "Version conflict: expected %d, got %d",
                    current.getVersion() + 1,
                    event.getVersion()
                )
            );
        }

        // Apply event to derive new state
        AccountState newState = applyEvent(current, event);

        // ATOMIC write — both in same DB transaction ✅
        // 1. Update current state
        accountStateRepo.save(newState);

        // 2. Append to event log — ONLY on successful processing
        AccountEventLog logEntry = AccountEventLog.builder()
            .eventId(event.getEventId())
            .accountId(event.getAccountId())
            .version(event.getVersion())
            .eventType(event.getEventType())
            .payload(serialize(event))
            .occurredAt(event.getOccurredAt())
            .correlationId(MDC.get("correlationId"))
            .processed(true)
            .build();

        eventLogRepo.save(logEntry);
        // Both committed atomically — ACID ✅
        // If anything fails → both rolled back
        // No partial state ✅
    }
}
```

---

## 3. Optimistic Locking — How It Works

```java
// Optimistic locking — no DB lock held
// Instead: check version hasn't changed since we read it

@Entity
public class AccountState {
    private String accountId;
    private double balance;
    private AccountStatus status;

    @Version  // JPA optimistic locking ✅
    private long version;
    // Hibernate automatically:
    // - Increments version on every update
    // - Adds WHERE version = ? to UPDATE statement
    // - Throws OptimisticLockException if version mismatch
}

// What happens under the hood:
// Thread 1 reads: accountId=ACC-001, balance=1000, version=5
// Thread 2 reads: accountId=ACC-001, balance=1000, version=5

// Thread 1 updates:
// UPDATE accounts SET balance=900, version=6
// WHERE accountId='ACC-001' AND version=5  ← optimistic check
// Rows affected: 1 → SUCCESS ✅

// Thread 2 updates (stale version):
// UPDATE accounts SET balance=800, version=6
// WHERE accountId='ACC-001' AND version=5  ← version is now 6!
// Rows affected: 0 → OptimisticLockException thrown ❌
// Thread 2 must retry with fresh data ✅
```

**Why optimistic over pessimistic locking:**
```
Pessimistic locking:
├── SELECT FOR UPDATE → holds DB lock
├── Other threads BLOCK waiting for lock
├── Risk of deadlocks
├── Poor performance under high concurrency
└── Not suitable for distributed systems

Optimistic locking:
├── No DB lock held ✅
├── Conflict detection at commit time
├── Rare conflicts → retry is cheap
├── High concurrency — threads don't block each other ✅
└── Better throughput for read-heavy workloads ✅

Your use case — account updates:
Conflicts are RARE (same account rarely updated simultaneously)
→ Optimistic locking is the right choice ✅
```

---

## 4. Snapshots — Preventing Full Replay

```java
// Problem — event log grows forever
// Rebuilding state = replaying ALL events from beginning
// Account with 10 years of history = millions of events
// Replay time: hours ❌

// Solution — periodic snapshots ✅
@Entity
public class AccountSnapshot {
    private String snapshotId;
    private String accountId;
    private long version;          // event version at snapshot time
    private String stateJson;      // complete serialised state
    private Instant createdAt;
}

// Snapshot service
@Service
public class SnapshotService {

    private static final int SNAPSHOT_THRESHOLD = 100;
    // Take snapshot every 100 events

    @Transactional
    public void maybeSnapshot(String accountId) {
        AccountState currentState = accountStateRepo
            .findById(accountId)
            .orElseThrow();

        // Check if snapshot needed
        long lastSnapshotVersion = snapshotRepo
            .findLatestVersionByAccountId(accountId)
            .orElse(0L);

        long eventsSinceSnapshot =
            currentState.getVersion() - lastSnapshotVersion;

        if (eventsSinceSnapshot >= SNAPSHOT_THRESHOLD) {
            AccountSnapshot snapshot = AccountSnapshot.builder()
                .snapshotId(UUID.randomUUID().toString())
                .accountId(accountId)
                .version(currentState.getVersion())
                .stateJson(serialize(currentState))
                .createdAt(Instant.now())
                .build();

            snapshotRepo.save(snapshot);
            log.info("Snapshot created for account: {} at version: {}",
                accountId, currentState.getVersion());
        }
    }
}
```

**Rebuilding state from snapshot + events:**
```java
@Service
public class AccountStateRebuilder {

    public AccountState rebuild(String accountId) {
        // Step 1 — find latest snapshot
        Optional<AccountSnapshot> snapshot = snapshotRepo
            .findLatestByAccountId(accountId);

        AccountState state;
        long fromVersion;

        if (snapshot.isPresent()) {
            // Start from snapshot — much faster ✅
            state = deserialize(snapshot.get().getStateJson());
            fromVersion = snapshot.get().getVersion();
            log.info("Rebuilding from snapshot at version: {}", fromVersion);
        } else {
            // No snapshot — start from beginning
            state = AccountState.empty(accountId);
            fromVersion = 0L;
        }

        // Step 2 — replay only events AFTER snapshot
        List<AccountEventLog> events = eventLogRepo
            .findByAccountIdAndVersionGreaterThan(accountId, fromVersion);

        log.info("Replaying {} events from version {}",
            events.size(), fromVersion);

        // Step 3 — apply each event in order
        for (AccountEventLog eventLog : events) {
            state = applyEvent(state, deserialize(eventLog.getPayload()));
        }

        return state;
    }
}
```

---

## 5. Auditability — How Did Record Reach State X

```java
// This is a key business value of event sourcing
// Complete history of every mutation ✅

@Service
public class AccountAuditService {

    // What happened to this account?
    public List<AuditEntry> getAccountHistory(String accountId) {
        return eventLogRepo
            .findByAccountIdOrderByVersionAsc(accountId)
            .stream()
            .map(log -> AuditEntry.builder()
                .version(log.getVersion())
                .eventType(log.getEventType())
                .occurredAt(log.getOccurredAt())
                .correlationId(log.getCorrelationId())
                .changes(extractChanges(log.getPayload()))
                .build())
            .collect(toList());
    }

    // What was the account state at a specific point in time?
    public AccountState getStateAtPointInTime(
            String accountId, Instant pointInTime) {

        List<AccountEventLog> events = eventLogRepo
            .findByAccountIdAndOccurredAtBeforeOrderByVersionAsc(
                accountId, pointInTime);

        AccountState state = AccountState.empty(accountId);
        for (AccountEventLog event : events) {
            state = applyEvent(state, deserialize(event.getPayload()));
        }
        return state;
    }

    // Why did balance change on date X?
    // → Query event log for that accountId and date range
    // → See exact events with correlationId
    // → Trace correlationId back to original request
}
```

**Real business value at Lloyds:**
```
Customer: "Why did my balance change on March 15th?"
Support:  Query event log → find MONEY_WITHDRAWN event v247
          correlationId → trace to original payment request
          Full audit trail in seconds ✅

Regulator: "Show us all mutations to this account in 2024"
Team:      Query event log by accountId and date range
           Complete immutable history ✅

Incident:  "Production data looks wrong — how?"
Team:      Replay event log → find exact event that caused bad state
           Fix the event handler → rebuild state from log ✅
```

---

## 6. Event Sourcing Trade-offs — Be Honest

```
BENEFITS ✅
├── Complete audit trail — every mutation recorded
├── Point-in-time queries — what was state at T?
├── Rebuild state — replay from event log
├── Debug production issues — trace exact sequence
├── Event-driven integration — events as integration points
└── Temporal decoupling — consumers process at their own pace

COSTS ⚠️
├── Complexity — significantly more complex than CRUD
├── Eventual consistency — read model lags write model
├── Query complexity — can't simply SELECT * FROM accounts
│   Must materialise views for read patterns
├── Schema evolution — old events must still be deserializable
│   Event versioning becomes critical
├── Snapshot management — operational overhead
└── Storage growth — event log grows forever
    (even with compaction)
```

**Schema evolution — a real challenge:**
```java
// Problem — event schema changes over time
// Old events in log still need to be deserializable

// Version 1 event — original
{
  "eventType": "MONEY_DEPOSITED",
  "amount": 1000,
  "currency": "GBP"
}

// Version 2 event — added new field
{
  "eventType": "MONEY_DEPOSITED",
  "amount": 1000,
  "currency": "GBP",
  "channel": "MOBILE"  // new field
}

// Event deserializer must handle both versions
public AccountEvent deserialize(String payload) {
    JsonNode node = objectMapper.readTree(payload);

    return MoneyDepositedEvent.builder()
        .amount(node.get("amount").asDouble())
        .currency(node.get("currency").asText())
        // Handle missing field gracefully ✅
        .channel(node.has("channel")
            ? node.get("channel").asText()
            : "UNKNOWN")
        .build();
}
```

---

## Complete Summary

```
Event Sourcing Architecture:

WRITE SIDE:
├── Command received
├── Load current state (snapshot + replay)
├── Apply business logic
├── Generate event
├── ATOMIC transaction:
│   ├── Update current state table
│   ├── Append to event log (only on success)
│   └── Write outbox entry for Kafka
├── Optimistic locking — version conflict detection
└── Idempotency — skip if eventId already processed

EVENT LOG:
├── Immutable append-only
├── Complete mutation history
├── Snapshots every N events
├── Point-in-time state reconstruction
└── Audit trail for compliance

READ SIDE (CQRS):
├── Kafka consumer processes events
├── Updates read store (Spanner)
├── Optimised for query patterns
└── Eventually consistent
```

---

## Polished 90-Second Interview Answer

*"We implemented Event Sourcing on the command side combined with CQRS for the read model. The event log is an immutable, append-only table — every mutation to an account is recorded as an ordered, versioned event. Current state is derived by replaying events, and we take snapshots every 100 events so rebuilding state only replays from the latest snapshot rather than the entire history.*

*Idempotency and ACID correctness were critical given at-least-once Kafka delivery. We wrote the state update and event log entry in the same database transaction — so the log entry is only added if processing succeeds. If a duplicate event arrives, we check the eventId against the log and silently ignore it. Both operations being in the same DB gave us ACID guarantees — no partial state.*

*Optimistic locking handled concurrent updates — each event carries a version, and we verify the expected version matches before committing. Version conflicts trigger a retry with fresh state. This gave us high concurrency without holding DB locks.*

*The event log has real business value beyond just event sourcing — complete audit trail for compliance, point-in-time state reconstruction for investigations, and the ability to rebuild the entire read store by replaying from the log, which we've used for migrations and incident recovery.*

*The honest trade-off is complexity — event sourcing is significantly harder than CRUD. Schema evolution requires careful versioning since old events must always be deserializable. And the read model is eventually consistent — which was acceptable for our use case but needs to be an explicit business decision upfront."*

---

Ready for **Question 3 — Explain the Saga pattern. When would you choose orchestration vs choreography?**

Great question — RTO and RPO are critical concepts for any senior engineer designing highly available systems. Let me explain them fully and then apply to your Spanner architecture.

---

## 1. RTO and RPO — What They Mean

```
RPO — Recovery Point Objective
"How much DATA can we afford to lose?"
"How old can the data be when we recover?"

├── RPO = 0        → zero data loss, every write must be durable
│                    before acknowledging success
├── RPO = 1 minute → up to 1 minute of data loss acceptable
├── RPO = 1 hour   → up to 1 hour of data loss acceptable
└── RPO = 24 hours → last night's backup acceptable

RTO — Recovery Time Objective
"How long can the system be DOWN?"
"How fast must we recover?"

├── RTO = 0        → zero downtime, instant failover
├── RTO = 1 minute → system can be down up to 1 minute
├── RTO = 1 hour   → 1 hour outage acceptable
└── RTO = 24 hours → next business day recovery acceptable
```

**Simple mental model:**
```
Disaster strikes at T=0

RPO answers: T=0 minus how far back can we restore data from?
RTO answers: T=0 plus how long until system is serving requests again?

Timeline:
─────────────────────────────────────────────────────►
    Last          Disaster      System back
    backup        strikes       online
      │               │              │
      │◄─── RPO ─────►│              │
                       │◄─── RTO ───►│
```

**Business context:**
```
Commercial Banking (your Lloyds platform):
├── RPO = near zero   → cannot lose financial transactions ✅
│                       losing even 1 transaction = regulatory issue
└── RTO = near zero   → cannot have downtime ✅
                        customers expect 24/7 availability
                        SLA: 99.999% = 5 minutes downtime per year

E-commerce:
├── RPO = minutes     → losing a few orders is painful but recoverable
└── RTO = minutes     → brief downtime acceptable

Internal reporting tool:
├── RPO = hours       → last backup acceptable
└── RTO = hours       → users can wait
```

---

## 2. How Spanner Achieves Near-Zero RTO and RPO

### Spanner Architecture
```
Google Cloud Spanner — Multi-Region Setup:

Region: europe-west2 (London)          Region: europe-west4 (Netherlands)
┌─────────────────────────┐            ┌─────────────────────────┐
│  Zone A    Zone B Zone C│            │  Zone A    Zone B Zone C│
│  ┌──────┐ ┌─────┐ ┌────┐│            │  ┌──────┐ ┌─────┐      │
│  │Node 1│ │Node2│ │Node││            │  │Node 4│ │Node5│      │
│  │      │ │     │ │  3 ││            │  │      │ │     │      │
│  └──────┘ └─────┘ └────┘│            │  └──────┘ └─────┘      │
└─────────────────────────┘            └─────────────────────────┘
              │                                      │
              └──────────────┬───────────────────────┘
                             │
                    TrueTime API
                 (Google atomic clocks
                  + GPS receivers)
                 Globally consistent
                 timestamps ✅
```

### How Near-Zero RPO Works — Paxos Consensus

```
Every write in Spanner goes through Paxos consensus:

Client writes: "Debit ACC-001 by £500"
        │
        ▼
Spanner Leader Node
        │
        ├──────────────────────────────────────────┐
        │          Paxos replication               │
        ▼                    ▼                     ▼
    Node 1              Node 2               Node 3
    (London-A)          (London-B)           (Netherlands-A)
        │                    │                     │
        ▼                    ▼                     ▼
    Persisted           Persisted            Persisted
        │                    │                     │
        └────────────────────┴─────────────────────┘
                             │
                    Majority quorum ✅
                    (2 of 3 confirmed)
                             │
                             ▼
              Write acknowledged to client
              DATA IS DURABLE ✅

RPO = 0 because:
Data is on multiple nodes BEFORE client receives success
If leader dies immediately after ack → data already on replicas
Nothing lost ✅
```

### How Near-Zero RTO Works — Automatic Failover

```
Normal operation:
Leader: London-A → handles all writes
Followers: London-B, Netherlands-A → replicate, serve reads

Node failure scenario:
T=0   London-A (leader) fails
T=1ms Paxos detects leader unavailable
T=2ms Remaining nodes hold election
T=5ms London-B elected new leader
T=5ms Writes resume on new leader ✅

RTO = ~5ms for regional node failure
No manual intervention ✅
No data loss ✅ (data already on London-B)
```

### Tunable Consistency — Your Point

```java
// Spanner consistency modes:

// STRONG consistency — your choice for financial data ✅
// Reads always reflect latest committed write
// Slightly higher latency — must coordinate across nodes
Statement statement = Statement.of(
    "SELECT balance FROM accounts WHERE id = @accountId"
);
// Uses strong read by default in single-region
// In multi-region — strong reads go through leader

// STALE reads — faster, may return slightly old data
TimestampBound staleness = TimestampBound.ofExactStaleness(
    15, TimeUnit.SECONDS
);
// Read data as of 15 seconds ago
// Served from nearest replica — no leader coordination
// Good for: analytics, reporting, non-critical reads

// For writes — commit options
CommitRequest.Builder commit = CommitRequest.newBuilder()
    .setReturnCommitStats(true);
// Write only succeeds when majority of replicas confirm ✅
// This is what gives RPO = 0
```

---

## 3. Your Question — What If BOTH Regions Fail?

This is a great question and gets to the heart of disaster recovery planning.

### Scenario Analysis

```
Multi-region Spanner setup:
├── Region 1: London (europe-west2)     — 3 nodes
├── Region 2: Netherlands (europe-west4) — 2 nodes
└── Region 3: Belgium (europe-west1)    — 2 nodes (witness)
              Total: 7 nodes, quorum = 4

Single region failure → 5 nodes remaining → quorum maintained ✅
Two region failure    → 3 nodes remaining → quorum LOST ❌
                        Spanner becomes READ-ONLY or unavailable
```

### What Happens When Quorum is Lost

```
T=0   London AND Netherlands both fail
T=1   Belgium has 2 nodes — cannot form quorum (need 4)
T=2   Spanner stops accepting WRITES ❌
      (to prevent split-brain and data corruption)
T=3   READ-ONLY mode — can still serve stale reads from Belgium
T=4   System waits for region recovery or manual intervention

This is a CAP theorem decision:
Spanner chooses CONSISTENCY over AVAILABILITY
When quorum lost → stops writes rather than risk inconsistency ✅
This is correct for financial data ✅
```

### How to Handle Double Region Failure

**Option 1 — Accept it (most realistic for most systems)**
```
For commercial banking:
├── Double region failure = catastrophic infrastructure event
├── Probability: extremely low (Google's infrastructure)
├── RTO in this scenario: hours (Google recovers region)
├── RPO: near zero (data safe on Belgium nodes)
└── Business decision: this risk level is acceptable

Most enterprises accept this trade-off:
"Two simultaneous region failures is a force majeure event
 affecting everyone, not just us"
```

**Option 2 — Active-Active Multi-Region Application**
```
Deploy application in 3 regions:
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   London     │  │ Netherlands  │  │   Frankfurt  │
│   App Pods   │  │   App Pods   │  │   App Pods   │
│   (active)   │  │   (active)   │  │   (active)   │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │                 │                  │
       └─────────────────┴──────────────────┘
                         │
                  Spanner (multi-region)

Traffic routing via Global Load Balancer:
├── London down → route to Netherlands + Frankfurt
├── London + Netherlands down → route to Frankfurt only
└── All three down → force majeure — no solution possible
```

**Option 3 — Backup and Restore Strategy (RPO > 0)**
```
Even with near-zero RPO from replication,
have a backup strategy as last resort:

Spanner automatic backups:
├── Scheduled backups: every hour → RPO = 1 hour worst case
├── Retained for: 7 days
└── Restore time: 30-60 minutes → RTO = 30-60 min for catastrophe

Your event log (event sourcing) adds another layer:
├── Events also in Kafka (durable, replicated)
├── Can replay Kafka to rebuild Spanner ✅
└── RPO = time since last Kafka offset
    (near zero if Kafka also multi-region)
```

**Option 4 — Export to GCS (Cloud Storage) for DR**
```
Spanner → GCS export pipeline:
├── Every 15 minutes export to Cloud Storage
├── GCS is multi-region by default
├── If ALL Spanner regions fail (unprecedented):
│   ├── Spin up new Spanner instance
│   ├── Import from GCS
│   └── RPO = 15 minutes, RTO = 1-2 hours
└── This is the ultimate disaster recovery backstop
```

---

## 4. Your Event Sourcing Adds Extra Protection

This is a great point specific to your architecture:

```
Your architecture has MULTIPLE durability layers:

Layer 1 — Spanner multi-region replication
├── RPO = 0 for regional failures
└── RTO = milliseconds for regional failures

Layer 2 — Kafka multi-region (if configured)
├── All events also in Kafka log
├── Kafka can be multi-region with MirrorMaker2
└── Can rebuild Spanner from Kafka if needed

Layer 3 — Event log in Spanner
├── Even if current state table corrupted
├── Replay event log → rebuild current state ✅
└── This is your custom rebuild capability you mentioned

Layer 4 — Spanner scheduled backups to GCS
├── Last resort
└── RPO = backup interval, RTO = hours

Recovery scenarios:
├── Node failure        → Spanner auto-heals, RPO=0, RTO=ms ✅
├── Region failure      → Spanner auto-heals, RPO=0, RTO=ms ✅
├── Two regions fail    → Replay from Kafka/event log ✅
├── Data corruption     → Replay event log → rebuild state ✅
└── Complete loss       → Restore from GCS backup → RPO=hours
```

---

## 5. RTO/RPO for Your Kafka Pipeline

```
Kafka multi-region setup:

Single broker failure:
├── Kafka replication factor = 3 (min.insync.replicas = 2)
├── Leader election: ~30 seconds
├── RTO = 30 seconds
└── RPO = 0 (replicated before ack)

Single region failure:
├── MirrorMaker2 replicates to second region
├── Consumer groups fail over to DR region
├── RTO = minutes (reconnect + rebalance)
└── RPO = near zero (async replication lag)

Complete Kafka loss:
├── Outbox table in Spanner still has events ✅
├── Replay outbox → republish to new Kafka ✅
└── Your outbox pattern gives you this recovery capability
```

---

## Complete RTO/RPO Summary for Your Architecture

```
Component          Normal RTO    Normal RPO    Catastrophic RTO  Catastrophic RPO
─────────────────────────────────────────────────────────────────────────────────
Spanner            milliseconds  0             hours (GCS restore) 15 minutes
(single region)
Spanner            milliseconds  0             hours             0
(multi-region)
Kafka              30 seconds    0             minutes           near zero
                   (leader elect)              (failover)        (mirror lag)
Read Store         seconds       seconds       minutes           minutes
(eventual)         (replay lag)  (lag)         (replay from log)

Overall system     milliseconds  0             hours             minutes
```

---

## Polished Interview Answer

*"RTO is how quickly you recover — how long can the system be down. RPO is how much data you can afford to lose — how old can the restored data be. For commercial banking both are near zero — losing even a single transaction is a regulatory issue and customers expect 24/7 availability.*

*Spanner achieves near-zero RPO through Paxos consensus — a write is only acknowledged to the client after a majority quorum of replicas across zones confirm persistence. So even if the leader node dies the millisecond after acknowledging, the data is already on multiple other nodes. Nothing is lost.*

*Near-zero RTO comes from automatic leader election — if a node fails, remaining nodes elect a new leader in milliseconds with no manual intervention and no data loss since replicas are already current.*

*Your question about double region failure is the interesting edge case. When quorum is lost, Spanner stops accepting writes rather than risk inconsistency — a correct decision for financial data, choosing consistency over availability per CAP theorem. In that scenario our architecture has additional recovery layers — the event log in Spanner means we can replay events to rebuild state, Kafka with MirrorMaker2 provides another copy of all events, and scheduled exports to GCS provide the ultimate backstop with RPO of 15 minutes and RTO of one to two hours.*

*In practice, simultaneous multi-region failure on Google's infrastructure is an extremely low probability event — the business decision is that this risk level is acceptable given the infrastructure investment required to protect against it."*

---

Ready for **Question 3 — Saga pattern: orchestration vs choreography?**

Perfect — you've nailed the core trade-offs. Let me structure this into a complete, polished answer with the technical depth that elevates it to senior level.

---

## What You Nailed ✅

- Orchestration = centralised control and coordination
- Choreography = decentralised, event-triggered chain
- Choreography sounds great but becomes event hell in production
- No visibility of current state in choreography
- Orchestration = bottleneck but gives control
- Critical processes → orchestration
- Non-critical → choreography but max 3-4 cascading events

---

## The Complete Picture

---

## 1. Choreography vs Orchestration — Visual Contrast

```
CHOREOGRAPHY — Decentralised:

Payment          Account          Notification      Audit
Service          Service          Service           Service
   │                │                  │               │
   │ PaymentCreated │                  │               │
   │────────────────►                  │               │
   │                │ AccountDebited   │               │
   │                │──────────────────►               │
   │                │                  │ Send Email    │
   │                │                  │               │
   │                │ AccountDebited   │               │
   │                │──────────────────────────────────►
   │                │                  │ Record Audit  │

Nobody owns the flow ❌
Nobody knows if payment is complete ❌
If AccountService fails — who retries? ❌
What is current state of payment? Nobody knows ❌

─────────────────────────────────────────────────────

ORCHESTRATION — Centralised:

                Payment Orchestrator
                (owns the workflow)
                        │
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
      Account      Notification    Audit
      Service       Service        Service
          │             │             │
          └─────────────┴─────────────┘
                        │
              Orchestrator knows:
              ✅ Current step
              ✅ Overall state
              ✅ What failed
              ✅ How to compensate
```

---

## 2. Saga Pattern — Why It Exists

```
Problem — distributed transactions across microservices:

// Traditional ACID transaction — works within single DB ✅
@Transactional
public void transfer(String fromId, String toId, double amount) {
    accountRepo.debit(fromId, amount);   // same DB
    accountRepo.credit(toId, amount);    // same DB
    auditRepo.record(...);               // same DB
    // All atomic — commit or rollback together ✅
}

// Distributed transaction — spans multiple services ❌
public void processPayment(PaymentRequest request) {
    accountService.debit(request);        // Service A, DB A
    paymentGateway.charge(request);       // Service B, external
    notificationService.notify(request);  // Service C, DB C
    auditService.record(request);         // Service D, DB D
    // NO distributed transaction available ❌
    // What if paymentGateway succeeds but notificationService fails?
    // Account debited, payment charged, but no notification
    // Partial state — data inconsistency ❌
}

// Saga solves this with:
// 1. Break into local transactions per service
// 2. Each step publishes event on success
// 3. On failure — execute COMPENSATING transactions to undo
```

---

## 3. Compensating Transactions — Critical Concept

```java
// Each Saga step has a forward action AND a compensating action

Step 1: Debit Account          ←→  Compensate: Credit Account back
Step 2: Reserve Inventory      ←→  Compensate: Release Inventory
Step 3: Charge Payment Gateway ←→  Compensate: Refund Payment
Step 4: Send Confirmation      ←→  Compensate: Send Cancellation Email

// If Step 3 fails:
// Execute compensations in REVERSE order:
// Compensate Step 2: Release Inventory
// Compensate Step 1: Credit Account back
// System returns to consistent state ✅
```

---

## 4. Orchestration — With Code

```java
// Orchestrator owns the entire workflow
// Single place to see state, handle failures, trigger compensations

@Service
public class PaymentSagaOrchestrator {

    // Saga state — persisted, survives crashes
    @Transactional
    public void startPaymentSaga(PaymentRequest request) {
        // Create saga instance — persisted to DB
        PaymentSaga saga = PaymentSaga.builder()
            .sagaId(UUID.randomUUID().toString())
            .paymentRequestId(request.getId())
            .currentStep(SagaStep.DEBIT_ACCOUNT)
            .status(SagaStatus.IN_PROGRESS)
            .startedAt(Instant.now())
            .build();

        sagaRepo.save(saga);

        // Step 1 — Debit account
        executeStep1(saga, request);
    }

    // Step 1
    private void executeStep1(PaymentSaga saga, PaymentRequest request) {
        try {
            DebitResult result = accountService.debit(
                request.getAccountId(),
                request.getAmount()
            );

            // Update saga state
            saga.setCurrentStep(SagaStep.CHARGE_GATEWAY);
            saga.setDebitTransactionId(result.getTransactionId());
            sagaRepo.save(saga);

            // Proceed to step 2
            executeStep2(saga, request);

        } catch (InsufficientFundsException e) {
            // Non-retryable business failure
            // No compensation needed — nothing happened yet
            failSaga(saga, "INSUFFICIENT_FUNDS", e.getMessage());

        } catch (Exception e) {
            // Retryable technical failure
            retrySaga(saga, SagaStep.DEBIT_ACCOUNT, e);
        }
    }

    // Step 2
    private void executeStep2(PaymentSaga saga, PaymentRequest request) {
        try {
            ChargeResult result = paymentGateway.charge(
                request.getAmount(),
                request.getPaymentMethod()
            );

            saga.setCurrentStep(SagaStep.SEND_NOTIFICATION);
            saga.setChargeId(result.getChargeId());
            sagaRepo.save(saga);

            executeStep3(saga, request);

        } catch (Exception e) {
            // Gateway failed — compensate step 1
            log.error("Gateway failed, compensating debit", e);
            compensateStep1(saga, request);
            failSaga(saga, "GATEWAY_ERROR", e.getMessage());
        }
    }

    // Compensation — undo step 1
    private void compensateStep1(PaymentSaga saga, PaymentRequest request) {
        try {
            accountService.credit(
                request.getAccountId(),
                request.getAmount(),
                saga.getDebitTransactionId()  // reference original debit
            );
            log.info("Compensation successful for saga: {}", saga.getSagaId());

        } catch (Exception e) {
            // Compensation failed — needs manual intervention ⚠️
            log.error("COMPENSATION FAILED for saga: {} — manual intervention required",
                saga.getSagaId(), e);
            alertOpsTeam(saga);  // page on-call engineer
        }
    }

    // Saga visibility — answer business questions instantly
    public SagaStatus getPaymentStatus(String paymentRequestId) {
        return sagaRepo.findByPaymentRequestId(paymentRequestId)
            .map(saga -> SagaStatus.builder()
                .sagaId(saga.getSagaId())
                .currentStep(saga.getCurrentStep())
                .status(saga.getStatus())
                .startedAt(saga.getStartedAt())
                .completedAt(saga.getCompletedAt())
                .failureReason(saga.getFailureReason())
                .build())
            .orElseThrow(() -> new SagaNotFoundException(paymentRequestId));
    }
}
```

**LangGraph / State Machine style orchestration:**
```java
// More sophisticated — explicit state machine
public enum SagaStep {
    DEBIT_ACCOUNT,
    CHARGE_GATEWAY,
    SEND_NOTIFICATION,
    RECORD_AUDIT,
    COMPLETED,
    FAILED,
    COMPENSATING
}

// State transitions
Map<SagaStep, SagaStep> successTransitions = Map.of(
    SagaStep.DEBIT_ACCOUNT,     SagaStep.CHARGE_GATEWAY,
    SagaStep.CHARGE_GATEWAY,    SagaStep.SEND_NOTIFICATION,
    SagaStep.SEND_NOTIFICATION, SagaStep.RECORD_AUDIT,
    SagaStep.RECORD_AUDIT,      SagaStep.COMPLETED
);

Map<SagaStep, SagaStep> compensationSteps = Map.of(
    SagaStep.CHARGE_GATEWAY,    SagaStep.DEBIT_ACCOUNT,  // credit back
    SagaStep.DEBIT_ACCOUNT,     SagaStep.COMPLETED       // nothing to undo
);
```

---

## 5. Choreography — With Code

```java
// No central orchestrator — services react to events

// Payment Service — publishes event, doesn't know what happens next
@Service
public class PaymentService {

    @Transactional
    public void createPayment(PaymentRequest request) {
        Payment payment = paymentRepo.save(
            Payment.from(request));

        // Publish event — fire and forget
        eventBus.publish(PaymentCreatedEvent.builder()
            .paymentId(payment.getId())
            .accountId(request.getAccountId())
            .amount(request.getAmount())
            .build());
        // Payment Service job done — doesn't track what happens next
    }
}

// Account Service — reacts to PaymentCreated
@KafkaListener(topics = "payment-created")
public void onPaymentCreated(PaymentCreatedEvent event) {
    accountService.debit(event.getAccountId(), event.getAmount());

    // Publishes its own event
    eventBus.publish(AccountDebitedEvent.builder()
        .paymentId(event.getPaymentId())
        .accountId(event.getAccountId())
        .build());
}

// Notification Service — reacts to AccountDebited
@KafkaListener(topics = "account-debited")
public void onAccountDebited(AccountDebitedEvent event) {
    notificationService.sendConfirmation(event.getPaymentId());
    // Publishes NotificationSentEvent...
}

// Problems in production:
// "Is payment PAY-001 complete?" → nobody knows ❌
// "Why didn't customer get notification?" → trace 5 topics ❌
// AccountService failed → who compensates? → nobody ❌
// New requirement: add fraud check between debit and notification
// → must change multiple services + add new topics ❌
```

---

## 6. Event Hell — Why Choreography Breaks Down

```
Simple flow — looks clean:
PaymentCreated → AccountDebited → NotificationSent

6 months later after new requirements:
PaymentCreated
    → AccountDebited
        → FraudCheckPassed
            → LimitCheckPassed
                → NotificationSent
                    → AuditRecorded
                        → ReportingUpdated
                            → LoyaltyPointsAwarded
                                → ...

Problems:
├── 8 services, 8 topics, event chain 8 deep
├── One failure anywhere = silent inconsistency
├── Tracing a payment requires correlating 8 topics
├── Adding step 5.5 requires changing services 5 and 6
├── Testing requires running all 8 services
└── "What is the state of payment X?" → impossible to answer quickly
```

---

## 7. Your Decision Framework — When to Use What

```
Critical business processes:
├── Financial transactions    → Orchestration ✅
├── Order processing          → Orchestration ✅
├── Account onboarding        → Orchestration ✅
├── Loan approval workflow    → Orchestration ✅
└── Regulatory reporting      → Orchestration ✅

Why: need visibility, auditability, compensation control

Non-critical, independent processes:
├── Sending notifications     → Choreography ✅ (max 1-2 hops)
├── Audit logging             → Choreography ✅ (fire and forget)
├── Cache invalidation        → Choreography ✅
├── Analytics event streaming → Choreography ✅
└── Search index updates      → Choreography ✅

Why: failure doesn't corrupt business state
     retrying independently is safe
     no compensation needed

Your rule — MAX 3-4 cascading events for choreography ✅
Beyond that → convert to orchestration
```

---

## 8. Hybrid — What You Actually Use in Production

```
Real architecture at Lloyds (conceptually):

Payment Processing (Orchestration):
Orchestrator → AccountService → PaymentGateway → [success]
                             ↘ [failure] → compensate → AccountService

Non-critical side effects (Choreography):
PaymentCompleted event →  NotificationService (1 hop) ✅
                       →  AuditService (1 hop) ✅
                       →  AnalyticsService (1 hop) ✅

These are fire-and-forget, independent, no compensation needed
Failure of notification doesn't affect payment ✅
```

---

## Polished 90-Second Interview Answer

*"The Saga pattern manages distributed transactions across microservices where a single ACID transaction isn't possible. Each step is a local transaction, and failures trigger compensating transactions to undo previous steps.*

*Orchestration has a central coordinator that owns the workflow — it knows the current step, drives each service call, handles failures, and triggers compensations in reverse order. The key benefit is complete visibility and control — you can answer 'what is the state of this payment?' instantly, trace failures to the exact step, and have a single place to manage retry and compensation logic. The trade-off is it becomes a bottleneck and a single point of failure if not designed carefully.*

*Choreography is decentralised — each service reacts to events and publishes its own. No single coordinator. It sounds elegant and loosely coupled but in production becomes what I call event hell — chains of 6-8 cascading events where nobody owns the overall state, failures cause silent inconsistencies, and tracing a single business transaction requires correlating events across multiple topics. Adding a new step requires changing multiple services.*

*My decision framework: critical business processes — payments, onboarding, loan approvals — always use orchestration. You need visibility, auditability, compensation control, and the ability to answer business questions about current state. Non-critical independent side effects — notifications, audit logging, analytics — use choreography, but I cap cascading events at 3-4 maximum. Beyond that it becomes unmanageable.*

*In practice at Lloyds, the payment processing saga was orchestrated — the orchestrator persisted saga state to the database at every step, so even if it crashed mid-flow we could resume from exactly where we left off. Notifications and audit were choreographed as independent single-hop consumers of the payment completed event."*

---

Ready for **Question 4 — How do you handle data consistency in distributed transactions?**

Here's a comprehensive answer drawing from everything we've covered:

---

## Data Consistency in Distributed Transactions

---

## The Core Problem

```
Microservices = each service owns its own database
No shared database = no single ACID transaction
Result = consistency must be managed at application level

// Cannot do this across services ❌
@Transactional
public void processPayment() {
    accountDB.debit(...);      // DB A
    paymentDB.charge(...);     // DB B
    notificationDB.record(...);// DB C
    // No distributed transaction ❌
}
```

---

## Strategy 1 — Saga Pattern (already covered)

```
Break distributed transaction into local transactions
Each step has compensating transaction for rollback

Forward:      Debit → Charge → Notify → Audit
Compensate:   Credit ← Refund ← Cancel ← (no undo needed)

Orchestrated Saga for critical flows:
├── Central orchestrator owns state
├── Persists saga step to DB at each transition
├── Survives crashes — resumes from last step
└── Compensation triggered on any failure
```

---

## Strategy 2 — Outbox Pattern (already covered)

```
Problem: dual write — DB write + Kafka publish not atomic
Solution: write event to outbox table IN SAME transaction

@Transactional
public void processCommand(Command cmd) {
    // Business write
    accountRepo.save(account);

    // Outbox write — same transaction ✅
    outboxRepo.save(OutboxEvent.from(cmd));
    // Committed atomically — no dual write problem
}

// Separate publisher reads outbox → publishes to Kafka
// Guarantees at-least-once delivery
```

---

## Strategy 3 — Idempotency (already covered)

```
At-least-once delivery = duplicate events possible
Solution: idempotent consumers

// Every event has unique eventId
// Before processing — check if already processed
if (eventLogRepo.existsByEventId(event.getEventId())) {
    return; // silent ignore ✅
}

// Monotonic versioning — reject stale events
if (event.getVersion() <= current.getVersion()) {
    return; // stale, ignore ✅
}
```

---

## Strategy 4 — Optimistic Locking (already covered)

```
Concurrent updates to same record
Solution: version-based conflict detection

@Version
private long version;

// UPDATE SET balance=900, version=6
// WHERE id='ACC-001' AND version=5
// Rows affected = 0 → conflict → retry ✅

No DB locks held
High concurrency
Correct for low-conflict scenarios like banking
```

---

## Strategy 5 — Eventual Consistency — Embrace It

```
Not everything needs strong consistency
Identify which operations can be eventually consistent

STRONG consistency required:
├── Account balance debit/credit  → must be immediate
├── Payment authorisation         → must be real-time
└── Duplicate transaction check   → must be immediate

EVENTUAL consistency acceptable:
├── Read store updates            → seconds lag fine ✅
├── Notification delivery         → minutes lag fine ✅
├── Analytics/reporting           → hours lag fine ✅
└── Search index updates          → seconds lag fine ✅

Your Lloyds architecture:
├── Write side (Spanner) → strongly consistent ✅
└── Read side (CQRS)     → eventually consistent ✅
```

---

## Strategy 6 — Two-Phase Commit (2PC) — Know Why to Avoid

```
Traditional distributed transaction:

Phase 1 — Prepare:
Coordinator asks all participants: "Can you commit?"
All participants: lock resources, respond YES/NO

Phase 2 — Commit/Rollback:
If all YES → Coordinator sends COMMIT
If any NO  → Coordinator sends ROLLBACK

Problems in microservices:
├── Coordinator is single point of failure ❌
├── Participants hold locks during both phases ❌
│   High latency, low throughput
├── Network partition → locks held indefinitely ❌
└── Tight coupling between services ❌

When acceptable:
├── Single database with multiple schemas
├── XA transactions within same infrastructure
└── NOT for microservices over network ❌
```

---

## Strategy 7 — Change Data Capture (CDC)

```
Alternative to Outbox for event publishing:

Instead of application writing to outbox table,
capture changes directly from DB transaction log

Debezium → reads Postgres/Spanner WAL (Write-Ahead Log)
         → publishes every DB change to Kafka automatically

┌─────────────┐     ┌──────────┐     ┌───────────┐
│  Service    │────►│  DB      │────►│ Debezium  │────► Kafka
│  (writes)   │     │  (WAL)   │     │ (CDC)     │
└─────────────┘     └──────────┘     └───────────┘

Benefits vs Outbox:
├── No outbox table needed — simpler application code
├── Captures ALL changes — even direct DB modifications
└── Zero application changes needed

Trade-offs:
├── Operational complexity — Debezium cluster to manage
├── Schema changes in DB affect CDC pipeline
├── Slightly higher latency than outbox
└── Tight coupling to DB internals

Your choice — Outbox pattern:
├── More control ✅
├── Application owns what events are published ✅
└── No dependency on DB internals ✅
```

---

## Strategy 8 — Distributed Locking for Critical Sections

```java
// When two instances might process same resource simultaneously
// Example: two pods process payment for same account concurrently

// Redis distributed lock
@Service
public class PaymentService {

    private final RedissonClient redisson;

    public PaymentResult processPayment(PaymentRequest request) {
        // Lock per account — prevent concurrent processing
        RLock lock = redisson.getLock(
            "payment-lock:" + request.getAccountId()
        );

        try {
            // Try acquire lock — wait max 5s, hold max 30s
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);

            if (!acquired) {
                throw new ConcurrentPaymentException(
                    "Account is being processed, retry later");
            }

            return doProcessPayment(request);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();  // always release ✅
            }
        }
    }
}

// When to use:
// ├── Critical section that must not run concurrently
// ├── Inventory reservation — prevent overselling
// └── Account processing — prevent concurrent debits

// When NOT to use:
// ├── High-throughput operations — lock becomes bottleneck
// ├── Optimistic locking is usually better for DB operations
// └── Stateless operations — no shared state to protect
```

---

## Strategy 9 — Idempotency Keys — API Level

```java
// Client sends idempotency key with every request
// Server uses it to deduplicate retried requests

@RestController
public class PaymentController {

    @PostMapping("/payments")
    public ResponseEntity<PaymentResult> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request) {

        // Check if already processed
        Optional<PaymentResult> existing =
            idempotencyStore.find(idempotencyKey);

        if (existing.isPresent()) {
            // Return cached result — safe retry ✅
            return ResponseEntity.ok(existing.get());
        }

        // Process and cache result
        PaymentResult result = paymentService.process(request);
        idempotencyStore.save(idempotencyKey, result, Duration.ofHours(24));

        return ResponseEntity.ok(result);
    }
}

// Client retries safely:
POST /payments
Idempotency-Key: client-generated-uuid-123
// Network timeout → client retries with SAME key
// Server returns cached result → no duplicate payment ✅
```

---

## Complete Consistency Strategy Decision Tree

```
What kind of operation is it?

├── Single service, single DB
│   └── @Transactional — standard ACID ✅
│
├── Multi-service, needs atomicity
│   ├── Critical business flow (payment, onboarding)
│   │   └── Orchestrated Saga + Outbox ✅
│   └── Non-critical side effects
│       └── Choreography + idempotent consumers ✅
│
├── Concurrent updates to same record
│   ├── Low conflict rate (most banking ops)
│   │   └── Optimistic locking (@Version) ✅
│   └── High conflict rate or critical section
│       └── Distributed lock (Redis) ✅
│
├── Event publishing reliability
│   └── Outbox pattern — atomic with business write ✅
│
├── Consumer deduplication
│   └── Idempotency key + monotonic versioning ✅
│
└── Read model consistency
    └── Eventual consistency — CQRS read store ✅
```

---

## How This Looked at Lloyds

```
Payment Processing — full consistency stack:

1. API receives payment request with Idempotency-Key
   └── Deduplicates retried requests at API level

2. Orchestrated Saga starts
   └── Saga state persisted to Spanner at each step

3. Each step uses Outbox pattern
   └── Business write + outbox event = atomic

4. Optimistic locking on account state
   └── @Version prevents concurrent balance corruption

5. Kafka consumers are idempotent
   └── Monotonic versioning rejects duplicates

6. Read store eventually consistent
   └── Acceptable — balance queries show near-real-time data

7. Distributed lock for high-value payments
   └── Prevents concurrent processing of same account
```

---

## Polished 90-Second Interview Answer

*"Data consistency in distributed systems requires a layered approach — there's no single solution.*

*At the transaction level, the Saga pattern handles multi-service atomicity — each step is a local transaction with a compensating action. Orchestrated Sagas persist state at every step so they survive crashes and resume correctly. For reliable event publishing, the Outbox pattern ensures the business write and event publication are atomic — written to the same DB transaction, eliminating the dual-write problem.*

*At the record level, optimistic locking handles concurrent updates — version-based conflict detection with no DB locks held, which suits low-conflict scenarios like account updates. For critical sections where concurrent processing must be prevented, Redis distributed locks provide a bounded wait with automatic expiry.*

*At the consumer level, idempotency is essential given at-least-once delivery. We use a combination of eventId deduplication and monotonic versioning — if an event version is already processed, it's silently ignored. At the API level, idempotency keys let clients safely retry without risk of duplicate operations.*

*The most important principle is not forcing strong consistency everywhere — identify what genuinely needs it versus what can be eventually consistent. In our platform, the write side was strongly consistent through Spanner's Paxos replication. The read store was eventually consistent — acceptable for balance queries where a few seconds lag is fine. Forcing strong consistency on the read store would have required locking across services, killing throughput for no real business benefit."*

---

Ready for **Question 5 — How do you approach the Strangler Fig pattern for legacy migration?**

Perfect — you've described a real production Strangler Fig implementation. Let me structure and elevate this into a complete senior-level answer.

---

## What You Nailed ✅

- Progressive modernisation — one journey at a time
- Decoupled and modernised Create Account first
- Edge routing — CDN rules redirect old paths to new
- Same domain — transparent to consumers
- New DNS and VIP on cloud for new journeys
- Monolith still serves unmodernised journeys

---

## The Complete Picture

---

## 1. What is Strangler Fig — The Mental Model

```
Named after the Strangler Fig tree:
├── Grows around an existing tree
├── Gradually takes over
└── Original tree eventually dies or is removed

Software equivalent:
├── New system grows around legacy
├── Gradually takes over traffic
└── Legacy eventually decommissioned

Key principle:
NEVER big-bang rewrite ❌
ALWAYS incremental replacement ✅

Big bang rewrite failures:
├── Netscape 6 rewrite — killed the browser ❌
├── NHS patient records rewrite — abandoned after £10B ❌
└── FBI virtual case file — scrapped after $170M ❌
```

---

## 2. Your Architecture — Fleshed Out

```
BEFORE — All traffic to monolith:

Client
  │
  ▼
CDN/Edge
  │
  ▼
Load Balancer
  │
  ▼
Legacy Monolith
├── Create Account journey
├── View Account journey
├── Deposit journey
├── Withdrawal journey
├── Statement journey
└── 6 more journeys...


DURING — Progressive migration:

Client
  │
  ▼
CDN/Edge ──── Routing Rules ────────────────────┐
  │           IF path = /accounts/create        │
  │           → new VIP (cloud native) ✅        │
  │           ELSE                              │
  │           → legacy VIP (monolith)           │
  │                                             │
  ▼                                             ▼
Legacy Monolith                    Cloud Native Platform
├── View Account     (legacy)      └── Create Account ✅
├── Deposit          (legacy)          (modernised)
├── Withdrawal       (legacy)
├── Statement        (legacy)
└── 6 more journeys  (legacy)


AFTER — Full migration:

Client
  │
  ▼
CDN/Edge
  │
  ▼
Cloud Native Platform
├── Create Account    ✅
├── View Account      ✅
├── Deposit           ✅
├── Withdrawal        ✅
├── Statement         ✅
└── All journeys      ✅

Legacy Monolith → DECOMMISSIONED ✅
```

---

## 3. Edge Routing — How It Works in Detail

```
CDN routing rules (your implementation):

# CDN configuration (Cloudflare / Akamai / GCP Cloud CDN)
rules:
  - condition:
      path: /api/accounts/create
    action:
      forward_to: cloud-native-vip.lloyds.internal
      # New cloud platform ✅

  - condition:
      path: /api/accounts/*/view
    action:
      forward_to: cloud-native-vip.lloyds.internal
      # Modernised ✅

  - condition:
      path: /api/accounts/*/deposit
    action:
      forward_to: legacy-vip.lloyds.internal
      # Not yet modernised — still monolith

  - condition:
      path: /api/**  # catch-all
    action:
      forward_to: legacy-vip.lloyds.internal
      # Default — everything else to monolith

Same domain throughout:
api.lloyds.com/accounts/create → cloud native ✅
api.lloyds.com/accounts/deposit → monolith ✅
Consumer sees same domain — transparent ✅
```

---

## 4. The Anti-Corruption Layer — Critical Addition

```java
// New cloud-native service often needs data from legacy
// Direct calls to legacy create tight coupling ❌
// Solution: Anti-Corruption Layer (ACL) ✅

// ACL translates between legacy and modern domain models
@Service
public class LegacyAccountAdapter {

    private final LegacyAccountClient legacyClient;

    // Translate legacy response to modern domain model
    public Account fetchFromLegacy(String accountId) {
        LegacyAccountResponse legacy =
            legacyClient.getAccount(accountId);

        // Legacy has different field names, types, structures
        return Account.builder()
            .id(legacy.getAcctNum())              // different name
            .ownerName(legacy.getCustFullNm())    // abbreviated name
            .balance(
                new BigDecimal(legacy.getBalAmt())// string → BigDecimal
                    .movePointLeft(2)             // pence → pounds
            )
            .status(mapStatus(legacy.getAcctSts()))// different enum
            .build();
    }

    private AccountStatus mapStatus(String legacyStatus) {
        return switch (legacyStatus) {
            case "A"  -> AccountStatus.ACTIVE;
            case "I"  -> AccountStatus.INACTIVE;
            case "S"  -> AccountStatus.SUSPENDED;
            default   -> AccountStatus.UNKNOWN;
        };
    }
}

// New service depends on ACL interface — not legacy directly
@Service
public class AccountService {
    private final AccountRepository modernRepo;
    private final LegacyAccountAdapter legacyAdapter;  // ACL ✅

    public Account findById(String id) {
        // Try modern store first
        return modernRepo.findById(id)
            .orElseGet(() ->
                legacyAdapter.fetchFromLegacy(id));  // fallback to legacy
    }
}
```

---

## 5. Data Migration Strategy — Parallel Running

```
Challenge: new platform needs data that lives in legacy DB

Options:

Option 1 — Lazy migration (your likely approach)
├── Don't migrate data upfront
├── On first access → fetch from legacy → cache in new store
├── Over time data naturally migrates
└── Legacy DB gradually empties

// Lazy migration pattern
public Account findById(String id) {
    // Check new store first
    Optional<Account> modern = modernRepo.findById(id);
    if (modern.isPresent()) {
        return modern.get();  // already migrated ✅
    }

    // Fetch from legacy
    Account account = legacyAdapter.fetchFromLegacy(id);

    // Migrate to new store
    modernRepo.save(account);

    return account;  // now in new store ✅
}

Option 2 — Bulk migration
├── One-time job migrates all data
├── Dual write during transition
└── Cut over when sync confirmed

Option 3 — Dual write
├── Writes go to BOTH legacy and new DB
├── New store catches up with legacy
├── Verify consistency
└── Cut over reads to new store
```

---

## 6. Feature Flags — Gradual Traffic Shifting

```java
// Don't cut over 100% immediately — use feature flags
// Canary release — send small % of traffic to new platform

@Component
public class JourneyRouter {

    private final FeatureFlagService featureFlags;

    public String routeCreateAccount(String userId) {

        // 5% of users → new platform initially
        if (featureFlags.isEnabled("new-create-account", userId)) {
            return "cloud-native";
        }
        return "legacy";
    }
}

// Feature flag config — gradually increase %
{
  "flag": "new-create-account",
  "rules": [
    {
      "percentage": 5,    // week 1 — 5% of users
      "target": "cloud-native"
    }
  ]
}

// Week 1:  5%  → new platform, monitor errors/latency
// Week 2: 20%  → increasing confidence
// Week 3: 50%  → majority on new platform
// Week 4: 100% → full cutover
// Week 6: remove legacy route ✅
```

---

## 7. Verification — How You Know Migration is Safe

```java
// Shadow mode — run both, compare results
@Service
public class ShadowModeRouter {

    public AccountResult createAccount(CreateAccountRequest request) {

        // Always execute on new platform
        AccountResult newResult = cloudNativeService
            .createAccount(request);

        // Also execute on legacy — async, don't affect response
        CompletableFuture.runAsync(() -> {
            try {
                AccountResult legacyResult = legacyService
                    .createAccount(request);

                // Compare results
                if (!newResult.equals(legacyResult)) {
                    log.warn("SHADOW MISMATCH: new={}, legacy={}",
                        newResult, legacyResult);
                    metrics.increment("shadow.mismatch");
                } else {
                    metrics.increment("shadow.match");
                }
            } catch (Exception e) {
                log.error("Shadow execution failed", e);
            }
        }, executor);

        return newResult;  // always return new result ✅
    }
}

// Monitor shadow mismatch rate:
// 0% mismatch → confident to increase traffic % ✅
// >0% mismatch → investigate before increasing % ❌
```

---

## 8. Rollback Strategy — Critical for Safety

```
Always have instant rollback capability:

CDN rollback — seconds:
├── CDN routing rule change → immediate effect
├── Route 100% back to legacy if issues detected
└── Zero code change needed

Feature flag rollback — seconds:
├── Flip flag to 0% → all traffic back to legacy
├── No deployment needed
└── Instant

Application rollback — minutes:
├── Deploy previous version
├── Kubernetes rolling update in reverse
└── ~2-5 minutes

Database rollback — complex:
├── If schema changes made → must be backward compatible
├── Expand-contract pattern:
│   Phase 1: Add new column (nullable) — both versions work
│   Phase 2: Migrate data to new column
│   Phase 3: Make column non-nullable
│   Phase 4: Remove old column
└── Never: make breaking schema change during migration ❌
```

---

## 9. Your Journey Sequencing Strategy

```
How to choose which journey to modernise first:

Priority matrix:
┌─────────────────────┬──────────────┬────────────────┐
│ Journey             │ Business     │ Technical      │
│                     │ Value        │ Complexity     │
├─────────────────────┼──────────────┼────────────────┤
│ Create Account  ✅  │ HIGH         │ MEDIUM         │ ← Start here
│ View Balance        │ HIGH         │ LOW            │ ← Second
│ Deposit             │ HIGH         │ MEDIUM         │
│ Withdrawal          │ HIGH         │ HIGH           │
│ Statement           │ MEDIUM       │ LOW            │
│ Close Account       │ LOW          │ HIGH           │ ← Last
└─────────────────────┴──────────────┴────────────────┘

Your approach — Create Account first:
├── High business value ✅
├── Clear bounded context ✅
├── Limited legacy dependencies ✅
├── Good test case for routing infrastructure ✅
└── Failure impact: new customers only, not existing ✅
    (safer than starting with withdrawal!)
```

---

## Complete Summary

```
Strangler Fig — Key Components:

1. EDGE ROUTING
   └── CDN rules route by path
   └── Same domain — transparent to consumers
   └── Instant rollback capability

2. ANTI-CORRUPTION LAYER
   └── Translates legacy ↔ modern domain models
   └── New service doesn't depend on legacy directly

3. DATA MIGRATION
   └── Lazy migration — migrate on first access
   └── Dual write during transition period
   └── Verify consistency before cutover

4. GRADUAL TRAFFIC SHIFTING
   └── Feature flags — 5% → 20% → 50% → 100%
   └── Shadow mode — compare results
   └── Monitor error rates at each stage

5. JOURNEY SEQUENCING
   └── Start with high value, low risk
   └── Build confidence before complex journeys
   └── Legacy serves remaining journeys throughout

6. DECOMMISSION
   └── Remove routing rule
   └── Remove legacy code
   └── Celebrate ✅
```

---

## Polished 90-Second Interview Answer

*"We used the Strangler Fig pattern for progressive modernisation of our commercial banking platform — never a big-bang rewrite, always incremental replacement one journey at a time.*

*The edge routing infrastructure was foundational. CDN rules at the boundary inspect the request path and route to either the new cloud-native platform or the legacy monolith — same domain throughout, completely transparent to consumers and downstream systems. This meant we could shift traffic at the CDN layer in seconds with instant rollback capability.*

*We started with Create Account — high business value, clear bounded context, limited legacy dependencies, and failure impact only on new customers rather than existing ones. The new journey was implemented end-to-end on cloud native, and action links within the journey pointed to new paths on the same domain, which the CDN routed to the new platform.*

*Between the new services and legacy we built an Anti-Corruption Layer — translating legacy data models, field names, and types into our clean modern domain model. New services depend on the ACL interface, never on legacy directly.*

*For data, we used lazy migration — on first access fetch from legacy, persist to new store, serve from new store on subsequent requests. Combined with shadow mode where both platforms processed the same request and we compared results, we gained confidence before increasing traffic percentage.*

*The sequencing was deliberate — high value, low complexity journeys first. Each modernised journey proved the routing infrastructure and built team confidence for more complex ones. Legacy continued serving unmodernised journeys throughout, decommissioned journey by journey as each cutover completed."*

---

That completes Area 2! Ready for **Area 3 — Middleware Integration & Service Communication?**

First question: *"When would you choose Kafka over REST? And gRPC over REST?"*