<%@page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%
//browser check
String ua = request.getHeader("User-Agent");
if (ua != null)
{
	ua = ua.toLowerCase();
	if (ua.indexOf("android")  >= 0 || ua.indexOf("iphone")  >= 0 || ua.indexOf("ipad")  >= 0 || ua.indexOf("ipod") >= 0)
	{%>
		<html><script> alert('Sorry, this site does not work with mobile browsers.');</script></html>
	<%
		return;
	}

	if (ua.indexOf("chrome") < 0 && ua.indexOf("safari")  < 0 && ua.indexOf("firefox") < 0)
	{%>
		<html><script> alert('Sorry, this browser is not supported. Please use the Chrome or Safari browsers.');</script></html>
	<%
		return;
	}
}

session.setAttribute("mode", "memorytest");

	session.setMaxInactiveInterval(60 * 60 * 24 * 2); // 48 hour timeout
	String code = (String) request.getParameter("code");
	if (code != null)
		code = code.trim();

MemoryStudy.UserStats user;
if (request.getParameter("options") != null) {
	session.setAttribute("debug", "true");
	user = new MemoryStudy.UserStats("unk", "male", "25", "phd", "none", "none", "testuser", request.getRemoteAddr().toString(), request.getHeader("User-Agent"));
}
else
	user = MemoryStudy.lookup(code);

boolean found = (user != null);
if (!found) {
%>
	<html>
	<head>
		<link rel="stylesheet" href="memorystudy/css/screen.css" type="text/css"/>
		<link rel="icon" href="images/ashoka-favicon.gif">
	</head>
	<body>
		<div class="box">
		Your code was not found. Please go back to the <a href ="/muse/memorystudy/loginlanding.html">code submission page</a>.
		</div>
	</body>
	</html>
	<%
	return;
}

user.IPaddress = request.getRemoteAddr().toString();
user.userAgent = request.getHeader("User-Agent");
MemoryStudy newStudy = new MemoryStudy(user);
session.setAttribute("study", newStudy);

if (request.getParameter("answers") != null) {
	session.setAttribute("answers", "true");
}

int numQ = HTMLUtils.getIntParam(request, "n", 40);
session.setAttribute("numQuestions", new Integer(numQ));

session.setAttribute("mode", "memorytest");
System.setProperty ("muse.dummy.ner", "true");
String googleClientId = null;
if(request.getLocalPort() == 8043) {
	googleClientId = "1072171428245-72o4t2f53c1ksrnefnh6amofj6d7h4op.apps.googleusercontent.com"; // Client ID for mem study
} else {
	if (!"localhost".equals(request.getServerName())){ /* this part for debugging only, when running on localhost but with server mode */
	 	//&& !JSPHelper.runningOnLocalhost(request)) {
        if (JSPHelper.runningOnMuseMachine(request)) {
            googleClientId = "1072171428245-lj239vjtemn7cgstafptk0c46c20kgih.apps.googleusercontent.com"; // this is for the muse installed at https://muse.stanford.edu:8443/muse
        }
        //running on cell account at Ashoka
        else if (JSPHelper.runningOnAshokaMachine(request)) {
            googleClientId = "8392513058-0oq4g55m7fhtgnqf8lrbhcgigfhuhmu4.apps.googleusercontent.com"; //for http://125.22.40.138:8080
        }
    }
    else {
		googleClientId = "1072171428245.apps.googleusercontent.com"; // this is for local host "1058011743827-t8e0fjt1btmujjesoaamgequ5utf4g77.apps.googleusercontent.com";
	}
}
%>
<!DOCTYPE HTML>
<html lang="en">
<head>
	<link rel = "stylesheet" type ="text/css" href="memorystudy/css/screen.css">
	<jsp:include page="css/css.jsp"/>
	<link rel="stylesheet" href="css/fonts.css" type="text/css"/>

	<link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
	<meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
	<link rel="icon" href="images/ashoka-favicon.gif">

	<script type="text/javascript" src="js/jquery/jquery.js"></script>
	<script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
	<script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
	<script type="text/javascript" src="js/statusUpdate.js"></script>
	<script type="text/javascript" src="js/muse.js"></script>
	<script type="text/javascript" src="js/ops.js"></script>
	<script>

	muse.mode = "memorytest";
	function googleOAuth(idx) {
		alert ("Please ensure that popup windows are enabled in your browser. You will authenticate directly with Google in another window. After you have authenticated, click the Start button.");
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
				$('#password' + idx).append($('<div style="font-size:small"><i>' + resp.email + '</i></div>'));
			} else {
				$('#message' + idx).html('No authorization');
				$('#password' + idx).attr('value', '');
			}
		});
	}


	$(document).ready(function() {
		$('input[type=password]').clickOnEnter($('#gobutton')[0]);
	});

		// function handleClientLoad() { gapi.auth.authorize({client_id: <%=googleClientId%>, 'scope': 'https://mail.google.com/ https://www.googleapis.com/auth/userinfo.email','response_type':'token','immediate': false}, function() { console.log('done init'); }); }
	</script>

<script type="text/javascript" src="https://apis.google.com/js/client.js?onload=handleClientLoad"></script>

<title>CELL Login</title>
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
		var $message = $('<span id="message' + idx + '" style="font-style: italic">Ensure popups are enabled for Oauth</span>');
		var $login = $('<input class="input-field" type="hidden" placeholder="email" name="loginName' + idx + '"id="loginName' + idx + '" />');
		var $password = $('<input class="input-field password" type="hidden" placeholder="password" name="password' + idx + '"id="password' + idx + '" size="20"/>');
		var $spinner = $('<img id="spinner0" src="images/spinner-white.gif" width="15" style="margin-left:10px;visibility:hidden"/>');

		$login_fields.append($message).append($login).append($password).append($spinner);
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
}

</script>

<div id="main"> <!-- style="margin-top:200px;position:relative;width:1200px;overflow:hidden" -->
<input style="display:none" id="dateRange" name="dateRange" value="<%=edu.stanford.muse.email.Filter.getDateRangeForLast1Year() %>"/>


<% String error = (String) JSPHelper.getSessionAttribute(session, "loginErrorMessage");
if (!Util.nullOrEmpty(error))
{
	%>
	<script type="text/javascript">$.jGrowl('<span class="growl"><%=Util.escapeHTML(error)%></span>');</script>
	<% session.removeAttribute("loginErrorMessage");
}

String dateRange = request.getParameter("dateRange");
if (dateRange == null)
	dateRange = edu.stanford.muse.email.Filter.getDateRangeForLast1Year();
%>
<div class="boxorange" style="font-size:11pt;position:relative;">
	<img style="position:absolute;top:5px;width:50px" title="Ashoka University" src="images/ashoka-logo.png"/>
	<h1 style="text-align:center;font-weight:normal;font-variant:normal;text-transform:none;font-family:Dancing Script, cursive">Cognitive Experiments with Life-Logs</h1>
<hr style="color:rgba(0,0,0,0.2);background-color:rgba(0,0,0,0.2);"/>
	This step will generate questions for you and can take 5 to 15 minutes, depending on your volume of email.
You can do other things while you wait, but please do not close this browser window.
<div class="login-account">
<div class="styled-select">
<select style="font-size:13px" class="accountType" name="accountType0" id="accountType0">
	<option value="none">Email account type</option>
	<option value="gmail">Gmail or Google apps</option>
	<option value="yahoo">Yahoo</option>
	<%if (request.getParameter("options") != null) { %>
		<option value="email">Email account</option>
		<option value="imap">IMAP server</option>
		<option value="mbox">Mbox files</option>
	<% } %>
</select>

</div> <!-- styledselect -->
<br/>
</div><!-- login-account -->
<br/>

<table>
<tr> <!--  last line of the table with the stuff other than login boxes -->
<td>
<div id="sent-only-div" style="display:none">
<input style="display:none" id="sent-messages-only" name="sent-messages-only" type="checkbox" checked/><span class="db-hint" >Use only sent mail folders</span>
<input style="display:none" id="downloadMessages" name="downloadMessages" value="false"/>
</div> <!-- sent-only -->
<span style="display:<%=(request.getParameter("options") != null) ? "inline":"none"%>">
Dates <input size="30" id="dateRange" name="dateRange" value="<%=dateRange%>"/>
</span>
<span style="display:<%=(request.getParameter("options") != null) ? "inline":"none"%>">
#Questions per Interval <input style="display:<%=(request.getParameter("options") != null) ? "inline":"none"%>" id="n" name="n" value="4"/>
</span>
</td>
</tr>
</table>
<br/>
<div>
	<input type ="button" id="gobutton" onclick="go_button()" style="font-size:large" value="Start">
</div> <!-- button -->
<input type="hidden" name="simple" value="true"/>
<br/>
<script>
function go_button() {
	var email = $('#loginName0').val();
	var expected_email = '<%=newStudy.stats.emailid%>';
	if (expected_email != 'unk' && email != expected_email) {
		alert ("Sorry, the code you provided is not valid for this email address");
		muse.log ('Error: provided email addr = ' + email + ' expected = ' + expected_email);
		return false;
	}
	return muse.do_logins();
}
</script>

</div> <!-- boxorange -->
<div style="clear:both"></div>

</div> <!-- main -->

</body>
</html>

