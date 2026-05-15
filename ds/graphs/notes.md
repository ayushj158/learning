# BFS & DFS — From Scratch

## The Core Idea

You have a bunch of nodes connected to each other. You want to visit all of them. There are two strategies:

```
BFS — visit all NEARBY nodes first, then go further
DFS — go as DEEP as possible first, then backtrack
```

---

## Physical Analogy

Imagine you're lost in a building looking for a exit.

**BFS — check every room on floor 1 first:**
```
Floor 1: room 1, room 2, room 3... (all checked)
Floor 2: room 1, room 2, room 3... (all checked)
Floor 3: ...
```
Guarantees you find the NEAREST exit.

**DFS — pick one corridor and go to the end:**
```
Corridor A → A1 → A1a → dead end → backtrack
          → A1b → dead end → backtrack
          → A2 → found exit!
```
Explores one path completely before trying another.

---

## On a Tree — The Difference Is Visual

```
        1
       / \
      2   3
     / \   \
    4   5   6

BFS order: 1, 2, 3, 4, 5, 6
  visit level by level — all of level 1, then level 2, then level 3

DFS order (preorder): 1, 2, 4, 5, 3, 6
  go deep left first, then backtrack
```

---

## On a Graph — Same Idea

```
Graph:
1 — 2 — 4
|       |
3 — — — 5

BFS from 1: 1, 2, 3, 4, 5
  visit all neighbours of 1 first (2,3)
  then all neighbours of 2 (4)
  then all neighbours of 3 (5)

DFS from 1: 1, 2, 4, 5, 3
  go deep: 1→2→4→5→backtrack→3
```

---

## BFS — How It Works

Uses a **Queue** (FIFO). Process node, add its neighbours, repeat.

```
BFS from node 1:

Start: queue=[1], visited={1}

Step 1: poll 1, process 1
        add neighbours 2,3 → queue=[2,3], visited={1,2,3}

Step 2: poll 2, process 2
        add unvisited neighbours 4 → queue=[3,4], visited={1,2,3,4}

Step 3: poll 3, process 3
        add unvisited neighbours 5 → queue=[4,5], visited={1,2,3,4,5}

Step 4: poll 4, process 4
        no unvisited neighbours → queue=[5]

Step 5: poll 5, process 5
        no unvisited neighbours → queue=[]

Done. Order: 1,2,3,4,5
```

**BFS Template:**
```java
Queue<Integer> queue = new LinkedList<>();
Set<Integer> visited = new HashSet<>();

queue.offer(start);
visited.add(start);

while (!queue.isEmpty()) {
    int node = queue.poll();
    // process node

    for (int neighbour : graph.get(node)) {
        if (!visited.contains(neighbour)) {
            visited.add(neighbour);
            queue.offer(neighbour);
        }
    }
}
```

---

## DFS — How It Works

Uses a **Stack** (LIFO) — or recursion (which uses call stack implicitly).

```
DFS from node 1:

Start: stack=[1], visited={1}

Step 1: pop 1, process 1
        push unvisited neighbours 3,2 → stack=[3,2]
        visited={1,2,3}

Step 2: pop 2, process 2
        push unvisited neighbours 4 → stack=[3,4]
        visited={1,2,3,4}

Step 3: pop 4, process 4
        push unvisited neighbours 5 → stack=[3,5]
        visited={1,2,3,4,5}

Step 4: pop 5, process 5
        no unvisited neighbours → stack=[3]

Step 5: pop 3, process 3
        no unvisited neighbours → stack=[]

Done. Order: 1,2,4,5,3
```

**DFS Iterative Template:**
```java
Deque<Integer> stack = new ArrayDeque<>();
Set<Integer> visited = new HashSet<>();

stack.push(start);
visited.add(start);

while (!stack.isEmpty()) {
    int node = stack.pop();
    // process node

    for (int neighbour : graph.get(node)) {
        if (!visited.contains(neighbour)) {
            visited.add(neighbour);
            stack.push(neighbour);
        }
    }
}
```

**DFS Recursive Template:**
```java
void dfs(int node, Set<Integer> visited, Map<Integer, List<Integer>> graph) {
    visited.add(node);
    // process node

    for (int neighbour : graph.get(node)) {
        if (!visited.contains(neighbour)) {
            dfs(neighbour, visited, graph);
        }
    }
}
```

---

## Key Difference — What Each Guarantees

```
BFS guarantees:        DFS guarantees:
  shortest path          visits all nodes
  level by level         memory efficient for deep graphs
  finds nearest first    good for cycle detection
```

---

## Problems Each Solves

### BFS Problems
```
Signal words: "shortest path", "minimum steps",
              "level by level", "nearest", "closest"

Problems:
  Level order traversal of tree
  Shortest path in unweighted graph
  Word ladder (minimum word transformations)
  Rotting oranges (minimum time to spread)
  Minimum steps to reach target
  Number of islands (can use either)
```

### DFS Problems
```
Signal words: "all paths", "exists a path",
              "connected components", "cycle detection",
              "topological sort"

Problems:
  All root-to-leaf paths in tree
  Path sum exists
  Number of connected components
  Detect cycle in graph
  Topological sort
  Flood fill
  Number of islands (can use either)
```

---

## On Trees vs Graphs — Important Distinction

```
Trees:
  No cycles → no visited set needed
  DFS = inorder/preorder/postorder
  BFS = level order traversal

Graphs:
  May have cycles → ALWAYS need visited set
  Without visited set → infinite loop
```

---

## How Graphs Are Represented

Two ways in Java:

**Adjacency List — most common:**
```java
Map<Integer, List<Integer>> graph = new HashMap<>();
graph.put(1, Arrays.asList(2, 3));
graph.put(2, Arrays.asList(1, 4));
graph.put(3, Arrays.asList(1, 5));
graph.put(4, Arrays.asList(2, 5));
graph.put(5, Arrays.asList(3, 4));
```

**Adjacency Matrix:**
```java
int[][] graph = new int[n][n];
graph[0][1] = 1;   // edge between 0 and 1
graph[1][0] = 1;   // undirected
```

Use adjacency list for sparse graphs (most interview problems).
Use adjacency matrix when you need O(1) edge lookup.

---

## BFS on Graph — Number of Islands

This is the most common graph problem in FSI interviews.

**"Given a 2D grid of '1's (land) and '0's (water), count the number of islands."**

```
Input:
1 1 0 0 0
1 1 0 0 0
0 0 1 0 0
0 0 0 1 1

Output: 3

Island 1: top-left 2x2 block
Island 2: center 1
Island 3: bottom-right 2
```

The grid IS the graph. Each cell is a node. Edges connect adjacent cells (up, down, left, right).

**Algorithm:**
```
For each unvisited land cell (value='1'):
  start BFS/DFS from it
  mark all connected land cells as visited
  increment island count
```

---

## Number of Islands — BFS

```java
public static int numIslands(char[][] grid) {
    if (grid == null || grid.length == 0) return 0;

    int rows = grid.length;
    int cols = grid[0].length;
    int islands = 0;

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == '1') {
                islands++;
                bfs(grid, r, c);   // mark all connected land
            }
        }
    }
    return islands;
}

private static void bfs(char[][] grid, int r, int c) {
    Queue<int[]> queue = new LinkedList<>();
    queue.offer(new int[]{r, c});
    grid[r][c] = '0';   // mark visited by sinking the land

    int[][] directions = {{0,1},{0,-1},{1,0},{-1,0}};

    while (!queue.isEmpty()) {
        int[] cell = queue.poll();
        for (int[] dir : directions) {
            int nr = cell[0] + dir[0];
            int nc = cell[1] + dir[1];
            if (nr >= 0 && nr < grid.length
                && nc >= 0 && nc < grid[0].length
                && grid[nr][nc] == '1') {
                grid[nr][nc] = '0';   // mark visited
                queue.offer(new int[]{nr, nc});
            }
        }
    }
}
```

**Why sink the land (`grid[r][c] = '0'`)?**
Instead of a separate visited set, you mark cells as visited by changing their value. Cleaner, less memory.

---

## DFS Version — Even Simpler

```java
private static void dfs(char[][] grid, int r, int c) {
    // out of bounds or water or visited
    if (r < 0 || r >= grid.length
        || c < 0 || c >= grid[0].length
        || grid[r][c] == '0') return;

    grid[r][c] = '0';   // mark visited

    dfs(grid, r+1, c);  // down
    dfs(grid, r-1, c);  // up
    dfs(grid, r, c+1);  // right
    dfs(grid, r, c-1);  // left
}
```

Four recursive calls — one per direction. Base case handles boundaries and visited cells.

---

## Complexity

```
Number of Islands:
  Time:  O(rows × cols) — visit each cell at most once
  Space: O(rows × cols) — queue/stack in worst case (all land)

BFS general:  O(V + E) — vertices + edges
DFS general:  O(V + E) — vertices + edges
```

---

## Summary Table

| | BFS | DFS |
|---|---|---|
| Data structure | Queue | Stack / Recursion |
| Order | Level by level | Deep first |
| Guarantees | Shortest path | All paths |
| Tree equivalent | Level order | Inorder/Pre/Post |
| Signal words | "shortest", "minimum steps", "nearest" | "all paths", "exists", "connected" |
| Graph needs | Visited set | Visited set |
| Tree needs | Nothing extra | Nothing extra |

---

Want to drill problems now? Suggested order:

1. **Number of islands** — write BFS and DFS versions
2. **Flood fill** — same pattern, easier
3. **Shortest path in grid** — BFS with distance tracking
4. **Connected components** — count groups

Which one?



# Working With Graphs — Grid Representation

## The Grid IS the Graph

You don't need to build an explicit graph. The grid itself is the graph.

```
grid = [
  ["1","1","1","1","0"],
  ["1","1","0","1","0"],
  ["1","1","0","0","0"],
  ["0","0","0","0","0"]
]
```

Every cell is a node. Two cells are connected if they are adjacent (up, down, left, right).

```
Cell (0,0) connects to: (0,1) and (1,0)
Cell (0,1) connects to: (0,0), (0,2), (1,1)
Cell (1,2) connects to: (0,2), (1,1), (1,3), (2,2)
```

No need to build adjacency list. Just use row and column indices.

---

## How To Access Any Cell

```java
grid[row][col]

grid[0][0] = "1"   // top-left
grid[0][4] = "0"   // top-right
grid[3][0] = "0"   // bottom-left
grid[3][4] = "0"   // bottom-right

rows = grid.length        // 4
cols = grid[0].length     // 5
```

---

## How To Get Neighbours

Four directions — up, down, left, right:

```java
int[][] directions = {
    {-1,  0},   // up    (row-1, same col)
    { 1,  0},   // down  (row+1, same col)
    { 0, -1},   // left  (same row, col-1)
    { 0,  1}    // right (same row, col+1)
};

// For cell (r, c), neighbours are:
for (int[] dir : directions) {
    int nr = r + dir[0];   // new row
    int nc = c + dir[1];   // new col
    // (nr, nc) is a neighbour
}
```

---

## Boundary Check — Critical

Not every neighbour exists. Edge and corner cells have fewer than 4 neighbours.

```
grid:
(0,0)(0,1)(0,2)
(1,0)(1,1)(1,2)
(2,0)(2,1)(2,2)

Cell (0,0) — top-left corner:
  up    = (-1, 0) → row -1 → OUT OF BOUNDS ❌
  down  = ( 1, 0) → (1,0)  → valid ✅
  left  = ( 0,-1) → col -1 → OUT OF BOUNDS ❌
  right = ( 0, 1) → (0,1)  → valid ✅

Must check before accessing grid[nr][nc]
```

**The boundary check:**
```java
if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
    // safe to access grid[nr][nc]
}
```

---

## Visited — How To Track

Two ways:

**Way 1 — Separate visited array:**
```java
boolean[][] visited = new boolean[rows][cols];

// mark visited:
visited[r][c] = true;

// check visited:
if (!visited[nr][nc])
```

**Way 2 — Modify grid in place (sink the land):**
```java
// mark visited by changing value:
grid[r][c] = "0";   // change land to water

// check visited:
if (grid[nr][nc].equals("1"))   // only process unvisited land
```

Way 2 is cleaner — no extra space. But it modifies input. Ask interviewer if that's okay.

---

## Full BFS — Number of Islands

```java
public int numIslands(char[][] grid) {
    if (grid == null || grid.length == 0) return 0;

    int rows = grid.length;
    int cols = grid[0].length;
    int islands = 0;

    int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {

            if (grid[r][c] == '1') {   // found unvisited land
                islands++;

                // BFS — explore entire island
                Queue<int[]> queue = new LinkedList<>();
                queue.offer(new int[]{r, c});
                grid[r][c] = '0';   // mark visited immediately

                while (!queue.isEmpty()) {
                    int[] cell = queue.poll();
                    int cr = cell[0];
                    int cc = cell[1];

                    for (int[] dir : directions) {
                        int nr = cr + dir[0];
                        int nc = cc + dir[1];

                        // boundary check AND unvisited land check
                        if (nr >= 0 && nr < rows
                            && nc >= 0 && nc < cols
                            && grid[nr][nc] == '1') {

                            grid[nr][nc] = '0';        // mark visited
                            queue.offer(new int[]{nr, nc});
                        }
                    }
                }
            }
        }
    }

    return islands;
}
```

---

## Full DFS — Same Problem

```java
public int numIslands(char[][] grid) {
    if (grid == null || grid.length == 0) return 0;

    int rows = grid.length;
    int cols = grid[0].length;
    int islands = 0;

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (grid[r][c] == '1') {
                islands++;
                dfs(grid, r, c);
            }
        }
    }
    return islands;
}

private void dfs(char[][] grid, int r, int c) {
    // base case — out of bounds OR water OR visited
    if (r < 0 || r >= grid.length
        || c < 0 || c >= grid[0].length
        || grid[r][c] == '0') return;

    grid[r][c] = '0';   // mark visited

    dfs(grid, r-1, c);  // up
    dfs(grid, r+1, c);  // down
    dfs(grid, r, c-1);  // left
    dfs(grid, r, c+1);  // right
}
```

---

## Trace — BFS On Your Grid

```
grid = [
  [1,1,1,1,0],
  [1,1,0,1,0],
  [1,1,0,0,0],
  [0,0,0,0,0]
]

Outer loop starts at (0,0):
  grid[0][0]='1' → islands=1, start BFS

BFS from (0,0):
  queue=[(0,0)], sink (0,0)→'0'

  poll (0,0):
    up    (-1,0) → out of bounds
    down  (1,0)  → '1' → add, sink → queue=[(1,0)]
    left  (0,-1) → out of bounds
    right (0,1)  → '1' → add, sink → queue=[(1,0),(0,1)]

  poll (1,0):
    up    (0,0) → '0' already visited
    down  (2,0) → '1' → add, sink → queue=[(0,1),(2,0)]
    left  (1,-1)→ out of bounds
    right (1,1) → '1' → add, sink → queue=[(0,1),(2,0),(1,1)]

  poll (0,1):
    up    (-1,1)→ out of bounds
    down  (1,1) → '0' already sunk
    left  (0,0) → '0' already sunk
    right (0,2) → '1' → add, sink → queue=[(2,0),(1,1),(0,2)]

  ... continues until all connected '1's are sunk

  After BFS: entire island sunk to '0'

grid after BFS:
  [0,0,0,0,0]
  [0,0,0,0,0]
  [0,0,0,0,0]
  [0,0,0,0,0]

Outer loop continues — no more '1's found
islands = 1 ✅
```

---

## The Four Things To Remember For Grid Problems

```
1. rows = grid.length, cols = grid[0].length

2. directions = {{-1,0},{1,0},{0,-1},{0,1}}
   (up, down, left, right)

3. Boundary check before accessing neighbour:
   nr >= 0 && nr < rows && nc >= 0 && nc < cols

4. Mark visited IMMEDIATELY when adding to queue
   not when polling — avoids adding same cell twice
```

---

## Why Mark Visited When Adding, Not When Polling?

```
Wrong — mark when polling:
  Cell (1,1) added to queue twice by (0,1) and (1,0)
  Queue = [..., (1,1), ..., (1,1)]
  Processed twice → wrong count, infinite loops possible

Correct — mark when adding:
  First time (1,1) is found → mark '0', add to queue
  Second time (1,1) is found → already '0' → skip
  Added exactly once ✅
```

---

Now write both BFS and DFS versions of number of islands. Post when ready.


Here's the full solution with every single line explained.

---

## Full Solution — Every Line Explained

```java
public int numIslands(char[][] grid) {
    
    // ── GUARD ──────────────────────────────────────────────
    if (grid == null || grid.length == 0) return 0;
    
    int rows = grid.length;       // number of rows = 4
    int cols = grid[0].length;    // number of cols = 5
    int islands = 0;              // answer counter, starts at 0
    
    // ── OUTER LOOP — scan every cell ───────────────────────
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            
            // found unvisited land → new island
            if (grid[r][c] == '1') {
                
                islands++;   // count it immediately
                
                // ── BFS — mark entire island as visited ────
                
                // Step 1: create queue, add starting cell
                Queue<int[]> queue = new LinkedList<>();
                queue.offer(new int[]{r, c});
                
                // Step 2: mark starting cell visited immediately
                grid[r][c] = '0';
                
                // Step 3: keep spreading until no more land
                while (!queue.isEmpty()) {
                    
                    // take next cell to spread from
                    int[] cell = queue.poll();
                    int cr = cell[0];   // current row
                    int cc = cell[1];   // current col
                    
                    // check all 4 neighbours
                    
                    // UP
                    if (cr - 1 >= 0                   // not out of bounds
                        && grid[cr-1][cc] == '1') {   // is unvisited land
                        queue.offer(new int[]{cr-1, cc}); // add to queue
                        grid[cr-1][cc] = '0';             // mark visited
                    }
                    
                    // DOWN
                    if (cr + 1 < rows
                        && grid[cr+1][cc] == '1') {
                        queue.offer(new int[]{cr+1, cc});
                        grid[cr+1][cc] = '0';
                    }
                    
                    // LEFT
                    if (cc - 1 >= 0
                        && grid[cr][cc-1] == '1') {
                        queue.offer(new int[]{cr, cc-1});
                        grid[cr][cc-1] = '0';
                    }
                    
                    // RIGHT
                    if (cc + 1 < cols
                        && grid[cr][cc+1] == '1') {
                        queue.offer(new int[]{cr, cc+1});
                        grid[cr][cc+1] = '0';
                    }
                }
                // BFS done — entire island marked '0'
            }
        }
    }
    
    return islands;
}
```

---

## Trace — Your Grid Step By Step

```
grid = [
  ['1','1','0'],
  ['1','0','0'],
  ['0','0','1']
]

islands = 0
```

---

**Outer loop r=0, c=0:**
```
grid[0][0] = '1' → found land!
islands = 1
queue = [(0,0)]
grid[0][0] = '0'   ← mark visited immediately

grid now:
0  1  0
1  0  0
0  0  1
```

**BFS starts — queue not empty:**
```
poll (0,0) → cr=0, cc=0

  UP:    cr-1 = -1 → out of bounds → skip
  DOWN:  cr+1 = 1, grid[1][0]='1' → offer(1,0), sink
  LEFT:  cc-1 = -1 → out of bounds → skip
  RIGHT: cc+1 = 1, grid[0][1]='1' → offer(0,1), sink

queue = [(1,0), (0,1)]

grid now:
0  0  0
0  0  0
0  0  1
```

**BFS continues — queue not empty:**
```
poll (1,0) → cr=1, cc=0

  UP:    grid[0][0]='0' → skip
  DOWN:  grid[2][0]='0' → skip
  LEFT:  cc-1=-1 → out of bounds → skip
  RIGHT: grid[1][1]='0' → skip

queue = [(0,1)]
```

**BFS continues — queue not empty:**
```
poll (0,1) → cr=0, cc=1

  UP:    cr-1=-1 → out of bounds → skip
  DOWN:  grid[1][1]='0' → skip
  LEFT:  grid[0][0]='0' → skip
  RIGHT: grid[0][2]='0' → skip

queue = []  ← empty!
```

**BFS done. Island 1 fully explored.**

---

**Outer loop continues:**
```
r=0, c=1 → grid[0][1]='0' → skip
r=0, c=2 → grid[0][2]='0' → skip
r=1, c=0 → grid[1][0]='0' → skip
r=1, c=1 → grid[1][1]='0' → skip
r=1, c=2 → grid[1][2]='0' → skip
r=2, c=0 → grid[2][0]='0' → skip
r=2, c=1 → grid[2][1]='0' → skip
r=2, c=2 → grid[2][2]='1' → found land!
```

**r=2, c=2:**
```
islands = 2
queue = [(2,2)]
grid[2][2] = '0'

grid now:
0  0  0
0  0  0
0  0  0
```

**BFS starts:**
```
poll (2,2) → cr=2, cc=2

  UP:    grid[1][2]='0' → skip
  DOWN:  cr+1=3 → out of bounds → skip
  LEFT:  grid[2][1]='0' → skip
  RIGHT: cc+1=3 → out of bounds → skip

queue = []  ← empty immediately
```

**BFS done. Island 2 fully explored.**

---

**Outer loop ends. Return islands = 2 ✅**

---

## The Six Things That Happen For Each Island

```
1. Outer loop finds '1'          → islands++
2. Add starting cell to queue    → queue.offer(new int[]{r,c})
3. Sink starting cell            → grid[r][c]='0'
4. Poll cell from queue          → int[] cell = queue.poll()
5. Check 4 neighbours:
     if valid AND '1':
       add to queue              → queue.offer(new int[]{nr,nc})
       sink immediately          → grid[nr][nc]='0'
6. Repeat 4-5 until queue empty  → entire island explored
```

---

## Now Write It From Scratch

```java
public int numIslands(char[][] grid) {

}
```

Don't look at the solution above. Just follow the six steps. Post when ready.


# BFS — Universal Template & Problem Types

## The One Template

```java
// ─── TREE BFS ──────────────────────────────────────────
Queue<TreeNode> queue = new LinkedList<>();
queue.offer(root);

while (!queue.isEmpty()) {
    int levelSize = queue.size();        // only for level-by-level problems

    for (int i = 0; i < levelSize; i++) {
        TreeNode node = queue.poll();
        // process node

        if (node.left  != null) queue.offer(node.left);
        if (node.right != null) queue.offer(node.right);
    }
}

// ─── GRAPH BFS ─────────────────────────────────────────
Queue<Integer> queue = new LinkedList<>();
Set<Integer> visited = new HashSet<>();
queue.offer(start);
visited.add(start);

while (!queue.isEmpty()) {
    int node = queue.poll();
    // process node

    for (int neighbour : graph.get(node)) {
        if (!visited.contains(neighbour)) {
            visited.add(neighbour);
            queue.offer(neighbour);
        }
    }
}

// ─── GRID BFS ──────────────────────────────────────────
int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
Queue<int[]> queue = new LinkedList<>();
queue.offer(new int[]{startR, startC});
grid[startR][startC] = '0';              // mark visited by sinking
                                         // OR use boolean[][] visited

while (!queue.isEmpty()) {
    int[] cell = queue.poll();
    int cr = cell[0], cc = cell[1];
    // process cell

    for (int[] dir : dirs) {
        int nr = cr + dir[0];
        int nc = cc + dir[1];
        if (nr >= 0 && nr < rows          // in bounds
            && nc >= 0 && nc < cols       // in bounds
            && grid[nr][nc] == '1') {     // valid unvisited cell
            queue.offer(new int[]{nr, nc});
            grid[nr][nc] = '0';           // mark visited immediately
        }
    }
}
```

---

## The Five Steps — Every BFS Problem

```
Step 1: CREATE queue, add starting point
Step 2: MARK starting point visited
Step 3: LOOP while queue not empty
Step 4:   POLL next item
Step 5:   ADD valid unvisited neighbours → mark visited immediately
```

Never changes. Only what counts as "valid" changes per problem.

---

## What Changes Per Problem

```
Problem                  Valid neighbour condition
──────────────────────── ──────────────────────────────────────
Number of islands        grid[nr][nc] == '1'
Rotting oranges          grid[nr][nc] == '1' (fresh orange)
Shortest path            grid[nr][nc] != '#' (not a wall)
Word ladder              differs by exactly 1 character
Level order tree         node.left/right != null
Walls and gates          grid[nr][nc] == INF (empty room)
01 matrix                grid[nr][nc] == 1 (not yet visited)
```

---

## What Gets Added To Queue

```
Problem type             What you store in queue
──────────────────────── ──────────────────────────────────────
Grid — just visit        int[]{row, col}
Grid — track distance    int[]{row, col, distance}
Grid — track path        int[]{row, col, pathLength}
Graph — just visit       node id (Integer)
Graph — shortest path    int[]{nodeId, distance}
Tree — level order       TreeNode
Tree — with level info   new int[]{val, level}
Word ladder              String (current word)
```

---

## Distance Tracking — Common Variation

When problem asks "minimum steps" or "shortest path":

```java
// store distance in queue
Queue<int[]> queue = new LinkedList<>();
queue.offer(new int[]{startR, startC, 0});   // {row, col, distance}
grid[startR][startC] = '0';

while (!queue.isEmpty()) {
    int[] cell = queue.poll();
    int cr = cell[0], cc = cell[1], dist = cell[2];

    if (cr == targetR && cc == targetC) return dist;  // found target

    for (int[] dir : dirs) {
        int nr = cr + dir[0];
        int nc = cc + dir[1];
        if (nr>=0 && nr<rows && nc>=0 && nc<cols && grid[nr][nc]=='1') {
            queue.offer(new int[]{nr, nc, dist+1});   // distance+1
            grid[nr][nc] = '0';
        }
    }
}
```

---

## Level Tracking — Tree Variation

When problem asks "return nodes level by level" or "right side view":

```java
Queue<TreeNode> queue = new LinkedList<>();
queue.offer(root);

while (!queue.isEmpty()) {
    int levelSize = queue.size();        // snapshot THIS level's count
    List<Integer> level = new ArrayList<>();

    for (int i = 0; i < levelSize; i++) {
        TreeNode node = queue.poll();
        level.add(node.val);             // process current level

        if (node.left  != null) queue.offer(node.left);
        if (node.right != null) queue.offer(node.right);
    }
    result.add(level);                   // save this level
}
```

**The `levelSize` snapshot is the key** — it freezes how many nodes are at the current level before you start adding the next level's nodes.

---

## Multi-Source BFS — Important Variation

Sometimes you start BFS from multiple points simultaneously. Add ALL starting points to queue before the loop.

```java
// Example: rotting oranges
// All rotten oranges spread simultaneously

for (int r = 0; r < rows; r++)
    for (int c = 0; c < cols; c++)
        if (grid[r][c] == 2)              // rotten orange
            queue.offer(new int[]{r, c}); // add ALL at once

// then BFS spreads from all simultaneously
while (!queue.isEmpty()) { ... }
```

Single source BFS → one starting point.
Multi source BFS → multiple starting points added upfront.

---

## Signal Words → Which BFS Variation

```
"level by level"           → tree BFS + levelSize snapshot
"right/left side view"     → tree BFS + levelSize, take last/first
"minimum steps/distance"   → grid BFS + distance in queue
"shortest path"            → grid/graph BFS + distance tracking
"number of islands"        → grid BFS, count how many times you start
"rotting oranges"          → multi-source grid BFS + time tracking
"walls and gates"          → multi-source grid BFS
"word ladder"              → graph BFS, words as nodes
"connected components"     → graph BFS, count starts
"nearest X"                → BFS from X outward
```

---

## The Three Mistakes To Avoid

```
1. Mark visited when POLLING instead of when ADDING
   → same cell added multiple times → wrong answer or infinite loop
   → always mark when adding to queue

2. Missing boundary check
   → nr>=0 && nr<rows && nc>=0 && nc<cols
   → always check before accessing grid[nr][nc]

3. Missing levelSize snapshot in level-order problems
   → queue.size() changes as you add next level's nodes
   → snapshot at start of each level: int levelSize = queue.size()
```

---

## Quick Reference Card

```
GRID BFS skeleton:
──────────────────
dirs = {{-1,0},{1,0},{0,-1},{0,1}}
queue.offer(start), mark visited
while not empty:
  poll cell
  for each dir:
    nr,nc = cell + dir
    if valid and unvisited:
      offer(nr,nc), mark visited

TREE BFS skeleton:
──────────────────
queue.offer(root)
while not empty:
  levelSize = queue.size()      ← only if level-by-level
  for i in levelSize:
    node = poll
    process node
    offer left, right if not null

GRAPH BFS skeleton:
───────────────────
visited.add(start), queue.offer(start)
while not empty:
  node = poll
  for each neighbour:
    if not visited:
      visited.add, queue.offer
```

---

Want to drill problems using this template?

Suggested order:
1. **Rotting Oranges** — multi source BFS
2. **01 Matrix** — distance tracking
3. **Word Ladder** — graph BFS with strings
4. **Binary Tree Right Side View** — level BFS variation

Which one?


Here's the full solution with every single line explained.

---

## Full Solution — Every Line Explained

```java
public int numIslands(char[][] grid) {
    
    // ── GUARD ──────────────────────────────────────────────
    if (grid == null || grid.length == 0) return 0;
    
    int rows = grid.length;       // number of rows = 4
    int cols = grid[0].length;    // number of cols = 5
    int islands = 0;              // answer counter, starts at 0
    
    // ── OUTER LOOP — scan every cell ───────────────────────
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            
            // found unvisited land → new island
            if (grid[r][c] == '1') {
                
                islands++;   // count it immediately
                
                // ── BFS — mark entire island as visited ────
                
                // Step 1: create queue, add starting cell
                Queue<int[]> queue = new LinkedList<>();
                queue.offer(new int[]{r, c});
                
                // Step 2: mark starting cell visited immediately
                grid[r][c] = '0';
                
                // Step 3: keep spreading until no more land
                while (!queue.isEmpty()) {
                    
                    // take next cell to spread from
                    int[] cell = queue.poll();
                    int cr = cell[0];   // current row
                    int cc = cell[1];   // current col
                    
                    // check all 4 neighbours
                    
                    // UP
                    if (cr - 1 >= 0                   // not out of bounds
                        && grid[cr-1][cc] == '1') {   // is unvisited land
                        queue.offer(new int[]{cr-1, cc}); // add to queue
                        grid[cr-1][cc] = '0';             // mark visited
                    }
                    
                    // DOWN
                    if (cr + 1 < rows
                        && grid[cr+1][cc] == '1') {
                        queue.offer(new int[]{cr+1, cc});
                        grid[cr+1][cc] = '0';
                    }
                    
                    // LEFT
                    if (cc - 1 >= 0
                        && grid[cr][cc-1] == '1') {
                        queue.offer(new int[]{cr, cc-1});
                        grid[cr][cc-1] = '0';
                    }
                    
                    // RIGHT
                    if (cc + 1 < cols
                        && grid[cr][cc+1] == '1') {
                        queue.offer(new int[]{cr, cc+1});
                        grid[cr][cc+1] = '0';
                    }
                }
                // BFS done — entire island marked '0'
            }
        }
    }
    
    return islands;
}
```

---

## Trace — Your Grid Step By Step

```
grid = [
  ['1','1','0'],
  ['1','0','0'],
  ['0','0','1']
]

islands = 0
```

---

**Outer loop r=0, c=0:**
```
grid[0][0] = '1' → found land!
islands = 1
queue = [(0,0)]
grid[0][0] = '0'   ← mark visited immediately

grid now:
0  1  0
1  0  0
0  0  1
```

**BFS starts — queue not empty:**
```
poll (0,0) → cr=0, cc=0

  UP:    cr-1 = -1 → out of bounds → skip
  DOWN:  cr+1 = 1, grid[1][0]='1' → offer(1,0), sink
  LEFT:  cc-1 = -1 → out of bounds → skip
  RIGHT: cc+1 = 1, grid[0][1]='1' → offer(0,1), sink

queue = [(1,0), (0,1)]

grid now:
0  0  0
0  0  0
0  0  1
```

**BFS continues — queue not empty:**
```
poll (1,0) → cr=1, cc=0

  UP:    grid[0][0]='0' → skip
  DOWN:  grid[2][0]='0' → skip
  LEFT:  cc-1=-1 → out of bounds → skip
  RIGHT: grid[1][1]='0' → skip

queue = [(0,1)]
```

**BFS continues — queue not empty:**
```
poll (0,1) → cr=0, cc=1

  UP:    cr-1=-1 → out of bounds → skip
  DOWN:  grid[1][1]='0' → skip
  LEFT:  grid[0][0]='0' → skip
  RIGHT: grid[0][2]='0' → skip

queue = []  ← empty!
```

**BFS done. Island 1 fully explored.**

---

**Outer loop continues:**
```
r=0, c=1 → grid[0][1]='0' → skip
r=0, c=2 → grid[0][2]='0' → skip
r=1, c=0 → grid[1][0]='0' → skip
r=1, c=1 → grid[1][1]='0' → skip
r=1, c=2 → grid[1][2]='0' → skip
r=2, c=0 → grid[2][0]='0' → skip
r=2, c=1 → grid[2][1]='0' → skip
r=2, c=2 → grid[2][2]='1' → found land!
```

**r=2, c=2:**
```
islands = 2
queue = [(2,2)]
grid[2][2] = '0'

grid now:
0  0  0
0  0  0
0  0  0
```

**BFS starts:**
```
poll (2,2) → cr=2, cc=2

  UP:    grid[1][2]='0' → skip
  DOWN:  cr+1=3 → out of bounds → skip
  LEFT:  grid[2][1]='0' → skip
  RIGHT: cc+1=3 → out of bounds → skip

queue = []  ← empty immediately
```

**BFS done. Island 2 fully explored.**

---

**Outer loop ends. Return islands = 2 ✅**

---

## The Six Things That Happen For Each Island

```
1. Outer loop finds '1'          → islands++
2. Add starting cell to queue    → queue.offer(new int[]{r,c})
3. Sink starting cell            → grid[r][c]='0'
4. Poll cell from queue          → int[] cell = queue.poll()
5. Check 4 neighbours:
     if valid AND '1':
       add to queue              → queue.offer(new int[]{nr,nc})
       sink immediately          → grid[nr][nc]='0'
6. Repeat 4-5 until queue empty  → entire island explored
```

---

## Now Write It From Scratch

```java
public int numIslands(char[][] grid) {

}
```

Don't look at the solution above. Just follow the six steps. Post when ready.


# Surrounded Regions

## The Problem

**"Given an `m x n` board containing `'X'` and `'O'`, capture all regions surrounded by `'X'`. A region is captured by flipping all `'O'`s into `'X'`s in that surrounded region."**

```
Input:
X X X X
X O O X
X X O X
X O X X

Output:
X X X X
X X X X
X X X X
X O X X
```

**A region is NOT captured if any `'O'` in it touches the border.**

---

## Before You Code — Understand The Rule

```
Input:
X X X X
X O O X
X X O X
X O X X

Which O's are surrounded?
  (1,1),(1,2),(2,2) → connected group
                    → do any touch border? NO → surrounded → flip to X

Which O's are NOT surrounded?
  (3,1) → touches bottom border → NOT surrounded → keep as O
```

---

## The Key Insight

Instead of finding surrounded regions directly — find the ones that are NOT surrounded and protect them.

```
NOT surrounded = connected to border O

So:
Step 1: find all O's on the border
Step 2: BFS/DFS from each border O — mark all connected O's as safe
Step 3: flip remaining O's to X (they're surrounded)
Step 4: restore safe O's back to O
```

---

## Visual Walk Through

```
Input:
X X X X
X O O X
X X O X
X O X X

Step 1 — find border O's:
  row 0: no O's
  row 3: (3,1) is O → border O
  col 0: no O's
  col 3: no O's

Step 2 — BFS from (3,1), mark connected O's safe:
  (3,1) → mark safe ('S')
  neighbours: up=(2,1)='X' skip, others out of bounds or X
  only (3,1) is safe

grid after Step 2:
X X X X
X O O X
X X O X
X S X X

Step 3 — flip remaining O's to X:
X X X X
X X X X
X X X X
X S X X

Step 4 — restore S back to O:
X X X X
X X X X
X X X X
X O X X  ✅
```

---

## Now Write It

Four steps translate directly to code:

```java
public void solve(char[][] board) {
    // step 1 and 2: BFS from all border O's
    // step 3: flip remaining O's to X
    // step 4: restore S to O
}

private void bfs(char[][] board, int r, int c) {
    // standard grid BFS
    // mark O's as 'S' (safe)
}
```

**Things to think through:**
- Which cells are border cells?
- What is the BFS valid neighbour condition?
- What two passes do you need after BFS?

Post your solution when ready.