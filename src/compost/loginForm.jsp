<%@ page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="org.apache.commons.logging.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/jquery.scrollTo-1.4.2-min.js"></script>
<script>
/* doesn't work because #loginForm's cannot get focus :(
$(document).ready( function() {
	alert ($('#loginForm').length + ' '  + $('#gobutton').length);
	$('#loginForm').clickOnEnter($('#gobutton')[0]);
});
*/
/** add to the UI a simple emailaddr/passwd input section */
muse.addServerLoginAccount = function()
{
	// there is a series of login tables, we just clone the first one and append it after the last

	var logins_jq = $('.server-login-details');
	var last_login_jq = logins_jq[0];
	var clone = $(last_login_jq).clone();
	var n_server_accounts = logins_jq.length;
	var nAccounts = n_server_accounts + $('#tbird-logins input[name*="loginName"]').length;
	muse.log ('adding one more account to ' + nAccounts + " existing");
	
	// fix the fields of the clone, to have name and id emailAddress<n> and password<n>
	var clone_input_fields = $('input', clone);
	$(clone_input_fields[0]).attr ('id', 'server' + nAccounts);
	$(clone_input_fields[0]).attr ('name', 'server' + nAccounts);
	$(clone_input_fields[0]).val ('');
	$(clone_input_fields[1]).attr ('id', 'loginName' + nAccounts);
	$(clone_input_fields[1]).attr ('name', 'loginName' + nAccounts);
	$(clone_input_fields[1]).val ('');
	$(clone_input_fields[2]).attr ('id', 'password' + nAccounts);
	$(clone_input_fields[2]).attr ('name', 'password' + nAccounts);
	$(clone_input_fields[2]).val ('');

	var clone_img_fields = $('img', clone);
	$(clone_img_fields[0]).attr ('id', 'spinner' + nAccounts);
	
	// append the clone after the last one
	clone.insertAfter(logins_jq[logins_jq.length -1]);
	$('<hr/>').insertAfter(logins_jq[logins_jq.length -1]);
};

</script>

<div id="loginForm" style="margin:auto;max-width:1200px;"> <!--  > 1000, otherwise causes wrap around with 3 columns -->

<%
session.setMaxInactiveInterval(-1);
//print initial config.
Log log = LogFactory.getLog("configuration");
JSPHelper.checkContainer(request);
log.info ("Logging: Info mode is on");
log.debug ("Logging: debug mode is on");
log.trace ("Logging: trace mode is on");

%>
<div id="header" style="height:45px;padding-top:5px;font-weight:normal;text-align:center;position:relative">
<a href="/muse">
<img style="position:absolute;top:0px;left:0px" width="200px" src="images/muse-logo.png"/>
</a>
<div id="rightlinks" style="position:absolute; right: 0px; height:45px;font-size:16px;font-weight:normal;">
	<ol class="topnav">
		<li>
			<img height="25px" src="images/question-mark-icon.png" title="Help" onclick="window.location='/muse/help';">
		</li>

		<li>
			<img height="25px" style="position:relative;top:3px" src="images/comment-icon.png" title="Feedback" onclick="window.location='https://docs.google.com/spreadsheet/viewform?formkey=dF9yYkhfbEdJNnVYaGhaYUdZaG1EeXc6MQ';">
		</li>
		
		<li>
		    <img title="Settings" height="25px" onclick="window.location='/muse/settings'" src="images/gears-icon.png"/>
		<li>
	</ol>
</div>
</div> <!--  header -->

<div id="expanded_login" class="rounded panel" style="float:left;padding-bottom:10px;position:relative">
&nbsp;&nbsp;
<div class="login-error-message" style="padding:0px; margin:0px">
<% String error = (String) JSPHelper.getSessionAttribute(session, "loginErrorMessage");
if (error != null && !"".equals(error))
{
	session.removeAttribute("loginErrorMessage");
	out.println(Util.escapeHTML(error));
}
%>
</div>

<table cellpadding="0px" cellspacing="0px" style="width:1100px;margin:auto">
<!--  <tr><td align="center"><img width="120" src="images/muse-logo.png"/><br/></td></tr>  -->
<tr>

<td>

<div class="db-hint" style="padding: 0px 0px 0px 20px;line-height:1.6em;">Choose your own email server or files on your local system.
<br/>Please provide information for one or more accounts and enter all your own addresses in the box provided below.
<%
//print the thunderbird logo and accounts only if installed
List<List<String>> tbirdAccounts = ThunderbirdUtils.getThunderbirdAccounts();

if (tbirdAccounts != null && tbirdAccounts.size() > 0) { %>
<br/>
<!-- Leave password fields blank for accounts you do not wish to use. -->
<% } %>
</div><br/>


<ul>
<%
int acctIndex = 0;

if (tbirdAccounts != null && tbirdAccounts.size() > 0)
{
	%>
	<li id="tbird-logins" class="login-method-box shadow">
	<div>
	<%
//		<a title="Thunderbird accounts" href="http://www.mozillamessaging.com/en-US/thunderbird/">
//				<img class="login-method-icon" style="vertical-align:middle" width="64px" src="images/thunderbird.png"></img>
//		</a>
	%>
		<span class="login-method-title login-method-text"><%=Util.pluralize(tbirdAccounts.size(), "Thunderbird account")%><br/>
		<span class="db-hint">Click on an account name to login.</span>
		</span>
			<br/>
			

	<table>
	<%

	for (int tbirdIndex = 0; tbirdIndex < tbirdAccounts.size(); tbirdIndex++)
	{
		List<String> account = tbirdAccounts.get(tbirdIndex);
		String acctName = account.get(0);
		if ("Local Folders".equals (acctName)) { %>
		 	<tr><td>
				<input class="bigcheck" type="checkbox" name="localFoldersChecked"/> <i>Local Folders</i>
				<input type="hidden" name="localFoldersDir" value="<%=account.get(6)%>"/>
				<br/>
			</td></tr>
		<% } else { %>
			<tr><td colspan="2" style="margin-bottom: 10px;">
			<i><b><span class="tbirdAccountName clickableLink" title="Click to login" ><%=acctName%></span></b></i>
			  		<div id="tbirdLoginDiv<%=acctIndex%>">
						<table style="display:none">
							<tr>
								<td> 
								<input type="hidden" name="server<%=acctIndex%>" id="server<%=acctIndex%>" value="<%=account.get(1)%>"/>
								<input type="hidden" name="protocol<%=acctIndex%>" id="protocol<%=acctIndex%>" value="<%=account.get(2)%>"/>
								<input type="hidden" name="defaultFolder<%=acctIndex%>" id="defaultFolder<%=acctIndex%>" value="<%=account.get(2)%>"/>
								<input placeholder="username" class="input-field" type="text" name="loginName<%=acctIndex%>" id="loginName<%=acctIndex%>" size="12" value="<%=account.get(3)%>"/>
								</td>
							</tr>
							<tr>
								<td> <input placeholder="password" class="input-field" type="password" name="password<%=acctIndex%>" id="password<%=acctIndex%>" size="12"></td>
								<td valign="middle"><img id="spinner<%=acctIndex%>" src="images/spinner.gif" width="15" style="visibility:hidden"></td>
							</tr>
						</table>
					</div> <!--  end tbird login -->
			</td></tr>
			<%
			acctIndex++;
		}
	} %>
	</table>
	</div>
</li>
<%
}
%>

<li class="login-method-box shadow" id="customSetup">
	<div style="position:relative">
	<div class="login-method-title login-method-text">IMAP/POP server</div>
	<br/>
	<br/>
	<table class="server-login-details">
		<tr><td> <input placeholder="mail server" title="server:port" class="input-field" type="text" name="server<%=acctIndex%>" value="" id="server<%=acctIndex%>"/></td> </tr>
		<tr> <td> <input placeholder="login" class="input-field" type="text" name="loginName<%=acctIndex%>" id="loginName<%=acctIndex%>"/></td></tr>
		<tr> <td> <input placeholder="password" class="input-field" type="password" name="password<%=acctIndex%>" id="password<%=acctIndex%>"></td>
			 <td valign="middle"><img id="spinner<%=acctIndex%>" src="images/spinner.gif" width="15" style="visibility:hidden"></td>
		</tr>
	</table>
	<img title="add an account" width="12px" height="12px" onclick="javascript:muse.addServerLoginAccount()" style="position:absolute;right:0px; bottom:-25px;font-size:60%" src="images/add.png"/>
	</div>
</li>
	<% if ("localhost".equals(request.getServerName())) { %>
<li class="login-method-box shadow">
    <div class="login-method-title login-method-text">Folders with email files</div>
    <p>
    <div style="margin-left:10px;">
	<input class="input-field" type="text" name="mailDirs" id="mailDirs"/><br/>
	<span class="db-hint">(mbox format. separate by commas)</span>
	</div>
</li>
	<% } %>
</ul>
<div style="clear:both"></div>
<div style="line-height:1em">
<span class="db-hint" title="We need to know all your email aliases to know which email is outgoing vs. incoming">Your email aliases<br/></span>
<%
String userEmailsAndNames = edu.stanford.muse.util.ThunderbirdUtils.getOwnEmailsAsString(tbirdAccounts);
String userEmailsColor = "black";
if (userEmailsAndNames.length() == 0)
{
	userEmailsColor = "gray";
}
%>
<input type="text" id="alternateEmailAddrs" style="color:<%=userEmailsColor%>" onClick="textFieldClicked(this);" name="alternateEmailAddrs" size="60" value ="<%=userEmailsAndNames%>"></input>
&nbsp;&nbsp;
</div>
</td>
</tr>
</table>
<script type="text/javascript">

// toggle for show/hide of tbird divs
$(document).ready(function() {
	$('.tbirdAccountName').click(function(e) {
		var $tbird_account_stuff = $(e.target).closest('td'); // td ancestor contains all the html for this account
		var $login_stuff = $('table', $tbird_account_stuff); // show/hide the table under the parent
		$login_stuff.toggle();
	});
});

function go_handler()
{
//	$.post('killSession.jsp', function() { muse.log ('Go button pressed, killed session'); });
	muse.do_logins();
}
</script>
<input id="gobutton" onclick="javascript:go_handler()" type ="button" style="position:absolute;left:900px;bottom:20px;color:#FFF; width:120px;height:40px;" value="sign in&nbsp;&nbsp;&nbsp&rarr;" class="tools-button"/> 

</div>

<div id="footer" style="align:center; min-width: 1000px; max-width:1000px">
<%@include file="footer.jsp"%>
</div>

<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>

<script type="text/javascript">

// if the UI on login form got touched, remember to invalidate existingfOlders and counts in the session.
// if it wasn't touched, we should let user re-visit the login page and then click go and get back
// original state without having to reread folders (which can be expensive)
function showTbirdLogin(x, accountIndex)
{
	if (x.checked) {
		$('#tbirdLoginDiv' + accountIndex).fadeIn('slow');
	}
}

</script>


