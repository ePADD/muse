package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;

import java.io.Serializable;
import java.util.*;

/**
 * Created by viharipiratla on 27/05/15.
 *
 * All features that depend on context should go here*/
public class ContextFeature extends FeatureGenerator {
    List<Pair<String,Short>> featureTypes = new ArrayList<Pair<String, Short>>();
    private static final long							serialVersionUID	= 1L;

    public ContextFeature(){
        featureTypes.add(new Pair<String, Short>("position", FeatureDictionary.NOMINAL));
        featureTypes.add(new Pair<String,Short>("bow",FeatureDictionary.NOMINAL));
        featureTypes.add(new Pair<String,Short>("has-marker", FeatureDictionary.BOOLEAN));
    }

    private static void put(Map<String,List<String>> features, String dim, String val) {
        if (!features.containsKey(dim))
            features.put(dim, new ArrayList<String>());
        features.get(dim).add(val);
    }

    @Override
    public List<Pair<String,Short>> getFeatureGens(){
        return featureTypes;
    }

    @Override
    public Boolean getContextDependence(){
        return true;
    }

    @Override
    public Map<String,List<String>> createFeatures(String name, Pair<Integer,Integer> offset, String content, String iType) {
        Map<String,List<String>> features = new LinkedHashMap<String, List<String>>();

        //identify the offset of the first line and the signature
        String[] lines = content.split("\\n|<br>");
        int firstlineend = lines[0].length();
        int signaturestart = Integer.MAX_VALUE;
        if(lines.length>0) {
            for (int l = lines.length - 1; l >= 0; l--) {
                if(lines[l].length()>40)
                    continue;
                signaturestart = content.indexOf(lines[lines.length-1]);
            }
        }
        if(offset.getSecond()<=firstlineend)
            put(features, "position","start");
        else if(offset.getFirst()>=signaturestart)
            put(features, "position","end");
        else put(features, "position","middle");

        //contexts, left and right window
        int window = 2;
        String prev = content.substring(Math.max(offset.getFirst()-50,0),offset.getFirst());
        String nxt = content.substring(offset.getSecond(),Math.min(content.length(),offset.getSecond()+50));
        String[] prevWords = prev.split("\\s+");
        String[] nxtWords = nxt.split("\\s+");
        for(int i=1;i<window+1;i++) {
            if(prevWords.length-i-1 >= 0)
                put(features, "bow", "-"+i+":"+prevWords[prevWords.length-1-i].toLowerCase());
            if(i < nxtWords.length)
                put(features, "bow", "+"+i+":"+nxtWords[i].toLowerCase());
        }

        //dont look in to the string for markers, but in the surrounding context
        int pos = 1;
        String prefix = null, suffix = null;
        if(prevWords.length-pos-1 >= 0)
            prefix = prevWords[prevWords.length-1-pos];
        if(pos < nxtWords.length)
            suffix = nxtWords[pos];

        String[] markers = new String[]{"dear","hi","hello", "mr", "mrs", "miss", "sir", "madam"};
        if(prefix!=null && Arrays.asList(markers).contains(prefix.toLowerCase()))
            put(features, "has-marker", "exists");
        else
            put(features, "has-marker","no");

        return features;
    }
}
