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
import edu.stanford.muse.email.CalendarUtil;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.util.*;

/** this is the class responsible for generating summaries (currently, in the form of cards). it is closely tied to the indexer.
 * mainly provides 2 mixtures: recomputeCards() which does TFIDF scoring and populateCards, which uses the scores already computed
 * to create cards.  if only the # of terms per card changes, populateCards can be used, since all the scores don't need to be recomputed.
 * nukeCards should be called if something changed (like a filter) which invalidates the existing cards.
 */
public class Summarizer implements java.io.Serializable {
    static Log log = LogFactory.getLog(Indexer.class);
    private static final long serialVersionUID = 1L;

    private Indexer indexer;
	public List<Card> cards;
	public Set<String> importantTermsCanonical = new LinkedHashSet<String>(); // this just stores all the terms in the current set of cards
	protected Map<String, Integer> termToSuperDocCountIndex = new LinkedHashMap<String, Integer>(); // term -> # of superdocs that contain the term (used for computing idf)
	protected Map<Document, List<Posting>> superdocToPostingsIndex = new LinkedHashMap<Document, List<Posting>>(); // (super) doc -> postings for that doc
	public static final int MAX_TERMS_PER_DOC = 4;
	public static final int DEFAULT_N_CARD_TERMS = 10;
	protected static final int TERM_FREQ_PER_SUPERDOC_THROTTLE = 10000; // if a doc has more than this # of the same term, it will be throttled to this number. setting to 10,000 so effectively not throttling
	
	public Summarizer(Indexer indexer) { this.indexer = indexer; }
	
	public void clear()
	{
		superdocToPostingsIndex.clear();
		termToSuperDocCountIndex.clear();
		importantTermsCanonical.clear();
	}
	
	private void computeIDFForTermsInOneDoc(MultiDoc d, int totalDocs)
	{
		List<Posting> dfs = superdocToPostingsIndex.get(d);
		if (dfs == null)
			return;

		for (Posting p: dfs)
		{
			float docsWithThisTerm = termToSuperDocCountIndex.get(p.term);

			float idfForThisTerm = (float) Math.log(totalDocs/docsWithThisTerm);
			if (totalDocs == docsWithThisTerm)
			{
				idfForThisTerm = 0.01f;
			}
			p.idf = idfForThisTerm;
		}
	}

	/** organize postings in superDocToPostings for d by score. 
	 * note that tf-idf score may change later as more docs are indexed, and idf score of terms changes... */
	protected void sortPostingsByTFIDFForOneDoc(edu.stanford.muse.index.Document d)
	{
		List<Posting> list = superdocToPostingsIndex.get(d);
		// note: list is sorted in place, and remains connected to superdocToPostingsIndex, so this is not a NOP
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

	/** compute tf-idf based scores for a single multi-doc */
	public synchronized void scoreNamesFromMultiDoc(edu.stanford.muse.index.MultiDoc mdoc) throws IOException, GeneralSecurityException, ClassNotFoundException
	{		
		// create a list of all the names in this mdoc first
		List<String> clusterNames = new ArrayList<String>();
		for (edu.stanford.muse.index.Document d: mdoc.docs)
		{
			String id = d.getUniqueId();
			clusterNames.addAll(indexer.getNamesForDocId(id, Indexer.QueryType.ORIGINAL));
		}
		
		// now create a posting map
		Map<String, Posting> termMap = new LinkedHashMap<String, Posting>();
		for (String term: clusterNames) 
		{
			// could be a multiWordTerm
			String canonicalTerm = DictUtils.canonicalizeMultiWordTerm(term, false); // we do canonicalization as usual because we need to eliminate multiple spaces... but no stemming for names
			
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
		superdocToPostingsIndex.put(mdoc, a);

		// for each term in this doc, update termIndex
		for (Posting p: termMap.values())
		{
			Integer I = termToSuperDocCountIndex.get(p.term);
			if (I == null)
				termToSuperDocCountIndex.put(p.term, 1);
			else
				termToSuperDocCountIndex.put(p.term, I+1);
		}
		termMap.clear();
	}

	/** prune the results so we don't have too much information from one document and don't see common or short words.
	 * drop terms pointing to documents that are already covered by higher ranked terms.
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
			if (DictUtils.hasOnlyCommonDictWords(tct.lookupTerm))
				continue;

			if (tct.lookupTerm.indexOf("@") >= 0) // we don't allow any embedded @ in terms - because often this is a long email address
				continue;

            Indexer.QueryOptions options = new Indexer.QueryOptions();
			options.setCluster(clusterNum);
            Collection<Document> docsForLookupTerm = (Collection) indexer.docsForQuery(tct.lookupTerm, options);
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

	/** list of nTopTerms highest scoring terms for the given mdoc. 
	 * note: we assume each list in the superDocToPostings map is already sorted.
	// tabooterms are dropped, they should be canonical (lower cased, multiple spaces converted to one, etc)
	 */
	private List<CardTerm> selectTopTerms (MultiDoc mdoc, int nTopTerms, int clusterNum, Set<String> tabooTerms) throws IOException, ClassNotFoundException
	{
		List<CardTerm> result = new ArrayList<CardTerm>();

		List<Posting> postings = superdocToPostingsIndex.get(mdoc);
		log.info("mdoc " + mdoc + " has " + postings.size() + " postings");
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

		// create cleaned up tags for each of the top postings
		for (Posting p: topPostings)
		{
			if (p.tf >= 1)
			{				
				String termToShow = MyTokenizer.cleanupDisplayTerm(p.originalTerm);
				if (termToShow.length() > 0)
					result.add(new CardTerm(1, termToShow,	p.term, (10.0f * p.tf * p.idf), p.tf, p.idf, -1)); // docsForThisTermThisCluster.size())); // first param (for size) is not used
			}
		}

		// prune tags to avoid over-representation and common words
		int nBeforePrune = result.size();
	//	result = pruneResult(result, clusterNum);
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
					Collection<Document> docsWithPhrase = indexer.docsForQuery(tct.lookupTerm, new Indexer.QueryOptions());
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
	
	public String computeStats(boolean blur)
	{		
		int nDocIndexPostings = 0;
		for (Document d: superdocToPostingsIndex.keySet())
			nDocIndexPostings += superdocToPostingsIndex.get(d).size();
					
		String result = Util.commatize(Posting.nPostingsAllocated) + " postings\n" +
			   Util.commatize(superdocToPostingsIndex.size()) + " entries in superdoc index with " + Util.commatize(nDocIndexPostings) + " postings\n";
		
		result += "\nPhrase terms stats: " + cardStats(cards);
		return result;
	}
	
	/** recompute top terms (n per month) based on the given docs 
	 * @throws ClassNotFoundException 
	 * @throws IOException 
	 * @throws GeneralSecurityException
     * */
	public synchronized List<Card> recomputeCards(Collection<edu.stanford.muse.index.Document> docs, int n, Set<String> tabooTerms) throws IOException, ClassNotFoundException, GeneralSecurityException
	{
		// reset state first
		clear();
		
		List<MultiDoc> docClusters = (List) IndexUtils.partitionDocsByInterval((List) docs, indexer.io.monthsNotYears);
		
		// bad hack... we set these docClusters in the indexer because we'll use them later from cards.jsp in the coloring... this should be simplified.
		indexer.docClusters = docClusters;
		
		// divide into multidocs
//		List<MultiDoc> docClusters = IndexUtils.partitionDocsByInterval((Collection) docs, true /* months, not years */);
		
		int mdocCountSoFar = 0;
		for (MultiDoc md: docClusters)
		{
			mdocCountSoFar++;
			scoreNamesFromMultiDoc(md);
			Util.ASSERT(superdocToPostingsIndex.containsKey(md));
			if (indexer.io.incrementalTFIDF)
			{
				// if incremental, we are computing IDF as we go along
				this.computeIDFForTermsInOneDoc(md, mdocCountSoFar);
				sortPostingsByTFIDFForOneDoc(md); // important that this is done now, later we'll assume the postings are already sorted
			}
			log.info ("Finished with multi doc " + md + " total #postings = " + superdocToPostingsIndex.get(md).size());
		}
		
		// if its not incremental TF-IDF, we'll have to compute all the IDFs now
		if (!indexer.io.incrementalTFIDF)
		{
			for (MultiDoc md: docClusters)
			{
				this.computeIDFForTermsInOneDoc(md, mdocCountSoFar);
				sortPostingsByTFIDFForOneDoc(md); // important that this is done now, later we'll assume the postings are already sorted
			}
		}
		populateCards (n, tabooTerms);
		return cards;
	}
	
	/** computes the given # of terms per session */
	public List<Card> recomputeCards(Collection<Document> docs, Set<String> ownNames, int nResults) throws Exception
	{
		return recomputeCards(docs, nResults, ownNames);
	}
	
	public List<Card> populateCards(int nTopTerms, AddressBook ab) throws IOException, ClassNotFoundException
	{
		return populateCards(nTopTerms, IndexUtils.readCanonicalOwnNames(ab));
	}
	
	/** populates cards (postings etc must already have been calculated) ... therefore much cheaper than recomputeCards */
	private List<Card> populateCards(int nTopTerms, Set<String> tabooTerms) throws IOException, ClassNotFoundException
	{
		int clusterNum = 0;
		List<Card> result = new ArrayList<Card>();
		importantTermsCanonical.clear();
		
		int startYear = 0, startMonth = 0;
		for (Document doc: superdocToPostingsIndex.keySet())
		{
			MultiDoc mdoc = (MultiDoc) doc;
			List<CardTerm> cloudTerms = selectTopTerms(mdoc, nTopTerms, clusterNum, tabooTerms);

			// enter all the tags into importantTerms
			for (CardTerm tct: cloudTerms)
				importantTermsCanonical.add(tct.lookupTerm);

			log.info (importantTermsCanonical.size() + " unique important terms so far");

			String description = mdoc.description;
			log.info (mdoc.description + " - " + mdoc.docs.size() + " documents, " + superdocToPostingsIndex.size() + " postings");
			
			// create a card now and add it to the result
			Card c = new Card(description, cloudTerms, mdoc.docs.size());			
			result.add(c);
			
			// try and get a representative month and year if possible for this mdoc
			if (mdoc.docs.size() > 0)
			{
				DatedDocument dd = (DatedDocument) mdoc.docs.iterator().next();
				if (dd.date != null)
				{
					Calendar cal = new GregorianCalendar();
					cal.setTime(dd.date);
					startYear = cal.get(Calendar.YEAR);
					startMonth = cal.get(Calendar.MONTH);
				}
			}
			c.setYearAndMonth(startYear, startMonth);
			int[] next = CalendarUtil.getNextMonth(startYear, startMonth);
			startYear = next[0]; startMonth = next[1]; // in case next card has no documents
			clusterNum++;
		}

		cards = result;
		return cards;
	}
	
	/** this is called when something changes and the prev. cards are no longer valid */
	public void nukeCards() { cards = null; }

}
