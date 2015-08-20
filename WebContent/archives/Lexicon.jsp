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
String archiveDescription = Sessions.getArchiveInfoMap(archiveId).get("description");

Archive archive = Sessions.loadSharedArchiveAndPrepareSession(session, archiveId);
AddressBook addressBook = archive.addressBook;
Collection<EmailDocument> allDocs = (Collection) archive.getAllDocs();

Pair<Date, Date> p = EmailUtils.getFirstLast(allDocs);
Date globalStart = p.getFirst();
Date globalEnd = p.getSecond();
List<Date> intervals = null;
int nIntervals = 0;
if (globalStart != null && globalEnd != null) {
	intervals = CalendarUtil.divideIntoMonthlyIntervals(globalStart, globalEnd);
	nIntervals = intervals.size() - 1;
}

Archive driver = JSPHelper.getArchive(session);
int normalizer = -1;//ProtovisUtil.normalizingMax(allDocs, addressBook, intervals);
int[] allMessagesHistogram = CalendarUtil.computeHistogram(EmailUtils.datesForDocs(allDocs), intervals);
boolean graph_is_empty = true;
boolean trackNOTA = false;
String graph_script = "";
String empty_graph_message = "";
if (!Util.nullOrEmpty(allDocs))
{
	//String lexiconName = Sessions.getArchiveInfoMap(archiveId).get("lexicon"); // "lexicon" became unused XML key
	//if (Util.nullOrEmpty(lexiconName)) {
	Lexicon lex = archive.getLexicon("default"); // show only default lexicon for now
	if (lex == null) {
		empty_graph_message = "No categories are defined for this archive";
	} else {
		session.setAttribute("lexicon", lex);
		Map<String, Collection<Document>> map = lex.getEmotions(driver.indexer, (Collection) allDocs, trackNOTA, false /* original content only */); // too heavyweight -- we just want to find if the damn graph is empty...
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
}
	
if (graph_is_empty && Util.nullOrEmpty(empty_graph_message))
	empty_graph_message = "No category hits in these " + allDocs.size() + " messages";

// String graphType = null;
// if (allDocs.size() < 500) {
// 	graphType = "boxy"; // force boxy if < 100, because curvy doesn't look good
// } else {
// 	graphType = "curvy";
// }
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
		Set<DatedDocument> docs = (Set) map.get(caption);
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

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>

<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="shortcut icon" href="http://library.stanford.edu/sites/all/themes/sulair_framework/favicon.ico" type="image/vnd.microsoft.icon" />
<link rel=StyleSheet href="general.css" type="text/css">
<link rel=StyleSheet href="searchresult.css" type="text/css">
<link rel=StyleSheet href="bootstrap/css/bootstrap.css" type="text/css">

<!-- JavaScript Sources -->
<link rel="stylesheet" href="/muse/js/jquery-lightbox/css/jquery.lightbox-0.5.css" type="text/css" media="screen" />

<script type="text/javascript" src="/muse/js/jquery/jquery.js"></script>
<script type="text/javascript" src="/muse/js/jquery/jquery.tools.min.js"></script>
<script type="text/javascript" src="/muse/js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="/muse/js/jquery.safeEnter.1.0.js"></script>
<script type="text/javascript" src="/muse/js/jquery-lightbox/js/jquery.lightbox-0.5.min.js"></script>
<script type="text/javascript" src="/muse/js/jquery-lightbox/js/jquery.lightbox-0.5.pack.js"></script>
<script type="text/javascript" src="/muse/js/muse.js"></script>
<script type="text/javascript" src="/muse/js/json2.js"></script>
<script type="text/javascript" src="/muse/js/d3.v2.min.js"></script>
<script type="text/javascript" src="/muse/js/d3_funcs.js"></script>

<link href="/muse/css/d3_funcs.css" rel="stylesheet" type="text/css"/>
<title>Browse by Category</title>
</head>

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

var graphType = 'curvy';

// for d3 stacked graph

var get_click_handler_sentiments = function(jsonForData) {
	var my_labels = jsonForData.map(function(d) { return d.caption; });
	return function(d, l, startYear, startMonth) {
		$('#prompt_click').hide();
		$('#browse_frame').css('min-height', '510px');
		var url = '/muse/browse?aId=<%=Util.URLEncode(archiveId)%>&noheader=true&';
		// if group filter is in effect, make sure that it is applied to the message view
// 		if (groupIdx != null && groupIdx.length > 0)
// 			url += 'groupIdx=' + groupIdx + '&';
		url += 'sentiment=' + /* TODO: should URL encode here? */ my_labels[l] + '&startMonth=' + startMonth + '&startYear=' + startYear;
		frames['browse_frame'].window.location = url;
	};
};

// callback from the embedded browse_frame
function jog_onload(doc) {
	var f_id = 'browse_frame';
	var f = frames[f_id]; // f.document is same as "doc"
	document.getElementById(f_id).height = f.document.body.parentElement.offsetHeight + 15;// scrollHeight doesn't seem to work on Firefox. +15 to avoid spurious vscroll;

	// override the css width to prevent overflow and hscoll bar. can't do it with <style> in this page.
	var fdoc = f.document;
	fdoc.getElementsByClassName('browsepage')[0].style.width = "1040px";
	fdoc.getElementsByClassName('browse_message_area')[0].style.width = "870px";
}

</script>

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
					>
					<span>
					Browse by Category
					</span>
				</nav>
				<br>

				<div id="searchresult-area">

					<% if (allDocs.size() > 0) { 
						if (graph_is_empty) {
							out.println ("<br><p align='center'>" + empty_graph_message + "</p><br>");
						} else {
					%>
							<div id="searchresult-info" align="center">

								<div id="div_graph" style="width:80%;height:400px;float:left"></div>
								<div id="div_legend" style="width:19%;height:400px;float:left;margin-left:1%;overflow:auto"></div>
								<div style="clear:both"></div>
								<%=graph_script%>
								<br>
								<div id="prompt_click" style="text-align:center;padding-bottom:10px">
									Click anywhere on the graph above to view messages.  
								</div>
	
								<div align="center">
									<iframe frameBorder="0" name="browse_frame" id="browse_frame" width="1042px" height="0" style="margin-bottom:10px">
									<!-- to avoid hscroll set width = 1042 due to div .message min-width of 1000 + some padding/border -->
									</iframe>
								</div>

							</div>
					<%
						}
					}
					%>

					<div class="clear"></div>
				</div>

			</div>
		</div>
	</div>
	
	
</body>
<%@include file="footer.html"%>
<% JSPHelper.logRequestComplete(request); %>
</html>
