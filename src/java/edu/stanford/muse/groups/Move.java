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

import edu.stanford.muse.graph.GroupsGraph;
import edu.stanford.muse.graph.Node;
import edu.stanford.muse.util.Util;

import java.util.*;

public class Move <T extends Comparable<? super T>> implements Comparable<Move<T>>
{
	// order is important here, the absolute numbers are used in MyComparator
    public enum Type { UNION, DOMINATE, INTERSECT, DROP }
//	public static final int MoveType_UNION = 1;
//	public static final int MoveType_DOMINATE = 2;
//	public static final int MoveType_INTERSECT = 3;
//	public static final int MoveType_DROP = 4;
    
	public Type type;
	public static float errWeight = 0.5f;
	public Node<SimilarGroup<T>> n1, n2; // note: for drop moves, n1 is same as n2
	public float valueReduction;
	public float multiplier = 1.0f; // actual value reduction is multiplier * valueReduction (see Grouper.MyComparator)
	public float n1Value, n2Value;
	public float newValue;
	public int moveNum;
	
	/** constructor for dominate and intersect moves */
	public Move (Type type, Node<SimilarGroup<T>> n1, Node<SimilarGroup<T>> n2,  Map<T, Float> individualsValueMap, GroupsGraph<T> graph) 
	{
		this.type = type;
		this.n1 = n1;
		this.n2 = n2;
		if (type == Type.INTERSECT)
			computeIntersectionValue(individualsValueMap, graph);
		else if (type == Type.DOMINATE)
			computeDominateValue(individualsValueMap, graph);			
		else
			Util.softAssert(false);
		// Grouper.log.debug("Created Move: " + this);
	}
	
	/** constructor for UNION moves */
	public Move (Type type, Node<SimilarGroup<T>> n1, Node<SimilarGroup<T>> n2, float affinity)
	{
		this.type = type;
		this.n1 = n1;
		this.n2 = n2;

		Util.softAssert (type == Type.UNION);

		computeUnionValue(affinity);
		Util.softAssert (this.valueReduction >= 0.0f);
		// Grouper.log.debug("Created Move: " + this);
	}
	
	/** single node constructor for DROP moves. note that for drop moves, both n1 and n2 are initialized to the same node */
	public Move(Node<SimilarGroup<T>> n, Map<T, Float> individualsValueMap, GroupsGraph<T> graph) {
		this.type = Type.DROP;
		this.n1 = this.n2 = n;
		
		this.valueReduction = n.value;
		Collection<T> elems = elementsOnlyInThisNode(n, graph);
		float lostElementsValue = totalValueOfElements(elems, individualsValueMap);

		if (n.payload.size() > 1) // only set multiplier for non-individuals
			setDroppedElementsMultiplier(n.value, lostElementsValue, n.payload.size());
		// Grouper.log.debug("Created Move: " + this);
	}

	/** set multiplier due to lost elements */
	private void setDroppedElementsMultiplier(float value, float lostElementsValue, int resultingGroupSize)
	{
		multiplier = (value + lostElementsValue)/value;
		// throttle multiplier to the range [1,5]
		if (multiplier < 1.0f)
			multiplier = 1.0f;
		if (multiplier > 5.0f)
			multiplier = 5.0f;
		
		// reduce multiplier for large groups - we want to make it easier to drop elements from those groups
		if (resultingGroupSize > Grouper.PREFERRED_MAX_GROUP_SIZE)
			multiplier /= (((float) resultingGroupSize)/Grouper.PREFERRED_MAX_GROUP_SIZE);
	}
	
	public void setMoveNum(int m)
	{
		this.moveNum = m;
	}
	
	public int numForType()
	{
		// dependency on this numbering: at least in move-graph.js for plotting the moves
		if (type == Type.UNION) return 1;
		if (type == Type.DOMINATE) return 2;
		if (type == Type.INTERSECT) return 3;
		if (type == Type.DROP)  return 4;
		Util.softAssert (false);
		return -1;
	}
	
	/** snapshot n1 and n2's values at the point this move is picked, because the value in those nodes may change later...
		need it just so that we can draw the move graph graphically. */
	public void grabSnapshot()
	{
		if (n1 != null)
			n1Value = n1.value;
		if (n2 != null)
			n2Value = n2.value;
		if (type != Type.DROP)
			newValue = n1Value + n2Value - valueReduction;
		else
			newValue = 0;
	}

	private List<T> elementsOnlyInThisNode(Node<SimilarGroup<T>> n, GroupsGraph<T> graph)
	{
		List<T> result = new ArrayList<T>();
		for (T t: n.payload.elements)
			if (graph.getNodeCount(t) == 1) // should not be zero of course
				result.add(t);
		/*
		Set<T> neighborElements = new LinkedHashSet<T>();
		for (Node<SimilarGroup<T>> cn : n.connectedNodes)
		{
			neighborElements.addAll(cn.payload.elements);
			// do an early out -- the common case
			if (neighborElements.containsAll(n.payload.elements))
				return result;
		}
		SimilarGroup<T> sg = n.payload;
		for (T t: sg.elements)
			if (!neighborElements.contains(t))
				result.add(t);
				*/
		return result;
	}
	
	private float totalValueOfElements(Collection<T> elems, Map<T, Float> individualsValueMap)
	{
		float result = 0.0f;
		for (T t: elems)
		{
			Float F = individualsValueMap.get(t);
			if (F != null)
				result += F;
		}
		return result;
	}
	
	public String toString(){
		StringBuilder sb=new StringBuilder();
		sb.append ((type == Type.UNION) ? "UNION " : ((type == Type.INTERSECT) ? "INTERSECT " : (type == Type.DOMINATE) ? "DOMINATE ":"DROP "));
		sb.append("< "+n1.payload.elementsToString());
		if (type == Type.UNION || type == Type.INTERSECT || type == Type.DOMINATE)
		{
			sb.append("> and < ");
			sb.append(n2.payload.elementsToString()+"> ");
		}
		else if (type == Type.DROP)
			sb.append(">");

		sb.append ("Value loss: " + valueReduction + " * " + this.multiplier + " = " + (this.valueReduction * this.multiplier));
		return sb.toString();
	}

	private boolean computeUnionValue(float affinity){
		SimilarGroup<T> group1 = n1.payload;
		SimilarGroup<T> group2 = n2.payload;
		SimilarGroup<T> n12=new SimilarGroup<T>(group1.minus(group2));
		SimilarGroup<T> n21=new SimilarGroup<T>(group2.minus(group1));
		float serr=0.0f;

		if (n12.size()>0) {
			serr += n2.value*(n12.size())/(n2.payload.size());
		}
		if (n21.size()>0) {
			serr += n1.value*(n21.size())/(n1.payload.size());
		}
		
		valueReduction = errWeight * serr;
		this.multiplier = (1-affinity);
		// increase penalty if merged group size is going to be larger than PREFERRED_MAX_GROUP_SIZE
		int newGroupSize = group1.size() + n21.size();
		if (newGroupSize > Grouper.PREFERRED_MAX_GROUP_SIZE)
			multiplier *= (newGroupSize/Grouper.PREFERRED_MAX_GROUP_SIZE);

		return true;
	}

	private void computeIntersectionValue(Map<T, Float> individualsValueMap, GroupsGraph<T> graph){
		SimilarGroup<T> group1 = n1.payload;
		SimilarGroup<T> group2 = n2.payload;
		int intersectSize = group1.intersectionSize(group2);
		float temp = n1.value*(intersectSize)/(group1.size()) + n2.value*(intersectSize)/(group2.size());
		valueReduction = n1.value + n2.value - temp;
	//	int nElemOnlyInThisNode = getNElementsOnlyInThisNode(n1) + getNElementsOnlyInThisNode(n2);
	//	this.multiplier = Math.min(5, 1+nElemOnlyInThisNode);
		Collection<T> elems = elementsOnlyInThisNode(n1, graph);
		float lostElementsValue = totalValueOfElements(elems, individualsValueMap);
		elems = elementsOnlyInThisNode(n2, graph);
		lostElementsValue += totalValueOfElements(elems, individualsValueMap);
		setDroppedElementsMultiplier(valueReduction, lostElementsValue, intersectSize);
	}
	
	private void computeDominateValue(Map<T, Float> individualsValueMap, GroupsGraph<T> graph) {
		// dominate is the move where we drop n2, but transfer its value to n1
		SimilarGroup<T> group1 = n1.payload;
		SimilarGroup<T> group2 = n2.payload;
		SimilarGroup<T> n12 = new SimilarGroup<T>(group1.minus(group2));
		int n1and2Size = group2.intersectionSize(group1);

		// alternative definition
		float importedValue = n2.value * (n1and2Size)/group2.size(); // transfer value to n2
		importedValue -= n2.value * errWeight * n12.size()/group2.size(); // adjust for error because n1 is seeing n2's messages
		valueReduction = n2.value - importedValue;
		
		// we're goint to lose n2's elements
		Collection<T> elems = elementsOnlyInThisNode(n2, graph);
		float lostElementsValue = totalValueOfElements(elems, individualsValueMap);
		setDroppedElementsMultiplier(valueReduction, lostElementsValue, n1.payload.size());
	}

	public boolean equals(Object o)
	{
		Move<T> m = (Move<T>) o;
		boolean b1 = this.n1.equals(m.n1) && this.n2.equals(m.n2);
//		boolean b2 = this.valueReduction == m.valueReduction; // false; // this.n1.equals(m.n2) && this.n2.equals(m.n1);
		boolean b3 = (this.type == m.type);
		boolean result = b3 && b1; // && b2;
		return result;
	}

	public int hashCode()
	{
		return n1.hashCode() ^ n2.hashCode() ^ type.hashCode();
	}

	// since move's are commutative, we have to ensure we return the same value whether the move is n1 U/I n2 or n2 U/I n1
	// moves are not commutative now with dominate's!
	private String makeCanonicalString()
	{
		String s1 = this.n1.payload.elementsToString();
		String s2 = this.n2.payload.elementsToString();
	//	if (s1.compareTo(s2) < 0)
			return type + "--" + s1 + "--" + s2;
	//	else
	//		return type + "--" + s2 + "--" + s1;
	}

	/** note: this is not a value-compare -- use MyComparator for that! */
	public int compareTo(Move<T> o) {
		if (this.equals(o)) {
			return 0;
		} else {
			String s1 = this.makeCanonicalString();
			String s2 = o.makeCanonicalString();
			int x = s1.compareTo(s2);
			if (x == 0)
				Util.breakpoint();
			return x;
		}
	}
	
	/** comparator for Moves; move with a lower (value reduction*multiplier) is ordered before a move with a higher one. */
	static class MyComparator<T extends Comparable<? super T>> implements Comparator<Move<T>>, java.io.Serializable
	{
		public int compare(Move<T> m1, Move<T> m2)
		{
			if (m1.equals(m2))
				return 0;

			float r = (m1.valueReduction * m1.multiplier) - (m2.valueReduction * m2.multiplier);
			if (r > 0) {
				return 1;
			}
			else if (r < 0) {
				return -1;
			}
			else {
				// prefer union to dominate to intersect to discard
				if (m1.type != m2.type)
					return (m1.numForType() - m2.numForType());

				String s11 = m1.n1.payload.elementsToString();
				String s12 = ((m1.n2 != null) ? m1.n2.payload.elementsToString() : "");
				String s21 = m2.n1.payload.elementsToString();
				String s22 = ((m2.n2 != null) ? m2.n2.payload.elementsToString() : "");
				String s1 = s11.compareTo(s12) < 0 ? s11 + " | " + s12 : s12 + " | " + s11;
				String s2 = s21.compareTo(s22) < 0 ? s21 + " | " + s22 : s22 + " | " + s21;

				int x = s1.compareTo(s2);
				if (x != 0)
					return x;
				return m1.compareTo(m2);
				/*
				if (x != 0)
					return x;
				int y = m1.compareTo(m2);
				if (y == 0)
					Util.breakpoint();

				return y;
				*/
			}
		}
	}

}
