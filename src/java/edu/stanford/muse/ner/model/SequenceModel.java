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

    public double score(String phrase, Short type) {
        //if contains "The" in the beginning, score it without "The"
        if(phrase.startsWith("The "))
            phrase = phrase.replaceAll("^The ","");
        List<String> commonWords = Arrays.asList("as", "because", "just", "in", "by", "for", "and", "to", "on", "of", "dear", "according", "think", "a", "an", "if", "at", "but", "the", "is");
        double sorg = 0, snon_org = 0;
        //what the candidate starts or ends with is important
        String[] swords = phrase.split("\\s+");
        String fw = swords[0].toLowerCase();
        fw = FeatureDictionary.endClean.matcher(fw).replaceAll("");
        String sw = null;
        if (swords.length > 1) {
            sw = swords[swords.length - 1].toLowerCase();
            sw = FeatureDictionary.endClean.matcher(sw).replaceAll("");
        }
        //the first word should not just be a word of special chars
        if (commonWords.contains(fw) || commonWords.contains(sw) || fw.equals(""))
            return 0.0;

        //String[] patts = FeatureDictionary.getPatts(substr);
        sorg = dictionary.getConditional(phrase, type, true, fdw);
        snon_org = dictionary.getConditional(phrase, type, false, fdw);
        if (fdw != null) {
            try {
                fdw.write("String: " + phrase + " - " + sorg + " " + snon_org + "\n");
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        return sorg-snon_org;
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
                double s = score(substr, type);
                ssubstrs.put(substr, s);
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
                //Pair<String, Double> p = scoreSubstrs(cand.first, type);
                double s = score(cand.first, type);//p.getSecond();
                if (s>0) {
                    maps.get(type).add(cand.getFirst());
                    offsets.add(new Triple<>(cand.getFirst(), cand.getSecond(), cand.getThird()));
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
            String content = "Bob,\n" +
                    "Are you back from Maine: how is your sister?\n" +
                    "One piece of business: the edition from Tamarind Insititute has not\n" +
                    "arrived here. Do you know of some delay, or should I just get on the matter\n" +
                    "my self.\n" +
                    "I think you knew that ND was to publish the Duncan/Levertov letters,\n" +
                    "but when the book got too big they were happy enough not to do it. Last week\n" +
                    "the volume was accepted by the editorial board at Stanford Univ. Press. It\n" +
                    "all comes out as a fine collaboration with Al Gelpi, half the letters here,\n" +
                    "half the letters at Stanford. Out in about a year, so I am told. To replace\n" +
                    "the ND book I have given them another book called \"Robert Duncan's Ezra\n" +
                    "Pound,\" which has both sides of the correspondence in a narrative with other\n" +
                    "docs. Plus poems and unpublished essays. Then a new edtion of Letters, RD\n" +
                    "title, from a small press in St Louis. I've finished the Olson/Duncan\n" +
                    "letters, but am now struggling with the transcriptions of RD lectures of CO.\n" +
                    "They so resist being reading texts. I'll have more on that soon. Letters and\n" +
                    "Lectures to go to Wisconsin. Or, the snow at Christmas kept me off the\n" +
                    "streets and at my desk. In that sense it was a moral snow.\n" +
                    "If you're in Buffalo could you stand a stop after work?\n" +
                    "My best, " +
                    "National Bank some. National Kidney Foundation some . University Commencement";
            Pair<Map<Short,List<String>>, List<Triple<String, Integer, Integer>>> mapsandoffsets = nerModel.find(content);
            for(Short type: mapsandoffsets.first.keySet())
                System.out.println(type + " : "+mapsandoffsets.first.get(type)+"<br>");
            Map<String,String> dbpedia = EmailUtils.readDBpedia();
            FileWriter rfw = new FileWriter(new File(System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator+"cache"+File.separator+"held-out.results"));
            int i=0, j=0;
            double m1 = 0, m2 = 0;
            int n1= 0, n2 = 0;
            double numCorrect = 0,numOrg = 0, numOrgRec = 0;
            for(String str: dbpedia.keySet()){
                if(i++<1000000)
                    continue;
                String type = dbpedia.get(str);
                if(type.endsWith("RadioStation|Broadcaster|Organisation") || type.endsWith("Band|Organisation") || type.endsWith("Album|MusicalWork|Work"))
                    continue;

                boolean allowed = false;
                for(String atype: FeatureDictionary.aTypes.get(FeatureDictionary.ORGANISATION))
                    if(type.endsWith(atype)) {
                        allowed = true;
                        break;
                    }

                //remove, "The" in the beginning before passing
                str = str.replaceAll("^The\\s","");
                Pair<String,Double> p = nerModel.scoreSubstrs(str, FeatureDictionary.ORGANISATION);
                double s = p.getSecond();
                if(allowed) {
                    m1 += s;
                    n1++;
                }else{
                    m2 += s;
                    n2++;
                }
                if(s>0)
                    numOrgRec++;
                if(allowed)
                    numOrg++;
                double v = s*(allowed?1.0:-1.0);
                if((v>0) && (s>0))
                    numCorrect++;

                if(j++>10000)
                    break;
                rfw.write(str+" realtype: "+type+", s:"+s+", result: "+(v>=0?"correct":"wrong")+"\n");
            }
            rfw.close();
            fdw.close();
            double p = numCorrect/numOrgRec;
            double r = numCorrect/numOrg;
            double f1 = 2*p*r/(p+r);
            System.err.println("----------------");
            System.err.println("Positive mean: "+m1/n1+" - "+m1+" - "+n1);
            System.err.println("-ve mean: "+m2/n2+"-"+m2+"-"+n2);
            System.err.println("P:" +p +" - R: "+r+" - F1: "+f1);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
