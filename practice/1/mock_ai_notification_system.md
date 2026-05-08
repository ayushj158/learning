# Imagine you are a Senior Software Engineer tasked with building a high-performance backend system for a company that handles real-time payment notifications and customer communications. How would you design a high-level data model to represent the relationships between the core application layer, communication/integration layer, and infrastructure layer? Please explain your approach and the technologies you would choose.


## High-Level System Design: Real-Time Payment Notification System

---

### First, let me frame the 3 layers clearly

```
┌─────────────────────────────────────────┐
│         CORE APPLICATION LAYER          │  ← Business logic, data models
├─────────────────────────────────────────┤
│     COMMUNICATION/INTEGRATION LAYER     │  ← Event routing, channel handling
├─────────────────────────────────────────┤
│         INFRASTRUCTURE LAYER            │  ← Compute, storage, networking
└─────────────────────────────────────────┘
```

Each layer has a **distinct responsibility** and they communicate **downward only** — application layer never talks directly to infrastructure.

---

## LAYER 1 — Core Application Layer

### What lives here
Business entities, domain logic, APIs. This layer is **channel-agnostic** — it doesn't care if a notification goes via SMS or Push.

### Data Model

```
┌──────────────┐         ┌───────────────────┐
│    User      │ 1────── │  UserPreference   │
│─────────────│         │───────────────────│
│ id (UUID)   │         │ userId            │
│ name        │         │ channelType       │  EMAIL/SMS/PUSH
│ email       │         │ notificationType  │  PAYMENT/ALERT
│ phone       │         │ enabled (bool)    │
│ timezone    │         └───────────────────┘
└──────┬───────┘
       │ 1
       │
       │ many
┌──────▼───────────────────────────────┐
│           Notification               │
│──────────────────────────────────────│
│ id (UUID)                            │
│ userId (FK)                          │
│ type        → PAYMENT_SUCCESS/FAILED │
│ payload     → JSON (amount, txnId)   │
│ idempotencyKey → prevent duplicates  │
│ priority    → HIGH / MEDIUM / LOW    │
│ status      → PENDING/PROCESSING     │
│ createdAt                            │
│ scheduledAt                          │
└──────────────────────────────────────┘
       │ 1
       │
       │ many
┌──────▼───────────────────────────────┐
│        NotificationDelivery          │  ← One per channel
│──────────────────────────────────────│
│ id (UUID)                            │
│ notificationId (FK)                  │
│ channelType  → EMAIL / SMS / PUSH    │
│ status       → SENT/FAILED/DELIVERED │
│ retryCount                           │
│ providerMsgId → Twilio/SES ref       │
│ channelMetadata → JSONB              │
│ sentAt / deliveredAt / failedAt      │
└──────────────────────────────────────┘
```

### Key design decisions here
- **Notification is channel-agnostic** — it holds the business event (payment of ₹500 succeeded)
- **NotificationDelivery tracks per-channel state** — SMS failed but Email succeeded, retry only SMS
- **idempotencyKey** — if payment service fires the same event twice, we don't double-notify the customer
- **payload as JSON** — flexible, no schema change when new fields are added

### Technologies
- **Spring Boot** — REST APIs, service layer
- **PostgreSQL** — primary store, JSONB for flexible payload
- **Redis** — idempotency key store with TTL, also user preference cache

---

## LAYER 2 — Communication / Integration Layer

### What lives here
This layer **receives events from Layer 1** and **routes them to the right channel**. It's the bridge. It must handle high throughput, retries, and failures gracefully.

### How it fits together

```
Payment Service
      │
      │ publishes event
      ▼
┌─────────────────┐
│   Kafka Topic   │  notifications.payment.high
│                 │  notifications.payment.low
└────────┬────────┘
         │ consumed by
         ▼
┌─────────────────────────────────┐
│     Notification Processor      │  Spring @KafkaListener
│  - reads UserPreference         │
│  - creates NotificationDelivery │
│  - routes to channel handlers   │
└────┬──────────┬──────────┬──────┘
     │          │          │
     ▼          ▼          ▼
┌─────────┐ ┌──────┐ ┌──────────┐
│  Email  │ │ SMS  │ │   Push   │
│ Handler │ │Handler│ │ Handler  │
│ (SES)   │ │(Twilio)│ │  (FCM)  │
└─────────┘ └──────┘ └──────────┘
```

### Retry & Failure Handling

```
First attempt fails
        │
        ▼
Retry after 2s → 4s → 8s   (exponential backoff)
        │
        │ still fails after 3 retries
        ▼
Dead Letter Queue (DLQ)     → ops team alerted
        │
        ▼
Manual replay or fallback channel
```

### Template Resolution

```
NotificationType + ChannelType + Locale
    PAYMENT_SUCCESS + EMAIL + en_IN
              │
              ▼
    "Dear {{name}}, ₹{{amount}} paid to {{merchant}}"
              │
              ▼
    Rendered → sent to provider
```

### Technologies
- **Apache Kafka** — event backbone, partitioned by userId for ordering
- **Spring Kafka** — consumer groups, auto-scaling consumers
- **Twilio** — SMS
- **AWS SES / SendGrid** — Email
- **Firebase FCM** — Push notifications
- **Freemarker/Mustache** — template rendering

---

## LAYER 3 — Infrastructure Layer

### What lives here
Everything the other two layers run on — compute, storage, networking, observability.

### Full Picture

```
                    ┌─────────────────┐
   Client Apps ────▶│  API Gateway    │  Rate limiting, Auth (JWT)
                    │  (Spring Cloud) │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ App Pod 1│  │ App Pod 2│  │ App Pod 3│  K8s auto-scaling
        └──────────┘  └──────────┘  └──────────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
                    ┌────────▼────────┐
                    │   Kafka Cluster │  3 brokers, replication=3
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Worker 1 │  │ Worker 2 │  │ Worker 3 │  Consumer group
        └──────────┘  └──────────┘  └──────────┘
              │
    ┌─────────┼──────────┐
    ▼         ▼          ▼
┌───────┐ ┌───────┐ ┌─────────┐
│  PG   │ │ Redis │ │  S3     │
│Primary│ │Cache  │ │ (Logs,  │
│+ Read │ │+ DeDup│ │  Audit) │
│Replica│ └───────┘ └─────────┘
└───────┘
```

### Observability Stack

```
Every notification event emits:
  - Structured logs     → ELK Stack (Elasticsearch + Kibana)
  - Metrics             → Prometheus + Grafana
    • notification_sent_total (by channel)
    • notification_failed_total (by channel, reason)
    • delivery_latency_p99
  - Distributed traces  → Jaeger (trace one payment event
                          across all 3 layers end to end)
```

### Technologies Summary

| Concern | Technology | Why |
|---|---|---|
| Compute | Kubernetes (EKS) | Auto-scale workers on queue lag |
| Service mesh | Istio | mTLS, circuit breaker, retries |
| Primary DB | PostgreSQL | ACID, JSONB, partitioning |
| Cache / Dedup | Redis Cluster | Sub-millisecond, TTL support |
| Message bus | Apache Kafka | 1M+ msg/sec, replay, ordering |
| Object store | AWS S3 | Audit logs, cold storage |
| Secrets | AWS Secrets Manager | API keys for Twilio/SES/FCM |
| Logs | ELK Stack | Full-text search on failures |
| Metrics | Prometheus + Grafana | Dashboards + alerting |
| Tracing | Jaeger | End-to-end trace per payment |

---

## How the 3 Layers Relate — The Flow

```
[Payment Service] fires PaymentSuccessEvent
         │
         ▼
[Layer 1 — App]
  - Validate idempotency key (Redis)
  - Persist Notification row (Postgres)
  - Publish to Kafka
         │
         ▼
[Layer 2 — Communication]
  - Kafka consumer picks up event
  - Fetch UserPreference → route to EMAIL + SMS
  - Render template
  - Call Twilio / SES
  - Update NotificationDelivery status
         │
         ▼
[Layer 3 — Infrastructure]
  - Postgres stores final state
  - Metrics emitted to Prometheus
  - Logs shipped to ELK
  - Trace completed in Jaeger
```

---

## The 3 Core Principles Driving Every Decision

1. **Decoupling** — Kafka sits between Layer 1 and Layer 2, so a spike in payments doesn't crash the notification workers. They process at their own pace.

2. **Idempotency** — At every boundary (API intake, Kafka consumption, provider call), we check idempotency keys. A customer should never receive 2 SMS for 1 payment.

3. **Observability first** — In a payment system, silent failures are the worst outcome. Every delivery attempt — success or failure — is traced, logged, and measured.


Yes, exactly! You've nailed the flow. Let me confirm and draw it out precisely:

---

## The Exact Consumer Chain

```
Payment Service
      │
      │ PaymentSuccessEvent { txnId, userId, amount }
      ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Topic: payments.events
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      │
      ▼
┌─────────────────────────────────────┐
│  CONSUMER 1 — Notification Creator  │
│                                     │
│  1. Check idempotency key (Redis)   │
│  2. Persist Notification row in DB  │
│  3. Publish NotificationEvent       │
└─────────────────────────────────────┘
      │
      │ NotificationEvent { notificationId, userId }
      ▼
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Topic: notifications.created
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      │
      ▼
┌─────────────────────────────────────┐
│  CONSUMER 2 — Channel Resolver      │
│                                     │
│  1. Fetch UserPreference (Redis)    │
│     → user wants EMAIL + SMS + PUSH │
│  2. Create 3 NotificationDelivery   │
│     rows in DB (one per channel)    │
│  3. Publish 3 separate messages     │
└─────────────────────────────────────┘
      │              │              │
      ▼              ▼              ▼
━━━━━━━━━━━  ━━━━━━━━━━━━  ━━━━━━━━━━━━━
notifications notifications notifications
.email       .sms         .push
━━━━━━━━━━━  ━━━━━━━━━━━━  ━━━━━━━━━━━━━
      │              │              │
      ▼              ▼              ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│CONSUMER 3│  │CONSUMER 4│  │CONSUMER 5│
│  Email   │  │   SMS    │  │  Push    │
│          │  │          │  │          │
│1.Render  │  │1.Render  │  │1.Render  │
│  template│  │  template│  │  template│
│2.Call SES│  │2.Call    │  │2.Call FCM│
│3.Update  │  │  Twilio  │  │3.Update  │
│  Delivery│  │3.Update  │  │  Delivery│
│  row→SENT│  │  Delivery│  │  row→SENT│
└──────────┘  │  row→SENT│  └──────────┘
              └──────────┘
```

---

## Why this 3-stage pipeline and not one big consumer?

| If one big consumer | With this pipeline |
|---|---|
| Payment spike → notification backlog together | Each stage scales **independently** |
| Channel failures block everything | Email down → SMS + Push still flow |
| Hard to retry specific step | Retry **only** the failed channel consumer |
| One deployment affects all logic | Deploy email handler without touching SMS |

---

## The one thing to be careful about

**Consumer 2 must be idempotent too.**

If Kafka redelivers `notifications.created` (consumer 2 crashed mid-way), it must not create 6 delivery rows instead of 3.

```java
// Before creating delivery rows, check:
boolean alreadyFanned = deliveryRepo
    .existsByNotificationId(notificationId);

if (!alreadyFanned) {
    // create 3 delivery rows + publish 3 messages
}
```

This is called **fan-out idempotency** — the most commonly missed edge case in this design.

# How do you use frameworks like Spring and Hibernate to ensure efficient data handling and seamless middleware integration in a high-performance Java backend? 

Let's go one by one, honestly calling out what applies and what doesn't.

---

## N+1 Queries

### Does it apply to our system?
**Mostly NO** — but one place it could sneak in.

Our consumers process one event at a time. Consumer 1 writes 1 row. Consumer 2 writes 3 rows. No loops fetching related entities.

The one place it could happen:

```
Imagine an admin dashboard query —
"show me all notifications and their delivery statuses"

Hibernate fetches 100 notifications        ← 1 query
Then for each notification fetches
its deliveries separately                  ← 100 queries

Total = 101 queries  ❌
```

Fix — tell Hibernate fetch everything in one JOIN:
```java
@Query("SELECT n FROM Notification n JOIN FETCH n.deliveries WHERE n.userId = :userId")
List<Notification> findWithDeliveries(@Param("userId") UUID userId);
```

**In our Kafka consumers — not a concern. In any reporting/dashboard built on top — absolutely a concern.**

---

## Batch Inserts — Consumer 2 Fan-out

### Does it apply to our system?
**YES — directly.**

Consumer 2 creates 3 delivery rows after resolving channels. By default Hibernate fires 3 separate INSERT statements:

```
INSERT INTO notification_delivery ... EMAIL    ← round trip 1
INSERT INTO notification_delivery ... SMS      ← round trip 2
INSERT INTO notification_delivery ... PUSH     ← round trip 3
```

Each round trip to DB costs ~1-5ms. Doesn't sound much but at 10,000 notifications/sec this adds up fast.

Fix:
```java
// application.properties
hibernate.jdbc.batch_size=50
hibernate.order_inserts=true

// Consumer 2 code
deliveryRepo.saveAll(List.of(emailDelivery, smsDelivery, pushDelivery));
// → Single round trip, one batch INSERT
```

**Directly applies. Always use saveAll() in Consumer 2, never three separate save() calls.**

---

## Lazy Loading in Wrong Context

### Does it apply to our system?
**NO — in Kafka consumers.**

Lazy loading problem happens when you load an entity, close the Hibernate session, then try to access a related entity. Hibernate session is already gone — it explodes.

```
Load Notification          ← session open
Return from method         ← session closed
Access notification.getDeliveries()  ← BOOM, session gone
```

In our Kafka consumers this doesn't happen because:
- Consumer 1 saves notification and publishes event. Done.
- Consumer 2 creates delivery rows. Done.
- Nobody is loading a Notification and then lazily traversing to its deliveries.

**Generic concern — not relevant to our consumer pipeline. Would matter if we built REST APIs on top querying notification history.**

---

## Second Level Cache for Hot Data

### Does it apply to our system?
**YES — specifically for two things.**

In our system Consumer 2 does this for every single notification event:

```
1. Fetch UserPreference  ← "does this user want EMAIL/SMS/PUSH?"
2. Fetch NotificationTemplate ← "what does the SMS message look like?"
```

These two things are read thousands of times per second but almost never change. Without caching:

```
10,000 notifications/sec
= 10,000 UserPreference DB reads/sec
+ 10,000 Template DB reads/sec
= 20,000 unnecessary DB hits/sec  ❌
```

Fix — cache these in Redis, not DB:

```java
@Service
public class UserPreferenceService {

    @Cacheable(value = "userPreferences", key = "#userId")
    public UserPreference getPreference(UUID userId) {
        return preferenceRepo.findByUserId(userId);
        // First call hits DB, stores in Redis
        // Every subsequent call hits Redis
    }
}
```

**Directly applies. UserPreference and NotificationTemplate are perfect cache candidates — hot reads, rarely written.**

---

## HikariCP Connection Pool

### Does it apply to our system?
**YES — this is critical.**

HikariCP is the pool of DB connections Spring maintains. Think of it like a pool of taxi cabs. Each Kafka consumer thread needs a cab (connection) to write to DB. If all cabs are busy, new requests wait.

Default pool size is **10**. In our system:

```
Consumer 1 — 5 threads processing payment events
Consumer 2 — 5 threads processing notification events
Consumer 3/4/5 — 3 threads each for email/sms/push

Total threads potentially needing DB = 5+5+9 = 19 threads
Default pool = 10 connections

9 threads sitting waiting for a connection  ❌
```

Fix:
```java
# application.properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=3000
```

**Directly applies. Under-tuned pool = threads queuing for DB connections = throughput collapse.**

---

## Async Processing — Don't Block Kafka Consumer Thread

### Does it apply to our system?
**YES — Consumer 3/4/5 specifically.**

Kafka gives each topic partition one consumer thread. If that thread is blocked waiting for Twilio to respond, no new messages are processed from that partition.

```
Consumer 3 (SMS thread) picks up event
Calls Twilio — waits 300ms for response    ← thread blocked
No other SMS events processed during this time

At 10,000 SMS/sec — massive backlog builds up  ❌
```

Fix — hand off to separate thread pool immediately:

```java
@KafkaListener(topics = "notifications.sms")
public void consume(NotificationEvent event) {
    smsService.sendAsync(event);  // returns immediately
}                                 // Kafka thread freed instantly

@Async("notificationExecutor")
public CompletableFuture<Void> sendAsync(NotificationEvent event) {
    twilioClient.send(event);           // HTTP call happens in separate thread
    deliveryRepo.updateStatus(SENT);
    return CompletableFuture.completedFuture(null);
}
```

**Directly applies to every channel consumer. Kafka thread should never wait for an external HTTP call.**

---

## Kafka Consumer Tuning — Throughput vs Latency

### Does it apply to our system?
**YES — specifically batch consuming in Consumer 2.**

By default Consumer 2 processes one notification event at a time:

```
Event 1 → fetch preference → create 3 deliveries → save → publish
Event 2 → fetch preference → create 3 deliveries → save → publish
...
```

With batch listening, Consumer 2 picks up say 10 events at once:

```
10 events arrive
→ fetch 10 preferences (can bulk fetch from Redis)
→ create 30 delivery rows
→ saveAll(30 rows) in ONE DB round trip   ← massive win
→ publish 30 messages
```

```java
factory.setBatchListener(true);

@KafkaListener(topics = "notifications.created")
public void consumeBatch(List<NotificationEvent> events) {
    List<NotificationDelivery> deliveries = events.stream()
        .flatMap(e -> buildDeliveries(e).stream())
        .collect(toList());

    deliveryRepo.saveAll(deliveries);  // 30 rows, 1 DB call
}
```

**Directly applies. Batch consuming + saveAll is the single biggest throughput win in Consumer 2.**

---

## Transaction Boundaries — Keep Them Tight

### Does it apply to our system?
**YES — Consumer 3/4/5 is the exact danger zone.**

Channel consumers do two things — write to DB and call external provider. If both are inside one transaction:

```java
@Transactional   ❌
public void process(NotificationEvent event) {
    deliveryRepo.save(delivery);      // DB connection held open
    twilioClient.send(event);         // 300ms HTTP call
    deliveryRepo.updateStatus(SENT);  // connection held 300ms+ total
}
// Connection only released when method exits
// 300ms of connection pool wasted on a Twilio HTTP call
```

Fix — split into two transactions, HTTP call outside both:

```java
public void process(NotificationEvent event) {
    deliveryService.persist(delivery);          // TX 1 — opens, writes, closes fast
    ProviderResponse r = twilioClient.send();   // HTTP call, no transaction
    deliveryService.updateStatus(SENT, r);      // TX 2 — opens, writes, closes fast
}
```

**Directly applies to every channel consumer. Never wrap an external HTTP call inside a @Transactional.**

---

## Circuit Breaker — When Twilio/SES Goes Down

### Does it apply to our system?
**YES — external providers will go down.**

Without circuit breaker:
```
Twilio goes down
Every SMS consumer thread calls Twilio
Every call waits for timeout (30 seconds)
All threads stuck
Kafka consumer lag builds up
System effectively dead  ❌
```

With circuit breaker:
```
Twilio fails 5 times in a row
Circuit OPENS — no more calls to Twilio
Fail fast immediately, push to retry queue
Other channels (Email, Push) completely unaffected
After 30 seconds — circuit tries again  ✅
```

```java
@CircuitBreaker(name = "smsProvider", fallbackMethod = "smsFallback")
public void sendSms(NotificationDelivery delivery) {
    twilioClient.send(delivery);
}

public void smsFallback(NotificationDelivery delivery, Exception ex) {
    // Don't lose the message — push to retry topic
    retryKafkaTemplate.send("notifications.sms.retry", delivery);
    deliveryRepo.updateStatus(delivery.getId(), RETRY_QUEUED);
}
```

**Directly applies. In a payment notification system, SMS going down should never affect Email or Push delivery.**

---

## Summary — What Actually Matters in Our System

| Problem | Applies? | Where |
|---|---|---|
| N+1 Queries | Partially | Dashboard/reporting only, not consumers |
| Batch Inserts | ✅ YES | Consumer 2 — always saveAll() |
| Lazy Loading | NO | Not relevant in event-driven consumers |
| Second Level Cache | ✅ YES | UserPreference + Templates in Redis |
| HikariCP Tuning | ✅ YES | Pool must cover all consumer threads |
| Async Processing | ✅ YES | Channel consumers — never block Kafka thread |
| Kafka Batch Tuning | ✅ YES | Consumer 2 — batch consume + saveAll |
| Transaction Boundaries | ✅ YES | Never wrap HTTP calls in @Transactional |
| Circuit Breaker | ✅ YES | Every external provider — Twilio/SES/FCM |


Great questions. Let me go deep on each one precisely.

---

## 1. Batch Inserts — How exactly does it work?

### First, understand what happens by default

```
You call save() 3 times

Hibernate does this:
  TX opens
  INSERT delivery 1  → flush to DB immediately
  INSERT delivery 2  → flush to DB immediately  
  INSERT delivery 3  → flush to DB immediately
  TX commits

= 3 separate round trips to DB
```

### Does saveAll() alone fix it?

**NO. saveAll() alone does NOT batch.**

saveAll() is just a loop internally:
```java
// Spring's actual saveAll() source code
public <S extends T> List<S> saveAll(Iterable<S> entities) {
    for (S entity : entities) {
        save(entity);   // still saves one by one!
    }
}
```

So without config, saveAll() = 3 saves = 3 round trips. **Same problem.**

---

### What actually enables batching?

**Step 1 — Config changes are mandatory**

```properties
# application.properties

# How many rows to batch together before sending to DB
spring.jpa.properties.hibernate.jdbc.batch_size=50

# Without these two, Hibernate mixes INSERT order
# and breaks batching silently
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true

# Show what Hibernate is actually doing (disable in prod)
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.generate_statistics=true
```

**Step 2 — Entity must NOT use IDENTITY generation**

This is the most common reason batching silently fails:

```java
// ❌ BREAKS batching — Hibernate must go to DB after each insert
// to get the generated ID back, so it can't batch
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

// ✅ ALLOWS batching — ID known before insert, no DB trip needed
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE,
                generator = "delivery_seq")
@SequenceGenerator(name = "delivery_seq",
                   allocationSize = 50)  // pre-fetches 50 IDs at once
private Long id;

// ✅ SIMPLEST — use UUID, always works with batching
@Id
private UUID id = UUID.randomUUID();
```

**Step 3 — Then saveAll() works correctly**

```java
// Consumer 2
List<NotificationDelivery> deliveries = List.of(
    emailDelivery, smsDelivery, pushDelivery
);
deliveryRepo.saveAll(deliveries);

// What Hibernate now does internally:
// TX opens
// Collects all 3 inserts in memory
// Sends ONE batch statement to DB:
//   INSERT INTO notification_delivery VALUES (?,?,?),(?,?,?),(?,?,?)
// TX commits
// = 1 round trip  ✅
```

---

### When does Hibernate flush to DB?

This is the key question. Hibernate has a concept called **session** — it holds changes in memory before writing to DB.

```
You call saveAll(3 deliveries)
         │
         ▼
Hibernate Session (in memory)
┌─────────────────────────────┐
│  pending: delivery1         │
│  pending: delivery2         │  ← not in DB yet
│  pending: delivery3         │
└─────────────────────────────┘
         │
         │ flush happens when:
         ▼
1. Transaction commits        ← most common
2. You call flush() manually
3. Before a query on same table (to avoid stale reads)
4. batch_size threshold hit   ← e.g. every 50 rows
```

**For our Consumer 2 — 3 deliveries:**
```
saveAll(3 rows)
→ held in session memory
→ transaction commits at end of method
→ flush fires
→ 1 batch INSERT sent to DB
```

**What if Consumer 2 was processing 200 events in a batch?**
```
saveAll(600 delivery rows)  ← 200 events × 3 channels
→ batch_size=50 means:
   flush at row 50   → 1 DB call (50 rows)
   flush at row 100  → 1 DB call (50 rows)
   flush at row 150  → 1 DB call (50 rows)
   ...
   flush at row 600  → 1 DB call (50 rows)
= 12 DB calls instead of 600  ✅
```

---

## 2. Lazy Loading and Sessions

### What is a Session?

Hibernate Session = the bridge between your Java code and DB. It's opened when a transaction starts, closed when it ends.

```
@Transactional method starts  →  Session OPENS
  - all DB reads/writes go through this session
  - loaded entities are tracked here
@Transactional method ends    →  Session CLOSES
  - all tracked entities detached
  - any lazy access after this = BOOM
```

### What is Lazy Loading?

When you load an entity, Hibernate does NOT automatically load its related entities. It waits until you actually access them.

```java
@Entity
public class Notification {
    @Id
    private UUID id;

    // LAZY = don't load deliveries until accessed
    @OneToMany(fetch = FetchType.LAZY)
    private List<NotificationDelivery> deliveries;
}
```

```
Notification n = notificationRepo.findById(id);
// DB query: SELECT * FROM notification WHERE id = ?
// deliveries NOT loaded yet — just a proxy placeholder
// Session still open — fine so far

n.getDeliveries()  // NOW Hibernate fires:
// SELECT * FROM notification_delivery WHERE notification_id = ?
// Works fine IF session is still open
```

### The Problem — Session closes before access

```java
// ❌ Classic mistake
@Service
public class NotificationService {

    // No @Transactional here
    public Notification getNotification(UUID id) {
        return notificationRepo.findById(id).get();
        // Session opens and closes inside findById
        // Entity returned but session GONE
    }
}

// Caller does:
Notification n = service.getNotification(id);
n.getDeliveries();  // LazyInitializationException
                    // Session is closed, Hibernate can't go to DB
```

### Does this apply to our Kafka consumers?

**NO — and here's exactly why.**

Our consumers don't load a Notification and then traverse to deliveries.

```
Consumer 1:
  receives PaymentEvent
  creates new Notification object
  calls save(notification)
  Done — never reads deliveries

Consumer 2:
  receives NotificationEvent {notificationId}
  fetches UserPreference
  creates 3 new NotificationDelivery objects
  calls saveAll(deliveries)
  Done — never loads Notification entity and traverses it
```

**Where it WOULD matter in our system:**

```java
// If you build a status check API:
// GET /notifications/{id}/status

@Transactional  // ← THIS is what saves you
public NotificationStatusDTO getStatus(UUID id) {
    Notification n = notificationRepo.findById(id).get();
    // Session open, inside transaction

    List<NotificationDelivery> deliveries = n.getDeliveries();
    // Hibernate fires SELECT here — session still open, works fine

    return buildDTO(n, deliveries);
}
// Session closes here — after we're done with deliveries
```

**Rule: Any time you load an entity and access its relations, wrap it in @Transactional.**

---

## 3. HikariCP Connection Pool

### What is it exactly?

Opening a DB connection is expensive — authentication, network handshake, memory allocation. Takes ~50-100ms.

HikariCP maintains a **pool of already-open connections**. Your code borrows one, uses it, returns it.

```
HikariCP Pool (size=10)
┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
│ C1 │ C2 │ C3 │ C4 │ C5 │ C6 │ C7 │ C8 │ C9 │C10│
└────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘
  ↑ all idle, waiting to be borrowed

Thread 1 needs DB → borrows C1
Thread 2 needs DB → borrows C2
...
Thread 10 needs DB → borrows C10

Thread 11 needs DB → WAITS
                      ↑
                      sits here until someone returns a connection
                      if waits > connection-timeout → exception thrown
```

### Default config and why it breaks under load

```properties
# Spring Boot defaults
maximum-pool-size = 10   # way too low for our system
connection-timeout = 30000ms  # 30 seconds wait — too long
```

### Our system's thread count

```
Consumer 1 (payment events)     = 3 threads
Consumer 2 (notification events) = 3 threads
Consumer 3 (email)              = 3 threads
Consumer 4 (sms)                = 3 threads
Consumer 5 (push)               = 3 threads
Async thread pool (Twilio calls) = 20 threads
REST API threads                 = 10 threads
                                 ─────────────
Total potentially needing DB     = 45 threads
Default pool size                = 10

35 threads waiting for connections at peak  ❌
```

### Correct config for our system

```properties
# application.properties

# Max connections in pool
# Rule: (num_cores × 2) + num_disks
# 4 core machine → ~10 per instance
# But we have many threads so go higher
spring.datasource.hikari.maximum-pool-size=20

# Keep 5 alive even when idle
spring.datasource.hikari.minimum-idle=5

# How long thread waits for a connection before failing
# 3 seconds — fail fast, don't queue forever
spring.datasource.hikari.connection-timeout=3000

# How long a connection can sit idle before removed
spring.datasource.hikari.idle-timeout=600000

# Max lifetime of a connection (prevents stale connections)
spring.datasource.hikari.max-lifetime=1800000
```

### The tight transaction connection — why it matters

Connection is borrowed at **start of @Transactional** and returned at **end of @Transactional**.

```
❌ Transaction wrapping HTTP call:

@Transactional
public void process(event) {
    save(delivery)          // borrows connection at method start
    twilioClient.send()     // 300ms — connection sitting idle
    updateStatus(SENT)      // connection finally used again
}                           // connection returned here

Connection held for 300ms+
At 20 threads all doing this = pool exhausted instantly


✅ Tight transaction:

public void process(event) {
    persist(delivery)       // TX opens, borrows, writes, returns — 5ms
                            // connection back in pool
    twilioClient.send()     // no connection held during this
    updateStatus(SENT)      // TX opens, borrows, writes, returns — 5ms
}

Connection held for 5ms total
Pool never exhausted  ✅
```

---

## Summary of how these three connect

```
Batch Inserts
→ Config (batch_size + order_inserts) + UUID IDs + saveAll()
→ Flush happens at TX commit or every batch_size rows
→ Directly applies to Consumer 2

Lazy Loading
→ Session lives inside @Transactional boundary
→ Access lazy relations outside = exception
→ Not relevant in our consumers
→ Relevant if building APIs on top — always use @Transactional

HikariCP
→ Pool of pre-opened DB connections
→ Borrowed at TX start, returned at TX end
→ Long transactions = connections held = pool exhausted
→ Fix = keep transactions tight, never wrap HTTP calls in @Transactional
→ Size pool to cover all threads that need DB simultaneously
```

Ready for Async Processing and Circuit Breaker when you are.

Let's go one by one, deep and precise.

---

## 1. Kafka Consumer Tuning — Batch vs Single

### First, how Kafka delivers messages by default

```
Kafka Topic: notifications.created
Partition 0: [msg1, msg2, msg3, msg4, msg5...]

Default behaviour:
Consumer thread picks up msg1
  → processes it completely
  → commits offset
Consumer thread picks up msg2
  → processes it completely
  → commits offset
...
One message at a time, one round trip to DB each time
```

### The problem at high volume

```
10,000 notification events/sec coming in

Consumer 2 per message:
  fetch UserPreference from Redis     ~1ms
  create 3 delivery objects           ~0ms
  saveAll(3 rows) to DB               ~5ms
  publish 3 Kafka messages            ~2ms
  commit Kafka offset                 ~1ms
                                    ──────
  Total per message                   ~9ms

Max throughput = 1000ms / 9ms = ~111 messages/sec per thread

You need 10,000/sec
You'd need 90 consumer threads just for Consumer 2  ❌
```

### Batch consuming fixes this

```
Instead of processing 1 message at a time:
Consumer picks up 100 messages at once

  fetch 100 UserPreferences from Redis  ~5ms  (bulk get)
  create 300 delivery objects           ~1ms
  saveAll(300 rows) ONE DB call         ~8ms  (vs 100 × 5ms = 500ms)
  publish 300 Kafka messages            ~10ms
  commit offset once for all 100        ~1ms
                                       ──────
  Total for 100 messages               ~25ms

Throughput = 100 messages / 25ms = 4,000 messages/sec per thread
vs 111/sec before — 36x improvement  ✅
```

### How to configure it

```java
// Step 1 — Kafka consumer properties
@Bean
public ConsumerFactory<String, NotificationEvent> consumerFactory() {
    Map<String, Object> props = new HashMap<>();

    // How many records Kafka returns in one poll()
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

    // Wait up to 500ms to fill the batch
    // If 100 records arrive before 500ms → deliver immediately
    props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
    props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

    return new DefaultKafkaConsumerFactory<>(props);
}

// Step 2 — Tell Spring to use batch listener
@Bean
public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>
    kafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(consumerFactory());
    factory.setBatchListener(true);   // ← key line

    // Manual offset commit — only after ALL 100 processed
    factory.getContainerProperties()
           .setAckMode(AckMode.MANUAL_IMMEDIATE);

    // How many threads per consumer
    // = number of partitions ideally
    factory.setConcurrency(3);

    return factory;
}

// Step 3 — Consumer 2 now receives a List
@KafkaListener(
    topics = "notifications.created",
    containerFactory = "kafkaListenerContainerFactory"
)
public void consumeBatch(
    List<NotificationEvent> events,
    Acknowledgment ack             // manual commit handle
) {
    // Build all delivery rows in memory first
    List<NotificationDelivery> allDeliveries = events.stream()
        .flatMap(event -> buildDeliveries(event).stream())
        .collect(toList());

    // ONE DB call for all 300 rows
    deliveryRepo.saveAll(allDeliveries);

    // Publish all channel messages
    allDeliveries.forEach(d ->
        kafkaTemplate.send("notifications." + d.getChannelType().toLowerCase(), d)
    );

    // Only commit offset after everything succeeded
    ack.acknowledge();
}
```

### What happens if batch processing fails halfway?

```
100 events received
Processed 60 successfully
DB write fails at row 61
  → ack.acknowledge() never called
  → Kafka offset NOT committed
  → All 100 messages redelivered on restart

Problem: first 60 get processed TWICE

Fix — idempotency check in Consumer 2:
```

```java
List<NotificationDelivery> allDeliveries = events.stream()
    .filter(event -> !deliveryRepo.existsByNotificationId(event.getNotificationId()))
    .flatMap(event -> buildDeliveries(event).stream())
    .collect(toList());
// Already processed ones skipped silently
```

---

## 2. Async Processing — Don't Block Kafka Thread

### The exact problem

Kafka assigns one thread per partition. That thread is responsible for polling new messages AND processing them.

```
Partition 0 → Thread A
Partition 1 → Thread B
Partition 2 → Thread C

Thread A picks up SMS delivery event
Thread A calls Twilio API          ← blocks for 300ms
  │
  │ During these 300ms:
  │ No new messages read from Partition 0
  │ Kafka sees Thread A not polling
  │ If silent > max.poll.interval.ms (5min default)
  │ Kafka thinks consumer is dead
  │ Triggers rebalance — expensive  ❌
  │
Thread A gets Twilio response
Thread A picks up next message
```

### The fix — hand off immediately

```java
// Channel consumer — Thread A
@KafkaListener(topics = "notifications.sms")
public void consume(NotificationDelivery delivery, Acknowledgment ack) {

    // Hand off to separate thread pool instantly
    // Thread A is FREE immediately — goes back to polling Kafka
    smsService.sendAsync(delivery);

    // Commit offset here — message accepted for processing
    ack.acknowledge();
}
```

### But wait — there's a problem with this approach

```
Thread A commits offset and returns to Kafka polling
Async thread calls Twilio — fails
  → message already committed
  → Kafka won't redeliver it
  → notification silently lost  ❌
```

### The correct async pattern for our system

```java
// Don't commit offset until async completes
@KafkaListener(topics = "notifications.sms")
public void consume(NotificationDelivery delivery, Acknowledgment ack) {
    smsService.sendAsync(delivery)
        .whenComplete((result, ex) -> {
            if (ex == null) {
                ack.acknowledge();          // success → commit offset
            } else {
                // Don't ack → Kafka redelivers
                // OR push to retry topic explicitly
                retryTemplate.send("notifications.sms.retry", delivery);
                ack.acknowledge();          // ack original, retry handles it
            }
        });
}

// Separate thread pool — sized for external HTTP calls
@Async("smsExecutor")
public CompletableFuture<Void> sendAsync(NotificationDelivery delivery) {
    try {
        ProviderResponse response = twilioClient.send(delivery);
        deliveryRepo.updateStatus(delivery.getId(), SENT, response.getMsgId());
        return CompletableFuture.completedFuture(null);
    } catch (Exception ex) {
        deliveryRepo.updateStatus(delivery.getId(), FAILED, null);
        return CompletableFuture.failedFuture(ex);
    }
}

// Thread pool config
@Bean("smsExecutor")
public Executor smsExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    // Sized for Twilio HTTP calls — these are IO bound
    // Can have many threads waiting on network
    executor.setCorePoolSize(20);
    executor.setMaxPoolSize(50);

    // Queue up to 500 before rejecting
    executor.setQueueCapacity(500);

    executor.setThreadNamePrefix("sms-async-");

    // When queue full — caller thread runs it
    // Prevents silent drops under extreme load
    executor.setRejectedExecutionHandler(new CallerRunsPolicy());
    return executor;
}
```

---

## 3. Transaction Boundaries

### The core rule

**A DB connection is borrowed when @Transactional opens. Returned when it closes. Every millisecond in between = wasted pool capacity.**

### The exact danger in our channel consumers

```java
// ❌ What seems natural but is wrong
@Transactional
public void processSmDelivery(NotificationDelivery delivery) {
    // TX opens, connection borrowed from HikariCP pool

    delivery.setStatus(PROCESSING);
    deliveryRepo.save(delivery);        // 5ms — DB write

    twilioClient.send(delivery);        // 300ms — HTTP call
                                        // connection sitting IDLE
                                        // doing absolutely nothing

    delivery.setStatus(SENT);
    deliveryRepo.save(delivery);        // 5ms — DB write

    // TX closes, connection returned
    // Total connection held: 310ms
}

// At 50 concurrent SMS deliveries:
// 50 connections × 310ms = pool exhausted constantly
```

```java
// ✅ Correct — two tight transactions, HTTP call outside
public void processSmDelivery(NotificationDelivery delivery) {

    // TX 1 — opens, writes, closes in ~5ms
    deliveryService.markProcessing(delivery.getId());
    // Connection returned to pool HERE

    // HTTP call — no connection held
    ProviderResponse response = twilioClient.send(delivery);

    // TX 2 — opens, writes, closes in ~5ms
    deliveryService.markSent(delivery.getId(), response.getMsgId());
    // Connection returned to pool HERE
}

@Transactional
public void markProcessing(UUID id) {
    deliveryRepo.updateStatus(id, PROCESSING);
}   // ← connection returned here, after ~5ms

@Transactional
public void markSent(UUID id, String providerMsgId) {
    deliveryRepo.updateStatus(id, SENT, providerMsgId);
}   // ← connection returned here, after ~5ms
```

### What if Twilio call succeeds but second TX fails?

```
markProcessing()  ✅  status = PROCESSING in DB
twilioClient.send() ✅  SMS delivered to customer
markSent()  ❌  DB write fails

Result:
  Customer received SMS  ✅
  DB still says PROCESSING  ❌
  Retry logic fires again
  Customer gets duplicate SMS  ❌
```

Fix — make Twilio call idempotent with a unique key:

```java
// Pass idempotency key to Twilio
// If same key sent twice, Twilio ignores second call
twilioClient.send(delivery, delivery.getId().toString());
// Second send attempt → Twilio says "already sent, here's original msgId"
// markSent() succeeds with same msgId
// No duplicate SMS  ✅
```

---

## 4. Circuit Breaker

### The problem without it

```
Twilio goes down at 10:00am

10:00:00  SMS thread 1 calls Twilio — waits 30s timeout
10:00:00  SMS thread 2 calls Twilio — waits 30s timeout
10:00:00  SMS thread 3 calls Twilio — waits 30s timeout
...
10:00:00  All 50 async threads calling Twilio — all waiting 30s

Thread pool exhausted
New SMS events pile up in queue
Queue fills up (500 capacity)
CallerRunsPolicy kicks in — Kafka thread now running SMS sends
Kafka thread blocked — stops polling
Kafka rebalance triggers
Entire SMS consumer dead  ❌

Meanwhile Email and Push still working fine
But they're also getting impacted by thread starvation
```

### Circuit Breaker — the concept

```
CLOSED state (normal)
  → calls go through to Twilio
  → tracking failure rate

Failure rate crosses threshold (say 50%)
  → circuit OPENS

OPEN state
  → calls FAIL IMMEDIATELY — no waiting
  → fallback executes instead
  → Twilio gets breathing room

After 30 seconds
  → circuit goes HALF-OPEN
  → one test call allowed through

Test call succeeds
  → circuit CLOSES — back to normal

Test call fails
  → circuit stays OPEN — wait another 30s
```

### Implementation with Resilience4j

```java
// Step 1 — Config
# application.properties

# SMS provider circuit breaker
resilience4j.circuitbreaker.instances.smsProvider
    .failure-rate-threshold=50        # open after 50% failures
    .minimum-number-of-calls=10       # need at least 10 calls to calculate rate
    .wait-duration-in-open-state=30s  # stay open for 30s
    .sliding-window-size=20           # look at last 20 calls
    .permitted-number-of-calls-in-half-open-state=3  # test with 3 calls

# Retry config — before circuit breaker counts it as failure
resilience4j.retry.instances.smsProvider
    .max-attempts=3
    .wait-duration=2s
    .retry-exceptions=java.io.IOException
```

```java
// Step 2 — Apply to SMS handler
@Service
public class SmsChannelHandler {

    // Retry 3 times first
    // THEN if still failing, circuit breaker counts it
    @CircuitBreaker(name = "smsProvider", fallbackMethod = "smsFallback")
    @Retry(name = "smsProvider")
    public void send(NotificationDelivery delivery) {
        twilioClient.send(delivery);
        deliveryRepo.updateStatus(delivery.getId(), SENT);
    }

    // Called when circuit is OPEN or all retries exhausted
    public void smsFallback(NotificationDelivery delivery, Exception ex) {
        log.warn("SMS unavailable for deliveryId={}, reason={}",
                 delivery.getId(), ex.getMessage());

        // Don't lose the message — push to retry topic
        // Will be reprocessed when Twilio recovers
        retryKafkaTemplate.send("notifications.sms.retry", delivery);

        // Update DB so we know it's queued for retry
        deliveryRepo.updateStatus(delivery.getId(), RETRY_QUEUED);
    }
}
```

### How retry topic works

```
notifications.sms.retry topic
  → separate consumer with delay
  → reads every 60 seconds
  → attempts resend
  → if circuit still open → back to retry topic
  → max retry attempts → mark FAILED, alert ops team
```

---

## How all four connect in Consumer 3/4/5

```
Kafka delivers batch of 10 SMS delivery events
          │
          ▼
SMS Kafka Consumer Thread (Thread A)
  for each delivery:
    smsService.sendAsync(delivery)   ← hands off, Thread A free instantly
  Thread A back to polling Kafka     ← never blocked


Async Thread Pool (Thread B, C, D...)
  for each delivery:

    ┌─────────────────────────────────────┐
    │ TX 1 (5ms)                          │
    │   markProcessing(deliveryId)        │
    │   connection borrowed + returned    │
    └─────────────────────────────────────┘

    ┌─────────────────────────────────────┐
    │ Circuit Breaker check               │
    │   OPEN?  → smsFallback()            │
    │           → push to retry topic     │
    │   CLOSED? → proceed                 │
    └─────────────────────────────────────┘

    ┌─────────────────────────────────────┐
    │ Retry (up to 3 attempts)            │
    │   twilioClient.send()               │
    │   no DB connection held here        │
    └─────────────────────────────────────┘

    ┌─────────────────────────────────────┐
    │ TX 2 (5ms)                          │
    │   markSent(deliveryId, msgId)       │
    │   connection borrowed + returned    │
    └─────────────────────────────────────┘
```

---

## The single thread timeline — before vs after

```
BEFORE (naive):
Thread: [──TX open──][──DB write 5ms──][──Twilio 300ms──][──DB write 5ms──][──TX close──]
        ←────────────── connection held 310ms ──────────────────────────────────────────→


AFTER (correct):
Thread: [TX1 5ms][free][──Twilio 300ms──][TX2 5ms][free]
         ↑                                  ↑
    connection                         connection
    held 5ms                           held 5ms
    returned                           returned
```

**Connection pool pressure drops by 98%.** That's the entire point of getting transaction boundaries right.

Let's go deep and precise. First understand what each one does independently, then how they work together.

---

## The 4 Resilience4j Concepts First

```
RETRY          → try again if it fails
CIRCUIT BREAKER → stop trying if failing too much
TIMEOUT        → don't wait forever
BULKHEAD       → limit concurrent calls (bonus - important for our system)

These are separate concerns
Each can be used alone or combined
```

---

## 1. Retry — Deep Dive

### What it actually does

```
You call Twilio
Twilio returns 500 (server error)

Without Retry:
  Exception thrown immediately  ❌

With Retry:
  Attempt 1 fails → wait 2s
  Attempt 2 fails → wait 4s  (exponential)
  Attempt 3 fails → wait 8s
  Give up → throw exception to caller
```

### Config — every option explained

```properties
# Retry for SMS provider
resilience4j.retry.instances.smsProvider

  # How many total attempts (1 original + 2 retries = 3 total)
  .max-attempts=3

  # Wait between retries
  .wait-duration=2s

  # Exponential backoff — each wait doubles
  # attempt 1 fails → wait 2s
  # attempt 2 fails → wait 4s
  # attempt 3 fails → give up
  .enable-exponential-backoff=true
  .exponential-backoff-multiplier=2

  # Cap the wait — don't wait more than 10s ever
  .exponential-max-wait-duration=10s

  # Retry on these exceptions
  .retry-exceptions=java.io.IOException,
                    java.net.SocketTimeoutException,
                    feign.RetryableException

  # Never retry on these — no point
  # 400 Bad Request = our bug, retrying won't help
  # 401 Unauthorized = wrong API key, retrying won't help
  .ignore-exceptions=com.notification.exception.BadRequestException,
                     com.notification.exception.AuthException
```

### Implementation

```java
@Service
public class SmsChannelHandler {

    @Retry(name = "smsProvider", fallbackMethod = "retryFallback")
    public void send(NotificationDelivery delivery) {
        twilioClient.send(delivery);
    }

    // Called only after ALL retry attempts exhausted
    public void retryFallback(NotificationDelivery delivery, Exception ex) {
        log.error("All retries exhausted for delivery={}", delivery.getId());
        deliveryRepo.updateStatus(delivery.getId(), FAILED, ex.getMessage());
        // Push to manual review queue
        deadLetterKafkaTemplate.send("notifications.dlq", delivery);
    }
}
```

### Critical — what counts as retryable in our system

```
Twilio 500/503    → retryable  (their server down temporarily)
Network timeout   → retryable  (transient network issue)
Twilio 400        → NOT retryable  (bad phone number format — our bug)
Twilio 401        → NOT retryable  (wrong API key — fix config)
Twilio 429        → retryable BUT with longer wait  (rate limited)
```

```java
// Custom retry condition for 429 rate limiting
resilience4j.retry.instances.smsProvider
  .result-predicate=com.notification.retry.TwilioRetryPredicate

@Component
public class TwilioRetryPredicate implements Predicate<Object> {
    @Override
    public boolean test(Object result) {
        if (result instanceof TwilioException ex) {
            // 429 = rate limited → retry with longer backoff
            return ex.getStatusCode() == 429;
        }
        return false;
    }
}
```

---

## 2. Timeout — Deep Dive

### What it actually does

```
Without timeout:
  Thread calls Twilio
  Twilio hangs — no response
  Thread waits forever
  Thread pool exhausted after 50 such calls
  System dead  ❌

With timeout:
  Thread calls Twilio
  Twilio hangs
  After 3 seconds → TimeoutException thrown
  Thread freed immediately  ✅
  Retry kicks in with next attempt
```

### Config

```properties
# Timeout for SMS provider
resilience4j.timelimiter.instances.smsProvider

  # Give up after 3 seconds
  .timeout-duration=3s

  # Throw TimeoutException when limit hit
  # false = just cancel, true = throw exception
  .cancel-running-future=true
```

### Critical — Timeout only works with async (CompletableFuture)

This is the most commonly missed point:

```java
// ❌ Timeout does NOT work on synchronous calls
@TimeLimiter(name = "smsProvider")
public void sendSync(NotificationDelivery delivery) {
    twilioClient.send(delivery);  // TimeLimiter ignored here
}

// ✅ Timeout ONLY works with CompletableFuture
@TimeLimiter(name = "smsProvider", fallbackMethod = "timeoutFallback")
public CompletableFuture<Void> sendAsync(NotificationDelivery delivery) {
    return CompletableFuture.supplyAsync(() -> {
        twilioClient.send(delivery);
        return null;
    }, smsExecutor);  // runs in our thread pool
}

public CompletableFuture<Void> timeoutFallback(
        NotificationDelivery delivery,
        TimeoutException ex) {
    log.warn("Twilio timed out for delivery={}", delivery.getId());
    deliveryRepo.updateStatus(delivery.getId(), RETRY_QUEUED);
    retryKafkaTemplate.send("notifications.sms.retry", delivery);
    return CompletableFuture.completedFuture(null);
}
```

### Why — how TimeLimiter works internally

```
CompletableFuture submitted to thread pool
TimeLimiter starts a 3s countdown

Scenario A — Twilio responds in 1s:
  Future completes  ✅
  TimeLimiter cancels countdown

Scenario B — Twilio hangs past 3s:
  TimeLimiter cancels the Future
  TimeoutException thrown
  fallback executes
  Thread freed  ✅
```

---

## 3. Circuit Breaker — Deep Dive

### The states in detail

```
          failure rate > 50%           test call fails
CLOSED ──────────────────────→ OPEN ──────────────────→ OPEN
  ↑                              │                      (reset timer)
  │    test calls succeed        │
  └──────────────────────────────┘
              HALF-OPEN
```

### Config — every option explained

```properties
resilience4j.circuitbreaker.instances.smsProvider

  # How many recent calls to track
  .sliding-window-type=COUNT_BASED   # or TIME_BASED
  .sliding-window-size=20            # last 20 calls

  # Don't open circuit until at least 10 calls made
  # Prevents opening on first failure
  .minimum-number-of-calls=10

  # Open circuit if 50% of last 20 calls failed
  .failure-rate-threshold=50

  # Also open if calls are too slow (not just failing)
  # Twilio taking 5s+ every call is also a problem
  .slow-call-rate-threshold=50       # 50% of calls slow = open
  .slow-call-duration-threshold=3s   # slow = more than 3s

  # Stay open for 30s before trying again
  .wait-duration-in-open-state=30s

  # In HALF-OPEN, allow 3 test calls through
  .permitted-number-of-calls-in-half-open-state=3

  # Record these as failures
  .record-exceptions=java.io.IOException,
                     java.util.concurrent.TimeoutException

  # Don't count these as failures
  # 400 is our bug, not Twilio's problem
  .ignore-exceptions=com.notification.exception.BadRequestException
```

### Implementation

```java
@Service
public class SmsChannelHandler {

    @CircuitBreaker(name = "smsProvider", fallbackMethod = "circuitFallback")
    public CompletableFuture<Void> send(NotificationDelivery delivery) {
        return CompletableFuture.supplyAsync(() -> {
            twilioClient.send(delivery);
            return null;
        }, smsExecutor);
    }

    // Called when circuit is OPEN — Twilio not even contacted
    public CompletableFuture<Void> circuitFallback(
            NotificationDelivery delivery,
            CallNotPermittedException ex) {  // specific to OPEN circuit
        log.warn("Circuit OPEN — skipping Twilio for delivery={}",
                 delivery.getId());
        deliveryRepo.updateStatus(delivery.getId(), RETRY_QUEUED);
        retryKafkaTemplate.send("notifications.sms.retry", delivery);
        return CompletableFuture.completedFuture(null);
    }
}
```

### Monitor circuit state — important for ops

```java
@Component
public class CircuitBreakerMonitor {

    @Autowired
    private CircuitBreakerRegistry registry;

    @PostConstruct
    public void registerListeners() {
        CircuitBreaker smsCB = registry.circuitBreaker("smsProvider");

        smsCB.getEventPublisher()
            .onStateTransition(event -> {
                log.error("SMS Circuit state changed: {} → {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState());
                // Alert ops team — PagerDuty/Slack alert here
                alertService.notify("SMS circuit breaker: " +
                    event.getStateTransition().getToState());
            });
    }
}
```

---

## 4. Combining All Three — The Right Order

### Order matters — this is critical

```
Request comes in
      │
      ▼
  BULKHEAD          ← first — limit concurrent calls
      │
      ▼
  CIRCUIT BREAKER   ← second — is provider healthy?
      │
      ▼
  TIMEOUT           ← third — don't wait forever
      │
      ▼
  RETRY             ← last — retry individual attempts
      │
      ▼
  Twilio API
```

**Why this order:**
```
Bulkhead first  → don't even attempt if too many concurrent calls
CB second       → don't attempt if provider known to be down
Timeout third   → each attempt has a time limit
Retry last      → retry each timed-out/failed attempt
```

### Full implementation — all four together

```java
@Service
public class SmsChannelHandler {

    // Annotations applied in ORDER — bottom executes first
    // So execution order = TimeLimiter → CircuitBreaker → Retry
    @Bulkhead(name = "smsProvider", type = Bulkhead.Type.THREADPOOL)
    @CircuitBreaker(name = "smsProvider", fallbackMethod = "fallback")
    @TimeLimiter(name = "smsProvider")
    @Retry(name = "smsProvider")
    public CompletableFuture<Void> send(NotificationDelivery delivery) {
        return CompletableFuture.supplyAsync(() -> {
            String msgId = twilioClient.send(
                delivery.getPhoneNumber(),
                delivery.getRenderedContent(),
                delivery.getId().toString()  // idempotency key
            );
            deliveryRepo.updateStatus(delivery.getId(), SENT, msgId);
            return null;
        }, smsExecutor);
    }

    // Handles ALL failures — CB open, timeout, retries exhausted
    public CompletableFuture<Void> fallback(
            NotificationDelivery delivery,
            Exception ex) {

        // Differentiate failure types for proper handling
        if (ex instanceof CallNotPermittedException) {
            // Circuit open — Twilio is down
            log.warn("Circuit OPEN — queuing for retry");
            retryKafkaTemplate.send("notifications.sms.retry", delivery);
            deliveryRepo.updateStatus(delivery.getId(), RETRY_QUEUED);

        } else if (ex instanceof TimeoutException) {
            // Twilio too slow — retry later
            log.warn("Twilio timeout — queuing for retry");
            retryKafkaTemplate.send("notifications.sms.retry", delivery);
            deliveryRepo.updateStatus(delivery.getId(), RETRY_QUEUED);

        } else if (ex instanceof BulkheadFullException) {
            // Too many concurrent calls — back pressure
            log.warn("Bulkhead full — queuing for retry");
            retryKafkaTemplate.send("notifications.sms.retry", delivery);
            deliveryRepo.updateStatus(delivery.getId(), RETRY_QUEUED);

        } else {
            // All retries exhausted — dead letter
            log.error("All attempts failed — sending to DLQ");
            deadLetterKafkaTemplate.send("notifications.dlq", delivery);
            deliveryRepo.updateStatus(delivery.getId(), FAILED);
        }

        return CompletableFuture.completedFuture(null);
    }
}
```

### Full config for all four

```properties
# SMS Provider — all four patterns

# RETRY
resilience4j.retry.instances.smsProvider
  .max-attempts=3
  .wait-duration=2s
  .enable-exponential-backoff=true
  .exponential-backoff-multiplier=2
  .exponential-max-wait-duration=10s
  .retry-exceptions=java.io.IOException,
                    java.util.concurrent.TimeoutException

# TIMEOUT
resilience4j.timelimiter.instances.smsProvider
  .timeout-duration=3s
  .cancel-running-future=true

# CIRCUIT BREAKER
resilience4j.circuitbreaker.instances.smsProvider
  .sliding-window-size=20
  .minimum-number-of-calls=10
  .failure-rate-threshold=50
  .slow-call-rate-threshold=50
  .slow-call-duration-threshold=3s
  .wait-duration-in-open-state=30s
  .permitted-number-of-calls-in-half-open-state=3

# BULKHEAD — max concurrent Twilio calls
resilience4j.thread-pool-bulkhead.instances.smsProvider
  .max-thread-pool-size=20    # max 20 concurrent Twilio calls
  .core-thread-pool-size=10
  .queue-capacity=100         # queue 100 more before rejecting
```

---

## Full Flow — One SMS Delivery

```
Async thread picks up delivery
        │
        ▼
Bulkhead — 20 slots available? YES → proceed
        │
        ▼
Circuit Breaker — CLOSED → proceed
        │
        ▼
Attempt 1:
  TimeLimiter starts 3s countdown
  twilioClient.send() → timeout after 3s  ❌
  Retry — wait 2s
        │
        ▼
Attempt 2:
  TimeLimiter starts fresh 3s countdown
  twilioClient.send() → timeout after 3s  ❌
  CB records 2nd failure
  Retry — wait 4s
        │
        ▼
Attempt 3:
  TimeLimiter starts fresh 3s countdown
  twilioClient.send() → success  ✅
  updateStatus(SENT)
  Done


If attempt 3 also fails:
  Retry exhausted
  CB records 3rd failure
  fallback() called
  → pushed to retry topic


If 10 out of 20 calls fail:
  CB opens
  All new calls → fallback immediately
  No Twilio contacted for 30s
  → 30s later, 3 test calls
  → if pass, CB closes
```

---

## Summary

| Pattern | Config key setting | Applies in our system |
|---|---|---|
| Retry | max-attempts, exponential backoff | All channel consumers |
| Timeout | timeout-duration, cancel-future | Must use async/CompletableFuture |
| Circuit Breaker | failure-rate-threshold, window-size | All external providers |
| Bulkhead | max-thread-pool-size, queue-capacity | Prevents one provider drowning others |

**The most important thing to remember: Timeout only works with CompletableFuture. Without async, TimeLimiter does nothing.**