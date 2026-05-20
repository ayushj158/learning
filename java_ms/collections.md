## Collections — What GS Tests

GS doesn't ask you to implement these — they ask **when to use which** and **trade-offs.** Common pattern: they give you a problem and ask which collection you'd pick and why.

---

## The full picture — one slide

```
Collection
├── List
│   ├── ArrayList    → random access, dynamic array
│   └── LinkedList   → fast insert/delete at ends
├── Set
│   ├── HashSet      → fast lookup, no order
│   ├── LinkedHashSet→ insertion order preserved
│   └── TreeSet      → sorted order
├── Queue
│   ├── PriorityQueue → min/max heap
│   ├── ArrayDeque   → fast stack/queue
│   └── LinkedList   → queue operations
└── Map
    ├── HashMap          → fast lookup, no order
    ├── LinkedHashMap    → insertion order preserved
    ├── TreeMap          → sorted by key
    └── ConcurrentHashMap→ thread safe
```

---

## The ones GS specifically tests

---

### PriorityQueue — min/max heap

```java
// min heap — smallest element at head (default)
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
minHeap.offer(5);
minHeap.offer(1);
minHeap.offer(3);
System.out.println(minHeap.peek());   // 1 — smallest
System.out.println(minHeap.poll());   // 1 — removes smallest

// max heap — largest element at head
PriorityQueue<Integer> maxHeap = 
    new PriorityQueue<>(Collections.reverseOrder());
// or
PriorityQueue<Integer> maxHeap = 
    new PriorityQueue<>((a, b) -> b - a);

// custom object — sort by price
PriorityQueue<Order> byPrice = 
    new PriorityQueue<>((a, b) -> Double.compare(a.price, b.price));
```

**Key operations:**
```java
pq.offer(x)   // add — O(log n)
pq.poll()     // remove head — O(log n)
pq.peek()     // view head — O(1)
pq.size()     // size — O(1)
```

**When GS asks:**
- Task Scheduler ✓ (we used it)
- Top K elements
- Merge K sorted lists
- TTL Cache ✓ (we used it)

---

### ArrayDeque — double ended queue

```java
ArrayDeque<Integer> deque = new ArrayDeque<>();

// use as STACK (LIFO)
deque.push(1);    // addFirst
deque.push(2);
deque.push(3);
deque.pop();      // removeFirst → 3 (last in, first out)

// use as QUEUE (FIFO)
deque.offer(1);   // addLast
deque.offer(2);
deque.offer(3);
deque.poll();     // removeFirst → 1 (first in, first out)

// peek both ends
deque.peekFirst();  // view front
deque.peekLast();   // view back
```

**Why ArrayDeque over Stack/LinkedList?**
```
Stack class     → legacy, synchronized (slow), avoid
LinkedList      → extra memory per node (pointers), slower
ArrayDeque      → resizable array, no sync overhead, faster
```

**Always use ArrayDeque for stack/queue in interviews.**

**When GS asks:**
- Sliding window maximum
- Valid parentheses
- BFS/DFS without recursion

---

### LinkedHashMap — insertion order map

```java
// preserves insertion order — iterates in order keys were added
Map<String, Double> prices = new LinkedHashMap<>();
prices.put("AAPL", 189.50);
prices.put("GOOG", 140.00);
prices.put("MSFT", 375.00);

// iterates in insertion order: AAPL, GOOG, MSFT
prices.forEach((k, v) -> System.out.println(k + "=" + v));
```

**LRU Cache with LinkedHashMap — most important use case:**

```java
// accessOrder=true → iterates in access order (LRU!)
Map<Integer, Integer> lruCache = new LinkedHashMap<>(
    capacity, 0.75f, true) {  // accessOrder = true
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
        return size() > capacity;  // auto-evict when over capacity
    }
};

lruCache.put(1, 1);
lruCache.put(2, 2);
lruCache.get(1);    // access 1 → moves to most recent
lruCache.put(3, 3); // capacity exceeded → evicts 2 (least recent)
```

This is the 3-line LRU Cache solution — mention this after coding the full version.

**When GS asks:**
- LRU Cache ✓
- Maintain insertion order
- Access frequency tracking

---

### TreeMap — sorted map

```java
// keys always sorted ascending
TreeMap<String, Double> prices = new TreeMap<>();
prices.put("MSFT", 375.00);
prices.put("AAPL", 189.50);
prices.put("GOOG", 140.00);

// iterates: AAPL, GOOG, MSFT (alphabetical)
System.out.println(prices.firstKey());  // AAPL
System.out.println(prices.lastKey());   // MSFT

// range queries — very useful for time series
prices.subMap("AAPL", "MSFT")   // keys between AAPL and MSFT
prices.headMap("GOOG")          // keys before GOOG
prices.tailMap("GOOG")          // keys from GOOG onwards

// floor/ceiling
prices.floorKey("FB")    // largest key ≤ "FB" → "AAPL" ... wait
prices.ceilingKey("FB")  // smallest key ≥ "FB" → "GOOG"
```

**When GS asks:**
- Time series data (priceHistory in Stock)
- Range queries
- "Find nearest price to X"

---

### HashMap vs LinkedHashMap vs TreeMap

| | HashMap | LinkedHashMap | TreeMap |
|---|---|---|---|
| Order | None | Insertion order | Sorted by key |
| get/put | O(1) | O(1) | O(log n) |
| Use when | Just need fast lookup | Need insertion order | Need sorted keys |

---

### HashSet vs LinkedHashSet vs TreeSet

```java
// HashSet — no order, O(1) lookup
Set<String> set = new HashSet<>();

// LinkedHashSet — insertion order, O(1) lookup  
Set<String> set = new LinkedHashSet<>();

// TreeSet — sorted, O(log n) lookup
Set<String> set = new TreeSet<>();
set.first()    // smallest
set.last()     // largest
set.floor(x)   // largest element ≤ x
set.ceiling(x) // smallest element ≥ x
set.subSet(a, b) // elements between a and b
```

---

## The decision tree — say this in interview

```
Need fast lookup only?              → HashMap / HashSet
Need insertion order?               → LinkedHashMap / LinkedHashSet
Need sorted order / range queries?  → TreeMap / TreeSet
Need min/max efficiently?           → PriorityQueue
Need stack?                         → ArrayDeque
Need queue?                         → ArrayDeque
Need thread safety?                 → ConcurrentHashMap
```

---

## GS interview questions on collections

**Q: "You need to find the K most frequent elements — which collection?"**
```
HashMap to count frequencies
PriorityQueue (min heap of size K) to track top K
→ O(n log k) solution
```

**Q: "Design a system that shows last 100 trades in order — which collection?"**
```
LinkedList or ArrayDeque — O(1) add to end, remove from front
→ sliding window of last 100
```

**Q: "You need to find if a price exists within a range — which collection?"**
```
TreeSet — floor() and ceiling() give O(log n) range check
```

**Q: "What's wrong with using Stack class in Java?"**
```
Stack extends Vector — synchronized on every operation
Even single-threaded code pays lock overhead
Use ArrayDeque instead — same functionality, no overhead
```

---

Clear? Any specific collection you want to go deeper on, or move to **Concurrency**?

## HashMap Internals

Let's go deep — one by one.

---

## 1. HashMap

### Internal structure

```
HashMap internally = array of buckets (Node[])
default size = 16 buckets

Each bucket = linked list of Node
Node = {key, value, hash, next}
```

```
index 0: null
index 1: [AAPL→189.50] → [GOOG→140.00]  ← collision, same bucket
index 2: null
index 3: [MSFT→375.00]
...
index 15: null
```

### How put() works

```java
map.put("AAPL", 189.50);

Step 1: hash("AAPL")
        hashCode = "AAPL".hashCode()  // Java String hashCode
        hash = hashCode ^ (hashCode >>> 16)  // spread bits

Step 2: find bucket
        index = hash & (capacity - 1)  // fast modulo using bitwise AND
        // capacity=16 → index = hash & 15

Step 3: insert at bucket
        if bucket empty → insert directly
        if bucket has nodes → traverse linked list
            if key exists → update value
            if key doesn't exist → append to list (collision)
```

### Why `hash & (capacity-1)` not `hash % capacity`?

```
capacity is always power of 2 (16, 32, 64...)
capacity-1 = 15 = 00001111

hash & 00001111 = last 4 bits of hash = bucket index
→ same as hash % 16 but bitwise AND is faster
```

### Collision handling — linked list → tree

```
Java 8+:
bucket has ≤ 8 nodes → linked list  O(n) lookup
bucket has > 8 nodes → converts to Red-Black Tree  O(log n) lookup
bucket drops to 6 nodes → converts back to linked list
```

### Load factor and rehashing

```
default load factor = 0.75
threshold = capacity * loadFactor = 16 * 0.75 = 12

when entries > 12:
  capacity doubles → 32
  ALL entries rehashed → expensive O(n) operation
  new index = hash & (newCapacity - 1)
```

**Why 0.75?**
```
0.75 balances memory vs performance
Low load factor (0.5) → fewer collisions, more memory wasted
High load factor (0.9) → more collisions, less memory wasted
0.75 = sweet spot from empirical testing
```

### get() — O(1) average

```java
map.get("AAPL");

Step 1: hash("AAPL") → index
Step 2: go to bucket[index]
Step 3: traverse linked list/tree
        compare key using equals()
        return value if found
```

### Why override both hashCode() AND equals()?

```
hashCode() → finds the right bucket
equals()   → finds the right key within bucket

If you override equals() but not hashCode():
  two equal objects → different hashCodes
  → stored in different buckets
  → get() can't find it even though it's there

If you override hashCode() but not equals():
  two equal objects → same bucket
  → but equals() uses reference comparison
  → treated as different keys
  → duplicates in map
```

---

## 2. LinkedHashMap Internals

### What's different from HashMap?

LinkedHashMap extends HashMap — same bucket array. But adds a **doubly linked list** running through all entries:

```
Bucket array (same as HashMap):
[0]: MSFT
[1]: AAPL → GOOG
[3]: TSLA

Doubly linked list (insertion order):
head ↔ AAPL ↔ GOOG ↔ MSFT ↔ TSLA ↔ tail
```

Every `put()` adds to both the bucket AND the doubly linked list tail.
Every `get()` in accessOrder=true mode moves the node to the tail.

### Memory overhead

```
HashMap Node:     {hash, key, value, next}
LinkedHashMap Entry: {hash, key, value, next, before, after}
                                              ↑ extra two pointers
```

Two extra pointers per entry — small overhead for ordered iteration.

### LRU with accessOrder=true

```
accessOrder=false (default): iterates insertion order
accessOrder=true:            iterates access order (LRU!)

put(1,1): list = [1]
put(2,2): list = [1,2]
put(3,3): list = [1,2,3]
get(1):   list = [2,3,1]  ← 1 moved to tail (most recent)
put(4,4): list = [2,3,1,4]
evict:    remove head = 2 (least recently used) ✓
```

`removeEldestEntry()` is called after every `put()` — if returns true, head is removed.

---

## 3. TreeMap Internals

### Internal structure — Red-Black Tree

```
Not an array — a self-balancing binary search tree

         GOOG(B)
        /        \
    AAPL(R)    MSFT(R)
                    \
                  TSLA(B)

B = Black node
R = Red node
```

### Red-Black Tree rules

```
1. Every node is RED or BLACK
2. Root is always BLACK
3. Red node can't have red child (no two consecutive reds)
4. Every path from root to null has same number of BLACK nodes
5. Null nodes are BLACK
```

These rules guarantee tree height ≤ 2 * log(n) → O(log n) operations.

### Why not plain BST?

```
Plain BST — worst case O(n):
put(1), put(2), put(3), put(4)...
→ degenerates to linked list

Red-Black Tree — always O(log n):
auto-balances via rotations and color flips after every insert/delete
```

### How put() works in TreeMap

```java
treeMap.put("GOOG", 140.00);

Step 1: start at root
Step 2: compare key using compareTo() or custom Comparator
        key < node.key → go left
        key > node.key → go right
        key == node.key → update value
Step 3: insert as red leaf
Step 4: fix Red-Black violations via rotations + recoloring
```

### floor() and ceiling() — why TreeMap is powerful

```java
TreeMap<Integer, String> prices = new TreeMap<>();
prices.put(100, "low");
prices.put(200, "mid");
prices.put(300, "high");

prices.floorKey(250)    // largest key ≤ 250 → 200
prices.ceilingKey(150)  // smallest key ≥ 150 → 200
prices.subMap(100, 300) // keys in range [100, 300)
```

Traverses tree in O(log n) — impossible with HashMap.

### Comparator in TreeMap

```java
// reverse order
TreeMap<String, Double> map = new TreeMap<>(
    (a, b) -> b.compareTo(a));  // reverse alphabetical

// custom object — sort by price
TreeMap<Stock, Double> map = new TreeMap<>(
    (a, b) -> Double.compare(a.price, b.price));
```

---

## 4. ConcurrentHashMap Internals

### Java 7 — Segment locking

```
16 segments, each is a mini HashMap with its own lock

Segment 0: [AAPL, MSFT]  ← ReentrantLock
Segment 1: [GOOG, AMZN]  ← ReentrantLock
...
Segment 15: [GS, JPM]    ← ReentrantLock

put("AAPL") → locks Segment 0 only
put("GOOG") → locks Segment 1 only
→ parallel writes to different segments ✓
→ writes to same segment → one waits ✗
```

### Java 8+ — Node-level locking (better)

```
No segments — uses synchronized on individual bucket head node

put("AAPL"):
  hash → bucket index
  synchronized(bucketHead) {  ← locks only this bucket
      insert/update
  }

put("GOOG"):
  different bucket → different lock → parallel ✓
```

Much finer granularity than segment locking.

### Read operations — lock free

```java
// get() is completely lock free in CHM
// uses volatile reads
// Node.value is volatile → always reads latest value
// no lock needed for reads
```

### size() — approximate

```java
map.size()  // not exactly accurate under concurrent modification
            // uses a distributed counter (LongAdder internally)
            // sums up per-segment counts → approximate
```

### compute() atomicity

```java
map.compute(key, (k, v) -> newValue);
// synchronized on bucket head
// entire lambda runs atomically within bucket lock
// other keys in different buckets → unaffected
```

---

## 5. HashSet / LinkedHashSet / TreeSet Internals

Simple — they're just wrappers around their Map counterparts:

```java
HashSet       → backed by HashMap,       value = dummy Object
LinkedHashSet → backed by LinkedHashMap, value = dummy Object
TreeSet       → backed by TreeMap,       value = dummy Object

set.add("AAPL")
→ map.put("AAPL", DUMMY_OBJECT)

set.contains("AAPL")
→ map.containsKey("AAPL")
```

That's literally it. All Set internals = Map internals.

---

## 6. Bounded vs Unbounded Queues

### Unbounded — no size limit

```java
// PriorityQueue — unbounded, grows forever
PriorityQueue<Integer> pq = new PriorityQueue<>();
// keeps growing until OutOfMemoryError

// LinkedList as queue — unbounded
Queue<Order> orders = new LinkedList<>();
// keeps growing forever
```

**Problem:** producer faster than consumer → queue grows → OOM

### Bounded — fixed capacity

```java
// ArrayBlockingQueue — bounded
BlockingQueue<Order> queue = new ArrayBlockingQueue<>(1000);
// max 1000 elements

// LinkedBlockingQueue — optionally bounded
BlockingQueue<Order> queue = new LinkedBlockingQueue<>(1000);
// LinkedBlockingQueue() with no arg → Integer.MAX_VALUE (effectively unbounded)
```

### Blocking operations — key difference from regular queue

```java
BlockingQueue<Order> queue = new ArrayBlockingQueue<>(10);

// PRODUCER
queue.put(order);      // blocks if full — waits until space available
queue.offer(order);    // returns false immediately if full
queue.offer(order, 5, TimeUnit.SECONDS);  // waits up to 5 sec

// CONSUMER
Order o = queue.take();    // blocks if empty — waits until element available
Order o = queue.poll();    // returns null immediately if empty
Order o = queue.poll(5, TimeUnit.SECONDS);  // waits up to 5 sec
```

### Producer-Consumer pattern — classic use

```java
BlockingQueue<Order> queue = new ArrayBlockingQueue<>(100);

// Producer thread — trading desk submits orders
Thread producer = new Thread(() -> {
    while (true) {
        Order order = getNextOrder();
        queue.put(order);     // blocks if queue full — natural backpressure
    }
});

// Consumer thread — risk engine processes orders
Thread consumer = new Thread(() -> {
    while (true) {
        Order order = queue.take();  // blocks if queue empty
        riskEngine.process(order);
    }
});
```

### PriorityBlockingQueue — unbounded priority queue

```java
// thread-safe priority queue — no capacity limit
PriorityBlockingQueue<Order> pq = new PriorityBlockingQueue<>(
    11, (a, b) -> Double.compare(b.price, a.price));  // highest price first

pq.offer(order);   // never blocks — unbounded
pq.take();         // blocks if empty — waits for element
```

**No put() on PriorityBlockingQueue** — unbounded so never blocks on insert.

### DelayQueue — elements available after delay

```java
// elements only available after their delay expires
DelayQueue<DelayedOrder> queue = new DelayQueue<>();

// use case: scheduled order execution
// "execute this order in 5 minutes"
queue.put(new DelayedOrder(order, 5, TimeUnit.MINUTES));

// take() blocks until an element's delay has expired
DelayedOrder delayed = queue.take();  // waits until 5 min passes
```

---

## Summary table

| Collection | Backed by | Order | Thread safe | Bounded |
|---|---|---|---|---|
| HashMap | Array + LinkedList/Tree | None | No | No |
| LinkedHashMap | HashMap + DoublyLinkedList | Insertion/Access | No | No |
| TreeMap | Red-Black Tree | Sorted | No | No |
| ConcurrentHashMap | Array + Node locking | None | Yes | No |
| HashSet | HashMap | None | No | No |
| LinkedHashSet | LinkedHashMap | Insertion | No | No |
| TreeSet | TreeMap | Sorted | No | No |
| PriorityQueue | Binary Heap (array) | Min/Max | No | No |
| PriorityBlockingQueue | Binary Heap | Min/Max | Yes | No |
| ArrayBlockingQueue | Array | FIFO | Yes | Yes |
| LinkedBlockingQueue | LinkedList | FIFO | Yes | Optional |
| DelayQueue | PriorityQueue | Delay expiry | Yes | No |

---

## What to say in interview for each

**HashMap:** *"Array of buckets, hash determines index, collision via linked list, converts to Red-Black Tree at 8 nodes. Load factor 0.75 triggers rehashing at 75% capacity."*

**LinkedHashMap:** *"Extends HashMap, adds doubly linked list for order. accessOrder=true enables LRU — get() moves node to tail, head is evicted."*

**TreeMap:** *"Red-Black Tree internally — self-balancing BST. All operations O(log n). Powerful for range queries via floor(), ceiling(), subMap()."*

**ConcurrentHashMap:** *"Java 8 uses node-level locking — synchronizes on bucket head, not entire map. Reads are lock-free via volatile. compute() runs lambda atomically under bucket lock."*

**Bounded vs Unbounded:** *"Bounded queues provide backpressure — producer blocks when full, preventing OOM. Critical for producer-consumer in trading systems. ArrayBlockingQueue for fixed capacity, PriorityBlockingQueue when order matters but no size limit needed."*

---

Clear? Move to **Concurrency** next?

Great set of questions — let me answer all 8 one by one.

---

## 1. Can PriorityQueue / PriorityBlockingQueue be bounded?

**PriorityQueue — No, unbounded always:**
```java
PriorityQueue<Integer> pq = new PriorityQueue<>(11);
// 11 is INITIAL CAPACITY not max capacity
// grows automatically like ArrayList
// no way to make it bounded
```

**PriorityBlockingQueue — No, also unbounded:**
```java
PriorityBlockingQueue<Integer> pq = new PriorityBlockingQueue<>(11);
// again 11 = initial capacity only
// offer() NEVER blocks — no capacity limit
// only take() blocks — waits when empty
```

**If you need bounded priority queue — build it yourself:**
```java
class BoundedPriorityQueue<T> {
    private final PriorityQueue<T> pq;
    private final int maxSize;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public BoundedPriorityQueue(int maxSize, Comparator<T> comparator) {
        this.maxSize = maxSize;
        this.pq = new PriorityQueue<>(comparator);
    }

    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (pq.size() >= maxSize) notFull.await(); // wait if full
            pq.offer(item);
            notEmpty.signal();  // notify consumer
        } finally {
            lock.unlock();
        }
    }

    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (pq.isEmpty()) notEmpty.await(); // wait if empty
            T item = pq.poll();
            notFull.signal();   // notify producer
            return item;
        } finally {
            lock.unlock();
        }
    }
}
```

---

## 2. ArrayBlockingQueue vs LinkedBlockingQueue

| | ArrayBlockingQueue | LinkedBlockingQueue |
|---|---|---|
| Backed by | Array (fixed) | LinkedList (nodes) |
| Bounded | Always — must specify | Optional — default Integer.MAX_VALUE |
| Memory | Pre-allocated upfront | Allocated per node |
| Lock | ONE lock for put+take | TWO separate locks |
| Throughput | Lower (shared lock) | Higher (separate locks) |
| Predictable memory | Yes — fixed array | No — grows with nodes |

**Two locks in LinkedBlockingQueue:**
```java
// LinkedBlockingQueue has:
ReentrantLock putLock  = new ReentrantLock(); // producer lock
ReentrantLock takeLock = new ReentrantLock(); // consumer lock

// producer and consumer never block EACH OTHER
// only producer blocks producer
// only consumer blocks consumer
// → higher throughput than ArrayBlockingQueue
```

**ArrayBlockingQueue has one lock:**
```java
// single lock — producer and consumer block each other
ReentrantLock lock = new ReentrantLock();
// put() and take() both acquire same lock
// → lower throughput but simpler
```

**When to use which:**
```
ArrayBlockingQueue:
  → memory is critical — pre-allocated, predictable
  → strictly bounded — can't accidentally grow
  → lower throughput ok

LinkedBlockingQueue:
  → higher throughput needed — separate put/take locks
  → optionally bounded — new LinkedBlockingQueue(1000)
  → memory less critical
```

---

## 3. PriorityBlockingQueue — offer vs take timeout

```java
PriorityBlockingQueue<Order> pq = new PriorityBlockingQueue<>();

// offer() — never blocks, always returns true
pq.offer(order);           // always succeeds — unbounded
pq.offer(order, 5, TimeUnit.SECONDS);  // timeout ignored! still never blocks

// take() — blocks indefinitely if empty
Order o = pq.take();       // blocks until element available

// poll() — non-blocking
Order o = pq.poll();       // returns null immediately if empty

// poll() with timeout — waits up to N time
Order o = pq.poll(5, TimeUnit.SECONDS);  // waits up to 5 sec, then null
```

**Summary:**
```
offer()          → never blocks (unbounded)
offer(timeout)   → timeout ignored, never blocks
take()           → blocks indefinitely
poll()           → returns null immediately
poll(timeout)    → waits up to timeout, then null
```

---

## 4. Bounded BlockingQueue — Producer Consumer

Real world example — trading order processing:

```java
class TradingSystem {
    // bounded queue — max 100 pending orders
    private final BlockingQueue<Order> orderQueue = 
        new ArrayBlockingQueue<>(100);

    private final ExecutorService producers = 
        Executors.newFixedThreadPool(5);   // 5 trading desks
    private final ExecutorService consumers = 
        Executors.newFixedThreadPool(3);   // 3 risk processors
    
    private volatile boolean running = true;

    // Producer — trading desk submits orders
    class OrderProducer implements Runnable {
        private final String deskId;

        public OrderProducer(String deskId) {
            this.deskId = deskId;
        }

        public void run() {
            while (running) {
                try {
                    Order order = generateOrder(deskId);
                    
                    // put() blocks if queue full — natural backpressure
                    // desk can't submit faster than risk engine processes
                    orderQueue.put(order);
                    System.out.println(deskId + " submitted: " + order.orderId);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // Consumer — risk engine processes orders
    class RiskProcessor implements Runnable {
        private final String processorId;

        public RiskProcessor(String processorId) {
            this.processorId = processorId;
        }

        public void run() {
            while (running || !orderQueue.isEmpty()) {
                try {
                    // poll with timeout — don't block forever on shutdown
                    Order order = orderQueue.poll(1, TimeUnit.SECONDS);
                    
                    if (order != null) {
                        processRisk(order);
                        System.out.println(processorId + 
                            " processed: " + order.orderId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void processRisk(Order order) {
            // risk calculation
            try { Thread.sleep(100); } 
            catch (InterruptedException e) {}
        }
    }

    public void start() {
        // start 5 producers
        for (int i = 1; i <= 5; i++)
            producers.submit(new OrderProducer("Desk" + i));

        // start 3 consumers
        for (int i = 1; i <= 3; i++)
            consumers.submit(new RiskProcessor("Processor" + i));
    }

    public void shutdown() {
        running = false;
        producers.shutdown();
        consumers.shutdown();
    }

    private Order generateOrder(String deskId) {
        return new Order(UUID.randomUUID().toString(), 
            deskId, "AAPL", 10, 189.50);
    }
}
```

**Key design decisions:**
```
1. put() in producer → natural backpressure
   desk can't flood system — blocks when queue full

2. poll(timeout) in consumer not take()
   allows graceful shutdown — doesn't block forever

3. volatile boolean running
   safe visibility across threads for shutdown flag

4. while (running || !orderQueue.isEmpty()) in consumer
   drain remaining orders after shutdown signal
```

---

## 5. CHM — is get() blocked when put() is in progress?

**Short answer — usually NO.**

Java 8 CHM uses volatile reads for get():

```java
// Node.value is volatile
static class Node<K,V> {
    final int hash;
    final K key;
    volatile V val;    // ← volatile
    volatile Node<K,V> next;  // ← volatile
}
```

**Scenario 1 — get() and put() on different keys:**
```
put("AAPL") → locks bucket 3
get("GOOG") → reads bucket 7 → no lock needed → parallel ✓
```

**Scenario 2 — get() and put() on SAME key:**
```
put("AAPL") → locks bucket 3, updates node.val
get("AAPL") → reads bucket 3

Does get() block? NO — reads volatile val directly
But: may read old value if put() not yet committed
→ eventual consistency within same bucket
```

**Scenario 3 — get() during rehashing:**
```
CHM never fully rehashes — uses ForwardingNode
Old bucket points to ForwardingNode during transfer
get() follows ForwardingNode to new table → always returns correct value
→ no blocking during resize ✓
```

**So when IS get() ever blocked?**
```
Only if compute()/merge()/putIfAbsent() is running on SAME bucket
These acquire bucket lock → get() on same bucket waits briefly
Normal put() → get() never blocks
```

---

## 6. compute() — return null, read value, remove key

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.put("AAPL", 5);

// return null → REMOVES the key
map.compute("AAPL", (k, v) -> null);
System.out.println(map.containsKey("AAPL")); // false — key removed ✓

// return value → updates/inserts
map.compute("AAPL", (k, v) -> {
    if (v == null) return 1;      // key didn't exist → insert 1
    return v + 1;                  // key exists → increment
});

// how to read AND manipulate AND return value
Integer[] result = new Integer[1];  // array trick for lambda
map.compute("AAPL", (k, v) -> {
    result[0] = v;          // capture current value
    if (v == null) return 1;
    return v + 1;
});
System.out.println("old value was: " + result[0]);

// cleaner — use computeIfPresent / computeIfAbsent
// computeIfAbsent — only runs if key absent
map.computeIfAbsent("AAPL", k -> 1);  // insert 1 only if not present

// computeIfPresent — only runs if key present
map.computeIfPresent("AAPL", (k, v) -> v + 1);  // increment only if exists

// merge — most useful for counters
map.merge("AAPL", 1, Integer::sum);
// if absent → put 1
// if present → apply Integer::sum(existing, 1) → existing + 1
```

**merge() is cleaner for frequency counting:**
```java
// instead of compute
map.compute("AAPL", (k, v) -> v == null ? 1 : v + 1);

// use merge
map.merge("AAPL", 1, Integer::sum);  // one line ✓
```

---

## 7. LRU Cache with LinkedHashMap

```java
class LRUCache {
    private final int capacity;
    private final Map<Integer, Integer> cache;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        
        // accessOrder=true → get() moves entry to tail
        // removeEldestEntry → evict head when over capacity
        this.cache = new LinkedHashMap<Integer, Integer>(
            capacity,    // initial capacity
            0.75f,       // load factor
            true         // accessOrder = true ← KEY
        ) {
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<Integer, Integer> eldest) {
                return size() > capacity;  // evict when over limit
            }
        };
    }

    public int get(int key) {
        // accessOrder=true → automatically moves to tail
        return cache.getOrDefault(key, -1);
    }

    public void put(int key, int value) {
        cache.put(key, value);
        // removeEldestEntry called automatically after put
        // if size > capacity → removes head (least recently used)
    }
}
```

**How size is controlled:**
```
capacity passed to constructor → stored as field
removeEldestEntry() checks size() > capacity
→ called after EVERY put()
→ if true → LinkedHashMap removes head automatically
→ head = least recently used (accessOrder=true)
```

**Why initial capacity = capacity in constructor:**
```java
new LinkedHashMap<>(capacity, 0.75f, true)
//                  ↑
//          initial bucket array size
//          set to capacity to avoid rehashing
//          since we'll never exceed capacity entries
```

---

## 8. TreeMap — custom complex comparators

```java
// Simple — sort by string length
TreeMap<String, Double> map = new TreeMap<>(
    (a, b) -> {
        int lenDiff = Integer.compare(a.length(), b.length());
        if (lenDiff != 0) return lenDiff;
        return a.compareTo(b);  // alphabetical if same length
    }
);

// Complex object — sort Stock by price then symbol
class Stock {
    String symbol;
    double price;
    int marketCap;
}

TreeMap<Stock, Integer> stockMap = new TreeMap<>(
    (a, b) -> {
        // primary sort: price ascending
        int priceCmp = Double.compare(a.price, b.price);
        if (priceCmp != 0) return priceCmp;
        
        // secondary sort: symbol alphabetical
        int symbolCmp = a.symbol.compareTo(b.symbol);
        if (symbolCmp != 0) return symbolCmp;
        
        // tertiary sort: marketCap descending
        return Integer.compare(b.marketCap, a.marketCap);
    }
);

// Comparator.comparing() chain — cleaner
TreeMap<Stock, Integer> stockMap = new TreeMap<>(
    Comparator.comparingDouble((Stock s) -> s.price)
              .thenComparing(s -> s.symbol)
              .thenComparingInt((Stock s) -> s.marketCap).reversed()
);
```

**Critical rule — comparator must be consistent with equals:**
```
if comparator.compare(a, b) == 0
→ TreeMap treats a and b as SAME KEY
→ second put() overwrites first

So if two stocks have same price AND symbol AND marketCap
→ treated as duplicate key → second overwrites first

Fix — add unique field as final tiebreaker:
Comparator.comparingDouble((Stock s) -> s.price)
          .thenComparing(s -> s.symbol)
          .thenComparing(s -> s.id)  // unique ID as tiebreaker
```

**TreeMap with null keys — NOT allowed:**
```java
TreeMap<String, Integer> map = new TreeMap<>();
map.put(null, 1);  // NullPointerException!
// compareTo(null) throws NPE
// use HashMap if null keys needed
```

---

Clear on all 8? Ready for **Concurrency** next?

## Collections Q&A — GS Style

I'll ask you questions one by one, exactly how GS would ask them. Answer each one before I move to the next.

---

### Q1

You're building a **real-time market data cache** at GS. Thousands of traders query prices for symbols like AAPL, GOOG every millisecond. Prices update frequently. Multiple threads read and write simultaneously.

Which Map implementation do you choose and why? What are the trade-offs?
Strong answer — you covered the key points. Let me add a few things to make it VP-level complete.

---

## What you said ✅

- Segment/bucket level locking for writes ✓
- get() lock-free via volatile reads ✓
- Eventual consistency on same key ✓

---

## What to add for VP level

**1. Why not synchronizedMap or Hashtable?**

```
Hashtable / Collections.synchronizedMap()
→ single lock on entire map
→ all reads AND writes sequential
→ 1000 threads reading same map → all wait for each other
→ unacceptable for real-time market data
```

**2. Eventual consistency — when is it a problem?**

```
For market data cache — acceptable:
  price updates milliseconds apart
  trader seeing 189.50 vs 189.51 for 1ms → fine

For order state — NOT acceptable:
  order filled or not → must be consistent
  → use compute() or explicit synchronization
```

**3. Cache expiry concern — what about stale prices?**

```
CHM has no TTL — stale prices stay forever
→ combine with TTL Cache we built earlier
→ or scheduled cleanup thread
→ or use Redis with EXPIRE in production
```

**4. Size concern**

```
CHM grows unbounded
For market data — bounded by number of symbols (~10,000)
→ acceptable
→ but mention you'd monitor memory
```

---

## Full VP answer

*"ConcurrentHashMap — bucket-level locking for writes means thousands of threads can read/write different symbols in parallel. get() is lock-free via volatile reads — no contention for reads. Occasional eventual consistency on same key is acceptable for market data — millisecond staleness doesn't affect trading decisions. I'd avoid Hashtable or synchronizedMap — their global lock would serialize all access and kill throughput. One concern is stale prices — CHM has no TTL so I'd add a background cleanup thread or wrap it with our TTL Cache pattern."*

---

## Q2

You're building an **order audit trail** — every order placed must be stored and later retrieved **in the exact order it was placed.** Concurrent writes from multiple trading desks. Fast reads for compliance reporting.

Which Map do you choose?

Great thinking — both valid approaches. Let me answer your question about LinkedHashMap race conditions first, then evaluate both options.

---

## LinkedHashMap Race Conditions

LinkedHashMap has TWO structures that must stay in sync:
1. Bucket array (like HashMap)
2. Doubly linked list (for order)

```
Thread1: put("order1") 
  Step1: insert into bucket array
  Step2: append to doubly linked list tail
                                            ← Thread2 interrupts here
Thread2: put("order2")
  Step1: insert into bucket array
  Step2: append to doubly linked list tail
  → both threads write to tail simultaneously
  → tail.next and newNode.prev corrupted
  → doubly linked list breaks
  → infinite loop during iteration (famous Java bug)
```

This is the exact race condition that caused production outages before ConcurrentHashMap existed. Iterating a corrupted LinkedHashMap can cause **infinite loop consuming 100% CPU.**

---

## Your two options evaluated

### Option 1 — LinkedHashMap + external synchronization

```java
Map<String, Order> map = Collections.synchronizedMap(
    new LinkedHashMap<>());

// BUT — iteration still needs manual sync
synchronized(map) {
    for (Map.Entry<String, Order> e : map.entrySet()) {
        // process
    }
}
```

Problems:
- Global lock — all reads and writes sequential
- Easy to forget synchronized block on iteration
- Low throughput under high concurrency

---

### Option 2 — Your idea: ConcurrentHashMap + BlockingQueue

```java
class OrderAuditTrail {
    // fast concurrent reads/writes
    private final ConcurrentHashMap<String, Order> orderMap 
                                    = new ConcurrentHashMap<>();
    
    // maintains insertion order
    private final BlockingQueue<String> insertionOrder 
                                    = new LinkedBlockingQueue<>();

    public void addOrder(Order order) {
        orderMap.put(order.orderId, order);    // O(1) concurrent write
        insertionOrder.offer(order.orderId);   // maintain order
    }

    public Order getOrder(String orderId) {
        return orderMap.get(orderId);          // O(1) concurrent read
    }

    public List<Order> getOrdersInSequence() {
        // iterate queue for order, lookup in map
        return insertionOrder.stream()
            .map(id -> orderMap.get(id))
            .filter(o -> o != null)
            .collect(Collectors.toList());
    }
}
```

Good approach but has one subtle race condition — can you spot it?

```
Thread1: orderMap.put("order1")  ← map updated
                                  ← Thread2 calls getOrdersInSequence()
Thread1: insertionOrder.offer("order1") ← not yet in queue

Thread2 sees order1 in map but not in queue yet
→ getOrdersInSequence() misses order1
```

Fix — reverse the order. Queue first, then map:

```
No — still broken. Queue has orderId but map doesn't have order yet.
getOrder() returns null.
```

Real fix — both operations under a single lock:

```java
private final ReentrantLock lock = new ReentrantLock();

public void addOrder(Order order) {
    lock.lock();
    try {
        orderMap.put(order.orderId, order);
        insertionOrder.offer(order.orderId);
    } finally {
        lock.unlock();
    }
}
```

---

## Option 3 — ConcurrentLinkedDeque (better for your use case)

Actually for audit trail you don't need Map at all if orderId is in the Order object:

```java
class OrderAuditTrail {
    // thread-safe, insertion ordered, unbounded
    private final ConcurrentLinkedDeque<Order> trail 
                            = new ConcurrentLinkedDeque<>();
    
    // fast lookup by orderId
    private final ConcurrentHashMap<String, Order> index 
                            = new ConcurrentHashMap<>();

    public void addOrder(Order order) {
        trail.addLast(order);              // O(1) append, thread-safe
        index.put(order.orderId, order);   // O(1) index
    }

    public Order getOrder(String orderId) {
        return index.get(orderId);         // O(1) lookup
    }

    public List<Order> getAllInOrder() {
        return new ArrayList<>(trail);     // snapshot in insertion order
    }
}
```

`ConcurrentLinkedDeque` is lock-free — uses CAS internally. No race condition between addLast() calls.

---

## Option 4 — Simplest correct answer for audit trail

Since audit trail is **append-only** and compliance reads are less frequent:

```java
class OrderAuditTrail {
    private final CopyOnWriteArrayList<Order> trail 
                        = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Order> index 
                        = new ConcurrentHashMap<>();

    public void addOrder(Order order) {
        index.put(order.orderId, order);
        trail.add(order);  // thread-safe append
    }

    public List<Order> getAllInOrder() {
        return Collections.unmodifiableList(trail);  // snapshot
    }
}
```

`CopyOnWriteArrayList`:
- Every write creates a new copy of array → expensive writes
- Reads are completely lock-free → fast
- Perfect for audit trail — rare writes, frequent reads

---

## Which to recommend in interview

```
High write throughput needed → ConcurrentHashMap + ConcurrentLinkedDeque
Read-heavy, rare writes      → ConcurrentHashMap + CopyOnWriteArrayList
Simple, correctness first    → synchronizedMap(LinkedHashMap) + manual sync
```

For GS audit trail:
*"I'd use ConcurrentHashMap for O(1) lookup plus ConcurrentLinkedDeque for insertion order — both lock-free. For compliance reads which are less frequent, snapshot the deque into a list. If write throughput is less critical than simplicity, CopyOnWriteArrayList gives lock-free reads with simpler code."*

---

## Q3

You're building a **trade matching engine**. Orders come in with a limit price. You need to always find:
- Highest BID (buy order) instantly
- Lowest ASK (sell order) instantly
- When highest bid >= lowest ask → match them

Which data structures for bids and asks? Why not TreeMap?


Great questions — let me answer all three properly.

---

## 1. LinkedBlockingQueue vs ConcurrentLinkedDeque

You're right to question this — let me clarify both:

### LinkedBlockingQueue
```java
BlockingQueue<String> queue = new LinkedBlockingQueue<>();
```

**Yes it IS thread-safe** — two separate locks:
```java
// internally
ReentrantLock putLock  = new ReentrantLock();  // producers
ReentrantLock takeLock = new ReentrantLock();  // consumers
```

But designed specifically for **producer-consumer**:
```
offer() / put()  → adds to TAIL only
poll() / take()  → removes from HEAD only
→ FIFO queue — not general purpose deque
→ no addFirst(), peekLast() etc.
→ bounded or unbounded
```

### ConcurrentLinkedDeque
```java
ConcurrentLinkedDeque<String> deque = new ConcurrentLinkedDeque<>();
```

Thread-safe but **no locks at all** — uses CAS:
```
addFirst() / addLast()      → both ends
peekFirst() / peekLast()    → both ends
pollFirst() / pollLast()    → both ends
→ true double-ended queue
→ always unbounded
→ no blocking operations (no take(), no put())
```

### Side by side

| | LinkedBlockingQueue | ConcurrentLinkedDeque |
|---|---|---|
| Thread safe | Yes — two locks | Yes — lock-free CAS |
| Blocking ops | Yes — take(), put() | No — never blocks |
| Bounded | Yes — optional | No — always unbounded |
| Direction | FIFO only | Both ends |
| Use case | Producer-consumer | General concurrent deque |

### For audit trail — why ConcurrentLinkedDeque

```
Audit trail needs:
  addLast()   → append new order ✓
  iterate()   → read all in order ✓
  no blocking needed — fire and forget append

LinkedBlockingQueue works too but:
  overkill — blocking not needed
  FIFO only — fine for audit but CLD is more flexible
```

---

## 2. Why lock makes map + queue atomic

Great question — this is about **memory visibility** and **atomicity** together.

### Problem without lock — two issues

**Issue 1 — Atomicity:**
```
Thread1: orderMap.put("order1", order)   ← step 1
                                          ← Thread2 calls getOrdersInSequence()
Thread1: insertionOrder.offer("order1")  ← step 2 not yet done

Thread2 sees order in map but not in queue
→ two operations not atomic → inconsistent state
```

**Issue 2 — Memory visibility:**
```
Thread1 writes to orderMap on CPU1
Thread1 writes to insertionOrder on CPU1

Without synchronization:
CPU1 cache → not yet flushed to main memory
Thread2 on CPU2 → reads stale values from its own cache
→ may not see Thread1's writes at all
```

### How lock fixes both

```java
private final ReentrantLock lock = new ReentrantLock();

public void addOrder(Order order) {
    lock.lock();    // ← acquire lock
    try {
        orderMap.put(order.orderId, order);      // step 1
        insertionOrder.offer(order.orderId);     // step 2
        // both steps happen — no thread can see between them
    } finally {
        lock.unlock();  // ← release lock
    }
}
```

**lock.lock() does two things:**

**Thing 1 — Mutual exclusion (atomicity):**
```
Only one thread inside lock block at a time
Thread2 calls addOrder() → waits at lock.lock()
Thread1 finishes both steps → unlocks
Thread2 acquires lock → sees consistent state
→ map and queue always in sync ✓
```

**Thing 2 — Memory barrier (visibility):**
```
lock.unlock() → flushes ALL writes to main memory
lock.lock()   → invalidates CPU cache, reads fresh from main memory

So yes — you're exactly right:
Thread1 unlocks → flushes orderMap + queue writes to main memory
Thread2 locks   → reads fresh values from main memory
→ Thread2 always sees both writes together ✓
```

This is called a **happens-before relationship** in Java Memory Model:

```
unlock() happens-before lock() on same monitor
→ everything Thread1 did before unlock()
   is visible to Thread2 after lock()
```

---

## 3. CopyOnWriteArrayList — deep dive

### The core idea

Every write operation creates a **brand new copy** of the entire array:

```java
CopyOnWriteArrayList<Order> list = new CopyOnWriteArrayList<>();

// internally starts as:
Object[] array = new Object[0];

list.add(order1);
// creates NEW array: [order1]
// atomically swaps reference: array = newArray

list.add(order2);
// creates NEW array: [order1, order2]
// atomically swaps reference: array = newArray
```

### How reads work — completely lock free

```java
// get() internally
public Order get(int index) {
    return (Order) array[index];  // just reads current array reference
    // no lock needed
    // array reference is volatile — always sees latest array
}
```

Readers always see a consistent snapshot — either the old array or the new array, never a partially written one.

### How writes work — expensive

```java
// add() internally
public boolean add(Order order) {
    synchronized (lock) {           // one writer at a time
        Object[] current = array;
        int len = current.length;
        
        Object[] newArray = Arrays.copyOf(current, len + 1);  // copy entire array
        newArray[len] = order;       // add new element
        array = newArray;            // atomic reference swap
        return true;
    }
}
```

Cost:
```
array has 1M orders
add() → copies 1M elements → O(n) per write
→ expensive for frequent writes
→ perfect for rare writes
```

### Why reads never see partial state

```java
// array reference is volatile
private volatile Object[] array;

// write:
Object[] newArray = Arrays.copyOf(current, len + 1);
newArray[len] = order;
array = newArray;  // volatile write — atomic reference swap
                   // reader either sees old array or new array
                   // never sees array being constructed

// read:
return array[index];  // volatile read — always sees latest complete array
```

The key insight — you're swapping a **reference** not modifying the array in place. Reference swap is atomic — no reader can see a half-written array.

### CopyOnWriteArrayList vs synchronizedList

```
synchronizedList:
  every get() acquires lock
  every add() acquires lock
  readers block each other
  → low read throughput

CopyOnWriteArrayList:
  get() — no lock, reads volatile reference
  add() — creates new array, swaps reference
  readers never block
  → high read throughput, expensive writes
```

### When to use

```
Good for:
  ✓ Read-heavy — event listeners, observer lists
  ✓ Rare writes — config lists, feature flags
  ✓ Audit trails — append occasionally, read frequently
  ✓ Iterating while others modify — no ConcurrentModificationException

Bad for:
  ✗ Frequent writes — O(n) copy per write kills performance
  ✗ Large lists with frequent updates — memory thrashing
  ✗ Real-time data — too expensive to update every ms
```

### For GS audit trail specifically

```java
class OrderAuditTrail {
    private final CopyOnWriteArrayList<Order> trail 
                        = new CopyOnWriteArrayList<>();

    // called thousands of times — each creates array copy
    // BUT orders are append-only and compliance reads dominate
    public void addOrder(Order order) {
        trail.add(order);  // expensive but acceptable
    }

    // called by compliance team — completely lock free
    // can iterate while new orders being added — no CME
    public List<Order> getAuditTrail() {
        return Collections.unmodifiableList(trail);
    }
}
```

If write frequency is high — switch to ConcurrentLinkedDeque + snapshot:

```java
// high write frequency alternative
public List<Order> getAuditTrail() {
    return new ArrayList<>(trail);  // snapshot of deque at this moment
}
```

---

## Summary — which to use when

```
Need blocking producer-consumer    → LinkedBlockingQueue
Need double-ended concurrent deque → ConcurrentLinkedDeque
Need lock-free reads, rare writes  → CopyOnWriteArrayList
Need atomic multi-step operations  → ReentrantLock
Need memory visibility             → volatile OR lock
```

---

Clear on all three? Shall we continue with **Q3 — trade matching engine** or move to **Concurrency** topic?

Exactly right on heaps — and good instinct to question TreeMap. Let me explain both.

---

## Your answer — MaxHeap + MinHeap ✅

```java
// Bids — MaxHeap, highest price first
PriorityQueue<Order> bids = new PriorityQueue<>(
    (a, b) -> Double.compare(b.price, a.price));

// Asks — MinHeap, lowest price first  
PriorityQueue<Order> asks = new PriorityQueue<>(
    (a, b) -> Double.compare(a.price, b.price));

// matching
Order bestBid = bids.peek();  // O(1)
Order bestAsk = asks.peek();  // O(1)

if (bestBid.price >= bestAsk.price) {
    // match!
    bids.poll();  // O(log n)
    asks.poll();  // O(log n)
}
```

peek() = O(1) — instant best price.
poll() = O(log n) — remove and reheapify.

---

## Why not TreeMap — the real reason

TreeMap also gives you min/max:

```java
TreeMap<Double, Order> bids = new TreeMap<>();
bids.lastKey()   // highest bid  O(log n)
bids.firstKey()  // lowest ask   O(log n)
```

**Problem 1 — duplicate prices:**

```
DeskA: buy AAPL at $189.00, qty=100
DeskB: buy AAPL at $189.00, qty=50

TreeMap key = price
bids.put(189.00, orderA)
bids.put(189.00, orderB)  // OVERWRITES orderA!
→ lost an order — catastrophic in trading
```

Fix would be `TreeMap<Double, List<Order>>` — messy.

**Problem 2 — peek is O(log n) not O(1):**

```
TreeMap.lastKey() → traverses to rightmost node → O(log n)
PriorityQueue.peek() → just returns array[0] → O(1)

Matching engine checks bid/ask millions of times per second
O(1) vs O(log n) matters enormously at that scale
```

**Problem 3 — remove by order ID is complex:**

```
Order cancelled → need to remove from book

TreeMap: find by price → find in list → remove → O(log n + k)
PriorityQueue: no direct remove by key → O(n) scan

Both have issues here — real matching engines use:
HashMap<orderId, Order> for O(1) cancel lookup
+ Heap for best price
+ lazy deletion — mark cancelled, skip on poll()
```

---

## Production matching engine pattern

```java
class OrderBook {
    // best price in O(1)
    private final PriorityQueue<Order> bids = new PriorityQueue<>(
        (a, b) -> Double.compare(b.price, a.price));
    private final PriorityQueue<Order> asks = new PriorityQueue<>(
        (a, b) -> Double.compare(a.price, b.price));

    // O(1) cancel lookup
    private final Set<String> cancelledOrders = new HashSet<>();

    public void cancel(String orderId) {
        cancelledOrders.add(orderId);  // lazy deletion
    }

    public List<Trade> match() {
        List<Trade> trades = new ArrayList<>();

        while (!bids.isEmpty() && !asks.isEmpty()) {
            // skip cancelled orders
            while (!bids.isEmpty() && 
                    cancelledOrders.contains(bids.peek().orderId))
                bids.poll();
            while (!asks.isEmpty() && 
                    cancelledOrders.contains(asks.peek().orderId))
                asks.poll();

            if (bids.isEmpty() || asks.isEmpty()) break;

            Order bid = bids.peek();
            Order ask = asks.peek();

            if (bid.price < ask.price) break;  // no match

            // match
            bids.poll();
            asks.poll();
            trades.add(new Trade(bid, ask));
        }
        return trades;
    }
}
```

---

## When IS TreeMap better than Heap?

```
Need range queries:
  "show all bids between $180-$190"
  → TreeMap.subMap(180.0, 190.0) → O(log n)
  → Heap can't do this at all

Need ordered iteration:
  "show full order book depth sorted by price"
  → TreeMap → natural sorted iteration
  → Heap → unsorted array internally

Need floor/ceiling:
  "nearest price to $189"
  → TreeMap.floorKey(189) → O(log n)
  → Heap → O(n) scan
```

For matching engine — **Heap wins** (O(1) best price, simple).
For order book display — **TreeMap wins** (sorted iteration, range queries).

**Real systems use both:**
```
PriorityQueue → matching (performance critical)
TreeMap       → order book display / market depth API
```

---

## What to say in interview

*"MaxHeap for bids and MinHeap for asks — peek() gives best price in O(1) which is critical for a matching engine running millions of matches per second. TreeMap gives O(log n) for min/max and has duplicate key problems at same price level. I'd add a HashSet of cancelled order IDs for lazy deletion — marking cancelled instead of removing from heap avoids O(n) scan."*

---

Q4 next or move to Concurrency?