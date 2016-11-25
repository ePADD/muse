package edu.stanford.muse.xword;

import java.util.Set;

/** simple class */
abstract public class Cluer {
	abstract public Clue bestClueFor (String word, Set<String> sentencesUsedAsClues) throws Exception;
	/** called when a word is committed to the grid (in case a clue cache has to be invalidated */
	public void commitWord(Word W) { /* do nothing */ }
}
