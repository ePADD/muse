<%@page language="java" import="edu.stanford.bespoke.JSPHelper"%>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page import="java.io.*"%>
<%
    response.setContentType("application/json");
    //System.out.println(request.getServletPath()+": Request URI was "+request.getRequestURI());
    
    String jsonpCallback = (String)request.getParameter("jsonp_callback");
    if (jsonpCallback == null) {
        System.out.println(request.getServletPath()+": jsonp_callback is empty!");
        return;
    }
    
    String json = null;
    String verifyCookiesEnable = (String)request.getParameter("verifyCookies");
    if (verifyCookiesEnable == null) {
    	session.setAttribute("isCookiesEnabled", "true");
    	json = "{ \"muse\":true }";
    }
    else {
    	String cookiesEnabled = (String)JSPHelper.getSessionAttribute(session, "isCookiesEnabled");
    	if (cookiesEnabled != null)
    	    json = "{ \"muse\":true, \"cookiesEnabled\":true }";
    	else
    		json = "{ \"muse\":true, \"cookiesEnabled\":false }";
    }

    String output = jsonpCallback+"("+json+")";
    
    System.out.println(request.getServletPath()+": JSONP output server side is - "+output);
    out.println(output);
%>