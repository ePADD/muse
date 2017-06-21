package edu.stanford.muse.xword;

class UnplaceableException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String unplaceableWord;
	public UnplaceableException(String message, String word) { super(message); this.unplaceableWord = word; }

}
