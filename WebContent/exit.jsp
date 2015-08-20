<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<% JSPHelper.logRequest(request); %>
<!DOCTYPE HTML>
<html lang="en">
<head>
<jsp:include page="css/css.jsp"/>
</head>
<% 
JSPHelper.logRequest(request);
boolean underWebstart = System.getProperty("javawebstart.version") != null;
if (!underWebstart)
{
	out.println ("<p><br/><br/>Sorry, exit can only be called when running on your own computer with Java webstart.");
	return;
}
String mesg = "Muse v" + Version.num + " was asked to shut down and exit completely in 2 seconds.";
String s = request.getParameter("message");
if (!Util.nullOrEmpty(s))
	mesg += "Message: " + s;
mesg += "<br/>To relaunch Muse, click on the Muse icon on your desktop or <a href=\"http://mobisocial.stanford.edu/muse\">visit the Muse page</a>.";
System.out.println (mesg);
JSPHelper.log.info (mesg);
out.println (mesg);
out.flush();
Thread.sleep(2000);
System.exit(0);
%>
