<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<% 	JSPHelper.logRequest(request);
	Archive archive = (Archive) session.getAttribute("archive");

	try {
		// cancel any pending ops
		StatusProvider obj = (StatusProvider) JSPHelper.getSessionAttribute(session, "statusProvider");
		if (obj != null)
		{
			JSPHelper.log.info ("Cancelling status provider object: " + obj);
		    obj.cancel();
		}
		
		// sleep for some time for cancel to take effect
		Thread.sleep(2000);
		
		if (archive != null)
			try { archive.close(); } catch (Exception e) { Util.print_exception(e, JSPHelper.log); }
		
		if (request.getParameter("clearCache") != null)
		{				
			String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
			String rootDir = JSPHelper.getRootDir(request);
			Archive.clearCache(cacheDir, rootDir);
		}
		
		// remove session vars
		if (!session.isNew())
			session.invalidate();
	} catch (Exception e) { 
		JSPHelper.log.warn ("Exception logging out: " + e);
	}
%>
<script type="text/javascript">window.location = "index.jsp"; </script>
<% 	
JSPHelper.log.info ("Memory status: " + Util.getMemoryStats());
JSPHelper.logRequestComplete(request); 
%>
