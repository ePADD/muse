package edu.stanford.muse.ie;

import com.google.common.collect.Sets;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.webapp.SimpleSessions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tartarus.snowball.ext.PorterStemmer;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by vihari on 24/12/15.
 * A model to link proper nouns in an archive
 */
public class ProperNounLinker {
    static Log log = LogFactory.getLog(ProperNounLinker.class);

    static Set<String> bow(String phrase) {
        String[] tokens = phrase.split("\\s+");
        Set<String> bows = new LinkedHashSet<>();
        for (String tok : tokens) {
            tok = tok.replaceAll("^\\W+|\\W+$", "");
            if(EnglishDictionary.sws.contains(tok))
                continue;
            String ct = EnglishDictionary.getSingular(tok.toLowerCase());
            bows.add(ct);
        }
        return bows;
    }

    static Set<String> nonAcronymWords(String phrase){
        String[] tokens = phrase.split("\\s+");
        Set<String> naw = new LinkedHashSet<>();
        Pattern p = Pattern.compile("[A-Z][a-z]+");
        for(String tok: tokens) {
            Matcher m = p.matcher(tok);
            while(m.find()) {
                //there should be only one such if any
                naw.add(m.group());
                break;
            }
        }
        return naw;
    }

    static boolean isValidCandidate(String c1, String c2) {
        if (c1 == null && c2 == null)
            return true;
        if (c1 == null || c2 == null)
            return false;
        if (c1.equals(c2))
            return true;

        //acronym check
        String acr1 = Util.getAcronym(c1), acr2 = Util.getAcronym(c2);
        if (acr1.equals(c2) || acr2.equals(c1))
            return true;

        //same bag of words
        //covers all the order, stemming variants and also missing words
        Set<String> bow1, bow2;
        bow1 = bow(c1);
        bow2 = bow(c2);
        int minS = bow1.size()<bow2.size()?bow1.size():bow2.size();
        if(Sets.intersection(bow1,bow2).size() == minS)
            return  true;

        //handles [US Supreme Court, United States Supreme Court], [NY Times, NYTimes, New York Times]
        if(acr1.equals(acr2)) {
            bow1 = nonAcronymWords(c1);
            bow2 = nonAcronymWords(c2);
            minS = bow1.size()<bow2.size()?bow1.size():bow2.size();
            if(Sets.intersection(bow1,bow2).size() == minS)
                return true;
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
                    log.info("Accumulated "+clusters.size()+" clusters from "+di+" docs");
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

    public static void main1(String[] args) {
        System.err.println(bow("New York Times"));
        System.err.println(nonAcronymWords("NYTimes"));

        List<Pair<String,String>> tps = new ArrayList<>();
        tps.add(new Pair<>("NYTimes", "NY Times"));
        tps.add(new Pair<>("NY Times", "New York Times"));
        tps.add(new Pair<>("US Supreme Court", "United States Supreme Court"));
        tps.add(new Pair<>("New York Times","NYT"));
        tps.add(new Pair<>("Global Travel Agency", "Global Transport Agency"));
        tps.add(new Pair<>("Transportation Authorities","Transportation Authority"));
        for(Pair<String,String> p: tps)
            System.err.println("["+p.getFirst()+" - "+p.getSecond()+"] are "+(isValidCandidate(p.getFirst(),p.getSecond())?"":"not ")+"valid candidates");
    }

    public static void main(String[] args){
        try{
            String userDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            archive.assignThreadIds();
            Hierarchy hierarchy = new EmailHierarchy();
            Clusters clusters = getMentionClusters(archive, (List)archive.getAllDocs(), hierarchy);
            for(Clusters.Cluster cluster: clusters.clusters) {
                if(cluster.mentions.size()<=1 || cluster.removed)
                    continue;
                System.err.println("-------------");
                System.err.println(cluster);
            }
            archive.close();
//            EmailDocument ed = (EmailDocument)archive.getAllDocs().get(2);
//            System.err.println(archive.getDoc(ed));
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
