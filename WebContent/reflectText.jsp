<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%
if (ModeConfig.isPublicMode()) {
	// this browse page is also used by Public mode where the following set up may be requried. 
	String archiveId = request.getParameter("aId");
	Sessions.loadSharedArchiveAndPrepareSession(session, archiveId);
	// TODO: should also pass "aId" downstream to leadsAsJson.jsp also. but it still relies on emailDocs and maybe other session attributes, whose dependence should also be eliminated in public mode for being RESTful.
}
%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Bulk Search Results</title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
</head>
<body>
<% if (!ModeConfig.isPublicMode()) { %>
<jsp:include page="header.jsp"/>
<% } %>
<div>
<br/>
Annotated text. Click on highlighted terms to see the messages containing them in the archive.
<hr/>
<p>

<%
String req = request.getParameter("refText");
out.println (Util.escapeHTML(req).replace("\r", "").replace("\n", "<br/>\n"));
%>
</div>
<script type="text/javascript">
	window.MUSE_URL = '/muse';
</script>
<script type="text/javascript" src="js/muse-lens.user.js"></script>
</body>
</html>
