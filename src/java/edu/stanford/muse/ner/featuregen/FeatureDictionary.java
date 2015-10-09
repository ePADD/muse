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
 * We know the
 * */
public class FeatureDictionary implements Serializable {
    //The data type of types (Short or String) below has no effect on the size of the dumped serialised model, Of course!
    //public static Short PERSON = 1, ORGANISATION = 2, PLACE = 3, OTHER = -1;
    public static short PERSON=0,COMPANY=1,BUILDING=2,PLACE=3,RIVER=4,ROAD=5,SPORTSTEAM=6,UNIVERSITY=7,MILITARYUNIT=8,
            MOUNTAIN=9,AIRPORT=10,ORGANISATION=11,DRUG=12,NEWSPAPER=13,ACADEMICJOURNAL=14,MAGAZINE=15,POLITICALPARTY=16,
            ISLAND=17,MUSEUM=18,BRIDGE=19,AIRLINE=20,NPORG=21,GOVAGENCY=22,RECORDLABEL=23,SHOPPINGMALL=24,HOSPITAL=25,
            POWERSTATION=26,AWARD=27,TRADEUNIN=28,PARK=29,HOTEL=30,THEATRE=31,LEGISTLATURE=32,LIBRARY=33,LAWFIRM=34,
            MONUMENT=35,OTHER=36;
    public static Short[] allTypes = new Short[]{PERSON,COMPANY,BUILDING,PLACE,RIVER,ROAD,SPORTSTEAM,
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
    public static List<String> sws = Arrays.asList("and","for","to","in","at","on","the","of", "a", "an", "is");
    static List<String> symbols = Arrays.asList("&","-",",");
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
        aTypes.put(SPORTSTEAM, new String[]{"SportsTeam|Organisation"});
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
        //TODO: nothings getting assigned to this type, take a look
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
        //aTypes.put("RailwayLine|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place",

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
        Map<String,Double> muVectorPositive;
        //number of times this mixture is probabilistically seen, is summation(gamma*x_k)
        public double numMixture;
        //total number of times, this mixture is considered
        public double numSeen;
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
                mu.muVectorPositive.put("T:"+type, initialParams.get(type).first);
                s1 += initialParams.get(type).first;
                s2 = initialParams.get(type).second;
            }
            //initially dont assume anything about gammas and pi's
            mu.numMixture = s2;
            mu.numSeen = s2;
            return mu;
        }

        private void initialise(){
            muVectorPositive = new LinkedHashMap<>();
            this.numMixture = 0;
            this.numSeen = 0;
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
        public double getLikelihood(Map<String,Double> features){
            double p = 1.0;
            for(String f: features.keySet()){
                int v = getNumberOfSymbols(f);
                boolean smooth = false;
                //does not want to smooth, if the feature is position label
                //also dont smooth if the feature is a type related feature
                //TODO: This way of checking the feature type is pathetic, improve this
                //it creates problem if there is smoothing for certain types and not for other types
//                if(f.startsWith("L:") || f.startsWith("R:"))
//                    smooth = true;

                //k, think before you change something related to the smoothing, I have done enough code dance here.
                //enabling smoothing will dilute everything, and produce undesired results, for example: New York Times may be recognised as York Times, since in the former case New and York contribute some score and make it a location name.
                //if something token is prominently appearing in many types, then the type affinities should automatically be smoothed out.
                //also, we dont want to make any judgement about unseen tokens, hence smoothing only in that case
                if(f.equals("L:-2") || f.equals("R:-2"))
                    smooth = true;
                //should not smooth type related mu params even initially
                //TODO: set proper condition for smoothing
                //just after initialisation, in this case the should not assign 0 mass for unseen observations
                if (muVectorPositive.size()==TYPE_LABELS.length)
                    smooth = true;

                p *= features.get(f);
                //System.err.println("p: "+p+", "+features.get(f));
                if(!muVectorPositive.containsKey(f)){
                    //no smoothing in the case of position label
                    if(!smooth)
                        p *= 0;
                    else {
                        //System.err.println("!!!FATAL!!! Unknown feature: "+f);
                        p *= 1.0/v;
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
                //System.err.println("f: "+f+", "+muVectorPositive.get(f)+" : "+numMixture+", "+val+", "+muVectorPositive.size()+", "+TYPE_LABELS.length+", "+smooth);

                if(Double.isNaN(val)) {
                    log.warn("Found a nan here: " + f + " " + muVectorPositive.get(f) + ", " + numMixture + ", " + val);
                    log.warn(toString());
                }
                p *= val;
            }
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
        public void add(double resp, Map<String,Double> features) {
            //if learn is set to false, ignore all the observations
            if (Double.isNaN(resp))
                log.warn("Responsibility is nan for: " + features);
            numMixture += resp;
            numSeen += 1;
            for (String f : features.keySet()) {
                if (!muVectorPositive.containsKey(f)) {
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
            Short ht = null;
            //Trying to assign ht is just pathetic, we know that these are full matches and gazette lookup will solve things,
            //Setting ht and setting more appropriate type initially is not helping anyway, all this gets lost in EM (if smoothing of the type is enabled)
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
                    Short eType = codeType(type);
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
        return new Pair<>(bl, bs);
    }

    /**
     * TODO: move this to an appropriate place
     * A generative function, that takes the phrase and generates features.
     * requires mixtures as a parameter, because some of the features depend on this
     * returns a map of mixture identity to the set of features relevant to the mixture
     * Map<String,Double> because features sometimes are associated with a score, for semantic type, get label to be precise</>*/
    public Map<String,Map<String,Double>> generateFeatures(String phrase, Map<String, MU> mixtures, Short type){
        Map<String, Map<String,Double>> mixtureFeatures = new LinkedHashMap<>();
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

        String fw = patts[0];
        String lw = patts[patts.length-1];
        for(int wi = 0; wi<patts.length; wi++){
            if(sws.contains(patts[wi].toLowerCase()))
                continue;
            Map<String,Double> features = new LinkedHashMap<>();
            String prevWord = null, nxtWord = null;
            prevWord = fw;
            nxtWord = lw;
            if(wi == 0)
                prevWord = null;
            if(wi == (patts.length-1))
                nxtWord = null;

            Pair<String,Double> pp = getLabel(prevWord, mixtures);
            Pair<String,Double> np = getLabel(nxtWord, mixtures);

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
            features.put("L:" + pp.first, pp.second);
            features.put("R:" + np.first, np.second);
            features.put("PL:"+ posLabel, 1.0);
            //the stop word that appeared in this string

            features.put("SW:" + sw, 1.0);
            //features.add("WL:"+nwlabel);
            features.put("T:" + type, 1.0);
            boolean containsAdj = false, containsAdv = false, containsVerb = false, containsPrep = false, containsPronoun = false, containsDict = false;
            for(String word: words) {
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
                features.put("DICT:Y", 1.0);
            else
                features.put("DICT:N", 1.0);
            if(containsAdj)
                features.put("ADJ:Y", 1.0);
            else
                features.put("ADJ:N",1.0);
            if(containsAdv)
                features.put("ADV:Y", 1.0);
            else
                features.put("ADV:N",1.0);
            if(containsPrep)
                features.put("PREP:Y", 1.0);
            else
                features.put("PREP:N",1.0);
            if(containsVerb)
                features.put("V:Y", 1.0);
            else
                features.put("V:N",1.0);
            if(containsPronoun)
                features.put("PN:Y", 1.0);
            else features.put("PN:N", 1.0);
            if(containsPOS)
                features.put("POS:Y",1.0);
            else
                features.put("POS:N",1.0);

            mixtureFeatures.put(patts[wi].toLowerCase(), features);
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
                Short coarseType = codeType(type);

                double z = 0;
                //responsibilities
                Map<String, Double> gamma = new LinkedHashMap<>();
                //Word (sort of mixture identity) -> Features
                Map<String, Map<String,Double>> featureMap = generateFeatures(phrase, mixtures, coarseType);
                for (String mi : featureMap.keySet()) {
                    //this should not even happen
                    if (mixtures.get(mi) == null) {
                        //System.err.println("Did not find mixture for: "+ mi +" "+iType);
                        continue;
                    }
                    MU mu = mixtures.get(mi);
                    double d = mu.getLikelihood(featureMap.get(mi)) * mu.getPrior();
                    //only two tags, org/non-org
                    //by N cancels out whe we normalize
                    //double d = (allowed?pfreq:(1-pfreq)) * (p.getSecond()+2);
                    if(Double.isNaN(d))
                        System.err.println("score for: " + mi + " "+ featureMap.get(mi) + " " + d);
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
                if(i==(MAX_ITER-1)) {
                    Short[] ats = FeatureDictionary.allTypes;//= new Short[]{FeatureDictionary.AIRLINE,FeatureDictionary.MILITARYUNIT,FeatureDictionary.PERSON, FeatureDictionary.GOVAGENCY, FeatureDictionary.UNIVERSITY};
                    for (Short type : ats) {
                        FileWriter fw = new FileWriter(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "em.dump." + type + "." + i);
                        Map<String, Double> some = new LinkedHashMap<>();
                        for (String w : mixtures.keySet()) {
                            double v = mixtures.get(w).getLikelihoodWithType(type) * Math.log(mixtures.get(w).numMixture);
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
                            fw.write(mixtures.get(p.getFirst()).toString());
                            fw.write("========================\n");
                        }
                        fw.close();
                    }
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
        Map<String, Map<String,Double>> tokenFeatures = generateFeatures(phrase, this.features, type);
        double sorg = 0;
        for(String mid: tokenFeatures.keySet()) {
            Double d;
            if(features.get(mid) != null)
                d = features.get(mid).getLikelihood(tokenFeatures.get(mid));
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
