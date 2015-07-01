<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>

<%
	JSPHelper.logRequest(request);
	Archive archive = JSPHelper.getArchive(session);
	if (archive == null)
	{
		if (!session.isNew())
			session.invalidate();
	%>
	    <script type="text/javascript">window.location="index.jsp";</script>
	<%
		System.err.println ("Error: session has timed out, archive is null");
		return;
	}
	AddressBook addressBook = archive.addressBook;
	Collection<EmailDocument> allDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
	if (allDocs == null)
		allDocs = (Collection) archive.getAllDocs();
	boolean groupsMode = "groups".equals(JSPHelper.getSessionAttribute(session, "mode"));
%>

<html>
<head>
<title>Organize Groups</title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/protovis.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/diary.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>
<script type="text/javascript" src="js/renderPerson.js"></script>
<script type="text/javascript" src="js/json2.js"></script>
<script type="text/javascript" src="js/dnd.js"></script>
<script type="text/javascript" src="js/proto_funcs.js"></script>

</head>
<body>
<!--  <div style="padding:2%; background-color:ffdead;">
<b>September study participants only:</b><br/>
Please compare the results of the following options, focusing on 2-3 months with high email traffic.
Each option opens in a new window or tab.<br/><br/>
<a target="_blank" href="cards?unigrams=true&nogroups=true">Option 1</a> (words only, no groups)
<a target="_blank" href="cards?nogroups=true">Option 2</a> (phrases, no groups)<br/><br/>
Then organize your groups below, and click on:<br/>
<a target="_blank" href="cards?unigrams=true">Option 3</a> (words only, groups)
<a target="_blank" href="cards">Option 4</a> (phrases, groups)<br/>
<br/>
</div>
-->
<script type="text/javascript">
// todo: still need some way to delete groupings (muse.deleted)
function refreshGroups() { window.location='groups'; return false; }
var saveGrouping = function() {
	var title = prompt ("Please enter a name for this grouping:");
	muse.save_grouping(title, '#saveGroupingSpinner', /* redirect to self to refresh page */ window.location);
}
var loadGrouping = function(title) {
	muse.load_grouping(title, '#spinner', /* redirect to self to refresh page */ window.location);
}

// global alert
var stack_layout_offset = 'zero';
var stacked_graph = null;

// global alert
function update_stacked_graph(event)
{
	if (event.target.value === 'counts')
		stacked_graph.set_counts_view(1);
	else
	{
		stacked_graph.set_counts_view(0);
		stack_layout_offset = event.target.value;
	}
	stacked_graph.render();
}
var click_func = function(d, l) { window.open('browse?groupIdx='+labels[l], '_newtab');};

</script>

<div id="groupsarea" style="width:1100px"> <!--  1500 px because we want indiv, groups and everyone also all to appear at the same level -->
<div align="center">
<h2>Organize your groups</h2>
<hr width="70%">
</div>

<p>
<!-- 
<div class="db-hint" style="margin-left: 5%; margin-right: 5%;">
These groups have been derived automatically from your email. Each group is assigned a color. (Note: Colors rotate after <%=JSPHelper.nColorClasses %> groups.)<br/>

You can edit the groups by dragging, dropping, duplicating and deleting people names. <br/>
You can also merge, clone and delete entire groups, and give meaningful names to the groups.<br/>
Until you give a group a name, Muse assigns it a default name which looks something like this: &lt;first member&gt; + &lt;the number of other members in it&gt;.<br/>
You can hover over a name to see all the email addresses associated with it. Muse automatically merges different email addresses associated with a single name.<br>
To reassign colors after editing, click on Refresh Colors, and use the browser's back button to go back to the previous screen.
 -->
<!--   If you use the <a href="http://people.mozilla.com/~mhanson/contacts/contacts-0.4-relbundle-rel.xpi">Firefox Contacts add-on</a>, you can see your friends' pictures as well. -->
</div>
<p>
<a class="clickableLink" href="#" onclick="refreshGroups()">Refresh Colors</a> &bull;
<a class="clickableLink" href="help#groups-editor">Help</a> &bull;
<a class="clickableLink" onclick="saveGrouping()">Save grouping</a> &bull;
<a class="clickableLink" href="info">Back to summary</a>

<img id="saveGroupingSpinner" style="visibility:hidden;position:relative;top:3px;" width="15" src="images/spinner.gif"/>

<%

String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
Collection<String> groupConfigs = GroupsConfig.list(cacheDir);
if (!Util.nullOrEmpty(groupConfigs))
{
	%>
	<br/>
	<a class="clickableLink">Load grouping:</a>
	<img id="loadGroupingSpinner" style="visibility:hidden" width="15" src="images/spinner.gif"/>
	<%
	int i = 0;
	for (String gc: groupConfigs)
	{
		String onclick = "muse.load_grouping('" + gc + "', '#loadGroupingSpinner', window.location)";
		out.println ("<a class=\"clickableLink\" onclick=\"" + onclick + "\">" + gc + "</a>");
		if (++i < groupConfigs.size())
			out.println ("&nbsp;&bull;&nbsp;");
	}
}	
%>
<div style="float:left;margin-right:20px;">
<%
	GroupAssigner groupAssigner = archive.groupAssigner;
//	groupAssigner.orderIndividualsBeforeMultiPersonGroups();
	List<SimilarGroup<String>> groups =  groupAssigner.getSelectedGroups();

	int nColumns = 2;
	out.println(GroupsUI.getGroupsDescriptionWithColor(addressBook, groupAssigner, (Collection) allDocs, true, nColumns)); // if groups mode, no printing of individuals
	
%>
</div>
<p>
<%
List<Contact> contacts = addressBook.sortedContacts(allDocs);
Set<String> selectedContacts = new LinkedHashSet<String>();

// for the all column, retain only the names that have sent to, above the threshold
// this is somewhat flaky because the same contact can be double-counted in the contactsToDatesMap, but that's ok.
Map<Contact, Pair<List<Date>, List<Date>>> map = EmailUtils.computeContactToDatesMap(addressBook, allDocs);
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
<div style="float:left;margin-left:20px;border:solid 1px;border-color: rgba(128,128,128,0.5)">

<a name="everyone"></a>
	<div align="center">
		<h3>Address Book <br/></h3>
		<input placeholder="filter" type="text" size="25" id="filter-addressbook"/>
		<img id="filter-spinner" src="images/spinner.gif" height="15px" style="visibility:hidden">
		<hr style="background-color:rgba(0,0,0,0.2); color: rgba(0,0,0,0.2))"/>
	</div>
	<div class="group" id="everyoneElse">
	<div id="addressbook">
	<%=GroupsUI.htmlForPersonList(g, addressBook, "tagCloud-m1", false, false, false)%>
	</div>
	</div>
<% } %>
<p/>
</div>
<div style="clear:both"></div>
<br/>
<!-- 
<a href="showGrouperMoves.jsp">Visual explanation</a>
 -->
<script>
// we want keyup here, not keypress, because otherwise we don't see the last char input in the field (event has not yet reached the input field)
$('#filter-addressbook').keyup(function(e) {
	var key = (e.keyCode ? e.keyCode : e.which);
	if (key == 33 || key == 34 || key == 38 || key == 40)
		return; // do nothing if its arrow keys

	$('#filter-spinner').css('visibility', 'visible');
	var filter = $('#filter-addressbook').val().toLowerCase();
	filter = muse.trim(filter);
	muse.log ('filtering addressbook to: ' + filter + ' (' + filter.length + ' chars)');
	$('#addressbook li.person').each(function(i, val) { 
		var name = $(this).text().toLowerCase(); // optionally make it $(this).attr('title') to match whole title
		if (name.indexOf(filter) < 0)
			$(this).css('display', 'none'); // hide this element
		else
			$(this).css('display', 'inherit'); // basically normally display
	});
	$('#filter-spinner').css('visibility', 'hidden');	
});

</script> 
</div>
<jsp:include page="footer.jsp"/>
</body>
</html>
