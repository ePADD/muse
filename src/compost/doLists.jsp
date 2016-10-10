<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>

<%
	JSPHelper.logRequest(request);
	boolean success = JSPHelper.fetchEmails(request, session, false);

	if (!success)
	{
	    response.setContentType("text/xml");
	    response.setHeader("Cache-Control", "no-cache");
	    out.println("<result><div resultPage=\"error.jsp\"></div></result>");
		return;
	}

    response.setContentType("text/xml");
    response.setHeader("Cache-Control", "no-cache");
    out.println("<result><div resultPage=\"showLists.jsp\"></div></result>");
	JSPHelper.logRequestComplete(request);

%>
