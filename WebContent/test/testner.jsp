<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="java.io.*" %>
<%@ page import="edu.stanford.muse.ner.NEREvaluator" %>
<%@ page import="edu.stanford.muse.util.Triple" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%
    String mwl = System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator;
    Short type = FeatureDictionary.ORGANISATION;

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

//    if(nerModel.dictionary.newWords == null)
//        nerModel.dictionary.computeNewWords();

    if (nerModel.fdw == null) {
        try {
            nerModel.fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "features.dump"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    NEREvaluator eval = new NEREvaluator(10000);
    List<String> sents = eval.getSentences();
    Set<String> orgs = new LinkedHashSet<>();
    double CUTOFF = 0;
    Map<String,Double> all = new LinkedHashMap<>();
    for(String sent: sents){
        Pair<Map<Short,List<String>>, List<Triple<String, Integer, Integer>>> some = nerModel.find(sent);
        for(String str: some.first.get(FeatureDictionary.ORGANISATION)) {
            //String[] patts = FeatureDictionary.getPatts(s);
            orgs.add(str);
            all.put(str, 1.0);
        }
        //System.err.println("Found: "+some.first.get(FeatureDictionary.ORGANISATION).size());
    }
    List<Pair<String,Double>> lst = Util.sortMapByValue(all);
    Set<String> borgs = eval.bNames.get(type);
    Set<String> temp = new LinkedHashSet<>();
    for(String org: borgs){
        String t = org.replaceAll("^\\W+|\\W+$","");
        temp.add(t);
    }
    borgs = temp;

    Set<String> found = new LinkedHashSet<>();
    for(Pair<String,Double> p: lst) {
        String color = "";
        if(borgs.contains(p.getFirst())) {
            color = "red";
            if(p.getSecond()>CUTOFF)
                found.add(p.getFirst());
        }
        out.println("<span style='color:"+color+"'>"+p.getFirst() + " : " + p.getSecond() + "</span><br>");
    }
    out.println("===================<br><br>Missing<br>");
    for(String bo: borgs){
        if(!found.contains(bo))
            out.println(bo+"<br>");
    }
    double p = (double)found.size()/orgs.size();
    double r = (double)found.size()/borgs.size();
    double f = 2*p*r/(p+r);
    //Triple<Double,Double, Double> t = eval.evaluate(orgs, FeatureDictionary.ORGANISATION);
    out.println("Precision: "+p+"<br>");
    out.println("Recall: "+r+"<br>");
    out.println("F1: "+f+"<br>");
    Map<Short, FeatureDictionary.MU> mus = nerModel.dictionary.features.get("co");
    for(Short srt: mus.keySet())
        out.println(srt+" - "+mus.get(srt).getLikelihoodWithThisType()+"<br>");
%>