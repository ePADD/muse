package edu.stanford.muse.ner.segmentation;

import com.google.gson.Gson;
import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.ner.featuregen.FeatureUtils;
import edu.stanford.muse.ner.featuregen.FeatureVector;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
/**
 * Created by viharipiratla on 22/05/15.
 *
 * Finds an optimisation expression over segmentation features for proper segmentation of names*/
public class WordSegmenter {
    static Log log					= LogFactory.getLog(WordSegmenter.class);
    public static class SegmentationModel{
        public double[] coeffs;
        static String modelFileName = "segmentationModel.json";
        //for gson loading
        public SegmentationModel(){}

        public SegmentationModel(double[] coeffs){
            this.coeffs = coeffs;
        }

        public Pair<String,String> segment(String cicname, FeatureUtils wfs, svm_model svmModel){
            String bestName = null, debugStr = "";
            double maxVal = -1;
            Set<String> substrs = IndexUtils.computeAllSubstrings(cicname);
            outer:
            for(String substr: substrs) {
                String[] words = substr.split("\\s+");
                if(words.length == 1 && DictUtils.commonDictWords.contains(words[0].toLowerCase()))
                    continue;
                String fw = words[0].toLowerCase();
                String ew = null;
                if(words.length>1)
                    ew = words[words.length-1].toLowerCase();
                String[] stopWords = Util.stopwords;
                for (String stopWord : stopWords) {
                    if((!fw.equals("the") && stopWord.equals(fw))||stopWord.equals(ew))
                        continue outer;
                }

                double[] fs = SegmentFeatures.genarateFeatures(substr, wfs, svmModel);
                if(fs[0]>=0.5) {
                    double v = evaluate(fs,coeffs);
                    //System.err.println(v + " ::: " + Arrays.toString(fs)+" ::: "+Arrays.toString(coeffs));
                    if(v>maxVal) {
                        bestName = substr;
                        maxVal = v;
                    }
                }
                debugStr += substr+" "+Arrays.toString(fs)+"\n";
            }
//            if(maxVal>0) {
//                System.err.println("Phrase: " + cicname + ", name: " + bestName);
//            }
            if(bestName!=null)
                bestName = bestName.replaceAll(SegmentFeatures.MARKERS_PATT, "");
            return new Pair<String,String> (bestName,debugStr);
        }

        public void saveModel(String fullPath, String type) {
            try {
                FileWriter fw = new FileWriter(new File(fullPath+File.separator+type+"_"+modelFileName));
                Gson gson = new Gson();
                String json = gson.toJson(this);
                fw.write(json);
                fw.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    //function that is optimised
    static double evaluate(double[] features, double[] coeffs){
        if(features==null||coeffs==null)
            return -1;
        double v = 0;
        v = features[2]*100 + features[0]*10 + features[1];
//        v += features[0] * coeffs[0];
//        v += features[1] * coeffs[1];
//        v += features[2] * coeffs[2];
//        //v *= features[1];
        return v;
    }

    public static class SegmentationFunction implements MultivariateFunction {
        //params \mu1, \mu2, \mu3 in x1*svm_confidence + x2*number_of_documents(phrase) + x3*exp(length_of_phrase)
        svm_model svmModel;
        FeatureUtils wfs;
        //features -> target value
        List<Pair<List<double[]>,Integer>> in;
        double[] means ;
        List<Pair<String,String>> tdata;

        public SegmentationFunction(svm_model svmModel, FeatureUtils wfs){
            this.svmModel = svmModel;
            this.wfs = wfs;
            this.in = new ArrayList<Pair<List<double[]>,Integer>>();
            this.tdata = new ArrayList<Pair<String,String>>();
            this.means = new double[3];
        }

        //chose the model such that the difference between the score of confident phrase differs max from other variants of this phrase [i.e. substrs]
        //optimise equation \summation [sc-si]
        //@param The cic phrase extracted from the doc and the match found in one of the gazettes
        public void add(String phrase, String match){
            Set<String> substrs = IndexUtils.computeAllSubstrings(phrase);
            int idx = 0, tgt = -1;
            List<double[]> features = new ArrayList<double[]>();
            double[] pmeans = new double[]{0,0,0};
            for(String substr: substrs){
                if(tgt<0 && substr.equals(match)) {
                    tgt = idx;
                }

                double[] fs = SegmentFeatures.genarateFeatures(substr, wfs, svmModel);
                for(int fi=0;fi<fs.length;fi++)
                    pmeans[fi] += fs[fi];
                features.add(fs);
                idx++;
            }
            if(tgt<0)
                System.err.println ("Phrase: "+phrase+" sub-string of "+match+"?");
            else{
                in.add(new Pair<List<double[]>,Integer>(features,tgt));
                tdata.add(new Pair<String,String>(phrase, match));
                for(int fi=0;fi<pmeans.length;fi++)
                    means[fi] += pmeans[fi]/substrs.size();
            }
        }

        //given a data point, return the value that is part of objective function
        //if implementing MultivariateFunction, the point that this gets are actually variables that are being optimised
        public double value(double[] vars){
            double val = 0;
            for(int ii=0;ii<in.size();ii++) {
                List<double[]> fs = in.get(ii).first;
                int tgt = in.get(ii).second;
                if (fs.size() <= tgt || tgt < 0) {
                    System.err.println("Continuing for " + tgt+", "+fs.size());
                    continue;
                }
                double[] tgtf = fs.get(tgt);
                double tgtval = evaluate(tgtf, vars);
                //value is the index with max value
                int max_index = -1;
                //value for this point
                double vpoint = 0;
                double max_value = Double.MIN_VALUE;

                for (int fi = 0;fi<fs.size();fi++) {
                    double[] f = fs.get(fi);
                    double e = evaluate(f, vars);

                    vpoint += tgtval - e;
                    if (e > max_value) {
                        max_value = e;
                        max_index = fi;
                    }
            //        System.err.println(ii+" eval for: "+Arrays.toString(f)+", "+e+", "+Arrays.toString(vars)+", tgt: "+in.get(ii).second);
                }
              //  System.err.println("Max idx: " + max_index + ", tgt:" + in.get(ii).second);
                //normalise
//                if(max_value!=0)
//                    vpoint /= max_value;
                if(max_index == in.get(ii).second)
                    vpoint = 1;
                else
                    vpoint = 0;
                val += vpoint;

            }
            System.err.println(val+", for: "+Arrays.toString(vars));
            return val;
        }

        public void test(SegmentationModel smodel){
            System.out.println("The trained model misses these");
            int missed = 0;
            for (Pair<String,String> tinst: tdata) {
                String phrase = tinst.getFirst();
                String match = tinst.getSecond();
                Pair<String,String> p = smodel.segment(phrase, this.wfs, this.svmModel);
                String sp = p.first;
                if (match!=null)
                    match =  match.replaceAll("^([Dd]ear|[Hh]i|[hH]ello|[Mm]r|[Mm]rs|[Mm]iss|[Ss]ir|[Mm]adam|[Dd]r\\.?|[Pp]rof\\.?)\\W+", "");
                if (sp!=null)
                    sp = sp.replaceAll("^([Dd]ear|[Hh]i|[hH]ello|[Mm]r|[Mm]rs|[Mm]iss|[Ss]ir|[Mm]adam|[Dd]r\\.?|[Pp]rof\\.?)\\W+", "");
                if (match != null)
                    if (!match.equals(sp)) {
                        missed++;
                        System.out.println("Segmented phrase: " + sp + " for " + phrase + ", expected: " + match);
                    }
            }
            System.out.println("Missed: "+missed+" of "+tdata.size());
        }

        //train on the data collected and return status of the optimisation, like convergence
        public double[] train(){
            BOBYQAOptimizer optim = new BOBYQAOptimizer(2*3+1);
            PointValuePair result = optim.optimize(new MaxEval(100), new ObjectiveFunction(this), GoalType.MAXIMIZE, new SimpleBounds(new double[]{0,0,0},new double[]{1,1,1}), new InitialGuess(new double[]{1.0, 1.0, 1.0}));
            double[] params = result.getPoint();
            double sum = 0;
            for(int p=0;p<params.length;p++)
                sum += params[p];
            for(int p=0;p<params.length;p++)
                params[p] /= sum;

            log.info("Params: " + Arrays.toString(params) + ", sum: " + sum);
            log.info("Value: " + result.getValue());
            System.out.println("Params: " + Arrays.toString(params)+", sum: "+sum);
            System.out.println("Value: " + result.getValue());
            test(new SegmentationModel(params));
            return params;
        }
    }

    @Deprecated
    public WordSegmenter(Set<Map<String,String>> gazettes, FeatureUtils wfs, svm_model model){
        int MAX_NAMES = Integer.MAX_VALUE;
        List<double[]> xL = new ArrayList<double[]>();
        List<Double> yL = new ArrayList<Double>();
        //sample few names randomly from many CIC names
        int i=0,pi=0,ni=0;
        for(String cicname: wfs.counts.keySet()){
            //TODO: review the next line
            FeatureVector wfv = wfs.getVector(cicname, FeatureUtils.PERSON);
            svm_node[] sx = wfv.getSVMNode();
            double v = svm.svm_predict(model,sx);
            if(v>0) {
                //present in any gazette
                boolean ping = false;
                for (Map<String, String> g : gazettes)
                    if (g.containsKey(cicname))
                        ping = true;
                if(ping) {
                    xL.add(SegmentFeatures.genarateFeatures(cicname,wfs,model));
                    yL.add(1.0);
                    pi++;
                    i++;
                }
                else{
                    String[] words = cicname.split("\\s+");
                    boolean containsDict = false;
                    for(String w: words)
                        if(DictUtils.commonDictWords.contains(w.toLowerCase())) {
                            containsDict = true;
                            break;
                        }
                    //TODO: this would only work for person name types, come up with something else for orgs and places
                    if(containsDict) {
                        xL.add(SegmentFeatures.genarateFeatures(cicname,wfs,model));
                        yL.add(0.0);
                        ni++;
                        i++;
                    }

                }
            }
            if(i>MAX_NAMES)
                break;
        }
        System.err.println("Training data contains "+ni+" negative and "+pi+" postive samples");

        double[][] x = xL.toArray(new double[xL.size()][]);
        double[] y = new double[xL.size()];
        int yi = 0;
        for(double ys: yL)
            y[yi++] = ys;

//        RealMatrix rm = MatrixUtils.createRealMatrix(x);
//        RealMatrix rmt = rm.transpose();
//        double[][] cov = new Covariance(rmt.getData()).getCovarianceMatrix().getData();
        for(int ii=0;ii<y.length;ii++) {
            String str = "";
            str += y[ii]+" -- ";
            for (int j = 0; j < x[ii].length; j++)
                str += x[ii][j] + " ";
            System.err.println(str);
        }
        //GLS estimation is throwing exception that the matrix(cov? x?) is singular
//        OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
//        reg.newSampleData(y,x);
//        reg.setNoIntercept(false);
//        double[] regparams = reg.estimateRegressionParameters();
//
//        for(int ri=0;ri<regparams.length;ri++)
//            System.err.println(ri+" : "+regparams[ri]);
//
//        NeuralNetwork perceptron = new Perceptron(SegmentFeatures.NUM_FEATURES,1);
//        //MultiLayerPerceptron perceptron = new MultiLayerPerceptron(TransferFunctionType.GAUSSIAN, 4,10,1);
//        DataSet data = new DataSet(SegmentFeatures.NUM_FEATURES,1);
//        for(int xi=0;xi<x.length;xi++)
//            data.addRow(new DataSetRow(x[xi],new double[]{y[xi]}));
//
//        perceptron.learn(data);
//        perceptron.stopLearning();
//        Double[] wts = perceptron.getWeights();
//        System.err.println("Params: ");
//        for(int wi=0;wi<wts.length;wi++)
//            System.err.println(wi+" : "+wts[wi]);
//
//        int ti = 0;
//        for(String lname: wfs.counts.keySet()) {
//            WordFeatureVector wfv = new WordFeatureVector(WordSurfaceFeature.compute(lname, wfs), wfs);
//            double v = svm.svm_predict(model,wfv.getSVMNode());
//            if(v>0) {
//                segment(lname, perceptron, gazettes, wfs, model);
//                if (++ti > 100)
//                    break;
//            }
//        }
    }

//    public static String segment(String lname,NeuralNetwork perceptron, Set<Map<String,String>> gazettes, WordFeatures wfs, svm_model model){
//        //all word level substrings
//        Set<String> substrs = IndexUtils.computeAllSubstrings(lname);
//       // Map<String, Double> out = new LinkedHashMap<String,Double>();
//        for(String substr: substrs) {
//            double[] vec = SegmentFeatures.genarateFeatures(substr, gazettes, wfs, model);
//            perceptron.setInput(vec);
//            perceptron.calculate();
//            double[] vals = perceptron.getOutput();
//            double val = vals[0];
//            System.err.println(substr+" : "+val+" -- "+Arrays.toString(vec));
//        }
//        return "some";
//    }

    public static SegmentationModel train(svm_model svmModel, FeatureUtils wfs, Set<Map<String,String>> gazzs){
        int numGood = 0;
        SegmentationFunction sfunction = new SegmentationFunction(svmModel, wfs);
        List<String> markers = Arrays.asList("dear","hi","hello","mr","mrs","miss","sir","madam","dr.","prof");
        String[] aTypes = FeatureUtils.aTypes.get(FeatureUtils.PERSON);
        //iterate over cicnames to find names that are super strings of names in gazettes
        for(String cicname: wfs.counts.keySet()){
            cicname = IndexUtils.stripExtraSpaces(cicname);

            //sort in decreasing order of length
            //dont supply phrase with marker as confident target, that leaves the optimeser confused and recognises 2408/2899 correct, in contrast to 2806/2899 correct when a plain phrase is supplied as target
            //TODO: This behaviour is probably a bug as phrases with markers are supposed to have a higher probability?
            List<String> substrs = IndexUtils.computeAllSubstrings(cicname, true);
            //useless to consider
            if(substrs.size()>50)
                continue;
            boolean good = false;
            String confName = null;
            outer:
            for(String substr: substrs){
                //if is single word, atleast check for length; so that strings like P, AB are not matched
                if(substr.contains(" ")|| substr.length()>2) {
                    //String cn = substr.replaceAll("^([Dd]ear|[Hh]i|[hH]ello|[Mm]r|[Mm]rs|[Mm]iss|[Ss]ir|[Mm]adam)\\W+", "");
                    for (Map<String, String> gazz : gazzs) {
                        if(gazz.containsKey(substr))
                            for (String aType : aTypes)
                                if (aType.contains(gazz.get(substr))) {
                                    good = true;
                                    confName = substr;
                                    break outer;
                                }
                    }
                }
            }

            if(good){
                numGood++;
                sfunction.add(cicname, confName);
            }
        }
        for(int fi=0;fi<sfunction.means.length;fi++)
            sfunction.means[fi] /= numGood;

        System.err.println("Found: "+numGood+" variations of confident names");
       return new SegmentationModel(sfunction.train());
    }

    //tries to build a model if the model is not found in the specified loacation
    public static SegmentationModel loadModel(String fullPath,String type, svm_model svmModel, FeatureUtils wfs, Set<Map<String,String>> gazzs){
        try{
            FileReader fr = new FileReader(new File(fullPath+File.separator+type+"_"+SegmentationModel.modelFileName));
            Gson gson = new Gson();
            SegmentationModel sm = gson.fromJson(fr, SegmentationModel.class);
            fr.close();
            return sm;
        }catch (Exception e){
            log.warn("Did not find the model file at "+fullPath+File.separator+SegmentationModel.modelFileName);
            System.err.println("Did not find the model file at " + fullPath + File.separator + SegmentationModel.modelFileName);
            e.printStackTrace();
        }
        //if not found, try to build one.
        SegmentationModel smodel = train(svmModel, wfs, gazzs);
        smodel.saveModel(fullPath,type);
        return smodel;
    }

    public static void main(String[] args){
    }
}
