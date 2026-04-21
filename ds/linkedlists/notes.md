# Linked Lists — Deep Dive

## The Core Insight

Arrays give you index-based access — `arr[3]` is O(1). Linked lists don't. Every node only knows about the next node. This constraint is what makes linked list problems interesting — you have to solve everything with pointer manipulation, no random access.

---

## The Node Structure — Burn This In

Everything in linked lists is built on this:

```java
class ListNode {
    int val;
    ListNode next;

    ListNode(int val) {
        this.val = val;
        this.next = null;
    }
}
```

A linked list is just a chain of these nodes:

```
head
 ↓
[1] → [2] → [3] → [4] → [5] → null
```

`head` is the only thing you're given. Lose `head`, lose the entire list.

---

## The Four Pointer Patterns — Everything Else Is a Variation

### Pattern 1 — Single Pointer (simple traversal)
```
ptr moves one step at a time
use when: printing, searching, counting
```

### Pattern 2 — Slow-Fast (two speeds)
```
slow moves 1 step, fast moves 2 steps
use when: finding middle, detecting cycle, nth from end
```

### Pattern 3 — Previous-Current (in-place modification)
```
prev trails behind curr by one node
use when: reversing, deleting nodes
```

### Pattern 4 — Multiple Lists (merging)
```
one pointer per list
use when: merging sorted lists
```

---

## Pattern 3 Deep Dive — Reverse a Linked List

This is the most fundamental operation. Every other reversal problem builds on it.

**What reversal means physically:**

```
Before:
head
 ↓
[1] → [2] → [3] → [4] → null

After:
                   head
                    ↓
null ← [1] ← [2] ← [3] ← [4]
```

Every arrow flips direction. The old tail becomes the new head.

**How to do it — three pointer technique:**

You need three pointers because when you flip an arrow, you lose access to what's ahead. `next` saves the forward reference before you break it.

```
prev = null
curr = head

Step 1:
prev=null  curr=[1]→[2]→[3]→null

  next = curr.next        // save [2] before breaking link
  curr.next = prev        // flip: [1]→null
  prev = curr             // prev moves to [1]
  curr = next             // curr moves to [2]

Step 2:
prev=[1]→null  curr=[2]→[3]→null

  next = curr.next        // save [3]
  curr.next = prev        // flip: [2]→[1]→null
  prev = curr             // prev moves to [2]
  curr = next             // curr moves to [3]

Step 3:
prev=[2]→[1]→null  curr=[3]→null

  next = curr.next        // save null
  curr.next = prev        // flip: [3]→[2]→[1]→null
  prev = curr             // prev moves to [3]
  curr = next             // curr = null → loop ends

prev is now the new head: [3]→[2]→[1]→null
```

**Java Code:**

```java
public static ListNode reverseList(ListNode head) {
    if (head == null || head.next == null) return head;

    ListNode prev = null;
    ListNode curr = head;

    while (curr != null) {
        ListNode next = curr.next;   // 1. save next
        curr.next = prev;            // 2. flip arrow
        prev = curr;                 // 3. advance prev
        curr = next;                 // 4. advance curr
    }

    return prev;   // prev is new head
}
```

**Why return `prev` and not `curr`?** Because when the loop ends, `curr` is `null`. `prev` is sitting on the last node processed — which is the new head.

---

## Pattern 2 Deep Dive — Slow-Fast Pointer

Two pointers move at different speeds. This creates a gap between them that you can exploit.

**Finding the middle:**

```
[1] → [2] → [3] → [4] → [5] → null
 s                              
 f

After 1 step: slow=[2], fast=[3]
After 2 steps: slow=[3], fast=[5]
fast.next = null → stop → slow is middle

For even length [1]→[2]→[3]→[4]:
After 1 step: slow=[2], fast=[3]
After 2 steps: slow=[3], fast=null → stop → slow is middle-right
```

**Detecting a cycle:**

```
If no cycle: fast hits null → return false
If cycle: fast laps slow → they meet → return true

Why they must meet: 
fast gains 1 step on slow per iteration
in a cycle of length L, they meet within L iterations
```

---

## Question 1 — Reverse a Linked List

**"Reverse a singly linked list iteratively. Then do it recursively."**

```
Input:  1 → 2 → 3 → 4 → 5 → null
Output: 5 → 4 → 3 → 2 → 1 → null

Input:  1 → null
Output: 1 → null

Input:  null
Output: null
```

**Two versions required:**
1. Iterative — using the prev/curr/next pattern above
2. Recursive — think about what the base case is, and what the recursive step does to the arrows

**For recursive — think through this:**
```
reverseList([1]→[2]→[3])

Assume reverseList([2]→[3]) already works and returns [3]→[2]
Now you have:
  [1]→[2]→[3]  and  [3]→[2]→null (returned)
  head = [1], head.next = [2]
  
How do you attach [1] at the end?
  head.next.next = head   // [2].next = [1]
  head.next = null        // [1].next = null (or it loops)
```

Write both versions. Post when ready.

# Linked Lists — Iterative Problems Only

First — quick review of your iterative reverse solution you posted, then we move to the next question.

---

## Review — Reverse Linked List Iterative

```java
public static void reversListIterative(LinkedList list) {
    ListNode prev = null;
    ListNode current = list.head;
    ListNode next = null;

    while (current != null) {
        next = current.next;      // ✅ save next
        current.next = prev;      // ✅ flip arrow
        prev = current;           // ✅ advance prev
        current = next;           // ✅ advance curr
    }
    list.tail = list.head;        // ✅ old head becomes tail
    list.head = prev;             // ✅ prev is new head
}
```

**Logic is perfect.** Three things to note:

### Issue 1 — Return type should be `ListNode`

```java
// Your version — void, mutates a LinkedList wrapper object
public static void reversListIterative(LinkedList list)

// Interview standard — takes head node, returns new head
public static ListNode reverseList(ListNode head)
```

In interviews they give you `ListNode head` directly, not a wrapper object. Interviewers at Goldman/JPMC will always use the LeetCode-style signature.

### Issue 2 — Null guard missing

```java
if (head == null || head.next == null) return head;
```

### Issue 3 — Typo

```java
reversListIterative  →  reverseListIterative
```

---

## Interview Standard Version

```java
public static ListNode reverseList(ListNode head) {
    if (head == null || head.next == null) return head;

    ListNode prev = null;
    ListNode curr = head;

    while (curr != null) {
        ListNode next = curr.next;   // save next
        curr.next = prev;            // flip arrow
        prev = curr;                 // advance prev
        curr = next;                 // advance curr
    }

    return prev;   // new head
}
```

---

# Question 2 — Find Middle of Linked List

**"Given a linked list, return the middle node. If two middle nodes exist, return the second one."**

```
Input:  1 → 2 → 3 → 4 → 5 → null
Output: node 3

Input:  1 → 2 → 3 → 4 → null
Output: node 3  (second middle)

Input:  1 → null
Output: node 1
```

**Constraints:** O(n) time, O(1) space. Single pass only — you cannot count nodes first then traverse again.

**The pattern:** Slow-fast pointers. Slow moves 1 step, fast moves 2 steps. When fast hits the end, slow is at the middle.

```
[1] → [2] → [3] → [4] → [5] → null
 s
 f

step 1: s=[2], f=[3]
step 2: s=[3], f=[5]
f.next=null → stop → s=[3] is middle ✅
```

**Think through:**
- What is the condition to keep moving — `fast != null` or `fast.next != null`?
- How does the even length case naturally give you the second middle?

Post your solution when ready.


# Code Review — Find Middle of Linked List

## Verdict: Perfect ✅

Clean, correct, complete. Nothing to fix in the logic.

---

## Correctness Check

```java
if (head == null || head.next == null) return head;  // ✅ null guard

ListNode slow = head;
ListNode fast = head;                                 // ✅ both start at head

while (fast != null && fast.next != null) {           // ✅ correct condition
    slow = slow.next;                                 // ✅ 1 step
    fast = fast.next.next;                            // ✅ 2 steps
}

return slow;                                          // ✅ middle node
```

---

## Why `fast != null && fast.next != null` — Not Just One?

You got this right. Here's why both conditions are needed:

```
Odd length  [1]→[2]→[3]→null:
  after step 1: slow=[2], fast=[3]
  fast.next = null → second condition fails → stop ✅

Even length [1]→[2]→[3]→[4]→null:
  after step 1: slow=[2], fast=[3]
  after step 2: slow=[3], fast=null
  fast = null → first condition fails → stop ✅

If you only checked fast.next != null:
  even case: fast=[4], fast.next=null → stop → slow=[3] ✅ works here
  but: fast=null → fast.next throws NullPointerException ❌

If you only checked fast != null:
  odd case: fast=[3], fast.next=null
  fast.next.next → NullPointerException ❌
```

Both conditions protect against NullPointerException on different cases.

---

## Trace — Even Length (second middle)

```
[1] → [2] → [3] → [4] → null
 s
 f

step 1: slow=[2], fast=[3]
step 2: slow=[3], fast=null → stop

return slow=[3] ✅ second middle
```

---

## Score Card

| Dimension | Your Solution | Senior Bar |
|---|---|---|
| Core logic | ✅ Perfect | ✅ |
| Null guard | ✅ Present | ✅ |
| Termination condition | ✅ Correct | ✅ |
| Even/odd handling | ✅ Correct | ✅ |
| Naming | ✅ camelCase | ✅ |

**First fully clean solution — null guard included, camelCase, perfect logic.** This is what every solution should look like.

---

# Question 3 — Detect Cycle in Linked List

**"Given a linked list, return `true` if it has a cycle, `false` if not."**

```
Input:  1 → 2 → 3 → 4 → 2 (node 4 points back to node 2)
Output: true

Input:  1 → 2 → 3 → null
Output: false

Input:  null
Output: false
```

**Constraints:** O(n) time, O(1) space.

**The pattern:** Same slow-fast pointers. But now instead of stopping at null, you're watching for slow and fast to **meet**.

```
No cycle: fast hits null → return false
Cycle:    fast laps slow → they point to same node → return true
```

**The key question to think through:** Why must slow and fast always meet inside a cycle — why can't fast permanently stay ahead and never land on slow?

```
Think of it like a circular running track:
fast runner laps slow runner — they MUST meet
fast gains exactly 1 step on slow per iteration
gap shrinks by 1 each time → eventually gap = 0 → they meet
```

Solution !!

> I use Floyd's cycle detection — slow moves one step, fast moves two. If there's no cycle, fast hits null and I return false. If there's a cycle, fast eventually laps slow and they meet at the same node — I return true. The gap between them shrinks by exactly one per iteration inside the cycle, so they're guaranteed to meet within one full cycle length


# Code Review — Detect Cycle

## Verdict: Perfect ✅

Second fully clean solution in a row. Every dimension correct.

---

## Correctness Check

```java
if (head == null || head.next == null) return false;  // ✅ null guard

while (fast != null && fast.next != null) {            // ✅ same condition as middle
    slow = slow.next;
    fast = fast.next.next;

    if (slow == fast) return true;                     // ✅ meeting point = cycle
}
return false;                                          // ✅ fast hit null = no cycle
```

---

## One Subtle Thing You Got Right

```java
if (slow == fast) return true;
```

You used `==` not `.equals()`. This is correct — you're comparing **object references** (are these the exact same node in memory?), not values. Two different nodes can have the same value but that doesn't mean there's a cycle.

```java
slow.val == fast.val   // ❌ wrong — different nodes can have same value
slow == fast           // ✅ correct — same node in memory = cycle detected
```

---

## Why The Check Is Inside The Loop — Not Before Movement

```java
// Your version — check AFTER moving:
slow = slow.next;
fast = fast.next.next;
if (slow == fast) return true;   // ✅ correct

// Wrong version — check BEFORE moving:
if (slow == fast) return true;   // ❌ triggers immediately since both start at head
slow = slow.next;
fast = fast.next.next;
```

Both start at `head`. If you check before moving, you'd always return `true` on the first iteration. Checking after the first move avoids the false positive.

---

## Score Card

| Dimension | Your Solution | Senior Bar |
|---|---|---|
| Core logic | ✅ Perfect | ✅ |
| Null guard | ✅ Present | ✅ |
| Reference vs value comparison | ✅ Correct | ✅ |
| Check placement | ✅ After move | ✅ |
| Termination — no cycle | ✅ Correct | ✅ |
| Naming | ✅ camelCase | ✅ |

---

## What To Say In Interview

> "I use Floyd's cycle detection — slow moves one step, fast moves two. If there's no cycle, fast hits null and I return false. If there's a cycle, fast eventually laps slow and they meet at the same node — I return true. The gap between them shrinks by exactly one per iteration inside the cycle, so they're guaranteed to meet within one full cycle length."

---

## Pattern So Far — Slow-Fast Pointer

Both Q2 and Q3 used identical setup:

```java
ListNode slow = head;
ListNode fast = head;

while (fast != null && fast.next != null) {
    slow = slow.next;
    fast = fast.next.next;
    // Q2: check if fast hit end → slow is middle
    // Q3: check if slow == fast → cycle exists
}
```

Same skeleton, different exit condition. This is the slow-fast template — burn it in.

---

# Question 4 — Find Nth Node From End

**"Given a linked list and integer `n`, return the nth node from the end."**

```
Input:  1 → 2 → 3 → 4 → 5 → null, n = 2
Output: node 4  (2nd from end)

Input:  1 → 2 → 3 → null, n = 3
Output: node 1  (3rd from end = first node)

Input:  1 → null, n = 1
Output: node 1
```

**Constraints:** O(n) time, O(1) space. Single pass only.

**The trick:** Two pointers again, but this time they start at different positions. Create a gap of exactly `n` nodes between them. When the front pointer hits null, the back pointer is at the answer.

```
n = 2
[1] → [2] → [3] → [4] → [5] → null

Step 1: advance fast n steps ahead
fast moves 2 steps:
slow=[1], fast=[3]

Gap of 2 between them.

Step 2: move both together until fast hits null
slow=[2], fast=[4]
slow=[3], fast=[5]
slow=[4], fast=null → stop

return slow=[4] ✅ 2nd from end
```

Post your solution when ready.

The idea of creating a gap is right, but the implementation has a structural problem. Let me show you.

The Bug — advanceNTimes Called Every Iteration
javawhile (front != null) {
    back = back.next;                    // back moves 1 step
    front = advanceNTimes(front, N);     // front moves N steps every iteration
}
You're moving front by N steps every iteration, not 1. So the gap isn't fixed — it grows every loop.
Trace with [1]→[2]→[3]→[4]→[5], n=2:
start: front=[1], back=[1]

iter 1: back=[2], front=advanceNTimes([1],2)=[3]   gap=1 node
iter 2: back=[3], front=advanceNTimes([3],2)=[5]   gap=2 nodes
iter 3: back=[4], front=advanceNTimes([5],2)=null  loop ends

return back=[4] ✅ — happens to work here

but with n=3, [1]→[2]→[3]→[4]→[5]:
iter 1: back=[2], front=advanceNTimes([1],3)=[4]
iter 2: back=[3], front=advanceNTimes([4],3)=null  loop ends
return back=[3] ❌ wrong — should be [3] actually... 

let me try n=1:
iter 1: back=[2], front=advanceNTimes([1],1)=[2]
iter 2: back=[3], front=advanceNTimes([2],1)=[3]
iter 3: back=[4], front=advanceNTimes([3],1)=[4]
iter 4: back=[5], front=advanceNTimes([4],1)=[5]
iter 5: back=null, front=advanceNTimes([5],1)=null
return back=null ❌ wrong — should be [5]
The approach accidentally works for some inputs but fails for others. Not reliable.

The Correct Mental Model
Two phases — not one loop:
Phase 1: advance front exactly N steps
         → creates a fixed gap of N between front and back
         → back stays at head

Phase 2: move BOTH one step at a time
         → gap stays fixed at N
         → when front hits null, back is exactly N from end
[1] → [2] → [3] → [4] → [5] → null, n=2

Phase 1 — advance front 2 steps:
back=[1], front=[3]

Phase 2 — move both until front=null:
back=[2], front=[4]
back=[3], front=[5]
back=[4], front=null → stop

return back=[4] ✅



# Code Review — Nth Node From End

## Verdict: Wrong Approach ⚠️

The idea of creating a gap is right, but the implementation has a structural problem. Let me show you.

---

## The Bug — advanceNTimes Called Every Iteration

```java
while (front != null) {
    back = back.next;                    // back moves 1 step
    front = advanceNTimes(front, N);     // front moves N steps every iteration
}
```

You're moving `front` by N steps every iteration, not 1. So the gap isn't fixed — it grows every loop.

Trace with `[1]→[2]→[3]→[4]→[5]`, n=2:

```
start: front=[1], back=[1]

iter 1: back=[2], front=advanceNTimes([1],2)=[3]   gap=1 node
iter 2: back=[3], front=advanceNTimes([3],2)=[5]   gap=2 nodes
iter 3: back=[4], front=advanceNTimes([5],2)=null  loop ends

return back=[4] ✅ — happens to work here

but with n=3, [1]→[2]→[3]→[4]→[5]:
iter 1: back=[2], front=advanceNTimes([1],3)=[4]
iter 2: back=[3], front=advanceNTimes([4],3)=null  loop ends
return back=[3] ❌ wrong — should be [3] actually... 

let me try n=1:
iter 1: back=[2], front=advanceNTimes([1],1)=[2]
iter 2: back=[3], front=advanceNTimes([2],1)=[3]
iter 3: back=[4], front=advanceNTimes([3],1)=[4]
iter 4: back=[5], front=advanceNTimes([4],1)=[5]
iter 5: back=null, front=advanceNTimes([5],1)=null
return back=null ❌ wrong — should be [5]
```

The approach accidentally works for some inputs but fails for others. Not reliable.

---

## The Correct Mental Model

Two phases — not one loop:

```
Phase 1: advance front exactly N steps
         → creates a fixed gap of N between front and back
         → back stays at head

Phase 2: move BOTH one step at a time
         → gap stays fixed at N
         → when front hits null, back is exactly N from end
```

```
[1] → [2] → [3] → [4] → [5] → null, n=2

Phase 1 — advance front 2 steps:
back=[1], front=[3]

Phase 2 — move both until front=null:
back=[2], front=[4]
back=[3], front=[5]
back=[4], front=null → stop

return back=[4] ✅
```

---

## Correct Solution

```java
public static ListNode findNthFromEnd(ListNode head, int n) {
    if (head == null) return null;

    ListNode front = head;
    ListNode back = head;

    // Phase 1: advance front n steps ahead
    for (int i = 0; i < n; i++) {
        if (front == null) return null;  // n > list length
        front = front.next;
    }

    // Phase 2: move both until front hits null
    while (front != null) {
        front = front.next;
        back = back.next;
    }

    return back;
}
```

---

## Trace — Correct Solution

```
[1]→[2]→[3]→[4]→[5], n=2

Phase 1:
i=0: front=[2]
i=1: front=[3]
back still=[1]

Phase 2:
front=[4], back=[2]
front=[5], back=[3]
front=null, back=[4] → stop

return [4] ✅

Edge — n equals list length:
[1]→[2]→[3], n=3
Phase 1: front=null after 3 steps
Phase 2: while(null) → skip
return back=[1] ✅

Edge — n > list length:
[1]→[2], n=5
Phase 1: front hits null before i=5
return null ✅
```

---

## What You Got Right

- Gap concept — correct instinct
- Two pointer names `front` and `back` — clear
- Null check inside advance — good defensive thinking

## What Went Wrong

- Mixed two phases into one loop — front should only advance N times upfront, then move 1 step per iteration like back

---

## Score Card

| Dimension | Your Solution | Senior Bar |
|---|---|---|
| Gap concept | ✅ Right idea | ✅ |
| Phase separation | ❌ Mixed into one loop | ✅ |
| Correctness | ❌ Fails some inputs | ✅ |
| Null guard | ✅ Present | ✅ |
| n > length edge case | ✅ Handled | ✅ |
| Naming | ✅ camelCase | ✅ |

---

# Question 5 — Merge Two Sorted Linked Lists

**"Given two sorted linked lists, merge them into one sorted linked list and return the head."**

```
Input:  1 → 3 → 5 → null
        2 → 4 → 6 → null
Output: 1 → 2 → 3 → 4 → 5 → 6 → null

Input:  1 → 2 → 4 → null
        1 → 3 → 4 → null
Output: 1 → 1 → 2 → 3 → 4 → 4 → null

Input:  null, 1 → 2 → null
Output: 1 → 2 → null
```

**Constraints:** O(n+m) time, O(1) space. Do not create new nodes — reuse existing ones by relinking.

**The trick:** Use a dummy head node to avoid special-casing the first node. Then walk both lists, always picking the smaller current node.

```
dummy → ?

l1=[1]→[3]→[5]
l2=[2]→[4]→[6]

compare l1 and l2:
1 < 2 → attach l1, advance l1
dummy → [1]
2 < 3 → attach l2, advance l2
dummy → [1] → [2]
3 < 4 → attach l1, advance l1
...and so on
```

**Think through:**
- What is a dummy node and why does it simplify the code?
- What do you do when one list runs out before the other?

Post your solution when ready.

# Code Review — Merge Two Sorted Lists

## Verdict: Almost — One Bug ⚠️

Core logic and dummy node pattern are correct. One bug at the end.

---

## The Bug — Leftover Nodes Not Attached

```java
while (head1 != null && head2 != null) { ... }

return mergedHead.next;   // ❌ what about remaining nodes?
```

When one list runs out before the other, the while loop stops. The remaining nodes in the longer list are never attached.

Trace:

```
l1 = [1]→[3]→[5]
l2 = [2]→[4]→[6]

After loop: dummy=[6], current1=[5] still unattached ❌
```

Fix — after the loop, attach whichever list still has nodes:

```java
if (current1 != null) dummy.next = current1;
if (current2 != null) dummy.next = current2;
```

This works because the remaining nodes are already sorted — you just attach the rest of the chain directly.

---

## Second Bug — Loop Uses head1/head2 Instead of current1/current2

```java
while (head1 != null && head2 != null) {  // ❌ head1/head2 never move
    if (current1.val < current2.val) {     // current1/current2 move
```

`head1` and `head2` never advance so this loop runs forever. You have two sets of pointers but only one set moves. Pick one — either use `current1`/`current2` throughout, or drop them and use `head1`/`head2` directly.

---

## Corrected Solution

```java
public static ListNode mergeSortedLists(ListNode head1, ListNode head2) {
    if (head1 == null) return head2;
    if (head2 == null) return head1;

    ListNode dummy = new ListNode(-1);
    ListNode curr = dummy;              // curr builds the merged list

    while (head1 != null && head2 != null) {
        if (head1.val <= head2.val) {
            curr.next = head1;
            head1 = head1.next;
        } else {
            curr.next = head2;
            head2 = head2.next;
        }
        curr = curr.next;              // advance merged list pointer
    }

    // Attach remaining nodes
    if (head1 != null) curr.next = head1;
    if (head2 != null) curr.next = head2;

    return dummy.next;                 // dummy.next is real head
}
```

---

## Trace — Corrected

```
head1=[1]→[3]→[5], head2=[2]→[4]→[6]
dummy=[-1], curr=[-1]

iter 1: 1<2 → curr.next=[1], head1=[3], curr=[1]
iter 2: 2<3 → curr.next=[2], head2=[4], curr=[2]
iter 3: 3<4 → curr.next=[3], head1=[5], curr=[3]
iter 4: 4<5 → curr.next=[4], head2=[6], curr=[4]
iter 5: 5<6 → curr.next=[5], head1=null, curr=[5]
loop ends — head1=null

curr.next = head2=[6]→null   ✅ attach remaining

return dummy.next=[1]→[2]→[3]→[4]→[5]→[6] ✅
```

---

## The Dummy Node Pattern — Why It Works

```java
ListNode dummy = new ListNode(-1);
ListNode curr = dummy;
```

Without dummy you'd need special case code for the first node:

```java
// Without dummy — messy:
ListNode head = null;
if (head1.val < head2.val) {
    head = head1;           // special case first node
    head1 = head1.next;
} else { ... }
```

With dummy, every node including the first is handled identically — just `curr.next = node`. The dummy is a placeholder that absorbs the first-node special case. You return `dummy.next` which skips the placeholder.

**Remember this pattern** — it appears in merge k lists, add two numbers, and several other linked list problems.

---

# Question 6 — Linked List — LRU Cache

This is the most important linked list question for JPMC/Goldman. Combines HashMap + DoublyLinkedList. Before I post it — do you want one more pure linked list question first (merge k sorted lists), or go straight to LRU Cache?

# LRU Cache — Deep Dive

## What Is an LRU Cache?

LRU = Least Recently Used. It's a cache with a fixed capacity that evicts the least recently used item when full.

**Real world context — directly relevant to your Lloyds work:**
- API response caching
- Database query result caching
- Session management in banking apps

---

## The Operations

```
LRUCache(int capacity)  — initialise with fixed size

get(int key)            — return value if key exists, else -1
                          marks this key as recently used

put(int key, int value) — insert or update key
                          if at capacity, evict least recently used first
                          marks this key as recently used
```

**Both operations must be O(1).**

---

## Why You Need Both a HashMap AND a DoublyLinkedList

Think about what you need:

```
O(1) lookup by key          → HashMap
O(1) track usage order      → DoublyLinkedList
O(1) evict least used       → tail of DoublyLinkedList
O(1) mark as recently used  → move node to head
```

Neither structure alone gives you everything:
- HashMap alone — O(1) lookup but no usage order
- LinkedList alone — O(1) order tracking but O(n) lookup

Together they give you O(1) for everything.

---

## The Data Structure Design

```
HashMap:  key → ListNode

DoublyLinkedList:
  head ↔ [most recent] ↔ ... ↔ [least recent] ↔ tail

head and tail are DUMMY nodes — they never hold real data
Real nodes sit between them

get(key):
  find node via HashMap → O(1)
  move node to front    → O(1)
  return value

put(key, value):
  if key exists → update value, move to front
  if key new    → create node, add to front, add to HashMap
  if over capacity → remove tail.prev from list AND HashMap
```

---

## Why DoublyLinkedList — Not Singly?

To remove a node in O(1) you need access to its previous node. Singly linked list requires O(n) traversal to find prev. Doubly linked gives you `node.prev` directly.

```java
class Node {
    int key;
    int val;
    Node prev;
    Node next;

    Node(int key, int val) {
        this.key = key;
        this.val = val;
    }
}
```

**Why store `key` in the node?** When you evict the tail, you need to remove it from the HashMap too. You need the key to do that.

---

## The Four Helper Operations

Everything reduces to these four:

```java
// Add node right after head (most recent position)
private void addToFront(Node node)

// Remove a node from its current position
private void removeNode(Node node)

// Move existing node to front (= removeNode + addToFront)
private void moveToFront(Node node)

// Remove least recently used (= remove tail.prev)
private void removeLRU()
```

---

## Visual — What Happens on Each Operation

```
capacity = 3
Initial: head ↔ tail

put(1,1): head ↔ [1] ↔ tail
put(2,2): head ↔ [2] ↔ [1] ↔ tail
put(3,3): head ↔ [3] ↔ [2] ↔ [1] ↔ tail

get(1):   head ↔ [1] ↔ [3] ↔ [2] ↔ tail   (1 moved to front)

put(4,4): capacity exceeded → evict tail.prev = [2]
          head ↔ [4] ↔ [1] ↔ [3] ↔ tail

get(2):   return -1 (evicted)
```

---

## Now Write It

```java
class LRUCache {
    // fields here

    public LRUCache(int capacity) { }

    public int get(int key) { }

    public void put(int key, int value) { }

    private void addToFront(Node node) { }

    private void removeNode(Node node) { }
}
```

**Fill in all five methods.** The four helpers are the key — get and put are just combinations of them.

**Things to think through:**
- What fields does LRUCache need?
- In `addToFront` — which pointers change?
- In `removeNode` — which pointers change?
- In `put` — what are the two cases (key exists vs key new)?
- When evicting — why do you need the key stored in the node?

Take your time — this is the hardest linked list problem. Post when ready.


# Merge K Sorted Lists — Deep Dive

## The Problem

**"Given an array of k sorted linked lists, merge them all into one sorted linked list and return the head."**

```
Input:  [
          1 → 4 → 7 → null,
          2 → 5 → 8 → null,
          3 → 6 → 9 → null
        ]
Output: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → null

Input:  [
          1 → 3 → 5 → null,
          2 → 4 → null
        ]
Output: 1 → 2 → 3 → 4 → 5 → null

Input:  []
Output: null

Input:  [null, 1→2→null]
Output: 1 → 2 → null
```

**Constraints:** O(n log k) time where n = total nodes, k = number of lists.

---

## Why Not Reuse Merge Two Lists Repeatedly?

You already know how to merge two sorted lists. Naive approach — merge list 1 and 2, then merge result with 3, then with 4...

```
k=4 lists, n total nodes

merge L1+L2     → O(n)
merge result+L3 → O(n)
merge result+L4 → O(n)

Total: O(k×n)   ← too slow for large k
```

Every merge touches the already-merged nodes again. You're reprocessing the same nodes k times.

---

## The Right Tool — Min Heap

A min heap always gives you the smallest element in O(log k). The insight:

```
At any point you have k candidate nodes — 
one from the front of each list.
You always want the smallest one next.
That's exactly what a min heap does.
```

Algorithm:

```
1. Push head of every list into min heap
2. Poll smallest node from heap → add to result
3. If that node has a next → push next into heap
4. Repeat until heap empty
```

Trace:

```
Lists:
L1: 1→4→7
L2: 2→5→8
L3: 3→6→9

Heap initially: [1,2,3]  (heads of all lists)

Poll 1 → result: 1, push 4 → heap: [2,3,4]
Poll 2 → result: 1→2, push 5 → heap: [3,4,5]
Poll 3 → result: 1→2→3, push 6 → heap: [4,5,6]
Poll 4 → result: 1→2→3→4, push 7 → heap: [5,6,7]
Poll 5 → result: 1→2→3→4→5, push 8 → heap: [6,7,8]
Poll 6 → result: 1→2→3→4→5→6, push 9 → heap: [7,8,9]
Poll 7 → result: ..→7, no next → heap: [8,9]
Poll 8 → result: ..→8, no next → heap: [9]
Poll 9 → result: ..→9, no next → heap: []

Done: 1→2→3→4→5→6→7→8→9 ✅
```

---

## Complexity

```
Heap size at any time: at most k nodes
Each poll/push operation: O(log k)
Total nodes processed: n

Total time: O(n log k)   ← much better than O(nk)
Space: O(k)              ← heap holds at most k nodes
```

---

## The Java Heap Setup

Java's `PriorityQueue` is a min heap by default. You need a custom comparator for `ListNode`:

```java
PriorityQueue<ListNode> heap = new PriorityQueue<>(
    (a, b) -> a.val - b.val   // min heap by node value
);
```

---

## Now Write It

```java
public static ListNode mergeKLists(ListNode[] lists) {
    // your code here
}
```

**Things to think through:**
- What do you push into the heap initially?
- What is the loop condition?
- When do you push a node's next into the heap?
- What handles null lists in the input array?

Post your solution when ready.

___
# Heaps

# Heap — From Scratch

## What Is a Heap?

A heap is a **complete binary tree** with one rule:

**Min Heap:** Every parent is smaller than its children.
**Max Heap:** Every parent is larger than its children.

```
Min Heap:
        1
       / \
      3   2
     / \ / \
    7  4 5  6

Rule: parent < both children — always
Root is ALWAYS the smallest element
```

That one rule gives you something powerful — **you always know where the smallest element is. It's at the root. O(1) access.**

---

## What Operations Does a Heap Support?

```
peek()   → see the min element      O(1)
poll()   → remove the min element   O(log n)
add()    → insert a new element     O(log n)
size()   → count of elements        O(1)
```

## That's it. A heap is not for searching. Not for random access. Only for repeatedly getting the minimum (or maximum) efficiently.

---

## How Does It Work Physically?

### Adding an Element — "Bubble Up"

Add at the bottom, then bubble up until heap rule is satisfied:

```
Add 1 to this heap:
        2
       / \
      3   4

Step 1 — add at bottom:
        2
       / \
      3   4
     /
    1

Step 2 — 1 < parent(3) → swap:
        2
       / \
      1   4
     /
    3

Step 3 — 1 < parent(2) → swap:
        1
       / \
      2   4
     /
    3

Done. Heap rule satisfied.
```

### Removing Min — "Bubble Down"

Remove root, put last element at root, bubble down:

```
Remove min from:
        1
       / \
      2   4
     /
    3

Step 1 — remove root, put last element there:
        3
       / \
      2   4

Step 2 — 3 > smaller child(2) → swap:
        2
       / \
      3   4

Done. Heap rule satisfied.
```

---

## How Is It Stored in Memory?

Here's the clever part — a heap is stored as a **flat array**, not as tree nodes with pointers:

```
        1
       / \
      3   2
     / \
    7   4

Array: [1, 3, 2, 7, 4]
        0  1  2  3  4

For any index i:
  parent     = (i-1) / 2
  leftChild  = 2*i + 1
  rightChild = 2*i + 2
```

No pointers needed. The math gives you the tree structure for free.

---

## Why Is Poll O(log n)?

The tree has height log n (it's complete and balanced). Bubble down travels at most from root to leaf — that's log n swaps.

```
n=8 nodes → height=3 → at most 3 swaps to bubble down
n=1000000 → height=20 → at most 20 swaps
```

This is why heap is so powerful — even with a million elements, you find and remove the minimum in 20 operations.

---

## Heap vs Sorted Array vs LinkedList

| Operation | Sorted Array | Sorted LinkedList | Heap |
|---|---|---|---|
| Find min | O(1) | O(1) | O(1) |
| Remove min | O(n) shift | O(1) | O(log n) |
| Insert | O(n) shift | O(n) search | O(log n) |

##Heap wins when you need **repeated min/max extraction with frequent insertions.**

---

## Java PriorityQueue — The Heap Implementation

```java
// Min heap — default
PriorityQueue<Integer> minHeap = new PriorityQueue<>();

// Max heap — reverse comparator
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());

// Custom comparator — min heap by string length
PriorityQueue<String> heap = new PriorityQueue<>((a, b) -> a.length() - b.length());

// Operations
minHeap.add(5);        // insert — O(log n)
minHeap.peek();        // see min without removing — O(1)
minHeap.poll();        // remove and return min — O(log n)
minHeap.size();        // count — O(1)
minHeap.isEmpty();     // check empty — O(1)
```

---

## Concrete Example — Top 3 Smallest Numbers

```java
int[] arr = {5, 2, 8, 1, 9, 3, 7};
PriorityQueue<Integer> heap = new PriorityQueue<>();

for (int n : arr) heap.add(n);
// heap internally: [1, 2, 7, 5, 9, 8, 3] (array representation)
// but logically always gives you the min first

System.out.println(heap.poll()); // 1
System.out.println(heap.poll()); // 2
System.out.println(heap.poll()); // 3
```

No sorting needed. Heap does it in O(n log n) — same as sort, but you get elements one by one on demand.

---

## The Key Mental Model

**Think of a heap as a magic bag:**
- You throw numbers in randomly
- Every time you reach in, you always pull out the smallest one
- Insertion and extraction both cost O(log n)

That's it. Don't think about the tree structure when using it. Just know:
- `add()` → throw something in
- `poll()` → pull out the smallest
- `peek()` → look at the smallest without removing

---

# When Do You Reach for a Heap?

```
"Find the k largest / k smallest elements"     → heap
"Merge k sorted lists"                         → heap
"Always process the minimum next"              → heap
"Median from a stream"                         → two heaps
"Task scheduling by priority"                  → heap
```

The signal phrase is: **"at each step, I need the current minimum (or maximum)"**

---

Now that you understand what a heap is — go back and try **merge k sorted lists**. The algorithm is:

```
1. Push all list heads into min heap
2. Poll min → attach to result
3. Push polled node's next into heap
4. Repeat until heap empty
```

Post your solution when ready.