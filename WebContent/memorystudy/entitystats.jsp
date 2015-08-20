<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.muse.email.Contact" %>
<%@ page import="edu.stanford.muse.ner.NER" %>
<%@include file="../getArchive.jspf" %>
<html>
<head>
<title>Entity stats</title>
</head>
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
                else
                    entities = ed.getAllNames();
                for (String e : entities) {
                    if (!"corr".equals(type))
                        links.put(e, "../newBrowse.jsp?term=\"" + e + "\"&sort_by=recent&searchType=original");
                    else {
                        Contact c = ab.lookupByEmailOrName(e);
                        e = c.pickBestName();
                        links.put(e, "../newBrowse.jsp?contactid=" + ab.getContactId(c) + "&sort_by=recent&searchType=original");
                    }

                    if (!timeStamps.containsKey(e)) {
                        timeStamps.put(e, new LinkedHashMap<Date, Integer>());
                    }
                    if (!timeStamps.get(e).containsKey(ed.getDate()))
                        timeStamps.get(e).put(ed.getDate(), 0);
                    timeStamps.get(e).put(ed.getDate(), timeStamps.get(e).get(ed.getDate()) + 1);

                    if (!recentDate.containsKey(ed.getDate()))
                        recentDate.put(e, ed.getDate());
                    Date d1 = ed.getDate();
                    Date d2 = recentDate.get(e);
                    if (d1.after(d2))
                        recentDate.put(e, d1);
                }
                if((++di)%100==0)
                    out.println(di+" of "+docs.size() + " <br/>");
            }
            List<Pair<String, Date>> srds = Util.sortMapByValue(recentDate);
            for (Pair<String, Date> p : srds) {
                out.println("<a href='"+links.get(p.getFirst())+"' target='_blank'>"+p.getFirst() + "</a>&nbsp:&nbsp" + p.getSecond() + "&nbsp #" + timeStamps.get(p.getFirst()).size() + "<br>");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
%>
