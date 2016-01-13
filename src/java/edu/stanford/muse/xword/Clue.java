package edu.stanford.muse.xword;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Util;

/* a committed clue, including how its displayed, any url's, any metadata about the clue etc */
public class Clue implements Serializable {
	class MyAddr implements Serializable { public final static long serialVersionUID = 1L; String email, personal; } // little class because Address does not have a default constructor #!#*($. so these fields can't really be relied upon
	
	public final static long serialVersionUID = 8699239120657874242L;
	public class ClueStats implements Serializable {
		public final static long serialVersionUID = 1L;
		final static int version = 1;

		// stats about the answer
		boolean answerPartOfAnyAddressBookName; // is the correct answer part of any name that any contact in the address book has
		int nMessagesWithAnswer = -1, nThreadsWithAnswer = -1; // total # of messages/threads in which the correct answer occurs (original content only). note: this refers to sent messages only
		int daysSinceFirstMention = -1; // days since this answer was first mentioned. note: this refers to sent messages, original content only
		int daysSinceLastMention = -1; // days since this answer was last mentioned. note: this refers to sent messages, original content only
		int[] histogramOfAnswerOccurrence; // occurrence frequency of the answer in each of the last 12 30-day intervals
		public String answerCategory = "none"; // category if name recognized from wikipedia (experimental)
		boolean containsNonSpecificWords; // starts with non-specific words like this, that, however
		boolean nameNotInClue; // answer not recognized as a name in the clue (though its present as a name)
		boolean containsBadName; // contains own or other poor names
		
		// stats about the clue sentence
		int namesInClue = -1; // #names in clue. original content only
		int nSmileys = -1;
		int clueLength = -1; // #chars in clue (incl. spaces)
		int sentenceNumInMessage = -1; // sentence number of this sentence within the message
		int nValidClueCandidates = -1; // total # "reasonable" sentences considered for this answer, in all messages
		String answerNERCategoryInClue = "none"; // category of the name as identified by NER in this sentence 
		
		// stats about the message containing the clue sentence
		int daysOld = -1; // days since this message was written
		public long dateOfMessage = -1; // date this message was written
		int namesInMessage = -1; // #names in message. original content only
		int charsInMessage = -1; // #chars in message. "original content" only. i.e. ignoring quoted and forwarded parts
		int sentencesInMessage = -1; // #sentences in message. "original content" only. i.e. ignoring quoted and forwarded parts
		boolean answerPartOfRecipientName; // is the answer part of any name of any of the recipients of this message
		int nRecipients = -1; // # recipients on the message, not including the sender
		int subjectLength = -1; // length of subject line of the message
		int nMessagesInThread = -1, daysSpannedByThread = -1; // note: this refers to sent messages only
		boolean threadInitiatedByUser; // true if subject line does not start with Re:
		Map<String, Integer> docSentiments;
		
		// scoring stuff
		public float finalScore;
		float sentenceNumBoost; // boost for sentences earlier in the message
		float docSentimentScore; // overall sentiment score of message
		float linesBoost; // boost due to # newlines in the sentence. too many newlines => low score because its lists or some abnormal sentence structure

		float namesScore; // score due to other names being present in the sentence
		float exclamationScore; // score boost due to exclamations 
		float smileyScore; // score boost due to smilies
		float lengthBoost; // score boost due to ideal-ness of sentence length
        float prepositionScore;
        float sigWordScore;
        float refWordScore;
        float pronounScore;
        float noisyThreadScore;
        float timeAnswerScore;
        float questionMarkScore;
        //boost score for when the recipients is above a certain threshold
        float recipientScore;
        //boost score related to number of concversations between two people in a certain interval
        float nMessageScore;
        //time difference between first and last mentions of either answer/corr
        float timeDiff;
        //Reflective words found in the clue
        String refWord = "";
        //first and last mentions of the answer
        Date firstMention, lastMention;

        @Override
        public String toString(){
            return "namesScore = " + namesScore + " exclamationScore = " + exclamationScore + " smileyScore = " + smileyScore + " lengthBoost = " + lengthBoost;
        }
	}
	
	public String refText;

	public long date; 
	MyAddr to[], cc[], bcc[], from[]; 
	String subject;
    //some of the clue scorer functions wants to look at the thread from which the message was fetched
    EmailDocument d;
			
	public transient ClueStats clueStats = new ClueStats();
	public String clue; // actual clue, with the answer blanked out
	public String audioURL = null, picURL = null, fullPicURL = null;
	public List<String> URLs;
	
	public List<String> getURLs() {
		return URLs;
	}
	
	public void setURLs(List<String> uRLs) {
		URLs = uRLs;
	}
	public String getFullPicURL() {
		return fullPicURL;
	}
	public void setFullPicURL(String fullPicURL) {
		this.fullPicURL = fullPicURL;
	}
	public String getAudioURL() {
		return audioURL;
	}
	public void setAudioURL(String audioURL) {
		this.audioURL = audioURL;
	}
	public String getPicURL() {
		return picURL;
	}
	public void setPicURL(String picURL) {
		this.picURL = picURL;
	}

	public String getRefText() {
		return refText;
	}

	public void setRefText(String refText) {
		this.refText = refText;
	}	

	public String getClue() {
		return clue;
	}
	public void setClue(String clue) {
		// canonicalize whitespace
		this.clue = clue;
	}
	String hint;
	public String getHint() {
		return hint;
	}
	public void setHint(String hint) {
		this.hint = hint;
	}		

	String fullSentenceLowerCase; // original sentence, not blanked out
	public String getFullSentence() {
		return fullSentenceLowerCase;
	}
	public void setFullSentence(String fullSentence) {
		this.fullSentenceLowerCase = fullSentence;
	}
	
	String fullSentenceOriginal;
	
	public String getFullSentenceOriginal() {
		return fullSentenceOriginal;
	}
	public void setFullSentenceOriginal(String fullSentenceOriginal) {
		this.fullSentenceOriginal = fullSentenceOriginal;
	}
    public EmailDocument getEmailDocument(){return this.d;}
	
	String url;
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	
	String fullMessage;
	public String getMessage() {
		return fullMessage;
	}
	
	public void clearFullMessage() {
		fullMessage = null;
	}
	
	public Clue () { } // need a no-arg constr for gson deserialization

	public Clue (String clueText, String hint)
	{
		this.clue = clueText;
		this.hint = hint;
		this.fullSentenceLowerCase = "";
	}
	
	private MyAddr[] convertAddressToMyAddr(Address[] addr)
	{
		if (addr == null)
			return null;
		
		MyAddr[] result = new MyAddr[addr.length];
		for (int i = 0; i < addr.length; i++)
		{
			result[i] = new MyAddr();
			if (addr[i] instanceof InternetAddress)
			{
				result[i].personal = ((InternetAddress) addr[i]).getPersonal();
				result[i].email = ((InternetAddress) addr[i]).getAddress();
			}
		}
		return result;
	}
	
	/** note: full = lowercase sentence */
	public Clue (String c, String fullSentenceOriginal, String fullSentenceLowerCase, String h, String u, String m, EmailDocument d) { 
		this.fullSentenceOriginal = fullSentenceOriginal;
		this.fullSentenceLowerCase = fullSentenceLowerCase; this.hint = h; this.url = u; this.fullMessage = m; 
		if (d != null)
		{
			this.clueStats.dateOfMessage = d.date.getTime();
			this.clueStats.daysOld = (int) ((new Date().getTime() - d.date.getTime()) / EmailUtils.MILLIS_PER_DAY); // # millis per day
			// extract the relevant fields from the email doc.
			// gson doesn't like us storing the emaildoc directly inside the clue
			this.to = convertAddressToMyAddr(d.to);
			this.cc = convertAddressToMyAddr(d.cc);
			this.bcc = convertAddressToMyAddr(d.bcc);
			this.from = convertAddressToMyAddr(d.from);
			this.subject = d.description;
            this.d = d;
		}
		
		this.clue = c; 
	}
	
	public String toString() { return clue; }
	public String toFullString() { return Util.fieldsToString(this, false) + " " + Util.fieldsToString(clueStats, false); }
}