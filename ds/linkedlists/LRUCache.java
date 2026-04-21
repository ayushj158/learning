import java.util.HashMap;

public class LRUCache {

    private HashMap<Integer, ListNode> map;
    private DoublyLinkedList list;
    private int capacity;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.list = new DoublyLinkedList();
    }

    public int get(int key) {
        if (!map.containsKey(key)) {
            System.out.println("GET - Key does not exist key=" + key);
            return -1;
        }
        ListNode keyNode = map.get(key);
        list.moveToFront(keyNode);

        return keyNode.value;
    }

    public void put(int key, int value) {
        if (map.containsKey(key)) {
            // Update existing node
            ListNode existing = map.get(key);
            existing.value = value;
            list.moveToFront(existing);
        return;
    }
        if (list.size >= capacity) {
            System.out.println("ADD - Starting keys removals as size:" + list.size + " is equal or more than capacity:" + capacity);
            int tailKey = list.removeTail();
            map.remove(tailKey);
            System.out.println("removed key: " + tailKey);
        }
        ListNode newNode = list.addToFront(key, value);
        map.put(key, newNode);
    }

    public void printCache() {
        System.out.println("Current cache state:");
        list.printList();
    }

    private class ListNode {
            public int key;
            public int value;
            public ListNode prev;
            public ListNode next;

            public ListNode(int key, int value) {
                this.key = key;
                this.value = value;
            }

            public ListNode(int key, int value, ListNode prev, ListNode next) {
                this.key = key;
                this.value = value;
                this.prev = prev;
                this.next = next;
            }
        }

    private class DoublyLinkedList { 
            public ListNode head;
            public ListNode tail;
            public int size;

            public DoublyLinkedList(){
                this.size = 0;
                this.head = null;
                this.tail = null;
            }

            public ListNode addToFront(int key, int val) {
                ListNode newNode = new ListNode(key,val);

                if (head == null) {
                    head = newNode;
                    tail = newNode;
                } else {
                newNode.next = head;
                head.prev = newNode;
                newNode.prev = null;
                head=newNode;
                }
                this.size++;
                return newNode;
            }

            public void moveToFront(ListNode node) {
                if (node == head) return;

                if (node.prev != null) {
                    node.prev.next = node.next;
                }
                if (node.next != null) {
                    node.next.prev = node.prev;
                }
                if (node == tail) {
                    tail = node.prev;
                }

                node.prev = null;
                node.next = head;
                if (head != null) {
                    head.prev = node;
                }
                head = node;

                if (tail == null) {
                    tail = head;
                }

                System.out.println("GET - Resetted the key to come in front key=" + node.key);
            }

            public int removeTail(){
                if (tail == null) return -1;

                int key = tail.key;
                if (tail.prev != null) {
                    tail.prev.next = null;
                } else {
                    head = null;
                }
                tail = tail.prev;
                this.size--;
                return key;
            } 

            public void printList() {
                ListNode temp = head;
                while(temp != null) {
                    System.out.println("Key=" + temp.key + ", Value=" + temp.value);
                    temp = temp.next;
                }
            }
    }
}