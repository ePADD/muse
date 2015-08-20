<%@page language="java" contentType="application/json; charset=UTF-8"%>
<%@page language="java" import="org.json.*"%>    
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="java.util.*"%>
<%
	// jsp to handle save/load of groups
	JSPHelper.setPageUncacheable(response);
	boolean success = true;
	
	String title=request.getParameter("save");
	if (title != null)
	{
		JSPHelper.log.info ("Saving grouping " + title);
		success = GroupsConfig.save(session, title);
	}
	else 
	{
		title = request.getParameter("load");
		if (title != null)
		{
			JSPHelper.log.info ("loading grouping " + title);
			success = GroupsConfig.load(session, title);
		}
		
		title = request.getParameter("delete");
		if (title != null)
		{
			String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
			JSPHelper.log.info ("deleting grouping " + title);
			success = GroupsConfig.delete(cacheDir, title);
		}
	}
	if (!success)
		throw new RuntimeException(); // return an ajax error, so client doesn't process assuming success.
	JSONObject obj = new JSONObject();
	obj.put("status", 0);
	out.println (obj);
%>
