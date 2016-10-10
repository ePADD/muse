<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>

<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.bespoke.mining.*"%>
<%@page language="java" import="edu.stanford.bespoke.directed.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Email terms graph</title>
</head>
<body>

<% 
Collection<EmailDocument> docs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
Map<Integer, Collection<Collection<String>>> nameCooccurrenceInParas = IndexUtils.nameCooccurrenceInParas (docs);
String filename = System.getProperty("java.io.tmpdir") + File.separator + "graph.GDF";
Digraph.doIt(nameCooccurrenceInParas, filename);
String fileurl = filename.replaceAll(File.separator, "/");
out.println ("Graph written out to <a href=\"file:///" + fileurl + "\">" + filename + "</a>");
%>

</body>
</html>