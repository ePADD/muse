<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.bespoke.JSPHelper"%>
<%@page language="java" import="java.util.*"%>
<%
	response.setHeader("Cache-Control","no-cache");
response.setHeader("Pragma","no-cache");
response.setHeader("Expires","-1");
response.setContentType("application/x-javascript; charset=utf-8");

// response.setContentType("application/json");

// prints json for groups in current session
AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
GroupAssigner groupAssigner = (GroupAssigner) JSPHelper.getSessionAttribute(session, "groupAssigner");
String json;
if (groupAssigner == null || addressBook == null)
{
	json = "[]";
}
else
{
	List<SimilarGroup<String>> groups = groupAssigner.getSelectedGroups();
	json = JSONUtils.jsonForGroups(addressBook, groups);
}
// out.println ("alert ('x');\n");
out.println ("var jsonForGroups = " + json + ';');
%>