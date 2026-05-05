# Complete Review — Linked Lists, Stacks & Queues, HashMaps

---

# Part 1 — Linked Lists

## Patterns & Templates

### Pattern 1 — Slow-Fast Pointers
```
Cue words: "middle", "cycle", "nth from end", "palindrome"
Cue situation: need to find a position without knowing length

Template:
ListNode slow = head;
ListNode fast = head;

while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
}
// slow is at middle
```

**Variations:**
```
Find middle      → stop when fast hits end → slow is middle
Detect cycle     → stop when slow==fast → cycle exists
Nth from end     → advance fast n steps first → then move both
```

---

### Pattern 2 — Prev-Curr (In-Place Modification)
```
Cue words: "reverse", "delete node", "modify in place"
Cue situation: need to change arrow directions

Template:
ListNode prev = null;
ListNode curr = head;

while (curr != null) {
    ListNode next = curr.next;  // 1. save next
    curr.next = prev;           // 2. flip arrow
    prev = curr;                // 3. advance prev
    curr = next;                // 4. advance curr
}
return prev;  // new head
```

---

### Pattern 3 — Dummy Node (Merging/Building)
```
Cue words: "merge", "combine", "build new list"
Cue situation: result list built by attaching nodes

Template:
ListNode dummy = new ListNode(-1);
ListNode curr = dummy;

while (condition) {
    curr.next = nextNode;
    curr = curr.next;
}
return dummy.next;  // skip dummy
```

---

### Pattern 4 — Multiple Pointers (K Lists)
```
Cue words: "merge k lists", "k sorted"
Cue situation: one pointer per list + heap

Template:
PriorityQueue<ListNode> heap = 
    new PriorityQueue<>((a,b) -> a.val - b.val);

// seed heap
for (ListNode head : lists) {
    if (head != null) heap.offer(head);
}

while (!heap.isEmpty()) {
    ListNode node = heap.poll();
    curr.next = node;
    curr = curr.next;
    if (node.next != null) heap.offer(node.next);
}
```

---

### Pattern 5 — HashMap + LinkedList (LRU Cache)
```
Cue words: "O(1) get and put", "cache", "evict least recently used"
Cue situation: need O(1) lookup AND O(1) order tracking

Template:
HashMap<Integer, Node> map;      // O(1) lookup
DoublyLinkedList list;           // O(1) order tracking

get(key):
  find via map → moveToFront → return value

put(key, val):
  if exists → update + moveToFront
  if new → addToFront + map.put
  if over capacity → removeTail + map.remove
```

---

## Problems Done vs Pending

| Problem | Pattern | Status |
|---|---|---|
| Reverse list — iterative | Prev-Curr | ✅ |
| Reverse list — recursive | Recursion | ⏸ Parked |
| Find middle | Slow-Fast | ✅ |
| Detect cycle | Slow-Fast | ✅ |
| Nth from end | Slow-Fast + gap | ✅ |
| Merge two sorted lists | Dummy node | ✅ |
| Merge K sorted lists | Heap + dummy | ✅ |
| LRU Cache | HashMap + DLL | ✅ |

---

## Pattern Recognition Nudges — Linked Lists

```
"Find middle"          → Slow-Fast
"Detect loop/cycle"    → Slow-Fast (stop when slow==fast)
"Nth from end"         → Slow-Fast (gap of n)
"Reverse"              → Prev-Curr
"Merge sorted"         → Dummy node + compare heads
"Merge k sorted"       → Dummy node + min heap
"O(1) get/put/evict"   → HashMap + DoublyLinkedList
"Palindrome list"      → Slow-Fast (find middle) + Prev-Curr (reverse half)
```

---

# Part 2 — Stacks & Queues

## Patterns & Templates

### Pattern 1 — Matching/Validation
```
Cue words: "valid", "balanced", "matching", "open/close"
Cue situation: pairs that must open before closing

Template:
Deque<Character> stack = new ArrayDeque<>();

for (char c : s.toCharArray()) {
    if (isOpening(c)) {
        stack.push(c);
    } else {
        if (stack.isEmpty()) return false;
        if (!matches(stack.peek(), c)) return false;
        stack.pop();
    }
}
return stack.isEmpty();  // ← not return true

Critical mistake: return true vs return stack.isEmpty()
```

---

### Pattern 2 — Monotonic Stack
```
Cue words: "next greater", "next smaller", "previous larger",
           "temperatures", "stock prices", "histogram"
Cue situation: for each element find first larger/smaller element

Template:
Deque<Integer> stack = new ArrayDeque<>();  // stores INDICES
int[] result = new int[n];
Arrays.fill(result, -1);

for (int i = 0; i < n; i++) {
    while (!stack.isEmpty() && arr[i] > arr[stack.peek()]) {
        result[stack.pop()] = arr[i];  // current resolves waiting
    }
    stack.push(i);  // push INDEX not value
}

Flip condition for next smaller:
  arr[i] < arr[stack.peek()]  → next smaller element
```

---

### Pattern 3 — Min/Max Stack
```
Cue words: "O(1) getMin", "O(1) getMax", "minimum at any point"
Cue situation: need running min/max with O(1) access

Template:
int[] valueStack;
int[] minStack;    // minStack[i] = min of everything up to index i
int top = -1;

push(val):
  top++
  valueStack[top] = val
  minStack[top] = (top==0) ? val : Math.min(val, minStack[top-1])

pop():  top--
peek(): valueStack[top]
getMin(): minStack[top]   // O(1) always

Key insight: both stacks always same size → single index
```

---

### Pattern 4 — Queue Using Two Stacks
```
Cue words: "implement queue using stacks", "FIFO from LIFO"
Cue situation: design question

Template:
Deque<Integer> inbox  = new ArrayDeque<>();
Deque<Integer> outbox = new ArrayDeque<>();

enqueue(val): inbox.push(val)

dequeue():
  pourIfNeeded()
  return outbox.pop()

pourIfNeeded():
  if (outbox.isEmpty()):
    while (!inbox.isEmpty()):
      outbox.push(inbox.pop())  // reversal gives FIFO

isEmpty(): inbox.isEmpty() && outbox.isEmpty()

Key insight: pour only when outbox empty — O(1) amortized
```

---

### Pattern 5 — BFS Using Queue
```
Cue words: "level by level", "level order", "minimum steps",
           "shortest path in unweighted graph"
Cue situation: process nodes layer by layer

Template:
if (root == null) return result;
Queue<TreeNode> queue = new LinkedList<>();
queue.offer(root);

while (!queue.isEmpty()) {
    int levelSize = queue.size();  // snapshot ← critical
    List<Integer> level = new ArrayList<>();

    for (int i = 0; i < levelSize; i++) {
        TreeNode node = queue.poll();
        level.add(node.val);
        if (node.left  != null) queue.offer(node.left);
        if (node.right != null) queue.offer(node.right);
    }
    result.add(level);
}

Key insight: levelSize snapshot separates levels
```

---

## Problems Done vs Pending

| Problem | Pattern | Status |
|---|---|---|
| Valid parentheses | Matching | ✅ |
| Min Stack | Min/Max Stack | ✅ |
| Queue using two stacks | Two Stack Queue | ✅ |
| Next greater element | Monotonic Stack | ✅ |
| Largest rectangle histogram | Monotonic Stack | ⏸ Parked |
| BFS level order traversal | BFS Queue | ✅ |

---

## Pattern Recognition Nudges — Stacks & Queues

```
"valid/balanced brackets"    → Matching stack
"next greater element"       → Monotonic decreasing stack
"next smaller element"       → Monotonic increasing stack
"previous greater"           → Monotonic stack scan right to left
"O(1) getMin/getMax"         → Two stacks in sync
"implement queue"            → Two stacks, inbox/outbox
"level by level"             → BFS with queue + levelSize snapshot
"shortest path unweighted"   → BFS with queue
"stock span problem"         → Monotonic stack
"daily temperatures"         → Monotonic stack
"largest rectangle"          → Monotonic stack
```

---

# Part 3 — HashMaps

## Patterns & Templates

### Pattern 1 — Frequency Count
```
Cue words: "most frequent", "count occurrences", "anagram",
           "duplicates", "top k"
Cue situation: need to count how many times each element appears

Template:
Map<Integer, Integer> freq = new HashMap<>();
for (int num : arr) {
    freq.compute(num, (k,v) -> v == null ? 1 : v + 1);
}
// or cleaner:
freq.merge(num, 1, Integer::sum);
```

---

### Pattern 2 — Complement Lookup
```
Cue words: "pair summing to", "two elements equal target",
           "find two numbers"
Cue situation: for each element find its partner

Template:
Map<Integer, Integer> seen = new HashMap<>();  // val → index

for (int i = 0; i < arr.length; i++) {
    int complement = target - arr[i];
    if (seen.containsKey(complement))
        return new int[]{seen.get(complement), i};
    seen.put(arr[i], i);  // store AFTER lookup
}

Critical: lookup before store — avoids using same element twice
```

---

### Pattern 3 — Grouping
```
Cue words: "group by", "anagrams together", "same property",
           "categorize"
Cue situation: elements sharing a property belong together

Template:
Map<String, List<String>> groups = new HashMap<>();

for (String s : arr) {
    String key = computeKey(s);  // sorted string, first char, frequency
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
}
return new ArrayList<>(groups.values());

Critical method: computeIfAbsent — get or create in one line
```

---

### Pattern 4 — Prefix Sum + HashMap
```
Cue words: "subarray sum equals", "contiguous sum",
           "number of subarrays summing to"
Cue situation: count/find subarrays with exact sum property
           works with NEGATIVES — sliding window doesn't

Template:
Map<Integer, Integer> prefixCount = new HashMap<>();
prefixCount.put(0, 1);  // sentinel ← never forget
int currSum = 0, count = 0;

for (int num : arr) {
    currSum += num;
    count += prefixCount.getOrDefault(currSum - k, 0);
    prefixCount.merge(currSum, 1, Integer::sum);
}

For divisibility variant:
  int remainder = ((currSum % k) + k) % k;  // handles negatives
  count += prefixCount.getOrDefault(remainder, 0);
  prefixCount.merge(remainder, 1, Integer::sum);

Critical: sentinel {0:1} handles subarrays starting at index 0
```

---

### Pattern 5 — Sliding Window + HashMap
```
Cue words: "longest substring", "window with at most k",
           "no repeating characters"
Cue situation: contiguous subarray/substring with character constraint
           NO negatives — window always expands/shrinks predictably

Template:
Map<Character, Integer> window = new HashMap<>();
int left = 0, maxLen = 0;

for (int right = 0; right < s.length(); right++) {
    window.merge(s.charAt(right), 1, Integer::sum);  // expand

    while (window invalid) {                          // shrink
        char lc = s.charAt(left);
        window.merge(lc, -1, Integer::sum);
        if (window.get(lc) == 0) window.remove(lc);  // clean zeros
        left++;
    }

    maxLen = Math.max(maxLen, right - left + 1);
}

Validity conditions:
  no repeats    → window.size() > 1 (any char appears twice)
  k distinct    → window.size() > k
  custom        → depends on problem
```

---

## Problems Done vs Pending

| Problem | Pattern | Status |
|---|---|---|
| Two Sum | Complement lookup | ✅ |
| Group Anagrams | Grouping | ✅ |
| Longest Consecutive | HashSet lookup | ✅ |
| Top K Frequent | Frequency + Heap | ✅ |
| Subarray Sum = k | Prefix + HashMap | ✅ |
| Subarray Divisible K | Prefix + HashMap | ✅ |
| Longest no repeat | Sliding + HashMap | ✅ |
| At most K distinct | Sliding + HashMap | ✅ |
| Minimum window substring | Sliding + HashMap | ⏸ Parked |

---

## Pattern Recognition Nudges — HashMaps

```
"count/frequency"           → freq.merge(key, 1, Integer::sum)
"pair summing to target"    → complement = target - num, lookup map
"group by property"         → computeIfAbsent + sorted key
"subarray sum = k"          → prefix sum + map + sentinel {0:1}
"subarray divisible by k"   → remainder + map + negative fix
"longest substring"         → sliding window + map + left pointer
"top k frequent"            → freq map + min heap size k
"first unique"              → freq map + second scan
"consecutive sequence"      → HashSet + only count from starts
```

---

## The Three Critical Rules — Apply Every Time

```
1. Sentinel {0:1} in prefix sum problems
   → handles subarrays starting at index 0

2. Lookup before store in complement problems
   → avoids using same element twice

3. Clean zero counts in sliding window
   → if (map.get(c) == 0) map.remove(c)
   → keeps map.size() accurate for validity check
```

---

## Universal Habits — Every Single Solution

```java
// 1. Always first line
if (arr == null || arr.length == 0) return defaultValue;

// 2. Always camelCase
longestSubarrayWithSumK  not  longest_subarray_with_sum_k

// 3. Overflow safety when summing
long sum = (long) arr[left] + arr[right];

// 4. Return stack.isEmpty() not return true
// 5. Push indices not values in monotonic stack
// 6. Single index for min stack — both stacks same size
// 7. pour only when outbox empty in two-stack queue
// 8. levelSize snapshot in BFS
```

---

Ready for **Graphs**?