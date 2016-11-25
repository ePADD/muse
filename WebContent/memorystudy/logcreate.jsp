<%@page import="com.sun.corba.se.spi.orb.StringPair"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="icon" href="../images/ashoka-favicon.gif">
<title>Creating Study Log</title>
</head>
<body>
<%
//create file
String emailid = (String) session.getAttribute ("emailid");
String userid = (String) session.getAttribute("userid");
JSPHelper.log.info("TESTLOG - Userid,Emailid,Total_Time,Total_Items,Number_Correct,Number_Correct_w_Hint,Number_Incorrect,Gender,Age,Education_Level,Profession,Ethnicity\n");
String age = (String) session.getAttribute("age");
String gender = (String) session.getAttribute("gender");
String education = (String) session.getAttribute("education");
String profession = (String) session.getAttribute("profession");
String ethnicity = (String) session.getAttribute("ethnicity");
String totalitems = (String) session.getAttribute("totalitems");

Date starttime = (Date) session.getAttribute("starttime");
Date endtime = (Date) session.getAttribute("endtime");
long timeBetweeninMillis = endtime.getTime() - starttime.getTime();
long totaltime = (timeBetweeninMillis/60000);

String correct = (String) session.getAttribute("numbercorrect");
String incorrect = (String) session.getAttribute("numberincorrect");
String correctwhint = (String) session.getAttribute("numbercorrectwhint");
JSPHelper.log.info("TESTLOG - " + userid+","+emailid +","+ totaltime+","+totalitems+","+correct+","+correctwhint+","+incorrect+","+gender+","+age+","+education+","+profession+","+ethnicity);

response.sendRedirect("/muse/memorystudy/testfinish.html");
%>
</body>
</html>