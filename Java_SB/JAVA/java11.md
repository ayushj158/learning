Perfect. **Java 11 is where “modern Java” became enterprise-ready**, and **`var` + `HttpClient`** are deceptively simple features that interviewers use to test **judgement, not syntax**.

We’ll go **slow and deep**, exactly like before.

---

# 🟦 JAVA 11 — `var` & `HttpClient` (DEEP DIVE)

We’ll do this in **two clear sections**:

1️⃣ `var` — local variable type inference
2️⃣ `HttpClient` — modern HTTP stack (and why banks cared)

---

# PART 1️⃣ — `var` (LOCAL VARIABLE TYPE INFERENCE)

---

## 1️⃣ Why `var` Was Introduced (Real Problem)

Before Java 10/11, Java had:

```java
Map<AccountId, List<Transaction>> transactionsByAccount =
        new HashMap<AccountId, List<Transaction>>();
```

Problems:

* Type noise dominates intent
* Refactoring is painful
* Readability suffers with generics-heavy code

Java wanted:

> **Less ceremony, without losing static typing**

Important:

> Java did NOT become dynamically typed.

---

## 2️⃣ What `var` REALLY Is (Critical Mental Model)

> **`var` is NOT a type.
> It is compiler-time type inference for local variables only.**

The compiler still infers a **strong, static type**.

```java
var x = new ArrayList<String>();
```

At compile time:

```java
ArrayList<String> x;
```

No runtime impact. No ambiguity.

---

## 3️⃣ Where `var` Is ALLOWED (Very Important)

### ✅ Allowed

* Local variables
* Loop variables
* Try-with-resources

```java
var accounts = accountService.findAll();

for (var acc : accounts) { ... }

try (var stream = Files.lines(path)) { ... }
```

---

### ❌ NOT allowed

```java
var field;            // ❌
var parameter;        // ❌
var returnType();     // ❌
```

This was **deliberate** to protect API clarity.

---

## 4️⃣ When `var` IMPROVES Readability (Use It)

### ✅ Good usage

```java
var transactionsByCurrency =
    accounts.stream()
            .collect(groupingBy(Account::getCurrency));
```

Why this is good:

* RHS makes type obvious
* LHS noise removed
* Refactoring safe

---

### ✅ Loop-heavy or stream-heavy code

```java
var total =
    balances.stream()
            .filter(this::isValid)
            .map(this::normalize)
            .reduce(ZERO, ADD);
```

Intent > type verbosity.

---

## 5️⃣ When `var` DESTROYS Readability (Avoid It)

### ❌ Bad usage

```java
var x = process(a, b, c);
```

Questions arise:

* What is `x`?
* Is it mutable?
* Is it optional?
* Is it a collection?

---

### ❌ Bad in domain logic

```java
var account = repo.findById(id);
```

Is this:

* `Account`?
* `Optional<Account>`?
* `AccountEntity`?

This is **dangerous in financial code**.

---

## 6️⃣ `var` + Streams (Subtle Pitfalls)

### ❌ Misleading

```java
var result = accounts.stream()
                     .map(...)
                     .filter(...);
```

This is a `Stream`, not a `List`.

Later:

```java
result.size(); // ❌
```

Senior rule:

> Use `var` only when the inferred type is obvious **and stable**.

---

## 7️⃣ `var` Does NOT Mean Weak Typing (Interview Trap)

Wrong claim:

> “`var` makes Java dynamically typed”

Correct response:

> “`var` is compile-time inference only; Java remains strongly and statically typed.”

Interviewers LOVE this clarification.

---

## 8️⃣ FS / Enterprise Guidance for `var`

Use `var`:

* Inside methods
* For short scopes
* Where RHS is self-explanatory

Avoid `var`:

* In domain logic
* Near money calculations
* Where type ambiguity increases risk

---

## 9️⃣ Interview-Grade Articulation (`var`)

Say:

> “We use `var` selectively to reduce verbosity in local scopes, but avoid it where explicit types improve clarity, especially in domain and financial logic.”

That is a **senior answer**.

---

# PART 2️⃣ — JAVA 11 `HttpClient`

---

## 1️⃣ Why a New HttpClient Was Needed

Before Java 11:

* No standard HTTP client
* Teams used:

  * Apache HttpClient
  * OkHttp
  * RestTemplate
* Inconsistent configs
* Different TLS behaviour
* Dependency sprawl

Java wanted:

> **A modern, standard, non-blocking HTTP client built into the JDK**

---

## 2️⃣ What `HttpClient` REALLY Is

Java 11 `HttpClient` is:

* Immutable
* Thread-safe
* Supports HTTP/1.1 & HTTP/2
* Supports sync & async
* Uses CompletableFuture

This was a **big deal for banks**.

---

## 3️⃣ Basic Usage (Clear & Explicit)

```java
HttpClient client = HttpClient.newHttpClient();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.bank.com/accounts"))
        .GET()
        .build();

HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());
```

Simple, no external deps.

---

## 4️⃣ Async & Non-Blocking (Where It Shines)

```java
client.sendAsync(request, BodyHandlers.ofString())
      .thenApply(HttpResponse::body)
      .thenAccept(this::process);
```

Key points:

* Uses `CompletableFuture`
* Non-blocking
* Backpressure-friendly

Useful for:

* Aggregation services
* Fan-out/fan-in patterns

---

## 5️⃣ FS-Critical Features (Why Banks Care)

### 5.1 TLS & Security

* Uses JDK TLS stack
* Centralised security policies
* Easier audits

### 5.2 HTTP/2

* Multiplexing
* Lower latency
* Better throughput

### 5.3 No Dependency Drift

* No CVE panic from Apache upgrades
* JDK-patched centrally

---

## 6️⃣ Where `HttpClient` Is NOT Enough

### ❌ Missing features

* Advanced connection pooling configs
* Interceptors / filters
* Spring ecosystem integration

That’s why:

* Spring WebClient still dominates
* HttpClient is often used for:

  * infra calls
  * internal services
  * platform utilities

---

## 7️⃣ Blocking vs Non-Blocking (Senior Decision)

### ❌ Blind async usage

```java
sendAsync(...).join(); // pointless
```

### Senior rule:

> Use async only when you can propagate async end-to-end.

Otherwise:

* Blocking is simpler
* Safer
* More predictable

---

## 8️⃣ Error Handling Nuances (Important)

`HttpClient`:

* Does NOT throw exception for 4xx/5xx
* You must inspect status codes

```java
if (response.statusCode() >= 400) {
    throw new ExternalServiceException();
}
```

This is often missed.

---

## 9️⃣ HttpClient vs RestTemplate vs WebClient

| Client       | Use Case                                |
| ------------ | --------------------------------------- |
| HttpClient   | Low-level, JDK-native                   |
| RestTemplate | Legacy (blocking, deprecated direction) |
| WebClient    | Reactive, Spring ecosystem              |

Banks often:

* Keep RestTemplate (legacy)
* Introduce WebClient (new)
* Use HttpClient for platform libs

---

## 🔟 FS / Enterprise Pitfalls

❌ Hardcoding timeouts
❌ No retry / circuit breaker
❌ Blocking async calls
❌ Logging request bodies (PII leak)

HttpClient must be wrapped by:

* resilience
* observability
* security layers

---

## 1️⃣1️⃣ Interview-Grade Articulation (HttpClient)

Say:

> “Java 11’s HttpClient gives a standard, secure HTTP stack with async support. We typically wrap it with resilience and observability layers, or use Spring WebClient where reactive pipelines are required.”

That is **very strong**.

---

## 🧠 FINAL SUMMARY (Java 11)

* `var` → reduce noise, not clarity
* HttpClient → standardisation, security, async
* Both require **judgement**, not blind adoption

---