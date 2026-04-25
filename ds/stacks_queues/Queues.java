package ds.stacks_queues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Queues {

    public static void main(String[] args) {
        // QueueUsingStacks queue = new QueueUsingStacks();
        // queue.enqueue(1);
        // queue.enqueue(2);
        // queue.enqueue(3);
        // System.out.println(queue.dequeue()); // Should print 1
        // System.out.println(queue.dequeue()); // Should print 2
        // System.out.println(queue.dequeue()); // Should print 3
        // queue.enqueue(4);
        // System.out.println(queue.dequeue()); // Should print 4

        // int[] prices = {8, 4, 6, 2, 3};
        // int[] result = monotonicStackStocksTrade(prices);
        // System.out.println(Arrays.toString(result)); // Should print [-1, 6, -1, 3,
        // -1]

        // TreeNode root = new TreeNode(1);
        // TreeNode left = new TreeNode(2);
        // root.left(left);
        // TreeNode leftLeft = new TreeNode(4);
        // left.left(leftLeft);
        // TreeNode leftRight = new TreeNode(5);
        // left.right(leftRight);
        // TreeNode right = new TreeNode(3);
        // root.right(right);
        // TreeNode rightLeft = new TreeNode(6);
        // right.left(rightLeft);
        // TreeNode rightRight = new TreeNode(7);
        // right.right(rightRight);

        // System.out.println(bfsLevelOrderTraversal(root)); // Should print [[1], [2,
        // 3], [4, 5, 6, 7]]
    }

    /**
     * Question — Level Order Traversal
     * "Given a binary tree, return its level order traversal as a list of lists —
     * each inner list contains the values at that level."
     * Input:
     * 3
     * / \
     * 9 20
     * / \
     * 15 7
     * 
     * Output: [[3], [9,20], [15,7]]
     * 
     * Input: [1]
     * Output: [[1]]
     * 
     * Input: null
     * Output: []
     * Constraints: O(n) time, O(n) space.
     * 
     * @param root
     * @return
     */
    public static List<List<Integer>> bfsLevelOrderTraversal(TreeNode root) {
        Queue<TreeNode> queue = new LinkedList<TreeNode>();
        List<List<Integer>> result = new ArrayList<List<Integer>>();
        if (root == null)
            return result;

        queue.offer(root);
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            List<Integer> currentLevelElements = new ArrayList<Integer>();
            for (int i = 0; i < levelSize; i++) {
                TreeNode node = queue.poll();
                currentLevelElements.add(node.val);
                if (node.left != null)
                    queue.offer(node.left);
                if (node.right != null)
                    queue.offer(node.right);
            }
            result.add(currentLevelElements);
        }
        return result;
    }

    public static class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;

        public TreeNode(int val) {
            this.val = val;
        }

        public void left(TreeNode left) {
            this.left = left;
        }

        public void right(TreeNode right) {
            this.right = right;
        }

        public boolean isLeaf() {
            return left == null && right == null;
        }
    }

    /**
     * You have daily stock prices. For each day, you want to know: "When is the
     * next day the price will be higher than today?"
     * prices = [2, 1, 5, 3, 6]
     * days = 0 1 2 3 4
     * 
     * Day 0, price=2: next higher price is 5 on day 2
     * Day 1, price=1: next higher price is 5 on day 2
     * Day 2, price=5: next higher price is 6 on day 4
     * Day 3, price=3: next higher price is 6 on day 4
     * Day 4, price=6: no higher price after → -1
     * This is exactly "next greater element". Real use case — trading systems,
     * Goldman literally has this in production.
     * 
     * @param prices
     * @return
     */
    public static int[] monotonicStackStocksTrade(int[] prices) {
        Deque<Integer> waiting = new java.util.ArrayDeque<>();
        int[] result = new int[prices.length];
        Arrays.fill(result, -1);

        for (int i = 0; i < prices.length; i++) {
            while (!waiting.isEmpty() && prices[i] > prices[waiting.peek()]) {
                int index = waiting.pop();
                result[index] = prices[i];
            }
            waiting.push(i);
        }
        return result;
    }

    /**
     * # Stacks Q3 — Implement Queue Using Two Stacks
     * 
     * ## The Problem
     ** 
     * "Implement a queue using only two stacks. The queue must support enqueue,
     * dequeue and peek — all amortized O(1)."**
     * 
     * ```
     * MyQueue queue = new MyQueue();
     * queue.enqueue(1);
     * queue.enqueue(2);
     * queue.enqueue(3);
     * queue.peek(); // 1 ← first in
     * queue.dequeue(); // 1
     * queue.dequeue(); // 2
     * queue.enqueue(4);
     * queue.dequeue(); // 3
     * queue.dequeue(); // 4
     * ```
     ** Constraints:** O(1) amortized per operation. Only stack operations allowed —
     * push, pop, peek, isEmpty.
     * ## The Amortized O(1) Argument
     * 
     * Each element is:
     * - Pushed to inbox once
     * - Poured to outbox once
     * - Popped from outbox once
     * 
     * Total: 3 operations per element regardless of n. That's O(1) amortized — not
     * every operation is O(1), but averaged across all operations the cost is O(1)
     * per element.
     * 
     * ---
     * 
     * ## Now Write It
     ** Things to think through:**
     * - When do you pour from inbox to outbox?
     * - What if you enqueue while outbox still has elements?
     * - What is the isEmpty condition?
     * - What do dequeue and peek do differently?
     * 
     * Post your solution when ready.
     */
    public static class QueueUsingStacks {

        Deque<Integer> inbox = new java.util.ArrayDeque<>();
        Deque<Integer> outbox = new java.util.ArrayDeque<>();

        public void enqueue(int val) {
            inbox.push(val);
        }

        public int dequeue() {
            pourIfNeeded();
            if (outbox.isEmpty()) {
                throw new RuntimeException("Queue is empty");
            }
            return outbox.pop();
        }

        public int peek() {
            pourIfNeeded();
            if (outbox.isEmpty()) {
                throw new RuntimeException("Queue is empty");
            }
            return outbox.peek();
        }

        public boolean isEmpty() {
            return inbox.isEmpty() && outbox.isEmpty();
        }

        private void pourIfNeeded() {
            if (outbox.isEmpty()) {
                while (!inbox.isEmpty()) {
                    outbox.push(inbox.pop());
                }
            }
        }
    }
}
