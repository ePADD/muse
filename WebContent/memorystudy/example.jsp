<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<script src="../js/jquery/jquery.js"></script>
<script src="../js/muse.js"></script>

<link href="intro.js-0.5.0/example/assets/css/demo.css" rel="stylesheet">
<link href="intro.js-0.5.0/introjs.css" rel="stylesheet">
<link rel="stylesheet" href="css/tester.css"/>
<link rel="icon" href="images/stanford-favicon.gif">
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
<div class="heading">
    <img title="Ashoka University" src="../images/ashoka-logo.png" width="100px" height="100px"/>
    <span style="float: right;font-size: 30px;color: black;">
            COGNITIVE EXPERIMENTS ON LIFE-LOGS (CELL)<br>Ashoka University
        </span>
</div>
<div class="box">
<hr style="color:red;background-color:red">
<div style="width:100%;text-align:center;color:red">
Example
</div>
<hr style="color:red;background-color:red">
<br/>
<div class="container2">
<form id="testinput" name = "testinput" action = "examplehandler" method="post">
		<div id="containerdiv" >
		<div id = "question" class="question">
		<span data-step = "1" data-intro = "You will see a sentence that was taken from your email (this one wasn't, it's just an example). Think about a name or entity that would fit in the empty slot. Remember, this will usually be a name, not an ordinary word.">
		1. I've always wondered why you sat on a wall in the first place, especially if you are an egg. Anyway, get well soon. <br>
        Email recipient name: _ _ _ _ _ _</span>
		</div>
		</div>
		<p/>

            <div style="margin-left: 20%">
                <input class="answer" id="answer" style="padding:7px" data-step="4" data-step="4" data-intro="Type in your answer in this box. The answer is not case sensitive, and spaces do not matter. If you haven't figured out yet, the correct answer is 'Humpty'." type="text" size="40" name="answer" autofocus autocomplete = "off">
                <button id ="hintbutton" onclick="return replacehint();" data-step="3" data-intro="If you're stuck, click this button to reveal the first letter of the answer. During the test, this button will only appear after about 15 seconds. Go ahead, click the button now and see the first letter of the answer reveal in the clue above." >
                    Show hint
                </button>
                <p style="margin-left:20%" class="smaller"><span id="answerLength" data-step = "2" data-intro="The number of words and letters in the answer. Sometimes the answer may be 2 words. This description will turn green when the number of letters you have entered in the answer box is correct.">[1 word: 6 characters]
                </span></p>
                <span data-step="5" data-intro="If you don't remember the name for some reason, choose the option that best applies to you">OR &nbsp;&nbsp; Answer why you forgot:<br></span>
                <input id="fComplete" type="checkbox"/>I forgot the email and events completely<br>
                <input id="fContext" type="checkbox"/>I remember the email/surrounding events but not the email recipient<br>
                <span data-step="6" data-intro="When you feel you know the answer to a question but you can't remember it at the moment and you feel the answer is about to pop into mind at any second, then you are in a tip-of-the-tongue state.  For example I might remember that I sent an email to the president and I know his name but I cannot recall his name right now. It feels like his name is ready to POP into mind at any second. If this happens, choose the tip-of-the-tongue option.">
                    <input id="fTip" type="checkbox" onclick="$('#tipRate').toggle()"/>I remember the person but their name is on the tip of my tongue
                    <span id="tipRate" style="margin-left:3%;display:none">
                        <br>
                        <span style="margin-left:3%">On a scale of 1 to 10, rate how close you are to having the name pop into mind&nbsp; <input id="tipScore" size="2"/></span><br>
                        <span style="margin-left:3%">1&nbsp; - I have no idea &nbsp;&nbsp;</span><br>
                        <span style="margin-left:3%">10 - It's close!</span>
                    </span>
                </span>
                <br>
                <input id="unfair" type="checkbox" onclick='$("#unfairReason").toggle()'/> Unfair question?
                <input type="text" placeholder="Please elaborate" size="40" style="display:none" id="unfairReason"/>
            </div>
    <br>
					
		<div data-step = "7" data-intro="You'll be asked to provide some more information about your answer. Since this is just an example, pick any option for each of the questions. ">
            <div>On a scale of 1 to 10, how confident are you about your answer? <input name="certainty" id="certainty" size="2"/>
                <br>
                10 - I am Certain<br>
                5&nbsp  - I am not sure<br>
                1&nbsp  - I have no Idea<br>
            </div>
		    <br/>

            <div>How vividly do you remember writing this mail? <input name="memory" id="memory" size="2"/>
                <br>
                10 - I clearly remember writing this mail<br>
                6&nbsp - I rember the person<br>
                4&nbsp  - I recall the general context<br>
                1&nbsp  - I have no memory of the event<br>
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
                        for(int m=1;m<=12;m++){
                            %><option value="<%=m%>"><%=m%></option><%
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
			
		<br/>
		<button type="submit" onclick="return handle_submit()" value="Submit" style="margin-left:20%;" data-step="7" data-intro="This button will record your answer and continue to the next question.">Submit</button>

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
            if ($("#answer").val() !== '') {
                if ('humpty' !== $('#answer').val().toLowerCase()) {
                    alert("Uh, oh. The correct answer is Humpty.");
                    return false;
                }
            } else if (!$("#fComplete")[0].checked && !$("#fContext")[0].checked && !($("#fTip")[0].checked && $("#tipRate").val() !== '') && !($("#unfair")[0].checked && $("#unfairReason").val() !== '')) {
                alert("Please enter the answer or answer why you forgot.");
                return false;
            }

            if ($('#memory').val()=='' || $('#certainty').val()=='' || (!$("#timeInfo")[0].checked && ($('#timeYear').val()==-1||$("#timeMonth").val()==-1||$("#timeYear")==-1))) {
                alert("Please answer all the three questions about your answer.");
                return false;
            }

			return true;				
		}
		
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
