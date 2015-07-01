<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>

<%
	JSPHelper.logRequest(request);
	Archive archive = JSPHelper.getArchive(session);
	if (archive == null)
	{
		if (!session.isNew())
			session.invalidate();
	%>
	    <script type="text/javascript">window.location="index.jsp";</script>
	<%
		System.err.println ("Error: session has timed out, archive is null");
		return;
	}
	AddressBook addressBook = archive.addressBook;
	Collection<DatedDocument> allDocs = (Collection) JSPHelper.getSessionAttribute(session, "emailDocs");
%>

<html>
<head>
<title>Grouper moves</title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/protovis.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/proto_funcs.js"></script>
<script type="text/javascript" src="js/move-graph.js"></script>
</head>
<body>

<%
// NOTE NOTE NOTE this doesn't work any more since we're not storing the grouper in the session
// can get it back if needed
Grouper g = (Grouper) JSPHelper.getSessionAttribute(session, "grouper");
String json = g.getUnanonmyizedGrouperStats();
%>

<script type="text/javascript+protovis">
muse.log('drawing');
var stats = <%=json%>;
var search_func = draw_move_graph(stats);
muse.log('done');
</script>

<div id="message" class="rounded" style="display:none;padding:10px;background-color:rgba(0,0,0,0.5); position:fixed;left:300px;top:20px;width:520px">
</div>

<div class="rounded" style="display:block;padding:10px;background-color:rgba(0,0,0,0.5); position:fixed;left:860px;top:20px;width:200px">
Search
<input type="text" id="searchTerm" name="searchTerm" onkeyup="search_func(this.value)" size="12"/>
</div>

<br/>
<br/>
<br/>

<hr/>

<jsp:include page="footer.jsp"/>
</body>
</html>
