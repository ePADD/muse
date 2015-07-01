<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.text.*"%>
<%@page language="java" import="java.io.*" %>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Wikileaks content matching</title>
</head>
<body>

<h1>Wikileaks cables</h1>

<%	
	String DATE_FORMAT = "EEE MMM d, yyyy";
	SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);

      new SimpleDateFormat(DATE_FORMAT);
	Collection<EmailDocument> docs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "wikileaks");
	if (docs == null)
	{
		ObjectInputStream ois = new ObjectInputStream (new FileInputStream("/Users/hangal/wikileaks/wikileaks.docs"));
		docs = (Collection<EmailDocument>) ois.readObject();
		session.setAttribute("wikileaks", docs);
		ois.close();
	}

	out.println ("<table>");

	int count = 0;
	for (EmailDocument ed: docs)
	{
		if (count++ > 500)
			break;
		
		out.println ("<tr>");
		out.println ("<td style=\"min-width:10em\">" + sdf.format(ed.date) + "</td>");
		out.println ("<td style=\"min-width:10em\">" + ed.from[0] + " </td>");
		out.println ("<td><a href=\"showWikileaks.jsp?idx=" + (count-1) + "\">" + ed.description + "</a></td>");
		out.println ("</tr>");
	}
	out.println ("</table>");
%>

</body>
</html>