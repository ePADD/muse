package edu.stanford.muse.ner.model;

import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.featuregen.FeatureGenerator;
import edu.stanford.muse.ner.featuregen.WordSurfaceFeature;
import edu.stanford.muse.ner.featuregen.FeatureDictionary.MU;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.ner.tokenizer.POSTokenizer;
import edu.stanford.muse.util.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;

/**
 * Created by vihari on 07/09/15.
 * This is a Bernoulli Mixture model, every word or pattern is considered a mixture. Does the parameter learning (mu, pi) for every mixture and assigns probabilities to every phrase.
 */
public class SequenceModel implements Serializable{
    public FeatureDictionary dictionary;
    public static String modelFileName = "SeqModel.ser";
    private static final long serialVersionUID = 1L;
    static Log log = LogFactory.getLog(SequenceModel.class);
    //public static final int MIN_NAME_LENGTH = 3, MAX_NAME_LENGTH = 100;
    public static FileWriter fdw = null;
    static CICTokenizer tokenizer = new CICTokenizer();
    public Map<String,String> dbpedia;

    public SequenceModel(FeatureDictionary dictionary, CICTokenizer tokenizer) {
        this.dictionary = dictionary;
        this.tokenizer = tokenizer;
    }

    public SequenceModel(){
    }

    /**
     * @param other boolean if is of other type*/
    public static double getLikelihoodWithOther(String phrase, boolean other) {
        phrase = phrase.replaceAll("^\\W+|\\W+$", "");
        if (phrase.length() == 0) {
            if (other)
                return 1;
            else
                return 1.0 / Double.MAX_VALUE;
        }

        String[] tokens = phrase.split("\\s+");
        double p = 1;
        for(String token: tokens) {
            String orig = token;
            token = token.toLowerCase();
            List<String> noise = Arrays.asList("P.M", "P.M.", "A.M.", "today", "saturday", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december", "thanks");
            if (noise.contains(token)) {
                if (other)
                    p *= 1;
                else
                    p *= 1.0 / Double.MAX_VALUE;
                continue;
            }
            Map<String, Pair<Integer, Integer>> map = EnglishDictionary.getDictStats();
            Pair<Integer, Integer> pair = map.get(token);

            if (pair == null) {
                //log.warn("Dictionary does not contain: " + token);
                if (orig.length() == 0) {
                    if (other)
                        p *= 1;
                    else
                        p *= 1.0 / Double.MAX_VALUE;
                }
                if (orig.charAt(0) == token.charAt(0)) {
                    if (other)
                        p *= 1;
                    else
                        p *= 1.0 / Double.MAX_VALUE;
                } else {
                    if (other)
                        p *= 1.0 / Double.MAX_VALUE;
                    else
                        p *= 1.0;
                }
                continue;
            }
            //thats pretty much no O token for initial capital word
            double v = (double) pair.getFirst() / (double) pair.getSecond();
            //System.err.println("Phrase: "+token+", "+v+", "+p+", "+other);
//            if (other)
//                p *= (1 - v);
//            else
//                p *= v;
            if (v > 0.25) {
                if(other)
                    return 1.0 / Double.MAX_VALUE;
                else
                    return 1.0;
            }
            else {
                if (token.charAt(0) == orig.charAt(0)) {
                    if(other)
                        return 1;
                    else
                        return 1.0/Double.MAX_VALUE;
                }
                else {
                    if(other)
                        return 1.0 / Double.MAX_VALUE;
                    else
                        return 1.0;
                }
                //return 1;
            }
        }
        return p;
    }

    /**
     * Does sequence labeling of a phrase; adapts a dynamic programming approach
     * The complexity of this method has quadratic dependence on number of words in the phrase, hence should be careful with the length
     * O(T*W^2) where W is number of tokens in the phrase and T is number of possible types
     * Since the word features that we are using are dependent on the boundary of the phrase i.e. the left and right semantic types, features on dictionary lookup e.t.c.
     * @note This method only returns the entities from he best labeled sequence.
     * @param phrase - String that is to be sequence labelled, keep this short; The string will be rejected if it contains more than 15 words
     * @return all the entities along with their types and quality score found in the phrase
    */
    public Map<String, Pair<Short, Double>> seqLabel(String phrase) {
        //This step of uncanonicalizing phrases helps merging things that have different capitalization and in lookup
        phrase = EmailUtils.uncanonicaliseName(phrase);
        //phrase = clean(phrase);
        Map<Integer, Triple<Double, Integer, Short>> tracks = new LinkedHashMap<>();
        if(phrase==null||phrase.length()==0||!phrase.contains(" "))
            return new LinkedHashMap<>();
        phrase = phrase.replaceAll("^\\W+|\\W+^","");

        String[] tokens = phrase.split("\\s+");
        if(tokens.length>15)
            return new LinkedHashMap<>();
        //since there can be large number of types every token can take
        //we restrict the number of possible types we consider to top 5
        //seer the complexity of the method
        Set<Short> cands = new LinkedHashSet<>();
        Map<Short, Double> candTypes = new LinkedHashMap<>();
        for (String token : tokens) {
            token = token.toLowerCase();
            FeatureDictionary.MU mu = dictionary.features.get(token);
            if (mu == null || mu.numMixture == 0)
                continue;
            for (String f : mu.muVectorPositive.keySet()) {
                if (f.startsWith("T:")) {
                    short type = Short.parseShort(f.substring(2));
                    double val = mu.muVectorPositive.get(f) / mu.numMixture;
                    if (!candTypes.containsKey(type))
                        candTypes.put(type, 0.0);
                    candTypes.put(type, candTypes.get(type) + val);
                }
            }
            List<Pair<Short, Double>> scands = Util.sortMapByValue(candTypes);
            int si = 0, MAX = 5;
            for (Pair<Short, Double> p : scands)
                if (si++ < MAX)
                    cands.add(p.getFirst());
        }
        //This is just a standard dynamic programming algo, the only difference is
        //at every word we are checking for the every possible segment
        short OTHER = -2;
        cands.add(OTHER);
        for (int ti = 0; ti < tokens.length; ti++) {
            double max = -1;
            int bi = -1;
            short bt = -10;
            for (short t : cands) {
                int tj = 0;
                //dont allow multi word phrases with these types
                if (t == OTHER || t == FeatureDictionary.OTHER)
                    tj = ti;
                for (; tj <= ti; tj++) {
                    double val = 1;
                    if (tj > 0)
                        val *= tracks.get(tj - 1).first;
                    String segment = "";
                    for (int k = tj; k < ti + 1; k++) {
                        segment += tokens[k];
                        if (k != ti)
                            segment += " ";
                    }
                    if (t != OTHER)
                        val *= getConditional(segment, t)*getLikelihoodWithOther(segment, false);
                    else
                        val *= getLikelihoodWithOther(segment, true);
                    //System.err.println("Considering segment: " + segment + ", type: " + t + ", " + val);
                    if (val > max) {
                        max = val;
                        bi = tj - 1;
                        bt = t;
                    }
                }
            }
            tracks.put(ti, new Triple<>(max, bi, bt));
        }

        //the backtracking step
        Map<String, Pair<Short, Double>> segments = new LinkedHashMap<>();
        int start = tokens.length - 1;
        while (true) {
            Triple<Double, Integer, Short> t = tracks.get(start);
            String seg = "";
            for (int ti = t.second + 1; ti <= start; ti++)
                seg += tokens[ti] + " ";
            seg = seg.substring(0,seg.length()-1);
            double val;
            if(t.getThird() != OTHER)
                val = getConditional(seg, t.getThird())*getLikelihoodWithOther(seg, false);
            else
                val = getLikelihoodWithOther(seg, true);

            segments.put(seg, new Pair<>(t.getThird(), val));
            start = t.second;
            if (t.second == -1)
                break;
        }
        return segments;
    }

    public Map<String, Double> getAllFeatureOfType(FeatureDictionary.MU mu, String selector) {
        Map<String, Double> select = new LinkedHashMap<>();
        for (String str : mu.muVectorPositive.keySet()) {
            if (str.startsWith(selector))
                select.put(str, mu.muVectorPositive.get(str));
        }
        return select;
    }

    //@param, neighbouring word and centered word
    public double dotProduct(String nstr, String cstr, Boolean left) {
        FeatureDictionary.MU nmu = dictionary.features.get(nstr);
        FeatureDictionary.MU cmu = dictionary.features.get(cstr);
        final int THRESH = 5;
        if (nmu == null || cmu == null || cmu.numSeen<THRESH || nmu.numSeen<THRESH)
            return 1.0 / FeatureDictionary.allTypes.length;
        Map<String, Double> v1;
        if (left)
            v1 = getAllFeatureOfType(cmu, "L:");
        else
            v1 = getAllFeatureOfType(cmu, "R:");
        Map<String, Double> v2 = getAllFeatureOfType(nmu, "T:");
//            System.err.println(cstr + " V1: " + v1);
//            System.err.println(nstr + " V2: " + v2);
        double s = 0;
        if (nmu.numMixture == 0 || cmu.numMixture == 0)
            return 0;
        for (String str : v1.keySet()) {
            if (str.endsWith("NULL"))
                continue;
            Short type = Short.parseShort(str.substring(2));
            if (v2.containsKey("T:" + type))
                s += v1.get(str) * v2.get("T:" + type) / (nmu.numMixture * cmu.numMixture);
        }
        //System.err.println("S:" + s);
        return s;
    }

    public double getConditional(String phrase, Short type) {
        Map<String, FeatureDictionary.MU> features = dictionary.features;
        Map<String, Set<String>> tokenFeatures = dictionary.generateFeatures2(phrase, type);
        String[] tokens = phrase.split("\\s+");
        if(FeatureDictionary.sws.contains(tokens[0]) || FeatureDictionary.sws.contains(tokens[tokens.length-1]))
            return 0;
        if(dbpedia == null){
            Map<String,String> orig = EmailUtils.readDBpedia();
            dbpedia = new LinkedHashMap<>();
            for(String str: orig.keySet())
                dbpedia.put(str.toLowerCase(),orig.get(str));
        }

        double sorg = 0;
        String dbpediaType = dbpedia.get(phrase.toLowerCase());
        if(dbpediaType!=null && FeatureDictionary.aTypes.get(type)!=null){
            for(String atype: FeatureDictionary.aTypes.get(type)){
                if(dbpediaType.endsWith(atype)) {
                    if(dbpediaType.endsWith("Country|PopulatedPlace|Place"))
                        return 1;
                    else if (phrase.contains(" "))
                        return 1;
                }
            }
        }
        String[] patts = FeatureDictionary.getPatts(phrase);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int si = 0; si < patts.length; si++) {
            String patt = patts[si];
            map.put(patt, si);
        }

        for (String mid : tokenFeatures.keySet()) {
            Double d;
            MU mu = features.get(mid);
            int THRESH = 5;
            //imposing the frequency constraint on numMixture instead of numSeen can benefit in weeding out terms that are ambiguous, which could have appeared many times, but does not appear to have common template
            //TODO: this check for new token is to reduce the noise coming from lowercase words starting with the word "new"
            if (mu != null && ((type!=FeatureDictionary.PERSON && mu.numMixture>THRESH)||(type==FeatureDictionary.PERSON && mu.numMixture>2)) && !mid.equals("new") && !mid.equals("first") && !mid.equals("open"))
                d = mu.getLikelihood(tokenFeatures.get(mid), dictionary);
            else
                //a likelihood that assumes nothing
                d = MU.getMaxEntProb();
            double val = d;
            if (val > 0) {
                double freq = 0;
                if (features.get(mid) != null)
                    freq = features.get(mid).getPrior();
                val *= freq;
            }
//            if (val == 0)
//                continue;
//            else {
//                int ci = map.get(mid);
//                for (String str : map.keySet()) {
//                    if (map.get(str) < ci) {
//                        val *= dotProduct(str, mid, true);//dictionary.getLikelihoodWithType(leftLabel, str);
//                    } else if (map.get(str) > ci)
//                        val *= dotProduct(str, mid, false);//dictionary.getLikelihoodWithType(rightLabel, str);
//                }
//                if(ci == 0) {
//                    Double fv = mu.muVectorPositive.get("L:NULL");
//                    int v = FeatureDictionary.MU.getNumberOfSymbols("L:NULL");
//                    if(fv!=null)
//                        val *= (fv+1)/(mu.numMixture+FeatureDictionary.MU.getNumberOfSymbols("L:NULL"));
//                    else val *= 1.0/v;
//                }
//                if(ci == patts.length-1){
//                    Double fv = mu.muVectorPositive.get("R:NULL");
//                    int v = FeatureDictionary.MU.getNumberOfSymbols("R:NULL");
//                    if(fv!=null)
//                        val *= (fv+1)/(mu.numMixture+v);
//                    else val *= 1.0/v;
//                }
//            }
            //Should actually use logs here, not sure how to handle sums with logarithms
            sorg += val;
        }
        return sorg;
    }

    public double score(String phrase, Short type) {
        //if contains "The" in the beginning, score it without "The"
        if(phrase.startsWith("The "))
            phrase = phrase.replaceAll("^The ","");
        List<String> commonWords = Arrays.asList("as", "because", "just", "in", "by", "for", "and", "to", "on", "of", "dear", "according", "think", "a", "an", "if", "at", "but", "the", "is");
        //what the candidate starts or ends with is important
        String[] swords = phrase.split("\\s+");
        String fw = swords[0].toLowerCase();
        fw = FeatureDictionary.endClean.matcher(fw).replaceAll("");
        String sw = null;
        if (swords.length > 1) {
            sw = swords[swords.length - 1].toLowerCase();
            sw = FeatureDictionary.endClean.matcher(sw).replaceAll("");
        }
        //the first word should not just be a word of special chars
        if (commonWords.contains(fw) || commonWords.contains(sw) || fw.equals(""))
            return 0.0;

        //String[] patts = FeatureDictionary.getPatts(substr);
        String[] scores = new String[FeatureDictionary.allTypes.length];
        //scores[0] = dictionary.getConditional(phrase, FeatureDictionary.OTHER, fdw);
        Short bt = FeatureDictionary.OTHER;
        double bs = -1;
        for(int ti=0;ti<FeatureDictionary.allTypes.length;ti++){
            Short t = FeatureDictionary.allTypes[ti];
            double s = 0;
            String frags = "";
            {
                Map<String, Set<String>> tokenFeatures = dictionary.generateFeatures2(phrase, type);
                for (String mid : tokenFeatures.keySet()) {
                    Double d;
                    if (dictionary.features.get(mid) != null)
                        d = dictionary.features.get(mid).getLikelihood(tokenFeatures.get(mid), dictionary);
                    else
                        d = 0.0;//(1.0/MU.WORD_LABELS.length)*(1.0/MU.WORD_LABELS.length)*(1.0/MU.TYPE_LABELS.length)*(1.0/MU.POSITION_LABELS.length)*(1.0/MU.ADJ_LABELS.length)*(1.0/MU.ADV_LABELS.length)*(1.0/MU.DICT_LABELS.length)*(1.0/MU.PREP_LABELS.length)*(1.0/MU.V_LABELS.length)*(1.0/MU.PN_LABELS.length);
                    if (Double.isNaN(d))
                        log.warn("Cond nan " + mid + ", " + d);
                    double val = d;
                    if (val > 0) {
                        double freq = 0;
                        if (dictionary.features.get(mid) != null)
                            freq = dictionary.features.get(mid).getPrior();
                        val *= freq;
                    }
                    s += val;//*dictionary.getMarginal(word);
                    frags += val+" ";
                }
            }
            scores[ti] = frags;
            if(s>bs) {
                bt = t;
                bs = s;
            }
        }
        if (fdw != null) {
            try {
                String str = "";
                for(int si=0;si<scores.length;si++)
                    str += FeatureDictionary.allTypes[si]+":<"+scores[si]+"> ";
                String[] words = phrase.split("[\\s,]+");
                String labelStr = "";
                for(String word: words) {
                    Pair<String,Double> p = dictionary.getLabel(word, dictionary.features);
                    FeatureDictionary.MU mu = dictionary.features.get(word);
                    String label = "";
                    if(mu == null)
                        label = p.getFirst();
                    else{
                        if(mu.getLikelihoodWithType(FeatureDictionary.OTHER)>p.getSecond())
                            label = ""+FeatureDictionary.OTHER;
                        else
                            label = p.getFirst();
                    }
                    labelStr += word+":"+label+" ";
                }
                fdw.write(labelStr+"\n");
                fdw.write(dictionary.generateFeatures2(phrase, type).toString()+"\n");
                fdw.write("String: " + phrase + " - " + str + "\n");
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        return bs*((bt==type)?1:-1);
    }

    public Pair<String,Double> scoreSubstrs(String phrase, Short type) {
        if (fdw == null) {
            try {
                fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator + "features.dump"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Pair<String,Double> ret = new Pair<>(phrase, 0.0);
       Map<String, Double> map = new LinkedHashMap<>();
        //recognises only orgs
        //labels = {O, B, I, E, S} null, beginning, in, end, solo
        //char[] labels = new char[]{'O', 'B', 'I', 'E', 'S'};
        //List<Triple<String,Integer,Integer>> cands = tokenizer.tokenize(content, type.equals(FeatureDictionary.PERSON));
        //for(Triple<String,Integer,Integer> cand: cands) {
        try {
            if (fdw != null)
                fdw.write(phrase + "\n");
            String[] words = phrase.split("\\s+");
            //brute force algorithm, is O(2^n)
            if (words.length > 10) {
                return new Pair<>(phrase, 0.0);
            }

            //look at all the sub strings and select a few with good score
            //if its a single word, we assign S label
            //if its multi word we assign B(I*)E labels.
            //We can afford to do this, because we are just looking inside a CIC pattern
            //In general, a search algorithm to find the subset with max probability should be employed
            Set<String> substrs = IndexUtils.computeAllSubstrings(phrase);
            Map<String, Double> ssubstrs = new LinkedHashMap<>();
            for (String substr : substrs) {
                double s = score(substr, type);
                ssubstrs.put(substr, s);
            }
            List<Pair<String, Double>> sssubstrs = Util.sortMapByValue(ssubstrs);
            for (Pair<String, Double> p : sssubstrs) {
                if (fdw != null)
                    fdw.write(p.getFirst() + " : " + p.getSecond() + "\n");
            }
            if (fdw != null)
                fdw.write("\n");
            if (sssubstrs.size() > 0)
                ret = new Pair<>(sssubstrs.get(0).first, sssubstrs.get(0).getSecond());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    public static Map<Short,List<String>> mergeTypes(Map<Short,Map<String,Double>> entities){
        Map<Short,List<String>> mTypes = new LinkedHashMap<>();
        Short[] types = new Short[]{FeatureDictionary.PERSON, FeatureDictionary.ORGANISATION, FeatureDictionary.PLACE};
        for(Short type: types)
            mTypes.put(type, new ArrayList<String>());

        Map<Short, Short[]>mappings = new LinkedHashMap<>();
        mappings.put(FeatureDictionary.PERSON, new Short[]{FeatureDictionary.PERSON});
        mappings.put(FeatureDictionary.PLACE, new Short[]{FeatureDictionary.AIRPORT, FeatureDictionary.HOSPITAL,FeatureDictionary.BUILDING, FeatureDictionary.PLACE, FeatureDictionary.RIVER, FeatureDictionary.ROAD, FeatureDictionary.MOUNTAIN,
                FeatureDictionary.ISLAND, FeatureDictionary.MUSEUM, FeatureDictionary.BRIDGE, FeatureDictionary.SHOPPINGMALL, FeatureDictionary.PARK,
                FeatureDictionary.HOTEL, FeatureDictionary.THEATRE, FeatureDictionary.LIBRARY,FeatureDictionary.MONUMENT});
        mappings.put(FeatureDictionary.ORGANISATION, new Short[]{FeatureDictionary.COMPANY,FeatureDictionary.POWERSTATION,FeatureDictionary.UNIVERSITY, FeatureDictionary.MILITARYUNIT, FeatureDictionary.ORGANISATION, FeatureDictionary.NEWSPAPER, FeatureDictionary.ACADEMICJOURNAL,
                FeatureDictionary.AIRLINE, FeatureDictionary.MAGAZINE, FeatureDictionary.POLITICALPARTY, FeatureDictionary.NPORG, FeatureDictionary.GOVAGENCY, FeatureDictionary.AWARD, FeatureDictionary.TRADEUNIN, FeatureDictionary.LEGISTLATURE, FeatureDictionary.LAWFIRM});
        for(Short gt: types){
            for(Short ft: mappings.get(gt))
                mTypes.get(gt).addAll(entities.get(ft).keySet());
        }
        return mTypes;
    }

    public Pair<Map<Short,Map<String,Double>>, List<Triple<String, Integer, Integer>>> find (String content){
        Map<Short, Map<String,Double>> maps = new LinkedHashMap<>();
        List<Triple<String,Integer,Integer>> offsets = new ArrayList<>();

        for(Short at: FeatureDictionary.allTypes)
            maps.put(at, new LinkedHashMap<String, Double>());

        String[] sents = NLPUtils.tokeniseSentence(content);
        for(String sent: sents) {
            List<Triple<String, Integer, Integer>> toks = tokenizer.tokenize(sent, false);
            for (Triple<String, Integer, Integer> t : toks) {
                Map<String,Pair<Short,Double>> entities = seqLabel(t.getFirst());
                for(String e: entities.keySet()){
                    Pair<Short,Double> p = entities.get(e);
                    //A new type is assigned to some words, which is of value -2
                    if(p.first<0)
                        continue;
                    if(p.first!=FeatureDictionary.OTHER && p.second>=1.0E-3) {
                        //System.err.println("Segment: "+t.first+", "+t.second+", "+t.third+", "+sent.substring(t.second,t.third));
                        offsets.add(new Triple<>(e, t.second+t.first.indexOf(e), t.second+t.first.indexOf(e)+e.length()));
                        maps.get(p.getFirst()).put(e, p.second);
                    }
                }
            }
        }
        return new Pair<>(maps, offsets);
    }

    public void writeModel(File modelFile) throws IOException{
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile));
        oos.writeObject(this);
        oos.close();
    }

    public static SequenceModel loadModel(File modelFile) throws IOException{
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FileInputStream(modelFile));
            SequenceModel model = (SequenceModel) ois.readObject();
            ois.close();
            return model;
        } catch (Exception e) {
            Util.print_exception("Exception while trying to load model from: " + modelFile, e, log);
            return null;
        }
    }

    public static SequenceModel train(){
        SequenceModel nerModel = new SequenceModel();
        Map<String,String> dbpedia = EmailUtils.readDBpedia(1.0/5);
        Set<String> fts = new LinkedHashSet<>();
        fts.add(WordSurfaceFeature.WORDS);
        FeatureGenerator[] fgs = new FeatureGenerator[]{new WordSurfaceFeature(fts)};
        FeatureDictionary dictionary = new FeatureDictionary(dbpedia, fgs);
        nerModel.dictionary = dictionary;
        //nerModel.tokenizer = new CICTokenizer();
        String mwl = System.getProperty("user.home")+File.separator+"epadd-settings"+File.separator;
        String modelFile = mwl + SequenceModel.modelFileName;
        log.info("Performing EM...");
        nerModel.dictionary.EM(dbpedia);
        try {
            nerModel.writeModel(new File(modelFile));
        }catch(IOException e){
            e.printStackTrace();
        }
        return nerModel;
    }

    public static void main(String[] args){
        try {
            String content = "Bob,\n" +
                    "Are you back from Maine: how is your sister?\n" +
                    "One piece of business: the edition from Tamarind Insititute has not\n" +
                    "arrived here. Do you know of some delay, or should I just get on the matter\n" +
                    "my self.\n" +
                    "I think you knew that ND was to publish the Duncan/Levertov letters,\n" +
                    "but when the book got too big they were happy enough not to do it. Last week\n" +
                    "the volume was accepted by the editorial board at Stanford Univ. Press. It\n" +
                    "all comes out as a fine collaboration with Al Gelpi, half the letters here,\n" +
                    "half the letters at Stanford. Out in about a year, so I am told. To replace\n" +
                    "the ND book I have given them another book called \"Robert Duncan's Ezra\n" +
                    "Pound,\" which has both sides of the correspondence in a narrative with other\n" +
                    "docs. Plus poems and unpublished essays. Then a new edtion of Letters, RD\n" +
                    "title, from a small press in St Louis. I've finished the Olson/Duncan\n" +
                    "letters, but am now struggling with the transcriptions of RD lectures of CO.\n" +
                    "They so resist being reading texts. I'll have more on that soon. Letters and\n" +
                    "Lectures to go to Wisconsin. Or, the snow at Christmas kept me off the\n" +
                    "streets and at my desk. In that sense it was a moral snow.\n" +
                    "If you're in Buffalo could you stand a stop after work?\n" +
                    "My best, " +
                    "National Bank some. National Kidney Foundation some . University Commencement.\n" +
                    "Address of Amuse Labs.OUT HOUSE, 19/1, Ramchandra Kripa, Mahishi Road, Malmaddi Dharwad.Address of US stay.483, Fulton Street, Palo Alto";
            System.err.println("Tokens: "+new POSTokenizer().tokenize(content));

            //String userDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user-creeley";
            String mwl = System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator;
            String modelFile = mwl + SequenceModel.modelFileName;
            if (fdw == null) {
                try {
                    fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator + "cache" + File.separator + "features.dump"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.err.println("Loading model...");
            SequenceModel nerModel = null;
            try{nerModel = SequenceModel.loadModel(new File(modelFile));}
            catch(IOException e){e.printStackTrace();}
            if(nerModel == null)
                nerModel = train();
//            int di =0;
//            for(Document doc: docs) {
//                String content = archive.getContents(doc, true);
//                System.err.println("Content: "+content);
//                Pair<Map<Short,List<String>>, List<Triple<String,Integer,Integer>>> mapsAndOffsets = nerModel.find(content);
//                for(Short type: mapsAndOffsets.getFirst().keySet())
//                    System.err.println(type +" : "+mapsAndOffsets.getFirst().get(type));
//                if(di++>10)
//                    break;
//            }
            Pair<Map<Short,Map<String,Double>>, List<Triple<String, Integer, Integer>>> mapsandoffsets = nerModel.find(content);
            Map<Short, List<String>> map = SequenceModel.mergeTypes(mapsandoffsets.first);
            for(Short type: map.keySet())
                System.out.println(type + " : "+mapsandoffsets.first.get(type)+"<br>");
            System.err.println(nerModel.dictionary.getConditional("Robert Creeley", FeatureDictionary.PERSON, fdw));
            System.err.println(nerModel.dictionary.getConditional("Robert Creeley", FeatureDictionary.OTHER, fdw));
            Map<String,String> dbpedia = EmailUtils.readDBpedia();
            System.err.println("DBpedia scoring check starts");
//            for(String entry: dbpedia.keySet()){
//                Short type = FeatureDictionary.codeType(dbpedia.get(entry));
//                System.err.println(entry + " " + dbpedia.get(entry) + " " + nerModel.dictionary.getConditional(entry, type, fdw));
//            }
            String[] check = new String[]{"California State Route 1", "New York Times", "Goethe Institute of Prague", "Venice high school students","Denver International Airport",
                    "New York International Airport", "Ramchandra Kripa, Mahishi Road"};
            for(String c: check) {
                System.err.println(c + ", " + nerModel.seqLabel(c));
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void main1(String[] args){
        System.err.println(getLikelihoodWithOther("SUnday saturday", true));
        System.err.println(getLikelihoodWithOther("SUnday saturday", false));
        System.err.println(getLikelihoodWithOther("Robert John", true));
        System.err.println(getLikelihoodWithOther("Robert John", false));
    }
}
