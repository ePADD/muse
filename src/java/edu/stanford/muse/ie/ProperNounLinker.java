package edu.stanford.muse.ie;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import edu.stanford.muse.index.*;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.Pair;

import edu.stanford.muse.webapp.SimpleSessions;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
        if(phrase == null)
            return new LinkedHashSet<>();
        phrase = stripTitles(phrase);
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
                //1. an upper case surrounded by lower cases, VanGogh = Van Gogh
                //2. an upper case character with lower case stuff to the right, like 'T' in NYTimes = NY Times
                //3. an upper case char preceded by '.' H.W.=>H. W.
                //4. Also split on hyphens
                if ((cUc && ti > 0 && ti < tok.length()-1 && ((!pUc && !nUc) || (pUc && !nUc) || tok.charAt(ti-1)=='.')) || tok.charAt(ti)=='-') {
                    //don't consider single chars or essentially single chars like 'H.' as words
                    if(buff.length()>2 || (buff.length()==2 && buff.charAt(buff.length()-1)!='.'))
                        subToks.add(buff);
                    if(tok.charAt(ti) != '-')
                        buff = ""+tok.charAt(ti);
                    else
                        buff = "";
                } else {
                    buff += tok.charAt(ti);
                }
            }
            if(buff.length()>2 || (buff.length()==2 && buff.charAt(buff.length()-1)!='.'))
                subToks.add(buff);
            for (String st : subToks) {
                String ct = EnglishDictionary.getSingular(st);
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
        //the pattern below should pick up all the extra chars that CIC tokenizer allows in the name, else may end up classifying Non-consecutive and Non-profit as a valid merge
        Pattern p = Pattern.compile("[A-Z][a-z']+");
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

        String lc = str.toLowerCase();
        for(String t: titles)
            if(lc.startsWith(t+" "))
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
            //log.info("BOW of one of: "+c1+", "+c2+" is null! "+bow1+", "+bow2);
            return false;
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
                        || (bw1.length()>1 && bw1.charAt(bw1.length()-1)=='.' && bw2.startsWith(bw1.substring(0,bw1.length()-1)))
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
                String word = sbow.iterator().next();
                if (word.length()<3)
                    return false;

                int idx;
                String[] cands = new String[]{c1.toLowerCase(), c2.toLowerCase()};
                boolean dirty = false;
                for(String cand: cands) {
                    String prevWord = null;
                    if ((idx = cand.indexOf(" " + word)) > 0) {
                        int prevSpace = cand.substring(0, idx).lastIndexOf(" ");
                        if (prevSpace < 0)
                            prevSpace = 0;
                        prevWord = cand.substring(prevSpace+1, idx);
                    }
                    if (prevWord!=null && EnglishDictionary.stopWords.contains(prevWord.toLowerCase())) {
                        dirty = true;
                        //log.info("Considering cands "+c1+" and "+c2+" matching on "+prevWord+" as dirty.");
                        break;
                    }
                }
                if(dirty)
                    return false;

                String str = word.toLowerCase();
                str = str.replaceAll("^\\W+|\\W+$","");
                Pair<Integer,Integer> p = EnglishDictionary.getDictStats().get(str);

                if(p==null || (((double)p.getFirst()/p.getSecond()>0.3) && (EnglishDictionary.getCommonNames().contains(str) || p.getSecond()<500))) {
                    return true;
                }
            }
        }

        return false;
    }

    static String getLastWord(String phrase) {
        String word = "";
        boolean start = false;
        for (int x = phrase.length() - 1; x >= 0; x--) {
            if (!Character.isLetterOrDigit(phrase.charAt(x))) {
                if (start)
                    break;
            } else {
                word = phrase.charAt(x)+word;
                if (!start) start = true;
            }
        }
        return word;
    }

    static String getFirstWord(String phrase) {
        String word = "";
        boolean start = false;
        for (int x = 0; x < phrase.length(); x++) {
            if (!Character.isLetterOrDigit(phrase.charAt(x))) {
                if (start)
                    break;
            } else {
                word += phrase.charAt(x);
                if (!start) start = true;
            }
        }
        return word;
    }

    /**
     * This is a much simpler merge evaluator
     * assumes that one of c1,c2 is a single word*/
    static boolean isValidMergeSimple(String c1, String c2) {
        if(c1.contains(" ") && c2.contains(" ")) {
            log.warn("Cannot handle [" + c1 + ", " + c2 + "] since they both contain space");
            return false;
        }
        c1 = stripTitles(c1);
        c2 = stripTitles(c2);
        if (c1.length() == 0 || c2.length()==0)
            return false;

        if(c1.length()>c2.length()) {
            String temp = c1;
            c1 = c2; c2 = temp;
        }
        if(DictUtils.fullDictWords.contains(c1.toLowerCase()))
            return false;
        c2 = flipComma(c2);

        String c1type = FeatureGeneratorUtil.tokenFeature(c1);
        if ("ac".equals(c1type)) {
            if (Util.getAcronym(c2).equals(c1))
                return true;
            else return false;
        }
        int idx;
        if ((idx = c2.indexOf(c1))<0)
            return false;
        if (idx>0 && Character.isLetterOrDigit(c2.charAt(idx-1)))
            return false;
        int endIdx = idx+c1.length();
        if (endIdx<c2.length() && Character.isLetterOrDigit(c2.charAt(endIdx)))
            return false;
        //make sure the previous or the next word is not a stop word
        String prevChunk = c2.substring(0,idx);
        String nxtChunk = c2.substring(idx+c1.length());
        if(prevChunk.length()>0 && prevChunk.charAt(prevChunk.length()-1)==' '){
            String prevWord = getLastWord(prevChunk);
            if (EnglishDictionary.stopWords.contains(prevWord.toLowerCase()))
                return false;
        }
        if(nxtChunk.length()>0 && nxtChunk.charAt(0)==' '){
            String nxtWord = getFirstWord(nxtChunk);
            if (EnglishDictionary.stopWords.contains(nxtWord.toLowerCase()))
                return false;
        }
        return true;
    }

    public static class Mentions{
        final int WINDOW = 12;
        Hierarchy hierarchy;
        //map from tokens to corresponding index in the mentions array
        public Map<String,Set<Integer>> tokenToMentionIdx = new LinkedHashMap<>();
        public Map<String,Set<Integer>> acronymToMentionIdx = new LinkedHashMap<>();
        //year*12+month=date to indexes in the mentions array
        public Map<Integer,Set<Integer>> dateToMentionIdx = new LinkedHashMap<>();
        List<EmailMention> mentions = new ArrayList<>();
        public Mentions(Hierarchy hierarchy){
            this.hierarchy = hierarchy;
        }

        public void add(Set<String> mentions, Object context){
            long st = System.currentTimeMillis();
            for(String m: mentions)
                add(new EmailMention(m, context));
//            log.info("Added #"+mentions.size()+" mentions to the index in "+(System.currentTimeMillis()-st)+"ms");
        }

        public void add(EmailMention mention) {
            if(mention == null || mention.phrase == null)
                return;

            checkIfIndexRecent(mention);
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(mention.getDate());
            int time = cal.get(Calendar.MONTH)+cal.get(Calendar.YEAR)*12;

            if(!dateToMentionIdx.containsKey(time))
                dateToMentionIdx.put(time, new LinkedHashSet<>());
            dateToMentionIdx.get(time).add(mentions.size());
            Set<String> bow = bow(mention.phrase);
            for(String word: bow){
                word = word.toLowerCase();
                if(!tokenToMentionIdx.containsKey(word))
                    tokenToMentionIdx.put(word, new LinkedHashSet<>());
                tokenToMentionIdx.get(word).add(mentions.size());
            }
            //get acronym if it is not already an acronym
            String tc = FeatureGeneratorUtil.tokenFeature(mention.phrase);
            if(!tc.equals("ac")) {
                String acr = Util.getAcronym(mention.phrase);
                if (acr != null) {
                    if (!acronymToMentionIdx.containsKey(acr))
                        acronymToMentionIdx.put(acr, new LinkedHashSet<>());
                    acronymToMentionIdx.get(acr).add(mentions.size());
                }
            }

            mentions.add(mention);
        }

        public Pair<String,Integer> getNearestMatch(EmailMention mention) {
            if(mention == null || mention.phrase==null)
                return new Pair<>(null,-1);

            //collect cands. first
            Set<Integer> mIdxs = new LinkedHashSet<>();
            String tc = FeatureGeneratorUtil.tokenFeature(mention.phrase);
            if(tc.equals("ac")) {
                //if the acronym is of size less than 3, the expansion is wrong most of the time
                //CY<->Companys Yosem - Level: 5 - UID: /home/dev/data/Terry/terry-split/Important-7454
                //Found a match HC<->Hong Cao, CC<->Christy Chin
                if (mention.phrase.length() > 2) {
                    if (acronymToMentionIdx.containsKey(mention.phrase))
                        mIdxs.addAll(acronymToMentionIdx.get(mention.phrase));
                }
                else {
                    return new Pair<>(null,-1);
                }
            }
            else {
                Set<String> bow = bow(mention.phrase);
                int ti = 0;
                for (String tok : bow) {
                    tok = tok.toLowerCase();
                    if (tokenToMentionIdx.containsKey(tok)) {
                        if(ti == 0)
                            mIdxs.addAll(tokenToMentionIdx.get(tok));
                        else
                            mIdxs = new LinkedHashSet<>(Sets.intersection(mIdxs, tokenToMentionIdx.get(tok)));
                    }
                    ti++;
                }
            }

            Map<Integer, Long> levelMap = new LinkedHashMap<>();
            String vLevels[] = new String[hierarchy.getNumLevels()];
            for(int l=0;l<hierarchy.getNumLevels();l++)
                vLevels[l] = hierarchy.getValue(l, mention.context);
            long time = mention.getDate().getTime();
            long MAX_DIFF = (WINDOW+1)*31*24*3600*1000L;
            if(log.isDebugEnabled())
                if (mIdxs.size()>100)
                    log.debug("Found #"+mIdxs.size()+" mentions for "+mention.phrase);

            for (int mid : mIdxs) {
                EmailMention mmention = mentions.get(mid);
                for (int l = 0; l < hierarchy.getNumLevels(); l++) {
                    String mv = hierarchy.getValue(l, mmention.context);
                    if(mv==null)
                        continue;
                    if (mv.equals(vLevels[l])) {
                        long val = Math.abs(mmention.getDate().getTime() - time);
                        if(val>=MAX_DIFF)
                            log.error("FATAL!!! Time diff. between mentions is more than the maximum expected!! "+val+", "+MAX_DIFF+" -- "+(val/(31*24*3600*1000L))+" months... "+dateToMentionIdx.keySet());
                        levelMap.put(mid, (l*MAX_DIFF) + val);
                        break;
                    }
                }
            }
            //sorts in descending value
            List<Pair<Integer,Long>> order = edu.stanford.muse.util.Util.sortMapByValue(levelMap);
            Collections.reverse(order);
            int nValidCheck = 0;
            for(Pair<Integer,Long> p: order) {
                EmailMention mmention = mentions.get(p.getFirst());
                int level = (int) (p.getSecond()/MAX_DIFF);
                if (mention!=null && mention.phrase!=null && !mention.phrase.equals(mmention.phrase)) {
                    nValidCheck++;
                    if (isValidMergeSimple(mmention.phrase, mention.phrase))
                        return new Pair<>(mmention.phrase, level);
                }
            }
            return new Pair<>(null, -1);
        }

        void checkIfIndexRecent(EmailMention mention){
            Date d = mention.getDate();
            Calendar curr = new GregorianCalendar();curr.setTime(d);
            int currTime = curr.get(Calendar.YEAR)*12+curr.get(Calendar.MONTH);
            int oldest = 100000;
            for(int time: dateToMentionIdx.keySet())
                if(time<oldest)
                    oldest = time;

            if(currTime<oldest)
                log.warn("FATAL!!! Data order not in chronological order?!");
            while(currTime-oldest>WINDOW) {
                if(dateToMentionIdx.containsKey(oldest))
                    remove(oldest);
                oldest++;
            }
        }

        /**
         * input: docs belonging to this time window are erased and the indices are updated*/
        void remove(int time){
            long st = System.currentTimeMillis();
            Set<Integer> mentionIdxs = dateToMentionIdx.get(time);
            if(mentionIdxs == null)
                log.warn("SERIOUS WARNING!! Time requested fro deletion unknown --- " + time + ", known times: "+dateToMentionIdx.keySet());

            //nothing to delete
            if(mentionIdxs.size() == 0)
                return;
            //map from old idx to new idx
            Map<Integer,Integer> oldTonew = new LinkedHashMap<>();
            int numDeletions = 0;
            for(int mi=0;mi<mentions.size();mi++) {
                if (mentionIdxs.contains(mi)) {
                    numDeletions++;
                    oldTonew.put(mi, -1);
                }else{
                    if(mi<numDeletions)
                        log.error("Something is wrong!! -ve value in oldTonew Index");
                    oldTonew.put(mi,mi-numDeletions);
                }
            }
            dateToMentionIdx.remove(time);
            List<EmailMention> newMentions = new ArrayList<>();
            for(int mi=0;mi<mentions.size();mi++)
                if(!mentionIdxs.contains(mi)) {
                    Date d = mentions.get(mi).getDate();
                    Calendar cal = new GregorianCalendar();
                    cal.setTime(d);
                    int t = cal.get(Calendar.YEAR)*12+cal.get(Calendar.MONTH);
                    if(t<=time) {
                        System.err.println("Removal is not proper still see: " + (t / 12) + ", " + (t % 12) + " -- " + (time / 12) + ", " + (time % 12) + "\nMID: " + mi + " -- " + mentionIdxs);
                        for(int x: dateToMentionIdx.keySet())
                            if(dateToMentionIdx.get(x).contains(mi))
                                System.err.println("In idx: " + (x / 12) + "," + (x % 12));
                    }
                    newMentions.add(mentions.get(mi));
                }
            mentions = newMentions;

            for(int date: dateToMentionIdx.keySet()){
                Set<Integer> dateIdxs = dateToMentionIdx.get(date);
                Set<Integer> newIdxs = new LinkedHashSet<>();
                for(int di: dateIdxs) {
                    Integer newIdx = oldTonew.get(di);
                    if(newIdx==null)
                        log.error("Something is not right!! Cannot translate the old index: "+di+".\n There are #"+oldTonew.size()+" entries in old to new translation map");
                    else if(newIdx!=-1)
                        newIdxs.add(newIdx);
                }
                dateToMentionIdx.put(date, newIdxs);
            }

            for(String token: tokenToMentionIdx.keySet()){
                //new posting list
                Set<Integer> newPL = new LinkedHashSet<>();
                for(Integer idx: tokenToMentionIdx.get(token)) {
                    Integer newIdx = oldTonew.get(idx);
                    if(newIdx==null)
                        log.error("Something is not right!! Cannot translate the old index: "+idx+".\n There are #"+oldTonew.size()+" entries in old to new translation map");
                    else if(newIdx!=-1)
                        newPL.add(newIdx);
                }
                tokenToMentionIdx.put(token, newPL);
            }

            for(String acr: acronymToMentionIdx.keySet()){
                //new posting list
                Set<Integer> newPL = new LinkedHashSet<>();
                for(Integer idx: acronymToMentionIdx.get(acr)) {
                    Integer newIdx = oldTonew.get(idx);
                    if(newIdx==null)
                        log.error("Something is not right!! Cannot translate the old index: "+idx+".\n There are #"+oldTonew.size()+" entries in old to new translation map");
                    else if(newIdx!=-1)
                        newPL.add(newIdx);
                }
                acronymToMentionIdx.put(acr, newPL);
            }
            log.info("Removed #"+mentionIdxs.size()+" mentions belonging to month: "+time%12+", "+(time/12) +" "+ dateToMentionIdx.keySet() + " in "+(System.currentTimeMillis()-st)+"ms");
            log.info(getStats());
        }
        public String getStats(){
            return ("Stats -- Number of mentions: "+ mentions.size()+"; token index size: "+tokenToMentionIdx.size()+"; date index size: "+dateToMentionIdx.size());
        }
    }

    public static class EmailMention{
        public String phrase;
        public EmailDocument context;
        public EmailMention(String phrase, Object context){
            this.phrase = phrase;
            if(context instanceof EmailDocument) {
                this.context = (EmailDocument)context;
            }
            else {
                log.error("FATAL!!! Cannot handle context of type: " + (context == null ? "null" : context.getClass().getName()));
                return;
            }
        }

        public Date getDate(){
            if(context == null) return null;
            return context.getDate();
        }
    }

    public static void findClusters(Archive archive){
        //The size of the sliding window in number of months and quantum jumps of the window again in months
        //for example window: 12 and quantum: 1 means all the messages in batches of one year are considered and the batch is moved by a month after every processing step
        //The sliding window is the blocking mechanism here
        if(archive == null)
            return;
        long st = System.currentTimeMillis();
        try {
            List<Document> docs = archive.getAllDocs();
            //reverse chronological order, recent last
            Collections.sort(docs);
            EmailDocument startDoc = (EmailDocument) docs.get(0);
            EmailDocument endDoc = (EmailDocument) docs.get(docs.size() - 1);
            Calendar startCal = new GregorianCalendar(), endCal = new GregorianCalendar();
            startCal.setTime(startDoc.getDate());
            endCal.setTime(endDoc.getDate());
            Tokenizer tokenizer = new CICTokenizer();
            archive.assignThreadIds();
            EmailHierarchy hierarchy = new EmailHierarchy();
            Mentions mentions = new Mentions(hierarchy);
            int di = 0;

            long addTime = 0, searchTime = 0;
            for (Document doc : docs) {
                EmailDocument ed = (EmailDocument) doc;
                String body = archive.getContents(doc, false);
                String subject = ed.getSubject();
                //people in the headers
                List<String> hpeople = ed.getAllNames();
                Set<String> cics = new LinkedHashSet<>();
                cics.addAll(tokenizer.tokenizeWithoutOffsets(body));
                cics.addAll(tokenizer.tokenizeWithoutOffsets(subject));
                cics.addAll(hpeople);
                Set<String> names = new LinkedHashSet<>();
                Map<Short, Map<String, Double>> map = edu.stanford.muse.ner.NER.getEntities(doc, true, archive);

                double thresh = 0.001;
                for (Short t : map.keySet())
                    if (t != FeatureDictionary.OTHER)
                        for (String str : map.get(t).keySet())
                            if (map.get(t).get(str) > thresh)
                                names.add(str);

                names.addAll(hpeople);
                long st1 = System.currentTimeMillis();
                mentions.add(names, ed);
                addTime += System.currentTimeMillis() - st1;

                //The SequenceModel based NER model fails or does not do a very good job on
                //1. Single word names [It just gives up on these, unless it is a country name or a non-dictionary word that appears in a longer CIC phrase in which case it may end up recognising it but should not rely on it]
                //2. Single word names also includes acronyms
                //3. Person names of the format: [A-Z].? [A-Z][A-Za-z']+
                //4. It may also fail at phrases of format: [FIRST NAME] (&|and) [FIRST NAME]. Yes, it can magically handle phrases like "Adam M. Weber and Sophie Reine" into two persons
                for (String cic : cics) {
                    if (cic == null)
                        continue;
                    String tokens[] = new String[]{cic};
                    int idx;
                    if ((idx = cic.indexOf(" and ")) > -1) {
                        if (!cic.substring(0, idx).contains(" ") && !cic.substring(idx + 5).contains(" "))
                            tokens = new String[]{cic.substring(0, idx), cic.substring(idx + 5)};
                    }
                    for (String token : tokens) {
                        cic = token;
                        String coreCIC = stripTitles(cic);
                        String type = FeatureGeneratorUtil.tokenFeature(cic);
                        if (!coreCIC.contains(" ") || type.equals("ac")) {
                            st1 = System.currentTimeMillis();
                            Pair<String, Integer> match = mentions.getNearestMatch(new EmailMention(coreCIC, ed));
                            searchTime += System.currentTimeMillis() - st1;
                            if (match.getSecond() >= 0)
                                log.info("Found a match " + cic + "<->" + match.getFirst() + " - Level: " + match.getSecond() + " - UID: " + ed.getUniqueId());
                        }
                    }
                }
                if (di++ % 1000 == 0) {
                    log.info("Search time: "+searchTime+" -- Add time: "+addTime);
                    log.info("Done processing: " + di + "/" + docs.size() + " -- elapsed time: " + (System.currentTimeMillis() - st) + "ms -- estimated time " + ((float) (System.currentTimeMillis() - st) / (di * 1000 * 60)) * (docs.size() - di) + " minutes");
                }
            }

            log.info("Done finding merges in " + (System.currentTimeMillis() - st) + "ms");
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
        tps.put(new Pair<>("University of Chicago", "Chicago Univ."), true);
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
        tps.put(new Pair<>("Dumontier Lab", "Lab"), false);
        tps.put(new Pair<>("Sudheendra Hangal", "Jaya Hangal"), false);
        tps.put(new Pair<>("DJBDX Thank", "Thank"), false);
        tps.put(new Pair<>("Non-ProfitOrganisation", "Non-profit"), false);
        tps.put(new Pair<>("Non-ProfitOrganisation", "Non-consecutive"), true);
        tps.put(new Pair<>("Stanford University","Washington University in St. Louis"), false);
        tps.put(new Pair<>("McAfee Research","MC"), false);
        tps.put(new Pair<>("Blog","Presto Blog Digest"), false);
        tps.put(new Pair<>("Rubin","Should Rubin"), true);
        int numFailed = 0, numTest = 0;
        long st = System.currentTimeMillis();
        for(Map.Entry e: tps.entrySet()) {
            boolean expected = (boolean)e.getValue();
            String cand1 = ((Pair<String,String>)e.getKey()).first;
            String cand2 = ((Pair<String,String>)e.getKey()).second;
            if(!cand1.contains(" ") || !cand2.contains(" ")) {
                if (isValidMergeSimple(cand1, cand2) != expected) {
                    System.err.println(cand1 + " - " + cand2 + ", expected: " + expected);
                    numFailed++;
                }
                numTest++;
            }
        }
        System.err.println("All tests done in: "+(System.currentTimeMillis()-st)+"ms\nFailed ["+numFailed+"/"+numTest+"]");
    }

    public static void BOWtest(){
        String[] phrases = new String[]{"NYTimes","DaVinci","Vincent VanGogh", "George H.W. Bush","George H W Bush","George W. Bush","Non-consecutive"};
        String[][] expected = new String[][]{
                new String[]{"ny","time"},
                new String[]{"da","vinci"},
                new String[]{"vincent","van","gogh"},
                new String[]{"george","bush"},
                new String[]{"george","bush"},
                new String[]{"george","bush"},
                new String[]{"non","consecutive"}
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
//        BOWtest();
//        test();
        try {
            String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user-terry-important";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            findClusters(archive);
        }catch(Exception e){
            e.printStackTrace();
        }
//        try {
//            RAMDirectory dir = new RAMDirectory();
//            Analyzer analyzer = new EnglishAnalyzer(Version.LUCENE_47, Indexer.MUSE_STOP_WORDS_SET);
//            IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(Version.LUCENE_47, analyzer));
//            org.apache.lucene.document.Document ldoc = new org.apache.lucene.document.Document();
//            ldoc.add(new TextField("content","Some gibberish and something else, hello",Field.Store.YES));
//            writer.addDocument(ldoc);
//            writer.commit();
//            DirectoryReader reader = DirectoryReader.open(dir);
//            IndexSearcher searcher = new IndexSearcher(reader);
//            // Parse a simple query that searches for "text":
//            QueryParser parser = new QueryParser(Version.LUCENE_47, "content", analyzer);
//            Query query = parser.parse("some something nothing");
//            TopDocs tds = searcher.search(query,10);
//            for(ScoreDoc sd: tds.scoreDocs)
//                System.err.println("Content: "+searcher.doc(sd.doc).get("content"));
//        }catch(Exception e){
//            e.printStackTrace();
//        }
    }
}
