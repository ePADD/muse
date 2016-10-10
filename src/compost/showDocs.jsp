<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="java.util.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<jsp:include page="css/css.jsp"/>
<body>
<%@include file="header.html"%>

<script src="js/jquery/jquery.js" type="text/javascript"></script>
<script src="js/jquery/jquery-ui.js" type="text/javascript"></script>
<script src="js/jog.js" type="text/javascript"></script>

<%
	JSPHelper.logRequest(request);
List<Document> selectedDocs = (List<Document>) JSPHelper.getSessionAttribute(session, "selectedDocs");
if (selectedDocs == null)
{
    out.println ("<br/><br/>Sorry, there are no documents in the current session. The session may have timed out or run out of memory. Please try again.<br/>");
    return;
}

String str[] = request.getParameterValues("doc");
if (str == null)
{
	out.println ("0 documents selected");
	return;
}
List<Document> ds = new ArrayList<Document>();
if (str.length == 1 && "all".equalsIgnoreCase(str[0]))
{
	ds.addAll(selectedDocs);
}
else
{
	// format for s is:
	// 2-10 or 14 i.e. a single page# or a comma separate range
	for (String s: str)
	{
		int startIdx = 0, endIdx = -1;

		try {
	if (s.indexOf("-") >= 0)
	{	// page range
		StringTokenizer pageST = new StringTokenizer(s);
		startIdx = Integer.parseInt(pageST.nextToken());
		endIdx = Integer.parseInt(pageST.nextToken());
	}
	else
		startIdx = endIdx = Integer.parseInt(s);
		} catch (Exception e) { System.err.println ("Bad doc# string in query: " + s); continue; }

		for (int idx = startIdx; idx <= endIdx; idx++)
		{
	if (idx < 0 || idx >= selectedDocs.size())
	{
		System.err.println ("Sorry, bad doc# " + idx + " # docs = " + selectedDocs.size());
		continue;
	}
	ds.add(selectedDocs.get(idx));
		}
	}
}

Archive driver = (Archive) JSPHelper.getSessionAttribute(session, "indexDriver");
Indexer indexer = null;
if (driver != null)
	indexer = driver.indexer;
GroupAssigner colorAssigner = (GroupAssigner) JSPHelper.getSessionAttribute(session, "colorAssigner");
%>
<table width="100%"><tr><td>
	<%=ds.size()%> document<%=((ds.size() != 1) ? "s":"")%> selected
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

<% out.flush();
   out.println (JSPHelper.pagesForDocuments (ds, indexer, colorAssigner, "dummy", (BlobStore) JSPHelper.getSessionAttribute(session, "attachmentsStore"), null));
   session.setAttribute("selectedDocs", ds);
%>

<script type="text/javascript">
$(document).ready(function() { start_jog('.page', '#jog_contents'); });
</script>

</body>
</html>
