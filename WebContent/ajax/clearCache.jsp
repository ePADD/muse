<%@ page language="java" contentType="application/json; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%
try {
	// wipe out the whole dirs and then create them afresh
	// in future, may consider ways to delete parts of cache
	Archive archive = (Archive) session.getAttribute("archive");
	if (archive != null)
	{
		archive.clear();
		archive.close();
		
		String cacheDir = (String) session.getAttribute("cacheDir");
	    String rootDir = JSPHelper.getRootDir(request);
		Archive.clearCache(cacheDir, rootDir);
	}
	
	Thread.sleep(2000);
	JSONObject o = new JSONObject();
	o.put("status", 0);
	out.println(o.toString());
} catch (Exception e) {
	JSPHelper.log.warn ("Cache failed");
	Util.print_exception(e, JSPHelper.log);
	JSONObject o = new JSONObject();
	o.put("status", 0);
	o.put("error", e.toString()); 
	out.print(o.toString());
}
%>
