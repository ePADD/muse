<%@page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="com.google.gson.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="org.apache.commons.logging.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.exceptions.*"%>

<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
<script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/ops.js"></script>

<%
String googleClientId = null;
if(request.getLocalPort() == 8080) {
	googleClientId = "1058011743827.apps.googleusercontent.com"; // Client ID for development environment
} else {
	if (!"localhost".equals(request.getServerName()) /* this part for debugging only, when running on localhost but with server mode */
	 	&& !JSPHelper.runningOnLocalhost(request))
		googleClientId = "1072171428245-lj239vjtemn7cgstafptk0c46c20kgih.apps.googleusercontent.com"; // this is for the muse installed at https://muse.stanford.edu:8443/muse
	else
		googleClientId = "1072171428245.apps.googleusercontent.com"; // this is for local host "1058011743827-t8e0fjt1btmujjesoaamgequ5utf4g77.apps.googleusercontent.com";	
}
%>
<script src="https://apis.google.com/js/client.js"></script>
<script>


function googleOAuth(idx) {
    var config = {
        'client_id': '<%=googleClientId%>',
        'scope': 'https://mail.google.com/ https://www.googleapis.com/auth/userinfo.email',
        'response_type': 'token'
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
}; 
$(document).ready(function() {
	$('input[type=password]').clickOnEnter($('#gobutton')[0]);
});
</script>

<div class="simple-login">

<%
JSPHelper.logRequest(request);
session.setMaxInactiveInterval(-1);
List<List<String>> tbirdAccounts = ThunderbirdUtils.getThunderbirdAccounts();
String linkAfterLoadSession = ("search".equals(JSPHelper.getSessionAttribute(session, "mode"))) ? "createEmailLinksCSE.jsp" : "info";

// set up tbirdLocalFolder if present
String tbirdLocalFoldersDir = null;
if (tbirdAccounts != null && tbirdAccounts.size() > 0) { 
	for (int tbirdIndex = 0; tbirdIndex < tbirdAccounts.size(); tbirdIndex++)
	{
		List<String> account = tbirdAccounts.get(tbirdIndex);
		String acctName = account.get(0);
		if ("Local Folders".equals (acctName)) // um, not sure if this is different for i18n accounts!
			tbirdLocalFoldersDir = account.get(6);
	}
}

// make the tbird account details available in JS
Gson gson = new Gson();
String json_for_tbird_accounts = gson.toJson(tbirdAccounts);
// each tbirdAccount is an array of elements, see ThunderbirdUtils for the sequence. #unfortunate
%>

<script>var tbirdAccounts = <%=json_for_tbird_accounts%>;</script>

<script type="text/javascript">
/** add to the UI a simple emailaddr/passwd input section */
muse.addSimpleLoginAccount = function()
{
	// no need for account-add controls in any account. do this before cloning
	$('.account-add').remove();

	// there is a series of login accounts, we just clone the last one and remove its login fields
	var $logins = $('.login-account');
	var $clone = $($logins[0]).clone();
	$('.login-fields', $clone).remove();
	var $select = $('select', $clone);
	$select.attr('name', 'accountType' + $logins.length);
	$select.attr('id', 'accountType' + $logins.length);

	// append the clone after the last one
	$clone.insertAfter($logins[$logins.length-1]);
	$('<br/>').insertAfter($logins[$logins.length-1]);
}

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
	var is_last = (idx == $('.login-account').length-1);
	
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
		var $message = $('<div id="message' + idx + '" style="font-style: italic">Ensure popups are enabled for Oauth</div>');
		var $login = $('<input class="input-field" type="hidden" placeholder="email" name="loginName' + idx + '"id="loginName' + idx + '" />');
		var $password = $('<input class="input-field password" type="hidden" placeholder="password" name="password' + idx + '"id="password' + idx + '" size="20"/>');
		var $spinner = $('<img id="spinner' + idx + '" src="images/spinner-white.gif" width="15" style="margin-left:10px;visibility:hidden"><br/>');
		$login_fields.append($message).append($login).append($password).append($spinner);
		googleOAuth(idx);
	}
	else if ("email" == type)
	{
		var $login = $('<input class="input-field" type="text" placeholder="email" name="loginName' + idx + '"id="loginName' + idx + '" size="20"/><br/>');
		var $password = $('<input class="input-field password" type="password" placeholder="password" name="password' + idx + '"id="password' + idx + '" size="20"/>');
		var $server = $('<input style="display: none" class="input-field" type="text" placeholder="server" name="server' + idx + '"id="server' + idx + '" size="20"/>');
		$login_fields.append($login).append($password).append($server).append($spinner);
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
	else if (type == 'tbirdLocalFolders')
	{
		<% if (tbirdLocalFoldersDir != null) { %>
		// the field is implicit, so make it hidden
		<% String escapedLocalFolders = tbirdLocalFoldersDir.replaceAll("\\\\", "\\\\\\\\"); // need to to this to convert single \ to double \\ %>
		tbirdLocalFolders = '<%=escapedLocalFolders%>'; // replace single backslash with double backslash
		var $dirs = $('<input class="input-field" value="' + tbirdLocalFolders + '" type="hidden" placeholder="path to folder with mbox files" name="mboxDir' + idx + '"id="mboxDir' + idx + '"/>');
		$login_fields.append($dirs);
		$login_fields.append('<div style="font-size:small" title="' + tbirdLocalFolders + '"><i><%=Util.ellipsize(escapedLocalFolders,30)%></i></div>');
		<% } %>
	}
	else if (type.indexOf("Thunderbird") == 0) // Thunderbird a/c types are things like Thunderbird: hangal@xenon.stanford.edu
	{
		var tbirdAccountIdx = tbirdAccountTypeToIdx[type];
		var tbirdAccount = tbirdAccounts[tbirdAccountIdx];
		// hidden fields for thunderbird account types based on the details in tbirdAccounts
		
		var $server = $('<input class="input-field" type="hidden" name="server' + idx + '"id="server' + idx + '" value="' + tbirdAccount[1] + '"/>');
		var $protocol = $('<input class="input-field" type="hidden" name="protocol' + idx + '"id="protocol' + idx + '" value="' + tbirdAccount[2] + '"/>');
		var $fcc = $('<input class="input-field" type="hidden" name="defaultFolder' + idx + '"id="defaultFolder' + idx + '" value="' + tbirdAccount[6] + '"/>');
		var $port = $('<input class="input-field" type="hidden" name="port' + idx + '"id="port' + idx + '" value="' + tbirdAccount[7] + '"/>');
		// note: we don't really support custom ports in the backend
		var $login = $('<input class="input-field" type="text" name="loginName' + idx + '"id="loginName' + idx + '" size="20" value="' + tbirdAccount[3] + '"/><br/>');
		var $password = $('<input class="input-field password" type="password" placeholder="password" name="password' + idx + '"id="password' + idx + '" size="20"/>');
		var $spinner = $('&nbsp;<img id="spinner"' + idx + ' src="images/spinner-white.gif" width="15" style="margin-left:10px;visibility:hidden"><br/>');
		$login_fields.append($server).append($protocol).append($fcc).append($port).append($login).append($password).append($spinner);
	}
	$account.append($login_fields);
	
	// add the + for more accounts if needed
	if (is_last && type != 'none' && $('.account-add', $account).length == 0) 
	{
		var $plus = $('<div class="account-add" style="position:relative">' +
                '<img title="Add an account" type="image" src="images/add.png" width="12px" height="12px" alt="add an account" id="add an account"' +
                'style="position:absolute; left: 12em; top:10px" onclick="muse.addSimpleLoginAccount()"/> </div>');	
		$account.append($plus);
	}
	else if (is_last && type == 'none')
	{
		$('.account-add', $account).remove();
	}
	set_sent_messages_only();
}

// re-evaluate whethere sent folders should be on...
function set_sent_messages_only()
{
	var $accountTypes = $('.accountType');
	var show_sent_only_option = true;
	$accountTypes.each(function(i, $o) { var val = $(this).val(); muse.log ('loginType: ' + val); if (val != 'email' && val != 'gmail' && val != 'yahoo' && val.indexOf('Thunderbird') < 0 && val.indexOf('stanford') < 0 && val != 'gapps') show_sent_only_option = false; });
	if (!show_sent_only_option) {
		$('#sent-messages-only').attr('checked', false);
		$('#sent-only-div').hide();
	} else {
		$('#sent-messages-only').attr('checked', true);
		$('#sent-only-div').show();
	}
	muse.log ('sent-only =  ' + show_sent_only_option);
}
</script>

<% 
// session options
String s = request.getParameter("session");
if (!Util.nullOrEmpty(s)) { 
	JSPHelper.log.info ("Loading session: " + session);
	%>
	<div class="panel" style="padding:50px 10px 50px 10px;">
		<h2>Loading session <%=s%> <img id="loadSessionSpinner" style="visibility:hidden" width="15" src="images/spinner-white.gif"/></h2>
	</div>
	
	<script type="text/javascript"> 
		muse.load_session('<%=s%>', '#loadSessionSpinner', '<%=linkAfterLoadSession%>');
	</script>
<% 	return;
} %>

<!--  the form target is folders but if we decide we want simple viz, the simpleLoginFormSubmit will return false and just do its thing. -->
<div id="header" style="height:45px;padding-top:5px;font-weight:normal;text-align:center;position:relative">
<a href="/muse">
<img style="position:absolute;top:0px;left:0px" width="200px" src="images/muse-logo.png"/>
</a>

<div id="rightlinks" style="position:absolute; right: 0px; height:45px;font-size:16px;font-weight:normal;">
	<ol class="topnav">
		<li>
			<img class="nav-item" height="25px" src="images/question-mark-icon.png" title="Help" onclick="window.location='/muse/help';">
		</li>

		<li>
			<img class="nav-item" height="25px" style="position:relative;top:3px" src="images/comment-icon.png" title="Feedback" onclick="window.location='https://docs.google.com/spreadsheet/viewform?formkey=dF9yYkhfbEdJNnVYaGhaYUdZaG1EeXc6MQ';">
		</li>
		
		<li>
		    <img class="nav-item" title="Settings" height="25px" onclick="window.location='/muse/settings'" src="images/gears-icon.png"/>
		<li>
	</ol>
</div>
</div> <!--  header -->

<div id="main" style="position:relative;width:1200px;overflow:hidden">
<div id="simple_login" style="float:left;position:relative;overflow:hidden">
<div class="rounded panel shadow" style="position:relative;overflow:hidden"">

<div class="tagline" style="float:left; margin-left:20px; margin-top:20px; font-size: 30px; color: #FFF">
Revive Precious Memories Using Email.
</div>

<div class="simple-login-half-column1" style="float:left">
<br/><br/>

<% // only assure about privacy if it's true ;-)
if (JSPHelper.runningOnLocalhost(request)) { %>
	<div><b>PRIVACY</b><br/>
		Muse is running on your own computer, so you can be assured about privacy. (<a href='#' onclick="$('#privacy_more').toggle(); toggleMoreAndLess(this);">More</a>)

		<div id="privacy_more" style="display:none">
			<div class="shadow" style="display:inline">
			<br/>
			<img class="shadow" width="250" src="images/localhost-tip.png"/><br/>
			</div>
			<br/>The <b>localhost</b> address in your browser's location bar indicates that you are connected to your own computer.
		</div>
	</div>
	<br/>
<% } 

   if ("search".equals(JSPHelper.getSessionAttribute(session, "mode"))) { %>
	<b>HOW DOES MUSE WORK?</b><br/>
		Muse extracts links from your sent message folders by default and creates a custom search engine. You can also specify other folders.
<% } else { %>
	<b>HOW DOES MUSE WORK?</b><br/>
		Muse analyzes sent message folders by default. You can also select other folders. See the <a href="help">help page</a> for more information.
<% } %>

</div> <!--  simple-login-half-column1 -->


<div class="simple-login-half-column" style="float:right;width:500px">
<div class="login-method-box-centered" style="position:relative;margin-left:250px;padding-left:50px;border-bottom: solid 1px rgba(255,255,255,0.2);border-left: solid 1px rgba(255,255,255,0.2);">

<% String error = (String) JSPHelper.getSessionAttribute(session, "loginErrorMessage");
if (!Util.nullOrEmpty(error))
{
	%>
	<script type="text/javascript">$.jGrowl('<span class="growl"><%=Util.escapeHTML(error)%></span>');</script>
	<% session.removeAttribute("loginErrorMessage");
}
%>
   
<div class="login-account">
<select class="accountType" name="accountType0" id="accountType0">
<option value="none">Account type</option>
<option value="gmail">Gmail or Google apps</option>
<option value="email">Email address</option>
<option value="yahoo">Yahoo</option>
<option value="live">Hotmail/Microsoft Live</option>
<%if (!Util.nullOrEmpty(tbirdAccounts)) { 
	if (tbirdAccounts != null && tbirdAccounts.size() > 0) { %>
	<script  type="text/javascript">var tbirdAccountTypeToIdx = {};</script>
	<%
		for (int tbirdIndex = 0; tbirdIndex < tbirdAccounts.size(); tbirdIndex++) {
			List<String> account = tbirdAccounts.get(tbirdIndex);
			String acctName = account.get(0);
			if ("Local Folders".equals (acctName)) // um, not sure if this is different for i18n accounts!
				continue;
			String optionValue = "Thunderbird: " + acctName;
			%>
				<option value="<%=optionValue%>"><%=optionValue%></option>
				<script type="text/javascript">
					tbirdAccountTypeToIdx['<%=optionValue%>'] = <%=tbirdIndex%>;
				</script>
			<%
		}
	}
}

if (!Util.nullOrEmpty(tbirdLocalFoldersDir)) { %>
	<option value="tbirdLocalFolders">Thunderbird Local folders</option>
<% } %>
<option value="imap">IMAP server</option>
<option value="pop">POP server</option>
<option value="mbox">Mbox files</option>
</select>
<br/>
</div>
<br/>
	
<table>
<tr> <!--  last line of the table with the stuff other than login boxes -->
<td>
<div id="sent-only-div" style="display:none">
<input id="sent-messages-only" name="sent-messages-only" type="checkbox"/><span class="db-hint" title="Automatically process the sent mail folder. Uncheck to select folders yourself." >Use only sent mail folders</span>
</div>
<% boolean downloadAttachments = !"false".equals(request.getParameter("downloadAttachments")); %>
<input id="downloadAttachments" name="downloadAttachments" type="hidden" value="<%=Boolean.toString(downloadAttachments)%>"/> 
 				
 	<!--ALT EMAIL BEGIN-->
			<div class="alt-email-addrs" style="display:inline;line-height:1em">
			<p/>
			<span class="db-hint" title="We need to know all your email addresses to know which email is outgoing vs. incoming.">Your email aliases:</span>
			<br/>
			<%
			String userEmails = edu.stanford.muse.util.ThunderbirdUtils.getOwnEmailsAsString(tbirdAccounts);
			String userEmailsColor = "black";
			if (userEmails.length() == 0)
			{
				userEmailsColor = "gray";
			}
			%>
		    <span class="db hint">
		    <input type="text" placeholder="other@email.com" id="alternateEmailAddrs" style="width:200px; padding:5px;color:<%=userEmailsColor%>" onClick="textFieldClicked(this);" name="alternateEmailAddrs" value ="<%=userEmails%>"/>
		    <%
		    int days = HTMLUtils.getIntParam(request, "days", -1);
		    if (days > 0) { %>
				<input style="display:none" id="dateRange" name="dateRange" value="<%=edu.stanford.muse.email.Filter.getDateRangeForLastNDays(days) %>"/>
		    <% } %>
		    </span>
</td>
</tr>
</table>
<input id="gobutton" onclick="javascript:muse.do_logins()" type ="button" style="color:#FFF; width:100px;height:40px;position:relative; left:80px; " value="muse on" class="tools-button"> abc</button>
<input type="hidden" name="simple" value="true"/>
<br/>

<br/>

</div>

</div> <!--end-->

<div style="clear:both"></div>
</div>
</div>
</div> <!-- main -->

