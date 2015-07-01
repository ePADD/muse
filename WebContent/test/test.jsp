<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<title>Diary Info</title>
<script type="text/javascript" src="../js/jquery/jquery.js"></script>
<script type="text/javascript" src="../js/jquery/jquery-ui.js"></script>
<link href="muse.css" rel="stylesheet" type="text/css"/>
<link href="cloud.css" rel="stylesheet" type="text/css"/>
</head>
<body>

<button id="foo" onclick="func()">Open</button>
<script>
$(document).ready(function() { 
	$('#foo').click();
});

function func() { 
	window.open ('http://stanford.edu');
}
</script>

</body>
</html>