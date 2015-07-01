<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="org.apache.log4j.*"%>
<%
	JSPHelper.logRequest(request);
      response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
      response.setHeader("Pragma","no-cache"); //HTTP 1.0
      response.setDateHeader ("Expires", 0); //prevent caching at the proxy server
	  // remove existing status provider if any because sometimes status provider
	  // for prev. op prints stale status till new one is put in the session
      if (JSPHelper.getSessionAttribute(session, "statusProvider") != null)
		  session.removeAttribute("statusProvider");

      int numFoldersPerRow = 4;
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
<title>Muse Settings</title>
<jsp:include page="css/css.jsp"/>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
<script type="text/javascript" src="js/stacktrace.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script>
// simple func to refresh the current page (typically after settings have changed)
function refresh_page() { window.location = 'settings'; }
</script>

</head>
<body class="fixed-width"> 
<jsp:include page="header.jsp"/>

<div class="panel shadow" >

<div style="padding:20px 50px 20px 50px">

<p>

<h2>User Interface</h2>
<p>
<% if (JSPHelper.RUNNING_ON_JETTY) { %>
<button title="The Muse server continues running in the background even if you exit your browser. Click on this button to stop the server completely." class="tools-pushbutton" onclick="javascript:window.location='exit.jsp';">Stop Muse entirely</button> 
<p>
<% } %>
Use Theme 
<% String currentTheme = (String) JSPHelper.getSessionAttribute(session, "style"); %>
<select style="font-size:10pt" onchange="muse.setConfigParam('style', event.target.value); window.setTimeout(muse.refresh_page, 1000);">
	<option <%="blue".equals(currentTheme) ? "selected":"" %> value="blue">Blue</option>
	<option <%="green".equals(currentTheme) ? "selected":"" %> value="green">Green</option>
	<option <%="red".equals(currentTheme) ? "selected":"" %> value="red">Red</option>
	<option <%=(currentTheme == null || "default".equals(currentTheme)) ? "selected":"" %> value="default">Default</option>
</select>

<br/>
<br/>

<%
String jog = (String) JSPHelper.getSessionAttribute(session, "jogDisabled"); 
boolean jogDisabled = "true".equals(jog); %>
<input id="jog_control" <%=(!jogDisabled)?"checked":""%> type="checkbox" onchange="jog_control(event)"/> Enable Jog dial for browsing messages

<script type="text/javascript">
function jog_control() {
	var enabled =  $('#jog_control').is(':checked');
	muse.setConfigParam('jogDisabled', !enabled, enabled?'Jog dial enabled':'Jog dial disabled');
}
</script>
<br/>

<% boolean IA_links = "true".equals(JSPHelper.getSessionAttribute(session, "IA_links")); %>
<input onchange="IA_links()" id="IA_links" type="checkbox" <%=IA_links?"checked":""%>/>&nbsp;Point links in messages to the Internet Archive's version of the page as of the message date.
<script type="text/javascript">
function IA_links() {
	muse.setConfigParam('IA_links', $('#IA_links').is(':checked'));
}
</script>
<p>

<%!
private static final String[] levels = new String[] {"ERROR", "WARN", "INFO", "DEBUG", "FINE"};

private static String htmlForLoggingSelectBox(String clazz, String currentLevel) 
{
	StringBuilder result = new StringBuilder();
	result.append("<select class=\"" + clazz + "\" onchange=\"muse.changeLoggingLevel(event)\">\n");
	// a huge pun on the class attr
	for (String level: levels) 
		result.append("<option " + (currentLevel.equalsIgnoreCase(level) ? "selected=\"selected\" ":"") + "value=\"" + level + "\">" + level + "</option>\n");		
	result.append("</select>\n");
	return result.toString();
}
%>

<%
if (!ModeConfig.isMultiUser())
{ 
%>

<a href="http://www.avantstar.com/metro/home/products/quickviewplusstandardedition" target="_blank">Quick View Plus</a> lets you view attachments in legacy formats (Windows-only). 
If installed, give the path to the Quickview executable below:
<br>
<% 
String quickDir = (String) JSPHelper.getSessionAttribute(session, "qvpDir"); 
if(quickDir == null)
	session.setAttribute("qvpDir", "C:\\Program Files\\Quick View Plus\\Program\\qvp64.exe");
	quickDir = "C:\\Program Files\\Quick View Plus\\Program\\qvp64.exe";
%>
<input size="60" id="qvpDir" name="value" placeholder="<%=(quickDir)%>" />
<button class="tools-pushbutton" title="" onclick="muse.setConfigParam('qvpDir', $('#qvpDir').val()); ">Save</button>
<p>
<h2>Archive management</h2>
<br/>
<% 
if (!ModeConfig.isMultiUser()) {
	String baseDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir"); 
	if(baseDir == null)
		baseDir = "";
	%>
	Base folder: 
	<input size="60" id="cacheDir" name="value" placeholder="<%=(baseDir)%>" %>
	<!--  <button class="tools-pushbutton" title="" onclick="muse.setConfigParam('cacheDir', $('#cacheDir').val()); ">Save</button> -->
	<button class="tools-pushbutton" title="" onclick="redirectWithNewCacheDir()">Save</button>
<% } %>
<p>
	<button title="Muse maintains a cache in the &lt;HOME&gt;/.muse folder to avoid having to repeatedly fetch email from external servers. We recommend that you delete this cache if you are not going to run Muse again. You can also manually delete this folder." class="tools-pushbutton" onclick="muse.clearCache()">Clear archive</button>
	<img id="clearCacheSpinner" style="display:none;position:relative;top:3px" width="15" src="images/spinner.gif"/>
<p> 
<a href="edit-correspondents">Edit address book</a>

<p>
<% Collection<String> groupConfigs = GroupsConfig.list((String) JSPHelper.getSessionAttribute(session, "cacheDir"));
if (!Util.nullOrEmpty(groupConfigs))
{
	%>
	
	Delete groups:
	<img id="deleteGroupingSpinner" style="visibility:hidden" width="15" src="images/spinner.gif"/>
		<%
	JSPHelper.log.info (Util.pluralize(groupConfigs.size(), "grouping"));
	int i = 0;
	for (String gc: groupConfigs)
	{
		out.println ("<a href=\"#\" onclick=\"javascript:muse.delete_grouping('" + gc + "', '#deleteGroupingSpinner', 'settings');\">" + gc + "</a>&nbsp;"); // redirect back to this page
		i++;
		if (i < groupConfigs.size())
			out.println ("&bull; &nbsp;\n");
	}
}
else
	out.println ("(No saved grouping)");
%>
<br/>

<p>


<script>
function redirectWithNewCacheDir() { window.location = '/muse/?cacheDir=' + $('#cacheDir').val(); } // note: this path should includes the /user subdir if used
</script>
<%
	Logger rootLogger = Logger.getRootLogger();
	Logger abLogger = Logger.getLogger("edu.stanford.muse.email.AddressBook");
	Logger groupsLogger = Logger.getLogger("edu.stanford.muse.groups.Grouper");
	Logger lensLogger = Logger.getLogger("edu.stanford.muse.webapp.Lens");
	String rootLevel = "INFO", abLevel = "INFO", groupsLevel = "INFO", lensLevel = "info";
	if (rootLogger != null)
		rootLevel = rootLogger.getEffectiveLevel().toString();
	if (abLogger != null)
		abLevel = abLogger.getEffectiveLevel().toString();
	if (groupsLogger != null)
		groupsLevel = groupsLogger.getEffectiveLevel().toString();
	if (lensLogger != null)
		lensLevel = lensLogger.getEffectiveLevel().toString();
	%>
	<p>
	<h2>Troubleshooting</h2>
	<p>
	<button title="Muse can check your system and network to ensure that it can access email from a test Gmail account. This ensures that there are no problems with your machine, network or firewall setup." class="tools-pushbutton" onclick="javascript:window.location='verifyEmailSetup';">Check network setup</button> 
	<p>
	Debug logging levels: 
	&nbsp;&nbsp;
	All <%=htmlForLoggingSelectBox("ROOT", rootLevel) %>
	&nbsp;&nbsp;
	Identities: <%=htmlForLoggingSelectBox("edu.stanford.muse.email.AddressBook", abLevel) %>
	&nbsp;&nbsp;
	Groups: <%=htmlForLoggingSelectBox("edu.stanford.muse.groups.Grouper", groupsLevel) %>
	&nbsp;&nbsp;
	Lens: <%=htmlForLoggingSelectBox("edu.stanford.muse.webapps.Lens", lensLevel) %>
	<br/>
<%} %>

<h2>Test Features</h2>
<br/>
<a href="types">Entity types</a> &bull;
<a href="graph">Social Graph</a> &bull; 
<a href="links">HTTP links</a> &bull; 
Youtubes &bull;
<a href="memorystudy/question.jsp">Memory study</a>
</div>
</div>
<jsp:include page="footer.jsp"/>
</body>
</html>