package edu.stanford.muse.ner.tokenizer;

import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.util.NLPUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import opennlp.tools.util.Span;

import java.util.*;

/**
 * Created by vihari on 09/10/15.
 *
 * A tokenizer based on POS tagging */
public class POSTokenizer {
    public List<Triple<String, Integer, Integer>> tokenize(String content, Short type){
        Span[] sents = NLPUtils.sentenceDetector.sentPosDetect(content);
        List<Triple<String, Integer, Integer>> ret = new ArrayList<>();
        for(Span span: sents) {
            List<Pair<String, Triple<String,Integer,Integer>>> posTags = NLPUtils.posTagWithOffsets(span.getCoveredText(content).toString());
            List<String> allowedPOSTags;
            if (type == FeatureDictionary.PERSON)
                allowedPOSTags = Arrays.asList("NNP", "NNS", "NN");
            else
                allowedPOSTags = Arrays.asList("NNP", "NNS", "NN", "JJ", "IN", "POS");

            String str = "";
            int startOffset = -1, endOffset = -1;
            int so = span.getStart();
            for (Pair<String, Triple<String,Integer,Integer>> p : posTags) {
                String tag = p.second.first;
                //POS for 's
                if (allowedPOSTags.contains(tag)) {
                    if(str.equals(""))
                        startOffset = p.second.getSecond();
                    str += p.getFirst() + "[" + tag + "]" + " ";
                    endOffset = p.second.getThird();
                }
                else {
                    if(!str.equals("")) {
                        //strip the last space
                        str = str.substring(0,str.length()-1);
                        ret.add(new Triple<>(str, so + startOffset, so + endOffset));
                        str = "";
                        startOffset = -1;
                        endOffset = -1;
                    }
                }
            }
            if (!str.equals("")) {
                str = str.substring(0,str.length()-1);
                ret.add(new Triple<>(str, so + startOffset, so + endOffset));
            }
        }
        return ret;
    }

    public Set<String> tokenizeWithoutOffsets(String content, Short type){
        List<Triple<String,Integer,Integer>> tokensWithOffsets = tokenize(content, type);
        Set<String> tokens = new LinkedHashSet<>();
        for(Triple<String,Integer,Integer> t: tokensWithOffsets)
            tokens.add(t.first);
        return tokens;
    }

    public static void main(String[] args){
        System.err.println(CICTokenizer.personNamePattern.pattern());
        POSTokenizer tok = new POSTokenizer();
        String content = "Hello, I am Vihari, Piratla of IIT Mandi, I went to college in UCB, GH then joined NASA after a brief tenure at CERN";
        List<Triple<String,Integer,Integer>> names = tok.tokenize(content, FeatureDictionary.PLACE);
        //names = tok.tokenizeWithoutOffsets("January 30, 2002: University at Buffalo, Center for Tomorrow, Service Center\n" +
        //"Road, North Campus", false);
        for(Triple<String,Integer,Integer> t: names)
            System.err.println(t);
        for(Triple<String,Integer,Integer> t: names)
            if(!content.substring(t.getSecond(), t.getThird()).equals(t.getFirst()))
                System.err.println("Offset improper for: "+t+", expected: "+content.substring(t.getSecond(),t.getThird()));

        String uc = "";
        int prev_end = 0;
        for(Triple<String,Integer,Integer> t: names) {
            uc += content.substring(prev_end, t.getSecond());
            prev_end = t.getThird();
            uc += "<u>"+t.getFirst()+"</u>";
            System.err.println("I: "+prev_end+", "+uc);
        }
        uc += content.substring(prev_end,content.length());
        System.err.println(uc);
    }
}
