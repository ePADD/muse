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

    Archive archive;
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
                numQ = HTMLUtils.getIntParam(request, "n", 36);

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
        int recency = HTMLUtils.getIntParam(request, "recency", -1);

        boolean userGaveUp = "Skip".equals(request.getParameter("submitType"));
        int recallType = -1, recallTypeBeforeHint = -1;
        if (request.getParameter("recall-type") != null) {
            try {
                recallType = HTMLUtils.getIntParam(request, "recall-type", -1);
                recallTypeBeforeHint = HTMLUtils.getIntParam(request, "recallTypeBeforeHint", -1);
            } catch (Exception e) {
                Util.print_exception(e, JSPHelper.log);
            }
        }

        currentStudy.enterAnswer(userAnswer, userAnswerBeforeHint, recallTypeBeforeHint, recallType, millis, hintUsed, certainty, memory, recency, userGaveUp);
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
            q = "<p>" + Util.escapeHTML(questiontodisplay.clue.clue) + "<p>Email recipient name: " + questiontodisplay.getBlanksWithHintForCorrespondentTest().replaceAll(" ", "&nbsp;") + " <span class=\"hint-letter\">Hint active</span></p>";
            out.println(q);
        %>
    </div>

    <input type="hidden" name="hintUsed" id="hintUsed" value="false">
    <input type="hidden" name="millis" id="millis" value="-1">
    <input type="hidden" name="answerBeforeHint" id="answerBeforeHint" value="-1">
    <input type="hidden" name="recallTypeBeforeHint" id="recallTypeBeforeHint" value="-1">

    <br/>

    <div style="margin-left: 5%">
        <div>
            <i class="fa fa-caret-right"></i> Type here:
            <input spellcheck="false" type="text" size="40" id="answer" class="answer" name="answer" autofocus autocomplete="off"/>
            <div style="display:inline">
                <button style="display:none; margin-left:50px;" type="button" id="hint-button">Hint</button>
            </div>

            <br/>
            <span id="answerLength" style="margin-left:100px">
                    [<%
                out.print(questiontodisplay.lengthDescr + "]");
                //Should also be able to handle Dileep A. D
                int correctAnswerLengthWithoutSpaces = questiontodisplay.correctAnswer.replaceAll("\\W", "").length();
            %>
                </span>

            <p>
                Tell us about this recollection: <br/>
                <input name="recall-type" type="radio" value="1"/>The name was easy to recall<br/>
                <input name="recall-type" type="radio" value="2"/>I got the name after a while<br/>
                <input name="recall-type" type="radio" value="3"/>The name is at the tip of my tongue!<br/>
                <input name="recall-type" type="radio" value="4"/>I know the person, not the name<br/>
                <input name="recall-type" type="radio" value="5"/>I don't know<br/>
                <!--
                <input id="fTip" name="fail" type="radio" value=2 onclick="show_hint()"/>I remember the person, and their name is on the tip of my tongue. Give me a hint.</br/>
                <input id="fContext" name="fail" value=1 type="radio" onclick="show_hint()"/>I remember the surrounding events but not the recipient. Give me a hint<br>
                <input id="fComplete" name="fail" value=0 type="radio" onclick="show_hint()"/>I forgot the email completely, give me a hint<br>
                -->
        </div>

        <script type="text/javascript">
            var correctAnswerLengthWithoutSpaces = <%=correctAnswerLengthWithoutSpaces%>;
        </script>

        <div class="vividness">
            <p>
                <i class="fa fa-caret-right"></i> How vividly do you remember this specific conversation?
                <br>
                (1: no idea; 5: fair idea; 10:strong memory)<br/>
            <div style="line-height:0.5em">
            <br/>
            <br/>
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

            <div style="position:relative;line-height:0.5em">
                <div style="font-size: small; position:relative;left:-33px; top:-30px;max-width:85px;max-height:94px;transform:rotate(270deg);">Not set</div>
                <span style="font-size: small; position:relative;left:40px">1</span>
                <span style="font-size: small; position:relative;left:162px">5</span>
                <span style="font-size: small; position:absolute;left:340px">10</span><br>
                <input name="memory" id="memory" type="range" min="0" max="10" step="1" value="0" list="steplist" oninput="outputUpdate(value)"/>
                <output style="position:relative;left:40px;top:-10px;" for="memory" id="memory-amount">Not set</output>
            </div>
                <script>
                    function outputUpdate(v) {
                        document.querySelector('#memory-amount').value = (v > 0) ? v : "Not set";
                    }
                </script>
            </div> <!-- .vividness -->

            <br/>

            <div class="when">
                <i class="fa fa-caret-right"></i> Approximately when do you think was this sentence written?
                <br/>
                <select name="recency" id="recency">
                    <option value="-2">Answer...</option>

                    <%
                        Date d = new Date();
                        GregorianCalendar gc = new GregorianCalendar();
                        gc.setTime(d);
                        int month = gc.get(Calendar.MONTH);
                        int year = gc.get(Calendar.YEAR);
                        for (int i = 0; i <= MemoryStudy.N_INTERVALS; i++) {
                            gc.set(year, month, 1);
                            d = new java.util.Date(gc.getTimeInMillis());
                            String option = new java.text.SimpleDateFormat("MMMM").format(d) + " " + year;
                    %>
                    <option value="<%=i%>"><%=option%>
                    </option>
                    <%
                            month--;
                            if (month < 0) {
                                month = 11;
                                year--;
                            }
                        }
                    %>
                    <option value="-1">I have no idea...</option>

                </select>

                <br>
            </div> <!-- .when -->
        </div>

        <p/>
        <input type="hidden" name="submitType" value="submit"/> <!-- will be submit or giveup -->
        <button class="submitButton" style="margin-left: 20%;display:inline;" type="submit" value="Submit">Submit</button>
        <button class="submitButton" style="margin-left: 20%;display:inline;" type="submit" value="Skip">Skip</button>

        <script>
            function show_hint() {
                // copy answer to save the answer before the hint was typed
                if ($('#nohint-question').is(':visible')) { /// only update this as long as nohint-q is visible
                    $('#recallTypeBeforeHint').val($('input[name="recall-type"]:checked').val());
                }

                $('#answerBeforeHint').val($('#answer').val());
                $('#nohint-question').hide();
                $('#hint-question').show(); // copy it over
                // remove hint button once its been shown
                $('#hint-button').fadeOut();
                $('#hintUsed').val('true');
            }

            /*
            setTimeout(function () {
                $("#hint-button").fadeIn().css('display', 'inline');
            }, 15000);

            $('#hint-button').click(show_hint);
            */

            $('input[name="recall-type"]').change(show_hint);

            function handle_submit(event) {
                var $target = $(event.target);
                var button_text = $target.text(); // text on the button that was pressed

                //ensure questions are filled out
                if (button_text != 'Skip') {
                    if ($("#recency").val() == -2) {
                        alert("Please answer the question about when the sentence was written.");
                        event.preventDefault();
                        event.stopPropagation();
                        return false;
                    }
                    if ($('#memory').val() == 0) {
                        alert("You did not answer the question about how vividly you remember the conversation. Please select a number between 1 and 10.");
                        event.preventDefault();
                        event.stopPropagation();
                        return false;
                    }
                    if (!$('input[name="recall-type"]').is(':checked')) {
                        alert('Please tell us about your recollection.');
                        event.preventDefault();
                        event.stopPropagation();
                        return false;
                    }
                }
                else {
                    $('#userGaveUp').val(1);
                    var any_hint_taken = $('#fTip').is(':checked') || $('#fContext').is(':checked') || $('#fComplete').is(':checked');
                    if (answer.length == 0 && !any_hint_taken) {
                        alert("Try to use one of the hint options before giving up.");
                        event.preventDefault();
                        event.stopPropagation();
                        return false;
                    }
                }

                // compute time from when the page was loaded till now
                var elapsed_time = new Date().getTime() - start_time;
                $('#millis').val(elapsed_time);

                // set up the submitType hidden field to track whether the skip button was pressed or submit
                $('input[name="submitType"]').val(button_text);

                return true; // this will proceed with the form submit
            }

            $('.submitButton').click(handle_submit);

            var start_time;
            $(document).ready(function () {
                start_time = new Date().getTime();
                /* disabling feedback on #letters
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
                */
            });

            $('#answer').focus();
        </script>

        </form>
    </div>
</div>
</body>
</html>
