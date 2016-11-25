<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.muse.email.Contact" %>

<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="javax.mail.Address" %>
<%@ page import="com.google.common.collect.Multimap" %>
<%@ page import="com.google.common.collect.LinkedHashMultimap" %>
<%@include file="../getArchive.jspf" %>
<html>
<head>
    <link rel = "stylesheet" type ="text/css" href="memorystudy/css/memory.css">
    <link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
    <meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
    <link rel="icon" href="../images/ashoka-favicon.gif">
    <script type="text/javascript" src="js/jquery/jquery.js"></script>
    <script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
    <script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
    <script type="text/javascript" src="js/statusUpdate.js"></script>
    <script type="text/javascript" src="js/muse.js"></script>
    <script type="text/javascript" src="js/ops.js"></script>
    <title>Contact stats</title>
    <style> td { padding: 2px 25px 2px 10px} </style>
</head>
<b>Contact listing by most recent occurrence (for testing only)</b><br/>
    <%
    try {
        archive.assignThreadIds();
        boolean originalOnly = true;
        AddressBook ab = archive.addressBook;
        List<Document> docs = archive.getAllDocs();
        Multimap<Contact, EmailDocument> contactToMessages = LinkedHashMultimap.create();
        Multimap<Contact, Long> contactToThreads = LinkedHashMultimap.create();

        Map<Contact, Date> corrToLastDate = new LinkedHashMap<Contact, Date>();
        Map<Contact, Date> singleCorrToLastDate = new LinkedHashMap<Contact, Date>();

        int di = 0;

        // sort by date
        Collections.sort(docs);

        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            Set<Contact> toContacts = new LinkedHashSet<Contact>();

            if (ed.to != null){
                for(Address addr: ed.to){
                    Contact c = ab.lookupByAddress(addr);
                    toContacts.add(c);
                }
            }

            for (Contact c: toContacts) {
                corrToLastDate.put(c, ed.date);
                if (toContacts.size() == 1)
                    singleCorrToLastDate.put(c, ed.date);
                else
                    singleCorrToLastDate.remove(c); // remove this name from singlecorr, because it has been seen later in a multi-corr message
                contactToMessages.put(c, ed);
                if (ed.threadID > 0)
                    contactToThreads.put(c, ed.threadID);
            }

            if ((++di)%1000==0)
                out.println(di + " of " + docs.size() + " messages processed...<br/>");
        }

        List<Pair<Contact, Date>> srds = Util.sortMapByValue(corrToLastDate);
        String prevMonth = null;
        out.println ("<table>");
        for (Pair<Contact, Date> p : srds) {
            Contact contact = p.getFirst();
            Calendar c = new GregorianCalendar();
            c.setTime(p.getSecond());
            String month = new SimpleDateFormat("MMM-yyyy").format(c.getTime());
            if (!month.equals(prevMonth))
                out.println ("</table><hr/><h2>" + month + "</h2><table><th><i>Contact name</i></th><th><i>Last message</i></th><th><i># Messages</i></th><th><i># Threads</i></th>");
            prevMonth = month;
            int nMessages = contactToMessages.get(contact).size();
            int nThreads = contactToThreads.get(contact).size();

            String displayString = contact.pickBestName();
            boolean noProperName = displayString.contains("@");
            displayString = Util.ellipsize(displayString, 50);
            if (noProperName)
                displayString = "<span style=\"color:red\">" + displayString + "</span>";

            String link = "../browse?contact=" + archive.addressBook.getContactId(contact);
            out.println("<tr><td><a href='" + link + "' target='_blank'>" + displayString +
            "</a></td><td>" + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getSecond()) + "</td><td>" + nMessages + "</td><td>" + nThreads + "</td></tr>");
        }
        out.println ("</table>");
    } catch (Throwable e) {
        e.printStackTrace();
    }
%>
