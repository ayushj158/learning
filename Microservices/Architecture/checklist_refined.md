# Microservices Architecture — Complete Interview Checklist
> Aligned to **microservices.io** · Chris Richardson pattern language  
> Tags: `NEW` = added from gap analysis | `EM` = Engineering Manager focus

---

## 01. Decomposition Patterns
*microservices.io: Decomposition*

### Decomposition Strategy
- [ ] Decompose by business capability — org-aligned services
- [ ] Decompose by subdomain — DDD strategic design
- [ ] Bounded contexts, ubiquitous language, context map
- [ ] Aggregates, value objects, domain events modelled
- [ ] Modular monolith as starting point before decomposition
- [ ] Strangler fig pattern for legacy monolith migration
- [ ] `EM` Decision framework: when to decompose vs consolidate

### Anti-Patterns to Avoid
- [ ] Database-per-table (too fine-grained)
- [ ] Chatty microservices (excessive inter-service calls)
- [ ] God services (monolith in disguise)
- [ ] Shared databases across services
- [ ] `NEW` Distributed monolith (microservices with tight coupling)

---

## 02. Communication Patterns
*microservices.io: Communication*

### Synchronous Communication
- [ ] REST vs gRPC — latency, contract, streaming tradeoffs
- [ ] API gateway — auth, rate limiting, routing, transformation
- [ ] Timeout defined for every outbound call
- [ ] Fan-out / fan-in (scatter-gather) pattern
- [ ] `EM` Synchronous vs asynchronous decision documented per use case

### Service Discovery
- [ ] `NEW` Client-side vs server-side discovery pattern chosen
- [ ] `NEW` Service registry (Consul, Eureka, Kubernetes DNS)
- [ ] `NEW` Self-registration vs third-party registration
- [ ] `NEW` Health-check aware routing (deregister on failure)

### Service Mesh
- [ ] Mesh platform chosen (Istio, Linkerd, etc.)
- [ ] mTLS enforced between services
- [ ] Traffic management — weighted routing, canary splits
- [ ] Retry / timeout policies at mesh level
- [ ] Observability integration with mesh (traces, metrics)

---

## 03. External API Patterns `NEW SECTION`
*microservices.io: External API*

### Client-Facing API Design
- [ ] `NEW` `EM` Backend for Frontend (BFF) — separate API per client type
- [ ] `NEW` API composition — aggregating data across services for client
- [ ] `NEW` GraphQL gateway for flexible client queries
- [ ] `NEW` `EM` Mobile vs web vs third-party API surface differences

### API Standards & Governance
- [ ] `EM` API versioning strategy — URL path, header, or content negotiation
- [ ] `EM` Deprecation policy and sunset dates communicated
- [ ] Backward compatibility maintained across releases
- [ ] `NEW` `EM` API design standards enforced (OpenAPI spec, linting)
- [ ] `NEW` `EM` Developer portal / API catalogue for internal consumers

---

## 04. Event-Driven Architecture
*microservices.io: Messaging*

### Core EDA Design
- [ ] Domain events identified and modelled per bounded context
- [ ] `EM` Event-driven vs request/response tradeoffs documented
- [ ] Choreography vs orchestration — decision per saga
- [ ] Pub/sub topology — topics, partitions, consumer groups designed

### Kafka / Broker Internals
- [ ] Message broker chosen and justified (Kafka, RabbitMQ, SNS/SQS)
- [ ] Kafka partitioning strategy — partition key for ordering guarantees
- [ ] Consumer group design and rebalancing implications
- [ ] Offset management — at-least-once vs exactly-once semantics
- [ ] Dead-letter queue (DLQ) and poison pill handling
- [ ] Retention policy and log compaction strategy

### Reliability & Schema Evolution
- [ ] Outbox pattern — reliable publishing, avoid dual write
- [ ] `NEW` Transactional inbox pattern — idempotent event processing
- [ ] `NEW` Polling publisher as simpler outbox alternative
- [ ] Event replay strategy and reprocessing design
- [ ] Event versioning — backward and forward compatibility
- [ ] `NEW` Schema registry (Avro, Protobuf, JSON Schema)
- [ ] Idempotent consumers — handle duplicate delivery safely

---

## 05. Data Management Patterns
*microservices.io: Data management*

### Database Strategy
- [ ] Database-per-service pattern enforced
- [ ] Data ownership — one service owns each dataset
- [ ] Polyglot persistence — relational, NoSQL, graph, search
- [ ] Read replicas for read-heavy and reporting workloads
- [ ] CDC (change data capture) for event-driven replication

### Consistency & Distributed Transactions
- [ ] CQRS — commands and queries separated, models optimised independently
- [ ] Event sourcing — immutable log, audit trail, replay capability
- [ ] Saga pattern — choreography vs orchestration per transaction
- [ ] Compensation logic and rollback scenarios fully defined
- [ ] Idempotency enforced on all write operations
- [ ] `EM` Eventual vs strong consistency tradeoffs documented per service

### Cross-Service Data Concerns
- [ ] `NEW` API composition for cross-service read queries (no joins across DBs)
- [ ] Data duplication strategy and sync mechanism
- [ ] Reconciliation jobs for consistency checks
- [ ] Stale data handling and conflict resolution strategy
- [ ] Reporting / analytics database separate from operational

---

## 06. Reliability Patterns
*microservices.io: Reliability*

### Fault Tolerance
- [ ] Circuit breaker (Resilience4j or equivalent)
- [ ] Bulkhead isolation — thread pool / semaphore per downstream
- [ ] Retry with exponential backoff and max retry cap
- [ ] Timeout on every outbound call
- [ ] Fallback strategy per critical dependency
- [ ] Graceful degradation — degraded mode planned per service

### Resilience Design
- [ ] `EM` Cascading failure scenarios identified and mitigated
- [ ] Idempotency on all retry-able operations
- [ ] `NEW` Rate limiting — protect services from traffic spikes
- [ ] `NEW` Backpressure handling in async pipelines
- [ ] `EM` Chaos engineering / resilience testing in staging

---

## 07. Observability Patterns
*microservices.io: Observability*

### Logging & Tracing
- [ ] Structured logging with consistent schema across all services
- [ ] Correlation IDs propagated across all service boundaries
- [ ] Distributed tracing — OpenTelemetry, Jaeger, or Zipkin
- [ ] Centralised log aggregation (ELK, Splunk, etc.)

### Metrics & Alerting
- [ ] RED method — Rate, Errors, Duration instrumented per service
- [ ] `NEW` USE method — Utilisation, Saturation, Errors for infrastructure
- [ ] Prometheus + Grafana (or equivalent) configured
- [ ] `EM` SLOs and SLIs defined and published per service
- [ ] `NEW` `EM` Error budgets tracked and reviewed with teams
- [ ] `EM` Alerting rules and on-call runbooks defined
- [ ] Liveness and readiness health checks per service
- [ ] Distributed health checks including dependencies

### Production Diagnostics
- [ ] `NEW` Exception aggregation (Sentry, Rollbar, etc.)
- [ ] `NEW` `EM` Incident playbooks and RCA templates defined
- [ ] `NEW` `EM` Post-mortem culture and blameless review process

---

## 08. Security Patterns
*microservices.io: Security*

### Authentication & Authorisation
- [ ] Centralised identity provider — OAuth2 / OIDC
- [ ] JWT or opaque token strategy with expiry policy
- [ ] mTLS for service-to-service authentication
- [ ] RBAC or ABAC authorisation model defined
- [ ] API gateway enforces auth on all inbound traffic
- [ ] `NEW` Zero-trust network model — never implicit trust

### Data & Secrets Security
- [ ] Encryption in transit (TLS everywhere)
- [ ] Encryption at rest
- [ ] `EM` PII handling, data residency, and masking strategy
- [ ] Secrets management — Vault, AWS Secrets Manager, etc.
- [ ] `EM` Audit logging for compliance (PCI-DSS, GDPR, SOX)
- [ ] Network policies — least-privilege ingress/egress
- [ ] `NEW` `EM` SAST / DAST security scanning in CI pipeline
- [ ] `NEW` Dependency vulnerability scanning (Snyk, Dependabot)

---

## 09. Deployment & Platform Patterns
*microservices.io: Deployment*

### Container & Orchestration
- [ ] Containerisation (Docker) and image registry strategy
- [ ] Kubernetes — namespaces, resource limits, pod disruption budgets
- [ ] `NEW` Horizontal pod autoscaling (HPA) and KEDA for event-driven scaling
- [ ] `NEW` `EM` Multi-region / multi-AZ strategy for HA

### CI/CD & Release
- [ ] `EM` CI/CD pipeline with automated quality gates
- [ ] `NEW` GitOps — ArgoCD or Flux for declarative deployments
- [ ] `NEW` Infrastructure as code (Terraform, Pulumi)
- [ ] Rolling deployments and zero-downtime strategy
- [ ] Canary deployments for high-risk changes
- [ ] `EM` Feature flags for decoupled releases
- [ ] `EM` Rollback procedures tested and documented

### Configuration Management
- [ ] Configuration externalised — not hardcoded in images
- [ ] Environment-specific config (ConfigMaps, Secrets)
- [ ] Hot reload / config refresh without restart (where needed)

---

## 10. Cross-Cutting Concerns & Developer Governance `NEW SECTION`
*microservices.io: Cross-cutting + Conway's Law*

### Service Chassis & Golden Path
- [ ] `NEW` `EM` Microservice chassis — shared library for logging, tracing, health, config
- [ ] `NEW` `EM` Service template / cookiecutter for new service bootstrapping
- [ ] `NEW` Sidecar pattern for cross-cutting concerns (logging, proxy, auth)
- [ ] `NEW` `EM` Tech radar maintained — approved vs trial vs hold technologies
- [ ] `NEW` `EM` API standards enforced across teams (linting, contracts)

### Team Structure & Ownership
- [ ] `EM` Conway's Law applied — team topology mirrors service boundaries
- [ ] `NEW` `EM` Team topologies: stream-aligned, platform, enabling, complicated-subsystem
- [ ] `EM` Clear service ownership — one team owns each service end-to-end
- [ ] `EM` On-call responsibilities and escalation paths defined
- [ ] `NEW` `EM` Inner dev loop optimised — local dev, fast feedback, test doubles

### Testing Strategy
- [ ] Unit tests per service (fast, isolated)
- [ ] Integration tests — with DBs and real dependencies
- [ ] Consumer-driven contract tests (Pact) for API compatibility
- [ ] `NEW` Component tests — service in isolation with stubs for deps
- [ ] `NEW` `EM` End-to-end tests minimised to critical paths only
- [ ] Load and performance testing before production
- [ ] `EM` Security scanning and dependency vulnerability checks in CI

---

## Summary

| # | Section | Pattern Reference |
|---|---------|-------------------|
| 01 | Decomposition patterns | microservices.io: Decomposition |
| 02 | Communication patterns | microservices.io: Communication |
| 03 | External API patterns *(new)* | microservices.io: External API |
| 04 | Event-driven architecture | microservices.io: Messaging |
| 05 | Data management patterns | microservices.io: Data management |
| 06 | Reliability patterns | microservices.io: Reliability |
| 07 | Observability patterns | microservices.io: Observability |
| 08 | Security patterns | microservices.io: Security |
| 09 | Deployment & platform patterns | microservices.io: Deployment |
| 10 | Cross-cutting concerns & governance *(new)* | microservices.io: Cross-cutting + Conway's Law |

> **Prep tip:** Filter on `EM` tags for leadership round preparation. Focus `NEW` tags first — these are your current gap areas.
