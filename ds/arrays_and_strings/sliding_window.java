import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class sliding_window {
    public static void main(String[] args) {

        System.out.println(longest_subarray_with_sum_k(new int[]{10, 2, 3}, 5));
    }


    public static int longest_subarray_with_atomost_k_distinct(int[] arr, int k){
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
     * "Given strings s and t, find the minimum length substring of s that contains all characters of t. If none exists return "".
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
     * #Given a string, find the length of the longest substring without repeating characters."**
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