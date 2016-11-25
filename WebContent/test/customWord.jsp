<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
    
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<jsp:include page="../css/css.jsp"/>
<link type="text/css" rel="stylesheet" href="../css/xword.css"/>
<!--  <link rel="stylesheet" type"text/css" href="css/printxword.css" media="toString">  -->
<link href="../css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/> 

<form method="post" action="../crossword">
<textarea name="inputTable" placeholder="lines of the form answer:clue"/>
<p>
<button type="submit">Go</button>
</form>

</body>
</html>