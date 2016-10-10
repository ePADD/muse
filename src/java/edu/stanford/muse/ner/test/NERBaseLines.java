package edu.stanford.muse.ner.test;

import edu.stanford.muse.email.Contact;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.ner.NER;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.featuregen.FeatureGenerator;
import edu.stanford.muse.ner.featuregen.FeatureVector;
import edu.stanford.muse.ner.segmentation.WordSegmenter;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.util.*;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.*;

/**
 * Created by viharipiratla on 27/05/15.
 */
public class NERBaseLines {
    public static abstract class NERRecogniser{
        abstract String getName();
        //supply all the resources required to initiate the model
        abstract void init(Archive archive, Map<String,String> abNames, Map<String,String> dbpedia, Object obj);
        abstract List<Triple<String,Integer,Integer>> recognise(String content, Document doc, String type);
        abstract Pair<Integer,Long> getPerfStats();
        //map from entity type to set of entities
        Map<String,Set<String>> falsePositives = new LinkedHashMap<String, Set<String>>(),
                truePositives = new LinkedHashMap<String, Set<String>>(),
                falseNegatives = new LinkedHashMap<String, Set<String>>();

        public static Pair<String,Boolean> match(String name, Collection<String> allNames){
            String match = name; boolean matches = false;
            int bl = -1;
            if(allNames == null)
                return new Pair<String,Boolean>(name, false);
            //chose the biggest match
            for(String rn: allNames) {
                if(name.equals(rn) || name.startsWith(rn+" ") || name.endsWith(" "+rn) || name.contains(" "+rn+" ")
                        || rn.startsWith(name+", ") || rn.startsWith(name+" ") || rn.contains(" "+name+" ")) {
                    matches = true;
                    if(rn.length()>bl) {
                        match = rn;
                        bl = match.length();
                    }
                }
//                if(name.equals(rn)){
//                    matches = true;
//                    match = rn;
//                }
            }
//            if(!matches)
//                System.err.println("Did not find: "+name);
            return new Pair<String,Boolean>(match,matches);
        }

        public void evaluate(Map<String,String> realNames, List<Triple<String,Integer,Integer>> recNames, String type){
            if(recNames == null)
                return;
            for(Triple<String,Integer,Integer> recName: recNames) {
                String name = recName.getFirst();
                name = name.replaceAll("^([Dd]ear|[Hh]i|[hH]ello|[Mm]r|[Mm]rs|[Mm]iss|[Ss]ir|[Mm]adam|[Dd]r\\.|[Pp]rof\\.)\\W+","");
                name = IndexUtils.stripExtraSpaces(name);
                boolean contains = false;
                String match = name;
                Pair<String,Boolean> p = match(name, realNames.keySet());
                match = p.first;
                contains = p.second;

                if(contains) {
                    if (type.equals(realNames.get(match)))
                        add(truePositives, match, type);
                    else {
                        System.err.println(name +", match: "+match + " found with type: " + type + ", expected: " + realNames.get(match));
                        add(falsePositives, name, type);
                    }
                }
                else {
                    add(falsePositives, name, type);
                    //System.err.println("Did not find "+name+" in benchmark");
                }
            }
        }

        public Triple<Double,Double,Double> concludeAndGiveStats(Map<String, String> realNames, String type){
           int numThisType = 0;
            for(String name: realNames.keySet()){
                String nt = realNames.get(name);
                if(!nt.equals(type))
                    continue;
                numThisType ++;
                Pair<String,Boolean> p = match(name, truePositives.get(type));
                if(p.getSecond())
                    continue;
//                if(truePositives.get(type)!=null&&truePositives.get(type).contains(name))
//                    continue;
                //System.err.println("Missed: "+name+", type:"+type);
                //System.err.println("Missed: "+name+", type:"+type);
                add(falseNegatives, name, type);
            }
            double precision = 0, recall = 0, f1 = 0;
            int numCorrect = 0, numFalse = 0, numMiss = 0;
            if(truePositives.get(type)!=null)
                numCorrect = truePositives.get(type).size();
            if(falsePositives.get(type)!=null)
                numFalse = falsePositives.get(type).size();
            if(falseNegatives.get(type)!=null)
                numMiss = falseNegatives.get(type).size();
            if(numCorrect+numFalse>0)
                precision = (double)numCorrect/(double)(numCorrect+numFalse);
            if(numThisType>0)
                recall = (double)numCorrect/(double)numThisType;

            if(precision > 0 && recall > 0)
                f1 = 2*precision*recall/(precision+recall);
            System.err.println(getName()+" missed "+numMiss+"/"+numThisType);
            return new Triple<Double,Double,Double>(precision, recall, f1);
        }

        public Map<String,Set<String>> getTruePositives(){
            return truePositives;
        }

        public Map<String,Set<String>> getFalsePositives(){
            return falsePositives;
        }

        public Map<String,Set<String>> getFalseNegatives(){
            return falseNegatives;
        }

        private void add(Map<String,Set<String>> data, String name, String type){
            if(!data.containsKey(type))
                data.put(type, new HashSet<String>());
            data.get(type).add(name);
        }
    }

    static class ABRecogniser extends NERRecogniser{
        Map<String,String> abNames = new LinkedHashMap<String, String>();
        int numDocs = 0; long totalTime = 0;
        public void init(Archive archive, Map<String,String> abNames, Map<String,String> dbpedia, Object obj){
            //this is cleaned addressbook, dont use it
//            this.abNames = abNames;
            List<Contact> contacts = archive.addressBook.allContacts();
            this.abNames = EmailUtils.getNames(contacts);
        }

        public String getName(){
            return "AddressBook";
        }
        public List<Triple<String,Integer,Integer>> recognise(String content, Document doc, String type){
            long start_time = System.currentTimeMillis();
            if(!FeatureDictionary.PERSON.equals(type)) {
                NERTest.log.info(getName() + " recogniser only supports person type");
                System.err.println(getName() + " recogniser only supports person type");
                return null;
            }
            List<Triple<String,Integer,Integer>> recognised = new ArrayList<Triple<String, Integer, Integer>>();
            List<Triple<String,Integer,Integer>> cnames = Tokenizer.getNamesFromPattern(content, true);
            for(Triple<String,Integer,Integer> cname: cnames)
                if(abNames.containsKey(cname.getFirst()))
                    recognised.add(cname);
            numDocs++;
            totalTime += System.currentTimeMillis()-start_time;
            return recognised;
        }

        public Pair<Integer,Long> getPerfStats(){
            return new Pair<Integer,Long>(numDocs, totalTime);
        }
    }

    static class DBpediaRecogniser extends NERRecogniser{
        int numDocs = 0; long totalTime = 0;
        Map<String, String> dbpedia = new LinkedHashMap<String, String>();

        public void init(Archive archive, Map<String, String> abNames, Map<String, String> dbpedia, Object obj) {
            this.dbpedia = dbpedia;
        }

        public String getName() {
            return "DBpedia";
        }

        public List<Triple<String, Integer, Integer>> recognise(String content, Document doc, String type) {
            long start_time = System.currentTimeMillis();
            List<Triple<String, Integer, Integer>> recognised = new ArrayList<Triple<String, Integer, Integer>>();
            Boolean pn = false;
            if(FeatureDictionary.PERSON.equals(type))
                pn = true;
            List<Triple<String, Integer, Integer>> cnames = Tokenizer.getNamesFromPattern(content, pn);
            String[] aTypes = FeatureDictionary.aTypes.get(type);
            for (Triple<String, Integer, Integer> cname : cnames)
                if (dbpedia.containsKey(cname.getFirst())) {
                    String cType = dbpedia.get(cname.getFirst());
                    for(String aType: aTypes)
                        if(cType.contains(aType)) {
                            recognised.add(cname);
                            break;
                        }
                }
            numDocs++;
            totalTime += System.currentTimeMillis()-start_time;
            return recognised;
        }

        public Pair<Integer,Long> getPerfStats(){
            return new Pair<Integer,Long>(numDocs, totalTime);
        }
    }

    //TODO: Implement Dictionary variants of NERs
    static class StanfordNER extends NERRecogniser{
        int numDocs = 0; long totalTime = 0;
        AbstractSequenceClassifier<CoreLabel> classifier;
        public void init(Archive archive, Map<String, String> abNames, Map<String, String> dbpedia, Object withAB){
            try {
                NERTest.log.info("Loading stanford 3 class NER model");
                String serializedClassifier = System.getProperty("user.home")+File.separator+"epadd-data"+File.separator+"english.nowiki.3class.distsim.crf.ser.gz";//english.all.3class.distsim.crf.ser.gz";
                classifier = CRFClassifier.getClassifier(serializedClassifier);
                NERTest.log.info("Done loading stanford model");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public String getName(){
            return "Stanford NER";
        }

        public List<Triple<String,Integer,Integer>> recognise(String contents, Document doc, String type){
            long start_time = System.currentTimeMillis();
            String sT;
            if(FeatureDictionary.PERSON.equals(type))
                sT = "PERSON";
            else if(FeatureDictionary.ORGANISATION.equals(type))
                sT = "ORGANIZATION";
            else if(FeatureDictionary.PLACE.equals(type))
                sT = "LOCATION";
            else {
                NERTest.log.info("Unknown type:" + type);
                return null;
            }
            //Note: does not return proper offsets
            List<Triple<String,Integer,Integer>> names = new ArrayList<Triple<String, Integer, Integer>>();
            String[] sents = NLPUtils.tokeniseSentence(contents);
            for(String sent: sents){
                List<edu.stanford.nlp.util.Triple<String, Integer, Integer>> triples = classifier.classifyToCharacterOffsets(sent);
                for (edu.stanford.nlp.util.Triple<String, Integer, Integer> trip : triples) {
                    if(sT.equals(trip.first))
                        if(trip.second>0 && trip.third<sent.length() && trip.second<trip.third)
                            names.add(new Triple<String,Integer,Integer>(sent.substring(trip.second,trip.third), trip.second, trip.third));
                }
            }
            numDocs++;
            totalTime += System.currentTimeMillis()-start_time;
            return names;
        }

        public Pair<Integer,Long> getPerfStats(){
            return new Pair<Integer,Long>(numDocs, totalTime);
        }
    }

    static class OpenNLPNER extends NERRecogniser{
        int numDocs = 0; long totalTime = 0;
        NameFinderME pFinder, oFinder, lFinder;
        TokenizerME tokenizer;
        public void init(Archive archive, Map<String, String> abNames, Map<String, String> dbpedia, Object withAB){
            try {
                InputStream pis = NERTest.class.getClassLoader().getResourceAsStream("models/en-ner-person.bin");
                TokenNameFinderModel pmodel = new TokenNameFinderModel(pis);
                InputStream lis = NERTest.class.getClassLoader().getResourceAsStream("models/en-ner-location.bin");
                TokenNameFinderModel lmodel = new TokenNameFinderModel(lis);
                InputStream ois = NERTest.class.getClassLoader().getResourceAsStream("models/en-ner-organization.bin");
                TokenNameFinderModel omodel = new TokenNameFinderModel(ois);
                InputStream tokenStream = NERTest.class.getClassLoader().getResourceAsStream("models/en-token.bin");
                TokenizerModel modelTokenizer = new TokenizerModel(tokenStream);
                tokenizer = new TokenizerME(modelTokenizer);

                pFinder = new NameFinderME(pmodel);
                lFinder = new NameFinderME(lmodel);
                oFinder = new NameFinderME(omodel);
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        public String getName(){
            return "OpenNLP";
        }

        public List<Triple<String,Integer,Integer>> recognise(String contents, Document doc, String type){
            long start_time = System.currentTimeMillis();
            String[] sents = NLPUtils.tokeniseSentence(contents);
            List<Triple<String,Integer,Integer>> names = new ArrayList<Triple<String, Integer, Integer>>();
            for(String sent: sents) {
                Span[] tokSpans = tokenizer.tokenizePos(sent);
                String tokens[] = new String[tokSpans.length];
                for (int i = 0; i < tokSpans.length; i++)
                    tokens[i] = sent.substring(tokSpans[i].getStart(), tokSpans[i].getEnd());

                Span[] spans = null;
                if (type.equals(FeatureDictionary.PERSON))
                    spans = pFinder.find(tokens);
                else if (type.equals(FeatureDictionary.ORGANISATION))
                    spans = oFinder.find(tokens);
                else if (type.equals(FeatureDictionary.PLACE))
                    spans = lFinder.find(tokens);

                for (Span span : spans) {
                    String name = "";
                    for (int i = span.getStart(); i < span.getEnd(); i++) {
                        name += tokens[i];
                        if(i<span.getEnd()-1)
                            name += " ";
                    }
                    names.add(new Triple<String,Integer,Integer>(name,-1,-1));
                }
            }
            numDocs++;
            totalTime += System.currentTimeMillis()-start_time;
            return names;
        }

        public Pair<Integer,Long> getPerfStats(){
            return new Pair<Integer,Long>(numDocs, totalTime);
        }
    }

    static class ePADDNER extends NERRecogniser{
        svm_model pmodel, lmodel, omodel;
        FeatureDictionary pwfs, lwfs, owfs;
        int numDocs = 0;long totalTime = 0;
        NER.NEROptions options = null;
        String name = null;
        Archive archive;
        Map<String,String> abNames, dbpedia;
        WordSegmenter.SegmentationModel psmodel, lsmodel, osmodel;
        public FileWriter pdump, ldump, odump;

        public void init(Archive archive, Map<String, String> abNames, Map<String, String> dbpedia, Object enOptions) {
            this.abNames = abNames;
            this.dbpedia = dbpedia;
            this.archive = archive;
            this.options = (NER.NEROptions) enOptions;
            try {
                System.err.println("Loading models for "+options.evaluatorName+" from "+options.wfsName+" and "+options.modelName);
                Pair<svm_model, FeatureDictionary> p = null;
                String recType = FeatureDictionary.ORGANISATION;
                if(recType.equals(FeatureDictionary.PERSON)) {
                    p = new NER(archive).loadModel(FeatureDictionary.PERSON, options);
                    pmodel = p.getFirst();
                    pwfs = p.getSecond();
                    pdump = new FileWriter(new File(options.dumpFldr + File.separator + FeatureDictionary.PERSON + "_" + options.prefix + "_features.dump"));
                }
                else if(recType.equals(FeatureDictionary.PLACE)) {
                    p = new NER(archive).loadModel(FeatureDictionary.PLACE, options);
                    lmodel = p.getFirst();
                    lwfs = p.getSecond();
                    ldump = new FileWriter(new File(options.dumpFldr + File.separator + FeatureDictionary.PLACE + "_" + options.prefix + "_features.dump"));
                }
                else if(recType.equals(FeatureDictionary.ORGANISATION)) {
                    p = new NER(archive).loadModel(FeatureDictionary.ORGANISATION, options);
                    omodel = p.getFirst();
                    owfs = p.getSecond();
                    odump = new FileWriter(new File(options.dumpFldr + File.separator + FeatureDictionary.ORGANISATION + "_" + options.prefix + "_features.dump"));
                }
                name = options.evaluatorName;

                Set<Map<String,String>> gset = new HashSet<Map<String,String>>();
                int numAB = 0, numDB = 0;
                if(options.addressbook) {
                    gset.add(abNames);
                    numAB = abNames.size();
                }
                if(options.dbpedia) {
                    gset.add(dbpedia);
                    numDB = dbpedia.size();
                }
                String path = archive.baseDir+File.separator+"models";
                System.err.println("Initialising segmentation models with "+numAB+" address book names and "+numDB+" dbpedia names");
                //type is used just to open or dump to a file
//                psmodel = WordSegmenter.loadModel(path,options.prefix+"_"+FeatureDictionary.PERSON,pmodel,pwfs,gset);
//                lsmodel = WordSegmenter.loadModel(path,options.prefix+"_"+FeatureDictionary.PLACE,lmodel,lwfs,gset);
//                osmodel = WordSegmenter.loadModel(path,options.prefix+"_"+FeatureDictionary.ORGANISATION,omodel,owfs,gset);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public String getName(){
            return name;
        }

        public List<Triple<String,Integer,Integer>> recognise(String contents, Document doc, String type){
            long start_time = System.currentTimeMillis();
            List<Triple<String,Integer,Integer>> names = Tokenizer.getNamesFromPattern(contents, type.equals(FeatureDictionary.PERSON));
            List<Triple<String,Integer,Integer>> recognised = new ArrayList<Triple<String, Integer, Integer>>();
            svm_model model;FeatureDictionary wfs;
            WordSegmenter.SegmentationModel smodel;
            FileWriter dump = null;
            if(FeatureDictionary.PERSON.equals(type)) {
                model = pmodel;
                wfs = pwfs;
                smodel = psmodel;
                dump = pdump;
            }else if(FeatureDictionary.PLACE.equals(type)){
                model = lmodel;
                wfs = lwfs;
                smodel = lsmodel;
                dump = ldump;
            }else if(FeatureDictionary.ORGANISATION.equals(type)){
                model = omodel;
                wfs = owfs;
                smodel = osmodel;
                dump = odump;
            }else{
                NERTest.log.warn("Unrecognised type :"+type+" in "+getName());
                return null;
            }
            String[] aTypes = FeatureDictionary.aTypes.get(type);
            outer:
            for(Triple<String,Integer,Integer> name: names){
                String dbpediaType =  dbpedia.get(name.getFirst());
                if(dbpediaType==null)
                    abNames.get(name.getFirst());
                if(dbpediaType!=null) {
                    for (String aType : aTypes) {
                        if (dbpediaType.endsWith(aType)) {
                            System.err.println("Recognised: " + IndexUtils.stripExtraSpaces(name.getFirst()) + " from dbpedia");
                            recognised.add(name);
                            continue outer;
                        }
                    }
                    continue outer;
                }

                String dumpStr = null;
                if(!options.segmentation) {
                    FeatureVector fv = wfs.getVector(name.getFirst());
                    svm_node[] sx = fv.getSVMNode();
                    double[] probs = new double[2];
                    double v = svm.svm_predict_probability(model, sx, probs);
                    String phrase = name.getFirst();
                    if(phrase!=null)
                        phrase = phrase.replaceAll(FeatureDictionary.MARKERS_PATT, "");
                    if (v > 0) {
                        //dont emit it if is a dictionary word
                        if(!DictUtils.commonDictWords5000.contains(phrase.toLowerCase())) {
                            recognised.add(new Triple<String, Integer, Integer>(phrase, name.getSecond(), name.getThird()));
                            System.err.println("SVM got: " + IndexUtils.stripExtraSpaces(phrase));
                        };
                    }else{
                        if(name.getFirst().length()>2) {
                            String wc = FeatureGeneratorUtil.tokenFeature(name.getFirst());
                            if(("ac".equals(wc) && FeatureDictionary.ORGANISATION.equals(type)) || FeatureDictionary.PLACE.equals(type)
                                    || (!"ac".equals(wc) && FeatureDictionary.PERSON.equals(type))) {
                                FeatureDictionary.EntityContext context = new FeatureDictionary.EntityContext(name.first, "some", doc, archive);
                                List<FeatureDictionary.EntityContext> scontexts = context.getClosestNHits(wfs.entityContexts.values(), 5);
                                int numThisType = 0;
                                for (FeatureDictionary.EntityContext sc : scontexts) {
                                    System.err.println(sc.type + ", " + sc.name + "->" + context.name);
                                    for (String aType : aTypes)
                                        if (sc.type.endsWith(aType)) {
                                            numThisType++;
                                        }
                                }
                                if (numThisType > 0 && numThisType == scontexts.size()) {
                                    System.err.println("Recognising " + IndexUtils.stripExtraSpaces(name.getFirst()) + " expands to: " + numThisType + "/" + scontexts.size());
                                    recognised.add(name);
                                }
                            }
                        }
                    }
                    dumpStr = probs[0]+"\n";
                    dumpStr += v+"  "+phrase+"  "+wfs.getVector(phrase);
                }else{
                    FeatureVector fv = wfs.getVector(name.getFirst());
                    Pair<String,String> ret = smodel.segment(name.getFirst(), wfs, model);
                    String sname = ret.first;
                    dumpStr = ret.second;
                    dumpStr += name.getFirst()+" -> "+sname+", "+fv;
                    if(sname != null)
                        recognised.add(new Triple<String,Integer,Integer>(sname, name.getSecond(), name.getThird()));
                }
                try {
                    if(dumpStr!=null)
                        dump.write(dumpStr + "\n");
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            totalTime += System.currentTimeMillis()-start_time;
            numDocs++;
            return recognised;
        }

        public Pair<Integer,Long> getPerfStats(){
            return new Pair<Integer,Long>(numDocs, totalTime);
        }
    }

    public static void main(String[] args){
        try {
            Collection<String> set = new HashSet<String>();
            set.add("Griffin Trust");
            set.add("Griffin Trust for Excellence in Poetry");
            set.add("GRIFFIN TRUST For Excellence In Poetry");

            String some = "Griffin Trust for Excellence in Poetry";
            String some2 = "Trust";
            int idx = some.indexOf(some2);
            if((idx == 0 || some.charAt(idx-1)==' ') && (idx==some.length()-1 || some.charAt(idx+1)==' '))
                System.err.println("Yes");
            System.err.println(NERRecogniser.match("The Griffin Trust", set));

//            AbstractSequenceClassifier<CoreLabel> classifier;
//            String serializedClassifier = "/Users/viharipiratla/sandbox/stanford-ner-2014-08-27/classifiers/english.all.3class.distsim.crf.ser.gz";
//            classifier = CRFClassifier.getClassifier(serializedClassifier);
//
//            String[] example = {"Good afternoon Rajat Raina, this is Vihari and how are you today?",
//                    "I go to school at Stanford University, which is located in California."};
//
//            // This gets out entities with character offsets
//            int j = 0;
//            for (String str : example) {
//                j++;
//                List<edu.stanford.nlp.util.Triple<String, Integer, Integer>> triples = classifier.classifyToCharacterOffsets(str);
//                for (edu.stanford.nlp.util.Triple<String, Integer, Integer> trip : triples) {
//                    System.out.printf("%s over character offsets [%d, %d) in sentence %d.%n",
//                            trip.first(), trip.second(), trip.third, j);
//                }
//            }
//            System.out.println("---");
//
//            // This prints out all the details of what is stored for each token
//            int i = 0;
//            for (String str : example) {
//                for (List<CoreLabel> lcl : classifier.classify(str)) {
//                    for (CoreLabel cl : lcl) {
//                        System.out.print(i++ + ": ");
//                        System.out.println(cl.toShorterString());
//                    }
//                }
//            }
//
//            System.out.println("---");
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
