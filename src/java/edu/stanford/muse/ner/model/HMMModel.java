package edu.stanford.muse.ner.model;

import com.google.common.collect.Sets;
import edu.stanford.muse.ner.tokenize.CICTokenizer;
import edu.stanford.muse.ner.tokenize.Tokenizer;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Span;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * Created by vihari on 09/04/17.
 * Implementation of http://dl.acm.org/citation.cfm?id=1014058 "Mining reference tables for automatic text segmentation"
 */
public class HMMModel extends NERModel implements Serializable{
    private static final long serialVersionUID = 1L;

    private static Log log = LogFactory.getLog(SequenceModel.class);
    private static String TOKEN_PREFIX = "TK:", TYPE_PREFIX = "T:", WORD_CLASS_PREFIX = "WC:";

    Learner.Transitions transitions;
    private Map<String, NEType.Type> tokenTypes = new LinkedHashMap<>();
    private Map<String, String> gazette;
    private Tokenizer tokenizer = new CICTokenizer();

    private static List<String> BIE(String s){
        String[] pos = new String[]{"B:", "I:", "E:"};
        return Stream.of(pos).map(p->p+s).collect(Collectors.toList());
    }

    private static String BIE(String token, int ti, int length){
        return (ti == 0 ? "B:" : (ti == (length - 1) ? "E:" : "I:")) + token;
    }

    private static class Learner extends RuleInducer {
        Map<String, NEType.Type> tokenTypes = new LinkedHashMap<>();
        Transitions transitions = new Transitions();

        Learner(Map<String, String> gazettes, Map<String, Map<String, Float>> tokenPriors) {
            super(gazettes, tokenPriors);
        }

        static class Transition implements Serializable{
            Map<NEType.Type, Integer> freq = new LinkedHashMap<>();
            void add(NEType.Type type){
                freq.put(type, freq.getOrDefault(type, 0)+1);
            }
            int getTotalFreq(NEType.Type t){
                return freq.getOrDefault(t, 0);
            }
            int getTotalFreq(){
                return freq.values().stream().mapToInt(v->v).sum();
            }
            Map<NEType.Type, Integer> getFreq(){
                return freq;
            }
        }

        static class Transitions implements Serializable{
            Map<String, Map<String, Transition>> transitions = new LinkedHashMap<>();
            Transition get(String s1, String s2){
                if(!transitions.containsKey(s1) || !transitions.get(s1).containsKey(s2))
                    return null;
                return transitions.get(s1).get(s2);
            }

            Set<String> allstates;

            //checks only if the state is ever part of the starting state in a transition
            boolean contains(String state){
                if(allstates==null) {
                    allstates = new LinkedHashSet<>();
                    allstates.addAll(transitions.keySet());
                    transitions.values().stream().flatMap(v->v.keySet().stream()).forEach(allstates::add);
                }

                return allstates.contains(state);
            }

            double getScore(String s1, String s2, NEType.Type type){
                Transition transition = get(s1, s2);
                int NUM_STATES = 3*(1000 + 38 + 20);

                Map<NEType.Type, Integer> total =
                        Stream.of(NEType.getAllTypes()).map(t -> new Pair<>(t, transitions.get(s1).values().stream().mapToInt(tr -> tr.getTotalFreq(t)).sum()))
                                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

                Map<NEType.Type, Integer> f_i_1_2, f_i_1;
                int f_1_2;//f(t(s1, s2))

                f_i_1 = total;
                if(transition == null) {
                    assert transitions.containsKey(s1):"Invalid state: "+s1+" is never seen during training, which is not supposed to happen because of the design";
                    return 1.0/(NUM_STATES+f_i_1.getOrDefault(type, 0));
                }

                f_i_1_2 = transition.getFreq();
                f_1_2 = transition.getTotalFreq();
                //add one smoothing
                if (!f_i_1_2.containsKey(type))
                    return 1.0/(f_1_2+NEType.getAllTypes().length);

                double score = ((double)(f_i_1_2.get(type)+1)/(double)(f_1_2+NEType.getAllTypes().length)) *
                        ((double)(f_i_1_2.get(type)+1)/(double)(f_i_1.get(type) + NUM_STATES));
                return score;
            }

            void add(String s1, String s2, Transition transition){
                if(!transitions.containsKey(s1))
                    transitions.put(s1, new LinkedHashMap<>());
                if(!transitions.get(s1).containsKey(s2))
                    transitions.get(s1).put(s2, transition);
            }
        }

        private Set<String> getAllSubsumingTypes(String[] tokens, int ti, Set<String> freqWords){
            String t1 = tokens[ti];
            Set<String> types = new LinkedHashSet<>();

            //add a new one only if both the tokens are frequent ones
            if(freqWords.contains(t1))
                types.add(BIE(TOKEN_PREFIX + t1, ti, tokens.length));

            //type priors
            MU mu1 = mixtures.get(t1);
            if (mu1 != null) {
                NEType.Type t = Stream.of(NEType.getAllTypes())
                        .max((tr1, tr2) -> Double.compare(mu1.getLikelihoodWithType(tr1.getCode()), mu1.getLikelihoodWithType(tr2.getCode()))).get();
                tokenTypes.put(tokens[ti], t);
                while(t!=null) {
                    types.add(BIE(TYPE_PREFIX + t.getCode(), ti, tokens.length));
                    t = t.parent();
                }
            }

            String wc = WORD_CLASS_PREFIX + FeatureGeneratorUtil.tokenFeature(t1);
            types.add(BIE(wc, ti, tokens.length));

            return types;
        }

        @Override
        void learn() {
            //collect word frequencies
            int LIMIT = 1000;
            log.info("Computing most frequent words");
            Set<String> freqWords = getGazettes().keySet().stream()
                    .flatMap(v->Stream.of(v.split("\\s+")))
                    .collect(groupingBy(Function.identity(), counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .map(Map.Entry::getKey)
                    .filter(s-> !StringUtil.isEmpty(s) && s.length()>1 && StringUtils.isAlpha(s))
                    .limit(LIMIT)
                    .collect(Collectors.toSet());

            log.info("Making a pass over gazette to collect transition stats");

            getGazettes().entrySet()
                    //.parallel()
                    .forEach(e -> {
                        String[] tokens = e.getKey().split("\\s+");
                        NEType.Type type = NEType.parseDBpediaType(e.getValue());
                        for (int ti = 0; ti < tokens.length - 1; ti++) {
                            String t1 = TOKEN_PREFIX + tokens[ti], t2 = TOKEN_PREFIX + tokens[ti + 1];
                            t1 = BIE(t1, ti, tokens.length);
                            int tj = ti + 1;
                            t2 = BIE(t2, tj, tokens.length);
                            Set<String> t1Types = getAllSubsumingTypes(tokens, ti, freqWords);
                            Set<String> t2Types = getAllSubsumingTypes(tokens, tj, freqWords);
                            Set<List<String>> cps = Sets.cartesianProduct(t1Types, t2Types);
                            Transition transition =
                                    cps.stream().map(cp -> transitions.get(cp.get(0), cp.get(1)))
                                            .filter(t->t!=null).findAny().orElse(null);

                            if(transition == null) {
                                transition = new Transition();
                                for(List<String> cp: cps)
                                    if(cp.get(0)!=null && cp.get(1)!=null) {
                                        transitions.add(cp.get(0), cp.get(1), transition);
                                        //System.err.println("Adding: "+cp.get(0)+ "::"+cp.get(1)+" Size: "+transitions.transitions.size());
                                        //System.err.println(transitions.transitions.keySet());
                                }
                            }
                            transition.add(type);
                        }
                    });
            System.out.println("Size: " + transitions.transitions.size());
        }
    }

    @Override
    //given a sequence, returns the confidence of the phrase being of certain type
    double getConditional(String phrase, Short typeCode){
        NEType.Type t = NEType.getTypeForCode(typeCode);
        String[] tokens = phrase.split("\\s+");
        String[] states = new String[tokens.length];

        for(int ti=0;ti<tokens.length;ti++) {
            //base token state
            String bts = BIE(TOKEN_PREFIX + tokens[ti].toLowerCase(), ti, tokens.length);
            if (!transitions.contains(bts)) {
                NEType.Type type = tokenTypes.get(tokens[ti].toLowerCase());
                if(type!=null) {
                    String state = BIE(TYPE_PREFIX + type.getCode(), ti, tokens.length);
                    states[ti] = state;
                }
                if(states[ti]==null)
                    states[ti] = BIE(WORD_CLASS_PREFIX + FeatureGeneratorUtil.tokenFeature(tokens[ti]), ti, tokens.length);
            } else states[ti] = bts;
        }

        System.out.println("States for " + phrase + " :: " + String.join(", ", states));

        double score = 0;
        for(int ti=0;ti<tokens.length-1;ti++){
            String s1 = states[ti];
            String s2 = states[ti+1];
            if(transitions.get(s1, s2)!=null)
                score += Math.log(transitions.getScore(s1, s2, t));
            else
                System.err.println("No transition from "+s1+"-"+s2);
        }

        System.err.println("Score for: "+phrase+" is "+score);
        return score;
    }

    @Override
    public void setTokenizer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    Tokenizer getTokenizer() {
        return tokenizer;
    }

    @Override
    Map<String, String> getGazette() {
        return gazette;
    }

    public static HMMModel train(Map<String, String> tdata){
        log.info(Util.getMemoryStats());

        float alpha = 0.2f;
        //page lengths from wikipedia
        Map<String,Map<String,Integer>> pageLens = SequenceModel.getTokenTypePriors();
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
        Learner trainer = new HMMModel.Learner(tdata, tokenPriors);
        trainer.learn();
        HMMModel model = new HMMModel();
        model.transitions = trainer.transitions;
        model.tokenTypes = trainer.tokenTypes;
        model.gazette = tdata;
        return model;
    }

    public static synchronized HMMModel loadModel(String modelPath) throws IOException {
        try {
            //the buffer size can be much higher than default 512 for GZIPInputStream
            HMMModel model = (HMMModel) Util.readObjectFromSerGZ(modelPath);
            return model;
        } catch (Exception e) {
            Util.print_exception("Exception while trying to load model from: " + modelPath, e, log);
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            String modelName = "dbpediaTest" + File.separator + "HMMModel-80.ser.gz";
            HMMModel model = HMMModel.loadModel(modelName);
            Span[] spans = model.find("Hello this is New York City and this is Steve Irwin! This is Vihari Piratla, I am from Rajahmundry. This is a project fuynded by Rajahmundry University");
            System.out.println("Found: " + Stream.of(spans).collect(Collectors.toList()));
            Stream.of(NEType.getAllTypes()).forEach(t -> {
                Learner.Transitions transitions = model.transitions;
                List<Pair<String, Double>> tt = transitions.transitions.entrySet()
                        .stream()
                        .flatMap(e1 -> e1.getValue().entrySet().stream()
                                .map(e2 -> new Pair<>(e1.getKey() + ":::" + e2.getKey(), transitions.getScore(e1.getKey(), e2.getKey(), t))))
                        .sorted((p1, p2) -> -Double.compare(p1.getSecond(), p2.getSecond()))
                        .limit(20)
                        .collect(Collectors.toList());
                System.out.println("Best phrases for type: " + t + " : " +
                        String.join("\n", tt.stream().map(Pair::toString).collect(Collectors.toList())));
            });
            Learner.Transition t = model.transitions.get("B:T:3", "E:TK:university");
            if(t!=null)
                System.out.println("Freq: " + t.getTotalFreq());
            //System.out.println(model.transitions.transitions);
        } catch (IOException ie) {
            ie.printStackTrace();
        }
//        Learner.Transitions transitions = new Learner.Transitions();
//        Learner.Transition t = new Learner.Transition();
//        transitions.add("I:WC:lc", "I:WC:dd", t);
//        transitions.add("I:WC:dd", "I:WC:lc", t);
//        transitions.add("I:WC:2d", "I:WC:4d", t);
//        transitions.add("I:WC:2d", "I:WC:an", t);
//        transitions.add("I:WC:4d", "I:WC:2d", t);
//        transitions.add("I:WC:an", "E:WC:an", t);
//        System.out.println(transitions.transitions.size());
    }
}
