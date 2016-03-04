/*
 * Copyright (C) 2012 The Stanford MobiSocial Laboratory
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.stanford.muse.index;

import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.datacache.BlobStore;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.CalendarUtil;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.groups.Group;
import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.util.*;
import edu.stanford.muse.webapp.ModeConfig;
import edu.stanford.muse.webapp.JSPHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/** useful utilities for indexing */
public class IndexUtils {
	private static Log	log	= LogFactory.getLog(IndexUtils.class);

	/** temporary method */
	public static boolean query(String s, String query)
	{
		List<String> tokens = Util.tokenize(query, "|");
		s = s.toLowerCase();
		for (String t : tokens)
		{
			t = t.trim();
			if (Util.nullOrEmpty(t))
				continue;
			if (s.contains(t))
				return true;
		}
		return false;
	}

	public static List<MultiDoc> partitionDocsByCategory(Collection<? extends Document> allDocs)
	{
		Map<String, MultiDoc> map = new LinkedHashMap<String, MultiDoc>();
		for (Document d : allDocs)
		{
			CategoryDocument cd = (CategoryDocument) d;
			MultiDoc docs = map.get(cd.category);
			if (docs == null)
			{
				docs = new MultiDoc(map.size(), cd.category);
				map.put(cd.category, docs);
			}
			docs.add(d);
		}

		List<MultiDoc> result = new ArrayList<MultiDoc>();
		for (MultiDoc docs : map.values())
			result.add(docs);

		return result;
	}

	public static List<MultiDoc> partitionDocsByInterval(Collection<? extends DatedDocument> allDocs, boolean monthsNotYears)
	{
		List<MultiDoc> result = new ArrayList<MultiDoc>();
		if (allDocs.size() == 0)
			return result;

		Pair<Date, Date> p = EmailUtils.getFirstLast(allDocs);
		Date first = p.getFirst();
		Date last = p.getSecond();

		// compute the monthly intervals
		List<Date> intervals;
		if (monthsNotYears)
			intervals = Util.getMonthlyIntervals(first, last);
		else
			intervals = Util.getYearlyIntervals(first, last);

		int nIntervals = intervals.size() - 1;
		for (int i = 0; i < nIntervals; i++)
		{
			String clusterDescription;
			Date d = intervals.get(i);
			GregorianCalendar c = new GregorianCalendar();
			c.setTime(d);

			if (!monthsNotYears)
				clusterDescription = Integer.toString(c.get(Calendar.YEAR));
			else
				clusterDescription = CalendarUtil.getDisplayMonth(c) + " " + c.get(Calendar.YEAR);

			result.add(new MultiDoc(i, clusterDescription));
		}

		for (DatedDocument ed : allDocs)
		{
			// find which interval this email belongs to
			int selectedInterval = -1;
			// TODO: if all this API does is either partition by month or year then no need to "search".
			Date c = ed.date;
			for (int i = 0; i < nIntervals; i++)
			{
				Date intervalStart = intervals.get(i);
				Date intervalEnd = intervals.get(i + 1);
				if (!c.before(intervalStart) && c.before(intervalEnd))
				{
					selectedInterval = i;
					break;
				}
			}

			// this doc goes into interval # selectedInterval
			MultiDoc whichList = result.get(selectedInterval);
			whichList.add(ed);
		}

		return result;
	}

	/**
	 * returns a map of group name -> set of docs associated with that group.
	 * if trackNotInAnyGroup is true, adds a special group called none.
	 */
	public static Map<String, Set<EmailDocument>> partitionDocsByGroup(Collection<EmailDocument> allDocs, List<SimilarGroup<String>> groups, AddressBook addressBook, boolean trackNotInAnyGroup)
	{
		String NOTAGroupName = "None"; // NOTA = none of the above

		Map<String, Set<EmailDocument>> map = new LinkedHashMap<String, Set<EmailDocument>>();
		if (allDocs.size() == 0)
			return map;
		GroupAssigner ca = new GroupAssigner();
		ca.setupGroups(allDocs, groups, addressBook, 0);

		for (EmailDocument ed : allDocs)
		{
			Map.Entry<Integer, Float> e = GroupAssigner.highestValueEntry(ca.getAssignedColorWeights(ed));
			int groupNum = (e != null) ? e.getKey() : -1;
			boolean invalidGroup = groupNum < 0 || groupNum >= groups.size();
			if (invalidGroup && !trackNotInAnyGroup)
				continue;

			String groupName = (invalidGroup) ? NOTAGroupName : groups.get(groupNum).name;

			Set<EmailDocument> docsForGroup = map.get(groupName);
			if (docsForGroup == null)
			{
				docsForGroup = new LinkedHashSet<EmailDocument>();
				map.put(groupName, docsForGroup);
			}
			docsForGroup.add(ed);
		}

		// now sort so that groups in the map are in original order
		Map<String, Set<EmailDocument>> sortedMap = new LinkedHashMap<String, Set<EmailDocument>>();
		for (SimilarGroup<String> group : groups)
		{
			Set<EmailDocument> docs = map.get(group.name);
			if (docs != null)
				sortedMap.put(group.name, docs);
		}
		Set<EmailDocument> docs = map.get(NOTAGroupName);
		if (docs != null)
			sortedMap.put(NOTAGroupName, docs);

		return sortedMap;
	}

	public static class Window {
		public Date					start;
		public Date					end;
		public List<EmailDocument>	docs;

		public String toString() {
			return "[" + CalendarUtil.getDisplayMonth(start) + " - " + CalendarUtil.getDisplayMonth(end) + "), " + docs.size() + " messages";
		}
	}

	/** returns list of list of docs organized by a series of time windows */
	public static List<Window> docsBySlidingWindow(Collection<EmailDocument> allDocs, int windowSizeInMonths, int stepSizeInMonths)
	{
		List<Window> result = new ArrayList<Window>();
		if (allDocs.size() == 0)
			return result;

		// compute the begin and end date of the corpus
		Date first = null;
		Date last = null;
		for (EmailDocument ed : allDocs)
		{
			Date d = ed.date;
			if (d == null)
			{ // drop this ed
				log.warn("Warning: null date on email: " + ed.getHeader());
				continue;
			}
			if (first == null || d.before(first))
				first = d;
			if (last == null || d.after(last))
				last = d;
		}

		// compute the monthly intervals
		List<Pair<Date, Date>> intervals = Util.getSlidingMonthlyIntervalsBackward(first, last, windowSizeInMonths, stepSizeInMonths);

		int nIntervals = intervals.size();
		for (int i = 0; i < nIntervals; i++)
		{
			Window w = new Window();
			w.start = intervals.get(i).getFirst();
			w.end = intervals.get(i).getSecond();
			w.docs = new ArrayList<EmailDocument>();
			result.add(w);
		}

		// for each message, add it to all intervals it belongs to
		// can be made more efficient by first sorting allDocs by date etc
		// but may not be needed except for a very large # of intervals and
		// a large # of docs
		for (EmailDocument ed : allDocs)
		{
			Date d = ed.date;
			if (d == null)
				continue;

			// add ed to all intervals that c falls in
			for (int i = 0; i < nIntervals; i++)
			{
				Pair<Date, Date> interval = intervals.get(i);
				Date intervalStart = interval.getFirst();
				Date intervalEnd = interval.getSecond();
				if (d.equals(intervalStart) || d.equals(intervalEnd) ||
						(d.after(intervalStart) && d.before(intervalEnd)))
				{
					result.get(i).docs.add(ed);
					break;
				}
			}
		}

		return result;
	}

	public static void dumpDocument(String prefix, String bodyText) throws IOException
	{
		// dump contents
		PrintWriter pw1 = new PrintWriter(new FileOutputStream(prefix + ".txt"));
		pw1.println(bodyText);
		pw1.close();
	}

	// read all the headers
	public static List<Document> findAllDocs(String prefix) throws ClassNotFoundException, IOException
	{
		List<Document> allDocs = new ArrayList<Document>();

		// weird: sometimes we get a double-slash or double-backslash which kills the matching...
		// better canonicalize first, which calling new File() and then getAbsolutePath does
		prefix = new File(prefix).getAbsolutePath();
		String dir = ".";
		int x = prefix.lastIndexOf(File.separator);
		if (x >= 0)
			dir = prefix.substring(0, x);
		File dirFile = new File(dir);
		// select valid header files
		File files[] = dirFile.listFiles(new Util.MyFilenameFilter(prefix, ".header"));
		for (File f : files)
		{
			try {
				Document d = null;
				ObjectInputStream headerOIS = new ObjectInputStream(new FileInputStream(f));
				d = (Document) headerOIS.readObject();
				headerOIS.close();
				allDocs.add(d);
			} catch (Exception e) {
				Util.print_exception(e, log);
			}
		}

		System.out.println(allDocs.size() + " documents found with prefix " + prefix);
		return allDocs;
	}

	// read all the headers
	//	public static List<Document> findAllDocsInJar(String prefix) throws ClassNotFoundException, IOException
	//	{
	//		String dir = Util.dirName(prefix);
	//		String file = Util.baseName(prefix);
	//		JarDocCache cache = new JarDocCache(dir);
	//		List<Document> allDocs = new ArrayList<Document>(cache.getAllHeaders(file).values());
	//		return allDocs;
	//	}

	static String capTo2Tokens(String s)
	{
		StringTokenizer st = new StringTokenizer(s);
		int nTokens = st.countTokens();
		if (nTokens <= 2)
			return s;
		else
			return st.nextToken() + " " + st.nextToken();
	}

	/**
	 * splits the input string into words e.g.
	 * if the input is "a b c", return a list containing "a" "b" and "c"
	 * if input is "a" a list containing just "a" is returned.
	 * input must have at least one token.
	 * 
	 * @param s
	 * @return
	 */
	public static List<String> splitIntoWords(String s)
	{
		List<String> result = new ArrayList<String>();
		if (s == null)
			return result;

		StringTokenizer st = new StringTokenizer(s);
		while (st.hasMoreTokens())
		{
			String term = st.nextToken();
			if (term.startsWith("\""))
			{
				term = term.substring(1); // skip the leading "
				while (st.hasMoreTokens())
				{
					term += " " + st.nextToken();
					if (term.endsWith("\""))
					{
						term = term.substring(0, term.length() - 1); // skip the trailing "
						break;
					}
				}
			}
			result.add(term);
		}

		return result;
	}

	/**
	 * splits the input string into pairs of words e.g.
	 * if the input is "a b c", return a list containing "a b" and "b c".
	 * if input is "a" a list containing just "a" is returned.
	 * if input is "a b", a list containing just "a b" is returned.
	 * input must have at least one token.
	 * 
	 * @param s
	 * @return
	 */
	public static List<String> splitIntoPairWords(String s)
	{
		List<String> result = new ArrayList<String>();
		if (s == null)
			return result;

		StringTokenizer st = new StringTokenizer(s);
		if (!st.hasMoreTokens())
			return result;

		String s1 = st.nextToken();
		if (!st.hasMoreTokens())
		{
			result.add(s1);
			return result;
		}

		while (st.hasMoreTokens())
		{
			String s2 = st.nextToken();
			if (DictUtils.isJoinWord(s2))
				continue;
			result.add(s1 + " " + s2);
			s1 = s2;
		}

		return result;
	}

	/**
	 * splits the input search query string into indiv. words.
	 * e.g. a b|"c d"|e returns a list of length 4: a, b, "c d", e
	 * 
	 * @return
	 */
	public static List<String> getAllWordsInQuery(String s)
	{
		List<String> result = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(s, "|");
		while (st.hasMoreTokens())
		{
			//			StringTokenizer st1 = new StringTokenizer(st.nextToken());
			//			while (st1.hasMoreTokens())
			{
				//				String word = st1.nextToken().trim().toLowerCase(); // canonicalize and add
				String word = st.nextToken();
				word = word.trim();
				if (word.length() > 0)
					result.add(word);
			}
		}
		return result;
	}

	/**
	 * returns docs with ALL the given email/names. looks for all aliases of the
	 * person's name, not just the given one
	 */
	public static Set<Document> selectDocsByPersons(AddressBook ab, Collection<EmailDocument> docs, String[] emailOrNames)
	{
		return selectDocsByPersons(ab, docs, emailOrNames, null);
	}

	public static List<Document> selectDocsByPersonsAsList(AddressBook ab, Collection<EmailDocument> docs, String[] emailOrNames)
	{
		return new ArrayList<Document>(selectDocsByPersons(ab, docs, emailOrNames, null));
	}

	/**
	 * returns docs with any/all the given email/names. looks for all aliases of
	 * the person's name, not just the given one.
	 * runs through all docs times # given emailOrNames.
	 */
	public static Set<Document> selectDocsByPersons(AddressBook ab, Collection<EmailDocument> docs, String[] emailOrNames, int[] contactIds)
	{
		if (ab == null)
		{
			// no addressbook, return everything
			Set<Document> result = new LinkedHashSet<Document>();
			result.addAll(docs);
			return result;
		}

		Set<Contact> contactSet = new LinkedHashSet<Contact>();
		if (emailOrNames != null) {
			for (String e : emailOrNames) {
				if (e.contains(" ")) {
					// not a single token, likely a specific name, e.g., "john doe"
					Contact c = ab.lookupByEmailOrName(e);
					if (c != null)
						contactSet.add(c);
				} else {
					// single token (partial name), use lookup that returns a set, e.g., "john"

					//@vihari: BUG-FIX: throws Null-pointer exception when ab.lookupByNameTokenAsSet is null.
					if (ab.lookupByNameTokenAsSet(e) == null) {
						log.info("Null pointer for: " + e);
						continue;
					}
					else
						contactSet.addAll(ab.lookupByNameTokenAsSet(e));
				}
				if (contactSet.isEmpty())
					log.info("Unknown email/name " + e);
			}
		}

		if (contactIds != null) {
			for (int id : contactIds) {
				Contact c = ab.getContact(id);
				if (c == null)
					log.info("Unknown contact ID " + id);
				else
					contactSet.add(c);
			}
		}

		if (contactSet.isEmpty())
			return new LinkedHashSet<Document>(); // return an empty list

		// consider map impl in future where we can go directly from the names to the messages
		// currently each call to selectDocsByPerson will go through all docs
		Set<Document> docsForThisPerson = IndexUtils.selectDocsByContact(ab, docs, contactSet);

		for (Document d : docsForThisPerson)
		{
			if (!d.equals(d))
				log.error("doc not itself!");
			//	if (!docsForThisPerson.contains(d))
			//		log.error ("Email doc is not good!");
			//	log.info (d);
		}

		if (log.isDebugEnabled())
		{
			StringBuilder sb = new StringBuilder();
			for (String s : emailOrNames)
				sb.append(s + " ");
			log.debug(docsForThisPerson.size() + " docs with person: [" + sb + "]");
		}

		return docsForThisPerson;
	}

	/**
	 * returns docs with ALL the given email/names. looks for all aliases of the
	 * person's name, not just the given one.
	 * runs through all docs times # given emailOrNames.
	 */
	public static Set<Document> selectDocsByAllPersons(AddressBook ab, Collection<EmailDocument> docs, String[] emailOrNames, int[] contactIds)
	{
		Set<Document> result = Util.castOrCloneAsSet((Collection) docs);

		if (emailOrNames != null) {
			for (String e : emailOrNames) {
				result = selectDocsByPersons(ab, (Collection) result, new String[] { e }, null);
				if (result.isEmpty())
					return result;
			}
		}

		if (contactIds != null) {
			for (int c : contactIds) {
				result = selectDocsByPersons(ab, (Collection) result, null, new int[] { c });
				if (result.isEmpty())
					return result;
			}
		}

		return result;
	}

	/*
	 * returns index of doc with date closest to the date startYear/startMonth/1
	 * startMonth is 0-based
	 * returns -1 if no docs or invalid start month/year
	 */
	public static int getDocIdxWithClosestDate(Collection<? extends DatedDocument> docs, int startMonth, int startYear)
	{
		if (docs.size() == 0)
			return -1;

		if (startMonth < 0 || startYear < 0)
			return -1;

		long givenDate = new GregorianCalendar(startYear, startMonth, 1).getTime().getTime();

		Long smallestDiff = Long.MAX_VALUE;
		int bestIdx = -1, idx = 0;
		for (DatedDocument d : docs)
		{
			long dateDiff = Math.abs(d.date.getTime() - givenDate);
			if (dateDiff < smallestDiff)
			{
				bestIdx = idx;
				smallestDiff = dateDiff;
			}
			idx++;
		}
		return bestIdx;
	}

	/*
	 * public static Map<String, Collection<FacetItem>>
	 * computeFacets(Collection<Document> docs, AddressBook addressBook,
	 * GroupAssigner groupAssigner, Indexer indexer)
	 * {
	 * Map<String, Collection<FacetItem>> facetMap = new LinkedHashMap<String,
	 * Collection<FacetItem>>();
	 * 
	 * // sentiments
	 * if (indexer != null)
	 * {
	 * List<FacetItem> sentimentItems = new ArrayList<FacetItem>();
	 * 
	 * // rather brute-force, compute docs for all sentiments and then
	 * intersect...
	 * // a better way might be to process the selected messages and see which
	 * sentiments they reflect
	 * for (String sentiment: Sentiments.captionToQueryMap.keySet())
	 * {
	 * String query = Sentiments.captionToQueryMap.get(sentiment);
	 * List<Document> docsForTerm = new
	 * ArrayList<Document>(indexer.docsWithPhrase(query, -1));
	 * docsForTerm.retainAll(docs);
	 * String url = "sentiment=" + sentiment;
	 * sentimentItems.add(new FacetItem(sentiment,
	 * Sentiments.captionToQueryMap.get(sentiment), docsForTerm.size(), url));
	 * }
	 * facetMap.put("Sentiments", sentimentItems);
	 * }
	 * 
	 * if (addressBook != null)
	 * {
	 * // groups
	 * if (groupAssigner != null)
	 * {
	 * Map<SimilarGroup<String>, FacetItem> groupMap = new
	 * LinkedHashMap<SimilarGroup<String>, FacetItem>();
	 * for (Document d: docs)
	 * {
	 * if (!(d instanceof EmailDocument))
	 * continue;
	 * EmailDocument ed = (EmailDocument) d;
	 * SimilarGroup<String> g = groupAssigner.getClosestGroup(ed);
	 * if (g == null)
	 * continue;
	 * FacetItem f = groupMap.get(g);
	 * if (f == null)
	 * {
	 * String url = "groupIdx=" + groupAssigner.getClosestGroupIdx(ed);
	 * groupMap.put(g, new FacetItem(g.name, g.elementsToString(), 1, url));
	 * }
	 * else
	 * f.count++;
	 * }
	 * 
	 * facetMap.put("Groups", groupMap.values());
	 * }
	 * 
	 * // people
	 * Map<Contact, FacetItem> peopleMap = new LinkedHashMap<Contact,
	 * FacetItem>();
	 * for (Document d: docs)
	 * {
	 * if (!(d instanceof EmailDocument))
	 * continue;
	 * EmailDocument ed = (EmailDocument) d;
	 * List<Contact> people = ed.getParticipatingContactsExceptOwn(addressBook);
	 * for (Contact c: people)
	 * {
	 * String s = c.pickBestName();
	 * FacetItem f = peopleMap.get(c);
	 * if (f == null)
	 * {
	 * String url = "person=" + c.canonicalEmail;
	 * peopleMap.put(c, new FacetItem(s, c.toTooltip(), 1, url));
	 * }
	 * else
	 * f.count++;
	 * }
	 * }
	 * facetMap.put("People", peopleMap.values());
	 * }
	 * 
	 * // can do time also... locations?
	 * 
	 * return facetMap;
	 * }
	 */

	public static Map<Contact, DetailedFacetItem> partitionDocsByPerson(Collection<? extends Document> docs, AddressBook ab)
	{
		Map<Contact, DetailedFacetItem> result = new LinkedHashMap<Contact, DetailedFacetItem>();
		Map<Contact, Pair<String, String>> tooltip_cache = new LinkedHashMap<Contact, Pair<String, String>>();
		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;
			EmailDocument ed = (EmailDocument) d;
			List<Contact> people = ed.getParticipatingContactsExceptOwn(ab);
			for (Contact c : people)
			{
				String s = null;
				String tooltip = null;
				Pair<String, String> p = tooltip_cache.get(c);
				if (p != null) {
					s = p.getFirst();
					tooltip = p.getSecond();
				} else {
					s = c.pickBestName();
					tooltip = c.toTooltip();
					if (ModeConfig.isPublicMode()) {
						s = Util.maskEmailDomain(s);
						tooltip = Util.maskEmailDomain(tooltip);
					}
					tooltip_cache.put(c, new Pair<String, String>(s, tooltip));
				}
				DetailedFacetItem f = result.get(c);
				if (f == null)
				{
					//String url = "person=" + c.canonicalEmail;
					//String url = "contact=" + ab.getContactId(c);
					f = new DetailedFacetItem(s, tooltip, "contact", Integer.toString(ab.getContactId(c)));
					result.put(c, f);
				}
				f.addDoc(ed);
			}
		}
		return result;
	}

	public static Map<String, DetailedFacetItem> partitionDocsByFolder(Collection<? extends Document> docs)
	{
		Map<String, DetailedFacetItem> folderNameMap = new LinkedHashMap<String, DetailedFacetItem>();
		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;
			EmailDocument ed = (EmailDocument) d;
			String s = ed.folderName;
			if (s == null)
				continue;
			DetailedFacetItem f = folderNameMap.get(s);
			if (f == null)
			{
				f = new DetailedFacetItem(Util.filePathTail(s), s, "folder", s);
				folderNameMap.put(s, f);
			}
			f.addDoc(ed);
		}
		return folderNameMap;
	}

	public static Map<SimilarGroup<String>, DetailedFacetItem> partitionDocsByGroup(Collection<? extends Document> docs, GroupAssigner groupAssigner)
	{
		Map<SimilarGroup<String>, DetailedFacetItem> groupMap = new LinkedHashMap<SimilarGroup<String>, DetailedFacetItem>();
		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;
			EmailDocument ed = (EmailDocument) d;
			SimilarGroup<String> g = groupAssigner.getClosestGroup(ed);
			if (g == null)
				continue;
			DetailedFacetItem f = groupMap.get(g);
			if (f == null)
			{
				f = new DetailedFacetItem(g.name, g.elementsToString(), "groupIdx", Integer.toString(groupAssigner.getClosestGroupIdx(ed)));
				groupMap.put(g, f);
			}
			f.addDoc(ed);
		}
		return groupMap;
	}

	public static Map<String, DetailedFacetItem> partitionDocsByDirection(Collection<? extends Document> docs, AddressBook ab)
	{
		Map<String, DetailedFacetItem> result = new LinkedHashMap<String, DetailedFacetItem>();
		DetailedFacetItem f_in = new DetailedFacetItem("Received", "Incoming messages", "direction", "in");
		DetailedFacetItem f_out = new DetailedFacetItem("Sent", "Outgoing messages", "direction", "out");

		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;
			EmailDocument ed = (EmailDocument) d;
			int sent_or_received = ed.sentOrReceived(ab);

			if ((sent_or_received & EmailDocument.RECEIVED_MASK) != 0)
				f_in.addDoc(ed);
			if ((sent_or_received & EmailDocument.SENT_MASK) != 0)
				f_out.addDoc(ed);
		}

		if (f_in.totalCount() > 0)
			result.put("Received", f_in);
		if (f_out.totalCount() > 0)
			result.put("Sent", f_out);

		return result;
	}

	public static Map<String, DetailedFacetItem> partitionDocsByDoNotTransfer(Collection<? extends Document> docs)
	{
		Map<String, DetailedFacetItem> result = new LinkedHashMap<String, DetailedFacetItem>();
		DetailedFacetItem t = new DetailedFacetItem("Transfer", "To be transferred", "doNotTransfer", "false");
		DetailedFacetItem f = new DetailedFacetItem("Do not transfer", "Not to be transferred", "doNotTransfer", "true");

		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;
			EmailDocument ed = (EmailDocument) d;

			if (ed.doNotTransfer)
				f.addDoc(ed);
			else
				t.addDoc(ed);
		}

		if (f.totalCount() > 0)
			result.put("Do not transfer", f);
		if (t.totalCount() > 0)
			result.put("Transfer", t);
		return result;
	}

	public static Map<String, DetailedFacetItem> partitionDocsByTransferWithRestrictions(Collection<? extends Document> docs)
	{
		Map<String, DetailedFacetItem> result = new LinkedHashMap<String, DetailedFacetItem>();
		DetailedFacetItem t = new DetailedFacetItem("Restrictions", "Transfer with restrictions", "transferWithRestrictions", "true");
		DetailedFacetItem f = new DetailedFacetItem("No restrictions", "Transfer with no restrictions", "transferWithRestrictions", "false");

		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;
			EmailDocument ed = (EmailDocument) d;

			if (ed.transferWithRestrictions)
				t.addDoc(ed);
			else
				f.addDoc(ed);
		}

		if (t.totalCount() > 0)
			result.put("Restrictions", t);
		if (f.totalCount() > 0)
			result.put("No restrictions", f);

		return result;
	}

	public static Map<String, DetailedFacetItem> partitionDocsByReviewed(Collection<? extends Document> docs)
	{
		Map<String, DetailedFacetItem> result = new LinkedHashMap<String, DetailedFacetItem>();
		DetailedFacetItem t = new DetailedFacetItem("Reviewed", "Reviewed", "reviewed", "true");
		DetailedFacetItem f = new DetailedFacetItem("Not reviewed", "Not reviewed", "reviewed", "false");
		result.put("Not reviewed", f);

		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;
			EmailDocument ed = (EmailDocument) d;

			if (ed.reviewed)
				t.addDoc(ed);
			else
				f.addDoc(ed);
		}

		if (t.totalCount() > 0)
			result.put("Reviewed", t);
		else
			result.put("Not reviewed", f);
		return result;
	}

	/** note: attachment types are lower-cased */
	public static Map<String, DetailedFacetItem> partitionDocsByAttachmentType(Collection<? extends Document> docs)
	{
		Map<String, DetailedFacetItem> result = new LinkedHashMap<String, DetailedFacetItem>();

		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;
			EmailDocument ed = (EmailDocument) d;
			List<Blob> attachments = ed.attachments;
			if (attachments != null)
				for (Blob b : attachments)
				{
					String ext = Util.getExtension(b.filename);
					if (ext == null)
						ext = "none";
					ext = ext.toLowerCase();
					DetailedFacetItem dfi = result.get(ext);
					if (dfi == null)
					{
						dfi = new DetailedFacetItem(ext, ext + " attachments", "attachment_type", ext);
						result.put(ext, dfi);
					}
					dfi.addDoc(ed);
				}
		}
		return result;
	}

	/** version that stores actual dates instead of just counts for each facet */
	public static Map<String, Collection<DetailedFacetItem>> computeDetailedFacets(Collection<Document> docs, Archive archive, Lexicon lexicon)
	{
		AddressBook addressBook = archive.addressBook;
		GroupAssigner groupAssigner = archive.groupAssigner;
		Indexer indexer = archive.indexer;

		Map<String, Collection<DetailedFacetItem>> facetMap = new LinkedHashMap<String, Collection<DetailedFacetItem>>();

		// Note: order is important here -- the facets will be displayed in the order they are inserted in facetMap
		// current order: sentiments, groups, people, direction, folders
		if (indexer != null)
		{
			List<DetailedFacetItem> sentimentItems = new ArrayList<DetailedFacetItem>();
			Set<Document> docSet = new LinkedHashSet<Document>(docs);

			// rather brute-force, compute docs for all sentiments and then intersect...
			// a better way might be to process the selected messages and see which sentiments they reflect
			Map<String, String> captionToQueryMap;
			if (lexicon != null && !ModeConfig.isPublicMode())
				captionToQueryMap = lexicon.getCaptionToQueryMap(indexer, docs);
			else
				captionToQueryMap = new LinkedHashMap<String, String>();

			for (String sentiment : captionToQueryMap.keySet())
			{
				String query = captionToQueryMap.get(sentiment);
                Indexer.QueryOptions options = new Indexer.QueryOptions();
                //options.setQueryType(Indexer.QueryType.ORIGINAL);
				options.setSortBy(Indexer.SortBy.RELEVANCE); // to avoid unnecessary sorting
				Collection<Document> docsForTerm = indexer.docsForQuery(query, options);
				docsForTerm.retainAll(docSet);
				sentimentItems.add(new DetailedFacetItem(sentiment, captionToQueryMap.get(sentiment), new ArrayList<Document>(docsForTerm), "sentiment", sentiment));
			}
			facetMap.put("sentiments", sentimentItems);
		}

		Set<Document> docSet = new LinkedHashSet<Document>(docs);
		Map<String, Set<Document>> tagToDocs = new LinkedHashMap<String, Set<Document>>();
		for (Document d : docs)
		{
			if (!Util.nullOrEmpty(d.comment))
			{
				String tag = d.comment.toLowerCase();
				Set<Document> set = tagToDocs.get(tag);
				if (set == null)
				{
					set = new LinkedHashSet<Document>();
					tagToDocs.put(tag, set);
				}
				set.add(d);
			}
		}

		if (addressBook != null)
		{
			// groups
			if (!ModeConfig.isPublicMode() && groupAssigner != null)
			{
				Map<SimilarGroup<String>, DetailedFacetItem> groupMap = partitionDocsByGroup(docs, groupAssigner);
				facetMap.put("groups", groupMap.values());
			}

			// people
			Map<Contact, DetailedFacetItem> peopleMap = partitionDocsByPerson(docs, addressBook);
			facetMap.put("people", peopleMap.values());

			// direction
			Map<String, DetailedFacetItem> directionMap = partitionDocsByDirection(docs, addressBook);
			if  (directionMap.size() > 1)
				facetMap.put("direction", directionMap.values());

			// flags -- provide them only if they have at least 2 types in these docs. if all docs have the same value for a particular flag, no point showing it.
			Map<String, DetailedFacetItem> doNotTransferMap = partitionDocsByDoNotTransfer(docs);
			if  (doNotTransferMap.size() > 1)
			facetMap.put("transfer", doNotTransferMap.values());
			Map<String, DetailedFacetItem> transferWithRestrictionsMap = partitionDocsByTransferWithRestrictions(docs);
			if  (transferWithRestrictionsMap.size() > 1)
				facetMap.put("restrictions", transferWithRestrictionsMap.values());
			Map<String, DetailedFacetItem> reviewedMap = partitionDocsByReviewed(docs);
			if  (reviewedMap.size() > 1)
				facetMap.put("reviewed", reviewedMap.values());

			List<DetailedFacetItem> tagItems = new ArrayList<DetailedFacetItem>();
			Set<Document> unannotatedDocs = new LinkedHashSet<Document>(docSet);
			for (String tag : tagToDocs.keySet())
			{
				Set<Document> docsForTag = tagToDocs.get(tag);
				docsForTag.retainAll(docSet);
				unannotatedDocs.removeAll(docsForTag);
				tagItems.add(new DetailedFacetItem(tag, tag, new ArrayList<Document>(docsForTag), "annotation", tag));
			}
			if (unannotatedDocs.size() > 0)
				tagItems.add(new DetailedFacetItem("none", "none", new ArrayList<Document>(unannotatedDocs), "annotation", "" /* empty value for annotation */));

			if (tagItems.size() > 1)
				facetMap.put("annotations", tagItems);

			// attachments
			if (!ModeConfig.isPublicMode())
			{
				Map<String, DetailedFacetItem> attachmentTypesMap = partitionDocsByAttachmentType(docs);
				facetMap.put("attachment type", attachmentTypesMap.values());
			}
		}

		if (!ModeConfig.isPublicMode())
		{
			Map<String, DetailedFacetItem> folderNameMap = partitionDocsByFolder(docs);
			if  (folderNameMap.size() > 1)
				facetMap.put("folders", folderNameMap.values());
		}

		// sort so that in each topic, the heaviest facets are first
		for (String s : facetMap.keySet())
		{
			Collection<DetailedFacetItem> detailedFacets = facetMap.get(s);
			List<DetailedFacetItem> list = new ArrayList<DetailedFacetItem>(detailedFacets);
			Collections.sort(list);
			facetMap.put(s, list);
		}

		return facetMap;
	}

	public static Pair<Date, Date> getDateRange(Collection<? extends DatedDocument> docs)
	{
		Date first = null, last = null;
		for (DatedDocument dd : docs)
		{
			if (dd.date == null)
				continue; // should not happen
			if (first == null)
			{
				first = last = dd.date;
				continue;
			}
			if (dd.date.before(first))
				first = dd.date;
			if (dd.date.after(last))
				last = dd.date;
		}
		return new Pair<Date, Date>(first, last);
	}

	public static String getDateRangeAsString(Collection<? extends DatedDocument> docs)
	{
		Pair<Date, Date> p = getDateRange(docs);
		Date first = p.getFirst();
		Date last = p.getSecond();

		String result = "";
		if (first == null)
			result += "??";
		else
		{
			Calendar c = new GregorianCalendar();
			c.setTime(first);
			result += CalendarUtil.getDisplayMonth(c) + " " + c.get(Calendar.DAY_OF_MONTH) + ", " + c.get(Calendar.YEAR);
		}
		result += " to ";
		if (last == null)
			result += "??";
		else
		{
			Calendar c = new GregorianCalendar();
			c.setTime(last);
			result += CalendarUtil.getDisplayMonth(c) + " " + c.get(Calendar.DAY_OF_MONTH) + ", " + c.get(Calendar.YEAR);
		}
		return result;
	}

    public static <D extends DatedDocument> List<D> selectDocsByDateRange(Collection<D> c, int year, int month)
    {
        return selectDocsByDateRange(c, year, month, -1);
    }

    // date, month is 1-based NOT 0-based
	// if month is < 0, it is ignored
	public static <D extends DatedDocument> List<D> selectDocsByDateRange(Collection<D> c, int year, int month, int date)
	{
        //Calendar date is not 0 indexed: https://docs.oracle.com/javase/7/docs/api/java/util/Calendar.html#DATE
		--month; // adjust month to be 0 based because that's what calendar gives us
		boolean invalid_month = month < 0 || month > 11;
        boolean invalid_date = date<1 || date>31;
		List<D> result = new ArrayList<D>();
		for (D d : c)
		{
			Calendar cal = new GregorianCalendar();
			cal.setTime(d.date);
			int doc_year = cal.get(Calendar.YEAR);
			int doc_month = cal.get(Calendar.MONTH);
            int doc_date = cal.get(Calendar.DATE);
			if (year == doc_year && (invalid_month || month == doc_month) && (invalid_date || date == doc_date))
				result.add(d);
		}
		return result;
	}

	/*
	 * return docs in given date range (inclusive), sorted by date
	 * month is 1-based NOT 0-based. Date is 1 based.
	 * see calendarUtil.getDateRange specs for handling of the y/m/d fields.
	 * if month is < 0, it is ignored, i.e. effectively 1 for the start year and
	 * 12 for the end year
	 * returns docs with [startDate, endDate] both inclusive
	 */
	public static List<Document> selectDocsByDateRange(Collection<DatedDocument> c, int startY, int startM, int startD, int endY, int endM, int endD)
	{
		Pair<Date, Date> p = CalendarUtil.getDateRange(startY, startM - 1, startD, endY, endM - 1, endD);
		Date startDate = p.getFirst(), endDate = p.getSecond();

		List<Document> result = new ArrayList<>();
		for (DatedDocument d : c)
		{
            //we want docs with the same date (year, month, date) or after start date
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			if (!startDate.after(d.date) && !endDate.before(d.date))
				result.add(d);
		}
		// Collections.sort(result);
		return result;
	}

	/**
	 * picks docs from given docs with indices from the given nums.
	 * format for each num is:
	 * // 2-10 or 14 i.e. a single page# or a comma separate range.
	 * special case: "all" selects all docs
	 */
	public static List<Document> getDocNumbers(List<Document> docs, String nums[])
	{
		List<Document> result = new ArrayList<Document>();

		if (docs == null || nums == null)
			return result;

		if (nums.length == 1 && "all".equalsIgnoreCase(nums[0]))
		{
			result.addAll(docs);
			return result;
		}

		// format for s is:
		// 2-10 or 14 i.e. a single page# or a comma separate range
		for (String s : nums)
		{
			int startIdx = 0, endIdx = -1;

			try {
				if (s.indexOf("-") >= 0)
				{ // page range
					StringTokenizer pageST = new StringTokenizer(s, "-");
					startIdx = Integer.parseInt(pageST.nextToken());
					endIdx = Integer.parseInt(pageST.nextToken());
				}
				else
					startIdx = endIdx = Integer.parseInt(s);
			} catch (Exception e) {
				JSPHelper.log.error("Bad doc# string in query: " + s);
				continue;
			}

			for (int idx = startIdx; idx <= endIdx; idx++)
			{
				if (idx < 0 || idx >= docs.size())
				{
					JSPHelper.log.error("Bad doc# " + idx + " # docs = " + docs.size() + " doc num spec = " + s);
					continue;
				}
				result.add(docs.get(idx));
			}
		}
		return result;
	}

	/**
	 * returns set of all blobs that have an attachment that ends in ANY one of
	 * the given tails
	 */
	public static Set<Blob> getBlobsForAttachments(Collection<? extends Document> docs, String[] attachmentTails, BlobStore attachmentsStore)
	{
		Set<Blob> result = new LinkedHashSet<Blob>();
		if (attachmentTails == null)
			return result; // empty results
		if (attachmentsStore == null)
		{
			JSPHelper.log.error("No attachments store!");
			return result;
		}

		Set<String> neededAttachmentTails = new LinkedHashSet<String>();
		for (String s : attachmentTails)
			neededAttachmentTails.add(s);
		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;

			EmailDocument ed = (EmailDocument) d;
			if (ed.attachments == null)
				continue;
			for (Blob b : ed.attachments)
			{
				String url = attachmentsStore.getRelativeURL(b);
				String urlTail = Util.URLtail(url);
				if (neededAttachmentTails.contains(urlTail))
				{
					result.add(b);
				}
			}
		}
		return result;

	}

	/**
	 * returns set of all blobs whose type match ANY of the given extensions.
	 * ("none" is a valid type) and matches attachments that don't have an
	 * extension.
	 */
	public static Set<Blob> getBlobsForAttachmentTypes(Collection<? extends Document> docs, String[] attachmentTypes)
	{
		Set<Blob> result = new LinkedHashSet<Blob>();
		// convert to a set for fast lookup
		Set<String> attachmentTypesSet = new LinkedHashSet<String>();
		for (String t : attachmentTypes)
			attachmentTypesSet.add(t);

		for (Document d : docs)
		{
			if (!(d instanceof EmailDocument))
				continue;

			EmailDocument ed = (EmailDocument) d;
			if (ed.attachments == null)
				continue;
			for (Blob b : ed.attachments)
			{
				String ext = Util.getExtension(b.filename);
				if (ext == null)
					ext = "none";
				ext = ext.toLowerCase();
				if (attachmentTypesSet.contains(ext))
				{
					result.add(b);
				}
			}
		}
		return result;
	}

	/**
	 * groupIdx can be -1, in which case we returns docs that are not assigned
	 * to any group
	 */
	public static List<EmailDocument> getDocsForGroupIdx(Collection<EmailDocument> docs, AddressBook addressBook, GroupAssigner groupAssigner, int groupIdx)
	{
		List<EmailDocument> result = new ArrayList<EmailDocument>();
		List<SimilarGroup<String>> groups = groupAssigner.getSelectedGroups();
		for (EmailDocument ed : docs)
		{
			List<String> rawEmailAddrs = ed.getParticipatingAddrsExcept(addressBook.getOwnAddrs());
			List<String> canonicalEmailAddrs = addressBook.convertToCanonicalAddrs(rawEmailAddrs);
			Collections.sort(canonicalEmailAddrs);
			Group<String> emailGroup = new Group<String>(canonicalEmailAddrs);
			int x = Group.bestFit(groups, emailGroup);
			// x can be -1, which is ok
			if (x == groupIdx)
				result.add(ed);
		}
		return result;
	}

	/** assigns colors to tags and sorts the rest by color */
	public static List<CardTerm> computerTermOrderAndColors(List<CardTerm> tagList, int displayedCloudNum, Archive archive, GroupAssigner groupAssigner) throws IOException
	{
		List<CardTerm> filteredTagList = new ArrayList<CardTerm>();
		filteredTagList.addAll(tagList);

		List<Pair<CardTerm, Integer>> pairs = new ArrayList<Pair<CardTerm, Integer>>();

		List<CardTerm> result = new ArrayList<CardTerm>();
		if (filteredTagList.size() == 0)
			return result;

		// sort all the tags
		for (CardTerm tag : filteredTagList)
		{
			IndexUtils.computeColorsForTag(archive, displayedCloudNum, tag, groupAssigner);
			int color = tag.bestColor();
			if (color == -1)
				pairs.add(new Pair<CardTerm, Integer>(tag, Integer.MAX_VALUE)); // color = -1 should appear last
			else
				pairs.add(new Pair<CardTerm, Integer>(tag, color));
		}

		Util.sortPairsBySecondElementIncreasing(pairs);

		for (Pair<CardTerm, Integer> pair : pairs)
			result.add(pair.getFirst());

		return result;
	}

	public static void computeColorsForTag(Archive archive, int cloudNum, CardTerm tct, GroupAssigner groupAssigner) throws IOException
	{
		if (tct.colorWeights != null)
			return; // do nothing if we already computed colors for tags

		// hack for the fact that our terms map does not have 3-grams or more
		// we cap the term to 2 tokens for the purposes of looking up terms Map

		Map<Integer, Float> colorWeights;
		if (groupAssigner != null)
		{
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setCluster(cloudNum);
            options.setQueryType(Indexer.QueryType.ORIGINAL);
			Collection<Document> list = archive.indexer.docsForQuery(tct.lookupTerm, options);
			colorWeights = groupAssigner.getAssignedColors(list);
		}
		else
			colorWeights = new LinkedHashMap<Integer, Float>();

		tct.setColors(colorWeights);
	}

	/** sort by site-alpha, so e.g. all amazon links show up together */
	public static void sortLinks(List<String> linksList)
	{
		Collections.sort(linksList, new Comparator<String>() {
			public int compare(String li1, String li2)
			{
				String site1 = Util.getTLD(li1);
				String site2 = Util.getTLD(li2);
				return site1.compareTo(site2);
			}
		});
	}

	public static String stripExtraSpaces(String name){
		String str = "";
		String[] words = name.split("\\s+");
		for(int wi=0;wi<words.length;wi++) {
			str += words[wi];
			if(wi<words.length-1)
				str += " ";
		}
		return str;
	}

	/**
	 * all suffixes of prefixes or all prefixes of suffixes.
	 * */
	public static Set<String> computeAllSubstrings(Set<String> set) {
		Set<String> substrs = new HashSet<String>();
		for (String s : set) {
			substrs.addAll(computeAllPrefixes(computeAllSuffixes(s)));
		}
		return substrs;
	}

	public static List<String> computeAllSubstrings(Set<String> set, boolean sort) {
		Set<String> substrs = new HashSet<String>();
		for (String s : set) {
			substrs.addAll(computeAllPrefixes(computeAllSuffixes(s)));
		}
		//sort
		Map<String, Integer> substrlen = new LinkedHashMap<String, Integer>();
		for(String substr: substrs)
			substrlen.put(substr, substr.length());
		List<Pair<String,Integer>> ssubstrslen = Util.sortMapByValue(substrlen);
		List<String> ssubstrs = new ArrayList<String>();
		for(Pair<String,Integer> p: ssubstrslen)
			ssubstrs.add(p.getFirst());
		return ssubstrs;
	}

	public static Set<String> computeAllSubstrings(String s)
	{
        s = s.replaceAll("^\\W+|\\W+$","");
		Set<String> set = new LinkedHashSet<String>();
		set.add(s);
		return computeAllSubstrings(set);
	}

	/**@param sort in descending order of length*/
	public static List<String> computeAllSubstrings(String s, boolean sort)
	{
        s = s.replaceAll("^\\W+|\\W+$","");
        Set<String> set = new LinkedHashSet<String>();
		set.add(s);
		return computeAllSubstrings(set, sort);
	}

	public static Set<String> computeAllPrefixes(Set<String> set)
	{
		Set<String> result = new LinkedHashSet<String>();
		for (String s : set)
		{
			String prefix = "";
			StringTokenizer st = new StringTokenizer(s);
			while (st.hasMoreTokens())
			{
				if (prefix.length() > 0)
					prefix += " ";
				prefix += st.nextToken();//.toLowerCase();
				result.add(prefix);
			}
		}
		return result;
	}

	public static Set<String> computeAllPrefixes(String s)
	{
		Set<String> set = new LinkedHashSet<String>();
		set.add(s);
		return computeAllPrefixes(set);
	}

	static Set<String> computeAllSuffixes(Set<String> set)
	{
		Set<String> result = new HashSet<String>();
		for (String s : set) {
			String suffix = "";
			String[] words = s.split("\\s+");
			for (int i = words.length - 1; i >= 0; i--) {
				if (suffix.length() > 0)
					suffix = " " + suffix;
				suffix = words[i] + suffix;
				result.add(suffix);
			}
		}
		return result;
	}

	static Set<String> computeAllSuffixes(String s)
	{
		Set<String> set = new LinkedHashSet<String>();
		set.add(s);
		return computeAllSuffixes(set);
	}

	/** experimental method, not used actively */
	//	public static Map<Integer, Collection<Collection<String>>> nameCooccurrenceInParas(Collection<EmailDocument> docs) throws IOException, GeneralSecurityException, ClassCastException, ClassNotFoundException
	//	{
	//		Pattern p = Pattern.compile("[1-9][0-9]*|\\d+[\\.]\\d+"); // don't want #s starting with 0, don't want numbers like 1.
	//		// numMap not used currently
	//		Map<String, List<String>> numMap = new LinkedHashMap<String, List<String>>();
	//
	//		Map<Integer, Collection<Collection<String>>> namesMap = new LinkedHashMap<Integer, Collection<Collection<String>>>();
	//		
	//		for (EmailDocument ed: docs)
	//		{
	//			System.out.println ("D" + ed.docId);
	//		
	//			String contents = "";
	//			try { ed.getContents(); }
	//			catch (ReadContentsException e) { Util.print_exception(e, log); }
	//
	//			Matcher m = p.matcher(contents);
	//			List<String> nums = new ArrayList<String>();
	//			while (m.find())
	//			{
	//				String num = m.group();
	//				
	//				try { 
	//					Integer.parseInt(num);  // just to check for exception
	//					nums.add(num);
	//				} catch (Exception e) {
	//					try { 
	//						Double.parseDouble(num); // just to check for exception
	//						nums.add(num);
	//					}
	//					catch (Exception e1) { Util.report_exception(e1); }
	//				}			
	//			}
	//	
	//			numMap.put(ed.docID, nums);
	//			Collection<String> paras = Util.breakIntoParas(contents);
	//			Collection<Collection<String>> docNames = new ArrayList<Collection<String>>();
	//	
	//			for (String para: paras)
	//			{
	//				List<Pair<String, Float>> names = NER.namesFromText(para);
	//				List<String> paraNames = new ArrayList<String>();
	//				for (Pair<String, ?> pair: names)
	//				{
	//					String name = pair.getFirst();
	//					name = name.replace("\n", " ").trim().toLowerCase().intern();
	//					paraNames.add(name);
	//				}
	//				docNames.add(paraNames);
	//			}
	//			namesMap.put(ed.getUniqueId(), docNames);
	//		}
	//		
	//		return namesMap;
	//	}

	public static Set<String> readCanonicalOwnNames(AddressBook ab)
	{
		// convert own names to canonical form
		Set<String> canonicalOwnNames = new LinkedHashSet<String>();
		Set<String> ownNames = (ab != null) ? ab.getOwnNamesSet() : null;
		if (ownNames == null)
			return canonicalOwnNames;

		for (String s : ownNames)
			if (s != null)
				canonicalOwnNames.add(s.toLowerCase());
		return canonicalOwnNames;
	}

	/** returns all languages in a set of docs */
	public static Set<String> allLanguagesInDocs(Collection<? extends Document> docs)
	{
		Set<String> result = new LinkedHashSet<String>();
		for (Document d : docs)
			if (d.languages != null)
				result.addAll(d.languages);
		if (result.size() == 0)
			result.add("english");
		return result;
	}

	public static List<Document> selectDocsByRegex(Archive archive, Collection<Document> allDocs, String term)
	{
		List<Document> result = new ArrayList<Document>();
		Pattern pattern = null;
		try {
			pattern = Pattern.compile(term);
		} catch (Exception e) {
			Util.report_exception(e);
			return result;
		}

		for (Document d : allDocs) {
			if (!Util.nullOrEmpty(d.description)) {
				if (pattern.matcher(d.description).find()) { // d.getHeader() will get message ID which may false match SSN patterns etc.
					result.add(d);
					continue;
				}
			}
			String text = archive.getContents(d, false /* full message */);
			if (pattern.matcher(text).find())
				result.add(d);
		}
		return result;
	}

	static Set<Document> selectDocsByContact(AddressBook ab, Collection<EmailDocument> docs, Set<String> contact_names, Set<String> contact_emails)
	{
		Set<Document> result = new LinkedHashSet<Document>();

		// look up ci for given name
		// look up emails for ci
		for (EmailDocument ed : docs)
		{
			// assemble all names and emails for this messages
			List<String> allEmailsAndNames = ed.getAllAddrs();
			allEmailsAndNames.addAll(ed.getAllNames());

			// and match against the given ci
			for (String s : allEmailsAndNames)
			{
				if (contact_names.contains(s) || contact_emails.contains(s))
				{
					result.add(ed);
					break;
				}
			}
		}
		return result;
	}

	/**
	 * returns docs with the given email or name. looks for all aliases of the
	 * person's name, not just the given one.
	 * goes through ALL docs
	 */
	public static Set<Document> selectDocsByContact(AddressBook ab, Collection<EmailDocument> docs, Contact c)
	{
		if (c == null)
			return new LinkedHashSet<Document>();

		return selectDocsByContact(ab, docs, c.names, c.emails);
	}

	public static Set<Document> selectDocsByContact(AddressBook ab, Collection<EmailDocument> docs, Set<Contact> cset)
	{
		if (cset == null)
			return new LinkedHashSet<Document>();

		Set<String> cnames = new LinkedHashSet<String>();
		Set<String> cemails = new LinkedHashSet<String>();
		for (Contact c : cset) {
			cnames.addAll(c.names);
			cemails.addAll(c.emails);
		}
		return selectDocsByContact(ab, docs, cnames, cemails);
	}

	public static String canonicalizeEntity(String e)
	{
		if (e == null)
			return e;
		e = e.replaceAll("\\s\\s", " ");
		return e.trim().toLowerCase();
	}

	public static void main(String args[])
	{
		//System.out.println(query("this is a test", "testing|is|match"));
		List<String> substrs = computeAllSubstrings("Some thing here", true);
		for(String substr: substrs)
			System.err.print(substr+" ::: ");
		System.err.println();
	}

	static final boolean	PHRASES_CAN_SPAN_EMPTY_LINE	= false;	// false by default, no external controls for this.

	// if false, empty line is treated as a sentence separator

	/**
	 * NEEDS REVIEW.
	 * removes http links etc, adds them in linkList if it is not null. removes
	 * quoted parts of message
	 */
	public static void populateDocLinks(Document d, String text, List<LinkInfo> linkList, boolean inclQM) throws IOException
	{
		BufferedReader br = new BufferedReader(new StringReader(text));

		while (true)
		{
			String line = br.readLine();
			if (line == null)
				break;

			line = line.trim();

			// strip links
			if (line.toLowerCase().contains("http:"))
			{
				StringTokenizer st = new StringTokenizer(line, " \r\n\t<>\""); // tokenize based on things likely to identify starting of link, http://...
				while (st.hasMoreTokens())
				{
					String s = st.nextToken();
					s = Util.stripPunctuation(s);
					if (s.toLowerCase().startsWith("http:"))
					{
						if (linkList != null && d != null)
							linkList.add(new LinkInfo(s, d));
					}
				}
			}
		}
	}

	// normalizes newlines by getting rid of \r's
	public static String normalizeNewlines(String s)
	{
		if (s == null)
			return null;
		String result = s.replaceAll("\r\n", "\n"); // note: no double escapes needed, \r is directly used as a char by java, not an esc. sequence
		result = result.replaceAll("\r", "\n");
		return result;
	}
}
