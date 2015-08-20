<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.apache.log4j.*"%>
<%@page language="java" import="org.apache.commons.logging.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>

<%

String logClass = request.getParameter("logger");
String level = request.getParameter("level");
JSPHelper.log.info ("changing logging for " + logClass + " to " + level);
		
if (!Util.nullOrEmpty(logClass) && !Util.nullOrEmpty(level))
{
	try {
	    Logger logger = "ROOT".equalsIgnoreCase(logClass) ? Logger.getRootLogger() : Logger.getLogger(logClass);
		Log4JUtils.setLoggingLevel(logger, level);	   
	} catch (Exception e) {
		System.err.println ("WARNING: LOGGING LEVEL CHANGE");
		String s = "Error trying to change level to " + level + ": " + e;
		JSPHelper.log.warn (s);
		return;
	}

	System.err.println ("DONE LOGGING LEVEL CHANGE");
	String message = "Changed logging level to " + level;
	JSPHelper.log.info (message);
}
%>
