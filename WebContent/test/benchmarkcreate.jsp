<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.io.File" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.stanford.muse.ner.model.BMMModel" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.io.FileWriter" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.util.*" %>
<%@ page import="opennlp.tools.util.Span" %>
<%@ page import="edu.stanford.muse.ner.tokenizer.POSTokenizer" %>
<%
    Archive archive = JSPHelper.getArchive(request.getSession());
    List<Document> docs = archive.getAllDocs();
    String mwl = System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator;
    String modelFile = mwl + BMMModel.modelFileName;
    BMMModel nerModel = (BMMModel)session.getAttribute("ner");
    if(nerModel == null) {
        System.err.println("Loading model...");
        try {
            nerModel = BMMModel.loadModel(modelFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (nerModel == null)
            nerModel = BMMModel.train();
        session.setAttribute("ner", nerModel);
    }

    Map<String, Set<String>> all = new LinkedHashMap<>();
    FileWriter fw = new FileWriter(System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator+"ner-benchmarks"+File.separator+"all-types.txt");
    Map<Short,String> desc = new LinkedHashMap<>();
    desc.put(FeatureDictionary.DISEASE, "HEALTH");desc.put(FeatureDictionary.HOSPITAL,"HEALTH");
    desc.put(FeatureDictionary.EVENT, "EVENT");
    desc.put(FeatureDictionary.PERSON,"PERSON");
    desc.put(FeatureDictionary.COMPANY,"COMPANY");desc.put(FeatureDictionary.LEGISLATURE,"COMPANY");
    desc.put(FeatureDictionary.AIRPORT,"PLACE");desc.put(FeatureDictionary.PLACE,"PLACE");
    desc.put(FeatureDictionary.RIVER,"NATURE");desc.put(FeatureDictionary.MOUNTAIN, "NATURE");
    desc.put(FeatureDictionary.ROAD,"ROAD");
    desc.put(FeatureDictionary.UNIVERSITY,"UNIVERSITY");//desc.put(FeatureDictionary.MILITARYUNIT,"MILITARYUNIT");
    desc.put(FeatureDictionary.ORGANISATION,"ORGANISATION"); desc.put(FeatureDictionary.GOVAGENCY,"ORGANISATION");
    desc.put(FeatureDictionary.AWARD,"AWARD");
    desc.put(FeatureDictionary.MUSEUM,"BUILDING");desc.put(FeatureDictionary.BUILDING,"BUILDING");desc.put(FeatureDictionary.LIBRARY,"BUILDING");

    int di = 0, numSent = 0;
    Random rand = new Random();
    for(Document doc: docs){
        String content = archive.getContents(doc, true);
        String[] sents = NLPUtils.tokenizeSentence(content);
        List<String> minorTypes = Arrays.asList("NATURE", "ROAD", "PARTY", "HEALTH", "AWARD","EVENT", "UNIVERSITY", "HEALTH", "NATURE");
        for(String sent: sents) {
            //having tokens that span multiple sentences is weird
            sent = sent.replaceAll("\n"," ");
            //because OpenNLP cannot handle URLs
            sent = sent.replaceAll("https?:[^\\s]+","");
            //these extra spaces are messing the offsets and hence the result
            sent = sent.replaceAll("\\s{2,}"," ");
            Pair<Map<Short,Map<String,Double>>, List<Triple<String, Integer, Integer>>> pair = nerModel.find(sent);
            Map<String,Short> types = new LinkedHashMap<>();
            Set<String> typesInSent = new LinkedHashSet<String>();
            for (Short type : pair.first.keySet()) {
                if (!desc.containsKey(type))
                    continue;
                if(pair.first.get(type).size()>0)
                    typesInSent.add(desc.get(type));
            }
            boolean good = false;
            for(String t: minorTypes){
                if(typesInSent.contains(t)) {
                    good = true;
                    break;
                }
            }
            //if the sentence does not contribute any of minor type, then dont consider
            if(!good) {
                if(rand.nextDouble()<0.5)
                    continue;
            }
            numSent++;
            fw.write("#"+sent+"\n");
            double THRESH = 1.0E-3;
            for (Short type : pair.first.keySet()) {
                if(!desc.containsKey(type))
                    continue;
                if(!all.containsKey(desc.get(type)))
                    all.put(desc.get(type), new LinkedHashSet<String>());
                for(String e: pair.first.get(type).keySet()) {
                    //if(e.equals("Boston") || e.equals("Geneva") || e.equals("Switzerland")){
                        System.err.println("Found "+e+" with type: "+desc.get(type)+", "+type + ", score: " +pair.first.get(type).get(e));
                    //}
                    if(pair.first.get(type).get(e)<THRESH)
                        continue;
                    all.get(desc.get(type)).add(e);
                    types.put(e, type);
                }
            }
            fw.write("#"+types+"\n");
            List<Triple<String,Integer,Integer>> toks = new POSTokenizer().tokenize(sent);
            Set<String> chunks = new LinkedHashSet<>();
            for(Triple<String, Integer,Integer> t: toks)
                chunks.add(t.getFirst());
            fw.write("#"+chunks+"\n");
            List<Triple<String,Integer,Integer>> offsets = pair.getSecond();
            Span[] tokens = NLPUtils.tokenizer.tokenizePos(sent);
            for(Span tok: tokens){
                String tag = "O";
                String token = tok.getCoveredText(sent).toString();
                //if the token does not contain any text, then just ignore it
                if(!token.matches("^\\W+$")) {
                    for (Triple<String, Integer, Integer> off : offsets) {
                        if (off.getSecond() <= (tok.getStart()) && (off.getThird())+1 >= tok.getEnd()) {
                            Short type = types.get(off.getFirst());
                            tag = desc.get(type);
                            break;
                        }
                    }
                }
                fw.write(token+" : "+tag+"\n");
            }
            fw.write("\n");
        }

        int MIN = Integer.MAX_VALUE;
        int mMin = Integer.MAX_VALUE;
        for(String t: all.keySet()) {
            if (!minorTypes.contains(t)) {
                if (all.get(t).size() < MIN)
                    MIN = all.get(t).size();
            }
            else{
                if (all.get(t).size() < mMin)
                    mMin = all.get(t).size();
            }
        }

        if(MIN>=30 && mMin>=10)
            break;

//        String str = "";
//        for(String a: all.keySet())
//            str += a+":"+all.get(a).size()+" ";

        System.err.println("Considered: " + numSent + " sents. in "+(di++) + " documents: "+MIN+", "+mMin);//+", as: "+all.size()+", desc size:"+desc.values().size());
        if(di%10 == 0) {
            System.err.println("Stats");
            for (String t : all.keySet())
                System.err.println(t + " : " +all.get(t).size());
        }
//        if(di>100)
//            break;
    }

    fw.close();
%>