<%@page language="java" import="java.io.*"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>
<%@include file="getArchive.jspf" %>

<%
	AddressBook addressBook = archive.addressBook;
	Collection<EmailDocument> allDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
	if (allDocs == null)
		allDocs = (Collection) archive.getAllDocs();
	String sort = request.getParameter("sort");
	boolean alphaSort = ("alphabetical".equals(sort));
	String bestName = addressBook.getBestNameForSelf();
%>

<html>
<head>
<title>Edit Correspondents</title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<script type="text/javascript" src="js/jquery.js"></script>
<script type="text/javascript" src="js/muse.js"></script>

</head>
<body class="fixed-width">
<jsp:include page="header.jsp"/>

<p>
<div style="text-align:center">
You can edit the address book on this page if you need to. All email addresses and names for a single person are entered on consecutive lines. 
Separate different people by a line containing only "--". The first person is considered the owner of the archive.
<br/>
<p>
<% String href = (alphaSort ? "edit-correspondents" : "edit-correspondents?sort=alphabetical"); String text = (alphaSort ? "Sort by email volume" : "Sort alphabetically"); %>
<a href="<%=href%>"><%=text%></a><br/>
<form method="post" action="info">
<textarea name="addressBookUpdate" id="text" cols="80" rows="40">
<%!
private static String dumpForContact(Contact c) {
	StringBuilder sb = new StringBuilder();
	for (String s: c.names)
		sb.append (Util.escapeHTML(s) + "\n");
	for (String s: c.emails)
		sb.append (Util.escapeHTML(s) + "\n");
	sb.append ("--\n");
	return sb.toString();
}
%>
<%
// always print first contact as self
Contact self = addressBook.getContactForSelf();
if (self != null)
	out.print(dumpForContact(self));

if (!alphaSort)
{
	for (Contact c: addressBook.sortedContacts((Collection) archive.getAllDocs()))
		if (c != self)
			out.print(dumpForContact(c));
}
else
{
	// build up a map of best name -> contact, sort it by best name and print contacts in the resulting order
	List<Contact> allContacts = addressBook.allContacts();
	Map<String, Contact> canonicalBestNameToContact = new LinkedHashMap<String, Contact>();
	for (Contact c: allContacts)
	{
		if (c == self)
			continue;
		String bestEmail = c.pickBestName();
		if (bestEmail == null)
			continue;
		canonicalBestNameToContact.put(bestEmail.toLowerCase(), c);		
	}
	
	List<Pair<String, Contact>> pairs = Util.mapToListOfPairs(canonicalBestNameToContact);
	Util.sortPairsByFirstElement(pairs);
	
	for (Pair<String, Contact> p: pairs)
	{
		Contact c = p.getSecond();
		if (c != self)
			out.print(dumpForContact(c));			
	}
}

	
%>
</textarea>
<br/>

<button type="submit">Submit</button>
</form>
</div>
<p/>
<br/>
<jsp:include page="footer.jsp"/>
</body>
</html>
