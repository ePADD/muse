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
<%@ page import="com.google.common.collect.Multimap" %>
<%@ page import="com.google.common.collect.LinkedHashMultimap" %>
<%@ page import="oracle.jrockit.jfr.StringConstantPool" %>
<%@ page import="edu.stanford.muse.util.DictUtils" %>
<%@ page import="org.w3c.tidy.Dict" %>
<%@ page import="edu.stanford.muse.xword.ArchiveCluer" %>
<%@ page import="edu.stanford.muse.xword.Clue" %>
<%@ page import="java.io.File" %>
<%@ page import="edu.stanford.muse.ner.model.SVMModel" %>
<%@ page import="edu.stanford.muse.ner.model.NERModel" %>
<%@include file="../getArchive.jspf" %>
<%@include file="../getNERModel.jspf" %>

<%!

public static String[] stops = new String[]{"a", "an", "the", "and", "after", "before", "to", "of", "for"};
public static Set<String> stopsSet = new LinkedHashSet<String>(Arrays.asList(stops));
public static String[] allowedTitles = new String[]{"mr.", "ms.", "mrs.", "dr.", "prof."};
public static Set<String> allowedTitlesSet = new LinkedHashSet<String>(Arrays.asList(allowedTitles));

public static String canonicalize(String s) {
    s = s.toLowerCase();
    List<String> tokens = Util.tokenize(s);
    tokens.removeAll(stopsSet);
    if (Util.nullOrEmpty(tokens))
        return null;

    boolean allDict = true;
    for (String t: tokens) {
        if (t.startsWith("i'") || t.startsWith("you'")) // remove i've, you're, etc.
            return null;
        if (t.endsWith(".") && !allowedTitlesSet.contains(t))
            return null;
        if (!(DictUtils.fullDictWords.contains(t) || (t.endsWith("s") && DictUtils.fullDictWords.contains(t.substring(0, t.length()-1)))))
            allDict = false;
    }
    if (allDict)
        return null;

    // sanity check all tokens. any of them has i' or you' or has a disallowed title, just bail out.
    return Util.join(tokens, " ");
}

%>
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
<b>Non-person Entity listing by most recent occurrence (for testing only)</b><br/>
    <%
    try {
        archive.assignThreadIds();
        Lexicon lex = archive.getLexicon("default");

        String modelFile = archive.baseDir + File.separator + "models" + File.separator + SVMModel.modelFileName;
        out.println ("loading model...");
        out.flush();
		NERModel nerModel = SVMModel.loadModel(new File(modelFile));

        ArchiveCluer cluer = new ArchiveCluer(null, archive, null, lex);

        boolean originalOnly = true;
        AddressBook ab = archive.addressBook;
        List<Document> docs = archive.getAllDocs();
        Map<String, Date> entityToLastDate = new LinkedHashMap<String, Date>();
        Multimap<String, EmailDocument> entityToMessages = LinkedHashMultimap.create();
        Multimap<String, Long> entityToThreads = LinkedHashMultimap.create();
        Multimap<String, String> ceToDisplayEntity = LinkedHashMultimap.create();

        int di = 0;

        // sort by date
        Collections.sort(docs);

        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            List<String> entities = new ArrayList<String>(), personEntities = new ArrayList<String>();
            if (Util.nullOrEmpty(request.getParameter("locations")))
                entities.addAll(archive.getEntitiesInDoc(doc, NER.EORG, true, originalOnly));
            if (Util.nullOrEmpty(request.getParameter("orgs")))
                entities.addAll(archive.getEntitiesInDoc(doc, NER.ELOC, true, originalOnly));

            personEntities.addAll(archive.getEntitiesInDoc(doc, NER.EPER, true, originalOnly));

            entities.removeAll(personEntities);

            // get entities

            for (String e : entities) {
                if (Util.nullOrEmpty(e))
                    continue;
               if (e.length() > 10 && e.toUpperCase().equals(e))
                   continue; // all upper case, more than 10 letters, you're out.

               String ce = canonicalize(e); // canonicalize
               if (ce == null) {
                   JSPHelper.log.info ("Dropping entity: "  + ce);
                   continue;
               }

               ceToDisplayEntity.put(ce, e);
               entityToLastDate.put(ce, ed.date);
               entityToMessages.put(ce, ed);
               entityToThreads.put(ce, ed.threadID);

            }

            if ((++di)%1000==0)
                out.println(di + " of " + docs.size() + " messages processed...<br/>");
        }

        Multimap<String, String> tokenToCE = LinkedHashMultimap.create();
        for (String ce: ceToDisplayEntity.keySet()) {
            List<String> tokens = Util.tokenize(ce);
            for (String t: tokens)
                tokenToCE.put(t, ce);
        }

        Set<String> coreTokens = new LinkedHashSet<String>();
        for (String token: tokenToCE.keySet())
            if (tokenToCE.get(token).size() > HTMLUtils.getIntParam(request, "max", 2))
                coreTokens.add(token);

        List<Pair<String, Date>> srds = Util.sortMapByValue(entityToLastDate);
        String prevMonth = null;
        out.println ("<table>");
        for (Pair<String, Date> p : srds) {
            String ce = p.getFirst();
            String displayEntity = ceToDisplayEntity.get(ce).iterator().next();

            List<String> tokens = Util.tokenize(displayEntity);
            displayEntity = "";
            String fullAnswer = "";
            for (String t: tokens) {
                if (stopsSet.contains(t.toLowerCase()))
                    continue;
                if (coreTokens.contains(t.toLowerCase()))
                    displayEntity += "<span style=\"color:red\">" + t + "</span> ";
                else
                    displayEntity += t + " ";
                fullAnswer += t + " ";
            }
            displayEntity = displayEntity.trim();
            fullAnswer = fullAnswer.trim();

            Calendar c = new GregorianCalendar();
            c.setTime(p.getSecond());
            String month = new SimpleDateFormat("MMM-yyyy").format(c.getTime());
            if (!month.equals(prevMonth))
                out.println ("</table><hr/><h2>" + month + "</h2><table><th><i>Non person name</i></th><th><i>Last message</i></th><th><i># Messages</i></th><th><i># Threads</i></th>");
            prevMonth = month;
            int nMessages = entityToMessages.get(ce).size();
            int nThreads = entityToThreads.get(ce).size();

            String link = "../browse?term=\"" + fullAnswer + "\"&sort_by=recent&searchType=original";

            out.println("<tr><td><a href='" + link + "' target='_blank'>" + displayEntity + "</a></td><td>" + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getSecond()) + "</td><td>" + nMessages + "</td><td>" + nThreads + "</td></tr>");
            Clue clue = cluer.createClue(fullAnswer, new LinkedHashSet<String>(), nerModel);
            out.println ("<tr><td style=\"color:brown\" colspan=\"5\">" + (clue != null ? (clue.clue + " stats: " + Util.fieldsToString(clue.clueStats, false)) : "No clue") + "<br/><br/></td></tr>");
        }
        out.println ("</table>");
        out.println ("<p><hr/><b>Core tokens</b><p>");
        int i = 0;
        for (String t: coreTokens) {
            out.println (++i + ". " + t + ": ");
            for (String ce: tokenToCE.get(t))
                out.println (ce + ", ");
            out.println ("<br/>");
        }
    } catch (Throwable e) {
        e.printStackTrace();
    }
%>
