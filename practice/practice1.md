**Area 1 — Java Backend & Coding Best Practices**
1. How does the JVM handle garbage collection? Which GC strategies have you used in production?
2. Explain the difference between `synchronized`, `volatile`, and `ReentrantLock`. When would you use each?
3. How do you design for thread safety in a high-concurrency Java service?
4. What are SOLID principles? Give a real example of applying each in your work.
5. How do you handle exception management across microservices?
6. Explain your approach to unit testing — what makes a good test vs a bad one?

---

**Area 2 — System Architecture for Scalable, Real-Time Systems**
1. Walk me through how you'd design a real-time event-driven system for high throughput and low latency.
2. How did you apply CQRS and Event Sourcing in your platform? What trade-offs did you make?
3. Explain the Saga pattern — when would you choose orchestration vs choreography?
4. How do you handle distributed transactions and data consistency across microservices?
5. How do you approach the Strangler Fig pattern for legacy migration?

---

**Area 3 — Middleware Integration & Service Communication**
1. When would you choose Kafka over REST? And gRPC over REST?
2. How do you ensure exactly-once delivery in Kafka?
3. Explain the Outbox pattern — why did you use it and how does it work?
4. How do you secure inter-service communication? (mTLS, JWT, OAuth2)
5. How have you implemented an API Gateway and what governance did you enforce?

---

**Area 4 — Google Cloud Deployment & Cloud Architecture**
1. Walk me through how you architected your GCP platform — what were the key design decisions?
2. How did you implement multi-tenancy on GKE? What isolation boundaries did you enforce?
3. Explain your approach to hub-spoke networking and zero-trust security on GCP.
4. How does GCP Dataflow (Apache Beam) compare to Kafka Streams for stream processing?
5. How do you manage IaC at scale with Terraform across 100+ teams?

---

**Area 5 — Scalability, Performance & CI/CD**
1. How do you design for 99.999% availability? What does your multi-region active-active setup look like?
2. What resilience patterns did you implement and how did they perform under failure?
3. How do you define and enforce SLOs? What happens when you breach one?
4. Walk me through your CI/CD pipeline — from code commit to production.
5. How do you achieve zero-downtime deployments on Kubernetes?

---

Let's start with **Area 1 — Java Backend & Coding Best Practices**.

I'll ask one question at a time, just like the real interview. Take your time, answer as you would on camera — conversational but structured.

---

**Question 1:**

*"How does the JVM handle garbage collection, and which GC strategies have you used or considered in production systems?"*

No problem — this is actually a good one to prep since it's commonly asked. Let me give you a concise, memorizable answer you can deliver confidently:

---

**Model Answer (deliver in ~90 seconds):**

*"The JVM automatically manages memory through garbage collection — it identifies objects that are no longer reachable and reclaims that heap memory. It works on a generational model: most objects die young, so the heap is split into Young Generation, Old Generation, and Metaspace. Minor GC runs frequently on the Young Gen, while Major/Full GC is less frequent but more expensive.*

*In production, the GC choice depends on your latency requirements. For the high-throughput, low-latency APIs we built at Lloyds — where we needed sub-second response times — G1GC is the right default choice. It balances throughput and pause times well for large heaps. For ultra-low latency scenarios, ZGC or Shenandoah are better as they keep pauses under a millisecond.*

*In practice, I've ensured our services were tuned with appropriate heap sizes, monitored GC pause times via Dynatrace, and set alerts on Full GC frequency as an early signal of memory pressure."*


Great mindset — understanding this deeply will help you answer any follow-up the AI throws at you. Let me break it all down clearly.

---

# JVM Memory Model & Garbage Collection — Deep Dive

---

## 1. JVM Memory Structure

```
┌─────────────────────────────────────────────┐
│                   HEAP                        │
│  ┌─────────────────┐  ┌────────────────────┐ │
│  │  Young Generation│  │  Old Generation    │ │
│  │  ┌────┐ ┌──────┐│  │  (Tenured Space)   │ │
│  │  │Eden│ │S0 S1 ││  │                    │ │
│  │  └────┘ └──────┘│  │                    │ │
│  └─────────────────┘  └────────────────────┘ │
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│              METASPACE (off-heap)             │
│         Class metadata, static fields         │
└─────────────────────────────────────────────┘
┌──────────┐  ┌──────────┐  ┌────────────────┐
│  Stack   │  │   PC     │  │ Native Method  │
│ (threads)│  │ Register │  │    Stack       │
└──────────┘  └──────────┘  └────────────────┘
```

---

## 2. Heap Generations Explained

### Young Generation
This is where all **new objects are born**. It has three spaces:

- **Eden Space** — every `new Object()` lands here first
- **Survivor 0 (S0) and Survivor 1 (S1)** — objects that survive one GC cycle move here

**How Minor GC works:**
1. Eden fills up → Minor GC triggers
2. Live objects from Eden copy to S0 (or S1)
3. Objects that have survived N cycles (age threshold, default 15) get **promoted to Old Gen**
4. Eden is wiped clean — very fast because it just discards dead objects

**Why it's fast:** Most objects die young (short-lived request objects, temp variables). Minor GC is cheap because it only scans Young Gen.

---

### Old Generation (Tenured Space)
Objects that survive multiple Minor GCs get **promoted here**. These are long-lived objects — caches, session data, connection pools, static collections.

**Major GC / Full GC** runs when Old Gen fills up. This is expensive because:
- It scans the entire heap
- It causes **Stop-The-World (STW) pauses** — all application threads freeze
- Can cause latency spikes in production

**Warning signs of Old Gen pressure:**
- Frequent Full GCs in logs
- Heap usage never drops after GC
- `OutOfMemoryError: Java heap space`

---

### Metaspace (Java 8+, replaced PermGen)
Stores **class metadata** — class definitions, method bytecode, static fields. Lives **off-heap** in native memory.

- Grows dynamically by default (unlike old PermGen which had a fixed cap)
- `OutOfMemoryError: Metaspace` happens when you have classloader leaks — common in apps that dynamically load classes (like Spring with many beans, or hot-deploy scenarios)

**Key config:**
```bash
-XX:MetaspaceSize=256m        # Initial size
-XX:MaxMetaspaceSize=512m     # Cap it to prevent runaway growth
```

---

## 3. Garbage Collectors — Types & When to Use

### Serial GC
- Single-threaded, Stop-The-World for everything
- Only for small, single-core apps or batch jobs
- **Never use in production microservices**
```bash
-XX:+UseSerialGC
```

---

### Parallel GC (Throughput Collector)
- Multiple threads for GC, but still Stop-The-World
- Optimises for **maximum throughput** at the cost of latency
- Good for batch processing where pause times don't matter
```bash
-XX:+UseParallelGC
-XX:ParallelGCThreads=8
```

---

### CMS (Concurrent Mark Sweep) — **Deprecated in Java 14**
- Tried to minimise STW pauses by doing most work concurrently
- Had fragmentation issues — replaced by G1GC
- Don't use this anymore

---

### G1GC (Garbage First) — **Default from Java 9+, recommended for most production**
This is the one you'll reference most in interviews.

**How it works:**
- Divides heap into equal-sized **regions** (not fixed young/old spaces)
- Regions are dynamically assigned as Eden, Survivor, or Old
- Collects regions with most garbage first (hence "Garbage First")
- Runs **concurrently** alongside your app — minimises STW pauses
- Targets a configurable **max pause time goal**

**Best for:** Most microservices with heaps 4GB–32GB, needing balanced latency and throughput.

**Key configs:**
```bash
-XX:+UseG1GC                          # Enable (default in Java 9+)
-XX:MaxGCPauseMillis=200              # Target max pause (default 200ms)
-Xms4g -Xmx8g                        # Min/Max heap size
-XX:G1HeapRegionSize=16m             # Region size (1–32MB, power of 2)
-XX:G1NewSizePercent=20              # Min % of heap for Young Gen
-XX:G1MaxNewSizePercent=40           # Max % of heap for Young Gen
-XX:ConcGCThreads=4                  # Concurrent GC threads
```

---

### ZGC (Z Garbage Collector) — **Java 15+ production ready**
- Designed for **ultra-low latency** — sub-millisecond pauses regardless of heap size
- Works concurrently for almost everything
- Scales to multi-terabyte heaps
- Slight throughput trade-off vs G1GC

**Best for:** APIs with strict sub-100ms latency SLOs, real-time systems like yours at Lloyds.

```bash
-XX:+UseZGC
-Xms8g -Xmx16g
-XX:SoftMaxHeapSize=12g              # Soft limit — ZGC tries to stay under this
```

---

### Shenandoah GC
- Similar goals to ZGC — ultra-low pause times
- Available in OpenJDK, RedHat builds
- Concurrent compaction (ZGC also does this now)
- Less common in enterprise — ZGC is generally preferred today

```bash
-XX:+UseShenandoahGC
```

---

## 4. Tuning Strategy — How to Approach It

### Step 1 — Right-size your heap
```bash
# Rule of thumb: set Xms = Xmx to avoid heap resize pauses
-Xms4g -Xmx4g

# For containers/Kubernetes — use percentage-based sizing
-XX:InitialRAMPercentage=50.0
-XX:MaxRAMPercentage=75.0
```

### Step 2 — Tune for your workload type

| Workload | Recommended GC | Key Tuning |
|---|---|---|
| High throughput batch | ParallelGC | Increase heap, more GC threads |
| Balanced microservice | G1GC | Set MaxGCPauseMillis, tune region size |
| Ultra-low latency API | ZGC | Set SoftMaxHeapSize, monitor allocation rate |
| Small service <1GB heap | SerialGC or G1GC defaults | Minimal tuning needed |

### Step 3 — Tune Young Gen sizing (G1GC)
If you see too many Minor GCs → increase Young Gen size:
```bash
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=50
```

### Step 4 — GC log analysis
Always enable GC logging in production:
```bash
-Xlog:gc*:file=/var/log/app/gc.log:time,uptime,level,tags:filecount=5,filesize=20m
```

---

## 5. Observability & Monitoring

### Key GC metrics to watch

| Metric | What it tells you | Alert threshold |
|---|---|---|
| GC pause duration | Latency impact | >200ms for G1, >10ms for ZGC |
| GC frequency | Memory pressure | Minor GC >1/sec, Full GC >1/min |
| Heap usage after GC | Memory leak signal | >80% after Full GC |
| Allocation rate | Object creation rate | Sudden spikes = leak risk |
| Promotion rate | Young→Old Gen rate | High rate = objects living too long |

### Tools

**Dynatrace** (what you used at Lloyds):
- Heap memory dashboard — live view of Young/Old/Metaspace
- GC activity timeline — pause durations, frequency
- Allocation hotspots — which code is allocating most
- Memory leak detection — heap growth trend over time

**Prometheus + Grafana:**
```yaml
# JVM metrics via Micrometer in Spring Boot
management:
  metrics:
    export:
      prometheus:
        enabled: true
```
Key metrics: `jvm_gc_pause_seconds`, `jvm_memory_used_bytes`, `jvm_gc_memory_promoted_bytes`

**GC Easy (online tool):**
Upload your GC log file → get a visual report of pauses, throughput, memory trends

**JVM flags for heap dump on OOM:**
```bash
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/app/heapdump.hprof
```
Then analyse with **Eclipse MAT** or **VisualVM** to find memory leaks.

---

## 6. How to Answer This in the Interview

**30-second version:**
*"JVM uses generational GC — new objects go to Young Gen (Eden + Survivors), long-lived objects get promoted to Old Gen, class metadata lives in Metaspace off-heap. For our sub-second latency APIs at Lloyds, G1GC was our baseline — tuned with a 200ms pause target and heap sized at 4–8GB per service. For tighter latency budgets, ZGC is the modern choice with sub-millisecond pauses. We monitored GC health via Dynatrace — tracking pause durations, heap utilisation after GC, and allocation rates as early signals of memory pressure."*

Great questions — let me address both clearly.

---

# G1GC — Deep Dive

## How G1GC Regions Work

Traditional GCs had **fixed contiguous spaces** for Young/Old Gen. G1GC completely changed this:

```
Traditional GC:
┌──────────────────┬────────────────────────────┐
│  Young Gen       │      Old Gen               │
│  [Eden][S0][S1]  │   [one big contiguous space]│
└──────────────────┴────────────────────────────┘

G1GC:
┌────┬────┬────┬────┬────┬────┬────┬────┬────┐
│ E  │ E  │ S  │ O  │ E  │ O  │ H  │ O  │ E  │
└────┴────┴────┴────┴────┴────┴────┴────┴────┘
E=Eden  S=Survivor  O=Old  H=Humongous
(each box = one region, dynamically assigned)
```

The heap is divided into equal-sized regions (1MB–32MB each). Each region is **dynamically labelled** as Eden, Survivor, Old, or Humongous. There's no fixed boundary — G1 reassigns region roles as needed. This is the fundamental architectural difference.

---

## G1GC Collection Phases

### Phase 1 — Minor GC (Young Collection)
- Runs when Eden regions fill up
- Copies live objects from Eden → Survivor regions
- Promotes aged objects → Old regions
- **Stop-The-World but very short** (typically <50ms)
- Runs in parallel using multiple threads

### Phase 2 — Concurrent Marking Cycle
This is what makes G1 special — it runs **alongside your application**:

```
Step 1: Initial Mark        (STW, piggybacks on Minor GC)
         ↓
Step 2: Root Region Scan    (Concurrent — app keeps running)
         ↓
Step 3: Concurrent Mark     (Concurrent — traces all live objects)
         ↓
Step 4: Remark              (STW, short — finalises marking)
         ↓
Step 5: Cleanup             (STW for accounting, concurrent for freeing)
```

The key insight: **most of the work is concurrent** — your API keeps serving requests during steps 2 and 3.

### Phase 3 — Mixed GC
After concurrent marking, G1 knows exactly which Old regions have the most garbage. It then collects:
- All Young regions (as usual)
- **Plus** a selection of Old regions with highest garbage ratio

This is the "Garbage First" — it prioritises regions that give the most memory back per unit of work.

### Phase 4 — Full GC (last resort)
If G1 can't keep up with allocation rate — falls back to single-threaded Full GC. This is a **failure mode** you want to avoid. It means your heap is too small or your allocation rate is too high.

---

## The Pause Time Goal — How It Actually Works

```bash
-XX:MaxGCPauseMillis=200   # This is a TARGET, not a guarantee
```

G1 uses this as a **soft goal**. Before each GC cycle it calculates:
- How many regions can I collect within 200ms?
- It picks only those regions — leaves the rest for later

It builds a **predictive model** based on historical GC times to estimate how long collecting N regions will take. Over time it gets better at meeting the target.

**Practical implication:** If you set 200ms but your regions are huge or allocation rate is very high, G1 will breach the target. It's a best-effort goal.

---

## Humongous Objects — A Special Case

Any object **larger than 50% of a region size** is called a Humongous object:

```bash
# If region size = 16MB, objects > 8MB are Humongous
-XX:G1HeapRegionSize=16m
```

Humongous objects:
- Get allocated directly in Old Gen (skipping Young Gen entirely)
- Occupy one or more contiguous regions
- Are collected only during Concurrent Marking or Full GC
- **Can cause fragmentation** if you have many large objects

**Real-world example:** In your Kafka/Dataflow pipelines at Lloyds, large message batches or BigQuery result sets could become Humongous objects. Worth monitoring.

---

## G1GC Tuning — Practical Scenarios

### Scenario 1: Too many Minor GCs (high allocation rate)
**Symptom:** Minor GC running every few seconds, high CPU
**Fix:** Increase Young Gen size
```bash
-XX:G1NewSizePercent=30        # was 20 (default)
-XX:G1MaxNewSizePercent=50     # was 40 (default)
```

### Scenario 2: Pause times exceeding target
**Symptom:** GC pauses >200ms, latency spikes in API
**Fix 1:** Reduce region size — more, smaller regions = finer-grained collection
```bash
-XX:G1HeapRegionSize=8m        # was 16m
```
**Fix 2:** Increase heap — G1 has more room to work with
```bash
-Xmx12g                        # was 8g
```
**Fix 3:** Start concurrent marking earlier
```bash
-XX:InitiatingHeapOccupancyPercent=35   # default 45
# Start marking when heap is 35% full instead of 45%
# Gives G1 more time before Old Gen fills up
```

### Scenario 3: Full GC happening (worst case)
**Symptom:** `GC (Allocation Failure)` or `GC (Humongous Allocation)` in logs, long pauses
**Fix:** Either heap too small, or objects promoted to Old Gen too fast
```bash
-Xmx16g                                    # Increase heap
-XX:G1HeapWastePercent=5                   # default 5, lower = more eager cleanup
-XX:G1MixedGCCountTarget=8                 # More mixed GC rounds to clear Old Gen
```

### Scenario 4: Kubernetes container sizing
```bash
# Don't hardcode heap in containers — use percentage
-XX:InitialRAMPercentage=50.0
-XX:MaxRAMPercentage=75.0
# For a 4GB container: starts at 2GB heap, max 3GB
# Leaves room for Metaspace, thread stacks, off-heap (Kafka buffers etc)
```

---

## G1GC Logs — What to Read

With logging enabled:
```bash
-Xlog:gc*:file=/logs/gc.log:time,uptime:filecount=5,filesize=20m
```

A healthy log looks like:
```
[2.456s] GC(5) Pause Young (Normal) (G1 Evacuation Pause) 512M->234M 45ms
[8.123s] GC(6) Pause Young (Normal) (G1 Evacuation Pause) 489M->201M 38ms
```
- Heap going down after GC ✅
- Pause times under target ✅
- Only Young collections, no Mixed or Full ✅

A **warning sign** looks like:
```
[45.2s]  GC(21) Pause Full (Allocation Failure) 7G->6.8G 4500ms  ⚠️
```
- Full GC with 4.5 second pause — your API just froze
- Heap barely reduced (7G → 6.8G) — possible memory leak

---

# Your Second Question — Heap vs Stack Memory

This is a really important distinction and you're right to ask.

## The Simple Rule

| Memory Area | What lives there | Lifetime |
|---|---|---|
| **Stack** | Method local variables, method call frames, primitive values, object references | Until method returns |
| **Heap** | Actual object instances (regardless of where they were created) | Until GC collects them |
| **Metaspace** | Class definitions, static variables, static methods | Until class is unloaded |

---

## Let's Trace Through Code

```java
public void processPayment(String accountId) {       // (1)
    int amount = 500;                                  // (2)
    Payment payment = new Payment(accountId, amount); // (3)
    PaymentResult result = paymentService             // (4)
        .process(payment);
    System.out.println(result.getStatus());
}
```

**What goes where:**

**(1) Method call frame** → pushed onto the **Stack** for this thread. Contains the method's local variable table.

**(2) `amount = 500`** → primitive `int`, lives entirely on the **Stack**. No heap involved.

**(3) `new Payment(...)`** →
- The **reference variable** `payment` (just a pointer, 4 or 8 bytes) → **Stack**
- The actual **Payment object** (with all its fields) → **Heap**

**(4) `result` reference** → **Stack**. The actual `PaymentResult` object → **Heap**

When `processPayment()` returns → stack frame is **popped**, local variables `amount`, `payment`, `result` are gone instantly. But the Payment and PaymentResult objects on the heap remain until GC determines nothing else references them.

---

## Visual Representation

```
STACK (Thread 1)                    HEAP
┌─────────────────────┐            ┌──────────────────────────┐
│ processPayment frame │            │                          │
│  amount = 500        │            │  Payment object          │
│  payment ──────────────────────► │  { accountId: "123"      │
│  result ───────────────────┐     │    amount: 500 }         │
└─────────────────────┘      │     │                          │
                              │     │  PaymentResult object    │
                              └───► │  { status: "SUCCESS" }  │
                                    │                          │
                                    └──────────────────────────┘
```

---

## What About Static Variables?

```java
public class PaymentService {
    private static final int MAX_RETRY = 3;           // Metaspace
    private static List<String> auditLog = new ArrayList<>(); // reference in Metaspace
                                                               // ArrayList object in Heap
}
```

- The **static reference** `auditLog` lives in **Metaspace**
- The actual **ArrayList object** it points to lives on the **Heap**
- Static objects are GC roots — they're never collected unless the class is unloaded

This is a common source of **memory leaks** — static collections that keep growing. In a banking system, if you accidentally store transaction objects in a static list and never clear it, Old Gen fills up and you get Full GCs.

---

## Instance Variables

```java
public class AccountService {
    private KafkaProducer producer;    // instance variable
    private int retryCount;            // instance variable
}
```

Instance variables live **on the Heap as part of their owning object** — they're not stack variables. When an `AccountService` object is created on the heap, all its fields (including `retryCount`) are stored within that object's memory block on the heap.

---

## Practical Implications for Your Work

**Memory leak pattern 1 — Static collection growth:**
```java
// BAD — this will fill up Old Gen
private static Map<String, Transaction> cache = new HashMap<>();
// Fix: use WeakHashMap, or bounded cache like Caffeine
```

**Memory leak pattern 2 — ThreadLocal not cleaned up:**
```java
// In request-scoped code with thread pools
ThreadLocal<UserContext> context = new ThreadLocal<>();
// If you don't call context.remove() after request, the
// object stays referenced by the thread → never GC'd
// In a thread pool, threads live forever → memory leak
```

**Memory leak pattern 3 — Event listeners not deregistered:**
```java
// Registering a listener that holds a reference to a large object
eventBus.register(this);
// If you never deregister, the eventBus holds a reference
// → object never GC'd even after it's "done"
```

---

## Interview Answer — Heap vs Stack in 30 Seconds

*"Stack memory is per-thread and holds method call frames — local primitive values and object references live there and are reclaimed automatically when the method returns. The actual object instances always live on the heap, regardless of where they were created. Static fields' references live in Metaspace, but the objects they point to are still on the heap. This distinction matters practically — memory leaks almost always come from heap objects being unintentionally kept alive through static references, uncleaned ThreadLocals, or underegistered listeners."*

---

Ready to move to **Question 2 — Thread safety: synchronized vs volatile vs ReentrantLock?** This one builds naturally on what you just learned!

That's an excellent answer — honestly better than most senior engineers give. You clearly understand the concepts deeply. Let me sharpen and structure it, fill the gaps, and add the bits that would make it a **perfect interview answer.**

---

# Synchronized vs Volatile vs ReentrantLock — Complete Picture


https://www.geeksforgeeks.org/java/reentrant-lock-in-java/
https://www.geeksforgeeks.org/java/happens-before-relationship-in-java/
https://www.geeksforgeeks.org/java/difference-between-atomic-volatile-and-synchronized-in-java/
---

## Your Foundation is Spot On

You nailed the two core concepts:

| Concept | Meaning |
|---|---|
| **Visibility** | Changes made by one thread are immediately visible to other threads (flushed to/read from main memory, not CPU cache) |
| **Atomicity** | A block of code executes as a single indivisible unit — no other thread can interleave |

| Mechanism | Visibility | Atomicity |
|---|---|---|
| `volatile` | ✅ Yes | ❌ No |
| `synchronized` | ✅ Yes | ✅ Yes |
| `ReentrantLock` | ✅ Yes | ✅ Yes |

---

## Why CPU Caches Cause the Problem

This is the root cause worth understanding deeply:

```
Thread 1 (CPU Core 1)          Thread 2 (CPU Core 2)
┌─────────────────┐            ┌─────────────────┐
│  L1/L2 Cache    │            │  L1/L2 Cache    │
│  balance = 100  │            │  balance = 100  │  ← stale copy!
└────────┬────────┘            └────────┬────────┘
         │                              │
         └──────────────┬───────────────┘
                        │
                ┌───────▼───────┐
                │  Main Memory  │
                │  balance = 150│  ← Thread 1 already wrote 150
                └───────────────┘
```

Thread 2 is reading `balance = 100` from its cache, unaware Thread 1 updated it to 150 in main memory. This is the **visibility problem.**

Now imagine both threads read 100, both add 50, both write 150. The correct answer should be 200. This is the **atomicity problem** — two operations (read + write) that should be one.

---

## 1. Synchronized — Deep Dive

```java
public class BankAccount {
    private int balance = 1000;

    // Method-level lock — locks the entire object (this)
    public synchronized void deposit(int amount) {
        balance += amount;
    }

    // Block-level lock — finer grained, locks only critical section
    public void withdraw(int amount) {
        synchronized(this) {
            if (balance >= amount) {
                balance -= amount;
            }
        }
    }
}
```

**What happens under the hood:**
1. Thread acquires the **monitor lock** on the object
2. JVM flushes thread's cache → reads fresh values from main memory (**visibility**)
3. Only one thread holds the lock at a time (**atomicity**)
4. On exit, JVM flushes changes back to main memory
5. Lock is released — other threads can now acquire it

**Reentrancy in synchronized:**
```java
public synchronized void methodA() {
    methodB();  // same thread can call this — won't deadlock
}

public synchronized void methodB() {
    // synchronized is reentrant by default
    // same thread can reacquire the same lock
}
```

**Key limitations you correctly identified:**
- No way to check if lock is available without blocking
- No timeout — thread waits indefinitely
- Cannot be interrupted while waiting
- Always released at end of block — no manual control

---

## 2. ReentrantLock — Deep Dive

```java
public class BankAccount {
    private int balance = 1000;
    private final ReentrantLock lock = new ReentrantLock();

    public void deposit(int amount) {
        lock.lock();           // explicit acquire
        try {
            balance += amount;
        } finally {
            lock.unlock();     // ALWAYS unlock in finally — critical!
        }
    }
}
```

**The additional capabilities over synchronized:**

### tryLock() — non-blocking attempt
```java
// Don't want to wait? Try and move on
if (lock.tryLock()) {
    try {
        balance += amount;
    } finally {
        lock.unlock();
    }
} else {
    // Lock not available — do something else instead of blocking
    System.out.println("Account busy, try again later");
}
```

### tryLock(timeout) — wait with timeout
```java
// Wait up to 2 seconds, then give up
if (lock.tryLock(2, TimeUnit.SECONDS)) {
    try {
        processPayment();
    } finally {
        lock.unlock();
    }
} else {
    throw new TimeoutException("Could not acquire lock within 2s");
}
```
**Real-world use:** In your payment processing at Lloyds — if you can't acquire a lock on an account within N seconds, fail fast and return an error rather than hanging the request.

### lockInterruptibly() — interruptible waiting
```java
// This thread can be interrupted while waiting for the lock
lock.lockInterruptibly();
try {
    processTransaction();
} finally {
    lock.unlock();
}
```
**Real-world use:** If you need to cancel a pending operation (user cancels a transfer), you can interrupt the thread waiting for the lock. Not possible with `synchronized`.

### Reentrancy — same thread, multiple acquisitions
```java
lock.lock();  // count = 1
try {
    lock.lock();  // same thread — count = 2, doesn't block itself
    try {
        // do work
    } finally {
        lock.unlock();  // count = 1
    }
} finally {
    lock.unlock();  // count = 0 — fully released
}
```
ReentrantLock tracks a **hold count** — the lock is only truly released when count reaches 0.

### Fair vs Unfair locking
```java
// Fair — threads acquire lock in order they requested it (FIFO)
ReentrantLock fairLock = new ReentrantLock(true);

// Unfair (default) — any waiting thread can jump the queue
ReentrantLock unfairLock = new ReentrantLock(false);
```
Fair locks prevent **thread starvation** but have lower throughput. Unfair is faster but a thread could theoretically wait forever.

### Condition variables — advanced coordination
```java
ReentrantLock lock = new ReentrantLock();
Condition notEmpty = lock.newCondition();
Condition notFull = lock.newCondition();

// Producer
lock.lock();
try {
    while (queue.isFull()) {
        notFull.await();        // release lock and wait
    }
    queue.add(item);
    notEmpty.signal();          // wake up a consumer
} finally {
    lock.unlock();
}
```
With `synchronized` you only get `wait()/notify()` on one condition. ReentrantLock lets you have **multiple conditions** on the same lock — much more expressive for producer/consumer patterns like your Kafka pipelines.

---

## 3. Volatile — Deep Dive

```java
public class StatusFlag {
    private volatile boolean running = true;  // volatile keyword

    public void stop() {
        running = false;        // write goes directly to main memory
    }

    public void run() {
        while (running) {       // read always from main memory
            processNextItem();
        }
    }
}
```

**What volatile guarantees:**
- Every write to `running` is immediately flushed to main memory
- Every read of `running` goes directly to main memory, bypassing CPU cache
- All writes to **any variable** that happen before writing `volatile` are also visible (happens-before guarantee)

**What volatile does NOT guarantee — the classic mistake:**
```java
private volatile int counter = 0;

// Thread 1                    // Thread 2
counter++;                     counter++;
// This is NOT atomic!
// counter++ = read + increment + write (3 operations)
// Even with volatile, two threads can both read 0,
// both increment to 1, both write 1
// Final value: 1 instead of 2
```

**Volatile is only safe when:**
- Only **one thread writes**, multiple threads read
- The write is a **single atomic operation** (assignment, not read-modify-write)

```java
// SAFE — one writer, many readers
private volatile boolean shutdownFlag = false;
// One thread sets it to true, others just read it

// SAFE — reference assignment is atomic in Java
private volatile Config config;
// One thread replaces entire config object, others read it

// NOT SAFE — read-modify-write
private volatile int counter;
counter++;  // Not atomic even with volatile
```

---

## 4. The Missing Piece — Atomic Classes

You mentioned this gap yourself — volatile doesn't solve atomicity for operations like `counter++`. The answer is **`java.util.concurrent.atomic`**:

```java
// Instead of volatile int counter
private AtomicInteger counter = new AtomicInteger(0);

counter.incrementAndGet();    // atomic, no lock needed
counter.compareAndSet(5, 10); // CAS — compare-and-swap
```

**How it works:** Uses **Compare-And-Swap (CAS)** CPU instructions — hardware-level atomicity without locks. Much faster than synchronized for simple operations.

```java
// AtomicReference — for atomically swapping object references
private AtomicReference<Config> config = new AtomicReference<>();

Config newConfig = loadConfig();
config.set(newConfig);        // atomic reference swap
```

**In your Lloyds platform:** AtomicInteger for counters/metrics, AtomicReference for swapping configuration objects without locks.

---

## 5. When to Use What — Decision Guide

```
Do you need atomicity?
│
├── NO (just visibility, single writer)
│   └── Use volatile
│       Example: shutdown flags, config references
│
└── YES (read-modify-write operations)
    │
    ├── Simple counter / single variable
    │   └── Use AtomicInteger / AtomicReference
    │       Fastest, lock-free
    │
    └── Complex multi-step operation / critical section
        │
        ├── Simple use case, no special requirements
        │   └── Use synchronized
        │       Clean, implicit, less code
        │
        └── Need timeout / tryLock / interruption / fairness
            └── Use ReentrantLock
                More control, more responsibility
```

---

## 6. Deadlock — The Risk With Both Synchronized and ReentrantLock

Worth knowing since interviewers often follow up:

```java
// Classic deadlock — two threads, two locks, opposite order
Thread 1: lock(accountA) → tries lock(accountB)  // waits
Thread 2: lock(accountB) → tries lock(accountA)  // waits
// Both wait forever
```

**Prevention strategies:**
- Always acquire locks in a **consistent order** across all threads
- Use `tryLock(timeout)` with ReentrantLock — gives up after N seconds
- Use higher-level abstractions like `ConcurrentHashMap` that handle locking internally

---

## Interview Answer — Polished 60-Second Version

*"In multithreaded systems, shared state creates two problems — visibility, where a thread reads stale data from its CPU cache instead of main memory, and atomicity, where a compound operation like read-modify-write can be interleaved by another thread.*

*Volatile solves only visibility — any variable marked volatile is always read from and written to main memory directly. It's appropriate for single-writer flags or reference swaps, but not for operations like incrementing a counter.*

*Synchronized solves both — only one thread enters a synchronized block at a time, and the JVM ensures memory is flushed on entry and exit. It's clean and simple but gives you no control over waiting behaviour.*

*ReentrantLock achieves the same but with additional power — tryLock with timeout, interruptible waiting, fairness control, and multiple condition variables. I've used this in payment processing scenarios where failing fast after a timeout is better than blocking indefinitely.*

*For simple atomic operations on single variables, AtomicInteger and AtomicReference are the best choice — they use hardware-level CAS instructions, giving you atomicity without any locking overhead."*

---

Ready for **Question 3 — How do you design for thread safety in a high-concurrency Java service?** This builds directly on everything we just covered!

Excellent answer again — you've got the right mental model. Let me do two things: **validate and sharpen** what you said, then **add the missing layer** that would elevate this to a senior/staff-level answer.

---

## What You Nailed ✅

Your thinking is exactly right and well structured:

- Stateless first — avoid shared state wherever possible
- Use battle-tested thread-safe libraries for cache/pools/sessions
- Only hand-roll thread safety when genuinely needed
- Correct decision tree: Atomic → Volatile → Synchronized → ReentrantLock based on use case

---

## What Would Elevate This to Senior Level

The interviewer will expect you to name **specific concurrency tools and patterns** beyond the basic four. Here's what a senior engineer adds:

---

## The Full Thread Safety Toolkit

### 1. Concurrent Collections — Use These First

Before reaching for locks, ask: *does Java already have a thread-safe version of what I need?*

```java
// Instead of synchronizing a HashMap manually
Map<String, Account> accounts = new ConcurrentHashMap<>();
// Segment-level locking — much better throughput than full lock
// reads are lock-free, writes lock only the affected segment

// Thread-safe list
List<Event> events = new CopyOnWriteArrayList<>();
// Every write creates a new copy of the array
// Great for read-heavy, write-rare scenarios (event listeners)

// Thread-safe queue — perfect for producer/consumer
BlockingQueue<Transaction> queue = new LinkedBlockingQueue<>(1000);
queue.put(transaction);    // blocks if full
queue.take();              // blocks if empty
// Used this pattern in your Kafka consumer pipelines implicitly
```

**Real-world from your Lloyds work:** `ConcurrentHashMap` for in-memory account state cache, `BlockingQueue` for internal task dispatch between threads.

---

### 2. ReadWriteLock — Underused but Powerful

Your answer covered single-writer/multi-reader with volatile. But for complex objects where volatile isn't enough:

```java
private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
private Map<String, RateLimit> rateLimitConfig = new HashMap<>();

// Multiple threads can read simultaneously
public RateLimit getLimit(String apiKey) {
    rwLock.readLock().lock();
    try {
        return rateLimitConfig.get(apiKey);
    } finally {
        rwLock.readLock().unlock();
    }
}

// Only one thread can write, blocks all readers
public void updateConfig(Map<String, RateLimit> newConfig) {
    rwLock.writeLock().lock();
    try {
        rateLimitConfig = newConfig;
    } finally {
        rwLock.writeLock().unlock();
    }
}
```

**When to use:** Configuration objects, rate limit maps, reference data that's read thousands of times per second but updated rarely. Much better throughput than `synchronized` for read-heavy workloads.

---

### 3. ThreadLocal — Per-Thread State

You mentioned request/thread context — this is the mechanism:

```java
// Each thread gets its own independent copy
private static final ThreadLocal<RequestContext> requestContext 
    = new ThreadLocal<>();

// In your request filter/interceptor
public void onRequestStart(HttpRequest request) {
    requestContext.set(new RequestContext(
        request.getCorrelationId(),
        request.getUserId()
    ));
}

// Anywhere in the same thread's call stack
public void processPayment() {
    String correlationId = requestContext.get().getCorrelationId();
    // No need to pass it as a method parameter
}

// CRITICAL — always clean up in finally
public void onRequestEnd() {
    requestContext.remove();  // prevents memory leak in thread pools
}
```

**Real-world from your work:** Correlation IDs, user context, tenant context flowing through your microservices request pipeline. Spring's `RequestContextHolder` uses this internally.

**The critical gotcha** — always `remove()` in finally. In thread pools, threads are reused. If you don't clean up, the next request picks up the previous request's context. This is a nasty, hard-to-reproduce bug in production.

---

### 4. ExecutorService — Thread Pool Management

Rather than creating raw threads:

```java
// Fixed thread pool — predictable resource usage
ExecutorService executor = Executors.newFixedThreadPool(10);

// For your use case — bounded thread pool with queue
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    10,                           // core threads
    20,                           // max threads
    60L, TimeUnit.SECONDS,        // idle thread timeout
    new ArrayBlockingQueue<>(100),// bounded task queue
    new ThreadPoolExecutor.CallerRunsPolicy()  // backpressure strategy
);

// Submit tasks
Future<PaymentResult> future = executor.submit(() -> processPayment(txn));
PaymentResult result = future.get(5, TimeUnit.SECONDS);  // timeout
```

**CallerRunsPolicy** is important — when queue is full, the calling thread executes the task itself instead of rejecting. This creates natural backpressure without throwing exceptions.

---

### 5. CompletableFuture — Modern Async Composition

For non-blocking, async flows:

```java
CompletableFuture
    .supplyAsync(() -> fetchAccount(accountId), executor)
    .thenApplyAsync(account -> validateBalance(account), executor)
    .thenCombineAsync(
        fetchExchangeRate(currency),
        (account, rate) -> calculateAmount(account, rate)
    )
    .thenAcceptAsync(amount -> postTransaction(amount))
    .exceptionally(ex -> {
        log.error("Payment failed", ex);
        return null;
    });
```

**Real-world:** Parallel calls to multiple downstream services — fetch account details and fetch rate limit config simultaneously, then combine. Much cleaner than managing threads manually.

---

## The Stateless Design Principle — Expand on This

Your instinct about stateless is exactly right. Worth articulating the full pattern:

```
Stateless Microservice Design:

Request → Thread → Process → Response
              ↓
    All state lives in:
    ├── Request context (ThreadLocal, cleared after request)
    ├── External cache (Redis — thread-safe by nature, shared across instances)
    ├── Database (PostgreSQL, Spanner — ACID transactions handle concurrency)
    └── Message broker (Kafka — immutable log, no shared mutable state)

    NOT in:
    ├── Static mutable fields ❌
    ├── Instance variables on singletons ❌
    └── In-memory shared collections without synchronization ❌
```

**For your Lloyds platform:** Account balances in Spanner (transactions handle consistency), rate limiting state in Redis, correlation IDs in ThreadLocal. The microservices themselves are stateless — any instance can handle any request.

---

## Polished 90-Second Interview Answer

*"My first principle is designing stateless services — push shared state to external systems like Redis, Spanner, or Kafka where concurrency is handled by the infrastructure itself. This eliminates most thread safety concerns at the application level.*

*Where shared in-process state is unavoidable — caches, connection pools, reference data — I reach for Java's concurrent collections first: ConcurrentHashMap for maps, CopyOnWriteArrayList for read-heavy lists, BlockingQueue for producer-consumer pipelines. These are battle-tested and avoid hand-rolled locking.*

*For request-scoped context like correlation IDs or tenant info, ThreadLocal gives each thread its own isolated copy — always cleaned up in a finally block to avoid leaks in thread pools.*

*When I genuinely need synchronization: AtomicInteger or AtomicLong for counters — lock-free and fast. ReadWriteLock for objects that are read heavily but written rarely, like config maps. Synchronized blocks for simple critical sections. ReentrantLock when I need tryLock with timeout — which I've used in payment flows where failing fast after a bounded wait is better than an indefinite block.*

*The problems I'm always designing against are race conditions, deadlocks — which I prevent through consistent lock ordering and bounded timeouts — and stale reads, which volatile and proper synchronization address."*

---

That's a complete, senior-level answer. Ready for **Question 4 — SOLID principles with real examples from your work?**

Outstanding answer — you clearly understand SOLID deeply and can relate it to real-world impact. Let me validate, sharpen, and add the senior-level layer with concrete code examples and your Lloyds context.

---

## What You Nailed ✅

- Correct definitions for all 5 principles
- Correctly linked Open/Closed to GoF patterns (Strategy, Decorator)
- Good real-world example for Interface Segregation (deposit account)
- Correctly explained DI as container-managed object creation
- Consistently tied back to loose coupling — that's the thread that runs through all 5

---

## Sharpened with Code & Your Lloyds Context

---

### S — Single Responsibility Principle

Your definition is perfect. Let me show the before/after that makes it concrete:

```java
// VIOLATES SRP — this class has 3 reasons to change:
// 1. Business logic changes, 2. Persistence changes, 3. Notification changes
public class PaymentService {
    public void processPayment(Payment payment) {
        // business logic
        validatePayment(payment);
        calculateFees(payment);

        // persistence — reason 2 to change
        database.save(payment);

        // notification — reason 3 to change
        emailService.sendConfirmation(payment);
        smsService.sendAlert(payment);
    }
}

// FOLLOWS SRP — each class has one reason to change
public class PaymentProcessor {
    public PaymentResult process(Payment payment) {
        validatePayment(payment);
        return calculateFees(payment);
    }
}

public class PaymentRepository {
    public void save(Payment payment) { ... }
}

public class PaymentNotificationService {
    public void notify(Payment payment) { ... }
}
```

**From your Lloyds platform:** Your 12 domain-aligned microservices are SRP at the service level — each owns one bounded context. Within a service, your layered architecture (controller → service → repository) is SRP at the class level.

---

### O — Open/Closed Principle

```java
// VIOLATES OCP — every new payment type requires modifying this class
public class PaymentProcessor {
    public void process(Payment payment) {
        if (payment.getType().equals("INSTANT")) {
            // instant logic
        } else if (payment.getType().equals("SCHEDULED")) {
            // scheduled logic
        } else if (payment.getType().equals("INTERNATIONAL")) {
            // new type added — modified existing class ❌
        }
    }
}

// FOLLOWS OCP — Strategy pattern
// Closed for modification, open for extension
public interface PaymentStrategy {
    PaymentResult execute(Payment payment);
}

public class InstantPaymentStrategy implements PaymentStrategy {
    public PaymentResult execute(Payment payment) { ... }
}

public class ScheduledPaymentStrategy implements PaymentStrategy {
    public PaymentResult execute(Payment payment) { ... }
}

// Adding international payments = new class, zero changes to existing code ✅
public class InternationalPaymentStrategy implements PaymentStrategy {
    public PaymentResult execute(Payment payment) { ... }
}

public class PaymentProcessor {
    private final PaymentStrategy strategy;

    public PaymentProcessor(PaymentStrategy strategy) {
        this.strategy = strategy;  // injected — DI ties in here
    }

    public PaymentResult process(Payment payment) {
        return strategy.execute(payment);  // no if/else, no modification needed
    }
}
```

**Decorator example — adding cross-cutting behaviour:**
```java
// Add audit logging to any payment strategy without modifying it
public class AuditedPaymentStrategy implements PaymentStrategy {
    private final PaymentStrategy delegate;

    public AuditedPaymentStrategy(PaymentStrategy delegate) {
        this.delegate = delegate;
    }

    public PaymentResult execute(Payment payment) {
        auditLog.record(payment);           // added behaviour
        PaymentResult result = delegate.execute(payment);  // original intact
        auditLog.record(result);
        return result;
    }
}
```

**From your Lloyds work:** Your resilience patterns (circuit breaker, retry) wrapping service calls are essentially decorators — adding behaviour without modifying the underlying service.

---

### L — Liskov Substitution Principle

Your explanation is correct. Here's the classic violation that makes it memorable:

```java
// Classic LSP violation — the Rectangle/Square problem
public class Rectangle {
    protected int width, height;

    public void setWidth(int width)   { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public int area() { return width * height; }
}

public class Square extends Rectangle {
    // A square must have equal sides — so override both setters
    public void setWidth(int width) {
        this.width = width;
        this.height = width;  // forces height = width ❌
    }
    public void setHeight(int height) {
        this.width = height;
        this.height = height;
    }
}

// Client code — works fine with Rectangle
// BREAKS with Square substituted in — LSP violated
Rectangle r = new Square();
r.setWidth(5);
r.setHeight(3);
// Expected area: 15, Actual area: 9 ❌
// Square silently broke the Rectangle contract
```

**Banking example — your deposit account scenario extended:**
```java
// VIOLATES LSP
public class Account {
    public void withdraw(double amount) { ... }
}

public class FixedTermDeposit extends Account {
    public void withdraw(double amount) {
        throw new UnsupportedOperationException("Cannot withdraw from FTD");
        // Child cannot honour parent's contract — LSP violated ❌
    }
}

// FOLLOWS LSP — restructure the hierarchy
public interface Account {
    double getBalance();
    void deposit(double amount);
}

public interface WithdrawableAccount extends Account {
    void withdraw(double amount);
}

public class CurrentAccount implements WithdrawableAccount { ... }  // ✅
public class FixedTermDeposit implements Account { ... }            // ✅ — no withdraw
```

**Key insight to add in interview:** LSP violation is usually a signal that your inheritance hierarchy is wrong. The fix is often to restructure using interfaces rather than inheritance — which naturally leads into Interface Segregation.

---

### I — Interface Segregation Principle

Your deposit/withdrawal example is perfect. Here's the code form of it:

```java
// VIOLATES ISP — fat interface forces FixedTermDeposit to implement withdraw
public interface BankAccount {
    void open(Customer customer);
    double getBalance();
    void deposit(double amount);
    void withdraw(double amount);        // FixedTermDeposit doesn't support this
    void applyInterest();                // CurrentAccount doesn't support this
    void setMaturityDate(LocalDate date); // Only FTD needs this
}

// FixedTermDeposit forced to do this ❌
public class FixedTermDeposit implements BankAccount {
    public void withdraw(double amount) {
        throw new UnsupportedOperationException(); // nasty
    }
}

// FOLLOWS ISP — segregated interfaces
public interface Account {
    void open(Customer customer);
    double getBalance();
    void deposit(double amount);
}

public interface Withdrawable {
    void withdraw(double amount);
}

public interface InterestBearing {
    void applyInterest();
}

public interface TermDeposit {
    void setMaturityDate(LocalDate date);
}

// Each class implements only what it needs
public class CurrentAccount implements Account, Withdrawable { ... }
public class SavingsAccount implements Account, Withdrawable, InterestBearing { ... }
public class FixedTermDeposit implements Account, InterestBearing, TermDeposit { ... }
```

**From your Lloyds work:** Your commercial savings products (FTD, Notice, Instant Access) are a real example — each has different capabilities. Segregated interfaces meant each product journey only implemented relevant operations.

---

### D — Dependency Inversion Principle

Your explanation nails it. Two sub-rules worth stating explicitly:

1. High-level modules should not depend on low-level modules — both should depend on abstractions
2. Abstractions should not depend on details — details should depend on abstractions

```java
// VIOLATES DIP — high-level PaymentService directly depends on
// low-level PostgreSQLRepository (a concrete detail)
public class PaymentService {
    private PostgreSQLRepository repository = new PostgreSQLRepository(); // ❌

    public void process(Payment payment) {
        repository.save(payment);
    }
}

// Migrating to CosmosDB = change PaymentService code ❌

// FOLLOWS DIP
public interface PaymentRepository {
    void save(Payment payment);
    Payment findById(String id);
}

public class PostgreSQLPaymentRepository implements PaymentRepository { ... }
public class CosmosDBPaymentRepository implements PaymentRepository { ... }
public class InMemoryPaymentRepository implements PaymentRepository { ... } // for tests!

// High-level module depends on abstraction only
public class PaymentService {
    private final PaymentRepository repository;  // interface, not concrete class

    // Spring injects the right implementation at runtime
    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public void process(Payment payment) {
        repository.save(payment);  // works with any implementation ✅
    }
}
```

**Spring Boot wires this automatically:**
```java
@Configuration
public class AppConfig {

    @Bean
    @Profile("production")
    public PaymentRepository productionRepository() {
        return new CosmosDBPaymentRepository();
    }

    @Bean
    @Profile("test")
    public PaymentRepository testRepository() {
        return new InMemoryPaymentRepository();  // fast tests, no DB needed
    }
}
```

**From your Lloyds work:** Migrating from one data store to another (batch pipelines → Kafka/Spanner) was lower risk precisely because your service layer depended on repository interfaces, not concrete implementations. Swap the bean, keep the business logic intact.

---

## How the 5 Principles Connect

This is what separates a senior answer — showing they're not isolated rules:

```
SRP  →  Each class has one job
         ↓
OCP  →  Add new jobs by extending, not modifying existing classes
         ↓
LSP  →  Extensions must honour the original contract
         ↓
ISP  →  Keep contracts small and focused so extensions only take what they need
         ↓
DIP  →  Depend on contracts (interfaces), not on specific extensions (classes)

All 5 → LOOSE COUPLING + HIGH COHESION = maintainable, testable, extensible code
```

---

## Polished 90-Second Interview Answer

*"SOLID is fundamentally about writing loosely coupled, highly cohesive code that's easy to change without regression.*

*SRP keeps classes focused — in our platform each class owned one concern: processing, persistence, or notification. Our 12 microservices are SRP at the architectural level too.*

*OCP means adding behaviour through extension, not modification. We used the Strategy pattern extensively for payment types — adding a new payment method meant a new class, zero changes to existing processing logic. Our resilience decorators — circuit breaker, retry wrapping around service calls — are OCP in action.*

*LSP ensures subclasses honour their parent's contract. Our commercial savings products — FTD, Notice, Instant Access — have different capabilities, so we never forced them into a single inheritance hierarchy. A FixedTermDeposit that throws UnsupportedOperationException on withdraw is an LSP violation and usually signals you need interface segregation instead.*

*ISP is about keeping interfaces small and focused. Rather than one fat Account interface, we had segregated contracts — Withdrawable, InterestBearing, TermDeposit — so each product implemented only what it actually supports.*

*DIP is what makes all of this work at runtime. Our service layer depends on repository interfaces, never concrete implementations. Spring injects the right bean per environment — same code runs against Spanner in production and an in-memory store in tests. When we migrated data stores, business logic was untouched."*

---

Ready for **Question 5 — Exception handling across microservices?** Or shall we move to Area 2?

Excellent — this is a genuinely strong, senior-level answer. You've covered the full spectrum from low-level handling to cross-cutting concerns. Let me validate, fill the gaps, and add the code that makes it interview-ready.

---

## What You Nailed ✅

- Never silently swallow exceptions
- Specific exceptions first, parent as fallback
- Custom business exceptions with meaningful names
- Wrapping internal exceptions before exposing to consumers
- Always log stack traces
- Structured logging with correlation/trace IDs
- 4xx vs 5xx segregation
- Global exception handler in Spring (`@ControllerAdvice`)
- Decoupling exception handling from business logic

---

## The Complete Picture with Code

---

### Layer 1 — Custom Exception Hierarchy

```java
// Base exception for your domain
public abstract class BankingException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    protected BankingException(String message, String errorCode, 
                                HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected BankingException(String message, String errorCode,
                                HttpStatus httpStatus, Throwable cause) {
        super(message, cause);  // preserves original stack trace ✅
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}

// 4xx — Client errors (bad input, business rule violations)
public class InvalidAccountException extends BankingException {
    public InvalidAccountException(String accountId) {
        super("Account not found: " + accountId, 
              "ACCOUNT_NOT_FOUND", 
              HttpStatus.NOT_FOUND);
    }
}

public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String accountId, double amount) {
        super("Insufficient funds for account: " + accountId,
              "INSUFFICIENT_FUNDS",
              HttpStatus.UNPROCESSABLE_ENTITY);  // 422 — better than 400 here
    }
}

public class DuplicateTransactionException extends BankingException {
    public DuplicateTransactionException(String idempotencyKey) {
        super("Duplicate transaction: " + idempotencyKey,
              "DUPLICATE_TRANSACTION",
              HttpStatus.CONFLICT);  // 409
    }
}

// 5xx — Server/infrastructure errors
public class PaymentProcessingException extends BankingException {
    public PaymentProcessingException(String message, Throwable cause) {
        super(message, "PAYMENT_PROCESSING_ERROR",
              HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}

public class DownstreamServiceException extends BankingException {
    public DownstreamServiceException(String serviceName, Throwable cause) {
        super("Downstream service unavailable: " + serviceName,
              "DOWNSTREAM_ERROR",
              HttpStatus.SERVICE_UNAVAILABLE, cause);  // 503
    }
}
```

---

### Layer 2 — Service Layer Exception Wrapping

Your point about abstracting internal exceptions from consumers is critical:

```java
@Service
public class PaymentService {

    public PaymentResult processPayment(PaymentRequest request) {
        
        // Specific exceptions first, parent as fallback
        try {
            Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> 
                    new InvalidAccountException(request.getAccountId()));

            validateFunds(account, request.getAmount());
            
            return paymentGateway.execute(request);

        } catch (InvalidAccountException | InsufficientFundsException e) {
            // Business exceptions — let them propagate as-is
            // They already have the right error code and HTTP status
            throw e;

        } catch (DataAccessException e) {
            // Wrap infrastructure exception — don't expose DB details to consumer
            log.error("Database error processing payment for account: {}", 
                      request.getAccountId(), e);  // log full stack trace
            throw new PaymentProcessingException(
                "Payment processing failed", e);  // wraps cause ✅

        } catch (HttpClientErrorException e) {
            // Wrap downstream HTTP error
            log.error("Downstream service error calling payment gateway", e);
            throw new DownstreamServiceException("payment-gateway", e);

        } catch (Exception e) {
            // Last resort fallback — catch unexpected exceptions
            // Never let raw RuntimeException propagate to consumer
            log.error("Unexpected error processing payment", e);
            throw new PaymentProcessingException(
                "Unexpected payment error", e);
        }
    }
}
```

---

### Layer 3 — Standardised Error Response Schema

Define once, enforce everywhere:

```java
// Consistent error response — all errors follow this schema
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String errorCode;        // e.g. "ACCOUNT_NOT_FOUND"
    private String message;          // human-readable
    private String correlationId;    // for log tracing
    private String traceId;          // distributed trace
    private Instant timestamp;
    private String path;             // which endpoint failed
    private Map<String, String> details;  // field-level validation errors

    // builder, getters...
}

// Example response consumer receives — clean, no internal details exposed
{
    "errorCode": "INSUFFICIENT_FUNDS",
    "message": "Insufficient funds for the requested operation",
    "correlationId": "abc-123-def",
    "traceId": "xyz-789",
    "timestamp": "2025-05-05T10:30:00Z",
    "path": "/api/v1/payments"
}
```

---

### Layer 4 — Global Exception Handler

Your point about decoupling exception handling from business logic — this is how:

```java
@RestControllerAdvice  // applies to all controllers
@Slf4j
public class GlobalExceptionHandler {

    // Handle your custom banking exceptions
    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ErrorResponse> handleBankingException(
            BankingException ex, HttpServletRequest request) {

        log.error("Banking exception [{}]: {} | path: {} | correlationId: {}",
                  ex.getErrorCode(),
                  ex.getMessage(),
                  request.getRequestURI(),
                  MDC.get("correlationId"),
                  ex);  // logs full stack trace ✅

        return ResponseEntity
            .status(ex.getHttpStatus())
            .body(buildErrorResponse(ex.getErrorCode(), 
                                     ex.getMessage(), 
                                     request));
    }

    // Handle validation errors (bean validation @NotNull, @Size etc)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage
            ));

        log.warn("Validation failed: {} | correlationId: {}",
                 fieldErrors, MDC.get("correlationId"));

        ErrorResponse response = buildErrorResponse(
            "VALIDATION_FAILED", "Request validation failed", request);
        response.setDetails(fieldErrors);

        return ResponseEntity.badRequest().body(response);
    }

    // Handle unexpected exceptions — safety net
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex, HttpServletRequest request) {

        // Always log unexpected exceptions with full stack trace
        log.error("Unexpected error | path: {} | correlationId: {}",
                  request.getRequestURI(),
                  MDC.get("correlationId"),
                  ex);  // full stack trace ✅

        // Never expose internal details to consumer
        return ResponseEntity
            .internalServerError()
            .body(buildErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred",  // generic message ✅
                request));
    }

    private ErrorResponse buildErrorResponse(String errorCode, 
                                              String message,
                                              HttpServletRequest request) {
        return ErrorResponse.builder()
            .errorCode(errorCode)
            .message(message)
            .correlationId(MDC.get("correlationId"))
            .traceId(MDC.get("traceId"))
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build();
    }
}
```

---

### Layer 5 — Structured Logging with MDC

Your point about correlation IDs in logs — this is the implementation:

```java
// Request filter — runs before every request
@Component
@Order(1)
public class RequestContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, 
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;

        // Extract from header if provided by upstream, else generate
        String correlationId = Optional
            .ofNullable(request.getHeader("X-Correlation-ID"))
            .orElse(UUID.randomUUID().toString());

        String traceId = Optional
            .ofNullable(request.getHeader("X-Trace-ID"))
            .orElse(UUID.randomUUID().toString());

        // MDC — Mapped Diagnostic Context
        // Automatically appended to every log line in this thread
        MDC.put("correlationId", correlationId);
        MDC.put("traceId", traceId);
        MDC.put("requestPath", request.getRequestURI());
        MDC.put("method", request.getMethod());

        // Add to response headers so client can correlate
        HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("X-Correlation-ID", correlationId);

        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();  // critical — clean up ThreadLocal ✅
        }
    }
}
```

**Logback pattern — structured JSON logging:**
```xml
<!-- logback-spring.xml -->
<pattern>
{
  "timestamp": "%d{ISO8601}",
  "level": "%level",
  "service": "payment-service",
  "correlationId": "%X{correlationId}",
  "traceId": "%X{traceId}",
  "requestPath": "%X{requestPath}",
  "logger": "%logger{36}",
  "message": "%message",
  "exception": "%exception"
}
</pattern>
```

Every log line now automatically includes correlationId and traceId — no need to pass them as method parameters. Search in Dynatrace/ELK by correlationId to see the complete request journey across all services.

---

### Layer 6 — Cross-Service Exception Propagation

One area most candidates miss — how do you handle exceptions from downstream services?

```java
@Service
public class AccountService {

    // When calling another microservice via REST
    public Account fetchAccount(String accountId) {
        try {
            return accountServiceClient.getAccount(accountId);

        } catch (FeignException.NotFound e) {
            // 404 from downstream — translate to your domain exception
            throw new InvalidAccountException(accountId);

        } catch (FeignException.ServiceUnavailable | 
                 FeignException.GatewayTimeout e) {
            // Downstream is down — wrap and propagate
            throw new DownstreamServiceException("account-service", e);

        } catch (FeignException e) {
            // Other downstream errors
            log.error("Unexpected response from account-service: status={}", 
                      e.status(), e);
            throw new DownstreamServiceException("account-service", e);
        }
    }
}
```

**Kafka consumer exception handling:**
```java
@KafkaListener(topics = "payment-events")
public void handlePaymentEvent(PaymentEvent event) {
    try {
        processEvent(event);

    } catch (InvalidAccountException e) {
        // Business error — don't retry, send to dead letter topic
        log.error("Business error processing event: {}, sending to DLT", 
                  event.getId(), e);
        deadLetterTemplate.send("payment-events.DLT", event);

    } catch (DownstreamServiceException e) {
        // Transient error — let Kafka retry
        log.warn("Transient error processing event: {}, will retry", 
                 event.getId(), e);
        throw e;  // Kafka will retry based on retry config
    }
}
```

---

## The Complete Exception Handling Architecture

```
Incoming Request
      ↓
RequestContextFilter    → sets correlationId, traceId in MDC
      ↓
Controller              → receives request, calls service
      ↓
Service Layer           → business logic
      ├── throws InvalidAccountException (4xx)
      ├── throws InsufficientFundsException (4xx)
      ├── catches DataAccessException → wraps → PaymentProcessingException (5xx)
      └── catches Exception → wraps → PaymentProcessingException (5xx)
      ↓
GlobalExceptionHandler  → catches all exceptions, maps to ErrorResponse
      ├── BankingException     → correct HTTP status + errorCode
      ├── ValidationException  → 400 + field-level details
      └── Exception            → 500 + generic message (no internals exposed)
      ↓
Structured JSON Response → correlationId, errorCode, message, timestamp
      ↓
Logs (ELK/Dynatrace)    → searchable by correlationId across all services
```

---

## Polished 60-Second Interview Answer

*"Exception handling in microservices works in layers. First principle — never swallow exceptions silently, even on non-critical paths, always log with full stack trace and correlation context.*

*I define a custom exception hierarchy rooted in a base domain exception — business exceptions like InvalidAccountException or InsufficientFundsException carry their own error code and HTTP status. The service layer handles specific exceptions first with a parent Exception fallback, and wraps infrastructure exceptions — DataAccessException, FeignException — into domain exceptions before they reach the consumer. This abstracts internal details and keeps the API contract clean.*

*In Spring, a @ControllerAdvice global handler decouples all exception-to-response mapping from business logic — enforcing a consistent error schema with errorCode, message, correlationId, and timestamp across every endpoint. Validation errors map to 400 with field-level details, business errors to appropriate 4xx codes, infrastructure errors to 5xx.*

*For observability, every request sets correlationId and traceId in MDC via a filter — so every log line across every service in that request's chain carries the same ID. In Dynatrace or ELK you can search one correlationId and reconstruct the entire distributed request flow instantly.*

*For Kafka consumers, I distinguish retryable errors — transient infrastructure failures — from non-retryable business errors that go straight to a dead letter topic."*

---

That's Area 1 complete — 6 questions, all answered at senior level. Ready to move to **Area 2 — System Architecture for Scalable, Real-Time Systems?** First question: *"Walk me through how you'd design a real-time event-driven system for high throughput and low latency."*

Strong answer — you've got the core principles right. Let me validate, sharpen and add the senior-level layer.

---

## What You Nailed ✅

- Test one unit of behaviour, not everything
- Test public interfaces only, not private methods
- Objective assertions — expected vs actual, no hardcoded manipulation
- Mock external dependencies — keep tests fast and focused
- Clean test data setup
- Self-explanatory test and method names

---

## The Complete Picture with Code

---

### Principle 1 — Test Naming — Self Documenting

```java
// BAD — tells you nothing about what's being tested
@Test
public void test1() { ... }

@Test
public void testProcess() { ... }

// GOOD — methodName_scenario_expectedOutcome pattern
@Test
public void processPayment_whenInsufficientFunds_shouldThrowInsufficientFundsException() { ... }

@Test
public void processPayment_whenValidRequest_shouldReturnSuccessResult() { ... }

@Test
public void processPayment_whenAccountNotFound_shouldThrowInvalidAccountException() { ... }
```

Reading the test name alone should tell you exactly what behaviour is being verified — no need to read the body.

---

### Principle 2 — AAA Pattern — Structure Every Test

Every good unit test follows **Arrange, Act, Assert:**

```java
@Test
public void processPayment_whenValidRequest_shouldDeductBalanceAndReturnSuccess() {

    // ARRANGE — set up test data and mock behaviours
    String accountId = "ACC-001";
    double initialBalance = 1000.0;
    double paymentAmount = 250.0;

    Account account = Account.builder()
        .id(accountId)
        .balance(initialBalance)
        .status(AccountStatus.ACTIVE)
        .build();

    PaymentRequest request = PaymentRequest.builder()
        .accountId(accountId)
        .amount(paymentAmount)
        .currency("GBP")
        .build();

    // Mock external dependency — not what we're testing
    when(accountRepository.findById(accountId))
        .thenReturn(Optional.of(account));
    when(paymentGateway.execute(any()))
        .thenReturn(PaymentResult.success("TXN-123"));

    // ACT — call the unit under test
    PaymentResult result = paymentService.processPayment(request);

    // ASSERT — verify outcome objectively
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getTransactionId()).isEqualTo("TXN-123");

    // Verify interactions — was the right thing called?
    verify(accountRepository).findById(accountId);
    verify(paymentGateway).execute(argThat(r -> 
        r.getAmount() == paymentAmount && 
        r.getAccountId().equals(accountId)
    ));
    verify(auditService).record(any(PaymentResult.class));
}
```

---

### Principle 3 — Test Public Interface, Not Implementation

```java
// BAD — testing private implementation detail via reflection
@Test
public void testPrivateValidation() {
    Method method = PaymentService.class
        .getDeclaredMethod("validateAmount", double.class);
    method.setAccessible(true);
    // This is brittle — refactoring breaks the test even if
    // behaviour is correct ❌
}

// GOOD — test behaviour through public interface
// Private methods get tested indirectly through public method tests
@Test
public void processPayment_whenNegativeAmount_shouldThrowValidationException() {
    PaymentRequest request = PaymentRequest.builder()
        .accountId("ACC-001")
        .amount(-100.0)  // triggers private validation indirectly
        .build();

    assertThatThrownBy(() -> paymentService.processPayment(request))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Amount must be positive");
}
```

---

### Principle 4 — Mocking External Dependencies

```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    // Mock all external dependencies
    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private AuditService auditService;

    // Inject mocks into unit under test
    @InjectMocks
    private PaymentService paymentService;

    @Test
    public void processPayment_whenGatewayFails_shouldThrowDownstreamException() {

        // Arrange
        Account account = buildValidAccount();
        when(accountRepository.findById(any()))
            .thenReturn(Optional.of(account));

        // Simulate downstream failure
        when(paymentGateway.execute(any()))
            .thenThrow(new HttpServerErrorException(
                HttpStatus.SERVICE_UNAVAILABLE));

        // Act & Assert
        assertThatThrownBy(() -> 
            paymentService.processPayment(buildValidRequest()))
            .isInstanceOf(DownstreamServiceException.class);

        // Verify audit was NOT called — payment didn't complete
        verify(auditService, never()).record(any());
    }
}
```

---

### Principle 5 — Clean Test Data Setup

Avoid repetition with builder helpers:

```java
// Test data builders — reusable across test class
// Keep in a TestDataFactory or as private helper methods
private Account buildValidAccount() {
    return Account.builder()
        .id("ACC-001")
        .balance(1000.0)
        .status(AccountStatus.ACTIVE)
        .currency("GBP")
        .build();
}

private Account buildInactiveAccount() {
    return buildValidAccount().toBuilder()
        .status(AccountStatus.INACTIVE)
        .build();
}

private PaymentRequest buildValidRequest() {
    return PaymentRequest.builder()
        .accountId("ACC-001")
        .amount(250.0)
        .currency("GBP")
        .idempotencyKey(UUID.randomUUID().toString())
        .build();
}
```

**Avoid magic numbers — use named constants:**
```java
// BAD
when(accountRepository.findById("ACC-001"))...
assertThat(result.getAmount()).isEqualTo(250.0);

// GOOD
private static final String VALID_ACCOUNT_ID = "ACC-001";
private static final double PAYMENT_AMOUNT = 250.0;
private static final double INITIAL_BALANCE = 1000.0;
```

---

### Principle 6 — What Makes a BAD Test

Worth knowing explicitly — interviewers love this:

```java
// BAD TEST 1 — tests too many things (not one unit of behaviour)
@Test
public void testEverything() {
    // creates account, processes payment, checks audit,
    // verifies email sent, checks balance updated...
    // When this fails you have no idea what broke ❌
}

// BAD TEST 2 — hardcoded/manipulated assertions
@Test
public void testPaymentAmount() {
    PaymentResult result = paymentService.process(request);
    // Hardcoded magic number — what does 750.0 mean? ❌
    assertThat(result.getAmount()).isEqualTo(750.0);
}

// BAD TEST 3 — testing implementation not behaviour
@Test
public void testThatRepositorySaveIsCalledExactly3Times() {
    paymentService.process(request);
    verify(repository, times(3)).save(any()); // ❌ brittle
    // Any refactor that changes internal save calls
    // breaks this test even if behaviour is correct
}

// BAD TEST 4 — non-deterministic test (flaky)
@Test
public void testWithCurrentTime() {
    Payment payment = new Payment(LocalDateTime.now()); // ❌
    // Test result depends on when it runs — flaky
    // Fix: inject a Clock dependency, mock it in tests
}

// BAD TEST 5 — tests that pass regardless
@Test
public void testPaymentProcessing() {
    paymentService.process(request);
    // No assertion — always passes ❌
    // Worse than no test — gives false confidence
}
```

---

### Principle 7 — Test Coverage Strategy

Senior-level addition — what to test and what not to:

```
ALWAYS test:
├── Happy path — valid input, expected output
├── Business rule boundaries — min/max amounts, account limits
├── Each exception scenario — invalid account, insufficient funds
├── Edge cases — null inputs, empty collections, zero amounts
└── State changes — balance updated, audit recorded

DON'T test:
├── Framework code — Spring DI wiring, JPA mappings
├── Getters/setters — no business logic
├── Private methods — covered via public interface
└── Third-party libraries — trust they work
```

**Coverage target — quality over quantity:**
```
// 100% coverage is NOT the goal
// A test that covers a line but doesn't assert anything
// is worse than no test

// Aim for:
// - 100% coverage of business logic paths
// - Every exception scenario tested
// - Every boundary condition tested
// - NOT chasing line coverage %
```

---

### Principle 8 — Integration vs Unit vs E2E

Worth articulating the **testing pyramid:**

```
         /\
        /E2E\          Few — slow, expensive, test full flows
       /──────\
      /Integr- \       Some — test service + DB, service + Kafka
     / ation    \
    /────────────\
   /  Unit Tests  \    Many — fast, isolated, test business logic
  /────────────────\
```

**Unit tests:** Mock everything external, test business logic only, run in milliseconds, hundreds of them.

**Integration tests:** Spring Boot test context, real DB (H2 or Testcontainers), test repository layer, service-to-DB interaction.

```java
// Integration test example with Testcontainers
@SpringBootTest
@Testcontainers
class PaymentRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:14");

    @Test
    public void save_whenValidPayment_shouldPersistAndReturnWithId() {
        Payment payment = buildValidPayment();
        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getId()).isNotNull();
        assertThat(paymentRepository.findById(saved.getId()))
            .isPresent()
            .hasValueSatisfying(p -> 
                assertThat(p.getAmount()).isEqualTo(payment.getAmount()));
    }
}
```

**E2E tests:** Full stack, real services, test complete user journeys. Few of them, run in CI only.

---

## Polished 60-Second Interview Answer

*"My approach to unit testing starts with testing one unit of behaviour through public interfaces — private methods get covered indirectly. Every test follows the AAA pattern: Arrange sets up clean, named test data, Act calls the single unit under test, Assert verifies outcome objectively with expected vs actual — never manipulated results.*

*All external dependencies are mocked — repositories, gateways, downstream services. This keeps tests fast, focused on business logic, and independent of infrastructure. Test names follow a methodName_scenario_expectedOutcome pattern so they're self-documenting — reading the name alone tells you what broke.*

*What makes a bad test: testing too many things at once, asserting on implementation details like exact number of repository calls, non-deterministic tests using real timestamps, or tests with no assertions that give false confidence.*

*I think about coverage qualitatively — every happy path, every exception scenario, every business rule boundary. Chasing line coverage percentage without meaningful assertions is worse than fewer, well-structured tests.*

*Unit tests sit at the base of the testing pyramid — fast and numerous. Integration tests with Testcontainers cover service-to-DB interactions. E2E tests cover full user journeys in CI. Each layer has a clear purpose."*

---

That's Question 6 done. Ready for the **additional questions** — shall we start with:

**"How does HashMap work internally? What happens during resize? HashMap vs ConcurrentHashMap?"**

Excellent — genuinely strong answer. You clearly understand the internals well. Let me validate, fill the small gaps, and add the senior-level details that complete the picture.

---

## What You Nailed ✅

- Key-value pairs, hash-based bucket identification
- O(1) average, O(n) worst case bad hashing, O(log n) with RBT
- Load factor 0.75, default capacity 16, doubles on resize
- Linked list → RBT conversion at bucket size 8 (you said 16 — small correction)
- HashMap not thread safe, race conditions
- ConcurrentHashMap — segment/bucket level locking, concurrent reads on different buckets

---

## Small Corrections

### Linked List → RBT threshold is 8, not 16
```java
// Java 8 HashMap source constants
static final int TREEIFY_THRESHOLD = 8;   // LL → RBT when bucket size > 8
static final int UNTREEIFY_THRESHOLD = 6; // RBT → LL when bucket shrinks < 6
static final int MIN_TREEIFY_CAPACITY = 64; // Only treeify if total capacity >= 64
                                             // Otherwise resize instead
```

So if a bucket has >8 entries AND total map capacity is ≥64, it converts to Red-Black Tree giving O(log n) on that bucket.

---

## The Gaps Worth Adding

---

### 1. How Hashing Actually Works — hashCode() + equals() Contract

```java
// Step 1 — Java calls key.hashCode()
// Step 2 — Applies additional spreading (perturbation) to reduce collisions
static final int hash(Object key) {
    int h;
    // XOR upper 16 bits with lower 16 bits
    // Spreads high bits into low bits — reduces collisions in small maps
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}

// Step 3 — Find bucket index
int bucketIndex = hash & (capacity - 1);  // equivalent to hash % capacity
                                           // but faster (bitwise operation)
```

**The critical hashCode() + equals() contract:**
```java
// If two objects are equal, they MUST have the same hashCode
// If two objects have the same hashCode, they MAY or MAY NOT be equal

// VIOLATING this contract breaks HashMap completely
public class BrokenKey {
    private String id;

    @Override
    public boolean equals(Object o) {
        return this.id.equals(((BrokenKey)o).id);
    }
    // forgot to override hashCode! ❌
    // Two equal BrokenKey objects will land in different buckets
    // get() will never find what put() stored
}

// CORRECT implementation
public class AccountKey {
    private String accountId;
    private String currency;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountKey)) return false;
        AccountKey other = (AccountKey) o;
        return Objects.equals(accountId, other.accountId) &&
               Objects.equals(currency, other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, currency);  // consistent with equals ✅
    }
}
```

**Real-world impact:** At Lloyds, if you use custom objects as HashMap keys (composite account identifiers etc.) and forget to implement both hashCode AND equals consistently, you get silent data loss — put() stores it, get() can never find it. No exception, just null returned.

---

### 2. Resize — What Actually Happens

```
Initial state:
Capacity: 16, Load factor: 0.75
Threshold: 16 × 0.75 = 12 elements

After 12th element added → RESIZE triggers:
├── New capacity: 32 (doubled)
├── New threshold: 32 × 0.75 = 24
├── New array allocated
├── ALL existing entries rehashed
│   (bucket index = hash & (newCapacity - 1))
└── Entries redistributed across new buckets

Performance impact:
├── O(n) operation during resize — all n entries rehashed
├── In single-threaded: just slow
└── In multi-threaded HashMap: can cause INFINITE LOOP (Java 7)
    (Java 8 fixed this but still not thread safe)
```

**Pre-sizing to avoid resize:**
```java
// If you know you'll store ~1000 entries
// initialCapacity = expectedSize / loadFactor = 1000 / 0.75 ≈ 1334
Map<String, Account> accounts = new HashMap<>(1334);
// No resize will occur up to 1000 entries ✅

// Or use Guava's Maps.newHashMapWithExpectedSize()
Map<String, Account> accounts = Maps.newHashMapWithExpectedSize(1000);
```

**Real-world:** In your data pipelines at Lloyds processing large batches of transactions, pre-sizing your maps avoids costly mid-processing resizes.

---

### 3. ConcurrentHashMap — Deeper Internals

Your segment locking explanation is correct for Java 7. Java 8 changed the implementation significantly:

```
Java 7 ConcurrentHashMap:
├── Divided into 16 Segments by default
├── Each Segment is a mini HashMap with its own lock
├── Concurrency level = 16 (16 threads can write simultaneously)
└── Lock is at Segment level

Java 8 ConcurrentHashMap (current):
├── No more Segments — uses individual bucket locking
├── Uses CAS (Compare-And-Swap) for lock-free reads
├── synchronized only on individual bucket head node for writes
├── Much finer grained — N buckets = N potential concurrent writers
└── Also uses LL → RBT like HashMap (threshold 8)
```

```java
// Java 8 ConcurrentHashMap key behaviours

ConcurrentHashMap<String, Account> map = new ConcurrentHashMap<>();

// Reads — completely lock-free using volatile reads ✅
Account account = map.get(accountId);

// Writes — locks only the affected bucket
map.put(accountId, account);  // locks bucket, other buckets unaffected

// Atomic compound operations — very useful
// putIfAbsent — atomic check-then-act
map.putIfAbsent(accountId, newAccount);

// computeIfAbsent — atomic, creates only if missing
map.computeIfAbsent(accountId, id -> loadFromDatabase(id));

// compute — atomic read-modify-write
map.compute(accountId, (id, existing) -> {
    if (existing == null) return new Account(id);
    existing.incrementVersion();
    return existing;
});

// merge — atomic merge operation
map.merge(accountId, newBalance, (existing, update) -> 
    existing.getBalance() > 0 ? update : existing);
```

**Real-world use at Lloyds:** Rate limiting cache, in-memory account state cache, request deduplication map — all ConcurrentHashMap with `computeIfAbsent` for thread-safe lazy loading.

---

### 4. HashMap vs Other Map Implementations

Worth knowing the full landscape:

```java
// HashMap — general purpose, not thread safe, allows null key/value
Map<String, Account> map = new HashMap<>();

// LinkedHashMap — maintains insertion order, useful for LRU cache
Map<String, Account> ordered = new LinkedHashMap<>(16, 0.75f, true);
// accessOrder=true → maintains access order (LRU eviction pattern)

// TreeMap — sorted by key, O(log n) operations, uses RBT internally
Map<String, Account> sorted = new TreeMap<>();
// Useful when you need keys in natural or custom order

// ConcurrentHashMap — thread safe, no null keys/values
Map<String, Account> concurrent = new ConcurrentHashMap<>();

// Collections.synchronizedMap — wraps HashMap, full lock on every operation
// Avoid — worse than ConcurrentHashMap in every way
Map<String, Account> synced = Collections.synchronizedMap(new HashMap<>());
// ❌ Single lock for everything — no concurrent reads

// WeakHashMap — keys are weakly referenced, GC can collect them
// Use for: caches where entries should be GC'd when key is no longer referenced
Map<String, Account> weakMap = new WeakHashMap<>();
```

---

### 5. Common Interview Follow-Up — Why null key works in HashMap but not ConcurrentHashMap?

```java
// HashMap — allows one null key (stored in bucket 0)
map.put(null, account);  // ✅ works

// ConcurrentHashMap — null keys/values are FORBIDDEN
concurrentMap.put(null, account);  // ❌ NullPointerException

// Why? In ConcurrentHashMap, null creates ambiguity:
// map.get(key) returns null — does it mean:
// (a) key exists but value is null, or
// (b) key doesn't exist?
// In a concurrent context you can't distinguish without locking
// HashMap avoids this by being single-threaded where containsKey() suffices
```

---

## Complete Comparison Table

| Feature | HashMap | ConcurrentHashMap | LinkedHashMap | TreeMap |
|---|---|---|---|---|
| Thread safe | ❌ | ✅ | ❌ | ❌ |
| Null keys | ✅ (one) | ❌ | ✅ | ❌ |
| Order | None | None | Insertion/Access | Sorted |
| Get/Put | O(1) avg | O(1) avg | O(1) avg | O(log n) |
| Locking | None | Bucket-level | None | None |
| Use case | General | Concurrent | LRU cache | Sorted keys |

---

## Polished 60-Second Interview Answer

*"HashMap uses hashing to achieve O(1) average get/put. It calls hashCode() on the key, applies a perturbation function to spread bits, then uses modulo against current capacity to find the bucket. Collisions are handled with a linked list per bucket, which Java 8 converts to a Red-Black Tree once a bucket exceeds 8 entries — giving O(log n) on that bucket instead of O(n).*

*Default capacity is 16 with a 0.75 load factor — when 75% full it doubles capacity and rehashes all entries, an O(n) operation. For known sizes I pre-size to avoid mid-operation resizes.*

*The critical contract — if you override equals() you must override hashCode() consistently. Breaking this causes silent data loss — put() stores the entry, get() can never find it.*

*HashMap is not thread safe — concurrent writes can corrupt the structure. ConcurrentHashMap solves this with bucket-level locking in Java 8 — reads are completely lock-free using CAS, writes lock only the affected bucket. This allows true concurrent writes across different buckets. It also provides atomic compound operations like computeIfAbsent which I use extensively for thread-safe lazy loading in caches.*

*One gotcha — ConcurrentHashMap forbids null keys and values because in a concurrent context you can't distinguish between a missing key and a key mapped to null without extra locking."*

---

Ready for the next additional question:

**"Explain Java 8+ features you use regularly — streams, lambdas, Optional, CompletableFuture?"**

Great question — this is a deep-dive follow-up that many senior candidates fumble. Let me explain all four scenarios clearly.

---

## The Contract First

```java
// The fundamental contract (defined in Java Object class):
// 1. If a.equals(b) == true  → a.hashCode() MUST == b.hashCode()
// 2. If a.hashCode() == b.hashCode() → a.equals(b) MAY be true or false
//    (hash collision is allowed, equality is not guaranteed)
// 3. If !a.equals(b) → hashCodes MAY still be equal (collision)
//    but ideally should differ for performance
```

---

## Scenario 1 — Default hashCode() AND equals() (neither overridden)

```java
public class Account {
    private String accountId;
    private double balance;
    // NO equals(), NO hashCode() overridden
}
```

**Default behaviour from Object class:**
```java
// Default hashCode() — based on memory address (identity)
// Every object instance gets a unique hashCode
Account a1 = new Account("ACC-001", 1000.0);
Account a2 = new Account("ACC-001", 1000.0);

a1.hashCode();  // e.g. 12345678 (memory address based)
a2.hashCode();  // e.g. 87654321 (different object, different address)

// Default equals() — reference equality (same as ==)
a1.equals(a2);  // false — different objects in memory
a1 == a2;       // false — same result
```

**Impact on HashMap:**
```java
Map<Account, String> map = new HashMap<>();
map.put(a1, "owner-john");

// Logically same account — but different object instances
map.get(a2);  // returns NULL ❌

// Why?
// a1.hashCode() != a2.hashCode() → different buckets
// map.get(a2) looks in wrong bucket → finds nothing
// Even if by chance same bucket (collision),
// a1.equals(a2) == false → not considered same key

// Works correctly ONLY with same reference
map.get(a1);  // returns "owner-john" ✅
```

**When is this acceptable?**
```java
// When object IDENTITY is intentionally the key
// i.e. you want each object instance to be a unique key
// regardless of field values
// Example: tracking which specific thread objects are running tasks
Map<Thread, TaskContext> threadContexts = new HashMap<>();
// Each Thread instance is unique — default behaviour is correct here ✅
```

---

## Scenario 2 — equals() Overridden BUT hashCode() NOT

```java
public class Account {
    private String accountId;
    private double balance;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        Account other = (Account) o;
        return Objects.equals(accountId, other.accountId);
    }
    // hashCode() NOT overridden ❌ — still uses memory address
}
```

**What happens:**
```java
Account a1 = new Account("ACC-001", 1000.0);
Account a2 = new Account("ACC-001", 1000.0);

// equals works correctly now
a1.equals(a2);  // true ✅ — same accountId

// But hashCode is still memory address based
a1.hashCode();  // 12345678
a2.hashCode();  // 87654321 — different!
```

**Impact on HashMap — SILENT DATA LOSS:**
```java
Map<Account, String> map = new HashMap<>();
map.put(a1, "owner-john");

// Step 1: put(a1) → hashCode(a1) = 12345678 → bucket 5
// Step 2: get(a2) → hashCode(a2) = 87654321 → bucket 11
// Different buckets → map never even checks equals()
// Returns null even though a1.equals(a2) is true ❌

map.get(a2);  // NULL — completely broken ❌
map.containsKey(a2);  // false — broken ❌

// Even worse — you can put duplicate logical keys
map.put(a1, "owner-john");
map.put(a2, "owner-jane");  // goes to different bucket!
map.size();  // 2 — but logically should be 1 ❌
```

**This is the most dangerous scenario** — no exception thrown, no warning. Code compiles and runs but silently produces wrong results. Extremely hard to debug in production.

---

## Scenario 3 — hashCode() Overridden BUT equals() NOT

```java
public class Account {
    private String accountId;
    private double balance;

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }
    // equals() NOT overridden ❌ — still uses reference equality
}
```

**What happens:**
```java
Account a1 = new Account("ACC-001", 1000.0);
Account a2 = new Account("ACC-001", 1000.0);

// hashCode now consistent
a1.hashCode();  // e.g. 67890 — based on accountId
a2.hashCode();  // e.g. 67890 — same! ✅

// But equals still reference based
a1.equals(a2);  // false ❌ — different objects
```

**Impact on HashMap:**
```java
Map<Account, String> map = new HashMap<>();
map.put(a1, "owner-john");

// Step 1: put(a1) → hashCode = 67890 → bucket 5
// Step 2: get(a2) → hashCode = 67890 → bucket 5 ✅ same bucket!
// Step 3: checks equals() → a1.equals(a2) = false ❌
// Treats them as different keys — returns null

map.get(a2);  // NULL ❌

// Duplicate logical keys again
map.put(a1, "owner-john");
map.put(a2, "owner-jane");
map.size();  // 2 — but in SAME bucket (collision)
             // Stored as linked list nodes in same bucket
             // Logical duplicates ❌
```

**Performance impact:**
```java
// If all your keys have same hashCode (intentionally or bug)
// All entries land in same bucket
// HashMap degrades to O(n) linked list search
// Java 8 converts to RBT at 8 — O(log n)
// But still far from O(1) you expect

// Classic interview trick question:
// "What if all keys return hashCode() = 1?"
public int hashCode() { return 1; }  // worst possible implementation
// Every entry in same bucket
// HashMap becomes a linked list — O(n) for everything ❌
```

---

## Scenario 4 — Both Correctly Overridden ✅

```java
public class Account {
    private String accountId;
    private double balance;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        Account other = (Account) o;
        return Objects.equals(accountId, other.accountId);
    }

    @Override
    public int hashCode() {
        // Must use SAME fields as equals() — accountId only
        return Objects.hash(accountId);
    }
}
```

**Everything works correctly:**
```java
Account a1 = new Account("ACC-001", 1000.0);
Account a2 = new Account("ACC-001", 2000.0);  // different balance

a1.hashCode() == a2.hashCode();  // true ✅ — same bucket
a1.equals(a2);                   // true ✅ — same accountId

Map<Account, String> map = new HashMap<>();
map.put(a1, "owner-john");
map.get(a2);         // "owner-john" ✅
map.size();          // 1 ✅ — correctly identified as same key

// put with a2 OVERWRITES a1's entry — correct behaviour
map.put(a2, "owner-jane");
map.get(a1);         // "owner-jane" ✅ — updated
map.size();          // still 1 ✅
```

---

## Complete Summary Table

| Scenario | hashCode | equals | Same Bucket? | Found in Map? | Risk |
|---|---|---|---|---|---|
| Neither overridden | Memory address | Reference (==) | ❌ Usually not | ❌ Only same ref | Acceptable if identity is key |
| Only equals | Memory address | Field-based | ❌ Usually not | ❌ Never | 🔴 Silent data loss |
| Only hashCode | Field-based | Reference (==) | ✅ Yes | ❌ Never | 🟡 Duplicates, O(n) |
| Both correctly | Field-based | Field-based | ✅ Yes | ✅ Always | ✅ Correct |

---

## The Immutability Gotcha — Mutable Keys

One more senior-level point worth knowing:

```java
// DANGEROUS — mutable object as HashMap key
Account account = new Account("ACC-001", 1000.0);
Map<Account, String> map = new HashMap<>();
map.put(account, "owner-john");

// Now mutate the key AFTER putting it in map
account.setAccountId("ACC-999");  // hashCode changes!

// HashMap looks in new bucket (based on ACC-999)
// Entry is still in old bucket (based on ACC-001)
map.get(account);  // NULL ❌ — lost in the map!

// The entry still EXISTS in the map — just unreachable
// Memory leak + silent data loss ❌
```

**Fix — always use immutable objects as HashMap keys:**
```java
// String, Integer, Long — all immutable — perfect keys ✅
Map<String, Account> map = new HashMap<>();  // accountId as key

// If custom key needed — make it immutable
public final class AccountKey {
    private final String accountId;  // final fields
    private final String currency;

    // constructor only, no setters
    // equals() and hashCode() on final fields ✅
}
```

---

## Polished 30-Second Interview Answer

*"The default hashCode uses memory address — every object instance is unique. Default equals is reference equality — same as ==. So by default two objects with identical fields are treated as completely different keys in a HashMap.*

*The most dangerous scenario is overriding equals but not hashCode — two logically equal objects get different hashCodes, land in different buckets, and the map never finds the entry. No exception, just silent null returns and duplicate key insertions — extremely hard to debug.*

*Overriding hashCode but not equals gets objects into the same bucket but equals still fails — same result, plus performance degrades as collisions accumulate.*

*Both must be overridden using the same fields, and those fields should be immutable. Mutating a key after insertion changes its hashCode — the entry becomes permanently unreachable, causing memory leaks and data loss."*

---

Ready for the next question: **"Explain Java 8+ features you use regularly — streams, lambdas, Optional, CompletableFuture?"**

Outstanding answer — you've covered the core of all three concepts well. Let me validate, correct one small thing, fill the gaps, and add the senior-level detail that completes the picture.

---

## Small Correction — Parallel Streams vs Regular Streams

Regular streams are **not** Fork Join pool — that's only parallel streams:

```java
// Regular stream — runs on CALLING thread, single threaded
list.stream()
    .filter(...)
    .collect(...);  // no thread pool involved

// Parallel stream — uses ForkJoinPool.commonPool()
list.parallelStream()
    .filter(...)
    .collect(...);  // splits work across ForkJoin threads
```

---

## What You Nailed ✅

- Lambdas enable functional programming — stateless, same input same output
- FunctionalInterface with single abstract method
- Consumer, Supplier, Function, BiFunction
- Streams are lazy — nothing executes until terminal operation
- filter, map, collect, findFirst
- CompletableFuture — chaining, async, reactive pipelines, fixes Future gaps

---

## The Complete Picture

---

## 1. Lambdas & Functional Interfaces — Deep Dive

```java
// FunctionalInterface — exactly one abstract method
// Can have default and static methods — still qualifies

@FunctionalInterface
public interface PaymentValidator {
    boolean validate(Payment payment);  // single abstract method
    
    // default method allowed
    default PaymentValidator and(PaymentValidator other) {
        return payment -> this.validate(payment) && other.validate(payment);
    }
}
```

**Core built-in Functional Interfaces:**
```java
// Function<T, R> — takes T, returns R
Function<String, Account> loader = accountId -> accountRepo.load(accountId);
Account account = loader.apply("ACC-001");

// BiFunction<T, U, R> — takes T and U, returns R
BiFunction<Account, Double, Boolean> canWithdraw = 
    (account, amount) -> account.getBalance() >= amount;

// Consumer<T> — takes T, returns nothing (side effect)
Consumer<Payment> auditor = payment -> auditLog.record(payment);
auditor.accept(payment);

// Supplier<T> — takes nothing, returns T (lazy evaluation)
Supplier<Account> defaultAccount = () -> Account.defaultAccount();
Account account = defaultAccount.get();

// Predicate<T> — takes T, returns boolean
Predicate<Account> isActive = account -> account.getStatus() == ACTIVE;
Predicate<Account> hasBalance = account -> account.getBalance() > 0;

// Composing predicates — very useful
Predicate<Account> eligibleForLoan = isActive.and(hasBalance);
Predicate<Account> needsReview = isActive.and(hasBalance.negate());

// UnaryOperator<T> — Function where T in = T out
UnaryOperator<Double> applyTax = amount -> amount * 1.20;

// BinaryOperator<T> — BiFunction where all types are T
BinaryOperator<Double> addAmounts = (a, b) -> a + b;
```

**Method references — cleaner lambda syntax:**
```java
// Instead of lambda
list.stream().map(account -> account.getId())
// Use method reference
list.stream().map(Account::getId)          // instance method ref

list.stream().map(account -> new DTO(account))
list.stream().map(DTO::new)               // constructor ref

list.stream().filter(s -> String.isEmpty(s))
list.stream().filter(String::isEmpty)     // static method ref

list.forEach(s -> System.out.println(s))
list.forEach(System.out::println)         // instance method ref on instance
```

---

## 2. Streams — Complete Deep Dive

### Lazy Evaluation — How It Actually Works

```java
List<Account> accounts = getMillionAccounts();

// This entire pipeline is LAZY
// Nothing executes until collect() is called
Stream<String> pipeline = accounts.stream()          // no work yet
    .filter(a -> a.getBalance() > 1000)              // no work yet
    .filter(a -> a.getStatus() == ACTIVE)            // no work yet
    .map(Account::getId);                            // no work yet

// Terminal operation triggers entire pipeline
List<String> result = pipeline.collect(toList());   // NOW it executes

// Key insight — short circuit operations stop early
Optional<Account> first = accounts.stream()
    .filter(a -> a.getBalance() > 1000)
    // Stops as soon as first match found
    // Doesn't process remaining million accounts ✅
    .findFirst();
```

### Intermediate vs Terminal Operations

```java
// INTERMEDIATE — lazy, return Stream, can be chained
.filter()       // Predicate<T> → keeps matching elements
.map()          // Function<T,R> → transforms each element
.flatMap()      // Function<T, Stream<R>> → flattens nested streams
.distinct()     // removes duplicates (uses equals/hashCode)
.sorted()       // sorts (natural or Comparator)
.peek()         // for debugging — sees elements without consuming
.limit(n)       // takes first n elements
.skip(n)        // skips first n elements

// TERMINAL — eager, triggers execution, returns non-Stream
.collect()      // accumulates into collection/map/string
.forEach()      // side effect on each element
.findFirst()    // first matching Optional<T>
.findAny()      // any matching (better for parallel)
.anyMatch()     // boolean — any element matches predicate
.allMatch()     // boolean — all elements match
.noneMatch()    // boolean — none match
.count()        // long — count of elements
.reduce()       // fold into single value
.min() .max()   // Optional<T>
```

### flatMap — The Tricky One

```java
// map gives you Stream<List<Transaction>>
List<Stream<Transaction>> nested = accounts.stream()
    .map(Account::getTransactions)  // each account has List<Transaction>
    .collect(toList());

// flatMap flattens to Stream<Transaction>
List<Transaction> allTransactions = accounts.stream()
    .flatMap(account -> account.getTransactions().stream())
    .collect(toList());

// Real world — get all unique currencies across all accounts
Set<String> currencies = accounts.stream()
    .flatMap(account -> account.getTransactions().stream())
    .map(Transaction::getCurrency)
    .collect(toSet());
```

### Collectors — Beyond Basic toList()

```java
// Group accounts by status
Map<AccountStatus, List<Account>> byStatus = accounts.stream()
    .collect(Collectors.groupingBy(Account::getStatus));

// Count by status
Map<AccountStatus, Long> countByStatus = accounts.stream()
    .collect(Collectors.groupingBy(
        Account::getStatus,
        Collectors.counting()
    ));

// Sum balance by currency
Map<String, Double> balanceByCurrency = accounts.stream()
    .collect(Collectors.groupingBy(
        Account::getCurrency,
        Collectors.summingDouble(Account::getBalance)
    ));

// Partition into two groups (true/false)
Map<Boolean, List<Account>> partitioned = accounts.stream()
    .collect(Collectors.partitioningBy(
        account -> account.getBalance() > 10000
    ));
// partitioned.get(true)  → high value accounts
// partitioned.get(false) → standard accounts

// Join to string
String accountIds = accounts.stream()
    .map(Account::getId)
    .collect(Collectors.joining(", ", "[", "]"));
// "[ACC-001, ACC-002, ACC-003]"

// toMap — be careful with duplicate keys
Map<String, Account> accountMap = accounts.stream()
    .collect(Collectors.toMap(
        Account::getId,           // key extractor
        Function.identity(),      // value extractor
        (existing, replacement) -> existing  // merge function for duplicates
    ));
```

### Parallel Streams — When to Use and When NOT To

```java
// Parallel stream — splits work across ForkJoinPool.commonPool()
long count = accounts.parallelStream()
    .filter(a -> a.getBalance() > 1000)
    .count();

// ForkJoinPool default size = number of CPU cores - 1
// Custom pool for parallel streams (avoid starving common pool)
ForkJoinPool customPool = new ForkJoinPool(4);
customPool.submit(() ->
    accounts.parallelStream()
        .filter(a -> a.getBalance() > 1000)
        .collect(toList())
).get();
```

**When parallel streams HELP:**
```java
// ✅ Large datasets (100k+ elements)
// ✅ CPU-intensive operations (complex calculations)
// ✅ Independent, stateless operations
// ✅ No ordering requirement (use findAny not findFirst)
```

**When parallel streams HURT:**
```java
// ❌ Small collections — thread overhead > benefit
// ❌ I/O bound operations — use CompletableFuture instead
// ❌ Stateful operations — shared mutable state = race conditions
// ❌ Ordered operations — merging results kills parallelism benefit
// ❌ In web servers — steals from ForkJoinPool.commonPool()
//    starving other parallel operations

// Real-world: at Lloyds for large batch reconciliation
// parallel stream for CPU-heavy calculations ✅
// For DB/API calls — CompletableFuture with custom executor ✅
```

---

## 3. Optional — Eliminating NullPointerException

You didn't mention this — worth adding:

```java
// Instead of null checks everywhere
Account account = accountRepo.findById(id);
if (account != null) {
    if (account.getAddress() != null) {
        String city = account.getAddress().getCity();
    }
}

// Optional — explicit API that forces null handling
Optional<Account> optAccount = accountRepo.findById(id);

// map — transform if present
String city = optAccount
    .map(Account::getAddress)
    .map(Address::getCity)
    .orElse("Unknown");  // default if any step is empty

// orElseThrow — throw if absent
Account account = optAccount
    .orElseThrow(() -> new InvalidAccountException(id));

// ifPresent — side effect if present
optAccount.ifPresent(a -> auditLog.record(a));

// filter — keep only if condition met
optAccount
    .filter(a -> a.getStatus() == ACTIVE)
    .orElseThrow(() -> new AccountInactiveException(id));
```

**Key rules:**
```java
// ✅ Use Optional as method return type to signal possible absence
public Optional<Account> findById(String id) { ... }

// ❌ Never use Optional as field type
private Optional<String> name;  // just use null for fields

// ❌ Never use Optional as method parameter
public void process(Optional<Account> account)  // just use @Nullable

// ❌ Never call .get() without checking isPresent() first
optAccount.get();  // throws NoSuchElementException if empty ❌
```

---

## 4. CompletableFuture — Complete Deep Dive

Your point about fixing Future's gaps is correct. Let's show exactly what was broken:

```java
// Old Future — problems
Future<Account> future = executor.submit(() -> fetchAccount(id));

future.get();           // BLOCKS calling thread ❌
future.get(5, SECONDS); // Timeout but still blocks ❌
// No way to chain operations
// No way to combine multiple futures declaratively
// No built-in exception handling
// Cannot be manually completed
```

**CompletableFuture — solutions:**
```java
// 1. Basic async execution
CompletableFuture<Account> accountFuture = CompletableFuture
    .supplyAsync(() -> fetchAccount(id), executor);  // non-blocking ✅

// 2. Chaining — thenApply (sync transform)
CompletableFuture<AccountDTO> dtoFuture = accountFuture
    .thenApply(account -> new AccountDTO(account));  // transforms result

// 3. thenCompose — async chaining (flatMap equivalent)
CompletableFuture<Balance> balanceFuture = accountFuture
    .thenComposeAsync(
        account -> fetchBalance(account.getId()),
        executor
    );

// 4. Combining independent futures — parallel execution
CompletableFuture<Account> accountFuture = 
    CompletableFuture.supplyAsync(() -> fetchAccount(id));
CompletableFuture<RateLimit> rateFuture = 
    CompletableFuture.supplyAsync(() -> fetchRateLimit(id));

// Both run in PARALLEL — combine when both complete
CompletableFuture<PaymentContext> context = accountFuture
    .thenCombineAsync(rateFuture,
        (account, rateLimit) -> new PaymentContext(account, rateLimit),
        executor
    );

// 5. Wait for ALL futures
CompletableFuture.allOf(accountFuture, rateFuture, configFuture)
    .thenRun(() -> System.out.println("All done"));

// 6. First to complete wins
CompletableFuture.anyOf(primaryFuture, fallbackFuture)
    .thenAccept(result -> process(result));

// 7. Exception handling
accountFuture
    .exceptionally(ex -> {
        log.error("Failed to fetch account", ex);
        return Account.defaultAccount();  // fallback value
    });

// 8. Handle — both success and failure in one
accountFuture
    .handle((account, ex) -> {
        if (ex != null) {
            log.error("Error", ex);
            return Account.defaultAccount();
        }
        return account;
    });

// 9. whenComplete — side effect, doesn't change result
accountFuture
    .whenComplete((account, ex) -> {
        metrics.recordLatency(System.currentTimeMillis() - startTime);
    });
```

**Real-world pattern from your Lloyds platform:**
```java
// Parallel downstream calls — fetch account, rate limit, and config simultaneously
// Instead of sequential calls (300ms + 200ms + 150ms = 650ms)
// Parallel: max(300, 200, 150) = 300ms ✅

public PaymentContext buildPaymentContext(String accountId) {
    CompletableFuture<Account> accountFuture = CompletableFuture
        .supplyAsync(() -> accountService.fetch(accountId), executor);

    CompletableFuture<RateLimit> rateFuture = CompletableFuture
        .supplyAsync(() -> rateLimitService.fetch(accountId), executor);

    CompletableFuture<PaymentConfig> configFuture = CompletableFuture
        .supplyAsync(() -> configService.fetch(), executor);

    return CompletableFuture
        .allOf(accountFuture, rateFuture, configFuture)
        .thenApply(v -> new PaymentContext(
            accountFuture.join(),   // join() safe here — already complete
            rateFuture.join(),
            configFuture.join()
        ))
        .orTimeout(5, TimeUnit.SECONDS)  // Java 9+ — timeout
        .exceptionally(ex -> PaymentContext.defaultContext())
        .join();
}
```

---

## Complete Summary

```
Java 8+ Features:

Lambdas
├── Inline functional implementation of FunctionalInterface
├── Stateless — same input, same output
├── Types: Function, Consumer, Supplier, Predicate, BiFunction
└── Method references — cleaner syntax

Streams
├── Lazy — nothing executes until terminal operation
├── Intermediate: filter, map, flatMap, sorted, distinct, peek
├── Terminal: collect, forEach, findFirst, reduce, count
├── Collectors: toList, toMap, groupingBy, partitioningBy, joining
└── Parallel: ForkJoinPool, use for CPU-bound large datasets only

Optional
├── Explicit null handling — forces caller to handle absence
├── map, flatMap, filter, orElse, orElseThrow, ifPresent
└── Return type only — not fields or parameters

CompletableFuture
├── Non-blocking async execution
├── Chaining: thenApply, thenCompose, thenCombine
├── Parallel: allOf, anyOf
├── Error handling: exceptionally, handle, whenComplete
└── Real-world: parallel downstream service calls
```

---

## Polished 90-Second Interview Answer

*"Java 8 introduced functional programming through lambdas and FunctionalInterfaces — interfaces with a single abstract method. This lets you write stateless inline behaviour — same input always produces same output. The built-in types cover most needs: Function for transformation, Predicate for filtering, Consumer for side effects, Supplier for lazy evaluation. Method references make this even cleaner.*

*Streams build on this for declarative collection processing. They're fundamentally lazy — the pipeline defines what to do but nothing executes until a terminal operation like collect or findFirst is attached. This enables short-circuit optimisation — findFirst stops at the first match, not after processing everything. Common operations are filter with Predicate, map for transformation, flatMap for flattening nested structures, and Collectors for rich aggregation — groupingBy, partitioningBy, joining. Parallel streams use ForkJoinPool but I use them selectively — only for CPU-intensive, stateless operations on large datasets. For I/O bound work, CompletableFuture with a dedicated executor is the right tool.*

*Optional makes absence explicit in APIs — forces callers to handle the missing case rather than getting surprise NullPointerExceptions. I use it exclusively as return types, never as field or parameter types.*

*CompletableFuture fixed Future's fundamental gap — blocking on get(). It enables truly async, non-blocking pipelines. The real power is combining futures — at Lloyds I used allOf to fire parallel calls to account service, rate limit service, and config service simultaneously, reducing latency from sequential 650ms to parallel 300ms. Exception handling with exceptionally and handle keeps the pipeline clean without try-catch blocks."*

---

Ready for the next additional question: **"What is the difference between checked and unchecked exceptions? When to use each?"**

Great — let me deep dive on both with rich examples.

---

# Streams — map, collect to Map, sorted, Comparators

---

## 1. map — Transformation Deep Dive

```java
// Sample domain objects
public class Account {
    private String id;
    private String ownerName;
    private double balance;
    private String currency;
    private AccountStatus status;
    private LocalDate openedDate;
    private List<Transaction> transactions;
}

public class Transaction {
    private String id;
    private double amount;
    private String currency;
    private LocalDateTime timestamp;
    private TransactionType type;
}
```

### Basic map — one type to another
```java
List<Account> accounts = fetchAccounts();

// map Account → String (extract field)
List<String> accountIds = accounts.stream()
    .map(Account::getId)           // method reference
    .collect(Collectors.toList());

// map Account → DTO (transform object)
List<AccountDTO> dtos = accounts.stream()
    .map(account -> new AccountDTO(
        account.getId(),
        account.getOwnerName(),
        account.getBalance()
    ))
    .collect(Collectors.toList());

// map with transformation logic
List<Double> balancesInUSD = accounts.stream()
    .map(account -> {
        if (account.getCurrency().equals("GBP")) {
            return account.getBalance() * 1.27;  // GBP to USD
        }
        return account.getBalance();
    })
    .collect(Collectors.toList());
```

### Chaining multiple maps
```java
// Chain maps — each transforms further
List<String> upperCaseOwners = accounts.stream()
    .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
    .map(Account::getOwnerName)          // Account → String
    .map(String::trim)                    // remove whitespace
    .map(String::toUpperCase)            // uppercase
    .distinct()                          // remove duplicates
    .collect(Collectors.toList());
```

### mapToInt, mapToDouble, mapToLong — primitive streams
```java
// Avoids boxing/unboxing overhead for numerical operations
// Returns IntStream, DoubleStream, LongStream — not Stream<Integer>

// Sum all balances
double totalBalance = accounts.stream()
    .mapToDouble(Account::getBalance)
    .sum();  // available on DoubleStream directly ✅

// Average balance
OptionalDouble avgBalance = accounts.stream()
    .mapToDouble(Account::getBalance)
    .average();

// Statistics in one pass
DoubleSummaryStatistics stats = accounts.stream()
    .mapToDouble(Account::getBalance)
    .summaryStatistics();

stats.getSum();    // total
stats.getAverage(); // average
stats.getMin();    // minimum
stats.getMax();    // maximum
stats.getCount();  // count
```

### flatMap — flattening nested structures
```java
// Each account has List<Transaction>
// map gives Stream<List<Transaction>> — nested
// flatMap gives Stream<Transaction> — flat

// Get ALL transactions across ALL accounts
List<Transaction> allTransactions = accounts.stream()
    .flatMap(account -> account.getTransactions().stream())
    .collect(Collectors.toList());

// Get all unique currencies across all transactions
Set<String> currencies = accounts.stream()
    .flatMap(account -> account.getTransactions().stream())
    .map(Transaction::getCurrency)
    .collect(Collectors.toSet());

// Get all high-value transactions across all accounts
List<Transaction> highValue = accounts.stream()
    .flatMap(account -> account.getTransactions().stream())
    .filter(txn -> txn.getAmount() > 10000)
    .collect(Collectors.toList());

// Real world — flatten optional results
List<String> optionalIds = accounts.stream()
    .map(account -> findPrimaryTransaction(account))  // returns Optional<String>
    .filter(Optional::isPresent)
    .map(Optional::get)
    .collect(Collectors.toList());

// Cleaner with flatMap on Optional
List<String> optionalIds = accounts.stream()
    .flatMap(account -> findPrimaryTransaction(account).stream())
    .collect(Collectors.toList());
```

---

## 2. Collecting to Map — Complete Guide

### Basic toMap
```java
// Simple: Account → Map<id, account>
Map<String, Account> accountMap = accounts.stream()
    .collect(Collectors.toMap(
        Account::getId,          // key extractor
        Function.identity()      // value extractor (same object)
    ));

// Custom value
Map<String, Double> balanceMap = accounts.stream()
    .collect(Collectors.toMap(
        Account::getId,          // key
        Account::getBalance      // value — just the balance
    ));

// Transform both key and value
Map<String, String> idToOwner = accounts.stream()
    .collect(Collectors.toMap(
        account -> account.getId().toLowerCase(),  // key transform
        account -> account.getOwnerName().trim()   // value transform
    ));
```

### Handling duplicate keys — merge function
```java
// If two accounts have same currency — which balance to keep?
// Without merge function → IllegalStateException on duplicate key ❌

// With merge function — sum balances for same currency
Map<String, Double> balanceByCurrency = accounts.stream()
    .collect(Collectors.toMap(
        Account::getCurrency,                    // key — may duplicate
        Account::getBalance,                     // value
        (existingBalance, newBalance) ->         // merge function
            existingBalance + newBalance         // sum on collision
    ));

// Keep the higher balance account per currency
Map<String, Account> highestBalancePerCurrency = accounts.stream()
    .collect(Collectors.toMap(
        Account::getCurrency,
        Function.identity(),
        (existing, replacement) ->
            existing.getBalance() > replacement.getBalance()
                ? existing : replacement
    ));

// Keep first encountered — ignore duplicates
Map<String, Account> firstPerCurrency = accounts.stream()
    .collect(Collectors.toMap(
        Account::getCurrency,
        Function.identity(),
        (existing, replacement) -> existing  // always keep first
    ));
```

### Controlling Map implementation type
```java
// Default toMap returns HashMap
// 4th parameter controls the Map type

// LinkedHashMap — preserves insertion order
Map<String, Account> ordered = accounts.stream()
    .collect(Collectors.toMap(
        Account::getId,
        Function.identity(),
        (e, r) -> e,           // merge — keep existing
        LinkedHashMap::new     // map factory
    ));

// TreeMap — sorted by key
Map<String, Account> sorted = accounts.stream()
    .collect(Collectors.toMap(
        Account::getId,
        Function.identity(),
        (e, r) -> e,
        TreeMap::new           // naturally sorted by key
    ));
```

### groupingBy — most powerful collector
```java
// Group accounts by status
Map<AccountStatus, List<Account>> byStatus = accounts.stream()
    .collect(Collectors.groupingBy(Account::getStatus));
// {ACTIVE: [acc1, acc3], INACTIVE: [acc2], PENDING: [acc4]}

// Group with downstream collector — count
Map<AccountStatus, Long> countByStatus = accounts.stream()
    .collect(Collectors.groupingBy(
        Account::getStatus,
        Collectors.counting()
    ));
// {ACTIVE: 150, INACTIVE: 30, PENDING: 20}

// Group with downstream — sum balance
Map<String, Double> totalByCurrency = accounts.stream()
    .collect(Collectors.groupingBy(
        Account::getCurrency,
        Collectors.summingDouble(Account::getBalance)
    ));

// Group with downstream — average
Map<String, Double> avgByCurrency = accounts.stream()
    .collect(Collectors.groupingBy(
        Account::getCurrency,
        Collectors.averagingDouble(Account::getBalance)
    ));

// Group with downstream — collect to set instead of list
Map<AccountStatus, Set<String>> idsByStatus = accounts.stream()
    .collect(Collectors.groupingBy(
        Account::getStatus,
        Collectors.mapping(Account::getId, Collectors.toSet())
    ));

// Multi-level grouping — group by currency then by status
Map<String, Map<AccountStatus, List<Account>>> byCurrencyThenStatus =
    accounts.stream()
        .collect(Collectors.groupingBy(
            Account::getCurrency,
            Collectors.groupingBy(Account::getStatus)
        ));

// Group with custom map type — TreeMap for sorted keys
Map<String, List<Account>> sortedByCurrency = accounts.stream()
    .collect(Collectors.groupingBy(
        Account::getCurrency,
        TreeMap::new,
        Collectors.toList()
    ));
```

### partitioningBy — splits into true/false
```java
// Simple partition
Map<Boolean, List<Account>> partitioned = accounts.stream()
    .collect(Collectors.partitioningBy(
        account -> account.getBalance() > 10000
    ));

List<Account> highValue = partitioned.get(true);
List<Account> standard = partitioned.get(false);

// Partition with downstream collector
Map<Boolean, Long> partitionCount = accounts.stream()
    .collect(Collectors.partitioningBy(
        account -> account.getBalance() > 10000,
        Collectors.counting()
    ));
// {true: 45, false: 155}
```

---

## 3. sorted and Comparators — Complete Guide

### Basic sorting
```java
// Natural order — requires Comparable on the type
List<String> sortedIds = accounts.stream()
    .map(Account::getId)
    .sorted()                    // String natural order (alphabetical)
    .collect(Collectors.toList());

// Reverse natural order
List<String> reversedIds = accounts.stream()
    .map(Account::getId)
    .sorted(Comparator.reverseOrder())
    .collect(Collectors.toList());
```

### Comparator — single field
```java
// Sort accounts by balance ascending
List<Account> byBalance = accounts.stream()
    .sorted(Comparator.comparingDouble(Account::getBalance))
    .collect(Collectors.toList());

// Sort by balance descending
List<Account> byBalanceDesc = accounts.stream()
    .sorted(Comparator.comparingDouble(Account::getBalance).reversed())
    .collect(Collectors.toList());

// Sort by name alphabetically
List<Account> byName = accounts.stream()
    .sorted(Comparator.comparing(Account::getOwnerName))
    .collect(Collectors.toList());

// Sort by date
List<Account> byDate = accounts.stream()
    .sorted(Comparator.comparing(Account::getOpenedDate))
    .collect(Collectors.toList());
```

### Comparator — multiple fields (thenComparing)
```java
// Sort by currency first, then by balance descending within currency
List<Account> multiSort = accounts.stream()
    .sorted(
        Comparator.comparing(Account::getCurrency)          // primary sort
            .thenComparingDouble(Account::getBalance)       // secondary sort
            .reversed()                                     // reverse entire chain
    )
    .collect(Collectors.toList());

// More explicit multi-level sort
List<Account> explicit = accounts.stream()
    .sorted(
        Comparator.comparing(Account::getCurrency)                    // asc
            .thenComparing(
                Comparator.comparingDouble(Account::getBalance)
                    .reversed()                                        // desc
            )
            .thenComparing(Account::getOwnerName)                     // asc
    )
    .collect(Collectors.toList());
```

### Handling nulls in sort
```java
// NullPointerException if ownerName can be null
// nullsFirst — null values come first
List<Account> nullsFirst = accounts.stream()
    .sorted(Comparator.comparing(
        Account::getOwnerName,
        Comparator.nullsFirst(Comparator.naturalOrder())
    ))
    .collect(Collectors.toList());

// nullsLast — null values come last
List<Account> nullsLast = accounts.stream()
    .sorted(Comparator.comparing(
        Account::getOwnerName,
        Comparator.nullsLast(Comparator.naturalOrder())
    ))
    .collect(Collectors.toList());
```

### Custom Comparator logic
```java
// Sort by custom business rule — ACTIVE first, then PENDING, then INACTIVE
Map<AccountStatus, Integer> statusOrder = Map.of(
    AccountStatus.ACTIVE, 1,
    AccountStatus.PENDING, 2,
    AccountStatus.INACTIVE, 3
);

List<Account> byCustomStatus = accounts.stream()
    .sorted(Comparator.comparingInt(
        account -> statusOrder.get(account.getStatus())
    ))
    .collect(Collectors.toList());

// Sort transactions by amount desc, then timestamp asc
List<Transaction> sortedTxns = transactions.stream()
    .sorted(
        Comparator.comparingDouble(Transaction::getAmount).reversed()
            .thenComparing(Transaction::getTimestamp)
    )
    .collect(Collectors.toList());
```

### min and max using Comparator
```java
// Highest balance account
Optional<Account> richest = accounts.stream()
    .max(Comparator.comparingDouble(Account::getBalance));

// Most recent transaction
Optional<Transaction> latest = transactions.stream()
    .max(Comparator.comparing(Transaction::getTimestamp));

// Earliest account opened
Optional<Account> oldest = accounts.stream()
    .min(Comparator.comparing(Account::getOpenedDate));
```

---

# CompletableFuture — Every Operation Explained

---

## 1. Creation Methods

```java
// supplyAsync — async, returns a value
// Uses ForkJoinPool.commonPool() if no executor provided
CompletableFuture<Account> future = CompletableFuture
    .supplyAsync(() -> fetchAccount("ACC-001"));

// supplyAsync with custom executor — ALWAYS use this in production
ExecutorService executor = Executors.newFixedThreadPool(10);
CompletableFuture<Account> future = CompletableFuture
    .supplyAsync(() -> fetchAccount("ACC-001"), executor);
// Why custom executor? ForkJoinPool.commonPool() is shared across JVM
// Other parallel operations compete for same threads — unpredictable latency

// runAsync — async, no return value (fire and forget)
CompletableFuture<Void> future = CompletableFuture
    .runAsync(() -> auditLog.record(event), executor);

// completedFuture — already complete, useful for testing/defaults
CompletableFuture<Account> ready = CompletableFuture
    .completedFuture(Account.defaultAccount());

// failedFuture — already failed, useful for testing
CompletableFuture<Account> failed = CompletableFuture
    .failedFuture(new InvalidAccountException("ACC-001"));
```

---

## 2. Transformation Operations

### thenApply — sync transform (like map on Stream)
```java
// Runs on same thread that completed the previous stage
// Use for: cheap, non-blocking transformations

CompletableFuture<AccountDTO> dto = CompletableFuture
    .supplyAsync(() -> fetchAccount("ACC-001"), executor)
    .thenApply(account -> new AccountDTO(account))    // sync transform
    .thenApply(dto -> enrichWithMetadata(dto));       // chain another transform

// thenApplyAsync — transform on different thread
CompletableFuture<AccountDTO> dto = CompletableFuture
    .supplyAsync(() -> fetchAccount("ACC-001"), executor)
    .thenApplyAsync(account -> heavyTransform(account), executor);
// Use thenApplyAsync when transform itself is expensive
```

### thenCompose — async chaining (like flatMap on Stream)
```java
// Use when next step is ALSO async (returns CompletableFuture)
// thenApply would give CompletableFuture<CompletableFuture<Balance>> — nested!
// thenCompose flattens to CompletableFuture<Balance>

// WRONG — gives nested future
CompletableFuture<CompletableFuture<Balance>> wrong = accountFuture
    .thenApply(account -> fetchBalance(account.getId()));  // fetchBalance is async

// CORRECT — flat future
CompletableFuture<Balance> correct = accountFuture
    .thenCompose(account -> fetchBalance(account.getId()));  // ✅

// Real world — sequential async calls
CompletableFuture<PaymentResult> result = CompletableFuture
    .supplyAsync(() -> fetchAccount(accountId), executor)
    .thenCompose(account -> validateAccount(account))     // async validation
    .thenCompose(account -> fetchRateLimit(account))      // async rate check
    .thenCompose(context -> executePayment(context));     // async execution
```

### thenAccept — consume result, no return (like Consumer)
```java
CompletableFuture<Void> done = CompletableFuture
    .supplyAsync(() -> fetchAccount("ACC-001"), executor)
    .thenAccept(account -> {
        auditLog.record(account);     // side effect, no return
        metrics.increment("accounts.fetched");
    });

// thenAcceptAsync — consume on different thread
.thenAcceptAsync(account -> heavySideEffect(account), executor)
```

### thenRun — run after completion, no access to result
```java
CompletableFuture<Void> done = CompletableFuture
    .supplyAsync(() -> processPayment(request), executor)
    .thenRun(() -> {
        // No access to payment result — just runs after completion
        log.info("Payment processing pipeline complete");
        metrics.increment("payments.processed");
    });
```

---

## 3. Combining Multiple Futures

### thenCombine — combine TWO independent futures
```java
// Both run in PARALLEL — combine results when BOTH complete
CompletableFuture<Account> accountFuture =
    CompletableFuture.supplyAsync(() -> fetchAccount(id), executor);

CompletableFuture<RateLimit> rateFuture =
    CompletableFuture.supplyAsync(() -> fetchRateLimit(id), executor);

// Combine — BiFunction receives both results
CompletableFuture<PaymentContext> contextFuture = accountFuture
    .thenCombine(rateFuture,
        (account, rateLimit) -> new PaymentContext(account, rateLimit)
    );

// thenCombineAsync — combine on different thread
accountFuture.thenCombineAsync(rateFuture,
    (account, rate) -> new PaymentContext(account, rate),
    executor
);
```

### allOf — wait for ALL futures
```java
CompletableFuture<Account> accountFuture =
    CompletableFuture.supplyAsync(() -> fetchAccount(id), executor);
CompletableFuture<RateLimit> rateFuture =
    CompletableFuture.supplyAsync(() -> fetchRateLimit(id), executor);
CompletableFuture<Config> configFuture =
    CompletableFuture.supplyAsync(() -> fetchConfig(), executor);

// allOf returns CompletableFuture<Void> — no result aggregation
// Need to join() individual futures after allOf completes
CompletableFuture<PaymentContext> context = CompletableFuture
    .allOf(accountFuture, rateFuture, configFuture)
    .thenApply(v -> new PaymentContext(
        accountFuture.join(),   // safe — already complete
        rateFuture.join(),
        configFuture.join()
    ));

// Pattern for collecting list of futures
List<CompletableFuture<Account>> futures = accountIds.stream()
    .map(id -> CompletableFuture.supplyAsync(
        () -> fetchAccount(id), executor))
    .collect(Collectors.toList());

// Wait for all, collect results
CompletableFuture<List<Account>> allAccounts = CompletableFuture
    .allOf(futures.toArray(new CompletableFuture[0]))
    .thenApply(v -> futures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toList())
    );
```

### anyOf — first to complete wins
```java
// Try primary and fallback simultaneously — use whichever responds first
CompletableFuture<Account> primary =
    CompletableFuture.supplyAsync(() -> fetchFromPrimary(id), executor);
CompletableFuture<Account> fallback =
    CompletableFuture.supplyAsync(() -> fetchFromCache(id), executor);

// anyOf returns CompletableFuture<Object> — need to cast
CompletableFuture<Account> fastest = CompletableFuture
    .anyOf(primary, fallback)
    .thenApply(result -> (Account) result);
```

---

## 4. Exception Handling — Every Method

```java
CompletableFuture<Account> accountFuture = CompletableFuture
    .supplyAsync(() -> {
        if (invalidId) throw new InvalidAccountException(id);
        return fetchAccount(id);
    }, executor);
```

### exceptionally — handle error, provide fallback
```java
// Only called if exception occurred — like catch block
// Returns same type as original future
CompletableFuture<Account> withFallback = accountFuture
    .exceptionally(ex -> {
        log.error("Failed to fetch account: {}", id, ex);
        return Account.defaultAccount();  // fallback value ✅
    });

// Can also rethrow different exception
.exceptionally(ex -> {
    if (ex.getCause() instanceof InvalidAccountException) {
        throw new RuntimeException(new BusinessException("Account invalid"));
    }
    return Account.defaultAccount();
});
```

### handle — handles BOTH success and failure
```java
// Always called — regardless of success or failure
// Like try-catch-finally combined
CompletableFuture<AccountDTO> handled = accountFuture
    .handle((account, ex) -> {
        if (ex != null) {
            // Error path
            log.error("Error fetching account", ex);
            return AccountDTO.defaultDTO();
        }
        // Success path
        return new AccountDTO(account);
    });

// Difference from exceptionally:
// exceptionally — only on failure, returns same type
// handle — always, can return different type
```

### whenComplete — side effect only, doesn't change result
```java
// Called regardless of success/failure
// CANNOT change the result — only side effects
// Original result or exception propagates unchanged
CompletableFuture<Account> withMetrics = accountFuture
    .whenComplete((account, ex) -> {
        long duration = System.currentTimeMillis() - startTime;
        metrics.recordLatency("fetchAccount", duration);

        if (ex != null) {
            metrics.increment("fetchAccount.errors");
        } else {
            metrics.increment("fetchAccount.success");
        }
        // Cannot return anything — result passes through unchanged
    });
```

### exceptionallyCompose — Java 12+ async fallback
```java
// Like exceptionally but fallback is also async
CompletableFuture<Account> withAsyncFallback = accountFuture
    .exceptionallyCompose(ex -> {
        log.warn("Primary fetch failed, trying cache", ex);
        return CompletableFuture
            .supplyAsync(() -> fetchFromCache(id), executor);
    });
```

---

## 5. Key Differences — thenApply vs thenCompose vs thenCombine

```
thenApply — TRANSFORM result synchronously
─────────────────────────────────────────
Future<A> → (A → B) → Future<B>
Use when: transformation is cheap, non-async
Example: Account → AccountDTO

thenCompose — CHAIN another async operation (flatMap)
─────────────────────────────────────────────────────
Future<A> → (A → Future<B>) → Future<B>
Use when: next step is also async
Example: Account → fetchBalance(account) → Future<Balance>

thenCombine — COMBINE two INDEPENDENT futures
─────────────────────────────────────────────
Future<A> + Future<B> → (A, B → C) → Future<C>
Use when: two operations run in parallel, combine results
Example: fetchAccount() + fetchRateLimit() → PaymentContext
```

---

## 6. Complete Real-World Pattern

```java
@Service
public class PaymentOrchestrator {

    // Sequential async — each step depends on previous
    public CompletableFuture<PaymentResult> processPayment(
            PaymentRequest request) {

        return CompletableFuture
            // Step 1 — fetch account async
            .supplyAsync(() -> accountService.fetch(
                request.getAccountId()), executor)

            // Step 2 — validate (depends on account)
            .thenCompose(account ->
                validationService.validate(account, request))

            // Step 3 — fetch rate limit in PARALLEL with step 2
            // Note: thenCombine used to merge
            .thenCombine(
                CompletableFuture.supplyAsync(() ->
                    rateLimitService.check(request.getAccountId()), executor),
                (validatedAccount, rateLimit) ->
                    new PaymentContext(validatedAccount, rateLimit)
            )

            // Step 4 — execute payment
            .thenComposeAsync(context ->
                paymentGateway.execute(context, request), executor)

            // Step 5 — record metrics regardless of outcome
            .whenComplete((result, ex) -> {
                metrics.record("payment.processed",
                    ex == null ? "success" : "failure");
            })

            // Step 6 — handle failure gracefully
            .exceptionally(ex -> {
                log.error("Payment failed for request: {}",
                    request.getId(), ex);
                return PaymentResult.failed(ex.getMessage());
            })

            // Step 7 — timeout after 10 seconds
            .orTimeout(10, TimeUnit.SECONDS);
    }
}
```

---

## Summary — When to Use What

```
Creation:
├── supplyAsync(supplier, executor) — async with return value
├── runAsync(runnable, executor)    — async, no return
└── completedFuture(value)         — already done (testing/defaults)

Transform:
├── thenApply    — sync transform (A → B), cheap operations
├── thenApplyAsync — async transform on different thread
└── thenCompose  — next step is also async (A → Future<B>)

Consume:
├── thenAccept   — side effect with result access
└── thenRun      — side effect without result access

Combine:
├── thenCombine  — two parallel futures → one result
├── allOf        — wait for ALL futures (no result — need join())
└── anyOf        — first to complete wins

Error Handling:
├── exceptionally      — catch, provide fallback value
├── handle             — always called, success + failure, can change type
├── whenComplete       — always called, side effect only, result unchanged
└── exceptionallyCompose — async fallback (Java 12+)
```

---

Ready for the next question: **"What is the difference between checked and unchecked exceptions? When to use each?"**