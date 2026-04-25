# Stacks & Queues — Deep Dive

## The Core Idea

**Stack:** Last In First Out (LIFO). Think of a stack of plates — you add and remove from the top only.

**Queue:** First In First Out (FIFO). Think of a bank queue — first person in is first person served.

```
Stack:          Queue:
push(1)         enqueue(1)
push(2)         enqueue(2)
push(3)         enqueue(3)
pop() → 3       dequeue() → 1
pop() → 2       dequeue() → 2
pop() → 1       dequeue() → 3
```

---

## Java Implementations

```java
// Stack — use Deque, not Stack class (Stack is legacy)
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1);      // add to top
stack.pop();        // remove from top
stack.peek();       // see top without removing
stack.isEmpty();    // check empty

// Queue
Queue<Integer> queue = new LinkedList<>();
queue.offer(1);     // add to back
queue.poll();       // remove from front
queue.peek();       // see front without removing
queue.isEmpty();    // check empty
```

**Why `Deque` over `Stack` class?**
`Stack` in Java extends `Vector` which is synchronized — unnecessary overhead. `ArrayDeque` is faster, unsynchronized, and the recommended replacement.

---

## The Four Patterns

```
Pattern 1 — Matching/Validation
  Use when: brackets, tags, open/close pairs
  Signal:   "valid", "matching", "balanced"

Pattern 2 — Monotonic Stack
  Use when: next greater/smaller element
  Signal:   "next larger", "previous smaller", 
            "largest rectangle"

Pattern 3 — Stack simulating Queue / Queue simulating Stack
  Use when: design questions
  Signal:   "implement X using Y"

Pattern 4 — BFS using Queue
  Use when: level order traversal, shortest path
  Signal:   "level by level", "minimum steps"
```

---

## Pattern 1 Deep Dive — Matching/Validation

The mental model: every time you see an opening bracket, push it. Every time you see a closing bracket, check if it matches the top of the stack.

```
s = "({[]})"

( → push        stack: [(]
{ → push        stack: [(, {]
[ → push        stack: [(, {, []
] → matches [   stack: [(, {]     pop
} → matches {   stack: [(]        pop
) → matches (   stack: []         pop

stack empty → valid ✅
```

---

## Question 1 — Valid Parentheses

**"Given a string containing only `(`, `)`, `{`, `}`, `[`, `]`, determine if it is valid. A string is valid if every opening bracket is closed in the correct order."**

```
Input:  "()"        Output: true
Input:  "()[]{}"    Output: true
Input:  "(]"        Output: false
Input:  "([)]"      Output: false
Input:  "{[]}"      Output: true
Input:  "{"         Output: false
Input:  ""          Output: true
```

**Constraints:** O(n) time, O(n) space.

**Things to think through:**
- What do you push onto the stack?
- What do you do when you see a closing bracket?
- What are the two failure conditions?
- What does an empty string return?

Post your solution when ready.


# Code Review — Valid Parentheses

## Verdict: Perfect ✅

Clean, correct, complete. Every case handled.

---

## Correctness Check

```java
if (c == '(' || c == '{' || c == '[') {
    stack.push(c);                     // ✅ push opening brackets

} else {
    if (stack.isEmpty()) return false; // ✅ closing bracket with nothing to match

    Character top = stack.peek();
    if (c == ')' && top == '(' ||
        c == '}' && top == '{' ||
        c == ']' && top == '[') {
        stack.pop();                   // ✅ matched — pop
    } else {
        return false;                  // ✅ wrong match
    }
}

return true;                           // ✅ all matched
```

---

## The Two Failure Conditions — Both Handled

```java
// Failure 1 — closing bracket but stack empty
// e.g. s = ")("
if (stack.isEmpty()) return false;

// Failure 2 — closing bracket doesn't match top
// e.g. s = "(]"
else return false;
```

**There's a third failure condition you're handling implicitly:**

```java
// Failure 3 — opened brackets never closed
// e.g. s = "(("
// stack still has elements after loop ends
// return true would be wrong — but wait...

return stack.isEmpty();  // ← this is more explicit
```

Actually your `return true` is wrong for `s = "(("`:

```
s = "(("
push ( → stack: [(]
push ( → stack: [(, (]
loop ends
return true ❌ — stack not empty, brackets unclosed
```

Fix:

```java
return stack.isEmpty();  // true only if all brackets matched and closed
```

---

## Corrected Solution

```java
public static boolean isValidBrackets(String s) {
    if (s == null || s.length() == 0) return true;

    Deque<Character> stack = new ArrayDeque<>();

    for (char c : s.toCharArray()) {
        if (c == '(' || c == '{' || c == '[') {
            stack.push(c);
        } else {
            if (stack.isEmpty()) return false;
            char top = stack.peek();
            if (c == ')' && top == '(' ||
                c == '}' && top == '{' ||
                c == ']' && top == '[') {
                stack.pop();
            } else {
                return false;
            }
        }
    }

    return stack.isEmpty();   // ✅ handles unclosed brackets
}
```

---

## Trace — All Cases

```
s = "{[]}"
push { → [{]
push [ → [{, []
] matches [ → pop → [{]
} matches { → pop → []
stack empty → true ✅

s = "(("
push ( → [(]
push ( → [(, (]
loop ends
stack.isEmpty() = false → false ✅

s = "(]"
push ( → [(]
] vs ( → no match → false ✅

s = ""
stack empty → true ✅
```

---

## Score Card

| Dimension | Your Solution | Senior Bar |
|---|---|---|
| Opening bracket push | ✅ | ✅ |
| Empty stack check | ✅ | ✅ |
| Bracket pair matching | ✅ | ✅ |
| Unclosed brackets | ❌ return true | ✅ return stack.isEmpty() |
| Null/empty guard | ❌ Missing | ✅ |
| Char vs Character | ⚠️ use primitive char | ✅ |

---

## One Small Thing — `char` not `Character`

```java
Character top = stack.peek();   // ⚠️ boxed type
char top = stack.peek();        // ✅ primitive — cleaner
```

Inside the loop you already used `Character c` in the for-each. For local variables, prefer primitive `char`. Only use `Character` when you need an object — like storing in a collection.

---

Ready for Q2 — **Min Stack**?

# Stacks Q2 — Min Stack

## The Problem

**"Design a stack that supports push, pop, peek and retrieving the minimum element — all in O(1) time."**

```
MinStack stack = new MinStack();
stack.push(5);
stack.push(3);
stack.push(7);
stack.push(1);
stack.getMin(); // 1
stack.pop();
stack.getMin(); // 3  ← not 5, not 7
stack.pop();
stack.getMin(); // 3
stack.pop();
stack.getMin(); // 5
```

**Constraints:** All operations O(1). No size limit.

---

## Why Is This Hard?

`getMin()` in O(1) sounds easy — just track a `currentMin` variable:

```java
int currentMin = Integer.MAX_VALUE;

push(x):
    currentMin = Math.min(currentMin, x)  // easy

getMin():
    return currentMin  // easy

pop():
    // what now? if you pop the minimum,
    // what is the new minimum?
    // you've lost that information ❌
```

When you pop the current minimum, you need to know what the previous minimum was. A single variable can't tell you that.

---

## The Insight

Every element you push potentially changes the minimum. So track the minimum **at every level of the stack** — not just globally.

```
push(5): min at this level = 5
push(3): min at this level = 3
push(7): min at this level = 3
push(1): min at this level = 1

pop() removes 1 → min at this level = 3  ← still know it
pop() removes 7 → min at this level = 3  ← still know it
pop() removes 3 → min at this level = 5  ← still know it
```

**How:** Use two stacks — one for values, one for minimums.

```
After push(5), push(3), push(7), push(1):

valueStack:  [5, 3, 7, 1]   ← top is 1
minStack:    [5, 3, 3, 1]   ← top is always current min

pop() removes 1 from both:
valueStack:  [5, 3, 7]
minStack:    [5, 3, 3]   ← getMin() = 3 ✅
```

---

## The Two Stack Rules

```
push(x):
    push x onto valueStack
    push min(x, minStack.peek()) onto minStack

pop():
    pop from valueStack
    pop from minStack

getMin():
    return minStack.peek()

peek():
    return valueStack.peek()
```

---

## Now Write It

```java
class MinStack {
    // fields here

    public MinStack() { }

    public void push(int val) { }

    public void pop() { }

    public int peek() { }

    public int getMin() { }
}
```

**Things to think through:**
- What happens when minStack is empty on first push?
- What is the minStack value when pushed element is larger than current min?
- What if all elements are the same value?

Post your solution when ready.

# Code Review — Min Stack

## Verdict: Clever but One Bug ⚠️

You implemented using arrays instead of Deque — more work but shows deeper understanding. One logic bug in `push()`.

---

## The Bug — minStack Not Updated When Value >= Current Min

```java
if (val < currentMin) {
    minTopIndex++;
    minStack[minTopIndex] = val;
}
// ❌ what if val >= currentMin? nothing pushed to minStack
```

This means `valueStack` and `minStack` grow at different rates. When you `pop()`, you compare popped value against `minStack` top — but the indices are misaligned.

Trace:

```
push(3): valueStack=[3], minStack=[3]
push(5): valueStack=[3,5], minStack=[3]  ← minStack didn't grow
push(1): valueStack=[3,5,1], minStack=[3,1]

pop() removes 1:
  poppedValue=1, minStack top=1 → match → minTopIndex--
  valueStack=[3,5], minStack=[3] ✅ fine here

pop() removes 5:
  poppedValue=5, minStack top=3 → no match → minTopIndex stays
  valueStack=[3], minStack=[3] ✅ fine here

pop() removes 3:
  poppedValue=3, minStack top=3 → match → minTopIndex--
  valueStack=[], minStack=[] ✅ 

Seems okay here but try this:

push(3): valueStack=[3], minStack=[3]
push(3): valueStack=[3,3], minStack=[3]  ← duplicate not pushed
pop():
  poppedValue=3, minStack top=3 → match → minTopIndex--
  valueStack=[3], minStack=[]  ❌ minStack empty but valueStack has element
getMin() → minTopIndex=-1 → throws exception ❌
```

---

## The Fix — Always Push to minStack

```java
public void push(int val) {
    valueTopIndex++;
    valueStack[valueTopIndex] = val;

    minTopIndex++;
    if (minTopIndex == 0) {
        minStack[minTopIndex] = val;                              // first element
    } else {
        minStack[minTopIndex] = Math.min(val, minStack[minTopIndex - 1]);  // min so far
    }
}
```

Now both stacks always grow and shrink together — indices always in sync. `pop()` can simply decrement both:

```java
public void pop() {
    if (valueTopIndex < 0) throw new RuntimeException("Stack is empty");
    valueTopIndex--;
    minTopIndex--;
}
```

---

## Corrected Full Solution

```java
public class MinStack {

    private int[] valueStack;
    private int[] minStack;
    private int top;   // single index — both stacks always same size

    public MinStack(int maxSize) {
        valueStack = new int[maxSize];
        minStack = new int[maxSize];
        top = -1;
    }

    public void push(int val) {
        top++;
        valueStack[top] = val;
        if (top == 0) {
            minStack[top] = val;
        } else {
            minStack[top] = Math.min(val, minStack[top - 1]);
        }
    }

    public void pop() {
        if (top < 0) throw new RuntimeException("Stack is empty");
        top--;
    }

    public int peek() {
        if (top < 0) throw new RuntimeException("Stack is empty");
        return valueStack[top];
    }

    public int getMin() {
        if (top < 0) throw new RuntimeException("Stack is empty");
        return minStack[top];
    }
}
```

**One index for both stacks** — since they always move together, you don't need `valueTopIndex` and `minTopIndex` separately. Simpler and less error prone.

---

## Trace — Corrected

```
push(5): top=0, valueStack=[5], minStack=[5]
push(3): top=1, valueStack=[5,3], minStack=[5,3]
push(7): top=2, valueStack=[5,3,7], minStack=[5,3,3]
push(1): top=3, valueStack=[5,3,7,1], minStack=[5,3,3,1]

getMin() = minStack[3] = 1 ✅

pop(): top=2
getMin() = minStack[2] = 3 ✅

pop(): top=1
getMin() = minStack[1] = 3 ✅

pop(): top=0
getMin() = minStack[0] = 5 ✅

push(3): top=1, valueStack=[5,3], minStack=[5,3]
push(3): top=2, valueStack=[5,3,3], minStack=[5,3,3]
pop(): top=1
getMin() = minStack[1] = 3 ✅  duplicate case handled
```

---

## What You Got Right

```java
// Detecting min is gone when popped ✅ right idea
if (poppedValue == minStack[minTopIndex]) {
    minTopIndex--;
}

// Empty stack guard with exception ✅ production thinking
throw new RuntimeException("Stack is empty");

// Array-based implementation ✅ shows deeper understanding
// Most candidates just use two Deques
```

---

## Interview Standard — Deque Version

For reference, the version most candidates write:

```java
class MinStack {
    Deque<Integer> valueStack = new ArrayDeque<>();
    Deque<Integer> minStack = new ArrayDeque<>();

    public void push(int val) {
        valueStack.push(val);
        int min = minStack.isEmpty() ? val : Math.min(val, minStack.peek());
        minStack.push(min);
    }

    public void pop() {
        valueStack.pop();
        minStack.pop();
    }

    public int peek() { return valueStack.peek(); }
    public int getMin() { return minStack.peek(); }
}
```

Your array version is more impressive if correct — but the Deque version is safer under interview pressure. Know both.

---

## Score Card

| Dimension | Your Solution | Senior Bar |
|---|---|---|
| Two stack concept | ✅ Correct | ✅ |
| Array implementation | ✅ Impressive | ✅ |
| minStack sync with valueStack | ❌ Out of sync | ✅ |
| Duplicate value handling | ❌ Breaks | ✅ |
| Empty stack guard | ✅ Exception thrown | ✅ |
| Single index insight | ❌ Two indices | ✅ |

---

Ready for Q3 — **Implement Queue Using Two Stacks?**

# Stacks Q3 — Implement Queue Using Two Stacks

## The Problem

**"Implement a queue using only two stacks. The queue must support enqueue, dequeue and peek — all amortized O(1)."**

```
MyQueue queue = new MyQueue();
queue.enqueue(1);
queue.enqueue(2);
queue.enqueue(3);
queue.peek();     // 1  ← first in
queue.dequeue();  // 1
queue.dequeue();  // 2
queue.enqueue(4);
queue.dequeue();  // 3
queue.dequeue();  // 4
```

**Constraints:** O(1) amortized per operation. Only stack operations allowed — push, pop, peek, isEmpty.

---

## The Core Problem

Stack is LIFO. Queue is FIFO. They're opposites.

```
enqueue 1,2,3 onto a stack:
stack: [1, 2, 3]  ← top is 3
pop()  → 3  ❌ want 1
```

You need to reverse the order somehow. Two stacks can do this:

```
Stack 1 — inbox:   receives all pushes
Stack 2 — outbox:  serves all pops

When outbox is empty and you need to dequeue:
  pour everything from inbox into outbox
  this reverses the order
  now pop from outbox gives you oldest element first
```

---

## Visual

```
enqueue(1), enqueue(2), enqueue(3):
inbox:  [1,2,3]  top=3
outbox: []

dequeue():
  outbox empty → pour inbox into outbox:
  inbox:  []
  outbox: [3,2,1]  top=1  ← correct FIFO order

  pop outbox → 1 ✅

dequeue():
  outbox not empty → pop directly
  outbox: [3,2]  top=2
  pop → 2 ✅

enqueue(4):
  push to inbox only
  inbox:  [4]
  outbox: [3,2]

dequeue():
  outbox not empty → pop directly
  pop → 3 ✅  (don't touch inbox yet)

dequeue():
  outbox empty → pour inbox into outbox
  inbox:  []
  outbox: [4]
  pop → 4 ✅
```

---

## The Amortized O(1) Argument

Each element is:
- Pushed to inbox once
- Poured to outbox once
- Popped from outbox once

Total: 3 operations per element regardless of n. That's O(1) amortized — not every operation is O(1), but averaged across all operations the cost is O(1) per element.

---

## Now Write It

```java
class MyQueue {
    // fields here

    public MyQueue() { }

    public void enqueue(int val) { }

    public int dequeue() { }

    public int peek() { }

    public boolean isEmpty() { }
}
```

**Things to think through:**
- When do you pour from inbox to outbox?
- What if you enqueue while outbox still has elements?
- What is the isEmpty condition?
- What do dequeue and peek do differently?

Post your solution when ready.

# Code Review — Queue Using Two Stacks

## Verdict: Right Idea, One Bug ⚠️

Structure is correct. The pour logic has a critical mistake.

---

## The Bug — Modifying Collection While Iterating

```java
for (Integer val : inbox) {
    inbox.pop();      // ❌ modifying inbox while iterating over it
    outbox.push(val);
}
```

This throws `ConcurrentModificationException` at runtime. You cannot modify a collection while a for-each loop is iterating over it.

The fix is simple — iterate based on size, not the collection itself:

```java
while (!inbox.isEmpty()) {
    outbox.push(inbox.pop());
}
```

`while` loop checks `inbox.isEmpty()` fresh each iteration. No concurrent modification.

---

## Second Issue — Pour Logic Duplicated

```java
// Same 3 lines in both dequeue() and peek()
if (outbox.isEmpty()) {
    while (!inbox.isEmpty()) {
        outbox.push(inbox.pop());
    }
}
```

Extract into a private helper — DRY principle:

```java
private void pourIfNeeded() {
    if (outbox.isEmpty()) {
        while (!inbox.isEmpty()) {
            outbox.push(inbox.pop());
        }
    }
}
```

Then both methods just call `pourIfNeeded()` — one line each.

---

## Third Issue — Missing isEmpty() and empty guards

```java
public int dequeue() {
    pourIfNeeded();
    if (outbox.isEmpty()) throw new RuntimeException("Queue is empty");
    return outbox.pop();
}
```

What if someone calls `dequeue()` on an empty queue? Both inbox and outbox are empty — `outbox.pop()` throws `NoSuchElementException`. Better to throw a meaningful exception yourself.

---

## Corrected Solution

```java
public class MyQueue {

    private Deque<Integer> inbox = new ArrayDeque<>();
    private Deque<Integer> outbox = new ArrayDeque<>();

    public void enqueue(int val) {
        inbox.push(val);
    }

    public int dequeue() {
        pourIfNeeded();
        if (outbox.isEmpty()) throw new RuntimeException("Queue is empty");
        return outbox.pop();
    }

    public int peek() {
        pourIfNeeded();
        if (outbox.isEmpty()) throw new RuntimeException("Queue is empty");
        return outbox.peek();
    }

    public boolean isEmpty() {
        return inbox.isEmpty() && outbox.isEmpty();
    }

    private void pourIfNeeded() {
        if (outbox.isEmpty()) {
            while (!inbox.isEmpty()) {
                outbox.push(inbox.pop());
            }
        }
    }
}
```

---

## Trace — Corrected

```
enqueue(1): inbox=[1], outbox=[]
enqueue(2): inbox=[2,1], outbox=[]
enqueue(3): inbox=[3,2,1], outbox=[]

dequeue():
  outbox empty → pour:
  inbox=[], outbox=[1,2,3]  top=1
  pop outbox → 1 ✅

dequeue():
  outbox not empty → skip pour
  pop outbox → 2 ✅

dequeue():
  outbox not empty → skip pour
  pop outbox → 3 ✅

enqueue(4):
  inbox=[4], outbox=[]

dequeue():
  outbox empty → pour:
  inbox=[], outbox=[4]
  pop outbox → 4 ✅
```

---

## The Amortized Argument — Say This Out Loud

> "Each element touches inbox once and outbox once. The pour only happens when outbox is empty — never repeatedly for the same elements. So averaged across all operations, each element costs O(1) total — O(1) amortized even though a single dequeue might trigger an O(n) pour."

---

## Score Card

| Dimension | Your Solution | Senior Bar |
|---|---|---|
| Two stack concept | ✅ Correct | ✅ |
| Pour condition | ✅ Correct | ✅ |
| ConcurrentModification bug | ❌ for-each + modify | ✅ while loop |
| Duplicated pour logic | ⚠️ repeated code | ✅ extract helper |
| isEmpty() method | ❌ Missing | ✅ |
| Empty queue guard | ❌ Missing | ✅ |

---

## Stacks & Queues Progress

| Question | Status |
|---|---|
| Valid parentheses | ✅ Done |
| Min Stack | ✅ Done |
| Queue using two stacks | ✅ Done |
| Monotonic stack | ⏸ Next |
| BFS using queue | ⏸ After monotonic |

---

Ready for Q4 — **Monotonic Stack**? This is the trickiest stack pattern and comes up frequently at Goldman.

# Monotonic Stack — Deep Dive

## What Is a Monotonic Stack?

A regular stack stores whatever you push. A monotonic stack maintains a **specific order** — either always increasing or always decreasing from bottom to top.

```
Monotonic Increasing Stack:   Monotonic Decreasing Stack:
bottom → top                  bottom → top
[1, 3, 5, 7]                 [9, 6, 4, 2]
each element > previous       each element < previous
```

---

## Why Is This Useful?

It efficiently answers the question: **"For each element, what is the next greater/smaller element?"**

Brute force — for each element scan right until you find a greater one:

```
arr = [2, 1, 5, 3, 6]

For 2: scan right → 5 is next greater   O(n) per element
For 1: scan right → 5 is next greater   O(n) per element
...
Total: O(n²)
```

Monotonic stack does this in O(n) total.

---

## The Core Mechanism

**Next Greater Element — using Monotonic Decreasing Stack:**

```
Rule: maintain stack in decreasing order
When new element > stack top:
  → stack top has found its next greater element (the new element)
  → pop stack top, record answer
  → repeat until stack top >= new element or stack empty
  → push new element
```

Trace — `arr = [2, 1, 5, 3, 6]`:

```
result = [0, 0, 0, 0, 0]  (0 means no answer yet)
stack = []  (stores indices)

i=0, val=2: stack empty → push 0 → stack=[0]
i=1, val=1: 1 < arr[0]=2 → push 1 → stack=[0,1]
i=2, val=5:
  5 > arr[1]=1 → pop 1, result[1]=5 → stack=[0]
  5 > arr[0]=2 → pop 0, result[0]=5 → stack=[]
  push 2 → stack=[2]
i=3, val=3: 3 < arr[2]=5 → push 3 → stack=[2,3]
i=4, val=6:
  6 > arr[3]=3 → pop 3, result[3]=6 → stack=[2]
  6 > arr[2]=5 → pop 2, result[2]=6 → stack=[]
  push 4 → stack=[4]

End: stack=[4] → index 4 has no next greater → result[4]=-1

result = [5, 5, 6, 6, -1] ✅
```

**Why store indices instead of values?**
You need to know which position in `result` to update. Value alone doesn't tell you that.

---

## The Template

```java
Deque<Integer> stack = new ArrayDeque<>();  // stores indices
int[] result = new int[n];
Arrays.fill(result, -1);  // default: no next greater element

for (int i = 0; i < n; i++) {
    // while stack not empty AND current breaks the monotonic order
    while (!stack.isEmpty() && arr[i] > arr[stack.peek()]) {
        int idx = stack.pop();
        result[idx] = arr[i];  // current element is next greater for idx
    }
    stack.push(i);
}
```

---

## Increasing vs Decreasing — When to Use Which

```
Next GREATER element → Decreasing stack
  maintain decreasing order
  new element breaks order when it's GREATER than top
  → pop, record as next greater

Next SMALLER element → Increasing stack
  maintain increasing order
  new element breaks order when it's SMALLER than top
  → pop, record as next smaller
```

Memory trick:
```
Looking for GREATER → stack is DECREASING (gets broken by greater elements)
Looking for SMALLER → stack is INCREASING (gets broken by smaller elements)
```

---

## Question 1 — Next Greater Element

**"Given an array, for each element find the next greater element to its right. If none exists return -1."**

```
Input:  [2, 1, 5, 3, 6, 4]
Output: [5, 5, 6, 6, -1, -1]

Input:  [5, 4, 3, 2, 1]
Output: [-1, -1, -1, -1, -1]  (strictly decreasing, no next greater)

Input:  [1, 2, 3, 4, 5]
Output: [2, 3, 4, 5, -1]  (strictly increasing)
```

**Constraints:** O(n) time, O(n) space.

Post your solution using the template above. Focus on:
- Stack stores indices not values
- Default result value is -1
- What condition triggers a pop

Fair. Let me ditch the abstract explanation and build it from a real problem.

---

## Forget "Monotonic Stack" — Just Think About This Problem

You have daily stock prices. For each day, you want to know: **"When is the next day the price will be higher than today?"**

```
prices = [2, 1, 5, 3, 6]
days   =  0  1  2  3  4

Day 0, price=2: next higher price is 5 on day 2
Day 1, price=1: next higher price is 5 on day 2
Day 2, price=5: next higher price is 6 on day 4
Day 3, price=3: next higher price is 6 on day 4
Day 4, price=6: no higher price after → -1
```

This is exactly "next greater element". Real use case — trading systems, Goldman literally has this in production.

---

## Why Brute Force Fails

```java
for (int i = 0; i < n; i++) {
    for (int j = i+1; j < n; j++) {
        if (prices[j] > prices[i]) {
            result[i] = prices[j];
            break;
        }
    }
}
```

O(n²). For 1 million prices that's 1 trillion operations. Too slow.

---

## The Human Intuition

Imagine you're standing in a line of people of different heights. You want to know — **for each person, who is the first taller person standing behind them?**

```
heights = [2, 1, 5, 3, 6]

Person at pos 0, height 2:
  looks right → pos 1 height 1, shorter, keep looking
  looks right → pos 2 height 5, taller → answer is 5

Person at pos 1, height 1:
  looks right → pos 2 height 5, taller → answer is 5
```

Now here's the key observation:

**When person at pos 2 (height 5) arrives, they are simultaneously the answer for BOTH pos 0 AND pos 1.**

Instead of each person scanning right independently, what if you kept a list of "people still waiting for their answer" and resolved them all at once when a taller person arrives?

That list of waiting people — **that's the stack.**

---

## The Stack As a "Waiting List"

```
prices = [2, 1, 5, 3, 6]

Process price=2: nobody waiting yet → add 2 to waiting list
waiting: [2]

Process price=1: 1 < 2, can't resolve anyone → add 1 to waiting list
waiting: [2, 1]

Process price=5: 5 > 1 → resolves day 1 → answer[1] = 5
                 5 > 2 → resolves day 0 → answer[0] = 5
                 waiting list empty → add 5
waiting: [5]

Process price=3: 3 < 5, can't resolve anyone → add 3
waiting: [5, 3]

Process price=6: 6 > 3 → resolves day 3 → answer[3] = 6
                 6 > 5 → resolves day 2 → answer[2] = 6
                 waiting list empty → add 6
waiting: [6]

End: day 4 (price=6) still waiting → answer[4] = -1
```

---

## Why The Waiting List Is Always Decreasing

Look at the waiting list at any point:

```
After processing 2,1:   waiting = [2, 1]   decreasing ✓
After processing 5:     waiting = [5]       decreasing ✓
After processing 5,3:   waiting = [5, 3]   decreasing ✓
```

**It's always decreasing because:** if you're waiting and a smaller price comes after you, that smaller price also needs to wait — and it gets added behind you. A smaller price behind a larger price never resolves the larger price. So the list naturally stays decreasing.

That's why it's called a **monotonic decreasing stack** — the property emerges naturally from the algorithm, you don't force it.

---

## Now The Code Makes Sense

```java
Deque<Integer> waiting = new ArrayDeque<>();  // stores indices of unresolved days
int[] answer = new int[n];
Arrays.fill(answer, -1);  // default: no higher price found

for (int i = 0; i < n; i++) {
    // current price resolves anyone in waiting list with lower price
    while (!waiting.isEmpty() && prices[i] > prices[waiting.peek()]) {
        int waitingIdx = waiting.pop();
        answer[waitingIdx] = prices[i];  // today's price is their answer
    }
    // today goes into waiting list — not yet resolved
    waiting.push(i);
}
```

Three lines inside the loop:
1. While current price is higher than whoever's waiting at the top — resolve them
2. Record current price as their answer
3. Add today to the waiting list

---

## Full Trace With Code

```
prices = [2, 1, 5, 3, 6]
answer = [-1,-1,-1,-1,-1]
waiting = []

i=0, price=2:
  waiting empty → skip while
  push 0 → waiting=[0]

i=1, price=1:
  prices[1]=1 > prices[waiting.peek()=0]=2? NO → skip while
  push 1 → waiting=[0,1]

i=2, price=5:
  prices[2]=5 > prices[waiting.peek()=1]=1? YES
    pop 1, answer[1]=5 → answer=[-1,5,-1,-1,-1]
  prices[2]=5 > prices[waiting.peek()=0]=2? YES
    pop 0, answer[0]=5 → answer=[5,5,-1,-1,-1]
  waiting empty → stop while
  push 2 → waiting=[2]

i=3, price=3:
  prices[3]=3 > prices[waiting.peek()=2]=5? NO → skip while
  push 3 → waiting=[2,3]

i=4, price=6:
  prices[4]=6 > prices[waiting.peek()=3]=3? YES
    pop 3, answer[3]=6 → answer=[5,5,-1,6,-1]
  prices[4]=6 > prices[waiting.peek()=2]=5? YES
    pop 2, answer[2]=6 → answer=[5,5,6,6,-1]
  waiting empty → stop while
  push 4 → waiting=[4]

End: waiting=[4] → answer[4] stays -1

Final answer = [5, 5, 6, 6, -1] ✅
```

---

## Is This Clear Now?

The one sentence summary:

> **Keep a waiting list of unresolved elements. When a new element arrives, it resolves everyone in the waiting list that it's greater than. Add itself to the waiting list and wait for its own resolver.**

Does the stock price analogy make it click? Once you say yes, write the solution — it's basically the code I showed above, just write it yourself from memory.

# BFS Using Queue — Deep Dive

## What Is BFS?

BFS = Breadth First Search. It explores a graph or tree **level by level** — all nodes at distance 1 first, then distance 2, then distance 3, and so on.

```
Tree:
        1
       / \
      2   3
     / \   \
    4   5   6

BFS order: 1 → 2 → 3 → 4 → 5 → 6

Level 0: [1]
Level 1: [2, 3]
Level 2: [4, 5, 6]
```

DFS goes deep first — down one branch to the end before backtracking. BFS goes wide first — all neighbours before going deeper.

---

## Why a Queue?

BFS processes nodes in the order they're discovered. First discovered = first processed. That's exactly FIFO — a queue.

```
Start: queue=[1]

Process 1: add its children 2,3 → queue=[2,3]
Process 2: add its children 4,5 → queue=[3,4,5]
Process 3: add its children 6   → queue=[4,5,6]
Process 4: no children          → queue=[5,6]
Process 5: no children          → queue=[6]
Process 6: no children          → queue=[]
Done.
```

The queue naturally gives you level-by-level processing.

---

## The BFS Template

```java
Queue<TreeNode> queue = new LinkedList<>();
queue.offer(root);

while (!queue.isEmpty()) {
    TreeNode node = queue.poll();
    
    // process node
    
    if (node.left != null)  queue.offer(node.left);
    if (node.right != null) queue.offer(node.right);
}
```

---

## Level-by-Level Variant

Most interview problems need you to process **one level at a time**. The trick — snapshot the queue size at the start of each level:

```java
while (!queue.isEmpty()) {
    int levelSize = queue.size();  // how many nodes at this level
    
    for (int i = 0; i < levelSize; i++) {
        TreeNode node = queue.poll();
        // process node — it belongs to current level
        if (node.left != null)  queue.offer(node.left);
        if (node.right != null) queue.offer(node.right);
    }
    // one full level processed here
}
```

**Why `levelSize` works:** When you start processing a level, the queue contains exactly that level's nodes. You snapshot the count, process exactly that many, and by the time you finish, the queue contains exactly the next level's nodes.

---

## Question — Level Order Traversal

**"Given a binary tree, return its level order traversal as a list of lists — each inner list contains the values at that level."**

```
Input:
        3
       / \
      9  20
        /  \
       15   7

Output: [[3], [9,20], [15,7]]

Input:  [1]
Output: [[1]]

Input:  null
Output: []
```

**Constraints:** O(n) time, O(n) space.

**Things to think through:**
- What goes into the queue initially?
- How do you know when one level ends and the next begins?
- What data structure holds the final result?

Post your solution when ready.


# Stacks & Queues — Complete Summary

## Patterns & Problem Types

| Pattern | Problems It Solves | Key Signal Words | Data Structure |
|---|---|---|---|
| Matching / Validation | Valid parentheses, HTML tag matching, expression balancing | "valid", "balanced", "matching", "open/close" | Stack |
| Monotonic Stack | Next greater/smaller element, stock prices, histogram | "next greater", "next smaller", "previous larger", "temperatures" | Stack (stores indices) |
| Stack simulating Queue | Design problems, stream processing | "implement queue using stacks", "FIFO from LIFO" | Two Stacks |
| Min/Max Stack | Running minimum, running maximum with O(1) access | "O(1) getMin", "minimum at any point" | Two Stacks |
| BFS using Queue | Level order traversal, shortest path, word ladder | "level by level", "minimum steps", "shortest path" | Queue |

---

## Pattern Detail — Implementation Revision

### 1. Matching / Validation
```java
Deque<Character> stack = new ArrayDeque<>();
for (char c : s.toCharArray()) {
    if (isOpening(c)) {
        stack.push(c);
    } else {
        if (stack.isEmpty()) return false;      // nothing to match
        if (!matches(stack.peek(), c)) return false; // wrong match
        stack.pop();
    }
}
return stack.isEmpty();   // unclosed brackets fail here
```
**Key mistake to avoid:** `return true` at end instead of `return stack.isEmpty()`

---

### 2. Monotonic Stack — Next Greater Element
```java
Deque<Integer> stack = new ArrayDeque<>();  // stores INDICES not values
int[] result = new int[n];
Arrays.fill(result, -1);                    // default no answer

for (int i = 0; i < n; i++) {
    while (!stack.isEmpty() && arr[i] > arr[stack.peek()]) {
        result[stack.pop()] = arr[i];       // current resolves waiting element
    }
    stack.push(i);                          // push index, wait for resolver
}
```
**Key insight:** Stack is a "waiting list" of unresolved elements. New element resolves everyone smaller than it.

| Looking for | Stack order | Condition to pop |
|---|---|---|
| Next GREATER | Decreasing | `arr[i] > arr[stack.peek()]` |
| Next SMALLER | Increasing | `arr[i] < arr[stack.peek()]` |

---

### 3. Min Stack — Two Stacks in Sync
```java
private int[] valueStack;
private int[] minStack;
private int top = -1;

void push(int val) {
    top++;
    valueStack[top] = val;
    minStack[top] = (top == 0) ? val : Math.min(val, minStack[top-1]);
}
void pop()    { top--; }
int peek()    { return valueStack[top]; }
int getMin()  { return minStack[top]; }   // O(1) always
```
**Key insight:** minStack[i] stores the minimum of everything pushed up to and including position i. Both stacks always same size — single index.

---

### 4. Queue Using Two Stacks
```java
Deque<Integer> inbox  = new ArrayDeque<>();  // receives all pushes
Deque<Integer> outbox = new ArrayDeque<>();  // serves all pops

void enqueue(int val) { inbox.push(val); }

int dequeue() {
    pourIfNeeded();
    if (outbox.isEmpty()) throw new RuntimeException("Queue is empty");
    return outbox.pop();
}

private void pourIfNeeded() {
    if (outbox.isEmpty()) {
        while (!inbox.isEmpty()) {
            outbox.push(inbox.pop());   // reverses order → FIFO
        }
    }
}

boolean isEmpty() { return inbox.isEmpty() && outbox.isEmpty(); }
```
**Key insight:** Pouring inbox into outbox reverses order — LIFO becomes FIFO. Pour only when outbox is empty — never mid-outbox. Each element moves exactly once → O(1) amortized.

---

### 5. BFS Using Queue — Level Order
```java
if (root == null) return result;

Queue<TreeNode> queue = new LinkedList<>();
queue.offer(root);

while (!queue.isEmpty()) {
    int levelSize = queue.size();           // snapshot — critical
    List<Integer> currentLevel = new ArrayList<>();

    for (int i = 0; i < levelSize; i++) {
        TreeNode node = queue.poll();
        currentLevel.add(node.val);

        if (node.left  != null) queue.offer(node.left);
        if (node.right != null) queue.offer(node.right);
    }
    result.add(currentLevel);
}
```
**Key insight:** `levelSize = queue.size()` snapshots exactly how many nodes are at the current level. Inner for loop processes exactly that many — queue naturally fills with next level during this time.

---

## Common Mistakes Across All Patterns

| Mistake | Correct Habit |
|---|---|
| `return true` at end of bracket validation | `return stack.isEmpty()` |
| Pushing values into monotonic stack | Always push **indices** |
| Iterating collection while modifying it | Use `while (!stack.isEmpty())` not for-each |
| Two indices for min stack | Single index — both stacks always same size |
| Null root in BFS | Guard at top — `if (root == null) return result` |
| Missing null guard on every method | First line always checks null/empty |

---

## Complexity Reference

| Pattern | Time | Space |
|---|---|---|
| Matching/Validation | O(n) | O(n) |
| Monotonic Stack | O(n) | O(n) |
| Min Stack — all ops | O(1) | O(n) |
| Queue using two stacks | O(1) amortized | O(n) |
| BFS Level Order | O(n) | O(n) |

---

Ready for **Trees & BSTs**?

# Priority Queue & Heap — Build From Scratch

## What We're Building

```
MinHeap  — poll() always gives smallest element
MaxHeap  — poll() always gives largest element
PriorityQueue — MinHeap with custom comparator support
```

---

## The Core Operations To Implement

```
add(val)     — insert element
poll()       — remove and return min/max
peek()       — see min/max without removing
size()       — count of elements
isEmpty()    — check empty
```

---

## The Two Internal Operations Everything Depends On

```
siftUp   — after add(), bubble new element UP until heap rule satisfied
siftDown — after poll(), bubble root DOWN until heap rule satisfied
```

---

## The Heap Array Math

```
For any index i:
  parent     = (i - 1) / 2
  leftChild  = 2 * i + 1
  rightChild = 2 * i + 2

Example — array [1, 3, 2, 7, 4, 5, 6]:
          1          index 0
        /   \
       3     2       index 1, 2
      / \   / \
     7   4 5   6     index 3, 4, 5, 6

parent(3)     = (3-1)/2 = 1  → arr[1]=3  ✅
leftChild(1)  = 2*1+1   = 3  → arr[3]=7  ✅
rightChild(1) = 2*1+2   = 4  → arr[4]=4  ✅
```

---

## Now Build It — MinHeap First

```java
public class MinHeap {

    private int[] data;
    private int size;

    public MinHeap(int capacity) {
        data = new int[capacity];
        size = 0;
    }

    public void add(int val) {
        // 1. add at end
        // 2. siftUp
    }

    public int poll() {
        // 1. save root (minimum)
        // 2. move last element to root
        // 3. reduce size
        // 4. siftDown
        // 5. return saved minimum
    }

    public int peek() {
        // return root
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    private void siftUp(int index) {
        // while index > 0 AND data[index] < data[parent(index)]:
        //   swap with parent
        //   move index to parent
    }

    private void siftDown(int index) {
        // while leftChild(index) exists:
        //   find smaller child
        //   if data[index] <= smaller child → stop
        //   swap with smaller child
        //   move index to smaller child
    }

    private void swap(int i, int j) {
        int temp = data[i];
        data[i] = data[j];
        data[j] = temp;
    }

    private int parent(int i)      { return (i - 1) / 2; }
    private int leftChild(int i)   { return 2 * i + 1; }
    private int rightChild(int i)  { return 2 * i + 2; }
}
```

**Fill in `add()`, `poll()`, `siftUp()`, `siftDown()`.**

Four methods. Think through each one:

**siftUp:** You just added at the end. The heap rule might be violated between the new element and its parent. Keep swapping upward until parent is smaller or you reach root.

**siftDown:** You just put the last element at the root. It might be larger than its children. Find the smaller child, swap if needed, repeat downward.

**poll():** The tricky part — you can't just remove the root and leave a gap. Move the last element to root position first, then siftDown.

Post your implementation when ready.

# Code Review — Heap From Scratch

## Verdict: Strong Attempt — Two Bugs ⚠️

`add()`, `peek()`, `poll()`, `heapifyUp()` are all correct in concept. `heapifyDown()` has two bugs that cause crashes and wrong answers.

---

## Bug 1 — heapifyDown Accesses Out of Bounds Indices

```java
private void heapifyDown(int parent) {
    int left = leftChild(parent);
    int right = rightChild(parent);

    if (items[parent] > items[left] || items[parent] > items[right]) {
```

What if `left` or `right` don't exist? If parent is a leaf node:

```
left  = 2*parent+1 → might be >= size → index out of bounds ❌
right = 2*parent+2 → might be >= size → index out of bounds ❌
```

You must check indices exist before accessing `items[left]` or `items[right]`.

---

## Bug 2 — heapifyDown Doesn't Handle Missing Right Child

Even if left child exists, right child might not. A heap is a complete binary tree — the last level fills left to right. So right child can be absent even when left exists:

```
heap = [1, 3, 2, 7, 4]
                        left=9, right=10 → don't exist
        [1]
       /   \
     [3]   [2]
     / \
   [7] [4]   ← node at index 1 has both children
              node at index 2 has NO children
```

---

## Bug 3 — heapifyUp Parent Calculation for Index 0

```java
private void heapifyUp(int index) {
    int parent = parent(index);
    if (parent < 0) return;   // ⚠️ parent(0) = (0-1)/2 = 0 in Java integer division
```

`parent(0)` returns `(-1)/2 = 0` in Java — not -1. So your guard `parent < 0` never triggers for index 0. It accidentally works because `items[0] > items[0]` is false so no swap happens — but the logic is fragile. Better guard:

```java
if (index == 0) return;   // explicit, clear
```

---

## Corrected heapifyDown

```java
private void heapifyDown(int index) {
    int left = leftChild(index);
    int right = rightChild(index);
    int smallest = index;         // assume parent is smallest

    // check left child exists and is smaller
    if (left < size && items[left] < items[smallest]) {
        smallest = left;
    }

    // check right child exists and is smaller
    if (right < size && items[right] < items[smallest]) {
        smallest = right;
    }

    // if parent is not smallest — swap and continue down
    if (smallest != index) {
        swap(index, smallest);
        heapifyDown(smallest);
    }
}
```

**The `smallest` variable pattern** — find the smallest among parent, left, right. If parent isn't smallest, swap down. This handles all cases:
- No children → smallest stays index → no swap → stops
- Only left child → right check skipped by `right < size`
- Both children → picks smaller one

---

## Corrected heapifyUp

```java
private void heapifyUp(int index) {
    if (index == 0) return;           // ✅ explicit root check

    int parent = parent(index);
    if (items[parent] > items[index]) {
        swap(index, parent);
        heapifyUp(parent);
    }
}
```

---

## Full Corrected Solution

```java
public class MinHeap {

    private int capacity;
    private int[] items;
    private int size;

    public MinHeap(int capacity) {
        this.capacity = capacity;
        this.items = new int[capacity];
        this.size = 0;
    }

    public void add(int item) {
        if (size == capacity) throw new IllegalStateException("Heap is full");
        items[size] = item;
        heapifyUp(size);
        size++;
    }

    public int poll() {
        if (size == 0) throw new IllegalStateException("Heap is empty");
        int min = items[0];
        items[0] = items[size - 1];
        size--;
        heapifyDown(0);
        return min;
    }

    public int peek() {
        if (size == 0) throw new IllegalStateException("Heap is empty");
        return items[0];
    }

    public boolean isEmpty() { return size == 0; }
    public int size() { return size; }

    private void heapifyUp(int index) {
        if (index == 0) return;
        int parent = parent(index);
        if (items[parent] > items[index]) {
            swap(index, parent);
            heapifyUp(parent);
        }
    }

    private void heapifyDown(int index) {
        int left = leftChild(index);
        int right = rightChild(index);
        int smallest = index;

        if (left < size && items[left] < items[smallest])
            smallest = left;
        if (right < size && items[right] < items[smallest])
            smallest = right;

        if (smallest != index) {
            swap(index, smallest);
            heapifyDown(smallest);
        }
    }

    private void swap(int i, int j) {
        int temp = items[i];
        items[i] = items[j];
        items[j] = temp;
    }

    private int parent(int i)     { return (i - 1) / 2; }
    private int leftChild(int i)  { return 2 * i + 1; }
    private int rightChild(int i) { return 2 * i + 2; }
}
```

---

## Trace — add(5,3,2,4,1)

```
add(5): items=[5], size=1
  heapifyUp(0): index==0 → return

add(3): items=[5,3], size=2
  heapifyUp(1): parent=0, items[0]=5 > items[1]=3 → swap
  items=[3,5], heapifyUp(0) → return

add(2): items=[3,5,2], size=3
  heapifyUp(2): parent=0, items[0]=3 > items[2]=2 → swap
  items=[2,5,3], heapifyUp(0) → return

add(4): items=[2,5,3,4], size=4
  heapifyUp(3): parent=1, items[1]=5 > items[3]=4 → swap
  items=[2,4,3,5], heapifyUp(1)
  heapifyUp(1): parent=0, items[0]=2 > items[1]=4? NO → stop

add(1): items=[2,4,3,5,1], size=5
  heapifyUp(4): parent=1, items[1]=4 > items[4]=1 → swap
  items=[2,1,3,5,4], heapifyUp(1)
  heapifyUp(1): parent=0, items[0]=2 > items[1]=1 → swap
  items=[1,2,3,5,4], heapifyUp(0) → return

Final: [1,2,3,5,4] ✅ valid min heap
```

---

## Now — MaxHeap

MaxHeap is identical except one character changes in both heapifyUp and heapifyDown:

```java
// MinHeap:
if (items[parent] > items[index])   // siftUp when parent GREATER
if (items[left] < items[smallest])  // pick SMALLER child

// MaxHeap:
if (items[parent] < items[index])   // siftUp when parent SMALLER
if (items[left] > items[largest])   // pick LARGER child
```

Write the MaxHeap version now — same structure, just flip the comparisons. Post when ready.


Great question. Two approaches — I'll show both, you pick which to implement.

---

## Approach 1 — Boolean Flag (Simple)

```java
public class PQHeap {
    private int[] items;
    private int size;
    private boolean isMinHeap;   // true = min, false = max

    public PQHeap(int capacity, boolean isMinHeap) {
        this.items = new int[capacity];
        this.size = 0;
        this.isMinHeap = isMinHeap;
    }

    private boolean shouldSwapUp(int child, int parent) {
        if (isMinHeap) {
            return items[parent] > items[child];  // min: parent should be smaller
        } else {
            return items[parent] < items[child];  // max: parent should be larger
        }
    }

    private void heapifyUp(int index) {
        if (index == 0) return;
        int parent = parent(index);
        if (shouldSwapUp(index, parent)) {
            swap(index, parent);
            heapifyUp(parent);
        }
    }

    private void heapifyDown(int index) {
        int left = leftChild(index);
        int right = rightChild(index);
        int target = index;   // target = smallest (min) or largest (max)

        if (isMinHeap) {
            if (left  < size && items[left]  < items[target]) target = left;
            if (right < size && items[right] < items[target]) target = right;
        } else {
            if (left  < size && items[left]  > items[target]) target = left;
            if (right < size && items[right] > items[target]) target = right;
        }

        if (target != index) {
            swap(index, target);
            heapifyDown(target);
        }
    }
}

// Usage:
PQHeap minHeap = new PQHeap(10, true);   // min heap
PQHeap maxHeap = new PQHeap(10, false);  // max heap
```

Simple but only works for `int`. Not reusable for custom objects.

---

## Approach 2 — Comparator (Proper Java Way)

This is how Java's `PriorityQueue` works internally. Generic type `T`, comparator injected at construction.

```java
public class PQHeap<T> {
    private Object[] items;
    private int size;
    private Comparator<T> comparator;

    public PQHeap(int capacity, Comparator<T> comparator) {
        this.items = new Object[capacity];
        this.size = 0;
        this.comparator = comparator;
    }

    @SuppressWarnings("unchecked")
    private T get(int index) {
        return (T) items[index];
    }

    private boolean shouldSwapUp(int child, int parent) {
        // comparator.compare(a, b) < 0 means a should come before b
        return comparator.compare(get(child), get(parent)) < 0;
    }

    private void heapifyUp(int index) {
        if (index == 0) return;
        int parent = parent(index);
        if (shouldSwapUp(index, parent)) {
            swap(index, parent);
            heapifyUp(parent);
        }
    }

    private void heapifyDown(int index) {
        int left = leftChild(index);
        int right = rightChild(index);
        int target = index;

        if (left < size && comparator.compare(get(left), get(target)) < 0)
            target = left;
        if (right < size && comparator.compare(get(right), get(target)) < 0)
            target = right;

        if (target != index) {
            swap(index, target);
            heapifyDown(target);
        }
    }

    @SuppressWarnings("unchecked")
    public void add(T item) {
        if (size == items.length) throw new IllegalStateException("Heap full");
        items[size] = item;
        heapifyUp(size);
        size++;
    }

    public T poll() {
        if (size == 0) throw new IllegalStateException("Heap empty");
        T min = get(0);
        items[0] = items[size - 1];
        size--;
        heapifyDown(0);
        return min;
    }

    public T peek() {
        if (size == 0) throw new IllegalStateException("Heap empty");
        return get(0);
    }

    public boolean isEmpty() { return size == 0; }
    public int size()        { return size; }

    private void swap(int i, int j) {
        Object temp = items[i];
        items[i] = items[j];
        items[j] = temp;
    }

    private int parent(int i)    { return (i - 1) / 2; }
    private int leftChild(int i) { return 2 * i + 1; }
    private int rightChild(int i){ return 2 * i + 2; }
}
```

---

## Usage — All Four Cases

```java
// Min heap of integers
PQHeap<Integer> minHeap = new PQHeap<>(10, (a, b) -> a - b);

// Max heap of integers
PQHeap<Integer> maxHeap = new PQHeap<>(10, (a, b) -> b - a);

// Min heap of strings by length
PQHeap<String> byLength = new PQHeap<>(10, (a, b) -> a.length() - b.length());

// Min heap of ListNode by val — exactly what merge k lists needs
PQHeap<ListNode> nodeHeap = new PQHeap<>(10, (a, b) -> a.val - b.val);
```

---

## How The Comparator Drives Everything

The entire heap logic reduces to one question:

```
"Should child come before parent?"
comparator.compare(child, parent) < 0  → yes → swap up

"Which child should come first?"
comparator.compare(left, target) < 0   → left is better → target = left
comparator.compare(right, target) < 0  → right is better → target = right
```

`compare(a, b) < 0` means **a should come before b** — that's the only rule. Min heap, max heap, custom object — all handled by changing the comparator. The heap structure itself never changes.

---

## The Key Insight

```
Comparator returns negative → first argument wins → comes to top
                                                      
(a, b) -> a - b   → smaller a wins  → min heap
(a, b) -> b - a   → smaller b wins  → max heap
```

---

Which approach do you want to implement — flag-based (simpler) or comparator-based (proper)?

# Full PriorityQueue Implementation

## What's Missing From Your Current Implementation

You have the core heap. A full PriorityQueue adds:

```
1. Dynamic resizing     — no fixed capacity, grows automatically
2. contains(item)       — check if item exists
3. remove(item)         — remove specific item (not just top)
4. toArray()            — dump contents
5. Iterator            — for-each support
```

---

## Dynamic Resizing — Most Important Addition

Instead of throwing when full, double the array:

```java
private void resize() {
    capacity = capacity * 2;
    Object[] newItems = new Object[capacity];
    System.arraycopy(items, 0, newItems, 0, size);
    items = newItems;
}

public void add(T item) {
    if (size == capacity) resize();    // ✅ grow instead of throw
    items[size] = item;
    heapifyUp(size);
    size++;
}
```

**Why double?** Amortized O(1) add. If you grow by 1 each time, every add triggers a copy — O(n) per add. Doubling means copies happen at sizes 1,2,4,8,16... Each element copied at most twice total → O(1) amortized.

---

## contains() and remove(item)

```java
// contains — linear scan, O(n)
public boolean contains(T item) {
    for (int i = 0; i < size; i++) {
        if (comparator.compare(get(i), item) == 0) return true;
    }
    return false;
}

// remove specific item — find it, remove it, reheapify
public boolean remove(T item) {
    // 1. find the item
    int index = -1;
    for (int i = 0; i < size; i++) {
        if (comparator.compare(get(i), item) == 0) {
            index = i;
            break;
        }
    }
    if (index == -1) return false;   // not found

    // 2. replace with last element
    items[index] = items[size - 1];
    items[size - 1] = null;
    size--;

    // 3. reheapify both directions — don't know which way it needs to go
    if (index < size) {
        heapifyUp(index);
        heapifyDown(index);
    }

    return true;
}
```

**Why heapifyUp AND heapifyDown after remove?**
The replacement element (last element) could be either larger or smaller than the removed element's neighbours. You don't know which direction it needs to move. Run both — only one will actually do any work.

---

## Full Implementation

```java
public class PriorityQueue<T> implements Iterable<T> {

    private Object[] items;
    private int size;
    private int capacity;
    private Comparator<T> comparator;

    // default min heap for Comparable types
    @SuppressWarnings("unchecked")
    public PriorityQueue() {
        this(16, (a, b) -> ((Comparable<T>) a).compareTo(b));
    }

    public PriorityQueue(Comparator<T> comparator) {
        this(16, comparator);
    }

    public PriorityQueue(int initialCapacity, Comparator<T> comparator) {
        this.capacity = initialCapacity;
        this.comparator = comparator;
        this.items = new Object[capacity];
        this.size = 0;
    }

    // ─── Core Operations ────────────────────────────────────────

    public void add(T item) {
        if (item == null) throw new NullPointerException("null not allowed");
        if (size == capacity) resize();
        items[size] = item;
        heapifyUp(size);
        size++;
    }

    public T poll() {
        if (size == 0) throw new NoSuchElementException("Queue empty");
        T item = get(0);
        items[0] = items[size - 1];
        items[size - 1] = null;
        size--;
        if (size > 0) heapifyDown(0);
        return item;
    }

    public T peek() {
        if (size == 0) throw new NoSuchElementException("Queue empty");
        return get(0);
    }

    // ─── Search & Remove ────────────────────────────────────────

    public boolean contains(T item) {
        for (int i = 0; i < size; i++) {
            if (comparator.compare(get(i), item) == 0) return true;
        }
        return false;
    }

    public boolean remove(T item) {
        int index = indexOf(item);
        if (index == -1) return false;

        items[index] = items[size - 1];
        items[size - 1] = null;
        size--;

        if (index < size) {
            heapifyUp(index);     // only one will actually move
            heapifyDown(index);
        }
        return true;
    }

    private int indexOf(T item) {
        for (int i = 0; i < size; i++) {
            if (comparator.compare(get(i), item) == 0) return i;
        }
        return -1;
    }

    // ─── Utility ────────────────────────────────────────────────

    public int size()        { return size; }
    public boolean isEmpty() { return size == 0; }

    public Object[] toArray() {
        return Arrays.copyOf(items, size);
    }

    public void clear() {
        Arrays.fill(items, 0, size, null);
        size = 0;
    }

    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOf(items, size));
    }

    // ─── Iterator ───────────────────────────────────────────────

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            int cursor = 0;

            @Override
            public boolean hasNext() { return cursor < size; }

            @Override
            @SuppressWarnings("unchecked")
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return (T) items[cursor++];
            }
        };
    }

    // ─── Internal Heap Operations ────────────────────────────────

    private void heapifyUp(int index) {
        if (index == 0) return;
        int parent = parent(index);
        if (comparator.compare(get(parent), get(index)) > 0) {
            swap(index, parent);
            heapifyUp(parent);
        }
    }

    private void heapifyDown(int index) {
        int left  = leftChild(index);
        int right = rightChild(index);
        int target = index;

        if (left  < size && comparator.compare(get(target), get(left))  > 0) target = left;
        if (right < size && comparator.compare(get(target), get(right)) > 0) target = right;

        if (target != index) {
            swap(index, target);
            heapifyDown(target);
        }
    }

    private void resize() {
        capacity = capacity * 2;
        items = Arrays.copyOf(items, capacity);
    }

    private void swap(int i, int j) {
        Object temp = items[i];
        items[i] = items[j];
        items[j] = temp;
    }

    @SuppressWarnings("unchecked")
    private T get(int index) { return (T) items[index]; }

    private int parent(int i)     { return (i - 1) / 2; }
    private int leftChild(int i)  { return 2 * i + 1; }
    private int rightChild(int i) { return 2 * i + 2; }
}
```

---

## What Each Addition Costs

| Operation | Time | Why |
|---|---|---|
| add() | O(log n) amortized | resize is O(n) but rare |
| poll() | O(log n) | heapifyDown |
| peek() | O(1) | just read root |
| contains() | O(n) | linear scan — no shortcut |
| remove(item) | O(n) | find item + O(log n) reheapify |
| toArray() | O(n) | copy |
| clear() | O(n) | null fill |
| iterator.next() | O(1) | array index |

---

## What Java's Built-in PriorityQueue Adds On Top

```
1. Bulk construction from Collection  → heapify in O(n) not O(n log n)
2. spliterator for parallel streams
3. Serialization support
4. Thread safety (use PriorityBlockingQueue for that)
```

The bulk heapify from a collection is the one interesting thing — instead of adding n elements one by one (O(n log n)), you can build a valid heap from an existing array in O(n) by running heapifyDown from the last non-leaf node upward. That's how Java does it internally.

---

## Quick Test

```java
PriorityQueue<Integer> min = new PriorityQueue<>((a, b) -> a - b);
min.add(5); min.add(3); min.add(1); min.add(4); min.add(2);

System.out.println(min.poll());    // 1
System.out.println(min.poll());    // 2
System.out.println(min.peek());    // 3
System.out.println(min.contains(4)); // true
min.remove(4);
System.out.println(min);           // [3, 5]

PriorityQueue<Integer> max = new PriorityQueue<>((a, b) -> b - a);
max.add(5); max.add(3); max.add(1);
System.out.println(max.poll());    // 5
```

---

Ready for **Trees & BSTs** now?