<%@ page language="java" contentType="application/json; charset=UTF-8"
    pageEncoding="ISO-8859-1"%>
 <% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="edu.stanford.muse.index.Archive"%>
<%@page language="java" import="edu.stanford.muse.index.Document"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<%@page language="java" import="org.json.JSONObject"%>
<%@page language="java" import="java.util.Collection"%>
<%@page language="java" %>
<%@page language="java" %>
<%@page language="java" %>
<%@page language="java" %>
<%@page language="java" %>
<%
	// simple call to remove a filter
Archive archive = JSPHelper.getArchive(session);
Collection<Document> fullEmailDocs = archive.getAllDocs();
JSONObject j = new JSONObject();
if (fullEmailDocs != null)
{
	session.setAttribute("emailDocs", fullEmailDocs);
	session.removeAttribute("currentFilter");
	j.put("status", 0);
	j.put("nDocs", fullEmailDocs.size());
	if (archive != null && archive.indexer != null)
		archive.indexer.summarizer.nukeCards(); // we have to nuke cards, since they are no longer valid
}
else
	j.put("status", 1);

out.println (j.toString());
%>
