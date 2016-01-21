package edu.stanford.muse.util;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

import edu.stanford.muse.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.index.InternTable;
import edu.stanford.muse.index.MyTokenizer;

public class DictUtils {
	private static Log			log				= LogFactory.getLog(IndexUtils.class);

	/**
	 * some common word sets. static so that all indexers share the same objects
	 */
	public static Set<String>			stopWords		= new HashSet<String>();
	public static Set<String>	commonDictWords	= new HashSet<String>();				// these will be pruned from the output
	public static Set<String>	fullDictWords	= new HashSet<String>();
	public static Set<String>	commonDictWords5000 = new HashSet<String>();
	public static Set<String>	topNames		= new HashSet<String>();				// used for NER
	public static Set<String>	tabooNames		= new LinkedHashSet<String>();			// this will be ignored by NER

	public static Set<String>	bannedWordsInPeopleNames	= new HashSet<String>(), bannedStringsInPeopleNames = new HashSet<String>();	// bannedWords => discrete word; bannedStrings => occurs anywhere in the name
	public static Set<String>	bannedAccountNamesInEmailAddresses	= new HashSet<String>();
	static Set<String>			joinWords					= new HashSet<String>();														// this will be ignored for the indexing
	private static final String	COMMENT_STRING				= "#";

	static {
		try {
			InputStream is = Config.getResourceAsStream("join.words");
			if (is != null) {
				joinWords = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				is.close();
			}

			is = Config.getResourceAsStream("stop.words");
			if (is != null) {
				stopWords = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				is.close();
			}

			is = Config.getResourceAsStream("dict.words");
			if (is != null) {
				commonDictWords = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				is.close();
			}

			is = Config.getResourceAsStream("dict.words.full");
			if (is != null) {
				fullDictWords = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				is.close();
			}

			is = Config.getResourceAsStream("words.5000");
			if (is != null) {
				commonDictWords5000 = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				is.close();
			}

			is = Config.getResourceAsStream("top-names");
			if (is != null) {
				topNames = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				is.close();
			}

			is = Config.getResourceAsStream("taboo-names");
			if (is != null) {
				tabooNames = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				tabooNames.addAll(joinWords);
				tabooNames.addAll(stopWords);
				is.close();
			}

			// banned words and strings in people names (for address book, to avoid noisy names merging unrelated entities)
			is = Config.getResourceAsStream("bannedWordsInPeopleNames.txt");
			if (is != null) {
				bannedWordsInPeopleNames = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				is.close();
			}

			is = Config.getResourceAsStream("bannedStringsInPeopleNames.txt");
			if (is != null) {
				bannedStringsInPeopleNames = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				is.close();
			}

			is = Config.getResourceAsStream("bannedAccountNamesInEmailAddresses.txt");
			if (is != null) {
				bannedAccountNamesInEmailAddresses = readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
				is.close();
			}

		} catch (Exception e) {
			Util.print_exception("Exception reading word lists: ", e, log);
		}
	}

	/* General util method */
	public static Set<String> readStreamAndInternStrings(Reader r)
	{
		Set<String> result = new LinkedHashSet<String>();
		try {
			LineNumberReader lnr = new LineNumberReader(r);
			while (true)
			{
				String term = lnr.readLine();
				if (term == null)
				{
					lnr.close();
					break;
				}
				term = term.trim();
				if (term.startsWith(COMMENT_STRING) || term.length() == 0)
					continue;
				term = canonicalizeMultiWordTerm(term, false); // TOFIX: not really sure if stemming shd be off
				term = InternTable.intern(term);
				result.add(term);
			}
		} catch (IOException e) {
			log.warn("Exception reading reader " + r + ": " + e + Util.stackTrace(e));
		}

		return result;
	}

	public static Set<String> readFileAndInternStrings(String file)
	{
		Set<String> result = new LinkedHashSet<String>();
		try {
			Reader r = null;
			if (file.toLowerCase().endsWith(".gz"))
				r = new InputStreamReader(new GZIPInputStream(new FileInputStream(file)));
			else
				r = new FileReader(file);
			return readStreamAndInternStrings(r);
		} catch (IOException e) {
			log.warn("Exception reading file " + file + ": " + e + Util.stackTrace(e));
		}

		return result;
	}

	public static boolean isJoinWord(String term)
	{
		// sometimes this gets called even when reading joinWords initially, so we need to be defensive
		if (joinWords == null)
			return false;

		return joinWords.contains(canonicalizeTerm(term));
	}

	public static String canonicalizeMultiWordTerm(String term, boolean doStemming)
	{
		//		return canonicalizeMultiWordTerm(term, doStemming, true); // by default, skip join words
		return canonicalizeMultiWordTerm(term, doStemming, false); // by default, skip join words
	}

	// canonicalizes term, may or may not be multi word
	public static String canonicalizeMultiWordTerm(String term, boolean doStemming, boolean skipJoinWords)
	{
		// tokenize, ignoring punctuation
		MyTokenizer st = new MyTokenizer(term); // use mytokenizer because the result of this function will eventually be matched with the content which is parsed with MyTokenizer too
		String result = "";
		while (st.hasMoreTokens())
		{
			String s = st.nextToken();
			if (DictUtils.isJoinWord(s) && skipJoinWords)
				continue;
			String newTerm = canonicalizeTerm(s, doStemming);
			if (newTerm.length() > 0)
			{
				if (result.length() > 0)
					result += " ";
				result += newTerm;
			}
		}
		return result;
	}

	/** for a single word only, use canonicalizeMultiWordTerm for phrases */
	public static String canonicalizeTerm(String term, boolean doStemming)
	{
		if (term.indexOf(" ") >= 0 || term.indexOf("\t") >= 0)
			log.warn("Multi word term used in canonicalizeterm: " + term + " \nUse canonicalizeMultiWordTerm() instead");
		String newTerm = term.toLowerCase();

		// our own version of stemming
		if (doStemming)
		{
			// strip most common suffixes
			if (newTerm.endsWith("s"))
				newTerm = newTerm.substring(0, newTerm.length() - "s".length());
			else if (newTerm.endsWith("ed"))
				newTerm = newTerm.substring(0, newTerm.length() - "ed".length());
			else if (newTerm.endsWith("ing"))
				newTerm = newTerm.substring(0, newTerm.length() - "ing".length());
			else if (newTerm.endsWith("ly"))
				newTerm = newTerm.substring(0, newTerm.length() - "ly".length());
			else if (newTerm.endsWith("ment"))
				newTerm = newTerm.substring(0, newTerm.length() - "ment".length());
		}

		// could do other suffixes: -ness, -ation, -atious, etc.
		if (newTerm.length() >= 2)
			return newTerm;
		else
			return term; // < 2 chars, play it safe and return the original term without modification
	}

	/** stemming on by default */
	public static String canonicalizeTerm(String term) {
		return canonicalizeTerm(term, true);
	}

	public static Set<String> canonicalizeMultiWordTerms(Set<String> set, boolean doStemming)
	{
		Set<String> result = new LinkedHashSet<String>();
		if (set == null)
			return result;
		for (String s : set)
			result.add(canonicalizeMultiWordTerm(s, doStemming));
		return result;
	}

	public static boolean hasOnlyCommonDictWords(String s)
	{
		StringTokenizer st = new StringTokenizer(s);
		while (st.hasMoreTokens())
		{
			String t = st.nextToken().toLowerCase();
			if (!commonDictWords.contains(t))
				return false;
		}
		return true;
	}

    public static boolean hasDictionaryWord(String s){
        String[] words = s.split("\\s+");
        for(String t: words) {
            t = t.toLowerCase();
            if (fullDictWords.contains(t))
                return true;
        }
        return false;
    }

	@SuppressWarnings("unused")
	private boolean isDictionaryWord(String term)
	{
		return commonDictWords.contains(canonicalizeTerm(term));
	}

}
