<%@page contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@include file="getArchive.jspf" %>
<!DOCTYPE HTML>
<html>
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Archive Information</title>
<jsp:include page="css/css.jsp"/>
<style>
	td > div {
		padding: 5px;
	}
</style>
<script src="js/jquery.js"></script>
</head>
<body class="fixed-width">
<jsp:include page="header.jsp"/>

<%
	// if archive not in session, nothing can be done

AddressBook ab = archive.addressBook;
String bestName = ab.getBestNameForSelf();
String title = "Email Archive " + (!Util.nullOrEmpty(bestName) ? ("of " + bestName) : "SUMMARY");
%>

<h1 style="text-align:center" "title="title_tooltip">Email Archive of <%= bestName%></h1>
<div style="text-align:center; margin:auto; width:600px;">
<div style="text-align:left; padding:5px">
<form method="get" action="browse">
<input style="font-size:14pt" name="term" size="80" placeholder="search term"/>
<br/>
<input type="radio" name="searchType" value="correspondents"/> Correspondents
<input type="radio" name="searchType" value="subject" /> Subject
<!--  <input type="radio" name="searchType" value="attachments"/> Attachments  -->
<input type="radio" name="searchType" value="original"/> Original Text
<input type="radio" name="searchType" value="All" checked/>All
<p>

Email direction:
<select name="direction" id="direction">
	<option value="in">Incoming</option>
	<option value="out">Outgoing</option>
	<option value="both" selected>Both</option>
</select>

<button style="margin-left:100px" onclick="handle_click()">Go</button>
</form>
<script>
function handle_click() { 
	var option = $("input[name=searchType]:checked").val();
	if ('original' == option) {
		$('#originalContentOnly').val('true');
	}
	if ('subject' == option) {
		$('#subjectOnly').val('true');
	}
	if ('correspondents' == option) {
		$('#correspondents').val('true');
	}
	return true;
}
</script>

<p>

</div>
</div>
<p>
<jsp:include page="footer.jsp"/>
</body>
</html>
