<%@page language="java" contentType="text/javascript; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<%
	// prevent caching of this page - sometimes the ajax seems to show stale status
	JSPHelper.setPageUncacheable(response);
    response.setContentType("application/json; charset=utf-8");
	StatusProvider obj = (StatusProvider) session.getAttribute("statusProvider");
    if (obj == null)
    {
		JSONObject json = new JSONObject();
		try {
			json.put("message", "Starting up ...");	
		} catch (JSONException jsone) {
			try {
				json.put("error", jsone.toString());	
			} catch (Exception e) { Util.report_exception(e); }
		}
	}
    else
        out.println (obj.getStatusMessage());

%>
