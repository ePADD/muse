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

Collection<Document> allDocs = (Collection<Document>) JSPHelper.getSessionAttribute(session, "emailDocs");
Archive archive = JSPHelper.getArchive(session);
AddressBook addressBook = archive.addressBook;
String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
Indexer indexer = archive.indexer;
boolean exclude = "exclude".equalsIgnoreCase(request.getParameter("type"));
boolean and_not_or = "and".equals(request.getParameter("op")); // if true, we need to and the params, not or them

Set<Document> filteredDocs = JSPHelper.selectDocs(request, session, false /* !only_apply_to_filtered_docs */, !and_not_or /* needs or_not_and */);
JSPHelper.log.info (filteredDocs.size() + " documents selected in filter");

boolean initialized = false; // this will be set to true when at least one specified facet item has been considered for filteredDocs

// now if exclude is set, then remove filteredDocs = allDocs - filteredDocs
if (exclude)
{
	Set<Document> tmp = new LinkedHashSet<Document>();
	for (Document d: allDocs)
		if (!filteredDocs.contains(d))
	tmp.add(d);
	filteredDocs = tmp;
}
JSPHelper.log.info (filteredDocs.size() + " docs in filter");
session.setAttribute("emailDocs", new ArrayList<Document>(filteredDocs));
JSONObject j = new JSONObject();
j.put("status", 0);
j.put("nDocs", filteredDocs.size());
out.println (j.toString());
if (indexer != null && indexer.summarizer != null)
	indexer.summarizer.nukeCards(); // we have to nuke cards, since they are no longer valid
NewFilter f = NewFilter.createFilterFromRequest(request);
session.setAttribute("currentFilter", f);
%>
