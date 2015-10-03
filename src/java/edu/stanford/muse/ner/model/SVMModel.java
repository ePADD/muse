package edu.stanford.muse.ner.model;

import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.featuregen.FeatureGenerator;
import edu.stanford.muse.ner.featuregen.FeatureVector;
import edu.stanford.muse.ner.featuregen.WordSurfaceFeature;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;

public class SVMModel implements NERModel, Serializable {
    public FeatureDictionary dictionary;
    public FeatureGenerator[] fgs;
    public Map<Short, svm_model> models;
    public Tokenizer tokenizer;
    public static String modelFileName = "SVMModel.ser";
    private static final long serialVersionUID	= 1L;
    static Log log	= LogFactory.getLog(SVMModel.class);
    public static final int		MIN_NAME_LENGTH		= 3, MAX_NAME_LENGTH = 100;
    public static FileWriter fdw=null;

    public SVMModel(){
        models = new LinkedHashMap<>();
    }

    public Pair<Map<Short, List<String>>, List<Triple<String, Integer, Integer>>> find(String content) {
        //check if the model is initialised
		if (fdw == null) {
			try {
				fdw = new FileWriter(new File(System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator+"cache" + File.separator + "features.dump"));
			} catch (Exception e) {
				;
			}
		}

        List<Triple<String, Integer, Integer>> names = new ArrayList<Triple<String, Integer, Integer>>();

        Map<Short, List<String>> map = new LinkedHashMap<Short, List<String>>();
        for (Short type : models.keySet()) {
            List<String> entities = new ArrayList<String>();

            List<Triple<String, Integer, Integer>> candNames = tokenizer.tokenize(content, type.equals(FeatureDictionary.PERSON));
            for (Triple<String, Integer, Integer> cand : candNames) {
                String name = cand.first;
                String tc = FeatureGeneratorUtil.tokenFeature(name);
                if (name == null || tc.equals("ac"))
                    continue;
                //this could be a good signal for name(occasionally could also be org). The training data (Address book) doesn't contain such pattern, hence probably have to hard code it and I dont want to.
                name = name.replaceAll("'s$", "");
                //stuff b4 colon like subject:, from: ...
                name = name.replaceAll("\\w+:\\W+", "");
                name = name.replaceAll("^\\W+|\\W+$", "");
                //trailing apostrophe

                FeatureVector wfv = dictionary.getVector(name, type);
                svm_node[] sx = wfv.getSVMNode();
                double[] probs = new double[2];
                svm_model svmModel = models.get(type);
                if(sx == null)
                    continue;
                double v = svm.svm_predict_probability(svmModel, sx, probs);
                //log.info("Name: "+name+", predict: "+v+", fv:"+wfv);
                if (v > 0) {
                    //clean before passing for annotation.
                    if(FeatureDictionary.PERSON == type) {
                        Pair<String, Boolean> p1 = WordSurfaceFeature.checkAndStrip(name, FeatureDictionary.startMarkersForType.get(FeatureDictionary.PERSON), true, true);
                        name = p1.getFirst();
                    }
                    if (DictUtils.tabooNames.contains(name.toLowerCase()) || DictUtils.hasOnlyCommonDictWords(name.toLowerCase())) {
                        if (log.isDebugEnabled())
                            log.debug("Skipping entity: " + name);
                        continue;
                    }

                    if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) // drop it
                        continue;

                    names.add(cand);
                    entities.add(name);
                    if (log.isDebugEnabled())
                        log.debug("Found entity name: " + name + ", type: " + type);
                }
                    //TODO: Should comment out these writes in the final version as the dump can get too big and also brings down the performance
				if (fdw != null) {
					try {
                        Map<String,List<String>> temp = FeatureGenerator.generateFeatures(name, null, null, type, dictionary.featureGens);
                        if(temp!=null && temp.get("words")!=null) {
                            String max = null, min = null;
                            double maxs = -1,mins = 2;
                            for(String w: temp.get("words")) {
                                double r;
                                if(dictionary.features.get("words")==null)
                                    r = 0;
                                else {
                                    Pair<Double, Double> p = new Pair<>(0.0,0.0);
                                    FeatureDictionary.MU mu = dictionary.features.get(w);
                                    p.first = mu.getLikelihoodWithType(type)*mu.getFreq();
                                    p.second = mu.getFreq();
                                    r = p.getFirst() / p.getSecond();
                                }
                                maxs = Math.max(r, maxs);
                                mins = Math.min(r, mins);
                                if(maxs == r)
                                    max = w;
                                if(mins == r)
                                    min = w;
                            }
                            fdw.write("name:" + name + ", Type: " + type + ", pred: " + v + ", " + wfv + ":::" + new LinkedHashSet<>() + ":::" + max + ":::" + min + "\n");
                        }
                        else
                            fdw.write("name:" + name + ", Type: " + type + ", pred: " + v + ", " + wfv +"\n");
                        fdw.flush();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
            }
            map.put(type, entities);
        }
        return new Pair<Map<Short, List<String>>, List<Triple<String, Integer, Integer>>>(map, names);
    }

    public static SVMModel loadModel(File modelFile) throws IOException{
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new FileInputStream(modelFile));
            SVMModel model = (SVMModel) ois.readObject();
            ois.close();
            return model;
        } catch (Exception e) {
            Util.print_exception("Exception while trying to load model from: " + modelFile, e, log);
            return null;
        }
    }

    public void writeModel(File modelFile) throws IOException{
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile));
        oos.writeObject(this);
        oos.close();
    }
}
