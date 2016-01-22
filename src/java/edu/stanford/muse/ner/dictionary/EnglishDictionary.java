package edu.stanford.muse.ner.dictionary;

import com.google.common.collect.*;
import edu.stanford.muse.util.Pair;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;

public class EnglishDictionary {
    static String adverbsFile = "dictionaries/en-pure-adv.txt";
    static String adjFile = "dictionaries/en-pure-adj.txt";
    static String verbsFile = "dictionaries/en-pure-verbs.txt";
    static String prepFile = "dictionaries/en-prepositions.txt";
    static String pronounFile = "dictionaries/en-pronouns.txt";
    static String abbFile = "dictionaries/en-abbreviations.txt";
    static String fullDictFile = "dict.words.full.safe";
    static String dictStatsFile = "stats.txt";
    static String commonNamesFile = "firstNames.txt";

    static Log log = LogFactory.getLog(EnglishDictionary.class);

    static Set<String> adverbs, adjectives, verbs, prepositions, pronouns, dictionary, commonNames;
    static Multimap<String,String> abbDict;
    //word -> <#capitalised,#total>
    static Map<String,Pair<Integer,Integer>> dictStats;
    public static List<String> stopWords = Arrays.asList("but", "be", "with", "such", "then", "for", "no", "will", "not", "are", "and", "their", "if", "this", "on", "into", "a", "there", "in", "that", "they", "was", "it", "an", "the", "as", "at", "these", "to", "of" );
    public static List<String> personTitles = Arrays.asList("mr.", "mr", "ms.", "ms", "mrs.", "mrs", "dr.", "dr", "prof.", "prof");
    public static List<String> articles = Arrays.asList("the","a","an");

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

    //This has nothing to do with English Dictionary though.
    public static Set<String> getCommonNames(){
        if(commonNames == null){
            Set<String> entries = readFile(commonNamesFile);
            commonNames = new LinkedHashSet<>();
            for(String str: entries) {
                //may contain some unicode chars
                if(str.contains("%"))
                    continue;
                commonNames.add(str.toLowerCase().replaceAll("_"," "));
            }
            log.info("Read "+commonNames.size()+" entries from "+commonNamesFile);
            return commonNames;
        }
        return commonNames;
    }

    /**
     * Based on the rules from: https://docs.lucidworks.com/display/lweug/Lucid+Plural+Stemming+Rules
     * Also see: http://www.csse.monash.edu.au/~damian/papers/HTML/Plurals.html
     * */
    public static String getSingular(String word){
        word = word.toLowerCase();
        if(word.length()<4)
            return word;
        List<Character> vowels = Arrays.asList('a','e','i','o','u');


        String[] signals = new String[]{"elves","indices","theses","aderies","ies","hes"};
        String[] replace = new String[]{"elf","index","thesis","aderie","y","h"};
        for(int si=0;si<signals.length;si++)
            if(word.endsWith(signals[si]))
                return word.replaceAll(signals[si]+"$",replace[si]);

        if (word.endsWith("oes") && word.length()>=6)
            return word.replaceAll("oes$","o");
        else if (word.equals("goes")) return "go";
        else if (word.equals("does")) return "do";
        else if (word.endsWith("oes") && (word.length()==4 || word.length()==5))
            return word.replaceAll("oes$","oe");
        signals = new String[]{"sses","igases","gases","mases"};
        replace = new String[]{"ss","igase","gas","mas"};
        for(int si=0;si<signals.length;si++)
            if(word.endsWith(signals[si]))
                return word.replaceAll(signals[si]+"$",replace[si]);
        if(word.endsWith("vases")&&word.length()>=6)
            return word.replaceAll("vases$","vas");

        signals = new String[]{"iases","abuses","cuses","fuses"};
        replace = new String[]{"ias", "abuse","cuse","fuse"};
        for(int si=0;si<signals.length;si++)
            if(word.endsWith(signals[si]))
                return word.replaceAll(signals[si]+"$",replace[si]);
        if(word.endsWith("uses") && word.length()>=5 && !vowels.contains(word.charAt(word.length()-5)))
            return word.replaceAll("uses$","us");

        signals = new String[]{"xes","zes","es","ras","oci","cti"};
        replace = new String[]{"x","z","e","ra","ocus","ctus"};
        for(int si=0;si<signals.length;si++)
            if(word.endsWith(signals[si]))
                return word.replaceAll(signals[si]+"$",replace[si]);

        if(word.endsWith("s") && word.length()>1){
            char c = word.charAt(word.length()-2);
            if((!vowels.contains(c) && c!='s') || c=='o')
                return word.replaceAll("s$","");
        }
        List<Pair<String,String>> exactMatches = new ArrayList<>();
        exactMatches.add(new Pair<>("plusses","plus"));
        exactMatches.add(new Pair<>("gasses","gas"));
        exactMatches.add(new Pair<>("classes","class"));
        exactMatches.add(new Pair<>("mice", "mouse"));
        exactMatches.add(new Pair<>("data", "datum"));
        exactMatches.add(new Pair<>("bases", "basis"));
        exactMatches.add(new Pair<>("amebiases", "amebiasis"));
        exactMatches.add(new Pair<>("atlases", "atlas"));
        exactMatches.add(new Pair<>("Eliases", "Elias"));
        exactMatches.add(new Pair<>("feet", "foot"));
        exactMatches.add(new Pair<>("backhoes", "backhoe"));
        exactMatches.add(new Pair<>("calories", "calorie"));
        for(Pair<String,String> em: exactMatches)
            if(em.getFirst().equals(word))
                return em.getSecond();
        return word;
    }

    public static String getSimpleForm(String word){
        word = word.toLowerCase();
        if(word.endsWith("ied"))
            word = word.replaceAll("ied$","y");
        else if(word.endsWith("ed"))
            word = word.replaceAll("ed$","");
        else if(word.endsWith("ing"))
            word = word.replaceAll("ing$","");
        return word;
    }

    public static Multimap getAbbreviations(){
        if(abbDict!=null)
            return abbDict;
        abbDict = LinkedHashMultimap.create();
        try{
            BufferedReader br = new BufferedReader(new InputStreamReader(EnglishDictionary.class.getClassLoader().getResourceAsStream(abbFile)));
            String line;
            while((line=br.readLine())!=null){
                if(line.startsWith("#"))
                    continue;
                line = line.trim();
                String[] fields = line.split("\\s{2,}|\t");
                String abbr = "", expT = "";
                for(int i=0;i<fields.length;i++){
                    String str = fields[i];
                    //remove explanatory text, there can be multiple brackets in the same expansion, make sure not to remove span the regex between them
                    str = str.replaceAll("(^|\\s)\\(.+?\\)\\s?","");
                    if(i==0)
                        abbr = str;
                    else
                        expT = str;
                }
                if(abbr.contains(", "))
                    log.warn("Found an abbreviation entry with comma, line: "+line+"\nFile: "+abbFile);
                String[] exps = expT.split(", ");
                for(String exp: exps) {
                    if(!exp.contains("("))
                        abbDict.put(abbr.toLowerCase(), exp.toLowerCase());
                    else{
                        if(exp.contains(" (") || exp.indexOf(")")==-1){
                            log.warn("The entry: "+exp+" in Line: "+line+" not properly cleaned!\nFile: "+abbFile);
                            continue;
                        }
                        String alt = exp.substring(exp.indexOf('(')+1,exp.indexOf(')'));
                        String sf = exp.substring(0,exp.indexOf('('));
                        abbDict.put(abbr.toLowerCase(), sf.toLowerCase());
                        abbDict.put(abbr.toLowerCase(), (sf+alt).toLowerCase());
                        //log.info("Adding the alternate forms: "+ sf+" - "+sf+alt);
                    }
                }
            }
        }catch(IOException e){
            log.warn("Cannot read file: "+abbFile);
            e.printStackTrace();
        }
        return abbDict;
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

    public static void testPlurals(){
        List<Pair<String,String>> plurals = new ArrayList<>();
        plurals.add(new Pair<>("selves","self"));
        plurals.add(new Pair<>("indices", "index"));
        plurals.add(new Pair<>("hypotheses","hypothesis"));
        plurals.add(new Pair<>("camaraderies","camaraderie"));
        plurals.add(new Pair<>("potatoes","potato"));
        plurals.add(new Pair<>("dishes","dish"));
        plurals.add(new Pair<>("countries", "country"));
        plurals.add(new Pair<>("toes","toe"));
        plurals.add(new Pair<>("passes","pass"));
        plurals.add(new Pair<>("ligases","ligase"));
        plurals.add(new Pair<>("gases","gas"));
        plurals.add(new Pair<>("christamases","christamas"));
        plurals.add(new Pair<>("canvases","canvas"));
        plurals.add(new Pair<>("aliases", "alias"));
        plurals.add(new Pair<>("disabuses", "disabuse"));
        plurals.add(new Pair<>("accuses","accuse"));
        plurals.add(new Pair<>("diffuses","diffuse"));
        plurals.add(new Pair<>("buses","bus"));
        plurals.add(new Pair<>("buzzes","buzz"));
        plurals.add(new Pair<>("cats","cat"));
        plurals.add(new Pair<>("cacti","cactus"));
        plurals.add(new Pair<>("houses","house"));
        for(Pair<String,String> p: plurals)
            if(!getSingular(p.first).equals(p.second))
                System.err.println("Fails at: "+p.first);
        System.err.println("Done testing!");
    }

    public static void main(String[] args){
        //testPlurals();
        getAbbreviations();
    }
}
