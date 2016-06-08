package edu.stanford.muse.xword;

import edu.stanford.muse.exceptions.ReadContentsException;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** simple cluer */
public class FixedCluer extends Cluer {
	private static final long serialVersionUID = 1L;
	private static Log log = LogFactory.getLog(FixedCluer.class);

	private Crossword c;
	private Map<String, Clue> fixedClues;

	public FixedCluer(Crossword crossword, Map<String, Clue> fixedClues)
	{
		this.c = crossword;
		this.fixedClues = fixedClues;
		
		// for fb only, weed out empty pics, which tend to be < 1000 bytes
		for (Iterator<String> it = fixedClues.keySet().iterator(); it.hasNext(); )
		{
			String str = it.next();
			Clue c = fixedClues.get(str);
			if (Util.nullOrEmpty(c.clue) && !Util.nullOrEmpty(c.getPicURL()))
			{
				// fb special: test this pic url to make sure its not an empty fb photo
				int x = 0;
				try { x = Util.url_content_size(c.getPicURL()); } catch (Exception e) { Util.print_exception(e, log); }
				log.info ("URL CONTENT SIZE: " + x);
				if (x < 1000)
					it.remove();
			}
		}
	}

	/** returns clue for word, either from cache if available, or creates a new one if possible. returns null if no adequate clue is found */
	@Override
	public Clue bestClueFor (String word, Set<String> sentencesUsedAsClues) throws IOException, GeneralSecurityException, ClassNotFoundException, ReadContentsException
	{
		String originalAnswer = this.c.getOriginalAnswer(word);
		Clue c = null;		
		c = fixedClues.get(originalAnswer);
		return c;
	}
}
