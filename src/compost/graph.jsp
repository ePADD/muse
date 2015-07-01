<%@page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="org.json.*"%>
<% 	JSPHelper.logRequest(request); 
// if address book not in session, nothing can be done
AddressBook ab = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
Collection<EmailDocument> allDocs = (Collection) JSPHelper.getSessionAttribute(session, "emailDocs"); 


Map<Integer, Collection<Collection<String>>> namesMap = ImportWikileaks.wlContents (allDocs);
ImportWikileaks.writeToFile("/Users/hangal/wikileaks/gmail-sent.5000.names", namesMap);
// Map<Integer, List<String>> namesMap = (Map<Integer, List<String>>) readFromFile("/Users/hangal/wikileaks/cables.500.names");
Digraph.doIt(namesMap, "/tmp/wl.graph");
out.println ("Done");
if (out != null)
	return;

%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<title>Overview</title>
<jsp:include page="../css/css.jsp"/>
</head>
<body class="backdrop">

<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/muse.js"></script>

<%@include file="../header.html"%>

<div id="main" style="max-width:1200px">

<div class="panel info" style="padding: 2% 5% 2% 5%;">
<br/>
<%
	Archive driver = (Archive) JSPHelper.getSessionAttribute(session, "indexDriver");
Indexer indexer = null;
if (driver != null)
	indexer = driver.indexer;

String bestName = ab.getBestNameForSelf();
Pair<String, List<Pair<String,Integer>>> p = null; // JSONUtils.getDBpediaForPerson(bestName);
String photoURL = (p == null) ? null : p.getFirst();
List<Pair<String,Integer>> names = (p == null) ? null : p.getSecond();

	int o = ab.getOutMessageCount(allDocs);
	
	//String photoURL = "http://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Ben_Shneiderman_at_UNCC.jpg/200px-Ben_Shneiderman_at_UNCC.jpg";
%>
	
	<%	if (photoURL != null) { %>
		<div style="float:left;width:70px; border-right: 1px red dotted; padding-right: 30px; ">
			<a href="<%=photoURL%>"><img class="rounded" width="70px" style="position:relative;" src="<%=photoURL%>"/></a>
		</div>
	<%	} %>
	
	<div class="info-header" style="float:left; width:600px;padding-top:5px; padding-left: 30px; ">
		<span style="font-weight: bold; font-size:20pt;text-transform: uppercase;">Email Archive<%=!Util.nullOrEmpty(bestName) ? (" of " + bestName) : " SUMMARY"%></span>
		<div class="info-header" style="font-size:small; line-height: 1em; text-transform: uppercase;">
			<br/><%=IndexUtils.getDateRangeAsString((List) allDocs)%> 
		</div>
	</div>

	<div style="clear:both"></div>
	
	<hr style="color: rgba(255,0,0,0.2); background-color: rgba(255,0,0,0.2)"/>
	<div style="margin-left:30px">
	<%=Util.commatize(allDocs.size()) %> EMAILS (<%=Util.commatize(o)%> outgoing, <%=Util.commatize(allDocs.size()-o)%> incoming)<br/>
	<%
	AddressBookStats abs = ab.getStats();
	%>
	<%=Util.commatize(abs.nContacts)%> PEOPLE <br/>
	<!--  
	<%=Util.commatize(abs.nNames)%> names <br/>
	<%=Util.commatize(abs.nEmailAddrs)%> email addresses <br/>
	-->
	
	<%
			String stats = ab.getStatsAsString(false); // don't blur, this report is for the user's own consumption
			JSPHelper.log.info ("STATS: " + stats);
			stats = Util.escapeHTML(stats);
			String htmlStats = stats.replace("\n", "<br/>\n");
		//	out.println(htmlStats);

		GroupAssigner groupAssigner = (GroupAssigner) JSPHelper.getSessionAttribute(session, "groupAssigner");
		if (groupAssigner != null)
		{
			List<SimilarGroup<String>> groups = groupAssigner.getSelectedGroups();
			Set<String> peopleInGroups = new LinkedHashSet<String>();
			int nIndividuals = 0;
			for (SimilarGroup<String> g: groups)
			{
				peopleInGroups.addAll(g.elements()); 
				if (g.size() == 1)
			nIndividuals++;
			}
		%>

	<%=groups.size() %> GROUPS
	 (with <%=peopleInGroups.size() %> people
	covering <%=Util.commatize(groupAssigner.docsOwnedByAnyColor(allDocs)) %> messages)<br/>

	<%
	String gstats = groupAssigner.toString(allDocs);
	JSPHelper.log.info ("Group STATS: " + gstats);
	String ghtmlStats = gstats.replace("\n", "<br/>\n");
	//out.println(htmlStats);
}

int nAttachments = EmailUtils.countAttachmentsInDocs(allDocs);
%>
	<%=Util.commatize(nAttachments)%> ATTACHMENTS <br/>

<!-- You have <%=Util.commatize(abs.nOwnNames)%> names, <%=Util.commatize(abs.nOwnEmails)%> email addresses  <br/>  -->	
</div>

<div style="clear:both"></div>
<br/>
<div style="margin-left:30px">
What next? Click on the icons below to view your sentiment patterns, groups, calendar highlights, and attachments.<br/>
</div>
<br/>
<br/>
<div style="margin-left:30px">
<div style="position:relative;float:left;width:200px;height:200px">
	<img style="position:relative" id="img1" width="120px" style="margin-right:50px" onmouseover="$('#img1').animate({'width': '183px', 'left': '-30px', 'top':'-30px'}, 'fast');" onmouseout="$('#img1').css('width', '120px').css('left', '0px').css('top', '0px');" onclick="javascript:window.location='stackedGraph?view=sentiments'" src="images/emotions_03.png"/> &nbsp;&nbsp;
</div>
<div style="position:relative;float:left;width:200px;height:200px">
	<img style="position:relative" id="img2" width="120px" style="margin-right:50px" onmouseover="$('#img2').animate({'width': '183px', 'left': '-30px', 'top':'-30px'}, 'fast');" onmouseout="$('#img2').css('width', '120px').css('left', '0px').css('top', '0px');" onclick="javascript:window.location='stackedGraph?view=groups'" src="images/groups_03.png"/> &nbsp;&nbsp;
	<br/>
</div>
<div style="position:relative;float:left;width:200px;height:200px">
	<img style="position:relative" id="img3" width="120px" style="margin-right:50px" onmouseover="$('#img3').animate({'width': '183px', 'left': '-30px', 'top':'-30px'}, 'fast');" onmouseout="$('#img3').css('width', '120px').css('left', '0px').css('top', '0px');" onclick="javascript:window.location='cards'" src="images/calendar_03.png"/> &nbsp;&nbsp;
</div>

<% if (nAttachments > 0)  { %>
<div style="position:relative;float:left;width:200px;height:200px">
	<img style="position:relative" id="img4" width="120px" onmouseover="$('#img4').animate({'width': '183px', 'left': '-30px', 'top':'-30px'}, 'fast');" onmouseout="$('#img4').css('width', '120px').css('left', '0px').css('top', '0px');" onclick="javascript:window.location='attachments'" src="images/attachments_03.png"/> &nbsp;&nbsp;
</div>
<% } %>

<div style="clear:both"></div>

	<!--  
	<button class="tools-pushbutton" onclick="javascript:window.location='stackedGraph?view=sentiments'">Sentiments</button> &nbsp;&nbsp;
	<button class="tools-pushbutton" onclick="javascript:window.location='stackedGraph?view=groups'">Groups</button> &nbsp;&nbsp;
	<button class="tools-pushbutton" onclick="javascript:window.location='cards'">Calendar</button> &nbsp;&nbsp;
	<button class="tools-pushbutton" onclick="javascript:window.location='attachments'">Attachments</button> &nbsp;&nbsp;
	-->
</div>	
<div style="margin-left:30px">
Muse is an experimental prototype, so after you are done please <a style="color:white" href="feedback.jsp">send us your feedback</a>. 
This will help us improve future versions.
</div>

	<hr style="color: rgba(255,0,0,0.2); background-color: rgba(255,0,0,0.2)"/>

<span style="font-size:small">
<script type="text/javascript">
function saveSession() {
	var sessionName = prompt('Enter a name for the session');
	muse.save_session(sessionName, '#none', null);
}
</script>

<a href="#" onclick="saveSession()" class="clickableLink">Save this session</a>
<span style="color:black">&bull;</span>

<a href="referenceText" class="clickableLink">Archivist tools</a>
</span>
&nbsp;&nbsp;
<!-- 
<span style="font-size:small;padding-left: 20px">
<a class="clickableLink" href="info.jsp">Developer information</a>
</span>
 -->
</div> 
<p>

<%@include file="../footer.jsp"%>
</div> <!--  main -->
</body>
</html>
