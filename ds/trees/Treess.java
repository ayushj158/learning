package ds.trees;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Treess {

    private TreeNode root;

    public static void main(String[] args) {
        Treess tree = new Treess();
        tree.root = new TreeNode(1);
        tree.root.left = new TreeNode(2);
        tree.root.right = new TreeNode(3);
        tree.root.left.left = new TreeNode(4);
        tree.root.left.right = new TreeNode(5);

        List<Integer> inorderList = tree.inorder(tree.root, new java.util.ArrayList<>());
        System.out.println("Inorder Traversal: " + inorderList);

        List<Integer> preorderList = tree.preorder(tree.root, new java.util.ArrayList<>());
        System.out.println("Preorder Traversal: " + preorderList);

        List<Integer> postorderList = tree.postorder(tree.root, new java.util.ArrayList<>());
        System.out.println("Postorder Traversal: " + postorderList);


        List<Integer> inorderList1 = tree.inorderIterative(tree.root);
        System.out.println("Inorder Traversal Iterative: " + inorderList1);

        List<Integer> preorderList1 = tree.preOrderIterative(tree.root);
        System.out.println("Preorder Traversal Iterative: " + preorderList1);

        // List<Integer> postorderList1 = tree.postorder(tree.root, new java.util.ArrayList<>());
        // System.out.println("Postorder Traversal: " + postorderList1);
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
        public int getData() {return data;}
        public void left(TreeNode node) {this.left = node;}
        public void right(TreeNode node) {this.right = node;}
    }

    public List<Integer> inorder(TreeNode root, List<Integer> list) {
        if (root == null) return list;
        inorder(root.left, list);
        list.add(root.data);
        inorder(root.right, list);
        return list;
    }

    public List<Integer> inorderIterative(TreeNode root) {
        List<Integer> result = new ArrayList<>();
        if (root == null) return result;
       
        Deque<TreeNode> stack = new ArrayDeque<>();
        TreeNode curr = root;

         while (curr !=null || !stack.isEmpty()) {

            while (curr !=null) {
                stack.push(curr);
                curr = curr.left;
            }
            curr = stack.pop();
            result.add(curr.data);
            curr = curr.right;       
         }

        return result;
    }

    public List<Integer> preorder(TreeNode root, List<Integer> list) {
        if (root == null) return list;
        list.add(root.data);
        preorder(root.left, list);
        preorder(root.right, list);
        return list;
    }

    public List<Integer> preOrderIterative(TreeNode root) {
        if (root == null) return new java.util.ArrayList<>();
        
        List<Integer> result = new ArrayList<>();
        Deque<TreeNode> stack = new ArrayDeque<>();
        stack.push(root);

         while (!stack.isEmpty()) {
            TreeNode curr = stack.pop();
            result.add(curr.data);
            if (curr.right != null) stack.push(curr.right);  // 
            if (curr.left  != null) stack.push(curr.left);   // 
            
         }

        return result;
    }

     public List<Integer> postorder(TreeNode root, List<Integer> list) {
        if (root == null) return list;
        postorder(root.left, list);
        postorder(root.right, list);
        list.add(root.data);
        return list;
    }
     
}