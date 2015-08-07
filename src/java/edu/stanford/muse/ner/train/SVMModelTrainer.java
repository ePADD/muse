package edu.stanford.muse.ner.train;

import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.ner.NER;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.featuregen.FeatureGenerator;
import edu.stanford.muse.ner.featuregen.FeatureVector;
import edu.stanford.muse.ner.featuregen.WordSurfaceFeature;
import edu.stanford.muse.ner.model.SVMModel;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.ner.util.ArchiveContent;
import edu.stanford.muse.util.*;
import libsvm.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Trains an SVM model with surface features and gazettes
 */
 public class SVMModelTrainer implements NERTrainer, StatusProvider {
    public static class TrainingParam {
        svm_parameter svmParam = new svm_parameter();
        String cacheDir;
        String modelWriteLocation;

        public static TrainingParam getDefaultParam () {
            TrainingParam param = new TrainingParam();
            param.svmParam.svm_type = svm_parameter.C_SVC;
            param.svmParam.kernel_type = svm_parameter.RBF;
            param.svmParam.degree = 3;
            param.svmParam.gamma = 0; // 1/num_features
            param.svmParam.coef0 = 0;
            param.svmParam.nu = 0.5;
            param.svmParam.cache_size = 100;
            param.svmParam.C = 1;
            param.svmParam.eps = 1e-3;
            param.svmParam.p = 0.1;
            param.svmParam.shrinking = 1;
            param.svmParam.probability = 1;
            param.svmParam.nr_weight = 0;
            param.svmParam.weight_label = new int[0];
            param.svmParam.weight = new double[0];
            return param;
        }

        public static TrainingParam initialize(String cacheDir, String mwl){
            TrainingParam tp = getDefaultParam();
            tp.cacheDir = cacheDir;
            tp.modelWriteLocation = mwl;
            return tp;
        }
    }
    String status;
    double pctComplete = 0;
    boolean cancelled;
    public static Log log = LogFactory.getLog(NER.class);

    @Override
    public boolean isCancelled(){
        return cancelled;
    }

    @Override
    public String getStatusMessage(){
        return JSONUtils.getStatusJSON(status, (int) pctComplete, -1, -1);
    }

    @Override
    public void cancel(){
        cancelled = true;
    }

    /**
     * Archive independent way of building models
     * TODO: test this routine*/
    public SVMModel trainArchiveIndependent(Map<String,String> externalGazz, List<Short> types, List<String[]> aTypes,
                          FeatureGenerator[] fgs, Tokenizer tokenizer, Object params){
        TrainingParam tparams = (TrainingParam) params;
        String CACHE_DIR = tparams.cacheDir;
        long st = System.currentTimeMillis(), time = 0;
        SVMModel model = new SVMModel();

        //build a feature dictionary
        FeatureDictionary dictionary = new FeatureDictionary(externalGazz, fgs);
        model.dictionary = dictionary;
        model.tokenizer = tokenizer;
        model.fgs = fgs;
        Map<String, String> hits = externalGazz;
        for (int iti = 0; iti < types.size(); iti++) {
            Short iType = types.get(iti);
            log.info("Training for type: " + iType);
            String[] aType = FeatureDictionary.aTypes.get(iType);
            List<Triple<String, FeatureVector, Integer>> fvs = new ArrayList<Triple<String, FeatureVector, Integer>>();
            int numC = 0;
            for (String h : hits.keySet()) {
                int label = -1;
                String type = hits.get(h);
                //dont add to training
                //if the name is a junk work and has not type, then dont train on it
                if ("notype".equals(type) || FeatureDictionary.ignoreTypes.contains(type))
                    continue;

                for (String at : aType)
                    if (type.endsWith(at))
                        label = 1;

                fvs.add(new Triple<String, FeatureVector, Integer>(h, dictionary.getVector(h, iType), label));
            }
            log.info("Wrote external #" + externalGazz.size());

            FileWriter fw1 = null, fw2 = null;
            if (CACHE_DIR != null) {
                File cdir = new File(CACHE_DIR);
                if (!cdir.exists())
                    cdir.mkdir();

                try {
                    fw1 = new FileWriter(new File(CACHE_DIR + File.separator + iType + "_fvs.train"));
                    fw2 = new FileWriter(new File(CACHE_DIR + File.separator + iType + "_names.train"));
                } catch (Exception e) {
                    log.warn(e);
                }
            }
            svm_problem prob = new svm_problem();
            //svm_node of this line and target
            log.info("Number of feature vectors for training: " + fvs.size());
            prob.l = fvs.size();
            prob.x = new svm_node[prob.l][];
            prob.y = new double[prob.l];
            //for gamma computation.
            int max_dim = -1;
            int i = 0;
            for (Triple<String, FeatureVector, Integer> fv : fvs) {
                prob.x[i] = fv.second.getSVMNode();
                //target
                prob.y[i] = fv.third;
                if (fw1 != null && fw2 != null) {
                    try {
                        fw1.write(fv.third + " " + fv.second.toVector() + "\n");
                        fw2.write(fv.first + "  " + fv.third + " " + fv.second + "\n");
                    } catch (Exception e) {
                    }
                }
                max_dim = Math.max(max_dim, fv.second.NUM_DIM);
                i++;
            }

            svm_parameter param = FeatureDictionary.getDefaultSVMParam();
            param.gamma = 1.0 / max_dim;
            param.probability = 1;
            param.shrinking = 0;

            status = "Learning " + iType + " model...";
            log.info(status);
            svm_print_interface svm_print_logger = new svm_print_interface() {
                public void print(String s) {
                    log.warn(s);
                }
            };
            svm.svm_set_print_string_function(svm_print_logger);

            svm_model svmModel = svm.svm_train(prob, param);

            if (fw1 != null && fw2 != null) {
                try {
                    fw1.close();
                    fw2.close();
                } catch (Exception e) {}
            }
            log.info("Wrote training file to : " + new File(CACHE_DIR + File.separator + iType + ".train").getAbsolutePath());
            status = "Done learning for type: " + iType;
            model.models.put(iType, svmModel);
        }
        time += System.currentTimeMillis() - st;
        st = System.currentTimeMillis();
        status = "Done learning... dumping model";
        log.info("Dumping model for reuse");
        try {
            if (!new File(tparams.modelWriteLocation).exists())
                new File(tparams.modelWriteLocation).mkdir();
            model.writeModel(new File(tparams.modelWriteLocation + File.separator + SVMModel.modelFileName));
        } catch (IOException e) {
            Util.print_exception("Fatal! Could not write the trained model to " + tparams.modelWriteLocation, e, log);
        }
        return model;
    }

    /**
     * @param archiveContent - that which returns the document content, since its not feasible to pass the entire content of archive in an array
     * @param externalGazz - this is corpus independent list of names, like DBpedia titles
     * @param internalGazz - corpus related list of names, like address book
     * external gazette and internal gazette are handled slightly different during training
     * only names that are present in external gazette and also mentioned in the corpus are considered for training
     * all the names in the internal gazette no matter if there are mentioned somewhere in the corpus are considered for training
     * We dont train over all the names in the external gazette, because that is not necessary;
     *                     In the end we just want to be able to classify all the mentions in the documents*/
    @Override
    public SVMModel train(ArchiveContent archiveContent, Map<String,String> externalGazz, Map<String,String> internalGazz, List<Short> types, List<String[]> aTypes,
                   FeatureGenerator[] fgs, Tokenizer tokenizer, Object params) {
        //TODO: put some basic checkups to make sure al the arguments are compatible
        Map<String, String> gazzs = new LinkedHashMap<>();
        gazzs.putAll(externalGazz);
        gazzs.putAll(internalGazz);
        TrainingParam tparams = (TrainingParam) params;
        String CACHE_DIR = tparams.cacheDir;
        long st = System.currentTimeMillis(), time = 0;
        SVMModel model = new SVMModel();

        status = "Reading your address book and DBpedia";
        //build a feature dictionary
        FeatureDictionary dictionary = new FeatureDictionary(gazzs, fgs);
        model.dictionary = dictionary;
        model.tokenizer = tokenizer;
        model.fgs = fgs;
        int numExternal = 0, di = 0, ds = archiveContent.getSize();
        Map<String, String> hits = new LinkedHashMap<String, String>();
        Set<String> considered = new HashSet<String>();
        for (int i = 0; i < ds; i++) {
            String content = archiveContent.getContent(i);
            List<Triple<String, Integer, Integer>> personLikeMentions = tokenizer.tokenize(content, true);
            List<Triple<String, Integer, Integer>> nonPersonLikeMentions = tokenizer.tokenize(content, false);
            List<Triple<String, Integer, Integer>> cands = new ArrayList<Triple<String, Integer, Integer>>();
            cands.addAll(personLikeMentions);
            cands.addAll(nonPersonLikeMentions);
            for (Triple<String, Integer, Integer> cand : cands) {
                String n = cand.getFirst();
                Pair<String, Boolean> cleanname = WordSurfaceFeature.checkAndStrip(n, FeatureDictionary.startMarkersForType.get(FeatureDictionary.PERSON), true, true);
                n = cleanname.getFirst();

                if (considered.contains(n))
                    continue;

                if (gazzs.containsKey(n)) {
                    String type = gazzs.get(n);

                    //if is a single word and is dictionary word, forget about this
                    if (!n.contains(" ") && DictUtils.fullDictWords.contains(n.toLowerCase()))
                        continue;

                    hits.put(n, type);
                    numExternal++;
                } else
                    hits.put(n, "notype");
                considered.add(n);
            }
            if ((++di) % 1000 == 0)
                log.info("Analysed " + di + "/" + ds + " to find known instances");
            pctComplete = 10 + ((double)i/ds)*30;
        }
        for (int iti = 0; iti < types.size(); iti++) {
            Short iType = types.get(iti);
            {
                String type = "";
                if (iType == 0)
                    type = "person";
                else if (iType == 1)
                    type = "place";
                else if (iType == 2)
                    type = "organization";
                status = "Learning to recognise " + type + " names";
            }
            log.info("Training for type: " + iType);
            String[] aType = FeatureDictionary.aTypes.get(iType);
            List<Triple<String, FeatureVector, Integer>> fvs = new ArrayList<Triple<String, FeatureVector, Integer>>();
            int numC = 0;
            for (String h : hits.keySet()) {
                int label = -1;
                String type = hits.get(h);
                //dont add to training
                //if the name is a junk work and has not type, then dont train on it
                if ("notype".equals(type) || FeatureDictionary.ignoreTypes.contains(type))
                    continue;

                for (String at : aType)
                    if (type.endsWith(at))
                        label = 1;

                fvs.add(new Triple<String, FeatureVector, Integer>(h, dictionary.getVector(h, iType), label));
            }
            if (iType == FeatureDictionary.PERSON) {
                log.info("Adding from internal gazette #" + internalGazz.size());

                for (String cname : internalGazz.keySet()) {
                    numC++;
                    fvs.add(new Triple<String, FeatureVector, Integer>(cname, dictionary.getVector(cname, iType), 1));
                    //for corporate and locations, we cannot use addressbook for positive examples. hence low accuracy
                    //The stats after dumping addressbook and before respectively are:
                    //Accuracy:0.6170212765957447, Recall:0.675
                    //Accuracy:0.59, Recall:0.75
                }
            }

            //try to equalize number of vectors of each class.
            int x = 0;
            int hi=0, total = hits.size();
            log.info("Adding some dummy names to balance the address book");
            for (String h : hits.keySet()) {
                if (x > numC)
                    break;
                String type = hits.get(h);
                if ((type != null && !type.equals("notype")))
                    continue;

                String[] tokens = h.split("\\s+");
                int num = 0;
                for (String t : tokens)
                    if (DictUtils.fullDictWords.contains(t.toLowerCase()) || Indexer.MUSE_STOP_WORDS_SET.contains(t.toLowerCase()))
                        num++;
                double maxp = -1;
                maxp = dictionary.getMaxpfreq(h, iType);

                //add to training sequence only if all the words are dictionary words or the max chance of having a person name is less than 0.25.
                //maxp is set to -1 if it cannot find any of the tokens in the gazette list
                boolean nonName = false;
                //works for only person names
                if (num == tokens.length) {
                    nonName = true;
                    if (maxp > 0.3)
                        nonName = false;
                }

                if (maxp < 0.2 && maxp > 0)
                    nonName = true;
                if (nonName) {
                    fvs.add(new Triple<String, FeatureVector, Integer>(h, dictionary.getVector(h, iType), -1));
                    //log.warn("Adding: " + h + ", number-of-words-in-dictionary/total-number-of-words: (" + num + "/" + tokens.length + "), maximum chance of being a name: " + maxp);
                    x++;
                }
                hi++;
                pctComplete += ((double)hi/total)*20;
            }
            log.info("Wrote external #" + numExternal + ", internal #" + numC + ", to balance: " + x);

            FileWriter fw1 = null, fw2 = null;
            if (CACHE_DIR != null) {
                File cdir = new File(CACHE_DIR);
                if (!cdir.exists())
                    cdir.mkdir();

                try {
                    fw1 = new FileWriter(new File(CACHE_DIR + File.separator + iType + "_fvs.train"));
                    fw2 = new FileWriter(new File(CACHE_DIR + File.separator + iType + "_names.train"));
                } catch (Exception e) {
                    log.warn(e);
                }
            }
            svm_problem prob = new svm_problem();
            //svm_node of this line and target
            log.info("Number of feature vectors for training: " + fvs.size());
            prob.l = fvs.size();
            prob.x = new svm_node[prob.l][];
            prob.y = new double[prob.l];
            //for gamma computation.
            int max_dim = -1;
            int i = 0;
            for (Triple<String, FeatureVector, Integer> fv : fvs) {
                prob.x[i] = fv.second.getSVMNode();
                //target
                prob.y[i] = fv.third;
                if (fw1 != null && fw2 != null) {
                    try {
                        fw1.write(fv.third + " " + fv.second.toVector() + "\n");
                        fw2.write(fv.first + "  " + fv.third + " " + fv.second + "\n");
                    } catch (Exception e) {
                    }
                }
                max_dim = Math.max(max_dim, fv.second.NUM_DIM);
                i++;
            }

            svm_parameter param = FeatureDictionary.getDefaultSVMParam();
            param.gamma = 1.0 / max_dim;
            param.probability = 1;
            param.shrinking = 0;

            log.info(status);
            svm_print_interface svm_print_logger = new svm_print_interface() {
                public void print(String s) {
                    log.warn(s);
                }
            };
            svm.svm_set_print_string_function(svm_print_logger);

            svm_model svmModel = svm.svm_train(prob, param);

            if (fw1 != null && fw2 != null) {
                try {
                    fw1.close();
                    fw2.close();
                } catch (Exception e) {}
            }
            log.info("Wrote training file to : " + new File(CACHE_DIR + File.separator + iType + ".train").getAbsolutePath());
            status = "Done learning for type: " + iType;
            model.models.put(iType, svmModel);
            pctComplete = 40 + (iType+1)*20;
        }
        time += System.currentTimeMillis() - st;
        st = System.currentTimeMillis();
        status = "Done learning... dumping model";
        log.info("Dumping model for reuse");
        try {
            File f = new File(tparams.modelWriteLocation);
            //when f be null?
            if(f != null && !f.exists())
                f.mkdir();

            model.writeModel(new File(tparams.modelWriteLocation + File.separator + SVMModel.modelFileName));
        } catch (IOException e) {
            Util.print_exception("Fatal! Could not write the trained model to " + tparams.modelWriteLocation, e, log);
        }
        return model;
    }
}
