<%@ page language="java" contentType="application/json; charset=UTF-8"
    pageEncoding="ISO-8859-1"%>
 <% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.util.zip.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%
	// simple call to remove a filter
Archive archive = JSPHelper.getArchive(session);
Collection<Document> fullEmailDocs = archive.getAllDocs();
JSONObject j = new JSONObject();
if (fullEmailDocs != null)
{
	Collection<Document> emailDocs = fullEmailDocs;
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
