<%@page language="java" contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <script src="../js/jquery/jquery.js"></script>
    <script src="../js/muse.js"></script>
    <link href="../css/fonts/font-awesome/css/font-awesome.css" rel="stylesheet">
    <link href="../css/fonts/font-awesome/css/font-awesome-4.3.min.css" rel="stylesheet">

    <link href="intro.js-0.5.0/example/assets/css/demo.css" rel="stylesheet">
    <link href="intro.js-0.5.0/introjs.css" rel="stylesheet">

    <link rel="stylesheet" href="../css/fonts.css"/>
    <link rel="stylesheet" href="css/memory.css"/>
    <link rel="icon" href="images/ashoka-favicon.gif">

    <title>Example</title>
<script>
$(function() {
	  $(".hintwait").delay(10000).fadeIn(); /*20,000 milliseconds = 20 seconds. Currently set at 10,000 milliseconds = 10 seconds.*/
});
</script>
</head>
<div>

<script type="text/javascript" src="intro.js-0.5.0/intro.js"></script>

<p>

<div class="box">
    <img title="Ashoka University" src="../images/ashoka-logo.png" style="width:50px"/>
        <span style="float: right;font-size: 30px;color: white;transform:rotate(30deg);background-color:#a70e13;padding:5px;position:relative;top:20px;left:10px">
            Example
        </span>
    <br/>
    <br/>
    <br/>

    <div style="clear:both"></div>

<br/>
<div class="container2">
<form id="testinput" name = "testinput" action = "examplehandler" method="post">
		<div id="containerdiv" >
            <span data-step = "1" data-intro = "You will see a sentence that was taken from your email (this one wasn't, it's just an example). Think about the person you sent this email to. Remember, the clue will be from an email to a single person, not a group.">
                <div id="nohint-question" class="question">
                    I've always wondered why you sat on a wall in the first place, especially if you are an egg. Anyway, get well soon. <br>
                    <p>     Email recipient name: _ _ _ _ _ _ &nbsp;&nbsp; _ _ _ _ _ _</p>
                </div>
                <div id="hint-question" class="question" style="display: none">
                    I've always wondered why you sat on a wall in the first place, especially if you are an egg. Anyway, get well soon. <br>
                    <p>     Email recipient name: H _ _ _ _ _ &nbsp;&nbsp; D _ _ _ _ _</p>
                </div>

            </span> </p>
            </div>
		</div>
		<p/>

            <div style="margin-left: 5%">
                <i class="fa fa-caret-right"></i> Type here:
                <input spellcheck="false" autofocus autocomplete="off" class="answer" id="answer" style="border:solid 2px #082041; background: #082041" data-step="4" data-step="4" data-intro="Type in your answer in this box. The answer is not case sensitive, and spaces do not matter. If you haven't figured it out yet, the correct answer in this example is 'Humpty Dumpty'." type="text" size="40" name="answer">
                <span class="smaller"><span id="answerLength" data-step = "2" data-intro="The number of words and letters in the answer. This description will turn green when the number of letters you have entered in the answer box is correct.">
                    [2 words: 6 letters, 6 letters<span id="nLettersCheck" style="color:green; display:none"> ✔</span>]
                </span>

                </p>
                <span data-step="5" data-intro="If you can't remember the answer, choose one of the options below and the initial letters of each word in the answer will be provided as a hint. Try to recall the name using this hint.">
                    Or choose one of the following:
                </span><br/>
                <span data-step="6" name="fail" data-intro="When you can't recall the answer, but you feel that it is about to come to you, then you are in a tip-of-the-tongue state. For example, you might know whom you sent this email to and feel you know who the person is, but cannot recall their name right now. It may feel like the name is ready to pop into your mind at any moment. If this happens, choose this option.">
                    <input id="fTip" name="fail" type="radio" onclick="show_hint()"/>I remember the person and their name is on the tip of my tongue. Give me a hint.
                </span><br/>
                <input id="fContext" name="fail" value=1 type="radio" onclick="show_hint()"/>I remember the surrounding events but not the recipient. Give me a hint<br>
                <input id="fComplete" name="fail" value=0 type="radio" onclick="show_hint()"/>I forgot the email completely. Give me a hint<br>
                    <!--
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
                    -->
                </span>
                <!--
                <input id="unfair" name="fail" type="radio" onclick='$("#unfairReason").toggle()'/> Unfair question?
                <input type="text" placeholder="Please elaborate" size="40" style="display:none" id="unfairReason"/>
                -->

            <!--
            <div>On a scale of 1 to 10, how confident are you about your answer?
                <br>
                10 - I am Certain<br>
                5&nbsp  - I am not sure<br>
                1&nbsp  - I have no Idea<br>
                <span style="position:absolute;left:30px">1</span><span style="position:absolute;left:150px">5</span><span style="position:absolute;left:300px">10</span><br>
                <input name="certainty" id="certainty" type="range" min="1" max="10" step="1" value="5" list="steplist"/>
            </div>
            -->
		    <br/>

            <div data-step = "7" data-intro = "On a scale of 1 to 10, rate how strongly you remember writing this email by dragging the slider to the left or right.">
                <p>
                    <i class="fa fa-caret-right"></i> How vividly do you remember writing this email?
                <br>
                (1: no idea; 5: fair idea; 10:strong memory)
                <p>

                <div style="position:relative;line-height:0.5em">
                    <span style="font-size: small; position:relative;left:0px">1</span>
                    <span style="font-size: small; position:relative;left:120px">5</span>
                    <span style="font-size: small; position:absolute;left:300px">10</span><br>
                    <input name="memory" id="memory" type="range" min="1" max="10" step="1" value="5" list="steplist" oninput="outputUpdate(value)"/>
                    <output style="position:relative;left:40px;top:-10px;" for="memory" id="memory-amount">5</output>
                </div>
            </div>

            <br/>

            <script>
                function outputUpdate(v) {
                    document.querySelector('#memory-amount').value = v;
                }
            </script>

            <div data-step = "8" data-intro = "Try to recall when you wrote the email. Entering the specific date is optional, but the month and year are required. If you cannot guess the time at all, choose &quot;I have no idea&quot;.">

                <i class="fa fa-caret-right"></i> Approximately when do you think was this sentence written?
                <div>
                <span id="time">
                    <select style="margin-left:10%" name="timeDate" id="timeDate">
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
                    <span style="margin-left:10%">Date&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span><span>Month&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span><span>Year</span>
                </span><br>
                <input type="checkbox" style="margin-left:10%" id="timeInfo"> I have no idea<br>
		    </div>

        <!--For tick marks-->
        <datalist id="steplist">
            <option>1</option><option>2</option><option>3</option><option>4</option><option>5</option>
            <option>6</option><option>7</option><option>8</option><option>9</option><option>10</option>
        </datalist>
			
		<br/>
            </div>
    </div>

    <div data-step="9" data-intro="Once you've answered all parts of the question, click on the Submit button. If you do not wish to attempt this question at all, you can click the Give up button at any point and move on to the next question">
        <button class="submitButton" style="margin-left: 20%;display:inline;" type="submit" value="Submit">Submit</button>
        <button class="submitButton" style="margin-left: 20%;display:inline;" type="submit" value="GiveUp">Give up</button>
    </div>
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

            function replacehint(){
			var hint = "H _ _ _ _ _";
			var spacetoreplace = "_ _ _ _ _ _"
			var text = $('#question').html();
			text = text.replace(spacetoreplace, hint);
			$('#question').html(text);
			return false;
		}
		
		function handle_submit(event) {
            var $target = $(event.target);
            var button_text = $target.text(); // text on the button that was pressed
            if (($("#answer").val() !== '' && 'humptydumpty' !== $('#answer').val().toLowerCase().replaceAll(" ", ""))
                || (button_text == 'Give up')) {
                    alert("Uh, oh. The correct answer is Humpty Dumpty.");
                    return false;
                }
            /*
            if ($('#memory').val()=='' || (!$("#timeInfo")[0].checked && ($('#timeYear').val()==-1||$("#timeMonth").val()==-1))) {
                alert("Please answer all the questions.");
                return false;
            }
            */

			return true;				
		}

        $('.submitButton').click(handle_submit);

        $('#answer').keyup(function() {
			var correctAnswerLengthWithoutSpaces = 'humptydumpty'.length;
			// check # of letters in answer
			var val = $('#answer').val();
			val = val.replace(/ /g, '');
            if (val.length == correctAnswerLengthWithoutSpaces) {
                $('#answerLength').css('color', '#05C105');
//                $('#nLettersCheck').show();

            }
            else {
                $('#answerLength').css('color', 'red');
                $('#nLettersCheck').hide();
            }
		});
        $('#answer').focus();

        </script>

		</form>
	</span>
	</div>
	<script type="text/javascript">
	alert('Here is an example of the kind of questions you will see. Please follow the tour by clicking on the Next button. You can also use the right and left arrow keys.');
	introJs().start();
	</script>	
</body>
</html>
