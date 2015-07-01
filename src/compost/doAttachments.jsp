<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>

<%
	JSPHelper.logRequest(request);
	try {
    boolean success = JSPHelper.fetchEmails(request, session, true, true, false);

	if (!success)
	{
        response.setContentType("text/xml");
        response.setHeader("Cache-Control", "no-cache");
	    out.println("<result><div resultPage=\"error.jsp\"></div></result>");
		return;
	}

	// compute redirect page
	String resultPage = (String) JSPHelper.getSessionAttribute(session, "userKey") + "/" + JSPHelper.getSessionAttribute(session, "piclensFile");
	response.setContentType("text/xml");
    response.setHeader("Cache-Control", "no-cache");
	out.println ("<result><div resultPage=\"attachments\"></div></result>");
	//	response.sendRedirect(resultPage);
} catch (Exception e) {
    session.setAttribute("errorMessage", e.toString());
	session.setAttribute("exception", e);
    e.printStackTrace(System.err);
    response.setContentType("text/xml");
    response.setHeader("Cache-Control", "no-cache");
    out.println("<result><div resultPage=\"error.jsp\"></div></result>");
}
JSPHelper.logRequestComplete(request);

%>

