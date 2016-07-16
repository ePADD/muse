package edu.stanford.muse.ner.model;

import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Span;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;

import java.util.*;

/**
 * Created by vihari on 24/02/16.
 */
public class DummyNERModel implements NERModel{
    CICTokenizer tokenizer = new CICTokenizer();
    public Span[] find (String content) {
        Short defType = FeatureDictionary.PERSON;
        List<Triple<String, Integer, Integer>> pns = tokenizer.tokenize(content, false);
        List<Span> names = new ArrayList<>();
        pns.forEach(tok->{
            Span sp = new Span(tok.first,tok.second,tok.third);
            sp.setType(defType,1.0f);
            names.add(sp);
        });
        return names.toArray(new Span[names.size()]);
    }
}
