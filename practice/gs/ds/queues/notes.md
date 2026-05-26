## Queue Summary Table

| Queue | Backed By | Bounded | Blocking | Thread Safe | Order | Key Operations | Use Case |
|---|---|---|---|---|---|---|---|
| `ArrayDeque` | Array | No | No | No | FIFO/LIFO | addFirst/Last, pollFirst/Last, peekFirst/Last | Stack or Queue, single thread |
| `LinkedList` | LinkedList | No | No | No | FIFO/LIFO | offer, poll, peek | Simple queue, single thread |
| `PriorityQueue` | Binary Heap | No | No | No | Min/Max | offer O(logn), poll O(logn), peek O(1) | Top K, scheduling |
| `ArrayBlockingQueue` | Array | YES | Yes | Yes | FIFO | put (blocks), take (blocks), offer, poll | Bounded producer-consumer |
| `LinkedBlockingQueue` | LinkedList | Optional | Yes | Yes | FIFO | put (blocks), take (blocks), offer, poll | High throughput producer-consumer |
| `PriorityBlockingQueue` | Binary Heap | No | Yes (take only) | Yes | Min/Max | offer O(logn), poll O(logn), take (blocks) | Thread-safe priority scheduling |
| `ConcurrentLinkedQueue` | LinkedList | No | No | Yes (CAS) | FIFO | offer O(1), poll O(1) | Lock-free concurrent queue |
| `ConcurrentLinkedDeque` | LinkedList | No | No | Yes (CAS) | FIFO/LIFO | addFirst/Last, pollFirst/Last | Lock-free deque, audit trail |
| `DelayQueue` | PriorityQueue | No | Yes | Yes | Delay expiry | offer, take (blocks until delay) | TTL eviction, scheduled tasks |
| `SynchronousQueue` | None | 0 capacity | Yes | Yes | N/A | put (blocks until taken), take (blocks) | Direct handoff between threads |

---

## Blocking behaviour summary

```
offer()  → returns false if full, never blocks
put()    → blocks if full, waits for space
poll()   → returns null if empty, never blocks
take()   → blocks if empty, waits for element
peek()   → returns null if empty, never blocks
```

---

## Complexity

| Operation | ArrayDeque | PriorityQueue | ArrayBlockingQueue | ConcurrentLinkedQueue |
|---|---|---|---|---|
| Add | O(1) amortized | O(log n) | O(1) | O(1) |
| Remove | O(1) | O(log n) | O(1) | O(1) |
| Peek | O(1) | O(1) | O(1) | O(1) |
| Contains | O(n) | O(n) | O(n) | O(n) |
| Size | O(1) | O(1) | O(1) | O(n) ← approximate |

---

## When to use which

```
Single thread stack/queue          → ArrayDeque
Single thread priority             → PriorityQueue
Bounded producer-consumer          → ArrayBlockingQueue
High throughput producer-consumer  → LinkedBlockingQueue
Thread-safe priority               → PriorityBlockingQueue
Lock-free concurrent append        → ConcurrentLinkedDeque
TTL / scheduled tasks              → DelayQueue
Direct thread handoff              → SynchronousQueue
```

---

## Finance use cases

```
Order book (best bid/ask)     → PriorityQueue (MaxHeap + MinHeap)
Trade audit trail             → ConcurrentLinkedDeque
Risk calculation pipeline     → LinkedBlockingQueue (producer-consumer)
Market data TTL cache         → DelayQueue
Notification delivery         → LinkedBlockingQueue + ThreadPoolExecutor
Hospital triage               → PriorityBlockingQueue
Rate limiter token refill     → ScheduledExecutorService + DelayQueue
```

---

## Why ConcurrentHashMap.newKeySet()?

---

### The problem with regular HashSet

```java
Set<Channel> set = new HashSet<>();
// NOT thread safe

Thread1: set.add(EMAIL)
Thread2: set.add(SMS)
→ internal array resize + add not atomic
→ data corruption
→ ConcurrentModificationException on iteration
```

---

### Why not Collections.synchronizedSet()?

```java
Set<Channel> set = Collections.synchronizedSet(new HashSet<>());
// thread safe BUT:
// single lock for ALL operations
// iteration still needs manual sync:

synchronized(set) {
    for (Channel c : set) { ... }  // must lock during iteration
}
// easy to forget → bugs
```

---

### Why ConcurrentHashMap.newKeySet()?

```java
Set<Channel> set = ConcurrentHashMap.newKeySet();
```

Internally it's a `ConcurrentHashMap<Channel, Boolean>` where:
```
key   = the set element
value = dummy Boolean.TRUE
```

Gets ALL benefits of ConcurrentHashMap:
```
✓ Segment-level locking — parallel adds on different elements
✓ Lock-free reads — contains() never blocks
✓ Safe iteration — weakly consistent, no CME
✓ No manual synchronization needed
✓ computeIfAbsent, forEach all atomic
```

---

### Comparison

| | HashSet | synchronizedSet | ConcurrentHashMap.newKeySet() |
|---|---|---|---|
| Thread safe | No | Yes | Yes |
| Lock granularity | N/A | Global | Segment |
| Parallel adds | No | No | Yes |
| Safe iteration | No | Manual sync | Yes (weakly consistent) |
| Performance | High | Low | High |

---

### In our notification system

```java
.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())

// subscribe() and notify() both access same Set<Channel>
// subscribe: set.add(channel)      → concurrent safe ✓
// notify:    iterate channels      → concurrent safe ✓
// unsubscribe: set.remove(channel) → concurrent safe ✓
```

---

Ready for **Stock Trading revision**?