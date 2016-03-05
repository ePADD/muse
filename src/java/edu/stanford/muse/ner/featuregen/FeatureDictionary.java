package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.Config;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.model.SequenceModel;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;

import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FeatureDictionary implements Serializable {
    //The data type of types (Short or String) below has no effect on the size of the dumped serialised model, Of course!
    //public static Short PERSON = 1, ORGANISATION = 2, PLACE = 3, OTHER = -1;
    //merged political party into organisation
    public static short PERSON=0,COMPANY=1,BUILDING=2,PLACE=3,RIVER=4,ROAD=5,UNIVERSITY=7,
            MOUNTAIN=9,AIRPORT=10,ORGANISATION=11,PERIODICAL_LITERATURE=13,
            ISLAND=17,MUSEUM=18,BRIDGE=19,AIRLINE=20,GOVAGENCY=22,HOSPITAL=25,
            AWARD=27,THEATRE=31,LEGISTLATURE=32,LIBRARY=33,LAWFIRM=34,
            MONUMENT=35,DISEASE = 36,EVENT=37, OTHER=38;
    //we assign this type to a token that is not seen before and hence its type unknown
    public static short UNKNOWN_TYPE = -10;
    public static Short[] allTypes = new Short[]{PERSON,COMPANY,BUILDING,PLACE,RIVER,ROAD,
            UNIVERSITY,MOUNTAIN,AIRPORT,ORGANISATION,PERIODICAL_LITERATURE,
            ISLAND,MUSEUM,BRIDGE,AIRLINE,GOVAGENCY,HOSPITAL,
            AWARD,THEATRE,LEGISTLATURE,LIBRARY,LAWFIRM,MONUMENT,DISEASE,EVENT,OTHER};

    public static Map<Short,String> desc = new LinkedHashMap<>();
    public static Map<Short, List<Short>> mappings = new LinkedHashMap<>();
    static Log log = LogFactory.getLog(FeatureDictionary.class);
    //DBpedia type mapping of the types this can handle
    public static Map<Short, String[]> aTypes = new LinkedHashMap<>();
    public static List<String> ignoreTypes = new ArrayList<>();
    static Pattern endClean = Pattern.compile("^\\W+|\\W+$");
    public static List<String> sws = Arrays.asList("and","for","to","in","at","on","the","of", "a", "an", "is", "from",
            "de", "van","von","da","ibn","mac","bin","del","dos","di","la","du","ben","ap","le","bint","do");
    static List<String> symbols = Arrays.asList("&","-",",");
    static final boolean DEBUG = false;
    static {
        //the extra '|' is appended so as not to match junk.
        //matches both Person and PersonFunction in dbpedia types.
        aTypes.put(PERSON, new String[]{"Person", "Agent"});
        aTypes.put(PLACE, new String[]{"Place", "Park|Place", "ProtectedArea|Place", "PowerStation|Infrastructure|ArchitecturalStructure|Place", "ShoppingMall|Building|ArchitecturalStructure|Place"});
        aTypes.put(COMPANY, new String[]{"Company|Organisation", "Non-ProfitOrganisation|Organisation"});
        aTypes.put(BUILDING, new String[]{"Building|ArchitecturalStructure|Place", "Hotel|Building|ArchitecturalStructure|Place"});
        aTypes.put(RIVER, new String[]{"River|Stream|BodyOfWater|NaturalPlace|Place", "Canal|Stream|BodyOfWater|NaturalPlace|Place", "Stream|BodyOfWater|NaturalPlace|Place", "BodyOfWater|NaturalPlace|Place", "Lake|BodyOfWater|NaturalPlace|Place"});
        aTypes.put(ROAD, new String[]{"Road|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place"});
        aTypes.put(UNIVERSITY, new String[]{"University|EducationalInstitution|Organisation", "School|EducationalInstitution|Organisation", "College|EducationalInstitution|Organisation"});
        aTypes.put(MOUNTAIN, new String[]{"Mountain|NaturalPlace|Place", "MountainRange|NaturalPlace|Place"});
        aTypes.put(AIRPORT, new String[]{"Airport|Infrastructure|ArchitecturalStructure|Place"});
        aTypes.put(ORGANISATION, new String[]{"Organisation", "PoliticalParty|Organisation", "TradeUnion|Organisation"});
        aTypes.put(PERIODICAL_LITERATURE, new String[]{"Newspaper|PeriodicalLiterature|WrittenWork|Work", "AcademicJournal|PeriodicalLiterature|WrittenWork|Work", "Magazine|PeriodicalLiterature|WrittenWork|Work"});
        aTypes.put(ISLAND, new String[]{"Island|PopulatedPlace|Place"});
        aTypes.put(MUSEUM, new String[]{"Museum|Building|ArchitecturalStructure|Place"});
        aTypes.put(BRIDGE, new String[]{"Bridge|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place"});
        aTypes.put(AIRLINE, new String[]{"Airline|Company|Organisation"});
        aTypes.put(GOVAGENCY, new String[]{"GovernmentAgency|Organisation"});
        aTypes.put(HOSPITAL, new String[]{"Hospital|Building|ArchitecturalStructure|Place"});
        aTypes.put(AWARD, new String[]{"Award"});
        aTypes.put(THEATRE, new String[]{"Theatre|Venue|ArchitecturalStructure|Place"});
        aTypes.put(LEGISTLATURE, new String[]{"Legislature|Organisation"});
        aTypes.put(LIBRARY, new String[]{"Library|Building|ArchitecturalStructure|Place"});
        aTypes.put(LAWFIRM, new String[]{"LawFirm|Company|Organisation"});
        aTypes.put(MONUMENT, new String[]{"Monument|Place"});
        aTypes.put(DISEASE, new String[]{"Disease|Medicine"});
        aTypes.put(EVENT, new String[]{"SocietalEvent|Event"});

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
        ignoreTypes = Arrays.asList(
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
        desc.put(PERSON, "PERSON");desc.put(COMPANY, "COMPANY");desc.put(BUILDING, "BUILDING");
        desc.put(PLACE, "PLACE");desc.put(RIVER, "RIVER");desc.put(ROAD, "ROAD");desc.put(UNIVERSITY, "UNIVERSITY");
        desc.put(MOUNTAIN, "MOUNTAIN");desc.put(AIRPORT, "AIRPORT");desc.put(ORGANISATION, "ORGANISATION");
        desc.put(PERIODICAL_LITERATURE, "PERIODICAL_LITERATURE");desc.put(ISLAND, "ISLAND");desc.put(MUSEUM, "MUSEUM");
        desc.put(BRIDGE, "BRIDGE");desc.put(AIRLINE, "AIRLINE");desc.put(GOVAGENCY, "GOVAGENCY");desc.put(HOSPITAL, "HOSPITAL");
        desc.put(AWARD, "AWARD");desc.put(THEATRE, "THEATRE");desc.put(LEGISTLATURE, "LEGISTLATURE");desc.put(LIBRARY, "LIBRARY");
        desc.put(LAWFIRM, "LAWFIRM");desc.put(MONUMENT, "MONUMENT");desc.put(DISEASE, "DISEASE");desc.put(EVENT, "EVENT");
        desc.put(OTHER, "OTHER");

        FeatureDictionary.mappings.put(FeatureDictionary.PERSON, Collections.singletonList(FeatureDictionary.PERSON));
        FeatureDictionary.mappings.put(FeatureDictionary.PLACE, Arrays.asList(FeatureDictionary.AIRPORT, FeatureDictionary.HOSPITAL, FeatureDictionary.BUILDING, FeatureDictionary.PLACE, FeatureDictionary.RIVER, FeatureDictionary.ROAD, FeatureDictionary.MOUNTAIN,
                FeatureDictionary.ISLAND, FeatureDictionary.MUSEUM, FeatureDictionary.BRIDGE,
                FeatureDictionary.THEATRE, FeatureDictionary.LIBRARY, FeatureDictionary.MONUMENT));
        FeatureDictionary.mappings.put(FeatureDictionary.ORGANISATION, Arrays.asList(FeatureDictionary.COMPANY, FeatureDictionary.UNIVERSITY, FeatureDictionary.ORGANISATION,
                FeatureDictionary.AIRLINE, FeatureDictionary.GOVAGENCY, FeatureDictionary.AWARD, FeatureDictionary.LEGISTLATURE, FeatureDictionary.LAWFIRM,
                FeatureDictionary.PERIODICAL_LITERATURE,EVENT,DISEASE));
    }
    /**
     * Features for a word that corresponds to a mixture
     * left and right labels, LABEL is one of: LOC, ORG,PER, OTHER, NEW, stop words, special chars
     * Change log
     * 1. Changed all the MU values from double to float to reduce the model size and we are not interested (actually undesired) to have very small values in MU*/
    public static class MU implements Serializable{
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

        public String id;
        //static int NUM_WORDLENGTH_LABELS = 10;
        //feature and the value, for example: <"LEFT: and",200>
        //indicates if the values are final or if they have to be learned
        public Map<String,Float> muVectorPositive;
        //Dirichlet prior for this mixture
        //just leave this object empty if you do not want to use
        public Map<String,Float> alpha;
        //accumulated sum across each feature type e.g. "T","L","R" etc.
        public Map<String,Float> alpha_0;
        public float alpha_pi = 0;
        //number of times this mixture is probabilistically seen, is summation(gamma*x_k)
        public float numMixture;
        //total number of times, this mixture is considered
        public float numSeen;
        public MU(String id, Map<String,Float> alpha, float alpha_pi) {
            initialize(id, alpha, alpha_pi);
        }
        //Smooth param alpha is chosen based on alpha*35(ie. number of types) = an evidence number you can trust.
        //with 0.2 it is 0.2*35=7
        static float SMOOTH_PARAM = 0.2f;

        public static double getMaxEntProb(){
            return (1.0/MU.WORD_LABELS.length)*(1.0/MU.WORD_LABELS.length)*(1.0/MU.TYPE_LABELS.length)*(1.0/MU.ADJ_LABELS.length)*(1.0/MU.ADV_LABELS.length)*(1.0/MU.DICT_LABELS.length)*(1.0/MU.PREP_LABELS.length)*(1.0/MU.V_LABELS.length)*(1.0/MU.PN_LABELS.length);
        }

        //Since we depend on tags of the neighbouring tokens in a big way, we initialize so that the mixture likelihood with type is more precise.
        //and the likelihood with all the other types to be equally likely
        //alpha is the parameter related to dirichlet prior, though the param is called alpha it is treated like alpha-1; See paper for more details
        public static MU initialize(String word, Map<Short, Pair<Float, Float>> initialParams, Map<String, Float> alpha, float alpha_pi){
            float s2 = 0;
            MU mu = new MU(word, alpha, alpha_pi);
            for(Short type: initialParams.keySet()) {
                mu.muVectorPositive.put("T:"+type, initialParams.get(type).first);
                s2 = initialParams.get(type).second;
            }

            //initially don't assume anything about gammas and pi's
            mu.numMixture = s2;
            mu.numSeen = s2;
            mu.alpha = alpha;
            return mu;
        }

        private void initialize(String id, Map<String, Float> alpha, float alpha_pi) {
            muVectorPositive = new LinkedHashMap<>();
            this.alpha = alpha;
            this.alpha_pi = alpha_pi;
            alpha_0 = new LinkedHashMap<>();
            if (alpha != null) {
                for(String val: alpha.keySet()) {
                    String dim = val.substring(0, val.indexOf(':'));
                    if(!alpha_0.containsKey(dim))
                        alpha_0.put(dim, 0f);
                    alpha_0.put(dim, alpha_0.get(dim) + alpha.get(val));
                }
            }
            this.numMixture = 0;
            this.numSeen = 0;
            this.id = id;
        }

        //returns smoothed P(type/this-mixture)
        public float getLikelihoodWithType(String typeLabel){
            float p1, p2;

            for(String tl: TYPE_LABELS) {
                if(("T:"+tl).equals(typeLabel)) {
                    float alpha_k = 0, alpha_k0 = 0;
                    if(alpha.containsKey("T:"+tl)) {
                        alpha_k = alpha.get("T:" + tl);
                        alpha_k0 = alpha_0.get("T");
                    }

                    if(muVectorPositive.containsKey(typeLabel)) {
                        p1 = muVectorPositive.get(typeLabel);
                        p2 = numMixture;
                        return (p1 + SMOOTH_PARAM + alpha_k) / (p2 + allTypes.length*SMOOTH_PARAM + alpha_k0);
                    }
                    //its possible that a mixture has never seen certain types
                    else
                        return (SMOOTH_PARAM + alpha_k)/(numMixture + allTypes.length*SMOOTH_PARAM + alpha_k0);
                }
            }
            log.warn("!!!FATAL: Unknown type label: " + typeLabel + "!!!");
            if(log.isDebugEnabled()) {
                log.debug("Expected one of: ");
                for (String tl : TYPE_LABELS)
                    log.debug(tl);
            }
            return 0;
        }

        public float getLikelihoodWithType(short typeLabel){
            return getLikelihoodWithType("T:"+typeLabel);
        }

        //gets number of symbols in the dimension represented by this feature
        static int getNumberOfSymbols(String f){
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
        //returns P(features/this-mixture)
        public double getLikelihood(List<String> features) {
            double p = 1.0;

            int numLeft = 0, numRight = 0;
            for (String f: features) {
                if(f.startsWith("L:"))
                    numLeft++;
                else if(f.startsWith("R:"))
                    numRight++;
            }
            //numLeft and numRight will always be greater than 0
            for (String f : features) {
                String dim = f.substring(0,f.indexOf(':'));
                float alpha_k = 0, alpha_k0 = 0;
                if(alpha.containsKey(f))
                    alpha_k = alpha.get(f);
                if(alpha_0.containsKey(dim))
                    alpha_k0 = alpha_0.get(dim);
                if(alpha_k>alpha_k0){
                    log.error("ALPHA initialisation wrong for: "+id+" -- "+alpha+" -- "+alpha_0);
                }
                int v = getNumberOfSymbols(f);
                double val;
                Float freq = muVectorPositive.get(f);
                val = ((freq==null?0:freq) + SMOOTH_PARAM + alpha_k) / (numMixture + v*SMOOTH_PARAM + alpha_k0);

                if (Double.isNaN(val)) {
                    log.warn("Found a NaN here: " + f + " " + muVectorPositive.get(f) + ", " + numMixture + ", " + val);
                    log.warn(toString());
                }

                if(f.startsWith("L:"))
                    val = Math.pow(val, 1.0f/numLeft);
                else if(f.startsWith("R:"))
                    val = Math.pow(val, 1.0f/numRight);

                p *= val;
            }
            return p;
        }

        //where N is the total number of observations, for normalization
        public float getPrior(){
            if(numSeen == 0) {
                //two symbols here SEEN and UNSEEN, hence the smoothing; the prior here makes no sense, but code never reaches here...
                //log.warn("FATAL!!! Number of times this mixture is seen is zero, that can't be true!!!");
                return 1.0f;
            }
            return (numMixture+alpha_pi)/(numSeen+alpha_pi);
        }

        /**Maximization step in EM update,
         * @param resp - responsibility of this mixture in explaining the type and features
         * @param features - set of all *relevant* features to this mixture*/
        public void add(Float resp, List<String> features) {
            //if learn is set to false, ignore all the observations
            if (Float.isNaN(resp))
                log.warn("Responsibility is NaN for: " + features);
            numMixture += resp;
            numSeen += 1;

            String type="NULL";
            for (String f: features)
                if(f.startsWith("T:")) {
                    type = f.substring(f.indexOf(":") + 1);
                    break;
                }
            int numLeft = 0, numRight = 0;
            for (String f: features) {
                if(f.startsWith("L:"))
                    numLeft++;
                else if(f.startsWith("R:"))
                    numRight++;
            }

            for (String f : features) {
                if(f.equals("L:"+FeatureDictionary.UNKNOWN_TYPE)) f = "L:"+type;
                if(f.equals("R:"+FeatureDictionary.UNKNOWN_TYPE)) f = "R:"+type;
                float fraction = 1;
                if(f.startsWith("L:")) fraction = 1.0f/numLeft;
                if(f.startsWith("R:")) fraction = 1.0f/numRight;
                if (!muVectorPositive.containsKey(f)) {
                    muVectorPositive.put(f, 0.0f);
                }
                muVectorPositive.put(f, muVectorPositive.get(f) + fraction*resp);
            }
        }

        public double difference(MU mu){
            if(this.muVectorPositive == null)
                return 0.0;
            double d = 0;
            for(String str: muVectorPositive.keySet()){
                double v1 = 0, v2 = 0;
                if(numMixture>0)
                    v1 = muVectorPositive.get(str)/numMixture;
                if(mu.muVectorPositive.containsKey(str) && mu.numMixture>0)
                    v2 = mu.muVectorPositive.get(str)/mu.numMixture;
                d += Math.pow(v1-v2,2);
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
            String p[] = new String[]{"L:","R:","T:","SW:","DICT:"};
            String[][] labels = new String[][]{WORD_LABELS,WORD_LABELS,TYPE_LABELS,sws.toArray(new String[sws.size()]),DICT_LABELS};
            str += "ID: " + id + "\n";
            for(int i=0;i<labels.length;i++) {
                Map<String,Float> some = new LinkedHashMap<>();
                for(int l=0;l<labels[i].length;l++) {
                    String d = p[i] + labels[i][l];
                    String dim = p[i].substring(0,p[i].length()-1);
                    float alpha_k = 0, alpha_k0 = 0;
                    if(alpha.containsKey(d))
                        alpha_k = alpha.get(d);
                    if(alpha_0.containsKey(dim))
                        alpha_k0 = alpha_0.get(dim);

                    Float v = muVectorPositive.get(d);
                    some.put(d, (((v==null)?0:v)+alpha_k) / (numMixture+alpha_k0));
                }
                List<Pair<String,Float>> smap;
                smap = Util.sortMapByValue(some);
                for(Pair<String,Float> pair: smap)
                    str += pair.getFirst()+":"+pair.getSecond()+"-";
                str += "\n";
            }
            str += "NM:"+numMixture+", NS:"+numSeen+"\n";
            str += "Alphas "+alpha + " -- "+alpha_0+"\n";
            str += "PI ALPHA: "+alpha_pi+"\n";
            return str;
        }

        public String prettyPrint(){
            String str = "";
            String p[] = new String[]{"L:","R:","T:","SW:","DICT:"};
            String[][] labels = new String[][]{WORD_LABELS,WORD_LABELS,TYPE_LABELS,sws.toArray(new String[sws.size()]),DICT_LABELS};
            str += "ID: " + id + "\n";
            for(int i=0;i<labels.length;i++) {
                Map<String,Float> some = new LinkedHashMap<>();
                for(int l=0;l<labels[i].length;l++) {
                    String k = p[i] + labels[i][l];
                    String d;
                    if(i==0 || i==1 || i==2)
                        d = p[i].replaceAll(":","") + "[" + (labels[i][l].equals("NULL")?"EMPTY":FeatureDictionary.desc.get(Short.parseShort(labels[i][l]))) + "]";
                    else
                        d = p[i].replaceAll(":","") + "[" + labels[i][l] + "]";

                    String dim = p[i].substring(0,p[i].length()-1);
                    float alpha_k = 0, alpha_k0 = 0;
                    if(alpha.containsKey(k))
                        alpha_k = alpha.get(k);
                    if(alpha_0.containsKey(dim))
                        alpha_k0 = alpha_0.get(dim);
                    if(muVectorPositive.get(k) != null) {
                        some.put(d, (muVectorPositive.get(k)+alpha_k) / (numMixture+alpha_k0));
                    }
                    else
                        some.put(d, 0.0f);
                }
                List<Pair<String,Float>> smap;
                smap = Util.sortMapByValue(some);
                int numF = 0;
                for(Pair<String,Float> pair: smap) {
                    if(numF>=3 || pair.getSecond()<=0)
                        break;

                    str += pair.getFirst() + ":" + new DecimalFormat("#.##").format(pair.getSecond()) + ":::";
                    numF++;
                }
                str += "\n";
            }
            str += "Evidence: "+numSeen+"\n";
            return str;
        }
    }
    private static final long serialVersionUID = 1L;
    //dimension -> instance -> entity type of interest -> #positive type, #negative type
    //patt -> Aa -> 34 100, pattern Aa occurred 34 times with positive classes of the 100 times overall.
    //mixtures of the BMM model
    public Map<String, MU> features = new LinkedHashMap<>();

    public FeatureDictionary(){
        features = new LinkedHashMap<>();
    }

    /**
     * address book should be specially handled and DBpedia gazette is required.
     * and make sure the address book is cleaned see cleanAB method
     */
    public FeatureDictionary(Map<String, String> gazettes, float alpha, int iter) {
        addGazz(gazettes, alpha);
        EM(gazettes, alpha, iter);
    }

    //TODO: Add to the project the code that produces this file
    //returns token -> {redirect (can be the same as token), page length of the page it redirects to}
    static Map<String,Map<String,Integer>> getTokenTypePriors(){
        Map<String,Map<String,Integer>> pageLengths = new LinkedHashMap<>();
        log.info("Parsing token types");
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

    void printMemoryUsage(){
        int mb = 1024*1024;
        Runtime runtime = Runtime.getRuntime();
        log.info(
                "Used memory: " + ((runtime.totalMemory() - runtime.freeMemory()) / mb) + "MB\n" +
                        "Free memory: " + (runtime.freeMemory() / mb) + "MB\n" +
                        "Total memory: " + (runtime.totalMemory() / mb) + "MB\n" +
                        "-------------"
        );
    }

    FeatureDictionary addGazz(Map<String,String> gazettes, float alphaFraction){
        long start_time = System.currentTimeMillis();
        long timeToComputeFeatures = 0, tms;
        log.info("Analysing gazettes");

        int g = 0, nume = 0;
        final int gs = gazettes.size();
        int gi = 0;
        printMemoryUsage();
        //The number of times a word appeared in a phrase of certain type
        Map<String, Map<Short,Integer>> words = new LinkedHashMap<>();
        Map<String,String> dbpedia = EmailUtils.readDBpedia();
        log.info("Done loading DBpedia");
        printMemoryUsage();
        Map<String, Integer> wordFreqs = new LinkedHashMap<>();
        for (String str : gazettes.keySet()) {
            tms = System.currentTimeMillis();

            String entityType = gazettes.get(str);
            Short ct = codeType(entityType);
            if (ignoreTypes.contains(entityType)) {
                continue;
            }

            String[] patts = getPatts(str);
            for(String patt: patts){
                if(!words.containsKey(patt))
                    words.put(patt, new LinkedHashMap<>());
                if(!words.get(patt).containsKey(ct))
                    words.get(patt).put(ct, 0);
                words.get(patt).put(ct, words.get(patt).get(ct)+1);

                if(!wordFreqs.containsKey(patt))
                    wordFreqs.put(patt, 0);
                wordFreqs.put(patt, wordFreqs.get(patt)+1);
            }

            timeToComputeFeatures += System.currentTimeMillis() - tms;

            if ((++gi) % 10000 == 0) {
                log.info("Analysed " + (gi) + " records of " + gs + " percent: " + (gi * 100 / gs) + "% in gazette: " + g);
                log.info("Time spent in computing features: " + timeToComputeFeatures);
            }
            nume++;
        }
        log.info("Done analyzing gazettes for frequencies");
        printMemoryUsage();

        /*
        * Here is the histogram of frequencies of words from the 2014 dump of DBpedia
        * To read -- there are 861K words taht are seen just seen once.
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
        * Also, single character words, words with numbers (like jos%c3%a9), numbers (like 2008, 2014), empty tokens
        */
        log.info("Considered " + nume + " entities in " + gazettes.size() + " total entities");
        log.info("Done analysing gazettes in: " + (System.currentTimeMillis() - start_time));
        log.info("Initialising MUs");

        //page lengths from wikipedia
        Map<String, Map<String,Integer>> pageLens = getTokenTypePriors();
        int initAlpha = 0;
        int wi=0, ws = words.size();
        //float fraction = 1.0f/5;
        int numIgnored = 0, numConsidered = 0;
        for(String str: words.keySet()) {
            float wordFreq = wordFreqs.get(str);
            if (wordFreq<3 || str.length()<=1) {
                numIgnored++;
                continue;
            }
            boolean hasNumber = false;
            for(char c: str.toCharArray())
                if(Character.isDigit(c)) {
                    hasNumber = true;
                    numIgnored++;
                    break;
                }
            if (hasNumber)
                continue;

            numConsidered++;
            if (features.containsKey(str))
                continue;
            Map<Short, Pair<Float, Float>> priors = new LinkedHashMap<>();
            for (Short type : FeatureDictionary.allTypes) {
                if (words.get(str).containsKey(type))
                    priors.put(type, new Pair<>((float)words.get(str).get(type), wordFreq));
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
            if (str.length() > 2 && pageLens.containsKey(str)) {
                Map<String, Integer> pls = pageLens.get(str);
                for (String page : pls.keySet()) {
                    String gt = dbpedia.get(page);
                    //Music bands especially are noisy
                    if (gt != null && !(ignoreTypes.contains(gt) || gt.equals("Agent"))) {
                        Short ct = codeType(gt);
                        //all the albums, films etc.
                        if (ct == FeatureDictionary.OTHER && gt.endsWith("|Work"))
                            continue;
                        String[] features = new String[]{"T:" + ct, "L:NULL", "R:NULL", "SW:NULL"};
                        for (String f : features) {
                            if (!alpha.containsKey(f)) alpha.put(f, 0f);
                            alpha.put(f, alpha.get(f) + (alphaFraction * pls.get(page) / 1000f));
                        }
                        alpha_pi += alphaFraction * pls.get(page) / 1000f;
                    }
                }
            }
            if (alpha.size() > 0)
                initAlpha++;
            features.put(str, MU.initialize(str, new LinkedHashMap<>(), alpha, alpha_pi));
            if (wi++ % 1000 == 0) {
                log.info("Done: " + wi + "/" + ws);
                if(wi%10000==0)
                    printMemoryUsage();
            }
        }
        log.info("Considered: "+numConsidered+" mixtures and ignored "+numIgnored);
        log.info("Initialised alpha for " + initAlpha + "/" + ws + " entries.");
        return this;
    }

    //codes a given DBpedia type into type coding of this class
    public static Short codeType(String type){
        Short ct = FeatureDictionary.OTHER;
        if(type == null)
            return ct;

        //strip "|Agent" in the end
        if(type.endsWith("|Agent"))
            type = type.substring(0, type.length()-6);
        String[] fs = type.split("\\|");
        //the loop codes the string type that may look like "University|EducationalInstitution|Organisation|Agent" into the most specific type by looking at the biggest to smallest prefix.
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

    static String[] getPatts(String phrase){
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
            //if the token is of the form [A-Z]. don't remove the trailing period since such tokens are invaluable in recognising some names, for ex:
            if(t.length()!=2 || t.charAt(1)!='.')
                t = endClean.matcher(t).replaceAll("");
            //sws.contains(words[i-1].toLowerCase()) only if the previous token is a symbol, we make an amalgamation
            //sws before a token should not be considered "Bank of Holland", should not have a feature "of Holland", instead "Holland" makes more sense
            if(i>0 && symbols.contains(words[i-1].toLowerCase()))
                t = words[i-1].toLowerCase()+" "+t;

            //for example in Roberston Stephens & Co -- we don't want to see "stephens &" but only "& co"
            if(i<(words.length-1) && (sws.contains(words[i+1].toLowerCase())))//||(symbols.contains(words[i+1].toLowerCase()))))
                t += " "+words[i+1].toLowerCase();

            //emit all the words or patterns
            if (t != null)
                patts.add(t);
        }
        return patts.toArray(new String[patts.size()]);
    }

    static Random rand = new Random();
    static{
        rand.setSeed(5);
    }

    //Input is a token and returns the best type assignment for token
    Short getType(String token){
        MU mu = features.get(token);
        if(mu == null){
            //log.warn("Token: "+token+" not initialised!!");
            return UNKNOWN_TYPE;
        }
        Short bestType = allTypes[rand.nextInt(allTypes.length)];double bv = 0;

        //We don't consider OTHER as even a type
        for(Short type: FeatureDictionary.allTypes) {
            if(type!=FeatureDictionary.OTHER) {
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
     * TODO: move this to an appropriate place
     * A generative function, that takes the phrase and generates features.
     * requires mixtures as a parameter, because some of the features depend on this
     * returns a map of mixture identity to the set of features relevant to the mixture
     * Map<String,Double> because features sometimes are associated with a score, for semantic type, get label to be precise</>*/
    Map<String,List<String>> generateFeatures(String phrase, Short type){
        Map<String, List<String>> mixtureFeatures = new LinkedHashMap<>();
        String[] patts = getPatts(phrase);
        String[] words = phrase.split("\\W+");
        String sw = "NULL";

        if(patts.length == 0)
            return mixtureFeatures;

        //scrapped position label feature for these reasons:
        //1. It is un-normalized, the possible labels are not equally likely
        //2. The left and right features already hold the position info very tightly
        for(int pi = 0; pi<patts.length; pi++){
            if(sws.contains(patts[pi].toLowerCase()))
                continue;
            for(int wi=0;wi<words.length;wi++){
                String word = words[wi];
                //Generally entries contain only one stop word per phrase, so not bothering which one
                //index>0 check to avoid considering 'A' and 'The' in the beginning
                if(wi>0 && sws.contains(word) && !(wi<words.length-1 && patts[pi].equals(word.toLowerCase()+" "+words[wi+1].toLowerCase())) && !(words[wi-1].toLowerCase()+" "+word.toLowerCase()).equals(patts[pi])) {
                    sw = word;
                    break;
                }
            }

            List<String> features = new ArrayList<>();

            for(int pj=0;pj<pi;pj++)
                features.add("L:" +getType(patts[pj]));
            if(pi==0)
                features.add("L:NULL");

            for(int pj=pi+1;pj<patts.length;pj++)
                features.add("R:" + getType(patts[pj]));
            if(pi+1 == patts.length)
                features.add("R:NULL");

            //This feature is redundant if the pattern itself has
            features.add("SW:" + sw);
            features.add("T:" + type);
            //boolean containsAdj = false, containsAdv = false, containsVerb = false, containsPrep = false, containsPronoun = false;
            boolean containsDict = false;
            for(String word: words) {
                word  = word.toLowerCase();
                //consider all the other words, other than this word
                if(!sws.contains(word) && !patts[pi].equals(word) && !patts[pi].contains(" "+word) && !patts[pi].contains(word+" ")) {
                    if(EnglishDictionary.getDict().contains(word)) {
                        containsDict = true;
                        break;
                    }
                }
            }
            if(containsDict)
                features.add("DICT:Y");
            else
                features.add("DICT:N");

            mixtureFeatures.put(patts[pi].toLowerCase(), features);
        }
        return mixtureFeatures;
    }

    /**If the phrase is of OTHER type, then consider no chunks and emit features for every word*/
    public Map<String,List<String>> generateFeatures2(String phrase, Short type){
        Map<String,List<String>> features = new LinkedHashMap<>();
        if(type == FeatureDictionary.OTHER){
            String[] words = getPatts(phrase);
            for(String w: words){
                Map<String,List<String>> map = generateFeatures(w, type);
                for(String m: map.keySet())
                    features.put(m, map.get(m));
            }
            return features;
        }
        features = generateFeatures(phrase, type);
        Map<String,List<String>> ffeatures = new LinkedHashMap<>();
        for(String f: features.keySet())
            if(f.length()>1)
                ffeatures.put(f, features.get(f));
        return ffeatures;
    }

    double getIncompleteDateLogLikelihood(){
        double ll = 0;
        List<String> nsws = new ArrayList<>();
        nsws.addAll(sws);nsws.add("NULL");
        String p[] = new String[]{"L:","R:","T:","SW:","DICT:"};
        String[][] labels = new String[][]{MU.WORD_LABELS,MU.WORD_LABELS,MU.TYPE_LABELS,nsws.toArray(new String[nsws.size()]),MU.DICT_LABELS};
        for(String mid: features.keySet()){
            MU mu = features.get(mid);
            for(int pi=0;pi<p.length;pi++) {
                for (String l : labels[pi]) {
                    String f = p[pi]+l;
                    String dim = f.substring(0, f.indexOf(':'));
                    float alpha_k = 0, alpha_k0 = 0;
                    if (mu.alpha.containsKey(f))
                        alpha_k = mu.alpha.get(f);
                    if (mu.alpha_0.containsKey(dim))
                        alpha_k0 = mu.alpha_0.get(dim);

                    int v = MU.getNumberOfSymbols(f);
                    double val;
                    Float freq = mu.muVectorPositive.get(f);
                    val = ((freq == null ? 0 : freq) + MU.SMOOTH_PARAM + alpha_k) / (mu.numMixture + v*MU.SMOOTH_PARAM + alpha_k0);
                    ll += Math.log(val) * ((freq == null ? 0 : freq) + alpha_k);
                }
            }
            ll += (mu.numMixture+mu.alpha_pi)*Math.log(mu.getPrior());
        }
        ll /= features.size();
        System.out.println("ll: "+ll+" -- "+features.size());
        return ll;
    }

    //just cleans up trailing numbers in the string
    static String cleanRoad(String title){
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
     * 3. If the type is settlement then the title is written as "Berkeley_California" which actually mean Berkeley_(California); so cleaning these too
     * 4. We ignore certain noisy types. see ignoreTypes
     * 5. Ignores any single word names
     * 6. If the type is person like but the phrase contains either "and" or "of", we filter this out.
     * returns either the cleaned phrase or null if the phrase cannot be cleaned.
     */
    String filterTitle(String phrase, String type){
        int cbi = phrase.indexOf(" (");
        if(cbi>=0)
            phrase = phrase.substring(0, cbi);

        if(type.equals("Road|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place"))
            phrase = cleanRoad(phrase);

        //in places there are things like: Shaikh_Ibrahim,_Iraq
        int idx;
        if (type.endsWith("Settlement|PopulatedPlace|Place") && (idx=phrase.indexOf(", "))>=0)
            phrase = phrase.substring(0,idx);

        boolean allowed = true;
        for(String it: FeatureDictionary.ignoreTypes)
            if(type.contains(it)) {
                allowed = false;
                break;
            }
        if(!allowed)
            return null;

        //Do not consider single word names for training, the model has to be more complex than it is right now to handle these
        if(!phrase.contains(" "))
            return null;

        if((type.endsWith("Person")||type.equals("Agent")) && (phrase.contains(" and ")||phrase.contains(" of ")||phrase.contains(" on ")||phrase.contains(" in ")))
            return null;
        return phrase;
    }

    //the argument alpha fraction is required only for naming of the dumped model size
    void EM(Map<String,String> gazettes, float alphaFraction, int iter){
        log.info("Performing EM on: #" + features.size() + " words");
        double ll = getIncompleteDateLogLikelihood();
        log.info("Start Data Log Likelihood: "+ll);
        System.out.println("Start Data Log Likelihood: " + ll);
        Map<String, MU> revisedMixtures = new LinkedHashMap<>();
        int N = gazettes.size();
        int wi;
        for(int i=0;i<iter;i++) {
            wi = 0;
            //computeTypePriors();
            for (Map.Entry e: gazettes.entrySet()) {
                String phrase = (String)e.getKey();
                String type = (String)e.getValue();
                phrase = filterTitle(phrase, type);
                if(phrase == null)
                    continue;

                if (wi++ % 1000 == 0)
                    log.info("EM iteration: " + i + ", " + wi + "/" + N);

                Short coarseType = codeType(type);
                float z = 0;
                //responsibilities
                Map<String, Float> gamma = new LinkedHashMap<>();
                //Word (sort of mixture identity) -> Features
                Map<String, List<String>> wfeatures = generateFeatures2(phrase, coarseType);

                if(coarseType!=FeatureDictionary.OTHER) {
                    for (String mi : wfeatures.keySet()) {
                        if (wfeatures.get(mi) == null) {
                            continue;
                        }
                        MU mu = features.get(mi);
                        if (mu == null) {
                            //log.warn("!!FATAL!! MU null for: " + mi + ", " + features.size());
                            continue;
                        }
                        double d = mu.getLikelihood(wfeatures.get(mi)) * mu.getPrior();
                        if (Double.isNaN(d))
                            log.warn("score for: " + mi + " " + wfeatures.get(mi) + " is NaN");
                        gamma.put(mi, (float) d);
                        z += d;
                        //System.err.println(mi + " : " + d + ", "+mu.getLikelihood(wfeatures.get(mi), this) );
                    }
                    if (z == 0) {
                        //log.warn("!!!FATAL!!! Skipping: " + phrase + " as none took responsibility");
                        continue;
                    }

                    for (String g : gamma.keySet()) {
                        gamma.put(g, gamma.get(g) / z);
                    }
                }
                else{
                    for(String mi: wfeatures.keySet())
                        gamma.put(mi, 1.0f/wfeatures.size());
                }

                if(DEBUG){
                    for(String mi: wfeatures.keySet()) {
                        log.info("MI:" + mi + ", " + gamma.get(mi) + ", " + wfeatures.get(mi));
                        log.info(features.get(mi).toString());
                    }
                    log.info("EM iter: "+i+", "+phrase+", "+type+", ct: "+coarseType);
                    log.info("-----");
                }

                for (String g : gamma.keySet()) {
                    MU mu = features.get(g);
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
                    //don't even update if the value is so low, that just adds meek affiliation with unrelated features
                    if (gamma.get(g) > 1E-7)
                        revisedMixtures.get(g).add(gamma.get(g), wfeatures.get(g));

                }
            }
            double change = 0;
            for (String mi : features.keySet())
                if (revisedMixtures.containsKey(mi))
                    change += revisedMixtures.get(mi).difference(features.get(mi));
            change /= revisedMixtures.size();
            log.info("Iter: " + i + ", change: " + change);
            System.out.println("EM Iteration: " + i + ", change: " + change);
            //incomplete data log likehood is better mesure than just the change in parameters
            //i.e. P(X/\theta) = \sum\limits_{z}P(X,Z/\theta)
            features = revisedMixtures;
            ll = getIncompleteDateLogLikelihood();
            log.info("Iter: "+i+", Data Log Likelihood: "+ll);
            System.out.println("EM Iteration: "+i+", Data Log Likelihood: "+ll);

            revisedMixtures = new LinkedHashMap<>();

            try {
                if(i==(iter-1)) {
                    Short[] ats = FeatureDictionary.allTypes;
                    //make cache dir if it does not exist
                    String cacheDir = System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator + "cache";
                    if(!new File(cacheDir).exists()) {
                        boolean mkdir = new File(cacheDir).mkdir();
                        if(!mkdir)
                            log.warn("Cannot create cache dir. "+cacheDir);
                    }
                    for (Short type : ats) {
                        FileWriter fw = new FileWriter(cacheDir + File.separator + "em.dump." + type + "." + i);
                        FileWriter ffw = new FileWriter(cacheDir + File.separator + FeatureDictionary.desc.get(type) + ".txt");
                        Map<String, Double> sortScores = new LinkedHashMap<>();
                        Map<String, Double> scores = new LinkedHashMap<>();
                        for (String w : features.keySet()) {
                            MU mu = features.get(w);
                            double v1 = mu.getLikelihoodWithType(type) * (mu.numMixture/mu.numSeen);
                            double v = v1 * Math.log(mu.numSeen);
                            if (Double.isNaN(v)) {
                                sortScores.put(w, 0.0);
                                scores.put(w,0.0);
                            }
                            else {
                                sortScores.put(w, v);
                                scores.put(w, v1);
                            }
                        }
                        List<Pair<String, Double>> ps = Util.sortMapByValue(sortScores);
                        for (Pair<String, Double> p : ps) {
                            if(type.equals(ats[0])){
                                fw.write(features.get(p.getFirst()).toString());
                                fw.write("========================\n");
                            }

                            MU mu = features.get(p.getFirst());
                            Short maxT = -1;double maxV = -1;
                            for(Short t: ats) {
                                double d = mu.getLikelihoodWithType(t);
                                if (d > maxV){
                                    maxT = t;
                                    maxV = d;
                                }
                            }
                            //only if both the below conditions are satisfied, this template will ever be seen in action
                            if(maxT.equals(type) && scores.get(p.getFirst())>=0.001) {
                                ffw.write("Token: " + EmailUtils.uncanonicaliseName(p.getFirst()) + "\n");
                                ffw.write(mu.prettyPrint());
                                ffw.write("========================\n");
                            }
                        }
                        fw.close();
                        ffw.close();
                    }
                }
                if(DEBUG && (i==0 || i==2 || i==5 || i==7 || i==9)){
                    SequenceModel nerModel = new SequenceModel();
                    nerModel.dictionary = this;
                    String mwl = System.getProperty("user.home")+File.separator+"epadd-settings"+File.separator+"experiment"+File.separator;
                    String modelFile = mwl + "ALPHA_"+alphaFraction+"-Iter_"+i+"-"+SequenceModel.modelFileName;
                    nerModel.writeModel(new File(modelFile));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**Given a type described in text returns a coarse coding for the type
     * for example: "University" -> [ORGANIZATION]*/
    public static Short getCoarseType(Short type){
        List<Short> coarseTypes = mappings.keySet().stream().filter(p -> mappings.get(p).contains(type)).collect(Collectors.toList());
        if(coarseTypes == null || coarseTypes.size() == 0) {
            log.warn("Unknown type: " + type);
            return OTHER;
        }
        else if(coarseTypes.size()>1) {
            log.warn("!!Unexpected!!Found more than one code for "+type);
            return coarseTypes.get(0);
        }
        else
            return coarseTypes.get(0);
    }

    public static void main(String[] args) {
        String[] test = new String[]{"Settlement|PopulatedPlace|Place","Town|Settlement|PopulatedPlace|Place","Road|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place",
        "Village|Settlement|PopulatedPlace|Place","Building|ArchitecturalStructure|Place"};
        for(String t: test)
            System.out.println(t + " - " + codeType(t) + " - " + getCoarseType(codeType(t)));
        for(Short t: allTypes)
            System.out.println(desc.get(t)+" -- "+getCoarseType(t));

    }
}
