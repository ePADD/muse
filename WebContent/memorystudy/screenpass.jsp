<%@page import="com.sun.net.httpserver.HttpServer"%>
<%@page import="com.sun.corba.se.spi.orb.StringPair"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<!DOCTYPE html>
<html>
<head>
<link rel="stylesheet" href="css/memoryjsp.css" type="text/css" />
<script src="../js/jquery/jquery.js"></script>
<script src="../js/muse.js"></script>
<link rel="icon" href="images/ashoka-favicon.gif">
<title>Finished Signup!</title>
</head>
<body>
<h1 class="title">Screening Pass</h1>
<div class="box">
<!-- Generate code, append to link. Output with a thank you message. -->
<%
	String passkey = "";

try {
	// read the new user's stats
	MemoryStudy.UserStats stats = new MemoryStudy.UserStats();
	stats.emailid = (String) session.getAttribute("screenPassEmail");
	stats.gender = (String) session.getAttribute("gender");
	stats.age = (String) session.getAttribute("age");
	stats.education = (String) session.getAttribute("education");
	stats.profession = (String) session.getAttribute("profession");
	stats.ethnicity = (String) session.getAttribute("ethnicity");
	stats.activityHistogram = (List<Integer>) session.getAttribute("activityHistogram");
	passkey = MemoryStudy.addUserAndIssueCode(stats);
} catch (NoMoreCodesException e) {
	out.println ("Sorry, but no more codes are available.");
} catch (Exception e) {
	out.println ("Sorry, but we are unable to issue any more codes right now. Please try again later.");
	JSPHelper.log.warn ("NOC SEVERE WARNING: codes file update failed!!!!");
	return;
}

%>
<br>
You are eligible! Your code is: <%=passkey %>
<p>
Please save this code.
<br>
<script>
var full = location.href;
var path = full.substring(0, (full.lastIndexOf("/")+1))
path = path + "loginlanding.html";
</script>
<p>
Please go to <a href="./loginlanding.html"><script>document.write(path);</script></a> for the actual study. 
You will have to login again, as we do not retain any of your data.  Thank you for signing up!
<p>
If you do not have an uninterrupted 45-60 minutes right now, you will have to save this link and code and visit the site later.
However, we strongly recommend you complete the study as soon as possible because it will close after the required number
of participants is reached.
<p> 
</div>
</body>
</html>