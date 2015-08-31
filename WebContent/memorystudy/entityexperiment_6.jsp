<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.muse.email.Contact" %>
<%@ page import="edu.stanford.muse.ner.NER" %>

<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="javax.mail.Address" %>
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
    <style> td { padding: 2px 25px 2px 10px} </style>
</head>
<b>Entity listing by most recent mention (for testing only)</b><br/>
    <%
    try {
        boolean originalOnly = true;
        AddressBook ab = archive.addressBook;
        List<Document> docs = archive.getAllDocs();
        //most recent date used on
        Map<String, Date> recentDate = new LinkedHashMap<String, Date>();
        //most recent correspondent mentioned with
        Map<String, String> recentCorr = new LinkedHashMap<String,String>();
        Map<String, String> links = new LinkedHashMap<String,String>();
        int di = 0;
        Collections.sort(docs);
        Set<String> ownAddr = archive.ownerEmailAddrs;
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            List<String> entities = new ArrayList<String>();
            entities.addAll(archive.getEntitiesInDoc(doc, NER.EORG, true, originalOnly));
            entities.addAll(archive.getEntitiesInDoc(doc, NER.ELOC, true, originalOnly));

            for (String e : entities) {
                if(e == null)
                    continue;

                links.put(e, "../browse?term=\"" + e + "\"&sort_by=recent&searchType=original");
                int dir = ed.sentOrReceived(ab);
                boolean sent = (dir & EmailDocument.SENT_MASK) != 0;

                Address[] corrs;
                if(sent)
                    corrs = ed.to; //ed.getAllNonOwnAddrs(ownAddr);
                else
                    corrs = ed.from;
                String corr = null;
                if(corrs!=null){
                    for(Address addr: corrs){
                        Contact c = ab.lookupByAddress(addr);
                        if (c != null)
                            corr = c.pickBestName();
                    }
                }
                //links.put(e, "../browse?contact=" + ab.getContactId(c) + "&sort_by=recent&searchType=original");

                if(corr!=null){
                    recentDate.put(e, ed.getDate());
                    recentCorr.put(e, corr);
                }
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
                out.println ("</table><hr/><h2>" + month + "</h2><table><th><i>Non person name</i></th><th><i>Person name</i></th><th><i>Last message</i></th>");
            prevMonth = month;
            out.println("<tr><td><a href='"+links.get(p.getFirst())+"' target='_blank'>" + Util.ellipsize(p.getFirst(), 50) + "</a></td><td>"+ Util.ellipsize(recentCorr.get(p.getFirst()),30) + "</td><td>" + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getSecond()) + "</td></tr>");
        }
        out.println ("</table>");
    } catch (Throwable e) {
        e.printStackTrace();
    }
%>
