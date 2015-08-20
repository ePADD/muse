<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>    
<!DOCTYPE html>
<html>
<head>
<title>Input reference text</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/muse.js"></script>

<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
</head>
<body class="fixed-width">
<jsp:include page="header.jsp"/>

<div class="panel" style="padding:20px 5%">
Provide some reference text about the owner of this email archive, or provide a list of URLs. <br/>
For example, you can paste in text of the owner's wikipedia page.<br/>
Muse will generate a set of leads into the archive based on this text.
<br/>

<form id="folders" method="post" action="reflectText">
<textarea name="refText" id="refText" cols="80" rows="30"></textarea>
<br/>
<input style="margin-top: 5px" type="submit" name="Submit"></input>
</form>

<p>
<!--  <a href="dump.jsp">Click here</a> to dump messages, one file per message (for use by other text processing tools.)  -->

<p>
Back to <a href="info">full info page</a>.
</div>

</body>
</html>
