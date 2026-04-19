import java.util.Map;

public class prefix_sum {
    public static void main(String[] args) {

        System.out.println(longest_subarray_with_atomost_k_distinct("araaci", 2));
    }

    /** 
        Prefix Sum Q2 — Subarrays Divisible by K
        Given an integer array and integer k, return the count of subarrays whose sum is divisible by k."
        Input:  arr = [4, 5, 0, -2, -3, 1], k = 5
        Output: 7

        Input:  arr = [5, 10, 15], k = 5
        Output: 6

        Input:  arr = [1, 2, 3], k = 7
        Output: 0
    ```
    */
    public static int subarraysDivisbleByK(int[] arr, int k) {
        int count =0 ;
        int currentSum = 0;
        Map<Integer, Integer> prefixRemainderCount = new HashMap<>();
        prefixRemainderCount.put(0, 1);

        for (int num : arr) {   
            currentSum += num; // ✅ build prefix sum
            
            // Normalise to handle negative numbers in Java
            int remainder = ((currentSum % k) + k) % k;

            // Each previous occurrence of same remainder = one valid subarray
            count += remainderCount.getOrDefault(remainder, 0);

            // Store remainder — same variable, consistent
            remainderCount.merge(remainder, 1, Integer::sum);
           
        }
        return count;
    }

    /** 
        ## Question 1 — Subarray Sum Equals K
        Given an integer array and integer `k`, return the count of subarrays whose sum equals k."**

        ```
        Input:  arr = [1, 2, 3, 2, 1, 5], k = 5
        Output: 3   ([2,3], [3,2], [5])

        Input:  arr = [1, 1, 1], k = 2
        Output: 2   ([1,1] twice)

        Input:  arr = [-1, 2, 3, -2, 4], k = 3
        Output: 3   (works with negatives too)
    ```
    */
    public static int subarraysSumEqualsK(int[] arr, int k){
        int count = 0;
        int currentSum = 0;
        Map<Integer, Integer> prefixSumCount = new HashMap<>();
        prefixSumCount.put(0, 1); // ✅ sentinel for subarrays starting at index 0

        for (int num : arr){
            currentSum += num;  // ✅ build prefix sum as you go

            // Check if there's a prefix sum that would make currentSum - prefixSum = k
            int neededPrefixSum = currentSum - k; // ✅ rearranged equation
            count += prefixSumCount.getOrDefault(neededPrefixSum, 0);  // ✅ count valid subarrays

            // Record the current prefix sum
            prefixSumCount.merge(currentSum, 1, Integer::sum); // ✅ store after lookup
        }

        return count;

     }

}



