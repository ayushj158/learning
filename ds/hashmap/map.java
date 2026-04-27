package ds.hashmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class map {

    public static void main(String[] args) {
        // int[] nums = new int []{2, 7, 11, 15};
        // int[] result = twoSumMap(nums, 17);
        // System.out.println("Indices: " + result[0] + ", " + result[1]);

        // String[] keys = new String[]{"eat","tea","tan","ate","nat","bat"};
        // System.out.println(anagrams(keys));

        // int [] keys = new int[]{100, 4, 200, 1, 3, 2,201,202,204,203,205,206};
        // System.out.println(longestConsequtiveSubsequence(keys));

        // int[] keys = new int[]{1,1,1,2,2,3};
        // System.out.println(Arrays.toString(topKFrequentElements(keys,2)));
        // int[] keys = new int[]{10, 2, -2, -20, 10};
        // // int[] keys = new int[]{9, 4, 20, 3, 10, 5};
        // // int [] keys = new int[]{1, 3, 5};
        // System.out.println(subarraySum(keys, -10));

        // int[] keys = {4, 5, 0, -2, -3, 1};
        // System.out.println(subarrayDivisbleByK(keys,5));

        System.out.println(longestSubstringKDistinct("araaci",2));
    }

    public static int longestSubstringKDistinct(String s, int k) {
        if (s==null || s.isEmpty() || k==0) return 0;
        Map<Character,Integer> window = new HashMap<>();
        int left = 0;
        int maxLen = 0;

        for (int right=0 ;right<s.length();right++){
             // 1. add incoming character
            char c = s.charAt(right);
            window.merge(c, 1, Integer::sum);

            // 2. shrink from left while invalid
            while (window.size()>k) {
                char leftChar = s.charAt(left);
                window.merge(leftChar, -1, Integer::sum);
                if(window.get(leftChar)==0) window.remove(leftChar);
                left++;
            }
            maxLen = Math.max(maxLen, right-left+1);
        }

        return maxLen;
    }
    public static int subarrayDivisbleByK(int[] arr, int k) {
        Map<Integer, Integer> prefixSum = new HashMap<>();
        int currSum = 0;
        int result = 0;

        // map lookup handles it automatically — no special case needed
        prefixSum.put(0,1); //sentinel// currSum == k means currSum - k = 0 sentinel already has {0:1}
       
        for (int i=0; i< arr.length; i++){
            currSum += arr[i];
            int remainder = ((currSum%k) + k)%k; //to handle -ve remainders like -3/%5 should still add 2 as remainder
            result+= prefixSum.getOrDefault(remainder, 0);
            // if (prefixSum.containsKey(currSum -k)) {
            //     System.out.println("Found another prefix sum="+ currSum +" at index="+i);
            //     result+=prefixSum.get(currSum-k);
            // }
            prefixSum.merge(remainder, 1, (oldVal,newVal) -> oldVal+newVal);
        }
       
        return result;
    }
    public static int[] topKFrequentElements(int[] items, int topK) {
        Map<Integer, Integer> keyToFreq = new HashMap<>();
        for (int item : items) {
            keyToFreq.compute(item, (k, v) -> v == null ? 1 : v + 1);
        }

        // Step 2 — min heap of size k, ordered by frequency
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>((a, b) -> b[1] - a[1]); // max heap
        // each element: int[]{value, frequency}

        for (Map.Entry<Integer, Integer> entry : keyToFreq.entrySet()) {
            maxHeap.offer(new int[] { entry.getKey(), entry.getValue() });
        }

        // Step 3 — extract results
        int[] result = new int[topK];
        for (int i = 0; i < topK; i++) {
            result[i] = maxHeap.poll()[0]; // poll gives least frequent first
        }

        return result;
    }
    public static int subarraySum(int[] arr, int k) {
        Map<Integer, Integer> prefixSum = new HashMap<>();
        int currSum = 0;
        int result = 0;

        // map lookup handles it automatically — no special case needed
        prefixSum.put(0,1); //sentinel// currSum == k means currSum - k = 0 sentinel already has {0:1}
       
        for (int i=0; i< arr.length; i++){
            currSum += arr[i];
            result+= prefixSum.getOrDefault(currSum-k, 0);
            // if (prefixSum.containsKey(currSum -k)) {
            //     System.out.println("Found another prefix sum="+ currSum +" at index="+i);
            //     result+=prefixSum.get(currSum-k);
            // }
            prefixSum.merge(currSum, 1, (oldVal,newVal) -> oldVal+newVal);
        }
       
        return result;
    }
    public static List<List<String>> anagrams(String[] keys) {
        if (keys == null || keys.length == 0) return new ArrayList<>();

        Map<String, List<String>> map = new HashMap<>();
        for (String key: keys){
            char[] chars = key.toCharArray(); 
            Arrays.sort(chars);
            String sorted = new String(chars);
            map.computeIfAbsent(sorted, k -> new ArrayList<>()).add(key);
        }

        // for (Map.Entry<String, List<String>> entry: map.entrySet()){
        //     result.add(entry.getValue());
        // }

        return new ArrayList<>(map.values());
    }
    public static int longestConsequtiveSubsequence(int[] arr){
        int maxLength = 0;
        Set<Integer> set = new HashSet<>();
        for (int item: arr) set.add(item);

        for (int item: arr){
            if (!set.contains(item-1)){  // ✅ only start from sequence starts
                int length = 1;
                int next = item+1;
                while(set.contains(next)){
                    next+=1;
                    length++;
                }
                maxLength = Math.max(length,maxLength);
            }
        }
        return maxLength;
    }
    public static int[] twoSumMap(int[] nums, int target) {
        if (nums == null || nums.length < 2) return new int[]{-1, -1};
        Map<Integer, Integer> map = new HashMap<>();
        for (int i=0 ; i <nums.length; i++){
            int complement = target-nums[i];
            if (map.containsKey(complement)) return new int[]{map.get(complement), i};
        
            map.put(nums[i], i);
        }
        return new int[]{-1, -1};
    }
    public static int[] twoSum(int[] nums, int target) {
        int start =0;
        int end = nums.length-1;
        while (start < end) {
            int sum = nums[start] + nums[end];
            if (sum == target) 
                return new int[]{start, end};
            else if (sum < target) start++;
            else end--;
        }
        
        return new int[]{-1, -1};
    }
}
