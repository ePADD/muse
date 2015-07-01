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

// these "term" and "person" request parameters are used downstream by actual search JSPHelper.selectDocs
String term = request.getParameter("term");
String person = request.getParameter("person");

//String type = request.getParameter("type");
String type = Util.nullOrEmpty(person) ? "Entities" : Util.nullOrEmpty(term) ? "Correspondents" : ""; // set type to "" for the case where both are (manually) supplied as URL params

String archiveName = Sessions.getArchiveInfoMap(archiveId).get("name");
String archiveDescription = Sessions.getArchiveInfoMap(archiveId).get("description");

Archive archive = Sessions.loadSharedArchiveAndPrepareSession(session, archiveId);
AddressBook addressBook = archive.addressBook;
AddressBookStats abs = addressBook.getStats();

Set<EmailDocument> allDocs = (Set) JSPHelper.selectDocs(request, session, false /* not only apply to filtered docs */, true /* or (not and) */);

assert (addressBook != null && allDocs != null);

Indexer indexer = null;
if (archive != null)
	indexer = archive.indexer;

int nAttachments = EmailUtils.countAttachmentsInDocs(allDocs);
int nImageAttachments = EmailUtils.countImageAttachmentsInDocs(allDocs);

Pair<Date, Date> p = EmailUtils.getFirstLast(allDocs);
Date globalStart = p.getFirst();
Date globalEnd = p.getSecond();
List<Date> intervals = null;
int nIntervals = 0;
if (globalStart != null && globalEnd != null) {
	intervals = CalendarUtil.divideIntoMonthlyIntervals(globalStart, globalEnd);
	nIntervals = intervals.size() - 1;
}

int normalizer = -1;//ProtovisUtil.normalizingMax((Collection) allDocs, addressBook, intervals);

String graphType = null;
if (allDocs.size() < 500) {
	graphType = "boxy"; // force boxy if < 100, because curvy doesn't look good
} else {
	graphType = "curvy";
}
%>

<%!
public String scriptFor1LayerStackedGraph(List<Date> intervals, Collection<EmailDocument> docs, String url_param, int w, int h)
{
	JSONArray j = new JSONArray();

	// create a 1 layer stacked graph
	int[] hist = CalendarUtil.computeHistogram(EmailUtils.datesForDocs((Collection) docs), intervals);
	try {
		JSONArray groupVolume = JSONUtils.arrayToJsonArray(hist);
		JSONObject o = new JSONObject();
		
		// we don't want a caption/legend. stacked graph will not show a legend for a layer if it has an empty caption
		o.put("caption", ""); o.put("full_caption", "");

		o.put("url", url_param);
		o.put("histogram", groupVolume);
		j.put(0, o); // just a 1 elem array
	} catch (Exception e) { Util.print_exception (e, JSPHelper.log); }
		
	String json = j.toString();
	if (ModeConfig.isPublicMode())
		json = Util.maskEmailDomain(json);
	
	String totalMessageVolume = JSONUtils.arrayToJson(hist); // only 1 layer so total volume same as hist
	Calendar c = new GregorianCalendar(); c.setTime(intervals.get(0)); 
	int start_mm = c.get(Calendar.MONTH), start_yy = c.get(Calendar.YEAR);
	
	return "<script type=\"text/javascript\">\n"
	+ "var jsonForData = " + json + ";\n"
	+ "draw_stacked_graph(jsonForData, " + totalMessageVolume + ", " + 0 + ", " + w + ", " + h + ", " + start_yy + ", " + start_mm + ", pv.colors('black'), get_click_handler_people_or_folders(jsonForData), graphType);\n"
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
<script type="text/javascript" src="/muse/js/protovis.js"></script>
<script type="text/javascript" src="/muse/js/muse.js"></script>
<script type="text/javascript" src="/muse/js/diary.js"></script>
<script type="text/javascript" src="/muse/js/statusUpdate.js"></script>
<script type="text/javascript" src="/muse/js/renderPerson.js"></script>
<script type="text/javascript" src="/muse/js/json2.js"></script>
<script type="text/javascript" src="/muse/js/dnd.js"></script>
<script type="text/javascript" src="/muse/js/proto_funcs.js"></script>
<script type="text/javascript" src="/muse/js/d3.v2.min.js"></script>
<script type="text/javascript" src="/muse/js/d3_funcs.js"></script>

<link href="/muse/css/d3_funcs.css" rel="stylesheet" type="text/css"/>
<title>Search: <%=Util.escapeHTML(term + person)%> (<%=type%>)</title>
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

var graphType = '';

var get_click_handler_people_or_folders = function(jsonForData) {
	return function(d, l, startYear, startMonth) {
		$('#prompt_click').hide();
		$('#browse_frame').css('min-height', '510px');
		var url = '/muse/browse?aId=<%=Util.URLEncode(archiveId)%>&noheader=true&'
				  + jsonForData[l].url + '&startMonth=' + startMonth + '&startYear=' + startYear;
		frames['browse_frame'].window.location = url;
	};
};

// for d3 chart

var data_top = {}, data_bottom = {}, browse_params = {};

function get_click_handler(url_param) {
	return function(startYear, startMonth) {
		$('#prompt_click').hide();
		$('#browse_frame').css('min-height', '510px');
		var url = '/muse/browse?aId=<%=Util.URLEncode(archiveId)%>&noheader=true&'
				  + url_param + '&startMonth=' + startMonth + '&startYear=' + startYear;
		frames['browse_frame'].window.location = url;
	};
}

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
					Search Results
					</span>
				</nav>
				<br>

				<div id="searchresult-area">

					<div class="navigation">
						<a class="btn btn-info" href="info?aId=<%=Util.URLEncode(archiveId)%>"><i class="icon-search icon-white"></i>&nbsp;New Search</a>
					</div>

					<div class="info-title">
						<h3><%=type%> Search Result</h3>
					</div>

					<div id="summary" style="margin:25px">
						<%=Util.commatize(allDocs.size())%> email messages 
						<% if ("Correspondents".equals(type)) { %>
						exchanged with "<%=Util.escapeHTML(person)%>".
						<% } else { %>
						contain the search term "<%=Util.escapeHTML(term)%>".
						<% } %>
						<%=Util.commatize(nAttachments)%> attachment<%=nAttachments != 1 ? "s" : ""%> <%=Util.commatize(nImageAttachments)%> images. 
						<!-- this is static, not adjusting to search result <%=Util.commatize(abs.nContacts)%> people.<br>  -->
						<br>
					</div>

					<% if (allDocs.size() > 0) { 
						String url_param = ("Correspondents".equals(type)) ? "person=" + Util.URLEncode(person) : "term=" + Util.URLEncode(term);
						int max = -1; // auto-detect
						String[] chartSpec = { "#resultChart" };
						String caption = null;
						String graph_script = ProtovisUtil.getProtovisForDocs(chartSpec, (Collection)allDocs, addressBook, intervals, max, 800, 360, /* showTotals */ true, /* inNOut */ true, /* browseParams */ "get_click_handler('" + url_param + "')", caption);
						//String graph_script = scriptFor1LayerStackedGraph(intervals, (Collection) allDocs, url_param, 1000, 400); %>
						<div id="searchresult-info" align="center">
							<style>
							.protovis-text {
								line-height: 2.5em; /* somehow required to adjust the placement of the in/out total numbers */
							}
							</style>
							<table class="protovis-summary"><tr><td>
							<!-- div style="margin-left:-100px" -->
							<%=graph_script%>
							<!-- /div -->
							</td></tr></table>
	
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
					<% } %>

					<div class="clear"></div>
				</div>

			</div>
		</div>
	</div>
	
	
</body>
<%@include file="footer.html"%>
<% JSPHelper.logRequestComplete(request); %>
</html>
