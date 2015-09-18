<%@ page import="java.io.File" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="java.util.List" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.io.FileWriter" %>
<%@ page import="edu.stanford.muse.util.EmailUtils" %>
<%@ page import="edu.emory.mathcs.backport.java.util.Arrays" %><%
    class Some{
        public String getNotation(String str, Map<String,Map<Short, Pair<Double, Double>>> words){
            List<String> sws = Arrays.asList(new String[]{"for","to","a","the", "an", "and"});
            String[] patt = FeatureDictionary.getPatts(str);
            String pattStr = "";
            String[] tokens = str.split("\\s+");
            int pi = 0;
            for(String p: patt) {
                pi++;
                String w = tokens[pi-1];
                if (!words.containsKey(p)) {
                    pattStr += "0,0,0,:::";
                    continue;
                }

                if (sws.contains(w.toLowerCase()))
                    pattStr += w.toLowerCase();
                else {
                    Map<Short, Pair<Double, Double>> pm = words.get(p);
                    for (Short at : FeatureDictionary.allTypes) {
                        Pair<Double, Double> pair = pm.get(at);
                        if (pair.second == 0) {
                            pattStr += "NULL";
                        }
                        double d = pair.getFirst() / pair.getSecond();
                        pattStr += (int) (d * 10) + ",";
                    }
                }
                pattStr+=":::";
            }
            return pattStr;
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

    if (nerModel.fdw == null) {
        try {
            nerModel.fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "features.dump"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //List<Document> docs = archive.getAllDocs();
    Map<String,Double> all = new LinkedHashMap<String, Double>();
    int i=0;

    List<Document> docs = archive.getAllDocs();
    for(Document doc: docs){
        String content = archive.getContents(doc, true);
        Map<String,Double> some = nerModel.find(content);
        for(String s: some.keySet())
            all.put(s, some.get(s));
        if(i++%1000 == 0)
            out.println("Done: "+i+"/"+docs.size()+"<br>");
    }

    Map<String, Pair<Integer,Integer>> patts = new LinkedHashMap<>();
    Map<String,Map<Short, Pair<Double,Double>>> words = nerModel.dictionary.features.get("words");

    List<Pair<String,Double>> sall = Util.sortMapByValue(all);
    for(Pair<String,Double> p: sall) {
        String not = new Some().getNotation(p.getFirst(), words);
        Pair<Integer, Integer> pair = patts.get(not);
        out.println(p.getFirst() + " ::: " + p.getSecond() + " ::: " + not + "<br>");
    }
%>