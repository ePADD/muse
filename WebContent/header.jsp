<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>

<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery.tools.min.js"></script>
<script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>

<link rel="icon" type="image/png" href="images/muse-favicon.png">

<script>
function toggle(id)
{
	$('.dropdown-menu').map(function(i, v) {
		if (id == v.id) {
			$(v).toggle('fast');
		} else {
			$(v).hide();
		}
	});
}
</script>

<div align="center">
	<table style="width:100%">
	<tr align="center">
	<td align="left" width="20%">
	<!-- <img width="57" height="54" src="/muse/images/logo.png"/> -->
	<a href="/muse"><img class="img90" width="200px" src="/muse/images/muse-logo.png"/></a>

	</td>
	<td align="right" valign="middle">
	<ol class="topnav">
		<li>
			<div style="position:relative;display:inline;">
			<div style="display:inline;">
				<img class="nav-item" height="25px" src="images/filter-icon.png" title="Filter messages" onclick="muse.show_filter();">
			</div>
			<% if (JSPHelper.isFilterSet(session)) { %>
			<div style="position:relative;display:inline;">
				<img src="images/check.png" style="width:12px;position:absolute;top:0px;left:-14px"/>
			</div>
			<% } %>
			</div>
			<div class="muse-overlay"></div>
			<div class="shadow nojog rounded" id="filter-div" style="box-shadow:0 4px 23px 5px rgba(255,255,255, 0.2), 0 2px 6px rgba(255,255,255,0.15); display:none"></div>
		</li>

		<li>
			<img  class="nav-item" height="25px" src="images/question-mark-icon.png" title="Help" onclick="window.location='/muse/help';">
		</li>

		<li>
		<div style="position:relative;display:inline;">
			<div style="display:inline;">
				<img  class="nav-item" height="25px" src="images/folder-icon.png" title="Save or load archives" onclick="toggle('folders_dropdown');">
				<span class="caret" style="position:relative;top:32px;left:-12px"></span>
			</div>
			<div class="rounded dropdown-menu" id="folders_dropdown">
				<% if (!ModeConfig.isMultiUser()) { %>
				<a rel="#loadMenu" id="loadLink"><span class="menuitem">Load ...</span></a><br/>
				<% } %>
				<span id="saveMenu" class="menuitem" onclick="">Save <img id="save_spinner" src="images/spinner3-greenie.gif" style="width:16px;visibility:hidden;"/></span><br/>
				<% if (!ModeConfig.isMultiUser()) { %>
				<a rel="#saveAsMenu" id="saveAsLink"><span class="menuitem">Save as ...</span></a><br/>
				<span class="menuitem" onclick="alert('To be implemented')">Save as mbox ...</span><br/>
				<% } %>
			</div>
		</div>
		</li>
<script>
// $('#saveMenu').click(function() { muse.save_session('default', '#save_spinner', function() { $('#folders_dropdown').hide(); });});
$('#saveMenu').click(function() { muse.save_session('default', '#save_spinner', null);});
</script>
		<li>
		<div style="position:relative;display:inline;">
			<div style="display:inline;">
				<img  class="nav-item" height="25px" src="images/add.png" title="Add folders or accounts to this archive" onclick="toggle('add_dropdown');">
				<span class="caret" style="position:relative;top:32px;left:-12px"></span>
			</div>
			<div class="rounded dropdown-menu" id="add_dropdown">
				<span class="menuitem" onclick="window.location='index?noredirect'">Add accounts</span><br/>
				<span class="menuitem" onclick="window.location='folders'">Add folders</span><br/>
			</div>
		</div>
		</li>

		<li class="last">
		<div style="position:relative;display:inline;">
			<div style="display:inline;">
				<img  class="nav-item" title="Settings" height="25px" src="images/gears-icon.png" onclick="toggle('settings_dropdown');"/>
				<span class="caret" style="position:relative;top:32px;left:-12px"></span>
			</div>
			<div class="rounded dropdown-menu" id="settings_dropdown">
				<span title="Click to open settings page" class="menuitem" onclick="window.location='settings'">Settings</span><br/>
				<span title="Click to send us feedback" class="menuitem" onclick="window.location='https://docs.google.com/spreadsheet/viewform?formkey=dF9yYkhfbEdJNnVYaGhaYUdZaG1EeXc6MQ'">Feedback</span><br/>
				<span title="Click to logout" class="menuitem" onclick="muse.doLogout()">Logout</span>
			</div>
		</div>
		</li>
	</ol>
	</td>

	</tr>
	</table>
</div>

<div id="loadMenu" style="background:white" class="modal nojog">
	<span style="font-size:125%">Load another archive from<br/><br/></span>
	<table>
		<tr title="The base directory of another collection"><td>Location </td><td><input id="load_base_dir" type="text" name="cacheDir" onkeyup="checkEnter(event, '#load_button')"/><img id="load_spinner" src="images/spinner3-greenie.gif" style="width:16px;visibility:hidden;"/></td></tr>
		<tr><td></td><td><br/><input onclick="doLoad()" id="load_button" type ="button" value="OK"/> <input type ="button" class="close" value="Cancel"/></td></tr>
	</table>
</div>

<%
Archive archive = JSPHelper.getArchive(session);
Collection<EmailDocument> allDocs = (Collection) JSPHelper.getSessionAttribute(session, "emailDocs");
Collection<EmailDocument> fullEmailDocs = null;
if (archive != null)
	fullEmailDocs = (Collection) archive.getAllDocs();
if (allDocs == null)
	allDocs = fullEmailDocs;

boolean filterInEffect = (fullEmailDocs != null) && (allDocs.size() != fullEmailDocs.size());
boolean showPublicModeOption = ModeConfig.isProcessingMode(); // showing public mode option only makes sense for archivist

String trimArchiveCss = filterInEffect ? "" : "style='display:none'";
String forPublicModeCss = showPublicModeOption ? "" : "style='display:none'";
%>
<div id="saveAsMenu" style="background:white" class="modal nojog">
	<span style="font-size:125%">Save this archive as<br/><br/></span>
	<table>
		<tr title="A name for this collection"><td>Name </td><td><input id="save_as_name" type="text" name="name" onkeyup="checkEnter(event, '#save_as_button')"/><img id="save_as_spinner" src="images/spinner3-greenie.gif" style="width:16px;visibility:hidden;"/></td></tr>
		<tr <%=trimArchiveCss%> title="Trim archive to just the filtered messages"><td style="vertical-align:top;text-align:right;"><input type="checkbox" id="trimArchive" style="margin-top:10px;"></td><td>Trim archive<br/>
		<i style="line-height:1em">The exported archive will only contain messages in the current filter.</i></td></tr>
		<tr <%=forPublicModeCss%> title="Save only information suitable for public view (only names are retained, all other information is redacted)"><td style="vertical-align:top;text-align:right;"><input type="checkbox" id="forPublicMode" style="margin-top:10px;"></td><td style="align:right">Restrict archive</td></tr>
		<tr><td></td><td><br/><input id="save_as_button" onclick="doSaveAs()" type ="button" value="OK"/> <input type ="button" class="close" value="Cancel"/></td></tr>
	</table>
</div>

<script>
// for all modal dialogs which are linked to tags whose "id" ends with word "Link"
$("[id$=Link]").overlay({// some mask tweaks suitable for modal dialogs
	mask: {
		color: '#ebecff',
		loadSpeed: 200,
		opacity: 0.9
	},
	closeOnClick: false
});

// note: this technique does not work with a "form" since it will trigger "submit" action on enter in which case this function would not be called.
function checkEnter(e, button) {
	if (e.keyCode == 13)
		$(button).click();
}

function doLoad() {
	var base_dir = $('#load_base_dir').val();
	muse.log ('new base dir = ' + base_dir);
	$('#load_spinner').css('visibility', 'visible');
	window.location = 'index?cacheDir=' + base_dir;
}

function doSaveAs() {
	muse.export_archive($('#save_as_name').val(), $('#trimArchive').is(':checked'), $('#forPublicMode').is(':checked'),
						'#save_as_spinner', null, statusAlertFunc)
}

function statusAlertFunc(stat) {
	var msg;
	if (stat.errorMessage)
		msg = stat.errorMessage;
	else if (stat.message)
		msg = stat.message
	else
		msg = "Internal error: unknown status";
	alert(msg);
}
</script>
