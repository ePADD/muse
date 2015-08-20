<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
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
		1. I've always wondered why _ _ _ _ _ _ Dumpty sat on a wall in the first place, especially if he was an egg.</span>
		</div>
		</div>
		<p/>
		
			<input class="answer" id="answer" style="margin-left:20%;padding:7px" "data-step="4" data-step="4" data-intro="Type in your answer in this box. The answer is not case sensitive, and spaces do not matter. If you haven't figured out yet, the correct answer is 'Humpty'." type="text" size="40" name="answer" autofocus autocomplete = "off">
			<button id ="hintbutton" onclick="return replacehint();" data-step="3" data-intro="If you're stuck, click this button to reveal the first letter of the answer. During the test, this button will only appear after about 15 seconds. Go ahead, click the button now and see the first letter of the answer appear in the clue above." >
				Show hint
			</button>
		<br/>
		<p style="margin-left:20%" class="smaller"><span id="answerLength" data-step = "2" data-intro="The number of words and letters in the answer. Sometimes the answer may be 2 words. This description will turn green when the number of letters you have entered in the answer box is correct.">[1 word: 6 characters]
		</span></p>
					
		<div data-step = "5" data-intro="You'll be asked to provide some more information about your answer. Since this is just an example, pick any option for each of the questions. ">
		<select style="margin-left:20%" name="certainty" id="certainty">
			<option value="0">How sure are you?</option>
			<option value="4">I'm certain.</option>
			<option value="3">I'm fairly sure.</option>
			<option value="2">I'm unsure -- my answer may or may not be right.</option>
			<option value="1">I have no idea.</option>
		</select>
		<br /> <select style="margin-left: 20%" name="memoryType" id="memoryType">
				<option value="0">What do you remember about this sentence?</option>
				<option value="1">I remember its general context</option>
				<option value="2">I can deduce the answer, but don't recall this context</option>
				<option value="3">I don't remember anything about it</option>
			</select> <br /> 
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
		</div>
			
		<br/>
		<button type="submit" onclick="return handle_submit()" value="Submit" style="margin-left:20%;" data-step="6" data-intro="This button will record your answer and continue to the next question. Please click it now.">Submit</button>

		<script>
		function replacehint(){
			var hint = "H _ _ _ _ _";
			var spacetoreplace = "_ _ _ _ _ _"
			var text = $('#question').text();
			text = text.replace(spacetoreplace, hint);
			$('#question').text(text);
			return false;
		}
		
		function handle_submit(event) {
			if ($('#memory').val() == 0 || $('#certainty').val() == 0 || $('#recency').val() == -1) {
				alert ("Please select an option for each of the dropdown boxes.");
				return false;
			}
			if ('humpty' !== $('#answer').val().toLowerCase()) {
				alert ("Uh, oh. The correct answer is Humpty.");
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
