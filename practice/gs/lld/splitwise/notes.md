## Splitwise — Expense Sharing

> Design a lightweight expense sharing system. Users add expenses split among participants. System tracks who owes whom and simplifies debts.

```
addUser("Alice")
addUser("Bob")
addUser("Charlie")

addExpense("Alice", 300, ["Alice","Bob","Charlie"])
→ Alice paid 300, split equally = 100 each
→ Bob owes Alice 100
→ Charlie owes Alice 100

addExpense("Bob", 60, ["Bob","Charlie"])
→ Bob paid 60, split = 30 each
→ Charlie owes Bob 30

showBalances("Alice")  → Bob owes 100, Charlie owes 100
showBalances("Bob")    → Charlie owes 30, net: Alice owes 40
showBalances("Charlie")→ owes Alice 100, owes Bob 30
```

**API:**
```java
void addUser(String userId)
void addExpense(String paidBy, double amount, List<String> participants)
Map<String, Double> getBalances(String userId)
List<Transaction> simplifyDebts()
```

---

## Before coding — three questions:

1. How do you store who owes whom — what data structure?
2. If Alice owes Bob 100 and Bob owes Alice 60 — how do you net them?
3. What is debt simplification — why is it hard?

Take a shot.

## Building intuition first

---

## Question 1 — How to store who owes whom

Simplest approach — a map of maps:

```
balances = Map<String, Map<String, Double>>

balances["Bob"]["Alice"] = 100    ← Bob owes Alice 100
balances["Charlie"]["Alice"] = 100 ← Charlie owes Alice 100
balances["Charlie"]["Bob"] = 30   ← Charlie owes Bob 30
```

Read as: `balances[person][otherPerson]` = how much person owes otherPerson.

---

## Question 2 — Netting debts

```
Alice pays 300 split 3 ways:
  Bob owes Alice 100
  Charlie owes Alice 100

Bob pays 60 split 2 ways:
  Charlie owes Bob 30

But also:
  Alice owes Bob 0? No — Alice PAID, others owe HER

What if:
  Alice owes Bob 50 (from prev expense)
  Bob owes Alice 100 (from new expense)

Net: Bob owes Alice 100-50 = 50
→ always net opposite direction debts
```

**Netting rule:**
```java
// when A owes B amount:
balances[A][B] += amount
balances[B][A] -= amount  // B effectively "owes" A negative = A owes B

// or simpler — one direction only:
// positive = you owe them
// negative = they owe you
```

---

## Question 3 — Debt simplification — why hard

```
Without simplification:
  Alice → Bob: 100
  Bob → Charlie: 100
  Charlie → Alice: 100
  = 3 transactions

With simplification:
  All three owe each other same amount → net = 0
  = 0 transactions ✓

Harder example:
  A owes B: 10
  B owes C: 10
  C owes D: 10
  = 3 transactions

Simplified:
  A owes D: 10
  = 1 transaction ✓

Even harder:
  A owes B: 10
  A owes C: 20
  B owes D: 30
  C owes D: 10
  = 4 transactions

Simplified: ?
  A net: owes 30
  B net: gets 10, owes 30 → net owes 20
  C net: gets 20, owes 10 → net gets 10
  D net: gets 40

  A→D: 30, B→D: 10, C→D: 10? 
  or A→D: 30, B→C: 10?
  
  Many valid solutions — just minimize transactions
```

Debt simplification = graph problem — find minimum transactions to settle all debts.

---

## Core algorithm — net balances first

```
Step 1: Calculate net balance per person
  positive = net creditor (others owe you)
  negative = net debtor (you owe others)

Step 2: Match largest debtor with largest creditor
  person who owes most pays person who is owed most
  repeat until settled
```

---

## Trace

```
Expenses:
  Alice paid 300, split Alice/Bob/Charlie → Bob owes 100, Charlie owes 100
  Bob paid 60, split Bob/Charlie → Charlie owes 30

Net balances:
  Alice: +200  (paid 300, share = 100, net = +200)
  Bob:   -70   (paid 60, owed 100, share = 30, net = 60-100-30 = -70)

  wait let me recalculate:
  Alice: paid 300, owes share of 100 → net = +200
  Bob:   paid 60,  owes Alice 100, Charlie owes Bob 30
         net = 60 - 100 + 30 = -10
  Charlie: owes Alice 100, owes Bob 30
         net = -130

  Check: 200 + (-10) + (-130) = 60 ← not 0, something wrong

  Let me redo:
  Expense 1: Alice pays 300, 3 people
    Each owes 100
    Alice net: +200 (paid 300, her share = 100)
    Bob net:   -100
    Charlie:   -100

  Expense 2: Bob pays 60, 2 people
    Each owes 30
    Bob net:   +30
    Charlie:   -30

  Final net:
    Alice:   +200
    Bob:     -100 + 30 = -70
    Charlie: -100 - 30 = -130

  Check: 200 - 70 - 130 = 0 ✓

Simplify:
  Debtors:   Bob(-70), Charlie(-130)
  Creditors: Alice(+200)

  Charlie owes most → pays Alice first:
    Charlie pays Alice 130 → Charlie settled
    Alice remaining: 200-130 = 70

  Bob owes Alice 70:
    Bob pays Alice 70 → Bob settled ✓

  Result:
    Charlie → Alice: 130
    Bob → Alice: 70
    = 2 transactions ✓ (vs original 3)
```

---

## Now code it — three classes needed

```java
class User {
    String userId;
    String name;
}

class Expense {
    String expenseId;
    String paidBy;
    double amount;
    List<String> participants;
    double splitAmount;  // amount / participants.size()
}

class SplitWise {
    // users
    Map<String, User> users = new HashMap<>();
    
    // balances[personA][personB] = amount personA owes personB
    Map<String, Map<String, Double>> balances = new HashMap<>();
    
    void addUser(String userId) { }
    
    void addExpense(String paidBy, double amount, 
                    List<String> participants) { }
    
    Map<String, Double> getBalances(String userId) { }
    
    List<String> simplifyDebts() { }
}
```

Give it a go — start with `addExpense()` first.

Good approach — logic is right but three bugs.

---

## Bug 1 — returning wrong map

```java
owedByUser.forEach((key,value) -> {
    owedToUser.merge(key, -value, Double::sum);
});

return owedByUser;  // ← returning owedByUser not owedToUser!
```

You built `owedToUser` correctly but returned `owedByUser`. Should return `owedToUser`.

---

## Bug 2 — owedByUser can be null

```java
Map<String, Double> owedByUser = balances.get(userId);
// if userId has never borrowed → balances.get(userId) = null
// owedByUser.forEach → NullPointerException!
```

Fix:
```java
Map<String, Double> owedByUser = balances.getOrDefault(userId, new HashMap<>());
```

---

## Bug 3 — owedToUser is immutable

```java
.collect(Collectors.toMap(...))  // returns mutable map ✓ actually fine here
```

This is actually fine — `Collectors.toMap()` returns a mutable HashMap. No bug here.

---

## Clean corrected version

```java
Map<String, Double> getBalances(String userId) {
    // what userId owes others (negative — userId is debtor)
    Map<String, Double> owedByUser = balances.getOrDefault(userId, new HashMap<>());

    // what others owe userId (positive — userId is creditor)
    Map<String, Double> result = balances.entrySet()
        .stream()
        .filter(entry -> entry.getValue().containsKey(userId))
        .collect(Collectors.toMap(
            entry -> entry.getKey(),
            entry -> entry.getValue().get(userId)));  // positive = they owe userId

    // merge: subtract what userId owes each person
    owedByUser.forEach((person, amount) ->
        result.merge(person, -amount, Double::sum));  // negative = userId owes them

    // remove zero balances
    result.entrySet().removeIf(e -> e.getValue() == 0.0);

    return result;
}
```

---

## Trace

```
balances:
  Bob     → {Alice: 100}   ← Bob owes Alice 100
  Charlie → {Alice: 100, Bob: 30}

getBalances("Alice"):

owedByUser = balances.get("Alice") = {} (Alice owes nobody)

result from stream (who owes Alice):
  Bob's map contains Alice → Bob owes Alice 100
  Charlie's map contains Alice → Charlie owes Alice 100
  result = {Bob: 100, Charlie: 100}

merge owedByUser (empty) → no change

return {Bob: 100, Charlie: 100} ✓
```

```
getBalances("Bob"):

owedByUser = balances.get("Bob") = {Alice: 100}  ← Bob owes Alice 100

result from stream (who owes Bob):
  Charlie's map contains Bob → Charlie owes Bob 30
  result = {Charlie: 30}

merge owedByUser:
  result.merge("Alice", -100, Double::sum)
  result = {Charlie: 30, Alice: -100}
  ← positive = they owe Bob
  ← negative = Bob owes them

return {Charlie: 30, Alice: -100} ✓
```

---

Now code `simplifyDebts()` — use the net balance algorithm we traced earlier:

```
1. Calculate net balance per person
2. Separate into debtors (negative) and creditors (positive)
3. Match largest debtor with largest creditor
4. Record transaction, reduce both balances
5. Repeat until all settled
```

Give it a go.

## simplifyDebts() — step by step

---

## Step 1 — Calculate net balance per person

```java
// net[person] = positive means others owe them
//             = negative means they owe others

Map<String, Double> net = new HashMap<>();

// for each debtor → each creditor
balances.forEach((debtor, creditors) ->
    creditors.forEach((creditor, amount) -> {
        net.merge(debtor,   -amount, Double::sum);  // debtor loses money
        net.merge(creditor, +amount, Double::sum);  // creditor gains money
    }));
```

---

## Step 2 — Separate into debtors and creditors

```java
// use priority queues for always picking largest
// debtors — most negative first (owes most)
PriorityQueue<double[]> debtors = new PriorityQueue<>(
    (a, b) -> Double.compare(a[1], b[1]));  // most negative first

// creditors — most positive first (owed most)
PriorityQueue<double[]> creditors = new PriorityQueue<>(
    (a, b) -> Double.compare(b[1], a[1]));  // most positive first

// [0] = personIndex, [1] = netBalance
// use index because PQ needs primitive-friendly structure
List<String> people = new ArrayList<>(net.keySet());

for (int i = 0; i < people.size(); i++) {
    double balance = net.get(people.get(i));
    if (balance < 0) debtors.offer(new double[]{i, balance});
    if (balance > 0) creditors.offer(new double[]{i, balance});
}
```

---

## Step 3 — Match and settle

```java
List<String> transactions = new ArrayList<>();

while (!debtors.isEmpty() && !creditors.isEmpty()) {
    double[] debtor   = debtors.poll();    // person who owes most
    double[] creditor = creditors.poll();  // person owed most

    String debtorName   = people.get((int) debtor[0]);
    String creditorName = people.get((int) creditor[0]);

    double owedAmount  = -debtor[1];    // make positive
    double owedToAmount = creditor[1];

    double settledAmount = Math.min(owedAmount, owedToAmount);

    transactions.add(debtorName + " pays " + creditorName 
                     + ": " + settledAmount);

    double newDebtorBalance   = debtor[1]   + settledAmount;
    double newCreditorBalance = creditor[1] - settledAmount;

    // if not fully settled → put back
    if (Math.abs(newDebtorBalance) > 0.001)
        debtors.offer(new double[]{debtor[0], newDebtorBalance});
    if (Math.abs(newCreditorBalance) > 0.001)
        creditors.offer(new double[]{creditor[0], newCreditorBalance});
}

return transactions;
```

---

## Full simplifyDebts()

```java
List<String> simplifyDebts() {
    // step 1: calculate net balance
    Map<String, Double> net = new HashMap<>();
    balances.forEach((debtor, creditors) ->
        creditors.forEach((creditor, amount) -> {
            net.merge(debtor,   -amount, Double::sum);
            net.merge(creditor, +amount, Double::sum);
        }));

    // step 2: separate into debtors and creditors
    List<String> people = new ArrayList<>(net.keySet());

    PriorityQueue<double[]> debtors = new PriorityQueue<>(
        (a, b) -> Double.compare(a[1], b[1]));  // most negative first

    PriorityQueue<double[]> creditors = new PriorityQueue<>(
        (a, b) -> Double.compare(b[1], a[1]));  // most positive first

    for (int i = 0; i < people.size(); i++) {
        double balance = net.get(people.get(i));
        if (balance < -0.001) debtors.offer(new double[]{i, balance});
        if (balance >  0.001) creditors.offer(new double[]{i, balance});
    }

    // step 3: match and settle
    List<String> transactions = new ArrayList<>();

    while (!debtors.isEmpty() && !creditors.isEmpty()) {
        double[] debtor   = debtors.poll();
        double[] creditor = creditors.poll();

        String debtorName   = people.get((int) debtor[0]);
        String creditorName = people.get((int) creditor[0]);

        double settledAmount = Math.min(-debtor[1], creditor[1]);

        transactions.add(debtorName + " pays " + creditorName 
                         + ": " + settledAmount);

        double newDebtorBalance   = debtor[1]   + settledAmount;
        double newCreditorBalance = creditor[1] - settledAmount;

        if (Math.abs(newDebtorBalance)   > 0.001)
            debtors.offer(new double[]{debtor[0],   newDebtorBalance});
        if (Math.abs(newCreditorBalance) > 0.001)
            creditors.offer(new double[]{creditor[0], newCreditorBalance});
    }

    return transactions;
}
```

---

## Trace

```
balances:
  Bob     → {Alice: 100}
  Charlie → {Alice: 100, Bob: 30}

Step 1 — net balances:
  Bob:     -100 (owes Alice)
  Alice:   +100+100 = +200 (owed by Bob and Charlie)

  wait Charlie also owes Bob:
  Bob:     -100 + 30 = -70  (owes Alice 100, Charlie owes Bob 30)
  Alice:   +200
  Charlie: -100 - 30 = -130

  Check: -70 + 200 - 130 = 0 ✓

Step 2 — separate:
  debtors:   Charlie(-130), Bob(-70)  ← most negative first
  creditors: Alice(+200)

Step 3 — match:
  poll Charlie(-130), Alice(+200)
  settled = min(130, 200) = 130
  transaction: "Charlie pays Alice: 130"
  
  Charlie new balance: -130+130 = 0 → done
  Alice new balance:   200-130 = 70 → back to creditors

  poll Bob(-70), Alice(+70)
  settled = min(70, 70) = 70
  transaction: "Bob pays Alice: 70"
  
  both settled → done

Result:
  ["Charlie pays Alice: 130", "Bob pays Alice: 70"]
  2 transactions ✓ (vs original 3)
```

---

## Full Splitwise class

```java
class SplitWise {
    Map<String, User> users     = new HashMap<>();
    Map<String, Map<String, Double>> balances = new HashMap<>();

    void addUser(String userId) {
        users.put(userId, new User(userId));
    }

    void addExpense(String paidBy, double amount, List<String> participants) {
        Expense expense = new Expense(UUID.randomUUID().toString(), 
                                      paidBy, amount, participants);
        for (String participant : participants) {
            if (!participant.equals(paidBy)) {
                balances.computeIfAbsent(participant, k -> new HashMap<>())
                        .merge(paidBy, expense.splitAmount, Double::sum);
            }
        }
    }

    Map<String, Double> getBalances(String userId) {
        Map<String, Double> owedByUser = 
            balances.getOrDefault(userId, new HashMap<>());

        Map<String, Double> result = balances.entrySet().stream()
            .filter(e -> e.getValue().containsKey(userId))
            .collect(Collectors.toMap(
                e -> e.getKey(),
                e -> e.getValue().get(userId)));

        owedByUser.forEach((person, amount) ->
            result.merge(person, -amount, Double::sum));

        result.entrySet().removeIf(e -> Math.abs(e.getValue()) < 0.001);
        return result;
    }

    List<String> simplifyDebts() {
        Map<String, Double> net = new HashMap<>();
        balances.forEach((debtor, creditors) ->
            creditors.forEach((creditor, amount) -> {
                net.merge(debtor,   -amount, Double::sum);
                net.merge(creditor, +amount, Double::sum);
            }));

        List<String> people = new ArrayList<>(net.keySet());
        PriorityQueue<double[]> debtors   = new PriorityQueue<>(
            (a, b) -> Double.compare(a[1], b[1]));
        PriorityQueue<double[]> creditors = new PriorityQueue<>(
            (a, b) -> Double.compare(b[1], a[1]));

        for (int i = 0; i < people.size(); i++) {
            double bal = net.get(people.get(i));
            if (bal < -0.001) debtors.offer(new double[]{i, bal});
            if (bal >  0.001) creditors.offer(new double[]{i, bal});
        }

        List<String> transactions = new ArrayList<>();
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            double[] d = debtors.poll();
            double[] c = creditors.poll();
            double settled = Math.min(-d[1], c[1]);
            transactions.add(people.get((int)d[0]) + " pays " 
                           + people.get((int)c[0]) + ": " + settled);
            double nd = d[1] + settled;
            double nc = c[1] - settled;
            if (Math.abs(nd) > 0.001) debtors.offer(new double[]{d[0], nd});
            if (Math.abs(nc) > 0.001) creditors.offer(new double[]{c[0], nc});
        }
        return transactions;
    }
}
```

---

## What to say in interview

*"Balances stored as nested map — balances[debtor][creditor] = amount. addExpense splits amount and updates each non-payer's balance. getBalances nets what user owes against what they're owed. simplifyDebts computes net balance per person, uses two MaxHeaps to always match largest debtor with largest creditor — greedy approach minimizes transactions."*

---

Splitwise ✅ done. **Snake & Ladder next?**