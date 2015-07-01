<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>    
<% 	JSPHelper.logRequest(request); %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<title>Browsing Lens</title>
<jsp:include page="css/css.jsp"/>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
</head>
<body class="fixed-width">
<%if (!ModeConfig.isPublicMode()) { %>
<jsp:include page="header.jsp"/>
<% } else { %>
<p>
<% } %>
<div class="panel" style="padding:20px 5%">
<p>

<h2>INSTALLATION</h2>
You can install your personal browsing lens in either of the two ways below. The lens will automatically highlight terms (specifically, 
named entities) that are present in the Muse index as you browse the web. <p>

1. Firefox: First install the <a href="https://addons.mozilla.org/en-US/firefox/addon/greasemonkey">GreaseMonkey</a> extension into Firefox.
Then, click on <a href="js/muse-lens.user.js">this user script</a> to install the browsing lens plugin. 
<p>
Chrome: Click on <a href="http://mobisocial.stanford.edu/muse/lens.crx">this link</a> to install the extension into Chrome</a>.

The lens will automatically highlight terms on all pages as they are loaded.

<p>

- OR -

<p>

2. Firefox, Safari or Chrome: Drag the following link to your bookmarks toolbar: 
<%String path = HTMLUtils.getRootURL(request); %>
<a href="javascript:(function(){window.MUSE_URL = '<%=path%>'; S=document.createElement('SCRIPT'); S.type='text/javascript'; S.src='<%=path%>/js/muse-lens.user.js';document.getElementsByTagName('head')[0].appendChild(S);})();">My Lens</a> bookmarklet to your bookmarks toolbar. 

Ensure the toolbar is always visible. See the video below for Firefox. <br/>
<video width="640px" controls="" src="http://videos-cdn.mozilla.net/serv/labs/hackasaurus/ToolBar-MacOS-FF7.webm"/>
Note that, in this mode, <b>you have to click on the bookmarklet whenever you wish to highlight a page</b>. 
<p>

<h2>USING THE LENS</h2>

Be sure to save your session before quitting the browser. If you shut down Muse or restart your browser or computer, you can directly go to the saved session state
by selecting the session on the login screen.

The lens looks up names on the current page and highlights these names on the page if they are also present in the archive.
Currently, it uses two levels of highlights, strong and weak, depending on the score of the term in the archive.
You may also see some names with a red dotted underline, which means that entity was looked up in the index, but there
were no matches.
<p>
Click on a highlighted term to view the messages containing it. A preview of the first 5 messages with date, sender and subject is shown.
To view all the messages, including the entire bodies, click on the &quot;View Messages&quot; link at the bottom left, and 
the messages will be displayed in a new message browsing tab.
<p>
If an existing link on the page is highlighted in its entirely, clicking on it will open the message summaries instead of following the link. 
If you wish to follow the link instead, use Shift-Click (opens in a new window).
<p>
You can also click on the Hide/Show buttons at the top right to remove/display the callout at the bottom.

<h2>TROUBLESHOOTING</h2>
Just in case the lens interferes with your normal browsing experience on a page, you can 
temporarily disable it using the Tools &rarr; Greasemonkey option in Firefox. You can also 
go to Tools &rarr; Greasemonkey &rarr; Manage User Scripts and entire URL patterns to be included or excluded from the lens.

<% if (!ModeConfig.isPublicMode()) { %>
<h2>Advanced Options</h2>
Try importing your own and your friends' <a href="surface">Facebook and LinkedIn</a> profiles 
into the lens. These options are under test and may not always be reliable. Please save your session after the import is complete.
<p>
We have an <a href="test/mytimes.jsp">early application</a> that tries to personalize the NYTimes front page based on your personal archive.
<br/>
<hr/>
<br/>
You can also use other <a href="archivist-tools">archivist tools</a>.
<% } %>
</div>
<jsp:include page="footer.jsp"/>

</body>
</html>