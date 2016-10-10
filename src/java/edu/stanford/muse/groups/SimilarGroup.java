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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.muse.index.CardTerm;
import edu.stanford.muse.util.Util;


/** 
 * SimilarGroup is just a Group with additional properties thrown in
 * (to track frequency, metrics etc)
 * structurally, both SimilarGroup and Group are a bunch of elements,
 * but Group is also used for the original data like recipients on email messages
 * and SimilarGroup is a bunch of people we've thrown together after some analysis, 
 * perhaps merging multiple original groups
 */
public class SimilarGroup<T extends Comparable<? super T>> extends Group<T> implements Serializable
{
	private final static long serialVersionUID = 1L;

	public List<CardTerm> descriptiveTags;
	public Map<String,Object> properties = new LinkedHashMap<String,Object>();
	public String name;
	public int freq;
	public float utility;
	double zScore;
	public float mass;

	public SimilarGroup() { }

	public SimilarGroup(Group<T> g) // constructor for a similar group from another, existing group
	{
		super(g.elements);
	}

	public SimilarGroup(T t) // constructor for a single element Group
	{
		super(t);
	}

	public SimilarGroup(Set<T> c) // input list should be sorted! may have duplicates
	{
		super (c);
	}

	public SimilarGroup(String name, Set<T> c) // input list should be sorted! may have duplicates
	{
		super (c);
		this.name = name;
	}

	public void setDescriptiveTags(List<CardTerm> tags)
	{
		descriptiveTags = new ArrayList<CardTerm>(tags);
	}

	public String descriptiveTags()
	{
		if (descriptiveTags == null)
			return null;

		String result = "";
		for (CardTerm t: descriptiveTags)
			result += (t.toHTML(false, 0, 0, "") + " &middot; ");
		return result;
	}

	// put/get properties
	public Object get (String s) { return properties.get(s); }
	public void put (String s, Object o) { properties.put(s, o); }

	/* default ordering is: order the one with higher # of elements first; if # of elements is same, order by count */
	public int compareTo(Group<T> o)
	{
//			if (this.count != o.count)
//				return o.count - this.count;
//
//			int osize = o.Group.size();
//			int size = this.Group.size();
//			if (size != osize)
//				return osize - size;

//			// if other things are equal, order by similarity metric
//			return (o.sim > this.sim) ? 1 : -1;
//			return (o.Q > this.Q) ? 1 : -1;
		return (((SimilarGroup<T>)o).utility > this.utility) ? 1 : -1;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
//		sb.append ("freq=" + freq + " utility=" + utility + " z=" + String.format("%.3f", zScore) + " ");
		sb.append ("freq=" + freq + " utility=" + utility + " ");
		sb.append (super.elementsToString());
		sb.append (" ");
		for (String property: properties.keySet())
			sb.append (property + "=" + properties.get(property) + " ");
		return sb.toString();
	}

	/** returns a new group that is the union of both of these */
	public SimilarGroup<T> union (SimilarGroup<T> other)
	{
		Group<T> group = super.union(other);
		SimilarGroup<T> sg = new SimilarGroup<T>(group);
		return sg; // leave at default for zscore, freq and utility, i.e. 0;
	}

	public SimilarGroup<T> intersect (SimilarGroup<T> other)
	{
		Group<T> group = super.intersect(other);
		SimilarGroup<T> sg = new SimilarGroup<T>(group);
		return sg; // leave at default for zscore, freq and utility, i.e. 0;
	}

	public float errorWRT (SimilarGroup<T> g)
	{
		// g must be a subset of this
		//Util.ASSERT (g.freq >= this.freq); // Not for the new algo
		Util.ASSERT (g.size() < this.size());
		int covered = g.freq * size(); // freq of subset times size of superset

		// both g.freq and covered.freq may be 0, for supergroups
		// the spirit is: if the freq's are the same, the error is the %age difference in members
		if (g.freq == 0)
			return ((float) this.size()-g.size())/this.size();

		int mislabelled = (this.size() - g.size()) * (g.freq - this.freq); // superset-people getting access to subset-only messages
		return ((float) mislabelled)/covered;
	}

	public double computeZScore(Map<T,Integer> indivFreqs, int nMessages)
	{
		double p = 1.0;
		for (T t: elements)
		{
			int indivCount = indivFreqs.get(t);
			p *= ((double) indivCount)/nMessages;
		}
		zScore = (freq - (nMessages * p))/Math.sqrt(nMessages * p * (1-p));
		return zScore;
	}

	public void computeLinearUtility()
	{
		utility = elements.size() * freq;
	}

	public void computeSquareUtility()
	{
		utility = elements.size() * elements.size() * freq;
	}

	public void computeExpUtility(float base)
	{
		utility = (float) Math.pow (base, elements.size()) * freq;
	}

	public boolean sameMembersAs(SimilarGroup g) {
		// TODO Auto-generated method stub
		return super.equals(g);
	}

	public void setMass(float mass)
	{
		this.mass = mass;
	}

	public static Comparator<SimilarGroup> sortByMass
	= new Comparator<SimilarGroup>() {
		public int compare (SimilarGroup g1, SimilarGroup g2)
		{
			// shouldn't it be " return (g2.mass-g1.mass); "
			return (g1.mass > g2.mass) ? -1 : 1;
		}
	  };


	/*
	  public String emailAddrtoString(){
			StringBuilder sb=new StringBuilder();
			sb.append (super.elementsToString());
			return sb.toString();
		}
	*/

	//	public SimilarGroup<T> union (SimilarGroup<T> other)
//	{
//		Group<T> newGroup = this.Group.union(other.Group);
//		SimilarGroup<T> sb = new SimilarGroup<T>(newGroup, 0, 0.0f);
//		sb.messages.addAll(this.messages);
//		sb.messages.addAll(other.messages);
//		return sb;
//	}
}
