<%@ page import="java.io.File" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.io.FileWriter" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.util.*" %><%
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
    int i=0;

    Short type = FeatureDictionary.PERSON;
    for(Document doc: docs){
        String content = archive.getContents(doc, true);
        Map<String,Double> some = new Some().find(content,type, nerModel);
        for(String s: some.keySet())
            all.put(s, some.get(s));
        if(i++%1000 == 0)
            out.println("Done: "+i+"/"+docs.size()+"<br>");
    }

    Map<String, Pair<Integer,Integer>> patts = new LinkedHashMap<>();

    List<Pair<String,Double>> sall = Util.sortMapByValue(all);
    for(Pair<String,Double> p: sall) {
        //String not = new Some().getNotation(p.getFirst(), words);
        //Pair<Integer, Integer> pair = patts.get(not);
        out.println(p.getFirst() + " ::: " + p.getSecond()+ "<br>");
    }
    //nerModel.fdw.close();
%>