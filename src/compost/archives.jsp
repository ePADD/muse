<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="org.apache.log4j.*"%>
<%
	JSPHelper.logRequest(request);
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<title>Muse Settings</title>
<jsp:include page="css/css.jsp"/>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/stacktrace.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
</head>
<body class="fixed-width"> 
<jsp:include page="header.jsp"/>

<div class="panel shadow" style="padding:20px 50px 20px 50px">


<h2>Available archives</h2>
<br/>

<p/>

<% Collection<String> sessions = Sessions.listSessions(Sessions.getSessionDir(session));
if (!Util.nullOrEmpty(sessions))
{
	JSPHelper.log.info (sessions.size() + " sessions");

	String xml_fname = Sessions.getDefaultSessionDir() + File.separatorChar + "archives.xml"; // see below for sample
	if ((new File(xml_fname)).exists()) {
		String xsl_fname = getServletContext().getRealPath("css/archives.xsl");
		//<!-- x:transform xml = "${xml_fname}" xslt = "${xsl_fname}"/ : maybe simpler but need jstl -->
		JSPHelper.xsltTransform(xml_fname, xsl_fname, out);
	} else {
		out.println("No archives.");
	}

	/* Sample of archives.xml in the shared session dir
		<archives>
	  		<item>
				<name>TEST_CHECKMUSE</name>
	    		<number>EC000</number>
	    		<description>Gmail archive of checkmuse</description>
	  		</item>
		</archives>
	*/
}
%>
	<br/>

</div>

<% JSPHelper.logRequestComplete(request); %>

<jsp:include page="footer.jsp"/>
</body>
</html>
