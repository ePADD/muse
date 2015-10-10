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
<%@ page import="edu.stanford.muse.index.IndexUtils" %><%
    class Some{
        public Map<String,Double> find (String content, Short type, SequenceModel model){
            Map<String,Double> map = new LinkedHashMap<>();
            //List<String> pns = NLPUtils.getAllProperNouns(content);
            Pair<Map<Short,List<String>>, List<Triple<String,Integer,Integer>>> temp = model.find(content);
            //for(String pn: pns){
            for(String pn: temp.getFirst().get(type)){
                double s = model.score(pn, type);
                if (s>0) {
                    map.put(pn, s);
                }
            }
            return map;
        }

        public String removeTrailingSW(String phrase){
            List<String> sws = Arrays.asList("and","for","to","in","at","on","the","of", "a", "an", "is");
            String[] words = phrase.split("[\\s,]+");
            if(sws.contains(words[words.length-1]))
                phrase = phrase.replaceAll("[\\s,]+"+words[words.length-1]+"$","");
            return phrase;
        }

        public List<Triple<String,Integer,Integer>> tokenize(String sent, Map<String,Integer> dict, SequenceModel nerModel) {
            String[] tokens = sent.split("[\\s,\\-:;\"\\(\\))!\\?]+");
            String[] labels = new String[tokens.length];
            List<String> sws = Arrays.asList("and", "for", "to", "in", "at", "on", "the", "of", "a", "an", "is");
            for (int ti = 0; ti < tokens.length; ti++) {
                String tok = tokens[ti];
                tok = tok.replaceAll("^\\W+|\\W+$","");
                tok = tok.replaceAll("'s","");
                Pair<String,Double> p = nerModel.dictionary.getLabel(tok, nerModel.dictionary.features);
                FeatureDictionary.MU mu = nerModel.dictionary.features.get(tok);
                double beliefScore = p.getSecond();
                if(mu!=null)
                    beliefScore*=Math.log(mu.numMixture);

                String label = p.getFirst();
                if(mu!=null && (mu.getLikelihoodWithType(""+FeatureDictionary.OTHER)>p.getSecond()))
                    label = ""+FeatureDictionary.OTHER;
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
                System.err.println(tok+", wc: "+wc+", label: "+label+", al: "+labels[ti]+": "+beliefScore);
            }
            //now cluster all the 'N' labels together
            Pattern p = Pattern.compile("[^\\s,\\-:;\"\\(\\))!\\?]+");
            Matcher m = p.matcher(sent);
            int pe = -1;
            int li = 0;
            List<Triple<String, Integer, Integer>> ftoks = new ArrayList<>();
            String str = "";
            int start = 0;
            List<String> stopChars = Arrays.asList(",",":",";","\"","(",")","!","-","?");
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
                            str = removeTrailingSW(str);
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
                ftoks.add(new Triple<>(removeTrailingSW(str), start, pe));
            List<Triple<String, Integer, Integer>> ctoks = new ArrayList<>();
            for (Triple<String, Integer, Integer> tok : ftoks) {
                String tc = tok.first.toLowerCase();
                if (tc.contains("i am") || tc.contains("i'm") || tc.startsWith("i ") || tc.contains(" i ") || tc.endsWith(" i"))
                    continue;
                ctoks.add(tok);
            }
            return ctoks;
        }

        public List<Triple<String,Integer, Integer>> tokenizePOS(String sent, Short type){
            List<Pair<String,String>> posTags = NLPUtils.posTag(sent);
            List<Triple<String,Integer,Integer>> ret = new ArrayList<>();
            List<String> allowedPOSTags = new ArrayList<>();
            if(type == FeatureDictionary.PERSON)
                allowedPOSTags = Arrays.asList("NNP","NNS","NN");
            else
                allowedPOSTags = Arrays.asList("NNP","NNS","NN","JJ","IN","POS");

            String str = "";
            for(Pair<String,String> p: posTags){
                String tag = p.second;
                //POS for 's
                if(allowedPOSTags.contains(tag))
                    str += p.getFirst()+" ";
                else{
                    ret.add(new Triple<>(str, -1, -1));
                    str = "";
                }
            }
            if(!str.equals(""))
                ret.add(new Triple<>(str,-1,-1));
            return ret;
        }

        public Pair<String,Double> scoreSubstrs(String phrase, Short type, SequenceModel nerModel, double threshold) {
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
    int MAX_SENT = 100;
    Map<String,Integer> dict = NEREvaluator.buildDictionary(3);
    Short type = FeatureDictionary.LEGISTLATURE;
    //Pair<Set<String>,Set<String>> p = new Some().readTokensDBpedia();
    Map<String,Double> found = new LinkedHashMap<>();
    for(Document doc: docs) {
        String c = archive.getContents(doc, true);
        String[] sents = NLPUtils.tokeniseSentence(c);
        for(String sent: sents) {
            List<Triple<String,Integer,Integer>> toks = new Some().tokenizePOS(sent, type);
            List<Pair<String,String>> posTags = NLPUtils.posTag(sent);
            for(Pair<String,String> p: posTags)
                out.println(p.getFirst()+"["+p.getSecond()+"]");
            out.println("<br>");
            for (Triple<String,Integer,Integer> t : toks) {
                Pair<String, Double> p = new Some().scoreSubstrs(t.getFirst(), type, nerModel, 1E-4);
                String color = "";
                if(p.getSecond()>0)
                    color="red";
                if(p.getSecond()>0) {
                    out.println("<span style='color:" + color + "'>Found: " + t.first + "</span>[" + p.first + ":" + p.second + "]----");
                    found.put(p.first, p.second);
                }
                else
                    out.println("<span style='color:"+color+"'>"+t.first+"</span>----");
            }
            out.println("<br>");
            out.println("----------------<br><br>");
            if(si++>MAX_SENT)
                break;
        }
    }
    List<Pair<String,Double>> sF = Util.sortMapByValue(found);
    out.println("Found: "+sF.size());
    out.println(" ----------------- <br>");
    for(Pair<String,Double> p: sF)
        out.println(p.getFirst()+" : "+p.getSecond()+"<br>");
    System.err.println(nerModel.dictionary.getLabel("this", nerModel.dictionary.features));
    System.err.println(nerModel.dictionary.features.get("this"));
//    Short type = FeatureDictionary.ORGANISATION;
//    String stype = "ORGANIZATION";//"LOCATION"; //, "PERSON"
//    String serializedClassifier = "/Users/vihari/epadd-ner/english.all.3class.distsim.crf.ser.gz";
//    AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifier(serializedClassifier);
//
//    Set<String> orgs = new LinkedHashSet<>();
//    int di = 0;
//    for(Document doc: docs){
//        String content = archive.getContents(doc, true);
//        Map<String,Double> some = new Some().find(content,type, nerModel);
//        for(String s: some.keySet()) {
//            String c = s.replaceAll("^The ","");
//            c = c.replaceAll("\\W+$|^\\W+","");
//            all.put(c, some.get(s));
//        }
//
//        List<List<CoreLabel>> labels = classifier.classify(content);
//        for (List<CoreLabel> sentence : labels) {
//            String str = "";
//            for (CoreLabel word : sentence) {
//                String ann = word.get(CoreAnnotations.AnswerAnnotation.class);
//                String w = word.word();
//                if(!ann.equals(stype)) {
//                    if(!str.equals(""))
//                        orgs.add(str);
//                    str = "";
//                }
//                else{
//                    if(!str.equals(""))
//                        str += " "+w;
//                    else
//                        str = w;
//                }
//            }
//        }
//        if(i++ > 100)
//            break;
//    }
//    List<Pair<String,Double>> ps = Util.sortMapByValue(all);
//    Set<String> found = new LinkedHashSet<>();
//    for(Pair<String,Double> p: ps){
//        String color="";
//        if(orgs.contains(p.getFirst())){
//            color="red";
//            found.add(p.getFirst());
//        }
//        out.println("<span style='color:"+color+"'>"+p.getFirst()+"</span><br>");
//    }
//    out.println("===================<br>Missing:<br>");
//    for(String o: orgs){
//        if(!found.contains(o))
//            out.println(o+"<br>");
//    }
//
//    Map<String, Pair<Integer,Integer>> patts = new LinkedHashMap<>();
//
//    List<Pair<String,Double>> sall = Util.sortMapByValue(all);
//    for(Pair<String,Double> p: sall) {
//        //String not = new Some().getNotation(p.getFirst(), words);
//        //Pair<Integer, Integer> pair = patts.get(not);
//        out.println(p.getFirst() + " ::: " + p.getSecond()+ "<br>");
//    }
    //nerModel.fdw.close();
%>