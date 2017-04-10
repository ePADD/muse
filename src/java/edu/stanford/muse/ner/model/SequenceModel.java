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
public class SequenceModel extends NERModel implements Serializable{
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

    @Override
    Tokenizer getTokenizer() {
        return tokenizer;
    }

    @Override
    Map<String, String> getGazette() {
        return gazettes;
    }

    public static void writeModelAsRules(SequenceModel model) {
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
            Util.writeObjectAsSerGZ(model.gazettes, rulesDir+File.separator+SequenceModel.GAZETTE_FILE);
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
                        ffw.write(mu.toString());
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

    @Override
    double getConditional(String phrase, Short type) {
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

    public synchronized void writeModel(String fileName) throws IOException{
        Util.writeObjectAsSerGZ(this, fileName);
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

    public static synchronized SequenceModel loadModel(String modelPath) {
        try {
            //the buffer size can be much higher than default 512 for GZIPInputStream
            SequenceModel model = (SequenceModel) Util.readObjectFromSerGZ(modelPath);
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
        String modelName = "dbpediaTest" + File.separator + "SequenceModel-80.ser.gz";
        SequenceModel model = loadModel(modelName);
        writeModelAsRules(model);
    }
}
