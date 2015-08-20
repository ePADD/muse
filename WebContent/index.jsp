<%@page contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%

Util.ASSERT(!ModeConfig.isPublicMode()); // this page should not be present in public mode

if (!ModeConfig.isServerMode())
	if (session.getAttribute("archive") == null || request.getParameter("cacheDir") != null)
		SimpleSessions.prepareAndLoadDefaultArchive(request); // if we don't have an archive and are running in desktop mode, try to load archive from given cachedir or from the default dir
	

	//<p/> <p/> <p/> <p/> <jsp:include page="header.jsp"/>
String mode = request.getParameter("mode");
if ("reset".equals(mode))
{
	// sometimes we just want to reset everything (equivalent to logout)
	if (!session.isNew())
		session.invalidate();
	return;
}

String session_mode = (String) JSPHelper.getSessionAttribute(session, "mode");

if ("search".equals(mode) || "search".equals(session_mode))
{
	session.setAttribute ("mode", "search");
	JSPHelper.log.info ("Running in search mode");
}

// if we already have an archive, no point spending time on this page (unless we've been told noredirect)
if (session.getAttribute("archive") != null && request.getParameter("noredirect") == null)
{
	response.sendRedirect("info");
	return;	
}
%>
<!DOCTYPE HTML>
<html lang="en">
<head>
<link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
<meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
<link rel="icon" type="image/png" href="images/muse-favicon.png" />
<jsp:include page="css/css.jsp"/>
<title>Muse Login</title>
</head>
<!-- 
<body style="background-color: #FFBB56">
 -->
<%@include file="div_status.jsp"%>
 
<body class="fixed-width"> 
<script src="js/jquery/jquery.js"></script>
<!--  <script src="js/jquery.backstretch.min.js"></script>  -->

<script>
//$('body').backstretch('http://24.media.tumblr.com/504af00a53a87b185873776e901e9a31/tumblr_mrnp1zPNNc1sfie3io1_1280.jpg');
</script>

<%
Object o = session.getAttribute("noblur"); // check blur state. c is not HttpSession
if (o != null && "true".equals(o.toString()))
{
	JSPHelper.log.info ("blurring turned off, all details will be logged");
	Util.setBlur(false);
}
	
String browserType=(String)request.getHeader("User-Agent");
JSPHelper.log.info ("browser = " + browserType);
if (browserType.indexOf("MSIE") >= 0) {
	out.println ("<br/><br/>Sorry, Muse does not work with Internet Explorer. <br/>\n");
	out.println ("Please open a browser like <a href=\"http://getfirefox.com\">Firefox</a>, <a href=\"http://www.google.com/chrome\">Chrome</a> or <a href=\"http://www.apple.com/safari/download/\">Safari</a> and go to this page: " + request.getRequestURL() + "<br/>");
	out.println ("</body></html>");
	return;
}
%>


<script type="text/javascript">
function textFieldClicked(field)
{
	if (!(field.style) || field.style.color != 'black')
	{
		field.value="";
		field.style.color = 'black';
	}
}
</script>

<jsp:include page="loginForm.jsp"/>


<%@include file="footer.jsp"%>

</body>
</html>
