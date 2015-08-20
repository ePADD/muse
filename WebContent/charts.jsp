<%@ page contentType="text/html; charset=UTF-8"%>

<!DOCTYPE HTML>
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<META http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Charts</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/protovis.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/proto_funcs.js"></script>
<script type="text/javascript" src="js/d3.v2.min.js"></script>
<script type="text/javascript" src="js/d3_funcs.js"></script>
<script type="text/javascript" src="js/jquery.prettyPhoto.js"></script>
<jsp:include page="css/css.jsp"/>
<%@include file="filter_common.html"%>

<link href="css/prettyPhoto.css" rel="stylesheet" type="text/css"/>
<link href="css/d3_funcs.css" rel="stylesheet" type="text/css"/>

<script type="text/javascript">
var data_top = {}, data_bottom = {}, browse_params = {};

function get_date_change_func(chart_canvas, below_data, above_data, w, h, startYear, startMonth)
{
	return function( event, ui ) {
		var idx0 = ui.values[0];
		var idx1 = ui.values[1] + 2; // +2 to add left/right pads so that the chart always has at least 3 ticks
		var date0 = startYear*12+startMonth + idx0;
		var date1 = startYear*12+startMonth + idx1;
		draw_protovis_box(below_data.slice(idx0,idx1+1), above_data.slice(idx0,idx1+1), w, h, -1, Math.floor(date0/12), date0%12, Math.floor(date1/12), date1%12, chart_canvas);
	}
}
</script>
</head>
<body>
<% if (!ModeConfig.isPublicMode()) { %>
<jsp:include page="header.jsp"/>
<% } %>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>

<%
	JSPHelper.logRequest(request);

	String archiveUrlParam = "''";

	int max_count = HTMLUtils.getIntParam(request, "maxCount", -1); // max # of entries to show
	Collection<EmailDocument> emailDocs = (Collection) JSPHelper.getSessionAttribute(session, "emailDocs");
	Lexicon lexicon = (Lexicon) JSPHelper.getSessionAttribute(session, "lexicon");
	Archive archive = JSPHelper.getArchive(session);
	Collection<EmailDocument> fullEmailDocs = (Collection) archive.getAllDocs();

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

		if (emailDocs == null)
			emailDocs = (Collection) archive.getAllDocs();
		
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

<div align="center"><h2>Communication Charts of <%=archiveOwner%></h2><span class="db-hint">Above the line: outgoing activity. Below the line: incoming.<br><br></span></div>

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
&nbsp;&nbsp;&nbsp;&nbsp;<%=allTermsForThisFacet.size()%> <%=view%></td></tr>
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

<%@include file="footer.jsp"%>
</body>
</html>
<% JSPHelper.logRequestComplete(request); %>
