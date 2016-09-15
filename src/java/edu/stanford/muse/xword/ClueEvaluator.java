package edu.stanford.muse.xword;

import com.google.common.collect.Sets;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Span;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by vihari on 11/12/15.
 * Note: Though the classes are called ClueEvaluators, they actually evaluate: Answer + Clue
 * for example: if an NER Model can recognise a answer as entity in clue or if the answer has been mentioned after a long period
 */
    abstract public class ClueEvaluator {
        public static Log log = LogFactory.getLog(ClueEvaluator.class);
        abstract public double computeScore(Clue clue, String answer,  NERModel nerModel, Archive archive);

    public static class LengthEvaluator extends ClueEvaluator{
        float[] params;

        /**
         * The parameters are the weight scores when the sentence length is
         * less than MIN_PREFERRED_CLUE_LENGTH
         * greater than MAX_PREFERRED_CLUE_LENGTH
         * if in the [MIN,MAX] range respectively
         * default [-100.0f, -10.0f, 0f]*/
        public LengthEvaluator(float[] params) {
            this();
            if (params == null || params.length != 3) {
                log.error("Wrong initialisation of params in " + LengthEvaluator.class + "!! Required 3 params");
            } else {
                this.params = params;
            }
        }

        public LengthEvaluator(){
            this.params = new float[]{-100.0f,-10.0f, 0.0f};
        }

        @Override
        public double computeScore(Clue clue, String answer, NERModel nerModel, Archive archive){
            String s = clue.getFullSentenceOriginal();
            // clue gets points the closer it is to the preferred clue length
            // lengthBoost is really a penalty (its -ve)
            float lengthBoost;
            if (s.length() < ArchiveCluer.MIN_PREFERRED_CLUE_LENGTH)
                lengthBoost = params[0] * Math.abs((ArchiveCluer.MIN_PREFERRED_CLUE_LENGTH - (float) s.length())/ArchiveCluer.MIN_PREFERRED_CLUE_LENGTH);
            else if (s.length() > ArchiveCluer.MAX_PREFERRED_CLUE_LENGTH)
                lengthBoost = params[1] * Math.abs( (((float) s.length()) - ArchiveCluer.MAX_PREFERRED_CLUE_LENGTH)/ArchiveCluer.MAX_PREFERRED_CLUE_LENGTH );
            else
                lengthBoost = params[2];

            clue.clueStats.lengthBoost = lengthBoost;
            return lengthBoost;
        }
    }

    public static class EmotionEvaluator extends ClueEvaluator{
        float[] params;
        /**Required param to weight the number of exclamations, question marks and smileys respectively in the sentence
         * default 5.0, 7.0, 7.0*/
        public EmotionEvaluator(float[] params){
            this();
            if(params==null || params.length!=3){
                log.error("Wrong initialisation of params in "+EmotionEvaluator.class+"!! Required 3 params");
            }else {
                this.params = params;
            }
        }
        public EmotionEvaluator(){
            this.params = new float[]{5.0f, 7.0f,7.0f};
        }

        @Override
        public double computeScore(Clue clue, String answer, NERModel nerModel, Archive archive) {
            //TODO: considers two exclamations and question marks as the same, is that OK?
            String s = clue.getFullSentenceOriginal().toLowerCase();
            // prefer exclamations, highly memorable
            float exclamationScore = (s.indexOf("!") >= 0) ? params[0] : 0.0f;
            clue.clueStats.exclamationScore = exclamationScore;

            float questionScore = (s.indexOf("?") >= 0) ? params[1] : 0.0f;
            clue.clueStats.questionMarkScore = questionScore;

            // good if it has emoticons
            int nSmileys = 0;
            for (String smiley : ArchiveCluer.smileys)
                if (s.indexOf(smiley) >= 0) {
                    nSmileys++;
                }
            float smileyScore = params[2] * nSmileys;

            clue.clueStats.nSmileys = nSmileys;
            clue.clueStats.smileyScore = smileyScore;
            //the original code for some reason adds one to the score, just retaining that here.
            return exclamationScore + questionScore + smileyScore;
        }
    }

    public static class NamesEvaluator extends ClueEvaluator {
        float[] params;

        /**
         * Required param to weigh the number of names in the sentence and a boost score for when the name is not found in the clue
         * default: 2.0f and -20.0f
         */
        public NamesEvaluator(float[] params) {
            this();
            if (params == null || params.length != 1) {
                log.error("Wrong initialisation of params in " + NamesEvaluator.class + "!! Required 2 param");
            } else {
                this.params = params;
            }
        }

        public NamesEvaluator(){
            this.params = new float[]{10.0f};
        }

        @Override
        public double computeScore(Clue clue, String answer, NERModel nerModel, Archive archive) {
            String canonicalizedanswer = (Util.canonicalizeSpaces(answer)).toLowerCase();
            double CUTOFF = 0.001;

            Span[] namesOriginal;
            try {
                namesOriginal = archive.getAllOriginalNamesInDoc(clue.d);
            } catch (IOException ioe){
                log.error("Names evaluator fail!! Something is wrong with the archive.",ioe);
                return 0;
            }

            Collection<String> names = Arrays.asList(namesOriginal).stream().filter(n->n.typeScore>CUTOFF).map(n->n.text).collect(Collectors.toSet());

            float namesScore = params[0]*names.size();

            boolean found = false;
            for (String name : names) { //check that NER detects same name in text as the name of the answer (eg. removes instances where NER returns "San Jose" from clue, but answer is "San")
                if (name.equals(canonicalizedanswer)) {
                    found = true;
                    break;
                }
            }

            clue.clueStats.namesInClue = names.size();
            clue.clueStats.clueLength = clue.getFullSentenceOriginal().length();

            clue.clueStats.namesScore = namesScore;
            return namesScore;
        }
    }

    /**Scores clue based on the presence of words from the list
     * The default lists are prepositions and list some hand-selected words
     * Whatever the list is, it should only contain words in lower case*/
    public static class ListEvaluator extends ClueEvaluator {
        float[] params;
        List<Set<String>> lists = new ArrayList<>();

        /**
         * Requires lists and their corresponding weights
         * default lists are
         *     <ul>
         *      <li>"from", "to", "in", "at", "as", "by", "inside", "like", "of", "towards", "toward", "via", "such as", "called", "named", "name"</li>
         *      <li>"flight", "travel", "city", "town", "visit", "arrive", "arriving", "land", "landing", "reach", "reaching", "train", "road", "bus", "college", "theatre", "restaurant", "book", "film", "movie", "play", "song", "writer", "artist", "author", "singer", "actor", "school"</li>
         *     </ul>
         * The default params are: 10.0 and 5.0 respectively
         * Though this class was implemented hoping that every list would be treated the same, the list of prepositions is treated differently.
         * A list is checked for prepositions if it contains "from" and "to"
         */
        public ListEvaluator(float[] params, List<String[]> lists) {
            this();
            if (params==null || lists==null || params.length==0 || params.length!=lists.size()) {
                log.error("Wrong/improper initialisation of params in " + ListEvaluator.class + "!! Required "+lists.size()+" params");
            } else {
                this.params = params;
                this.lists = new ArrayList<>();
                String str = "";
                int p = 0;
                for(String[] lst: lists) {
                    this.lists.add(Sets.newHashSet(lst));
                    str += "Param: "+params[p++]+" - Lst: "+Sets.newHashSet(lst)+"\n";
                }

                log.info("List evaluator initialised with "+this.lists.size()+" lists - "+str);
            }
        }

        public ListEvaluator() {
            lists = Arrays.asList(
                    Sets.newHashSet("from", "to", "in", "at", "as", "by", "inside", "like", "of", "towards", "toward", "via", "such as", "called", "named", "name"),
                    Sets.newHashSet("flight", "travel", "city", "town", "visit", "arrive", "arriving", "land", "landing", "reach", "reaching", "train", "road", "bus", "college", "theatre", "restaurant", "book", "film", "movie", "play", "song", "writer", "artist", "author", "singer", "actor", "school")
            );
            this.params = new float[]{10.0f, 5.0f};
        }

        /**This is not a proper test, sort of a hackaround*/
        static boolean isPrep(Set<String> list){
            return list.contains("from") && list.contains("to");
        }
        static boolean isReflective(Set<String> list){
            return list.contains("absorb") && list.contains("accept");
        }

        static String[] getNeighbours(String next, String tgtWord){
            String a = tgtWord.toLowerCase();
            String sent = next.toLowerCase();
            int idx = sent.indexOf(a);
            if(idx<0)
                return new String[]{};
            String prevToken = "", nxtToken = "";
            String prevS = sent.substring(0,idx);
            String nextS = sent.substring(idx+a.length());
            Matcher pm = Pattern.compile("\\W+([a-z0-9]+)\\W+$").matcher(prevS);
            Matcher nm = Pattern.compile("^\\W+([a-z0-9]+)\\W+").matcher(nextS);
            while(pm.find())
                prevToken = pm.group(1);

            while(nm.find())
                nxtToken = nm.group(1);

            return new String[]{prevToken, nxtToken};
        }

        static String[] getNeighboursOfPronouns(String[] tokens){
            Set<String> pronouns = EnglishDictionary.getTopPronouns();
            List<String> sts = new ArrayList<>();
            if(tokens != null && tokens.length>0) {
                for (int ti = 0; ti < tokens.length; ti++) {
                    if (pronouns.contains(tokens[ti]) && ti < tokens.length-1) {

                        String phrase = "";
                        int[] sis = new int[]{ti+1, ti+2, ti+3};
                        for(int si: sis) {
                            phrase = "";
                            if(si>=tokens.length) {
                                break;
                            }
                            int k = Math.min(si+5, tokens.length);
                            for (int j = si; j < k; j++) {
                                phrase += tokens[j];
                                if (j < k - 1)
                                    phrase += " ";
                            }
                            sts.add(phrase);
                        }
                    }
                }
            }
            return sts.toArray(new String[sts.size()]);
        }

        static String canonicalize(String phrase){
            String[] words = phrase.split("\\s+");
            String cp = "";
            for(int wi=0;wi<words.length;wi++){
                String word = EnglishDictionary.getSingular(words[wi]);
                word = EnglishDictionary.getSimpleForm(word);
                cp += word;
                if(wi<words.length-1)
                    cp+=" ";
            }
            return cp;
        }

        @Override
        public double computeScore(Clue clue, String answer, NERModel nerModel, Archive archive) {
            String s = clue.getFullSentenceOriginal().toLowerCase();
            float boost = 0;
            for(int i=0;i<params.length;i++) {
                if(params[i]==0)
                    continue;
                String[] tokens = s.split("[\\s\\n]+");

                float b = 0;
                if (isPrep(lists.get(i)))
                    tokens = getNeighbours(s, answer);
                else if(isReflective(lists.get(i)))
                    tokens = getNeighboursOfPronouns(tokens);

                //if there is a pronoun at index ti, then it would have covered the ref words in ti+1, ti+2, ti+3
                //we navigate tokens at 3 offset to avoid considering the same ref word multiple times.
                for (int ti=0;ti<tokens.length;ti+=3) {
                    String tok = tokens[ti];
                    tok = tok.replaceAll("^\\W+|\\W+$", "");
                    //log.info("New Lst:"+i+" contains "+tok+"? - "+lists.get(i).contains(tok)+", "+lists.get(i));
                    if(isReflective(lists.get(i))) {
                        Set<String> prefixes = IndexUtils.computeAllPrefixes(tok);
                        for(String prefix: prefixes) {
                            prefix = canonicalize(prefix);
                            if (lists.get(i).contains(prefix)) {
                                clue.clueStats.refWord = clue.clueStats.refWord + "-" + prefix;
                                b += params[i];
                                break;
                            }
                        }
                    }else{
                        b += lists.get(i).contains(tok) ? params[i] : 0.0f;
                    }
                }
                boost += b;
                if(i==0) clue.clueStats.sigWordScore = b;
                else if (i == 1) clue.clueStats.prepositionScore = b;
                else if (i==2) clue.clueStats.refWordScore = b;
                else clue.clueStats.pronounScore = b;
            }
            return boost;
        }
    }

    public static void main(String[] args){
        try {
//            String userDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user";
//            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
//            String answer = "Robert Creeley";
//            Clue clue = new Clue("Is this the address: _________________________________, Plot No: 8, Srinilaya, Radha krishna nagar, Dharwad, Karnataka 580003 can you please confirm.Also, can you please provide your mobile number.Thanks", answer);
//            clue.setFullSentenceOriginal("Is this the address: Nandanavana Bansuri Music Academy, Plot No: 8, Srinilaya, Radha krishna nagar, Dharwad, Karnataka 580003 can you please confirm.Also, can you please provide your mobile number.Thanks");
//            ClueEvaluator eval = new EmailDocumentEvaluator();
//            double score = 0;
//            Calendar sc = Calendar.getInstance(), lc = Calendar.getInstance();
//            sc.set(1993, 2, 11); lc.set(1993, 3, 11);
//            eval.computeScore(score, (short)1, clue, answer, sc.getTime(), lc.getTime(), new LinkedHashSet<String>(), null, archive);
//            System.err.println(Util.fieldsToString(clue.clueStats));
            //score = eval.computeScore(score,clue,"yes", null,null,null);
            //System.err.println("Score: "+score);

//            Indexer.QueryOptions qo = new Indexer.QueryOptions();
//            qo.setSortBy(Indexer.SortBy.CHRONOLOGICAL_ORDER);
//            String term = "\"San Jose\"";
//            log.info("Searching for: "+term);
//            //Collection<Document> docs = archive.docsForQuery(term, qo);
//            Collection<Document> docs = archive.docsForQuery(term, qo);
//            Date fd = ((EmailDocument) docs.iterator().next()).getDate();
//            Date ld = fd;
//            Iterator it = docs.iterator();
//            while (it.hasNext())
//                ld = ((EmailDocument) it.next()).getDate();
//            System.err.println("Fd: "+fd+" ::: LD: "+ld);
//            System.err.println("Fd ms: "+fd.getTime()+", Ld ms: "+ld.getTime());
//            System.err.println("Months: "+((ld.getTime()-fd.getTime())/(3600.0*1000*24*30)));
            //new ListEvaluator();
//            System.err.println("Done!");
            String test = "absorb, accept, admit, affirm, analyze, appreciate, assume, convinced of, believe, consider,  decide,  dislike, doubt, dream, dream up,  expect, fail, fall for, fancy , fathom, feature , feel, find, foresee , forget, forgive, gather, get, get the idea, get the picture, grasp, guess, hate, have a hunch, have faith in, have no doubt, hold, hypothesize, ignore, image , imagine, infer, invent, judge, keep the faith, know, lap up, leave, lose, maintain, make rough guess, misunderstand, neglect, notice, overlook, perceive, place, place confidence in, plan, plan for , ponder, predict, presume, put, put heads together, rack brains, read, realise, realize, reckon, recognize, regard, reject, rely on, remember, rest assured, sense, share, suppose , suspect , swear by, take ,  take at one's word, take for granted, think, trust, understand, vision , visualize , wonder";
            String[] toks = "Thank you for accepting to update.Please find the updated CV attached.".toLowerCase().split("\\s+");
            String[] nbrs = ListEvaluator.getNeighboursOfPronouns(toks);
            for(String nbr: nbrs)
                System.err.println(nbr);
            //ce.computeScore();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
