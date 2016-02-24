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
<%@ page import="com.google.common.collect.Lists" %>
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
    class Hit{
        public String pn, npn;
        //doc info
        public String docId;
        public int numCorrs;
        public Date docDate;
        public void setHit(String personName, String nonPersonName){
            this.pn = personName;
            this.npn = nonPersonName;
        }
        public void setDocInfo(EmailDocument ed){
            docDate = ed.getDate();
            numCorrs = ed.getAllAddrs().size();
            docId = ed.getUniqueId();
        }

        @Override
        public boolean equals(Object obj){
            if(obj instanceof Hit){
                Hit h = (Hit)obj;
                if(this.pn == null || h.pn == null || this.npn==null || h.npn==null)
                    return false;
                return this.pn.equals(h.pn) && this.npn.equals(h.npn);
            }
            return false;
        }

        @Override
        public int hashCode(){
            return (this.pn+":::"+this.npn).hashCode();
        }

        @Override
        public String toString(){
            return pn + " ::: " + npn;
        }
    }

    try {
        boolean originalOnly = true;
        AddressBook ab = archive.addressBook;
        List<Document> docs = archive.getAllDocs();
        Map<String, String> links = new LinkedHashMap<String,String>();
        int di = 0;
        Collections.sort(docs);
        //read the most recent first
        docs = Lists.reverse(docs);
        //entity -> date of most recent mention
        Map<Integer,Date> seenContacts = new LinkedHashMap<Integer,Date>();
        Map<String,Date> seenEntities = new LinkedHashMap<String,Date>();
        Set<String> ownAddrs = ab.getOwnAddrs();
        Map<Hit, Date> hitDates = new LinkedHashMap<Hit, Date>();
        Map<String, Integer> freqs = new LinkedHashMap<String,Integer>();
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            if(ed.getDate()==null)
                continue;

            List<String> entities = new ArrayList<String>();
            entities.addAll(archive.getEntitiesInDoc(doc, NER.EORG, true, originalOnly));
            entities.addAll(archive.getEntitiesInDoc(doc, NER.ELOC, true, originalOnly));

            List<String> addrs = ed.getAllAddrs();
            for(String no: addrs){
                Contact c = ab.lookupByEmail(no);
                int cid = ab.getContactId(c);
                if(seenContacts.get(cid)==null){
                    //System.err.println("Putting: "+cid+", "+c.pickBestName()+", "+ed.getDate());
                    seenContacts.put(cid, ed.getDate());
                }
            }
            for(String e: entities)
                if(seenEntities.get(e)==null){
                   seenEntities.put(e, ed.getDate());
                   //System.err.println("Putting: "+e+", "+ed.getDate());
                }
            int dir = ed.sentOrReceived(ab);
            boolean sent = (dir & EmailDocument.SENT_MASK) != 0;

            Address[] corrs;
            if(sent)
                corrs = ed.to;
            else
                corrs = ed.from;
            Contact contact = null;
            if(corrs!=null){
                //generally only one from and to (others cc, bcc) are expected
                for(Address addr: corrs){
                    contact = ab.lookupByAddress(addr);
                }
            }

            for (String e : entities) {
                if(e == null)
                    continue;

                links.put(e, "../browse?term=\"" + e + "\"&sort_by=recent&searchType=original");
                //links.put(e, "../browse?contact=" + ab.getContactId(c) + "&sort_by=recent&searchType=original");

                //if this is the first time we are seeing both the correspondent and the entity, then mark this.
                if(e!=null && contact!=null){
                    Date npd = seenEntities.get(e);
                    Date cd = seenContacts.get(ab.getContactId(contact));
                    if(npd!=null && cd!=null){
                        Calendar cal1 = new GregorianCalendar();cal1.setTime(npd);
                        Calendar cal2 = new GregorianCalendar();cal2.setTime(cd);
                        Calendar cal3 = new GregorianCalendar();cal3.setTime(ed.getDate());

                        if(cal1.get(GregorianCalendar.MONTH)==cal2.get(GregorianCalendar.MONTH) && cal1.get(GregorianCalendar.YEAR)==cal2.get(GregorianCalendar.YEAR)
                        //without this condition, things that are mentioned in the same month, but not mentioned in the same mail will be printed if and when they occur in the same email.
                        //which is not desired as both the entities are seen before.
                        && ((cal1.get(GregorianCalendar.MONTH)==cal3.get(GregorianCalendar.MONTH) && cal1.get(GregorianCalendar.YEAR)==cal3.get(GregorianCalendar.YEAR))
                        ||  (cal2.get(GregorianCalendar.MONTH)==cal3.get(GregorianCalendar.MONTH) && cal2.get(GregorianCalendar.YEAR)==cal3.get(GregorianCalendar.YEAR))
                        )){
//                            System.err.println(e+"->"+contact.pickBestName()+":::"+cal2.get(GregorianCalendar.MONTH)+","+cal2.get(GregorianCalendar.YEAR));
//                            System.err.println(cal1.get(GregorianCalendar.MONTH)+","+cal1.get(GregorianCalendar.YEAR)+"\n"+ed.getDate());
                            Hit hit = new Hit();
                            hit.setHit(contact.pickBestName(), e);
                            hit.setDocInfo(ed);
                            //this condition does not seem to work
                            if(hitDates.get(hit)==null){
                                hitDates.put(hit, ed.getDate());
                                freqs.put(hit.toString(), 1);
                            }
                            else {
                                String str = hit.toString();
                                if(!freqs.containsKey(str)) freqs.put(str, 1);
                                else freqs.put(str, freqs.get(str)+1);
                            }
                        }
                    }
                }
            }

            if ((++di)%1000==0)
                out.println(di + " of " + docs.size() + " messages processed...<br/>");
        }
        List<Pair<Hit, Date>> srds = Util.sortMapByValue(hitDates);
        String prevMonth = null;
        for (Pair<Hit, Date> p : srds) {
            Calendar c = new GregorianCalendar();
            c.setTime(p.getSecond());
            String month = new SimpleDateFormat("MMM-YYYY").format(c.getTime());
            if (!month.equals(prevMonth))
                out.println ("</table><hr/><h2>" + month + "</h2><table><th><i>Non person name</i></th><th><i>Person name</i></th><th><i>Last message</i></th><th><i>Frequency</i></th><th><i># correspondents</i></th>");
            prevMonth = month;
            String link = "../browse?docId=" + p.getFirst().docId;
            out.println("<tr><td><a href='"+link+"' target='_blank'>" + Util.ellipsize(p.getFirst().npn, 50) + "</a></td><td>"+ Util.ellipsize(p.getFirst().pn,30) + "</td><td>" +
             edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getSecond()) +"</td><td>"+freqs.get(p.getFirst().toString())+"</td><td>"+ p.getFirst().numCorrs +"</td></tr>");
        }
        out.println ("</table>");
//        String sent = "Rockwell International Corp.'s Tulsa unit said it signed a tentative agreement extending its contract with Boeing Co. to provide structural parts for Boeing's 747 jetliners";
//        String[] tokens = NLPUtils.tokenize(sent);
//        String[] tags = NLPUtils.posTag(tokens);
//        String[] chunks = NLPUtils.chunker.chunk(tokens, tags);
//        out.println((new ChunkSample(tokens, tags, chunks)).nicePrint());
//
//        List<String> pns = NLPUtils.getAllProperNouns(sent);
//        out.println("Pns<br>");
//        for(String pn: pns){
//            out.println(pn+"<br>");
//        }
    } catch (Throwable e) {
        e.printStackTrace();
    }
%>
