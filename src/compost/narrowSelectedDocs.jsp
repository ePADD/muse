<%@ page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>

<%
	JSPHelper.logRequest(request);
// returns json
Set<Document> selectedDocs = (Set<Document>) JSPHelper.getSessionAttribute(session, "selectedDocs");
String term = request.getParameter("term");
Archive driver = (Archive) JSPHelper.getSessionAttribute(session, "indexDriver");
List<Integer> list;
List<Document> docList = new ArrayList<Document>();
docList.addAll(selectedDocs);
if (driver != null)
    list = driver.indexer.getSelectedDocsWithPhrase(docList, term);
else
{
	list = new ArrayList<Integer>();
	// no indexer, do it ourselves :-(
	for (int i = 0 ; i < selectedDocs.size(); i++)
	{
		Document d = docList.get(i);
		if (d.getContents().toLowerCase().indexOf(term) >= 0)
	list.add(i);
	}
}

System.out.println ("narrowed to " + list.size() + " docs");
String json = JSONUtils.jsonForIntList(list);
System.out.println ("json = " + json);
out.println (JSONUtils.jsonForIntList(list));
%>