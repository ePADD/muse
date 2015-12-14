package edu.stanford.muse.xword;

import com.google.common.collect.Sets;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Created by vihari on 11/12/15.
 * Note: Though the classes are called ClueEvaluators, they actually evaluate: Answer + Clue
 * for example: if an NER Model can recognise a answer as entity in clue or if the answer has been mentioned after a long period
 */
public class ClueEvaluator {
    public static Log log						= LogFactory.getLog(ClueEvaluator.class);
    public static List<ClueEvaluator> getDefaultEvaluators(){
        List<ClueEvaluator> evals = new ArrayList<>();
        evals = Arrays.asList(new LengthEvaluator(), new EmotionEvaluator(), new DirtEvaluator(), new NamesEvaluator(), new ListEvaluator(),new EmailDocumentEvaluator());
        return evals;
    }

    public double computeScore(double score, Clue clue, String answer, Set<String> tabooNames, NERModel nerModel){
        return score;
    }

    public double computeScore(double score, Clue clue, String answer, Set<String> tabooNames, NERModel nerModel, Archive archive){
        //if not overloaded then pass it to the method that does not take the archive argument
        return computeScore(score, clue, answer, tabooNames, nerModel);
    }

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
        public double computeScore(double score, Clue clue, String answer, Set<String> tabooNames, NERModel nerModel){
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
            return score+lengthBoost;
        }
    }

    public static class EmotionEvaluator extends ClueEvaluator{
        float[] params;
        /**Required param to weight the number of exclamations and smileys respectively in the sentence
         * default 7.0f, 7.0f*/
        public EmotionEvaluator(float[] params){
            this();
            if(params==null || params.length!=2){
                log.error("Wrong initialisation of params in "+EmotionEvaluator.class+"!! Required 2 params");
            }else {
                this.params = params;
            }
        }
        public EmotionEvaluator(){
            this.params = new float[]{7.0f,7.0f};
        }

        @Override
        public double computeScore(double score, Clue clue, String answer, Set<String> tabooNames, NERModel nerModel) {
            String s = clue.getFullSentenceOriginal().toLowerCase();
            // prefer exclamations, highly memorable
            float exclamationScore = (s.indexOf("!") >= 0) ? params[0] : 0.0f;
            clue.clueStats.exclamationScore = exclamationScore;

            // good if it has emoticons
            int nSmileys = 0;
            for (String smiley : ArchiveCluer.smileys)
                if (s.indexOf(smiley) >= 0) {
                    nSmileys++;
                }
            float smileyScore = params[1] * nSmileys;

            clue.clueStats.nSmileys = nSmileys;
            clue.clueStats.smileyScore = smileyScore;
            //the original code for somereason adds one to the score, just retaining that here.
            return score + 1.0f + exclamationScore + smileyScore;
        }
    }

    /**
     * Penalises any dirty words in the clue
     * */
    public static class DirtEvaluator extends ClueEvaluator {
        float[] params;

        /**
         * Required param to weight the number of bad names in the sentence
         * default: -20.0f
         */
        public DirtEvaluator(float[] params) {
            this();
            if (params == null || params.length != 1) {
                log.error("Wrong initialisation of params in " + DirtEvaluator.class + "!! Required 1 param");
            }else {
                this.params = params;
            }
        }

        public DirtEvaluator(){
            this.params = new float[]{-20.0f};
        }

        @Override
        public double computeScore(double score, Clue clue, String answer, Set<String> tabooNames, NERModel nerModel) {
            String s = clue.getFullSentenceOriginal().toLowerCase();
            for (String badName : tabooNames) { //drop own name, as well as other terms that may be overused in the xword/memorystudy.
                if (s.contains(badName)) {
                    score += params[0];
                    clue.clueStats.containsBadName = true;
                    break;
                }
            }
            return score;
        }
    }

    /**
     * Penalises the existence of any non-specific words
     * */
    public static class SpecificityEvaluator extends ClueEvaluator {
        float[] params;

        /**
         * Required param to penalise sentences starting with non-specific words such as <i>this, that, however</i>
         * default: -10.0f
         */
        public SpecificityEvaluator(float[] params) {
            this();
            if (params == null || params.length != 1) {
                log.error("Wrong initialisation of params in " + SpecificityEvaluator.class + "!! Required 1 param");
            } else {
                this.params = params;
            }
        }

        public SpecificityEvaluator(){
            this.params = new float[]{-10.0f};
        }

        @Override
        public double computeScore(double score, Clue clue, String answer, Set<String> tabooNames, NERModel nerModel) {
            String s = clue.getFullSentenceOriginal().toLowerCase();
            if (s.startsWith("this") || s.startsWith("that") || s.startsWith("however")) {
                clue.clueStats.containsNonSpecificWords = true;
                score += params[0]; // non-specific, depends on context
            }
            return score;
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
            if (params == null || params.length != 2) {
                log.error("Wrong initialisation of params in " + NamesEvaluator.class + "!! Required 2 param");
            } else {
                this.params = params;
            }
        }

        public NamesEvaluator(){
            this.params = new float[]{2.0f, -20.0f};
        }

        @Override
        public double computeScore(double score, Clue clue, String answer, Set<String> tabooNames, NERModel nerModel) {
            String sOrig = clue.getFullSentenceOriginal();
            String canonicalizedanswer = (Util.canonicalizeSpaces(answer)).toLowerCase();
            List<String> names = new ArrayList<>();
            double CUTOFF = 0.001;
            if (nerModel != null) {
                log.info("Identifying names in the content");
                Pair<Map<Short, Map<String,Double>>, List<Triple<String, Integer, Integer>>> mapAndOffsets = nerModel.find(sOrig);
                Map<Short, Map<String,Double>> map = mapAndOffsets.first;
                log.info("Found: " + mapAndOffsets.getSecond().size() + " names in sentences: " + sOrig);
                for (short x : map.keySet()) {
                    //if(map.get(x).)
                    //log.info(x + ":" + map.get(x));
                    for(String e: map.get(x).keySet())
                        if(map.get(x).get(e)>CUTOFF)
                            names.add(e);
                }
            }
            float namesScore = params[0]*names.size();
            score += namesScore;

            boolean found = false;
            for (String name : names) { //check that NER detects same name in text as the name of the answer (eg. removes instances where NER returns "San Jose" from clue, but answer is "San")
                if (name.equals(canonicalizedanswer)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                score += params[1];
                clue.clueStats.nameNotInClue = true;
            }

            clue.clueStats.namesInClue = names.size();
            clue.clueStats.clueLength = clue.getFullSentenceOriginal().length();

            clue.clueStats.namesScore = namesScore;
            return score;
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
         */
        public ListEvaluator(float[] params, List<String[]> lists) {
            this();
            if (params==null || lists==null || params.length==0 || params.length!=lists.size()) {
                log.error("Wrong/improper initialisation of params in " + ListEvaluator.class + "!! Required "+lists.size()+" params");
            } else {
                this.params = params;
                this.lists = new ArrayList<>();
                String str = "";
                for(String[] lst: lists) {
                    this.lists.add(Sets.newHashSet(lst));
                    str += "Lst: "+Sets.newHashSet(lst)+"\n";
                }

                log.info("List evaluator initialised with "+this.lists.size()+" lists - "+str);
            }
        }

        public ListEvaluator() {
            lists = Arrays.asList(
                    (Set<String>)Sets.newHashSet("from", "to", "in", "at", "as", "by", "inside", "like", "of", "towards", "toward", "via", "such as", "called", "named", "name"),
                    (Set<String>)Sets.newHashSet("flight", "travel", "city", "town", "visit", "arrive", "arriving", "land", "landing", "reach", "reaching", "train", "road", "bus", "college", "theatre", "restaurant", "book", "film", "movie", "play", "song", "writer", "artist", "author", "singer", "actor", "school")
            );
            this.params = new float[]{10.0f, 5.0f};
        }

        @Override
        public double computeScore(double score, Clue clue, String answer, Set<String> tabooNames, NERModel nerModel) {
            String s = clue.getFullSentenceOriginal().toLowerCase();
            String[] tokens = s.split("[\\s\\n]+");
            float boost = 0;
            for(int i=0;i<params.length;i++){
                float b = 0;
                for(String tok: tokens) {
                    tok = tok.replaceAll("^\\W+|\\W+$","");
                    //log.info("New Lst:"+i+" contains "+tok+"? - "+lists.get(i).contains(tok)+", "+lists.get(i));
                    b += lists.get(i).contains(tok) ? params[i] : 0.0f;
                }
                boost += b;
                if(i == 1) clue.clueStats.prepositionScore = b;
                else clue.clueStats.sigWordScore = b;
            }
            return score + boost;
        }
    }

    /**
     * Evaluates clue based on the email message the clue is fetched from*/
    public static class EmailDocumentEvaluator extends ClueEvaluator {
        float[] params;

        /**
         * requires params to weigh these features
         * <ol>
         *     <li>clues for every other month mentioned score+[this parameter]*frequency of the answer*Number of months between the last mention and the latest</li>
         *     <li>penalising thread length: score+[this parameter]*(number of sent mails in the thread/thread size)</li>
         * </ol>
         * default: [-1.0, 5.0]
         */
        public EmailDocumentEvaluator(float[] params) {
            this();
            if (params == null || params.length != 2) {
                log.error("Wrong/improper initialisation of params in " + EmailDocumentEvaluator.class + "!! Required " + 2 + " params");
            } else {
                this.params = params;
            }
        }

        public EmailDocumentEvaluator(){
            params = new float[]{-1.0f,5.0f};
        }

        @Override
        public double computeScore(double score, Clue clue, String answer, Set<String> tabooNames, NERModel nerModel, Archive archive) {
            if(archive!=null) {
                //String s = clue.getFullSentenceOriginal();
                EmailDocument ed = clue.d;
                Indexer.QueryOptions qo = new Indexer.QueryOptions();
                qo.setSortBy(Indexer.SortBy.CHRONOLOGICAL_ORDER);
                String term = "\"" + answer + "\"";
                log.info("Searching for: "+term);
                Collection<Document> docs = archive.docsForQuery(term, qo);
                if (docs.size() == 0)
                    log.error("Something is not right! Did not find: " + answer + " in the index!!");
                else if (docs.size() == 1) {
                    log.info("Only one doc with the mention of " + answer);
                    clue.clueStats.timeAnswerScore = 0;
                } else if (docs.size() >= 2) {
                    Date fd = ((EmailDocument) docs.iterator().next()).getDate();
                    Date ld = fd;
                    Iterator it = docs.iterator();
                    while (it.hasNext())
                        ld = ((EmailDocument) it.next()).getDate();

                    Calendar fc = Calendar.getInstance(), lc = Calendar.getInstance();
                    fc.setTime(fd);
                    lc.setTime(ld);

                    float timeScore = params[0] * docs.size() * (lc.get(Calendar.MONTH) - fc.get(Calendar.MONTH) + 12 * (lc.get(Calendar.YEAR) - fc.get(Calendar.YEAR)));
                    clue.clueStats.timeAnswerScore = timeScore;
                    score += timeScore;
                }

                //thread score
                //proportional to number of sent mails in the thread
                log.info("Looking for docs with thread Id: "+ed.threadID);
                long st = System.currentTimeMillis();
                List<Document> threads = archive.docsWithThreadId(ed.threadID);
                log.info("Done looking for docs with thread Id: "+ed.threadID+" in "+(System.currentTimeMillis()-st));
                score += params[1] * threads.size();
                clue.clueStats.noisyThreadScore = params[1] * threads.size();
            }else{
                log.error("The archive param supplied to "+EmailDocument.class+" is null!!");
            }
            return score;
        }
    }

    public static void main(String[] args){
        List<String[]> lists = new ArrayList<>();
        lists.add(new String[]{"from", "to", "in", "at", "as", "by", "inside", "like", "of", "towards", "toward", "via", "such as", "called", "named", "name", "can"});
        lists.add(new String[]{"df","jdfg"});
        float[] params = new float[]{1.0f,1.0f};
        Clue clue = new Clue("Is this the address: _________________________________, Plot No: 8, Srinilaya, Radha krishna nagar, Dharwad, Karnataka 580003 can you please confirm.Also, can you please provide your mobile number.Thanks", "Nandanavana Bansuri Music Academy");
        clue.setFullSentenceOriginal("Is this the address: Nandanavana Bansuri Music Academy, Plot No: 8, Srinilaya, Radha krishna nagar, Dharwad, Karnataka 580003 can you please confirm.Also, can you please provide your mobile number.Thanks");
        ClueEvaluator eval = new ListEvaluator(params, lists);
        double score = 0;
        score = eval.computeScore(score,clue,"yes", null,null,null);
        System.err.println("Score: "+score);
    }
}
