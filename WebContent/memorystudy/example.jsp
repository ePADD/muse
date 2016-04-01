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
            <span data-step="1" data-intro="You will see a sentence that was taken from your email (this one wasn't, it's just an example). Think about the person you sent this email to. Remember, the clue will be from an email to a single person, not a group.">
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
                <input spellcheck="false" autofocus autocomplete="off" class="answer" id="answer" style="border:solid 2px #082041; background: #082041" data-step="4" data-intro="Type in your answer in this box. The answer is not case sensitive, and spaces do not matter. If you haven't figured it out yet, the correct answer in this example is 'Humpty Dumpty'." type="text" size="40" name="answer">
                <button data-step="3" data-intro="If you can't remember the answer, this button will appear after 15 seconds. Click on it to reveal the initial letters of each word in the name and try to recall the name using this hint." style="margin-left:50px;display:inline-block;" type="button" id="hint-button">Hint</button>
                <br/>
                <div class="smaller">
                    <span id="answerLength" data-step="2" data-intro="These are the number of words and letters in the answer.">
                        [2 words: 6 letters, 6 letters]
                    </span>
                </div>

            </div>
<br/>

<div data-step="5" data-intro="After attempting the answer with or without the hint, choose the option that best describes your recall for the name.">
    Tell us about this recollection: <br/>
    <input name="recall-type" type="radio" value="1"/>The name was easy to recall<br/>
                <span data-step="6" data-intro=" If you couldn't recall the name right away, but you felt that it was about to pop into your mind at any moment, then you were in a tip-of-the-tongue state. For example, you might have known the person you sent the email to, but could not recall their name immediately and strongly felt that you knew it. It may also happen that you were able to recall the name after some seconds on your own, or by using the hint. If any of this happened, choose this option.">
                    <input name="recall-type" type="radio" value="2"/>The name was on the tip of my tongue<br/>
                </span>
    <input name="recall-type" type="radio" value="3"/>I remembered the person, but not the name<br/>
                <span data-step="7" data-intro="If you remembered the general context of the email, but not the person you sent it to, choose this option.">
                    <input name="recall-type" type="radio" value="4"/>I remembered the surrounding events, but not the person<br/>
                </span>
    <input name="recall-type" type="radio" value="5"/>I forgot the email completely<br/>
            </div>
		    <br/>

<div data-step="8" data-intro="On a scale of 1 to 10, rate how strongly you remember writing this episode by dragging the slider to the left or right.">
                <p>
                    <i class="fa fa-caret-right"></i> How vividly do you remember this specific conversation?
                <br>
                (1: no idea; 5: fair idea; 10:strong memory)
                <p>
                <br/>

                <div style="position:relative;line-height:0.5em">
                <div style="font-size: small; position:relative;left:-33px; top:-30px;max-width:85px;max-height:94px;transform:rotate(270deg);">Not set</div>
                <span style="font-size: small; position:relative;left:40px">1</span>
                <span style="font-size: small; position:relative;left:162px">5</span>
                    <span style="font-size: small; position:absolute;left:340px">10</span><br>
                <input name="memory" id="memory" type="range" min="0" max="10" step="1" value="0" list="steplist" oninput="outputUpdate(value)"/>
                <output style="position:relative;left:40px;top:-10px;" for="memory" id="memory-amount">Not set</output>
                </div>
            </div>

            <br/>

            <script>
                function outputUpdate(v) {
                    document.querySelector('#memory-amount').value = (v > 0) ? v : "Not set";
                }
            </script>

<div data-step="9" data-intro="Try to recall when you wrote the email. If you cannot guess the month and year at all, choose &quot;I have no idea&quot;.">

                <i class="fa fa-caret-right"></i> Approximately when do you think was this sentence written?
                <div>
                <span id="time">
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
                        <% int currentYear = new GregorianCalendar().get(Calendar.YEAR); %>
                        <option value="<%=currentYear-1%>"><%=currentYear-1%></option>
                        <option value="<%=currentYear%>"><%=currentYear%></option>
                    </select>
                    <br>
                    <span>Month&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span><span>Year</span>
                </span><br>
                <input type="checkbox" id="timeInfo"> I have no idea<br>
		    </div>

        <!--For tick marks-->
        <datalist id="steplist">
            <option>1</option><option>2</option><option>3</option><option>4</option><option>5</option>
            <option>6</option><option>7</option><option>8</option><option>9</option><option>10</option>
        </datalist>
			
		<br/>
    </div>

<div data-step="10" data-intro="Once you've answered all parts of the question, click on the Submit button. If you do not wish to attempt this question at all, you can click the Give up button at any point and move on to the next question">
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

        $('#hint-button').click(show_hint);


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

        /*
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
		*/
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
