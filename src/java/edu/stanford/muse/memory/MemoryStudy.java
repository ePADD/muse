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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.ie.NameInfo;
import edu.stanford.muse.ie.NameTypes;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.Lexicon;
import edu.stanford.muse.util.CryptoUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;
import edu.stanford.muse.xword.ArchiveCluer;
import edu.stanford.muse.xword.Clue;
import edu.stanford.muse.xword.Crossword;

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
	 * @throws IOException */
	public void generateQuestions (Archive archive, Collection<EmailDocument> allDocs, Lexicon lex, int N) throws IOException {
		this.archive = archive;
		if (allDocs == null)
			allDocs = (Collection) archive.getAllDocs();
		
		ArchiveCluer cluer = new ArchiveCluer(null, archive, null, lex);

		// get the top names and name infos
		Map<String, NameInfo> nameMap = NameTypes.computeNameMap(archive, allDocs);
		archive.indexer.stats.nUniqueNamesOriginal = nameMap.size();
		ArrayList<NameInfo> topNameInfos = new ArrayList<NameInfo>(nameMap.values());
		Collections.sort(topNameInfos);
		List<String> topNames = new ArrayList<String>();
		for (NameInfo ni: topNameInfos)
			topNames.add(ni.title);

		// remember, topNames is not canonicalized
		
		// eliminate the taboo answers
		// compile the taboo names, starting from the own names and the xword-taboowords file
		Set<String> ownNames = archive.addressBook.getOwnNamesSet();
		Set<String> tabooNames = Crossword.getTabooTokensFromOwnNames(ownNames);		
		
		String TABOO_FILE = "xword-taboowords.txt"; // in the web-inf/classes dir
		Collection<String> tabooWordsFromFile = Util.getLinesFromInputStream(Crossword.class.getClassLoader().getResourceAsStream(TABOO_FILE), true);
		log.info ("words read from taboo words file " + TABOO_FILE + ":" + tabooWordsFromFile.size());
		
		tabooNames.addAll(tabooWordsFromFile);
		
		topNames = Crossword.removeBadCandidatesAndCap(topNames, tabooNames);
		// remember, topNames is not canonicalized even here

		// todo: remove lol*
		
	    questions = new ArrayList<MemoryQuestion>();
		
		Set<String> tabooCluesSet = new LinkedHashSet<String>();

		for (String name: topNames) {
			
			try {
				if (questions.size() >= (N*2))
					break;
				
				NameInfo ni = nameMap.get(name.toLowerCase().trim().replaceAll(" ", "_"));
	
				if (Util.tokenize(name).size() > 2)
					continue; // these tend to be long org. names or "Mr. XXX YYYY"
				
				// check if it satisfies length constraints
				String nameWithoutSpaces = name.replaceAll("\\s",  "");
				if (nameWithoutSpaces.length() < MIN_ANSWER_LENGTH || nameWithoutSpaces.length() > MAX_ANSWER_LENGTH)
					continue;
	
				// skip anything with non-letter chars, e.g. periods (to elim. words like Mr. Smith)
				if (Util.nLetterChars(nameWithoutSpaces) != nameWithoutSpaces.length())
					continue;
				
				// compute the length descr string
				List<Integer> lengthList = Crossword.convertToWord(name).getSecond();
				String lengthDescr = "";
				if (lengthList.size() > 1)
					lengthDescr += Integer.toString(lengthList.size()) + " words: ";
				
				for (Integer i :lengthList) {
					lengthDescr += i + " characters, ";
				}
				lengthDescr = lengthDescr.substring(0, lengthDescr.length()-2); //subtract the extra comma.
	
				// create the clue
				Clue clueforname = cluer.createClue(name, tabooCluesSet);
				if (clueforname == null)
					continue;
				
				int times = (ni != null) ? ni.times : -1;
				MemoryQuestion mq = new MemoryQuestion(this, name, clueforname, times, lengthDescr);
				questions.add(mq);
				
				tabooCluesSet.add(clueforname.getFullSentence().toLowerCase());
			} catch (Exception e) {
				Util.print_exception("Error while trying to generate clue for name: " + name, e, JSPHelper.log);
			}
		}
		
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
		
		if (questions.size() >= N) {
			questions = dropPrefixSuffixfromSortedList(questions, N);
		}

		int new_size = questions.size();
		
		log.info ("#questions before prefix-suffix elim: " + prev_size + " after: " + new_size);
		
		// assign wikipedia types to the answers
		try {
			assignTypes(questions, nameMap);
		} catch (Exception e) {
			Util.print_exception("Error reading wikipedia categories", e, log);
		}
		
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
			try { mq.clue.clueStats.answerCategory = nameMap.get(mq.correctAnswer.toLowerCase().trim().replaceAll(" ", "_")).type; } catch (Exception e) { Util.print_exception("Error reading types for question: " + mq, e, log); }
	}
	
	public boolean checkQuestionListSize(int N){
		if (questions.size() < N)
			return true;
		else
			return false;
	}
	
	/* Drops terms that contain other terms (prefixes, suffixes, etc.) and trims the questionlist to the target size.*/
	private ArrayList<MemoryQuestion> dropPrefixSuffixfromSortedList (ArrayList<MemoryQuestion> questionlist, int targetSize) {
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
	public void enterAnswer (String userAnswer, String userAnswerBeforeHint, long millis, boolean hintused, int certainty, int memoryType, int recency) {
		MemoryQuestion mq = questions.get(listLocation);
		mq.recordUserResponse(userAnswer, userAnswerBeforeHint, millis, hintused, certainty, memoryType, recency);
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
		StringBuilder statsLog = new StringBuilder();
		Pair<String, String> indexStats = Util.fieldsToCSV(archive.indexer.stats, true);
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
