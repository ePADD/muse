package edu.stanford.muse.ner.model;

import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;

import java.util.*;

/**
 * Created by vihari on 24/02/16.
 */
public class DummyNERModel implements NERModel{
    CICTokenizer tokenizer = new CICTokenizer();
    public Pair<Map<Short,Map<String,Double>>, List<Triple<String, Integer, Integer>>> find (String content) {
        // collect pseudo proper nouns
        if (Util.nullOrEmpty(content)) {
            return new Pair(<new HashMap<>(), new ArrayList<>());
        }
        List<Triple<String, Integer, Integer>> pns = tokenizer.tokenize(content, false);
        //we will make a dummy object of type map
        Map<Short, Map<String,Double>> map = new LinkedHashMap<>();
        Short defType = FeatureDictionary.PERSON;
        map.put(defType,new LinkedHashMap<>());
        for(Triple<String,Integer,Integer> pn: pns)
            map.get(defType).put(pn.getFirst(), 1.0);

        return new Pair<>(map, pns);
    }
}
