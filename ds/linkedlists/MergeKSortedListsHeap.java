import java.util.PriorityQueue;

public class MergeKSortedListsHeap {

    public static void main(String[] args) {

        Linkedlists.LinkedList list1 = new Linkedlists.LinkedList();
        Linkedlists.LinkedList list2 = new Linkedlists.LinkedList();
        Linkedlists.LinkedList list3 = new Linkedlists.LinkedList();
        list1.add(1);
        list1.add(4);
        list1.add(7);
        list2.add(2);
        list2.add(5);
        list2.add(8);
        list3.add(3);
        list3.add(6);
        list3.add(9);

        Linkedlists.ListNode mergedhead = mergeKLists(new Linkedlists.ListNode[]{list1.head, list2.head, list3.head});

        while (mergedhead != null) {
            System.out.println(mergedhead.val);
            mergedhead = mergedhead.next;
        }

    }

    public static Linkedlists.ListNode mergeKLists(Linkedlists.ListNode[] lists) {

        Linkedlists.ListNode dummy = new Linkedlists.ListNode(-1);
        Linkedlists.ListNode current = dummy;

        PriorityQueue<Linkedlists.ListNode> minHeap = new PriorityQueue<>((a, b) -> a.val - b.val);

        for (Linkedlists.ListNode listHead : lists) {
            if (listHead != null) {
                minHeap.add(listHead);
            }
        }

        while (!minHeap.isEmpty()){
            Linkedlists.ListNode smallestInHeap = minHeap.poll();
            if (smallestInHeap.next != null) {
                minHeap.add(smallestInHeap.next);
            }
            current.next = smallestInHeap;
            current = current.next;
        }

        return dummy.next;
       

    }
}
