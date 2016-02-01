<%@ page import="java.io.File" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.stanford.muse.ner.model.SequenceModel" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="java.io.FileWriter" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.ner.dictionary.EnglishDictionary" %>
<%@ page import="edu.stanford.muse.util.Triple" %>
<%@ page import="edu.stanford.muse.util.EmailUtils" %>
<%
    class Some{
        public double getLikelihoodWithOther(String token){
            token = token.replaceAll("^\\W+|\\W+$","");
            if(token.length()==0)
                return 1;
            String orig = token;
            token = token.toLowerCase();
            Map<String,Pair<Integer,Integer>> map = EnglishDictionary.getDictStats();
            if(!map.containsKey(token)){
                System.err.println("Dictionary does not contain: "+token+", "+orig);
                //some random figures
                if(orig.charAt(0) == token.charAt(0))
                    return 1.0;
                else
                    return 1.0/Double.MAX_VALUE;
            }
            Pair<Integer,Integer> p = map.get(token);
            double v = (double)p.getFirst()/(double)p.getSecond();
            if(v>0.15)
                return 1.0/Double.MAX_VALUE;
            else {
                if(token.charAt(0)==orig.charAt(0))
                    return 1;
                else
                    return 1.0/Double.MAX_VALUE;
            }
        }

        public Map<String,Double> getAllFeatureOfType(FeatureDictionary.MU mu, String selector){
            Map<String,Double> select = new LinkedHashMap<>();
            for(String str: mu.muVectorPositive.keySet()) {
                if(str.startsWith(selector))
                    select.put(str, mu.muVectorPositive.get(str));
            }
            return select;
        }

        public double getMLikelihoodWithOther(String segment, boolean other, FeatureDictionary dictionary){
            String[] patts = FeatureDictionary.getPatts(segment);
            double p = 1;
            for(String patt: patts){
                FeatureDictionary.MU mu = dictionary.features.get(patt);
                if(mu!=null) {
                    if(other)
                        p *= mu.getLikelihoodWithType(FeatureDictionary.OTHER);
                    else
                        p *= (1-mu.getLikelihoodWithType(FeatureDictionary.OTHER));
                }
                else
                    p *= 0.5;
            }
            return p;
        }

        public Map<String,Pair<Short,Double>> seqLabel(String phrase, FeatureDictionary dictionary){
            Map<Integer,Triple<Double,Integer,Short>> tracks = new LinkedHashMap<>();
            phrase = phrase.replaceAll("^\\W+|\\W+^", "");
            String[] tokens = phrase.split("\\s+");
            Set<Short> cands = new LinkedHashSet<>();
            Map<Short,Double> candTypes = new LinkedHashMap<>();
            for(String token: tokens){
                token = token.toLowerCase();
                FeatureDictionary.MU mu = dictionary.features.get(token);
                if( mu == null || mu.numMixture==0)
                    continue;
                for(String f: mu.muVectorPositive.keySet()) {
                    if (f.startsWith("T:")) {
                        short type = Short.parseShort(f.substring(2));
                        double val = mu.muVectorPositive.get(f)/mu.numMixture;
                        if(!candTypes.containsKey(type))
                            candTypes.put(type,0.0);
                        candTypes.put(type, candTypes.get(type)+val);
                    }
                }
                List<Pair<Short,Double>> scands = Util.sortMapByValue(candTypes);
                int si=0, MAX = 5;
                for(Pair<Short,Double> p: scands)
                    if(si++<MAX)
                        cands.add(p.getFirst());
            }
            short OTHER = -2;
            cands.add(OTHER);
            for(int ti=0;ti<tokens.length;ti++){
                double max = -1;
                int bi = -1; short bt = -10;
                for(short t: cands) {
                    int tj = 0;
                    if(t==OTHER)
                        tj = ti;
                    for (; tj <= ti; tj++) {
                        double val = 1;
                        if(tj>0)
                            val *= tracks.get(tj-1).first;
                        String segment = "";
                        for(int k=tj;k<ti+1;k++) {
                            segment += tokens[k];
                            if(k!=ti)
                                segment+=" ";
                        }
                        if(t != OTHER)
                            val *= getConditional(segment, t, dictionary);
                        else
                            val *= new Some().getLikelihoodWithOther(segment);
                        System.err.println("Considering segment: "+segment+", type: "+t+", "+val);
                        if(val>max) {
                            max = val;
                            bi = tj - 1;
                            bt = t;
                        }
                    }
                    System.err.println("Maxval: "+max+", "+bi+", "+bt);
                }
                tracks.put(ti,new Triple<>(max, bi, bt));
            }

            Map<String,Pair<Short,Double>> segments = new LinkedHashMap<>();
            int start = tokens.length-1;
            while(true){
                Triple<Double,Integer,Short> t = tracks.get(start);
                String seg = "";
                for(int ti=t.second+1;ti<=start;ti++)
                    seg += tokens[ti]+" ";
                seg = seg.substring(0,seg.length()-1);
                double val;
                if(t.getThird() != OTHER)
                    val = getConditional(seg, t.getThird(), dictionary);
                else
                    val = getLikelihoodWithOther(seg);
                segments.put(seg, new Pair<>(t.getThird(),val));
                start = t.second;
                if(t.second == -1)
                    break;
            }
            return segments;
        }

        public double getConditional(String phrase, Short type, FeatureDictionary dictionary){
            Map<String,FeatureDictionary.MU> features = dictionary.features;
            Map<String, Set<String>> tokenFeatures = dictionary.generateFeatures(phrase, type);
            double sorg = 0;
            String[] patts = FeatureDictionary.getPatts(phrase);
            Map<String,Integer> map = new LinkedHashMap<>();
            for(int si=0;si<patts.length;si++) {
                String patt = patts[si];
                map.put(patt, si);
            }

            for(String mid: tokenFeatures.keySet()) {
                Double d;
                if(features.get(mid) != null)
                    d = features.get(mid).getLikelihood(tokenFeatures.get(mid), dictionary);
                else
                    //a likelihood that assumes nothing
                    d = 0.0;//(1.0/MU.WORD_LABELS.length)*(1.0/MU.WORD_LABELS.length)*(1.0/MU.TYPE_LABELS.length)*(1.0/MU.POSITION_LABELS.length)*(1.0/MU.ADJ_LABELS.length)*(1.0/MU.ADV_LABELS.length)*(1.0/MU.DICT_LABELS.length)*(1.0/MU.PREP_LABELS.length)*(1.0/MU.V_LABELS.length)*(1.0/MU.PN_LABELS.length);
                double val = d;
                if(val>0){
                    double freq = 0;
                    if(features.get(mid) != null)
                        freq = features.get(mid).getPrior();
                    val *= freq;
                }

                //Should actually use logs here, not sure how to handle sums with logarithms
                sorg += val;
            }
            return sorg;
        }

        public String[][] genCands(String phrase){
            //List<String> labelCands = new ArrayList<>();
            Short[] types = new Short[]{FeatureDictionary.AWARD,FeatureDictionary.PERSON,FeatureDictionary.UNIVERSITY};//FeatureDictionary.PLACE, FeatureDictionary.AWARD
            //};
            //Short[] types = new Short[]{FeatureDictionary.PLACE, FeatureDictionary.UNIVERSITY};
            List<String> allLabels = new ArrayList<>();
            for(Short t: types) {
//                if(FeatureDictionary.OTHER==t)
//                    continue;
                allLabels.add("B-" + t);
                allLabels.add("I-" + t);
            }
            allLabels.add("O");
            String[] toks = phrase.split("\\s+");
            List<String> labels = new ArrayList<>();
            labels.add("");
            for(String tok: toks){
                List<String> temp = new ArrayList<>();
                for(String l: labels)
                    for(String lab: allLabels) {
                        if(!l.equals(""))
                            temp.add(l + " " + lab);
                        else
                            temp.add(lab);
                    }
                labels = temp;
            }
            List<String[]> alabs = new ArrayList<>();
            for(String l: labels) {
                //l = l.substring(0, l.length()-1);
                String[] tmp = l.split("\\s+");
                boolean begin = false, dirty = false;
                Short type = -1;
                for(String t: tmp) {
                    if (t.startsWith("B-")) {
                        begin = true;
                        type = Short.parseShort(t.substring(2));
                    }
                    if((!begin&&t.startsWith("I-"))||(t.startsWith("I-")&&(type!=Short.parseShort(t.substring(2))))) {
                        dirty = true;
                        break;
                    }
                    if(t.equals("O"))
                        begin = false;
                }
                if(!dirty) {
                    alabs.add(tmp);
                    System.err.println("Adding: "+type+", "+l);
                }
            }

            return alabs.toArray(new String[alabs.size()][]);
        }

        public double score(String[] tokens, String[] labels, FeatureDictionary dictionary){
            Map<String,Short> segments = new LinkedHashMap<>();
            for(int ti=0;ti<tokens.length;ti++){
                String token = tokens[ti];
                if(labels[ti].equals("O"))
                    segments.put(token, FeatureDictionary.OTHER);
                else {
                    int tj = ti;
                    String seg = "";
                    Short type = Short.parseShort(labels[ti].substring(2));
                    do {
                        seg += tokens[tj] + " ";
                        tj++;
                    } while (tj < labels.length && labels[tj].startsWith("I-"));
                    ti = tj - 1;
                    System.err.println("Found segment " + seg + "," + type);
                    segments.put(seg, type);
                }
            }

            double s = 1;
            for(String str: segments.keySet()){
                Short type = segments.get(str);
                double v;
                if (type!=FeatureDictionary.OTHER)
                    v = new Some().getConditional(str, type, dictionary);
                else
                    v = new Some().getLikelihoodWithOther(str);
                s *= v;
                System.err.println("Score: "+str+" - "+type+" : "+v);
            }
            return s;
        }

        public Pair<Double,String> getLikelihood(FeatureDictionary.MU mu, Set<String> features, FeatureDictionary dictionary) {
            double p = 1.0;
            String debug_str = "";
            Set<String> left = new LinkedHashSet<>();
            Set<String> right = new LinkedHashSet<>();
            for (String f : features) {
                if (f.startsWith("L:"))
                    left.add(f);
                if (f.startsWith("R:"))
                    right.add(f);
            }
            Set<String> ts = new LinkedHashSet<>();
            for (Short at : FeatureDictionary.allTypes) {
                ts.add(at + "");
            }
            ts.add("NULL");
            boolean smooth = true;
            Short[] allTypes = FeatureDictionary.allTypes;
            String[] TYPE_LABELS = new String[allTypes.length];
            Map<String,Double> muVectorPositive = mu.muVectorPositive;
            double numMixture = mu.numMixture;
            if (muVectorPositive.size()==TYPE_LABELS.length)
                smooth = true;
            int si=0;
            for (Set<String> strs : new Set[]{left, right}) {
                si++;
                for (String l : strs) {
                    double s = 0;
                    Map<String,Double> ls = new LinkedHashMap<>();
                    for (String t : ts) {
                        Double v;
                        if(si==1)
                            v = muVectorPositive.get("L:"+t);
                        else
                            v = muVectorPositive.get("R:"+t);

                        if (numMixture == 0)
                            continue;
                        if(v==null)
                            v=0.0;
                        if(!smooth) {
                            s += dictionary.getConditionalOfTypeWithWord(l, t) * (v / numMixture);
                            ls.put(t,dictionary.getConditionalOfTypeWithWord(l, t) * (v / numMixture));//new Pair<>(dictionary.getConditionalOfTypeWithWord(l, t), (v / numMixture)));
                        }
                        else {
                            s += dictionary.getConditionalOfTypeWithWord(l, t) * ((v + 1) / (numMixture + allTypes.length + 1));
                            ls.put(t,dictionary.getConditionalOfTypeWithWord(l, t) * ((v + 1) / (numMixture + allTypes.length + 1)));//new Pair<>(dictionary.getConditionalOfTypeWithWord(l, t), (v + 1) / (numMixture + allTypes.length + 1)));
                        }
                        //System.err.println(dictionary.getConditionalOfTypeWithWord(l, t)+", "+v+","+numMixture);
                    }
                    String str = "";
                    List<Pair<String,Double>> tmp = Util.sortMapByValue(ls);
                    p *= Math.pow(s, 1.0 / strs.size());
                    for(Pair<String,Double> pair: tmp)
                        str += "<"+pair.getFirst()+":"+pair.getSecond()+"> ";
                    debug_str+="word: "+l+", "+str+"<br>";
                    debug_str+="score:"+Math.pow(s, 1.0/strs.size())+"<br>";
                }
            }
            //System.err.println(numMixture+", s: "+s);

            for (String f : features) {
                int v = FeatureDictionary.MU.getNumberOfSymbols(f);
                smooth = false;

                if (f.startsWith("L:") || f.startsWith("R:")) {
                    continue;
                }
                if (f.equals("L:-2") || f.equals("R:-2"))
                    smooth = true;
                if (muVectorPositive.size() == TYPE_LABELS.length)
                    smooth = true;

                if (!muVectorPositive.containsKey(f)) {
                    if (!smooth)
                        p *= 0;
                    else
                        p *= 1.0 / v;
                    continue;
                }
                double val;
                if (smooth)
                    val = (muVectorPositive.get(f) + 1) / (numMixture + v);
                else if (numMixture > 0)
                    val = (muVectorPositive.get(f)) / (numMixture);
                else
                    val = 0;
                debug_str += "F:"+f+", "+val+"<br>";
                p *= val;
            }
            return new Pair<>(p, debug_str);
        }
    }
    //Archive archive = JSPHelper.getArchive(request.getSession());
    String mwl = System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator;
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

   // List<Document> docs = archive.getAllDocs();
    //String[] tokens = new String[]{"new","york","state","university"};
    //String[][] labels = new String[4][4];
//    short tu = FeatureDictionary.UNIVERSITY, tp=FeatureDictionary.PLACE;
//    labels[0] = new String[]{"O","B-"+tu,"I-"+tu,"I-"+tu};
//    labels[1] = new String[]{"B-"+tu,"I-"+tu,"I-"+tu,"I-"+tu};
//    labels[2] = new String[]{"B-"+tp,"I-"+tp,"O","O"};
//    labels[3] = new String[]{"B-"+tp,"I-"+tp,"B-"+tu,"I-"+tu};
    //String phrase = "new york state university";
//    String phrase = "fulbright scholar award";//in american literature";;////"National Bank of Poland";//"John F. Kennedy International Airport";//"Iraqi National Bank";//"Hello National Bank of Poland";/;
//    String[] tokens = phrase.split("\\s+");
//    String[][] labels = new Some().genCands(phrase);
//    Map<String,Double> map = new LinkedHashMap<>();
//    for(String[] l: labels) {
//        double s = new Some().score(tokens, l, nerModel.dictionary);
//        String str = "";
//        for(int ti=0;ti<tokens.length;ti++)
//            str += tokens[ti]+"["+l[ti]+"] ";
//        //out.println(str + ", score: "+s+"<br>");
//        map.put(str, s);
//    }
//    List<Pair<String,Double>> pls = Util.sortMapByValue(map);
//    for(Pair<String,Double> p: pls)
//        out.println(p.getFirst()+" : "+p.getSecond()+"<br>");
    Map<Short,String> desc = new LinkedHashMap<>();
    desc.put(FeatureDictionary.PERSON,"PERSON");desc.put(FeatureDictionary.COMPANY,"COMPANY");
    desc.put(FeatureDictionary.DISEASE,"DISEASE");
    desc.put(FeatureDictionary.BUILDING,"BUILDING");desc.put(FeatureDictionary.PLACE,"PLACE");desc.put(FeatureDictionary.RIVER,"RIVER");desc.put(FeatureDictionary.ROAD,"ROAD");desc.put(FeatureDictionary.UNIVERSITY,"UNIVERSITY");desc.put(FeatureDictionary.MILITARYUNIT,"MILITARYUNIT");desc.put(FeatureDictionary.MOUNTAIN,"MOUNTAIN");desc.put(FeatureDictionary.AIRPORT,"AIRPORT");desc.put(FeatureDictionary.ORGANISATION,"ORGANISATION");desc.put(FeatureDictionary.NEWSPAPER,"NEWSPAPER");desc.put(FeatureDictionary.ACADEMICJOURNAL,"ACADEMICJOURNAL");desc.put(FeatureDictionary.MAGAZINE,"MAGAZINE");desc.put(FeatureDictionary.POLITICALPARTY,"POLITICALPARTY");desc.put(FeatureDictionary.ISLAND,"ISLAND");desc.put(FeatureDictionary.MUSEUM,"MUSEUM");desc.put(FeatureDictionary.BRIDGE,"BRIDGE");desc.put(FeatureDictionary.AIRLINE,"AIRLINE");desc.put(FeatureDictionary.NPORG,"NPORG");desc.put(FeatureDictionary.GOVAGENCY,"GOVAGENCY");desc.put(FeatureDictionary.SHOPPINGMALL,"SHOPPINGMALL");desc.put(FeatureDictionary.HOSPITAL,"HOSPITAL");desc.put(FeatureDictionary.POWERSTATION,"POWERSTATION");desc.put(FeatureDictionary.AWARD,"AWARD");desc.put(FeatureDictionary.TRADEUNIN,"TRADEUNIN");desc.put(FeatureDictionary.PARK,"PARK");desc.put(FeatureDictionary.HOTEL,"HOTEL");desc.put(FeatureDictionary.THEATRE,"THEATRE");desc.put(FeatureDictionary.LEGISTLATURE,"LEGISTLATURE");desc.put(FeatureDictionary.LIBRARY,"LIBRARY");desc.put(FeatureDictionary.LAWFIRM,"LAWFIRM");desc.put(FeatureDictionary.MONUMENT,"MONUMENT");desc.put(FeatureDictionary.OTHER,"OTHER");
    String[] phrases = new String[]{"Arts Awards","Geneva","Massachusetts College of Art in Boston","Delhi Times","Governor 's Arts Awards in New York State","Hatem Bazian from Univ. of California","U.S. Navy training academy","John F Kennedy International airport","fulbright scholar award in american Literature","New York Times","NY Times","Coffee Company","ad hoc March","Sidney","Smith","Chris Towne","song with texts by playwright/poet Bertolt Brecht","National Office in Washington DC","Bolivarian Circle","Organisation of Islamic Conference","California","Chicago March","Afghanistan","President Trent Willis","Drill Team","American Muslim Voice","BAYAN-USA Northern California","New Paltz","National Office in Washington DC","University of California at Berkeley","Grandmothers for Peace International in Phoenix","place at Highland Coffee Company","student at Senn High School","Fillipino community","Global Resistance Network","California Trent Willis","Paul George from Peninsula Peace","National Association of Purchasing Management","Reuters Ottawa Bureau","Douglas & Lomason Co","Caltex Petroleum Corp","China Daily"};//,"fulbright scholar award in american literature in syria","Buffalo Film Seminars Wendy Hiller","John F. Kennedy International Airport","Booker Prize recipient South African novelist John Coetzee","National Bank of Poland","called Iraqi National Bank"};

    for(String phrase: phrases) {
        Map<String, Pair<Short, Double>> segs = new Some().seqLabel(phrase, nerModel.dictionary);
        out.println(phrase + "<br>");

        for (String str : segs.keySet()) {
            Pair<Short, Double> p = segs.get(str);
            out.println(str + " ::: [" + desc.get(p.getFirst()) + ", " + p.getSecond() + "]<br>");
        }
        out.println("===============<br>");
    }
    String phrase = "New York Times";
    Short[] types = new Short[]{FeatureDictionary.NEWSPAPER, FeatureDictionary.PLACE};
    for(short type: types) {
        out.println("Type: "+type+"<br><br>");
        Map<String, Set<String>> features = nerModel.dictionary.generateFeatures2(phrase, type);
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, String> info = new LinkedHashMap<>();
        for (String mid : features.keySet()) {
            if (nerModel.dictionary.features.get(mid) != null) {
                FeatureDictionary.MU mu = nerModel.dictionary.features.get(mid);
                Pair<Double, String> p = new Some().getLikelihood(mu, features.get(mid), nerModel.dictionary);
                scores.put(mid, p.first * mu.getPrior());
                info.put(mid, p.getSecond());
            }
        }
        List<Pair<String, Double>> ss = Util.sortMapByValue(scores);
        for (Pair<String, Double> p : ss) {
            out.println(p.getFirst() + " " + p.second + "<br>");
            out.println(info.get(p.getFirst()));
            out.println("----<br>");
        }
    }
%>