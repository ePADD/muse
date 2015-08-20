<%@page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="org.json.*"%>
<% 	JSPHelper.logRequest(request); %>
<!DOCTYPE HTML>
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<title>Report final statistics</title>
<jsp:include page="css/css.jsp"/>
</head>
<body>

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
	document.getElementById('submitForm').action = "http://prpl.stanford.edu/report/field_report.php";
	// we'll send back everything inside the main div
	// add on the current value of the submitter field since its not in the inner html
	var data = 'Submitter is ' + document.getElementById('submitter').value + escape(document.getElementById('main').innerHTML);
	// jam in the current page into the submit form as another hidden var
	document.getElementById('submitForm').innerHTML = document.getElementById('submitForm').innerHTML + '<input type="hidden" name="debugpage" value="' + data + '"></input>';
	document.getElementById('submitForm').submit();
//	document.getElementById('submitFormDiv').innerHTML = "Thank you for submitting a problem report.";
}

</script>

<jsp:include page="header.jsp"/>

Thank you for testing Muse. Please click submit to send
high-level statistics about your session back to Stanford.
These statistics are used only for research purposes and to improve in the program.
If you would like access to future versions of Muse, please send email to hangal@cs.stanford.edu.
<p>
<div id="main">

<div id="submitFormDiv">
    <form method="post" id="submitForm" action="http://prpl.stanford.edu/report/field_report.php">
    <input type="hidden" name="to" value="hangal@cs.stanford.edu"></input>
	<input type="button" value="Submit" onclick="javascript:sendDebugInfo()"></input> these statistics back to Stanford.
	<p>
	Your email address (optional) <input id="submitter" type="text" value="" size="40"></input>
	</form>
</div>

<b>Muse version v<%=Version.num%></b><p/>

<p/> <p/> <p/> <p/>
    <%@page language="java" import="edu.stanford.muse.index.*"%>

    <b>Browser</b><p/>
    <%= request.getHeader("User-agent")%>
<p/> <p/> <p/> <p/>

<% Collection<EmailDocument> allDocs = (Collection) JSPHelper.getSessionAttribute(session, "emailDocs"); %>

<b>Corpus</b>
<p>
<%
Archive archive = JSPHelper.getArchive(session);
if (allDocs != null) {
	out.println (allDocs.size() + " docs with " + EmailUtils.countAttachmentsInDocs(allDocs) + " attachments<br/>\n");
	out.println ("Attachments store: " + archive.blobStore + "<br/>\n");
}

%>
<p>

<b>Last Indexer Run Stats</b>
<p>
<%
if (archive != null && archive.indexer != null)
{
	String stats = archive.indexer.computeStats();
	JSPHelper.log.info ("STATS: " + stats);
	String htmlStats = stats.replace("\n", "<br/>\n");
	out.println(htmlStats);
}
%>
<p>
<b>Contacts Stats</b>
<p>
<%
AddressBook ab = archive.addressBook;
if (ab != null)
{
	String stats = ab.toString();
	JSPHelper.log.info ("STATS: " + stats);
	String htmlStats = stats.replace("\n", "<br/>\n");
	out.println(htmlStats);
}
%>

<p>
<b>Groups distribution</b>
<p>
<%
	GroupAssigner groupAssigner = archive.groupAssigner;
if (groupAssigner != null)
{
	String stats = groupAssigner.toString(allDocs);
	JSPHelper.log.info ("STATS: " + stats);
	String htmlStats = stats.replace("\n", "<br/>\n");
	out.println(htmlStats);
}
%>
</p>
    <b>Session attributes</b><p/>
    <% long created = session.getCreationTime();
       long accessed = session.getLastAccessedTime();
       long activeTimeSecs = (accessed - created)/1000;
       long sessionHours = activeTimeSecs/3600;
	   activeTimeSecs = activeTimeSecs%3600;
       long sessionMins = activeTimeSecs/60;
       long sessionSecs = activeTimeSecs%60; %>
    Session created <%= new Date(created) %>, active for <%=sessionHours%> hours, <%=sessionMins%> minutes, <%=sessionSecs%> seconds<br/>
    Last accessed <%= new Date(accessed)%>

    <p>

    <b>Memory status</b><p/>
    <%=Util.getMemoryStats()%><br/>

<p/>

<b>System properties</b><p/>

<%
	java.util.Hashtable properties = System.getProperties();
	for (Object obj: properties.entrySet())
	{
		java.util.Map.Entry entry = (java.util.Map.Entry) obj;
		String key = (String) entry.getKey();
		String val = (String) entry.getValue();
		out.println("<font color=\"red\">" + key + "</font>: " + val + "<br>");
	}
%>
<br/>

<%@include file="footer.jsp"%>
</div> <!--  main -->
</body>
</html>
