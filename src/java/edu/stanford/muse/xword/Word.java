package edu.stanford.muse.xword;

import java.io.Serializable;
import java.util.List;

/** captures a placed word's text, coordinates */
class Word implements Comparable<Word>, Serializable {
	public final static long serialVersionUID = 1604516333421018737L;
	public String word;
	private String originalTerm;
	public int x, y; // starting x, y
	public boolean acrossNotDown;
	private int clueNum = -1; // just indicates its uninitialized
	public Clue clue;
	private List<Integer> wordLens; // word lengths for multi-word answers, word is 7 letters, but broken up as (5,2)
	private boolean intersects /* other words */;
	public List<Integer> getWordLens() { return wordLens; }
	public void setWordLens(List<Integer> wordLens) { this.wordLens = wordLens; }
	public Word () { } // need a no-arg constr for gson deserialization

	public Word(String s, String original, int x, int y, boolean acrossNotDown, boolean intersects) {
		this.word = s;
		this.originalTerm = original;
		this.x = x;
		this.y = y;
		this.acrossNotDown = acrossNotDown;
		this.intersects = intersects;
	}
	
	// not consistent with equals!
	public int compareTo(Word other)
	{
		if (acrossNotDown && !other.acrossNotDown)
			return -1;
		if (!acrossNotDown && other.acrossNotDown)
			return 1;
		// both have same direction
		if (this.y != other.y)
			return this.y - other.y;
		return this.x - other.x;
	}

	public static String getWordLens(List<Integer> wordLens)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		if (wordLens != null)
		{
			for (int i = 0; i < wordLens.size(); i++)
			{
				sb.append(wordLens.get(i));
				if (i < wordLens.size()-1)
					sb.append(",");
			}
		}
		sb.append (")");
		return sb.toString();
	}
	
	public String toString() 
	{
		StringBuilder sb = new StringBuilder();
		sb.append ("at "  + x + "," + y);
		sb.append(" ");
		sb.append(word);
		sb.append(" (");
		if (wordLens != null)
			for (Integer wl: wordLens)
				sb.append (wl + "-");
		sb.append(")");
		sb.append(" " + clueNum + " " + (acrossNotDown ? "across":"down") + ". " + (clue == null ? "no clue":clue));
		if (!intersects)
			sb.append(" (may not intersect)");
		return sb.toString();
	}
	
	public void setClueNum(int num)	{ this.clueNum = num; }
	public void setClue(Clue c)	{ this.clue = c; }
	public Clue getClue() { return this.clue; }
	public int getClueNum() { return clueNum; }
}