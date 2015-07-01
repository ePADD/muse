<%@page language="java" import="java.security.AccessControlException"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Muse Export</title>
</head>
<body>
<%
// consider converting this to an ajax call so we can reflect better status
JSPHelper.logRequest(request);

//if (!ModeConfig.isAdminMode()) {
//	throw new AccessControlException("Command-line interface is only available in admin mode");
//}

String cmd = request.getParameter("cmd");
String message;

if ("export-archive".equals(cmd)) {
	Pair<Boolean,String> result = Sessions.exportArchive(request);
	message = result.second;
}
else if ("modify-session".equals(cmd)) {
	//boolean result = Sessions.modifySession(request);
	//message = cmd + " " + (result ? "succeeded" : "failed");
	message = "Obsoleted command. Use 'export-archive' or choose 'Save as' from the menu instead.";
}
else
	message = "Unknown command " + cmd;

out.println(message);
JSPHelper.logRequestComplete(request);
%>
</body>
</html>
