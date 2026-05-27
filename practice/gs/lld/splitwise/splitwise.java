import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.stream.Collectors;

public class splitwise {

    // users
    static Map<String, User> users = new HashMap<>();
    // balances[personA][personB] = amount personA owes personB
    static Map<String, Map<String, BigDecimal>> balances = new HashMap<>();
    
    void addUser(String userId) { }

    public static void main (String[] args) {
        addExpense("Alice", 200.0, List.of("Alice","Bob"));
        addExpense("Alice", 200.0, List.of("Alice","Charlie"));
        addExpense("Bob", 60.0, List.of("Bob","Charlie"));

        System.out.println(balances);
        System.out.println(getBalances("Alice"));
        System.out.println( simplifyDebts());
    }
    
   private static void addExpense(String paidBy, double amount, List<String> participants) {
      String expenseId = UUID.randomUUID().toString();
      Expense expense  = new Expense(expenseId, paidBy, amount, participants);

      for(String participant: participants){
        if(!participant.equals(paidBy)) {
            balances.computeIfAbsent(participant, k-> new HashMap<>())
                    .merge(paidBy,expense.splitAmount, BigDecimal::add);
        }
      }
    }
    
    // show who owes this user AND who this user owes.
    private static Map<String, BigDecimal> getBalances(String userId) { 
        // what userId owes others (negative — userId is debtor)
        Map<String, BigDecimal> owedByUser = new HashMap<> (balances.getOrDefault(userId,new HashMap<>()));
        // what others owe userId (positive — userId is creditor)
        Map<String, BigDecimal> result = balances.entrySet()
                                                .stream()
                                                .filter(entry -> entry.getValue().containsKey(userId))
                                                .collect(Collectors.toMap(
                                                    entry -> entry.getKey(),
                                                    entry -> entry.getValue().get(userId)));
        owedByUser.forEach((person,amount)->{
            result.merge(person, amount.negate(), BigDecimal::add);
        });
        result.entrySet().removeIf(e -> e.getValue().compareTo(BigDecimal.ZERO) == 0);
        return result;
    }
    
   private static List<String> simplifyDebts() {
    Map<String, BigDecimal> net = new HashMap<>();
    List<String> transactions = new ArrayList<>();
    balances.forEach((debtor, creditors) -> {
        creditors.forEach((creditor,amount) -> {
            net.merge(debtor, amount.negate(), BigDecimal::add); // debtor owes money
            net.merge(creditor, amount, BigDecimal::add); // creditor gets money
        });
    });
    System.out.println(net);

    PriorityQueue<BigDecimal[]> debtors = new PriorityQueue<>((a,b) -> a[1].compareTo(b[1])); // most negatives first, minheap
    PriorityQueue<BigDecimal[]> creditors = new PriorityQueue<>((a,b) -> b[1].compareTo(a[1])); // most positives first, maxheap
    List<String> people = new ArrayList(net.keySet());
    

    for(int i=0; i< people.size(); i++){
        BigDecimal balance = net.get(people.get(i));
        if(balance.compareTo(BigDecimal.ZERO) < 0) debtors.offer(new BigDecimal[]{new BigDecimal(i), balance});
        if(balance.compareTo(BigDecimal.ZERO) > 0) creditors.offer(new BigDecimal[]{new BigDecimal(i), balance});
    }
    System.out.println("debtors=" + Arrays.toString(debtors.toArray()));
    System.out.println("creditors=" + Arrays.toString(creditors.toArray()));
    while(!debtors.isEmpty() && !creditors.isEmpty()){
        BigDecimal[] debtor = debtors.poll(); //highest debt
        BigDecimal[] creditor = creditors.poll(); // highest credit

        System.out.println("debtor=" + Arrays.toString(debtor) + " creditor=" + Arrays.toString(creditor));
        String debtorName = people.get(debtor[0].intValue());
        String creditorName = people.get(creditor[0].intValue());

        //  // settled = min(what debtor owes, what creditor is owed)
        BigDecimal settledAmount = debtor[1].negate().min(creditor[1]);
        transactions.add(debtorName + " pays " + creditorName 
                         + ": " + settledAmount);

        BigDecimal newDebtorBalance = debtor[1].add(settledAmount);
        BigDecimal newCreditorBalance = creditor[1].subtract(settledAmount);

        if (newDebtorBalance.compareTo(BigDecimal.ZERO) != 0)
            debtors.offer(new BigDecimal[]{debtor[0],   newDebtorBalance});
        if (newCreditorBalance.compareTo(BigDecimal.ZERO) != 0)
            creditors.offer(new BigDecimal[]{creditor[0], newCreditorBalance});
    }

        return transactions;
    }

}

class User {
    String userId;
    String name;
}

class Expense {
    String expenseId;
    String paidBy;
    BigDecimal amount;
    List<String> participants;
    BigDecimal splitAmount;  // amount / participants.size()

    public Expense(String expenseId,String paidBy,double amount, List<String> participants){
        this.expenseId = expenseId;
        this.paidBy = paidBy;
        this.amount = new BigDecimal(String.valueOf(amount));
        this.participants = participants;

        BigDecimal total = new BigDecimal(String.valueOf(amount));
        BigDecimal count = new BigDecimal(participants.size());

        this.splitAmount = total.divide(count,2, RoundingMode.HALF_UP);
    }
}