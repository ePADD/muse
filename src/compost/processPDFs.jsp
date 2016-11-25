<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%
	JSPHelper.logRequest(request);
%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page import="java.text.ParseException"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="java.util.zip.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%
	JSPHelper.checkContainer(request); // do this early on so we are set up
	request.setCharacterEncoding("UTF-8");
%>

<%
	try {
		// compute redirect page
		String memoryStats = "";

		String documentRootPath = application.getRealPath("/")
		.toString();
		String libDir = documentRootPath + File.separator + "WEB-INF"
		+ File.separator + "lib";
		String rootDir = JSPHelper.getRootDir(request);

		memoryStats += " Memory status at start of getTextPage: "
		+ Util.getMemoryStats() + "\n";
		long t1 = System.currentTimeMillis();

		String prefix = request.getParameter("prefix");
		String title = request.getParameter("title");
		session.setAttribute("dataset", title);
		PDFHandler pdfHandler = new PDFHandler();
		session.setAttribute("statusProvider", pdfHandler);
		List<Document> pdfDocs = pdfHandler.preparePDFs(prefix,
		rootDir, libDir,
		(String) JSPHelper.getSessionAttribute(session, "userKey"), title);

		boolean doIndex = request.getParameter("index") != null;
		if (doIndex) {
	List<String> list = new ArrayList<String>();
	list.add("-categoryBased");
	list.add("-i");
	list.add(prefix + File.separator + title);

	String keywords = request.getParameter("keywords");
	if (keywords != null && !keywords.equals("")) {
		list.add("-keywords");
		list.add(keywords);
	}

	// end filter params

	// advanced options
	if ("true".equalsIgnoreCase(request
	.getParameter("incrementalTFIDF")))
		list.add("-incrementalTFIDF");
	if ("true".equalsIgnoreCase(request.getParameter("NER")))
		list.add("-ner");
	if ("true".equalsIgnoreCase(request
	.getParameter("includedQuotedMessages")))
		list.add("-includedQuotedMessages");

	String subjWeight = request.getParameter("subjectWeight");
	if (subjWeight != null) {
		list.add("-subjectWeight");
		list.add(subjWeight);
	}

	String[] s = new String[list.size()];
	list.toArray(s);

	// careful about the ordering here.. first setup, then read indexer, then run it
	Archive driver = Archive.createArchive();
	session.setAttribute("indexDriver", driver);
	driver.setup(JSPHelper.getBaseDir(session), s);
	driver.setAddressBook(null);

	GroupAssigner ca = new GroupAssigner();
	session.setAttribute("groupAssigner", ca);

	session.setAttribute("statusProvider", driver.getStatusProvider());
	t1 = System.currentTimeMillis();
	Collection<Document> docs = driver.discoverDocs();
	driver.run(docs);

	long timeToIndex = System.currentTimeMillis() - t1;
//	driver.indexer.summarizer.recomputeCards(driver.indexer.getAllDocs(), addressBook, Summarizer.DEFAULT_N_CARD_TERMS);

	pdfDocs = driver.getAllDocs(); // should be the same as before

	StringBuilder statSB = new StringBuilder("Request URL: "
	+ request.getRequestURL() + " params: ");
	Map rpMap = request.getParameterMap();
	for (Object o : rpMap.keySet()) {
		String str1 = (String) o;
		statSB.append(str1 + " -> ");
		String[] vals = (String[]) rpMap.get(str1);
		if (vals.length == 1)
	statSB.append(vals[0]);
		else {
	statSB.append("{");
	for (String x : vals)
		statSB.append(x + ",");
	statSB.append("}");
		}
		statSB.append(" ");
	}

	statSB.append("timeToIndex = " + timeToIndex + "\n");
	String sessionStats = (String) JSPHelper.getSessionAttribute(session, "statString");
	if (sessionStats == null)
		sessionStats = "";
	sessionStats += statSB.toString();
	System.out
	.println("BEGIN STATS\n" + sessionStats
	+ memoryStats + driver.getStats()
	+ "\nEND STATS\n");

	//      Grouper g = new Grouper(addressBook, allDocs, topContactsByMonthOrYear);
		}

		session.setAttribute("emailDocs", pdfDocs);

		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		if (doIndex)
	out.println("<result><div resultPage=\"cards\"></div></result>");
		else
	out.println("<result><div resultPage=\"browse\"></div></result>");

	} catch (Exception e) {
		// for easier debugging, report exception to javascript
		session.setAttribute("errorMessage",
		e.toString() + "\n" + Util.stackTrace(e));
		session.setAttribute("exception", e);
		e.printStackTrace(System.err);
		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		out.println("<result><div resultPage=\"error.jsp\"></div></result>");
	}
	JSPHelper.logRequestComplete(request);
%>
