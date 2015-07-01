<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<% 
String style = (String) session.getAttribute("style"); 
String mode = (String) session.getAttribute("mode"); 

if ("games".equals(mode)) {%>
	<link href="/muse/css/amuse.css" rel="stylesheet" type="text/css"/>	
<% } else if (style == null || "default".equals(style)){ %>
	<link href="/muse/css/muse.css" rel="stylesheet" type="text/css"/>
<% } else { %>
	<link href="/muse/css/muse-<%=style%>.css" rel="stylesheet" type="text/css"/>
<% } %>
