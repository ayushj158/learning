package java_ms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Stream {

    public static void main(String[] args) {
        // old way — loop
        List<String> list = new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.startsWith("A")) result.add(s.toUpperCase());
        }

        // Stream Pipeline — 3 parts ! source → intermediate operations → terminal operation
        result = list.stream() // source
                     .filter(ele -> ele.startsWith("A")) // intermediate — lazy, not executed yet
                     .map(ele -> ele.toUpperCase()) // intermediate — lazy, not executed yet
                     .collect(Collectors.toList()); // terminal — triggers execution

        List<Orders> orders = new ArrayList<>();
        orders.add(new Orders("1", "a", 10));
        orders.add(new Orders("2", "a", 20));
        orders.add(new Orders("3", "b", 30));
        System.out.println("********************** orders= " + orders);   

        //Q1 — Group orders by symbol and find total qty per symbol
        Map<String, Integer> r = orders.stream()
              .collect(Collectors.groupingBy(o->o.symbol, Collectors.summingInt(o-> o.qty)));
        //count occurenes of symbol
        Map<String, Long> r1 = orders.stream()
              .collect(Collectors.groupingBy(o->o.symbol, Collectors.counting()));
        System.out.println(r1);

        // Q2 — Find top 3 most traded symbols
        List<String> r3 = orders.stream()
                .collect(Collectors.groupingBy(o->o.symbol, Collectors.counting()))
                .entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String,Long> ele) -> ele.getValue()).reversed())
                .limit(2)
                .map(ele -> ele.getKey())
                .collect(Collectors.toList());
         System.out.println(r3);

        //Q3 — Flatten and deduplicate
        List<String> items = orders.stream()
                                .flatMap(o -> o.items.stream())
                                .distinct()
                                .collect(Collectors.toList());

        //Q4 Get list of all unique userIds who placed orders.
        List<Integer>  uniqueUserIds = orders.stream()
                                             .map(o -> o.userId)
                                             .distinct()
                                             .collect(Collectors.toList());
                                  
        System.out.println("uniqueUserIds= " + uniqueUserIds);

       //Q5- Get total value (qty * price) of all BUY orders.
       long value = orders.stream()
                          .mapToLong(o -> o.qty * o.price)
                          .sum();         
        System.out.println("totalValue= " + value);

       //Q6: Group orders by symbol — return Map<String, List<Order>>.
       Map<String, List<Orders>> ordersBySymbol = orders.stream()
                                                        .collect(Collectors.groupingBy(o-> o.symbol));
       System.out.println("ordersBySymbol= " + ordersBySymbol);

       //Q6': Group userId by symbol — return Map<String, List<Integer>>.
       Map<String, List<Integer>> usersBySymbol = orders.stream()
                                                        .collect(
                                                            Collectors.groupingBy(o-> o.symbol, 
                                                            Collectors.mapping(o->o.userId, Collectors.toList()))); // Very Important to remember
       System.out.println("usersBySymbol= " + usersBySymbol);

       //Q7: For each symbol, find total qty traded — return Map<String, Integer>.
       Map<String, Integer> qtyBySymbol = orders.stream()
                                                .collect(Collectors.groupingBy(o-> o.symbol,  Collectors.summingInt(o->o.qty)));
       System.out.println("qtyBySymbol= " + qtyBySymbol);

       //Q8: Find top 3 users by total order value — return List<Integer> of userIds.
       List<Integer> topUsers = orders.stream()
             .collect(Collectors.groupingBy(o->o.userId, Collectors.summingInt(o -> o.price*o.qty)))
             .entrySet().stream()
             .sorted(Comparator.comparingInt((Map.Entry<Integer,Integer> o) -> o.getValue()).reversed())
             .limit(3)
             .map(o-> o.getKey())
             .collect(Collectors.toList());                                         
                                    
       System.out.println("topUsers= " + topUsers);

       // Q' — Your frequency map from earlier — stream version
       // instead of loop
        String s = "abcasedfsdfdfkaccgscbb";
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);

        Map<Character, Long> r4 = s.chars()
        .mapToObj(c -> (char)c) // very important
        .collect(Collectors.groupingBy(c ->c, Collectors.counting()));
        System.out.println(r4);

    }

}

class Orders {
    
    public String orderId;
    public String symbol;
    public int userId;
    public int qty;
    public int price;
    public List<String> items = new ArrayList<>() ;
    Random r = new Random();

    public Orders(String orderId, String symbol,int qty){
        this.orderId = orderId;
        this.symbol = symbol;
        this.qty = qty;
        this.userId = r.nextInt(4);
        this.price = r.nextInt(100);

        items.add(String.valueOf(r.nextInt(0, 5)));
        items.add(String.valueOf(r.nextInt(0, 5)));
        items.add(String.valueOf(r.nextInt(0, 5)));
    }

    public String toString(){
        return orderId.concat(" symbol="+symbol).concat(" userId="+userId).concat(" qty="+qty).concat(" items="+items);
    }
}
