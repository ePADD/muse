<%@page language="java" contentType="text/html; charset=utf-8"%>
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

String inus_response = (String) request.getParameter("residenceinus");
if (!"yes".equalsIgnoreCase(inus_response)) {
	redirect = false;
	message = "Unfortunately, the scope of our study is currently limited to those living within the United States.\n";
}

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
profession = profession.replaceAll("\\s", "_");
session.setAttribute("profession", profession);
String ethnicity = (String) request.getParameter("ethnicity");
session.setAttribute("ethnicity", ethnicity);
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<link rel="stylesheet" href="css/memoryjsp.css" type="text/css" />
<link rel="icon" href="images/stanford-favicon.gif">
<title>Not Eligible</title>
</head>
<body>
<div class="box">
<h1 class="title">Sorry, you're not eligible</h1>
	<br>
	<%= message %>
	<p class="bold">Please go back to the  <a href="eligibilitypage.html">eligibility page</a>.</p>
	<br>
</div>
</body>
</html>