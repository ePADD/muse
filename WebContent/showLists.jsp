<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<title>Mailing Lists</title>
<script type="text/javascript" src="js/protovis.js"></script>
<script type="text/javascript" src="js/proto_funcs.js"></script>
<jsp:include page="css/css.jsp"/>
</head>
<body>
<jsp:include page="header.jsp"/>

<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>

<h3>Mailing Lists</h3>

<%
	JSPHelper.logRequest(request);
	Archive archive = JSPHelper.getArchive(session);

	if (archive == null)
	{
		if (!session.isNew())
			session.invalidate();
	//	session.setAttribute("loginErrorMessage", "Your session has timed out -- please click login again.");
	%>
	    <script type="text/javascript">window.location="index.jsp";</script>
	<%
		JSPHelper.log.error ("Error: session has timed out, archive = " + archive);
		return;
	}
	AddressBook addressBook = archive.addressBook;
	
	out.println ("<div style=\"background-color:#ffd0d0\">");
	for (Contact ci : addressBook.mailingListMap.keySet())
	{
		String descr = "";
		if ((ci.mailingListState & MailingList.SUPER_DEFINITE) != 0)
		{
			MailingList ml = addressBook.mailingListMap.get(ci);
			if (ml != null)
			{
				out.println ("<b>" + ci + "</b>: definite mailing list, " + ml.members.size() + " members<br/>");
				for (Contact c : ml.members)
					out.println (c + "<br/>");
				out.println ("<p/>");
			}
		}
	}

	out.println ("</div>");
	out.println ("<div style=\"background-color:#ffd0d0\">");

	out.println ("<hr/>");

	for (Contact ci : addressBook.mailingListMap.keySet())
	{
		if ((ci.mailingListState & MailingList.DEFINITE_NOT) != 0)
			continue;
		String descr = "";
		if ((ci.mailingListState & MailingList.DEFINITE) != 0)
		{
			MailingList ml = addressBook.mailingListMap.get(ci);
			if (ml != null)
			{
				out.println ("<b>" + ci + "</b>: probable mailing list, " + ml.members.size() + " members<br/>");
				for (Contact c : ml.members)
					out.println (c + "<br/>");
				out.println ("<p/>");
			}
		}
	}

	out.println ("<hr/>");

	for (Contact ci : addressBook.mailingListMap.keySet())
	{
		if ((ci.mailingListState & MailingList.DEFINITE_NOT) != 0)
			continue;
		if ((ci.mailingListState & MailingList.DEFINITE) != 0)
			continue;
		if ((ci.mailingListState & MailingList.MAYBE) != 0)
		{
			out.println ("<b>" + ci + "</b>: Maybe mailing list<br/>");
			MailingList ml = addressBook.mailingListMap.get(ci);
			for (Contact c : ml.members)
				out.println (c + "<br/>");
			out.println ("<p/>");
		}
	}
	%>

<%@include file="footer.jsp"%>

</body>
</html>
