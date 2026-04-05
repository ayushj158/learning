Totally fair — let me redo Arrays & Strings with the depth you need. No widget this time, pure markdown you can copy.

---

# Arrays & Strings — Deep Dive

## Pattern 1: Two Pointers

### The Core Insight

Most array problems that ask you to find a **pair, triplet, or partition** have an O(n²) brute force — try every combination. Two pointers eliminates the inner loop by exploiting **sorted order as a decision oracle**.

The key mental model: if your array is sorted and you're standing at both ends, the sum of those two elements tells you exactly which direction to move. You never need to backtrack.

```
sorted array: [1, 3, 5, 7, 9, 11, 13, 15]
               L                        R

sum = L + R
  if sum == target  → found a pair. Move BOTH inward.
  if sum > target   → sum is too big. Only fix: move R left (smaller value).
  if sum < target   → sum is too small. Only fix: move L right (larger value).
```

This works because in a sorted array, moving L right always increases the sum, moving R left always decreases it. You never miss a valid pair because you only skip elements that provably can't form the target.

### Java Template — Two Pointers

```java
int left = 0, right = arr.length - 1;

while (left < right) {
    int sum = arr[left] + arr[right];
    
    if (sum == target) {
        // process pair
        left++;
        right--;
        // skip duplicates if needed:
        while (left < right && arr[left] == arr[left - 1]) left++;
        while (left < right && arr[right] == arr[right + 1]) right--;
        
    } else if (sum < target) {
        left++;
    } else {
        right--;
    }
}
```

**Why `left < right` and not `left <= right`?** Because at `left == right` you're looking at the same element twice — a single number can't form a pair with itself (unless the problem allows reuse, which it will state explicitly).

### Complexity
- Time: O(n) — each pointer moves at most n steps total
- Space: O(1) — no auxiliary structure, just two index variables

### When to reach for this pattern
- "Find a pair / triplet with sum = target"
- "Check if array can be partitioned into pairs"
- "Remove duplicates in-place"
- "Container with most water"
- Any time the array is **sorted** (or you can sort it) and you need O(n)

---

## Pattern 2: Sliding Window

### The Core Insight

Two pointers is about opposite ends. Sliding window is about a **contiguous subarray** that moves in one direction. The key insight: when your window slides right by one position, 99% of the window is the same. Instead of recomputing the entire window, you:

```
new_window = old_window - element_leaving_left + element_entering_right
```

This converts an O(n×k) computation into O(n).

### Fixed vs Variable Window

**Fixed window** (window size k is given):
```
Entering right:  arr[right]
Leaving left:    arr[right - k]   (element that falls out as window slides)

sum = sum - arr[right - k] + arr[right]
```

**Variable window** (find the smallest/largest window satisfying a condition):
- Expand right until condition is met
- Shrink left until condition is violated
- Track min/max window size seen

### Java Template — Fixed Window

```java
// Find max sum subarray of size k
int windowSum = 0;
int maxSum = 0;

// Build first window
for (int i = 0; i < k; i++) {
    windowSum += arr[i];
}
maxSum = windowSum;

// Slide window
for (int right = k; right < arr.length; right++) {
    windowSum += arr[right];          // add incoming element
    windowSum -= arr[right - k];      // remove outgoing element
    maxSum = Math.max(maxSum, windowSum);
}
```

### Java Template — Variable Window

```java
// Longest substring without repeating characters
Map<Character, Integer> freq = new HashMap<>();
int left = 0, maxLen = 0;

for (int right = 0; right < s.length(); right++) {
    char c = s.charAt(right);
    freq.merge(c, 1, Integer::sum);  // freq.put(c, freq.getOrDefault(c,0)+1)
    
    // Shrink from left while window is invalid
    while (freq.get(c) > 1) {
        char leftChar = s.charAt(left);
        freq.merge(leftChar, -1, Integer::sum);
        left++;
    }
    
    maxLen = Math.max(maxLen, right - left + 1);
}
```

### The "at most K distinct" pattern

This is a very common variation in FSI interviews (think: "at most K different transaction types in a window"):

```java
while (map.size() > k) {
    // shrink left until we have at most k distinct elements
    char leftChar = s.charAt(left);
    map.merge(leftChar, -1, Integer::sum);
    if (map.get(leftChar) == 0) map.remove(leftChar);
    left++;
}
```

### Complexity
- Time: O(n) — right pointer moves n steps; left pointer also moves at most n steps total (amortized)
- Space: O(k) for fixed window; O(alphabet size) for string windows

### When to reach for this pattern
- "Subarray / substring of size k with max/min property"
- "Longest substring with at most k distinct characters"
- "Minimum window substring"
- Any time the word **contiguous** appears with a constraint

---

## Pattern 3: Prefix Sum

### The Core Insight

Prefix sum is a **precomputation trick**. You pay O(n) upfront to build a cumulative sum array, and then every range sum query becomes O(1).

```
arr:    [3,  1,  4,  2,  5,  7]
         0   1   2   3   4   5

prefix: [0,  3,  4,  8, 10, 15, 22]
         0   1   2   3   4   5   6

prefix[i] = sum of arr[0..i-1]
Note: prefix has length n+1, with prefix[0] = 0 (sentinel)

sum(arr[i..j]) = prefix[j+1] - prefix[i]

Example: sum(arr[2..4]) = prefix[5] - prefix[2] = 15 - 4 = 11
Check:   arr[2]+arr[3]+arr[4] = 4+2+5 = 11  ✓
```

### Building the prefix array in Java

```java
int n = arr.length;
int[] prefix = new int[n + 1];   // n+1 so prefix[0]=0 is the sentinel
prefix[0] = 0;

for (int i = 1; i <= n; i++) {
    prefix[i] = prefix[i - 1] + arr[i - 1];
}

// Range sum query [l, r] (0-indexed, both inclusive)
int rangeSum = prefix[r + 1] - prefix[l];
```

### The killer combination: Prefix Sum + HashMap

This unlocks "subarray sum equals k" in O(n) — one of the most commonly asked problems at Goldman and JPMC.

The idea: `sum(i..j) = k` means `prefix[j+1] - prefix[i] = k`, which means `prefix[i] = prefix[j+1] - k`. So as you scan right, check if `(currentSum - k)` has appeared before as a prefix sum.

```java
// Count subarrays with sum == k
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0, 1);   // empty prefix has sum 0, seen once
    
    int currentSum = 0;
    int count = 0;
    
    for (int num : nums) {
        currentSum += num;
        
        // How many times has (currentSum - k) appeared?
        // Each occurrence = one valid subarray ending here
        count += prefixCount.getOrDefault(currentSum - k, 0);
        
        prefixCount.merge(currentSum, 1, Integer::sum);
    }
    
    return count;
}
```

**Why `prefixCount.put(0, 1)` at the start?** Because if the entire array from index 0 to j sums to k, then `currentSum - k = 0`, and you need that 0 to already be in the map. Without this sentinel, you'd miss subarrays that start at index 0.

### Complexity
- Build: O(n) time, O(n) space
- Query: O(1) per query after build
- Prefix+HashMap combo: O(n) time, O(n) space

### When to reach for this pattern
- "Subarray sum equals k" (count or existence)
- "Number of subarrays divisible by k" (use prefix[i] % k)
- Multiple range sum queries on the same array
- 2D matrix problems: "number of submatrices summing to target"

---

## Pattern 4: Binary Search on Answer Space

### The Core Insight

Classic binary search finds a **value in a sorted array**. This pattern flips it: you binary search on **the answer itself**, which exists on an implicit number line that is monotone — meaning if answer X is feasible, all answers > X are also feasible (or vice versa).

The mental model:
```
Answer space:  [lo .............. boundary .............. hi]
               infeasible region | feasible region

You binary search for the leftmost feasible point.

At each mid:
  if feasible(mid)  → boundary is at mid or left of mid → hi = mid
  if infeasible(mid) → boundary is right of mid → lo = mid + 1

When lo == hi, that's your answer.
```

### Java Template

```java
int lo = minPossibleAnswer;
int hi = maxPossibleAnswer;

while (lo < hi) {
    int mid = lo + (hi - lo) / 2;   // avoids integer overflow vs (lo+hi)/2
    
    if (feasible(mid)) {
        hi = mid;           // mid works, but maybe something smaller works too
    } else {
        lo = mid + 1;       // mid doesn't work, need strictly larger
    }
}

return lo;  // lo == hi == answer
```

**Why `lo + (hi - lo) / 2` instead of `(lo + hi) / 2`?** Integer overflow. If `lo = 2×10⁹` and `hi = 2×10⁹`, their sum overflows int. The subtraction form is always safe.

### Example: Minimum capacity to ship packages in D days

```java
public int shipWithinDays(int[] weights, int days) {
    int lo = 0, hi = 0;
    for (int w : weights) {
        lo = Math.max(lo, w);    // must carry the heaviest package
        hi += w;                  // worst case: carry all in one day
    }
    
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (canShip(weights, days, mid)) {
            hi = mid;
        } else {
            lo = mid + 1;
        }
    }
    return lo;
}

private boolean canShip(int[] weights, int days, int capacity) {
    int daysNeeded = 1;
    int currentLoad = 0;
    
    for (int w : weights) {
        if (currentLoad + w > capacity) {
            daysNeeded++;
            currentLoad = 0;
        }
        currentLoad += w;
    }
    
    return daysNeeded <= days;
}
```

**Why `lo = max(weights)` as lower bound?** Because if capacity is less than the heaviest package, that package can never be shipped — the problem is immediately infeasible.

### Complexity
- O(n log(answerRange)) — log factor from binary search, n from feasibility check
- Answer range is usually sum(arr) - max(arr), which is at most 10⁸ → ~27 iterations of binary search

### When to reach for this pattern
- "Minimum X such that some condition holds"
- "Maximum X such that some condition holds"  
- "Koko eating bananas", "Split array largest sum", "Aggressive cows"
- Any time you catch yourself thinking "I'll try each possible answer" — that's the signal

---

# Drilling — Question 1 (Two Pointers)

**Find all unique pairs in a sorted array that sum to a target.**

```
Input:  arr = [1, 3, 5, 7, 9, 11, 13, 15], target = 16
Output: [[1,15], [3,13], [5,11], [7,9]]
```

**Constraints:** O(n) time, O(1) space. Array is sorted. No duplicate pairs.

Write your Java solution here. After you share it I'll review line by line — correctness, edge cases, code style for a senior candidate, and then we move to Q2 (medium difficulty).


Got it — let's stay on two pointers and go deeper. Here's the progression: each question adds a new wrinkle on top of the last.

---

# Two Pointers — Full Drill Set

## Question 2 — Remove Duplicates from Sorted Array In-Place

**"Given a sorted array, remove duplicates in-place so each element appears only once. Return the count of unique elements. Do not allocate extra space."**

```
Input:  [1, 1, 2, 3, 3, 3, 4, 5, 5]
Output: 5  (array modified to [1, 2, 3, 4, 5, _, _, _, _])

Input:  [1, 1, 1, 1]
Output: 1

Input:  []
Output: 0
```

**Constraints:** O(n) time, O(1) space. Modify array in-place. You don't care what sits beyond index `k` (the unique count) in the array.

**The mindset shift from Q1:**
Q1 had two pointers moving toward each other from opposite ends. This one is different — both pointers move in the **same direction**, but at different speeds. One pointer (`slow`) marks where the next unique element should be written. The other (`fast`) scans ahead looking for the next unique value.

This is called the **slow-fast pointer** variant. Draw it out:

```
arr = [1, 1, 2, 3, 3, 3, 4, 5, 5]
       s
       f

fast finds a value different from slow → write it at slow+1 → advance slow
fast finds same value as slow          → skip it, advance fast only
```

Write your Java solution. Focus on:
- Where do `slow` and `fast` start?
- What is the condition to write vs skip?
- What do you return?

---

## Question 3 — Container With Most Water (after Q2)

**"Given an array where `height[i]` is the height of a vertical line at position i, find two lines that together with the x-axis forms a container that holds the most water."**

```
Input:  [1, 8, 6, 2, 5, 4, 8, 3, 7]
Output: 49

Explanation:
Lines at index 1 (height 8) and index 8 (height 7)
Width = 8 - 1 = 7
Height = min(8, 7) = 7
Area = 7 × 7 = 49
```

**Constraints:** O(n) time, O(1) space.

**The key insight you need to discover:** At each step you have a left and right pointer. The area is `min(height[L], height[R]) × (R - L)`. If you move the **taller** pointer inward, can the area ever increase? Think carefully about why the answer is no — and therefore which pointer you must move.

---

## Question 4 — Three Sum (after Q3)

**"Given an array of integers, find all unique triplets that sum to zero."**

```
Input:  [-4, -1, -1, 0, 1, 2]
Output: [[-1, -1, 2], [-1, 0, 1]]

Input:  [0, 0, 0, 0]
Output: [[0, 0, 0]]

Input:  [1, 2, 3]
Output: []
```

**Constraints:** O(n²) time. No duplicate triplets in output.

**The pattern:** This is two pointers inside an outer loop. Fix one element, run two-pointer on the rest. The tricky part is duplicate skipping — you have three places where duplicates can occur, not just two.

---

## Question 5 — Trapping Rain Water (hardest two-pointer problem)

**"Given an elevation map, compute how much water it can trap after raining."**

```
Input:  [0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1]
Output: 6

Visual:
        |
    |   ||  |
_|_||_|||||||
0 1 0 2 1 0 1 3 2 1 2 1
```

**Constraints:** O(n) time, O(1) space.

**Why this is hard:** The brute force is O(n²) — for each position, scan left for max height and scan right for max height. The O(n) prefix array approach uses O(n) space. The two-pointer solution gets O(1) space by realising you don't need both maxes simultaneously — you only need the smaller one to determine water level at any position.

---

# Sliding Window — Start Here
## Concept Recap (the one thing to internalize before Q1)

> Two pointers worked on sorted arrays with a target condition. Sliding window works on contiguous subarrays/substrings where you maintain a window that satisfies a constraint. The window expands right freely, and shrinks from left only when the constraint is violated.
The universal template in your head should be:

```
javaint left = 0;
Map/Set/Array windowState;   // tracks what's inside the window

for (int right = 0; right < n; right++) {
    // 1. Add arr[right] into window state
    
    // 2. Shrink from left while window is INVALID
    while (window is invalid) {
        // remove arr[left] from window state
        left++;
    }
    
    // 3. Window is now valid — update answer
    maxLen = Math.max(maxLen, right - left + 1);
}
```

Everything else is a variation of this skeleton.

---