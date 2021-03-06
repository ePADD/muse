package edu.stanford.muse.xword;

import edu.stanford.muse.email.Contact;
import edu.stanford.muse.exceptions.ReadContentsException;
import edu.stanford.muse.index.*;
import edu.stanford.muse.memory.MemoryStudy;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

/** cluer that generates sentences from an archive */
public class ArchiveCluer extends Cluer {
	private static final long serialVersionUID = 1L;
	private static Log log = LogFactory.getLog(ArchiveCluer.class);

	private Crossword c;
	private Archive archive;
    private NERModel nerModel;
	private Set<String> filteredIds;
	private Map<String, Clue> wordToClueCache = new LinkedHashMap<String, Clue>();
	protected static final int MIN_CLUE_LENGTH = 25; // absolute min. clue length
	protected static final int MIN_PREFERRED_CLUE_LENGTH = 80;
	protected static final int MAX_PREFERRED_CLUE_LENGTH = 140;
	protected static final int MIN_SENTENCE_LENGTH = 20; // less than this length is not considered a valid sentence at all because it could be just "Thanks" etc.

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
	protected static String[] smileys = new String[]{":D", ":-D", ":)", "=D", "=]", "=)", "(:", ":')", ":]", ":-)", ";)", ";D", ";-)", ";P", "=P", ":P", ":-P", "=(", ":(", ":-(", ":'(", ":O"}; // don't have "XD".. could be noisy?. ideally should make sure they are not followed by a letter

	public ArchiveCluer(Crossword c, Archive archive, NERModel nerModel, Set<String> filteredIds, Lexicon lex)
	{
		this.archive = archive;
        this.nerModel = nerModel;
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
	public Clue bestClueFor (String word, Set<String> sentencesUsedAsClues) throws IOException, ParseException, GeneralSecurityException, ClassNotFoundException, ReadContentsException
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
		
		return new Pair<>(score, map);
	}

    public Clue createClue(String answer, Set<String> tabooClues) throws IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, ParseException
    {
        return createClue(answer, tabooClues, nerModel, archive);
    }

	public Clue createClue(String answer, Set<String> tabooClues, NERModel nerModel, Archive archive) throws IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, ParseException
	{
		return createClue(answer, QuestionType.FILL_IN_THE_BLANK, null, tabooClues, nerModel, new Date(0L), new Date(Long.MAX_VALUE), 1, archive);
	}

    /**
     * create clue for the given answer. cannot pick clue from sentences that have taboo clues
     * if filterDocIds is not null, we use only clues from docs with ids in filterDocIds.
     * @throws ParseException
     */
    public Clue createClue(String answer, ArchiveCluer.QuestionType questionType, List<ClueEvaluator> evals, Set<String> tabooClues, NERModel nerModel, Date startDate, Date endDate, int numSentences, Archive archive) throws IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, ParseException {
        Clue[] clues = createClues(answer, questionType, evals, tabooClues, nerModel, startDate, endDate, numSentences, 1, archive);
        if(clues==null || clues.length==0)
            return null;
        else
            return clues[0];
    }

	public enum QuestionType{
        FILL_IN_THE_BLANK, GUESS_CORRESPONDENT;
    };


	/**
	 * <ol>
	 *     <li>Fill in the blank type where the answer is blanked out in the best context found</li>
	 *     <li>Clues that test the recall of the subjects in a conversation (ie. correspondents)</li>
	 * </ol>
	 * The type of clue can be selected by setting the mode value, 0 for type 1, and 1 for type 2
	 * when mode is 0, i.e. first type of clue, then returns clues formed from alternate sentences from the best context (in a doc)
	 * when mode is 1, i.e. second type of clue, returns the top 5 best clues in all the docs
	 * @param c - contact
	 * @param evaluationRules - Clue evaluators to evaluate the clue statement
	 * @param nerModel - clue evaluators depend on the model to recognise names and for evaluation
	 * @param startDate @param endDate - Marks the beginning and end of the time interval
	 * @param numSentences - Number of sentences in the clue
	 * @param tabooSentenceHashes - if a sentence's lower case form has a hash in this set, it will not be considered
	 * */
	public Clue createPersonNameClue(Contact c, List<ClueEvaluator> evaluationRules, NERModel nerModel, Date startDate, Date endDate, int numSentences, Archive archive, Set<Integer> tabooSentenceHashes) throws IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, ParseException
	{
		if (evaluationRules == null || evaluationRules.size()==0) {
			evaluationRules = MemoryStudy.getDefaultEvals();
		}

        String name = c.pickBestName();
        // messages for c
		List<EmailDocument> messagesWithContact = new ArrayList<>((Set) IndexUtils.selectDocsByContact(archive.addressBook,(Collection)archive.getAllDocs(),c));
        Collections.sort (messagesWithContact);

		float contactScore = computeContactScore(c, messagesWithContact, startDate, endDate);

        log.info("Trying to generate clue for " + name + " (full contact info: " + c + ")");
		log.info("A total of " + Util.pluralize(messagesWithContact.size(), "message") + " were sent to " + name);

		if (messagesWithContact.size() <= 1) {
			log.info("Dropping " + name + " because of <= 1 message");
			return null;
		}

		log.info("Contact score for " + name + " = " + contactScore);

        // find valid docs -- those sent only to c, and in the specified interval
		List<EmailDocument> validMessages = new ArrayList<>();
		// total messages involving c (and perhaps others) in the specified interval
		List<EmailDocument> totalMessagesToC = new ArrayList<>();
        {
            for (EmailDocument ed : messagesWithContact) {
                if (startDate.before(ed.date) && endDate.after(ed.date)) {
					totalMessagesToC.add(ed);
                    Collection<Contact> contacts = ed.getParticipatingContactsExceptOwn(archive.addressBook);
                    if (contacts.size() == 1)
                        validMessages.add(ed);
                }
            }
			log.info (Util.pluralize(totalMessagesToC.size(), "message") + " sent in all to " + name + " within the interval: [" + startDate + ", " + endDate + "]");
			log.info (Util.pluralize(validMessages.size(), "message") + " sent *only* to " + name + " within the interval: [" + startDate + ", " + endDate + "]");
        }

        Set<Long> threadIds = new LinkedHashSet<>();
        for (EmailDocument ed: messagesWithContact)
            threadIds.add(ed.threadID);
        int nThreadsWithThisContact = threadIds.size();

		// now find the clue with the best score
		Clue bestClue = null;
		Document bestClueMessage = null;
		//set of clues formed around best clue, but different sentence choice for clue sentence
		Map<Clue, Float> scoredClues = new LinkedHashMap<>();

		float bestScore = -Float.MAX_VALUE;

		int docCount = 0;
		int nValidClueCandidates = 0;
		for (EmailDocument message: validMessages)
		{
            log.info ("Looking for clues in message: " + message);

            try {
				if (filteredIds != null && !filteredIds.contains(message.getUniqueId()))
					continue; // not in filter docs, so can't use this doc

				Pair<Float, Map<String, Integer>> p = scoreDocForSentiments(message);
				float docSentimentScore = p.getFirst();
				Map<String, Integer> sentimentMap = p.getSecond();

				// scoring for this email message
				List<DatedDocument> messagesInThread = (List) archive.docsWithThreadId(message.threadID);

				// check the # of names in this doc, drastic penalty in doc score if
				// there are a lot of names in it (typically a complete news article)
				List<String> namesInMessage = archive.getNamesForDocId(message.getUniqueId(), Indexer.QueryType.ORIGINAL);

				float emailScore = messagesInThread.size() * 5.0f + docSentimentScore;

				int subjectLength = (message.description != null ? message.description.length() : 0);
				// now process sentences within the doc
				String contents = archive.getContents(message, true);
				String cleanedContents = EmailUtils.cleanupEmailMessage(contents);
				SentenceTokenizer st = new SentenceTokenizer(cleanedContents);

                // make a list of all sentences in the message
				List<String> sentences = new ArrayList<>();
				while (st.hasMoreSentences())
					sentences.add(st.nextSentence(true));

                log.info ("Message has " + Util.pluralize(sentences.size(), "sentence"));

                if (sentences.size() < numSentences) {
                    log.info ("Message has too few sentences");
                    continue;
                }

				nextSentence:
				for (int i = 0; i < sentences.size(); i++)
				{
					if (i < numSentences-1) // e.g. if nSentences = 3, we can start building candidate clues at i = 2
						continue;

					String candidateClue = "";

					for (int j = i - numSentences+1; j <= i; j++) {
						// check if any of the sentences is < MIN_SENTENCE_LENGTH, if so, the clue is invalid, so just break out with an empty string
						String sentence = sentences.get(j);
						if (sentence.length() < MIN_SENTENCE_LENGTH || !sentenceIsValidAsClue(sentence.toLowerCase(), numSentences)) {
							continue nextSentence;
						}

						// taboo hash check
						int hashCode = MemoryStudy.canonicalizeSentence(sentence).hashCode();
						if (tabooSentenceHashes.contains(hashCode)) {
							log.info("Dropping taboo duplicate sentence: "); // + sentence);
							continue nextSentence;
						}

						candidateClue += sentence + " ";
					}

					//String oos = originalSentence;
					candidateClue = candidateClue.replaceAll("\r", "\n"); // weird DOS type stuff has \r's sometimes

					// 2 newlines are normal, but more is bad...it tends to be a signature or list... or code.... doesn't make for a good clue.
					float linesBoost = 1.0f;
					for (int j = 0; j < numSentences; j++) {
						int nLines = new StringTokenizer(sentences.get(i-j), "\n").countTokens();
						if (nLines > numSentences)
							linesBoost += (float) -Math.pow(5.0f, nLines - numSentences); // steep penalty if it spans more than numSentence physical lines
					}

					candidateClue = candidateClue.trim().replaceAll("\n", " ");
					candidateClue = Util.canonicalizeSpaces(candidateClue);
					String lowerCaseSentence = candidateClue.toLowerCase();

                    // check if any overlapping words between candidateClue and name. be careful to tokenize only the alphabetical chars,
                    // because we want to match the clue "Hi John, How are you" with the name "John", ignoring the comma after John.
                    // include all tokens in all names of the contact (not just the best name)
                    Set<String> nameTokens = new LinkedHashSet<>();
                    for (String n: c.names) {
                        nameTokens.addAll(Util.tokenizeAlphaChars(n.toLowerCase()));
                    }
                    Set<String> candidateClueTokens = new LinkedHashSet<>(Util.tokenizeAlphaChars(candidateClue.toLowerCase()));
                    candidateClueTokens.retainAll(nameTokens);

					// we'll exclude single letter initials in someone's name, they can be present as words in the candidate clue
					candidateClueTokens = candidateClueTokens.stream().filter(t -> t.length() > 1).collect(Collectors.toSet());

					if (candidateClueTokens.size() > 0) // there is a give away in the clue - at least one word of the name is directly present in it
                        continue nextSentence;

					int MAX_CLUE_CHARS = 200 * numSentences;

					if (candidateClue.length() >= MAX_CLUE_CHARS) {
						JSPHelper.log.warn("Rejecting candidate clue due to overlong length");
						continue;
					}

					// now score the sentence
					nValidClueCandidates++;
					Clue clue = new Clue(candidateClue, candidateClue, lowerCaseSentence, "" /* hint */, null /* url */, null /* ellipsis message */, message);

					float clueScore = scoreClueByEvalRules(clue, name, evaluationRules, nerModel, archive);
					clueScore += linesBoost;

					// grand total: score for this clue + score for message + score for contact
					float finalScore = clueScore + emailScore + contactScore;
					clue.clueStats.finalScore = finalScore;
					clue.clueStats.contactScore = contactScore;
					clue.clueStats.clueScore = clueScore;
					clue.clueStats.emailScore = emailScore;

					if (finalScore > bestScore)
					{
//                        log.info("New best clue for " + name + " is " + clue.clueStats.finalScore + " (clueScore = " + clueScore + ", emailScore = " + emailScore + ", contactScore = " + contactScore +") for sentence# " + i + " in doc #" + docCount + ":" + clue);
						bestClue = clue;
						bestClueMessage = message;
						bestScore = finalScore;
					}
					scoredClues.put(clue, finalScore);

					// the below should ideally be updated only inside the above if

					// update all the stats now
					clue.clueStats.docSentimentScore = docSentimentScore;
					clue.clueStats.docSentiments = sentimentMap;

					clue.clueStats.sentenceNumInMessage = i;

					// copy stats related to the message
					clue.clueStats.namesInMessage = namesInMessage.size();
					clue.clueStats.charsInMessage = archive.getContents(message, false /* full message, not just original content */).length();

                    clue.clueStats.subjectLength = subjectLength;

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
						clue.clueStats.threadInitiatedByUser = !(message.description != null && message.description.trim().toLowerCase().startsWith("re:")); // really means this message is not the first one in the thread
					}
					// Pair<String, String> p1 = Util.fieldsToCSV(clue.clueStats, false);
					//	JSPHelper.log.info ("CLUELOG-1 " + p1.getFirst() + "answer,clue");
					//	JSPHelper.log.info ("CLUELOG-2 " + p1.getSecond() + "," + answer + "," + clue.clue.replaceAll(",", " "));
//					}
				}

				// update sentencesInMessage at the end of the message, because we know it only at the end of the message
				if (bestClueMessage == message) {
					bestClue.clueStats.sentencesInMessage = sentences.size();
				}
			} catch (Exception e) {
                Util.print_exception("Error trying to generate clues", e, log);
            }

			docCount++;
		}

		if (bestClue != null)
		{
//            log.info("Final best question for " + name + " is score: " + bestClue.clueStats.finalScore + ": clue:"  + bestClue);

            bestClue.clueStats.nValidClueCandidates = nValidClueCandidates; // # of clues considered seriously, i.e. given a score

			int daysSinceFirstMention, daysSinceLastMention;
			if (messagesWithContact.size() > 0)
			{
				bestClue.clueStats.nMessagesWithAnswer = messagesWithContact.size();
				bestClue.clueStats.nThreadsWithAnswer = nThreadsWithThisContact;

				Collections.sort(messagesWithContact);
				EmailDocument firstMentionDoc = (EmailDocument) messagesWithContact.get(0);
				EmailDocument lastMentionDoc = (EmailDocument) messagesWithContact.get(messagesWithContact.size()-1);
				daysSinceFirstMention = (int) ((new Date().getTime() - firstMentionDoc.date.getTime())/EmailUtils.MILLIS_PER_DAY);
				daysSinceLastMention = (int) ((new Date().getTime() - lastMentionDoc.date.getTime())/EmailUtils.MILLIS_PER_DAY);
				bestClue.clueStats.daysSinceFirstMention = daysSinceFirstMention;
				bestClue.clueStats.daysSinceLastMention = daysSinceLastMention;

				// compute mention frequency for each of the last N months
				List<Date> datesOfMessagesWithContact = new ArrayList<Date>();
				for (Document doc: messagesWithContact) {
					EmailDocument ed = (EmailDocument) doc;
					datesOfMessagesWithContact.add(ed.date);
				}
				List<Integer> hist = EmailUtils.histogram(datesOfMessagesWithContact, new Date().getTime(), MemoryStudy.INTERVAL_MILLIS);

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

	/** score for this contact based on communication pattern */
	private static float computeContactScore(Contact c, Collection<EmailDocument> messagesWithContact, Date intervalStart, Date intervalEnd) {
		float outsideThisIntervalScore = 0.0f; // this will be -ve and penalize contacts outside the preferred interval
		int messagesInThisInterval = 0;

		for (EmailDocument message: messagesWithContact) {
			if (message.date.before(intervalStart)) {
				outsideThisIntervalScore += ((intervalStart.getTime() - message.date.getTime())/MemoryStudy.INTERVAL_MILLIS); // don't add 1 here -- let us start penalizing from one additional interval backwards.
			} else if (message.date.before(intervalEnd)) {
				// it must be in "this interval"
				messagesInThisInterval++;
			} else {
				log.warn ("Scoring a contact: seeing a message outside interval");
			}
		}

		float inThisIntervalScore = 0.0f;

		if (messagesInThisInterval > 10)
			inThisIntervalScore = messagesInThisInterval * 20;
		else if (messagesInThisInterval > 5)
			inThisIntervalScore = messagesInThisInterval * 15;
		else if (messagesInThisInterval > 1) // strongly penalize one-off correspondents, they get no boost. But if they're messaged more than once, give them at least a 10X reward
			inThisIntervalScore = messagesInThisInterval * 10;

		return inThisIntervalScore - outsideThisIntervalScore;
	}

    /**
     * <ol>
     *     <li>Fill in the blank type where the answer is blanked out in the best context found</li>
     *     <li>Clues that test the recall of the subjects in a conversation (ie. correspondents)</li>
     * </ol>
     * The type of clue can be selected by setting the mode value, 0 for type 1, and 1 for type 2
     * when mode is 0, i.e. first type of clue, then returns clues formed from alternate sentences from the best context (in a doc)
     * when mode is 1, i.e. second type of clue, returns the top 5 best clues in all the docs
     * @param answer - answer to the clue
     * @param questionType - The clue type @see QuestionType
     * @param evals - Clue evaluators to evaluate the clue statement
     * @param tabooClues - makes sure none of the clues have statements taht are candidates of this set
     * @param nerModel - clue evaluators depend on the model to recognise names and for evaluation
     * @param startDate @param endDate - Marks the beginning and end of the time interval
     * @param numSentences - Number of sentences in the clue
     * @param maxClues - maximum number of high scoring clues returned
     * */
	public Clue[] createClues(String answer, QuestionType questionType, List<ClueEvaluator> evals, Set<String> tabooClues, NERModel nerModel, Date startDate, Date endDate, int numSentences, int maxClues, Archive archive) throws IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, ParseException
	{
		// first canonicalize w
		answer = answer.toLowerCase();
        Collection<Document> docs;
        if(archive == null||archive.addressBook==null) {
            log.error("Archive or its address book is null! This is unexpected and !!!SERIOUS!!!\nReturning without generating clues.");
            return new Clue[]{};
        }
        if(answer == null){
            log.warn("Unexpected arguments!! Answer: "+answer+", Evaluators: "+evals);
        }
        if (endDate == null){
            Calendar cal = Calendar.getInstance();
            endDate = cal.getTime();
            log.warn("Initialized end date to "+endDate);
        }
        if (startDate == null){
            Calendar cal = new GregorianCalendar();
            cal.set(1960, 0, 1);
            startDate = cal.getTime();
            log.warn("Initialized start date to "+startDate);
        }

		if (evals == null || evals.size()==0) {
			evals = MemoryStudy.getDefaultEvals();
		}

		boolean answerPartOfAnyAddressBookName = archive.addressBook.isStringPartOfAnyAddressBookName(answer);
        if(questionType == QuestionType.FILL_IN_THE_BLANK)
            docs = archive.docsForQuery("\"" + answer + "\"", Indexer.QueryType.ORIGINAL); // look up inside double quotes since answer may contain blanks
        else{
            Contact c = archive.addressBook.lookupByName(answer);
            if(c == null) {
                log.error("Contact: "+answer+" not found!! returning");
                return new Clue[]{};
            }
            docs = IndexUtils.selectDocsByContact(archive.addressBook,(Collection)archive.getAllDocs(),c);
        }
        log.info("Docs with answer: "+answer+" is #"+docs.size());

        // find all messages in the date range
//        List<Document> docsWithAnswer = new ArrayList<>();
//        for (Document doc : docs) {
//            DatedDocument dd = (DatedDocument) doc;
//            if (dd.date.before(startDate) || dd.date.after(endDate))
//                continue;
//            docsWithAnswer.add(doc);
//        }

        List<Document> docsWithAnswer = new ArrayList<>(docs);
		// note: docsWithAnswer is not sorted by time
		int nDocsWithAnswer = docsWithAnswer.size();
        log.info("Docs with answer: " + answer + " and within the window: ["+startDate+", "+endDate+"] is #" + nDocsWithAnswer);

		// compute #threads in the docsWithAnswer set
		Set<Long> set = new LinkedHashSet<Long>();
		for (Document doc: docsWithAnswer)
		{
            EmailDocument ed = (EmailDocument)doc;
			if (ed.threadID != 0)
				set.add(ed.threadID);
		}
		int nThreadsWithAnswer = set.size();

		// now find the clue with the best score
		Clue bestClue = null; 
		Document bestClueDoc = null;
        //set of clues formed around best clue, but different sentence choice for clue sentence
        Map<Clue, Float> scoredClues = new LinkedHashMap<>();

        float bestScore = -Float.MAX_VALUE;
		boolean useFirstLetterClue = (archive.getAllDocs().size() == 1); // if only 1 doc, must be a dummy, use first letter as clue instead

		// we want a good pool of docs to select clues from. at the same time, we don't want to look at ALL docs with the term because the
		// number could be very large. so we look at at least N_DOCS_TO_CHECK_AT_LEAST and return the best from it.
		int N_DOCS_TO_CHECK_FOR_CLUES = 50;
		int docCount = 0;
		int nValidClueCandidates = 0;
        List<ClueFilter> clueFilters = ClueFilter.getDefaultFilters(questionType);
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
				List<String> sentences = new ArrayList<>();
				while (st.hasMoreSentences())
					sentences.add(st.nextSentence(true));

				if (sentences.size() < numSentences)
					continue;

                List<Clue> clueSet = new ArrayList<>();
				outer:
				for (int i = 0; i < sentences.size(); i++)
				{
					if (i < numSentences-1) // e.g. if nSentences = 3, we can start building candidate clues at i = 2
						continue;

					String originalSentence = "";
					for (int j = i - numSentences+1; j <= i; j++) {
						// check if any of the sentences is < MIN_SENTENCE_LENGTH, if so, the clue is invalid, so just break out with an empty string
						if (sentences.get(j).length() < MIN_SENTENCE_LENGTH) {
							continue outer;
						}
						originalSentence += sentences.get(j);
					}

                    //String oos = originalSentence;
					originalSentence = originalSentence.replaceAll("\r", "\n"); // weird DOS type stuff has \r's sometimes

					// 1 newline is normal, but 2 or more is bad...it tends to be a signature or list... or code.... doesn't make for a good clue.
					float linesBoost = 1.0f;
					for (int j = 0; j < numSentences; j++) {
						int nLines = new StringTokenizer(sentences.get(i-j), "\n").countTokens();
						if (nLines > 2)
							linesBoost += (float) -Math.pow(5.0f, nLines - 1); // steep penalty if it spans more than 2 lines
					}

					originalSentence = originalSentence.trim().replaceAll("\n", " ");
					originalSentence = Util.canonicalizeSpaces(originalSentence);
					String lowerCaseSentence = originalSentence.toLowerCase();

                    //this test is valid only for first type of clues
					if (questionType==QuestionType.FILL_IN_THE_BLANK && !Util.occursOnlyAsWholeWord(lowerCaseSentence, answer)) {
                        //System.err.println("Rejecting because no whole word!! "+answer);
                        continue;
                    }

                    if(questionType==QuestionType.GUESS_CORRESPONDENT){
                        //should not see any of the archive owner name in the clue and also the answer in the clue
                        boolean dirty = false;
                        for(String str: archive.ownerNames)
                            if(Util.occursOnlyAsWholeWord(lowerCaseSentence, str.toLowerCase())) {
                                dirty = true;
                                break;
                            }
                        if(!dirty && Util.occursOnlyAsWholeWord(lowerCaseSentence, answer))
                            dirty = true;
                        if(dirty)
                            continue;
                    }

					int MAX_CLUE_CHARS = 200 * numSentences;

					if (originalSentence.length() >= MAX_CLUE_CHARS) {
                        JSPHelper.log.warn("Rejecting for extra size");
                        continue;
                    }

					// check if #letter chars in sentence = #letters chars in word.
					// we can't just check length of sentence == length of word
					// because sometimes we get a sentence like <X + punctuation> as a clue for X and we want to eliminate such sentences
					if (Util.nLetterChars(originalSentence) == Util.nLetterChars(answer)) {
						JSPHelper.log.warn("Rejecting due to unequal letter chars!! " + answer);
                        continue;
                    }

					if (!sentenceIsValidAsClue(lowerCaseSentence, numSentences)) {
						JSPHelper.log.warn("Rejecting because it failed the valid clue check!! " + lowerCaseSentence);
                        continue;
                    }
					if (tabooClues != null && (tabooClues.contains(lowerCaseSentence) || tabooClues.contains(originalSentence))) {
						JSPHelper.log.warn("Rejecting because it is a taboo clue!! " + originalSentence);
                        continue;
                    }
					
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

                    boolean dirty = false;
                    for(ClueFilter clueFilter: clueFilters) {
                        if (!clueFilter.filter(clue, questionType, answer, startDate, endDate, tabooNamesSet, nerModel, archive)) {
                            dirty = true;
                            break;
                        }
                    }
                    if(dirty)
                        continue;
                    float clueScore = scoreClueByEvalRules(clue, answer, evals, nerModel, archive);
					clueScore += linesBoost;

					// a small boost for sentences earlier in the message -- other things being equal, they are likely to be more important
					float sentenceNumBoost = -0.2f * i;
					// log.info ("raw clue score for " + answer + " is " + clueScore + " sentence num boost = " + sentenceNumBoost);
					// add in the score for message factors
					clueScore += docSentimentScore;
					clueScore += sentenceNumBoost;

					clue.clueStats.finalScore = clueScore;
					log.info("clue score for " + answer + " is " + clueScore + " (docscore: " + docSentimentScore + ") for sentence# " + i + " in doc #" + docCount + ":" + clue + " lines boost = " + linesBoost);

					if (clueScore > bestScore)
					{
						log.info ("Prev. clue: New high!");
						bestClue = clue;
						bestClueDoc = ed;
						bestScore = clueScore;
					}
                    scoredClues.put(clue, clueScore);
					
					// the below should ideally be updated only inside the above if
					
						// update all the stats now
						clue.clueStats.linesBoost = linesBoost;
						clue.clueStats.docSentimentScore = docSentimentScore;
						clue.clueStats.docSentiments = sentimentMap;
						clue.clueStats.sentenceNumBoost = sentenceNumBoost;
						
						clue.clueStats.sentenceNumInMessage = i;
						
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
				if (bestClueDoc == ed) {
                    bestClue.clueStats.sentencesInMessage = sentences.size();
                }
			} catch (Exception e) { Util.print_exception("Error trying to generate clues", e, log); }
			
			docCount++;
		}
		
		if (bestClue != null) 
		{
			bestClue.clueStats.nValidClueCandidates = nValidClueCandidates; // # of clues considered seriously, i.e. given a score

			int daysSinceFirstMention, daysSinceLastMention;
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
        List<Pair<Clue, Float>> sclues = Util.sortMapByValue(scoredClues);
        log.info("Found "+sclues.size()+" for "+answer);
        List<Clue> clues = new ArrayList<>();
        int i=0;
        for(Pair<Clue, Float> p: sclues) {
            clues.add(p.getFirst());
            if(++i>=maxClues)
                break;
        }
        return clues.toArray(new Clue[clues.size()]);
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
	public static float scoreClueByEvalRules(Clue clue, String answer, List<ClueEvaluator> evals, NERModel nerModel, Archive archive) throws ClassCastException, IOException, ClassNotFoundException
	{
//		String canonicalizedanswer = (Util.canonicalizeSpaces(answer)).toLowerCase();
//		String s = clue.getFullSentenceOriginal();
//		// first look for signs of bad clues and return a low score
//		String sOrig = s;
//		s = s.toLowerCase();

		// other possible things to factor in, in the future... 
		// does it start with a capital letter?
		// does it have number tokens in it?


		// these are things that make the clue less interesting, but we'll use them if we can't find anything else!
		// see if the sentence mainly consists of the word
		//		int sChars = Util.nLetterChars(s);
		//		int wChars = Util.nLetterChars(w);
		//		if (sChars < wChars + 20)
		//			score = -10.0f;
		// how many names does it have? more names is good.

//		for (Pair<String, Float> namePair : names){ //check that NER detects same name in text as the name of the answer (eg. removes instances where NER returns "San Jose" from clue, but answer is "San")
//			String name = (Util.canonicalizeSpaces(namePair.getFirst())).toLowerCase();

		double score = 0;
        for(ClueEvaluator eval: evals)
            score += eval.computeScore(clue, answer, nerModel, archive);

        //log.info ("score = " + score + " namesScore = " + namesScore + " exclamationScore = " + exclamationScore + " smileyScore = " + smileyScore + " lengthBoost = " + lengthBoost);
        // log.info("Score: "+score+" "+clue.clueStats);
        return (float)score;
	}

	/** checks if sentence is ok as clue. returns true if yes, false otherwise */
	public static boolean sentenceIsValidAsClue(String lowerCaseSentence, int nSentences)
	{					
//		if (lowerCaseSentence.indexOf("/") >= 0 || lowerCaseSentence.indexOf("|") >= 0 || lowerCaseSentence.indexOf(">") >= 0 || lowerCaseSentence.indexOf("---") >= 0 || lowerCaseSentence.indexOf("<") >= 0 || lowerCaseSentence.indexOf("___") >= 0) // get rid of things like http:// or >> or | quotes

        // removing the barring of "/" from the sentence, because sometimes it can occur in real english sentences like lonavala/khandala
        if (lowerCaseSentence.indexOf("|") >= 0 || lowerCaseSentence.indexOf(">") >= 0 || lowerCaseSentence.indexOf("---") >= 0 || lowerCaseSentence.indexOf("<") >= 0 || lowerCaseSentence.indexOf("___") >= 0) // get rid of things like http:// or >> or | quotes
			return false;
		if (lowerCaseSentence.charAt(0) < 'a' || lowerCaseSentence.charAt(0) > 'z')
			return false;
		if (lowerCaseSentence.startsWith("to")) // too generic
			return false;
		if (lowerCaseSentence.startsWith(")") || lowerCaseSentence.startsWith(","))
			return false;
		//protect against excessively large words and slightly large words with numbers in them
		List<String> wordsInSentence = Util.tokenize(lowerCaseSentence, " ");
		int MAX_WORDS = 20 * nSentences;
		int MAX_WORDS_W_NUMBER = 15 * nSentences;
		for (String word: wordsInSentence){
			if (word.length() > MAX_WORDS)
				return false;
			
			//protect against numbers in a large word.
			if (word.length() > MAX_WORDS_W_NUMBER)
				for (char c: word.toCharArray()) 
					if (Character.isDigit(c))
						return false;

            // disallow = in long words because we see url params authKey=someLongHashCode
            // just = in a sentence is ok.
            if (word.contains("=") && word.length() > 10)
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
		if (lowerCaseSentence.indexOf("http") >= 0)
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
