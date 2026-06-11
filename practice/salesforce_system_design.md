Got it. Let me build the full handbook. I'll start with the summary table across all questions, then do each deep dive one by one.

---

# Salesforce Senior Manager — System Design Interview Handbook

---

## Master Summary Table

| # | Problem | Core Challenge | Correct Approach | Key Services | Primary Tech Stack | Scale Mechanism | Critical Failure Handling | Key Interview Point |
|---|---------|---------------|-----------------|-------------|-------------------|----------------|--------------------------|---------------------|
| Q1 | Multi-Tenant CRM Data Layer | 150K orgs on shared infra, each needing custom fields and data isolation | Sparse columns + metadata mapping table. Shard by org_id via Citus | Tenant Context Middleware (in-process), Org Registry | PostgreSQL + Citus, Redis cache | hash(org_id) → shard. Citus coordinator routes queries. Rebalance by moving shards live | Read replica for analytics. Regional stacks (EU/India/US) for data residency. Per-org encryption key deletion for GDPR erasure | EAV fails on queries. Dedicated DB fails on upgrades. Sparse columns is the only viable approach at this scale |
| Q2 | Real-Time Notification System | One event → many recipients (fan-out). User connected to server A but notification arrives at server B | 5 services: Fan-out → Orchestrator → Channel Workers. Redis pub/sub for cross-server WebSocket delivery | Fan-out Service, Orchestrator, In-App/Email/Push Workers, Wait Manager | Kafka, Redis (pub/sub + registry + counters), PostgreSQL, WebSockets | Kafka partitioned by org_id. Each channel worker scales independently | Server crash → client reconnects → DB replay of missed notifications by sequence number | Redis pub/sub channel name IS the targeting — server-7 subscribes to "ws-server:server-7". Publisher publishes to that exact string. No pre-setup needed |
| Q3 | Rate Limiter for Multi-Tenant API Gateway | Noisy neighbour: one org's overuse affects all 150K orgs. Race conditions on shared counters | Three algorithms simultaneously: Token bucket (per-sec burst), Sliding window hybrid (hourly/daily), Fixed window (per-user daily) | API Gateway (evaluates all three limits per request) | Redis (Lua scripts for atomicity), separate Redis cluster | Per-org limits based on plan. Platform-wide ceiling. Redis single-threaded = atomic operations | Redis down → local in-memory fallback per API server. Approximate but protective. Never fail open | Lua scripts are critical — read-then-write without Lua creates race conditions. Two simultaneous requests both see 1 token and both pass |
| Q4 | Event-Driven Audit Log Pipeline | Atomicity: record change + audit entry must both happen or neither. Immutability: logs must never be modified. Ordering: wall clock unreliable | CDC reads WAL (catches everything including direct SQL). Hash chain for tamper detection. PostgreSQL XID for authoritative ordering | Debezium (CDC), Enrichment Service, Audit Writer Service, DLQ Monitor | Kafka, ClickHouse (append-only, INSERT grants only), Debezium | ClickHouse partitioned by month. DROP PARTITION for O(1) retention. Columnar compression 10:1 | DLQ after 3 retries with exponential backoff. Pipeline never blocked by one bad event | PostgreSQL XID = authoritative order, NOT Kafka offset (changes on DLQ replay) and NOT wall clock (unreliable across servers) |
| Q5 | Distributed Workflow Engine | Waiting days without holding resources. Crash recovery without re-executing completed steps. Thundering herd on bulk triggers | State machine + idempotency keys + outbox pattern. WAITING→READY→RUNNING handoff prevents race conditions | Trigger Service, Workflow Executor, Wait Condition Listener, Timeout Poller | Kafka, PostgreSQL (state), Redis (delay queue sorted set), Airflow | Rate limit intake (100 instances/sec per org). Bulkify loop steps (batch 500 ops). Horizontal executor scaling | Crash → Kafka redelivers → idempotency key skips completed steps → resume from last committed state | READY state is the safe handoff. WAITING→RUNNING directly causes race condition. Two executors can both claim same instance |
| Q6 | Cross-Cloud Integration Hub (MuleSoft) | 66 point-to-point integrations become unmaintainable. One API change breaks multiple integrations | Config-in-DB (not JAR per flow). Three trigger types: Event/API/Schedule. Transformation as in-process library | Intake Service, Execution Engine, Transformation Library, Connector Library, Credential Vault | Kafka, PostgreSQL, Redis (flow config cache + delay queue), AWS KMS | Kafka partitioned by org_id. Add Execution Engine instances = linear scale-out. Config update = DB change, no deployment | Transient: Redis delay queue retry. Permanent: DLQ + alert. OAuth auto-refresh 30min before expiry | Real MuleSoft uses JAR per customer + dedicated VM. We use config-in-DB for 150K orgs. JAR approach not viable at this scale |
| Q7 | File Storage System | 2GB files cannot go in DB. Network drops mid-upload. Access control on downloads. Duplicate file storage cost | S3 for bytes (content-addressed keys via SHA-256). Our DB for metadata only. Chunked upload via S3 Multipart. Pre-signed URLs for download | File Service (API), Upload Session Manager, Virus Scanner Service | S3, PostgreSQL (metadata), Redis (quota cache), Kafka (virus scan trigger) | App server is a pipe not a bucket (streams to S3, never stores locally). Pre-signed URLs offload all download bandwidth to S3 | Upload crash → session in DB tracks received chunks → client resumes from last received chunk. Virus found → QUARANTINED status | Deduplication security: isolation at metadata layer (org_id check in FILE table), not at S3 layer. Two orgs can share same S3 object safely |
| Q8 | Real-Time Analytics Pipeline | CRM DB cannot handle analytical queries. Need real-time freshness AND historical accuracy. Different query patterns need different approaches | Lambda architecture: CDC → Kafka → two paths: Stream (Redis counters today) + Batch (ClickHouse history). Serving layer merges both | CDC/Debezium, Stream Processor, Spark Batch Job, Serving Layer | Kafka, Redis (today's counters), ClickHouse (historical facts + materialised views), Spark, Airflow | Stream: micro-batch inserts to ClickHouse (never per-event). ClickHouse: columnar + partition pruning + vectorised SIMD + materialised views for high concurrency | Stream crash → Kafka offset replay → Redis INCR idempotent. Batch failure → Airflow retry → ReplacingMergeTree idempotent upsert | ClickHouse still scans rows but columnar storage + compression + SIMD = 100x faster than PostgreSQL. Materialised views for pre-aggregated high-concurrency dashboard queries |
| Q9 | Collaborative Document Editor (Quip) | Concurrent edits produce conflicts. Position-based operations become invalid after other insertions/deletions | OT (Operational Transformation): central server transforms conflicting operations. Optimistic local application. Snapshots every 100 ops | Collaboration Service (OT engine), Snapshot Service, Presence Service | PostgreSQL (operations log, snapshots), Redis (presence, WebSocket routing), WebSockets | One room per document. Operations broadcast via WebSocket. Stateless collaboration servers with shared DB | Client disconnects → reconnects → replays missed ops from DB by sequence. OT bug → periodic hash check detects divergence → full resync | Optimistic local application is critical for UX. Without it: every keystroke has 200ms latency. With it: instant local, server confirms async |
| Q10 | Authentication and SSO Platform | Validate identity on millions of API calls/second without DB hits. Long sessions without security risk. Enterprise SSO with Microsoft/Okta | JWT (RS256) self-contained tokens. Public key cached in memory on every API server. Refresh token rotation with absolute + idle expiry | Auth Service, Token Validator (in API Gateway), SSO Handler, MFA Service | PostgreSQL (users, tokens as SHA-256 hash, org SSO config), Redis (blocklist, rate limiting, MFA temp tokens), AWS KMS | JWT validation: pure CPU, 0.5ms, no network. Horizontal scale = add API servers, each validates independently | Urgent revocation: Redis blocklist by JTI. Refresh token theft: rotation detects reuse → invalidate entire token family → alert | Microsoft session cookie only readable by Microsoft. Salesforce sends browser to Microsoft with prompt=none. Browser carries cookie automatically. Microsoft reads its OWN cookie and returns code silently |
| Q12 | Batch ETL Data Pipeline | Event-driven is preferred but batch still needed for: cross-system joins, historical backfill, bulk SQL corrections bypassing CDC, nightly reconciliation | Event-driven primary pipeline (CDC → Kafka → ClickHouse). Batch supplementary. Watermark incremental extraction. Full recomputation not incremental addition | CDC Listener, Spark Transform Jobs, ClickHouse Writer, Airflow DAG, Data Quality Service | PostgreSQL read replica, Kafka, S3 parquet (batch staging), Spark, ClickHouse (ReplacingMergeTree), Airflow | Spark partitions data (128MB chunks), processes in parallel, spills to disk if needed. ClickHouse micro-batch inserts (10K rows) | Airflow retries with backoff. Data quality checks after every load. Staging table swap = never partial state in production | Q12 and Q8 share same ingestion pipeline. Difference is granularity: Q8 = pre-aggregated metrics, Q12 = raw fact rows for drill-down. Not two separate systems |
| Q13 | Search System for CRM Records | PostgreSQL ILIKE cannot use indexes, no relevance ranking, no typo tolerance. Multi-object search across Account/Contact/Opportunity/Case | Elasticsearch with inverted index. Per-org aliases over shared sharded index. Denormalise account_name/owner_name. Access control post-filter | Search Indexer Service (CDC consumer), Search Service (API), Typeahead Service | Elasticsearch, Kafka (CDC trigger for reindex), Redis (typeahead cache, result cache 60s TTL), PostgreSQL (source of truth) | 3,000 shards across N nodes. Each shard: primary + replica. Per-org Redis result cache — first user populates, next 100 benefit | ES down → circuit breaker → fall back to PostgreSQL ILIKE. Account name change → cascade reindex all linked Opportunities/Contacts | Access control cannot be pushed into Elasticsearch. Salesforce's row/field-level security too complex. Post-filter after ES returns results |
| Q14 | Feature Flag / A/B Testing | Consistent assignment (same org always same variant). Gradual rollout without disrupting existing users. Kill switch must propagate in seconds | Deterministic hashing: hash(org_id + flag_key + salt) % 100. A/B stored in DB (never switch mid-experiment). SDK fetches ALL flags at init | Flag Service, SDK (client + server), Experiment Analyst, Cache Invalidation Service | PostgreSQL (flag definitions, rules, assignments), Redis (config cache 5min, pub/sub invalidation), Kafka (sampled eval events), ClickHouse (experiment metrics) | SDK: in-memory dictionary lookup, microseconds. Flag Service: Redis cached, pure computation. No DB on hot path | SDK failure → stale cache (5min) → then defaults (always OFF). Kill switch: DB update → Redis pub/sub → 30 seconds to all SDKs | Deterministic hash: same inputs always same output. Rolling from 10%→20%: orgs in 0-9 keep feature, orgs in 10-19 newly added. No disruption |

---

Now let me do the deep dives one by one. Starting with Q1.

---

# Q1 Deep Dive — Multi-Tenant CRM Data Layer

---

### Problem Statement

```
Infosys, TCS, Wipro and 149,997 other companies
use Salesforce CRM on the SAME platform simultaneously.

Each company (called an ORG) needs:

  DATA ISOLATION:
    Infosys cannot see TCS's Opportunities.
    TCS cannot see Wipro's Contacts.
    Complete logical separation on shared physical hardware.

  CUSTOM FIELDS:
    Infosys adds "GSTIN" field to Account object.
    TCS adds "Vendor Code" to the same Account object.
    Wipro adds "Billing Region" to Account.
    All three use the same physical table.
    Each sees only their own fields.

  PERFORMANCE ISOLATION:
    TCS runs a bulk import of 500,000 records.
    This cannot slow down Infosys's users.
    Resource fairness across all orgs.

  DATA RESIDENCY:
    German bank's data must physically stay in EU. (GDPR)
    Indian company's data can stay in India.
    US company's data in Virginia.
```

---

### Why Naive Approaches Fail

```
APPROACH 1 — DEDICATED DATABASE PER ORG:

  Give Infosys their own PostgreSQL instance.
  Give TCS their own.
  150,000 separate databases.

  FAILS BECAUSE:
    Salesforce releases a bug fix → 150,000 migrations.
    One schema change → weeks of work.
    Small org (10 users) gets a whole DB → 99% waste.
    Monitoring 150,000 DB instances → operationally impossible.

APPROACH 2 — EAV (Entity Attribute Value):

  Store every field as a separate row:
  entity_id    attribute     value
  opp-1        "stage"       "Closed Won"
  opp-1        "amount"      "1500000"
  opp-1        "GSTIN"       "27AABCU9603R1ZX"

  FAILS BECAUSE:
    One Opportunity = 20 rows.
    SELECT SUM(amount) FROM opportunity → impossible on EAV.
    Reconstruct one record = fetch 20 rows + pivot.
    JOIN across objects = nightmare.
    Performance terrible at scale.

APPROACH 3 — SPARSE COLUMNS (correct solution):
  Pre-allocate columns of each type.
  Metadata table gives them meaning per org.
  Same physical column, different logical name per org.
```

---

### Core Services

```
SERVICE 1 — TENANT CONTEXT MIDDLEWARE:

  What it is:
    In-process library. NOT a separate service.
    Runs inside every app server process.
    
  What it does:
    Reads org_id from JWT on every request.
    Stores org_id in thread-local storage.
    Intercepts every DB query.
    Automatically appends: AND org_id = 'infosys'
    
  What it does NOT do:
    Does not make network calls.
    Does not query a separate service.
    Zero latency overhead (runs in-process).
    
  Also enforces governor limits:
    Tracks per-request counters.
    SOQL queries: 100 max.
    DML statements: 150 max.
    CPU time: 10 seconds max.
    Blocks further operations when limit hit.
    Returns governor limit exception.

SERVICE 2 — ORG REGISTRY (Global Control Plane):

  What it is:
    Separate lightweight service.
    Stores org routing metadata ONLY.
    Never stores customer PII.
    
  What it does:
    "Which regional stack is Infosys on?"
    infosys → India stack (Mumbai)
    german-bank → EU stack (Frankfurt)
    us-corp → US stack (Virginia)
    
    GeoDNS routes login requests to correct regional stack.
    
  What it does NOT do:
    Does not store any CRM data.
    Does not handle authentication.
    Does not process queries.

SERVICE 3 — METADATA SERVICE:

  What it is:
    Manages custom field definitions per org.
    
  What it does:
    Admin creates field "GSTIN" in Infosys org.
    Finds next available str_val_N slot.
    Inserts mapping: infosys + "GSTIN" → str_val_3.
    Caches mapping in Redis per org.
    
  On every read/write:
    Application asks: "which column is GSTIN for Infosys?"
    Metadata service: "str_val_3"
    Query uses str_val_3.
```

---

### Data Model

```
OPPORTUNITY TABLE (shared across all orgs):

  id              UUID        primary key
  org_id          UUID        ← tenant identifier, indexed
  name            VARCHAR     standard field
  stage           VARCHAR     standard field
  amount          DECIMAL     standard field
  close_date      DATE        standard field
  owner_id        UUID        standard field
  account_id      UUID        standard field
  created_at      TIMESTAMP
  updated_at      TIMESTAMP
  is_deleted      BOOLEAN
  
  str_val_0       VARCHAR     custom field slot
  str_val_1       VARCHAR     custom field slot
  ...
  str_val_100     VARCHAR     100 string slots
  
  num_val_0       DECIMAL     custom field slot
  ...
  num_val_50      DECIMAL     50 numeric slots
  
  date_val_0      DATE
  ...
  date_val_20     DATE        20 date slots

FIELD_METADATA TABLE:
  org_id          UUID
  object_type     VARCHAR     "opportunity"
  field_name      VARCHAR     "GSTIN"
  column_name     VARCHAR     "str_val_3"
  data_type       ENUM        STRING/NUMBER/DATE/BOOLEAN
  is_required     BOOLEAN
  created_at      TIMESTAMP

ORG TABLE:
  id              UUID
  name            VARCHAR     "Infosys"
  plan            ENUM        DEVELOPER/PROFESSIONAL/ENTERPRISE
  region          ENUM        US/EU/IN/AU
  my_domain       VARCHAR     "infosys.salesforce.com"
  created_at      TIMESTAMP
  is_active       BOOLEAN
```

---

### Data Flow

```
STEP 1 — Request arrives:
  Ravi (Infosys) calls: GET /api/opportunities
  JWT decoded: org_id = "infosys-uuid"
  Tenant Context Middleware stores org_id in thread-local.

STEP 2 — Routing:
  Org Registry: "infosys is on India stack"
  GeoDNS already routed request to Mumbai.
  (This happens at DNS level before request arrives)

STEP 3 — Query execution:
  Application code:
  SELECT * FROM opportunity WHERE owner_id = 'ravi-uuid'
  
  Tenant Context Middleware intercepts:
  SELECT * FROM opportunity 
  WHERE owner_id = 'ravi-uuid'
  AND org_id = 'infosys-uuid'    ← automatically injected
  
  Citus coordinator:
  hash('infosys-uuid') % 32 = shard 7
  Routes to Worker Node 2 (owns shard 7)
  
  Worker Node 2 executes query.
  Returns results.

STEP 4 — Custom field resolution:
  Result has: str_val_3 = "27AABCU9603R1ZX"
  
  Metadata service (Redis cache hit):
  "For infosys, str_val_3 = GSTIN"
  
  Application maps: GSTIN = "27AABCU9603R1ZX"
  Returns to Ravi with proper field name.

STEP 5 — Governor limit check:
  This was query 47 of allowed 100.
  Counter incremented in thread-local.
  Request completed normally.
  
  If query 101:
  Middleware throws: QueryLimitExceededException
  HTTP 429 returned.
```

---

### Sharding Deep Dive

```
SETUP:
  1 Coordinator node (metadata only, no data)
  N Worker nodes (each a full PostgreSQL server)

SHARD ASSIGNMENT:
  CREATE TABLE opportunity DISTRIBUTE BY org_id
  
  Citus creates 32 shards (configurable).
  Assigns round-robin to workers:
  Shard 0  → Worker 1
  Shard 1  → Worker 2
  Shard 2  → Worker 3
  Shard 3  → Worker 1
  ...
  
  Coordinator stores mapping:
  shard 7 → Worker 2
  shard 15 → Worker 3
  ...

QUERY ROUTING:
  Query for org_id = 'infosys':
  hash('infosys-uuid') % 32 = 7
  Coordinator: shard 7 is on Worker 2
  Forward query to Worker 2.
  Single shard query. Fast.

ADDING A NEW WORKER:
  New Worker 4 added.
  Target: each worker should have 32/4 = 8 shards.
  Workers 1,2,3 each give 2-3 shards to Worker 4.
  
  Per shard migration:
    Copy shard data from source to destination.
    Writes go to BOTH during copy.
    Short lock (milliseconds) at switchover.
    Coordinator metadata updated.
    Traffic shifts to new worker.
    Old data deleted from source.
  
  System stays online throughout.

DEDICATED NODE FOR ONE ORG:
  Large enterprise customer (e.g. major bank).
  Mark Worker 5 as shouldhaveshards=false.
  Manually move bank's shard to Worker 5 only.
  No other org's data on Worker 5.
  Bank gets dedicated CPU/RAM/disk.
  No noisy neighbours.
```

---

### Failure Handling

```
CRM DB PRIMARY FAILS:
  PostgreSQL streaming replication to read replica.
  Automatic failover (using Patroni or AWS RDS).
  Replica promoted to primary.
  Brief downtime (30-60 seconds).
  All transactions after last WAL sync may be lost.
  (Acceptable: Salesforce's RPO = seconds)

READ REPLICA OVERLOADED (analytics queries):
  Analytical queries run on read replica.
  Never on primary.
  If read replica slow:
  Retry on different read replica.
  Primary never touched for analytics.

DATA RESIDENCY FAILURE:
  EU stack goes down.
  European customers cannot access Salesforce.
  Non-European customers unaffected (on different stacks).
  EU stack restored independently.
  No cross-region data movement needed.

GDPR RIGHT TO ERASURE:
  User requests data deletion.
  Each user has a unique encryption key in AWS KMS.
  All their data encrypted with that key.
  
  Delete the key from KMS.
  All their data becomes undecryptable garbage.
  Instant logical deletion.
  No need to find and delete every row.
  Key deletion = permanent erasure.
```

---

### Tech Stack Justification

| Technology | Why This, Not Something Else |
|-----------|------------------------------|
| PostgreSQL | ACID transactions, mature, excellent for OLTP, row-level security, streaming replication |
| Citus | Sharding extension for PostgreSQL — same SQL syntax, transparent sharding, no application changes |
| Redis | Metadata mapping cache — field name lookups on every request must be sub-millisecond |
| AWS KMS | Per-org encryption keys — hardware security module, key never touches disk, instant key deletion for GDPR |
| Sparse columns over EAV | SQL aggregations work normally, indexes work, JOINs work — EAV breaks all of these |
| Shard by org_id not record_id | All queries include org_id — single shard access. Record_id sharding = scatter-gather across all shards |

---

### Interview One-Paragraph Summary

> "The multi-tenant CRM uses a shared-schema PostgreSQL database extended with Citus for sharding. Custom fields per org are handled via sparse pre-allocated columns — str_val_0 through str_val_100 — with a metadata mapping table translating logical field names to physical column names per org, avoiding both the upgrade nightmare of dedicated databases and the query nightmare of EAV. Every table has org_id as the shard key, and Tenant Context Middleware — an in-process library, not a service — automatically injects org_id into every query and enforces governor limits, so even if a developer forgets isolation, the middleware enforces it. Sharding is handled by Citus: the coordinator hashes org_id to determine the shard, routes to the correct worker, and handles rebalancing when new workers are added by live-migrating shards with a millisecond switchover lock. Data residency for GDPR is handled by separate regional stacks with a Global Control Plane storing only routing metadata, and per-user encryption keys in AWS KMS mean that GDPR erasure is as simple as deleting the key — the data becomes permanently undecryptable without touching individual rows."

---

### Key Design Decisions Table

| Decision | Choice Made | Rejected Alternative | Why |
|----------|------------|---------------------|-----|
| Custom fields | Sparse columns + metadata | EAV | SQL works normally on sparse columns. EAV breaks aggregations and JOINs |
| Multi-tenancy | Shared schema + org_id isolation | Dedicated DB per org | 150K migrations on each release is operationally impossible |
| Sharding | Citus, shard by org_id | Application-level sharding | Citus handles routing transparently. org_id = single shard per query |
| Isolation enforcement | Tenant Context Middleware (in-process) | Separate gateway service | In-process = zero network overhead. Every query automatically protected |
| GDPR erasure | Per-user KMS key deletion | Delete all rows | Key deletion is instant and permanent. Row deletion requires finding every record |
| Analytics load | Read replica | Query primary | Primary handles all writes. Analytics on replica = zero impact on live users |

---

Shall I continue with Q2 deep dive?

# Q2 Deep Dive — Real-Time Notification System

---

### Problem Statement

```
Sales manager Priya has Salesforce open in her browser.
Ravi just closed a deal with TCS worth ₹1.5 Crore.

Priya should see immediately — without refreshing:
  "Ravi Kumar closed TCS deal — ₹1.5 Crore"

CEO wants an email for any deal over ₹1 Crore.
Slack #wins channel should get a message.
Ravi himself should see a task: "Send contract to legal"

All of this from ONE event: Opportunity stage changed.
One event → many recipients → many channels.
This is the FAN-OUT problem.

At Salesforce scale:
  150,000 orgs × multiple events per second
  = millions of notifications per second
  Each notification potentially going to
  multiple recipients via multiple channels.
```

---

### Why Naive Approaches Fail

```
APPROACH 1 — POLLING (client asks server repeatedly):

  Browser asks every 5 seconds:
  "Do I have new notifications?"
  
  FAILS BECAUSE:
    150,000 orgs × 1,000 users each = 150M users.
    150M users polling every 5 seconds
    = 30M requests/second just for polling.
    99.9% of polls return "nothing new".
    Massive waste of resources.
    Still has 5-second delay (not real-time).

APPROACH 2 — SYNCHRONOUS FAN-OUT:

  When deal closes:
  For each recipient: deliver notification synchronously.
  100 recipients × 10ms each = 1 second blocking.
  
  FAILS BECAUSE:
    Ravi's "Save" button takes 1 second.
    If any recipient's delivery fails: whole save fails.
    Cannot block CRM writes for notification delivery.

APPROACH 3 — SINGLE NOTIFICATION SERVICE (does everything):

  One service: receives event, finds recipients,
  checks prefs, delivers via all channels.
  
  FAILS BECAUSE:
    Email is slow (external API, 200ms).
    WebSocket is fast (local, 1ms).
    Email slowness blocks WebSocket delivery.
    One service cannot scale email and WebSocket
    delivery independently.
    Single point of failure.
```

---

### Core Services — 5 Services, Each Does ONE Thing

```
SERVICE 1 — FAN-OUT SERVICE:

  Input:  Raw CRM event from Kafka
  Output: List of (user_id, notification_content)
          published to Kafka "notifications.fanout"
  
  Responsibilities:
    Load ORG_NOTIFICATION_POLICIES for this event type.
    Evaluate each policy:
      RECORD_TEAM: query who is on this deal's team
      ROLE/HIERARCHY: walk up reporting chain N levels
      EXPLICIT: users who subscribed to this event
    Build notification content per recipient.
    Deduplicate recipients appearing in multiple policies.
  
  Does NOT:
    Look at channels (email/push/in-app).
    Store anything to DB.
    Deliver anything.
  
  Why separate:
    Pure computation. Stateless.
    Can scale independently.
    Failure here: no notification sent.
    Does not affect CRM writes.

SERVICE 2 — ORCHESTRATOR SERVICE:

  Input:  (user_id, notification_content) from Kafka
  Output: Routed to channel-specific Kafka topics
  
  Responsibilities:
    Persist notification record to PostgreSQL DB.
    Increment unread counter in Redis: INCR "unread:user-id"
    Load USER_NOTIFICATION_PREFS for this user.
    Check org policy channel overrides.
    Publish to correct channel topics:
      Kafka "notifications.inapp"
      Kafka "notifications.email"
      Kafka "notifications.push"
  
  Does NOT:
    Deliver anything to end user.
    Make outbound HTTP calls.
  
  Why persist first:
    Notification in DB = never lost even if delivery fails.
    On reconnect: client pulls missed notifications from DB.

SERVICE 3 — IN-APP DELIVERY WORKER:

  Input:  (user_id, notification) from Kafka "notifications.inapp"
  Output: Notification pushed to user's browser via WebSocket
  
  Responsibilities:
    SMEMBERS "ws:{user_id}" → get server names user connected to.
    PUBLISH "ws-server:{server-name}" {user_id, notification}
    Redis delivers to that server's subscription.
    That server pushes to user's open socket(s).
  
  Does NOT:
    Check preferences (Orchestrator already did this).
    Make routing decisions.
    Connect directly to WebSocket servers.

SERVICE 4 — EMAIL DELIVERY WORKER:

  Input:  (user_id, notification) from Kafka "notifications.email"
  Output: Email sent via SendGrid/SES
  
  Responsibilities:
    Load user email address from DB.
    Format email (HTML template).
    Call email provider API.
    Handle delivery failures + retry.
  
  Does NOT:
    Decide whether to send (Orchestrator decided).
    Handle other channels.

SERVICE 5 — PUSH/SMS DELIVERY WORKERS:
  Same pattern as Email Worker.
  Each channel: independent service, independent scaling.
```

---

### Org Notification Policies

```
Admin configures default rules for the whole org.
This is what answers: "who gets notified by default?"

ORG_NOTIFICATION_POLICY table:

org_id          UUID
event_type      VARCHAR     "OPPORTUNITY_CLOSED"
condition       JSONB       { amount: { gt: 1000000 } }
                            null = always apply
recipient_type  ENUM        RECORD_OWNER
                            RECORD_TEAM
                            ROLE_IN_HIERARCHY
                            SPECIFIC_USER
                            EXTERNAL_CHANNEL
recipient_config JSONB      {
                              levels: 2,        ← for hierarchy
                              role: "Manager"
                            }
                            OR
                            { user_id: "ceo-uuid" }
                            OR
                            { type: "SLACK", channel: "#wins" }
channel_override ENUM       null (use user pref)
                            IN_APP (force regardless of pref)
                            EMAIL (force regardless of pref)
priority        INT         evaluation order

Example policies for Infosys:

Policy 1: OPPORTUNITY_CLOSED, always, RECORD_TEAM, IN_APP
→ Always notify deal team members in-app

Policy 2: OPPORTUNITY_CLOSED, amount>1Cr, ROLE_IN_HIERARCHY
          levels=2, EMAIL
→ Email managers 2 levels up for big deals

Policy 3: OPPORTUNITY_CLOSED, amount>1Cr, EXTERNAL_CHANNEL
          Slack #wins, SLACK
→ Post to Slack for big deals
```

---

### User Notification Preferences

```
USER_NOTIFICATION_PREFS table:

user_id     UUID
event_type  VARCHAR     "OPPORTUNITY_CLOSED"
channel     ENUM        IN_APP / EMAIL / PUSH / SMS
is_enabled  BOOLEAN

Example for Priya:
  OPPORTUNITY_CLOSED  IN_APP  true
  OPPORTUNITY_CLOSED  EMAIL   true
  OPPORTUNITY_CLOSED  PUSH    false
  CASE_CREATED        IN_APP  true
  CASE_CREATED        EMAIL   false

RELATIONSHIP BETWEEN POLICY AND PREFS:
  Policy channel_override = IN_APP:
    Force in-app regardless of user pref.
    User cannot opt out of org-mandated notifications.
  
  Policy channel_override = null:
    Check user pref.
    If user opted out: respect their choice.
  
  Policy wins over pref when override is set.
  User pref wins when no override.
```

---

### WebSocket Cross-Server Delivery — Full Mechanics

```
THE PROBLEM:
  100 WebSocket servers handle browser connections.
  Priya's browser connected to Server-7.
  In-App Worker processes notification on Server-12.
  Server-12 cannot reach Priya's socket directly.

SOLUTION: Redis Pub/Sub as intermediary.

SETUP (each server on startup):
  Server-7 reads its Kubernetes pod name:
  MY_POD_NAME = "ws-server-7"  ← from k8s env var
  
  Opens connection to Redis.
  SUBSCRIBE "ws-server:ws-server-7"
  Now listening. Blocking. Forever.
  
  Server-12: SUBSCRIBE "ws-server:ws-server-12"
  Server-23: SUBSCRIBE "ws-server:ws-server-23"
  
  Redis routing table (internal):
  "ws-server:ws-server-7"  → Server-7's connection
  "ws-server:ws-server-12" → Server-12's connection

PRIYA OPENS SALESFORCE (registration):
  Browser establishes WebSocket to Server-7.
  Server-7 registers in Redis:
  SADD "ws:priya-uuid" "ws-server-7"
  TTL: 24 hours (auto-cleanup if server crashes)
  
  Server-7 stores locally:
  HashMap { "priya-uuid": [socket-object-1] }

PRIYA OPENS SECOND TAB:
  Browser creates new WebSocket to Server-7 (same server).
  Server-7 adds socket:
  HashMap { "priya-uuid": [socket-1, socket-2] }
  Redis SET already has "ws-server-7". No change needed.

PRIYA OPENS THIRD TAB (lands on Server-23):
  Server-23: SADD "ws:priya-uuid" "ws-server-23"
  Redis SET: { "ws-server-7", "ws-server-23" }
  
  Server-23 HashMap: { "priya-uuid": [socket-3] }

IN-APP WORKER DELIVERS TO PRIYA:

  Step 1: SMEMBERS "ws:priya-uuid"
          → ["ws-server-7", "ws-server-23"]
  
  Step 2: PUBLISH "ws-server:ws-server-7"
          '{"user_id":"priya","notification":{...}}'
          
          PUBLISH "ws-server:ws-server-23"
          '{"user_id":"priya","notification":{...}}'
  
  Step 3: Redis delivers to Server-7's subscription.
          Server-7 receives. Parses user_id = "priya".
          HashMap lookup: [socket-1, socket-2]
          Push to both. Both tabs get notification.
          
          Redis delivers to Server-23's subscription.
          Server-23: HashMap lookup: [socket-3]
          Push to socket-3. Third tab gets notification.

WHY REDIS PUB/SUB NOT KAFKA HERE:
  Kafka: persistent, ordered, consumer groups.
  Redis pub/sub: ephemeral, fire-and-forget, instant.
  
  If Server-7 is down: message dropped.
  That's CORRECT. Server-7 down = Priya's socket dead.
  No point delivering to dead server.
  Priya will reconnect and pull from DB.
  Fire-and-forget is the right semantic here.
  
  Latency: Redis pub/sub < 1ms. Kafka adds 5-50ms.
  For WebSocket delivery: every ms matters for UX.

IN-APP WORKER CONNECTIONS:
  ONE connection to Redis (already exists).
  NEVER connects directly to WebSocket servers.
  No N×M connection problem.
```

---

### Unread Counter

```
Every user has a badge count (red circle with number).

WRONG approach:
  SELECT COUNT(*) FROM notification
  WHERE user_id = 'priya' AND is_read = false
  
  150M users × every page load = DB overwhelmed.

CORRECT approach — Redis counter:
  "unread:priya-uuid" = 3
  
  New notification arrives:
  INCR "unread:priya-uuid"
  → atomic, no race condition, microseconds
  
  User reads a notification:
  DECR "unread:priya-uuid"
  
  User clicks "Mark all read":
  SET "unread:priya-uuid" 0
  
  Page loads:
  GET "unread:priya-uuid" → instant, no DB
  
  Redis counter IS the source of truth for unread count.
  DB has individual notification records for detail.
  Counter for the badge. DB for the list.
```

---

### Crash Recovery

```
SCENARIO: Server-7 crashes.
Priya's sockets drop. She gets disconnected.

WHAT HAPPENS:

  1. Browser detects WebSocket close immediately.
  
  2. JavaScript reconnects automatically.
     May land on Server-23 this time.
  
  3. Server-23 registers Priya:
     SADD "ws:priya-uuid" "ws-server-23"
     
     Old "ws-server-7" entry: Server-7 had TTL on it.
     Or: Server-7's shutdown hook cleaned it up.
     Or: TTL expires after 24 hours automatically.
  
  4. Client sends: "I last saw notification sequence 47"
  
  5. Server-23 queries DB:
     SELECT * FROM notification
     WHERE user_id = 'priya'
     AND sequence > 47
     AND is_read = false
     ORDER BY sequence ASC
     
     Returns all missed notifications.
  
  6. Client receives and displays missed notifications.
     Nothing lost.

WHY THIS WORKS:
  Orchestrator persists every notification to DB
  BEFORE attempting WebSocket delivery.
  DB is the durable backup.
  WebSocket is the fast path.
  If fast path fails: DB path recovers everything.
```

---

### Data Model

```
NOTIFICATION table:
  id              UUID
  org_id          UUID
  recipient_id    UUID        user who receives it
  event_type      VARCHAR     "OPPORTUNITY_CLOSED"
  title           VARCHAR     "Ravi closed TCS deal"
  body            TEXT        detailed message
  related_type    VARCHAR     "Opportunity"
  related_id      UUID        opp-uuid (link to record)
  sequence        BIGINT      monotonically increasing per user
  is_read         BOOLEAN     default false
  read_at         TIMESTAMP
  created_at      TIMESTAMP
  delivered_at    TIMESTAMP   null until delivered

ORG_NOTIFICATION_POLICY table:
  (described above)

USER_NOTIFICATION_PREFS table:
  (described above)

DOCUMENT_SESSION (WebSocket presence):
  user_id         UUID
  server_name     VARCHAR     "ws-server-7"
  connected_at    TIMESTAMP
  last_seen_at    TIMESTAMP   updated by heartbeat
```

---

### Data Flow End to End

```
Ravi saves Opportunity → stage = Closed Won

  │
  ▼
CRM Record Service:
  Saves to PostgreSQL.
  Publishes to Kafka "crm.events":
  { event_type: OPPORTUNITY_CLOSED,
    org_id: infosys, opp_id: opp-123,
    amount: 1500000, owner_id: ravi }

  │
  ▼
FAN-OUT SERVICE:
  Reads from Kafka "crm.events".
  Loads Infosys org notification policies.
  
  Policy 1 (deal team, in-app):
    Load deal team: [Ravi, Priya, Legal]
  Policy 2 (amount>1Cr, email managers 2 levels):
    Ravi's manager = Priya.
    Priya's manager = VP-Sales.
  Policy 3 (amount>1Cr, Slack #wins):
    External channel.
  
  Deduplicates: Priya appears in policy 1 and 2.
  Merges: Priya gets IN_APP + EMAIL.
  
  Publishes to Kafka "notifications.fanout":
  { user: Ravi,     content: "You closed TCS deal" }
  { user: Priya,    content: "Ravi closed TCS deal" }
  { user: Legal,    content: "Ravi closed TCS deal" }
  { user: VP-Sales, content: "Ravi closed TCS deal" }
  { channel: Slack, target: "#wins", content: "..." }

  │
  ▼
ORCHESTRATOR SERVICE (for Priya):
  Reads { user: Priya, content: "Ravi closed TCS deal" }
  
  INSERT notification to DB (sequence = 48).
  INCR "unread:priya-uuid" → 4.
  
  Load Priya's prefs:
    IN_APP: enabled (policy forces it anyway).
    EMAIL: enabled (policy forces it for amount>1Cr).
  
  Publish to Kafka "notifications.inapp":
    { user: priya, sequence: 48, content: "..." }
  Publish to Kafka "notifications.email":
    { user: priya, sequence: 48, content: "..." }

  │
  ├────────────────────────────────────────────┐
  ▼                                            ▼
IN-APP WORKER:                           EMAIL WORKER:
  SMEMBERS "ws:priya" → [server-7]         Load priya@infosys.com
  PUBLISH "ws-server:server-7" {...}       Format HTML email
  Redis → Server-7 subscription           Call SendGrid API
  Server-7 → Priya's socket(s)            Email delivered
  Priya sees notification instantly.
  Total time from save: < 500ms.
```

---

### Tech Stack Justification

| Technology | Why This | Not That |
|-----------|----------|---------|
| Kafka between services | Durable, replay on crash, natural backpressure, independent scaling | Direct HTTP calls — tight coupling, no retry, cascading failures |
| Redis pub/sub for WebSocket | Sub-millisecond, fire-and-forget correct semantic, no pre-setup needed | Kafka — adds 5-50ms latency, persistence not needed here |
| Redis INCR for unread count | Atomic, no race condition, microseconds | DB COUNT query — too slow at 150M users scale |
| 5 separate services | Each scales independently, email slowness cannot block in-app | One monolithic notification service — email blocks WebSocket |
| DB persistence before delivery | Never lose a notification, reconnect recovery | DB write after delivery — delivery failure = notification lost forever |
| WebSocket over polling | Server pushes instantly, no wasted requests | Polling — 99.9% of polls find nothing, massive waste |

---

### Failure Handling

| Failure | Impact | Recovery |
|---------|--------|---------|
| Fan-out service crashes | No notifications sent for that event | Kafka offset not committed → reprocessed on restart |
| Orchestrator crashes | Notification not persisted or routed | Kafka redelivers → idempotency check (sequence exists?) → skip or process |
| In-app worker crashes | WebSocket delivery missed | Kafka redelivers → re-publishes to Redis → delivered |
| WebSocket server crashes | Connected users disconnected | Clients reconnect → send last sequence → DB replays missed notifications |
| Redis down | Unread counts stale, WebSocket delivery fails | Fall back: deliver email only. In-app delivery skipped until Redis recovers |
| Email provider down | Emails not delivered | Kafka retains message → retry with backoff → delivered when provider recovers |

---

### Interview One-Paragraph Summary

> "The notification system separates concerns into five services: Fan-out determines WHO gets notified by evaluating org notification policies against the CRM event — policies define recipients by role, record team membership, or explicit subscription with optional channel overrides; Orchestrator determines HOW they're notified by loading user preferences, persisting the notification to DB first (so it's never lost), incrementing the Redis unread counter atomically, then routing to channel-specific Kafka topics; and independent Channel Workers (in-app, email, push) each do nothing except delivery with zero routing decisions. Real-time in-browser delivery uses WebSockets: each server subscribes to its own named Redis channel on startup, the connection registry stores which servers a user is connected to as a Redis SET, and the in-app worker publishes to the exact server name — Redis delivers only to that subscriber, solving the cross-server problem without any direct server-to-server connections. If a server crashes, clients reconnect and pull missed notifications from the DB using their last known sequence number, making the WebSocket fast path and the DB durable backup complementary rather than redundant."

---

### Key Design Decisions Table

| Decision | Choice | Rejected | Why |
|----------|--------|---------|-----|
| Fan-out vs direct delivery | Fan-out service determines recipients first | Deliver directly in CRM save path | CRM save cannot block on notification fan-out |
| Cross-server WebSocket | Redis pub/sub with named channels | Direct server-to-server connections | Named channels = targeted delivery, no N×M connections |
| Unread counter | Redis INCR | DB COUNT query | Atomic, sub-millisecond, no race conditions |
| Channel separation | Independent workers per channel | One worker handles all channels | Email slowness cannot block in-app delivery |
| Persistence timing | DB write BEFORE delivery attempt | DB write after delivery | Delivery failure = notification still in DB = recoverable |
| WebSocket vs polling | WebSocket (server push) | Polling | Polling wastes 99.9% of requests finding nothing |

---

Shall I continue with Q3 deep dive?

# Q3 Deep Dive — Rate Limiter for Multi-Tenant API Gateway

---

### Problem Statement

```
Salesforce exposes APIs that all 150,000 orgs call.
Every CRM operation — read, write, search — is an API call.

THREE REASONS WE NEED RATE LIMITING:

REASON 1 — NOISY NEIGHBOUR:
  Infosys developer writes a buggy script.
  Script calls Salesforce API in infinite loop.
  50,000 requests per second from one org.
  
  Without rate limiting:
    All 50,000 hit the database.
    DB CPU at 100%.
    ALL other 149,999 orgs experience slowness.
    TCS, Wipro, everyone affected by one bad actor.
  
  With rate limiting:
    Infosys hits their limit at request 1,001.
    Returns HTTP 429 Too Many Requests.
    Other orgs: completely unaffected.

REASON 2 — PLAN ENFORCEMENT:
  Developer plan: 1,000 calls/day (free tier).
  Enterprise plan: 500,000 calls/day.
  
  Without rate limiting:
    Free tier user consumes same resources as Enterprise.
    No way to differentiate plans.
    Revenue model collapses.

REASON 3 — PLATFORM PROTECTION:
  Even legitimate traffic can overload the platform.
  Flash mob: 10,000 orgs all run batch jobs at midnight.
  Platform-wide ceiling ensures total load stays manageable.
```

---

### Why Naive Approaches Fail

```
APPROACH 1 — DATABASE COUNTER:

  Every request:
    UPDATE rate_limit SET count = count + 1
    WHERE org_id = 'infosys' AND window = 'current'
    
    SELECT count FROM rate_limit
    WHERE org_id = 'infosys' AND window = 'current'
    
    If count > limit: reject.
  
  FAILS BECAUSE:
    Two DB queries per API call.
    At 100,000 requests/second:
    200,000 DB queries/second just for rate limiting.
    Rate limiter kills the DB it's trying to protect.

APPROACH 2 — SINGLE COUNTER PER ORG (fixed window):

  Counter "infosys:hour-bucket" incremented each request.
  Reset at top of every hour.
  
  FAILS BECAUSE (boundary attack):
    Limit: 1,000 requests/hour.
    Infosys sends 1,000 at 10:59am. All allowed.
    Window resets at 11:00am.
    Infosys sends 1,000 at 11:01am. All allowed.
    
    2,000 requests in 2 minutes.
    Limit effectively doubled at boundary.
    Platform can receive 2× expected load.

APPROACH 3 — SLIDING WINDOW LOG:

  Store timestamp of every request.
  Count requests in last 60 minutes.
  
  FAILS BECAUSE:
    1,000 requests/hour = 1,000 timestamps stored per org.
    150,000 orgs = 150 million timestamps in memory.
    Memory cost unacceptable.
```

---

### The Three Algorithms — When and Why

---

#### Algorithm 1 — Token Bucket (Per-Second Burst Control)

```
CONCEPT:
  A bucket holds tokens.
  Each token = permission for one request.
  Tokens refill at a constant rate.
  Request arrives: consume one token if available.
  Bucket empty: reject request (429).

CONCRETE EXAMPLE:
  Bucket capacity: 100 tokens (max burst allowed).
  Refill rate: 10 tokens/second.
  
  Initial state: bucket full = 100 tokens.
  
  Infosys sends 80 requests in 1 second:
    80 tokens consumed.
    20 tokens remain.
    All 80 requests allowed.
    
  Infosys sends 30 more requests immediately:
    20 tokens available.
    20 requests allowed.
    10 requests rejected (429).
  
  After 5 seconds of no requests:
    5 × 10 = 50 tokens refilled.
    Bucket has 70 tokens again.

WHY TOKEN BUCKET FOR PER-SECOND:
  Allows BURST — legitimate spikes absorbed.
  Infosys normally sends 5 req/sec.
  Suddenly sends 80 req/sec for 2 seconds.
  Bucket had 90 tokens saved up.
  All 80 absorbed. No false rejections.
  
  Without burst allowance:
  Any momentary spike = 429 errors.
  Bad user experience for legitimate usage.

REDIS IMPLEMENTATION:
  Two values per org:
  "tokens:infosys"      = 87.5     ← current tokens (float)
  "last_refill:infosys" = 1705123456  ← unix timestamp
  
  On each request (Lua script — atomic):
  
  local tokens = redis.call('GET', 'tokens:infosys')
  local last   = redis.call('GET', 'last_refill:infosys')
  local now    = tonumber(ARGV[1])
  local rate   = tonumber(ARGV[2])   -- 10 tokens/sec
  local cap    = tonumber(ARGV[3])   -- 100 max tokens
  
  -- Refill based on elapsed time
  local elapsed = now - tonumber(last)
  local new_tokens = math.min(cap, tokens + elapsed * rate)
  
  if new_tokens >= 1 then
    -- Allow: consume one token
    redis.call('SET', 'tokens:infosys', new_tokens - 1)
    redis.call('SET', 'last_refill:infosys', now)
    return 1  -- allowed
  else
    return 0  -- rejected
  end
  
  WHY LUA SCRIPT:
    Entire check-and-update is ONE atomic operation.
    Redis is single-threaded for Lua execution.
    No other command runs while Lua executes.
    Impossible for two simultaneous requests to both
    read "1 token" and both get allowed.
    Without Lua: race condition guaranteed at scale.
```

---

#### Algorithm 2 — Sliding Window Counter (Hourly/Daily Limits)

```
CONCEPT:
  Approximate the count of requests in a sliding window.
  Use only TWO counters: current window + previous window.
  Weighted average gives accurate approximation.

THE BOUNDARY PROBLEM IT SOLVES:
  Fixed window: 1,000 req/hour.
  10:59am: 1,000 requests. Allowed (in current window).
  11:00am: window resets. Counter = 0.
  11:01am: 1,000 requests. Allowed (new window).
  
  2,000 requests in 2 minutes. Limit bypassed.
  Platform sees double expected load at boundary.

HOW SLIDING WINDOW HYBRID WORKS:

  Two Redis counters per org per window size:
  "sw:infosys:hour:current"  = 450  ← requests this hour
  "sw:infosys:hour:previous" = 800  ← requests last hour
  
  User makes a request at 10:45am.
  (45 minutes into current window)
  
  elapsed_fraction = 45/60 = 0.75
  
  estimated_count = 
    previous_window × (1 - elapsed_fraction)
    + current_window
    
  = 800 × (1 - 0.75) + 450
  = 800 × 0.25 + 450
  = 200 + 450
  = 650
  
  If limit = 1,000: 650 < 1,000 → ALLOW.
  Increment current counter.

WHAT THIS APPROXIMATION MEANS:
  Not exact. But close enough.
  Error margin: at most 0-1% over the limit.
  Acceptable for rate limiting purposes.
  
  Memory cost: 2 integers per org per window size.
  vs Sliding Window Log: 1,000+ timestamps per org.
  1,000× more memory efficient.

WINDOW ROTATION:
  At top of each hour:
    "previous" ← value of "current"
    "current"  ← reset to 0
    
  TTL on both keys: 2× window size.
  Auto-cleanup if org goes inactive.
```

---

#### Algorithm 3 — Fixed Window (Per-User Daily Limits)

```
CONCEPT:
  Simple counter per user per day.
  Reset at midnight.

REDIS IMPLEMENTATION:
  Key: "fw:infosys:ravi:2024-01-15" = 47
  
  On each request:
  INCR "fw:infosys:ravi:2024-01-15"
  → returns new count atomically
  
  If new count > daily_limit:
    DECR (undo the increment)
    Return 429.
  
  Key TTL: set to expire at midnight of that date.
  Auto-resets daily. No cleanup needed.

WHY FIXED WINDOW IS OK FOR DAILY LIMITS:
  Boundary attack: 2× limit at midnight.
  At daily granularity: this means someone could get
  2,000 requests instead of 1,000 at midnight.
  
  Acceptable trade-off:
    Daily limits are for compliance/billing purposes.
    Small boundary violation not harmful.
    Simplicity of fixed window worth it.
  
  The boundary attack matters most for short windows
  (seconds, minutes) where doubling is impactful.
  For daily windows: 2× at midnight is negligible.
```

---

### All Three Run Simultaneously

```
Every API request is checked against ALL THREE:

1. Token bucket (per-second):    Is burst limit exceeded?
2. Sliding window (per-hour):    Is hourly limit exceeded?
3. Fixed window (per-user/day):  Is daily limit exceeded?

Request rejected if ANY ONE is exceeded.

Example: Enterprise org, 100 req/sec limit:

  10:45am, request arrives:
  
  Check 1 (token bucket): 87 tokens available → ALLOW
  Check 2 (sliding window hourly): 4,200/50,000 → ALLOW
  Check 3 (fixed window daily): 47,000/500,000 → ALLOW
  
  All three pass → request allowed.
  
  10:45am, 101st request in one second:
  
  Check 1 (token bucket): 0 tokens → REJECT → 429
  
  Stop checking. Return 429 immediately.
  Don't even check hourly/daily.
  First failure = immediate rejection.

PLAN TIER LIMITS:

  DEVELOPER (free):
    Token bucket:     5 req/sec, burst 20
    Sliding window:   1,000 req/hour
    Fixed window:     10,000 req/day
  
  PROFESSIONAL:
    Token bucket:     25 req/sec, burst 100
    Sliding window:   10,000 req/hour
    Fixed window:     100,000 req/day
  
  ENTERPRISE:
    Token bucket:     100 req/sec, burst 500
    Sliding window:   50,000 req/hour
    Fixed window:     500,000 req/day

  Limits loaded from DB at org login.
  Cached in Redis per org (1 hour TTL).
  Plan upgrade: invalidate cache immediately.
```

---

### Redis Atomicity — Why It's Critical

```
THE RACE CONDITION WITHOUT ATOMICITY:

  Two requests arrive simultaneously for Infosys.
  Both on different API servers.
  Token bucket has 1 token remaining.
  
  Server A reads tokens: 1
  Server B reads tokens: 1  ← same value, same time
  
  Server A: 1 >= 1 → ALLOW. Sets tokens = 0.
  Server B: 1 >= 1 → ALLOW. Sets tokens = 0.
  
  Both requests allowed. Limit bypassed.
  At 100,000 requests/second: this happens constantly.

WHY REDIS SOLVES THIS:
  Redis is single-threaded for command execution.
  Lua script = one atomic unit.
  
  While Server A's Lua script runs:
  NO other command can execute on Redis.
  Server B's Lua waits.
  
  Server A: reads 1 token, sets 0. Script complete.
  Server B: reads 0 tokens. Returns rejected.
  
  One allowed. One rejected. Correct.

WHY NOT A DISTRIBUTED LOCK:
  Lock → Redis operation → unlock = 3 round trips.
  Lua script = 1 round trip (atomic).
  3× faster. No deadlock risk. Simpler.
```

---

### Distributed Rate Limiting Across API Servers

```
We have N API servers.
Each handles a portion of requests.
Rate limit is per org across ALL servers.

PROBLEM:
  Limit: 100 requests/second.
  10 API servers.
  Each server sees 10 requests/second from Infosys.
  Each server thinks Infosys is within limit.
  Actually: 100 total requests/second across all servers.
  Exactly at the limit but no single server knows.
  If Infosys sends 200/second:
  Each server sees 20/second, thinks it's fine.
  Total: 200/second — double the limit.
  Nobody rejects.

SOLUTION — CENTRALISED REDIS COUNTER:
  All API servers check the SAME Redis counter.
  
  Server A receives request:
  Runs Lua against shared Redis.
  Redis has global view of Infosys's token count.
  
  Server B receives request:
  Runs same Lua against same Redis.
  Redis already updated by Server A.
  Global count is accurate.
  
  Redis is the single source of truth.
  All 10 API servers share one counter.
  Rate limit enforced globally not per-server.

REDIS CLUSTER FOR AVAILABILITY:
  One Redis instance = single point of failure.
  Use Redis Cluster:
    Multiple Redis nodes.
    Sharded by org_id.
    Infosys always hits same Redis node.
    (Consistent: same node = same counter)
    
  If one Redis node fails:
  Only orgs on that shard affected.
  Other orgs unaffected.
  Failover to replica within seconds.
```

---

### What Happens When Redis Is Down

```
Rate limiter depends entirely on Redis.
Redis cluster goes down.
What do we do?

OPTION A — FAIL OPEN (allow all requests):
  Redis down → skip rate limiting → all requests pass.
  
  Risk: during Redis outage, no rate limiting.
  Buggy scripts can hammer the DB.
  Outage + no rate limiting = dangerous combination.
  
  Use when: API availability is the highest priority.
  Short Redis outages acceptable.

OPTION B — FAIL CLOSED (reject all requests):
  Redis down → reject everything with 503.
  
  Risk: ALL legitimate traffic blocked.
  100% of users affected by Redis failure.
  Likely worse than the protection it provides.
  
  Never use this.

OPTION C — LOCAL FALLBACK (what we use):
  Each API server maintains LOCAL in-memory
  rate limiter as backup.
  
  Simple per-server counter:
  If Infosys rate limit = 100/sec globally.
  10 API servers.
  Local limit = 100/10 = 10/sec per server.
  
  Redis down:
    Each server enforces 10/sec locally.
    Total: 100/sec approximate.
    Not exactly correct (traffic not evenly distributed).
    But provides ~80% of the protection.
  
  When Redis recovers:
    Switch back to global Redis counter.
    Local counters discarded.
  
  Best balance:
    Service available during Redis outage.
    Some protection maintained.
    Exact limits not guaranteed but approximate.
```

---

### Data Model

```
RATE_LIMIT_CONFIG (per org, loaded at startup):
  org_id              UUID
  plan                ENUM      DEVELOPER/PROFESSIONAL/ENTERPRISE
  
  token_bucket_rate   INT       10 tokens/sec (Developer)
  token_bucket_cap    INT       20 max burst (Developer)
  
  sliding_window_hourly_limit  INT    1000 (Developer)
  sliding_window_daily_limit   INT    10000 (Developer)
  
  per_user_daily_limit         INT    1000 (Developer)
  
  updated_at          TIMESTAMP

Redis keys (per org):

  Token bucket:
  "tb:tokens:{org_id}"       FLOAT    current tokens
  "tb:refill:{org_id}"       INT      last refill timestamp
  
  Sliding window (hourly):
  "sw:hour:cur:{org_id}"     INT      current hour count
  "sw:hour:prev:{org_id}"    INT      previous hour count
  
  Sliding window (daily):
  "sw:day:cur:{org_id}"      INT      current day count
  "sw:day:prev:{org_id}"     INT      previous day count
  
  Fixed window (per user per day):
  "fw:{org_id}:{user_id}:{date}"  INT   daily count
  TTL: seconds until midnight
  
  Rate limit config cache:
  "rlcfg:{org_id}"           JSON     plan limits
  TTL: 1 hour
```

---

### Data Flow — Single Request

```
Request arrives at API Gateway:
GET /api/opportunities
Authorization: Bearer {JWT}
Org: infosys, User: ravi

STEP 1 — Load config:
  Redis GET "rlcfg:infosys"
  Cache hit → { token_rate: 100, hourly: 50000, daily: 500000 }
  Cache miss → load from DB → cache for 1 hour.

STEP 2 — Token bucket check (per-second):
  Run Lua script against Redis.
  Keys: "tb:tokens:infosys", "tb:refill:infosys"
  Args: current_time, rate=100, capacity=500
  
  Returns: 1 (allowed) or 0 (rejected)
  
  If 0: return HTTP 429
  Response headers:
    X-RateLimit-Limit: 100
    X-RateLimit-Remaining: 0
    X-RateLimit-Reset: 1705123500
    Retry-After: 1

STEP 3 — Sliding window check (hourly):
  Run Lua script.
  Compute estimated count.
  If over limit: return HTTP 429.

STEP 4 — Fixed window check (daily per user):
  INCR "fw:infosys:ravi:2024-01-15"
  If > 1000: DECR (undo), return 429.

STEP 5 — All passed:
  Forward request to backend.
  Request processed normally.

TOTAL OVERHEAD:
  3 Redis round trips (or 1 pipeline call).
  ~1-2ms total.
  Negligible compared to actual API processing.
```

---

### Tech Stack Justification

| Technology | Why This | Not That |
|-----------|----------|---------|
| Redis for counters | Single-threaded atomic operations, sub-millisecond, Lua scripts | PostgreSQL — too slow, locks contention at scale |
| Lua scripts | Single atomic operation, no race conditions, one round trip | Locks — deadlock risk, 3× round trips |
| Separate Redis cluster | Rate limiter Redis failure doesn't affect session/cache Redis | Shared Redis — one overloaded operation affects all |
| Three algorithms together | Each solves different problem: burst/hourly/daily | Single algorithm — misses one dimension of abuse |
| Token bucket for burst | Allows legitimate spikes, penalises sustained overuse | Fixed window per-second — too strict, false positives on spikes |
| Local fallback on Redis down | Service available, approximate protection maintained | Fail closed — 100% of users affected by Redis failure |

---

### Failure Handling

| Failure | Impact | Recovery |
|---------|--------|---------|
| Redis node down | ~⅓ of orgs lose rate limit counters | Failover to replica. Local fallback during failover |
| Redis cluster down | All rate limiting falls back to local | Local in-memory approximate limits. Switch back when Redis recovers |
| API server crash | In-flight requests lost | Client retries. Stateless API server — no state lost |
| Rate limit config DB down | Cannot load new org configs | Cached configs serve from Redis for 1 hour. Alerts fire |
| Wrong plan limits deployed | Orgs get wrong limits | Config TTL expires → reloads correct limits. Manual invalidation available |

---

### Interview One-Paragraph Summary

> "The rate limiter runs three algorithms simultaneously on every API request: a token bucket for per-second burst control which allows legitimate traffic spikes by accumulating tokens during idle periods, a sliding window hybrid for hourly limits which approximates the count using two counters (current and previous window) to avoid the boundary attack that doubles effective limits at window boundaries, and a fixed window for per-user daily limits where the boundary attack is acceptable given the daily granularity. All three use Redis as the centralised counter store with Lua scripts for atomic check-and-decrement — Lua is critical because Redis's single-threaded execution during a Lua script prevents the race condition where two simultaneous requests both read the same counter value and both get allowed past a limit of one. All API servers share the same Redis counters so the rate limit is enforced globally not per-server. When Redis is unavailable, each API server falls back to local in-memory approximate limits sized as global-limit divided by server-count, maintaining roughly correct protection while keeping the service available."

---

### Key Design Decisions Table

| Decision | Choice | Rejected | Why |
|----------|--------|---------|-----|
| Counter storage | Redis | PostgreSQL | Redis sub-millisecond, atomic. PG too slow at 100K req/sec |
| Atomicity | Lua scripts | Read-then-write | Lua = single atomic op. Read-then-write = guaranteed race condition |
| Per-second algorithm | Token bucket | Fixed window per-second | Token bucket allows burst. Fixed window = false positives on spikes |
| Per-hour algorithm | Sliding window hybrid | Sliding window log | Log = 1000 timestamps per org. Hybrid = 2 integers. 1000× less memory |
| Multi-server coordination | Centralised Redis | Per-server local | Per-server = each server allows full quota. Need global view |
| Redis failure | Local fallback | Fail closed | Fail closed = 100% users affected. Local fallback = service stays up |

---

Shall I continue with Q4 deep dive?

Yes. Let me do Q4, Q5, Q6 together in one shot.

---

# Q4 Deep Dive — Event-Driven Audit Log Pipeline

---

### Problem Statement

```
Every action in Salesforce must be recorded:
  Who changed what field
  From what value to what value
  When exactly
  From which IP address

WHY:
  Compliance: GDPR, SOX, HIPAA require audit trails.
  Dispute resolution: "Who deleted that TCS record?"
  Security: detect suspicious activity.
  Internal governance: manager sees who changed deal amounts.

SCALE:
  150,000 orgs × thousands of operations/day
  = billions of audit events per day.
  Must never miss a single event.
  Must never allow modification of logged events.
  Must be queryable: "show me all changes by Ravi in January"
```

---

### Three Core Challenges

```
CHALLENGE 1 — ATOMICITY:
  Ravi updates Opportunity stage to Closed Won.
  Two things must happen:
    a) Save change to CRM DB
    b) Write audit log entry
  
  If (a) succeeds but (b) fails:
    Record saved. Audit missing. Compliance violation.
  
  If (a) fails but (b) succeeds:
    Audit says change happened. It didn't. False record.
  
  Both must succeed or both must fail. Atomically.

CHALLENGE 2 — IMMUTABILITY:
  Audit logs must NEVER be modified.
  If modifiable: people can cover their tracks.
  "I deleted that record" → edit audit log → blame someone else.
  
  Must be enforced at DB level:
    INSERT-only grants on audit table.
    No UPDATE. No DELETE. No TRUNCATE.
    Even if audit service is compromised.

CHALLENGE 3 — ORDERING:
  Event A generated at 14:32:00.001 (Server 1)
  Event B generated at 14:32:00.002 (Server 2)
  
  Due to network delays:
  Event B arrives at Kafka first.
  Event A arrives second.
  
  Wall clock timestamps are unreliable across servers.
  Clock skew between servers is real.
  
  Audit log must show TRUE causal order.
  Not arrival order. Not wall clock order.
  True order = PostgreSQL transaction order.
```

---

### Core Services

```
SERVICE 1 — CDC (Debezium):
  Reads PostgreSQL WAL (Write-Ahead Log).
  Captures every INSERT, UPDATE, DELETE.
  Publishes raw change event to Kafka.
  
  WHY CDC NOT OUTBOX:
    Outbox requires application code to write to outbox table.
    If developer writes direct SQL bypassing application:
    Outbox misses it. CDC catches it.
    DBA runs UPDATE directly on DB: CDC captures it.
    Outbox would miss this. Compliance gap.
    CDC captures EVERYTHING including direct SQL changes.

SERVICE 2 — ENRICHMENT SERVICE:
  Reads raw CDC events from Kafka.
  Raw event has IDs, not names:
    owner_id: "user-uuid-456"
    account_id: "acc-uuid-789"
  
  Enrichment adds human-readable context:
    owner_name: "Ravi Kumar"
    account_name: "TCS"
    org_name: "Infosys"
    user_email: "ravi@infosys.com"
    ip_address: (from request context)
  
  Also adds:
    PostgreSQL XID (transaction ID) for ordering.
    Kafka topic + offset for pipeline tracing.
  
  Publishes enriched event to Kafka "audit.enriched".

SERVICE 3 — AUDIT WRITER SERVICE:
  Reads from Kafka "audit.enriched".
  Computes hash chain entry.
  Batch inserts to ClickHouse (1,000 rows per insert).
  Never per-event inserts (ClickHouse requirement).
  Commits Kafka offset AFTER successful ClickHouse write.
  
  On ClickHouse failure:
    Retry 3× with exponential backoff.
    After 3 failures: publish to DLQ.
    Commit Kafka offset (don't block pipeline).

SERVICE 4 — DLQ MONITOR:
  Watches "audit.dlq" topic.
  Alerts on-call engineer.
  Failed events stored for manual replay.
  Never silently dropped.
```

---

### Authoritative Ordering — PostgreSQL XID

```
WRONG: use wall clock timestamp.
  Server 1 clock: 14:32:00.001
  Server 2 clock: 14:32:00.003
  Clocks differ by 2ms (clock skew).
  Which event happened first? Cannot tell reliably.

WRONG: use Kafka offset.
  Event A: offset 47 in Kafka topic.
  Event A fails, goes to DLQ.
  DLQ replay: Event A gets NEW offset in DLQ topic.
  Original offset 47 is now meaningless.
  Offset changes on replay. Not stable.

CORRECT: PostgreSQL Transaction ID (XID).
  PostgreSQL assigns XID to every transaction.
  XIDs are strictly monotonically increasing.
  Lower XID = committed first. Always. No exceptions.
  
  Even if Event A arrives at Kafka after Event B:
  Event A XID = 1000 (committed first in PostgreSQL)
  Event B XID = 1001 (committed second)
  
  Audit log orders by XID: A before B. Correct.
  XID assigned at commit time. Never changes.
  Debezium includes XID in every CDC event payload.
  
  Audit queries: ORDER BY xid ASC = true causal order.
```

---

### Hash Chain for Tamper Detection

```
Every audit entry contains hash of previous entry.
Like a blockchain but simpler.

Entry 1:
  data:      "Ravi updated TCS deal stage to Closed Won"
  xid:       1000
  prev_hash: "0000000000"  (genesis entry)
  hash:      SHA256(data + xid + prev_hash) = "abc123"

Entry 2:
  data:      "Priya deleted Contact Arjun Mehta"
  xid:       1001
  prev_hash: "abc123"
  hash:      SHA256(data + xid + prev_hash) = "def456"

Entry 3:
  data:      "Admin changed Ravi's role to Manager"
  xid:       1002
  prev_hash: "def456"
  hash:      SHA256(data + xid + prev_hash) = "ghi789"

IF SOMEONE MODIFIES ENTRY 2:
  Entry 2's hash changes: was "def456", now "xyz999"
  Entry 3's prev_hash = "def456" no longer matches.
  Chain is broken from Entry 3 onwards.
  
  Verification scan detects: 
  "hash chain broken at entry 2, tampered at 14:32:00"
  
  Any modification to any entry breaks all subsequent hashes.
  Mathematically impossible to hide tampering.
```

---

### ClickHouse — Why and How

```
WHY CLICKHOUSE NOT POSTGRESQL:
  Audit logs = write-heavy, read-analytical, append-only.
  
  PostgreSQL (row-oriented):
    "Show all changes by Ravi in January"
    Scans every row, reads all columns.
    Slow. Storage expensive.
  
  ClickHouse (columnar):
    Same query reads ONLY:
      user_name column (filter for Ravi)
      timestamp column (filter for January)
      action column (what changed)
    
    Other 15 columns not read at all.
    10× less data read. 10× faster.
    Compression 10:1 on columnar data.
    Petabytes at reasonable cost.

INSERT-ONLY GRANTS (immutability at DB level):
  CREATE USER audit_writer WITH PASSWORD '...';
  GRANT INSERT ON audit_log TO audit_writer;
  -- NO UPDATE. NO DELETE. NO TRUNCATE.
  
  Even if audit service is hacked:
  Attacker can INSERT (add entries — acceptable).
  Cannot UPDATE (modify existing entries).
  Cannot DELETE (remove entries).
  Immutability enforced at database permission level.

PARTITIONING FOR RETENTION:
  PARTITION BY toYYYYMM(created_at)
  
  2024-01 data → one physical folder on disk.
  2024-02 data → separate physical folder.
  
  Retention: keep 7 years (compliance requirement).
  
  Delete data older than 7 years:
  ALTER TABLE audit_log DROP PARTITION '201701'
  
  One command. O(1) regardless of partition size.
  Drops entire folder. Instant.
  No row-by-row DELETE. No table lock.
  No scanning 7 years of data to find old rows.

MICRO-BATCH INSERTS:
  Never insert per-event. Always batch.
  
  Writer buffers events:
  buffer = []
  
  For each event:
    buffer.append(event)
    
    IF buffer.size >= 1000 OR 10_seconds_elapsed:
      ClickHouse.INSERT(buffer)  ← one bulk insert
      commit Kafka offsets
      buffer.clear()
  
  Why: each ClickHouse INSERT creates a "part" on disk.
  Per-event inserts → millions of tiny parts.
  ClickHouse slows down, throws "too many parts" error.
  Batch of 1,000 → one part → efficient.
```

---

### Data Model

```
AUDIT_LOG (ClickHouse):
  org_id          String
  event_id        UUID
  xid             Int64       PostgreSQL transaction ID
  
  object_type     String      "Opportunity"
  object_id       UUID        which record changed
  object_name     String      "TCS Platform Deal" (denormalised)
  
  action          Enum        INSERT/UPDATE/DELETE
  
  changed_fields  String      JSON: { stage: { from: "Proposal",
                                               to: "Closed Won" } }
  
  actor_user_id   UUID
  actor_name      String      "Ravi Kumar" (denormalised)
  actor_email     String      "ravi@infosys.com"
  actor_ip        String
  
  prev_hash       String      hash of previous entry
  entry_hash      String      hash of this entry
  
  created_at      DateTime
  kafka_offset    Int64       for pipeline tracing

ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_at)
ORDER BY (org_id, xid)
```

---

### Data Flow

```
Ravi saves Opportunity (stage → Closed Won)
  │
  ▼
PostgreSQL WAL records the change with XID=1002
  │
  ▼
Debezium reads WAL:
  { table: opportunity, action: UPDATE,
    before: { stage: "Proposal" },
    after:  { stage: "Closed Won" },
    xid:    1002,
    org_id: "infosys" }
  │
  ▼
Kafka "crm.changes"
  │
  ▼
Enrichment Service:
  Looks up owner_id → "Ravi Kumar"
  Looks up account_id → "TCS"
  Adds IP from request context
  Publishes to Kafka "audit.enriched"
  │
  ▼
Audit Writer Service:
  Buffers event.
  At 1,000 events or 10 seconds:
    Compute hash chain for batch.
    INSERT 1,000 rows to ClickHouse.
    Commit Kafka offsets.
  │
  ▼
ClickHouse audit_log:
  Immutable. Ordered by XID. Hash chain intact.
  Queryable: "show all changes by Ravi in January"
```

---

### Interview Summary

> "The audit log pipeline uses CDC via Debezium reading PostgreSQL's WAL rather than an outbox pattern, because CDC captures everything including direct SQL changes that bypass application code — critical for compliance. Raw events flow through Kafka to an enrichment service that resolves IDs to human-readable names and attaches the PostgreSQL XID as the authoritative ordering key — not Kafka offset which changes on DLQ replay, and not wall clock timestamps which have clock skew across servers. The audit writer micro-batches 1,000 events before inserting to ClickHouse, because per-event inserts create too many small parts causing performance degradation. ClickHouse is partitioned by month enabling O(1) retention via DROP PARTITION, has INSERT-only grants enforcing immutability at the database level, and uses a hash chain where each entry contains the hash of the previous entry so any tampering breaks the chain from that point forward and is immediately detectable."

---

---

# Q5 Deep Dive — Distributed Workflow Automation Engine

---

### Problem Statement

```
Salesforce Flow automates business processes.

When Ravi closes a TCS deal:
  1. Task created: "Send contract to legal team"
  2. Email sent to manager: "Deal closed — ₹1.5Cr"
  3. WAIT until contract is signed (could be 5 days)
  4. Once signed: update Account "Last Won Date = today"
  5. Send customer welcome email

All automatic. No human intervention after Ravi clicks Save.

THREE HARD PROBLEMS:

PROBLEM 1 — WAITING:
  Step 3 waits up to 5 days.
  Cannot hold a thread for 5 days.
  Server restarts happen. Thread would be lost.
  Must pause execution, free resources, resume later.

PROBLEM 2 — CRASH RECOVERY:
  Step 1 completes: task created.
  Step 2 completes: email sent.
  SERVER CRASHES before Step 3.
  On restart: must resume from Step 3.
  NOT from Step 1 (would create duplicate task and email).

PROBLEM 3 — THUNDERING HERD:
  Admin bulk updates 50,000 Opportunities to Closed Won.
  50,000 workflows trigger simultaneously.
  50,000 × 3 steps = 150,000 DB operations at once.
  System collapses.
```

---

### Four Core Concepts

```
CONCEPT 1 — STATE MACHINE:
  Workflow instance always in exactly one state:
  PENDING → RUNNING → WAITING → COMPLETED / FAILED
  
  Like a traffic light: always RED or GREEN.
  Never "between states". Never two states at once.
  
  Why it matters:
    After crash: load state from DB.
    "RUNNING at step 2" → know exactly where to resume.
    No ambiguity.

CONCEPT 2 — IDEMPOTENCY KEYS:
  Before executing any step:
  Generate key: "instance-123:step-1:attempt-1"
  
  Check: does completed execution exist for this key?
  YES → step already ran → skip, use saved result.
  NO  → execute step → save result with this key.
  
  Crash and retry:
    Same key generated.
    Step completed before crash? Result exists → skip.
    Step not completed? No result → execute fresh.
    Never duplicate. Never skip.

CONCEPT 3 — OUTBOX PATTERN (at executor level):
  After step completes, two things must happen atomically:
    a) Save step result + update instance state in DB
    b) Queue next step in Kafka
  
  BEGIN TRANSACTION
    UPDATE step_execution SET status = COMPLETED
    UPDATE workflow_instance SET current_step = 'step-2'
    INSERT INTO outbox (job: "execute step-2")
  COMMIT
  
  Outbox relay publishes to Kafka.
  Atomic. Either all saved or none saved.

CONCEPT 4 — WAITING→READY→RUNNING HANDOFF:
  WAITING: condition not yet met, no processing.
  READY:   condition met, waiting to be picked up by executor.
  RUNNING: executor claimed it, actively processing.
  
  Why not WAITING→RUNNING directly:
    Listener marks RUNNING before executor picks up.
    Two listeners both detect condition.
    Both mark RUNNING. Both publish to Kafka.
    Two executors pick up. Duplicate execution.
  
  With READY as intermediate:
    Listener marks WAITING→READY.
    Executor does optimistic lock:
    UPDATE workflow_instance SET status = RUNNING
    WHERE id = X AND status = READY  ← only if READY
    
    If 0 rows: another executor got it. Skip.
    If 1 row: I own it. Proceed.
    Exactly one executor runs the instance.
```

---

### Core Services

```
SERVICE 1 — TRIGGER SERVICE (stateless):
  Input:  CRM events from Kafka "crm.events"
  Output: Workflow execution jobs to Kafka "workflow.execute"
  
  Loads workflow definitions (cached in Redis).
  For each event: find matching definitions.
  Publishes one job per match.
  
  NO DB writes. NO outbox.
  Kafka offset not committed until all jobs published.
  Crash → re-reads event → re-publishes (idempotent).
  
  Rate limited: max 100 workflow creations/sec per org.
  Prevents thundering herd from bulk triggers.

SERVICE 2 — WORKFLOW EXECUTOR:
  Input:  Jobs from Kafka "workflow.execute"
  Output: Step executions, state transitions
  
  Reads job → loads instance from DB.
  Checks idempotency key.
  Executes current step via Step Executor.
  Saves result + next step in DB + outbox (atomic).
  
  Horizontally scalable.
  Kafka partitioned by org_id.
  Each executor instance owns assigned partitions.

SERVICE 3 — WAIT CONDITION LISTENER:
  Part of executor codebase (not separate service).
  Subscribes to Kafka "crm.events" (all domain events).
  Maintains query index: which instances waiting on what.
  
  When domain event arrives:
    Query: any WAITING instances matching this condition?
    For each match:
      UPDATE instance WAITING→READY (via outbox).
      Outbox relay publishes resume job to Kafka.

SERVICE 4 — TIMEOUT POLLER:
  Runs every minute.
  SELECT * FROM workflow_instance
  WHERE status = 'WAITING'
  AND wait_until < NOW()
  
  For each timed-out instance:
    Move to READY with timeout result.
    Outbox → Kafka → Executor handles timeout path.

SERVICE 5 — STEP EXECUTORS (in-process libraries):
  CREATE_RECORD, UPDATE_RECORD, SEND_EMAIL,
  CALL_WEBHOOK, CONDITION, LOOP, WAIT
  
  Each step type: separate class, same interface.
  execute(step_config, context) → result
  
  LOOP step: bulkifies DB operations.
  Instead of 50,000 individual UPDATEs:
  Collect 500 → execute one bulk UPDATE → repeat.
```

---

### Thundering Herd Solutions

```
PROBLEM: 50,000 bulk-triggered workflows at once.

SOLUTION 1 — RATE LIMIT AT INTAKE:
  Trigger Service enforces per-org rate limit.
  Max 100 workflow instances created/sec per org.
  
  50,000 / 100 = 500 seconds to process all.
  Spread over ~8 minutes. DB load stays flat.
  Nothing lost — just delayed and ordered.

SOLUTION 2 — BULKIFICATION IN LOOP STEPS:
  Workflow loops over 50,000 records.
  Each record needs an Account UPDATE.
  
  WRONG: 50,000 individual UPDATE statements.
  RIGHT: Collect 500 records → one bulk UPDATE → repeat.
  
  50,000 / 500 = 100 batch operations.
  vs 50,000 individual operations.
  100× fewer DB round trips.

SOLUTION 3 — CHANNEL RATE LIMITS:
  50,000 workflows each send an email.
  Email provider (SendGrid) limit: 1,000/sec.
  
  Email step executor has its own rate limiter.
  Max 1,000 email calls/sec.
  Excess queued in Kafka.
  All emails eventually sent. None dropped.
  No 429 errors to SendGrid.
```

---

### Data Model

```
WORKFLOW_DEFINITION:
  id, org_id, name, version
  trigger_type:   RECORD_CHANGE / SCHEDULE / MANUAL
  trigger_config: { object: "Opportunity",
                    condition: "stage = 'Closed Won'" }
  steps:          JSON array of step definitions
  is_active:      BOOLEAN

WORKFLOW_INSTANCE:
  id, org_id, definition_id
  status:         PENDING/RUNNING/WAITING/READY/COMPLETED/FAILED
  current_step:   "step-3"
  context:        JSON (all variables, created IDs, etc.)
  wait_condition: { field: "contract_signed__c", value: true }
  wait_until:     TIMESTAMP (timeout time)
  created_at, updated_at

STEP_EXECUTION:
  id, instance_id, step_id
  idempotency_key: "instance-123:step-1:attempt-1"
  status:          COMPLETED/FAILED
  output:          JSON (what step produced)
  attempt_number:  INT
  executed_at:     TIMESTAMP

OUTBOX:
  id, instance_id
  job_payload:    JSON
  status:         PENDING/SENT
  created_at:     TIMESTAMP
```

---

### Data Flow

```
Ravi closes deal → CRM event on Kafka
  │
  ▼
TRIGGER SERVICE:
  Matches "Opportunity Closed Won" → workflow definition.
  Rate check: < 100/sec for Infosys? Yes.
  Publishes to Kafka "workflow.execute":
  { definition_id, trigger_event, org_id: infosys }
  │
  ▼
WORKFLOW EXECUTOR:
  Creates instance (status=RUNNING, step=1).
  
  STEP 1 — Create Task:
    Check idempotency: "inst-123:step-1:attempt-1" exists? NO.
    Execute: INSERT task in CRM.
    BEGIN TRANSACTION:
      INSERT step_execution (idempotency_key, status=COMPLETED)
      UPDATE instance (current_step=step-2)
      INSERT outbox (job: execute step-2)
    COMMIT.
    Outbox relay → Kafka.
  
  STEP 2 — Send Email:
    Same pattern. Email sent. DB updated. Outbox queued.
  
  STEP 3 — WAIT:
    Step type = WAIT.
    condition: contract_signed__c = true on opp-uuid.
    timeout: 72 hours.
    
    BEGIN TRANSACTION:
      UPDATE instance:
        status = WAITING
        wait_condition = { field: contract_signed__c, value: true }
        wait_until = NOW() + 72 hours
    COMMIT.
    
    No outbox published (no next step yet).
    Executor moves on. Thread freed. Instance just a DB row.

(5 DAYS LATER)

Legal team marks contract as signed.
CRM: contract_signed__c = true on opp-uuid.
CDC detects change → Kafka "crm.events".
  │
  ▼
WAIT CONDITION LISTENER:
  Receives event.
  Queries: WAITING instances watching opp-uuid?
  Found: instance-123 waiting on contract_signed__c = true.
  Condition met.
  
  BEGIN TRANSACTION:
    UPDATE instance: status = READY
    INSERT outbox: job = "resume instance-123"
  COMMIT.
  Outbox relay → Kafka "workflow.execute".
  │
  ▼
WORKFLOW EXECUTOR:
  Picks up "resume instance-123" job.
  Optimistic lock:
    UPDATE instance SET status=RUNNING
    WHERE id=inst-123 AND status=READY
  1 row updated → I own it.
  
  STEP 4 — Update Account:
    Execute. Save. Complete.
  
  UPDATE instance: status=COMPLETED.
  Workflow done.
```

---

### Interview Summary

> "The workflow engine is built on four concepts: a state machine ensuring we always know the exact status of every instance, idempotency keys preventing duplicate step execution on crash-and-retry, the outbox pattern making step completion and next-step queuing atomic in one DB transaction, and a WAITING→READY→RUNNING three-state handoff preventing two executors from claiming the same instance. The Trigger Service is stateless — purely Kafka-in Kafka-out with no DB writes, relying on Kafka offset replay for crash safety and rate limiting at intake to prevent thundering herd when bulk operations trigger thousands of workflows simultaneously. WAITING instances are resumed by a Wait Condition Listener that subscribes to all domain events and uses an optimistic lock to atomically transition WAITING→READY, and a Timeout Poller that catches instances whose wait_until has passed. Bulkification in loop steps reduces 50,000 individual DB operations to 100 batch operations."

---

---

# Q6 Deep Dive — Cross-Cloud Integration Hub (MuleSoft)

---

### Problem Statement

```
Infosys uses 12 systems: Salesforce, SAP, Workday,
Slack, Stripe, ServiceNow, Jira, Marketo...

WITHOUT an integration hub:
  Each system connects to every other.
  12 × 11 = 66 point-to-point integrations.
  Each built differently. No retry. No monitoring.
  SAP updates API → 8 integrations break simultaneously.
  Weeks to fix. This is SPAGHETTI INTEGRATION.

WITH MuleSoft (middleman):
  Each system connects to MuleSoft ONCE.
  12 connections total.
  SAP updates API → fix ONE SAP connector.
  All integrations using SAP automatically work.

DIFFERENCE FROM SALESFORCE FLOW (Q5):
  Flow (Q5): lives INSIDE Salesforce.
             automates things WITHIN Salesforce only.
             "deal closes → create task in Salesforce"
  
  MuleSoft (Q6): lives OUTSIDE all systems.
                 connects DIFFERENT systems together.
                 "deal closes in Salesforce → create invoice in SAP"
  
  They work together:
    Flow handles internal Salesforce automation.
    MuleSoft fires when cross-system integration needed.
```

---

### Three Trigger Types

```
TRIGGER 1 — EVENT (Webhook, Async):

  External system pushes event to MuleSoft.
  MuleSoft reacts asynchronously.
  Source system gets 202 Accepted immediately.
  Heavy processing happens after.
  
  Example:
    Deal closes in Salesforce.
    Salesforce POSTs to MuleSoft webhook.
    MuleSoft: 202 immediately.
    Then: transforms data, calls SAP, creates invoice.
  
  Implementation:
    Intake Service validates HMAC signature.
    Persists event to DB.
    Publishes to Kafka.
    Returns 202. Done.
    Execution Engine processes asynchronously.
  
  When to use:
    Source can fire webhooks.
    Caller doesn't need immediate answer.
    Near real-time (seconds) is sufficient.

TRIGGER 2 — API (Synchronous):

  Caller asks MuleSoft a question and WAITS.
  MuleSoft orchestrates multiple systems in parallel.
  Returns combined answer.
  
  Example:
    Mobile app: GET /customer/TCS-001
    MuleSoft simultaneously:
      Calls Salesforce → CRM data
      Calls SAP → billing data
      Calls ServiceNow → support tickets
    Combines → returns unified response.
    Total: < 500ms.
  
  Implementation:
    Intake Service receives request.
    Executes flow INLINE (not via Kafka).
    Runs independent steps in PARALLEL.
    Sequential: 3 × 200ms = 600ms. Too slow.
    Parallel: max(200ms) = 200ms. Acceptable.
    Returns combined result synchronously.
  
  When to use:
    Caller needs immediate answer.
    Cannot wait for async.
    "Is this customer valid before I create them?"

TRIGGER 3 — SCHEDULE:

  MuleSoft runs flow at configured times.
  No external trigger. Time triggers it.
  
  Example:
    Every night 2am: sync SAP customers to Salesforce.
    Every Monday 8am: send pipeline summary email.
  
  Implementation:
    Redis sorted set scored by next run timestamp.
    ZADD "hub:scheduled" 1705190400 "infosys:flow-uuid"
    
    Scheduler polls every second:
    ZRANGEBYSCORE "hub:scheduled" 0 now()
    → gets flows due now
    Creates integration event.
    Publishes to Kafka.
    Same execution path as event trigger.
    
    After firing: compute next run time from cron.
    Update sorted set with next timestamp.
  
  When to use:
    Overnight sync is acceptable.
    Source system cannot fire events (legacy).
    Need to aggregate before processing.
    Cross-system reconciliation.
```

---

### JAR per Flow vs Config in DB

```
REAL MULESOFT — JAR PER FLOW:

  Customer's flows compiled into .jar file.
  Deployed to dedicated VM (CloudHub worker).
  Each customer gets dedicated execution environment.
  
  PROS:
    Custom Java code in flows.
    Strong isolation (separate JVM per customer).
    Independent versioning.
  
  CONS:
    Dedicated VM per customer.
    MuleSoft serves thousands of customers.
    Average contract: $200K-$2M/year.
    Dedicated VM affordable at that price.
    NOT viable for 150,000 orgs at Salesforce scale.

OUR DESIGN — CONFIG IN DB:

  Flows stored as JSON config in PostgreSQL.
  Not compiled. Not deployed.
  
  One generic Execution Engine binary.
  Reads any org's any flow from DB.
  Executes it.
  
  PROS:
    150,000 orgs → no 150,000 deployments.
    Flow update = change DB row. Instant. No restart.
    One binary serves all orgs.
  
  CONS:
    No arbitrary custom code.
    Only built-in step types available.
    (Security: custom code on shared infra = risk)
  
  SCALING:
    Kafka partitioned by org_id.
    Add Execution Engine instances.
    Each instance owns assigned Kafka partitions.
    Linear scale-out. No coordination needed.
    
    Flow config update:
    Admin saves to DB.
    Publish to Redis pub/sub: "flow:infosys:flow-uuid:updated"
    All Execution Engine instances invalidate cache.
    Next event: load fresh config.
    No deployment. No restart. 30 seconds to propagate.

ANALOGY:
  Real MuleSoft = AWS EC2 (dedicated per customer)
  Our design    = AWS Lambda (shared, config-driven)
```

---

### Transformation Engine — In-Process Library

```
SAP sends XML. Salesforce wants JSON.
Fields named differently. Formats different.

ARCHITECTURE CHOICE: In-process library.
NOT a separate Transformation Service.

WHY NOT SEPARATE SERVICE:
  Every single event needs transformation.
  At 8,000 events/second:
  
  Separate service:
    8,000 network calls/second to Transform Service.
    Each call: 5-10ms network overhead.
    Total overhead: 40-80 seconds per second. Impossible.
  
  In-process library:
    Function call in same process.
    Zero network. Microseconds.
    8,000 events/second: trivial.

TRANSFORMATION RULE (stored in DB, cached in Redis):
{
  source_format: "XML",
  target_format: "JSON",
  mappings: [
    {
      from: "CustomerName",
      to:   "AccountName",
      op:   "DIRECT"
    },
    {
      from: "InvoiceDate",
      to:   "CloseDate",
      op:   "DATE_FORMAT",
      params: { from: "dd.MM.yyyy", to: "yyyy-MM-dd" }
    },
    {
      from: "Amount",
      to:   "Revenue",
      op:   "CALCULATE",
      params: { formula: "value * 1.18" }
    },
    {
      from: "AccountId",
      to:   "SAPCustomerID",
      op:   "LOOKUP",
      params: { table: "sf_to_sap_mapping",
                key: "sf_account_id",
                value: "sap_customer_id" }
    }
  ]
}

OPERATIONS:
  DIRECT:      copy value as-is
  DATE_FORMAT: convert date format
  CALCULATE:   apply formula (value * 1.18)
  LOOKUP:      look up in mapping table (Redis cached)
  CONDITIONAL: if/else branching
  LOOP:        iterate over array, transform each item

Pure function: input + rule → output.
No side effects. Fully stateless.
```

---

### Connector Library — In-Process

```
Each connector wraps one external system.
Hides: authentication, API format, error codes.
Exposes: one simple interface.

CONNECTOR INTERFACE:
  execute(
    operation:  "CREATE_INVOICE",
    payload:    { transformed data },
    config:     { base_url, auth_type, credential_id }
  )
  → { success: true, response: { invoice_id: "INV-123" } }
  → { success: false, error_type: "TRANSIENT", message: "503" }

SAP CONNECTOR:
  execute("CREATE_INVOICE", payload, config):
    1. Get credentials from Vault.
       { username: "sap_user", password: "..." }
    
    2. Build SAP HTTP request:
       POST {base_url}/api/v1/invoices
       Authorization: Basic {base64(user:pass)}
       Content-Type: application/xml
       Body: payload (already transformed to XML)
    
    3. Send with 30s timeout.
    
    4. Handle response:
       HTTP 201 → success, return invoice_id.
       HTTP 503 → TRANSIENT error. Retry.
       HTTP 400 → PERMANENT error. Don't retry.
       Timeout  → TRANSIENT error. Retry.

SALESFORCE CONNECTOR:
  execute("CREATE_ACCOUNT", payload, config):
    1. Get OAuth token from Vault.
       Check expiry: < 5 minutes? Refresh first.
    
    2. POST to Salesforce REST API.
       Authorization: Bearer {access_token}
    
    3. Handle response same pattern.

ALL CONNECTORS:
  Same execute() interface.
  Execution Engine calls execute() the same way
  regardless of which system it's connecting to.
  
  Adding new connector:
  Write one new class implementing execute().
  Zero changes to Execution Engine.
  Deploy new connector library.
  Done.
```

---

### Credential Vault

```
Every connector needs credentials.
150,000 orgs × multiple systems = millions of credentials.

STORAGE:
  credentials table in PostgreSQL:
  {
    org_id:           "infosys"
    connector_type:   "SAP"
    auth_type:        "BASIC"
    encrypted_data:   {AES-256 encrypted bytes}
    encryption_key_id: "kms-key-infosys"
    expires_at:       null (BASIC never expires)
  }

ENCRYPTION:
  Each org has unique AWS KMS key.
  Credentials encrypted with that key.
  
  Store:
    plaintext = { username: "sap_user", password: "secret" }
    encrypted = AWS_KMS.encrypt(plaintext, org_kms_key)
    Store encrypted bytes in DB.
  
  Retrieve:
    Load encrypted from DB.
    AWS_KMS.decrypt(encrypted, org_kms_key) → plaintext.
    Use for API call.
    Never log or store plaintext.
  
  If DB compromised:
    Attacker has encrypted bytes.
    Cannot decrypt without AWS KMS access.
    Requires separate AWS IAM permissions.
    DB breach alone is useless.

CACHING:
  KMS decryption: ~20ms per call.
  At 8,000 events/second: 160 seconds overhead per second.
  
  Cache decrypted credentials in Redis:
  "cred:infosys:SAP" → { username, password }
  TTL: 5 minutes.
  
  First call: KMS decrypt → cache.
  Next calls: Redis hit → < 1ms.
  
  Credential changed: DEL Redis key.
  Next call: fresh from KMS.

OAUTH TOKEN AUTO-REFRESH:
  OAuth tokens expire (Salesforce: 2 hours).
  
  Token Refresh Service runs every 15 minutes:
    SELECT credentials WHERE auth_type = OAUTH2
    AND expires_at < NOW() + 30 minutes
    
    For each near-expiry token:
      Use refresh_token to get new access_token.
      Encrypt new token.
      UPDATE credentials.
      DEL Redis cache key.
  
  Pro-active: refresh 30 min before expiry.
  API calls never fail due to expired token.
```

---

### Three-Layer API Pattern

```
Design philosophy for organising flows.
NOT separate infrastructure.
Just logical layers of flow definitions.

LAYER 1 — SYSTEM APIs (bottom):
  One flow per external system.
  Wraps that system completely.
  Hides: auth, format, error handling.
  Exposes: clean REST endpoint.
  
  "SAP System API":
    Trigger: GET/POST /systems/sap/invoices
    Step 1: Call SAP with Basic Auth
    Step 2: Transform SAP XML → clean JSON
    Step 3: Return clean response
  
  SAP changes their API:
    Update ONLY this one flow.
    Everything above: unaffected.

LAYER 2 — PROCESS APIs (middle):
  Orchestrates System APIs for a business process.
  Contains business logic.
  Does NOT know about API formats or auth.
  
  "Deal Closure Process API":
    Step 1: CALL_FLOW "Salesforce System API" → get deal details
    Step 2: CALL_FLOW "SAP System API" → create invoice
    Step 3: CALL_FLOW "Stripe System API" → initiate billing
    Step 4: CALL_FLOW "Slack System API" → post to #wins

LAYER 3 — EXPERIENCE APIs (top):
  Tailored for specific consumers.
  
  "Mobile App API":
    Calls Deal Closure Process API.
    Filters to mobile-needed fields.
    Lightweight response.
  
  "Partner Portal API":
    Calls same Process API.
    Removes internal pricing fields.
  
  "Executive Dashboard API":
    Calls same Process API.
    Adds calculated KPIs.

WHY IT MATTERS:
  20 apps all call SAP directly:
    SAP changes: 20 codebases to fix.
  
  20 apps call Experience APIs:
    Experience → Process → System → SAP.
    SAP changes: fix ONE System API flow.
    20 apps: automatically work.

IN OUR EXECUTION ENGINE:
  Add step type: CALL_FLOW.
  Process API flow calls System API flow inline.
  Same Execution Engine. Same Kafka. Same DB.
  Just logical layering.
```

---

### Complete Architecture

```
EXTERNAL SYSTEMS (Salesforce, SAP, Workday, Slack...)
  │ webhooks/API calls in        ▲ API calls out
  ▼                              │
INTAKE SERVICE:
  Event trigger: validate HMAC, persist, 202, Kafka.
  API trigger:   execute flow inline, parallel steps.
  Schedule:      fired by Scheduler from Redis sorted set.
  │
  ▼
KAFKA (partitioned by org_id)
  │
  ▼
EXECUTION ENGINE (N stateless instances):
  Load flow config from Redis (5 min TTL cache).
  Check idempotency.
  Execute steps:
    TRANSFORM → in-process transformation library
    CONNECTOR → in-process connector library
    CONDITION → evaluate, branch
    CALL_FLOW → execute sub-flow inline (three-layer pattern)
  
  On transient failure:
    ZADD "hub:delayed" (now + backoff) event_id
    Scheduler re-publishes when due.
  
  On permanent failure (5th attempt):
    Publish to Kafka "hub.dlq"
    Alert org admin.
  │
  ├──────────────────┐
  ▼                  ▼
TRANSFORMATION    CONNECTOR LIBRARY
LIBRARY           (in-process)
(in-process)      SalesforceConnector
                  SAPConnector
                  SlackConnector
                  GenericHTTPConnector
                  All get creds from Vault.
  │
  ▼
CREDENTIAL VAULT:
  Per-org AES-256 encryption.
  Keys in AWS KMS.
  Decrypted values cached in Redis (5 min).
  OAuth auto-refresh 30 min before expiry.
```

---

### Interview Summary

> "The integration hub separates into three abstractions: connectors that wrap each external system behind a common execute() interface hiding auth and API complexity, a transformation engine that runs as an in-process library for zero network overhead converting between formats using declarative mapping rules, and an async execution pipeline where event triggers are validated and immediately acknowledged with 202 while Kafka buffers the work for stateless Execution Engine instances to process at their own pace. API triggers bypass Kafka entirely and execute inline with independent steps running in parallel to meet the 500ms SLA. The config-in-DB approach — where flows are JSON stored in PostgreSQL rather than compiled JARs deployed to dedicated VMs like real MuleSoft — is what enables 150,000 orgs without 150,000 deployments; flow updates take effect within 30 seconds via Redis cache invalidation with no restarts. Credentials are encrypted per-org via AWS KMS and cached decrypted in Redis so KMS latency never hits the hot path, with OAuth tokens proactively refreshed 30 minutes before expiry so API calls never fail mid-flight."

---

### Key Design Decisions — All Three Questions

| Question | Decision | Choice | Why |
|----------|----------|--------|-----|
| Q4 | Event capture | CDC (Debezium) | Catches direct SQL changes that bypass application code |
| Q4 | Ordering | PostgreSQL XID | Stable, never changes on replay, true causal order |
| Q4 | Storage | ClickHouse INSERT-only | Columnar for queries, immutability at DB permission level |
| Q4 | Retention | DROP PARTITION | O(1) regardless of partition size. No row-by-row DELETE |
| Q5 | Trigger service | Kafka in→Kafka out, no DB | Stateless, crash-safe via offset replay, no outbox needed |
| Q5 | Crash recovery | Idempotency keys | Only reliable way to prevent duplicate execution on retry |
| Q5 | Wait handoff | WAITING→READY→RUNNING | Prevents two executors claiming same instance simultaneously |
| Q5 | Thundering herd | Rate limit + bulkify + channel limits | Three separate problems needing three separate solutions |
| Q6 | Deployment model | Config in DB | JAR per flow not viable for 150,000 orgs |
| Q6 | Transformation | In-process library | Network call per event = system cannot keep up at 8K events/sec |
| Q6 | Credential security | AWS KMS + Redis cache | KMS for security, cache for performance, never on hot path |
| Q6 | Flow layering | CALL_FLOW step type | Three-layer pattern via logical flow composition, no extra infra |

---

Shall I continue with Q7, Q8, Q10 together?


# Q7 Deep Dive — File Storage System

---

### Problem Statement

```
Salesforce users attach files to CRM records.
  Contracts attached to Opportunities.
  Support screenshots attached to Cases.
  Demo videos attached to Accounts.
  Files range from 1KB to 2GB.

FOUR PROBLEMS TO SOLVE:

PROBLEM 1 — SIZE:
  A 500MB video cannot go in PostgreSQL.
  DB is optimised for structured data (rows, columns).
  Binary blobs in DB: kills performance for everyone.
  Needs dedicated binary storage.

PROBLEM 2 — LARGE UPLOAD RELIABILITY:
  Uploading 500MB over a mobile connection.
  Network drops at 450MB.
  Without chunking: restart from 0.
  User waits 20 minutes, fails at minute 18.
  Must restart entirely. Unacceptable.

PROBLEM 3 — DOWNLOAD AT SCALE:
  100,000 users downloading files simultaneously.
  If all downloads proxy through our servers:
  Our servers need massive bandwidth.
  Each server buffers 500MB per download.
  Extremely expensive. Does not scale.

PROBLEM 4 — STORAGE COST:
  Infosys uploads contract.pdf.
  TCS uploads the identical contract.pdf.
  Without deduplication: stored twice.
  At scale: significant wasted storage cost.
```

---

### Fundamental Design Decision

```
WHERE DO BYTES LIVE?

Option A — Build our own storage:
  Buy hard drives.
  Write distributed file system.
  Handle replication, failure, expansion.
  Takes years. Not our core business.

Option B — AWS S3 (correct):
  99.999999999% durability (11 nines). Built in.
  Unlimited scale. Built in.
  We focus on: metadata, access control,
              chunked upload API, deduplication.
  S3 focuses on: storing and serving bytes.

WHAT WE BUILD ON TOP OF S3:
  FILE metadata in PostgreSQL.
  Access control (who can read which file).
  Chunked upload API (resume on network failure).
  Deduplication (same bytes = one S3 object).
  Virus scanning (async, post-upload).
  Versioning (linked list of versions).
  External sharing (time-limited tokens).
```

---

### S3 Key Design — Content-Addressed Storage

```
TWO APPROACHES:

APPROACH A — Org-namespaced:
  "files/{org_id}/{uuid}/{filename}"
  
  Same file uploaded by two orgs = stored twice.
  Wastes storage.

APPROACH B — Content-addressed (what we use):
  "files/content/{first_2_chars_of_sha256}/{sha256_hash}"
  
  Example:
  SHA-256 of contract.pdf = "abc123def456..."
  S3 key: "files/content/ab/abc123def456..."
  
  KEY IS THE CONTENT HASH.
  Same content → same hash → same key → same S3 object.
  
  Infosys uploads contract.pdf:
    SHA-256 = "abc123..."
    Check: does "files/content/ab/abc123..." exist in S3?
    NO → upload. Store FILE record pointing to this key.
  
  TCS uploads identical contract.pdf:
    SHA-256 = "abc123..."
    Check: does "files/content/ab/abc123..." exist in S3?
    YES → don't upload again.
    Store TCS FILE record pointing to SAME S3 key.
  
  Two FILE records in our DB.
  One S3 object.
  50% storage saving on identical files.

WHY 2-CHAR PREFIX:
  S3 partitions internally by key prefix.
  All keys starting "files/content/" → same S3 partition.
  Hot partition → performance degrades.
  
  2-char hex prefix: 00 to ff = 256 possible prefixes.
  Keys distributed across 256 S3 partitions.
  Even load distribution.
```

---

### Chunked Upload — Complete Mechanics

```
TWO LEVELS OF CHUNKING:

LEVEL 1: Client → Our Server (our API)
LEVEL 2: Our Server → S3 (S3 Multipart Upload API)

They mirror each other exactly.

CLIENT SIDE — How file is split:

  User selects: demo_recording.mp4 (500MB)
  
  Browser gets file metadata instantly:
  file.size = 524,288,000  ← from OS file system table
                              NO file content read
  
  file.slice(startByte, endByte):
    Creates a REFERENCE to disk location.
    Does NOT copy bytes into RAM.
    Does NOT read from disk yet.
    Just records: "bytes 0-5MB are at disk blocks X-Y"
  
  When actually SENDING a chunk:
    Browser reads that disk range sequentially.
    Streams to network card 64KB at a time.
    RAM used at any moment: ~64KB. Not 5MB. Not 500MB.
  
  100 chunks × 5MB = 500MB file.
  RAM used: ~64KB throughout entire upload.

SERVER SIDE — App server as a PIPE:

  Receives chunk bytes from client.
  Does NOT write to server disk.
  Does NOT buffer full chunk in RAM.
  Streams bytes directly to S3 as an S3 "part".
  S3 returns etag string for that part.
  Server stores ONLY the etag (tiny string) in PostgreSQL.
  
  Server RAM per chunk: ~64KB (streaming buffer).
  Not 5MB. Not 500MB.
  10,000 concurrent uploads × 64KB = 640MB server RAM.
  Manageable.

UPLOAD SESSION in PostgreSQL:
  {
    session_id:      "sess-abc",
    org_id:          "infosys",
    file_name:       "demo_recording.mp4",
    file_size:       524288000,
    chunk_size:      5242880,       ← 5MB per chunk
    total_chunks:    100,
    received_chunks: {              ← which chunks received
      "0": "etag-part-1",
      "1": "etag-part-2",
      "2": "etag-part-3"
    },
    s3_upload_id:    "s3-mpu-xyz",  ← S3's multipart ID
    status:          IN_PROGRESS,
    expires_at:      NOW() + 24hrs  ← auto-cleanup
  }

WHY STATELESS SERVERS WORK:
  Session stored in PostgreSQL (shared).
  NOT in app server memory.
  
  Chunk 0 → App Server 7:
    Reads session from DB.
    Streams to S3. Saves etag.
    Updates received_chunks in DB.
  
  Chunk 1 → App Server 23 (different server):
    Reads same session from DB.
    Sees chunk 0 already received.
    Processes chunk 1 independently.
  
  Any server can handle any chunk.
  No affinity needed.
  Load balancer distributes freely.
```

---

### Complete Upload Flow

```
PHASE 1 — INITIATE:

  Client: POST /files/upload/initiate
  { file_name, file_size: 524288000, mime_type, record_id }
  
  File Service:
    Check quota: used + 500MB <= quota?
    NO → 413 Quota Exceeded.
    YES → continue.
    
    Call S3: create_multipart_upload(bucket, key)
    S3 returns: s3_upload_id = "s3-mpu-xyz"
    
    Create UPLOAD_SESSION in DB.
    
    Return: { session_id, chunk_size: 5242880, total_chunks: 100 }

PHASE 2 — UPLOAD CHUNKS (repeat 100 times):

  Client: PUT /files/upload/{session_id}/chunk/{chunk_number}
  Body: [5MB raw bytes streamed]
  
  File Service:
    Validate session exists and IN_PROGRESS.
    Check chunk not already received.
    Stream bytes to S3:
      S3 API: upload_part(s3_upload_id, part_number, bytes)
      S3 returns etag.
    UPDATE session: received_chunks[chunk_number] = etag.
    Return: { chunk: 0, status: "received" }

NETWORK DROP RECOVERY:

  Client detects connection lost.
  Waits. Network recovers.
  
  Client: GET /files/upload/{session_id}/status
  Service: { received: [0,1,...,46], missing: [47,48,...,99] }
  
  Client resumes from chunk 47.
  Already-uploaded 47 chunks: NOT re-sent.
  Only missing chunks sent.

PHASE 3 — COMPLETE:

  Client: POST /files/upload/{session_id}/complete
  { checksum_sha256: "abc123..." }
  
  File Service:
    Verify all 100 chunks received.
    Any missing? Return error with missing chunk list.
    
    Tell S3 to assemble:
    complete_multipart_upload(s3_upload_id, [
      { part_number: 1, etag: "etag-1" },
      { part_number: 2, etag: "etag-2" },
      ...
      { part_number: 100, etag: "etag-100" }
    ])
    S3 joins all parts → one permanent object.
    Temporary parts deleted by S3.
    
    Deduplication check:
    SELECT * FROM file
    WHERE checksum_sha256 = 'abc123...'
    AND org_id = 'infosys'  ← check within org first
    OR globally (cross-org dedup)
    
    FOUND (duplicate):
      Delete newly uploaded S3 object (waste of space).
      Create FILE record pointing to EXISTING S3 key.
    
    NOT FOUND:
      Create FILE record with new S3 key.
    
    Create FILE_ASSOCIATION:
    { file_id, record_type: "Opportunity", record_id }
    
    UPDATE org_storage_quota: used_bytes += file_size.
    
    Mark UPLOAD_SESSION as COMPLETED.
    
    Publish to Kafka "files.scan" for virus scan.
    
    Return: { file_id, name, size, status: "active" }
```

---

### Download Flow — Pre-Signed URLs

```
User clicks download.
GET /files/{file_id}
Authorization: Bearer {jwt_token}

File Service:

Step 1 — Authenticate:
  Decode JWT: org_id = "infosys", user_id = "ravi"

Step 2 — Load file metadata:
  SELECT * FROM file
  WHERE id = 'file-uuid'
  AND org_id = 'infosys'   ← multi-tenant isolation
  
  Not found or wrong org: 404.

Step 3 — Check file status:
  QUARANTINED: 403 "File unavailable — security concern"
  DELETED:     404
  ACTIVE:      proceed.

Step 4 — Check permissions:
  SELECT * FROM file_permission
  WHERE file_id = 'file-uuid'
  AND (
    permission_type = 'ORG_INTERNAL'
    OR (permission_type = 'PRIVATE'
        AND grantee_user_id = 'ravi')
  )
  
  No permission found: 403 Forbidden.

Step 5 — Generate pre-signed URL:
  S3 API: generate_presigned_url(
    key:    "files/content/ab/abc123...",
    expiry: 300  ← 5 minutes
  )
  Returns: https://s3.amazonaws.com/bucket/key
            ?X-Amz-Signature=...&X-Amz-Expires=300

Step 6 — Return redirect:
  HTTP 302
  Location: {presigned_url}
  
  Browser follows redirect.
  Downloads DIRECTLY from S3.
  Our server: handled only the tiny redirect response.
  S3: handles all the bytes, all the bandwidth.

SECURITY MODEL:
  S3 bucket is PRIVATE. No direct public access.
  Pre-signed URL: valid 5 minutes, contains cryptographic signature.
  Access control: enforced by OUR service BEFORE URL generation.
  S3 only checks: valid signature + not expired.
  
  Isolation at metadata layer:
    FILE table has org_id.
    Even if two orgs share same S3 object (dedup):
    Each org can only access their own FILE records.
    Cannot generate pre-signed URL for another org's file.

WHY NOT CDN:
  CDN caches content at edge.
  Cannot enforce per-user access control.
  If file cached at CDN edge:
    Anyone with the CDN URL can access.
    No way to check org_id or file permissions.
  
  CDN appropriate for: public files (logos, profile pictures).
  Not appropriate for: private CRM files.
  All private files: direct S3 pre-signed URL.
```

---

### Virus Scanning, Versioning, External Sharing

```
VIRUS SCANNING:

  Upload completes → status = ACTIVE (immediately accessible).
  Publish to Kafka "files.scan":
  { file_id, storage_key, org_id }
  
  Virus Scanner Service:
    Download file from S3.
    Run through ClamAV or commercial scanner.
    
    CLEAN:
      UPDATE file SET scan_status = CLEAN.
      No other action.
    
    VIRUS FOUND:
      UPDATE file SET status = QUARANTINED.
      Notify org admin: "File X quarantined: [threat name]"
      Any download attempt: 403 "File unavailable"
      Delete from S3 after quarantine confirmed.
  
  Why allow access before scan:
    Scanning takes 10-60 seconds (large files: minutes).
    Users expect file immediately after upload.
    Enterprise context: most files legitimate.
    Small risk window acceptable.

VERSIONING:

  User uploads contract_v1.pdf.
  User uploads updated contract_v2.pdf.
  
  FILE records:
  v1: { id: file-1, version: 1, parent_file_id: null,   status: SUPERSEDED }
  v2: { id: file-2, version: 2, parent_file_id: file-1, status: ACTIVE }
  
  Linked list. Full history preserved.
  v1 not deleted — SUPERSEDED. Still accessible.
  v2 is current — ACTIVE.
  
  Each version: separate S3 object (different content = different hash).
  Dedup still works: if v2 identical to v1 → same S3 object.

EXTERNAL SHARING:

  Ravi wants to share contract with external lawyer.
  Lawyer has no Salesforce account.
  
  Ravi clicks "Share Externally":
  
  INSERT FILE_PERMISSION:
  {
    file_id:         "file-uuid",
    permission_type: EXTERNAL_LINK,
    external_token:  random 256-bit secure string,
    expires_at:      NOW() + 7 days
  }
  
  URL sent to lawyer:
  https://files.salesforce.com/share/{random-token}
  
  Lawyer clicks:
    File Service: SELECT WHERE external_token = 'xyz'
                  AND expires_at > NOW()
    Found → generate pre-signed URL → redirect.
    Not found or expired → 404.
  
  Security:
    256-bit randomness: impossible to guess.
    Time-limited: expires in 7 days.
    Revocable: DELETE permission record.
    Audited: every access logged.
```

---

### Data Model

```
FILE:
  id, org_id, name, description
  mime_type, size_bytes
  checksum_sha256       ← for deduplication
  storage_key           ← S3 object key
  version, parent_file_id  ← for versioning
  status: UPLOADING/ACTIVE/SUPERSEDED/DELETED/QUARANTINED
  scan_status: PENDING/CLEAN/QUARANTINED
  uploaded_by, created_at, updated_at

FILE_ASSOCIATION:
  file_id, org_id
  record_type: "Opportunity"/"Case"/"Account"
  record_id
  created_by, created_at

FILE_PERMISSION:
  file_id, org_id
  permission_type: PRIVATE/ORG_INTERNAL/SPECIFIC_USER/EXTERNAL_LINK
  grantee_user_id  ← null if not SPECIFIC_USER
  external_token   ← null if not EXTERNAL_LINK
  expires_at
  created_by, created_at

UPLOAD_SESSION:
  id (session_id), org_id, user_id
  file_name, file_size, mime_type
  chunk_size, total_chunks
  received_chunks: JSONB  ← { chunk_number: s3_etag }
  s3_upload_id
  status: IN_PROGRESS/COMPLETED/EXPIRED
  expires_at

ORG_STORAGE_QUOTA:
  org_id
  quota_bytes    ← plan limit
  used_bytes     ← currently used
  last_updated
```

---

### Interview Summary

> "The file storage system separates bytes from metadata: bytes live in AWS S3 using content-addressed keys derived from SHA-256 hashes so identical files across orgs share one S3 object while remaining isolated at the metadata layer, and metadata including file records, associations, permissions, and upload sessions lives in PostgreSQL. Large file uploads use S3 Multipart Upload exposed through our chunked upload API where the client uses file.slice() to create disk references without loading content into RAM, the app server streams each chunk directly to S3 without buffering locally acting as a pipe not a bucket, and the upload session stored in PostgreSQL tracks received chunk etags making it stateless so any server can handle any chunk. Downloads generate pre-signed S3 URLs valid for 5 minutes after our access control checks pass, redirecting the browser to download directly from S3 so our servers handle only the lightweight redirect. CDN is avoided for private files because it cannot enforce per-user access control. Virus scanning is async post-upload via Kafka with files immediately accessible, quarantined if a threat is detected."

---

---

# Q8 Deep Dive — Real-Time Analytics Pipeline

---

### Problem Statement

```
Sales manager Priya opens her Salesforce dashboard.

She sees:
  Revenue This Month:     ₹4.2 Crore     ← must be live
  Deals Closed Today:     3               ← must be live
  Pipeline by Stage:      [bar chart]
  Top Performers:         [leaderboard]
  Revenue Last 12 Months: [trend chart]

TWO CONFLICTING REQUIREMENTS:

REQUIREMENT 1 — REAL-TIME:
  Ravi closes a deal at 2pm.
  Dashboard should show it within seconds.
  Not after the nightly batch job at 2am.
  Manager makes decisions based on live numbers.

REQUIREMENT 2 — HISTORICAL ACCURACY:
  Monthly trend chart shows last 12 months.
  Must be accurate even if Ravi edited a
  deal's amount from 3 months ago.
  Must handle deletions, corrections, bulk SQL updates.

WHY CRM DB CANNOT SERVE DASHBOARDS:
  "Revenue by rep by month" query:
  SELECT owner_name, close_month, SUM(amount)
  FROM opportunity
  WHERE org_id = 'infosys'
  GROUP BY owner_name, close_month
  
  Infosys has 500,000 opportunity records.
  PostgreSQL must scan all of them.
  150,000 orgs × 10 dashboard users each = 1.5M queries.
  All scanning 500,000 rows.
  CRM DB collapses. Ravi cannot save records.
  Completely unacceptable.
```

---

### The Architecture Decision — Lambda Architecture

```
THREE OPTIONS:

OPTION A — BATCH ONLY (hourly Spark job):
  Simple. Accurate. But 1-hour staleness.
  "Deals closed today" could be 59 minutes stale.
  Not real-time. Unacceptable for live dashboard.

OPTION B — STREAM ONLY (event-driven):
  Real-time. But drift accumulates over time.
  Stream processor crashes → misses events.
  Bulk SQL corrections bypass stream → drift.
  Historical data unreliable without periodic correction.

OPTION C — LAMBDA ARCHITECTURE (correct):
  BOTH batch and stream. Each covers the other's weakness.
  
  STREAM LAYER: handles freshness (seconds).
    CDC → Kafka → Stream Processor → Redis counters.
    Updates within seconds of each CRM change.
    Simple counters only (INCR/INCRBYFLOAT).
    Today's data only.
  
  BATCH LAYER: handles accuracy (historical).
    CDC → Kafka → ClickHouse raw fact rows.
    OR: periodic Spark job from read replica.
    Full recomputation ensures correctness.
    Handles edits, deletes, corrections.
    Historical periods.
  
  SERVING LAYER: merges both.
    Today's data → Redis (live, seconds fresh).
    Historical → ClickHouse (accurate, hourly fresh).
    Dashboard gets both merged seamlessly.

MODERN ALTERNATIVE (Druid/Pinot):
  Real-time OLAP database.
  Consumes Kafka events directly.
  Aggregates PER EVENT as they arrive (rollup).
  No separate Redis counters needed.
  No batch layer needed for most queries.
  Sub-second queries on pre-aggregated segments.
  
  For interview: mention Lambda as primary design.
  Mention Druid/Pinot as modern alternative.
  Show you know both.
```

---

### Stream Layer — How It Actually Works

```
CDC detects Ravi closes TCS deal (₹1.5Cr):

Kafka event:
{
  event_type:  "OPPORTUNITY_UPDATED",
  org_id:      "infosys",
  before:      { stage: "Negotiation", amount: 1500000 },
  after:       { stage: "Closed Won",  amount: 1500000 },
  owner_id:    "ravi-uuid",
  owner_name:  "Ravi Kumar",
  updated_at:  "2024-01-15T14:32:00"
}

Stream Processor receives event:

Step 1 — What metrics are affected?
  stage changed to Closed Won.
  Affected metrics:
    org daily revenue closed
    org daily deals closed
    Ravi's daily revenue
    Ravi's daily deal count
    Stage distribution (Negotiation -1, Closed Won +1)

Step 2 — Update Redis atomically:
  INCRBYFLOAT "analytics:infosys:2024-01-15:revenue:won"    1500000
  INCR        "analytics:infosys:2024-01-15:deals:won"
  INCRBYFLOAT "analytics:infosys:2024-01-15:rep:ravi:revenue" 1500000
  INCR        "analytics:infosys:2024-01-15:rep:ravi:deals"
  INCRBYFLOAT "analytics:infosys:pipeline:negotiation"      -1500000
  INCRBYFLOAT "analytics:infosys:pipeline:closed_won"        1500000

  8 Redis operations. All atomic. All sub-millisecond.
  Never read-then-write. Always INCR/INCRBYFLOAT.
  Why: read-then-write has race condition.
       Two events simultaneously: both read same value,
       both add their amount, one overwrites the other.
       One deal lost from the total.
  
  INCRBYFLOAT is atomic: Redis adds internally.
  No race condition possible.
  Two simultaneous events: both counted correctly.

Step 3 — Push to dashboard (if open):
  Publish to Kafka "dashboard.updates":
  { org_id: infosys, metrics_changed: [revenue, deals] }
  
  Notification system (Q2) delivers via WebSocket.
  Dashboard numbers update live.
  No page refresh needed.

MICRO-BATCHING TO CLICKHOUSE:
  Stream processor also writes raw fact rows to ClickHouse.
  But NOT per-event.
  
  Buffer events: accumulate 10,000 rows OR 10 seconds.
  Then: one bulk INSERT to ClickHouse opportunity_fact.
  
  Why: ClickHouse creates one "part" per INSERT.
  Per-event = millions of tiny parts = performance disaster.
  Batch of 10,000 = one part = efficient.
  
  Max latency to ClickHouse: 10 seconds.
  Redis counters: seconds.
  ClickHouse raw rows: ~10 seconds.
```

---

### Batch Layer — When and Why Still Needed

```
Event-driven handles 99% of cases.
Batch still needed for:

CASE 1 — Corrections that bypass CDC:
  Finance team discovers: 50 deals categorised wrong.
  They run direct SQL UPDATE on CRM DB.
  CDC catches these (reads WAL).
  But stream processor already processed original values.
  Redis counters now wrong.
  
  Nightly batch: recomputes from source of truth.
  Overrides wrong Redis/ClickHouse values.
  Next morning: correct.

CASE 2 — Cross-system joins:
  Report: "reconcile Salesforce opportunities with
           SAP invoices and Stripe payments"
  
  Three separate systems.
  Event-driven: each fires events independently.
  Joining in real-time is complex and error-prone.
  
  Batch: extract from all three at same point in time.
  Spark joins them with consistent snapshot.
  Clean result.

CASE 3 — Historical backfill:
  New report requirement: need 3 years of history.
  Event-driven: only processes new events.
  Cannot go back in time.
  
  Batch: query CRM DB for 3 years.
  Spark processes. Loads to ClickHouse.
  Done.

BATCH FLOW:
  CRM read replica
    → Watermark extract (updated_at > last_run)
    → S3 parquet (intermediate staging)
    → Spark transform (JOINs, derived fields)
    → ClickHouse (ReplacingMergeTree upsert)
  
  Airflow orchestrates. Runs hourly or nightly.
  Idempotent: same result whether run once or ten times.
```

---

### ClickHouse — Why Fast Even on Raw Rows

```
You might ask: ClickHouse still stores raw rows.
Still needs to scan and aggregate.
How is it faster than PostgreSQL?

REASON 1 — COLUMNAR STORAGE:
  PostgreSQL: stores data row by row on disk.
  "Revenue by rep" → must read ALL columns of EVERY row.
  
  ClickHouse: stores data column by column.
  "Revenue by rep" → reads ONLY:
    owner_name column (to filter/group)
    amount column (to sum)
    close_month column (to filter)
  
  Other 15 columns: not read at all.
  20 columns → read 3 = 85% less data read from disk.
  85% less disk I/O = 85% faster.

REASON 2 — COMPRESSION:
  owner_name column: same names repeat thousands of times.
  "Ravi Kumar" appears 5,000 times.
  LZ4 compression: 20:1 ratio.
  
  Read 1GB from disk instead of 20GB.
  20× less disk I/O.

REASON 3 — PARTITION PRUNING:
  PARTITION BY toYYYYMM(close_date)
  
  Query: WHERE close_date BETWEEN '2024-01' AND '2024-03'
  ClickHouse: reads ONLY Jan/Feb/Mar partitions.
  Other months: not touched at all.
  5 years of data → reads 3 months = 95% skip.

REASON 4 — VECTORISED SIMD EXECUTION:
  Normal DB: one row at a time.
  Compare owner_name = 'Ravi' for one row. Move to next.
  
  ClickHouse: 1,024 rows at once using CPU SIMD instructions.
  Compare 1,024 owner_names simultaneously in one clock cycle.
  Process 1,024 rows in same time as 1 row normally.
  
  Combined: query that takes 30 seconds in PostgreSQL
  takes 50-200ms in ClickHouse on same raw data.

MATERIALISED VIEWS FOR HIGH CONCURRENCY:
  Raw rows fast enough for ad-hoc queries.
  But: 10,000 users opening dashboard simultaneously.
  10,000 GROUP BY queries at once → ClickHouse struggles.
  
  Solution: ClickHouse Materialised Views.
  
  CREATE MATERIALIZED VIEW opp_monthly_summary
  ENGINE = SummingMergeTree()
  ORDER BY (org_id, owner_name, close_month, is_won)
  AS SELECT org_id, owner_name, close_month, is_won,
            SUM(amount) as total, COUNT(*) as deals
  FROM opportunity_fact
  GROUP BY ...
  
  Auto-updates on every INSERT to opportunity_fact.
  Dashboard query hits materialised view:
  36M rows vs 7.5B raw rows.
  200× smaller. 1-10ms queries.
  Handles 10,000 concurrent users.
```

---

### Three-Level Query Routing

```
DASHBOARD QUERY ARRIVES:
  "Revenue this month" → Redis (live, seconds)
  "Revenue by rep this month" → Materialised view (fast)
  "All deals this month, sortable" → Raw fact rows (ad-hoc)
  "Revenue trend 12 months" → Serving layer merges:
      Jan-Nov: ClickHouse historical
      December (current month): Redis live counters

DECISION RULE:
  Use Redis when: simple counter, today only, sub-ms needed.
  Use materialised view when: predictable, repeated, high concurrency.
  Use raw fact rows when: ad-hoc, unpredictable filters, drill-down.
  
  These are not different systems.
  Same ClickHouse cluster.
  Same ingestion pipeline.
  Different READ PATTERNS on same data.
```

---

### Data Model

```
OPPORTUNITY_FACT (ClickHouse, raw rows):
  org_id, opp_id, owner_name, account_name
  stage, amount, close_date
  close_month, close_quarter, close_year  ← pre-computed
  days_to_close, deal_size_bucket         ← derived
  is_won, is_lost, is_open                ← boolean flags
  created_at, updated_at, loaded_at

ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(close_date)
ORDER BY (org_id, opp_id)

OPP_MONTHLY_SUMMARY (ClickHouse materialised view):
  org_id, owner_name, close_month, is_won
  total_amount, deal_count
  
ENGINE = SummingMergeTree()
ORDER BY (org_id, owner_name, close_month, is_won)

Redis keys (stream layer):
  "analytics:{org_id}:{date}:revenue:won"    FLOAT
  "analytics:{org_id}:{date}:deals:won"      INT
  "analytics:{org_id}:{date}:rep:{name}:rev" FLOAT
  "analytics:{org_id}:pipeline:{stage}"      FLOAT
  TTL: 48 hours (auto-cleanup)
```

---

### Interview Summary

> "The analytics pipeline uses Lambda Architecture with a stream layer for real-time freshness and a batch layer for historical accuracy, both feeding ClickHouse with serving layer merging them for queries. CDC via Debezium reads the CRM WAL and publishes to Kafka, where two consumers run: a stream processor that updates Redis counters atomically using INCRBYFLOAT — never read-then-write which would create race conditions — for today's live numbers, and a ClickHouse writer that micro-batches 10,000 rows before inserting because per-event inserts create too many small parts causing performance degradation. ClickHouse is fast on raw rows not because it avoids scanning but because columnar storage reads only queried columns, compression reduces disk I/O 10-20×, partition pruning skips entire months, and vectorised SIMD execution processes 1,024 rows per clock cycle. Materialised views auto-aggregate on insert for high-concurrency dashboard queries hitting 36M pre-aggregated rows instead of 7.5B raw rows. The batch layer via Spark handles corrections from direct SQL updates, cross-system joins, and historical backfill — things event-driven cannot handle."

---

---

# Q10 Deep Dive — Authentication and SSO Platform

---

### Problem Statement

```
THREE SCENARIOS:

SCENARIO 1 — PASSWORD LOGIN:
  Ravi opens salesforce.com.
  Enters: ravi@infosys.com / password123.
  Gets access to Infosys's org only.
  Cannot see TCS data. Cannot see Wipro data.

SCENARIO 2 — SSO (Microsoft):
  Infosys IT says: "use your Microsoft account to login".
  Ravi clicks "Login with Infosys SSO".
  Redirected to Microsoft. Enters Microsoft credentials.
  Returns to Salesforce. Logged in. No separate SF password.
  
  When Ravi leaves Infosys:
    IT disables his Microsoft account.
    He immediately loses Salesforce access.
    No separate SF deactivation needed.

SCENARIO 3 — API ACCESS:
  Developer app calls Salesforce API.
  Cannot use a human's password (they might leave).
  App gets client_id + client_secret.
  Exchanges for access token.
  Uses token for all API calls.
  Token expires. App refreshes silently.

FIVE PROBLEMS TO SOLVE:
  1. IDENTITY: who is this person really?
  2. AUTHORIZATION: what are they allowed to do?
  3. SESSIONS: how does every subsequent request know
               who they are without re-login?
  4. FEDERATION: how do we trust Microsoft's assertion?
  5. SCALE: every API call must be authenticated.
            150M users × 100 calls/day = 15B auth checks/day.
            Cannot hit DB for each check.
```

---

### JWT — The Core of Everything

```
WHAT IS JWT:
  JSON Web Token.
  Self-contained. Carries its own claims.
  Signed but NOT encrypted (base64 encoded).
  
  Three parts separated by dots:
  header.payload.signature

HEADER (base64 decoded):
  { "alg": "RS256", "typ": "JWT" }

PAYLOAD (base64 decoded):
  {
    "sub":    "user-uuid-ravi",
    "org_id": "infosys-uuid",
    "email":  "ravi@infosys.com",
    "role":   "Standard",
    "scope":  "full_access",
    "iat":    1705123456,    ← issued at
    "exp":    1705130656,    ← expires at (iat + 2 hours)
    "jti":    "unique-token-uuid"  ← JWT ID (for revocation)
  }

SIGNATURE:
  RS256_sign(base64(header) + "." + base64(payload), PRIVATE_KEY)
  
  Signed with Auth Service's PRIVATE key.
  Verified with PUBLIC key.
  Public key: freely distributed.
  Private key: never leaves Auth Service (stored in AWS KMS).

WHY SELF-CONTAINED MATTERS FOR SCALE:
  Every API request has JWT in Authorization header.
  API server validates JWT:
    Load public key from MEMORY (cached at startup).
    Verify signature: pure CPU computation.
    Check expiry: compare timestamps.
    Extract claims: read payload.
    
    ZERO DB calls.
    ZERO network calls.
    ~0.5ms per validation.
  
  15B checks/day on JWT:
    15B × 0.5ms = 2.08 hours of pure CPU.
    Across 100 API servers: trivial.
  
  15B checks/day on DB:
    15B DB queries.
    DB is completely destroyed.
    Impossible.
  
  JWT self-contained = auth scales infinitely.
```

---

### Password Authentication Flow

```
POST /auth/login
{ email: "ravi@infosys.com", password: "password123" }

Step 1 — Rate limiting (brute force protection):
  Redis INCR "auth:attempts:ip:103.21.45.67:15min"
  If > 20: return 429 (IP rate limited).
  
  Check USER.failed_attempts.
  If > 10: check USER.locked_until.
  If locked: return 423 Account Locked.

Step 2 — Load user:
  SELECT * FROM user
  WHERE email = 'ravi@infosys.com'
  AND org_id = 'infosys-uuid'
  AND is_active = true
  
  Not found: return 401 "Invalid credentials"
  (NOT "user not found" — reveals valid emails to attackers)

Step 3 — Check org auth config:
  SELECT * FROM org_auth_config WHERE org_id = 'infosys'
  
  auth_type = SSO_ONLY:
    Return: "Infosys uses SSO. Login via company portal."
  
  auth_type = PASSWORD or PASSWORD_AND_SSO:
    Continue.

Step 4 — Verify password:
  DB stores: bcrypt hash of password. NOT plaintext.
  
  bcrypt.verify("password123", user.password_hash)
  
  Why bcrypt:
    One-way hash: cannot reverse to get original password.
    Slow by design: 100ms per check.
    Attacker steals DB, has hashes.
    Brute force at 100ms/attempt:
    10 billion attempts = 317 years.
    Useless stolen DB.
  
  If mismatch:
    INCREMENT failed_attempts.
    If > 10: SET locked_until = NOW() + 30 minutes.
    Return 401.

Step 5 — MFA (if enabled):
  user.mfa_enabled = true:
    Return intermediate: { status: "MFA_REQUIRED", mfa_token: "temp" }
    Store temp token in Redis (TTL 5 minutes).
    
    User opens Google Authenticator. Gets 6-digit code.
    POST /auth/mfa { mfa_token: "temp", code: "482936" }
    
    Server:
      Load user from temp token (Redis lookup).
      Get user's TOTP secret (encrypted in DB).
      Compute expected code:
        counter = floor(unix_time / 30)  ← 30-second window
        expected = HOTP(secret, counter)
      
      Check submitted code matches expected.
      Also check previous window (±1) for clock skew.
      
      Mismatch: 401 Invalid MFA code.

Step 6 — Issue tokens:
  Generate JWT access token:
  {
    sub:    "ravi-uuid",
    org_id: "infosys-uuid",
    role:   "Standard",
    exp:    NOW() + 2 hours,
    jti:    new UUID
  }
  Sign with private key (AWS KMS).
  
  Generate refresh token:
    Random 256-bit cryptographically secure string.
    Hash it: SHA-256(raw_token) = stored_hash.
    (Never store raw refresh token — same as passwords)
  
  Store refresh token in DB:
  {
    token_hash:    SHA256(raw_token),
    user_id:       "ravi-uuid",
    org_id:        "infosys-uuid",
    issued_at:     NOW(),
    expires_at:    NOW() + 90 days,      ← absolute expiry
    idle_expires:  NOW() + 7 days,       ← idle expiry
    device_info:   { type: "browser" }
  }
  
  Reset failed_attempts = 0.
  Update last_login_at = NOW().
  Log LOGIN_SUCCESS to audit.
  
  Return:
  {
    access_token:  "eyJ...",
    token_type:    "Bearer",
    expires_in:    7200,
    refresh_token: "raw-token-never-store-client-side-in-localstorage"
  }
  
  access_token:  stored in browser MEMORY (not localStorage).
  refresh_token: stored in httpOnly cookie.
```

---

### Refresh Token Lifecycle

```
TWO EXPIRY DIMENSIONS:

ABSOLUTE EXPIRY (hard limit, never extends):
  90 days from initial login.
  Clock starts at login. Never resets.
  Even if used every day: expires at day 90.
  Only full re-login resets this.

IDLE EXPIRY (sliding window, resets on use):
  7 days of inactivity.
  Every time refresh token is used: resets to 7 days.
  Don't use app for 7 days: must re-login.
  Use app daily: idle timer keeps resetting.

ROTATION (security mechanism):
  Every time refresh token used to get new access token:
  OLD refresh token: INVALIDATED immediately.
  NEW refresh token: issued with SAME absolute expiry.
                     New idle expiry (7 more days).
  
  Why same absolute expiry:
    Rotation gives new token VALUE (one-time-use security).
    Not new expiry DATE.
    Clock never resets.
    Cannot stay logged in forever by rotating.

THEFT DETECTION:
  Attacker steals refresh token at Day 10.
  Legitimate user: uses token at Day 10. Token rotated.
  Attacker: tries to use OLD token at Day 11.
  Server: old token was already rotated = REUSE DETECTED.
  
  Server response:
    Invalidate ENTIRE token family.
    (All tokens descended from the original login)
    Legitimate user forced to re-login.
    Attacker's tokens all invalidated.
    Alert security team: possible token theft.

STORAGE:
  httpOnly cookie (not localStorage).
  
  Why not localStorage:
    JavaScript can read localStorage.
    XSS attack: malicious script reads token.
    SADD to attacker's server. Account compromised.
  
  httpOnly cookie:
    Browser sends automatically with requests.
    JavaScript CANNOT read it (document.cookie excludes it).
    XSS cannot steal it.
    SameSite=Strict prevents CSRF.
    Path=/auth/token: only sent to refresh endpoint.
    Not sent with every API call.
```

---

### OIDC SSO with Microsoft — Complete Flow

```
SETUP (one-time by Infosys IT admin):
  Register Salesforce as app in Microsoft Azure AD.
  Microsoft issues: client_id, client_secret, tenant_id.
  Infosys admin enters these in Salesforce SSO config.
  Salesforce stores in ORG_AUTH_CONFIG.
  Admin registers redirect_uri in Azure AD:
    https://infosys.salesforce.com/auth/callback
  Only pre-registered URIs accepted. Security.

RUNTIME — Ravi visits infosys.salesforce.com:

Step 1 — Salesforce detects SSO org:
  Load ORG_AUTH_CONFIG for infosys.
  auth_type = SSO_ONLY. Protocol = OIDC.
  
  Generate state: random string "f8k2m9x7"
  Store in Redis: "oauth_state:f8k2m9x7" → { org_id: infosys }
  TTL: 5 minutes.
  
  Generate PKCE:
  code_verifier  = random 64 bytes = "dBjft..."
  code_challenge = BASE64URL(SHA256(code_verifier)) = "E9Mh..."
  Store code_verifier in Redis: "pkce:f8k2m9x7" → "dBjft..."
  
  Redirect browser to Microsoft:
  GET login.microsoftonline.com/infosys-tenant/authorize
    ?client_id=sf-app-id
    &response_type=code
    &redirect_uri=https://infosys.salesforce.com/auth/callback
    &scope=openid email profile
    &state=f8k2m9x7
    &code_challenge=E9Mh...
    &code_challenge_method=S256
    &prompt=none         ← try silent first (session cookie)
    &login_hint=ravi@infosys.com

Step 2 — Microsoft handles authentication:
  Browser carries Microsoft's session cookie automatically.
  (Browser sends cookies to their origin domain always)
  Microsoft reads ITS OWN cookie.
  
  IF session valid (prompt=none succeeds):
    Microsoft issues auth code immediately.
    No login form shown. Ravi sees brief redirect.
  
  IF no session (prompt=none fails):
    Microsoft returns error=login_required.
    Salesforce retries WITHOUT prompt=none.
    Microsoft shows login form.
    Ravi enters Microsoft credentials.
    Microsoft sets new session cookie.
    Issues auth code.

Step 3 — Browser redirected back to Salesforce:
  GET https://infosys.salesforce.com/auth/callback
    ?code=one-time-code-xyz
    &state=f8k2m9x7

Step 4 — Salesforce validates state:
  Redis GET "oauth_state:f8k2m9x7"
  Found → state valid.
  Not found → CSRF attack → reject.
  
  Why state protects against CSRF:
    Attacker runs their own OAuth flow.
    Gets callback URL with their own code + their state.
    Tricks Ravi into visiting that URL.
    State in URL = attacker's state.
    Ravi's session has NO state (never started a flow).
    Mismatch → rejected.
    Attacker cannot know what state Ravi's session has.

Step 5 — Token exchange (server to server):
  POST login.microsoftonline.com/infosys-tenant/token
  {
    grant_type:    "authorization_code",
    code:          "one-time-code-xyz",
    redirect_uri:  "https://infosys.salesforce.com/auth/callback",
    client_id:     "sf-app-id",
    client_secret: "sf-app-secret",
    code_verifier: "dBjft..."  ← PKCE verifier
  }
  
  PKCE verification by Microsoft:
    Computes: BASE64URL(SHA256("dBjft...")) = "E9Mh..."
    Matches stored code_challenge? YES.
    Code exchange allowed.
  
  Microsoft returns:
  {
    id_token:     "eyJ..."   ← JWT with user claims
    access_token: "ms-token" ← for Microsoft Graph (we discard)
    expires_in:   3600
  }

Step 6 — Extract user identity from id_token:
  Decode JWT payload:
  {
    "email": "ravi@infosys.com",
    "name":  "Ravi Kumar",
    "groups": ["group-sales-team", "group-all-employees"],
    "iss": "https://login.microsoftonline.com/infosys-tenant"
  }
  
  Verify:
    Signature: using Microsoft's public key from JWKS endpoint.
    Issuer: matches configured Microsoft tenant.
    Audience: matches our client_id.
    Expiry: not expired.

Step 7 — Map to Salesforce user:
  EMAIL IS THE BRIDGE KEY.
  Microsoft knows: ravi@infosys.com is valid.
  Salesforce finds: user WHERE email = 'ravi@infosys.com'
                            AND org_id = 'infosys'
  
  FOUND:
    Use existing Salesforce user and their role.
    Update last_login_at.
  
  NOT FOUND + auto_provision = true:
    Create Salesforce user from id_token claims.
    Assign role based on group mapping.
    No password set (SSO-only user).
  
  NOT FOUND + auto_provision = false:
    Error: "Account not found. Contact your admin."

Step 8 — Role assignment from Microsoft groups:
  ORG_AUTH_CONFIG role_mapping:
  {
    "group-salesforce-admin":  "System Administrator",
    "group-sales-managers":    "Sales Manager",
    "group-sales-reps":        "Standard User",
    "group-read-only":         "Read Only"
  }
  
  id_token groups: ["group-sales-reps", "group-all-employees"]
  First match: "group-sales-reps" → "Standard User"
  Ravi gets Standard User role.
  
  Role changes in Microsoft (Ravi promoted to manager):
    IT adds Ravi to "group-sales-managers".
    Next login: role = "Sales Manager". Automatic.

Step 9 — Issue Salesforce tokens:
  Same as password login step 6.
  Issue JWT access token + refresh token.
  Microsoft's tokens DISCARDED.
  Salesforce manages its own session from here.

Step 10 — SCIM for real-time provisioning:
  Microsoft pushes user changes to Salesforce without login:
  
  New employee joins:
    Microsoft → POST /scim/v2/Users → Salesforce creates user.
    Before they ever log in.
  
  Employee promoted:
    Microsoft → PATCH /scim/v2/Users/{id} → role updated immediately.
  
  Employee leaves:
    Microsoft → PATCH { active: false } → Salesforce deactivates.
    Their current JWT: add jti to Redis blocklist.
    They're logged out within seconds.
    Even mid-session.
```

---

### Token Revocation

```
NORMAL LOGOUT:
  User clicks logout.
  Delete refresh token from DB.
  Access token: let it expire naturally (< 2 hours).
  No need to blocklist (low urgency).

URGENT REVOCATION (fired employee):
  Add JWT ID (jti) to Redis blocklist:
  SET "blocklist:jti:uuid" "revoked" EX 7200
  TTL = remaining token lifetime.
  
  API Gateway on every request:
  Check Redis: EXISTS "blocklist:jti:{jti}"
  YES → 401 Token Revoked.
  NO  → proceed.
  
  Redis check: < 1ms.
  Only revoked tokens in blocklist.
  Normal tokens: no blocklist entry.
  Blocklist is tiny.
  Performance impact: negligible.
  
  Combined: fired employee loses access within seconds.
  Not "within 2 hours when their JWT expires."
```

---

### Data Model

```
USER:
  id, org_id, email (unique per org)
  full_name, password_hash, salt
  mfa_enabled, mfa_secret (encrypted)
  role, is_active
  failed_attempts, locked_until
  last_login_at, created_at

ORG_AUTH_CONFIG:
  org_id
  auth_type: PASSWORD_ONLY / SSO_ONLY / BOTH
  sso_protocol: SAML / OIDC
  idp_name: "Microsoft Azure AD"
  idp_entity_id, idp_sso_url
  idp_certificate: (Microsoft's public cert)
  client_id, client_secret (encrypted)
  tenant_id
  attribute_mapping: JSON
  role_mapping: JSON
  auto_provision: BOOLEAN

REFRESH_TOKEN:
  id, token_hash (SHA-256 of raw token)
  user_id, org_id
  issued_at, expires_at (absolute, 90 days)
  idle_expires_at (sliding, 7 days)
  is_revoked, replaced_by
  device_info: JSON

AUTH_CODE (short-lived, one-time use):
  code, org_id, user_id, client_id
  redirect_uri, scopes
  expires_at (60 seconds), is_used
  code_challenge (PKCE)
```

---

### Interview Summary

> "The auth system uses JWT RS256 tokens for stateless validation — every API server caches the public key in memory and validates tokens with pure CPU computation, enabling 15 billion daily auth checks without a single DB call. The three-token model has an access token (2hr, in browser memory), a refresh token (90-day absolute + 7-day idle, httpOnly cookie to prevent XSS theft), and a Microsoft SSO session cookie at Microsoft's domain. For SSO, Salesforce always tries prompt=none first — redirecting the browser to Microsoft with prompt=none so Microsoft reads its own session cookie and issues an auth code silently without showing a login form; if that fails with login_required, Salesforce retries without prompt=none for the full login flow. Salesforce never reads Microsoft's cookie — the browser carries it automatically when following the redirect. Email is the bridge key mapping Microsoft identity to Salesforce user, with Microsoft groups mapped to Salesforce roles via org-configured role_mapping. Refresh token rotation detects theft by invalidating the old token on each use — if both attacker and legitimate user try to use the same token, the reuse is detected, the entire token family is invalidated, and the user is forced to re-login. Urgent revocation adds the JWT's jti to a Redis blocklist checked on every API request at sub-millisecond cost."

---

### Key Design Decisions — Q7, Q8, Q10

| Question | Decision | Choice | Why |
|----------|----------|--------|-----|
| Q7 | Byte storage | AWS S3 | 11-nine durability, unlimited scale, not our core competency |
| Q7 | S3 key design | Content-addressed (SHA-256) | Same content = same key = deduplication built in |
| Q7 | Upload mechanism | S3 Multipart via chunked API | Resume on failure, app server as pipe not bucket |
| Q7 | Download | Pre-signed URL redirect | Offloads bandwidth to S3, access control before URL generation |
| Q7 | Dedup security | Isolation at metadata layer | S3 sharing transparent, org_id check protects access |
| Q8 | Architecture | Lambda (batch + stream) | Stream for freshness, batch for accuracy, each covers the other |
| Q8 | Stream counters | Redis INCRBYFLOAT | Atomic, no race condition, never read-then-write |
| Q8 | ClickHouse inserts | Micro-batch 10K rows | Per-event inserts create too many parts, performance disaster |
| Q8 | High concurrency | Materialised views | Pre-aggregate at write time, query 36M vs 7.5B rows |
| Q10 | Token format | JWT RS256 | Self-contained, zero DB calls for validation, scales infinitely |
| Q10 | Refresh token storage | SHA-256 hash in DB | Never store raw token, same as passwords |
| Q10 | httpOnly cookie | Refresh token in cookie | XSS cannot steal it, JavaScript cannot read httpOnly |
| Q10 | SSO silent auth | Always try prompt=none first | Cannot know if Microsoft session is valid without trying |
| Q10 | User mapping | Email as bridge key | Microsoft authenticates, Salesforce authorizes, email connects them |
| Q10 | Urgent revocation | Redis jti blocklist | JWT self-contained cannot be revoked without blocklist or short expiry |

---

Shall I continue with Q12, Q13, Q14 together?
