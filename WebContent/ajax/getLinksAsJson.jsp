<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<%@page language="java" import="java.util.*"%>
<%
	// this is for abhinay's stuff
JSPHelper.setPageUncacheable(response);
response.setContentType("application/x-javascript; charset=utf-8");
Archive archive = JSPHelper.getArchive(session);
if (archive == null || archive.indexer == null)
{
	out.println ("[]");
	return;
}

String json = LinkInfo.linksToJson(archive.getLinks());
out.print (json);
%>
