<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.apache.log4j.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%
response.setHeader("Cache-Control","no-cache");
response.setHeader("Pragma","no-cache");
response.setHeader("Expires","-1");
response.setContentType("application/x-javascript; charset=utf-8");


String date = request.getParameter("date");
String hour = request.getParameter("hour");

int activity[];

try {
	String sessiondate=(String)JSPHelper.getSessionAttribute(session,  "date" );
	if(sessiondate==null||!sessiondate.equals(date))
	{
		activity=new int[24];
		activity[Integer.parseInt(hour)-1]=1;
		session.setAttribute( "activity", activity );
	}
	else
	{
		activity=(int[])JSPHelper.getSessionAttribute(session,  "activity" );
		activity[Integer.parseInt(hour)-1]=1;
		
	}
	session.setAttribute( "date", date );
	
	
            
	
} catch (Exception e) {
	
}
%>
