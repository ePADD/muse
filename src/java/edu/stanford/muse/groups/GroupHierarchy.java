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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A hierarchy of groups. probably overkill. Most clients will just want to take this and say getAllGroups() to get the top N groups */
public class GroupHierarchy<T extends Comparable<? super T>> {

	public Map<SimilarGroup<T>, List<SimilarGroup<T>>> parentToChildrenMap  = new LinkedHashMap<SimilarGroup<T>, List<SimilarGroup<T>>>();
	public Set<SimilarGroup<T>> rootGroups = new LinkedHashSet<SimilarGroup<T>>();
	private List<SimilarGroup<T>> allGroups;

	public GroupHierarchy(List<SimilarGroup<T>> input)
	{
		allGroups = input;
		computeHierarchy(input);
	}

	public List<SimilarGroup<T>> getAllGroups() { return allGroups; }

	/** returns a map of parent->list(immediate children) from the given groups. */
	private void computeHierarchy(List<SimilarGroup<T>> input)
	{
		Set<SimilarGroup<T>> childOrParent = new LinkedHashSet<SimilarGroup<T>>();

		// order the input in increasing order of size
		List<SimilarGroup<T>> sortedInput = new ArrayList<SimilarGroup<T>>();
		sortedInput.addAll(input);
		Collections.sort(sortedInput, new Comparator<SimilarGroup<T>>() {
			public int compare(SimilarGroup<T> g1, SimilarGroup<T> g2) {
              return g1.size() - g2.size(); // return -ve if first is < second
			}
		});

		// first compute a childToParent map: each child has a unique parent
		// which is the best superset it finds.
		// note that the group could have multiple supersets. we blithely ignore the rest
		// for the purposes of display a child will have only one parent.
		Map<SimilarGroup<T>, SimilarGroup<T>> childToParentMap = new LinkedHashMap<SimilarGroup<T>, SimilarGroup<T>>();

		for (int i = 0; i < sortedInput.size(); i++)
		{
			SimilarGroup<T> group_i = sortedInput.get(i);
			// for this group find its 'best' superset
			SimilarGroup<T> bestSuperset = null;
			float bestError = 1.1f; // real errors are < 1
			for (int j = i+1; j < sortedInput.size(); j++)
			{
				SimilarGroup<T> group_j = sortedInput.get(j);
				if (group_j.contains(group_i))
				{
					float error = group_j.errorWRT(group_i);
					if (error < bestError)
					{
						bestError = error;
						bestSuperset = group_j;
				//		bestSize = group_j.size();
						// could break out here if we just wanted the first superset
						// but it seems cheap enough to compute the best superset
					}
				}

				if (bestSuperset != null)
				{
					childToParentMap.put(group_i, bestSuperset); // g2 contains g1, so its its (heh heh) parent
					childOrParent.add(bestSuperset);
					childOrParent.add(bestSuperset);
				}
			}
		}

		/*
		// go thru all groups in reverse order
		// assigning levels to each group
		Map<SimilarGroup<T>, SimilarGroup<T>> childToParentMap = new LinkedHashMap<SimilarGroup<T>, SimilarGroup<T>>();

		for (int i = sortedInput.size()-1; i >= 0; i--)
		{
			SimilarGroup<T> g = sortedInput.get(i);
			SimilarGroup<T> parent = childToParentMap.get(g);
			// if this group does not have a parent, make it level 0,
			// otherwise parent's level + 1
			if (parent == null)
				groupToLevelMap.put(g, 0);
			else
				groupToLevelMap.put(g, groupToLevelMap.get(parent)+1);
		}
		*/

		for (SimilarGroup<T> child : sortedInput)
		{
			SimilarGroup<T> parent = childToParentMap.get(child);
			if (parent == null)
				continue;
			List<SimilarGroup<T>> list = parentToChildrenMap.get(parent);
			if (list == null)
			{
				list = new ArrayList<SimilarGroup<T>>();
				parentToChildrenMap.put(parent, list);
			}
			list.add(child);
		}

		// there may be some groups that are neither child nor parent, so identify those separately and
		// give them an empty child list
		for (SimilarGroup<T> g: sortedInput)
			if (!childOrParent.contains(g))
				parentToChildrenMap.put (g, new ArrayList<SimilarGroup<T>>());

		for (SimilarGroup<T> parent: parentToChildrenMap.keySet())
			rootGroups.add(parent);
		for (SimilarGroup<T> parent: parentToChildrenMap.keySet())
			for (SimilarGroup<T> child: parentToChildrenMap.get(parent))
				rootGroups.remove(child);
	}
}
