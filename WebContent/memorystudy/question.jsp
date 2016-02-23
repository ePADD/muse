<%@page language="java" contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<%@ page import="edu.stanford.muse.ner.model.NERModel" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.text.DateFormat" %>
<%

	Integer numQ = (Integer) session.getAttribute("numQuestions");
	if (numQ == null)
		numQ = HTMLUtils.getIntParam(request, "n", 40); //should be 40 by default

    Archive archive = null;
    String escapePassword = request.getParameter("escape");
    if (escapePassword != null && escapePassword.equals(System.getProperty ("escape.password"))) {
        archive = SimpleSessions.prepareAndLoadDefaultArchive(request);
    } else
 	    archive = JSPHelper.getArchive(session);

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
    session.setAttribute("debug", "true");

	if (userAnswer == null) {
		// no answer => this is the first question
		if (currentStudy == null) 
		{
            NERModel nerModel = (NERModel)session.getAttribute("ner");
			// create a new study from the current archive, this is for debug only
			MemoryStudy.UserStats us = new MemoryStudy.UserStats("<unk>", "<unk>", "<unk>", "<unk>", "<unk>", "<unk>", "<unk>", request.getRemoteAddr().toString(), request.getHeader("User-Agent"));
			currentStudy = new MemoryStudy(us);
			currentStudy.generateQuestions(archive, nerModel, (Collection<EmailDocument>) session.getAttribute("emailDocs"), lex, numQ, true);
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
		int memory = HTMLUtils.getIntParam(request, "memory", -1);
		Date recency = null;
        if(!"on".equals(request.getParameter("noTime"))) {
            Calendar cal = new GregorianCalendar();
            cal.set(Integer.parseInt(request.getParameter("timeYear")), Integer.parseInt(request.getParameter("timeMonth"))-1, Integer.parseInt(request.getParameter("timeDate")));
            recency = cal.getTime();
        }
        MemoryQuestion.RecallType recallType = null;
        Object recallObject = null;
        if(request.getParameter("fail")!=null){
            Integer failType = Integer.parseInt(request.getParameter("fail"));
            if(failType!=null){
                if(failType==0)
                    recallType = MemoryQuestion.RecallType.Nothing;
                else if(failType==1)
                    recallType = MemoryQuestion.RecallType.Context;
                else if(failType==2){
                    recallType = MemoryQuestion.RecallType.TipOfTongue;
                    recallObject = Integer.parseInt(request.getParameter("tipScore"));
                }else {
                    recallType = MemoryQuestion.RecallType.UnfairQuestion;
                    recallObject = request.getParameter("unfairReason");
                }
            }
        }
		
		currentStudy.enterAnswer(userAnswer, userAnswerBeforeHint, recallType, recallObject, millis, hintUsed, certainty, memory, recency);
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
    <div class="heading">
        <img title="Ashoka University" src="../images/ashoka-logo.png" width="100px" height="100px"/> <span style="float: right;font-size: 30px;color: black;">
        Question <%= currentStudy.getQuestionindex()%>/<%=currentStudy.getQuestions().size() %></span>
    </div>
	<div class="box">
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
                    String q = Util.escapeHTML(questiontodisplay.getPreHintQuestion());
                    out.println(q);
                %>
			</div>
			<p>
			<div id="hint-question" style="display: none">
				<%
				    out.print(currentStudy.getQuestionindex() + ". ");
					q = Util.escapeHTML(questiontodisplay.getPostHintQuestion());
				    out.println(q);
                %>
			</div>

            <datalist id="steplist">
                <option>1</option><option>2</option><option>3</option><option>4</option><option>5</option>
                <option>6</option><option>7</option><option>8</option><option>9</option><option>10</option>
            </datalist>
			<div style="margin-left: 20%">
                <input type="hidden" name="hintUsed" id="hintUsed"
				value="false"> <input type="hidden" name="millis"
				id="millis" value="-1"> <input type="hidden"
				name="answerBeforeHint" id="answerBeforeHint" value="-1">
                <input type="text" size="40" id="answer" class="answer"
				name="answer" autofocus autocomplete="off">
                <span id="hint-button" style="display: none">
                    <button id="hintbutton" type="button" onclick="show_hint(); return false;" style="">Show Hint</button>
				<br /></span>
			    <p class="smaller">
                <span id="answerLength">
                    [<%
                        out.print(questiontodisplay.lengthDescr);
                        //Should also be able to handle Dileep A. D
                        int correctAnswerLengthWithoutSpaces = questiontodisplay.correctAnswer.replaceAll("\\W", "").length();
                    %>]
                </span>
                </p>

                <span>OR &nbsp;&nbsp; Answer why you forgot:<br></span>
                <input id="fComplete" name="fail" value=0 type="radio"/>I forgot the email and events completely<br>
                <input id="fContext" name="fail" value=1 type="radio"/>I remember the email/surrounding events but not the email recipient<br>
                <span>
                    <input id="fTip" name="fail" type="radio" value=2 onclick="$('#tipRate').toggle()"/>I remember the person but their name is on the tip of my tongue
                    <span id="tipRate" style="margin-left:3%;display:none">
                        <br>
                        <span style="margin-left:3%">On a scale of 1 to 10, rate how close you are to having the name pop into mind&nbsp;<br>
                            <span style="margin-left:3%">1&nbsp; - I have no idea &nbsp;&nbsp;</span>&nbsp
                            <span style="margin-left:3%">10 - It's close!</span><br>
                            <span style="position:relative;left:30px">1</span><span style="position:relative;left:140px">5</span><span style="position:relative;left:280px">10</span><br>
                            <input style="padding-left:10px" type="range" min=1 max=10 name="tipScore" id="tipScore" value="5" step="1" data-step="steplist"/>
                        </span>
                        <br>
                    </span>
                </span>
                <br>
                <input id="unfair" name="fail" type="radio" value=3 onclick='$("#unfairReason").toggle()'/> Unfair question?
                <input type="text" placeholder="Please elaborate" size="40" style="display:none" id="unfairReason" name="unfairReason"/>
            </div>

			<script type="text/javascript">
				var correctAnswerLengthWithoutSpaces = <%=correctAnswerLengthWithoutSpaces%>;
			</script>

        <!--
        <div>On a scale of 1 to 10, how confident are you about your answer?
            <br>
            10 - I am Certain<br>
            5&nbsp  - I am not sure<br>
            1&nbsp  - I have no Idea<br>
            <span style="position:absolute;left:30px">1</span><span style="position:absolute;left:150px">5</span><span style="position:absolute;left:300px">10</span><br>
            <input name="certainty" id="certainty" type="range" min="1" max="10" step="1" value="5" list="steplist"/>
        </div>
        <br/>
        -->


        <div>How vividly do you remember writing this mail?
            <br>
            10 - I clearly remember writing this mail<br>
            6&nbsp - I remember the person<br>
            4&nbsp  - I recall the general context<br>
            1&nbsp  - I have no memory of the event<br>
            <span style="position:absolute;left:30px">1</span><span style="position:absolute;left:150px">5</span><span style="position:absolute;left:300px">10</span><br>
            <input name="memory" id="memory" type="range" min="1" max="10" step="1" value="5" list="steplist"/>
        </div>
        <br/>

        <div>
            Approximately when do you think was this sentence written?

        </div>
        <div>
		    <span id="time">
                <select style="margin-left:20%" name="timeDate" id="timeDate">
                    <option value="-1"></option>
                    <%
                        for(int d=1;d<=31;d++){
                    %><option value="<%=d%>"><%=d%></option><%
                    }
                %>
                </select>
                <select name="timeMonth" id="timeMonth">
                    <option value="-1"></option>
                    <%
                        Calendar cal = new GregorianCalendar();
                        DateFormat df = new SimpleDateFormat("MMM");
                        for(int m=1;m<=12;m++){
                            cal.set(Calendar.MONTH, m-1);
                    %><option value="<%=m%>"><%=df.format(cal.getTime())%></option><%
                    }
                %>
                </select>
                <select name="timeYear" id="timeYear">
                    <option value="-1"></option>
                    <option value="2015">2015</option>
                    <option value="2016">2016</option>
                </select>
                <br>
                <span style="margin-left:20%">Date&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span><span>Month&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span><span>Year</span>
            </span><br>
            <input type="checkbox" style="margin-left:20%" id="timeInfo" name="noTime" onclick='$("#time").toggle()'> I have no idea<br>
        </div>

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
                    //ensure answer or the reason is filled
                    if ($("#answer").val()=='' && !$("#fComplete")[0].checked && !$("#fContext")[0].checked && !($("#fTip")[0].checked && $("#tipScore").val() !== '') && !$("#unfair")[0].checked) {
                        alert("Please enter the answer or answer why you forgot.");
                        return false;
                    } else if($("#fTip")[0].checked && $("#tipScore").val() !== ''){
                        var tVal = parseInt($("#tipScore").val());
                        if(isNaN(tVal) || tVal<1 || tVal>10){
                            alert('Please enter a number in the range 1 to 10 for "how close you are to having the name pop into mind"');
                            return false;
                        }
                    } else if($("#unfair")[0].checked && $("#unfairReason").val() == ''){
                        alert("Please enter a reason why you think the question is unfair.")
                        return false;
                    }

                    //ensure questions are filled out
                    if ($('#memory').val()=='' || $('#certainty').val()=='' || (!$("#timeInfo")[0].checked && ($('#timeYear').val()==-1||$("#timeMonth").val()==-1||$("#timeYear")==-1))) {
                        alert("Please answer all the three questions about your answer.");
                        return false;
                    }
                    else{
                        var mVal = parseInt($("#memory").val());
                        var cVal = parseInt($("#certainty").val());
                        if(isNaN(cVal) || cVal<1 || cVal>10){
                            alert('Please enter a number in the range of 1 to 10 for "How confident are you about your answer?"');
                            return false;
                        }
                        if(isNaN(mVal) || mVal<1 || mVal>10){
                            alert('Please enter a number in the range of 1 to 10 for "How vividly do you remember writing this mail?"');
                            return false;
                        }
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
