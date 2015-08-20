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
Almost done! Now, we'll show you the answers that the computer marked as incorrect.
Please provide us a little more information about these answers. 
<p>

It's possible that these answers are actually correct, or reasonably close to being correct; for example, 
for a clue like "I went to _ _ _ _ _ _ _ last summer", it may be reasonable to enter the answer word "Germany" instead of "Hamburg", or to substitute
a nickname for a person's real name. In such cases, select the option: "My answer is essentially correct".

<p>

On the next page, you will be able to review all the questions.
<p>

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
			<option value="1">I really should have gotten this correct</option>
			<option value="2">The answer was on the "tip of my tongue"</option>
			<option value="3">My answer is essentially correct</option>
			<option value="4">This is an insignificant detail that I'm unlikely to have remembered</option>
			<option value="5">The answer is hard to guess... the clue sentence did not provide enough context</option>
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
