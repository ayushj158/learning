# Inter-Service Communication — EM Interview Cheat Sheet
> Microservices Day 2 | FS context: Lloyds / Commercial Savings / Payments

---

## 1. The Three-Question Decision Framework

```
Q1: Does the caller need the response to continue?
    YES → Synchronous (REST / gRPC)
    NO  → Asynchronous (Kafka / Pub/Sub)

Q2: Is this internal service-to-service or external/client-facing?
    Internal, high-throughput, strict contract → gRPC
    External, browser/mobile, flexible         → REST
    Aggregating multiple services for one client → GraphQL / BFF

Q3: What are the latency and coupling tradeoffs?
    Tight SLA, simple request-response → Synchronous
    Loose coupling, resilience, audit trail → Asynchronous
```

---

## 2. Synchronous vs Asynchronous

| Dimension | Synchronous | Asynchronous |
|---|---|---|
| Caller blocks? | Yes | No |
| Both services must be up? | Yes — temporal coupling | No — temporal decoupling |
| Error feedback | Immediate | Eventually (DLQ) |
| Latency characteristic | Adds up across chain | Non-blocking |
| Protocols | REST, gRPC, GraphQL | Kafka, Pub/Sub, RabbitMQ |

### FS Decision Examples

| Scenario | Pattern | Why |
|---|---|---|
| Screen payment for fraud before submitting | Synchronous gRPC | Result gates the operation |
| Notify customer after payment settles | Async Kafka | Notification failure must not block settlement |
| Update audit log when payment submitted | Async Kafka | Audit failure must never block payment |
| Get account balance before initiating payment | Synchronous REST/gRPC | Balance check gates the operation |
| Post interest to 2M savings accounts overnight | Async Kafka batch | No caller waiting |
| FTD maturity reached — sweep + close + notify | Async event-driven | MaturityReachedEvent → Operations BC chain |

---

## 3. REST + Richardson Maturity Model
REST is an architectural style — not a protocol. Six constraints define it: client-server, stateless, cacheable, uniform interface, layered system, code on demand (optional).
The Richardson Maturity Model measures how "RESTful" an API actually is
  

### The Four Levels

| Level | Name | Description | FS Verdict |
|---|---|---|---|
| 0 | RPC style | One endpoint, everything POSTed | Avoid |
| 1 | Resources | Individual URLs, wrong HTTP methods | Partial |
| 2 | HTTP Verbs | Resources + correct HTTP methods | **Industry standard — target this** |
| 3 | HATEOAS | Hypermedia links in responses | Rarely worth complexity |

### Level 2 in Practice — Savings API

```
GET    /savings-accounts/ACC123              → get account details
POST   /savings-accounts                    → open new account
POST   /savings-accounts/ACC123/deposits    → add funds
POST   /savings-accounts/ACC123/withdrawals → submit withdrawal
PUT    /savings-accounts/ACC123/maturity-instruction → set/replace
DELETE /savings-accounts/ACC123             → close account
GET    /savings-accounts/ACC123/transactions → transaction history
```

### EM stance on HATEOAS
> "We implement Level 2 in all our APIs. HATEOAS adds significant client and server complexity for minimal real-world benefit. I've never seen a production FS API implement full Level 3. Aim for Level 2, understand Level 3 conceptually, be honest about the tradeoff."

---

## 4. HTTP Semantics — FS Critical

### HTTP Methods

| Method | Safe? | Idempotent? | Use in FS |
|---|---|---|---|
| GET | Yes | Yes | Balance checks, account queries, transaction history |
| POST | No | No | Initiate payment, open account, submit withdrawal — needs idempotency key |
| PUT | No | Yes | Set maturity instruction, replace standing order (full replacement) |
| PATCH | No | No | Update specific fields only |
| DELETE | No | Yes | Close account, cancel standing order |

### HTTP Status Codes — FS Semantics

```
200 OK              → Synchronous success with body
201 Created         → Resource created (POST) — include Location header
202 Accepted        → Accepted for ASYNC processing — use for payment initiation
204 No Content      → Success, no body (DELETE, some PUTs)
400 Bad Request     → Client error — schema validation failure
401 Unauthorized    → Not authenticated (no/invalid JWT)
403 Forbidden       → Authenticated but not authorised (wrong role/scope)
404 Not Found       → Resource doesn't exist
409 Conflict        → Duplicate idempotency key, account already closed
422 Unprocessable   → Valid JSON but semantically wrong (exceeds daily limit)
429 Too Many Requests → Rate limit exceeded (PCI-DSS velocity control)
500 Internal Error  → Server fault — never leak stack traces (GDPR)
503 Unavailable     → Downstream dependency down (CBS unavailable)
```

### The 202 Accepted Pattern — Critical for Payments

```
POST /payments
Request: { debitAccount, creditAccount, amount, idempotencyKey }

Response: 202 Accepted
{
  "paymentId": "PMT-2024-001234",
  "status": "PROCESSING",
  "links": {
    "poll": "/payments/PMT-2024-001234/status"
  }
}

Client polls GET /payments/PMT-2024-001234/status
Until status = SUBMITTED, SETTLED, or FAILED

WHY: Never return 200 synchronously for payment initiation.
Processing takes 50-500ms involving fraud + CBS.
Returning 200 before fraud screen = race condition = FCA complaint risk.
```

---

## 5. gRPC

### Three Technical Pillars

**Pillar 1: Protocol Buffers**
- Binary encoding — ~10x smaller than JSON, ~10x faster to parse
- Schema mandatory — enforced at compile time, not runtime
- Store money as `int64 amount_minor_units` — NEVER float (floating point = FCA reportable incident)
- Field numbers enable safe additive evolution — add field = safe, remove = breaking

**Pillar 2: HTTP/2**
- Multiplexing — multiple streams over ONE connection (vs HTTP/1.1 head-of-line blocking)
- HPACK header compression — headers sent once, delta only
- One persistent connection — no TCP handshake per call
- Result: 10,000 concurrent fraud screens → ~100 connections, 100 streams each

**Pillar 3: Streaming (4 patterns)**
```
Unary:             1 request  → 1 response      (like REST)
Server streaming:  1 request  → N responses     (real-time transaction feed)
Client streaming:  N requests → 1 response      (bulk payment upload)
Bidirectional:     N requests → N responses     (FX rate negotiation)
```

### Protobuf Example

```protobuf
syntax = "proto3";
service SavingsAccountService {
  rpc GetAccount (GetAccountRequest) returns (AccountResponse);
  rpc Deposit (DepositRequest) returns (DepositResponse);
  rpc StreamTransactions (StreamRequest) returns (stream TransactionEvent);
}

message Money {
  int64  amount_minor_units = 1;  // pence — never float
  string currency_code = 2;       // "GBP"
}

message DepositRequest {
  string account_id = 1;
  Money  amount = 2;
  string idempotency_key = 3;     // exactly-once semantics
  string deposited_by = 4;        // audit trail
  DepositChannel channel = 5;
}
```

### gRPC vs REST Decision Table

| Dimension | REST | gRPC |
|---|---|---|
| Protocol | HTTP/1.1 or HTTP/2 | HTTP/2 always |
| Payload | JSON (text, flexible) | Protobuf (binary, strict) |
| Contract | OpenAPI (optional) | Proto file (mandatory) |
| Browser support | Native | Needs gRPC-Web proxy |
| Human readable | Yes — debug with curl | No — binary, need grpcurl |
| Streaming | SSE / WebSocket separate | Built-in (4 patterns) |
| Performance | Baseline | ~5-10x faster, ~7x smaller |
| Latency (internal) | 5-20ms typical | 1-5ms typical |
| Schema evolution | Manual discipline | Field numbers — additive safe |
| Load balancer | L4/L7 universal | Needs L7-aware LB (Envoy/Istio) |

### When to Use Which

```
Use REST when:                          Use gRPC when:
──────────────                          ──────────────
External API (mobile, open banking)     Internal service-to-service
Browser clients call directly           Strict latency SLA (<50ms)
Human readability matters               High throughput (10k+ TPS)
Unknown consumers                       Streaming needed
Simple request-response, low volume     Polyglot services (Java/Python/Go)
                                        Strong contract enforcement
```

### EM Real-World Answer
> "In the Lloyds engagement: REST for all external-facing channel APIs. gRPC for internal service-to-service — Account Lifecycle BC calling Operations BC, interest accrual calling rate service. REST average 8-12ms → gRPC 2-3ms. That matters when 6 services in a chain with 100ms SLA. Protobuf field numbering enforces contract safety between 15 teams — breaking change fails at build time not 2am in production."

---

## 6. GraphQL

### Core Value Proposition
- Client specifies exactly what fields it needs — no over-fetching
- One query aggregates data from multiple services — no under-fetching / N+1 at API level
- Federation: multiple BCs exposed as one unified schema

### N+1 Problem and Fix

```
PROBLEM: Naive resolver called once per entity
1 customer query
+ N account queries (one per account)
+ N transaction queries (one per account)
= 1 + N + N queries — catastrophic at scale

FIX: DataLoader — batch and cache
DataLoader collects all IDs in one tick, fires ONE batch query
= 1 customer + 1 accounts + 1 batch transactions = 3 queries always
```

### When GraphQL in FS

```
✅ USE for:
BFF / RM dashboard — data from 5 services in one query
Customer 360 view — customer + accounts + transactions + cases
Self-service developer portals — flexible exploration

❌ AVOID for:
Payment initiation — needs idempotency + 202 Accepted → use REST
Internal service-to-service — use gRPC
Simple CRUD — REST is simpler
Public API introspection — DISABLE in production (FCA data security)
```

### EM Real-World Answer
> "Evaluated GraphQL for RM dashboard — needs Account Lifecycle, Servicing, Operations, Reporting data. BFF with 4 REST calls: 120ms. GraphQL federation gateway firing 4 in parallel: 35ms. We disabled schema introspection in production — FCA data security. Wrapped all mutations as REST endpoints — GraphQL mutations with financial side effects and idempotency requirements are too complex."

---

## 7. Async Messaging — Kafka vs RabbitMQ vs GCP Pub/Sub

| Dimension | Kafka | RabbitMQ | GCP Pub/Sub |
|---|---|---|---|
| Model | Append-only log, consumer reads at own offset | Queue — consumed = deleted | Topic/subscription — pull or push |
| Retention | Configurable — days to forever. **Replay possible.** | Until consumed or TTL | 7 days default |
| Ordering | Per partition — strong | Per queue (single consumer) | Best effort |
| Replay | **Yes** — reset offset | No | No |
| Throughput | Millions/sec | Thousands/sec | High — auto-scale |
| Sweet spot | Event streaming, audit logs, CQRS, Event Sourcing | Task queues, work distribution | GCP-native workloads |

### When to Use Which (FS)

```
Kafka:      Domain events across BCs, payment audit trail, CQRS read models,
            interest posting batch, FCA audit log (replay needed for 7yr)

RabbitMQ:   Task distribution, dead letter queues, priority queues,
            RPC-style async, smaller scale

GCP Pub/Sub: Primary cloud is GCP, Cloud Functions triggers,
             BigQuery streaming, lower ops overhead than self-managed Kafka
```

### EM Key Insight — Kafka vs Pub/Sub
> "GCP is our primary cloud. For greenfield services I evaluate Pub/Sub first — no cluster to manage, native IAM. But for savings event stream and audit trail we chose Confluent Kafka on GCP for one reason: replay. FCA investigations require replaying exact event sequence for an account going back 7 years. Pub/Sub doesn't give reliable replay beyond 7 days. Kafka with indefinite retention does. Regulatory decision driving infrastructure choice."

---

## 8. API Versioning Strategies

### Three Strategies

```
Strategy 1: URL versioning            → /v1/savings-accounts, /v2/savings-accounts
Pros:  Explicit, easy to route, cache, test
Cons:  URLs should identify resources not versions
Use:   External APIs — Open Banking standard, industry norm

Strategy 2: Header versioning         → Accept-Version: v2 or API-Version: 2024-01-15
Pros:  Clean URLs, more RESTful
Cons:  Harder to test (can't change in browser), silent default if header missing
Use:   Internal service-to-service APIs

Strategy 3: Content negotiation       → Accept: application/vnd.bank.savings.v2+json
Pros:  Most RESTful in principle
Cons:  Complex, rarely implemented correctly
Use:   Rare — academic interest only
```

### Breaking vs Non-Breaking Changes

```
NON-BREAKING (safe, no version bump needed):
✅ Adding new optional field to response
✅ Adding new optional request parameter
✅ Adding new enum value (IF consumers handle unknown gracefully)
✅ Adding new endpoint
✅ Relaxing validation rules

BREAKING (requires version bump + deprecation):
❌ Removing a field from response
❌ Renaming a field (balance → currentBalance)
❌ Changing field type (int → string, object → array)
❌ Changing HTTP method (PUT → PATCH)
❌ Changing URL structure
❌ Changing error response structure
❌ Making optional field mandatory
❌ Tightening validation rules
```

### Deprecation Process

```
1. Publish v2 alongside v1 (both live)
2. Add response headers to v1:
   Deprecation: true
   Sunset: 2024-12-31
   Link: /docs/migration-v1-to-v2
3. Monitor v1 traffic by consumer in API gateway
4. Alert: which services haven't migrated?
5. Contact consuming teams — 6 months internal, 12 months external
   (FCA operational resilience — adequate notice required)
6. Sunset date: return 410 Gone (not 404)
7. Remove v1 code after sunset + 30 days
```

### EM Framing
> "API versioning is a team process, not just technical. When we introduced v2 of the Savings Account API, we kept v1 running 4 months with Sunset headers. Tracked v1 traffic per service in API gateway. Three weeks before sunset, two services hadn't migrated — escalated to team leads directly. Under FCA operational resilience, breaking a downstream service's API without adequate notice is reportable if it impacts an important business service."

---

## 9. Full Communication Pattern Decision Map

```
What are you building?
│
├── External API (mobile, browser, open banking partner)
│   → REST + JSON Level 2. OpenAPI. URL versioning.
│     202 Accepted for async operations.
│
├── Internal service-to-service (high throughput, strict contract)
│   → gRPC + Protobuf. Proto field evolution for versioning.
│     Sub-10ms SLA. Streaming if real-time feed needed.
│
├── RM / colleague dashboard (data from 5+ services)
│   → GraphQL federation. DataLoader for N+1.
│     Disable introspection. REST for mutations.
│
├── Cross-service domain events (decouple producer/consumer)
│   → Kafka. Avro/Protobuf schema + Schema Registry.
│     Consumer group per BC. Retain forever for regulatory replay.
│
├── Simple task distribution / batch jobs
│   → RabbitMQ or GCP Pub/Sub.
│     Dead letter queue for failures.
│
└── GCP-native trigger (Cloud Function, BigQuery)
    → GCP Pub/Sub. Native IAM. Auto-scaling.
```

---

## 10. Numbers to Quote in Interviews

| Metric | Value | Context |
|---|---|---|
| REST internal latency | 5-20ms | Typical internal service call |
| gRPC internal latency | 1-5ms | Same call over Protobuf/HTTP2 |
| gRPC size reduction | ~7x | vs JSON for same payload |
| gRPC parse speed | ~10x | vs JSON |
| Kafka throughput | Millions/sec | Per broker, with partitioning |
| GraphQL N+1 fix | 3 queries | vs 1 + N + N with naive resolvers |
| CHAPS cut-off | 17:00 | After which payments go next day |
| FCA audit retention | 7 years | Minimum for regulatory replay |
| Fraud screen SLA | <50ms | Synchronous gRPC for payment flow |
| Payment chain SLA | <200ms | End-to-end at Lloyds scale |

---

*Richardson Levels: 0=RPC, 1=Resources, 2=HTTP Verbs (target), 3=HATEOAS (rarely worth it)*
*Breaking changes always need version bump + deprecation period (6mo internal, 12mo external)*


Here's your drill question — take your time, answer as you would in the actual interview:

---

> *"Your fraud screening service currently handles 50,000 payment assessments per second at peak using REST/JSON. Average latency is 12ms. The payments team is complaining that fraud screening is the bottleneck in their 100ms end-to-end SLA — they want it under 3ms. Walk me through migrating to gRPC. What changes in the contract, what happens to your load balancer, what's the latency improvement you'd expect, and what are the risks of this migration?"*

---

Good answer — you've got the right instincts. Let me score it, then push you where the interviewer would.

---

## Score: 6.5 / 10

### What You Got Right ✅

- Protobuf contract creation — correct starting point
- API Gateway gRPC compatibility check — critical, most candidates miss this
- gRPC-compatible web server on producer side — correct (Netty-based in Spring)
- Client stub generation from proto — correct
- Incremental migration with traffic splitting — excellent EM thinking
- Metrics and tracking — right direction
- Connection drop handling — good — most candidates don't mention this

### What's Missing or Needs Sharpening ❌

**Four specific gaps the interviewer will probe:**

1. **No numbers** — "under 3ms" was the ask. What latency do you actually expect and why? What's the payload size reduction?

2. **Load balancer specifics** — you said "ensure API gateway supports gRPC" but didn't name what changes. HTTP/2 L7 load balancing is the specific answer. Most L4 load balancers don't do gRPC properly.

3. **The contract migration itself** — what specifically goes in the Protobuf? How do you handle the money field? How do you version the proto file? This is a technical depth question.

4. **Connection drop handling** — you mentioned it but didn't say HOW you handle it. What's the actual mechanism?

---

Let me show you what a 9/10 answer looks like, built on your foundation:

---

## The 9/10 Answer

### Step 1 — Define the Protobuf Contract

```protobuf
syntax = "proto3";
package fraud.v1;

service FraudScreeningService {
  // Unary — synchronous screen per payment
  // Replaces: POST /fraud/assess
  rpc AssessPayment (FraudAssessmentRequest)
      returns (FraudAssessmentResponse);

  // Server streaming — real-time fraud signal feed
  // New capability REST couldn't do efficiently
  rpc StreamFraudAlerts (StreamAlertsRequest)
      returns (stream FraudAlertEvent);
}

message FraudAssessmentRequest {
  string payment_id = 1;
  string idempotency_key = 2;        // Exactly-once at assessment level

  // Money — NEVER float. Store as minor units.
  int64  amount_minor_units = 3;     // pence
  string currency_code = 4;          // "GBP"

  string debit_account_id = 5;
  string credit_account_id = 6;
  string beneficiary_name = 7;

  // Fraud signals
  string device_fingerprint = 8;
  GeoLocation transaction_location = 9;
  string ip_address = 10;
  int64  transaction_timestamp_ms = 11;
  PaymentChannel channel = 12;
}

message FraudAssessmentResponse {
  string payment_id = 1;
  FraudDecision decision = 2;        // ALLOW, REVIEW, BLOCK
  double confidence_score = 3;       // 0.0 - 1.0
  repeated string triggered_rules = 4;
  int64  assessment_duration_ms = 5; // Observability baked in
}

enum FraudDecision {
  FRAUD_DECISION_UNSPECIFIED = 0;    // Always define default = 0
  ALLOW = 1;
  REVIEW = 2;
  BLOCK = 3;
}

message GeoLocation {
  double latitude = 1;
  double longitude = 2;
  string country_code = 3;
}
```

**Why these specific decisions:**
- `int64 amount_minor_units` — floating point in financial calculations is a FCA reportable incident. Pence as integer, always.
- `FRAUD_DECISION_UNSPECIFIED = 0` — Protobuf default for enums is 0. Never let default be a meaningful value like ALLOW — that's a security flaw. Unknown decision = block or review, never silently allow.
- `assessment_duration_ms` in response — observability baked into the contract, not bolted on after.

---

### Step 2 — Load Balancer — The Specific Problem

This is where most candidates are vague. Here's the precise issue:

```
THE PROBLEM:
HTTP/1.1 load balancing works at connection level.
Each request = new connection (or reused connection).
L4 load balancer: "new TCP connection → Route to server A"

HTTP/2 (gRPC) load balancing BREAKS with L4:
One long-lived TCP connection carries multiple streams.
L4 LB sees: "existing connection → Server A"
ALL 50,000 RPS go to Server A forever.
Server B, C, D sit idle.
You've accidentally created a single point of failure.

THE FIX: L7-aware load balancer that understands HTTP/2 frames
Options (specific to your GCP context):

Option 1: Istio service mesh (recommended for your stack)
- Envoy sidecar does HTTP/2 aware load balancing
- Balances at the gRPC stream level, not connection level
- mTLS between services included for free
- Add to fraud service: VirtualService + DestinationRule
  with round-robin or least-requests policy

Option 2: GCP Cloud Load Balancing
- Supports HTTP/2 natively as of 2021
- Backend service with HTTP/2 protocol configured
- Works but less control than Istio

Option 3: nginx (if self-managed)
- Must compile with --with-http_v2_module
- grpc_pass directive instead of proxy_pass
- More operational overhead

Your context: GCP + likely Istio → Option 1 is the answer.
```

---

### Step 3 — Producer Side (Fraud Service)

```java
// Spring Boot gRPC server — using grpc-spring-boot-starter
// Netty-based HTTP/2 server replaces Tomcat

@GrpcService
public class FraudScreeningGrpcService
        extends FraudScreeningServiceGrpc.FraudScreeningServiceImplBase {

    private final FraudScoringEngine scoringEngine;
    private final FraudAuditService auditService;

    @Override
    public void assessPayment(
            FraudAssessmentRequest request,
            StreamObserver<FraudAssessmentResponse> responseObserver) {

        long startMs = System.currentTimeMillis();

        try {
            // Existing domain logic — unchanged
            FraudScore score = scoringEngine.score(
                FraudAssessmentMapper.toDomain(request));

            FraudAssessmentResponse response =
                FraudAssessmentResponse.newBuilder()
                    .setPaymentId(request.getPaymentId())
                    .setDecision(toProtoDecision(score.getDecision()))
                    .setConfidenceScore(score.getConfidence())
                    .addAllTriggeredRules(score.getTriggeredRules())
                    .setAssessmentDurationMs(
                        System.currentTimeMillis() - startMs)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            // gRPC error model — not HTTP status codes
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Fraud assessment failed")
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}

# application.yml
grpc:
  server:
    port: 9090
    # Keep-alive — critical for long-lived connections
    keep-alive-time: 30s
    keep-alive-timeout: 10s
    permit-keep-alive-without-calls: true
    # Max concurrent streams per connection
    max-concurrent-calls-per-connection: 1000
```

---

### Step 4 — Consumer Side (Payment Service)

```java
// gRPC client configuration in Payment Service
@Configuration
public class FraudServiceGrpcConfig {

    @Bean
    public FraudScreeningServiceGrpc.FraudScreeningServiceBlockingStub
            fraudServiceStub(ManagedChannel fraudChannel) {
        return FraudScreeningServiceGrpc
            .newBlockingStub(fraudChannel)
            .withDeadlineAfter(45, TimeUnit.MILLISECONDS); // SLA guard
    }

    @Bean
    public ManagedChannel fraudChannel() {
        return ManagedChannelBuilder
            .forAddress("fraud-service", 9090)
            // Connection keep-alive — THE connection drop fix
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)    // Keep alive even when idle
            // Retry policy — handles transient drops
            .enableRetry()
            .maxRetryAttempts(3)
            // Connection pool
            .defaultLoadBalancingPolicy("round_robin")
            .build();
    }
}

// Usage in PaymentService
@Service
public class PaymentService {

    public PaymentResult processPayment(PaymentRequest request) {
        FraudAssessmentRequest fraudRequest =
            FraudAssessmentRequest.newBuilder()
                .setPaymentId(request.getPaymentId())
                .setAmountMinorUnits(
                    request.getAmount().getAmountInPence())
                .setCurrencyCode("GBP")
                .setDebitAccountId(request.getDebitAccountId())
                .setDeviceFingerprint(request.getDeviceFingerprint())
                .build();

        FraudAssessmentResponse response;
        try {
            // Blocking stub — synchronous, waits for response
            response = fraudServiceStub.assessPayment(fraudRequest);
        } catch (StatusRuntimeException e) {
            // gRPC-specific exception handling
            if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                // Fraud service took >45ms — treat as allow with flag
                // Don't let fraud timeout block payment entirely
                // Log for investigation, continue with elevated monitoring
                log.error("Fraud screen timeout for payment: {}",
                    request.getPaymentId());
                return processWithElevatedMonitoring(request);
            }
            throw new FraudScreeningException(e);
        }

        if (response.getDecision() == FraudDecision.BLOCK) {
            return PaymentResult.blocked(response.getTriggeredRules());
        }
        // Continue payment processing...
    }
}
```

---

### Step 5 — The Connection Drop Problem — Specific Fix

```
THE PROBLEM:
gRPC uses long-lived HTTP/2 connections.
Network infrastructure (NAT gateways, firewalls, proxies)
silently drops idle connections after ~300-600 seconds.
Client thinks connection is alive.
Next RPC call → connection reset → error.
At 50k TPS this hits constantly.

THE THREE-LAYER FIX:

Layer 1: HTTP/2 PING frames (keep-alive)
Server sends PING every 30s → client responds
If no response in 10s → connection dead → reconnect
Config: keepAliveTime=30s, keepAliveTimeout=10s
(shown in config above)

Layer 2: Client-side retry policy
gRPC built-in retry with exponential backoff
Transparent to caller — retries happen in the channel
Config: enableRetry(), maxRetryAttempts(3)
Only retries IDEMPOTENT calls — AssessPayment is safe to retry
(same payment assessed twice = same result, idempotent by design)

Layer 3: Health checking + circuit breaker
gRPC health check protocol — fraud service exposes:
grpc.health.v1.Health/Check
Istio circuit breaker opens if 5xx rate > 50%
Falls back to REST endpoint during gRPC degradation
(your parallel running REST = the fallback)
```

---

### Step 6 — Incremental Migration with Traffic Splitting

```
Your instinct was correct. Here's the specific mechanism:

Phase 1: Shadow mode (Week 1-2)
Payment service calls BOTH REST and gRPC for every request.
Uses REST result for the actual decision.
Compares gRPC result — are they identical?
Measure: latency, error rate, decision parity.
Target: 99.9% decision parity before cutting over.

Phase 2: Canary via Istio VirtualService (Week 3)
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: fraud-service
spec:
  http:
  - match:
    - headers:
        x-fraud-protocol:
          exact: grpc
    route:
    - destination:
        host: fraud-service-grpc
      weight: 100
  - route:
    - destination:
        host: fraud-service-rest
      weight: 100

Payment service sends x-fraud-protocol: grpc header
for 10% of requests → routes to gRPC endpoint.
REST remains default for 90%.

Phase 3: Progressive cutover
Week 3:  10% gRPC, 90% REST
Week 4:  30% gRPC, 70% REST  — if p99 latency OK
Week 5:  70% gRPC, 30% REST  — if error rate < 0.01%
Week 6: 100% gRPC, REST decommission after 30 days

Automated rollback trigger:
If gRPC p99 latency > 8ms OR error rate > 0.1%
→ Istio weight flips back to 100% REST automatically
→ PagerDuty alert to on-call engineer
```

---

### Step 7 — Metrics and Tracking

```
Metrics to track during migration:

Latency (the primary goal):
p50, p95, p99 latency per protocol
Target: REST p99 = 12ms → gRPC p99 < 3ms
Alert: gRPC p99 > 5ms = rollback trigger

Error rates:
gRPC status codes — DEADLINE_EXCEEDED, UNAVAILABLE, INTERNAL
Target: < 0.01% error rate
REST error rate as baseline comparison

Decision parity:
% of payments where gRPC and REST agree on ALLOW/BLOCK/REVIEW
Target: 99.9% parity — any divergence = investigation
A divergence means the serialisation/deserialisation is losing data

Throughput:
Requests per second per protocol
Connection count (gRPC should be ~100 connections vs REST ~5000)
Stream count per connection

Business metrics:
False positive rate (legitimate payments blocked)
False negative rate (fraudulent payments allowed)
Must not change during migration — fraud model is unchanged

Operational:
CPU utilisation — Protobuf parsing is cheaper than JSON
Memory — fewer connections = less memory on both sides
GC pressure — Protobuf generates less garbage than JSON parsing
```

---

### Expected Latency Improvement — The Numbers

```
Current REST baseline:
  JSON serialisation:    ~0.8ms
  Network (HTTP/1.1):   ~2.0ms
  JSON deserialisation: ~0.8ms
  Processing:           ~8.0ms
  Total p50:            ~11.6ms ≈ 12ms quoted

Expected gRPC:
  Protobuf serialisation:   ~0.08ms  (10x faster than JSON)
  Network (HTTP/2):         ~0.5ms   (multiplexed, no handshake)
  Protobuf deserialisation: ~0.08ms
  Processing:               ~8.0ms   (unchanged — same fraud model)
  Total p50:                ~8.66ms  ≈ ~9ms p50

Wait — that's 9ms, not 3ms. Why?

Because processing time (the fraud ML model) dominates.
gRPC reduces the transport overhead, not the compute.
To get to 3ms you need BOTH:
  1. gRPC (saves ~3ms on transport)
  2. Model optimisation — quantised model, feature caching,
     pre-computed velocity counters in Redis
     (reduces processing from 8ms to ~2ms)

Honest interview answer:
"gRPC alone gets us from 12ms to ~9ms p99.
 To hit the 3ms target, we also need to optimise the
 fraud model serving — pre-computing velocity features
 in Redis rather than querying on each request.
 Combined: transport savings + model optimisation = 2-3ms p99.
 I'd commit to <5ms with gRPC alone, <3ms with model work."

This is the answer that impresses. Interviewers WANT you to
push back on unrealistic targets with technical reasoning.
```

---

## What Made Your Answer 6.5 vs 9/10

The gap was **specificity and depth on three points:**

The load balancer answer needed to name Istio/Envoy and explain WHY L4 breaks with HTTP/2 — connection-level vs stream-level balancing is the exact technical detail interviewers probe.

The connection drop fix needed the three-layer mechanism: HTTP/2 PING frames, client retry policy, and health check + circuit breaker. Saying "handle connection drops" without the mechanism is a junior answer.

The latency expectation needed the honest pushback — gRPC alone won't hit 3ms if the fraud model takes 8ms. Showing you can reason from first principles about where time is actually spent, and pushing back on the target with technical justification, is what separates EM candidates from senior dev candidates.

---

Here's your drill question:

---

> *"Design the REST API for the Savings Account Servicing BC of your enterprise UK bank. You need to cover the full servicing lifecycle for both Liquid Savings Account (LSA) and Fixed Term Deposit (FTD). Specifically:*
>
> *1. Open a savings account (product-specific)*
> *2. Add funds (LSA only)*
> *3. Submit a withdrawal with notice period (LSA only)*
> *4. Set or update a maturity instruction (FTD only)*
> *5. Get transaction history with pagination*
> *6. Close an account*
>
> *For each endpoint give me: HTTP method, URL, request body (key fields), success response + status code, and at least two error scenarios with correct status codes. Also tell me: how do you handle idempotency for fund additions, what pagination strategy do you use for transaction history and why, and how does your API communicate that add-funds is not available for FTD?"*

---

This is a design question — think out loud, be specific with field names, status codes, and FS context. Take your time. Go.

Good answer — strong business thinking at the start, solid on async vs sync reasoning, and the idempotency cache approach shows production awareness. Let me score it properly.

---

## Score: 6 / 10

### What You Got Right ✅

- STP feasibility check before committing to sync vs async — excellent EM opening, most candidates skip this entirely
- Business requirement driving design — real-time vs offline fulfilment — correct framing
- Saga for async multi-step fulfilment — right pattern named
- Separate URLs for LSA and FTD — correct product-aware design
- 202 for async, 200 for sync — correct HTTP semantics
- Cursor-based pagination with timestamps — correct choice, good reasoning
- 25 transactions first — sensible default
- PATCH for servicing instructions — correct
- 429 for rate limiting — correct
- Product type validation returning 400 — right direction

### What's Missing or Needs Sharpening ❌

**Six specific gaps:**

1. **URL design has issues** — `/savings/accounts/lsa/transactions` with accountId in header is wrong. Account ID belongs in the path, not a header. Headers are for metadata (auth, correlation, idempotency), not resource identity.

2. **PATCH vs PUT confusion** — servicing instructions are a full replacement (set maturity instruction = replace the whole instruction). That's PUT, not PATCH. PATCH is partial update.

3. **No idempotency key mechanism specified** — you mentioned session ID + request ID + operation ID cache but didn't say WHERE the idempotency key travels (request header), what the cache TTL is, or what happens on a duplicate mid-processing.

4. **Missing 409 Conflict** — duplicate idempotency key that's still processing should return 409, not a cached 200. The distinction matters.

5. **Error scenarios not specific enough** — "5xx for server errors" is too vague. Which 5xx for which scenario? 503 when CBS is down is different from 500 when your code throws.

6. **Add funds on FTD** — you said PATCH with wrong product type returns 400. But add funds is a completely separate endpoint — the question is whether the FTD endpoint even exposes a deposits sub-resource, and if not, what does the 404 vs 405 vs 400 tell the client.

---

## The 9/10 Answer

### Framing First — Your STP Analysis (Keep This)

> *"Before I design the API surface, I need to answer two questions. First — does TM Vault support real-time STP for account opening and fund movements? Vault is API-first so yes — account opening and posting orders are synchronous APIs. Second — does the business require real-time customer feedback? For LSA deposits below £100k — yes, customers expect instant confirmation. For FTD opening — it involves rate lock, CBS contract creation, and potentially a funding transfer — 202 Accepted with polling is more appropriate. This distinction drives my HTTP status code choices per endpoint."*

---

### URL Structure — The Correction

```
YOUR design:
/savings/accounts/lsa
/savings/accounts/ftd
/savings/accounts/lsa/transactions  (accountId in header — wrong)

CORRECTED design — why:
Account ID is a resource identifier. It belongs in the path.
Headers are for request metadata — auth, correlation, idempotency.
Putting resource IDs in headers breaks caching, logging, and tracing.

/v1/savings/accounts                           → collection (all products)
/v1/savings/accounts/lsa                       → LSA collection
/v1/savings/accounts/lsa/{accountId}           → specific LSA account
/v1/savings/accounts/lsa/{accountId}/deposits  → LSA deposits sub-resource
/v1/savings/accounts/lsa/{accountId}/withdrawals → LSA withdrawals
/v1/savings/accounts/lsa/{accountId}/transactions → LSA transaction history
/v1/savings/accounts/ftd                       → FTD collection
/v1/savings/accounts/ftd/{accountId}           → specific FTD account
/v1/savings/accounts/ftd/{accountId}/maturity-instruction → FTD instruction

WHY separate /lsa and /ftd rather than one /accounts?
Product type in the URL makes routing explicit.
API gateway can apply different policies per product:
  - /lsa/* → rate limit 100 TPS (liquid, high frequency)
  - /ftd/* → rate limit 10 TPS (low frequency, high value)
Different OpenAPI schemas per product — LSA schema has deposits,
FTD schema does not. Client gets 404 Not Found if they try
POST /ftd/{id}/deposits — endpoint doesn't exist. Clean.
```

---

### Endpoint by Endpoint

**1. Open a Savings Account**

```
POST /v1/savings/accounts/lsa
POST /v1/savings/accounts/ftd

LSA Request:
{
  "customerId": "CUST-123",
  "productCode": "LSA-INSTANT-ACCESS",
  "initialDeposit": {
    "amount": 10000,              // pence — 10000 = £100.00
    "currency": "GBP",
    "fundingAccountId": "CA-456"  // source current account
  },
  "noticePeriodDays": 0,          // 0 = instant, 30/60/90 available
  "marketingConsent": false
}

FTD Request:
{
  "customerId": "CUST-123",
  "productCode": "FTD-12M",
  "termMonths": 12,
  "openingDeposit": {
    "amount": 5000000,            // £50,000.00 in pence
    "currency": "GBP",
    "fundingAccountId": "CA-456"
  },
  "maturityInstruction": {        // Collected at opening — FCA good practice
    "action": "RENEW",
    "renewalProductCode": "FTD-12M"
  }
}

LSA Response: 201 Created (TM Vault STP — real time)
Location: /v1/savings/accounts/lsa/LSA-789
{
  "accountId": "LSA-789",
  "status": "ACTIVE",
  "balance": { "amount": 10000, "currency": "GBP" },
  "interestRate": { "value": "0.0350", "type": "VARIABLE" },
  "noticePeriodDays": 0,
  "openedAt": "2024-11-15T09:23:45Z"
}

FTD Response: 202 Accepted (multi-step — rate lock + contract + funding transfer)
Location: /v1/savings/accounts/ftd/FTD-890
{
  "accountId": "FTD-890",
  "status": "PENDING_ACTIVATION",
  "estimatedActivationBy": "2024-11-15T09:24:00Z",
  "links": {
    "poll": "/v1/savings/accounts/ftd/FTD-890/status"
  }
}

Error scenarios:
400 Bad Request     → Missing mandatory field, invalid productCode,
                      termMonths not in [3, 6, 12, 24]
409 Conflict        → Duplicate idempotency key — same request already submitted
422 Unprocessable   → Opening deposit below minimum (£1,000 for FTD)
                      Opening deposit exceeds FSCS cap (£85,000)
                      Funding account belongs to different customer
403 Forbidden       → Customer not eligible (U18, wrong segment, ring-fence rule)
503 Service Unavailable → TM Vault unavailable
                          Retry-After: 30 header included
```

---

**2. Add Funds (LSA Only)**

```
POST /v1/savings/accounts/lsa/{accountId}/deposits

WHY POST not PATCH:
A deposit creates a new transaction record — it's resource creation.
POST /deposits creates a new deposit resource.
PATCH /lsa/{accountId} would mean "partially update the account"
which is semantically wrong for a financial transaction.

Request:
Idempotency-Key: dep-20241115-abc123   ← REQUEST HEADER, not body
{
  "amount": {
    "value": 500000,                   // £5,000.00 in pence
    "currency": "GBP"
  },
  "sourceAccountId": "CA-456",
  "depositChannel": "ONLINE_BANKING",
  "customerReference": "November top-up"
}

Response: 201 Created (TM Vault STP — real time for LSA)
{
  "depositId": "DEP-20241115-001",
  "status": "COMPLETED",
  "amount": { "value": 500000, "currency": "GBP" },
  "balanceAfter": { "value": 5500000, "currency": "GBP" },
  "completedAt": "2024-11-15T09:25:00Z",
  "transactionReference": "TXN-REF-789"
}

Error scenarios:
400 Bad Request     → amount.value <= 0, missing currency
404 Not Found       → accountId doesn't exist or not owned by customer
409 Conflict        → Duplicate Idempotency-Key (already processing or completed)
                      Response body includes original deposit response
422 Unprocessable   → Deposit would exceed maximum balance (FSCS cap)
                      Source account has insufficient funds
                      Account in NOTICE_GIVEN status (some products restrict)
429 Too Many Requests → Velocity limit exceeded (PCI-DSS control)
                        Retry-After: 60

FTD — endpoint does NOT exist:
POST /v1/savings/accounts/ftd/{accountId}/deposits
→ 404 Not Found
The /ftd/{accountId}/deposits path is simply not registered.
Client gets 404 — clean signal that this resource doesn't exist
for this product type. Better than 400 because 400 implies
you sent a bad request — you didn't, the operation just doesn't exist.
```

---

**3. Withdrawal with Notice Period (LSA Only)**

```
POST /v1/savings/accounts/lsa/{accountId}/withdrawals

WHY POST not DELETE:
Withdrawal doesn't delete the account.
It creates a withdrawal instruction/transaction.
POST /withdrawals creates a withdrawal resource.

Request:
Idempotency-Key: wdl-20241115-xyz789
{
  "amount": {
    "value": 100000,               // £1,000.00 in pence
    "currency": "GBP"
  },
  "destinationAccountId": "CA-456",
  "withdrawalChannel": "ONLINE_BANKING"
}

For instant access (noticePeriodDays=0):
Response: 202 Accepted
{
  "withdrawalId": "WDL-20241115-001",
  "status": "PROCESSING",
  "amount": { "value": 100000, "currency": "GBP" },
  "expectedCompletionDate": "2024-11-15",
  "paymentRail": "FASTER_PAYMENTS",
  "links": { "poll": "/v1/savings/accounts/lsa/{accountId}/withdrawals/WDL-001" }
}

For notice account (noticePeriodDays=30):
Response: 202 Accepted
{
  "withdrawalId": "WDL-20241115-002",
  "status": "NOTICE_GIVEN",
  "amount": { "value": 100000, "currency": "GBP" },
  "noticeGivenAt": "2024-11-15T09:30:00Z",
  "fundsAvailableFrom": "2024-12-15",  // 30 days hence
  "cancellableUntil": "2024-11-22T09:30:00Z",
  "links": {
    "cancel": "/v1/savings/accounts/lsa/{accountId}/withdrawals/WDL-002",
    "poll": "/v1/savings/accounts/lsa/{accountId}/withdrawals/WDL-002/status"
  }
}

Error scenarios:
404 Not Found       → accountId not found or not owned by customer
409 Conflict        → Duplicate Idempotency-Key
422 Unprocessable   → Withdrawal would breach minimum balance (£1 for LSA-INSTANT)
                      Active notice already exists for maximum permitted amount
                      Account status is CLOSURE_PENDING
503                 → BACS/FPS unavailable — include Retry-After
```

---

**4. Maturity Instruction (FTD Only)**

```
PUT /v1/savings/accounts/ftd/{accountId}/maturity-instruction

WHY PUT not PATCH:
Maturity instruction is a complete replacement — not partial update.
Customer sets the whole instruction: action + optional renewal details.
PUT semantics: "replace this resource entirely with what I'm sending."
PATCH would mean "update only the fields I specify" — that's not the
business meaning here.

Request:
{
  "action": "TRANSFER_AND_CLOSE",       // RENEW | TRANSFER_AND_CLOSE | CLOSE
  "transferDestination": {
    "accountId": "CA-456",
    "accountType": "CURRENT_ACCOUNT"
  }
}

Response: 200 OK (synchronous — just storing an instruction)
{
  "accountId": "FTD-890",
  "maturityInstruction": {
    "action": "TRANSFER_AND_CLOSE",
    "transferDestination": { "accountId": "CA-456" },
    "setAt": "2024-11-15T10:00:00Z",
    "settableUntil": "2025-11-13T23:59:59Z"  // 2 business days before maturity
  }
}

Error scenarios:
404 Not Found       → accountId not found
409 Conflict        → Instruction deadline passed (2 business days before maturity)
                      Status code 409 not 400 — it's a state conflict, not bad input
422 Unprocessable   → action=RENEW but no renewalProductCode specified
                      transferDestination account belongs to different customer
400 Bad Request     → action not in [RENEW, TRANSFER_AND_CLOSE, CLOSE]
```

---

**5. Transaction History with Pagination**

```
GET /v1/savings/accounts/lsa/{accountId}/transactions
GET /v1/savings/accounts/ftd/{accountId}/transactions

WHY cursor-based over offset-based:
Offset pagination: GET /transactions?page=2&size=25
Problem: "page 2" shifts as new transactions are inserted.
Customer on page 2 at 09:00, new transactions arrive,
page 2 at 09:01 has different content → confusing, duplicates.

Cursor pagination: timestamp or transactionId as cursor
Stable — cursor points to a fixed position in the ordered log.
No duplicates regardless of new inserts.
Works with Kafka-backed transaction log (your architecture).
Natural for financial history — always ordered by time.

Request:
GET /v1/savings/accounts/lsa/LSA-789/transactions
    ?limit=25
    &before=2024-11-15T09:00:00Z   // cursor — get txns before this time
    &type=DEPOSIT                   // optional filter
    &fromDate=2024-01-01            // optional date range

Default: 25 most recent, no type filter, no date range.
Max limit: 100 per request (prevent abuse).

Response: 200 OK
{
  "accountId": "LSA-789",
  "transactions": [
    {
      "transactionId": "TXN-001",
      "type": "DEPOSIT",
      "amount": { "value": 500000, "currency": "GBP" },
      "balanceAfter": { "value": 5500000, "currency": "GBP" },
      "description": "Online banking deposit",
      "channel": "ONLINE_BANKING",
      "valueDate": "2024-11-15",
      "postedAt": "2024-11-15T09:25:00Z",
      "reference": "TXN-REF-789"
    }
    // ... 24 more
  ],
  "pagination": {
    "count": 25,
    "hasMore": true,
    "nextCursor": "2024-11-14T15:30:00Z",  // timestamp of last item
    "nextLink": "/v1/savings/accounts/lsa/LSA-789/transactions?before=2024-11-14T15:30:00Z&limit=25"
  }
}

Error scenarios:
400 Bad Request → limit > 100, invalid date format
404 Not Found   → accountId not found or wrong product type
                  (GET /ftd/{id}/transactions on an LSA account → 404)
```

---

**6. Close an Account**

```
DELETE /v1/savings/accounts/lsa/{accountId}
DELETE /v1/savings/accounts/ftd/{accountId}

LSA closure: requires zero balance first
FTD closure: only at maturity (or penalty closure if product permits)

Request body on DELETE (unusual but valid for closure reason):
{
  "closureReason": "CUSTOMER_REQUEST",
  "confirmZeroBalance": true   // explicit acknowledgement
}

LSA Response: 202 Accepted (sweep remaining balance first if any)
{
  "closureId": "CLO-001",
  "status": "CLOSURE_PENDING",
  "accountId": "LSA-789",
  "estimatedClosureDate": "2024-11-15",
  "links": { "poll": "/v1/savings/accounts/lsa/LSA-789/closure/CLO-001" }
}

FTD early closure: 422 Unprocessable Entity
{
  "error": {
    "code": "EARLY_CLOSURE_NOT_PERMITTED",
    "message": "Fixed Term Deposit cannot be closed before maturity date.",
    "maturityDate": "2025-11-15",
    "suggestion": "Set a CLOSE maturity instruction to close at maturity."
  }
}

Error scenarios:
409 Conflict        → LSA has non-zero balance and confirmZeroBalance not sent
422 Unprocessable   → FTD before maturity (as above)
404 Not Found       → accountId not found
```

---

### Idempotency — The Specific Mechanism

```
WHERE the key travels: Request header, not body.
Idempotency-Key: {clientGeneratedUUID}

WHY header not body:
Body is the business payload — what you want to do.
Header is request metadata — how to treat this request.
Keeps concerns separated. OpenAPI documents it as a header param.

THE CACHE:
Key:   SHA256(customerId + Idempotency-Key + operationType)
         — include customerId to prevent cross-customer replay attacks
Value: { status, response, expiresAt }
Store: Redis with TTL

TTL: 24 hours for deposit/withdrawal
     7 days for account opening
     (longer operations need longer dedup window)

THE THREE STATES and responses:

State 1: Key not seen before
→ Process request normally
→ Cache { status: "PROCESSING", requestId } immediately
→ Update cache with final response when complete

State 2: Key seen, still PROCESSING (duplicate during processing)
→ Return 409 Conflict
{
  "error": "DUPLICATE_REQUEST",
  "message": "Request with this idempotency key is already being processed",
  "originalRequestId": "dep-20241115-abc123",
  "retryAfter": 5
}
→ Client waits 5 seconds and polls or retries

State 3: Key seen, COMPLETED (retry after success)
→ Return original response with 200 (not 201 — already created)
→ Include header: Idempotency-Replayed: true
→ Client knows this is a cached response, not a new operation

CROSS-CUSTOMER PROTECTION:
SHA256(customerId + idempotency key) means:
Customer A's key "abc123" ≠ Customer B's key "abc123"
Prevents a malicious client reusing another customer's idempotency key
```

---

## What Moved You from 6 to 9/10

The four things that sharpen the answer:

Account ID in path not header — this is a fundamental REST resource identity principle that interviewers check. Headers are metadata, paths are identity.

PUT vs POST vs PATCH clarity — deposits and withdrawals are POST because they create transaction resources. Maturity instruction is PUT because it's a full replacement. This precision signals you understand HTTP semantics, not just the labels.

404 for FTD deposit endpoint vs 400 — the endpoint simply not existing is cleaner than rejecting a request to an endpoint that exists. Architecture enforces the product rule, not validation logic.

Idempotency key in header with three states — PROCESSING returning 409, COMPLETED returning original response with `Idempotency-Replayed: true`. The three-state model is what production systems actually implement.

---

Here's your Drill D question:

---

> *"Your Savings Account API currently has this withdrawal endpoint response:*
>
> ```json
> {
>   "withdrawalId": "WDL-001",
>   "amount": 100000,
>   "status": "PROCESSING",
>   "accountId": "LSA-789",
>   "createdDate": "2024-11-15"
> }
> ```
>
> *The team wants to make the following changes:*
> 1. *Add a `noticePeriodDays` field showing how many days until funds are available*
> 2. *Rename `amount` to `withdrawalAmountPence` to make the unit explicit*
> 3. *Rename `createdDate` to `submittedAt` and change it from a date string to an ISO 8601 timestamp*
> 4. *Change `status` from a free-text string to a strict enum: `NOTICE_GIVEN`, `PROCESSING`, `COMPLETED`, `FAILED`*
> 5. *Remove `accountId` from the response — it's already in the URL path*
> 6. *Add a nested `links` object with HATEOAS-style next actions*
>
> *For each change: is it breaking or non-breaking? Justify your reasoning. Then give me your versioning strategy, deprecation timeline, and how you communicate this to the three teams consuming this API — the Mobile App team, the Internet Banking team, and the Reporting team who stores raw API responses in their data warehouse."*

---

Be specific — name which changes are breaking and exactly why, give a timeline with real numbers, and think about the Reporting team's data warehouse problem carefully — that's the curveball in this question. Go.

Good instincts on breaking vs non-breaking classification. Score and detailed answer below.

---

## Score: 5.5 / 10

### What You Got Right ✅
- Adding `noticePeriodDays` = non-breaking — correct
- Renaming fields = breaking — correct reasoning
- Removing `accountId` = breaking — correct
- Publish v2 alongside v1 — correct approach
- Deprecation header — correct
- 6-12 month timeline — correct ballpark

### What's Missing ❌

**Five specific gaps:**

1. **Status enum classification is wrong** — you said item 4 is both non-breaking AND breaking. It needs a clear decision with reasoning. The answer depends on whether existing consumers handle unknown enum values gracefully.

2. **HATEOAS links classification needs nuance** — adding `links` is non-breaking only IF it's optional and consumers ignore unknown fields. But if Reporting team stores raw JSON in a data warehouse schema with fixed columns, a new nested object breaks their ETL pipeline. Context matters.

3. **No specific versioning mechanism named** — URL versioning vs header versioning? Which one and why for this specific scenario?

4. **The Reporting team data warehouse problem completely missed** — this is the curveball. Storing raw API responses in a data warehouse means even non-breaking API changes can break their ETL. This needs a specific answer.

5. **No Sunset header content, no 410 Gone plan, no traffic monitoring** — the deprecation process needs specifics not just "communicate to customers."

---

## The 9/10 Answer

### Change-by-Change Analysis

```
CHANGE 1: Add noticePeriodDays
─────────────────────────────
Decision: NON-BREAKING ✅

Reasoning:
Adding a new optional field to a response is safe IF consumers
follow Postel's Law — "be conservative in what you send,
liberal in what you accept."
Well-behaved JSON consumers ignore unknown fields.
Mobile app deserialises into a model object — unknown fields dropped.
Internet Banking parses what it needs — noticePeriodDays ignored
if not in their model yet.

Caveat — the Reporting team exception:
If Reporting has a rigid data warehouse schema:
CREATE TABLE withdrawals (
  withdrawal_id VARCHAR,
  amount INT,
  status VARCHAR,
  account_id VARCHAR,
  created_date DATE
  -- no noticePeriodDays column
)
Their ETL might fail on schema validation if they use strict
column mapping. Non-breaking at API level, breaking at
consumer implementation level.
Action: Alert Reporting team specifically, give them
schema migration window before deploying this field.


CHANGE 2: Rename amount → withdrawalAmountPence
────────────────────────────────────────────────
Decision: BREAKING ❌

Reasoning:
Any consumer reading response.amount gets null after this change.
Mobile app: binding.amount → null → displays £0.00 to customer
Internet Banking: payment confirmation shows £0 → FCA complaint
Reporting: amount column in data warehouse gets null → silent data loss

This is a field removal from the consumer's perspective.
Even though you're adding withdrawalAmountPence, consumers
don't know that — they still read amount.

Migration path in v2:
{
  "withdrawalAmountPence": 100000,  // new field name
  "amount": 100000                  // kept temporarily in v2
                                    // for backwards compat
                                    // removed in v3
}
Actually return BOTH during transition period.
This is the strangler fig applied to a field.


CHANGE 3: createdDate → submittedAt + type change
──────────────────────────────────────────────────
Decision: BREAKING ❌ — double breaking

Two violations:
(a) Field rename — createdDate removed, submittedAt added
    Any consumer reading createdDate gets null.

(b) Type change — date string → ISO 8601 timestamp
    Before: "2024-11-15"          (date only)
    After:  "2024-11-15T09:30:00Z" (datetime)

    This is WORSE than a rename.
    Reporting team has:
    created_date DATE column → stores "2024-11-15" fine
    After change: "2024-11-15T09:30:00Z" fails DATE type parsing
    → ETL job crashes at 2am → data gap in regulatory reports
    → FCA reporting failure if gap crosses a reporting period

    Mobile app that does date arithmetic on createdDate:
    LocalDate.parse(response.createdDate) → works
    LocalDate.parse("2024-11-15T09:30:00Z") → DateTimeParseException


CHANGE 4: status free-text → strict enum
─────────────────────────────────────────
Decision: CONDITIONALLY NON-BREAKING ⚠️

This is the nuanced one you classified inconsistently.

The values themselves haven't changed:
"PROCESSING" existed before and exists in the enum.
"NOTICE_GIVEN", "COMPLETED", "FAILED" are new values.

Non-breaking IF:
Consumer handles unknown/unexpected status values gracefully:
switch (response.status) {
  case "PROCESSING": showSpinner(); break;
  case "COMPLETED":  showSuccess(); break;
  default:           showGenericStatus(); // handles NOTICE_GIVEN, FAILED
}
→ Mobile app degrades gracefully — shows generic status for new values.

Breaking IF:
Consumer fails on unexpected enum values:
StatusEnum status = StatusEnum.valueOf(response.status);
// NOTICE_GIVEN not in their enum → IllegalArgumentException
// App crashes → customer sees error screen

Decision for your API:
Treat as breaking to be safe. You cannot know how all consumers
handle unknown enum values. Consumers should have updated
their models to handle new values before you deploy.
Communicate new values in advance with a migration guide.
Deprecation header on responses that will start returning
NOTICE_GIVEN once this deploys.


CHANGE 5: Remove accountId
───────────────────────────
Decision: BREAKING ❌

Even though accountId is in the URL, some consumers may be
extracting it from the response body for convenience —
especially the Reporting team building their data warehouse
denormalised table.

Removing any field that consumers might be reading is breaking.
You cannot know what every consumer is doing with every field.

The principle: you can add, you cannot remove without versioning.

Mitigation: check your API gateway logs before removing.
Filter: requests where accountId in response body was the only
place that consumer called — if no consumer ever reads accounts
from this endpoint's response (they GET /accounts separately),
removal risk is lower. But still needs version bump.


CHANGE 6: Add links object (HATEOAS)
──────────────────────────────────────
Decision: NON-BREAKING for API consumers ✅
         BREAKING for Reporting team ⚠️

For Mobile and Internet Banking:
Adding a new nested object is safe — unknown fields ignored.
{
  "withdrawalId": "WDL-001",
  ...existing fields...,
  "links": {                    // new — ignored by old consumers
    "cancel": "/v1/savings/accounts/lsa/LSA-789/withdrawals/WDL-001",
    "status": "/v1/savings/accounts/lsa/LSA-789/withdrawals/WDL-001/status"
  }
}

For Reporting team data warehouse:
Their ETL stores raw JSON response in a JSONB column or
flattens to columns. If they flatten:
INSERT INTO withdrawals (withdrawal_id, amount, status, account_id, created_date)
A new nested links object causes:
- Strict ETL: "unexpected field links" → job fails
- Permissive ETL: links ignored → no data loss
You don't know which they use → treat as breaking for Reporting.
```

---

### Summary Classification

| Change | Breaking? | Primary Reason |
|---|---|---|
| Add `noticePeriodDays` | Non-breaking (⚠️ Reporting ETL risk) | Adding optional field |
| Rename `amount` | Breaking | Field removal from consumer view |
| Rename + retype `createdDate` | Breaking (double) | Field removal + type incompatibility |
| `status` → enum | Treat as breaking | Unknown enum values may crash consumers |
| Remove `accountId` | Breaking | Field removal |
| Add `links` object | Non-breaking (⚠️ Reporting ETL risk) | Adding optional object |

---

### Versioning Strategy

```
Choice: URL versioning — /v1/ → /v2/
Why for this scenario:
Three consuming teams with different tech stacks.
URL versioning is the most explicit — no hidden header,
no "I forgot to send the version header" silent failures.
Mobile app, Internet Banking, Reporting all see /v2/ clearly.
API gateway routing by URL prefix is trivial.
Open Banking spec uses URL versioning — industry norm in UK FS.

What v2 looks like:
{
  "withdrawalId": "WDL-001",
  "withdrawalAmountPence": 100000,   // renamed
  "amount": 100000,                  // KEPT in v2 for transition
  "status": "NOTICE_GIVEN",          // now strict enum
  "submittedAt": "2024-11-15T09:30:00Z",  // renamed + retyped
  "createdDate": "2024-11-15",        // KEPT in v2 for transition
  "noticePeriodDays": 30,             // new field
  "links": {                          // new object
    "cancel": "...",
    "status": "..."
  }
  // accountId removed from v2
}

Strangler fig on fields:
Both old and new field names returned in v2 initially.
Gives consumers time to migrate their parsing logic
before old field names are removed in v3 (6 months later).
```

---

### Deprecation Timeline — Specific

```
Week 0:  Publish v2 in staging. Migration guide published.
         Notify all three consuming teams with:
         - What changed (field-by-field diff)
         - v2 endpoint URLs
         - Code examples in their language (Java for Reporting ETL)
         - Contact person for questions

Week 1:  Deploy v2 to production alongside v1.
         Add headers to ALL v1 responses:
         Deprecation: true
         Sunset: Sat, 15 Nov 2025 00:00:00 GMT  (12 months)
         Link: https://developer.bank.com/savings/migration-v1-v2

Week 2:  API gateway dashboard — track v1 vs v2 traffic per consumer.
         Three consumers visible by their client certificate (mTLS)
         or client_id in JWT.

Month 1: Mobile App team — target: migrate by Month 3
Month 2: Internet Banking team — target: migrate by Month 4
Month 3: Reporting team — longest runway, data warehouse migration needed

Month 6: Check dashboard — who is still on v1?
         If Mobile still on v1 → escalate to their EM directly.
         SLT visibility if any team is blocking.

Month 9: Final warning to any remaining v1 consumers.
         Add warning logs: "This consumer is still on v1 — 90 days remaining"
         Visible in their own service logs via correlation ID.

Month 12: Sunset date.
          v1 returns 410 Gone with body:
          {
            "error": "API_VERSION_SUNSET",
            "message": "v1 of the Savings API was sunset on 2025-11-15.",
            "migrationGuide": "https://developer.bank.com/savings/migration-v1-v2",
            "currentVersion": "v2",
            "v2Endpoint": "https://api.bank.com/v2/savings/accounts"
          }
          410 not 404 — resource existed, now gone intentionally.

Month 13: Remove v1 code from codebase.
          Keep v1 OpenAPI spec in documentation archive
          (FCA may ask what your API contract was at a point in time).
```

---

### The Reporting Team — The Curveball Answer

```
This is the hardest part and most candidates miss it entirely.

THE PROBLEM:
Reporting team stores raw API responses in their data warehouse.
Two possible architectures — you need to find out which:

Architecture A: Raw JSON in JSONB column
SELECT response_body::jsonb->>'amount' AS amount
FROM withdrawals_raw;
→ Adding new fields: safe (JSONB ignores unknown)
→ Renaming fields: breaking (their queries still read old field names)
→ Type change on createdDate: queries doing date arithmetic break

Architecture B: Flattened columns via ETL
amount INT,
status VARCHAR,
account_id VARCHAR,
created_date DATE
→ Schema change needed for every API change
→ Even adding noticePeriodDays needs ALTER TABLE
→ Type change DATE → TIMESTAMP on created_date → ETL job rewrite

YOUR ACTIONS specific to Reporting team:

1. Discovery call before any deployment:
   "How are you consuming our API response?
    Show me your ETL pipeline and schema."
   This determines their blast radius.

2. For Architecture A (JSONB):
   Give them 3 months to update their queries from:
   response_body->>'amount'
   to:
   response_body->>'withdrawalAmountPence'
   AND keep old field names in v2 response for transition.

3. For Architecture B (flattened ETL):
   They need:
   - ALTER TABLE ADD COLUMN withdrawal_amount_pence INT
   - ALTER TABLE ADD COLUMN submitted_at TIMESTAMP
   - ALTER TABLE ADD COLUMN notice_period_days INT
   - Update ETL mapping config
   - Backfill historical records where possible
   - Run old and new ETL in parallel until verified
   Give them 6 months minimum — data warehouse migrations
   require QA, UAT, sign-off from Finance and Risk.

4. FCA consideration:
   Reporting team feeds FCA regulatory returns.
   If their ETL breaks on the createdDate type change,
   they miss a submission deadline.
   FCA SUP 16 — firms must submit accurate regulatory data.
   A data gap is a reportable incident.
   This is why the Reporting team gets the longest runway
   and why createdDate → submittedAt needs the most careful
   migration — both field name AND type change together
   is maximum blast radius for a data warehouse consumer.

5. Practical mitigation:
   Deploy the non-breaking changes (noticePeriodDays, links)
   to v1 first — no version bump needed.
   Let Reporting team add columns for new fields on v1 timeline.
   Then cut to v2 for the breaking changes — Reporting team
   has already adapted their schema for non-breaking additions,
   only needs to handle the breaking renames in v2 migration.
   Reduces their v2 migration scope by 40%.
```

---

## What Moved You from 5.5 to 9/10

**The enum nuance** — treating status enum as conditionally non-breaking depending on consumer implementation, but defaulting to breaking to be safe. Interviewers want you to reason about the consumer, not just the API.

**The Reporting team data warehouse problem** — this is deliberately planted to test whether you think beyond the API contract to downstream consumers. A non-breaking API change can be a breaking ETL change. Discovering their architecture before deploying is the EM move.

**The strangler fig on fields** — returning both `amount` and `withdrawalAmountPence` in v2 during transition gives consumers time to migrate without a hard cutover. This is production-grade thinking.

**The FCA regulatory reporting angle** — connecting createdDate type change to a potential FCA submission failure. That's the regulatory consequence that lands in EM interviews at JPMC/GS.

---
