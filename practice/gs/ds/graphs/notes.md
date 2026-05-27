## LC 582 — Kill Process

> Given a list of process IDs and their parent IDs, kill a target process and ALL its children (recursively).

```
pid    = [1, 3, 10, 5]
ppid   = [3, 0, 5,  3]
kill   = 5

Relationships:
  3 is parent of 1
  0 is parent of 3  ← root (no parent)
  5 is parent of 10
  3 is parent of 5

Tree:
      3
     / \
    1   5
        |
        10

kill=5 → kill 5 and all descendants → [5, 10]
```

---

## Before coding — two questions:

1. How do you build the parent→children relationship efficiently?
2. Once you have the tree — which traversal gives you a process AND all its descendants?

Take a shot.