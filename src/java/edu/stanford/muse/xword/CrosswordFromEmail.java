package edu.stanford.muse.xword;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.index.Lexicon;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.HTMLUtils;
import edu.stanford.muse.webapp.JSPHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;

public class CrosswordFromEmail {

	public static int DEFAULT_TIMEOUT_MILLIS = 5000;
	public static Log log = LogFactory.getLog(CrosswordFromEmail.class);

	public static Crossword createCrossword (HttpServletRequest request) throws Exception
	{
		Map<String, String> map = HTMLUtils.getRequestParamMap(request);
		return createCrossword(map, request.getSession());
	}
	
	public static Crossword createCrossword (Map<String, String> params, HttpSession session) throws Exception
	{
		// print a count of the most popular names

		int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
		String millisStr = params.get("millis");
		if (millisStr != null)
		{
			try {
					timeoutMillis = Integer.parseInt(millisStr);
			} catch (Exception e) { log.warn ("Bad timeout millis: " + millisStr); }
		}
		
		Map<String, Integer> map = new LinkedHashMap<String, Integer>();

//		Map<String, Integer> thirdPartyCount = new LinkedHashMap<String, Integer>();
		Set<String> filteredIds = null;
		Archive archive = null;

		archive = JSPHelper.getArchive(session);
		Collection<edu.stanford.muse.index.Document> allDocs = (Collection) session.getAttribute("emailDocs");
		if (allDocs == null)
			allDocs = archive.getAllDocs();
		
		/* identify ids of docs (may be filtered, and a subset of all the docs in the indexer) */

		filteredIds = new LinkedHashSet<String>();
		for (edu.stanford.muse.index.Document d : allDocs)
			filteredIds.add(d.getUniqueId());

		archive.openForRead();

		List<Set<String>> docs = archive.indexer.getAllNames(filteredIds, Indexer.QueryType.ORIGINAL);
		// docs is now the set of names in the filtered docs. count the names
		for (Set<String> names : docs) {
			if (names == null)
				continue;
			for (String name : names) {
				name = name.toLowerCase();
				Integer I = map.get(name);
				map.put(name, (I == null) ? 1 : I + 1);
			}
		}

		// sort names by freq.
		List<Pair<String, Integer>> termFreqList = Util.sortMapByValue(map);
		List<Pair<String, Integer>> newTermFreqList = new ArrayList<Pair<String, Integer>>();
		for (Pair<String, Integer> p: termFreqList)
		{
			if (p.getSecond() == 1)
				break;
			newTermFreqList.add(p);
		}
		JSPHelper.log.info("Candidate answer set reduced from: " + termFreqList.size() + " to: " + newTermFreqList.size() + " after dropping singletons");

		// don't generate a new crossword if it already exists and we just need answers
		Lexicon lex = (session != null) ? (Lexicon) session.getAttribute("lexicon") : null;
		if (session != null && lex == null)
		{
			String baseDir = (String) session.getAttribute("cacheDir");
			String name = "default";
			lex = new Lexicon(baseDir, name);
			session.setAttribute("lexicon", lex);	
		}

		boolean doSymmetric = params.get("symmetric") != null;
		int size = HTMLUtils.getIntParam(params, "size", 15); 

		if (params.get("noclues") != null)
			archive = null;

		Crossword c = null;

		int w = HTMLUtils.getIntParam(params, "w", size); 
		int h = HTMLUtils.getIntParam(params, "h", size); 
		int minAnswerLen = 3, maxAnswerLen = 15;
		c = Crossword.createCrossword(termFreqList, w, h, archive, lex, filteredIds, null, doSymmetric, null /* fixedClues */, minAnswerLen, maxAnswerLen, timeoutMillis);
	
		if (session != null)
			session.setAttribute("crossword", c);
		c.haveMessages = true;
		
		return c;
	}
}
