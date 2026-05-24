import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Mains {

    public static void main(String[] args) {
        // System.out.println(longest_substtring_with_atmost_k_distinct("eceba", 2));

        int[] nums = new int[] {1,2,3,4,5,6,7,8,9,10};
        System.out.println(Arrays.stream(nums).max().getAsInt());
        
    }

    public static int longest_substtring_with_atmost_k_distinct(String s, int k){
        Map<Character, Integer> window = new HashMap<>();

        int left =0;
        int max = 0;        
        for (int right = 0; right<s.length(); right++){
            char c = s.charAt(right);
            window.merge(c, 1, Integer::sum);
            
            while (window.size()>k){
                char leftChar = s.charAt(left);
                window.merge(leftChar, -1, Integer::sum);
                if (window.get(leftChar) == 0) window.remove(leftChar); // only fully remove when count=0
                left++;
            }

            max = Math.max(max, right-left+1);
        }

        return max;
    }
}