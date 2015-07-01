package edu.stanford.muse.xword;

public class UnplaceableException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String unplaceableWord;
	public UnplaceableException(String message, String word) { super(message); this.unplaceableWord = word; }

}
