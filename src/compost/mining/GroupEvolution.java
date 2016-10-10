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
package edu.stanford.muse.mining;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONException;

import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.CalendarUtil;
import edu.stanford.muse.groups.Group;
import edu.stanford.muse.groups.GroupAlgorithmStats;
import edu.stanford.muse.groups.GroupHierarchy;
import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.groups.SimilarGroupMethods;
import edu.stanford.muse.webapp.HTMLUtils;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.IndexUtils;

public class GroupEvolution {

	// helper class for Diana's stuff
	static class GlobalGroups {

		private List<String> windowDescriptions;
		int nClusters;

		public GlobalGroups (List<String> timeClusterDescriptions)
		{
			this.windowDescriptions = timeClusterDescriptions;
			this.nClusters = windowDescriptions.size();
		}

		// global groups is a time vs group matrix, containing message counts
		public List<SimilarGroup> globalGroups = new ArrayList();
		public List<Object> globalGroupCounts = new ArrayList();

		// for timestep idx with given description, these are the given groups
		public void setGroupsForTimeCluster(List<SimilarGroup<String>> groups, int timeClusterIdx)
		{
			for (SimilarGroup g: groups)
			{
				// update counts for this group
				int groupIdx = -1;
				// check if we've seen this group, globally before
				boolean found = false;
				for (SimilarGroup<String> gg: globalGroups)
				{
					groupIdx++;
					if (gg.sameMembersAs(g))
					{
						found = true;
						break;
					}
				}

				// add it to global groups if we have not
				if (!found)
				{
					globalGroups.add(g);
					groupIdx = globalGroups.size()-1;
					int[] countsByTime = new int[nClusters];
					globalGroupCounts.add(countsByTime);
				}

				// update the counts for this group in the global count matrix
				int[] counts = (int []) globalGroupCounts.get(groupIdx);
				counts[timeClusterIdx] = g.freq;
			}
		}

		public void emitGlobalGroups(String countsFile, String namesFile) throws IOException
		{
			PrintWriter countsOut = new PrintWriter (countsFile);
			PrintWriter namesOut = new PrintWriter (namesFile);

			countsOut.print ("--\t");
			for (int i = 0; i < nClusters; i++)
				countsOut.print (windowDescriptions.get(i) + "\t");
			countsOut.println("");

			for (int i = 0; i < globalGroups.size(); i++)
			{
				countsOut.print ("G" + i + "\t");
				int[] counts = (int[]) globalGroupCounts.get(i);
				for (int x: counts)
					countsOut.print (x + "\t");
				countsOut.println ();

				namesOut.print ("G" + i + "\t");
				for (Object s: globalGroups.get(i).elements) // s is always a string
					namesOut.print(s + "\t");
				namesOut.println();
			}
			countsOut.close();
			namesOut.close();
		}
	} // end of global groups

	/**
	 * prefix: file prefix for temp files
	 */
	public static String groupsByTime(String prefix, String userKey, Collection<EmailDocument> allDocs, AddressBook addressBook, float MAX_ERROR, int MINCOUNT, int MIN_GROUP_SIZE, float MIN_MERGE_GROUP_SIM, boolean monthsNotYears, int windowSize, int windowScroll) throws IOException, JSONException
	{
		List<EmailDocument> list = new ArrayList<EmailDocument>();
		list.addAll(allDocs);
		int slide = windowScroll;
		List<IndexUtils.Window> timeClusters = IndexUtils.docsBySlidingWindow(list, windowSize, slide);

		String fullHtml = "<b>" + timeClusters.size() + " window(s), window size: " + windowSize + " months, sliding backward by " + slide + " months</b><br/>";

		fullHtml += "Matrices: <a href=\"" + userKey + "/counts.txt\">counts.txt</a>, <a href=\"" + userKey + "/names.txt\">names.txt</a><br/>";

		for (IndexUtils.Window w: timeClusters)
			fullHtml += w.toString() + "<br/>";

		// set up Diana's data
		List<String> clusterDescr = new ArrayList<String>();
		for (IndexUtils.Window w: timeClusters)
			clusterDescr.add(CalendarUtil.getDisplayMonth(w.start));
		GlobalGroups globalGroups = new GlobalGroups(clusterDescr);

		List<SimilarGroup<String>> prevGroups = null;
		int windowCount = 0;

		int count = -1;
		for (IndexUtils.Window w: timeClusters)
		{
			List<EmailDocument> docs = w.docs;
			count++;

//			Calendar start = w.start;
//			Calendar end = w.end;
			String description = w.toString();

			if (docs.size() == 0)
			{
				// empty cluster, toString header... more for debugging
				fullHtml += "<hr><h3>" + description + "</h3><br/>";
				continue;
			}

			List<Group<String>> input = addressBook.convertEmailsToGroups(docs);
			GroupAlgorithmStats<String> stats = new GroupAlgorithmStats<String>();
			GroupHierarchy<String> hierarchy = SimilarGroupMethods.findContactGroupsIUI(input, MINCOUNT, MIN_GROUP_SIZE, MAX_ERROR, MIN_MERGE_GROUP_SIM, "linear", 1.4f, stats);
			List<SimilarGroup<String>> groups = hierarchy.getAllGroups();

			// now we have the final groups for this time cluster
			// tell global groups about it so it can build up the counts matrix
			globalGroups.setGroupsForTimeCluster(groups, count);

			String tmp = description;
			// sanitize description so we get a reasonable file name
			tmp = tmp.replace ("[", "").replace(")", "").replace(" ", "_").replace(",","_");
			windowCount++;

			String html = "<hr><h3>" + description + "</h3><br/>";
			GroupCompareResult gcr = new GroupCompareResult();
			html += compareGroups(prevGroups, groups, addressBook, MIN_MERGE_GROUP_SIM, gcr);

			fullHtml += html;
			prevGroups = groups;
		}

		globalGroups.emitGlobalGroups(prefix + File.separator + "counts.txt", prefix + File.separator + "names.txt");
		return fullHtml;
	}

	/** finds the basket in list which has the most jaccard sim to b.
	 * if list is empty, returns an empty basket
	 */
	/*
	private static Basket closestBasket(Basket b, Set<Basket> set)
	{
		float max = -1;
		Basket bestFit = null;

		for (Basket sb: set)
		{
			float sim = b.jaccardSim(sb);
			if (sim > max)
			{
				max = sim;
				bestFit = sb;
			}
		}

		if (bestFit == null)
			bestFit = new Basket(new ArrayList());

		return bestFit;
	}

*/
	/** finds the group in list which has the most jaccard sim to b.
	 * if list is empty, returns an empty group
	 */
	private static Group<String> closestGroup(Group<String> b, Set<Group<String>> set)
	{
		float max = -1;
		Group<String> bestFit = null;

		for (Group<String> sb: set)
		{
			float sim = b.jaccardSim(sb);
			if (sim > max)
			{
				max = sim;
				bestFit = sb;
			}
		}

		if (bestFit == null)
			bestFit = new Group<String>(new ArrayList());

		return bestFit;
	}


	public static class GroupCompareResult
	{
		List<Group<String>> addedGroups = new ArrayList(), deletedGroups = new ArrayList();
		List<GroupChange> changedGroups = new ArrayList();
	}

	public static class GroupChange {
	//	List<Group<String>> originalGroup;
	//	List<Group<String>> changedGroup;
		List<String> removed = new ArrayList<String>(), added = new ArrayList<String>();
	}

	/** returns html for difference of b from a. also puts the info into result */
	private static String compareGroups (List<SimilarGroup<String>> a, List<SimilarGroup<String>> b, AddressBook addressBook, float minSimilarity, GroupCompareResult result)
	{
		Set<Group<String>> aGroups = new LinkedHashSet<Group<String>>();
		Set<Group<String>> bGroups = new LinkedHashSet<Group<String>>();
		Set<Group<String>> intersection = new LinkedHashSet<Group<String>>();

		// compute groups - new, deleted, retained
		{
			if (a != null)
				aGroups.addAll(a);
			bGroups.addAll(b);

			intersection.addAll(aGroups);
			intersection.retainAll(bGroups);

			// intersection contains the baskets that haven't changed
			bGroups.removeAll(intersection);
			aGroups.removeAll(intersection);
		}

		// now generate HTML:
		{
			// aBGroupsDiffed will track which of the A groups have been diffed with respect to some B group
			// the remaining A groups will be reported as completely deleted
			Set<Group> aGroupsMapped = new LinkedHashSet();
			StringBuilder sb = new StringBuilder();
			if (bGroups.size() > 0)
			{
				sb.append ("Groups are:<br/>\n");
				for (Group group: bGroups)
					sb.append (group + "<br/>\n");
//					sb.append(HTMLUtils.getHTMLForGroup(addressBook, group) + "<br/>\n");

				sb.append("<br/>\n<b>New or changed groups</b><br/>\n");
				for (Group bGroup: bGroups)
				{
					// find the closest A to bGroup
					Group<String> closestA = closestGroup(bGroup, aGroups);

					Group<String> common = closestA.intersect(bGroup);
					Group<String> onlyB = bGroup.minus(common);
					Group<String> onlyA = closestA.minus(common);

					float sim = ((float) common.size())/(common.size() + onlyA.size() + onlyB.size());
					if (sim < minSimilarity)
					{
						// treat b as an entirely new group
						sb.append ("<font color=\"green\">New group: " + bGroup + "</font><br/>");
						result.addedGroups.add(bGroup);
						continue;
					}

					// b is not entirely new, it maps to some a with the min. similarity
					aGroupsMapped.add(closestA);

					GroupChange gc = new GroupChange();
					result.changedGroups.add(gc);

					sb.append ("Changed group: ");
					if (onlyB.size() > 0)
					{
						for (String added: onlyB.elements)
							gc.added.add(added); // heh heh
						sb.append ("<font color=\"green\"> + " + onlyB + "</font> "); // these members got added to the group
					}

					// if no common, then its an entirely new group
					if (onlyA.size() > 0)
					{
						sb.append ("<font color=\"red\"> - " + onlyA + "</font> ");
						for (String removed: onlyA.elements)
							gc.removed.add(removed);
					}
					// common size should be at least one
					sb.append ("<font color=\"black\"> " + common + "</font>");
					sb.append ("<br/>\n");
				}
			}

			// remove all the a baskets that were considered for diff'ing
			aGroups.removeAll(aGroupsMapped);

			if (aGroups.size() > 0)
			{
				sb.append("<br/>\n<b>Deleted groups</b><br/>\n");
				for (Group aGroup: aGroups)
				{
					sb.append("<span color=\"#c04040\">" + aGroup + "</span>");
					sb.append ("<br/>\n");
					result.deletedGroups.add(aGroup);
				}
			}

			if (intersection.size() > 0)
			{
				sb.append("<br/>\n<b>Unchanged groups</b><br/>\n");
				for (Group group: intersection)
				{
					sb.append(HTMLUtils.getHTMLForGroup(addressBook, group));
					sb.append ("<br/>\n");
				}
			}

			return sb.toString();
		}
	}

	/** returns html for difference of b from a */
	/*
	private static String compareBaskets (List<SimilarBasket<String>> a, List<SimilarBasket<String>> b, AddressBook addressBook)
	{
		Set<Basket> aBaskets = new LinkedHashSet<Basket>();
		Set<Basket> bBaskets = new LinkedHashSet<Basket>();
		Set<Basket> intersection = new LinkedHashSet<Basket>();

		// compute groups - new, deleted, retained
		{
			if (a != null)
				for (SimilarBasket sb: a)
					aBaskets.add(sb.basket);
			for (SimilarBasket sb: b)
				bBaskets.add(sb.basket);

			intersection.addAll(aBaskets);
			intersection.retainAll(bBaskets);

			// intersection contains the baskets that haven't changed
			bBaskets.removeAll(intersection);
			aBaskets.removeAll(intersection);
		}

		// now generate HTML:
		{
			// aBasketsDiffed will track which of the A groups have been diffed with respect to some B group
			// the remaining A groups will be reported as completely deleted
			Set aBasketsDiffed = new LinkedHashSet();
			StringBuilder sb = new StringBuilder();
			if (bBaskets.size() > 0)
			{
				sb.append ("Groups are:<br/>\n");
				for (Basket basket: bBaskets)
					sb.append(HTMLUtils.getHTMLForGroup(addressBook, basket) + "<br/>\n");

				sb.append("<br/>\n<b>New or changed groups</b><br/>\n");
				for (Basket bBasket: bBaskets)
				{
					// find the closest A to bBasket
					Basket closestA = closestBasket(bBasket, aBaskets);
					aBasketsDiffed.add(closestA);

					Basket common = closestA.intersect(bBasket);
					bBasket = bBasket.minus(common);
					closestA = closestA.minus(common);

					if (bBasket.size() > 0)
						sb.append ("<font color=\"green\"> + " + HTMLUtils.getHTMLForGroup(addressBook, bBasket) + "</font>");

					// if no common, then its an entirely new group
					if (common.size() > 0)
					{
						if (closestA.size() > 0)
							sb.append ("<font color=\"red\"> - " + HTMLUtils.getHTMLForGroup(addressBook, closestA) + "</font>");
						sb.append ("<font color=\"black\"> " + HTMLUtils.getHTMLForGroup(addressBook, common) + "</font>");
					}
					sb.append ("<br/>\n");
				}
			}

			// remove all the a baskets that were considered for diff'ing
			aBaskets.removeAll(aBasketsDiffed);

			if (aBaskets.size() > 0)
			{
				sb.append("<br/>\n<b>Deleted groups</b><br/>\n");
				for (Basket basket: aBaskets)
				{
					sb.append(HTMLUtils.getHTMLForGroup(addressBook, basket));
					sb.append ("<br/>\n");
				}
			}

			if (intersection.size() > 0)
			{
				sb.append("<br/>\n<b>Unchanged groups</b><br/>\n");
				for (Basket basket: intersection)
				{
					sb.append(HTMLUtils.getHTMLForGroup(addressBook, basket));
					sb.append ("<br/>\n");
				}
			}

			return sb.toString();
		}
	}
	*/
}
