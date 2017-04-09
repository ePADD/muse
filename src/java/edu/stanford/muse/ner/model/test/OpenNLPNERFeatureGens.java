package edu.stanford.muse.ner.model.test;

import edu.stanford.muse.ner.model.NEType;
import edu.stanford.muse.ner.model.SequenceModel;
import edu.stanford.muse.util.DBpediaUtils;
import edu.stanford.muse.util.Span;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.CachedFeatureGenerator;
import opennlp.tools.util.featuregen.FeatureGeneratorAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by vihari on 06/04/17.
 */
public class OpenNLPNERFeatureGens {
    @SuppressWarnings("Duplicates")
    static class GazetteLookupFeatureGenerator extends FeatureGeneratorAdapter {
        static String PER = "gazz_per", LOC = "gazz_loc", ORG = "gazz_org";
        static Map<String, String> dbpedia = DBpediaUtils.readDBpedia();

        @Override
        public void createFeatures(List<String> features, String[] tokens, int index, String[] previousOutcomes) {
            int WINDOW = 3;

            IntStream.range(Math.max(0, index - WINDOW), index + 1).filter(ti ->
                    IntStream.range(index + 1, Math.min(index + WINDOW, tokens.length)).filter(tj ->
                    {
                        String k = String.join(" ", Arrays.copyOfRange(tokens, ti, tj));
                        //System.err.println("Trying: "+k);
                        String v;
                        if ((v = dbpedia.get(k)) != null) {
                            //System.err.println("Found match: " + v);
                            NEType.Type type = NEType.getCoarseType(NEType.parseDBpediaType(v));
                            if (type == NEType.Type.PERSON) {
                                features.add(PER);
                                return true;
                            } else if (type == NEType.Type.PLACE) {
                                features.add(LOC);
                                return true;
                            } else if (type == NEType.Type.ORGANISATION) {
                                features.add(ORG);
                                return true;
                            }
                        }
                        return false;
                    }).findAny().isPresent()
            ).findAny();
        }
    }

    @SuppressWarnings("Duplicates")
    static class SpellingRuleFeatureGenerator extends FeatureGeneratorAdapter{
        static String PER = "rule_per", LOC = "rule_loc", ORG = "rule_org";
        static SequenceModel model;
        static {
            try {
                model = SequenceModel.loadModelFromRules(SequenceModel.RULES_DIRNAME);
            } catch (IOException ie) {
                System.err.println("Could not load model from: " + SequenceModel.RULES_DIRNAME);
                ie.printStackTrace();
            }
        }
        @Override
        public void createFeatures(List<String> features, String[] tokens, int index, String[] previousOutcomes) {
            int WINDOW = 3;
            String[] sts = Arrays.copyOfRange(tokens, Math.max(0, index-WINDOW), Math.min(tokens.length, index+WINDOW));
            Span[] spans = model.find(String.join(" ", sts));
            String t = tokens[index];
            Stream.of(spans).filter(sp->{
                try {
                    if (sp.getText().matches(".*\\b" + t + "\\b.*") && sp.typeScore > 1E-10) {
                        int ls = (int) (Math.ceil(-Math.log10(sp.typeScore)));
                        ls = ls>5?0:1;
                        NEType.Type type = NEType.getCoarseType(sp.getType());
                        if (type == NEType.Type.PERSON) {
                            features.add(PER + ls);
                            return true;
                        } else if (type == NEType.Type.PLACE) {
                            features.add(LOC + ls);
                            return true;
                        } else if (type == NEType.Type.ORGANISATION) {
                            features.add(ORG + ls);
                            return true;
                        }
                    }
                }catch (PatternSyntaxException pse){
                }
                return false;
            }).findAny();
        }

    }

    public static void main(String[] args) {
        AdaptiveFeatureGenerator fg = new CachedFeatureGenerator(new AdaptiveFeatureGenerator[]{
                new GazetteLookupFeatureGenerator(),
                new SpellingRuleFeatureGenerator()
        });
        //should be ["gazz_loc"]
        String[] tokens = new String[]{"New", "York", "is", "a", "beautiful",
                "city", "and", "New", "York", "Times", "is", "the", "best"};
        for(int ti=0;ti<tokens.length; ti++) {
            List<String> features = new ArrayList<>();
            fg.createFeatures(features, tokens, ti, new String[]{});
            System.out.println("Features for "+ tokens[ti] + " :" + features);
        }
    }
}
