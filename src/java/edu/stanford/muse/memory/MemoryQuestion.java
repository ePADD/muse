package edu.stanford.muse.memory;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.xword.Clue;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;
import java.util.Date;

public class MemoryQuestion implements Comparable<MemoryQuestion>, java.io.Serializable {
	private static final long serialVersionUID = 1L;
	public static Log log = LogFactory.getLog(MemoryStudy.class);

	MemoryStudy study; /* link back to full study for details about user id etc */
	
	public Clue clue;
	public String correctAnswer;
	public String type;
	public String lengthDescr;
	public String length;

	public String userAnswerBeforeHint, userAnswer;
	public UserAnswerStats stats;
    public enum RecallType{
        Nothing,Context,TipOfTongue,UnfairQuestion
	}
	
	static public class UserAnswerStats implements java.io.Serializable {
        //comment by @vihari: Why are some fields marked public and others not? I don't see a pattern or a need for that.
		public String uid;
		public int num;
		private static final long serialVersionUID = 1L;
		
		public int nMessagesWithAnswer; // # messages in which the answer appears at least once		
		public long millis = -1; // millisecs from when the question was shown to submit button clicked
		public boolean hintused; // was hint button clicked? (can be set only if hint button was shown, i.e. millis must be > millis to show hint)
		public boolean userAnswerCorrect; // was the answer judged to be correct
		// potentially add edit distance

        //Will be set only if the answer is wrong
        public RecallType recallType;
        //extra info. about the recall type
        public Object recallExtra;

		int certainty = -1;
		int memoryType = -1;
		public Date guessedDate = null;
		public boolean onlyMonthAndYearGuessed = false;

		// stats computed when answer is wrong
		public boolean letterCountCorrect; // (only populated if the answer is wrong)
		public boolean userAnswerPartOfAnyAddressBookName; // (only populated if the answer is wrong)
		int wrongAnswerReason = -1; // only populated if the answer is wrong
		public int nMessagesWithUserAnswer = -1; // original content only
		public int userAnswerAssociationWithCorrectAnswer = -1; // # messages in which the user answer and the correct answer appear together (only populated if the answer is wrong)
		public String toString() { return Util.fieldsToString(this, true); }
	}
	
	/** Constructor is used in and designed for MemoryStudy.generateQuestions */
	public MemoryQuestion(MemoryStudy study, String correctAnswer, Clue clue, int times, String lengthString){
		this.study = study;
		this.correctAnswer = correctAnswer;
		this.clue = clue;
		stats = new UserAnswerStats();
		stats.uid = study.stats.userid; // redundant, but useful in the spreadsheet to have uid with every question
		stats.nMessagesWithAnswer = times;
		lengthDescr = lengthString;
	}
	
	public int compareTo(MemoryQuestion other){
		if ((other.clue.clueStats.finalScore - this.clue.clueStats.finalScore) > 0)
			return 1;
		if ((other.clue.clueStats.finalScore - this.clue.clueStats.finalScore) < 0)
			return -1;
		if ((other.clue.clueStats.finalScore - this.clue.clueStats.finalScore) == 0)
			return 0;
		return 0;
	}
	
	public void setWrongAnswerReason(int o) {
		this.stats.wrongAnswerReason = o;
 	}
	
	public void setQuestionNum(int n) { stats.num = n; } 
	
	/** separates out the blanks and inserts first letter of the blanks. e.g. input: "I went to ______", output: "I went to G _ _ _ _ _ _" */

	public Clue getClueToShow() { 
		if (clue != null)
			return clue;
		else
			return null;
	}
	

    /**
     * @param userAnswer - The user answer when the submit button is clicked
     * @param userAnswerBeforeHint - The user answer before the hint is used
     * @param recallType - The reason why the answer cannot be recalled
     * @param failReason - Depending on the type of failure to give answer, a further explanation or info. about the failure
     * @param millis - Time elapsed before the question is answered
     * @param hintused - indicating if the hint is used
     * @param certainty - A rating on how certain the user is about the answer
     * @param memoryType - A rating on how well the user can recall the context
     * @param guessedDate - The user's guess on when the particular sentence is compiled
	 * */
	public void recordUserResponse(String userAnswer, String userAnswerBeforeHint, MemoryQuestion.RecallType recallType, Object failReason, long millis, boolean hintused, int certainty, int memoryType, Date guessedDate, boolean onlyMonthAndYearGuessed) {
		this.userAnswer = userAnswer;
		this.userAnswerBeforeHint = userAnswerBeforeHint;
		
		this.stats.userAnswerCorrect = isUserAnswerCorrect();
		this.stats.certainty = certainty;
		this.stats.memoryType = memoryType;
		this.stats.guessedDate = guessedDate;
		this.stats.hintused = hintused;
		this.stats.onlyMonthAndYearGuessed = onlyMonthAndYearGuessed;
		this.stats.millis = millis;

		boolean userAnswerPartOfAnyAddressBookName = study.archive.addressBook.isStringPartOfAnyAddressBookName(userAnswer);
		this.stats.userAnswerPartOfAnyAddressBookName = userAnswerPartOfAnyAddressBookName;
		String cAnswer = Util.canonicalizeSpaces(userAnswer.trim().toLowerCase());
		
		stats.letterCountCorrect = (cAnswer.length() == correctAnswer.length());

		if (userAnswer.equals("") || !isUserAnswerCorrect()) {
			// do further lookups on user answer if its wrong
			try {
				Archive archive = study.archive;
				Collection<EmailDocument> docs = archive.convertToED(archive.docsForQuery("\"" + cAnswer + "\"", edu.stanford.muse.index.Indexer.QueryType.ORIGINAL)); // look up inside double quotes since answer may contain blanks
				stats.nMessagesWithUserAnswer = docs.size();
				Collection<EmailDocument> correctAnswerDocs = archive.convertToED(archive.docsForQuery("\"" + correctAnswer.toLowerCase() + "\"", edu.stanford.muse.index.Indexer.QueryType.ORIGINAL)); // look up inside double quotes since answer may contain blanks
				docs.retainAll(correctAnswerDocs);
				stats.userAnswerAssociationWithCorrectAnswer = docs.size();
                this.stats.recallType = recallType;
                this.stats.recallExtra = failReason;
            } catch (Exception e) { Util.print_exception("error looking up stats for incorrect answer", e, log); }
		} 
	}

	public String getBlanksWithNoHintForCorrespondentTest() {
		String correctanswer = this.correctAnswer;
		String blanks = "";

		for (int i = 0; i < correctanswer.length(); i++) {
			//we can reveal the spaces in the answer, else it is very counter-intuitive.
			if (correctanswer.charAt(i) != ' ')
				blanks += "_ ";
			else
				blanks += "  ";
		}

		return blanks;
	}

	public String getBlanksWithHintForCorrespondentTest() {
		String correctanswer = this.correctAnswer;
		String blanks = "";
		boolean showNextLetter = true;
		for (int i = 0; i < correctanswer.length(); i++) {
			char ch = correctanswer.charAt(i);
			// we can reveal the spaces in the answer, else it is very counter-intuitive.
			if (showNextLetter) {
				blanks += ch;
			} else {
				blanks += (Character.isWhitespace(ch) ? " " : "_ ");
			}

			showNextLetter = (Character.isWhitespace(ch));
		}
		return blanks;
	}

	public String getPreHintQuestion() {
		String originalClue = clue.getClue();
		// do some slight reformatting... "______ Dumpty sat on a wall" to "_ _ _ _ _ _ Dumpty sat on a wall"
		String correctanswer = this.correctAnswer;
		String blanksToReplace = "", blanksPlusSpace = "";
		for (int i = 0; i < correctanswer.length(); i++){
			//we can reveal the spaces in the answer, else it is very counter-intuitive.
			if(correctanswer.charAt(i) != ' ')
				blanksPlusSpace += "_ ";
			else
				blanksPlusSpace += "  ";
			blanksToReplace +="_";
		}
		if(originalClue.contains(blanksToReplace))
			return originalClue.replaceAll(blanksToReplace, blanksPlusSpace);
			//some type of questions are not fill in the blank type
		else
			return originalClue + "<p>Email recipient name: " + blanksPlusSpace + "</p>";
	}

	public String getPostHintQuestion() {
        String clueText = clue.getClue();
        String correctanswer = this.correctAnswer;
        String hint = Character.toString(correctanswer.charAt(0));
        hint += " ";
        String blanksToReplace = "_ ";
        for (int i = 1; i < correctanswer.length(); i++) {
            if(correctanswer.charAt(i-1) == ' ')
                hint = hint + correctanswer.charAt(i)+" ";
            else if (correctAnswer.charAt(i)==' ')
                hint = hint + "  ";
            else
                hint = hint + "_ ";
            blanksToReplace += "_ ";
        }

        if (clueText.contains(blanksToReplace)) {
            log.info("Original text: "+clueText+", after replace: "+clue.getClue().replaceAll(blanksToReplace, hint));
            return clue.getClue().replaceAll(blanksToReplace, hint);
        }
        else {
            return clueText+"<p>Email recipient name: "+hint + "</p>";
        }
    }

	/** case and space normalize first */
	public static String normalizeAnswer(String s) {
		return (s == null) ? "" : s.toLowerCase().replaceAll("[\\s\\.]", "");
	}
	
	public boolean isUserAnswerCorrect() {
		String s1 = normalizeAnswer(userAnswer);
		String s2 = normalizeAnswer(correctAnswer);
		return s1.equals(s2);
	}
	
	public String getCorrectAnswer() { 
		if (correctAnswer != null)
			return correctAnswer;
		else
			return null;
	}
	
	public String getUserAnswer(){
		if (userAnswer != null)
			return userAnswer;
		else
			return null;
	}
	
	public String detailsToHTMLString()
	{
		return Util.fieldsToString(clue.clueStats) + stats.nMessagesWithAnswer + "," + Util.fieldsToString(stats);
	}
	
	public String toString() { 	return Util.fieldsToString(this, false) + " " + Util.fieldsToString(stats, false); }

    public static void main(String[] args){
        System.err.println(normalizeAnswer("Dileep A. D"));
    }

}