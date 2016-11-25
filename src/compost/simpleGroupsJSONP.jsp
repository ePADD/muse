<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%

JSPHelper.logRequest(request, false);

//SocialFlows mode
String socialflowsURL = request.getParameter("socialflow");
if (socialflowsURL != null) {
	socialflowsURL = URLDecoder.decode(socialflowsURL, "UTF-8");
    session.setAttribute("mode", "socialflow");
    session.setAttribute("socialflow", socialflowsURL);
    JSPHelper.log.info ("running in socialflow mode with socialflow=" + socialflowsURL);
}

// JSONP processing
String jsonpCallback = (String)request.getParameter("jsonp_callback");
if (jsonpCallback == null) {
    System.out.println(request.getServletPath()+": jsonp_callback is empty!");
    return;
}

// Account for login errors
String loginErrorMessage = (String)JSPHelper.getSessionAttribute(session, "loginErrorMessage");
if (loginErrorMessage != null) {
    session.removeAttribute("loginErrorMessage");
    
    // Setup error msg
    JSONObject result = new JSONObject();
    result.put("error", true);
    result.put("msg", loginErrorMessage);
    
    // JSON & Javascript based response to facilitate JSONP cross-domain
    String output = jsonpCallback+"("+result.toString()+")";
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-cache");
    out.println(output);
    return;
}

// Account for other errors
String errorMessage = "Oops! An error occurred with our email analysis utility. Please retry again.";
String x = (String) JSPHelper.getSessionAttribute(session, "errorMessage");
if (x != null)
{
    session.removeAttribute("errorMessage");
    
    x = Util.escapeHTML(x); // htmlize
    x = x.replace("\n", "<br/>\n"); // htmlize
    errorMessage += "<br/>\n" + x;
    
    // Setup error msg
    JSONObject result = new JSONObject();
    result.put("error", true);
    result.put("msg", errorMessage);

    String output = jsonpCallback+"("+result.toString()+")";
    System.out.println(request.getServletPath()+": JSONP output server side is - "+output);

    // JSON & Javascript based response to facilitate JSONP cross-domain
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-cache");
    out.println(output);
    return;
}



// Check whether simpleGroups.jsp processing is done or not
Boolean simpleGroupsDone = (Boolean)JSPHelper.getSessionAttribute(session, "DONE_simpleGroups");
if (simpleGroupsDone == null || !simpleGroupsDone.booleanValue())
{
    // Setup not done yet msg
    JSONObject result = new JSONObject();
    result.put("simpleGroupsNotDone", true);
    
    // Get current status message
    StatusProvider obj = (StatusProvider) JSPHelper.getSessionAttribute(session, "statusProvider");
    if (obj == null) {
        result.put("statusMsg", "");
    }
    else {
        result.put("statusMsg", obj.getStatusMessage());
    }

    // JSON & Javascript based response to facilitate JSONP cross-domain
    String output = jsonpCallback+"("+result.toString()+")";
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-cache");
    out.println(output);
    return;
}
session.removeAttribute("DONE_simpleGroups");



// Setup success msg
JSONObject result = new JSONObject();
result.put("success", true);
result.put("redirectURL", "getContactGroupsToSocialFlows.jsp");

String output = jsonpCallback+"("+result.toString()+")";
System.out.println(request.getServletPath()+": JSONP output server side is - "+output);

//JSON & Javascript based response to facilitate JSONP cross-domain
response.setContentType("application/json");
response.setHeader("Cache-Control", "no-cache");
out.println(output);
return;

%>