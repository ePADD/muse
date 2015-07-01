<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<% 	JSPHelper.logRequest(request); %>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%
	JSPHelper.logRequest(request);

	session.setMaxInactiveInterval(-1);
    response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
    response.setHeader("Pragma","no-cache"); //HTTP 1.0
    response.setDateHeader ("Expires", 0); //prevent caching at the proxy server
    String userKey = "user";
    session.setAttribute("userKey", userKey);
	String documentRootPath = application.getRealPath("/").toString();
    session.setAttribute("cacheDir", System.getProperty("user.home") + File.separator + ".muse" + File.separator + userKey);

%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<title>Index NYT</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>

<jsp:include page="../css/css.jsp"/>
</head>
<body>
<%@include file="../header.jsp"%>

<p/>
<p/>

<%@include file="../div_status.jsp"%>

<form id="form1" method="#" action="#">

<table width="98%">
<tr><td valign="top">
<div align="center" id="foldersPlusAllBox" class="panel shadow">
<div class="panel-header">Files</div><br/>
<table align="center">
<tr>
<td>File prefix:&nbsp;&nbsp;</td><td><input id="prefix" name="prefix" size="40" value="/Users/hangal/NYT/muse/nyt."></input></td>
</tr>
<tr><td></td>
<td>
<table width="90%" align="center">
<tr>
<td align="center"  class="icon" width="16%"><img width="75" src="images/monthly.png" onclick="submitFolders(); return false;"/></td>
<td align="center"  class="icon" width="16%"><img width="75" src="images/yearly.png" onclick="doLifeBlogYearly(); return false;"/></td>
</tr>
<tr>
<td align="center" width="16%"><button class="tools-button" onclick="submitFolders();return false;" onmouseover="muse.highlightButton(event)" onmouseout="muse.unhighlightButton(event)" >Monthly</button></td>
<td align="center" width="16%"><button class="tools-button" onclick="doLifeBlogYearly();return false;" onmouseover="muse.highlightButton(event)" onmouseout="muse.unhighlightButton(event)" >Yearly</button></td>
</tr>
</table>

</td></tr>
</table>

</div>
<br/>
<span class="db-hint" onclick="muse.toggle_advanced_panel()" id="div_advanced_text" style="text-decoration:underline">Advanced controls</span>
<%@include file="../div_advanced.jsp"%><br/><p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;

</td>
</tr>
</table>
</form>

<script type="text/javascript">
//throws an exception if at least one folder is not selected
function getSelectedFolderParams() {
	var x = $('#prefix').val();
    return x;
}

function getDateRange()
{
	var selection = document.getElementById('dateRange');
	if (selection.value != null)
     	return '&dateRange=' + selection.value;
	else
		return '';
}

function getKeywords()
{
	var selection = document.getElementById('keywords');
	if (selection.value != null)
     	return '&keywords=' + selection.value;
	else
		return '';
}

function getSentOnly()
{
    var f = document.getElementById("sentOnly").checked;
    return '&sentOnly=' + f;
}

function getIncrementalTFIDF()
{
	// advanced params should be inside try-catch because adv. control panel may be missing
	try {
	    var f = document.getElementById("incrementalTFIDF").checked;
	    return '&incrementalTFIDF=' + f;
	} catch(err) { return '';}
}

function getNER()
{
	// advanced params should be inside try-catch because adv. control panel may be missing
	try {
	    var f = document.getElementById("NER").checked;
	    return '&NER=' + f;
	} catch(err) { return '';}
}

function getSubjectWeight()
{
	try {
	    var f = document.getElementById("subjectWeight").value;
	    return '&subjectWeight=' + f;
	} catch(err) { return '';}
}

function includeQuotedMessages()
{
	try {
	    var f = document.getElementById("includeQuotedMessages").checked;
	    return '&includeQuotedMessages=' + f;
	} catch(err) { return '';}
}

function getFilter()
{
    return getSentOnly() + getDateRange() + getKeywords() + getIncrementalTFIDF() + getNER() + getSubjectWeight();
}

function submitFolders()
{
	try {
		var page = "getTextPage.jsp?period=Monthly&prefix=" + getSelectedFolderParams()  + getFilter();
		fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'));
	} catch(err) { }
}

function doLifeBlogYearly()
{
	try {
		var page = "getTextPage.jsp?period=Yearly&prefix=" + getSelectedFolderParams()  + getFilter();
		fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'));
	} catch(err) { }
}
</script>

<jsp:include page="../footer.jsp"/>

</body>
</html>
