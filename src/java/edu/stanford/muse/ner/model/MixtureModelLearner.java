package edu.stanford.muse.ner.model;

import edu.stanford.muse.util.DBpediaUtils;
import edu.stanford.muse.util.Util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vihari on 09/04/17.
 */
public class MixtureModelLearner extends RuleInducer {
    public static class Options{
        int emIters;
        Options(int emIters){
            this.emIters = emIters;
        }
        Options(){
           this.emIters = 5;
        }
    }

    Options options = new Options();

    MixtureModelLearner(Map<String, String> gazettes, Map<String, Map<String, Float>> tokenPriors, Options options){
        super(gazettes, tokenPriors);
        if(options != null)
            this.options = options;
    }


    //the argument alpha fraction is required only for naming of the dumped model size
    void learn() {
        log.info("Performing EM on: #" + mixtures.size() + " words");
        double ll = getIncompleteDataLogLikelihood();
        log.info("Start Data Log Likelihood: " + ll);
        System.out.println("Start Data Log Likelihood: " + ll);
        Map<String, MU> revisedMixtures = new LinkedHashMap<>();
        int N = gazettes.size();
        int wi;
        for (int i = 0; i < options.emIters; i++) {
            log.info(Util.getMemoryStats());
            wi = 0;
            for (Map.Entry e : gazettes.entrySet()) {
                String phrase = (String) e.getKey();
                String dbpediaType = (String) e.getValue();
                phrase = DBpediaUtils.filterTitle(phrase, dbpediaType);
                if (phrase == null)
                    continue;

                if (wi++ % 1000 == 0)
                    log.info("EM iteration: " + i + ", " + wi + "/" + N);

                NEType.Type type = NEType.parseDBpediaType(dbpediaType);
                float z = 0;
                //responsibilities
                Map<String, Float> gamma = new LinkedHashMap<>();
                //Word (sort of mixture identity) -> Features
                Map<String, List<String>> wfeatures = genFeatures(phrase, type.getCode());

                if (type != NEType.Type.OTHER) {
                    for (String mi : wfeatures.keySet()) {
                        if (wfeatures.get(mi) == null) {
                            continue;
                        }
                        MU mu = mixtures.get(mi);
                        if (mu == null) {
                            //log.warn("!!FATAL!! MU null for: " + mi + ", " + mixtures.size());
                            continue;
                        }
                        double d = mu.getLikelihood(wfeatures.get(mi)) * getPrior(mu, mixtures);
                        if (Double.isNaN(d))
                            log.warn("score for: " + mi + " " + wfeatures.get(mi) + " is NaN");
                        gamma.put(mi, (float) d);
                        z += d;
                    }
                    if (z == 0) {
                        if (SequenceModel.DEBUG)
                            log.info("!!!FATAL!!! Skipping: " + phrase + " as none took responsibility");
                        continue;
                    }

                    for (String g : gamma.keySet()) {
                        gamma.put(g, gamma.get(g) / z);
                    }
                } else {
                    for (String mi : wfeatures.keySet())
                        gamma.put(mi, 1.0f / wfeatures.size());
                }

                if (SequenceModel.DEBUG) {
                    for (String mi : wfeatures.keySet()) {
                        log.info("MI:" + mi + ", " + gamma.get(mi) + ", " + wfeatures.get(mi));
                        log.info(mixtures.get(mi).toString());
                    }
                    log.info("EM iter: " + i + ", " + phrase + ", " + type + ", ct: " + type);
                    log.info("-----");
                }

                for (String g : gamma.keySet()) {
                    MU mu = mixtures.get(g);
                    //ignore this mixture if the effective number of times it is seen is less than 1 even with good evidence
                    if (mu == null)//|| (mu.numSeen > 0 && (mu.numMixture + mu.alpha_pi) < 1))
                        continue;
                    if (!revisedMixtures.containsKey(g))
                        revisedMixtures.put(g, new MU(g, muPriors.get(g)));

                    if (Double.isNaN(gamma.get(g)))
                        log.error("Gamma NaN for MID: " + g);
                    if (SequenceModel.DEBUG)
                        if (gamma.get(g) == 0)
                            log.warn("!! Resp: " + 0 + " for " + g + " in " + phrase + ", " + type);
                    //don't even update if the value is so low, that just adds meek affiliation with unrelated mixtures
                    if (gamma.get(g) > 1E-7)
                        revisedMixtures.get(g).add(gamma.get(g), wfeatures.get(g), muPriors.get(g));

                }
            }
            double change = 0;
            for (String mi : mixtures.keySet())
                if (revisedMixtures.containsKey(mi))
                    change += revisedMixtures.get(mi).difference(mixtures.get(mi));
            change /= revisedMixtures.size();
            log.info("Iter: " + i + ", change: " + change);
            System.out.println("EM Iteration: " + i + ", change: " + change);
            //incomplete data log likelihood is better measure than just the change in parameters
            //i.e. P(X/\theta) = \sum\limits_{z}P(X,Z/\theta)
            mixtures = revisedMixtures;
            ll = getIncompleteDataLogLikelihood();
            log.info("Iter: " + i + ", Data Log Likelihood: " + ll);
            System.out.println("EM Iteration: " + i + ", Data Log Likelihood: " + ll);

            revisedMixtures = new LinkedHashMap<>();
        }
    }
}
