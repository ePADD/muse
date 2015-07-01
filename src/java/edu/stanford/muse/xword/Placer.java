package edu.stanford.muse.xword;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;

import edu.stanford.muse.exceptions.ReadContentsException;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;

/** no state that is relevant for the finished xword should be here.... all the state in this class is temp state used for placability calc.
 * general rule: any state that is visible to the UI should not be in this class */
public class Placer {
	private static final long serialVersionUID = 1L;
	private static Log log = LogFactory.getLog(Placer.class);

	private PlacabilityCalculator placabilityCalculator;
	private boolean cantStartFrom[][][];
	protected Crossword c;
	private static final int MAX_TWIN_SEEK_LENGTH = 1000; // how far we'll go down the candidates list (from the beginning, not from the original twin!) to ensure symmetry
	private int w, h; // duplicated for convenience
	private Set<String> unclueableWords = new LinkedHashSet<String>();

	public Placer(Crossword c) 
	{
		this.c = c;
		this.w = c.w;
		this.h = c.h;
		cantStartFrom = new boolean[w][h][2];
        placabilityCalculator = new PlacabilityCalculator();
	}
	
	protected void markCantStartFrom(int x, int y, int b)
	{
		if (!isLegalSquare(x, y))
			return;
		if (log.isDebugEnabled())
			log.debug ("marking can't start from " + x + " " + y + " " + b);
		cantStartFrom[x][y][b] = true;
	}	

	protected void unmarkCantStartFrom(int x, int y, int b)
	{
		if (!isLegalSquare(x, y))
			return;
		if (log.isDebugEnabled())
			log.debug ("marking can't start from " + x + " " + y + " " + b);
		cantStartFrom[x][y][b] = false;
	}	

	public boolean canStartFrom(int x, int y, int b)
	{
		return !cantStartFrom[x][y][b] && c.box[x][y] != Crossword.STOP;
	}	
	
	public boolean isLegalSquare(int x, int y)
	{
		return (x >= 0 && x < w && y >= 0 && y < h);
	}
	
	/** is the given word a solitary word, i.e. it has no intersecting squares so far? */
	private boolean isSolitaryWord(int x, int y, char c, boolean acrossNotDown)
	{
		int incrX = acrossNotDown ? 1 : 0;
		int incrY = acrossNotDown ? 0 : 1;

		// go back as much as possible to find the beginning of the word
		int ix = x, iy = y;
		while (isLegalSquare(ix-incrX, iy-incrY) && !Crossword.stopOrEmpty(this.c.box[ix-incrX][iy-incrY]))
		{
			ix -= incrX;
			iy -= incrY;
			if (this.c.boxToPlacedWordsIdxs[ix][iy] != null && this.c.boxToPlacedWordsIdxs[ix][iy].size() > 1)
				return false;
		}
		
		// scroll forward to find the end of the word
		ix = x; iy = y;
		while (isLegalSquare(ix+incrX, iy+incrY) && !Crossword.stopOrEmpty(this.c.box[ix+incrX][iy+incrY]))
		{
			ix += incrX;
			iy += incrY;
			if (this.c.boxToPlacedWordsIdxs[ix][iy] != null && this.c.boxToPlacedWordsIdxs[ix][iy].size() > 1)
				return false;
		}
		
		return true;
	}
	
	protected boolean willWordFitOnGrid(String word, int x, int y, boolean acrossNotDown)
	{
		int incrX = acrossNotDown ? 1 : 0;
		int incrY = acrossNotDown ? 0 : 1;
		
		// check if word will even fit in the grid
		// also, beyond the end must be empty or stop, can't be a letter
		int endX = x + word.length() * incrX;
		int endY = y + word.length() * incrY;					
		// if it goes beyond the edge of the grid, give up
		if (endX > w || endY > h) // note > not >=
			return false;
		
		// if endX = w or endY = h, we end at the edge, so no need to check 
		
		// if we end before the grid, the box beyond the end of the word must be empty or stop
		if (endX < w && endY < h)
		{
			// if we did reach the end of the board, no need to check this
			if (!Crossword.stopOrEmpty(c.box[endX][endY]))
				return false;
		}
	
		return true;
	}

	private static class PlacabilityInfo { 
		int placabilityLoss = 0; 
		int nIntersects; 
		boolean meetsAnyNonIntersectingWord; 
		public static PlacabilityInfo unplacable = new PlacabilityInfo();
		public String toString() { return "loss: " + placabilityLoss + " nIntersects: " + nIntersects + " meetsNonIntersectingWord: " + meetsAnyNonIntersectingWord; }
	}

	private class PlacabilityCalculator implements java.io.Serializable {
		private static final long serialVersionUID = 1L;

		private static final int PLACABILITY_WINDOW_SIZE = 50;  // for aggreg wordCountsForLetterByPos
		private static final int PLACABILITY_DEPTH = 30;  // for looking ahead while placing the next word only
		private int wordCountsForLetterByPos[][], wordCountsForLetterByPosBackward[][];
		private int wordLenFreqs[]; // # of words with the given length. invariant: when placing all words on the grid, should be 0 after the last word is placed
		transient private ListIterator<String> placabilityWindowIt;

		private PlacabilityCalculator()
		{
			wordCountsForLetterByPos = new int[26][Math.max(w, h)];
			wordCountsForLetterByPosBackward = new int[26][Math.max(w, h)];
			wordLenFreqs = new int[Math.max(h, w)+1];
		}

		private void deleteWordFromPlacabilityWindow(String word)
		{
			int i = 0;
			log.debug ("removing word from placability window: " + word);
			wordLenFreqs[word.length()]--;

			for (char c: word.toCharArray())
			{
				int idx = c - 'a';
				if (idx >= 0 && idx < 26)
				{
					wordCountsForLetterByPos[idx][i]--;
					wordCountsForLetterByPosBackward[idx][word.length()-1-i]--;
					i++;
				}        	
			}
		}

		private void addWordToPlacabilityWindow(String word)
		{
			int i = 0;
			log.debug ("adding word to placability window: " + word);
			wordLenFreqs[word.length()]++;
			for (char c: word.toCharArray())
			{
				int idx = c - 'a';
				if (idx >= 0 && idx < 26)
				{
					wordCountsForLetterByPos[idx][i]++;
					wordCountsForLetterByPosBackward[idx][word.length()-1-i]++;
					i++;
				}
			}
		}

		private void addNextWordToPlacabilityWindow()
		{
			if (placabilityWindowIt.hasNext())
				addWordToPlacabilityWindow(placabilityWindowIt.next());
		}

		private void setupPlacability()
		{
			log.info ("Setting up placability from the beginning with " + PLACABILITY_WINDOW_SIZE + " words");

			// clear the counts
			for (int[] arr: wordCountsForLetterByPos)
				for (int i = 0; i < arr.length; i++)
					arr[i] = 0;
			for (int[] arr: wordCountsForLetterByPosBackward)
				for (int i = 0; i < arr.length; i++)
					arr[i] = 0;

			for (int i = 0; i < wordLenFreqs.length; i++)
				wordLenFreqs[i] = 0;
			
			int count = 0;
			for (String word: c.candidateWords)
			{   
				if (isUnclueable(word))
					continue;
				if (count++ > PLACABILITY_WINDOW_SIZE)
					break;  
				addWordToPlacabilityWindow(word);
			}
			placabilityWindowIt = c.candidateWords.listIterator(count);
		}

		/** how many empty squares before x, y in the given direction? AND the square must have both its adjacent squares free in the orthogonal direction */
		private int squaresFreeBefore(int x, int y, boolean acrossNotDown)
		{
			int incrX = acrossNotDown ? 1:0;
			int incrY = acrossNotDown ? 0:1;
			int ix = x-incrX, iy = y-incrY, freeSquares = 0;
			while (true)
			{
				// let's see if ix, iy is a properly free squares, i.e. it is free and its adjacent squares in the orthogonal direction are also free.
				if (ix < 0 || iy < 0)
					break;
				if (c.box[ix][iy] == Crossword.STOP)
					break;
				else if (c.box[ix][iy] != Crossword.EMPTY)
				{
					freeSquares--;
					break;
				}

				// now check if adj sq are empty. if not, nothing can be put in this square in direction acrossNotDown
				int adj1X = ix-incrY, adj1Y = iy-incrX; // adj sq before
				int adj2X = ix+incrY, adj2Y = iy+incrX; // adj sq after
				if (isLegalSquare(adj1X, adj1Y) && c.box[adj1X][adj1Y] != Crossword.EMPTY && c.box[adj1X][adj1Y] != Crossword.STOP)
					break;
				if (isLegalSquare(adj2X, adj2Y) && c.box[adj2X][adj2Y] != Crossword.EMPTY && c.box[adj2X][adj2Y] != Crossword.STOP)
					break;
					
				freeSquares++;
				ix -= incrX;
				iy -= incrY;
			}
			return freeSquares;
		}

		/** how many empty squares before x, y in the given direction? */
		private int squaresFreeAfter(int x, int y, boolean acrossNotDown)
		{
			int incrX = acrossNotDown ? 1:0;
			int incrY = acrossNotDown ? 0:1;
			int ix = x+incrX, iy = y+incrY, freeSquares = 0;
			while (true)
			{
				if (ix >= w || iy >= h)
					break;
				if (c.box[ix][iy] == Crossword.STOP)
					break;
				else if (c.box[ix][iy] != Crossword.EMPTY)
				{
					freeSquares--;
					break;
				}
				
				// now check if adj sq are empty. if not, nothing can be put in this square in direction acrossNotDown
				int adj1X = ix-incrY, adj1Y = iy-incrX; // adj sq before
				int adj2X = ix+incrY, adj2Y = iy+incrX; // adj sq after
				if (isLegalSquare(adj1X, adj1Y) && c.box[adj1X][adj1Y] != Crossword.EMPTY && c.box[adj1X][adj1Y] != Crossword.STOP)
					break;
				if (isLegalSquare(adj2X, adj2Y) && c.box[adj2X][adj2Y] != Crossword.EMPTY && c.box[adj2X][adj2Y] != Crossword.STOP)
					break;
					
				freeSquares++;
				ix += incrX;
				iy += incrY;
			}
			return freeSquares;
		}

		/** just a debug function */
		private void dumpWordCounts()
		{
			StringBuilder sb = new StringBuilder();
			int nLetters = 0;
			for (int i = 0; i < wordCountsForLetterByPos.length; i++)
			{
				char c = (char) (i + 'a');
				sb.append(c + ": ");
				for (int j = 0; j < wordCountsForLetterByPos[i].length; j++)
				{
					sb.append(wordCountsForLetterByPos[i][j] + " ");
					nLetters += wordCountsForLetterByPos[i][j];
				}
				sb.append("\n");
			}
			
			sb.append("Backward: \n");
			for (int i = 0; i < wordCountsForLetterByPosBackward.length; i++)
			{
				char c = (char) (i + 'a');
				sb.append(c + ": ");
				for (int j = 0; j < wordCountsForLetterByPosBackward[i].length; j++)
				{
					sb.append(wordCountsForLetterByPosBackward[i][j] + " ");
				}
				sb.append("\n");
			}
			sb.append("Total #letters: " + nLetters);
			log.info (sb);
		}

		private int placabilityLoss(String word, int x, int y, boolean acrossNotDown)
		{   
			int ix = x, iy = y;
			int incrX = (acrossNotDown ? 1:0);
			int incrY = (acrossNotDown ? 0:1);

			int wordLoss = 0;
					
			for (char c: word.toCharArray())
			{
				// if (log.isDebugEnabled())
				// sq free before is slightly iffy, should we use squaresFreeBefore-1? cos word can't actually start from squaresFreeBefore squares earlier. 
				// and if its = x or y, then use it as is
				if (Placer.this.c.box[ix][iy] != c) { // if char is already in the grid, no loss due to placing this word
					int squaresFreeBefore = squaresFreeBefore(ix, iy, !acrossNotDown); // sq. free in orthogonal direction before this letter
					int squaresFreeAfter = squaresFreeAfter(ix, iy, !acrossNotDown); // sq. free in orthogonal direction before this letter
	
					int totalFreeSquares = squaresFreeBefore + squaresFreeAfter + 1;
					if (totalFreeSquares >= Placer.this.c.MIN_WORD_LENGTH)
					{
						int placabilityBeforePlacingChar = 0;
						for (int i = 0; i <= totalFreeSquares ; i++)
							for (int idx = 0; idx < 26; idx++)
								if (i < wordCountsForLetterByPosBackward[idx].length)
									placabilityBeforePlacingChar += wordCountsForLetterByPos[idx][i]; // the right way to do this might be to iterate over all letters
		
						int placabilityAfterPlacingCharBwd = 0;
						for (int i = 0; i <= squaresFreeBefore; i++)
						{
							int idx = c - 'a';
							if (idx >= 0 && idx < 26)
								if (i < wordCountsForLetterByPosBackward[idx].length)
									placabilityAfterPlacingCharBwd += wordCountsForLetterByPos[idx][i];
						}
		
						int placabilityAfterPlacingCharFwd = 0;
						for (int i = 0; i <= squaresFreeAfter; i++)
						{
							int idx = c - 'a';
							if (idx >= 0 && idx < 26)
								if (i < wordCountsForLetterByPosBackward[idx].length)
									placabilityAfterPlacingCharFwd += wordCountsForLetterByPosBackward[idx][i];
						}
						int placabilityAfterPlacingChar = (placabilityAfterPlacingCharBwd + placabilityAfterPlacingCharFwd)/2; // /2 cos we may have double counted in the before and after...
						int thisCharLoss =  (placabilityBeforePlacingChar - placabilityAfterPlacingChar);
						if (thisCharLoss < 0)
							thisCharLoss = 0; // shouldn't happen 
						wordLoss += thisCharLoss;						
						if (log.isDebugEnabled()) // && word.equals("adana"))
							log.debug ("loss due to char " + c + " at " + ix + "," + iy + " = " + thisCharLoss + " (" + placabilityBeforePlacingChar + " - (" + placabilityAfterPlacingCharBwd + "+" + placabilityAfterPlacingCharFwd + ")/2, cum = " + wordLoss);
					}
					else 
						log.debug ("skipping loss for " + c + " at " + ix + "," + iy);
				}
			//	log.info ("placability of char " + c + " is " + placabilityForChar + " loss is " + thisCharLoss + " at " + ix + ", " + iy + " sq. free before,after = " + squaresFreeBefore + "," + squaresFreeAfter);
				ix += incrX;
				iy += incrY;
			}
			int avg_loss = (!c.placeAll) ? (10 * wordLoss)/word.length() : wordLoss; // for fixed clues, better to use loss directly instead of per-char loss. bias to top-left
			if (log.isDebugEnabled())
				log.debug ("Placability loss of " + word + " at " + x + ", " + y + " " + (acrossNotDown?"across":"down") + " is " + avg_loss);
			return avg_loss;
		}
	}
	
	/** return info about placability of word at given position */
	private PlacabilityInfo placabilityLossOf(String word, int x, int y, boolean acrossNotDown, boolean mustIntersect)
	{
		if (c.box[x][y] == Crossword.STOP)
			return PlacabilityInfo.unplacable;
		int b = acrossNotDown ? 0 : 1;
		if (!canStartFrom(x, y, b))
			return PlacabilityInfo.unplacable;
		if (!willWordFitOnGrid(word, x, y, acrossNotDown))
			return PlacabilityInfo.unplacable;
	
		int incrX = acrossNotDown ? 1 : 0;
		int incrY = acrossNotDown ? 0 : 1;
		int ix = x, iy = y;

		int sqBeforeX = ix-incrX, sqBeforeY = iy-incrY;
		if (isLegalSquare(sqBeforeX, sqBeforeY) && this.c.box[sqBeforeX][sqBeforeY] != Crossword.EMPTY && this.c.box[sqBeforeX][sqBeforeY] != Crossword.STOP)
			return PlacabilityInfo.unplacable;

		int intersects = 0; // #intersects with existing chars
		boolean meetsAnyNonIntersectingWord = false;
		for (int i = 0; i < word.length(); i++)
		{
			char c = word.charAt(i);
			// bail out if its not empty and not the same as the existing char
			if (this.c.box[ix][iy] != Crossword.EMPTY && this.c.box[ix][iy] != c)
				return PlacabilityInfo.unplacable;

			if (this.c.box[ix][iy] == c)
			{
				meetsAnyNonIntersectingWord |= isSolitaryWord(ix, iy, c, !acrossNotDown); // || because it might be meeting more than one word
				intersects++;
			}
			else
			{
				// if its empty, check whether neighboring squares in orthogonal direction are also empty
				if (!neighboringSquaresStopOrEmpty(ix, iy, !acrossNotDown))
					return PlacabilityInfo.unplacable;
			}
			ix += incrX;
			iy += incrY;
		}
		
		// check if the sq after (where ix, iy are pointing now) is free ... it shouldn't be a letter
		if (isLegalSquare(ix, iy) && this.c.box[ix][iy] != Crossword.EMPTY && this.c.box[ix][iy] != Crossword.STOP)
			return PlacabilityInfo.unplacable;

		if (intersects == 0 && mustIntersect)
		{
			log.debug("can place " + word + " but doesn't intersect with existing chars");
			return PlacabilityInfo.unplacable;
		}
		
		PlacabilityInfo result = new PlacabilityInfo();
		result.placabilityLoss = placabilityCalculator.placabilityLoss(word, x, y, acrossNotDown);
		result.nIntersects = intersects;
		result.meetsAnyNonIntersectingWord = meetsAnyNonIntersectingWord;
		return result;
	}

	/** returns if the neighboring squares in the given direction are stop or empty */
	protected boolean neighboringSquaresStopOrEmpty(int x, int y, boolean acrossNotDown)
	{
		int incrX = acrossNotDown ? 1 : 0;
		int incrY = acrossNotDown ? 0 : 1;

		if (isLegalSquare(x-incrX, y-incrY) && !Crossword.stopOrEmpty(this.c.box[x-incrX][y-incrY]))
			return false;
		if (isLegalSquare(x+incrX, y+incrY) && !Crossword.stopOrEmpty(this.c.box[x+incrX][y+incrY]))
			return false;
		return true;
	}

	/** main (and only) entry point to placer. try to place candidateWords on the grid. placement may depend on clues, so placer needs to know about cluer also. */
	public void placeWordsAndCreateClues (boolean doSymmetric, Cluer cluer) throws UnplaceableException, CorruptIndexException, LockObtainFailedException, IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException 
	{
		boolean preferAcrossNotDown = true;
		
		placabilityCalculator.setupPlacability();
		String word = "";
		word_loop:
		for (ListIterator<String> it = this.c.candidateWords.listIterator(); it.hasNext(); )  
		{
			word = it.next();
			if (isUnclueable(word))
				continue;
			
			try {
				placabilityCalculator.deleteWordFromPlacabilityWindow(word);
				placabilityCalculator.addNextWordToPlacabilityWindow();
			} catch (Exception e) {
				Util.print_exception(e, log);
			}
			
			for (int intersect = 0; intersect < 2; intersect++)
			{
				// prefer intersecting placements if possible
				boolean mustIntersect = (intersect == 0);
				if (mustIntersect && c.placedWords.size() == 0)
					mustIntersect = false;
				
				for (int dir = 0; dir < 2; dir++)
				{
					// ... in both directions
					// 2 dirs, if preferAcrossNotDown: we first check across, then down. and vice versa
					// b = 0 captures across not down.

					int b = (preferAcrossNotDown == (dir == 0)) ? 0 : 1;
					boolean acrossNotDown = (b == 0);
					int bestPlacability = Integer.MAX_VALUE;
					PlacabilityInfo bestPI = null;
					int nCandidatePositions = 0;
					
					// sweep entire grid
					Word bestW1 = null, bestW2 = null;

					for (int xit = 0; xit < w; xit++)
						for (int yit = 0; yit < h; yit++)
						{
							// inner loop to start scanning forwards within placability_depth
							// wordToTry is set to outer loop word to start with
							ListIterator<String> pdepth_it = c.candidateWords.listIterator(it.nextIndex());
							String wordToTry = word;
							int x = 0;
							do {
								if (isUnclueable(wordToTry))
									continue;

								PlacabilityInfo pi = placabilityLossOf(wordToTry, xit, yit, acrossNotDown, mustIntersect);
								if (pi != PlacabilityInfo.unplacable)
								{
									int placability = pi.placabilityLoss;
									nCandidatePositions++;
									if (!c.placeAll) // don't go by position if we have to place all
										placability *= (x+1); // mult. by x+1 to prefer earlier words within the placability depth
								
									if (pi.meetsAnyNonIntersectingWord)
									{
										placability /= 10; // attenuate loss, because its bailing out a non intersecting word
										log.info(wordToTry + " bailed out some solitary word!");
									}
									
									if (pi.nIntersects > 0)
										placability /= (pi.nIntersects * 10); // attenuate loss, because its good to intersect with many other words
								
									if (placability < bestPlacability)
									{
										// ok, we have a shot at improving placability
										log.info ("Improved placability: " + wordToTry + " = " + placability + " at " + xit + ", " + yit);
										if (!word.equals(wordToTry))
											log.info ("lookahead worked! might place " + wordToTry + " over " + word);
										
										// first check if we have a clue for wordToTry
										Clue W1Clue = null;
										
										try { W1Clue = cluer.bestClueFor(wordToTry, c.sentencesUsedAsClues); } catch (Exception e) { Util.print_exception(e, log); }
										
										if (W1Clue == null)
										{
											log.info ("Almost placed CANDIDATE " +  wordToTry + " at (" + xit + "," + yit + ") " + (acrossNotDown ? "across":"down") + " but dropped because no good clue was found");
											unclueableWords.add(wordToTry);
											log.info ("Discarding word " + wordToTry + " because it is unclueable");
											// it.remove();
											// placabilityCalculator.setupPlacability(); // reinit from the whole outer loop 
											continue;
										}
										
										// ok, we have a possible word and its clue.
										String originalAnswer = c.getOriginalAnswer(wordToTry);
										Word W1 = new Word(wordToTry, originalAnswer, xit, yit, acrossNotDown, mustIntersect);
										Pair<String, List<Integer>> p1 = Crossword.convertToWord(originalAnswer);
										W1.setWordLens(p1.getSecond());											
										W1.setClue(W1Clue);
	
										// do we need its twin?
										Word W2 = null;
										if (doSymmetric)
										{
											W2 = findTwinFor(W1, mustIntersect, cluer);
											if (W1 == W2)
												log.info ("Wow: word is its own twin: " + W1);
											if (W2 == null)
											{		
												log.info("Almost placed CANDIDATE WORD AT: " + W1  + " but no twin found");
												continue; // sorry, it didn't work out
											}
										}
										// ok, we have both W1 and W2 (if needed). this is a serious candidate so update bestPlacability.
										// we will add to the grid, unless W1 and W2 are scooped by someone else.
	
										bestPlacability = placability;
										bestPI = pi;
										bestW1 = W1;
										bestW2 = W2;
									}
								}
								if (!pdepth_it.hasNext())
									break;
								wordToTry = pdepth_it.next();
							} while (++x < PlacabilityCalculator.PLACABILITY_DEPTH);
						}

					// end of grid and pdepth sweep
					
					if (bestW1 != null) 
					{
						// ok, let's place best W1 and best W2
						commitWordToGrid(bestW1); // this is just for the grid
						c.commitWord(bestW1, true);
						log.info("FINALLY PLACED WORD AT: " + bestW1.word  + " with pref. across not down =" + preferAcrossNotDown + " placability = " + bestPlacability + " (info: " + bestPI + ") selected from " + nCandidatePositions + " candidate position(s)\n" + this);
						// note W2 can be = W1 if its right in the center
						if (doSymmetric && bestW2 != bestW1)
						{
							commitWordToGrid(bestW2);
							c.commitWord(bestW2, true);
							log.info("FINALLY PLACED TWIN WORD AT: " + bestW2  + " with pref. across not down =" + this);
						}
						preferAcrossNotDown = !bestW1.acrossNotDown; // toggle after every placed word to balance between across and down
						it = c.candidateWords.listIterator(); // start from the beginning
						placabilityCalculator.setupPlacability();
						continue word_loop;
					}
				}
			}
		}
	}

	private boolean isUnclueable(String word)
	{
		return unclueableWords.contains(word);
	}
	
	/** returns twin word, if one can be placed. note: a word can be its own twin */
	private Word findTwinFor(Word W, boolean mustIntersect, Cluer cluer) throws CorruptIndexException, LockObtainFailedException, IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException
	{
		int twinX = c.w-1-W.x, twinY = c.h-1-W.y;
		if (W.acrossNotDown)
			twinX -= (W.word.length()-1);
		else
			twinY -= (W.word.length()-1);
		
		int x = twinX, y = twinY;
		
		boolean acrossNotDown = W.acrossNotDown;

		// this logic is a little complicated but its necessary.
		// we haven't updated the vox array to reflect W, so we have to careful that W and its twin don't overlap.
		// however, W can be its own twin(!)
		if (W.x == twinX && W.y == twinY)
			return W;

		// now check if W and twin overlap, or if they are neighboring ranks
		if (acrossNotDown && Math.abs(W.y-twinY) < 1)
		{
			int twinEndX = twinX+W.word.length()-1, WEndX = W.x + W.word.length()-1;
			if (W.x <= twinX && WEndX >= twinX) // twin is sandwiched between W
				return null;
			if (twinX <= W.x && twinEndX >= W.x) // W is sandwiched between twin
				return null;
		}
		else if (!acrossNotDown && Math.abs(W.x-twinX) < 1)
		{
			int twinEndY = twinY+W.word.length()-1, WEndY = W.y + W.word.length()-1;
			if (W.y <= twinY && WEndY >= twinY) // twin start overlaps with W sandwiched between W
				return null;
			if (twinY <= W.y && twinEndY >= W.y) // W is sandwiched between twin
				return null;
		}
			
		int count = 0;

		word_loop:
		for (Iterator<String> it = c.candidateWords.iterator(); it.hasNext(); )  
		{
			if (++count == MAX_TWIN_SEEK_LENGTH)
				break;
			String word = it.next();
			word = word.toLowerCase();
			if (isUnclueable(word))
				continue;

			if (word.length() != W.word.length())
				continue;
			if (word.equals(W.word))
				continue;
			
			/*
			if (prefixOrSuffixOfAny(placedWords, word))
			{
				// if w is a prefix/suffix of any word, remove it so we don't consider it again. (e.g. if we placed "american", remove "america"
				it.remove();
				continue;
			}
			*/
			// now walk through boxes at this position for each letter of the word
			if (log.isDebugEnabled())
				log.debug ("Trying " + word + " at (" + x + "," + y + ") " + (acrossNotDown ? "across":"down"));
	
			PlacabilityInfo pi = placabilityLossOf(word, x, y, acrossNotDown, mustIntersect);
			boolean possible = (pi != PlacabilityInfo.unplacable);
			
			if (possible) 
			{
				String originalAnswer = c.getOriginalAnswer(word);
				Word twin = new Word(word, originalAnswer, x, y, acrossNotDown, mustIntersect);
				Pair<String, List<Integer>> p1 = Crossword.convertToWord(originalAnswer);
                twin.setWordLens(p1.getSecond());

				// check that the word actually has a clue
				Clue clue = null;
				try { cluer.bestClueFor(word, c.sentencesUsedAsClues); } catch (Exception e) { Util.print_exception(e, log); }

				if (clue == null)
				{
					log.info ("Almost placed " +  twin + " at (" + x + "," + y + ") " + (acrossNotDown ? "across":"down") + " but dropped because no good clue was found");
					log.info ("Discarding word " + word);
					unclueableWords.add(word);
					//it.remove(); don't fiddle with the collection because the iterator is still working on the original list
					continue word_loop;
				}
				twin.setClue(clue);
				return twin;
			}
		}
		return null;
	}
				
	protected void commitWordToGrid(Word W)
	{
		// log.info ("Committing word: " + W);
		int ix = W.x, iy = W.y;
		boolean acrossNotDown = W.acrossNotDown;
		int incrX = acrossNotDown ? 1 : 0;
		int incrY = acrossNotDown ? 0 : 1;
		String word = W.word;
		
		c.markStop(ix-incrX, iy-incrY); // put a stop before this word 
		int b = (acrossNotDown?0:1);
		
		for (int i = 0; i < word.length(); i++) 
		{
			Util.ASSERT (c.box[ix][iy] == Crossword.EMPTY || c.box[ix][iy] == word.charAt(i));
			c.box[ix][iy] = word.charAt(i);
			markCantStartFrom(ix, iy, b);
			// note: if we're going horiz, can't start from adjacent Y (therefore ix + incrY not ix + incrX).
			// note this is not symmetrical: the box ix-incrY, iy-incrX is perfectly ok to start from
			markCantStartFrom(ix+incrY, iy+incrX, 1-b);
			ix += incrX;
			iy += incrY;
		}
		
		// mark one more can't start from ix, iy at just beyond the end of the word, just for completeness (this is the same as stop)
		markCantStartFrom(ix, iy, b);
		c.markStop(ix, iy); // put a stop at the end of the word
		
		// log.info ("placing " +  W + " at (" + W.x + "," + W.y + ") " + (acrossNotDown ? "across":"down"));
	}

	public String toString() 
	{
		StringBuilder sb = new StringBuilder();
		sb.append (c.placedWords.size() + " placed word(s), " + c.candidateWords.size() + " unplaced\n");
		for (int j = 0; j < h; j++)
		{
			for (int i = 0; i < w; i++)
			{
				char c = this.c.box[i][j];
				if (c == this.c.EMPTY)
					c = '#';	
				sb.append (c + " ");
			}

			sb.append ("       ");
			for (int i = 0; i < w; i++)
			{
				sb.append (cantStartFrom[i][j][0] ? "x" : ".");
				sb.append(" ");
			}
			sb.append ("       ");
			for (int i = 0; i < w; i++)
			{
				sb.append (cantStartFrom[i][j][1] ? "x" : ".");
				sb.append(" ");
			}
			
			sb.append('\n');
		}
		
		for (Word w: c.placedWords)
			sb.append (w + "\n");
		return sb.toString();
	}
}
