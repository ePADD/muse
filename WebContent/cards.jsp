<%@ page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>

<%

	String archiveUrlParam = "";

	Archive archive = JSPHelper.getArchive(session);
	Indexer indexer = (archive != null) ? archive.indexer : null;
	Summarizer summarizer = (indexer != null) ? indexer.summarizer : null;
	AddressBook addressBook = (archive != null) ? archive.addressBook : null;
	Collection<Document> allDocs = (Collection<Document>) JSPHelper.getSessionAttribute(session, "emailDocs");
	if (allDocs == null)
		allDocs = archive.getAllDocs();

	if (indexer == null)
	{
		if (!session.isNew())
			session.invalidate();
%>
	    <script type="text/javascript">window.location="index.jsp";</script>
<%
			System.err.println ("Error: session has timed out");
			return;
	}
			
	boolean nogroups = (request.getParameter("nogroups") != null);
	List<Card> cards = summarizer.cards;
	int nResults = HTMLUtils.getIntParam(request, "n", Summarizer.DEFAULT_N_CARD_TERMS);

	// we may not have cards, e.g. because the filter changed, so recompute them
	if (cards == null)
	{
		// TODO: consider showing a user message here because it might take a while...
		summarizer.recomputeCards(allDocs, addressBook.getOwnNamesSet(), nResults);
	}
	else // even if cards didn't change, nResults may have changed. in which case we need to repopulate the cards
		summarizer.populateCards(nResults, addressBook);
	
	cards = summarizer.cards;

	// recompute top terms if needed.
	// remember to read cards from the session again if terms are recomputed
	if (nResults != -1)
	{
		cards = summarizer.cards;
	}
	else
	{
		nResults = Summarizer.DEFAULT_N_CARD_TERMS;

		if (archive != null && cards == null)
			cards = summarizer.cards;
	}

	GroupAssigner groupAssigner = archive.groupAssigner;
	boolean haveGroups = (groupAssigner != null && groupAssigner.getSelectedGroups() != null && groupAssigner.getSelectedGroups().size() > 0);
%>

<html>
<head>
<Title>Cards</Title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/diary.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>
<script type="text/javascript" src="js/renderPerson.js"></script>
<script type="text/javascript" src="js/json2.js"></script>
<script type="text/javascript" src="js/dnd.js"></script>
<script type="text/javascript" src="js/protovis.js"></script>
<script type="text/javascript" src="js/proto_funcs.js"></script>

<script>
function saveDiary()
{
	var contents = $('#diary').val();
	$.post('ajax/saveDiary.jsp', {diaryContents: contents}, function(data) { });
}
$(document).ready(function() { $('#spinner').hide(); });
</script>

</head>
<body style="margin:0% 1% 1% 1%">
<%@include file="div_status.jsp"%>

<%
	if (haveGroups) {
%>
<div id="div_main" class="cardspage" style="margin-left:140px;border-left:solid 1px rgba(127,127,127,0.4); min-height:1000px">
<%
	} else {
%>
<div id="div_main" class="cardspage" style="min-height:1000px">
<%
	}
%>

<!--
<h2>
<span style="text-decoration:underline" onclick="$('#display').html($('#diary_tab').html())">Diary</span> &nbsp;
<span style="text-decoration:underline" onclick="$('#display').html($('#cards_tab').html())">Cards</span> &nbsp;
</h2>
 -->
<%

String diaryContents = (String) JSPHelper.getSessionAttribute(session, "diaryContents");
if (diaryContents == null)
	diaryContents = "Write your diary here!";
else
	diaryContents = Util.escapeHTML(diaryContents);

boolean timeBasedDocs = true;
for (Document d: allDocs)
	if (!(d instanceof DatedDocument))
	{
		timeBasedDocs = false;
		break;
	}

boolean onlyEmailDocs = true;
for (Document d: allDocs)
	if (!(d instanceof EmailDocument))
	{
		onlyEmailDocs = false;
		break;
	}
%>


<div id="diary_tab" style="display:none">
<br/>
	<div align="center" id="diaryarea">
		<textarea autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false" rows="12" cols="80" class="diary" id="diary"><%=diaryContents%></textarea>
		<br/>
		<br/>
		<button onclick="javascript:saveDiary()">Save</button> &nbsp;&nbsp;&nbsp;&nbsp;
		<button onclick="javascript:window.print()">Print</button> &nbsp;&nbsp;&nbsp;&nbsp;
		<button onclick="javascript:alert('To be implemented!')">Email</button><br/>
		<br/>
	</div>
	<hr width="90%"/>
</div>

<p></p>

<div id="cards_tab">

<%
	if (haveGroups) {
%>
	
<div class="groups-list rounded" style="position:fixed;left:20;top:20;width:120px" id="groups_legend">
Groups (<a href="groups">Refine</a>)<br>
<hr/>
<%=GroupsUI.getGroupNamesWithColor(groupAssigner)%>
<br/>
</div>
<%
	}
%>

<%@include file="cards_table.jnc"%>

<br/>

<script>
//$('.monthly-card').mouseenter(function() { $('.cardmenu', $(this)).css('visibility', 'visible'); }).mouseleave(function() { $('.cardmenu', $(this)).css('visibility', 'hidden'); });
$('.monthly-card').mouseenter(function() { $('.cardmenu', $(this)).fadeIn('slow'); }).mouseleave(function() { $('.cardmenu', $(this)).fadeOut(); });
</script>

<br/>
</div> <!--  cards tab -->
</div> <!-- div_main -->
<jsp:include page="footer.jsp"/>
</body>
</html>
