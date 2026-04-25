package ds.stacks_queues;

import java.util.ArrayDeque;
import java.util.Deque;

public class MinStack {

    // private int[] valueStack;
    // private int[] minStack;
    // private int top;

    // public MinStack(int maxSize) {
    //     valueStack = new int[maxSize];
    //     minStack = new int[maxSize];
    //     top = -1;
    // }

    // public void push(int val) {
    //     top++;
    //     valueStack[top] = val;

    //     if (top == 0) {
    //         minStack[top] = val;
    //     } else {
    //         minStack[top] = Math.min(val, minStack[top - 1]);
    //     }
    // }

    // public void pop() {
    //     if (top < 0) {
    //         throw new RuntimeException("Stack is empty");
    //     }
    //     top--;
    // }

    // public int peek() {
    //     if (top < 0) {
    //         throw new RuntimeException("Stack is empty");
    //     }
    //     return valueStack[top];
    // }

    // public int getMin() {
    //     if (top < 0) {
    //         throw new RuntimeException("Stack is empty");
    //     }

    //     return minStack[top];
    // }


    Deque<Integer> valueStack = new ArrayDeque<>();
    Deque<Integer> minStack = new ArrayDeque<>();

    public void push(int val) {
        valueStack.push(val);
        int min = minStack.isEmpty() ? val : Math.min(val, minStack.peek());
        minStack.push(min);
    }

    public void pop() {
        valueStack.pop();
        minStack.pop();
    }

    public int peek() {return valueStack.peek();}
    public int getMin() {return minStack.peek();}

}
