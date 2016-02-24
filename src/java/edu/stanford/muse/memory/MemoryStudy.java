package edu.stanford.muse.memory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.*;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.exceptions.ReadContentsException;
import edu.stanford.muse.index.*;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.util.DictUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.ie.NameInfo;
import edu.stanford.muse.ie.NameTypes;
import edu.stanford.muse.util.CryptoUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;
import edu.stanford.muse.xword.ArchiveCluer;
import edu.stanford.muse.xword.Clue;
import edu.stanford.muse.xword.Crossword;
import org.apache.lucene.queryparser.classic.ParseException;

import javax.mail.Address;

public class MemoryStudy implements Serializable{

	private static final long serialVersionUID = 1L;

	public static Log log = LogFactory.getLog(MemoryStudy.class);
	private static final int MIN_ANSWER_LENGTH = 3, MAX_ANSWER_LENGTH = 15;
	public static String PAYMENT = System.getProperty("PAYMENT"); // note, this does not have $10, only 10
	
	/** screening params */
	public static final long INTERVAL_MILLIS = 30L * 24 * 60 * 60 * 1000; /** millis per 30 days */
	public static final int N_INTERVALS = 12; // # 30 day intervals 
	public static final int MIN_MESSAGES_PER_INTERVAL = 12; // # 30 day intervals 
	
	/** overall information across the study */
	public static class UserStats implements Serializable {
		private static final long serialVersionUID = 1L;
		public String userid, code, emailid, gender, age, education, profession, ethnicity, IPaddress;
		public long starttime;
		public long endtime;
		public String userAgent;
		public List<Integer> activityHistogram;
		
		public UserStats() { }
		
		public UserStats(String emailid, String gender, String age, String education, String profession, String ethnicity, String userid, String IPaddress, String userAgent) {
			this.userid = userid;
			this.emailid = emailid;
			this.gender = gender;
			this.age = age;
			this.education = education;
			this.profession = profession;
			this.ethnicity = ethnicity;
			this.IPaddress = IPaddress;
			this.userAgent = userAgent;
		}
		
		public String toString() { return Util.fieldsToString(this); }
	}

    /* small util class -- like clue but allows answers whose clue is null */
    public static class ClueInfo implements Comparable<ClueInfo> {
        //clues corrsponding to different choice of sentences in the context
        public Clue[] clues;
        public String link, displayEntity;
        public int nMessages, nThreads;
        public Date lastSeenDate;
        public boolean hasCoreTokens;

        public String toHTMLString() {
            String str = "";
            for(Clue clue: clues){
                str += "<tr><td><a href='" + link + "' target='_blank'>" + displayEntity + "</a></td><td>" + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(lastSeenDate) + "</td><td>" + nMessages + "</td><td>" + nThreads + "</td><td>" + (clue != null ? clue.clueStats.finalScore : "-") + "</td></tr>"
                        + "<tr><td class=\"clue\" colspan=\"6\">" + (clue != null ? (clue.clue + "<br/><br/><div class=\"stats\"> stats: " + Util.fieldsToString(clue.clueStats, false)) : "No clue") + "</div><br/><br/></td></tr><br>";
            }
            return str;
        }

        public int compareTo(ClueInfo c2) {
            // all answers with core tokens should be last in sort order
            if (this.hasCoreTokens && !c2.hasCoreTokens)
                return 1;
            if (c2.hasCoreTokens && !this.hasCoreTokens)
                return -1;

            if(this.clues == null || c2.clues == null) {
                if (this.clues == c2.clues) return 0;
                else return (this.clues==null)?1:-1;
            }
            if(this.clues.length == 0 || c2.clues.length == 0) {
                if (c2.clues.length == this.clues.length)
                    return 0;
                else return (this.clues.length > c2.clues.length)? -1 : 1;
            }

            //decide based on their first clues
            Clue clue = this.clues[0], cclue = c2.clues[0];
            // all answers with clues should come towards the end
            if (clue == null && cclue != null)
                return 1;
            if (clue != null && cclue == null)
                return -1;
            if (clue == null && cclue == null)
                return displayEntity.compareTo(c2.displayEntity); // just some order, as long as it is consistent

            if (clue != null && cclue.clue != null)
                return (clue.clueStats.finalScore > cclue.clueStats.finalScore) ? -1 : (cclue.clueStats.finalScore > clue.clueStats.finalScore ? 1 : 0);
            return 0;
        }
    }
	
	public UserStats stats;
	private int questionIndex;
	private int listLocation;
	private Set<String> tabooCluesSet;
	private ArrayList<MemoryQuestion> questions;
	public Archive archive;

	public static List<String> codes;
	static String CODES_FILE, USERS_FILE;
	
	static {
		// read all the codes at bootup -- the codes file is not encrypted and never changes
		CODES_FILE = System.getProperty("user.home") + java.io.File.separator + "results" + java.io.File.separator + "codes.txt";
		USERS_FILE = System.getProperty("user.home") + File.separator + "results" + File.separator + "users";

		try {
			// read the codes statically at startup
			codes = Util.getLinesFromFile(CODES_FILE, true);
		} catch (Exception e) {
			Util.print_exception ("\n\n\n\nSEVERE WARNING IN BOOTUP\n\n\n, codes file not read", e, log);
			codes = new ArrayList<String>(); // dummy list of codes (empty)
		}
	}
	
	/*Constructor is used in memorytestlogin.jsp*/
	public MemoryStudy(UserStats stats){
		this.stats = stats;
		listLocation = 0;
		questionIndex = 1;
	}
	
	synchronized public static boolean anyCodesAvailable() throws IOException, GeneralSecurityException, ClassNotFoundException
	{
		List<UserStats> users = readUsersFile();
		return (users.size() < codes.size());
	}
	
	// add the new user to the encrypted users file which has the detailed stats for all users	
	// synchronize to make sure that only one thread can be inside this block at a time.
	synchronized public static String addUserAndIssueCode(UserStats stats) throws IOException, GeneralSecurityException, ClassNotFoundException, NoMoreCodesException {
		
		// ok, now we have a list of stats objects, representing all users.
		// add this user to the list.
		// the code assigned to this user will simply be codes.get(users.size())
		// but do an error check first to be sure we're not out of codes
		List<UserStats> users = readUsersFile();
		
		if (users.size() >= MemoryStudy.codes.size()) {
			log.warn ("NOC WARNING: codes file exhausted!!!!");
			throw new NoMoreCodesException();
		}

		// ok, we have enough codes, just give out one
		String passkey = MemoryStudy.codes.get(users.size());
		int USERID_BASE = 100;	// userid numbering starts from USERID_BASE, new id is base + # existing codes
		stats.userid = Integer.toString(USERID_BASE + users.size());
		stats.code = passkey;
		users.add(stats);

		// encrypt and write the users file back
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(users);
		oos.close();
		CryptoUtils.writeEncryptedBytes(baos.toByteArray(), USERS_FILE);
		
		return passkey;
	}
	
	private static List<UserStats> readUsersFile() throws IOException, GeneralSecurityException, ClassNotFoundException {
		// read the bytes, decrypt them and read a list object, with stats for each user
		if (!new File(USERS_FILE).exists())
			return new ArrayList<MemoryStudy.UserStats>();
		
		byte[] b = CryptoUtils.readEncryptedBytes(USERS_FILE);
		ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b));
		ois.close();
		List<MemoryStudy.UserStats> users = (List<MemoryStudy.UserStats>) ois.readObject();		
		return users;
	}
	
	/** lookup doesn't have to be synchronized 
	 * @throws ClassNotFoundException 
	 * @throws GeneralSecurityException 
	 * @throws IOException */
	synchronized public static UserStats lookup (String userCode) throws IOException, GeneralSecurityException, ClassNotFoundException 
	{
		List<UserStats> users = readUsersFile();

		if (codes == null)
			return null;
		for (int i = 0; i < codes.size(); i++) {
			if (codes.get(i).equals(userCode))
				return users.get(i); // simple logic: user is providing code #1. therefore s/he must be user #i. what's not to like?.
		}
		return null;
	}
	
	public boolean isInitialized() { return (questions != null); }
	
	public void recordStartTime() { 
		stats.starttime = new Date().getTime();
	}
	
	/** Generates list of questions and stores it in the current instance of MemoryStudy
     * We handle two kinds of questions namely, person names tests and non-person name tests.
     * Non-person name test is a fill in the blank kind where the blank is to be filled with the correct non-person entity to complete the sentence
     * person name test is to guess the person in correspondent list based on some distinctive sentences in the mail
     * @param maxInt - max. number of questions from a interval
	 * @throws IOException */
	public void generateQuestions(Archive archive, NERModel nerModel, Collection<EmailDocument> allDocs, Lexicon lex, int maxInt, boolean personTest) throws IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, ParseException {
		this.archive = archive;
		if (allDocs == null)
			allDocs = (Collection) archive.getAllDocs();
        questions = new ArrayList<>();
		ArchiveCluer cluer = new ArchiveCluer(null, archive, nerModel, null, lex);

        Short[] itypes = new Short[]{FeatureDictionary.BUILDING,FeatureDictionary.PLACE, FeatureDictionary.RIVER, FeatureDictionary.ROAD, FeatureDictionary.UNIVERSITY, FeatureDictionary.MOUNTAIN, FeatureDictionary.AIRPORT,
                FeatureDictionary.ISLAND,FeatureDictionary.MUSEUM, FeatureDictionary.BRIDGE, FeatureDictionary.AIRLINE,FeatureDictionary.THEATRE,
                FeatureDictionary.LIBRARY, FeatureDictionary.LAWFIRM, FeatureDictionary.GOVAGENCY};
        double CUTOFF = 0.001;
        tabooCluesSet = new LinkedHashSet<>();
        archive.assignThreadIds();

        List<Document> docs = archive.getAllDocs();
        Map<String, Date> entityToLastDate = new LinkedHashMap<>();
        Multimap<String, EmailDocument> entityToMessages = LinkedHashMultimap.create();
        Multimap<String, Long> entityToThreads = LinkedHashMultimap.create();
        Multimap<String, String> ceToDisplayEntity = LinkedHashMultimap.create();

        int di = 0;

        // sort by date
        Collections.sort(docs);

        Set<String> ownerNames = archive.ownerNames;
        Date earliestDate = null, latestDate = null;
        Set<String> allEntities = new LinkedHashSet<>();
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            if (earliestDate == null || ed.date.before(earliestDate))
                earliestDate = ed.date;
            if (latestDate == null || ed.date.after(latestDate))
                latestDate = ed.date;

            List<String> entities = new ArrayList<>();
            if(!personTest) {
                Map<Short, Map<String, Double>> es = edu.stanford.muse.ner.NER.getEntities(archive.getDoc(doc), true);
                for (Short t : itypes) {
                    Map<String, Double> tes = es.get(t);
                    for (String str : tes.keySet())
                        if (tes.get(str) > CUTOFF)
                            entities.add(str);
                }
            }
            else{
                //do not consider mailing lists
                if(ed.sentToMailingLists!=null && ed.sentToMailingLists.length>0)
                    continue;
                //discard doc if it is not a sent mail
                if((ed.sentOrReceived(archive.addressBook) & EmailDocument.SENT_MASK)==0)
                    continue;

                List<Address> addrs = new ArrayList<>();
                if(ed.to!=null)
                    for(Address addr: ed.to)
                        addrs.add(addr);

                List<String> names = new ArrayList<>();
                for(Address addr: addrs) {
                    Contact c = archive.addressBook.lookupByAddress(addr);
                    names.add(c.pickBestName());
                }

				for (String name : names) {
					if (!ownerNames.contains(name) && !DictUtils.hasDictionaryWord(name)) {
						entities.add(name);
					}
				}
            }
            allEntities.addAll(entities);

            // get entities
            for (String e : entities) {
                if (Util.nullOrEmpty(e))
                    continue;
                e = e.replaceAll("^\\W+|\\W+$","");
                if (e.length() > 10 && e.toUpperCase().equals(e))
                    continue; // all upper case, more than 10 letters, you're out.

                String ce = DictUtils.canonicalize(e); // canonicalize
                if (ce == null) {
                    JSPHelper.log.info ("Dropping entity: "  + e);
                    continue;
                }

                ceToDisplayEntity.put(ce, e);
                entityToLastDate.put(ce, ed.date);
                entityToMessages.put(ce, ed);
                entityToThreads.put(ce, ed.threadID);
            }

            if ((++di)%1000==0)
                log.info(di + " of " + docs.size() + " messages processed...<br/>");
        }
        log.info("Considered #" + allEntities.size() + " unique entities and #" + ceToDisplayEntity.size() + " good ones in #" + docs.size() + " docs<br>");
        log.info("Owner Names: " + ownerNames);
        JSPHelper.log.info("Considered #"+allEntities.size()+" unique entities and #"+ceToDisplayEntity.size()+" good ones in #"+docs.size()+"docs");

        JSPHelper.log.info ("earliest date = " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(earliestDate));
        JSPHelper.log.info ("latest date = " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(latestDate));

        Multimap<String, String> tokenToCE = LinkedHashMultimap.create();
        for (String ce: ceToDisplayEntity.keySet()) {
            List<String> tokens = Util.tokenize(ce);
            for (String t: tokens)
                tokenToCE.put(t, ce);
        }

        // Compute date intervals
        int DAYS_PER_INTERVAL = 30;
        List<Pair<Date, Date>> intervals = new ArrayList<Pair<Date, Date>>();
        {
            JSPHelper.log.info ("computing time intervals");
            Date closingDate = latestDate;

            JSPHelper.log.info ("closing = " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(closingDate));
            while (earliestDate.before(closingDate)) {
                Calendar cal = new GregorianCalendar();
                cal.setTime(closingDate); // this is the time of the last sighting of the term
                // scroll to the beginning of this month
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                Date endDate = cal.getTime();

                cal.add(Calendar.DATE, (1-DAYS_PER_INTERVAL)); // 1- because we want from 0:00 of first date to 23:59 of last date
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                Date startDate = cal.getTime();

                intervals.add(new Pair<Date, Date>(startDate, endDate));
                // ok we got an interval

                // closing date for the next interval is 1 day before endDate
                cal.add(Calendar.DATE, -1);
                closingDate = cal.getTime();
            }
            JSPHelper.log.info ("done computing intervals, #time intervals: " + intervals.size());
            for (Pair<Date, Date> p: intervals)
                JSPHelper.log.info ("Interval: " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getFirst()) + " - " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getSecond()));
        }

        // initialize clueInfos to empty lists
        List<ClueInfo> clueInfos[] = new ArrayList[intervals.size()];
        for (int i = 0; i < intervals.size(); i++) {
            clueInfos[i] = new ArrayList<ClueInfo>();
        }

        Map<Integer, Integer> intervalCount = new LinkedHashMap<>();
        //nSent is the number of sentences allowed in a clue text
        int nvalidclues = 0, nSent = 2;
        // generate clueInfos for each entity
        for (String ce: entityToLastDate.keySet()) {
            Date lastSeenDate = entityToLastDate.get(ce);

            // compute displayEntity (which has red for core words) and fullAnswer, which is a simple string
            String fullAnswer = "";
            {
                List<String> tokens = Util.tokenize(ceToDisplayEntity.get(ce).iterator().next());
                for (String t: tokens) {
                    if (EnglishDictionary.stopWords.contains(t.toLowerCase()))
                        continue;
                    fullAnswer += t + " ";
                }
                fullAnswer = fullAnswer.trim();
            }
            //dont want the answer to be scored low just because it has extra non-word chars in the begin or end
            fullAnswer = fullAnswer.replaceAll("^\\W+|\\W+$","");

            // which interval does this date belong to?
            int interval = -1;
            Date intervalStart = null, intervalEnd = null;
            {
                int i = 0;
                for (Pair<Date, Date> p : intervals)
                {
                    intervalStart = p.getFirst();
                    intervalEnd = p.getSecond();

                    if ((intervalStart.before(lastSeenDate) && intervalEnd.after(lastSeenDate)) || intervalStart.equals(lastSeenDate) || intervalEnd.equals(lastSeenDate))
                    {
                        interval = i;
                        break;
                    }
                    i++;
                }
            }
            if (interval < 0 || interval == intervals.size())
                JSPHelper.log.info ("What, no interval!? for " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(lastSeenDate));
            if(!intervalCount.containsKey(interval))
                intervalCount.put(interval, 0);
            if(intervalCount.get(interval)>maxInt)
                continue;
            intervalCount.put(interval, intervalCount.get(interval)+1);

            List<Integer> lengthList = Crossword.convertToWord(fullAnswer).getSecond();
            String lengthDescr = "";
            if (lengthList.size() > 1)
                lengthDescr += Integer.toString(lengthList.size()) + " words: ";

            for (Integer i :lengthList) {
                lengthDescr += i + " characters, ";
            }
            lengthDescr = lengthDescr.substring(0, lengthDescr.length()-2); //subtract the extra comma.

            ClueInfo ci = new ClueInfo();
            ci.link = "../browse?term=\"" + fullAnswer + "\"&sort_by=recent&searchType=original";;
            ci.lastSeenDate = lastSeenDate;
            ci.nMessages = entityToMessages.get(ce).size();;
            ci.nThreads = entityToThreads.get(ce).size();

            //TODO: we are doing default initialisation of evaluators by setting it to null below, it is more appropriate to consider it as an argument for this method
            Clue clue = cluer.createClue(fullAnswer, (personTest ? ArchiveCluer.QuestionType.GUESS_CORRESPONDENT : ArchiveCluer.QuestionType.FILL_IN_THE_BLANK), null, tabooCluesSet, null, intervalStart, intervalEnd, nSent, archive);
            if(clue!=null)
                ci.clues = new Clue[]{clue};

            if(ci.clues == null || ci.clues.length == 0 || clue==null){
                JSPHelper.log.warn("Did not find any clue for: "+fullAnswer);
            }
            else{
                //is the times value of the clue important?
                questions.add(new MemoryQuestion(this,fullAnswer,clue, 1, lengthDescr));
                nvalidclues++;
                //makes sure that the clue with the same statement is not generated again
                tabooCluesSet.add(clue.clue);
            }
            clueInfos[interval].add(ci);
        }
        log.info("Found valid clues for "+nvalidclues+" answers");
        JSPHelper.log.info("Found valid clues for "+nvalidclues+" answers");
		
		log.info("Top candidates are:");
		for (MemoryQuestion mq: questions)
			log.info (mq.correctAnswer + " times=" + mq.stats.nMessagesWithAnswer);
		
		// sort q's by clue score
		Collections.sort(questions);

		log.info("Based on clue score, top answers:");
		for (MemoryQuestion mq: questions)
			log.info (mq.correctAnswer + " times= clue=" + mq.clue.clue);

		// now we have up to 2*N questions, sorted by cluescore.
		// drop ones that are prefix/suffix of another, and cap to N
		int prev_size = questions.size();

		int new_size = questions.size();
		
		log.info ("#questions before prefix-suffix elim: " + prev_size + " after: " + new_size);
		
		int count = 0;
		for (MemoryQuestion mq: questions) {
			mq.setQuestionNum(count++);
		}

		// log the questions as well, just in case we don't get to the final point due to user fatigue or crashes
		logStats("questions.final");
	}
	
	private static void assignTypes(Collection<MemoryQuestion> questions, Map<String, NameInfo> nameMap) throws IOException
	{
		// assign categories. we have to assemble all the terms we want to look up together for efficient lookup..
		// we'll put them in map
		Map<String, NameInfo> map = new LinkedHashMap<String, NameInfo>();
		for (MemoryQuestion mq: questions)
		{
			String name = mq.correctAnswer;
			String cname = name.toLowerCase().trim();
			map.put(cname, nameMap.get(cname));
		}
		
		// now we do a bulk type lookup...
		NameTypes.readTypes(map);
		
		// ... and copy the types back into the mq's
		for (MemoryQuestion mq: questions)
			try {
                if(mq!=null && mq.correctAnswer!=null) {
                    NameInfo ni = nameMap.get(mq.correctAnswer.toLowerCase().trim().replaceAll(" ", "_"));
                    if (ni != null)
                        mq.clue.clueStats.answerCategory = ni.type;
                }
            } catch (Exception e) {
                Util.print_exception("Error reading types for question: " + mq, e, log);
            }
	}
	
	public boolean checkQuestionListSize(int N){
		if (questions.size() < N)
			return true;
		else
			return false;
	}
	
	/* Drops terms that contain other terms (prefixes, suffixes, etc.) and trims the questionlist to the target size.*/
	private ArrayList<MemoryQuestion> dropPrefixSuffixfromSortedList (List<MemoryQuestion> questionlist, int targetSize) {
		ArrayList<MemoryQuestion> resultList = new ArrayList<MemoryQuestion>();
		Set<Integer> badIndexSet = new LinkedHashSet<Integer>();
		int terms = questionlist.size();
		for (int first = 0; first < terms; first++){
			MemoryQuestion mq1 = questionlist.get(first);
			String name1 = mq1.correctAnswer;
			
			if (badIndexSet.contains(first))
				continue;
			resultList.add(mq1);
			
			if (resultList.size() >= targetSize)
				return resultList;
			
			for (int second = first+1; second < terms; second++){
				MemoryQuestion mq2 = questionlist.get(second);
				String name2 = mq2.correctAnswer;
				
				if (name1.contains(name2) || name2.contains(name1)){
					badIndexSet.add(second);
				}
			}
		}		
		return resultList;
	}
	
	public Collection<MemoryQuestion> getQuestions() {
		return questions;
	}
	
	/*returns current question being considered in the study*/
	public MemoryQuestion returncurrentQuestion (){
		return (questions.get(listLocation));
	}
	
	/** moves to the next question - iterates the location in the list of question objects and the questionindex.*/
	public void iterateQuestion(){
		listLocation++;
		questionIndex++;
	}
	
	/** Takes in user response and whether a hint was used. Evaluates whether answer was correct, assigns points, and logs information about the question response. */
	public void enterAnswer (String userAnswer, String userAnswerBeforeHint, MemoryQuestion.RecallType recallType, Object recallInfo, long millis, boolean hintused, int certainty, int memoryType, Date recency) {
		MemoryQuestion mq = questions.get(listLocation);
		mq.recordUserResponse(userAnswer, userAnswerBeforeHint, recallType, recallInfo, millis, hintused, certainty, memoryType, recency);
	}
	
	/*checks whether the test is done. if it is, it outputs the final log info and does time calculations*/
	public boolean checkForFinish(){
		if (questionIndex > questions.size()){
			stats.endtime = new Date().getTime();
			return true;
		}
		else
			return false;
	}

	public int getQuestionindex() {
		return questionIndex;
	}


	public void setQuestionindex(int questionindex) {
		this.questionIndex = questionindex;
	}

	public Set<String> getTabooCluesSet() {
		return tabooCluesSet;
	}

	public void setTabooCluesSet(Set<String> tabooCluesSet) {
		this.tabooCluesSet = tabooCluesSet;
	}

	public int getListLocation() {
		return listLocation;
	}

	public void setListLocation(int listLocation) {
		this.listLocation = listLocation;
	}

	public List<MemoryQuestion> getQuestion_list() {
		return questions;
	}

	public void setQuestion_list(ArrayList<MemoryQuestion> question_list) {
		this.questions = question_list;
	}
	
	/** writes out csv stats as an encrypted file in RESULTS_DIR/<userid>/filename */
	public void logStats(String filename)
	{
        Indexer.IndexStats stats = archive.getIndexStats();
		StringBuilder statsLog = new StringBuilder();
		Pair<String, String> indexStats = Util.fieldsToCSV(stats, true);
		Pair<String, String> addressBookStats = Util.fieldsToCSV(archive.addressBook.getStats(), true);
		Pair<String, String> studyStats = Util.fieldsToCSV(stats, true);
		Pair<String, String> archiveStats = Util.fieldsToCSV(archive.stats, true);
		statsLog.append("STUDYSTATS-1: " + studyStats.getFirst() + indexStats.getFirst() + addressBookStats.getFirst() + archiveStats.getFirst() + "\n");
		statsLog.append("STUDYSTATS-2: " + studyStats.getSecond() + indexStats.getSecond() + addressBookStats.getSecond() + archiveStats.getSecond() + "\n");
		int idx = 1;
		for (MemoryQuestion mq : this.getQuestions()) {
			Pair<String, String> p = Util.fieldsToCSV(mq.clue.clueStats, true);		
			Pair<String, String> p1 = Util.fieldsToCSV(mq.stats, true);
			if (idx == 1)
				statsLog.append("QUESTIONSTATS-header: " + p.getFirst()  + ',' + p1.getFirst() + "correct answer, user answer, user answer before hint, clue" + "\n");
			statsLog.append("QUESTIONSTATS-2: " + p.getSecond()  + ',' + p1.getSecond() + mq.correctAnswer + "," + mq.userAnswer + "," + mq.userAnswerBeforeHint + "," + mq.clue.clue.replaceAll(",", " ") + "\n");
			idx = idx + 1;
		}
		String RESULTS_DIR = System.getProperty("user.home") + File.separator + "results" + File.separator + this.stats.userid;
		new File(RESULTS_DIR).mkdirs();
		String file = RESULTS_DIR + File.separator + filename; 
		try { CryptoUtils.writeEncryptedBytes(statsLog.toString().getBytes("UTF-8"), file); } 
		catch (UnsupportedEncodingException e) { }
		catch (Exception e) { Util.print_exception("NOC ERROR: encryption failed!", e, log); }
		log.info (statsLog);	
	}
	
	public String toString() { return "Question list with " + (questions != null ? questions.size() : 0) + " questions"; }
}
