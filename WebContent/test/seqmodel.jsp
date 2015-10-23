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
<%@ page import="edu.stanford.muse.ner.NEREvaluator" %>
<%@ page import="opennlp.tools.util.featuregen.FeatureGeneratorUtil" %>
<%@ page import="edu.stanford.muse.index.IndexUtils" %>
<%@ page import="edu.stanford.muse.ner.dictionary.EnglishDictionary" %>
<%@ page import="edu.stanford.muse.ner.tokenizer.CICTokenizer" %>
<%@ page import="edu.stanford.nlp.ling.CoreLabel" %>
<%@ page import="edu.stanford.nlp.ie.crf.CRFClassifier" %>
<%@ page import="edu.stanford.nlp.ie.AbstractSequenceClassifier" %>
<%@ page import="edu.stanford.nlp.ling.CoreAnnotations" %><%
    class Some {
        public Map<String, Double> find(String content, Short type, SequenceModel model) {
            Map<String, Double> map = new LinkedHashMap<>();
            //List<String> pns = NLPUtils.getAllProperNouns(content);
            Pair<Map<Short, List<String>>, List<Triple<String, Integer, Integer>>> temp = model.find(content);
            //for(String pn: pns){
            for (String pn : temp.getFirst().get(type)) {
                double s = model.score(pn, type);
                if (s > 0) {
                    map.put(pn, s);
                }
            }
            return map;
        }

        public String removeSW(String phrase) {
            List<String> sws = Arrays.asList("and", "for", "to", "in", "at", "on", "the", "of", "a", "an", "is");
            String[] words = phrase.split("[\\s,]+");
            if (sws.contains(words[words.length - 1]))
                phrase = phrase.replaceAll("[\\s,]+" + words[words.length - 1] + "$", "");
            if(words.length>1 && sws.contains(words[0]))
                phrase = phrase.replaceAll("^"+words[0]+"[\\s,]+","");
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
            for (int pi=0;pi<posTags.size();pi++) {
                Pair<String, String> p = posTags.get(pi);
                String tag = p.second;
                String nxtTag = null;
                if(pi<posTags.size()-1)
                    nxtTag = posTags.get(pi+1).second;

                //POS for 's
                //should not end or start in improper tags
                if (allowedPOSTags.contains(tag)) {
                    if(str.equals("") && (tag.equals("POS")||tag.equals("IN")||p.getFirst().equals("'")||p.getFirst().equals("Dear")||p.getFirst().equals("from")))
                        continue;
                    if((nxtTag==null||!allowedPOSTags.contains(nxtTag)) && (tag.equals("POS")||tag.equals("IN")||p.getFirst().equals("'")))
                        continue;
                    str += p.getFirst() + " ";
                }
                else {
                    ret.add(new Triple<>(str, -1, -1));
                    str = "";
                }
            }
            if (!str.equals(""))
                ret.add(new Triple<>(str, -1, -1));
            return ret;
        }

        Map<String,Set<String>> stanfordRec(String content){
            Map<String,Set<String>> ret = new LinkedHashMap<>();
            try {
                String[] types = new String[]{"PERSON","ORGANIZATION","LOCATION"};
                String serializedClassifier = "/Users/vihari/epadd-ner/english.all.3class.distsim.crf.ser.gz";
                AbstractSequenceClassifier<CoreLabel> classifier = (AbstractSequenceClassifier)request.getSession().getAttribute("stanclassifier");
                if(classifier==null) {
                    classifier = CRFClassifier.getClassifier(serializedClassifier);
                    request.getSession().setAttribute("stanclassifier",classifier);
                }
                for(String t: types)
                    ret.put(t, new LinkedHashSet<String>());
                for(String stype: types) {
                    Set<String> orgs = new LinkedHashSet<>();
                    List<List<CoreLabel>> labels = classifier.classify(content);
                    for (List<CoreLabel> sentence : labels) {
                        String str = "";
                        for (CoreLabel word : sentence) {
                            String ann = word.get(CoreAnnotations.AnswerAnnotation.class);
                            String w = word.word();
                            if (!ann.equals(stype)) {
                                if (!str.equals("")) {
                                    str = str.substring(0,str.length());
                                    orgs.add(str);
                                }
                                str = "";
                            } else {
                                if (!str.equals(""))
                                    str += " " + w;
                                else
                                    str = w;
                            }
                        }
                    }
                    ret.get(stype).addAll(orgs);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
            return ret;
        }

        public String[][] genCands(String phrase) {
            //List<String> labelCands = new ArrayList<>();
            Short[] types = new Short[]{FeatureDictionary.AWARD, FeatureDictionary.PERSON, FeatureDictionary.UNIVERSITY};//FeatureDictionary.PLACE, FeatureDictionary.AWARD
            //};
            //Short[] types = new Short[]{FeatureDictionary.PLACE, FeatureDictionary.UNIVERSITY};
            List<String> allLabels = new ArrayList<>();
            for (Short t : types) {
//                if(FeatureDictionary.OTHER==t)
//                    continue;
                allLabels.add("B-" + t);
                allLabels.add("I-" + t);
            }
            allLabels.add("O");
            String[] toks = phrase.split("\\s+");
            List<String> labels = new ArrayList<>();
            labels.add("");
            for (String tok : toks) {
                List<String> temp = new ArrayList<>();
                for (String l : labels)
                    for (String lab : allLabels) {
                        if (!l.equals(""))
                            temp.add(l + " " + lab);
                        else
                            temp.add(lab);
                    }
                labels = temp;
            }
            List<String[]> alabs = new ArrayList<>();
            for (String l : labels) {
                //l = l.substring(0, l.length()-1);
                String[] tmp = l.split("\\s+");
                boolean begin = false, dirty = false;
                Short type = -1;
                for (String t : tmp) {
                    if (t.startsWith("B-")) {
                        begin = true;
                        type = Short.parseShort(t.substring(2));
                    }
                    if ((!begin && t.startsWith("I-")) || (t.startsWith("I-") && (type != Short.parseShort(t.substring(2))))) {
                        dirty = true;
                        break;
                    }
                    if (t.equals("O"))
                        begin = false;
                }
                if (!dirty) {
                    alabs.add(tmp);
                    System.err.println("Adding: " + type + ", " + l);
                }
            }

            return alabs.toArray(new String[alabs.size()][]);
        }

//        public double score(String[] tokens, String[] labels, FeatureDictionary dictionary) {
//            Map<String, Short> segments = new LinkedHashMap<>();
//            for (int ti = 0; ti < tokens.length; ti++) {
//                String token = tokens[ti];
//                if (labels[ti].equals("O"))
//                    segments.put(token, FeatureDictionary.OTHER);
//                else {
//                    int tj = ti;
//                    String seg = "";
//                    Short type = Short.parseShort(labels[ti].substring(2));
//                    do {
//                        seg += tokens[tj] + " ";
//                        tj++;
//                    } while (tj < labels.length && labels[tj].startsWith("I-"));
//                    ti = tj - 1;
//                    System.err.println("Found segment " + seg + "," + type);
//                    segments.put(seg, type);
//                }
//            }
//
//            double s = 1;
//            for (String str : segments.keySet()) {
//                Short type = segments.get(str);
//                double v;
//                if (type != FeatureDictionary.OTHER)
//                    v = new Some().getConditional(str, type, dictionary);
//                else
//                    v = new Some().getLikelihoodWithOther(str);
//                s *= v;
//                System.err.println("Score: " + str + " - " + type + " : " + v);
//            }
//            return s;
//        }

        public Pair<String, Double> scoreSubstrs(String phrase, Short type, SequenceModel nerModel, double threshold) {
            Pair<String, Double> ret = new Pair<>(phrase, 0.0);
            String[] words = phrase.split("\\s+");
            //brute force algorithm, is O(2^n)
//            if (words.length > 10) {
//                return new Pair<>(phrase, 0.0);
//            }

            Set<String> substrs = IndexUtils.computeAllSubstrings(phrase);
            Map<String, Integer> substrL = new LinkedHashMap<>();
            for (String substr : substrs)
                substrL.put(substr, substr.length());
            List<Pair<String, Integer>> ssl = Util.sortMapByValue(substrL);
            Map<String, Double> ssubstrs = new LinkedHashMap<>();
            for (Pair<String, Integer> p : ssl) {
                String substr = p.first;
                double s = nerModel.score(substr, type);
                if (s > threshold)
                    return new Pair<>(substr, s);
//            }
//            List<Pair<String, Double>> sssubstrs = Util.sortMapByValue(ssubstrs);
//            if (sssubstrs.size() > 0)
//                ret = new Pair<>(sssubstrs.get(0).first, sssubstrs.get(0).getSecond());
//            return ret;
            }
            return new Pair<>("", -1.0);
        }
    }
    Archive archive = JSPHelper.getArchive(request.getSession());
    String mwl = System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator;
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
            nerModel.fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "features.dump"));
        } catch (Exception e) {
            e.printStackTrace();
        }

    List<Document> docs = archive.getAllDocs();
    Map<String,Double> all = new LinkedHashMap<>();
    int i=0, si=0;
    int MAX_SENT = 110;
    //Map<String,Integer> dict = NEREvaluator.buildDictionary(3);
    //Pair<Set<String>,Set<String>> p = new Some().readTokensDBpedia();
    Map<Short,Map<String,Double>> found = new LinkedHashMap<>();
    Map<Short,String> desc = new LinkedHashMap<>();
    desc.put(FeatureDictionary.PERSON,"PERSON");desc.put(FeatureDictionary.COMPANY,"COMPANY");desc.put(FeatureDictionary.BUILDING,"BUILDING");desc.put(FeatureDictionary.PLACE,"PLACE");desc.put(FeatureDictionary.RIVER,"RIVER");desc.put(FeatureDictionary.ROAD,"ROAD");desc.put(FeatureDictionary.UNIVERSITY,"UNIVERSITY");desc.put(FeatureDictionary.MILITARYUNIT,"MILITARYUNIT");desc.put(FeatureDictionary.MOUNTAIN,"MOUNTAIN");desc.put(FeatureDictionary.AIRPORT,"AIRPORT");desc.put(FeatureDictionary.ORGANISATION,"ORGANISATION");desc.put(FeatureDictionary.DRUG,"DRUG");desc.put(FeatureDictionary.NEWSPAPER,"NEWSPAPER");desc.put(FeatureDictionary.ACADEMICJOURNAL,"ACADEMICJOURNAL");desc.put(FeatureDictionary.MAGAZINE,"MAGAZINE");desc.put(FeatureDictionary.POLITICALPARTY,"POLITICALPARTY");desc.put(FeatureDictionary.ISLAND,"ISLAND");desc.put(FeatureDictionary.MUSEUM,"MUSEUM");desc.put(FeatureDictionary.BRIDGE,"BRIDGE");desc.put(FeatureDictionary.AIRLINE,"AIRLINE");desc.put(FeatureDictionary.NPORG,"NPORG");desc.put(FeatureDictionary.GOVAGENCY,"GOVAGENCY");desc.put(FeatureDictionary.RECORDLABEL,"RECORDLABEL");desc.put(FeatureDictionary.SHOPPINGMALL,"SHOPPINGMALL");desc.put(FeatureDictionary.HOSPITAL,"HOSPITAL");desc.put(FeatureDictionary.POWERSTATION,"POWERSTATION");desc.put(FeatureDictionary.AWARD,"AWARD");desc.put(FeatureDictionary.TRADEUNIN,"TRADEUNIN");desc.put(FeatureDictionary.PARK,"PARK");desc.put(FeatureDictionary.HOTEL,"HOTEL");desc.put(FeatureDictionary.THEATRE,"THEATRE");desc.put(FeatureDictionary.LEGISTLATURE,"LEGISTLATURE");desc.put(FeatureDictionary.LIBRARY,"LIBRARY");desc.put(FeatureDictionary.LAWFIRM,"LAWFIRM");desc.put(FeatureDictionary.MONUMENT,"MONUMENT");
    long start_time = System.currentTimeMillis();
    for(Document doc: docs) {
        String c = archive.getContents(doc, true);
        String[] sents = NLPUtils.tokeniseSentence(c);
        for(String sent: sents) {
//            if(sent.length()>1000)
//                continue;
            List<Triple<String,Integer,Integer>> toks = new Some().tokenizePOS(sent,FeatureDictionary.PLACE);//new CICTokenizer().tokenize(sent, false);
            out.println(sent+"<br>");
            //List<Pair<String,String>> posTags = NLPUtils.posTag(sent);
//            for(Pair<String,String> p: posTags)
//                out.println(p.getFirst()+"["+p.getSecond()+"]");
//            out.println("<br>");
            Map<String,Set<String>> es = new Some().stanfordRec(sent);
            String line = sent;
            for(String str: es.keySet()){
                Set<String> entities = es.get(str);
                for(String e: entities) {
                    e = e.replaceAll("[><\\)\\(\"]+","");
                    line = line.replaceAll(e, "<span style='color:green'>" + e + "[" + str + "]</span> ");
                }
            }
            out.println(line + "<br>");

            for (Triple<String,Integer,Integer> t : toks) {
                //Pair<String, Double> p = new Some().scoreSubstrs(t.getFirst(), FeatureDictionary.PLACE, nerModel, 1E-4);
                String phrase = t.getFirst().replaceAll("^\\W+|\\W+$","");
                phrase = new Some().removeSW(phrase);
                Map<String,Pair<Short,Double>> entities = nerModel.seqLabel(phrase);
                String str = t.getFirst();
                for(String e: entities.keySet()) {
                    Pair<Short,Double> p = entities.get(e);
                    String color;
                    if(desc.get(p.first)!=null) {
                        if (!found.containsKey(p.first))
                            found.put(p.first, new LinkedHashMap<String, Double>());
                        found.get(p.first).put(e, p.getSecond());
                    }
                    if(desc.get(p.getFirst())!=null && p.getSecond()>1.0E-10)
                        color = "red";
                    else
                        continue;

                    try {
                        e = e.replaceAll("[><\\)\\(\"]+","");

                        str = str.replaceAll(e, "<span style='color:" + color + "'>" + e + "[" + desc.get(p.getFirst()) + "," + p.getSecond() + "]</span> ");
                    }catch(Exception e1){
                        e1.printStackTrace();
                    }
                }
                out.println(str+"----");
            }
            out.println("<br>");
            out.println("----------------<br><br>");
        }
        if(si%10 == 0)
            System.err.println("sent: "+si);
        if(si++>MAX_SENT)
            break;
    }
    System.err.println("Done in: "+(System.currentTimeMillis()-start_time));
    for(Short type: found.keySet()) {
        Map<String,Double> ft = found.get(type);
        List<Pair<String, Double>> sF = Util.sortMapByValue(ft);
        out.println("<br>Type: "+desc.get(type)+"<br>");
        if(desc.get(type)==null)
            continue;
        out.println("Found: " + sF.size() + "<br>");
        out.println(" ----------------- <br>");
        for (Pair<String, Double> p : sF)
            out.println(p.getFirst() + " : " + p.getSecond() + "<br>");
    }
    //out.println(new Some().removeSW("of AFSCME District Council")+"<br>");
%>