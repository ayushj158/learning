## Problem 24 — Stock Trading Platform

> Design a stock trading platform where:
> - Users can **buy and sell** stocks
> - Each user has a **wallet** with a balance
> - Admins can **add stocks** with an initial price
> - Users can **view their portfolio**

```
Admin: addStock("AAPL", 189.50)
Admin: addStock("GOOG", 140.00)

User deskA: deposit(10000)
User deskA: buy("AAPL", 5)   → wallet -= 5 * 189.50 = 947.50
User deskA: buy("GOOG", 2)   → wallet -= 2 * 140.00 = 280.00
User deskA: portfolio()      → {AAPL:5, GOOG:2}, balance: 8772.50

User deskA: sell("AAPL", 2)  → wallet += 2 * 189.50 = 379.00
User deskA: portfolio()      → {AAPL:3, GOOG:2}, balance: 9151.50
```

**API:**
```java
void addStock(String symbol, double price)     // admin
void deposit(String userId, double amount)     // user
boolean buy(String userId, String symbol, int qty)
boolean sell(String userId, String symbol, int qty)
Portfolio getPortfolio(String userId)
```

---

Before coding — design the domain model first. Tell me:

1. What are your core classes/entities?
2. Where does the money/stock validation live — in `User` or in a `TradingService`?
3. What happens if two threads try to buy the same stock simultaneously — where's the race condition?


Great domain thinking — very close to how real trading systems are modelled. Let me refine and align with standard IB terminology:

---

## Domain Model

### 1. Stock (not Indices)
In IB context these are called **instruments** or **securities.** For simplicity — Stock.

```java
class Stock {
    String symbol;          // "AAPL"
    String name;            // "Apple Inc"
    double currentPrice;    // latest price
    Map<Long, Double> priceHistory;  // timestamp → price (time series)
}
```

Price history keyed by timestamp — covers your minute/hour/day granularity requirement.

---

### 2. User

```java
class User {
    String userId;
    String name;
    UserRole role;          // ADMIN, TRADER
}

enum UserRole { ADMIN, TRADER }
```

User is just identity — no business logic here.

---

### 3. Account

```java
class Account {
    String accountId;
    String userId;
    double balance;
    List<Transaction> transactions;  // full audit trail
}
```

Account owns money. All balance changes go through transactions — critical for IB audit requirements.

---

### 4. Transaction

```java
class Transaction {
    String transactionId;
    TransactionType type;   // DEPOSIT, WITHDRAWAL, BUY, SELL
    String symbol;          // null for DEPOSIT/WITHDRAWAL
    int qty;                // null for DEPOSIT/WITHDRAWAL
    double amount;          // total value
    double priceAtTime;     // price when trade executed
    long timestamp;
}

enum TransactionType { DEPOSIT, WITHDRAWAL, BUY, SELL }
```

Every money/stock movement is an immutable transaction — you never edit transactions, only append.

---

### 5. Portfolio

```java
class Portfolio {
    String userId;
    Map<String, Position> positions;  // symbol → position
}
```

---

### 6. Position

```java
class Position {
    String symbol;
    int qty;
    double avgBuyingPrice;    // weighted average cost
    double currentValue;      // qty * currentPrice (computed)
    double unrealisedPnL;     // currentValue - (qty * avgBuyingPrice)
}
```

Position tracks how much you hold and at what average cost — standard IB portfolio view.

---

## Full Domain Picture

```
User
 └── Account (1:1)
       └── balance
       └── List<Transaction>  ← immutable audit trail

Portfolio (1:1 with User)
 └── Map<symbol, Position>
       └── qty
       └── avgBuyingPrice
       └── unrealisedPnL

Stock
 └── currentPrice
 └── priceHistory (time series)
```

---

## Service Layer

```
TradingService          ← orchestrates buy/sell
AccountService          ← manages balance, transactions
PortfolioService        ← manages positions
StockService            ← manages stock prices, history
```

Business logic lives in services — domain objects are pure data.

---

## Key IB design decisions to mention

| Decision | Why |
|---|---|
| Immutable transactions | Audit trail — regulatory requirement in IB |
| Separate Account from User | User is identity, Account is financial — clean separation |
| Position tracks avgBuyingPrice | Weighted average cost — standard portfolio accounting |
| unrealisedPnL computed | currentValue - costBasis — traders need this live |
| priceHistory as time series | Supports minute/hour/day granularity for charts |

---

## Now the race condition question

Given this domain — where do you see the race condition when two traders buy the same stock simultaneously?

Hint — there are actually **two** race conditions, one in Account and one in Portfolio.

Exactly right — and that's the standard **anemic domain model** pattern used in enterprise systems like GS.

---

## Why validation lives in TradingService not User/Account

```
User.buy()  ← WRONG — mixes identity with business logic
              User shouldn't know about prices, portfolios, accounts

TradingService.buy() ← RIGHT — orchestrates everything
    1. UserService.isActive(userId)          → validate user
    2. StockService.getPrice(symbol)         → get current price
    3. AccountService.validateBalance(...)   → enough funds?
    4. AccountService.debit(...)             → deduct money
    5. PortfolioService.addPosition(...)     → update holdings
    6. TransactionService.record(...)        → audit trail
```

Each service owns one concern. TradingService orchestrates them.

---

## Now the race condition — you spotted two?

Here's the first one — in Account:

```
Thread1 buy(deskA, AAPL, 5): balance = getBalance() → 1000
Thread2 buy(deskA, GOOG, 3): balance = getBalance() → 1000

Thread1: 5 * 189.50 = 947.50 ≤ 1000 → valid ✓
Thread2: 3 * 140.00 = 420.00 ≤ 1000 → valid ✓

Thread1: debit 947.50 → balance = 52.50
Thread2: debit 420.00 → balance = -367.50 ✗ OVERDRAFT!
```

Can you spot the second race condition — in Portfolio?

Here it is:

---

## Race Condition 2 — in Portfolio

```
Thread1 sell(deskA, AAPL, 5): qty = getQty("AAPL") → 5
Thread2 sell(deskA, AAPL, 3): qty = getQty("AAPL") → 5

Thread1: 5 ≤ 5 → valid ✓
Thread2: 3 ≤ 5 → valid ✓

Thread1: qty -= 5 → qty = 0
Thread2: qty -= 3 → qty = -3 ✗ NEGATIVE HOLDINGS!
```

Selling stocks you don't have — in real trading this is **naked short selling** — illegal in most markets and a massive compliance issue at GS.

---

## Two race conditions summary

| | Account | Portfolio |
|---|---|---|
| Problem | Overdraft | Negative holdings |
| Shared state | balance | qty per position |
| Risk | Financial loss | Regulatory breach |

---

## How to fix both

Same pattern for both — **optimistic locking or synchronized block per user.**

### Fix 1 — synchronized per userId

```java
// lock per user — deskA and deskB never block each other
private final ConcurrentHashMap<String, Object> userLocks 
                                = new ConcurrentHashMap<>();

private Object getLock(String userId) {
    return userLocks.computeIfAbsent(userId, k -> new Object());
}

public boolean buy(String userId, String symbol, int qty) {
    synchronized (getLock(userId)) {
        // all checks and updates atomic per user
        double price = stockService.getPrice(symbol);
        double total = price * qty;

        if (!accountService.hasSufficientBalance(userId, total))
            return false;

        accountService.debit(userId, total);
        portfolioService.addPosition(userId, symbol, qty, price);
        transactionService.record(userId, BUY, symbol, qty, total, price);
        return true;
    }
}
```

User-level lock means:
- deskA and deskB trade simultaneously ✓
- deskA can't have two conflicting trades simultaneously ✓

---

### Fix 2 — optimistic locking (mention verbally)

```java
// Account has a version number
class Account {
    int version;
    double balance;
}

// update only succeeds if version matches
UPDATE accounts 
SET balance = balance - ?, version = version + 1
WHERE userId = ? AND version = ? AND balance >= ?
// if 0 rows updated → someone else modified → retry
```

Used in DB-backed systems — no explicit locks needed.

---

Now let's code `TradingService.buy()` and `sell()` — you have the domain model, you have the fix. Give it a go.

Excellent catch — you're describing a real **Order Book** system. This is how actual trading works.

---

## What you're describing

```
Buyer:  "I want 5 AAPL at $189.00 or lower"  → BID
Seller: "I want to sell 5 AAPL at $189.00 or higher" → ASK

Order Book matches them:
BID $189.00 meets ASK $189.00 → TRADE EXECUTED

Buyer:  wallet  -= 5 * 189.00, portfolio += 5 AAPL
Seller: wallet  += 5 * 189.00, portfolio -= 5 AAPL
```

---

## New entities needed

### Order

```java
class Order {
    String orderId;
    String userId;
    String symbol;
    OrderType type;         // BUY or SELL
    OrderStatus status;     // PENDING, PARTIAL, FILLED, CANCELLED
    int requestedQty;
    int filledQty;
    int remainingQty;       // requestedQty - filledQty
    double limitPrice;      // max price buyer will pay / min price seller will accept
    long timestamp;
}

enum OrderType   { BUY, SELL }
enum OrderStatus { PENDING, PARTIAL, FILLED, CANCELLED }
```

### OrderBook

```java
class OrderBook {
    String symbol;
    // BUY orders sorted by price DESC — highest bid first
    PriorityQueue<Order> bids;
    // SELL orders sorted by price ASC — lowest ask first
    PriorityQueue<Order> asks;
}
```

Why these sort orders?
```
Best bid = highest price buyer willing to pay   → DESC
Best ask = lowest price seller willing to accept → ASC

Match when: bestBid.price >= bestAsk.price
```

### Trade (execution record)

```java
class Trade {
    String tradeId;
    String buyOrderId;
    String sellOrderId;
    String symbol;
    int qty;
    double executionPrice;  // price at which trade happened
    long timestamp;
}
```

---

## Updated Domain Picture

```
User
 └── Account (balance, transactions)
 └── Portfolio (positions)

Order
 └── placed by User
 └── matched by OrderBook

Trade
 └── result of OrderBook match
 └── triggers Account + Portfolio updates for both buyer and seller

OrderBook (per symbol)
 └── bids PriorityQueue (DESC price)
 └── asks PriorityQueue (ASC price)
```

---

## Full Implementation

```java
// ─── Enums ───────────────────────────────────────────────────
enum OrderType   { BUY, SELL }
enum OrderStatus { PENDING, PARTIAL, FILLED, CANCELLED }
enum TransactionType { DEPOSIT, WITHDRAWAL, BUY, SELL }

// ─── Order ───────────────────────────────────────────────────
class Order {
    String orderId;
    String userId;
    String symbol;
    OrderType type;
    OrderStatus status;
    int requestedQty;
    int filledQty;
    double limitPrice;
    long timestamp;

    public Order(String userId, String symbol, 
                 OrderType type, int qty, double limitPrice) {
        this.orderId      = UUID.randomUUID().toString();
        this.userId       = userId;
        this.symbol       = symbol;
        this.type         = type;
        this.status       = OrderStatus.PENDING;
        this.requestedQty = qty;
        this.filledQty    = 0;
        this.limitPrice   = limitPrice;
        this.timestamp    = System.currentTimeMillis();
    }

    public int getRemainingQty() { 
        return requestedQty - filledQty; 
    }
}

// ─── Trade ───────────────────────────────────────────────────
class Trade {
    String tradeId;
    String buyOrderId;
    String sellOrderId;
    String symbol;
    int qty;
    double executionPrice;
    long timestamp;

    public Trade(String buyOrderId, String sellOrderId,
                 String symbol, int qty, double executionPrice) {
        this.tradeId        = UUID.randomUUID().toString();
        this.buyOrderId     = buyOrderId;
        this.sellOrderId    = sellOrderId;
        this.symbol         = symbol;
        this.qty            = qty;
        this.executionPrice = executionPrice;
        this.timestamp      = System.currentTimeMillis();
    }
}

// ─── Account ─────────────────────────────────────────────────
class Account {
    String userId;
    double balance;
    List<Transaction> transactions = new ArrayList<>();

    public Account(String userId, double initialBalance) {
        this.userId  = userId;
        this.balance = initialBalance;
    }

    public boolean debit(double amount) {
        if (balance < amount) return false;
        balance -= amount;
        transactions.add(new Transaction(
            TransactionType.WITHDRAWAL, amount, balance));
        return true;
    }

    public void credit(double amount) {
        balance += amount;
        transactions.add(new Transaction(
            TransactionType.DEPOSIT, amount, balance));
    }
}

// ─── Transaction ─────────────────────────────────────────────
class Transaction {
    TransactionType type;
    double amount;
    double balanceAfter;
    long timestamp;

    public Transaction(TransactionType type, 
                       double amount, double balanceAfter) {
        this.type         = type;
        this.amount       = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp    = System.currentTimeMillis();
    }
}

// ─── Position ────────────────────────────────────────────────
class Position {
    String symbol;
    int qty;
    double avgBuyingPrice;

    public Position(String symbol, int qty, double price) {
        this.symbol         = symbol;
        this.qty            = qty;
        this.avgBuyingPrice = price;
    }

    // weighted average cost on additional buy
    public void addQty(int additionalQty, double price) {
        double totalCost    = avgBuyingPrice * qty + price * additionalQty;
        qty                += additionalQty;
        avgBuyingPrice      = totalCost / qty;
    }

    public boolean removeQty(int sellQty) {
        if (sellQty > qty) return false;
        qty -= sellQty;
        return true;
    }
}

// ─── OrderBook ───────────────────────────────────────────────
class OrderBook {
    private final String symbol;
    
    // highest bid first
    private final PriorityQueue<Order> bids = new PriorityQueue<>(
        (a, b) -> Double.compare(b.limitPrice, a.limitPrice));
    
    // lowest ask first
    private final PriorityQueue<Order> asks = new PriorityQueue<>(
        (a, b) -> Double.compare(a.limitPrice, b.limitPrice));

    private final ReentrantLock lock = new ReentrantLock();

    public OrderBook(String symbol) { 
        this.symbol = symbol; 
    }

    public List<Trade> addOrder(Order order) {
        lock.lock();
        try {
            if (order.type == OrderType.BUY) {
                bids.offer(order);
            } else {
                asks.offer(order);
            }
            return match();
        } finally {
            lock.unlock();
        }
    }

    private List<Trade> match() {
        List<Trade> trades = new ArrayList<>();

        while (!bids.isEmpty() && !asks.isEmpty()) {
            Order bid = bids.peek();
            Order ask = asks.peek();

            // no match — best bid lower than best ask
            if (bid.limitPrice < ask.limitPrice) break;

            // match found
            int matchQty = Math.min(
                bid.getRemainingQty(), ask.getRemainingQty());
            
            // execution at ask price (standard convention)
            double executionPrice = ask.limitPrice;

            // update filled quantities
            bid.filledQty += matchQty;
            ask.filledQty += matchQty;

            // update order status
            bid.status = bid.getRemainingQty() == 0 
                ? OrderStatus.FILLED : OrderStatus.PARTIAL;
            ask.status = ask.getRemainingQty() == 0 
                ? OrderStatus.FILLED : OrderStatus.PARTIAL;

            // remove fully filled orders
            if (bid.getRemainingQty() == 0) bids.poll();
            if (ask.getRemainingQty() == 0) asks.poll();

            trades.add(new Trade(
                bid.orderId, ask.orderId, 
                symbol, matchQty, executionPrice));
        }
        return trades;
    }
}

// ─── TradingService ──────────────────────────────────────────
class TradingService {
    private final Map<String, Account>   accounts   = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Position>> portfolios 
                                                     = new ConcurrentHashMap<>();
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, Double>    stockPrices= new ConcurrentHashMap<>();
    private final List<Trade>            tradeHistory = new ArrayList<>();

    // ── Admin operations ──────────────────────────────────────
    public void addStock(String symbol, double price) {
        stockPrices.put(symbol, price);
        orderBooks.put(symbol, new OrderBook(symbol));
    }

    // ── User operations ───────────────────────────────────────
    public void createAccount(String userId, double initialBalance) {
        accounts.put(userId, new Account(userId, initialBalance));
        portfolios.put(userId, new ConcurrentHashMap<>());
    }

    public void deposit(String userId, double amount) {
        accounts.get(userId).credit(amount);
    }

    // ── Place order ───────────────────────────────────────────
    public boolean placeOrder(String userId, String symbol,
                               OrderType type, int qty, double limitPrice) {
        // validate
        if (!accounts.containsKey(userId)) return false;
        if (!orderBooks.containsKey(symbol)) return false;

        Account account = accounts.get(userId);

        // for BUY — reserve funds upfront
        if (type == OrderType.BUY) {
            double maxCost = limitPrice * qty;
            synchronized (account) {
                if (!account.debit(maxCost)) return false; // insufficient funds
            }
        }

        // for SELL — validate holdings
        if (type == OrderType.SELL) {
            Map<String, Position> portfolio = portfolios.get(userId);
            Position position = portfolio.get(symbol);
            if (position == null || position.qty < qty) return false;
        }

        // create and submit order
        Order order = new Order(userId, symbol, type, qty, limitPrice);
        List<Trade> trades = orderBooks.get(symbol).addOrder(order);

        // settle all matched trades
        for (Trade trade : trades) {
            settleTrade(trade, symbol);
        }

        return true;
    }

    // ── Settle trade — update both buyer and seller ───────────
    private void settleTrade(Trade trade, String symbol) {
        // find buyer and seller orders — in real system stored in OrderService
        // for simplicity we pass symbol and use trade data directly

        double totalCost = trade.qty * trade.executionPrice;

        // update buyer portfolio
        portfolios.get(getBuyerUserId(trade))
            .computeIfAbsent(symbol, 
                k -> new Position(symbol, 0, trade.executionPrice))
            .addQty(trade.qty, trade.executionPrice);

        // credit seller wallet + update seller portfolio
        String sellerUserId = getSellerUserId(trade);
        accounts.get(sellerUserId).credit(totalCost);
        portfolios.get(sellerUserId).get(symbol).removeQty(trade.qty);

        // refund buyer overpayment if execution < limit price
        // buyer paid limitPrice * qty upfront, actual cost may be less
        // this would require storing original order limit — simplified here

        tradeHistory.add(trade);
        stockPrices.put(symbol, trade.executionPrice); // update last price
    }

    public Portfolio getPortfolio(String userId) {
        return new Portfolio(userId, portfolios.get(userId));
    }
}
```

---

## What to say in interview

*"A real trading platform needs an Order Book — buyers specify limit price (max willing to pay), sellers specify limit price (min willing to accept). The order book matches them when bid >= ask. Execution happens at ask price by convention. Settlement updates both buyer and seller atomically — wallet and portfolio. Order book has its own ReentrantLock per symbol — AAPL and GOOG order books never block each other."*

---

## Key design decisions

| Decision | Why |
|---|---|
| Order Book per symbol | Independent matching per instrument |
| Limit price on orders | Real trading — no market orders for simplicity |
| Reserve funds on BUY order | Prevent overdraft before match |
| Execution at ask price | Standard price-time priority convention |
| Partial fills | Order may not fill completely if insufficient liquidity |
| Trade as separate entity | Immutable audit record of every execution |
| ReentrantLock per OrderBook | Symbol-level isolation |

---

Stock Trading Platform ✅ done. **URL Shortener or Parking Lot next?**



## Stock Trading Platform Revision

Answer these questions verbally:

1. What are the core domain entities?
2. What is an Order Book and why do we need it?
3. How does matching work — what data structures for bids and asks?
4. What happens when a trade is matched — what's settlement?
5. Where are the race conditions and how did we solve them?
6. What design patterns did we use?

Go.

Strong answer — very close. Two small corrections:

---

## Correction 1 — Heap directions are flipped

```
BUY orders  → MAX heap  (highest bid first — best buyer willing to pay most)
SELL orders → MIN heap  (lowest ask first — best seller willing to accept least)

Match when: bestBid (max of buys) >= bestAsk (min of sells)
```

You said:
```
buy side min heap  ← WRONG
sell side max heap ← WRONG
```

Think about it:
```
As a buyer — you want to match with cheapest seller first
As a seller — you want to match with highest bidder first

So:
BUY heap  → MAX heap → highest bidder at top → matches cheapest seller
SELL heap → MIN heap → cheapest seller at top → matches highest bidder
```

---

## Correction 2 — CHM for all indices ✅

```
Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
// key = ticker symbol "AAPL", "GOOG"
// value = OrderBook with bids + asks heaps
```

AAPL and GOOG order books never block each other ✓

---

## What you missed — settlement

When bid >= ask → trade executes:

```
Buyer:  wallet  -= qty * executionPrice
        portfolio += qty of stock

Seller: wallet  += qty * executionPrice
        portfolio -= qty of stock

Trade record created → immutable audit trail
Stock price updated → executionPrice becomes latest price
```

---

## What you missed — race conditions

```
Race 1: Two buyers matching same ask simultaneously
→ OrderBook has ReentrantLock per symbol
→ AAPL matching locked independently from GOOG

Race 2: Buyer wallet deducted but order never fills
→ Reserve funds upfront on BUY order placement
→ Refund if order cancelled

Race 3: Seller sells more than they own
→ Validate portfolio holdings before accepting SELL order
→ synchronized on portfolio per user
```

---

## Design patterns

```
Domain Model:    User, Account, Portfolio, Order, Stock, Trade
Service Layer:   TradingService orchestrates everything
Repository:      AccountService, PortfolioService, StockService
Observer:        Trade events → notify subscribers (our notification system!)
Strategy:        Order types — Market, Limit, Stop (extensible)
```

---

## Full domain picture — say this in interview

```
User        → identity only
Account     → owns wallet (balance + transactions)
Portfolio   → owns positions (symbol + qty + avgPrice)
Order       → placed by user, matched by OrderBook
OrderBook   → one per symbol, two heaps (MaxHeap bids, MinHeap asks)
Trade       → immutable record of matched order
Stock       → price + history
```

---

## One liner

*"CHM of OrderBooks keyed by symbol — each OrderBook has MaxHeap for bids and MinHeap for asks. Match when bestBid >= bestAsk. ReentrantLock per OrderBook for thread safety. Settlement updates buyer wallet/portfolio and seller wallet/portfolio atomically. Funds reserved upfront to prevent overdraft."*

---

Ready for **URL Shortener revision** or move to new LLD problems?