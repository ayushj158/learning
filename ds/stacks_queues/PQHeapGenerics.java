package ds.stacks_queues;

import java.util.Arrays;
import java.util.Comparator;


public class PQHeapGenerics<T> {

    private int capacity;
    private Object[] items;
    private int size;
    Comparator<T> comparator;

     public PQHeapGenerics(int capacity, Comparator<T> comparator) {
        this.size = 0;
        this.capacity = capacity;
        this.comparator = comparator;
        items = new Object[capacity];
    }

    public static void main(String[] args) {
        // Min heap of integers
        PQHeapGenerics<Integer> pq = new PQHeapGenerics<>(5, (a, b) -> a - b);
        // Max heap of integers
        PQHeapGenerics<Integer> maxHeap = new PQHeapGenerics<>(10, (a, b) -> b - a);
        // Min heap of strings by length
        PQHeapGenerics<String> byLength = new PQHeapGenerics<>(10, (a, b) -> a.length() - b.length());
        // Min heap of ListNode by val — exactly what merge k lists needs
        //PQHeapGenerics<ListNode> nodeHeap = new PQHeapGenerics<>(10, (a, b) -> a.val - b.val);

        pq.add(5);
        pq.add(3);
        pq.add(2);
        pq.add(4);
        pq.add(1);

        System.out.println(Arrays.toString(pq.items));

        System.out.println(pq.poll());
        System.out.println(pq.poll());
        System.out.println(Arrays.toString(pq.items));
    }

    public void add(T item) {
        if (size == capacity) {
            throw new IllegalStateException();
        }
        items[size] = item;
        heapifyUp(size);
        size++;
    }

    public T poll() {
        if (size == 0) throw new IllegalStateException();
        T item = get(0);
        items[0] = items[size - 1];
        items[size - 1] = null;
        size--;
        heapifyDown(0);
        
        return item;
    }

    public T peek() {
        if (size == 0) throw new IllegalStateException();
        return get(0);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return this.size;
    }

    public void print() {
        System.out.println(items);
    }

    private void heapifyUp(int index) {
        if (index == 0) return;         
        int parent = parent(index);
        if (comparator.compare(get(parent), get(index)) > 0) {
            swap(index, parent);
            heapifyUp(parent);
        }
    }

    private void heapifyDown(int parent) {
        int left = leftChild(parent);
        int right = rightChild(parent);
        int smallest = parent;

        if (left < size && comparator.compare(get(smallest), get(left)) > 0) {
            smallest = left;
        }
        if (right < size && comparator.compare(get(smallest), get(right)) > 0) {
            smallest = right;
        }

        if (smallest != parent) {
            swap(parent, smallest);
            heapifyDown(smallest);
        }
    }

    private void swap(int first, int second) {
        Object temp = items[first];
        items[first] = items[second];
        items[second] = temp;
    }
    
    @SuppressWarnings("unchecked")
    private T get(int index) {
        return (T) items[index];
    }
    private int parent(int index) {return (index - 1) / 2;}
    private int leftChild(int index) {return 2 * index + 1;}
    private int rightChild(int index) {return 2 * index + 2;}
    
}
