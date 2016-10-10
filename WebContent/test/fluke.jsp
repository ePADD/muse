<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<jsp:include page="../css/css.jsp"/>
<title>Fluke</title>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
</head>
<body>

Please enter your lat long: 
<p>
<form action="flukeResults.jsp" method="get">
Search for 
<input name="term" size="20" value="food"/> &nbsp;&nbsp; near
<input name="lat" size="20" value="37.445016891037966"/> &nbsp;&nbsp;
<input name="longi" size="20" value="-122.16339826583862"/> &nbsp;&nbsp;
<br/>
<br/>
<button class="tools-pushbutton">Go</button>
</form>

</body>
</html>