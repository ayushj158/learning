import java.util.List;

public class linkedlists {
    public static void main(String[] args) {
        // LinkedList list = new LinkedList();
        // list.add(1);
        // list.add(2);
        // list.add(5);
        //list.printList();
        //System.out.println(findNthNode(list.head, 4).val);
        //System.out.println(detectCycle(list.head));
        // System.out.println(findMiddleOfList(list.head).val);
        // list.head = reverseListIterative(list.head);
        // list.printList();


        // LinkedList list2 = new LinkedList();
        // list2.add(1);
        // list2.add(3);
        // list2.add(4);

        // ListNode mergedHead = mergedSortedLists(list.head, list2.head);
        // while (mergedHead != null) {
        //     System.out.println(mergedHead.val);
        //     mergedHead = mergedHead.next;       
        // }

        LRUCache cache = new LRUCache(3);
        cache.put(1, 1);   // [1]
        cache.put(2, 2);   // [2,1]
        cache.put(3, 3);   // [3,2,1]
        cache.get(1);      // [1,3,2]  returns 1
        cache.put(4, 4);   // evict 2, [4,1,3]
        cache.get(2);      // returns -1 (evicted)
        cache.put(1, 100); // update, [1,4,3]
        cache.get(1);      // returns 100
        cache.printCache();
    }

     /**
      * Question 5 — Merge Two Sorted Linked Lists
        "Given two sorted linked lists, merge them into one sorted linked list and return the head."
        Input:  1 → 3 → 5 → null
                2 → 4 → 6 → null
        Output: 1 → 2 → 3 → 4 → 5 → 6 → null

        Input:  1 → 2 → 4 → null
                1 → 3 → 4 → null
        Output: 1 → 1 → 2 → 3 → 4 → 4 → null

        Input:  null, 1 → 2 → null
        Output: 1 → 2 → null
        Constraints: O(n+m) time, O(1) space. Do not create new nodes — reuse existing ones by relinking.
        The trick: Use a dummy head node to avoid special-casing the first node. Then walk both lists, always picking the smaller current node.
        dummy → ?

        l1=[1]→[3]→[5]
        l2=[2]→[4]→[6]

        compare l1 and l2:
        1 < 2 → attach l1, advance l1
        dummy → [1]
        2 < 3 → attach l2, advance l2
        dummy → [1] → [2]
        3 < 4 → attach l1, advance l1
        ...and so on
        Think through:

        What is a dummy node and why does it simplify the code?
        What do you do when one list runs out before the other?

        Post your solution when ready.
      * @param head1
      * @param head2
      * @return
      */
     public static ListNode mergedSortedLists (ListNode head1, ListNode head2) {
        if (head1 ==null) return head2;
        if (head2 ==null) return head1;

        ListNode dummy = new ListNode(-1);
        ListNode current = dummy;

        while (head1!=null && head2!=null){
            if (head1.val <head2.val){
                current.next = head1;
                head1 = head1.next;
            }
            else {
                current.next = head2;
                head2 = head2.next;
            }
            current = current.next;
        }

        if (head1 != null) current.next = head1;
        if (head2 != null) current.next = head2;

        return dummy.next;
     }

    /**
     * Question 4 — Find Nth Node From End
        "Given a linked list and integer n, return the nth node from the end."
        Input:  1 → 2 → 3 → 4 → 5 → null, n = 2
        Output: node 4  (2nd from end)

        Input:  1 → 2 → 3 → null, n = 3
        Output: node 1  (3rd from end = first node)

        Input:  1 → null, n = 1
        Output: node 1
        Constraints: O(n) time, O(1) space. Single pass only.
        The trick: Two pointers again, but this time they start at different positions. Create a gap of exactly n nodes between them. When the front pointer hits null, the back pointer is at the answer.
        n = 2
        [1] → [2] → [3] → [4] → [5] → null

        Step 1: advance fast n steps ahead
        fast moves 2 steps:
        slow=[1], fast=[3]

        Gap of 2 between them.

        Step 2: move both together until fast hits null
        slow=[2], fast=[4]
        slow=[3], fast=[5]
        slow=[4], fast=null → stop

        return slow=[4] ✅ 2nd from end
     * @param head
     * @param N
     * @return
     */
    public static ListNode findNthNode (ListNode head, int N){
        if (head == null) return null;
        ListNode front = head;
        ListNode back = head;

         for (int i= 0 ; i< N;i++){
            if (front != null) front = front.next;
        }

        while(front!=null) {
            back = back.next;
            front = front.next;
        }

        return back;
    }

    private static ListNode advanceNTimes(ListNode node, int N) {

       
        return node;
    }

    /**
     * Detect Cycle in Linked List
        "Given a linked list, return true if it has a cycle, false if not."
        Input:  1 → 2 → 3 → 4 → 2 (node 4 points back to node 2)
        Output: true

        Input:  1 → 2 → 3 → null
        Output: false

        Input:  null
        Output: false
        Constraints: O(n) time, O(1) space.
        The pattern: Same slow-fast pointers. But now instead of stopping at null, you're watching for slow and fast to meet.
        No cycle: fast hits null → return false
        Cycle:    fast laps slow → they point to same node → return true
        The key question to think through: Why must slow and fast always meet inside a cycle — why can't fast permanently stay ahead and never land on slow?
        Think of it like a circular running track:
        fast runner laps slow runner — they MUST meet
        fast gains exactly 1 step on slow per iteration
        gap shrinks by 1 each time → eventually gap = 0 → they meet
     * @param head
     * @return
     */
    public static boolean detectCycle (ListNode head){
        if (head == null || head.next == null)
            return false;

        ListNode slow = head;
        ListNode fast = head;

        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;

            if (slow == fast)
                return true;
        }
        return false;
    }
    
    /**
     * Find Middle of Linked List
        "Given a linked list, return the middle node. If two middle nodes exist, return the second one."
        Input:  1 → 2 → 3 → 4 → 5 → null
        Output: node 3

        Input:  1 → 2 → 3 → 4 → null
        Output: node 3  (second middle)

        Input:  1 → null
        Output: node 1
        Constraints: O(n) time, O(1) space. Single pass only — you cannot count nodes first then traverse again.
        The pattern: Slow-fast pointers. Slow moves 1 step, fast moves 2 steps. When fast hits the end, slow is at the middle.
        [1] → [2] → [3] → [4] → [5] → null
        s
        f

        step 1: s=[2], f=[3]
        step 2: s=[3], f=[5]
        f.next=null → stop → s=[3] is middle ✅
        Think through:

        What is the condition to keep moving — fast != null or fast.next != null?
        How does the even length case naturally give you the second middle?
     * @param head
     * @return
     */
    public static ListNode findMiddleOfList (ListNode head){
        if (head == null || head.next == null) return head;
        ListNode slow = head;
        ListNode fast = head;

        while (fast!=null && fast.next!=null) {
            slow = slow.next;
            fast = fast.next.next;
        }

       return slow;
    }

    public static ListNode reverseListIterative (ListNode head){
        if (head == null || head.next == null) return head;

        ListNode prev = null;
        ListNode current = head;
        ListNode next = null;

        while (current!=null) {
            next = current.next;
            current.next = prev;
            prev = current;
            current = next;
        }

       return prev;
    }

    public static class ListNode{
        ListNode next;
       public int val;

        public ListNode(int val){
            this.val = val;
            this.next = null;
        }

         public ListNode(int val, ListNode next){
            this.val = val;
            this.next = next;
        }
    }

    public static class LinkedList { 
        public ListNode head;
        public ListNode tail;

        public LinkedList(){
            this.head = null;
            this.tail = null;
        }

        public void add(int val) {
            ListNode newNode = new ListNode(val);

            if (head == null) {
                head = newNode;
                tail = newNode;
            } else {
               tail.next = newNode;
               tail = tail.next;
            }
        }

        public void add(int val, ListNode next) {
            ListNode newNode = new ListNode(val, next);

            if (head == null) {
                head = newNode;
                tail = newNode;
            } else {
               tail.next = newNode;
               tail = tail.next;
            }
        }

        public void printList() {
            ListNode temp = head;
            while(temp != null) {
                System.out.println(temp.val);
                temp = temp.next;
            }
        }
    }
}