# Trees & BSTs — Deep Dive

## The Core Idea

A tree is a hierarchical data structure. Unlike linked lists (linear) and arrays (indexed), trees branch. Every node can have multiple children.

```
        1          ← root (no parent)
       / \
      2   3        ← internal nodes
     / \   \
    4   5   6      ← leaf nodes (no children)
```

---

## Terminology — Burn These In

```
Root        — top node, no parent (node 1)
Leaf        — no children (nodes 4, 5, 6)
Height      — longest path from root to leaf (height=2 here)
Depth       — distance from root to a node (depth of 4 = 2)
Subtree     — any node and all its descendants
Parent      — direct node above
Children    — direct nodes below
Sibling     — nodes sharing same parent
```

---

## Binary Tree vs BST

**Binary Tree** — each node has at most 2 children. No ordering rule.

**Binary Search Tree (BST)** — binary tree with one extra rule:

```
For every node N:
  everything in LEFT subtree  < N.val
  everything in RIGHT subtree > N.val

        8
       / \
      3   10
     / \    \
    1   6    14
       / \   /
      4   7 13

Is this a BST? Check node 3:
  left=1 < 3 ✅
  right=6 > 3 ✅
Check node 10:
  right=14 > 10 ✅
  14's left=13 > 10 ✅ (all of right subtree must be > 10)
```

This ordering rule makes search O(log n) — at each node you eliminate half the tree.

---

## The Four Traversals — Everything Builds On These

```
        1
       / \
      2   3
     / \
    4   5
```

**Inorder (Left → Root → Right):** 4, 2, 5, 1, 3
For a BST, inorder always gives sorted ascending order. This is the most important property.

**Preorder (Root → Left → Right):** 1, 2, 4, 5, 3
Used for copying/serializing a tree.

**Postorder (Left → Right → Root):** 4, 5, 2, 3, 1
Used for deleting a tree, calculating directory sizes.

**Level Order (BFS):** 1, 2, 3, 4, 5
You already implemented this.

---

## Recursive Template — All Traversals

```java
// Inorder
void inorder(TreeNode root) {
    if (root == null) return;    // base case
    inorder(root.left);          // left
    process(root.val);           // root
    inorder(root.right);         // right
}

// Preorder
void preorder(TreeNode root) {
    if (root == null) return;
    process(root.val);           // root first
    preorder(root.left);
    preorder(root.right);
}

// Postorder
void postorder(TreeNode root) {
    if (root == null) return;
    postorder(root.left);
    postorder(root.right);
    process(root.val);           // root last
}
```

**The base case `if (root == null) return` is non-negotiable.** Every recursive tree function starts with this. Without it you get NullPointerException on leaf nodes' children.

---

## How Recursion Works on Trees

This is where most people get lost. Let me make it physical.

```
inorder(1):
  inorder(2):
    inorder(4):
      inorder(null) → return
      print 4
      inorder(null) → return
    print 2
    inorder(5):
      inorder(null) → return
      print 5
      inorder(null) → return
  print 1
  inorder(3):
    inorder(null) → return
    print 3
    inorder(null) → return

Output: 4 2 5 1 3 ✅
```

The call stack does the work. You don't manage it — you just define what to do at each node and trust the recursion.

---

## Question 1 — All Three Traversals

**"Implement inorder, preorder, and postorder traversal. Return results as a list."**

```
Input:
        1
       / \
      2   3
     / \
    4   5

Inorder:   [4, 2, 5, 1, 3]
Preorder:  [1, 2, 4, 5, 3]
Postorder: [4, 5, 2, 3, 1]
```

**Write all three — recursive versions first.**

```java
public static List<Integer> inorder(TreeNode root) { }
public static List<Integer> preorder(TreeNode root) { }
public static List<Integer> postorder(TreeNode root) { }
```

**Then for each one — the interviewer will ask:**
> "Can you do it iteratively without recursion?"

Think about the iterative approach too. Hint — you simulate the call stack manually using a `Deque`.

Post all three recursive versions first. We'll do iterative after.

Great question — this is the most important distinction in trees.

---

## Your Tree Is NOT a BST

You built this tree:

```
        1
       / \
      2   3
     / \
    4   5
```

This is just a **binary tree** — nodes placed wherever you want, no ordering rule. The values 1,2,3,4,5 are just labels you chose. They don't follow BST ordering.

Check — is it a BST?

```
Node 1: left subtree has 2,4,5 — all should be < 1. 
2 < 1? NO ❌ — not a BST
```

---

## Inorder Gives Sorted Output ONLY for BSTs

```
Regular binary tree inorder → visits nodes in a specific pattern
                            → output depends on structure, not values

BST inorder               → always sorted ascending
                            → because BST ordering guarantees it
```

Your tree gives `[4,2,5,1,3]` — correct inorder for that structure. Just not sorted because it's not a BST.

---

## Build a BST — Then Inorder Gives Sorted Output

```java
tree.root = new TreeNode(4);
tree.root.left = new TreeNode(2);
tree.root.right = new TreeNode(6);
tree.root.left.left = new TreeNode(1);
tree.root.left.right = new TreeNode(3);
tree.root.right.left = new TreeNode(5);
tree.root.right.right = new TreeNode(7);
```

This builds:

```
        4
       / \
      2   6
     / \ / \
    1  3 5  7

BST check:
Node 4: left=2 < 4 ✅, right=6 > 4 ✅
Node 2: left=1 < 2 ✅, right=3 > 2 ✅
Node 6: left=5 < 6 ✅, right=7 > 6 ✅
✅ valid BST
```

Now inorder traversal:

```
inorder(4):
  inorder(2):
    inorder(1):
      inorder(null) → return
      print 1
      inorder(null) → return
    print 2
    inorder(3):
      print 3
  print 4
  inorder(6):
    inorder(5): print 5
    print 6
    inorder(7): print 7

Output: [1, 2, 3, 4, 5, 6, 7] ✅ sorted
```

---

## The Rule — Simple

```
Any binary tree  + inorder → some order depending on structure
BST              + inorder → always sorted ascending ✅
```

---

## Your Code

Your traversal implementations are all correct by the way:

```java
public List<Integer> inorder(TreeNode root, List<Integer> list) {
    if (root == null) return list;   // ✅ base case
    inorder(root.left, list);        // ✅ left first
    list.add(root.data);             // ✅ root second
    inorder(root.right, list);       // ✅ right last
    return list;
}
```

Perfect. All three traversals are correct. The output confused you because the tree wasn't a BST — not because the code was wrong.

Change your tree to the BST above and run again — you'll get `[1,2,3,4,5,6,7]` from inorder.

---

Now post the **iterative versions** of all three traversals. That's where the real interview depth is tested.


Yes — stack. Here's the intuition before you code.

---

## Why a Stack?

Recursion uses the **call stack** implicitly. When you write:

```java
inorder(root.left)   // go left
process(root)        // come back, process
inorder(root.right)  // go right
```

The function calls pile up on the call stack and unwind in reverse order. When you do it iteratively, you manage that stack yourself explicitly.

---

## Inorder Iterative — Build The Intuition

Inorder = Left → Root → Right.

The human way of doing inorder on this tree:

```
        4
       / \
      2   6
     / \
    1   3
```

```
"Go as far left as possible first"
→ 4 → 2 → 1 → can't go left anymore

"Process current, then go right"
→ process 1 → no right → done with 1
→ back to 2 → process 2 → go right to 3
→ process 3 → no right → done with 3
→ back to 4 → process 4 → go right to 6
→ process 6
```

Two actions repeat:
1. **Go left as far as possible** — push everything onto stack
2. **Hit null — pop, process, go right**

---

## Inorder Iterative — The Pattern

```
curr = root
stack = []

loop forever:
  phase 1 — go left, push everything
    while curr != null:
      push curr
      curr = curr.left

  phase 2 — can't go left, pop and process
    if stack empty → done
    curr = stack.pop()
    process curr.val
    curr = curr.right   ← now try going left from right child
```

Trace:

```
curr=4, stack=[]

phase 1: push 4→2→1, curr=null
stack=[4,2,1]

phase 2: pop 1, process 1, curr=1.right=null
phase 1: curr=null, nothing to push
phase 2: pop 2, process 2, curr=2.right=3
phase 1: push 3, curr=null
stack=[4,3]
phase 2: pop 3, process 3, curr=3.right=null
phase 1: nothing
phase 2: pop 4, process 4, curr=4.right=6
phase 1: push 6, curr=null
stack=[6]
phase 2: pop 6, process 6, curr=null
phase 1: nothing
phase 2: stack empty → done

Output: 1,2,3,4,6 ✅
```

---

## Now Write It

```java
public List<Integer> inorderIterative(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;

    while (curr != null || !stack.isEmpty()) {
        // phase 1 — go left
        while (curr != null) {
            stack.push(curr);
            curr = curr.left;
        }
        // phase 2 — pop, process, go right
        curr = stack.pop();
        result.add(curr.data);
        curr = curr.right;
    }

    return result;
}
```

The outer loop condition `curr != null || !stack.isEmpty()` means:
- `curr != null` — still have nodes to explore
- `!stack.isEmpty()` — still have nodes waiting to be processed

Both must be false to stop.

---

## Preorder Iterative — Simpler

Preorder = Root → Left → Right.

Process node immediately when you see it. Then push right first, left second (stack is LIFO — left gets processed first).

```
stack = [root]

while stack not empty:
  node = stack.pop()
  process node          ← process immediately
  push node.right       ← right first (processed second)
  push node.left        ← left second (processed first)
```

Trace:

```
stack=[4]

pop 4, process 4, push 6, push 2 → stack=[6,2]
pop 2, process 2, push 3, push 1 → stack=[6,3,1]
pop 1, process 1, no children   → stack=[6,3]
pop 3, process 3, no children   → stack=[6]
pop 6, process 6, no children   → stack=[]

Output: 4,2,1,3,6 ✅
```

---

## Postorder Iterative — Trickiest

Postorder = Left → Right → Root. Root is processed LAST.

The trick — postorder is the reverse of a modified preorder:

```
Preorder:           Root → Left  → Right
Modified preorder:  Root → Right → Left   (swap push order)
Reverse of that:    Left → Right → Root   = Postorder ✅
```

So:
1. Run modified preorder (push left before right so right processes first)
2. Collect into a list
3. Reverse the list

```java
// modified preorder → reverse = postorder
Deque<TreeNode> stack = new ArrayDeque<>();
List<Integer> result = new ArrayList<>();
stack.push(root);

while (!stack.isEmpty()) {
    TreeNode node = stack.pop();
    result.add(node.data);              // collect
    if (node.left  != null) stack.push(node.left);   // left first
    if (node.right != null) stack.push(node.right);  // right second
}

Collections.reverse(result);           // reverse at end
return result;
```

---

## Now Write All Three Iterative Versions

You have the full pattern for each. Write them:

```java
public List<Integer> inorderIterative(TreeNode root) { }
public List<Integer> preorderIterative(TreeNode root) { }
public List<Integer> postorderIterative(TreeNode root) { }
```

Post when ready.

# Code Review — Iterative Traversals

## Inorder — One Line Missing ⚠️

```java
curr = stack.pop();
result.add(curr.data);
// ❌ missing: curr = curr.right
```

After processing a node you must move to its right child. Without this line `curr` stays null forever after the first pop — the outer while loop exits immediately after processing the leftmost node.

Trace of the bug:

```
tree: 4→2→1

phase 1: push 4,2,1, curr=null
phase 2: pop 1, process 1
         curr still = null  ← never moves to 1.right
outer while: curr=null, stack=[4,2] → continues
phase 1: curr=null, nothing pushed
phase 2: pop 2, process 2
         curr still = null
...only processes nodes, never explores right subtrees ❌
```

Fix — one line:

```java
curr = stack.pop();
result.add(curr.data);
curr = curr.right;      // ✅ explore right subtree next
```

---

## Preorder — One Bug ⚠️

```java
stack.push(curr.right);   // ❌ pushes null if no right child
stack.push(curr.left);    // ❌ pushes null if no left child
```

You're pushing null onto the stack. Next iteration pops null → `curr.data` → NullPointerException.

Fix — null check before pushing:

```java
if (curr.right != null) stack.push(curr.right);  // ✅
if (curr.left  != null) stack.push(curr.left);   // ✅
```

---

## Corrected Solutions

```java
public List<Integer> inorderIterative(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    if (root == null) return result;

    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;

    while (curr != null || !stack.isEmpty()) {
        while (curr != null) {
            stack.push(curr);
            curr = curr.left;
        }
        curr = stack.pop();
        result.add(curr.data);
        curr = curr.right;          // ✅ missing line added
    }

    return result;
}

public List<Integer> preorderIterative(TreeNode root) {
    List<Integer> result = new ArrayList<>();
    if (root == null) return result;

    Deque<TreeNode> stack = new ArrayDeque<>();
    stack.push(root);

    while (!stack.isEmpty()) {
        TreeNode curr = stack.pop();
        result.add(curr.data);
        if (curr.right != null) stack.push(curr.right);  // ✅ null check
        if (curr.left  != null) stack.push(curr.left);   // ✅ null check
    }

    return result;
}
```

---

## Now Write Postorder Iterative

You have the pattern from earlier:

```
1. Modified preorder — process root, push LEFT then RIGHT (opposite of preorder)
2. Collect into list
3. Reverse at end
```

Post it when ready. Then we move to BST operations — search, insert, validate.

# BST Operations — Deep Dive

## The BST Property — Always In Your Head

```
For every node N:
  ALL nodes in left subtree  < N.val
  ALL nodes in right subtree > N.val
```

This one rule makes every operation O(log n) on a balanced BST — at each node you eliminate half the tree.

---

## Operation 1 — Search

**"Does value X exist in this BST?"**

```
Find 6 in:
        8
       / \
      3   10
     / \    \
    1   6    14

At 8:  6 < 8 → go left
At 3:  6 > 3 → go right
At 6:  6 == 6 → found ✅
```

The decision at each node is always:
```
target == node.val → found
target  < node.val → go left
target  > node.val → go right
```

---

## Operation 2 — Insert

**"Insert value X maintaining BST property."**

```
Insert 5 into:
        8
       / \
      3   10
     / \
    1   6

At 8:  5 < 8 → go left
At 3:  5 > 3 → go right
At 6:  5 < 6 → go left
At null → insert here

        8
       / \
      3   10
     / \
    1   6
       /
      5  ✅
```

Key insight — **you always insert at a leaf position**. Never in the middle. The BST property guides you to exactly the right spot.

---

## Operation 3 — Validate BST

**"Is this binary tree a valid BST?"**

The naive approach — for each node check left < node < right. **This is wrong:**

```
        8
       / \
      3   10
       \
        20   ← 20 > 3 locally looks fine
             but 20 > 8 violates BST globally ❌
```

The correct approach — pass down valid range `[min, max]` for each node:

```
root must be in (-∞, +∞)
root.left must be in (-∞, root.val)
root.right must be in (root.val, +∞)

        8          valid range: (-∞, +∞)      8 in range ✅
       / \
      3   10       3: (-∞, 8) ✅   10: (8, +∞) ✅
     / \
    1   6          1: (-∞, 3) ✅   6: (3, 8) ✅
```

---

## Operation 4 — Delete

The trickiest BST operation. Three cases:

```
Case 1 — node is a leaf:
  just remove it

Case 2 — node has one child:
  replace node with its child

Case 3 — node has two children:
  find inorder successor (smallest node in right subtree)
  replace node's value with successor's value
  delete successor from right subtree
```

Why inorder successor? It's the smallest value that is still larger than the deleted node — maintains BST property perfectly.

---

## Now Write All Four

```java
// Search
public TreeNode search(TreeNode root, int target) { }

// Insert
public TreeNode insert(TreeNode root, int val) { }

// Validate
public boolean isValidBST(TreeNode root) { }

// Delete
public TreeNode delete(TreeNode root, int val) { }
```

**Start with search and insert — they're cleanest. Post those first, then we do validate and delete.**

Things to think through for search:
- Base cases — what are the two base cases?
- Recursive case — which direction do you go?

Things to think through for insert:
- What do you return at each recursive call?
- What do you return when you hit null?

Post when ready.

# Code Review — BST Search & Insert

## Search — Perfect ✅

```java
while (current != null) {
    if (current.data == key) return current;   // ✅ found
    if (current.data > key) current = current.left;   // ✅ go left
    else current = current.right;              // ✅ go right
}
return null;                                   // ✅ not found
```

Clean iterative solution. Nothing to fix.

---

## Insert — Correct Logic, One Signature Issue ⚠️

### Issue — Return Type Should Be `TreeNode`

```java
public static void insert(TreeNode root, int key)  // ❌ void
```

**What if the tree is empty — root is null?**

```java
insert(null, 5)
→ root == null → return   // ❌ new node never attached
                          // caller's root still null
```

You can't attach a new root node inside a void method. The caller's reference never updates. Fix — return `TreeNode`:

```java
public static TreeNode insert(TreeNode root, int key)

// Caller does:
root = insert(root, 5);   // ✅ captures new root if tree was empty
```

### Issue — Duplicate Handling

```java
} else if (current.data <= key) {   // ⚠️ <= sends duplicates right
```

Standard BST convention — duplicates are either rejected or go left. Using `<=` silently inserts duplicates. Be explicit:

```java
if (current.data == key) return root;   // reject duplicate
```

---

## Corrected Solutions

```java
// Search — iterative ✅ keep as is, just add null guard
public static TreeNode search(TreeNode root, int key) {
    while (root != null) {
        if (root.data == key)       return root;
        if (key < root.data)        root = root.left;
        else                        root = root.right;
    }
    return null;
}

// Insert — iterative, returns TreeNode
public static TreeNode insert(TreeNode root, int key) {
    TreeNode newNode = new TreeNode(key);

    // empty tree
    if (root == null) return newNode;

    TreeNode current = root;
    while (true) {
        if (key == current.data) return root;   // duplicate — reject

        if (key < current.data) {
            if (current.left == null) {
                current.left = newNode;          // ✅ insert left
                return root;
            }
            current = current.left;
        } else {
            if (current.right == null) {
                current.right = newNode;         // ✅ insert right
                return root;
            }
            current = current.right;
        }
    }
}
```

---

## Recursive Versions — Know These Too

Interviewers often ask for recursive after iterative:

```java
// Search recursive
public static TreeNode searchRecursive(TreeNode root, int key) {
    if (root == null || root.data == key) return root;
    if (key < root.data) return searchRecursive(root.left, key);
    return searchRecursive(root.right, key);
}

// Insert recursive
public static TreeNode insertRecursive(TreeNode root, int key) {
    if (root == null) return new TreeNode(key);  // base case — insert here
    if (key < root.data)
        root.left  = insertRecursive(root.left,  key);  // attach returned node
    else if (key > root.data)
        root.right = insertRecursive(root.right, key);  // attach returned node
    return root;  // always return root back up the chain
}
```

**Why `root.left = insertRecursive(root.left, key)`?**
The recursive call returns the (possibly new) subtree root. You attach it back. When insertion happens at a leaf, `null` becomes `new TreeNode(key)` — that new node bubbles back up and gets attached by its parent.

---

## Score Card

| Dimension | Search | Insert |
|---|---|---|
| Core logic | ✅ Perfect | ✅ Correct |
| Return type | ✅ | ❌ void → TreeNode |
| Empty tree case | ✅ returns null | ❌ silently returns |
| Duplicate handling | n/a | ⚠️ implicit |
| Iterative approach | ✅ | ✅ |

---

Now write **validate BST** and **delete**. Start with validate — it's the more commonly asked one at Goldman/JPMC.

Let me build the intuition before you code.

---

## Why The Naive Approach Fails

First instinct — check each node against its direct children:

```java
// WRONG approach
if (node.left.val < node.val && node.right.val > node.val)
    → valid?  NO ❌
```

This fails here:

```
        8
       / \
      3   10
       \
        20    ← 20 > 3 locally ✅
              but 20 > 8 globally ❌ violates BST
```

The problem — each node isn't just constrained by its parent. It's constrained by **every ancestor above it**.

---

## The Right Mental Model — Valid Range

Every node has a valid range it must fall within. That range narrows as you go deeper.

```
        8           must be in (-∞, +∞)
       / \
      3   10        3 must be in (-∞, 8)
     / \              10 must be in (8, +∞)
    1   6           1 must be in (-∞, 3)
                      6 must be in (3, 8)
```

How does the range update as you go down?

```
Going LEFT  from node N:
  max shrinks to N.val   (everything left must be < N)
  min stays the same

Going RIGHT from node N:
  min grows to N.val     (everything right must be > N)
  max stays the same
```

---

## Concrete Trace

```
isValid(8,  min=-∞, max=+∞):  -∞ < 8 < +∞  ✅
  isValid(3,  min=-∞, max=8):   -∞ < 3 < 8   ✅
    isValid(1,  min=-∞, max=3):   -∞ < 1 < 3   ✅
    isValid(6,  min=3,  max=8):   3  < 6 < 8   ✅
  isValid(10, min=8,  max=+∞):  8  < 10 < +∞  ✅

All pass → valid BST ✅
```

Now the broken tree:

```
        8
       / \
      3   10
       \
        20

isValid(8,  min=-∞, max=+∞):  ✅
  isValid(3,  min=-∞, max=8):  ✅
    isValid(20, min=3,  max=8):  3 < 20 < 8?  20 < 8? NO ❌
```

---

## The Code Structure

```java
public boolean isValidBST(TreeNode root) {
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}

private boolean validate(TreeNode node, long min, long max) {
    // base case
    if (node == null) return true;

    // check current node is in valid range
    if (node.data <= min || node.data >= max) return false;

    // recurse — update range
    return validate(node.left,  min, node.data)   // left: max shrinks
        && validate(node.right, node.data, max);  // right: min grows
}
```

**Why `Long.MIN_VALUE` and `Long.MAX_VALUE` instead of `Integer`?**

```
If root.val = Integer.MIN_VALUE:
  validate(root.left, Integer.MIN_VALUE, root.val)
  node.data <= min → Integer.MIN_VALUE <= Integer.MIN_VALUE → true → fails ❌

Using Long avoids this — integer values never equal Long boundaries
```

---

## Now Write It

Three things to get right:

1. Base case — null node
2. Range check — current node violates range → return false
3. Recursive calls — what range do you pass left? What range do you pass right?

Post your solution.

# Generic Templates to Navigate Subtrees

## The One Mental Model

Every tree problem is just a question you ask at each node, combined with answers from left and right subtrees.

```
solve(node):
  if node == null → return base case
  
  leftAnswer  = solve(node.left)    // trust left subtree
  rightAnswer = solve(node.right)   // trust right subtree
  
  return combine(leftAnswer, rightAnswer, node.val)
```

Everything else is just what "base case", "trust", and "combine" mean for your specific problem.

---

## Template 1 — Top Down (Pass Information Down)

Use when: parent needs to tell children something — valid range, running sum, current depth.

```java
void topDown(TreeNode node, int valueFromParent) {
    if (node == null) return;

    // use valueFromParent + node.val to do something
    int newValue = compute(valueFromParent, node.val);

    // pass updated value down to children
    topDown(node.left,  newValue);
    topDown(node.right, newValue);
}

// Call with initial value:
topDown(root, initialValue);
```

**Real examples:**
```
Validate BST     → pass (min, max) range down
Max depth        → pass current depth down
Path sum exists  → pass remaining sum down
```

---

## Template 2 — Bottom Up (Collect Information Up)

Use when: children need to tell parent something — height, diameter, max path sum.

```java
int bottomUp(TreeNode node) {
    if (node == null) return baseCase;   // leaf's children return this

    int left  = bottomUp(node.left);     // collect from left
    int right = bottomUp(node.right);    // collect from right

    // combine left + right + current node
    return combine(left, right, node.val);
}
```

**Real examples:**
```
Height of tree   → return 1 + max(left, right)
Diameter         → update global max with left+right, return 1+max(left,right)
Max path sum     → update global max, return node.val + max(left,right)
Count nodes      → return 1 + left + right
```

---

## Template 3 — Top Down + Bottom Up (Pass Down AND Collect Up)

Use when: you need both — pass constraints down AND collect results up.

```java
int solve(TreeNode node, int fromParent) {
    if (node == null) return baseCase;

    // use parent's info at this node
    int current = compute(fromParent, node.val);

    // collect from children, passing current down
    int left  = solve(node.left,  current);
    int right = solve(node.right, current);

    return combine(left, right);
}
```

**Real examples:**
```
Path sum count    → pass running sum down, collect count up
Good nodes count  → pass max so far down, collect count up
```

---

## Template 4 — Early Return (Prune Subtrees)

Use when: once a condition fails you don't need to explore further.

```java
boolean solve(TreeNode node) {
    if (node == null) return true;   // empty subtree always valid

    // check condition at current node
    if (conditionFails(node)) return false;   // prune — stop here

    // only explore if current node passes
    return solve(node.left) && solve(node.right);
}
```

**Real examples:**
```
Validate BST      → range violated → return false immediately
Symmetric tree    → mismatch → return false immediately
Same tree         → values differ → return false immediately
```

The `&&` short circuits — if left returns false, right never evaluates.

---

## Template 5 — Global Variable (Collect Across Subtrees)

Use when: the answer spans across left AND right subtrees through a node — can't be returned up cleanly.

```java
int globalMax = Integer.MIN_VALUE;   // or 0, or whatever makes sense

void solve(TreeNode node) {
    if (node == null) return 0;

    int left  = solve(node.left);
    int right = solve(node.right);

    // answer at this node combines left + right + current
    // this crosses subtrees — can't return it up
    globalMax = Math.max(globalMax, left + right + node.val);

    // return only the best single path upward
    return node.val + Math.max(left, right);
}
```

**Real examples:**
```
Diameter of tree  → diameter crosses through node (left+right)
                    but height returned upward is 1+max(left,right)
Max path sum      → path crosses through node
                    but single path returned upward
```

---

## Which Template For Which Problem

| Problem | Template | Why |
|---|---|---|
| Validate BST | Top Down | pass (min,max) range down |
| Height of tree | Bottom Up | collect height up |
| Diameter | Bottom Up + Global | crosses subtrees |
| Path sum exists | Top Down | pass remaining sum down |
| Count path sums | Top Down + Bottom Up | pass sum down, count up |
| Max path sum | Bottom Up + Global | crosses subtrees |
| LCA | Bottom Up | collect found nodes up |
| Symmetric tree | Early Return | prune on mismatch |
| Same tree | Early Return | prune on mismatch |
| Serialize tree | Preorder Top Down | visit root before children |

---

## The Decision Flow

```
Does parent need to tell children something?
  YES → Top Down (pass value down)

Do children need to tell parent something?
  YES → Bottom Up (return value up)

Does the answer span ACROSS left and right subtrees?
  YES → Bottom Up + Global variable

Can you stop early when condition fails?
  YES → Early Return with &&
```

Most problems use Bottom Up. When you're stuck, start there.

---

## Applied To Your Next Problem — Validate BST

```
Does parent need to tell children something? 
  YES → valid range (min, max) → Top Down

Does it need early return?
  YES → range violated → return false immediately → Early Return too

Template: Top Down + Early Return
```

```java
boolean isValidBST(TreeNode node, long min, long max) {
    if (node == null) return true;                    // Template 4 base case
    if (node.val <= min || node.val >= max)           // Template 4 early return
        return false;
    return isValidBST(node.left,  min, node.val)      // Template 1 pass down
        && isValidBST(node.right, node.val, max);     // Template 1 pass down
}
```

Now write it with your own variable names and post it.

# Height & Diameter — Deep Dive

## Height of a Tree

**Definition:** Longest path from root to any leaf. Measured in edges or nodes depending on the problem — always clarify.

```
        1
       / \
      2   3
     / \
    4   5

Height = 2 (edges) or 3 (nodes)
Most problems mean nodes — confirm in interview
```

**The recursive insight — Bottom Up Template:**

```
Height of a node = 1 + max(height of left, height of right)
Height of null  = 0  ← base case
```

At every node you're asking: *"what's the tallest path below me?"* You get that from your children, add 1 for yourself, return up.

```
height(4) = 1 + max(0,0) = 1
height(5) = 1 + max(0,0) = 1
height(2) = 1 + max(1,1) = 2
height(3) = 1 + max(0,0) = 1
height(1) = 1 + max(2,1) = 3
```

---

## Diameter of a Tree

**Definition:** Longest path between any two nodes. The path may or may not pass through root.

```
        1
       / \
      2   3
     / \
    4   5

Diameter = 3 edges (4→2→5 is length 2, but 4→2→1→3 is length 3)
```

**The key insight — the diameter at any node = left height + right height:**

```
At node 2: left height=1, right height=1 → path through 2 = 2
At node 1: left height=2, right height=1 → path through 1 = 3  ← answer
```

But you can't return both the diameter AND the height up the chain in one return value. This is the **Bottom Up + Global Variable** template:

```
return height upward      → used by parent to compute its diameter
update global diameter    → left height + right height at current node
```

---

## Question 1 — Height of Binary Tree

**"Given a binary tree, find its height (number of nodes on longest root-to-leaf path)."**

```
Input:
        1
       / \
      2   3
     / \
    4   5

Output: 3

Input: null
Output: 0

Input: single node
Output: 1
```

**Write it — one of the shortest tree solutions you'll write:**

```java
public static int height(TreeNode root) { }
```

Post when ready.

# Code Review — Height of Tree

## Verdict: Perfect ✅

Two lines. Exactly right. Nothing to fix.

---

## Correctness Check

```java
if (root == null) return 0;                              // ✅ base case
return 1 + Math.max(height(root.left), height(root.right)); // ✅ recursive case
```

This is the canonical solution. Every interviewer at Goldman/JPMC/Citi will recognise this immediately as correct.

---

## What To Say Out Loud

> "I use bottom-up recursion. Base case — null returns 0. For every other node, height is 1 for the current node plus the taller of its two subtrees. The recursion bottoms out at leaves and builds the answer upward."

---

# Question 2 — Diameter of Binary Tree

**"Given a binary tree, find the length of the longest path between any two nodes. The path does not need to pass through the root."**

```
Input:
        1
       / \
      2   3
     / \
    4   5

Output: 3  (path: 4→2→5 = 2 edges, but 4→2→1→3 = 3 edges)

Input:
        1
       /
      2
     /
    3

Output: 2  (path: 3→2→1)

Input: single node
Output: 0  (no edges)
```

**Constraints:** O(n) time.

---

## The Insight

At every node the longest path **through that node** = height of left subtree + height of right subtree.

```
At node 2:
  left height  = 1  (node 4)
  right height = 1  (node 5)
  path through 2 = 1 + 1 = 2

At node 1:
  left height  = 2  (nodes 2,4 or 2,5)
  right height = 1  (node 3)
  path through 1 = 2 + 1 = 3  ← answer
```

**The problem:** you need to track the maximum across ALL nodes, not just return one value up. This needs a global variable.

```java
int maxDiameter = 0;   // updated at every node

// at each node:
maxDiameter = Math.max(maxDiameter, leftHeight + rightHeight);

// return height upward for parent to use:
return 1 + Math.max(leftHeight, rightHeight);
```

**Two things happening at each node:**
1. **Update** global max with path through current node
2. **Return** height for parent's calculation

---

## Write It

```java
private int maxDiameter = 0;

public int diameterOfBinaryTree(TreeNode root) {
    // your code here
}

private int heightForDiameter(TreeNode node) {
    // your code here
    // at each node: update maxDiameter
    // return height upward
}
```

Post when ready.