<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
    
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%	
JSPHelper.setPageUncacheable(response);
%>
<!DOCTYPE html>
<html>
<head>
<title>Verify your network connections</title>
<jsp:include page="css/css.jsp"/>
</head>
<body>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/jquery/jquery.js"></script>

<span class="inprogress">Verifying email and network setup <img width="15px" src="images/spinner.gif"/></span>
<%
out.flush();
Pair<Boolean, String> result = VerifyEmailSetup.run();
boolean success = result.getFirst();
String details = result.getSecond();
// don't show the password
details = details.replaceAll ("ga104tes", "*********");
details = Util.escapeHTML(details);
details = details.replaceAll ("\n", "\n<br/>");
if (success) 
	out.println ("<span style=\"color:green\">Your computer is able to contact Gmail correctly.</span>");
else
{
	out.println ("<span style=\"color:red\">Your computer is unable to contact Gmail correctly.</span>");
 %>
<p>
<div class="details" style="display:hidden">
	Details: <br/>
	<%=details %> 
</div>
<% } %>

<script type="text/javascript">
	$('.inprogress').hide();
</script>
<p>

</body>
</html>
