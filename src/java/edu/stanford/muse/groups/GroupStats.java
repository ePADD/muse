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


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.muse.util.Util;


// could be parameterized by T, but am too lazy to do so ...
public class GroupStats<T extends Comparable<? super T>> {
	int nGroups;
	int maxGroupSize, minGroupSize;
	float avgGroupSize;
	int nPeople, nPeopleInMoreThanOneGroup;
	
	// supports sets of both similargroups and groups
	public GroupStats(Set<SimilarGroup<T>> groups)
	{
		List<SimilarGroup<T>> list = new ArrayList<SimilarGroup<T>>();
		for (Object g: groups)
		{
			if (g instanceof SimilarGroup)
				list.add((SimilarGroup<T>) g);
			else
				list.add (new SimilarGroup<T>((Group<T>) g));
		}
		analyzeGroups(list);
	}

	public GroupStats(List<SimilarGroup<T>> groups)
	{
		analyzeGroups(groups);
	}

	private void analyzeGroups(List<SimilarGroup<T>> groups)
	{
		nGroups = groups.size();
		if (groups.size() == 0)
			return;
			
		maxGroupSize = Integer.MIN_VALUE; 
		minGroupSize = Integer.MAX_VALUE;
		nPeopleInMoreThanOneGroup = 0;
		int sum = 0;
		for (SimilarGroup<T> sg: groups)
		{
			if (sg.size() > maxGroupSize)
				maxGroupSize = sg.size();
			if (sg.size() < minGroupSize)
				minGroupSize = sg.size();
			sum += sg.size();
		}
		avgGroupSize = ((float) sum)/groups.size();
		
		Set<Object> peopleInMoreThanOneGroup = new LinkedHashSet<Object>();
		Set<Object> allPeople = new LinkedHashSet<Object>();
		for (SimilarGroup<T> sg: groups)
		{
			for (Object person: sg.elements)
			{
				Util.softAssert(person.equals(person));
				if (allPeople.contains(person))
					peopleInMoreThanOneGroup.add(person);
				allPeople.add(person);
			}
			nPeopleInMoreThanOneGroup = peopleInMoreThanOneGroup.size();
			nPeople = allPeople.size();
		}			
	}
	
	public String toString()
	{
		return " groups: " + nGroups + " group_size: " 
				+ "max: " + maxGroupSize + " min: " + minGroupSize + " avg: " + String.format("%.2f", avgGroupSize)
				+ " people: " + nPeople + " people_in_2_or_more_groups: " + nPeopleInMoreThanOneGroup;
	}

}
