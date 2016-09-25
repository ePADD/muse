package edu.stanford.muse.ner.model;

import edu.stanford.muse.Config;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.featuregen.FeatureUtils;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.ner.tokenizer.POSTokenizer;
import edu.stanford.muse.util.*;
import opennlp.tools.formats.Conll03NameSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by vihari on 07/09/15.
 * This class implements NER task with a Bernoulli Mixture model.
 * Templates or Binomial mixtures are learned over any list of entities without any supervision required.
 * An EM algorithm is used to estimate the params. The implementation can handle training size of order 100K.
 * It is sometimes desired to train over a much larger training files.
 * TODO: Consider implementing an online EM based param estimation -- see http://cs.stanford.edu/~pliang/papers/online-naacl2009.pdf
 * It is beneficial to include Address-book in training. Names can have an uncommon first and last name --
 * for example a model trained on one-fifth of DBPedia instance types, that is 300K entries assigns 3E-7 score to {Sudheendra Hangal, PERSON}, which is understandable since the DBpedia list contains only one entry with Sudheendra
 */
public class SequenceModel implements NERModel, Serializable {
    public static String modelFileName = "SeqModel.ser.gz";
    private static final long serialVersionUID = 1L;
    static Log log = LogFactory.getLog(SequenceModel.class);
    //public static final int MIN_NAME_LENGTH = 3, MAX_NAME_LENGTH = 100;
    public static FileWriter fdw = null;
    public static CICTokenizer tokenizer = new CICTokenizer();
    public Map<String, String> dbpedia;

    static boolean DEBUG = false;
    protected static short UNKNOWN_TYPE = -10;

    static Random rand = new Random(1);

    //mixtures of the BMM model
    public Map<String, MU> mixtures = new LinkedHashMap<>();
    //Keep the ref. to the gazette lists it is trained on so that we can lookup these when extracting entities.
    public Map<String,String> gazettes;

    public SequenceModel(Map<String,MU> mixtures, Map<String,String> gazettes) {
        this.mixtures = mixtures;
        this.gazettes = gazettes;
    }

    //A training helper function for better organization, won't be serialized
    static class Trainer {
        Map<String, MU> mixtures;
        Map<String,String> gazettes;

        public static List<String> ignoreDBpediaTypes = new ArrayList<>();
        static{
            //Do not expect that these ignore types will get rid of person names with any stop word in it.
            //Consider this, the type dist. of person-like types with the stop word _of_ is
            //10005 Person|Agent
            //4765 BritishRoyalty|Royalty|Person|Agent
            //2628 Noble|Person|Agent
            //1150 Saint|Cleric|Person|Agent
            //669 Monarch|Person|Agent
            //668 OfficeHolder|Person|Agent
            //627 ChristianBishop|Cleric|Person|Agent
            //525 MilitaryPerson|Person|Agent
            //249 SportsTeamMember|OrganisationMember|Person|Agent
            //247 SoapCharacter|FictionalCharacter|Person|Agent
            //158 FictionalCharacter|Person|Agent
            //114 Pope|Cleric|Person|Agent
            ignoreDBpediaTypes = Arrays.asList(
                    "RecordLabel|Company|Organisation",
                    "Band|Organisation",
                    "Band|Group|Organisation",
                    //Tokyo appears in 94 Album|MusicalWork|Work, 58 Film|Work, 57 City|Settlement|PopulatedPlace|Place
                    //London appears in 192 Album|MusicalWork|Work, 123 Settlement|PopulatedPlace|Place
                    //Pair in 130 Film|Work, 109 Album|MusicalWork|Work
                    //Can you believe this?!
                    "Album|MusicalWork|Work",
                    "Film|Work",
                    //This type is too noisy and contain titles like
                    //Cincinatti Kids, FA_Youth_Cup_Finals, The Strongest (and other such team names)
                    "OrganisationMember|Person",
                    "PersonFunction",
                    "GivenName",
                    "Royalty|Person",
                    //the following type has entities like "Cox_Broadcasting_Corp._v._Cohn", that may assign wrong type to tokens like corp., co., ltd.
                    "SupremeCourtOfTheUnitedStatesCase|LegalCase|Case|UnitOfWork",
                    //should be careful about Agent type, though it contains personal names it can also contain many non-personal entities
                    "ComicsCharacter|FictionalCharacter|Person"
            );
        }

        public Trainer(Map<String, String> gazettes, Map<String, Map<String, Float>> tokenPriors, int iter) {
            this.mixtures = new LinkedHashMap<>();
            log.info("Initializing the model with gazettes");
            log.info(Util.getMemoryStats());
            addGazz(gazettes, tokenPriors);
            log.info("Starting EM on gazettes");
            log.info(Util.getMemoryStats());
            EM(gazettes, iter);
        }

        public SequenceModel getModel(){
            return new SequenceModel(mixtures, gazettes);
        }

        //initialize the mixtures
        private void addGazz(Map<String, String> gazettes, Map<String, Map<String, Float>> tokenPriors) {
            long start_time = System.currentTimeMillis();
            long timeToComputeFeatures = 0, tms;
            log.info("Analysing gazettes");

            int g = 0, nume = 0;
            final int gs = gazettes.size();
            int gi = 0;
            log.info(Util.getMemoryStats());
            //The number of times a word appeared in a phrase of certain type
            Map<String, Map<Short, Integer>> words = new LinkedHashMap<>();
            log.info("Done loading DBpedia");
            log.info(Util.getMemoryStats());
            Map<String, Integer> wordFreqs = new LinkedHashMap<>();

            for (String str : gazettes.keySet()) {
                tms = System.currentTimeMillis();

                String entityType = gazettes.get(str);
                Short ct = NEType.parseDBpediaType(entityType).getCode();
                if (ignoreDBpediaTypes.contains(entityType)) {
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
                    log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs) + "% in gazette: " + g);
                    log.info("Time spent in computing mixtures: " + timeToComputeFeatures);
                }
                nume++;
            }
            log.info("Done analyzing gazettes for frequencies");
            log.info(Util.getMemoryStats());

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

                if (DEBUG) {
                    String ds = "";
                    for (Short t : priors.keySet())
                        ds += t + "<" + priors.get(t).first + "," + priors.get(t).second + "> ";
                    log.info("Initialising: " + str + " with " + ds);
                }
                Map<String, Float> alpha = new LinkedHashMap<>();
                float alpha_pi = 0;
                if (str.length() > 2 && tokenPriors.containsKey(str)) {
                    Map<String, Float> tps = tokenPriors.get(str);
                    for (String gt : tps.keySet()) {
                        //Music bands especially are noisy
                        if (gt != null && !(ignoreDBpediaTypes.contains(gt) || gt.equals("Agent"))) {
                            NEType.Type type = NEType.parseDBpediaType(gt);
                            if(type==null) type = NEType.Type.OTHER;
                            //all the albums, films etc.
                            if (type == NEType.Type.OTHER && gt.endsWith("|Work"))
                                continue;
                            String[] features = new String[]{"T:" + type.getCode(), "L:NULL", "R:NULL", "SW:NULL"};
                            for (String f : features) {
                                alpha.put(f, alpha.getOrDefault(f, 0f) + tps.get(gt));
                            }
                            alpha_pi += tps.get(gt);
                        }
                    }
                }
                if (alpha.size() > 0)
                    initAlpha++;
                //TODO: clean the MU initialize interface
                mixtures.put(str, MU.initialize(str, new LinkedHashMap<>(), alpha, alpha_pi));
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

        //just cleans up trailing numbers in the string
        private static String cleanRoad(String title){
            String[] words = title.split(" ");
            String lw = words[words.length-1];
            String ct = "";
            boolean hasNumber = false;
            for(Character c: lw.toCharArray())
                if(c>='0' && c<='9') {
                    hasNumber = true;
                    break;
                }
            if(words.length == 1 || !hasNumber)
                ct = title;
            else{
                for(int i=0;i<words.length-1;i++) {
                    ct += words[i];
                    if(i<words.length-2)
                        ct += " ";
                }
            }
            return ct;
        }

        /**
         * We put phrases through some filters in order to avoid very noisy types
         * These are the checks
         * 1. Remove stuff in the brackets to get rid of disambiguation stuff
         * 2. If the type is road, then we clean up trailing numbers
         * 3. If the type is settlement then the title is written as "Berkeley,_California" which actually mean Berkeley_(California); so cleaning these too
         * 4. We ignore certain noisy types. see ignoreDBpediaTypes
         * 5. Ignores any single word names
         * 6. If the type is person like but the phrase contains either "and" or "of", we filter this out.
         * returns either the cleaned phrase or null if the phrase cannot be cleaned.
         */
        private String filterTitle(String phrase, String type) {
            int cbi = phrase.indexOf(" (");
            if (cbi >= 0)
                phrase = phrase.substring(0, cbi);

            if (type.equals("Road|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place"))
                phrase = cleanRoad(phrase);

            //in places there are things like: Shaikh_Ibrahim,_Iraq
            int idx;
            if (type.endsWith("Settlement|PopulatedPlace|Place") && (idx = phrase.indexOf(", ")) >= 0)
                phrase = phrase.substring(0, idx);

            boolean allowed = true;
            for (String it : ignoreDBpediaTypes)
                if (type.contains(it)) {
                    allowed = false;
                    break;
                }
            if (!allowed)
                return null;

            //Do not consider single word names for training, the model has to be more complex than it is right now to handle these
            if (!phrase.contains(" "))
                return null;

            if ((type.endsWith("Person") || type.equals("Agent")) && (phrase.contains(" and ") || phrase.contains(" of ") || phrase.contains(" on ") || phrase.contains(" in ")))
                return null;
            return phrase;
        }

        private static void writeRulesToCache(Map<String,MU> features) {
            try {
                NEType.Type[] ats = NEType.getAllTypes();
                //make cache dir if it does not exist
                String cacheDir = System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator + "cache";
                if (!new File(cacheDir).exists()) {
                    boolean mkdir = new File(cacheDir).mkdir();
                    if (!mkdir)
                        log.warn("Cannot create cache dir. " + cacheDir);
                }
                for (NEType.Type et: ats) {
                    short type = et.getCode();
                    //FileWriter fw = new FileWriter(cacheDir + File.separator + "em.dump." + type + "." + i);
                    FileWriter ffw = new FileWriter(cacheDir + File.separator + et + ".txt");
                    Map<String, Double> sortScores = new LinkedHashMap<>();
                    Map<String, Double> scores = new LinkedHashMap<>();
                    for (String w : features.keySet()) {
                        MU mu = features.get(w);
                        double v1 = mu.getLikelihoodWithType(type) * (mu.numMixture / mu.numSeen);
                        double v = v1 * Math.log(mu.numSeen);
                        if (Double.isNaN(v)) {
                            sortScores.put(w, 0.0);
                            scores.put(w, 0.0);
                        } else {
                            sortScores.put(w, v);
                            scores.put(w, v1);
                        }
                    }
                    List<Pair<String, Double>> ps = Util.sortMapByValue(sortScores);
                    for (Pair<String, Double> p : ps) {
                        MU mu = features.get(p.getFirst());
                        Short maxT = -1;
                        double maxV = -1;
                        for (NEType.Type et1: ats) {
                            short t = et1.getCode();
                            double d = mu.getLikelihoodWithType(t);
                            if (d > maxV) {
                                maxT = t;
                                maxV = d;
                            }
                        }
                        //only if both the below conditions are satisfied, this template will ever be seen in action
                        if (maxT.equals(type) && scores.get(p.getFirst()) >= 0.001) {
                            ffw.write("Token: " + EmailUtils.uncanonicaliseName(p.getFirst()) + "\n");
                            ffw.write(mu.prettyPrint());
                            ffw.write("========================\n");
                        }
                    }
                    ffw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Map<String,List<String>> genFeatures(String phrase, short type){
            Map<String,List<String>> features = FeatureUtils.generateFeatures2(phrase,type);
            return typeFeatures(features, mixtures);
        }

        //an approximate measure for sigma(P(x;theta)) over all the observations
        double getIncompleteDataLogLikelihood(){
            double ll = gazettes.entrySet().stream()
                    .filter(e->rand.nextInt(10)==1)
                    .mapToDouble(e->{
                        String phrase = e.getKey();
                        short type = NEType.parseDBpediaType(e.getValue()).getCode();
                        Map<String,List<String>> midFeatures = genFeatures(phrase,type);
                        double llv = midFeatures.entrySet().stream().mapToDouble(mf->{
                            MU mu = mixtures.get(mf.getKey());
                            if(mu!=null){
                                return mu.getLikelihood(mf.getValue())*mu.getPrior();
                            }
                            return 0;
                        }).sum();
                        if(llv<=0)
                            return 0;
                        else
                            return Math.log(llv);
                    }).average().orElse(0);
            return ll;
        }

        //the argument alpha fraction is required only for naming of the dumped model size
        void EM(Map<String, String> gazettes, int iter) {
            log.info("Performing EM on: #" + mixtures.size() + " words");
            double ll = getIncompleteDataLogLikelihood();
            log.info("Start Data Log Likelihood: " + ll);
            System.out.println("Start Data Log Likelihood: " + ll);
            Map<String, MU> revisedMixtures = new LinkedHashMap<>();
            int N = gazettes.size();
            int wi;
            for (int i = 0; i < iter; i++) {
                wi = 0;
                for (Map.Entry e : gazettes.entrySet()) {
                    String phrase = (String) e.getKey();
                    String dbpediaType = (String) e.getValue();
                    phrase = filterTitle(phrase, dbpediaType);
                    if (phrase == null)
                        continue;

                    if (wi++ % 1000 == 0)
                        log.info("EM iteration: " + i + ", " + wi + "/" + N);

                    NEType.Type type = NEType.parseDBpediaType(dbpediaType);
                    float z = 0;
                    //responsibilities
                    Map<String, Float> gamma = new LinkedHashMap<>();
                    //Word (sort of mixture identity) -> Features
                    Map<String, List<String>> wfeatures = genFeatures(phrase, type.getCode());

                    if (type != NEType.Type.OTHER) {
                        for (String mi : wfeatures.keySet()) {
                            if (wfeatures.get(mi) == null) {
                                continue;
                            }
                            MU mu = mixtures.get(mi);
                            if (mu == null) {
                                //log.warn("!!FATAL!! MU null for: " + mi + ", " + mixtures.size());
                                continue;
                            }
                            double d = mu.getLikelihood(wfeatures.get(mi)) * mu.getPrior();
                            if (Double.isNaN(d))
                                log.warn("score for: " + mi + " " + wfeatures.get(mi) + " is NaN");
                            gamma.put(mi, (float) d);
                            z += d;
                        }
                        if (z == 0) {
                            if(DEBUG)
                                log.info("!!!FATAL!!! Skipping: " + phrase + " as none took responsibility");
                            continue;
                        }

                        for (String g : gamma.keySet()) {
                            gamma.put(g, gamma.get(g) / z);
                        }
                    } else {
                        for (String mi : wfeatures.keySet())
                            gamma.put(mi, 1.0f / wfeatures.size());
                    }

                    if (DEBUG) {
                        for (String mi : wfeatures.keySet()) {
                            log.info("MI:" + mi + ", " + gamma.get(mi) + ", " + wfeatures.get(mi));
                            log.info(mixtures.get(mi).toString());
                        }
                        log.info("EM iter: " + i + ", " + phrase + ", " + type + ", ct: " + type);
                        log.info("-----");
                    }

                    for (String g : gamma.keySet()) {
                        MU mu = mixtures.get(g);
                        //ignore this mixture if the effective number of times it is seen is less than 1 even with good evidence
                        if (mu == null)//|| (mu.numSeen > 0 && (mu.numMixture + mu.alpha_pi) < 1))
                            continue;
                        if (!revisedMixtures.containsKey(g))
                            revisedMixtures.put(g, new MU(g, mu.alpha, mu.alpha_pi));

                        if (Double.isNaN(gamma.get(g)))
                            log.error("Gamma NaN for MID: " + g);
                        if (DEBUG)
                            if (gamma.get(g) == 0)
                                log.warn("!! Resp: " + 0 + " for " + g + " in " + phrase + ", " + type);
                        //don't even update if the value is so low, that just adds meek affiliation with unrelated mixtures
                        if (gamma.get(g) > 1E-7)
                            revisedMixtures.get(g).add(gamma.get(g), wfeatures.get(g));

                    }
                }
                double change = 0;
                for (String mi : mixtures.keySet())
                    if (revisedMixtures.containsKey(mi))
                        change += revisedMixtures.get(mi).difference(mixtures.get(mi));
                change /= revisedMixtures.size();
                log.info("Iter: " + i + ", change: " + change);
                System.out.println("EM Iteration: " + i + ", change: " + change);
                //incomplete data log likelihood is better measure than just the change in parameters
                //i.e. P(X/\theta) = \sum\limits_{z}P(X,Z/\theta)
                mixtures = revisedMixtures;
                ll = getIncompleteDataLogLikelihood();
                log.info("Iter: " + i + ", Data Log Likelihood: " + ll);
                System.out.println("EM Iteration: " + i + ", Data Log Likelihood: " + ll);

                revisedMixtures = new LinkedHashMap<>();

                if(i==iter-1)
                    writeRulesToCache(mixtures);
            }
        }
    }

    //Input is a token and returns the best type assignment for token
    private static Short getType(String token, Map<String,MU> mixtures) {
        MU mu = mixtures.get(token);
        if (mu == null) {
            //log.warn("Token: "+token+" not initialised!!");
            return UNKNOWN_TYPE;
        }
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
        return bestType;
    }

    /**
     * Generalizes mixtures by replacing literal labels with its type such as Paris Weekly => $CITY Weekly
     * {robert:[L:NULL,R:creeley,T:PERSON,SW:NULL,DICT:FALSE],creeley:[L:robert,R:NULL,T:PERSON,SW:NULL,DICT:FALSE]}
     * ==>
     * {robert:[L:NULL,R:PERSON,T:PERSON,SW:NULL,DICT:FALSE],creeley:[L:PERSON,R:NULL,T:PERSON,SW:NULL,DICT:FALSE]}
     * */
    private static Map<String,List<String>> typeFeatures(Map<String,List<String>> features, Map<String,MU> mixtures){
        Map<String, List<String>> nwfs = new LinkedHashMap<>();
        features.keySet().forEach(k->{
            List<String> fs = new ArrayList<>();
            features.get(k).forEach(f->{
                if(f.startsWith("L:") && !f.equals("L:NULL"))
                    fs.add("L:"+getType(f.substring(2), mixtures));
                else if(f.startsWith("R:") && !f.equals("R:NULL"))
                    fs.add("R:"+getType(f.substring(2), mixtures));
                else
                    fs.add(f);
            });
            nwfs.put(k, fs);
        });
        return nwfs;
    }

    /**
     * Returns a probabilistic measure for
     * @param phrase to be a noun
     * @param nonNoun if true, then returns P(~noun/phrase) = 1-P(noun/phrase)
     * */
    private static double getNounLikelihood(String phrase, boolean nonNoun) {
        phrase = phrase.replaceAll("^\\W+|\\W+$", "");
        if (phrase.length() == 0) {
            if (nonNoun)
                return 1;
            else
                return 1.0 / Double.MAX_VALUE;
        }

        String[] tokens = phrase.split("\\s+");
        double p = 1;
        for (String token : tokens) {
            String orig = token;
            token = token.toLowerCase();
            List<String> noise = Arrays.asList("P.M", "P.M.", "A.M.", "today", "saturday", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december", "thanks");
            if (noise.contains(token)) {
                if (nonNoun)
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
                    if (nonNoun)
                        p *= 1;
                    else
                        p *= 1.0 / Double.MAX_VALUE;
                }
                if (orig.charAt(0) == token.charAt(0)) {
                    if (nonNoun)
                        p *= 1;
                    else
                        p *= 1.0 / Double.MAX_VALUE;
                } else {
                    if (nonNoun)
                        p *= 1.0 / Double.MAX_VALUE;
                    else
                        p *= 1.0;
                }
                continue;
            }
            double v = (double) pair.getFirst() / (double) pair.getSecond();
            if (v > 0.25) {
                if (nonNoun)
                    return 1.0 / Double.MAX_VALUE;
                else
                    return 1.0;
            } else {
                if (token.charAt(0) == orig.charAt(0)) {
                    if (nonNoun)
                        return 1;
                    else
                        return 1.0 / Double.MAX_VALUE;
                } else {
                    if (nonNoun)
                        return 1.0 / Double.MAX_VALUE;
                    else
                        return 1.0;
                }
            }
        }
        return p;
    }

    private String lookup(String phrase) {
        //if the phrase is from CIC Tokenizer, it won't start with an article
        //enough with the confusion between [New York Times, The New York Times], [Giant Magellan Telescope, The Giant Magellan Telescope]
        Set<String> vars = new LinkedHashSet<>();
        vars.add(phrase);
        vars.add("The "+phrase);
        String type;
        for(String var: vars) {
            type = gazettes.get(var.toLowerCase());
            if(type!=null) {
                log.debug("Found a match for: "+phrase+" -- "+type);
                return type;
            }
        }
        return null;
    }

    /**
     * Does sequence labeling of a phrase with type -- a dynamic programming approach
     * The complexity of this method has quadratic dependence on number of words in the phrase, hence should be careful with the length (a phrase with more than 7 words is rejected)
     * O(T*W^2) where W is number of tokens in the phrase and T is number of possible types
     * Note: This method only returns the entities from the best labeled sequence.
     * @param phrase - String that is to be sequence labelled, keep this short; The string will be rejected if it contains more than 9 words
     * @return all the entities along with their types and quality score found in the phrase
    */
    private Map<String, Pair<Short, Double>> seqLabel(String phrase) {
        Map<String, Pair<Short, Double>> segments = new LinkedHashMap<>();
        {
            String dbpediaType = lookup(phrase);
            NEType.Type type = NEType.parseDBpediaType(dbpediaType);

            if (dbpediaType != null && (phrase.contains(" ") || dbpediaType.endsWith("Country|PopulatedPlace|Place"))) {
                segments.put(phrase, new Pair<>(type.getCode(), 1.0));
                return segments;
            }
        }

        //This step of uncanonicalizing phrases helps merging things that have different capitalization and in lookup
        phrase = EmailUtils.uncanonicaliseName(phrase);

        if (phrase == null || phrase.length() == 0)
            return new LinkedHashMap<>();
        phrase = phrase.replaceAll("^\\W+|\\W+^", "");

        String[] tokens = phrase.split("\\s+");

        /**
         * In TW's sub-archive with ~65K entities scoring more than 0.001. The stats on frequency of #tokens per word is as follows
         * Freq  #tokens
         * 36520 2
         * 15062 3
         * 5900  4
         * 2645  5
         * 2190  1
         * 1301  6
         * 721   7
         * 18    8
         * 9     9
         * 2     10
         * 1     11
         * Total: 64,369 -- hence the cutoff below
         */
        if (tokens.length > 9) {
            return new LinkedHashMap<>();
        }
        //since there can be large number of types every token can take
        //we restrict the number of possible types we consider to top 5
        //see the complexity of the method
        Set<Short> cands = new LinkedHashSet<>();
        for (String token : tokens) {
            Map<Short, Double> candTypes = new LinkedHashMap<>();
            if (token.length() != 2 || token.charAt(1) != '.')
                token = token.replaceAll("^\\W+|\\W+$", "");
            token = token.toLowerCase();
            MU mu = mixtures.get(token);
            if (token.length() < 2 || mu == null || mu.numMixture == 0) {
                //System.out.println("Skipping: "+token+" due to mu "+mu==null);
                continue;
            }
            for (Short candType : NEType.getAllTypeCodes()) {
                double val = mu.getLikelihoodWithType(candType);
                candTypes.put(candType, candTypes.getOrDefault(candType, 0.0) + val);
            }
            List<Pair<Short, Double>> scands = Util.sortMapByValue(candTypes);
            int si = 0, MAX = 5;
            for (Pair<Short, Double> p : scands)
                if (si++ < MAX)
                    cands.add(p.getFirst());
        }
        //This is just a standard dynamic programming algo. used in HMMs, with the difference that
        //at every word we are checking for the every possible segment (or chunk)
        short NON_NOUN = -2;
        cands.add(NON_NOUN);
        Map<Integer, Triple<Double, Integer, Short>> tracks = new LinkedHashMap<>();
        Map<Integer,Integer> numSegmenation = new LinkedHashMap<>();
        //System.out.println("Cand types for: "+phrase+" "+cands);

        for (int ti = 0; ti < tokens.length; ti++) {
            double max = -1, bestValue = -1;
            int bi = -1;
            short bt = -10;
            for (short t : cands) {
                int tj = Math.max(ti - 6, 0);
                //don't allow multi word phrases with these types
                if (t == NON_NOUN || t == NEType.Type.OTHER.getCode())
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

                    if (NON_NOUN != t)
                        val *= getConditional(segment, t) * getNounLikelihood(segment, false);
                    else
                        val *= getNounLikelihood(segment, true);

                    double ov = val;
                    int numSeg = 1;
                    if(tj>0)
                        numSeg += numSegmenation.get(tj-1);
                    val = Math.pow(val, 1f/numSeg);
                    if (val > max) {
                        max = val;
                        bestValue = ov;
                        bi = tj - 1;
                        bt = t;
                    }
                    //System.out.println("Segment: "+segment+" type: "+t+" val: "+ov+" bi:"+bi+" bv: "+bestValue+" bt: "+bt);
                }
            }
            numSegmenation.put(ti, ((bi>=0)?numSegmenation.get(bi):0)+1);
            tracks.put(ti, new Triple<>(bestValue, bi, bt));
        }
        //System.out.println("Tracks: "+tracks);

        //the backtracking step
        int start = tokens.length - 1;
        while (true) {
            Triple<Double, Integer, Short> t = tracks.get(start);
            String seg = "";
            for (int ti = t.second + 1; ti <= start; ti++)
                seg += tokens[ti] + " ";
            seg = seg.substring(0,seg.length()-1);

            double val;
            if(NON_NOUN != t.getThird())
                val = getConditional(seg, t.getThird()) * getNounLikelihood(seg, false);
            else
                val = getNounLikelihood(seg, true);

            //if is a single word and a dictionary word or word with less than 4 chars and not acronym, then skip the segment
            if (seg.contains(" ") ||
                    (seg.length() >= 3 &&
                            (seg.length() >= 4 || FeatureGeneratorUtil.tokenFeature(seg).equals("ac")) &&
                            !DictUtils.commonDictWords.contains(EnglishDictionary.getSingular(seg.toLowerCase()))
                    ))
                segments.put(seg, new Pair<>(t.getThird(), val));

            start = t.second;
            if (t.second == -1)
                break;
        }
        return segments;
    }

    private double getConditional(String phrase, Short type) {
        Map<String, List<String>> tokenFeatures = FeatureUtils.generateFeatures2(phrase, type);
        tokenFeatures = typeFeatures(tokenFeatures, mixtures);
        String[] tokens = phrase.split("\\s+");
        if(FeatureUtils.sws.contains(tokens[0]) || FeatureUtils.sws.contains(tokens[tokens.length-1]))
            return 0;

        double sorg = 0;
        String dbpediaType = lookup(phrase);
        short ct = NEType.parseDBpediaType(dbpediaType).getCode();

        if(dbpediaType!=null && ct==type){
            if(dbpediaType.endsWith("Country|PopulatedPlace|Place"))
                return 1;
            else if (phrase.contains(" "))
                return 1;
        }

        for (String mid : tokenFeatures.keySet()) {
            Double d;
            MU mu = mixtures.get(mid);
            //Do not even consider the contribution from this mixture if it does not have a good affinity with this type
            if(mu!=null && mu.getLikelihoodWithType(type)<0.1)
                continue;

            int THRESH = 0;
            //imposing the frequency constraint on numMixture instead of numSeen can benefit in weeding out terms that are ambiguous,
            // which could have appeared many times, but does not appear to have common template
            //the check for "new" token is to reduce the noise coming from lowercase words starting with the word "new"
            if (mu != null &&
                    ((!type.equals(NEType.Type.PERSON.getCode()) && mu.numMixture>THRESH)||(type.equals(NEType.Type.PERSON.getCode()) && mu.numMixture>0)) &&
                    !mid.equals("new") && !mid.equals("first") && !mid.equals("open"))
                d = mu.getLikelihood(tokenFeatures.get(mid));
            else
                //a likelihood that assumes nothing
                d = MU.getMaxEntProb();
            double val = d;

            double freq = 0;
            if (d > 0) {
                if (mixtures.get(mid) != null)
                    freq = mixtures.get(mid).getPrior();
                val *= freq;
                //System.out.println("phrase:"+phrase+" type: "+type+" mid: "+mid+" val: "+val+":::mixtures: "+mixtures.get(mid));
            }
            //Should actually use logs here, not sure how to handle sums with logarithms
            sorg += val;
        }
        return sorg;
    }

    public Span[] find (String content){
        List<Span> spans = new ArrayList<>();

        String[] sents = NLPUtils.tokenizeSentence(content);
        for(String sent: sents) {
            List<Triple<String, Integer, Integer>> toks = tokenizer.tokenize(sent);
            for (Triple<String, Integer, Integer> t : toks) {
                //this should never happen
                if(t==null || t.first == null)
                    continue;

                Map<String,Pair<Short,Double>> entities = seqLabel(t.getFirst());
                for(String e: entities.keySet()){
                    Pair<Short,Double> p = entities.get(e);
                    //A new type is assigned to some words, which is of value -2
                    if(p.first<0)
                        continue;

                    if(!p.first.equals(NEType.Type.OTHER.getCode()) && p.second>0) {
                        Span chunk = new Span(e, t.second + t.first.indexOf(e), t.second + t.first.indexOf(e) + e.length());
                        chunk.setType(p.first, new Float(p.second));
                        spans.add(chunk);
                    }
                }
            }
        }
        return spans.toArray(new Span[spans.size()]);
    }

    //writes a .ser.gz file to the file passed in args.
    public synchronized void writeModel(File modelFile) throws IOException{
        FileOutputStream fos = new FileOutputStream(modelFile);
        GZIPOutputStream gos = new GZIPOutputStream(fos);
        ObjectOutputStream oos = new ObjectOutputStream(gos);
        oos.writeObject(this);
        oos.close();
    }

    public static synchronized SequenceModel loadModel(String modelPath) throws IOException{
        ObjectInputStream ois;
        try {
            //the buffer size can be much higher than default 512 for GZIPInputStream
            ois = new ObjectInputStream(new GZIPInputStream(Config.getResourceAsStream(modelPath)));
            SequenceModel model = (SequenceModel) ois.readObject();
            ois.close();
            return model;
        } catch (Exception e) {
            Util.print_exception("Exception while trying to load model from: " + modelPath, e, log);
            return null;
        }
    }

    //samples [fraction] fraction of entries from dictionary supplied and splices the supplied dict
    private static Pair<Map<String,String>,Map<String,String>> split(Map<String,String> dict, float fraction){
        Map<String,String> dict1 = new LinkedHashMap<>(), dict2 = new LinkedHashMap<>();
        Random rand = new Random();
        for(String str: dict.keySet()){
            if(rand.nextFloat()<fraction){
                dict1.put(str, dict.get(str));
            }else{
                dict2.put(str, dict.get(str));
            }
        }
        System.err.println("Sliced " + dict.size() + " entries into " + dict1.size() + " and " + dict2.size());
        return new Pair<>(dict1, dict2);
    }

    public static void testDBpedia(NERModel nerModel){
        //when testing remember to change
        //1. lookup method, disable the lookup
        System.err.println("DBpedia scoring check starts");
        String twl = System.getProperty("user.home")+File.separator+"epadd-settings"+File.separator+"SeqModel-test.en.txt.bz2";
        //clear the cache
        EmailUtils.dbpedia = null;
        Map<String,String> dbpedia = EmailUtils.readDBpedia(1, twl);
        //NOther == Not OTHER
        //number of things shown (NON-OTHER) and number of things that should be shown
        int ne = 0, neShown = 0, neShouldShown = 0;
        //number of entries assigned to wrong type and number missed because they are assigned OTHER
        int missAssigned=0, missSegmentation = 0, missNoEvidence = 0;
        int correct = 0;
        //these are the entries which are not completely tagged as OTHER by NER, but may have some segments that are not OTHER, hence visible
        double CUTOFF = 0;
        Map<Short,Map<Short,Integer>> confMat = new LinkedHashMap<>();
        Map<Short, Integer> freqs = new LinkedHashMap<>();
        String[] badSuffixTypes = new String[]{"MusicalWork|Work","Sport", "Film|Work", "Band|Group|Organisation", "Food",
                "EthnicGroup","RadioStation|Broadcaster|Organisation", "MeanOfTransportation", "TelevisionShow|Work",
                "Play|WrittenWork|Work","Language", "Book|WrittenWork|Work","Genre|TopicalConcept", "InformationAppliance|Device",
                "SportsTeam|Organisation", "Eukaryote|Species","Software|Work", "TelevisionEpisode|Work", "Comic|WrittenWork|Work",
                "Mayor", "Website|Work", "Cartoon|Work"
        };
        ol:
        for(String entry: dbpedia.keySet()){
            if(!entry.contains(" "))
                continue;
            String fullType = dbpedia.get(entry);
            Short type = NEType.parseDBpediaType(dbpedia.get(entry)).getCode();

            if(fullType.equals("Agent"))
                type = NEType.Type.PERSON.getCode();
            else
                for (String bst: badSuffixTypes)
                    if(fullType.endsWith(bst))
                        continue ol;

            entry = EmailUtils.uncanonicaliseName(entry);
            if(entry.length()>=15)
                continue;
            Span[] spans = nerModel.find(entry);
            Map<Short, Map<String,Float>> es = new LinkedHashMap<>();
            for(Span sp: Arrays.asList(spans))
                es.getOrDefault(sp.type,new LinkedHashMap<>()).put(sp.text,sp.typeScore);
            Map<Short, Map<String,Float>> temp = new LinkedHashMap<>();

            for(Short t: es.keySet()) {
                if(es.get(t).size()==0)
                    continue;
                temp.put(t, new LinkedHashMap<>());
                for (String str : es.get(t).keySet())
                    if(es.get(t).get(str)>CUTOFF)
                        temp.get(t).put(str, es.get(t).get(str));
            }
            es = temp;

            short assignedTo = type;
            boolean shown = false;
            //we should not bother about segmentation in the case of OTHER
            if(!(es.containsKey(NEType.Type.OTHER.getCode()) && es.size()==1)) {
                shown = true;
                boolean any;
                if (!type.equals(NEType.Type.OTHER.getCode()) && es.containsKey(type) && es.get(type).containsKey(entry))
                    correct++;
                else {
                    any = false;
                    boolean found = false;
                    assignedTo = -1;
                    for (Short t : es.keySet()) {
                        if (es.get(t).containsKey(entry)) {
                            found = true;
                            assignedTo = t;
                            break;
                        }
                        if (es.get(t).size() > 0)
                            any = true;
                    }
                    if (found) {
                        missAssigned++;
                        System.err.println("Wrong assignment miss\nExpected: " + entry + " - " + fullType + " found: " + assignedTo + "\n" + "--------");
                    } else if (any) {
                        System.err.println("Segmentation miss\nExpected: " + entry + " - " + fullType + "\n" + "--------");
                        missSegmentation++;
                    } else {
                        missNoEvidence++;
                        System.err.println("Not enough evidence for: " + entry + " - " + fullType);
                    }
                }
            }
            if(shown)
                neShown++;
            if(type!= NEType.Type.OTHER.getCode())
                neShouldShown++;


            if(ne++%100 == 0)
                System.err.println("Done testing on "+ne+" of "+dbpedia.size());
            if(!confMat.containsKey(type))
                confMat.put(type, new LinkedHashMap<>());
            if(!confMat.get(type).containsKey(assignedTo))
                confMat.get(type).put(assignedTo, 0);
            confMat.get(type).put(assignedTo, confMat.get(type).get(assignedTo)+1);

            if(!freqs.containsKey(type))
                freqs.put(type, 0);
            freqs.put(type, freqs.get(type)+1);
        }
        List<Short> allTypes = new ArrayList<>();
        for(Short type: confMat.keySet())
            allTypes.add(type);
        Collections.sort(allTypes);
        allTypes.add((short)-1);
        System.err.println("Tested on "+ne+" entries");
        System.err.println("------------------------");
        String ln = "  ";
        for(Short type: allTypes)
            ln += String.format("%5s",type);
        System.err.println(ln);
        for(Short t1: allTypes){
            ln = String.format("%2s",t1);
            for(Short t2: allTypes) {
                if(confMat.containsKey(t1) && confMat.get(t1).containsKey(t2) && freqs.containsKey(t1))
                    ln += String.format("%5s", new DecimalFormat("#.##").format((double)confMat.get(t1).get(t2)/freqs.get(t1)));//new DecimalFormat("#.##").format((double) confMat.get(t1).get(t2) / freqs.get(t1)));
                else
                    ln += String.format("%5s","-");
            }
            System.err.println(ln);
        }
        System.err.println("------------------------\n");
        double precision = (double)(correct)/(neShown);
        double recall = (double)correct/neShouldShown;
        //miss and misAssigned are number of things we are missing we are missing, but for different reasons, miss is due to segmentation problem, assignment to OTHER; misAssigned is due to wrong type assignment
        //visible = ne - number of entries that are assigned OTHER label and hence visible
        System.err.println("Missed #"+missAssigned+" due to improper assignment\n#"+missSegmentation+"due to improper segmentation\n" +
                "#"+missNoEvidence+" due to single word or no evidence");
        System.err.println("Precision: "+precision+"\nRecall: "+recall);
    }

    public static void test2(SequenceModel nerModel){
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

            Span[] spans = nerModel.find(content);
            for(Span sp: spans)
                System.out.println(sp);
            String[] check = new String[]{"California State Route 1", "New York Times", "Goethe Institute of Prague", "Venice high school students","Denver International Airport",
                    "New York International Airport", "Ramchandra Kripa, Mahishi Road"};
            for(String c: check) {
                System.err.println(c + ", " + nerModel.seqLabel(c));
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //TODO: Add to the project the code that produces this file
    //returns token -> {redirect (can be the same as token), page length of the page it redirects to}
    static Map<String,Map<String,Integer>> getTokenTypePriors(){
        Map<String,Map<String,Integer>> pageLengths = new LinkedHashMap<>();
        log.info("Parsing token types...");
        try{
            LineNumberReader lnr = new LineNumberReader(new InputStreamReader(Config.getResourceAsStream("TokenTypes.txt")));
            String line;
            while((line=lnr.readLine())!=null){
                String[] fields = line.split("\\t");
                if(fields.length!=4){
                    log.warn("Line --"+line+"-- has an unexpected pattern!");
                    continue;
                }
                int pageLen = Integer.parseInt(fields[3]);
                String redirect = fields[2];
                //if the page is not a redirect, then itself is the title
                if(fields[2] == null || fields[2].equals("null"))
                    redirect = fields[1];
                String lc = fields[0].toLowerCase();
                if(!pageLengths.containsKey(lc))
                    pageLengths.put(lc, new LinkedHashMap<>());
                pageLengths.get(lc).put(redirect, pageLen);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        return pageLengths;
    }

    //we are missing F.C's like F.C. La Valletta
    /**
     * Tested on 28th Jan. 2016 on what is believed to be the testa.dat file of original CONLL.
     * I procured this data-set from a prof's (UMass Prof., don't remember the name) home page where he provided the test files for a homework, guess who topped the assignment :)
     * (So, don't use this data to report results at any serious venue)
     * The results on multi-word names is as follows.
     * Note that the test only considered PERSON, LOCATION and ORG; Also, it does not distinguish between the types because the type assigned by Sequence Labeler is almost always right. And, importantly this will avoid any scuffle over the mapping from fine-grained type to the coarse types.
     *  -------------
     *  Found: 8861 -- Total: 7781 -- Correct: 6675
     *  Precision: 0.75330096
     *  Recall: 0.8578589
     *  F1: 0.80218726
     *  ------------
     * I went through 2691 sentences of which only 200 had any unrecognised entities and identified various sources of error.
     * The sources of missing names are as follows in decreasing order of their contribution (approximately), I have put some examples with the sources. The example phrases are recognized as one chunk with a type.
     * Obviously, this list is not exhaustive, USE IT WITH CAUTION!
     *  1. Bad segmentation -- which is minor for ePADD and depends on training data and principles.
     *     For example: "Overseas Development Minister <PERSON>Lynda Chalker</PERSON>",Czech <PERSON>Daniel Vacek</PERSON>, "Frenchman <PERSON>Cedric Pioline</PERSON>"
     *     "President <PERSON>Nelson Mandela</PERSON>","<BANK>Reserve Bank of India</BANK> Governor <PERSON>Chakravarty Rangarajan</PERSON>"
     *     "Third-seeded <PERSON>Wayne Ferreira</PERSON>",
     *     Hong Kong Newsroom -- we got only Hong Kong, <BANK>Hong Kong Interbank</BANK> Offered Rate, Privately-owned <BANK>Bank Duta</BANK>
     *     [SERIOUS]
     *  2. Bad training data -- since our training data (DBpedia instances) contain phrases like "of Romania" a lot
     *     Ex: <PERSON>Yayuk Basuki</PERSON> of Indonesia, <PERSON>Karim Alami</PERSON> of Morocc
     *     This is also leading to errors like when National Bank of Holand is segmented as National Bank
     *     [SERIOUS]
     *  3. Some unknown names, mostly personal -- we see very weird names in CONLL; Hopefully, we can avoid this problem in ePADD by considering the address book of the archive.
     *     Ex: NOVYE ATAGI, Hans-Otto Sieg, NS Kampfruf, Marie-Jose Perec, Billy Mayfair--Paul Goydos--Hidemichi Tanaki
     *     we miss many (almost all) names of the form "M. Dowman" because of uncommon or unknown last name.
     *  4. Bad segmentation due to limitations of CIC
     *     Ex: Hassan al-Turabi, National Democratic party, Department of Humanitarian affairs, Reserve bank of India, Saint of the Gutters, Queen of the South, Queen's Park
     *  5. Very Long entities -- we refrain from seq. labelling if the #tokens>7
     *     Ex: National Socialist German Workers ' Party Foreign Organisation
     *  6. We are missing OCEANs?!
     *     Ex: Atlantic Ocean, Indian Ocean
     *  7. Bad segments -- why are some segments starting with weird chars like '&'
     *     Ex: Goldman Sachs & Co Wertpapier GmbH -> {& Co Wertpapier GmbH, Goldman Sachs}
     *  8. We are missing Times of London?! We get nothing that contains "Newsroom" -- "Amsterdam Newsroom", "Hong Kong News Room"
     *     Why are we getting "Students of South Korea" instead of "South Korea"?
     *
     * 1/50th on only MWs
     * 13 Feb 13:24:54 BMMModel INFO  - -------------
     * 13 Feb 13:24:54 BMMModel INFO  - Found: 4238 -- Total: 4236 -- Correct: 3242 -- Missed due to wrong type: 358
     * 13 Feb 13:24:54 BMMModel INFO  - Precision: 0.7649835
     * 13 Feb 13:24:54 BMMModel INFO  - Recall: 0.7653447
     * 13 Feb 13:24:54 BMMModel INFO  - F1: 0.765164
     * 13 Feb 13:24:54 BMMModel INFO  - ------------
     *
     * Best performance on CONLL testa full, model trained on entire DBpedia.
     * 4 Feb 00:41:34 BMMModel INFO  - -------------
     * 14 Feb 00:41:34 BMMModel INFO  - Found: 6707 -- Total: 7219 -- Correct: 4988 -- Missed due to wrong type: 1150
     * 14 Feb 00:41:34 BMMModel INFO  - Precision: 0.7437006
     * 14 Feb 00:41:34 BMMModel INFO  - Recall: 0.69095445
     * 14 Feb 00:41:34 BMMModel INFO  - F1: 0.71635795
     * 14 Feb 00:41:34 BMMModel INFO  - ------------
     *
     * Best performance on testa with [ignore segmentation] and single word with CONLL data is
     * 25 Sep 13:27:03 SequenceModel INFO  - -------------
     * 25 Sep 13:27:03 SequenceModel INFO  - Found: 4117 -- Total: 4236 -- Correct: 3368 -- Missed due to wrong type: 266
     * 25 Sep 13:27:03 SequenceModel INFO  - Precision: 0.8180714
     * 25 Sep 13:27:03 SequenceModel INFO  - Recall: 0.7950897
     * 25 Sep 13:27:03 SequenceModel INFO  - F1: 0.80641687
     * 25 Sep 13:27:03 SequenceModel INFO  - ------------
     *
     * on testa, ignoring segmentation, any number of words and ignoring segmentation
     * 25 Sep 20:35:18 SequenceModel INFO  - -------------
     * 25 Sep 20:35:18 SequenceModel INFO  - Found: 5997 -- Total: 7219 -- Correct: 4764 -- Missed due to wrong type: 918
     * 25 Sep 20:35:18 SequenceModel INFO  - Precision: 0.7943972
     * 25 Sep 20:35:18 SequenceModel INFO  - Recall: 0.6599252
     * 25 Sep 20:35:18 SequenceModel INFO  - F1: 0.7209444
     * 25 Sep 20:35:18 SequenceModel INFO  - ------------
     *
     * on testa, *not* ignoring segmentation (exact match), any number of words
     * 25 Sep 17:23:14 SequenceModel INFO  - -------------
     * 25 Sep 17:23:14 SequenceModel INFO  - Found: 6006 -- Total: 7219 -- Correct: 4245 -- Missed due to wrong type: 605
     * 25 Sep 17:23:14 SequenceModel INFO  - Precision: 0.7067932
     * 25 Sep 17:23:14 SequenceModel INFO  - Recall: 0.5880316
     * 25 Sep 17:23:14 SequenceModel INFO  - F1: 0.6419659
     * 25 Sep 17:23:14 SequenceModel INFO  - ------------
     *
     * on testa, exact matches, multi-word names
     * 25 Sep 17:28:04 SequenceModel INFO  - -------------
     * 25 Sep 17:28:04 SequenceModel INFO  - Found: 4117 -- Total: 4236 -- Correct: 3096 -- Missed due to wrong type: 183
     * 25 Sep 17:28:04 SequenceModel INFO  - Precision: 0.7520039
     * 25 Sep 17:28:04 SequenceModel INFO  - Recall: 0.7308782
     * 25 Sep 17:28:04 SequenceModel INFO  - F1: 0.74129057
     * 25 Sep 17:28:04 SequenceModel INFO  - ------------
     *
     * With a model that is not trained on CONLL lists
     * On testa, ignoring segmentation, any number of words.
     * Sep 19:22:26 SequenceModel INFO  - -------------
     * 25 Sep 19:22:26 SequenceModel INFO  - Found: 6129 -- Total: 7219 -- Correct: 4725 -- Missed due to wrong type: 964
     * 25 Sep 19:22:26 SequenceModel INFO  - Precision: 0.7709251
     * 25 Sep 19:22:26 SequenceModel INFO  - Recall: 0.6545228
     * 25 Sep 19:22:26 SequenceModel INFO  - F1: 0.7079712
     * 25 Sep 19:22:26 SequenceModel INFO  - ------------
     * */
    public static void test(SequenceModel seqModel, boolean verbose) {
        try {
            InputStream in = new FileInputStream(new File(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "ner-benchmarks" + File.separator + "umasshw" + File.separator + "testaspacesep.txt"));
            //7==0111 PER, LOC, ORG
            Conll03NameSampleStream sampleStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, in, 7);
            Set<String> correct = new LinkedHashSet<>(), found = new LinkedHashSet<>(), real = new LinkedHashSet<>(), wrongType = new LinkedHashSet<>();
            Multimap<String, String> matchMap = ArrayListMultimap.create();
            Map<String, String> foundTypes = new LinkedHashMap<>(), benchmarkTypes = new LinkedHashMap<>();

            //only multi-word are considered
            boolean onlyMW = false;
            //use ignoreSegmentation=true only with onlyMW=true it is not tested otherwise
            boolean ignoreSegmentation = true;
            NameSample sample = sampleStream.read();
            CICTokenizer tokenizer = new CICTokenizer();
            while (sample != null) {
                String[] words = sample.getSentence();
                String sent = "";
                for (String s : words)
                    sent += s + " ";
                sent = sent.substring(0, sent.length() - 1);

                Map<String, String> names = new LinkedHashMap<>();
                opennlp.tools.util.Span[] nspans = sample.getNames();
                for (opennlp.tools.util.Span nspan : nspans) {
                    String n = "";
                    for (int si = nspan.getStart(); si < nspan.getEnd(); si++) {
                        if (si < words.length - 1 && words[si + 1].equals("'s"))
                            n += words[si];
                        else
                            n += words[si] + " ";
                    }
                    if (n.endsWith(" "))
                        n = n.substring(0, n.length() - 1);
                    if (!onlyMW || n.contains(" "))
                        names.put(n, nspan.getType());
                }
                Span[] chunks = seqModel.find(sent);
                Map<String, String> foundSample = new LinkedHashMap<>();
                if (chunks != null)
                    for (Span chunk : chunks) {
                        String text = chunk.text;
                        Short type = chunk.type;
                        if(type == NEType.Type.DISEASE.getCode() || type == NEType.Type.EVENT.getCode() || type == NEType.Type.AWARD.getCode())
                            continue;

                        Short coarseType = NEType.getCoarseType(type).getCode();
                        String typeText;
                        if (coarseType == NEType.Type.PERSON.getCode())
                            typeText = "person";
                        else if (coarseType == NEType.Type.PLACE.getCode())
                            typeText = "location";
                        else
                            typeText = "organization";
                        double s = chunk.typeScore;
                        if (s > 0 && (!onlyMW || text.contains(" ")))
                            foundSample.put(text, typeText);
                    }

                Set<String> foundNames = new LinkedHashSet<>();
                Map<String, String> localMatchMap = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : foundSample.entrySet()) {
                    foundTypes.put(entry.getKey(), entry.getValue());
                    boolean foundEntry = false;
                    String foundType = null;
                    for (String name : names.keySet()) {
                        String cname = EmailUtils.uncanonicaliseName(name).toLowerCase();
                        String ek = EmailUtils.uncanonicaliseName(entry.getKey()).toLowerCase();
                        if (cname.equals(ek) || (ignoreSegmentation && ((cname.startsWith(ek + " ") || cname.endsWith(" " + ek) || ek.startsWith(cname + " ") || ek.endsWith(" " + cname))))) {
                            foundEntry = true;
                            foundType = names.get(name);
                            matchMap.put(entry.getKey(), name);
                            localMatchMap.put(entry.getKey(), name);
                            break;
                        }
                    }

                    if (foundEntry) {
                        if (entry.getValue().equals(foundType)) {
                            foundNames.add(entry.getKey());
                            correct.add(entry.getKey());
                        } else {
                            wrongType.add(entry.getKey());
                        }
                    }
                }

                if (verbose) {
                    log.info("CIC tokens: " + tokenizer.tokenizeWithoutOffsets(sent));
                    log.info(chunks);
                    String fn = "Found names:";
                    for (String f : foundNames)
                        fn += f + "[" + foundSample.get(f) + "] with " + localMatchMap.get(f) + "--";
                    if (fn.endsWith("--"))
                        log.info(fn);

                    String extr = "Extra names: ";
                    for (String f : foundSample.keySet())
                        if (!localMatchMap.containsKey(f))
                            extr += f + "[" + foundSample.get(f) + "]--";
                    if (extr.endsWith("--"))
                        log.info(extr);
                    String miss = "Missing names: ";
                    for (String name : names.keySet())
                        if (!localMatchMap.values().contains(name))
                            miss += name + "[" + names.get(name) + "]--";
                    if (miss.endsWith("--"))
                        log.info(miss);

                    String misAssign = "Mis-assigned Types: ";
                    for (String f : foundSample.keySet())
                        if (matchMap.containsKey(f)) {
                            //this can happen since matchMap is a global var. and an entity that is tagged in one place is untagged in other
                            //if (names.get(matchMap.get(f)) == null)
                            //  log.warn("This is not expected: " + f + " in matchMap not found names -- " + names);
                            if (names.get(matchMap.get(f)) != null && !names.get(matchMap.get(f)).equals(foundSample.get(f)))
                                misAssign += f + "[" + foundSample.get(f) + "] Expected [" + names.get(matchMap.get(f)) + "]--";
                        }
                    if (misAssign.endsWith("--"))
                        log.info(misAssign);

                    log.info(sent + "\n------------------");
                }
                for (String name : names.keySet())
                    benchmarkTypes.put(name, names.get(name));

                real.addAll(names.keySet());
                found.addAll(foundSample.keySet());
                sample = sampleStream.read();
            }
            float prec = (float) correct.size() / (float) found.size();
            float recall = (float) correct.size() / (float) real.size();
            if (verbose) {
                log.info("----Correct names----");
                for (String str : correct)
                    log.info(str + " with " + new LinkedHashSet<>(matchMap.get(str)));
                log.info("----Missed names----");
                real.stream().filter(str -> !matchMap.values().contains(str)).forEach(log::info);
                log.info("---Extra names------");
                found.stream().filter(str -> !matchMap.keySet().contains(str)).forEach(log::info);

                log.info("---Assigned wrong type------");
                for (String str : wrongType) {
                    Set<String> bMatches = new LinkedHashSet<>(matchMap.get(str));
                    for (String bMatch : bMatches) {
                        String ft = foundTypes.get(str);
                        String bt = benchmarkTypes.get(bMatch);
                        if (!ft.equals(bt))
                            log.info(str + "[" + ft + "] expected " + bMatch + "[" + bt + "]");
                    }
                }
            }

            log.info("-------------");
            log.info("Found: " + found.size() + " -- Total: " + real.size() + " -- Correct: " + correct.size() + " -- Missed due to wrong type: " + (wrongType.size()));
            log.info("Precision: " + prec);
            log.info("Recall: " + recall);
            log.info("F1: " + (2 * prec * recall / (prec + recall)));
            log.info("------------");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void testParams(){
        float alphas[] = new float[]{1.0f/5};//new float[]{0, 1.0f/50, 1.0f/5, 1.0f/2, 1.0f, 5f};
        int emIters[] = new int[]{9};//new int[]{0,2,5,7,9};
        int numIter = 1;
        String expFolder = "experiment";
        String resultsFile = System.getProperty("user.home")+File.separator+"epadd-settings"+File.separator+"paramResults.txt";
        //flush the previous results
        try{new FileOutputStream(resultsFile);}catch(IOException e){e.printStackTrace();}
        String oldName = modelFileName;
        for(float alpha: alphas) {
            SequenceModel.modelFileName = "ALPHA_"+alpha+"-"+oldName;
            String modelFile = expFolder + File.separator + "Iter_" + emIters[emIters.length - 1] + SequenceModel.modelFileName;
            try {
                if (!new File(modelFile).exists()) {
                    PrintStream def = System.out;
                    System.setOut(new PrintStream(new FileOutputStream(resultsFile, true)));
                    System.out.println("------------------\n" +
                            "Alpha fraction: " + alpha + " -- # Iterations: " + numIter);
                    train(alpha, numIter);
                    System.setOut(def);
                }
                for (int emIter : emIters) {
                    modelFile = expFolder + File.separator + "Iter_" + emIter + "-" + SequenceModel.modelFileName;
                    SequenceModel seqModel = loadModel(modelFile);
                    PrintStream def = System.out;
                    System.setOut(new PrintStream(new FileOutputStream(resultsFile, true)));
                    System.out.println("------------------\n" +
                            "Alpha fraction: " + alpha + " -- Iteration: " + (emIter + 1));
                    test(seqModel, false);
                    System.setOut(def);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        modelFileName = oldName;
    }

    /**
     * A low level train interface for experimentation and extension over the default model.
     * Use this method for training the default model
     * Training data should be a list of phrases and their types, the type should follow DBpedia ontology; specifically http://downloads.dbpedia.org/2015-04/dbpedia_2015-04.nt.bz2
     * See epadd-settings/instance_types to understand the format better
     * It is possible to relax the ontology constraint by changing the aTypes and ignoreDBpediaTypes fields in FeatureDictionary appropriately
     * With tokenPriors it is possible to set initial beliefs, for example "Nokia" is a popular company; the first key in the map should be a single word token, the second map is the types and its affiliation for various types (DBpedia ontology again)
     * iter param is the number of EM iterations, any value >5 is observed to have no effect on performance with DBpedia as training data
     *  */
    public static SequenceModel train(Map<String,String> trainData, Map<String,Map<String,Float>> tokenPriors, int iter){
        log.info("Initializing trainer");
        log.info(Util.getMemoryStats());
        Trainer trainer = new Trainer(trainData, tokenPriors, iter);
        return trainer.getModel();
    }

    /**
     * Use this routine to read any entity list, the resource is expected to be a plain text file
     * the lines in the resource should be two fields separated by ' ', the first field should be the title and second it's type.
     * The type of the resource should follow the style of DBpedia types in our generated instance file, see aTypes field in FeatureDictionary for more info.
     * for example, a building type should be expanded to "Building|ArchitecturalStructure|Place"
     * The spaces in the title, ie. the first entry should be replaced by '_'
     */
    private static Map<String,String> readEntityList(String resourcePath) {
        Map<String,String> content = new LinkedHashMap<>();
          BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(Config.getResourceAsStream(resourcePath)));
        }
        //Not a big deal if one entity listing is missed
        catch(Exception e){
            Util.print_exception("Could not load resource:"+resourcePath, e, log);
            return content;
        }

        String line;
        try {
            while ((line = br.readLine()) != null){
                line = line.trim();
                String[] fs = line.split(" ");
                String title = fs[0];
                title = title.replaceAll("_"," ");
                content.put(title, fs[1]);
            }
        } catch(IOException e){
            log.warn("Could not open and read the resource from "+resourcePath, e);
        }
        log.info("Read "+content.size()+" entries from "+resourcePath);
        log.debug("The top 10 entries");
        int i=0;
        for(Map.Entry<String,String> e: content.entrySet()) {
            log.debug(e.getKey() + " -- " + e.getValue());
            if(i++>=10)
                break;
        }
        return content;
    }

    private static SequenceModel train(float alpha, int emIter){
        Map<String,String> tdata = EmailUtils.readDBpedia();
        //also include CONLL lists
//        String resources[] = Config.NER_RESOURCE_FILES;
//        for(String rsrc: resources) {
//            //DBpedia has a finer type, respect it.
//            Map<String,String> map = readEntityList(rsrc);
//            for(Map.Entry<String,String> e: map.entrySet())
//                    tdata.putIfAbsent(e.getKey(),e.getValue());
//        }

        //page lengths from wikipedia
        Map<String,Map<String,Integer>> pageLens = getTokenTypePriors();
        //getTokenPriors returns Map<String, Map<String,Integer>> where the first key is the single word DBpedia title and second keys are the titles it redirects to and its page length
        Map<String,Map<String,Float>> tokenPriors = new LinkedHashMap<>();
        //The Dir. prior related param alpha is empirically found to be performing at the value of 0.2f
        for(String tok: pageLens.keySet()) {
            Map<String,Float> tmp =  new LinkedHashMap<>();
            Map<String,Integer> tpls = pageLens.get(tok);
            for(String page: tpls.keySet()) {
                String type = tdata.get(page.toLowerCase());
                tmp.put(type, tpls.get(page)*alpha/1000f);
            }
            tokenPriors.put(tok, tmp);
        }
	    log.info("Initialized "+tokenPriors.size()+" token priors.");
        return train(tdata, tokenPriors, emIter);
    }

    /**
     * Trains a SequenceModel with default parameters*/
    public static SequenceModel trainSeqModel() {
        long st = System.currentTimeMillis();
        SequenceModel model = train(0.2f, 5);
        try {
            model.writeModel(new File(Config.SETTINGS_DIR+File.separator+modelFileName));
        } catch(IOException e){
            log.warn("Unable to write model to disk");
            e.printStackTrace();
        }
        long et = System.currentTimeMillis();
        log.info("Trained and dumped model in "+((et-st)/1000)+"s");
        return model;
    }

    static void loadAndTestNERModel(){
        System.out.println("Resource files...");
        Stream.of(Config.NER_RESOURCE_FILES).forEach(Object::toString);

        //Map<String,String> dbpedia = EmailUtils.readDBpedia(1.0/5);
        String modelFile = SequenceModel.modelFileName;
        if (fdw == null) {
            try {
                fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator + "cache" + File.separator + "mixtures.dump"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.err.println("Loading model...");
        SequenceModel nerModel = null;
        log.info(Util.getMemoryStats());
        try {
            nerModel = SequenceModel.loadModel(modelFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info(Util.getMemoryStats());
        if (nerModel == null)
            nerModel = trainSeqModel();

        if (nerModel != null) {
            Stream.of(nerModel.find("We are traveling to Vietnam the next summer and will come to New York (NYC) soon")).forEach(sp -> System.out.println(sp.toString()));
            Stream.of(nerModel.find("Mr. HariPrasad was present.")).map(Object::toString).forEach(System.out::println);
            Stream.of(nerModel.find("A book named Information Retrieval by Christopher Manning")).map(Object::toString).forEach(System.out::println);
        }
        log.info(Util.getMemoryStats());
//        test2(nerModel);
        test(nerModel, false);
        String phrase = "Keane Inc";
        System.out.println(nerModel.seqLabel(phrase));
        System.out.println(nerModel.getConditional(phrase,NEType.Type.COMPANY.getCode()));
        System.out.println(nerModel.getConditional(phrase, NEType.Type.PERSON.getCode()));
    }

    public static void main(String[] args) {
        loadAndTestNERModel();
//        Map<String,String> places = readEntityList("CONLL/lists/ePADD.ned.list.LOC");
//        Map<String,String> orgs = readEntityList("CONLL/lists/ePADD.ned.list.ORG");
//        Map<String,String> people = readEntityList("CONLL/lists/ePADD.ned.list.PER");
//        System.out.println("Places");
//        places.entrySet().stream().limit(10).map(e->e.getKey()+" "+NEType.parseDBpediaType(e.getValue())).forEach(System.out::println);
//        System.out.println("===========\nORGS");
//        orgs.entrySet().stream().limit(10).map(e->e.getKey()+" "+NEType.parseDBpediaType(e.getValue())).forEach(System.out::println);
//        System.out.println("===========\nPEOPLE");
//        people.entrySet().stream().limit(10).map(e->e.getKey()+" "+NEType.parseDBpediaType(e.getValue())).forEach(System.out::println);
    }
}
