package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.Config;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.ner.model.SVMModel;
import edu.stanford.muse.ner.model.SequenceModel;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;

import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.SimpleSessions;
import libsvm.svm_parameter;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probable improvements in train file generation are:
 * 1. Use proper relevant stop words for extracting entities for each type. (for example why would entity contain "in"? take stop-word stats from Dbpedia)
 * 2. recognise words that truly belong to the Org name and place name. For example in "The New York Times", dont consider "New" to be part of the org name, but just as a place name. Two questions: Will this really improve the situation?, Does this worse the results? because we are not considering the inherent ambiguity affiliated with that word.
 * 3. Get all the names associated with the social network of the group communicating and use it annotate sents.
 * 4. Better classification of People and orgs. In Robert Creeley's archive names like: Penn state, Some Internet solutions are also names from AB
 *
 * As the name suggests, feature dictionary keeps track of all the features generated during training.
 * During prediction/recognition step, features should be generated through this class, this emits a feature vector representation of the phrase
 * Behaviour of generating feature vector
 * Nominal: generates value based on proportion of times the nominal value appeared in iType to total frquency
 * Boolean: Passes the value of the feature generator as is
 * Numeric: Passes the value of the feature generator as is
 * TODO: Explore indexing of this data-structure
 * */
public class FeatureDictionary implements Serializable {
    private static final long serialVersionUID = 1L;
    //dimension -> instance -> entity type of interest -> #positive type, #negative type
    //patt -> Aa -> 34 100, pattern Aa occurred 34 times with positive classes of the 100 times overall.
    public Map<String, Map<String, Map<Short, Pair<Double, Double>>>> features = new LinkedHashMap<>();
    public static Set<String> newWords = null;
    //threshold to be classified as new word
    public static int THRESHOLD_FOR_NEW = 10;
    //contains number of times a CIC pattern is seen (once per doc), also considers quoted text which may reflect wrong count
    //This can get quite depending on the archive and is not a scalable solution

    //this data-structure is only used for Segmentation which itself is not employed anywhere
    public Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    //sum of all integers in the map above, i.e. frequencies of all pairs of words, required for normalization
    public int totalcount = 0;
    //total number of word patterns
    public Integer numPatts=0, numPattsWithDuplicates=0;

    //The data type of types (Short or String) below has no effect on the size of the dumped serialised model, Of course!
    public static Short PERSON = 1, ORGANISATION = 2, PLACE = 3;
    public static Short[] allTypes = new Short[]{PERSON, ORGANISATION, PLACE};
    static Log log = LogFactory.getLog(FeatureDictionary.class);
    public static Map<Short, String[]> aTypes = new LinkedHashMap<Short, String[]>();
    public FeatureGenerator[] featureGens = null;
    public static Map<Short, String[]> startMarkersForType = new LinkedHashMap<Short, String[]>();
    public static Map<Short, String[]> endMarkersForType = new LinkedHashMap<Short, String[]>();
    public static List<String> ignoreTypes = new ArrayList<String>();
    //feature types
    public static short NOMINAL = 0, BOOLEAN = 1, NUMERIC = 2, OTHER = 3;
    public static Pattern endClean = Pattern.compile("^\\W+|\\W+$");
    static List<String> sws = Arrays.asList("and","for","to","in","at","on","the","of");
    static {
        //the extra '|' is appended so as not to match junk.
        //matches both Person and PersonFunction in dbpedia types.
        aTypes.put(FeatureDictionary.PERSON, new String[]{"Person"});
        aTypes.put(FeatureDictionary.PLACE, new String[]{"Place"});
        aTypes.put(FeatureDictionary.ORGANISATION, new String[]{"Organisation", "PeriodicalLiterature|WrittenWork|Work"});

        //case insensitive
        startMarkersForType.put(FeatureDictionary.PERSON, new String[]{"dear", "hi", "hello", "mr", "mr.", "mrs", "mrs.", "miss", "sir", "madam", "dr.", "prof", "dr", "prof.", "dearest", "governor", "gov."});
        endMarkersForType.put(FeatureDictionary.PERSON, new String[]{"jr", "sr"});
        startMarkersForType.put(FeatureDictionary.PLACE, new String[]{"new"});
        endMarkersForType.put(FeatureDictionary.PLACE, new String[]{"shire", "city", "state", "bay", "beach", "building", "hall"});
        startMarkersForType.put(FeatureDictionary.ORGANISATION, new String[]{"the", "national", "univ", "univ.", "university", "school"});
        endMarkersForType.put(FeatureDictionary.ORGANISATION, new String[]{"inc.", "inc", "school", "university", "univ", "studio", "center", "service", "service", "institution", "institute", "press", "foundation", "project", "org", "company", "club", "industry", "factory"});

        /**
         * Something funny happening here, Creeley has a lot of press related orgs and many press are annotated with non-org type,
         * fox example periodicals, magazine etc. If these types are not ignored, then word proportion score for "*Press" is very low and is unrecognised
         * leading to a drop of recall from 0.6 to 0.53*/
        //these types may contain tokens from this type
        //dont see why these ignoreTypes have to be type (i.e. person, org, loc) specific
        ignoreTypes = Arrays.asList(
                "RecordLabel|Company|Organisation",
                "Band|Organisation"
        );
//        ignoreTypes = Arrays.asList(
//                "Election|Event", "MilitaryUnit|Organisation", "Ship|MeanOfTransportation", "OlympicResult",
//                "SportsTeamMember|OrganisationMember|Person", "TelevisionShow|Work", "Book|WrittenWork|Work",
//                "Film|Work", "Album|MusicalWork|Work", "Band|Organisation", "SoccerClub|SportsTeam|Organisation",
//                "TelevisionEpisode|Work", "SoccerClubSeason|SportsTeamSeason|Organisation", "Album|MusicalWork|Work",
//                "Ship|MeanOfTransportation", "Newspaper|PeriodicalLiterature|WrittenWork|Work", "Single|MusicalWork|Work",
//                "FilmFestival|Event",
//                "SportsTeamMember|OrganisationMember|Person",
//                "SoccerClub|SportsTeam|Organisation"
//        );
    }

    public FeatureDictionary(){
        features = new LinkedHashMap<>();
        newWords = new LinkedHashSet<>();
    }
    public FeatureDictionary(FeatureGenerator[] featureGens) {
        this.featureGens = featureGens;
    }

    /**
     * address book should be specially handled and DBpedia gazette is required.
     * and make sure the address book is cleaned see cleanAB method
     */
    public FeatureDictionary(Map<String, String> gazettes, FeatureGenerator[] featureGens) {
        this.featureGens = featureGens;
        addGazz(gazettes);
    }

    public FeatureDictionary addGazz(Map<String,String> gazettes){
        long start_time = System.currentTimeMillis();
        long timeToComputeFeatures = 0, tms;
        log.info("Analysing gazettes");

        int g = 0, nume = 0;
        final int gs = gazettes.size();
        int gi = 0;
        for (String str : gazettes.keySet()) {
            tms = System.currentTimeMillis();

            //if is a single word name and in dictionary, ignore.
            if (!str.contains(" ") && DictUtils.fullDictWords.contains(str.toLowerCase()))
                continue;

            String entityType = gazettes.get(str);
            if (ignoreTypes.contains(entityType)) {
                continue;
            }

            for (FeatureGenerator fg : featureGens) {
                if (!fg.getContextDependence()) {
                    for (Short iType : allTypes)
                        add(fg.createFeatures(str, null, null, iType), entityType, iType);
                }
            }
            String[] words = str.split("\\s+");
            for(int ii=0;ii<words.length-1;ii++) {
                String cstr = words[ii]+":::"+words[ii+1];
//                if(!counts.containsKey(cstr))
//                    counts.put(cstr, 0);
//                counts.put(cstr, counts.get(cstr)+1);
                totalcount++;
            }
            numPattsWithDuplicates += words.length;

            timeToComputeFeatures += System.currentTimeMillis() - tms;

            if ((++gi) % 10000 == 0) {
                log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs) + "% in gazette: " + g);
                log.info("Time spent in computing features: " + timeToComputeFeatures);
            }
            nume++;
        }

        log.info("Considered " + nume + " entities in " + gazettes.size() + " total entities");
        log.info("Done analysing gazettes in: " + (System.currentTimeMillis() - start_time));
        numPatts = features.get("words").size();

        return this;
    }

    public FeatureVector getVector(String cname, Short iType) {
        Map<String, List<String>> features = FeatureGenerator.generateFeatures(cname, null, null, iType, featureGens);
        return new FeatureVector(this, iType, featureGens, features);
    }

    //should not try to build dictionry outside of this method
    private void add(Map<String, List<String>> wfeatures, String type, Short iType) {
        if(wfeatures==null)
            return;
        for (String dim : wfeatures.keySet()) {
            if (!features.containsKey(dim))
                features.put(dim, new LinkedHashMap<String, Map<Short, Pair<Double, Double>>>());
            Map<String, Map<Short, Pair<Double, Double>>> hm = features.get(dim);
            if (wfeatures.get(dim) != null)
                for (String val : wfeatures.get(dim)) {
                    if (!hm.containsKey(val)) {
                        hm.put(val, new LinkedHashMap<Short, Pair<Double, Double>>());
                        for (Short at : allTypes)
                            hm.get(val).put(at, new Pair<>(0.0, 0.0));
                    }
                    Pair<Double, Double> p = hm.get(val).get(iType);
                    String[] allowT = aTypes.get(iType);
                    for (String at : allowT)
                        if (type.endsWith(at)) {
                            p.first++;
                            break;
                        }
                    p.second++;
                    hm.get(val).put(iType, p);
                }
            features.put(dim, hm);
        }
    }

    public static void computeNewWords(){
        Map<String,String> dbpedia = EmailUtils.readDBpedia();
        Map<String,Integer> wordFreqs = new LinkedHashMap<>();
        newWords = new LinkedHashSet<>();
        for (String str : dbpedia.keySet()) {
            //if is a single word name and in dictionary, ignore.
            if (!str.contains(" ") && DictUtils.fullDictWords.contains(str.toLowerCase()))
                continue;

            String entityType = dbpedia.get(str);
            if (ignoreTypes.contains(entityType)) {
                continue;
            }

            String[] words = str.split("\\s+");
            for(int ii=0;ii<words.length-1;ii++) {
                String w = words[ii].toLowerCase();
                w = endClean.matcher(w).replaceAll("");
                if(!wordFreqs.containsKey(w))
                    wordFreqs.put(w, 0);
                wordFreqs.put(w, wordFreqs.get(w)+1);
            }
        }
        for(String word: wordFreqs.keySet()){
            if(wordFreqs.get(word)<THRESHOLD_FOR_NEW)
                newWords.add(word);
        }
    }

    public static String[] getPatts(String phrase){
        List<String> patts = new ArrayList<>();
//        if(newWords == null){
//            log.info("Computing NEW words");
//            computeNewWords();
//        }
        String[] words = phrase.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            //dont emit stop words
            //canonicalize the word
            word = word.toLowerCase();
            word = endClean.matcher(word).replaceAll("");
            if(sws.contains(word)){
                continue;
            }
            String t;

//            if(newWords.contains(word))
//                word = ":NEW:";
            //if the next word is stop-word, append to the patt
            if(i<(words.length-1) && sws.contains(words[i+1]))
                word += " "+words[i+1];

            if (i > 0 && i < (words.length - 1))
                t = "*" + word + "*";
            else if (i == 0 && words.length > 1)
                t = word + "*";
            else if (i == (words.length - 1) && words.length > 1)
                t = "*" + word;
                //references are generally made with first name and this may have continuation like Harvard University or Apple_Inc
            else t = word;


            //emit all the words or patterns
            if (t != null)
                patts.add(t);
        }
        return patts.toArray(new String[patts.size()]);
    }

    public void EM(Map<String,String> gazettes){
        Map<String, Map<Short,Pair<Double, Double>>> words = features.get("words");
        Map<String, Map<Short,Pair<Double, Double>>> revisedwords = new LinkedHashMap<>();
        int MAX_ITER = 5;
        for(int i=0;i<MAX_ITER;i++) {
            for (String phrase : gazettes.keySet()) {
                String type = gazettes.get(phrase);
                for (Short iType : allTypes) {
                    String[] patts = getPatts(phrase);
                    boolean allowed = false;
                    for (String aType : aTypes.get(iType))
                        if (type.endsWith(aType)) {
                            allowed = true;
                            break;
                        }

                    double z = 0;
                    //responsibilities
                    Map<String, Double> gamma = new LinkedHashMap<>();
                    for (String patt : patts) {
                        if(words.get(patt) == null) {
                            //System.err.println("Did not find: "+patt+" "+iType);
                            continue;
                        }
                        Pair<Double, Double> p = words.get(patt).get(iType);
                        //this should not happen as we are just making pass over gazettes
                        if (p.getSecond() <= 0)
                            continue;

                        //only two tags, org/non-org
                        double pfreq =  (p.getFirst() + 1)/ (p.getSecond()+2);
//                        //pfreq and log functions as belief for the measure
                        double val = Math.log(p.getSecond()+1);
                        double d = (allowed?pfreq:(1-pfreq)) * val;
                        //by N cancels out whe we normalize
                        //double d = (allowed?pfreq:(1-pfreq)) * (p.getSecond()+2);
                        gamma.put(patt, d);
                        z += d;
                    }
                    if(z == 0)
                        continue;
                    //this one, approximates the contribution from all the other mixtures not considered here: ~ 1-n/N, N>>>n
                    z += 1;
                    for (String g : gamma.keySet())
                        gamma.put(g, gamma.get(g) / z);

                    for (String g : gamma.keySet()) {
                        if (!revisedwords.containsKey(g)) {
                            Map<Short, Pair<Double, Double>> map = new LinkedHashMap<>();
                            for (Short t : allTypes) {
                                map.put(t, new Pair<>(0.0, 0.0));
                            }
                            revisedwords.put(g, map);
                        }
                        if (allowed)
                            revisedwords.get(g).get(iType).first += gamma.get(g);
                        revisedwords.get(g).get(iType).second += gamma.get(g);
                    }
                }
            }
            double change = 0;
            for(String w: words.keySet())
                if(revisedwords.containsKey(w))
                    change += Math.abs(revisedwords.get(w).get(FeatureDictionary.ORGANISATION).getFirst()-words.get(w).get(FeatureDictionary.ORGANISATION).getFirst());
            log.info("Iter: "+i+", change: "+change);
            words = revisedwords;
            revisedwords = new LinkedHashMap<>();
        }
        try {
            FileWriter fw = new FileWriter(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "em.dump");
            Map<String, Double> some = new LinkedHashMap<>();
            for (String w: words.keySet())
                some.put(w, words.get(w).get(FeatureDictionary.ORGANISATION).getFirst()/words.get(w).get(FeatureDictionary.ORGANISATION).getSecond());
            List<Pair<String,Double>> ps = Util.sortMapByValue(some);
            for(Pair<String,Double> p: ps)
                fw.write(p.getFirst()+" ::: "+p.getSecond()+":::"+words.get(p.getFirst()).get(FeatureDictionary.ORGANISATION).getFirst()+":::"+words.get(p.getFirst()).get(FeatureDictionary.ORGANISATION).getSecond()+"\n");
        }catch(IOException e){
            e.printStackTrace();
        }
        features.put("words", words);
    }

    private static FeatureDictionary buildAndDumpDictionary(Map<String,String> gazz, String fn){
        try {
            FeatureDictionary dictionary = new FeatureDictionary(gazz, new FeatureGenerator[]{new WordSurfaceFeature()});
            //dump this
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fn));
            oos.writeObject(dictionary);
            oos.close();
            return dictionary;
        }catch(Exception e){
            log.info("Could not build/write feature dictionary");
            Util.print_exception(e, log);
            return null;
        }
    }

    public static FeatureDictionary loadDefaultDictionary() {
        String fn = Config.SETTINGS_DIR + File.separator + "dictionary.ser";
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FileInputStream(fn));
            FeatureDictionary model = (FeatureDictionary) ois.readObject();
            return model;
        } catch (Exception e) {
            Util.print_exception(e, log);
            if (e instanceof FileNotFoundException || e instanceof IOException) {
                log.info("Failed to load default dictionary file, building one");
                Map<String, String> dbpedia = EmailUtils.readDBpedia();
                return buildAndDumpDictionary(dbpedia, fn);
            } else {
                log.error("Failed to load default dictionary file, building one");
                return null;
            }
        }
    }

    public double getMaxpfreq(String name, Short iType) {
        String[] words = name.split("\\s+");
        double p = 0;
        for (String word : words) {
            double pw = 0;
            try {
                Pair<Double, Double> freqs = features.get("word").get(word).get(iType);
                pw = (double) freqs.first / freqs.second;
            } catch (Exception e) {
                ;
            }
            p = Math.max(pw, p);
        }
        return p;
    }

    /**
     * returns the value of the dimension in the features generated
     */
    public Double getFeatureValue(String name, String dim, Short iType) {
        FeatureVector wfv = getVector(name, iType);
        Double[] fv = wfv.fv;
        Integer idx = wfv.featureIndices.get(dim);
        if (idx == null || idx > fv.length) {
            //log.warn("No proper index found for dim " + dim + ", " + idx + " for " + name);
            return 0.0;
        }
        return fv[idx];
    }

    public static Map<String, String> cleanAB(Map<String, String> abNames, FeatureDictionary dictionary) {
        if (abNames == null)
            return abNames;

        Short iType = FeatureDictionary.PERSON;
        Map<String, String> cleanAB = new LinkedHashMap<>();
        for (String name : abNames.keySet()) {
            double val = dictionary.getFeatureValue(name, "words", iType);
            if(val > 0.5)
                cleanAB.put(name, abNames.get(name));
        }

        return cleanAB;
    }

    //P(tag/x,type);
    public double getConditional(String word, String patt, Short type){
        Map<Short,Pair<Double,Double>> pairMap;
        pairMap = features.get("words").get(patt);

        Pair<Double,Double> p;
        if(pairMap == null||pairMap.get(type) == null || pairMap.get(type).getSecond()==0)
            return 0.0;//p = new Pair<>(0.0,0.0);
        else
            p = pairMap.get(type);
        return p.getFirst()/p.getSecond();
    }

    //returns smoothed frequency
    public double getFreq(String patt){
        //some dummy type;
        Short type = FeatureDictionary.PERSON;
        Map<Short,Pair<Double,Double>> pairMap;
        pairMap = features.get("words").get(patt);
        Pair<Double,Double> p;
        if(pairMap == null||pairMap.get(FeatureDictionary.ORGANISATION) == null)
            p = new Pair<>(0.0,0.0);
        else
            p = pairMap.get(type);
        return p.getSecond();
    }

    //P(x)
    public double getMarginal(String word){
        double n = getFreq(word);
        return n/((double)numPatts + numPattsWithDuplicates);
    }

    /**
     * @param fw - First word
     * @param sw - Second word
     * @returns P(sw/fw)*/
    public double getMutualInformation(String fw, String sw){
        return 1.0;
//        //n(x)
//        double pfw = getMarginal(fw);
//        //double psw = getMarginal(sw);
//        Integer nfsw = counts.get(fw+":::"+sw);
//        if(nfsw == null)
//            nfsw = 0;
//        double tc = 2*Math.log(totalcount+counts.size());
//        double psfw = Math.log(nfsw+1)-tc;
//        return psfw - (pfw);
    }

    public int numPattHits(String phrase){
        String[] words = phrase.split("\\s+");
        int wi =0, numHits = 0;
        String[] patts = FeatureDictionary.getPatts(phrase);
        for(String patt: patts) {
            Map<Short, Pair<Double, Double>> pairMap = features.get("words").get(patt);
            if (pairMap != null && pairMap.get(FeatureDictionary.ORGANISATION) != null) {
                Pair<Double, Double> p = pairMap.get(FeatureDictionary.ORGANISATION);
                if(p.getFirst()>0 && p.getSecond()>0)
                    numHits++;
            }
            wi++;
        }
        return numHits;
    }

    @Override
    public String toString() {
        String res = "";
        for (String dim : features.keySet()) {
            res += "Dim:" + dim + "--";
            for (String val : features.get(dim).keySet())
                res += val + ":::" + features.get(dim).get(val) + "---";
            res += "\n";
        }
        return res;
    }

    //TODO: get a better location for this method
    public static svm_parameter getDefaultSVMParam() {
        svm_parameter param = new svm_parameter();
        // default values
        param.svm_type = svm_parameter.C_SVC;
        param.kernel_type = svm_parameter.RBF;
        param.degree = 3;
        param.gamma = 0; // 1/num_features
        param.coef0 = 0;
        param.nu = 0.5;
        param.cache_size = 100;
        param.C = 1;
        param.eps = 1e-3;
        param.p = 0.1;
        param.shrinking = 1;
        param.nr_weight = 0;
        param.weight_label = new int[0];
        param.weight = new double[0];
        return param;
    }

    public static void main(String[] args) {
        try {
            String baseDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user";
            Archive archive = SimpleSessions.readArchiveIfPresent(baseDir);
            List<Document> docs = archive.getAllDocs();
            for(Document doc: docs)
                System.err.println(doc.getUniqueId());
            System.err.println("Num docs: "+docs.size());
        }catch(IOException e){
            e.printStackTrace();
        }
//        try {
//            String mwl = System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator;
//            String modelFile = mwl + SequenceModel.modelFileName;
//            SequenceModel nerModel;
//            if(new File(modelFile).exists()) {
//                System.err.println("Loading model ... ");
//                nerModel = SequenceModel.loadModel(new File(modelFile));
//            }else{
//                System.err.println("Building model...");
//                nerModel = SequenceModel.train();
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}
