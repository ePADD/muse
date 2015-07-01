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


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.JSONUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;

/* Note: this indexer is being gradually retired in favor of LuceneIndexer.
 * Optimized 2-level indexer that scores MultiDocs (that themselves contain entire documents as subdocs) against each other,
 * and gets top terms per multi-doc.
 * however, index can be looked up with specific terms, and returns subdocs that contain the term.
 * best effort to be memory efficient, goal is to take < 300MB for 20,000 mostly small documents (like email messages).
 * Index stores NER terms also.
 */
public class Indexer implements StatusProvider, java.io.Serializable {
	// map of term ->
	// compute term -> freq for this doc
	// add term -> List of <doc,freq>
	// for each term, compute tf-idf per doc.
	// for each doc, gather terms, sort by tf-idf
    static Log log = LogFactory.getLog(Indexer.class);
    private static final long serialVersionUID = 1L;

    private static final boolean DEFAULT_INCLUDE_QUOTED_MESSAGES = false;
    static final int DEFAULT_SUBJECT_WEIGHT = 2; // weight given to email subject; 2 means subject is given 2x weight
    private static final boolean DEFAULT_INCREMENTAL_TFIDF = true;
    private static final boolean PHRASES_CAN_SPAN_EMPTY_LINE = false; // false by default, no external controls for this.
	// if false, empty line is treated as a sentence separator

	private static final int MAX_TOKEN_LENGTH = 20;
	private static final int MAX_DOCUMENT_SIZE = 200000;
	protected static final int TERM_FREQ_PER_SUPERDOC_THROTTLE = 10000; // if a doc has more than this # of the same term, it will be throttled to this number. setting to 10,000 so effectively not throttling
	private static int NGRAM_LENGTH = 2;
	public static final int MAX_MAILING_LIST_NAME_LENGTH = 20;
	public static final int N_TOP_TERMS_PICKED_PER_SUPERDOC = 30;
	public static final int MAX_TERMS_PER_DOC = 4;
	public static final boolean MERGE_TERMS = false; // turned off since we're doing NER and only using unigrams

	@SuppressWarnings("unused")
	private static Set<String> stopWords = new LinkedHashSet<String>(); // note - static so that all indexers share the same file
	private static Set<String> commonDictWords = new LinkedHashSet<String>(); // these will be pruned from the output
	public static Set<String> fullDictWords = new LinkedHashSet<String>();
	public static Set<String> topNames = new LinkedHashSet<String>();
	private static Set<String> joinWords = new LinkedHashSet<String>(); // this will be ignored for the indexing

	static {
		try {
			InputStream is = Indexer.class.getClassLoader().getResourceAsStream("join.words");
			joinWords = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
			is = Indexer.class.getClassLoader().getResourceAsStream("stop.words");
			stopWords = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
			is = Indexer.class.getClassLoader().getResourceAsStream("dict.words");
			commonDictWords = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
			is = Indexer.class.getClassLoader().getResourceAsStream("dict.words.full");
			fullDictWords = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
			is = Indexer.class.getClassLoader().getResourceAsStream("top-names");
			topNames = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));

		} catch (Exception e) { }
	}

	static Set<String> placeNames = new LinkedHashSet<String>();
	IndexOptions io;
	private boolean cancel = false;
//	private Set<String> stopTokensSeen = new LinkedHashSet<String>();
//	private Set<String> dictTokensSeen = new LinkedHashSet<String>();
	private List<LinkInfo> links = new ArrayList<LinkInfo>();
    public Set<String> importantTermsCanonical = new LinkedHashSet<String>();
    private Set<String> importantTermsPrefixes = new LinkedHashSet<String>(); // includes full terms also
	Collection<String> dataErrors = new LinkedHashSet<String>();
	int nUnigrams = 0, nBigrams = 0, nTokens = 0, nStopTokens = 0, nDictTokens = 0;
	long processedTextLength = 0, nProcessedNames = 0;

	protected List<MultiDoc> docClusters;
	Map<Integer,Integer> nonEmptyTimeClusterMap = new LinkedHashMap<Integer, Integer>(); // mapping of non-zero time cluster index to actual time cluster index
	public List<Card> cards;
	private String clusterStats = "";
	private String memoryStats = "";

	/* Memory considerations for these indices:
	 * memory consumption total 195MB for indexing 2,900 messages.
	 * null out docIndex => 193 MB, so doesn't save much
	 * null out subdocIndex => 145MB => can be a big saving
	 * null out termIndex => 31MB
	 * docIndex is kept only for computing IDF.
	 * we need to iterate on all terms in just that doc.
	 * we could do it another way too, but docIndex memory consumption does not seem to be significant
	 *
	 * there are 2 kinds of postings: one for superdocs and one for subdocs
	 * technically the subdoc postings don't need to have tf-idf scores.
	 */
	// the most memory-intensive of these is the termToSubDocIndex
	/// e.g. on palin archive, total mem = 190MB, IndexEmailDriver takes 146MB, Indexer takes 145 MB, termToSubDocIndex takes 125MB, termToPostings takes 7MB and termToSuperDocCountIndex takes 3MB
	// to alleviate this, we make an exception to the rule that maps/sets should always be linkedhashmap/set's
	// and just for termToSubDocIndex, we use a plain hashMap, and key-values are plain hashsets.
	private Map<String, Set<Integer>> termToSubDocIndex = new HashMap<String, Set<Integer>>(); // term -> (sub)docs that term appears in (used for term lookup)
	private Map<String, IntSet> packedTermToSubDocIndex = new HashMap<String, IntSet>(); // term -> (sub)docs that term appears in (used for term lookup)
	private Map<Document, Integer> docToDocIdx = new LinkedHashMap<Document, Integer>();
	
	protected Map<String, List<Posting>> termToPostingsIndex = new LinkedHashMap<String, List<Posting>>(); // master list for term -> posting
	protected Map<String, Integer> termToSuperDocCountIndex = new LinkedHashMap<String, Integer>(); // term -> # of superdocs that contain the term (used for computing idf)
	protected Map<Document, List<Posting>> superdocToPostingsIndex = new LinkedHashMap<Document, List<Posting>>(); // (super) doc -> postings for that doc
	public Map<String, Integer> locationCounts = new LinkedHashMap<String, Integer>(); // the # of times a string was listed as a location type by NER
	List<Document> docs = new ArrayList<Document>();
	List<Document> subdocs = new ArrayList<Document>();

	public Indexer(IndexOptions io) throws IOException
	{
		clear();
		this.io = io;
		try {
		if (io != null && io.do_NER)
			NER.initialize();
		} catch (Exception e) {
			Util.report_exception(e);
		}
	}

	public void clear()
	{
		cancel = false;
		termToPostingsIndex.clear();
		superdocToPostingsIndex.clear();
		links.clear();
		importantTermsCanonical.clear();
		importantTermsPrefixes.clear();
	}

	/** tofix: this seems a convoluted way to get a multidoc! */
	public String getClusterDescription(int idx)
	{
		// unfortunately no way to directly look up description from index, so iterate idx times
		if (idx >= 0 && idx < superdocToPostingsIndex.size())
		{
			int i = 0;
			for (Document d: superdocToPostingsIndex.keySet())
			{
				if (i == idx)
					return ((MultiDoc) d).description;
				i++;
			}
		}
		log.warn ("Can't look up description for time cluster " + idx + " max = " + superdocToPostingsIndex.size());
		return "Cluster " + idx;
	}

	public boolean clustersIncludeAllDocs(Collection<Document> docs)
	{
		Set<Document> allIndexerDocs = new LinkedHashSet<Document>();
		for (MultiDoc mdoc: docClusters)
			allIndexerDocs.addAll(mdoc.docs);
		for (Document doc: docs)
			if (!allIndexerDocs.contains(doc))
				return false;
		return true;
	}

	public Collection<String> getDataErrors()
	{
		return Collections.unmodifiableCollection(dataErrors);
	}
	
	public List<MultiDoc> clustersForDocs(Collection<Document> docs)
	{
		Map<Document, Integer> map = new LinkedHashMap<Document, Integer>();
		int i = 0;
		for (MultiDoc mdoc: docClusters)
		{
			for (Document d: mdoc.docs)
				map.put(d, i);
			i++;
		}

		List<MultiDoc> new_mDocs = new ArrayList<MultiDoc>();
		for (@SuppressWarnings("unused") MultiDoc md: docClusters)
			new_mDocs.add(null);

		for (Document d: docs)
		{
			int x = map.get(d);
			MultiDoc new_mDoc = new_mDocs.get(x);
			if (new_mDoc == null)
			{
				MultiDoc original = docClusters.get(x);
				new_mDoc = new MultiDoc(original.docNum, original.description);
				new_mDocs.set(x, new_mDoc);
			}
			new_mDoc.add(d);
		}

		List<MultiDoc> result = new ArrayList<MultiDoc>();
		for (MultiDoc md: new_mDocs)
			if (md != null)
				result.add(md);

		return result;
	}

	private String computeClusterStats(List<MultiDoc> clusters2)
	{
		int nClusters = clusters2.size();
		String clusterCounts = "";
		for (MultiDoc mdoc: clusters2)
			clusterCounts += mdoc.docs.size() + "-";
		return nClusters + " time clusters with message counts: " + clusterCounts;
	}

	@SuppressWarnings("unused")
	private void computeTimeClusterStats(List<MultiDoc> docClusters2)
	{
		// we're assuming docs are sorted within the time clusters which may not be true ??
		String firstDate = "?";
		String lastDate = "?";
		if (docClusters2 != null && docClusters2.size() > 0 && docClusters2.get(0) != null)
		{
			DatedDocument dd = (DatedDocument) docClusters2.get(0).docs.get(0);
			firstDate = Util.formatDate(dd.getDate());
			MultiDoc lastCluster = null;
			int last = docClusters2.size()-1;
			while (last >= 0)
			{
				lastCluster = docClusters2.get(last);
				if (lastCluster.docs.size() > 0)
					break;
				last--;
			}
			if (lastCluster != null)
			{
				Document d = lastCluster.docs.get(lastCluster.docs.size()-1);
				Date date = ((DatedDocument) d).getDate();
				lastDate = Util.formatDate(date);
			}
		}
		clusterStats = computeClusterStats((List) docClusters2) + " Date range: " + firstDate + " to " + lastDate;
	}

	public static Set<String> readStreamAndInternStrings(Reader r)
	{
		Set<String> result = new LinkedHashSet<String>();
		try {
			LineNumberReader lnr = new LineNumberReader(r);
			while (true)
			{
				String word = lnr.readLine();
				if (word == null)
				{
					lnr.close();
					break;
				}
				word = word.trim();
				if (word.startsWith("#") || word.length() == 0)
					continue;
				word = IndexUtils.canonicalizeMultiWordTerm(word, false); // TOFIX: not really sure if stemming shd be off
				word = InternTable.intern(word);
				result.add(word);
			}
		} catch (IOException e) { log.warn ("Exception reading reader " + r + ": " + e + Util.stackTrace(e)); }

		return result;
	}

	public static Set<String> readFileAndInternStrings(String file)
	{
		Set<String> result = new LinkedHashSet<String>();
		try {
			Reader r = null;
			if (file.toLowerCase().endsWith(".gz"))
				r = new InputStreamReader (new GZIPInputStream(new FileInputStream(file)));
			else
				r = new FileReader(file);

			LineNumberReader lnr = new LineNumberReader(r);
			while (true)
			{
				String word = lnr.readLine();
				if (word == null)
				{
					lnr.close();
					break;
				}
				word = word.trim();
				if (word.startsWith("#") || word.length() == 0)
					continue;
				word = IndexUtils.canonicalizeMultiWordTerm(word, false); // TOFIX: not really sure if stemming shd be on
				word = InternTable.intern(word);
				result.add(word);
			}
		} catch (IOException e) { log.warn ("Exception reading file " + file + ": " + e + Util.stackTrace(e)); }

		return result;
	}

	// returns true if term is to be filtered out
	// currently rules out stop words or long words
	// testing out dropping pure numbers
	// note: term should already be canonicalized
	private static boolean drop(String token)
	{
		if (commonDictWords.contains(token) || (token.length() > MAX_TOKEN_LENGTH))
			return true;

		// try to parse for number only if term has a chance of being one
		// earlier we were trying to parseInt each term which is expensive for runtime
		// since it throws an exception per non-numeric term
		if (Character.isDigit(token.charAt(0)))
		{
			char[] chars = token.toCharArray();
			for (char c: chars)
				if (Character.isDigit(c))
					return true;
		}
        return false;
	}

	@SuppressWarnings("unused")
	private boolean isDictionaryWord (String term)
	{
		return commonDictWords.contains(IndexUtils.canonicalizeTerm(term));
	}

	public static boolean isJoinWord (String term)
	{
		return joinWords.contains(IndexUtils.canonicalizeTerm(term));
	}

	// removes http links etc, puts them in linkList if it is not null
		// removes quoted messages unless inclQM is true
	public static String preprocessDocument(Document doc, String text, List<LinkInfo> linkList, boolean inclQM) throws IOException
	{
		StringBuilder result = new StringBuilder();
		BufferedReader br = new BufferedReader(new StringReader(text));

		// stopper for the tokenizer when we meet a line that needs to be ignored
		String stopper = " . ";

		while (true)
		{
			String line = br.readLine();
			if (line == null)
				break;

			line = line.trim();

			if (!inclQM && line.startsWith(">")) // ignore quoted messages
			{
				result.append(stopper);
				continue;
			}

			// we often see lines like this just before a quoted block begins:
			// hangal@cs.stanford.edu wrote:
			// note: we ignore the line, regardless of whether includeQM is true,
			// because we never want to consider this line, it generates garbage results
			if (line.endsWith("wrote:"))
			{
				result.append(stopper);
				continue;
			}

			if (line.startsWith("----") && line.indexOf("Original Message") >= 0)
			{
				result.append(stopper);
				continue;
			}

			// often there is an empty line separating sentences that otherwise don't have a punctuation separator.
			// e.g. regards, <empty line> <name>
			if (!PHRASES_CAN_SPAN_EMPTY_LINE && line.length() == 0)
			{
				result.append (stopper);
				continue;
			}
			//	if (line.equals("--")) // strip all lines including and after signature
			//		break;

			// strip links
			if (line.toLowerCase().contains("http:"))
			{
				// replace http links with a stop word

				StringBuilder line1 = new StringBuilder();
				StringTokenizer st = new StringTokenizer(line, " \r\n\t<>\""); // tokenize based on things likely to identify starting of link, http://...
				while (st.hasMoreTokens())
				{
					String s = st.nextToken();
					s = Util.stripPunctuation(s);
					if (s.toLowerCase().startsWith("http:"))
					{
//						log.info ("found http link: " + s);
						if (linkList != null && doc != null)
							linkList.add(new LinkInfo(s, doc));
//						line1.append ("a"); // arbitrary stop word in place of http link
						line1.append (" . "); // arbitrary stop word in place of http link
					}
					else
						line1.append (s);

					line1.append (" ");
				}
				//				String origLine = line;
				line = line1.toString(); // overwrite original line
				//				log.info (origLine + "\ntransformed to \n" + line);
			}

			result.append (line + "\n");
		}

		return result.toString();
	}

	/** computes term->list<docs> index.
	 * returns the list of names in the document text (if io.do_NER is true)
	 * this is used only to query a term for all docs that contain it.
	 * could also be done in a separate pass if its memory intensive. */
	public  List<String> indexSubdoc(String documentText, Document doc) throws IOException
	{
		subdocs.add(doc);
		int subdocNum = subdocs.size()-1;
		docToDocIdx.put(doc, subdocNum);
		
		// parse the doc, just to get all the canonical terms. tempMap will be thrown away and the postings will not be used.
		// that's why set maintainPositions = false.
		Triple<Map<String, Posting>, Map<String, Posting>, List<String>> t = getTermMap(documentText, doc, io, false /* maintain positions */, io.do_NER, io.do_allText /* do plain */, null /* location counts  */ );
		Map<String, Posting> NERtermMap = t.getFirst(), plainTermMap = t.getSecond();
		List<String> names = t.getThird();

		// transfer both NER and plain terms in the termToSubDocIndex, which we will use for lookup
		for (String term: NERtermMap.keySet())
		{
			Set<Integer> docs = termToSubDocIndex.get(term);
			if (docs == null)
			{
				docs = new HashSet<Integer>(2); // note: plain hashset to save memory, not a linkedHashSet
				termToSubDocIndex.put (term, docs);
			}
			docs.add(subdocNum);
		}

		for (String term: plainTermMap.keySet())
		{
			Set<Integer> docs = termToSubDocIndex.get(term);
			if (docs == null)
			{
				docs = new HashSet<Integer>(2); // note: plain hashset to save memory, not a linkedHashSet
				termToSubDocIndex.put (term, docs);
			}
			docs.add(subdocNum);
		}
		NERtermMap.clear();
		plainTermMap.clear();
		return names;
	}

	// should be called only if io.do_NER is true
	private void indexSuperDocNames(List<String> names, Document doc) throws IOException
	{
		docs.add(doc);
		
		Map<String, Posting> termMap = new LinkedHashMap<String, Posting>();
		for (String term: names) 
		{
			// could be a multiWordTerm
			String canonicalTerm = IndexUtils.canonicalizeMultiWordTerm(term, false); // we do canonicalization as usual because we need to eliminate multiple spaces... but no stemming for names
			
			Posting p = termMap.get(canonicalTerm);
			if (p == null)
			{
					p = new Posting();
					p.term = InternTable.intern(canonicalTerm);
					p.originalTerm = InternTable.intern(term);
					p.tf = 0;
					if (log.isTraceEnabled())
						log.trace("New Token: " + p);
					termMap.put(p.term, p);
			}
			p.tf++;
		}
	
		// termMap is what we want to index for the cards
		for (Posting p : termMap.values())
			if (p.tf > TERM_FREQ_PER_SUPERDOC_THROTTLE)
			{
				log.info("Throttling freq to " + TERM_FREQ_PER_SUPERDOC_THROTTLE + " for posting: "  + p);
				p.tf = TERM_FREQ_PER_SUPERDOC_THROTTLE;
			}

		// set up doc index and normalize tf
		List<Posting> a = new ArrayList<Posting>(termMap.values());
		superdocToPostingsIndex.put(doc, a);

		//		normalizeTF(doc);

		// for each term in this doc, update termIndex
		for (Posting p: termMap.values())
		{
			String term = p.term;
			Integer I = termToSuperDocCountIndex.get(p.term);
			if (I == null)
				termToSuperDocCountIndex.put(p.term, 1);
			else
				termToSuperDocCountIndex.put(p.term, I+1);

			// is a complete termToPostingsIndex necessary?
			// one way we use it is to compute the total freq of a term across corpus (e.g. for locations)
			// but it could be done in a more lightweight way, perhaps
			List<Posting> postingList = termToPostingsIndex.get(term);
			if (postingList == null)
			{
				postingList = new ArrayList<Posting>();
				termToPostingsIndex.put (term, postingList);
			}
			postingList.add(p);
		}

		termMap.clear();	
	}
	
	/* workhorse method. given a "super"doc, indexes document and computes tf fields.
	 * this is what is used for computing top terms. do not call if io.do_NER is false */
	private void indexSuperDoc(String documentText, Document doc) throws IOException
	{
		docs.add(doc);
	  //  NLP.tag(documentText);

		Triple<Map<String, Posting>, Map<String, Posting>, List<String>> triple = getTermMap(documentText, doc, io, !io.do_NER /* maintain pos */, io.do_NER, !io.do_NER /* doPlain = !NER */, locationCounts);
		Map<String, Posting> tempMap;
		tempMap = triple.getSecond();

		// tempMap is what we want to index for the cards
		for (Posting p : tempMap.values())
			if (p.tf > TERM_FREQ_PER_SUPERDOC_THROTTLE)
			{
				log.info("Throttling freq to " + TERM_FREQ_PER_SUPERDOC_THROTTLE + " for posting: "  + p);
				p.tf = TERM_FREQ_PER_SUPERDOC_THROTTLE;
			}

		// set up doc index and normalize tf
		List<Posting> a = new ArrayList<Posting>(tempMap.values());
		superdocToPostingsIndex.put(doc, a);

		//		normalizeTF(doc);

		// for each term in this doc, update termIndex
		for (Posting p: tempMap.values())
		{
			String term = p.term;
			Integer I = termToSuperDocCountIndex.get(p.term);
			if (I == null)
				termToSuperDocCountIndex.put(p.term, 1);
			else
				termToSuperDocCountIndex.put(p.term, I+1);

			// is a complete termToPostingsIndex necessary?
			// one way we use it is to compute the total freq of a term across corpus (e.g. for locations)
			// but it could be done in a more lightweight way, perhaps
			List<Posting> postingList = termToPostingsIndex.get(term);
			if (postingList == null)
			{
				postingList = new ArrayList<Posting>();
				termToPostingsIndex.put (term, postingList);
			}
			postingList.add(p);
		}

		tempMap.clear();	
	}

	/* for all documents, normalize tf by computing tf/total words in doc.
	 * actually right now, we just leave normalizedTF = TF. */
	@SuppressWarnings("unused")
	private void normalizeTF(Document d)
	{
//		normalizedTF = p.tf;
		return;
		/*
			List<Posting> list = docIndex.get(d);
			int nTerms = 0; // total # of terms in this doc
			for (Posting p: list)
				nTerms += p.tf;
			for (Posting p: list)
			{
				// there are at least 3 alternate definitions of TF

	//			df.normalizedTF = ((float)df.tf)/nTerms;
				p.normalizedTF = p.tf;
//				df.normalizedTF = (float) (1+Math.log(df.tf));
			}
			*/
	}

	/** term freq across entire corpus */
	public int getTermFrequency(String term)
	{
		// is this the only place where termToPostingsIndex is used? if so, it might be cheaper to store the aggregate freq directly
		int freq = 0;
		List<Posting> postings = termToPostingsIndex.get(term);
		if (postings != null)
			for (Posting p: postings)
				freq += p.tf;
		return freq;
	}

	private void computeIDFForTermsInAllDocs()
	{
		float totalDocs = superdocToPostingsIndex.size();

		for (List<Posting> postings : superdocToPostingsIndex.values())
		{
			for (Posting p: postings)
			{
				String term = p.term;
				int docsWithThisTerm = termToSuperDocCountIndex.get(term);
				float idfForThisTerm = (float) Math.log(totalDocs/docsWithThisTerm);
				if (totalDocs == docsWithThisTerm)
					idfForThisTerm = 0.01f;
				p.idf = idfForThisTerm;
			}
		}
	}

	private void computeIDFForTermsInOneDoc(Document d)
	{
		List<Posting> dfs = superdocToPostingsIndex.get(d);
		if (dfs == null)
			return;

		float totalDocs = docs.size();
		for (Posting df: dfs)
		{
			// note df is in the docIndex,
			// we want to find how many docs have this term,
			// which we'll do by looking up the term index.
//			List<Posting> list = termIndex.get(term);
//			float docsWithThisTerm = list.size(); // how many docs this term appears in
			float docsWithThisTerm = termToSuperDocCountIndex.get(df.term);

			float idfForThisTerm = (float) Math.log(totalDocs/docsWithThisTerm);
			if (totalDocs == docsWithThisTerm)
			{
				idfForThisTerm = 0.01f;
//				if (totalDocs > 1)
//				System.err.println ("Warning: " + term + " occurs in all " + totalDocs +  " documents");
			}

			// assign this
			df.idf = idfForThisTerm;
		}
	}

	private void sortByTFIDFForOneDoc(Document d)
	{
		List<Posting> list = superdocToPostingsIndex.get(d);
		Collections.sort (list, new Comparator<Posting>() {
			  public int compare (Posting freq1, Posting freq2) {
				 float tfidf1 = freq1.tf * freq1.idf;
				 float tfidf2 = freq2.tf * freq2.idf;

				 if (tfidf2 > tfidf1)
					 return 1;
				 else if (tfidf1 > tfidf2)
					 return -1;
				 else
					 return 0;
			  }
		});
	}

	private void sortByTFIDF()
	{
		for (Document d : superdocToPostingsIndex.keySet())
			sortByTFIDFForOneDoc(d);
	}

	// compute tf idf scores for each term
	private void computeTFIDF()
	{
		computeIDFForTermsInAllDocs();
		sortByTFIDF();
	}

	// compute tf idf scores for each term
	protected void computeTFIDFForOneDoc(Document d)
	{
		Util.ASSERT(d instanceof MultiDoc);
		computeIDFForTermsInOneDoc(d);
		sortByTFIDFForOneDoc(d);
	}

	/*
	// do the 2 postings have any docs in common ?
	private List<Document> commonDocuments(String s1, String s2)
	{
		Set<Document> set1 = new LinkedHashSet<Document>();
		set1.addAll(termToSubDocIndex.get(s1));;
		set1.retainAll(termToSubDocIndex.get(s2));
		return new ArrayList<Document>(set1);
	}
	 */
		/* merges term a with term b if a's suffix matches b's prefix.
		 * side effect: removes terms subsumed by another. */
	private List<Posting> mergeConsecutiveTerms(List<Posting> list)
	{
		boolean redundant[] = new boolean[list.size()]; // all false by default,
		// redundant array tracks which terms have already been merged with something else
		// which is easier than actually removing it from the list during iteration.

		boolean changed = true;
		while (changed)
		{
			// fixed point loop, iterate till we don't merge any terms

			changed = false;

			// compare all i's and j's if they can be merged
			// somewhat expensive, O(n^2), can be implemented more efficient
			// if it becomes expensive
			// below loop always merges j into i and marks i redundant
			outer:
			for (int i = 0; i < list.size(); i++)
			{
				if (redundant[i])
					continue;

				// get iterm suffix, currently == last term of i.
				// if i has only 1 term, itermSuffix is the whole term
				String iTerm = list.get(i).term;
				String iTermOriginal = list.get(i).originalTerm;
				StringTokenizer sti = new StringTokenizer(iTerm);
				String itermLastToken = "";
				while (sti.hasMoreTokens())
					itermLastToken = sti.nextToken();

				// compare with prefix of each jterm
				for (int j = 0; j < list.size(); j++)
				{
					if ((i == j) || redundant[j])
						continue;

					// check that the terms co-occur in at least one doc
		//			List<Document> commonDocuments = commonDocuments(list.get(i).term, list.get(j).term);
		//			if (commonDocuments.size() == 0)
		//				continue;

					String jTerm = list.get(j).term;

					// if jterm is contained in iterm, ignore it
					if (iTerm.indexOf(jTerm) >= 0)
					{
						log.debug ("marking redundant " + j + ". " + jTerm);
						redundant[j] = true;
						continue;
					}

					if (jTerm.indexOf(iTerm) >= 0)
					{
						log.debug ("marking redundant " + i + ". " + iTerm);
						redundant[i] = true;
						continue outer;
					}

					StringTokenizer stj = new StringTokenizer(jTerm);
					if (!stj.hasMoreTokens())
					{
						// saw this happen once.
						log.error ("j term has no tokens: " + jTerm);
						continue;
					}

					String jtermFirstToken = stj.nextToken(); // currently, prefix == first term
					if (itermLastToken.equals(jtermFirstToken) && list.get(i).isAdjacentTo(list.get(j)))
					{
						String jTermOriginal = list.get(j).originalTerm;

						// merge i and j terms
						String jTermSuffix = "";
						while (stj.hasMoreTokens())
						{
							if (jTermSuffix.equals(""))
								jTermSuffix = stj.nextToken();
							else
								jTermSuffix += " " + stj.nextToken();
						}

						// compute new term canonical by combining original i and j terms, but leaving out the overlapping part. so...
						// iterm must end with common part. so newTermOriginal = iterm - common token
						String newTerm = iTerm + " "  + jTermSuffix;
						String newTermOriginal = iTermOriginal.substring(0, iTermOriginal.length() - itermLastToken.length());
						newTermOriginal += " " + jTermOriginal;

						// update the original i term and mark j as redundant
						// this is dangerous. we are losing the index of the original term and destroying invariants in the indexes. @TODO @TOFIX
						list.get(i).term = newTerm;
						list.get(i).originalTerm = newTermOriginal;

						if (log.isDebugEnabled())
						{
							log.debug ("Combining " + i + ". \"" + iTerm + "\" -- and -- " + j + ". \"" + jTerm + "\" -- to form -- \"" + newTerm);
							log.debug ("Original terms \"" + iTermOriginal + "\" -- and -- \"" + jTermOriginal + "\" -- to form -- \"" + newTermOriginal);
						}
						redundant[j] = true;
						int itf = list.get(i).tf, jtf = list.get(j).tf;
						float iscore = list.get(i).score(), jscore = list.get(j).score();
						int newTf = (int) Math.max(itf, jtf);
						float newscore = Math.max(iscore, jscore);
						list.get(i).tf = newTf;
						// work backwards from the desired score to get the new idf
						list.get(i).idf = newscore/newTf;
						// TODO: Q: what about score for list.get(i) - should we make it lower of i and j scores ??

						changed = true;
					}
				}
			}
//			pw.close();
		}

		List<Posting> resultList = new ArrayList<Posting>();
		for (int i = 0; i < list.size(); i++)
			if (!redundant[i])
			{
				Posting p = list.get(i);
				resultList.add(p);
			}

		Collections.sort (resultList);

		return resultList;
	}

	private float[] getScoreIntervals(Collection<Document> mds, int nIntervals)
	{
		float f[] = new float[nIntervals];
		List<Posting> allTerms = new ArrayList<Posting>();
		for (Document d: mds)
		{
			MultiDoc md = (MultiDoc) d;
			List<Posting> terms = superdocToPostingsIndex.get(md);
			allTerms.addAll(terms);
		}
		log.info ("All terms across all multi-docs: size is " + allTerms.size());

//		log.info ("Scores in decreasing order: ");
//		for (Posting p: allTerms)
//			log.info (p.term + " : " + p.score());

		Collections.sort (allTerms);

		// effectiveAllTermsSize is the idx of the first ~ zero we see
		int effectiveAllTermsSize = 0;
		for (; effectiveAllTermsSize < allTerms.size() ; effectiveAllTermsSize++)
		{
			if (allTerms.get(effectiveAllTermsSize).score() < 0.0001)
				break;
		}

		if (effectiveAllTermsSize == 0) // degenerate case, but better handle here, otherwise causes NPEs later
		{
			for (int i = 1; i <= nIntervals; i++)
				f[i-1] = i; // doesn't matter what this value is, there are no terms!
			return f;
		}

		log.info ("effective terms being considered: " + effectiveAllTermsSize);
		for (int i = 1; i <= nIntervals; i++)
		{
			int idx = (effectiveAllTermsSize*i -1)/nIntervals; // idx must always be < allTerms.size(), hence the -1
			f[i-1] = allTerms.get(idx).score();
			log.info ("score intervals[" + (i-1) + "] = " + f[i-1]);
		}

		return f;
	}

	private static boolean hasOnlyCommonDictWords(String s)
	{
		StringTokenizer st = new StringTokenizer(s);
		while (st.hasMoreTokens())
		{
			String t = st.nextToken();
			if (!commonDictWords.contains(t))
				return false;
		}
		return true;
	}

	/** prune the results so we don't have too much information from one document and don't see common or short words.
	 * drop terms pointing to documents that are already covered by higher ranged terms.
	 * terms is assumed to be sorted by score.
	 */
	private List<CardTerm> pruneResult(List<CardTerm> terms, int clusterNum)
	{
		List<CardTerm> result = new ArrayList<CardTerm>();
		Map<Document, Integer> docToCountMap = new LinkedHashMap<Document, Integer>();
		for (CardTerm tct: terms)
		{
			// drop terms of length 1
			if (tct.lookupTerm.length() < 2)
				continue;

			// drop common dict words
			if (hasOnlyCommonDictWords(tct.lookupTerm))
				continue;

			Collection<Document> docsForLookupTerm = this.docsForQuery(tct.lookupTerm, clusterNum);
			boolean selectTerm = false;

			// do any docs for this term have a count < MAX_TERMS_PER_DOC. if so selectTerm = true
			// if all docs for this term have a count > MAX_TERMS_PER_DOC, they are all well represented, so this term isn't adding much value
			// side effect: if no docs, then we drop the term
			for (Document d: docsForLookupTerm)
			{
				Integer I = docToCountMap.get(d);
				if (I == null || I < MAX_TERMS_PER_DOC)
					selectTerm = true;
			}

			if (!selectTerm)
			{
				log.debug("dropping term because it already is well represented in all its " + docsForLookupTerm.size() + " docs: " + tct.lookupTerm);
				continue;
			}

			result.add(tct);
			// bump the counts for the docs
			for (Document d: docsForLookupTerm)
			{
				Integer I = docToCountMap.get(d);
				if (I == null)
					docToCountMap.put (d, 1);
				else
					docToCountMap.put (d, I+1);
			}
		}

		return result;
	}

	// list of top terms in the result
	// tabooterms should be canonical (lower cased, multiple spaces converted to one, etc)
	private List<CardTerm> selectTopTerms (MultiDoc mdoc, int nTopTerms, int clusterNum, Set<String> tabooTerms) throws IOException, ClassNotFoundException
	{
		List<CardTerm> result = new ArrayList<CardTerm>();

		List<Posting> postings = superdocToPostingsIndex.get(mdoc);
		log.info("mdoc " + mdoc + " has " + postings.size() + " postings");
		Collections.sort (postings);
		if (log.isDebugEnabled())
		{
			log.debug (postings.size() + " terms:");
			for (Posting p: postings)
				log.debug ("p: " + p);
		}

		// retain only the top postings
		List<Posting> topPostings = new ArrayList<Posting>();
		int nSelected = 0;
		for (Posting p: postings)
		{
			if (tabooTerms != null && tabooTerms.contains(p.term))
				continue;
			nSelected++;
			if (nSelected >= nTopTerms)
				break;
			topPostings.add(p);
		}

		// create phrases
		if (MERGE_TERMS)
		{
			topPostings = mergeConsecutiveTerms(topPostings);
			Collections.sort (topPostings);
		}

		// create cleaned up tags for each of the top postings
		for (Posting p: topPostings)
		{
			// too expensive to compute # of docs the term is in, so remove it
		//	Set<Document> docsForThisTermThisCluster = docsWithPhrase(p.term, -1);
		//	if (docsForThisTermThisCluster != null)
		//		docsForThisTermThisCluster.retainAll(mdoc.docs);

			if (p.tf >= 1)
			{				
				String termToShow = MyTokenizer.cleanupDisplayTerm(p.originalTerm);
				if (termToShow.length() > 0)
					result.add(new CardTerm(1, termToShow,	p.term, (10.0f * p.tf * p.idf), p.tf, p.idf, -1)); // docsForThisTermThisCluster.size())); // first param (for size) is not used
			}
		}

		// prune tags to avoid over-representation and common words
		int nBeforePrune = result.size();
		result = pruneResult(result, clusterNum);
		int nPruned = nBeforePrune - result.size();

		log.info(result.size() + " terms (" + nPruned + " pruned)");
		return result;
	}

	private String cardStats(List<Card> clouds)
	{
		if (clouds == null)
			return "No top terms";

		StringBuilder sb = new StringBuilder();
		sb.append ("Top terms: " + clouds.size() + " superdocs, ");
		int nWords = 0, nTags = 0;
		Map<Integer, Integer> colorFreq = new LinkedHashMap<Integer, Integer>(); // count of how many times a color occurs
		int nMultipleColorTags = 0;

		// docs with at least one term pointing to them
		Set<Document> docsCoveredByColors = new LinkedHashSet<Document>();
		for (Card tc: clouds)
		{
			for (CardTerm tct: tc.terms)
			{
				// count # of words
				StringTokenizer st = new StringTokenizer(tct.lookupTerm);
				int count = st.countTokens();
				nWords += count;
				nTags++;

				int color = tct.bestColor();
				if (color != -1)
				{
					Set<Document> docsWithPhrase = docsForQuery(tct.lookupTerm, -1);
					docsCoveredByColors.addAll(docsWithPhrase);
				}

				if (tct.colorWeights != null && tct.colorWeights.size() > 1)
					nMultipleColorTags++;
				// update color freqs
				Integer I = colorFreq.get(color);
				if (I == null)
					colorFreq.put(color, 1);
				else
					colorFreq.put(color, I+1);
			}
		}
		sb.append (nTags + " phrases with " + nWords + " words\n");
		sb.append (nMultipleColorTags + " phrases with possibly multiple colors\n");
		sb.append (docsCoveredByColors.size() + " docs are pointed to by at least one term\n");
		sb.append ("Color histogram for selected terms:\n"); // idea: show this as a protovis graph
		for (int c = -1; c < 100; c++) // randomly picking 100, should go to max # colors. TOFIX
		{
			 Integer I = colorFreq.get(c);
			 if (I != null)
				 sb.append ("Color: " + c + " : " + I + "\n");
		}
		return sb.toString();
	}

	/** prints super doc index, debug only */
	public void printIndex(PrintStream out)
	{
		for (Map.Entry<Document, List<Posting>> entry: superdocToPostingsIndex.entrySet())
		{
			Document doc = entry.getKey();
			List<Posting> list = entry.getValue();
			out.print("Document " + doc.description + ": ");
			for (Posting p: list)
				out.print (p.term + "(" + 1000.0f * p.tf * p.idf + ") ");
			out.println();
		}
	}

	public String computeStats()
	{
		return computeStats(true);
	}

	public String computeStats(boolean blur)
	{
		int nTermIndexPostings = 0;
		for (String term: termToPostingsIndex.keySet())
		{
			nTermIndexPostings += termToPostingsIndex.get(term).size();
			if (term.indexOf(' ') >= 0)
				nBigrams++;
			else
				nUnigrams++;
		}

		int nDocIndexPostings = 0;
		for (Document d: superdocToPostingsIndex.keySet())
			nDocIndexPostings += superdocToPostingsIndex.get(d).size();
		int nTermSubDocIndexEntries = 0, nPackedTermSubDocIndexEntries = 0;
		
		if (termToSubDocIndex != null)
			for (String term: termToSubDocIndex.keySet())
				nTermSubDocIndexEntries += termToSubDocIndex.get(term).size();
		if (packedTermToSubDocIndex != null)
			for (String term: packedTermToSubDocIndex.keySet())
				nPackedTermSubDocIndexEntries += packedTermToSubDocIndex.get(term).size();
			
		String result = "Index options: " + io.toString(blur) + "\n" + currentJobDocsetSize + " post-filter docs and " +
				docs.size() + " multi-docs\n" +
			   clusterStats + "\n" +
//			   nTokens + " tokens \n" +
			   Util.commatize(Posting.nPostingsAllocated) + " postings, " + Util.commatize(processedTextLength/1024) + "K chars processed, " + nProcessedNames + " names\n" +
//			   nStopTokens + " stop words (" + stopTokensSeen.size() + " distinct) \n" +
//			   nDictTokens + " dictionary words (" + dictTokensSeen.size() + " distinct)\n" +
			   Util.commatize(nUnigrams) + " distinct unigrams and " + Util.commatize(nBigrams) + " distinct bigrams\n" +
			   Util.commatize(termToPostingsIndex.size()) + " entries in term index with " + Util.commatize(nTermIndexPostings) + " postings\n" +
			   Util.commatize(superdocToPostingsIndex.size()) + " entries in superdoc index with " + Util.commatize(nDocIndexPostings) + " postings\n" +
			   (termToSubDocIndex != null ?
				   Util.commatize(termToSubDocIndex.size()) + " entries in term->documents index pointing to " + Util.commatize(nTermSubDocIndexEntries) + " sub documents\n":"") +
			   (packedTermToSubDocIndex != null ?
				   Util.commatize(packedTermToSubDocIndex.size()) + " entries in packed term->documents index pointing to " + Util.commatize(nPackedTermSubDocIndexEntries) + " sub documents\n" : "") +
				   
			   Util.commatize(InternTable.getSizeInChars()) + " chars in " + Util.commatize(InternTable.getNEntries()) + " entries in intern table\n" +
			   Util.commatize(getLinks().size()) + " links\n" +
			   Util.getMemoryStats();

		result += "\nPhrase terms stats: " + cardStats(cards);
		return result;
	}

	long currentJobStartTimeMillis = 0L;
	public int currentJobDocsetSize, currentJobDocsProcessed, currentJobErrors;

	public void cancel()
	{
		cancel = true;
		log.warn ("Indexer cancelled!");
	}

	public boolean isCancelled() { return cancel; }

	public String getStatusMessage()
	{
		if (currentJobDocsetSize == 0)
			return JSONUtils.getStatusJSON("Starting indexer...");

		// compute how much time remains
		long elapsedTimeMillis = System.currentTimeMillis() - currentJobStartTimeMillis;
		long unprocessedTimeSeconds = -1;

		// compute unprocessed message
		if (currentJobDocsProcessed != 0)
		{
			long unprocessedTimeMillis = (currentJobDocsetSize - currentJobDocsProcessed) * elapsedTimeMillis/currentJobDocsProcessed;
			unprocessedTimeSeconds = unprocessedTimeMillis / 1000;

			/*
			 StringBuilder sb = new StringBuilder();

			Formatter formatter = new Formatter(sb);
			long hours = unprocessedTimeSeconds / 3600;
			long x = unprocessedTimeSeconds % 3600;
			long mins = x / 60;
			long secs = x % 60;
			if (hours > 0)
				formatter.format("%02d:", hours);

			formatter.format( "%02d:", mins);
			if (hours == 0 && mins == 0 && secs == 0)
				secs = 1; // its embarassing to show 00:00s, so always show 00:01 sec remaining

			formatter.format( "%02ds", secs);
			unprocessedMessage = sb.toString() + " remaining";
			unprocessedMessage = Util.approximateTimeLeft(unprocessedTimeSeconds);
			*/
		}

		int doneCount = currentJobDocsProcessed + currentJobErrors;
		String descriptor = (io.ignoreDocumentBody) ? "headers" : " messages";
		int pctComplete = (doneCount*100)/currentJobDocsetSize;
		String processedMessage = "";
		if (pctComplete < 100)
			processedMessage = "Indexing " + Util.commatize(currentJobDocsetSize) + " " + descriptor;
		else
		{
			processedMessage = "Creating summaries...";
			unprocessedTimeSeconds = -1L;
		}
		return JSONUtils.getStatusJSON(processedMessage, pctComplete, elapsedTimeMillis/1000, unprocessedTimeSeconds);
	}

	/** -1 => all clusters */
	public List<Document> getDocsInCluster(int clusterNum)
	{
		List<Document> result = new ArrayList<Document>();
		if (clusterNum < 0)
		{
			for (MultiDoc md: docClusters)
				result.addAll(md.docs);
		}
		else
		{
			MultiDoc clusterDocs = docClusters.get(clusterNum);
			result.addAll(clusterDocs.docs);
		}
		return result;
	}

	/** Core indexing method. parses a doc and returns a map of <canonical term>->postings.
	 * the returned postings have the tf field filled out (but not idf, obviously)
	 * postings maintain a positions array if maintainPositions is true
	 * performance critical. */
	private static Triple<Map<String, Posting>, Map<String, Posting>, List<String>> getTermMap(String documentText, Document doc, IndexOptions io, boolean maintainPositions, boolean do_NER, boolean doPlain, Map<String, Integer> locationCounts) throws IOException
	{
		if (do_NER)
			Util.ASSERT(!maintainPositions); // no point maintaining positions if we're doing NER because we don't want to combine NER terms

		if (log.isTraceEnabled())
			log.trace("Tokenizing document: " + doc + "\ntext: " + documentText);

		// termMap is term -> posting object
		Map<String, Posting> NERtermMap = new LinkedHashMap<String, Posting>();
		Map<String, Posting> plainTermMap = new LinkedHashMap<String, Posting>();
		// posMap is term-> list of positions for the term (positions are in terms of token position)
		Map<String, List<Integer>> posMap = new LinkedHashMap<String,List<Integer>>();
		List<String> names = new ArrayList<String>();
		MyTokenizer st;
		if (do_NER)
		{
			st = NER.parse(documentText, io.locationsOnly, io.orgsOnly, locationCounts);
			while (st.hasMoreTokens())
			{
				String term = st.nextToken();
				names.add(term);
				// could be a multiWordTerm
				String canonicalTerm = IndexUtils.canonicalizeMultiWordTerm(term, false); // we do canonicalization as usual because we need to eliminate multiple spaces... also we are going to canonicalize the lookup query, so we might as well do it here. // term.toLowerCase(); // just lowercase for NER terms, no stemming etc.

				Posting p = NERtermMap.get(canonicalTerm);
				if (p == null)
				{
		//			if (!isDict) // allocate unigram if not dictionary word
	//				{
						p = new Posting();
						p.term = InternTable.intern(canonicalTerm);
						p.originalTerm = InternTable.intern(term);
						p.tf = 0;

						if (log.isTraceEnabled())
							log.trace("New Token: " + p);
						NERtermMap.put(p.term, p);
						// posMap.put(p.term, new ArrayList<Integer>()); NER's are never going to be combined, so need of maintaining positions for them
	//				}
				}
				p.tf++;
			}
			NGRAM_LENGTH=1;
		}

		if (doPlain)
		{
			// now do the non-NER part. we always have to do this, even if we only report NERs in our cards because the user may want to do a plain text search
			st = new MyTokenizer(documentText);

			// key structures
			String[] tokens = new String[NGRAM_LENGTH]; // actual tokens
			int[] tokenStartIdx = new int[NGRAM_LENGTH]; // this will maintain absolute char pointer into the documentText for the starting of each token

			for (int i = 0; i < NGRAM_LENGTH; i++)
			{
				tokens[i] = "";
				tokenStartIdx[i] = -1;
			}

			int pos = 0; // pos is tokenPosition
			int tokensInCurrentSentence = 0;

			// join words are completely ignored for tokenization, but retained for final display to user.
			// position in sentence is tracked with st.getTokenStartPointer()
			while (st.hasMoreTokens())
			{
				// tokensInCurrentSentence will track how many non-join-word tokens we have so far in this sentence
				// will max out at NGRAM_LENGTH

				String token = st.nextNonJoinWordToken();
				// empty token means end of sentence or doc
				if ("".equals(token))
				{
					tokensInCurrentSentence = 0;
					continue;
				}

	//			System.out.println ("next non join word: " + token);

				if (tokensInCurrentSentence == NGRAM_LENGTH)
				{
					// shift over tokens
					for (int i = 1; i < NGRAM_LENGTH; i++)
					{
						tokens[i-1] = tokens[i];
						tokenStartIdx[i-1] = tokenStartIdx[i];
					}
				}
				else
					tokensInCurrentSentence++;

				tokens[tokensInCurrentSentence-1] = token;
				tokenStartIdx[tokensInCurrentSentence-1] = st.getTokenStartPointer();

				// go backwards by NGRAM_LENGTH
				// new terms are all window of tokens culminating in this token.
				String canonicalTerm = "";
				for (int i = 0; i < tokensInCurrentSentence; i++)
				{
					int startToken = tokensInCurrentSentence-1-i;
					int endToken = tokensInCurrentSentence-1;
					if (i > 0)
						canonicalTerm = " " + canonicalTerm;
					canonicalTerm = IndexUtils.canonicalizeTerm(tokens[startToken]) + canonicalTerm;
					boolean drop = drop(canonicalTerm);
					if (drop)
						continue;
					if (NERtermMap.containsKey(canonicalTerm))
						continue; // do not index any of the NER_terms

					String originalTerm = null;
					int originalTermStartIdx = tokenStartIdx[startToken];
					int originalTermEndIdx = tokenStartIdx[endToken] + tokens[endToken].length();
					originalTerm = documentText.substring(originalTermStartIdx, originalTermEndIdx);

					Posting p = plainTermMap.get(canonicalTerm);
					if (p == null)
					{
						p = new Posting();
						p.term = InternTable.intern(canonicalTerm);
						p.originalTerm = InternTable.intern(originalTerm);
						p.tf = 0;

						if (log.isTraceEnabled())
							log.trace("New Token: " + p);
						plainTermMap.put(p.term, p);
						posMap.put(p.term, new ArrayList<Integer>());
					}
					p.tf++;
					posMap.get(p.term).add(pos - i); // posmap tracks start of this term
				}
				pos++;
				if (st.prevTokenEndsSentence())
					tokensInCurrentSentence = 0;
			}

			// set the positions for all postings for this doc
			if (maintainPositions)
				for (Posting p: plainTermMap.values())
					p.setPositions(posMap.get(p.term));
		}

		posMap.clear();
		return new Triple<Map<String, Posting>, Map<String, Posting>, List<String>>(NERtermMap, plainTermMap, names);
	}

	/** we're done indexing, optimize for storage, and prepare for queries, if applicable */
	protected void packIndex()
	{
		log.info("packing index: " + Util.getMemoryStats());
		for (String term: termToSubDocIndex.keySet())
			packedTermToSubDocIndex.put (term, new IntSet(termToSubDocIndex.get(term)));
		termToSubDocIndex.clear(); // clear to save memory
		log.info("After packing index: " + Util.getMemoryStats());
		
		// intern intsets, so that common intsets share the same object
		Map<IntSet, IntSet> internedSetsMap = new HashMap<IntSet, IntSet>();
		for (String s: packedTermToSubDocIndex.keySet())
		{
			IntSet x = packedTermToSubDocIndex.get(s);
			IntSet internedSet = internedSetsMap.get(x);
			if (internedSet != null)
				packedTermToSubDocIndex.put(s,  internedSet); // replace x with the internedSet in packedTermToSubDocIndex
			else
				internedSetsMap.put(x, x);
		}
		packedTermToSubDocIndex = Collections.unmodifiableMap(packedTermToSubDocIndex);
		log.info("After interning packed index: " + Util.getMemoryStats());
	}

	/**. Note: term is canonical term here (i.e. posting term, used inside the posting and in the indexer maps.
	 * clusterNum = -1 => all clusters.
	 * internal because this is called after breaking up the term into bigrams.
	 * external clients should only use getDocsWithPhrase */
	private IntSet getDocsWithTermInternal(String term)
	{
		// first sanitize the term the same way the indexer would originally have
		//	String term1 = Util.stripPunctuation(term);  !! WRONG!! canonical terms are being used here...
		String term1 = IndexUtils.canonicalizeMultiWordTerm(term, true);
		IntSet docsWithTerm = packedTermToSubDocIndex.get(term1);
		return docsWithTerm;
	}

	private Set<Document> docIdxsToDocs(IntSet set)
	{
		Set<Document> result = new LinkedHashSet<Document>();
		for (Integer I : set.elements())
			result.add(subdocs.get(I));
		return result;
	}
	
	private IntSet docIdxsForDocsInCluster(int clusterNum)
	{
		List<Document> docsInCluster = getDocsInCluster(clusterNum);
		return docsToDocIdxs(docsInCluster);
	}
	
	private IntSet docsToDocIdxs(Collection<Document> docs)
	{
		Set<Integer> result = new LinkedHashSet<Integer>();
		for (Document d: docs)
			result.add(docToDocIdx.get(d));
		return new IntSet(result);
	}
	
	/** return docs that contain all given terms. the returned set can be freely modified. returns all docs in cluster if terms is null */
	protected Set<Document> getDocumentsWithAllTerms(Collection<String> terms, int clusterNum)
	{
		if (terms == null)
			return new LinkedHashSet<Document>(getDocsInCluster(clusterNum)); // gets all docs in this time cluster

		IntSet result = null;

		// first sanitize the term the same way the indexer would originally have
		for (String term: terms)
		{
			term = IndexUtils.canonicalizeMultiWordTerm(term, true);
			IntSet set = getDocsWithTermInternal(term);
			if (result == null)
				result = set;
			else
				result = result.intersect(set); // intersection

			if (result == null || result.size() == 0)
				break;
		}

		if (result == null || result.size() == 0)
			return new LinkedHashSet<Document>();

		// common case: result size is 0. getDocsInCluster etc only in the uncommon case
		if (result.size() > 0 && clusterNum != -1)
		{
			IntSet docIdxsInCluster = docIdxsForDocsInCluster(clusterNum);
			result = result.intersect(docIdxsInCluster);
		}

		// if no terms, lets return an empty set
		if (result == null)
			return new LinkedHashSet<Document>();
		return docIdxsToDocs(result);
	}

	public List<Integer> getSelectedDocsWithPhrase(List<Document> selectedDocs, String term) throws IOException, GeneralSecurityException
	{
		List<Integer> result = new ArrayList<Integer>();
		if (selectedDocs != null)
		{
			Set<Document> set = docsForQuery(term, -1);
			if (set != null)
			{
				for (int i = 0; i < selectedDocs.size(); i++)
					if (set.contains(selectedDocs.get(i)))
						result.add(i);
			}
		}

		log.info(result.size() + "/" + selectedDocs + " selected docs have term: [" + term + "]");
		return result;
	}

	public Set<edu.stanford.muse.index.Document> docsForQuery(String term, int cluster) 
	{
		return docsForQuery(term, cluster, 1);
	}	

	/* returns contents of all emails containing the given phrase from the given timecluster.
	 * or operators (|) are allowed
	 * if term is null, all docs in cluster.
	 * if cluster is -1, equivalent to all docs
	 * @throws GeneralSecurityException */
	public Set<Document> docsForQuery(String query, int clusterNum, int threshold)
	{
		if (Util.nullOrEmpty(query))
			return getDocumentsWithAllTerms(null, clusterNum);
	
		List<String> andTerms = null;
		query = query.toLowerCase();
		
		// splot the or terms and perform individual lookups
		List<String> orTerms = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(query, "|");
		while (st.hasMoreTokens())
		{
			String phrase = st.nextToken();
			phrase = phrase.trim();
			if (!Util.nullOrEmpty(phrase))
				orTerms.add(phrase);
		}
	
		Set<Document> resultSet = null;
		for (String phrase: orTerms)
		{
			// split the term because it may be more than tri-or-more-grams, while our indexers only have
			// 1 or 2-grams
	
			// termPairs = IndexUtils.splitIntoPairWords(phrase);
			// splitting into single words because our index currently contains only 1-grams
			andTerms = IndexUtils.splitIntoWords(phrase); 
	
			if (log.isDebugEnabled())
			{
				StringBuilder sb = new StringBuilder();
				sb.append ("Looking up terms: ");
				for (String s: andTerms)
					sb.append ("[" + s + "] ");
				log.debug (sb.toString());
			}
	
			// if term is null, andTerms remains null, getDocumentsWithAllTerms can handle that
	
			Set<Document> ds = getDocumentsWithAllTerms(andTerms, clusterNum);
			if (log.isDebugEnabled())
				log.debug(ds.size() + " doc(s) with phrase: [" + phrase + "]");
			if (resultSet == null)
				resultSet = ds;
			else
				resultSet.addAll(ds);
		}
	
		if (log.isDebugEnabled())
			log.debug(resultSet.size() + " doc(s) satisfy query: [" + query + "]");
	
		return resultSet;
	}

	/* returns contents of all emails containing the given phrase from the given timecluster.
	 * or operators (|) are allowed
	 * if term is null, all docs in cluster.
	 * if cluster is -1, equivalent to all docs
	 * @throws GeneralSecurityException */
	public Set<Document> docsWithPhraseThreshold(String query, int clusterNum, int threshold)
	{
		if (Util.nullOrEmpty(query))
			return getDocumentsWithAllTerms(null, clusterNum);
	
		query = query.toLowerCase();
		List<String> orTerms = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(query, "|");
		while (st.hasMoreTokens())
		{
			String phrase = st.nextToken();
			phrase = phrase.trim();
			if (!Util.nullOrEmpty(phrase))
				orTerms.add(phrase);
			orTerms.add(phrase);
		}
	
		Map<Integer, Integer> countMap = new LinkedHashMap<Integer, Integer>();
		for (String phrase: orTerms)
		{
			IntSet set = packedTermToSubDocIndex.get(phrase);
			if (set == null || set.isEmpty())
				continue;
	
			if (log.isDebugEnabled())
				log.debug(set.size() + " doc(s) with phrase: [" + phrase + "]");
			if (countMap == null)
			{
				countMap = new LinkedHashMap<Integer, Integer>();
				for (Integer d: set.elements())
					countMap.put(d, 1);
			}
			else
			{
				for (Integer d: set.elements())
				{
					if (countMap.get(d) == null)
						countMap.put(d, 1);
					else
						countMap.put(d, countMap.get(d) + 1);
				}
			}
		}
	
		Set<Document> resultSet = new LinkedHashSet<Document>();
		for (Integer d: countMap.keySet())
			if (countMap.get(d) >= threshold)
				resultSet.add(subdocs.get(d));
	
		if (log.isDebugEnabled())
			log.debug(resultSet.size() + " doc(s) satisfy query: [" + query + "]");
	
		return resultSet;
	}

	boolean isPhraseInMultipleSubDocs (String phrase, int clusterNum)
	{
		Set<Document> docList = docsForQuery(phrase, clusterNum);
		return (docList != null && docList.size() > 1);
	}

	public String getHTMLAnnotatedDocumentContents(String contents, Date d, String docId, Set<String> stemmedTermsToHighlight, Set<String> unstemmedTermsToHighlight) throws Exception
	{
		return Highlighter.getHTMLAnnotatedDocumentContents(contents, d, docId, stemmedTermsToHighlight, unstemmedTermsToHighlight, 
									null, importantTermsCanonical /* unstemmed because we are only using names */);
	}

	// remove any duplicate link URLs from the incoming LinkInfos
	private List<LinkInfo> removeDuplicateLinkInfos(List<LinkInfo> input)
	{
		List<LinkInfo> result = new ArrayList<LinkInfo>();
		Set<String> seenURLs = new LinkedHashSet<String>();
		for (LinkInfo li: input)
			if (!seenURLs.contains(li.link))
			{
				result.add(li);
				seenURLs.add(li.link);
			}
		return result;
	}

	public List<LinkInfo> getLinks()
	{
		return links;
	}

	/** quick method for extracting links only, without doing all the parsing and indexing - used by slant, etc. */
	public void extractLinks(Collection<Document> docs) throws IOException
	{
		try {
			for (Document d: docs)
			{
				if (cancel)
					break;

				String contents = "";
				if (!io.ignoreDocumentBody)
				{
					try {
						contents = d.getContents();
					} catch (Exception e) {
						log.warn ("REAL WARNING: Exception trying to read doc: " + e + "\nDoc is: " + d.getHeader() + "\nURL is: " + d.url);
					}
				}

				if (d instanceof EmailDocument)
				{
					// do preprocessing only for emails, not for other kinds of documents
					// int len = contents.length();
					List<LinkInfo> linksForThisDoc = new ArrayList<LinkInfo>();
					contents = preprocessDocument(d, contents, linksForThisDoc, io.includeQuotedMessages); // Util.getFileContents(inputPrefix + "." + ed.docNum);
					linksForThisDoc = removeDuplicateLinkInfos(linksForThisDoc);
					d.links = linksForThisDoc; // not necessarily needed, right ? should have been init'ed when we first read the contents of this Doc. but doesn't hurt.
					links.addAll(linksForThisDoc);
				}
			}
		} catch (Exception e) { Util.print_exception(e); }
	}

	private void markDataError(String s) 
	{
		log.warn ("DATA WARNING:" + s);
		if (dataErrors == null)
			dataErrors = new ArrayList<String>();
		dataErrors.add(s);
	}

	// splits collection by time and creates new aggregate (multi-)docs for each time cluster
	// indexes the docs in each cluster.
	// computes tf-idf
	// prefix is the global prefix for this document set (i.e. folder.cluster)
	// also maintains per-doc indexer (memory permitting)
	private void indexDocumentCollection(List<MultiDoc> mDocs, List<Document> allDocs) throws IOException
	{
		this.clear();
		currentJobStartTimeMillis = System.currentTimeMillis();
		currentJobDocsetSize = allDocs.size();
		currentJobDocsProcessed = currentJobErrors = 0;

		System.gc();
		String stat1 = "Memory status before indexing " + allDocs.size() + " documents: " + Util.getMemoryStats();
		log.info (stat1);
		memoryStats += stat1 + "\n";
		docClusters = mDocs;

		if (io.do_NER)
			NER.printAllTypes();

		computeClusterStats(mDocs);
		log.info ("Indexing " + allDocs.size() + " documents in " + docClusters.size() + " clusters");
		int clusterCount = -1;
		processedTextLength = 0;
		int docsIndexed = 0, multiDocsIndexed = 0;
		Posting.nPostingsAllocated = 0;
		docClusters = mDocs;

		try {
			for (MultiDoc md: docClusters)
			{
				if (cancel)
					break;

				clusterCount++;

	//			if (md.docs.size() == 0)
	//				continue;

	//			MultiDoc md = new MultiDoc(timeClusterCount, cluster.name);

				StringBuilder clusterFullText = new StringBuilder(); // will hold contents of entire time cluster, to dump to a file later
				List<String> clusterNames = new ArrayList<String>();
				log.info ("-----------------------------");
				log.info ("Indexing " + md.docs.size() + " documents in document cluster #" + clusterCount + ": " + md.description);
	//				if (md.docs.size() == 0)
	//					continue; // early out, don't compute stats etc which can be a little expensive

				for (Document d: md.docs)
				{
					if (cancel)
						break;

					String contents = "";
					if (!io.ignoreDocumentBody)
					{
						try {
							contents = d.getContents();
						} catch (Exception e) {
							markDataError ("Exception trying to read " + d + ": " + e);
						}
					}

					if (contents.length() > MAX_DOCUMENT_SIZE)
					{
						markDataError ("Document too long, size " + Util.commatize(contents.length()) + " bytes, dropping it. Begins with: " + d + Util.ellipsize(contents, 80));
						contents = "";
					}
					
					if (d instanceof EmailDocument)
					{
						// do preprocessing only for emails, not for other kinds of documents
						int len = contents.length();
						List<LinkInfo> linksForThisDoc = new ArrayList<LinkInfo>();
						contents = preprocessDocument(d, contents, linksForThisDoc, io.includeQuotedMessages); // Util.getFileContents(inputPrefix + "." + ed.docNum);
						linksForThisDoc = removeDuplicateLinkInfos(linksForThisDoc);
						d.links = linksForThisDoc; // not necessarily needed, right ? should have been init'ed when we first read the contents of this Doc. but doesn't hurt.
						links.addAll(linksForThisDoc);
						log.debug ("preprocessed documents: removed " + (len - contents.length()) + " chars");
						len = contents.length();
						contents = Util.cleanupEmailMessage(contents);
						log.debug ("sanitized message: removed " + (len - contents.length()) + " chars");
					}

					String subject = d.getSubjectWithoutTitle();
					subject = EmailUtils.cleanupSubjectLine(subject);
					// add '.' between and after subject to avoid merging subject+subject or subject+contents (or prev. message + this subject)

					String fullText = " . \n";
					for (int i = 0; i < io.subjectWeight; i++)
						fullText += subject + " . \n";
					fullText += contents;

					List<String> docNames = indexSubdoc(fullText, d);
					if (docNames != null)
						clusterNames.addAll(docNames);
					// don't bother to maintain clusterfulltext if we're only going to do names
					if (io.do_allText)
					{
						clusterFullText.append(fullText);
						clusterFullText.append(" . ");
					}
					docsIndexed++;
					currentJobDocsProcessed++;
					processedTextLength += contents.length() + subject.length();
					nProcessedNames += docNames.size();
				} // end cluster

				// now index the whole cluster
				if (io.do_NER)
				{
					this.indexSuperDocNames(clusterNames, md);
				}
				else
				{ 
					// not really used... may not work
					String clusterText = clusterFullText.toString();
					this.indexSuperDoc (clusterText, md);
				}
				
				if (io.incrementalTFIDF)
				{
					this.computeTFIDFForOneDoc(md);
					// possible memory opt: remove low-ranked terms from superDocIndex
				}

				log.info ("Finished with multi doc " + md + " total #postings = " + superdocToPostingsIndex.get(md).size());
				if (md.docs.size() > 0)
					log.info ("Current stats:" + computeStats());

				multiDocsIndexed++;
				//			IndexUtils.dumpDocument(clusterPrefix, clusterText); // i don't think we need to do this except for debugging
				System.out.print("."); // goes to console, that's ok...

				if (md.docs.size() > 0)
				{
					String stat2 = ("Memory status after indexing " + docsIndexed + " of " + allDocs.size() + " documents in "
							+ multiDocsIndexed + " (non-zero) multi-docs, total text length " + processedTextLength + " chars, " + nProcessedNames + " names. " + Util.getMemoryStats());
					log.info (stat2);
					memoryStats += stat2 + "\n";
				}
			}
		} catch (OutOfMemoryError oome) {
			String s = "REAL WARNING! SEVERE WARNING! Out of memory during indexing. Please retry with more memory!" + oome;
			s += "\n" + getMemoryStatsAndReleaseMemory();
			log.error (s);
			memoryStats += s;

			// option: heroically soldier on and try to work with partial results
		}

		// imp: do this at the end to save memory. doesn't save memory during indexing but saves mem later, when the index is being used.
		// esp. important for lens.
		NER.release_classifier(); // release memory for classifier
		log.info ("Memory status after releasing classifier: " + Util.getMemoryStats());
		packIndex();
		if (!cancel)
		{
			// if we weren't computing idf's per cluster, do so now
			if (!io.incrementalTFIDF)
				this.computeTFIDF();	
		}
		return;
	}

	// indexes terms and returns a set of cards for each cluster
	// prefix is the global prefix for this document set (i.e. folder.cluster)
	void processDocumentCollection(List<MultiDoc> mDocs, List<Document> docs) throws IOException, GeneralSecurityException, ClassNotFoundException
	{
		log.info ("Processing " + docs.size() + " documents");
		try {
			indexDocumentCollection(mDocs, docs);
		} catch (OutOfMemoryError oome) {
			log.error ("Sorry, out of memory, results may be incomplete!");
			clear();
			// cl.docs = null; // wipe it
			// cl.docs = null;
		}		
	}

	public void computeCards(int nTopTerms) throws IOException, ClassNotFoundException
	{
		computeCards(nTopTerms, null);
	}
	
	/** returns <result, unigram results> pair */
	public void computeCards(int nTopTerms, Set<String> tabooTerms) throws IOException, ClassNotFoundException
	{
		int clusterNum = 0;
		List<Card> result = new ArrayList<Card>();

		for (Document doc: superdocToPostingsIndex.keySet())
		{
			MultiDoc mdoc = (MultiDoc) doc;
			List<CardTerm> cloudTerms = selectTopTerms(mdoc, nTopTerms, clusterNum, tabooTerms);

			// enter all the tags into importantTerms
			for (CardTerm tct: cloudTerms)
			{
				importantTermsCanonical.add(tct.lookupTerm);
				importantTermsPrefixes.add(tct.lookupTerm);
			}

			log.info (importantTermsCanonical.size() + " unique important terms so far");

			String description = mdoc.description;
			log.info (mdoc.description + " - " + mdoc.docs.size() + " documents, " + superdocToPostingsIndex.size() + " postings");
			result.add(new Card(description, cloudTerms, mdoc.docs.size()));
			clusterNum++;
		}

		cards = result;
	}

	public Pair<List<Card>, List<Card>> removeTabooTermsFromResults(Set<String> tabooTerms)
	{
		List<Card> newCards = new ArrayList<Card>();

		for (Card c: cards)
		{
			List<CardTerm> newTerms = new ArrayList<CardTerm>();
			outer:
			for (CardTerm ct: c.terms)
			{
				for (String tabooTerm: tabooTerms)
					if (tabooTerm.contains(ct.lookupTerm))
						continue outer;
				newTerms.add(ct);
			}
			Card newTC = new Card(c.description, newTerms, c.nMessages);
			newCards.add(newTC);
		}

		return new Pair<List<Card>, List<Card>>(newCards, null);
	}
	
/*
	public Map<String, List<String>> getSentiments(Document d)
	{
		Map<String, List<String>> emotionWordsMap = new LinkedHashMap<String, List<String>>();
		for (String s[]: Sentiments.emotionsData)
		{
			String emotion = s[0];
			String emotionWords = s[1];
			StringTokenizer st = new StringTokenizer(emotionWords, "|");
			while (st.hasMoreTokens())
			{
				String word = st.nextToken();
				IntSet docIdxs = packedTermToSubDocIndex.get(word);
				Set<Document> docs = docIdxsToDocs(docIdxs);
				if (!Util.nullOrEmpty(docs) && docs.contains(d))
				{
					List<String> list = emotionWordsMap.get(emotion);
					if (list == null)
					{
						list = new ArrayList<String>();
						emotionWordsMap.put(emotion, list);
					}
					list.add(word);
				}
			}
		}

		emotionWordsMap = Util.sortMapByListSize(emotionWordsMap);
		return emotionWordsMap;
	}
	*/
	public Set<String> allLanguagesInDocs(Collection<Document> docs) 
	{
		Set<String> result = new LinkedHashSet<String>();
		for (Document d: docs)
			if (d.languages != null)
				result.addAll(d.languages);
		if (result.size() == 0)
			result.add("english");
		return result;
	}
	
	/** DEBUG ONLY (destructive) tries to print relative memory usage for the various indexes, 
	 * to get some sort of memory profile */
	String getMemoryStatsAndReleaseMemory() throws IOException
	{
		StringBuilder sb = new StringBuilder();
		sb.append ("At out of memory, total memory consumption: " + Util.getMemoryStats() + "\n");
		termToPostingsIndex = null;
		sb.append ("After nulling termIndex: " + Util.getMemoryStats() + "\n");
		superdocToPostingsIndex = null;
		sb.append ("After nulling docIndex: " + Util.getMemoryStats() + "\n");
//		termSubDocIndex = null;
//		sb.append ("After nulling term-subdocIndex: " + Util.getMemoryStats() + "\n");
		// InternTable.dump("/tmp/intern");
		sb.append ("Intern table stats : " + InternTable.getSizeInChars() + " chars, " + InternTable.getNEntries() + " entries");
		InternTable.clear();
		sb.append ("After nulling intern table: " + Util.getMemoryStats() + "\n");
		return sb.toString();
	}

	public String toFullString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append ("Dumping indexer:\n");
		for (String s: termToPostingsIndex.keySet())
			sb.append (s + " -> " + termToPostingsIndex.get(s) + "\n");
		return sb.toString();
	}

	public String toString()
	{
		return computeStats();
	}


}
