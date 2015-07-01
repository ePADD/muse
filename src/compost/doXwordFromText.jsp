<%@ page language="java" contentType="application/x-javascript; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.util.zip.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="com.google.gson.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.xword.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="org.json.*"%>
<%
JSPHelper.logRequest(request);
String json = "";
try {
	Crossword c = CrosswordHelper.createCrossword(request);
	int size = c.w; // should be the same as c.h
	Gson gson = new Gson();
	json = gson.toJson(c);
	session.setAttribute("crossword", c);
	boolean hintsEnabled = true; // request.getParameter("hintsEnabled") != null;
		
	JSONObject result = new JSONObject();
	// we have to let this json be rendered by xword.jsp if webui param is enabled
	if (request.getParameter("webui") != null)
	{
		result.put("status", 0);	
		result.put("resultPage", "xword.jsp");
		out.println (result.toString());
	}
	else
		out.println(json);		
} catch (Exception e) {
	Util.print_exception(e, JSPHelper.log); out.println ("{status: 1, error: 'Unable to generate crossword'}");
}
session.removeAttribute("statusProvider");
JSPHelper.logRequestComplete(request);
%>