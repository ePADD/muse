<%@ page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%
// we are already logged into all accounts at the point this is called
// we may not have finished reading the folders though.
	JSPHelper.logRequest(request);
	session.setMaxInactiveInterval(-1);
    // never let session expire

      response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
      response.setHeader("Pragma","no-cache"); //HTTP 1.0
      response.setDateHeader ("Expires", 0); //prevent caching at the proxy server
	  // remove existing status provider if any because sometimes status provider
	  // for prev. op prints stale status till new one is put in the session
      if (JSPHelper.getSessionAttribute(session, "statusProvider") != null)
		  session.removeAttribute("statusProvider");
      session.removeAttribute("loginErrorMessage");
      String mode = (String)JSPHelper.getSessionAttribute(session, "mode");

	  // UI display of folders
      int numFoldersPerRow = 4;
      if (JSPHelper.getSessionAttribute(session, "fbmode") != null)
    	  numFoldersPerRow = 2;

    MuseEmailFetcher m = (MuseEmailFetcher) JSPHelper.getSessionAttribute(session, "museEmailFetcher");
    // re-read accounts again only if we don't already have them in this session.
    // later we might want to provide a way for users to refresh the list of folders.
    Archive archive = JSPHelper.getArchive(session);
  	AddressBook addressBook = archive.addressBook;
    Indexer indexer = archive.indexer;

    // recreate the cache dir if needed, user may have just cleared it
    String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
    if (!Util.nullOrEmpty(cacheDir))
    {
	    File cacheDirFile = new File(cacheDir);
	    if (!cacheDirFile.exists())
		    cacheDirFile.mkdirs();
    }

%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<title>Data Quality Report</title>
<jsp:include page="css/css.jsp"/>

<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/stacktrace.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/ops.js"></script> <!--  including ops.js early so we have ajax error action defined -->
</head>
<body>
<div style="min-height:300px;" class="panel rounded">

<%if (addressBook == null)  { %>
	<br/>
	You are not currently logged in. Please <a href="index.jsp">login</a> first.
	<br/>
</div>
</body>
</html>
	<% 
	return;
} 

Collection<String> dataErrors = addressBook.getDataErrors();
if (dataErrors != null) { %>

<h2><%=dataErrors.size()%> data errors in address book</h2>
<p>
<%
for (String s: dataErrors) { 
	out.println (Util.escapeHTML(s) + "<br/>\n");
}
}
%>

<% if (m != null)  {
	dataErrors = m.getDataErrors();
	if (dataErrors != null) {
		%>
		<hr/>
		<h2><%=dataErrors.size()%> data errors in message fetching/parsing</h2>
		<%
		for (String s: dataErrors) { 
			out.println (Util.escapeHTML(s) + "<br/>\n");
		}
	}
}
%>

</div>
<jsp:include page="footer.jsp"/>
</body>
</html>
