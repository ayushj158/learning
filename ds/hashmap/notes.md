# HashMaps — Deep Dive

## What Is a HashMap?

A HashMap stores **key-value pairs** with O(1) average get, put, and remove.

```
map.put("AAPL", 150)    // store
map.get("AAPL")         // retrieve → 150
map.remove("AAPL")      // delete
map.containsKey("AAPL") // check → true
```

Real world — directly relevant to your Lloyds work:
```
Account number → Balance
Transaction ID → Transaction details
Currency code  → Exchange rate
```

---

## How It Works Internally — What Goldman Will Ask

```
Key → hashCode() → compressed to array index → store value there

"AAPL".hashCode() = 2000123
2000123 % arraySize = 47    ← bucket index
store 150 at index 47
```

**Collision** — two keys hash to same index:

```
"AAPL" → index 47
"MSFT" → index 47  ← collision

Solution 1 — Chaining:
  each bucket holds a LinkedList
  index 47 → [("AAPL",150) → ("MSFT",280)]

Solution 2 — Open Addressing:
  find next empty slot
  index 47 taken → try 48 → try 49...
```

Java's HashMap uses **chaining**. From Java 8, when a chain exceeds 8 nodes it converts to a **Red-Black Tree** for O(log n) worst case instead of O(n).

---

## Java HashMap Internals — Senior EM Depth

```
Initial capacity:  16 buckets
Load factor:       0.75
Resize trigger:    when size > capacity × 0.75
                   (12 entries → resize to 32)
Resize cost:       O(n) — rehash all entries
TreeNode threshold: 8 — chain becomes tree
Untree threshold:   6 — tree reverts to chain
```

**Why 0.75 load factor?** Balance between memory and collision rate. Higher → more collisions, slower. Lower → more memory wasted.

**What to say in interview:**
> "Java HashMap is O(1) average for get/put. Worst case is O(n) with many collisions, but Java 8 mitigates this by converting long chains to Red-Black Trees giving O(log n) worst case. Resizing is O(n) but amortized O(1) per operation."

---

## The Five Patterns

```
Pattern 1 — Frequency Count
  count occurrences of each element
  signal: "most frequent", "anagram", "duplicates"

Pattern 2 — Two Sum / Complement Lookup
  store seen elements, look up complement
  signal: "pair summing to", "two elements that"

Pattern 3 — Grouping
  group elements by some property
  signal: "group anagrams", "group by"

Pattern 4 — Prefix Sum + HashMap
  already covered — subarray sum = k
  signal: "subarray sum", "contiguous elements summing to"

Pattern 5 — Sliding Window + HashMap
  already covered — at most k distinct
  signal: "substring with", "window containing"
```

Patterns 4 and 5 you've already done. Today we drill 1, 2, 3.

---

## Question 1 — Two Sum

**"Given an array and target, return indices of two numbers that add up to target."**

```
Input:  arr=[2,7,11,15], target=9
Output: [0,1]  (arr[0]+arr[1] = 2+7 = 9)

Input:  arr=[3,2,4], target=6
Output: [1,2]

Input:  arr=[3,3], target=6
Output: [0,1]
```

**Constraints:** O(n) time, O(n) space. Exactly one solution exists.

---

## The Insight

Brute force — try every pair → O(n²).

HashMap approach — for each element, ask: *"have I seen its complement before?"*

```
target = 9
arr = [2, 7, 11, 15]

i=0, val=2: complement = 9-2 = 7, seen 7? NO → store {2:0}
i=1, val=7: complement = 9-7 = 2, seen 2? YES at index 0 → return [0,1] ✅
```

You store `value → index` so you can return both indices.

---

## Write It

```java
public static int[] twoSum(int[] arr, int target) {

}
```

**Think through:**
- What is the key in the map? What is the value?
- Do you look up before or after storing?
- What do you return if no pair found?

Post when ready.

# Code Review — Two Sum

## Verdict: Perfect ✅

Clean, correct, complete. This is the canonical solution.

---

## Correctness Check

```java
int complement = target - nums[i];                          // ✅ compute complement
if (map.containsKey(complement))                            // ✅ seen before?
    return new int[]{map.get(complement), i};               // ✅ return both indices
map.put(nums[i], i);                                        // ✅ store after lookup
```

Every line right. Lookup before store — correct order.

---

## Only One Thing Missing

```java
if (nums == null || nums.length < 2) return new int[]{-1, -1};
```

---

## What To Say Out Loud

> "For each element I compute its complement — target minus current value. I check if that complement exists in the map from previous elements. If yes, I have my pair. If no, I store current value and its index. Lookup before store ensures I don't use the same element twice. O(n) time, O(n) space."

---

## Score Card

| Dimension | Your Solution | Senior Bar |
|---|---|---|
| Core logic | ✅ Perfect | ✅ |
| Lookup before store | ✅ Correct | ✅ |
| Return type | ✅ Correct | ✅ |
| Null guard | ❌ Missing | ✅ |
| No pair found | ✅ returns -1,-1 | ✅ |

---

# HashMap Q2 — Group Anagrams

**"Given an array of strings, group anagrams together."**

```
Input:  ["eat","tea","tan","ate","nat","bat"]
Output: [["eat","tea","ate"], ["tan","nat"], ["bat"]]

Input:  [""]
Output: [[""]]

Input:  ["a"]
Output: [["a"]]
```

**Constraints:** O(n × k log k) time where k = max string length.

---

## The Insight

Two strings are anagrams if they have the same characters in the same frequencies. The key insight:

```
"eat" → sorted → "aet"
"tea" → sorted → "aet"   ← same key
"ate" → sorted → "aet"   ← same key

Use sorted string as HashMap key
All anagrams map to same key → group them together
```

**Map stores:** `sortedString → List of original strings`

```
{
  "aet" → ["eat", "tea", "ate"]
  "ant" → ["tan", "nat"]
  "abt" → ["bat"]
}
```

Post your solution when ready.

# Code Review — Group Anagrams

## Verdict: Pass ✅ — Two Simplifications Needed

Core logic is correct. Code is more verbose than it needs to be.

---

## Correctness Check

```java
char[] chars = key.toCharArray();
Arrays.sort(chars);
String sorted = new String(chars);          // ✅ sorted string as key

if (map.containsKey(sorted)) {             // ✅ group exists
    map.get(sorted).add(key);
} else {                                    // ✅ new group
    List<String> list = new ArrayList<>();
    list.add(key);
    map.put(sorted, list);
}
```

Logic is right. Every anagram maps to same sorted key, grouped correctly.

---

## Simplification 1 — Use `computeIfAbsent`

Your if/else block is 7 lines. Java HashMap has a method for exactly this pattern — get existing list or create new one:

```java
// Your version — 7 lines:
if (map.containsKey(sorted)) {
    List<String> curreList = map.get(sorted);
    curreList.add(key);
    map.put(sorted, curreList);
} else {
    List<String> curreList = new ArrayList<>();
    curreList.add(key);
    map.put(sorted, curreList);
}

// Clean version — 1 line:
map.computeIfAbsent(sorted, k -> new ArrayList<>()).add(key);
```

`computeIfAbsent(key, mappingFunction)`:
- If key exists → returns existing value
- If key missing → creates new value using function, stores it, returns it
- Then `.add(key)` adds to whichever list was returned

This is idiomatic Java — Goldman/JPMC engineers will expect this.

---

## Simplification 2 — Return Map Values Directly

```java
// Your version — extra loop:
for (Map.Entry<String, List<String>> entry : map.entrySet()) {
    result.add(entry.getValue());
}
return result;

// Clean version — one line:
return new ArrayList<>(map.values());
```

`map.values()` returns all values as a Collection. Wrap in `new ArrayList<>()` and return directly.

---

## Also — Unnecessary `map.put` After `map.get`

```java
List<String> curreList = map.get(sorted);
curreList.add(key);
map.put(sorted, curreList);   // ❌ unnecessary
```

`map.get()` returns a reference to the actual list in the map — not a copy. When you call `curreList.add(key)`, you're modifying the list that's already in the map. No need to `put` it back.

---

## Corrected Solution

```java
public static List<List<String>> groupAnagrams(String[] strs) {
    if (strs == null || strs.length == 0) return new ArrayList<>();

    Map<String, List<String>> map = new HashMap<>();

    for (String str : strs) {
        char[] chars = str.toCharArray();
        Arrays.sort(chars);
        String sorted = new String(chars);

        map.computeIfAbsent(sorted, k -> new ArrayList<>()).add(str);
    }

    return new ArrayList<>(map.values());
}
```

---

## Trace

```
strs = ["eat","tea","tan","ate","nat","bat"]

"eat" → sorted="aet" → map={"aet":["eat"]}
"tea" → sorted="aet" → map={"aet":["eat","tea"]}
"tan" → sorted="ant" → map={"aet":["eat","tea"], "ant":["tan"]}
"ate" → sorted="aet" → map={"aet":["eat","tea","ate"], "ant":["tan"]}
"nat" → sorted="ant" → map={"aet":["eat","tea","ate"], "ant":["tan","nat"]}
"bat" → sorted="abt" → map={"aet":[...], "ant":[...], "abt":["bat"]}

return map.values() = [["eat","tea","ate"],["tan","nat"],["bat"]] ✅
```

---

## Score Card

| Dimension | Your Solution | Senior Bar |
|---|---|---|
| Core logic | ✅ Correct | ✅ |
| Sorted key approach | ✅ Correct | ✅ |
| computeIfAbsent | ❌ manual if/else | ✅ |
| Unnecessary map.put | ⚠️ redundant | ✅ |
| Return map.values() | ❌ extra loop | ✅ |
| Null guard | ❌ Missing | ✅ |

---

## The One Method To Remember

```java
map.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
```

You'll use this in every grouping problem. Anagrams, grouping by first letter, grouping by frequency — always this pattern.

---

# HashMap Q3 — Longest Consecutive Sequence

**"Given an unsorted array, find the length of the longest consecutive sequence."**

```
Input:  [100, 4, 200, 1, 3, 2]
Output: 4   (sequence: 1,2,3,4)

Input:  [0, 3, 7, 2, 5, 8, 4, 6, 0, 1]
Output: 9   (sequence: 0,1,2,3,4,5,6,7,8)

Input:  [1]
Output: 1
```

**Constraints:** O(n) time. No sorting allowed — that would be O(n log n).

---

## The Insight

The trick — only start counting a sequence from its **actual start**. A number is a sequence start if `num-1` does not exist in the set.

```
arr = [100, 4, 200, 1, 3, 2]
set = {100, 4, 200, 1, 3, 2}

100: is 99 in set? NO → start sequence → 100,101? NO → length 1
4:   is 3 in set? YES → not a start → skip
200: is 199 in set? NO → start sequence → 200,201? NO → length 1
1:   is 0 in set? NO → start sequence → 1,2,3,4,5? → length 4 ✅
3:   is 2 in set? YES → not a start → skip
2:   is 1 in set? YES → not a start → skip

Answer: 4 ✅
```

**Why O(n)?** Each number is visited at most twice — once when checking if it's a start, once when extending a sequence from its start.

Post your solution when ready.

No problem. Let me break it down step by step before you code.

---

## The Problem With Brute Force

```
arr = [100, 4, 200, 1, 3, 2]

For each number, count how long a sequence starts there:
100 → 100,101,102...? check each one → O(n) per number
4   → 4,5,6...? check each one       → O(n) per number

Total: O(n²)
```

Checking "does 101 exist?" on an array is O(n). We need O(1) lookup — that's a HashSet.

---

## Step 1 — Put Everything in a HashSet

```
arr = [100, 4, 200, 1, 3, 2]
set = {100, 4, 200, 1, 3, 2}

Now "does X exist?" is O(1)
```

---

## Step 2 — Only Start Counting From Sequence Starts

Key insight — if `num-1` exists in the set, then `num` is not the start of a sequence. Someone before it already starts that sequence.

```
4: does 3 exist in set? YES → 4 is not a start → skip
3: does 2 exist in set? YES → 3 is not a start → skip
2: does 1 exist in set? YES → 2 is not a start → skip
1: does 0 exist in set? NO  → 1 IS a start → count from here
```

This means you only do the counting work for actual sequence starts — never for middle or end elements.

---

## Step 3 — Count From Each Start

```
1 is a start:
  does 2 exist? YES → length=2
  does 3 exist? YES → length=3
  does 4 exist? YES → length=4
  does 5 exist? NO  → stop
  sequence length = 4

100 is a start:
  does 101 exist? NO → stop
  sequence length = 1

200 is a start:
  does 201 exist? NO → stop
  sequence length = 1

maxLength = 4 ✅
```

---

## The Full Algorithm — Plain English

```
1. Put all numbers in a HashSet
2. For each number:
     if num-1 NOT in set → this is a sequence start
       count = 1
       while num+count exists in set → count++
       update maxLength
3. Return maxLength
```

---

## Now Write It

```java
public static int longestConsecutive(int[] nums) {
    // step 1 — build set
    // step 2 — find starts and count
    // step 3 — return max
}
```

Three steps, straight translation from the plain English above. Post when ready.

# HashMap Q4 — Top K Frequent Elements

**"Given an integer array, return the k most frequent elements."**

```
Input:  arr=[1,1,1,2,2,3], k=2
Output: [1,2]

Input:  arr=[1], k=1
Output: [1]

Input:  arr=[1,1,2,2,3], k=2
Output: [1,2]  (order doesn't matter)
```

**Constraints:** O(n log k) time. Better than O(n log n) sorting.

---

## The Insight — Two Steps

**Step 1 — Count frequencies:**
```
arr=[1,1,1,2,2,3]
freqMap = {1:3, 2:2, 3:1}
```

**Step 2 — Find top k by frequency:**

Naive — sort by frequency → O(n log n). Too slow.

Better — use a **min heap of size k**:

```
Keep a min heap of size k
  → heap always contains k most frequent so far
  → when heap exceeds k, remove the LEAST frequent
  → at end, heap contains exactly top k

Why min heap not max heap?
  → you want to EVICT the smallest frequency
  → min heap gives you smallest at top → easy to evict
```

Trace with k=2:

```
freqMap = {1:3, 2:2, 3:1}

process (1,freq=3): heap=[(1,3)]
process (2,freq=2): heap=[(2,2),(1,3)]
process (3,freq=1): heap size would be 3 > k=2
  → add (3,1) → heap=[(3,1),(1,3),(2,2)]
  → evict min freq → remove (3,1)
  → heap=[(2,2),(1,3)]

Result: [2,1] ✅
```

---

## Java Setup

```java
// Min heap by frequency
PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> a[1] - b[1]);
// each element is int[]{value, frequency}
// a[1] - b[1] → smaller frequency at top → min heap by frequency
```

---

## Write It

```java
public static int[] topKFrequent(int[] arr, int k) {
    // step 1: build frequency map
    // step 2: min heap of size k
    // step 3: extract results from heap
}
```

**Three things to think through:**
- What does each heap element store — value, frequency, or both?
- When do you evict from the heap?
- How do you extract the final answer from the heap?

Post when ready.


# Code Review — Top K Frequent Elements

## Verdict: Wrong Approach ⚠️

Logic has good instincts but the stream/sort approach has bugs and misses the O(n log k) requirement. Let me break it down.

---

## Bug 1 — Sorting Ascending But Reading From Front

```java
.sorted((a,b) -> a.getValue() - b.getValue())  // ❌ ascending order
```

This sorts lowest frequency first. Then you read from index 0 — getting least frequent, not most frequent.

Fix would be `b.getValue() - a.getValue()` for descending. But even then...

---

## Bug 2 — `freqToKey` Map Loses Duplicate Frequencies

```java
Map<Integer,Integer> freqToKey = ...
    .collect(Collectors.toMap(set -> set.getValue(), set -> set.getKey()));
```

Key is frequency, value is element. What if two elements have the same frequency?

```
arr=[1,1,2,2,3], k=2
freqMap = {1:2, 2:2, 3:1}

freqToKey tries to map:
  2 → 1
  2 → 2   ← duplicate key! overwrites previous → loses element 1 ❌
```

`toMap` throws `IllegalStateException` on duplicate keys unless you provide a merge function.

---

## Bug 3 — O(n log n) Not O(n log k)

Sorting all elements is O(n log n). The requirement is O(n log k) — heap of size k is the right approach.

For large n and small k this matters significantly:
```
n=1,000,000, k=10
O(n log n) = 20,000,000 operations
O(n log k) =  3,321,928 operations  ← 6x faster
```

---

## What's Good

```java
keyToFreq.compute(item, (k,v) -> v==null ? 1 : v+1);  // ✅ elegant frequency count
```

`compute` is a clean way to build frequency maps. Better than `getOrDefault`. Keep this.

---

## Correct Approach — Min Heap

```java
public static int[] topKFrequent(int[] arr, int k) {
    if (arr == null || arr.length == 0) return new int[0];

    // Step 1 — frequency map
    Map<Integer, Integer> freqMap = new HashMap<>();
    for (int num : arr) {
        freqMap.compute(num, (key, val) -> val == null ? 1 : val + 1);
    }

    // Step 2 — min heap of size k, ordered by frequency
    PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> a[1] - b[1]);
    // each element: int[]{value, frequency}

    for (Map.Entry<Integer, Integer> entry : freqMap.entrySet()) {
        heap.offer(new int[]{entry.getKey(), entry.getValue()});
        if (heap.size() > k) {
            heap.poll();   // evict least frequent
        }
    }

    // Step 3 — extract results
    int[] result = new int[k];
    for (int i = k - 1; i >= 0; i--) {
        result[i] = heap.poll()[0];   // poll gives least frequent first
    }

    return result;
}
```

---

## Trace

```
arr=[1,1,1,2,2,3], k=2
freqMap={1:3, 2:2, 3:1}

Process entry (1,3): heap=[(1,3)], size=1 ≤ 2
Process entry (2,2): heap=[(2,2),(1,3)], size=2 ≤ 2
Process entry (3,1): heap=[(3,1),(2,2),(1,3)], size=3 > 2
  → poll min freq → remove (3,1)
  → heap=[(2,2),(1,3)]

Extract:
i=1: poll (2,2) → result[1]=2
i=0: poll (1,3) → result[0]=1

result=[1,2] ✅
```

---

## Alternative — Bucket Sort O(n)

If interviewer pushes for O(n), use bucket sort. Frequency can be at most n, so create n+1 buckets:

```java
public static int[] topKFrequentBucket(int[] arr, int k) {
    Map<Integer, Integer> freqMap = new HashMap<>();
    for (int num : arr)
        freqMap.compute(num, (key, val) -> val == null ? 1 : val + 1);

    // bucket[i] = list of elements with frequency i
    List<Integer>[] bucket = new List[arr.length + 1];
    for (Map.Entry<Integer, Integer> entry : freqMap.entrySet()) {
        int freq = entry.getValue();
        if (bucket[freq] == null) bucket[freq] = new ArrayList<>();
        bucket[freq].add(entry.getKey());
    }

    // collect top k from highest frequency buckets
    int[] result = new int[k];
    int idx = 0;
    for (int freq = bucket.length - 1; freq >= 0 && idx < k; freq--) {
        if (bucket[freq] != null) {
            for (int num : bucket[freq]) {
                result[idx++] = num;
                if (idx == k) break;
            }
        }
    }
    return result;
}
```

---

## Three Approaches — Know All Three

| Approach | Time | Space | When to mention |
|---|---|---|---|
| Sort by frequency | O(n log n) | O(n) | Never — too slow |
| Min heap size k | O(n log k) | O(n) | Default answer |
| Bucket sort | O(n) | O(n) | When pushed for optimal |

---

## Score Card

| Dimension | Your Solution | Senior Bar |
|---|---|---|
| Frequency count | ✅ compute() elegant | ✅ |
| Duplicate frequency handling | ❌ map key collision | ✅ |
| Sort direction | ❌ ascending not descending | ✅ |
| Time complexity | ❌ O(n log n) | ✅ O(n log k) |
| Heap approach | ❌ not used | ✅ |
| Null guard | ❌ Missing | ✅ |

---

## HashMap Progress

| Question | Status |
|---|---|
| Two Sum | ✅ |
| Group Anagrams | ✅ |
| Longest Consecutive Sequence | ✅ |
| Top K Frequent Elements | ✅ reviewed |

---
Exactly right. Let me confirm with a clean summary.

---

## Comparator — Final Reference

```
(a, b) -> a - b    →  ascending  →  smallest at top  →  MIN heap
(a, b) -> b - a    →  descending →  largest at top   →  MAX heap
```

---

## Concrete Proof

```java
PriorityQueue<Integer> minHeap = new PriorityQueue<>((a, b) -> a - b);
minHeap.offer(3);
minHeap.offer(1);
minHeap.offer(2);

minHeap.poll() → 1   ← smallest first ✅
minHeap.poll() → 2
minHeap.poll() → 3

PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
maxHeap.offer(3);
maxHeap.offer(1);
maxHeap.offer(2);

maxHeap.poll() → 3   ← largest first ✅
maxHeap.poll() → 2
maxHeap.poll() → 1
```

---

## Applied To Top K Problem

```
You want TOP K frequent → largest frequencies first → MAX heap → (a,b) -> b-a
You want BOTTOM K      → smallest frequencies first → MIN heap → (a,b) -> a-b
```

---

## Memory Trick — One Sentence

```
a-b → a wins when smaller → MIN heap
b-a → b wins when smaller → a wins when LARGER → MAX heap
```

Got it locked in now? Graphs next?

Ready for **Graphs** or one more HashMap question?