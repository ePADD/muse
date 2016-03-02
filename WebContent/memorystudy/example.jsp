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

<link href="intro.js-0.5.0/example/assets/css/demo.css" rel="stylesheet">
<link href="intro.js-0.5.0/introjs.css" rel="stylesheet">
<link rel="stylesheet" href="css/tester.css"/>
<link rel="icon" href="images/ashoka-favicon.gif">
<title>Example</title>
<script>
$(function() {
	  $(".hintwait").delay(10000).fadeIn(); /*20,000 milliseconds = 20 seconds. Currently set at 10,000 milliseconds = 10 seconds.*/
});
</script>
</head>
<body>

<br/>
<br/>
<script type="text/javascript" src="intro.js-0.5.0/intro.js"></script>

<p>

<div class="box">
    <img title="Ashoka University" src="../images/ashoka-logo.png" width="100px" height="100px"/>
    <span style="float: right;font-size: 30px;color: red;">
        Example</span>
    <div style="clear:both"></div>

<br/>
<div class="container2">
<form id="testinput" name = "testinput" action = "examplehandler" method="post">
		<div id="containerdiv" >
		<div id = "question" class="question">
		<span data-step = "1" data-intro = "You will see a sentence that was taken from your email (this one wasn't, it's just an example). Think about the person you sent this email to. Remember, the clue will be from an email to a single person, not a group.">
		1. I've always wondered why you sat on a wall in the first place, especially if you are an egg. Anyway, get well soon. <br>
        <p>
        Email recipient name: _ _ _ _ _ _</span>
		</div>
		</div>
		<p/>

            <div style="margin-left: 20%">
                <input class="answer" id="answer" style="border:solid 2px blue; background: #7c7c7c" data-step="4" data-step="4" data-intro="Type in your answer in this box. The answer is not case sensitive, and spaces do not matter. If you haven't figured out yet, the correct answer is 'Humpty'." type="text" size="40" name="answer" autofocus autocomplete = "off">
                <p style="margin-left:20%" class="smaller"><span id="answerLength" data-step = "2" data-intro="The number of words and letters in the answer. Sometimes the answer may be 2 words. This description will turn green when the number of letters you have entered in the answer box is correct.">[1 word: 6 characters]
                </span></p>
                <span data-step="5" data-intro="If you don't remember the name for some reason, choose the option that best applies to you">OR &nbsp;&nbsp; Answer why you forgot:<br></span>
                <input id="fComplete" name="fail" value=0 type="radio" onclick="show_hint()"/>I forgot the email completely, give me a hint<br>
                <input id="fContext" name="fail" value=1 type="radio" onclick="show_hint()"/>I remember the surrounding events but not the recipient. Give me a hint<br>
                        <span data-step="6" name="fail" data-intro="When you feel you know the answer to a question but you can't remember it at the moment and you feel the answer is about to pop into mind at any second, then you are in a tip-of-the-tongue state.  For example I might remember that I sent an email to the president and I know his name but I cannot recall his name right now. It feels like his name is ready to POP into mind at any second. If this happens, choose the tip-of-the-tongue option.">
                    <input id="fTip" name="fail" type="radio" onclick="$('#tipRate').toggle()"/>I remember the person and their name is on the tip of my tongue. Give me a hint.
                            </span>
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
                <br>
                <!--
                <input id="unfair" name="fail" type="radio" onclick='$("#unfairReason").toggle()'/> Unfair question?
                <input type="text" placeholder="Please elaborate" size="40" style="display:none" id="unfairReason"/>
                -->
            </div>
    <br>

        <div data-step = "7" data-intro="You'll be asked to provide some more information about your answer. Since this is just an example, pick any option for each of the questions. ">
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

            <div>How vividly do you remember writing this email?
                <br>
                (1: no idea; 5: fair idea; 10:strong memory)<br/>

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
            <input type="checkbox" style="margin-left:20%" id="timeInfo" onclick='$("#time").toggle()'> I have no idea<br>
            </div>

		</select>
		</div>
        <!--For tick marks-->
        <datalist id="steplist">
            <option>1</option><option>2</option><option>3</option><option>4</option><option>5</option>
            <option>6</option><option>7</option><option>8</option><option>9</option><option>10</option>
        </datalist>
			
		<br/>
		<button class="submitButton" style="margin-left: 20%" type="submit" value="Submit">Submit</button>
        <button class="submitButton" style="margin-left: 20%" type="submit" value="GiveUp">Give up</button>

		<script>
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
            if ($("#answer").val() !== '' || button_text == 'Give up') {
                if ('humpty' !== $('#answer').val().toLowerCase()) {
                    alert("Uh, oh. The correct answer is Humpty.");
                    return false;
                }
            } /* else if (!$("#fComplete")[0].checked && !$("#fContext")[0].checked && !($("#fTip")[0].checked && $("#tipRate").val() !== '') && !($("#unfair")[0].checked && $("#unfairReason").val() !== '')) {
                alert("Please enter the answer or answer why you forgot.");
                return false;
            }
*/
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
			var correctAnswerLengthWithoutSpaces = 'humpty'.length;
			// check # of letters in answer
			var val = $('#answer').val();
			val = val.replace(/ /g, '');
			if (val.length == correctAnswerLengthWithoutSpaces)
				$('#answerLength').css('color', 'green');
			else
				$('#answerLength').css('color', 'red');
		});
		</script>
	
		</form>
	</div>
	</div>
	<script type="text/javascript">
	alert('Here is an example of the kind of questions you will see. Please follow the tour by clicking on the Next button. You can also use the right and left arrow keys.');
	introJs().start();
	</script>	
</body>
</html>
