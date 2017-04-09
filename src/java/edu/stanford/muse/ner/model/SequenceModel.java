package edu.stanford.muse.ner.model;

import com.google.common.collect.Multimap;
import edu.stanford.muse.Config;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.featuregen.FeatureUtils;
import edu.stanford.muse.ner.model.test.SequenceModelTest;
import edu.stanford.muse.ner.tokenize.CICTokenizer;
import edu.stanford.muse.ner.tokenize.Tokenizer;
import edu.stanford.muse.util.*;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
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
    public static String MODEL_FILENAME = "SeqModel.ser.gz";
    public static String GAZETTE_FILE = "gazettes.ser.gz";
    public static String RULES_DIRNAME = "rules";
    private static final long serialVersionUID = 1L;
    static Log log = LogFactory.getLog(SequenceModel.class);
    //public static final int MIN_NAME_LENGTH = 3, MAX_NAME_LENGTH = 100;
    private static FileWriter fdw = null;
    protected static Tokenizer tokenizer = new CICTokenizer();

    static boolean DEBUG = false;

    //mixtures of the BMM model
    public Map<String, MU> mixtures = new LinkedHashMap<>();
    //Keep the ref. to the gazette lists it is trained on so that we can lookup these when extracting entities.
    public Map<String,String> gazettes;

    public SequenceModel(Map<String,MU> mixtures, Map<String,String> gazettes) {
        this.mixtures = mixtures;
        this.gazettes = gazettes;
    }

    @Override
    public void setTokenizer(Tokenizer tokenizer){
        SequenceModel.tokenizer = tokenizer;
    }

    private static void writeModelAsRules(SequenceModel model) {
        try {
            NEType.Type[] ats = NEType.getAllTypes();
            //make cache dir if it does not exist
            String rulesDir = Config.SETTINGS_DIR + File.separator + "rules";
            if (!new File(rulesDir).exists()) {
                boolean mkdir = new File(rulesDir).mkdir();
                if (!mkdir) {
                    log.fatal("Cannot create rules dir. " + rulesDir + "\n" +
                            "Please make sure you have access rights and enough disk space\n" +
                            "Cannot proceed, exiting....");
                    return;
                }
            }
            writeObjectAsSerGZ(model.gazettes, rulesDir+File.separator+SequenceModel.GAZETTE_FILE);
            Map<String, MU> features = model.mixtures;
            for (NEType.Type et: ats) {
                short type = et.getCode();
                //FileWriter fw = new FileWriter(cacheDir + File.separator + "em.dump." + type + "." + i);
                Writer ffw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(rulesDir + File.separator + et + ".txt")));
                Map<String, Double> scoreForSort = new LinkedHashMap<>();
                Map<String, Double> scores = new LinkedHashMap<>();
                for (String w : features.keySet()) {
                    MU mu = features.get(w);
                    double v1 = mu.getLikelihoodWithType(type) * (mu.numMixture / mu.numSeen);
                    double v = v1 * Math.log(mu.numSeen);
                    if (Double.isNaN(v)) {
                        scoreForSort.put(w, 0.0);
                        scores.put(w, v1);
                    } else {
                        scoreForSort.put(w, v);
                        scores.put(w, v1);
                    }
                }
                List<Pair<String, Double>> ps = Util.sortMapByValue(scoreForSort);
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
                    //some mixtures may have very low evidence that their "numMixture" is 0, there is just no point dumping them
                    if (maxT.equals(type) && mu.numMixture>0) { //&& scores.get(p.getFirst()) >= 0.001) {
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

    private double getPrior(MU mu){
        return mu.getNumSeenEffective()/mixtures.size();
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
            //Map<String,Pair<Integer,Integer>> map = EnglishDictionary.getDictStats();
            //Pair<Integer,Integer> pair = map.get(token);
            Multimap<String, Pair<String, Integer>> map = EnglishDictionary.getTagDictionary();//getDictStats();
            Collection<Pair<String, Integer>> pairs = map.get(token);

            if (pairs == null) {
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
            //double v = (double) pair.getFirst() / (double) pair.getSecond();
            double v = pairs.stream().filter(pair->pair.first.startsWith("NN")||pair.first.startsWith("JJ")).mapToDouble(pair->pair.second).sum();
            v /= pairs.stream().mapToDouble(pair->pair.second).sum();
            //if (v > 0.25) {
            if(v > 0.25) {
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
        tokenFeatures = RuleInducer.typeFeatures(tokenFeatures, mixtures);
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
                    freq = getPrior(mixtures.get(mid));
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

        opennlp.tools.util.Span[] sentSpans = NLPUtils.tokenizeSentenceAsSpan(content);
        assert sentSpans!=null;
        for(opennlp.tools.util.Span sentSpan: sentSpans) {
            String sent = sentSpan.getCoveredText(content).toString();
            int sstart = sentSpan.getStart();

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
                        Span chunk = new Span(e, sstart + t.second + t.first.indexOf(e), sstart + t.second + t.first.indexOf(e) + e.length());
                        chunk.setType(p.first, new Float(p.second));
                        spans.add(chunk);
                    }
                }
            }
        }
        return spans.toArray(new Span[spans.size()]);
    }

    public synchronized void writeModel(String fileName) throws IOException{
        writeObjectAsSerGZ(this, fileName);
    }

    //writes a .ser.gz file to the file passed in args.
    static void writeObjectAsSerGZ(Object o, String fileName) throws IOException{
        FileOutputStream fos = new FileOutputStream(new File(fileName));
        GZIPOutputStream gos = new GZIPOutputStream(fos);
        ObjectOutputStream oos = new ObjectOutputStream(gos);
        oos.writeObject(o);
        oos.close();
    }

    public static synchronized SequenceModel loadModelFromRules(String rulesDirName) throws IOException {
        String rulesDir = Config.DEFAULT_SETTINGS_DIR+File.separator+rulesDirName;
        if (!new File(rulesDir).exists()) {
            log.fatal("The supplied directory: " + rulesDirName + " does not exist!\n" +
                    "Cannot continue, exiting....");
            return null;
        }
        List<String> files = Arrays.asList(new File(rulesDir).list());
        NEType.Type[] alltypes = NEType.getAllTypes();
         List<NEType.Type> notFound = Stream.of(alltypes).filter(t -> !files.contains(t.name()+".txt")).collect(Collectors.toList());
        if (notFound.size()>0) {
            notFound.forEach(t -> log.warn("Did not find " + t.name() + " in the rules dir: " + rulesDirName));
            log.warn("Some types not found in the directory supplied.\n" +
                    "Perhaps a version mismatch or the folder is corrupt!\n" +
                    "Be warned, I will see what I can do.");
        }
        Map<String, String> gazette = loadGazette(rulesDirName);
        Map<String, MU> mixtures = new LinkedHashMap<>();
        List<String> classes = Stream.of(NEType.Type.values()).map(NEType.Type::toString).collect(Collectors.toList());
        files.stream().filter(f->f.endsWith(".txt") && classes.contains(f.substring(0, f.length()-4))).forEach(f -> {
            try {
                LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new FileInputStream(rulesDir + File.separator + f)));
                String line;
                List<String> lines_MU = new ArrayList<>();
                while ((line = lnr.readLine()) != null) {
                    //This marks the end of one MU block in the rules file
                    if (line.startsWith("==")) {
                        MU mu = MU.parseFromText(lines_MU.toArray(new String[lines_MU.size()]));
                        if (mu != null)
                            mixtures.put(mu.id, mu);
                        lines_MU.clear();
                    } else
                        lines_MU.add(line);
                }
            } catch (IOException ie) {
                log.warn("Could not read file: " + f + " from " + rulesDirName, ie);
            }
        });
        return new SequenceModel(mixtures, gazette);
    }

    public static synchronized Map<String,String> loadGazette(String modelDirName){
        ObjectInputStream ois;
        try {
            ois = new ObjectInputStream(new GZIPInputStream(Config.getResourceAsStream(modelDirName + File.separator + GAZETTE_FILE)));
            Map<String,String> model = (Map<String, String>) ois.readObject();
            ois.close();
            return model;
        } catch (Exception e) {
            Util.print_exception("Exception while trying to load gazette from: " + modelDirName, e, log);
            return null;
        }
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

    /**
     * Use this routine to read an external gazette list, the resource is expected to be a plain text file
     * the lines in the file should be two fields separated by ' ' (space), the first field should be the title and second: its type.
     * The type of the resource should follow the style of DBpedia types in our generated instance file, see NEType.dbpediaTypesMap for more info.
     * for example, a type annotation for building should look like "Building|ArchitecturalStructure|Place"
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

    /**
     * A low level train interface for experimentation and extension over the default model.
     * Use this method for training the default model
     * Training data should be a list of phrases and their types, the type should follow DBpedia ontology; specifically http://downloads.dbpedia.org/2015-04/dbpedia_2015-04.nt.bz2
     * See epadd-settings/instance_types to understand the format better
     * It is possible to relax the ontology constraint by changing the aTypes and ignoreDBpediaTypes fields in FeatureDictionary appropriately
     * With tokenPriors it is possible to set initial beliefs, for example "Nokia" is a popular company; the first key in the map should be a single word token, the second map is the types and its affiliation for various types (DBpedia ontology again)
     * iter param is the number of EM iterations, any value >5 is observed to have no effect on performance with DBpedia as training data
     *  */
    private static SequenceModel train(Map<String,String> trainData, Map<String,Map<String,Float>> tokenPriors, int iter){
        log.info("Initializing trainer");
        log.info(Util.getMemoryStats());
        RuleInducer trainer = new MixtureModelLearner(trainData, tokenPriors, new MixtureModelLearner.Options());
        trainer.learn();

        return new SequenceModel(trainer.getMixtures(), trainer.getGazettes());
    }

    public static SequenceModel train(Map<String,String> tdata){
        log.info(Util.getMemoryStats());

        float alpha = 0.2f;
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
        RuleInducer trainer = new MixtureModelLearner(tdata, tokenPriors, new MixtureModelLearner.Options());
        trainer.learn();
        return new SequenceModel(trainer.getMixtures(), trainer.getGazettes());
    }

    public static SequenceModel train(float alpha, int emIter){
        Map<String,String> tdata = DBpediaUtils.readDBpedia();
        //also include CONLL lists
        String resources[] = Config.NER_RESOURCE_FILES;
        for(String rsrc: resources) {
            //DBpedia has a finer type, respect it.
            Map<String,String> map = readEntityList(rsrc);
            for(Map.Entry<String,String> e: map.entrySet())
                    tdata.putIfAbsent(e.getKey(),e.getValue());
        }

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
    public static SequenceModel train() {
        long st = System.currentTimeMillis();
        SequenceModel model = train(0.2f, 3);

        long et = System.currentTimeMillis();
        log.info("Trained and dumped model in "+((et-st)/60000)+" minutes.");
        return model;
    }

    static void loadAndTestNERModel(){
        if (fdw == null) {
            try {
                fdw = new FileWriter(new File(System.getProperty("user.home") +
                        File.separator + "epadd-settings" + File.separator + "cache" +
                        File.separator + "mixtures.dump"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.err.println("Loading model...");
        SequenceModel nerModel;
        log.info(Util.getMemoryStats());
        try {
            nerModel = SequenceModel.loadModelFromRules("rules");
            if(nerModel==null) {
                nerModel = train();
                writeModelAsRules(nerModel);
            }

            log.info(Util.getMemoryStats());
            SequenceModelTest.ParamsCONLL params = new SequenceModelTest.ParamsCONLL();
            SequenceModelTest.testCONLL(nerModel, false, params);
            log.info(Util.getMemoryStats());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        loadAndTestNERModel();
    }
}
