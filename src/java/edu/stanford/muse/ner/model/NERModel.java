package edu.stanford.muse.ner.model;

import edu.stanford.muse.util.Span;

public interface NERModel {
    /**
     * @param content - text in which to find entities
     * spans of text found in the content that contain the type and offset info. of the entity
     */
    Span[] find (String content);
}
