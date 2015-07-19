package edu.stanford.muse.ner.tokenizer;

import edu.stanford.muse.util.Triple;

import java.util.Set;
import java.util.List;

/**
 * A mention detector interface
 * */
public interface Tokenizer {
    List<Triple<String, Integer, Integer>> tokenize(String content, boolean pn);
    Set<String> tokenizeWithoutOffsets(String content, boolean pn);
}
