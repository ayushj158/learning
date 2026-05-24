## Java Streams — Complete Guide

Let me teach this through real examples mapped to your JD context — trading, portfolios, risk.

---

## What is a Stream?

A pipeline to process collections without writing loops.

```java
// old way — loop
List<String> result = new ArrayList<>();
for (String s : list) {
    if (s.startsWith("A")) result.add(s.toUpperCase());
}

// stream way
List<String> result = list.stream()
    .filter(s -> s.startsWith("A"))
    .map(String::toUpperCase)
    .collect(Collectors.toList());
```

Same result — cleaner, composable, can be parallelized.

---

## Stream Pipeline — 3 parts

```
source → intermediate operations → terminal operation

list.stream()          // source
    .filter(...)       // intermediate — lazy, not executed yet
    .map(...)          // intermediate — lazy
    .collect(...)      // terminal — triggers execution
```

**Lazy evaluation** — nothing runs until terminal operation is called.

---

## Core Operations — with trading examples

### filter() — keep elements matching condition

```java
List<Order> orders = getOrders();

// get only BUY orders
List<Order> buyOrders = orders.stream()
    .filter(o -> o.getType() == OrderType.BUY)
    .collect(Collectors.toList());

// orders above 10000 value
List<Order> largeOrders = orders.stream()
    .filter(o -> o.getQty() * o.getPrice() > 10000)
    .collect(Collectors.toList());
```

---

### map() — transform each element

```java
// get just the symbols from orders
List<String> symbols = orders.stream()
    .map(Order::getSymbol)           // method reference
    .collect(Collectors.toList());

// calculate order values
List<Double> values = orders.stream()
    .map(o -> o.getQty() * o.getPrice())
    .collect(Collectors.toList());
```

---

### reduce() — collapse to single value

```java
// total value of all orders
double totalValue = orders.stream()
    .mapToDouble(o -> o.getQty() * o.getPrice())
    .sum();  // shortcut for reduce on numbers

// same with reduce explicitly
double totalValue = orders.stream()
    .map(o -> o.getQty() * o.getPrice())
    .reduce(0.0, Double::sum);
//           ↑           ↑
//        identity    accumulator
```

---

### sorted() — sort elements

```java
// sort orders by price descending
List<Order> sorted = orders.stream()
    .sorted((a, b) -> Double.compare(b.getPrice(), a.getPrice()))
    .collect(Collectors.toList());

// or using Comparator
List<Order> sorted = orders.stream()
    .sorted(Comparator.comparingDouble(Order::getPrice).reversed())
    .collect(Collectors.toList());
```

---

### distinct() / limit() / skip()

```java
// unique symbols traded
List<String> uniqueSymbols = orders.stream()
    .map(Order::getSymbol)
    .distinct()
    .collect(Collectors.toList());

// top 5 largest orders
List<Order> top5 = orders.stream()
    .sorted(Comparator.comparingDouble(
        (Order o) -> o.getQty() * o.getPrice()).reversed())
    .limit(5)
    .collect(Collectors.toList());
```

---

### flatMap() — flatten nested collections

```java
// each desk has list of orders, you want all orders flat
List<Desk> desks = getDesks();

List<Order> allOrders = desks.stream()
    .flatMap(desk -> desk.getOrders().stream())  // List<List<Order>> → List<Order>
    .collect(Collectors.toList());
```

---

## Collectors — terminal collectors

### toList() / toSet()

```java
List<String> symbols = orders.stream()
    .map(Order::getSymbol)
    .collect(Collectors.toList());

Set<String> uniqueSymbols = orders.stream()
    .map(Order::getSymbol)
    .collect(Collectors.toSet());
```

---

### groupingBy() — group into map

```java
// group orders by symbol
Map<String, List<Order>> bySymbol = orders.stream()
    .collect(Collectors.groupingBy(Order::getSymbol));

// group orders by type, count each
Map<OrderType, Long> countByType = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getType,
        Collectors.counting()));

// group by symbol, sum total value per symbol
Map<String, Double> valueBySymbol = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getSymbol,
        Collectors.summingDouble(o -> o.getQty() * o.getPrice())));
```

---

### toMap() — collect into map

```java
// symbol → latest price map
Map<String, Double> priceMap = stocks.stream()
    .collect(Collectors.toMap(
        Stock::getSymbol,    // key
        Stock::getPrice));   // value

// handle duplicate keys
Map<String, Double> priceMap = stocks.stream()
    .collect(Collectors.toMap(
        Stock::getSymbol,
        Stock::getPrice,
        (existing, replacement) -> replacement));  // merge function
```

---

### joining() — concatenate strings

```java
// comma separated symbols
String symbols = orders.stream()
    .map(Order::getSymbol)
    .distinct()
    .collect(Collectors.joining(", "));
// → "AAPL, GOOG, MSFT"

// with prefix/suffix
String result = orders.stream()
    .map(Order::getSymbol)
    .collect(Collectors.joining(", ", "[", "]"));
// → "[AAPL, GOOG, MSFT]"
```

---

### partitioningBy() — split into two groups

```java
// partition into profitable and loss-making positions
Map<Boolean, List<Position>> partitioned = positions.stream()
    .collect(Collectors.partitioningBy(
        p -> p.getUnrealisedPnL() > 0));

List<Position> profitable = partitioned.get(true);
List<Position> losses     = partitioned.get(false);
```

---

## Numeric Streams — mapToInt/mapToDouble/mapToLong

```java
// avoid boxing overhead for primitives
int totalQty = orders.stream()
    .mapToInt(Order::getQty)
    .sum();

double avgPrice = orders.stream()
    .mapToDouble(Order::getPrice)
    .average()
    .orElse(0.0);

OptionalDouble maxPrice = orders.stream()
    .mapToDouble(Order::getPrice)
    .max();
```

---

## findFirst() / anyMatch() / allMatch() / noneMatch()

```java
// find first BUY order
Optional<Order> firstBuy = orders.stream()
    .filter(o -> o.getType() == OrderType.BUY)
    .findFirst();

firstBuy.ifPresent(o -> System.out.println(o.getSymbol()));

// any order above 10k?
boolean hasLargeOrder = orders.stream()
    .anyMatch(o -> o.getQty() * o.getPrice() > 10000);

// all orders filled?
boolean allFilled = orders.stream()
    .allMatch(o -> o.getStatus() == OrderStatus.FILLED);
```

---

## Parallel Streams

```java
// just add .parallel() — uses ForkJoinPool internally
double totalValue = orders.parallelStream()
    .mapToDouble(o -> o.getQty() * o.getPrice())
    .sum();
```

When to use:
- Large collections (10k+ elements)
- CPU-intensive operations
- No shared mutable state

When NOT to use:
- Small collections — overhead exceeds benefit
- Order matters — parallel doesn't guarantee order
- Shared state — race conditions

---

## Common GS interview stream questions

### Q1 — Group orders by symbol and find total qty per symbol

```java
Map<String, Integer> qtyBySymbol = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getSymbol,
        Collectors.summingInt(Order::getQty)));
```

### Q2 — Find top 3 most traded symbols

```java
List<String> top3 = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getSymbol, Collectors.counting()))
    .entrySet().stream()
    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
    .limit(3)
    .map(Map.Entry::getKey)
    .collect(Collectors.toList());
```

### Q3 — Flatten and deduplicate

```java
List<String> allSymbols = desks.stream()
    .flatMap(d -> d.getOrders().stream())
    .map(Order::getSymbol)
    .distinct()
    .sorted()
    .collect(Collectors.toList());
```

### Q4 — Your frequency map from earlier — stream version

```java
// instead of loop
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);

// stream version
Map<Character, Long> freq = s.chars()
    .mapToObj(c -> (char) c)
    .collect(Collectors.groupingBy(
        c -> c, Collectors.counting()));
```

---

## Quick reference card

| Operation | Type | Purpose |
|---|---|---|
| `filter()` | Intermediate | Keep matching elements |
| `map()` | Intermediate | Transform elements |
| `flatMap()` | Intermediate | Flatten nested collections |
| `sorted()` | Intermediate | Sort elements |
| `distinct()` | Intermediate | Remove duplicates |
| `limit()` | Intermediate | Take first N |
| `skip()` | Intermediate | Skip first N |
| `reduce()` | Terminal | Collapse to single value |
| `collect()` | Terminal | Gather into collection |
| `findFirst()` | Terminal | First matching element |
| `anyMatch()` | Terminal | Any element matches? |
| `count()` | Terminal | Count elements |
| `forEach()` | Terminal | Iterate elements |

---

Ready for **CompletableFuture** next?


Share the code you're referring to — looks like it didn't come through!

And yes — lambdas over method references is completely fine in interviews. Interviewers care about correctness not syntax style.

---

## Stream Practice Problems

Here are 8 problems — increasing difficulty. All based on trading context.

---

### Setup — use these classes for all problems

```java
class Order {
    String orderId;
    String symbol;
    String userId;
    OrderType type;      // BUY or SELL
    OrderStatus status;  // FILLED, PENDING, CANCELLED
    int qty;
    double price;
    
    // getters for all fields
}

List<Order> orders = getOrders(); // assume this exists
```

---

### Problem 1 — Easy
Get all FILLED orders as a list.

---

### Problem 2 — Easy
Get total number of orders for symbol "AAPL".

---

### Problem 3 — Easy
Get list of all unique userIds who placed orders.

---

### Problem 4 — Medium
Get total value (qty * price) of all BUY orders.

---

### Problem 5 — Medium
Group orders by symbol — return `Map<String, List<Order>>`.

---

### Problem 6 — Medium
For each symbol, find total qty traded — return `Map<String, Integer>`.

---

### Problem 7 — Hard
Find top 3 users by total order value — return `List<String>` of userIds.

---

### Problem 8 — Hard
Find the symbol with highest number of CANCELLED orders.

---

Try them one by one — paste your answers and I'll review. Start with Problem 1.


Really solid — you've got the core patterns down cold. A few things to flag:

---

## Bug 1 — summingInt with multiplication returns wrong type

```java
Collectors.summingInt(o -> o.price * o.qty)
```

`price` and `qty` are both `int` — multiplication can overflow int range silently for large values. Use `summingLong`:

```java
Collectors.summingLong(o -> (long) o.price * o.qty)
```

---

## Bug 2 — mapToLong loses decimal precision

```java
long value = orders.stream()
    .mapToLong(o -> o.qty * o.price)  // int * int = int, then cast to long
    .sum();
```

If price is ever a `double` (real trading — always is), use `mapToDouble`:

```java
double value = orders.stream()
    .mapToDouble(o -> o.qty * o.price)
    .sum();
```

---

## Bug 3 — Q8 Comparator type inference issue

```java
.sorted(Comparator.comparingInt(
    (Map.Entry<Integer,Integer> o) -> o.getValue()).reversed())
```

Works but fragile — `summingInt` returns `Integer` so comparison is fine here. But if you switch to `summingLong` the type breaks. Safer pattern:

```java
.sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
```

Explicit, no type inference issues, always works.

---

#IMP::: Thing to remember — Collectors.mapping()

You used it correctly in Q6':

```java
Collectors.groupingBy(o -> o.symbol,
    Collectors.mapping(o -> o.userId, Collectors.toList()))
```

This is a **downstream collector** — very commonly asked at senior level. Pattern is:

```
groupingBy(classifier, downstream)

downstream can be:
- counting()
- summingInt/Long/Double()
- mapping(transformer, collector)  ← transform then collect
- joining()
- toList()
```

---

#IMP::: One missing pattern — chained stream on entrySet

Your Q2 is correct but note this pattern cold:

```java
map.entrySet().stream()           // stream the map entries
   .sorted(...)                   // sort by value
   .limit(n)                      // take top n
   .map(e -> e.getKey())          // extract keys
   .collect(Collectors.toList())  // collect
```

This comes up constantly — top N by value questions.

---

## Overall

✅ groupingBy + downstream collectors — solid
✅ flatMap + distinct — solid
✅ chained streams on maps — solid
✅ chars() + mapToObj for strings — solid
✅ Collectors.mapping() — good catch

**One thing to practice** — `Optional` handling after `findFirst()`:

```java
Optional<Order> first = orders.stream()
    .filter(o -> o.symbol.equals("AAPL"))
    .findFirst();

// three ways to handle
first.ifPresent(o -> System.out.println(o.orderId));     // if present do something
String id = first.map(o -> o.orderId).orElse("none");    // transform or default
Order o = first.orElseThrow(() -> new RuntimeException("Not found")); // or throw
```

---

Streams ✅ done. **CompletableFuture next?**



# Primitive vs Object Streams 


Here's the full mental model to go with the widget:

**The core rule**: Java has two parallel stream worlds — *primitive streams* (`IntStream`, `LongStream`, `DoubleStream`) and *object streams* (`Stream<T>`). The friction points are always at the boundary between them.

**For `int[]` arrays**, `Arrays.stream(arr)` gives you an `IntStream` directly — no boxing, and you get `.sum()`, `.min()`, `.max()`, `.average()` for free. The moment you need a `List` or any `Collector`, you pay the boxing cost via `.boxed()`.

**For `char[]`**, Java has no `CharStream`. The standard path is `new String(chars).chars()` which gives you an `IntStream` of Unicode code points (chars as ints). If you need `Stream<Character>`, use `.mapToObj(c -> (char) c)` — but that boxes every character into a `Character` object.

**The `.mapToInt()` trick** is crucial when going the other direction — you have a `List<T>` and want a sum of some field. Instead of `.map()` then reducing, `.mapToInt(T::getField).sum()` unboxes cleanly and uses the optimized primitive path.

**`reduce` vs `collect`**: `reduce` folds into a single value and works on primitive streams natively. `collect` mutates a container (List, Map, StringBuilder) and requires an object stream — except the 3-argument form of `collect` on `IntStream`, which lets you build a `StringBuilder` without boxing, great for filtered string reconstruction.