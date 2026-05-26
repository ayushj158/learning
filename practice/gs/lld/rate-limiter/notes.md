## Rate Limiter — Complete Summary

---

## What is a Rate Limiter?

A rate limiter controls how many requests a client can make to a resource in a given time window. Without it:

```
Trade API: 1000 requests/sec from one client
→ server overwhelmed
→ other clients starved
→ system crashes
```

With rate limiter:
```
Trade API: configured 5 requests/sec per client
→ 6th request blocked
→ server protected
→ fair usage enforced
```

**Real world at Goldman Sachs:**
- Trading API — limit orders per second per desk
- Market data feed — limit subscriptions per client
- Risk engine — limit concurrent calculations per user

---

## Two Strategies

### Fixed Window
```
Window = 1 sec, limit = 3

|----1sec----|----1sec----|
 req req req  req req req
 ✓   ✓   ✓   ✓   ✓   ✓

Weakness — burst at boundary:
t=0.9s: 3 requests ✓
t=1.1s: 3 requests ✓
→ 6 requests in 0.2 seconds!
```

### Token Bucket
```
Capacity = 3, refill = 1 token/sec

Start: [●●●] 3 tokens
req1:  [●●]  consumed 1 ✓
req2:  [●]   consumed 1 ✓
req3:  []    consumed 1 ✓
req4:  []    blocked   ✗

After 1 sec: [●] refilled 1
req5:  []    consumed 1 ✓
```
Smoother — no hard boundary burst problem.

---

## Design Pattern — Strategy Pattern

Rate limiter needs to support multiple algorithms (Fixed Window, Token Bucket) without changing the core `RateLimiter` class. That's exactly what **Strategy Pattern** solves.

```
RateLimitStrategy (interface)
        │
        ├── FixedWindow
        └── TokenBucket

RateLimiter
        └── Map<resourceId, RateLimitStrategy>
```

`RateLimiter` doesn't know or care which algorithm is used — it just calls `strategy.isAllowed()`. New strategies can be added without touching `RateLimiter`.

---

## Implementation

### Version 1 — Non Thread-Safe (single thread)

```java
interface RateLimitStrategy {
    boolean isAllowed();
}

class FixedWindow implements RateLimitStrategy {
    private final int limit;
    private final long windowSizeMs;
    private int counter;
    private long windowStart;

    public FixedWindow(int limit, long windowSizeMs) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
        this.counter = 0;
        this.windowStart = System.currentTimeMillis();
    }

    public boolean isAllowed() {
        long now = System.currentTimeMillis();

        // reset window if expired
        if (now - windowStart > windowSizeMs) {
            counter = 0;
            windowStart = now;
        }

        // check and consume
        if (counter < limit) {
            counter++;
            return true;
        }
        return false;
    }
}

class RateLimiter {
    private final Map<String, RateLimitStrategy> resourceMap 
                                            = new HashMap<>();

    public void register(String resourceId, RateLimitStrategy strategy) {
        resourceMap.put(resourceId, strategy);
    }

    public boolean isAllowed(String resourceId) {
        RateLimitStrategy strategy = resourceMap.get(resourceId);
        if (strategy == null) return true;  // fail-open
        return strategy.isAllowed();
    }
}
```

**Problem:**
```
Thread1: reads counter=4
Thread2: reads counter=4
Thread1: counter++ → 5 returns true
Thread2: counter++ → 5 returns true  ← both allowed, limit breached!
```

---

### Version 2 — Thread-Safe Single Server

Lock lives **inside the strategy** — so each resource has its own lock and resources never block each other.

```java
class FixedWindow implements RateLimitStrategy {
    private final int limit;
    private final long windowSizeMs;
    private int counter;
    private long windowStart;
    private final ReentrantLock lock = new ReentrantLock(); // ← per resource

    public FixedWindow(int limit, long windowSizeMs) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
        this.counter = 0;
        this.windowStart = System.currentTimeMillis();
    }

    public boolean isAllowed() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();

            if (now - windowStart > windowSizeMs) {
                counter = 0;
                windowStart = now;
            }

            if (counter < limit) {
                counter++;
                return true;
            }
            return false;
        } finally {
            lock.unlock(); // always releases even if exception thrown
        }
    }
}

// RateLimiter uses ConcurrentHashMap now — map reads are thread-safe
class RateLimiter {
    private final Map<String, RateLimitStrategy> resourceMap 
                                        = new ConcurrentHashMap<>();

    public void register(String resourceId, RateLimitStrategy strategy) {
        resourceMap.put(resourceId, strategy);
    }

    public boolean isAllowed(String resourceId) {
        RateLimitStrategy strategy = resourceMap.get(resourceId);
        if (strategy == null) return true;
        return strategy.isAllowed();  // strategy handles its own locking
    }
}
```

**Why lock inside strategy not RateLimiter:**

```
Global lock — BAD:
Thread1 → trade-api    LOCKED
Thread2 → market-data  WAITING ← unnecessarily blocked!

Per resource lock — GOOD:
Thread1 → trade-api    LOCKED
Thread2 → market-data  RUNNING ← independent, no blocking
```

---

### Version 3 — Distributed Servers

**Problem with in-memory:**
```
Server1: trade-api counter=4
Server2: trade-api counter=4

Request → Server1: counter=5 ✓
Request → Server2: counter=5 ✓  ← 6 requests passed, limit was 5!
```

**Solution — Redis as shared counter:**

```
Each server talks to same Redis instance.
Redis INCR is atomic — no race condition.
Redis EXPIRE resets the window automatically.
```

```java
class FixedWindowRedis implements RateLimitStrategy {
    private final JedisPool jedisPool;  // Redis connection pool
    private final String resourceId;
    private final int limit;
    private final long windowSizeSeconds;

    public FixedWindowRedis(JedisPool jedisPool, String resourceId,
                             int limit, long windowSizeSeconds) {
        this.jedisPool = jedisPool;
        this.resourceId = resourceId;
        this.limit = limit;
        this.windowSizeSeconds = windowSizeSeconds;
    }

    public boolean isAllowed() {
        try (Jedis jedis = jedisPool.getResource()) {
            // INCR is atomic in Redis — no race condition across servers
            long count = jedis.incr(resourceId);

            if (count == 1) {
                // first request in window — set expiry
                jedis.expire(resourceId, windowSizeSeconds);
            }

            return count <= limit;
        }
    }
}
```

**How Redis handles it:**
```
Server1 → Redis: INCR trade-api → 1, EXPIRE 1sec
Server2 → Redis: INCR trade-api → 2
Server1 → Redis: INCR trade-api → 3
...
Server1 → Redis: INCR trade-api → 6 → 6 > 5 → blocked ✗
```

Single counter shared across all servers. Atomic. No race condition.

---

## Full Picture — What to Say in Interview

```
"I'd use Strategy Pattern so Fixed Window and Token Bucket 
are interchangeable without changing RateLimiter.

For single server — ReentrantLock inside each strategy 
instance gives per-resource locking so trade-api and 
market-data-api never block each other.

For distributed — push the counter to Redis. INCR is atomic 
across nodes, EXPIRE handles window reset. For Token Bucket 
distributed, wrap check-and-decrement in a Lua script for 
atomicity.

Fail-open on unknown resources — better to allow than to 
accidentally block legitimate traffic."
```

---

## Design Decisions at a Glance

| Decision | Why |
|---|---|
| Strategy Pattern | Swap algorithms without changing RateLimiter |
| Lock inside strategy | Per-resource locking, no cross-resource blocking |
| `ConcurrentHashMap` | Thread-safe map reads |
| `finally` for unlock | Guarantees release even on exception |
| Fail-open for unknown | Avoid accidental blocking |
| Redis for distributed | Atomic INCR, single source of truth |
| Lua script for Token Bucket | Atomic check-and-decrement across servers |

---

Great catch — this is a real-world concern. In GS context:

```
trade-api:
  - Desk A → 100 requests/sec  (high frequency trading desk)
  - Desk B → 10 requests/sec   (retail client)
  - Desk C → 5 requests/sec    (external partner)
```

Same resource, different limits per client.

---

## The fix is simple — composite key

Instead of:
```java
Map<String, RateLimitStrategy> resourceMap
key = "trade-api"
```

Use:
```java
Map<String, RateLimitStrategy> resourceMap
key = "trade-api:deskA"
key = "trade-api:deskB"
key = "trade-api:deskC"
```

---

## Updated RateLimiter

```java
class RateLimiter {
    private final Map<String, RateLimitStrategy> resourceMap 
                                        = new ConcurrentHashMap<>();

    public void register(String resourceId, String clientId, 
                                        RateLimitStrategy strategy) {
        String key = buildKey(resourceId, clientId);
        resourceMap.put(key, strategy);
    }

    public boolean isAllowed(String resourceId, String clientId) {
        String key = buildKey(resourceId, clientId);
        RateLimitStrategy strategy = resourceMap.get(key);
        if (strategy == null) return true;  // fail-open
        return strategy.isAllowed();
    }

    private String buildKey(String resourceId, String clientId) {
        return resourceId + ":" + clientId;
    }
}
```

---

## Usage

```java
RateLimiter limiter = new RateLimiter();

// same resource, different limits per client
limiter.register("trade-api", "deskA", new FixedWindow(100, 1000));
limiter.register("trade-api", "deskB", new FixedWindow(10, 1000));
limiter.register("trade-api", "deskC", new FixedWindow(5, 1000));

limiter.isAllowed("trade-api", "deskA"); // uses 100 req/sec limit
limiter.isAllowed("trade-api", "deskB"); // uses 10 req/sec limit
```

---

## For Redis distributed version

Same composite key idea:
```java
String redisKey = resourceId + ":" + clientId;
long count = jedis.incr(redisKey);
```

Each `trade-api:deskA`, `trade-api:deskB` gets its own Redis counter — completely independent.

---

## What to say in interview

*"The composite key `resourceId:clientId` gives us independent rate limiting per client per resource. No code change needed in the strategy — just the key changes. In Redis this maps naturally to separate keys per client."*

---

Anything else to revise before Token Bucket?


## Token Bucket — `isAllowed()`

You already know the logic from earlier. Just two steps:

**Step 1 — Refill based on elapsed time**
**Step 2 — Check and consume**

```java
class TokenBucket implements RateLimitStrategy {
    private final int capacity;
    private final double refillRatePerMs;  // tokens per millisecond
    private double currentTokens;
    private long lastRefillTime;
    private final ReentrantLock lock = new ReentrantLock();

    public TokenBucket(int capacity, double refillRatePerMs) {
        this.capacity = capacity;
        this.refillRatePerMs = refillRatePerMs;
        this.currentTokens = capacity;  // starts full
        this.lastRefillTime = System.currentTimeMillis();
    }

    public boolean isAllowed() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();

            // step 1: refill
            long elapsed = now - lastRefillTime;
            currentTokens = Math.min(capacity, 
                              currentTokens + elapsed * refillRatePerMs);
            lastRefillTime = now;

            // step 2: check and consume
            if (currentTokens >= 1) {
                currentTokens--;
                return true;
            }
            return false;

        } finally {
            lock.unlock();
        }
    }
}
```

---

## Usage

```java
// 5 requests per second = 5 tokens capacity, refill 0.005 per ms
RateLimiter limiter = new RateLimiter();
limiter.register("trade-api", "deskA", 
    new TokenBucket(5, 0.005));  // 5 tokens, 5/1000ms = 0.005/ms

limiter.register("trade-api", "deskB",
    new FixedWindow(10, 1000));  // different strategy, same RateLimiter
```

---

## Trace

```
capacity=3, refillRate=0.001/ms (1 token/sec), starts full

t=0ms:    tokens=3
isAllowed: elapsed=0, refill=0, tokens=3 → consume → tokens=2 ✓

t=300ms:  
isAllowed: elapsed=300, refill=0.3, tokens=2.3 → consume → tokens=1.3 ✓

t=700ms:  
isAllowed: elapsed=400, refill=0.4, tokens=1.7 → consume → tokens=0.7 ✓

t=900ms:  
isAllowed: elapsed=200, refill=0.2, tokens=0.9 → 0.9 < 1 → blocked ✗

t=1500ms: 
isAllowed: elapsed=600, refill=0.6, tokens=1.5 → consume → tokens=0.5 ✓
```

---

## Fixed Window vs Token Bucket — one liner difference

| | Fixed Window | Token Bucket |
|---|---|---|
| State | counter + windowStart | currentTokens + lastRefillTime |
| Reset | hard reset at window end | continuous gradual refill |
| Burst | dangerous at boundary | handled smoothly |
| Complexity | simpler | slightly more complex |

---

Rate Limiter ✅ fully closed.

**Next — TTL Cache.** Ready?

## Real Trading Scenario

Imagine a trading desk running an algorithm. At 9:30am market opens — they need to fire 20 orders **instantly** to capture the opening price. Miss the window and the opportunity is gone.

---

## Fixed Window behaviour

```
limit=5, window=1sec

9:30:00.000  req1  → counter=1 ✓
9:30:00.001  req2  → counter=2 ✓
9:30:00.002  req3  → counter=3 ✓
9:30:00.003  req4  → counter=4 ✓
9:30:00.004  req5  → counter=5 ✓
9:30:00.005  req6  → BLOCKED ✗
...
9:30:00.020  req20 → BLOCKED ✗
```

15 orders never got placed. Trading desk lost money. **Fixed Window can't distinguish between abuse and legitimate burst.**

---

## Token Bucket behaviour

```
capacity=20, refillRate=5/sec

9:30:00.000  tokens=20
req1  → tokens=19 ✓
req2  → tokens=18 ✓
...
req20 → tokens=0  ✓   ← all 20 burst orders placed!

9:30:01.000  refill 5 tokens → tokens=5
9:30:01.001  req21 → tokens=4 ✓  (back to sustained rate)
```

All 20 burst orders go through. After burst, sustained rate kicks in at 5/sec. **System protected AND trading desk happy.**

---

## The mental model

```
Fixed Window:  security guard counting people per minute
               6th person in same minute → turned away
               doesn't matter if next 50 minutes are empty

Token Bucket:  prepaid mobile data
               you have 20GB saved up
               stream a movie → uses burst allowance instantly
               but recharges at 5GB/month going forward
```

---

## Why this matters at GS specifically

Different desks have different burst profiles:

| Desk | Burst need | Sustained rate |
|---|---|---|
| HFT desk | 1000 orders at open | 50/sec |
| Retail desk | 10 orders at open | 2/sec |
| Risk engine | 500 recalculations on market event | 10/sec |

Token Bucket handles all of these with just two parameters — capacity and refillRate. Fixed Window can't model burst at all.

---

Clear? Ready for TTL Cache now?


> can i check if we manage resource and desk as one unit instance the number of instances grows massively ...how can we do efficient and scalable locking but do strategy object per resource only and redis hold configs for each customer(desk A) ....also asses in prod system which is pattern to be applooed

Great question — you're thinking like a real architect now. Let me address both.

---

## Problem — too many strategy instances

```
Resources: 1000 APIs
Desks:     500 trading desks
Combos:    1000 * 500 = 500,000 strategy instances!

Each has:
  ReentrantLock object
  counter/tokens
  timestamps
  
→ massive memory footprint
→ GC pressure
→ not scalable
```

---

## Solution 1 — Strategy per resource, config per client

Instead of strategy per resource+client combination:

```
Strategy per resource (1000 instances only):
  trade-api → FixedWindowStrategy
  market-data → TokenBucketStrategy

Config per client stored separately:
  trade-api:deskA → limit=100/sec
  trade-api:deskB → limit=10/sec
  
Strategy instance reads client config dynamically:
  isAllowed(resourceId, clientId):
    config = configService.getConfig(resourceId, clientId)
    → uses config.limit for THIS client
```

```java
class FixedWindow implements RateLimitStrategy {
    // NO hardcoded limit — reads from config
    private final ConfigService configService;
    
    // ONE counter map for ALL clients of this resource
    private final ConcurrentHashMap<String, AtomicInteger> counters
                                        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> windowStarts
                                        = new ConcurrentHashMap<>();

    public boolean isAllowed(String resourceId, String clientId) {
        // get client-specific limit from config
        int limit = configService.getLimit(resourceId, clientId);
        
        // get/create counter for this client
        counters.computeIfAbsent(clientId, k -> new AtomicInteger(0));
        windowStarts.computeIfAbsent(clientId, k -> System.currentTimeMillis());

        long now = System.currentTimeMillis();
        long windowStart = windowStarts.get(clientId);

        // reset window if expired
        if (now - windowStart > 1000) {
            counters.get(clientId).set(0);
            windowStarts.put(clientId, now);
        }

        // atomic increment and check
        return counters.get(clientId).incrementAndGet() <= limit;
    }
}
```

Now:
```
Strategy instances: 1000 (one per resource)
Counter maps:       1000 maps, each with 500 entries
Memory:             ~1000 * 500 * (AtomicInteger + Long) = manageable
```

---

## Solution 2 — Stripe locking (fine-grained without per-client lock)

Instead of one lock per client — use a fixed pool of locks:

```java
private static final int STRIPE_COUNT = 256;
private final ReentrantLock[] stripes = new ReentrantLock[STRIPE_COUNT];

// initialize
for (int i = 0; i < STRIPE_COUNT; i++) {
    stripes[i] = new ReentrantLock();
}

// get lock for a client
private ReentrantLock getLock(String clientId) {
    int index = Math.abs(clientId.hashCode() % STRIPE_COUNT);
    return stripes[index];
}

public boolean isAllowed(String resourceId, String clientId) {
    ReentrantLock lock = getLock(clientId);
    lock.lock();
    try {
        // check and update counter
    } finally {
        lock.unlock();
    }
}
```

```
500,000 clients → 256 locks
clients hash to different stripes
collision probability = 1/256
→ 99.6% of clients never block each other
→ fixed memory for locks regardless of client count
```

This is exactly how ConcurrentHashMap works internally.

---

## Solution 3 — AtomicInteger per client (lock-free)

```java
private final ConcurrentHashMap<String, AtomicInteger> counters
                                    = new ConcurrentHashMap<>();

public boolean isAllowed(String clientId, int limit) {
    AtomicInteger counter = counters.computeIfAbsent(
        clientId, k -> new AtomicInteger(0));
    
    return counter.incrementAndGet() <= limit;
    // CAS operation — no explicit lock needed
}
```

Lock-free — uses hardware CAS. Scales to millions of clients.

---

## Redis for config — correct thinking

```
Config stored in Redis:
  HSET rate-limit:trade-api deskA 100
  HSET rate-limit:trade-api deskB 10
  HSET rate-limit:market-data deskA 500

Application reads on startup → caches locally
Hot reload — Redis pub/sub notifies when config changes
→ application updates local cache

Benefits:
  Config changes without restart
  Centralized — all servers see same config
  Fast — local cache for hot path
```

---

## Production pattern — which to apply?

For GS IB platform specifically:

```
Scale:     1000+ APIs, 500+ desks, millions of requests/sec
Latency:   <1ms requirement for trading APIs
HA:        99.99% uptime required
```

**Recommended production pattern:**

```
Layer 1 — API Gateway (Kong/Nginx):
  Coarse limits — block obvious abuse at edge
  Simple fixed window per IP/client
  No DB needed — in-memory only

Layer 2 — Local L1 Cache (per server):
  AtomicInteger per clientId per resource
  Stripe locking for thread safety
  Config from Redis with local cache
  Handles 99% of traffic without Redis call
  Window reset via scheduled thread

Layer 3 — Redis Cluster (L2, source of truth):
  Lua script for atomic check+increment
  EXPIRE for window management
  Only consulted on L1 miss or strict limits
  Composite key: resource:client:window

Layer 4 — Config Service:
  Per-client limits stored in Redis/DB
  Hot reload via pub/sub
  Tiered limits: GOLD=1000/s, SILVER=100/s, BRONZE=10/s
```

```
Request flow:
1. API Gateway coarse check (1ms)
2. L1 local AtomicInteger check (0.1ms)
3. If L1 miss → Redis Lua check (1-2ms)
4. If Redis down → L1 fallback with conservative limit
5. Log all blocks to Kafka for audit
```

---

## What to say in interview

*"One strategy instance per resource, not per resource+client — strategy holds a ConcurrentHashMap of AtomicInteger counters keyed by clientId. Stripe locking gives fixed memory for locks regardless of client count. Config per client stored in Redis with local cache for hot path — hot reload via pub/sub. For strict limits on critical APIs, Redis Lua script for distributed atomicity. API gateway handles coarse first-line defence."*

---

This is a genuine VP-level answer. Ready for **TTL Cache revision**?


