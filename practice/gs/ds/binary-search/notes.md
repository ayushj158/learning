## Binary Search — All Patterns

Three problems, one template. Let me teach the template first.

---

## The universal template

```java
int lo = 0, hi = n - 1;

while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;  // avoid overflow

    if (nums[mid] == target) {
        return mid;           // found
    } else if (nums[mid] < target) {
        lo = mid + 1;         // go right
    } else {
        hi = mid - 1;         // go left
    }
}
return -1;  // not found
```

---

## Problem 1 — LC 33 — Search in Rotated Sorted Array

> Array was sorted then rotated at some pivot. Find target.

```
Input:  nums = [4,5,6,7,0,1,2], target = 0
Output: 4

Input:  nums = [4,5,6,7,0,1,2], target = 3
Output: -1
```

---

## Key insight — one half is always sorted

```
[4, 5, 6, 7, 0, 1, 2]
         mid=7

left half  [4,5,6,7] → sorted ✓
right half [0,1,2]   → sorted ✓

At any mid point — ONE side is always sorted
Use sorted side to decide which half to search
```

---

## Decision logic

```
if left half sorted (nums[lo] <= nums[mid]):
  if target in left range (nums[lo] <= target < nums[mid]):
    search left → hi = mid-1
  else:
    search right → lo = mid+1

else right half sorted:
  if target in right range (nums[mid] < target <= nums[hi]):
    search right → lo = mid+1
  else:
    search left → hi = mid-1
```

---

Now code it — give it a go.