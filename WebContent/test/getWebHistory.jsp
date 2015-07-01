<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<title>Web History</title>
<script src="js/jquery/jquery.js"></script>
<script src="js/jquery/jquery-ui.js"></script>

<jsp:include page="../css/css.jsp"/>
</head>
<body>

<%@include file="../header.jsp"%>
<p/> <p/> <p/> <p/>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>

<%
	String documentRootPath = application.getRealPath("/").toString();
     String inputPrefix = JSPHelper.convertRequestParamToUTF8(request.getParameter("folderPrefix"));
     String outputPrefix = documentRootPath + "/test";
     // compute redirect page
     String resultPage = inputPrefix + ".html";
     Archive driver =Archive.createArchive();
     driver.setup (JSPHelper.getBaseDir(null, request), new String[]{"-i", inputPrefix, "-o", outputPrefix});
     session.setAttribute("statusProvider", driver.getStatusProvider());

     // TOFIX the rest!!!
%>
<a href="test.html">Web history</a>
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<jsp:include page="../footer.jsp"/>

</body>
</html>
