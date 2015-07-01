<%@ page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<title>Email Folders</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/stacktrace.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<jsp:include page="../css/css.jsp"/>
</head>
<body>
<%@include file="header.html"%>

<div class="panel shadow">
<div style="padding: 20px 0px 20px 100px">
<h2>
Sessions
</h2>
<p>
Name of current session:&nbsp;&nbsp;
<input id="saveSessionTitle" type="text" size="15"/> &nbsp;&nbsp;&nbsp;
<a href="#" onclick="javascript:muse.save_session($('#saveSessionTitle').attr('value'), '#saveSessionSpinner', null)">Save</a>
<img id="saveSessionSpinner" style="visibility:hidden" width="15" src="images/spinner.gif"/>
<p/>

<% List<String> sessions = Sessions.listSessions((String) JSPHelper.getSessionAttribute(session, "cacheDir"));
if (!Util.nullOrEmpty(sessions))
{
	%>
	Load session:
	<img id="loadSessionSpinner" style="visibility:hidden" width="15" src="images/spinner.gif"/>
	<%
	JSPHelper.log.info (sessions.size() + " sessions");
	for (int i = 0; i < sessions.size(); i++)
	{
		String s = sessions.get(i);
		out.println ("<a href=\"#\" onclick=\"javascript:muse.load_session('" + s + "', '#loadSessionSpinner', 'info');\">" + s + "</a>&nbsp;");
		if (i < sessions.size()-1)
			out.println ("&bull; &nbsp;\n");
	}
	%>
	Delete session:
	<img id="deleteSessionSpinner" style="visibility:hidden" width="15" src="images/spinner.gif"/>
	<%
	JSPHelper.log.info (sessions.size() + " sessions");
	for (int i = 0; i < sessions.size(); i++)
	{
		String s = sessions.get(i);
		out.println ("<a href=\"#\" onclick=\"javascript:muse.delete_session('" + s + "', '#deleteSessionSpinner', 'info');\">" + s + "</a>&nbsp;");
		if (i < sessions.size()-1)
			out.println ("&bull; &nbsp;\n");
	}
}
else
	out.println ("(No saved sessions)");
%>
<br/>
</div>
</div>
<jsp:include page="footer.jsp"/>
</body>
</html>
