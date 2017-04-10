package edu.stanford.muse.ner.model;

import edu.stanford.muse.ner.featuregen.FeatureUtils;
import edu.stanford.muse.util.DBpediaUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Created by vihari on 09/04/17.
 *
 * A training helper function for better organization, won't be serialized
 */
abstract class RuleInducer {
    protected static short UNKNOWN_TYPE = -10;

    public Map<String, MU> getMixtures() {
        return mixtures;
    }

    public Map<String, String> getGazettes() {
        return gazettes;
    }

    Map<String, MU> mixtures;
    Map<String, Map<String, Float>> muPriors;
    Map<String, String> gazettes;

    static Log log = LogFactory.getLog(SequenceModel.class);
    static Random rand = new Random(1);

    RuleInducer(Map<String, String> gazettes, Map<String, Map<String, Float>> tokenPriors) {
        this.mixtures = new LinkedHashMap<>();
        this.muPriors = new LinkedHashMap<>();
        SequenceModel.log.info("Initializing the model with gazettes");
        SequenceModel.log.info(Util.getMemoryStats());
        addGazz(gazettes, tokenPriors);
        SequenceModel.log.info("Starting EM on gazettes");
        SequenceModel.log.info(Util.getMemoryStats());
    }

    //Input is a token and returns the best type assignment for token
    private static List<String> getType(String token, Map<String,MU> mixtures) {
        List<String> types = new ArrayList<>();
        MU mu = mixtures.get(token);
        if (mu == null) {
            //log.warn("Token: "+token+" not initialised!!");
            types.add("T:" + UNKNOWN_TYPE);
            types.add("Wc:" + FeatureGeneratorUtil.tokenFeature(token));
        }
        else {
            Short[] allTypes = NEType.getAllTypeCodes();
            Short bestType = allTypes[rand.nextInt(allTypes.length)];
            double bv = 0;

            //We don't consider OTHER as even a type
            for (Short type : allTypes) {
                if (!type.equals(NEType.Type.OTHER.getCode())) {
                    double val = mu.getLikelihoodWithType(type);
                    if (val > bv) {
                        bv = val;
                        bestType = type;
                    }
                }
            }
            if(mu.numSeen>10)
                types.add("Tk:" + token);
            types.add("T:" + bestType);
        }
        return types;
    }

    /**
     * Generalizes mixtures by replacing literal labels with its type such as Paris Weekly => $CITY Weekly
     * {robert:[L:NULL,R:creeley,T:PERSON,SW:NULL,DICT:FALSE],creeley:[L:robert,R:NULL,T:PERSON,SW:NULL,DICT:FALSE]}
     * ==>
     * {robert:[L:NULL,R:PERSON,T:PERSON,SW:NULL,DICT:FALSE],creeley:[L:PERSON,R:NULL,T:PERSON,SW:NULL,DICT:FALSE]}
     * */
    static Map<String,List<String>> typeFeatures(Map<String, List<String>> features, Map<String, MU> mixtures){
        Map<String, List<String>> nwfs = new LinkedHashMap<>();
        features.keySet().forEach(k->{
            List<String> fs = new ArrayList<>();
            features.get(k).forEach(f->{
                if(f.startsWith("L:") && !f.equals("L:NULL"))
                    getType(f.substring(2), mixtures).stream().forEach(t->fs.add("L:"+t));
                else if(f.startsWith("R:") && !f.equals("R:NULL"))
                    getType(f.substring(2), mixtures).stream().forEach(t->fs.add("R:"+t));
                else
                    fs.add(f);
            });
            nwfs.put(k, fs);
        });
        return nwfs;
    }

    //initialize the mixtures
    private void addGazz(Map<String, String> gazettes, Map<String, Map<String, Float>> tokenPriors) {
        long start_time = System.currentTimeMillis();
        long timeToComputeFeatures = 0, tms;
        SequenceModel.log.info("Analysing gazettes");

        int g = 0, nume = 0;
        final int gs = gazettes.size();
        int gi = 0;
        SequenceModel.log.info(Util.getMemoryStats());
        //The number of times a word appeared in a phrase of certain type
        Map<String, Map<Short, Integer>> words = new LinkedHashMap<>();
        SequenceModel.log.info("Done loading DBpedia");
        SequenceModel.log.info(Util.getMemoryStats());
        Map<String, Integer> wordFreqs = new LinkedHashMap<>();

        for (String str : gazettes.keySet()) {
            tms = System.currentTimeMillis();

            String entityType = gazettes.get(str);
            Short ct = NEType.parseDBpediaType(entityType).getCode();
            if (DBpediaUtils.ignoreDBpediaTypes.contains(entityType)) {
                continue;
            }

            String[] patts = FeatureUtils.getPatts(str);
            for (String patt : patts) {
                if (!words.containsKey(patt))
                    words.put(patt, new LinkedHashMap<>());
                words.get(patt).put(ct, words.get(patt).getOrDefault(ct, 0) + 1);

                wordFreqs.put(patt, wordFreqs.getOrDefault(patt, 0) + 1);
            }

            timeToComputeFeatures += System.currentTimeMillis() - tms;

            if ((++gi) % 10000 == 0) {
                SequenceModel.log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs) + "% in gazette: " + g);
                SequenceModel.log.info("Time spent in computing mixtures: " + timeToComputeFeatures);
            }
            nume++;
        }
        SequenceModel.log.info("Done analyzing gazettes for frequencies");
        SequenceModel.log.info(Util.getMemoryStats());

        /*
        * Here is the histogram of frequencies of words from the 2014 dump of DBpedia
        * To read -- there are 861K words that are seen just once.
        * By ignoring words that are only seen once or twice we can reduce the number of mixtures by a factor of ~ 10
        * PAIR<1 -- 861698>
        * PAIR<2 -- 146458>
        * PAIR<3 -- 60264>
        * PAIR<4 -- 32683>
        * PAIR<5 -- 21006>
        * PAIR<6 -- 14361>
        * PAIR<7 -- 10512>
        * PAIR<8 -- 7865>
        * PAIR<9 -- 6480>
        * PAIR<10 -- 5327>
        *
        * Also, single character words, words with numbers (like jos%c3%a9), numbers (like 2008, 2014), empty tokens are ignored
        */
        log.info("Considered " + nume + " entities in " + gazettes.size() + " total entities");
        log.info("Done analysing gazettes in: " + (System.currentTimeMillis() - start_time));
        log.info("Initialising MUs");

        int initAlpha = 0;
        int wi = 0, ws = words.size();
        int numIgnored = 0, numConsidered = 0;
        for (String str : words.keySet()) {
            float wordFreq = wordFreqs.get(str);
            if (wordFreq < 3 || str.length() <= 1) {
                numIgnored++;
                continue;
            }
            boolean hasNumber = false;
            for (char c : str.toCharArray())
                if (Character.isDigit(c)) {
                    hasNumber = true;
                    numIgnored++;
                    break;
                }
            if (hasNumber)
                continue;

            numConsidered++;
            if (mixtures.containsKey(str))
                continue;
            Map<Short, Pair<Float, Float>> priors = new LinkedHashMap<>();
            for (Short type : NEType.getAllTypeCodes()) {
                if (words.get(str).containsKey(type))
                    priors.put(type, new Pair<>((float) words.get(str).get(type), wordFreq));
                else
                    priors.put(type, new Pair<>(0f, wordFreq));
            }

            if (SequenceModel.DEBUG) {
                String ds = "";
                for (Short t : priors.keySet())
                    ds += t + "<" + priors.get(t).first + "," + priors.get(t).second + "> ";
                SequenceModel.log.info("Initialising: " + str + " with " + ds);
            }
            Map<String, Float> alpha = new LinkedHashMap<>();
            if (str.length() > 2 && tokenPriors.containsKey(str)) {
                Map<String, Float> tps = tokenPriors.get(str);
                for (String gt : tps.keySet()) {
                    //Music bands especially are noisy
                    if (gt != null && !(DBpediaUtils.ignoreDBpediaTypes.contains(gt) || gt.equals("Agent"))) {
                        NEType.Type type = NEType.parseDBpediaType(gt);
                        if (type == null) type = NEType.Type.OTHER;
                        //all the albums, films etc.
                        if (type == NEType.Type.OTHER && gt.endsWith("|Work"))
                            continue;
                        String[] features = new String[]{"T:" + type.getCode(), "L:NULL", "R:NULL", "SW:NULL"};
                        for (String f : features) {
                            alpha.put(f, alpha.getOrDefault(f, 0f) + tps.get(gt));
                        }
                    }
                }
            }
            if (alpha.size() > 0)
                initAlpha++;
            //initializing the mixture with this world knowledge can help the mixture assign itself the right types and move in right direction
            mixtures.put(str, new MU(str, alpha));
            muPriors.put(str, alpha);
            if (wi++ % 1000 == 0) {
                log.info("Done: " + wi + "/" + ws);
                if (wi % 10000 == 0)
                    log.info(Util.getMemoryStats());
            }
        }
        log.info("Considered: " + numConsidered + " mixtures and ignored " + numIgnored);
        log.info("Initialised alpha for " + initAlpha + "/" + ws + " entries.");

        this.gazettes = gazettes;
    }

    Map<String, List<String>> genFeatures(String phrase, short type) {
        Map<String, List<String>> features = FeatureUtils.generateFeatures2(phrase, type);
        return typeFeatures(features, mixtures);
    }

    protected static double getPrior(MU mu, Map<String,MU> mixtures){
        return mu.getNumSeenEffective()/mixtures.size();
    }

    //an approximate measure for sigma(P(x;theta)) over all the observations
    double getIncompleteDataLogLikelihood() {
        return gazettes.entrySet().stream()
                //.parallel()
                .filter(e -> rand.nextInt(10) == 1)
                .mapToDouble(e -> {
                    String phrase = e.getKey();
                    short type = NEType.parseDBpediaType(e.getValue()).getCode();
                    Map<String, List<String>> midFeatures = genFeatures(phrase, type);
                    double llv = midFeatures.entrySet().stream().mapToDouble(mf -> {
                        MU mu = mixtures.get(mf.getKey());
                        if (mu != null) {
                            return mu.getLikelihood(mf.getValue()) * getPrior(mu, mixtures);
                        }
                        return 0;
                    }).sum();
                    if (llv <= 0)
                        return 0;
                    else
                        return Math.log(llv);
                }).average().orElse(0);
    }

    abstract void learn();
}
