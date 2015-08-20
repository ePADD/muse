<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>

<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="icon" href="images/stanford-favicon.gif">
<link rel="stylesheet" href="css/tester.css"/>
<title>Answers Page</title>
</head>
<body>
<%
	StringBuilder statsLog = new StringBuilder();
	MemoryStudy study = (MemoryStudy) session.getAttribute("study");
	if (study == null) {
		out.println("Sorry, your session has timed out, please retry");
		return;
	}	
	boolean debug = "true".equals(session.getAttribute("debug"));
	String newline = "<br>";
	int nCorrect = 0;
	for (MemoryQuestion mq : study.getQuestions())
		if (mq.isUserAnswerCorrect()) 
			nCorrect++;
%>
<div class="box">
<p>
Here are your questions and the correct answers. You got <%=nCorrect%> out of <%=study.getQuestions().size()%> correct.
You are welcome to save or print a copy of this page for your records.
<p>
<%
	int idx = 1;
	for (MemoryQuestion mq : study.getQuestions()) {
		if (!mq.isUserAnswerCorrect()) {
			int wrongAnswerOption = HTMLUtils.getIntParam(request, "wrongAnswerOption" + idx, -1);
			mq.setWrongAnswerReason(wrongAnswerOption);
		}

		Date clueDate = new Date(mq.clue.clueStats.dateOfMessage);
		String date = CalendarUtil.formatDateForDisplay(clueDate);
		String correctAnswer = mq.getCorrectAnswer();
		String userAnswer = mq.getUserAnswer();
		if (userAnswer == null)
			userAnswer = "";

		String guessedDate = "";
		if (mq.stats.recency >= 1 && mq.stats.recency <= 12) // recency goes from 1 to 12
		{
			Date d1 = new Date();
			GregorianCalendar gc = new GregorianCalendar();
			gc.setTime(d1);
			int month = gc.get(Calendar.MONTH);
			int year = gc.get(Calendar.YEAR);
			month -= (mq.stats.recency-1); // -1 because for current month recency is 1
			if (month < 0)
			{
				month += 12;
				year--;
			}
			gc.set(year, month, 1);
			d1 = new java.util.Date(gc.getTimeInMillis());
			guessedDate = "(Your guess:" + new java.text.SimpleDateFormat("MMMM").format(d1) + " " + year + ")";
		}
		else if (mq.stats.recency == 0)
			guessedDate = ("(Your guess: no idea)");
		correctAnswer = Util.canonicalizeSpaces(correctAnswer);
		userAnswer = Util.canonicalizeSpaces(userAnswer);
	%>
		<div class="question">
		<%=idx%>. <%=mq.getClueToShow().clue%>
		</div>
		<span style="color:#666;font-size:small;margin-left:5%;position:relative;top:-25px"><%= date%> <%=guessedDate%></span><br/>
		<br/>Answer: <%=correctAnswer%>
		<span style="color:<%=(mq.isUserAnswerCorrect() ? "green" : "red")%>"> Your answer: <%=userAnswer %> </span>
		
		<br/>
		<%
		idx = idx + 1;
	%>
	<br/><hr/>
<% }
	// VERY IMPORTANT: log the stats!
	study.logStats("results.final");	
%>
<p>
<div style="text-align:center">
<% if (debug) { %>
	<a href="dumpStats.jsp">stats</a>,	<a href="dumpStats.jsp?csv=1">CSV</a>
	<p>
<% } 
%>
	<a href="finish">Click here</a> to finish the study.
</div>
</div>
</body>
</html>
