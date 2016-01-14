package edu.stanford.muse.ie;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by vihari on 24/12/15.
 * A model to link proper nouns in an archive
 */
public class ProperNounLinker {
    static Log log = LogFactory.getLog(ProperNounLinker.class);

    /**
     * breaks the phrase into words, lowercases and stems each of the word
     * will break mixed capitals into individual words,
     * for example: VanGogh -> [van, gogh] and NYTimes -> [ny, time]*/
    static Set<String> bow(String phrase) {
        String[] tokens = phrase.split("\\s+");
        Set<String> bows = new LinkedHashSet<>();
        for (String tok : tokens) {
            //don't touch the period or other special chars suffixed
            tok = tok.replaceAll("^\\W+", "");
            if (EnglishDictionary.sws.contains(tok))
                continue;

            List<String> subToks = new ArrayList<>();
            String buff = "";
            for (int ti = 0; ti < tok.length(); ti++) {
                boolean cUc = Character.isUpperCase(tok.charAt(ti));
                boolean nUc = false, pUc = false;
                if (ti+1 < tok.length())
                    nUc = Character.isUpperCase(tok.charAt(ti + 1));
                if (ti-1 >= 0)
                    pUc = Character.isUpperCase(tok.charAt(ti - 1));
                //two cases for breaking a word further
                //1. an upper case surrounded by lower cases
                //2. an upper case character with lower case stuff to the right, like 'T' in NYTimes
                if (cUc && ti > 0 && ti < tok.length() && ((!pUc && !nUc) || (pUc && !nUc))) {
                    subToks.add(buff);
                    buff = ""+tok.charAt(ti);
                } else {
                    buff += tok.charAt(ti);
                }
            }
            subToks.add(buff);
            for (String st : subToks) {
                String ct = EnglishDictionary.getSingular(st.toLowerCase());
                bows.add(ct);
            }
        }
        return bows;
    }

    /**All the words in the phrase that follow the pattern of Capital word followed by lower-case letters
     * US Supreme Court -> [Supreme, Court]
     * NYTimes -> Times*/
    static Set<String> nonAcronymWords(String phrase){
        String[] tokens = phrase.split("\\s+");
        Set<String> naw = new LinkedHashSet<>();
        Pattern p = Pattern.compile("[A-Z][a-z]+");
        for(String tok: tokens) {
            Matcher m = p.matcher(tok);
            while(m.find()) {
                //There can be more than one sequence of upper-case letter followed by lower-case
                //e.g. DaVinci
                naw.add(m.group());
            }
        }
        return naw;
    }

    static String stripTitles(String str){
        String[] personTitles = new String[]{"Dear", "Hi", "Hello", "Mr", "Mr.", "Mrs", "Mrs.", "Miss", "Sir", "Madam", "Dr.", "Prof", "Dr", "Prof.", "Dearest", "Governor", "Gov.", "Col.", "CEO", "Cpl.", "Gen.", "St.",
                //also including articles
                "The", "A", "An"
        };
        for(String pt: personTitles)
            if(str.startsWith(pt+" "))
                return str.substring(pt.length()+1);
        return str;
    }

    static Map<Character, Integer> charHist(String str){
        Map<Character, Integer> map = new LinkedHashMap<>();
        for(Character c: str.toCharArray()) {
            if(!Character.isLetter(c))
                continue;
            if (!map.containsKey(c))
                map.put(c, 0);
            map.put(c, map.get(c)+1);
        }
        return map;
    }

    /**
     * Checks if c2 is an acronym of c1
     * can handle MoMA, Museum of Modern Arts
     * does not handle WaPo, Washington Post are acronyms like these common enough to bother?
     */
    static boolean isAcronymOf(String c1, String c2){
        if(c2.equals("WaPo") && (c1.equals("The Washington Post")||c1.equals("Washington Post")))
            return true;
        int uc = 0, lc = 0;
        //a single word cannot have an acronym and acronym cannot span multiple words
        if(!c1.contains(" ") || c2.contains(" "))
            return false;
        for(int ci=0;ci<c2.length();ci++) {
            if (Character.isUpperCase(c2.charAt(ci)))
                uc++;
            else
                lc++;
        }
        //there can be equal number of upper-case and lower-case, as in WaPo
        if(uc<lc)
            return false;


        if(Util.getAcronym(c1).equals(c2) || Util.getAcronym(c1, true).equals(c2))
            return true;
        return false;
    }

    static String flipComma(String str) {
        if (!str.contains(", "))
            return str;
        String fields[] = str.split(", ");
        //What are some of these cases?
        if (fields.length != 2) {
            log.warn("Flip comma cannot handle: " + str);
            return str;
        } else {
            return fields[1] + " " + fields[0];
        }
    }

    /**
     * Strip all known titles
     * return false if one of the phrases is empty at this stage
     * [want to be able to quickly say something is bad] check if the character (excluding periods, apostrophe, hyphen) histogram of one is subset of other (there are 11 different people names and short names in which case this is not true)
     * if one is acronym of other
     * if one of the phrases has ', ' flip the order of words before and after the char and remove ','
     * if both of them have the same acronym, check if they also share non-acronym word
     * if one of the phrases has all the words in the other and if the smaller phrase is not a dictionary word.
     *         Canonicalize words being compared: stemmed, lower-cased, expanded if found in the abbreviation dictionary
     *         Scan the canonicalized words in both the phrases and see if the other set contains two contiguous words in one set, if so merge the words into one
     * */
    static boolean isValidCandidate(String c1, String c2) {
        if (c1 == null && c2 == null)
            return true;
        if (c1 == null || c2 == null)
            return false;
        c1 = stripTitles(c1);
        c2 = stripTitles(c2);

        if(c1.length()==1 || c2.length()==1)
            return false;

        if (c1.equals(c2))
            return true;

        Map<Character, Integer> charHist1 = charHist(c1);
        Map<Character, Integer> charHist2 = charHist(c2);
        if(charHist2.size()==charHist1.size() && Sets.intersection(charHist1.keySet(),charHist2.keySet()).size()==charHist1.size()) {
            Map<Character,Integer> sch = c1.length()<c2.length()?charHist1:charHist2;
            Map<Character,Integer> lch = c1.length()>c2.length()?charHist1:charHist2;
            for (Character ch: sch.keySet())
                if(lch.get(ch)<sch.get(ch))
                    return false;
        }

        //there is no point moving forward if this is the case
        if(FeatureDictionary.sws.contains(c1.toLowerCase()) || FeatureDictionary.sws.contains(c2.toLowerCase()))
            return false;

        c1 = flipComma(c1);
        c2 = flipComma(c2);

        //acronym check
        if (isAcronymOf(c2, c1) || isAcronymOf(c1, c2))
            return true;

        Set<String> bow1, bow2;
        //They have same acronyms and share non-acronym words
        //handles [US Supreme Court, United States Supreme Court], [NY Times, NYTimes, New York Times]
        if(Util.getAcronym(c1).equals(Util.getAcronym(c2))) {
            bow1 = nonAcronymWords(c1);
            bow2 = nonAcronymWords(c2);
            int minS = bow1.size() < bow2.size() ? bow1.size() : bow2.size();
            if (minS > 0 && Sets.intersection(bow1, bow2).size() == minS)
                return true;
        }

        //same bag of words
        //covers order, stemming variants and also missing words
        bow1 = bow(c1);
        bow2 = bow(c2);
        int minS = bow1.size() < bow2.size() ? bow1.size() : bow2.size();
        Set<String> sbow, lbow;
        if(minS == bow1.size()) {
            sbow = bow1;
            lbow = bow2;
        }
        else {
            sbow = bow2;
            lbow = bow1;
        }
        int numMatches = 0;
        Multimap abb = EnglishDictionary.getAbbreviations();
        for(String bw1: sbow) {
            for (String bw2 : lbow) {
                String lbw1, lbw2;
                if(bw1.length()<bw2.length()) {
                    lbw1 = bw1.toLowerCase();
                    lbw2 = bw2.toLowerCase();
                }else{
                    lbw1 = bw2.toLowerCase();
                    lbw2 = bw1.toLowerCase();
                }
                if (bw1.equals(bw2)
                        || (lbw1.length()>1 && lbw1.charAt(lbw1.length()-1)=='.' && lbw2.startsWith(lbw1.substring(0,lbw1.length()-1)))
                        || abb.containsEntry(lbw1, lbw2) || abb.containsEntry(lbw1+".", lbw2)) {
                    numMatches++;
                    break;
                }
            }
        }
        if(numMatches == minS) {
            if(minS > 1)
                return true;
            //make sure the deciding term is not a dictionary word
            else {
                String str = sbow.iterator().next().toLowerCase();
                str = str.replaceAll("^\\W+|\\W+$","");
                Pair<Integer,Integer> p = EnglishDictionary.getDictStats().get(str);
                if(p==null || ((double)p.getFirst()/p.getSecond()>0.3))
                    return true;
            }
        }

        return false;
    }

    public static class Clusters{
        public Hierarchy hierarchy;
        //index of the (field name + values) to the docId
        Map<String,Set<String>> index;
        //index of docId to cluster Idxes in List of clusters
        Map<String,Set<Integer>> clusterIdx;
        List<Cluster> clusters;
        public static class Cluster{
            //list of docIds in which the docIds are mentioned
            Set<String> docIds, mentions;
            boolean namedEntity = false;
            boolean removed = false;
            Cluster(Cluster c){
                this.mentions = c.mentions;
                this.docIds = c.docIds;
                this.namedEntity = c.namedEntity;
            }
            Cluster(){
                docIds = new LinkedHashSet<>();
                mentions = new LinkedHashSet<>();
            }
            public void addMention(String phrase, EmailDocument ed){
                mentions.add(phrase);
                docIds.add(ed.getUniqueId());
            }

            public void merge(Cluster cluster){
                if(cluster == null || cluster.mentions==null || cluster.docIds==null) {
                    log.error("Improper cluster for merging!!");
                    return;
                }
                this.mentions.addAll(cluster.mentions);
                this.docIds.addAll(cluster.docIds);
                this.namedEntity = this.namedEntity||cluster.namedEntity;
            }

            public boolean isValidMerge(Cluster c){
                for(String m: mentions)
                    for(String m1: c.mentions)
                        if(!isValidCandidate(m,m1))
                            return false;
                return true;
            }

            @Override
            public int hashCode(){
                return mentions.hashCode();
            }

            @Override
            public String toString(){
                return mentions.toString()+(removed?"[Removed]":"");
            }

            @Override
            public boolean equals(Object o){
                if(o==null||!(o instanceof Cluster))
                    return false;
                Cluster c = (Cluster) o;
                if(this.mentions.size()!=c.mentions.size())
                    return false;
                for(String str: c.mentions)
                    if(!mentions.contains(str)) {
                        //System.err.println("Returning false due to different mention sets: "+c.mentions+", "+mentions);
                        return false;
                    }
                //System.err.println(c.mentions+", "+mentions+" are equal");
                return true;
            }
        }

        public Clusters(Hierarchy hierarchy){
            this.hierarchy = hierarchy;
            index = new LinkedHashMap<>();
            clusterIdx = new LinkedHashMap<>();
            clusters = new ArrayList<>();
        }

        public void addMention(String mention, EmailDocument ed) {
            addMention(mention, ed, false);
        }

        public void addMention(String mention, EmailDocument ed, boolean namedEntity) {
            if (!index.values().contains(ed.getUniqueId())) {
                for (int f = 0; f < hierarchy.getNumLevels(); f++) {
                    String field = hierarchy.getName(f);
                    String val = hierarchy.getValue(f, ed);
                    String key = field + ":" + val;
                    if (!index.containsKey(key))
                        index.put(key, new LinkedHashSet<String>());
                    index.get(key).add(ed.getUniqueId());
                }
            }
            Cluster cluster = new Cluster();
            cluster.namedEntity = namedEntity;
            cluster.addMention(mention, ed);
            int cid = clusters.indexOf(cluster);
            if (cid <= 0)
                clusters.add(cluster);
            else
                clusters.get(cid).merge(cluster);

            if (!clusterIdx.containsKey(ed.getUniqueId()))
                clusterIdx.put(ed.getUniqueId(), new LinkedHashSet<Integer>());
            clusterIdx.get(ed.getUniqueId()).add(clusters.size() - 1);
        }

        /**Merge the cluster cluster2 into cluster1*/
        public void mergeClusters(Cluster cluster1, Cluster cluster2){
            if(cluster1 == null || cluster2 == null) {
                log.error("Cannot merge (into) null cluster");
                return;
            }
            if(clusters.indexOf(cluster1)==-1 || clusters.indexOf(cluster2)==-1) {
                log.error("Unknown cluster in merge operation!! "+cluster1+"("+clusters.indexOf(cluster1)+"), "+cluster2+"("+clusters.indexOf(cluster2)+")");
                return;
            }
            if(cluster1.removed || cluster2.removed){
                log.error("One of the clusters is already removed!!"+cluster1+", "+cluster2);
                return;
            }
            int out = clusters.indexOf(cluster2);
            int into = clusters.indexOf(cluster1);
            //System.err.println("Removing: "+cluster2);
            cluster1.merge(cluster2);
            cluster2.removed = true;
            //clusters.remove(cluster2);
            clusters.set(out, cluster2);
            clusters.set(into, cluster1);
            //update the index
            for(String docId: cluster2.docIds) {
                clusterIdx.get(docId).add(into);
                clusterIdx.get(docId).remove(out);
            }
        }

        public void buildSingletonClusters(Archive archive, List<EmailDocument> docs){
            CICTokenizer tokenizer = new CICTokenizer();
            int di = 0;
            for(EmailDocument doc: docs){
                String[] types = new String[]{"en_person","en_org","en_loc"};
                List<String> entities = new ArrayList<>();
                for(String type: types) {
                    List<String> tes = archive.getEntitiesInDoc(doc, type);
                    entities.addAll(tes);
                }
                String content = archive.getContents(doc, true);
                List<Triple<String,Integer,Integer>> tokens = tokenizer.tokenize(content, false);
                for(Triple<String,Integer,Integer> tok: tokens) {
                    String t = tok.getFirst();
                    t = t.replaceAll("^\\W+|\\W+$","");
                    if(EnglishDictionary.sws.contains(t.toLowerCase()))
                        continue;
                    if(t.length()>50)
                        continue;
                    addMention(t, doc);
                }
                for(String e: entities) {
                    e = e.replaceAll("^\\W+|\\W+$","");
                    addMention(e, doc, true);
                }
                if(di++%100 == 0)
                    log.info("Accumulated " + clusters.size() + " clusters from " + di + " docs");
            }
            log.info("Collected #" + clusters.size() + " singleton clusters");
        }

        public void merge(Archive archive){
            List<Cluster> oldClusters = new ArrayList<>(clusters.size());
            int numNamedEntity = 0;
            for(Cluster c: clusters) {
                oldClusters.add(new Cluster(c));
                if(c.namedEntity)
                    numNamedEntity++;
            }
            log.info("Found: #"+numNamedEntity+" named entities");
            int numProcessed = 0;
            for(int cid = 0;cid < oldClusters.size(); cid++){
                Cluster c = oldClusters.get(cid);
                if(c.removed || !c.namedEntity)
                    continue;
                numProcessed++;
                //System.err.println("Trying: "+c);
                boolean found = false;
                for(int f=0;f<hierarchy.getNumLevels();f++) {
                    String field = hierarchy.getName(f);
                    List<String> docIds = new ArrayList<>();
                    //get docs in proximity wrt this level of hierarchy
                    for(String docId: c.docIds) {
                        EmailDocument ed = archive.docForId(docId);
                        String val = hierarchy.getValue(f, ed);
                        String key = field + ":" + val;
                        Set<String> ldocIds = index.get(key);
                        if (ldocIds != null)
                            docIds.addAll(ldocIds);
                    }

                    //now get all the candidate clusters
                    Set<Integer> cands = new LinkedHashSet<>();
                    for(String did: docIds)
                        if(clusterIdx.get(did)!=null)
                            cands.addAll(clusterIdx.get(did));

                    //System.err.println(c+", "+cands.size()+" cands in level "+f);
                    int ccid = clusters.indexOf(c);
                    for(int cid2: cands) {
//                        //else double update of clusters may happen
//                        if (cid2 >= cid)
//                            continue;
                        if(cid2 == ccid)
                            continue;
                        if(cid2<0) {
                            log.error("What??!! the cluster index is less than 0");
                            continue;
                        }
                        Cluster c1 = clusters.get(cid2);
                        if(c1.removed)
                            continue;
                        if(c1.isValidMerge(c)){
                            found = true;
                            System.err.println("("+c1+", "+c+"), level:"+f);
                            //mergeClusters(c1, c);
                            break;
                        }
                    }
                    if(found)
                        break;
                }
                if(cid%10==0) {
                    int curr = 0;
                    for(Cluster cluster: clusters)
                        if(!cluster.removed)
                            curr++;
                    log.info("Iterated over: " + numProcessed + "/" + numNamedEntity +" clusters. No. of clusters after merging is: " + curr + "/" + oldClusters.size());
                }
            }
        }
    }

    /**
     * Collects and merges mention clusters
     * @arg docs - List of documents
     * @arg hierarchy - list of field names in the order of priority*/
    public static Clusters getMentionClusters(Archive archive, final List<EmailDocument> docs, Hierarchy hierarchy) {
        Clusters clusters = new Clusters(hierarchy);
        clusters.buildSingletonClusters(archive, docs);
        clusters.merge(archive);
        return clusters;
    }

    public static void test() {
        BOWtest();
        Map<Pair<String,String>,Boolean> tps = new LinkedHashMap<>();
        tps.put(new Pair<>("NYTimes", "NY Times"),true);
        tps.put(new Pair<>("NY Times", "New York Times"), true);
        tps.put(new Pair<>("The New York Times", "New York Times"), true);
        tps.put(new Pair<>("US Supreme Court", "United States Supreme Court"), true);
        tps.put(new Pair<>("New York Times", "NYT"), true);
        tps.put(new Pair<>("Global Travel Agency", "Global Transport Agency"), false);
        tps.put(new Pair<>("Transportation Authorities", "Transportation Authority"), true);
        tps.put(new Pair<>("MoMA", "Museum of Modern Arts"), true);
        //this is too much to expect
        //tps.put(new Pair<>("MoMA", "MMA"), true);
        tps.put(new Pair<>("Dr. Sherlock", "Sherlock"), true);
        tps.put(new Pair<>("Apple", "Apple Inc."), true);
        tps.put(new Pair<>("Inc.", "Apple Inc."), false);
        tps.put(new Pair<>("Washington Post", "The Washington Post"), true);
        tps.put(new Pair<>("The Washington Post", "WaPo"), true);
        tps.put(new Pair<>("New Jersey", "New Journey"), false);
        tps.put(new Pair<>("San Francisco", "SF"), true);
        tps.put(new Pair<>("The National Aeronautics and Space Administration", "NASA"), true);
        tps.put(new Pair<>("David Bowie", "Bowie, David"), true);
        tps.put(new Pair<>("David Bowie", "D. Bowie"), true);
        tps.put(new Pair<>("David Bowie", "Mr. David"), true);
        tps.put(new Pair<>("David Bowie", "Mr. Bowie"), true);
        tps.put(new Pair<>("David Bowie", "David"), true);
        tps.put(new Pair<>("David Bowie", "Bowie"), true);
        //possible
        tps.put(new Pair<>("David Bowie", "DB"), true);
        tps.put(new Pair<>("Bowie, David", "DB"), true);
        tps.put(new Pair<>("Bowie, David", "BD"), false);
        tps.put(new Pair<>("Mr.", "Mr. David"), false);
        tps.put(new Pair<>("Mr D", "Mr. David"), true);
        tps.put(new Pair<>("MrD", "Mr. David"), false);
        tps.put(new Pair<>("David Bowie", "David                            \n-:?Bowie"), true);
        tps.put(new Pair<>("Apple Inc.", "Apple Inc"), true);
        tps.put(new Pair<>("Dr. Sherlock", "Dr."), false);
        tps.put(new Pair<>("University of Chicago", "Chicago"), false);
        tps.put(new Pair<>("University of Chicago", "University"), false);
        tps.put(new Pair<>("University of Chicago", "of"), false);
        tps.put(new Pair<>("University of Chicago", "UC"), true);
        tps.put(new Pair<>("University of Chicago", "Chicago Univ"), true);
        tps.put(new Pair<>("University of Chicago", "U. Chicago"), true);
        tps.put(new Pair<>("Pt. Hariprasad", "Hariprasad"), true);
        tps.put(new Pair<>("Mt. Everest", "Everest"), true);
        //because such acronyms are unlikely
        tps.put(new Pair<>("Mt. Everest", "ME"), false);
        tps.put(new Pair<>("Mt. Everest", "Mount Everest"), true);
        tps.put(new Pair<>("The Dept. of Chemistry", "The Chemistry"), false);
        tps.put(new Pair<>("Mr. Spectator", "Spectator Sport"), false);
        tps.put(new Pair<>("Robert William Creeley", "Robert Creeley"), true);
        tps.put(new Pair<>("Mahishi Road", "Mahishi"), true);
        tps.put(new Pair<>("Mahishi Road", "Mahishi Rd."), true);
        tps.put(new Pair<>("Mahishi Road", "Road"), false);
        //because roads are not generally abbreviated like this, I know its hard; would be nice to capture this
        tps.put(new Pair<>("Mahishi Road", "MR"), false);
        //This is not technically wrong, the both phrases still point to the same name
        tps.put(new Pair<>("For Robert Creeley", "Robert Creeley"), true);
        tps.put(new Pair<>("Richmond Avenue", "Richmond Ave."), true);
        tps.put(new Pair<>("Department of Chemistry", "Chemistry Dept."), true);
        tps.put(new Pair<>("New York City", "New York"), true);
        tps.put(new Pair<>("New York State", "New York"), true);
        tps.put(new Pair<>("Harvard Square", "Harvard"), true);
        //this is probably OK
        tps.put(new Pair<>("Harvard Square", "Square"), true);
        tps.put(new Pair<>("Harvard School of Business", "Harvard"), true);
        //specific enough
        tps.put(new Pair<>("Harvard School of Business", "Business School"), true);
        //can we handle these??
        tps.put(new Pair<>("Thomas Burton", "Tim"), true);
        tps.put(new Pair<>("Robert", "Bob Creeley"), true);
        tps.put(new Pair<>("Leonardo di ser Piero da Vinci","Leonardo da Vinci"), true);
        tps.put(new Pair<>("Leonardo da Vinci","Leonardo DaVinci"), true);
        tps.put(new Pair<>("Leonardo da Vinci","Leonardo Da Vinci"), true);
        tps.put(new Pair<>("Leonardo da Vinci","Leonardoda Vinci"), false);
        tps.put(new Pair<>("Vincent van Gogh","Van Gogh"), true);
        tps.put(new Pair<>("Vincent VanGogh", "Vincent van Gogh"), true);
        //van is "The" for person names
        tps.put(new Pair<>("Vincent van Gogh", "van"), false);
        tps.put(new Pair<>("Vincent VanGogh", "Vincent"), true);
        tps.put(new Pair<>("Vincent VanGogh", "Gogh"), true);
        //what about these? Thank you India!!
        tps.put(new Pair<>("Chandra Babu", "Chandrababu"), true);
        tps.put(new Pair<>("Yograj", "Yog Raj"), true);
        tps.put(new Pair<>("Lakshmi", "Laxmi"), true);

        int numFailed = 0;
        long st = System.currentTimeMillis();
        for(Map.Entry e: tps.entrySet()) {
            boolean expected = (boolean)e.getValue();
            String cand1 = ((Pair<String,String>)e.getKey()).first;
            String cand2 = ((Pair<String,String>)e.getKey()).second;
            if (isValidCandidate(cand1, cand2) != expected) {
                System.err.println(cand1 + " - " + cand2 + ", expected: " + expected);
                numFailed++;
            }
        }
        System.err.println("All tests done in: "+(System.currentTimeMillis()-st)+"ms\nFailed ["+numFailed+"/"+tps.size()+"]");
    }

    public static void BOWtest(){
        String[] phrases = new String[]{"NYTimes","DaVinci","Vincent VanGogh"};
        String[][] expected = new String[][]{
                new String[]{"ny","time"},
                new String[]{"da","vinci"},
                new String[]{"vincent","van","gogh"}
        };
        for(int pi=0;pi<phrases.length;pi++) {
            String p = phrases[pi];
            Set<String> res = bow(p);
            List<String> exp = Arrays.asList(expected[pi]);
            boolean missing = false;
            for (String cic : res)
                if (!exp.contains(cic)) {
                    missing = true;
                    break;
                }
            if (res.size() != exp.size() || missing) {
                String str = "------------\n" +
                        "Test failed!\n" +
                        "Phrase: " + p + "\n" +
                        "Expected tokens: " + exp + "\n" +
                        "Found: " + res + "\n";
                System.err.println(str);
            }
        }
        System.out.println("All tests done!");
    }

    public static void main(String[] args){
//        try{
//            String userDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user";
//            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
//            archive.assignThreadIds();
//            Hierarchy hierarchy = new EmailHierarchy();
//            Clusters clusters = getMentionClusters(archive, (List)archive.getAllDocs(), hierarchy);
//            for(Clusters.Cluster cluster: clusters.clusters) {
//                if(cluster.mentions.size()<=1 || cluster.removed)
//                    continue;
//                System.err.println("-------------");
//                System.err.println(cluster);
//            }
//            archive.close();
////            EmailDocument ed = (EmailDocument)archive.getAllDocs().get(2);
////            System.err.println(archive.getDoc(ed));
//        }catch(Exception e){
//            e.printStackTrace();
//        }
        test();
    }
}
