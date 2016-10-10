<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.bespoke.mining.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>
<html>
<head>
<title>Groups Visualization</title>
<link href="muse.css" rel="stylesheet" type="text/css"/>
<link href="cloud.css" rel="stylesheet" type="text/css"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/diary.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>
<script type="text/javascript" src="js/json2.js"></script>
<script type="text/javascript" src="js/dnd.js"></script>
<script type="text/javascript" src="js/protovis.js"></script>

</head>
<body>
<%
   JSPHelper.logRequest(request);

   if ("groups".equals(JSPHelper.getSessionAttribute(session, "mode")))
   {
	%>
		<%@include file="/header_groups.html"%>
<% } else { %>
	<%@include file="/header.html"%>
<% }


// ** START NEW CODE: INSERTED BY ANKITA **
out.println ("<br/><b>Visualizations with D3</b>\n");
out.println ("<br/><a href=\"viz-d3.jsp\">Visualization</a> without iterate\n");
/*
out.println ("<br/><b>Visualizations with no iterations set</b>\n");
out.println ("<br/><a href=\"viz1a.jsp\">Visualization</a> with invisible links from group nodes to invisible central node\n");
out.println ("<br/><a href=\"viz1c.jsp\">Visualization</a> with invisible links from group nodes to invisible central node + with individual nodes\n");
out.println("<br/>\n");
out.println ("<br/><a href=\"viz2.jsp\">Visualization</a> with colored edges and discolored nodes<br/>\n");

out.println ("<br/><b>Visualizations with iterations set to 200</b>\n");
out.println ("<br/><a href=\"viz1ai.jsp\">Visualization</a> with invisible links from group nodes to invisible central node\n");
out.println ("<br/><a href=\"viz1ci.jsp\">Visualization</a> with invisible links from group nodes to invisible central node + with individual nodes\n");
out.println("<br/>\n");
out.println ("<br/><a href=\"viz2i.jsp\">Visualization</a> with colored edges and discolored nodes<br/>\n");
*/
out.println ("<br/><b>Visualizations with Protovis</b>\n");
out.println ("<br/><a href=\"viz3a.jsp\">default</a>\n");
out.println ("<br/><a href=\"viz3c.jsp\">individual nodes around central node</a>\n");
out.println ("<br/><a href=\"viz3d.jsp\">individual nodes in a panel</a>\n");
out.println ("<br/><a href=\"viz5.jsp\">default with colored edges and colored nodes</a>\n");
out.println ("<br/><a href=\"viz6.jsp\">default with skeletal topology, double click to expand</a><br/>\n");

/*
//CODE INSERTING FOR CLUSTERING COMPARISON

    String rootDir = JSPHelper.getRootDir(request);
String simple_groups = (String) JSPHelper.getSessionAttribute(session, "simple_groups");

new File(rootDir).mkdirs();
String file = rootDir + File.separator + "clustering_input.txt";
PrintWriter pw = new PrintWriter(file, "UTF-8");
pw.println(simple_groups);
pw.close();
*/

//** END NEW CODE: INSERTED BY ANKITA **
%>
