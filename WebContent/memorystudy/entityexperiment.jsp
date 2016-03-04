<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.muse.email.Contact" %>
<%@ page import="edu.stanford.muse.ner.NER" %>
<%@ page import="edu.stanford.muse.util.Triple" %>
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
    /**TODO: This simple code looks for only third person pronouns after an entity mention.
    * Does not even check if the teh pronoun is referring to the mention
    * Would be cool to see if we can also recognise nominal mentions such as Google, <i>the company</i> my friend is working in.
    * */
    //considers only poeple names
    try {
            boolean originalOnly = true;
            AddressBook ab = archive.addressBook;
            List<Document> docs = archive.getAllDocs();
            Map<String, Map<Date, Integer>> timeStamps = new LinkedHashMap<String, Map<Date, Integer>>();
            //most recent date used on
            Map<String, Date> recentDate = new LinkedHashMap<String, Date>();
            Map<String, String> links = new LinkedHashMap<String,String>();
            Set<String> withMentions = new LinkedHashSet<String>();
            int di = 0;
            Collections.sort(docs);
            List<String> pronouns = Arrays.asList("he", "she", "his","hers", "him", "her");
            for (Document doc : docs) {
                EmailDocument ed = (EmailDocument) doc;
                List<String> entities = archive.getEntitiesInDoc(doc, NER.EPER, true, originalOnly);
                List<Triple<String,Integer,Integer>> triples = new ArrayList<>();
                String contents = archive.getContents(doc, false);
                if(triples == null){
                    continue;
                }
                int ti=0;
                for(Triple<String,Integer,Integer> t: triples){
                    String e = t.getFirst();
                    if(e == null || !entities.contains(e))
                        continue;
                    Contact c = ab.lookupByName(e);
                    //filter away all the contacts from people names
                    if(c!=null)
                        continue;

                    String text = "";
                    if(ti<(triples.size()-1)){
                        Triple<String,Integer,Integer> nt = triples.get(ti+1);
                        int start = t.getThird();
                        int end = nt.getSecond();
                        if(start>=0 && end<contents.length() && end>=0 && start<contents.length() && start<=end)
                            text = contents.substring(start, Math.min(end, start+200));
                    }
                    else{
                        int start = t.getThird();
                        if((start>=0) && start<contents.length())
                            text = contents.substring(start, Math.min(contents.length(), start+200));
                    }
                    if(t.getSecond()<0 || t.getThird()>contents.length()){
                        out.println("This is a strange tuple: ("+t.getFirst()+","+t.getSecond()+","+t.getThird()+")&nbsp"+contents.length()+"<br/>");
                    }
                    if(!withMentions.contains(e)){
                        //look for mentions
                        if(text.length()>0){
                            String[] words = text.split("\\s+");
                            for(String w: words)
                                if(pronouns.contains(w.toLowerCase())){
                                    withMentions.add(e);
                                    break;
                                }
                        }
                    }
                    links.put(e, "../browse?term=\"" + e + "\"&sort_by=recent&searchType=original");

                    if (!timeStamps.containsKey(e)) {
                        timeStamps.put(e, new LinkedHashMap<Date, Integer>());
                    }
                    if (!timeStamps.get(e).containsKey(ed.getDate()))
                        timeStamps.get(e).put(ed.getDate(), 0);
                    timeStamps.get(e).put(ed.getDate(), timeStamps.get(e).get(ed.getDate()) + 1);

                    recentDate.put(e, ed.getDate());
                    ti++;
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
                    out.println ("</table><hr/><h2>" + month + "</h2><table><th><i>Name</i></th><th><i>Last message</i></th><th><i>Total Messages</i></th><th><i>With mention</i></th>");
                prevMonth = month;
                out.println("<tr><td><a href='"+links.get(p.getFirst())+"' target='_blank'>" + Util.ellipsize(p.getFirst(), 30) + "</a></td><td>" + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getSecond()) + "</td><td style=\"text-align:right\">" + timeStamps.get(p.getFirst()).size() + "</td><td>"+withMentions.contains(p.getFirst())+"</td></tr>");
            }
            out.println ("</table>");
     } catch (Throwable e) {
            e.printStackTrace();
        }
%>
