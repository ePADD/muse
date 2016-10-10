<%@ page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
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

		String[] folders = request.getParameterValues("folder");
		folders = JSPHelper.convertRequestParamsToUTF8(folders);

		String prefix = Util.sanitizeFolderName(folders[0]);
		String xmlFile = prefix + ".xml";

		// we create a standard (non-protovis) page because we may be running on MSIE
		String rootDir = (String) JSPHelper.getSessionAttribute(session, "rootDir");
		new File(rootDir).mkdirs();
		String resourceDir = (String) JSPHelper.getSessionAttribute(session, "resourceDir");
		// copy template file to userid directory - can probably improve this
        Util.copy_file(resourceDir + File.separatorChar + "ajax" + File.separatorChar + "template.jsp", rootDir + File.separatorChar + prefix + ".jsp");

		// compute redirect page
		String resultPage = (String) JSPHelper.getSessionAttribute(session, "userKey") + "/" + prefix + ".jsp";
		String ua = request.getHeader("User-Agent");
		boolean isFirefox = (ua != null && ua.indexOf("Firefox/") != -1);
		boolean isMSIE = (ua != null && ua.indexOf("MSIE") != -1);
		if (!isMSIE)
			resultPage = "people?maxCount=400";

		session.setAttribute("resultPage", resultPage);
		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		// out.println ("<?xml version=\"1.0\"?>");
		out.println("<result><div resultPage=\"" + resultPage
		+ "\"></div></result>");
		
	} catch (Exception e) {
		session.setAttribute("errorMessage", e.toString() + "\n" + Util.stackTrace(e));
		session.setAttribute("exception", e);
		e.printStackTrace(System.err);
		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		out.println("<result><div resultPage=\"error.jsp\"></div></result>");
	}
	JSPHelper.logRequestComplete(request);

%>

