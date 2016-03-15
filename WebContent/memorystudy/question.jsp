<%@page language="java" contentType="text/html; charset=UTF-8" %>
<%@page trimDirectiveWhitespaces="true" %>
<%@page language="java" import="edu.stanford.muse.index.Archive" %>
<%@page language="java" import="edu.stanford.muse.index.EmailDocument" %>
<%@page language="java" import="edu.stanford.muse.index.Lexicon" %>
<%@page language="java" import="edu.stanford.muse.memory.MemoryQuestion" %>
<%@page language="java" import="edu.stanford.muse.memory.MemoryStudy" %>
<%@page language="java" import="edu.stanford.muse.ner.model.NERModel" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.webapp.HTMLUtils" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.webapp.SimpleSessions" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.GregorianCalendar" %>
<%

    Archive archive = null;
    String escapePassword = request.getParameter("escape");
    if (escapePassword != null && escapePassword.equals(System.getProperty("escape.password"))) {
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
    if (lex == null) {
        lex = archive.getLexicon("default");
        session.setAttribute("lexicon", lex);
    }

    MemoryStudy currentStudy = (MemoryStudy) session.getAttribute("study");

    String userAnswer = request.getParameter("answer");
    session.setAttribute("debug", "true");

    if (userAnswer == null) {
        // no answer => this is the first question
        if (currentStudy == null || request.getParameter("newStudy") != null) {
            NERModel nerModel = (NERModel) session.getAttribute("ner");
            // create a new study from the current archive, this is for debug only
            MemoryStudy.UserStats us = new MemoryStudy.UserStats("<unk>", "<unk>", "<unk>", "<unk>", "<unk>", "<unk>", "<unk>", request.getRemoteAddr().toString(), request.getHeader("User-Agent"));
            currentStudy = new MemoryStudy(us);

            Integer numQ = (Integer) session.getAttribute("numQuestions");
            if (numQ == null)
                numQ = HTMLUtils.getIntParam(request, "n", 1);

            currentStudy.generatePersonNameQuestions(archive, nerModel, (Collection<EmailDocument>) session.getAttribute("emailDocs"), lex, numQ);
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
        boolean onlyMonthAndYearGuessed = false;
        if (!"on".equals(request.getParameter("noTime"))) {
            Calendar cal = new GregorianCalendar();
            try {
                int dd = Integer.parseInt(request.getParameter("timeDate"));
                if (dd == -1) {
                    dd = 15; // just pick a midpoint
                    onlyMonthAndYearGuessed = true;
                }
                cal.set(Integer.parseInt(request.getParameter("timeYear")), Integer.parseInt(request.getParameter("timeMonth")) - 1, dd);
            } catch (Exception e) {
                Util.print_exception(e, JSPHelper.log);
            }
            recency = cal.getTime();
        }
        MemoryQuestion.RecallType recallType = null;
        Object recallObject = null;
        if (request.getParameter("fail") != null) {
            Integer failType = null;
            try {
                failType = Integer.parseInt(request.getParameter("fail"));
            } catch (Exception e) {
                Util.print_exception(e, JSPHelper.log);
            }

            if (failType != null) {
                if (failType == 0)
                    recallType = MemoryQuestion.RecallType.Nothing;
                else if (failType == 1)
                    recallType = MemoryQuestion.RecallType.Context;
                else if (failType == 2) {
                    recallType = MemoryQuestion.RecallType.TipOfTongue;
                } else {
                    recallType = MemoryQuestion.RecallType.UnfairQuestion;
                    recallObject = request.getParameter("unfairReason");
                }
            }
        }

        currentStudy.enterAnswer(userAnswer, userAnswerBeforeHint, recallType, recallObject, millis, hintUsed, certainty, memory, recency, onlyMonthAndYearGuessed);
        currentStudy.iterateQuestion();
        boolean finished = currentStudy.checkForFinish();
        if (finished) {
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
    <link href="../css/fonts/font-awesome/css/font-awesome-4.3.min.css" rel="stylesheet">
    <link rel="stylesheet" href="css/memory.css"/>
    <link rel="icon" href="images/ashoka-favicon.gif">

    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Research Study on Memory</title>
</head>
<body>
<div class="box">
    <img title="Ashoka University" src="../images/ashoka-logo.png" style="width:50px"/>
        <span style="float: right;font-size: 30px;color: black;">
            Question <%= currentStudy.getQuestionindex()%>/<%=currentStudy.getQuestions().size() %>
        </span>

    <div style="clear:both"></div>

    <p>
        <form id="testinput" name="testinput" action="question" method="post">

            <div id="nohint-question" class="question">
                <%
                    String q = "<p>" + Util.escapeHTML(questiontodisplay.clue.clue) + "<p>Email recipient name: " + Util.escapeHTML(questiontodisplay.getBlanksWithNoHintForCorrespondentTest()).replaceAll(" ", "&nbsp;") + "</p>";
                    out.println(q);
                %>
            </div>
    <p>
    <div id="hint-question" class="question" style="display: none">
        <%
            q = "<p>" + Util.escapeHTML(questiontodisplay.clue.clue) + "<p>Email recipient name: " + Util.escapeHTML(questiontodisplay.getBlanksWithHintForCorrespondentTest()).replaceAll(" ", "&nbsp;") + "</p>";
            out.println(q);
        %>
    </div>

    <input type="hidden" name="hintUsed" id="hintUsed" value="false">
    <input type="hidden" name="millis" id="millis" value="-1">
    <input type="hidden" name="answerBeforeHint" id="answerBeforeHint" value="-1">
    <br/>

    <div style="margin-left: 5%">
        <div>
            <i class="fa fa-caret-right"></i> Type here:
            <input spellcheck="false" type="text" size="40" id="answer" class="answer" name="answer" autofocus autocomplete="off">
                <span id="answerLength">
                    [<%
                    out.print(questiontodisplay.lengthDescr);
                    //Should also be able to handle Dileep A. D
                    int correctAnswerLengthWithoutSpaces = questiontodisplay.correctAnswer.replaceAll("\\W", "").length();
                %>
                    <span id="nLettersCheck" style="color:green; display:none"> ✔</span>]
                </span>
            <p>
                <span>Or choose one of the following:</span><br/>
                <input id="fTip" name="fail" type="radio" value=2 onclick="show_hint()"/>I remember the person, and their name is on the tip of my tongue. Give me a hint.</br/>
                <input id="fContext" name="fail" value=1 type="radio" onclick="show_hint()"/>I remember the surrounding events but not the recipient. Give me a hint<br>
                <input id="fComplete" name="fail" value=0 type="radio" onclick="show_hint()"/>I forgot the email completely, give me a hint<br>
        </div>

        <script type="text/javascript">
            var correctAnswerLengthWithoutSpaces = <%=correctAnswerLengthWithoutSpaces%>;
        </script>

        <div class="vividness">
            <p>
                <i class="fa fa-caret-right"></i> How vividly do you remember writing this email?
                <br>
                (1: no idea; 5: fair idea; 10:strong memory)<br/>
            <div style="line-height:0.5em">
                <span style="font-size: small; position:relative;left:0px">1</span>
                <span style="font-size: small; position:relative;left:120px">5</span>
                <span style="font-size: small; position:absolute;left:340px">10</span><br>
                <!--For tick marks-->
                <datalist id="steplist">
                    <option>1</option>
                    <option>2</option>
                    <option>3</option>
                    <option>4</option>
                    <option>5</option>
                    <option>6</option>
                    <option>7</option>
                    <option>8</option>
                    <option>9</option>
                    <option>10</option>
                </datalist>

                <input name="memory" id="memory" type="range" min="1" max="10" step="1" value="5" list="steplist" oninput="outputUpdate(value)"/>
                <output style="position:relative;left:40px;top:-10px;" for="memory" id="memory-amount">5</output>
                <script>
                    function outputUpdate(v) {
                        document.querySelector('#memory-amount').value = v;
                    }
                </script>
            </div> <!-- .vividness -->

            <br/>

            <div class="when">
                <i class="fa fa-caret-right"></i> Approximately when do you think was this sentence written?
                <br/>
                    <span id="time">
                    <select style="margin-left:10%" name="timeDate" id="timeDate">
                        <option value="-1"></option>
                        <%
                            for (int d = 1; d <= 31; d++) {
                        %>
                        <option value="<%=d%>"><%=d%>
                        </option>
                        <%
                            }
                        %>
                    </select>

                    <select name="timeMonth" id="timeMonth">
                        <option value="-1"></option>
                        <%
                            Calendar cal = new GregorianCalendar();
                            DateFormat df = new SimpleDateFormat("MMM");
                            for (int m = 1; m <= 12; m++) {
                                cal.set(Calendar.MONTH, m - 1);
                        %>
                        <option value="<%=m%>"><%=df.format(cal.getTime())%>
                        </option>
                        <%
                            }
                        %>
                    </select>
                    <select name="timeYear" id="timeYear">
                        <option value="-1"></option>
                        <option value="2015">2015</option>
                        <option value="2016">2016</option>
                    </select>
                    <br>
                    <span style="margin-left:10%">Date&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span><span>Month&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span><span>Year</span>
                    </span>
                <br>
                <input type="checkbox" style="margin-left:10%" id="timeInfo" name="noTime"> I have no idea<br>
            </div> <!-- .when -->
        </div>

        <p/>
        <input type="hidden" name="submitType" value="submit"/> <!-- will be submit or giveup -->
        <button class="submitButton" style="margin-left: 20%;display:inline;" type="submit" value="Submit">Submit</button>
        <button class="submitButton" style="margin-left: 20%;display:inline;" type="submit" value="GiveUp">Give up</button>

        <script>
            function show_hint() {
                // copy answer to save the answer before the hint was typed
                $('#answerBeforeHint').val($('#answer').val());
                $('#nohint-question').hide();
                $('#hint-question').show(); // copy it over
                // remove hint button once its been shown
                $('#hint-button').fadeOut();
                $('#hintUsed').val('true');
            }

            $(function () {
                $("#hint-button").delay(15000).fadeIn();
            });

            function handle_submit(event) {
                var $target = $(event.target);
                var button_text = $target.text(); // text on the button that was pressed
                //ensure answer or the reason is filled
                /*
                 if ($("#answer").val()=='' && !$("#fComplete")[0].checked && !$("#fContext")[0].checked && !$("#unfair")[0].checked) {
                 alert("Please enter the answer or answer why you forgot.");
                 event.preventDefault();
                 event.stopPropagation();
                 return false;
                 } */
                /* else if($("#fTip")[0].checked && $("#tipScore").val() !== ''){
                 var tVal = parseInt($("#tipScore").val());
                 if(isNaN(tVal) || tVal<1 || tVal>10){
                 alert('Please enter a number in the range 1 to 10 for "how close you are to having the name pop into mind"');
                 event.preventDefault();
                 event.stopPropagation();
                 return false;
                 }

                 } else if($("#unfair")[0].checked && $("#unfairReason").val() == ''){
                 alert("Please enter a reason why you think the question is unfair.")
                 event.preventDefault();
                 event.stopPropagation();
                 return false;
                 } */

                //ensure questions are filled out
                if (button_text != 'Give up') {
                    if (!$("#timeInfo")[0].checked && ($('#timeYear').val() == -1 || $("#timeMonth").val() == -1)) {
                        alert("Please enter the month and the year.");
                        event.preventDefault();
                        event.stopPropagation();
                        return false;
                    }
                    else {
                        var answer = $('#answer').val();
                        var any_hint_taken = $('#fTip').is(':checked') || $('#fContext').is(':checked') || $('#fComplete').is(':checked');
                        if (answer.length == 0 && !any_hint_taken) {
                            alert("Try to use one of the hint options before clicking submit.");
                            event.preventDefault();
                            event.stopPropagation();
                            return false;
                        }
                    }
                }

                // compute time from when the page was loaded till now
                var elapsed_time = new Date().getTime() - start_time;
                $('#millis').val(elapsed_time);

                // set up the submitType hidden field to track whether the give up button was pressed or submit
                LOG(button_text);
                $('input[name="submitType"]').val(button_text);

                return true; // this will proceed with the form submit
            }

            $('.submitButton').click(handle_submit);

            var start_time;
            $(document).ready(function () {
                start_time = new Date().getTime();
                $('#answer').keyup(function () {
                    // check # of letters in answer
                    var val = $('#answer').val();
                    val = val.replace(/ /g, '');
                    if (val.length == correctAnswerLengthWithoutSpaces) {
                        $('#answerLength').css('color', '#05C105');
//                            $('#nLettersCheck').show();
                    }
                    else {
                        $('#answerLength').css('color', 'red');
                        $('#nLettersCheck').hide();
                    }
                });
            });

            $('#answer').focus();
        </script>

        </form>
    </div>
</div>
</body>
</html>
