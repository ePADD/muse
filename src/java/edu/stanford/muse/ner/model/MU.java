package edu.stanford.muse.ner.model;

import edu.stanford.muse.ner.featuregen.FeatureUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Features for a word that corresponds to a mixture
 * left and right labels, LABEL is one of: LOC, ORG,PER, OTHER, NEW, stop words, special chars
 * Change log
 * 1. Changed all the MU values from double to float to reduce the model size and we are not interested (actually undesired) to have very small values in MU*/
public class MU implements Serializable {
    static Log log = LogFactory.getLog(MU.class);
    static final long serialVersionUID = 1L;
    static String[] POSITION_LABELS = new String[]{"S","B","I","E"};
    static String[] WORD_LABELS = new String[NEType.getAllTypes().length+1];
    static String[] TYPE_LABELS = new String[NEType.getAllTypes().length];
    static String[] BOOLEAN_VARIABLES = new String[]{"Y","N"};
    static String[] DICT_LABELS = BOOLEAN_VARIABLES,ADJ_LABELS = BOOLEAN_VARIABLES, ADV_LABELS = BOOLEAN_VARIABLES,PREP_LABELS = BOOLEAN_VARIABLES,V_LABELS = BOOLEAN_VARIABLES,PN_LABELS = BOOLEAN_VARIABLES;
    static{
        NEType.Type[] allTypes = NEType.getAllTypes();
        for(int i=0;i< allTypes.length;i++)
            WORD_LABELS[i] = allTypes[i].getCode()+"";
        for(int i=0;i< allTypes.length;i++)
            TYPE_LABELS[i] = allTypes[i].getCode()+"";
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
    public MU(String id, Map<String, Float> alpha, float alpha_pi) {
        initialize(id, alpha, alpha_pi);
    }
    //Smooth param alpha is chosen based on alpha*35(ie. number of types) = an evidence number you can trust.
    //with 0.2 it is 0.2*35=7
    static float SMOOTH_PARAM = 0.2f;

    public static double getMaxEntProb(){
        return (1.0/ MU.WORD_LABELS.length)*(1.0/ MU.WORD_LABELS.length)*(1.0/ MU.TYPE_LABELS.length)*(1.0/ MU.ADJ_LABELS.length)*(1.0/ MU.ADV_LABELS.length)*(1.0/ MU.DICT_LABELS.length)*(1.0/ MU.PREP_LABELS.length)*(1.0/ MU.V_LABELS.length)*(1.0/ MU.PN_LABELS.length);
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
                    return (p1 + SMOOTH_PARAM + alpha_k) / (p2 + NEType.getAllTypes().length*SMOOTH_PARAM + alpha_k0);
                }
                //its possible that a mixture has never seen certain types
                else
                    return (SMOOTH_PARAM + alpha_k)/(numMixture + NEType.getAllTypes().length*SMOOTH_PARAM + alpha_k0);
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

    public float getLikelihoodWithType(Short typeCode){
        return getLikelihoodWithType("T:"+typeCode);
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
            return FeatureUtils.sws.size()+1;
        if(f.startsWith("DICT:")||f.startsWith("ADJ:")||f.startsWith("ADV:")||f.startsWith("PREP:")||f.startsWith("V:")||f.startsWith("PN:")||f.startsWith("POS:"))
            return 2;
        log.error("!!!REALLY FATAL!!! Unknown feature: " + f);
        return 0;
    }

    //mixtures also include the type of the phrase
    //returns P(mixtures/this-mixture)
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
     * @param resp - responsibility of this mixture in explaining the type and mixtures
     * @param features - set of all *relevant* mixtures to this mixture*/
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
            if(f.equals("L:"+SequenceModel.UNKNOWN_TYPE)) f = "L:"+type;
            if(f.equals("R:"+SequenceModel.UNKNOWN_TYPE)) f = "R:"+type;
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
        String[][] labels = new String[][]{WORD_LABELS,WORD_LABELS,TYPE_LABELS, FeatureUtils.sws.toArray(new String[FeatureUtils.sws.size()]),DICT_LABELS};
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
        String[][] labels = new String[][]{WORD_LABELS,WORD_LABELS,TYPE_LABELS, FeatureUtils.sws.toArray(new String[FeatureUtils.sws.size()]),DICT_LABELS};
        str += "ID: " + id + "\n";
        for(int i=0;i<labels.length;i++) {
            Map<String,Float> some = new LinkedHashMap<>();
            for(int l=0;l<labels[i].length;l++) {
                String k = p[i] + labels[i][l];
                String d;
                if(i==0 || i==1 || i==2)
                    d = p[i].replaceAll(":","") + "[" + (labels[i][l].equals("NULL")?"EMPTY": NEType.getTypeForCode(Short.parseShort(labels[i][l]))) + "]";
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
