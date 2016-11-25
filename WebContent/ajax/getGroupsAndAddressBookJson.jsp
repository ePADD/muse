<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<%@page language="java" import="java.util.*"%>
<%
	// this is for abhinay's stuff
response.setHeader("Cache-Control","no-cache");
response.setHeader("Pragma","no-cache");
response.setHeader("Expires","-1");
response.setContentType("application/x-javascript; charset=utf-8");

Archive archive = JSPHelper.getArchive(session);
GroupAssigner ga = archive.groupAssigner;
if (ga != null)
{
	String json = ga.getHierarchyJSON(20).toString().trim();
	out.print (json);
	
}
else
	out.print ("null");
%>
