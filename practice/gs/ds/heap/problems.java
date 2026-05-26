package heap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class problems {

    public static void main(String[] args) {

        System.out.println(Arrays.toString(topKTallest(new int[]{3,1,4,1,5,9,2,6}, 3)));
        
    }

    /**
     * GS Custom — Top K Tallest Buildings
        Given a list of building heights, return indexes of top K tallest. Same height → lower index first. Return from tallest to shortest.

        Input:  heights = [3,1,4,1,5,9,2,6], k = 3
        Output: [5, 7, 4]
        → heights[5]=9, heights[7]=6, heights[4]=5
     * @param heights
     * @param k
     * @return
     */
    public static int[] topKTallest(int[] heights, int k){

        Map<Integer,Integer> map = new HashMap<>();
        PriorityQueue<Integer> minHeap  = new PriorityQueue<>((a,b) -> {
            // different heights store in min heap in asc order
            if(heights[a] != heights[b]) return Integer.compare(heights[a], heights[b]);
            return Integer.compare(b, a); // same height → higher index evicted
        });

        for(int i=0 ; i< heights.length; i++){  
            minHeap.offer(i);  // store INDEX in heap
            if(minHeap.size() > k){
                minHeap.poll();  // evict lowest height (or higher index if tie)
            }
        }
        return  minHeap.stream()
                       .sorted((a,b)-> {
                            if(heights[a] != heights[b]) return Integer.compare(heights[b], heights[a]); // height desc
                            return Integer.compare(a, b);  // same height → lower index first
                       })
                       .mapToInt(num -> num)
                       .toArray();
    }

}
