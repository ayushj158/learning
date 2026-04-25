package ds.trees;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.List;

public class BST {

    public static void main (String[] args){
        TreeNode root = new TreeNode(8);
        insert(root, 3);
        insert(root, 1);
        insert(root, 10);
        insert(root, 6);

        List<List<Integer>> levelOrder = printLevelOrder(root);
        TreeNode node = search(root, 100);

        if (levelOrder != null) {
             System.out.println(levelOrder);
        } else {
            System.out.println("The tree is empty.");
        }

        if (node != null) {
            System.out.println(node.data); // Should print 6
        } else {
            System.out.println("Key not found in the BST.");
        }
    }

    public static boolean validateBST(TreeNode root) {


        return false;
    }

    public static int height(TreeNode root) {
        if (root == null)return 0;

        return 1 + Math.max(height(root.left), height(root.right));
    }

    public static TreeNode search(TreeNode root, int key) {
        TreeNode current = root;
        while (current != null) {
            if (current.data == key) return current;
            if (current.data > key) current = current.left;
            else current = current.right;
        }
        return null;
    }
    public static TreeNode insert(TreeNode root, int key) {
        TreeNode newNode = new TreeNode(key);
        if (root == null) return newNode;
       
        TreeNode current = root;
        while (true) {
            if (key <= current.data){
                if (current.left ==null){
                    current.left = newNode;
                    return root;
                }
                current = current.left;
            } else {
                if (current.right == null){
                    current.right = newNode;
                    return root;
                }
                current = current.right;
            }
        }
    }

    public static List<List<Integer>> printLevelOrder(TreeNode root) {
        if (root == null) return null;

        List<List<Integer>> result = new ArrayList<List<Integer>>();
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);

        while (!queue.isEmpty()) {
            int level = queue.size();
            List<Integer> levelList = new ArrayList<>();
            for (int i = 0; i < level; i++) {
                TreeNode node = queue.poll();
                levelList.add(node.data);
                if (node.left != null) queue.offer(node.left);
                if (node.right != null) queue.offer(node.right);
            }
            result.add(levelList);
        }
        return result;
    }

    public static class TreeNode {
        int data;
        TreeNode left;
        TreeNode right;

        public TreeNode(int data) {
            this.data = data;
            this.left = null;
            this.right = null;
        }

        public TreeNode(int data, TreeNode left, TreeNode right) {
            this.data = data;
            this.left = left;
            this.right = right;
        }

        public int getData() {
            return data;
        }

        public void left(TreeNode node) {
            this.left = node;
        }

        public void right(TreeNode node) {
            this.right = node;
        }
    }

}
