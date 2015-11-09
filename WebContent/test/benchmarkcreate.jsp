<%@ page contentType="text/html;charset=UTF-8" language="java" %>
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
<%@ page import="opennlp.tools.util.Span" %>
<%
    Archive archive = JSPHelper.getArchive(request.getSession());
    List<Document> docs = archive.getAllDocs();
    String mwl = System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator;
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

    Map<Short, Set<String>> all = new LinkedHashMap<>();
    FileWriter fw = new FileWriter(System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator+"ner-benchmarks"+File.separator+"all-types.txt");
    Map<Short,String> desc = new LinkedHashMap<>();
    desc.put(FeatureDictionary.PERSON,"PERSON");desc.put(FeatureDictionary.COMPANY,"COMPANY");desc.put(FeatureDictionary.BUILDING,"BUILDING");desc.put(FeatureDictionary.PLACE,"PLACE");desc.put(FeatureDictionary.RIVER,"RIVER");
    desc.put(FeatureDictionary.ROAD,"ROAD");desc.put(FeatureDictionary.UNIVERSITY,"UNIVERSITY");desc.put(FeatureDictionary.MILITARYUNIT,"MILITARYUNIT");desc.put(FeatureDictionary.MOUNTAIN,"MOUNTAIN");desc.put(FeatureDictionary.AIRPORT,"AIRPORT");desc.put(FeatureDictionary.ORGANISATION,"ORGANISATION");desc.put(FeatureDictionary.DRUG,"DRUG");desc.put(FeatureDictionary.NEWSPAPER,"NEWSPAPER");desc.put(FeatureDictionary.ACADEMICJOURNAL,"ACADEMICJOURNAL");desc.put(FeatureDictionary.MAGAZINE,"MAGAZINE");desc.put(FeatureDictionary.POLITICALPARTY,"POLITICALPARTY");desc.put(FeatureDictionary.ISLAND,"ISLAND");desc.put(FeatureDictionary.MUSEUM,"MUSEUM");desc.put(FeatureDictionary.BRIDGE,"BRIDGE");desc.put(FeatureDictionary.AIRLINE,"AIRLINE");desc.put(FeatureDictionary.NPORG,"NPORG");desc.put(FeatureDictionary.GOVAGENCY,"GOVAGENCY");desc.put(FeatureDictionary.RECORDLABEL,"RECORDLABEL");desc.put(FeatureDictionary.SHOPPINGMALL,"SHOPPINGMALL");desc.put(FeatureDictionary.HOSPITAL,"HOSPITAL");desc.put(FeatureDictionary.POWERSTATION,"POWERSTATION");desc.put(FeatureDictionary.AWARD,"AWARD");desc.put(FeatureDictionary.TRADEUNIN,"TRADEUNIN");desc.put(FeatureDictionary.PARK,"PARK");desc.put(FeatureDictionary.HOTEL,"HOTEL");desc.put(FeatureDictionary.THEATRE,"THEATRE");desc.put(FeatureDictionary.LEGISTLATURE,"LEGISTLATURE");desc.put(FeatureDictionary.LIBRARY,"LIBRARY");desc.put(FeatureDictionary.LAWFIRM,"LAWFIRM");desc.put(FeatureDictionary.MONUMENT,"MONUMENT");
    int di = 0;
    for(Document doc: docs){
        String content = archive.getContents(doc, true);
        String[] sents = NLPUtils.tokeniseSentence(content);
        for(String sent: sents) {
            //having tokens that span multiple sentences is weird
            sent = sent.replaceAll("\n"," ");
            //because OpenNLP cannot handle URLs
            sent = sent.replaceAll("https?:[^\\s]+","");
            fw.write("#"+sent+"\n");
            Pair<Map<Short,Map<String,Double>>, List<Triple<String, Integer, Integer>>> pair = nerModel.find(sent);
            Map<String,Short> types = new LinkedHashMap<>();
            for (Short type : pair.first.keySet()) {
                if(!all.containsKey(type))
                    all.put(type, new LinkedHashSet<String>());
                all.get(type).addAll(pair.first.get(type).keySet());
                for(String e: pair.first.get(type).keySet())
                    types.put(e,type);
            }
            List<Triple<String,Integer,Integer>> offsets = pair.getSecond();
            Span[] tokens = NLPUtils.tokenizer.tokenizePos(sent);
            for(Span tok: tokens){
                String tag = "O";
                Triple<String,Integer,Integer> moff = new Triple<>("",-1,-1);
                for(Triple<String,Integer,Integer> off: offsets){
                    if(off.getSecond()<=tok.getStart() && off.getThird()>=tok.getEnd()) {
                        tag = desc.get(types.get(off.getFirst()));
                        moff = off;
                        break;
                    }
                }
                fw.write(tok.getCoveredText(sent)+" : "+tag+"\n");//+" [ "+tok.getStart()+", "+tok.getEnd()+"]"+" <"+moff.first+", "+moff.second+", "+moff.third+">\n");
            }
            fw.write("\n");
        }

        int MIN = Integer.MAX_VALUE;
        for(Short t: all.keySet())
            if(all.get(t).size()<MIN)
                MIN = all.get(t).size();
        if(MIN>=1 && all.size()>20)
            break;

        System.err.println("Considered: " + (di++) + " documents");
        if(di%10 == 0) {
            System.err.println("Stats");
            for (Short t : all.keySet())
                System.err.println(desc.get(t) + " : " +all.get(t).size());
        }
//        if(di>100)
//            break;
    }

    fw.close();
%>