<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.slant.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>

<html lang="en">
<head>
<title>Links</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<jsp:include page="css/css.jsp"/>
</head>
<body>

<script type="text/javascript">
function createCSE()
{
	$('#CSE_status').html('<img width="20" src="images/spinner.gif">');
	$('#CSE_status').show();
	$.post('ajax/createCSE.jsp', {login: $('#login').val(), password: $('#password').val(), cseName: $('#cseName').val()}, 
			function(resp) { $('#CSE_status').html (resp);}
	);
}
// $('#login').clickOnEnter($('loginButton')[0]);
// submit login when enter is pressed in the password field
//$('#password').clickOnEnter($('#loginButton')[0]);
$(document).ready(function() {
	$('#password').clickOnEnter($('#loginButton')[0]);
	$('#login').clickOnEnter($('#loginButton')[0]);
	$('#cseName').clickOnEnter($('#loginButton')[0]);
});
</script>

<jsp:include page="header.jsp"/>

<%
	List<Document> docs = JSPHelper.selectDocsAsList(request, session);	
	List<LinkInfo> links = EmailUtils.getLinksForDocs(docs);
%>
<div class="panel rounded">
Sites based on the links below will be entered into a personalized <a href="http://www.google.com/cse/manage/all">Google custom search engine</a>.<br/>
This search engine restricts search results to the sites listed below.<br/>
To use this feature, you need to have a Google account.<br/>
(Note: There is a limit of 5000 domains in a Google account).<br/>

<br/><br/>

<div style="padding-left:50px">
<input placeholder="Google login" class="input-field" type="text" name="login" id="login" size="12"/> 
&nbsp;&nbsp;<br/><br/>
<input placeholder="password" class="input-field password" type="password" name="password" id="password" size="12">
&nbsp;&nbsp;<br/><br/>
<input type="text" class="input-field" name="cseName" id="cseName" size="12" value="Slant"> (search engine name)<br/><br/>
<button id="loginButton" class="tools-pushbutton" onclick='createCSE();' onmouseover="muse.highlightButton(event)" onmouseout="muse.unhighlightButton(event)">Continue</button> 
<span id="CSE_status" style="display:none">
<!--  after CSE is created, we will display some message here -->
</span>
</div>


<hr/>
<h2>List of sites</h2><p>
<b>Weight &nbsp;&nbsp;Domain</b><br/>
<% 
	CustomSearchHelper.expandShortenedURLs(links);
	List<String> httpURLs = CustomSearchHelper.linksToHttpURLs(links);
	Map<String, Float> domainWeights = CustomSearchHelper.getDomainWeights(httpURLs);
	List<Pair<String, Float>> list = Util.sortMapByValue(domainWeights);
	for (Pair<String, Float> p: list)
		out.println ("&nbsp;" + String.format ("%.3f", p.getSecond()) + " &nbsp;&nbsp;&nbsp;&nbsp; " + p.getFirst() + "<br/>");
%>
</div>

<%@include file="footer.jsp"%>
</body>
</html>
