<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%
JSPHelper.setPageUncacheable(response);
// prevent caching of this page - sometimes the ajax seems to show stale status

Archive archive = JSPHelper.getArchive(session);
AddressBook addressBook = archive.addressBook;
if (addressBook == null || archive.indexer == null) {
	out.println ("{error:'no session loaded}");
	return;
}

String friends = request.getParameter("friends");
String comment = request.getParameter("comment");
String description = request.getParameter("description");
String body = request.getParameter("body");
String folder = request.getParameter("folder");
Collection<EmailDocument> allDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");

String sourceURL = request.getParameter("sourceURL");
String pictureURL = request.getParameter("pictureURL");

JSONArray farr = new JSONArray(friends);
JSONObject f_data = new JSONObject(body);

for (int i = 0; i < farr.length(); i++)
{
	try {
		JSONObject fobj = farr.getJSONObject(i);
		String id = fobj.getString("id");
		String friendName = fobj.getString("name");
		String json_for_id = f_data.getString(id);
		json_for_id = new JSONObject(json_for_id).toString(4); // just to pretty toString
		JSPHelper.log.info ("adding data for " + id + " name=" + friendName + " body = " + Util.ellipsize(json_for_id, 100));
		PersonDocument pd = new PersonDocument(addressBook, friendName, folder, "Facebook profile of " + friendName, json_for_id, comment, null, sourceURL, "http://graph.facebook.com/" + id + "/picture");
		allDocs.add(pd);
		archive.addDoc((edu.stanford.muse.index.Document) pd, body);
	} catch (Exception e) {
		JSPHelper.log.warn ("adding data failed for index " + i);
	}
}

archive.close();out.println ("{}");
%>
