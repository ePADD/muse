<%@page language="java" contentType="text/html; charset=UTF-8" %>
<%@page language="java" import="edu.stanford.muse.util.*" %>
<%@page language="java" import="edu.stanford.muse.memory.*" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>CELL Consent Form</title>
    <link rel="stylesheet" href="css/memory.css" type="text/css"/>
    <link rel="stylesheet" href="../css/fonts.css" type="text/css"/>
    <link rel="icon" href="../images/ashoka-favicon.gif">
</head>
<body>
<div class="box">
    <jsp:include page="header.jspf"/>
    <h2 class="title">Consent Form</h2>

    <form id="eligibility" action="consenthandler.jsp" method="post">

        <fieldset>
            <legend>What is Consent?</legend>
            This consent form below will explain important information. Please read it carefully. If you want to participate in the study you must agree to what is in this form.

        </fieldset>
        <br/>

        <fieldset>
            <legend>Consent Form</legend>
            <p class="bold">ASHOKA UNIVERSITY: Research Information Sheet</p><br>
            Protocol Director: Sudheendra Hangal<br>
            Protocol Title: Computerized Assessment of Cognition<br>
            <p class="bold">DESCRIPTION:</p> You are invited to participate in a research study on email archives and whether they can be applied to study memory and other mental processes. Participants will provide their email information to a secure computer server. Our program will automatically analyze your email and evaluate whether you could reasonably participate in the study. For example if you rarely use email or do not speak English then our study may not find useful information and we may ask you not to participate. If you pass our screening then the program will generate questions for you, and record your answers.
            <br>
            <p class="bold">TIME INVOLVEMENT:</p> Your participation will take approximately 45 to 60 minutes.
            <br>
            <p class="bold">RISKS AND BENEFITS:</p> The only risks in this study are breaches of confidentiality and minor frustration from the procedures which might be mentally challenging.
            <br><br>Email can contain sensitive and personal information so that we carefully protect the security of your data by encrypting the server computer on which you perform the study, removing the information after you complete the study, and only save non-identifying information.

            There is no benefit to the individual participant. We cannot and do not guarantee or promise that you will receive any benefits from this study. Your decision of whether or not to participate in this study will not affect you negatively in any way.
            <br>
            <% if (!Util.nullOrEmpty(MemoryStudy.PAYMENT)) { %>
            <p class="bold">PAYMENTS:</p> If you pass our screening and complete the test, you will receive $<%=MemoryStudy.PAYMENT%> in Amazon.com credit as payment for your participation.
            <br>
            <% } %>
            <p class="bold">SUBJECT'S RIGHTS:</p> If you have read this form and have decided to participate in this project, please understand your participation is voluntary and you have the right to withdraw your consent or discontinue participation at any time without penalty or loss of benefits to which you are otherwise entitled. The alternative is not to participate. You have the right to refuse to answer particular questions. Your individual privacy will be maintained in all published and written data resulting from the study.
            <br>
            <p class="bold">CONTACT INFORMATION:</p>
            <p class="bold">Questions:</p> If you have any questions, concerns or complaints about this research, its procedures, risks and benefits, contact the Protocol Director, Sudheendra Hangal at +91-98454-61040.
            <br>
            <p class="bold">Independent Contact:</p> If you are not satisfied with how this study is being conducted, or if you have any concerns, complaints, or general questions about the research or your rights as a participant, please contact the Ashoka Institutional Review Board (IRB) at irb@ashoka.edu.in. You can also write to: The Ashoka IRB, Ashoka University, Haryana, India 131028.
            <p>
                Please print a copy of this page for your records.
            <p>
                By consenting you are agreeing that you are 18 years of age or over and willing to perform this research.
                If you agree to participate in this research, please complete the survey that follows.
        </fieldset>
        <fieldset>
            <legend>Consent Agreement</legend>
            <table>
                <tr>
                    <td>I agree to the terms stated in the above consent form</td>
                    <td><input type="radio" name="consentagree" value="Yes" required></td>
                </tr>
                <tr>
                    <td>I do not agree to the above consent form, and I wish to forego compensation</td>
                    <td><input type="radio" name="consentagree" value="No"></td>
                </tr>
            </table>
        </fieldset>
        <br/>
        <fieldset>
            <button type="submit">Continue</button>
        </fieldset>
    </form>
</div>
</body>
</html>