<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<link rel="stylesheet" href="css/tester.css"/>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Timed out</title>
<link rel="icon" href="images/stanford-favicon.gif">
</head>
<body>
<div class="box" style="padding:50px">
	We're sorry, the test has timed out or another error has occurred.<br/>
	Please go back, retry, and contact us at cell@ashoka.edu.in if you still have trouble.

	<% out.println ("<br/>Error code:" + request.getAttribute("javax.servlet.error.status_code") + " type: " + request.getAttribute("javax.servlet.error.exception_type")); %>

</div>

</body>
</html>