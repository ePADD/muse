package edu.stanford.muse.ner.tokenizer;

import edu.emory.mathcs.backport.java.util.Arrays;
import edu.stanford.muse.ner.NER;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.util.NLPUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CIC Pattern based tokenizer
 * This is a utility class to segment pseudo proper nouns from text and emit them
 *
 * @TODO: Why is parser looking beyond a single new line as in Harvard Law School\n\nDr. West?
 * @TODO: initialise a method to get the acronyms from the content
 * @TODO: CICTokenizer is not handling names that start with '>' well for ex: ">Holly Crumpton" will emit Holly and Crumpton as two tokens
 * also is not handling '-' well, sometimes this signals continuation of a name.
 * !!!!!This is a serious bug, fix it asap!!!!!!
 *
 * @TODO: Clean email related stuff like: "Subject: Re:", "Subject:", "Date:", "Email:", ">"
 * @TODO: Having two dashes in the name is not ok
 */
public class CICTokenizer implements Tokenizer, Serializable {
	static Pattern	personNamePattern, entityPattern, acronymPattern;
	static String[] stopWords =  new String[]{"and", "for","on","a","the","to","at","of", "in"};
	static List<String> estuff = Arrays.asList(new String[]{"Email","To","From","Date","Subject"});
    //private static final long serialVersionUID = 5699314474092217343L;
    private static final long serialVersionUID = 1L;

    static {
		initPattern();
	}

    //keeps track of mapping between the tokens and sentences they are extracted from
    public static class Sentences{
        List<String> sents;
        Map<Integer,Integer> mapping;
        public void setSentences(List<String> sents){
            this.sents = sents;
        }
        /**mapping from token offset to sentence*/
        public void setMapping(Map<Integer,Integer> mapping){
            this.mapping = mapping;
        }

        public String getSentence(int tokenStartOffset){
            if(mapping.containsKey(tokenStartOffset))
                return sents.get(mapping.get(tokenStartOffset));
            else{
                NER.log.warn("Did not find enclosing sentence for offset: "+tokenStartOffset);
                return null;
            }
        }
    }

	public static String cleanEmailStuff(String content){
		content = content.replaceAll("(Email:|To:|From:|Date:|Subject: Re:|Subject:)\\W+","");
		return content;
	}

	public static void initPattern() {
        //simplified this pattern: "[A-Z]+[A-Za-z]*(['\\-][A-Za-z]+)?"
        //nameP can end in funny chars this way
        //ignoring period at he end of the name may not be desired, "Rockwell International Corp.", the period here is part of the name
		String nameP = "[A-Z][A-Za-z'\\-\\.]*";
		//comma is a terrible character to allow, it sometimes crawls in the full list the entity is contained in.
		String allowedCharsOther = "\\s&", allowedCharsPerson = "\\s";

		StringBuilder sp = new StringBuilder("");
		int i = 0;
		for (String stopWord : stopWords) {
			sp.append(stopWord);
			if (i++ < (stopWords.length - 1))
				sp.append("|");
		}
		String stopWordsPattern = "(" + sp.toString() + ")";

		//[\"'_:\\s]*
		//number of times special chars between words can recur
		String recur = "{1,3}";
		//doe not allow more than two stop words occuring between two ic words.
		//should more than one sw be allowed? "The Supreme Court of the United States" or "The Supreme Court"?
		String nps = "(" + nameP + "([" + allowedCharsOther + "]" + recur + "(" + nameP + "[" + allowedCharsOther + "]" + recur + "|(" + stopWordsPattern + "[" + allowedCharsOther + "]" + recur + "))*" + nameP + ")?)";
		entityPattern = Pattern.compile(nps);
		NER.log.info("EP: " + nps);
		//allow coma only once after the first word
		nps = "(" + nameP + "([" + allowedCharsPerson + ",]" + recur + "(" + nameP + "[" + allowedCharsPerson + "]" + recur + ")*" + nameP + ")?)";
		personNamePattern = Pattern.compile(nps);
		NER.log.info("PNP: " + nps);

		acronymPattern = Pattern.compile("[A-Z]{3,}");
	}

    @Override
	public Set<String> tokenizeWithoutOffsets(String content, boolean pn) {
		List<Triple<String, Integer, Integer>> offsets = tokenize(content, pn);
		Set<String> names = new HashSet<String>();
		for (Triple<String, Integer, Integer> t : offsets)
			names.add(t.first);
		return names;
	}

	public static Set<String> getAcronyms(String content) {
		if (content == null)
			return null;

		content = cleanEmailStuff(content);

		if (acronymPattern == null)
			initPattern();

		Pattern namePattern = acronymPattern;

		Set<String> acrs = new HashSet<String>();
		Matcher m = namePattern.matcher(content);
		while (m.find()) {
			String acr = m.group();
			String tt = FeatureGeneratorUtil.tokenFeature(acr);
			if (!tt.equals("ac")) {
				continue;
			}
			acrs.add(acr);
		}
		return acrs;
	}

    private String[] tokeniseQuoteS(String name){
        List<String> tokens = new ArrayList<String>();
        if(name.contains("'s")) {
            String sp = "";
            int i = 0;
            for (String stopWord : stopWords) {
                sp += stopWord;
                if (i++ < (stopWords.length - 1))
                    sp += "|";
            }
            sp = "(" + sp + ")";

            String[] snames = name.split("'s(\\s|$)");
            for (String sname : snames) {
                //separated by atleast two stop words
                String str = "[\\s\\.\\:]" + sp + "([\\s\\.\\:]+" + sp + ")*" + "[\\s\\.\\:]" + sp;
                String[] sns = sname.split(str);
                for (String sn : sns) {
                    //the emitted string should not start or end with stop word.
                    sn = sn.replaceAll("^\\W*"+sp+"\\W+|\\W+"+sp+"\\W*$", "");
                    tokens.add(sn);
                }
            }
        }else
            tokens.add(name);
        return tokens.toArray(new String[tokens.size()]);
    }


    @Override
    public List<Triple<String, Integer, Integer>> tokenize(String content, boolean pn) {
        Pair<List<Triple<String,Integer, Integer>>, Sentences> ret = tokenizeWithSentences(content,pn);
        if(ret == null)
            return null;
        return ret.getFirst();
    }

	public Pair<List<Triple<String, Integer, Integer>>,Sentences> tokenizeWithSentences(String content, boolean pn) {
		List<Triple<String, Integer, Integer>> matches = new ArrayList<Triple<String, Integer, Integer>>();
		if (content == null)
			return null;

		//!!!!!!!!!!!
		//Imagine a skull drwan with ascii text here
		//!!!!!!!!!!!
		//trying to do this, will mess the offsets and hence creates problems for redaction;
		//TODO: is there someplace safe we can do this?
		//content = cleanEmailStuff(content);

		if (personNamePattern == null || entityPattern == null) {
			//System.err.println("Initiating pattern for: " + personNames);
			initPattern();
		}
		Pattern namePattern = null;
		if (pn)
			namePattern = personNamePattern;
		else
			namePattern = entityPattern;

		//we need a proper sentence splitter, as some of the names can contain period.
		String[] lines = content.split("\\n");
		//dont change the length of the content, so that the offsets are not messed up.
		content = "";
		for (String line : lines) {
			//for very short lines, new line is used as a sentence breaker.
			if (line.length() < 40)
				//dont want to rely on the sentence tokeniser for breaking of this sentence, should be robust and handle even when sentence tokeniser merges this line eith the next.
				//some is a non-stopword word that will do this, but this is still a hack
				//!!!!!!!!!!HACKY CODE!!!!!!!!!!!!!!!!!
				//be careful not to append more chars than that are being replaced, so as not to mess up the tokenizer offsets
				//% is not considered a special char in any of the entity names and hence is added.
				content += line + "%";
			else
				content += line + " ";
		}

        List<String> sents = new ArrayList<>();
        Map<Integer, Integer> smap = new LinkedHashMap<>();
		Span[] sentenceSpans = NLPUtils.tokeniseSentenceAsSpan(content);
		int si = 0;
        for (Span sentenceSpan : sentenceSpans) {
            si++;
			int sentenceStartOffset = sentenceSpan.getStart();
			String sent = sentenceSpan.getCoveredText(content).toString();
            sents.add(sent);
            //TODO: sometimes these long sentences are actually long list of names, which we cannot afford to lose.
            //Is there an easy way to test with the sentence is good or bad quickly and easy way to tokenize it further if it is good?
            if(sent.length()>=2000)
                continue;

            //NER.log.info(sent.length());
//			if(!content.substring(sentenceSpan.getStart(), sentenceSpan.getEnd()).equals(sent))
//				NER.log.warn("Warning: Sentence offset wrong for sent:"+sent);
			Matcher m = namePattern.matcher(sent);
			while (m.find()) {
				if (m.groupCount() > 0) {
					String name = m.group(1);
					//email related stuff
					if(estuff.contains(name))
						continue;
					name = name.replaceAll(", [A-Za-z]{1,3}$", "");
					name = name.replaceAll("^[A-Za-z]{1,3}, ", "");
					int start = m.start(1) + sentenceStartOffset, end = m.end(1) + sentenceStartOffset;
//					if(!content.substring(start,end).equals(name))
//						NER.log.warn("Serious warning: offsets not proper for: "+name);

					//if the length is less than 3, accept only if it is all capitals.
					if (name.length() < 3) {
						String tt = FeatureGeneratorUtil.tokenFeature(name);
						if (tt.equals("ac")) {
							matches.add(new Triple<String, Integer, Integer>(name, start, end));
                            smap.put(start, si);
                        }
					}
					else {
						//if the name contains, person start markers, then trim the offset
						String[] markers = FeatureDictionary.startMarkersForType.get(FeatureDictionary.PERSON);
						String lc = name.toLowerCase();
						//does this slow down things a lot?
						for (String marker : markers)
							if (lc.startsWith(marker+" ")) {
								start += marker.length()+1;
								break;
							}
						//further cleaning to remove "'s" pattern
						//@TODO: Can these "'s" be put to a good use?
						String[] cns = tokeniseQuoteS(name);
                        for(String cn: cns) {
                            matches.add(new Triple<String, Integer, Integer>(cn, start, end));
                            smap.put(start, si);
                        }
					}
				}
			}
		}
        Sentences sentences = new Sentences();
        sentences.setSentences(sents);
        sentences.setMapping(smap);
		return new Pair<>(matches, sentences);
	}

	public static void main(String[] args) {
		System.err.println(CICTokenizer.personNamePattern.pattern());
        Tokenizer tok = new CICTokenizer();
        Set<String> names = tok.tokenizeWithoutOffsets("Hello, I am Vihari, Piratla'. Some thing IIT Mandi, I went to college in UCB, GH then joined NASA after a brief tenure at CERN", true);
        names = tok.tokenizeWithoutOffsets("January 30, 2002: University at Buffalo, Center for Tomorrow, Service Center\n" +
                "Road, North Campus", false);
        for(String name: names)
            System.err.println(name);
	}
}
