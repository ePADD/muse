package edu.stanford.muse.ie;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.DatedDocument;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.util.Pair;

import edu.stanford.muse.webapp.SimpleSessions;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by vihari on 24/12/15.
 * An Utility class to link proper nouns in an archive
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
            if (EnglishDictionary.stopWords.contains(tok))
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
                //three cases for breaking a word further
                //1. an upper case surrounded by lower cases, VanGogh
                //2. an upper case character with lower case stuff to the right, like 'T' in NYTimes
                //3. an upper case char preceded by '.' H.W.=>H. W.
                if (cUc && ti > 0 && ti < tok.length()-1 && ((!pUc && !nUc) || (pUc && !nUc) || tok.charAt(ti-1)=='.')) {
                    //don't consider single chars or essentially single chars like 'H.' as words
                    if(buff.length()>2 || (buff.length()==2 && buff.charAt(buff.length()-1)!='.'))
                        subToks.add(buff);
                    buff = ""+tok.charAt(ti);
                } else {
                    buff += tok.charAt(ti);
                }
            }
            if(buff.length()>2 || (buff.length()==2 && buff.charAt(buff.length()-1)!='.'))
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
        EnglishDictionary.articles.toArray(new String[EnglishDictionary.articles.size()]);
        List<String> titles = new ArrayList<>();
        titles.addAll(Arrays.asList("dear", "hi", "hello"));
        titles.addAll(EnglishDictionary.personTitles);
        titles.addAll(EnglishDictionary.articles);

        for(String t: titles)
            if(str.toLowerCase().startsWith(t+" "))
                return str.substring(t.length()+1);
        if(titles.contains(str.toLowerCase()))
            return "";
        return str;
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
     * The two candidates are initially stripped any known titles/articles
     * then returns true (valid merge)
     * if one is acronym of other
     * (flips the order of words, if one of the phrases has ', '; This step takes care of Creeley, Robert)
     * if both of them have the same acronym, check if they also share all the non-acronym word(s), NYTimes and NY Times
     * if one of the phrases has all the words in the other and if the smaller phrase is not a dictionary word. This step also handles matches between abbreviations and expansions like [A.,Andrew], [Sen., Senate]
     *         Along with the following two additional steps:
     *         Canonicalize words being compared: stemmed, lower-cased, expanded if found in the abbreviation dictionary
     *         (not implemented) Scan the canonicalized words in both the phrases and see if the other set contains two contiguous words in one set, if so merge the words into one. This will take care of cases like [Chandra Babu<->Chandrababu]
     *         Single word chars like middle names or abbreviations such as A. or H.W. in "George H.W. Bush" should not be considered as words
     * */
    static boolean isValidMerge(String c1, String c2) {
        if (c1 == null && c2 == null)
            return true;
        if (c1 == null || c2 == null)
            return false;
        c1 = stripTitles(c1);
        c2 = stripTitles(c2);

        if(c1.length()<=1 || c2.length()<=1)
            return false;

        if (c1.equals(c2))
            return true;

        //there is no point moving forward if this is the case
        if(FeatureDictionary.sws.contains(c1.toLowerCase()) || FeatureDictionary.sws.contains(c2.toLowerCase()))
            return false;

        c1 = flipComma(c1);
        c2 = flipComma(c2);

        Set<String> bow1, bow2;
        //They have same acronyms and share non-acronym wo

        //acronym check
        if (isAcronymOf(c2, c1) || isAcronymOf(c1, c2))
            return true;
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
        if(minS == 0){
            log.info("BOW of one of: "+c1+", "+c2+" is null! "+bow1+", "+bow2);
        }
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
                if(p==null || (((double)p.getFirst()/p.getSecond()>0.3) && (EnglishDictionary.getCommonNames().contains(str) || p.getSecond()<500))) {
                    return true;
                }
            }
        }

        return false;
    }

    public static class Cluster implements Comparable<Cluster> {
        Set<String> mentions = new LinkedHashSet<>();
        public void add(String str){
            mentions.add(str);
        }

        @Override
        public int hashCode() {return mentions.hashCode();}

        @Override
        public String toString() {return "==========\n"+mentions.toString();}

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof Cluster))
                return false;
            Cluster c = (Cluster) o;
            if (this.mentions.size() != c.mentions.size())
                return false;
            for (String str : c.mentions)
                if (!mentions.contains(str)) {
                    return false;
                }
            return true;
        }

        @Override
        public int compareTo(Cluster c){
            if(c==null)
                return -1;

            return ((Integer)c.mentions.size()).compareTo(mentions.size());
        }
    }

    public static void findClusters(Archive archive){
        //The size of the sliding window in number of months and quantum jumps of the window again in months
        //for example window: 12 and quantum: 1 means all the messages in batches of one year are considered and the batch is moved by a month after every processing step
        //The sliding window is the blocking mechanism here
        if(archive == null)
            return;
        int WINDOW = 12, QUANTUM = 1;
        try {
            List<Document> docs = archive.getAllDocs();
            //reverse chronological order, recent last
            Collections.sort(docs);
            int startIdx = -1;
            EmailDocument startDoc = (EmailDocument)docs.get(0);
            EmailDocument endDoc = (EmailDocument)docs.get(docs.size()-1);
            Calendar startCal = new GregorianCalendar(), endCal = new GregorianCalendar();
            startCal.setTime(startDoc.getDate());endCal.setTime(endDoc.getDate());
            int totalMonths = endCal.get(Calendar.MONTH)-startCal.get(Calendar.MONTH) + 12*(endCal.get(Calendar.YEAR)-startCal.get(Calendar.YEAR));
            //only include index for clusters with number of mention>1
            Map<String, Integer> clusterIdx = new LinkedHashMap<>();
            //list of clusters with more than one candidate in the merges, the elements in this list are not deleted when the window is slided, so keep it short
            List<Cluster> clusters = new ArrayList<>();
            Tokenizer tokenizer = new CICTokenizer();
            archive.assignThreadIds();
            EmailHierarchy hierarchy = new EmailHierarchy();
            RAMDirectory dir = new RAMDirectory();
            Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47);
            IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(Version.LUCENE_47, analyzer));
            for(int endIdx = 0; endIdx<totalMonths; endIdx+=QUANTUM){
                if(endIdx-startIdx>=WINDOW)
                    startIdx++;
                //remove the old docs and update the clusters with newly found links between nouns.
                DirectoryReader reader = null;
                if(startIdx>=1){
                    BooleanQuery bq = new BooleanQuery();
                    bq.add(NumericRangeQuery.newIntRange("month", startCal.get(Calendar.MONTH)+(startIdx-1)%12, startCal.get(Calendar.MONTH)+startIdx%12, true, false), BooleanClause.Occur.MUST);
                    bq.add(NumericRangeQuery.newIntRange("year", startCal.get(Calendar.YEAR)+(startIdx-1)/12, startCal.get(Calendar.YEAR)+((startIdx-1)/12), true, false), BooleanClause.Occur.MUST);
                    writer.deleteDocuments(bq);
                    try {
                        reader = DirectoryReader.open(dir);
                    }catch(IndexNotFoundException e){
                        e.printStackTrace();
                    }
                    log.warn("Number of deleted docs: " + reader.numDeletedDocs());
                }
                if(reader == null) {
                    try {
                        reader = DirectoryReader.open(dir);
                    }catch(IndexNotFoundException e){
                        e.printStackTrace();
                    }
                }
                //add the new docs corresponding to incremented endIdx and link these new CICs to the old ones.
                Calendar cal1 = new GregorianCalendar();cal1.set(startCal.get(Calendar.YEAR)+(endIdx/12), startCal.get(Calendar.MONTH)+endIdx%12,1);
                Calendar cal2 = new GregorianCalendar();cal2.set(startCal.get(Calendar.YEAR)+((endIdx+1)/12), startCal.get(Calendar.MONTH)+(endIdx+1)%12,1);
                Collection<DatedDocument> newDocs = archive.docsInDateRange(cal1.getTime(), cal2.getTime());
                log.info("Between "+cal1.getTime()+", "+cal2.getTime()+" - #docs: "+newDocs.size()+" - "+startCal.getTime());
                for(Document doc: newDocs){
                    EmailDocument ed = (EmailDocument) doc;
                    String body = archive.getContents(doc, false);
                    String subject = ed.getSubject();
                    //people in the headers
                    List<String> hpeople = ed.getAllNames();
                    Set<String> cics = new LinkedHashSet<>();
                    cics.addAll(tokenizer.tokenizeWithoutOffsets(body, false));
                    cics.addAll(tokenizer.tokenizeWithoutOffsets(body,true));
                    cics.addAll(tokenizer.tokenizeWithoutOffsets(subject,false));
                    cics.addAll(tokenizer.tokenizeWithoutOffsets(subject,true));
                    cics.addAll(hpeople);

                    //Addresses are more specific than names, in case if two people have the same name but different email address
                    for(String cic: cics){
                        if(cic == null)
                            continue;
                        org.apache.lucene.document.Document ldoc = new org.apache.lucene.document.Document();
                        ldoc.add(new TextField("cic", cic, Field.Store.YES));
                        for(int level = 0;level<hierarchy.getNumLevels();level++) {
                            String val = hierarchy.getValue(level, ed);
                            if(val!=null)
                                ldoc.add(new StringField(hierarchy.getName(level), val, Field.Store.YES));
                        }
                        Calendar currCal = new GregorianCalendar();currCal.setTime(ed.date);
                        ldoc.add(new IntField("month", currCal.get(Calendar.MONTH), Field.Store.YES));
                        ldoc.add(new IntField("year", currCal.get(Calendar.YEAR), Field.Store.YES));
                        ldoc.add(new LongField("date", ed.date.getTime(), Field.Store.YES));
                        //hopefully this is more efficient than regexp match
                        if(!"ac".equals(FeatureGeneratorUtil.tokenFeature(cic))) {
                            String acr = Util.getAcronym(cic);
                            if(acr!=null)
                                ldoc.add(new StringField("acr", acr, Field.Store.YES));
                        }
                        //System.out.println("Adding document: " + ldoc);
                        writer.addDocument(ldoc);
                        //if it is a header name, there is no need to recognize the semantic type (Well! most of the times.)
                        /*TODO mark if the CIC is a header name*/
                    }

                    if(reader!=null) {
                        IndexSearcher searcher = new IndexSearcher(reader);

                        //see if any of the cic pattern can be linked
                        for (String cic : cics) {
                            if(cic==null)
                                continue;
                            String cicField = "cic";
                            String tc = FeatureGeneratorUtil.tokenFeature(cic);
                            if ("ac".equals(tc))
                                cicField = "acr";
                            for (int level = 0; level < hierarchy.getNumLevels(); level++) {
                                //retrieve lucene docs based on search for cic
                                BooleanQuery bq = new BooleanQuery();
                                String val = hierarchy.getValue(level, ed);
                                if(val==null)
                                    continue;

                                bq.add(new TermQuery(new Term(hierarchy.getName(level), val)), BooleanClause.Occur.MUST);
                                bq.add(new TermQuery(new Term(cicField, cic)), BooleanClause.Occur.MUST);
                                TopDocs tds = searcher.search(bq, Integer.MAX_VALUE);
                                String bestMatch = null;
                                long bestDiff = -1;
                                //if there are multiple hits, just accept the hit closest in time
                                for (ScoreDoc sd : tds.scoreDocs) {
                                    org.apache.lucene.document.Document hdoc = searcher.doc(sd.doc);
                                    long time = (long) hdoc.getField("date").numericValue();
                                    //this will always be +ve
                                    String cand = hdoc.get("cic");
                                    if(cic.equals(cand))
                                        continue;
                                    if (("ic".equals(tc) || isValidMerge(cand, cic)) && ed.date.getTime() - time > bestDiff) {
                                        bestMatch = hdoc.get("cic");
                                        bestDiff = ed.date.getTime() - time;
                                    }
                                }
                                if (bestMatch != null) {
                                    if (clusterIdx.containsKey(cic)) {
                                        int idx = clusterIdx.get(cic);
                                        clusters.get(idx).add(bestMatch);
                                        clusterIdx.put(bestMatch, idx);
                                    } else if (clusterIdx.containsKey(bestMatch)) {
                                        int idx = clusterIdx.get(cic);
                                        clusters.get(clusterIdx.get(cic)).add(cic);
                                        clusterIdx.put(cic, idx);
                                    } else {
                                        int idx = clusters.size();
                                        clusters.add(new Cluster());
                                        clusters.get(idx).add(cic);
                                        clusters.get(idx).add(bestMatch);
                                        clusterIdx.put(cic, idx);
                                        clusterIdx.put(bestMatch, idx);
                                    }
                                    log.info("Found: " + cic + "<->" + bestMatch + " level: " + level);
                                    break;
                                }
                            }
                        }
                    }
                    writer.commit();
                }
                log.info("Found #"+clusters.size()+" clusters. Steps: "+endIdx+"/"+totalMonths);
            }

            Collections.sort(clusters);
            for(Cluster clx: clusters){
                log.info(clx);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void test() {
        BOWtest();
        Map<Pair<String,String>,Boolean> tps = new LinkedHashMap<>();
        tps.put(new Pair<>("NYTimes", "NY Times"),true);
        tps.put(new Pair<>("NY Times", "New York Times"), true);
        tps.put(new Pair<>("The New York Times", "New York Times"), true);
        tps.put(new Pair<>("Times", "New York Times"), false);
        tps.put(new Pair<>("The Times", "New York Times"), false);
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
        tps.put(new Pair<>("Thomas Burton", "Tim Burton"), true);
        tps.put(new Pair<>("Robert", "Bob Creeley"), true);
        tps.put(new Pair<>("Robert Creeley", "Bob Creeley"), true);
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

        tps.put(new Pair<>("Chicago University", "Chicago Square"), false);
        tps.put(new Pair<>("A. J. Cheyer","Adam Cheyer"), true);
        tps.put(new Pair<>("Prez Abdul Kalam","Abdul J Kalam"), true);
        //When we mark two phrases as a valid merge based on one common word then we check if it is a common word,
        //since we rely on american national corpus for such frequencies (stats.txt), some of the valid merges like the one below are marked wrong
        //yet to deal with this problem
        tps.put(new Pair<>("Washington", "Washington State"), true);
        tps.put(new Pair<>("Dharwad University","Dharwad"), false);

        int numFailed = 0;
        long st = System.currentTimeMillis();
        for(Map.Entry e: tps.entrySet()) {
            boolean expected = (boolean)e.getValue();
            String cand1 = ((Pair<String,String>)e.getKey()).first;
            String cand2 = ((Pair<String,String>)e.getKey()).second;
            if (isValidMerge(cand1, cand2) != expected) {
                System.err.println(cand1 + " - " + cand2 + ", expected: " + expected);
                numFailed++;
            }
        }
        System.err.println("All tests done in: "+(System.currentTimeMillis()-st)+"ms\nFailed ["+numFailed+"/"+tps.size()+"]");
    }

    public static void BOWtest(){
        String[] phrases = new String[]{"NYTimes","DaVinci","Vincent VanGogh", "George H.W. Bush","George H W Bush","George W. Bush"};
        String[][] expected = new String[][]{
                new String[]{"ny","time"},
                new String[]{"da","vinci"},
                new String[]{"vincent","van","gogh"},
                new String[]{"george","bush"},
                new String[]{"george","bush"},
                new String[]{"george","bush"}
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
        System.out.println("Bag of Words test done!");
    }

    public static void main(String[] args) {
        //BOWtest();
        //test();
        try {
            String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user-terry-important";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            findClusters(archive);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
