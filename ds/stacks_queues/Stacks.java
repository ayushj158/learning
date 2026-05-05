package ds.stacks_queues;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class Stacks {

    public static void main(String[] args) {

        // MinStack minStack = new MinStack();
        // minStack.push(5);
        // minStack.push(3);
        // minStack.push(7);
        // minStack.push(2);
        // System.out.println("Current Min: " + minStack.getMin()); // Should print 2
        // minStack.pop();
        // System.out.println("Current Min after popping: " + minStack.getMin()); // Should print 3
        // int[] temps = new int[]{73, 74, 75, 71, 69, 72, 76, 73};
        // System.out.println(Arrays.toString(monotonicStackTemprature(temps)));

        int[] arr = new int[]{100, 80, 60, 70, 60, 75, 85};
        System.out.println(Arrays.toString(monotonicStackStockSpan(arr)));

        
    }
    /**
     * "For each day's stock price, find the span — the number of consecutive days immediately before today 
     * (including today) where the price was less than or equal to today's price."
        Input:  [100, 80, 60, 70, 60, 75, 85]
        Output: [1,   1,  1,  2,  1,  4,  6]
     * @param arr
     * @return
     */
    public static int[] monotonicStackStockSpan(int[] arr){
        int [] result = new int [arr.length];
        Arrays.fill(result, 1);
        Deque<Integer> waiting = new ArrayDeque<>();

        for (int i=0;i<arr.length;i++) {
            while(!waiting.isEmpty()&& arr[i]>= arr[waiting.peek()]){
                waiting.pop();
            }
            result[i]= waiting.isEmpty()? i+1: i-waiting.peek();
            waiting.push(i);
        }
        return result;
    }

     /**
     * "Given an array of daily temperatures, return an array where each element is the number of days you have to wait until a warmer temperature. If no warmer day exists, put 0."
        Input:  [73, 74, 75, 71, 69, 72, 76, 73]
        Output: [1,  1,  4,  2,  1,  1,  0,  0]

        Explanation:
        Day 0 (73°): next warmer is day 1 (74°) → wait 1 day
        Day 1 (74°): next warmer is day 2 (75°) → wait 1 day
        Day 2 (75°): next warmer is day 6 (76°) → wait 4 days
        Day 3 (71°): next warmer is day 5 (72°) → wait 2 days
        ...
        Day 6 (76°): no warmer day → 0
        Day 7 (73°): no warmer day → 0
     * @param temps
     * @return
     */
    public static int[] monotonicStackTemprature(int[] temps){
        int [] result = new int [temps.length];
        Arrays.fill(result, 0);
        Deque<Integer> waiting = new ArrayDeque<>();

        for (int i=0;i<temps.length;i++) {
            while(!waiting.isEmpty()&& temps[i]> temps[waiting.peek()]){
                int index = waiting.pop();
                result[index] = i-index; //no of days it will get warmer,✅ distance not value
            }
            waiting.push(i);
        }
        return result;
    }

    
    public static boolean isValidBrackets(String s){

        Deque<Character> stack = new ArrayDeque<>();

        for (Character c : s.toCharArray()){
            if (c == '(' || c == '{' || c == '['){
                stack.push(c);
            } else {
                if (stack.isEmpty()) return false;
                Character top = stack.peek();
                if (c == ')' && top == '(' || c == '}' && top == '{' || c == ']' && top == '['){
                    stack.pop();
                } else {
                    return false;
                }
            }
        }

        return true;
    }
    
}
