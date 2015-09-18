package edu.stanford.muse.ner;

import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;

import java.io.*;
import java.util.*;

/**
 * Created by vihari on 11/09/15.
 */
public class NEREvaluator {
    public Map<Short, Set<String>> bNames = new LinkedHashMap<>();
    List<String> sents = new ArrayList<>();
    String homeFldr = System.getProperty("user.home")+File.separator+"epadd-ner"+File.separator+"ner-benchmarks"+File.separator+"umasshw";
    int n = -1;

    Pair<Short, String> getTagAndToken(String line){
        line = line.trim();
        String[] words = line.split("\\s+");
        Short tag = -1;
        if(words[3].equals("ORG"))
            tag = FeatureDictionary.ORGANISATION;
        else if(words[3].equals("PER"))
            tag = FeatureDictionary.PERSON;
        else if(words[3].equals("LOC"))
            tag = FeatureDictionary.PLACE;
        return new Pair<>(tag, words[0]);
    }

    int pl = 0;
    String ptok;
    short ptype=-1;
    //keps track of line, so as to merge tokens in consecutive lines
    void add(Short type, String token, int ll){
        if(ll == (pl+1) && ptype==type){
            ptok = ptok+" "+token;
            pl = ll;
            return;
        }
        else if(ptype>=0 && ptok!=null){
            if(!bNames.containsKey(ptype))
                bNames.put(ptype, new LinkedHashSet<String>());
            bNames.get(ptype).add(ptok);
        }
        ptype = type;
        ptok = token;
        pl = ll;
    }

    //call this at the end of every sentence
    void addResidue(){
        if(ptype>=0 && ptok!=null){
            if(!bNames.containsKey(ptype))
                bNames.put(ptype, new LinkedHashSet<String>());
            bNames.get(ptype).add(ptok);
        }
        ptype = -1;
        ptok=null;
        pl = 0;
    }

    /**
     * n - sample size, samples only n sentences from benchmark*/
    public NEREvaluator(int n) {
        this.n = n;
        String dataFile = homeFldr + File.separator + "testa.dat";
        int numSents=0,nl = 0;
        try {
            BufferedReader br = new BufferedReader(new FileReader(dataFile));
            String line;
            while((line = br.readLine())!=null) {
                String sent = "";
                Pair<Short, String> p = getTagAndToken(line.trim());
                add(p.getFirst(), p.getSecond(), nl);
                sent += p.getSecond();
                while ((line = br.readLine()) != null && !line.equals("")) {
                    nl ++;
                    p = getTagAndToken(line.trim());
                    add(p.getFirst(), p.getSecond(), nl);
                    sent += " " + p.getSecond();
                }
                addResidue();
                sents.add(sent);
                if(numSents++>n)
                    break;
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public NEREvaluator(){
        this(-1);
    }

    public List<String> getSentences() {
        return sents;
    }

    //returns precision, recall, F1 fractions
    public Triple<Double,Double,Double> evaluate(Set<String> names, Short type){
        if(bNames == null || bNames.get(type) == null)
            System.err.println("benchmark data null or unknown type: "+type);
        Set<String> bnames = bNames.get(type);
        double p=0, r=0;
        for(String bname: bnames){
            if(names.contains(bname))
                r++;
        }
        r/=bnames.size();
        p = r/names.size();
        double f = 2*r*p/(r+p);
        return new Triple<>(p,r,f);
    }

    public static void main(String[] args){
        NEREvaluator eval = new NEREvaluator(100);
        List<String> sents = eval.getSentences();
        Short[] types = new Short[]{FeatureDictionary.PERSON, FeatureDictionary.ORGANISATION, FeatureDictionary.PLACE};
        for(Short type: types)
            System.out.println("Type: " + type + ", " + eval.bNames.get(type).size());
        for(String sent: sents)
            System.out.println(sent);
        System.out.println(eval.bNames.get(FeatureDictionary.PERSON));
        System.out.println(eval.bNames.get(FeatureDictionary.ORGANISATION));
        System.out.println(eval.bNames.get(FeatureDictionary.PLACE));
    }
}
