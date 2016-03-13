<%
if ("yes".equalsIgnoreCase(request.getParameter("consentagree")))
	response.sendRedirect("/muse/screen");
else
	response.sendRedirect("/muse/memorystudy/reject-consent.jsp");
%>