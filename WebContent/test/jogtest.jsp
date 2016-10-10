<%@ page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<META http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link href="muse.css" rel="stylesheet" type="text/css"/>
<script type="text/javascript">
function updateOrientation() {
	alert ('foo');
}
alert ('test');
</script>
<body onorientationchange="updateOrientation();">
<%@include file="../header.jsp"%>

<script src="jquery/jquery.js" type="text/javascript"></script>
<script src="jquery/jquery-ui.js" type="text/javascript"></script>
<script src="js/jog.js" type="text/javascript"></script>

<table width="100%"><tr>
<td valign="middle" >
<span id="jog_status1" class="showMessageFilter rounded" style="opacity:0.5;">&nbsp;0/0&nbsp;</span>
</td>

<td valign="middle" >
<span class="showMessageFilter rounded" id="chapterName">
&nbsp;All months&nbsp;
</span>
</td>

<td valign="middle">
<span class="showMessageFilter rounded">
</span>
</td>

	<td valign="middle" align="right">
		<img style="display:none" src="images/spinner.gif" id="search_spinner"></img> &nbsp;
	Search&nbsp;&nbsp;<input class="searchbox" id="searchbox" type="text" size="15" value=""></input>
	<button class="tools-button" onclick="narrowSearch();return false;" onmouseover="highlightButton(event)" onmouseout="unhighlightButton(event)">Go</button>
</td></tr></table>

	<br/>
	<div class="muted">Click to jog. Or use left/right arrow keys. Tab/shift-tab for next/previous chapter.</div>
	<div onclick="handleClick(event)" id="jog_contents" class="message">
	<br/><br/><br/><br/><br/><br/><div style="align:center"><img src="images/spinner.gif"><h2>Loading pages...</h2> </img></div><br/><br/><br/><br/><br/>
	</div>

<div class="page" style="display:none">
<div class="doc-contents">
123
</div>
</div>

<div class="page" style="display:none">
<div class="doc-contents">
456
</div>
</div>

<script type="text/javascript">
$(document).ready(function() { start_jog('.page', '#jog_contents'); });
</script>

</body>
</html>
