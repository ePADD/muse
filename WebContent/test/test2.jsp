<%@ page contentType="text/html; charset=UTF-8"%>
<%JSPHelper.checkContainer(request);
request.setCharacterEncoding("UTF-8");%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>

<html>
<body>
<h1>टेस्ट 2</h1>

<%
String f = request.getParameter("f");
out.println ("f is " + f + "<br/>\n");
out.println ("converted f is " + JSPHelper.convertRequestParamToUTF8(f) + "<br/>\n");

String g = request.getParameter("g");
out.println ("g is " + g + "<br/>\n");
out.println ("converted g is " + JSPHelper.convertRequestParamToUTF8(g) + "<br/>\n");
%>

<%
%>

the end.

</body>
</html>