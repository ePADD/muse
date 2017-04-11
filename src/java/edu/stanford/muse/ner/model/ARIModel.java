package edu.stanford.muse.ner.model;

import edu.stanford.muse.ner.tokenize.CICTokenizer;
import edu.stanford.muse.ner.tokenize.Tokenizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.muse.util.DBpediaUtils;
import jscip.*;


/**
 * Created by vihari on 11/04/17.
 *
 * Rule inducer based on ARI model from:
 * http://dl.acm.org/citation.cfm?id=2882975 -- "Discovering structure in the universe of Attribute Names"
 */

/** Example how to create a problem with linear constraints. */
public class ARIModel extends NERModel {
    Tokenizer tokenizer = new CICTokenizer();

    @Override
    void setTokenizer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    Tokenizer getTokenizer() {
        return tokenizer;
    }

    @Override
    Map<String, String> getGazette() {
        return null;
    }

    @Override
    double getConditional(String phrase, Short type) {
        return 0;
    }

    static class Learner extends RuleInducer {
        Learner(Map<String, String> gazettes, Map<String, Map<String, Float>> tokenPriors) {
            super(gazettes, tokenPriors);
        }

        @Override
        public void learn() {
            Map<String, MU> revisedMixtures = new LinkedHashMap<>();
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

            System.loadLibrary("jscip");

            Scip scip = new Scip();

            // set up data structures of SCIP
            scip.create("ARI");

            int N = gazettes.size();
            for(int i=0;i<N;i++)
                scip.createVar("err-"+i, 0.0, scip.infinity(), 1.0, SCIP_Vartype.SCIP_VARTYPE_CONTINUOUS);
            for(int ri=0;ri<revisedMixtures.size();ri++)
                scip.createVar("rule-"+ri, 0, 1, penalties.get(revisedMixtures.get(ri)), SCIP_Vartype.SCIP_VARTYPE_BINARY);

            //add constraints
            scip.createConsLinear()
        }
    }

    public static void main(String[] args){
        // load generated C-library
        System.loadLibrary("jscip");

        Scip scip = new Scip();

        // set up data structures of SCIP
        scip.create("ARI");

        // create variables (also adds variables to SCIP)
        Variable x = scip.createVar("x", 2.0, 3.0, 1.0, SCIP_Vartype.SCIP_VARTYPE_CONTINUOUS);
        Variable y = scip.createVar("y", 7.0, scip.infinity(), -3.0, SCIP_Vartype.SCIP_VARTYPE_INTEGER);

        // create a linear constraint
        Variable[] vars = {x, y};
        double[] vals = {1.0, 2.0};
        Constraint lincons = scip.createConsLinear("lincons", vars, vals, 12, 20);

        // add constraint to SCIP
        scip.addCons(lincons);

        // release constraint (if not needed anymore)
        scip.releaseCons(lincons);

        // set parameters
        scip.setRealParam("limits/time", 100.0);
        scip.setRealParam("limits/memory", 10000.0);
        scip.setLongintParam("limits/totalnodes", 1000);

        // solve problem
        scip.solve();

        // print all solutions
        Solution[] allsols = scip.getSols();

        for (int s = 0; allsols != null && s < allsols.length; ++s)
            System.out.println("solution (x,y) = (" + scip.getSolVal(allsols[s], x) + ", " + scip.getSolVal(allsols[s], y) + ") with objective value " + scip.getSolOrigObj(allsols[s]));

        // release variables (if not needed anymore)
        scip.releaseVar(y);
        scip.releaseVar(x);

        // free SCIP
        scip.free();

    }
}