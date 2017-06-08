package edu.stanford.muse.ie.matching;

import com.google.gson.Gson;
import edu.stanford.muse.util.Util;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Matches {

    /* small class that encapsulates information of a given match */
    public class Match {
        private String originalString;
        private String match;
        private float score;
        private StringMatchType matchType;
        private String matchDescription;
        private boolean isContact;

        public Match(String matchString, float score, StringMatchType matchType, String matchDescription, boolean isContact) {
            this.score = score;
            this.match = matchString;
            this.matchType = matchType;
            this.matchDescription = matchDescription;
            this.isContact = isContact;
        }
    }

    private String matchString;
    private int maxMatches;
    private transient Set<String> matchedCstrings = new LinkedHashSet();
    private List<Matches.Match> matches = new ArrayList();

    public Matches(String matchString, int maxMatches) {
        this.matchString = matchString;
        this.maxMatches = maxMatches;
    }

    public String getMatchString() {
        return this.matchString;
    }

    private static String canonicalize(String s) {
        return Util.canonicalizeSpaces(s.toLowerCase().trim());
    }

    /** returns match type of s with candidate */
    public static StringMatchType match(String s, String candidate) {
        if(s != null && candidate != null) {
            String cs = canonicalize(s);
            String ccandidate = canonicalize(candidate);
            if(ccandidate.contains(cs) && !ccandidate.equals(cs)) {
                return StringMatchType.CONTAINED;
            } else {
                if(Util.allUppercase(s)) {
                    String candidateAbbrevLower = "";
                    List<String> candidateTokensLower = Util.tokenize(ccandidate);
                    Iterator var6 = candidateTokensLower.iterator();

                    while(var6.hasNext()) {
                        String ttl = (String)var6.next();
                        if(ttl.length() >= 1) {
                            candidateAbbrevLower = candidateAbbrevLower + ttl.charAt(0);
                        }
                    }

                    if(cs.equals(candidateAbbrevLower)) {
                        return StringMatchType.ACRONYM;
                    }

                    Set<String> candidateTokensLowerSet = new LinkedHashSet(candidateTokensLower);
                    Set<String> sTokensSet = new LinkedHashSet(Util.tokenize(cs));
                    if(candidateTokensLowerSet.containsAll(sTokensSet)) {
                        return StringMatchType.CONTAINED_DIFFERENT_ORDER;
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    boolean addMatch(String matchString, float score, StringMatchType matchType, String matchDescription, boolean isContact) {
        String cmatchString = canonicalize(matchString);
        if(!this.matchedCstrings.contains(cmatchString)) {
            this.matches.add(new Matches.Match(matchString, score, matchType, matchDescription, isContact));
            this.matchedCstrings.add(cmatchString);
        }

        return this.matches.size() >= this.maxMatches;
    }

    public String toJson() {
        return (new Gson()).toJson(this);
    }
}

