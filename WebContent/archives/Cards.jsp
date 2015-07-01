<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>

<%
JSPHelper.logRequest(request);

String archiveId = request.getParameter("aId");
String archiveName = Sessions.getArchiveInfoMap(archiveId).get("name");
String archiveUrlParam = archiveId == null ? "" : "aId=" + archiveId;

Archive archive = Sessions.loadSharedArchiveAndPrepareSession(session, archiveId);

Indexer indexer = (archive != null) ? archive.indexer : null;
Summarizer summarizer = (indexer != null) ? indexer.summarizer : null;
AddressBook addressBook = (archive != null) ? archive.addressBook : null;
List<Document> allDocs = archive.getAllDocs(); // must get List

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

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>

<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="shortcut icon" href="http://library.stanford.edu/sites/all/themes/sulair_framework/favicon.ico" type="image/vnd.microsoft.icon" />

<!-- JavaScript Sources -->
<link rel="stylesheet" href="/muse/js/jquery-lightbox/css/jquery.lightbox-0.5.css" type="text/css" media="screen" />

<script type="text/javascript" src="/muse/js/jquery/jquery.js"></script>
<script type="text/javascript" src="/muse/js/jquery/jquery.tools.min.js"></script>
<script type="text/javascript" src="/muse/js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="/muse/js/jquery.safeEnter.1.0.js"></script>
<script type="text/javascript" src="/muse/js/jquery-lightbox/js/jquery.lightbox-0.5.min.js"></script>
<script type="text/javascript" src="/muse/js/jquery-lightbox/js/jquery.lightbox-0.5.pack.js"></script>
<script type="text/javascript" src="/muse/js/protovis.js"></script>
<script type="text/javascript" src="/muse/js/muse.js"></script>
<script type="text/javascript" src="/muse/js/diary.js"></script>
<script type="text/javascript" src="/muse/js/statusUpdate.js"></script>
<script type="text/javascript" src="/muse/js/renderPerson.js"></script>
<script type="text/javascript" src="/muse/js/json2.js"></script>
<script type="text/javascript" src="/muse/js/dnd.js"></script>

<jsp:include page="/css/css.jsp"/>

<link rel=StyleSheet href="general.css" type="text/css">
<link rel=StyleSheet href="searchresult.css" type="text/css">
<link rel=StyleSheet href="bootstrap/css/bootstrap.css" type="text/css">

<title>Monthly Terms of <%=Util.escapeHTML(archiveName)%></title>
</head>

<style>
select {
	width: auto;
}
#actual_cards .db-hint a, .groups-list a {
	text-decoration: underline;
}
</style>

<body style="margin: 0px;">
	<div class="site-wrapper">
		<div id="wrapper" class="clearfix">
			<div id="wrapper-inner">
				<%@include file="header.html"%> 
				<%@include file="title.html"%> 

				<nav class="breadscrumb">
					<a href="/muse/archives">Home</a>
					&gt;
					<span>
					<a href="info?aId=<%=Util.URLEncode(archiveId)%>">
					<%=Util.escapeHTML(archiveName)%>
					</a>
					</span>
					&gt;
					<span>
					Monthly Terms
					</span>
				</nav>
				<br>

				<div id="searchresult-area">

					<div id="searchresult-info" align="center">

					<%@include file="/cards_table.jnc"%>

					</div>

				</div>

			</div>
		</div>
	</div>
	
	
</body>
<%@include file="footer.html"%>
<% JSPHelper.logRequestComplete(request); %>
</html>
