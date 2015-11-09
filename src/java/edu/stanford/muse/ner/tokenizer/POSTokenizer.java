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
    final static int MAX_SENT_LENGTH = 500;
    public List<Triple<String, Integer, Integer>> tokenize(String content){
        Span[] sents = NLPUtils.sentenceDetector.sentPosDetect(content);
        List<Triple<String, Integer, Integer>> ret = new ArrayList<>();
        for(Span span: sents) {
            String sent = span.getCoveredText(content).toString();
            if(sent==null || sent.length()>MAX_SENT_LENGTH)
                continue;
            List<Pair<String,Triple<String,Integer,Integer>>> posTags = NLPUtils.posTagWithOffsets(sent);
            List<String> allowedPOSTags = Arrays.asList("NNP", "NNS", "NN", "JJ", "IN", "POS");

            int startOffset = 0;
            int endOffset = 0;
            String str = "";
            for (int pi=0;pi<posTags.size();pi++) {
                Pair<String, Triple<String,Integer,Integer>> p = posTags.get(pi);
                String tag = p.second.first;
                String nxtTag = null;
                if(pi<posTags.size()-1)
                    nxtTag = posTags.get(pi+1).second.first;

                //POS for 's
                //should not end or start in improper tags
                //!!Think twice before making changes here, dont mess up the offsets!!
                boolean startCond = str.equals("") && (tag.equals("POS")||tag.equals("IN")||p.getFirst().equals("'")||p.getFirst().equals("Dear")||p.getFirst().equals("from"));
                boolean endCond = ((nxtTag==null||!allowedPOSTags.contains(nxtTag)) && (tag.equals("POS")||tag.equals("IN")||p.getFirst().equals("'")));
                if (allowedPOSTags.contains(tag) && !startCond && !endCond) {
                    str += p.getFirst()+" ";
                }
                else {
                    if(!str.equals("")) {
                        str = str.substring(0, str.length() - 1);
                        ret.add(new Triple<>(str, startOffset, endOffset));
                        str = "";
                    }
                    if(pi<posTags.size()-1)
                        startOffset = posTags.get(pi+1).second.getSecond();
                }
                endOffset = p.second.getThird();
            }
            if (!str.equals(""))
                str = str.substring(0, str.length() - 1);
                //sentence ending is the segment ending
                ret.add(new Triple<>(str, startOffset, endOffset));
        }
        return ret;
    }

    public Set<String> tokenizeWithoutOffsets(String content, Short type){
        List<Triple<String,Integer,Integer>> tokensWithOffsets = tokenize(content);
        Set<String> tokens = new LinkedHashSet<>();
        for(Triple<String,Integer,Integer> t: tokensWithOffsets)
            tokens.add(t.first);
        return tokens;
    }

    public static void main(String[] args){
        POSTokenizer tok = new POSTokenizer();
        String content = "Hello, I am Vihari, Piratla of IIT Mandi, I went to college in UCB, GH then joined NASA after a brief tenure at CERN";
        List<Triple<String,Integer,Integer>> names = tok.tokenize(content);
        //names = tok.tokenizeWithoutOffsets("January 30, 2002: University at Buffalo, Center for Tomorrow, Service Center\n" +
        //"Road, North Campus", false);
        for(Triple<String,Integer,Integer> t: names)
            System.err.println(t);
        for(Triple<String,Integer,Integer> t: names)
            if(!content.substring(t.getSecond(), t.getThird()).equals(t.getFirst()))
                System.err.println("Offset improper for: "+t+", expected: -"+content.substring(t.getSecond(),t.getThird())+"-");

    }
}
