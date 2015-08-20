<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.util.zip.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="com.google.gson.*"%>
<%@page language="java" import="org.apache.lucene.document.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.xword.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%
JSPHelper.logRequest(request);

// allow access since it may be invoked by a bookmarklet
response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
//response.setHeader("Access-Control-Allow-Origin", "http://xenon.stanford.edu");
response.setHeader("Access-Control-Allow-Credentials", "true");
response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");

Crossword c;

if (request.getParameter("useSessionXword") == null)
	c = CrosswordFromEmail.createCrossword(request);
else
 	c = (Crossword) session.getAttribute("crossword");

if (c == null)
{
	out.println ("Not enough messages in this account!");
	return;
}

int size = c.w;
if (size < 15) size = 15;
int CELL_SIZE_PX = 40;
int bodyWidth = 700+size*CELL_SIZE_PX;
boolean hintsEnabled = true;
boolean messagesEnabled = c.haveMessages;
%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<link rel="stylesheet" type="text/css" href="js/fancybox/jquery.fancybox.css?v=2.1.5" media="screen" />
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png" />
<script type='text/javascript' src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
<script type='text/javascript' src="js/json2.js"></script>
<!-- <script language='javascript' type='text/javascript' src='http://openjunction.github.com/JSJunction/strophejs/1.0.1/strophe.js'></script> -->
<script type='text/javascript' src="js/junction.js"></script>
<script type='text/javascript' src="js/muse.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>
<link type="text/css" rel="stylesheet" href="css/xword.css"/>
<!--  <link rel="stylesheet" type"text/css" href="css/printxword.css" media="print">  -->
<link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>

<%

String page_title, title;
page_title = title = "Personal Crossword";
//letters look weak without bold style on the ipad
if (HTMLUtils.runningOnTablet(request)) { %>
	<style type="text/css"> .box {font-weight:bold}</style>
<% } %>

	<script> window.runningOnTablet = <%=HTMLUtils.runningOnTablet(request)?"true":"false"%>; // note: global var 
	</script>

<script type='text/javascript' src="js/xwordbase.js"></script>

<title><%=page_title%></title>
</head>
<body style="width:<%=bodyWidth%>px">

<!--  dummy input field which gets focus on tablets to cause keyboard to pop up -->
<% if (HTMLUtils.runningOnTablet(request)) { %>
	<input style="position:absolute;color:white;height:1px;width:1px;outline-width:0;border:0;padding:0;margin:0" id="dummy" type="text"></input>
<% } %>
<%@include file="div_status.jsp"%>

<script>
	var picClues=false; 
</script>
	
	<script type="text/javascript">
	// important: this has to be before any of the ready handlers for the fill() calls
	$(document).ready(function(){ 
		clearCrossword(); 
		join(); 
		<% 
		
		boolean runningOnTablet = HTMLUtils.runningOnTablet(request);
		String message = "";
		message += "Click on squares to type a name. Shift-click to choose the orthogonal word if a square belongs to multiple words. ";
		message += (runningOnTablet ? "Hover on clues for hints." : ""); 
		message += " You can check your answers " + (messagesEnabled ? "and see the original messages ":"") + "after you\\'ve attempted all words.";
		%>
		$.jGrowl('<span class="growl"><%=message%></span>', {life: 15000});

		if (typeof cross.help !== 'undefined' && cross.help.length > 0)
			alert(cross.help);
	});
	</script>

	<!--  header -->
	<div style="width:100%;text-align:center;font-size:36px">
		<div style="float:left;color:rgba(127,127,127,0.5);font-size:16px"><%=c.placedWords.size() %> words</div>
		<%=title %>
		<% String author = "Muse"; %>
		<div style="float:right;color:rgba(127,127,127,0.5);font-size:16px">By <%=author%></div>
		<hr style="color:rgba(0,0,0,0.1);background-color:rgba(0,0,0,0.1);margin-top:5px"/>
		<div style="clear:both"></div>
	</div>
	
	<div id="main_body" style="width:<%=700+CELL_SIZE_PX*size%>px;padding:10px;margin:auto;line-height:30px">
	
	<div style="width:<%=CELL_SIZE_PX*size%>px;float:left">
		<div id="crossword">
		</div>
		<p style="color:royalblue;font-size:16px">
		<br/>
			<button style="height:30px;position:relative;top:-12px;cursor:pointer;rgba(127,127,127,0.5);" id="answers-button">Answers</button>
			
			<br/>
			<div id="save_info"></div>
		</div>

<div id="aclues" class="clues-column">
	<span class="clue-header">Across</span><p>
	<%
	out.println(c.clueColumnAsHTML(true, hintsEnabled, true /* includeWordLens */));
	 %>
</div>
<div id="dclues" class="clues-column">
	<span class="clue-header">Down</span><p>
	<% out.println(c.clueColumnAsHTML(false, hintsEnabled, true /* includeWordLens */)); %>
</div>
<div style="clear:both"></div>

<script>
<%
Gson gson = new Gson();
String json = gson.toJson(c);
out.println("var cross = " + json + "; is_creator = true;");
%>

//$(document).ready(function() { $('.fancybox').fancybox({width:420, live:true});})
</script>

<hr style="color:rgba(0,0,0,0.1);background-color:rgba(0,0,0,0.1);margin-top:5px"/>
<br/>
<br/>
</body>
</html>
<% JSPHelper.logRequestComplete(request); %>