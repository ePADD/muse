<%@ page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%
	JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8");
JSPHelper.logRequest(request);
MuseEmailFetcher m = (MuseEmailFetcher) JSPHelper.getSessionAttribute(session, "museEmailFetcher");
if (m == null)
{
	response.sendRedirect("index?noredirect=");
	return;
}
		
String mode = (String) JSPHelper.getSessionAttribute(session, "mode");

Accounts.updateUserInfo(request);
// re-read accounts again only if we don't already have them in this session.
// later we might want to provide a way for users to refresh the list of folders.
	
// we are already logged into all accounts at the point this is called
// we may not have finished reading the folders though.
	session.setMaxInactiveInterval(-1);
    // never let session expire

      response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
      response.setHeader("Pragma","no-cache"); //HTTP 1.0
      response.setDateHeader ("Expires", 0); //prevent caching at the proxy server
	  // remove existing status provider if any because sometimes status provider
	  // for prev. op prints stale status till new one is put in the session
      if (JSPHelper.getSessionAttribute(session, "statusProvider") != null)
		  session.removeAttribute("statusProvider");
      session.removeAttribute("loginErrorMessage");

	  // UI display of folders
      int numFoldersPerRow = 4;
      if (JSPHelper.getSessionAttribute(session, "fbmode") != null)
    	  numFoldersPerRow = 2;
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<title>Choose Folders</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery.tools.min.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/stacktrace.js"></script>
<script type="text/javascript" src="js/muse.js"></script>

<%if (!Util.nullOrEmpty(mode))
{
	%>
	<script type="text/javascript">muse.mode = '<%=mode%>'; </script>
	<%
}
%>

<script type="text/javascript" src="js/ops.js"></script> <!--  including ops.js early so we have ajax error action defined -->

<script type="text/javascript">
     var numFoldersPerRow = <%=numFoldersPerRow%>;
</script>
<script type="text/javascript" src="js/showFolders.js"></script>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
</head>
<body class="fixed-width">

<jsp:include page="header.jsp"/>

<div class="panel">

<br/> <!--  some space at the top -->

<div id="div_main" style="min-width:400px" class="folderspage">
<form id="folders">
	<div align="center" style="width:95%">

<%
	int nAccounts = m == null ? 0 : m.getNAccounts();
	JSPHelper.log.info ("Muse has " + nAccounts + " accounts");
	// we could parallelize the fetching of folders and counts for each fetcher at some point
	// need to worry about displaying multiple statuses (statii ?) from each fetcher
	// and aborting the others if one fails etc.
	for (int accountIdx = 0; accountIdx < nAccounts; accountIdx++)
	{
		String accountName = m.getDisplayName(accountIdx);
		// 2 divs for each fetcher: the account header and a div containing the actual folders
		%> <div>
			    <div class="account" style="width:100%" id="<%=accountName%>">
    				<div class="accountHeader panel-header">
	    				<%=accountName%>
    				</div>
		<hr/>
    				<div id="accountBody-<%=accountIdx%>" style="width:100%" class="accountBody folders-div">
		    	    	<table align="center">
	    				<tbody class="foldersTable">
							<!-- ajax will fill in stuff here -->
	    				</tbody>
	    				</table>
    				</div>
    			</div>
   			</div>
   			<%=	accountIdx < nAccounts-1?"<br/>":""%>
		<%
	}

	%> <hr/>
 		<div style="margin-top:5px">
			 		
			<div id="div_controls" class="toolbox">
				<div style="float:left"><span class="db-hint">Select some folders. Message counts for each folder in parantheses.</span></div>
				<div style="float:right">
				<% String checked = "";;
					if (!"search".equals(JSPHelper.getSessionAttribute(session, "mode")) && !"games".equals(JSPHelper.getSessionAttribute(session, "mode"))) { 
						checked = "checked"; // (request.getParameter("downloadAttachments") != null) ? "checked" : "";
				%>
				<% } %>
				<INPUT TYPE=hidden ID="downloadAttachments" NAME="downloadAttachments" <%=checked%>/>
				</div>
				<div style="clear:both"></div>
				<div style="float:right">
				<button class="tools-pushbutton" onclick="muse.submitFolders();return false;">Go</button>
				</div>
				<br/>				
			</div> <!--  div_controls -->

	<div align="left" style="margin-top:5px">
		<span id="select_all_folders" class="clickableLink" onclick="toggle_select_all_folders(this);">Select all folders</span> &bull;
		<span class="clickableLink" onclick="muse.toggle_advanced_panel();" id="div_advanced_text">Advanced controls</span>
		<script type="text/javascript">function go_to_logins() { window.location = "index?noredirect";}</script>
	</div>
	<div>
	<%@include file="div_advanced.jsp"%><br/>
	</div>
	</div>
</form>
</div>
</div> <!-- div_main -->
<script type="text/javascript" src="js/statusUpdate.js"></script>

<%@include file="div_status.jsp"%>

	<%
	for (int accountIdx = 0; accountIdx < nAccounts; accountIdx++)
	{
		String accountName = m.getDisplayName(accountIdx);
		String sanitizedAccountName = accountName.replace(".", "-");

		boolean success = true;
		String failMessage = "";

    	boolean toConnectAndRead = !m.folderInfosAvailable(accountIdx);
        %>
        <script type="text/javascript">display_folders('<%=accountName%>', <%=accountIdx%>, true);</script>
		<%
        out.flush();

    	if (toConnectAndRead)
  	    {
    		// mbox can only be with "desktop" mode, which means its a fixed cache dir (~/.muse/user by default)
    		// consider moving to its own directory under ~/.muse
    		String mboxFolderCountsCacheDir = Sessions.CACHE_DIR; 
    		JSPHelper.log.info ("getting folders and counts from fetcher #" + accountIdx);
           	// refresh_folders() will update the status and the account's folders div
            m.readFoldersInfos(accountIdx, mboxFolderCountsCacheDir);
  	    }
	} // end of fetcher loop

    JSPHelper.logRequestComplete(request);
    %>

<script type="text/javascript">
// when the page is ready, fade out the spinner and fade in the folders
$(document).ready(function() {
    $('#div_main').fadeIn("slow");
    updateAllFolderSelections(); // some folders may be in selected state when page is refreshed
    var newdiv = document.createElement('div');
    newdiv.setAttribute('id', 'folders-completed');
    var div_main = document.getElementById('div_main');
    div_main.appendChild(newdiv);
});
</script>
</div> <!--  panel -->
<jsp:include page="footer.jsp"/>
</body>
</html>
