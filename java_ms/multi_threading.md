## Concurrency — Complete Guide

Let me structure this properly — theory first, then code, then GS-specific questions.

---

## The core problem concurrency solves

```
Single thread:
  read counter(0) → increment → write counter(1) ✓

Two threads, no sync:
  Thread1: read counter(0)
  Thread2: read counter(0)  ← both read 0
  Thread1: write counter(1)
  Thread2: write counter(1) ← overwrites Thread1!
  
  Expected: 2
  Actual:   1  ← lost update
```

---

## Three problems to solve

```
1. Atomicity   — operation completes fully or not at all
2. Visibility  — thread sees latest value written by another
3. Ordering    — instructions don't get reordered unexpectedly
```

---

## Tool 1 — synchronized

### On method

```java
class Counter {
    private int count = 0;

    // only one thread can execute this at a time
    public synchronized void increment() {
        count++;  // read + increment + write — atomic
    }

    public synchronized int get() {
        return count;
    }
}
```

### On block — finer grained

```java
class Counter {
    private int count = 0;
    private final Object lock = new Object();

    public void increment() {
        // only lock what needs protection
        synchronized (lock) {
            count++;
        }
        // other code runs without lock
    }
}
```

### What synchronized gives you

```
1. Mutual exclusion — only one thread in block at a time
2. Memory visibility — unlock flushes to main memory
                       lock reads fresh from main memory
3. Happens-before — everything before unlock() is visible
                     after lock() by another thread
```

### Limitations of synchronized

```
1. No timeout — thread waits forever for lock
2. No tryLock — can't attempt without blocking
3. Not interruptible — can't cancel waiting thread
4. Single condition — only one wait/notify queue
5. Coarse — locks entire object
```

---

## Tool 2 — ReentrantLock

Solves all synchronized limitations:

```java
class OrderBook {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    public void addOrder(Order order) {
        lock.lock();
        try {
            orders.add(order);
            notEmpty.signal();  // notify waiting consumers
        } finally {
            lock.unlock();  // ALWAYS in finally
        }
    }

    // tryLock — don't wait if locked
    public boolean tryAddOrder(Order order) {
        if (lock.tryLock()) {
            try {
                orders.add(order);
                return true;
            } finally {
                lock.unlock();
            }
        }
        return false;  // couldn't acquire lock, didn't wait
    }

    // tryLock with timeout
    public boolean tryAddOrderWithTimeout(Order order) 
            throws InterruptedException {
        if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
            try {
                orders.add(order);
                return true;
            } finally {
                lock.unlock();
            }
        }
        return false;
    }
}
```

### ReentrantLock — why "reentrant"?

```java
public synchronized void methodA() {
    methodB();  // calls another synchronized method
}

public synchronized void methodB() {
    // same thread already holds lock
    // reentrant = can acquire same lock again
    // non-reentrant would deadlock here!
}
```

Same thread can acquire the same lock multiple times — tracks hold count:

```
Thread1: lock() → holdCount=1
Thread1: lock() → holdCount=2  (reentrant)
Thread1: unlock() → holdCount=1
Thread1: unlock() → holdCount=0 → released
```

### Fairness

```java
// fair lock — threads acquire in order they requested
ReentrantLock fairLock = new ReentrantLock(true);

// unfair lock (default) — any waiting thread can jump queue
ReentrantLock unfairLock = new ReentrantLock(false);
```

```
Fair:   lower throughput, no starvation
Unfair: higher throughput, possible starvation
        (one thread could wait very long)

For trading systems — unfair usually preferred (throughput)
For fair resource allocation — fair lock
```

### Condition variables — multiple wait queues

```java
class BoundedOrderQueue {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final Queue<Order> queue = new LinkedList<>();
    private final int capacity;

    public void put(Order order) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() >= capacity) {
                notFull.await();  // wait until not full
            }
            queue.offer(order);
            notEmpty.signal();    // wake one consumer
        } finally {
            lock.unlock();
        }
    }

    public Order take() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();  // wait until not empty
            }
            Order order = queue.poll();
            notFull.signal();     // wake one producer
            return order;
        } finally {
            lock.unlock();
        }
    }
}
```

Two conditions — producers wait on notFull, consumers wait on notEmpty. They don't wake each other unnecessarily.

With synchronized you only have one wait queue — notify() might wake wrong thread.

---

## Tool 3 — volatile

```java
class MarketDataFeed {
    private volatile boolean running = true;  // ← volatile
    private volatile double lastPrice = 0.0;  // ← volatile

    public void start() {
        while (running) {          // reads fresh value every iteration
            updatePrice();
        }
    }

    public void stop() {
        running = false;           // visible to all threads immediately
    }

    public double getPrice() {
        return lastPrice;          // always reads from main memory
    }
}
```

### What volatile gives you

```
1. Visibility  — write immediately visible to all threads
2. No caching  — never reads from CPU cache
3. Ordering    — no instruction reordering around volatile read/write

Does NOT give:
✗ Atomicity — counter++ is still NOT atomic even if volatile
```

### volatile vs synchronized

```
volatile:
  lightweight — no lock
  only fixes visibility
  single variable
  read/write must be atomic already (long/double need synchronized)

synchronized:
  heavyweight — acquires lock
  fixes visibility + atomicity + ordering
  protects code block
```

### When to use volatile

```
✓ Simple flag — running = false
✓ Single writer, multiple readers
✓ Double-checked locking pattern
✗ Never for compound operations (check-then-act, counter++)
```

---

## Tool 4 — Atomic classes

```java
// AtomicInteger — lock-free thread-safe integer
AtomicInteger counter = new AtomicInteger(0);

counter.incrementAndGet()     // atomic ++
counter.decrementAndGet()     // atomic --
counter.addAndGet(5)          // atomic += 5
counter.get()                 // read
counter.set(10)               // write
counter.compareAndSet(10, 20) // CAS — if value==10, set to 20

// AtomicLong — same for long
AtomicLong orderCount = new AtomicLong(0);

// AtomicBoolean
AtomicBoolean initialized = new AtomicBoolean(false);
initialized.compareAndSet(false, true)  // atomic check and set

// AtomicReference — for objects
AtomicReference<Order> currentOrder = new AtomicReference<>();
currentOrder.compareAndSet(null, newOrder)
```

### How CAS works internally

```
compareAndSet(expected, newValue):

  CPU instruction: CMPXCHG (compare and exchange)
  
  atomically:
    if current == expected:
        current = newValue
        return true   ← won the race
    else:
        return false  ← lost the race, retry
```

No lock — CPU guarantees atomicity at hardware level.

### CAS loop pattern

```java
AtomicInteger counter = new AtomicInteger(0);

// lock-free increment
public void increment() {
    while (true) {
        int current = counter.get();
        int next    = current + 1;
        if (counter.compareAndSet(current, next)) {
            return;  // succeeded
        }
        // failed — another thread changed value — retry
    }
}

// same as above — built in
counter.incrementAndGet();
```

### ABA problem with CAS

```
Thread1: reads value A
Thread2: changes A → B → A  (back to A)
Thread1: CAS sees A == A → succeeds
         but value WAS changed — Thread1 doesn't know

For counters — not a problem (A→B→A means two increments)
For references — can be a problem

Fix: AtomicStampedReference — adds version number
AtomicStampedReference<Order> ref = new AtomicStampedReference<>(order, 0);
// CAS checks both value AND stamp (version)
```

---

## Tool 5 — ReadWriteLock

```java
class PriceCache {
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock  = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final Map<String, Double> prices = new HashMap<>();

    public Double getPrice(String symbol) {
        readLock.lock();   // multiple readers allowed simultaneously
        try {
            return prices.get(symbol);
        } finally {
            readLock.unlock();
        }
    }

    public void updatePrice(String symbol, double price) {
        writeLock.lock();  // exclusive — blocks all readers and writers
        try {
            prices.put(symbol, price);
        } finally {
            writeLock.unlock();
        }
    }
}
```

### When ReadWriteLock beats synchronized

```
synchronized:
  read1 and read2 → sequential (unnecessary)

ReadWriteLock:
  read1 and read2 → parallel ✓
  read and write  → sequential ✓
  write and write → sequential ✓

Perfect for read-heavy, write-rare scenarios
→ market data cache — many readers, few price updates
```

---

## Deadlock

### How it happens

```
Thread1 holds LockA, wants LockB
Thread2 holds LockB, wants LockA
→ both wait forever → deadlock
```

### Four conditions for deadlock

```
1. Mutual exclusion  — resource held exclusively
2. Hold and wait     — holding one, waiting for another
3. No preemption     — lock can't be forcibly taken
4. Circular wait     — T1 waits for T2, T2 waits for T1
```

### Prevention — consistent lock ordering

```java
// WRONG — can deadlock
Thread1: lock(accountA) then lock(accountB)
Thread2: lock(accountB) then lock(accountA)

// RIGHT — always lock in same order
void transfer(Account from, Account to, double amount) {
    Account first  = from.id < to.id ? from : to;
    Account second = from.id < to.id ? to : from;
    
    synchronized(first) {
        synchronized(second) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

### Prevention — tryLock with timeout

```java
// never wait forever
if (lock1.tryLock(100, TimeUnit.MILLISECONDS)) {
    try {
        if (lock2.tryLock(100, TimeUnit.MILLISECONDS)) {
            try {
                // do work
            } finally {
                lock2.unlock();
            }
        }
    } finally {
        lock1.unlock();
    }
}
// if either lock fails → retry or abort
```

---

## Thread pools

```java
// fixed thread pool — N threads always alive
ExecutorService fixed = Executors.newFixedThreadPool(10);

// cached thread pool — grows as needed, shrinks when idle
ExecutorService cached = Executors.newCachedThreadPool();

// single thread — sequential execution
ExecutorService single = Executors.newSingleThreadExecutor();

// scheduled — run after delay or periodically
ScheduledExecutorService scheduler = 
    Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS);

// submit task
Future<Double> future = fixed.submit(() -> getPrice("AAPL"));
Double price = future.get();  // blocks until done

// shutdown gracefully
fixed.shutdown();                          // no new tasks
fixed.awaitTermination(5, TimeUnit.SECONDS); // wait for running tasks
fixed.shutdownNow();                       // interrupt running tasks
```

### Which pool for which use case

```
CPU-bound tasks (calculations):
→ fixedThreadPool(Runtime.getRuntime().availableProcessors())
→ no more threads than CPUs — context switching overhead

IO-bound tasks (network, DB):
→ cachedThreadPool or larger fixedThreadPool
→ threads spend time waiting — more threads = better throughput

Scheduled tasks (cleanup, heartbeat):
→ scheduledThreadPool
```

---

## GS Q&A — common interview questions

### Q1 — What's wrong with this code?

```java
class Counter {
    private int count = 0;
    
    public void increment() { count++; }
    public int get() { return count; }
}
```

```
Three problems:
1. count++ not atomic — read + increment + write
   → lost updates under concurrency
2. No visibility — changes may not be seen by other threads
3. No ordering — compiler may reorder instructions

Fix options:
→ synchronized on both methods
→ AtomicInteger
→ volatile (fixes visibility but not atomicity of ++)
```

### Q2 — Double-checked locking for Singleton

```java
class TradingEngine {
    private static volatile TradingEngine instance;  // volatile!

    private TradingEngine() {}

    public static TradingEngine getInstance() {
        if (instance == null) {              // first check — no lock
            synchronized (TradingEngine.class) {
                if (instance == null) {      // second check — with lock
                    instance = new TradingEngine();
                }
            }
        }
        return instance;
    }
}
```

Why volatile here?

```
instance = new TradingEngine() has 3 steps:
1. allocate memory
2. initialize object
3. assign reference to instance

Without volatile — steps can reorder to 1→3→2:
Thread1: allocates memory, assigns reference (step 3 before 2)
Thread2: sees instance != null → returns uninitialized object!

volatile prevents reordering → always 1→2→3
```

### Q3 — What is ThreadLocal?

```java
// each thread gets its own copy of variable
ThreadLocal<SimpleDateFormat> dateFormat = 
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

// Thread1 gets its own SimpleDateFormat
// Thread2 gets its own SimpleDateFormat
// no sharing → no synchronization needed

// common use case — DB connection per thread
ThreadLocal<Connection> connection = new ThreadLocal<>();
```

---

## Summary table

| Tool | Atomicity | Visibility | Use when |
|---|---|---|---|
| `synchronized` | ✓ | ✓ | Simple mutual exclusion |
| `ReentrantLock` | ✓ | ✓ | Need tryLock, timeout, conditions |
| `volatile` | ✗ | ✓ | Simple flag, single writer |
| `AtomicInteger` | ✓ | ✓ | Lock-free counter |
| `ReadWriteLock` | ✓ | ✓ | Read-heavy, rare writes |
| `ThreadLocal` | n/a | n/a | Per-thread state |

---

Ready for Q&A on this like we did for Collections?

## Concurrency Q&A — GS Style

One question at a time, exactly how GS asks.

---

### Q1

You're building a **trade counter** at GS — tracks total number of trades executed today. 500 trading threads all increment this counter simultaneously throughout the day.

```java
class TradeCounter {
    private int totalTrades = 0;
    
    public void recordTrade() {
        totalTrades++;
    }
    
    public int getTotal() {
        return totalTrades;
    }
}
```

Three questions:

1. What's wrong with this code?
2. Give me three different ways to fix it
3. Which would you use in production and why?


Strong answer — you identified the problems correctly. Let me push deeper on each fix since your answers were brief.

---

## Problems — you got them ✅

```
1. count++ not atomic — read + increment + write
   Thread1 and Thread2 both read 0 → both write 1 → lost update

2. Visibility — Thread1 writes totalTrades
                Thread2 may read stale value from CPU cache

3. Ordering — compiler/CPU may reorder instructions around totalTrades
```

---

## Now explain each fix properly

---

### Fix 1 — volatile + synchronized (you mentioned this)

Why does volatile alone not fix it?

```java
private volatile int totalTrades = 0;

public void recordTrade() {
    totalTrades++;  // still not atomic!
    // volatile fixes visibility — every thread sees latest value
    // but ++ is still 3 steps: read → increment → write
    // two threads can still both read 5 and both write 6
}
```

So you need synchronized too:

```java
private volatile int totalTrades = 0;

public synchronized void recordTrade() {
    totalTrades++;  // now atomic — only one thread at a time
}

public int getTotal() {
    return totalTrades;  // volatile read — no lock needed
}
```

Actually volatile is redundant here — synchronized already gives visibility. Cleaner:

```java
public synchronized void recordTrade() { totalTrades++; }
public synchronized int getTotal()     { return totalTrades; }
```

**Problem with synchronized:**
```
500 threads all want to increment
→ all queue up for single lock
→ sequential bottleneck
→ throughput limited
```

---

### Fix 2 — AtomicInteger (your second answer)

```java
class TradeCounter {
    private final AtomicInteger totalTrades = new AtomicInteger(0);

    public void recordTrade() {
        totalTrades.incrementAndGet();  // lock-free, atomic CAS
    }

    public int getTotal() {
        return totalTrades.get();  // lock-free read
    }
}
```

Why better than synchronized?

```
synchronized:
  500 threads → 499 waiting → sequential
  one lock → one thread at a time

AtomicInteger:
  uses CPU CAS instruction — hardware level atomic
  no OS lock — no thread blocking
  500 threads → all attempt CAS simultaneously
  losers retry immediately — no blocking
  → much higher throughput
```

When does AtomicInteger lose?

```
Very high contention — 500 threads all retrying CAS
→ lots of failed CAS → lots of retries → CPU spinning
→ LongAdder is better here
```

**LongAdder — mention this at VP level:**

```java
private final LongAdder totalTrades = new LongAdder();

public void recordTrade() {
    totalTrades.increment();  // each thread has its own cell
}

public long getTotal() {
    return totalTrades.sum();  // sums all cells
}
```

```
LongAdder internally:
  maintains separate counter per CPU/thread (cells)
  increment() → updates thread's own cell → no contention
  sum() → adds all cells → O(cells)

500 threads → 500 cells → zero contention
→ highest throughput for counter under high load
```

---

### Fix 3 — ReadWriteLock (your third answer)

```java
class TradeCounter {
    private int totalTrades = 0;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock  = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public void recordTrade() {
        writeLock.lock();    // exclusive write
        try {
            totalTrades++;
        } finally {
            writeLock.unlock();
        }
    }

    public int getTotal() {
        readLock.lock();     // parallel reads allowed
        try {
            return totalTrades;
        } finally {
            readLock.unlock();
        }
    }
}
```

Is ReadWriteLock right here?

```
ReadWriteLock shines when:
  reads >> writes
  Example: 1000 reads per second, 10 writes per second

TradeCounter reality:
  recordTrade() called 500 times simultaneously
  getTotal() called occasionally for reporting

→ writes dominate → ReadWriteLock gives no benefit
→ write lock is still exclusive → same bottleneck as synchronized
→ WRONG choice for this use case
```

---

## Which to use in production — the real VP answer

```
Simple counter, low contention  → synchronized (simplest)
High contention counter         → AtomicInteger
Very high contention (500+ threads) → LongAdder
Read-heavy, rare writes         → ReadWriteLock
```

For 500 trading threads:

*"AtomicInteger for moderate contention — lock-free CAS avoids thread blocking. If contention is extreme, LongAdder — maintains per-thread cells so threads never contend. ReadWriteLock is wrong here because writes dominate. synchronized is simplest but creates a bottleneck with 500 threads all queuing for one lock."*

---

## Q2

Two trading desks transfer funds between accounts simultaneously:

```java
class Account {
    String id;
    double balance;
    
    void transfer(Account to, double amount) {
        synchronized(this) {
            synchronized(to) {
                this.balance -= amount;
                to.balance   += amount;
            }
        }
    }
}

Account deskA = new Account("A", 10000);
Account deskB = new Account("B", 10000);

// Thread1
deskA.transfer(deskB, 500);

// Thread2
deskB.transfer(deskA, 300);
```

What problem can occur and how do you fix it?

Exactly right on the problem. Let me explain the fix.

---

## Why it deadlocks

```
Thread1: deskA.transfer(deskB, 500)
  → locks deskA
  → wants deskB

Thread2: deskB.transfer(deskA, 300)
  → locks deskB
  → wants deskA

Thread1 holds A, waiting for B
Thread2 holds B, waiting for A
→ both wait forever → deadlock
```

---

## Fix 1 — Consistent lock ordering

Break circular wait by always locking in same order — lower ID first:

```java
void transfer(Account to, double amount) {
    Account first  = this.id.compareTo(to.id) < 0 ? this : to;
    Account second = this.id.compareTo(to.id) < 0 ? to : this;

    synchronized(first) {
        synchronized(second) {
            this.balance -= amount;
            to.balance   += amount;
        }
    }
}
```

Now:
```
Thread1: deskA.transfer(deskB)
  first=A, second=B → locks A then B

Thread2: deskB.transfer(deskA)
  first=A, second=B → locks A then B ← same order!

Thread1 holds A → Thread2 waits for A
Thread1 acquires B → completes → releases both
Thread2 acquires A then B → completes
→ no deadlock ✓
```

---

## Fix 2 — tryLock with timeout

```java
void transfer(Account to, double amount) 
        throws InterruptedException {
    while (true) {
        if (this.lock.tryLock(50, TimeUnit.MILLISECONDS)) {
            try {
                if (to.lock.tryLock(50, TimeUnit.MILLISECONDS)) {
                    try {
                        this.balance -= amount;
                        to.balance   += amount;
                        return;  // success
                    } finally {
                        to.lock.unlock();
                    }
                }
                // couldn't get second lock
                // first lock released in finally
            } finally {
                this.lock.unlock();
            }
        }
        // both locks released — retry after small backoff
        Thread.sleep(10);  // backoff before retry
    }
}
```

If can't get both locks within timeout — release first lock and retry. No circular wait possible.

---

## Fix 3 — Single global lock (simplest)

```java
private static final Object TRANSFER_LOCK = new Object();

void transfer(Account to, double amount) {
    synchronized(TRANSFER_LOCK) {  // one global lock for all transfers
        this.balance -= amount;
        to.balance   += amount;
    }
}
```

No deadlock — only one lock. But:
```
All transfers sequential → bottleneck
deskA→deskB and deskC→deskD can't happen in parallel
→ only acceptable for low throughput
```

---

## Which fix to recommend

```
Fix 1 — consistent ordering:
  ✓ No deadlock
  ✓ High throughput — unrelated accounts parallel
  ✓ No retry overhead
  → Best for production

Fix 2 — tryLock:
  ✓ No deadlock
  ✓ Flexible timeout
  ✗ Retry overhead — livelock possible if both keep retrying
  → Good when lock ordering not possible

Fix 3 — global lock:
  ✓ Simplest
  ✗ All transfers sequential
  → Only for low throughput
```

*"Consistent lock ordering is the cleanest fix — always lock lower account ID first. This breaks circular wait without retry overhead. tryLock is a good alternative when you can't control lock ordering, but adds retry complexity and risks livelock."*

---

## Q3

You have a market data service:

```java
class MarketDataService {
    private boolean marketOpen = false;

    public void openMarket()  { marketOpen = true; }
    public void closeMarket() { marketOpen = false; }

    public void processOrder(Order order) {
        if (marketOpen) {
            // process order
        }
    }
}
```

Three threads — one opens market, one closes market, many process orders.

What are the concurrency issues and how do you fix them?

No problem — let me break it down step by step.

---

## Step 1 — spot the problems one by one

### Problem 1 — Visibility

```java
private boolean marketOpen = false;
```

`marketOpen` is a plain boolean — not volatile, not synchronized.

```
Thread1 (openMarket):   marketOpen = true
                        → written to CPU1 cache
                        → NOT flushed to main memory yet

Thread2 (processOrder): reads marketOpen
                        → reads from CPU2 cache
                        → still sees false!
                        → processes nothing even though market is open
```

Thread2 never sees Thread1's write. This is the **visibility problem.**

---

### Problem 2 — Check-then-act race condition

```java
public void processOrder(Order order) {
    if (marketOpen) {           // CHECK
                                // ← Thread2 closes market HERE
        // process order        // ACT
    }
}
```

```
Thread1: checks marketOpen → true ✓
Thread2: closeMarket() → marketOpen = false
Thread1: processes order — but market is now closed!
```

Check and act are two separate steps — not atomic.

---

### Problem 3 — No atomicity on state change

```java
public void openMarket()  { marketOpen = true; }
public void closeMarket() { marketOpen = false; }
```

What if openMarket() and closeMarket() called simultaneously?

```
Thread1: openMarket()  → writes true
Thread2: closeMarket() → writes false
→ final state unpredictable
```

---

## Step 2 — fixes

### Fix 1 — volatile for visibility

```java
private volatile boolean marketOpen = false;
```

Fixes visibility — every thread reads from main memory always. But does NOT fix check-then-act race condition.

---

### Fix 2 — synchronized for atomicity + visibility

```java
class MarketDataService {
    private boolean marketOpen = false;

    public synchronized void openMarket() {
        marketOpen = true;
    }

    public synchronized void closeMarket() {
        marketOpen = false;
    }

    public synchronized void processOrder(Order order) {
        if (marketOpen) {
            // check and act atomic — no thread can change
            // marketOpen between check and act
        }
    }
}
```

Fixes everything — but:

```
processOrder() called thousands of times/sec
synchronized → all sequential → bottleneck
```

---

### Fix 3 — ReadWriteLock (best for this case)

```java
class MarketDataService {
    private boolean marketOpen = false;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock  = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public void openMarket() {
        writeLock.lock();       // exclusive — blocks all reads
        try {
            marketOpen = true;
        } finally {
            writeLock.unlock();
        }
    }

    public void closeMarket() {
        writeLock.lock();       // exclusive
        try {
            marketOpen = false;
        } finally {
            writeLock.unlock();
        }
    }

    public void processOrder(Order order) {
        readLock.lock();        // shared — multiple threads parallel
        try {
            if (marketOpen) {
                // process — marketOpen can't change while read lock held
            }
        } finally {
            readLock.unlock();
        }
    }
}
```

Why ReadWriteLock is right here:

```
openMarket/closeMarket → rare (twice a day)
processOrder          → thousands per second

reads >> writes → ReadWriteLock shines
→ thousands of processOrder() run in parallel ✓
→ openMarket/closeMarket get exclusive access ✓
→ no race condition between check and act ✓
```

---

### Fix 4 — AtomicBoolean (simplest)

```java
class MarketDataService {
    private final AtomicBoolean marketOpen = new AtomicBoolean(false);

    public void openMarket()  { marketOpen.set(true); }
    public void closeMarket() { marketOpen.set(false); }

    public void processOrder(Order order) {
        if (marketOpen.get()) {
            // still has check-then-act issue
            // but visibility is fixed
        }
    }
}
```

Fixes visibility — but check-then-act race still exists. If strict atomicity needed → use ReadWriteLock.

---

## When is check-then-act ok?

```
If processing an order when market JUST closed is acceptable:
→ AtomicBoolean is fine
→ eventual consistency — tiny window of stale read ok

If processing after close is a compliance violation:
→ ReadWriteLock — hold read lock for entire check + process
→ market can't close while order being checked/processed
```

For GS — **ReadWriteLock.** Compliance is non-negotiable.

---

## Full VP answer

*"Three issues — visibility (plain boolean not flushed to main memory), check-then-act race (market closes between check and act), and concurrent state changes. volatile fixes visibility but not atomicity. synchronized fixes everything but serializes thousands of processOrder() calls. ReadWriteLock is optimal — processOrder() holds read lock so multiple orders process in parallel, openMarket/closeMarket acquire write lock exclusively. Market state can't change mid-processing — critical for compliance."*

---

## Q4

```java
class OrderProcessor {
    private static OrderProcessor instance;

    private OrderProcessor() {}

    public static OrderProcessor getInstance() {
        if (instance == null) {
            instance = new OrderProcessor();
        }
        return instance;
    }
}
```

What's wrong and how do you fix it?

Excellent questions — all three are deep and important. Let me answer each one properly.

---

## Question 1 — Would Thread2 eventually see the value without volatile?

**Short answer — yes eventually, but unpredictably.**

```
Thread1 writes marketOpen = true to CPU1 cache
Thread1 cache flushes to main memory:
  → on context switch
  → on synchronized block exit
  → on thread sleep
  → periodically by JVM (unpredictable timing)

Thread2 reads marketOpen:
  → may read stale cache for milliseconds or seconds
  → JVM gives NO guarantee WHEN it sees the update
```

For market data — milliseconds of stale read = orders processed after market closed = compliance breach.

**volatile removes the uncertainty:**

```
volatile write → immediately flushes to main memory
volatile read  → always reads from main memory
→ Thread2 sees Thread1's write instantly
→ no waiting for cache flush
```

---

## Question 2 — ReadWriteLock, what if write lock requested while read lock held?

Great catch — this is exactly how ReadWriteLock works:

```
Thread1: processOrder() → acquires readLock ✓
Thread2: processOrder() → acquires readLock ✓  (parallel reads allowed)
Thread3: closeMarket()  → requests writeLock
          → BLOCKS until ALL read locks released
          → waits for Thread1 and Thread2 to finish

Thread1 finishes → releases readLock
Thread2 finishes → releases readLock
Thread3: writeLock acquired → marketOpen = false
```

This is exactly what you want:

```
Any in-progress orders COMPLETE before market closes
Market can't close mid-order-processing
→ compliance safe ✓
```

What about new orders while write lock waiting?

```
Thread3 waiting for writeLock
Thread4: processOrder() → requests readLock

Two behaviors depending on fairness:
  fair=true:  Thread4 WAITS behind Thread3 (write lock has priority)
  fair=false: Thread4 MAY acquire readLock before Thread3

For GS — fair=true:
  new ReentrantReadWriteLock(true)
  write lock gets priority → market closes promptly
  no new orders sneak in after close requested
```

---

## Question 3 — synchronized on each method doesn't give global sync?

You're absolutely right — and this is a subtle but critical point.

```java
public synchronized void openMarket() {
    marketOpen = true;
}

public synchronized void processOrder(Order order) {
    if (marketOpen) {
        // process
    }
}
```

Each method locks on `this` — but they're separate lock acquisitions:

```
Thread1: openMarket() → acquires this lock → sets true → releases lock
                                              ← Thread2 can enter here
Thread2: processOrder() → acquires this lock → checks marketOpen
```

Actually — synchronized on instance methods DOES give global sync between them because they all lock on the SAME object (`this`):

```
Thread1 in openMarket()   → holds lock on 'this'
Thread2 in processOrder() → wants lock on 'this' → BLOCKED

They CAN'T run simultaneously
→ openMarket and processOrder ARE mutually exclusive ✓
```

But your instinct is right for a different reason:

```java
// if methods lock on DIFFERENT objects — not mutually exclusive
public void openMarket() {
    synchronized(lockA) { marketOpen = true; }
}

public void processOrder() {
    synchronized(lockB) { if (marketOpen) {...} }
}
// lockA != lockB → NOT mutually exclusive → race condition
```

And with ReadWriteLock — they DO share the same lock:

```java
private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
// ALL methods use rwLock → mutually exclusive when needed ✓
```

---

## Q4 — Your answer is correct ✅

You identified both problems:

```
1. Multiple threads call getInstance() simultaneously
2. All see instance == null → all create new object
3. Multiple instances created → Singleton violated
```

And both fixes:

---

### Fix 1 — Static initializer (your suggestion)

```java
class OrderProcessor {
    // JVM guarantees class loading is thread-safe
    // static block runs once, before any thread calls getInstance()
    private static final OrderProcessor instance = new OrderProcessor();

    private OrderProcessor() {}

    public static OrderProcessor getInstance() {
        return instance;  // no null check needed — always initialized
    }
}
```

**Why this works:**

```
JVM class loading is inherently thread-safe
Static fields initialized before any thread accesses the class
→ instance always non-null when getInstance() called
→ no synchronization needed
→ simplest and most correct
```

**Downside:**

```
Eager initialization — created even if never used
If OrderProcessor is expensive to create → wasted resources
```

---

### Fix 2 — Double-checked locking (your suggestion)

```java
class OrderProcessor {
    private static volatile OrderProcessor instance;  // volatile!

    private OrderProcessor() {}

    public static OrderProcessor getInstance() {
        if (instance == null) {                    // first check — no lock
            synchronized (OrderProcessor.class) {
                if (instance == null) {            // second check — with lock
                    instance = new OrderProcessor();
                }
            }
        }
        return instance;
    }
}
```

**Why two checks:**

```
First check (no lock):
  instance already created → return immediately
  99.99% of calls take this path → no lock overhead

Second check (with lock):
  Thread1 and Thread2 both pass first check simultaneously
  Thread1 acquires lock → creates instance
  Thread2 acquires lock → checks AGAIN → not null → skips creation
  → only one instance created ✓
```

**Why volatile is critical:**

```java
instance = new OrderProcessor();
// Three steps:
// 1. allocate memory
// 2. initialize object
// 3. assign reference to instance

// Without volatile — CPU can reorder to 1→3→2:
// Thread1: allocates memory, assigns reference (step 3 before 2)
// Thread2: first check → instance != null → returns it
// Thread2: uses UNINITIALIZED object → crash or corrupt state

// volatile prevents reordering → always 1→2→3
```

---

### Fix 3 — Enum Singleton (most robust)

```java
enum OrderProcessor {
    INSTANCE;

    public void processOrder(Order order) {
        // implementation
    }
}

// usage
OrderProcessor.INSTANCE.processOrder(order);
```

Why enum is best:

```
JVM guarantees enum instantiation is thread-safe
Serialization safe — enum deserialization never creates new instance
Reflection safe — can't create second instance via reflection
→ Joshua Bloch recommends this in Effective Java
```

---

## Summary — which Singleton to use

| Approach | Thread safe | Lazy | Simple |
|---|---|---|---|
| Static field | ✓ | ✗ (eager) | ✓ |
| Double-checked locking | ✓ | ✓ | Medium |
| Enum | ✓ | ✗ (eager) | ✓ |
| synchronized getInstance() | ✓ | ✓ | ✓ but slow |

**For GS interview:**

*"Static initializer is simplest and correct — JVM guarantees thread-safe class loading. If lazy initialization needed, double-checked locking with volatile prevents both multiple instantiation and uninitialized object access. Enum singleton is most robust — serialization and reflection safe."*

---

## Q5

```java
class TradeExecutor {
    private final List<Trade> executedTrades = new ArrayList<>();

    public void executeTrade(Trade trade) {
        executedTrades.add(trade);
    }

    public List<Trade> getRecentTrades(int n) {
        return executedTrades
            .subList(executedTrades.size() - n, executedTrades.size());
    }
}
```

500 threads calling executeTrade() simultaneously. What are the issues and how do you fix them?

Really good thinking — you identified the core issues. Let me push deeper on each point.

---

## Your answers evaluated

### "Ordering might be wrong"

Actually ordering is a secondary concern here. The **primary** problem is more severe:

```java
executedTrades.add(trade);
```

`ArrayList.add()` is NOT thread-safe:

```
ArrayList internally:
  Object[] elementData;  // backing array
  int size;

add() does:
  1. elementData[size] = trade  // write element
  2. size++                     // increment size

Thread1: elementData[5] = trade1, size=5 (not yet incremented)
Thread2: elementData[5] = trade2  ← overwrites trade1!
Both:    size++ → size=6 but one trade lost

Worse — ArrayList resizes when full:
  copies to new array
  Thread2 still writing to OLD array
  → ArrayIndexOutOfBoundsException
  → data corruption
  → NullPointerException
```

This is a **data corruption** issue — not just ordering.

---

### getRecentTrades() has second problem

```java
public List<Trade> getRecentTrades(int n) {
    return executedTrades
        .subList(executedTrades.size() - n, executedTrades.size());
}
```

Two separate calls to `size()`:

```
Thread1: executedTrades.size() = 100  ← first call
Thread2: adds 5 trades
Thread1: executedTrades.size() = 105  ← second call

subList(100-10=90, 105)  ← inconsistent bounds
→ ConcurrentModificationException or wrong results
```

---

## Your three fixes evaluated

### Fix 1 — synchronized ✓ correct but has issue

```java
public synchronized void executeTrade(Trade trade) {
    executedTrades.add(trade);
}

public synchronized List<Trade> getRecentTrades(int n) {
    int size = executedTrades.size();  // single call — consistent
    return new ArrayList<>(
        executedTrades.subList(size - n, size));  // copy — safe to return
}
```

You're right — expensive under 500 threads. But correct.

---

### Fix 2 — CopyOnWriteArrayList ✓ correct analysis

```java
private final List<Trade> executedTrades = new CopyOnWriteArrayList<>();
```

You correctly identified the problem:

```
500 threads adding trades simultaneously
Each add() copies entire array
→ 500 simultaneous array copies
→ memory pressure + latency

With 1M trades in list:
each add() copies 1M elements → O(n) per write
→ completely unacceptable for trading system
```

---

### Fix 3 — ConcurrentLinkedDeque ✓ best instinct

```java
private final ConcurrentLinkedDeque<Trade> executedTrades 
                            = new ConcurrentLinkedDeque<>();

public void executeTrade(Trade trade) {
    executedTrades.addLast(trade);  // lock-free O(1)
}
```

You're right — this is the best approach. Let me complete it:

```java
class TradeExecutor {
    private final ConcurrentLinkedDeque<Trade> executedTrades 
                                = new ConcurrentLinkedDeque<>();

    public void executeTrade(Trade trade) {
        executedTrades.addLast(trade);   // lock-free, thread-safe O(1)
    }

    public List<Trade> getRecentTrades(int n) {
        // snapshot — consistent view at this moment
        List<Trade> snapshot = new ArrayList<>(executedTrades);
        int size = snapshot.size();
        if (size < n) return snapshot;
        return snapshot.subList(size - n, size);
    }
}
```

Why ConcurrentLinkedDeque wins:

```
addLast()  → lock-free CAS → O(1) → 500 threads no contention ✓
snapshot   → ArrayList copy at point in time → consistent ✓
no lock    → high throughput ✓
no copy on every write → low latency ✓
```

---

## One more thing you missed — ordering

You mentioned ordering — let me clarify:

```
Thread1 adds trade1 at t=10:00:00.001
Thread2 adds trade1 at t=10:00:00.002

ConcurrentLinkedDeque guarantees:
→ both added successfully ✓
→ insertion order = order addLast() completed

But "completed" order may differ from "submitted" order:
Thread2 may complete addLast() before Thread1
→ trade2 appears before trade1 in deque

For audit purposes — use trade timestamp not insertion order:
```

```java
class Trade {
    String tradeId;
    long timestamp = System.nanoTime();  // capture at creation
}

// sort by timestamp when needed
public List<Trade> getRecentTrades(int n) {
    return new ArrayList<>(executedTrades).stream()
        .sorted((a, b) -> Long.compare(a.timestamp, b.timestamp))
        .skip(Math.max(0, executedTrades.size() - n))
        .collect(Collectors.toList());
}
```

---

## Final answer for interview

*"ArrayList has two problems — add() is not thread-safe causing data corruption under concurrent writes, and getRecentTrades() calls size() twice creating inconsistent bounds. CopyOnWriteArrayList fixes safety but O(n) copy per write is unacceptable for 500 concurrent threads. ConcurrentLinkedDeque is optimal — lock-free CAS for addLast() means 500 threads add without contention. For getRecentTrades(), snapshot the deque into ArrayList for consistent view. For strict trade ordering, capture timestamp at Trade creation and sort by timestamp rather than relying on insertion order."*

---

## Q6 — Final question

```java
class RiskEngine {
    private final Map<String, Double> riskScores = new HashMap<>();

    public void updateRisk(String deskId, double score) {
        riskScores.put(deskId, score);
    }

    public double getRisk(String deskId) {
        return riskScores.getOrDefault(deskId, 0.0);
    }

    public double getTotalRisk() {
        return riskScores.values()
                         .stream()
                         .mapToDouble(Double::doubleValue)
                         .sum();
    }
}
```

Multiple threads updating risk scores simultaneously. Multiple threads reading total risk for compliance checks.

What are all the issues and what's your production solution?


Good instinct on ConcurrentHashMap — but let me push deeper. Two questions before I give the answer:

---

## Question 1 — why compute for getRisk?

getRisk is just:
```java
return riskScores.getOrDefault(deskId, 0.0);
```

This is a single read — why would you need compute here? What race condition are you seeing?

---

## Question 2 — is getTotalRisk() safe with ConcurrentHashMap?

```java
public double getTotalRisk() {
    return riskScores.values()
                     .stream()
                     .mapToDouble(Double::doubleValue)
                     .sum();
}
```

Even with ConcurrentHashMap — what problem remains here?

```
Thread1: getTotalRisk() → iterating values
Thread2: updateRisk("deskA", 500.0) → updates mid-iteration

What does Thread1 see?
```

Think about both questions — then I'll give the full answer.

## Full Answer

---

## Issue 1 — HashMap data corruption

```
HashMap.put() not thread-safe:
  resize → copies array
  two threads resize simultaneously → infinite loop
  two threads put same bucket → one entry lost
→ data corruption, not just wrong values
```

Fix → ConcurrentHashMap ✓

---

## Issue 2 — getRisk() is fine as is

```java
return riskScores.getOrDefault(deskId, 0.0);
```

Single read operation on ConcurrentHashMap — already thread-safe. No compute needed.

compute() needed when you **read + modify together:**

```java
// needs compute — read then write atomically
riskScores.compute(deskId, (k, v) -> v == null ? score : v + score);

// doesn't need compute — just read
riskScores.getOrDefault(deskId, 0.0);  // fine as is
```

---

## Issue 3 — getTotalRisk() inconsistent snapshot

This is the real problem even with ConcurrentHashMap:

```
Thread1: getTotalRisk() starts iterating
  reads deskA=100, deskB=200...

Thread2: updateRisk("deskA", 500)  ← updates mid-iteration

Thread1: continues iterating
  may see deskA's NEW value 500
  OR may see old value 100
  → sum is neither old total nor new total
  → phantom read — inconsistent snapshot
```

ConcurrentHashMap iteration gives **weakly consistent** view — reflects some but not necessarily all updates made during iteration.

---

## Full solution

```java
class RiskEngine {
    private final ConcurrentHashMap<String, Double> riskScores 
                                        = new ConcurrentHashMap<>();
    
    // ReadWriteLock for getTotalRisk consistency
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock  = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public void updateRisk(String deskId, double score) {
        writeLock.lock();
        try {
            riskScores.put(deskId, score);
        } finally {
            writeLock.unlock();
        }
    }

    public double getRisk(String deskId) {
        // single read — no lock needed
        // ConcurrentHashMap.get() is lock-free
        return riskScores.getOrDefault(deskId, 0.0);
    }

    public double getTotalRisk() {
        readLock.lock();   // blocks updateRisk() during sum
        try {
            return riskScores.values()
                             .stream()
                             .mapToDouble(Double::doubleValue)
                             .sum();
        } finally {
            readLock.unlock();
        }
    }
}
```

---

## Why this works

```
updateRisk() → writeLock → exclusive
  blocks other updateRisk() calls ✓
  blocks getTotalRisk() during update ✓

getRisk() → no lock
  CHM.getOrDefault() is lock-free ✓
  single read — no inconsistency possible ✓

getTotalRisk() → readLock → shared
  multiple compliance threads can sum simultaneously ✓
  blocks updateRisk() during iteration ✓
  → consistent snapshot guaranteed ✓
```

---

## But wait — do we even need ConcurrentHashMap now?

Good question — with ReadWriteLock protecting all writes and reads:

```java
// could use plain HashMap
private final Map<String, Double> riskScores = new HashMap<>();

// ALL access via ReadWriteLock
// writeLock protects put()
// readLock protects iteration
// getRisk() needs readLock too now
```

But mixing CHM + ReadWriteLock is cleaner:

```java
getRisk()      → CHM lock-free read  → fast path, no lock
getTotalRisk() → readLock            → consistent snapshot
updateRisk()   → writeLock           → exclusive write
```

getRisk() stays lock-free — called thousands of times per second for individual desk checks. Only getTotalRisk() needs consistency guarantee.

---

## What to say in interview

*"Three issues — HashMap data corruption under concurrent writes, getRisk is fine as single CHM read, getTotalRisk has phantom read problem even with CHM since iteration is weakly consistent. Solution: ConcurrentHashMap for lock-free individual reads, ReadWriteLock for updateRisk and getTotalRisk — write lock ensures no updates mid-iteration, read lock allows multiple compliance threads to sum simultaneously. getRisk stays lock-free — it's on the hot path called thousands of times per second."*

---

Concurrency ✅ done.

## Remaining

| # | Topic | Status |
|---|---|---|
| 27 | JVM Memory — Heap vs Stack, GC | ⬜ Pending |
| 28 | Design Patterns | ⬜ Pending |
| 13 | Binary Search patterns | ⬜ Pending |
| 14 | Unique Paths / Minimum Path Sum | ⬜ Pending |
| 15 | Coin Change + Word Break | ⏸ Parked |
| 29 | Puzzles | ⬜ Pending |

**JVM Memory next or Design Patterns?**


Great question — this is fundamental and often misunderstood. Let me build it from scratch.

---

## How threads and CPU are related

### Physical reality

```
Your machine has say 8 CPU cores
Each core can execute ONE thread at a time

8 cores → 8 threads truly parallel at any moment
         → everything else is WAITING or CONTEXT SWITCHING
```

### What actually happens with more threads than cores

```
You create 100 threads, 8 cores

OS scheduler:
  gives each thread a time slice (1-10ms)
  after time slice → pause thread, save state
  switch to next thread → load state → run

This is CONTEXT SWITCHING:
  save registers, stack pointer, program counter
  load next thread's state
  resume execution

100 threads on 8 cores:
  at any moment: 8 running, 92 waiting
  OS rapidly switches between all 100
  gives illusion of parallelism
```

### Context switching cost

```
Each context switch:
  save/load CPU registers    ~microseconds
  CPU cache invalidation     ← expensive!
  TLB flush (memory mapping) ← expensive!

Too many threads:
  more time switching than working
  CPU cache constantly invalidated
  → thrashing — performance degrades
```

---

## CPU-bound vs IO-bound tasks

### CPU-bound task

```
Task that keeps CPU busy entire time:
  mathematical calculations
  risk calculations
  sorting large datasets
  image processing
  encryption

Timeline:
Thread: [■■■■■■■■■■■■■■■■■■■■] done
CPU:    [████████████████████] 100% busy entire time
```

CPU is the bottleneck — more cores = faster.

### IO-bound task

```
Task that spends most time WAITING:
  network request (getPrice from exchange)
  database query
  file read/write
  external API call

Timeline:
Thread: [■░░░░░░░░░░░░░░░░░░■] done
CPU:    [█                  █] tiny CPU usage
        waiting for response →
```

Thread is BLOCKED waiting for IO — CPU is idle during wait.

---

## Why this matters for thread pools

### CPU-bound pool

```java
int cores = Runtime.getRuntime().availableProcessors(); // e.g. 8

ExecutorService cpuPool = Executors.newFixedThreadPool(cores);
// or cores + 1 to keep CPU busy during rare pauses
```

Why `cores` threads?

```
8 cores, 8 CPU-bound threads:
  all 8 cores busy 100% ✓
  no context switching overhead ✓
  maximum CPU utilization ✓

8 cores, 16 CPU-bound threads:
  8 running, 8 waiting
  context switches every time slice
  cache invalidation on every switch
  → SLOWER than 8 threads!
  adding more threads hurts performance
```

### IO-bound pool

```java
// much larger pool
ExecutorService ioPool = Executors.newFixedThreadPool(100);
// or
ExecutorService ioPool = Executors.newCachedThreadPool();
```

Why more threads than cores?

```
8 cores, 100 IO-bound threads:

Thread1:  [■░░░░░░░░░░░░░░░░░■]  waiting for DB
Thread2:  [■░░░░░░░░░░░░░░░░░■]  waiting for network
Thread3:  [■░░░░░░░░░░░░░░░░░■]  waiting for file
...
Thread8:  [■░░░░░░░░░░░░░░░░░■]  waiting for API

While Thread1 waits:
  CPU switches to Thread2 → does tiny bit of work
  Thread2 waits → switches to Thread3
  ...all 8 cores stay busy serving different waiting threads

Without extra threads:
  Thread1 waits → CPU IDLE (wasted!)
  Thread2 waits → CPU IDLE (wasted!)
  → 8 threads on 8 cores = 7 cores idle most of time
```

More threads = more concurrent IO operations in flight = better throughput.

---

## Can you spin any number of threads?

**Technically yes — practically no.**

Each thread costs:

```
Java thread = OS thread (by default)
Each thread needs:
  Stack memory: 256KB - 1MB default
  OS kernel data structures
  Thread scheduling overhead

1000 threads:
  1000 * 512KB = 500MB stack memory
  OS scheduler managing 1000 threads
  → memory pressure + scheduling overhead

10000 threads:
  → OutOfMemoryError
  → system instability
```

### Why you can't just create unlimited threads for IO

```
You have 10000 DB connections to make:

Option 1: 10000 threads
  10000 * 512KB = 5GB memory just for stacks
  OS overwhelmed scheduling 10000 threads
  → system crash

Option 2: 100 thread pool + queue
  100 threads handle connections
  remaining 9900 tasks wait in queue
  threads pick up next task when done
  → 500MB memory, system stable
```

---

## The formula for IO-bound pool size

```
Optimal threads = cores * (1 + wait time / compute time)

Example:
  cores = 8
  DB query takes 100ms total
  actual CPU work = 10ms
  waiting = 90ms
  wait/compute = 9

  optimal = 8 * (1 + 9) = 80 threads

With 80 threads:
  while 72 threads wait for DB
  8 threads doing actual CPU work
  → all 8 cores busy 100% ✓
```

---

## Why dedicated pools matter

### Problem — mixing CPU and IO in one pool

```java
// BAD — single pool for everything
ExecutorService pool = Executors.newFixedThreadPool(8);

// CPU-bound tasks
pool.submit(() -> calculateRisk(portfolio));    // needs CPU

// IO-bound tasks  
pool.submit(() -> fetchPriceFromDB("AAPL"));   // mostly waiting
```

What happens:

```
8 threads in pool
4 threads: waiting for DB (IO-bound)  → CPU idle
4 threads: doing risk calc (CPU-bound) → CPU busy

4 cores idle because IO threads occupying pool slots
CPU-bound tasks starved — only 4 threads instead of 8
→ risk calculations take 2x longer
```

### Solution — dedicated pools

```java
// CPU-bound pool — matches core count
ExecutorService riskPool = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors());

// IO-bound pool — much larger
ExecutorService dbPool = Executors.newFixedThreadPool(50);

// CPU tasks go to riskPool
riskPool.submit(() -> calculateRisk(portfolio));

// IO tasks go to dbPool
dbPool.submit(() -> fetchPriceFromDB("AAPL"));
```

Now:

```
riskPool: 8 threads, all doing CPU work → 8 cores fully utilized
dbPool:   50 threads, most waiting for DB → cores shared efficiently
→ no starvation, optimal throughput
```

---

## Virtual threads — Java 21 (mention at VP level)

```java
// Project Loom — virtual threads
// lightweight threads managed by JVM not OS

ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor();

// create 10000 virtual threads — no problem
// JVM maps many virtual threads to few OS threads
// when virtual thread blocks on IO → JVM unmounts it
// OS thread picks up another virtual thread
// → no OS thread wasted on waiting

virtualPool.submit(() -> fetchPriceFromDB("AAPL"));
// blocks on DB? → JVM parks virtual thread
// OS thread immediately picks up another virtual thread
// → 10000 concurrent IO operations with only 8 OS threads
```

Virtual threads make IO-bound pool sizing largely irrelevant — JVM handles it automatically.

---

## Summary

| | CPU-bound | IO-bound |
|---|---|---|
| Bottleneck | CPU cores | Network/DB/Disk wait |
| Optimal threads | = core count | cores * (1 + wait/compute) |
| More threads → | slower (thrashing) | faster (more concurrent IO) |
| Example | Risk calc, encryption | DB query, API call |
| Pool type | FixedThreadPool(cores) | FixedThreadPool(large) or CachedThreadPool |

---

## What to say in interview

*"Each CPU core runs one thread at a time — OS context switches give illusion of parallelism. CPU-bound tasks should use core-count threads — more threads causes cache thrashing and slower performance. IO-bound tasks spend most time blocked waiting — extra threads let other work happen during waits, keeping all cores busy. Mixing both in one pool causes starvation — dedicated pools prevent CPU threads from being blocked by IO waiters. At Java 21+, virtual threads eliminate IO pool sizing concerns — JVM parks blocked virtual threads and reuses OS threads automatically."*

---

<- say i submit  8 cpu operation on  8 threads vs 8 io operation on 8 threads !! what happens is it that more cpu intenvice operation all threads re doing computation vs for IO they are just waiting so for cpu even if i add more than 8 other wouls be waiting whereas for io parallelsiation ! also if i have a cpu internsive task how addiing more cores will help ? as task would be running on a thread only ...also how cpu processing power have imapct


Great questions — let me build this up carefully.

---

## Scenario 1 — 8 CPU tasks on 8 threads

```
Core1: [████████████████████] Task1 computing
Core2: [████████████████████] Task2 computing
Core3: [████████████████████] Task3 computing
Core4: [████████████████████] Task4 computing
Core5: [████████████████████] Task5 computing
Core6: [████████████████████] Task6 computing
Core7: [████████████████████] Task7 computing
Core8: [████████████████████] Task8 computing

All 8 cores 100% busy
All 8 tasks complete in parallel
Total time = time of ONE task ✓
```

### What if you add 9th CPU task with 8 threads?

```
Core1: [████████████████████░░░░░░░░░░░░░░░░░░░░] Task1 then Task9
Core2-8: Tasks 2-8

Task9 must WAIT for a core to free up
Total time = time of ONE task + time of Task9
→ adding more CPU tasks doesn't help — cores are the bottleneck
```

### What if you add 9th thread for 9th CPU task?

```
8 cores, 9 CPU threads:

OS scheduler gives each thread time slice:
Core1: [██░██░██░██░] switching between Task1 and Task9
Core2-8: Tasks 2-8

Task1 AND Task9 both slower — sharing Core1
Context switch overhead on top
→ SLOWER than 8 threads ✗
```

**Adding more threads than cores for CPU tasks = worse performance.**

---

## Scenario 2 — 8 IO tasks on 8 threads

```
Core1: [■░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░■] Task1
Core2: [■░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░■] Task2
...
Core8: [■░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░■] Task8

■ = CPU work (tiny)
░ = waiting for IO (most of the time)

All 8 cores IDLE most of time
→ massive waste
```

### Add 100 threads for 100 IO tasks

```
Core1: [■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■]
       switches between Tasks 1,9,17,25,33,41,49...
       while each waits for IO, Core1 serves another

All 8 cores now busy serving different waiting threads
100 IO operations in flight simultaneously
Total time ≈ time of ONE IO task (all parallel)
→ adding more threads = better throughput ✓
```

**This is the key insight:**

```
CPU-bound: thread blocked on CPU → core busy → can't help other tasks
IO-bound:  thread blocked on IO  → core FREE → can serve other tasks
```

---

## Your question — how does adding more cores help CPU-bound tasks?

Single core, single CPU-bound task:

```
Core1: [████████████████████] Task takes 10 seconds
```

Add second core — but task runs on ONE thread:

```
Core1: [████████████████████] Task still takes 10 seconds
Core2: [                    ] idle — task doesn't use it
```

**Second core does NOTHING for single-threaded task.**

### How to use multiple cores for ONE task — parallelism

You must split the task into subtasks:

```java
// single threaded risk calculation — uses 1 core
double risk = positions.stream()
    .mapToDouble(p -> calculateRisk(p))
    .sum();
// takes 10 seconds on Core1

// parallel stream — splits across all cores
double risk = positions.stream()
    .parallel()           // splits work across cores
    .mapToDouble(p -> calculateRisk(p))
    .sum();
// takes 10/8 = ~1.25 seconds using all 8 cores ✓
```

Internally:

```
ForkJoinPool splits positions into 8 chunks:
Core1: [████] chunk1
Core2: [████] chunk2
Core3: [████] chunk3
...
Core8: [████] chunk8

All finish simultaneously → merge results
→ 8x faster ✓
```

### Real world — more cores help when

```
Task can be split into independent subtasks:
  ✓ Process 1M trades — split into 8 chunks of 125k
  ✓ Calculate risk for 1000 positions — split into 8 * 125
  ✓ Sort large dataset — parallel merge sort
  ✗ Sequential algorithm — step2 depends on step1
  ✗ Single DB query — network is bottleneck not CPU
```

---

## CPU processing power impact

### Clock speed (GHz)

```
Core running at 4GHz vs 2GHz:
  4GHz core executes 2x instructions per second
  same task completes in half the time
  → directly speeds up CPU-bound tasks
  → no impact on IO-bound tasks (bottleneck is network/disk)
```

### CPU cache (L1/L2/L3)

```
Memory hierarchy (fastest to slowest):
  L1 cache:  4ns  — per core, tiny (32KB)
  L2 cache:  12ns — per core (256KB)
  L3 cache:  40ns — shared across cores (8MB)
  RAM:       100ns
  SSD:       100μs
  Network:   1ms+

Cache miss impact on trading system:
  processing order data that fits in L1 → 4ns per access
  data evicted to RAM → 100ns per access → 25x slower!

This is why context switching hurts CPU-bound tasks:
  Thread1 loads data into cache → context switch
  Thread2 runs → overwrites cache with its data
  Thread1 resumes → cache miss → reload from RAM
  → 25x slower memory access
```

### NUMA (Non-Uniform Memory Access)

```
Multi-socket servers (common at GS):
  Socket1: 8 cores + 64GB RAM
  Socket2: 8 cores + 64GB RAM

Core on Socket1 accessing Socket2 RAM:
  → 2-3x slower than accessing Socket1 RAM

Thread pinning — bind threads to cores on same socket as their data:
  → avoids cross-socket memory access
  → critical for latency-sensitive trading systems
```

---

## The full picture

```
You have: calculateRisk() for 1000 positions

Single thread, 1 core, 2GHz:
  1000 * 10ms each = 10 seconds

Single thread, 1 core, 4GHz:
  1000 * 5ms each = 5 seconds (2x faster clock)

8 threads, 8 cores, 2GHz:
  parallel → 125 positions each → 1.25 seconds (8x faster)

8 threads, 8 cores, 4GHz:
  125 * 5ms = 0.625 seconds (16x faster than baseline)

16 threads, 8 cores, 4GHz:
  context switching overhead
  cache thrashing
  → maybe 0.7 seconds (slower than 8 threads!)
```

---

## Summary — the mental model

```
CPU-bound task:
  bottleneck = CPU cycles
  more cores  = faster (if task parallelized)
  faster clock = faster
  more threads than cores = slower (thrashing)
  IO doesn't matter

IO-bound task:
  bottleneck = network/disk latency
  more cores  = doesn't help much
  faster clock = doesn't help much
  more threads = faster (more concurrent IO)
  CPU mostly idle — just coordinating IO
```

---

## What to say in interview

*"CPU-bound tasks keep cores busy — adding more threads than cores causes context switching and cache thrashing, making performance worse. The only way to speed up CPU-bound work is more cores or faster clock speed, combined with parallelizing the task across threads. IO-bound tasks spend most time blocked waiting — the core is free to serve other threads during the wait. More threads means more concurrent IO operations in flight, keeping all cores busy. Dedicated pools prevent IO-bound threads from starving CPU-bound ones — IO threads hold pool slots while waiting, blocking CPU tasks from running."*

---

Ready for **JVM Memory** or **Design Patterns** next?