package edu.stanford.muse.ner.model;

import com.google.common.collect.Multimap;
import edu.stanford.muse.Config;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.tokenize.Tokenizer;
import edu.stanford.muse.util.*;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for all NER models that learns over gazettes and scores a given phrase to be of certain type
 * This class implements a dynamic programming based sequence labeler that uses the score assigned by "getConditional" to find the best sequence labeling
 * */
public abstract class NERModel {
    private static Log log = LogFactory.getLog(NERModel.class);

    abstract void setTokenizer(Tokenizer tokenizer);
    abstract Tokenizer getTokenizer();

    abstract Map<String, String> getGazette();

    abstract double getConditional(String phrase, Short type);

    protected String lookup(String phrase) {
        //if the phrase is from CIC Tokenizer, it won't start with an article
        //enough with the confusion between [New York Times, The New York Times], [Giant Magellan Telescope, The Giant Magellan Telescope]
        Set<String> vars = new LinkedHashSet<>();
        vars.add(phrase);
        vars.add("The "+phrase);
        String type;
        for(String var: vars) {
            type = getGazette().get(var.toLowerCase());
            if(type!=null) {
                log.debug("Found a match for: "+phrase+" -- "+type);
                return type;
            }
        }
        return null;
    }

    /**
     * Returns a probabilistic measure for
     * @param phrase to be a noun
     * @param nonNoun if true, then returns P(~noun/phrase) = 1-P(noun/phrase)
     * */
    private static double getNounLikelihood(String phrase, boolean nonNoun) {
        phrase = phrase.replaceAll("^\\W+|\\W+$", "");
        if (phrase.length() == 0) {
            if (nonNoun)
                return 1;
            else
                return 1.0 / Double.MAX_VALUE;
        }

        String[] tokens = phrase.split("\\s+");
        double p = 1;
        for (String token : tokens) {
            String orig = token;
            token = token.toLowerCase();
            List<String> noise = Arrays.asList("P.M", "P.M.", "A.M.", "today", "saturday", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december", "thanks");
            if (noise.contains(token)) {
                if (nonNoun)
                    p *= 1;
                else
                    p *= 1.0 / Double.MAX_VALUE;
                continue;
            }
            //Map<String,Pair<Integer,Integer>> map = EnglishDictionary.getDictStats();
            //Pair<Integer,Integer> pair = map.get(token);
            Multimap<String, Pair<String, Integer>> map = EnglishDictionary.getTagDictionary();//getDictStats();
            Collection<Pair<String, Integer>> pairs = map.get(token);

            if (pairs == null) {
                //log.warn("Dictionary does not contain: " + token);
                if (orig.length() == 0) {
                    if (nonNoun)
                        p *= 1;
                    else
                        p *= 1.0 / Double.MAX_VALUE;
                }
                if (orig.charAt(0) == token.charAt(0)) {
                    if (nonNoun)
                        p *= 1;
                    else
                        p *= 1.0 / Double.MAX_VALUE;
                } else {
                    if (nonNoun)
                        p *= 1.0 / Double.MAX_VALUE;
                    else
                        p *= 1.0;
                }
                continue;
            }
            //double v = (double) pair.getFirst() / (double) pair.getSecond();
            double v = pairs.stream().filter(pair->pair.first.startsWith("NN")||pair.first.startsWith("JJ")).mapToDouble(pair->pair.second).sum();
            v /= pairs.stream().mapToDouble(pair->pair.second).sum();
            //if (v > 0.25) {
            if(v > 0.25) {
                if (nonNoun)
                    return 1.0 / Double.MAX_VALUE;
                else
                    return 1.0;
            } else {
                if (token.charAt(0) == orig.charAt(0)) {
                    if (nonNoun)
                        return 1;
                    else
                        return 1.0 / Double.MAX_VALUE;
                } else {
                    if (nonNoun)
                        return 1.0 / Double.MAX_VALUE;
                    else
                        return 1.0;
                }
            }
        }
        return p;
    }

    /**
     * Does sequence labeling of a phrase with type -- a dynamic programming approach
     * The complexity of this method has quadratic dependence on number of words in the phrase, hence should be careful with the length (a phrase with more than 7 words is rejected)
     * O(T*W^2) where W is number of tokens in the phrase and T is number of possible types
     * Note: This method only returns the entities from the best labeled sequence.
     * @param phrase - String that is to be sequence labelled, keep this short; The string will be rejected if it contains more than 9 words
     * @return all the entities along with their types and quality score found in the phrase
     */
    private Map<String, Pair<Short, Double>> seqLabel(String phrase) {
        Map<String, Pair<Short, Double>> segments = new LinkedHashMap<>();
        {
            String dbpediaType = lookup(phrase);
            NEType.Type type = NEType.parseDBpediaType(dbpediaType);

            if (dbpediaType != null && (phrase.contains(" ") || dbpediaType.endsWith("Country|PopulatedPlace|Place"))) {
                segments.put(phrase, new Pair<>(type.getCode(), 1.0));
                return segments;
            }
        }

        //This step of uncanonicalizing phrases helps merging things that have different capitalization and in lookup
        phrase = EmailUtils.uncanonicaliseName(phrase);

        if (phrase == null || phrase.length() == 0)
            return new LinkedHashMap<>();
        phrase = phrase.replaceAll("^\\W+|\\W+^", "");

        String[] tokens = phrase.split("\\s+");

        /**
         * In TW's sub-archive with ~65K entities scoring more than 0.001. The stats on frequency of #tokens per word is as follows
         * Freq  #tokens
         * 36520 2
         * 15062 3
         * 5900  4
         * 2645  5
         * 2190  1
         * 1301  6
         * 721   7
         * 18    8
         * 9     9
         * 2     10
         * 1     11
         * Total: 64,369 -- hence the cutoff below
         */
        if (tokens.length > 9) {
            return new LinkedHashMap<>();
        }
        //since there can be large number of types that any token can take
        //we restrict the number of possible types we consider to the top 5
        //see the complexity of the method
        Set<Short> cands = Stream.of(NEType.getAllTypeCodes()).collect(Collectors.toSet());
//        for (String token : tokens) {
//            Map<Short, Double> candTypes = new LinkedHashMap<>();
//            if (token.length() != 2 || token.charAt(1) != '.')
//                token = token.replaceAll("^\\W+|\\W+$", "");
//            token = token.toLowerCase();
//            MU mu = mixtures.get(token);
//            if (token.length() < 2 || mu == null || mu.numMixture == 0) {
//                //System.out.println("Skipping: "+token+" due to mu "+mu==null);
//                continue;
//            }
//            for (Short candType : NEType.getAllTypeCodes()) {
//                double val = mu.getLikelihoodWithType(candType);
//                candTypes.put(candType, candTypes.getOrDefault(candType, 0.0) + val);
//            }
//            List<Pair<Short, Double>> scands = Util.sortMapByValue(candTypes);
//            int si = 0, MAX = 5;
//            for (Pair<Short, Double> p : scands)
//                if (si++ < MAX)
//                    cands.add(p.getFirst());
//        }
        //This is just a standard dynamic programming algo. used in HMMs, with the difference that
        //at every word we are checking for the every possible segment (or chunk)
        short NON_NOUN = -2;
        cands.add(NON_NOUN);
        Map<Integer, Triple<Double, Integer, Short>> tracks = new LinkedHashMap<>();
        Map<Integer,Integer> numSegmenation = new LinkedHashMap<>();
        //System.out.println("Cand types for: "+phrase+" "+cands);

        for (int ti = 0; ti < tokens.length; ti++) {
            double max = -1, bestValue = -1;
            int bi = -1;
            short bt = -10;
            for (short t : cands) {
                int tj = Math.max(ti - 6, 0);
                //don't allow multi word phrases with these types
                if (t == NON_NOUN || t == NEType.Type.OTHER.getCode())
                    tj = ti;
                for (; tj <= ti; tj++) {
                    double val = 1;
                    if (tj > 0)
                        val *= tracks.get(tj - 1).first;
                    String segment = "";
                    for (int k = tj; k < ti + 1; k++) {
                        segment += tokens[k];
                        if (k != ti)
                            segment += " ";
                    }

                    if (NON_NOUN != t)
                        val *= getConditional(segment, t) * getNounLikelihood(segment, false);
                    else
                        val *= getNounLikelihood(segment, true);

                    double ov = val;
                    int numSeg = 1;
                    if(tj>0)
                        numSeg += numSegmenation.get(tj-1);
                    val = Math.pow(val, 1f/numSeg);
                    if (val > max) {
                        max = val;
                        bestValue = ov;
                        bi = tj - 1;
                        bt = t;
                    }
                    //System.out.println("Segment: "+segment+" type: "+t+" val: "+ov+" bi:"+bi+" bv: "+bestValue+" bt: "+bt);
                }
            }
            numSegmenation.put(ti, ((bi>=0)?numSegmenation.get(bi):0)+1);
            tracks.put(ti, new Triple<>(bestValue, bi, bt));
        }
        //System.out.println("Tracks: "+tracks);

        //the backtracking step
        int start = tokens.length - 1;
        while (true) {
            Triple<Double, Integer, Short> t = tracks.get(start);
            String seg = "";
            for (int ti = t.second + 1; ti <= start; ti++)
                seg += tokens[ti] + " ";
            seg = seg.substring(0,seg.length()-1);

            double val;
            if(NON_NOUN != t.getThird())
                val = getConditional(seg, t.getThird()) * getNounLikelihood(seg, false);
            else
                val = getNounLikelihood(seg, true);

            //if is a single word and a dictionary word or word with less than 4 chars and not acronym, then skip the segment
            if (seg.contains(" ") ||
                    (seg.length() >= 3 &&
                            (seg.length() >= 4 || FeatureGeneratorUtil.tokenFeature(seg).equals("ac")) &&
                            !DictUtils.commonDictWords.contains(EnglishDictionary.getSingular(seg.toLowerCase()))
                    ))
                segments.put(seg, new Pair<>(t.getThird(), val));

            start = t.second;
            if (t.second == -1)
                break;
        }
        return segments;
    }


    //returns token -> {redirect (can be the same as token), page length of the page it redirects to}
    static Map<String,Map<String,Integer>> getTokenTypePriors(){
        Map<String,Map<String,Integer>> pageLengths = new LinkedHashMap<>();
        log.info("Parsing token types...");
        try{
            LineNumberReader lnr = new LineNumberReader(new InputStreamReader(Config.getResourceAsStream("TokenTypes.txt")));
            String line;
            while((line=lnr.readLine())!=null){
                String[] fields = line.split("\\t");
                if(fields.length!=4){
                    log.warn("Line --"+line+"-- has an unexpected pattern!");
                    continue;
                }
                int pageLen = Integer.parseInt(fields[3]);
                String redirect = fields[2];
                //if the page is not a redirect, then itself is the title
                if(fields[2] == null || fields[2].equals("null"))
                    redirect = fields[1];
                String lc = fields[0].toLowerCase();
                if(!pageLengths.containsKey(lc))
                    pageLengths.put(lc, new LinkedHashMap<>());
                pageLengths.get(lc).put(redirect, pageLen);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        return pageLengths;
    }


    static Map<String, Map<String, Float>> getNormalizedTokenPriors(Map<String, String> tdata, float alpha){
        //page lengths from wikipedia
        Map<String,Map<String,Integer>> pageLens = getTokenTypePriors();
        //getTokenPriors returns Map<String, Map<String,Integer>> where the first key is the single word DBpedia title and second keys are the titles it redirects to and its page length
        Map<String,Map<String,Float>> tokenPriors = new LinkedHashMap<>();
        //The Dir. prior related param alpha is empirically found to be performing at the value of 0.2f
        for(String tok: pageLens.keySet()) {
            Map<String,Float> tmp =  new LinkedHashMap<>();
            Map<String,Integer> tpls = pageLens.get(tok);
            for(String page: tpls.keySet()) {
                String type = tdata.get(page.toLowerCase());
                tmp.put(type, tpls.get(page)*alpha/1000f);
            }
            tokenPriors.put(tok, tmp);
        }
        return tokenPriors;
    }

    /**
     * @param content - text in which to find entities
     * @return spans of text found in the content that contain the type and offset info. of the entity
     */
    public Span[] find (String content){
        List<Span> spans = new ArrayList<>();

        opennlp.tools.util.Span[] sentSpans = NLPUtils.tokenizeSentenceAsSpan(content);
        assert sentSpans!=null;
        for(opennlp.tools.util.Span sentSpan: sentSpans) {
            String sent = sentSpan.getCoveredText(content).toString();
            int sstart = sentSpan.getStart();

            List<Triple<String, Integer, Integer>> toks = getTokenizer().tokenize(sent);
            for (Triple<String, Integer, Integer> t : toks) {
                //this should never happen
                if(t==null || t.first == null)
                    continue;

                Map<String,Pair<Short,Double>> entities = seqLabel(t.getFirst());
                for(String e: entities.keySet()){
                    Pair<Short,Double> p = entities.get(e);
                    //A new type is assigned to some words, which is of value -2
                    if(p.first<0)
                        continue;

                    if(!p.first.equals(NEType.Type.OTHER.getCode()) && p.second>0) {
                        Span chunk = new Span(e, sstart + t.second + t.first.indexOf(e), sstart + t.second + t.first.indexOf(e) + e.length());
                        chunk.setType(p.first, new Float(p.second));
                        spans.add(chunk);
                    }
                }
            }
        }
        return spans.toArray(new Span[spans.size()]);
    }
}
