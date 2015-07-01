<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.bespoke.mining.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>

<%
	JSPHelper.logRequest(request);
	AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
	Collection<EmailDocument> allDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");

	if (addressBook == null)
	{
		if (!session.isNew())
			session.invalidate();
//		session.setAttribute("loginErrorMessage", "Your session has timed out -- please click login again.");
	%>
	    <script type="text/javascript">window.location="index.jsp";</script>
	<%
		System.err.println ("Error: session has timed out, addressBook = " + addressBook);
			return;
	}
%>

<html>
<head>
<title>Facebook Groups</title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/diary.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>
<script type="text/javascript" src="js/json2.js"></script>
<script type="text/javascript" src="js/dnd.js"></script>
<script type="text/javascript" src="js/protovis.js"></script>
<script type="text/javascript" src="js/proto_funcs.js"></script>

</head>
<body>
<%
   if ("groups".equals(JSPHelper.getSessionAttribute(session, "mode")))
   {
	%>
		<%@include file="header_groups.html"%>
<% } else { %>
	<%@include file="header.html"%>
<% } %>

<div id="groupsarea">
<div align="center">
<h2>Export your groups to Facebook</h2>
<hr width="70%">
</div>


<div id="instructions" style="margin-left:5%;margin-right:5%">
<%
String base_url = request.getRequestURL().toString();
base_url = base_url.substring(0, base_url.lastIndexOf("/"));
String js_url = base_url + "/js/facebookGroups.js";
String getGroupsUrl = base_url + "/ajax/getGroupsAsJson.jsp";
%>
To use the inferred groups as Facebook friends lists, perform the following steps:<br/>
<ol>
1. <div style="display:inline"><b>Edit</b> the groups. You can delete or merge entire groups, delete people from groups and name groups.
You can also drag and drop people between groups.<br/></div>
2. <div style="display:inline"><a class="bookmarklet" href="javascript:var getGroupsUrl = '<%= getGroupsUrl%>'; (function(){mc_script=document.createElement('SCRIPT'); mc_script.type='text/javascript'; mc_script.src='<%=js_url%>?'; document.getElementsByTagName('head')[0].appendChild(mc_script);})();">Facebook Friends Lists</a>
&larr; <b>Drag</b> this to your browser's bookmarks toolbar. <br/></div>
3. <div style="display:inline"><b>Log in</b> to Facebook in a different window or tab, and <b>click on the bookmarklet</b> while on the Facebook home page. The groups you see will appear as Facebook friends lists.<br/></div>
4. <div style="display:inline">Finally, please <b>fill out</b> a brief <a href="https://spreadsheets.google.com/viewform?formkey=dHZ1MmhZRkhMeU5jYmxTSTl2T25vQWc6MQ">feedback form</a>. This will help improve our research and future versions of this program.<br/></div>
<p>
Need more help ? You can view a small video tutorial <a href="http://tinyurl.com/musegroups/tutorial.htm">here</a>.<br/>
<% String ua = request.getHeader("User-agent");
boolean maybeChrome = (ua == null || ua.toLowerCase().indexOf("chrome") >= 0);
if (maybeChrome) { %>
	<b>Chrome Users:</b><br/>
	For showing the bookmarks bar: <br/>
	1. Click the wrench icon  on the browser toolbar.<br/>
	2. Select Tools. <br/>
	3. Select Always show bookmarks bar.<br/>
<% } %>
</p>
</div>


<div align="center">

<%
	GroupAssigner groupAssigner = (GroupAssigner) JSPHelper.getSessionAttribute(session, "groupAssigner");
	int nColumns = (JSPHelper.getSessionAttribute(session, "fbmode") == null) ? 3 : 2;
	out.println(GroupsUI.getGroupsDescriptionWithColor(addressBook, groupAssigner, (Collection) allDocs, true, nColumns)); // if groups mode, no printing of individuals
%>
<%
List<Contact> contacts = addressBook.sortedContacts(allDocs);
Set<String> selectedContacts = new LinkedHashSet<String>();

Map<Contact, Pair<List<Date>, List<Date>>> map = addressBook.computeContactToDatesMap(allDocs);
int threshold = HTMLUtils.getIntParam(request, "threshold", 3);
for (Contact c: contacts)
{
	Pair<List<Date>, List<Date>> pair = map.get(c);
	if (pair == null)
		continue;

	int nMessages = pair.getFirst().size() + pair.getSecond().size();
	if (nMessages > threshold)
		selectedContacts.add(c.getCanonicalEmail());
}

SimilarGroup<String> g = new SimilarGroup<String>(selectedContacts);
if (g.size() > 0)
{
%>

<a name="everyone"></a>
	<div align="center"><a href="#everyone" onclick="$('#everyoneElse').toggle(); toggleShowAndHide(this); return false;">Show</a> everyone else (under construction)</b></div>
	<div class="group" id="everyoneElse" style="display:none">
	<%=GroupsUI.htmlForPersonList(g, addressBook, "tagCloud-m1", true, true)%>
	</div>
<% }

%>
<p/>

<p>&nbsp;
</div>
