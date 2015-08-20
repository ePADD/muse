<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="com.google.gson.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@include file="getArchive.jspf" %>
    
<!DOCTYPE html>
<html>
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<title>Social graph</title>
    <script type="text/javascript" src="http://mbostock.github.com/d3/d3.js?2.1.3"></script>
    <script type="text/javascript" src="http://mbostock.github.com/d3/d3.geom.js?2.1.3"></script>
    <script type="text/javascript" src="http://mbostock.github.com/d3/d3.layout.js?2.1.3"></script>
    <script type="text/javascript" src="js/jquery/jquery.js"></script>
    <style type="text/css">

.link {
  stroke: #ccc;
}

.node .nodetext { font-size:12px; z-index:2000000; background-color: #777; color: green; }

.node:not(:hover) .nodetext {
  display: none;
}

    </style>
</head>
<body>
<script src="http://d3js.org/d3.v3.min.js"></script>

<% 
Collection<EmailDocument> allDocs = (Collection<EmailDocument>) session.getAttribute("emailDocs");
AddressBook ab = archive.addressBook;

if (allDocs == null)
	allDocs = (List) archive.getAllDocs();

%>
<h2 style="padding:20px;">Social graph based on <%=allDocs.size() %> messages.</h2>

<div id="graph"></div>

<% 
// first get all the unique addrs involved, and their counts
Map<String, Integer> countMap = new LinkedHashMap<String, Integer>();
for (EmailDocument ed: allDocs)
{
	List<String> addrs = ed.getAllAddrs();
	addrs = ab.convertToCanonicalAddrs(addrs);
	addrs = ab.removeOwnAddrs(addrs);
	for (String addr: addrs)
	{
		Integer I = countMap.get(addr);
		if (I == null)
			countMap.put(addr, 1);
		else
			countMap.put(addr, I+1);
	}
}

countMap = Util.reorderMapByValue(countMap);

// keep only the top cutoff addrs in uniqueAddrs
int DEFAULT_COUNT = 100;
int count = 0, cutoff = HTMLUtils.getIntParam(request, "count", DEFAULT_COUNT);
Set<String> uniqueAddrs = new LinkedHashSet<String>();
for (String addr: countMap.keySet())
{
	if (++count > cutoff)
		break;
	uniqueAddrs.add(addr);
}

Graph g = new Graph();

for (String s: uniqueAddrs)
	g.addNode(ab.getBestDisplayNameForEmail(s));

// now compute edge weights between them
for (EmailDocument ed: allDocs)
{
	List<String> addrs = ed.getAllAddrs();
	addrs = ab.convertToCanonicalAddrs(addrs);
	addrs = ab.removeOwnAddrs(addrs);
	addrs.retainAll(uniqueAddrs); // only worry about unique addrs
	for (String a: addrs)
	{
		g.bumpNodeSize(ab.getBestDisplayNameForEmail(a));
		for (String b: addrs)
			if (a != b)
				g.bumpWeight (ab.getBestDisplayNameForEmail(a), ab.getBestDisplayNameForEmail(b));
	}
}

g.finalize(5);
String json = new Gson().toJson(g);

%>
<script>
var json = <%=json%>;

var w = 1024,
h = 768;

var vis = d3.select("#graph").append("svg:svg")
.attr("width", w)
.attr("height", h);

var maxValue = d3.max ($.map(json.links, function(o) { return o.value;}));
var maxNodeSize = d3.max ($.map(json.nodes, function(o) { return o.size;}));

// add the type field 
$.map(function(o) { o.type = d3.svg.symbolTypes[~~(Math.random() * d3.svg.symbolTypes.length)]; return o;});

var force = d3.layout.force()
    .nodes(json.nodes)
    .links(json.links)
    .gravity(.2)
    .linkStrength(function(d) { return (40 *d.value) / maxValue; })
    .charge(-300)
    .size([w, h])
    .start();

var link = vis.selectAll("line.link")
    .data(json.links)
  .enter().append("svg:line")
    .attr("class", "link")
    .attr("stroke-width", function(d) { return 10 * d.value / maxValue; });

/*
json.nodes.push({
    type: d3.svg.symbolTypes[~~(Math.random() * d3.svg.symbolTypes.length)],
    size: Math.random() * 300 + 100
  });
  */
var node = vis.selectAll("g.node")
    .data(json.nodes)
  .enter().append("svg:g")
    .attr("class", "node")
    .call(force.drag);

node.append("svg:circle")
//    .attr("xlink:href", "https://d3nwyuy0nl342s.cloudfront.net/images/icons/public.png")
    .attr("cx", "0px")
    .attr("cy", "0px")
//    .attr("rx", "4px")
 //   .attr("ry", "4px")
    .attr("fill", function(d) { return d3.interpolateLab("sandybrown", "red")(Math.sqrt(d.size)/Math.sqrt(maxNodeSize)); }) // svg color names here: http://www.december.com/html/spec/colorsvg.html
    .attr("r", function(d) { var size = Math.floor(16*Math.log(d.size)/Math.log(maxNodeSize)); if (size < 5) { size = 5; } d.r = size; return size + 'px';});
//    .attr("width", function(d) { var size = Math.floor(32*Math.log(d.size)/Math.log(maxNodeSize)); if (size < 16) { size = 16; } return size + 'px';})
 //   .attr("height", function(d) { var size = Math.floor(32*Math.log(d.size)/Math.log(maxNodeSize)); if (size < 16) { size = 16; } return size + 'px';});

node.append("svg:text")
    .attr("class", "nodetext")
    .attr("dx", 12)
    .attr("dy", ".35em")
    .text(function(d) { return d.name + '(' + d.size + ')'; })
    .attr('fill', 'royalblue');

force.on("tick", function() {

  link.attr("x1", function(d) { return d.source.x; })
      .attr("y1", function(d) { return d.source.y; })
      .attr("x2", function(d) { return d.target.x; })
      .attr("y2", function(d) { return d.target.y; });

  node.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });
  // add bounding box effect: http://mbostock.github.io/d3/talk/20110921/bounding.html
  node.attr("cx", function(d) { return d.x = Math.max(d.r, Math.min(w - d.r, d.x)); })
  .attr("cy", function(d) { return d.y = Math.max(d.r, Math.min(h - d.r, d.y)); });

});
</script>
</body>
</html>