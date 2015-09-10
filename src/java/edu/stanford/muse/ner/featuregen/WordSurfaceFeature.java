package edu.stanford.muse.ner.featuregen;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.ner.NER;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.ner.model.SVMModel;
import edu.stanford.muse.ner.train.SVMModelTrainer;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.webapp.SimpleSessions;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// @TODO: Good to have segmentation too.
// For example: "Clemens Hall" recognized with good confidence should segment out the entity in phrase like: "Party in Clemens Hall"
/**
 * All lexical and morphological features (surface features) which do not depend on the context, should go here
 * @note: keep the feature generation very clean and efficient, dont use regular expressions anywhere as that goes a long way
 *
 * Ideally this should contain more manipulatable features, each type in different class, but that leads to a lot of fragmentation
 *
 * END_PERIOD [BOOLEAN] - signals if the phrase ends with a period
 * CONTAINS_SPECIAL [BOOLEAN] - if the phrase has a special character
 * CONTAINS_MARKER [BOOLEAN][TYPE SPECIFIC] - if it contains the type specific markers such as Dr. Mrs. Inc. e.t.c.
 * CONTAINS_STOPWORD [BOOLEAN] - if the phrase contains one or more stop words
 * CONTAINS_PERIOD [BOOLEAN] - if the phrase contains a period in it
 * CONTAINS_DICT [BOOLEAN] - if the phrase contains a dictionary word
 * PATTERN [NOMINAL] - reflects the structure of the phrase ePADD-aA sOmE-aAaA
 * SUFFIX [NOMINAL] - suffix of every word in the phrase
 * PREFIX [NOMINAL] - prefix of every word in the phrase
 * WORDS [NOMINAL][TYPE SPECIFIC] - every word in the phrase
 * emitted words may differ depending on the entity type of interest. For organisations this feature generates
 * features that preserve the position. For example "The National Park"[ORG] -> "The*", "*National*", "*Park"
 * WORD_CLASS [NOMINAL] - generic word class of the phrase, very similar to OpenNLP wordclass feature
 */
public class WordSurfaceFeature extends FeatureGenerator implements Serializable{
	//use only an order preserving data structure
	List<Pair<String, Short>> featureGens = new ArrayList<Pair<String, Short>>();
    String END_PERIOD = "end-period", CONTAINS_SPECIAL = "contains-special",
    CONTAINS_MARKER = "contains-marker", CONTAINS_STOPWORD = "contains-sw",
    CONTAINS_PERIOD = "contains-period", CONTAINS_DICT = "contains-dict",
    PATTERN = "patt", SUFFIX = "suff",
    WORDS = "words", PREFIX = "pre",
    WORD_CLASS = "wc";
    private static final long							serialVersionUID	= 1L;

    public WordSurfaceFeature(){
		featureGens.add(new Pair<String, Short>("end-period", FeatureDictionary.BOOLEAN));
		featureGens.add(new Pair<String, Short>("contains-special", FeatureDictionary.BOOLEAN));
		featureGens.add(new Pair<String, Short>("contains-marker", FeatureDictionary.BOOLEAN));
		featureGens.add(new Pair<String, Short>("contains-sw", FeatureDictionary.BOOLEAN));
		featureGens.add(new Pair<String, Short>("contains-period", FeatureDictionary.BOOLEAN));
		featureGens.add(new Pair<String, Short>("contains-dict", FeatureDictionary.BOOLEAN));
		featureGens.add(new Pair<String, Short>("patt", FeatureDictionary.NOMINAL));
		featureGens.add(new Pair<String, Short>("suff", FeatureDictionary.NOMINAL));
        featureGens.add(new Pair<String, Short>("words", FeatureDictionary.NOMINAL));
        featureGens.add(new Pair<String, Short>("pre", FeatureDictionary.NOMINAL));
        //wordclass
		featureGens.add(new Pair<String, Short>("wc", FeatureDictionary.NOMINAL));
	}

    public WordSurfaceFeature (Set<String> featureTypes){
        if(featureTypes.contains(END_PERIOD))
            featureGens.add(new Pair<String, Short>("end-period", FeatureDictionary.BOOLEAN));
        if(featureTypes.contains(CONTAINS_SPECIAL))
            featureGens.add(new Pair<String, Short>("contains-special", FeatureDictionary.BOOLEAN));
        if(featureTypes.contains(CONTAINS_MARKER))
            featureGens.add(new Pair<String, Short>("contains-marker", FeatureDictionary.BOOLEAN));
        if(featureTypes.contains(CONTAINS_STOPWORD))
            featureGens.add(new Pair<String, Short>("contains-sw", FeatureDictionary.BOOLEAN));
        if(featureTypes.contains(CONTAINS_PERIOD))
            featureGens.add(new Pair<String, Short>("contains-period", FeatureDictionary.BOOLEAN));
        if(featureTypes.contains(CONTAINS_DICT))
            featureGens.add(new Pair<String, Short>("contains-dict", FeatureDictionary.BOOLEAN));
        if(featureTypes.contains(PATTERN))
            featureGens.add(new Pair<String, Short>("patt", FeatureDictionary.NOMINAL));
        if(featureTypes.contains(SUFFIX))
            featureGens.add(new Pair<String, Short>("suff", FeatureDictionary.NOMINAL));
        if(featureTypes.contains(WORDS))
            featureGens.add(new Pair<String, Short>("words", FeatureDictionary.NOMINAL));
        if(featureTypes.contains(PREFIX))
            featureGens.add(new Pair<String, Short>("pre", FeatureDictionary.NOMINAL));
        if(featureTypes.contains(WORD_CLASS))
            featureGens.add(new Pair<String, Short>("wc", FeatureDictionary.NOMINAL));
    }

	private static void put(Map<String,List<String>> features, String dim, String val) {
		if (!features.containsKey(dim))
			features.put(dim, new ArrayList<String>());
		features.get(dim).add(val);
	}

	@Override
	public List<Pair<String,Short>> getFeatureTypes(){
		return featureGens;
	}

	@Override
	public Boolean getContextDependence(){
		return false;
	}

	@Override
	public Map<String,List<String>> createFeatures(String name, Pair<Integer,Integer> offsets, String content, Short iType){
		return createFeatures(name, iType);
	}

	public static Pair<String,Boolean> checkAndStrip(String name, String[] markers, Boolean start, Boolean strip){
		if(markers == null)
			return new Pair<String, Boolean>(name, false);

		//TODO: replace this regular expression, is there a better way to get words in string?
		String[] words = name.split("\\s+");
		Set<String> ms = new HashSet<String>();
		for(String marker: markers)
			ms.add(marker);

		String cn = "";
		boolean marker = false;
		for(int i=0;i<words.length;i++){
			String word = words[i];
			if(((i==words.length-1 && !start) || (i==0 && start)) && ms.contains(word.toLowerCase())) {
				marker = true;
				if (!strip)
					cn += word+" ";
			}else
				cn += word+" ";
		}
		if(cn.length()>0 && cn.charAt(cn.length()-1) == ' ')
			cn = cn.substring(0,cn.length()-1);
		return new Pair<String,Boolean>(cn, marker);
	}

	//computes the word feature.
	//TODO: Some very common words like: William, mike etc. appear in the dictionary, bad?
	public Map<String,List<String>> createFeatures(String name, Short iType) {
        if(name == null || name.length()>200)
            return null;
		Map<String,List<String>> features = new LinkedHashMap<String, List<String>>();

		String[] startMarkers = FeatureDictionary.startMarkersForType.get(iType);
		String[] endMarkers = FeatureDictionary.endMarkersForType.get(iType);
		//thought this is efficient than name.matches as the regex of latter needs .* to be appended
		//String temp = name.replaceAll(FeatureDictionary.MARKERS_PATT, "");
		Pair<String,Boolean> p1 = checkAndStrip(name, startMarkers, true, FeatureDictionary.PERSON.equals(iType));
		Pair<String,Boolean> p2 = checkAndStrip(p1.getFirst(), endMarkers, false, false);
		boolean marker = p1.getSecond() || p2.getSecond();
		name = p2.getFirst();

		if (marker)
			put(features, "contains-marker", "exists");
		else
			put(features, "contains-marker", "no");

		//word features.
		String tc = FeatureGeneratorUtil.tokenFeature(name);
		put(features, "wc", tc);
		//System.err.println("Word class: " + tc + " for: " + name + "\t" + t);

		//end-period?
		if (name.length() > 1) {
			if (name.charAt(name.length() - 1) == '.')
				put(features,"end-period", "exists");
			else
				put(features, "end-period", "no");
		} else
			put(features, "end-period", "no");

		//contains period
		if (name.contains("."))
			put(features, "contains-period", "exists");
		else
			put(features, "contains-period", "no");

		//any special chars like apos, hypen, apresand
		if (name.contains("'") || name.contains("-") || name.contains("&"))
			put(features, "contains-special", "exists");
		else
			put(features, "contains-special", "no");

		//pattern, akin to summarised pattern
		String patt = name.replaceAll("[A-Z]+", "A");
		patt = patt.replaceAll("[a-z]+", "a");
		patt = patt.replaceAll("[0-9]+", "d");
		put(features, "patt", patt);

		//take prefix and suffix of every single word.
		String[] words = name.split("\\s+");
		for (String word : words) {
			if (word.length() > 2) {
				String pre = word.substring(0, 3);
				String suff = word.substring(Math.max(0, word.length() - 3), word.length());
				put(features,"pre", pre);
				put(features,"suff", suff);
			}
		}

		//check for stop-words
		boolean sws = false;
		for (String word : words)
			if (Indexer.MUSE_STOP_WORDS_SET.contains(word.toLowerCase())) {
				sws = true;
				break;
			}
		if (sws)
			put(features,"contains-sw", "exists");
		else
			put(features,"contains-sw", "no");

		//contains dictionary word?
		boolean dict = false;
		for (String word : words)
			if (DictUtils.fullDictWords.contains(word.toLowerCase())) {
				dict = true;
				break;
			}

		if (dict)
			put(features,"contains-dict", "exists");
		else
			put(features,"contains-dict", "no");

		//signals if the name contains signals like "W." in W.S. Merwin, George W. Bush
		//		boolean nameLikeAcronym = false;
		//		for (String word : words) {
		//			if (word.matches("[A-Z]\\.") || word.matches("[A-Z]\\.[A-Z]\\."))
		//				nameLikeAcronym = true;
		//			if (word.matches("[A-Z]") && !word.equals("I") && !word.equals("A"))
		//				nameLikeAcronym = true;
		//		}
		//		wf.put("contains-nla", (nameLikeAcronym ? "exists" : "no"));

		//signals if the name contains acronyms related to orgs such as Inc., Co. 
		//		double orgLikeAcronym = 0.0;
		//		String[] knownAcrs = new String[] { "Inc.", "Co.", "Ltd.", "Corp.", "Bros.", "Govt.", "Pvt.", "Dept.", "Mfg." };
		//		for (String word : words)
		//			for (String knownAcr : knownAcrs) {
		//				if (word.equals(knownAcr)) {
		//					orgLikeAcronym = 1.0;
		//					continue;
		//				}
		//				if (word.matches("[A-Z][a-z]+\\."))
		//					orgLikeAcronym = 0.2;
		//			}
		//		wf.put("contains-ola", orgLikeAcronym + "");

		double freqs = 0, minfreq = 10, maxfreq = -1;
		Pattern endClean = Pattern.compile("^\\W+|\\W+$");
		/** Using patterns like this improved the accuracy in the case of orgs. */
		String t = null;
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			if (i > 0 && i < (words.length - 1))
			    t = "*" + word + "*";
			else if (i == 0 && words.length > 1)
			    t = word + "*";
			else if (i == (words.length - 1) && words.length > 1)
			    t = "*" + word;
			//references are generally made with first name and this may have continuation like Harvard University or Apple_Inc
			else t = word;

			//emit all the words or patterns
			if(t!=null)
				put(features, "words",t);
		}
		//The performance over the test set without averaging and with averaging respectively are:
		//Accuracy:0.9113924050632911, Recall:0.8648648648648649, F1:0.8875192604006163
		//Accuracy:0.9597701149425287, Recall:0.9009009009009009, F1:0.9294042351893591
		//mainly because it misses out on single word names that have a low ptype score like Irving, marta etc.
//		if (numLongWords > 0)
//			freqs /= (double) numLongWords;
//
//		//Person, Org and Geographic
//		wf.put("pfreq", "" + freqs);

		//Stats for orgs without the first and last words as features and with first and last word counts respectively are
		//Accuracy:0.6, Recall:0.775, F1:0.6763636363636363
		//Accuracy:0.6382978723404256, Recall:0.675, F1:0.6561360874848117
		//The decrease in recall could be because of bad segmentation. Even though the model is trained on "National Park Service", could not recognise "The National Park Service"
		//Similarly couldn't recognize "Harvard University" in "Harvard University on Friday"
		//		//Especially in orgs case, the first and last words have larger signal.
		//		Pair<Integer, Integer> p = wfs.gFreqs.get(words[0]);
		//		if (p != null)
		//			wf.put("pfreq-fw", "" + (double) p.first / (double) (p.first + p.second));
		//		if (words.length > 1) {
		//			p = wfs.gFreqs.get(words[words.length - 1]);
		//			if (p != null) {
		//				wf.put("pfreq-lw", "" + (double) p.first / (double) (p.first + p.second));
		//			}
		//		}

		//Stats when maxpfreq is emitted as dimension and not respectively are:
		//Accuracy:0.6, Recall:0.775, F1:0.6763636363636363
		//Accuracy:0.6444444444444445, Recall:0.7, F1:0.6710743801652892
		//This emision does not make sense when there is a single word and hence puts higher threshold. (explanation for stats above)
//		wf.put("minpfreq", "" + (minfreq == 10 ? -1 : minfreq));
//		wf.put("maxpfreq", "" + (maxfreq == 10 ? -1 : maxfreq));
		//wf.put("freq-gazz", "" + (double) (pfreqs) / (double) wfs.dbpedia.size());
		return features;
	}

	public static void main(String[] args) {
		try {
			WordSurfaceFeature wsf = new WordSurfaceFeature();
            Map<String, List<String>> map = wsf.createFeatures("Sunday",FeatureDictionary.ORGANISATION);
            for(String k: map.keySet())
                System.err.println(k+":"+new LinkedHashSet<>(map.get(k)));
            String mwl = System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator;
            String modelFile = mwl + SVMModel.modelFileName;
            //SVMModel nerModel = SVMModel.loadModel(new File(modelFile));
        }catch(Exception e){
			e.printStackTrace();
		}
	}
}
