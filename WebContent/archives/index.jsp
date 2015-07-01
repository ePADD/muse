<%@ page language="java" import="java.io.*"%>
<%@ page language="java" import="edu.stanford.muse.webapp.*"%>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>

<%JSPHelper.logRequest(request);%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="shortcut icon" href="http://library.stanford.edu/sites/all/themes/sulair_framework/favicon.ico" type="image/vnd.microsoft.icon" />
<link rel=StyleSheet href="mainpage.css" type="text/css">
<link rel=StyleSheet href="general.css" type="text/css">
<link rel=StyleSheet href="bootstrap/css/bootstrap.css" type="text/css">
<script type="text/javascript" src="/muse/js/jquery/jquery.js"></script>
<script type="text/javascript" src="/muse/js/muse.js"></script>
<script type="text/javascript" src="/muse/js/d3.v2.min.js"></script>
<script type="text/javascript" src="/muse/js/d3_funcs.js"></script>
<title>ePADD Discovery Module - BETA SITE</title>

<style>
#intro p { text-align: justify; }

#intro {
	border-height: 1000px;
	border-style: groove;
	border-radius: 15px;
	margin-top: 10px;
	background-color: #CFDCE7;
	padding: 6px;
}

svg {
  font: 10px sans-serif;
}

path {
  stroke-width: 2;
  fill-opacity: 0;
  stroke-opacity: 1;
}

.path:nth-child(3n+0) { stroke: green }
.path:nth-child(3n+1) { stroke: red }
.path:nth-child(3n+2) { stroke: blue }

/* path legend */
#table tr:nth-child(3n+0) { stroke: green }
#table tr:nth-child(3n+1) { stroke: red }
#table tr:nth-child(3n+2) { stroke: blue }

.legend {
	width: 20px;
	height: 20px;
	float: right;
}

.axis path, .axis line, line.axis {
  fill: none;
  stroke: #000;
  stroke-width: 2;
  shape-rendering: crisp-edges;
}

.brush .extent {
  stroke: #f88;
  fill-opacity: .125;
  shape-rendering: crisp-edges;
}
</style>

</head>
<body style="margin: 0px;">
<div class="site-wrapper">
	<div id="wrapper" class="clearfix">
		<div id="wrapper-inner">
 			<%@include file="header.html"%>

			<h2 class="title" align="center">
			Email: Process, Appraise, Discover, Deliver<br>
			ePADD Discovery Module - BETA SITE
			</h2>

			<div id="intro">
				<div style="padding: 10px">
				<p>
				Welcome to the BETA SITE of the ePADD Discovery Module. This site is powered by the <a href="http://mobisocial.stanford.edu/muse">MUSE</a> project at the Stanford University Computer Science Department. Please visit the <a href="http://library.stanford.edu/libraries/spc/about"><span style="white-space:nowrap;">Special Collections &amp; University Archives</span></a> homepage for updates on current development efforts.
				</p>

				<p>
				The Discovery Module displays summary metadata extracted from email collections held by Special Collections &amp; University Archives. This metadata includes: collection extent, monthly summaries of frequently used terms, named entities, subjects, and correspondents. The module also features visualizations of entities and correspondents.
				</p>

				<p>
				For more information or access to the full content of the email collections, please contact us at <a href="mailto:speccollref@stanford.edu">speccollref@stanford.edu</a>.
				</p>
				</div>
			</div>

			<div id="tablewrapper">
<!-- 			<div id="tableheader"> -->
<!-- 			<div class="search"> -->
<!-- 			<select id="columns" onchange="sorter.search('query')"></select> -->
<!-- 			<input type="text" id="query" onkeyup="sorter.search('query')" /> -->
<!-- 			</div> -->
<!-- 			<div class="details"> -->
<!-- 			Records <span id="startrecord"></span>-<span id="endrecord"></span> of <span id="totalrecords"></span> -->
<!-- 			<a href="javascript:sorter.reset()">reset</a> -->
<!-- 			</div> -->
<!-- 			</div> -->

			<%
			String xml_fname = Sessions.getArchivesIndexFilename();
			if (Sessions.parseArchivesXml(xml_fname)) {
				String xsl_fname = getServletContext().getRealPath("css/archives_list.xsl");
				JSPHelper.xsltTransform(xml_fname, xsl_fname, out);
			} else {
				out.println("No archives.");
			}
			%>

<!-- 			<div id="tablefooter"> -->
<!-- 			<div id="tablenav"> -->
<!-- 			<div> -->
<!-- 			<img src="images/first.gif" width="16" height="16" alt="First Page" onclick="sorter.move(-1,true)" /> -->
<!-- 			<img src="images/previous.gif" width="16" height="16" alt="First Page" onclick="sorter.move(-1)" /> -->
<!-- 			<img src="images/next.gif" width="16" height="16" alt="First Page" onclick="sorter.move(1)" /> -->
<!-- 			<img src="images/last.gif" width="16" height="16" alt="Last Page" onclick="sorter.move(1,true)" /> -->
<!-- 			</div> -->
<!-- 			<div> -->
<!-- 			<select id="pagedropdown"></select> -->
<!-- 			</div> -->
<!-- 			<div> -->
<!-- 			<a href="javascript:sorter.showall()">view all</a> -->
<!-- 			</div> -->
<!-- 			</div> -->

<!-- 			<div id="tablelocation"> -->
<!-- 			<div> -->
<!-- 			<select onchange="sorter.size(this.value)"> -->
<!-- 			<option value="5">5</option> -->
<!-- 			<option value="10" selected="selected">10</option> -->
<!-- 			<option value="20">20</option> -->
<!-- 			<option value="50">50</option> -->
<!-- 			<option value="100">100</option> -->
<!-- 			</select> -->
<!-- 			<span>Entries Per Page</span> -->
<!-- 			</div> -->
<!-- 			<div class="page">Page <span id="currentpage"></span> of <span id="totalpages"></span></div> -->
<!-- 			</div> -->
<!-- 			</div> -->

			</div>
			<script type="text/javascript" src="script.js"></script>
			<script type="text/javascript">
			var sorter = new TINY.table.sorter('sorter','table',{
			headclass:'head',
			ascclass:'asc',
			descclass:'desc',
			evenclass:'evenrow',
			oddclass:'oddrow',
			evenselclass:'evenselected',
			oddselclass:'oddselected',
			paginate:true,
			size:10,
			//sortcolumn:0,
			//sortdir:1,
			//sum:[8],
			//avg:[6,7,8,9],
			//columns:[{index:7, format:'%', decimals:1},{index:8, format:'$', decimals:0}],
			init:true
			});
			</script>

			<div id="count-chart-wrapper" class="white-box">
			<div align="center"><b>Email count by collection thru time</b></div>
			<div id="count-chart"></div>
			</div>
			<script>
			  var nMonths = 200;
			  var nLines = 2;

			  var data = d3.range(nLines).map(function(i) {
			    return { histogram: [Math.floor(Math.random()*(100 + Math.random()*100))] };
			  });

			  for (var i = 1; i < nMonths; i++) {
			    data.map(function(d) {
			      var v = d.histogram[i-1] + Math.floor((Math.random()-0.5)*80);
			      d.histogram.push(v < 0 ? 0 : v);
			    });
			  }

			  data.map(function(d) {
			    d3.range(Math.random()*nMonths/3).map(function(i) {
			      d.histogram[i] = 0;
			    });
			    d3.range(Math.random()*nMonths/3).map(function(i) {
			      d.histogram[d.histogram.length-i] = 0;
			    });
			  });

			  draw_count_chart("#count-chart", data, 1999, 0, 650, 300);
			</script>

		</div>
	</div>
</div>
</body>
<%@include file="footer.html"%>
</html> 
<%JSPHelper.logRequestComplete(request);%>