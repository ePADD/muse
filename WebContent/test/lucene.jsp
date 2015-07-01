<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.text.*"%>
<%@page language="java" import="java.io.*" %>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="org.apache.lucene.store.*"%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Lucene index</title>
<script type="text/javascript" src="../js/jquery/jquery.js"></script>
<script type="text/javascript" src="../js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="../js/jquery.safeEnter.1.0.js"></script>
<script src="../js/jog_plugin.js" type="text/javascript"></script>
<script src="../js/muse.js" type="text/javascript"></script>

</head>
<body style="font-family:'Myriad Pro',Calibri,Arial; margin: 0% 5%;">

<script>
function doSearch()
{
	window.location = 'wikileaks.jsp?q=' + $('#searchbox').val();	
}

$(document).ready(function() {
	$('#searchbox').clickOnEnter($('#submitsearch')[0]);
});
</script>
<h1>Lucene index</h1>

<%	
	JSPHelper.logRequest(request);
	Collection<EmailDocument> emailDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
%>

Lucene indexing completed.

</body>
</html>