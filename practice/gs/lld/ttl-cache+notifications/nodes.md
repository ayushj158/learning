## Problem 22 — TTL Cache

> Design a cache where each key has a **Time To Live (TTL)**. A key is only valid within its TTL window. After TTL expires the key is gone.

```
put("price:AAPL", 189.50, ttl=5000ms)  // valid for 5 seconds
get("price:AAPL") → 189.50             // within TTL ✓

// 6 seconds later
get("price:AAPL") → null               // expired ✗

put("price:AAPL", 192.00, ttl=5000ms)  // fresh entry
get("price:AAPL") → 192.00             // new value ✓
```

**API to implement:**
```java
void put(String key, Object value, long ttlMs)
Object get(String key)
void evict()  // proactive cleanup
```

---

## Before coding — three questions:

1. What do you store per key — just value, or more?
2. How does `get()` know if a key is expired?
3. `evict()` needs to find all expired keys efficiently — what data structure helps here beyond the main map?

What's your thinking?

Great question — locking entire `get()` is too coarse. Let me think through this properly.

---

## Problem with locking entire get()

```java
// Thread1: get("AAPL")  → holds lock for entire duration
// Thread2: get("GOOG")  → waiting unnecessarily
// Thread3: get("MSFT")  → waiting unnecessarily
```

AAPL, GOOG, MSFT are completely independent — no reason they should block each other.

---

## What actually needs protection?

Let's identify the real race conditions:

### Race 1 — get() + evict() conflict
```
Thread1 get("AAPL"):  entry = cache.get("AAPL") → entry not null
Thread2 evict():      cache.remove("AAPL")        ← removes it
Thread1 get("AAPL"):  returns stale value          ← should have been null
```

### Race 2 — put() + get() conflict
```
Thread1 put("AAPL", 192.00, 5000) → halfway through updating
Thread2 get("AAPL")               → reads partial state
```

### Race 3 — put() + put() conflict
```
Thread1 put("AAPL", 192.00, 5000)
Thread2 put("AAPL", 193.00, 3000)  → both writing simultaneously
→ map and heap out of sync
```

---

## Optimization 1 — ConcurrentHashMap for map

Replaces coarse lock on map reads/writes:

```java
private final ConcurrentHashMap<String, CacheEntry> cache 
                                    = new ConcurrentHashMap<>();
```

Now `get()` and `put()` on different keys never block each other. ConcurrentHashMap uses **segment-level locking** internally — only locks the bucket the key hashes to.

---

## Optimization 2 — PriorityQueue still needs locking

`ConcurrentHashMap` fixes the map. But `PriorityQueue` is **not thread safe** — `evict()` and `put()` both touch it.

Options:

### Option A — PriorityBlockingQueue
```java
private final PriorityBlockingQueue<CacheEntry> queue 
    = new PriorityBlockingQueue<>(11,
        (a,b) -> Long.compare(a.getExpiryTime(), b.getExpiryTime()));
```

Thread-safe heap — `offer()` and `poll()` are both atomic. No explicit lock needed.

### Option B — Separate lock just for heap
```java
private final ReentrantLock heapLock = new ReentrantLock();

public void put(...) {
    cache.put(key, entry);  // ConcurrentHashMap handles this
    heapLock.lock();
    try {
        queue.offer(entry);
    } finally {
        heapLock.unlock();
    }
}
```

---

## Optimization 3 — Atomic check-then-act in get()

Even with ConcurrentHashMap, this is a race:

```java
// NOT atomic — two separate operations
if (System.currentTimeMillis() > entry.getExpiryTime()) {
    cache.remove(key);  // another thread might remove between check and remove
    return null;
}
```

Fix using `compute` — atomic check and remove in one operation:

```java
public Object get(String key) {
    // atomic — no other thread can interleave between check and remove
    CacheEntry[] result = new CacheEntry[1];
    
    cache.compute(key, (k, entry) -> {
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.getExpiryTime()) {
            result[0] = null;
            return null;  // removes from map atomically
        }
        result[0] = entry;
        return entry;  // keeps in map
    });
    
    return result[0] == null ? null : result[0].getValue();
}
```

`compute()` is atomic in ConcurrentHashMap — the lambda runs under segment lock.

---

## Full thread-safe optimized version

```java
class TTLCache {
    private final ConcurrentHashMap<String, CacheEntry> cache 
                                        = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<CacheEntry> queue 
        = new PriorityBlockingQueue<>(11,
            (a,b) -> Long.compare(a.getExpiryTime(), b.getExpiryTime()));

    public void put(String key, Object value, long ttlMs) {
        long expiryTime = System.currentTimeMillis() + ttlMs;
        CacheEntry entry = new CacheEntry(key, value, expiryTime);
        cache.put(key, entry);   // atomic per key
        queue.offer(entry);      // thread-safe heap
    }

    public Object get(String key) {
        CacheEntry[] result = new CacheEntry[1];
        
        cache.compute(key, (k, entry) -> {
            if (entry == null) return null;
            if (System.currentTimeMillis() > entry.getExpiryTime()) {
                result[0] = null;
                return null;        // atomic removal
            }
            result[0] = entry;
            return entry;
        });
        
        return result[0] == null ? null : result[0].getValue();
    }

    public void evict() {
        while (!queue.isEmpty()) {
            CacheEntry entry = queue.peek();
            if (System.currentTimeMillis() < entry.getExpiryTime()) {
                break;
            }
            queue.poll();
            // guard against stale heap entries
            cache.compute(entry.getKey(), (k, current) -> {
                if (current != null && 
                    current.getExpiryTime() == entry.getExpiryTime()) {
                    return null;    // atomic removal
                }
                return current;     // keep if updated
            });
        }
    }
}
```

---

## Summary — what locks what

| Operation | Protection |
|---|---|
| `cache` reads/writes | `ConcurrentHashMap` segment locking |
| `queue` reads/writes | `PriorityBlockingQueue` internal lock |
| check-then-remove in `get()` | `ConcurrentHashMap.compute()` atomic |
| check-then-remove in `evict()` | `ConcurrentHashMap.compute()` atomic |
| Different keys | Never block each other ✓ |

---

## What to say in interview

*"Coarse ReentrantLock on get() is too broad — independent keys block each other needlessly. ConcurrentHashMap gives per-segment locking so AAPL and GOOG never block each other. PriorityBlockingQueue makes heap operations thread-safe. compute() in get() and evict() makes check-then-remove atomic — no window for another thread to interleave."*

---

Clear? Ready to move on?

Good — let me clarify all three.

---

## Three CHM operations you need to know

---

### 1. `remove(key)` — unconditional remove

```java
cache.remove("AAPL");
```

Just deletes the key. No condition. Not atomic with any check.

**Problem:**
```
Thread1: checks entry expired → yes
Thread2: put("AAPL", 192.00, 5000) → fresh entry added
Thread1: remove("AAPL") → removes the fresh entry! ✗
```

---

### 2. `remove(key, value)` — conditional atomic remove

```java
cache.remove("AAPL", entry);
```

Only removes if **current value in map == entry you pass in.** Atomic — check and remove happen together under segment lock.

```
Thread1: remove("AAPL", oldEntry)
Thread2: put("AAPL", newEntry) → map now has newEntry

Thread1 remove executes:
  current value = newEntry
  oldEntry != newEntry → does NOT remove ✓
```

This is what makes evict() safe — you only remove the entry you intended to remove, not a freshly updated one.

---

### 3. `compute(key, BiFunction)` — atomic read-modify-write

```java
cache.compute("AAPL", (key, currentEntry) -> {
    // currentEntry = what's currently in map (null if absent)
    // whatever you return becomes the new value
    // return null = remove the key
    // runs atomically under segment lock
    
    if (currentEntry == null) return null;
    if (expired(currentEntry)) return null;  // removes key
    return currentEntry;                      // keeps key
});
```

The entire lambda runs atomically — no other thread can read or write this key while it's executing.

**vs doing it manually:**
```java
// NOT atomic — race condition between get and remove
CacheEntry entry = cache.get("AAPL");     // Thread2 can put here
if (expired(entry)) cache.remove("AAPL"); // removes wrong entry
```

---

## When to use which

| Operation | Use when |
|---|---|
| `remove(key)` | You just want to delete, no conditions |
| `remove(key, value)` | You want to delete only if value hasn't changed |
| `compute(key, fn)` | You need to read + conditionally modify/delete atomically |

---

## Applied to TTLCache

```java
// get() — read + conditionally remove if expired
cache.compute("AAPL", (k, entry) -> {
    if (entry == null) return null;
    if (System.currentTimeMillis() > entry.getExpiryTime()) 
        return null;   // removes atomically
    return entry;      // keeps atomically
});

// evict() — remove only if entry hasn't been updated
cache.remove("AAPL", oldEntry);  // safe conditional remove
```

---

Clear? Ready to move on?


Great questions — let me explain both properly.

---

## compute() internals — what gets locked

compute() locks at **segment level** not key level.

```
Segment 3: [AAPL, MSFT, NVDA]

Thread1: compute("AAPL", ...) → locks Segment 3
Thread2: compute("MSFT", ...) → wants Segment 3 → WAITS
Thread3: compute("GOOG", ...) → different segment → runs parallel ✓
```

So AAPL and MSFT block each other if they hash to same segment. AAPL and GOOG don't.

**Why not key-level locking?**

Key-level locking would need a lock object per key — that's a `Map<String, Lock>` which itself needs synchronization. Segment locking is the practical middle ground — 16 segments means 16x parallelism vs one global lock.

---

## What compute() does internally

```java
// simplified CHM source code
public V compute(K key, BiFunction remappingFunction) {
    int segment = hash(key) % 16;
    
    synchronized(segments[segment]) {  // lock this segment
        V oldValue = get(key);         // read current value
        V newValue = remappingFunction.apply(key, oldValue); // your lambda
        
        if (newValue == null) {
            remove(key);               // return null = delete
        } else {
            put(key, newValue);        // return value = update
        }
        return newValue;
    }
}
```

The read + lambda + write all happen inside the synchronized block — that's what makes it atomic.

---

## remove(key, value) — reference or equals?

**It uses `.equals()` not reference (`==`) check.**

```java
// CHM source simplified
public boolean remove(K key, V value) {
    int segment = hash(key) % 16;
    
    synchronized(segments[segment]) {
        V current = get(key);
        
        if (current != null && current.equals(value)) {  // equals() not ==
            remove(key);
            return true;
        }
        return false;
    }
}
```

**So for TTLCache — you MUST override equals() in CacheEntry:**

```java
class CacheEntry {
    String key;
    Object value;
    long expiryTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheEntry)) return false;
        CacheEntry other = (CacheEntry) o;
        return expiryTime == other.expiryTime 
            && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, expiryTime);
    }
}
```

**Why expiryTime in equals?**

```
oldEntry: key="AAPL", expiryTime=1000
newEntry: key="AAPL", expiryTime=2000  ← updated via put()

cache.remove("AAPL", oldEntry)
→ current in map = newEntry
→ newEntry.equals(oldEntry)?
→ expiryTime 2000 != 1000 → false → does NOT remove ✓
```

Without overriding equals — default is reference equality (`==`):

```
oldEntry == newEntry? → false (different objects)
→ never removes anything ✗
```

---

## Full picture

| Operation | Lock scope | Equality check |
|---|---|---|
| `get(key)` | segment lock briefly for read | n/a |
| `put(key, value)` | segment lock | n/a |
| `remove(key)` | segment lock | n/a |
| `remove(key, value)` | segment lock | `.equals()` |
| `compute(key, fn)` | segment lock for entire lambda | n/a |

---

## What to say in interview

*"compute() locks at segment level — not key level. Keys in same segment block each other, different segments run parallel. The lambda runs atomically inside the lock — read, modify, write are one unit. remove(key, value) uses equals() not reference equality — so CacheEntry must override equals() comparing key and expiryTime. This ensures we don't accidentally remove a freshly updated entry that shares the same key."*

---

Clear? Ready to move on?


You're absolutely right — same object reference, so `==` would also work in your specific usage. Let me clarify the exact scenario:

---

## Your usage — why it's safe

```java
// evict() flow
CacheEntry entry = queue.poll();           // got entry from heap
cache.remove(entry.getKey(), entry);       // passing SAME object

// put() flow  
CacheEntry newEntry = new CacheEntry(...); // NEW object created
cache.put(key, newEntry);                  // different reference
```

So:
```
entry from heap == newEntry? 
→ different objects → false → remove does nothing ✓
```

Reference equality (`==`) works perfectly here because `put()` always creates a **new** CacheEntry object. You're safe.

`.equals()` override only matters if you're comparing entries created separately with same data — not your case.

---

## Final Complete Code

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class TTLCache {
    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final PriorityBlockingQueue<CacheEntry> queue;
    private final ScheduledExecutorService scheduler;

    public TTLCache() {
        this.cache = new ConcurrentHashMap<>();
        this.queue = new PriorityBlockingQueue<>(11,
            (a, b) -> Long.compare(a.getExpiryTime(), b.getExpiryTime()));
        
        // background eviction every second
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(
            this::evict, 1, 1, TimeUnit.SECONDS);
    }

    public void put(String key, Object value, long ttlMs) {
        long expiryTime = System.currentTimeMillis() + ttlMs;
        CacheEntry entry = new CacheEntry(key, value, expiryTime);
        cache.put(key, entry);   // atomic per segment
        queue.offer(entry);      // thread-safe heap insert
    }

    public Object get(String key) {
        // compute is atomic — read + conditional remove under segment lock
        CacheEntry[] result = new CacheEntry[1];

        cache.compute(key, (k, entry) -> {
            if (entry == null) return null;
            if (System.currentTimeMillis() > entry.getExpiryTime()) {
                result[0] = null;
                return null;        // lazy eviction — removes atomically
            }
            result[0] = entry;
            return entry;           // keep in map
        });

        return result[0] == null ? null : result[0].getValue();
    }

    public void evict() {
        while (!queue.isEmpty()) {
            CacheEntry entry = queue.peek();

            // heap sorted by expiry — first entry not expired means rest aren't either
            if (System.currentTimeMillis() < entry.getExpiryTime()) break;

            queue.poll();

            // remove only if SAME object reference still in map
            // if put() updated this key, new object is in map → remove does nothing
            cache.remove(entry.getKey(), entry);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}

class CacheEntry {
    private final String key;
    private final Object value;
    private final long expiryTime;

    public CacheEntry(String key, Object value, long expiryTime) {
        this.key = key;
        this.value = value;
        this.expiryTime = expiryTime;
    }

    public String getKey()       { return key; }
    public Object getValue()     { return value; }
    public long getExpiryTime()  { return expiryTime; }
}
```

---

## Why each decision was made

| Decision | Why |
|---|---|
| `ConcurrentHashMap` | Parallel get/put on different keys |
| `PriorityBlockingQueue` | Thread-safe heap, no explicit lock needed |
| `compute()` in get() | Atomic lazy eviction — no race between check and remove |
| `remove(key, entry)` in evict() | Same object reference — safe against put() updates |
| Background scheduler | Proactive cleanup off hot path |
| `peek()` before `poll()` in evict() | Avoid unnecessary poll — heap sorted so first entry tells us if anything expired |

---

## Thread safety summary

```
get() vs get()   → different segments → parallel ✓
put() vs put()   → different segments → parallel ✓
get() vs put()   → same key → segment lock → sequential ✓
evict() vs put() → remove(key,entry) safe via reference check ✓
evict() vs get() → compute() in get() handles lazy eviction ✓
```

---

TTL Cache ✅ fully closed. **Next — Notification System or Java/OOP?**

## Problem 23 — Notification System

> Design a notification system that supports multiple channels. Users subscribe to event types on specific channels. When an event fires, all subscribers get notified through their subscribed channels.

```
subscribe("deskA", EventType.TRADE_EXECUTED, Channel.EMAIL)
subscribe("deskA", EventType.TRADE_EXECUTED, Channel.SMS)
subscribe("deskB", EventType.RISK_BREACH, Channel.PUSH)

notify(EventType.TRADE_EXECUTED, "Your trade was executed")
→ deskA gets EMAIL ✓
→ deskA gets SMS ✓
→ deskB gets nothing (subscribed to different event) ✓
```

**API to implement:**
```java
void subscribe(String userId, EventType eventType, Channel channel)
void unsubscribe(String userId, EventType eventType, Channel channel)
void notify(EventType eventType, String message)
```

```java
enum EventType { TRADE_EXECUTED, RISK_BREACH, PRICE_ALERT }
enum Channel   { EMAIL, SMS, PUSH }
```

---

Before coding — two questions:

1. What data structure holds the subscriptions? Think about what `notify()` needs to do — given an `EventType`, find all subscribers and their channels quickly.
2. What design pattern does this scream at you?


Perfect on both — exactly right.

---

## Refining the data structure

`ArrayList<SubscriberObject>` works but think about what `unsubscribe()` needs to do — iterate list to find and remove. O(n).

Better structure:

```java
Map<EventType, Map<String, Set<Channel>>> subscriptions
//              userId    channels they want
```

```
TRADE_EXECUTED → {
    deskA → [EMAIL, SMS]
    deskB → [EMAIL]
}
RISK_BREACH → {
    deskB → [PUSH]
}
```

- `notify(TRADE_EXECUTED)` → get inner map → iterate users → iterate their channels
- `unsubscribe(deskA, TRADE_EXECUTED, EMAIL)` → O(1) set remove
- `subscribe(deskA, TRADE_EXECUTED, EMAIL)` → O(1) set add

---

## Observer Pattern — exactly right

```
Subject   = NotificationService  (holds subscriptions, fires events)
Observer  = Subscriber           (gets notified)
Strategy  = Channel              (HOW to deliver — email/sms/push)
```

Observer tells you **who** to notify. Strategy tells you **how** to deliver.

---

Now code it — three classes:

```java
// 1. Channel interface — Strategy pattern
interface NotificationChannel {
    void send(String userId, String message);
}

class EmailChannel implements NotificationChannel { ... }
class SMSChannel implements NotificationChannel { ... }
class PushChannel implements NotificationChannel { ... }

// 2. NotificationService — Observer pattern
class NotificationService {
    // subscriptions map
    // channel implementations map
    
    void subscribe(...)
    void unsubscribe(...)
    void notify(...)
}
```

Give it a go — focus on `NotificationService` first.

No problem — let me explain Observer pattern first.

---

## Observer Pattern — core idea

```
Publisher (Subject)    → knows who is listening
Subscribers (Observer) → get called when event happens
```

Classic structure:

```java
interface Observer {
    void update(String message);
}

class Publisher {
    List<Observer> observers = new ArrayList<>();
    
    void subscribe(Observer o)   { observers.add(o); }
    void unsubscribe(Observer o) { observers.remove(o); }
    
    void notifyAll(String msg) {
        for (Observer o : observers) {
            o.update(msg);  // tell everyone
        }
    }
}
```

That's literally it. Publisher holds a list of observers. When something happens — loop and call everyone.

---

## Apply to Notification System

In your case:
- Publisher = `NotificationService`
- Observer = each `userId + channel` combination
- Event = `EventType`

The only twist is — you have **multiple event types** and **multiple channels per user.** So instead of one flat list of observers, you have:

```
Map<EventType, Map<userId, Set<Channel>>>
```

And `notify()` is just:

```java
void notify(EventType type, String message) {
    // get all subscribers for this event
    // for each subscriber
    //   for each channel they subscribed on
    //     send via that channel
}
```

---

Now code the full thing — I'll give you the skeleton:

```java
enum EventType { TRADE_EXECUTED, RISK_BREACH, PRICE_ALERT }
enum Channel   { EMAIL, SMS, PUSH }

interface NotificationChannel {
    void send(String userId, String message);
}

class EmailChannel implements NotificationChannel {
    public void send(String userId, String message) {
        System.out.println("EMAIL to " + userId + ": " + message);
    }
}

class SMSChannel implements NotificationChannel {
    public void send(String userId, String message) {
        System.out.println("SMS to " + userId + ": " + message);
    }
}

class PushChannel implements NotificationChannel {
    public void send(String userId, String message) {
        System.out.println("PUSH to " + userId + ": " + message);
    }
}

class NotificationService {
    // subscriptions
    private final Map<EventType, Map<String, Set<Channel>>> subscriptions
                                            = new ConcurrentHashMap<>();
    
    // channel implementations — Channel enum → actual sender
    private final Map<Channel, NotificationChannel> channels = Map.of(
        Channel.EMAIL, new EmailChannel(),
        Channel.SMS,   new SMSChannel(),
        Channel.PUSH,  new PushChannel()
    );

    public void subscribe(String userId, EventType eventType, Channel channel) {
        // hint: nested computeIfAbsent
        // outer: eventType → Map
        // inner: userId → Set
        // then add channel to set
    }

    public void unsubscribe(String userId, EventType eventType, Channel channel) {
        // get inner map for eventType
        // get set for userId
        // remove channel
    }

    public void notify(EventType eventType, String message) {
        // get inner map for eventType
        // for each userId → channels entry
        //   for each channel → send
    }
}
```

Fill in the three methods — they're all short. Give it a go.


## Current Status

Before moving on — quick check. Did you code it yourself or want me to show the full solution first?

---

Either way here's the complete implementation for your notes:

```java
class NotificationService {
    private final Map<EventType, Map<String, Set<Channel>>> subscriptions
                                            = new ConcurrentHashMap<>();

    private final Map<Channel, NotificationChannel> channels = Map.of(
        Channel.EMAIL, new EmailChannel(),
        Channel.SMS,   new SMSChannel(),
        Channel.PUSH,  new PushChannel()
    );

    public void subscribe(String userId, EventType eventType, Channel channel) {
        subscriptions
            .computeIfAbsent(eventType, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
            .add(channel);
    }

    public void unsubscribe(String userId, EventType eventType, Channel channel) {
        Map<String, Set<Channel>> userMap = subscriptions.get(eventType);
        if (userMap == null) return;
        Set<Channel> userChannels = userMap.get(userId);
        if (userChannels == null) return;
        userChannels.remove(channel);
    }

    public void notify(EventType eventType, String message) {
        Map<String, Set<Channel>> userMap = subscriptions.get(eventType);
        if (userMap == null) return;

        userMap.forEach((userId, userChannels) ->
            userChannels.forEach(channel ->
                channels.get(channel).send(userId, message)));
    }
}
```

---

## Key design decisions — say these in interview

| Decision | Why |
|---|---|
| `Map<EventType, Map<userId, Set<Channel>>>` | O(1) lookup by event, O(1) subscribe/unsubscribe |
| `ConcurrentHashMap.newKeySet()` | Thread-safe Set for channels |
| `computeIfAbsent` nested | Atomic creation of inner maps |
| Strategy for channels | Swap EMAIL/SMS/PUSH impl without changing service |
| Observer for subscription | Decouple publisher from subscribers |

---

## Follow-up answers — verbal only

**Async delivery?**
```java
// wrap send in executor — don't block notify() caller
ExecutorService executor = Executors.newFixedThreadPool(10);
executor.submit(() -> channels.get(channel).send(userId, message));
```

**High volume — Kafka?**
*"Push notification events to Kafka topic per EventType. Channel consumers read and deliver independently. Retry on failure via dead letter queue."* — directly maps to your JD.

---

Notification System ✅ done.

**Remaining LLD:**
- Stock Trading Platform
- URL Shortener
- Parking Lot

Which one next?