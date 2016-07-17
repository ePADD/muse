<%@page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.Pair"%>
<%
String title = request.getParameter("title");
String loginUrl = ModeConfig.isPublicMode() ? "/muse/archives" : "/muse";

// good to give a meaningful title to the browser tab since a lot of them may be open
String term = request.getParameter("term");
term = JSPHelper.convertRequestParamToUTF8(term);
String sentiments[] = request.getParameterValues("sentiment");
int groupIdx = HTMLUtils.getIntParam(request, "groupIdx", -1);
String[] persons = request.getParameterValues("person");
String[] attachments = request.getParameterValues("attachment");
int month = HTMLUtils.getIntParam(request, "month", -1);
int year = HTMLUtils.getIntParam(request, "year", -1);
int cluster = HTMLUtils.getIntParam(request, "timeCluster", -1);

String sentimentSummary = "";
if (sentiments != null && sentiments.length > 0)
	for (int i = 0; i < sentiments.length; i++)
	{
		sentimentSummary += sentiments[i];	
		if (i < sentiments.length-1)
			sentimentSummary += " & ";
	}
		
if (Util.nullOrEmpty(title))
{
	if (term != null)
		title = "Search: " + term;
	else if (groupIdx != -1)
		title = "Group " + groupIdx;
	else if (cluster != -1)
		title = "Cluster " + cluster;
	else if (sentimentSummary != null)
		title = sentimentSummary;
	else if (attachments != null && attachments.length > 0)
		title = attachments[0];
	else if (month >= 0 && year >= 0)
		title = month + "/" + year;
	else if (year >= 0)
		title = Integer.toString(year);
	else if (persons != null && persons.length > 0)
	{
		title = persons[0];
		if (persons.length > 1)
			title += "+" + (persons.length-1);
	}
	else
		title = "Muse Browse";
}
title = Util.escapeHTML(title);

boolean noFacets = request.getParameter("noFacets") != null;// || ModeConfig.isPublicMode();

if (ModeConfig.isPublicMode()) {
	// this browse page is also used by Public mode where the following set up may be requried. 
	String archiveId = request.getParameter("aId");
	Sessions.loadSharedArchiveAndPrepareSession(session, archiveId);
}

%>
<!DOCTYPE HTML>
<html lang="en">
<head>
<title><%=title%></title>
<META http-equiv="Content-Type" content="text/html; charset=UTF-8">
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<link rel="stylesheet" href="js/jquery-lightbox/css/jquery.lightbox-0.5.css" type="text/css" media="screen" />

<link rel="prefetch" href="images/movieReel0.png">
<link rel="prefetch" href="images/movieReel1.png">
<link rel="prefetch" href="images/movieReel2.png">
<link rel="prefetch" href="images/movieReel3.png">
<link rel="prefetch" href="images/movieReel4.png">
<link rel="prefetch" href="images/movieReel5.png">
<link rel="prefetch" href="images/movieReel6.png">
<link rel="prefetch" href="images/movieReel7.png">
</head>
<body class="fixed-width" style="margin:0px;"> <!--  override margin because this page is framed. -->

<script src="js/jquery/jquery.js" type="text/javascript"></script> 
<script src="js/jquery/jquery.tools.min.js" type="text/javascript"></script>
<script type="text/javascript" src="js/jquery-lightbox/js/jquery.lightbox-0.5.min.js"></script>
<script type="text/javascript" src="js/jquery-lightbox/js/jquery.lightbox-0.5.pack.js"></script>
<!-- <script src="js/jQueryRotateCompressed.2.1.js" type="text/javascript"></script> -->
<!-- <script src="js/protovis.js" type="text/javascript"></script> -->
<script src="js/muse.js" type="text/javascript"></script>
<!-- <script src="js/proto_funcs.js" type="text/javascript"></script>  -->

<% boolean noheader = request.getParameter("noheader") != null || ModeConfig.isPublicMode();
if (!noheader) { %>
	<jsp:include page="header.jsp"/>
<br/>
<% } %>

<!--  important: include jog_plugin AFTER header.jsp, otherwise the extension is applied to a jquery ($) object that is overwritten when header.jsp is included! -->
<script src="js/jog_plugin.js" type="text/javascript"></script>

<div class="browsepage">

<%  boolean acrobatics = "acrobatics".equals(JSPHelper.getSessionAttribute(session, "mode"));
int acrobaticsVal = (acrobatics) ? 1: 0; %>
<script type="text/javascript">var acrobatics = <%=acrobaticsVal%>;</script>

<%
// archive is null if we haven't been able to read an existing dataset
Archive archive = JSPHelper.getArchive(session);
if (archive == null)
{
//	session.setAttribute("loginErrorMessage", "Your session has timed out -- please click login again.");
%>
<!--    <script type="text/javascript">window.location="index.jsp";</script>  --> 
<% if (ModeConfig.isPublicMode()) { %>
<br/> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;   The session has timed out. Please <a href="#" onclick="redirect_to('<%=loginUrl%>')">choose the archive</a> again.
<% } else { %>
<br/> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;   The session has timed out. Please <a href="#" onclick="redirect_to('<%=loginUrl%>')">login</a> again.
<% } %>
<script type="text/javascript">
function redirect_to(url) { if (top === self) { window.location = url; } else { window.open(url); } }
</script>

<%
	JSPHelper.log.warn ("Error: session has timed out, allDocs = null");
 	return;
 }


 // now init session vars
 Collection<Document> allDocs = (Collection<Document>) JSPHelper.getSessionAttribute(session, "emailDocs");
 if (Util.nullOrEmpty(allDocs)) {
	allDocs = archive.getAllDocs();
	session.setAttribute("emailDocs", allDocs);
 }

 AddressBook addressBook = archive.addressBook;
 GroupAssigner groupAssigner = archive.groupAssigner;
 String groupName = null;
 if (groupIdx >= 0)
 {
 	List<SimilarGroup<String>> groups = groupAssigner.getSelectedGroups();
 	if (groupIdx < groups.size())
 		groupName = groups.get(groupIdx).name;
 	if (groupName == null)
 		groupName = "";
 }
 if (groupName == null)
 	groupName = "";

 // nofilter is used in some cases like browse with a specific docids where we don't want to apply to docs in current filter, but to all docs in archive
 boolean onlyFilteredDocs = request.getParameter("nofilter") == null;
 
 Pair<Collection<Document>,Collection<Blob>> search_result = JSPHelper.selectDocsWithHighlightAttachments(request, session, onlyFilteredDocs, false);
 List<Document> docs = new ArrayList<Document>(search_result.first);
 Collection<Blob> highlightAttachments = search_result.second;
 Lexicon lexicon = (Lexicon) JSPHelper.getSessionAttribute(session, "lexicon");
 if (lexicon == null)
 {
	lexicon = archive.getLexicon("default");
	session.setAttribute("lexicon", lexicon);	
 }
 
 Map<String, Collection<DetailedFacetItem>> facets = IndexUtils.computeDetailedFacets(docs, archive);
 boolean jogDisabled = ("true".equals(JSPHelper.getSessionAttribute(session, "jogDisabled"))) ? true : false;

 // now docs is the selected docs

 String jog_contents_class = "";
 if (!acrobatics)
 	jog_contents_class = "message";
%>

<% int left_padding = noFacets ? 10 : 160; %>
<div style="padding-left:<%=left_padding%>px">
<div class="db-hint">

<%
	String x = "";
	if (!Util.nullOrEmpty(sentimentSummary))
		x += "Sentiment: <b>" + Util.escapeHTML(sentimentSummary) + "</b>&nbsp;";
	if (!Util.nullOrEmpty(groupName))
		x += "Group: <b>" + Util.escapeHTML(groupName)  + "</b>&nbsp;";
	String termString = "";
	if (!Util.nullOrEmpty(term))
	{
		termString = "Search: <b>" + Util.escapeHTML(term) + "</b><br/>&nbsp;";
		x += termString;
	}
	if (persons != null && persons.length > 0)
	{
		x += "<b>" + Util.escapeHTML(persons[0]);
		if (persons.length > 1)
			x += "+" + (persons.length-1);
		x += "</b>";
	}

	// little key at the top to what we are currently displaying. Not needed if we have facets... perhaps? 
	if (!Util.nullOrEmpty(x) && noFacets)
		out.println (x + "<br>");

	String origQueryString = request.getQueryString();
	if (origQueryString == null)
		origQueryString = "";
%>

<% if (docs.size() < 2) { %>
	Use <span class="helpcue">&larr;</span> and <span class="helpcue">&rarr;</span> keys to move between messages.
	(Shift) <span class="helpcue">TAB</span> for (previous) next month. 
<% } else if (jogDisabled) { %>
	Jog disabled. Use <span class="helpcue">&larr;</span> and <span class="helpcue">&rarr;</span> keys to move between messages.
	(Shift) <span class="helpcue">TAB</span> for (previous) next month. 
<% } else {	%>
	<span class="helpcue">CLICK</span> to summon/dismiss the jog dial. Rotate it to move between <%=docs.size()%> messages.
	Or use <span class="helpcue">&larr;</span> and <span class="helpcue">&rarr;</span> keys.
	(Shift) <span class="helpcue">TAB</span> for (previous) next month. 
<% } %>
	</div>
</div> <!-- padding-left -->

<%
if (!noFacets)
{
%>

<div style="float:left;width:140px;padding-left:5px">
<div class="facets" style="width:10em;text-align:left;margin-bottom:0px;">
<%
	if (!Util.nullOrEmpty(term)) { 
		out.println("<b>search</b><br/>\n");		
		String displayTerm = Util.ellipsize(term, 15);

		out.println("<span title=\"" + Util.escapeHTML(displayTerm) + "\" class=\"facet nojog selected-facet rounded\" style=\"padding-left:2px;padding-right:2px\">" + Util.escapeHTML(displayTerm));
		out.println (" <span class=\"facet-count\">(" + docs.size() + ")</span>");
		out.println ("</span><br/>\n");
		out.println("<hr/>\n");
	}
	for (String facet: facets.keySet())
	{
		List<DetailedFacetItem> items = new ArrayList<DetailedFacetItem>(facets.get(facet));
		if (items.size() == 0)
			continue; // don't show facet if it has no items.

		// the facet items could still all have 0 count, in which case, skip this facet
		boolean nonzero = false;
		for (DetailedFacetItem f: items)
			if (f.totalCount() > 0)
			{
				nonzero = true;
				continue;
			}
		if (!nonzero)
			continue;


		out.println("<b>" + Util.escapeHTML(facet) + "</b><br/>\n");
		Collections.sort(items);

		// generate html for each facet. selected and unselected facets separately
		List<String> htmlForSelectedFacets = new ArrayList<String>();
		List<String> htmlForUnSelectedFacets = new ArrayList<String>();
		
		// random idea: BUI (Blinds user interface. provide blinds-like controls (pull a chain down/to-the-side to reveal information))
		for (DetailedFacetItem f: items)
		{
			if (f.totalCount() == 0)
				continue;
	    	// find the facet url in the facet params
			int facetParamIdx = Util.indexOfUrlParam(origQueryString, f.messagesURL); // TODO: do we need to worry about origQueryString.replaceAll("%20", " ")?
			boolean facetAlreadySelected = facetParamIdx >= 0;
			if (Util.nullOrEmpty(f.name))
			{
				JSPHelper.log.info ("Warning; empty title!"); /* happened once */
				continue;
			}
			String name = Util.ellipsize(f.name, 15);
			String url = request.getRequestURI();
			// f.url is the part that is to be added to the current url
			if (!Util.nullOrEmpty(origQueryString))
			{
			    if (!facetAlreadySelected)
			    	url += "?" + origQueryString + "&" + f.messagesURL;
			    else
			    	// facet filter already selected, so unselect it
					url += "?" + Util.excludeUrlParam(origQueryString, f.messagesURL);
			}
			else
			{
				// no existing params ... not sure if this can happen (might some day if we want to browse all messages in session)
				url += '?' + f.messagesURL;
			}

			String c = facetAlreadySelected ? " selected-facet rounded" : "";
			
			String html = "<span class=\"facet nojog" + c + "\" style=\"padding-left:2px;padding-right:2px\" onclick=\"javascript:self.location.href='" + url + "';\" title=\"" + Util.escapeHTML(f.description) + "\">" + Util.escapeHTML(name)
						+ " <span class=\"facet-count\">(" + f.totalCount() + ")</span>"
						+ "</span><br/>\n";
			if (facetAlreadySelected)
				htmlForSelectedFacets.add(html);
			else
				htmlForUnSelectedFacets.add(html);
		}
		
		// prioritize selected over unselected facets
		List<String> htmlForAllFacets = new ArrayList<String>();
		htmlForAllFacets.addAll(htmlForSelectedFacets);
		htmlForAllFacets.addAll(htmlForUnSelectedFacets);

		int N_INITIAL_FACETS = 5;
		// toString the first 5 items, rest hidden under a More link
		int count = 0;
		for (String html: htmlForAllFacets)
		{
			out.println (html);
			if (++count == N_INITIAL_FACETS)
				out.println("<div style=\"display:none;margin:0px;\">\n");
		}
		
		if (count >= N_INITIAL_FACETS)
		{
			out.println("</div>");
			out.println("<div class=\"clickableLink\" style=\"text-align:right;padding-right:10px;font-size:80%\" onclick=\"muse.reveal(this)\">More</div>\n");
		}
		
		out.println("<hr/>\n");
	} // String facet
	%>
</div> <!--  .facets -->
<span style="font-size:80%" class="db-hint">Click to toggle filter</span><br/>
<% if (!ModeConfig.isPublicMode()) { %>
<a style="font-size:9pt" class="clickableLink" href="groups" target="_">Refine Groups</a><br/>
<a style="font-size:9pt" class="clickableLink" href="editLexicon" target="_">Edit Lexicon</a><br/>
<% } %>
<a style="font-size:9pt" class="clickableLink" href="help.jsp#facets-panel" target="_">More Help</a>

</div>

<%
} // if (!noFacets)

String datasetName = String.format("docset-%08x", EmailUtils.rng.nextInt());// "dataset-1";
int nAttachments = EmailUtils.countAttachmentsInDocs((Collection) docs);

%>

<div style="float:left;padding-left:10px">
	<div class="browse_message_area rounded shadow;position:relative" style="width:1020px">
	<!--  to fix: these image margins and paddings are held together with ducttape cos the orig. images are not a consistent size -->
	<span id="jog_status1" class="showMessageFilter rounded" style="float:left;opacity:0.5;margin-left:30px;margin-top:10px;">&nbsp;0/0&nbsp;</span>
	<div id="pageheader" style="float:right;right:12px;position:relative;margin-top:10px">
		<% if (!ModeConfig.isPublicMode()) { %>

			<% if (nAttachments > 0) { %>
		<!-- disabling because attachments can be reached through filters
		<img class="browse-menu" title="View <%=nAttachments%> attachments in these <%=docs.size()%> messages" style="height:30px;padding:1px 2px" src="images/PaperClip4_Black.png" onclick="window.open('attachments?<%=origQueryString%>');"/>  -->	
			<% } %>
			
			<img rel="#exportMenu" id="exportMessages" class="browse-menu" title="Export these messages to a file" style="height:28px;padding: 2px; margin: 0px 5px 0px 7px" src="images/save.png"/>
		<!-- <img class="browse-menu" title="Save messages" style="height:30px;padding-left:7px" src="images/save.png"/> -->	
		<img class="browse-menu" title="Write a comment" style="height:30px;margin-top:3px;padding-top:2px" src="images/edit-icon_06.png" onclick="$('#comment_div').show();$('.comment')[0].focus();"/>
		<img id="heart" class="browse-menu" title="Like this message" style="height:30px" src="images/heart-icon.png" onclick="heart_click()"/>
		<br/>
		<div id="comment_div" style="z-index:1000;display:none;position:relative">
			<a id="applyToAll" style="font-size:small;position:absolute;right:0px" href="#" onclick="applyCommentToAllPages();">Apply to all</a>
			<textarea class="rounded comment" ></textarea>
		</div>
		<% } %>
	</div>
	<div align="center" style="font-size:12pt;opacity:0.5;margin-left:30px;margin-right:30px;margin-top:10px;"><span id="jog_docId" title="Unique ID for this message"></span></div>
	<div style="clear:both"></div>
	<div id="jog_contents" style="position:relative" class="<%=jog_contents_class%>">
		<div style="text-align:center"><img src="images/spinner.gif"/><h2>Loading <%=Util.commatize(docs.size())%> messages...</h2></div>
	</div>
	</div>	
</div>

<div id="exportMenu" class="modal nojog">
	<form id="exportForm" method="post" action="saveMessages">
	<span style="font-size:125%">Save Messages<br/><br/></span>
	<table>
		<tr title="A name for this collection"><td>File name </td><td><input type="text" name="name"/><br/></td></tr>
		<tr><td>Only <img src="images/heart-icon-red.png" width="20px" style="position:relative;top:5px"> messages </td><td><input type="checkbox" class="nojog" id="save_only_likes"/><br/></td></tr>
		<tr><td>Messages tagged with</td><td><input class="nojog" type="text" name="tag"><br/></td></tr>
		<tr><td>Messages not tagged with </td><td><input class="nojog" type="text" name="nottag"><br/></td></tr>
		<tr title="Check this option to reduce file size by removing attachments"><td>Strip attachments  </td><td><input class="nojog" type="checkbox" name="noattach"><br/></td></tr>
		<tr title="Check this option to remove quoted parts of messages"><td>Strip quoted text </td><td><input class="nojog" type="checkbox" name="stripQuoted"><br/></td></tr>
		<tr><td></td><td><br/><input onclick="javascript:doSaveMessages()" type ="button" value="OK"/></td></tr>
	</table>
	
	<input id="selectedMessageNums" type="hidden" name="num" value=""/>
	<input type="hidden" name="docset" value="<%=datasetName%>"/>
	<input id="noheader" type="hidden" name="noheader" value="true"/>
	
	</form>	
</div>

<% 	out.flush();

	// has indexer indexed these docs ? if so, we can use the clusters in the indexer.
	// but it may not have, in which case, we just split up the docs into monthly intervals.
	Set<String> selectedPrefixes = lexicon == null ? null : lexicon.wordsForSentiments(archive.indexer, docs, request.getParameterValues("sentiment"));
	if (selectedPrefixes == null)
		selectedPrefixes = new LinkedHashSet<String>();
	String[] searchTerms = request.getParameterValues("term");
	// warning: remember to convert, otherwise will not work for i18n queries!
	searchTerms = JSPHelper.convertRequestParamsToUTF8(request.getParameterValues("term"));
	Set<String> highlightTermsUnstemmed = new LinkedHashSet<String>(); 
	if (searchTerms != null && searchTerms.length > 0)
		for (String s: searchTerms) {
			selectedPrefixes.addAll(IndexUtils.getAllWordsInQuery(s));
			// note: we add the term to unstemmed terms as well -- no harm. this is being introduced to fix a query param we had like term=K&L Gates and this term wasn't being highlighted on the page earlier, because it didn't match modulo stemming 
			// if the query param has quotes, strip 'em
			if (s.startsWith("\"") && s.endsWith("\""))
				s = s.substring(1, s.length()-1);
			highlightTermsUnstemmed.addAll(IndexUtils.getAllWordsInQuery(s));
		}
	
	// now if filter is in effect, we highlight the filter word too
	NewFilter filter = (NewFilter) JSPHelper.getSessionAttribute(session, "currentFilter");
	if (filter != null && filter.isRegexSearch()) {
		highlightTermsUnstemmed.add(filter.get("term"));
	}

    String hci = request.getParameter("contactid");
    Set<Integer> highlightContactIds = new LinkedHashSet<Integer>();
    if(hci!=null) {
        try {
            int val = Integer.parseInt(hci);
            highlightContactIds.add(val);
        }catch(Exception e){
            JSPHelper.log.info("Highlight contact id param is not integer"+hci);
        }
    }
    Set<String> highlightTerms = new HashSet<>();
    if(highlightTermsUnstemmed!=null)
        highlightTerms.addAll(highlightTermsUnstemmed);
    if(selectedPrefixes!=null)
        highlightTerms.addAll(selectedPrefixes);
    Pair<DataSet, String> pair;
    String sortBy = request.getParameter("sort_by");
    if ("recent".equals(sortBy) || "relevance".equals(sortBy))
        pair = EmailRenderer.pagesForDocuments(docs, archive, datasetName, highlightContactIds, highlightTerms, highlightAttachments, MultiDoc.ClusteringType.NONE);
    else
        pair = EmailRenderer.pagesForDocuments(docs, archive, datasetName, highlightContactIds, highlightTerms, highlightAttachments);

    DataSet browseSet = pair.getFirst();
	String html = pair.getSecond();

	// entryPct says how far (what percentage) into the selected pages we want to enter
	int entryPage = IndexUtils.getDocIdxWithClosestDate((Collection) docs, HTMLUtils.getIntParam(request, "startMonth", -1), HTMLUtils.getIntParam(request, "startYear", -1));
	if (entryPage < 0) {
		// if initdocid is set, look for a doc with that id to set the entry page
		String docId = request.getParameter("initDocId");
		int idx = 0;
		for (Document d: docs)
		{
			if (d.getUniqueId().equals(docId))
				break;
			idx++;		
		}
		if (idx < docs.size()) // means it was found
			entryPage = idx;
		else
			entryPage = 0;
	}
	out.println ("<script type=\"text/javascript\">var entryPage = " + entryPage + ";</script>\n");

	session.setAttribute (datasetName, browseSet);
	session.setAttribute ("docs-" + datasetName, new ArrayList<Document>(docs));
	out.println (html);
	JSPHelper.log.info ("Browsing " + browseSet.size() + " pages in dataset " + datasetName);
	
%>
</div> <!--  browsepage -->
<br/>
<script type="text/javascript">

var comments = new Array();
var post_comments_url = 'ajax/postCommentsAndLikes.jsp?docset=docs-<%=datasetName%>';  // make sure this url has at least one param
var liked = new Array();

function applyCommentToAllPages(event)
{
	var comment = $('.comment').val();
	var len = $('.page').length;
	for (var i = 0; i < len; i++)
		comments[i] = comment;
	$.get(post_comments_url + '&page=all' + '&comment=' + escape(comment)); // & and not ? because we always expect postCommentURL will have a ? param
	$('#applyToAll').fadeOut();
	return false;
}

/* moving off the given page. clean up/persist if needed. */
function postCommentForPage(num)
{
	var comment = $('.comment').val();
	if (comment != null && comment.length > 0) {
		muse.log ('posting comment for page ' + num + ' at url ' + post_comments_url + ': ' + comment);
		$.get(post_comments_url + '&page=' + num + '&comment=' + escape(comment)); // & and not ? because we always expect postCommentURL will have a ? param
		// if needed, can check for successful get....
		comments[num] = comment;
	}
}

var heart_click = function() {
	muse.log ('like clicked for page ' + PAGE_ON_SCREEN + ' ' + liked[PAGE_ON_SCREEN]);
	if (liked[PAGE_ON_SCREEN])
		liked[PAGE_ON_SCREEN] = false;
	else
		liked[PAGE_ON_SCREEN] = true;
//	$('#heart').css('background-color', (liked[PAGE_ON_SCREEN] ? 'red' : '#f8f8ff'));
	$('#heart').attr('src', (liked[PAGE_ON_SCREEN] ? 'images/heart-icon-red.png' : 'images/heart-icon.png'));
	$('#heart').attr('title', (liked[PAGE_ON_SCREEN] ? 'Unlike this message' : 'Like this message'));

	muse.log ('like updated for page ' + PAGE_ON_SCREEN + ' ' + liked[PAGE_ON_SCREEN]);
};

var PAGE_ON_SCREEN = -1, TOTAL_PAGES = 0;
var SAVE_DOCSET = false; // controls whether the docset is released when page is unloaded.

// currently called before the new page has been rendered
var callback = function(oldPage, currentPage) {
	postCommentForPage(oldPage);

	if (comments[currentPage] != null && comments[currentPage].length > 0)
	{
		$comment = $('.comment');
		$comment.val(comments[currentPage]);
		$('#comment_div').show(); // make sure comment box is visible if it does have comments
		$('#applyToAll').show(); // reshow this, because if apply to all was used previously, it has been hidden
		// $comment[0].setSelectionRange($comment.val().length, $comment.val().length); // set cursor to end of field - TODO check, doesn't seem to work
	}
	else
	{
		$('#comment_div').hide();
		$('.comment').val('');
	}
	$('#heart').attr('src', (liked[currentPage] ? 'images/heart-icon-red.png' : 'images/heart-icon.png'));
	$('#heart').attr('title', (liked[currentPage] ? 'Unlike this message' : 'Like this message'));
	PAGE_ON_SCREEN = currentPage;
};

//called after the new page has been rendered
var load_callback = function(currentPage) {
	if (parent.jog_onload)
		parent.jog_onload(document);
};

function comment_keystroke_handler(code)
{
	// check if its special keys like escape, ctrl, command etc.
	// based on http://unixpapa.com/js/key.html
	if (code == 16 || code == 17 || code == 18 || code == 224 || code == 27 || code == 91 || code == 92 || code == 93 || code == 219 || code == 220)
		return false; 
	else
		return true;
	LOG ('in keystroke code' + code);
	var comment_jq = $('.comment')[0];
	comment_jq.focus();
	comment_jq.setSelectionRange($(comment_jq).val().length, $(comment_jq).val().length); // set cursor to end of field - TODO check, doesn't seem to work
}

$('.comment').click (function() { muse.log ('text area click'); return false;});
//$(document).keydown(comment_keystroke_handler);
$('.comment').keydown(function(e) { e.jog_ignore = true; muse.log ('text area key'); return true;});

$('body').ready(function() { 
			$(document).jog({
				paging_info: {url: 'ajax/jogPageIn.jsp?datasetId=<%=datasetName%>', window_size_back: 30, window_size_fwd: 50}, 
				page_change_callback: callback,
				page_load_callback: load_callback,
				logger: muse.log,
				width: 180,
				disabled: <%= (jogDisabled || docs.size() < 2) ?"true":"false"%>,
				reel1: 'images/movieReel2.png', // 'images/movieReel2.png'
				reel2: 'images/movieReel1a.png', // 'images/movieReel2.png',
				dynamic: false
			});
	
	// set up initial comments if any...
	$pages = $('.page');
	TOTAL_PAGES = $pages.length;
	for (var i = 0; i < TOTAL_PAGES; i++)
	{
		comments[i] = $pages[i] . getAttribute('comment'); // pages[i] is not HttpSession
		liked[i] = ($pages[i] . getAttribute('liked') != null); // pages[i] is not HttpSession
	}
});

// on page unload, release dataset to free memory 
$(window).unload(function() {
	muse.log ('releasing dataset <%=datasetName%>');
	$.get('ajax/releaseDataset.jsp?datasetId=<%=datasetName%>');
});

$('#exportMessages').overlay({// some mask tweaks suitable for modal dialogs
		mask: {
			color: '#ebecff',
			loadSpeed: 200,
			opacity: 0.9
		},
		closeOnClick: false
	});

function count_likes()
{
	var count = 0;
	for (var i = 0; i < TOTAL_PAGES; i++)
		if (liked[i])
			count++;
	return count;
}

function doSaveMessages() {
	var nums = '', nliked = 0;
	
	// first save the current page's comment as it may not have been saved
	postCommentForPage(PAGE_ON_SCREEN);	
	
	// likes are only on the client side.
	if ($('#save_only_likes:checked').length > 0)
	{
		muse.log ('identifying liked pages');
		for (var i = 0; i < TOTAL_PAGES; i++)
			if (liked[i])
			{
				nliked++;
				nums += ('' + i + ' ');
			}
	}
	muse.log (nliked + ' liked pages');

	$('#selectedMessageNums').val(nums);
	if (window.top === window.self)
		$('#noheader').val(''); // if in top-level frame, then we need header (so set noheader to the empty string), otherwise leae
	else
		$('#noheader').val('true'); 
	SAVE_DOCSET = true;	 // set to true because we want the next screen (saveMessages) to have access to this docset.
	$('#exportForm').submit();
}

</script>
<br/>
</body>
</html>
