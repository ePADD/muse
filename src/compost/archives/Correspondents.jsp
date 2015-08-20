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
String archiveUrlParam = archiveId == null ? "''" : "'aId=" + Util.URLEncode(archiveId) + "'";

Archive archive = Sessions.loadSharedArchiveAndPrepareSession(session, archiveId);
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
<script type="text/javascript" src="/muse/js/proto_funcs.js"></script>
<script type="text/javascript" src="/muse/js/d3.v2.min.js"></script>
<script type="text/javascript" src="/muse/js/d3_funcs.js"></script>
<script type="text/javascript" src="/muse/js/jquery.prettyPhoto.js"></script>

<link href="/muse/css/prettyPhoto.css" rel="stylesheet" type="text/css"/>
<link href="/muse/css/d3_funcs.css" rel="stylesheet" type="text/css"/>
<jsp:include page="/css/css.jsp"/>

<link rel=StyleSheet href="general.css" type="text/css">
<link rel=StyleSheet href="searchresult.css" type="text/css">
<link rel=StyleSheet href="bootstrap/css/bootstrap.css" type="text/css">

<title>Correspondents</title>
</head>

<script type="text/javascript">
var data_top = {}, data_bottom = {}, browse_params = {};
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
					Browse by Correspondents
					</span>
				</nav>
				<br>

				<div id="searchresult-area">

					<div id="searchresult-info" align="center">

<!-- 						<div align="center" style="clip:rect(200,200,200,200)"> -->
<%-- 							<iframe frameBorder="0" id="charts_frame" width="1150px" height="2000px" style="position:relative;margin-bottom:10px;top:0px;left:-45px" src="/muse/charts?aId=<%=archiveId%>&view=people&maxCount=200"> --%>
<!-- 							to avoid hscroll set width = 1042 due to div .message min-width of 1000 + some padding/border -->
<!-- 							</iframe> -->
<!-- 						</div> -->

<%
	int max_count = HTMLUtils.getIntParam(request, "maxCount", -1); // max # of entries to show
	Lexicon lexicon = null;
	Collection<EmailDocument> fullEmailDocs = (Collection) archive.getAllDocs();
	Collection<EmailDocument> emailDocs = fullEmailDocs; // (Collection) JSPHelper.getSessionAttribute(session, "emailDocs");

	if (archive == null)
	{
		if (!session.isNew())
			session.invalidate();
//		session.setAttribute("loginErrorMessage", "Your session has timed out -- please click login again.");
	%>
	    <script type="text/javascript">window.location="index.jsp";</script>
	<%
		System.err.println ("Error: session has timed out, archive = " + archive);
			return;
		}

		String archiveOwner = archive.addressBook.getBestNameForSelf();

		String view = request.getParameter("view");
		if (view == null)
			view = "people";


		int size = (emailDocs == null) ? 0: emailDocs.size();
		Pair<Date, Date> p = EmailUtils.getFirstLast(size > 0 ? emailDocs : fullEmailDocs);
		Date globalStart = p.getFirst();
		Date globalEnd = p.getSecond();
		
		Calendar cStart = new GregorianCalendar(); cStart.setTime(globalStart);
		Calendar cEnd = new GregorianCalendar(); cEnd.setTime(globalEnd);
		String dateRange = cStart.get(Calendar.YEAR) + "-"
		+ String.format("%02d", (1 + cStart.get(Calendar.MONTH))) + "-"
		+ String.format("%02d", cStart.get(Calendar.DAY_OF_MONTH)) + " to "
		+ cEnd.get(Calendar.YEAR) + "-"
		+ String.format("%02d", (1 + cEnd.get(Calendar.MONTH))) + "-"
		+ String.format("%02d", cEnd.get(Calendar.DAY_OF_MONTH));

		int cFirst_year = cStart.get(Calendar.YEAR);
		int cFirst_month = cStart.get(Calendar.MONTH) - 1; // -1 to also include the left dummy pad
	%>

<div align="center"><h2>Communication Charts of <%=Util.escapeHTML(archiveOwner)%></h2><span class="db-hint">Above the line: outgoing activity. Below the line: incoming.<br><br></span></div>

<table align="center">
	<tr><td align="center">Overall activity based on <%=size%> messages, <%=dateRange%></td></tr>

	<%// the global chart
	if (emailDocs.size() > 0)
	{
	%>
	<tr><td colspan="4" align="center">
	<table class="protovis-summary shadow">
<!-- 		<tr><td> -->
<!-- 			<p style="font-size:70%" align="center">Date range <span id="slider-text"></span></p> -->
<!-- 			<div id="slider-bar" style="width:92%;height:50%;margin-left:6%"></div> -->
<!-- 		</td></tr> -->

		<tr><td colspan="2" class="noborder"><br></td></tr>
		<tr><td>
		<%
				List<Date> tmpDates = new ArrayList<Date>();
				for (EmailDocument ed: emailDocs)
					tmpDates.add(ed.date);

				//List<Date> intervals = CalendarUtil.divideIntoIntervals(globalStart, globalEnd, 160); // used for the global graph
				List<Date> fineIntervals = CalendarUtil.divideIntoMonthlyIntervals(globalStart, globalEnd);
				//int max = ProtovisUtil.findMaxInOrOutInAnInterval(tmpDates, tmpDates, fineIntervals);
				int max = -1; // auto-detect
				String[] chartSpec = { "#globalChart", "#slider-bar", "#slider-text" };
				String protovisChart = ProtovisUtil.getProtovisForDocs(chartSpec, (Collection)emailDocs, archive.addressBook, fineIntervals, max, 800, 360, /* showTotals */ true, /* inNOut */ true, /* browseParams */ archiveUrlParam, /* caption */ "ALL");
		%>
				<%=protovisChart%>
		</td></tr>
	</table>
	<br>
	</td></tr>

	<%
	} // if (emailDocs.size() > 0)
	%>

	<%// Chart type selection%>
	<tr><td>
	<% if (!ModeConfig.isPublicMode()) { %>
	View
	<select id="viewType" onchange="window.location = 'charts?maxCount=400&view=' + $('#viewType').val();">
		<option <%="sentiments".equals(view) ? "selected":"" %> value="sentiments">Sentiments over time</option>
		<option <%="groups".equals(view) ? "selected":"" %> value="groups">Groups over time</option>
		<option <%="people".equals(view) ? "selected":"" %> value="people">People over time</option>
		<option <%="folders".equals(view) ? "selected":"" %> value="folders">Folders over time</option>
	</select>
	<% } %>

	<%// now individual charts
	//List<Date> intervals = CalendarUtil.divideIntoIntervals(globalStart, globalEnd, 40); // used for individual graphs
	List<Date> intervals = CalendarUtil.divideIntoMonthlyIntervals(globalStart, globalEnd);
	Map<String, Collection<DetailedFacetItem>> detailedFacets = IndexUtils.computeDetailedFacets((Collection) emailDocs, archive, lexicon);
			
	Collection<DetailedFacetItem> allTermsForThisFacet = detailedFacets.get(view);

	if (allTermsForThisFacet == null)
	{
		JSPHelper.log.info("no items for view " + view);
		return;
	}
	
	// compute max in any interval
	List<Pair<List<Date>, List<Date>>> datas = new ArrayList<Pair<List<Date>, List<Date>>>();	
	int max = Integer.MIN_VALUE;
	Map<DetailedFacetItem, Pair<int[],int[]>> histogramMap = new LinkedHashMap<DetailedFacetItem, Pair<int[],int[]>>();
	int count = 0;
	for (DetailedFacetItem fi : allTermsForThisFacet)
	{
		count++;
		Pair<List<Date>, List<Date>> inOutDates = archive.addressBook.splitInOutDates((Collection) fi.docs, true /* inNOut */);
		int[] inGram = CalendarUtil.computeHistogram(inOutDates.getFirst(), intervals);
		int[] outGram = CalendarUtil.computeHistogram(inOutDates.getSecond(), intervals);
		histogramMap.put(fi, new Pair<int[],int[]>(inGram, outGram));
		int max_for_this_contact = ProtovisUtil.findMaxInOrOutInAnInterval(inGram, outGram, intervals);
		if (max_for_this_contact > max)
			max = max_for_this_contact;
		if (max_count > 0 && count == max_count)
			break;
	}
	%>
&nbsp;&nbsp;&nbsp;&nbsp;<%=allTermsForThisFacet.size()%> <%=Util.escapeHTML(view)%></td></tr>
<tr><td><table id="individual-charts" class="protovizcontacts">
<%
	// note: the normalization base for the per-indiv. graphs is different from the global.
	// the base here is the max in or out in an interval for any one graph.
	// now max is the max # of messages, incoming or outgoing in a single time interval

	count = 0;
	int ENTRIES_PER_ROW = 4;
	boolean row_open = false; // tracks whether a row is currently open, so we can close it later.
	for (DetailedFacetItem fi : allTermsForThisFacet)
	{
		if (count % ENTRIES_PER_ROW == 0)
		{
			out.print ("<tr>");
			row_open = true;
		}
		String tooltip = fi.description;
		tooltip = Util.escapeHTML(tooltip);
		
		//Pair<List<Date>, List<Date>> inOutDates = addressBook.splitInOutDates((Collection) fi.docs, true /* inNOut */);
		//List<Date> inDates = (inOutDates != null) ? inOutDates.getFirst() : null;
		//List<Date> outDates = (inOutDates != null) ? inOutDates.getSecond() : null;

		int[] inGram = histogramMap.get(fi).getFirst();
		int[] outGram = histogramMap.get(fi).getSecond();
		String chartDivId = new String("#c" + count);
		String[] chartSpec = { chartDivId };
		out.println ("<td title=\"" + tooltip + "\" valign=\"top\">" + ProtovisUtil.getProtovisForDetailedFacet(chartSpec, fi, inGram, outGram, intervals, max) + "</td>");

		count++;
		if (count % ENTRIES_PER_ROW == 0)
		{
			out.print ("</tr>");
			row_open = false;
		}
		if (max_count > 0 && count == max_count)
			break;
	}
	if (row_open)
		out.println ("</tr>");

%>
</table>
</tr></table>
<%
if (count < allTermsForThisFacet.size())
{
	int remaining = allTermsForThisFacet.size() - count;
	%><span class="db-hint"><br/><%=remaining%> entries not shown (<a href="charts?view=people]">Show all)</a> </span> <br/><%
}
%>
<br/>
&nbsp;&nbsp;&nbsp;&nbsp;

<script>
$("a[rel^='subchart']").prettyPhoto({
	markup: '<div class="pp_pic_holder"> \
		<div class="ppt">&nbsp;</div> \
		<div class="pp_top"> \
			<div class="pp_left"></div> \
			<div class="pp_middle"></div> \
			<div class="pp_right"></div> \
		</div> \
		<div class="pp_content_container"> \
			<div class="pp_left"> \
			<div class="pp_right"> \
				<div class="pp_content"> \
					<div class="pp_loaderIcon"></div> \
					<div class="pp_fade"> \
						<div class="pp_hoverContainer"> \
							<a class="pp_next" href="#">next</a> \
							<a class="pp_previous" href="#">previous</a> \
						</div> \
						<div id="pp_full_res"></div> \
						<div class="pp_details"> \
							<p class="pp_description" style="float:left"></p> \
							<div class="pp_nav" style="float:right;position:relative;right:30px"> \
								<a href="#" class="pp_arrow_previous">Previous</a> \
								<p class="currentTextHolder">0/0</p> \
								<a href="#" class="pp_arrow_next">Next</a> \
							</div> \
							<a class="pp_close" href="#">Close</a> \
						</div> \
					</div> \
				</div> \
			</div> \
			</div> \
		</div> \
		<div class="pp_bottom"> \
			<div class="pp_left"></div> \
			<div class="pp_middle"></div> \
			<div class="pp_right"></div> \
		</div> \
	</div> \
	<div class="pp_overlay"></div>',
	allow_resize: false, default_width: 800, default_height: 400,
	show_title: false, slideshow: false,
	changepicturecallback: function() {
		//var id = "c" + set_position; // simple, but set_position is only loosely coupled to actual div id
		var id = muse.getURLParameter("id", pp_images[set_position]);
		draw_chart("#pp_full_res", data_bottom[id], data_top[id], -1, <%=cFirst_year%>, <%=cFirst_month%>, 800, 400, false, <%=archiveUrlParam%> + "&" + browse_params[id]);
	}
});
</script>


					</div>

				</div>

			</div>
		</div>
	</div>
	
	
</body>
<%@include file="footer.html"%>
<% JSPHelper.logRequestComplete(request); %>
</html>
