package edu.stanford.muse.ner.dictionary;

import edu.stanford.muse.util.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class EnglishDictionary {
    static String adverbsFile = "dictionaries/en-pure-adv.txt";
    static String adjFile = "dictionaries/en-pure-adj.txt";
    static String verbsFile = "dictionaries/en-pure-verbs.txt";
    static String prepFile = "dictionaries/en-prepositions.txt";
    static String pronounFile = "dictionaries/en-pronouns.txt";
    static String fullDictFile = "dict.words.full.safe";
    static String dictStatsFile = "stats.txt";

    static Log log = LogFactory.getLog(EnglishDictionary.class);

    static Set<String> adverbs, adjectives, verbs, prepositions, pronouns, dictionary;
    //word -> <#capitalised,#total>
    static Map<String,Pair<Integer,Integer>> dictStats;

    /**
     * @return dictionary entry -> #times appeared in capitalised form, total number of occurrences */
    public static Map<String,Pair<Integer,Integer>> getDictStats(){
        if(dictStats==null){
            dictStats = new LinkedHashMap<>();
            try{
                BufferedReader br = new BufferedReader(new InputStreamReader(EnglishDictionary.class.getClassLoader().getResourceAsStream(dictStatsFile)));
                String line;
                while((line=br.readLine())!=null){
                    if(line.startsWith("#"))
                        continue;
                    String[] fields = line.split("\\s");
                    if(fields == null || fields.length<3)
                        continue;
                    int cnum = Integer.parseInt(fields[1]);
                    int num = Integer.parseInt(fields[2]);
                    dictStats.put(fields[0], new Pair<>(cnum, num));
                }
            }catch(Exception e){
                log.warn("Cannot read file: "+dictStatsFile);
                e.printStackTrace();
            }
            return dictStats;
        }
        return dictStats;
    }

    public static Set<String> getDict(){
        if(dictionary==null){
            dictionary = readFile(fullDictFile);
            log.info("Read: "+dictionary.size()+" entries from "+fullDictFile);
            return dictionary;
        }
        return dictionary;
    }

    public static Set<String> getTopPronouns(){
        if(pronouns==null) {
            pronouns = readFile(pronounFile);
            log.info("Read "+pronouns.size()+" entries from "+pronounFile);
            return pronouns;
        }
        return pronouns;
    }

    public static Set<String> getTopAdverbs(){
        if(adverbs==null) {
            adverbs = readFile(adverbsFile);
            log.info("Read "+adverbs.size()+" entries from "+adverbsFile);
            return adverbs;
        }
        return adverbs;
    }

    public static Set<String> getTopAdjectives(){
        if(adjectives == null){
            adjectives = readFile(adjFile);
            log.info("Read "+adjectives.size()+" entries from "+adjFile);
            return adjectives;
        }
        return adjectives;
    }

    public static Set<String> getTopVerbs(){
        if(verbs == null){
            verbs = readFile(verbsFile);
            log.info("Read "+verbs.size()+" entries from "+verbsFile);
            return verbs;
        }
        return verbs;
    }

    public static Set<String> getTopPrepositions(){
        if(prepositions == null){
            prepositions = readFile(prepFile);
            log.info("Read "+prepositions.size()+" entries from "+prepFile);
            return prepositions;
        }
        return prepositions;
    }

    public static Set<String> readFile(String fileName){
        Set<String> entries = new LinkedHashSet<>();
        try{
            //new FileReader("/Users/vihari/repos/epadd-git/muse/WebContent/WEB-INF/classes/dictionaries/en-pronouns.txt"));
            BufferedReader br = new BufferedReader(new InputStreamReader(EnglishDictionary.class.getClassLoader().getResourceAsStream(fileName)));
            String line;
            while((line=br.readLine())!=null){
                if(line.startsWith("#"))
                    continue;
                entries.add(line.trim().toLowerCase());
            }
        }catch(IOException e){
            log.warn("Cannot read file: "+fileName);
            e.printStackTrace();
        }
        return entries;
    }

    public static void main(String[] args){
        System.err.println(getDictStats().get("john"));
    }
}
