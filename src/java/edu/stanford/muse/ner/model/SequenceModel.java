package edu.stanford.muse.ner.model;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.featuregen.FeatureGenerator;
import edu.stanford.muse.ner.featuregen.WordSurfaceFeature;
import edu.stanford.muse.ner.tokenizer.CICTokenizer;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.SimpleSessions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;

/**
 * Created by vihari on 07/09/15.
 * This is a Bernoulli Mixture model, every word or pattern is considered a mixture. Does the parameter learning (mu, pi) for every mixture and assigns probabilities to every phrase.
 */
public class SequenceModel implements NERModel, Serializable{
    public FeatureDictionary dictionary;
    public Tokenizer tokenizer;
    public static String modelFileName = "SeqModel.ser";
    private static final long serialVersionUID = 1L;
    static Log log = LogFactory.getLog(SequenceModel.class);
    public static final int MIN_NAME_LENGTH = 3, MAX_NAME_LENGTH = 100;
    public static FileWriter fdw = null;

    public SequenceModel(FeatureDictionary dictionary, Tokenizer tokenizer) {
        this.dictionary = dictionary;
        this.tokenizer = tokenizer;
    }

    public SequenceModel(){

    }

    public Pair<String,Double> scoreSubstrs(String phrase, Short type) {
        if (fdw == null) {
            try {
                fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator + "features.dump"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Pair<String,Double> ret = new Pair<>(phrase, 0.0);
        List<String> commonWords = Arrays.asList("as", "because", "just", "in", "by", "for", "and", "to", "on", "of", "dear", "according", "think", "a", "an", "if", "at", "but", "the", "is");
        Map<String, Double> map = new LinkedHashMap<>();
        //recognises only orgs
        //labels = {O, B, I, E, S} null, beginning, in, end, solo
        //char[] labels = new char[]{'O', 'B', 'I', 'E', 'S'};
        //List<Triple<String,Integer,Integer>> cands = tokenizer.tokenize(content, type.equals(FeatureDictionary.PERSON));
        //for(Triple<String,Integer,Integer> cand: cands) {
        try {
            if (fdw != null)
                fdw.write(phrase + "\n");
            String[] words = phrase.split("\\s+");
            //brute force algorithm, is O(2^n)
            if (words.length > 10) {
                return new Pair<>(phrase, 0.0);
            }

            //look at all the sub strings and select a few with good score
            //if its a single word, we assign S label
            //if its multi word we assign B(I*)E labels.
            //We can afford to do this, because we are just looking inside a CIC pattern
            //In general, a search algorithm to find the subset with max probability should be employed
            Set<String> substrs = IndexUtils.computeAllSubstrings(phrase);
            Map<String, Double> ssubstrs = new LinkedHashMap<>();
            for (String substr : substrs) {
                double sorg = 0, snon_org = 0;
                //what the candidate starts or ends with is important
                String[] swords = substr.split("\\s+");
                String fw = swords[0].toLowerCase();
                fw = FeatureDictionary.endClean.matcher(fw).replaceAll("");
                String sw = null;
                if (swords.length > 1) {
                    sw = swords[swords.length - 1].toLowerCase();
                    sw = FeatureDictionary.endClean.matcher(sw).replaceAll("");
                }
                //should not just be a word of special chars
                if (commonWords.contains(fw) || commonWords.contains(sw) || fw.equals(""))
                    continue;

                //String[] patts = FeatureDictionary.getPatts(substr);
                sorg = dictionary.getConditional(substr, type, true, fdw);
                snon_org = dictionary.getConditional(substr, type, false, fdw);
                if (fdw != null)
                    fdw.write("String: " + substr + " - " + sorg + " " + snon_org + "\n");
                ssubstrs.put(substr, sorg - snon_org);
            }
            List<Pair<String, Double>> sssubstrs = Util.sortMapByValue(ssubstrs);
            for (Pair<String, Double> p : sssubstrs) {
                if (fdw != null)
                    fdw.write(p.getFirst() + " : " + p.getSecond() + "\n");
            }
            if (fdw != null)
                fdw.write("\n");
            if (sssubstrs.size() > 0)
                ret = new Pair<>(sssubstrs.get(0).first, sssubstrs.get(0).getSecond());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    @Override
    public Pair<Map<Short,List<String>>, List<Triple<String, Integer, Integer>>> find (String content){
        Short[] types = new Short[]{FeatureDictionary.PERSON, FeatureDictionary.ORGANISATION, FeatureDictionary.PLACE};

        Map<Short, List<String>> maps = new LinkedHashMap<>();
        List<Triple<String,Integer,Integer>> offsets = new ArrayList<>();
        for(Short t: types)
            maps.put(t, new ArrayList<String>());
        for(int t=0;t<types.length;t++) {
            Short type = types[t];
            List<Triple<String,Integer,Integer>> cands = tokenizer.tokenize(content, type==FeatureDictionary.PERSON);
            for (Triple<String,Integer,Integer> cand : cands) {
                //Double val = allMaps[t].get(cand.getFirst());
                Pair<String,Double> p = scoreSubstrs(cand.first, type);
                if (p.getFirst()!=null && p.getSecond()>0) {
                    maps.get(type).add(p.getFirst());
                    offsets.add(new Triple<>(p.getFirst(), cand.getSecond(), cand.getThird()));
                }
            }
        }
        return new Pair<>(maps,offsets);
    }

    public void writeModel(File modelFile) throws IOException{
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile));
        oos.writeObject(this);
        oos.close();
    }

    public static SequenceModel loadModel(File modelFile) throws IOException{
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FileInputStream(modelFile));
            SequenceModel model = (SequenceModel) ois.readObject();
            ois.close();
            return model;
        } catch (Exception e) {
            Util.print_exception("Exception while trying to load model from: " + modelFile, e, log);
            return null;
        }
    }

    public static SequenceModel train(){
        SequenceModel nerModel = new SequenceModel();
        Map<String,String> dbpedia = EmailUtils.readDBpedia();
        Set<String> fts = new LinkedHashSet<>();
        fts.add(WordSurfaceFeature.WORDS);
        FeatureGenerator[] fgs = new FeatureGenerator[]{new WordSurfaceFeature(fts)};
        FeatureDictionary dictionary = new FeatureDictionary(dbpedia, fgs);
        nerModel.dictionary = dictionary;
        nerModel.tokenizer = new CICTokenizer();
        String mwl = System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator;
        String modelFile = mwl + SequenceModel.modelFileName;
        log.info("Performing EM...");
        nerModel.dictionary.EM(dbpedia);
        try {
            nerModel.writeModel(new File(modelFile));
        }catch(IOException e){
            e.printStackTrace();
        }
        return nerModel;
    }

    public static void main(String[] args){
        try {
            String userDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user-creeley";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            String mwl = System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator;
            String modelFile = mwl + SequenceModel.modelFileName;
            if (fdw == null) {
                try {
                    fdw = new FileWriter(new File(System.getProperty("user.home") + File.separator + "epadd-ner" + File.separator + "cache" + File.separator + "features.dump"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.err.println("Loading model...");
            SequenceModel nerModel = null;
            try{nerModel = SequenceModel.loadModel(new File(modelFile));}
            catch(IOException e){e.printStackTrace();}
            if(nerModel == null)
                nerModel = train();
            List<Document> docs = archive.getAllDocs();
            int di =0;
//            for(Document doc: docs) {
//                String content = archive.getContents(doc, true);
//                System.err.println("Content: "+content);
//                Pair<Map<Short,List<String>>, List<Triple<String,Integer,Integer>>> mapsAndOffsets = nerModel.find(content);
//                for(Short type: mapsAndOffsets.getFirst().keySet())
//                    System.err.println(type +" : "+mapsAndOffsets.getFirst().get(type));
//                if(di++>10)
//                    break;
//            }

            double p = 0;
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
