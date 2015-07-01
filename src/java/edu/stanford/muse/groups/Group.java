/*
 Copyright (C) 2012 The Stanford MobiSocial Laboratory

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.stanford.muse.groups;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.muse.util.Util;

/** a core data structure, holding together a group of elements.
 * keeps elements in sorted order for quick computation of intersection etc.	
 */
public class Group<T extends Comparable<? super T>> implements Comparable<Group<T>>, Serializable {
	private final static long serialVersionUID = 1L;

	public List<T> elements;
	
	public Group() { }
	
	// bigger Groups first, which is usually what we want
	public int compareTo(Group<T> o) { return elements.size() - o.elements.size(); }
	
	public List<T> elements()
	{
		return elements;
	}
	
	public Group(T t) // constructor for a single element Group
	{
		elements = new ArrayList<T>();
		elements.add(t);
	}
	
	public Group(Set<T> c) // input list should be sorted!
	{
		elements = new ArrayList<T>();
		elements.addAll(c);
		Collections.sort(elements);
	}
	
	public Group(List<T> c) // input list should be sorted! may have duplicates
	{
		elements = new ArrayList<T>();
		if (c.isEmpty())
			return;
		T prev = c.get(0);
		elements.add(prev);
		for (int i = 1; i < c.size(); i++)
		{
			T t = c.get(i);
			int cmp = t.compareTo(prev);
			if (cmp > 0) {
				elements.add(t);
				prev = t;
			} else if (cmp < 0) {
				// ERROR: input list is not sorted
				System.out.println ("Error: unsorted " + prev + " " + t);
				Util.ASSERT(cmp < 0);
			}
		}
	}

	public int size()
	{
		return elements.size();
	}
	
	public T get(int x)
	{
		return elements.get(x);
	}
	
	/* factory method. creates a new Group consisting of the elements of b1 and b2
	 * and returns it, IF b1 and b2 do not intersect.
	 * if they do intersect, returns null.
	 * useful when creating subsets of a particular size.
	 */
	public static<T extends Comparable<? super T>> Group<T> createGroupIfNonIntersecting(Group<T> g1, Group<T> g2)
	{
		Set<T> set = new LinkedHashSet<T>();
		set.addAll(g1.elements);
		set.addAll(g2.elements);
	
		if (set.size() != g1.elements.size() + g2.elements.size())
			return null;
	
		List<T> list = new ArrayList<T>();
		list.addAll(g1.elements);
		list.addAll(g2.elements);
		Collections.sort(list);
	
		return new Group<T>(list);
	}
	
	public static<T extends Comparable<? super T>> List<Group<T>> selectGroupsOfSize2OrMore(List<Group<T>> input)
	{
		List<Group<T>> result = new ArrayList<Group<T>>();
		for (Group<T> g: input)
			if (g.size() > 1)
				result.add(g);
		return result;
	}
	
	/** returns a new group that is the union of this and g */
	public Group<T> union(Group<T> g) 
	{
		Set<T> set = new LinkedHashSet<T>();
		set.addAll(this.elements);
		set.addAll(g.elements);
		return new Group<T>(set);
	}
	
	/** returns a new group that is the intersection of this and g */
	public Group<T> intersect(Group<T> g)
	{
		Set<T> set = new LinkedHashSet<T>();
		set.addAll(this.elements);
		set.retainAll(g.elements); // take intersection
		return new Group<T>(set);
	}
	
	/** returns a new group that is the result of this - g*/
	public Group<T> minus(Group<T> g)
	{
		//Set<T> set = new LinkedHashSet<T>();
		//set.addAll(this.elements);
		//set.removeAll(g.elements); // minus
		//return new Group<T>(set);
		
		// use explicit code for better performance as minus() is called often.
		// somehow Set.removeAll() would otherwise resort to suboptimal ArrayList.contains().
		int i_end = this.elements.size();
		int j_end = g.elements.size();
		if (i_end == 0 || j_end == 0)
			return new Group<T>(this.elements);
		int i = 0;
		int j = 0;
		T e_i = this.elements.get(i);
		T e_j = g.elements.get(j);
		Group<T> result = new Group<T>(new ArrayList<T>());
		while (true)
		{
			int cmp = e_j.compareTo(e_i);
			if (cmp == 0) { // minus case => skip e_i, e_j.
				i++;
				j++;
				if (i >= i_end)
					break;
				if (j >= j_end)
					break;
				e_i = this.elements.get(i);
				e_j = g.elements.get(j);
			} else if (cmp < 0) { // g's head is smaller => move g's head.
				j++;
				if (j >= j_end)
					break;
				e_j = g.elements.get(j);
			} else { // g's head is larger => we can keep e_i.
				result.elements.add(e_i);
				i++;
				if (i >= i_end)
					break;
				e_i = this.elements.get(i);
			}
		}
		result.elements.addAll(this.elements.subList(i, i_end));
		return result;
	}
	
	/** efficiently computes size of intersection of this and g */
	public int intersectionSize(Group<T> g)
	{
		int count = 0;
		int i = 0;
		int i_end = this.elements.size();
		if (i_end == 0)
			return 0;
		T e_i = this.elements.get(i);
		int j = 0;
		int j_end = g.elements.size();
		if (j_end == 0)
			return 0;
		T e_j = g.elements.get(j);
		while (true)
		{
			int cmp = e_j.compareTo(e_i);
			if (cmp == 0) {
				count++;
				i++;
				j++;
				if (i >= i_end)
					break;
				if (j >= j_end)
					break;
				e_i = this.elements.get(i);
				e_j = g.elements.get(j);
			} else if (cmp < 0) { // g's head is smaller
				j++;
				if (j >= j_end)
					break;
				e_j = g.elements.get(j);
			} else {
				i++;
				if (i >= i_end)
					break;
				e_i = this.elements.get(i);
			}
		}
		return count;
	}
	
	
	public void remove(T t)
	{
		elements.remove(t);
	}
	
	public float jaccardSim(Group<T> g)
	{
		int common = this.intersectionSize(g);
		int unionSize = this.size() + g.size() - common;
		return ((float) common)/unionSize;
	}
	
	/** intersectionSize > 0; early out if non empty intersection */
	public boolean nonEmptyIntersection(Group<T> g)
	{
		int i = 0;
		int i_end = this.elements.size();
		int j = 0;
		int j_end = g.elements.size();
		while (i < i_end)
		{
			if (j >= j_end)
				break;
			int cmp = g.elements.get(j).compareTo(this.elements.get(i));
			if (cmp == 0) {
				return true;
			} else if (cmp < 0) { // g's head is smaller
				j++;
			} else {
				i++;
			}
		}
		return false;
	}
	
	static<T> List<List<T>> cloneListOfLists(List<List<T>> list)
	{
		List<List<T>> result = new ArrayList<List<T>>();
		for (List<T> x : list)
		{
			// clone list x into y
			List<T> y = new ArrayList<T>();
			for (T t : x)
				y.add(t);
			result.add(y);
		}
		return result;
	}
	
	/** internal version of function, that creates lists of lists instead of lists of Groups */
	private List<List<T>> generatePermutationsInternal (List<T> allElements, int beginIdx, int nToPick)
	{
		List<List<T>> result = new ArrayList<List<T>>();
		for (int i = beginIdx; i < allElements.size(); i++)
		{
			if (nToPick == 1)
			{
				List<T> x = new ArrayList<T>();
				x.add(allElements.get(i));
				result.add(x);
			}
			else
			{
				List<List<T>> minus1 = generatePermutationsInternal(allElements, i+1, nToPick-1);
				List<List<T>> clone = cloneListOfLists(minus1);
				for (List<T> list : clone)
					list.add(allElements.get(i));
				result.addAll(clone);
			}
		}
		return result;
	}
	
	/** computes index of best fit for g in groups, based on jaccard sim.
	 * returns -1 if g has no element in common with any element of groups 
	 */
	public static<T extends Comparable<? super T>> int bestFit (List<? extends Group<T>> groups, Group<T> g)
	{
		int best = -1;
		float bestSim = 0;
		for (int i = 0; i < groups.size(); i++)
		{
			Group<T> g1 = groups.get(i);
			float sim = g1.jaccardSim(g);
			if (sim > bestSim)
			{
				best = i;
				bestSim = sim;
			}
		}
		return best;
	}
	
	/** generates all subsets of this Group with nToPick elements */
	public List<Group<T>> generateSubsets(int nToPick)
	{
		List<List<T>> permutations = generatePermutationsInternal(elements, 0, nToPick);
		List<Group<T>> result = new ArrayList<Group<T>>();
		for (List<T> list: permutations)
		{
			Collections.sort(list); // this is important to maintain the invariant that the list is sorted
			result.add (new Group<T>(list));
		}
		return result;
	}
	
	/** given a list of groups, eliminates ones that are contained completely in another.
	 not particularly efficient */
	public static<T extends Comparable<? super T>> List<Group<T>> eliminateSubsets(List<Group<T>> list)
	{
		// first sort the elements of the bucket (lazily)
		for (Group<T> g: list)
			Collections.sort(g.elements);
	
		boolean redundant[] = new boolean[list.size()]; // all false by default,
	
		// compare all elements with each other to check if one is redundant
		// somewhat expensive, O(n^2), can be implemented more efficient
		// if it becomes expensive in practice
		for (int i = 0; i < list.size(); i++)
		{
			if (redundant[i])
				continue;
	
			// compare with prefix of each jterm
			for (int j = 0; j < list.size(); j++)
			{
				if ((i == j) || redundant[j])
					continue;
	
				if (list.get(i).contains(list.get(j)))
					redundant[j] = true;
			}
		}
	
		List<Group<T>> resultList = new ArrayList<Group<T>>();
		for (int i = 0; i < list.size(); i++)
			if (!redundant[i])
				resultList.add(list.get(i));
	
		return resultList;
	}
	
	public boolean contains(T element)
	{
		return Collections.binarySearch(elements, element) >= 0; // most significant performance improvement. can use binarySearch because elements are already sorted.
	}
	
	/** returns true if this contains the other Group (including if the two Groups are equal)
	 * note elements must already have been sorted!
	 * this method is performance critical!
	 * this lets us compare the elements in the 2 sets sequentially */
	public boolean contains(Group<T> other)
	{
		if (elements.size() < other.elements.size())
			return false;
	
		// i will walk over other's elements, j's over this element
		for (int i = 0, j = 0; i < other.elements.size(); i++, j++)
		{
			// at the end of this loop, i and j will be elements we can
			// start looking for matches for
	
			// get other's element
			T otherElement = other.elements.get(i);
	
			// now sequentially find if there's a j element in this which matches otherElement
			boolean matched = false;
			while (j < this.elements.size())
			{
				// invariant: j is the index into this.elements
				// which last matched the i element
				T possibleMatch = this.elements.get(j);
				if (possibleMatch.equals(otherElement))
				{
					matched = true;
					break;
				}
				// if our candidate compares > the otherElement we're trying
				// to match with, no hope of finding a match
				if (possibleMatch.compareTo(otherElement) > 0)
					return false;
				j++;
			}
	
			if (!matched)
				return false;
		}
	
		return true;
	}
	
	public int hashCode()
	{
		int xor = 0;
		for (T t: elements)
			xor ^= t.hashCode();
		return xor;
	}
	
	@SuppressWarnings("unchecked")
	public boolean equals(Object o)
	{
		Group<T> other = (Group<T>) o;
		if (elements.size() != other.elements.size())
			return false;
	
		// important: assumption here that the elements are sorted!
		for (int i = 0; i < elements.size(); i++)
			if (!elements.get(i).equals(other.elements.get(i)))
				return false;
	
		return true;
	}
	
	public String toString()
	{
		return (elements.size() + "-Group: " + elementsToString());
	}
	
	public String elementsToString()
	{
		StringBuilder sb = new StringBuilder();
		for (T t: elements)
			sb.append (t + " ");
		return sb.toString();
	}
	
	public String elementsToStringHTML()
	{
		StringBuilder sb = new StringBuilder();
		for (T t: elements)
			sb.append (t + " <br/>\n");
		return sb.toString();
	}
	
	public static void main (String args[])
	{
		List<Integer> list = new ArrayList<Integer>();
		for (int i = 0; i < 10; i++)
			list.add(i);
		Group<Integer> g = new Group<Integer>(list);
		List<Group<Integer>> one = g.generateSubsets(1);
		List<Group<Integer>> x = g.generateSubsets(2);
		x.addAll(one);
		x.add(g);	// original superset
	
		for (Group<Integer> y: x)
		{
			for (int i : y.elements)
				System.out.print (i + " ");
			System.out.println();
		}
		System.out.println (x.size() + " lists");
	
		List<Group<Integer>> result = eliminateSubsets(x);
		for (Group<Integer> y: result)
		{
			for (int i : y.elements)
				System.out.print (i + " ");
			System.out.println();
		}
	
		System.out.println (result.size() + " lists");
	}
}
