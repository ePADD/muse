package edu.stanford.muse.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** A compact representation for a set of int's. random access is not provided. 
 * it is kept sorted for efficiency of intersection. it is meant to be immutable. */
public class IntSet implements java.io.Serializable {

	/** make sure to keep arr private, we may want to change the internal representation later */
	private int arr[]; 

	public IntSet(Set<Integer> input)
	{
	    this(new ArrayList<Integer>(input));
	}

	public IntSet(List<Integer> list)
	{
	    Collections.sort(list);
	    arr = new int[list.size()];
	    for (int i = 0; i < list.size(); i++)
	        arr[i] = list.get(i);
	}

	public IntSet intersect(IntSet other)
	{
	    List<Integer> resultList = new ArrayList<Integer>();

	    // i will walk over other's elements, j's over this element
	    int i = 0, j = 0; 
	    if (other != null)
	    {
		    while (i < arr.length && j < other.arr.length)
		    {   
		        if (arr[i] == other.arr[j])
		        {
		        	resultList.add(arr[i]);
		            i++; j++;
		        }
		        else if (arr[i] < other.arr[j])
		            i++;
		        else
		            j++;
		    }
	    }
	    return new IntSet(resultList);
	}
	
	public boolean isEmpty() { return size() == 0;	}

	public int size() { return arr.length; }

	/** WARNING: returning actual array for speed, instead of creating a copy. callers should not modify it! */
	public int[] elements() { return arr; }

	@Override
	public int hashCode() {
		int h = 0;
		for (int i: arr)
			h ^= i;
		return h;
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof IntSet))
			return false;
		
		IntSet other = (IntSet) o;
		if (other.arr.length != this.arr.length)
			return false;
		
		for (int i = 0; i < this.arr.length; i++)
			if (this.arr[i] != other.arr[i])
				return false;
		
		return true;
	}
}
