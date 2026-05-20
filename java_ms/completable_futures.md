## CompletableFuture — Complete Guide

---

## Why CompletableFuture exists

Before it, async code looked like this with `Future`:

```java
Future<Double> price = executor.submit(() -> getPrice("AAPL"));
Double result = price.get();  // BLOCKS calling thread until done
// can't chain, can't combine, can't handle errors cleanly
```

`CompletableFuture` fixes all of this — non-blocking, chainable, composable.

---

## Basic creation

```java
// run async, no return value
CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
    System.out.println("fire and forget");
});

// run async, returns value
CompletableFuture<Double> cf = CompletableFuture.supplyAsync(() -> {
    return getPrice("AAPL");  // runs on ForkJoinPool by default
});

// with custom executor
ExecutorService executor = Executors.newFixedThreadPool(10);
CompletableFuture<Double> cf = CompletableFuture.supplyAsync(() -> {
    return getPrice("AAPL");
}, executor);
```

---

## thenApply() — transform result (like map)

```java
// sync transformation — runs on same thread
CompletableFuture<String> cf = CompletableFuture
    .supplyAsync(() -> getPrice("AAPL"))     // returns Double
    .thenApply(price -> "Price is: " + price); // transforms Double → String

String result = cf.get();  // "Price is: 189.50"
```

Think of it as `map()` for CompletableFuture.

---

## thenApplyAsync() — transform on different thread

```java
CompletableFuture<String> cf = CompletableFuture
    .supplyAsync(() -> getPrice("AAPL"))
    .thenApplyAsync(price -> {
        // runs on different thread from ForkJoinPool
        return formatPrice(price);
    });
```

Use `thenApplyAsync` when transformation is expensive — don't block the thread that completed the previous stage.

---

## thenCompose() — chain dependent futures (like flatMap)

```java
// wrong way — nested futures
CompletableFuture<CompletableFuture<Order>> nested =
    CompletableFuture.supplyAsync(() -> getUser("deskA"))
        .thenApply(user -> placeOrder(user));  // placeOrder returns CF<Order>
// gives CF<CF<Order>> — double wrapped!

// correct way — thenCompose flattens
CompletableFuture<Order> cf =
    CompletableFuture.supplyAsync(() -> getUser("deskA"))
        .thenCompose(user -> placeOrder(user));  // flattens CF<CF<Order>> → CF<Order>
```

Rule: 
- next step returns plain value → `thenApply`
- next step returns CompletableFuture → `thenCompose`

---

## thenAccept() — consume result, no return

```java
CompletableFuture.supplyAsync(() -> getPrice("AAPL"))
    .thenAccept(price -> {
        System.out.println("Price: " + price);  // consume, no return
    });
```

---

## thenRun() — run after completion, ignore result

```java
CompletableFuture.supplyAsync(() -> placeOrder(order))
    .thenRun(() -> {
        System.out.println("Order placed, sending notification");
        // doesn't see previous result
    });
```

---

## allOf() — wait for ALL futures

```java
// fetch prices for multiple symbols in parallel
CompletableFuture<Double> aaplPrice = 
    CompletableFuture.supplyAsync(() -> getPrice("AAPL"));
CompletableFuture<Double> googPrice = 
    CompletableFuture.supplyAsync(() -> getPrice("GOOG"));
CompletableFuture<Double> msftPrice = 
    CompletableFuture.supplyAsync(() -> getPrice("MSFT"));

// wait for ALL to complete
CompletableFuture<Void> all = CompletableFuture.allOf(
    aaplPrice, googPrice, msftPrice);

all.thenRun(() -> {
    // all three done — safe to get results
    try {
        System.out.println(aaplPrice.get());
        System.out.println(googPrice.get());
        System.out.println(msftPrice.get());
    } catch (Exception e) {
        e.printStackTrace();
    }
});
```

**Common pattern — allOf + stream:**

```java
List<String> symbols = List.of("AAPL", "GOOG", "MSFT");

List<CompletableFuture<Double>> futures = symbols.stream()
    .map(s -> CompletableFuture.supplyAsync(() -> getPrice(s)))
    .collect(Collectors.toList());

// wait for all
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenRun(() -> {
        List<Double> prices = futures.stream()
            .map(f -> f.join())  // join() like get() but unchecked exception
            .collect(Collectors.toList());
        System.out.println(prices);
    });
```

---

## anyOf() — complete when ANY future completes

```java
// get price from fastest of three sources
CompletableFuture<Object> fastest = CompletableFuture.anyOf(
    CompletableFuture.supplyAsync(() -> getPriceFromSource1("AAPL")),
    CompletableFuture.supplyAsync(() -> getPriceFromSource2("AAPL")),
    CompletableFuture.supplyAsync(() -> getPriceFromSource3("AAPL"))
);

fastest.thenAccept(price -> System.out.println("Fastest price: " + price));
```

---

## exceptionally() — error handling

```java
CompletableFuture<Double> cf = CompletableFuture
    .supplyAsync(() -> {
        if (marketClosed()) throw new RuntimeException("Market closed");
        return getPrice("AAPL");
    })
    .exceptionally(ex -> {
        System.out.println("Error: " + ex.getMessage());
        return 0.0;  // fallback value
    });
```

---

## handle() — success AND error in one place

```java
CompletableFuture<Double> cf = CompletableFuture
    .supplyAsync(() -> getPrice("AAPL"))
    .handle((price, ex) -> {
        if (ex != null) {
            System.out.println("Failed: " + ex.getMessage());
            return 0.0;  // fallback
        }
        return price;   // success
    });
```

Difference from `exceptionally`:
- `exceptionally` — only called on error
- `handle` — always called, you check if ex is null

---

## get() vs join()

```java
// get() — checked exception, must try-catch
try {
    Double price = cf.get();
    Double price = cf.get(5, TimeUnit.SECONDS);  // with timeout
} catch (InterruptedException | ExecutionException e) {
    e.printStackTrace();
}

// join() — unchecked exception, cleaner in streams
Double price = cf.join();  // throws CompletionException (unchecked)
```

Use `get()` when you need timeout control. Use `join()` inside streams.

---

## Real GS use case — parallel risk calculation

```java
// calculate risk for multiple positions in parallel
List<Position> positions = getPortfolio("deskA");

List<CompletableFuture<RiskScore>> riskFutures = positions.stream()
    .map(p -> CompletableFuture.supplyAsync(
        () -> riskEngine.calculate(p), executor))
    .collect(Collectors.toList());

// wait for all risk calculations
CompletableFuture.allOf(
    riskFutures.toArray(new CompletableFuture[0]))
    .thenAccept(v -> {
        double totalRisk = riskFutures.stream()
            .map(f -> f.join())
            .mapToDouble(RiskScore::getValue)
            .sum();
        System.out.println("Total portfolio risk: " + totalRisk);
    });
```

---

## Quick reference

| Method | Purpose | Returns |
|---|---|---|
| `supplyAsync()` | Start async task with result | `CF<T>` |
| `runAsync()` | Start async task no result | `CF<Void>` |
| `thenApply()` | Transform result | `CF<U>` |
| `thenApplyAsync()` | Transform on new thread | `CF<U>` |
| `thenCompose()` | Chain dependent CF | `CF<U>` |
| `thenAccept()` | Consume result | `CF<Void>` |
| `thenRun()` | Run after, ignore result | `CF<Void>` |
| `allOf()` | Wait for all | `CF<Void>` |
| `anyOf()` | Wait for first | `CF<Object>` |
| `exceptionally()` | Handle error | `CF<T>` |
| `handle()` | Handle both | `CF<T>` |
| `get()` | Block and get | `T` |
| `join()` | Block and get (unchecked) | `T` |

---

## What GS asks at VP level

They won't ask you to code CompletableFuture from scratch — they'll ask:

1. *"How would you fetch prices for 500 symbols in parallel?"* → `supplyAsync` + `allOf` + stream
2. *"What's the difference between thenApply and thenCompose?"* → map vs flatMap analogy
3. *"How do you handle errors in async chains?"* → `exceptionally` or `handle`
4. *"What thread pool does CompletableFuture use by default?"* → `ForkJoinPool.commonPool()`
5. *"When would you provide a custom executor?"* → IO-bound tasks — ForkJoinPool is for CPU-bound

---

Clear? Want practice problems or move to **Vector from scratch**?


## CompletableFuture Practice Problems

### Setup

```java
// assume these methods exist
Double getPrice(String symbol)           // takes 100ms
Order placeOrder(String userId, String symbol, int qty)  // takes 200ms
RiskScore calculateRisk(Position p)      // takes 300ms
UserProfile getUser(String userId)       // takes 150ms
boolean validateOrder(Order order)       // takes 50ms
```

---

### Problem 1 — Easy
Fetch price of "AAPL" asynchronously and print it when done. Don't block main thread.

---

### Problem 2 — Easy
Fetch price of "AAPL" asynchronously. When done, convert it to a formatted string `"AAPL: $189.50"` and print it.

---

### Problem 3 — Medium
Fetch prices of "AAPL", "GOOG", "MSFT" **in parallel**. When ALL are done, print the average price.

---

### Problem 4 — Medium
Get user profile for "deskA" — then using that user, place an order for "AAPL" qty 10. These must happen **sequentially** (need user before placing order). Return `CompletableFuture<Order>`.

---

### Problem 5 — Medium
Fetch price of "AAPL". If it fails (exception), return a default price of 0.0.

---

### Problem 6 — Hard
Given a list of 10 symbols, fetch all prices in parallel. Return a `Map<String, Double>` of symbol → price when all are done.

---

### Problem 7 — Hard
Place an order, then validate it — both async and sequential. If validation fails, throw `RuntimeException("Order invalid")`. If both succeed, return the order.

---

### Problem 8 — Hard
Fetch prices from three different sources for "AAPL" simultaneously. Return whichever comes back first.

---

Try them one by one — paste answers and I'll review.

Really strong — you've got the core patterns cold. A few things to flag:

---

## Bug 1 — Q1 may not print

```java
CompletableFuture<Void> r1 = CompletableFuture.supplyAsync(() -> getPrice("AAPL"))
    .thenAccept(a -> System.out.println(a));
// no join() — main thread may exit before async completes
```

You did `r2.join()` correctly — do the same for r1:

```java
r1.join();  // wait for completion
```

---

## Improvement — Q3 thenAccept receives Void not all results

```java
CompletableFuture.allOf(...)
    .thenAccept(all -> {  // 'all' is Void — you can't use it
        // you correctly ignored it and used prices list directly ✓
    });
```

You handled it correctly by referencing `prices` list directly inside the lambda. Good instinct.

---

## Improvement — Q6 cleaner with streams

Your for loop works perfectly — but stream version is cleaner for interview:

```java
CompletableFuture<Map<String, Double>> r6 = 
    CompletableFuture.allOf(allPrices.toArray(new CompletableFuture[0]))
        .thenApply(v -> 
            IntStream.range(0, allSymbols.size())
                .boxed()
                .collect(Collectors.toMap(
                    i -> allSymbols.get(i),
                    i -> allPrices.get(i).join()
                ))
        );
```

Your version is fine too — don't change it if it feels less natural.

---

## Missing — Q5, Q7, Q8

You skipped three:

**Q5 — exceptionally:**
```java
CompletableFuture<Double> r5 = CompletableFuture
    .supplyAsync(() -> {
        throw new RuntimeException("Market closed");
        // return getPrice("AAPL");
    })
    .exceptionally(ex -> {
        System.out.println("Error: " + ex.getMessage());
        return 0.0;  // fallback
    });
System.out.println(r5.join());
```

**Q7 — sequential with validation:**
```java
CompletableFuture<Order> r7 = CompletableFuture
    .supplyAsync(() -> getUser("deskA"))
    .thenCompose(user -> CompletableFuture.supplyAsync(
        () -> placeOrder(user, "AAPL", 10)))
    .thenCompose(order -> CompletableFuture.supplyAsync(() -> {
        if (!validateOrder(order))
            throw new RuntimeException("Order invalid");
        return order;
    }))
    .exceptionally(ex -> {
        System.out.println("Failed: " + ex.getMessage());
        return null;
    });
System.out.println(r7.join());
```

**Q8 — anyOf:**
```java
CompletableFuture<Object> r8 = CompletableFuture.anyOf(
    CompletableFuture.supplyAsync(() -> getPriceFromSource1("AAPL")),
    CompletableFuture.supplyAsync(() -> getPriceFromSource2("AAPL")),
    CompletableFuture.supplyAsync(() -> getPriceFromSource3("AAPL"))
);
System.out.println("Fastest price: " + r8.join());
// source2 wins — 100ms delay vs 200ms and 300ms
```

---

## One thing to remember — thenCompose vs thenApply

You used both correctly but say this in interview:

```
next step returns plain value    → thenApply   (like map)
next step returns CF<something>  → thenCompose (like flatMap)

Q4: getUser returns UserProfile (plain) → thenCompose because
    placeOrder itself is wrapped in supplyAsync → returns CF<Order>

If placeOrder was synchronous:
    .thenApply(user -> placeOrder(user, "AAPL", 10))  ← plain value
```

---

## Overall

✅ supplyAsync + thenAccept — solid
✅ thenApply chaining — solid
✅ allOf + parallel fetch — solid
✅ thenCompose for sequential — solid
✅ exceptionally — solid
✅ anyOf pattern — understood

CompletableFuture ✅ done. **Vector from scratch next?**