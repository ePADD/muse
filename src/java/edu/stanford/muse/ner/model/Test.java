package edu.stanford.muse.ner.model;

import edu.stanford.muse.ie.Util;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.webapp.SimpleSessions;
import java.util.*;

import java.io.File;

/**
 * Created by vihari on 22/12/15.
 */
public class Test {
    public static void main(String[] args){
        try {
            String userDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            Map<String, Set<String>> acrs = new LinkedHashMap<>();
            CICTokenizer tokenizer = new CICTokenizer();
            for(Document doc: archive.getAllDocs()){
                String content = archive.getContents(doc, true);
                List<Triple<String,Integer,Integer>> tokens = tokenizer.tokenize(content, false);
                for(Triple<String,Integer,Integer> tok: tokens){
                    String acr = Util.getAcronym(tok.first);
                    if(acr.length()<=3)
                        continue;
                    if(!acrs.containsKey(acr))
                        acrs.put(acr, new LinkedHashSet<String>());
                    acrs.get(acr).add(tok.getFirst());
                }
            }
            Map<String,Integer> acrFreqs = new LinkedHashMap<>();
            for(String acr: acrs.keySet())
                acrFreqs.put(acr, acrs.get(acr).size());

            List<Pair<String,Integer>> sAcrs = edu.stanford.muse.util.Util.sortMapByValue(acrFreqs);
            for(Pair<String,Integer> p: sAcrs){
                String acr = p.getFirst();
                System.err.println("ACR: " + acr);
                for(String str: acrs.get(acr))
                    System.err.println(str);
                System.err.println("-----");
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
