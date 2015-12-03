package edu.stanford.muse.ner.model;

import com.google.common.collect.Lists;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vihari on 19/09/15.
 */
public class Experiment {
    public static double prob(String[] patts, Map<String,Double> mu){
        List<String> pl = Lists.newArrayList(patts);
        double v = 0;
        for(String str: mu.keySet()){
            if(pl.contains(str))
                v += Math.log(mu.get(str));
            else
                v += Math.log(1-mu.get(str));
        }
        return Math.exp(v);
    }

    public static void print(String msg, Map<String,Double> mu){
        System.err.println(msg);
        System.err.println("=====================");
        List<Pair<String,Double>> sm = Util.sortMapByValue(mu);
        for(Pair<String,Double> p: sm)
            System.err.println(p.getFirst()+" : "+mu.get(p.getFirst()));
        System.err.println("=====================");
    }

    public static void em(String[] names, Map<String,Double> mu1, Map<String,Double> mu2, int n1, int n2, int niter){
        double pi1 = (double)n1/(n1+n2);
        double pi2 = (double)n2/(n1+n2);
        print("Original org mixture: ",mu1);
        print("Original non-org mixture ", mu2);
        for(int iter=0;iter<niter;iter++){
            Map<String,Double> nm1 = new LinkedHashMap<>(), nm2 = new LinkedHashMap<>();
            double ngs1 = 0, ngs2 = 0;
            for(String name: names) {
                String[] patts = FeatureDictionary.getPatts(name);
                //E step
                double gamma1 = pi1 * prob(patts, mu1);
                gamma1 = gamma1/(gamma1+pi2*prob(patts, mu2));
                double gamma2 = pi2 * prob(patts, mu2);
                gamma2 = gamma2/(gamma2+pi1*prob(patts, mu1));
                ngs1 += gamma1;
                ngs2 += gamma2;
                for(String patt: patts){
                    if(!nm1.containsKey(patt))
                        nm1.put(patt,0.0);
                    if(!nm2.containsKey(patt))
                        nm2.put(patt, 0.0);
                    nm1.put(patt, nm1.get(patt)+gamma1);
                    nm2.put(patt, nm2.get(patt)+gamma2);
                }
            }
            for(String str: nm1.keySet())
                nm1.put(str, nm1.get(str)/ngs1);
            for(String str: nm2.keySet())
                nm2.put(str, nm2.get(str)/ngs2);
            print("New mu for ORG mixture: ", nm1);
            print("New mu for NON-ORG mixture: ", nm2);
        }
    }

    public static void main(String[] args){
        String[] names = new String[]{"New York State University", "Princeton University", "New York Times", "Public Service Commission", "Robert Creeley", "New York"};
        Boolean[] types = new Boolean[]{true,true,true,true,false,false};
        Map<String,Double> mu1 = new LinkedHashMap<>(), mu2 = new LinkedHashMap<>();
        int norg = 0,nnorg = 0;
        int ni = 0;
        for(String name: names){
            String[] patts = FeatureDictionary.getPatts(name);
            boolean type = types[ni];
            for(String patt: patts){
                if (!mu1.containsKey(patt))
                    mu1.put(patt, 0.0);
                if(!mu2.containsKey(patt))
                    mu2.put(patt,0.0);
                if(type) {
                    mu1.put(patt, mu1.get(patt)+1);
                    norg++;
                }
                else{
                    mu2.put(patt, mu2.get(patt)+1);
                    nnorg++;
                }
            }
            ni ++;
        }
        for(String str: mu1.keySet()){
            mu1.put(str, mu1.get(str)/norg);
        }
        for(String str: mu2.keySet()){
            mu2.put(str, mu2.get(str)/nnorg);
        }

        em(names, mu1, mu2, norg, nnorg, 5);
    }
}
