import java.util.Arrays;

public class two_pointer {
    public static void main(String[] args) {

        // int[] arr = {1, 3, 5, 7, 9, 11, 13, 15};
        // int target = 16;
        // findUniquePairs(arr, target);

        // int[] arr1 = {1, 1, 2, 3, 3, 3, 4, 5, 5};
        // int[] arr2 = {1, 2, 2, 2, 2, 2, 2, 2, 2};
        // remove_duplicates_in_place(new int[]{});

        // int[] arr = {1, 8, 6, 2, 5, 4, 8, 3, 7};
        // System.out.println(container_with_most_water(arr));
    }


    /**
     * Container With Most Water (after Q2)
     * "Given an array where height[i] is the height of a vertical line at position i, 
     * find two lines that together with the x-axis forms a container that holds the most water."
     * Input:  [1, 8, 6, 2, 5, 4, 8, 3, 7]
        Output: 49

        Explanation:
        Lines at index 1 (height 8) and index 8 (height 7)
        Width = 8 - 1 = 7
        Height = min(8, 7) = 7
        Area = 7 × 7 = 49
     */

    public static int container_with_most_water(int[] arr){
        int left = 0;
        int right = arr.length-1;
        int maxArea = 0;

       while(left < right){
            int area = Math.min(arr[left], arr[right]) * (right - left);
            // System.out.println(area + " left: " + left + " right: " + right);
            maxArea = Math.max(maxArea, area);
            if(arr[left] < arr[right]){
                left++;
            } else {
                right--;
            }
        }

        return maxArea;
    }
    /**
     * Remove Duplicates from Sorted Array In-Place
     * Given a sorted array, remove duplicates in-place so each element appears only once.
     *  Return the count of unique elements. Do not allocate extra space
     * Input:  [1, 1, 2, 3, 3, 3, 4, 5, 5]
        Output: 5  (array modified to [1, 2, 3, 4, 5, _, _, _, _])

        Input:  [1, 1, 1, 1]
        Output: 1

        Input:  []
        Output: 0
    */
    public static int remove_duplicates_in_place(int[] arr){
       
        if (arr == null || arr.length == 0 || arr.length ==1) return 0;

        int slow = 0;
        int fast = 0;
        int count = 1;

        for (int i=0;i<arr.length;i++){
            if (arr[slow] != arr[fast]){               
               slow++;
               arr[slow] = arr[fast];
               count++;
            }
            fast++;
        }

        System.out.println(Arrays.toString(arr));//*** */

        return count;

    }
    /** 
     * Find all unique pairs in a sorted array that sum to a target
        Input:  arr = [1, 3, 5, 7, 9, 11, 13, 15], target = 16
    */
    public static int findUniquePairs(int[] arr, int target){
        
        
        int left = 0;
        int right = arr.length - 1;
        int count = 0;
        while (left < right){
            int sum = arr[left] + arr[right];
            if (sum == target){
                count++; 
                left++;
                right--;

                while(left<right && arr[left] == arr[left-1]) left++;
                while(left<right && arr[right] == arr[right+1]) right--;
            } else if (sum < target){
                left++;
            } else {
                right--;
            }
        }

        System.out.println(count);
        return count;
    }
}