<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%
JSPHelper.logRequest(request, false);

MuseEmailFetcher m = (MuseEmailFetcher) JSPHelper.getSessionAttribute(session, "museEmailFetcher");
String mode = (String) JSPHelper.getSessionAttribute(session, "mode");

boolean success = JSPHelper.fetchEmails(request, session, false, false, true); // true so we can use default folders
if (!success)
{ // ! success could be due to cancellation also... in that case we should go to folders page ??

	response.setContentType("text/xml");
    response.setHeader("Cache-Control", "no-cache");
    out.println("<result><div resultPage=\"error.jsp\"></div></result>");
    return;
}

session.setAttribute("statusProvider", new StaticStatusProvider("Computing top groups...<br/>&nbsp;<br/>&nbsp;"));

JSPHelper.doGroups(request, session);
GroupHierarchy<String> hierarchy = (GroupHierarchy<String>) JSPHelper.getSessionAttribute(session, "groupHierarchy");
AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
response.setContentType("text/xml");
response.setHeader("Cache-Control", "no-cache");

if ("facebookgroups".equals(mode))
	out.println("<result><div resultPage=\"facebookGroups.jsp\"></div></result>"); // straight to the fb groups
else
	out.println("<result><div resultPage=\"groups\"></div></result>"); // ordinary editing interface
%>