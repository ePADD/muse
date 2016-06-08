package edu.stanford.muse.ner.tokenizer;

import edu.stanford.muse.ner.NER;
import edu.stanford.muse.ner.dictionary.EnglishDictionary;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.NLPUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;

import com.google.common.collect.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CIC Pattern based tokenizer
 * This is a utility class to segment pseudo proper nouns from text and emit them
 * Tokenizer is often called in an NER or some other text-processing pipeline so be extra careful about the efficiency
 *
 * TODO: CIC tokenizer fails when the sentence tokenizer fails, it is required to make the sentence tokenizer handle at least a few common abbreviations (such as Col. Mt. Inc. Corp. etc.) to make the application look less stupid; OpenNLP splits on some of the periods falsely
 * TODO: split tokens like P.V. Krishnamoorthi -> P. V. Krishnamoorthi
 * TODO: canonicalize and tokenize words such that stop words irrespective of their capitalised form are recognized, for example: "In American Culture", "IN SPANISH", "A NEW FEDERAL POLICY", "THE PROVOST"
 */
public class CICTokenizer implements Tokenizer, Serializable {
    static Log log						= LogFactory.getLog(CICTokenizer.class);

    static Pattern	entityPattern, multipleStopWordPattern;
    //NOTE: All the string lists below namely commonStartWords, commonEndWords, badSubstrings are case-insensitive
    //strips these words from the emitted token if they are seen in the start
    //Checks and strips if a phrase contains {[common start word]+" "}
    static String[] commonStartWords = new String[]{
            "hey","the","a","an","hello","dear","hi","met","from","quote","from:","and","my","our","regarding","quoting","on behalf of","behalf of","sorry","but",
            //include all stop words
            "and","for","a","the","to","at", "in", "of",
            "on","with","ok","your","I","to:","thanks", "let","does", "sure", "thank","you","about","&","yes","if","by","why","said","even","am","respected","although","as"
    };
    //Checks and strips if a phrase contains {" "+[common end word]}
    static String[] commonEndWords = new String[]{
            "I", "and","for","a","the","to","at", "in", "of"
    };
    //Emitted tokens containing these sub-strings will be dropped
    //checked for {contains " "+[bad substring]+" "} or starts with {[bad substring]+" "} or ends with {" "+[bad substring]}
    static String[] badSubstrings = new String[]{
            "Not ", "I'm", "I'll","I am","n't","I've"," Have ","I'd", "You've", "We've", "They've",
            //stuff specifically related to emails
            "Email","To","From","Date","Subject", "begin pgp"
    };

    //de often appears in personal names like "Alain de Lille", "Christine de Pizan", "Ellen van Langen"
    //https://en.wikipedia.org/wiki/Portuguese_name#The_particle_.27de.27
    //how useful is "on" in the stop words list
    //Consecutive capital words are allowed to be separated by these words, so this list is more restrictive than general stop words list
	static List<String> stopWords =  Arrays.asList(
            "and","for","a","the","at", "in", "of",
            //based on occurrence frequency of more than 100 in English DBpedia personal names list of 2014
            "de", "van","von","da","ibn","mac","bin","del","dos","di","la","du","ben","no","ap","le","bint","do", "den"/*John den Braber*/
    );
	private static final long serialVersionUID = 1L;

    static {
		initPattern();
	}

	static void initPattern() {
        //This def. of a word that can appear in person or non-person names
		String nameP = "[A-Z][A-Za-z0-9'\\-\\.]*";
        //these are the chars that are allowed to appear between words in the chunk
		//comma is a terrible character to allow, it sometimes crawls in the full list an entity is part of.
		String allowedCharsOther = "\\s&'";
        //allowedCharsPerson = "\\s";

		StringBuilder sp = new StringBuilder("");
		int i = 0;
		for (String stopWord : stopWords) {
			sp.append(stopWord);
			if (i++ < (stopWords.size() - 1))
				sp.append("|");
		}
		String stopWordsPattern = "(" + sp.toString() + ")";

        //defines the number of occurrences of allowed chars between words
        String recur = "{1,3}";
        //the person name pattern or entity pattern can match more than one consecutive stop word or multiple appearances of [-.'] which is undesired
        //Hence we do another level of tokenisation with the pattern below
        multipleStopWordPattern = Pattern.compile("(\\s|^)("+stopWordsPattern+"["+allowedCharsOther+"]"+recur+"){2,}|(['-\\.]{2,})|'s(\\s|$)");

		//[\"'_:\\s]*
		//number of times special chars between words can recur
		String nps = "(" + nameP + "([" + allowedCharsOther + "]" + recur + "(" + nameP + "[" + allowedCharsOther + "]" + recur + "|(" + stopWordsPattern + "[" + allowedCharsOther + "]" + recur + "))*" + nameP + ")?)";
		entityPattern = Pattern.compile(nps);
		NER.log.info("EP: " + nps);
		//allow comma only once after the first word
		//nps = "(" + nameP + "([" + allowedCharsPerson + ",]" + recur + "(" + nameP + "[" + allowedCharsPerson + "]" + recur + ")*" + nameP + ")?)";
		//personNamePattern = Pattern.compile(nps);
    }

    public static void setStopWords(List<String> stopWords){
        CICTokenizer.stopWords = stopWords;
        initPattern();
    }

    /**
     * {@inheritDoc}
     * */
    @Override
	public List<Triple<String, Integer, Integer>> tokenize(String content) {
		List<Triple<String, Integer, Integer>> matches = new ArrayList<>();
		if (content == null)
			return matches;

		if (entityPattern == null) {
			initPattern();
		}
		Pattern namePattern = entityPattern;

		//we need a proper sentence splitter, as some of the names can contain period.
		String[] lines = content.split("\\n");
		//don't change the length of the content, so that the offsets are not messed up.
		content = "";
		for (String line : lines) {
			//for very short lines, new line is used as a sentence breaker.
			if (line.length() < 40)
				content += line + "%";
			else
				content += line + " ";
		}

		Span[] sentenceSpans = NLPUtils.tokenizeSentenceAsSpan(content);
        assert sentenceSpans != null;

        for (Span sentenceSpan : sentenceSpans) {
          	int sentenceStartOffset = sentenceSpan.getStart();
			String sent = sentenceSpan.getCoveredText(content).toString();
            //TODO: sometimes these long sentences are actually long list of names, which we cannot afford to lose.
            //Is there an easy way to test with the sentence is good or bad quickly and easy way to tokenize it further if it is good?
            //Sometimes there can be junk in the content such as a byte code or a randomly long string of characters,
            //we don't want to process such sentences and feed the many CIC tokens it would generate to the entity recogniser
            if(sent.length()>=2000)
                continue;

			Matcher m = namePattern.matcher(sent);
			while (m.find()) {
				if (m.groupCount() > 0) {
					String name = m.group(1);
					int start = m.start(1) + sentenceStartOffset, end = m.end(1) + sentenceStartOffset;
					//if the length is less than 3, accept only if it is all capitals.
					if (name.length() < 3) {
						String tt = FeatureGeneratorUtil.tokenFeature(name);
                        if (tt.equals("ac")) {
                            //this list contains many single-word bad names like Jan, Feb, Mon, Tue, etc.
                            if(DictUtils.tabooNames.contains(name.toLowerCase())) {
                                continue;
                            }
							matches.add(new Triple<>(name, start, end));
                        }
					}
					else {
                        //further cleaning to remove "'s" pattern
                        //@TODO: Can these "'s" be put to a good use? Right now, we are just tokenizing on them
                        String[] tokens = clean(name);
                        outer:
                        for (String token : tokens) {
                            int s = name.indexOf(token);
                            if (s < 0) {
                                log.error("Did not find " + token + " extracted and cleaned from " + name);
                                continue;
                            }
                            String lc = token.toLowerCase();
                            for(String bs: badSubstrings){
                                String lbs = bs.toLowerCase();
                                if(lc.equals(lbs) || lc.contains(" "+lbs+" ") || lc.startsWith(lbs+" ") || lc.endsWith(" "+lbs))
                                    continue outer;
                            }
                            //this list contains many single word bad names like Jan, Feb, Mon, Tue, etc.
                            if(DictUtils.tabooNames.contains(token.toLowerCase())) {
                                continue;
                            }
                            matches.add(new Triple<>(canonicalize(token), s, s + token.length()));
                        }
                    }
				}
			}
		}
		return matches;
	}

    /**
     * <ul>
     *     <li>cleans more than one extra space in the phrase</li>
     * </ul>
     * */
    static String canonicalize(String phrase){
        if(phrase.contains("  "))
            phrase = phrase.replaceAll("\\s{2,}"," ");
        return phrase;
    }

    /**
     * Ensures the sanity of the entity chunk, does the following checks:
     * <ul>
     *  <li>tokenizes on multiple stop words or more than one occurrence of {',-,.} (chars that are allowed in an entity word) or quote-s ie. 's, see multipleStopWordPattern</li>
     *  <li>ensures that the token does not end in space or period or hyphen</li>
     *  <li>If the chunk starts the sentence, then removes articles or other common words that start the sentence</li>
     *  <li>Drop the phrase if it is a member of English Dictionary</li>
     *  <li>Tokenize further on tokens that never had the history of being a noun.</li>
     * </ul>
     * @param phrase is the string that is to be cleaned
     * @return the tokenized, cleaned and filtered sub-chunks in the phrase passed.
     * */
    static String[] clean(String phrase){
        List<String> tokenL = new ArrayList<>();
        Matcher m = multipleStopWordPattern.matcher(phrase);
        int end = 0;
        while(m.find()){
            tokenL.add(phrase.substring(end, m.start()));
            end = m.end();
        }
        if(end!=phrase.length())
            tokenL.add(phrase.substring(end, phrase.length()));
        //we have all the split tokens, will have to filter now
        List<String> nts = new ArrayList<>();
        for (String t : tokenL) {
            t = t.replaceAll("^\\W+|\\W+$", "");

            //if the chunk is the first word then, double check the capitalisation
            if (DictUtils.fullDictWords.contains(t.toLowerCase())) {
                continue;
            }
            //remove common start words
            boolean hasCSW = false;
            do {
                String lc = t.toLowerCase();
                for (String cw : commonStartWords)
                    if (lc.startsWith(cw.toLowerCase() + " ")) {
                        t = t.substring(cw.length() + 1);
                        hasCSW = true;
                        break;
                    }
                if (!hasCSW) break;
                hasCSW = false;
            } while (true);

            boolean hasCEW = false;
            do {
                String lc = t.toLowerCase();
                for (String cw : commonEndWords)
                    if (lc.endsWith(" " + cw.toLowerCase())) {
                        t = t.substring(0, t.length() - cw.length() - 1);
                        hasCEW = true;
                        break;
                    }
                if (!hasCEW) break;
                hasCEW = false;
            } while (true);

            String[] words = t.split("\\s+");
            String segment = "";
            int currOff = 0;
            for (String word : words) {
                String temp = t.substring(currOff);
                String pad = t.substring(currOff, currOff + temp.indexOf(word));
                currOff += (pad + word).length();

                String lc = word.toLowerCase();
                if (!stopWords.contains(lc)) {
                    Multimap<String, Pair<String, Integer>> tdict = EnglishDictionary.getTagDictionary();

                    int freq = 0, nounCount = 0;
                    if (tdict != null)
                        for (Pair<String, Integer> p : tdict.get(lc)) {
                            freq += p.getSecond();
                            String tag = p.getFirst();
                            if ("NN".equals(tag) || "NNS".equals(tag) || "NNP".equals(tag) || "NNPS".equals(tag))
                                nounCount += p.getSecond();
                        }

                    //We miss on probable tokens (ADJ) like Iraqi, Turkish because they was mostly JJ.
                    // which I think is OK, else no complaints over CONLL testa
                    //Royal Meteorological Institute is tokenized on the middle word
                    //Also tokenizes on Limited
                    if (tdict != null && (((float) nounCount / freq) < 0.05)) {
                        if (segment.length() > 0)
                            nts.add(segment);
                        segment = "";
                    } else {
                        //don't add padding when the segment is empty
                        if (segment.length() > 0)
                            segment += pad;
                        segment += word;
                    }
                } else {
                    if (segment.length() > 0)
                        segment += pad;
                    segment += word;
                }
            }
            if (segment.length() > 0)
                nts.add(segment);
        }
        return nts.toArray(new String[nts.size()]);
    }

    static void test(){
        Tokenizer tokenizer = new CICTokenizer();
        String[] contents = new String[]{
                "A book named Information Retrieval by Christopher Manning",
                "I have visited Museum of Modern Arts aka. MoMA, MMA, MoMa",
                "Sound of the Music and Arts program by SALL Studios",
                "Performance by Chaurasia, Hariprasad was great!",
                "Dummy of the and Something",
                "Mr. HariPrasad was present.",
                "We traveled through A174 Road.",
                "The MIT school has many faculty members who were awarded the Nobel Prize in Physics",
                "We are celebrating Amy's first birthday",
                "We are meeting at Barnie's and then go to Terry's",
                "Patrick's portrayal of Barney is wonderful",
                "He won a gold in 1874 Winter Olympics",
                "India got independence in 1947",
                ">Holly Crumpton in an interview said he will never speak to public directly",
                "The popular Ellen de Generes show made a Vincent van Gogh themed episode",
                "Barack-O Obama is the President of USA",
                "CEO--Sundar attended a meeting in Delhi",
                "Subject: Jeb Bush, the presidential candidate",
                "From: Ted Cruz on Jan 15th, 2015",
                "Harvard Law School\n\nDr. West is on a holiday trip now.",
                "I met Frank'O Connor in the CCD",
                "I have met him in the office yesterday",
                "Annapoorna Residence,\nHouse No: 1975,\nAlma Street,\nPalo Alto,\nCalifornia",
                //It fails here, because OpenNLP sentence model marks Mt. as end of the sentence.
                "I have been thinking about it, and I should say it out loud. I am going to climb Mt. \nEverest",
                "Met Mr. Robert Creeley at his place yesterday",
                "Dear Folks, it is party time!",
                "Few years ago, I wrote an article on \"Met The President\"",
                "This is great! I am meeting with Barney   Stinson",
                "The Department of Geology is a hard sell!",
                "Sawadika!\n" +
                        "\n" +
                        "fondly,\n\n",
                "Judith C Stern MA PT\n" +
                        "AmSAT Certified Teacher of the Alexander Technique\n" +
                        "31 Purchase Street\n" +
                        "Rye NY 10580",
                "Currently I am working in a Company",
                "Unfortunately I cannot attend the meeting",
                "Personally I prefer this over anything else",
                "On Behalf of Mr. Spider Man, we would like to apologise",
                "Quoting Robert Creeley, a Black Mountain Poet",
                "Hi Mrs. Senora, glad we have met",
                "Our XXX Company, produces the best detergents in the world",
                "My Thought on Thought makes an infinite loop",
                "Regarding The Bangalore Marathon, it has been cancelled due to stray dogs",
                "I am meeting with him in Jan, and will request for one in Feb, will say OK to everything and disappear on the very next Mon or Tue, etc.",
                "North Africa is the northern portion of Africa",
                "Center of Evaluation has developed some evaluation techniques.",
                "Hi Professor Winograd, this is your student from nowhere",
                ">> Hi Professor Winograd, this is your student from nowhere",
                "Why Benjamin Netanyahu may look",
                "I am good Said Netanyahu",
                "Even Netanyahu was present at the party",
                "The New York Times is a US based daily",
                "Do you know about The New York Times Company that brutally charges for Digital subscription",
                "Fischler proposed EU-wide measures after reports from Britain and France that under laboratory conditions sheep could contract Bovine Spongiform Encephalopathy ( BSE ) -- mad cow disease",
                "Spanish Farm Minister Loyola de Palacio had earlier accused Fischler at an EU farm ministers ' meeting of causing unjustified alarm through \" dangerous generalisation .",
                "P.V. Krishnamoorthi",
                "Should Rubin be told about this?",
                "You are talking to Robert Who?",
                "I will never say a thing SAID REBECCA HALL",
                "\" Airport officials declared an emergency situation at the highest level and the fire brigade put out the flames while the plane was landing , he said .",
                "Brussels received 5.6 cm ( 2.24 inches ) of water in the past 24 hours -- compared to an average 7.4 cm ( 2.96 inches ) per month -- but in several communes in the south of the country up to 8 cm ( 3.2 inches ) fell , the Royal Meteorological Institute ( RMT ) said",
                "Danish cleaning group ISS on Wednesday said it had signed a letter of intent to sell its troubled U.S unit ISS Inc to Canadian firm Aaxis Limited",
                "That was one hell of a Series!",
                "I am from India said No one.",
                "Rachel and I went for a date in the imaginary land of geeks.",
                "I'm the one invited."
        };
        String[][] tokens = new String[][]{
                new String[]{"Information Retrieval","Christopher Manning"},
                new String[]{"Museum of Modern Arts","MoMA","MMA","MoMa"},
                new String[]{"Music and Arts", "SALL Studios"},
                new String[]{"Chaurasia, Hariprasad"},
                new String[]{"Something"},
                new String[]{"Mr. HariPrasad"},
                new String[]{"A174 Road"},
                new String[]{"MIT","Nobel Prize in Physics"},
                new String[]{"Amy"},
                new String[]{"Barnie","Terry"},
                new String[]{"Patrick","Barney"},
                new String[]{"Winter Olympics"},
                new String[]{"India"},
                new String[]{"Holly Crumpton"},
                new String[]{"Ellen de Generes","Vincent van Gogh"},
                new String[]{"Barack-O Obama", "President of USA"},
                new String[]{"CEO","Sundar","Delhi"},
                new String[]{"Jeb Bush"},
                //Can we do a better job here? without knowing that Ted Cruz is a person.
                new String[]{"Ted Cruz"},
                new String[]{"Harvard Law School","Dr. West"},
                new String[]{"Frank'O Connor","CCD"},
                new String[]{},
                new String[]{"Annapoorna Residence","House No","Alma Street","Palo Alto","California"},
                new String[]{"Mt. Everest"},
                new String[]{"Mr. Robert Creeley"},
                new String[]{},
                new String[]{"President"},
                new String[]{"Barney Stinson"},
                new String[]{"Department of Geology"},
                new String[]{"Sawadika"},
                new String[]{"Judith C Stern MA PT","AmSAT Certified Teacher","Alexander Technique","Purchase Street","Rye NY"},
                new String[]{"Company"},
                new String[]{},
                new String[]{},
                new String[]{"Mr. Spider Man"},
                new String[]{"Robert Creeley", "Black Mountain Poet"},
                new String[]{"Mrs. Senora"},
                new String[]{"XXX Company"},
                new String[]{"Thought"},
                new String[]{"Bangalore Marathon"},
                new String[]{},
                new String[]{"North Africa","Africa"},
                new String[]{"Center of Evaluation"},
                new String[]{"Professor Winograd"},
                new String[]{"Professor Winograd"},
                new String[]{"Benjamin Netanyahu"},
                new String[]{"Netanyahu"},
                new String[]{"Netanyahu"},
                new String[]{"New York Times","US"},
                new String[]{"New York Times Company","Digital"},
                new String[]{"Fischler","EU-wide","Britain and France","Bovine Spongiform Encephalopathy","BSE"},
                new String[]{"Spanish Farm Minister Loyola de Palacio","Fischler","EU"},
                new String[]{"P. V. Krishnamoorthi"},
                new String[]{"Rubin"},
                new String[]{"Robert"},
                new String[]{"REBECCA HALL"},
                new String[]{},
                new String[]{"Royal Meteorological Institute","RMT","Brussels"},
                new String[]{"Danish","ISS","ISS Inc","Canadian","Aaxis Limited"},
                new String[]{},
                new String[]{"India"},
                new String[]{"Rachel"},
                new String[]{}
        };
        for(int ci=0;ci<contents.length;ci++){
            String content = contents[ci];
            List<String> ts = Arrays.asList(tokens[ci]);
            //want to specifically test person names tokenizer for index 3.
            Set<String> cics = tokenizer.tokenizeWithoutOffsets(content);
            boolean missing  = false;
            for(String cic: cics)
                if(!ts.contains(cic)) {
                    missing = true;
                    break;
                }
            if(cics.size()!=ts.size() || missing) {
                String str = "------------\n" +
                             "Test failed!\n" +
                             "Content: "+content+"\n"+
                             "Expected tokens: "+ts+"\n"+
                             "Found: "+cics+"\n";
                System.err.println(str);
            }
        }
        System.out.println("All tests done!");
    }

	public static void main(String[] args) {
		test();
	}
}
