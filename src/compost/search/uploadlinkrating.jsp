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

//googlelikes="+googlelikes+"&twitterlikes="+twitterlikes+"&peoplelikes="+peoplelikes+"&toptwitterlikes="+toptwitterlikes+"&emaillikes="+emaillikes
String googlelikes = request.getParameter("googlelikes");
String twitterlikes = request.getParameter("twitterlikes");
String peoplelikes = request.getParameter("peoplelikes");
String toptwitterlikes = request.getParameter("toptwitterlikes");
String emaillikes = request.getParameter("emaillikes");
String query = request.getParameter("query");

try {
	TwitterCallbackServlet.logger.log(Level.INFO, 
            "<GOOGLE LIKES,TWITTER LIKES, PEOPLE LIKES , TOPTWITTER LIKES,EMAIL LIKES ,QUERY >: "+ "< "+googlelikes+" , "+ twitterlikes + " , "+ peoplelikes+ " , "+ toptwitterlikes + " , "+ emaillikes +" , " + query + ">");
	
} catch (Exception e) {
	
}
%>