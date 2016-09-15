<%@page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<!DOCTYPE HTML>
<html lang="en">
<head>
<title>Report a problem</title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
</head>
<body class="color:gray;background-color:white">
<br/>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/muse.js"></script>

<script type="text/javascript">

function new_xhr()
{
	if (window.XMLHttpRequest) {
		return new XMLHttpRequest();
	}
	// IE
	else if (window.ActiveXObject) {
		return new ActiveXObject("Microsoft.XMLHTTP");
	}
}

function sendDebugInfo() {
	var url = "https://musubi.us/report/field_report.php";
	// jam in the current page into the submit form as another hidden var
	// we'll send back everything inside the main div
	$.post(url, {debugpage: $('#main').html()}, function(response) { 
		// actually this is never executed!
		muse.log ('response from debug post is ' + response);
		$('#main').html(response);
	});
	alert ("Thank you for submitting the error report");
	
//	document.getElementById('submitFormDiv').innerHTML = "Thank you for submitting a problem report.";
}

</script>

<jsp:include page="header.jsp"/>

<div id="main">
<br/>
We are sorry you encountered a problem with Muse.
Submitting this report back to Stanford will help us fix it.<br/>

<div id="submitFormDiv">
    <input type="hidden" name="to" value="hangal@cs.stanford.edu"/>
	<p>
	<input id="submitter" style="background-color: #FAFFBD" type="text" value="" placeholder="your email address (optional)" size="40"/><br/><br/>
	<input style="font-size:24px" type="button" value="Submit" onclick="javascript:sendDebugInfo()"/>
	<p>
	<!--
	What were you trying to do when you encountered an error?<br/> 
	<textarea autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false" style="background-color: #FAFFBD" id="context" value="" rows="4" cols="52"></textarea>
  	-->			
	</form>
</div>
<hr style="color:rgba(0,0,0,0.2)"/>
<b>Muse version <%=Version.version %> <%=Version.buildInfo%></b>
<p/>

<% if (ModeConfig.isMultiUser()) { return; } %>
<p/> <p/> <p/> <p/>
    <%@page language="java" %>

    <b>Browser</b><p/>
    <%= request.getHeader("User-agent")%>
<p/> <p/> <p/> <p/>
    <b>Memory status</b><p/>
    <%=Util.getMemoryStats()%><br/>

<br/>


    <b>System properties</b><p/>

<%
	java.util.Hashtable properties = System.getProperties();
	for (Object obj: properties.entrySet())
	{
		java.util.Map.Entry entry = (java.util.Map.Entry) obj;
		String key = (String) entry.getKey();
		String val = (String) entry.getValue();
		out.println("<b>" + key + "</b>: " + val + "<br>");
	}
%>
<br/>
    <b>Session attributes</b><p/>
    <% long created = session.getCreationTime();
       long accessed = session.getLastAccessedTime();
       long activeTimeSecs = (accessed - created)/1000;
       long sessionHours = activeTimeSecs/3600;
	   activeTimeSecs = activeTimeSecs%3600;
       long sessionMins = activeTimeSecs/60;
       long sessionSecs = activeTimeSecs%60; %>
    Session id: <%=session.getId()%>, created <%= new Date(created) %>, last accessed <%= new Date(accessed)%><br/>
    Session active for <%=sessionHours%> hours, <%=sessionMins%> minutes, <%=sessionSecs%> seconds<br/><br/>
    <%
    java.util.Enumeration keys = session.getAttributeNames();

    while (keys.hasMoreElements())
    {
      String key = (String)keys.nextElement();
      if (!"emailDocs".equals(key) && !"lexicon".equals(key) && !"fullEmailDocs".equals(key) && !key.startsWith("dataset") && !key.startsWith("docs-docset") && !key.startsWith("GroupsJSON")) // don't toString email docs which have email headers (too sensitive), same with dataset
	      out.println("<b>" + key + "</b>: " + JSPHelper.getSessionAttribute(session, key) + "<br>");
      else
    	  out.println("<b>" + key + "</b>: not printed<br>");
    }

    String documentRootPath = application.getRealPath("/").toString();

%>
<br/>
<%
	Log4JUtils.flushAllLogs(); // if we dont flush file is sometimes truncated
%>
<%
	String tmpDir = System.getProperty ("java.io.tmpdir");
	String debugFile = tmpDir + File.separator + "muse.log";
	File f = new File(debugFile);
    if (f.exists() && f.canRead())
    {
%>
		<b>Debug log</b> (from <%=debugFile%>)
		<hr style="color:rgba(0,0,0,0.2)"/>
		<div id="testdiv">
<%
    	String s = Util.getFileContents(debugFile);
    	s = s.replace ("\n", "<br/>\n");
		out.println (s);
%>
		</div>
<%
    }
    else
    	out.println ("No debug log in " + debugFile);
%>
<br/>
<p/>

</div> <!--  main -->
<%@include file="footer.jsp"%>
</body>
</html>
