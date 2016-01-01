package edu.stanford.muse.ie;

import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Created by vihari on 29/12/15.
 */
public class TokenAnalyzer {
    public static void main(String[] args){
        String file = System.getProperty("user.home")+ File.separator+"Downloads"+File.separator+"SurfaceForms_LRD-WAT.nofilter.tsv.gz";
        //token pair combinations
        Map<String,Integer> freqs = new LinkedHashMap<>();
        //Map<String,Integer> tokFreqs = new LinkedHashMap<>();
        try {
            LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), "UTF-8"));
            String line;
            while((line=lnr.readLine())!=null) {
                String[] tokens = line.split("\\t");
                if (tokens == null || tokens.length < 2)
                    continue;
                String tit = tokens[0], var = tokens[1];
                List<String> titToks = Arrays.asList(tit.toLowerCase().split("_"));
                List<String> varToks = Arrays.asList(var.toLowerCase().split(" "));
                List<String> ctoks1 = new ArrayList<>(), ctoks2 = new ArrayList<>();
                Map<String, Integer> flags = new LinkedHashMap<>();
                for (List<String> arr : new List[]{titToks, varToks})
                    for (String tok : arr) {
                        tok = tok.replaceAll("^\\W+|\\W+$","");
                        if (!flags.containsKey(tok))
                            flags.put(tok, 0);
                        flags.put(tok, flags.get(tok) + 1);
                    }
                int ai = 0;
                for (List<String> arr : new List[]{titToks, varToks}) {
                    for (String t : arr) {
                        String ct = t.replaceAll("^\\W+|\\W+$","");
                        if (flags.get(ct) != 1)
                            continue;
                        if (ct.length()<2 || t.indexOf('%') >= 0)
                            continue;
                        if (t.indexOf('(') >= 0)
                            break;

                        if(EnglishDictionary.sws.contains(ct))
                            continue;

                        if (ai == 0)
                            ctoks1.add(ct);
                        else
                            ctoks2.add(ct);
                    }
                    ai++;
                }

                if (ctoks1.size() > 0 && ctoks2.size() > 0) {
                    for (String ct1 : ctoks1)
                        for (String ct2 : ctoks2) {
                            String str;
                            if (ct1.length() < ct2.length())
                                str = ct1 + "::" + ct2;
                            else
                                str = ct2 + "::" + ct1;
                            if (!freqs.containsKey(str))
                                freqs.put(str, 0);
                            freqs.put(str, freqs.get(str) + 1);
//                            for(String ct: new String[]{ct1, ct2}) {
//                                if (!tokFreqs.containsKey(ct))
//                                    tokFreqs.put(ct, 0);
//                                tokFreqs.put(ct, tokFreqs.get(ct)+1);
//                            }
                        }
                }

                //System.err.println("Adding: "+ctoks1+", "+ctoks2+" from "+tokens[0]+", "+tokens[1]);
                if (lnr.getLineNumber() % 10000 == 0) {
                    System.err.println("Found "+freqs.size()+" unique pairs");
                    System.err.println("Read: " + lnr.getLineNumber() + " of around 20M lines");
                    //break;
                }
            }
//            Map<String,Double> scores = new LinkedHashMap<>();
//            for(Map.Entry e: freqs.entrySet()){
//                String str = (String)e.getKey();
//                int freq = (int)e.getValue();
//
//                String[] tokens = str.split("::");
//                if(tokens.length<2)
//                    continue;
//                Integer tf1 = tokFreqs.get(tokens[0]), tf2 = tokFreqs.get(tokens[1]);
//                if(tf1==null || tf2 == null)
//                    continue;
//
//                double s = ((double)freq+1)/(tf1+1);
//                if(s>0.01){
//                    //s *= Math.log(tf1+1);
//                    scores.put(str+"["+tokens[0]+"]", s);
//                }
//
//                s = ((double)freq+1)/(tf2+1);
//                if(s>0.01){
//                    //s *= Math.log(tf2+1);
//                    scores.put(str+"["+tokens[1]+"]", s);
//                }
//            }
            List<Pair<String,Integer>> sfreqs = edu.stanford.muse.util.Util.sortMapByValue(freqs);
            for(Pair<String,Integer> pair: sfreqs) {
                if(pair.getSecond()<100)
                    break;
                System.err.println(pair);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
