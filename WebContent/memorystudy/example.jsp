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
            <div data-step="1" data-intro="You will see a sentence taken from your sent email (this one is just an example). Think about the person you sent this email to. Remember, the clue will be from an email to a single person, not a group.">
                <div id="nohint-question" class="question">
                    I've always wondered why you sat on a wall in the first place, especially if you are an egg. Anyway, get well soon. <br>
                    <p>     Email recipient name: <span data-step="5" data-intro="Upon answering the first question, the first letters in the name will be revealed here. If you were not able to recall the name earlier, you can use this hint to try again."> _ _ _ _ _ _ &nbsp;&nbsp; _ _ _ _ _ _</span> </p>
                </div>
                <div id="hint-question" class="question" style="display: none">
                    I've always wondered why you sat on a wall in the first place, especially if you are an egg. Anyway, get well soon. <br>
                    <p>     Email recipient name: <span class="hint-letter">H</span> _ _ _ _ _ &nbsp;&nbsp; <span class="hint-letter">D</span> _ _ _ _ _ <span class="hint-letter">Hint active</span></p>
                </div>

            </div> </p>
            </div>
		</div>
		<p/>

            <div style="margin-left: 5%">
                <i class="fa fa-caret-right"></i> Type here:
                <input data-step="2" data-intro="Type in your answer in this box. If you remember only part of the name, or are unsure about the spelling, type what you know." spellcheck="false" autofocus autocomplete="off" class="answer" id="answer" style="border:solid 2px #082041; background: #082041" type="text" size="40" name="answer">
                <br/>
                <div class="smaller">
                    <span id="answerLength">
                        [2 words: 6 letters, 6 letters]
                    </span>
                </div>

            </div>
<br/>

            <div data-step="3" data-intro="After attempting the answer, choose the option that best describes your recall process.">
                Tell us about this recollection: <br/>

                <input name="recall-type" type="radio" value="1"/>The name was easy to recall<br/>
                <span><input name="recall-type" type="radio" value="2"/>I got the name after a while</span><br/>
                <span data-step="4" data-intro="If you can't recall the name right now, but you strongly feel that it is about to pop into your mind at any moment, then you are in a tip-of-the-tongue state. If this happens, choose this option."><input name="recall-type" type="radio" value="3"/>The name is at the tip of my tongue!</span><br/>
                <input name="recall-type" type="radio" value="4"/>I know the person, not the name<br/>
                <span><input name="recall-type" type="radio" value="5"/>I don't know</span><br/>
            </div>
		    <br/>

            <div>
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

            <div>
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

<div data-step="6" data-intro="Once you've answered all parts of the question, click on the Submit button. You can also click the Skip button at any point and move on to the next question. For now, type 'Humpty Dumpty' in the answer box and click on Submit to start.">
        <button class="submitButton" style="margin-left: 20%;display:inline;" type="submit" value="Submit">Submit</button>
        <button class="submitButton" style="margin-left: 20%;display:inline;" type="submit" value="GiveUp">Skip</button>
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
                || (button_text == 'Skip')) {
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
