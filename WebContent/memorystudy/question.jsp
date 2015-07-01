<%@page language="java" contentType="text/html; charset=utf-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.ie.ie.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<%
JSPHelper.logRequest(request);

	Integer numQ = (Integer) session.getAttribute("numQuestions");
	if (numQ == null)
		numQ = HTMLUtils.getIntParam(request, "n", 40); //should be 40 by default
	
	Archive archive = JSPHelper.getArchive(session);
	if (archive == null) {
%>
<html>
<body>No archive in session. Please login again.
</body>
</html>
<%
	return;
	}
	Lexicon lex = (Lexicon) session.getAttribute("lexicon");
	if (lex == null)
	{
		lex = archive.getLexicon("default");
		session.setAttribute("lexicon", lex);	
	}
	
	MemoryStudy currentStudy = (MemoryStudy) session.getAttribute("study");
	
	String userAnswer = request.getParameter("answer");

	if (userAnswer == null) {
		// no answer => this is the first question
		if (currentStudy == null) 
		{
			// create a new study from the current archive, this is for debug only
			MemoryStudy.UserStats us = new MemoryStudy.UserStats("<unk>", "<unk>", "<unk>", "<unk>", "<unk>", "<unk>", "<unk>", request.getRemoteAddr().toString(), request.getHeader("User-Agent"));
			currentStudy = new MemoryStudy(us);
			currentStudy.generateQuestions(archive, (Collection<EmailDocument>) session.getAttribute("emailDocs"), lex, numQ);			
			session.setAttribute("study", currentStudy);
		}
		currentStudy.recordStartTime();
	} else {
		if (currentStudy == null) {
			// should have been init'ed in memorystudy login page
			// better thing to do here is to send redirect to a timed out page
			response.sendRedirect("/muse/error"); // remove details = later
			return;
		}

		// record response to previous q
		String userAnswerBeforeHint = request.getParameter("answerBeforeHint");
		boolean hintUsed = "true".equals(request.getParameter("hintUsed"));
		int certainty = HTMLUtils.getIntParam(request, "certainty", -1);
		long millis = (long) HTMLUtils.getIntParam(request, "millis", -1);
		int memoryType = HTMLUtils.getIntParam(request, "memoryType", -1);
		int recency = HTMLUtils.getIntParam(request, "recency", -1);
		
		currentStudy.enterAnswer(userAnswer, userAnswerBeforeHint, millis, hintUsed, certainty, memoryType, recency);
		currentStudy.iterateQuestion();
		boolean finished = currentStudy.checkForFinish();
		if (finished){
			session.setAttribute("study", currentStudy);
			response.sendRedirect("/muse/memorystudy/answerCheck?details="); // remove details = later
			return;
		}
	}
	
	MemoryQuestion questiontodisplay = currentStudy.returncurrentQuestion();
%>

<!DOCTYPE html>
<html>
<meta charset="utf-8">
<head>
<script src="../js/jquery/jquery.js"></script>
<script src="../js/muse.js"></script>
<link rel="stylesheet" href="css/tester.css" />
<link rel="icon" href="images/stanford-favicon.gif">
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Study</title>
</head>
<body>
	<div class="box">
	<div style="color:#777;font-size:12pt;float:right">
	   Question <%= currentStudy.getQuestionindex()%>/<%=currentStudy.getQuestions().size() %>
	</div>
	<div style="clear:both"></div>
		<br />
		<p>
			<script>
				
			</script>
		<form id="testinput" name="testinput" action="question"
			method="post">

			<div id="question" class="question">
				<%
					out.print(currentStudy.getQuestionindex() + ". ");
					out.println(Util.escapeHTML(questiontodisplay.getPreHintQuestion()));
				%>
			</div>
			<p>
			<div id="hint-question" style="display: none">
				<%
				out.print(currentStudy.getQuestionindex() + ". ");
					out.println(Util.escapeHTML(questiontodisplay.getPostHintQuestion()));
				%>
			</div>

			<br> <input type="hidden" name="hintUsed" id="hintUsed"
				value="false"> <input type="hidden" name="millis"
				id="millis" value="-1"> <input type="hidden"
				name="answerBeforeHint" id="answerBeforeHint" value="-1"> <input
				style="margin-left: 20%" type="text" size="40" id="answer" class="answer"
				name="answer" autofocus autocomplete="off"> <span
				id="hint-button" style="display: none"><button
					id="hintbutton" type="button" onclick="show_hint(); return false;"
					style="">Show Hint</button>
				<br /></span>
			<p style="margin-left: 20%" class="smaller">
			<span id="answerLength">
				[<%
				out.print(questiontodisplay.lengthDescr);
				int correctAnswerLengthWithoutSpaces = questiontodisplay.correctAnswer.replaceAll("\\s", "").length();
				%>]
			</span>
			</p>
			<script type="text/javascript">
				var correctAnswerLengthWithoutSpaces = <%=correctAnswerLengthWithoutSpaces%>;
			</script>

			<select style="margin-left: 20%" name="certainty"
				id="certainty">
				<option value="0">How sure are you?</option>
				<option value="4">I'm certain.</option>
				<option value="3">I'm fairly sure.</option>
				<option value="2">I'm unsure -- my answer may or may not be right.</option>
				<option value="1">I have no idea</option>
			</select> 
			<br/> 
			<select style="margin-left: 20%" name="memoryType" id="memoryType">
				<option value="0">What do you remember about this sentence?</option>
				<option value="4">I remember this specific message</option>
				<option value="3">I only recall the general context, not the message</option>
				<option value="2">I can infer the answer, but don't recall this context</option>
				<option value="1">I don't remember anything about it</option>
			</select>
			<br/> 
			
			<select style="margin-left:20%" name="recency" id="recency">
			<option value="-1">Approximately when do you think was this sentence written?</option>
			<%
			Date d = new Date();
			GregorianCalendar gc = new GregorianCalendar();
			gc.setTime(d);
			int month = gc.get(Calendar.MONTH);
			int year = gc.get(Calendar.YEAR);
			for (int i = 1; i <= 12; i++) {
				gc.set(year, month, 1);
				d = new java.util.Date(gc.getTimeInMillis());
				String option  = new java.text.SimpleDateFormat("MMMM").format(d) + " " + year;
				%> <option value="<%=i%>"><%=option%></option>
			<%
				month--;
				if (month < 0)
				{
					month = 11;
					year--;
				}
			} 
			%>
			<option value="0">I have no idea</option>
			
		</select>
			<p/>
			<button onclick="return handle_submit()" style="margin-left: 20%"
				type="submit" value="Submit">Submit</button>

			<script>
				function show_hint() {
					// copy answer to save the answer before the hint was typed
					$('#answerBeforeHint').val($('#answer').val());
					$('.question').text($('#hint-question').text()); // copy it over
					// remove hint button once its been shown
					$('#hint-button').fadeOut();
					$('#hintUsed').val('true');
				}

				$(function() {
					$("#hint-button").delay(15000).fadeIn();
				});

				function handle_submit(event) {
					// ensure options are filled out
					if ($('#memory').val() == 0 || $('#certainty').val() == 0 || $('#recency').val() == -1) {
						alert("Please select an option for each of the dropdown boxes.");
						return false;
					}

					// compute time from when the page was loaded till now
					var elapsed_time = new Date().getTime() - start_time;
					$('#millis').val(elapsed_time);

					return true; // this will proceed with the form submit
				}
				var start_time;
				$(document).ready(function() {
					start_time = new Date().getTime();
					$('#answer').keyup(function() {
						// check # of letters in answer
						var val = $('#answer').val();
						val = val.replace(/ /g, '');
						if (val.length == correctAnswerLengthWithoutSpaces)
							$('#answerLength').css('color', 'green');
						else
							$('#answerLength').css('color', 'red');
					});
				});
			</script>

		</form>
	</div>
</body>
</html>
<%JSPHelper.logRequestComplete(request);%>
