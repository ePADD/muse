<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.exceptions.*"%>

<%
JSPHelper.logRequest(request, false);

session.setAttribute("statusProvider", new StaticStatusProvider("Logging in...<br/>&nbsp; "));
Accounts.simpleSetup(request, session, application.getRealPath("/").toString());
MuseEmailFetcher m = (MuseEmailFetcher) JSPHelper.getSessionAttribute(session, "museEmailFetcher");
try {
	m.login(); // login to all accounts, see if any exception occurs
} catch (Exception e) {
	// both connect and getFoldersAndCounts() will thrown a RTE if something goes wrong, with a user displayable message
    // we're going to abort all fetchers on an exception in any of them. that's why we return directly from the catch block
    JSPHelper.log.warn ("Login failed: " + e.getMessage());
	String loginErrorMessage = "";
    if (e instanceof InvalidFetcherException)
    	loginErrorMessage = "Please login with a supported account.";
    else if (e instanceof LoginFailedException)
    	loginErrorMessage = e.getMessage();
    else
		loginErrorMessage = "Login failed: " + e.getMessage();
    session.setAttribute("loginErrorMessage", loginErrorMessage);
	response.setContentType("text/xml");
    response.setHeader("Cache-Control", "no-cache");
    out.println("<result><div resultPage=\"index.jsp\"></div></result>");
    return;
}

boolean success = JSPHelper.fetchEmails(request, session, true, false, true); // true so we can use default folders

// step 2. extract links
List<EmailDocument> docsToIndex = (List<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
List<String> extraOptions = new ArrayList<String>();
JSPHelper.extractLinks(request, session, docsToIndex);

response.setContentType("text/xml");
response.setHeader("Cache-Control", "no-cache");
out.println("<result><div resultPage=\"createEmailLinksCSE.jsp\"></div></result>"); // ordinary editing interface
JSPHelper.logRequestComplete(request);
%>