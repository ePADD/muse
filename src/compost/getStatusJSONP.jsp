<%@page contentType="text/html; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.JSPHelper"%>
<%
	// prevent caching of this page - sometimes the ajax seems to show stale status
    response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
    response.setHeader("Pragma","no-cache"); //HTTP 1.0
    response.setDateHeader ("Expires", 0); //prevent caching at the proxy server

    // JSONP processing
    String jsonpCallback = (String)request.getParameter("jsonp_callback");
    if (jsonpCallback == null) {
        System.out.println(request.getServletPath()+": jsonp_callback is empty!");
        return;
    }
    
    // Setup status msg obj
    JSONObject result = new JSONObject();
    //result.put("error", true);
    
	StatusProvider obj = (StatusProvider) JSPHelper.getSessionAttribute(session, "statusProvider");
    if (obj == null) {
    	result.put("statusMsg", "");
    }
    else {
    	result.put("statusMsg", obj.getStatusMessage());
    }

    String output = jsonpCallback+"("+result.toString()+")";
    System.out.println(request.getServletPath()+": JSONP output server side is - "+output);

    // JSON & Javascript based response to facilitate JSONP cross-domain
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-cache");
    out.println(output);
%>
