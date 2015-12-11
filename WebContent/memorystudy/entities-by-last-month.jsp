<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.ner.NER" %>

<%@ page import="com.google.common.collect.Multimap" %>
<%@ page import="com.google.common.collect.LinkedHashMultimap" %>
<%@ page import="edu.stanford.muse.util.DictUtils" %>
<%@ page import="edu.stanford.muse.xword.ArchiveCluer" %>
<%@ page import="edu.stanford.muse.xword.Clue" %>
<%@ page import="java.io.File" %>
<%@ page import="edu.stanford.muse.ner.model.SVMModel" %>
<%@include file="../getArchive.jspf" %>
<%@include file="../getNERModel.jspf" %>

<%!

    /* small util class -- like clue but allows answers whose clue is null */
    class ClueInfo implements Comparable<ClueInfo> {
        Clue clue;
        String link, displayEntity;
        int nMessages, nThreads;
        Date lastSeenDate;
        boolean hasCoreTokens;

        public String toHTMLString() {
            return "<tr><td><a href='" + link + "' target='_blank'>" + displayEntity + "</a></td><td>" + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(lastSeenDate) + "</td><td>" + nMessages + "</td><td>" + nThreads + "</td><td>" + (clue != null ? clue.clueStats.finalScore : "-") + "</td></tr>"
            + "<tr><td class=\"clue\" colspan=\"6\">" + (clue != null ? (clue.clue + "<br/><br/><div class=\"stats\"> stats: " + Util.fieldsToString(clue.clueStats, false)) : "No clue") + "</div><br/><br/></td></tr>";
        }

        public int compareTo(ClueInfo c2) {
            // all answers with core tokens should be last in sort order
            if (this.hasCoreTokens && !c2.hasCoreTokens)
                return 1;
            if (c2.hasCoreTokens && !this.hasCoreTokens)
                return -1;

            // all answers with clues should come towards the end
            if (this.clue == null && c2.clue != null)
                return 1;
            if (this.clue != null && c2.clue == null)
                return -1;
            if (this.clue == null && c2.clue == null)
                return displayEntity.compareTo(c2.displayEntity); // just some order, as long as it is consistent

            if (this.clue != null && c2.clue != null)
                return (this.clue.clueStats.finalScore > c2.clue.clueStats.finalScore) ? -1 : (c2.clue.clueStats.finalScore > this.clue.clueStats.finalScore ? 1 : 0);
            return 0;
        }
    }

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
    <style>
        td { padding: 2px 25px 2px 10px}
        .stats { font-size: 10pt; color: gray; }
        .clue { font-size: 14pt; color: #2A54A5; }
        .interval {font-color: black; font-weight: bold;}
    </style>
</head>
<h2>Length related params</h2>
<input type="text" id="len1" placeholder="-100.0,-10.0,0" />
<br>
<h2>Weight for exclamation and similey</h2>
<input type="text" id="es1" placeholder="7.0,7.0"/>

<h2>Weight for number of taboo words found</h2>
<input type="text" id="t1" placeholder="-20.0"/>

<>

<b>Non-person Entity listing by most recent occurrence (for testing only)</b><br/>
    <%
    try {
        archive.assignThreadIds();
        Lexicon lex = archive.getLexicon("default");

        String modelFile = archive.baseDir + File.separator + "models" + File.separator + SVMModel.modelFileName;
        out.println ("loading model...");
        out.flush();

        ArchiveCluer cluer = new ArchiveCluer(null, archive, null, lex);

        boolean originalOnly = true;
        List<Document> docs = archive.getAllDocs();
        Map<String, Date> entityToLastDate = new LinkedHashMap<String, Date>();
        Multimap<String, EmailDocument> entityToMessages = LinkedHashMultimap.create();
        Multimap<String, Long> entityToThreads = LinkedHashMultimap.create();
        Multimap<String, String> ceToDisplayEntity = LinkedHashMultimap.create();

        int di = 0;

        // sort by date
        Collections.sort(docs);

        Date earliestDate = null, latestDate = null;

        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            if (earliestDate == null || ed.date.before(earliestDate))
                earliestDate = ed.date;
            if (latestDate == null || ed.date.after(latestDate))
                latestDate = ed.date;

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

        JSPHelper.log.info ("earliest date = " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(earliestDate));
        JSPHelper.log.info ("latest date = " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(latestDate));

        Multimap<String, String> tokenToCE = LinkedHashMultimap.create();
        for (String ce: ceToDisplayEntity.keySet()) {
            List<String> tokens = Util.tokenize(ce);
            for (String t: tokens)
                tokenToCE.put(t, ce);
        }

         // compute coreTokens
        Set<String> coreTokens = new LinkedHashSet<String>();
        {
            JSPHelper.log.info ("Computing core tokens");
            int maxOccurrences = HTMLUtils.getIntParam(request, "max", 2);
            for (String token: tokenToCE.keySet())
                if (tokenToCE.get(token).size() > maxOccurrences)
                    coreTokens.add(token);
            JSPHelper.log.info ("#core tokens " + coreTokens.size());
        }

        // Compute date intervals
        int DAYS_PER_INTERVAL = HTMLUtils.getIntParam(request, "intervalDays", 30);
        List<Pair<Date, Date>> intervals = new ArrayList<Pair<Date, Date>>();
        {
            JSPHelper.log.info ("computing time intervals");
            Date closingDate = latestDate;

            JSPHelper.log.info ("closing = " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(closingDate));
            while (earliestDate.before(closingDate)) {
                Calendar cal = new GregorianCalendar();
                cal.setTime(closingDate); // this is the time of the last sighting of the term
                    // scroll to the beginning of this month
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                Date endDate = cal.getTime();

                cal.add(Calendar.DATE, (1-DAYS_PER_INTERVAL)); // 1- because we want from 0:00 of first date to 23:59 of last date
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                Date startDate = cal.getTime();

                intervals.add(new Pair<Date, Date>(startDate, endDate));
                // ok we got an interval

                // closing date for the next interval is 1 day before endDate
                cal.add(Calendar.DATE, -1);
                closingDate = cal.getTime();
            }
            JSPHelper.log.info ("done computing intervals, #time intervals: " + intervals.size());
            for (Pair<Date, Date> p: intervals)
               JSPHelper.log.info ("Interval: " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getFirst()) + " - " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(p.getSecond()));
        }

        // initialize clueInfos to empty lists
        List<ClueInfo> clueInfos[] = new ArrayList[intervals.size()];
        for (int i = 0; i < intervals.size(); i++) {
            clueInfos[i] = new ArrayList<ClueInfo>();
        }

        // generate clueInfos for each entity
        for (String ce: entityToLastDate.keySet()) {
            Date lastSeenDate = entityToLastDate.get(ce);

            boolean hasCoreTokens = false;
            // compute displayEntity (which has red for core words) and fullAnswer, which is a simple string
            String displayEntity = "", fullAnswer = "";
            {
                List<String> tokens = Util.tokenize(ceToDisplayEntity.get(ce).iterator().next());
                for (String t: tokens) {
                    if (stopsSet.contains(t.toLowerCase()))
                        continue;
                    if (coreTokens.contains(t.toLowerCase())) {
                        hasCoreTokens = true;
                        displayEntity += "<span style=\"color:red\">" + t + "</span> ";
                    } else
                        displayEntity += t + " ";
                    fullAnswer += t + " ";
                }
                displayEntity = displayEntity.trim();
                fullAnswer = fullAnswer.trim();
            }

            // which interval does this date belong to?
            int interval = -1;
            Date intervalStart = null, intervalEnd = null;
            {
                int i = 0;
                for (Pair<Date, Date> p : intervals)
                {
                    intervalStart = p.getFirst();
                    intervalEnd = p.getSecond();

                    if ((intervalStart.before(lastSeenDate) && intervalEnd.after(lastSeenDate)) || intervalStart.equals(lastSeenDate) || intervalEnd.equals(lastSeenDate))
                    {
                        interval = i;
                        break;
                    }
                    i++;
                }
            }
            if (interval < 0 || interval == intervals.size())
                JSPHelper.log.info ("What, no interval!? for " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(lastSeenDate));

            ClueInfo ci = new ClueInfo();
            ci.link = "../browse?term=\"" + fullAnswer + "\"&sort_by=recent&searchType=original";;
            ci.displayEntity = displayEntity;
            ci.lastSeenDate = lastSeenDate;
            ci.nMessages = entityToMessages.get(ce).size();;
            ci.nThreads = entityToThreads.get(ce).size();

            ci.clue = cluer.createClue(fullAnswer, new LinkedHashSet<String>(), nerModel, intervalStart, intervalEnd, HTMLUtils.getIntParam(request, "sentences", 3));
            ci.hasCoreTokens = hasCoreTokens;
            clueInfos[interval].add(ci);
         }

         // now print out the clue tables for all intervals
         {
             for (int i = 0; i < clueInfos.length; i++) {
                 Collections.sort(clueInfos[i]);
                 out.println ("<h2>Interval #" + i + ": " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(intervals.get(i).getFirst()) + " - " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(intervals.get(i).getSecond()) + "</h2>");
                 out.println ("<table><th><i>Non person name</i></th><th><i>Last message</i></th><th><i># Messages</i></th><th><i># Threads</i></td><td>Clue score</td></th>");
                 for (ClueInfo ci: clueInfos[i]) {
                     if (request.getParameter("hideCoreTokens") != null && ci.hasCoreTokens)
                        continue;
                     if (request.getParameter("hideNoClue") != null && ci.clue == null)
                        continue;
                     out.println (ci.toHTMLString());
                 }
                 out.println ("</table><hr/>");
             }
         }

        // dump core tokens
        {
            out.println ("<p><hr/><b>Core tokens</b><p>");
            int i = 0;
            for (String t: coreTokens) {
                out.println (++i + ". " + t + ": ");
                for (String ce: tokenToCE.get(t))
                    out.println (ce + ", ");
                out.println ("<br/>");
            }
        }
    } catch (Throwable e) {
        e.printStackTrace();
    }
%>
</body>
