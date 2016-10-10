<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="edu.stanford.muse.ner.tokenizer.CICTokenizer" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Triple" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="java.io.File" %>
<%@ page import="java.io.FileWriter" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.*" %>
<%
    class Some{
        public Map<String,Double> find (String content, Short type, SequenceModel model){
            Map<String,Double> map = new LinkedHashMap<>();
            List<Triple<String,Integer,Integer>> cands = new CICTokenizer().tokenize(content, type==FeatureDictionary.PERSON);
            for(Triple<String,Integer, Integer> t: cands){
                String pn = t.getFirst().replaceAll("^\\W+|\\W+$","");
                double s = model.score(pn, type);
                if (s>0) {
                    map.put(pn, s);
                }
            }
            return map;
        }
    }
    String mwl = System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator;
    //FeatureDictionary.LEGISTLATURE
    Short[] types;
    if(request.getParameter("type")!=null) {
        types = new Short[1];
        types[0] = Short.parseShort(request.getParameter("type"));
    }
    else {
//        types = new Short[]{FeatureDictionary.SETTLEMENT,FeatureDictionary.COMPANY,FeatureDictionary.SCHOOL,FeatureDictionary.SPORTSTEAM,
//            FeatureDictionary.UNIVERSITY,FeatureDictionary.AIRPORT,FeatureDictionary.ORGANISATION,FeatureDictionary.NEWSPAPER,FeatureDictionary.ACADEMICJOURNAL,
//            FeatureDictionary.MAGAZINE,FeatureDictionary.POLITICALPARTY,FeatureDictionary.AIRLINE,FeatureDictionary.NPORG,FeatureDictionary.GOVAGENCY,FeatureDictionary.RECORDLABEL,
//            FeatureDictionary.SHOPPINGMALL,FeatureDictionary.HOSPITAL,
//            FeatureDictionary.POWERSTATION,FeatureDictionary.TRADEUNIN,FeatureDictionary.LEGISTLATURE,FeatureDictionary.LIBRARY,FeatureDictionary.LAWFIRM,FeatureDictionary.COLLEGE};
        types = FeatureDictionary.allTypes;
    }
    String modelFile = mwl + SequenceModel.modelFileName;
    SequenceModel nerModel = (SequenceModel)session.getAttribute("ner");

    if(nerModel == null) {
        System.err.println("Loading model...");
        try {
            nerModel = SequenceModel.loadModel(modelFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (nerModel == null)
            nerModel = SequenceModel.train();
        session.setAttribute("ner", nerModel);
    }

    if (SequenceModel.fdw == null) {
        try {
            SequenceModel.fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "features.dump"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    Archive archive = JSPHelper.getArchive(request.getSession());

    List<String> sents = new ArrayList<>();
    int NUM_DOCS=1000;
    int di=0;
    for(Document doc: archive.getAllDocs()) {
        sents.add(archive.getContents(doc, true));
        if(di++>NUM_DOCS)
            break;
    }
    Set<String> orgs = new LinkedHashSet<>();
    double CUTOFF = 0;

    Map<Short, String> desc = FeatureDictionary.desc;
    if(types.length==1) {
        Map<String, Double> all = new LinkedHashMap<>();
        for (String sent : sents) {
            for (short t : types) {
                Map<String, Double> temp = new Some().find(sent, t, nerModel);
                for (String str : temp.keySet()) {
                    all.put(str, temp.get(str));
                    orgs.add(str);
                }
            }
        }
        List<Pair<String, Double>> lst = Util.sortMapByValue(all);
        Set<String> borgs = new LinkedHashSet<>();
        Set<String> temp = new LinkedHashSet<>();
        for (String org : borgs) {
            String t = org.replaceAll("^\\W+|\\W+$", "");
            temp.add(t);
        }
        borgs = temp;

        Set<String> found = new LinkedHashSet<>();
        for (Pair<String, Double> p : lst) {
            String color = "";
            String str = p.getFirst().replaceAll("^The ", "");
            if (borgs.contains(str)) {
                color = "red";
                if (p.getSecond() > CUTOFF) {
                    found.add(str);
                }
            }
            out.println("<span style='color:" + color + "'>" + p.getFirst() + " : " + p.getSecond() + "</span><br>");
        }
    }else{
        Map<Short, Set<String>> all = new LinkedHashMap<>();
        for (short t : types) {
            System.err.println("Done: "+t);
            for (String sent : sents) {
                Map<String, Double> temp = new Some().find(sent, t, nerModel);
                if(!all.containsKey(t))
                    all.put(t, new LinkedHashSet<String>());
                for(String str: temp.keySet())
                    if(temp.get(str)>CUTOFF)
                        all.get(t).add(str);
            }
        }
        for(Short str: all.keySet())
            out.println("<a href='browsetypes.jsp?type="+str+"' target='_blank'>"+desc.get(str)+"</a> : "+all.get(str).size()+"<br>");
    }
%>