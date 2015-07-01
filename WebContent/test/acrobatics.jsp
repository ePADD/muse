<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>

<%
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
<title>Acrobatics Browser</title>
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

<% session.setAttribute("mode", "acrobatics"); %>

<form id="form1" method="#" action="#">

<table width="98%">
<tr><td valign="top">
<div align="center" id="foldersPlusAllBox" class="panel shadow">
<div class="panel-header">Files</div><br/>
<table align="center">
<tr>
<td>File prefix:&nbsp;&nbsp;</td><td><input id="prefix" name="prefix" size="40" value=""/></td>
</tr>
<tr><td>Title:&nbsp;&nbsp;</td><td><input id="title" name="title" size="40" value=""/></td></tr>
<tr><td></td>
<td>
<table width="90%" align="center">
<tr>
<td align="center" class="icon" width="16%"><img width="75" src="images/pages.png" onclick="doPages(); return false;"/></td>
<td align="center" class="icon" width="16%"><img width="75" src="images/words.png" onclick="doWords(); return false;"/></td>
</tr>
<tr>
<td align="center" width="16%"><button class="tools-button" onclick="doPages();return false;" onmouseover="muse.highlightButton(event)" onmouseout="muse.unhighlightButton(event)" >Pages</button></td>
<td align="center" width="16%"><button class="tools-button" onclick="doWords();return false;" onmouseover="muse.highlightButton(event)" onmouseout="muse.unhighlightButton(event)" >Words</button></td>
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

function getTitle() {
	var x = $('#title').val();
    return x;
}

function getKeywords()
{
	var selection = document.getElementById('keywords');
	if (selection.value != null)
     	return '&keywords=' + selection.value;
	else
		return '';
}

function getSubjectWeight()
{
	try {
	    var f = document.getElementById("subjectWeight").value;
	    return '&subjectWeight=' + f;
	} catch(err) { return '';}
}

function getFilter()
{
    return getKeywords();
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

function doWords()
{
	try {
		// note: no incremental TF-IDF option - it should be off for pdf browse right now, but we can enable it later.
		var page = "processPDFs.jsp?index=true&prefix=" + getSelectedFolderParams() + '&title=' + getTitle() + getFilter();
		fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'));
	} catch(err) { }
}

function doPages()
{
	try {
		var page = "processPDFs.jsp?prefix=" + getSelectedFolderParams() + '&title=' + getTitle() + getFilter();
		fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'));
	} catch(err) { }
}

</script>

<jsp:include page="../footer.jsp"/>

</body>
</html>
