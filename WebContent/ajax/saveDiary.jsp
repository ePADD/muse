<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<% String diaryContents = request.getParameter("diaryContents");
	JSPHelper.log.info ("diary contents: " + diaryContents);
	session.setAttribute("diaryContents", request.getParameter("diaryContents"));
%>