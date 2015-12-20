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
<%@ page import="edu.stanford.muse.xword.ClueEvaluator" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="javax.mail.Address" %>
<%@ page import="edu.stanford.muse.email.Contact" %>
<%@include file="../getArchive.jspf" %>
<%@include file="../getNERModel.jspf" %>

<%!

    /* small util class -- like clue but allows answers whose clue is null */
    class ClueInfo implements Comparable<ClueInfo> {
        //clues corrsponding to different choice of sentences in the context
        Clue[] clues;
        String link, displayEntity;
        int nMessages, nThreads;
        Date lastSeenDate;
        boolean hasCoreTokens;

        public String toHTMLString() {
            String str = "";
            for(Clue clue: clues){
                str += "<tr><td><a href='" + link + "' target='_blank'>" + displayEntity + "</a></td><td>" + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(lastSeenDate) + "</td><td>" + nMessages + "</td><td>" + nThreads + "</td><td>" + (clue != null ? clue.clueStats.finalScore : "-") + "</td></tr>"
                        + "<tr><td class=\"clue\" colspan=\"6\">" + (clue != null ? (clue.clue + "<br/><br/><div class=\"stats\"> stats: " + Util.fieldsToString(clue.clueStats, false)) : "No clue") + "</div><br/><br/></td></tr><br>";
            }
            return str;
        }

        public int compareTo(ClueInfo c2) {
            // all answers with core tokens should be last in sort order
            if (this.hasCoreTokens && !c2.hasCoreTokens)
                return 1;
            if (c2.hasCoreTokens && !this.hasCoreTokens)
                return -1;

            if(this.clues == null || c2.clues == null) {
                if (this.clues == c2.clues) return 0;
                else return (this.clues==null)?1:-1;
            }
            if(this.clues.length == 0 || c2.clues.length == 0) {
                if (c2.clues.length == this.clues.length)
                    return 0;
                else return (this.clues.length > c2.clues.length)? -1 : 1;
            }

            //decide based on their first clues
            Clue clue = this.clues[0], cclue = c2.clues[0];
            // all answers with clues should come towards the end
            if (clue == null && cclue != null)
                return 1;
            if (clue != null && cclue == null)
                return -1;
            if (clue == null && cclue == null)
                return displayEntity.compareTo(c2.displayEntity); // just some order, as long as it is consistent

            if (clue != null && cclue.clue != null)
                return (clue.clueStats.finalScore > cclue.clueStats.finalScore) ? -1 : (cclue.clueStats.finalScore > clue.clueStats.finalScore ? 1 : 0);
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
    <script type="text/javascript" src="../js/jquery/jquery.js"></script>
    <script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
    <script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
    <script type="text/javascript" src="../js/statusUpdate.js"></script>
    <script type="text/javascript" src="../js/muse.js"></script>
    <script type="text/javascript" src="js/ops.js"></script>
    <title>Entity stats</title>
    <style>
        td { padding: 2px 25px 2px 10px}
        .stats { font-size: 10pt; color: gray; }
        .clue { font-size: 14pt; color: #2A54A5; }
        .interval {font-color: black; font-weight: bold;}
    </style>
</head>
<body>
    <div style="background: antiquewhite none repeat scroll 0% 0%;">
    <form action="entities-by-last-month.jsp" method="get" id="params">
        <h2>Length related params</h2>
        <input type="text" name="len1" placeholder="-100.0,-10.0,0" />
        <br>
        <h2>Weight for exclamation, question marks and smileys</h2>
        <input type="text" name="es1" placeholder="5.0,7.0,7.0"/>

        <h2>Weight for number of taboo words found</h2>
        <input type="text" name="t1" placeholder="-20.0"/>

        <h2>Weight for number of non-specific words found</h2>
        <input type="text" name="ns1" placeholder="-10.0"/>

        <h2>Weight for number of names found in the clue and boost for when the answer is not a name</h2>
        <input type="text" name="na1" placeholder="2.0,-20.0"/>

        <h2>Boost for frequency/latest mention and thread length</h2>
        <input type="text" name="ed1" placeholder="-1.0,5.0"/>

        <h2>Lists and their corresponding weights</h2>
        <h3>List 1</h3>
        <input type="text" size="100" name="list1" placeholder="flight, travel, city, town, visit, arrive, arriving, land, landing, reach, reaching, train, road, bus, college, theatre, restaurant, book, film, movie, play, song, writer, artist, author, singer, actor, school">
        <input type="text" name="lw1" placeholder="5.0">
        <h3>List 2</h3>
        <input type="text" size="100" name="list2" placeholder="from, to, in, at, as, by, inside, like, of, towards, toward, via, such as, called, named, name">
        <input type="text" name="lw2" placeholder="10.0">
        <h3>List 3</h3>
        <input type="text" size="200" name="list3" placeholder="absorb, accept, admit, affirm, analyze, appreciate, assume, convinced of, believe, consider,  decide,  dislike, doubt, dream, dream up,  expect, fail, fall for, fancy , fathom, feature , feel, find, foresee , forget, forgive, gather, get, get the idea, get the picture, grasp, guess, hate, have a hunch, have faith in, have no doubt, hold, hypothesize, ignore, image , imagine, infer, invent, judge, keep the faith, know, lap up, leave, lose, maintain, make rough guess, misunderstand, neglect, notice, overlook, perceive, place, place confidence in, plan, plan for , ponder, predict, presume, put, put heads together, rack brains, read, realise, realize, reckon, recognize, regard, reject, rely on, remember, rest assured, sense, share, suppose , suspect , swear by, take ,  take at one's word, take for granted, think, trust, understand, vision , visualize , wonder">
        <input type="text" name="lw3" placeholder="0.0">
        <h3>List 4</h3>
        <input type="text" size="100" name="list4" placeholder="he,she,i,me,you">
        <input type="text" name="lw4" placeholder="0.0">

        <br>
        <input type="text" size="10" name="mode">
    </form>
    <button type="submit" form="params" value="Submit">Update</button><br>
    </div>
    <hr>

<b>Non-person Entity listing by most recent occurrence (for testing only)</b><br/>

  <script>
      //copied from: http://www.jquerybyexample.net/2012/06/get-url-parameters-using-jquery.html
      function getURLParameter(sParam){
        var sPageURL = window.location.search.substring(1);
        var sURLVariables = sPageURL.split('&');
        for (var i = 0; i < sURLVariables.length; i++){
            var sParameterName = sURLVariables[i].split('=');
            if (sParameterName[0] == sParam) {
                return sParameterName[1];
            }
        }
      }

      $("#params input").each(function(i){
       var name = $(this).attr("name");
       var mode = getURLParameter("mode");
       var val = getURLParameter(name);
       if(typeof(val)=="undefined" || val=="") {
           val = $(this).attr("placeholder");
           if(typeof(mode)!="undefined" && mode == "person"){
               if(name=="lw3" || name=="lw4")
                   val = "10.0";
               else if(name=="lw2")
                   val = "0";
           }
           if(name=="mode") val = mode;
           $(this).val(val);
       } else {
           val = decodeURIComponent(val);
           val = val.replace(/\+/g," ");
           if(typeof(mode)!="undefined" && mode == "person" && (name=="lw3" || name=="lw4")) {
               val = "10.0";
           }
           if(name=="mode") val = mode;
           $(this).val(val);
       }
      });
      //$("form").submit(function(){alert("Submitting form...")});
      if(window.location.href.indexOf("&")<0)
        $("form").submit();
  </script>

    <%
        if(request.getParameter("len1") == null)
            return;
        List<ClueEvaluator> evals = new ArrayList<>();
        float[] params = new float[3];
        String[] pS = request.getParameter("len1").split("[,\\s]+");
        for(int i=0;i<pS.length;i++)
            params[i] = Float.parseFloat(pS[i]);
        evals.add(new ClueEvaluator.LengthEvaluator(params));

        params = new float[3];
        pS = request.getParameter("es1").split("[,\\s]+");
        for(int i=0;i<pS.length;i++)
            params[i] = Float.parseFloat(pS[i]);
        evals.add(new ClueEvaluator.EmotionEvaluator(params));

        params = new float[1];
        pS = request.getParameter("t1").split("[,\\s]+");
        for(int i=0;i<pS.length;i++)
            params[i] = Float.parseFloat(pS[i]);
        evals.add(new ClueEvaluator.DirtEvaluator(params));

        params = new float[1];
        pS = request.getParameter("t1").split("[,\\s]+");
        for(int i=0;i<pS.length;i++)
            params[i] = Float.parseFloat(pS[i]);
        evals.add(new ClueEvaluator.DirtEvaluator(params));

        params = new float[1];
        pS = request.getParameter("ns1").split("[,\\s]+");
        for(int i=0;i<pS.length;i++)
            params[i] = Float.parseFloat(pS[i]);
        evals.add(new ClueEvaluator.SpecificityEvaluator(params));

        params = new float[2];
        pS = request.getParameter("na1").split("[,\\s]+");
        for(int i=0;i<pS.length;i++)
            params[i] = Float.parseFloat(pS[i]);
        evals.add(new ClueEvaluator.NamesEvaluator(params));

        params = new float[2];
        pS = request.getParameter("ed1").split("[,\\s]+");
        for(int i=0;i<pS.length;i++) {
            params[i] = Float.parseFloat(pS[i]);
            //System.err.println("ed params: "+params[i]);
        }
        evals.add(new ClueEvaluator.EmailDocumentEvaluator(params));

        params = new float[4];
        List<String[]> lists = new ArrayList<>();
        params[0] = Float.parseFloat(request.getParameter("lw1"));
        params[1] = Float.parseFloat(request.getParameter("lw2"));
        params[2] = Float.parseFloat(request.getParameter("lw3"));
        params[3] = Float.parseFloat(request.getParameter("lw4"));
        lists.add(request.getParameter("list1").split("[\\s,]+"));
        lists.add(request.getParameter("list2").split("[\\s,]+"));
        lists.add(request.getParameter("list3").split("[\\s,]+"));
        lists.add(request.getParameter("list4").split("[\\s,]+"));

        JSPHelper.log.info("Request params: list initialisation: "+ Arrays.asList(request.getParameterValues("list1"))+", "+Arrays.asList(request.getParameterValues("list2"))+", "+Arrays.asList(request.getParameterValues("list3"))+", "+Arrays.asList(request.getParameterValues("list4")));
        evals.add(new ClueEvaluator.ListEvaluator(params, lists));

    try {
        //the only types we are interested in
        List<Short> type = new ArrayList<>();
        String mode = request.getParameter("mode");
        Set<String> ownerNames = new LinkedHashSet<>();
        for(String str: archive.ownerNames) {
            ownerNames.add(str.toLowerCase());
        }
        //for(Short )
        Short[] itypes = new Short[]{FeatureDictionary.BUILDING,FeatureDictionary.PLACE, FeatureDictionary.RIVER, FeatureDictionary.ROAD, FeatureDictionary.UNIVERSITY, FeatureDictionary.MOUNTAIN, FeatureDictionary.AIRPORT,
                FeatureDictionary.ISLAND,FeatureDictionary.MUSEUM, FeatureDictionary.BRIDGE, FeatureDictionary.AIRLINE, FeatureDictionary.SHOPPINGMALL, FeatureDictionary.PARK, FeatureDictionary.HOTEL,FeatureDictionary.THEATRE,
                FeatureDictionary.LIBRARY, FeatureDictionary.LAWFIRM, FeatureDictionary.GOVAGENCY};
        double CUTOFF = 0.001;
        archive.assignThreadIds();
        Lexicon lex = archive.getLexicon("default");

        //String modelFile = archive.baseDir + File.separator + "models" + File.separator + SVMModel.modelFileName;
        out.println ("loading model...");
        out.flush();

        ArchiveCluer cluer = new ArchiveCluer(null, archive, null, lex);

        boolean originalOnly = true;
        List<Document> docs = archive.getAllDocs();
        Map<String, Date> entityToLastDate = new LinkedHashMap<>();
        Multimap<String, EmailDocument> entityToMessages = LinkedHashMultimap.create();
        Multimap<String, Long> entityToThreads = LinkedHashMultimap.create();
        Multimap<String, String> ceToDisplayEntity = LinkedHashMultimap.create();

        int di = 0;

        // sort by date
        Collections.sort(docs);

        Date earliestDate = null, latestDate = null;
        Set<String> allEntities = new HashSet<>();
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            if (earliestDate == null || ed.date.before(earliestDate))
                earliestDate = ed.date;
            if (latestDate == null || ed.date.after(latestDate))
                latestDate = ed.date;

            List<String> entities = new ArrayList<String>();
            if(mode==null || !mode.equals("person")) {
                Map<Short, Map<String, Double>> es = NER.getEntities(archive.getDoc(doc), true);
                for (Short t : itypes) {
                    Map<String, Double> tes = es.get(t);
                    for (String str : tes.keySet())
                        if (tes.get(str) > CUTOFF)
                            entities.add(str);
                }
            }
            else{
                List<Address> addrs = new ArrayList<Address>();
                if(ed.to!=null)
                    for(Address addr: ed.to)
                        addrs.add(addr);
                if(ed.cc!=null)
                    for(Address addr: ed.cc)
                        addrs.add(addr);
                if(ed.bcc!=null)
                    for(Address addr: ed.bcc)
                        addrs.add(addr);

                List<String> names = new ArrayList<>();
                for(Address addr: addrs) {
                    Contact c = archive.addressBook.lookupByAddress(addr);
                    names.add(c.pickBestName());
                }
                for(String name: names){
                    if(!ownerNames.contains(name))
                        entities.add(name);
                }
            }
            allEntities.addAll(entities);
//            if (Util.nullOrEmpty(request.getParameter("locations")))
//                entities.addAll(archive.getEntitiesInDoc(doc, NER.EORG, true, originalOnly));
//            if (Util.nullOrEmpty(request.getParameter("orgs")))
//                entities.addAll(archive.getEntitiesInDoc(doc, NER.ELOC, true, originalOnly));

            //personEntities.addAll(archive.getEntitiesInDoc(doc, NER.EPER, true, originalOnly));

            //entities.removeAll(personEntities);

            // get entities
            for (String e : entities) {
                if (Util.nullOrEmpty(e))
                    continue;
                e = e.replaceAll("^\\W+|\\W+$","");
               if (e.length() > 10 && e.toUpperCase().equals(e))
                   continue; // all upper case, more than 10 letters, you're out.

               String ce = canonicalize(e); // canonicalize
               if (ce == null) {
                   JSPHelper.log.info ("Dropping entity: "  + e);
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
        out.println("Considered #"+allEntities.size()+" unique entities and #"+ceToDisplayEntity.size()+" good ones in #"+docs.size()+" docs<br>");
        out.println("Owner Names: "+ownerNames);
        JSPHelper.log.info("Considered #"+allEntities.size()+" unique entities and #"+ceToDisplayEntity.size()+" good ones in #"+docs.size()+"docs");

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

        int nvalidclues = 0;
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

            short clueType = (short)((mode==null||!mode.equals("person"))?0:1);
            ci.clues = cluer.createClues(fullAnswer, clueType, evals, new LinkedHashSet<String>(), nerModel, intervalStart, intervalEnd, HTMLUtils.getIntParam(request, "sentences", 2), archive);
            if(ci.clues == null || ci.clues.length == 0){
                JSPHelper.log.warn("Did not find any clue for: "+fullAnswer);
            }
            else{
                nvalidclues++;
            }
            ci.hasCoreTokens = hasCoreTokens;
            clueInfos[interval].add(ci);
         }
         out.println("Found valid clues for "+nvalidclues+" answers<br>");
         JSPHelper.log.info("Found valid clues for "+nvalidclues+" answers");

         // now print out the clue tables for all intervals
         {
             for (int i = 0; i < clueInfos.length; i++) {
                 Collections.sort(clueInfos[i]);
                 out.println ("<h2>Interval #" + i + ": " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(intervals.get(i).getFirst()) + " - " + edu.stanford.muse.email.CalendarUtil.formatDateForDisplay(intervals.get(i).getSecond()) + "</h2>");
                 out.println ("<table><th><i>"+((mode==null||!mode.equals("person"))?"Non person name":"Person name")+"</i></th><th><i>Last message</i></th><th><i># Messages</i></th><th><i># Threads</i></td><td>Clue score</td></th>");
                 for (ClueInfo ci: clueInfos[i]) {
                     if(ci == null || ci.clues==null || ci.clues.length==0)
                         continue;
                     if (request.getParameter("hideCoreTokens") != null && ci.hasCoreTokens)
                        continue;
                     if (request.getParameter("hideNoClue") != null && (ci.clues == null || ci.clues.length==0))
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
</html>
