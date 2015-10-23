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
            List<Pair<String, String>> posTags = NLPUtils.posTag(sent);
            List<String> allowedPOSTags = Arrays.asList("NNP", "NNS", "NN", "JJ", "IN", "POS");

            String str = "";
            for (int pi=0;pi<posTags.size();pi++) {
                Pair<String, String> p = posTags.get(pi);
                String tag = p.second;
                String nxtTag = null;
                if(pi<posTags.size()-1)
                    nxtTag = posTags.get(pi+1).second;

                //POS for 's
                //should not end or start in improper tags
                if (allowedPOSTags.contains(tag)) {
                    if(str.equals("") && (tag.equals("POS")||tag.equals("IN")||p.getFirst().equals("'")||p.getFirst().equals("Dear")||p.getFirst().equals("from")))
                        continue;
                    if((nxtTag==null||!allowedPOSTags.contains(nxtTag)) && (tag.equals("POS")||tag.equals("IN")||p.getFirst().equals("'")))
                        continue;
                    str += p.getFirst() + " ";
                }
                else {
                    if(!str.equals("")) {
                        str = str.substring(0, str.length() - 1);
                        ret.add(new Triple<>(str, -1, -1));
                        str = "";
                    }
                }
            }
            if (!str.equals(""))
                ret.add(new Triple<>(str, -1, -1));
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
        System.err.println(CICTokenizer.personNamePattern.pattern());
        POSTokenizer tok = new POSTokenizer();
        String content = "Hello, I am Vihari, Piratla of IIT Mandi, I went to college in UCB, GH then joined NASA after a brief tenure at CERN";
        List<Triple<String,Integer,Integer>> names = tok.tokenize(content);
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
