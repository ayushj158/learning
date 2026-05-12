## Interval Problems — Master Notes

---

### Your 4 solved problems + pattern

| Problem | Core Operation | Key Decision |
|---|---|---|
| Merge Intervals | union (expand) | `max` of ends |
| Insert Interval | union (expand) | 3 phases: before, merge, after |
| Minimum Arrows | intersection (shrink) | `min` of ends |
| Summary Ranges | consecutive detection | `nums[i] != nums[i-1]+1` |

---

### The one insight that connects merge vs arrows

```
MERGE INTERVALS — want the UNION (how far does overlap span?)
  rightRange = Math.max(right, next.end)   ← grow the range

MINIMUM ARROWS — want the INTERSECTION (where do ALL overlap?)
  right = Math.min(right, next.end)        ← shrink the range

Example: [1,6] [2,8]
  Union:        [1,8]  ← merge intervals answer
  Intersection: [2,6]  ← arrows answer (single point x=3 bursts both)
```

---

### Template — interval problems

```java
// STEP 1: always sort by start (safe comparator)
Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));

// STEP 2: walk with outer + inner while
int i = 0;
while (i < n) {
    int left  = intervals[i][0];
    int right = intervals[i][1];

    while (i + 1 < n && right >= intervals[i+1][0]) {
        // MERGE:  right = Math.max(right, intervals[i+1][1])  ← union
        // ARROWS: right = Math.min(right, intervals[i+1][1])  ← intersection
        //         left  = Math.max(left,  intervals[i+1][0])  ← intersection start
        i++;
    }

    // do something with [left, right]
    i++;
}
```

---

### Why `Integer.compare` not subtraction

```java
// NEVER — overflows with large/negative values
(a, b) -> a[0] - b[0]

// ALWAYS
(a, b) -> Integer.compare(a[0], b[0])
```

---

### All interval overlap conditions

```
A: [a1, a2]
B: [b1, b2]

A ends before B starts:   a2 < b1   → NO overlap
A starts after B ends:    a1 > b2   → NO overlap
everything else           → OVERLAP

Overlap condition:  a2 >= b1 AND a1 <= b2
After sorting by start, only need: a2 >= b1
```

---

### Types of interval problems

```
1. MERGE — combine overlapping into one
   → Math.max for end, leftRange stays (sorted guarantees min start)
   → output: list of merged intervals

2. INSERT — add one interval, re-merge
   → 3 phases: add before, merge overlapping, add after
   → no sort needed (already sorted)

3. MINIMUM ARROWS / MEETING ROOMS II — how many groups needed
   → Math.min for end (intersection)
   → output: count of non-overlapping groups

4. SUMMARY RANGES — find consecutive sequences
   → no sorting needed if already sorted
   → detect break: nums[i] != nums[i-1] + 1

5. MEETING ROOMS I — can one person attend all?
   → after sort, check if any intervals[i][0] < intervals[i-1][1]
   → output: boolean

6. NON-OVERLAPPING INTERVALS — min removals to make non-overlapping
   → greedy: keep interval with smallest end
   → count removals when overlap found
```

---

### Your arrows solution — what you got right

```java
// ✅ correct: intersection not union
left  = Math.max(left,  points[i+1][0]);  // tighten left
right = Math.min(right, points[i+1][1]);  // tighten right

// ✅ correct: overlap condition
right >= points[i+1][0]

// ✅ correct: answer = number of non-overlapping groups
return overlap.size();

// ✅ correct: Integer.compare to avoid overflow
```

---

### Quick decision guide for new interval problems

```
asked for: merged list          → union  (Math.max end)
asked for: min arrows/groups    → intersection (Math.min end)
asked for: can attend all?      → check no overlap after sort
asked for: min removals?        → greedy keep smallest end
asked for: ranges from array?   → consecutive gap detection
```


https://leetcode.com/problems/minimum-number-of-arrows-to-burst-balloons/?envType=study-plan-v2&envId=top-interview-150