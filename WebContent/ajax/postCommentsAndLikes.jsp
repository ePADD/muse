<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.util.zip.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%
JSPHelper.logRequest(request);
String docset = request.getParameter("docset"); // this is actually docs-docset-NNN
String comment = request.getParameter("comment");
boolean like = request.getParameter("like") != null;

List<Document> docs = (List<Document>) JSPHelper.getSessionAttribute(session, docset);
if (docs == null)	
{
	JSPHelper.log.error ("Docset " + docset + " is null");
	response.sendError(HttpServletResponse.SC_REQUEST_TIMEOUT); // 404.
    return;
}

// if docnum is "all", apply to all docs in this docset
String docNumStr = request.getParameter("page");
if ("all".equalsIgnoreCase(docNumStr))
{
	for (Document d: docs)
		if (!like)
			d.setComment(comment);
		else
			d.setLike();
}
else
{
	int docNum = HTMLUtils.getIntParam(request, "page", -1);	
	if (docNum < 0 || docNum >= docs.size())
	{
		JSPHelper.log.error ("Bad doc num " + docNum + " for docset " + docset + " size " + docs.size());
	    response.sendError(HttpServletResponse.SC_REQUEST_TIMEOUT); // 404.
	    return;
	}

	Document d = docs.get(docNum);
	if (like)
		d.setLike();
	else
		d.setComment(comment);
}
JSPHelper.logRequestComplete(request);

%>