<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@page language="java" import="edu.stanford.muse.slant.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.apache.log4j.*"%>

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
			
}
		
out.println (str);
%>