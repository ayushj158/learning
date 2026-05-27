This is a specific DFS pattern called **backtracking**. Let me explain the difference.

---

## Three DFS patterns

---

### Pattern 1 — Pure DFS (graph/tree traversal)

Just visit nodes, no backtracking needed:

```java
void dfs(int node, Set<Integer> visited) {
    visited.add(node);
    System.out.println(node);  // process

    for (int neighbor : graph.get(node)) {
        if (!visited.contains(neighbor)) {
            dfs(neighbor, visited);  // go deeper
        }
    }
    // NO backtracking — visited set prevents revisiting
}
```

Use when:
```
Number of Islands ✅
Kill Process ✅
Virus Spread ✅
Cycle Detection ✅
```

---

### Pattern 2 — Backtracking DFS

Visit, process, UNDO when coming back:

```java
void dfs(TrieNode node, StringBuilder current) {
    if (node.isEndOfWord) print(current);

    for (char c : node.children.keySet()) {
        current.append(c);           // ← DO
        dfs(node.children.get(c), current);
        current.deleteCharAt(last);  // ← UNDO
    }
}
```

Use when:
```
Print Trie words ✅
Permutations ✅
Subsets ✅
Restore IP Addresses ✅
Word Search ✅
Generate all paths ✅
```

Key difference from Pattern 1:
```
Pattern 1: visited set prevents revisiting
           no need to undo

Pattern 2: no visited set needed
           undo at each step to explore all combinations
```

---

### Pattern 3 — DFS with return value

Returns something from each subtree:

```java
boolean dfs(TreeNode node, int target) {
    if (node == null) return false;  // base case

    if (node.val == target) return true;  // found

    return dfs(node.left, target)     // check left
        || dfs(node.right, target);   // check right
}
```

Use when:
```
Path exists in graph ✅
Validate BST ✅
Has path sum ✅
Equal Tree Partition ✅
```

---

## Which template for which problem

```
"Find/count paths, explore ALL combinations"
→ Backtracking (Pattern 2)
→ append + recurse + deleteCharAt

"Visit all nodes, mark visited"
→ Pure DFS (Pattern 1)
→ visited set + recurse

"Check condition, return true/false"
→ DFS with return (Pattern 3)
→ recurse left/right, return boolean
```

---

## The Trie print IS backtracking

```
Why backtracking for Trie?

Because you're exploring ALL possible word paths
Same StringBuilder reused throughout
Must undo each character to try other branches

If you didn't backtrack:
  visit 'c' → current="c"
  visit 'a' → current="ca"
  visit 't' → current="cat" → print "cat" ✓
  visit 'r' → current="catr" ← WRONG! should be "car"
  
With backtracking:
  visit 't' → "cat" → print → undo → "ca"
  visit 'r' → "car" → print ✓
```

---

## Summary

```
Pure DFS:       navigate graph/tree, visited set
Backtracking:   explore all combinations, undo steps
DFS+return:     check condition, bubble up result

All three use recursion
All three go depth-first
Difference is WHAT you do after returning from recursion
```

---

Clear? Ready for **LC 211 — Wildcard Search**?