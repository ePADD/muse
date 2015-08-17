<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="ISO-8859-1">
<title>Study Complete</title>
<link rel="stylesheet" href="css/memory.css" type="text/css" />
<link rel="icon" href="images/stanford-favicon.gif">
</head>
<body>
<h1 class="title">Study Complete</h1>
<div class ="box">
<%
Archive archive = (Archive) session.getAttribute("archive");
if (archive != null) {
	archive.clear();
	archive.close();
	String cacheDir = (String) session.getAttribute("cacheDir");
    String rootDir = JSPHelper.getRootDir(request);
	Archive.clearCache(cacheDir, rootDir);
	// avoid trouble later if the same session is used
	session.removeAttribute("emailDocs");
	session.removeAttribute("archive");
}
%>
We sincerely thank you for participating in this study.
<% if (!Util.nullOrEmpty(MemoryStudy.PAYMENT)) { %>
You will receive an email with a $<%=MemoryStudy.PAYMENT%> Amazon gift coupon in 2-5 business days, as soon as we process and verify the completion of this test. 
You can contact stanfordmemorystudy@gmail.com in case you have any questions. Thank you!
<% } %>

<p>
If you know other people who may be eligible for this study and are interested, please refer our link (https://muse.stanford.edu/muse/memorystudy) to them.
<p>
 
If you logged in with a Google account, please go to <a target="_blank" href="https://security.google.com/settings/security">this page</a> and revoke Oauth access for Muse under "Connected applications and sites" -&gt; "Review permissions".
If you used a Yahoo account, nothing further needs to be done.

<p>
Please provide your overall reactions, comments and suggestions about the study for us in the box below. We will be running more editions of similar studies
and your comments will be very useful to us. Please also indicate if you would like to participate in future studies.

<div align="center">
<form method="post" action="http://prpl.stanford.edu/report/field_report.php">
< type="hidden" name="to" id="to" value="hangal@cs.stanford.edu"/>

<table>
<tr><td align="left">
Your email address <input placeholder="(Optional)" size="30" name="from" id="from"/>
</td></tr>

<tr><td>
Comments:<br/>
<textarea style="padding:5px" rows="25" cols="60" name="message" id="message"></textarea>
</td></tr>

<tr><td><input type="submit" value="Submit"/></td></tr>

</table>
</form>
</div>
<br>
<p>
To hear about scientific publications resulting from this study, please sign up for our mailing list 
<a href="https://mailman.stanford.edu/mailman/listinfo/memorystudy" target="_blank" >here</a>.
<br>
<br>
</div>
</body>
</html>