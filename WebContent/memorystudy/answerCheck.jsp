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
<link rel="icon" href="images/stanford-favicon.gif">
<link rel="stylesheet" href="css/tester.css"/>
<script src="../js/jquery/jquery.js"></script>
<title>Check answers</title>
</head>
<body>
<div class="box">
<p>
	Almost done! We would now like some more information on the answers that the computer has marked as incorrect.
	It is possible that your answer is really correct. For example, you may have used a nickname, or only the first or last name, or made a typo.
	In such cases, please choose the option &quot;My answer is essentially correct&quot;.
</p>

<form action = "answers.jsp?details=1" method="post">

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
			<option value="2">My answer is essentially correct</option>
			<option value="3">I'm unlikely to have remembered this person</option>
			<option value="4">the clue was not good enough to trigger the memory of this email</option>
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
