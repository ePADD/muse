package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.Config;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;

import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.SimpleSessions;
import libsvm.svm_parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;

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
    public Map<String, Map<String, Map<Short, Pair<Integer, Integer>>>> features = new LinkedHashMap<String, Map<String, Map<Short, Pair<Integer, Integer>>>>();
    //contains number of times a CIC pattern is seen (once per doc), also considers quoted text which may reflect wrong count
    //This can get quite depending on the archive and is not a scalable solution

    //this data-structure is only used for Segmentation which itself is not employed anywhere
    //public Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
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
                features.put(dim, new LinkedHashMap<String, Map<Short, Pair<Integer, Integer>>>());
            Map<String, Map<Short, Pair<Integer, Integer>>> hm = features.get(dim);
            if (wfeatures.get(dim) != null)
                for (String val : wfeatures.get(dim)) {
                    if (!hm.containsKey(val)) {
                        hm.put(val, new LinkedHashMap<Short, Pair<Integer, Integer>>());
                        for (Short at : allTypes)
                            hm.get(val).put(at, new Pair<Integer, Integer>(0, 0));
                    }
                    Pair<Integer, Integer> p = hm.get(val).get(iType);
                    String[] allowT = aTypes.get(iType);
                    for (String at : allowT)
                        if (type.contains(at)) {
                            p.first++;
                            break;
                        }
                    p.second++;
                    hm.get(val).put(iType, p);
                }
            features.put(dim, hm);
        }
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
                Pair<Integer, Integer> freqs = features.get("word").get(word).get(iType);
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

    //clean address book names based on gazette frequencies on DBpedia.
    static Map<String, Pair<String, Double>> scoreAB(Map<String, String> abNames, Map<String, String> dbpedia) {
        long timeToComputeFeatures = 0, timeOther = 0;
        long tms = System.currentTimeMillis();
        Short iType = FeatureDictionary.PERSON;
        log.info("Analysing gazettes");
        FeatureDictionary wfs = new FeatureDictionary(new FeatureGenerator[]{new WordSurfaceFeature()});
        int gi = 0, gs = dbpedia.size();
        for (String str : dbpedia.keySet()) {
            timeOther += System.currentTimeMillis() - tms;
            tms = System.currentTimeMillis();

            //the type supplied to WordFeatures should not matter, at least for filtering
            wfs.add(new WordSurfaceFeature().createFeatures(str, iType), dbpedia.get(str), iType);
            timeToComputeFeatures += System.currentTimeMillis() - tms;
            if ((++gi) % 10000 == 0) {
                log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs) + "%");
                log.info("Time spent in computing features: " + timeToComputeFeatures + " total time spent: " + (timeOther + timeToComputeFeatures));
            }
        }

        Map<String, Pair<String, Double>> scoredAB = new LinkedHashMap<String, Pair<String, Double>>();
        for (String name : abNames.keySet()) {
            double val = wfs.getFeatureValue(name, "words", iType);
            scoredAB.put(name, new Pair<>(abNames.get(name), val));
        }
        return scoredAB;
    }

    public static Map<String, String> cleanAB(Map<String, String> abNames, Map<String, String> dbpedia) {
        if (abNames == null)
            return abNames;

        if (dbpedia == null || dbpedia.size() < 1)
            dbpedia = EmailUtils.readDBpedia();

        Map<String, Pair<String, Double>> scoredAddressBook = scoreAB(abNames, dbpedia);

        Map<String, String> cleanAB = new LinkedHashMap<>();
        for (String entry : scoredAddressBook.keySet()) {
            Pair<String, Double> p = scoredAddressBook.get(entry);
            if (p.second > 0.5)
                cleanAB.put(entry, p.first);
        }
        return cleanAB;
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
    public double getConditional(String word, Character l, Short type){
        char[] labels = new char[]{'O', 'B', 'I', 'E', 'S'};
        Map<Short,Pair<Integer,Integer>> pairMap;
        double nw = getFreq(word);
        if(l==labels[4]) {
            pairMap = features.get("words").get(word);
        }
        else if(l == labels[1]){
            pairMap = features.get("words").get(word+"*");
        }
        else if(l == labels[2]){
            pairMap = features.get("words").get("*"+word+"*");
        }
        else if(l == labels[3])
            pairMap = features.get("words").get("*"+word);
        else {
            int f = 0;
            String[] patts = new String[]{word, word+"*","*"+word+"*","*"+word};
            for (String patt: patts) {
                pairMap = features.get("words").get(patt);
                if(pairMap != null && pairMap.get(FeatureDictionary.ORGANISATION) != null)
                    f += pairMap.get(type).second-pairMap.get(type).first;
            }
            return (double)(f+1)/nw;
        }
        Pair<Integer,Integer> p;
        if(pairMap == null||pairMap.get(FeatureDictionary.ORGANISATION) == null)
            p = new Pair<>(0,0);
        else
            p = pairMap.get(type);
        return Math.log(p.getFirst()+1) - Math.log(nw);
    }

    //returns smoothed frequency
    public Integer getFreq(String word){
        //some dummy type;
        Short type = FeatureDictionary.PERSON;
        String[] patts = new String[]{word,word+"*","*"+word+"*","*"+word};
        int n = 0;
        for(String patt: patts){
            Map<Short,Pair<Integer,Integer>> pairMap;
            pairMap = features.get("words").get(patt);
            Pair<Integer,Integer> p;
            if(pairMap == null||pairMap.get(FeatureDictionary.ORGANISATION) == null)
                p = new Pair<>(0,0);
            else
                p = pairMap.get(type);
            n += p.getSecond();
        }
        //+5 for smoothing
        return (n+5);
    }

    //P(x)
    public double getMarginal(String word){
        int n = getFreq(word);
        return Math.log(n)-Math.log(numPatts + numPattsWithDuplicates);
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
        for(String word: words) {
            String patt = null;
            if (words.length == 1)
                patt = word;
            else if (wi == 0)
                patt = word + "*";
            else if (wi == words.length - 1)
                patt = "*" + word;
            else if (wi < (words.length - 1))
                patt = "*" + word + "*";
            Map<Short, Pair<Integer, Integer>> pairMap = features.get("words").get(patt);
            if (pairMap != null && pairMap.get(FeatureDictionary.ORGANISATION) != null)
                numHits++;
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
            String userDir = "/Users/vihari/epadd-appraisal/user";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            Map<String, String> abNames = EmailUtils.getNames(archive.addressBook.allContacts());
            Map<String, String> dbpedia = EmailUtils.readDBpedia();
            Map<String, String> gazzs = new LinkedHashMap<>();
            gazzs.putAll(abNames);
            gazzs.putAll(dbpedia);
            System.err.println("AB: " + abNames.size() + "\nDBpedia: " + dbpedia.size() + "\nAll:" + gazzs.size());
            FeatureGenerator[] fgs = new FeatureGenerator[]{new WordSurfaceFeature()};
            FeatureDictionary dictionary = new FeatureDictionary(gazzs, fgs);
            Map<String, Map<String, Map<Short, Pair<Integer, Integer>>>> features = dictionary.features;
            for (String str1 : features.keySet())
                for (String str2 : features.get(str1).keySet()) {
                    for (Short s3 : features.get(str1).get(str2).keySet()) {
                        System.err.print(str1 + " : " + str2 + " : " + s3 + " : " + features.get(str1).get(str2).get(s3) + ", ");
                    }
                    System.err.println();
                }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
