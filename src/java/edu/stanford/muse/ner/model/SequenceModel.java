package edu.stanford.muse.ner.model;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.Config;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.featuregen.FeatureDictionary.MU;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.util.*;
import opennlp.tools.formats.Conll03NameSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by vihari on 07/09/15.
 * This class implements NER task with a Bernoulli Mixture model, every word or pattern is considered a mixture. It does the parameter learning (mu, pi) for every mixture and assigns probabilities to every phrase.
 * An EM algorithm is used to estimate the params. The implementation can handle training size of order 100K. It is sometimes desired to train over a larger training files.
 * TODO: Consider implementing an online EM based param estimation -- see http://cs.stanford.edu/~pliang/papers/online-naacl2009.pdf
 * It is beneficial to include Address-book in training. Names can have an uncommon first and last name --
 * for example a model trained on one-fifth of DBPedia instance types, that is 300K entries assigns 3E-7 score to {Sudheendra Hangal, PERSON}, which is understandable since the DBpedia list contains only one entry with Sudheendra
 */
public class SequenceModel implements NERModel, Serializable {
    public FeatureDictionary dictionary;
    public static String modelFileName = "SeqModel.ser.gz";
    private static final long serialVersionUID = 1L;
    static Log log = LogFactory.getLog(SequenceModel.class);
    //public static final int MIN_NAME_LENGTH = 3, MAX_NAME_LENGTH = 100;
    public static FileWriter fdw = null;
    public static CICTokenizer tokenizer = new CICTokenizer();
    Map<String, String> dbpedia;

    public SequenceModel(FeatureDictionary dictionary, CICTokenizer tokenizer) {
        this.dictionary = dictionary;
        SequenceModel.tokenizer = tokenizer;
    }

    public SequenceModel() {
    }

    /**
     * @param other boolean if is of other type
     */
    static double getLikelihoodWithOther(String phrase, boolean other) {
        phrase = phrase.replaceAll("^\\W+|\\W+$", "");
        if (phrase.length() == 0) {
            if (other)
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
            double v = (double) pair.getFirst() / (double) pair.getSecond();
            if (v > 0.25) {
                if (other)
                    return 1.0 / Double.MAX_VALUE;
                else
                    return 1.0;
            } else {
                if (token.charAt(0) == orig.charAt(0)) {
                    if (other)
                        return 1;
                    else
                        return 1.0 / Double.MAX_VALUE;
                } else {
                    if (other)
                        return 1.0 / Double.MAX_VALUE;
                    else
                        return 1.0;
                }
                //return 1;
            }
        }
        return p;
    }

    String lookup(String phrase) {
        if (dbpedia == null) {
            Map<String, String> orig = EmailUtils.readDBpedia();
            dbpedia = new LinkedHashMap<>();
            for (String str : orig.keySet())
                dbpedia.put(str.toLowerCase(), orig.get(str));
        }

        //if the phrase is from CIC Tokenizer, it won't start with an article
        //enough with the confusion between [New York Times, The New York Times], [Giant Magellan Telescope, The Giant Magellan Telescope]
        Set<String> vars = new LinkedHashSet<>();
        vars.add(phrase);
        vars.add("The "+phrase);
        String dbpediaType;
        for(String var: vars) {
            dbpediaType = dbpedia.get(var.toLowerCase());
            if(dbpediaType!=null) {
                //log.info("Found a match for: "+phrase+" -- "+dbpediaType);
                return dbpediaType;
            }
        }
        return null;
    }

    /**
     * Does sequence labeling of a phrase -- a dynamic programming approach
     * The complexity of this method has quadratic dependence on number of words in the phrase, hence should be careful with the length (a phrase with more than 7 words is rejected)
     * O(T*W^2) where W is number of tokens in the phrase and T is number of possible types
     * Since the word features that we are using are dependent on the boundary of the phrase i.e. the left and right semantic types, features on dictionary lookup e.t.c.
     * Note: This method only returns the entities from the best labeled sequence.
     * @param phrase - String that is to be sequence labelled, keep this short; The string will be rejected if it contains more than 15 words
     * @return all the entities along with their types and quality score found in the phrase
    */
    Map<String, Pair<Short, Double>> seqLabel(String phrase) {
        Map<String, Pair<Short, Double>> segments = new LinkedHashMap<>();
        String dbpediaType = lookup(phrase);
        Short ct = FeatureDictionary.codeType(dbpediaType);

        if (dbpediaType != null && ct >= 0 && (phrase.contains(" ") || dbpediaType.endsWith("Country|PopulatedPlace|Place"))) {
            segments.put(phrase, new Pair<>(ct, 1.0));
            return segments;
        }

        //This step of uncanonicalizing phrases helps merging things that have different capitalization and in lookup
        phrase = EmailUtils.uncanonicaliseName(phrase);
        //phrase = clean(phrase);
        if (phrase == null || phrase.length() == 0)//||!phrase.contains(" "))
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
        if (tokens.length > 7) {
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
            FeatureDictionary.MU mu = dictionary.features.get(token);
            if (token.length() < 2 || mu == null || mu.numMixture == 0)
                continue;
            for (Short type : FeatureDictionary.allTypes) {
                double val = mu.getLikelihoodWithType(type);
                if (!candTypes.containsKey(type))
                    candTypes.put(type, 0.0);
                candTypes.put(type, candTypes.get(type) + val);
            }
            List<Pair<Short, Double>> scands = Util.sortMapByValue(candTypes);
            int si = 0, MAX = 5;
            for (Pair<Short, Double> p : scands)
                if (si++ < MAX)
                    cands.add(p.getFirst());
        }
        //This is just a standard dynamic programming algo. used in HMMs, with the difference that
        //at every word we are checking for the every possible segment
        short OTHER = -2;
        cands.add(OTHER);
        Map<Integer, Triple<Double, Integer, Short>> tracks = new LinkedHashMap<>();
        Map<Integer,Integer> numSegmenation = new LinkedHashMap<>();

        for (int ti = 0; ti < tokens.length; ti++) {
            double max = -1, bestValue = -1;
            int bi = -1;
            short bt = -10;
            for (short t : cands) {
                int tj = Math.max(ti - 6, 0);
                //don't allow multi word phrases with these types
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
                        val *= getConditional(segment, t) * getLikelihoodWithOther(segment, false);
                    else
                        val *= getLikelihoodWithOther(segment, true);

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
                }
            }
            numSegmenation.put(ti, ((bi>=0)?numSegmenation.get(bi):0)+1);
            tracks.put(ti, new Triple<>(bestValue, bi, bt));
        }

        //the backtracking step
        int start = tokens.length - 1;
        while (true) {
            Triple<Double, Integer, Short> t = tracks.get(start);
            String seg = "";
            for (int ti = t.second + 1; ti <= start; ti++)
                seg += tokens[ti] + " ";
            seg = seg.substring(0,seg.length()-1);

            double val;
            if(t.getThird() != OTHER)
                val = getConditional(seg, t.getThird()) * getLikelihoodWithOther(seg, false);
            else
                val = getLikelihoodWithOther(seg, true);

            //if is a single word and dictionary word or word with less than 4 chars and not acronym, then break
            if (!seg.contains(" ") && (seg.length()<3 || (seg.length()<4 && !FeatureGeneratorUtil.tokenFeature(seg).equals("ac")) || DictUtils.fullDictWords.contains(EnglishDictionary.getSingular(seg.toLowerCase()))))
                ;
            else
                segments.put(seg, new Pair<>(t.getThird(), val));
            start = t.second;
            if (t.second == -1)
                break;
        }
        return segments;
    }

    double getConditional(String phrase, Short type) {
        Map<String, FeatureDictionary.MU> features = dictionary.features;
        Map<String, List<String>> tokenFeatures = dictionary.generateFeatures2(phrase, type);
        String[] tokens = phrase.split("\\s+");
        if(FeatureDictionary.sws.contains(tokens[0]) || FeatureDictionary.sws.contains(tokens[tokens.length-1]))
            return 0;

        double sorg = 0;
        String dbpediaType = lookup(phrase);
        short ct = FeatureDictionary.codeType(dbpediaType);

        if(dbpediaType!=null && ct==type){
            if(dbpediaType.endsWith("Country|PopulatedPlace|Place"))
                return 1;
            else if (phrase.contains(" "))
                return 1;
        }

        for (String mid : tokenFeatures.keySet()) {
            Double d;
            MU mu = features.get(mid);
            //Do not even consider the contribution from this mixture if it does not have a good affinity with this type
            if(mu!=null && mu.getLikelihoodWithType(type)<0.1)
                continue;

            int THRESH = 0;
            //imposing the frequency constraint on numMixture instead of numSeen can benefit in weeding out terms that are ambiguous, which could have appeared many times, but does not appear to have common template
            //TODO: this check for "new" token is to reduce the noise coming from lowercase words starting with the word "new"
            if (mu != null && ((type!=FeatureDictionary.PERSON && mu.numMixture>THRESH)||(type==FeatureDictionary.PERSON && mu.numMixture>0)) && !mid.equals("new") && !mid.equals("first") && !mid.equals("open"))
                d = mu.getLikelihood(tokenFeatures.get(mid));
            else
                //a likelihood that assumes nothing
                d = MU.getMaxEntProb();
            double val = d;

            double freq = 0;
            if (d > 0) {
                if (features.get(mid) != null)
                    freq = features.get(mid).getPrior();
                val *= freq;
            }

            //Should actually use logs here, not sure how to handle sums with logarithms
            sorg += val;
        }
        return sorg;
    }

    public Span[] find (String content){
        List<Span> chunks = new ArrayList<>();

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

                    if(p.first!=FeatureDictionary.OTHER && p.second>0) {
                        Span chunk = new Span(e, t.second + t.first.indexOf(e), t.second + t.first.indexOf(e) + e.length());
                        chunk.setType(p.first, new Float(p.second));
                        chunks.add(chunk);
                    }
                }
            }
        }
        return chunks.toArray(new Span[chunks.size()]);
    }

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
            //the buffer size can be much higher than the default 512 for GZIPInputStream
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
    static Pair<Map<String,String>,Map<String,String>> split(Map<String,String> dict, float fraction){
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

    //alpha for initializing Dir.priors and iter is number of EM iterations
    public static SequenceModel train(float alpha, int iter){
        SequenceModel nerModel = new SequenceModel();
        Map<String,String> train = EmailUtils.readDBpedia(1.0);
        //This split is essential to isolate some entries that trained model has not seen
        //Do the train and test splits only in a controlled environment, creating a new copy of DBpedia is costly

        //split the dictionary into train and test sets
        FeatureDictionary dictionary = new FeatureDictionary(train, alpha, iter);
        nerModel.dictionary = dictionary;
        return nerModel;
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
     *     Ex: <PERSON>Yayuk Basuki</PERSON> of Indonesia, <PERSON>Karim Alami</PERSON> of Morocco
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
     * 06 Feb 00:18:01 SequenceModel INFO  - -------------
     * 06 Feb 00:18:01 SequenceModel INFO  - Found: 4119 -- Total: 4236 -- Correct: 3392 -- Missed due to wrong type: 323
     * 06 Feb 00:18:01 SequenceModel INFO  - Precision: 0.8235009
     * 06 Feb 00:18:01 SequenceModel INFO  - Recall: 0.80075544
     * 06 Feb 00:18:01 SequenceModel INFO  - F1: 0.81196886
     * 06 Feb 00:18:01 SequenceModel INFO  - ------------
     *
     * 1/50th on only MWs
     * 13 Feb 13:24:54 SequenceModel INFO  - -------------
     * 13 Feb 13:24:54 SequenceModel INFO  - Found: 4238 -- Total: 4236 -- Correct: 3242 -- Missed due to wrong type: 358
     * 13 Feb 13:24:54 SequenceModel INFO  - Precision: 0.7649835
     * 13 Feb 13:24:54 SequenceModel INFO  - Recall: 0.7653447
     * 13 Feb 13:24:54 SequenceModel INFO  - F1: 0.765164
     * 13 Feb 13:24:54 SequenceModel INFO  - ------------
     *
     * Best performance on CONLL testa full, model trained on entire DBpedia.
     * 4 Feb 00:41:34 SequenceModel INFO  - -------------
     * 14 Feb 00:41:34 SequenceModel INFO  - Found: 6707 -- Total: 7219 -- Correct: 4988 -- Missed due to wrong type: 1150
     * 14 Feb 00:41:34 SequenceModel INFO  - Precision: 0.7437006
     * 14 Feb 00:41:34 SequenceModel INFO  - Recall: 0.69095445
     * 14 Feb 00:41:34 SequenceModel INFO  - F1: 0.71635795
     * 14 Feb 00:41:34 SequenceModel INFO  - ------------
     * */
    public static void test(SequenceModel seqModel, boolean verbose){
        try {
            InputStream in = new FileInputStream(new File(System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator+"ner-benchmarks"+File.separator+"umasshw"+File.separator+"testaspacesep.txt"));
            //7==0111 PER, LOC, ORG
            Conll03NameSampleStream sampleStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, in, 7);
            int numCorrect = 0, numFound = 0, numReal = 0, numWrongType = 0;
            Set<String> correct = new LinkedHashSet<>(), found = new LinkedHashSet<>(), real = new LinkedHashSet<>(), wrongType = new LinkedHashSet<>();
            Multimap<String,String> matchMap = ArrayListMultimap.create();
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
                for(String s: words)
                    sent += s+" ";
                sent = sent.substring(0,sent.length()-1);

                Map<String,String> names = new LinkedHashMap<>();
                opennlp.tools.util.Span[] nspans = sample.getNames();
                for(opennlp.tools.util.Span nspan: nspans) {
                    String n = "";
                    for (int si = nspan.getStart(); si < nspan.getEnd(); si++) {
                        if (si < words.length - 1 && words[si+1].equals("'s"))
                            n += words[si];
                        else
                            n += words[si] + " ";
                    }
                    if(n.endsWith(" "))
                        n = n.substring(0, n.length()-1);
                    if(!onlyMW || n.contains(" "))
                        names.put(n, nspan.getType());
                }
                Span[] chunks = seqModel.find(sent);
                Map<String,String> foundSample = new LinkedHashMap<>();
                if(chunks!=null)
                    for (Span chunk: chunks){
                        String text = chunk.text;
                        Short type = chunk.type;
                        Short coarseType = FeatureDictionary.getCoarseType(type);
                        String typeText;
                        if (coarseType == FeatureDictionary.PERSON)
                            typeText = "person";
                        else if (coarseType == FeatureDictionary.PLACE)
                            typeText = "location";
                        else
                            typeText = "organization";
                        double s = chunk.typeScore;
                        if (s>0 && (!onlyMW || text.contains(" ")))
                            foundSample.put(text, typeText);
                    }

                Set<String> foundNames = new LinkedHashSet<>();
                Map<String,String> localMatchMap = new LinkedHashMap<>();
                for (Map.Entry<String,String> entry : foundSample.entrySet()) {
                    foundTypes.put(entry.getKey(), entry.getValue());
                    boolean foundEntry = false;
                    String foundType = null;
                    for (String name : names.keySet()) {
                        String cname = EmailUtils.uncanonicaliseName(name).toLowerCase();
                        String ek = EmailUtils.uncanonicaliseName(entry.getKey()).toLowerCase();
                        if (cname.equals(ek) || (ignoreSegmentation && (cname.startsWith(ek + " ") || cname.endsWith(" " + ek) || ek.startsWith(cname + " ") || ek.endsWith(" " + cname)))) {
                            foundEntry = true;
                            foundType = names.get(name);
                            matchMap.put(entry.getKey(), name);
                            localMatchMap.put(entry.getKey(), name);
                            break;
                        }
                    }

                    if (foundEntry) {
                        if (entry.getValue().equals(foundType)) {
                            numCorrect++;
                            foundNames.add(entry.getKey());
                            correct.add(entry.getKey());
                        } else {
                            wrongType.add(entry.getKey());
                            numWrongType++;
                        }
                    }
                }

                if(verbose) {
                    log.info("CIC tokens: " + tokenizer.tokenizeWithoutOffsets(sent));
                    log.info(chunks);
                    String fn = "Found names:";
                    for (String f : foundNames)
                        fn += f + "[" + foundSample.get(f) + "] with " + localMatchMap.get(f) + "--";
                    if (fn.endsWith("--"))
                        log.info(fn);

                    String extr = "Extra names: ";
                    for (String f : foundSample.keySet())
                        if (!matchMap.containsKey(f))
                            extr += f + "[" + foundSample.get(f) + "]--";
                    if (extr.endsWith("--"))
                        log.info(extr);
                    String miss = "Missing names: ";
                    for (String name : names.keySet())
                        if (!matchMap.values().contains(name))
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
                for(String name: names.keySet())
                    benchmarkTypes.put(name, names.get(name));

                numReal += names.size();
                numFound += foundSample.size();
                real.addAll(names.keySet());
                found.addAll(foundSample.keySet());
                sample = sampleStream.read();
            }
            float prec = (float)correct.size()/(float)found.size();
            float recall = (float)correct.size()/(float)real.size();
            if(verbose) {
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

            System.out.println("-------------");
            System.out.println("Found: "+found.size()+" -- Total: "+real.size()+" -- Correct: "+correct.size()+" -- Missed due to wrong type: "+(wrongType.size()));
            System.out.println("Precision: " + prec);
            System.out.println("Recall: " + recall);
            System.out.println("F1: "+(2*prec*recall/(prec+recall)));
            System.out.println("------------");
        }catch(IOException e){
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
        try{new FileOutputStream(resultsFile);}catch(IOException e){}

        for(float alpha: alphas) {
            String modelFile = expFolder + File.separator + "ALPHA_" + alpha + "-Iter_" + emIters[emIters.length - 1] + SequenceModel.modelFileName;
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
                    modelFile = expFolder + File.separator + "ALPHA_" + alpha + "-Iter_" + emIter + "-" + SequenceModel.modelFileName;
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
    }

    public static void train() {
        SequenceModel model = train(0.2f, 10);
        try {
            model.writeModel(new File(Config.SETTINGS_DIR+File.separator+modelFileName));
        } catch(IOException e){
            System.err.println("Unable to write model to disk");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
//        testParams();
        String modelFilePath = "experiment-full/ALPHA_0.2-Iter_9-SeqModel.ser";
        try {
            SequenceModel model = SequenceModel.loadModel(modelFilePath);
            test(model,true);
        }catch(IOException e){
            e.printStackTrace();
        }
//        train();
    }
}
