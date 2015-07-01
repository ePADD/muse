<%@page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="org.json.*"%>
<%
	JSPHelper.logRequest(request); 

// if archive not in session, nothing can be done
Archive archive = JSPHelper.getArchive(session);
if (archive == null)
{
	session.setAttribute("checkCookie", true);
	response.sendRedirect("checkCookie.jsp");
	return;
}
AddressBook ab = archive.addressBook;
String addressBookUpdate = request.getParameter("addressBookUpdate");
if (!Util.nullOrEmpty(addressBookUpdate))
	ab.initialize(addressBookUpdate);

Collection<EmailDocument> allDocs = (Collection) JSPHelper.getSessionAttribute(session, "emailDocs"); 
Collection<EmailDocument> fullEmailDocs = (Collection) archive.getAllDocs();
if (allDocs == null)
	allDocs = fullEmailDocs;

Indexer indexer = null;
if (archive != null)
	indexer = archive.indexer;

String bestName = ab.getBestNameForSelf();
String title = "Email Archive " + (!Util.nullOrEmpty(bestName) ? ("of " + bestName) : "SUMMARY");
Contact me = ab.getContactForSelf();
String title_tooltip = title;
if (me != null)
	title_tooltip = "a.k.a." + me.toTooltip();
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
<title><%=Util.escapeHTML(title)%></title>
<meta name="viewport" content="width=device-width">
<jsp:include page="css/css.jsp"/>
</head>
<body class="fixed-width">


<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script src="js/jquery/jquery.tools.min.js" type="text/javascript"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
<script type="text/javascript" src="js/jquery-lightbox/js/jquery.lightbox-0.5.min.js"></script>
<script type="text/javascript" src="js/jquery-lightbox/js/jquery.lightbox-0.5.pack.js"></script>

<jsp:include page="header.jsp"/>

<div id="main" class="panel" style="max-width:1200px">

<div class="info" style="padding: 2% 5% 2% 5%;">
<br/>
<%

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
	
	<div class="info-header" style="float:left; width:800px;padding-top:5px; padding-left: 30px; ">
		<span style="font-weight: bold; font-size:20pt;text-transform: uppercase;" title="<%=Util.escapeHTML(title_tooltip)%>"><%=Util.escapeHTML(title)%></span>
		<div class="info-header" style="font-size:small; line-height: 1em; text-transform: uppercase;">
			<br/><%=IndexUtils.getDateRangeAsString((List) allDocs)%> 
		</div>
	</div>

	<div style="clear:both"></div>
	
	<br/>
	<div style="border:1px solid rgba(255,255,255,0.2);padding-top:40px">
	<div style="margin-left:30px" class="info-header">
	
	<div class="infocolumn">
		<span class="infocolumn-header"><%=Util.commatize(allDocs.size()) %> MESSAGE<%=allDocs.size() != 1 ? "S":"" %></span> <br/>
		<%=Util.commatize(o)%> outgoing<br/>
		<%=Util.commatize(allDocs.size()-o)%> incoming<br/>
		<%=Util.commatize(EmailUtils.threadEmails(allDocs).size())%> threads
	</div>
	
	<%
	AddressBookStats abs = ab.getStats();
	%>
	<div class="infocolumn">
	<span class="infocolumn-header"><%=Util.commatize(abs.nContacts)%> PEOPLE</span> <br/>
	</div>
	<!--  
	<%=Util.commatize(abs.nNames)%> names <br/>
	<%=Util.commatize(abs.nEmailAddrs)%> email addresses <br/>
	-->
	
	<%
		String stats = ab.getStatsAsString(false); // don't blur, this report is for the user's own consumption
		JSPHelper.log.info ("Addressbook stats: " + stats);

		GroupAssigner groupAssigner = archive.groupAssigner;
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
	<div class="infocolumn">
		<span class="infocolumn-header"><%=groups.size() %> GROUPS</span><br/>
		Covering <%=peopleInGroups.size() %> people<br/>
		and <%=Util.commatize(groupAssigner.docsOwnedByAnyColor(allDocs)) %> messages<br/>
	</div>
	<%
	String gstats = groupAssigner.toString(allDocs);
	JSPHelper.log.info ("Group STATS: " + gstats);
	String ghtmlStats = gstats.replace("\n", "<br/>\n");
	//out.println(htmlStats);
}

int nAttachments = EmailUtils.countAttachmentsInDocs(allDocs);
int nImageAttachments = EmailUtils.countImageAttachmentsInDocs(allDocs);
%>

	<div class="infocolumn">
		<span class="infocolumn-header"><%=Util.commatize(nAttachments)%> ATTACHMENT<%=nAttachments != 1 ? "S":"" %> </span><br/>
		<%=Util.commatize(nImageAttachments)%> images
	</div>

<!-- You have <%=Util.commatize(abs.nOwnNames)%> names, <%=Util.commatize(abs.nOwnEmails)%> email addresses  <br/>  -->	
</div>

<div style="clear:both"></div>
<br/>
<div style="margin-left:30px">
What next? Click on the icons below to view your sentiment patterns, groups, calendar highlights, and attachments.
<br/>
New! Try connecting your archives to your web browsing with the Muse lens.
</div>
<br/>
<br/>
<div class="info-icons">

<div class="info-icon-container">
	<img title="Sentiments" class="animated-img" width="100px" style="margin-right:50px" onclick="javascript:window.location='stackedGraph?view=sentiments'" src="images/emotions_03.png"/> &nbsp;&nbsp;
</div>

<div class="info-icon-container">
	<img title="Groups" class="animated-img" style="margin-right:50px" onclick="javascript:window.location='stackedGraph?view=groups'" src="images/groups_03.png"/> &nbsp;&nbsp;
	<br/>
</div>

<div class="info-icon-container">
	<img title="Monthly terms" class="animated-img" style="margin-right:50px" onclick="javascript:window.location='cards'" src="images/calendar_03.png"/> &nbsp;&nbsp;
</div>

<div class="info-icon-container">
	<img title="Attachments" class="animated-img" onclick="javascript:window.location='attachments'" src="images/attachments_03.png"/> &nbsp;&nbsp;
</div>

<!-- 
<div class="info-icon-container">
	<img title="Personal crossword"  class="animated-img" style="margin-right:50px" onclick="javascript:window.location='crossword'" src="images/xword.png"/> &nbsp;&nbsp;
</div> 
 -->
 
<div class="info-icon-container">
	<img title="Browsing lens" class="animated-img" style="margin-right:50px" onclick="javascript:window.location='lens'" src="images/lens.png"/> &nbsp;&nbsp;
</div> 


<script type="text/javascript">
$('.animated-img').mouseover(function(e) { 
	$('.animated-img').stop(); // stop existing animation on any image. Needed because an animation may already be in progress. we used to have the problem of multiple images being zoomed at the same time.
	$('.animated-img').css('width', '100px').css('left', '0px').css('top', '0px');	// reset sizes of all images
	$(e.currentTarget).animate({'width': '152px', 'left': '-25px', 'top':'-25px'}, 'fast'); // animate this image
});
$('.animated-img').mouseout(function(e) { 
	$(e.currentTarget).stop();
	$(e.currentTarget).css('width', '100px').css('left', '0px').css('top', '0px');	// reset size of this image
});
</script>
<div style="clear:both"></div>
</div> <!--  .info-icons -->

	<!--  
	<button class="tools-pushbutton" onclick="javascript:window.location='stackedGraph?view=sentiments'">Sentiments</button> &nbsp;&nbsp;
	<button class="tools-pushbutton" onclick="javascript:window.location='stackedGraph?view=groups'">Groups</button> &nbsp;&nbsp;
	<button class="tools-pushbutton" onclick="javascript:window.location='cards'">Calendar</button> &nbsp;&nbsp;
	<button class="tools-pushbutton" onclick="javascript:window.location='attachments'">Attachments</button> &nbsp;&nbsp;
	-->
</div>	

<span style="font-size:small">
<a href="browse" class="clickableLink">Browse all messages</a>&bull;
<a href="search-query" class="clickableLink">Search</a>&bull;
<a class="clickableLink" href="dataReport">Data Quality</a>

<!-- 
<span style="font-size:small;padding-left: 20px">
<a class="clickableLink" href="info.jsp">Developer information</a>
</span>
 -->
</span>
</div> 
<p>

<%
int visits = 0;
javax.servlet.http.Cookie[] cookies = request.getCookies();
for (javax.servlet.http.Cookie cookie: cookies)
	if ("visits".equals(cookie.getName()))
	{
		try { visits = Integer.parseInt(cookie.getValue()); } catch (NumberFormatException nfe) { }
		break;
	}

javax.servlet.http.Cookie cookie1 = new javax.servlet.http.Cookie("visits", Integer.toString(visits+1));
cookie1.setMaxAge(24*60*60*7); // 7 days
response.addCookie(cookie1); 

if (visits < 5) { %>
	<script type="text/javascript">
		$(document).ready(function() { $.jGrowl('<span class="growl">Please send us your comments about Muse after you are done, using the link at the bottom of this page. Thanks!</span>'); }); 
	</script>
	<% 
} 	

	JSPHelper.logRequestComplete(request); 
%>

</div> <!--  main -->
<%@include file="footer.jsp"%>
</body>
</html>
