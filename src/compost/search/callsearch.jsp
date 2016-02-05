<%@ page contentType="text/html;charset=UTF-8" language="java" %>
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

// response.setContentType("application/json");

// prints html in current session
String url = request.getParameter("url");
String htmlresponse= GoogleGet.get(url);
// out.println ("alert ('x');\n");
out.println (htmlresponse);
%>
