
<%@page language="java" import="edu.stanford.muse.slant.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.apache.log4j.*"%>

<%
response.setHeader("Cache-Control","no-cache");
response.setHeader("Pragma","no-cache");
response.setHeader("Expires","-1");
response.setContentType("application/x-javascript; charset=utf-8");


String id = request.getParameter("searchengineid");
String rating = request.getParameter("rating");
String query = request.getParameter("query");


try {
	TwitterCallbackServlet.logger.log(Level.INFO, 
            "<ID , RATING , QUERY>: "+ "< "+id+" , "+ rating +" , " + query + ">");
	
} catch (Exception e) {
	
}
%>
