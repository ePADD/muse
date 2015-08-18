<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.Archive"%>
<%@page language="java" import="edu.stanford.muse.index.Indexer"%>
<%@page language="java" import="edu.stanford.muse.index.Indexer.IndexStats"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="icon" href="images/stanford-favicon.gif">
<title>Stats Page</title>
<style>
td { 
border-right: solid 1px rgba(0,0,0,0.2);
border-bottom: solid 1px rgba(0,0,0,0.2);
text-align:right;
padding:2px;
margin:0px;
}
body { font-family: Courier}
tr { margin:0px;}
table { 
border: solid 1px rgba(0,0,0,0.2);
text-align:right;
}
</style>
</head>
<body style="background:transparent">
<%
	boolean csv = request.getParameter("csv") != null;
	
	MemoryStudy study = (MemoryStudy) session.getAttribute("study");
	Archive archive = (Archive) session.getAttribute("archive");
	if (study == null) {
		out.println("Sorry, your session has timed out, please retry");
		return;
	}	
	String newline = "<br>";

    Indexer.IndexStats stats = study.archive.getIndexStats();
	if (!csv) {
	Pair<String, String> indexStats = Util.fieldsToHTMLTD(stats, true);
	Pair<String, String> addressBookStats = Util.fieldsToHTMLTD(study.archive.addressBook.getStats(), true);
	Pair<String, String> studyStats = Util.fieldsToHTMLTD(study.stats, true);
	Pair<String, String> archiveStats = Util.fieldsToHTMLTD(archive.stats, true);
	%>
	<table>
	<tr>
		<%=studyStats.getFirst() + indexStats.getFirst() + addressBookStats.getFirst() + archiveStats.getFirst()%>
	</tr>
	<tr>
		<%=studyStats.getSecond() + indexStats.getSecond() + addressBookStats.getSecond() + archiveStats.getSecond()%>
	</tr>			
	</table>
	<%
	} else {
		Pair<String, String> indexStats = Util.fieldsToCSV(stats, true);
		Pair<String, String> addressBookStats = Util.fieldsToCSV(study.archive.addressBook.getStats(), true);
		Pair<String, String> studyStats = Util.fieldsToCSV(study.stats, true);
		  Pair<String, String> archiveStats = Util.fieldsToCSV(archive.stats, true);
          out.println (studyStats.getFirst() + indexStats.getFirst() + addressBookStats.getFirst() + archiveStats.getFirst() + "<br/>");
          out.println (studyStats.getSecond() + indexStats.getSecond() + addressBookStats.getSecond() + archiveStats.getSecond() + "<br/>");
	}
%>	
	<p>
<%
	int idx = 1;
	if (!csv) { out.println("<table>"); }
	for (MemoryQuestion mq : study.getQuestions()) {

		Date clueDate = new Date(mq.clue.date);

		String correctAnswer = mq.getCorrectAnswer();
		String userAnswer = mq.getUserAnswer();
		if (userAnswer == null)
			userAnswer = "";
		correctAnswer = Util.canonicalizeSpaces(correctAnswer);
		userAnswer = Util.canonicalizeSpaces(userAnswer);
		if (!csv) {
			Pair<String, String> p = Util.fieldsToHTMLTD(mq.clue.clueStats, true);		
			Pair<String, String> p1 = Util.fieldsToHTMLTD(mq.stats, true);
			if (idx == 1) {
				out.println("<tr>" + p.getFirst()  + p1.getFirst() + "<td>correct answer</td><td>user answer</td><td>user answer before hint</td><td>clue</tr>");
			}
			out.println("<tr>" + p.getSecond()  + p1.getSecond() + "<td>" + mq.correctAnswer + "</td><td>" + mq.userAnswer + "</td><td>" + mq.userAnswerBeforeHint + "</td><td>" + mq.clue.clue + "</tr>");
		} else {
			Pair<String, String> p = Util.fieldsToCSV(mq.clue.clueStats, true);		
			Pair<String, String> p1 = Util.fieldsToCSV(mq.stats, true);
			if (idx == 1)
				out.println(p.getFirst() + p1.getFirst() + ",correct answer,user answer,user answer before hint,clue<br/>");
			out.println(p.getSecond() + p1.getSecond() + "," + mq.correctAnswer + "," + mq.userAnswer + "," + mq.userAnswerBeforeHint + "," + mq.clue.clue.replaceAll(",", "") + ",<br/>");			
		}
		idx = idx + 1;
	} 
	if (!csv) { out.println("</table>"); }

	// For each contact, we should print out the last date of communication with them, going backwards in time (visually separated by month)
	// Similarly, for each entity (all types, as well as broken down by P/L/O

	%>
<p>
</body>
</html>