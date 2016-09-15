<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<link rel="icon" href="images/ashoka-favicon.gif">
	<link rel="stylesheet" href="css/memory.css"/>
	<link rel="stylesheet" href="../css/fonts.css"/>
	<title>CELL error</title>
</head>
<body>
<div class="box" style="padding:50px">

	We're sorry, the test has timed out or another error has occurred.<br/>
	Please go back, retry, and contact us at cell@ashoka.edu.in if you still have trouble.

	<% out.println ("<br/>Error code:" + request.getAttribute("javax.servlet.error.status_code") + " type: " + request.getAttribute("javax.servlet.error.exception_type")); %>

</div>

</body>
</html>