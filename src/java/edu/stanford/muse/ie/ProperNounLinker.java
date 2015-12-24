package edu.stanford.muse.ie;

import org.tartarus.snowball.SnowballProgram;
import org.tartarus.snowball.ext.PorterStemmer;
import java.util.*;

/**
 * Created by vihari on 24/12/15.
 * A model to link proper nouns in an archive
 */
public class ProperNounLinker {
    static Set<String> bow(String phrase) {
        String[] tokens = phrase.split("\\s+");
        Set<String> bows = new LinkedHashSet<>();
        SnowballProgram stemmer = new PorterStemmer();
        for (String tok : tokens) {
            tok = tok.replaceAll("^\\W+|\\W+$", "");
            stemmer.setCurrent(tok.toLowerCase());
            stemmer.stem();
            String ct = new String(stemmer.getCurrentBuffer());
            bows.add(ct);
        }
        return bows;
    }

    boolean isValidCandidate(String c1, String c2) {
        if (c1 == null && c2 == null)
            return true;
        if (c1 == null || c2 == null)
            return false;
        if (c1.equals(c2))
            return true;

        //acronym check
        String acr1 = Util.getAcronym(c1), acr2 = Util.getAcronym(c2);
        if (acr1.equals(c2) || acr2.equals(c1))
            return true;

        //same bag of words
        Set<String> bow1 = new LinkedHashSet<>(), bow2 = new LinkedHashSet<>();
        bow1 = bow(c1);
        bow2 = bow(c2);
        Set<String> sbow = bow1.size()<bow2.size()?bow1:bow2;
        for(String w: sbow){

        }
        return false;
    }

    public static void main(String[] args) {
        System.err.println(bow("National Transportation day"));
    }
}
