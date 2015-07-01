<%@page language="java" contentType="text/javascript; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page import="java.text.ParseException"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.exceptions.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%
	// this JSP is like doFetchAndIndex. it sets up the archive in prep. for the memory test.
	
	JSPHelper.logRequest(request);
	session.setAttribute("statusProvider", new StaticStatusProvider("Setting up the memory test..."));
			
	// simple flow means we're running off the login page and that we'll just use sent folders
	boolean memoryTestMode = "memorytest".equals(JSPHelper.getSessionAttribute(session, "mode"));
	// can check here for 
	
	boolean cancelled = false;
	JSONObject result = new JSONObject();
	String errorMessage = null;
	String resultPage = null;
	MuseEmailFetcher m = (MuseEmailFetcher) JSPHelper.getSessionAttribute(session, "museEmailFetcher");
	// m can't be null here, the stores should already have been set up inside it
try {
	SimpleSessions.prepareAndLoadArchive(m, request);
	Archive archive = (Archive) session.getAttribute("archive");

	for (EmailStore store : m.emailStores)
		if (!(Util.nullOrEmpty(store.emailAddress)))
			archive.addOwnerEmailAddr(store.emailAddress);

		// step 1: fetch		
		Collection<EmailDocument> emailDocs = null;

		boolean downloadMessageText = true, downloadAttachments = false;
		// now fetch and index... can take a while
		m.setupFetchers(-1);			
		
		// set up the owner email addrs from the email addrs saved in the fetcher's stores
		String altEmailAddrs = (String) JSPHelper.getSessionAttribute(session, "alternateEmailAddrs");
		for (EmailStore store : m.emailStores)
			if (!(Util.nullOrEmpty(store.emailAddress)))
				archive.addOwnerEmailAddr(store.emailAddress);
		if (!Util.nullOrEmpty(altEmailAddrs))
			archive.addOwnerEmailAddrs(EmailUtils.parseAlternateEmailAddrs(altEmailAddrs));
		
		// make sure to remove existing emailDocs, used to be a bug -- would be left over as an empty list from screening, somehow
		session.removeAttribute("emailDocs");

		JSPHelper.fetchAndIndexEmails(archive, m, request, session, downloadMessageText, downloadAttachments, true /* simpleFlow */); // download message text, maybe attachments, use default folders
		archive.assignThreadIds();
		archive.postProcess();
		archive.close();
		
		// re-open for reading clues, etc.
		archive.openForRead();
				
		// set up the actual study
		MemoryStudy currentStudy = (MemoryStudy) session.getAttribute("study");
		if (currentStudy == null) {
			// should have been init'ed in memorystudy login page
			// better thing to do here is to send redirect to a timed out page
			resultPage = "/muse/error";
		} else {
			Lexicon lex = archive.getLexicon("default");
			session.setAttribute("lexicon", lex);
			session.setAttribute("statusProvider", new StaticStatusProvider("Generating questions"));
			Pair<String, String> p1 = Util.fieldsToCSV(currentStudy.stats, false);
			Pair<String, String> p2 = Util.fieldsToCSV(archive.addressBook.getStats(), false);
			Pair<String, String> p3 = Util.fieldsToCSV(archive.indexer.stats, false);
			JSPHelper.log.info("STUDYSTATS-1: " + p1.getFirst() + "," + p2.getFirst() + "," + p3.getFirst());
			JSPHelper.log.info("STUDYSTATS-2: " + p1.getSecond() + "," + p2.getSecond() + "," + p3.getSecond());

			Collection<EmailDocument> allDocs = (Collection<EmailDocument>) session.getAttribute("emailDocs");		
			int numQ = HTMLUtils.getIntParam(request, "n", 40); //should be 40 by default
			currentStudy.generateQuestions(archive, allDocs, lex, numQ);
			resultPage = "memorystudy/example";
			boolean not_enough_questions = currentStudy.checkQuestionListSize(numQ);
			if (not_enough_questions) {
				JSPHelper.log.info ("Not enough questions!");
				resultPage = "/muse/memorystudy/notenoughquestions.html";
			}
		}
	} catch (CancelledException ce) {
		// op was cancelled, so just go back to where we must have been
		JSPHelper.log.warn("Fetch groups and indexing cancelled by user");
		cancelled = true;
	} catch (Exception e) {
		JSPHelper.log.warn("Exception fetching/indexing emails");
		Util.print_exception(e, JSPHelper.log);
		errorMessage = "An error occured while accessing the messages";
		// we'll leave archive in this
	}

	if (cancelled) {
		result.put("status", 0);
		result.put("cancelled", true);
	} else if (errorMessage == null) {
		result.put("status", 0);
		result.put("resultPage", resultPage);
	} else {
		result.put("status", 1);
		result.put("resultPage", "error");
		result.put("error", errorMessage);
	}
	out.println(result);

	// resultPage is set up to where we want to go next
	session.removeAttribute("statusProvider");
	JSPHelper.logRequestComplete(request);
%>
