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
import java.util.stream.Collectors;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.exceptions.ReadContentsException;
import edu.stanford.muse.ie.NameInfo;
import edu.stanford.muse.ie.NameTypes;
import edu.stanford.muse.index.*;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.featuregen.FeatureUtils;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.ner.model.NEType;
import edu.stanford.muse.util.*;
import edu.stanford.muse.xword.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.webapp.JSPHelper;
import org.apache.lucene.queryparser.classic.ParseException;

import javax.mail.Address;

public class MemoryStudy implements Serializable{

	private static final long serialVersionUID = 1L;

	public static Log log = LogFactory.getLog(MemoryStudy.class);
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

            if (cclue.clue != null)
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
        USERS_FILE = System.getProperty("user.home") + File.separator + "results" + File.separator + "users"; // remember to change cryptoutils if you change this

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
        try {
            List<UserStats> users = readUsersFile();
            return (users.size() < codes.size());
        } catch (Exception e) { Util.print_exception(e, log);}
        return false;
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
        return (List<UserStats>) ois.readObject();
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

        Short[] itypes = new Short[]{NEType.Type.BUILDING.getCode(), NEType.Type.PLACE.getCode(), NEType.Type.RIVER.getCode(),
                NEType.Type.ROAD.getCode(), NEType.Type.UNIVERSITY.getCode(), NEType.Type.MOUNTAIN.getCode(), NEType.Type.AIRPORT.getCode(),
                NEType.Type.ISLAND.getCode(), NEType.Type.MUSEUM.getCode(), NEType.Type.BRIDGE.getCode(), NEType.Type.AIRLINE.getCode(), NEType.Type.THEATRE.getCode(),
                NEType.Type.LIBRARY.getCode(), NEType.Type.LAWFIRM.getCode(), NEType.Type.GOVAGENCY.getCode()};
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
                entities.addAll(Arrays.asList(archive.getAllNamesInDoc(doc, true)).stream().filter(n->n.typeScore>CUTOFF)
                        .map(n->n.text).collect(Collectors.toList()));
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
                lengthDescr += Util.pluralize(i, "letter") + ", ";
            }
            lengthDescr = lengthDescr.substring(0, lengthDescr.length()-2); //subtract the extra comma.

            ClueInfo ci = new ClueInfo();
            ci.link = "../browse?term=\"" + fullAnswer + "\"&sort_by=recent&searchType=original";
            ci.lastSeenDate = lastSeenDate;
            ci.nMessages = entityToMessages.get(ce).size();
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

//		log.info("Based on clue score, top answers:");
//		for (MemoryQuestion mq: questions)
//			log.info (mq.correctAnswer + " times= clue=" + mq.clue.clue);

		// now we have up to 2*N questions, sorted by cluescore.
		// drop ones that are prefix/suffix of another, and cap to N
		int prev_size = questions.size();

		int new_size = questions.size();

        //	log.info ("#questions before prefix-suffix elim: " + prev_size + " after: " + new_size);

        int count = 0;
		for (MemoryQuestion mq: questions) {
			mq.setQuestionNum(count++);
		}

		// log the questions as well, just in case we don't get to the final point due to user fatigue or crashes
        logStats("questions.final", false);
    }

    // Compute date intervals, working backwards from latestDate, until earliestDate is covered
    // most recent interval is interval 0.
    private static List<Pair<Date, Date>> computeDateIntervals(Date earliestDate, Date latestDate) {
        int DAYS_PER_INTERVAL = 30;
        List<Pair<Date, Date>> intervals = new ArrayList<Pair<Date, Date>>();
        {
            JSPHelper.log.info("computing time intervals");
            Date closingDate = latestDate;

            JSPHelper.log.info("closing = " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(closingDate));
            if (earliestDate == null || closingDate == null)
                return intervals;

            while (earliestDate.before(closingDate)) {
                Calendar cal = new GregorianCalendar();
                cal.setTime(closingDate);
                // scroll to the beginning of this month
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                Date endDate = cal.getTime();

                // scroll back by DAYS_PER_INTERVAL days
                cal.add(Calendar.DATE, (1 - DAYS_PER_INTERVAL)); // 1- because we want from 0:00 of first date to 23:59 of last date
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
        }
        return intervals;
    }

    public static List<ClueEvaluator> getDefaultEvals()
    {
        List<ClueEvaluator> evals = new ArrayList<>();
        //default tuned params
        evals.add(new ClueEvaluator.LengthEvaluator(new float[]{-100.0f, -20.0f, 0f}));
        evals.add(new ClueEvaluator.EmotionEvaluator(new float[]{20.0f,10.0f,20.0f}));
        evals.add(new ClueEvaluator.NamesEvaluator(new float[]{10.0f}));
        float[] params = new float[]{10.0f, 0.0f, 10.0f, 0.0f, 10f, 10f, 5f};
        List<String[]> lists = new ArrayList<>();
        lists.add("flight, travel, city, town, visit, arrive, arriving, land, landing, reach, reaching, train, road, bus, college, theatre, restaurant, book, film, movie, play, song, writer, artist, author, singer, actor, school".split("\\s*,\\s*"));
        lists.add("from, to, in, at, as, by, inside, like, of, towards, toward, via, such as, called, named, name".split("\\s*,\\s*"));
        lists.add("absorb, accept, admit, affirm, analyze, appreciate, assume, convinced of, believe, consider, decide, dislike, doubt, dream, dream up, expect, fail, fall for, fancy, fathom, feature, feel, find, foresee, forget, forgive, gather, get the idea, get the picture, grasp, hate, have a hunch, have faith in, have no doubt, hypothesize, ignore, image, imagine, infer, invent, judge, keep the faith, lap up, leave, lose, maintain, make rough guess, misunderstand, neglect, notice, overlook, perceive, place, place confidence in, plan, plan for, ponder, predict, presume, put heads together, rack brains, realise, realize, reckon, recognize, regard, reject, rely on, remember, rest assured, sense, share, suppose, suspect, swear by, take at one's word, take for granted, trust, understand, vision, visualize, wonder".split("\\s*,\\s*"));
        lists.add("he,she,i,me,you".split("\\s*,\\s*"));
        lists.add ("husband, wife, partner, spouse, sister-in-law, brother-in-law, mother-in-law, father-in-law, daughter-in-law, son-in-law, fiancé, fiancée, aunt, brother, cousin, daughter, parent, father, dad, grandparent, granddaughter, grandmother, grandfather, grandpa, grandma, grandchild, grandson, mother, mom, nephew, niece, sister, children, child, baby, son, stepdaughter, stepmother, stepson, uncle, boyfriend, girlfriend, batchmate, buddy, colleague, mentor, co-worker, family, flatmate, folks, house-mate, junior, senior, neighbour, neighbor, relative, roommate".split("\\s*,\\s*"));
        lists.add ("happy, alive, understanding, playful, calm, confident, gay, courageous, peaceful, reliable, joyous, energetic, at ease, easy, lucky, liberated, comfortable, amazed, fortunate, optimistic, pleased, free, delighted, provocative, sympathetic, overjoyed, impulsive, clever, gleeful, surprised, satisfied, thankful, frisky, receptive, animated, quiet, accepting, festive, spirited, certain, ecstatic, thrilled, enjoy, enjoyed, relaxed,  satisfied, wonderful, serene, cheerful, bright, sunny, blessed, reassured, elated, jubilant, love, loving, loved, concerned, eager, impulsive, considerate, affected, keen, affectionate, fascinated, earnest, sensitive,  intrigued, intent, anxious, rebellious, devoted, inquisitive, inspired, unique, attracted, determined, dynamic, passionate, excited, tenacious, admiration, engrossed, enthusiastic, hardy, curious, bold, brave, sympathy, daring, optimistic, comforted, drawn, confident, hopeful, amazing, fantastic, wow".split("\\s*,\\s*"));
        lists.add ("angry, depressed, sad, sadly, unfortunate, unfortunately, confused, irritated, lousy, upset, incapable, enraged, disappointed, doubtful, alone, hostile, discouraged, uncertain, paralyzed, insulting, ashamed, indecisive, fatigued, sore, powerless, perplexed, useless, annoyed, embarrassed, inferior, guilty, hesitant, vulnerable, hateful, dissatisfied, shy, unpleasant, miserable, stupefied, offensive, detestable, disillusioned, bitter, unbelieving, despair, aggressive, despicable, skeptical, frustrated, resentful, disgusting, distrustful, distressed, inflamed, abominable, misgiving, woeful, provoked, terrible, pathetic, incensed, tragic, infuriated, sulky, uneasy, pessimistic, tense, fuming, indignant, indifferent, afraid, hurt, fearful, tearful, dull, terrified, tormented, sorrowful, nonchalant, suspicious, deprived, pained, neutral, pained,  grief, alarmed, tortured, anguish, weary, panic, dejected, desolate, bored, nervous, rejected, desperate, preoccupied, scared, injured, worried, offended, unhappy, disinterested, frightened, afflicted, lonely, lifeless, timid, aching, grieved, shaky, victimized, mournful, restless, heartbroken, dismayed, doubtful, agonized, threatened, appalled, cowardly, humiliated, wronged, menaced, alienated, wary".split("\\s*,\\s*"));
        return evals;
    }

    /**
     * given a sentence, returns a new version of it which is lowercased and strips everything except letters and digit from it
     */
    public static String canonicalizeSentence(String sentence) {
        if (sentence == null)
            return null;

        StringBuilder sb = new StringBuilder();
        for (char ch : sentence.toCharArray())
            if (Character.isLetterOrDigit(ch)) {
                sb.append(Character.toLowerCase(ch));
            }
        return sb.toString();
    }

    /** Generates person names tests from the given archive. @throws IOException */
	public void generatePersonNameQuestions(Archive archive, NERModel nerModel, Collection<EmailDocument> allDocs, Lexicon lex, int numClues) throws IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, ParseException {
		this.archive = archive;
		questions = new ArrayList<>();
		ArchiveCluer cluer = new ArchiveCluer(null, archive, nerModel, null, lex);

		tabooCluesSet = new LinkedHashSet<>();
		archive.assignThreadIds();

        List<ClueEvaluator> evaluators = getDefaultEvals();

		List<Document> docs = archive.getAllDocs();
		Multimap<Contact, EmailDocument> contactToMessages = LinkedHashMultimap.create();
		Multimap<Contact, Long> contactToThreadIds = LinkedHashMultimap.create();

		// sort by date
		Collections.sort(docs);

		Date earliestDate = null, latestDate = null;
        Map<Contact, Date> contactToLatestDate = new LinkedHashMap<>();

        // compute earliest and latest date across all messages in corpus
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;

            if (earliestDate == null || ed.date.before(earliestDate))
                earliestDate = ed.date;
            if (latestDate == null || ed.date.after(latestDate))
                latestDate = ed.date;
        }
        JSPHelper.log.info ("===================\nStarting to generate person names memory questions from " + docs.size() + " messages with " + numClues + " questions" + ", earliest date = " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(earliestDate)
                        + " latest date = " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(latestDate));


        Set<Integer> tabooSentenceHashes = new LinkedHashSet<>();

        // create hashes of all sentences seen at least twice (case insensitive, lower cased)
        {
            Set<Integer> hashesSeen = new LinkedHashSet<>();
            for (Document d : docs) {
                String contents = archive.getContents(d, true);
                String cleanedContents = EmailUtils.cleanupEmailMessage(contents);
                SentenceTokenizer st = new SentenceTokenizer(cleanedContents);
                while (st.hasMoreSentences()) {
                    String sentence = st.nextSentence();
                    sentence = canonicalizeSentence(sentence);
                    int hashCode = sentence.hashCode();
                    if (hashesSeen.contains(hashCode)) {
                        tabooSentenceHashes.add(hashCode);
                        log.info ("Marking sentence as taboo: " + sentence);
                    } else
                        hashesSeen.add(hashCode);
                }
            }
        }

        // compute contactToLatestDate that contact has been seen on
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            //discard doc if it is not a sent mail
            if ((ed.sentOrReceived(archive.addressBook) & EmailDocument.SENT_MASK)==0)
                continue;

            for (Contact c: ed.getParticipatingContactsExceptOwn(archive.addressBook)) {
                Date currentLatestDate = contactToLatestDate.get(c);
                if (currentLatestDate == null || currentLatestDate.before(ed.date))
                    contactToLatestDate.put(c, ed.date);
                contactToMessages.put(c, ed);
                contactToThreadIds.put(c, ed.threadID);
            }
		}

		log.info("We are considering " + contactToLatestDate.size() + " contacts");

        Date currentDate = new Date();
        List<Pair<Date, Date>> intervals = computeDateIntervals(earliestDate, currentDate); // go back from current date
        // intervals[0] is the most recent.
        JSPHelper.log.info ("done computing " + intervals.size() + " intervals");
        for (Pair<Date, Date> p: intervals)
            JSPHelper.log.info ("Interval: " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getFirst()) + " - " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getSecond()));

        int cluesPerInterval = (numClues > 0 && intervals.size() > 0) ? (numClues + intervals.size() - 1) / intervals.size() : 0;
        JSPHelper.log.info ("Will try to generate " + Util.pluralize(cluesPerInterval, "questions") + " per interval");

        Multimap<Integer, Contact> intervalToContacts = LinkedHashMultimap.create();

        //nSent is the number of sentences allowed in a clue text
		int nSent = 2;
		for (Contact c: contactToLatestDate.keySet()) {
            Date lastSeenDate = contactToLatestDate.get(c);

            // which interval does this date belong to? we'll assign this contact in that interval in the intervalToContacts map
            int interval = -1;
            Date intervalStart = null, intervalEnd = null;
            {
                int i = 0;
                for (Pair<Date, Date> p : intervals) {
                    intervalStart = p.getFirst();
                    intervalEnd = p.getSecond();

                    if ((intervalStart.before(lastSeenDate) && intervalEnd.after(lastSeenDate)) || intervalStart.equals(lastSeenDate) || intervalEnd.equals(lastSeenDate)) {
                        interval = i;
                        break;
                    }
                    i++;
                }
            }

            if (interval < 0 || interval == intervals.size()) {
                JSPHelper.log.info("What, no interval!? for " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(lastSeenDate));
                continue;
            }

            intervalToContacts.put(interval, c);
        }

        log.info ("Interval information (interval 0 is the most recent):");
        for (int interval = 0; interval < intervals.size(); interval++) {
            Collection<Contact> contacts = intervalToContacts.get(interval);
            int nContactsForThisInterval = (contacts == null) ? 0 : contacts.size();
            log.info ("In interval " + interval + " there are " + Util.pluralize (nContactsForThisInterval, "candidate contact") + " who were last seen in this interval");
        }

        for (int interval = 0; interval < intervals.size(); interval++) {
            Date intervalStart = intervals.get(interval).getFirst();
            Date intervalEnd = intervals.get(interval).getSecond();
            Collection<Contact> candidateContactsForThisInterval = intervalToContacts.get(interval);
            if (candidateContactsForThisInterval == null) {
                log.info("Skipping interval " + interval + " because there are no contacts");
                continue;
            }

            Map<Clue, Contact> clueToContact = new LinkedHashMap<>();
            log.info ("=======\nGenerating questions for interval " + interval);

            outer:
            for (Contact c: candidateContactsForThisInterval) {
                String name = c.pickBestName();
                if (name.length() < 2) // could also check if alphanumberic only
                    continue outer;

                // ignore contact if name does not contain all alphabets. Even a period is not allowed. only space is allowed.
                for (char ch : name.toCharArray()) {
                    if (!Character.isAlphabetic(ch) && !Character.isSpaceChar(ch))
                        continue outer;
                }

                Clue clue = cluer.createPersonNameClue(c, evaluators, nerModel, intervalStart, intervalEnd, nSent, archive, tabooSentenceHashes);
                if (clue != null)
                    clueToContact.put(clue, c);
            }

            List<Clue> clueList = new ArrayList(clueToContact.keySet());
            Collections.sort (clueList);
            List<Clue> selectedClues = new ArrayList<>();
            for (int i = 0; i < cluesPerInterval && i < clueList.size(); i++) {
                selectedClues.add(clueList.get(i));
            }

            log.info ("For interval " + interval + " selected " + selectedClues.size() + " contacts out of " + clueList.size() + " possible candidates.");
//            for (Clue c: clueList)
            //               log.info ("Clue candidate for " + clueToContact.get(c).pickBestName() + " score = " + c.clueStats.finalScore+ " clue is " + c );
            //          for (Clue c: selectedClues)
            //             log.info ("Selected clue: " + clueToContact.get(c).pickBestName() + " score = " + c.clueStats.finalScore+ " clue is " + c);

            for (Clue selectedClue: selectedClues) {
                Contact c = clueToContact.get(selectedClue);
                String name = c.pickBestName();

                List<Integer> lengthList = Crossword.convertToWord(name).getSecond();
                String lengthDescr = "";
                if (lengthList.size() > 1)
                    lengthDescr += Integer.toString(lengthList.size()) + " words: ";

                for (Integer i : lengthList) {
                    lengthDescr += Util.pluralize(i, "letter") + ", ";
                }
                lengthDescr = lengthDescr.substring(0, lengthDescr.length() - 2); //subtract the extra comma.

                ClueInfo ci = new ClueInfo();
                ci.lastSeenDate = contactToLatestDate.get(c);
                ci.nMessages = contactToThreadIds.get(c).size();
                ci.nThreads = contactToThreadIds.get(c).size();

				questions.add(new MemoryQuestion(this,name,selectedClue, 1, lengthDescr));
			}
		}

		log.info (questions.size() + " questions generated");

		log.info ("Top candidates are:");

		// sort q's by clue score
		Collections.sort(questions);

//		log.info("Based on clue score, top answers:");
//		for (MemoryQuestion mq: questions)
//			log.info (mq.correctAnswer + " times= clue=" + mq.clue.clue);

		int count = 0;
		for (MemoryQuestion mq: questions) {
			mq.setQuestionNum(count++);
		}

		// log the questions as well, just in case we don't get to the final point due to user fatigue or crashes
        logStats("questions.final", false);
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
        return questions.size() < N;
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
    public void enterAnswer(String userAnswer, String userAnswerBeforeHint, int recallTypeBeforeHint, int recallType, long millis, boolean hintused, int certainty, int memoryType, int recency, boolean userGaveUp) {
        MemoryQuestion mq = questions.get(listLocation);
        mq.recordUserResponse(userAnswer, userAnswerBeforeHint, recallTypeBeforeHint, recallType, millis, hintused, certainty, memoryType, recency, userGaveUp);
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
    public void logStats(String filename, boolean nullClues) {
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
            if (nullClues)
                mq.clue.clue = null;
            Pair<String, String> p = Util.fieldsToCSV(mq.clue.clueStats, true);
            Pair<String, String> p1 = Util.fieldsToCSV(mq.stats, true);
			if (idx == 1)
				statsLog.append("QUESTIONSTATS-header: " + p.getFirst()  + ',' + p1.getFirst() + "correct answer, user answer, user answer before hint, clue" + "\n");
//			statsLog.append("QUESTIONSTATS-2: " + p.getSecond()  + ',' + p1.getSecond() + mq.correctAnswer + "," + mq.userAnswer + "," + mq.userAnswerBeforeHint + "," + mq.clue.clue.replaceAll(",", " ") + "\n");
            statsLog.append("QUESTIONSTATS-2: " + p.getSecond() + ',' + p1.getSecond() + mq.correctAnswer + "," + mq.userAnswer + "," + mq.userAnswerBeforeHint + "\n");
            idx = idx + 1;
		}
		String RESULTS_DIR = System.getProperty("user.home") + File.separator + "results" + File.separator + this.stats.userid;
		new File(RESULTS_DIR).mkdirs();
		String file = RESULTS_DIR + File.separator + filename; 
		try { CryptoUtils.writeEncryptedBytes(statsLog.toString().getBytes("UTF-8"), file); } 
		catch (UnsupportedEncodingException e) { Util.print_exception(e, log); }
		catch (Exception e) { Util.print_exception("NOC ERROR: encryption failed!", e, log); }
		log.info (statsLog);	
	}
	
	public String toString() { return "Question list with " + (questions != null ? questions.size() : 0) + " questions"; }
}
