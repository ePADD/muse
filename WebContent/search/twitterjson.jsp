<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>

<%
// this is for abhinay's stuff
response.setHeader("Cache-Control","no-cache");
response.setHeader("Pragma","no-cache");
response.setHeader("Expires","-1");
response.setContentType("application/x-javascript; charset=utf-8");
org.json.simple.JSONArray obj = (org.json.simple.JSONArray) JSPHelper.getSessionAttribute(session, "MySessionJSON");
out.print (obj);

%>
