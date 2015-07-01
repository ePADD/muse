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

import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;

import edu.stanford.muse.exceptions.ReadContentsException;
import edu.stanford.muse.ie.NameInfo;
import edu.stanford.muse.ie.NameInfoMemory;
import edu.stanford.muse.ie.NameTypes;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Lexicon;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;

/** terminology: word is a canonicalized word, without spaces that is used for placing letters in the grid. token is a single token within such a word.
 * term/answer is the actual answer (which could be a phrase). typically all of these are canonicalized to lower case, multiple spaces removed, etc.
 */
public class Crossword implements Serializable {
	private static final long serialVersionUID = 1L;
    private static Log log = LogFactory.getLog(Crossword.class);
    public static final char STOP = '-', EMPTY = 0; // special chars
	private static final int MAX_ANSWER_WORDS = 10000; // we'll only consider the top 10000 words for placement in the grid
	
	/* below is the core state that is part of a generated crossword. 
	 * we explicitly keep temporary state in other places such as the cluer and the placer.
	 * Fragile! these fields in json format are relied upon by the UI */
	public int w, h; // w should be = h
	public char[][] box; // chars in each box
	public char[][] current_state; // current state of chars; not used in this class, but this field is used by javascript in the UI
	public int[][] clueNums;
	public List<Integer> boxToPlacedWordsIdxs[][];
	public List<Word> placedWords;
	public List<String> tags = new ArrayList<String>();
	public String sortKey;
	public int level;
	public boolean levelsExhausted = true; // xword manager will set this if we do have levels. if running without xword manager, we don't really have levels.
	public boolean haveMessages = true;
	public boolean placeAll = false;
	public String id; // the id with which this xword can be retrieved

	/** these could also be transient, but no harm keeping some state about the generation params */
	public int MAX_WORD_LENGTH = 12;
	public int MIN_WORD_LENGTH = 3;

	/** imp: these transient fields will not be serialized when we emit the xword json */
	transient public List<String> candidateWords;
	transient public Map<String, Float> wordToWeight;
	transient public Map<String, String> wordToOriginalTerm = new LinkedHashMap<String, String>();

	/* cluer */
	transient public Set<String> sentencesUsedAsClues = new LinkedHashSet<String>();
	transient public Map<String, NameInfo> wordToNameInfo;
	transient public Cluer cluer;
	transient public Placer placer;
		
	public Crossword () { } // need a no-arg constr for gson deserialization

	public Crossword (int w, int h)
	{
		this.w = w;
		this.h = h;
		reset();
	}

	// separate reset function because the placer can call this seperately
	public void reset()
	{
		box = new char[w][h]; // coords start from 0, 0 in top left.
		current_state = new char[w][h]; // coords start from 0, 0 in top left.
		for (int i = 0; i < w; i++)
			for (int j = 0; j < h; j++)
				box[i][j] = EMPTY;
		boxToPlacedWordsIdxs = new List[w][h];
		placedWords = new ArrayList<Word>();
	}

	public static boolean stopOrEmpty(char c)
	{
		return c == EMPTY || c == STOP;
	}
	
	/** trim the trailing rows/cols of the crossword if applicable. but still keep it square */
	public void trimCrosswordSymmetrical()
	{
		if (this.h != this.w)
			return; // no trimming if not a symmetrical grid
		
		int MIN_SIZE_FOR_TRIM = 1;
		int size = this.h;

		outer:
		while (size > MIN_SIZE_FOR_TRIM)
		{
			// check for empty last row
			for (int i = 0; i < w; i++)
				if (!stopOrEmpty(box[i][size-1]))
					break outer;
			// check for empty last col
			for (int i = 0; i < h; i++)
				if (!stopOrEmpty(box[size-1][i]))
					break outer;
			--size;
		}
		if (size != this.h)
			log.info ("Trimming crossword size from " + this.h + " to " + size);
		this.h = this.w = size;
	}

	public void trimCrossword()
	{
		int origW = w, origH = h;
		
		int MIN_SIZE_FOR_TRIM = 1;
		outer1:
		while (h > MIN_SIZE_FOR_TRIM)
		{
			// check for empty last row
			for (int i = 0; i < w; i++)
				if (!stopOrEmpty(box[i][h-1]))
					break outer1;
			--h;
		}

		outer2:
		while (w > MIN_SIZE_FOR_TRIM)
		{
			// check for empty last col
			for (int i = 0; i < h; i++)
				if (!stopOrEmpty(box[w-1][i]))
					break outer2;
			--w;
		}
		
		log.info ("Trimming crossword size from " + origW + "x" + origH + " to " + this.w + "x" + this.h);
	}

	public String getOriginalAnswer(String w)
	{
		return wordToOriginalTerm.get(w);
	}

	/** commits word to grid and updates data structs. warning: changes candidateWords, outstanding iterators will have to be reset. */
	public void commitWord(Word W, boolean eliminateSimilarWords)
	{
		placedWords.add(W);
		candidateWords.remove(W.word);
		if (W.clue != null)
			sentencesUsedAsClues.add(W.clue.getFullSentence());
		
		// remove any other candidates that have a prefix/suffix relationship with the newly placed word
		if (eliminateSimilarWords)
		{
			for (Iterator<String> it2 = candidateWords.iterator(); it2.hasNext(); )
			{
				String cw = it2.next();
				if (W.word.startsWith(cw) || cw.startsWith(W.word) || W.word.endsWith(cw) || cw.endsWith(W.word))
				{
					log.info("Removing answer word: " + cw);
					it2.remove();
				}
			}
		}		
//		updateBoxToPlacedWordsIdxs(W, placedWords.size()-1);

		// tell cluer that we're committing the word so it can keep track of clue cache etc.
		cluer.commitWord(W);	
	}
	
	public void markStop (int x, int y)
	{
		if (x < 0 || x >= w || y < 0 || y >= h)
			return;
		box[x][y] = STOP;
	}
	
	// check if word has only alpha's and num's.
	private static boolean wordHasOnlyLetters(String w)
	{
		for (char c: w.toCharArray())
			if (!Character.isLetter(c))
				return false;
		return true;
	}
	
	/** is the string s a prefix or suffix of any of the given words? */
	private static boolean prefixOrSuffixOfAny(Collection<Word> words, String s)
	{
		if (s == null)
			return true;
		
		for (Word w: words)
			if (w.word.startsWith(s) || s.startsWith(w.word))
			{
				log.info ("prefix:suffix relationship between " + s + " - and word in collection: " + w.word);
				return true;
			}
		return false;
	}
    
	/** looks up all names in the given docs in the names archive and assigns types to them. key in returned map has _ instead of spaces */
	public void assignTypes (List<String> names) throws IOException
	{
		// compute name -> nameInfo
		wordToNameInfo = new LinkedHashMap<String, NameInfo>();
		// we're assuming no dups in the names
		for (String name: names)
		{
			String word = convertToWord(name).getFirst();
			String cTitle = name.trim().toLowerCase().replaceAll(" ", "_"); // canonical wp title			
			NameInfo ni = wordToNameInfo.get(word);
			ni = new NameInfo(cTitle);
			ni.score = 1;
			Float F = wordToWeight.get(word);
			if (F != null) {
				ni.score = F;
				ni.times = (int) F.floatValue();
			}
			ni.snippet = "";
			ni.word = word;
			ni.originalTerm = wordToOriginalTerm.get(name);
			wordToNameInfo.put(word, ni);
		}
		
		// assign types to all the names
		NameTypes.readTypes(wordToNameInfo);
	}
	
	private void assignTypesAndResortCandidates() throws IOException
	{
		log.info ("Assigning types to " + candidateWords.size() + " candidates and will re-sort");
		// compute all prefixes of all lengths, including the entire words themselves
		List<String> originalTerms = new ArrayList<String>();
		for (String s: candidateWords)
		{
			String orig = wordToOriginalTerm.get(s);
			if (orig != null)
				originalTerms.add(orig);
		}
		
		assignTypes(originalTerms);

		// give a boost of 3 to all names whose type is recognized 
		for (NameInfo ni : wordToNameInfo.values())
			if (!"notype".equals(ni.type))
			{
				Util.softAssert (ni.score > 0.0f);
				ni.score += 3.0f;				
			}

		List<NameInfo> nis = new ArrayList<NameInfo>(wordToNameInfo.values());
		Collections.sort (nis, new Comparator<NameInfo>() {
		    public int compare(NameInfo n1, NameInfo n2) {
				return n2.score > n1.score ? 1 : (n2.score < n1.score ? -1:0);
			}
		});
		
		List<String> newCandidates = new ArrayList<String>();
		for (NameInfo ni: nis)
			newCandidates.add(ni.word);
		candidateWords = newCandidates;
	}

	public void printCandidates()
	{
		try {
			for (String s: candidateWords)
			{
				String o = wordToOriginalTerm.get(s);
				String type = " ";
				float score = 0.0f;
				if (o != null)
				{
					String o1 = o.replaceAll(" ", "_");
					if (wordToNameInfo != null)
					{
						NameInfo ni = wordToNameInfo.get(o1);
						if (ni != null)
						{
							type = " Type: " + ni.type;
							score = ni.score;
						}
					}
				}
				log.info ("Candidate: " + s + " (original term: " + o + ", score:" + score + " " + type + ")");
			}
		} catch (Exception e) { log.warn ("Exception while printing candidates, may not have been able to assign types!"); Util.print_exception(e, log); }	
	}
	
	/** places candidateWords on the grid and generates clues 
	 * @throws ReadContentsException 
	 * @throws ClassNotFoundException 
	 * @throws GeneralSecurityException 
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws LockObtainFailedException 
	 * @throws CorruptIndexException */
	public void generateGrid(boolean doSymmetric) throws UnplaceableException, CorruptIndexException, LockObtainFailedException, IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException 
	{
		if (!placeAll)
			assignTypesAndResortCandidates();
		printCandidates();
		log.info ("Placing " + candidateWords.size() + " candidates");
		placedWords = new ArrayList<Word>();
		
		placer.placeWordsAndCreateClues(doSymmetric, cluer);
		
		assignClueNums();
		Collections.sort(placedWords);
		recomputeBoxToPlacedWordIdxs();
	}

	/** this is a map going from x, y to placed word idx's */
	public void recomputeBoxToPlacedWordIdxs()
	{
		boxToPlacedWordsIdxs = new List[w][h];

		// compute boxToPlacedWordsIdxs, make sure to do this after sorting placedWords
		for (int p = 0; p < placedWords.size(); p++)
		{
			Word W = placedWords.get(p);
			updateBoxToPlacedWordsIdxs(W, p);
		}
	}
	
	private void updateBoxToPlacedWordsIdxs(Word w, int p)
	{
		// add p to boxToPlacedWordsIdxs for all the letters for this word;

		int ix = w.acrossNotDown ? 1 : 0;
		int iy = !w.acrossNotDown ? 1 : 0;

		int x = w.x, y = w.y;	
		for (int i = 0; i < w.word.length(); i++, x += ix, y += iy)
		{
			// add this word to the list of words that span this box
			List<Integer> wordsForThisBox = boxToPlacedWordsIdxs[x][y];
			boolean insertAtBegin = false;
			if (wordsForThisBox == null)
				wordsForThisBox = boxToPlacedWordsIdxs[x][y] = new ArrayList<Integer>();
			else
			{
				// if this box belongs to 2 words, and the new word starts from this box, but the existing word did not, kill the existing word
				// because if we click on this box, we want it to fill the new word. 
				// update: we don't remove it but we will place at first
				Word existingWord = placedWords.get(wordsForThisBox.get(0));
				boolean thisBoxStartsExistingWord = existingWord.x == x && existingWord.y == y;
				boolean thisBoxStartsNewWord = w.x == x && w.y == y;
				if (thisBoxStartsNewWord && !thisBoxStartsExistingWord)
					insertAtBegin = true; // wordsForThisBox.clear(); // remove the existing word, because the current word overrides it
			}
			if (insertAtBegin)
				wordsForThisBox.add (0, p);
			else
				wordsForThisBox.add(p);
		}	
	}

	/** assign clue numbers going from L->R and Top->Bottom in the grid */
	public void assignClueNums()
	{
		// mark all boxes+dir that are the starting of a placed word
		Word[][][] mark = new Word[w][h][2];
		for (Word w: placedWords)
			mark[w.x][w.y][w.acrossNotDown ? 0 : 1] = w;
		
		/** assign clue numbers going from L->R and Top->Bottom in the grid */
		clueNums = new int[w][h];
		int count = 1;
		for (int i = 0; i < h; i++)
			for (int j = 0; j < w; j++)
			{
				boolean assigned = mark[j][i][0] != null || mark[j][i][1] != null;
				
				if (assigned && mark[j][i][0] != null)
					mark[j][i][0].setClueNum(count);
				if (assigned && mark[j][i][1] != null)
					mark[j][i][1].setClueNum(count);
				if (assigned)
				{
					clueNums[j][i] = count;
					count++;
				}
			}
	}
	
	/** returns the word without spaces, after canonicalization (replaces periods and hyphens with spaces), along with the breakup of the words in the term */
	public static Pair<String,List<Integer>> convertToWord(String s)
	{
		s = cleanCandidate(s);
		StringTokenizer st = new StringTokenizer(s);
		String trimmedWord = "";
		List<Integer> list = new ArrayList<Integer>();
		while (st.hasMoreTokens())
		{
			String token = st.nextToken();
			trimmedWord += token;
			list.add(token.length());
		}
		return new Pair<String, List<Integer>>(trimmedWord, list);
	}
	
	/** replace periods and hyphens, canonicalize spaces */
	private static String cleanCandidate(String t)
	{
		t = t.replaceAll("\\.", " ");
		t = t.replaceAll("-", " ");
		t = t.replaceAll("_", " ");
		t = Util.canonicalizeSpaces(t);
		t = t.trim().toLowerCase();
		return t;
	}
	
	/** removes the terms that are bad as answers. Caps to MAX_ANSWER_WORDS */
	public static List<String> removeBadCandidatesAndCap(List<String> terms, Set<String> tabooTokens)
	{
		log.info ("removing bad candidates with " + tabooTokens.size() + " taboo words");
		
		// create list of candidates words 
		List<String> result = new ArrayList<String>();
		int count = 0;

		next_term:
		for (String term: terms) 
		{			
			// any token in term matches any taboo token, and the entire term is nuked
			Set<String> tokens = cleanAndTokenize(term);
			for (String token: tokens)
			{
				if (tabooTokens.contains(token))
					continue next_term;
				if (DictUtils.hasOnlyCommonDictWords(token)) // hasOnlyCommonDictWords is robust for case
					continue next_term;
				if (DictUtils.fullDictWords.contains(token))
					continue next_term;
			//	if (IndexUtils.topNames.contains(token))
			//		continue next_term;
			}

			result.add(term);
			count++;
			if (count >= MAX_ANSWER_WORDS) {
				log.info ("reached limit of " + MAX_ANSWER_WORDS + " dropping the rest");
				break;
			}
		}
		log.info ("after removing taboo candidates, answer terms: " + result.size() + " original terms: " + terms.size());
		return result;
	}
	
	public static ArrayList<NameInfoMemory> removeBadCandidatesandCap_ReturnsNameInfo(ArrayList<NameInfoMemory> terms, Set<String> tabooTokens){
		log.info ("removing bad candidates with " + tabooTokens.size() + " taboo words");
		
		// create list of candidates words 
		ArrayList<NameInfoMemory> result = new ArrayList<NameInfoMemory>();
		next_term:
		for (NameInfoMemory term: terms) 
		{			
			// any token in term matches any taboo token, and the entire term is nuked
			Set<String> tokens = cleanAndTokenize(Util.canonicalizeSpaces(term.title));
			System.out.println(tokens);
			for (String token: tokens)
			{
				if ((tabooTokens.contains(token))||(tabooTokens.contains(token.toLowerCase())))
					continue next_term;
				//if (IndexUtils.topNames.contains(token))
					//continue next_term;
				if (DictUtils.hasOnlyCommonDictWords(token)) // hasOnlyCommonDictWords is robust for case
					continue next_term;
				if (DictUtils.fullDictWords.contains(token))
					continue next_term;
			}

			result.add(term);
		}
		log.info ("after removing taboo candidates, answer terms: " + result.size() + " original terms: " + terms.size());
		return result;
	}
	
	public static Set<String> getTabooTokensFromOwnNames(Set<String> ownNames)
	{
		Set<String> result = new LinkedHashSet<String>();
		for (String n: ownNames)
			result.addAll(cleanAndTokenize(n));
		return result;
	}
	
	/** converts the term to a "word" and tokenizes it, including the original string. e.g. "sudheendra hangal" => Set {"sudheendra hangal", "sudheendra", "hangal"} */
	public static Set<String> cleanAndTokenize(String s)
	{
		Set<String> result = new LinkedHashSet<String>();
		// e.g. n = "sudheendra hangal" returns pair <"sudheendrahangal", [10,6]>
		Pair<String, List<Integer>> p = convertToWord(s);
		String word = p.getFirst();
		result.add(word); // add the full name
		
		// extract individual words from the word lens, e.g. pair <"sudheendrahangal", [10,6]> -> ["sudheendra", "hangal"]
		int prev = 0;
		for (Integer I : p.getSecond())
		{
			result.add(convertToWord(word.substring(prev, prev+I)).getFirst()); // add the tokens
			prev = prev+I;
		}
		return result;
	}
	

	
	public static void sortCandidateWordsByLength(List<String> words)
	{
		Collections.sort(words, new Comparator<String>() { 
			public int compare(String s1, String s2) {
				return s1.length() - s2.length();
			}
		});
	}

	public static void computeLetterFreqs(List<String> words, Map<String, Float> weights)
	{
		Map<Character, Integer> charFreqs = new LinkedHashMap<Character, Integer>();
		for (String w: words)
		{
			for (char c: w.toCharArray())
			{
				Integer I = charFreqs.get(c);
				if (I != null)
					charFreqs.put(c, I+1);
				else
					charFreqs.put(c, 1);
			}
		}
		
		List<Pair<Character, Integer>> pairs = Util.sortMapByValue(charFreqs);
		for (Pair<Character, Integer> p: pairs)
			log.info ("freq of " + p.getFirst() + " is " + p.getSecond());
		
		final Map<String, Float> costMap = new LinkedHashMap<String, Float>();
		for (String w: words)
		{
			float cost = 0;
			for (char c: w.toCharArray())
			{
				int count = charFreqs.get(c);
				cost += 1.0/count;
			}
			costMap.put(w, cost);
		}
		
		List<Pair<String, Float>> fpairs = Util.sortMapByValue(costMap);
		for (Pair<String, Float> p: fpairs)
			log.info ("cost of " + p.getFirst() + " is " + p.getSecond() + " (weight: " + (weights != null ? weights.get(p.getFirst()) : "?") + ")");
		
		Collections.sort(words, new Comparator<String>() { 
			public int compare(String s1, String s2) {
				return (costMap.get(s2) - costMap.get(s1)) > 0 ? 1 : -1;
			}
		});
	}
	
	public String toString()
	{
		return placer.toString() + "\n" + stats();
	}
	
	public String toHTMLString(boolean printAnswers) 
	{
		StringBuilder sb = new StringBuilder();
		for (int j = 0; j < h; j++)
		{
			for (int i = 0; i < w; i++)
			{
				char c = box[i][j];
				String s = "_", css_class = "empty";
				if (c == EMPTY)
					css_class = "empty";
				else if (c == STOP)
					css_class = "stop";
				else
				{
					s = printAnswers ? Character.toString(c) : "_";
					css_class = "letter";
				}
				String clueNumSpan = "";
				if (clueNums[i][j] != 0)
					clueNumSpan = "<span class=\"cluenum-in-box\">" + clueNums[i][j] + "</span>";
				sb.append ("<div class=\"box " + css_class + "\">" + clueNumSpan + s + "</div>");
			}
			sb.append("<div class=\"endRow\"></div>");
		}
		return sb.toString();
	}

	public Set<Word> getDisconnectedWords()
	{
		Set<Word> result = new LinkedHashSet<Word>();
		outer:
		for (Word W: placedWords)
		{
			int incrX = W.acrossNotDown ? 1:0;
			int incrY = W.acrossNotDown ? 0:1;
			
			for (int x = W.x, y = W.y, c = 0; c < W.word.length(); x += incrX, y += incrY, c++)
			{
				if (boxToPlacedWordsIdxs[x][y] == null)
					continue; // SHOULD NOT HAPPEN!
				if (boxToPlacedWordsIdxs[x][y].size() > 1)
					continue outer;
			}
			result.add(W);
		}
		return result;
	}
	
	public String stats() 
	{
		verify();
		
		int count = 0; 
		for (char[] col: box)
			for (char c: col)
				if (c != STOP && c != EMPTY)
					count++;
		int pct_filled = (w == 0 || h == 0) ? 0 : (int) (100 * count)/(w*h);		
//		return count + "/" + (w*h) + " boxes filled (" + pct_filled + "%)";
		
		int sum = 0, nIntersectingBoxes = 0;
		for (int i = 0; i < boxToPlacedWordsIdxs.length; i++)
			for (int j = 0; j < boxToPlacedWordsIdxs[0].length; j++)
				if (boxToPlacedWordsIdxs[i][j] != null)
				{
					sum += boxToPlacedWordsIdxs[i][j].size();
					nIntersectingBoxes++;
				}

		// stats on # squares that are intersecting
		int nWordsWithOver4Intersects = 0, nWordsWith3Intersects = 0, nWordsWith2Intersects = 0, nWordsWith1Intersect = 0;
		for (Word W: placedWords)
		{
			int nIntersectingForW = 0;
			int incrX = (W.acrossNotDown ? 1 : 0);
			int incrY = (W.acrossNotDown ? 0 : 1);
			int ix = W.x, iy = W.y;
			for (int i = 0; i < W.word.length(); i++)
			{
				if (boxToPlacedWordsIdxs[ix][iy] != null)	
					if (boxToPlacedWordsIdxs[ix][iy].size() > 1)
						nIntersectingForW++;
				ix += incrX;
				iy += incrY;
			}
			
			if (nIntersectingForW >= 4) 
				nWordsWithOver4Intersects++;
			else if (nIntersectingForW == 3)
				nWordsWith3Intersects++;
			else if (nIntersectingForW == 2)
				nWordsWith2Intersects++;
			else if (nIntersectingForW == 1)
				nWordsWith1Intersect++;
		}
		
		int disconnectedWords = getDisconnectedWords().size();
		return count + "/" + (w*h) + " squares filled (occupancy:" + pct_filled + "%) " + String.format("%.2f", (sum*1.0/count)) + " words/letter, placed words: " + placedWords.size() + ", intersecting boxes: " + nIntersectingBoxes + ", disconnected: " + disconnectedWords
				+ "\n#words with > 4 intersects: " + nWordsWithOver4Intersects + " 3 intersects: " + nWordsWith3Intersects + " 2 intersects: " + nWordsWith2Intersects + " 1 intersect: " + nWordsWith1Intersect;
	}
	
	public void verify()
	{
		if (sentencesUsedAsClues.size() < placedWords.size())
			log.warn("sentences used as clues: " + sentencesUsedAsClues.size() + " placed words: " + placedWords.size());
	}
	
	/** helper function to write out clues .. consider moving elsewhere as we don't want this class to depend on javax.servlet 
	 * @throws IOException */
	public String clueColumnAsHTML(boolean acrossNotDown, boolean hintsEnabled, boolean includeWordLens) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		for (Word w: placedWords)
		{
			if (w.acrossNotDown == acrossNotDown)
			{
				Clue clue = w.getClue();
				if (clue == null)
					continue;
				
				String wordLensAsHtml = Word.getWordLens(w.getWordLens());
				String hintStr = (hintsEnabled) ? Util.escapeHTML(clue.getHint()) : "";
				if (hintStr == null)
					hintStr = "";
				String awrapper_open = ""; 
				String awrapper_close = "";
				String onclick_handler = "";
				String attr = "clueNum=\"" + w.getClueNum() + "\" direction=\"" + (w.acrossNotDown ? "across":"down") + "\"";
				sb.append (awrapper_open + "<div title=\"" + hintStr + "\" class=\"clueDiv\" " + onclick_handler + " " + attr + ">");
				int cluenum = w.getClueNum();
				String clueNumStr = Integer.toString(cluenum);
				String style = (cluenum < 10) ? " \"style=margin-left:5px\"" : " ";
				
				sb.append ("<div" + style + " class=\"clueNum\">" + clueNumStr + "</div>");
				sb.append ("<div class=\"clue\">");

				String clueStr = clue.getClue();
				String clueAsHtml = Util.escapeHTML(clueStr);			
				sb.append ("<span class=\"clueText\">" + clueAsHtml + "</span>");
				if (includeWordLens)
					sb.append (" <span class=\"wordlens\">" + wordLensAsHtml + "</span>");

				List<String> urls = clue.getURLs();
				if (!Util.nullOrEmpty(urls))
					for (String url: urls) {
						if (Util.is_image_filename(url))
							sb.append (" (Picture)");
						else if (url.indexOf("youtube.com/") >= 0 || url.indexOf("youtu.be/") >= 0)
							sb.append (" (Video)");							
						else
							sb.append (" <a target=\"_blank\" href=\"" + url + "\">Link</a> ");
					}
				
				sb.append ("</div>");
				sb.append ("<div style=\"clear:both\"></div>");
				sb.append ("</div>" + awrapper_close); // answer clue div	}
			}
		}
		return sb.toString();
	}
	
	public String picClueColumnAsHTML(boolean acrossNotDown, boolean hintsEnabled) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		int count = 0;
		for (Word w: placedWords)
		{
			if (w.acrossNotDown == acrossNotDown)
			{
				Clue clue = w.getClue();
				if (clue == null)
					continue;
				
				String hintStr = (hintsEnabled) ? Util.escapeHTML(clue.getHint()) : "";
				String awrapper_open = ""; 
				String awrapper_close = "";
				String onclick_handler = "";
				String attr = "clueNum=\"" + w.getClueNum() + "\" direction=\"" + (w.acrossNotDown ? "across":"down") + "\"";
				sb.append (awrapper_open + "<div style=\"display:inline\" title=\"" + hintStr + "\" class=\"clueDiv\" " + onclick_handler + " " + attr + ">");
				int cluenum = w.getClueNum();
				String clueNumStr = "";
				if (cluenum < 10)
					clueNumStr = "&nbsp;" + Integer.toString(cluenum);
				else
					clueNumStr = Integer.toString(cluenum);
				
				sb.append ("<span style=\"display:inline\" style=\"font-size:9pt\" class=\"clueNum-dummy\">" + clueNumStr + "</span>");
				
				String hint = "";
				if (hintsEnabled && !Util.nullOrEmpty(clue.getHint()))
					hint = clue.getHint();
				else if (!Util.nullOrEmpty(w.word))
					hint = "Starts with " + Character.toUpperCase(w.word.charAt(0)); // default, simple hint
				
				String fullPicURL = clue.getFullPicURL();
				if (Util.nullOrEmpty(fullPicURL))
					fullPicURL = clue.getPicURL();
				
				sb.append ("<a class=\"fancybox\" href=\"" + fullPicURL + "\">");
				String pic = clue.getPicURL();
				if (!Util.nullOrEmpty(pic)) 
					sb.append ("<img title=\"" + hint + "\" src=\"" + pic + "\" height=\"80\"/>");
				sb.append ("</a>");
				sb.append ("</div>" + awrapper_close); // answer clue div	}
				if (++count % 3 == 0)
					sb.append("<br/>");
			}
		}
		return sb.toString();
	}
	
	/** create xword of the given size from possibleAnswers (in decreasing order of preference), with clues drawn from the filtered ids.
	 * if max/minWordLens are -1, the default is used (3, 12). Otherwise, words between min and max word lens (both inclusive) are placed. 
	 * @throws UnplaceableException */
	public static Crossword createCrossword(List<Pair<String, Integer>> possibleAnswersAndWeight, int w, int h, Archive archive, Lexicon lex, Set<String> filteredIds, Set<String> inputTabooWords, boolean doSymmetric, Map<String, Clue> fixedClues, int minWordLen, int maxWordLen, int timeoutMillis) throws CorruptIndexException, LockObtainFailedException, IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException, UnplaceableException
	{		
		int nMessages = ((archive != null) ? archive.getAllDocs().size() : 0);
		if (filteredIds != null)
			nMessages = filteredIds.size();

		long startTimeMillis = System.currentTimeMillis();

		Crossword c = new Crossword(w, h);

		// word len checks
		if (minWordLen > 0)
			c.MIN_WORD_LENGTH = minWordLen;
		if (maxWordLen > 0)
			c.MAX_WORD_LENGTH = maxWordLen;
		// set the max_word_len to the max of c.w and c.h if the dimensions of the crossword are smaller
		c.MAX_WORD_LENGTH = Math.min(Math.max(c.w, c.h), c.MAX_WORD_LENGTH);
		
		List<String> possibleAnswers = new ArrayList<String>();
		c.wordToWeight = new LinkedHashMap<String, Float>();
		c.placeAll = (fixedClues != null);
		c.placer = new Placer(c);
		
		// compute legal possible answers and their weights. if placeAll, no cleaning is done.
		Set<String> ownNameWords = new LinkedHashSet<String>(), tabooWords = new LinkedHashSet<String>();
		int nInputTabooWords = (inputTabooWords != null) ? inputTabooWords.size():0;

		if (!c.placeAll)
		{
			for (Pair<String, Integer> p: possibleAnswersAndWeight) 
			{
				// convert answer to word (without spaces) and check length
				String answer = p.getFirst();
				String word = convertToWord(answer).getFirst();
				if (word.length() < c.MIN_WORD_LENGTH || word.length() > c.MAX_WORD_LENGTH)
					continue;
				
				// some basic sanity checks
				if (word.indexOf('&') >= 0)
					continue;
				if (word.endsWith(".")) // this tends to be names that are recog. along with a period. e.g. "Guil." in Hamlet. These tend to be bad words (could also happen for titles like Mr.?)
					continue;
	
				possibleAnswers.add(answer);
				
				// multiple answers may map to the same word, so add on to the existing weight in this case.
				Float F = c.wordToWeight.get(word);
				float f = (F == null ? 0.0f : F);
				c.wordToWeight.put(word, p.getSecond() + f);
			}
		
			// clean and filter the answers
			if (archive != null && archive.addressBook != null)
				ownNameWords = getTabooTokensFromOwnNames(archive.addressBook.getOwnNamesSet());
	
			tabooWords.addAll(ownNameWords);
			
			if (inputTabooWords != null)
				tabooWords.addAll(inputTabooWords);
			
			String TABOO_FILE = "xword-taboowords.txt"; // in the web-inf/classes dir
			Collection<String> tabooWordsFromFile = Util.getLinesFromInputStream(Crossword.class.getClassLoader().getResourceAsStream(TABOO_FILE), true);
			tabooWords.addAll(tabooWordsFromFile);
			log.info ("words read from taboo words file " + TABOO_FILE + ":" + tabooWordsFromFile.size());
	//		possibleAnswers = c.removeCandidatesWithInvalidLength(possibleAnswers);
			if (!c.placeAll) // no filtering if we've been directed to place all
				possibleAnswers = removeBadCandidatesAndCap(possibleAnswers, tabooWords);
		}
		else
		{
			nInputTabooWords = (inputTabooWords != null) ? inputTabooWords.size() : 0;
			for (Pair<String, Integer> p: possibleAnswersAndWeight) 
			{
				String answer = p.getFirst();
				String word = convertToWord(answer).getFirst();
				possibleAnswers.add(p.getFirst());
				c.wordToWeight.put(word, 1.0f);
			}
		}
	
		String logStr = "Starting crossword generation with " + nMessages + " messages, " + possibleAnswers.size() + " possible answers, " 
				+ tabooWords.size() + " taboo words (input:" + nInputTabooWords + " own name:" + ownNameWords.size() + ": " + Util.join(ownNameWords, ",") + ")";
		log.info(logStr);

		// build up list of candidate words
		Set<String> candidateWordsSet = new LinkedHashSet<String>();
		c.wordToOriginalTerm = new LinkedHashMap<String, String>();
		for (String originalAnswer: possibleAnswers)
		{
			// original answer is what will be used for searching for clues within a sentence
			// the word will be in canonicalized form, e.g. te-yuan, u.s., i.b.m. => "te yuan", "u s", "i b m"
			Pair<String, List<Integer>> p = Crossword.convertToWord(originalAnswer);
			String word = p.getFirst();
			candidateWordsSet.add(word);
			if (c.wordToOriginalTerm.get(word) == null)
				c.wordToOriginalTerm.put(word, originalAnswer); // well in theory, the same word could map to multiple originalAnswer's... like "i.b.m" vs "ibm"
		}
		
		// add words back to the candidate set.
		// stop when total # of letters of words in the candidate set exceeds the # of words in the grid
		c.candidateWords = new ArrayList<String>();
		int totalLength = 0;
		for (String s: candidateWordsSet)
		{
			c.candidateWords.add(s);
			totalLength += s.length() + 2; // the word plus 2 stops
			if (totalLength > 2 * c.w * c.h)
				break;
		}
		
		log.info ("generating grid from " + c.candidateWords.size() + " words " + " size = " + c.w + "x" + c.h);
		
		// place words on grid, creating clues along the way
		// cluer doesn't have to know about placer, but placer needs to know about cluer
		if (fixedClues != null)
			c.cluer = new FixedCluer(c, fixedClues);
		else
			c.cluer = new ArchiveCluer(c, archive, filteredIds, lex);
		
		// now we always have hints
	//	if (fixedClues != null)
	//		c.haveHints = false;
		
		c.generateGrid(doSymmetric);
		c.trimCrossword();
		log.info ("generated grid from " + c.candidateWords.size() + " words " + " size = " + c.w + "x" + c.h + " in " + Util.commatize(System.currentTimeMillis()-startTimeMillis) + "ms");
		log.info (c.toString());

		return c;
	}
	
	public static void main (String args[])
	{
		List<String> ownNameTerms = new ArrayList<String>();
		
		// e.g. n = "sudheendra hangal" returns pair <"sudheendrahangal", [10,6]>
		String n = "sudheendra   - hangal";
		Pair<String, List<Integer>> p = convertToWord(n);
		String word = p.getFirst();
		ownNameTerms.add(word); // add the full name
		
		// extract individual words from the word lens, e.g. pair <"sudheendrahangal", [10,6]> -> ["sudheendra", "hangal"]
		int prev = 0;
		for (Integer I : p.getSecond())
		{
			ownNameTerms.add(word.substring(prev, prev+I)); // add the tokens
			prev = prev+I;
		}
		
		for (String s: ownNameTerms)
			System.out.println (s);
	}
}
