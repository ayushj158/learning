# 📋 Master Prep Checklist — All Categories

## 1. 🏗️ System Design & Architecture

### 1.1 Foundations
- [x] CAP Theorem + PACELC
- [x] ACID vs BASE
- [x] Caching Strategies (5 patterns)
- [x] Load Balancing (6 algorithms)
- [x] Database Choices (6 types)
- [ ] Scalability patterns — horizontal vs vertical, stateless services

### 1.2 Distributed Systems Concepts
- [x] Consistency models — strong, eventual, read-your-writes
- [x] Distributed transactions — 2PC vs Saga
- [x] Idempotency keys — concept
- [x] Message queues — Kafka deep dive
- [x] Polyglot persistence
- [ ] Leader election — Raft, ZooKeeper internals
- [ ] Idempotency — end-to-end design pattern

### 1.3 Microservices Patterns
- [x] Saga (choreography vs orchestration)
- [x] CQRS
- [x] Event Sourcing
- [x] Outbox Pattern
- [x] Strangler Fig
- [x] Dual-Write Pattern
- [ ] API Composition pattern
- [ ] Backends for Frontends (BFF)

### 1.4 Resilience Patterns
- [x] Circuit Breaker
- [x] Retry + Exponential Backoff
- [x] Bulkhead
- [x] Timeout
- [x] Fallback & Graceful Degradation
- [ ] Backpressure patterns

### 1.5 API & Communication
- [ ] REST vs gRPC vs GraphQL — deep comparison
- [ ] API Gateway patterns — auth, rate limiting, routing
- [ ] Rate Limiting — token bucket, leaky bucket, sliding window
- [ ] API versioning strategies
- [ ] Service discovery — client-side vs server-side

### 1.6 Service Mesh
- [ ] Istio architecture — control plane vs data plane
- [ ] Sidecar pattern — Envoy proxy internals
- [ ] mTLS — automatic cert rotation, zero-trust
- [ ] Traffic management — VirtualService, DestinationRule
- [ ] Canary deployments via Istio
- [ ] Circuit breaking in Istio

### 1.7 Data Architecture
- [ ] OLTP vs OLAP — differences, when to use each
- [ ] Lambda architecture — batch + speed + serving layers
- [ ] Kappa architecture — stream-only, when over Lambda
- [ ] Data Lake patterns — raw, curated, consumption zones
- [ ] Event streaming pipelines — Kafka → Flink/Spark → Data Lake
- [ ] Data modeling — normalisation vs denormalisation tradeoffs
- [ ] Time-series data patterns

### 1.8 Security Architecture
- [ ] Zero Trust architecture — never trust, always verify
- [ ] mTLS between microservices — mutual authentication
- [ ] OAuth2 / OIDC / JWT flows — Authorization Code, Client Credentials
- [ ] Secrets management — Vault, GCP Secret Manager
- [ ] Data encryption — at rest, in transit, field-level
- [ ] API security — input validation, injection prevention

### 1.9 Reliability & Operations Design
- [ ] SLI — what to measure
- [ ] SLO — how to define targets
- [ ] SLA — contractual commitments
- [ ] Error budgets — how to use them
- [ ] RTO vs RPO — definitions, design implications
- [ ] Active-Active vs Active-Passive — when each
- [ ] Chaos Engineering — principles, GameDays, tools
- [ ] Blue-Green deployments — zero downtime
- [ ] Canary deployments — gradual rollout, metrics-based
- [ ] Feature flags — runtime toggles, kill switches

### 1.10 Observability ⚠️ (promised!)
- [ ] Distributed tracing — Jaeger, Zipkin, Cloud Trace
- [ ] Trace context propagation — W3C TraceContext, B3
- [ ] Structured logging — JSON logs, correlation IDs
- [ ] Log aggregation — ELK, Cloud Logging
- [ ] Metrics — counters, gauges, histograms, summaries
- [ ] Prometheus + Grafana — scraping, dashboards, alerting
- [ ] Alerting — symptom vs cause, alert fatigue
- [ ] Incident management — detection, triage, RCA
- [ ] Post-mortem culture — blameless, learning

### 1.11 Cloud-Native Architecture
- [x] Containerisation — Docker, Kubernetes
- [x] Multi-region design patterns
- [ ] Serverless patterns — when to use, cold start
- [ ] Service Mesh (cloud-native) — Istio on GKE
- [ ] GitOps — ArgoCD, Flux

### 1.12 Full System Design Problems
- [x] Account Statement Service (full)
- [x] Real-Time Fraud Detection (partial)
- [x] Trade Settlement Platform (partial)
- [ ] Payment Processing System (full end-to-end)
- [ ] API Rate Limiter
- [ ] Notification System
- [ ] Trade Order Management System
- [ ] Audit & Compliance System

---

## 2. ☕ Java / Microservices Deep Dive

### 2.1 Core Java Internals
- [ ] JVM architecture — ClassLoader, JIT, Execution Engine
- [ ] Memory areas — Heap, Stack, Metaspace, Code Cache
- [ ] Heap structure — Young Gen, Old Gen, GC regions
- [ ] GC algorithms — G1, ZGC, Shenandoah basics
- [ ] GC tuning — heap sizing, pause targets
- [ ] Memory leaks — causes, detection, tools
- [ ] Java Memory Model — happens-before, visibility
- [ ] volatile — guarantees and limitations

### 2.2 Java Versions — Key Features
- [ ] Java 8 — Streams, Lambdas, Optional, CompletableFuture
- [ ] Java 11 — HTTP Client, var, String methods
- [ ] Java 17 — Records, Sealed classes, Pattern Matching
- [ ] Java 21 — Virtual threads, Structured concurrency

### 2.3 Collections
- [ ] HashMap internals — hashing, resizing, tree buckets
- [ ] ConcurrentHashMap — CAS + synchronized (Java 8+)
- [ ] LinkedHashMap, TreeMap — when to use each
- [ ] BlockingQueue — ArrayBlockingQueue, LinkedBlockingQueue
- [ ] CopyOnWriteArrayList — use case, tradeoffs

### 2.4 Concurrency
- [ ] Thread lifecycle — all 6 states
- [ ] synchronized vs ReentrantLock vs StampedLock
- [ ] volatile vs Atomic classes
- [ ] CAS — how atomics work internally
- [ ] ExecutorService + ThreadPoolExecutor internals
- [ ] Thread pool sizing — CPU-bound vs IO-bound formula
- [ ] CompletableFuture — thenApply, thenCompose, allOf, anyOf
- [ ] ForkJoinPool — work stealing
- [ ] Virtual threads (Java 21) — vs platform threads
- [ ] Race condition, deadlock, livelock, starvation
- [ ] Double-checked locking — correct implementation

### 2.5 Reactive Programming
- [ ] Reactive Streams spec — Publisher, Subscriber
- [ ] Project Reactor — Mono, Flux
- [ ] WebFlux — non-blocking HTTP
- [ ] Backpressure — what it is, how handled
- [ ] Reactive vs imperative — when each in FS

### 2.6 Design Patterns (GoF)
- [ ] Singleton — thread-safe (enum, holder pattern)
- [ ] Factory Method + Abstract Factory
- [ ] Builder — fluent, immutable objects
- [ ] Adapter — legacy CBS integration
- [ ] Decorator — adding behaviour
- [ ] Facade — simplifying subsystems
- [ ] Proxy — virtual, protection, remote
- [ ] Strategy — payment routing, fraud scoring
- [ ] Observer — domain events
- [ ] Chain of Responsibility — validation, approval
- [ ] Command — encapsulating requests, audit
- [ ] State — payment state machine
- [ ] Template Method

### 2.7 Enterprise / DDD Patterns
- [ ] Repository pattern
- [ ] Value Object — Money, AccountNumber
- [ ] Aggregate Root — consistency boundary
- [ ] Domain Events
- [ ] Anti-corruption Layer
- [ ] Specification pattern

### 2.8 DDD
- [ ] Ubiquitous language
- [ ] Bounded Context
- [ ] Context Map — relationships
- [ ] Strategic vs Tactical DDD
- [ ] Identifying service boundaries in FS

### 2.9 Inter-Service Communication
- [ ] REST — Richardson Maturity Model, HATEOAS
- [ ] gRPC — Protobuf, HTTP/2, streaming, when over REST
- [ ] GraphQL — federation, N+1 problem
- [ ] API versioning — URL, header, content negotiation
- [ ] Idempotency — end-to-end key design

### 2.10 Spring Boot
- [ ] IoC container — BeanFactory vs ApplicationContext
- [ ] Dependency injection — constructor vs field vs setter
- [ ] Bean lifecycle + scopes
- [ ] AOP — aspects, pointcuts, FS use cases
- [ ] @Transactional — propagation, isolation, rollback
- [ ] Spring Data JPA — N+1 problem, fetch strategies
- [ ] Transaction isolation levels
- [ ] Optimistic vs pessimistic locking
- [ ] Spring Actuator — health, metrics
- [ ] Spring Security — filter chain, JWT validation

### 2.11 Performance & Tuning
- [ ] HikariCP connection pool sizing
- [ ] JVM heap sizing flags
- [ ] Thread pool sizing formula
- [ ] Caffeine cache — local JVM cache
- [ ] Async processing — CompletableFuture patterns
- [ ] Serialisation performance — JSON vs Protobuf vs Avro

### 2.12 Security in Java
- [ ] OAuth2 flows — Authorization Code, Client Credentials, PKCE
- [ ] OIDC — ID token, userinfo
- [ ] JWT — structure, RS256 vs HS256, validation
- [ ] Service-to-service auth — client credentials + mTLS
- [ ] Secrets management — Vault integration
- [ ] PII masking in logs

### 2.13 DevOps & CI/CD
- [ ] Docker best practices — multi-stage, minimal images
- [ ] JVM in containers — container-aware flags
- [ ] Blue-Green deployment
- [ ] Canary deployment
- [ ] Feature flags
- [ ] CI/CD pipeline stages — build, test, SAST, deploy
- [ ] Test pyramid — unit, integration, contract, E2E
- [ ] Contract testing — Pact

---

## 3. ☁️ Cloud (GCP Primary)

### 3.1 Compute
- [x] GKE — regional clusters, node pools, autoscaling
- [x] GKE security hardening — private cluster, binary auth
- [x] GLB + ILB + NEG — health checks, failover
- [x] East-West traffic — ILB, Cross-Region ILB, Istio
- [ ] Cloud Run — serverless containers, when over GKE
- [ ] Anthos — hybrid cloud, multi-cloud GKE

### 3.2 Networking
- [x] VPC — Shared VPC, Hub-and-Spoke
- [x] Subnets — design for FS
- [x] Private Google Access + PSC
- [x] Dedicated Interconnect — HA configuration
- [x] Cloud Armor — WAF, DDoS, OWASP
- [x] VPC Service Controls — data exfiltration prevention
- [x] Cloud NAT — outbound internet for private nodes
- [ ] Cloud DNS — internal zones, split-horizon DNS
- [ ] Network Intelligence Center — topology, monitoring

### 3.3 Security
- [x] Cloud IAM — hierarchy, predefined vs custom roles
- [x] Workload Identity — KSA → GSA mapping
- [x] Cloud KMS + CMEK — crypto shredding
- [x] VPC Service Controls + Access Context Manager
- [x] Cloud Armor
- [x] IAP Tunnel + PAM — zero-trust production access
- [x] Binary Authorization
- [ ] Secret Manager — rotation, versioning
- [ ] Cloud DLP — PII discovery, de-identification
- [ ] Security Command Center — threat detection
- [ ] Chronicle SIEM

### 3.4 Databases
- [x] Cloud Spanner — TrueTime, splits, multi-region configs
- [x] Cloud SQL — HA, read replicas, connection proxy
- [x] AlloyDB — columnar engine, read pool
- [x] Bigtable — row key design, tablet architecture
- [x] Firestore — documents, real-time, hot document limit
- [x] BigQuery — columnar, partitioning, clustering, slots
- [x] Memorystore — Redis HA, cluster mode
- [ ] Cloud Datastore — legacy, vs Firestore

### 3.5 Data & Streaming
- [x] Pub/Sub — ordering keys, exactly-once, DLQ
- [x] Pub/Sub vs Kafka — when each
- [ ] Dataflow — streaming + batch, Apache Beam
- [ ] Datastream — CDC from databases, FS use cases
- [ ] Data Catalog — metadata management, policy tags
- [ ] Cloud DLP — data de-identification
- [ ] BigQuery ML — ML on structured data

### 3.6 Multi-Region & Resilience
- [x] Multi-region architecture — UK + Belgium, EU stacks
- [x] Global Load Balancer — anycast, regional failover
- [x] Disaster Recovery tiers — cold, warm, hot
- [x] Active-Active vs Active-Passive
- [x] Data residency — jurisdiction routing by JWT
- [ ] Cloud Endpoints — API management
- [ ] Traffic Director — service mesh control plane

### 3.7 Cost Optimisation
- [x] Committed Use Discounts — 1yr vs 3yr
- [x] Spot VMs — when safe in FS
- [x] Storage lifecycle policies
- [x] Right-sizing — VPA, GCP Recommender
- [x] Network egress optimisation
- [x] BigQuery slot commitments
- [ ] FinOps tooling — billing exports, Looker dashboards

---

## 4. 👔 Leadership & Behavioural

### 4.1 Tell Your Story
- [ ] 2-minute intro — 12 years, Publicis Sapient, Lloyds, EM
- [ ] Career trajectory — consultant → EM → GCC
- [ ] Why GCC vs consulting?
- [ ] Why this bank specifically?
- [ ] Why India GCC now?

### 4.2 Leadership Style
- [ ] How you manage a team of 30
- [ ] Engineering culture you've built
- [ ] How you develop engineers
- [ ] Handling underperformers
- [ ] Hiring decisions

### 4.3 Delivery & Execution
- [ ] Managing 3 features simultaneously
- [ ] Prioritisation framework — how you decide what matters
- [ ] Handling scope creep
- [ ] Delivering under pressure
- [ ] Commercial savings — how you drove them at Lloyds

### 4.4 Stakeholder Management
- [ ] Managing upward — CTO, MD conversations
- [ ] Managing sideways — peer EMs, product managers
- [ ] Managing client (Lloyds) relationship as consultant
- [ ] Influencing without authority

### 4.5 Technical Leadership
- [ ] Architecture decisions — how you make and communicate them
- [ ] Tech debt — how you balance it vs delivery
- [ ] Build vs buy decisions — FS examples
- [ ] Engineering standards + governance
- [ ] Driving engineering excellence across 30 people

### 4.6 Conflict & Challenge
- [ ] Biggest failure — what happened, what you learned
- [ ] Disagreement with senior stakeholder — how resolved
- [ ] Team conflict — how managed
- [ ] Saying no — to business, to scope

### 4.7 STAR Stories (prepare 8-10 strong ones from Lloyds)
- [ ] Greatest technical achievement
- [ ] Biggest delivery under pressure
- [ ] Driving commercial savings
- [ ] Managing a difficult stakeholder
- [ ] Turning around an underperforming team/project
- [ ] Introducing engineering culture change
- [ ] Handling a production incident
- [ ] Making a difficult prioritisation call
- [ ] Influencing without authority
- [ ] Hiring/growing talent

### 4.8 GCC-Specific Questions
- [ ] How will you transition from consulting to product?
- [ ] How will you handle being an IC contributor vs managing?
- [ ] What do you know about India GCC landscape?
- [ ] How do you plan to ramp up on a new domain?

---

## 5. 🏦 Domain (Financial Services)

### 5.1 Payments
- [ ] UK payment schemes — Faster Payments, CHAPS, Bacs
- [ ] International — SWIFT, SEPA, correspondent banking
- [ ] Card payment flow — issuer, acquirer, card scheme
- [ ] Open Banking — PSD2, APIs, TPPs
- [ ] Payment reconciliation — how it works
- [ ] Fraud in payments — types, controls
- [ ] ISO 20022 — new messaging standard

### 5.2 Banking Products
- [ ] Current accounts — features, overdraft mechanics
- [ ] Savings accounts — interest calculation
- [ ] Loans + mortgages — origination, servicing
- [ ] Commercial banking — SME vs corporate products
- [ ] Trade finance — letters of credit, guarantees

### 5.3 Capital Markets / Trading
- [ ] Trade lifecycle — order → execution → clearing → settlement
- [ ] Settlement — T+2 moving to T+1, DVP
- [ ] FIX protocol — standard for trade messages
- [ ] Order types — market, limit, stop
- [ ] Asset classes — equities, FX, bonds, derivatives basics

### 5.4 Regulatory & Compliance
- [x] FCA — UK regulator, requirements
- [x] GDPR — principles, rights, data residency
- [x] PCI-DSS — card data protection
- [x] AML — Anti Money Laundering concepts
- [x] KYC — Know Your Customer process
- [ ] Basel III — capital requirements basics
- [ ] MiFID II — EU trading regulations
- [ ] DORA — Digital Operational Resilience Act (new!)
- [ ] PSD2 — Open Banking regulation
- [ ] SR 11-7 — model risk management (US/global)

### 5.5 Risk
- [ ] Credit risk — PD, LGD, EAD basics
- [ ] Market risk — VaR, stress testing basics
- [ ] Operational risk — definition, controls
- [ ] Liquidity risk — basics

### 5.6 Technology in FS
- [x] Core Banking Systems — what they are, why hard to replace
- [x] Data residency — regulatory requirements by jurisdiction
- [x] Tokenisation in FS — PCI-DSS compliance
- [ ] Real-time payments — technical requirements
- [ ] Open Banking APIs — technical standards
- [ ] RegTech — technology for regulatory compliance
- [ ] SupTech — supervisory technology

---

## 📊 Master Coverage Summary

| Category | Total Topics | Covered ✅ | Pending ❌ | % Done |
|---|---|---|---|---|
| **System Design & Arch** | 80 | 45 | 35 | 56% |
| **Java / MS Deep Dive** | 90 | 10 | 80 | 11% |
| **Cloud (GCP)** | 55 | 42 | 13 | 76% |
| **Leadership & Behavioural** | 35 | 0 | 35 | 0% |
| **Financial Services Domain** | 40 | 12 | 28 | 30% |
| **TOTAL** | **300** | **109** | **191** | **36%** |

---

## 🎯 Priority Matrix — What to Do Next

### 🔴 Highest Priority (interview-critical, not covered):

```
System Design:
→ Observability (Day 7 — promised!)
→ SLI/SLO/SLA/Error budgets
→ API Gateway + Rate Limiting
→ Service Mesh (Istio)
→ OAuth2/JWT flows
→ Full Payment System Design

Leadership & Behavioural:
→ Your STAR stories (0% done — separate round!)
→ Your intro story
→ Why GCC narrative

Financial Services:
→ Payment schemes (Faster Payments, SWIFT)
→ DORA (new regulation — will be asked!)
→ Trade lifecycle
```

### 🟡 Medium Priority:
```
Java:
→ CompletableFuture patterns
→ @Transactional deep dive
→ OAuth2/JWT in Spring Security
→ DDD + Bounded Contexts

System Design:
→ Blue-Green + Canary
→ Lambda vs Kappa architecture
→ Zero Trust architecture

GCP:
→ Dataflow + Datastream
→ Secret Manager
→ Cloud DLP
```

### 🟢 Lower Priority (time permitting):
```
Java internals:
→ GC deep dive
→ JVM memory model
→ Collections internals

FS Domain:
→ Basel III, MiFID II
→ Credit/Market/Liquidity risk
→ Trade finance details
```

---

This is your complete master checklist — 300 topics across 5 categories. Shall we now pick what to tackle next? 🎯