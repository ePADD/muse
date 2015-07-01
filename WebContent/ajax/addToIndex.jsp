<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%
JSPHelper.logRequest(request);
JSPHelper.setPageUncacheable(response);
// prevent caching of this page - sometimes the ajax seems to show stale status

Archive archive = JSPHelper.getArchive(session);
AddressBook addressBook = archive.addressBook;
if (addressBook == null || archive.indexer == null) {
	out.println ("{error:'no session loaded}");
    return;
}

String friendName = request.getParameter("friendName");
String comment = request.getParameter("comment");
String description = request.getParameter("description");
String body = request.getParameter("body");
String sourceURL = request.getParameter("sourceURL");
String pictureURL = request.getParameter("pictureURL");
String folder = request.getParameter("folder");
Collection<EmailDocument> allDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");

PersonDocument pd = new PersonDocument(addressBook, friendName, folder, description, body, comment, null, sourceURL, pictureURL);
allDocs.add(pd);

archive.addDoc((edu.stanford.muse.index.Document) pd, body);
archive.close();
out.println ("{}");
%>
