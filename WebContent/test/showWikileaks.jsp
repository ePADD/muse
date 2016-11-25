<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.text.*"%>
<%@page language="java" import="java.io.*" %>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%	
	String DATE_FORMAT = "EEE MMM d, yyyy";
	SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
	Map<String, EmailDocument> docnumMap = (Map<String, EmailDocument>) JSPHelper.getSessionAttribute(session, "docnumMap");
	Integer idx = HTMLUtils.getIntParam(request, "docnum", -1);
	String docnum = request.getParameter("docnum");
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Wikileaks #<%=docnum%></title>
</head>
<body style="font-family:'Myriad Pro',Calibri,Arial; margin: 0% 5%;">

<div style="position:relative;max-width:400px;margin-top:10px;"><span style="font-size:16pt;font-weight:bold">Wikileaks cable  #<%=docnum%></span></div>
<div style="clear:both"></div>
<hr/>
<%
	Archive archive = JSPHelper.getArchive(session);
	EmailDocument ed = docnumMap.get(docnum);

	String contents = archive.getContents(ed, false);
	contents = Util.escapeHTML(contents);
	contents = contents.replaceAll("\n", "<br/>");
	out.println (contents);
%>

</body>
</html>