<%
if ("yes".equalsIgnoreCase(request.getParameter("consentagree")))
	response.sendRedirect("eligibility");
else
	response.sendRedirect("/muse/memorystudy/reject-consent.jsp");
%>