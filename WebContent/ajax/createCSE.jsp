<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.slant.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%
JSPHelper.logRequest(request);
// returns an html fragment that will be stuck into the status box.
List<Document> docs = JSPHelper.selectDocsAsList(request, session);	
List<LinkInfo> links = EmailUtils.getLinksForDocs(docs);

// explicitly authenticate before expanding URLs etc... we want to warn the user early because url expansion can take time
String oauthToken = CustomSearchHelper.authenticate(request.getParameter("login"), request.getParameter("password"));
String cseName = request.getParameter("cseName");
if (Util.nullOrEmpty(cseName))
	cseName = "Slant";

if (oauthToken == null)
	out.println ("Login failed. Please try again.");
else
{
	String id = CustomSearchHelper.readCreatorID (oauthToken);
	session.setAttribute("creator", id);
	Triple<Integer,Integer, Integer> t = CustomSearchHelper.annotateCSEFromLinkInfos (oauthToken, cseName, links);
	out.println ("&nbsp;Search engine domains: " + t.getFirst() + " added");
	if (t.getSecond() > 0)
		out.println (", " + t.getSecond() + " failed");
	if (t.getThird() > 0)
		out.println (", " + t.getThird() + " dropped due to Google limits");
	List<CSEDetails> cses = CustomSearchHelper.getExistingCSEs(oauthToken);
	if (!Util.nullOrEmpty(cses))
	{
		out.println ("<br/><br/>\n");
		out.println ("The following custom search engines exist in your account. Please bookmark the links<br/>\n");
		out.println ("<table>\n");
		for (CSEDetails cse: cses)
		{
			String href = "http://www.google.com/cse/home?cx=" + cse.creator + ":" + cse.title;
			out.println ("<tr><td align=\"right\"><a href=\"" + href + "\">" + cse.title + "</a></td><td>&nbsp;&nbsp;" + cse.description + "</td></tr>");
		}
		out.println ("</table>\n");
	}		
}
JSPHelper.logRequestComplete(request);
%>