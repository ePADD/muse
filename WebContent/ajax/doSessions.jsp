<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" contentType="text/javascript; charset=UTF-8"%>
<%@page language="java" import="org.json.*"%>    
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="java.util.*"%>
<%
	response.setHeader("Cache-Control","no-cache"); 
	response.setHeader("Pragma","no-cache"); 
	response.setDateHeader ("Expires", -1); 
	JSONObject obj = new JSONObject();
	String verb = request.getParameter("verb");
	String title = request.getParameter("title");
	
	String explicitCacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
	JSPHelper.log.info ("cacheDir is " + explicitCacheDir);

	/*	// "save" and "load" are no longer supported.
		// use "load_archive" instead of "load" and use Archive.export() instead of "save".
	if ("save".equals(verb))
	{
		Sessions.saveSession(session, title);
		obj.put("status", 0);
		obj.put ("errorMessage", "Session saved");
		out.println (obj);
		return;
	}

	if ("load".equals(verb))
	{
		JSPHelper.log.info ("loading session " + title);
		boolean success = Sessions.loadGlobalSession(session, title);
		if (!success)
		{
			obj.put("status", 1);
			obj.put ("errorMessage", "Failed to load session");
		} else {
			obj.put("status", 0);
			obj.put ("message", "Ok, session loaded");
		}
		out.println (obj);
		return;
	}
	*/

	// "load_archive" is for server mode. the following directory structure is assumed:
	// <baseDir>
	//    - archives.xml (list of available archives)
	//    - <archive1_folder>
	//         - sessions (contain "default.session.v3")
	//         - indexes (contain Lucene index files)
	//    - ...
	if ("load_archive".equals(verb))
	{
		JSPHelper.log.info ("loading archive " + title);
		boolean success = Sessions.loadSharedArchiveAndPrepareSession(session, title) != null;
		if (!success)
		{
			obj.put("status", 1);
			obj.put ("errorMessage", "Failed to load session");
		} else {
			obj.put("status", 0);
			obj.put ("message", "Ok, session loaded");
		}
		out.println (obj);
		return;
	}
	
	if ("export".equals(verb))
	{
		JSPHelper.log.info ("exporting archive " + title);
		Pair<Boolean,String> result = Sessions.exportArchive(request);
		boolean success = result.first;
		String msg = result.second;
		if (!success)
		{
			obj.put("status", 1);
			obj.put ("errorMessage", msg);
		} else {
			obj.put("status", 0);
			obj.put ("message", msg);
		}
		out.println (obj);
		return;
	}
	
	if ("delete".equals(verb))
	{
		String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
		JSPHelper.log.info ("deleting session " + title);
		boolean success = Sessions.deleteSession(cacheDir, title);
		if (success)
		{
			obj.put("status", 0);
			obj.put ("message", "Ok, session deleted");
		}
		else
		{
			obj.put("status", 1);
			obj.put ("errorMessage", "Could not delete session");
		}
		out.println (obj);
		return;
	}
	obj.put("status", 1);
	obj.put ("errorMessage", "Unknown operation requested for sessions");
	out.println (obj);
%>
