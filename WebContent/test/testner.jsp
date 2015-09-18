<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.IndexUtils" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.webapp.SimpleSessions" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="java.io.*" %>
<%@ page import="edu.stanford.muse.ner.NEREvaluator" %>
<%@ page import="edu.stanford.muse.util.Triple" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%
    String mwl = System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator;

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
    double CUTOFF = 1;
    Map<String,Double> all = new LinkedHashMap<>();
    for(String sent: sents){
        Map<String,Double> some = nerModel.find(sent);
        for(String s: some.keySet()) {
            String[] patts = FeatureDictionary.getPatts(s);
            double x = some.get(s);
            if(patts.length==0)
                continue;
            x /= patts.length;
            if (x > CUTOFF)
                orgs.add(s);
            all.put(s, x);
        }
    }
    List<Pair<String,Double>> lst = Util.sortMapByValue(all);
    Set<String> borgs = eval.bNames.get(FeatureDictionary.ORGANISATION);
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
    Triple<Double,Double, Double> t = eval.evaluate(orgs, FeatureDictionary.ORGANISATION);
    out.println("Precision: "+p+"<br>");
    out.println("Recall: "+r+"<br>");
    out.println("F1: "+f+"<br>");
%>