package edu.stanford.muse.ner.model;

import edu.stanford.muse.Config;
import edu.stanford.muse.ner.model.test.SequenceModelTest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.stanford.muse.util.DBpediaUtils;
import edu.stanford.muse.util.Util;
import jscip.*;



/**
 * Created by vihari on 11/04/17.
 *
 * Rule inducer based on ARI model from:
 * http://dl.acm.org/citation.cfm?id=2882975 -- "Discovering structure in the universe of Attribute Names"
 */

/** Example how to create a problem with linear constraints. */
public class ARIModel extends SequenceModel implements Serializable{
    static boolean DEBUG = false;

    public ARIModel(Map<String, MU> mixtures, Map<String, String> gazettes) {
        super(mixtures, gazettes);
    }

    static class Learner extends RuleInducer {
        Learner(Map<String, String> gazettes, Map<String, Map<String, Float>> tokenPriors) {
            super(gazettes, tokenPriors);
        }

        @Override
        public void learn() {
            //make sure this is linked hashmap
            Map<String, MU> revisedMixtures = new LinkedHashMap<>();
            String rulesDir = Config.SETTINGS_DIR + File.separator + "ARI_rules";

            if (DEBUG) {
                if (!new File(rulesDir).exists())
                    new File(rulesDir).mkdir();
            }

            for (Map.Entry e : gazettes.entrySet()) {
                String phrase = (String) e.getKey();
                String dbpediaType = (String) e.getValue();
                phrase = DBpediaUtils.filterTitle(phrase, dbpediaType);
                if (phrase == null)
                    continue;

                NEType.Type type = NEType.parseDBpediaType(dbpediaType);
                Map<String, List<String>> wfeatures = genFeatures(phrase, type.getCode());

                Map<String, Float> gamma = new LinkedHashMap<>();
                for (String mi : wfeatures.keySet())
                    gamma.put(mi, 1.0f / wfeatures.size());

                for (String g : gamma.keySet()) {
                    MU mu = mixtures.get(g);
                    if (mu == null)
                        continue;
                    if (!revisedMixtures.containsKey(g))
                        revisedMixtures.put(g, new MU(g, muPriors.get(g)));
                    revisedMixtures.get(g).add(gamma.get(g), wfeatures.get(g), muPriors.get(g));
                }
            }


            long st = System.currentTimeMillis();
            System.err.println("Adding rule vars");
            for (NEType.Type type : NEType.getAllTypes()) {
                System.loadLibrary("jscip");
                System.gc();
                System.out.println("Learning for: "+type.getDisplayName());
                System.out.println(Util.getMemoryStats());
                Scip scip = new Scip();

                // set up data structures of SCIP
                scip.create("ARI");

                List<Variable> rule_vars = new ArrayList<>();
                Map<String, Integer> midToRule = new LinkedHashMap<>();
                int ri = 0;
                System.out.println("Adding rule vars");
                for (String mid : revisedMixtures.keySet()) {
                    rule_vars.add(scip.createVar("rule-" + ri, 0, 1, revisedMixtures.get(mid).numSeen / gazettes.size(), SCIP_Vartype.SCIP_VARTYPE_BINARY));
                    midToRule.put(mid, ri);
                    ri++;
                }

                System.out.println("Time to add rule vars: " + (System.currentTimeMillis() - st));

                //long neg = gazettes.entrySet().stream().map(e->NEType.parseDBpediaType(e.getValue())).filter(t->type!=t).count();
                System.err.println("Adding gazette vars");
                int i = 0;
                for (Map.Entry<String, String> e : gazettes.entrySet()) {
                    String phrase = e.getKey();
                    phrase = DBpediaUtils.filterTitle(phrase, e.getValue());
                    if (phrase == null)
                        continue;
                    NEType.Type thisType = NEType.parseDBpediaType(e.getValue());

                    Map<String, List<String>> features = genFeatures(phrase, thisType.getCode());
                    Set<String> patts = features.keySet();

                    List<Variable> vars = new ArrayList<>();
                    List<Double> vals = new ArrayList<>();

                    //Map<NEType.Type, Variable> gvars = new LinkedHashMap();
                    Variable g_var = scip.createVar("err-" + type.getCode() + "-" + i, 0.0, scip.infinity(), 1.0, SCIP_Vartype.SCIP_VARTYPE_CONTINUOUS);

                    for (String patt : patts) {
                        MU mu = revisedMixtures.get(patt);
                        if (mu == null) {
                            continue;
                        }

                        double n_a_r = mu.getLikelihood(features.get(patt));
                        Variable rule_var = rule_vars.get(midToRule.get(patt));
                        vars.add(rule_var);
                        vals.add(n_a_r);
                    }

                    //add constraint
                    if (thisType == type) {
                        vars.add(g_var);
                        vals.add(1.0);
                        Constraint c = scip.createConsLinear("err_+_" + i, vars.toArray(new Variable[vars.size()]),
                                vals.stream().mapToDouble(d -> d).toArray(), 1, scip.infinity());
                        scip.addCons(c);
                    } else {
                        vars.add(g_var);
                        vals.add(-1.0);
                        Constraint c = scip.createConsLinear("err_-_" + i, vars.toArray(new Variable[vars.size()]),
                                vals.stream().mapToDouble(d -> d).toArray(), -scip.infinity(), 0);
                        scip.addCons(c);
                    }
                    i++;
                    if (i % 500000 == 0)
                        System.err.println("Added vars for: " + i + "/" + gazettes.size());
                }

                // solve problem
                System.err.println("Starting to solve the problem with: " + (rule_vars.size() + i) + " vars and " + i + " constraints");
                st = System.currentTimeMillis();
                scip.solve();
                System.out.println("Time for solve op: " + (System.currentTimeMillis() - st));

                st = System.currentTimeMillis();
                // print all solutions
                Solution bestSol = scip.getBestSol();
                System.out.println("Getting best sol: " + (System.currentTimeMillis() - st));

                st = System.currentTimeMillis();
                //IntStream.range(0, rule_vars.size()).forEach(rvi-> System.out.println(rvi + " " + scip.getSolVal(bestSol, rule_vars.get(rvi))));
                Set<Integer> goodRulesIndices = IntStream.range(0, rule_vars.size()).filter(rvi -> scip.getSolVal(bestSol, rule_vars.get(rvi)) == 1)
                        .boxed().collect(Collectors.toSet());
                System.out.println("Iteration over: " + (System.currentTimeMillis() - st));
                ri = 0;
                if (DEBUG) {

                    try {
                        FileWriter fw = new FileWriter(rulesDir + File.separator + type.getDisplayName() + ".txt");
                        for (Map.Entry<String, MU> e : revisedMixtures.entrySet()) {
                            if (goodRulesIndices.contains(ri))
                                fw.write("------------\n" + e.getValue().toString() + "\n");
                            ri++;
                        }
                        fw.close();
                    } catch (IOException ie) {
                        ie.printStackTrace();
                    }
                }

                ri = 0;
                System.out.println("Found " + goodRulesIndices.size() + " rules for " + type.getDisplayName());

                for (Map.Entry<String, MU> e : revisedMixtures.entrySet()) {
                    if (goodRulesIndices.contains(ri)) {
                        MU mu = revisedMixtures.get(e.getKey());
                        mu.muVectorPositive.keySet().stream().filter(f -> f.startsWith("T:")).forEach(f -> mu.muVectorPositive.put(f, 0f));
                        mu.muVectorPositive.put("T:" + type.getCode(), mu.numMixture);
                    }
                    ri++;
                }

                scip.free();
            }
            mixtures = revisedMixtures;

        }
    }

    public static ARIModel train(Map<String, String> tdata){
        float alpha = 0.2f;
        Map<String, Map<String, Float>> tokenPriors = getNormalizedTokenPriors(tdata, alpha);
        Learner learner = new Learner(tdata, tokenPriors);
        learner.learn();
        return new ARIModel(learner.getMixtures(), learner.getGazettes());
    }

    public static synchronized ARIModel loadModel(String modelPath) {
        try {
            //the buffer size can be much higher than default 512 for GZIPInputStream
            ARIModel model = (ARIModel) Util.readObjectFromSerGZ(modelPath);
            return model;
        } catch (Exception e) {
            Util.print_exception("Exception while trying to load model from: " + modelPath, e, log);
            return null;
        }
    }

    static void loadAndTestNERModel(){
        System.err.println("Loading model...");
        SequenceModel nerModel;
        log.info(Util.getMemoryStats());
        try {
            String modelName = ARIModel.class.getCanonicalName()+".ser.gz";
            nerModel = ARIModel.loadModel(modelName);
            if(nerModel==null) {
                nerModel = ARIModel.train(DBpediaUtils.readDBpedia());
                Util.writeObjectAsSerGZ(nerModel, Config.SETTINGS_DIR + File.separator + modelName);
                writeModelAsRules(nerModel, Config.SETTINGS_DIR + File.separator + "ARI_rules");
            }

            log.info(Util.getMemoryStats());
            SequenceModelTest.ParamsCONLL params = new SequenceModelTest.ParamsCONLL();
            params.ignoreSegmentation = false;
            SequenceModelTest.testCONLL(nerModel, false, params);
            log.info(Util.getMemoryStats());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args){
        Map<String, String> dbpedia = DBpediaUtils.readDBpedia(0.05f, null);
        ARIModel.train(dbpedia);
    }
}