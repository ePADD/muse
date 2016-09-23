package edu.stanford.muse.ner.segmentation;

import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.ner.featuregen.FeatureUtils;
import edu.stanford.muse.ner.featuregen.FeatureVector;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

/**
 * Created by viharipiratla on 22/05/15.
 *
 * Creates features for segmentation
 */
public class SegmentFeatures {
    public static String MARKERS_PATT = "^([Dd]ear|[Hh]i|[hH]ello|[Mm]r|[Mm]rs|[Mm]iss|[Ss]ir|[Mm]adam|[Dd]r\\.|[Pp]rof\\.)\\W+";
    static int NUM_FEATURES = 3;
    //fills the classifiedtype value upon return
    public static double[] genarateFeatures(String phrase,FeatureUtils wfs,svm_model model){
        String cp = phrase;
        phrase = phrase.replaceAll(MARKERS_PATT, "");
        //TODO: what is the type that is being segmented here? Review the next line
        FeatureVector wfv = wfs.getVector(cp, FeatureUtils.PERSON);
        svm_node[] sx = wfv.getSVMNode();
        double[] probs = new double[2];
        double prob = svm.svm_predict_probability(model,sx,probs);
        double[] features = new double[NUM_FEATURES];
        String[] words = phrase.split("\\s+");
        features[0] = probs[0];
        features[1] = (double)words.length/5;
        int numMatches = 0;
//        for(Map<String,String> gazz: gazettes)
//            if(gazz.containsKey(phrase))
//                numMatches++;
//        //direct match to gazette?
//        features[2] = (double)numMatches/2;
        phrase = IndexUtils.stripExtraSpaces(phrase);
        int c = 0;
        if(wfs.counts.containsKey(phrase))
            c += wfs.counts.get(phrase);
        //Integer c = wfs.counts.get(phrase);
        c = c > 5 ? 1 : 0;
        features[2] = (double) c / 10;

        double namelike = 0;

//        if(words.length>0)
//            namelike /= words.length;
        return features;
    }
}