<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@include file="../getArchive.jspf" %>
<head>
<title>Entity stats</title>
</head>
<%
    String type = request.getParameter("type");
    if(type==null){
        out.println("<a href='entitystats.jsp?type=en_people' target='_blank'>People</a><br>");
        out.println("<a href='entitystats.jsp?type=en_loc' target='_blank'>Locations</a><br>");
        out.println("<a href='entitystats.jsp?type=en_org' target='_blank'>Organization</a><br>");
        out.println("<a href='entitystats.jsp?type=corr' target='_blank'>Correspondents</a><br>");
    }
    else {
        try {
            List<Document> docs = archive.getAllDocs();
            Map<String, Map<Date, Integer>> timeStamps = new LinkedHashMap<String, Map<Date, Integer>>();
            //most recent date used on
            Map<String, Date> recentDate = new LinkedHashMap<String, Date>();
            for (Document doc : docs) {
                EmailDocument ed = (EmailDocument) doc;
                List<String> entities;
                if(!"corr".equals(type)) {
                    if("en_people".equals(type))
                        entities = archive.getEntitiesInDoc(doc, type, true);
                    else
                        entities = archive.getQualityEntitiesInDoc(doc, type, true);
                }
                else
                    entities = ed.getAllNames();
                for (String e : entities) {
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
            }
            List<Pair<String, Date>> srds = Util.sortMapByValue(recentDate);
            for (Pair<String, Date> p : srds) {
                out.println(p.getFirst() + "&nbsp:&nbsp" + p.getSecond() + "&nbsp #" + timeStamps.get(p.getFirst()).size() + "<br>");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
%>