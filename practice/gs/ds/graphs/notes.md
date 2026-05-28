## Graph Theory — Complete Guide

---

## What is a Graph?

```
A graph is a set of NODES connected by EDGES

Nodes = entities (cities, users, processes, instruments)
Edges = relationships (roads, friendships, dependencies, trades)

Real examples:
  Social network: users = nodes, friendships = edges
  Trading system: instruments = nodes, correlations = edges
  Process tree:   processes = nodes, parent-child = edges
  City map:       cities = nodes, roads = edges
```

---

## Types of Graphs

### Directed vs Undirected

```
Undirected:
  A — B (A connects to B AND B connects to A)
  Friendship: if I'm your friend, you're my friend
  
Directed (Digraph):
  A → B (A connects to B but NOT necessarily B to A)
  Twitter follow: I follow you doesn't mean you follow me
  Process: parent spawns child (Kill Process LC582)
  
  0 → 3 → 1
      ↓
      5 → 10
```

### Weighted vs Unweighted

```
Unweighted: edges just exist (no cost)
  "Is there a path from A to B?"

Weighted: edges have cost/distance
  City map: road length = weight
  "What is SHORTEST path from A to B?"
```

### Cyclic vs Acyclic

```
Cyclic: contains a cycle (can visit same node twice)
  A → B → C → A ← cycle!

Acyclic: no cycles
  Tree is an acyclic graph
  DAG = Directed Acyclic Graph
  Task dependencies (must do A before B before C)
```

---

## Graph Representations

### 1. Adjacency List (most common)

```java
// For each node, store list of neighbors
Map<Integer, List<Integer>> graph = new HashMap<>();

// undirected edge A-B:
graph.get(A).add(B);
graph.get(B).add(A);

// directed edge A→B:
graph.get(A).add(B);  // only one direction

Example:
  0: [1, 2]
  1: [0, 3]
  2: [0, 4]
  3: [1]
  4: [2]

Space: O(V + E)  ← V=vertices, E=edges
Good for: sparse graphs (few edges)
```

### 2. Adjacency Matrix

```java
int[][] matrix = new int[n][n];

// edge from i to j:
matrix[i][j] = 1;
// undirected: also matrix[j][i] = 1

Space: O(V²)
Good for: dense graphs, O(1) edge lookup
Bad for: sparse graphs (wastes space)
```

---

## BFS vs DFS — when to use which

```
BFS (Breadth First Search):
  Explore level by level
  Uses: Queue
  Finds: SHORTEST path (unweighted)
  Good for:
    Shortest path
    Level-order traversal
    "Minimum steps to reach X"
    Connected components

DFS (Depth First Search):
  Explore one path fully, then backtrack
  Uses: Stack/Recursion
  Finds: IF path exists
  Good for:
    Cycle detection
    Topological sort
    All paths
    Connected components
    Tree problems
```

---

## BFS — template

```java
void bfs(int start, Map<Integer, List<Integer>> graph) {
    Queue<Integer> queue = new LinkedList<>();
    Set<Integer> visited = new HashSet<>();

    queue.offer(start);
    visited.add(start);

    while (!queue.isEmpty()) {
        int node = queue.poll();
        System.out.println(node);  // process

        for (int neighbor : graph.getOrDefault(node, List.of())) {
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                queue.offer(neighbor);
            }
        }
    }
}
```

### BFS for shortest path

```java
int shortestPath(int start, int end, 
                 Map<Integer, List<Integer>> graph) {
    Queue<Integer> queue = new LinkedList<>();
    Set<Integer> visited = new HashSet<>();
    int steps = 0;

    queue.offer(start);
    visited.add(start);

    while (!queue.isEmpty()) {
        int size = queue.size();  // process level by level

        for (int i = 0; i < size; i++) {
            int node = queue.poll();
            if (node == end) return steps;  // found!

            for (int neighbor : graph.getOrDefault(node, List.of())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        steps++;  // finished one level
    }
    return -1;  // not reachable
}
```

---

## DFS — template

```java
// recursive
void dfs(int node, Set<Integer> visited,
         Map<Integer, List<Integer>> graph) {
    visited.add(node);
    System.out.println(node);  // process

    for (int neighbor : graph.getOrDefault(node, List.of())) {
        if (!visited.contains(neighbor)) {
            dfs(neighbor, visited, graph);
        }
    }
}

// iterative (using stack)
void dfsIterative(int start, Map<Integer, List<Integer>> graph) {
    Stack<Integer> stack = new Stack<>();
    Set<Integer> visited = new HashSet<>();

    stack.push(start);

    while (!stack.isEmpty()) {
        int node = stack.pop();
        if (visited.contains(node)) continue;
        visited.add(node);
        System.out.println(node);

        for (int neighbor : graph.getOrDefault(node, List.of())) {
            if (!visited.contains(neighbor)) {
                stack.push(neighbor);
            }
        }
    }
}
```

---

## Important Graph Algorithms

### 1. Connected Components

```
How many separate groups exist in graph?

[1-2-3]   [4-5]   [6]
Group 1   Group 2  Group 3
= 3 connected components

Algorithm: run DFS/BFS from each unvisited node
Each new DFS = new component

int countComponents(int n, int[][] edges) {
    Map<Integer, List<Integer>> graph = buildGraph(edges);
    Set<Integer> visited = new HashSet<>();
    int components = 0;

    for (int i = 0; i < n; i++) {
        if (!visited.contains(i)) {
            dfs(i, visited, graph);  // marks all in this component
            components++;
        }
    }
    return components;
}
```

### 2. Cycle Detection (Directed Graph)

```
Used in: dependency resolution, deadlock detection

Three states per node:
  WHITE = unvisited
  GRAY  = currently in recursion stack (being processed)
  BLACK = fully processed

Cycle exists if we visit a GRAY node
(we're already processing it = cycle!)

boolean hasCycle(int node, int[] color,
                 Map<Integer, List<Integer>> graph) {
    color[node] = GRAY;  // mark as being processed

    for (int neighbor : graph.getOrDefault(node, List.of())) {
        if (color[neighbor] == GRAY) return true;  // cycle!
        if (color[neighbor] == WHITE) {
            if (hasCycle(neighbor, color, graph)) return true;
        }
    }

    color[node] = BLACK;  // fully processed
    return false;
}
```

### 3. Topological Sort

```
Order tasks so dependencies come first
Only works on DAG (no cycles)

Example:
  Task A must happen before B and C
  Task B must happen before D
  
  Valid order: A → B → C → D

Algorithm: DFS + add to result AFTER processing all neighbors

void topoSort(int node, Set<Integer> visited,
              Stack<Integer> result,
              Map<Integer, List<Integer>> graph) {
    visited.add(node);
    
    for (int neighbor : graph.getOrDefault(node, List.of())) {
        if (!visited.contains(neighbor)) {
            topoSort(neighbor, visited, result, graph);
        }
    }
    
    result.push(node);  // add AFTER processing all neighbors
}

// call for all nodes, then read stack top-to-bottom
```

### 4. Shortest Path — Dijkstra (weighted graph)

```
Find shortest path considering edge weights

Uses: MinHeap (PriorityQueue)
Like BFS but with distances

int[] dijkstra(int start, int n,
               Map<Integer, List<int[]>> graph) {
    // graph: node → list of [neighbor, weight]
    
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[start] = 0;

    PriorityQueue<int[]> pq = new PriorityQueue<>(
        (a, b) -> a[1] - b[1]);  // min heap by distance
    pq.offer(new int[]{start, 0});

    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int node = curr[0], d = curr[1];

        if (d > dist[node]) continue;  // stale entry

        for (int[] edge : graph.getOrDefault(node, List.of())) {
            int neighbor = edge[0], weight = edge[1];
            int newDist = dist[node] + weight;

            if (newDist < dist[neighbor]) {
                dist[neighbor] = newDist;
                pq.offer(new int[]{neighbor, newDist});
            }
        }
    }
    return dist;
}
```

---

## GS-specific graph problems

### Virus Spread in Directed Graph

```
Given directed graph — if node X is infected,
which nodes get infected?

Answer: DFS/BFS from X following directed edges only

void spread(int infected,
            Map<Integer, List<Integer>> graph,
            Set<Integer> affected) {
    Queue<Integer> queue = new LinkedList<>();
    queue.offer(infected);
    affected.add(infected);

    while (!queue.isEmpty()) {
        int node = queue.poll();
        for (int neighbor : graph.getOrDefault(node, List.of())) {
            if (!affected.contains(neighbor)) {
                affected.add(neighbor);
                queue.offer(neighbor);
            }
        }
    }
}
```

### Largest Tree in Forest

```
Given list of edges forming a forest (multiple trees)
Find the tree with most nodes

Algorithm:
  Build adjacency list
  BFS/DFS from each unvisited node
  Count nodes in each component
  Return max

int largestTree(int n, int[][] edges) {
    Map<Integer, List<Integer>> graph = new HashMap<>();
    for (int[] edge : edges) {
        graph.computeIfAbsent(edge[0], k -> new ArrayList<>()).add(edge[1]);
        graph.computeIfAbsent(edge[1], k -> new ArrayList<>()).add(edge[0]);
    }

    Set<Integer> visited = new HashSet<>();
    int maxSize = 0;

    for (int i = 0; i < n; i++) {
        if (!visited.contains(i)) {
            int size = bfsCount(i, visited, graph);
            maxSize = Math.max(maxSize, size);
        }
    }
    return maxSize;
}

int bfsCount(int start, Set<Integer> visited,
             Map<Integer, List<Integer>> graph) {
    Queue<Integer> queue = new LinkedList<>();
    queue.offer(start);
    visited.add(start);
    int count = 0;

    while (!queue.isEmpty()) {
        int node = queue.poll();
        count++;
        for (int neighbor : graph.getOrDefault(node, List.of())) {
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                queue.offer(neighbor);
            }
        }
    }
    return count;
}
```

---

## GS interview — graph pattern recognition

```
"Find connected components"     → DFS/BFS, count starts
"Shortest path (unweighted)"    → BFS, level counting
"Shortest path (weighted)"      → Dijkstra, MinHeap
"All paths exist?"              → DFS, visited set
"Detect cycle"                  → DFS, gray/black coloring
"Task ordering/dependencies"    → Topological sort
"Spread from infected node"     → BFS from infected node
"Kill process and children"     → BFS from target (LC582)
"Islands/connected regions"     → DFS/BFS on grid
```

---

## Grid as graph

```
Grid problems ARE graph problems
Each cell = node
Adjacent cells = edges (up/down/left/right)

int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

void dfs(int[][] grid, int r, int c, boolean[][] visited) {
    if (r < 0 || r >= grid.length ||
        c < 0 || c >= grid[0].length ||
        visited[r][c] || grid[r][c] == 0) return;

    visited[r][c] = true;

    for (int[] dir : dirs) {
        dfs(grid, r + dir[0], c + dir[1], visited);
    }
}

Used for:
  Number of Islands (LC200) ✅
  Max Area of Island (LC695) ✅
  Max Rocks Grid (asked at GS!) ✅
```

---

## Complexity summary

```
BFS/DFS:        O(V + E)  time, O(V) space
Dijkstra:       O((V+E) log V) with MinHeap
Topological:    O(V + E)
Cycle detection: O(V + E)

V = number of vertices/nodes
E = number of edges
```

---

Graph Theory ✅ complete.



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


## Mental Model — How to Identify the Right Algorithm

---

## The Decision Tree

### Step 1 — What kind of problem is it?

```
Does it involve a GRID or GRAPH (nodes + connections)?
  YES → Graph/Grid section below

Does it involve OPTIMIZATION (min/max/count)?
  YES → DP section below

Does it involve SEARCHING in sorted data?
  YES → Binary Search

Does it involve a STREAM or WINDOW of data?
  YES → Sliding Window / Two Pointer

Does it involve NEXT GREATER/SMALLER element?
  YES → Monotonic Stack

Does it involve TOP K elements?
  YES → Heap

Does it involve PREFIX matching / string search?
  YES → Trie

Does it involve ORDERING with constraints?
  YES → Greedy
```

---

## Graph / Grid Mental Model

### First question — directed or undirected?

```
Directed edges (A→B not B→A):
  Parent-child relationships (Kill Process)
  Dependency chains
  Virus spread (infection flows one way)

Undirected edges (A-B both ways):
  Islands (cells connect both ways)
  Social networks (friendship is mutual)
  Connected components
```

### Second question — what are you finding?

```
"Does path exist?"
"Visit all nodes"
"Count components"
→ DFS (simpler, less code)

"SHORTEST path"
"MINIMUM steps"
"LEVEL of node"
→ BFS (guarantees shortest in unweighted)

"SHORTEST path with WEIGHTS"
→ Dijkstra (BFS + MinHeap)

"ORDER tasks by dependency"
"Schedule with prerequisites"
→ Topological Sort (DFS variant)

"Detect cycle"
→ DFS with gray/black coloring
```

### Grid problems — always same template

```
Clue words: "island", "region", "connected cells",
            "spread", "flood fill", "rocks/stones"

Always:
  Each cell = node
  Adjacent cells = edges
  visited array prevents revisiting

Use DFS when: just need to explore, count area
Use BFS when: need minimum steps/distance
```

---

## BFS vs DFS — precise rule

```
USE BFS WHEN:
  "Minimum", "shortest", "fewest steps"
  Need level-by-level processing
  Answer is close to start node
  
  Examples:
    Shortest path in maze
    Minimum dice rolls (Snakes & Ladders)
    Level order of tree
    Minimum infection spread time

USE DFS WHEN:
  "Does path exist?", "all paths", "find any path"
  Need to explore complete subtrees
  Backtracking needed
  Tree/recursive structure
  
  Examples:
    Number of islands (just explore, count)
    Kill process (explore all children)
    Cycle detection
    Topological sort
    Word search on grid
    Print trie words
```

---

## DP Mental Model

### When is it DP?

```
Three signals — if ANY present → think DP:

Signal 1: OPTIMAL substructure
  "Optimal answer to big problem
   built from optimal answers to subproblems"
  
  Coin change: min coins for 10 = min(
    1 + min coins for 9,
    1 + min coins for 7,
    1 + min coins for 6
  )

Signal 2: OVERLAPPING subproblems
  "Same subproblem computed multiple times"
  Without DP: exponential time
  With DP: polynomial time

Signal 3: Key words
  "minimum", "maximum", "count ways",
  "can you achieve", "longest", "shortest"
  + involves making CHOICES at each step
```

### DP subtype identification

```
"Count paths / ways to reach"
  → 2D grid DP
  → dp[r][c] = paths to reach (r,c)
  → Unique Paths, Min Path Sum

"Minimum coins / steps to reach amount"
  → 1D DP, unbounded choices
  → dp[i] = min cost to reach i
  → Coin Change

"Can we form / reach / break"
  → 1D boolean DP
  → dp[i] = can we reach/form i
  → Word Break, Jump Game

"Longest subsequence"
  → 1D DP, look back at all previous
  → dp[i] = longest ending at i
  → LIS

"Max profit with constraints"
  → State machine DP
  → track multiple states simultaneously
  → Stock Buy Sell

"Triangle / path in tree"
  → DP on tree structure
  → dp[i][j] = best at row i, position j
  → Triangle
```

---

## Two Pointer Mental Model

```
USE WHEN:
  Sorted array + find pair/triplet
  Palindrome check
  Remove duplicates
  Container / water problems

Pattern:
  lo = 0, hi = n-1
  move based on condition

Clue words:
  "sorted array", "pair that sums to",
  "palindrome", "container", "two sum"
```

---

## Sliding Window Mental Model

```
USE WHEN:
  Subarray / substring of size K
  Longest/shortest subarray with condition
  Running computation over window

Pattern:
  expand right → check condition
  shrink left → maintain condition

Clue words:
  "substring", "subarray", "window",
  "consecutive", "at most K distinct",
  "longest without repeating"
```

---

## Binary Search Mental Model

```
USE WHEN:
  Sorted array (or can be sorted)
  "Find X in sorted"
  "Minimum possible maximum"
  "Can we achieve X?" → binary search on answer

Pattern:
  lo=0, hi=n-1
  check mid → go left or right

Clue words:
  "sorted", "rotated sorted",
  "minimum/maximum possible value",
  "feasibility check" (Koko, capacity)
```

---

## Heap Mental Model

```
USE WHEN:
  "Top K", "Kth largest/smallest"
  "Running median"
  "Priority based processing"
  Streaming data + maintain K elements

MinHeap: top K largest (smallest at top = evicted first)
MaxHeap: top K smallest (largest at top = evicted first)

Clue words:
  "top K", "Kth", "most frequent",
  "median", "priority", "scheduling"
```

---

## Monotonic Stack Mental Model

```
USE WHEN:
  "Next greater element"
  "Next smaller element"
  "Previous greater element"
  Spans / ranges in array

Pattern:
  maintain decreasing stack
  when current > top → pop (found answer for top)
  push current

Clue words:
  "next greater", "next warmer",
  "daily temperatures", "stock span",
  "largest rectangle"
```

---

## Greedy Mental Model

```
USE WHEN:
  Local optimal = global optimal
  "Minimum number of X"
  Can make one-pass decisions

Test: can you prove greedy works?
  If choosing locally best at each step
  leads to globally best answer → greedy

Clue words:
  "minimum jumps", "minimum arrows",
  "meeting rooms", "gas station",
  "task scheduling"

vs DP:
  Greedy: make ONE choice per step, never reconsider
  DP:     try ALL choices, remember best
  
  If greedy gives wrong answer → use DP
```

---

## Backtracking Mental Model

```
USE WHEN:
  Generate ALL combinations/permutations
  Find ALL valid solutions
  Constraint satisfaction

Pattern:
  choose → explore → unchoose (backtrack)

Clue words:
  "all combinations", "all permutations",
  "generate all", "valid arrangements",
  "restore IP", "letter combinations"
```

---

## The full decision tree — one page

```
START
  │
  ├── Grid/Graph problem?
  │     ├── Shortest path, minimum steps → BFS
  │     ├── Explore all, check existence → DFS
  │     ├── Weighted shortest path → Dijkstra
  │     ├── Task ordering → Topological Sort
  │     └── Cycle detection → DFS (gray/black)
  │
  ├── Sorted array?
  │     ├── Find element → Binary Search
  │     ├── Find pair/palindrome → Two Pointer
  │     └── Subarray → Sliding Window
  │
  ├── Optimal value (min/max/count)?
  │     ├── Can greedy work? → Greedy
  │     ├── Overlapping subproblems? → DP
  │     │     ├── 1D choices → 1D DP
  │     │     ├── Grid → 2D DP
  │     │     └── State machine → Multi-state DP
  │     └── Generate all → Backtracking
  │
  ├── Top K / Priority?
  │     └── Heap (MinHeap for top K largest)
  │
  ├── Next greater/smaller?
  │     └── Monotonic Stack
  │
  └── String prefix matching?
        └── Trie
```

---

## Applied to GS problems you've seen

```
Number of Islands      → Grid DFS (explore connected cells)
Snakes & Ladders       → Grid BFS (minimum dice rolls)
Kill Process           → Graph BFS (all descendants)
Virus Spread           → Directed Graph BFS
Largest Tree           → Graph BFS (count component size)
Coin Change            → 1D DP (min coins)
Word Break             → 1D DP (can we form)
Unique Paths           → 2D DP (count paths)
LIS                    → 1D DP (look back)
Stock Buy Sell III     → State Machine DP
House Robber           → 1D DP (skip or take)
Koko Eating Bananas    → Binary Search on answer
Single Element Sorted  → Binary Search
Top K Frequent         → MinHeap of size K
Daily Temperatures     → Monotonic Stack
Next Greater Element   → Monotonic Stack
Restore IP Addresses   → Backtracking
Trie problems          → Trie + DFS
```

---

## Quick identification test — 3 questions

```
Question 1: Is there a graph/grid?
  YES → BFS (shortest) or DFS (exists/explore)

Question 2: Are you optimizing something?
  YES → DP (if choices + overlap) or Greedy (if local=global)

Question 3: Is data sorted?
  YES → Binary Search or Two Pointer
```

---

Clear? Ready for mock interview or remaining DSA problems?
