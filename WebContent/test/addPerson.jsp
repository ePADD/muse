<%@ page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.Pair"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.util.zip.*"%>
<%@page language="java" import="java.io.*"%>
String title = "Advanced Search";
%>

<p>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<title>Add a person's details</title>
<META http-equiv="Content-Type" content="text/html; charset=UTF-8">
<jsp:include page="../css/css.jsp"/>
<link rel="icon" type="image/png" href="../images/muse-favicon.png">
</head>
<body style="margin-left:10%">

<script src="../js/jquery/jquery.js" type="text/javascript"></script>
<script src="../js/muse.js" type="text/javascript"></script>
<script src="../js/protovis.js" type="text/javascript"></script>

<p>
&nbsp;
<p>
Friend name: <input id="friendName" name="friendName">  <p/>
Picture URL: <input id="pictureURL" name="pictureURL">  <p/>
Text:<br/> <textarea id="b" name="b" rows="20" cols="80"></textarea>  <p/>

<button onclick="submitPerson()">Submit</button>

<script>
function submitPerson()
{
	$.post('../ajax/addToIndex.jsp', {friendName: $('#friendName').val(), body: $('#b').val()});
}
</script>
<% // other ideas: proverbs.
%>
<jsp:include page="../footer.jsp"/>

</body>
</html>
