<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="stylesheet" href="css/tester.css" type="text/css" />
<link rel="icon" href="images/stanford-favicon.gif">
<title>Example Result</title>
</head>
<body>
<div class="box">
    <img title="Ashoka University" src="../images/ashoka-logo.png" width="50px" height="50px"/>
    <div style="clear:both"></div>

    <br/>
    <br/>

    <%
    String correctanswer = "humptydumpty";
    String useranswer = request.getParameter("answer");
    if (useranswer != null)
        useranswer = useranswer.replaceAll(" ", "");
    if ("humptydumpty".equalsIgnoreCase(useranswer)){
    %>
        Awesome! You got that right!
    <%}
    else {
    %>
        Looks like you got that wrong. You entered "<%=useranswer%>", but the correct answer was Humpty Dumpty.
    <%}

    %>
<p>
Now let's start the real study. Please take care of any other activities first, and ensure that you have an uninterrupted 45 to 60 minutes to complete the study. 
Please answer the questions from memory only; do NOT refer to your email, ask anyone, or use any other aids for help. Try your best to recall the answer, but do 
not spend more than a couple of minutes on a single question.
When the test is complete, you will be able to see the answers to all the questions, and we will ask you for some reactions.

    <!--
<p> Remember: 1) The answer to each of these questions is generally <b>a name</b>, and 2) All the sentences are contained in emails <b>you've written</b> over the last one year.
-->

<br>
<p class="bold" style="font-size: 1.25em;">Are you ready to begin the study?</p>
<p class="bold" style="font-size: 1.00em;text-align:center"><a style="text-decoration:none" href="question"><button>Begin</button></a></p>
</div>
</body>
</html>