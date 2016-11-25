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
package edu.stanford.muse.index;


import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.groups.Group;
import edu.stanford.muse.groups.GroupHierarchy;
import edu.stanford.muse.groups.JSONGroup;
import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.JSONUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import org.json.JSONException;
import org.json.JSONObject;

import javax.mail.internet.InternetAddress;
import java.io.Serializable;
import java.util.*;

/* should probably move this to groups package. 
 * move it the next time we are going to break existing session files. */
/** assigns colors based on email participants to tag cloud terms */
public class GroupAssigner implements Serializable {

	private final static long serialVersionUID = 1L;

	// max # of groups and individuals to assign colors for
	// note: actual number may be lower
	// these colors are duplicated in cloud.css
	public static final int COLORS[] = new int[]{
		0x1f77b4, 0xaec7e8, 0xff7f0e, 0xffbb78, 0x2ca02c, 0x98df8a, 0xd62728, 0xff9896, 0x9467bd, 0xc5b0d5,
		0x8c564b, 0xc49c94, 0xe377c2, 0xf7b6d2, 0x7f7f7f, 0xc7c7c7, 0xbcbd22, 0xdbdb8d, 0x17becf, 0x9edae5};

	private int nGroupsToColor;
	private int nIndividualsToColor;

	// these are the baskets to which colors have been assigned
	// position on list indicates colors for that basket
	private List<SimilarGroup<String>> selectedGroups = new ArrayList<SimilarGroup<String>>();
	transient private Map<String,Integer> nameToIdxMap = new LinkedHashMap<String, Integer>(); // must be kept in sync with selectedGroups

	private AddressBook addressBook;
	private Set<String> ownAddrs = new LinkedHashSet<String>(); // just a set repn. of array of strings

	public List<SimilarGroup<String>> getSelectedGroups()
	{
		return selectedGroups;
	}

	private void setSelectedGroups(List<SimilarGroup<String>> sg)
	{
		selectedGroups = sg;
		nameToIdxMap.clear();
		for (int i = 0; i < sg.size(); i++) {
			if (sg.get(i).name != null)
				nameToIdxMap.put(sg.get(i).name, i);
		}
	}

	public int getGroupIdx(String name)
	{
		if (nameToIdxMap == null) // transient field deserialized as null
			nameToIdxMap = new LinkedHashMap<String, Integer>();

		if (nameToIdxMap.size() != selectedGroups.size())
			setSelectedGroups(selectedGroups); // reprocess nameToIdxMap

		return nameToIdxMap.get(name);
	}

	public GroupAssigner()
	{
//		this.nGroupsToColor = nGroups;
//		this.nIndividualsToColor = nIndividuals;
	}

	public GroupAssigner(List<JSONGroup> topGroups, AddressBook ab)
	{
		this.addressBook = ab;
		selectedGroups.clear();
		for (JSONGroup g : topGroups)
		{
			if (g != null && g.members.size() > 0)
			{
				Set<String> set = new LinkedHashSet<String>();
				set.addAll(g.members);
				for (String s: g.members)
					if (s == null)
						Util.breakpoint();

				if (Util.nullOrEmpty(g.name) && g.members.size() == 1)
					g.name = Util.userIdFromEmail(g.members.get(0));
				SimilarGroup<String> sg = new SimilarGroup<String>(g.name, set);
				selectedGroups.add(sg);
			}
		}

		setSelectedGroups(selectedGroups); // reprocess nameToIdxMap
	}

	public String getHierarchyJSON(int nGroups) throws JSONException
	{
		List<SimilarGroup<String>> groups = getSelectedGroups();
		GroupHierarchy<String> hierarchy = new GroupHierarchy<String>(groups);
		Map<SimilarGroup<String>, List<SimilarGroup<String>>> parentToChildGroupMap = hierarchy.parentToChildrenMap;
		List<SimilarGroup<String>> rootGroups = new ArrayList<SimilarGroup<String>>();
	    rootGroups.addAll(hierarchy.rootGroups);

	    //Map<SimilarGroup<String>, List<SimilarGroup<String>>> emptyMap
	    //= new LinkedHashMap<SimilarGroup<String>, List<SimilarGroup<String>>>();
	    JSONObject obj = JSONUtils.jsonForHierarchy(addressBook, rootGroups, parentToChildGroupMap);
		return obj.toString();		
	}
	
	/** given a list of multi-person groups, adds 1-person groups for the most freq. individuals */
	public void setupGroups (Collection<EmailDocument> docs, List<SimilarGroup<String>> topGroups, AddressBook addressBook, int nIndividuals)
	{
		setSelectedGroups(topGroups);
		this.addressBook = addressBook;
		nIndividualsToColor = nIndividuals;
		if (nIndividuals > 0)
		{
			// add the top 5 individuals. note sortedContacts() returns self contacts as well, which is what we want.
			List<Contact> cList = addressBook.sortedContacts(docs);
			int n = 0;

			for (Contact c: cList)
			{
				if (c == addressBook.getContactForSelf())
					continue;

				// check if this ci already exists in any groups, if so don't include it
				String email = c.getCanonicalEmail();
				/* disabling the check, so we get all strong individuals even if they are covered by a group.
				for (Group<String> b : coloredGroups)
					if (b.contains(email))
						continue outer;
				*/
				// create a new group for this indiv.
				SimilarGroup<String> b = new SimilarGroup<String>(email);
				selectedGroups.add(b);
				n++;

				if (n >= nIndividuals)
					break;
			}
		}

		Map <Contact, Pair<List<Date>, List<Date>>> map = EmailUtils.computeContactToDatesMap(addressBook, docs);
		for (SimilarGroup<String> g: selectedGroups)
			if (Util.nullOrEmpty(g.name))
			{
				if (g.elements.size() == 1)
					g.name = Util.userIdFromEmail(g.elements.get(0));
				else
					g.name = Util.userIdFromEmail(mostProlificPerson(g, addressBook, map)) + "+" + (g.elements.size()-1);
			}
	}
	
    /** returns the (possibly) best person to represent the group - best effort only */
	private static String mostProlificPerson(SimilarGroup<String> g, AddressBook ab, Map <Contact, Pair<List<Date>, List<Date>>> map)
	{
	    int maxVolume = -1;
        String mostProlific = null;

        for (String s: g.elements)
        {
            Contact c = ab.lookupByEmail(s);
            if (c == null)
                continue; // shouldn't happen

            int volume = 0;
            // we'll use c.inDates and c.outDates to compute volume for c for now even though they may be unreliable
            Pair<List<Date>, List<Date>> pair = map.get(c);
            if (pair != null)
            	volume = pair.getFirst().size() + pair.getSecond().size();
            
            if (volume > maxVolume)
            {
                mostProlific = s;
                maxVolume = volume;
            }
        }

        String result = (mostProlific != null) ? mostProlific : g.elements.get(0);
        return result;

	}

	/** returns selected groups, just ordering the individuals before the multi-person groups */
	public List<SimilarGroup<String>> getSelectedGroupsIndivBeforeMulti()
	{
		List<SimilarGroup<String>> reorderedGroups = new ArrayList<SimilarGroup<String>>();
  		for (SimilarGroup<String> g: selectedGroups)
			if (g.size() == 1)
				reorderedGroups.add(g);
		for (SimilarGroup<String> g: selectedGroups)
			if (g.size() > 1)
				reorderedGroups.add(g);
		return reorderedGroups;
	}

	/** assigns weights to colors based on similarity and returns a color -> weight map*/
	public Map<Integer, Float> getAssignedColorWeights(EmailDocument ed)
	{
		Map<Integer, Float> result = new LinkedHashMap<Integer, Float>();
		if (addressBook == null || ed == null)
			return result;
		List<String> rawEmailAddrs = ed.getParticipatingAddrsExcept(ownAddrs);

		List<String> canonicalEmailAddrs = addressBook.convertToCanonicalAddrs(rawEmailAddrs);
		Collections.sort(canonicalEmailAddrs);
		Group<String> emailBasket = new Group<String>(canonicalEmailAddrs);

		for (int color = 0; color < selectedGroups.size(); color++)
		{
			Group<String> b = selectedGroups.get(color);
			float sim = emailBasket.jaccardSim(b);
			if (sim > 0)
				result.put(color, sim);
		}
		return result;
	}

	/** returns a map of: color -> # docs owned by that color */
	public Map<Integer, Integer> documentsOwnedByColorsMap(Collection<EmailDocument> docs)
	{
		Map<Integer, Integer> colorToDocCount = new LinkedHashMap<Integer, Integer>();
		for (EmailDocument ed: docs)
		{
			Map<Integer, Float> map = getAssignedColorWeights(ed);
			Map.Entry<Integer, Float> best = highestValueEntry(map);
			if (best != null)
			{
				int bestColor = best.getKey();
				Integer I = colorToDocCount.get(bestColor);
				if (I == null)
					colorToDocCount.put (bestColor, 1);
				else
					colorToDocCount.put (bestColor, I+1);
			}
		}
		return colorToDocCount;
	}

	/** assigns weights to colors based on similarity and returns a color -> weight map*/
	public Map<Integer, Float> getAssignedColorWeights(Contact c)
	{
		Map<Integer, Float> result = new LinkedHashMap<Integer, Float>();
		List<String> canonicalEmailAddrs = new ArrayList<String>();
		canonicalEmailAddrs.add(c.getCanonicalEmail());
		Group<String> emailBasket = new Group<String>(canonicalEmailAddrs);

		for (int color = 0; color < selectedGroups.size(); color++)
		{
			Group<String> b = selectedGroups.get(color);
			float sim = emailBasket.jaccardSim(b);
			if (sim > 0)
				result.put(color, sim);
		}

		return result;
	}

	/** assigns weights to colors based on similarity and returns a color -> weight map*/
	public int getBestColorForAddress(InternetAddress a)
	{
		Contact c = addressBook.lookupByEmail( a.getAddress());
		if (c == null)
			return -1;
		Map<Integer, Float> map = getAssignedColorWeights(c);
		Map.Entry<Integer, Float> entry = highestValueEntry(map);
		if (entry == null)
			return -1;
		int color = highestValueEntry(map).getKey();
		return color;
	}

	/** gets the highest float value for the given map. null if no entry in the map */
	public static<T> Map.Entry<T, Float> highestValueEntry(Map<T, Float> map)
	{
		Map.Entry<T, Float> max = null;

		if (map == null)
			return null;

		for (Map.Entry<T, Float> f: map.entrySet())
			if (max == null)
				max = f;
			else
				if (f.getValue() > max.getValue())
					max = f;
		return max;
	}

	public SimilarGroup<String> getClosestGroup(EmailDocument ed)
	{
		int key = getClosestGroupIdx(ed);
		return (key < 0) ? null : selectedGroups.get(key);
	}

	public int getClosestGroupIdx(EmailDocument ed)
	{
		Map<Integer, Float> colorWeights = getAssignedColorWeights(ed);
		Map.Entry<Integer, Float> e = GroupAssigner.highestValueEntry(colorWeights);
		if (e == null)
			return -1;
		return e.getKey();
	}

	/** return color weights for this set of email messages,
		based on which groups are included per message.
		overall weight is simply the sum of weights for each doc.
		(note: there are multiple messages, and multiple groups can match a single message as well)
		individuals are considered single member groups
	 	if not email docs, an empty list is returned */
	// possible enhancement: also return # of times each group matched across all messages
	public Map<Integer, Float> getAssignedColors(Collection<Document> messages)
	{
		Map<Integer, Float> result = new LinkedHashMap<Integer, Float>();

		for (Document d: messages)
		{
			// for this message, what's the best color ?
			if (d instanceof EmailDocument && addressBook != null)
			{
				EmailDocument ed = (EmailDocument) d;

				// merge the color weights due to this doc
				Map<Integer, Float> colorWeights = getAssignedColorWeights(ed);
				for (int color: colorWeights.keySet())
				{
					Float F = result.get(color);
					if (F == null)
						result.put(color, colorWeights.get(color));
					else
						result.put (color, F + colorWeights.get(color));
				}
			}
		}
		return result;
	}

	public String toString()
	{
		StringBuilder sb = new StringBuilder("Color assigner with max " + nGroupsToColor + " groups and " + nIndividualsToColor + " individuals.\n");
		for (int i = 0; i < selectedGroups.size(); i++)
			sb.append ("Colored Group #" + i + ": " + selectedGroups.get(i).size() + " members\n");
		return sb.toString();
	}

	public int docsOwnedByAnyColor(Collection<EmailDocument> docs)	
	{
		Map<Integer, Integer> map = documentsOwnedByColorsMap(docs);
		int nDocsCoveredByAllColors = 0;
		for (int i = 0; i < selectedGroups.size(); i++)
		{
			Integer nDocsI = map.get(i);
			if (nDocsI != null)
				nDocsCoveredByAllColors += nDocsI;
		}
		return nDocsCoveredByAllColors;
	}

	public String toString(Collection<EmailDocument> docs)
	{
		if (docs == null)
			return toString();
		Map<Integer, Integer> map = documentsOwnedByColorsMap(docs);

		StringBuilder sb = new StringBuilder(); // "Group information assigner with max " + nGroupsToColor + " groups and " + nIndividualsToColor + " individuals.\n");
		int nDocsCoveredByAllColors = 0;
		for (int i = 0; i < selectedGroups.size(); i++)
		{
			Integer nDocsI = map.get(i);
			int nDocs = (nDocsI == null) ? 0 : nDocsI;
			nDocsCoveredByAllColors += nDocs;
			SimilarGroup<String> sg = selectedGroups.get(i);
			sb.append ((i+1) + ". " + sg.name + " (" + (sg.size() > 1 ? sg.size() + " members":"individual") + ", assigned to " + nDocs + " messages)\n");
		}
		int nCovered = docs.size() - nDocsCoveredByAllColors;
		String pct = (docs.size() > 0) ? "(" + (nCovered*100/docs.size()) + "%)" :"";
		sb.append (nCovered + " of " + docs.size() + " messages not assigned to any group " + pct + "\n");
		return sb.toString();
	}
}
