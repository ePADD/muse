<%@page contentType="text/html; charset=UTF-8"%>
<%
	JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8");
%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>
<%!

public String scriptForFacetsGraph(List<DetailedFacetItem> dfis, List<Date> intervals, Collection<EmailDocument> docs, int[] allMessagesHistogram, int w, int h)
{
	Collections.sort(dfis);
	JSONArray j = new JSONArray();

	int count = 0, MAX_COUNT = 20; // we can only show top 20 layers
	for (DetailedFacetItem dfi: dfis)
	{
		String folder = dfi.name;
		int[] hist = CalendarUtil.computeHistogram(EmailUtils.datesForDocs((Collection) dfi.docs), intervals);
		try {
			JSONArray groupVolume = JSONUtils.arrayToJsonArray(hist);
			JSONObject o = new JSONObject();
			o.put("caption", dfi.name);
			o.put("full_caption", dfi.description);
			o.put("url", dfi.messagesURL);
			o.put("histogram", groupVolume);
			j.put(count, o);
		} catch (Exception e) { Util.print_exception (e, JSPHelper.log); }
		count++;
		if (count >= MAX_COUNT) 
			break;
	}
	String json = j.toString();
	
	String totalMessageVolume = JSONUtils.arrayToJson(allMessagesHistogram);
	Calendar c = new GregorianCalendar(); c.setTime(intervals.get(0)); 
	int start_mm = c.get(Calendar.MONTH), start_yy = c.get(Calendar.YEAR);
	
	return "<script type=\"text/javascript\">\n"
	+ "var jsonForData = " + json + ";\n"
	//+ "draw_stacked_graph(jsonForData, " + totalMessageVolume + ", " + 0 + ", " + w + ", " + h + ", " + start_yy + ", " + start_mm + ", pv.Colors.category20(), get_click_handler_people_or_folders(jsonForData), graphType);\n"
	+ "draw_stacked_graph_d3('#div_graph', '#div_legend', jsonForData, " + totalMessageVolume + ", " + w + ", " + h + ", " + start_yy + ", " + start_mm + ", d3.scale.category20b(), get_click_handler_people_or_folders(jsonForData), graphType);\n"
	+ "</script>\n";
}

public String scriptForGroupsGraph(List<Date> intervals, GroupAssigner groupAssigner, Map<String, Set<EmailDocument>> map, int[] allMessagesHistogram, int w, int h)
{
	String totalMessageVolume = JSONUtils.arrayToJson(allMessagesHistogram);
	StringBuilder json = new StringBuilder("[");
	for (String name: map.keySet())
	{
		Set<DatedDocument> docs = (Set) map.get(name);
		int[] hist = CalendarUtil.computeHistogram(EmailUtils.datesForDocs(docs), intervals);
		String groupVolume = JSONUtils.arrayToJson(hist);
		if (json.length() > 2)
			json.append (",");
		int groupIdx = groupAssigner.getGroupIdx(name);
		json.append ("{caption: \"" + name + "\", url: \"groupIdx=" + groupIdx + "\", histogram: " + groupVolume + "}");
	}
	json.append("]");

	Calendar c = new GregorianCalendar(); c.setTime(intervals.get(0)); int start_mm = c.get(Calendar.MONTH); int start_yy = c.get(Calendar.YEAR);
	return "<script type=\"text/javascript\">\n"
	+ "var jsonForData = " + json + ";\n"
	//+ "draw_stacked_graph(jsonForData, " + totalMessageVolume + ", " + 0 + ", " + w + ", " + h + ", " + start_yy + ", " + start_mm + ", pv.Colors.category20(), get_click_handler_groups(jsonForData), graphType);\n"
	+ "draw_stacked_graph_d3('#div_graph', '#div_legend', jsonForData, " + totalMessageVolume + ", " + w + ", " + h + ", " + start_yy + ", " + start_mm + ", d3.scale.category20b(), get_click_handler_groups(jsonForData), graphType);\n"
	+ "</script>\n";
}
%>
<%!
public String scriptForSentimentsGraph(Map<String, Collection<Document>> map, List<Date> intervals, int[] allMessagesHistogram, int w, int h, int normalizer, HttpSession session)
{
	String totalMessageVolume = JSONUtils.arrayToJson(allMessagesHistogram);

	// normalizer is the max # of documents in a single intervals

	List<Set<DatedDocument>> sentimentDocs = new ArrayList<Set<DatedDocument>>();
	StringBuilder json = new StringBuilder("[");
	for (String caption: map.keySet())
	{
		Collection<DatedDocument> docs = (Collection)map.get(caption);
		int[] hist = CalendarUtil.computeHistogram(EmailUtils.datesForDocs(docs), intervals);
		String sentimentVolume = JSONUtils.arrayToJson(hist);
		if (json.length() > 2)
			json.append (",");
		json.append ("{caption: \"" + caption + "\", url: \"sentiment=" + caption + "\", histogram: " + sentimentVolume + "}");
	}
	session.setAttribute("availableSentiments", map.keySet());
	json.append("]");
	Calendar c = new GregorianCalendar(); c.setTime(intervals.get(0)); int start_mm = c.get(Calendar.MONTH); int start_yy = c.get(Calendar.YEAR);
	return "<script type=\"text/javascript\">\n"
	+ "var jsonForData = " + json + ";\n"
	//+ "draw_stacked_graph(jsonForData, " + totalMessageVolume + ", " + 0 + ", " + w + ", " + h + ", " + start_yy + ", " + start_mm + ", pv.Colors.category19(), get_click_handler_sentiments(jsonForData), graphType);\n"
	+ "draw_stacked_graph_d3('#div_graph', '#div_legend', jsonForData, " + totalMessageVolume + ", " + w + ", " + h + ", " + start_yy + ", " + start_mm + ", d3.scale.category20b(), get_click_handler_sentiments(jsonForData), graphType);\n"
	+ "</script>\n";
}
%>
<%!public String emitScriptForSentimentsByGroupGraph(HttpServletRequest request, Archive archive, Collection<DatedDocument> docsToPlot, int w, int h, HttpSession session)
{
	// normalizer is the max # of documents in a single intervals
	GroupAssigner groupAssigner = archive.groupAssigner;
	AddressBook addressBook = archive.addressBook;
//	groupAssigner.orderIndividualsBeforeMultiPersonGroups();
	List<SimilarGroup<String>> groups =  groupAssigner.getSelectedGroups();
	Lexicon lex = (Lexicon) JSPHelper.getSessionAttribute(session, "lexicon");
	Map<String, Collection<Document>> map = lex.getEmotions(archive.indexer, (Set) docsToPlot, false, request.getParameter("originalContentOnly") != null, null);

//	Map<String, Set<Document>> map = Sentiments.getEmotions(Sentiments.captionToQueryMap, archive.indexer, (Set) docsToPlot);
	List<Set<DatedDocument>> sentimentDocs = new ArrayList<Set<DatedDocument>>();
	StringBuilder json = new StringBuilder("[");
	for (String caption: map.keySet())
	{
		Collection<DatedDocument> docs = (Collection) map.get(caption);
		// mapForSentiment is group -> docs set for this sentiment
		Map<String, Set<EmailDocument>> mapForSentiment = IndexUtils.partitionDocsByGroup((Collection) docs, groups, addressBook, true);
		int[] hist = new int[mapForSentiment.size()];
		int i = 0;
		for (String group: mapForSentiment.keySet())
			hist[i++] = mapForSentiment.get(group).size();

		String sentimentVolumeByGroup = JSONUtils.arrayToJson(hist);
		if (json.length() > 1)
			json.append (",");
		json.append ("{caption: \"" + caption + "\", histogram: " + sentimentVolumeByGroup + "}");
	}
	json.append("]");

	JSONArray gnames = new JSONArray();
	int gcount = 0;
	for (SimilarGroup<String> g: groups)
	{
		try {
			gnames.put(gcount++, g.name);
		} catch (Exception e) { Util.print_exception (e, JSPHelper.log); }
	}
	String groupNamesJson = gnames.toString();

	session.setAttribute("availableSentiments", map.keySet());
	return "<script type=\"text/javascript\">\n"
	+ "var jsonForData = " + json + ";\n"
	+ "draw_stacked_graph(jsonForData, null, 0 , " + w + ", " + h + ", null, null, false, get_click_handler_sentiments_by_group(jsonForData), graphType, " + groupNamesJson + ");\n"
	+ "</script>\n";
}%>
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
		System.err.println ("Error: session has timed out, archive is null.");
		return;
	}

    Collection<DatedDocument> allDocs = (Collection) JSPHelper.selectDocs(request, session, true /* only apply to filtered docs */, false);

	AddressBook addressBook = archive.addressBook;
	Lexicon lex = (Lexicon) JSPHelper.getSessionAttribute(session, "lexicon");
	String name = request.getParameter("lexicon");

	Pair<Date, Date> p = EmailUtils.getFirstLast(allDocs);
	Date globalStart = p.getFirst();
	Date globalEnd = p.getSecond();
	List<Date> intervals = null;
	int nIntervals = 0;
	if (globalStart != null && globalEnd != null) {
		intervals = CalendarUtil.divideIntoMonthlyIntervals(
				globalStart, globalEnd);
		nIntervals = intervals.size() - 1;
	}
	boolean doGroups = false, doSentiments = false, doPeople = false, doFolders = false;
	String view = request.getParameter("view");
	List<SimilarGroup<String>> groups = null;
	GroupAssigner groupAssigner = archive.groupAssigner;
	if (groupAssigner != null)
		groups = groupAssigner.getSelectedGroups();

	if ("sentiments".equals(view)) {
		doSentiments = true;
		JSPHelper.log.info("req lex name = " + name
				+ " session lex name = "
				+ ((lex == null) ? "(lex is null)" : lex.name));
		// resolve lexicon based on name in request and existing lex in session.
		// name overrides lex
		if (!Util.nullOrEmpty(name)) {
			if (lex == null || !lex.name.equals(name)) {
				lex = archive.getLexicon(name);
				session.setAttribute("lexicon", lex);
			}
			// else do nothing, the right lex is already loaded
		} else {
			if (lex == null) {
				// nothing in session, no request param... probably shouldn't happen
				name = "default";
				lex = archive.getLexicon(name);
				session.setAttribute("lexicon", lex);
			} else
				name = lex.name;
		}
	} else if ("groups".equals(view)) {
		doGroups = true;
	} else if ("people".equals(view)) {
		doPeople = true;
	} else if ("folders".equals(view)) {
		doFolders = true;
	}
	%>
<html>
<head>
<title>Browse messages</title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<link rel="stylesheet" href="js/jquery-lightbox/css/jquery.lightbox-0.5.css" type="text/css" media="screen" />
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script src="js/jquery/jquery.tools.min.js" type="text/javascript"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/jquery-lightbox/js/jquery.lightbox-0.5.min.js"></script>
<script type="text/javascript" src="js/jquery-lightbox/js/jquery.lightbox-0.5.pack.js"></script>
<script type="text/javascript" src="js/protovis.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/diary.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>
<script type="text/javascript" src="js/renderPerson.js"></script>
<script type="text/javascript" src="js/json2.js"></script>
<script type="text/javascript" src="js/dnd.js"></script>
<script type="text/javascript" src="js/proto_funcs.js"></script>
<script type="text/javascript" src="js/d3.v2.min.js"></script>
<script type="text/javascript" src="js/d3_funcs.js"></script>

<link href="css/d3_funcs.css" rel="stylesheet" type="text/css"/>
<style>
.brush .extent {
  stroke: black;
  stroke-width: 2;
  fill-opacity: .5;
}
</style>

</head>
<body style="margin:1% 2%"> <!--  override the default of 1% 5% because we need more width on this page -->

<%
	String sentiment = request.getParameter("sentiment");
	if (sentiment == null)
		sentiment = "";
	String groupIdx = request.getParameter("groupIdx");
	if (groupIdx == null)
		groupIdx = "";
	String graphType = request.getParameter("graphType");
	if (graphType == null)
		graphType = "curvy";
	if (allDocs.size() < 500)
		graphType = "boxy"; // force boxy if < 100, because curvy doesn't look good
%>

<script type="text/javascript">

var stack_layout_offset = 'zero';
var stacked_graph = null;
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

function set_view_type(event)
{
	if (event.target.value === 'links')
		window.open("links", "_blank");
	else if (event.target.value === 'map')
		window.open("map.jsp");
	else  if (event.target.value === 'sentimentsByGroup')
		window.location = 'stackedGraph?view=sentimentsByGroup=1&graphType=boxy'; // '=&graphType=' + $('#graphType').val();
	else if (event.target.value === 'exportMessages')
		window.open('saveMessages.jsp');
	else
		window.location = 'stackedGraph?view=' + $('#viewType').val(); // + '&graphType=<%=graphType%>';
}

var sentiment = '<%=sentiment%>';
var groupIdx = '<%=groupIdx%>';
var graphType = '<%=graphType%>';

function set_sentiment_type(event) { window.location = 'stackedGraph?view=groups&sentiment=' + event.target.value;}
function set_group(event) { window.location = 'stackedGraph?view=sentiments&groupIdx=' + event.target.value; }

var get_click_handler_groups = function(jsonForData) {
	return function(d, l, startYear, startMonth) {
		$('#prompt_click').hide();
		$('#browse_frame').css('min-height', '510px');
		var url = 'browse?noheader=true&';
		// if sentiment filter is in effect, make sure that it is applied to the message view
		if (sentiment && sentiment.length > 0) 
			url += 'sentiment=' + sentiment + '&';
		url += jsonForData[l].url + '&startMonth=' + startMonth + '&startYear=' + startYear;
		frames['browse_frame'].window.location = url;
	};
};

var get_click_handler_people_or_folders = function(jsonForData) {
	return function(d, l, startYear, startMonth) {
		$('#prompt_click').hide();
		$('#browse_frame').css('min-height', '510px');
		var url = 'browse?noheader=true&';
		url += jsonForData[l].url + '&startMonth=' + startMonth + '&startYear=' + startYear;
		frames['browse_frame'].window.location = url;
	};
};

var get_click_handler_sentiments = function(jsonForData) {
	var my_labels = jsonForData.map(function(d) { return d.caption; });
	return function(d, l, startYear, startMonth) {
		$('#prompt_click').hide();
		$('#browse_frame').css('min-height', '510px');
		var url = 'browse?noheader=true&';
		// if group filter is in effect, make sure that it is applied to the message view
		if (groupIdx != null && groupIdx.length > 0)
			url += 'groupIdx=' + groupIdx + '&';
		url += 'sentiment=' + my_labels[l] + '&startMonth=' + startMonth + '&startYear=' + startYear;
		frames['browse_frame'].window.location = url;
	};
};

var get_click_handler_sentiments_by_group = function(jsonForData) {
	var my_labels = jsonForData.map(function(d) { return d.caption; });
	return function(xcell, ycell) {
		$('#prompt_click').hide();
		$('#browse_frame').css('min-height', '510px');
		var url = 'browse?noheader=true&sentiment=' + my_labels[ycell] + "&groupIdx=" + xcell;
		frames['browse_frame'].window.location = url;
	};
};

// callback from the embedded browse_frame
function jog_onload(doc) {
	var f_id = 'browse_frame';
	var f = frames[f_id]; // f.document is same as "doc"
	document.getElementById(f_id).height = f.document.body.parentElement.offsetHeight + 15;// scrollHeight doesn't seem to work on Firefox. +15 to avoid spurious vscroll;
	//f.document.getElementsByClassName('browsepage')[0].style.width = "1040px"; // override the css width to prevent hscoll bar. can't do it with <style> in this page.
}

</script>

<p>

<div align="center">	
<jsp:include page="header.jsp"/>
<div style="font-size:14px">
<br/>
<hr style="margin-top:-6px">
<select id="viewType" onchange='set_view_type(event);'>
	<option <%="sentiments".equals(view) ? "selected":""%> value="sentiments">Sentiments over time</option>
	<%
		if (!ModeConfig.isPublicMode()) {
	%>
	<option <%="groups".equals(view) ? "selected":""%> value="groups">Groups over time</option>
	<%
		}
	%>
	<option <%="people".equals(view) ? "selected":""%> value="people">Top people over time</option>
	<%
		if (!ModeConfig.isPublicMode()) {
	%>
	<option <%="folders".equals(view) ? "selected":""%> value="folders">Top folders over time</option>
	<%
		}
	%>
<!-- <option value="None1">----Un -->	der Test----</option> 
<!-- <option value="map">Map</option>  -->	
<!-- <option <%=("sentimentsByGroup").equals(view) ? "selected=\"selected\"":""%> value="sentimentsByGroup">Sentiments by group</option>  -->	
<!-- <option value="links">Links</option>	 -->	
<!-- <option value="exportMessages">Export messages</option>  -->	
	
	<!-- 
	<option value="keyUnigrams">unigrams</option>
	<option value="noGroups">no groups</option>
	 -->
</select>
&nbsp;&nbsp;

<%
	Collection<String> availableSentiments = (Collection) JSPHelper.getSessionAttribute(session, "availableSentiments");
String selectedSentiment = request.getParameter("sentiment");
if (doGroups && !Util.nullOrEmpty(availableSentiments)) // fix sentiment, see groups over time
{
%>
	<select onchange="set_sentiment_type(event);">
	<%
		if (request.getParameter("sentiment") == null)  {
	%>
	<option selected value="all">All sentiments</option>
	<%
		} else {
	%>
		<option value="all">All sentiments</option>
	<%
		}
		for (String s: availableSentiments)
		{
			String selected = s.equals(selectedSentiment) ? "selected": "";
	%> <option <%=selected%> value="<%=s%>"><%=s%></option> <%
 	}
 %>
	</select>
<%
	}
else if (doSentiments  && !Util.nullOrEmpty(groups)) // see sentiments over time for a fixed group
{
%>
	<select onchange="set_group(event);">

	<%
		if (request.getParameter("sentiment") == null)  {
	%>
			<option selected value="all">All groups</option>
	<%
		} else {
	%>
			<option value="all">All groups</option>
	<%
		}
			for (int i = 0; i < groups.size(); i++)
			{
		SimilarGroup<String> group = groups.get(i);
		String gname = Util.nullOrEmpty(group.name) ? ("group" + i) : group.name;
		String selected = (i == HTMLUtils.getIntParam(request, "groupIdx", -1)) ? "selected": "";
	%> <option <%=selected%> value="<%=i%>"><%=gname%></option> <%
 	}
 %>
	</select>
<%
	}
%>
&nbsp;&nbsp;
<!--
<select id="graphType" onchange="set_view_type(event)">
	<option <%=graphType.equals("boxy")?"":"selected"%> value="curvy">Curvy</option>
	<option <%=graphType.equals("boxy")?"selected":""%> value="boxy">Boxy</option>
</select>
&nbsp;&nbsp;
  -->

<!--
<select onchange="update_stacked_graph(event);">
<option value="zero">Percentages</option>
	<option selected value="counts">Counts</option>
	<option value="silohouette">Silhouette</option>
</select>
-->

<%
	if (doGroups) {
%>
	<a class="clickableLink" href="groups">Refine groups</a>&nbsp;&bull;
	<a class="clickableLink" href="help.jsp#groups">What's this Graph?</a>
<%
	} else if (doSentiments) {
%>
		<%
			Collection<String> lexiconNames = archive.getAvailableLexicons();

				boolean onlyOneLexicon = (lexiconNames.size() == 1);
				if (lexiconNames.size() > 1)
				{
		%>
			<script>function changeLexicon() {	window.location = 'stackedGraph?view=sentiments&lexicon=' +	$('#lexiconName').val(); }</script>
			Lexicon <select id="lexiconName" onchange="changeLexicon()">
			<%
				// common case, only one lexicon, don't show load lexicon
				for (String n: lexiconNames)
				{
			%> <option <%=name.equalsIgnoreCase(n) ? "selected":""%> value="<%=n.toLowerCase()%>"><%=Util.capitalizeFirstLetter(n)%></option>
			<%
				}
			%>
			</select>
			(<a class="clickableLink" style="margin:0;padding:0;" href="editLexicon">Edit</a>)
		<%
				} else {
			%>
					<a class="clickableLink" style="margin:0;padding:0;" href="editLexicon">Edit Lexicon</a>
		<%
			}
		%>
		&nbsp;&bull;<a class="clickableLink" href="help#sentiments">What's this Graph?</a>
	<%
		}
	%>

	&nbsp;&bull;&nbsp;<a class="clickableLink" href="charts?view=<%=view%>">Individual charts</a>
	&nbsp;&bull;&nbsp;<a class="clickableLink" href="info">Back to Summary</a>

<!-- &nbsp;&bull; <a class="clickableLink" href="charts?maxCount=400&view=<%=view%>">Individual charts</a> -->	

</div>
</div>

<!-- make div "stackedgraph-container" fixed aspect ratio - see http://stackoverflow.com/questions/9219005/proportionally-scale-a-div-with-css-based-on-max-width-similar-to-img-scaling -->
<div class="stackedgraph-container" align="center" style="position:relative;min-width:640px;width:95%;padding-top:32%;padding-bottom:5%;height:0;margin:0 auto">
<div style="position:absolute;top:0;bottom:0;width:100%">

<div id="div_graph" style="width:80%;float:left"></div>
<div id="div_legend" style="width:19%;float:left;margin-left:1%;overflow:auto"></div>
<div style="clear:both"></div>

<%
	String graph_script = "";
	boolean trackNOTA = request.getParameter("nota") != null;

	boolean graph_is_empty = false;
	String empty_graph_message = null;
	
	if (doSentiments)
	{
		int normalizer = ProtovisUtil.normalizingMax(allDocs, addressBook, intervals);
		int[] allMessagesHistogram = CalendarUtil.computeHistogram(EmailUtils.datesForDocs(allDocs), intervals);
		graph_is_empty = true;
		if (!Util.nullOrEmpty(allDocs))
		{
			Map<String, Collection<Document>> map = lex.getEmotions(archive.indexer, (Collection) allDocs, trackNOTA, request.getParameter("originalContentOnly") != null); // too heavyweight -- we just want to find if the damn graph is empty...
			for (String key: map.keySet())
			{
				Collection<Document> set = map.get(key);
				if (set != null && set.size() > 0)
				{
					graph_is_empty = false;
					break;
				}
			}
			graph_script = scriptForSentimentsGraph(map, intervals, allMessagesHistogram, 1000, 450, normalizer, session);
		}
	
		if (graph_is_empty)
	empty_graph_message = "No sentiment hits in these " + allDocs.size() + " messages";
	}
	else if (doGroups) /* default: if (request.getParameter("groups") != null) */
	{
		doGroups = true;
	//	groupAssigner.orderIndividualsBeforeMultiPersonGroups();
		Map<String, Set<EmailDocument>> groupNamesToDocsMap = IndexUtils.partitionDocsByGroup((Collection) allDocs, groups, addressBook, trackNOTA);
		int[] allMessagesHistogram = CalendarUtil.computeHistogram(EmailUtils.datesForDocs(allDocs), intervals);
		graph_script = scriptForGroupsGraph(intervals, groupAssigner, groupNamesToDocsMap, allMessagesHistogram, 1000, 450);
	}
	else if (doPeople)
	{
		int normalizer = ProtovisUtil.normalizingMax(allDocs, addressBook, intervals);
		int[] allMessagesHistogram = CalendarUtil.computeHistogram(EmailUtils.datesForDocs(allDocs), intervals);
		Map<Contact, DetailedFacetItem> folders = IndexUtils.partitionDocsByPerson((Collection) allDocs, addressBook);
		List<DetailedFacetItem> list = new ArrayList<DetailedFacetItem>(folders.values());
		graph_script = scriptForFacetsGraph(list, intervals, (Collection) allDocs, allMessagesHistogram, 1000, 450);
	}
	else if (doFolders) /* default: if (request.getParameter("groups") != null) */
	{
		doGroups = true;
	//	groupAssigner.orderIndividualsBeforeMultiPersonGroups();
		Map<String, Set<EmailDocument>> groupNamesToDocsMap = IndexUtils.partitionDocsByGroup((Collection) allDocs, groups, addressBook, trackNOTA);
		int[] allMessagesHistogram = CalendarUtil.computeHistogram(EmailUtils.datesForDocs(allDocs), intervals);
		Map<String, DetailedFacetItem> folders = IndexUtils.partitionDocsByFolder((Collection) allDocs);
		List<DetailedFacetItem> list = new ArrayList<DetailedFacetItem>(folders.values());

		graph_script = scriptForFacetsGraph(list, intervals, (Collection) allDocs, allMessagesHistogram, 1000, 450);
	}
	else if ("sentimentsByGroup".equals(view))
	{
		graph_script = emitScriptForSentimentsByGroupGraph(request, archive, allDocs, 1000, 450, session);
	}

	if (graph_is_empty)
		out.println ("<br/><br/>" + empty_graph_message);
	else {
		out.println (graph_script);
%>

<div id="prompt_click" style="padding-left:12%;padding-right:12%;text-align:left">
Click anywhere on the graph above to view messages related to that category.  
The message view is initialized to the approximate point in time that you click on.
</div>

</div>
</div>
<% } %>
<div align="center">
<iframe frameBorder="0" name="browse_frame" id="browse_frame" width="1220px"> <!--  addition 20px for scrollbar -->
</iframe>
</div>


<jsp:include page="footer.jsp"/>
</body>
</html>
<%
	JSPHelper.logRequestComplete(request);
%>
