
<%@page import="java.text.ParseException"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>

<%
	JSPHelper.logRequest(request);
	boolean success = JSPHelper.fetchEmails(request, session, true);

	if (!success)
	{
	    response.setContentType("text/xml");
	    response.setHeader("Cache-Control", "no-cache");
	    out.println("<result><div resultPage=\"error.jsp\"></div></result>");
		return;
	}

	Collection<EmailDocument> allDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");

	out.println ("purchases.jsp has " + allDocs.size() + " documents");
	int i = 0;
	for (EmailDocument ed: allDocs)
	{
		String content = ed.getContents();
		out.println ("<hr/>contents of message " +  (++i) + ":<p>\n<pre>\n" + content + "\n</pre>\n");
	}
	JSPHelper.logRequestComplete(request);
%>

