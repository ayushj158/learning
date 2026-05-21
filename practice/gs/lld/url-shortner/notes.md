Good thinking — but let me correct the UUID bit first, then we'll align on design.

---

## Short Code Generation

### Why not UUID
```
UUID = 128 bits = 32 hex chars → "x7Kp2" is only 5-6 chars
UUID is too long for a short URL
```

### Standard approach — Base62 encoding

```
Characters: a-z + A-Z + 0-9 = 62 chars
6 chars = 62^6 = 56 billion unique URLs ← more than enough
7 chars = 62^7 = 3.5 trillion
```

Two ways to generate:

**Option 1 — Auto-increment counter + Base62 encode:**
```
counter = 1 → Base62 → "000001"
counter = 2 → Base62 → "000002"
...
counter = 56billion → Base62 → "zzzzzz"
```

**Option 2 — Random Base62 string:**
```java
String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
// pick 6 random chars
```

**Which is better?**
- Counter → predictable, sequential, no collision
- Random → unpredictable (security), but needs collision check

For GS internal tool → counter is fine.
For public URL shortener → random preferred (users can't guess other URLs).

---

## Now system design — let's think at three levels

---

### Level 1 — Single server (CoderPad implementation)

```
Client → URLShortenerService → HashMap
```

Simple in-memory. Counter + Base62. No persistence.

---

### Level 2 — Multi server, production

```
Client → Load Balancer → App Server 1  ↘
                       → App Server 2  → Redis (cache) → DB (persistence)
                       → App Server 3  ↗
```

**Create flow:**
```
1. App server gets long URL
2. Generate short code:
   - Redis INCR global_counter → atomic, no collision across servers
   - Base62 encode counter value
3. Store in DB (source of truth)
4. Store in Redis cache (fast reads)
5. Return short code
```

**Resolve flow:**
```
1. Request comes in for short code "x7Kp2"
2. Check Redis cache first → cache hit → return longUrl (fast path)
3. Cache miss → check DB → store in Redis → return longUrl
4. If deactivated → return null
```

---

### Level 3 — High scale (mention verbally)

```
Client → CDN → Load Balancer → App Servers → Redis Cluster → DB Cluster
```

- CDN caches popular URLs at edge — resolves without hitting servers
- Redis cluster shards by short code
- DB sharded by short code range
- Read replicas for resolve (read-heavy workload)

---

## Persistence patterns to mention

### Write-through cache
```
create() → write to DB AND Redis simultaneously
Pros: cache always consistent
Cons: write latency higher
```

### Write-behind cache
```
create() → write to Redis immediately → async write to DB
Pros: fast writes
Cons: risk of data loss if Redis crashes before DB write
```

### Read-through cache
```
resolve() → check Redis → miss → load from DB → populate Redis
Pros: only hot URLs in cache
Cons: first request always slow (cache miss)
```

**For URL shortener — write-through for create, read-through for resolve.**

---

## Now let's code the CoderPad version

```java
class URLShortenerService {
    private final Map<String, URLEntry> store = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong(1);
    private static final String BASE_URL = "https://short.gs/";
    private static final String CHARS = 
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // encode counter to Base62
    private String encode(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(CHARS.charAt((int)(num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }

    public String create(String longUrl) {
        // your turn — generate code, create entry, store, return short URL
    }

    public String resolve(String shortCode) {
        // your turn — lookup, check active, return longUrl
    }

    public void deactivate(String shortCode) {
        // your turn — soft delete
    }

    public URLEntry getDetails(String shortCode) {
        // your turn — return full entry
    }
}

class URLEntry {
    String shortCode;
    String longUrl;
    boolean isActive;
    long createdAt;

    public URLEntry(String shortCode, String longUrl) {
        this.shortCode = shortCode;
        this.longUrl   = longUrl;
        this.isActive  = true;
        this.createdAt = System.currentTimeMillis();
    }
}
```

Fill in the four methods — they're all short. Give it a go.

Let me explain all three things clearly.

---

## UUID — 128 bits and 32 chars relationship

```
128 bits of data
→ each hex character represents 4 bits
→ 128 / 4 = 32 hex characters

Example:
550e8400-e29b-41d4-a716-446655440000
← 32 hex chars, hyphens are just formatting →
```

Hex uses 16 characters (0-9, a-f). Each hex char = 4 bits.

```
4 bits can represent 0-15 → 16 values → 1 hex char
8 bits = 1 byte → 2 hex chars
128 bits = 16 bytes → 32 hex chars
```

That's the relationship — purely about how many bits each character encodes.

---

## Why UUID is bad for short URLs

```
UUID:    550e8400-e29b-41d4-a716-446655440000  ← 32 chars
Short:   x7Kp2                                 ← 5 chars
```

Short URL needs to be SHORT — 5-7 chars. UUID is 32. Too long.

---

## Option 1 — Counter + Base62

**Step 1 — get unique number**

In single server:
```java
AtomicLong counter = new AtomicLong(1);
long id = counter.incrementAndGet();  // 1, 2, 3, 4...
```

In distributed (multiple servers):
```
Server1: Redis INCR global_counter → returns 1
Server2: Redis INCR global_counter → returns 2
Server3: Redis INCR global_counter → returns 3
```

INCR is atomic in Redis — two servers can never get the same number. It has nothing to do with randomness — it's just a globally unique sequential number.

**Step 2 — encode number to short string via Base62**

Base62 uses 62 characters: `a-z + A-Z + 0-9`

```
counter=1   → Base62 → "b"
counter=100 → Base62 → "bM"
counter=999 → Base62 → "g7"
```

How encoding works:
```
num = 100
100 % 62 = 38 → chars[38] = 'M'
100 / 62 = 1
1  % 62 = 1  → chars[1]  = 'b'
1  / 62 = 0  → stop

reverse → "bM"
```

Just like converting decimal to binary — but base 62 instead of base 2.

**Result:**
```
Counter 1       → "b"
Counter 1000    → "g8"
Counter 1000000 → "4c92"
```

Short, unique, no collision possible — each counter value maps to exactly one Base62 string.

---

## Option 2 — Random Base62

Generate a random 6-character string from Base62 alphabet:

```java
String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
Random random = new Random();

String generateCode() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 6; i++) {
        sb.append(CHARS.charAt(random.nextInt(62)));
    }
    return sb.toString();
}
```

**Result:**
```
call 1 → "x7Kp2m"
call 2 → "aB9Tz1"
call 3 → "x7Kp2m"  ← COLLISION! same as call 1
```

Random strings CAN collide — two calls can generate the same code.

**How to handle collisions:**
```java
String create(String longUrl) {
    String code;
    do {
        code = generateRandomCode();        // generate
    } while (store.containsKey(code));      // retry if collision

    store.put(code, new URLEntry(code, longUrl));
    return BASE_URL + code;
}
```

Retry until unique. With 62^6 = 56 billion possibilities, collision probability is extremely low — but theoretically possible.

---

## Counter vs Random — comparison

| | Counter + Base62 | Random Base62 |
|---|---|---|
| Collision | Impossible | Possible, needs retry |
| Predictable | Yes — users can guess sequential URLs | No — unpredictable |
| Distributed | Redis INCR — atomic counter | Any server generates independently |
| Security | Low — sequential | Higher — random |
| Complexity | Simple | Slightly more (retry loop) |

**For GS internal tool** → Counter + Base62. Simple, no collisions, sequential fine.
**For public URL shortener** → Random. Users shouldn't guess each other's URLs.

---

## Full picture — how Redis INCR fits

```
User1 → Server1: create("https://gs.com/trading")
Server1 → Redis: INCR global_counter → 1001
Server1: Base62(1001) → "g9"
Server1 → DB: store {g9 → https://gs.com/trading}
Server1 → Redis: cache {g9 → https://gs.com/trading}
Return: "https://short.gs/g9"

User2 → Server2: create("https://gs.com/risk")
Server2 → Redis: INCR global_counter → 1002  ← atomic, never 1001 again
Server2: Base62(1002) → "ga"
...
```

Redis INCR doesn't generate a random URL — it gives a unique number. Base62 encoding turns that number into a short string. Two completely separate steps.

---

Clear on all three? Now code the four methods in CoderPad version.


You've got the high level right. Let me refine and fill the gaps.

---

## Create Flow — refined

Your flow is almost right but one correction — you don't push SnowflakeID to Redis and DB separately. Let me show the correct sequence:

```
Client → Server: create("https://gs.com/trading")

Step 1: Generate SnowflakeID locally
        id = snowflake.nextId()  → 1234567890

Step 2: Base62 encode
        shortCode = Base62.encode(id) → "x7Kp2"

Step 3: Write-through — DB first, then Redis
        DB.insert(shortCode, longUrl, createdAt, isActive=true)
        Redis.set(shortCode, longUrl, TTL=24hrs)

Step 4: Return
        return "https://short.gs/" + shortCode
```

**Why DB first, then Redis?**

```
Redis first approach:
  Redis.set → success
  DB.insert → FAILS
  → Redis has URL, DB doesn't
  → data lost on Redis eviction/crash ✗

DB first approach:
  DB.insert → success
  Redis.set → FAILS
  → DB has URL, Redis doesn't
  → next get() is cache miss → loads from DB ✓
  → eventually consistent, no data loss ✓
```

DB is source of truth. Redis is just a cache.

---

## Get Flow — refined

```
Client → Server: resolve("x7Kp2")

Step 1: Check Redis first
        value = Redis.get("x7Kp2")
        
Step 2a: Cache hit
        if value != null → return value  (fast path, ~1ms)

Step 2b: Cache miss
        url = DB.get("x7Kp2")
        if url == null → return 404
        if !url.isActive → return 410 Gone
        
        Redis.set("x7Kp2", url.longUrl, TTL=24hrs)  // populate cache
        return url.longUrl
```

**Why TTL on Redis?**

```
URL deactivated in DB → Redis still has it for TTL duration
→ stale cache serves deactivated URL temporarily
→ TTL ensures eventual consistency
→ OR on deactivate() → Redis.delete(shortCode) for immediate consistency
```

---

## Deactivate Flow

```
Client → Server: deactivate("x7Kp2")

Step 1: DB soft delete
        DB.update(shortCode, isActive=false)

Step 2: Invalidate cache immediately
        Redis.delete("x7Kp2")
        → next get() → cache miss → loads from DB → sees isActive=false → 410
```

---

## Full System Flow Diagram

```
                    ┌─────────────────┐
                    │   Load Balancer  │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Server 1 │  │ Server 2 │  │ Server 3 │
        │Snowflake │  │Snowflake │  │Snowflake │
        │serverID=1│  │serverID=2│  │serverID=3│
        └────┬─────┘  └────┬─────┘  └────┬─────┘
             │              │              │
             └──────────────┼──────────────┘
                            │
                ┌───────────┴───────────┐
                ▼                       ▼
         ┌────────────┐          ┌────────────┐
         │   Redis    │          │     DB     │
         │  (cache)   │          │  (source   │
         │  TTL=24hr  │          │  of truth) │
         └────────────┘          └────────────┘
```

---

## Locking — where and why

### SnowflakeID generation — synchronized

```java
// per server — only one thread generates ID at a time
// prevents sequence collision within same millisecond
public synchronized long nextId() {
    // timestamp + serverID + sequence
}
```

### Redis operations — no explicit lock needed

```
Redis is single-threaded internally
All operations are atomic
No lock needed from application side
```

### DB writes — DB handles concurrency

```
DB has its own transaction isolation
shortCode is primary key → duplicate insert throws exception
Application catches and retries with new ID
```

---

## Now code it — CoderPad version

For CoderPad we simulate Redis with ConcurrentHashMap and DB with another ConcurrentHashMap:

```java
class URLShortenerService {
    // simulate Redis
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    // simulate DB
    private final Map<String, URLEntry> db = new ConcurrentHashMap<>();
    
    private final SnowflakeIDGenerator idGenerator;
    private static final String BASE_URL = "https://short.gs/";
    private static final String CHARS = 
        "abcdefghijklmnopqrstuvwxyz" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public URLShortenerService(long serverId) {
        this.idGenerator = new SnowflakeIDGenerator(serverId);
    }

    private String encode(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(CHARS.charAt((int)(num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }

    public String create(String longUrl) {
        // step 1: generate snowflake id
        // step 2: base62 encode
        // step 3: write to db first, then cache
        // step 4: return short URL
    }

    public String resolve(String shortCode) {
        // step 1: check cache
        // step 2: cache miss → check db
        // step 3: populate cache on miss
        // step 4: check isActive
    }

    public void deactivate(String shortCode) {
        // soft delete in db
        // invalidate cache
    }

    public URLEntry getDetails(String shortCode) {
        // return full entry from db
    }
}

class URLEntry {
    String shortCode;
    String longUrl;
    boolean isActive;
    long createdAt;

    public URLEntry(String shortCode, String longUrl) {
        this.shortCode = shortCode;
        this.longUrl   = longUrl;
        this.isActive  = true;
        this.createdAt = System.currentTimeMillis();
    }
}

class SnowflakeIDGenerator {
    private final long serverId;
    private long lastTimestamp = -1;
    private long sequence = 0;
    private static final long EPOCH = 1700000000000L;

    public SnowflakeIDGenerator(long serverId) {
        this.serverId = serverId;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis() - EPOCH;

        if (now == lastTimestamp) {
            sequence = (sequence + 1) & 0xFFF;
            if (sequence == 0) {
                while (System.currentTimeMillis() - EPOCH == now);
                now = System.currentTimeMillis() - EPOCH;
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = now;
        return (now << 22) | (serverId << 12) | sequence;
    }
}
```

Fill in the four methods — give it a go.

Great — let me explain every bit of this from scratch.

---

## What is a Snowflake ID?

It's a 64-bit number — like a `long` in Java. Designed to be:
- Globally unique across servers
- Time-ordered — newer IDs are always larger
- Generated without coordination between servers

64 bits split into 4 parts:

```
| 1 bit  | 41 bits      | 10 bits   | 12 bits  |
| sign   | timestamp    | server ID | sequence |
```

---

## Part 1 — Sign bit (1 bit)

Always 0. Just makes the number positive. Ignored effectively.

---

## Part 2 — Timestamp (41 bits)

Not the full Unix timestamp — that's too large. Instead:

```
timestamp = System.currentTimeMillis() - EPOCH
```

**What is EPOCH?**

Unix time starts from Jan 1, 1970. By 2024 that's ~1.7 trillion milliseconds — needs more than 41 bits.

So you pick a **custom start date** — your system's birthday:

```
EPOCH = Nov 14, 2023 in milliseconds = 1700000000000L

timestamp = now - EPOCH
          = 1700500000000 - 1700000000000
          = 500000000  ← much smaller number, fits in 41 bits
```

41 bits can hold up to `2^41 = 2.2 trillion` — so your system works for 2.2 trillion milliseconds = **69 years** from your EPOCH.

---

## Part 3 — Server ID (10 bits)

```
10 bits = 2^10 = 1024 possible server IDs
```

Each server/pod gets a unique ID — injected via config or environment variable:

```java
// Kubernetes pod
env:
  SERVER_ID: "42"

// Java reads it
long serverId = Long.parseLong(System.getenv("SERVER_ID"));
```

This is what prevents two servers from generating the same ID — even if they generate at the exact same millisecond, their server IDs differ.

---

## Part 4 — Sequence (12 bits)

```
12 bits = 2^12 = 4096 values per millisecond per server
```

Within the same millisecond on the same server — sequence increments:

```
t=1000ms, server=1: seq=0 → ID: 1000|1|0
t=1000ms, server=1: seq=1 → ID: 1000|1|1
t=1000ms, server=1: seq=2 → ID: 1000|1|2
...
t=1000ms, server=1: seq=4095 → ID: 1000|1|4095
t=1000ms, server=1: seq=4096 → OVERFLOW → wait for next ms
t=1001ms, server=1: seq=0 → ID: 1001|1|0  ← reset
```

4096 IDs per millisecond per server = **4 million IDs per second per server.** More than enough.

---

## How bits combine into one number

```java
return (timestamp << 22) | (serverId << 12) | sequence;
```

Bit shifting explained:

```
timestamp = 500000000  → shift left 22 bits → occupies bits 63-22
serverId  = 42         → shift left 12 bits → occupies bits 21-12
sequence  = 5          → no shift           → occupies bits 11-0

Combined:
[0][timestamp 41 bits][serverId 10 bits][sequence 12 bits]
= one 64-bit long number
```

Think of it like building a number from parts:

```
timestamp = 500  → "500"
serverId  = 042  → "042"  (padded to 3 digits)
sequence  = 005  → "005"  (padded to 3 digits)
combined  = "500042005"
```

Bit shifting does the same but in binary.

---

## How Snowflake ID becomes 6-char short code

Snowflake generates a 64-bit number — large but fixed size. Base62 encodes it:

```
Snowflake ID = 1701234567890123456  (example)

Base62 encoding:
1701234567890123456 % 62 = 14 → chars[14] = 'o'
1701234567890123456 / 62 = 27439266417582636
27439266417582636   % 62 = 28 → chars[28] = 'C'
...repeat until 0

Result: "x7Kp2mQ"  ← 7 chars
```

64-bit number in Base62 needs at most **11 chars** (2^64 in base62). But since timestamp part dominates and is always similar magnitude — typically **9-11 chars** in practice.

If you want strictly 6 chars — use only lower bits or truncate. But 9-11 is fine for a short URL.

---

## Server clock consistency — NTP

**The problem:**

```
Server1 clock: 10:00:00.500
Server2 clock: 10:00:00.300  ← 200ms behind

Server2 generates ID with timestamp 300
Server1 generates ID with timestamp 500
Server2's ID looks OLDER even if generated after Server1's
→ time ordering broken
```

**The solution — NTP (Network Time Protocol):**

All servers sync their clocks to the same NTP server every few minutes. Keeps clocks within 1-2ms of each other. Industry standard — you just configure it at infrastructure level, not in code.

**What if a server's clock drifts backward?**

```java
public synchronized long nextId() {
    long now = System.currentTimeMillis() - EPOCH;
    
    // clock went backward — refuse to generate
    if (now < lastTimestamp) {
        throw new RuntimeException(
            "Clock moved backwards! Refusing to generate ID. " +
            "Drift: " + (lastTimestamp - now) + "ms");
    }
    // rest of generation...
}
```

Throw exception and let the caller retry or failover to another server. Never generate with a backward clock — would create duplicate IDs.

---

## What happens on server restart

**The problem:**

```
Before restart:
t=1000ms, server=1, seq=500 → generated 500 IDs

Server restarts — sequence resets to 0

t=1000ms, server=1, seq=0   → COLLISION with earlier seq=0!
```

Wait — is this actually a problem?

**No — because of the timestamp.**

After restart, time has moved forward:

```
Before restart: t=1000ms
After restart:  t=1500ms  ← at least a few ms later

t=1500ms, server=1, seq=0 → completely different from t=1000ms, server=1, seq=0
```

As long as restart takes at least 1 millisecond (it always does), no collision.

**Edge case — what if restart is instantaneous?**

Theoretically impossible — server shutdown + startup always takes seconds. But to be safe:

```java
// on startup, wait 1ms to ensure clock has advanced
Thread.sleep(1);
```

Or persist last timestamp to disk before shutdown:

```java
// on shutdown
Files.write(Paths.get("last_timestamp.txt"), 
    String.valueOf(lastTimestamp).getBytes());

// on startup
lastTimestamp = Long.parseLong(
    new String(Files.readAllBytes(Paths.get("last_timestamp.txt"))));
// now sequence starts fresh but timestamp is already past last used value
```

---

## Full picture — what guarantees uniqueness

```
Same server, same ms:    different sequence    → unique ✓
Same server, diff ms:    different timestamp   → unique ✓
Diff server, same ms:    different serverID    → unique ✓
Diff server, diff ms:    different timestamp   → unique ✓
After restart:           timestamp moved fwd   → unique ✓
```

Every combination is unique. No coordination between servers needed.

---

## Summary table

| Part | Bits | Max value | Purpose |
|---|---|---|---|
| Sign | 1 | 0 | Always 0, keeps positive |
| Timestamp | 41 | 2.2 trillion ms = 69 years | Time ordering |
| Server ID | 10 | 1024 servers | Cross-server uniqueness |
| Sequence | 12 | 4096 per ms | Same-server same-ms uniqueness |

---

Clear on all of it? Now code the four methods.

Let me explain all three from scratch.

---

## 1. What is Epoch and how to generate it

Epoch = a reference point in time. All timestamps measured from this point.

Unix epoch = Jan 1, 1970. By 2024 that's huge number:
```
System.currentTimeMillis() = 1700500000000  (milliseconds since 1970)
```

You want a smaller number — so pick your own start date:

```java
// How to generate epoch for a specific date in Java
// "I want my epoch to be Nov 14, 2023"

LocalDateTime myStartDate = LocalDateTime.of(2023, 11, 14, 0, 0, 0);
ZonedDateTime zdt = myStartDate.atZone(ZoneOffset.UTC);
long EPOCH = zdt.toInstant().toEpochMilli();

System.out.println(EPOCH);  // 1699920000000
```

Then:
```java
long now = System.currentTimeMillis();  // 1700500000000
long timestamp = now - EPOCH;           // 1700500000000 - 1699920000000 = 580000000
```

580 million fits in 41 bits. 1700 billion does not.

---

## 2. Bits in Java — from scratch

A `long` in Java = 64 bits = 64 on/off switches.

```
64 bits:
0000000000000000000000000000000000000000000000000000000000000000
← bit 63 (leftmost)                          bit 0 (rightmost) →
```

### What does << (left shift) do?

Moves all bits N positions to the left. Fills right with zeros.

Simple example with small number:

```
number = 5
binary = 00000101

5 << 2 (shift left by 2):
00000101
→ 00010100  = 20

5 * 4 = 20  ← left shift by N = multiply by 2^N
```

### Why we use shifts for Snowflake

We want to pack three numbers into one 64-bit long:

```
timestamp = 580000000   → goes in bits 63-22  (41 bits)
serverId  = 42          → goes in bits 21-12  (10 bits)
sequence  = 5           → goes in bits 11-0   (12 bits)
```

**Step 1 — place timestamp in bits 63-22:**
```
timestamp = 580000000
binary    = 00100010100011010010000000000000000000000

timestamp << 22  →  shift left 22 positions
→ timestamp now occupies bits 63-22
→ bits 21-0 are all zeros (room for serverId and sequence)
```

**Step 2 — place serverId in bits 21-12:**
```
serverId = 42
binary   = 00101010

serverId << 12  →  shift left 12 positions
→ serverId now occupies bits 21-12
→ bits 11-0 are all zeros (room for sequence)
```

**Step 3 — place sequence in bits 11-0:**
```
sequence = 5
binary   = 000000000101
→ no shift needed, already in bits 11-0
```

**Step 4 — combine with OR (`|`):**
```
OR combines bits — if either bit is 1, result is 1:

timestamp << 22:  [timestamp bits][000000000000000000000000]
serverId  << 12:  [000000000000000000000000000000][serverId][000000000000]
sequence:         [0000000000000000000000000000000000000000000][sequence]

OR all together:  [timestamp][serverId][sequence]
= one 64-bit number
```

In Java:
```java
long id = (timestamp << 22) | (serverId << 12) | sequence;
```

---

## 3. How sequence generates 4096 per ms and resets

12 bits = positions 11 down to 0.

Maximum value of 12 bits:
```
111111111111 in binary = 4095 in decimal = 2^12 - 1
```

So sequence goes 0, 1, 2, 3... up to 4095 = 4096 values.

### The sequence mask trick

```java
sequence = (sequence + 1) & 0xFFF;
```

**What is `0xFFF`?**

```
0xFFF = hex for 4095
      = 111111111111 in binary  (twelve 1s)
```

**What does `& 0xFFF` do?**

AND operation — both bits must be 1 for result to be 1:

```
4094 & 0xFFF:
4094 = 111111111110
0xFFF= 111111111111
AND  = 111111111110 = 4094  ← unchanged, within 12 bits

4095 & 0xFFF:
4095 = 111111111111
0xFFF= 111111111111
AND  = 111111111111 = 4095  ← unchanged

4096 & 0xFFF:
4096 = 1000000000000  ← 13 bits!
0xFFF= 0111111111111  ← only 12 bits
AND  = 0000000000000 = 0    ← RESETS TO ZERO! ✓
```

So `& 0xFFF` automatically wraps sequence back to 0 after 4095 — no if statement needed.

### How reset per millisecond works

```java
public synchronized long nextId() {
    long now = System.currentTimeMillis() - EPOCH;

    if (now == lastTimestamp) {
        // same millisecond — increment sequence
        sequence = (sequence + 1) & 0xFFF;

        if (sequence == 0) {
            // sequence exhausted this ms (hit 4096, wrapped to 0)
            // wait for next millisecond
            while (System.currentTimeMillis() - EPOCH == now);
            now = System.currentTimeMillis() - EPOCH;
        }
    } else {
        // new millisecond — reset sequence to 0
        sequence = 0;
    }

    lastTimestamp = now;
    return (now << 22) | (serverId << 12) | sequence;
}
```

Trace:
```
t=1000ms:
  request1: now==lastTimestamp? No(first call) → seq=0, lastTimestamp=1000
            id = (1000<<22)|(1<<12)|0
  
  request2: now==lastTimestamp? Yes → seq=(0+1)&0xFFF=1
            id = (1000<<22)|(1<<12)|1
  
  request3: now==lastTimestamp? Yes → seq=(1+1)&0xFFF=2
            id = (1000<<22)|(1<<12)|2
  ...
  request4096: seq=(4095+1)&0xFFF=0 → sequence==0 → WAIT for next ms

t=1001ms:
  request4097: now!=lastTimestamp → seq=0 (reset)
               id = (1001<<22)|(1<<12)|0
```

---

## Full SnowflakeIDGenerator with all comments

```java
class SnowflakeIDGenerator {
    // custom epoch — Nov 14, 2023 00:00:00 UTC
    private static final long EPOCH;
    static {
        LocalDateTime startDate = LocalDateTime.of(2023, 11, 14, 0, 0, 0);
        EPOCH = startDate.atZone(ZoneOffset.UTC)
                         .toInstant()
                         .toEpochMilli();  // 1699920000000
    }

    // bit lengths
    private static final long SERVER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS  = 12L;

    // max values via masking
    private static final long MAX_SERVER_ID = ~(-1L << SERVER_ID_BITS); // 1023
    private static final long MAX_SEQUENCE  = ~(-1L << SEQUENCE_BITS);  // 4095

    // bit shift positions
    private static final long SERVER_ID_SHIFT  = SEQUENCE_BITS;           // 12
    private static final long TIMESTAMP_SHIFT  = SERVER_ID_BITS + SEQUENCE_BITS; // 22

    private final long serverId;
    private long lastTimestamp = -1L;
    private long sequence      = 0L;

    public SnowflakeIDGenerator(long serverId) {
        if (serverId < 0 || serverId > MAX_SERVER_ID)
            throw new IllegalArgumentException(
                "Server ID must be between 0 and " + MAX_SERVER_ID);
        this.serverId = serverId;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis() - EPOCH;

        // clock went backward — refuse
        if (now < lastTimestamp)
            throw new RuntimeException(
                "Clock moved backward by " + (lastTimestamp - now) + "ms");

        if (now == lastTimestamp) {
            // same ms — increment sequence with auto-wrap at 4096
            sequence = (sequence + 1) & MAX_SEQUENCE;

            if (sequence == 0) {
                // sequence exhausted — wait for next ms
                while ((now = System.currentTimeMillis() - EPOCH) 
                        == lastTimestamp);
            }
        } else {
            // new ms — reset sequence
            sequence = 0L;
        }

        lastTimestamp = now;

        return (now       << TIMESTAMP_SHIFT)   // bits 63-22
             | (serverId  << SERVER_ID_SHIFT)   // bits 21-12
             | sequence;                         // bits 11-0
    }
}
```

---

## One more thing — `~(-1L << N)` explained

This is how you compute max value for N bits without hardcoding:

```
-1L in binary = 1111111111111111111111111111111111111111111111111111111111111111
                (64 ones — two's complement)

-1L << 12     = 1111111111111111111111111111111111111111111111110000000000000000
                (last 12 bits become 0)

~(-1L << 12)  = 0000000000000000000000000000000000000000000000001111111111111111
                (flip all bits → last 12 bits are 1)
              = 4095 = MAX_SEQUENCE ✓
```

Much cleaner than hardcoding `4095` — works for any bit length.

---

## Summary

| Concept | How it works |
|---|---|
| Epoch | Custom start date — subtract from currentTimeMillis to get small timestamp |
| Left shift `<<` | Moves bits left, multiplies by 2^N, makes room for other parts |
| OR `\|` | Combines three parts into one 64-bit number |
| `& 0xFFF` | Masks to 12 bits — auto-wraps sequence to 0 after 4095 |
| Sequence reset | `now != lastTimestamp` → new ms → sequence = 0 |
| Per-ms limit | 4096 IDs per ms per server — waits for next ms if exhausted |

---

Clear? Now code the four URL Shortener methods.


Good structure! But several bugs. Let me go through them:

---

## Bug 1 — create() is incomplete

```java
public String create(String longUrl) {
    return idGenerator.generate();  // ← returns what? generate() doesn't exist yet
}
```

Should be:
```java
public String create(String longUrl) {
    long id = idGenerator.nextId();        // generate snowflake id
    String shortCode = encode(id);         // base62 encode
    URLEntry entry = new URLEntry(shortCode, longUrl);
    db.put(shortCode, entry);              // DB first
    cache.put(shortCode, longUrl);         // then cache
    return BASE_URL + shortCode;           // return full short URL
}
```

---

## Bug 2 — resolve() doesn't check isActive

```java
} else if(db.containsKey(shortCode)) {
    return db.get(shortCode).longUrl;  // ← returns even if deactivated!
}
```

Fix:
```java
} else if (db.containsKey(shortCode)) {
    URLEntry entry = db.get(shortCode);
    if (!entry.isActive) throw new Exception("URL deactivated");
    cache.put(shortCode, entry.longUrl);  // populate cache on miss
    return entry.longUrl;
}
```

---

## Bug 3 — resolve() cache returns deactivated URL

```java
if (cache.containsKey(shortCode)) {
    return cache.get(shortCode);  // ← cache may have stale deactivated URL
}
```

deactivate() removes from cache — so this is safe. But use `cache.get()` directly instead of containsKey + get — two operations vs one:

```java
String cached = cache.get(shortCode);
if (cached != null) return cached;
```

---

## Bug 4 — deactivate() race condition

```java
URLEntry entry = db.get(shortCode);
entry.isActive = false;          // ← modifying object directly
db.put(shortCode, entry);        // ← not atomic
```

Two threads could both get the entry and modify simultaneously. Use `compute()`:

```java
db.compute(shortCode, (k, entry) -> {
    if (entry == null) return null;
    entry.isActive = false;
    return entry;
});
cache.remove(shortCode);
```

---

## Clean complete version

```java
public class URLShortener {
    private final Map<String, String> cache    = new ConcurrentHashMap<>();
    private final Map<String, URLEntry> db     = new ConcurrentHashMap<>();
    private final SnowflakeIDGenerator idGenerator;
    private static final String BASE_URL = "https://short.gs/";
    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public URLShortener(long serverId) {
        this.idGenerator = new SnowflakeIDGenerator(serverId);
    }

    private String encode(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(CHARS.charAt((int)(num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }

    public String create(String longUrl) {
        long id          = idGenerator.nextId();
        String shortCode = encode(id);
        URLEntry entry   = new URLEntry(shortCode, longUrl);
        db.put(shortCode, entry);              // DB first
        cache.put(shortCode, longUrl);         // then cache
        return BASE_URL + shortCode;
    }

    public String resolve(String shortCode) throws Exception {
        // cache first
        String cached = cache.get(shortCode);
        if (cached != null) return cached;

        // cache miss → check db
        URLEntry entry = db.get(shortCode);
        if (entry == null)       throw new Exception("URL not found");
        if (!entry.isActive)     throw new Exception("URL deactivated");

        cache.put(shortCode, entry.longUrl);   // populate cache
        return entry.longUrl;
    }

    public void deactivate(String shortCode) {
        // soft delete in db atomically
        db.compute(shortCode, (k, entry) -> {
            if (entry != null) entry.isActive = false;
            return entry;
        });
        cache.remove(shortCode);               // invalidate cache immediately
    }

    public URLEntry getDetails(String shortCode) throws Exception {
        URLEntry entry = db.get(shortCode);
        if (entry == null) throw new Exception("URL not found");
        return entry;
    }
}

class URLEntry {
    String shortCode;
    String longUrl;
    boolean isActive;
    long createdAt;

    public URLEntry(String shortCode, String longUrl) {
        this.shortCode = shortCode;
        this.longUrl   = longUrl;
        this.isActive  = true;
        this.createdAt = System.currentTimeMillis();
    }
}
```

---

## Now code SnowflakeIDGenerator

You understand it fully — just code it. Fields needed:

```java
class SnowflakeIDGenerator {
    private static final long EPOCH = ?       // Nov 14 2023
    private static final long MAX_SEQUENCE = ?  // ~(-1L << 12)
    private static final long SERVER_ID_SHIFT = 12L
    private static final long TIMESTAMP_SHIFT = 22L

    private final long serverId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public synchronized long nextId() { }
}
```

Give it a go.