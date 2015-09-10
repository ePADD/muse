<%@ page import="java.io.File" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.webapp.SimpleSessions" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.ner.model.SVMModel" %>
<%@ page import="java.util.Set" %>
<%@ page import="edu.stanford.muse.index.IndexUtils" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="com.sun.org.apache.xalan.internal.utils.FeatureManager" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="java.util.List" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.io.FileWriter" %>
<%@ page import="edu.stanford.muse.util.EmailUtils" %><%
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
    List<Document> docs = archive.getAllDocs();
    Map<String,Double> all = new LinkedHashMap<String, Double>();
    int i=0;
    System.err.println(nerModel.dictionary.getConditional("Sparrow", 'B', FeatureDictionary.ORGANISATION));
    System.err.println(nerModel.dictionary.getConditional("Press", 'E', FeatureDictionary.ORGANISATION));
    System.err.println(nerModel.dictionary.getConditional("Prof.", 'B', FeatureDictionary.ORGANISATION));
    System.err.println(nerModel.dictionary.getConditional("Buscaglia", 'E', FeatureDictionary.ORGANISATION));

//    Map<String, Pair<Integer,Integer>> patts = new LinkedHashMap<>();
//    Map<String,Map<Short, Pair<Double,Double>>> words = nerModel.dictionary.features.get("words");
//    Map<String,String> dbpedia = EmailUtils.readDBpedia();
//    Map<String,String> examples = new LinkedHashMap<>();
//    outer:
//    for(String str: dbpedia.keySet()){
//        String type = dbpedia.get(str);
//        String[] aTypes = FeatureDictionary.aTypes.get(FeatureDictionary.ORGANISATION);
//        boolean allowed = false;
//        for(String at: aTypes)
//            if(type.endsWith(at)) {
//                allowed = true;
//                break;
//            }
//        String[] patt = FeatureDictionary.getPatts(str);
//        String pattStr = "";
//        for(String p: patt){
//            if(!words.containsKey(p)){
//                continue outer;
//            }
//            Map<Short, Pair<Double,Double>> pm = words.get(p);
//            for(Short at: FeatureDictionary.allTypes){
//                Pair<Double,Double> pair = pm.get(at);
//                if(pair.second == 0)
//                    continue outer;
//                double d = pair.getFirst()/pair.getSecond();
//                pattStr += (int)(d*10)+",";
//            }
//            pattStr+=":::";
//        }
//        if(!patts.containsKey(pattStr))
//            patts.put(pattStr, new Pair<>(0,0));
//        if(allowed)
//            patts.get(pattStr).first++;
//        patts.get(pattStr).second++;
//        examples.put(pattStr, str);
//    }
//    Map<String,Double> pattS = new LinkedHashMap<>();
//    for(String str: patts.keySet()){
//        pattS.put(str, (double)patts.get(str).first);//(double)patts.get(str).first/patts.get(str).second);
//    }
//    List<Pair<String,Double>> spatts = Util.sortMapByValue(pattS);
//    for(Pair<String,Double> sp: spatts){
//        out.println(sp.getFirst()+" ::: "+patts.get(sp.getFirst()).first + " ::: "+patts.get(sp.getFirst()).second+" ::: "+sp.getSecond()+" --- "+examples.get(sp.getFirst())+"<br>");
//    }

    for(Document doc: docs){
        String content = archive.getContents(doc, true);
        Map<String,Double> some = nerModel.find(content);
        for(String s: some.keySet())
            all.put(s, some.get(s));
        if(i++%1000 == 0)
            out.println("Done: "+i+"/"+docs.size()+"<br>");
    }
    List<Pair<String,Double>> sall = Util.sortMapByValue(all);
    for(Pair<String,Double> p: sall)
        out.println(p.getFirst()+" ::: "+p.getSecond()+"<br>");
%>