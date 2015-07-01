<%@ page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
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

	try {
		boolean success = JSPHelper.fetchEmails(request, session, false);

		if (!success)
		{
		    response.setContentType("text/xml");
		    response.setHeader("Cache-Control", "no-cache");
		    out.println("<result><div resultPage=\"error.jsp\"></div></result>");
			return;
		}
		String dateSpec = request.getParameter("dateSpec");
		Collection<DatedDocument> docs = JSPHelper.filterByDate((Collection) JSPHelper.getSessionAttribute(session, "emailDocs"), dateSpec);
		session.setAttribute("emailDocs", docs);

		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		// out.println ("<?xml version=\"1.0\"?>");
		out.println("<result><div resultPage=\"browse\"></div></result>");
	} catch (Exception e) {
		session.setAttribute("errorMessage", e.toString() + "\n" + Util.stackTrace(e));
		e.printStackTrace(System.err);
		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		out.println("<result><div resultPage=\"error.jsp\"></div></result>");
	}
%>

