package edu.stanford.muse.ner.model.test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.hp.hpl.jena.rdf.model.Seq;
import edu.stanford.muse.Config;
import edu.stanford.muse.ner.model.*;
import edu.stanford.muse.ner.tokenize.CICTokenizer;
import edu.stanford.muse.util.*;
import opennlp.tools.formats.Conll03NameSampleStream;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.FMeasure;
import opennlp.tools.util.featuregen.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

/**
 * Created by vihari on 26/09/16.
 */
public class SequenceModelTest {
    static Log log = LogFactory.getLog(SequenceModelTest.class);

    public static class ParamsCONLL{
        enum TEST{
            testa,testb
        }
        public TEST testType;
        public boolean ignoreSegmentation, onlyMultiWord;

        public ParamsCONLL(){
            testType = TEST.testa;
            ignoreSegmentation = true;
            onlyMultiWord = false;
        }
    }

    static class PerfStats{
        int numFound, numReal, numCorrect;
        int numWrongType;

        float precision, recall, f1;

        @Override
        public String toString(){
            StringBuilder sb = new StringBuilder();
            sb.append("-------------\n");
            sb.append("Found: " + numFound + " -- Total: " + numReal + " -- Correct: " + numCorrect + " -- Missed due to wrong type: " + numWrongType+"\n");
            sb.append("Precision: " + precision+"\n");
            sb.append("Recall: " + recall+"\n");
            sb.append("F1: " + (2 * precision * recall / (precision + recall))+"\n");
            sb.append("------------");
            return sb.toString();
        }
    }

    //we are missing F.C's like F.C. La Valletta
    /**
     * Tested on 28th Jan. 2016 on what is believed to be the testa.dat file of original CONLL.
     * I procured this data-set from a prof's (UMass Prof., don't remember the name) home page where he provided the test files for a homework, guess who topped the assignment :)
     * (So, don't use this data to report results at any serious venue)
     * The results on multi-word names is as follows.
     * Note that the test only considered PERSON, LOCATION and ORG; Also, it does not distinguish between the types because the type assigned by Sequence Labeler is almost always right. And, importantly this will avoid any scuffle over the mapping from fine-grained type to the coarse types.
     *  -------------
     *  Found: 8861 -- Total: 7781 -- Correct: 6675
     *  Precision: 0.75330096
     *  Recall: 0.8578589
     *  F1: 0.80218726
     *  ------------
     * I went through 2691 sentences of which only 200 had any unrecognised entities and identified various sources of error.
     * The sources of missing names are as follows in decreasing order of their contribution (approximately), I have put some examples with the sources. The example phrases are recognized as one chunk with a type.
     * Obviously, this list is not exhaustive, USE IT WITH CAUTION!
     *  1. Bad segmentation -- which is minor for ePADD and depends on training data and principles.
     *     For example: "Overseas Development Minister <PERSON>Lynda Chalker</PERSON>",Czech <PERSON>Daniel Vacek</PERSON>, "Frenchman <PERSON>Cedric Pioline</PERSON>"
     *     "President <PERSON>Nelson Mandela</PERSON>","<BANK>Reserve Bank of India</BANK> Governor <PERSON>Chakravarty Rangarajan</PERSON>"
     *     "Third-seeded <PERSON>Wayne Ferreira</PERSON>",
     *     Hong Kong Newsroom -- we got only Hong Kong, <BANK>Hong Kong Interbank</BANK> Offered Rate, Privately-owned <BANK>Bank Duta</BANK>
     *     [SERIOUS]
     *  2. Bad training data -- since our training data (DBpedia instances) contain phrases like "of Romania" a lot
     *     Ex: <PERSON>Yayuk Basuki</PERSON> of Indonesia, <PERSON>Karim Alami</PERSON> of Morocc
     *     This is also leading to errors like when National Bank of Holand is segmented as National Bank
     *     [SERIOUS]
     *  3. Some unknown names, mostly personal -- we see very weird names in CONLL; Hopefully, we can avoid this problem in ePADD by considering the address book of the archive.
     *     Ex: NOVYE ATAGI, Hans-Otto Sieg, NS Kampfruf, Marie-Jose Perec, Billy Mayfair--Paul Goydos--Hidemichi Tanaki
     *     we miss many (almost all) names of the form "M. Dowman" because of uncommon or unknown last name.
     *  4. Bad segmentation due to limitations of CIC
     *     Ex: Hassan al-Turabi, National Democratic party, Department of Humanitarian affairs, Reserve bank of India, Saint of the Gutters, Queen of the South, Queen's Park
     *  5. Very Long entities -- we refrain from seq. labelling if the #tokens>7
     *     Ex: National Socialist German Workers ' Party Foreign Organisation
     *  6. We are missing OCEANs?!
     *     Ex: Atlantic Ocean, Indian Ocean
     *  7. Bad segments -- why are some segments starting with weird chars like '&'
     *     Ex: Goldman Sachs & Co Wertpapier GmbH -> {& Co Wertpapier GmbH, Goldman Sachs}
     *  8. We are missing Times of London?! We get nothing that contains "Newsroom" -- "Amsterdam Newsroom", "Hong Kong News Room"
     *     Why are we getting "Students of South Korea" instead of "South Korea"?
     *
     * 1/50th on only MWs
     * 13 Feb 13:24:54 BMMModel INFO  - -------------
     * 13 Feb 13:24:54 BMMModel INFO  - Found: 4238 -- Total: 4236 -- Correct: 3242 -- Missed due to wrong type: 358
     * 13 Feb 13:24:54 BMMModel INFO  - Precision: 0.7649835
     * 13 Feb 13:24:54 BMMModel INFO  - Recall: 0.7653447
     * 13 Feb 13:24:54 BMMModel INFO  - F1: 0.765164
     * 13 Feb 13:24:54 BMMModel INFO  - ------------
     *
     * Best performance on testa with [ignore segmentation] and single word with CONLL data is
     * 25 Sep 13:27:03 SequenceModel INFO  - -------------
     * 25 Sep 13:27:03 SequenceModel INFO  - Found: 4117 -- Total: 4236 -- Correct: 3368 -- Missed due to wrong type: 266
     * 25 Sep 13:27:03 SequenceModel INFO  - Precision: 0.8180714
     * 25 Sep 13:27:03 SequenceModel INFO  - Recall: 0.7950897
     * 25 Sep 13:27:03 SequenceModel INFO  - F1: 0.80641687
     * 25 Sep 13:27:03 SequenceModel INFO  - ------------
     **
     * on testa, *not* ignoring segmentation (exact match), any number of words
     * 25 Sep 17:23:14 SequenceModel INFO  - -------------
     * 25 Sep 17:23:14 SequenceModel INFO  - Found: 6006 -- Total: 7219 -- Correct: 4245 -- Missed due to wrong type: 605
     * 25 Sep 17:23:14 SequenceModel INFO  - Precision: 0.7067932
     * 25 Sep 17:23:14 SequenceModel INFO  - Recall: 0.5880316
     * 25 Sep 17:23:14 SequenceModel INFO  - F1: 0.6419659
     * 25 Sep 17:23:14 SequenceModel INFO  - ------------
     *
     * on testa, exact matches, multi-word names
     * 25 Sep 17:28:04 SequenceModel INFO  - -------------
     * 25 Sep 17:28:04 SequenceModel INFO  - Found: 4117 -- Total: 4236 -- Correct: 3096 -- Missed due to wrong type: 183
     * 25 Sep 17:28:04 SequenceModel INFO  - Precision: 0.7520039
     * 25 Sep 17:28:04 SequenceModel INFO  - Recall: 0.7308782
     * 25 Sep 17:28:04 SequenceModel INFO  - F1: 0.74129057
     * 25 Sep 17:28:04 SequenceModel INFO  - ------------
     *
     * With a model that is not trained on CONLL lists
     * On testa, ignoring segmentation, any number of words.
     * Sep 19:22:26 SequenceModel INFO  - -------------
     * 25 Sep 19:22:26 SequenceModel INFO  - Found: 6129 -- Total: 7219 -- Correct: 4725 -- Missed due to wrong type: 964
     * 25 Sep 19:22:26 SequenceModel INFO  - Precision: 0.7709251
     * 25 Sep 19:22:26 SequenceModel INFO  - Recall: 0.6545228
     * 25 Sep 19:22:26 SequenceModel INFO  - F1: 0.7079712
     * 25 Sep 19:22:26 SequenceModel INFO  - ------------
     *
     * testa -- model trained on CONLL, ignore segmenatation, any phrase
     * 26 Sep 20:23:58 SequenceModelTest INFO  - -------------
     * Found: 6391 -- Total: 7219 -- Correct: 4900 -- Missed due to wrong type: 987
     * Precision: 0.7667032
     * Recall: 0.67876434
     * F1: 0.7200588
     * ------------
     *
     * testb -- model trained on CONLL, ignore segmenatation, any phrase
     * 26 Sep 20:24:01 SequenceModelTest INFO  - -------------
     * Found: 2198 -- Total: 2339 -- Correct: 1597 -- Missed due to wrong type: 425
     * Precision: 0.7265696
     * Recall: 0.68277043
     * F1: 0.7039894
     * ------------
     * */
    public static PerfStats testCONLL(SequenceModel seqModel, boolean verbose, ParamsCONLL params) {
        PerfStats stats = new PerfStats();
        try {
            //only multi-word are considered
            boolean onlyMW = params.onlyMultiWord;
            //use ignoreSegmentation=true only with onlyMW=true it is not tested otherwise
            boolean ignoreSegmentation = params.ignoreSegmentation;
            String test = params.testType.toString();
            InputStream in = Config.getResourceAsStream("CONLL" + File.separator + "annotation" + File.separator + test + "spacesep.txt");
            //7==0111 PER, LOC, ORG
            Conll03NameSampleStream sampleStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, in, 7);
            Set<String> correct = new LinkedHashSet<>(), found = new LinkedHashSet<>(), real = new LinkedHashSet<>(), wrongType = new LinkedHashSet<>();
            Multimap<String, String> matchMap = ArrayListMultimap.create();
            Map<String, String> foundTypes = new LinkedHashMap<>(), benchmarkTypes = new LinkedHashMap<>();

            NameSample sample = sampleStream.read();
            CICTokenizer tokenizer = new CICTokenizer();
            while (sample != null) {
                String[] words = sample.getSentence();
                String sent = "";
                for (String s : words)
                    sent += s + " ";
                sent = sent.substring(0, sent.length() - 1);

                Map<String, String> names = new LinkedHashMap<>();
                opennlp.tools.util.Span[] nspans = sample.getNames();
                for (opennlp.tools.util.Span nspan : nspans) {
                    String n = "";
                    for (int si = nspan.getStart(); si < nspan.getEnd(); si++) {
                        if (si < words.length - 1 && words[si + 1].equals("'s"))
                            n += words[si];
                        else
                            n += words[si] + " ";
                    }
                    if (n.endsWith(" "))
                        n = n.substring(0, n.length() - 1);
                    if (!onlyMW || n.contains(" "))
                        names.put(n, nspan.getType());
                }
                Span[] chunks = seqModel.find(sent);
                Map<String, String> foundSample = new LinkedHashMap<>();
                if (chunks != null)
                    for (Span chunk : chunks) {
                        String text = chunk.text;
                        Short type = chunk.type;
                        if (type == NEType.Type.DISEASE.getCode() || type == NEType.Type.EVENT.getCode() || type == NEType.Type.AWARD.getCode())
                            continue;

                        Short coarseType = NEType.getCoarseType(type).getCode();
                        String typeText;
                        if (coarseType == NEType.Type.PERSON.getCode())
                            typeText = "person";
                        else if (coarseType == NEType.Type.PLACE.getCode())
                            typeText = "location";
                        else
                            typeText = "organization";
                        double s = chunk.typeScore;
                        if (s > 0 && (!onlyMW || text.contains(" ")))
                            foundSample.put(text, typeText);
                    }

                Set<String> foundNames = new LinkedHashSet<>();
                Map<String, String> localMatchMap = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : foundSample.entrySet()) {
                    foundTypes.put(entry.getKey(), entry.getValue());
                    boolean foundEntry = false;
                    String foundType = null;
                    for (String name : names.keySet()) {
                        String cname = EmailUtils.uncanonicaliseName(name).toLowerCase();
                        String ek = EmailUtils.uncanonicaliseName(entry.getKey()).toLowerCase();
                        if (cname.equals(ek) || (ignoreSegmentation && ((cname.startsWith(ek + " ") || cname.endsWith(" " + ek) || ek.startsWith(cname + " ") || ek.endsWith(" " + cname))))) {
                            foundEntry = true;
                            foundType = names.get(name);
                            matchMap.put(entry.getKey(), name);
                            localMatchMap.put(entry.getKey(), name);
                            break;
                        }
                    }

                    if (foundEntry) {
                        if (entry.getValue().equals(foundType)) {
                            foundNames.add(entry.getKey());
                            correct.add(entry.getKey());
                        } else {
                            wrongType.add(entry.getKey());
                        }
                    }
                }

                if (verbose) {
                    log.info("CIC tokens: " + tokenizer.tokenizeWithoutOffsets(sent));
                    log.info(chunks);
                    String fn = "Found names:";
                    for (String f : foundNames)
                        fn += f + "[" + foundSample.get(f) + "] with " + localMatchMap.get(f) + "--";
                    if (fn.endsWith("--"))
                        log.info(fn);

                    String extr = "Extra names: ";
                    for (String f : foundSample.keySet())
                        if (!localMatchMap.containsKey(f))
                            extr += f + "[" + foundSample.get(f) + "]--";
                    if (extr.endsWith("--"))
                        log.info(extr);
                    String miss = "Missing names: ";
                    for (String name : names.keySet())
                        if (!localMatchMap.values().contains(name))
                            miss += name + "[" + names.get(name) + "]--";
                    if (miss.endsWith("--"))
                        log.info(miss);

                    String misAssign = "Mis-assigned Types: ";
                    for (String f : foundSample.keySet())
                        if (matchMap.containsKey(f)) {
                            //this can happen since matchMap is a global var. and an entity that is tagged in one place is untagged in other
                            //if (names.get(matchMap.get(f)) == null)
                            //  log.warn("This is not expected: " + f + " in matchMap not found names -- " + names);
                            if (names.get(matchMap.get(f)) != null && !names.get(matchMap.get(f)).equals(foundSample.get(f)))
                                misAssign += f + "[" + foundSample.get(f) + "] Expected [" + names.get(matchMap.get(f)) + "]--";
                        }
                    if (misAssign.endsWith("--"))
                        log.info(misAssign);

                    log.info(sent + "\n------------------");
                }
                for (String name : names.keySet())
                    benchmarkTypes.put(name, names.get(name));

                real.addAll(names.keySet());
                found.addAll(foundSample.keySet());
                sample = sampleStream.read();
            }
            float prec = (float) correct.size() / (float) found.size();
            float recall = (float) correct.size() / (float) real.size();
            if (verbose) {
                log.info("----Correct names----");
                for (String str : correct)
                    log.info(str + " with " + new LinkedHashSet<>(matchMap.get(str)));
                log.info("----Missed names----");
                real.stream().filter(str -> !matchMap.values().contains(str)).forEach(log::info);
                log.info("---Extra names------");
                found.stream().filter(str -> !matchMap.keySet().contains(str)).forEach(log::info);

                log.info("---Assigned wrong type------");
                for (String str : wrongType) {
                    Set<String> bMatches = new LinkedHashSet<>(matchMap.get(str));
                    for (String bMatch : bMatches) {
                        String ft = foundTypes.get(str);
                        String bt = benchmarkTypes.get(bMatch);
                        if (!ft.equals(bt))
                            log.info(str + "[" + ft + "] expected " + bMatch + "[" + bt + "]");
                    }
                }
            }

            stats.f1 = (2 * prec * recall / (prec + recall));
            stats.precision = prec;
            stats.recall = recall;
            stats.numFound = found.size();
            stats.numReal = real.size();
            stats.numCorrect = correct.size();
            stats.numWrongType = wrongType.size();
            log.info(stats.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stats;
    }

    static boolean compareSpan(Span obj, Span tgt){
        if(obj.text.equals(tgt.text) &&
                obj.type == tgt.type &&
                obj.start == tgt.start &&
                obj.end == tgt.end)
            return true;
        return false;
    }

    //makes sure everything in target is in vals
    static boolean compareSpans(Span[] tgts, Span[] vals){
        return !Stream.of(tgts).filter(t->
            !Stream.of(vals).filter(v -> compareSpan(t, v)).findAny().isPresent()
        ).findAny().isPresent();
    }

    static String debugText(String content, Span[] found, Span[] expected){
        StringBuilder sb = new StringBuilder();
        sb.append("\nContent: "+content+"\n");
        sb.append("Found: ");
        Stream.of(found).map(sp->"{"+sp.text+"["+sp.start+","+sp.end+") "+NEType.getTypeForCode(sp.type)+"}").forEach(sb::append);
        sb.append("\nExpected: ");
        Stream.of(expected).map(sp -> "{" + sp.text + "[" + sp.start + ", " + sp.end + ") " + NEType.getTypeForCode(sp.type) + "}").forEach(sb::append);
        return sb.toString();
    }

    //checks if the model is working sensibly and not missing at least the obvious ones.
    public static void testCommon(SequenceModel model) {
        if(model!=null) {
            List<Pair<String, Span[]>> test = new ArrayList<>();
            //test.add(new Pair<>("Bob", new Span[]{new Span("Bob", 0, 3, NEType.Type.PERSON.getCode())}));
            test.add(new Pair<>("Are you back from Maine: how is your sister?",
                    new Span[]{new Span("Maine", 18, 23, NEType.Type.PLACE.getCode())}));
            test.add(new Pair<>("One piece of business: the edition from Tamarind Institute has not arrived here. " +
                    "Do you know of some delay, or should I just get on the matter my self.",
                    new Span[]{new Span("Tamarind Institute", 40, 58, NEType.Type.UNIVERSITY.getCode())}));
            test.add(new Pair<>("I think you knew that ND was to publish the Duncan/Levertov letters,\n" +//69
                    "but when the book got too big they were happy enough not to do it. Last week\n" + //146
                    "the volume was accepted by the editorial board at Stanford Univ. Press. It\n" + //221
                    "all comes out as a fine collaboration with Al Gelpi, half the letters here,\n" +//297
                    "half the letters at Stanford. Out in about a year, so I am told. To replace\n" +//373
                    "the ND book I have given them another book called \"Robert Duncan's Ezra\n" +   //445
                    "Pound,\" which has both sides of the correspondence in a narrative with other\n" +//522
                    "docs. Plus poems and unpublished essays. Then a new edtion of Letters, RD\n" + //598
                    "title, from a small press in St Louis. I've finished the Olson/Duncan\n" +     //668
                    "letters, but am now struggling with the transcriptions of RD lectures of CO.\n" +//745
                    "They so resist being reading texts. I'll have more on that soon. Letters and\n" +//822
                    "Lectures to go to Wisconsin. Or, the snow at Christmas kept me off the\n" +
                    "streets and at my desk. In that sense it was a moral snow.\n" +
                    "If you're in Buffalo could you stand a stop after work?\n" +
                    "My best, ",
                    new Span[]{
                            new Span("Duncan", 44, 50, NEType.Type.PERSON.getCode()),
                            //new Span("Levertov", 51, 59, NEType.Type.PERSON.getCode()),
                            //gets this as PERSON, unfortunately
                            //new Span("Stanford Univ", 196, 209, NEType.Type.PERSON.getCode()),
                            new Span("Al Gelpi", 264, 272, NEType.Type.PERSON.getCode()),
                            new Span("Stanford", 317, 325, NEType.Type.UNIVERSITY.getCode()),
                            new Span("Robert Duncan", 424, 437, NEType.Type.PERSON.getCode()),
                            new Span("Ezra Pound", 440, 450, NEType.Type.PERSON.getCode()),
                            new Span("St Louis", 625, 633, NEType.Type.BUILDING.getCode()),
                            new Span("Olson", 653, 658, NEType.Type.PERSON.getCode()),
                            new Span("Duncan", 659, 665, NEType.Type.PERSON.getCode()),
                            new Span("Wisconsin", 838, 847, NEType.Type.PLACE.getCode())
                    }));
            test.add(new Pair<>("We are traveling to Vietnam the next summer and will come to New York (NYC) soon",
                    new Span[]{
                            new Span("Vietnam", 20, 27,NEType.Type.PLACE.getCode()),
                            new Span("New York", 61, 69, NEType.Type.PLACE.getCode()),
                            new Span("NYC", 71, 74, NEType.Type.PLACE.getCode())
                    }));
            test.add(new Pair<>("Mr. HariPrasad was present.",
                    new Span[]{new Span("Mr. HariPrasad", 0, 14, NEType.Type.PERSON.getCode())}));
            test.add(new Pair<>("A book named Information Retrieval by Christopher Manning",
                    new Span[]{new Span("Christopher Manning", 38, 57, NEType.Type.PERSON.getCode())}));
            test.add(new Pair<>("Keane Inc",
                    new Span[]{new Span("Keane Inc", 0, 9, NEType.Type.COMPANY.getCode())}));
            test.add(new Pair<>("I saw him last on Tuesday. You do know that he has Williams Syndrome, right?. " +
                    "I took him to Wimbledon Open the last weekend.",
                    new Span[]{new Span("Williams Syndrome", 51, 68, NEType.Type.DISEASE.getCode())}));
            //misses Wimbledon completely
            //new Span("Wimbledon", 78 + 14, 78 + 14 + 9, NEType.Type.EVENT.getCode())}));
            test.add(new Pair<>("National Bank some. National Kidney Foundation some . University Commencement.\n"+ //79
                    "Address of Amuse Labs: OUT HOUSE, 19/1, Ramchandra Kripa, Mahishi Road, Malmaddi Dharwad. " +
                    "Address of US stay. 483, Fulton Street, Palo Alto",
                    new Span[]{
                            new Span("National Bank", 0, 13, NEType.Type.ORGANISATION.getCode()),
                            new Span("National Kidney Foundation", 20, 46, NEType.Type.COMPANY.getCode()),
                            //new Span("Amuse Labs", , , NEType.Type.COMPANY.getCode()),
                            new Span("Mahishi Road", 137, 149, NEType.Type.ROAD.getCode()),
                            //new Span("Dharwad", , , NEType.Type.PLACE.getCode()),
                            new Span("Fulton Street", 194, 207, NEType.Type.PLACE.getCode()),
                            new Span("Palo Alto", 209, 218, NEType.Type.PLACE.getCode()),
                    }));

            test.stream().forEach(p -> {
                Span[] found = model.find(p.first);
                assertTrue(debugText(p.first, found, p.getSecond()), compareSpans(p.getSecond(), found));
            });
        }
    }

    @Test
    public void test() {
        SequenceModel model;
        try {
            model = SequenceModel.loadModelFromRules(SequenceModel.RULES_DIRNAME);
            testCommon(model);

            ParamsCONLL params = new ParamsCONLL();

            params.ignoreSegmentation = true;
            params.onlyMultiWord = false;
            params.testType = params.testType.testa;
            PerfStats stats = testCONLL(model, false, params);
            assertTrue("Severe drop in F1 score with " + params + "!!!\n" + stats.toString(), 0.72 - stats.f1 < 0.05);
            assertTrue("Severe drop in precision with " + params + "!!!\n" + stats.toString(), 0.75 - stats.precision < 0);
            assertTrue("Severe drop in recall with " + params + "!!!\n" + stats.toString(), 0.65 - stats.recall < 0);

            params.testType = params.testType.testb;
            stats = testCONLL(model, false, params);
            assertTrue("Severe drop in F1 score with " + params + "!!!\n" + stats.toString(), 0.70 - stats.f1 < 0.05);
            assertTrue("Severe drop in precision with " + params + "!!!\n" + stats.toString(), 0.65 - stats.precision < 0);
            assertTrue("Severe drop in recall with " + params + "!!!\n" + stats.toString(), 0.65 - stats.recall < 0.05);
        } catch (IOException e) {
            log.error("Could not oad model from: " + SequenceModel.MODEL_FILENAME, e);
        }

    }

    static void testParams(){
        float alphas[] = new float[]{0, 1.0f/50, 1.0f/5, 1.0f/2, 1.0f, 5f};
        int emIters[] = new int[]{9};//new int[]{0,2,5,7,9};
        int numIter = 1;
        String expFolder = "experiment";
        String resultsFile = System.getProperty("user.home")+File.separator+"epadd-settings"+File.separator+"paramResults.txt";
        //flush the previous results
        try{new FileOutputStream(resultsFile);}catch(IOException e){e.printStackTrace();}
        String oldName = SequenceModel.MODEL_FILENAME;
        for(float alpha: alphas) {
            SequenceModel.MODEL_FILENAME = "ALPHA_"+alpha+"-"+oldName;
            String modelFile = expFolder + File.separator + "Iter_" + emIters[emIters.length - 1] + SequenceModel.MODEL_FILENAME;
            try {
                if (!new File(modelFile).exists()) {
                    PrintStream def = System.out;
                    System.setOut(new PrintStream(new FileOutputStream(resultsFile, true)));
                    System.out.println("------------------\n" +
                            "Alpha fraction: " + alpha + " -- # Iterations: " + numIter);
                    SequenceModel.train(alpha, numIter);
                    System.setOut(def);
                }
                for (int emIter : emIters) {
                    modelFile = expFolder + File.separator + "Iter_" + emIter + "-" + SequenceModel.MODEL_FILENAME;
                    SequenceModel seqModel = SequenceModel.loadModel(modelFile);
                    PrintStream def = System.out;
                    System.setOut(new PrintStream(new FileOutputStream(resultsFile, true)));
                    System.out.println("------------------\n" +
                            "Alpha fraction: " + alpha + " -- Iteration: " + (emIter + 1));
                    SequenceModelTest.PerfStats stats = SequenceModelTest.testCONLL(seqModel, false, new SequenceModelTest.ParamsCONLL());
                    System.out.println(stats.toString());
                    System.setOut(def);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SequenceModel.MODEL_FILENAME = oldName;
    }

    //samples [fraction] fraction of entries from dictionary supplied and splices the supplied dict
    private static Pair<Map<String,String>,Map<String,String>> split(Map<String,String> dict, float fraction){
        Map<String,String> dict1 = new Util.CaseInsensitiveMap<>(), dict2 = new Util.CaseInsensitiveMap<>();
        Random rand = new Random();
        for(String str: dict.keySet()){
            if(rand.nextFloat()<fraction){
                dict1.put(str, dict.get(str));
            }else{
                dict2.put(str, dict.get(str));
            }
        }
        System.err.println("Sliced " + dict.size() + " entries into " + dict1.size() + " and " + dict2.size());
        return new Pair<>(dict1, dict2);
    }

    public static void testOnDBpedia(NERModel nerModel, Map<String,String> dbpediaTestSplit){
        //when testing remember to change
        //1. lookup method, disable the lookup
        System.err.println("DBpedia scoring check starts");
        //NOther == Not OTHER
        //number of things shown (NON-OTHER) and number of things that should be shown
        int ne = 0, neShown = 0, neShouldShown = 0;
        //number of entries assigned to wrong type and number missed because they are assigned OTHER
        int missAssigned=0, missSegmentation = 0, missNoEvidence = 0;
        int correct = 0;
        //these are the entries which are not completely tagged as OTHER by NER, but may have some segments that are not OTHER, hence visible
        double CUTOFF = nerModel instanceof HMMModel?-100:1E-10;
        Map<Short,Map<Short,Integer>> confMat = new LinkedHashMap<>();
        Map<Short, Integer> freqs = new LinkedHashMap<>();
        String[] badSuffixTypes = new String[]{"MusicalWork|Work","Sport", "Film|Work", "Band|Group|Organisation", "Food",
                "EthnicGroup","RadioStation|Broadcaster|Organisation", "MeanOfTransportation", "TelevisionShow|Work",
                "Play|WrittenWork|Work","Language", "Book|WrittenWork|Work","Genre|TopicalConcept", "InformationAppliance|Device",
                "SportsTeam|Organisation", "Eukaryote|Species","Software|Work", "TelevisionEpisode|Work", "Comic|WrittenWork|Work",
                "Mayor", "Website|Work", "Cartoon|Work"
        };
        ol:
        for(String entry: dbpediaTestSplit.keySet()){
            if(!entry.contains(" "))
                continue;
            String fullType = dbpediaTestSplit.get(entry);
            Short type = NEType.parseDBpediaType(dbpediaTestSplit.get(entry)).getCode();

            if(fullType.equals("Agent"))
                type = NEType.Type.PERSON.getCode();
            else
                for (String bst: DBpediaUtils.ignoreDBpediaTypes)
                    if(fullType.contains(bst))
                        continue ol;

            entry = EmailUtils.uncanonicaliseName(entry);
            if(entry.length()>=15)
                continue;
            Span[] spans = nerModel.find(entry);
            Map<Short, Map<String,Float>> es = new LinkedHashMap<>();
            for(Span sp: Arrays.asList(spans)) {
                if(!es.containsKey(sp.type)) es.put(sp.type, new LinkedHashMap<>());
                es.get(sp.type).put(sp.text, sp.typeScore);
            }
            Map<Short, Map<String,Float>> temp = new LinkedHashMap<>();

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
            if(!(es.containsKey(NEType.Type.OTHER.getCode()) && es.size()==1)) {
                shown = true;
                boolean any;
                Short bt = -1;
                if(es.size()>0)
                    bt = es.entrySet().stream()
                            .map(e->new Pair<>(e.getKey(),
                                    e.getValue().keySet().stream().mapToInt(k->k.length()).max().orElse(0)))
                            .max((e1, e2)->Double.compare(e1.getSecond(), e2.getSecond()))
                            .map(Pair::getFirst).get();
                if (!type.equals(NEType.Type.OTHER.getCode()) && bt==type) //es.containsKey(type) && es.get(type).containsKey(entry))
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
                        //System.err.println("Wrong assignment miss\nExpected: " + entry + " - " + fullType + " found: " + assignedTo + "\n" + "--------");
                    } else if (any) {
                        //System.err.println("Segmentation miss\nExpected: " + entry + " - " + fullType + "\n" + "--------");
                        missSegmentation++;
                    } else {
                        missNoEvidence++;
                        //System.err.println("Not enough evidence for: " + entry + " - " + fullType + " found: "+Stream.of(spans).collect(Collectors.toSet()));
                    }
                }
            }
            if(shown)
                neShown++;
            if(type!= NEType.Type.OTHER.getCode())
                neShouldShown++;


            if(ne++%100 == 0)
                System.err.println("Done testing on " + ne + " of " + dbpediaTestSplit.size());
            if(!confMat.containsKey(type))
                confMat.put(type, new LinkedHashMap<>());
            if(!confMat.get(type).containsKey(assignedTo))
                confMat.get(type).put(assignedTo, 0);
            confMat.get(type).put(assignedTo, confMat.get(type).get(assignedTo)+1);

            if(!freqs.containsKey(type))
                freqs.put(type, 0);
            freqs.put(type, freqs.get(type)+1);
        }
        List<Short> allTypes = confMat.keySet().stream().collect(Collectors.toList());
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
                    ln += String.format("%5s", new DecimalFormat("#.##").format((double)confMat.get(t1).get(t2)/freqs.get(t1)));
                else
                    ln += String.format("%5s","-");
            }
            System.err.println(ln);
        }
        System.err.println("------------------------\n");
        double averagePrecision = allTypes.stream().filter(t->t>=0 && t!=NEType.Type.OTHER.getCode())
                .mapToDouble(t1->{
                    if (confMat.containsKey(t1) && confMat.get(t1).containsKey(t1) && freqs.containsKey(t1))
                        return (double)confMat.get(t1).get(t1)/freqs.get(t1);
                    else return 0;
                })
                .average().getAsDouble();
        double precision = (double)(correct)/(neShown);
        double recall = (double)correct/neShouldShown;
        //miss and misAssigned are number of things we are missing we are missing, but for different reasons, miss is due to segmentation problem, assignment to OTHER; misAssigned is due to wrong type assignment
        //visible = ne - number of entries that are assigned OTHER label and hence visible
        System.err.println("Missed #"+missAssigned+" due to improper assignment\n#"+missSegmentation+"due to improper segmentation\n" +
                "#"+missNoEvidence+" due to single word or no evidence");
        System.err.println("Precision: "+precision+"\nRecall: "+recall+"\nF1:"+(2*precision*recall)/(precision+recall) + "\nAverage Precision: " + averagePrecision);
    }

    static void writeToDir(Pair<Map<String,String>,Map<String,String>> trainTest, String dirName){
        try {
            FileOutputStream fos = new FileOutputStream(dirName + File.separator + "trainTestDBpedia.ser.gz");
            GZIPOutputStream gos = new GZIPOutputStream(fos);
            ObjectOutputStream oos = new ObjectOutputStream(gos);
            oos.writeObject(trainTest);
            oos.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    static Pair<Map<String,String>,Map<String,String>> readFromDir(String dirName){
        try {
            //the buffer size can be much higher than default 512 for GZIPInputStream
            ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(dirName+File.separator+"trainTestDBpedia.ser.gz")));
            Pair<Map<String,String>,Map<String,String>> model = (Pair<Map<String,String>,Map<String,String>>) ois.readObject();
            ois.close();
            return model;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static void testOnDbpediaHelper(){
        Class clazz = ARIModel.class;
        NERModel model;
        try {
            String modelName = "dbpediaTest"+ File.separator + clazz.getName() + "-80.ser.gz";
            model = (NERModel) clazz.getDeclaredMethod("loadModel", String.class).invoke(null, modelName);
            Pair<Map<String,String>,Map<String,String>> trainTestSplit;
            trainTestSplit = readFromDir(Config.SETTINGS_DIR+File.separator+"dbpediaTest");
            if(trainTestSplit == null) {
                trainTestSplit = split(DBpediaUtils.readDBpedia(), 0.8f);
                writeToDir(trainTestSplit,Config.SETTINGS_DIR+File.separator+"dbpediaTest");
            }

            if(model == null) {
                model = (NERModel) clazz.getDeclaredMethod("train", Map.class).invoke(null, trainTestSplit.first);
                if(model instanceof SequenceModel || model instanceof ARIModel)
                    SequenceModel.writeModelAsRules((SequenceModel) model, Config.SETTINGS_DIR + File.separator + "ARI_rules");
                Util.writeObjectAsSerGZ(model, Config.SETTINGS_DIR + File.separator + modelName);
            }
            testOnDBpedia(model,trainTestSplit.second);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    static void testOpenNLP(ObjectStream<NameSample> sampleStream, AdaptiveFeatureGenerator[] thisFeatureGens, String modelName){
        if (thisFeatureGens==null)
            thisFeatureGens = new AdaptiveFeatureGenerator[]{};

        InputStream modelIn = Config.getResourceAsStream(modelName);
        try {
            List<AdaptiveFeatureGenerator> featureGens = new ArrayList<>();
            Stream.of(getDefaultFeatureGens()).forEach(featureGens::add);
            Stream.of(thisFeatureGens).forEach(featureGens::add);
            AdaptiveFeatureGenerator featureGenerator = new CachedFeatureGenerator(
                    featureGens.toArray(new AdaptiveFeatureGenerator[featureGens.size()])
            );

            TokenNameFinderModel nerModel = new TokenNameFinderModel(modelIn);
            NameFinderME finder = new NameFinderME(nerModel, featureGenerator, NameFinderME.DEFAULT_BEAM_SIZE);

            FMeasure result = new FMeasure();
            int totalFound = 0, totalTarget = 0;
            try {
                NameSample sample;
                while ((sample = sampleStream.read()) != null) {
                    opennlp.tools.util.Span[] found = finder.find(sample.getSentence());
                    result.updateScores(sample.getNames(), found);
                    totalFound += found.length;
                    totalTarget += sample.getNames().length;
//                    String[] tokens = sample.getSentence();
//                    System.err.println(
//                            Stream.of(found)
//                            .map(sp->Arrays.asList(Arrays.copyOfRange(tokens, sp.getStart(), sp.getEnd()))+", " + sp.getType())
//                                    .collect(Collectors.toList())
//                    );
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("\n\n" + result.toString());
            System.out.println("Found #"+totalFound+" names of #"+totalTarget+" correct #"+result.getPrecisionScore()*totalFound);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try{
                if(modelIn != null)
                    modelIn.close();
            } catch (IOException e){

            }
        }

    }

    static AdaptiveFeatureGenerator[] getDefaultFeatureGens(){
        return new AdaptiveFeatureGenerator[]{
                new WindowFeatureGenerator(new TokenFeatureGenerator(), 2, 2),
                new WindowFeatureGenerator(new TokenClassFeatureGenerator(true), 2, 2),
                new OutcomePriorFeatureGenerator(),
                new PreviousMapFeatureGenerator(),
                new BigramNameFeatureGenerator(),
                new SentenceFeatureGenerator(true, false)
        };
    }

    static String stats(ObjectStream<NameSample> stream){
        NameSample sample;
        int numTokens = 0, numNames = 0;
        try {
            while((sample=stream.read())!=null) {
                numNames += sample.getNames().length;
                numTokens += sample.getSentence().length;
            }
            stream.reset();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "#" + numTokens + " tokens and #" + numNames;
    }

    static void trainOpennlpModel(ObjectStream<NameSample> sampleStream, AdaptiveFeatureGenerator[] thisFeatureGens, String modelName){
        assert modelName!=null: "Model name can't be null";
        if (thisFeatureGens==null)
            thisFeatureGens = new AdaptiveFeatureGenerator[]{};

        try {
            List<AdaptiveFeatureGenerator> featureGens = new ArrayList<>();
            Stream.of(getDefaultFeatureGens()).forEach(featureGens::add);
            Stream.of(thisFeatureGens).forEach(featureGens::add);
            AdaptiveFeatureGenerator featureGenerator = new CachedFeatureGenerator(
                    featureGens.toArray(new AdaptiveFeatureGenerator[featureGens.size()])
            );

            TokenNameFinderModel model = NameFinderME.train("en", null, sampleStream,
                    TrainingParameters.defaultParams(),
                    featureGenerator,
                    Collections.emptyMap());

            BufferedOutputStream modelOut = null;
            try {
                modelOut = new BufferedOutputStream(new FileOutputStream(
                        new File(String.join(File.separator, new String[]{System.getProperty("user.home"), "epadd-settings", modelName})
                        )));
                model.serialize(modelOut);
            } finally {
                if (modelOut != null)
                    modelOut.close();
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
    On testa dataset
     =======================
     Performance with Gazette lookup feature
     Precision: 0.9116753472222222
     Recall: 0.8368525896414343
     F-Measure: 0.8726630660573327
     =======================
     Performance with Spelling rules feature
     Precision: 0.9148258283772303
     Recall: 0.8579681274900398
     F-Measure: 0.885485197368421
     =======================
     =======================
     Without any special features
     Precision: 0.8982166159199652
     Recall: 0.8227091633466136
     F-Measure: 0.8588064046579331
     =======================

     On testb dataset
     =======================
     Performance with Gazette lookup feature
     Precision: 0.8779056646355224
     Recall: 0.7882472137791287
     F-Measure: 0.8306641042067051
     =======================
     =======================
     Performance with Spelling rules feature
     Precision: 0.8872743284896522
     Recall: 0.8166160081053698
     F-Measure: 0.850480109739369
     =======================
     =======================
     Without any special features
     Precision: 0.8692587539790814
     Recall: 0.7746707193515704
     F-Measure: 0.8192435444123004
     =======================

     * */
    static void sameDomainTest(){
        String test = "testb";
        InputStream conllTestIn = Config.getResourceAsStream("CONLL" + File.separator + "annotation" + File.separator + test + "spacesep.txt");
        InputStream conllTrainIn = Config.getResourceAsStream("CONLL" + File.separator + "annotation" + File.separator + "trainspacesep.txt");
        Conll03NameSampleStream conllTestStream, conllTrainStream;
        conllTestStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, conllTestIn, 7);
        conllTrainStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, conllTrainIn, 7);

        AdaptiveFeatureGenerator[] lookupFeature = new AdaptiveFeatureGenerator[]{
                new OpenNLPNERFeatureGens.GazetteLookupFeatureGenerator()
        };
        AdaptiveFeatureGenerator[] ruleFeature = new AdaptiveFeatureGenerator[]{
                new OpenNLPNERFeatureGens.SpellingRuleFeatureGenerator()
        };

        System.out.println("===============ON CONLL==================");

        String modelName = String.join(File.separator, new String[]{"opennlp", "conll", "en-ner-gazette.bin"});
        //trainOpennlpModel(conllTrainStream, lookupFeature, modelName);
        System.out.println("=======================\n\nPerformance with Gazette lookup feature");
        testOpenNLP(conllTestStream, lookupFeature, modelName);
        System.out.println("=======================");
        conllTestIn = Config.getResourceAsStream("CONLL" + File.separator + "annotation" + File.separator + test + "spacesep.txt");
        conllTrainIn = Config.getResourceAsStream("CONLL" + File.separator + "annotation" + File.separator + "trainspacesep.txt");
        conllTestStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, conllTestIn, 7);
        conllTrainStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, conllTrainIn, 7);

        modelName = String.join(File.separator, new String[]{"opennlp", "conll", "en-ner-rules.bin"});
        //trainOpennlpModel(conllTrainStream, ruleFeature, modelName);
        System.out.println("=======================\n\nPerformance with Spelling rules feature");
        testOpenNLP(conllTestStream, ruleFeature, modelName);
        System.out.println("=======================");

        conllTestIn = Config.getResourceAsStream("CONLL" + File.separator + "annotation" + File.separator + test + "spacesep.txt");
        conllTrainIn = Config.getResourceAsStream("CONLL" + File.separator + "annotation" + File.separator + "trainspacesep.txt");
        conllTestStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, conllTestIn, 7);
        conllTrainStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, conllTrainIn, 7);

        modelName = String.join(File.separator, new String[]{"opennlp", "conll", "en-ner.bin"});
        //trainOpennlpModel(conllTrainStream, null, modelName);
        System.out.println("=======================\n\nWithout any special features");
        testOpenNLP(conllTestStream, null, modelName);
        System.out.println("=======================");

        String okeTestIn = Config.SETTINGS_DIR + File.separator + "oke-challenge-2016/evaluation-data/task1/evaluation-dataset-task1.ttl";
        String okeTrainIn = Config.SETTINGS_DIR + File.separator + "oke-challenge-2016/GoldStandard_sampleData/task1/dataset_task_1.ttl";
        NIFNameSampleStream okeTestStream = new NIFNameSampleStream(okeTestIn);
        NIFNameSampleStream okeTrainStream = new NIFNameSampleStream(okeTrainIn);

        System.out.println("===============ON OKE CHALLENGE 2016==================");

        modelName = String.join(File.separator, new String[]{"opennlp", "oke", "en-ner-gazette.bin"});
        trainOpennlpModel(okeTrainStream, lookupFeature, modelName);
        System.out.println("=======================\n\nPerformance with Gazette lookup feature");
        testOpenNLP(okeTestStream, lookupFeature, modelName);
        System.out.println("=======================");
        okeTestStream = new NIFNameSampleStream(okeTestIn);
        okeTrainStream = new NIFNameSampleStream(okeTrainIn);

        modelName= String.join(File.separator, new String[]{"opennlp", "oke", "en-ner-rules.bin"});
        trainOpennlpModel(okeTrainStream, ruleFeature, modelName);
        System.out.println("=======================\n\nPerformance with Spelling rules feature");
        testOpenNLP(okeTestStream, ruleFeature, modelName);
        System.out.println("=======================");
        okeTestStream = new NIFNameSampleStream(okeTestIn);
        okeTrainStream = new NIFNameSampleStream(okeTrainIn);

        modelName = String.join(File.separator, new String[]{"opennlp", "oke", "en-ner.bin"});
        trainOpennlpModel(okeTrainStream, null, modelName);
        System.out.println("=======================\n\nWithout any special features");
        testOpenNLP(okeTestStream, null, modelName);
        System.out.println("=======================");

        System.out.println("===============ON NER WEB ILLINOIS==================");

        InputStream nwiTestIn = Config.getResourceAsStream("NERWeb" + File.separator +  "test.dat");
        InputStream nwiTrainIn = Config.getResourceAsStream("NERWeb" + File.separator + "train.dat");
        CustomConll03NameSampleStream nwiTrainStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTrainIn, 7);
        CustomConll03NameSampleStream nwiTestStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTestIn, 7);

        modelName = String.join(File.separator, new String[]{"opennlp", "nwi", "en-ner-gazette.bin"});
        trainOpennlpModel(nwiTrainStream, lookupFeature, modelName);
        System.out.println("=======================\n\nPerformance with Gazette lookup feature");
        testOpenNLP(nwiTestStream, lookupFeature, modelName);
        System.out.println("=======================");

        nwiTestIn = Config.getResourceAsStream("NERWeb" + File.separator +  "test.dat");
        nwiTrainIn = Config.getResourceAsStream("NERWeb" + File.separator + "train.dat");
        nwiTrainStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTrainIn, 7);
        nwiTestStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTestIn, 7);

        modelName = String.join(File.separator, new String[]{"opennlp", "nwi", "en-ner-rules.bin"});
        trainOpennlpModel(nwiTrainStream, ruleFeature, modelName);
        System.out.println("=======================\n\nPerformance with Spelling rules feature");
        testOpenNLP(nwiTestStream, ruleFeature, modelName);
        System.out.println("=======================");

        nwiTestIn = Config.getResourceAsStream("NERWeb" + File.separator +  "test.dat");
        nwiTrainIn = Config.getResourceAsStream("NERWeb" + File.separator + "train.dat");
        nwiTrainStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTrainIn, 7);
        nwiTestStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTestIn, 7);

        modelName = String.join(File.separator, new String[]{"opennlp", "nwi", "en-ner.bin"});
        trainOpennlpModel(nwiTrainStream, null, modelName);
        System.out.println("=======================\n\nWithout any special features");
        testOpenNLP(nwiTestStream, null, modelName);
        System.out.println("=======================");
    }

    public static void crossDomainTest(){
        String test = "testa";
        InputStream conllTestIn = Config.getResourceAsStream("CONLL" + File.separator + "annotation" + File.separator + test + "spacesep.txt");
        InputStream conllTrainIn = Config.getResourceAsStream("CONLL" + File.separator + "annotation" + File.separator + "trainspacesep.txt");
//        Conll03NameSampleStream conllTestStream, conllTrainStream;
//        conllTestStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, conllTestIn, 7);
//        conllTrainStream = new Conll03NameSampleStream(Conll03NameSampleStream.LANGUAGE.EN, conllTrainIn, 7);

        AdaptiveFeatureGenerator[] lookupFeature = new AdaptiveFeatureGenerator[]{
                new OpenNLPNERFeatureGens.GazetteLookupFeatureGenerator()
        };
        AdaptiveFeatureGenerator[] ruleFeature = new AdaptiveFeatureGenerator[]{
                new OpenNLPNERFeatureGens.SpellingRuleFeatureGenerator()
        };

        String gazzModelName = String.join(File.separator, new String[]{"opennlp", "conll", "en-ner-gazette.bin"});
        String ruleModelName = String.join(File.separator, new String[]{"opennlp", "conll", "en-ner-rules.bin"});
        String modelName = String.join(File.separator, new String[]{"opennlp", "conll", "en-ner.bin"});

        System.out.println("===============ON OKE CHALLENGE 2016==================");

        String OKE_TEST = "oke-challenge-2016/GoldStandard_sampleData/task1/dataset_task_1.ttl";
        String OKE_TEST2 = "oke-challenge-2016/evaluation-data/task1/evaluation-dataset-task1.ttl";
        String okeTestIn = Config.SETTINGS_DIR + File.separator + OKE_TEST;
        String okeTestIn2 = Config.SETTINGS_DIR + File.separator + OKE_TEST2;
        NIFNameSampleStream okeTestStream = new NIFNameSampleStream(okeTestIn);
        NIFNameSampleStream okeTestStream2 = new NIFNameSampleStream(okeTestIn2);
        final String NWI_TEST = "all.dat";
        InputStream nwiTestIn = Config.getResourceAsStream("NERWeb" + File.separator +  NWI_TEST);
        CustomConll03NameSampleStream nwiTestStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTestIn, 7);

        System.out.println("=======================\n\nPerformance with Gazette lookup feature");
        System.out.println("------Gold standard---------");
        testOpenNLP(okeTestStream, lookupFeature, gazzModelName);
        System.out.println("------Evaluation data--------");
        testOpenNLP(okeTestStream2, lookupFeature, gazzModelName);
        System.out.println("=======================");
        okeTestStream = new NIFNameSampleStream(okeTestIn);
        okeTestStream2 = new NIFNameSampleStream(okeTestIn2);

        System.out.println("=======================\n\nPerformance with Spelling rules feature");
        System.out.println("------Gold standard---------");
        testOpenNLP(okeTestStream, ruleFeature, ruleModelName);
        System.out.println("------Evaluation data--------");
        testOpenNLP(okeTestStream2, ruleFeature, ruleModelName);
        System.out.println("=======================");
        okeTestStream = new NIFNameSampleStream(okeTestIn);
        okeTestStream2 = new NIFNameSampleStream(okeTestIn2);

        System.out.println("=======================\n\nWithout any special features");
        System.out.println("------Gold standard---------");
        testOpenNLP(okeTestStream, null, modelName);
        System.out.println("------Evaluation data--------");
        testOpenNLP(okeTestStream2, null, modelName);
        System.out.println("=======================");


        System.out.println("===============ON NER WEB ILLINOIS==================");

        System.out.println("=======================\n\nPerformance with Gazette lookup feature");
        testOpenNLP(nwiTestStream, lookupFeature, gazzModelName);
        System.out.println("=======================");

        nwiTestIn = Config.getResourceAsStream("NERWeb" + File.separator +  NWI_TEST);
        nwiTestStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTestIn, 7);

        System.out.println("=======================\n\nPerformance with Spelling rules feature");
        testOpenNLP(nwiTestStream, ruleFeature, ruleModelName);
        System.out.println("=======================");

        nwiTestIn = Config.getResourceAsStream("NERWeb" + File.separator +  NWI_TEST);
        nwiTestStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTestIn, 7);

        System.out.println("=======================\n\nWithout any special features");
        testOpenNLP(nwiTestStream, null, modelName);
        System.out.println("=======================");
    }

    public static void bootstrap(){
        try {
            SequenceModel model = SequenceModel.loadModelFromRules(SequenceModel.RULES_DIRNAME);
            FileWriter fw = new FileWriter(new File(Config.SETTINGS_DIR + File.separator + "NERWeb" + File.separator + "weak_train.txt"));

            final String NWI_TEST = "all.dat";
            InputStream nwiTestIn = Config.getResourceAsStream("NERWeb" + File.separator + NWI_TEST);
            ObjectStream<NameSample> nwiTestStream = new IllinoisHTMLSampleStream(Config.getResourceAsStream("datasets/IllinoisHTML/train.txt"));
            //CustomConll03NameSampleStream nwiTestStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTestIn, 7);
            NameSample sample;
            while ((sample = nwiTestStream.read()) != null) {
                String[] tokens = sample.getSentence();
                String text = String.join(" ", tokens);
                Span[] spans = model.find(text);
                String[] types = new String[tokens.length];
                for (int ti = 0; ti < tokens.length; ti++)
                    types[ti] = "O";

                Map<String, Short> names = new LinkedHashMap<>();
                Stream.of(spans).filter(sp-> sp.typeScore>1E-10).forEach(sp -> names.put(sp.getText(), sp.getType()));
                for (int ti = 0; ti < tokens.length; ti++) {
                    int from = ti, to = Math.min(ti + 10, tokens.length);
                    for (int tj = to - 1; tj >= from; tj--) {
                        String cand = String.join(" ", Arrays.copyOfRange(tokens, ti, tj + 1));
                        Short type = names.get(cand);
                        if (type != null) {
                            NEType.Type t = NEType.getCoarseType(type);
                            String ct = null;
                            if (t == NEType.Type.PERSON)
                                ct = "PER";
                            else if (t == NEType.Type.PLACE)
                                ct = "LOC";
                            else if (t == NEType.Type.ORGANISATION)
                                ct = "ORG";

                            if (ct != null) {
                                for (int i = ti; i <= tj; i++)
                                    types[i] = "I-" + ct;
                                types[ti] = "B-" + ct;
                            }
                            //ti will be incremented by one by the loop
                            ti = tj;
                            break;
                        }
                    }
                }

                if (sample.isClearAdaptiveDataSet())
                    fw.write("O\t0\t0\tO\t-X-\t-DOCSTART-\tx\tx\t0\n\n");
                IntStream.range(0, tokens.length).forEach(ti -> {
                            try {
                                fw.write(types[ti] + "\t0\t0\tO\tO\t" + tokens[ti] + "\tx\tx\t0\n");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                );
                fw.write("\n");
            }
            fw.close();

            AdaptiveFeatureGenerator[] ruleFeature = new AdaptiveFeatureGenerator[]{
                    new OpenNLPNERFeatureGens.SpellingRuleFeatureGenerator()
            };
            nwiTestIn = Config.getResourceAsStream("NERWeb" + File.separator + "all.dat");
            InputStream nwiTrainIn = Config.getResourceAsStream("NERWeb" + File.separator + "weak_train.txt");
            nwiTestStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTestIn, 7);
            CustomConll03NameSampleStream nwiTrainStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTrainIn, 7);
            String modelName = String.join(File.separator, new String[]{"opennlp", "nwi", "en-ner-weakly.bin"});
            trainOpennlpModel(nwiTrainStream, ruleFeature, modelName);
            testOpenNLP(nwiTestStream, ruleFeature, modelName);
        } catch(IOException ie){
            ie.printStackTrace();
        }
    }

    public static void main(String[] args){
        testOnDbpediaHelper();
    }
}

/**
 ------------------------
 0    1    2    3    4    5    7    9   10   11   13   17   18   19   20   22   25   27   31   32   33   35   36   37   38   -1
 0 0.92    0    0 0.01    0    -    0    0    0    0    0    0    0    0    0    0    -    0    0    0    0    -    0    0    - 0.07
 1 0.07  0.4 0.01 0.06    0    0    0    0    0 0.02 0.01    0    0    -    0    0    0    0    0    0    0    -    0    0    - 0.41
 2 0.09 0.01 0.27 0.15    -    - 0.01    0    0 0.01    0    -    0 0.01    -    -    -    -    0    -    -    -    -    -    - 0.43
 3 0.08    0 0.01 0.64 0.01    0    0 0.02    0    0    0    0    0    0    -    0    0    0    0    -    -    -    0    0    - 0.23
 4 0.06    0    0 0.06  0.6    -    0    0    0    0    0    0    0    -    -    -    -    -    -    -    -    -    -    -    - 0.27
 5 0.02    -    - 0.07    - 0.67    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.25
 7 0.02    0    - 0.02    -    - 0.84    -    - 0.01    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.09
 9 0.06 0.01    0 0.14    0    -    - 0.49    -    0    0    0    -    -    -    -    -    -    -    -    -    -    0    -    - 0.28
 10 0.01    -    0 0.08    -    -    -    - 0.76 0.01    -    0    -    -    -    -    -    -    -    -    -    -    -    -    - 0.13
 11 0.03 0.03    0 0.04    0    -    0    0    -  0.4 0.01    -    -    -    0    -    -    -    -    0    -    -    -    0    - 0.48
 13 0.05 0.02    0 0.03    -    -    -    -    - 0.01 0.26    -    -    -    0    0    -    0    -    -    -    -    - 0.01    - 0.62
 17    0    -    - 0.53    -    -    -    0    0    -    - 0.05    -    -    -    -    -    -    -    -    -    -    -    -    - 0.42
 18 0.03 0.01    - 0.12    -    -    -    -    -    -    -    - 0.53    -    -    -    -    -    -    -    -    -    -    -    - 0.32
 19 0.03    -    - 0.01 0.01    -    -    -    -    -    -    -    - 0.81    -    -    -    -    -    -    -    -    -    -    - 0.13
 20 0.01 0.04    - 0.02    -    -    -    -    - 0.01    -    -    -    -  0.4    -    -    -    -    -    -    -    -    -    - 0.52
 22    - 0.08    - 0.15    -    -    -    -    - 0.23    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.54
 25 0.33 0.07    - 0.13    -    - 0.07    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -  0.4
 27 0.03    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.87    -    -    -    -    -    -    -  0.1
 31 0.03    - 0.03 0.03    -    -    -    -    - 0.03    -    -    -    -    -    -    -    - 0.43    -    -    -    -    -    - 0.46
 32 0.17    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -  0.5    -    -    -    -    - 0.33
 33    -    -    - 0.57    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.43
 35    -    - 0.12 0.38    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -  0.5
 36 0.05    -    - 0.01    -    -    -    -    - 0.01 0.01    -    -    -    -    -    -    -    -    -    -    - 0.66    -    - 0.27
 37 0.06 0.01 0.01 0.09 0.01    - 0.01    - 0.01    -    -    -    - 0.01    -    -    -    -    -    -    -    -    - 0.44    - 0.36
 38 0.03 0.01    0 0.02    0    -    0    -    -    0 0.01    -    -    0    0    -    -    0    -    -    -    -    0    0    - 0.93
 -1    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -
 ------------------------

 Missed #5675 due to improper assignment
 #8802due to improper segmentation
 #9605 due to single word or no evidence
 Precision: 0.8140917266881277
 Recall: 0.8445453526180065
 F1:0.8290389657354669
 Average Precision: 0.45527249227963645

 ------------------------
 0    1    2    3    4    5    7    9   10   11   13   17   18   19   20   22   25   27   31   32   33   35   36   37   38   -1
 0 0.92    0    0 0.01    0    -    0    0    0    0    0    0    0    0    0    0    -    0    0    0    0    -    0    0    - 0.07
 1 0.07 0.41 0.01 0.06    0    0    0    0    - 0.02 0.01    0    0    -    0    0    0    0    0    0    0    -    0    0    - 0.41
 2 0.09 0.01 0.27 0.15    -    - 0.01    0    0 0.01    0    -    0 0.01    -    -    -    -    0    -    -    -    -    -    - 0.43
 3 0.08    0 0.01 0.64 0.01    0    0 0.02    0    0    0    0    0    0    -    0    0    0    0    -    -    -    0    0    - 0.23
 4 0.06    0    0 0.06  0.6    -    0    0    0    0    0    0    0    -    -    -    -    -    -    -    -    -    -    -    - 0.27
 5 0.02    -    - 0.07    - 0.67    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.24
 7 0.03    0    - 0.02    -    - 0.83    -    - 0.01    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -  0.1
 9 0.07 0.01    0 0.14    0    -    - 0.49    -    0    0    0    -    -    -    -    -    -    -    -    -    -    0    -    - 0.28
 10 0.01    -    0 0.07    -    -    -    - 0.76 0.01    -    0    -    -    -    -    -    -    -    -    -    -    -    -    - 0.13
 11 0.03 0.03    0 0.04    0    -    0    0    -  0.4 0.01    -    -    -    0    -    -    -    -    0    -    -    -    0    - 0.47
 13 0.05 0.02    0 0.03    -    -    -    -    - 0.01 0.26    -    -    -    0    0    -    0    -    -    -    -    - 0.01    - 0.62
 17    0    -    - 0.53    -    -    -    0    0    0    - 0.05    -    -    -    -    -    -    -    -    -    -    -    -    - 0.41
 18 0.03 0.01    - 0.12    -    -    -    -    -    -    -    - 0.53    -    -    -    -    -    -    -    -    -    -    -    - 0.32
 19 0.03    -    - 0.01 0.01    -    -    -    -    -    -    -    - 0.81    -    -    -    -    -    -    -    -    -    -    - 0.13
 20 0.01 0.04    - 0.02    -    -    -    -    - 0.01    -    -    -    - 0.41    -    -    -    -    -    -    -    -    -    - 0.52
 22    - 0.08    - 0.15    -    -    -    -    - 0.23    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.54
 25 0.33    -    - 0.13    -    - 0.07    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.47
 27 0.03    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.87    -    -    -    -    -    -    -  0.1
 31 0.03    - 0.03 0.03    -    -    -    -    - 0.03    -    -    -    -    -    -    -    - 0.43    -    -    -    -    -    - 0.46
 32 0.17    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -  0.5    -    -    -    -    - 0.33
 33    -    -    - 0.57    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    - 0.43
 35    -    - 0.12 0.38    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -  0.5
 36 0.05    -    - 0.01    -    -    -    -    - 0.01 0.01    -    -    -    -    -    -    -    -    -    -    - 0.67    -    - 0.26
 37 0.06 0.01 0.02  0.1 0.01    - 0.01    - 0.01    -    -    -    - 0.01    -    -    -    -    -    -    -    -    -  0.4    - 0.39
 38 0.03 0.01    0 0.02    0    -    0    -    -    0    0    -    -    0    0    -    -    0    -    -    -    -    0    0    - 0.93
 -1    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -    -
 ------------------------

 Missed #5658 due to improper assignment
 #8724due to improper segmentation
 #9623 due to single word or no evidence
 Precision: 0.8146861514470769
 Recall: 0.8451620136786635
 F1:0.8296443045089877
 Average Precision: 0.45397282171135217
 */