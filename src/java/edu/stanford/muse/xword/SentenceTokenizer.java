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
package edu.stanford.muse.xword;

import edu.stanford.muse.util.Util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A simple rule-based sentence delimiter. 
 * See http://nlp.stanford.edu/software/tokenizer.shtml for a possible alternative, however, I don't think
 * it handles emoticons. Currently used only for crossword clue generation. */
public class SentenceTokenizer {
	String text, lowerCaseText;
	int nextSentence = 0;
	List<Integer> sentenceStarts = new ArrayList<Integer>(); // index of sentence starts
	List<Integer> sentenceEnds = new ArrayList<Integer>(); // index of sentence end, exclusive
	static Set<Character> delimChars = new LinkedHashSet<Character>();
	static Set<Character> nonSentenceStartChars = new LinkedHashSet<Character>();
	static boolean removeQuotedText = true;
	static {
		if ("false".equals(System.getProperty("muse.removeQuotedText")))
			removeQuotedText = false;
	}
	static {
		delimChars.add('.');
		delimChars.add(';');
		delimChars.add('?');
		delimChars.add('!');
		//delimChars.add(':');
		
		nonSentenceStartChars.add('\n');
		nonSentenceStartChars.add('\r');
		nonSentenceStartChars.add('>');
	}

	/** checks if there is a sentence delim at position i, taking context into account */
	private boolean sentenceDelimAt(int i)
	{
		char c = text.charAt(i);

		if (!delimChars.contains(c))
			return false; // nothing interesting

		// look out for a decimal like 2.30pm
		if (c == '.' && i < text.length()-2 && Character.isDigit(text.charAt(i+1))) 
			return false;

		// look out for emoticons
		if ((c == ':' || c == ';') && i <= text.length()-2)
		{
			char tail_char = text.charAt(i+1); // tail char of the emoticon
			if (tail_char == '-' && i <= text.length()-3)
				tail_char = text.charAt(i+2);
			// tail char has to be one of the following
			if (tail_char == ')' || tail_char == '(' || tail_char == 'D' || tail_char == 'P')
				return false;
		}
		
		// try to catch abbreviations like xxx i.b.m. yyy 
		// catches periods at the start and middle of an abbrev. e.g. i and b	
		if (c == '.' && i < text.length()-2 && text.charAt(i+2) == '.') 
			return false;
		
		// catches the period after m in ibm. the final period is not really a sentence delimiter in that case (hopefully, though it could be if the sentence ends with I.B.M.)
		if (c == '.' && i >= 2 && text.charAt(i-2) == '.')
			return false;

		// catches the period after a single upper-case initial letter, e.g. don't terminate at the stop after U in "Vipin Kumar (U. Minnesota)." 
		// A sentence is unlikely to end with a word of just a single letter. 
		// i know, i know, we won't get "The King and I." correct. but that's ok.
		if (c == '.' && i >= 2 && Character.isUpperCase(text.charAt(i-1)))
			if (!Character.isLetterOrDigit(text.charAt(i-2)))
				return false;

		// catch the period in a middle initial monica s. lam
		if (c == '.' && i >= 5 && Character.isLetter(text.charAt(i-1)) && text.charAt(i-2) == ' ' && Character.isLetter(text.charAt(i-3)) && i <= text.length()-4 && text.charAt(i+1) == ' ' && Character.isUpperCase(text.charAt(i+2)))
			return false;

		// are we at the end of a title, like a sentence ending in Mr. 
		// note: the original text should already be in lowercase
		// a profile showed that this toLowerCase() is extremely expensive
		String prefix = lowerCaseText.substring(0, i); // .toLowerCase();
		return !(c == '.' && (prefix.endsWith("mr") || prefix.endsWith("mrs") || prefix.endsWith("prof") || prefix.endsWith("dr") || prefix.endsWith(" ms") || prefix.endsWith(" st")));

	}
	
	private int skipOverNonSentenceStartChars(int i)
	{
		// nonSentenceStartChars chars are in addition to delims, e.g. \r, \n, and > (in quoted parts of emails)
		while (i < text.length())
		{
			char c = text.charAt(i);
			if (sentenceDelimAt(i) || Character.isWhitespace(c) || nonSentenceStartChars.contains(c))
				i++;
			else
				break;
		}
		return i;
	}
	
	private String removePatternString (String patterntext, String text)
	{
		Pattern linkPattern = Pattern.compile(patterntext + "[^\\s]*(\\s|$)");
		Matcher linkMatcher = linkPattern.matcher(text);
		while (linkMatcher.find()){
			//from start of link, go back to the closest delimiter.
			int linkstartIndex = linkMatcher.start();
			while ((linkstartIndex > 0) && (!sentenceDelimAt(linkstartIndex))){
				linkstartIndex = linkstartIndex - 1;
			}
			int linkendIndex = linkMatcher.end();
			//find end of next sentence.
			while ((linkendIndex < text.length()) && (!sentenceDelimAt(linkendIndex)))
				linkendIndex = linkendIndex + 1;
			//System.out.println("Dropped link in document: " + text.substring(linkMatcher.start(), linkMatcher.end()));
			try{
				if ((linkendIndex < text.length()) && (linkstartIndex > 0)) //most common case
					text = (text.substring(0, linkstartIndex)) + (text.substring(linkendIndex));
				if ((linkendIndex < text.length()) && (linkstartIndex == 0))
					text = text.substring(linkendIndex);		
				if ((linkendIndex == text.length()) && (linkstartIndex > 0))
					text = text.substring(0, linkstartIndex);
				if ((linkendIndex == text.length()) && (linkstartIndex == 0))
					text = " ";
			}
			catch (Exception e){
				System.out.println("Exception in SentenceTokenizer.removePatternString if-statements.");
				System.out.println("Debug data: (length, linkstart, linkend) " + text.length() + " " + linkstartIndex + " " + linkendIndex);
			}
			linkMatcher = linkPattern.matcher(text); //reassign to reset.
		}
		return text;
	}
		
	public SentenceTokenizer(String str)
	{ 
		// treat all these <header>: lines as a sentence delimiter, otherwise they are merged with the prev. line
		text = str;
		if (removeQuotedText)
			text = text.replaceAll("[\\r\\n][ >]+[^\\r\\n]*[\\r\\n]", "\n\n"); //checks for whitespace then 1 or more ">"
		text = text.replaceAll("\nSubject:", "."); 
		text = text.replaceAll("\nDate:", "."); 
		text = text.replaceAll("\nFrom:", "."); // treat all these subject: lines as a sentence.
		text = text.replaceAll("\nTo:", "."); // treat all these subject: lines as a sentence.
		text = text.replaceAll("\nCc:", "."); // treat all these subject: lines as a sentence.
		text = text.replaceAll("\nBcc:", "."); // treat all these subject: lines as a sentence.
		text = text.replaceAll("\n\n", ".\n\n"); // treat double newlines as a delimiter
		text = text.replaceAll(":\n", ".\n\n"); // useful for things like: Message from XYZ: or XYZ wrote: to avoid pulling those lines into the sentence
		int idx = text.indexOf("\nContent-Type: text/html;"); 
		
		if (idx >= 0)
			text = text.substring(0, idx); // strip everything after this... sometimes the text/plain part also has the text/html tagging along. e.g. see sgh sent message to Darlene, 2/12/2013
		lowerCaseText = text.toLowerCase(); // perf. opt -- keep both upper and lower versions of the strings, avoids repeatedly converting to lower case when detecing delim
		
		String HTTP = "http://";
		String HTTPS = "https://";
		String WWW = "www";
		String FTP = "ftp://";
		String GIT = "git://";
		String SSH = "ssh://";
		text = removePatternString(HTTP, text);
		text = removePatternString(HTTPS, text);
		text = removePatternString(FTP, text);
		text = removePatternString(GIT, text);
		text = removePatternString(SSH, text);
		text = removePatternString(WWW, text); //this comes last to cover missed opportunities.
		
		// find the begin of the first sentence by skipping past non sentence start chars
		int i = skipOverNonSentenceStartChars(0);
		if (i < text.length())
			sentenceStarts.add(i);

		for (; i < text.length(); i++) 
		{
			if (!sentenceDelimAt(i))
				continue;

			// if the delim is repeated, pull it into the sentence.
			// e.g. YAY!!!! should not return "YAY!", but "YAY!!!!" 
			// e.g. WHY??? should not return "WHY?", but "WHY???" 
			char delim = text.charAt(i);
			if (delim == '?' || delim == '!')
				while (i < text.length() && text.charAt(i) == delim)
					i++;
			// we have a delim, mark the end of the current sentence. 
			sentenceEnds.add(i);
			// skip over non sentence start chars
			i = skipOverNonSentenceStartChars(i);
				
			// now is the beginning of new sentence, unless we've reached the end of the line
			if (i < text.length()) // no point starting a new sentence if we're not at the end
				sentenceStarts.add(i);
		}

		if (sentenceEnds.size() < sentenceStarts.size())
			sentenceEnds.add(text.length()); // the ends were not matched with the starts, the last sent must end at the end of s
	}

	public boolean hasMoreSentences() { return nextSentence < sentenceStarts.size(); }

	public String nextSentence() { return nextSentence(false); }

	/** if include delimiter is true, includes one letter beyond the end */
	public String nextSentence(boolean includeDelimiter)
	{
		int start = sentenceStarts.get(nextSentence);
		int end = sentenceEnds.get(nextSentence);
		if (includeDelimiter && end < text.length())
			end++;
		
		String sentence = text.substring(start, end); // substring is inclusive at the begin and exclusive at the end
		nextSentence++;
		// our sentence may still have newlines embedded in it, which should be removed, i think. 
		sentence = sentence.replaceAll("\n", " ");
		sentence = Util.canonicalizeSpaces(sentence);
		return sentence;
	}
	
	public int countSentences() { return sentenceStarts.size(); }

	public static void main (String args[])
	{
        //Sometimes, sentence is being clipped
        //Eg. What do you mean by conceptual terms.Cant "notable type" field of a Freebase node serve your purpose ield can have more than one value in decreasing order of relevance.
		Util.ASSERT(new SentenceTokenizer("Vipin Kumar (U. Minnesota).").countSentences() == 1); // the period after U. should not be a sentence delim
		Util.ASSERT(new SentenceTokenizer("Vipin Kumar (Univ. Minnesota).").countSentences() == 2); // ideally this would count as one sentence, but right now it will be 2
		Util.ASSERT(new SentenceTokenizer("I've talked to Jiwon and Monica, and Jiwon will handle the demo session at \nthe workshop on Thursday.").countSentences() == 1);
		Util.ASSERT(new SentenceTokenizer("Mrs. Robinson is one phrase").nextSentence().equals("Mrs. Robinson is one phrase"));
		Util.ASSERT(new SentenceTokenizer("This is a multi-delim test .?! should be two sentences").countSentences() == 2);
		Util.ASSERT(new SentenceTokenizer("YAY!!!").nextSentence().equals("YAY!!!"));
		Util.ASSERT(new SentenceTokenizer("YAY!?!").nextSentence().equals("YAY!"));
		Util.ASSERT(new SentenceTokenizer("WHY???").nextSentence().equals("WHY???"));
		Util.ASSERT(new SentenceTokenizer("This is a smiley test :) should be one sentence").countSentences() == 1);
		Util.ASSERT(new SentenceTokenizer("This is a colon test : should be one sentence :)").countSentences() == 1);
		Util.ASSERT(new SentenceTokenizer("This is another smiley test :-) should be one sentence").countSentences() == 1);
		Util.ASSERT(new SentenceTokenizer("This is a time test 12:30 should be only one sentence").countSentences() == 1);
		Util.ASSERT(new SentenceTokenizer("This is a decimal test 12.3 should be one sentences").countSentences() == 1);
		Util.ASSERT(new SentenceTokenizer("This is another decimal test 12.305 should be one sentences").countSentences() == 1);
		Util.ASSERT(new SentenceTokenizer("this is an abbrev test xxx i.b.m. yyy").countSentences() == 1);
		Util.ASSERT(new SentenceTokenizer("xxx i.yyy").countSentences() == 2);
		Util.ASSERT(new SentenceTokenizer("Gupta worked at four software and services companies in India before being recruited to IBM. At I.B.M. Mr. Gupta does something").countSentences() == 2);
		Util.ASSERT(new SentenceTokenizer("I've talked to Jiwon and Monica, and Jiwon will handle the demo session at \r\nthe workshop on Thursday. ").countSentences() == 1);
		SentenceTokenizer st = new SentenceTokenizer("Gupta does a lot of work! Gupta does a lot of work again!");
		Util.ASSERT (st.countSentences() == 2);
		st = new SentenceTokenizer("Hi Jiwon, Kanak,\nI'm sick today, so I won't be coming in.\nCould you pls go to Gates-415 a little before 12.45, meet the speaker and help her setup?");
		Util.ASSERT (st.countSentences() == 2);
		Util.ASSERT(st.nextSentence(true).equals("Hi Jiwon, Kanak, I'm sick today, so I won't be coming in."));
		
		st = new SentenceTokenizer("I have an ibook, but I don't think I can use it to project since I'm not carrying the external VGA");
		Util.ASSERT (st.countSentences() == 1);

		SentenceTokenizer st1 = new SentenceTokenizer("\n" +
				"Sanjna Sudan\n" +
				"\n" +
				"Deputy Manager, Communications & Media Relations\n" +
				"\n" +
				"Fellow- Young India Fellowship, 2014-15");
		System.out.println(st1.countSentences());
		System.out.println(st1.nextSentence());
		System.out.println(st1.nextSentence());
		System.out.println(st1.nextSentence());

		System.out.println ("All tests passed!"); 
	}
}
