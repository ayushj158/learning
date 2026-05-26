## DP — From Scratch

Before any problem — let me explain what DP actually is in plain English.

---

## What is DP?

DP = solving a big problem by breaking it into smaller subproblems and **reusing** their answers.

The key insight:

```
"The answer to problem(n) depends on 
 answers to smaller versions of the same problem"
```

---

## Simplest example — Fibonacci

```
fib(5) = fib(4) + fib(3)
fib(4) = fib(3) + fib(2)
fib(3) = fib(2) + fib(1)
fib(2) = fib(1) + fib(0)
fib(1) = 1
fib(0) = 0
```

Naive recursion — recalculates same thing many times:

```
fib(5)
  fib(4)
    fib(3)
      fib(2) ← calculated here
    fib(2)   ← calculated AGAIN
  fib(3)     ← calculated AGAIN
```

DP — calculate once, store result, reuse:

```
dp[0] = 0
dp[1] = 1
dp[2] = dp[1] + dp[0] = 1
dp[3] = dp[2] + dp[1] = 2
dp[4] = dp[3] + dp[2] = 3
dp[5] = dp[4] + dp[3] = 5
```

---

## Three steps to solve ANY DP problem

```
Step 1: Define dp[i]
        "What does dp[i] mean?"
        This is the hardest part.

Step 2: Find the recurrence
        "How does dp[i] relate to smaller dp values?"
        dp[i] = some function of dp[i-1], dp[i-2]...

Step 3: Base case
        "What is dp[0] or dp[1]?"
        The starting point everything builds from.
```

---

## Now — Problem 1: Unique Paths (LC 62)

> A robot is on an `m x n` grid. It starts at top-left and wants to reach bottom-right. It can only move **right** or **down**. How many unique paths exist?

```
Input:  m=3, n=7
Output: 28

Input:  m=3, n=2
Output: 3
```

For m=3, n=2 grid:
```
S . 
. .
. E

Three paths:
Right→Down→Down
Down→Right→Down
Down→Down→Right
```

---

## Before I explain — one question

To reach any cell `(r, c)` — which cells could the robot have come from?

```
? → (r,c)
```

Only two options — think about it.

# Problems 

1. https://leetcode.com/problems/unique-paths/
## DP — From Scratch

Before any problem — let me explain what DP actually is in plain English.

---

## What is DP?

DP = solving a big problem by breaking it into smaller subproblems and **reusing** their answers.

The key insight:

```
"The answer to problem(n) depends on 
 answers to smaller versions of the same problem"
```

---

## Simplest example — Fibonacci

```
fib(5) = fib(4) + fib(3)
fib(4) = fib(3) + fib(2)
fib(3) = fib(2) + fib(1)
fib(2) = fib(1) + fib(0)
fib(1) = 1
fib(0) = 0
```

Naive recursion — recalculates same thing many times:

```
fib(5)
  fib(4)
    fib(3)
      fib(2) ← calculated here
    fib(2)   ← calculated AGAIN
  fib(3)     ← calculated AGAIN
```

DP — calculate once, store result, reuse:

```
dp[0] = 0
dp[1] = 1
dp[2] = dp[1] + dp[0] = 1
dp[3] = dp[2] + dp[1] = 2
dp[4] = dp[3] + dp[2] = 3
dp[5] = dp[4] + dp[3] = 5
```

---

## Three steps to solve ANY DP problem

```
Step 1: Define dp[i]
        "What does dp[i] mean?"
        This is the hardest part.

Step 2: Find the recurrence
        "How does dp[i] relate to smaller dp values?"
        dp[i] = some function of dp[i-1], dp[i-2]...

Step 3: Base case
        "What is dp[0] or dp[1]?"
        The starting point everything builds from.
```

---

## Now — Problem 1: Unique Paths (LC 62)

> A robot is on an `m x n` grid. It starts at top-left and wants to reach bottom-right. It can only move **right** or **down**. How many unique paths exist?

```
Input:  m=3, n=7
Output: 28

Input:  m=3, n=2
Output: 3
```

For m=3, n=2 grid:
```
S . 
. .
. E

Three paths:
Right→Down→Down
Down→Right→Down
Down→Down→Right
```

---

## Before I explain — one question

To reach any cell `(r, c)` — which cells could the robot have come from?

```
? → (r,c)
```

Only two options — think about it.

Perfect — completely correct. You solved DP without knowing DP! 🎉

---

## What you did — the three steps perfectly

**Step 1 — defined dp[r][c]:**
```
dp[r][c] = number of unique paths to reach cell (r,c)
```

**Step 2 — recurrence:**
```java
dp[row][col] = dp[row-1][col]  // came from above
             + dp[row][col-1]  // came from left
```

**Step 3 — base cases:**
```java
dp[row][0] = 1  // only one way to reach any cell in col 0 — go down
dp[0][col] = 1  // only one way to reach any cell in row 0 — go right
```

---

## Trace for m=3, n=2

```
After base cases:
[1, 1]
[1, 0]
[1, 0]

Fill:
dp[1][1] = dp[0][1] + dp[1][0] = 1+1 = 2
dp[2][1] = dp[1][1] + dp[2][0] = 2+1 = 3

Final dp:
[1, 1]
[1, 2]
[1, 3]

return dp[2][1] = 3 ✓
```

---

## What to say in interview

*"dp[r][c] = unique paths to reach cell (r,c). Can only arrive from above or left — so dp[r][c] = dp[r-1][c] + dp[r][c-1]. Base case: first row and column all 1s — only one way to reach them."*

---

LC 62 ✅ — and you just learned DP fundamentals. 

Now **Coin Change (LC 322)** — same three steps, 1D array. Ready?
2. https://leetcode.com/problems/coin-change/


Good attempt — I can see you're trying to build a map of reachable amounts. But this approach has a fundamental flaw — it only considers pairs of coin combinations, not arbitrary combinations.

---

## Why your approach fails

```
coins = [1, 3, 4], amount = 6

Your map builds:
1→1, 2→2, 3→1, 4→1, 5→2 (via 1+4 or 3+2?)... 

Then you check pairs:
currAmount + delta = amount

But what about 3 coins? 1+1+4=6 needs THREE entries
You only check TWO entries at a time → misses combinations
```

---

## Let me teach DP approach properly

---

## Step 1 — define dp[i]

```
dp[i] = minimum coins needed to make amount i
```

---

## Step 2 — recurrence

To make amount `i` — last coin used could be any coin:

```
if last coin = coins[0]: dp[i] = dp[i - coins[0]] + 1
if last coin = coins[1]: dp[i] = dp[i - coins[1]] + 1
...

dp[i] = min over all coins where coin <= i:
        dp[i - coin] + 1
```

In plain English:
```
"To make amount i, try removing each coin
 check how many coins needed for remaining amount
 take the minimum"
```

---

## Step 3 — base case

```
dp[0] = 0   ← 0 coins needed to make amount 0
dp[1..amount] = MAX  ← unknown initially
```

---

## Build up from small to large

```
coins = [1,3,4], amount = 6

dp = [0, MAX, MAX, MAX, MAX, MAX, MAX]
      0   1    2    3    4    5    6

i=1: try coin 1: dp[1-1]+1 = dp[0]+1 = 1
     dp[1] = 1
     
i=2: try coin 1: dp[2-1]+1 = dp[1]+1 = 2
     dp[2] = 2
     
i=3: try coin 1: dp[3-1]+1 = dp[2]+1 = 3
     try coin 3: dp[3-3]+1 = dp[0]+1 = 1  ← better!
     dp[3] = 1
     
i=4: try coin 1: dp[3]+1 = 2
     try coin 3: dp[1]+1 = 2
     try coin 4: dp[0]+1 = 1  ← better!
     dp[4] = 1
     
i=5: try coin 1: dp[4]+1 = 2
     try coin 3: dp[2]+1 = 3
     try coin 4: dp[1]+1 = 2
     dp[5] = 2
     
i=6: try coin 1: dp[5]+1 = 3
     try coin 3: dp[3]+1 = 2  ← better!
     try coin 4: dp[2]+1 = 3
     dp[6] = 2

Answer = dp[6] = 2 ✓ (3+3)
```

---

## Code

```java
public int coinChange(int[] coins, int amount) {
    // step 1: create dp array
    int[] dp = new int[amount + 1];

    // step 2: base case
    Arrays.fill(dp, Integer.MAX_VALUE);
    dp[0] = 0;

    // step 3: fill dp bottom up
    for (int i = 1; i <= amount; i++) {
        for (int coin : coins) {
            // can we use this coin?
            if (coin <= i && dp[i - coin] != Integer.MAX_VALUE) {
                dp[i] = Math.min(dp[i], dp[i - coin] + 1);
            }
        }
    }

    return dp[amount] == Integer.MAX_VALUE ? -1 : dp[amount];
}
```

---

## Why `dp[i-coin] != Integer.MAX_VALUE`?

```
If dp[i-coin] = MAX → that amount is unreachable
MAX + 1 = overflow → wrong answer
→ skip unreachable amounts
```

---

## The pattern to remember

```
dp[i] = min/max/count of something for amount/length i
dp[i] = best(dp[i - something] + cost)

This pattern covers:
  Coin Change    → dp[i] = min coins for amount i
  Word Break     → dp[i] = can we make string of length i
  Unique Paths   → dp[i][j] = paths to reach cell i,j
  Climbing Stairs→ dp[i] = ways to reach step i
```

---

## Key insight you were missing

```
Your approach: build map → check pairs
DP approach:   build answer for EVERY amount from 0 to target
               each answer builds on previous answers
               → handles any number of coins automatically
```

Try coding it yourself now — you have the template. Give it a go.

Really good — logic is correct! Three small issues.

---

## Issue 1 — early break is wrong

```java
if (minCoins == 1) break;
```

Breaking when minCoins==1 means you stop trying other coins. But minCoins=1 is already optimal for this coin — however another coin might give 1 too AND be needed for correctness in other amounts. Actually minCoins=1 IS the minimum possible so breaking is fine here.

**BUT** — what if `dp[i-coin]` is `MAX-1` (unreachable)? Then `MAX-1 + 1 = MAX` which looks like a valid value but isn't.

---

## Issue 2 — MAX_VALUE-1 + 1 = MAX_VALUE — still causes issues

```java
Arrays.fill(dp, Integer.MAX_VALUE - 1);

// if dp[i-coin] = MAX-1 (unreachable)
dp[i-coin] + 1 = MAX  // treated as valid!
Math.min(minCoins, MAX) = minCoins // ok here but...
```

Better — check if `dp[i-coin]` is reachable before using it:

```java
if (coin <= i && dp[i - coin] != Integer.MAX_VALUE) {
    minCoins = Math.min(minCoins, dp[i - coin] + 1);
}
```

---

## Issue 3 — final check is overly complex

```java
return dp[dp.length-1] != Integer.MAX_VALUE && 
       dp[dp.length-1] != Integer.MAX_VALUE-1 
       ? dp[dp.length-1] : -1;
```

Simpler — just check one value:

```java
return dp[amount] == Integer.MAX_VALUE ? -1 : dp[amount];
```

---

## Clean corrected version

```java
public int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, Integer.MAX_VALUE);
    dp[0] = 0;

    for (int i = 1; i <= amount; i++) {
        for (int coin : coins) {
            if (coin <= i && dp[i - coin] != Integer.MAX_VALUE) {
                dp[i] = Math.min(dp[i], dp[i - coin] + 1);
            }
        }
    }

    return dp[amount] == Integer.MAX_VALUE ? -1 : dp[amount];
}
```

---

## Your code was 90% right

```
✓ dp definition correct
✓ recurrence correct
✓ base case correct
✓ bottom-up iteration correct
✗ MAX_VALUE-1 → use MAX_VALUE + guard check
✗ early break → remove
✗ complex return → simplify
```

---

## The DP pattern you now know

```
1. dp array size = amount+1
2. fill with MAX (unknown)
3. dp[0] = 0 (base case)
4. for each amount i → try each coin
5. if reachable → take minimum
```

Same pattern works for Word Break — ready for it?

## Word Break (LC 139)
3. https://leetcode.com/problems/word-break/

> Given a string `s` and a dictionary of strings `wordDict`, return `true` if `s` can be segmented into space-separated sequence of dictionary words.

```
Input:  s = "leetcode", wordDict = ["leet","code"]
Output: true  →  "leet" + "code"

Input:  s = "applepenapple", wordDict = ["apple","pen"]
Output: true  →  "apple" + "pen" + "apple"

Input:  s = "catsandog", wordDict = ["cats","dog","sand","and","cat"]
Output: false
```

---

## Three DP steps — you do it this time

Based on Coin Change pattern:

**Step 1:** What does `dp[i]` mean here?

Hint — `s` has length n. Think about what `dp[i]` represents for position `i` in the string.

**Step 2:** How does `dp[i]` relate to smaller values?

Hint — if `dp[j]` is true and `s[j..i]` is in dictionary → what can you say about `dp[i]`?

**Step 3:** What is `dp[0]`?

Take a shot at all three before coding.

Let me build the intuition from scratch.

---

## Think about it manually first

```
s = "leetcode", dict = ["leet", "code"]

Can we break "leetcode"?

Try splitting at position 4:
  left  = "leet" → in dict? YES
  right = "code" → can we break "code"?
    try splitting "code" at position 4:
      left  = "code" → in dict? YES
      right = ""     → empty string = always true
    → "code" breakable ✓
  → "leetcode" breakable ✓
```

---

## The pattern

```
s breakable up to position i
= some position j exists where:
    s[0..j] is breakable   AND
    s[j..i] is in dictionary
```

---

## Three steps

**Step 1 — dp[i] means:**

```
dp[i] = can string s[0..i] be broken into dictionary words?
        true or false
```

**Step 2 — recurrence:**

```
dp[i] = true IF:
  there exists some j < i where:
    dp[j] == true           ← left part breakable
    AND s[j..i] in dict     ← right part in dictionary

dp[i] = false otherwise
```

**Step 3 — base case:**

```
dp[0] = true   ← empty string always breakable
```

---

## Visual

```
s = "leetcode"
     01234567

dp[0] = true  ← empty string

dp[4]: try all j from 0 to 3:
  j=0: dp[0]=true AND s[0..4]="leet" in dict? YES → dp[4]=true

dp[8]: try all j from 0 to 7:
  j=0: dp[0]=true AND s[0..8]="leetcode" in dict? NO
  j=4: dp[4]=true AND s[4..8]="code" in dict? YES → dp[8]=true

return dp[8] = true ✓
```

---

## Code it now

```java
public boolean wordBreak(String s, List<String> wordDict) {
    Set<String> dict = new HashSet<>(wordDict);  // O(1) lookup
    int n = s.length();

    boolean[] dp = new boolean[n + 1];  // dp[0..n]
    dp[0] = true;  // base case

    for (int i = 1; i <= n; i++) {
        for (int j = 0; j < i; j++) {
            if (dp[j] && dict.contains(s.substring(j, i))) {
                dp[i] = true;
                break;  // found one valid split → no need to check more
            }
        }
    }

    return dp[n];
}
```

---

## Trace

```
s = "leetcode", dict = {leet, code}
n = 8, dp = [T,F,F,F,F,F,F,F,F]

i=1: j=0: dp[0]=T, s[0,1]="l" in dict? NO → dp[1]=F
i=2: j=0: s[0,2]="le" NO
     j=1: dp[1]=F → skip
     dp[2]=F
i=3: similar → dp[3]=F
i=4: j=0: dp[0]=T, s[0,4]="leet" YES → dp[4]=T, break
i=5: j=0: s[0,5]="leetc" NO
     j=4: dp[4]=T, s[4,5]="c" NO
     dp[5]=F
i=6: j=4: dp[4]=T, s[4,6]="co" NO → dp[6]=F
i=7: j=4: dp[4]=T, s[4,7]="cod" NO → dp[7]=F
i=8: j=0: s[0,8]="leetcode" NO
     j=4: dp[4]=T, s[4,8]="code" YES → dp[8]=T, break

return dp[8] = true ✓
```

---

## Coin Change vs Word Break — same pattern

```
Coin Change:
  dp[i] = min coins for amount i
  try each coin → dp[i] = min(dp[i-coin] + 1)

Word Break:
  dp[i] = can we break s[0..i]
  try each split point j → dp[i] = dp[j] && s[j..i] in dict
```

Both build answer for position `i` using answers for smaller positions.

---

## Complexity

```
Time:  O(n² * m) where n=string length, m=avg word length (substring)
Space: O(n) for dp array
```

---

Try coding it yourself — give it a go.


## Minimum Path Sum (LC 64)

> Given an `m x n` grid filled with non-negative numbers, find a path from top-left to bottom-right which minimizes the sum of numbers along the path. You can only move **right** or **down**.

```
Input:
grid = [[1,3,1],
        [1,5,1],
        [4,2,1]]

Output: 7
Path: 1→3→1→1→1 = 7
```

---

You just did Unique Paths — same grid, same moves. Only difference:

```
Unique Paths:  count number of paths
Min Path Sum:  find minimum cost path
```

Apply the three steps yourself:

1. What does `dp[r][c]` mean?
2. What's the recurrence?
3. What are the base cases?

Take a shot.

Perfect — completely correct on all three steps and the code. Nothing to fix.

---

## Trace to verify

```
grid = [[1,3,1],
        [1,5,1],
        [4,2,1]]

Base cases:
dp[0][0] = 1
row 0: dp[0][1]=1+3=4, dp[0][2]=4+1=5
col 0: dp[1][0]=1+1=2, dp[2][0]=2+4=6

Fill rest:
dp[1][1] = 5 + min(dp[0][1], dp[1][0]) = 5 + min(4,2) = 7
dp[1][2] = 1 + min(dp[0][2], dp[1][1]) = 1 + min(5,7) = 6
dp[2][1] = 2 + min(dp[1][1], dp[2][0]) = 2 + min(7,6) = 8
dp[2][2] = 1 + min(dp[1][2], dp[2][1]) = 1 + min(6,8) = 7

return dp[2][2] = 7 ✓
```

---

## One thing to remove

```java
int[][] dirs = new int[][] {{1,0}, {0,1}};
```

You defined this but never used it — remove it. Clean code matters at VP level.

---

## DP complete ✅

You now know all four core DP patterns:

| Pattern | Problem | Key insight |
|---|---|---|
| 2D paths counting | Unique Paths | came from above or left |
| 1D minimization | Coin Change | try each coin, take min |
| 1D reachability | Word Break | try each split point |
| 2D minimization | Min Path Sum | came from above or left, add grid cost |

---

## The unified DP mental model

```
1. What am I optimizing at each position?
   → count / min / max / true-false

2. How did I get here?
   → from where? using what?

3. What did I know before getting here?
   → smaller subproblem answer

4. Base case — what do I know for free?
   → dp[0] = 0 or 1 or grid[0][0]
```

---

Next — **Graph problems (Virus Spread + Largest Tree in Forest)?**



Your solution is logically moving in the right direction, but you are solving a harder subproblem than necessary.

You are thinking:

```text
dp[i] = best robbery ending EXACTLY at house i
```

So you search all previous valid houses:

```java
for (int j = 0; j < i - 1; j++)
```

That works, but becomes:

```text
O(n²)
```

---

4. House robbery https://leetcode.com/problems/house-robber/
# 🧠 The key DP insight

At every house `i`, there are only 2 choices:

## 1. Rob current house

Then you CANNOT rob previous house.

So:

```text
nums[i] + dp[i-2]
```

---

## 2. Skip current house

Then answer remains:

```text
dp[i-1]
```

---

Therefore:

```text
dp[i] = max(
    dp[i-1],
    nums[i] + dp[i-2]
)
```

This is the entire problem.

---

# Why your approach is unnecessary

You compute:

```text
best among all previous non-adjacent houses
```

But:

```text
dp[i-2]
```

already stores the BEST answer till that point.

So no inner loop is needed.

---

# Correct DP Meaning

Define:

```text
dp[i] = maximum money possible till index i
```

NOT:

```text
maximum money ending at i
```

This change simplifies everything.

---

# Correct Solution

```java
class Solution {

    public int rob(int[] nums) {

        if (nums.length == 1) {
            return nums[0];
        }

        int[] dp = new int[nums.length];

        dp[0] = nums[0];
        dp[1] = Math.max(nums[0], nums[1]);

        for (int i = 2; i < nums.length; i++) {

            dp[i] = Math.max(
                dp[i - 1],              // skip current
                nums[i] + dp[i - 2]    // rob current
            );
        }

        return dp[nums.length - 1];
    }
}
```

---

# Walkthrough

## nums = [2,7,9,3,1]

---

## dp[0]

```text
2
```

---

## dp[1]

```text
max(2,7)=7
```

---

## dp[2]

```text
max(
    7,
    9 + 2
)

= 11
```

---

## dp[3]

```text
max(
    11,
    3 + 7
)

= 11
```

---

## dp[4]

```text
max(
    11,
    1 + 11
)

= 12
```

---

Final:

```text
12
```

---

# 🧠 Mental Model

This is classic:

```text
Take / Skip DP
```

At each position:

| Choice       | Formula           |
| ------------ | ----------------- |
| Take current | nums[i] + dp[i-2] |
| Skip current | dp[i-1]           |

Take best.

---

# ⚡ Pattern recognition

If problem says:

```text
Cannot take adjacent
Choose maximum
```

Think:

```text
take vs skip DP
```

This same pattern appears in:

* House Robber II
* Delete and Earn
* Maximum sum non-adjacent
* Stock cooldown
* Weighted interval scheduling

---

# Space Optimized (Best)

Only previous 2 states matter.

```java
class Solution {

    public int rob(int[] nums) {

        if (nums.length == 1) {
            return nums[0];
        }

        int prev2 = nums[0];
        int prev1 = Math.max(nums[0], nums[1]);

        for (int i = 2; i < nums.length; i++) {

            int curr = Math.max(
                prev1,
                nums[i] + prev2
            );

            prev2 = prev1;
            prev1 = curr;
        }

        return prev1;
    }
}
```

---

# Complexity

| Approach        | Time  | Space |
| --------------- | ----- | ----- |
| Your approach   | O(n²) | O(n)  |
| Optimized DP    | O(n)  | O(n)  |
| Space optimized | O(n)  | O(1)  |


Excellent — this is the exact subtle DP understanding that separates memorization from real intuition.

You are asking about:

```java id="mxfwln"
dp[1] = Math.max(nums[0], nums[1]);
```

instead of:

```java id="x4z9im"
dp[1] = nums[1];
```

---

# 🧠 The MOST important thing

The meaning of DP state is:

```text id="40pkmg"
dp[i] = maximum money possible till index i
```

NOT:

```text id="ztyfpm"
maximum money if we rob house i
```

This distinction changes everything.

---

# Let's visualize

Suppose:

```text id="vop9xk"
nums = [10, 1]
```

---

# What is dp[1] asking?

```text id="z4qklr"
Best possible answer till index 1
```

Meaning:

* considering houses `[0...1]`
* what is maximum robbery possible?

Choices:

* rob house 0 → 10
* rob house 1 → 1

Best:

```text id="98o7wk"
10
```

So:

```java id="m0zxqc"
dp[1] = max(10,1)
```

---

# If you did:

```java id="96x5pd"
dp[1] = nums[1]
```

then:

```text id="pw0k3d"
dp[1] = 1
```

which loses the optimal answer till that point.

---

# Another example

```text id="kwtrq7"
nums = [2, 7]
```

Best till index 1?

```text id="7y9idw"
7
```

because:

* robbing 7 is better than robbing 2.

Thus:

```java id="ghnnxp"
dp[1] = max(2,7)
```

---

# 🧠 Deep DP intuition

DP states should represent:

```text id="f6yljv"
the BEST answer up to that point
```

not:

```text id="xw6r0n"
one particular path
```

That’s why:

```java id="1ozq4k"
dp[i-1]
```

already carries forward:

* all previous decisions
* all optimal paths
* all best combinations

---

# Why recurrence works

At house `i`:

## Skip current

Then best answer is already known:

```java id="e3s0o1"
dp[i-1]
```

---

## Rob current

Then previous house forbidden:

```java id="7n4w79"
nums[i] + dp[i-2]
```

---

Take best:

```java id="6a2f9e"
dp[i] = max(dp[i-1], nums[i] + dp[i-2])
```

---

# ⚡ The confusion comes from this

You are still mentally thinking:

```text id="3y0ofe"
"Which house should I start with?"
```

But DP is not greedily deciding start/end.

It is progressively building:

```text id="dx28op"
best answer for every prefix
```

---

# Another very important insight

By the time you reach:

```java id="2v7r8n"
dp[10]
```

it already includes possibilities like:

* start from 0
* start from 1
* skip multiple
* alternate patterns

ALL of them.

That’s why DP feels magical initially.

---

# Mental shortcut for DP

When defining DP:

Ask:

```text id="v1pob5"
"What EXACTLY does dp[i] mean?"
```

If definition is unclear:

* recurrence becomes confusing
* base cases become confusing
* transitions feel random

Correct state definition is 80% of DP.

## LC 300 — Longest Increasing Subsequence

Good choice — classic DP. Apply three steps yourself first:

1. What does `dp[i]` mean?
2. What's the recurrence? — for each `i`, what smaller values do you look at?
3. What's the base case?

Hint — subsequence means elements don't have to be contiguous. For each position `i`, you look back at all `j < i` where `nums[j] < nums[i]`.

Take a shot at all three before coding.


### > dp[i] length of max possible increasing subsequence !! for i i check all elements less than nums[i] and take max lenth of subsequence among them and add +1 ! d[0] = 1

Perfect — completely correct. Clean and efficient.

---

## Trace to verify

```
nums = [10,9,2,5,3,7,101,18]

dp[0]=1 (10)
dp[1]=1 (9)  — no j where nums[j]<9 with good dp
dp[2]=1 (2)  — no j where nums[j]<2
dp[3]=2 (5)  — nums[2]=2<5 → dp[2]+1=2
dp[4]=2 (3)  — nums[2]=2<3 → dp[2]+1=2
dp[5]=3 (7)  — nums[3]=5<7 → dp[3]+1=3
dp[6]=4 (101)— nums[5]=7<101 → dp[5]+1=4
dp[7]=4 (18) — nums[5]=7<18 → dp[5]+1=4

max=4 ✓
```

---

## What you got right ✅

```
✓ dp[i] = LIS ending at index i
✓ base case dp[i] = 1 (each element is LIS of length 1)
✓ look back at all j < i
✓ only update if nums[i] > nums[j]
✓ track global max separately — answer isn't always dp[n-1]
```

---

## That last point is important

```
dp[n-1] is NOT always the answer
→ LIS might end at any position
→ must track max across all dp[i]

You handled this correctly with:
max = Math.max(max, dp[i]) ✓
```

---

## Complexity

```
Time:  O(n²) — two nested loops
Space: O(n)  — dp array

O(n log n) exists using binary search + patience sorting
→ mention verbally at VP level if asked for optimization
```

---

## O(n log n) approach — mention verbally

```
Maintain a tails array:
tails[i] = smallest tail element of all increasing 
           subsequences of length i+1

For each num:
  binary search in tails for first element >= num
  replace it with num (or append if num > all tails)

tails length = LIS length
```

---

## DP patterns you now own

| Problem | dp[i] means | Recurrence |
|---|---|---|
| Unique Paths | paths to cell (r,c) | dp[r][c] = dp[r-1][c] + dp[r][c-1] |
| Coin Change | min coins for amount i | dp[i] = min(dp[i-coin]+1) |
| Word Break | can break s[0..i] | dp[i] = dp[j] && s[j..i] in dict |
| Min Path Sum | min cost to reach (r,c) | dp[r][c] = min(above,left) + grid |
| LIS | LIS length ending at i | dp[i] = max(dp[j]+1) where nums[j]<nums[i] |

---

Ready for **Max Path Sum in Matrix** or move to **Graph problems**?

## LC 120 — Triangle

Three steps — you do it:

1. What does `dp[i][j]` mean?
2. What's the recurrence? — can come from `(i-1, j-1)` or `(i-1, j)`
3. What's the base case?

Take a shot.

Good attempt but wrong approach — you're picking the minimum of each row independently, but the minimum element might not be reachable from the previous position.

---

## Why it fails

```
triangle = [[2],[3,4],[6,5,7],[4,1,8,3]]

Your approach:
  row 0: min=2, dp[0]=2
  row 1: min=3, dp[1]=2+3=5
  row 2: min=5, dp[2]=5+5=10
  row 3: min=1, dp[3]=10+1=11  ← correct by coincidence!

But try:
triangle = [[2],[3,4],[6,5,7],[4,8,1,3]]
                                  ↑
                                  1 is now at index 2

Your approach: still picks 1 → answer=11
But from 5 (index 1 in row 2) you can only reach index 1 or 2 in row 3
  index 1 = 8, index 2 = 1
  5→1 is valid! → 2+3+5+1=11 ✓ still works

But:
triangle = [[2],[3,4],[6,5,7],[1,8,8,3]]
                               ↑
                               1 is now at index 0

Your approach: picks 1 → answer = 2+3+5+1 = 11
But from 5 (index 1) you can only reach index 1 or 2
  can't reach index 0! 
Correct path: 2+3+6+1=12 or 2+3+5+8=18... 
  wait 2→3→6→1: index 0→0→0→0 valid!
  = 12

Your answer: 11 ← WRONG
```

---

## The right approach — 2D DP

```
dp[i][j] = min path sum to reach cell (i,j)

From (i,j) you came from:
  (i-1, j-1)  ← diagonal left
  (i-1, j)    ← directly above

dp[i][j] = triangle[i][j] + min(dp[i-1][j-1], dp[i-1][j])

Edge cases:
  j=0: can only come from (i-1, 0) — no left diagonal
  j=last: can only come from (i-1, last-1) — no right
```

Base case:
```
dp[0][0] = triangle[0][0]
```

Answer:
```
min of last row of dp
```

---

## Code it now

```java
public int minimumTotal(List<List<Integer>> triangle) {
    int n = triangle.size();
    int[][] dp = new int[n][n];

    // base case
    dp[0][0] = triangle.get(0).get(0);

    for (int i = 1; i < n; i++) {
        List<Integer> row = triangle.get(i);

        for (int j = 0; j <= i; j++) {  // row i has i+1 elements
            // fill dp[i][j]
            // handle edge cases for j=0 and j=i
        }
    }

    // return min of last row
    int min = Integer.MAX_VALUE;
    for (int j = 0; j < n; j++) {
        min = Math.min(min, dp[n-1][j]);
    }
    return min;
}
```

Fill in the inner loop — handle three cases:
- `j=0` → can only come from above
- `j=i` → can only come from diagonal left
- else → min of both

Give it a go.


Good question — there's no magic. Let me explain clearly.

---

## The 2D array is NOT stored in 1D

The 1D `dp` array doesn't store the full 2D table. It only stores **one row at a time** — the current best answers for the row you're processing.

---

## Think of it this way

In your 2D solution:
```
dp[0] = [2]
dp[1] = [5, 6]
dp[2] = [11, 10, 13]
dp[3] = [15, 11, 18, 16]  ← final answer is min of this
```

You compute row by row top to bottom. After computing row 3, you never look at rows 0,1,2 again. They're wasted memory.

---

## 1D approach — reuse same array

Instead of keeping ALL rows — just keep ONE row and update it in place.

But top-down has a problem:

```
dp = [5, 6]  ← row 1
computing row 2:
  dp[0] = 6 + min(dp[0], nothing) 
  
  But dp[0] gets overwritten before dp[1] uses it!
  dp[1] needs OLD dp[0] → already gone ✗
```

That's why bottom-up works better — going right to left or using row below:

---

## The real trick — work BOTTOM UP

Instead of top→bottom, go bottom→top.

```
Start with last row in dp:
dp = [4, 1, 8, 3]   ← row 3 (bottom)

Now process row 2 = [6, 5, 7]:
  For each cell j in row 2:
    dp[j] = row2[j] + min(dp[j], dp[j+1])
    
    This asks: "what's the min path from row2[j] to bottom?"
    = row2[j] + min(best path from cell below, best path from cell below-right)
```

---

## What dp[j] means changes as you go up

```
After copying last row:
  dp[j] = min path from row3[j] to bottom = just row3[j]

After processing row 2:
  dp[j] = min path from row2[j] to bottom

After processing row 1:
  dp[j] = min path from row1[j] to bottom

After processing row 0:
  dp[0] = min path from top to bottom = answer
```

The array meaning EVOLVES as you process each row upward.

---

## Why no overwriting problem going bottom-up

```
Processing row 2 = [6,5,7], dp=[4,1,8,3]

j=0: dp[0] = 6+min(dp[0],dp[1]) = 6+min(4,1) = 7
     dp = [7,1,8,3]
     ↑ dp[0] overwritten — but j=0 is DONE, never needed again
     
j=1: dp[1] = 5+min(dp[1],dp[2]) = 5+min(1,8) = 6
     dp = [7,6,8,3]
     ↑ uses dp[1] and dp[2] — dp[1] not yet overwritten ✓
     
j=2: dp[2] = 7+min(dp[2],dp[3]) = 7+min(8,3) = 10
     dp = [7,6,10,3]
     ↑ uses dp[2] and dp[3] — both still from row 3 ✓
```

Key: each `dp[j]` update only needs `dp[j]` and `dp[j+1]` — and `dp[j+1]` hasn't been updated yet when you process `j`.

---

## Summary

```
Not storing 2D in 1D array

Instead:
  dp[j] meaning changes as you process each row
  At any point dp[j] = "best answer from current row[j] to bottom"
  
  Each row update uses only dp[j] and dp[j+1]
  Updating left to right ensures dp[j+1] is still "old" when needed
  
  After processing all rows upward:
  dp[0] = answer for entire triangle
```
https://leetcode.com/problems/triangle/?envType=study-plan-v2&envId=top-interview-150

---

Clear now? Ready for **Max Path Sum in Matrix**?

Same as Unique Paths but with obstacles. Apply three steps yourself:

1. `dp[r][c]` means?
2. Recurrence — same as before but one extra condition?
3. Base case — what happens if obstacle is in first row or column?

Take a shot.

Logic is correct but using `-1` for obstacles is unnecessarily complex. Let me show why and how to simplify.

---

## The problem with -1 approach

```java
} else if(dp[row-1][col] == -1){
    dp[row][col] = dp[row][col-1];  // only use left
} else if(dp[row][col-1] == -1){
    dp[row][col] = dp[row-1][col];  // only use above
}
```

This works but you need to handle `-1` everywhere. Gets messy.

---

## Simpler insight — use 0 for obstacles

```
If a cell has obstacle → dp[r][c] = 0 (0 paths through it)
If dp[r-1][c] = 0 → adding it contributes nothing → dp[r][c] += 0
If dp[r][c-1] = 0 → adding it contributes nothing → dp[r][c] += 0

So dp[r][c] = dp[r-1][c] + dp[r][c-1] ALWAYS
Just set obstacle cells to 0 — the math handles itself!
```

No special cases needed.

---

## Trace

```
obstacleGrid = [[0,0,0],
                [0,1,0],
                [0,0,0]]

Base cases:
row 0: dp[0][0]=1, dp[0][1]=1, dp[0][2]=1
col 0: dp[1][0]=1, dp[2][0]=1

Fill:
dp[1][1] = obstacle → 0
dp[1][2] = dp[0][2] + dp[1][1] = 1+0 = 1
dp[2][1] = dp[1][1] + dp[2][0] = 0+1 = 1
dp[2][2] = dp[1][2] + dp[2][1] = 1+1 = 2 ✓
```

---

## Clean version

```java
public int uniquePathsWithObstacles(int[][] grid) {
    int rows = grid.length;
    int cols = grid[0].length;
    int[][] dp = new int[rows][cols];

    // base case — first row
    for (int col = 0; col < cols; col++) {
        if (grid[0][col] == 1) break;  // obstacle blocks all after it
        dp[0][col] = 1;
    }

    // base case — first col
    for (int row = 0; row < rows; row++) {
        if (grid[row][0] == 1) break;  // obstacle blocks all after it
        dp[row][0] = 1;
    }

    // fill rest
    for (int row = 1; row < rows; row++) {
        for (int col = 1; col < cols; col++) {
            if (grid[row][col] == 1) {
                dp[row][col] = 0;  // obstacle → no paths
            } else {
                dp[row][col] = dp[row-1][col] + dp[row][col-1];
            }
        }
    }

    return dp[rows-1][cols-1];
}
```

---

## Your approach vs clean approach

| | Your approach | Clean approach |
|---|---|---|
| Obstacle marker | -1 | 0 |
| Special cases | Many if/else | None — math handles it |
| Base case | Complex | Simple break |
| Correctness | ✓ | ✓ |

---

## Key insight to remember

```
obstacle → dp = 0
0 paths through obstacle × anything = 0
→ naturally doesn't contribute to neighbors
→ no special handling needed
```

Ready for **Max Path Sum in Matrix** or another DP problem?

Park palindrome ✅

---

## LC 97 — Interleaving String

Before I explain anything — let me ask one question to build intuition:

```
s1 = "aab"
s2 = "axy"
s3 = "aaaxby"  ← is this an interleaving of s1 and s2?

s1:  a a b
s2:  a x y
s3:  a a a x b y

Can you match each char of s3 to either s1 or s2
in order?

s3[0]='a' → s1[0]='a' ✓
s3[1]='a' → s1[1]='a' ✓  OR  s2[0]='a' ✓  ← two choices!
s3[2]='a' → ?
...
```

This is the key challenge — at each position you have a **choice** — take from s1 or take from s2.

---

## One question before we go further

If you've used `i` characters from s1 and `j` characters from s2 — which character of s3 are you currently at?



Ok let me build intuition from scratch.

---

## Forget two transactions — think about states

At any point in time you are in one of these states:

```
State 1: never bought anything yet
State 2: holding stock (bought once, not sold)
State 3: sold once (completed 1 transaction)
State 4: holding stock again (bought twice, sold once)
State 5: sold twice (completed 2 transactions) ← final state
```

---

## How states transition

```
State 1 → State 2: BUY   (spend money)
State 2 → State 3: SELL  (gain money)
State 3 → State 4: BUY   (spend money)
State 4 → State 5: SELL  (gain money)
```

You can also STAY in same state (do nothing that day).

---

## What you track in each state

```
State 1: profit = 0 (did nothing)
State 2: profit = -prices[i] (spent money buying)
State 3: profit = best profit after 1 complete transaction
State 4: profit = best profit after 1 complete transaction - prices[i]
State 5: profit = best profit after 2 complete transactions ← ANSWER
```

---

## Trace day by day

```
prices = [3, 3, 5, 0, 0, 3, 1, 4]

Start:
  s1 = 0      (never bought)
  s2 = -INF   (not holding yet)
  s3 = -INF   (haven't sold once)
  s4 = -INF   (not holding second time)
  s5 = -INF   (haven't sold twice)

Day 0, price=3:
  s2 = max(s2, s1-price) = max(-INF, 0-3) = -3   ← buy
  s3 = max(s3, s2+price) = max(-INF, -3+3) = 0   ← sell
  s4 = max(s4, s3-price) = max(-INF, 0-3) = -3   ← buy again
  s5 = max(s5, s4+price) = max(-INF, -3+3) = 0   ← sell again

Day 1, price=3:
  s2 = max(-3, 0-3) = -3
  s3 = max(0, -3+3) = 0
  s4 = max(-3, 0-3) = -3
  s5 = max(0, -3+3) = 0

Day 2, price=5:
  s2 = max(-3, 0-5) = -3
  s3 = max(0, -3+5) = 2    ← sold at 5, bought at 3, profit=2
  s4 = max(-3, 2-5) = -3
  s5 = max(0, -3+5) = 2

Day 3, price=0:
  s2 = max(-3, 0-0) = 0    ← buy at 0!
  s3 = max(2, 0+0) = 2
  s4 = max(-3, 2-0) = 2    ← buy second time
  s5 = max(2, -3+0) = 2

Day 4, price=0:
  s2 = max(0, 0-0) = 0
  s3 = max(2, 0+0) = 2
  s4 = max(2, 2-0) = 2
  s5 = max(2, 2+0) = 2

Day 5, price=3:
  s2 = max(0, 0-3) = 0
  s3 = max(2, 0+3) = 3     ← sell at 3, bought at 0, profit=3
  s4 = max(2, 3-3) = 2
  s5 = max(2, 2+3) = 5     ← first txn profit=2, second txn partial

Day 6, price=1:
  s2 = max(0, 0-1) = 0
  s3 = max(3, 0+1) = 3
  s4 = max(2, 3-1) = 2
  s5 = max(5, 2+1) = 5

Day 7, price=4:
  s2 = max(0, 0-4) = 0
  s3 = max(3, 0+4) = 4
  s4 = max(2, 4-4) = 2     ← hmm, wait
  
  Actually:
  s4 = max(2, 3-4) = max(2,-1) = 2  ← hold from day 6 (bought at 1)
  
  wait let me retrace day 6:
  s4 = max(2, 3-1) = 2  ← bought at 1, s3 was 3 before buying
  
  day 7:
  s5 = max(5, 2+4) = 6  ← sell! profit = 6 ✓

Answer = s5 = 6 ✓
```

---

## The code — just 4 variables

```java
public int maxProfit(int[] prices) {
    int s2 = Integer.MIN_VALUE;  // holding first stock
    int s3 = Integer.MIN_VALUE;  // sold first stock
    int s4 = Integer.MIN_VALUE;  // holding second stock
    int s5 = Integer.MIN_VALUE;  // sold second stock

    for (int price : prices) {
        s5 = Math.max(s5, s4 + price);  // sell second
        s4 = Math.max(s4, s3 - price);  // buy second
        s3 = Math.max(s3, s2 + price);  // sell first
        s2 = Math.max(s2, -price);       // buy first (s1=0 always)
    }

    return Math.max(0, s5);
}
```

---

## Why update in reverse order?

```
s5 updated first using OLD s4
s4 updated using OLD s3
s3 updated using OLD s2
s2 updated last

If you updated s2 first → s3 would use NEW s2
→ buy and sell on same day (not allowed)
→ reverse order prevents same-day transactions
```

---

## Key insight

```
s2 = best "negative profit" from buying = -minPrice
s3 = best profit after 1 transaction
s4 = best profit after buying second time = s3 - price
s5 = best profit after 2 transactions = s4 + price
```

Try tracing it yourself on `prices = [1,2,3,4,5]` — expected output 4.

## State Machine — from scratch

Forget stocks for a minute.

---

## Think about it as a game

You have 5 possible situations you can be in:

```
Situation 1: "I have cash, never bought anything"
Situation 2: "I'm holding a stock (bought once)"
Situation 3: "I sold once, have cash"
Situation 4: "I'm holding a stock again (bought twice)"
Situation 5: "I sold twice, have cash" ← GOAL
```

---

## Rules

```
From Situation 1 → can BUY  → go to Situation 2
From Situation 2 → can SELL → go to Situation 3
From Situation 3 → can BUY  → go to Situation 4
From Situation 4 → can SELL → go to Situation 5
ANY situation    → can WAIT → stay in same situation
```

---

## What does "profit" mean in each situation?

```
Sit 1: profit = 0          (did nothing, have all cash)
Sit 2: profit = -buyPrice  (spent money, so negative)
Sit 3: profit = sellPrice - buyPrice  (gained money)
Sit 4: profit = sit3profit - buyPrice2  (spent again)
Sit 5: profit = sit4profit + sellPrice2 (gained again)
```

---

## Tiny example — prices = [1, 5]

```
Day 0, price=1:

Sit1 = 0 (always 0, do nothing)

Sit2 = best profit while HOLDING after day 0
     = max(was already holding, buy today)
     = max(-INF, 0 - 1)   ← 0 is sit1 profit, spend 1 to buy
     = -1

Sit3 = best profit after SELLING ONCE after day 0
     = max(was already sold, sell today)
     = max(-INF, -1 + 1)  ← sit2 profit + sell at 1
     = 0

Sit4 = best profit while HOLDING SECOND TIME after day 0
     = max(-INF, 0 - 1)   ← sit3 profit - buy at 1
     = -1

Sit5 = best profit after SELLING TWICE after day 0
     = max(-INF, -1 + 1)  ← sit4 profit + sell at 1
     = 0
```

```
Day 1, price=5:

Sit2 = max(-1, 0-5) = -1   ← keep holding from day 0
Sit3 = max(0, -1+5) = 4    ← sell today at 5! profit=4
Sit4 = max(-1, 4-5) = -1   ← buy again but sit3=4, spend 5
Sit5 = max(0, -1+5) = 4    ← sell second time at 5

Answer = Sit5 = 4 ✓
(buy at 1, sell at 5 = profit 4 — only need 1 transaction here)
```

---

## Now trace your example

```
prices = [3,3,5,0,0,3,1,4]

Start:
s2=-INF, s3=-INF, s4=-INF, s5=-INF

Day 0, price=3:
s2 = max(-INF, 0-3) = -3    ← buy at 3
s3 = max(-INF, -3+3) = 0    ← buy and sell same day, profit=0
s4 = max(-INF, 0-3) = -3    ← buy again
s5 = max(-INF, -3+3) = 0    ← sell again, profit=0

Day 1, price=3: (same price, nothing changes)
s2=-3, s3=0, s4=-3, s5=0

Day 2, price=5:
s2 = max(-3, 0-5) = -3      ← keep holding from day 0
s3 = max(0, -3+5) = 2       ← sell at 5! bought at 3, profit=2
s4 = max(-3, 2-5) = -3      ← buy second time
s5 = max(0, -3+5) = 2       ← sell second, profit=2

Day 3, price=0:
s2 = max(-3, 0-0) = 0       ← buy at 0! better than buying at 3
s3 = max(2, 0+0) = 2        ← sell at 0? no, keep s3=2
s4 = max(-3, 2-0) = 2       ← buy second at 0, already have profit 2!
s5 = max(2, -3+0) = 2       ← no improvement

Day 4, price=0: (same, nothing changes)
s2=0, s3=2, s4=2, s5=2

Day 5, price=3:
s2 = max(0, 0-3) = 0        ← keep holding (bought at 0)
s3 = max(2, 0+3) = 3        ← sell at 3! bought at 0, profit=3
s4 = max(2, 3-3) = 2        ← buy second at 3, profit=3-3=0+prev
s5 = max(2, 2+3) = 5        ← sell second at 3! total profit=5

Day 6, price=1:
s2 = max(0, 0-1) = 0
s3 = max(3, 0+1) = 3
s4 = max(2, 3-1) = 2        ← buy second at 1! profit=3-1=2
s5 = max(5, 2+1) = 5

Day 7, price=4:
s2 = max(0, 0-4) = 0
s3 = max(3, 0+4) = 4
s4 = max(2, 3-4) = 2        ← keep s4=2 (bought at 1)
s5 = max(5, 2+4) = 6        ← sell second at 4! profit=6 ✓

Answer = 6 ✓
```

---

## Why update in reverse order in code?

```java
for (int price : prices) {
    s5 = Math.max(s5, s4 + price);  // ← update s5 FIRST using OLD s4
    s4 = Math.max(s4, s3 - price);  // ← update s4 using OLD s3
    s3 = Math.max(s3, s2 + price);  // ← update s3 using OLD s2
    s2 = Math.max(s2, -price);      // ← update s2 LAST
}
```

If you updated s2 first:
```
s2 updated → new s2 reflects buying TODAY
s3 uses new s2 → sells TODAY using TODAY's buy
→ buy and sell same day → NOT ALLOWED

Reverse order ensures each state uses
YESTERDAY's values not TODAY's
```

---

## One sentence summary

```
Track 4 variables representing best profit in each situation.
Each day update all 4 in reverse order.
s5 at the end = maximum profit with at most 2 transactions.
```

Clear now? Want to code it?

Perfect — completely correct. Clean and exactly right.

---

## Only one edge case to handle

```java
return s5;
```

What if prices only goes down?
```
prices = [5,4,3,2,1]
→ never profitable to buy
→ s5 stays Integer.MIN_VALUE
→ return MIN_VALUE ← wrong, should return 0
```

Fix:
```java
return Math.max(0, s5);
```

---

## Everything else ✅

```
✓ s2 = max(s2, -price)        — buy first, no previous profit
✓ s3 = max(s3, s2+price)      — sell first
✓ s4 = max(s4, s3-price)      — buy second using first profit
✓ s5 = max(s5, s4+price)      — sell second
✓ reverse order — prevents same day buy/sell
✓ comments explain each state clearly
```

---

## What to say in interview

*"I track 4 states — best profit while holding first stock, after selling first, while holding second, after selling second. Each day I update in reverse order so each state uses yesterday's values — prevents same-day buy and sell. Time O(n), space O(1)."*

---

LC 123 ✅ — and you just solved a Hard problem!

**More DP or move to Graphs?**

You're right — the issue is `Integer.MIN_VALUE + price` overflows.

---

## The bug

```java
s2 = Integer.MIN_VALUE  // -2147483648

s3 = Math.max(s3, s2 + price)
   = Math.max(MIN_VALUE, -2147483648 + 3)
   = Math.max(MIN_VALUE, -2147483645)  ← ok here

But:
s4 = Math.max(s4, s3 - price)
   = Math.max(MIN_VALUE, -2147483645 - 0)  ← fine

s5 = Math.max(s5, s4 + price)
   = Math.max(MIN_VALUE, MIN_VALUE + price)
   = MIN_VALUE + 3 = -2147483645  ← negative, ok

Hmm but you got 2147483646 = MAX_VALUE - 1...
```

Let me check more carefully:

```
MIN_VALUE = -2147483648
MIN_VALUE + price where price is large:
  -2147483648 + 5 = -2147483643  ← fine

But MIN_VALUE - price:
  s4 = s3 - price
  if s3 = MIN_VALUE:
  MIN_VALUE - 0 = MIN_VALUE  ← ok
  MIN_VALUE - 5 = -2147483648 - 5 = OVERFLOW → 2147483643
```

**That's it** — `MIN_VALUE - price` overflows to large positive number.

---

## Fix — use a safer initial value

```java
int s2 = -prices[0];           // buy on day 0
int s3 = 0;
int s4 = -prices[0];
int s5 = 0;
```

Or use a safe large negative:

```java
int NEG_INF = -1_000_000_000;  // safe, won't overflow when adding prices

int s2 = NEG_INF;
int s3 = NEG_INF;
int s4 = NEG_INF;
int s5 = NEG_INF;
```

---

## Clean fixed version

```java
public int maxProfit(int[] prices) {
    int s2 = -prices[0];  // buy on day 0
    int s3 = 0;
    int s4 = -prices[0];
    int s5 = 0;

    for (int price : prices) {
        s5 = Math.max(s5, s4 + price);
        s4 = Math.max(s4, s3 - price);
        s3 = Math.max(s3, s2 + price);
        s2 = Math.max(s2, -price);
    }

    return s5;
}
```

---

## Trace to verify

```
prices = [3,3,5,0,0,3,1,4]

Start: s2=-3, s3=0, s4=-3, s5=0

price=3: (same as before, no change)
price=3: (same)
price=5: s3=max(0,-3+5)=2, s5=max(0,-3+5)=2
price=0: s2=max(-3,0)=0, s4=max(-3,2-0)=2
price=0: no change
price=3: s3=max(2,0+3)=3, s5=max(2,2+3)=5
price=1: s4=max(2,3-1)=2
price=4: s5=max(5,2+4)=6

return 6 ✓
```

---

Key lesson — **never use Integer.MIN_VALUE when you'll do arithmetic on it.** Always use a safe large negative like `-1_000_000_000` or initialize with first element.

Ready to move on?

but for fist run we are adding that negative number to price to it reduces and will become max?


Great catch — you're right to question this. Let me trace carefully.

---

## The issue you're pointing at

```java
int s2 = -prices[0];  // = -3
int s3 = 0;
int s4 = -prices[0];  // = -3
int s5 = 0;

First iteration, price=3:
s5 = max(0, s4+price) = max(0, -3+3) = max(0, 0) = 0
s4 = max(-3, s3-price) = max(-3, 0-3) = max(-3,-3) = -3
s3 = max(0, s2+price) = max(0, -3+3) = max(0, 0) = 0
s2 = max(-3, -price) = max(-3, -3) = -3
```

Nothing changes on day 0 — correct because we initialized with day 0 already.

---

## Why initializing with -prices[0] is correct

```java
int s2 = -prices[0];
```

This means — "best profit if I BUY on day 0 = -prices[0]" — I spent prices[0] so my profit is negative.

```
s2 = -3  means "I bought at price 3, so I'm down 3"
```

This is CORRECT — buying costs money so profit is negative.

---

## Your concern — does -3 + price become large?

```
s3 = max(s3, s2 + price)
   = max(0, -3 + price)
```

Only becomes large if price is very large:
```
price = 1000:
s3 = max(0, -3+1000) = 997  ← correct! profit of 997
```

That's not a bug — that's the correct profit from buying at 3 and selling at 1000.

---

## The REAL overflow issue was with Integer.MIN_VALUE

```
Integer.MIN_VALUE = -2147483648

MIN_VALUE - price:   ← subtraction
-2147483648 - 3 = OVERFLOW → large positive number ← BUG

-prices[0] - price:
-3 - 3 = -6  ← fine, no overflow
```

The overflow only happened with `Integer.MIN_VALUE` because it's at the extreme edge of int range. `-prices[0]` is a normal number — no overflow possible.

---

## Summary

```
Integer.MIN_VALUE + price → could overflow if MIN_VALUE is used ✗
-prices[0] + price        → normal arithmetic, no overflow ✓

Your concern: "-3 becomes large positive"
Reality:      -3 + price = profit of buying at 3, selling at price
              That's correct behavior, not a bug
```

Clear? Ready to move on?


Yes — two alternatives.

---

## Alternative 1 — Forward/Backward pass

**Intuition:**
```
Split array at every point k:
  Left  [0..k]   → best profit with 1 transaction
  Right [k..n-1] → best profit with 1 transaction
  Answer = max(left[k] + right[k]) for all k
```

**Two passes:**
```
Pass 1 (forward):  left[i]  = best profit using 1 txn in prices[0..i]
Pass 2 (backward): right[i] = best profit using 1 txn in prices[i..n-1]

Answer = max(left[i] + right[i]) for all i
```

---

## Trace

```
prices = [3,3,5,0,0,3,1,4]

Forward pass — best 1 txn profit up to day i:
  i=0: 0  (only one price, can't profit)
  i=1: 0  (3→3, no profit)
  i=2: 2  (3→5, profit=2)
  i=3: 2  (still best is 3→5)
  i=4: 2
  i=5: 3  (0→3, profit=3)
  i=6: 3
  i=7: 4  (0→4, profit=4)
left = [0,0,2,2,2,3,3,4]

Backward pass — best 1 txn profit from day i to end:
  i=7: 0
  i=6: 3  (1→4)
  i=5: 3  (3→4? no, 1→4=3)
  i=4: 4  (0→4)
  i=3: 4  (0→4)
  i=2: 4  (0→4)
  i=1: 4  (0→4)
  i=0: 4  (0→4)
right = [4,4,4,4,4,3,3,0]

Answer = max(left[i] + right[i]):
  i=0: 0+4=4
  i=1: 0+4=4
  i=2: 2+4=6 ← max!
  i=3: 2+4=6
  i=4: 2+4=6
  i=5: 3+3=6
  i=6: 3+3=6
  i=7: 4+0=4

Answer = 6 ✓
```

---

## Code

```java
public int maxProfit(int[] prices) {
    int n = prices.length;
    int[] left = new int[n];   // best profit [0..i]
    int[] right = new int[n];  // best profit [i..n-1]

    // forward pass
    int minPrice = prices[0];
    for (int i = 1; i < n; i++) {
        minPrice = Math.min(minPrice, prices[i]);
        left[i] = Math.max(left[i-1], prices[i] - minPrice);
    }

    // backward pass
    int maxPrice = prices[n-1];
    for (int i = n-2; i >= 0; i--) {
        maxPrice = Math.max(maxPrice, prices[i]);
        right[i] = Math.max(right[i+1], maxPrice - prices[i]);
    }

    // combine
    int maxProfit = 0;
    for (int i = 0; i < n; i++) {
        maxProfit = Math.max(maxProfit, left[i] + right[i]);
    }
    return maxProfit;
}
```

---

## Comparison

| Approach | Time | Space | Intuition |
|---|---|---|---|
| State machine | O(n) | O(1) | Track 4 states |
| Forward/backward | O(n) | O(n) | Split at every point |

---

## Which to use in interview?

```
Forward/Backward:
  ✓ More intuitive — easy to explain
  ✓ Clear separation of two transactions
  ✗ O(n) space

State machine:
  ✓ O(1) space — optimal
  ✗ Harder to derive from scratch
  ✓ Generalizes to K transactions easily
```

*"I'd start with forward/backward — easier to explain and verify. Then optimize to state machine for O(1) space if asked."*

---

Ready to move on?

## Intuition for left + right

---

## The core idea

You have at most 2 transactions. They can't overlap.

So somewhere there's a **split point**:

```
Transaction 1 happens BEFORE split
Transaction 2 happens AFTER split

prices = [3, 3, 5, 0, 0, 3, 1, 4]
                     ↑
                  split here

Left:  [3,3,5] → best profit = 2 (buy 3, sell 5)
Right: [0,0,3,1,4] → best profit = 4 (buy 0, sell 4)
Total: 2 + 4 = 6 ✓
```

---

## Why try ALL split points?

You don't know WHERE the best split is. So try every possible split:

```
Split at 0: left=[3]        right=[3,5,0,0,3,1,4]
Split at 1: left=[3,3]      right=[5,0,0,3,1,4]
Split at 2: left=[3,3,5]    right=[0,0,3,1,4]
Split at 3: left=[3,3,5,0]  right=[0,3,1,4]
...
```

For each split — best total = best left profit + best right profit.

---

## Why build left[] and right[] arrays?

If you computed best profit naively for each split — O(n) per split = O(n²) total.

Instead — precompute:

```
left[i]  = best profit from prices[0..i]
         = "what's the best I can do with ONE transaction
            using only prices up to day i?"

right[i] = best profit from prices[i..n-1]
         = "what's the best I can do with ONE transaction
            using only prices from day i onwards?"
```

Then for each split point i:
```
total profit = left[i] + right[i]
```

Take the maximum across all i.

---

## How to build left[] — forward pass

```
prices = [3, 3, 5, 0, 0, 3, 1, 4]

left[i] = best profit buying and selling somewhere in [0..i]

i=0: only one price [3] → can't buy and sell → 0
i=1: prices [3,3] → buy 3 sell 3 → profit 0
i=2: prices [3,3,5] → buy 3 sell 5 → profit 2
i=3: prices [3,3,5,0] → best still buy 3 sell 5 → profit 2
i=4: prices [3,3,5,0,0] → best still 2
i=5: prices [3,3,5,0,0,3] → buy 0 sell 3 → profit 3
i=6: prices [...,1] → best still 3
i=7: prices [...,4] → buy 0 sell 4 → profit 4

left = [0, 0, 2, 2, 2, 3, 3, 4]
```

How to compute efficiently:
```
track minPrice seen so far
left[i] = max(left[i-1], prices[i] - minPrice)
```

---

## How to build right[] — backward pass

```
prices = [3, 3, 5, 0, 0, 3, 1, 4]

right[i] = best profit buying and selling somewhere in [i..end]

i=7: only [4] → 0
i=6: prices [1,4] → buy 1 sell 4 → profit 3
i=5: prices [3,1,4] → buy 1 sell 4 → profit 3
i=4: prices [0,3,1,4] → buy 0 sell 4 → profit 4
i=3: prices [0,0,3,1,4] → buy 0 sell 4 → profit 4
i=2: prices [5,0,0,3,1,4] → buy 0 sell 4 → profit 4
i=1: prices [3,5,0,0,3,1,4] → buy 0 sell 4 → profit 4
i=0: prices [3,3,5,0,0,3,1,4] → buy 0 sell 4 → profit 4

right = [4, 4, 4, 4, 4, 3, 3, 0]
```

How to compute efficiently:
```
track maxPrice seen so far (going backward)
right[i] = max(right[i+1], maxPrice - prices[i])
```

---

## Now combine

```
left  = [0, 0, 2, 2, 2, 3, 3, 4]
right = [4, 4, 4, 4, 4, 3, 3, 0]
sum   = [4, 4, 6, 6, 6, 6, 6, 4]
              ↑
           max = 6 ✓
```

---

## In plain English

```
left[i]  = "best I can do if my FIRST transaction ends by day i"
right[i] = "best I can do if my SECOND transaction starts from day i"

left[i] + right[i] = total profit if split happens at day i

max over all i = best possible with 2 transactions
```

---

Clear? Try coding it now.