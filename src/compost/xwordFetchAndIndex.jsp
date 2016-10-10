<%@page language="java" contentType="text/javascript; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>  
<%@page language="java" import="org.json.*"%>    
<%@page language="java" import="java.util.*"%>    
<%@page language="java" import="edu.stanford.muse.email.*"%>    
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>    
<%@page language="java" import="edu.stanford.muse.util.*"%>    
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>    
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.exceptions.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.xword.*"%>
<%@page import="java.text.ParseException"%>
<%
	// does a login for a particular account, and adds the emailStore to the session var emailStores (list of stores for the current doLogin's)
JSPHelper.logRequest(request);
session.setAttribute("statusProvider", new StaticStatusProvider("Starting up..."));
response.setHeader("Cache-Control","no-cache");
response.setHeader("Pragma","no-cache");
response.setHeader("Expires","-1");

JSONObject result = new JSONObject();
String errorMessage = null;

// use the existing archive if its already in the session
Archive archive = (Archive) session.getAttribute("session");
MuseEmailFetcher m = (MuseEmailFetcher) JSPHelper.getSessionAttribute(session, "museEmailFetcher");

if (archive == null) {
	SimpleSessions.prepareAndLoadArchive(m, request);
	archive = (Archive) session.getAttribute("archive");
}

archive.openForReadWrite();

try {
	// now fetch and index... can take a while
	m.setupFetchers(-1);
	for (EmailStore store: m.emailStores)
		if (!(Util.nullOrEmpty(store.emailAddress)))
	archive.addOwnerEmailAddr(store.emailAddress);			

	CrosswordManager cm = new CrosswordManager(archive, m, null);
	session.setAttribute("crosswordManager", cm);
	synchronized (cm) {
		cm.setupDocsForLevel(session, 0);
		if (cm.isExhausted())
			errorMessage = "Not enough messages in this account.";
	}
} catch (Exception e) {
	JSPHelper.log.warn("Exception fetching/indexing emails");
	Util.print_exception(e, JSPHelper.log);
	errorMessage = "Exception fetching/indexing emails";
	// we'll leave archive in this
}

if (Util.nullOrEmpty(errorMessage)) {
	result.put("status", 0);
	result.put("resultPage", "xword.jsp?symmetric=");
} else {
	result.put("status", 1);
	result.put("error", errorMessage);
}
out.println(result);

session.removeAttribute("statusProvider");
JSPHelper.logRequestComplete(request);
%>
