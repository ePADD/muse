package edu.stanford.muse.ner.model;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;

import java.util.List;
import java.util.Map;

public interface NERModel {
    /**
     * @param content - text in which to find entities
     * @returns map and offset, map with key the type of entity and offsets contain the string, start offset and end offset
     * @returns names recognised by the custom trained SVM model
     */
    Pair<Map<Short,Map<String,Double>>, List<Triple<String, Integer, Integer>>> find (String content);
}
