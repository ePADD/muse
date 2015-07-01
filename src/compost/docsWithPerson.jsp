<%@ page contentType="text/html; charset=UTF-8"%>
<%
JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8");
%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="java.util.*"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<jsp:include page="css/css.jsp"/>
<body>
<%@include file="header.html"%>
<%
// jsp to display messages sent to ALL email addresses in person parameter
String[] persons = request.getParameterValues("person");
persons = JSPHelper.convertRequestParamsToUTF8(persons);

AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
Collection<EmailDocument> allEmailDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
if (addressBook == null || allEmailDocs == null)
{
	if (!session.isNew())
		session.invalidate();
	session.setAttribute("loginErrorMessage", "Your session has timed out -- please click login again.");
%>
    <script type="text/javascript">window.location="index.jsp";</script>
<%
    System.err.println ("Error: session has timed out, addressBook = " + addressBook);
	if (allEmailDocs != null)
		System.err.println ("allEmailDocs size = " + allEmailDocs);
	else
		System.err.println ("allEmailDocs = null");
	return;
}
%>

<script src="js/jquery/jquery.js" type="text/javascript"></script>
<script src="jquery/jquery-ui.js" type="text/javascript"></script>

<%
	Archive archive = JSPHelper.getArchive(session);
Indexer indexer = null;
if (archive != null)
	indexer = archive.indexer;

List<Document> ds = IndexUtils.selectDocsByPersons(addressBook, allEmailDocs, persons);
%>
<table width="100%"><tr><td>
	<%=ds.size()%> message<%=((ds.size() != 1) ? "s":"")%> for: <b><font color="blue">
<%
	if (persons != null)
		for (int i = 0; i < persons.length; i++)
			for (String emailOrName: persons)
				out.println (emailOrName + ((i < persons.length-1) ? ", ":""));
%>
	</font></b><span class="muted">(+ aliases)</span>
	</td>
	<td align="right"><img style="display:none" src="images/spinner.gif" id="search_spinner"></img> &nbsp;
	Search&nbsp;&nbsp;<input class="searchbox" id="searchbox" type="text" size="15" value=""></input>
	<button class="tools-button" onclick="narrowSearch();return false;" onmouseover="muse.highlightButton(event)" onmouseout="muse.unhighlightButton(event)">Go</button>

	</td>
	</tr></table>
	<br/>
	<div onclick="handleClick(event)" id="jog_contents" class="message">
	<br/><br/><br/><br/><br/><br/><span style="align:center">Loading page...</span><br/><br/><br/><br/><br/>
	</div>

<%
	out.flush();
	GroupAssigner groupAssigner = (GroupAssigner) JSPHelper.getSessionAttribute(session, "groupAssigner");
	Pair<DataSet, String> pair = JSPHelper.pagesForDocuments (ds, archive, "", null);
	String datasetName = "dataset-1";
	DataSet dataset = pair.getFirst();
	String html = pair.getSecond();
	session.setAttribute (datasetName, dataset);
	out.println (html);
	JSPHelper.log.info ("Browsing " + dataset.size() + " pages in dataset " + datasetName);
    session.setAttribute("selectedDocs", ds);
%>


<script type="text/javascript">
$(document).ready(function() { start_jog('.page', '#jog_contents'); });
</script>

</body>
</html>
