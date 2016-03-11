<%@page language="java" contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%
boolean redirect = true;
String message = ""; // set if no redirect

String age = (String) request.getParameter("age");
session.setAttribute("age", age);

int ageint = HTMLUtils.getIntParam(request, "age", -1);
if (ageint < 18) {
	redirect = false;
	message = "We limit the participants of this study to those above 18. We apologize about this inconvenience.";
}

// yes/no questions

String lang_response = (String) request.getParameter("emaillang");
if (!"yes".equalsIgnoreCase(lang_response)) {
	redirect = false;
	message = "Because our software cannot read text that is not in English, we will not be able to accept you for this study.\n";
}

String access_response = (String) request.getParameter("access");
if (!"yes".equalsIgnoreCase(access_response)) {
	redirect = false;
	message = "Because our study requires an uninterrupted test, we cannot accept you into this study.\n";
}

if (redirect) {
	response.sendRedirect("/muse/memorystudy/consentForm");
	return;
}

String gender = (String) request.getParameter("gender");
session.setAttribute("gender", gender);
String education = (String) request.getParameter("education");
session.setAttribute("education", education);
String profession = (String) request.getParameter("profession");
if (profession != null)
	profession = profession.replaceAll("\\s", "_");
session.setAttribute("profession", profession);
String ethnicity = (String) request.getParameter("ethnicity");
session.setAttribute("ethnicity", ethnicity);
%>
<!DOCTYPE html>
<html>
<head>
	<link rel="icon" href="../images/ashoka-favicon.gif">
	<link rel="stylesheet" href="css/memory.css"/>
<title>Not Eligible</title>
</head>
<body>
<div class="box">
	<img style="position:absolute;top:5px;width:50px" title="Ashoka University" src="../images/ashoka-logo.png"/>
	<h1 style="text-align:center;font-weight:normal;font-variant:normal;text-transform:none;font-family:Dancing Script, cursive">Cognitive Experiments with Life-Logs</h1>
	<hr style="color:rgba(0,0,0,0.2);background-color:rgba(0,0,0,0.2);"/>


	<h1>Sorry, you're not eligible.</h1>
	<br>
	<%= message %>
	<p class="bold">Please go back to the  <a href="eligibilitypage.html">eligibility page</a>.</p>
	<br>
</div>
</body>
</html>