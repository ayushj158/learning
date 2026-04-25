package ds.stacks_queues;

import java.util.Arrays;

public class PQHeap {

    private int capacity;
    private int[] items;
    private int size;

    public static void main(String[] args) {
        PQHeap pq = new PQHeap(5);
        pq.add(5);
        pq.add(3);
        pq.add(2);
        pq.add(4);
        pq.add(1);

        System.out.println(Arrays.toString(pq.items));

        System.out.println(pq.poll());
        System.out.println(pq.poll());
        System.out.println(Arrays.toString(pq.items));

        // while (!pq.isEmpty()){
        // System.out.println(pq.poll());
        // }
    }

    public PQHeap(int capacity) {
        this.size = 0;
        this.capacity = capacity;
        items = new int[capacity];

    }

    public void add(int item) {
        if (size == capacity) {
            throw new IllegalStateException();
        }
        items[size] = item;
        heapifyUp(size);
        size++;
    }

    public int poll() {
        if (size == 0) throw new IllegalStateException();
        int item = items[0];
        items[0] = items[size - 1];
        items[size - 1] = -1;
        size--;
        heapifyDown(0);
        
        return item;
    }

    public int peek() {
        if (size == 0) throw new IllegalStateException();
        return items[0];
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
        if (items[parent] > items[index]) {
            swap(index, parent);
            heapifyUp(parent);
        }
        // while (items[parent] > items[index]){
        // swap(index, parent);
        // index = parent;
        // parent = parent(index);
        // }
    }

    private void heapifyDown(int parent) {
        int left = leftChild(parent);
        int right = rightChild(parent);
        int smallest = parent;

        if (left < size && items[smallest] > items[left]) {
            smallest = left;
        }
        if (right < size && items[smallest] > items[right]) {
            smallest = right;
        }

        if (smallest != parent) {
            swap(parent, smallest);
            heapifyDown(smallest);
        }
    }

    private void swap(int first, int second) {
        int temp = items[first];
        items[first] = items[second];
        items[second] = temp;
    }

    private int parent(int index) {
        return (index - 1) / 2;
    }

    private int leftChild(int index) {
        return 2 * index + 1;
    }

    private int rightChild(int index) {
        return 2 * index + 2;
    }

}
