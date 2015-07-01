<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<% 	JSPHelper.logRequest(request); %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<jsp:include page="css/css.jsp"/>
</head>
<body class="fixed-width">
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<jsp:include page="header.jsp"/>

<div class="panel">
<div style="text-align:left">
Thanks for offering your feedback on Muse. <br/>
To be notified of future developments with Muse, please sign up to the <a href="https://mailman.stanford.edu/mailman/listinfo/muse-friends">muse-friends mailing list</a>
or follow our Facebook page by clicking on the "Like" button at the bottom of this page.
<hr/>
</div>

<div align="center">
<form method="post" action="http://prpl.stanford.edu/report/field_report.php">
<input type="hidden" name="to" id="to" value="hangal@cs.stanford.edu"></input>

<table>
<tr><td align="left">
Your email address (optional): <input size="30" name="from" id="from"/>
</td></tr>

<tr><td>
Comments:<br/>
<textarea style="padding:5px" rows="25" cols="60" name="message" id="message"></textarea>
</td></tr>

<tr><td><input type="submit" value="Submit"></input></td></tr>

</table>
</form>
</div>

</div> <!--  panel -->
<jsp:include page="footer.jsp"/>

</body>
</html>

