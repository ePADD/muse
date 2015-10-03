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
<%@ page import="edu.stanford.nlp.ling.CoreLabel" %>
<%@ page import="edu.stanford.nlp.ling.CoreAnnotations" %>
<%@ page import="edu.stanford.nlp.ie.AbstractSequenceClassifier" %>
<%@ page import="edu.stanford.nlp.ie.crf.CRFClassifier" %><%
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

    Short type = FeatureDictionary.ORGANISATION;
    String stype = "ORGANIZATION";//"LOCATION"; //, "PERSON"
    String serializedClassifier = "/Users/vihari/epadd-ner/english.all.3class.distsim.crf.ser.gz";
    AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifier(serializedClassifier);

    Set<String> orgs = new LinkedHashSet<>();
    int di = 0;
    for(Document doc: docs){
        String content = archive.getContents(doc, true);
        Map<String,Double> some = new Some().find(content,type, nerModel);
        for(String s: some.keySet()) {
            String c = s.replaceAll("^The ","");
            c = c.replaceAll("\\W+$|^\\W+","");
            all.put(c, some.get(s));
        }

        List<List<CoreLabel>> labels = classifier.classify(content);
        for (List<CoreLabel> sentence : labels) {
            String str = "";
            for (CoreLabel word : sentence) {
                String ann = word.get(CoreAnnotations.AnswerAnnotation.class);
                String w = word.word();
                if(!ann.equals(stype)) {
                    if(!str.equals(""))
                        orgs.add(str);
                    str = "";
                }
                else{
                    if(!str.equals(""))
                        str += " "+w;
                    else
                        str = w;
                }
            }
        }
        if(i++ > 100)
            break;
    }
    List<Pair<String,Double>> ps = Util.sortMapByValue(all);
    Set<String> found = new LinkedHashSet<>();
    for(Pair<String,Double> p: ps){
        String color="";
        if(orgs.contains(p.getFirst())){
            color="red";
            found.add(p.getFirst());
        }
        out.println("<span style='color:"+color+"'>"+p.getFirst()+"</span><br>");
    }
    out.println("===================<br>Missing:<br>");
    for(String o: orgs){
        if(!found.contains(o))
            out.println(o+"<br>");
    }

    Map<String, Pair<Integer,Integer>> patts = new LinkedHashMap<>();

    List<Pair<String,Double>> sall = Util.sortMapByValue(all);
//    for(Pair<String,Double> p: sall) {
//        //String not = new Some().getNotation(p.getFirst(), words);
//        //Pair<Integer, Integer> pair = patts.get(not);
//        out.println(p.getFirst() + " ::: " + p.getSecond()+ "<br>");
//    }
    //nerModel.fdw.close();
%>