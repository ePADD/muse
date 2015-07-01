package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.util.Pair;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by viharipiraurtla on 27/05/15.
 *
 * For the sake of simplicity many features are merged in just two files WordFeature and ContextFeature
 * generally more flexibility on the features is required, and features should be selected through the constructor.
 * Thats a TODO*/
abstract public class FeatureGenerator implements Serializable{
    private static final long							serialVersionUID	= 1L;
    public abstract Map<String,List<String>> createFeatures(String name, Pair<Integer,Integer> offsets, String content, String iType);
    public abstract Boolean getContextDependence();
    public abstract List<Pair<String,Short>> getFeatureTypes();

    public static Map<String,List<String>> generateFeatures(String name, Pair<Integer,Integer> offsets, String content, String iType, FeatureGenerator[] fgs){
        Map<String,List<String>> features = new LinkedHashMap<String, List<String>>();
        for(FeatureGenerator fg: fgs) {
            Map<String,List<String>> temp = fg.createFeatures(name,offsets,content, iType);
            for(String dim: temp.keySet())
                features.put(dim,temp.get(dim));
        }
        return features;
    }
}
