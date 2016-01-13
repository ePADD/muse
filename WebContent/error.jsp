<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.exceptions.*"%>
<%@page isErrorPage="true" %>
<% 	
if ("memorytest".equals(session.getAttribute("mode"))) {
	response.sendRedirect("/muse/memorystudy/error.jsp");
	return;
}
ErrorData err = pageContext.getErrorData();
if (err != null)
{
	String req = err.getRequestURI();
	if (req != null)
	{
		String message = " Error accessing " + req + " servlet: " + err.getServletName() + " status: " + err.getStatusCode() + " Exception: " + err.getThrowable();
		JSPHelper.log.warn (message);
	
		// if an ajax request failed, send back a json instead of a regular error page
		if (req.indexOf("ajax/") >= 0)
		{
			session.setAttribute("exception", err.getThrowable());
			response.setHeader("Cache-Control","no-cache");
			response.setHeader("Pragma","no-cache");
			response.setHeader("Expires","-1");
			response.setContentType("application/x-javascript; charset=utf-8");
		
			out.println ("{error: \"" + message + "\"}");
			return;
		}
	}
}
%>
<!DOCTYPE HTML>
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<title>Error</title>
<jsp:include page="css/css.jsp"/>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
</head>
<body class="backdrop">

<p> &nbsp; 
<div>
<div style="float:left;width:200;margin-right:50px">
<img width="200" src="/muse/images/keepcalm.jpg"/>
</div>
<div class="error" style="float:left; margin-top:20px;max-width:700px">
<p>
<%
// get the root exception
Throwable e = (Throwable) session.getAttribute("exception");
if (e == null)
	e = err.getThrowable();
if (e instanceof TimedOutException)
{
%>
	<h3>Oops! Your browser session timed out. Please <a href="index.jsp">login again</a>.</h3><br/><br/>
<%
} else {
%>
	<h3>Oops! There was a problem. </h3><br/>
	Sorry. Muse is still under development.<br/>
	<%	
	if (e instanceof OutOfMemoryError || e instanceof NotEnoughMemoryException)
	{
		%>
		You are probably running without enough memory... Current memory size is: <%=Util.getMemoryStats()%><br/>
		Please try again with more memory, or on a folder with fewer messages.
		<%
	}
	else
	{
		// toString error details
		String x = (String) session.getAttribute("errorMessage");
		if (e != null)
		{
			String details = Util.stackTrace(e);
			x = (x == null) ? details: x + "\n\n" + details; 
		}
		%>
		Please retry by clicking the browser's back button to go back (or <a href="#" onclick="muse.doLogout()">logout</a> and try again.)<br/>
		Or look at the <a href="help#problems">trouble-shooting section</a> of Muse help.
		<p>
		And importantly, please <a href="debug.jsp">report</a> this error so we can fix it.<br/>
		<%
		JSPHelper.log.warn ("On error page: exception is : " + x);
	}
}
session.removeAttribute("errorMessage");
session.removeAttribute("exception");

%>
<br/>
</div>
<div style="clear:both">
</div>
</div>
<jsp:include page="footer.jsp"/>

</body>
</html>
