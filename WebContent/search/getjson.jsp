<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@page language="java" import="edu.stanford.muse.util.Util"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<%@page language="java" import="java.io.BufferedReader"%>
<%@page language="java" import="java.io.FileReader"%>
<%@ page import="java.io.IOException" %>

<%
String str="";
try {
			String userHome = System.getProperty("user.home");  
			String file = userHome + "/tmp/addressbook.txt";
		    BufferedReader in = new BufferedReader(new FileReader(file));
		    str = in.readLine();
		    in.close();
		   
} 
catch (IOException e) {
	Util.print_exception(e, JSPHelper.log);
}
		
out.println (str);
%>