package edu.stanford.muse.ner.tokenizer;

import com.google.gson.Gson;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.util.NLPUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import opennlp.tools.util.Span;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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

    static class WorkRecord{
        Map<String, String> fields;
        Set<String> tags;
        public WorkRecord(Map<String,String> fields, Set<String> tags){
            this.tags = tags;
            this.fields = fields;
        }
    }

    public static void main(String[] args) {
        try {
            String dataPath = System.getProperty("user.home")+File.separator+"repos/hack4hd/data";
            BufferedReader br = new BufferedReader(new FileReader(new File(dataPath+File.separator+"all_work_records.csv")));
            String line;
            line = br.readLine();
            List<String> fields = new ArrayList<>();

            String[] hs = line.split(",");
            for(String h: hs)
                fields.add(h);
            Set<WorkRecord> data = new LinkedHashSet<>();
            POSTokenizer tokenizer = new POSTokenizer();
            int lno = 0;
            while((line=br.readLine())!=null){
                String nl = "";
                int li = 0;
                boolean iq = false;
                for(int j=0;j<line.length();j++){
                    if(line.charAt(j)=='"')
                        iq = !iq;
                    else if(line.charAt(j)==',' && iq) {
                        nl += line.substring(li, j);
                        li = j+1;
                    }
                }
                if(li<line.length())
                    nl += line.substring(li, line.length());
                //System.err.println("Cleaned: "+line+" to \n"+nl+"\n");
                line = nl.replaceAll("\"","");

                String[] vals = line.split(",");
                if(vals.length!=fields.size()) {
                    System.err.println(fields.size()+", "+ vals.length+" Skipping bad line: "+line);
                    continue;
                }
                Map<String,String> rec = new LinkedHashMap<>();
                for(int i=0;i<Math.min(vals.length,fields.size());i++){
                    if(!fields.get(i).equals("Description Kan"))
                        rec.put(fields.get(i), vals[i]);
                }
                String desc = rec.get("Description Eng");
                int wi = desc.indexOf("ward");
                int Wi = desc.indexOf("Ward");
                if(wi>0)
                    desc = desc.substring(0,wi-1);
                else if(Wi>0)
                    desc = desc.substring(0, Wi-1);
                List<Triple<String,Integer,Integer>> ttags=tokenizer.tokenize(desc);
                Set<String> tags = new LinkedHashSet<>();
                for(Triple<String,Integer,Integer> tt: ttags) {
                    String[] toks = tt.first.split("\\sin\\s|\\sat\\s");
                    for(String tok: toks)
                        if(tok.length()>3)
                            tags.add(tok);
                }
                data.add(new WorkRecord(rec, tags));
                lno++;
                if(lno%100 == 0)
                    System.err.println("Processed "+lno+" lines");
//                if(lno>1000)
//                    break;
            }

            System.err.println("Total lines read: "+lno);
            FileWriter fw = new FileWriter(new File(dataPath+File.separator+"all_work_records.json"));
            Gson gson = new Gson();
            gson.toJson(data, fw);
            fw.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
