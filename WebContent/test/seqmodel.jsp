<%@ page import="java.io.File" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.io.FileWriter" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.util.*" %>
<%@ page import="java.util.regex.Pattern" %>
<%@ page import="java.util.regex.Matcher" %>
<%@ page import="opennlp.tools.util.featuregen.FeatureGeneratorUtil" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="org.json.JSONArray" %>
<%--<%@ page import="edu.stanford.nlp.ling.CoreLabel" %>--%>
<%--<%@ page import="edu.stanford.nlp.ie.crf.CRFClassifier" %>--%>
<%--<%@ page import="edu.stanford.nlp.ie.AbstractSequenceClassifier" %>--%>
<%--<%@ page import="edu.stanford.nlp.ling.CoreAnnotations" %>--%>
<%
    class Info {
        public int freq;
        public Date firstDate, lastDate;
        public void update(Date d){
            freq ++;
            if(firstDate==null)
                firstDate = d;
            else if(firstDate.after(d))
                firstDate = d;
            if(lastDate == null)
                lastDate = d;
            else if(lastDate.before(d))
                lastDate = d;
        }
    }
    class Some {
        public String removeSW(String phrase) {
            List<String> sws = Arrays.asList("and", "for", "to", "in", "at", "on", "the", "of", "a", "an", "is");
            String[] words = phrase.split("[\\s,]+");
            if (sws.contains(words[words.length - 1]))
                phrase = phrase.replaceAll("[\\s,]+" + words[words.length - 1] + "$", "");
            if (words.length > 1 && sws.contains(words[0]))
                phrase = phrase.replaceAll("^" + words[0] + "[\\s,]+", "");
            return phrase;
        }

        public List<Triple<String, Integer, Integer>> tokenize(String sent, Map<String, Integer> dict, SequenceModel nerModel) {
            String[] tokens = sent.split("[\\s,\\-:;\"\\(\\))!\\?]+");
            String[] labels = new String[tokens.length];
            List<String> sws = Arrays.asList("and", "for", "to", "in", "at", "on", "the", "of", "a", "an", "is");
            for (int ti = 0; ti < tokens.length; ti++) {
                String tok = tokens[ti];
                tok = tok.replaceAll("^\\W+|\\W+$", "");
                tok = tok.replaceAll("'s", "");
                Pair<String, Double> p = nerModel.dictionary.getLabel(tok, nerModel.dictionary.features);
                FeatureDictionary.MU mu = nerModel.dictionary.features.get(tok);
                double beliefScore = p.getSecond();
                if (mu != null)
                    beliefScore *= Math.log(mu.numMixture);

                String label = p.getFirst();
                if (mu != null && (mu.getLikelihoodWithType("" + FeatureDictionary.OTHER) > p.getSecond()))
                    label = "" + FeatureDictionary.OTHER;
                if (sws.contains(tok.toLowerCase()))
                    label = "SW";
                //present in dictionary but absent from the gazette
                String wc = FeatureGeneratorUtil.tokenFeature(tok);
                if (dict.containsKey(tok.toLowerCase()) && label.equals("-2"))
                    labels[ti] = "Y";
                    //present in both dictionary and gazette, but marked as "OTH" type in latter
                    //extra conditions for tokens like "New"
                else if (dict.containsKey(tok.toLowerCase()) && (label.equals("-1")) && (!wc.equals("ic")))
                    labels[ti] = "Y1";
                else if (wc.equals("lc") && tok.endsWith("ing"))
                    labels[ti] = "Y2";
                else if (label.equals("SW"))
                    labels[ti] = label;
//                else if (wc.equals("lc") && beliefScore<0.01)
//                    labels[ti] = "Y2";
                else if (wc.equals("lc") && tok.endsWith("ed"))
                    labels[ti] = "V";
                else if (tok.equals("I"))
                    labels[ti] = "I";
                else
                    labels[ti] = "N";
                System.err.println(tok + ", wc: " + wc + ", label: " + label + ", al: " + labels[ti] + ": " + beliefScore);
            }
            //now cluster all the 'N' labels together
            Pattern p = Pattern.compile("[^\\s,\\-:;\"\\(\\))!\\?]+");
            Matcher m = p.matcher(sent);
            int pe = -1;
            int li = 0;
            List<Triple<String, Integer, Integer>> ftoks = new ArrayList<>();
            String str = "";
            int start = 0;
            List<String> stopChars = Arrays.asList(",", ":", ";", "\"", "(", ")", "!", "-", "?");
            while (m.find()) {
                String sc = "";
                if (pe >= 0)
                    sc = sent.substring(pe, m.start());
                String lab = labels[li];
                //System.err.println("2: " + m.group() + ", " + lab);
                boolean stopChar = false;
                for (String c : stopChars)
                    if (sc.contains(c)) {
                        stopChar = true;
                        break;
                    }
                boolean cut = true;
                if ((lab.equals("N") || lab.equals("SW")) && !stopChar)
                    cut = false;
                //dont allow consecutive stop words
                if (lab.equals("SW") && li > 0 && labels[li - 1].equals("SW"))
                    cut = true;
                //dont let it start with or end with SW
                if (str.equals("") && lab.equals("SW"))
                    cut = true;
//                if(m.group().endsWith("'s"))
//                    cut = true;
                if (!cut)
                    str = str + sc + m.group();
                else {
                    if (!str.equals("")) {
                        if (li > 0 && labels[li - 1].equals("SW"))
                            str = removeSW(str);
                        ftoks.add(new Triple<>(str, start, pe));
                    }
                    if (lab.equals("N") && !m.group().endsWith("'s"))
                        str = m.group();
                    else str = "";
                    start = m.end();
                }
                li++;
                pe = m.end();
            }
            if (!str.equals(""))
                ftoks.add(new Triple<>(removeSW(str), start, pe));
            List<Triple<String, Integer, Integer>> ctoks = new ArrayList<>();
            for (Triple<String, Integer, Integer> tok : ftoks) {
                String tc = tok.first.toLowerCase();
                if (tc.contains("i am") || tc.contains("i'm") || tc.startsWith("i ") || tc.contains(" i ") || tc.endsWith(" i"))
                    continue;
                ctoks.add(tok);
            }
            return ctoks;
        }

        public List<Triple<String, Integer, Integer>> tokenizePOS(String sent, Short type) {
            List<Pair<String, String>> posTags = NLPUtils.posTag(sent);
            List<Triple<String, Integer, Integer>> ret = new ArrayList<>();
            List<String> allowedPOSTags = new ArrayList<>();
            if (type == FeatureDictionary.PERSON)
                allowedPOSTags = Arrays.asList("NNP", "NNS", "NN");
            else
                allowedPOSTags = Arrays.asList("NNP", "NNS", "NN", "JJ", "IN", "POS");

            String str = "";
            for (int pi = 0; pi < posTags.size(); pi++) {
                Pair<String, String> p = posTags.get(pi);
                String tag = p.second;
                String nxtTag = null;
                if (pi < posTags.size() - 1)
                    nxtTag = posTags.get(pi + 1).second;

                //POS for 's
                //should not end or start in improper tags
                if (allowedPOSTags.contains(tag)) {
                    if (str.equals("") && (tag.equals("POS") || tag.equals("IN") || p.getFirst().equals("'") || p.getFirst().equals("Dear") || p.getFirst().equals("from")))
                        continue;
                    if ((nxtTag == null || !allowedPOSTags.contains(nxtTag)) && (tag.equals("POS") || tag.equals("IN") || p.getFirst().equals("'")))
                        continue;
                    str += p.getFirst() + " ";
                } else {
                    ret.add(new Triple<>(str, -1, -1));
                    str = "";
                }
            }
            if (!str.equals(""))
                ret.add(new Triple<>(str, -1, -1));
            return ret;
        }

        public boolean bad(String phrase){
            int sp = 0;
            for(Character c: phrase.toCharArray())
                if(!((c>='A' && c<='Z') || (c>='a' && c<='z') || c==' ' || c=='.' || c=='\''))
                    sp++;
            if(sp>3) {
                //System.err.println("Rejecting: "+phrase+", "+sp);
                return true;
            }
            return false;
        }

//        Map<String, Set<String>> stanfordRec(String content) {
//            Map<String, Set<String>> ret = new LinkedHashMap<>();
//            try {
//                String[] types = new String[]{"PERSON", "ORGANIZATION", "LOCATION"};
//                String serializedClassifier = "/Users/vihari/epadd-ner/english.all.3class.distsim.crf.ser.gz";
//                AbstractSequenceClassifier<CoreLabel> classifier = (AbstractSequenceClassifier) request.getSession().getAttribute("stanclassifier");
//                if (classifier == null) {
//                    classifier = CRFClassifier.getClassifier(serializedClassifier);
//                    request.getSession().setAttribute("stanclassifier", classifier);
//                }
//                for (String t : types)
//                    ret.put(t, new LinkedHashSet<String>());
//                for (String stype : types) {
//                    Set<String> orgs = new LinkedHashSet<>();
//                    List<List<CoreLabel>> labels = classifier.classify(content);
//                    for (List<CoreLabel> sentence : labels) {
//                        String str = "";
//                        for (CoreLabel word : sentence) {
//                            String ann = word.get(CoreAnnotations.AnswerAnnotation.class);
//                            String w = word.word();
//                            if (!ann.equals(stype)) {
//                                if (!str.equals("")) {
//                                    str = str.substring(0, str.length());
//                                    orgs.add(str);
//                                }
//                                str = "";
//                            } else {
//                                if (!str.equals(""))
//                                    str += " " + w;
//                                else
//                                    str = w;
//                            }
//                        }
//                    }
//                    ret.get(stype).addAll(orgs);
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            return ret;
//        }
    }
    %>
    <title>Fine-type entities</title>
    <script src="../js/jquery.js"></script>
    <link href="../css/jquery.dataTables.css" rel="stylesheet" type="text/css"/>
    <script src="../js/jquery.dataTables.min.js"></script>
    <link rel="stylesheet" href="../bootstrap/dist/css/bootstrap.min.css">

    <jsp:include page="../css/css.jsp"/>
    <script src="../js/epadd.js"></script>
    <style type="text/css">
        .js #entities {display: none;}
    </style>
    <style>
        body{
            padding-left: 30px;
        }
        .entities{
            color: grey; font-family: verdana; font-size: 1.2em;
        }
    </style>
    <script>
        change = function(){
            t = document.getElementsByName("type")[0].value;
            for(var i=0;i<40;i++){
                elt = document.getElementById("entities-"+i);
                if(typeof(elt)!=="undefined" && elt!=null)
                    elt.style.display = "none";
            }
            elt = document.getElementById("entities-"+t);
            if(typeof(elt)!=="undefined" && elt!=null)
                elt.style.display = "block";
        }
    </script>
    <%
    Archive archive = JSPHelper.getArchive(request.getSession());
    String mwl = System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator;
    File f = new File(mwl);
    if(!f.exists())
        f.mkdir();
    f = new File(mwl + "cache");
    if(!f.exists())
        f.mkdir();

    String modelFile = mwl + SequenceModel.modelFileName;
    SequenceModel nerModel = (SequenceModel)session.getAttribute("ner");
    if(nerModel == null) {
        System.err.println("Loading model...");
        try {
            nerModel = SequenceModel.loadModel(new File(modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (nerModel == null)
            nerModel = SequenceModel.train();
        session.setAttribute("ner", nerModel);
    }

    try {
            nerModel.fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator + "cache" + File.separator + "features.dump"));
    } catch (Exception e) {
            e.printStackTrace();
    }

    List<Document> docs = archive.getAllDocs();
    int si=0;
    int MAX_SENT = 50000;
    Map<Short,Map<String,Double>> found = new LinkedHashMap<>();
    Map<Short,String> desc = new LinkedHashMap<>();
    desc.put(FeatureDictionary.PERSON,"PERSON");desc.put(FeatureDictionary.COMPANY,"COMPANY");desc.put(FeatureDictionary.BUILDING,"BUILDING");
        desc.put(FeatureDictionary.DISEASE,"DISEASE");desc.put(FeatureDictionary.PLACE,"PLACE");desc.put(FeatureDictionary.RIVER,"RIVER");
        desc.put(FeatureDictionary.ROAD,"ROAD");desc.put(FeatureDictionary.UNIVERSITY,"UNIVERSITY");desc.put(FeatureDictionary.MILITARYUNIT,"MILITARYUNIT");
        desc.put(FeatureDictionary.MOUNTAIN,"MOUNTAIN");desc.put(FeatureDictionary.AIRPORT,"AIRPORT");desc.put(FeatureDictionary.ORGANISATION,"ORGANISATION");
        desc.put(FeatureDictionary.NEWSPAPER,"NEWSPAPER");desc.put(FeatureDictionary.ACADEMICJOURNAL,"ACADEMICJOURNAL");
        desc.put(FeatureDictionary.MAGAZINE,"MAGAZINE");desc.put(FeatureDictionary.POLITICALPARTY,"PARTY");
        desc.put(FeatureDictionary.ISLAND,"ISLAND");desc.put(FeatureDictionary.MUSEUM,"MUSEUM");desc.put(FeatureDictionary.BRIDGE,"BRIDGE");
        desc.put(FeatureDictionary.AIRLINE,"AIRLINE");desc.put(FeatureDictionary.NPORG,"NPORG");desc.put(FeatureDictionary.GOVAGENCY,"GOVAGENCY");
        desc.put(FeatureDictionary.SHOPPINGMALL,"SHOPPINGMALL");desc.put(FeatureDictionary.HOSPITAL,"HOSPITAL");desc.put(FeatureDictionary.POWERSTATION,"POWERSTATION");
        desc.put(FeatureDictionary.AWARD,"AWARD");desc.put(FeatureDictionary.TRADEUNIN,"UNION");desc.put(FeatureDictionary.PARK,"PARK");
        desc.put(FeatureDictionary.HOTEL,"HOTEL");desc.put(FeatureDictionary.THEATRE,"THEATRE");desc.put(FeatureDictionary.LEGISTLATURE,"LEGISTLATURE");
        desc.put(FeatureDictionary.LIBRARY,"LIBRARY");desc.put(FeatureDictionary.LAWFIRM,"LAWFIRM");desc.put(FeatureDictionary.MONUMENT,"MONUMENT");
        desc.put(FeatureDictionary.EVENT, "EVENT");
        long start_time = System.currentTimeMillis();
    Map<String,Info> infos = new LinkedHashMap<>();
    for(Document doc: docs) {
        String c = archive.getContents(doc, true);
        EmailDocument ed = (EmailDocument)doc;
        String[] sents = NLPUtils.tokeniseSentence(c);
        for(String sent: sents) {
            sent = sent.replaceAll("[><\\)\\(\"\\[\\]\\*\\+]+","");
            List<Triple<String,Integer,Integer>> toks = new Some().tokenizePOS(sent,FeatureDictionary.PLACE);//new CICTokenizer().tokenize(sent, false);
           // out.println(sent+"<br>");
//            Map<String,Set<String>> es = new Some().stanfordRec(sent);
//            String line = sent;
//            for(String str: es.keySet()){
//                Set<String> entities = es.get(str);
//                for(String e: entities) {
//                    line = line.replaceAll(e, "<span style='color:green'>" + e + "[" + str + "]</span> ");
//                }
//            }
//            out.println(line + "<br>");

            for (Triple<String,Integer,Integer> t : toks) {
                //Pair<String, Double> p = new Some().scoreSubstrs(t.getFirst(), FeatureDictionary.PLACE, nerModel, 1E-4);
                String phrase = t.getFirst().replaceAll("^\\W+|\\W+$","");
                phrase = new Some().removeSW(phrase);
                if(new Some().bad(phrase))
                    continue;
                Map<String,Pair<Short,Double>> entities = nerModel.seqLabel(phrase);
                for(String e: entities.keySet()) {
                    Pair<Short,Double> p = entities.get(e);
                    if(desc.get(p.first)!=null) {
                        if (!found.containsKey(p.first))
                            found.put(p.first, new LinkedHashMap<String, Double>());
                        found.get(p.first).put(e, p.getSecond());
                        if(infos.get(e) == null)
                            infos.put(e, new Info());
                        infos.get(e).update(ed.getDate());
                    }
//                    try {
//                        str = str.replaceAll(e, "<span style='color:" + color + "'>" + e + "[" + desc.get(p.getFirst()) + "," + p.getSecond() + "]</span> ");
//                    }catch(Exception e1){
//                        e1.printStackTrace();
//                    }
                }
//                out.println(str+"----");
            }
//            out.println("<br>");
//            out.println("----------------<br><br>");
        }
        if(si%10 == 0)
            System.err.println("sent: "+si);
        if(si++>MAX_SENT)
            break;
    }
    System.err.println("Done in: "+(System.currentTimeMillis()-start_time));
    //ordered according to what I think is doing best
    Short[] otypes = new Short[]{FeatureDictionary.AWARD, FeatureDictionary.BUILDING, FeatureDictionary.ROAD,FeatureDictionary.DISEASE, FeatureDictionary.EVENT,
            FeatureDictionary.UNIVERSITY, FeatureDictionary.LIBRARY, FeatureDictionary.MUSEUM, FeatureDictionary.ORGANISATION, FeatureDictionary.NEWSPAPER, FeatureDictionary.PERSON, FeatureDictionary.COMPANY,
            FeatureDictionary.ACADEMICJOURNAL, FeatureDictionary.AIRPORT, FeatureDictionary.LAWFIRM, FeatureDictionary.LEGISTLATURE, FeatureDictionary.MAGAZINE,
            FeatureDictionary.POLITICALPARTY,
            FeatureDictionary.HOSPITAL,FeatureDictionary.HOTEL, FeatureDictionary.THEATRE, FeatureDictionary.PLACE, FeatureDictionary.ISLAND,
            FeatureDictionary.BRIDGE,FeatureDictionary.GOVAGENCY,FeatureDictionary.NPORG, FeatureDictionary.MONUMENT,
            FeatureDictionary.SHOPPINGMALL, FeatureDictionary.THEATRE, FeatureDictionary.TRADEUNIN, FeatureDictionary.MILITARYUNIT, FeatureDictionary.RIVER, FeatureDictionary.PARK};
    String shtml = "Select type: <select name='type' onchange='change()'>";
    for(Short type: otypes){
        shtml += "<option value='"+type+"'>"+desc.get(type)+"</option>";
    }
    shtml += "</select>";
    out.println(shtml);
    Map<Short, JSONArray> tables = new LinkedHashMap<Short, JSONArray>();
    for(Short type: found.keySet()) {
        Map<String, Double> ft = found.get(type);
        List<Pair<String, Double>> sF = Util.sortMapByValue(ft);
        //out.println("<br>Type: " + desc.get(type) + "<br>");
        if (desc.get(type) == null)
            continue;
        out.println("<div class='entities' id=entities-"+type+" style='display:"+((type==FeatureDictionary.AWARD)?"block":"none")+"'>");
        int cnt = 0;
        for (Pair<String, Double> p : sF) {
            if(p.second<1.0E-3)
                break;
            cnt++;
            if(p.getSecond()!=1.0 || (p.getSecond()==1.0 && p.getFirst().contains(" "))) {
                JSONArray arr = new JSONArray();
                Info info = infos.get(p.getFirst());
                String color = p.getSecond()==1.0?"green":"";
                arr.put(0,"<span style='color:" + color + "'>" + p.getFirst() + "</span>");
                arr.put(1, info.freq);
                arr.put(2, info.firstDate);
                arr.put(3,info.lastDate);
                arr.put(4, p.getSecond());
                if(tables.get(type)==null)
                    tables.put(type, new JSONArray());
                tables.get(type).put(tables.get(type).length(),arr);
            }
        }
        out.println("Found: " + cnt + " entities of type: "+desc.get(type)+"<br>");
        out.println("<table id='table"+type+"'>");
        out.println("<thead><th>Entity</th><th>Mentions</th><th>First</th><th>Last</th><th>Score</th></thead><tbody></tbody></table>");

        out.println("</div>");
    }
    %>
        <script type="text/javascript">
            do_search = function(term) {
                window.open ('localhost:9099/browse?term=\"' + term + '\"');
            };
        $(document).ready(function() {
            var click_to_search = function ( data, type, full, meta ) {
                // epadd.do_search will open search result in a new tab.
                // Note, we have to insert onclick into the rendered HTML,
                // we were earlier trying $('.search').click(epadd.do_search) - this does not work because only the few rows initially rendered to html match the $('.search') selector, not the others
                term = data.replace(/<span .*?>/,"").replace(/<\/span>/,"");
                //console.log("You clicked: "+term);
                return '<span style="cursor:pointer" onclick="do_search(term)">' + data + '</span>';
            };

            <%for(short type: tables.keySet()){%>
                var entities = <%=tables.get(type).toString(4)%>;
                //console.log("Initialising #table<%=type%> with "+entities);
                $('#table<%=type%>').dataTable({
                    data: entities,
                    //pagingType: 'simple',
                    paging: false,
                    columnDefs: [{ className: "dt-right", "targets": [ 2,3,4 ] },{width: "600px", targets: 0},{targets: 0, render:click_to_search}
                        ,{render:function(data,type,row){return Math.round(row[4]*1000)/1000}, targets:[4]}
                    ],
                    order:[[4, 'desc'],[1,'desc']], // col 1 (entity message count), descending
                    fnInitComplete: function() { $('#spinner-div').hide(); $('#entities').fadeIn(); }
                });
            <%}%>
        });
    </script>