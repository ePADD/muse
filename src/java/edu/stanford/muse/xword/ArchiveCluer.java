package edu.stanford.muse.xword;

import edu.stanford.muse.email.Contact;
import edu.stanford.muse.exceptions.ReadContentsException;
import edu.stanford.muse.index.*;
import edu.stanford.muse.memory.MemoryStudy;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.LockObtainFailedException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

/** cluer that generates sentences from an archive */
public class ArchiveCluer extends Cluer {
	private static final long serialVersionUID = 1L;
	private static Log log = LogFactory.getLog(ArchiveCluer.class);

	private Crossword c;
	private Archive archive;
	private Set<String> filteredIds;
	private Map<String, Clue> wordToClueCache = new LinkedHashMap<String, Clue>();
	private static final int MIN_CLUE_LENGTH = 25; // absolute min. clue length
	private static final int MIN_PREFERRED_CLUE_LENGTH = 80;
	private static final int MAX_PREFERRED_CLUE_LENGTH = 140;

	public final static Set<String> goodSentiments = new LinkedHashSet<String>();
	static {
		goodSentiments.add("superlative");
		goodSentiments.add("congratulations");
		goodSentiments.add("wow");
		goodSentiments.add("confidential");
		goodSentiments.add("memories");
		goodSentiments.add("family");
		goodSentiments.add("life event");
		goodSentiments.add("religion");
		goodSentiments.add("festivals");
		goodSentiments.add("love");
		goodSentiments.add("vacations");
		goodSentiments.add("racy");
		goodSentiments.add("emergency");
		goodSentiments.add("grief");
		goodSentiments.add("anger");
	}

	// note: sentimentToDocs captures all docs in index, not just the filterDocIds
	// createClue will first filter 
	public Map<String, Collection<Document>> sentimentToDocs = new LinkedHashMap<>();

	// https://stacks.stanford.edu/file/druid:fm335ct1355/Dissertation_Schnoebelen_final_8-29-12-augmented.pdf page ~199
	private static String[] smileys = new String[]{":D", ":-D", ":)", "=D", "=]", "=)", "(:", ":')", ":]", ":-)", ";)", ";D", ";-)", ";P", "=P", ":P", ":-P", "=(", ":(", ":-(", ":'(", ":O"}; // don't have "XD".. could be noisy?. ideally should make sure they are not followed by a letter

	public ArchiveCluer(Crossword c, Archive archive, Set<String> filteredIds, Lexicon lex)
	{
		this.archive = archive;
		this.filteredIds = filteredIds;
		this.c = c;
		if (archive != null) {
			sentimentToDocs = archive.getSentimentMap(lex, true /* original content only */); // note this does not take filtered ids into account, createClue will use the filterIds first before looking up sentiments
			log.info ("sentiment count statistics (full archive, not filtered): ");
			for (String sentiment: sentimentToDocs.keySet())
				log.info (sentiment + " = " + sentimentToDocs.get(sentiment).size());
			Map<String, Integer> archiveSentimentCounts = new LinkedHashMap<String, Integer>();
			
			// store only the "good sentiments" in the archive's stats because we want the same stats per user
			for (String sentiment: goodSentiments)
			{
				Collection<Document> docs = sentimentToDocs.get(sentiment);
				int count = (docs == null) ? 0 : docs.size();
				archiveSentimentCounts.put(sentiment, count);
			}
			archive.stats.sentimentCounts = archiveSentimentCounts;
		}
	}

	/** returns clue for word, either from cache if available, or creates a new one if possible. returns null if no adequate clue is found */
	public Clue bestClueFor (String word, Set<String> sentencesUsedAsClues) throws CorruptIndexException, LockObtainFailedException, IOException, ParseException, GeneralSecurityException, ClassNotFoundException, ReadContentsException
	{
		Clue c = wordToClueCache.get(word);
		if (c != null)
			return c;

		String originalAnswer = this.c.getOriginalAnswer(word);

		if (archive != null)
			c = createClue(originalAnswer, sentencesUsedAsClues);

		if (c != null)
			wordToClueCache.put(word, c);
		return c;
	}

	/** returns <overall sentiment score, sentiment->0/1 score map> */
	private Pair<Float, Map<String, Integer>> scoreDocForSentiments(Document d)
	{
		float score = 1.0f;
		Map<String, Integer> map = new LinkedHashMap<String, Integer>();

		// map order and key presence is important. (so that CSV statistics are exactly parallel between different clues) 
		// initialize all entries to 0 (in the same order) and then update the ones that are actually present 
		for (String sentiment: goodSentiments)
				map.put(sentiment, 0);
		
		// scoring: big boost for goodSentiments, small boost for other sentiments
		for (String sentiment: sentimentToDocs.keySet())
		{
			Collection<Document> set = sentimentToDocs.get(sentiment);
			if (set != null && set.contains(d))
			{
				if (goodSentiments.contains(sentiment)) {
					score += 10.0; // increase by 10
					map.put(sentiment, 1);
				} else
					score += 2.0;
			}
		}
		
		return new Pair<Float, Map<String, Integer>>(score, map);
	}

	public Clue createClue(String answer, Set<String> tabooClues) throws CorruptIndexException, LockObtainFailedException, IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, ParseException
	{
		return createClue(answer, tabooClues, null);
	}
	/** create clue for the given answer.
    cannot pick clue from sentences that have taboo clues
    if filterDocIds is not null, we use only clues from docs with ids in filterDocIds.
	 * @throws ParseException 
	 */
	public Clue createClue(String answer, Set<String> tabooClues, NERModel nerModel) throws CorruptIndexException, LockObtainFailedException, IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, ParseException
	{
		// first canonicalize w
		answer = answer.toLowerCase();
		Collection<Document> docs = archive.docsForQuery("\"" + answer + "\"", Indexer.QueryType.ORIGINAL); // look up inside double quotes since answer may contain blanks
	    boolean answerPartOfAnyAddressBookName = archive.addressBook.isStringPartOfAnyAddressBookName(answer);

	    // find all messages with the answer in them (original content only)
	    List<Document> docsWithAnswer = new ArrayList<>();
		for (Document doc: docs)
			docsWithAnswer.add(doc);

		// note: docsWithAnswer is not sorted by time
		
		int nDocsWithAnswer = docsWithAnswer.size();

		// compute #threads with answer
		Set<Long> set = new LinkedHashSet<Long>();
		for (Document doc: docsWithAnswer)
		{
            EmailDocument ed = (EmailDocument)doc;
			if (ed.threadID != 0)
				set.add(ed.threadID);
		}
		int nThreadsWithAnswer = set.size();
		
		// lucenelookupDocs returns emails sorted by time
		// permute the candidates randomly otherwise lots of clues might be from nearby docs, e.g. from the same time range in the corpus
		/*
	for (int i = 0; i < candidates.size()-1; i++)
	{
		int idx = i + random.nextInt(candidates.size() - i);
		// interchange idx and i
		EmailDocument tmp = candidates.get(idx);
		candidates.set(idx, candidates.get(i));
		candidates.set(i, tmp);
	}
		 */

		// now find the clue with the best score
		Clue bestClue = null; 
		Document bestClueDoc = null;
		float bestScore = -100.0f;
		boolean useFirstLetterClue = (archive.getAllDocs().size() == 1); // if only 1 doc, must be a dummy, use first letter as clue instead

		// we want a good pool of docs to select clues from. at the same time, we don't want to look at ALL docs with the term because the
		// number could be very large. so we look at at least N_DOCS_TO_CHECK_AT_LEAST and return the best from it.
		int N_DOCS_TO_CHECK_FOR_CLUES = 50;
		int docCount = 0;
		int nValidClueCandidates = 0;
		for (Document doc: docsWithAnswer)
		{
            EmailDocument ed = (EmailDocument)doc;
			try {

				if (filteredIds != null && !filteredIds.contains(ed.getUniqueId()))
					continue; // not in filter docs, so can't use this doc
	
				// ok, we've looked at the min. # of docs, return the best clue if there is one
				if (docCount >= N_DOCS_TO_CHECK_FOR_CLUES)
					if (bestClue != null)
						break;
				docCount++;
	
				Pair<Float, Map<String, Integer>> p = scoreDocForSentiments(ed);
				float docSentimentScore = p.getFirst();
				Map<String, Integer> sentimentMap = p.getSecond();
				
				// check the # of names in this doc, drastic penalty in doc score if 
				// 1) there are a lot of names in it (typically a complete news article)
				// 2) the answer does not occur as a name within it
				List<String> namesInMessage = archive.getNamesForDocId(ed.getUniqueId(), Indexer.QueryType.ORIGINAL);
				if (namesInMessage.size() > 10)
					docSentimentScore /= namesInMessage.size(); 
	
				Set<String> namesSet = new LinkedHashSet<String>();
				for (String name: namesInMessage)
					namesSet.add( Util.canonicalizeSpaces(name.toLowerCase()));
	
				if (!namesSet.contains(Util.canonicalizeSpaces(answer.toLowerCase())))
					docSentimentScore /= 10;
	
				// check if the answer is part of the name of a person on this message
				boolean answerPartOfRecipientName = false;
				for (String addr: ed.getAllAddrs())
				{
					// get all the names for this contact and check if the answer is part of them
					Contact c = archive.addressBook.lookupByEmail(addr);
					if (c.checkIfStringPartOfAnyName(answer)) 
					{				
							answerPartOfRecipientName = true;
							break;
					}
				}
				
				int nRecipients = ed.getAllAddrs().size()-1; // -1 to un-count the sender
				int subjectLength = (ed.description != null ? ed.description.length() : 0);
				// now process sentences within the doc			
				String contents = archive.getContents(ed, true);
				String cleanedContents = EmailUtils.cleanupEmailMessage(contents);
				SentenceTokenizer st = new SentenceTokenizer(cleanedContents);
				int sentenceNum = 0;

				while (st.hasMoreSentences())
				{				
					sentenceNum++;
					String originalSentence = st.nextSentence(true); // include trailing delim
					originalSentence = originalSentence.replaceAll("\r", "\n"); // weird DOS type stuff has \r's sometimes

					// 1 newline is normal, but 2 or more is bad...it tends to be a signature or list... or code.... doesn't make for a good clue.
					float linesBoost = 1.0f;
					int nLines = new StringTokenizer(originalSentence, "\n").countTokens();
					if (nLines > 2)
						linesBoost = (float) -Math.pow(5.0f, nLines-1); // steep penalty if it spans more than 2 lines

					originalSentence = originalSentence.trim().replaceAll("\n", " ");
					originalSentence = Util.canonicalizeSpaces(originalSentence);
					String lowerCaseSentence = originalSentence.toLowerCase();

					if (!Util.occursOnlyAsWholeWord(lowerCaseSentence, answer))
						continue;

					int MAX_CLUE_CHARS = 200;

					if (originalSentence.length() >= MAX_CLUE_CHARS)
						continue;

					// check if #letter chars in sentence = #letters chars in word.
					// we can't just check length of sentence == length of word
					// because sometimes we get a sentence like <X + punctuation> as a clue for X and we want to eliminate such sentences
					if (Util.nLetterChars(originalSentence) == Util.nLetterChars(answer))
						continue; 

					if (!sentenceIsValidAsClue(lowerCaseSentence))
						continue;
					if (tabooClues != null && (tabooClues.contains(lowerCaseSentence) || tabooClues.contains(originalSentence)))
						continue;
					
					originalSentence = originalSentence.replaceAll("\n", " ");

					// now score the sentence
					nValidClueCandidates++;
					String unblankedLowerCaseSentence = lowerCaseSentence;
					String blankedSentence = Util.blankout(originalSentence, answer);
					String hint = ed.toStringAsHint();
					String blankedHint = (useFirstLetterClue) ? ("Starts with " + Character.toUpperCase(answer.charAt(0))) : Util.blankout(hint, answer);
					String url = "browse?docId=" + ed.getUniqueId() + "&nofilter=";
					String messageContentOriginal = archive.getContents(ed, false /* full message */);
					String ellipsisizedMessage = Util.ellipsize(messageContentOriginal, 3000);
					Clue clue = new Clue(blankedSentence, originalSentence, unblankedLowerCaseSentence, blankedHint, url, ellipsisizedMessage, ed);
					
					Set<String> tabooNamesSet = archive.addressBook.getOwnNamesSet(); 
					float clueScore = scoreClue(clue, answer, tabooNamesSet, nerModel);
					clueScore += linesBoost;

					// a small boost for sentences earlier in the message -- other things being equal, they are likely to be more important
					float sentenceNumBoost = -0.2f*sentenceNum;
					// log.info ("raw clue score for " + answer + " is " + clueScore + " sentence num boost = " + sentenceNumBoost);
					// add in the score for message factors
					clueScore += docSentimentScore;
					clueScore += sentenceNumBoost;

					clue.clueStats.finalScore = clueScore;
					log.info ("clue score for " + answer + " is " + clueScore + " (docscore: " + docSentimentScore + ") for sentence# " + sentenceNum + " in doc #" + docCount + ":" + clue + " lines boost = " + linesBoost);

					if (clueScore > bestScore)
					{
						log.info ("Prev. clue: New high!");
						bestClue = clue;
						bestClueDoc = ed;
						bestScore = clueScore;
					} 
					
					// the below should ideally be updated only inside the above if
					
						// update all the stats now
						clue.clueStats.linesBoost = linesBoost;
						clue.clueStats.docSentimentScore = docSentimentScore;
						clue.clueStats.docSentiments = sentimentMap;
						clue.clueStats.sentenceNumBoost = sentenceNumBoost;
						
						clue.clueStats.sentenceNumInMessage = sentenceNum;
						
						// copy stats related to the message
						clue.clueStats.namesInMessage = namesInMessage.size();
						clue.clueStats.charsInMessage = messageContentOriginal.length();
						clue.clueStats.answerPartOfRecipientName = answerPartOfRecipientName;
						clue.clueStats.nRecipients = nRecipients; 
						clue.clueStats.subjectLength = subjectLength;

						List<DatedDocument> messagesInThread = (List) archive.docsWithThreadId(ed.threadID);
						clue.clueStats.nMessagesInThread = messagesInThread.size(); 
						if (messagesInThread.size() > 0)
						{
							Collections.sort(messagesInThread);
							clue.clueStats.daysSpannedByThread = (int) ((messagesInThread.get(messagesInThread.size()-1).date.getTime() - messagesInThread.get(0).date.getTime())/EmailUtils.MILLIS_PER_DAY);
							
							// 2 ways of trying to guess if this thread was initiated by the user. 
							// 1. check if the first message in thread has original content == full content, i.e. no quoted parts).
							// 2. check if subject line starts with Re:
							// Document firstMessageInThread = messagesInThread.get(0);
							// String firstMessageOriginalContent = archive.getContents(firstMessageInThread, true);
							// String firstMessageFullContent = archive.getContents(firstMessageInThread, false);
//							clue.clueStats.threadInitiatedByUser = firstMessageOriginalContent.equals(firstMessageFullContent);
							clue.clueStats.threadInitiatedByUser = !(ed.description != null && ed.description.trim().toLowerCase().startsWith("re:")); // 
						}
						// Pair<String, String> p1 = Util.fieldsToCSV(clue.clueStats, false);
					//	JSPHelper.log.info ("CLUELOG-1 " + p1.getFirst() + "answer,clue");
					//	JSPHelper.log.info ("CLUELOG-2 " + p1.getSecond() + "," + answer + "," + clue.clue.replaceAll(",", " "));
//					}
				}
				
				// update sentencesInMessage at the end of the message, because we know it only at the end of the message
				if (bestClueDoc == ed)
					bestClue.clueStats.sentencesInMessage = sentenceNum;
			} catch (Exception e) { Util.print_exception("Error trying to generate clues", e, log); }
			
			docCount++;
		}
		
		if (bestClue != null) 
		{
			bestClue.clueStats.nValidClueCandidates = nValidClueCandidates; // # of clues considered seriously, i.e. given a score

			int daysSinceFirstMention = 0, daysSinceLastMention = 0;
			if (docsWithAnswer.size() > 0)
			{
				bestClue.clueStats.answerPartOfAnyAddressBookName = answerPartOfAnyAddressBookName;
				bestClue.clueStats.nMessagesWithAnswer = nDocsWithAnswer;
				bestClue.clueStats.nThreadsWithAnswer = nThreadsWithAnswer;

				Collections.sort(docsWithAnswer);
				EmailDocument firstMentionDoc = (EmailDocument)docsWithAnswer.get(0);
				EmailDocument lastMentionDoc = (EmailDocument)docsWithAnswer.get(docsWithAnswer.size()-1);
				daysSinceFirstMention = (int) ((new Date().getTime() - firstMentionDoc.date.getTime())/EmailUtils.MILLIS_PER_DAY);
				daysSinceLastMention = (int) ((new Date().getTime() - lastMentionDoc.date.getTime())/EmailUtils.MILLIS_PER_DAY);
				bestClue.clueStats.daysSinceFirstMention = daysSinceFirstMention;
				bestClue.clueStats.daysSinceLastMention = daysSinceLastMention;
				
				// compute mention frequency for each of the last 12 months
				List<Date> dates = new ArrayList<Date>();
				for (Document doc: docs) {
                    EmailDocument ed = (EmailDocument)doc;
                    dates.add(ed.date);
                }
				 List<Integer> hist = EmailUtils.histogram(dates, new Date().getTime(), MemoryStudy.INTERVAL_MILLIS);

				 // copy over histogram to dates, up to the first 12 intervals (hist might possibly have more?)
				 int[] histogram = new int[MemoryStudy.N_INTERVALS]; // 12 intervals
				 int count = 0;
				 for (Integer I : hist) {
					 histogram[count] = I;
					 if (++count >= histogram.length)
						 break;
				 }
				 bestClue.clueStats.histogramOfAnswerOccurrence = histogram;
			}
		}
		return bestClue;
	}

	/** this should be called when a word is commited into the grid. we will invalidate clue $ if this clue sentence has been mapped to another word */
	@Override
	public void commitWord(Word W)
	{
		for (Iterator<Map.Entry<String, Clue>> it = wordToClueCache.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<String, Clue> next = it.next();
			String cacheWord = next.getKey();
			Clue cacheClue = next.getValue();
			if (!W.word.equals(cacheWord) && W.clue.getFullSentence().equals(cacheClue.getFullSentence()))
				it.remove(); // delete this cacheClue from the $
		}
	}
	
	// returns a score for the given string as a clue. this does not take into account the doc s is a part of. 
	// note s is not lower-cased
	public static float scoreClue(Clue clue, String answer, Set<String> tabooNames, NERModel nerModel) throws ClassCastException, IOException, ClassNotFoundException
	{
		String canonicalizedanswer = (Util.canonicalizeSpaces(answer)).toLowerCase();
		String s = clue.getFullSentenceOriginal();
		// first look for signs of bad clues and return a low score
		String sOrig = s;
		s = s.toLowerCase();

		// other possible things to factor in, in the future... 
		// does it start with a capital letter?
		// does it have number tokens in it?
		
		// prefer exclamations, highly memorable
		float exclamationScore = (s.indexOf("!") >= 0) ? 7.0f : 0.0f;

		// good if it has emoticons
		int nSmileys = 0;
		for (String smiley: smileys)
			if (s.indexOf(smiley) >= 0) {
				nSmileys++;
			}
		float smileyScore = 7.0f * nSmileys;
		
		// clue gets points the closer it is to the preferred clue length
		// lengthBoost is really a penalty (its -ve)
		float lengthBoost = 0.0f;
		if (s.length() < MIN_PREFERRED_CLUE_LENGTH)
			lengthBoost = -100.0f * Math.abs((MIN_PREFERRED_CLUE_LENGTH - (float) s.length())/MIN_PREFERRED_CLUE_LENGTH);
		else if (s.length() > MAX_PREFERRED_CLUE_LENGTH)
			lengthBoost = -10.0f * Math.abs( (((float) s.length()) - MAX_PREFERRED_CLUE_LENGTH)/MAX_PREFERRED_CLUE_LENGTH );
		else
			lengthBoost = 0.0f;

		// these are things that make the clue less interesting, but we'll use them if we can't find anything else!
		// see if the sentence mainly consists of the word
		//		int sChars = Util.nLetterChars(s);
		//		int wChars = Util.nLetterChars(w);
		//		if (sChars < wChars + 20)
		//			score = -10.0f;
		// how many names does it have? more names is good.

		List<String> names = new ArrayList<>();
		if (nerModel != null) {
			Pair<Map<Short, List<String>>, List<Triple<String, Integer, Integer>>> mapAndOffsets = nerModel.find(sOrig);
			Map<Short, List<String>> map = mapAndOffsets.first;
            //TODO: remove these logs
            log.info("Found: "+mapAndOffsets.getSecond().size()+" names in sentences: "+sOrig);
			for (short x: map.keySet()) {
                log.info(x+":"+map.get(x));
                names.addAll(map.get(x));
            }
		}

//		List<Pair<String, Float>> names = edu.stanford.muse.ner.NER.namesFromText(sOrig); // do NER on the original, not after lower-casing
		float namesScore = 2 * names.size();

		// compute total score, but penalize for the following
		float score = 1.0f + namesScore + exclamationScore + smileyScore + lengthBoost;
		if (s.startsWith("this") || s.startsWith("that") || s.startsWith("however")) {
			clue.clueStats.containsNonSpecificWords = true;
			score -= 10.0f; // non-specific, depends on context
		}

		boolean found = false;
//		for (Pair<String, Float> namePair : names){ //check that NER detects same name in text as the name of the answer (eg. removes instances where NER returns "San Jose" from clue, but answer is "San")
//			String name = (Util.canonicalizeSpaces(namePair.getFirst())).toLowerCase();
		for (String name : names) { //check that NER detects same name in text as the name of the answer (eg. removes instances where NER returns "San Jose" from clue, but answer is "San")
			if (name.equals(canonicalizedanswer)) {
				found = true;
				break;
			}
		}
		if (!found) {
			score -= 20.0f;
			clue.clueStats.nameNotInClue = true;
		}		
		for (String badName: tabooNames) { //drop own name, as well as other terms that may be overused in the xword/memorystudy.
			if (s.contains(badName)) {
				score -= 20.0f;
				clue.clueStats.containsBadName = true;
				break;
			}
		}
		
		clue.clueStats.namesInClue = names.size();
		clue.clueStats.nSmileys = nSmileys;
		clue.clueStats.clueLength = clue.getFullSentenceOriginal().length();
		
		clue.clueStats.namesScore = namesScore;
		clue.clueStats.exclamationScore = exclamationScore;
		clue.clueStats.smileyScore = smileyScore;
		clue.clueStats.lengthBoost = lengthBoost;
		
		//	if (log.isDebugEnabled())
		log.info ("score = " + score + " namesScore = " + namesScore + " exclamationScore = " + exclamationScore + " smileyScore = " + smileyScore + " lengthBoost = " + lengthBoost);
		return score;
	}

	/** checks if sentence is ok as clue. returns true if yes, false otherwise */
	public static boolean sentenceIsValidAsClue(String lowerCaseSentence)
	{					
		if (lowerCaseSentence.indexOf("/") >= 0 || lowerCaseSentence.indexOf("|") >= 0 || lowerCaseSentence.indexOf(">") >= 0 || lowerCaseSentence.indexOf("---") >= 0 || lowerCaseSentence.indexOf("<") >= 0 || lowerCaseSentence.indexOf("___") >= 0) // get rid of things like http:// or >> or | quotes
			return false;
		if (lowerCaseSentence.charAt(0) < 'a' || lowerCaseSentence.charAt(0) > 'z')
			return false;
		if (lowerCaseSentence.startsWith("to")) // too generic
			return false;
		if (lowerCaseSentence.startsWith(")") || lowerCaseSentence.startsWith(","))
			return false;
		//protect against excessively large words and slightly large words with numbers in them
		List<String> wordsInSentence = Util.tokenize(lowerCaseSentence, " ");
		int MAX_LENGTH = 20;
		int MAX_LENGTH_W_NUMBER = 10;
		for (String word: wordsInSentence){	
			if (word.length() > MAX_LENGTH)
				return false;
			
			//protect against numbers in a large word.
			if (word.length() > MAX_LENGTH_W_NUMBER)
				for (char c: word.toCharArray()) 
					if (Character.isDigit(c))
						return false;
		}
		// unbalanced parens look ugly
		// unless they are in emoticons (so first nuke emoticons
		lowerCaseSentence = lowerCaseSentence.replaceAll("[:;][-][\\)\\(DP]", "");
		lowerCaseSentence = lowerCaseSentence.replaceAll("[:;][\\)\\(DP]", "");
		if (lowerCaseSentence.indexOf(")") >= 0 !=  lowerCaseSentence.indexOf("(") >= 0) 
			return false;

		if (lowerCaseSentence.indexOf("]") >= 0 !=  lowerCaseSentence.indexOf("[") >= 0) 
			return false;
		if (lowerCaseSentence.length() < MIN_CLUE_LENGTH)
			return false;					

		// get rid of the following templates of sentences... they tend to be very noisy
		if (lowerCaseSentence.indexOf("html") >= 0) // junk like html randomly shows up sometimes
			return false;
		if (lowerCaseSentence.indexOf("http") >= 0 || lowerCaseSentence.indexOf("=") >= 0) // also "=" because we see url params authKey=someLongHashCode
			return false;
		if (lowerCaseSentence.indexOf("@") >= 0)
			return false;
		if (lowerCaseSentence.startsWith("hello") || lowerCaseSentence.startsWith("hi ") || lowerCaseSentence.startsWith("regards") || lowerCaseSentence.startsWith("thanks,"))
			return false;					
		if (lowerCaseSentence.contains("subject:"))
			return false;					
		if (lowerCaseSentence.contains("vcalendar") || lowerCaseSentence.contains("vevent")) // fragments of calendar appts (.ics)
			return false;		
		if (lowerCaseSentence.indexOf("wrote:") >= 0)
			return false;		// lot of bad sentences like: "So-and-so wrote:..."
		// get rid of sentences like "On Mon, Nov 5, 2012 at 9:46 AM, _______, J."
		if (lowerCaseSentence.matches("^.*on (sun|mon|tue|wed|thu|fri|sat), (jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec).*"))
			return false;	
		int maxTokenLength = Util.maxTokenLength(lowerCaseSentence);
		if (maxTokenLength > 20) // some token is very long... suspicious... seems to be a jumble of letters, 
			return false;
		
		// find cases where a period is not really the end of a sentence. this can be if the sentence ends with a single-letter dot.
		// or vs. or mr. or mrs.
		if (lowerCaseSentence.length() > 2 && Character.isWhitespace(lowerCaseSentence.charAt(lowerCaseSentence.length()-2)))
			return false;
		if (lowerCaseSentence.endsWith(" vs") || lowerCaseSentence.endsWith(" mr") || lowerCaseSentence.endsWith(" mrs"))
			return false; // most likely not a properly formed sentence
		if (lowerCaseSentence.startsWith("edu")  || lowerCaseSentence.startsWith("com") || lowerCaseSentence.startsWith("net"))
			return false;  // most likely not a properly formed sentence
		
		if (lowerCaseSentence.length() > 2){
			char char0 = lowerCaseSentence.charAt(0), char1 = lowerCaseSentence.charAt(1), char2 = lowerCaseSentence.charAt(2);
			if (Character.isDigit(char0) && Character.isDigit(char1) && Character.isSpaceChar(char2)) // happens for time 12:30 or 12.30
				return false;
		}
		
		return true;
	}	
}
