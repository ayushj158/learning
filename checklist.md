Excellent choice! For an **Engineering Manager at a Product-focused FS GCC**, System Design is often the **heaviest and most differentiating round** — they expect you to think like an Architect while demonstrating leadership judgment on tradeoffs.

Here's your complete prep map:

---

## 🏗️ System Design & Architecture — What to Prep

### 1. Foundations — Non-Negotiables
These are table-stakes before anything else:
- **CAP Theorem** — Consistency, Availability, Partition Tolerance — and when to pick which in FS context
- **Scalability patterns** — Horizontal vs Vertical scaling, Stateless services
- **Load balancing** — Round robin, Least connections, Consistent hashing
- **Caching strategies** — Write-through, Write-behind, Cache-aside (Redis/Memcached), TTL, cache invalidation
- **Database choices** — RDBMS vs NoSQL, when to use what, sharding, partitioning, replication

---

### 2. High-Level Design Patterns (FS-Heavy)
These are the most commonly asked design problems in FS GCC interviews:

| Design Problem | Key Concepts to Cover |
|---|---|
| **Payment Processing System** | Idempotency, Saga, exactly-once, ledger design |
| **Fraud Detection System** | Real-time stream processing, Kafka, ML model serving |
| **Trade Order Management** | Low latency, event sourcing, CQRS, FIX protocol awareness |
| **Notification Service** | Fan-out, pub/sub, retry, dead letter queues |
| **API Gateway / Rate Limiter** | Token bucket, leaky bucket, Redis counters |
| **Audit & Compliance System** | Immutable logs, event sourcing, data retention |

---

### 3. Distributed Systems Concepts
- **Consistency models** — Strong, Eventual, Read-your-writes
- **Distributed transactions** — 2PC (Two Phase Commit) vs Saga pattern
- **Leader election** — Zookeeper, Raft consensus
- **Message queues deep dive** — Kafka partitions, consumer groups, offset management, exactly-once semantics
- **Idempotency keys** — Critical in payments, how to design them

---

### 4. Data Architecture
- **Data modeling** — Normalisation vs Denormalisation tradeoffs
- **Time-series data** — Trade history, transaction history (InfluxDB, TimescaleDB)
- **Data warehousing concepts** — OLTP vs OLAP, Lambda vs Kappa architecture
- **Event Streaming pipelines** — Kafka → Flink/Spark → Data Lake patterns
- **Polyglot persistence** — Using multiple DB types in one system

---

### 5. Security Architecture (Must for FS GCCs)
- **Zero Trust architecture** — Never trust, always verify
- **mTLS** between microservices
- **OAuth2 / OIDC / JWT** flows — Authorization Code, Client Credentials
- **Secrets management** — HashiCorp Vault, AWS Secrets Manager
- **Data encryption** — At rest, in transit, field-level encryption for PII/PCI data

---

### 6. Reliability & Operations Design
- **SLA / SLO / SLI** — How to define and design for 99.99% uptime
- **Disaster Recovery** — RTO vs RPO, active-active vs active-passive
- **Chaos Engineering** — Principles, tools (Chaos Monkey), how you'd apply it
- **Zero-downtime deployments** — Blue-Green, Canary, Feature flags
- **Bulkhead & Isolation patterns** — Preventing cascade failures

---

### 7. Cloud-Native Architecture (Ties into Cloud topic)
- **Containerisation** — Docker, Kubernetes (pods, services, ingress, HPA)
- **Service Mesh** — Istio for traffic management, observability, security
- **Serverless patterns** — When to use Lambda/Functions vs containers
- **Multi-region design** — Data residency, latency, failover (critical for UK FS regulations)

---

## 🎯 How EM-Level System Design Differs from IC Level

This is important — at EM level they expect **more** than just drawing boxes:

| IC Level Expected | EM Level Expected |
|---|---|
| Correct components & flow | **Why** each component was chosen |
| Handle scale numbers | Tradeoffs between options considered |
| One working solution | Alternate approaches + risks of each |
| Technical correctness | **Cost, team capability, time-to-market** consideration |
| — | Regulatory & compliance constraints called out proactively |

---

## 🏦 Lloyds Experience — How to Leverage It

You have gold here. Structure every design answer using your real experience:

- **"At Lloyds, we designed X for Commercial Savings..."** → instantly credible
- Talk about **constraints you navigated** — legacy systems, compliance, uptime SLAs
- Mention **team decisions** — how you got 30 people aligned on an architectural direction
- Reference **incidents or tradeoffs** — where you chose pragmatism over perfection

---

## 📋 Suggested Prep Sequence (2 Weeks)

**Days 1–3** → CAP, Caching, DB choices, Load Balancing fundamentals

**Days 4–7** → Practice 2 design problems end-to-end (Payment System + Fraud Detection)

**Days 8–11** → Distributed systems (Kafka, Saga, CQRS, Event Sourcing)

**Days 12–14** → Security architecture + Reliability design + Mock design interview

---

## 🔁 Practice Framework for Every Design Question

Use this structure in the interview:

1. **Clarify requirements** — functional + non-functional (scale, latency, availability)
2. **Estimate scale** — users, TPS, data volume
3. **High-level design** — major components first
4. **Deep dive** — 2–3 critical components in detail
5. **Tradeoffs** — what you chose and what you gave up
6. **FS lens** — compliance, security, audit considerations

---

Ready to go deeper on any of these areas, or shall we move to the next topic? Also happy to run a **mock System Design question** on a payment or banking use case anytime! 🎯
