package edu.stanford.muse.ner.test;

import edu.stanford.muse.email.Contact;
import edu.stanford.muse.index.*;
import edu.stanford.muse.ner.NER;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.featuregen.FeatureVector;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.NLPUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.webapp.SimpleSessions;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;

/**
 * Created by viharipiratla on 26/05/15.
 *
 * Evaluates variations of epadd ner algorithms, stanford-ner and opennlp over benchmarks
 * Blind match to DBpedia                (BD)
 * Blind match to Addressbook            (BAB)
 * Stanford NER                          (SN)
 * OpenNLP NER                           (ON)
 * Stanford NER with AB                  (SND)
 * OpenNLP with AB                       (OND)
 * ePADD NER with segmentation,
 * addressbook and latent groups         (EN)
 * ePADD NER minus addressbook           (EN-D)
 * ePADD NER minus Wikipedia             (EN-W)
 * ePADD NER minus segmentation          (EN-S)
 * ePADD NER minus latent groups         (EN-E)
 * */
public class NERTest {
    //parse line from manually annotated benchmark file
    private static Pair<String,String> parseLine(String line){
        line = line.trim();
        String[] fields = line.split(" ::: ");
        if (fields.length < 2) {
            System.err.println("Improper tagging in line: " + line);
            return null;
        }
        String type = null;
        if ("p".equals(fields[1]))
            type = FeatureDictionary.PERSON;
        else if ("o".equals(fields[1]))
            type = FeatureDictionary.ORGANISATION;
        else if ("l".equals(fields[1]))
            type = FeatureDictionary.PLACE;
        else {
            System.err.println("Unknown tag: "+type+" in " + line);
            return null;
        }

        String cname = IndexUtils.stripExtraSpaces(fields[0]);
        cname = cname.replaceAll("^([Dd]ear|[Hh]i|[hH]ello|[Mm]r|[Mm]rs|[Mm]iss|[Ss]ir|[Mm]adam|[Dd]r\\.|[Pp]rof\\.)\\W+","");
        return new Pair<String,String>(cname, type);
    }

    public static void test(){
        try {
            String userDir = System.getProperty("user.home") + File.separator + "ePADD" + File.separator + "user";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            //NER ner = new NER(archive);
            String dataFldr = System.getProperty("user.home")+File.separator+"epadd-data"+File.separator+"ner-benchmarks"+File.separator+"benchmark-Jeb-Bush";
            Map<String, String> dbpedia = EmailUtils.readDBpedia();
            List<Contact> contacts = archive.addressBook.allContacts();
            Map<String, String> abNames = EmailUtils.getNames(contacts);
//            abNames = FeatureDictionary.cleanAB(abNames,dbpedia);
//            System.err.println("Cleaned addressbook size: " + abNames.size());

            String recType = FeatureDictionary.ORGANISATION;

            NERBaseLines.NERRecogniser[] recognisers = new NERBaseLines.NERRecogniser[]{
                    new NERBaseLines.ABRecogniser(),
                    new NERBaseLines.DBpediaRecogniser(),
                    new NERBaseLines.StanfordNER(),
                    new NERBaseLines.OpenNLPNER(),
               //     new NERBaseLines.ePADDNER(),
//                    new NERBaseLines.ePADDNER(),
//                    new NERBaseLines.ePADDNER(),
                     new NERBaseLines.ePADDNER()
            };
            Object[] options = new Object[]{
                    null,
                    null,
                    false,
                    false,
               //     new NER.NEROptions().setPrefix("").setName("ePADD NER").setDumpFldr(dataFldr),
//                    new NER.NEROptions().setAddressBook(false).setPrefix("woAB_").setName("ePADD NER minus AB"),
//                    new NER.NEROptions().setDBpedia(false).setPrefix("woDB_").setName("ePADD NER minus DBpedia"),
                    new NER.NEROptions().setSegmentation(false).setPrefix("").setName("ePADD NER minus segmentation").setDumpFldr(dataFldr)
            };
            for (int ri = 0; ri < recognisers.length; ri++) {
                System.err.println("Initiating: "+recognisers[ri].getName());
                NERBaseLines.NERRecogniser recogniser = recognisers[ri];
                recogniser.init(archive, abNames, dbpedia, options[ri]);
            }

            BufferedReader br = new BufferedReader(new FileReader(dataFldr + File.separator + "docIds.txt"));
            Set<String> docIds = new HashSet<String>();
            String line = null;
//            while((line = br.readLine())!=null){
//                docIds.add(line.trim());
//            }
//            br.close();

            Map<String, String> bNames = new LinkedHashMap<String, String>();
//            br = new BufferedReader(new FileReader(dataFldr+File.separator+"people.txt"));
//            line = null;
//            while((line = br.readLine())!=null){
//                bNames.put(line.trim(), FeatureDictionary.PERSON);
//            }
//            br.close();
            //map from name to type; all the names in benchmark files
;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                String[] fields = line.split("\\/");
                if(fields.length == 0)
                    continue;
                String[] words = fields[fields.length-1].split(" ");
                String docId = words[words.length-1];
                try {
                    BufferedReader brr = new BufferedReader(new FileReader(new File(dataFldr + File.separator + "docs" +
                                    File.separator + docId + ".txt")));
                    String eline = null;
                    boolean checked = true;
                    while ((eline = brr.readLine()) != null) {
                        if (eline.startsWith("#"))
                            continue;
                        if (!checked)
                            continue;

                        Pair<String, String> entry = parseLine(eline);
                        if (entry != null)
                            bNames.put(entry.getFirst(), entry.getSecond());
                    }
                    if (checked)
                        docIds.add(line);
                }catch(Exception e){
                    System.err.println("File: "+dataFldr + File.separator + "docs" + File.separator +docId + ".txt"+" not found");
                    e.printStackTrace();
                };
            }
            br.close();

            try {
                //also read missing.txt
                br = new BufferedReader(new FileReader(new File(dataFldr + File.separator + "docs" +
                        File.separator+ "missing.txt")));
                line = null;
                while ((line = br.readLine()) != null) {
                    Pair<String, String> entry = parseLine(line);
                    if (entry != null)
                        bNames.put(entry.getFirst(), entry.getSecond());
                }
            }catch(Exception e){
                System.err.println("NO missing file?");
                e.printStackTrace();
            }

            System.err.println("Benchmark contains "+docIds.size()+" ids");
            int numPerson = 0, numOrg = 0, numLoc = 0;
            for(String rec: bNames.keySet()) {
                if (FeatureDictionary.PERSON.equals(bNames.get(rec)))
                    numPerson++;
                else if(FeatureDictionary.PLACE.equals(bNames.get(rec)))
                    numLoc ++;
                else if(FeatureDictionary.ORGANISATION.equals(bNames.get(rec)))
                    numOrg++;
            }
            System.err.println("**************\n" +
                    "Found "+numPerson+" person entities\n"+
                    "Found "+numLoc+" location entities\n"+
                    "Found "+numOrg+" org entities");

            LuceneIndexer li = (LuceneIndexer) archive.indexer;
            for (String docId : docIds) {
                System.err.println(docId);
                EmailDocument ed = li.docForId(docId);
                String contents = archive.getContents(ed, false);
                for (NERBaseLines.NERRecogniser recogniser : recognisers) {
                    List<Triple<String,Integer,Integer>> recNames = recogniser.recognise(contents, ed, recType);
                    recogniser.evaluate(bNames,recNames, recType);
                }
            }

            System.err.println("Stats for "+recType+"\n----------------------");
            for(NERBaseLines.NERRecogniser recogniser: recognisers) {
                Triple<Double,Double,Double> stats = recogniser.concludeAndGiveStats(bNames, recType);
                Pair<Integer,Long> perf = recogniser.getPerfStats();
                System.err.println("##################\n"+
                        recogniser.getName());
                System.err.println("Precision: "+stats.getFirst()+", Recall: "+stats.getSecond()+", F1: "+stats.getThird());
                System.err.println("Docs processed: " + perf.getFirst() + ", Time spent: " + perf.getSecond());
                Map<String,Set<String>> fn = recogniser.getFalseNegatives();
                Map<String,Set<String>> fp = recogniser.getFalsePositives();
                Map<String,Set<String>> tp = recogniser.getTruePositives();
                String[] tags = new String[]{"fn","fp","tp"};
                List<Map<String,Set<String>>> dumps = new ArrayList<Map<String,Set<String>>>();
                dumps.add(fn);dumps.add(fp);dumps.add(tp);
                int ti=0;
                for(Map<String,Set<String>> dump: dumps) {
                    for(String str: dump.keySet()) {
                        String filename = dataFldr + File.separator + str + "_" + tags[ti] + "_" + recogniser.getName() + ".txt";
                        FileWriter fw = new FileWriter(new File(filename));
                        for(String e: dump.get(str))
                            fw.write(e+"\n");
                        fw.close();
                    }
                    ti++;
                }
                if(recogniser instanceof NERBaseLines.ePADDNER) {
                    NERBaseLines.ePADDNER epaddrec = (NERBaseLines.ePADDNER) recogniser;
                    if(epaddrec.ldump!=null)
                        epaddrec.ldump.close();
                    if(epaddrec.odump!=null)
                        epaddrec.odump.close();
                    if(epaddrec.ldump!=null)
                        epaddrec.ldump.close();
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
   }

    public static short PERSON = 0, LOCATION = 1, ORG = 2;
    static Log log					= LogFactory.getLog(NER.class);
    private static Triple<List<String>, Integer, Set<String>> consider(String name, Triple<List<String>, Integer, Set<String>> t, Set<String> names, String label) {
        t.first.add(name);
        boolean f = false;
        name = name.replaceAll("\\s+", " ");
        for (String n : names) {
            if (n.contains(name) || name.contains(n)) {
                f = true;
                break;
            }
        }

        if (f) {
            t.second++;
            Set<String> m = new HashSet<String>();
            for (String n : t.third)
                if (n.contains(name) || name.contains(n))
                    m.add(n);
            for (String n : m)
                t.third.remove(n);
        }

        return t;
    }

    public static void testNER() {
        boolean rand = false;
        try {
            String userDir = System.getProperty("user.home") + File.separator + "ePADD" + File.separator + "user-creeley2";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            List<Document> docs = archive.getAllDocs();
            LuceneIndexer li = (LuceneIndexer) archive.indexer;

            //AddressBook ab = archive.addressBook;

            String[] types = new String[] { "person-walstreet", "location", "organization" };
            String[] modelFiles = new String[types.length];
            String[] preTags = new String[] { "<span class=\"class3\">", "<span class=\"class4\">", "<span class=\"class5\">" };
            String[] postTags = new String[] { "</span>", "</span>", "</span>" };

            System.err.println("Loading features dictionary.");
            String type = FeatureDictionary.PERSON;
            NER ner = new NER(archive);
            Pair<svm_model, FeatureDictionary> p = ner.loadModel(type);
            System.err.println("Done loading features dictionary.");

            svm_model svmModel = p.first;
            FeatureDictionary wfs = p.second;
            int nr_class = svmModel.nr_class;
            double[] prob_estimates = new double[nr_class];

            List<Contact> contacts = archive.addressBook.allContacts();
            //gazz2 should be AB, as we are also seeing how many of these names are recognised by the addressbook alone.
            Map<String, String> gazz2 = EmailUtils.getNames(contacts);

            int[] freqs = new int[] { 0, 0, 0, 0 };

            System.err.println("Loading required models");
            String modeldir = "";//userDir + "/";
            for (int i = 0; i < types.length; i++) {
                modelFiles[i] = modeldir + "models/en-ner-" + types[i] + ".bin";
                System.err.println("Model file: " + modelFiles[i]);
            }

            NameFinderME[] finders = new NameFinderME[modelFiles.length];
            Map<String, Set<String>> entities = new HashMap<String, Set<String>>();
            SentenceDetectorME sentenceDetector;
            InputStream SentStream = NLPUtils.class.getClassLoader().getResourceAsStream("models/en-sent.bin");
            SentenceModel model = null;
            TokenizerME tokenizer = null;
            try {
                for (int i = 0; i < modelFiles.length; i++) {
                    TokenNameFinderModel nmodel = new TokenNameFinderModel(NLPUtils.class.getClassLoader().getResourceAsStream(modelFiles[i]));
                    finders[i] = new NameFinderME(nmodel);
                }

                model = new SentenceModel(SentStream);
                InputStream tokenStream = NLPUtils.class.getClassLoader()
                        .getResourceAsStream("models/en-token.bin");
                TokenizerModel modelTokenizer = new TokenizerModel(tokenStream);
                tokenizer = new TokenizerME(modelTokenizer);
            } catch (Exception e) {
                e.printStackTrace();
            }
            sentenceDetector = new SentenceDetectorME(model);
            System.err.println("Done loading required models");

            int l = 0, numNames = 0;

            FileWriter fw = new FileWriter("nertest.html");
            fw.write("<style>.class1{color: rgb(197, 16, 16);}.class2{background-color: green;}.class3{background-color:yellow;}.class4{background-color:skyblue;}.class5{color: rgb(55, 197, 16);}</style>");
            Random randnum = new Random();

            Set<String> people = new HashSet<String>(), places = new HashSet<String>(), orgs = new HashSet<String>();
            List<String> docIds = new ArrayList<String>();

            if (!rand) {
                //nechmark files for a given doc id set.
                String home = System.getProperty("user.home");
                String fldrName = home+File.separator+"epadd-data/ner-benchmarks/benchmark-Robert Creeley/";
                File fldr = new File(fldrName);
                if(!fldr.exists() || !fldr.isDirectory()){
                    System.err.println("The folder: "+fldr.getAbsolutePath()+" does not exist or is not a dir");
                    return;
                }
                File[] benFiles = fldr.listFiles();
                System.err.println("Total files:" +benFiles.length);
                List<Set<String>> temp = Arrays.asList(people, places, orgs);
                for (int i = 0; i < benFiles.length; i++) {
                    BufferedReader br = new BufferedReader(new FileReader(benFiles[i]));
                    String fname = benFiles[i].getName();
                    if (fname.endsWith("~"))
                        continue;
                    String line = null;
                    boolean checked = false;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("#") || !checked) {
                            if (!checked) {
                                if (line.contains("#checked"))
                                    checked = true;
                            }
                            continue;
                        }
                        line = line.trim();
                        String[] fields = line.split(" ::: ");
                        if (fields.length < 2) {
                            System.err.println("Improperly formatted line: " + line);
                            continue;
                        }
                        String name = fields[0];
                        String nt = fields[1];
                        int ti = 0;
                        if ("p".equals(nt))
                            ti = 0;
                        else if ("l".equals(nt))
                            ti = 1;
                        else if ("o".equals(nt))
                            ti = 2;
                        else {
                            System.err.println("Unknown type: " + nt + " in line: " + line);
                            continue;
                        }
                        temp.get(ti).add(name);
                    }
                    System.err.println("Done loading ... " + fname);
                    br.close();
                    if (checked) {
                        String docId = fname.substring(0, fname.length() - 4);
                        docId = "/home/hangal/data/creeley/1/"+docId;
                        docIds.add(docId);
                    }
                }
                System.err.println("Read: "+docIds.size()+" files");
                System.err.println("Found: " + people.size() + " people, " + places.size() + " places and " + orgs.size() + "orgs");
            }
            List<Set<String>> recnames = new ArrayList<Set<String>>();
            //stats of recognition total found and correct
            List<Triple<List<String>, Integer, Set<String>>> found = new ArrayList<Triple<List<String>, Integer, Set<String>>>();
            for (int x = 0; x < types.length + 2; x++) {
                recnames.add(new HashSet<String>());
                Set<String> arr = new HashSet<String>();
                Set<String> temp = null;
                if (x == 0 || x == 2)
                    temp = people;
                if (x == 1) {
                    if (type.equals(FeatureDictionary.PERSON))
                        temp = people;
                    else if (type.equals(FeatureDictionary.ORGANISATION))
                        temp = orgs;
                    else if (type.equals(FeatureDictionary.PLACE))
                        temp = places;
                }

                if (x == 3)
                    temp = places;
                if (x == 4)
                    temp = orgs;
                for (String str : temp)
                    arr.add(str);

                found.add(new Triple<List<String>, Integer, Set<String>>(new ArrayList<String>(), 0, arr));
            }

            FileWriter dfw = new FileWriter("data/features.dump");
            Set<String> dumped = new HashSet<String>();

            int NUM = 100;
            if (!rand)
                NUM = docIds.size();
            for (int k = 0; k < NUM; k++) {
                System.err.println("Processed: " + k + " of " + NUM);
                EmailDocument ed = null;
                if (rand)
                    ed = (EmailDocument) docs.get(randnum.nextInt(docs.size()));
                else {
                    ed = li.docForId(docIds.get(k));
                    if (ed == null) {
                        System.err.println("Couldn't find doc for id: " + docIds.get(k));
                        continue;
                    }
                }
                if (ed == null)
                    continue;

                fw.write("<a target='_blank' href='http://localhost:8080/epadd/browse?docId=" + StringEscapeUtils.escapeHtml(ed.getUniqueId()) + "'>" + ed.getUniqueId() + "</a>\n<br>");
                docIds.add(ed.getUniqueId());

                String content = li.getContents(ed, false);
                content = content.replaceAll("^>+.*", "");
                content = content.replaceAll("\\n\\n", ". ");
                content = content.replaceAll("\\n", " ");
                content.replaceAll(">+", "");
                content = content.replaceAll("\"", "").replaceAll(">|<", " ");

                String[] sents = sentenceDetector.sentDetect(content);
                l++;
                if (l > 0 && l % 1000 == 0) {
                    System.err.println("Processed: " + l);
                    System.err.println("#" + numNames + " found");
                    for (String t : types)
                        if (entities.containsKey(t))
                            System.err.println("type:" + t + "\t" + "#" + entities.get(t).size());
                }

                for (int i = 0; i < sents.length; i++) {
                    boolean printed = false;
                    String text = sents[i];
                    List<Triple<String, Integer, Integer>> temp = Tokenizer.getNamesFromPattern(text, type.equals(FeatureDictionary.PERSON));
                    List<String> names = new ArrayList<String>();
                    for (Triple<String, Integer, Integer> t : temp)
                        names.add(t.first);

                    boolean contains = false;
                    for (String name : names) {
                        if (gazz2.containsKey(name)) {
                            recnames.get(0).add(name);
                            text = text.replaceAll(name, "<span class=\"class1\">" + StringEscapeUtils.escapeHtml(name) + "</span>");
                            contains = true;
                            found.set(0, consider(name, found.get(0), people, "people"));
                        }
                    }
                    if (contains) {
                        fw.write(text + "<br>\n");
                        printed = true;
                    }

                    contains = false;
                    text = sents[i];
                    for (String name : names) {
                        String tc = FeatureGeneratorUtil.tokenFeature(name);
                        if (name == null || tc.equals("ac"))
                            continue;
                        name = name.replaceAll("^\\W+|\\W+$", "");
                        //trailing apostrophe
                        //this could be a good signal for name(occasionally could also be org). The training data (Address book) doesn't contain such pattern, hence probably have to hard code it and I dont want to.
                        name = name.replaceAll("'s$", "");
                        //stuff b4 colon like subject:, from: ...
                        name = name.replaceAll("\\w+:\\W+", "");
                        //remove stuff in the beginning
                       // name = name.replaceAll("([Dd]ear|[hH]i|[hH]ello)\\W+", "");
                        name = name.replaceAll("^\\W+|\\W+$", "");

                        FeatureVector wfv = wfs.getVector(name);
                        svm_node[] sx = wfv.getSVMNode();

                        double v = svm.svm_predict_probability(svmModel, sx, prob_estimates);
                        //System.err.println(name + " : " + v + ", " + wfv);
                        if (!dumped.contains(name)) {
                            dfw.write(prob_estimates[0] + "\t" + prob_estimates[1] + "\n");
                            dfw.write(name + " : " + v + " " + wfv + "\n");
                            dumped.add(name);
                        }

                        if (v > 0) {
                            String label = "";
                            if (v == 1) {
                                label = FeatureDictionary.PERSON;
                                System.err.println("Found: "+name);
                            }
                            else if (v == 2)
                                label = FeatureDictionary.ORGANISATION;
                            else if (v == 3)
                                label = FeatureDictionary.PLACE;

                            //System.err.println("Postive phrase: " + name + " found with prob " + v);
                            recnames.get(1).add(name);
                            text = text.replaceAll(name, "<span class=\"class2\">{svm-" + label + ": " + StringEscapeUtils.escapeHtml(name) + "}</span>");
                            contains = true;
                            if (label.equals("Person"))
                                found.set(1, consider(name, found.get(1), people, "people"));
                            else if (label.equals("Organisation"))
                                found.set(2, consider(name, found.get(1), orgs, "orgs"));
                            else if (label.equals("Place"))
                                found.set(3, consider(name, found.get(1), places, "places"));
                        }
                    }
                    if (contains) {
                        fw.write(text + "<br>\n");
                        printed = true;
                    }

                    text = sents[i];
                    Span[] tokSpans = tokenizer.tokenizePos(text);
                    // Sometimes there are illformed long sentences in the text that
                    // give hard time to NLP.
                    if (tokSpans.length > 2000)
                        continue;

                    String tokens[] = new String[tokSpans.length];
                    for (int t = 0; t < tokSpans.length; t++) {
                        tokens[t] = text.substring(
                                Math.max(0, tokSpans[t].getStart()),
                                Math.min(text.length(), tokSpans[t].getEnd()));
                    }
                    for (int fi = 0; fi < finders.length; fi++) {
                        text = sents[i];
                        Set<String> pnames = new HashSet<String>();
                        NameFinderME finder = finders[fi];
                        Span[] bSpans = finder.find(tokens);

                        for (Span span : bSpans) {
                            String pname = "";
                            for (int m = span.getStart(); m < span.getEnd(); m++) {
                                int s = tokSpans[m].getStart(), e = tokSpans[m].getEnd();
                                if (s < 0 || s >= text.length())
                                    continue;
                                if (e < 0 || e >= text.length())
                                    continue;
                                pname += text.substring(
                                        tokSpans[m].getStart(),
                                        tokSpans[m].getEnd());
                                if (m < (span.getEnd() - 1)) {
                                    pname += " ";
                                }
                            }
                            if (pname == null || pname.equals(""))
                                continue;

                            pname = pname.replaceAll("\"", "");
                            pnames.add(pname);

                            recnames.get(fi + 2).add(pname);
                            if (fi == 0)
                                found.set(fi + 2, consider(pname, found.get(fi + 2), people, "people"));
                            else if (fi == 1)
                                found.set(fi + 2, consider(pname, found.get(fi + 2), places, "location/orgs"));
                            else if (fi == 2)
                                found.set(fi + 2, consider(pname, found.get(fi + 2), orgs, "location/orgs"));

                            try {
                                String preTag = preTags[fi];
                                String postTag = postTags[fi];
                                text = text.replaceAll(pname, preTag + "{" + types[fi] + " : " + pname + "}" + postTag);
                                break;
                            } catch (Exception e) {
                                System.err.println("Exception while replacing pattern");
                            }
                        }
                        if (!entities.containsKey(types[fi]))
                            entities.put(types[fi], new HashSet<String>());

                        entities.get(types[fi]).addAll(pnames);

                        numNames += bSpans.length;
                        if (bSpans.length > 0) {
                            fw.write("<br>" + text + " " + pnames + " <br>\n");
                            printed = true;
                        }
                        freqs[fi] += bSpans.length;
                    }
                    if (!printed) {
                        fw.write("<br>" + sents[i] + "<br>");
                        printed = true;
                    }
                }
                fw.write("<br>----------------------------------------------<br>\n");
            }
            dfw.close();

            int tu = 0;
            for (String t : entities.keySet()) {
                PrintWriter pw = new PrintWriter(new File("data/" + t + ".txt"));
                if (entities.get(t) != null) {
                    for (String e : entities.get(t))
                        pw.println(e);
                    tu += entities.get(t).size();
                }
                pw.close();
            }

            fw.write("Toatal: " + numNames + " found" + " unique names: #" + tu + "\n");//" found by ner: "+gnerNames.size());
            for (int fi = 0; fi < types.length; fi++)
                fw.write("Found: " + freqs[fi] + " entities of type: " + types[fi] + "\n");
            fw.close();

            FileWriter fw1 = new FileWriter(new File("data/AB.txt"));
            for (String str : recnames.get(0))
                fw1.write(str + "\n");
            fw1.close();
            fw1 = new FileWriter(new File("data/ner-svm.txt"));
            for (String str : recnames.get(1))
                fw1.write(str + "\n");
            fw1.close();
            fw1 = new FileWriter(new File("data/ner-svm-couldntget.txt"));
            for (String str : found.get(1).third)
                fw1.write(str + "\n");
            fw1.close();
            fw1 = new FileWriter(new File("data/ner-walstreet.txt"));
            for (String str : recnames.get(2))
                fw1.write(str + "\n");
            fw1.close();

            //		System.err.println("OpenNLP NER-" + type + " found: " + comp1.size() + " and SVM-" + type + " found: " + comp2.size() + ", common: " + common);
            /*********** Stats *************/
            String[] ann = new String[] { "AB", "SVM-" + type, "NER-People", "NER-Places", "NER-Orgs" };
            for (int i = 0; i < found.size(); i++) {
                int totalsize = 0;
                if (i == 0 || i == 4)
                    totalsize = people.size();
                if (i == 1)
                    totalsize = people.size();
                if (i == 2)
                    totalsize = orgs.size();
                if (i == 3)
                    totalsize = places.size();

                if (i == 5)
                    totalsize = places.size();
                if (i == 6)
                    totalsize = orgs.size();
                if (totalsize == 0)
                    continue;
                double acc = (double) found.get(i).second / (double) found.get(i).first.size();
                double recall = (double) (totalsize - found.get(i).third.size()) / (double) totalsize;
                double f1 = 2 * acc * recall / (acc + recall);
                System.err.println("******" + ann[i] + "\tAccuracy:" + acc + ", Recall:" + recall + ", F1:" + f1);
            }
            testStanfordNER(archive, docIds, people);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testStanfordNER(Archive archive, List<String> docIds, Set<String> people) {
        try {
            String serializedClassifier = "/Users/viharipiratla/sandbox/stanford-ner-2014-08-27/classifiers/english.nowiki.3class.distsim.crf.ser.gz";//english.all.3class.distsim.crf.ser.gz";
            LuceneIndexer li = (LuceneIndexer) archive.indexer;
            AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifier(serializedClassifier);
            Set<String> arr = new HashSet<String>();
            for (String str : people)
                arr.add(str);

            Triple<List<String>, Integer, Set<String>> stats = new Triple<List<String>, Integer, Set<String>>(new ArrayList<String>(), 0, arr);
            for (String docId : docIds) {
                EmailDocument ed = li.docForId(docId);
                String contents = archive.getContents(ed, false);
                List<List<CoreLabel>> out = classifier.classify(contents);
                for (List<CoreLabel> sentence : out) {
                    for (CoreLabel word : sentence) {
                        String ann = word.get(CoreAnnotations.AnswerAnnotation.class);
                        String w = word.word();
                        if (ann.equals("PERSON") && w.length() > 3)
                            consider(w, stats, people, "person");
                    }
                }
            }

            FileWriter fw = new FileWriter(new File("data/ner-stanford-couldn't-get.txt"));
            for (String str : stats.third)
                fw.write(str + "\n");
            fw.close();
            System.err.println("NER-stanford was able to get " + (people.size() - stats.third.size()) + " of " + people.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void generateTestData(){
        try {
            String userDir = System.getProperty("user.home") + File.separator + "ePADD" + File.separator + "user";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            String dataFldr = System.getProperty("user.home")+File.separator+"epadd-data"+File.separator+"sandbox";
            List<Document> docs = archive.getAllDocs();
            Random random = new Random();
            int numSents = 0;
            Set<String> docIds = new HashSet<String>();
            while(numSents<1500){
                int di = random.nextInt(docs.size());
                Document doc = docs.get(di);
                String contents = archive.getContents(doc, false);
                String[] sents = NLPUtils.tokeniseSentence(contents);
                String docId = doc.getUniqueId();
                String[] fields = docId.split("\\/");

                String name = null;
                if(fields!=null && fields.length>0) {
                    String[] words = fields[fields.length - 1].split(" ");
                    name = words[words.length-1];
                }
                else
                    continue;
                numSents+= sents.length;
                String fn = dataFldr+File.separator+name+".txt";
                FileWriter fw = new FileWriter(new File(fn));
                fw.write(contents);
                fw.close();
                docIds.add(docId);
            }
            FileWriter fw = new FileWriter(new File(dataFldr+File.separator+"docIds.txt"));
            for(String docId: docIds)
                fw.write(docId + "\n");
            fw.close();
            System.err.println("Wrote: "+numSents+" from "+docIds.size()+" docs");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void testDBpedia(NERModel nerModel){
        //when testing remember to change
        //1. lookup method, disable the lookup
        System.err.println("DBpedia scoring check starts");
        String twl = System.getProperty("user.home")+File.separator+"epadd-settings"+File.separator+"SeqModel-test.en.txt.bz2";
        //clear the cache
        EmailUtils.dbpedia = null;
        Map<String,String> dbpedia = EmailUtils.readDBpedia(1, twl);
        //NOther == Not OTHER
        //number of things shown (NON-OTHER) and number of things that should be shown
        int ne = 0, neShown = 0, neShouldShown = 0;
        //number of entries assigned to wrong type and number missed because they are assigned OTHER
        int missAssigned=0, missSegmentation = 0, missNoEvidence = 0;
        int correct = 0;
        //these are the entries which are not completely tagged as OTHER by NER, but may have some segments that are not OTHER, hence visible
        double CUTOFF = 0;
        Map<Short,Map<Short,Integer>> confMat = new LinkedHashMap<>();
        Map<Short, Integer> freqs = new LinkedHashMap<>();
        String[] badSuffixTypes = new String[]{"MusicalWork|Work","Sport", "Film|Work", "Band|Group|Organisation", "Food",
                "EthnicGroup","RadioStation|Broadcaster|Organisation", "MeanOfTransportation", "TelevisionShow|Work",
                "Play|WrittenWork|Work","Language", "Book|WrittenWork|Work","Genre|TopicalConcept", "InformationAppliance|Device",
                "SportsTeam|Organisation", "Eukaryote|Species","Software|Work", "TelevisionEpisode|Work", "Comic|WrittenWork|Work",
                "Mayor", "Website|Work", "Cartoon|Work"
        };
        ol:
        for(String entry: dbpedia.keySet()){
            if(!entry.contains(" "))
                continue;
            String fullType = dbpedia.get(entry);
            Short type = FeatureDictionary.codeType(dbpedia.get(entry));

            if(fullType.equals("Agent"))
                type = FeatureDictionary.PERSON;
            else
                for (String bst: badSuffixTypes)
                    if(fullType.endsWith(bst))
                        continue ol;

            entry = EmailUtils.uncanonicaliseName(entry);
            if(entry.length()>=15)
                continue;
            edu.stanford.muse.util.Span[] chunks = nerModel.find(entry);
            Map<Short, Map<String,Double>> es = p.getFirst();
            Map<Short, Map<String,Double>> temp = new LinkedHashMap<>();
            for(Short t: es.keySet()) {
                if(es.get(t).size()==0)
                    continue;
                temp.put(t, new LinkedHashMap<>());
                for (String str : es.get(t).keySet())
                    if(es.get(t).get(str)>CUTOFF)
                        temp.get(t).put(str, es.get(t).get(str));
            }
            es = temp;

            short assignedTo = type;
            boolean shown = false;
            //we should not bother about segmentation in the case of OTHER
            if(!(es.containsKey(FeatureDictionary.OTHER) && es.size()==1)) {
                shown = true;
                boolean any;
                if (type!=FeatureDictionary.OTHER && es.containsKey(type) && es.get(type).containsKey(entry))
                    correct++;
                else {
                    any = false;
                    boolean found = false;
                    assignedTo = -1;
                    for (Short t : es.keySet()) {
                        if (es.get(t).containsKey(entry)) {
                            found = true;
                            assignedTo = t;
                            break;
                        }
                        if (es.get(t).size() > 0)
                            any = true;
                    }
                    if (found) {
                        missAssigned++;
                        System.err.println("Wrong assignment miss\nExpected: " + entry + " - " + fullType + " found: " + assignedTo + "\n" + p.getFirst() + "--------");
                    } else if (any) {
                        System.err.println("Segmentation miss\nExpected: " + entry + " - " + fullType + "\n" + p.getFirst() + "--------");
                        missSegmentation++;
                    } else {
                        missNoEvidence++;
                        System.err.println("Not enough evidence for: " + entry + " - " + fullType);
                    }
                }
            }
            if(shown)
                neShown++;
            if(type!=FeatureDictionary.OTHER)
                neShouldShown++;

            if(ne++%100 == 0)
                System.err.println("Done testing on "+ne+" of "+dbpedia.size());
            if(!confMat.containsKey(type))
                confMat.put(type, new LinkedHashMap<>());
            if(!confMat.get(type).containsKey(assignedTo))
                confMat.get(type).put(assignedTo, 0);
            confMat.get(type).put(assignedTo, confMat.get(type).get(assignedTo)+1);

            if(!freqs.containsKey(type))
                freqs.put(type, 0);
            freqs.put(type, freqs.get(type)+1);
        }
        List<Short> allTypes = new ArrayList<>();
        for(Short type: confMat.keySet())
            allTypes.add(type);
        Collections.sort(allTypes);
        allTypes.add((short)-1);
        System.err.println("Tested on "+ne+" entries");
        System.err.println("------------------------");
        String ln = "  ";
        for(Short type: allTypes)
            ln += String.format("%5s",type);
        System.err.println(ln);
        for(Short t1: allTypes){
            ln = String.format("%2s",t1);
            for(Short t2: allTypes) {
                if(confMat.containsKey(t1) && confMat.get(t1).containsKey(t2) && freqs.containsKey(t1))
                    ln += String.format("%5s", new DecimalFormat("#.##").format((double)confMat.get(t1).get(t2)/freqs.get(t1)));//new DecimalFormat("#.##").format((double) confMat.get(t1).get(t2) / freqs.get(t1)));
                else
                    ln += String.format("%5s","-");
            }
            System.err.println(ln);
        }
        System.err.println("------------------------\n");
        double precision = (double)(correct)/(neShown);
        double recall = (double)correct/neShouldShown;
        //miss and misAssigned are number of things we are missing we are missing, but for different reasons, miss is due to segmentation problem, assignment to OTHER; misAssigned is due to wrong type assignment
        //visible = ne - number of entries that are assigned OTHER label and hence visible
        System.err.println("Missed #"+missAssigned+" due to improper assignment\n#"+missSegmentation+"due to improper segmentation\n" +
                "#"+missNoEvidence+" due to single word or no evidence");
        System.err.println("Precision: "+precision+"\nRecall: "+recall);
    }

    public static void main(String[] args){
        test();
    }
}
