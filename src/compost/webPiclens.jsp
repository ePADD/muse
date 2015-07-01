<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="org.xhtmlrenderer.simple.*"%>
<%@page language="java" import="java.awt.image.*"%>
<%@page language="java" import="javax.imageio.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>
<% 
String rootDir = JSPHelper.getRootDir(request);
String userKey = JSPHelper.getUserKey(session);

// fetch thumbnails upto a limit
List<Document> allDocs = (List<Document>) JSPHelper.getSessionAttribute(session, "emailDocs");
int limit = 20;
String s = request.getParameter("limit");
try { 
	if (s != null) 
		limit = Integer.parseInt(s); 
} catch (NumberFormatException nfe) { }

WebPageThumbnail.fetchWebPageThumbnails(rootDir, "links", allDocs, false, limit);
%>
<html>
<head>
<meta HTTP-EQUIV="REFRESH" content="0; url=<%=userKey%>/links.piclens.index.html">
</head>
</html>