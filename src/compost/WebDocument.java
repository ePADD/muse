package edu.stanford.muse.index;

public class WebDocument extends EmailDocument {
	String sourceURL, body;
	static int nextDocId = 0;

	public WebDocument(String text, String sourceURL)
	{
		super(nextDocId++);
		this.sourceURL = sourceURL;
		this.body = text;
		this.folderName = "Web pages";
		if (sourceURL == null)
			this.body = body;
		else
			this.body = "Original page: " + sourceURL + "\n" + body;
	}
}
