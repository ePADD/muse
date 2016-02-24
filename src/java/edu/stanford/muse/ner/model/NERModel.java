package edu.stanford.muse.ner.model;

import edu.stanford.muse.util.Span;

public interface NERModel {
    /**
     * @param content - text in which to find entities
     * @returns map and offset, map with key the type of entity and offsets contain the string, start offset and end offset
     * @returns names recognised by the custom trained SVM model
     */
    Span[] find (String content);
}
