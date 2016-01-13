package edu.stanford.muse.ner.tokenizer;

import edu.stanford.muse.util.Triple;

import java.util.Set;
import java.util.List;

/**
 * A mention detector interface
 * Chunker is probably a more intuitive name for this.
 * */
public interface Tokenizer {
    /**
     * @param content to tokenize
     * @param pn - if set will make the CICTokenizer return person name like CIC tokens, does nothing is the instance is POSTokenizer
     * CICTokenizer uses different patterns to identify person and non-persons mentions.
     * @return list of triples that contain the token, begin offset and end offset of the token in the content*/
    List<Triple<String, Integer, Integer>> tokenize(String content, boolean pn);

    /**
     * Same as tokenize, but omits offset in the return value and returns the list of tokens*/
    Set<String> tokenizeWithoutOffsets(String content, boolean pn);
}
