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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;

/** static methods related to similar groups */
public class SimilarGroupMethods {
	private static Log log = LogFactory.getLog(SimilarGroupMethods.class);

	/* returns a map consisting of group -> its freq in the input */
	private static <T extends Comparable<? super T>> void
	    computeGroupFrequencies(List<Group<T>> input, Collection<SimilarGroup<T>> candidates)
	{
		// brute force, could be made more efficient by keeping a person->group index
		for (Group<T> b : input) {
			for (SimilarGroup<T> g : candidates)
				if (b.contains(g))
					g.freq++;
		}
		return;
	}


	/**
	 * find intersections till fixed point and compute frequency for each group.
	 * returns a run log and list of similar groups
	 */
	private static <T extends Comparable<? super T>> Set<SimilarGroup<T>>
	    intersectGroups(Collection<SimilarGroup<T>> startingGroups,
	                    int minSize,
			            GroupAlgorithmStats stats)
    {
		boolean fixedPoint = false;
		Set<SimilarGroup<T>> candidates = new LinkedHashSet<SimilarGroup<T>>();

		// add all the actually occurring (exact) recipient sets
		candidates.addAll(startingGroups);
		// allGroups will be the master list of all groups that we have (uptil
		// the previous iteration)

		Set<SimilarGroup<T>> newGroupsPrevIteration = candidates;
		// newGroupsPrevIteration will start off as all groups in the first iteration,
		// but reduce to only the newly derived groups at the end of each iteration.
		// we do this so as have to check for intersections only between the newly
		// derived groups and all other groups. trying to intersect each group with
		// every other group known in each iteration might be too expensive.

		int iteration = 1;
		while (!fixedPoint) {
			// newGroups will be the groups we derive in this iteration
			Set<SimilarGroup<T>> newGroups = new LinkedHashSet<SimilarGroup<T>>();

			// brute force: all to all intersection between existing groups and
			// groups newly created in prev. iteration can be made more efficient
			// by maintaining person -> group map and only intersecting those
			// groups that have at least one person in common.
			for (SimilarGroup<T> g1 : candidates)
				for (SimilarGroup<T> g2 : newGroupsPrevIteration)
				{
					SimilarGroup<T> newGroup
					= new SimilarGroup<T>(g1.intersect(g2));
					if (newGroup.size() == 0)
						continue;
					if (newGroup.size() < minSize)
						continue;
					// add to newGroups if we dont already have it
					if (!candidates.contains(newGroup)
					    && !newGroups.contains(newGroup))
						newGroups.add(newGroup);
				}

			log.info("Intersection iteration " + iteration + ": "
					 + newGroups.size() + " new sets");
			stats.intersectionGroupsAdded.add(new GroupStats(newGroups));

			for (SimilarGroup<T> g : newGroups)
				log.debug("new group: " + g);

			candidates.addAll(newGroups);

			iteration++;
			// reached fixed point when no new groups
			fixedPoint = (newGroups.size() == 0);
			newGroupsPrevIteration = newGroups;
		}

		return candidates;
	}


	/** just return freqs of each item in the given corpus */
	@SuppressWarnings("unused")
	private static <T extends Comparable<? super T>> Map<T, Integer>
	    computeIndivFreqs(List<Group<T>> input)
	{
		Map<T, Integer> result = new LinkedHashMap<T, Integer>();
		for (Group<T> g : input)
		{
		    // sometimes same person is present twice on the
		    // same message, in that case, do not double count
			Set<T> set = new LinkedHashSet<T>();
			for (T t : g.elements)
			{
				if (set.contains(t))
					continue;

				Integer I = result.get(t);
				if (I == null)
					result.put(t, 1);
				else
					result.put(t, I + 1);
			}
		}
		return result;
	}

	private static <T extends Comparable<? super T>> Set<SimilarGroup<T>>
	    selectGroupsWithMinFreq(Collection<SimilarGroup<T>> groups, int minFreq)
	{
		Set<SimilarGroup<T>> result = new LinkedHashSet<SimilarGroup<T>>();
		for (SimilarGroup<T> g : groups)
		{
			// should we select T ?
			// yes, if it is frequent and not subsumed
			// by a superset previously emit
			if (g.freq >= minFreq)
				result.add(g); // could also redistribute to first subset
		}
		return result;
	}

	/** select groups above mincount and not subsumed according to maxerror */
	private static <T extends Comparable<? super T>> List<SimilarGroup<T>>
	    selectGroupsNotSubsumed(List<SimilarGroup<T>> groups, float maxError)
	{
		// sort the groups by size
		Collections.sort(groups, new Comparator<SimilarGroup<T>>() {
			public int compare(SimilarGroup<T> g1, SimilarGroup<T> g2) {
				return g2.size() - g1.size();
			}
		});

		List<SimilarGroup<T>> selectedGroups = new ArrayList<SimilarGroup<T>>();
		for (SimilarGroup<T> g : groups)
		{
			// is it subsumed by previously selected groups
			boolean subsumed = false;
			for (SimilarGroup<T> selected : selectedGroups)
			{
				// if a superset exists with smaller than maxError rate,
				// subsumed is true
				if (selected.contains(g))
				{
					double error = selected.errorWRT(g);
					if (error <= maxError)
					{
						subsumed = true;
						log.debug(g + "\nsubsumed with error " + error
								  + " by\n " + selected);
						break;
					}
				}
			}

			if (!subsumed)
				selectedGroups.add(g);
		}

		return selectedGroups;
	}

	/** select groups above mincount and not subsumed according to maxerror */
	private static <T extends Comparable<? super T>> Set<SimilarGroup<T>>
	    selectGroupsWithMinSize(Collection<SimilarGroup<T>> groups, int minSize)
	{
		LinkedHashSet<SimilarGroup<T>> selectedGroups
		= new LinkedHashSet<SimilarGroup<T>>();
		for (SimilarGroup<T> g : groups)
			if (g.size() >= minSize)
				selectedGroups.add(g);

		return selectedGroups;
	}

	/*
	private static<T extends Comparable<? super T>> float
        computeErrorWRT(List<Group<T>> originalGroups,
                        SimilarGroup<T> superGroup,
                        SimilarGroup<T> group_i)
	{
		return 0.0f;
	}
    */

	// compute sims matrix. its symmetric.
	// note: diagonal entries should always be kept at 0.
	private static <T extends Comparable<? super T>> Pair<float[][], int[][]>
	    computeSims(List<SimilarGroup<T>> groups)
	{
		float sims[][] = new float[groups.size()][groups.size()];
		int interSize[][] = new int[groups.size()][groups.size()];
		for (int i = 0; i < groups.size(); i++)
		{
			SimilarGroup<T> group_i = groups.get(i);
			for (int j = i+1; j < groups.size(); j++)
			{
				SimilarGroup<T> group_j = groups.get(j);
				float sim = group_i.jaccardSim(group_j);
				sims[i][j] = sims[j][i] = sim;
				int intersectionSize = group_i.intersectionSize(group_j);
				interSize[i][j] = interSize[j][i] = intersectionSize;
			}
		}
		return new Pair<float[][], int[][]>(sims, interSize);
	}

	private static <T extends Comparable<? super T>> Set<SimilarSuperGroup<T>>
	    manufactureSuperGroups(List<Group<T>> originalMessages,
							   Set<SimilarGroup<T>> startingGroups, float maxError,
							   float groupMembersSimThreshold)
    {
        // result will contain only the new supergroups that we generate
		Set<SimilarSuperGroup<T>> result = new LinkedHashSet<SimilarSuperGroup<T>>();

		// convert to list, its easier to use indices
		// select only groups of size > 2 as candidates for merging
		Set<SimilarGroup<T>> s = selectGroupsWithMinSize(startingGroups, 2);
		List<SimilarGroup<T>> similarGroupsList = new ArrayList<SimilarGroup<T>>();
		similarGroupsList.addAll(s);

		if (similarGroupsList.size() == 0)
			return result; // empty result if no groups of size 2 or more

		// compute sims matrix. its symmetric.
		// note: diagonal entries should always be kept at 0.
		Pair<float[][], int[][]> matrix = computeSims(similarGroupsList);
		float sims[][] = matrix.getFirst();
		int interSize[][] = matrix.getSecond();

		// merge best non-used pair of groups in whole matrix, till done
		// heuristic: once a group is used for a merge, it will not be used again.
		//            used[] keeps track of whether a group has been used
		boolean[] used = new boolean[similarGroupsList.size()];

		while (true)
		{
			float bestSim = -0.1f;
			int bestSim_i = -1, bestSim_j = -1;
			// find best sim in the whole matrix, ignoring used rows and cols
			for (int i = 0; i < similarGroupsList.size(); i++)
			{
				if (used[i])
					continue;
				for (int j = i+1; j < similarGroupsList.size(); j++)
				{
					if (used[j])
						continue;
					if (sims[i][j] > bestSim)
					{
						bestSim = sims[i][j];
						bestSim_i = i;
						bestSim_j = j;
					}
				}
			}

			if (bestSim_i == -1 || bestSim_j == -1)
				break;
			if (bestSim < groupMembersSimThreshold && interSize[bestSim_i][bestSim_j] < 3)
				break;

			// best* is the best in the whole matrix. mark these two groups used
			used[bestSim_i] = used[bestSim_j] = true;

			// create the new supergroup
			SimilarGroup<T> group_i = similarGroupsList.get(bestSim_i);
			SimilarGroup<T> group_j = similarGroupsList.get(bestSim_j);
			log.info("Merging most similar groups in this iteration: " + bestSim + " between G"
					  + bestSim_i + " " + group_i + " + G" + bestSim_j
					  + " " + group_j);
			SimilarSuperGroup<T> superGroup	= new SimilarSuperGroup<T>(group_i, group_j);
			float u1 = group_i.utility * (superGroup.size() / group_i.size());
			float u2 = group_j.utility * (superGroup.size() / group_j.size());
			superGroup.utility = Math.max (u1, u2);
			
			// ignore if we already generated this supergroup,
			// or if our starting groups contain it
			if (result.contains(superGroup) || startingGroups.contains(superGroup)) {
				continue;
			}

			// in theory could also check if the error is reasonable
			// if (computeErrorWRT(originalGroups, superGroup, group_i) < maxError
			//     && computeErrorWRT(originalGroups, superGroup, group_j) < maxError)
			//     result.add(superGroup);

			// it's official. we can manufacture a new group.
			result.add(superGroup);

			// supergroup could be a superset of other groups too.
			// heuristic: mark them used up.
			// another heuristic: let them be
			for (int i = 0; i < similarGroupsList.size(); i++)
			{
				if (superGroup.contains(similarGroupsList.get(i)))
					used[i] = true;
			}

			// for convenience, we'll replace group i with supergroup and
			// recompute sims for just that row and col sims diagonal will
			// still be 0 because used[i] is currently true
			similarGroupsList.set(bestSim_i, superGroup);
			for (int i = 0; i < similarGroupsList.size(); i++)
			{
				if (used[i])
					continue;
				float sim = superGroup.jaccardSim(similarGroupsList.get(i));
				sims[bestSim_i][i] = sims[i][bestSim_i] = sim;
			}

			used[bestSim_i] = false; // revive entry i, its now the supergroup
		}

		log.info("Manufactured " + result.size() + " supergroups");

		return result;
	}

	private static <T extends Comparable<? super T>> void
	    doDFS(List<SimilarGroup<T>> groups,
	          boolean used[], int lastAddedIdx,
			  List<SimilarGroup<T>> result)
	{
		SimilarGroup<T> lastAddedGroup = groups.get(lastAddedIdx);

		// find all unused groups with non-zero sim with lastadded group
		List<Pair<Integer, Float>> similarGroupsInfo
		= new ArrayList<Pair<Integer, Float>>();
		for (int j = 0; j < groups.size(); j++)
		{
			if (used[j])
				continue;

			float sim = lastAddedGroup.jaccardSim(groups.get(j));
			if (sim > 0.0001)
				similarGroupsInfo.add(new Pair<Integer, Float>(j, sim));
		}

		// now sort sim group idx's according to decreasing similarity
		Util.sortPairsBySecondElement(similarGroupsInfo);

		for (Pair<Integer, Float> p : similarGroupsInfo)
		{
			int groupIdx = p.getFirst();
			// groupIdx could have become used in the meantime in calls to doDFS()
			if (used[groupIdx])
				continue;
			result.add(groups.get(groupIdx));
			used[groupIdx] = true;
			doDFS(groups, used, groupIdx, result);
		}
	}


//	private static <T extends Comparable<? super T>> void
//	    dumpGroupsForDebug(String title, Set<SimilarGroup<T>> set)
//	{
//		if (!log.isDebugEnabled())
//			return;
//		List<SimilarGroup<T>> list = new ArrayList<SimilarGroup<T>>();
//		list.addAll(set);
//		dumpGroupsForDebug(title, list);
//	}

	/** This is the alternative group algorithm, described in the IUI-2011 paper */
	public static <T extends Comparable<? super T>> GroupHierarchy<T>
		findContactGroupsIUI(List<Group<T>> input,
				           int MINCOUNT,
				           int MIN_GROUP_SIZE,
				           float MAX_SUBSUMPTION_ERROR,
				           float MIN_MERGE_GROUP_SIM,
				           String utilityType,
				           float UTILITY_MULTIPLIER,
				           GroupAlgorithmStats<T> stats)
	{
		log.info ("-----------------------------------------------   GROUPER  -----------------------------------------------\n");

		long startTimeMillis = System.currentTimeMillis();
		// copy over the alg. parameters so everything is in one place
		stats.MIN_GROUP_SIZE = MIN_GROUP_SIZE;
		stats.MIN_FREQ = MINCOUNT;
		stats.MAX_SUBSUMPTION_ERROR = MAX_SUBSUMPTION_ERROR;
		stats.MIN_MERGE_GROUP_SIM = MIN_MERGE_GROUP_SIM;
		int MAX_EDGES = 1000;
		Set<T> hypers = Grouper.findHyperConnectedElementsRaw(input, MAX_EDGES);

		for (Group<T> g: input)
		{
			for (Iterator<T> it = g.elements.iterator(); it.hasNext(); )
			{
				T t = it.next();
				if (hypers.contains(t))
					it.remove();
				if (g.elements.size() == 0)
					continue;					
			}
		}
		
		List<SimilarGroup<T>> exactGroups = Grouper.convertToSimilarGroups(input);

		//int nUniqueGroups = exactGroups.size();
		stats.startingGroups = new GroupStats<T>(exactGroups);
	//	dumpGroupsForDebug("Starting Groups", exactGroups);

		Set<SimilarGroup<T>> candidates = selectGroupsWithMinSize(exactGroups, MIN_GROUP_SIZE);
		stats.groupsWithMinSize = new GroupStats<T>(candidates);
	//	dumpGroupsForDebug("Groups with min size " + MIN_GROUP_SIZE, candidates);

		log.warn ("Intersections are disabled because taking too long for Ken Lay!!");
		//candidates // returns (log, result)
		//= SimilarGroupMethods.intersectGroups(candidates, MIN_GROUP_SIZE, stats);
		stats.groupsAfterIntersections = new GroupStats<T>(candidates);
	//	dumpGroupsForDebug("Groups after intersections ", candidates);

		// verify
		for (SimilarGroup<T> sg : candidates) {
			Util.softAssert(candidates.contains(sg));
			Util.softAssert(sg.size() >= MIN_GROUP_SIZE);
		}

		// now filter based on min freq
		computeGroupFrequencies(input, candidates);
		candidates = SimilarGroupMethods.selectGroupsWithMinFreq(candidates, MINCOUNT);
		stats.groupsWithMinFreqAndMinSize = new GroupStats<T>(candidates);
	//	dumpGroupsForDebug("Groups with min. freq. " + MINCOUNT, candidates);

		// compute utilities
		// Map<T, Integer> indivFreqs =
		// SimilarGroupMethods.computeIndivFreqs(input);
		for (SimilarGroup<T> sg : candidates)
		{
			if ("linear".equals(utilityType))
				sg.computeLinearUtility();
			else if ("square".equals(utilityType))
				sg.computeSquareUtility();
			else
				sg.computeExpUtility(UTILITY_MULTIPLIER);

			// sg.computeZScore(indivFreqs, input.size());
		}

		// convert candidates from set to list now, because we need sorting etc

		List<SimilarGroup<T>> candidateList = new ArrayList<SimilarGroup<T>>();
		candidateList.addAll(candidates);

		// remove subsumed groups
		List<SimilarGroup<T>> selectedGroups = SimilarGroupMethods.selectGroupsNotSubsumed(candidateList, MAX_SUBSUMPTION_ERROR);
		stats.groupsAfterSubsumption = new GroupStats<T>(selectedGroups);
	//	dumpGroupsForDebug("Groups after subsumption with error "
	//	                   + MAX_SUBSUMPTION_ERROR, selectedGroups);

		// now compute hierarchy, just to identify the root groups
		GroupHierarchy<T> hierarchy = new GroupHierarchy<T>(selectedGroups);
		// Map<SimilarGroup<T>, List<SimilarGroup<T>>> parentToChildGroupMap
		// = hierarchy.parentToChildrenMap;
		Set<SimilarGroup<T>> rootGroups = hierarchy.rootGroups;
		log.info("hierarchy: #root groups = " + rootGroups.size());

		// add supergroups. supergroups never subsume other subgroups
		Set<SimilarSuperGroup<T>> manufacturedGroups = SimilarGroupMethods.manufactureSuperGroups(input, rootGroups, MAX_SUBSUMPTION_ERROR, MIN_MERGE_GROUP_SIM);
		stats.manufacturedGroups = new GroupStats(manufacturedGroups);
		selectedGroups.addAll(manufacturedGroups);
		stats.finalGroups = new GroupStats<T>(selectedGroups);
	//	dumpGroupsForDebug("Final groups " + MAX_SUBSUMPTION_ERROR, selectedGroups);

		// recompute hierarchy. its easier than
		// trying to update the existing hierarchy
		hierarchy = new GroupHierarchy<T>(selectedGroups);
		rootGroups = hierarchy.rootGroups;
		stats.finalRootGroups = new GroupStats(rootGroups);
		long endTimeMillis = System.currentTimeMillis();
		stats.executionTimeMillis = endTimeMillis - startTimeMillis;
		return hierarchy;
	}

	/** returns top groups based on freq. */
	public static <T extends Comparable<? super T>> List<SimilarGroup<T>>
		topGroups(GroupHierarchy<T> hierarchy, int nGroups)
	{
		List<SimilarGroup<T>> result = new ArrayList<SimilarGroup<T>>();

		// annoying set -> list conversion. no good reason
		List<SimilarGroup<T>> similarGroupsList = new ArrayList<SimilarGroup<T>>();
		similarGroupsList.addAll(hierarchy.getAllGroups());

		if (log.isDebugEnabled())
			log.debug("# similar groups = " + similarGroupsList.size());

		// sort the groups by frequency of occurrence
		Collections.sort(similarGroupsList, new Comparator<SimilarGroup<T>>() {
			public int compare(SimilarGroup<T> g1, SimilarGroup<T> g2) {
				if (g2.utility == g1.utility)
					return 0;
				else if (g2.utility > g1.utility)
					return 1;
				else
					return -1;
			}
		}); // sorts of frequency count of basket

		int i = 0;
		for (; i < nGroups && i < similarGroupsList.size(); i++)
			result.add(similarGroupsList.get(i));
		if (i < similarGroupsList.size())
			log.info ("group utility cutoff is : " + similarGroupsList.get(i).utility);
		return result;
	}



	/**
	 * this is a pure display thing to cluster similar root groups together.
	 * we'll try and cluster root groups together following DFS paths in order
	 * of similarity
	 */
	public static <T extends Comparable<? super T>> List<SimilarGroup<T>>
	    orderGroupsBySimilarity(List<SimilarGroup<T>> groups)
	{
		// we'll generate a better ordering in result
		List<SimilarGroup<T>> result = new ArrayList<SimilarGroup<T>>();

		// used will track whether the corresponding index in the input groups
		// has been already used up, i.e. added to result
		boolean used[] = new boolean[groups.size()];

		while (true)
		{
			int nextIdx = 0;
			for (nextIdx = 0; nextIdx < groups.size(); nextIdx++)
				if (!used[nextIdx])
					break;

			// if all groups are used, next == groups.size(), so we are done
			if (nextIdx == groups.size())
				break;

			// mark group[next] as done.
			result.add(groups.get(nextIdx));
			used[nextIdx] = true;

			// invoke DFS
			doDFS(groups, used, nextIdx, result);
		}

		return result;
	}

	//compute the exact frequency of the group instead of including its superset's msgs
	private static <T extends Comparable<? super T>> void
		computeGroupFrequenciesv2(List<Group<T>> input, Collection<SimilarGroup<T>> candidates)
	{
		for (Group<T> b : input)
		{
			for (SimilarGroup<T> g : candidates)
				if (b.equals(g))
					g.freq++;
		}
		return;
	}

}
