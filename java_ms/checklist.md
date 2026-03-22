# ☕ Java / Microservices Deep Dive — Complete Checklist

Let me build this properly from scratch — everything an EM at a tier-1 FS GCC would be expected to know.

---

## 🗂️ Java / Microservices — Full Topic Map

### 1. Core Java Internals

#### 1.1 JVM Architecture
- [ ] JVM components — ClassLoader, JIT, Execution Engine
- [ ] Memory areas — Heap, Stack, Metaspace, Code Cache, PC Register
- [ ] Heap structure — Young Gen (Eden, S0, S1), Old Gen
- [ ] Object lifecycle — creation, promotion, collection
- [ ] ClassLoader hierarchy — Bootstrap, Extension, Application
- [ ] Class loading process — Loading, Linking, Initialisation

#### 1.2 Garbage Collection
- [ ] GC algorithms — Serial, Parallel, CMS, G1, ZGC, Shenandoah
- [ ] G1 GC internals — regions, mixed collections, humongous objects
- [ ] ZGC internals — concurrent, low-pause, coloured pointers
- [ ] GC tuning flags — heap size, pause targets, region size
- [ ] GC logs — reading and interpreting GC output
- [ ] Memory leaks — common causes, detection, tools (VisualVM, MAT)
- [ ] OutOfMemoryError types — heap space, metaspace, GC overhead limit

#### 1.3 Java Memory Model (JMM)
- [ ] Happens-before relationship
- [ ] Visibility problem — why volatile needed
- [ ] volatile keyword — guarantees, limitations
- [ ] Memory barriers — read/write barriers
- [ ] Reordering — compiler + CPU reordering risks

#### 1.4 Java Versions — Key Features
- [ ] Java 8 — Streams, Lambdas, Optional, default methods, CompletableFuture
- [ ] Java 9 — Modules (JPMS), Flow API (reactive streams)
- [ ] Java 11 — HTTP Client, var in lambdas, String methods
- [ ] Java 14 — Records (preview), Pattern Matching (preview)
- [ ] Java 17 — Records, Sealed classes, Pattern Matching, Text blocks
- [ ] Java 21 — Virtual threads (Project Loom), Structured concurrency

#### 1.5 Collections Deep Dive
- [ ] HashMap internals — hashing, buckets, linked list → tree (Java 8+)
- [ ] HashMap resizing — load factor, rehashing, capacity doubling
- [ ] ConcurrentHashMap — segments (Java 7) vs CAS + synchronized (Java 8+)
- [ ] LinkedHashMap — access order vs insertion order
- [ ] TreeMap — Red-Black tree, NavigableMap
- [ ] ArrayDeque vs LinkedList — when to use each
- [ ] CopyOnWriteArrayList — use case, tradeoffs
- [ ] BlockingQueue implementations — ArrayBlockingQueue, LinkedBlockingQueue

---

### 2. Java Concurrency

#### 2.1 Thread Fundamentals
- [ ] Thread lifecycle — NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED
- [ ] Thread creation — Thread class vs Runnable vs Callable
- [ ] Daemon threads vs user threads
- [ ] Thread interruption — interrupt(), isInterrupted(), InterruptedException
- [ ] Thread priority — how it works, OS scheduling

#### 2.2 Synchronisation Primitives
- [ ] synchronized keyword — object lock, class lock, monitor
- [ ] ReentrantLock — fairness, tryLock, lockInterruptibly
- [ ] ReentrantReadWriteLock — multiple readers, single writer
- [ ] StampedLock — optimistic reading (Java 8+)
- [ ] volatile — visibility guarantee, no atomicity
- [ ] Atomic classes — AtomicInteger, AtomicReference, AtomicLong
- [ ] Compare-And-Swap (CAS) — how atomics work internally

#### 2.3 High-Level Concurrency
- [ ] ExecutorService — ThreadPoolExecutor internals
- [ ] ThreadPoolExecutor parameters — corePool, maxPool, queue, keepAlive
- [ ] Thread pool types — fixed, cached, scheduled, work-stealing
- [ ] Future vs CompletableFuture
- [ ] CompletableFuture — thenApply, thenCompose, thenCombine, exceptionally, allOf, anyOf
- [ ] ForkJoinPool — work stealing, RecursiveTask, RecursiveAction
- [ ] Virtual threads (Java 21) — how they differ from platform threads

#### 2.4 Concurrency Problems
- [ ] Race condition — definition, example, fix
- [ ] Deadlock — conditions, detection, prevention (lock ordering)
- [ ] Livelock — definition, example
- [ ] Starvation — definition, prevention
- [ ] Thread safety — what it means, how to achieve
- [ ] Double-checked locking — problem, correct implementation with volatile

#### 2.5 Reactive Programming
- [ ] Reactive Streams spec — Publisher, Subscriber, Subscription, Processor
- [ ] Project Reactor — Mono, Flux
- [ ] WebFlux — non-blocking HTTP server
- [ ] Backpressure — what it is, how Reactor handles it
- [ ] When reactive vs imperative — tradeoffs in FS context

---

### 3. Design Patterns (GoF)

#### 3.1 Creational Patterns
- [x] Singleton — thread-safe implementations (enum, double-checked, holder)
- [x] Factory Method — when to use, FS example
- [x] Abstract Factory — difference from Factory Method
- [x] Builder — fluent API, immutable objects, FS example (Payment builder)
- [x] Prototype — cloning, shallow vs deep copy

#### 3.2 Structural Patterns
- [x] Adapter — legacy CBS integration, SOAP → REST adapter
- [x] Decorator — adding behaviour without inheritance, FS example
- [x] Facade — simplifying complex subsystem, FS example (CBS facade)
- [x] Proxy — virtual proxy, protection proxy, remote proxy
- [x] Composite — tree structures
- [x] Bridge — separating abstraction from implementation

#### 3.3 Behavioural Patterns
- [x] Strategy — payment routing strategy, fraud scoring strategy
- [x] Observer — event-driven, domain events
- [x] Chain of Responsibility — validation chains, approval workflows
- [x] Command — encapsulating requests, undo/redo, audit trail
- [x] Template Method — defining algorithm skeleton
- [x] State — order state machine, payment state machine
- [x] Iterator — custom collection traversal
- [x] Mediator — reducing direct dependencies

#### 3.4 Enterprise / FS Patterns
- [ ] Repository pattern — data access abstraction
- [ ] Unit of Work — batching DB operations
- [ ] Domain Object — rich vs anemic domain models
- [ ] Value Object — immutable, equality by value (Money, AccountNumber)
- [ ] Aggregate Root — DDD, consistency boundary
- [ ] Specification pattern — composable business rules

---

### 4. Microservices Architecture

#### 4.1 Domain-Driven Design (DDD)
- [x] Ubiquitous language — shared vocabulary between dev and business
- [x] Bounded Context — defining service boundaries
- [x] Context Map — relationships between bounded contexts
- [x] Aggregate — consistency boundary, aggregate root
- [x] Entity vs Value Object
- [x] Domain Events — what happened in the domain
- [x] Anti-corruption Layer — protecting domain from external models
- [x] Strategic vs Tactical DDD

#### 4.2 Microservices Decomposition
- [x] Decompose by business capability
- [x] Decompose by subdomain (DDD)
- [x] Single Responsibility for services
- [x] Service granularity — too fine vs too coarse
- [x] Identifying service boundaries — FS example (payments, accounts, fraud)

#### 4.3 Inter-Service Communication
- [x] Synchronous — REST, gRPC
- [x] Asynchronous — Kafka, Pub/Sub, RabbitMQ
- [x] REST — Richardson Maturity Model, HATEOAS
- [x] gRPC — Protocol Buffers, HTTP/2, streaming, when over REST
- [x] GraphQL — when to use, federation, N+1 problem
- [x] API versioning strategies — URL, header, content negotiation

#### 4.4 API Design
- [x] RESTful API design principles
- [x] HTTP methods — GET, POST, PUT, PATCH, DELETE semantics
- [x] HTTP status codes — correct usage in FS
- [x] Pagination — offset vs cursor-based
- [x] Idempotency — idempotency keys, end-to-end design
- [x] API versioning — strategies and tradeoffs
- [x] OpenAPI / Swagger — contract-first design
- [x] API Gateway patterns — authentication, rate limiting, routing

#### 4.5 Service Mesh
- [x] What is a service mesh — data plane vs control plane
- [x] Sidecar pattern — how Envoy proxy works
- [x] Istio components — Pilot, Citadel, Galley, Mixer
- [x] Traffic management — VirtualService, DestinationRule
- [x] mTLS — automatic certificate rotation, zero-trust
- [x] Observability via mesh — distributed tracing, metrics
- [x] Circuit breaking in Istio — outlier detection
- [x] Canary deployments via Istio — traffic splitting

#### 4.6 Data Management in Microservices
- [ ] Database per service pattern
- [ ] Shared database antipattern — why to avoid
- [ ] Saga pattern ✅ (covered)
- [ ] CQRS ✅ (covered)
- [ ] Event Sourcing ✅ (covered)
- [ ] API Composition — aggregating data from multiple services
- [ ] Polyglot persistence ✅ (covered)

---

### 5. Spring Boot / Framework

#### 5.1 Spring Core
- [ ] IoC container — BeanFactory vs ApplicationContext
- [ ] Dependency Injection — constructor vs field vs setter injection
- [ ] Bean lifecycle — init, destroy, post-processors
- [ ] Bean scopes — singleton, prototype, request, session
- [ ] AOP — aspects, pointcuts, advice, FS use cases (logging, security)
- [ ] Spring profiles — environment-specific configuration
- [ ] @Transactional — propagation, isolation levels, rollback rules

#### 5.2 Spring Boot
- [ ] Auto-configuration — how it works, @ConditionalOn
- [ ] Actuator — health, metrics, info endpoints
- [ ] Spring Boot starters — what they do
- [ ] Externalized configuration — application.yml, environment variables, Config Server
- [ ] Spring Cloud Config — centralised configuration management

#### 5.3 Spring Data
- [ ] Spring Data JPA — repositories, query methods, JPQL
- [ ] JPA internals — EntityManager, persistence context, dirty checking
- [ ] N+1 problem — how it occurs, how to fix (fetch join, EntityGraph)
- [ ] Transaction isolation levels — READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE
- [ ] Optimistic vs pessimistic locking in JPA
- [ ] Spring Data Redis — template, repositories

#### 5.4 Spring Security
- [ ] Security filter chain
- [ ] Authentication vs Authorisation
- [ ] JWT validation — filter, claims extraction, role-based access
- [ ] OAuth2 resource server configuration
- [ ] Method-level security — @PreAuthorize, @PostAuthorize

#### 5.5 Testing
- [ ] Unit testing — JUnit 5, Mockito
- [ ] Integration testing — @SpringBootTest, TestContainers
- [ ] Contract testing — Pact, Spring Cloud Contract
- [ ] Performance testing — JMH (microbenchmarks)
- [ ] Test pyramid — unit vs integration vs E2E ratio
- [ ] Test-driven development (TDD) — when and how

---

### 6. Performance & Tuning

#### 6.1 JVM Performance
- [ ] JVM startup optimisation — AOT compilation, CDS
- [ ] Heap sizing — Xms, Xmx, metaspace limits
- [ ] GC tuning — pause time targets, region sizes
- [ ] Thread pool sizing — CPU-bound vs IO-bound formula
- [ ] JIT compilation — C1 vs C2 compiler, tiered compilation
- [ ] Profiling tools — JProfiler, async-profiler, Flight Recorder

#### 6.2 Application Performance
- [ ] Connection pool sizing — HikariCP configuration
- [ ] Query optimisation — indexes, execution plans, EXPLAIN
- [ ] Caching strategies in Java — Caffeine, EhCache
- [ ] Serialisation performance — JSON vs Protobuf vs Avro
- [ ] HTTP client performance — connection reuse, timeouts, retries

#### 6.3 Microservices Performance
- [ ] Async processing — CompletableFuture, reactive
- [ ] Bulk operations — batching DB calls
- [ ] Circuit breaker tuning — Resilience4j config ✅
- [ ] Thread pool bulkhead configuration ✅

---

### 7. Security in Java Microservices

#### 7.1 Authentication & Authorisation
- [ ] OAuth2 flows — Authorization Code, Client Credentials, PKCE
- [ ] OIDC — ID token, userinfo endpoint
- [ ] JWT — structure, signing (RS256 vs HS256), validation
- [ ] Token introspection vs local validation
- [ ] Service-to-service auth — client credentials flow, mTLS

#### 7.2 API Security
- [ ] Input validation — Bean Validation, custom validators
- [ ] SQL injection prevention — parameterised queries, JPA
- [ ] XSS prevention — output encoding
- [ ] CSRF — when relevant, Spring Security CSRF
- [ ] Secrets management — HashiCorp Vault integration, GCP Secret Manager
- [ ] Sensitive data in logs — masking PII, MDC

#### 7.3 Transport Security
- [ ] TLS configuration — cipher suites, protocol versions
- [ ] mTLS — mutual authentication, certificate management
- [ ] Certificate rotation — zero-downtime approaches

---

### 8. DevOps & CI/CD for Java Microservices

#### 8.1 Containerisation
- [ ] Docker best practices — multi-stage builds, minimal base images
- [ ] JVM in containers — container-aware JVM flags
- [ ] Image optimisation — layer caching, Jib
- [ ] Health checks in Docker/K8s — liveness vs readiness

#### 8.2 CI/CD Pipeline
- [ ] Pipeline stages — build, test, SAST, DAST, deploy
- [ ] Blue-Green deployment — zero downtime
- [ ] Canary deployment — gradual rollout, metrics-based promotion
- [ ] Feature flags — LaunchDarkly, homegrown
- [ ] Rollback strategies — automated vs manual triggers

#### 8.3 Code Quality
- [ ] Static analysis — SonarQube, SpotBugs
- [ ] Code coverage — JaCoCo, coverage thresholds
- [ ] Dependency scanning — OWASP dependency check
- [ ] API linting — OpenAPI spec validation

---

## 📊 Coverage Summary

| Section | Total Topics | Covered | Pending | % Done |
|---|---|---|---|---|
| **Core Java Internals** | 35 | 0 | 35 | 0% |
| **Java Concurrency** | 30 | 0 | 30 | 0% |
| **Design Patterns** | 25 | 0 | 25 | 0% |
| **Microservices Architecture** | 35 | 15 | 20 | 43% |
| **Spring Boot / Framework** | 30 | 2 | 28 | 7% |
| **Performance & Tuning** | 15 | 3 | 12 | 20% |
| **Security in Java MS** | 15 | 3 | 12 | 20% |
| **DevOps & CI/CD** | 15 | 2 | 13 | 13% |
| **Overall** | **200** | **25** | **175** | **12%** |

---

## 🎯 Priority for EM Role at FS GCC

Not all 200 topics are equally important for an EM role. Here's what matters most:

### 🔴 Must Know (EM will be tested on these):
```
Concurrency:
→ CompletableFuture + async patterns
→ Thread pool sizing (CPU vs IO bound)
→ Race conditions + deadlocks

Design Patterns:
→ Strategy, Observer, Builder, Factory, Adapter
→ Repository, Value Object, Aggregate (DDD)

Microservices:
→ DDD + Bounded Contexts
→ API design + idempotency
→ Service Mesh + mTLS
→ gRPC vs REST vs Kafka

Spring Boot:
→ @Transactional — isolation, propagation
→ N+1 problem
→ Actuator + health checks

Security:
→ OAuth2 / JWT flows end-to-end
→ mTLS service-to-service
→ Secrets management
```

### 🟡 Should Know (may come up):
```
→ GC algorithms (G1 vs ZGC basics)
→ JVM memory model basics
→ Virtual threads (Java 21)
→ CompletableFuture composition
→ HikariCP connection pool tuning
→ Blue-Green + Canary deployments
```

### 🟢 Nice to Know (unlikely for EM):
```
→ GC tuning flags in detail
→ JMH microbenchmarking
→ JPA dirty checking internals
→ AOP pointcut expressions
→ Specific JVM startup flags
```

---
