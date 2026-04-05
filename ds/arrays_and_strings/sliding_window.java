import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class sliding_window {
    public static void main(String[] args) {

        System.out.println(longest_subarray_with_atomost_k_distinct("araaci", 2));
    }

   /** 
    * Q5: Given a binary array and integer k, find the maximum number of consecutive 1s if you can flip at most k zeros."
        Input:  arr = [1,1,0,0,1,1,1,0,1,1], k = 2
        Output: 9   (flip zeros at index 2 and 7)

        Input:  arr = [0,0,1,1,1,0,0,1,1,1], k = 2
        Output: 7   (flip zeros at index 5 and 6)

        Input:  arr = [1,1,1,1,1], k = 0
        Output: 5   (no flips needed)

        Input:  arr = [0,0,0], k = 0
        Output: 0
    */ 
    public static int maxConsecutiveOnesWithKFlips(int[] arr, int k){

        if (arr == null || arr.length == 0) return 0;
        
        int left = 0;
        int maxLen = 0;
        int zeroCount = 0;

        for (int right=0; right<arr.length;right++){
            int current = arr[right];

            if (current == 0){ // ✅ expand — track zeros
                zeroCount++;
            } 

            while (zeroCount>k) { // ✅ shrink while invalid
                if (arr[left] == 0){ // ✅ only decrement if leaving a zero
                    zeroCount--;
                }
                left++;
            }

            maxLen = Math.max(maxLen, right-left+1); // ✅ update after shrink
        }

        return maxLen;
    }


    /**
     * Sliding Window Q4 — Longest Subarray with At Most K Distinct Characters
        "Given a string s and integer k, find the length of the longest substring that contains at most k distinct characters."
        Input:  s = "araaci", k = 2
        Output: 4   ("araa")

        Input:  s = "araaci", k = 1
        Output: 2   ("aa")

        Input:  s = "cbbebi", k = 3
        Output: 5   ("cbbeb" or "bbebi")

        Input:  s = "", k = 2
        Output: 0
     * @param s
     * @param k
     * @return
     */
    public static int longest_subarray_with_atomost_k_distinct(String s, int k){
        Map<Character, Integer> map = new HashMap<>();
        int left = 0;
        int maxLen = 0;
    
        for(int right=0; right<s.length(); right++){
            char element = s.charAt(right);
            // 1. Add incoming element first
            map.merge(element, 1, Integer::sum);

            // 2. Then shrink if violated
            while (map.size()>k){
                map.merge(s.charAt(left), -1, Integer::sum);
                if (map.get(s.charAt(left))==0){
                    map.remove(s.charAt(left));
                }
                left++;
            }

            // 3. Window guaranteed valid here
            maxLen = Math.max(maxLen, right-left+1);
        }

        return maxLen;
    }
    /**
     * "Q2 Given strings s and t, find the minimum length substring of s that contains all characters of t. If none exists return "".
     *  Input:  s = "ADOBECODEBANC", t = "ABC"
        Output: "BANC"

        Input:  s = "a", t = "a"
        Output: "a"

        Input:  s = "a", t = "b"
        Output: ""

        Input:  s = "AABB", t = "AB"
        Output: "AB"

        Input:  s = "ABC", t = "AABC"  
        Output: ""   (t longer, impossible)
     */
    public static int min_window_substring(String s){

        return 0;


    }
    
    /**
     * Sliding Window Q3 — Longest Subarray with Sum ≤ K
        Given an array of positive integers and a value k, find the length of the longest contiguous subarray whose sum is less than or equal to k."
        Input:  arr = [3, 1, 2, 7, 4, 2, 1, 1, 5], k = 8
        Output: 4   (subarray [4, 2, 1, 1])

        Input:  arr = [1, 1, 1, 1, 1], k = 3
        Output: 3

        Input:  arr = [10, 2, 3], k = 5
        Output: 2   (subarray [2, 3])
     */
    public static int longest_subarray_with_sum_k(int[] arr, int k){
        int left = 0;
        int currentSum = 0;
        int maxLen = 0;

        for(int right=0; right<arr.length; right++){
            currentSum += arr[right];
            while (currentSum>k){
                currentSum -= arr[left];
                left++;
            }

            maxLen = Math.max(maxLen, right-left+1);
        }

        return maxLen;
    }

     /**
     * #Q1 Given a string, find the length of the longest substring without repeating characters."**
     *  Input:  "abcabcbb"    Output: 3   ("abc")
        Input:  "bbbbb"       Output: 1   ("b")
        Input:  "pwwkew"      Output: 3   ("wke")
        Input:  ""            Output: 0
     */

    public static int longest_substring(String s){

        HashSet<Character> set = new HashSet<>();
        int left =0;
        int right =0;
        int maxLen = 0;

        while(right < s.length()){ 
            char element = s.charAt(right);
            while (set.contains(element)){
                set.remove(s.charAt(left));
                left++;
            }
            set.add(element);
            maxLen = Math.max(maxLen, right-left+1);
            right++;
        }

        return maxLen;
    }
    public static int longest_substring_optimised(String s){

        HashMap<Character, Integer> map = new HashMap<>();
        int left =0;
        int right =0;
        int maxLen = 0;

        while(right < s.length()){ 
            char element = s.charAt(right);
            if (map.containsKey(element) && map.get(element) >= left){
                left =map.get(element) +1;
            }
            map.put(element,right);
            maxLen = Math.max(maxLen, right-left+1);
            right++;
        }

        return maxLen;
    }

}