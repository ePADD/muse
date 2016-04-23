<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<%
int nWrongAnswers = 0;
MemoryStudy study = (MemoryStudy) session.getAttribute("study");

for (MemoryQuestion mq : study.getQuestions())
	if (!mq.isUserAnswerCorrect())
		nWrongAnswers++;
if (nWrongAnswers == 0) {
	response.sendRedirect("/muse/memorystudy/answers.jsp");
	return;
}
	
%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<link rel="icon" href="../images/ashoka-favicon.gif">
	<link rel="stylesheet" href="../css/fonts.css"/>
	<link rel="stylesheet" href="css/memory.css"/>
<script src="../js/jquery/jquery.js"></script>
<title>Check answers</title>
</head>
<body>
<div class="box">
	<img style="position:absolute;top:5px;width:50px" title="Ashoka University" src="../images/ashoka-logo.png"/>
	<h1 style="text-align:center;font-weight:normal;font-variant:normal;text-transform:none;font-family:Dancing Script, cursive">Cognitive Experiments with Life-Logs</h1>
	<hr style="color:rgba(0,0,0,0.2);background-color:rgba(0,0,0,0.2);"/>

	<p>
	Almost done! We would now like some more information on the answers that the computer has marked as incorrect.
	It is possible that your answer is really correct. For example, you may have used a nickname, or only the first or last name, or made a typo.
	In such cases, please choose the option &quot;My answer is essentially correct&quot;.
</p>

<form action = "answers?details=1" method="post">

<%
int idx = 0;
for (MemoryQuestion mq : study.getQuestions()) {
	idx++;
	if (mq.isUserAnswerCorrect())
		continue;	
	%>
	<div class="question">
	<%=idx%>. <%=mq.getClueToShow().clue%>
	</div>
	<br/>Answer: <%=mq.getCorrectAnswer()%>
	<span style="color:red"> Your answer: <%=((Util.nullOrEmpty(mq.userAnswer))?"&lt;No answer&gt;":mq.userAnswer)%> </span>
		<p>
			<select class="wrongAnswerOption" name="wrongAnswerOption<%=idx%>" id="<%=idx%>">
			<option value="0">About this answer...</option>
			<option value="1">I feel like I should have remembered this name</option>
			<option value="2">My answer is essentially correct</option> <!-- don't change this! calculating # correct answers depends on this being #2 -->
			<option value="3">I knew something associated with this person, but didn't know their name</option>
			<option value="4">The clue was too vague -- I would never have been able to guess the answer</option>
		</select>
	<br/><hr/>
<% } %>
<br/>
<button onclick="return handle_submit()" style="margin-left:35%" type="submit" value="Submit">Submit</button>
</form>
<p>
</div>
<script>
function handle_submit() {
	$wrongAnswerDetails = $('.wrongAnswerOption');
	for (var i = 0; i < $wrongAnswerDetails.length; i++) {
		var $select = $($wrongAnswerDetails[i]);		
		if ($select.val() == 0) {
			alert ("Please select an option for Question #" + $select.attr('id'));
			return false;
		}
	}
	return true;
}
</script>
</body>
</html>
