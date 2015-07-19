package edu.stanford.muse.ner.train;

import edu.stanford.muse.ner.featuregen.FeatureGenerator;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.ner.util.ArchiveContent;

import java.util.*;

public interface NERTrainer {
    /**
     * @param archiveContent - it may not be feasible in every case to pass content array
     * @param externalGazz - External gazettes for training <key,value> pairs; where key is name and value is the type of the name
     * @param internalGazz - Gazette related to the archive
     * @param types - list of types to classification learning
     * @param aTypes - allowed types for each type in the types parameter
     * @param fgs - Feature generator
     * @param tokenizer - Tokenizer to identify mentions or candidates
     * @param params - training parameters for model to be trained
     * @return trained NER model*/
    NERModel train(ArchiveContent archiveContent, Map<String,String> externalGazz, Map<String,String> internalGazz, List<String> types, List<String[]> aTypes,
                   FeatureGenerator[] fgs, Tokenizer tokenizer, Object params);
}
