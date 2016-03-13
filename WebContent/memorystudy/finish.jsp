<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Study Complete</title>
    <link rel="stylesheet" href="../css/fonts.css" type="text/css" />
    <link rel="stylesheet" href="css/memory.css" type="text/css" />
    <link rel="icon" href="images/ashoka-favicon.gif">
</head>
<body>
<div class ="box">
    <jsp:include page="header.jspf"/>
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
	The study is now complete. We sincerely thank you for your participation.
<% if (!Util.nullOrEmpty(MemoryStudy.PAYMENT)) { %>
You will receive an email with a $<%=MemoryStudy.PAYMENT%> Amazon gift coupon in 2-5 business days, as soon as we process and verify the completion of this test. 
You can contact stanfordmemorystudy@gmail.com in case you have any questions. Thank you!
<% } %>

	<p>
	If you know other people who may be eligible for this study and are interested, please refer them to http://cell.ashoka.edu.in. To hear about scientific publications resulting from this study, please contact us at cell@ashoka.edu.in

    <p>

	If you logged in with a Google account, please go to <a target="_blank" href="https://security.google.com/settings/security">this page</a> and revoke Oauth access for Muse under "Connected apps and sites" -&gt; "Review permissions".
	If you used a Yahoo account, nothing further needs to be done.

	<p>
	Please provide your overall reactions, comments and suggestions about the study for us in the box below. We will be running more editions of similar studies
	and your comments will be very useful to us. Please also indicate if you would like to participate in future studies.
    <p>

	Please fill the feedback form below.
    <p>

    <iframe src="https://docs.google.com/forms/d/1F4TsPMbAGUrumw-zgSEdr-g1FZn6NS2jCCmJzhhIZ4E/viewform?embedded=true" width="600" height="600" frameborder="0" marginheight="0" marginwidth="0">Loading...</iframe>


</div>
<br>
<br>
<br>
</div>
</body>
</html>