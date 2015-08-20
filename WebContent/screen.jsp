<%@page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="com.google.gson.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="org.apache.commons.logging.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.exceptions.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>

<%

if (!MemoryStudy.anyCodesAvailable()) { 
	MemoryStudy.UserStats stats = new MemoryStudy.UserStats();
	stats.emailid = (String) session.getAttribute("screenPassEmail");
	stats.gender = (String) session.getAttribute("gender");
	stats.age = (String) session.getAttribute("age");
	stats.education = (String) session.getAttribute("education");
	stats.profession = (String) session.getAttribute("profession");
	stats.ethnicity = (String) session.getAttribute("ethnicity");
	stats.activityHistogram = (List<Integer>) session.getAttribute("activityHistogram");
	JSPHelper.log.info(Util.fieldsToString(stats, false));
	JSPHelper.log.info("NOC: no more codes available");
%>
<html>
<body>
<div style="margin:10%;font-size:12pt">
Sorry, but all available codes have been used up.<br/>
Please sign up for our mailing list 
<a href="https://mailman.stanford.edu/mailman/listinfo/memorystudy" target="_blank" >here</a> to be notified about future studies.

</div>
</body>
</html>
<% return;
}

session.setAttribute("mode", "memorytest");
String googleClientId = null;
if(request.getLocalPort() == 8043) {
	googleClientId = "1072171428245-72o4t2f53c1ksrnefnh6amofj6d7h4op.apps.googleusercontent.com"; // Client ID for development environment
} else {
	if (!"localhost".equals(request.getServerName()) /* this part for debugging only, when running on localhost but with server mode */
	 	&& !JSPHelper.runningOnLocalhost(request))
		googleClientId = "1072171428245-lj239vjtemn7cgstafptk0c46c20kgih.apps.googleusercontent.com"; // this is for the muse installed at https://muse.stanford.edu:8443/muse
	else {
		googleClientId = "1072171428245.apps.googleusercontent.com"; // this is for local host "1058011743827-t8e0fjt1btmujjesoaamgequ5utf4g77.apps.googleusercontent.com";	
	}
}
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>

<link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
<link rel="stylesheet" href="memorystudy/css/screen.css" type="text/css" />
<meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
<link rel="icon" href="memorystudy/images/stanford-favicon.gif">
<jsp:include page="css/css.jsp"/>
<title>Screening</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
<script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
<script src="https://apis.google.com/js/client.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/ops.js"></script>
<script>
function googleOAuth(idx) {
	alert ("Please ensure that popup windows are enabled in your browser. You will authenticate directly with Google in another window.");
    var config = {
        'client_id': '<%=googleClientId%>',
        'scope': 'https://mail.google.com/ https://www.googleapis.com/auth/userinfo.email',
        'response_type': 'token',
        'immediate': false
    };
    gapi.auth.authorize(config, function() {
        var token = gapi.auth.getToken();
        if(!!token) {
      	    gapi.client.load('oauth2', 'v2', function() { 
      		    var request = gapi.client.oauth2.userinfo.get();
      		    request.execute(function (resp) {
          	        $('#loginName' + idx).attr('value', resp.email);
                	$('#message' + idx).html(resp.email);        	  
                });
            });
            $('#password' + idx).attr('value', 'xoauth' + token.access_token); // this field is already hidden
        } else {
  	        $('#message' + idx).html('No authorization');        	  
            $('#password' + idx).attr('value', '');
        }
    });
}
$(document).ready(function() {
	$('input[type=password]').clickOnEnter($('#gobutton')[0]);
});
</script>

</head>
<!-- 
<body style="background-color: #FFBB56">
 -->
<%@include file="div_status.jsp"%>
 
<body class="graded"> 

<%
session.setMaxInactiveInterval(-1);
String linkAfterLoadSession = ("search".equals(JSPHelper.getSessionAttribute(session, "mode"))) ? "createEmailLinksCSE.jsp" : "info";

%>

<script type="text/javascript">
/** add to the UI a simple emailaddr/passwd input section */

// $(document).ready(function() { $('body').css('background-color', '#dca9d4').css('background-image', 'none');}); // hack cos we are being included from index.jsp!

$(document).ready(function() {
	$('.accountType').live("change", accountTypeChanged);
});

// handler for accountType change on any login
function accountTypeChanged(e)
{
	// warning: we are not properly escaping special chars in account names if they have double/single quotes etc.
	
	// DOM structure: there is a series of .login-accounts, each containing [one or more .login-fields and perhaps a .account-add icon]

	$('.login-account').each(function(i, val) { $(val).data("idx", i);}); // set a idx value for each a/c

	var $account = $(e.target).closest('.login-account');
	var idx = $account.data("idx"); // this accounts idx is idx;
	
	var type = $('select', $account).val();
	$('.login-fields', $account).remove(); // wipe out existing fields
	$('.account-add', $account).remove(); // wipe out existing plus if any
	$login_fields = $('<div class="login-fields"></div>');
	if ('imap' == type || 'pop' == type)
	{
		// server needed only for explicit imap/pop types
		var $server = $('<input class="input-field" type="text" placeholder="server" name="server' + idx + '"id="server' + idx + '" size="20"/><br/>');
		$login_fields.append($server);
	}	
	
	if ("gmail" == type) {
		var $message = $('<span id="message' + idx + '" style="font-style: italic">Ensure popups are enabled for Oauth</div>');
		var $login = $('<input class="input-field" type="hidden" placeholder="email" name="loginName' + idx + '"id="loginName' + idx + '" />');
		var $password = $('<input class="input-field password" type="hidden" placeholder="password" name="password' + idx + '"id="password' + idx + '" size="20"/>');
		var $spinner = $('<img id="spinner0" src="images/spinner-white.gif" width="15" style="margin-left:10px;visibility:hidden"/>');
		$login_fields.append($login).append($password).append($message).append($spinner);
		googleOAuth(idx);
	}
	else if ("email" == type)
	{
		var $login = $('<input class="input-field" type="text" placeholder="email" name="loginName' + idx + '"id="loginName' + idx + '" size="20"/><br/>');
		var $password = $('<input class="input-field password" type="password" placeholder="password" name="password' + idx + '"id="password' + idx + '" size="20"/>');
		var $server = $('<input style="display: none" class="input-field" type="text" placeholder="server" name="server' + idx + '"id="server' + idx + '" size="20"/>');
		$login_fields.append($login).append($password).append($server);
		var $message = $('<div id="message' + idx + '" style="font-style: italic">Will identify email server automatically</div>');	
		$login_fields.append($message);		
	}
	else if ("yahoo" == type || "live" == type || "stanford" == type || 'imap' == type || 'pop' == type)
	{
		var $login = $('<input class="input-field" type="text" placeholder="email" name="loginName' + idx + '"id="loginName' + idx + '" size="20"/><br/>');
		var $password = $('<input class="input-field password" type="password" placeholder="password" name="password' + idx + '"id="password' + idx + '" size="20"/>');
		var $spinner = $('<img id="spinner' + idx + '" src="images/spinner-white.gif" width="15" style="margin-left:10px;visibility:hidden"><br/>');
		$login_fields.append($login).append($password).append($spinner);
		if ('live' == type) {
			var $message = $('<div id="message' + idx + '" style="font-style: italic">Only Inbox is accessible for Hotmail <br/>(<a href="help.jsp#hotmail">More</a>)</div>');	
			$login_fields.append($message);
		}
	}
	else if (type == 'mbox')
	{
		var $dirs = $('<input class="input-field" type="text" placeholder="path to folder with mbox files" name="mboxDir' + idx + '"id="mboxDir' + idx + '" size="20"/><br/>');
		$login_fields.append($dirs);
	}
	$account.append($login_fields);
	
//	set_sent_messages_only();
}

</script>

<div id="main"> <!--  style="margin-top:100px;position:relative;width:1000px;overflow:hidden" -->

<h1 style="color:#ffffff; text-align:center">Check your Eligibility <!-- font-style:italic; -->
 </h1>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
<p>



<% String error = (String) JSPHelper.getSessionAttribute(session, "loginErrorMessage");
if (!Util.nullOrEmpty(error))
{
	%>
	<script type="text/javascript">$.jGrowl('<span class="growl"><%=Util.escapeHTML(error)%></span>');</script>
	<% session.removeAttribute("loginErrorMessage");
}
%>
<div class = "boxorange" style="font-size:11pt">
We will now run a quick screening check to ensure that you are eligible for the study.
This should only take a minute or two.
<p>
<div class="login-account">
<div class ="styled-select">
<!--Styling for select: http://bavotasan.com/2011/style-select-box-using-only-css/ -->
<select style="font-size:11pt" class="accountType" name="accountType0" id="accountType0"> <!--  -->
	<option value="none">Email account type</option>
	<option value="gmail">Gmail or Google apps</option>
	<option value="yahoo">Yahoo</option>
</select>
</div> <!-- styled select -->
</div> <!-- login-account -->
<br/>

<table>
<tr> <!--  last line of the table with the stuff other than login boxes -->
<td>
<div id="sent-only-div" style="display:none">

<input style="display:none" id="sent-messages-only" name="sent-messages-only" type="checkbox" checked/><span class="db-hint" >Use only sent mail folders</span>
<input style="display:none" id="downloadMessages" name="downloadMessages" value="false"/>
<input style="display:none" id="dateRange" name="dateRange" value="<%=edu.stanford.muse.email.Filter.getDateRangeForLast1Year() %>"/>
</div> <!-- sent-only -->
</td>
</tr>
</table>

<br/>
<div id="button"></div>
<input type ="button" id="gobutton" onclick="javascript:muse.do_logins()" style="font-size:large" value="Check eligibility">
<input type="hidden" name="simple" value="true"/>
<br/>
</div> <!-- boxorange -->


<div style="clear:both"></div>
</div> <!-- id=main -->
<!-- main -->

</body>
</html>

