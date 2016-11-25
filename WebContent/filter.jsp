<%@page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.util.zip.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.Pair"%><!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%
/// NOTE **********************************
// This page is fragile, it can be invoked on ANY web page through the lens 


//https://developer.mozilla.org/en/http_access_control
response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
//response.setHeader("Access-Control-Allow-Origin", "*");
//response.setHeader("Access-Control-Allow-Origin", "http://xenon.stanford.edu");
response.setHeader("Access-Control-Allow-Credentials", "true");

Archive archive = JSPHelper.getArchive(session);
Collection<Document> allDocs = (Collection<Document>) JSPHelper.getSessionAttribute(session, "emailDocs");
if (Util.nullOrEmpty(allDocs)) {
	allDocs = archive.getAllDocs();
	session.setAttribute("emailDocs", allDocs);
 }

AddressBook addressBook = archive.addressBook;
GroupAssigner groupAssigner = archive.groupAssigner;
String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
Lexicon lexicon = (Lexicon) JSPHelper.getSessionAttribute(session, "lexicon");
if (lexicon == null)
{
	lexicon = archive.getLexicon("default");
	session.setAttribute("lexicon", lexicon);	
}

NewFilter currentFilter = (NewFilter) JSPHelper.getSessionAttribute(session, "currentFilter");

// read the first/last date of allDocs
Pair<Date, Date> p = EmailUtils.getFirstLast((List) allDocs);
Calendar start = new GregorianCalendar();
if (p.getFirst() != null)
	start.setTime(p.getFirst());
Calendar end = new GregorianCalendar();
if (p.getSecond() != null)
	end.setTime(p.getSecond());
int startYear = start.get(Calendar.YEAR), startMonth = start.get(Calendar.MONTH);
int endYear = end.get(Calendar.YEAR), endMonth = end.get(Calendar.MONTH);

assert(Calendar.JANUARY == 0 && startMonth >= 0 && startMonth <= 11); // assumption for convert_month_num_to_yy_mm()

// if current filter is not set the gStart/EndYear/Month are all the same as for the current alldocs
int gStartYear = startYear, gStartMonth = startMonth, gEndYear = endYear, gEndMonth = endMonth;
if (currentFilter != null) {
	try {
		gStartYear = Integer.parseInt(currentFilter.get("gStartYear"));
		gStartMonth = Integer.parseInt(currentFilter.get("gStartMonth"));
		gEndYear = Integer.parseInt(currentFilter.get("gEndYear"));
		gEndMonth = Integer.parseInt(currentFilter.get("gEndMonth"));
		/*
		// maybe less jumpy to get the current start/end from the filter
		// rather than extract them from the current docs (which can
		// give visually different range from what user specified/saw)
		startYear = Integer.parseInt(currentFilter.get("year"));
		startMonth = Integer.parseInt(currentFilter.get("month")) - 1;
		endYear = Integer.parseInt(currentFilter.get("endYear"));
		endMonth = Integer.parseInt(currentFilter.get("endMonth")) - 1;
		*/
	} catch (NumberFormatException nfe) {
		Util.print_exception(nfe, JSPHelper.log);
	}
}

int g_n_months = (gEndYear*12 + gEndMonth) - (gStartYear*12 + gStartMonth); // global
int n_months = (endYear*12 + endMonth) - (startYear*12 + startMonth); // for current selected allDocs

Map<String, Collection<DetailedFacetItem>> facetMap = IndexUtils.computeDetailedFacets(allDocs, archive);
String muse_url = request.getParameter("muse_url");
if (muse_url == null)
	muse_url = "";

String op = "and", type = "include";
if (currentFilter != null)
{
	op = currentFilter.get("op");
	type = currentFilter.get("type");	 // note: "type", not "filterType"
}
String museURL = HTMLUtils.getRootURL(request);
%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<style> /* warning: duplicated from muse.css -- because it is independently needed when this filter is invokved on lens pages */
.muse-overlay{
	display: none; 
	position: absolute; 
	top: 0%;
	left: 0%; 
	width: 100%;
	height: 100%; 
	background-color: black; 
	z-index:999; 
	-moz-opacity: 0.75;
	 opacity:.60; 
	 filter: alpha(opacity=60);
}
</style>

<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script src="js/muse.js"></script>

<%@include file="filter_common.html"%>

<script type="text/javascript">
var n_months = <%=n_months%>;
var g_n_months = <%=g_n_months%>;
var startYear = <%=startYear%>;
var startMonth = <%=startMonth%>;
var endYear = <%=endYear%>;
var endMonth = <%=endMonth%>;
var gStartYear = <%=gStartYear%>;
var gStartMonth = <%=gStartMonth%>;
var gEndYear = <%=gEndYear%>;
var gEndMonth = <%=gEndMonth%>;

make_date_slider("#slider-range", "#amount", g_n_months, gStartYear, gStartMonth, n_months, startYear, startMonth);
</script>

<title>Filters</title>
</head>
<body>
<!--  important: this html is replicated in muse.js for showing the header... update muse.js too in case of changes -->
<div style="padding:7px;margin:7px;">
<div style="position:relative">
	<div style="float:left">
		<h2><img style="width:20px;position:relative;top:2px"src="<%=museURL%>/images/filter-icon.png">Message Filters</h2>
	</div>
	<div class="cancel-filter" style="float:right;top:-10px;right:-20px;position:absolute"> <img src="<%=museURL%>/images/closetab.png"/> </div>
	<div style="clear:both"></div>
	</div>
	<hr style="width:95%;color:rgba(0,0,0,0.2)"/>
<div>
<p>
<select id="filterType">
<option value="include" <%="include".equals(type) ? "selected=\"selected\"":""%>>Include</option>
<option value="exclude" <%="exclude".equals(type) ? "selected=\"selected\"":""%>>Exclude</option>
</select> 
messages matching
<select id="op">
<option value="and" <%=(!"or".equals(op)) ? "selected=\"selected\"":""%>>All</option>
<option  value="or" <%="or".equals(op) ? "selected=\"selected\"":""%>>Any</option>
</select>
of:

<p>
	date range
	<span id="amount" style="text-transform:uppercase;"/>
</p>
<div id="slider-range" style="width:95%"></div>
<p>

<p>
	<input placeholder="search terms" id="search-term" size="20"/>
	<%
	if (ModeConfig.isProcessingMode()) {
		%><input type="checkbox" id="unindexed">Verbatim regexp match</input><%
	}
	%>
</p>
<p>

<%
List<DetailedFacetItem> allFacetItems = new ArrayList<DetailedFacetItem>();

for (String s: facetMap.keySet())
{
	// the facet name is stuffed into the id of the outer <p> that contains all that facet's elements
	%>
	<div class="facet-category">
	<span id="facet-name-<%=s%>"><%=s%></span> <!--  title -->
	<%
	// assemble html for all the elements for this kind of facet
	Collection<DetailedFacetItem> items = facetMap.get(s);
	List<String> htmlForFacets = new ArrayList<String>();
	for (DetailedFacetItem fi: items)
	{
		if (fi.totalCount() == 0)
			continue;
		
		// set checked if currentFilter contains this facet
		boolean checked = (currentFilter != null) && currentFilter.containsFacet(fi);
		String checked_str = checked ? "checked":"";
		
		String html = "<input style=\"height:10px\" id=\"url-" + fi.messagesURL + "\" class=\"facet\" type=\"checkbox\" " + checked_str + "></input><span>" + Util.escapeHTML(fi.name) + "</span> (" + fi.totalCount() + ")<br/>";		
		allFacetItems.add(fi);
		htmlForFacets.add(html);
	}
	
	%>
	<div class="facet-items" style="display:none">
	<%
	// htmlForFacets is a list of html for each indiv. element
	int count = 0;
	int N_INITIAL_FACETS = 5;
	out.println ("");

	// toString the first 5 items, rest hidden under a More link
	for (String html: htmlForFacets)
	{
		out.println (html);
		if (++count == N_INITIAL_FACETS) {
				out.println("<span style=\"display:none;margin:0px;\">\n");
		}		
	}
	
	if (count >= N_INITIAL_FACETS)
	{
		out.println("</span>"); // close the more items div
		if (count > N_INITIAL_FACETS) // note this has to be shown only if strictly more than N_INITIAL_FACETS
			out.println("<span class=\"clickableLink\" style=\"color:royalblue;text-align:right;padding-right:10px;font-size:80%\" onclick=\"muse.reveal(this)\">More</span>\n");
	}	
	%>
	</div> <!--  facet-items -->
	</div> <!--  outer div for facet-name-* -->
	<%
}

boolean filterCurrentlyInEffect = false;
Collection<Document> fullEmailDocs = archive.getAllDocs();
if (fullEmailDocs != null && fullEmailDocs.size() != allDocs.size())
	filterCurrentlyInEffect = true;
%>
	
<script type="text/javascript">
function assign_filter_handlers($) {
	$('[id^=facet-name-]').hover(function (){
	    $(this).css("text-decoration", "underline");
    },function(){
    	$(this).css("text-decoration", "none");
    }
);
}

$(document).ready(function() { 
	assign_filter_handlers($);
	$('[id^=facet-name-]').click(function(event) {
		// event.target is facet-name-* span which is the title of the facet.
		// look for sibling facet-items div
		muse.log ("toggling " + $(event.target).html());
		$('.facet-items', $(event.target).closest('div')).toggle();
	});	
	$('.facet').click(function(event) {
		// event.target is facet-name-* span which is the title of the facet.
		// look for sibling facet-items div
		muse.log ("toggling " + $(event.target).html());
		$('.facet-items', $(event.target).closest('div')).toggle();
	});	
	
	// for each checked facet, go to the closest facet-category (which contains the entire html for the facet including the title)
	// within that, bold the #facet-name-* element to bold the category name for any facet type that has an enabled facet
	var closest_facet_items = $('.facet-items input:checked').closest('.facet-category');
	$('[id^=facet-name-]', closest_facet_items).css('font-weight', 'bold');

});
</script>
<br/>
<button onclick="javascript:saveFilter()" style="font-size:80%">Apply filter</button>
<% if (filterCurrentlyInEffect) { %>
&nbsp;
<button onclick="javascript:removeFilter()" style="font-size:80%">Remove filter</button>
<% } %>
&nbsp;
<button class="cancel-filter" style="font-size:80%">Cancel</button>
<img id="filter_spinner" src="images/spinner.gif" style="display:none;position:relative;top:8px;width:15px"/>

<script type="text/javascript">
function removeFilter() { 
	$.ajax({url: 'ajax/removeFilter.jsp', 
		success: function(response) {
					// muse_response = trim(response);
			window.location.reload();
		},
		error: function(r) { alert ('remove filter failed'); window.location.reload();},
		type: 'GET',
		dataType: 'json'
	});
};

function saveFilter()
{
	$('#filter_spinner').show();

	// full_url += 'name=' + $('#filterName').val();
	var url_params = '&type=' + $('#filterType').val();	
	url_params += '&op=' + $('#op').val();
	
	// the current status of the date slider is reflected in the values option
	var values = $('#slider-range').slider('option', 'values');
	// convert the 2 ends of the slider to from a month offset to mm,yy
	var slider_start = convert_month_num_to_yy_mm(values[0], gStartYear, gStartMonth);
	var slider_end = convert_month_num_to_yy_mm(values[1], gStartYear, gStartMonth);
	muse.log (values[0] + ' - ' + values[1]);
	url_params += '&year=' + slider_start.yy;
	url_params += '&month=' + slider_start.mm;
	url_params += '&endYear=' + slider_end.yy;
	url_params += '&endMonth=' + slider_end.mm;

	// g* params are for global start/end yy/mm. these are for all the indexed docs, not just the current filtered ones
	// first month is 0 (0-based) becauses it is never processed by convert_month_num_to_yy_mm()
	// maybe we should make these g*Month 1-based to be consistent with above month/endMonth
	url_params += '&gStartYear=' + gStartYear + '&gStartMonth=' + gStartMonth;
	url_params += '&gEndYear=' + gEndYear + '&gEndMonth=' + gEndMonth;
	
	var search_terms = $('#search-term').val();
	if (search_terms != null && search_terms.length > 0) {
		url_params += '&term=' + search_terms;
		if ($('#unindexed').is(':checked'))
			url_params += '&unindexed=on'; // + $('#unindexed').val();
	}

	var full_url = 'ajax/saveFilter.jsp?' + url_params;
	
	var $checked = $('.facet:checked'); // next element of the checkboxes
	for (var i = 0; i < $checked.length; i++)
	{
		var url = $($checked[i]).closest('[id^=url-]').attr('id'); // look for element with starting id = http://stackoverflow.com/questions/5376431/wildcards-in-jquery-selectors
	 	url = url.substring('url-'.length);
	 	full_url += '&' + url;
	}
	muse.log ('url is ' + full_url);
	$.ajax({url: full_url, 
			success: function(response) {
						// muse_response = trim(response);
//						$('body').html('Filter has ' + response.nDocs + ' messages. <a href="info">Back</a>');
						$('#filter_spinner').hide(); // not really needed because we're going to reload the whole page, but still...
						window.location.reload();

					},
			error: function(r) { alert ('save filter failed'); window.location.reload();},
			type: 'GET',
			dataType: 'json'
		});
}
</script>
</div>
</div>
</body>
</html>
