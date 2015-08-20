<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.muse.email.Contact" %>
<%@ page import="edu.stanford.muse.ner.NER" %>
<%@ page import="com.google.common.collect.*" %>

<%@ page import="java.text.SimpleDateFormat" %>
<%@include file="../getArchive.jspf" %>
<html>
<head>
    <link rel = "stylesheet" type ="text/css" href="memorystudy/css/screen.css">
    <link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
    <meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
    <link rel="icon" href="memorystudy/images/stanford-favicon.gif">
    <jsp:include page="../css/css.jsp"/>
    <script type="text/javascript" src="js/jquery/jquery.js"></script>
    <script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
    <script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
    <script type="text/javascript" src="js/statusUpdate.js"></script>
    <script type="text/javascript" src="js/muse.js"></script>
    <script type="text/javascript" src="js/ops.js"></script>
    <title>Entity stats</title>
    <style> td { padding: 2px 10px 2px 10px} </style>
</head>
<b>Entity listing by most recent mention (for testing only)</b><br/>
<%
    String type = request.getParameter("type");
    if(type==null){
        out.println("<a href='entitystats.jsp?type="+NER.EPER+"' target='_blank'>People</a><br>");
        out.println("<a href='entitystats.jsp?type="+NER.ELOC+"' target='_blank'>Locations</a><br>");
        out.println("<a href='entitystats.jsp?type="+NER.EORG+"' target='_blank'>Organization</a><br>");
        out.println("<a href='entitystats.jsp?type=corr' target='_blank'>Correspondents</a><br>");
    }
    else {
        try {
            boolean originalOnly = true;
            AddressBook ab = archive.addressBook;
            List<Document> docs = archive.getAllDocs();
            Map<String, Map<Date, Integer>> timeStamps = new LinkedHashMap<String, Map<Date, Integer>>();
            //most recent date used on
            Map<String, Date> recentDate = new LinkedHashMap<String, Date>();
            Map<String, String> links = new LinkedHashMap<String,String>();
            int di = 0;
            Collections.sort(docs);
            for (Document doc : docs) {
                EmailDocument ed = (EmailDocument) doc;
                List<String> entities;
                if(!"corr".equals(type)) {
                    if(NER.EPER.equals(type)) {
                        entities = archive.getEntitiesInDoc(doc, type, true, originalOnly);
                    }
                    else
                        entities = archive.getQualityEntitiesInDoc(doc, type, true, originalOnly);
                }
                else {
                    entities = ed.getAllAddrs();
//                    Multimap<Contact, Date> solos = HashMultimap.create();
                    Collection<Contact> contacts = ed.getParticipatingContactsExceptOwn(ab);
                    if (contacts.size() == 1) {
                        // solos
//                        for (Contact c: contacts) {
 //                           solos.put(contacts.iterator().next(), ed.getDate());
                       // }
                    }
                }

                for (String e : entities) {
                    if(e == null)
                        continue;
                    if(NER.EPER.equals(type)){
                        Contact c = ab.lookupByName(e);
                        //filter away all the contacts from people names
                        if(c!=null)
                            continue;
                    }

                    if (!"corr".equals(type))
                        links.put(e, "../browse?term=\"" + e + "\"&sort_by=recent&searchType=original");
                    else {
                        Contact c = ab.lookupByEmailOrName(e);
                        if (c != null)
                            e = c.pickBestName();
                        links.put(e, "../browse?contact=" + ab.getContactId(c) + "&sort_by=recent&searchType=original");
                    }

                    if (!timeStamps.containsKey(e)) {
                        timeStamps.put(e, new LinkedHashMap<Date, Integer>());
                    }
                    if (!timeStamps.get(e).containsKey(ed.getDate()))
                        timeStamps.get(e).put(ed.getDate(), 0);
                    timeStamps.get(e).put(ed.getDate(), timeStamps.get(e).get(ed.getDate()) + 1);

                    //if (!recentDate.containsKey(ed.getDate()))
                    recentDate.put(e, ed.getDate());
//                    Date d1 = ed.getDate();
//                    Date d2 = recentDate.get(e);
//                    if (d1.after(d2))
//                        recentDate.put(e, d1);
                }
                if ((++di)%1000==0)
                    out.println(di + " of " + docs.size() + " messages processed...<br/>");
            }
            List<Pair<String, Date>> srds = Util.sortMapByValue(recentDate);
            String prevMonth = null;
            for (Pair<String, Date> p : srds) {
                Calendar c = new GregorianCalendar();
                c.setTime(p.getSecond());
                String month = new SimpleDateFormat("MMM-YYYY").format(c.getTime());
                if (!month.equals(prevMonth))
                    out.println ("</table><hr/><h2>" + month + "</h2><table><th><i>Name</i></th><th><i>Last message</i></th><th><i>Total Messages</i></th>");
                prevMonth = month;
                out.println("<tr><td><a href='"+links.get(p.getFirst())+"' target='_blank'>" + Util.ellipsize(p.getFirst(), 30) + "</a></td><td>" + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getSecond()) + "</td><td style=\"text-align:right\">" + timeStamps.get(p.getFirst()).size() + "</td></tr>");
            }
            out.println ("</table>");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
%>
