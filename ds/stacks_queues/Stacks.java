package ds.stacks_queues;

import java.util.ArrayDeque;
import java.util.Deque;

public class Stacks {

    public static void main(String[] args) {

        MinStack minStack = new MinStack();
        minStack.push(5);
        minStack.push(3);
        minStack.push(7);
        minStack.push(2);
        System.out.println("Current Min: " + minStack.getMin()); // Should print 2
        minStack.pop();
        System.out.println("Current Min after popping: " + minStack.getMin()); // Should print 3
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
