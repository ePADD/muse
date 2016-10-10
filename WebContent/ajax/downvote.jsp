<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.apache.log4j.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.lens.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%
JSPHelper.setPageUncacheable(response);

//https://developer.mozilla.org/en/http_access_control
response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
//response.setHeader("Access-Control-Allow-Origin", "http://xenon.stanford.edu");
response.setHeader("Access-Control-Allow-Credentials", "true");
response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");

response.setContentType("application/x-javascript; charset=utf-8");

String term = request.getParameter("term");
String termcount = request.getParameter("termcount");
String totalcount = request.getParameter("totalcount");
String url = request.getParameter("url");
LensPrefs lensPrefs = (LensPrefs) JSPHelper.getSessionAttribute(session, "lensPrefs");

try {
	JSPHelper.log.info("Cancelling: <URL , TERM >: " + "< " + url + " , " + term + ">");
	if (lensPrefs != null)
	{
		lensPrefs.cancelTerm (url, term);
		lensPrefs.cancelTerm ("GLOBAL", term); // right now, we are going to ignore url and just use a global boost score
	}
} catch (Exception e) {
	JSPHelper.log.warn ("Exception while cancelling term: " + Util.stackTrace(e));	
}
%>
