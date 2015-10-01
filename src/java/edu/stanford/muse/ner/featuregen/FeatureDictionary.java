package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.Config;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;

import edu.stanford.muse.util.Util;
import libsvm.svm_parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;
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
 * Nominal: generates value based on proportion of times the nominal value appeared in iType to total frequency
 * Boolean: Passes the value of the feature generator as is
 * Numeric: Passes the value of the feature generator as is
 * TODO: Explore indexing of this data-structure
 *
 * TODO: There are all kinds of crazy names in DBpedia, for example: ESCP_Europe is an University, the algorithm should not let such examples to sway its decision of tag
 * We know the
 * */
public class FeatureDictionary implements Serializable {
    /**
     * Features for a word that corresponds to a mixture
     * position: SOLO, INSIDE, BEGIN, END
     * number of words: number of words in the phrase, can be any value from 1 to 10
     * left and right labels, LABEL is one of: LOC, ORG,PER, OTHER, NEW, stop words, special chars*/
    public static class MU implements Serializable{
        //the likelihood with the type is also considered
        static String[] TYPE_LABELS = new String[]{"per","org","loc","oth"};
        //static String[] TYPE_LABELS = new String[]{"Y","N"};
        //all possible labels of the words to the left and right
        //NULL symbol when there is no previous or the next word, I am not convinced if this is required, since we already have position label
        //"LOC","ORG","PER","OTHER","NEW"
        //Responsibility, does not seem to do well in some cases, For example: "Co", though occurred in many ORG names, occurred in few people names with other words that are relatively unknown words, hence Co is being marked as PERSON name

        //has a bad feeling about the LOC, ORG, PER, as they depend on the parameters that are being learned
        //For example: in New York Stock Exchange or California State University, "New York" and "California State" have good location scores, since they are in comfortable context.
        //this is probably the problem of scoring, when the mixture frequency is included in the score, things like New though is single token will get a high score due to the pi term
        //New York, for example might have explained a lot of locations,
        //"and","for","to","in","at","on","the","of","a","an","is","&",","
        static String[] WORD_LABELS = new String[]{"OTHER", "LOC", "ORG", "PER", "NULL"};
        //it is useful to have special symbols for position, even though we have NULL symbol in the word labels, so that we dont see symbols like University, Association e.t.c.
        static String[] POSITION_LABELS = new String[]{"S","B","I","E"};
        //static int NUM_WORDLENGTH_LABELS = 10;
        //feature and the value, for example: <"LEFT: and",200>
        //indicates if the values are final or if they have to be learned
        Map<String,Double> muVectorPositive;
        //number of times this mixture is probabilistically seen, is summation(gamma*x_k)
        public double numMixture;
        public MU() {
            initialise();
        }

        //Since we depend on tags of the neighbouring tokens in a big way, we initialise so that the mixture likelihood with type is more precise.
        //and the likelihood with all the other types to be equally likely
        public static MU initialise(Map<Short, Pair<Double,Double>> initialParams){
            //dont perform smoothing here, the likelihood is sometimes set to 0 deliberately for some token with some types
            //performing smoothing, will make the contribution of these params non-zero in some phrases where it should not be and leads to unexpected results.
            double s1 = 0, s2 = 0;
            MU mu = new MU();
            for(Short type: initialParams.keySet()) {
                if(type==FeatureDictionary.PERSON)
                    mu.muVectorPositive.put("per", initialParams.get(type).first);
                else if(type==FeatureDictionary.ORGANISATION)
                    mu.muVectorPositive.put("org", initialParams.get(type).first);
                else if(type==FeatureDictionary.PLACE)
                    mu.muVectorPositive.put("loc", initialParams.get(type).first);
                else
                    mu.muVectorPositive.put("oth", initialParams.get(type).first);
                s1 += initialParams.get(type).first;
                s2 = initialParams.get(type).second;
            }
            //System.err.println("INitialised with: "+initialParams);
            //mu.muVectorPositive.put("oth", 1-s1);
            mu.numMixture = s2;
//            for(int i=0;i<TYPE_LABELS.length;i++){
//                if(i==0) {
//                    mu.muVectorPositive.put(TYPE_LABELS[i], (1+numThisType)/(2+numTotal));
//                }
//                else{
//                    mu.muVectorPositive.put(TYPE_LABELS[i], (numTotal - numThisType + 1)/(2 + numTotal));
//                }
//            }
//            mu.numMixture = numTotal+1;
            return mu;
        }

        private void initialise(){
            muVectorPositive = new LinkedHashMap<>();
            this.numMixture = 0;
        }

        //returns P(type/this-mixture)
        public double getLikelihoodWithType(String typeLabel){
            double p1, p2;
//            if(muVectorPositive.get(TYPE_LABELS[0]) == null) {
//                System.err.println("Uninitialised type priors for the mixture");
//                return 0;
//            }

            for(String tl: TYPE_LABELS) {
                if(tl.equals(typeLabel)) {
                    if(muVectorPositive.containsKey(tl)) {
                        p1 = muVectorPositive.get(tl);
                        p2 = numMixture;
                        return p1 / p2;
                    }
                    //its possible that a mixture has never seen certain types
                    else{
                        return 0;
                    }
                }
            }
            System.err.println("!!!FATAL: Unknown type label: "+typeLabel+"!!!");
            return 0;
        }

        public double getLikelihoodWithType(short typeLabel){
            if(typeLabel==FeatureDictionary.PERSON)
                return getLikelihoodWithType("per");
            else if(typeLabel==FeatureDictionary.ORGANISATION)
                return getLikelihoodWithType("org");
            else if(typeLabel==FeatureDictionary.PLACE)
                return getLikelihoodWithType("loc");
            else
                return getLikelihoodWithType("oth");
        }

        //gets number of symbols in the dimension represented by this feature
        public static int getNumberOfSymbols(String f){
            for(String str: TYPE_LABELS)
                if(str.equals(f))
                    return TYPE_LABELS.length;
            for(String str: WORD_LABELS)
                if(f.endsWith(str))
                    return WORD_LABELS.length;
            for(String str: POSITION_LABELS)
                if(f.endsWith(str))
                    return POSITION_LABELS.length;
//            if(f.startsWith("WL:"))return NUM_WORDLENGTH_LABELS;
            log.error("!!!REALLY FATAL!!! Unknown feature: " + f);
            return 0;
        }

        public double getFreq(){
            return numMixture;
        }

        //features also include the type of the phrase
        //returns the log of P(type,features/this-mixture)
        public double getLikelihood(Set<String> features){
            double p = 1;
            for(String f: features){
                int v = getNumberOfSymbols(f);
                boolean smooth = true;
                //does not want to smooth, if the feature is position label
                //also dont smooth if the feature is a type related feature
                //TODO: This way of checking the feature type is pathetic, improve this
                if(!features.contains("per") && f.startsWith("PL:"))
                    smooth = false;
                //TODO: set proper condition for smoothing
                //just after initialisation, in this case the should not assign 0 mass for unseen observations
                if(muVectorPositive.size()==TYPE_LABELS.length)
                    smooth = true;

                //should not smooth type related mu params even initially
                if(f.indexOf(':')==-1)
                    smooth = false;

                if(!muVectorPositive.containsKey(f)){
                    //no smoothing in the case of position label
                    if(!smooth)
                        p *= 0;
                    else {
                        //System.err.println("!!!FATAL!!! Unknown feature: "+f);
                        p *= (1.0/v);
                    }
                    //System.err.println("!f: "+f+", "+muVectorPositive.get(f)+" : "+numMixture+", "+v);
                    continue;
                }
                double val;
                if(smooth)
                    val = (muVectorPositive.get(f)+1)/(numMixture+v);
                else
                    if(numMixture>0)
                        val = (muVectorPositive.get(f))/(numMixture);
                    else
                        val = 0;
                //System.err.println("f: "+f+", "+muVectorPositive.get(f)+" : "+numMixture+", "+v);

                if(Double.isNaN(val)) {
                    log.warn("Found a nan here: " + f + " " + muVectorPositive.get(f) + ", " + numMixture + ", " + v);
                    log.warn(toString());
                }
                p *= val;
            }
            return p;
        }

        //where N is the total number of observations, for normalization
        public double getPrior(int N){
            //Initially numMixtures is just set to 1
            //Initially, this value is used only for computing the gamma value, hence any value,
            //that remains the same for all the mixtures will do
            return numMixture/N;
        }

        /**Maximization step in EM update, update under the assumption that add will be called only if the evidence for the corresponding mixture is seen, i.e. xk!=0
         * @param resp - responsibility of this mixture in explaining the type and features
         * @param features - set of all *relevant* features to this mixture*/
        public void add(double resp, Set<String> features) {
            //if learn is set to false, ignore all the observations
            if (Double.isNaN(resp))
                log.warn("Responsibility is nan for: " + features);
            numMixture += resp;
            for (String f : features) {
                if (!muVectorPositive.containsKey(f)) {
                    //System.err.println("!!!FATAL!!! Unknown feature: "+f);
                    //continue;
                    muVectorPositive.put(f, 0.0);
                }
                muVectorPositive.put(f, muVectorPositive.get(f) + resp);
            }
        }

        public double difference(MU mu){
            if(this.muVectorPositive == null || mu.muVectorPositive == null)
                return 0.0;
            //probably happening for stop words,
            if(numMixture==0 || mu.numMixture == 0)
                return 0.0;
            double d = 0;
            for(String str: muVectorPositive.keySet()){
                if(mu.muVectorPositive.get(str)==null){
                    //that is strange, should not happen through the way this method is being used
                    continue;
                }
                d += Math.pow((muVectorPositive.get(str)/numMixture)-(mu.muVectorPositive.get(str)/mu.numMixture),2);
            }
            double res = Math.sqrt(d);
            if(Double.isNaN(res)) {
                System.err.println("============================");
                for(String str: muVectorPositive.keySet()){
                    if(mu.muVectorPositive.get(str)==null){
                        //that is strange, should not happen through the way this method is being used
                        continue;
                    }
                    System.err.println((muVectorPositive.get(str)/numMixture));
                    System.err.println((mu.muVectorPositive.get(str)/mu.numMixture));
                }
                System.err.println(numMixture + "  " + mu.numMixture);
            }
            return res;
        }

        @Override
        public String toString(){
            String str = "";
            String p[] = new String[]{"L:","R:","PL:",""};
//            String[] WORD_LENGTHS = new String[NUM_WORDLENGTH_LABELS];
//            for(int i=1;i<=NUM_WORDLENGTH_LABELS;i++)
//                WORD_LENGTHS[i-1] = i+"";
            String[][] labels = new String[][]{WORD_LABELS,WORD_LABELS,POSITION_LABELS,TYPE_LABELS};
            for(int i=0;i<labels.length;i++) {
                Map<String,Double> some = new LinkedHashMap<>();
                for(int l=0;l<labels[i].length;l++) {
                    String d = p[i] + labels[i][l];
                    if(muVectorPositive.get(d) != null)
                        some.put(d, muVectorPositive.get(d) / numMixture);
                    else
                        some.put(d, 0.0);
                }
                List<Pair<String,Double>> smap;
                smap = Util.sortMapByValue(some);
                for(Pair<String,Double> pair: smap)
                    str += pair.getFirst()+":"+pair.getSecond()+"-";
                str += "\n";
            }
            return str;
        }
    }
    private static final long serialVersionUID = 1L;
    //dimension -> instance -> entity type of interest -> #positive type, #negative type
    //patt -> Aa -> 34 100, pattern Aa occurred 34 times with positive classes of the 100 times overall.
    //mixtures of the BMM model
    public Map<String, MU> features = new LinkedHashMap<>();
    public static Set<String> newWords = null;
    //threshold to be classified as new word
    public static int THRESHOLD_FOR_NEW = 1;
    //contains number of times a CIC pattern is seen (once per doc), also considers quoted text which may reflect wrong count
    //This can get quite depending on the archive and is not a scalable solution

    //this data-structure is only used for Segmentation which itself is not employed anywhere
    public Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    //sum of all integers in the map above, i.e. frequencies of all pairs of words, required for normalization
    public int totalcount = 0;
    //total number of word patterns

    //The data type of types (Short or String) below has no effect on the size of the dumped serialised model, Of course!
    public static Short PERSON = 1, ORGANISATION = 2, PLACE = 3, OTHER = -1;
    public static Short[] allTypes = new Short[]{PERSON, ORGANISATION, PLACE};
    static Log log = LogFactory.getLog(FeatureDictionary.class);
    public static Map<Short, String[]> aTypes = new LinkedHashMap<Short, String[]>();
    public FeatureGenerator[] featureGens = null;
    public static Map<Short, String[]> startMarkersForType = new LinkedHashMap<Short, String[]>();
    public static Map<Short, String[]> endMarkersForType = new LinkedHashMap<Short, String[]>();
    public static List<String> ignoreTypes = new ArrayList<String>();
    //feature types
    public static short NOMINAL = 0, BOOLEAN = 1, NUMERIC = 2;
    public static Pattern endClean = Pattern.compile("^\\W+|\\W+$");
    static List<String> sws = Arrays.asList("and","for","to","in","at","on","the","of", "a", "an", "is");
    static List<String> symbols = Arrays.asList("&","-",",");
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
        Map<String, Map<String,Map<Short, Pair<Double, Double>>>> lfeatures = new LinkedHashMap<>();
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
                        add(lfeatures, fg.createFeatures(str, null, null, iType), entityType, iType);
                }
            }
            String[] words = str.split("\\s+");
            for(int ii=0;ii<words.length-1;ii++) {
                String cstr = words[ii]+":::"+words[ii+1];
                totalcount++;
            }

            timeToComputeFeatures += System.currentTimeMillis() - tms;

            if ((++gi) % 10000 == 0) {
                log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs) + "% in gazette: " + g);
                log.info("Time spent in computing features: " + timeToComputeFeatures);
            }
            nume++;
        }

        log.info("Considered " + nume + " entities in " + gazettes.size() + " total entities");
        log.info("Done analysing gazettes in: " + (System.currentTimeMillis() - start_time));

        Map<String, Map<Short, Pair<Double,Double>>> words = lfeatures.get("words");
        for(String str: words.keySet()){
//            if(!features.containsKey(str))
//                features.put(str, new LinkedHashMap<Short, MU>());
//            System.err.println(str);
            String ic = str.substring(0,1).toUpperCase();
            if(str.length()>1)
                ic+=str.substring(1,str.length());
            //System.err.println(str+"->"+ic);
            Short ht = null;
            if(gazettes.containsKey(ic)) {
                String type = gazettes.get(ic);
                for(String at: FeatureDictionary.aTypes.get(FeatureDictionary.PERSON))
                    if(type.endsWith(at)) {
                        ht = FeatureDictionary.PERSON;
                        break;
                    }
                if(ht==null)
                    for(String at: FeatureDictionary.aTypes.get(FeatureDictionary.ORGANISATION))
                        if(type.endsWith(at)) {
                            ht = FeatureDictionary.ORGANISATION;
                            break;
                        }
                if(ht==null)
                    for(String at: FeatureDictionary.aTypes.get(FeatureDictionary.PLACE))
                        if(type.endsWith(at)) {
                            ht = FeatureDictionary.PLACE;
                            break;
                        }
            }
            Map<Short, Pair<Double,Double>> priors = new LinkedHashMap<>();
            double total=0,totalT=0;
            for(Short type: words.get(str).keySet()){
//                if(ht!=null && ht>0)
//                    System.err.println("Initialising "+str+" with type: "+ht);
                Pair<Double,Double> p = words.get(str).get(type);
                if(ht==null) {
                    priors.put(type, p);
                    totalT+=p.first;
                }
                else if(ht==type) {
                    priors.put(type, new Pair<>(p.getSecond(), p.getSecond()));
                    totalT+=p.getSecond();
                }
                else {
                    priors.put(type, new Pair<>(0.0, p.getSecond()));
                    totalT += 0;
                }
                total=p.getSecond();
            }
            if(ht!=null)
                priors.put(FeatureDictionary.OTHER, new Pair<>(0.0, total));
            else
                priors.put(FeatureDictionary.OTHER, new Pair<>(total-totalT,total));
            features.put(str, MU.initialise(priors));
        }

        return this;
    }

    public FeatureVector getVector(String cname, Short iType) {
        Map<String, List<String>> features = FeatureGenerator.generateFeatures(cname, null, null, iType, featureGens);
        return new FeatureVector(this, iType, featureGens, features);
    }

    //should not try to build dictionary outside of this method
    private void add( Map<String, Map<String,Map<Short, Pair<Double, Double>>>> lfeatures, Map<String, List<String>> wfeatures, String type, Short iType) {
        if(wfeatures==null)
            return;
        for (String dim : wfeatures.keySet()) {
            if (!lfeatures.containsKey(dim))
                lfeatures.put(dim, new LinkedHashMap<String, Map<Short, Pair<Double, Double>>>());
            Map<String, Map<Short, Pair<Double, Double>>> hm = lfeatures.get(dim);
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
            lfeatures.put(dim, hm);
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
                if(w.equals(""))
                    continue;
                if(!wordFreqs.containsKey(w))
                    wordFreqs.put(w, 0);
                wordFreqs.put(w, wordFreqs.get(w)+1);
            }
        }
        for(String word: wordFreqs.keySet()){
            if(wordFreqs.get(word)<=THRESHOLD_FOR_NEW)
                newWords.add(word);
        }
    }

    public static String[] getPatts2(String phrase){
        List<String> patts = new ArrayList<>();
        String[] words = phrase.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            //dont emit stop words
            //canonicalize the word
            word = word.toLowerCase();
            if(sws.contains(word))
                continue;
            patts.add(word);
        }
        return patts.toArray(new String[patts.size()]);
    }

    public static String[] getPatts(String phrase){
        List<String> patts = new ArrayList<>();
        String[] words = phrase.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            //dont emit stop words
            //canonicalize the word
            word = word.toLowerCase();
            if(sws.contains(word) || symbols.contains(word)){
                continue;
            }
            String t = word;
            if(i>0 && (sws.contains(words[i-1].toLowerCase())||symbols.contains(words[i-1].toLowerCase())))
                t = words[i-1].toLowerCase()+" "+t;
            if(i<(words.length-1) && (sws.contains(words[i+1].toLowerCase())||(symbols.contains(words[i+1].toLowerCase()))))
                t += " "+words[i+1].toLowerCase();
//            if (i > 0 && i < (words.length - 1))
//                t = "*" + word + "*";
//            else if (i == 0 && words.length > 1)
//                t = word + "*";
//            else if (i == (words.length - 1) && words.length > 1)
//                t = "*" + word;
//            //references are generally made with first name and this may have continuation like Harvard_University or Apple_Inc
//            else t = word;


            //emit all the words or patterns
            if (t != null)
                patts.add(t);
        }
        return patts.toArray(new String[patts.size()]);
    }

    public String getLabel(String word, Map<String, MU> mixtures){
//        if(newWords == null)
//            computeNewWords();
        if(word == null)
            return "NULL";
        word = word.toLowerCase();
//        if(sws.contains(word))
//            return word;
//        if(word.equals("&")||word.equals(","))
//            return word;
//        if(newWords.contains(word))
//            return "NEW";
        double per, org, loc, other;
        //System.err.println("Token: "+word);
        //this can happen, since we ignore a few types
        if(mixtures.get(word) == null)
            return "OTHER";
        per = mixtures.get(word).getLikelihoodWithType("per");
        org = mixtures.get(word).getLikelihoodWithType("org");
        loc = mixtures.get(word).getLikelihoodWithType("loc");
        other = 1-per-org-loc;
        double m = Math.max(per,org);
        m = Math.max(m, loc);m = Math.max(m, other);
        //System.err.println(word+" "+per+", "+org+", "+loc+", "+other+" 444ln");
        if(m == per)
            return "PER";
        if(m == org)
            return "ORG";
        if(m == loc)
            return "LOC";
        return "OTHER";
    }

    /**
     * TODO: move this to an appropriate place
     * A generative function, that takes the phrase and generates features.
     * requires mixtures as a parameter, because some of the features depend on this
     * returns a map of mixture identity to the set of features relevant to the mixture */
    public Map<String,Set<String>> generateFeatures(String phrase, Map<String, MU> mixtures, Short type){
        Map<String, Set<String>> mixtureFeatures = new LinkedHashMap<>();
        String[] words = getPatts(phrase);
        if(words.length == 0)
            return mixtureFeatures;
        String fw = words[0];
        String lw = words[words.length-1];
        for(int wi = 0; wi<words.length; wi++){
            if(sws.contains(words[wi].toLowerCase()))
                continue;
            Set<String> features = new LinkedHashSet<>();
            String prevWord = null, nxtWord = null;
//            if(wi > 0)
//                prevWord = words[wi-1];
//            if(wi < (words.length-1))
//                nxtWord = words[wi+1];
            prevWord = fw;
            nxtWord = lw;
            if(wi == 0)
                prevWord = null;
            if(wi == (words.length-1))
                nxtWord = null;

            String prevLabel = getLabel(prevWord, mixtures);
            String nxtLabel = getLabel(nxtWord, mixtures);

            String posLabel;
            if(wi==0) {
                if (words.length == 1)
                    posLabel = "S";
                else
                    posLabel = "B";
            }
            else if(wi>0 && wi<(words.length-1)){
                posLabel = "I";
            }
            else //if(wi == (words.length-1))
                posLabel = "E";
            String nwlabel = words.length+"";
            features.add("L:"+prevLabel);
            features.add("R:"+nxtLabel);
            features.add("PL:"+ posLabel);
//            features.add("WL:"+nwlabel);
            if(type==FeatureDictionary.PERSON)
                features.add("per");
            else if(type==FeatureDictionary.ORGANISATION)
                features.add("org");
            else if(type==FeatureDictionary.PLACE)
                features.add("loc");
            else if(type==FeatureDictionary.OTHER)
                features.add("oth");
            else
                System.err.println("!!FATAL: Unknown feature type: "+type+"!!");

            mixtureFeatures.put(words[wi].toLowerCase(), features);
        }
        return mixtureFeatures;
    }

    public void EM(Map<String,String> gazettes){
//        if(newWords == null)
//            computeNewWords();
        //System.err.println("Done computing new words");
        System.err.println("Performing EM on: #"+features.size()+" words");

        Map<String, MU> mixtures = features;
        Map<String, MU> revisedMixtures = new LinkedHashMap<>();
        int MAX_ITER = 3;
        int N = gazettes.size();
        int wi = 0;
        for(int i=0;i<MAX_ITER;i++) {
            wi = 0;
            for (String phrase : gazettes.keySet()) {
                if (wi++ % 1000 == 0)
                    System.err.println("EM iter: " + i + ", " + wi + "/" + N);
                String type = gazettes.get(phrase);
                boolean allowed = false;
                Short coarseType = FeatureDictionary.OTHER;
                outer:
                for(Short iType: allTypes) {
                    for (String aType : aTypes.get(iType))
                        if (type.endsWith(aType)) {
                            coarseType = iType;
                            break outer;
                        }
                }

                double z = 0;
                //responsibilities
                Map<String, Double> gamma = new LinkedHashMap<>();
                //Word (sort of mixture identity) -> Features
                Map<String, Set<String>> featureMap = generateFeatures(phrase, mixtures, coarseType);
                for (String mi : featureMap.keySet()) {
                    //this should not even happen
                    if (mixtures.get(mi) == null) {
                        //System.err.println("Did not find mixture for: "+ mi +" "+iType);
                        continue;
                    }
                    MU mu = mixtures.get(mi);
                    double d = mu.getLikelihood(featureMap.get(mi)) * mu.getPrior(N);
                    //only two tags, org/non-org
                    //by N cancels out whe we normalize
                    //double d = (allowed?pfreq:(1-pfreq)) * (p.getSecond()+2);
                    //System.err.println("score for: " + mi + " "+ featureMap.get(mi) + " " + d);
                    gamma.put(mi, d);
                    z += d;
                }
                if (z == 0)
                    continue;

                for (String g : gamma.keySet())
                    gamma.put(g, gamma.get(g) / z);

                for (String g : gamma.keySet()) {
                    if (!revisedMixtures.containsKey(g)) {
                        revisedMixtures.put(g, new MU());
                    }

                    if (Double.isNaN(gamma.get(g)))
                        System.err.println("Gamma: " + gamma.get(g) + ", " + g);
                    revisedMixtures.get(g).add(gamma.get(g), featureMap.get(g));
                }
            }
            double change = 0;
            for (String mi : mixtures.keySet())
                if (revisedMixtures.containsKey(mi))
                    change += revisedMixtures.get(mi).difference(mixtures.get(mi));
            log.info("Iter: " + i + ", change: " + change);
            mixtures = revisedMixtures;
            revisedMixtures = new LinkedHashMap<>();

            try {
                for (Short type : allTypes) {
                    FileWriter fw = new FileWriter(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "em.dump." + type + "." + i);
                    Map<String, Double> some = new LinkedHashMap<>();
                    for (String w : mixtures.keySet()) {
                        double v = mixtures.get(w).getLikelihoodWithType(type) * Math.log(mixtures.get(w).getFreq());
                        if(Double.isNaN(v))
                            some.put(w, 0.0);
                        else
                            some.put(w, v);
                    }
                    List<Pair<String, Double>> ps = Util.sortMapByValue(some);
                    for (Pair<String, Double> p : ps) {
                        fw.write("Token: " + p.getFirst() + " : " + p.getSecond() + "\n");
                        fw.write(mixtures.get(p.getFirst()).toString());
                    }
                    fw.write("========================\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        features = mixtures;
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

//    public double getMaxpfreq(String name, Short iType) {
//        String[] words = name.split("\\s+");
//        double p = 0;
//        for (String word : words) {
//            double pw = 0;
//            try {
//                Pair<Double, Double> freqs = features.get("word").get(word).get(iType);
//                pw = (double) freqs.first / freqs.second;
//            } catch (Exception e) {
//                ;
//            }
//            p = Math.max(pw, p);
//        }
//        return p;
//    }

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

    //not using logarithms, since the number of symbols is less
    public double getConditional(String phrase, Short type, FileWriter fw){
        Map<String, Set<String>> tokenFeatures = generateFeatures(phrase, this.features, type);
        double sorg = 0;
        for(String mid: tokenFeatures.keySet()) {
            Double d;
            if(features.get(mid) != null)
                d = features.get(mid).getLikelihood(tokenFeatures.get(mid));
            else
                //a likelihood that assumes nothing
                d = (1.0/MU.WORD_LABELS.length)*(1.0/MU.WORD_LABELS.length)*(1.0/MU.TYPE_LABELS.length)*(1.0/MU.POSITION_LABELS.length);
            try {
                if (fw != null)
                    fw.write("Features for: " + mid + " in " + phrase + ", " + tokenFeatures.get(mid) + " score: " + d + ", type: "+type+"\n");
            }catch(IOException e){
                e.printStackTrace();
            }
            if (Double.isNaN(d))
                log.warn("Cond nan " + mid + ", " + d);
            double val = d;
            if(val>0){
                double freq = 0;
                if(features.get(mid) != null)
                    freq = features.get(mid).getFreq();
                val *= 1+freq;
            }
            sorg += val;//*dictionary.getMarginal(word);
        }
        return sorg;
    }

    //P(tag/x,type);
//    public double getConditional(String word, String patt, Short type){
//        Map<Short,Pair<Double,Double>> pairMap;
//        pairMap = features.get("words").get(patt);
//
//        Pair<Double,Double> p;
//        if(pairMap == null||pairMap.get(type) == null || pairMap.get(type).getSecond()==0)
//            return 0.0;//p = new Pair<>(0.0,0.0);
//        else
//            p = pairMap.get(type);
//        return p.getFirst()/p.getSecond();
//    }

//    //returns smoothed frequency
//    public double getFreq(String patt){
//        //some dummy type;
//        Short type = FeatureDictionary.PERSON;
//        Map<Short,Pair<Double,Double>> pairMap;
//        pairMap = features.get("words").get(patt);
//        Pair<Double,Double> p;
//        if(pairMap == null||pairMap.get(FeatureDictionary.ORGANISATION) == null)
//            p = new Pair<>(0.0,0.0);
//        else
//            p = pairMap.get(type);
//        return p.getSecond();
//    }

    //P(x)
//    public double getMarginal(String word){
//        double n = getFreq(word);
//        return n/((double)numPatts + numPattsWithDuplicates);
//    }

//    @Override
//    public String toString() {
//        String res = "";
//        for (String dim : features.keySet()) {
//            res += "Dim:" + dim + "--";
//            for (String val : features.get(dim).keySet())
//                res += val + ":::" + features.get(dim).get(val) + "---";
//            res += "\n";
//        }
//        return res;
//    }

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
        for(String str: getPatts("The Secular Nation & India"))
            System.err.println(str);
//        try {
//            String baseDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user";
//            Archive archive = SimpleSessions.readArchiveIfPresent(baseDir);
//            List<Document> docs = archive.getAllDocs();
//            for(Document doc: docs)
//                System.err.println(doc.getUniqueId());
//            System.err.println("Num docs: "+docs.size());
//        }catch(IOException e){
//            e.printStackTrace();
//        }
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
