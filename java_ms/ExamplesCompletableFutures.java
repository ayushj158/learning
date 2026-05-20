package java_ms;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class ExamplesCompletableFutures {

    public static void main(String[] args){
        //q1: Fetch price of "AAPL" asynchronously and print it when done. Don't block main thread.
         CompletableFuture<Void> r1 = CompletableFuture.supplyAsync(() -> getPrice("AAPL"))
                           .thenAccept(a -> System.out.println(a));

        //q2: Fetch price of "AAPL" asynchronously. When done, convert it to a formatted string "AAPL: $189.50" and print it.
        CompletableFuture<Void> r2 = CompletableFuture.supplyAsync(() -> getPrice("AAPL"))
                         .thenApply(a -> "AAPL: " +a  )
                         .thenAccept(a -> System.out.println(a));
        r2.join();


        //q3 — Medium Fetch prices of "AAPL", "GOOG", "MSFT" in parallel. When ALL are done, print the average price.
        List<String> symbols = List.of("AAPL","GOOG", "MSFT" );
        List<CompletableFuture<Double>> prices = symbols.stream()
                                                        .map(a -> CompletableFuture.supplyAsync(() -> getPrice(a)))
                                                        .collect(Collectors.toList());
        CompletableFuture<Void> r3 = CompletableFuture.allOf(prices.toArray(new CompletableFuture[0]))
                         .thenAccept(all -> {
                              Double avg =  prices.stream()
                                      .mapToDouble(p->p.join())
                                      .average()   //.map(p -> p.join()).collect(Collectors.averagingDouble(p -> p));
                                      .orElse(0);
                              System.out.println("Avg=" +avg);
                         });
        r3.join();
        //Q4 Get user profile for "deskA" — then using that user, place an order for "AAPL" qty 10. 
        // These must happen sequentially (need user before placing order). Return CompletableFuture<Order>.
        CompletableFuture<Order> r4 = CompletableFuture.supplyAsync(() -> getUser("deskA"))
                           .thenCompose(up -> CompletableFuture.supplyAsync(() -> placeOrder(up, "AAPL",10)))
                           .exceptionally(ex -> {
                                System.out.println("Error=" +ex.getMessage() + "Trace=" +ex);
                                return new Order("2", "mock", "mock", 0, 0);
                           });

        System.out.println(r4.join().toString());

        //q6: Given a list of 10 symbols, fetch all prices in parallel. Return a Map<String, Double> of symbol → price when all are done.
        List<String> allSymbols = List.of("AAPL","GOOG", "MSFT","AMZN","TSLA","NVDA","META", "NFLX","JPM","GS");
        List<CompletableFuture<Double>> allPrices = allSymbols.stream()
                                                        .map(a -> CompletableFuture.supplyAsync(() -> getPrice(a)))
                                                        .collect(Collectors.toList());
       CompletableFuture<Map<String, Double>> r6 = CompletableFuture.allOf(allPrices.toArray(new CompletableFuture[0]))
                         .thenApply(v -> {
                             Map<String, Double> map = new HashMap<>();
                             for (int i = 0; i < allSymbols.size(); i++) {
                                map.put(
                                    allSymbols.get(i),
                                    allPrices.get(i).join()
                                );
                             }
                             return map;
                         });
        System.out.println(r6.join());

                         


    }

     // ── Price Service ─────────────────────────────────────────
    public static Double getPrice(String symbol) {
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        Map<String, Double> prices = Map.of(
            "AAPL", 189.50,
            "GOOG", 140.00,
            "MSFT", 375.00,
            "AMZN", 178.00,
            "TSLA", 245.00,
            "NVDA", 875.00,
            "META", 505.00,
            "NFLX", 625.00,
            "JPM",  198.00,
            "GS",   412.00
        );
        return prices.getOrDefault(symbol, 100.00);
    }

    public static Double getPriceFromSource1(String symbol) {
        try { Thread.sleep(300); } catch (InterruptedException e) {}
        return getPrice(symbol) * 1.001;  // slightly different price
    }

    public static Double getPriceFromSource2(String symbol) {
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        return getPrice(symbol) * 0.999;
    }

    public static Double getPriceFromSource3(String symbol) {
        try { Thread.sleep(200); } catch (InterruptedException e) {}
        return getPrice(symbol) * 1.002;
    }

    // ── User Service ──────────────────────────────────────────
    public static UserProfile getUser(String userId) {
        try { Thread.sleep(150); } catch (InterruptedException e) {}
        return new UserProfile(userId, userId + "@gs.com", "ACTIVE");
    }

    // ── Order Service ─────────────────────────────────────────
    public static Order placeOrder(UserProfile user, 
                                   String symbol, int qty) {
        try { Thread.sleep(200); } catch (InterruptedException e) {}
        double price = getPrice(symbol);
        return new Order(
            UUID.randomUUID().toString(),
            user.userId, symbol, qty, price);
    }

    public static boolean validateOrder(Order order) {
        try { Thread.sleep(50); } catch (InterruptedException e) {}
        // validate — reject orders over 1000 qty
        return order.qty <= 1000;
    }

    // ── Risk Service ──────────────────────────────────────────
    public static RiskScore calculateRisk(Position p) {
        try { Thread.sleep(300); } catch (InterruptedException e) {}
        return new RiskScore(p.symbol, p.qty * p.currentPrice * 0.05);
    }
}

// ── Domain Classes ────────────────────────────────────────────

class UserProfile {
    String userId;
    String email;
    String status;

    public UserProfile(String userId, String email, String status) {
        this.userId = userId;
        this.email  = email;
        this.status = status;
    }

    public String toString() {
        return "User{" + userId + ", " + email + ", " + status + "}";
    }
}

class Order {
    String orderId;
    String userId;
    String symbol;
    int qty;
    double price;

    public Order(String orderId, String userId, 
                 String symbol, int qty, double price) {
        this.orderId = orderId;
        this.userId  = userId;
        this.symbol  = symbol;
        this.qty     = qty;
        this.price   = price;
    }

    public String toString() {
        return "Order{" + orderId + 
               ", user=" + userId + 
               ", symbol=" + symbol + 
               ", qty=" + qty + 
               ", price=" + price + "}";
    }
}

class Position {
    String symbol;
    int qty;
    double currentPrice;

    public Position(String symbol, int qty, double currentPrice) {
        this.symbol       = symbol;
        this.qty          = qty;
        this.currentPrice = currentPrice;
    }
}

class RiskScore {
    String symbol;
    double value;

    public RiskScore(String symbol, double value) {
        this.symbol = symbol;
        this.value  = value;
    }

    public double getValue() { return value; }

    public String toString() {
        return "Risk{" + symbol + "=" + value + "}";
    }

}
