## Trie — From Scratch

---

## What is a Trie?

A tree where each node represents one character. Used to store and search strings efficiently.

```
Insert: "cat", "car", "card", "care", "bat"

         root
        /    \
       c      b
       |      |
       a      a
      / \     |
     t   r    t
         |
         d/e
```

Every path from root to a marked node = a word.

---

## Why not HashMap for string search?

```
HashMap: search "cat" → O(1) exact match only
Trie:    search "cat" → O(k) where k=length
         BUT also supports:
         - prefix search: "all words starting with ca"
         - autocomplete
         - count words with prefix
         HashMap can't do these efficiently
```

---

## Node structure

```java
class TrieNode {
    Map<Character, TrieNode> children = new HashMap<>();
    boolean isEndOfWord = false;
}
```

Each node:
- `children` — map of next characters
- `isEndOfWord` — marks if a complete word ends here

---

## Three operations

```
insert("cat"):
  root → c → a → t (mark end)

search("cat"):
  root → c → a → t → isEndOfWord? YES ✓

search("ca"):
  root → c → a → isEndOfWord? NO ✗ (prefix not word)

startsWith("ca"):
  root → c → a → exists? YES ✓ (prefix check)
```

---

## Code it — LC 208

```java
class TrieNode {
    Map<Character, TrieNode> children = new HashMap<>();
    boolean isEndOfWord = false;
}

class Trie {
    private TrieNode root = new TrieNode();

    public void insert(String word) {
        // traverse char by char
        // create node if not exists
        // mark end
    }

    public boolean search(String word) {
        // traverse char by char
        // return false if char missing
        // return isEndOfWord at last char
    }

    public boolean startsWith(String prefix) {
        // same as search but return true at end
        // don't check isEndOfWord
    }
}
```

Give it a go — all three methods.


## Insert — step by step

---

## insert("cat")

Start at root. For each character, check if child exists. If not, create it. Move to that child. At end, mark isEndOfWord = true.

```
start:  node = root

char='c': root.children has 'c'? NO → create new node
          root.children.put('c', new TrieNode())
          node = root.children.get('c')

char='a': node.children has 'a'? NO → create new node
          node.children.put('a', new TrieNode())
          node = node.children.get('a')

char='t': node.children has 't'? NO → create new node
          node.children.put('t', new TrieNode())
          node = node.children.get('t')

end: node.isEndOfWord = true
```

Now insert("car"):

```
start: node = root

char='c': root.children has 'c'? YES → don't create
          node = root.children.get('c')  ← reuse existing

char='a': node.children has 'a'? YES → reuse
          node = node.children.get('a')

char='r': node.children has 'r'? NO → create
          node = node.children.get('r')

end: node.isEndOfWord = true
```

---

## Insert code

```java
public void insert(String word) {
    TrieNode node = root;
    for (char c : word.toCharArray()) {
        node.children.computeIfAbsent(c, k -> new TrieNode());
        node = node.children.get(c);
    }
    node.isEndOfWord = true;
}
```

---

## search("cat") — step by step

```
start: node = root

char='c': root.children has 'c'? YES → move
char='a': has 'a'? YES → move
char='t': has 't'? YES → move

end: node.isEndOfWord? YES → return true ✓
```

search("ca"):

```
char='c': YES → move
char='a': YES → move

end: node.isEndOfWord? NO → return false ✗
     "ca" is a prefix, not a complete word
```

search("cab"):

```
char='c': YES
char='a': YES
char='b': has 'b'? NO → return false ✗
```

---

## Search code

```java
public boolean search(String word) {
    TrieNode node = root;
    for (char c : word.toCharArray()) {
        if (!node.children.containsKey(c)) return false;
        node = node.children.get(c);
    }
    return node.isEndOfWord;  // must be complete word
}
```

---

## startsWith("ca") — only difference from search

```
char='c': YES → move
char='a': YES → move

end: don't check isEndOfWord
     just return true — prefix exists ✓
```

---

## startsWith code

```java
public boolean startsWith(String prefix) {
    TrieNode node = root;
    for (char c : prefix.toCharArray()) {
        if (!node.children.containsKey(c)) return false;
        node = node.children.get(c);
    }
    return true;  // ← only difference from search
}
```

---

## Full Trie — LC 208

```java
class TrieNode {
    Map<Character, TrieNode> children = new HashMap<>();
    boolean isEndOfWord = false;
}

class Trie {
    private final TrieNode root = new TrieNode();

    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node.children.computeIfAbsent(c, k -> new TrieNode());
            node = node.children.get(c);
        }
        node.isEndOfWord = true;
    }

    public boolean search(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (!node.children.containsKey(c)) return false;
            node = node.children.get(c);
        }
        return node.isEndOfWord;
    }

    public boolean startsWith(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            if (!node.children.containsKey(c)) return false;
            node = node.children.get(c);
        }
        return true;
    }
}
```

---

## Trace full example

```
insert("cat"), insert("car"), insert("card")

Trie after inserts:
root
 └─ c
     └─ a
         ├─ t (isEnd=true)
         └─ r (isEnd=true)
             └─ d (isEnd=true)

search("cat")    → c→a→t, isEnd=true  → true  ✓
search("ca")     → c→a,   isEnd=false → false ✓
search("card")   → c→a→r→d, isEnd=true → true ✓
search("care")   → c→a→r→e, 'e' missing → false ✓
startsWith("ca") → c→a exists → true ✓
startsWith("cb") → c→b, 'b' missing → false ✓
```

---

## Complexity

```
insert():     O(k) — k = word length
search():     O(k)
startsWith(): O(k)
Space:        O(total chars across all words)
```

---

## Common follow-ups GS asks

**Count words with prefix:**
```java
// add count field to TrieNode
class TrieNode {
    Map<Character, TrieNode> children = new HashMap<>();
    boolean isEndOfWord = false;
    int prefixCount = 0;  // ← how many words pass through
}

// in insert — increment prefixCount at each node
node.prefixCount++;
```

**Delete a word:**
```java
// recursive DFS — delete node if no children + not end of another word
// mark isEndOfWord = false at target
// remove node if leaf
```

---

Now try **LC 211 — Search with Wildcard** — same Trie but '.' matches any character. Ready?

## Print Every Character

---

## Print every character in the Trie

BFS level by level — prints every node:

```java
public void printEveryChar() {
    Queue<TrieNode> queue = new LinkedList<>();
    Queue<String> prefixes = new LinkedList<>();

    queue.offer(root);
    prefixes.offer("");

    while (!queue.isEmpty()) {
        TrieNode node = queue.poll();
        String prefix = prefixes.poll();

        // print every character stored in this node's children
        for (Map.Entry<Character, TrieNode> entry : 
             node.children.entrySet()) {
            
            char c = entry.getKey();
            TrieNode child = entry.getValue();
            String currentPath = prefix + c;

            System.out.println("char='" + c + "' path='" 
                              + currentPath + "'"
                              + (child.isEndOfWord ? " [WORD]" : ""));

            queue.offer(child);
            prefixes.offer(currentPath);
        }
    }
}
```

Output for "cat", "car", "card":
```
char='c' path='c'
char='a' path='ca'
char='t' path='cat' [WORD]
char='r' path='car' [WORD]
char='d' path='card' [WORD]
```

---

## Print every complete word

Simple DFS — only prints when isEndOfWord:

```java
public void printEveryWord() {
    printWords(root, new StringBuilder());
}

private void printWords(TrieNode node, StringBuilder current) {
    if (node.isEndOfWord) {
        System.out.println(current.toString());
    }
    for (Map.Entry<Character, TrieNode> entry : 
         node.children.entrySet()) {
        current.append(entry.getKey());
        printWords(entry.getValue(), current);
        current.deleteCharAt(current.length() - 1);
    }
}
```

Output:
```
cat
car
card
```

---

## Both together in main

```java
public static void main(String[] args) {
    Trie trie = new Trie();
    trie.insert("cat");
    trie.insert("car");
    trie.insert("card");
    trie.insert("bat");

    System.out.println("--- Every Character ---");
    trie.printEveryChar();

    System.out.println("--- Every Word ---");
    trie.printEveryWord();
}
```

Output:
```
--- Every Character ---
char='c' path='c'
char='b' path='b'
char='a' path='ca'
char='a' path='ba'
char='t' path='cat' [WORD]
char='r' path='car' [WORD]
char='t' path='bat' [WORD]
char='d' path='card' [WORD]

--- Every Word ---
cat
car
card
bat
```

---

Ready for **LC 211 — Wildcard Search**?

## Print Every Character

---

## Print every character in the Trie

BFS level by level — prints every node:

```java
public void printEveryChar() {
    Queue<TrieNode> queue = new LinkedList<>();
    Queue<String> prefixes = new LinkedList<>();

    queue.offer(root);
    prefixes.offer("");

    while (!queue.isEmpty()) {
        TrieNode node = queue.poll();
        String prefix = prefixes.poll();

        // print every character stored in this node's children
        for (Map.Entry<Character, TrieNode> entry : 
             node.children.entrySet()) {
            
            char c = entry.getKey();
            TrieNode child = entry.getValue();
            String currentPath = prefix + c;

            System.out.println("char='" + c + "' path='" 
                              + currentPath + "'"
                              + (child.isEndOfWord ? " [WORD]" : ""));

            queue.offer(child);
            prefixes.offer(currentPath);
        }
    }
}
```

Output for "cat", "car", "card":
```
char='c' path='c'
char='a' path='ca'
char='t' path='cat' [WORD]
char='r' path='car' [WORD]
char='d' path='card' [WORD]
```

---

## Print every complete word

Simple DFS — only prints when isEndOfWord:

```java
public void printEveryWord() {
    printWords(root, new StringBuilder());
}

private void printWords(TrieNode node, StringBuilder current) {
    if (node.isEndOfWord) {
        System.out.println(current.toString());
    }
    for (Map.Entry<Character, TrieNode> entry : 
         node.children.entrySet()) {
        current.append(entry.getKey());
        printWords(entry.getValue(), current);
        current.deleteCharAt(current.length() - 1);
    }
}
```

Output:
```
cat
car
card
```

---

## Both together in main

```java
public static void main(String[] args) {
    Trie trie = new Trie();
    trie.insert("cat");
    trie.insert("car");
    trie.insert("card");
    trie.insert("bat");

    System.out.println("--- Every Character ---");
    trie.printEveryChar();

    System.out.println("--- Every Word ---");
    trie.printEveryWord();
}
```

Output:
```
--- Every Character ---
char='c' path='c'
char='b' path='b'
char='a' path='ca'
char='a' path='ba'
char='t' path='cat' [WORD]
char='r' path='car' [WORD]
char='t' path='bat' [WORD]
char='d' path='card' [WORD]

--- Every Word ---
cat
car
card
bat
```

---

Ready for **LC 211 — Wildcard Search**?

## LC 211 — Search with Wildcard

> Design a data structure that supports adding words and searching for words with wildcard `.` which matches any single character.

```java
WordDictionary dict = new WordDictionary();
dict.addWord("bad");
dict.addWord("dad");
dict.addWord("mad");

dict.search("pad") → false
dict.search("bad") → true
dict.search(".ad") → true  ← '.' matches 'b','d','m'
dict.search("b..") → true  ← '..' matches 'ad'
dict.search("...") → true  ← matches any 3-char word
dict.search("b.d.") → false ← 4 chars, none inserted
```

---

## How is this different from LC 208?

```
LC 208: search exact string
        → simple traversal, char by char

LC 211: '.' matches ANY character
        → when you see '.', you must try ALL children
        → can't just follow one path
        → need DFS backtracking
```

---

## The key insight

```
Normal char 'b':
  go to children['b'] only → one path

Wildcard '.':
  go to children['b'], children['d'], children['m']...
  try ALL children → multiple paths
  if ANY path returns true → return true
```

---

## Before coding — two questions:

1. `addWord()` — same as Trie insert, no change needed. Agree?
2. `search()` — when you hit `.`, what do you do?

Take a shot at the search logic.

Good question — yes you CAN but DFS is better here. Let me explain why.

---

## BFS approach — works but complex

```
For each level of BFS:
  if char is normal → only keep nodes matching that char
  if char is '.' → keep ALL children nodes

Track all possible nodes at each level:
```

```java
public boolean search(String word) {
    Set<TrieNode> currentNodes = new HashSet<>();
    currentNodes.add(root);

    for (char c : word.toCharArray()) {
        Set<TrieNode> nextNodes = new HashSet<>();

        for (TrieNode node : currentNodes) {
            if (c == '.') {
                // add ALL children
                nextNodes.addAll(node.children.values());
            } else {
                // add only matching child
                if (node.children.containsKey(c)) {
                    nextNodes.add(node.children.get(c));
                }
            }
        }

        if (nextNodes.isEmpty()) return false;
        currentNodes = nextNodes;
    }

    // check if any node in final set is end of word
    return currentNodes.stream()
                       .anyMatch(n -> n.isEndOfWord);
}
```

---

## Trace BFS on ".ad"

```
word = ".ad"
currentNodes = {root}

char='.':
  root has children: b,d,m
  nextNodes = {b-node, d-node, m-node}

char='a':
  b-node has 'a'? YES → add ba-node
  d-node has 'a'? YES → add da-node
  m-node has 'a'? YES → add ma-node
  nextNodes = {ba-node, da-node, ma-node}

char='d':
  ba-node has 'd'? YES → add bad-node
  da-node has 'd'? YES → add dad-node
  ma-node has 'd'? YES → add mad-node
  nextNodes = {bad-node, dad-node, mad-node}

check isEndOfWord:
  bad-node.isEndOfWord = true → return true ✓
```

---

## Why DFS is better

```
BFS:
  tracks ALL possible nodes at each level
  needs HashSet of nodes → extra memory
  processes ALL paths even after finding answer
  O(n × 26^k) worst case where k = wildcards

DFS:
  goes one path at a time
  returns true IMMEDIATELY when found
  short-circuits — stops as soon as answer found
  O(26^k) worst case but stops early

For search problems → DFS wins because of early exit
```

---

## Both are valid in interview

```
BFS approach:
  ✓ intuitive — level by level
  ✓ iterative — no recursion
  ✗ no early exit
  ✗ extra memory for node sets

DFS approach:
  ✓ early exit on first match
  ✓ less memory — just call stack
  ✗ recursive
```

**Say in interview:**
*"I can solve this with BFS tracking all possible nodes at each level, or DFS which short-circuits on first match. I'll use DFS for efficiency."*

---

## DFS solution

```java
class WordDictionary {
    private TrieNode root = new TrieNode();

    public void addWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node.children.computeIfAbsent(c, k -> new TrieNode());
            node = node.children.get(c);
        }
        node.isEndOfWord = true;
    }

    public boolean search(String word) {
        return dfs(root, word, 0);
    }

    private boolean dfs(TrieNode node, String word, int index) {
        // base case — processed all chars
        if (index == word.length()) {
            return node.isEndOfWord;
        }

        char c = word.charAt(index);

        if (c == '.') {
            // try ALL children
            for (TrieNode child : node.children.values()) {
                if (dfs(child, word, index + 1)) return true;
            }
            return false;

        } else {
            // follow exact char
            if (!node.children.containsKey(c)) return false;
            return dfs(node.children.get(c), word, index + 1);
        }
    }
}
```

---

## Trace DFS on ".ad"

```
dfs(root, ".ad", 0)
  c='.' → try ALL children of root: b,d,m

  dfs(b-node, ".ad", 1)
    c='a' → b-node has 'a'? YES
    dfs(ba-node, ".ad", 2)
      c='d' → ba-node has 'd'? YES
      dfs(bad-node, ".ad", 3)
        index==length → isEndOfWord? YES → return true ✓

  → true found! return true immediately ← early exit ✓
  never even tries d-node and m-node
```

---

## BFS vs DFS — when to use in general

```
BFS:
  shortest path
  level-order traversal
  "minimum steps to reach X"

DFS:
  explore all paths
  find IF something exists
  backtracking problems
  early exit on first match ← this problem
```

---

LC 211 ✅ done. Ready for **Kill Process** next?

