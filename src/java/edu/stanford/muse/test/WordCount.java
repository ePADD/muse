package edu.stanford.muse.test;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class WordCount {
    // the command line parameter is the directory where the archive is stored.
    // The default place where the appraisal archive is stored is <HOME>/epadd-appraisal/user

    /*
        If system.out is needed, use below block, and change method to "void main", and comment
        out the constructor and instance data.
    */
    public static List<Pair<String,Integer>> getFreqs (Archive archive) throws IOException {
        Collection<Document> emails = archive.getAllDocs();

        //load stop words into list
        Charset charset = Charset.forName("US-ASCII");
        Path path = Paths.get("/Users/AnshulMathur/epadd/target/classes/stop.words.full");
        List<String> stopList = new ArrayList<>();
        stopList.add("subject");
        stopList.add("sent");
        stopList.add("received");//Subject is included in the email corpus and must thus be excluded
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                stopList.add(line);
            }
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
        }

        //load corpus into map
        Map<String, Integer> map = new HashMap<>();
        for (Document email: emails) {
            String emailContent = archive.getContents(email, false); // this is the body of the email
            String[] words = (emailContent).split("\\s+");
            for (String word: words){
                String key = Util.stripPunctuation(word);
                if (!key.equals("") && !stopList.contains(key)) {
                    if (map.containsKey(key)) {
                        map.put(key, map.get(key)+1);
                    } else {
                        map.put(key, 1);
                    }
                }
            }
        }
        //sort the map into entries arranged
        List<Map.Entry<String,Integer>> sortedList = sortMap(map);

        //computes total word count
        Collection<Integer> valueSet= map.values();
        List<Integer> valueList = new ArrayList<>();
        valueList.addAll(valueSet);
        int total = 0;
        for (Integer elem: valueList){
            total += elem;
        }

        //Calculates the probability of a word occurring in the corpus and adds to probability list
        List<Pair<String,Float>> probList = new ArrayList<>();
        for (Map.Entry<String,Integer> elem: sortedList) {
            float val = elem.getValue();
            val = val/total;
            probList.add(new Pair<>(elem.getKey(), val));
        }

        //Loads a corpus of English words and their probability of occurring into a hashmap
        path = Paths.get("/Users/AnshulMathur/Documents/Python_Workspace/readable.txt");
        charset = Charset.forName("US-ASCII");
        Map<String, Pair<Integer, Float>> englishMap = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String line = null;
            while ((line = reader.readLine()) != null){
                String[] lineArr = line.split(":");
                englishMap.put(lineArr[0], new Pair<>(Integer.parseInt(lineArr[1]), Float.parseFloat(lineArr[2])));
            }
        } catch (IOException x) {
            System.err.format("IOException: %s%n. Nothing to worry about, just an end of file", x);
        }
        /*
            Compute a smoothed ratio of our corpus to the English language and take the log of the ratio
            to provide an easier reading. If the ratio is > 1 (the author uses the word more than most people
            use the word in normal English), the log returns a > 0 value.
        */
        List<Pair<String,Float>> liftList = new ArrayList<>();
        for (Pair<String,Float> elem: probList){
            if (englishMap.containsKey(elem.getFirst())) {
                float ratio = (elem.getSecond()+10)/((englishMap.get(elem.getFirst()).getSecond())+10);
                float log = (float)Math.log10(ratio);
                if (log > 0){
                    liftList.add(new Pair(elem.getFirst(), log));
                }
            }
            else{
                liftList.add(new Pair(elem.getFirst(), (float)0));
            }
        }
        liftList = sortPairList(liftList);
        List<Pair<String,Integer>> truncList = new ArrayList<>();
        for (Pair<String, Float> elem: liftList) {
            if (elem.getSecond() > 0) {
                truncList.add(new Pair<>(elem.getFirst(), map.get(elem.getFirst())));
            }
        }
        return truncList;
    }

    static List<Map.Entry<String, Integer>> sortMap(Map<String, Integer> map){
        Set<Map.Entry<String,Integer>> sortedSet = map.entrySet();
        List<Map.Entry<String,Integer>> sortedList = new ArrayList<>();
        sortedList.addAll(sortedSet);
        Collections.sort(sortedList, new Comparator<Map.Entry<String, Integer>>() {
            //returns greater, not lesser.
            @Override
            public int compare(Map.Entry<String, Integer> entry1, Map.Entry<String, Integer> entry2) {
                return -1*(entry1.getValue().compareTo(entry2.getValue()));
            }
        });
        return sortedList;
    }

    static List<Pair<String,Float>> sortPairList(List<Pair<String,Float>> list){
        Collections.sort(list, new Comparator<Pair<String, Float>>() {
            @Override
            public int compare(Pair<String, Float> pair1, Pair<String, Float> pair2) {
                return -1*(pair1.getSecond().compareTo(pair2.getSecond()));
            }
        });
        return list;
    }

}

