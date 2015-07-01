<%@ page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<% JSPHelper.checkContainer(request);
request.setCharacterEncoding("UTF-8");%>
<html>
<script type="text/javascript" src="jquery/jquery.js"></script>
<script type="text/javascript" src="jquery/jquery-ui.js"></script>
<body>
<h1>टेस्ट</h1>

<form method="get" action="test2.jsp">
<% String userEmails = "userEmails"; %>
Get <input type="text" name="f" id="f" size="12" value="<%=userEmails%>"></input>

<button>submit</button>
</form>

<form method="post" action="test2.jsp">
post <input name="g" id="g" size="12"></input>
<button>submit</button>
</form>

<br/>
<button onclick="dosubmit()">ajax</button>

<%
%>

the end.

<script>
function dosubmit()
{
	var f = document.getElementById('f');
	var g = document.getElementById('g');
	var url = 'test2.jsp?f=' + f.value + '&g=' + g.value;
	alert (url);
	var x = $.get(url);
	$.get(url, function(data) {
		  document.write(data);
		  alert('Load was performed.');
		});
}
</script>
</body>
</html>