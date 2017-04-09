package edu.stanford.muse.ner.model;

import edu.stanford.muse.util.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * Created by vihari on 09/04/17.
 * Implementation of http://dl.acm.org/citation.cfm?id=1014058 "Mining reference tables for automatic text segmentation"
 */
public class HMMModelLeearner extends RuleInducer {
    HMMModelLeearner(Map<String, String> gazettes, Map<String, Map<String, Float>> tokenPriors) {
        super(gazettes, tokenPriors);
    }

    static class TransitionStat{
        Map<NEType.Type, Integer> freq = new LinkedHashMap<>();
        void add(NEType.Type type){
            freq.put(type, freq.getOrDefault(type, 0)+1);
        }
        int getFreq(NEType.Type t){
            return freq.getOrDefault(t, 0);
        }
    }

    @Override
    void learn() {
        Map<String, Map<String, TransitionStat>> tstats = new LinkedHashMap<>();
        //set transistion freqs and collect word freqs
        Map<String, Long> wordFreqs = getGazettes().entrySet().stream()
                //.parallel()
                .flatMap(e->{
                    String[] tokens = e.getKey().split("\\s+");
                    NEType.Type type = NEType.parseDBpediaType(e.getValue());
                    for(int ti=0;ti<tokens.length-1;ti++) {
                        String t1 = tokens[ti], t2 = tokens[ti+1];
                        if (!tstats.containsKey(t1))
                            tstats.put(t1, new LinkedHashMap<>());
                        if(!tstats.get(t1).containsKey(t2))
                            tstats.get(t1).put(t2, new TransitionStat());
                        tstats.get(t1).get(t2).add(type);
                    }
                    return Stream.of(tokens);
                }).collect(groupingBy(Function.identity(), counting()));

        int LIMIT_TOKENS = 100000;

        //collect states
        List<String> states = new ArrayList<>();
        wordFreqs.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(LIMIT_TOKENS)
                .map(Map.Entry::getKey)
                .forEach(s->states.add("tok:"+s));

        //add to states all the types
        Stream.of(NEType.getAllTypeCodes()).forEach(t->states.add("typ:"+t));

        //add word classes
        //from opennlp's FastTokenClassFeatureGenerator.tokenFeature
        Stream.of(new String[]{"lc", "2d", "4d", "an", "dd", "ds", "dc", "dp", "num", "sc", "ac", "cp", "ic", "other"})
                .forEach(wc->states.add("wc:"+wc));

        //compute transition frequencies

        //propagate frequencies
        //make and set mixtures
    }
}
