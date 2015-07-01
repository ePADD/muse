<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="java.io.*"%>

  
<%
JSPHelper.logRequest(request);
String filename = request.getParameter("attachmentName");
String qvpDirectory = (String) JSPHelper.getSessionAttribute(session, "qvpDir");
String baseDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");

try
{
	String [] argument = {qvpDirectory, baseDir + "\\blobs\\" + filename};
	Process proc = Runtime.getRuntime().exec(argument);
} catch (IOException e) 
{
	e.printStackTrace();
}


%>