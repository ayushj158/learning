import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class killprocess {

 public static void main(String[] args) {
        // Test 1
        List<Integer> pid  = Arrays.asList(1, 3, 10, 5);
        List<Integer> ppid = Arrays.asList(3, 0, 5,  3);
        int kill = 5;
        System.out.println(killProcess(pid, ppid, kill));
    }


     public static List<Integer> killProcess(List<Integer> pid,
                                             List<Integer> ppid,
                                             int kill) {
        // step 1: build parent → children map
        Map<Integer, List<Integer>> children = new HashMap<>();

       for (int i = 0; i < pid.size(); i++) {
            children.computeIfAbsent(ppid.get(i) ,
                                     v -> new ArrayList<>())
                    .add(pid.get(i));
       }
       List<Integer> result = new ArrayList<>();
       Queue<Integer> queue = new LinkedList<>();
       queue.offer(kill);

       while(!queue.isEmpty()){
            int process = queue.poll();
            result.add(process);
            queue.addAll(children.getOrDefault(process, new ArrayList<>()));
       }
       int[] arr = new int[2];
       Arrays.stream(arr).sum();

       return result;
    }
}
