<%@ page language="java" pageEncoding="ISO-8859-1"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*" %>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.lens.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.bespoke.iris.*"%>
<%@page language="java" import="java.util.Calendar"%>
<%@page language="java" import="java.text.SimpleDateFormat"%>
<% 

// https://developer.mozilla.org/en/http_access_control
response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
//response.setHeader("Access-Control-Allow-Origin", "http://xenon.stanford.edu");
response.setHeader("Access-Control-Allow-Credentials", "true");

JSPHelper.logRequest(request); 
String s = request.getParameter("session");
s="test";
if (s != null)
{
	Sessions.loadGlobalSession(session, s);
	// check if cachedir is explicitly specified, if so, we need to redirect the content urls	
}


Collection<EmailDocument> allDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
NattyTest.parseEmailforEvents( allDocs);

%>