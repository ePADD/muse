package edu.stanford.muse.groups;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.CalendarUtil;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;

public class GroupUtils {
	/** given a collection of equiv. sets, sets up pairs for each combination of items in the set. 
	 * has redundant pairs, e.g. the set {c1, c2} will cause both (c1, c2) and (c2, c1) to be included, but it doesn't really matter
	 * since we expect these to be small sets. 
	 * equiv sets larger than maxEquivSetSize are ignored. */
	private static<T> Set<Pair<T, T>> convertEquivSetsToPairs(Collection<Set<T>> sets, int maxEquivSetSize)
	{
		Set<Pair<T, T>> pairs = new LinkedHashSet<Pair<T, T>>();
		for (Set<T> set: sets)
		{
			if (set.size() < 2)
				continue; // no need to do anything
			if (set.size() > maxEquivSetSize)
			{
				StringBuilder sb = new StringBuilder("Ignoring " + set.size() + " equiv set\n");
				for (T t: set)
				{
					sb.append (t);
					sb.append ("\n");
				}
				Grouper.log.warn (sb);
				break;
			}
			for (T c1: set)
				for (T c2: set)
					if (!c1.equals(c2))
						pairs.add (new Pair<T, T>(c1, c2));
		}
		return pairs;
	}
	
	/** computes affinity based on last name and org. in email domain. in future, could consider other features like
	 * topics associated with the person. */
	public static Map<String, Map<String, Float>> computeAffinityMap(Collection<Contact> contacts)
	{
		// create map of last names -> all contacts with that name
		Map<String, Set<Contact>> lastNameMap = new LinkedHashMap<String, Set<Contact>>();
		for (Contact c: contacts)
		{
			for (String name: c.names)
			{
				String lastName = EmailUtils.getLastName(name);
				if (Util.nullOrEmpty(lastName))
					continue;
				lastName = lastName.toLowerCase();
				Set<Contact> set = lastNameMap.get(lastName);
				if (set == null)
				{
					set = new LinkedHashSet<Contact>();
					lastNameMap.put (lastName, set);
				}
				set.add(c);
			}
		}

		// more than N people with the same last name or email domain? 
		// probably not worth considering affinity. too expensive anyway, given our current way of computing it.
		// (we could do it better by computing affinity inside the grouper, but currently don't have a way of doing that)
		int MAX_EQUIV_SET_SIZE_LAST_NAMES = 100;  // 100 seems a lot, but I know a lot of Kulkarni's...
		
		Set<Pair<Contact, Contact>> sameLastNameContactPairs = convertEquivSetsToPairs(lastNameMap.values(), MAX_EQUIV_SET_SIZE_LAST_NAMES);
		Map<String, Set<Contact>> orgMap = new LinkedHashMap<String, Set<Contact>>();

		for (Contact c: contacts)
		{
			for (String email: c.emails)
			{
				String org = EmailUtils.getOrg(email);
				if (Util.nullOrEmpty(org))
					continue;
				org = org.toLowerCase();
				Set<Contact> set = orgMap.get(org);
				if (set == null)
				{
					set = new LinkedHashSet<Contact>();
					orgMap.put (org, set);
				}
				set.add(c);
			}
		}

		int MAX_EQUIV_SET_SIZE_DOMAINS = 100; 
		Set<Pair<Contact, Contact>> sameOrgContactPairs = convertEquivSetsToPairs(orgMap.values(), MAX_EQUIV_SET_SIZE_DOMAINS);
		float SAME_LAST_NAME_AFFINITY_WEIGHT = 0.25f;
		float SAME_ORG_AFFINITY_WEIGHT = 0.1f;
		
		Map<Contact, Map<Contact, Float>> resultC = new LinkedHashMap<Contact, Map<Contact, Float>>();
		for (Pair<Contact, Contact> p: sameLastNameContactPairs)
		{
			Contact c1 = p.getFirst(), c2 = p.getSecond();
			Map<Contact, Float> affinityMap = resultC.get(c1);
			if (affinityMap == null)
			{
				affinityMap = new LinkedHashMap<Contact, Float>();
				resultC.put(c1, affinityMap);
			}
			affinityMap.put(c2, SAME_LAST_NAME_AFFINITY_WEIGHT);
		}
		
		for (Pair<Contact, Contact> p: sameOrgContactPairs)
		{
			Contact c1 = p.getFirst(), c2 = p.getSecond();
			Map<Contact, Float> affinityMapForC1 = resultC.get(c1);
			if (affinityMapForC1 == null)
			{
				affinityMapForC1 = new LinkedHashMap<Contact, Float>();
				resultC.put(c1, affinityMapForC1);
			}
			Float F = affinityMapForC1.get(c2);
			float existingAffinity;
			if (F == null)
				existingAffinity = 0.0f;
			else
				existingAffinity = F;
			
			affinityMapForC1.put(c2, existingAffinity + SAME_ORG_AFFINITY_WEIGHT);
		}

		Map<String, Map<String, Float>> result = new LinkedHashMap<String, Map<String, Float>>();
		for (Contact c: resultC.keySet())
		{
			Map<Contact, Float> affMapC = resultC.get(c);
			Map<String, Float> affMap = new LinkedHashMap<String, Float>();
			result.put (c.canonicalEmail, affMap);
			for (Contact c1: affMapC.keySet())
				affMap.put(c1.canonicalEmail, affMapC.get(c1));
		}
		
		return result;
	}

	/** sorts by decreasing order of mass (total comm. volume with all the members of a group). 
	 * this is not used for ordering selected groups in color assigner.
	 * this is used mainly for groupsjson.  */
	public static void sortByMass(Collection<EmailDocument> docs, AddressBook ab, List<SimilarGroup<String>> list)
	{
		Map <Contact, Pair<List<Date>, List<Date>>> map = EmailUtils.computeContactToDatesMap(ab, docs);
	
		// compute the group's mass one-time, expensive to compute it within compare
		for (SimilarGroup<String> sg: list)
		{
			float mass = 0;
			for (String s: sg.elements)
			{
				Contact c = ab.lookupByEmail(s);
				if (c != null)
				{
				    int volume = 0;
				    Pair<List<Date>, List<Date>> pair = map.get(c);
		            if (pair != null)
		            	volume = pair.getFirst().size() + pair.getSecond().size();
		            mass += volume;
				}
				else
					JSPHelper.log.warn("Contact not found in address book for email: " + s);
			}
			sg.setMass(mass);
		}
	
		Collections.sort(list, new Comparator<SimilarGroup<String>>() {
			public int compare (SimilarGroup<String> g1, SimilarGroup<String> g2)
			{
				// @check shouldn't it be " return (g2.mass-g1.mass); what if they are equal??"
				return (g1.mass > g2.mass) ? -1 : 1;
			};
		});
	}

	/** score people by importance, based on the given emails.
	 * uses a formula based on sqrt(monthly comm. volume) to dampen bursts */
	public static Map<String, Float> getScoreForContacts(AddressBook ab, Collection<EmailDocument> emails, Collection<Contact> contactsToIgnore)
	{
		Map<String, List<Date>> map = new LinkedHashMap<String, List<Date>>();
		Map<String, Float> result = new LinkedHashMap<String, Float>();
	
		Set<String> ignoreContactsCanonical = new LinkedHashSet<String>();
		for (Contact c: contactsToIgnore)
			ignoreContactsCanonical.add(c.canonicalEmail);
		
		for (EmailDocument ed: emails)
		{
			List<String> addrs = ed.getAllAddrs();
			addrs = ab.convertToCanonicalAddrs(addrs);
			addrs = ab.removeOwnAddrs(addrs);
			addrs = Util.removeDups(addrs);
			addrs = EmailUtils.removeMailingLists(addrs);
			addrs = EmailUtils.removeIncorrectNames(addrs);
			if (ignoreContactsCanonical != null)
				addrs.removeAll(ignoreContactsCanonical);
	
			if (addrs.size() == 0) // can happen - no non-own addrs
				continue;
	
			for (String s: addrs)
			{
				List<Date> dates = map.get(s);
				if (dates == null)
				{
					dates = new ArrayList<Date>();
					map.put (s, dates);
				}				
				dates.add(ed.date);
			}
		}
		
		// score people based on not just volume, but on consistency of comm.
		// score = sum(sqrt of monthly communication) to dampen burst communication.
		for (String person: map.keySet())
		{
			List<Date> dates = map.get(person);
			Collections.sort(dates);
			List<Date> intervals = CalendarUtil.divideIntoMonthlyIntervals(dates.get(0), dates.get(dates.size()-1));
			int[] hist = CalendarUtil.computeHistogram(dates, intervals);
			float score = 0.0f;
			for (int h: hist)
				score += Math.sqrt(h);
			result.put (person, score);
		}
	
		return result;
	}

	/** adapter function to convert emaildocs to groups of email addrs associated with the email
	 * (own addrs are removed) (similar to above) */
	public static Map<Group<String>, Float> convertEmailsToGroupsWeighted(AddressBook ab, Collection<EmailDocument> emails, Collection<Contact> contactsToIgnore)
	{
		Map<Group<String>, List<Date>> map = new LinkedHashMap<Group<String>, List<Date>>();
		Map<Group<String>, Float> result = new LinkedHashMap<Group<String>, Float>();
	
		Set<String> ignoreContactsCanonical = new LinkedHashSet<String>();
		for (Contact c: contactsToIgnore)
			ignoreContactsCanonical.add(c.getCanonicalEmail());
		
		for (EmailDocument ed: emails)
		{
			List<String> addrs = ed.getAllAddrs();
			addrs = ab.convertToCanonicalAddrs(addrs);
			addrs = ab.removeOwnAddrs(addrs);
			addrs = Util.removeDups(addrs);
			addrs = EmailUtils.removeMailingLists(addrs);
			addrs = EmailUtils.removeIncorrectNames(addrs);
			if (ignoreContactsCanonical != null)
				addrs.removeAll(ignoreContactsCanonical);
	
			if (addrs.size() == 0) // can happen - no non-own addrs
				continue;
	
			Collections.sort(addrs);
			Group<String> b = new Group<String>(addrs);
			List<Date> dates = map.get(b);
	
			if (dates == null)
			{
				dates = new ArrayList<Date>();
				map.put (b, dates);
			}
			
			dates.add(ed.date);
		}
		
		for (Group<String> g: map.keySet())
		{
			List<Date> dates = map.get(g);
			Collections.sort(dates);
			List<Date> intervals = CalendarUtil.divideIntoMonthlyIntervals(dates.get(0), dates.get(dates.size()-1));
			int[] hist = CalendarUtil.computeHistogram(dates, intervals);
			float score = 0.0f;
			for (int h: hist)
				score += Math.sqrt(h);
			// throttle to 10
			float multiplier = Math.min (g.elements.size(), 10);
			score *= multiplier;
			result.put (g, score);
		}
	
		return result;
	}

	/** remove contacts that occur <= threshold # of times */
	public static Set<Contact> contactsAtOrBelowThreshold(AddressBook ab, Collection<EmailDocument> docs, int threshold)
	{
		Map<Contact, Integer> contactToCountMap = new LinkedHashMap<Contact, Integer>();
	
		// create count map of contact -> # of messages
		for (EmailDocument ed: docs)
		{
			List<String> addrs = ed.getAllAddrs();
			for (String addr: addrs)
			{
				Contact c = ab.lookupByEmail(addr);
				if (c == null)
					continue;  // #defensive
	
				Integer I = contactToCountMap.get(c);
				if (I == null)
					contactToCountMap.put(c, 1);
				else
					contactToCountMap.put(c, I+1);
			}
		}
	
		Set<Contact> result = new LinkedHashSet<Contact>();
		for (Contact c: contactToCountMap.keySet())
			if (contactToCountMap.get(c) <= threshold)
				result.add(c);
	
		return result;
	}

	/** adapter function to convert emaildocs to groups of email addrs associated with the email
	 * (own addrs are removed) (similar to above) */
	public static List<Group<String>> convertEmailsToGroups(AddressBook ab, Collection<EmailDocument> emails)
	{
		List<Group<String>> result = new ArrayList<Group<String>>();
	
		for (EmailDocument ed: emails)
		{
			List<String> addrs = ed.getAllAddrs();
			addrs = ab.convertToCanonicalAddrs(addrs);
			addrs = ab.removeOwnAddrs(addrs);
			addrs = Util.removeDups(addrs);
			addrs = EmailUtils.removeMailingLists(addrs);
			addrs = EmailUtils.removeIncorrectNames(addrs);
	
			if (addrs.size() == 0) // can happen - no non-own addrs
				continue;
	
			Collections.sort(addrs);
			Group<String> b = new Group<String>(addrs);
			result.add(b);
		}
	
		return result;
	}

}
