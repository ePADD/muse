<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%	JSPHelper.logRequest(request);  
String message = request.getParameter("message"); %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<jsp:include page="css/css.jsp"/>
<body>
<%@include file="header.html"%>
<hr style="margin-top:-6px">

Muse got a shutdown request <%=!Util.nullOrEmpty(message) ? message: "" %> <br/> Terminating now... Goodbye!

<%
	out.flush();
	Thread.sleep (2000);  // header doesn't render if we shut down the server immediately
	// send a cr-lf to port 8079
	try {
	    Socket s = new Socket(InetAddress.getByName("127.0.0.1"), 8079);
	    OutputStream sout = s.getOutputStream();
	    System.out.println("*** sending jetty stop request");
	    sout.write(("\r\n").getBytes());
	    sout.flush();
	    s.close();
	    %>
	    Goodbye! <br/>
	    Click <a href="http://mobisocial.stanford.edu/~hangal/muse/muse.jnlp">here</a> to
	    launch the program again.
		<%
	}
	catch (Exception e)	{
		out.println ("<br/>Sorry, unable to shut down the Muse Jetty server.<br/> " + e.toString().replaceAll("\n", "<br/>\n") + "<br/>Please try to kill any java/javaw processes manually.");
	}
%>