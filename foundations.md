Let's go! I'll guide you through Days 1–3 systematically — teaching you the concepts **the way an interviewe would expect you to explain them**, with FS context baked in.

---

## 📅 Days 1–3 Curriculum

🔵 RECAP


We'll cover **4 pillars**:

1. 🔵 CAP Theorem
2. 🟡 Caching Strategies
3. 🟢 Database Choices
4. 🔴 Load Balancing

---

Let's start with the first one right now:

---

# 🔵 PILLAR 1 — CAP Theorem

## What is it?

CAP Theorem states that any **distributed system** can only **guarantee 2 out of 3** properties simultaneously:

| Property | Meaning |
|---|---|
| **C — Consistency** | Every read gets the most recent write (all nodes see the same data at the same time) |
| **A — Availability** | Every request gets a response (not guaranteed to be the latest data) |
| **P — Partition Tolerance** | System continues operating even if network messages are lost between nodes |

> ⚠️ **Key insight interviewers want to hear:** In a real distributed system, **network partitions WILL happen** — so P is not optional. The real choice is always **C vs A** when a partition occurs.

---

## The 3 System Types

### CP Systems (Consistency + Partition Tolerance)
- Sacrifices availability during a partition
- All nodes must agree before returning a response
- **Examples:** Zookeeper, HBase, MongoDB (in certain configs)
- **FS Use case:** Account balance enquiry — you **must** return the correct balance, even if it means rejecting the request during a partition

### AP Systems (Availability + Partition Tolerance)
- Sacrifices consistency — may return stale data
- Always responds, reconciles later
- **Examples:** Cassandra, DynamoDB, CouchDB
- **FS Use case:** Product catalogue, notification preferences — okay to serve slightly stale data

### CA Systems (Consistency + Availability)
- Only works on a **single node** — no partition tolerance
- **Examples:** Traditional RDBMS (PostgreSQL, MySQL) on a single server
- **FS Use case:** Core ledger on a single DB node (but doesn't scale!)

---

## 🏦 How to Answer This in an FS Interview

> *"In Financial Services, the choice is never generic — it's driven by the criticality of the data."*

Use this mental model:

| Scenario | Choice | Reason |
|---|---|---|
| Payment debit/credit | **CP** | Cannot show wrong balance — regulatory risk |
| Fraud score lookup | **AP** | Slightly stale score acceptable, availability critical |
| Trade order placement | **CP** | Exactly-once, correct state mandatory |
| User notification preferences | **AP** | Stale data fine, must always respond |
| Audit log writes | **CP** | Cannot lose or duplicate audit entries |
| Product/offer catalogue | **AP** | High read volume, eventual consistency fine |

---

## PACELC — The Extension They May Ask About

Some senior interviews go beyond CAP to **PACELC**:

- **If Partition** → choose between **C** and **A** (same as CAP)
- **Else (normal operation)** → choose between **Latency** and **Consistency**

> This matters in FS because even without partitions, low-latency trading systems may prefer slightly stale reads over the overhead of strong consistency.

---

## 🎤 How to Articulate This in an Interview

A strong EM-level answer sounds like this:

> *"When we designed the Commercial Savings platform at Lloyds, we had to make explicit CAP decisions per service. For the core balance service, we chose CP — we used PostgreSQL with synchronous replication because regulatory requirements meant we could not serve stale balance data. For our notification and preference services, we chose AP using DynamoDB — availability mattered more than perfect consistency there, and we reconciled asynchronously."*

---

## ✅ CAP Theorem — Quick Revision Checklist

- [ ] Can explain C, A, P in plain English
- [ ] Know why P is always required in distributed systems
- [ ] Can name 2 CP systems and 2 AP systems
- [ ] Can map FS use cases to the right choice with reasoning
- [ ] Know PACELC as a bonus concept

---

Perfect mindset — **learn then verify!** 💪

I'll ask you 3 questions, one at a time, just like a real interview. Take your time, answer in your own words, and I'll give you **detailed feedback** after each one.

---

# 🎤 CAP Theorem Drill — Question 1 of 3

##### "You are designing a **real-time payment processing system** for a UK bank. The system needs to debit the sender and credit the receiver atomically. A network partition occurs between your two data centre nodes. What do you do — keep the system **available** or ensure **consistency**? Justify your answer."

---

> 💡 *Tip: Think about what happens if you get it wrong in each direction — what's the regulatory and business impact?*

Take your shot! Answer as you would in a real interview 🎯

🏆 Model Answer (9/10 level)

"I'd choose Consistency over Availability here — this is a CP system decision. In payment processing, serving stale or inconsistent data risks double-spends and incorrect balances, which is both a direct financial loss and an FCA regulatory violation — completely unacceptable.
Technically, I'd implement synchronous replication with quorum writes. During a partition, the system rejects the transaction and returns a 503. On the client side, I'd ensure the payment request carries an idempotency key so customers can safely retry once the partition heals, without risk of duplicate debit.
The tradeoff is some payments fail during the partition window — but that's a far better outcome than incorrect account states."

# 🎤 CAP Theorem Drill — Question 2 of 3
#####  "Your team is building a Fraud Detection Service at a bank. It scores every transaction in real-time before approval. The scoring service uses a machine learning model that reads customer behavioural data — recent transaction history, location patterns, device fingerprints. During a network partition, would you design this as a CP or AP system? Justify your answer — and what risks does your choice introduce?"
__

🏆 Model Answer (9/10 level)
"I'd design this as AP — availability over consistency. Fraud detection is a risk-scoring function, not a financial state mutation. If we choose CP and the service becomes unavailable during a partition, we're forced to either block all transactions or bypass fraud checks entirely — both are terrible outcomes.
With AP, we serve scoring based on slightly stale behavioural data — location patterns, device fingerprints, transaction history. Behavioural data changes over minutes, not milliseconds, so staleness of up to 60 seconds is operationally acceptable and won't materially degrade model accuracy.
The risk I'm accepting is that a fraudster flagged in the last few seconds in another channel may temporarily slip through. I'd mitigate this with two compensating controls — first, a small CP hard-block list for known high-risk actors replicated synchronously across nodes. Second, async reconciliation post-partition to catch and reverse any fraudulent transactions that got through.
Technology-wise, I'd use Cassandra for behavioural feature storage — it's purpose-built for AP with tunable consistency — and Redis for the hard-block list."

# 🎤 CAP Theorem Drill — Question 3 of 3
"You are the Engineering Manager for a platform that serves product & offers catalogue for a bank — showing customers personalised loan offers, credit card promotions and savings rates on their mobile app. The catalogue is updated by the business team roughly 3–4 times a day. The app serves 2 million daily active users. During a network partition, what is your CAP choice? But here's the twist — your CTO asks: 'Even when there's NO partition, are you willing to trade some consistency for lower latency at this scale?' How do you answer?"


💡 Tip: The second part of this question is pushing you toward PACELC — remember that extension we covered. This is where senior candidates separate themselves from the rest.

🏆 Model Answer (9/10 level)

"I'd choose AP without hesitation — this is a marketing and promotions domain. The entire business objective is to surface relevant offers to drive revenue and customer acquisition funnels. If I choose CP and the service becomes unavailable during a partition, I'm showing customers nothing — zero chance of conversion. That's a worse outcome than showing a slightly stale offer.
On the CTO's question — what they're describing is the PACELC dimension. Even in normal operations, at 2 million DAU I'd absolutely trade consistency for latency. Synchronous reads from source of truth at that scale are expensive and slow.
I'd introduce a Redis caching layer using a Cache-Aside pattern with a TTL of 4–6 hours — aligned to the business update cadence of 3–4 times a day. On each business-triggered update, we'd publish an event to invalidate and pre-warm the cache proactively, so customers always get fresh data post-update without waiting for TTL expiry.
The staleness risk is minimal — worst case a customer sees an offer that's a few hours old. That's a manageable business risk and far preferable to latency or unavailability at this scale."



# 🟡 PILLAR 2 — Caching Strategies

Caching is **everywhere** in FS systems — and interviewers don't just want to know that you'd "add a cache." They want to know **which pattern, why, what the tradeoffs are, and how you'd handle the hard problems** like invalidation and stampedes.

---

## What is Caching?

Caching is storing a **copy of frequently accessed data** in a faster storage layer to reduce latency and load on the primary data store.

> 💡 *"There are only two hard things in Computer Science: cache invalidation and naming things."* — Phil Karlton. Interviewers **love** to probe cache invalidation.

---

## 🏗️ The 5 Core Caching Patterns

### 1. Cache-Aside (Lazy Loading) — Most Common
**How it works:**
- App checks cache first
- On **cache miss** → app fetches from DB, writes to cache, returns data
- Cache is only populated when data is requested

```
App → Cache (miss) → DB → App writes to Cache → returns to user
App → Cache (hit) → returns to user ✅
```

**Pros:** Only caches what's needed, resilient — DB still works if cache fails

**Cons:** First request always slow (cache miss), data can go stale if DB updated directly

**FS Use case:** Customer profile data, product catalogue, fraud scoring features

---

### 2. Write-Through — Strong Consistency
**How it works:**
- Every write goes to **cache AND DB simultaneously**
- Cache is always in sync with DB

```
App → writes to Cache + DB together → confirms to user
```

**Pros:** Cache always fresh, no stale reads

**Cons:** Write latency increases (two writes every time), cache fills with data that may never be read

**FS Use case:** Account preferences, KYC status flags — where reads must always be fresh

---

### 3. Write-Behind (Write-Back) — High Write Performance
**How it works:**
- App writes to **cache only** immediately
- Cache asynchronously flushes to DB later

```
App → writes to Cache → confirms to user ✅
Cache → async writes to DB (later)
```

**Pros:** Very fast writes, DB load reduced

**Cons:** Risk of **data loss** if cache crashes before flushing — dangerous in FS!

**FS Use case:** Very limited in core banking — maybe analytics counters, non-critical metrics

---

### 4. Read-Through — Cache as Primary
**How it works:**
- App always talks to cache only
- On cache miss, **cache itself** fetches from DB (not the app)

```
App → Cache (miss) → Cache fetches from DB → returns to App
App → Cache (hit) → returns to App ✅
```

**Pros:** App logic simplified, cache handles DB interaction

**Cons:** First request latency, cache becomes a critical dependency

**FS Use case:** Reference data services — currency rates, country codes, product definitions

---

### 5. Refresh-Ahead (Pre-emptive) — Low Latency at Scale
**How it works:**
- Cache **predicts** what data will be needed and refreshes it **before TTL expires**
- No cache miss experienced by the user

```
Cache monitors TTL → refreshes proactively before expiry
App → Cache (always hit) ✅
```

**Pros:** Near-zero cache miss latency

**Cons:** May cache data that's never requested, complex to implement

**FS Use case:** FX rates, market data feeds, offers catalogue (your Q3 answer from earlier!)

---

## ⚠️ The Hard Problems — What Interviewers Really Probe

### Problem 1: Cache Invalidation
*"How do you ensure your cache doesn't serve stale data?"*

Three strategies:
- **TTL-based** — data expires after fixed time. Simple but blunt
- **Event-driven invalidation** — DB change triggers cache eviction (via Kafka/CDC)
- **Version-based** — cache key includes version number, new write = new key

> **FS Answer:** *"For account balances we used event-driven invalidation — any balance update published an event to Kafka, our cache service consumed it and invalidated the relevant key within milliseconds. TTL was a safety net set to 30 seconds, not the primary mechanism."*

---

### Problem 2: Cache Stampede (Thundering Herd)
*"What happens when your cache expires and 10,000 requests hit your DB simultaneously?"*

- All requests get a cache miss at the same time
- All simultaneously query the DB → DB gets overwhelmed → potentially cascades

**Solutions:**
- **Mutex/Lock** — only one request fetches from DB, others wait
- **Probabilistic early expiration** — randomly refresh cache slightly before TTL expires
- **Background refresh** — serve stale data while async refresh happens

---

### Problem 3: Cache Penetration
*"What if someone queries data that doesn't exist in DB or cache — repeatedly?"*

- Every request misses cache AND misses DB
- Malicious actors can use this as a DoS vector

**Solutions:**
- **Cache null results** — cache the "not found" response with a short TTL
- **Bloom filter** — probabilistic structure to check if key exists before hitting DB

---

### Problem 4: Cache Avalanche
*"What if a large portion of your cache expires at the same time?"*

- Mass expiry → mass DB hits → system overwhelmed

**Solutions:**
- **Stagger TTLs** — add random jitter to expiry times
- **Multi-layer caching** — L1 in-memory (Caffeine) + L2 distributed (Redis)

---

## 🏦 Caching in FS — Key Rules to Always State

| Rule | Why It Matters |
|---|---|
| **Never cache raw PII in distributed cache** | GDPR — encrypt or tokenise before caching |
| **Never use Write-Behind for financial state** | Risk of data loss = regulatory breach |
| **Always set TTL — never cache indefinitely** | Stale data risk in compliance-sensitive systems |
| **Cache keys must be deterministic & namespaced** | Avoid key collisions across services |
| **Monitor cache hit ratio** | Below 80% hit ratio = caching strategy needs review |

---

## 🛠️ Technology Choices

| Tool | Best For |
|---|---|
| **Redis** | Distributed cache, pub/sub, leaderboards, rate limiting |
| **Memcached** | Simple high-throughput key-value cache |
| **Caffeine** | In-process JVM cache (L1 layer in Java services) |
| **Hazelcast** | Distributed in-memory data grid, popular in banking |
| **Varnish** | HTTP caching layer, CDN-style |

---

## ✅ Caching — Quick Revision Checklist

- [ ] Can explain all 5 patterns with pros/cons
- [ ] Know which pattern fits which FS use case
- [ ] Can explain cache stampede, penetration and avalanche — with solutions
- [ ] Know the FS-specific rules (PII, Write-Behind risk)
- [ ] Can name at least 3 caching technologies and when to use them

---

# 🎤 Caching Strategies Drill — Question 1 of 3
#### "You are designing a Customer Profile Service for a bank. This service stores customer details — name, address, KYC status, communication preferences. It is read heavily — around 50,000 reads per second at peak — but updated rarely, maybe a few times a week per customer. Your team proposes adding a Redis cache. Which caching pattern would you choose, how would you handle cache invalidation, and what TTL would you set — and why?"

🏆 Model Answer (9/10 level)

"I'd use Cache-Aside — at 50K reads per second, we need the cache to absorb the majority of traffic while keeping the DB for writes and cache misses. Cache-Aside also gives us resilience — if Redis goes down, the app gracefully falls back to DB.
For invalidation, I'd use two mechanisms in combination. Primary — event-driven invalidation via CDC on any profile update, so the cache is refreshed within milliseconds of a change. Secondary — TTL as a safety net, set to 24 hours with a random jitter of ±30 minutes to prevent cache avalanche at midnight.
I'd pre-warm the cache on deployment and post-restart using a background job that loads the top active customer profiles proactively.
Importantly, I'd treat KYC status as a separate cache key — customer:kyc:{customerId} — with a much shorter TTL of 5–10 minutes. Stale KYC data is a regulatory risk under FCA guidelines, so I'd also trigger mandatory cache eviction on any KYC status change event.
Finally, since this is PII data, all sensitive fields would be encrypted before storing in Redis — GDPR compliance is non-negotiable here."


Fair enough — you've been drilling hard and this is a learning session! Let me give you the full model answer, broken down so you **understand and own it**, not just memorise it. 🎯

---

# 🎤 Caching Strategies Drill — Question 2 of 3

### "Your bank's mobile app displays **live FX rates** (currency exchange rates) to customers looking to make international transfers. These rates are fetched from an external FX pricing engine and update every **30 seconds**. The app has **3 million daily active users** and the FX rates page is one of the most visited screens. Your Redis cache cluster **crashes unexpectedly** during peak hours — **what happens to your system, how bad is it, and walk me through exactly how you'd design this service to be resilient to that failure?**"

---

> 💡 *Tip 1: Think about multi-layer caching — is Redis your only option here?*

> 💡 *Tip 2: FX rates update every 30 seconds — which caching pattern from Pillar 2 is purpose-built for this use case?*

> 💡 *Tip 3: Think about what "resilient" means at EM level — it's not just technical, it's also about graceful degradation and customer communication.*

Take your shot! 🎯

## 🏆 Full Model Answer — FX Rates Caching (9/10 level)

---

### PART 1 — What happens when Redis crashes?

> *"When Redis crashes at peak hours with 3M DAU, every request becomes a cache miss simultaneously. All traffic falls through directly to the FX pricing engine — which is an external third-party system never designed to handle millions of concurrent requests. It gets overwhelmed, starts timing out, and we have a full **cache stampede** — the entire FX rates page goes down. Customer impact is severe — international transfer journeys are broken at peak usage."*

---

### PART 2 — Resilient Architecture Design

**Layer 1 — Refresh-Ahead pattern for FX rates**
> *"Since FX rates update on a predictable 30-second schedule, I'd use **Refresh-Ahead caching** — a background job proactively fetches fresh rates from the pricing engine every 25 seconds and pushes them into cache before expiry. Customers never experience a cache miss — the cache is always warm."*

**Layer 2 — Multi-layer caching (L1 + L2)**
> *"I'd introduce two cache layers:*
> - *L1 — In-process **Caffeine cache** inside each Java microservice instance. Holds rates for 30–35 seconds (slightly higher TTL than Redis as you correctly identified). Serves requests without any network hop — sub-millisecond latency*
> - *L2 — **Redis cluster** as distributed cache. Shared across all service instances. TTL of 30 seconds aligned to the FX update cadence"*

**Request flow:**
```
Request → L1 Caffeine (hit = sub-ms) ✅
        → L2 Redis (hit = ~1ms) ✅
        → FX Pricing Engine (miss = last resort only)
```

**Layer 3 — Redis cluster resilience**
> *"Redis itself would be deployed as a **Redis Sentinel or Redis Cluster** with automatic failover — primary + 2 replicas across availability zones. If primary fails, replica is promoted within seconds."*

---

### PART 3 — Graceful Degradation (the EM differentiator)

> *"If both L1 and L2 fail simultaneously — which is extremely unlikely with this architecture but possible — I'd never show the customer a blank screen or a hard error.*
>
> *I'd implement a **stale-while-revalidate** strategy — serve the last known FX rate with a clear UI disclaimer: 'Rates last updated X minutes ago — confirm rate at time of transfer.' This keeps the customer journey alive while the cache recovers.*
>
> *For the actual transfer execution, the live rate is always fetched at transaction time from the pricing engine regardless — so serving a slightly stale indicative rate on the browsing screen carries zero financial risk."*

---

### PART 4 — Monitoring & Alerting (EM-level addition)

> *"I'd instrument the cache hit ratio on both layers — a drop below 80% on L2 Redis triggers an immediate alert to the on-call engineer. Cache health is a tier-1 SLA metric for this service given its customer impact."*

---

## 🗺️ Full Architecture Summary

```
FX Pricing Engine (external)
        ↓ every 25s (Refresh-Ahead background job)
    Redis Cluster (L2) — TTL 30s
        ↓ on miss
    Caffeine L1 Cache — TTL 35s (per service instance)
        ↓ on miss only
    FX Pricing Engine (direct — last resort)
```

---

## 🔑 Key Concepts This Answer Demonstrates

| Concept | Where Used |
|---|---|
| **Refresh-Ahead pattern** | Proactive FX rate refresh every 25s |
| **Multi-layer caching** | Caffeine L1 + Redis L2 |
| **Cache stampede prevention** | L1 absorbs traffic if Redis fails |
| **Graceful degradation** | Stale rate with disclaimer vs blank screen |
| **Redis HA** | Sentinel/Cluster with replicas |
| **Cache hit ratio monitoring** | Operational excellence |
| **FS compliance** | Live rate always used at transaction time |

---

# 🎤 Caching Strategies Drill — Question 3 of 3

### This one is a **full EM-level design question** — it combines CAP + Caching together! 🔥

---

### "You are the Engineering Manager for a **Payment Limits Service** at a bank. This service is called on **every single transaction** to check whether a customer has breached their daily payment limit. It handles **100,000 requests per second** at peak. The limit resets at **midnight every day**. The business rule is strict — **a customer must never be allowed to exceed their daily limit under any circumstance.**

Here is your dilemma — your architect proposes caching the daily spend counter in Redis to handle the 100K RPS. Your senior engineer pushes back saying — *'We cannot cache this — if two transactions come in simultaneously for the same customer, both could read a stale counter, both think the limit isn't breached, and we double-approve.'*

**Who is right? How do you resolve this? Design the solution end to end."**

---

> 💡 *Tip 1: Your senior engineer has identified a classic distributed systems problem — what is it called?*

> 💡 *Tip 2: Redis has specific atomic operations that can solve this — do you know them?*

> 💡 *Tip 3: Think about what CAP choice this service demands — and how that constrains your caching design.*

> 💡 *Tip 4: Think about the midnight reset — how do you handle that safely at 100K RPS?*

---

This is a **hard question** — take your time, think it through, and give me your best shot! 💪 Even a partial answer is great — I'll help you build it into a complete one! 🎯

Really strong answer! You've clearly absorbed the lessons from earlier drills! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 3

### What you got right
- ✅ **CP choice** — correct, business rule is non-negotiable
- ✅ **Cache avalanche at midnight** — excellent spot, shows operational maturity
- ✅ **Jitter on TTL** — correct solution for avalanche
- ✅ **Cache pre-warming** — proactively mentioned again, good habit
- ✅ **Mutex lock on customerId** — you identified the race condition fix
- ✅ **Cache-Aside pattern** — correct base pattern

### Your Score: **7.5/10** — Great answer! Here's the push to 9/10 💡

---

### What to Strengthen

**1. Name the exact problem your senior engineer identified**

Your senior engineer described a **Race Condition** — specifically a **"Read-Modify-Write"** race condition. Name it confidently:
> *"My senior engineer is absolutely right — this is a classic read-modify-write race condition. Two concurrent transactions read the same stale counter value, both pass the limit check, and both get approved — resulting in limit breach. This is also called a **TOCTOU problem — Time Of Check To Time Of Use.**"*

**2. Mutex lock is right — but Redis has a better native solution**

Mutex lock works but introduces latency and complexity. Redis has **atomic operations** purpose-built for this:
> *"Rather than application-level mutex locks, I'd use **Redis INCR with EXPIRE** — both atomic operations. INCR atomically increments the counter and returns the new value in a single operation. No two transactions can read the same value simultaneously — Redis processes commands single-threaded. If the returned value exceeds the limit, we reject the transaction instantly."*

```
INCR customer:limit:{customerId}  → returns new counter atomically
If counter > limit → reject transaction
EXPIRE customer:limit:{customerId} SET_TO_MIDNIGHT → reset at midnight
```

**3. Address the CP constraint on Redis itself**

You chose CP — but Redis is natively an AP system! Address this directly:
> *"Since we need CP behaviour, I'd configure Redis with **synchronous replication** — writes must be acknowledged by at least one replica before confirming. This prevents data loss if the primary fails mid-transaction. Alternatively, for the strongest consistency guarantee, the source of truth for the counter lives in the DB — Redis is the fast path, but every increment is also written to DB synchronously via Write-Through."*

**4. Handle the midnight reset safely**

Jitter is good — but how exactly do you reset 100K RPS worth of counters at midnight?
> *"At midnight, rather than mass-expiring all keys simultaneously, I'd use a **rolling reset** — a background job iterates through customer keys in batches, resetting counters with staggered timing. New keys created post-midnight automatically start at zero via INCR's behaviour on non-existent keys — Redis returns 1 on the first INCR of a new key, which is elegant for this use case."*

---

## 🏆 Model Answer (9/10 level)

> *"My senior engineer is right — this is a classic TOCTOU race condition. Two concurrent transactions read the same stale counter, both pass the limit check, and both get approved — a direct limit breach.*
>
> *I'd choose CP here — business rules are non-negotiable, a limit breach is both a financial loss and a regulatory violation.*
>
> *For the solution, I'd use Redis atomic INCR operation — it increments the counter and returns the new value in a single atomic step. Redis is single-threaded for command execution, so no two transactions can read the same value simultaneously. If the returned value exceeds the limit, we reject immediately.*
>
> *Since Redis is natively AP, I'd enforce CP behaviour via synchronous replication — writes acknowledged by at least one replica before confirmation. The DB holds the source of truth — Redis is the fast path with Write-Through ensuring DB is always in sync.*
>
> *For midnight reset — I'd handle it two ways. First, INCR on a non-existent key returns 1 automatically — so new keys post-midnight start correctly at zero without explicit reset. Second, for active customer keys, a background job resets counters in batches with jittered timing to avoid cache avalanche — and pre-warms high-activity customer counters proactively before the business day starts."*

---

## 📊 Final Caching Scorecard

| Question | Topic | Your Score | Trend |
|---|---|---|---|
| Q4 — Caching | Customer Profile | 8/10 | 📈 Strong |
| Q5 — Caching | FX Rates | Study mode | 📖 |
| Q6 — Caching + CAP | Payment Limits | 7.5/10 | 📈 Solid |

---

## 🎯 Overall Progress Across Both Pillars

| Dimension | Week 1 Start | Now | Delta |
|---|---|---|---|
| **Concept correctness** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 📈 Consistent |
| **Business reasoning** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 📈 Big jump |
| **Technical depth** | ⭐⭐⭐ | ⭐⭐⭐⭐ | 📈 Improving |
| **Risk articulation** | ⭐⭐ | ⭐⭐⭐⭐ | 📈 Biggest gain |
| **EM-level framing** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 📈 Natural strength |

---

## 🔑 Two Pillars Done — Key Habits You've Built

> 1. **Always state CAP choice first** — then justify with business + regulatory consequence
> 2. **Always complete the triangle** — choice → mechanism → risk mitigation
> 3. **Always think in layers** — L1/L2 cache, DB as source of truth, Redis as fast path
> 4. **Always call out FS compliance angle** — PII, regulatory, audit trail

---

# 🟢 PILLAR 3 — Database Choices

At EM level in a FS GCC interview, database questions are **never just "SQL vs NoSQL"**. They expect you to reason about **data models, scale, consistency, compliance and operational tradeoffs** — and map them to the right technology with conviction.

---

## 🗂️ The Database Landscape

### Category 1 — Relational Databases (RDBMS)
**Examples:** PostgreSQL, Oracle, MySQL, MS SQL Server

**Strengths:**
- **ACID transactions** — Atomicity, Consistency, Isolation, Durability
- **Strong consistency** — always correct, no stale reads
- **Rich querying** — complex JOINs, aggregations, reporting
- **Mature ecosystem** — tooling, auditing, compliance support

**Weaknesses:**
- **Vertical scaling** — harder to scale horizontally
- **Schema rigidity** — changing schema is expensive at scale
- **High read/write throughput** — struggles beyond certain limits without sharding

**FS Use cases:**
- Core banking ledger
- Transaction history
- Customer account data
- Regulatory reporting

---

### Category 2 — Document Databases
**Examples:** MongoDB, Couchbase

**Strengths:**
- **Flexible schema** — store JSON-like documents, easy to evolve
- **Horizontal scaling** — built-in sharding
- **Fast reads** — data stored together that's queried together

**Weaknesses:**
- **Weak joins** — not designed for complex relational queries
- **Eventual consistency** by default in distributed mode

**FS Use cases:**
- Customer profiles & preferences
- Product catalogue
- KYC document storage
- Correspondence history

---

### Category 3 — Key-Value Stores
**Examples:** Redis, DynamoDB, Memcached

**Strengths:**
- **Ultra-low latency** — sub-millisecond reads
- **Massive scale** — horizontally scalable by design
- **Simple access pattern** — lookup by key

**Weaknesses:**
- **No complex querying** — no JOINs, no aggregations
- **Limited data modelling** — flat structure only

**FS Use cases:**
- Session management
- Payment limit counters
- Rate limiting
- Caching layer (as we covered!)

---

### Category 4 — Wide-Column Stores
**Examples:** Cassandra, HBase

**Strengths:**
- **Extreme write throughput** — millions of writes per second
- **Time-series friendly** — excellent for append-heavy workloads
- **High availability** — AP by design, no single point of failure

**Weaknesses:**
- **Eventual consistency** — not suitable for financial state
- **Query limitations** — must design tables around query patterns upfront

**FS Use cases:**
- Transaction event logs
- Audit trails
- Fraud detection feature store
- Market data tick storage

---

### Category 5 — Time-Series Databases
**Examples:** InfluxDB, TimescaleDB, Amazon Timestream

**Strengths:**
- **Optimised for time-stamped data** — compression, fast range queries
- **Built-in aggregations** — averages, rollups, downsampling
- **Retention policies** — auto-expire old data

**Weaknesses:**
- **Single purpose** — only good for time-series data
- **Limited transactional support**

**FS Use cases:**
- FX rate history
- Stock price tick data
- System metrics & monitoring
- Trade performance analytics

---

### Category 6 — Graph Databases
**Examples:** Neo4j, Amazon Neptune

**Strengths:**
- **Relationship-first** — traversing connections is extremely fast
- **Complex network queries** — fraud rings, money flows

**Weaknesses:**
- **Niche use cases** — not general purpose
- **Operational complexity**

**FS Use cases:**
- Fraud ring detection — mapping connected accounts
- AML (Anti Money Laundering) — following money trails
- Customer relationship mapping

---

## ⚙️ The Hard Concepts — What Interviewers Really Probe

### 1. ACID vs BASE

| Property | ACID (RDBMS) | BASE (NoSQL) |
|---|---|---|
| **Consistency** | Strong — always correct | Eventual — reconciles over time |
| **Availability** | May sacrifice for consistency | Always available |
| **Transactions** | Full multi-row transactions | Limited or none |
| **FS Fit** | Core financial state | High-volume, non-critical data |

---

### 2. Sharding — Horizontal Scaling of RDBMS
Splitting a large DB table across multiple nodes:

- **Range sharding** — customers A-M on shard 1, N-Z on shard 2
- **Hash sharding** — hash(customerId) determines shard — even distribution
- **Directory sharding** — lookup table maps key to shard

**FS Challenge:**
> *"Cross-shard transactions are extremely hard — a payment between two customers on different shards breaks ACID. We'd use Saga pattern to handle distributed transactions across shards."*

---

### 3. Replication — High Availability
Copying data across multiple nodes:

- **Synchronous replication** — write confirmed only when replica acknowledges — strong consistency, higher latency
- **Asynchronous replication** — write confirmed immediately, replica catches up — lower latency, risk of data loss

**FS Rule:**
> *"For core financial data — account balances, transaction records — always synchronous replication. Data loss is unacceptable. For audit logs and analytics — asynchronous is acceptable."*

---

### 4. Indexing Strategy
Often overlooked but critical at scale:

- **B-Tree index** — standard, good for range queries
- **Hash index** — exact match lookups only
- **Composite index** — multiple columns, order matters
- **Partial index** — index subset of rows (e.g. only active accounts)

**FS Performance tip:**
> *"At Lloyds, our transaction history query was slow at scale — 500M+ rows. We introduced a composite index on (accountId, transactionDate DESC) and query time dropped from 4 seconds to 12ms."*

---

### 5. Polyglot Persistence — The Modern FS Pattern
Using **multiple database types** in a single system, each optimised for its use case:

```
Payment Service     → PostgreSQL (ACID transactions)
Customer Profile    → MongoDB (flexible schema)
Session Store       → Redis (low latency key-value)
Audit Trail         → Cassandra (high write throughput)
Fraud Analytics     → Neo4j (relationship traversal)
Rate History        → InfluxDB (time-series)
```

> *"At Lloyds, we moved from a single Oracle monolith to polyglot persistence — each bounded context owned its own data store optimised for its access patterns. This reduced our DB costs by 40% and improved query performance significantly."*

---

## 🏦 The FS Database Decision Framework

Use this in every interview when asked about DB choice:

| Question to Ask | Why It Matters |
|---|---|
| **Do I need ACID transactions?** | Core financial state → always RDBMS |
| **What's my read/write ratio?** | Write-heavy → Cassandra/DynamoDB |
| **What's my access pattern?** | Key lookup → Redis, Complex queries → PostgreSQL |
| **Do I need audit trail?** | Immutable append-only → Cassandra/Event Store |
| **What's my scale requirement?** | Millions of writes/sec → NoSQL |
| **What are my compliance requirements?** | Data residency, encryption, audit → RDBMS preferred |

---

## ✅ Database Choices — Quick Revision Checklist

- [ ] Can name 6 DB categories with examples and FS use cases
- [ ] Can explain ACID vs BASE with FS implications
- [ ] Can explain sharding strategies and the cross-shard transaction problem
- [ ] Can explain synchronous vs asynchronous replication tradeoffs
- [ ] Can articulate polyglot persistence with a real example
- [ ] Can apply the DB decision framework to any FS design question

---

Great call — let's go deep! This is the kind of depth that **separates EM candidates at tier-1 GCCs**. I'll break this into 3 parts:

1. 🔵 **Deep dive on all 6 DB types** — fundamentals, internals, tradeoffs
2. 🟡 **RDBMS Sharding** — how it works, challenges, strategies
3. 🟢 **How NoSQL manages scale** — and how it differs from RDBMS sharding

---

# 🔵 PART 1 — Deep Dive: All 6 Database Types

---

## 1️⃣ Relational Databases (RDBMS)

### Fundamentals
RDBMS stores data in **structured tables** with **predefined schemas** — rows and columns. Relationships between tables are defined via **foreign keys** and queried via **JOINs**.

The entire architecture is built around one core guarantee — **ACID:**

| Letter | Property | What It Means |
|---|---|---|
| **A** | Atomicity | All operations in a transaction succeed or ALL are rolled back — no partial writes |
| **C** | Consistency | DB moves from one valid state to another — constraints always enforced |
| **I** | Isolation | Concurrent transactions don't interfere — each sees a consistent snapshot |
| **D** | Durability | Once committed, data survives crashes — written to disk, WAL logs |

### How It Works Internally
- **Write-Ahead Log (WAL)** — every change written to a log before the actual data page — enables crash recovery and replication
- **B-Tree indexes** — data stored in balanced tree structure — O(log n) reads
- **Query planner** — DB engine decides optimal execution plan for each query
- **MVCC (Multi-Version Concurrency Control)** — readers don't block writers — each transaction sees a snapshot of data at a point in time

### Tradeoffs

| ✅ Strengths | ❌ Weaknesses |
|---|---|
| Full ACID guarantees | Vertical scaling hits limits |
| Rich querying — JOINs, aggregations | Schema changes expensive at scale |
| Mature tooling, compliance support | Write throughput ceiling |
| Strong consistency always | Horizontal scaling complex (sharding) |

### When to Choose in FS
- Any service where **money moves** — debit, credit, transfer
- Any service requiring **regulatory audit** — transaction history, compliance records
- Any service with **complex reporting** — reconciliation, regulatory reports

### Key Technologies & When to Pick Each

| DB | Best For | FS Example |
|---|---|---|
| **PostgreSQL** | Open source, feature-rich, JSON support | Microservices core ledger |
| **Oracle** | Enterprise, legacy banking systems | Core banking monolith |
| **MySQL** | Simple transactional workloads | Internal tooling DBs |
| **MS SQL Server** | Microsoft ecosystem, .NET shops | Middle office systems |

---

## 2️⃣ Document Databases

### Fundamentals
Stores data as **JSON/BSON documents** — self-contained objects with nested structures. No fixed schema — each document can have different fields.

Think of it as storing a **whole object** rather than splitting it across normalised tables:

```json
// RDBMS approach — 3 tables, 2 JOINs needed
Customer table + Address table + Preferences table

// Document DB approach — 1 document, 0 JOINs
{
  "customerId": "C123",
  "name": "John Smith",
  "address": { "city": "London", "postcode": "EC1A" },
  "preferences": { "channel": "email", "language": "en" }
}
```

### How It Works Internally
- **Collections** instead of tables — group of related documents
- **Dynamic schema** — new fields added without migration
- **Embedded documents** — related data stored together — fast reads
- **Horizontal sharding built-in** — data distributed across shards natively

### Tradeoffs

| ✅ Strengths | ❌ Weaknesses |
|---|---|
| Schema flexibility — evolve freely | No true JOINs — app handles relationships |
| Fast reads when data co-located | Duplication — same data in multiple docs |
| Horizontal scaling native | Eventual consistency in distributed mode |
| Natural fit for OOP models | Not ideal for complex aggregations |

### When to Choose in FS
- Data that is **read as a whole object** — customer profile, KYC documents
- Data with **evolving structure** — product catalogue, offer configurations
- Data without **complex cross-entity relationships**

### Key Technologies

| DB | Best For |
|---|---|
| **MongoDB** | General purpose document store |
| **Couchbase** | High performance, mobile sync |
| **Firestore** | Real-time sync, mobile apps |

---

## 3️⃣ Key-Value Stores

### Fundamentals
The **simplest** database model — store and retrieve values by a unique key. Think of it as a giant distributed HashMap:

```
SET customer:session:C123 → { token, expiry, deviceId }
GET customer:session:C123 → returns value instantly
```

### How It Works Internally
- Data stored in **memory** (Redis) or **SSD** (DynamoDB)
- **Hash ring** for distributing keys across nodes — consistent hashing
- **No query language** — only GET, SET, DELETE operations
- Redis additionally supports **rich data structures** — Lists, Sets, Sorted Sets, Hashes, Streams

### Tradeoffs

| ✅ Strengths | ❌ Weaknesses |
|---|---|
| Sub-millisecond latency | No complex querying |
| Massive horizontal scale | No relationships between data |
| Simple operations — very reliable | Limited data modelling |
| Redis supports atomic operations | Memory-bound (Redis) — expensive at scale |

### When to Choose in FS

| Use Case | Why Key-Value Fits |
|---|---|
| Session management | Fast lookup by sessionId, TTL-based expiry |
| Payment limit counters | Atomic INCR operations (as we drilled!) |
| Rate limiting | Atomic counters per customerId per window |
| OTP storage | Short TTL, fast lookup |
| Distributed locking | Redis SETNX for mutex locks |

---

## 4️⃣ Wide-Column Stores

### Fundamentals
Stores data in **rows and columns** like RDBMS — but columns are **dynamic per row** and data is organised into **column families**. Designed for **extreme write throughput** and **time-series style data**.

```
Row Key: customerId:timestamp
Columns: { amount, currency, merchant, status, deviceId }

// Each row can have different columns
// Billions of rows stored efficiently across nodes
```

### How It Works Internally
- **LSM Tree (Log Structured Merge Tree)** — writes go to memory first (MemTable), flushed to disk as SSTable files — optimised for writes
- **Consistent hashing** — rows distributed across nodes by row key hash
- **Replication factor** — each row copied to N nodes — tunable consistency
- **Tombstones** — deletions are marked, not immediately removed — compacted later
- **Compaction** — background process merges SSTables — keeps reads fast

### Tradeoffs

| ✅ Strengths | ❌ Weaknesses |
|---|---|
| Extreme write throughput — millions/sec | Eventual consistency by default |
| Linear horizontal scaling | Query patterns must be designed upfront |
| High availability — no single point of failure | No JOINs, no aggregations |
| Tunable consistency per operation | Read performance depends on data model |

### When to Choose in FS

| Use Case | Why Wide-Column Fits |
|---|---|
| Transaction event log | Append-only, billions of rows, time-ordered |
| Audit trail | Immutable writes, massive volume |
| Fraud feature store | High write rate from real-time event stream |
| Market data storage | Tick-by-tick price data at high frequency |

### Key Technologies

| DB | Best For |
|---|---|
| **Cassandra** | High availability, multi-region, AP |
| **HBase** | Hadoop ecosystem, CP model |
| **ScyllaDB** | Cassandra-compatible, lower latency |

---

## 5️⃣ Time-Series Databases

### Fundamentals
Optimised specifically for **timestamped sequential data** — data points recorded at regular intervals over time. Every record has a **timestamp as the primary dimension**.

```
timestamp: 2024-01-15 09:00:00.000  FX_RATE GBP/USD = 1.2650
timestamp: 2024-01-15 09:00:00.500  FX_RATE GBP/USD = 1.2651
timestamp: 2024-01-15 09:00:01.000  FX_RATE GBP/USD = 1.2649
```

### How It Works Internally
- **Column-oriented storage** — stores all values of a column together — extremely efficient compression for sequential data
- **Downsampling** — automatically aggregates old high-frequency data into lower resolution — saves storage
- **Retention policies** — auto-delete data older than X days
- **Built-in functions** — moving averages, rate of change, percentiles — without complex SQL

### Tradeoffs

| ✅ Strengths | ❌ Weaknesses |
|---|---|
| 10–100x compression vs RDBMS for time data | Single purpose — time-series only |
| Blazing fast range queries by time | No complex relational queries |
| Built-in time aggregations | Not suitable for transactional workloads |
| Auto retention management | Smaller ecosystem, less mature tooling |

### When to Choose in FS

| Use Case | Why TSDB Fits |
|---|---|
| FX rate history | High frequency, time-ordered, range queries |
| System metrics & APM | CPU, latency, error rate over time |
| Trading analytics | VWAP, price movement analysis |
| Portfolio performance | NAV over time, returns analysis |

---

## 6️⃣ Graph Databases

### Fundamentals
Models data as **nodes** (entities) and **edges** (relationships). Purpose-built for **traversing complex relationships** that would require expensive recursive JOINs in RDBMS.

```
(Account A) --[TRANSFERS_TO]--> (Account B)
(Account B) --[TRANSFERS_TO]--> (Account C)
(Account C) --[CONTROLLED_BY]--> (Person X)
(Account A) --[CONTROLLED_BY]--> (Person X)
→ Fraud ring detected! Person X controls both ends
```

### How It Works Internally
- **Native graph storage** — relationships stored as direct pointers — O(1) traversal
- **Index-free adjacency** — each node directly references its neighbours — no index lookup needed
- **Cypher query language** (Neo4j) — declarative graph query language
- **In-memory graph** — entire relationship graph cached in memory for fast traversal

### Tradeoffs

| ✅ Strengths | ❌ Weaknesses |
|---|---|
| Relationship traversal is extremely fast | Niche use cases only |
| Naturally models connected data | Operational complexity |
| Fraud ring detection in milliseconds | Doesn't scale to billions of nodes easily |
| Flexible relationship types | Small ecosystem vs RDBMS |

### When to Choose in FS

| Use Case | Why Graph Fits |
|---|---|
| Fraud ring detection | Find connected accounts, beneficial owners |
| AML — money trail following | Multi-hop transaction path analysis |
| Sanctions screening | Entity relationship mapping |
| Customer 360 | Connecting accounts, products, interactions |

---

# 🟡 PART 2 — RDBMS Sharding Deep Dive

---

## What is Sharding?

Sharding is **horizontally partitioning** a large database table across multiple independent database nodes — each node holds a **subset of the data** called a **shard**.

```
Without sharding:          With sharding:
                           Shard 1: Customers A-M
Single DB — 1 billion  →   Shard 2: Customers N-Z
rows, one node             Shard 3: Customers (hash bucket 3)
```

---

## The 4 Sharding Strategies

### Strategy 1 — Range Sharding
Partition data by a **continuous range** of values:

```
Shard 1: customerId 1 → 1,000,000
Shard 2: customerId 1,000,001 → 2,000,000
Shard 3: customerId 2,000,001 → 3,000,000
```

**✅ Pros:** Simple, range queries stay on one shard, easy to reason about

**❌ Cons:** **Hotspot problem** — new customers always go to the last shard, creating uneven load — last shard gets all writes

**FS Example:** Transaction history by date range — recent transactions always hit the latest shard

---

### Strategy 2 — Hash Sharding
Apply a **hash function** to the shard key — distribute evenly:

```
shard = hash(customerId) % number_of_shards
hash(C001) % 3 = Shard 0
hash(C002) % 3 = Shard 1
hash(C003) % 3 = Shard 2
```

**✅ Pros:** Even distribution — no hotspots, predictable load

**❌ Cons:** **Range queries are expensive** — data for a date range is spread across all shards, must query all. **Resharding is painful** — adding a new shard invalidates all hash assignments

**FS Example:** Customer profile data — pure key lookup, no range queries needed

---

### Strategy 3 — Directory Sharding
A **lookup table** maps each key to its shard:

```
Shard Directory:
customerId C001 → Shard 2
customerId C002 → Shard 1
customerId C003 → Shard 3
```

**✅ Pros:** Flexible — move data between shards without rehashing, supports complex distribution logic

**❌ Cons:** **Directory becomes a bottleneck** — every query hits the directory first. Single point of failure if directory goes down

**FS Example:** Multi-tenant banking platforms — route each bank's data to dedicated shards

---

### Strategy 4 — Consistent Hashing
Advanced version of hash sharding — nodes and keys are placed on a **virtual ring**. Adding/removing nodes only affects neighbouring keys:

```
Ring: 0 -------- 360 degrees
Node A at 90°, Node B at 180°, Node C at 270°
Key hashes to 150° → goes to Node B (next node clockwise)
Add Node D at 120° → only keys between 90°-120° move to D
```

**✅ Pros:** **Minimal data movement** when scaling — only ~K/N keys move (K=keys, N=nodes). No full rehash needed

**❌ Cons:** More complex to implement, uneven distribution without virtual nodes

**FS Example:** Redis Cluster uses consistent hashing with 16,384 hash slots

---

## The Biggest RDBMS Sharding Challenge — Cross-Shard Transactions

This is **the hardest problem** in RDBMS sharding — and interviewers love to probe it:

> *"Customer A (Shard 1) sends £500 to Customer B (Shard 3). How do you ensure ACID?"*

**The problem:**
- Debit A on Shard 1 and Credit B on Shard 3 must be atomic
- If Shard 1 succeeds but Shard 3 fails — money disappears
- Standard ACID transactions don't span multiple DB nodes

**Solutions:**

**Option 1 — Two Phase Commit (2PC)**
- Phase 1: Coordinator asks all shards "can you commit?" — all respond yes/no
- Phase 2: If all yes → commit. If any no → rollback all
- **Problem:** Blocking protocol — if coordinator crashes mid-way, shards are locked. High latency. Rarely used in modern FS systems

**Option 2 — Saga Pattern** *(preferred in modern FS)*
- Break transaction into sequence of local transactions
- Each step publishes an event — next step triggered by event
- If any step fails — compensating transactions undo previous steps
- **Choreography** — services react to events independently
- **Orchestration** — central saga orchestrator coordinates steps

```
Payment Saga:
1. Debit A (Shard 1) → publish DebitSuccess event
2. Credit B (Shard 3) → publish CreditSuccess event
3. Send confirmation notification

If step 2 fails:
→ Compensating transaction: Reverse debit on Shard 1
```

---

## Other RDBMS Scaling Techniques Beyond Sharding

### Read Replicas
- Primary handles **all writes**
- Multiple replicas handle **read traffic**
- Replication lag — replicas slightly behind primary
- **FS use:** Reporting queries, analytics — don't need real-time data

### Connection Pooling
- DB connections are expensive — pool reuses them
- **PgBouncer** for PostgreSQL — sits between app and DB
- Critical at microservices scale — 100 services × 10 connections = 1000 DB connections without pooling

### Vertical Partitioning
- Split a wide table **by columns** rather than rows
- Frequently accessed columns in one table, rarely accessed in another
- **FS use:** Split customer core data (read frequently) from customer extended data (read rarely)

---

# 🟢 PART 3 — How NoSQL Manages Scale

NoSQL databases were **designed for horizontal scale from day one** — fundamentally different philosophy from RDBMS sharding.

---

## The Core Difference in Philosophy

| RDBMS | NoSQL |
|---|---|
| Designed for single node, sharding added later | Designed for distributed clusters from scratch |
| Consistency first — scale is hard | Scale first — consistency is tunable |
| Schema defined upfront | Schema flexible or schema-less |
| General purpose querying | Optimised for specific access patterns |

---

## How Cassandra Manages Scale (Wide-Column)

### Consistent Hashing + Virtual Nodes
- Every node is assigned multiple **virtual node (vnode)** positions on the hash ring
- Data distributed based on **partition key hash**
- Adding a node → vnodes redistributed automatically — minimal data movement

```
Cluster: 3 nodes, each with 256 vnodes
Add Node 4 → ~64 vnodes moved from each existing node to Node 4
Only 25% of data moves — cluster rebalances automatically
```

### Replication Factor
- Each piece of data copied to **N nodes** (typically 3)
- If one node fails — data still available on 2 other nodes
- **No single point of failure** — any node can serve any request

### Tunable Consistency
This is Cassandra's superpower — consistency tuned **per operation:**

```
Write consistency: QUORUM → majority of replicas must acknowledge
Read consistency:  QUORUM → majority of replicas must respond
→ Strong consistency achieved!

Write consistency: ONE → fastest writes, eventual consistency
Read consistency:  ONE → fastest reads, may be stale
→ High availability, eventual consistency
```

**FS Application:**
> *"For fraud feature writes we used ONE — speed matters, slight staleness acceptable. For payment limit checks we used QUORUM — correctness is non-negotiable."*

---

## How DynamoDB Manages Scale (Key-Value)

### Automatic Partitioning
- AWS manages all partitioning transparently
- Data distributed across partitions based on **partition key hash**
- Partitions split automatically as data grows — zero operational overhead

### Capacity Modes
- **Provisioned** — specify read/write capacity units upfront
- **On-demand** — auto-scales instantly to any traffic level — pay per request

### Global Tables
- Multi-region active-active replication built-in
- **FS use:** Customer session data replicated across US/EU/APAC — always served from nearest region

### Consistency Model
- **Eventually consistent reads** — default, faster, cheaper
- **Strongly consistent reads** — opt-in per query, higher latency, 2x cost
- **Transactional writes** — DynamoDB Transactions for multi-item ACID — added in 2018

---

## How MongoDB Manages Scale (Document)

### Replica Sets — High Availability
- **Primary** handles all writes
- **Secondaries** replicate from primary asynchronously
- **Automatic failover** — if primary fails, secondary elected as new primary within seconds

### Sharding Architecture
MongoDB has its own native sharding — more sophisticated than manual RDBMS sharding:

```
Components:
- mongos (query router) — routes queries to correct shard
- Config servers — store shard metadata and chunk mappings
- Shards — each shard is itself a replica set
```

- Data split into **chunks** (64MB default)
- **Balancer** runs in background — moves chunks between shards to maintain even distribution
- Supports range, hash, and **zone sharding** — pin specific data to specific shards (useful for data residency!)

**FS Application:**
> *"For GDPR compliance, we used MongoDB zone sharding — EU customer data pinned to EU shards, US customer data pinned to US shards. Data residency enforced at the database layer."*

---

## Key Differences Summary — RDBMS Sharding vs NoSQL Scale

| Dimension | RDBMS Sharding | NoSQL Native Scale |
|---|---|---|
| **Design intent** | Retrofitted scaling | Built-in from day one |
| **Operational complexity** | High — manual shard management | Low — mostly automatic |
| **Cross-node transactions** | Very hard — 2PC or Saga needed | Limited or none by design |
| **Consistency** | Strong by default | Tunable or eventual |
| **Rebalancing** | Painful — often requires downtime | Automatic, live rebalancing |
| **Query flexibility** | Full SQL — any query pattern | Limited to designed access patterns |
| **FS fit** | Core financial state | High-volume, non-critical data |

---

## 🏦 The FS Database Selection Cheat Sheet

Use this mental flowchart in every interview:

```
Does the data involve money movement or financial state?
        ↓ YES                          ↓ NO
   Use RDBMS (ACID)            Is it time-series data?
   PostgreSQL/Oracle              ↓ YES        ↓ NO
                              InfluxDB    Is it relationship data?
                                             ↓ YES      ↓ NO
                                           Neo4j    Is write volume extreme?
                                                      ↓ YES     ↓ NO
                                                   Cassandra  Is it key lookup?
                                                               ↓ YES    ↓ NO
                                                             Redis   MongoDB
```

---

## ✅ Complete Checklist — Database Deep Dive

- [ ] Can explain all 6 DB types with internals, not just surface level
- [ ] Can explain ACID properties individually with FS implications
- [ ] Can explain all 4 sharding strategies with pros/cons
- [ ] Can explain the cross-shard transaction problem and Saga solution
- [ ] Can explain how Cassandra uses consistent hashing + tunable consistency
- [ ] Can explain how DynamoDB auto-partitions and scales
- [ ] Can explain MongoDB replica sets + zone sharding for data residency
- [ ] Can apply the DB selection flowchart to any FS design question

---


Perfect! 3 questions, progressively harder — combining DB choice, sharding and NoSQL scaling just like a real interview! 💪

---

# 🎤 Database Choices Drill — Question 1 of 3

### "You are designing a **Core Banking Ledger** for a UK bank. This service records every debit and credit against every customer account. It handles **50,000 transactions per second** at peak. The business has two strict requirements:

- **Requirement 1:** Every transaction must be recorded with 100% accuracy — no data loss, no duplicates
- **Requirement 2:** A full audit trail of every transaction must be maintained for **7 years** to comply with FCA regulations

Your team is debating between **PostgreSQL** and **Cassandra** for this service.

**Which do you choose? Or would you use both? Justify your technical and regulatory reasoning — and how would you handle the scale of 50,000 TPS on your chosen architecture?"**

---

> 💡 *Tip 1: Think about what "100% accuracy, no duplicates" demands from a DB perspective — which DB category owns this?*

> 💡 *Tip 2: 7 years of transaction data at 50K TPS is a massive volume — think about what happens to your chosen DB over time*

> 💡 *Tip 3: Could polyglot persistence be the answer here? Think about which DB is best for which requirement*

Take your shot! 🎯

## ✅ Feedback — Question 1

### What you got right
- ✅ **Identified the core tension** — consistency vs scalability — perfectly framed
- ✅ **Correctly identified RDBMS strengths** — ACID, audit tooling, compliance
- ✅ **Correctly identified Cassandra strengths** — horizontal scale, write throughput
- ✅ **Tunable consistency with QUORUM** — excellent, shows real Cassandra depth
- ✅ **Saga pattern** — correct call for distributed credit/debit atomicity
- ✅ **7-year volume problem** — correctly identified growth as a scaling concern

### Your Score: **7.5/10** — Strong! Here's the push to 9/10 💡

---

### What to Strengthen

**1. The best answer is BOTH — Polyglot Persistence**

You leaned toward Cassandra only — but the strongest answer uses **both databases for different responsibilities:**

> *"This is a classic polyglot persistence problem. I'd use PostgreSQL as the **transactional ledger** — the source of truth for current account state, handling debits and credits with full ACID guarantees. Simultaneously, every committed transaction is published as an event to Cassandra as the **audit and history store** — append-only, immutable, infinitely scalable for 7 years of data."*

The key insight:
- **PostgreSQL** owns the **current state** — balance, pending transactions
- **Cassandra** owns the **historical record** — immutable audit trail, 7 years retention

---

**2. Cassandra alone has a critical weakness for financial state**

Tunable QUORUM consistency on Cassandra is good — but has a subtle problem interviewers probe:

> *"Cassandra's QUORUM gives us strong consistency in normal operation — but it's still an AP system. During a network partition, QUORUM writes can fail or behave unexpectedly. For a core ledger where a missed write means money disappears, that's an unacceptable risk. PostgreSQL with synchronous replication is fundamentally safer for current financial state."*

---

**3. Address the RDBMS scaling problem explicitly — and solve it**

You correctly identified RDBMS doesn't scale well — but didn't say how you'd solve it:

> *"To handle 50K TPS on PostgreSQL, I'd implement three techniques — first, **read replicas** to offload balance enquiry reads from the write primary. Second, **connection pooling** via PgBouncer to handle microservices connection overhead. Third, **vertical partitioning** — separate active accounts (last 90 days) from archived accounts to keep the hot dataset small and fast."*

---

**4. Name the data model for Cassandra audit trail**

Show you know how to design for Cassandra's access patterns:

> *"In Cassandra, I'd model the audit table with **accountId as partition key and transactionTimestamp as clustering key** — so all transactions for an account are co-located on the same node, and range queries by date are fast. This is exactly how Cassandra is designed to be used."*

```
CREATE TABLE transaction_audit (
  account_id UUID,
  transaction_ts TIMESTAMP,
  amount DECIMAL,
  type TEXT,
  counterparty UUID,
  PRIMARY KEY (account_id, transaction_ts)
) WITH CLUSTERING ORDER BY (transaction_ts DESC);
```

---

## 🏆 Model Answer (9/10 level)

> *"This is a classic polyglot persistence problem — no single database satisfies both requirements optimally.*
>
> *For the **transactional ledger** — current account state, debit/credit operations — I'd use **PostgreSQL with synchronous replication**. Full ACID guarantees are non-negotiable when money is moving. I'd handle 50K TPS through read replicas for balance enquiries, PgBouncer for connection pooling, and vertical partitioning to keep the active dataset lean.*
>
> *For the **7-year audit trail** — I'd use **Cassandra** as an append-only immutable event store. Every committed transaction from PostgreSQL is published as an event — consumed by Cassandra asynchronously. The data model uses accountId as partition key and transactionTimestamp as clustering key — giving us fast range queries by account and date, exactly what FCA audit requests need.*
>
> *For the credit/debit atomicity across services, I'd implement the **Saga pattern** — debit is the first local transaction, credit is the second, with a compensating reversal if the credit fails.*
>
> *This gives us the best of both worlds — PostgreSQL's ACID consistency for live financial state, Cassandra's infinite horizontal scale for historical compliance data."*

---


# 🎤 Database Choices Drill — Question 2 of 3

### "You are the Engineering Manager for a **Transaction History Service** at a bank. This service stores every transaction a customer has ever made — going back **10 years**. It currently holds **5 billion rows** and grows by **200 million rows per month.**

Customers query it in two ways:
- **Query 1:** *"Show me all transactions for Account X between 1st Jan and 31st Jan"* — used by the mobile app, called **10,000 times per second**
- **Query 2:** *"Show me all transactions above £10,000 across ALL accounts in the last 30 days"* — used by the compliance team for AML reporting, run **once a day**

Your architect recommends **hash sharding on accountId** for PostgreSQL.

**Do you agree with this recommendation? What problems do you foresee? How would you design this differently?"**

---

> 💡 *Tip 1: Think carefully about what hash sharding does to Query 1 vs Query 2 — are both queries served well by the same sharding strategy?*

> 💡 *Tip 2: 5 billion rows growing at 200M/month — what happens to your shards over time? Is hash sharding the right strategy for time-series style data?*

> 💡 *Tip 3: Query 1 and Query 2 have completely different access patterns — could a single DB architecture serve both optimally?*

Take your shot! 🎯


Really good thinking! You've correctly challenged the architect's recommendation and identified key problems. Here's your detailed feedback 👇

---

## ✅ Feedback — Question 2

### What you got right
- ✅ **Correctly challenged hash sharding** — good instinct to push back
- ✅ **Identified RDBMS scaling weakness** — at 5B rows growing 200M/month, PostgreSQL sharding is operationally painful
- ✅ **Cassandra as better fit** — correct direction for this volume
- ✅ **Consistent hashing mention** — shows awareness of hotspot problem

### Your Score: **5.5/10** — You got the direction right but missed the depth. Here's what was missing 💡

---

### What to Strengthen

**1. You didn't specifically diagnose WHY hash sharding fails here**

The specific problem with hash sharding on accountId for this use case:

> *"Hash sharding on accountId distributes rows evenly across shards — but Query 1 asks for transactions between two dates for one account. With hash sharding, all of Account X's transactions ARE on one shard — that part is fine. The real problem is Query 2 — show transactions above £10,000 across ALL accounts in last 30 days. This is a **scatter-gather query** — it must hit EVERY shard, aggregate results, and merge — at 5 billion rows across N shards, this becomes extremely expensive and slow."*

---

**2. You missed the two completely different access patterns**

This was the biggest hint in the question — Query 1 and Query 2 need fundamentally different data architectures:

> *"Query 1 is an **OLTP pattern** — fast, targeted, high frequency — 10,000 RPS, single account lookup by date range. Query 2 is an **OLAP pattern** — slow, full scan, low frequency — once a day, cross-account aggregation. Trying to serve both from the same database is the root problem. The architect's recommendation fails because it optimises for neither."*

---

**3. Cassandra is right — but you need to explain the data model**

Saying "use Cassandra" without the data model is a 50% answer at EM level:

> *"In Cassandra, I'd model with **accountId as partition key and transactionDate as clustering key** — this means all transactions for an account are co-located on one node, and date range queries are natively fast. Query 1 becomes a single-partition range scan — extremely efficient at 10K RPS."*

---

**4. You completely missed Query 2 — the AML compliance query**

This is the hardest part of the question and you didn't address it:

> *"Query 2 — cross-account, amount-filtered, 30-day scan — is fundamentally an analytical query. Cassandra is NOT good at this — it can't do cross-partition aggregations efficiently. For Query 2, I'd introduce a separate **data warehouse or OLAP layer** — Apache Spark or a dedicated analytics DB like Redshift/BigQuery — fed by a daily batch export from Cassandra. The compliance team runs their AML queries there, completely isolated from the operational system."*

---

**5. Address the 10-year retention problem**

> *"With 10 years of data at 200M rows/month, I'd implement **tiered storage in Cassandra** — hot data (last 90 days) on fast SSD nodes, warm data (90 days to 2 years) on standard nodes, cold data (2-10 years) archived to object storage like S3 with Cassandra's TTL and archival policies. This keeps operational costs manageable while meeting the 10-year FCA retention requirement."*

---

## 🏆 Model Answer (9/10 level)

> *"I'd challenge the architect's recommendation on two fronts.*
>
> *First — hash sharding on PostgreSQL at 5 billion rows growing 200M/month is operationally unsustainable. Resharding is painful, cross-shard range queries are expensive, and PostgreSQL wasn't designed for this scale.*
>
> *Second — and more fundamentally — Query 1 and Query 2 are completely different access patterns. Query 1 is OLTP — high frequency, single account, date range. Query 2 is OLAP — low frequency, full cross-account scan, aggregation. No single database architecture serves both optimally.*
>
> *My design uses two layers:*
>
> *Layer 1 — **Cassandra for operational transaction history** serving Query 1. Data modelled with accountId as partition key and transactionDate as clustering key — all transactions for an account co-located, date range queries are single-partition scans. At 10K RPS this is extremely efficient. I'd implement tiered storage — hot data on SSD, cold data archived to S3 — to manage 10 years of retention cost-effectively.*
>
> *Layer 2 — **OLAP data warehouse** (Redshift or BigQuery) for Query 2. A daily batch job exports the previous day's transactions from Cassandra into the warehouse. The compliance team runs AML queries there — full table scans, amount filters, cross-account aggregations — completely isolated from the operational system, no impact on customer-facing performance.*
>
> *This is a classic CQRS + polyglot persistence pattern — separate the read models for different query types, each optimised for its access pattern."*

---
# 🎤 Database Choices Drill — Question 3 of 3 🔥

### This is a **full system design question combining CAP + Caching + Database Choices** — exactly what a principal-level interview at JPMC or Goldman looks like!

---

### "You are the Engineering Manager at a UK bank. You are asked to design a **Real-Time Fraud Detection Platform** from scratch. Here are the requirements:

**Functional Requirements:**
- Score every transaction for fraud **before it is approved** — in under **100ms**
- Maintain a **behavioural profile** for every customer — spending patterns, location history, device fingerprints — updated in real time as transactions occur
- Flag suspicious transactions for **manual review** by the fraud operations team
- Store **complete fraud investigation history** for every flagged transaction — notes, decisions, evidence — for **5 years** for FCA compliance

**Non-Functional Requirements:**
- **200,000 transactions per second** at peak
- **99.99% availability** — fraud scoring must never be the reason a payment is blocked
- Scoring must complete in **under 100ms end-to-end**
- System must be **GDPR compliant** — EU customer behavioural data must never leave EU region

**The question has 3 parts:**
1. What **databases** would you use for each component — and why?
2. What **caching strategy** would you apply — and where?
3. What **CAP choices** does each component demand — and why?"**

---

> 💡 *Tip 1: Break the system into components first — scoring engine, behavioural profiles, flagged transactions, investigation history — each has different requirements*

> 💡 *Tip 2: 99.99% availability + 100ms SLA + 200K TPS — these three together should immediately tell you something about your CAP choice for the scoring path*

> 💡 *Tip 3: GDPR data residency — which database we studied has a native feature purpose-built for this?*

> 💡 *Tip 4: The fraud ops team querying investigation history is a different access pattern from real-time scoring — think CQRS*

-
Outstanding answer! This is your most complete and sophisticated response across the entire session! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 3

### What you got right
- ✅ **Broke the system into components first** — exactly the right approach
- ✅ **AP choice with strong justification** — "if scoring fails, send for offline reconciliation" — excellent EM thinking
- ✅ **Cassandra for behavioural profiles** — correct, high write throughput, right fit
- ✅ **partitionKey on customerId** — correct Cassandra data modelling instinct
- ✅ **Multi-layer caching** — distributed cache in front of Cassandra — right
- ✅ **Cache-Aside pattern** — correct for profile reads
- ✅ **Event-driven invalidation + TTL** — shows maturity
- ✅ **Refresh-Ahead at midnight** — correctly applied from earlier learning
- ✅ **TTL jitter** — correctly applied from earlier learning
- ✅ **Document DB for investigation history** — correct, unstructured dynamic fields
- ✅ **Differentiated KYC treatment** — shorter TTL for compliance-sensitive data

### Your Score: **8/10** — Best answer of the entire session! Here's the final push to 9/10 💡

---

### What to Strengthen

**1. You didn't address the 100ms SLA explicitly**

The 100ms end-to-end scoring SLA needs a dedicated latency budget breakdown:

> *"To hit 100ms end-to-end I'd allocate a latency budget — 5ms for API gateway, 10ms for cache lookup (L1 Caffeine + L2 Redis), 20ms for ML model scoring, 15ms for writing the score result, leaving 50ms buffer for network and serialisation. If cache miss occurs and Cassandra is hit directly, that's ~20ms — still within budget."*

---

**2. You missed the ML scoring engine database**

The fraud scoring model needs a **feature store** — you mentioned behavioural profiles but didn't explicitly name the scoring component:

> *"The ML scoring engine reads pre-computed features from a **Redis feature store** — device fingerprint score, location anomaly score, velocity counters — all pre-computed asynchronously and cached in Redis. This keeps the synchronous scoring path purely in-memory — sub-millisecond feature lookup, well within our 100ms SLA."*

---

**3. GDPR data residency — you missed MongoDB zone sharding**

The question explicitly called out EU data residency — this was a direct hint:

> *"For GDPR compliance, EU customer behavioural data must never leave the EU. I'd use **MongoDB zone sharding** for the investigation history store — EU customer documents pinned to EU shards deployed in AWS eu-west-2. For Cassandra behavioural profiles, I'd deploy a separate EU-region cluster for EU customers — DynamoDB Global Tables or Cassandra multi-DC with NetworkTopologyStrategy, routing EU customers to EU datacenter exclusively."*

---

**4. Address the 99.99% availability requirement explicitly**

> *"99.99% availability means maximum 52 minutes downtime per year. To achieve this on the scoring path — multi-region active-active deployment, no single point of failure. If Redis cache cluster fails, L1 Caffeine absorbs traffic. If Cassandra node fails, replication factor of 3 means 2 other nodes serve the request. Circuit breaker on the scoring service — if ML model is slow, fall back to a rule-based scorer that responds in 5ms."*

---

**5. Flagged transactions store — complete the picture**

You mentioned document DB for investigation history but didn't close the loop on how flagged transactions flow there:

> *"Every transaction scored above the fraud threshold publishes a FraudFlaggedEvent to Kafka. The fraud ops service consumes this event and writes the investigation record to MongoDB — including transaction details, fraud score, triggered rules, customer profile snapshot at time of detection. The fraud ops team queries MongoDB for their workflow — each investigation is a self-contained document with dynamic fields per fraud type."*

---

## 🏆 Model Answer (9/10 level)

> *"Let me break this into four components with different requirements.*
>
> **Component 1 — Real-time scoring path (200K TPS, 100ms SLA)**
> AP system — availability is paramount. If scoring is unavailable, transactions go to offline reconciliation with a hold — far better than blocking payments.*
>
> *Architecture: L1 Caffeine in-process cache → L2 Redis feature store → ML scoring engine. Pre-computed features — location anomaly, device trust score, velocity counters — cached in Redis, refreshed asynchronously every few seconds via event stream. Latency budget: 5ms gateway + 10ms cache + 20ms model + 15ms write = 50ms with 50ms buffer.*
>
> **Component 2 — Customer behavioural profiles (high write, low read latency)**
> Cassandra with customerId as partition key, eventTimestamp as clustering key. Handles 200K writes/second natively. Redis cache in front — Cache-Aside pattern, event-driven invalidation via CDC, 24hr TTL with jitter, Refresh-Ahead pre-warm at midnight. KYC status gets separate cache key with 5-minute TTL — compliance sensitive.*
>
> **Component 3 — Investigation history (unstructured, 5-year retention)**
> MongoDB — dynamic schema per fraud type, rich querying for fraud ops team. FraudFlaggedEvent published to Kafka → fraud ops service writes full investigation document to MongoDB. Zone sharding for GDPR — EU customer documents pinned to EU shards in AWS eu-west-2.*
>
> **Component 4 — GDPR compliance**
> Cassandra NetworkTopologyStrategy — EU customers routed exclusively to EU datacenter. MongoDB zone sharding — EU data pinned to EU shards. No cross-region replication for EU customer data.*
>
> **99.99% availability:**
> Multi-region active-active, replication factor 3 on Cassandra, Redis Sentinel, L1 Caffeine as last-resort cache, circuit breaker with rule-based fallback scorer if ML model degrades."*

---
# 🔴 PILLAR 4 — Load Balancing

Load balancing is often treated as a simple topic — but at EM level in a tier-1 GCC interview, they expect you to reason about **algorithms, layers, failure scenarios, session persistence and cloud-native patterns** — all tied back to FS context.

---

## What is Load Balancing?

Load balancing is **distributing incoming traffic across multiple servers** to ensure no single server becomes a bottleneck — achieving high availability, scalability and fault tolerance.

```
                    ┌─── Server A (33% traffic)
Client → Load Balancer ─── Server B (33% traffic)
                    └─── Server C (33% traffic)
```

---

## 🏗️ The Two Layers of Load Balancing

### Layer 4 — Transport Layer (L4)
- Operates at **TCP/UDP level** — routes based on IP address and port
- **Doesn't inspect packet content** — pure network routing
- **Extremely fast** — minimal processing overhead
- **Examples:** AWS NLB (Network Load Balancer), HAProxy in TCP mode

**FS Use case:** High-frequency trading systems, FIX protocol connections — where microsecond latency matters and packet inspection overhead is unacceptable

---

### Layer 7 — Application Layer (L7)
- Operates at **HTTP/HTTPS level** — routes based on URL, headers, cookies, content
- **Intelligent routing** — can route `/payments` to payment servers, `/accounts` to account servers
- **Slightly higher latency** — inspects packet content
- **Examples:** AWS ALB (Application Load Balancer), NGINX, Istio

**FS Use case:** Microservices routing, API gateway, A/B testing, canary deployments — most modern FS platforms use L7

---

## ⚙️ The 6 Load Balancing Algorithms

### Algorithm 1 — Round Robin
**How it works:** Requests distributed to servers in rotation — A, B, C, A, B, C...

```
Request 1 → Server A
Request 2 → Server B
Request 3 → Server C
Request 4 → Server A (back to start)
```

**✅ Pros:** Simple, even distribution, no state needed

**❌ Cons:** Ignores server capacity and current load — a slow server gets same traffic as a fast one

**FS Use case:** Stateless microservices with identical capacity — account enquiry, product catalogue

---

### Algorithm 2 — Weighted Round Robin
**How it works:** Servers assigned weights based on capacity — higher weight = more requests

```
Server A (weight 3) → gets 3 requests per cycle
Server B (weight 1) → gets 1 request per cycle
Server C (weight 2) → gets 2 requests per cycle
```

**✅ Pros:** Accounts for heterogeneous server capacity

**❌ Cons:** Weights are static — doesn't adapt to real-time load changes

**FS Use case:** Mixed instance types in cloud — some servers have more CPU/RAM than others

---

### Algorithm 3 — Least Connections
**How it works:** New request goes to server with **fewest active connections**

```
Server A: 100 active connections
Server B: 45 active connections  ← next request goes here
Server C: 78 active connections
```

**✅ Pros:** Adapts to actual server load — naturally handles slow requests

**❌ Cons:** Requires tracking connection state — slight overhead

**FS Use case:** Payment processing — some transactions take longer (3DS authentication, fraud checks) — least connections prevents overloading slow-transaction servers

---

### Algorithm 4 — Least Response Time
**How it works:** Routes to server with **lowest combination of active connections AND response time**

**✅ Pros:** Most intelligent routing — accounts for both load and performance

**❌ Cons:** Higher overhead — must measure response times continuously

**FS Use case:** Premium banking APIs where latency SLAs are strict — private banking, trading platforms

---

### Algorithm 5 — IP Hash (Sticky Sessions)
**How it works:** Hash of client IP determines which server handles their requests — same client always goes to same server

```
hash(clientIP) % 3 = Server index
Client 192.168.1.1 → always Server B
Client 192.168.1.2 → always Server A
```

**✅ Pros:** Session persistence — stateful applications work correctly

**❌ Cons:** Uneven distribution if few clients send lots of traffic. Server failure breaks all sessions for that IP

**FS Use case:** Legacy stateful banking applications that store session on server — though modern FS systems avoid this by externalising session state to Redis

---

### Algorithm 6 — Consistent Hashing
**How it works:** Both servers and requests mapped to a hash ring — request goes to nearest server clockwise

**✅ Pros:** Minimal redistribution when servers added/removed — only neighbouring keys affected

**❌ Cons:** More complex implementation

**FS Use case:** Distributed caching layers, API gateway routing in microservices — same request type always hits same cache-warmed server

---

## ⚠️ The Hard Problems — What Interviewers Really Probe

### Problem 1 — Session Persistence (Sticky Sessions)
*"Your payment journey is multi-step — customers go through 3 screens before confirming. How do you handle session state across load balanced servers?"*

**Wrong answer:** Use IP Hash sticky sessions — creates hotspots, breaks on server failure

**Right answer:**
> *"Externalise session state entirely — store in Redis with a sessionId cookie. Every server can serve any request because session data is retrieved from Redis, not local memory. This makes all servers stateless — load balancer can freely route to any server. Session fails only if Redis fails — not if any individual server fails."*

---

### Problem 2 — Health Checks
*"How does your load balancer know when a server is unhealthy?"*

Two types:

**Passive health checks** — load balancer detects failures from real traffic errors (connection refused, timeouts)

**Active health checks** — load balancer proactively pings a health endpoint:
```
GET /health → 200 OK (healthy)
GET /health → 503 (unhealthy → remove from pool)
```

**FS Best practice:**
> *"I'd implement a deep health check endpoint — not just 'is the server running' but 'can it reach its dependencies' — DB connection pool available, Redis reachable, downstream services responsive. A server that's running but can't reach the DB shouldn't receive traffic."*

---

### Problem 3 — Thundering Herd on Server Recovery
*"A server comes back online after being unhealthy. What happens?"*

- Load balancer detects it's healthy
- Immediately sends it full traffic share
- Server gets overwhelmed before it's fully warmed up — goes unhealthy again
- Creates oscillation loop

**Solution — Slow Start / Warmup:**
> *"Configure slow start on the recovered server — gradually ramp traffic from 0% to full share over 2-3 minutes. This allows JVM to warm up, caches to populate, connection pools to stabilise before handling full load."*

---

### Problem 4 — SSL Termination
*"Where do you terminate SSL in your load balancing architecture?"*

**Option 1 — SSL Termination at Load Balancer**
- LB decrypts traffic, forwards plain HTTP to servers
- ✅ Offloads CPU from servers, centralised cert management
- ❌ Traffic between LB and servers is unencrypted — risk inside network

**Option 2 — SSL Passthrough**
- LB forwards encrypted traffic, servers decrypt
- ✅ End-to-end encryption
- ❌ LB can't inspect L7 content — loses intelligent routing

**Option 3 — SSL Re-encryption (FS Best Practice)**
- LB terminates SSL, re-encrypts for server communication with internal cert
- ✅ Intelligent L7 routing + end-to-end encryption
- **FS Use case:** PCI-DSS compliance requires encryption in transit — even inside the network

---

## 🏗️ Load Balancing Architecture in Modern FS Microservices

Modern FS platforms have **multiple layers** of load balancing:

```
Internet
    ↓
CDN (CloudFront/Akaike) — static content, DDoS protection
    ↓
External Load Balancer (AWS ALB) — L7, SSL termination, WAF
    ↓
API Gateway (Kong/AWS API GW) — rate limiting, auth, routing
    ↓
Internal Load Balancer / Service Mesh (Istio) — service-to-service
    ↓
Microservices (Kubernetes pods) — HPA auto-scales pods
    ↓
Database Load Balancer (PgBouncer/ProxySQL) — DB connection pooling
```

---

## ☁️ Cloud Load Balancing in AWS (Most Common in FS GCCs)

| Service | Layer | Best For |
|---|---|---|
| **ALB** (Application LB) | L7 | HTTP/HTTPS microservices, path-based routing |
| **NLB** (Network LB) | L4 | Ultra-low latency, TCP, high throughput |
| **GLB** (Gateway LB) | L3 | Security appliances, firewall integration |
| **Route 53** | DNS | Global traffic routing, failover between regions |

**Multi-region active-active pattern (critical for 99.99% FS SLAs):**
```
Route 53 (latency-based routing)
    ├── EU-WEST-2 (London) ALB → EKS cluster
    └── EU-WEST-1 (Ireland) ALB → EKS cluster (failover)
```

---

## 🏦 Load Balancing in FS — Key Rules

| Rule | Why It Matters |
|---|---|
| **Always externalise session state** | Stateless servers = free load balancing |
| **Deep health checks, not shallow pings** | Avoid routing to servers that can't serve requests |
| **SSL re-encryption inside network** | PCI-DSS compliance — encrypt in transit everywhere |
| **Slow start on server recovery** | Prevent oscillation loops |
| **Multi-layer load balancing** | CDN → ALB → Service Mesh → DB pool |
| **Circuit breaker at service mesh layer** | Prevent cascade failures across services |

---

## ✅ Load Balancing — Quick Revision Checklist

- [ ] Can explain L4 vs L7 load balancing with FS use cases
- [ ] Can explain all 6 algorithms with pros/cons
- [ ] Can explain sticky sessions problem and Redis-based solution
- [ ] Can explain deep health checks vs shallow pings
- [ ] Can explain SSL termination options and FS best practice
- [ ] Can describe multi-layer LB architecture in modern FS microservices
- [ ] Can name AWS load balancing services and when to use each

---

Perfect! 3 questions, one at a time, progressively harder! 💪

---

# 🎤 Load Balancing Drill — Question 1 of 3

### "You are the Engineering Manager for a **Mobile Banking API** at a large UK bank. The API handles **150,000 requests per second** at peak — covering login, balance checks, payments and account management. The platform runs on **AWS** across **two availability zones** in London (eu-west-2).

Your infrastructure team proposes using **Round Robin** load balancing across 20 application servers.

**Three specific scenarios are happening in production:**

- **Scenario A:** During peak hours, 3 of your 20 servers are running a **batch reconciliation job** in the background — making them significantly slower than the other 17
- **Scenario B:** A customer initiates a **payment journey** — it's a 4-step flow across 4 separate API calls. On step 3, they get routed to a different server which has no context of steps 1 and 2
- **Scenario C:** One of your servers **crashes mid-request** — 500 active connections are dropped simultaneously

**For each scenario — what is the problem with Round Robin, and how do you fix it?"**

---

> 💡 *Tip 1: Each scenario points to a different weakness of Round Robin — treat them independently*

> 💡 *Tip 2: Scenario B is a classic session management problem — think about what we covered on sticky sessions and why the RIGHT fix isn't sticky sessions*

> 💡 *Tip 3: Scenario C is about health checks — what type and how fast?*

Take your shot! 🎯

Excellent answer! You tackled all three scenarios with strong reasoning! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 1

### What you got right
- ✅ **Scenario A** — Correctly identified Round Robin's blindness to server load
- ✅ **Weighted Round Robin as primary fix** — correct and pragmatic
- ✅ **Least Response Time as alternative** — correctly noted the overhead tradeoff
- ✅ **NFT-driven decision** — outstanding EM thinking — test before committing
- ✅ **Scenario B** — Correctly rejected sticky sessions as unscalable
- ✅ **Externalise state to distributed cache** — perfect, stateless servers
- ✅ **Scenario C** — Deep health checks covering DB + downstream dependencies
- ✅ **Not just ping** — shows real production experience

### Your Score: **8/10** — Strong across all three! Here's the push to 9/10 💡

---

### What to Strengthen

**Scenario A — One missing piece: dynamic vs static weights**

You correctly chose weighted Round Robin — but interviewers at this level will probe further:

> *"The challenge with weighted Round Robin is weights are **statically configured** — if the batch job finishes at 2am, those 3 servers still carry reduced weight until someone manually changes the config. I'd pair weighted Round Robin with **auto-scaling triggers** — when batch reconciliation starts, an automation script reduces those servers' weights via the load balancer API. When batch completes, weights are restored automatically. No manual intervention needed."*

---

**Scenario B — Name the exact solution more precisely**

You got the concept right — but name the full solution:

> *"I'd externalise session state to **Redis** with a **sessionId stored in a secure HttpOnly cookie**. Every API server reads and writes session state from Redis — completely stateless from the load balancer's perspective. The 4-step payment journey state lives in Redis keyed by sessionId — any server can pick up any step seamlessly. This also means if a server crashes mid-journey, the customer's next request goes to a healthy server with full session context intact — no journey restart needed."*

The key addition — **what happens to in-flight journeys when a server crashes** — this links Scenario B and C together elegantly.

---

**Scenario C — Add the slow start / warmup detail**

You nailed health checks — one more layer:

> *"When the crashed server recovers and passes health checks, I'd configure **slow start warmup** — traffic ramped from 0% to full share over 2–3 minutes. This prevents the recovered server getting overwhelmed before its JVM is warmed up, connection pools are filled, and local caches are populated. Without slow start, the recovered server often fails again immediately — creating an oscillation loop in production."*

---

**Missing — AWS specifics for this scenario**

Since the question mentioned AWS eu-west-2 explicitly:

> *"On AWS, I'd use **Application Load Balancer (ALB)** — L7, supports path-based routing, has native health check configuration with customisable intervals and thresholds. For the health check, I'd set — interval 10 seconds, healthy threshold 2, unhealthy threshold 2 — so a server is pulled from rotation within 20 seconds of failing, and reinstated after 2 consecutive healthy checks."*

---

## 🏆 Model Answer (9/10 level)

> **Scenario A:**
> *"Round Robin treats all servers equally — the 3 servers running batch reconciliation get the same traffic as the 17 clean servers, causing latency spikes for customers hitting those servers. I'd switch to Weighted Round Robin — reduce weights on the 3 batch servers proportionally. To avoid manual weight management, I'd automate weight adjustment via the ALB API triggered by the batch job start/end events. During NFT testing, if weighted Round Robin still shows latency variance, I'd evaluate Least Response Time — but only if the tracking overhead is justified by performance gains."*

> **Scenario B:**
> *"This is a session state management problem. The wrong fix is sticky sessions — IP Hash creates hotspots and breaks on server failure. The right fix is externalising session state to Redis — sessionId in a secure HttpOnly cookie, payment journey state stored in Redis keyed by sessionId. All servers become stateless — any server handles any step. Crucially, if a server crashes mid-journey, the customer's next request hits a healthy server with full journey context from Redis — no restart needed."*

> **Scenario C:**
> *"Round Robin has no awareness of server health — it keeps routing to the crashed server until connections time out. I'd configure ALB active health checks — GET /health/deep every 10 seconds, unhealthy threshold of 2 failures = server pulled from rotation within 20 seconds. The deep health endpoint checks DB connectivity, Redis reachability and critical downstream availability — not just process liveness. On recovery, slow start warmup ramps traffic gradually over 2–3 minutes — preventing the oscillation loop where a recovering server immediately gets overwhelmed and fails again."*

---
# 🎤 Load Balancing Drill — Question 2 of 3

### "You are the Engineering Manager for a **Global Payments Platform** at a bank with customers across **UK, EU and Asia Pacific (Singapore)**. The platform processes international wire transfers and must meet a **99.99% availability SLA.**

Your current architecture runs **entirely in AWS London (eu-west-2)**. The CTO raises three concerns in an architecture review:

- **Concern 1:** *"If eu-west-2 goes down — even partially — we lose all global traffic. We have no resilience beyond a single region."*
- **Concern 2:** *"Our Singapore customers are experiencing **280ms API latency** — they're complaining and we're losing them to competitors with local presence."*
- **Concern 3:** *"During a major incident last month, our team manually switched traffic away from a degraded availability zone. It took **12 minutes**. That's unacceptable for 99.99% SLA."*

**Design a multi-region load balancing architecture on AWS that addresses all three concerns. Be specific about which AWS services you'd use at each layer and how traffic flows globally."**

---

> 💡 *Tip 1: 99.99% SLA = maximum 52 minutes downtime per year — 12 minutes on a single incident is already 23% of your annual budget gone*

> 💡 *Tip 2: Think about the layers we covered — CDN → External LB → API Gateway → Service Mesh — which AWS services map to each layer?*

> 💡 *Tip 3: Singapore latency is a data residency + routing problem — but remember GDPR means EU data can't leave EU. How do you serve Singapore customers from Singapore without mixing EU data?*

> 💡 *Tip 4: 12-minute manual failover → what AWS service does automatic DNS-based failover in under 60 seconds?*

Take your shot! 🎯

Outstanding answer! This is your most architecturally complete response of the entire session! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 2

### What you got right
- ✅ **Full stack per region** — correct, each country needs its own deployment
- ✅ **CDN with geo-aware routing** — Akamai for edge routing — excellent
- ✅ **Data residency awareness** — proactively called out per-country requirements
- ✅ **Multi-layer architecture** — CDN → ALB → API GW → ILB → Service Mesh → Compute — complete
- ✅ **Multi-AZ within each region** — high availability within country
- ✅ **Singapore traffic never leaves Singapore** — GDPR + data residency correctly applied
- ✅ **Database replication choices** — sync replication, read replicas, CP/AP per use case
- ✅ **Resilience within region** — multi-AZ stack correctly described

### Your Score: **7.5/10** — Strongest architectural thinking yet! Here's the push to 9/10 💡

---

### What to Strengthen

**1. Name the specific AWS services — the question explicitly asked for them**

You described the architecture correctly but used generic terms. Interviewers at JPMC/Goldman want AWS specifics:

| Layer | What You Said | What to Say |
|---|---|---|
| CDN | Akamai | **CloudFront** (AWS native) or Akamai — both valid, but name AWS Route 53 for global DNS routing |
| Global routing | Geo-aware routing | **AWS Route 53 with Latency-Based Routing or Geolocation Routing policies** |
| External LB | MR ALB | **AWS ALB per region** — ALB is regional, not global — Route 53 routes to the right regional ALB |
| Auto-failover | Not mentioned | **Route 53 Health Checks + Failover Routing** — automatic DNS failover in under 60 seconds |
| API Gateway | Multi-regional APIGW | **AWS API Gateway Regional endpoints** per region |
| Service Mesh | ILB | **AWS App Mesh or Istio on EKS** |

---

**2. You didn't address the 12-minute manual failover concern directly**

This was Concern 3 — the most specific and measurable problem:

> *"The 12-minute manual failover is unacceptable against a 99.99% SLA. I'd automate this entirely using **Route 53 Health Checks with Failover Routing** — Route 53 polls the ALB health endpoint every 10 seconds. After 3 consecutive failures (30 seconds), it automatically updates DNS to route traffic to the secondary AZ or region. Total automated failover time — under 60 seconds. No human intervention required."*

---

**3. Active-Active vs Active-Passive — explicitly state your choice**

> *"I'd design UK and EU as **active-active** — both AZs serving live traffic simultaneously with Route 53 latency-based routing splitting load. This means failover is instantaneous — no cold start, traffic simply shifts to the surviving AZ. Singapore would be active-active within its region but active-passive relative to UK — Singapore traffic stays in Singapore, only fails over to Hong Kong or Sydney if the entire Singapore region is down."*

---

**4. Address the GDPR constraint explicitly for EU**

> *"EU customer data never leaves the EU region — enforced at two layers. First, Route 53 Geolocation Routing pins EU IP ranges to eu-west-2 and eu-west-1 exclusively. Second, MongoDB zone sharding and Cassandra NetworkTopologyStrategy ensure EU customer data is only replicated within EU datacenters — never to ap-southeast-1 Singapore."*

---

**5. Mention CloudFront for latency improvement**

> *"For the 280ms Singapore latency — the root cause is packets travelling London → Singapore round-trip. CloudFront edge locations in Singapore cache static API responses and terminate TLS at the edge — reducing latency to ~20ms for cached responses. For dynamic requests, CloudFront still accelerates by using AWS backbone network from Singapore edge to London origin — reducing latency from 280ms to ~80ms even for non-cacheable requests."*

---

## 🏆 Model Answer (9/10 level)

> **Global Routing Layer:**
> *"I'd use **AWS Route 53** as the global DNS layer with two routing policies — Geolocation Routing to pin EU IPs to EU regions (GDPR compliance) and Latency-Based Routing within each region to always serve from the lowest-latency endpoint. CloudFront sits in front for edge caching and TLS termination — Singapore edge nodes cut dynamic request latency from 280ms to ~80ms using AWS backbone, and cached responses drop to ~20ms."*

> **Regional Stack (deployed identically in eu-west-2 London, eu-west-1 Ireland, ap-southeast-1 Singapore):**
> *"Each region runs: AWS ALB (L7, path-based routing, SSL re-encryption) → AWS API Gateway Regional endpoint (rate limiting, auth, request validation) → AWS App Mesh service mesh (mTLS, circuit breakers, observability) → EKS compute clusters across 3 AZs → Regional databases (PostgreSQL RDS Multi-AZ for transactional data, Cassandra/DynamoDB for high-volume data with NetworkTopologyStrategy for data residency enforcement)."*

> **Concern 1 — Single region failure:**
> *"Active-active within each region across 3 AZs. Route 53 health checks poll ALB every 10 seconds — automatic failover to secondary region in under 60 seconds if primary region degrades."*

> **Concern 2 — Singapore latency:**
> *"Full stack deployed in ap-southeast-1 — Singapore customers served locally. CloudFront edge in Singapore for TLS termination and response caching. Singapore data pinned to ap-southeast-1 — never replicated to EU regions."*

> **Concern 3 — 12-minute manual failover:**
> *"Eliminated entirely. Route 53 Health Checks + Failover Routing — 3 consecutive health check failures (30 seconds) triggers automatic DNS failover. Zero human intervention. Total failover time under 60 seconds — within 99.99% SLA budget."*

---
# 🎤 Load Balancing Drill — Question 3 of 3 🔥

### The hardest question of the entire session — this is a **real production incident scenario** combined with architecture design. Exactly what Principal/EM interviews at Goldman Sachs and JPMC look like!

---

### "You are the Engineering Manager for a **High-Frequency Trading (HFT) Support Platform** at a tier-1 investment bank. The platform processes **trade order confirmations** for institutional clients — hedge funds, asset managers, pension funds.

Here is your architecture today:
- AWS ALB (L7) in front of 15 application servers
- Round Robin load balancing
- Each server maintains **in-memory order state** for active trades
- Average request latency: **45ms**
- Peak load: **80,000 requests/second**

**A major incident occurs on a Monday morning at market open:**
- Trade volume spikes to **3x normal** — 240,000 RPS suddenly
- 4 of your 15 servers become unresponsive due to memory pressure
- The ALB keeps routing 26% of traffic to the 4 dead servers
- Clients are experiencing **order confirmation failures**
- Your on-call engineer is manually removing dead servers from the ALB — it's taking **8 minutes per server**
- Institutional clients are calling — they are threatening to move business elsewhere

**You have four tasks:**

1. **Immediate mitigation** — what do you do RIGHT NOW to stop the bleeding?
2. **Root cause** — what architectural weaknesses caused this incident?
3. **Fix** — redesign the architecture to prevent this ever happening again
4. **Client communication** — your MD asks you to brief the institutional clients. What do you say and what commitments do you make?"**

---

> 💡 *Tip 1: Task 1 is about speed — what's the fastest way to remove dead servers from rotation without waiting 8 minutes each?*

> 💡 *Tip 2: Task 2 has MULTIPLE root causes — count them. In-memory state, Round Robin, no health checks, no auto-scaling — how many can you identify?*

> 💡 *Tip 3: Task 3 — the redesign should address every root cause you identified. Think about L4 vs L7 for HFT, stateless servers, health checks, auto-scaling*

> 💡 *Tip 4: Task 4 is an EM/leadership test — how do you communicate under pressure to institutional clients without over-promising or damaging trust?*

---

Brilliant answer! You covered all 4 tasks with real EM maturity! Here's your detailed feedback 👇

---

## ✅ Feedback — Question 3

### What you got right
- ✅ **Task 1** — Health checks at ALB to auto-remove dead servers — correct immediate fix
- ✅ **Task 2** — Correctly identified stateful architecture as root cause
- ✅ **Task 2** — Auto-scaling gap identified — excellent
- ✅ **Task 2** — Predictive health checks called out — shows maturity
- ✅ **Task 3** — Kubernetes with HPA auto-scaling — correct modern solution
- ✅ **Task 3** — Redis for stateless architecture — correctly applied
- ✅ **Task 3** — Deep health checks replacing ping — consistent with earlier learning
- ✅ **Task 4** — Acknowledgement + apology + fix assurance + dedicated helpline — empathetic and professional

### Your Score: **7/10** — Good across all 4 tasks! Here's the push to 9/10 💡

---

### What to Strengthen

**Task 1 — Immediate mitigation needs to be faster and more specific**

Health checks are the RIGHT fix — but configuring them takes time during an active incident. The FASTEST immediate actions are:

> *"In the next 5 minutes — three parallel actions:*
> *First, use **AWS ALB console or CLI to manually deregister the 4 dead servers immediately** — don't wait for the engineer to do it one by one via UI. A single AWS CLI command deregisters all 4 simultaneously:*
> ```
> aws elbv2-deregister-targets --target-group-arn <arn> 
> --targets all-4-dead-servers
> ```
> *Second, immediately trigger **manual horizontal scaling** — add 8 new servers to absorb the 3x traffic spike right now.*
> *Third, enable **Connection Draining** on the dead servers — gracefully complete in-flight requests before deregistration rather than dropping them.*"

---

**Task 2 — You missed 3 root causes**

You identified 2 — here are the full 5:

| Root Cause | Your Answer | Missing? |
|---|---|---|
| Stateful in-memory architecture | ✅ Identified | — |
| No auto-scaling | ✅ Identified | — |
| No health checks | Partially | Should name — ALB was routing to dead servers for 8+ minutes |
| Wrong LB algorithm | Mentioned briefly | Round Robin ignores server health AND load |
| No capacity planning for 3x spike | ❌ Missed | Market open spikes are predictable — should have been anticipated |
| Manual runbook for server removal | ❌ Missed | 8 minutes per server = no automation, pure human dependency |

---

**Task 3 — L4 vs L7 for HFT — critical missed point**

This was in the tips — HFT is latency-sensitive:

> *"Current ALB is L7 — it inspects every HTTP packet adding ~2-5ms overhead. For a trading platform where latency is a competitive differentiator, I'd evaluate switching the **hot path to AWS NLB (L4)** — pure TCP routing, sub-millisecond overhead, supports ultra-low latency. Order confirmations don't need HTTP header inspection — they need speed. L7 ALB stays for management APIs and non-latency-sensitive paths."*

---

**Task 3 — Predictive auto-scaling for market open**

> *"Market open at 8am is a **known, predictable traffic spike** — this isn't random. I'd implement **scheduled scaling** in AWS Auto Scaling Groups — automatically pre-warm 20 additional pods at 7:45am every trading day, before the spike hits. Reactive scaling is too slow for sudden 3x spikes — by the time CloudWatch detects the spike and spins up new pods, the damage is done. Predictive + scheduled scaling eliminates the reaction time entirely."*

---

**Task 4 — Client communication needs more structure**

Your instincts are right — empathy, ownership, fix, helpline. But at MD/institutional client level, the communication needs to be more structured and specific:

> *"I'd communicate in three layers:*
>
> *Immediate (within 30 minutes of resolution):*
> *'We experienced a platform incident between 08:02 and 08:47 this morning affecting order confirmation response times. The issue has been fully resolved. We deeply apologise for the impact on your trading operations.'*
>
> *Short-term (within 24 hours):*
> *'Root cause analysis complete. The incident was caused by [specific causes]. Immediate fixes implemented: [list]. No client orders were lost — all confirmations are being reconciled and will be confirmed within 2 hours.'*
>
> *Long-term (within 1 week):*
> *'Architectural remediation plan with timeline. Specific SLA commitments going forward. Named account manager contact for any residual concerns.'*
>
> *Critical — NEVER say 'this will never happen again.' Say instead: 'We have implemented controls that significantly reduce the probability and impact of similar incidents.'"*

---

## 🏆 Model Answer (9/10 level)

> **Task 1 — Immediate Mitigation (first 5 minutes):**
> *"Three parallel actions — deregister all 4 dead servers simultaneously via AWS CLI (not one by one via console), enable connection draining to protect in-flight orders, and trigger manual scale-out adding 8 servers immediately. Simultaneously, page the database team to check if Redis or downstream DB is contributing to memory pressure."*

> **Task 2 — Root Causes (5 identified):**
> *"Five root causes: stateful in-memory order state preventing auto-scaling; no ALB health checks — dead servers stayed in rotation for 8+ minutes; Round Robin ignoring server load; no auto-scaling group configured; and critically — market open 3x spike is predictable every trading day — there was no capacity planning or scheduled pre-warming for this known pattern."*

> **Task 3 — Architectural Redesign:**
> *"Four changes: First — externalise all order state to Redis — stateless pods, freely auto-scalable. Second — Kubernetes HPA with scheduled pre-scaling at 7:45am daily before market open, plus reactive scaling triggers at 60% CPU. Third — deep health checks every 10 seconds, unhealthy threshold 2 — automatic deregistration in 20 seconds, no human needed. Fourth — evaluate NLB (L4) for the hot order confirmation path — eliminates L7 inspection overhead, reduces latency from 45ms toward sub-10ms target."*

> **Task 4 — Client Communication:**
> *"Acknowledge, own, explain, commit — in that order. Never say 'never again' — say 'significantly reduced risk.' Provide RCA within 24 hours, remediation plan within 1 week, named account manager for each affected institutional client. Offer SLA credit per contract terms without waiting for clients to ask — proactively demonstrates accountability."*



## 🏁 Days 1–3 COMPLETE! Full Pillar Summary

| Pillar | Status | Your Strength | Gap to Close |
|---|---|---|---|
| 🔵 CAP Theorem | ✅ Complete | Business reasoning | Regulatory depth |
| 🟡 Caching | ✅ Complete | Multi-layer thinking | Failure scenarios |
| 🟢 Database Choices | ✅ Complete | Polyglot instinct | OLTP vs OLAP split |
| 🔴 Load Balancing | ✅ Complete | Architecture layers | AWS service specifics |

---



# 📚 Days 1–3 Complete Recap

Here's your full condensed summary — everything you need to revise before an interview, in one place.

---

## 🔵 PILLAR 1 — CAP Theorem

### The Core Rule
In any distributed system, you can only guarantee **2 of 3** — but since **Partition Tolerance is mandatory** in real distributed systems, the real choice is always **Consistency vs Availability** when a partition occurs.

### The Decision Framework

| Scenario | Choice | Reason |
|---|---|---|
| Payment debit/credit | **CP** | Wrong balance = regulatory breach |
| Fraud scoring | **AP** | Stale score better than no score |
| Offers catalogue | **AP** | Availability drives revenue |
| Account balance | **CP** | Must be correct, always |
| Audit logs | **CP** | Cannot lose compliance records |
| Notification preferences | **AP** | Stale data acceptable |

### PACELC Extension
Even without partitions — at scale you choose between **Latency vs Consistency**. High-volume read services (offers, catalogue) favour latency over perfect consistency.

### The Interview Triangle ✅
> **Choice → Business/Regulatory consequence → Technical mechanism**

---

## 🟡 PILLAR 2 — Caching Strategies

### The 5 Patterns

| Pattern | How It Works | FS Use Case |
|---|---|---|
| **Cache-Aside** | App checks cache, misses → fetches DB, populates cache | Customer profiles, product catalogue |
| **Write-Through** | Every write goes to cache + DB simultaneously | KYC status, account preferences |
| **Write-Behind** | Write to cache, async flush to DB | ❌ Avoid for financial state — data loss risk |
| **Read-Through** | Cache fetches from DB on miss, not the app | Reference data — currency codes, country lists |
| **Refresh-Ahead** | Cache proactively refreshes before TTL expires | FX rates, market data — predictable update schedule |

### The 4 Hard Problems

| Problem | Cause | Solution |
|---|---|---|
| **Cache Stampede** | Mass cache miss simultaneously | Mutex lock + jittered TTL |
| **Cache Avalanche** | Mass TTL expiry at same time | Stagger TTLs with random jitter |
| **Cache Penetration** | Repeated queries for non-existent data | Cache null results + Bloom filter |
| **Stale Data** | DB updated, cache not invalidated | Event-driven invalidation via CDC/Kafka |

### Multi-Layer Caching
```
L1 — Caffeine (in-process, sub-ms, JVM)
L2 — Redis (distributed, ~1ms, shared)
L3 — Database (source of truth, last resort)
```

### FS Rules
- **Never Write-Behind** for financial state
- **Never cache PII unencrypted** — GDPR
- **Always TTL** — never cache indefinitely
- **KYC/compliance fields** — shorter TTL (5–10 mins) + mandatory event invalidation
- **Monitor hit ratio** — below 80% = strategy needs review

---

## 🟢 PILLAR 3 — Database Choices

### The 6 Database Types

| Type | Examples | FS Use Case | Key Strength |
|---|---|---|---|
| **RDBMS** | PostgreSQL, Oracle | Core ledger, transactions | Full ACID, regulatory compliance |
| **Document** | MongoDB, Couchbase | Customer profiles, KYC docs | Flexible schema, embedded objects |
| **Key-Value** | Redis, DynamoDB | Sessions, rate limiting, counters | Sub-ms latency, atomic ops |
| **Wide-Column** | Cassandra, HBase | Audit trail, transaction history | Extreme write throughput |
| **Time-Series** | InfluxDB, TimescaleDB | FX rates, market data, metrics | Time-range queries, compression |
| **Graph** | Neo4j, Neptune | Fraud rings, AML, money trails | Relationship traversal |

### ACID vs BASE

| | ACID (RDBMS) | BASE (NoSQL) |
|---|---|---|
| Consistency | Strong always | Eventual |
| Transactions | Full multi-row | Limited |
| FS Fit | Money movement | High-volume non-critical |

### Sharding Strategies

| Strategy | How | Pros | Cons |
|---|---|---|---|
| **Range** | Split by value range | Simple, range queries fast | Hotspot on latest shard |
| **Hash** | hash(key) % shards | Even distribution | Range queries expensive, resharding painful |
| **Directory** | Lookup table maps key → shard | Flexible | Directory = bottleneck + SPOF |
| **Consistent Hashing** | Keys + nodes on hash ring | Minimal data movement on scaling | Complex, needs virtual nodes |

### Cross-Shard Transaction Problem
> RDBMS ACID doesn't span shards → Use **Saga Pattern** (preferred) or 2PC (avoid — blocking, slow)

### How NoSQL Scales Differently

| DB | Scaling Mechanism |
|---|---|
| **Cassandra** | Consistent hashing + vnodes + tunable consistency per operation |
| **DynamoDB** | Automatic partitioning, on-demand scaling, Global Tables |
| **MongoDB** | Native sharding with mongos router + zone sharding for data residency |

### Polyglot Persistence — The Golden Rule
> **Never force one DB to do everything.** Match each component to the DB optimised for its access pattern.

```
Payment Service     → PostgreSQL  (ACID transactions)
Customer Profile    → MongoDB     (flexible schema)
Session Store       → Redis       (low latency)
Audit Trail         → Cassandra   (high write throughput)
Fraud Analytics     → Neo4j       (relationship traversal)
Rate History        → InfluxDB    (time-series)
```

### The DB Selection Flowchart
```
Money movement / financial state? → PostgreSQL (ACID)
Time-series data?                 → InfluxDB / TimescaleDB
Relationship / fraud network?     → Neo4j
Extreme write volume?             → Cassandra
Simple key lookup, low latency?   → Redis / DynamoDB
Flexible schema, object store?    → MongoDB
```

---

## 🔴 PILLAR 4 — Load Balancing

### L4 vs L7

| Layer | Operates At | Speed | Routing Basis | FS Use Case |
|---|---|---|---|---|
| **L4** | TCP/UDP | Ultra-fast | IP + Port | HFT, FIX protocol — microsecond latency |
| **L7** | HTTP/HTTPS | Fast | URL, headers, cookies | Microservices, API routing — most FS platforms |

### The 6 Algorithms

| Algorithm | Best For | FS Use Case |
|---|---|---|
| **Round Robin** | Identical stateless servers | Simple microservices |
| **Weighted Round Robin** | Mixed capacity servers | Batch + API servers mixed |
| **Least Connections** | Variable request duration | Payment processing |
| **Least Response Time** | Strict latency SLAs | Private banking, trading |
| **IP Hash** | Legacy stateful apps | Avoid — use Redis instead |
| **Consistent Hashing** | Cache-warmed routing | API gateway, caching layer |

### The 4 Hard Problems

| Problem | Cause | Solution |
|---|---|---|
| **Sticky sessions** | Stateful servers | Externalise state to Redis — make servers stateless |
| **Health check blindness** | Shallow ping only | Deep health checks — DB, Redis, downstream dependencies |
| **Thundering herd on recovery** | Full traffic hit instantly | Slow start warmup — ramp traffic over 2–3 minutes |
| **Manual failover** | No automation | Route 53 health checks — auto DNS failover under 60 seconds |

### Multi-Layer LB Architecture
```
CloudFront / CDN        — Edge caching, DDoS, TLS termination
Route 53                — Global DNS, geo routing, auto failover
AWS ALB (L7)            — Per region, path routing, SSL re-encryption
AWS API Gateway         — Rate limiting, auth, request validation
AWS App Mesh / Istio    — Service mesh, mTLS, circuit breakers
EKS / Kubernetes        — HPA auto-scaling, pod health management
PgBouncer               — DB connection pooling
```

### AWS Load Balancing Services

| Service | Layer | Best For |
|---|---|---|
| **ALB** | L7 | HTTP microservices, path routing |
| **NLB** | L4 | Ultra-low latency, TCP, HFT |
| **Route 53** | DNS | Global routing, auto failover |
| **CloudFront** | CDN/Edge | Latency reduction, static caching |

### FS Rules
- **Always stateless servers** — session state in Redis
- **Deep health checks** — check DB, cache, downstream
- **SSL re-encryption** inside network — PCI-DSS compliance
- **Scheduled pre-scaling** for predictable spikes — market open, month-end
- **Automate everything** — no manual runbooks for failover

---

## 🎯 The Master Interview Framework

### For Every Design Question — Always Cover:

```
1. CLARIFY    → Functional + Non-functional requirements
2. CAP CHOICE → CP or AP — with business/regulatory justification
3. DB CHOICE  → Which DB for which component — polyglot if needed
4. CACHING    → Which pattern, TTL, invalidation, multi-layer
5. SCALING    → Load balancing algorithm, auto-scaling, health checks
6. RESILIENCE → Failover, circuit breaker, graceful degradation
7. COMPLIANCE → GDPR, PCI-DSS, FCA, data residency, encryption
```

### Your Scoring Triangle — The Habit That Makes 7 into 9
> **Choice → Technical mechanism → Risk accepted + mitigation**

---

## 📈 Your Progress Through Days 1–3

| Dimension | Day 1 Start | Day 3 End |
|---|---|---|
| Concept correctness | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Business reasoning | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Technical depth | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| Regulatory thinking | ⭐⭐ | ⭐⭐⭐⭐ |
| EM-level framing | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Polyglot thinking | ⭐⭐ | ⭐⭐⭐⭐ |

---

## 🚀 What's Coming — Days 4–7

| Day | Topic |
|---|---|
| **Day 4** | Microservices Patterns — Saga, CQRS, Event Sourcing |
| **Day 5** | Resilience Patterns — Circuit Breaker, Bulkhead, Retry, Timeout |
| **Day 6** | Kafka Deep Dive — Partitions, Consumer Groups, Exactly-Once |
| **Day 7** | Observability — Distributed Tracing, Metrics, Alerting, SLOs |

---

Ready to start **Day 4 — Microservices Patterns?** 🎯
