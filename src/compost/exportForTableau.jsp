<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>    
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Insert title here</title>
</head>
<body>


<%
	Collection<EmailDocument> allDocs = (Collection) JSPHelper.getSessionAttribute(session, "emailDocs"); 
AddressBook ab = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
Contact oc = ab.getContactForSelf();

for (EmailDocument ed: allDocs)
{
	Date d = ed.date;
	GregorianCalendar gc = new GregorianCalendar();
	gc.setTime(d);

	String s = "";
	s += gc.get(Calendar.DAY_OF_WEEK);
	s += ", ";
	s += gc.get(Calendar.DAY_OF_MONTH);
	s += ", ";
	s += gc.get(Calendar.MONTH);
	s += ", ";
	s += gc.get(Calendar.YEAR);
	s += ", ";
	s += gc.get(Calendar.HOUR_OF_DAY);
	s += ", ";
	int temp = ed.sentOrReceived(ab);
	s += (temp & EmailDocument.RECEIVED_MASK);
	s += ", ";
	s += (temp & EmailDocument.SENT_MASK);
	s += ", ";
	s += (ed.links != null ? ed.links.size() : 0);
	s += ", ";
	s += (ed.attachments != null ? ed.attachments.size() : 0);
	s += ("\"" + ed.description + "\"");
	s += ", ";
	
	List<String> addrs = ed.getAllAddrs();
	addrs = ab.convertToCanonicalAddrs(addrs);
	addrs = ab.removeOwnAddrs(addrs);
	addrs = Util.removeDups(addrs);
	addrs = EmailUtils.removeMailingLists(addrs);
//	addrs = EmailUtils.removeUndisclosedRecipients(addrs);
	if (addrs.size() == 0) // can happen - no non-own addrs
		continue;


}
%>


</body>
</html>