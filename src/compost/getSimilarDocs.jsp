

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<title>Similar Messages</title>

<link href="muse.css" rel="stylesheet" type="text/css"/>
</head>
<body>

<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.mining.*"%>
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
		List<Document> allDocs = (List<Document>) JSPHelper.getSessionAttribute(session, "emailDocs");

		int docNum = 0;
		for (Document d : allDocs)
			d.setDocNum(docNum++);

        List<List<Document>> result = new SimilarDocs().run((List) allDocs);

        for (List<Document> docList: result)
        {
        	for (Document doc: docList)
        	{
        		out.println ("<a href=\"showDoc.jsp?docNum=" + doc.docNum + "\">Message " + doc.docNum + "</a><br/>");
        		out.println ("<pre>" + doc.getHeader() + "</pre>");
        	}
        	out.println ("<hr/>");
        }
        //  response.sendRedirect(resultPage);
    } catch (Exception e) {
        session.setAttribute("errorMessage", e.toString());
        e.printStackTrace(System.err);
        response.setContentType("text/xml");
        response.setHeader("Cache-Control", "no-cache");
        out.println("<result><div resultPage=\"error.jsp\"></div></result>");
    }
	JSPHelper.logRequestComplete(request);

%>
