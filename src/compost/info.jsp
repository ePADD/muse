<%@page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.bespoke.mining.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="org.json.*"%>
<% 	JSPHelper.logRequest(request); %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<title>Information</title>
<jsp:include page="css/css.jsp"/>
</head>
<body class="fixed-width">

<%@include file="header.html"%>
<hr style="margin-top:-6px">

<p>
<div id="main" class="info">
<div style="padding: 2% 5% 2% 5%" class="info">

<br/>
<% 
Collection<EmailDocument> allDocs = (Collection) JSPHelper.getSessionAttribute(session, "emailDocs"); 
AddressBook ab = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
%>

<p>

<p>
<%

if (allDocs != null) {
	int o = ab.getOutMessageCount(allDocs);
	out.print (Util.commatize(allDocs.size()) + " message" + (allDocs.size() > 1 ? "s":"") + " (");
	out.print (Util.commatize(o) + " outgoing, " + Util.commatize(allDocs.size()-o) + " incoming) ");
	out.println (" from " + IndexUtils.getDateRangeAsString((List) allDocs) + "<br/>\n");
	out.println (EmailUtils.countAttachmentsInDocs(allDocs) + " attachments <br/>");
}
%>
<p>

<b>Contacts</b>
<p>
<%
if (ab != null)
{
	String stats = ab.getStatsAsString(false); // don't blur, this report is for the user's own consumption
	JSPHelper.log.info ("STATS: " + stats);
	// stats = Util.escapeHTML(stats);
	String htmlStats = stats.replace("\n", "<br/>\n");
	out.println(htmlStats);
}
%>

<p>
<b>Groups:</b>
<p>
<%
	GroupAssigner groupAssigner = (GroupAssigner) JSPHelper.getSessionAttribute(session, "groupAssigner");
if (groupAssigner != null)
{
	String stats = groupAssigner.toString(allDocs);
	JSPHelper.log.info ("Group STATS: " + stats);
	String htmlStats = stats.replace("\n", "<br/>\n");
	out.println(htmlStats);
}
%>
</p>
<p>

<b>Blob store</b>
<p>
<%
	BlobStore bs = (BlobStore) JSPHelper.getSessionAttribute(session, "attachmentsStore");
	if (bs != null)
		out.println (bs.uniqueBlobs() + " unique blobs" + "<br/>\n");
%>
<p>
<b>Indexer Statistics</b><br/>
(for the developers)
<p>
<%
	Archive driver = (Archive) JSPHelper.getSessionAttribute(session, "indexDriver");
if (driver != null && driver.indexer != null)
{
	String stats = driver.indexer.computeStats(false);
	JSPHelper.log.info ("STATS: " + stats);
	String htmlStats = stats.replace("\n", "<br/>\n");
	out.println(htmlStats);
}
%>
<p>
<a href="exportMessagesWithoutAttachments.jsp">Export all messages without attachments</a>

<p>
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

Muse version v<%=Version.num%>
<br/>
    <%@page language="java" import="edu.stanford.muse.index.*"%>

Browser: <%= request.getHeader("User-agent")%>
Memory status: <%=Util.getMemoryStats()%><br/>

<p/>

</div>
</div> <!--  main -->

<%@include file="footer.jsp"%>
</body>
</html>
