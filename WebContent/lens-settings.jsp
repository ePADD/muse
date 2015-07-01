<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="org.apache.log4j.*"%>
<%
	JSPHelper.logRequest(request);
    response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
    response.setHeader("Pragma","no-cache"); //HTTP 1.0
    response.setDateHeader ("Expires", 0); //prevent caching at the proxy server
    // remove existing status provider if any because sometimes status provider
    // for prev. op prints stale status till new one is put in the session

	Archive archive = JSPHelper.getArchive(session);	  
	if (archive == null)
	{
	    response.sendRedirect("index.jsp");
	    return;
	}

	Collection<EmailDocument> allDocs = (Collection) JSPHelper.getSessionAttribute(session, "emailDocs"); 
	AddressBook ab = archive.addressBook;
	Indexer indexer = archive.indexer;
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<title>Muse Settings</title>
<jsp:include page="css/css.jsp"/>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/stacktrace.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
</head>
<body class="fixed-width"> 
<jsp:include page="header.jsp"/>

<div class="panel shadow" style="padding:20px 50px 20px 50px">

<h2>Interface Settings</h2>
Max lines in callout: <input id="max_lines" size="2" value="3">&nbsp;&nbsp; <button onclick="muse.setConfigParam('lens.callout.lines', $('#max_lines').val());">set</button>
<p>



</div>
<jsp:include page="footer.jsp"/>
</body>
</html>