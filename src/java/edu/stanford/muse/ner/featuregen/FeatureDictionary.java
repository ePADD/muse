package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.Config;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
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
 * */
public class FeatureDictionary implements Serializable {
    //The data type of types (Short or String) below has no effect on the size of the dumped serialised model, Of course!
    //public static Short PERSON = 1, ORGANISATION = 2, PLACE = 3, OTHER = -1;
    public static short PERSON=0,COMPANY=1,BUILDING=2,PLACE=3,RIVER=4,ROAD=5,UNIVERSITY=7,MILITARYUNIT=8,
            MOUNTAIN=9,AIRPORT=10,ORGANISATION=11,DRUG=12,NEWSPAPER=13,ACADEMICJOURNAL=14,MAGAZINE=15,POLITICALPARTY=16,
            ISLAND=17,MUSEUM=18,BRIDGE=19,AIRLINE=20,NPORG=21,GOVAGENCY=22,RECORDLABEL=23,SHOPPINGMALL=24,HOSPITAL=25,
            POWERSTATION=26,AWARD=27,TRADEUNIN=28,PARK=29,HOTEL=30,THEATRE=31,LEGISTLATURE=32,LIBRARY=33,LAWFIRM=34,
            MONUMENT=35,OTHER=36;
    public static Short[] allTypes = new Short[]{PERSON,COMPANY,BUILDING,PLACE,RIVER,ROAD,
            UNIVERSITY,MILITARYUNIT,MOUNTAIN,AIRPORT,ORGANISATION,DRUG,NEWSPAPER,ACADEMICJOURNAL,
            MAGAZINE,POLITICALPARTY,ISLAND,MUSEUM,BRIDGE,AIRLINE,NPORG,GOVAGENCY,RECORDLABEL,SHOPPINGMALL,HOSPITAL,
            POWERSTATION,AWARD,TRADEUNIN,PARK,HOTEL,THEATRE,LEGISTLATURE,LIBRARY,LAWFIRM,MONUMENT,OTHER};
    static Log log = LogFactory.getLog(FeatureDictionary.class);
    public static Map<Short, String[]> aTypes = new LinkedHashMap<>();
    public FeatureGenerator[] featureGens = null;
    public static Map<Short, String[]> startMarkersForType = new LinkedHashMap<>();
    public static Map<Short, String[]> endMarkersForType = new LinkedHashMap<>();
    public static List<String> ignoreTypes = new ArrayList<String>();
    //feature types
    public static short NOMINAL = 0, BOOLEAN = 1, NUMERIC = 2;
    public static Pattern endClean = Pattern.compile("^\\W+|\\W+$");
    public static List<String> sws = Arrays.asList("and","for","to","in","at","on","the","of", "a", "an", "is", "from");
    static List<String> symbols = Arrays.asList("&","-",",");
    static final boolean DEBUG = false;
    static {
        //the extra '|' is appended so as not to match junk.
        //matches both Person and PersonFunction in dbpedia types.
//        aTypes.put(FeatureDictionary.PERSON, new String[]{"Person"});
//        aTypes.put(FeatureDictionary.PLACE, new String[]{"Place"});
//        aTypes.put(FeatureDictionary.ORGANISATION, new String[]{"Organisation", "PeriodicalLiterature|WrittenWork|Work"});

        aTypes.put(PLACE, new String[]{"Place"});
        aTypes.put(PERSON, new String[]{"Person"});
        aTypes.put(COMPANY, new String[]{"Company|Organisation"});
        aTypes.put(BUILDING, new String[]{"Building|ArchitecturalStructure|Place"});
        aTypes.put(RIVER, new String[]{"River|Stream|BodyOfWater|NaturalPlace|Place","Canal|Stream|BodyOfWater|NaturalPlace|Place","Stream|BodyOfWater|NaturalPlace|Place","BodyOfWater|NaturalPlace|Place", "Lake|BodyOfWater|NaturalPlace|Place"});
        aTypes.put(ROAD, new String[]{"Road|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place"});
        aTypes.put(UNIVERSITY, new String[]{"University|EducationalInstitution|Organisation","School|EducationalInstitution|Organisation","College|EducationalInstitution|Organisation"});
        aTypes.put(MILITARYUNIT, new String[]{"MilitaryUnit|Organisation"});
        aTypes.put(MOUNTAIN, new String[]{"Mountain|NaturalPlace|Place", "MountainRange|NaturalPlace|Place"});
        aTypes.put(AIRPORT, new String[]{"Airport|Infrastructure|ArchitecturalStructure|Place"});
        aTypes.put(ORGANISATION, new String[]{"Organisation"});
        aTypes.put(DRUG, new String[]{"Drug"});
        aTypes.put(NEWSPAPER, new String[]{"Newspaper|PeriodicalLiterature|WrittenWork|Work"});
        aTypes.put(ACADEMICJOURNAL, new String[]{"AcademicJournal|PeriodicalLiterature|WrittenWork|Work"});
        aTypes.put(MAGAZINE, new String[]{"Magazine|PeriodicalLiterature|WrittenWork|Work"});
        aTypes.put(POLITICALPARTY, new String[]{"PoliticalParty|Organisation"});
        aTypes.put(ISLAND, new String[]{"Island|PopulatedPlace|Place"});
        aTypes.put(MUSEUM, new String[]{"Museum|Building|ArchitecturalStructure|Place"});
        aTypes.put(BRIDGE, new String[]{"Bridge|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place"});
        aTypes.put(AIRLINE, new String[]{"Airline|Company|Organisation"});
        aTypes.put(NPORG, new String[]{"Non-ProfitOrganisation|Organisation"});
        aTypes.put(GOVAGENCY, new String[]{"GovernmentAgency|Organisation"});
        aTypes.put(RECORDLABEL, new String[]{"RecordLabel|Company|Organisation"});
        aTypes.put(SHOPPINGMALL, new String[]{"ShoppingMall|Building|ArchitecturalStructure|Place"});
        aTypes.put(HOSPITAL, new String[]{"Hospital|Building|ArchitecturalStructure|Place"});
        aTypes.put(POWERSTATION, new String[]{"PowerStation|Infrastructure|ArchitecturalStructure|Place"});
        aTypes.put(AWARD, new String[]{"Award"});
        aTypes.put(TRADEUNIN, new String[]{"TradeUnion|Organisation"});
        aTypes.put(PARK, new String[]{"Park|ArchitecturalStructure|Place"});
        aTypes.put(HOTEL, new String[]{"Hotel|Building|ArchitecturalStructure|Place"});
        aTypes.put(THEATRE, new String[]{"Theatre|Building|ArchitecturalStructure|Place"});
        aTypes.put(LEGISTLATURE, new String[]{"Legislature|Organisation"});
        aTypes.put(LIBRARY, new String[]{"Library|EducationalInstitution|Organisation|Agent|Building|ArchitecturalStructure|Place"});
        aTypes.put(LAWFIRM, new String[]{"LawFirm|Company|Organisation"});
        aTypes.put(MONUMENT, new String[]{"Monument|Place"});

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
                "Band|Organisation",
                "OrganisationMember|Person",
                "PersonFunction"
        );
    }
    /**
     * Features for a word that corresponds to a mixture
     * position: SOLO, INSIDE, BEGIN, END
     * number of words: number of words in the phrase, can be any value from 1 to 10
     * left and right labels, LABEL is one of: LOC, ORG,PER, OTHER, NEW, stop words, special chars*/
    public static class MU implements Serializable{
        //the likelihood with the type is also considered
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
        //static String[] WORD_LABELS = new String[]{"OTHER", "LOC", "ORG", "PER", "NULL"};
        //it is useful to have special symbols for position, even though we have NULL symbol in the word labels, so that we dont see symbols like University, Association e.t.c.
        static final long serialVersionUID = 1L;
        static String[] POSITION_LABELS = new String[]{"S","B","I","E"};
        static String[] WORD_LABELS = new String[allTypes.length+1];
        static String[] TYPE_LABELS = new String[allTypes.length];
        static String[] BOOLEAN_VARIABLES = new String[]{"Y","N"};
        static String[] DICT_LABELS = BOOLEAN_VARIABLES,ADJ_LABELS = BOOLEAN_VARIABLES, ADV_LABELS = BOOLEAN_VARIABLES,PREP_LABELS = BOOLEAN_VARIABLES,V_LABELS = BOOLEAN_VARIABLES,PN_LABELS = BOOLEAN_VARIABLES;
        static{
            for(int i=0;i<allTypes.length;i++)
                WORD_LABELS[i] = allTypes[i]+"";
            for(int i=0;i<allTypes.length;i++)
                TYPE_LABELS[i] = allTypes[i]+"";
            WORD_LABELS[WORD_LABELS.length-1] = "NULL";
        }

        //static int NUM_WORDLENGTH_LABELS = 10;
        //feature and the value, for example: <"LEFT: and",200>
        //indicates if the values are final or if they have to be learned
        public Map<String,Double> muVectorPositive;
        //number of times this mixture is probabilistically seen, is summation(gamma*x_k)
        public double numMixture;
        //these are the denominator sums in the case of mu's corresponding to left and right semantic types
        public double nmR, nmL;
        //total number of times, this mixture is considered
        public double numSeen;
        public MU() {
            initialise();
        }

        public static double getMaxEntProb(){
            return (1.0/MU.WORD_LABELS.length)*(1.0/MU.WORD_LABELS.length)*(1.0/MU.TYPE_LABELS.length)*(1.0/MU.POSITION_LABELS.length)*(1.0/MU.ADJ_LABELS.length)*(1.0/MU.ADV_LABELS.length)*(1.0/MU.DICT_LABELS.length)*(1.0/MU.PREP_LABELS.length)*(1.0/MU.V_LABELS.length)*(1.0/MU.PN_LABELS.length);
        }

        //Since we depend on tags of the neighbouring tokens in a big way, we initialise so that the mixture likelihood with type is more precise.
        //and the likelihood with all the other types to be equally likely
        public static MU initialise(String word, Map<Short, Pair<Double,Double>> initialParams){
            //dont perform smoothing here, the likelihood is sometimes set to 0 deliberately for some token with some types
            //performing smoothing, will make the contribution of these params non-zero in some phrases where it should not be and leads to unexpected results.
            double s2 = 0;
            MU mu = new MU();
            for(Short type: initialParams.keySet()) {
                mu.muVectorPositive.put("T:"+type, initialParams.get(type).first);
                s2 = initialParams.get(type).second;
            }
            double n = 0, d = 0;
            for(Pair<Double,Double> p: initialParams.values()) {
                n += p.getFirst();
                d = p.getSecond();
            }
            if(initialParams.size()==0 || n==0 || d==0 || n!=d)
                System.err.println("!!!FATAL!!! mu initialisation improper"+", "+n+", "+d+", "+mu.muVectorPositive+", word: "+word);
            //initially dont assume anything about gammas and pi's
            mu.numMixture = s2;
            mu.numSeen = s2;
            mu.nmR = s2;
            mu.nmL = s2;
            return mu;
        }

        private void initialise(){
            muVectorPositive = new LinkedHashMap<>();
            this.numMixture = 0;
            this.numSeen = 0;
            this.nmL = 0;
            this.nmR = 0;
        }

        //returns P(type/this-mixture)
        public double getLikelihoodWithType(String typeLabel){
            double p1, p2;

            if(numMixture == 0)
                return 0;
            for(String tl: TYPE_LABELS) {
                if(tl.equals(typeLabel)) {
                    if(muVectorPositive.containsKey("T:"+typeLabel)) {
                        p1 = muVectorPositive.get("T:"+typeLabel);
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
            System.err.println("Expected");
            for(String tl: TYPE_LABELS)
                System.err.println(tl);
            return 0;
        }

        public double getLikelihoodWithType(short typeLabel){
            return getLikelihoodWithType(""+typeLabel);
        }

        //gets number of symbols in the dimension represented by this feature
        public static int getNumberOfSymbols(String f){
            if(f.startsWith("L:")||f.startsWith("R:"))
                return WORD_LABELS.length;
            if(f.startsWith("T:"))
                return TYPE_LABELS.length;
            for(String str: POSITION_LABELS)
                if(f.endsWith(str))
                    return POSITION_LABELS.length;
            if(f.startsWith("SW:"))
                return sws.size()+1;
            if(f.startsWith("DICT:")||f.startsWith("ADJ:")||f.startsWith("ADV:")||f.startsWith("PREP:")||f.startsWith("V:")||f.startsWith("PN:")||f.startsWith("POS:"))
                return 2;
            log.error("!!!REALLY FATAL!!! Unknown feature: " + f);
            return 0;
        }

        //features also include the type of the phrase
        //returns the log of P(type,features/this-mixture)
        public double getLikelihood(Set<String> features, FeatureDictionary dictionary) {
            double p = 1.0;
            Set<String> left = new LinkedHashSet<>();
            Set<String> right = new LinkedHashSet<>();
            for (String f : features) {
                if (f.startsWith("L:"))
                    left.add(f);
                if (f.startsWith("R:"))
                    right.add(f);
            }
            Set<String> ts = new LinkedHashSet<>();
            for (Short at : allTypes) {
                ts.add(at + "");
            }
            ts.add("NULL");
            boolean smooth = true;
            if (muVectorPositive.size()==TYPE_LABELS.length)
                smooth = true;
            int si=0;
            for (Set<String> strs : new Set[]{left, right}) {
                si++;
                for (String l : strs) {
                    double s = 0;
                    Map<String,Pair<Double,Double>> ls = new LinkedHashMap<>();
                    for (String t : ts) {
                        Double v;
                        double denom;
                        if(si==1) {
                            v = muVectorPositive.get("L:" + t);
                            denom = nmL;
                        }
                        else {
                            v = muVectorPositive.get("R:" + t);
                            denom = nmR;
                        }

                        if (numMixture == 0)
                            continue;
                        if(v==null)
                            v=0.0;
                        if(!smooth) {
                            s += dictionary.getConditionalOfWordWithType(l, t) * (v / denom);
                            ls.put(t,new Pair<>(dictionary.getConditionalOfWordWithType(l, t), (v / denom)));
                        }
                        else {
                            s += dictionary.getConditionalOfWordWithType(l, t) * ((v + 1) / (denom + allTypes.length + 1));
                            ls.put(t,new Pair<>(dictionary.getConditionalOfWordWithType(l, t), (v + 1) / (denom + allTypes.length + 1)));
                        }
                        //System.err.println(dictionary.getConditionalOfWordWithType(l, t)+", "+v+","+numMixture);
                    }
                    if(DEBUG) {
                        log.info("Scoring: " + features+"- score: "+s);
                        for (String t : ls.keySet())
                            log.info(t + ": <" + ls.get(t).first+"-"+ls.get(t).second+">");
                    }
                    p *= Math.pow(s, 1.0 / strs.size());
                }
            }
            //System.err.println(numMixture+", s: "+s);

            for (String f : features) {
                int v = getNumberOfSymbols(f);
                smooth = false;
                //does not want to smooth, if the feature is position label
                //also dont smooth if the feature is a type related feature
                //TODO: This way of checking the feature type is pathetic, improve this
                //it creates problem if there is smoothing for certain types and not for other types
//                if(f.startsWith("L:") || f.startsWith("R:"))
//                    smooth = true;

                if (f.startsWith("L:") || f.startsWith("R:")) {
                    //should be specially handled
                    continue;
                }
                //k, think before you change something related to the smoothing, I have done enough code dance here.
                //enabling smoothing will dilute everything, and produce undesired results, for example: New York Times may be recognised as York Times, since in the former case New and York contribute some score and make it a location name.
                //if some token is prominently appearing in many types, then the type affinities should automatically be smoothed out.
                //also, we dont want to make any judgement about unseen tokens, hence smoothing only in that case
                if (f.equals("L:-2") || f.equals("R:-2"))
                    smooth = true;
                //should not smooth type related mu params even initially
                //TODO: set proper condition for smoothing
                //just after initialisation, in this case the should not assign 0 mass for unseen observations
                if (muVectorPositive.size() == TYPE_LABELS.length)
                    smooth = true;

                if (!muVectorPositive.containsKey(f)) {
                    //no smoothing in the case of position label
                    if (!smooth)
                        p *= 0;
                    else
                        //System.err.println("!!!FATAL!!! Unknown feature: "+f);
                        p *= 1.0 / v;
                    //System.err.println("!f: "+f+", "+muVectorPositive.get(f)+" : "+numMixture+", "+v);
                    continue;
                }
                double val;
                if (smooth)
                    val = (muVectorPositive.get(f) + 1) / (numMixture + v);
                else if (numMixture > 0)
                    val = (muVectorPositive.get(f)) / (numMixture);
                else
                    val = 0;
                //System.err.println("f: "+f+", "+muVectorPositive.get(f)+" : "+numMixture+", "+val+", "+muVectorPositive.size()+", "+TYPE_LABELS.length+", "+smooth);

                if (Double.isNaN(val)) {
                    log.warn("Found a nan here: " + f + " " + muVectorPositive.get(f) + ", " + numMixture + ", " + val);
                    log.warn(toString());
                }
//                if(val == 0)
//                    System.err.println(f+","+muVectorPositive.get(f));
                p *= val;
            }
            //System.err.println("Score: "+p+", "+features);
            return p;
        }

        //where N is the total number of observations, for normalization
        public double getPrior(){
            if(numSeen == 0)
                System.err.println("FATAL!!! Number of times this mixture is seen is zero, that cant be true!!!");
            //two symbols here SEEN and UNSEEN, hence the smoothing
            return (numMixture+1)/(numSeen+2);
        }

        /**Maximization step in EM update, update under the assumption that add will be called only if the evidence for the corresponding mixture is seen, i.e. xk!=0
         * @param resp - responsibility of this mixture in explaining the type and features
         * @param features - set of all *relevant* features to this mixture*/
        public void add(double resp, Set<String> features, FeatureDictionary dictionary) {
            //if learn is set to false, ignore all the observations
            if (Double.isNaN(resp))
                log.warn("Responsibility is nan for: " + features);
            numMixture += resp;
            numSeen += 1;
            for (String f : features) {
                if(!f.startsWith("L:") && !f.startsWith("R:")) {
                    if (!muVectorPositive.containsKey(f)) {
                        muVectorPositive.put(f, 0.0);
                    }
                    muVectorPositive.put(f, muVectorPositive.get(f) + resp);
                }
            }

            //System.err.println("Features: "+features);
            Set<String> left = new LinkedHashSet<>(), right = new LinkedHashSet<>();
            for(String f: features) {
                if (f.startsWith("L:"))
                    left.add(f);
                else if(f.startsWith("R:"))
                    right.add(f);
            }
            Set<String> ts = new LinkedHashSet<>();
            for(Short at: allTypes)
                ts.add(at+"");
            ts.add("NULL");
            //selct top types for every left and right word, trying to populate fields for every type blows up the space requirement
            int MAX = 2;
            for(String l: left){
                Map<String,Double> ltop = new LinkedHashMap<>();
                for(String t: ts)
                    ltop.put(t, dictionary.getConditionalOfWordWithType(l, t));

                List<Pair<String,Double>> temp = Util.sortMapByValue(ltop);
                if(DEBUG)
                    log.info("Left temp size: "+temp.size());
                for(int i=0;i<Math.min(MAX,temp.size());i++){
                    String t = temp.get(i).first;
                    if(!muVectorPositive.containsKey("L:"+t))
                        muVectorPositive.put("L:"+t,0.0);
                    muVectorPositive.put("L:" + t, muVectorPositive.get("L:" + t) + (resp * ltop.get(t) / left.size()));
                    nmL += (resp * ltop.get(t) / left.size());
                    //System.err.println("Adding: L:"+t+", "+resp+", "+ltop.get(t)+", "+Math.min(temp.size(),MAX));
                    if(DEBUG)
                        log.info("Adding left type: "+t+" for "+l);
                }
            }

            for(String r: right){
                Map<String,Double> rtop = new LinkedHashMap<>();
                for(String t: ts)
                    rtop.put(t, dictionary.getConditionalOfWordWithType(r, t));

                List<Pair<String,Double>> temp = Util.sortMapByValue(rtop);
                if(DEBUG)
                    log.info("Right temp size: "+temp.size());
                for(int i=0;i<Math.min(MAX,temp.size());i++){
                    String t = temp.get(i).first;
                    if(!muVectorPositive.containsKey("R:"+t))
                        muVectorPositive.put("R:"+t,0.0);
                    muVectorPositive.put("R:" + t, muVectorPositive.get("R:" + t) + (resp * rtop.get(t) / right.size()));
                    //NMR update can be outside the loop as marginal of conditional word with type sums to 0, having it inside the loop will expose any problems in the normlisation
                    nmR += (resp * rtop.get(t) / right.size());
                    //System.err.println("Word: "+r+", Type: "+t+", "+resp+", "+rtop.get(t)+", "+temp.size());
                    if(DEBUG)
                        log.info("Adding right type: "+t+" for "+r);
                }
                //System.err.println("-----");
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
            String p[] = new String[]{"L:","R:","PL:","T:","SW:","DICT:","ADJ:","ADV:","PREP:","V:","PN:"};
            String[][] labels = new String[][]{WORD_LABELS,WORD_LABELS,POSITION_LABELS,TYPE_LABELS,sws.toArray(new String[sws.size()]),DICT_LABELS,ADJ_LABELS, ADV_LABELS,PREP_LABELS,V_LABELS,PN_LABELS};
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
            str += "NM:"+numMixture+", NS:"+numSeen+"\n";
            str += "NMR:"+nmR+", NML:"+nmL+"\n";
            return str;
        }
    }
    private static final long serialVersionUID = 1L;
    //dimension -> instance -> entity type of interest -> #positive type, #negative type
    //patt -> Aa -> 34 100, pattern Aa occurred 34 times with positive classes of the 100 times overall.
    //mixtures of the BMM model
    public Map<String, MU> features = new LinkedHashMap<>();
    //priors over every type label, computed by P(t) = \sum\limits_{w} P(w)*P(t/w)
    public Map<Short, Double> typePriors = new LinkedHashMap<>();
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
        log.info("Initialising MUs");

        Map<String, Map<Short, Pair<Double,Double>>> words = lfeatures.get("words");
        int wi=0, ws = words.size();
        for(String str: words.keySet()){
            Map<Short, Pair<Double,Double>> priors = new LinkedHashMap<>();
            for(Short type: words.get(str).keySet()){
                Pair<Double,Double> p = words.get(str).get(type);
                priors.put(type, p);
            }
            if(DEBUG) {
                String ds = "";
                for(Short t: priors.keySet())
                    ds+=t+"<"+priors.get(t).first+","+priors.get(t).second+"> ";
                log.info("Initialising: " + str + " with " + ds);
            }
            features.put(str, MU.initialise(str, priors));
            if(wi++%1000 == 0)
                log.info("Done: "+wi+"/"+ws);
        }

        return this;
    }

    //returns P(w/type) = P(type/w)*P(w)/P(type)
    public double getConditionalOfWordWithType(String word, String type){
        if(word.startsWith("L:")||word.startsWith("R:"))
            word = word.substring(2);
        //base case
        if(word.equals("NULL") || type.equals("NULL")) {
            if (word.equals("NULL") && type.equals("NULL"))
                return 1.0;
            else
                return 0.0;
        }

        MU mu = features.get(word);
        Short t = Short.parseShort(type);
        if(typePriors.get(t)==null || typePriors.get(t)==0) {
            if(DEBUG && typePriors.get(t)==null)
                log.warn("!!!FATAL!!! Unknown type or type priors not computed: Type priors null? "+(typePriors==null)+", Type: "+t);
            return 0;
        }

        //smoothing is very important, there are two facets to it
        //by adding some numbers to bothe numerator and denominator, we are encouraging ratios with higher components than just the ratio
        //when the values are smoothed, the values are virually clamped and will be swayed only if a good evidence is found
         if(mu!=null) {
            //System.err.println("Returning: "+((1 + mu.numSeen) / (N + features.size())) * mu.getLikelihoodWithType(Short.parseShort(type)));
                //System.err.println(t+", "+typePriors.get(t)+","+typePriors.keySet());
            return mu.getLikelihoodWithType(t) / typePriors.get(t);
        }
        else {
            if(DEBUG)
                log.info("Unknown word: "+word+", returning best guess");
            return (1.0 / allTypes.length);
        }
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
                        //System.err.println("Putting: "+val);
                    }
                    Pair<Double, Double> p = hm.get(val).get(iType);
                    Short eType = codeType(type);
                    //System.err.println("Type info: "+eType+", "+iType+", "+type);
                    if(eType == iType)
                        p.first++;
                    p.second++;
                    hm.get(val).put(iType, p);
                }
            lfeatures.put(dim, hm);
        }
    }

    //codes a given DBpedia type into type coding of this class
    public static Short codeType(String type){
        Short ct = FeatureDictionary.OTHER;
        String[] fs = type.split("\\|");
        outer:
        for(int ti=0;ti<fs.length;ti++) {
            String st = "";
            for(int tj=ti;tj<fs.length;tj++) {
                st += fs[tj];
                if(tj<fs.length-1)
                    st+="|";
            }
            for (Short t : allTypes) {
                String[] allowT = FeatureDictionary.aTypes.get(t);
                if (allowT != null)
                    for (String at : allowT)
                        if (st.equals(at)) {
                            ct = t;
                            break outer;
                        }
            }
        }
        return ct;
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
            //do not emit stop words
            //canonicalize the word
            word = word.toLowerCase();
            if(sws.contains(word) || symbols.contains(word)){
                continue;
            }
            //some DBpedia entries end in ',' as in Austin,_Texas
            String t = word;
            t = endClean.matcher(t).replaceAll("");
            if(i>0 && (sws.contains(words[i-1].toLowerCase())||symbols.contains(words[i-1].toLowerCase())))
                t = words[i-1].toLowerCase()+" "+t;
            if(i<(words.length-1) && (sws.contains(words[i+1].toLowerCase())||(symbols.contains(words[i+1].toLowerCase()))))
                t += " "+words[i+1].toLowerCase();

            //emit all the words or patterns
            if (t != null)
                patts.add(t);
        }
        return patts.toArray(new String[patts.size()]);
    }

    //there is no point emitting OTHER label, since the constraint is too broad, leads to noise
    public Pair<String,Double> getLabel(String word, Map<String, MU> mixtures){
        if(word == null)
            return new Pair<>("NULL",1.0);
        word = word.toLowerCase();
        //this can happen, since we ignore a few types
        //this label cannot be the same as OTHER, these are NEW or previously unseen tokens.
        if(mixtures.get(word) == null)
            return new Pair<>(""+(-2),1.0);
        String bl = "-2";
        double bs =-1;
        //Have to be extremely careful about OTHER symbol
        for(String tl: MU.TYPE_LABELS) {
            if(tl.equals(""+FeatureDictionary.OTHER))
                continue;
            double s = mixtures.get(word).getLikelihoodWithType(tl);
            bs = Math.max(s, bs);
            if(s==bs)
                bl=tl;
        }
        return new Pair<>(bl, 1.0);
    }

    /**
     * TODO: move this to an appropriate place
     * A generative function, that takes the phrase and generates features.
     * requires mixtures as a parameter, because some of the features depend on this
     * returns a map of mixture identity to the set of features relevant to the mixture
     * Map<String,Double> because features sometimes are associated with a score, for semantic type, get label to be precise</>*/
    public Map<String,Set<String>> generateFeatures(String phrase, Short type){
        Map<String, Set<String>> mixtureFeatures = new LinkedHashMap<>();
        String[] patts = getPatts(phrase);
        String[] words = phrase.split("\\s+");
        String sw = "NULL";

        for(int wi=0;wi<words.length;wi++){
            String word = words[wi];
            //Generally entries contain only one stop word per phrase, so not bothering which one
            //index>0 check to avoid considering 'A' and 'The' in the beginning
            if(wi>0 && sws.contains(word)) {
                sw = word;
                break;
            }

        }
        if(patts.length == 0)
            return mixtureFeatures;
        boolean containsPOS = false;
        if(phrase.contains("'s "))
            containsPOS = true;

        for(int wi = 0; wi<patts.length; wi++){
            if(sws.contains(patts[wi].toLowerCase()))
                continue;
            Set<String> features = new LinkedHashSet<>();

            String posLabel;
            if(wi==0) {
                if (patts.length == 1)
                    posLabel = "S";
                else
                    posLabel = "B";
            }
            else if(wi>0 && wi<(patts.length-1)){
                posLabel = "I";
            }
            else //if(wi == (words.length-1))
                posLabel = "E";
            String nwlabel = patts.length+"";
            for(int wj=0;wj<wi;wj++)
                features.add("L:"+patts[wj]);
            if(wi==0)
                features.add("L:NULL");
            for(int wj=wi+1;wj<patts.length;wj++)
                features.add("R:"+patts[wj]);
            if(wi+1 == patts.length)
                features.add("R:NULL");

            features.add("PL:" + posLabel);
            //the stop word that appeared in this string

            features.add("SW:" + sw);
            //features.add("WL:"+nwlabel);
            features.add("T:" + type);
            boolean containsAdj = false, containsAdv = false, containsVerb = false, containsPrep = false, containsPronoun = false, containsDict = false;
            for(String word: words) {
                //consider all the other words, other than this word
                if(!sws.contains(word) && !patts[wi].equals(word) && !patts[wi].contains(" "+word) && !patts[wi].contains(word+" ")) {
                    word  = word.toLowerCase();
                    if(EnglishDictionary.getTopAdjectives().contains(word))
                        containsAdj = true;
                    if(EnglishDictionary.getTopAdverbs().contains(word))
                        containsAdv = true;
                    if(EnglishDictionary.getTopPrepositions().contains(word))
                        containsPrep = true;
                    if(EnglishDictionary.getTopVerbs().contains(word))
                        containsVerb = true;
                    if(EnglishDictionary.getTopPronouns().contains(word))
                        containsPronoun = true;
                    if(EnglishDictionary.getDict().contains(word))
                        containsDict = true;
                }
            }
            if(containsDict)
                features.add("DICT:Y");
            else
                features.add("DICT:N");
            if(containsAdj)
                features.add("ADJ:Y");
            else
                features.add("ADJ:N");
            if(containsAdv)
                features.add("ADV:Y");
            else
                features.add("ADV:N");
            if(containsPrep)
                features.add("PREP:Y");
            else
                features.add("PREP:N");
            if(containsVerb)
                features.add("V:Y");
            else
                features.add("V:N");
            if(containsPronoun)
                features.add("PN:Y");
            else features.add("PN:N");
            if(containsPOS)
                features.add("POS:Y");
            else
                features.add("POS:N");

            mixtureFeatures.put(patts[wi].toLowerCase(), features);
        }
        return mixtureFeatures;
    }

    public void computeTypePriors(){
        typePriors = new LinkedHashMap<>();
//        N = 0;
//        for(String w: features.keySet())
//            N += features.get(w).numSeen;

        for(Short at: allTypes)
            typePriors.put(at, 1.0);
//        for(String w: features.keySet()){
//            for(Short at: allTypes) {
//                double t = 0;
//                if(features.get(w).muVectorPositive.get("T:"+at)!=null && features.get(w).numMixture>0) {
//                    t = features.get(w).muVectorPositive.get("T:"+at) / features.get(w).numMixture;
//                    //System.err.println("T: "+t+", "+features.get(w).numSeen/N+", "+features.get(w).muVectorPositive.get("T:"+at)/features.get(w).numMixture);
//                }
//                //t *= features.get(w).numSeen/N;
//                if(!typePriors.containsKey(at))
//                    typePriors.put(at, 0.0);
//                typePriors.put(at,typePriors.get(at)+t);
//            }
//        }
        if(DEBUG) {
            log.info("Type priors: ");
            for (short t : typePriors.keySet())
                log.info(t + " " + typePriors.get(t) + "\n");
        }
    }

    public void EM(Map<String,String> gazettes){
        System.err.println("Performing EM on: #"+features.size()+" words");

        Map<String, MU> revisedMixtures = new LinkedHashMap<>();
        int MAX_ITER = 3;
        int N = gazettes.size();
        int wi;
        for(int i=0;i<MAX_ITER;i++) {
            wi = 0;
            computeTypePriors();

            for (String phrase : gazettes.keySet()) {
                if (wi++ % 1000 == 0)
                    System.err.println("EM iter: " + i + ", " + wi + "/" + N);
                String type = gazettes.get(phrase);
                Short coarseType = codeType(type);
                double z = 0;
                //responsibilities
                Map<String, Double> gamma = new LinkedHashMap<>();
                //Word (sort of mixture identity) -> Features
                Map<String, Set<String>> wfeatures = generateFeatures(phrase, coarseType);
                //System.err.println(" ---- ");
                for (String mi : wfeatures.keySet()) {
                    //this should not even happen
                    if (wfeatures.get(mi) == null) {
                        continue;
                    }
                    MU mu = features.get(mi);
                    if(mu == null)
                        System.err.println("mu null for: " + mi + ", " + features.size());

                    double d = mu.getLikelihood(wfeatures.get(mi), this) * mu.getPrior();
                    //only two tags, org/non-org
                    //by N cancels out when we normalize
                    //double d = (allowed?pfreq:(1-pfreq)) * (p.getSecond()+2);
                    if(Double.isNaN(d))
                        System.err.println("score for: " + mi + " "+ wfeatures.get(mi) + " " + d);
                    gamma.put(mi, d);
                    z += d;
                    //System.err.println(mi + " : " + d + ", "+mu.getLikelihood(wfeatures.get(mi), this) );
                }
                if (z == 0) {
                    log.warn("!!!FATAL!!! Skipping: " + phrase + " as none took responsibility");
                    continue;
                }

                for (String g : gamma.keySet())
                    gamma.put(g, gamma.get(g) / z);

                if(DEBUG){
                    for(String mi: wfeatures.keySet()) {
                        log.info("MI:" + mi + ", " + gamma.get(mi) + ", " + wfeatures.get(mi));
                        log.info(features.get(mi).toString());
                    }
                    log.info("EM iter: "+i+", "+phrase+", "+type+", ct: "+coarseType);
                    log.info("-----");
                }

                for (String g : gamma.keySet()) {
                    if (!revisedMixtures.containsKey(g)) {
                        revisedMixtures.put(g, new MU());
                    }

                    if (Double.isNaN(gamma.get(g)))
                        System.err.println("Gamma: " + gamma.get(g) + ", " + g);
                    if(DEBUG)
                        if(gamma.get(g) == 0)
                            log.warn("!! Resp: " + 0 + " for "+g+" in "+phrase+", "+gazettes.get(phrase));
                    revisedMixtures.get(g).add(gamma.get(g), wfeatures.get(g), this);
                }
            }
            double change = 0;
            for (String mi : features.keySet())
                if (revisedMixtures.containsKey(mi))
                    change += revisedMixtures.get(mi).difference(features.get(mi));
            log.info("Iter: " + i + ", change: " + change);
            features = revisedMixtures;
            revisedMixtures = new LinkedHashMap<>();

            try {
                if(i==(MAX_ITER-1)) {
                    Short[] ats = FeatureDictionary.allTypes;//= new Short[]{FeatureDictionary.AIRLINE,FeatureDictionary.MILITARYUNIT,FeatureDictionary.PERSON, FeatureDictionary.GOVAGENCY, FeatureDictionary.UNIVERSITY};
                    for (Short type : ats) {
                        FileWriter fw = new FileWriter(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "em.dump." + type + "." + i);
                        Map<String, Double> some = new LinkedHashMap<>();
                        for (String w : features.keySet()) {
                            double v = features.get(w).getLikelihoodWithType(type) * Math.log(features.get(w).numMixture);
                            if (Double.isNaN(v))
                                some.put(w, 0.0);
                            else
                                some.put(w, v);
                        }
                        List<Pair<String, Double>> ps = Util.sortMapByValue(some);
                        for (Pair<String, Double> p : ps) {
                            if(type!=ats[0])
                                if(p.second<=0.01)
                                    break;
                            fw.write("Token: " + p.getFirst() + " : " + p.getSecond() + "\n");
                            fw.write(features.get(p.getFirst()).toString());
                            fw.write("========================\n");
                        }
                        fw.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        Map<String, Set<String>> tokenFeatures = generateFeatures(phrase, type);
        double sorg = 0;
        for(String mid: tokenFeatures.keySet()) {
            Double d;
            if(features.get(mid) != null)
                d = features.get(mid).getLikelihood(tokenFeatures.get(mid), this);
            else
                //a likelihood that assumes nothing
                d = 0.0;//(1.0/MU.WORD_LABELS.length)*(1.0/MU.WORD_LABELS.length)*(1.0/MU.TYPE_LABELS.length)*(1.0/MU.POSITION_LABELS.length)*(1.0/MU.ADJ_LABELS.length)*(1.0/MU.ADV_LABELS.length)*(1.0/MU.DICT_LABELS.length)*(1.0/MU.PREP_LABELS.length)*(1.0/MU.V_LABELS.length)*(1.0/MU.PN_LABELS.length);
//            try {
//                if (fw != null)
//                    fw.write("Features for: " + mid + " in " + phrase + ", " + tokenFeatures.get(mid) + " score: " + d + ", type: "+type+"\n");
//            }catch(IOException e){
//                e.printStackTrace();
//            }
            if (Double.isNaN(d))
                log.warn("Cond nan " + mid + ", " + d);
            double val = d;
            if(val>0){
                double freq = 0;
                if(features.get(mid) != null)
                    freq = features.get(mid).getPrior();
                val *= freq;
            }
            sorg += val;//*dictionary.getMarginal(word);
        }
        return sorg;
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
        System.err.println(codeType("Hospital|Building|ArchitecturalStructure|Place"));
    }
}
