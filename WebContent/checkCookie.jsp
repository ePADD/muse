<%
if (session.getAttribute("checkCookie") != null) {
	session.removeAttribute("checkCookie");
	response.sendRedirect("index.jsp");
} else {
%>
<html>
<title>Error</title>
<body>
	<p>Browser cookies are required for this site.</p>
</body>
</html>
<%
}
%>