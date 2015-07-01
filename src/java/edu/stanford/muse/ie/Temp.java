package edu.stanford.muse.ie;

import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.webapp.SimpleSessions;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.*;

public class Temp {
	public static void main(String[] args) {
		Archive archive = null;
		try {
			archive = SimpleSessions.readArchiveIfPresent(System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user");
		} catch (Exception e) {
			e.printStackTrace();
		}
		Collection<EmailDocument> docs = (Collection) archive.getAllDocs();
		Indexer indexer = archive.indexer;
		AddressBook ab = archive.addressBook;
		NameFinderME bFinder = null;
		String BOOK = "book", UNIV = "university", MUSIC_ARTIST = "musical_artist", HOTEL = "hotel", MUSEUM = "museum", COMPANY = "company", AWARD = "award", MOVIE = "movie", PEOPLE = "people";
		SentenceDetectorME sentenceDetector;
		InputStream SentStream = Temp.class.getClassLoader().getResourceAsStream("models/en-sent.bin");
		SentenceModel model = null;
		TokenizerME tokenizer = null;
		CharArraySet stopWordsSet = StopAnalyzer.ENGLISH_STOP_WORDS_SET;
		String[] stopWords = new String[stopWordsSet.size()];
		Iterator it = stopWordsSet.iterator();
		int j = 0;
		while (it.hasNext()) {
			char[] stopWord = (char[]) it.next();
			stopWords[j++] = new String(stopWord);
		}

		String stopWordsList = "";
		for (int i = 0; i < stopWords.length; i++) {
			String stopWord = stopWords[i];
			stopWordsList += stopWord;
			if (i < (stopWords.length - 1))
				stopWordsList += "|";
		}
		stopWordsList += "";

		String rt = null, rc = null;
		String[] types, colors;
		if (rt == null || rc == null) {
			types = new String[] { BOOK, UNIV, MUSEUM, COMPANY, AWARD, MOVIE };
			colors = new String[] { "green", "red", "deepskyblue", "orange", "violet", "fuchsia" };
		} else {
			types = new String[] { rt };
			colors = new String[] { rc };
		}

		String[] modelFiles = new String[types.length];
		String modeldir = "";//edu.stanford.epadd.ModeConfig.BASE_DIR;
		for (int i = 0; i < types.length; i++)
			modelFiles[i] = modeldir + "models/en-ner-" + types[i] + ".bin";

		//modelFiles = new String[]{"models/en-ner-book.bin","models/en-ner-university.bin","models/en-ner-museum.bin","models/en-ner-company.bin","models/en-ner-award.bin","models/en-ner-movies.bin"};
		//String[] types = new String[]{PEOPLE};
		//String[] colors = new String[]{"red"};
		//Map<String,Pattern> patterns = new HashMap<String,Pattern>();
		//Map<String,Pattern> listPatterns = new HashMap<String,Pattern>();
		Map<String, String> stopWordsPattern = new HashMap<String, String>();

		for (String type : types) {
			String nameP = "[A-Z]+[a-z':]*";
			String allowedChars = "[\\s\\-\\&\\.]";
			String swPattern = "(" + stopWordsList + ")";//"[a-z]+";
			if (type.equals(BOOK) || type.equals(MOVIE))
				swPattern = "(but|be|with|such|then|for|no|will|not|are|and|their|if|this|on|into|a|there|in|that|they|was|it|an|the|as|at|these|to|of)";
			//for universities
			else if (type.equals(UNIV) || type.equals(MUSEUM))
				swPattern = "(for|and|a|the|of)";
			else if (type.equals(MUSIC_ARTIST))
				swPattern = "";
			else if (type.equals(AWARD))
				swPattern = "(of|and|a|an|on|in)";
			else if (type.equals(COMPANY))
				swPattern = "(of|and|a|an|on)";
			else if (type.equals(HOTEL))
				swPattern = "(of)";
			else
				swPattern = "";

			String bookPattern1 = "[\"'_:\\-\\s]*" + nameP + "(" + allowedChars + "+(" + nameP + allowedChars + "+|" + stopWordsPattern + allowedChars + "+)*" + nameP + ")?";
			String bookPattern2 = "[\"'_:\\-\\s]*" + nameP + "(" + allowedChars + "+(" + nameP + allowedChars + "+|" + stopWordsPattern + allowedChars + "+)*[\"'_:\\-\\s]*[,-;])?";
			String bookPatternString = "(" + bookPattern1 + "|" + bookPattern2 + ")";
			//String bookListPatternString = "("+bookPatternString+"|"+"\\W)+";
			//Pattern bookPattern = Pattern.compile(bookPatternString);
			//Pattern bookListPattern = Pattern.compile(bookListPatternString);
			//patterns.put(type,bookPattern);
			//listPatterns.put(type,bookListPattern);
			stopWordsPattern.put(type, swPattern);
		}

		//String[] modelFiles = new String[]{"models/en-ner-people.bin"};
		NameFinderME[] finders = new NameFinderME[modelFiles.length];
		Map<String, Set<String>> entities = new HashMap<String, Set<String>>();
		try {
			int i = 0;
			for (String modelFile : modelFiles) {
				//InputStream pis = NLPUtils.class.getClassLoader().getResourceAsStream(modelFiles[i]);
				System.err.println("Loading: " + modelFiles[i]);
				TokenNameFinderModel nmodel = new TokenNameFinderModel(Temp.class.getClassLoader().getResourceAsStream(modelFiles[i]));
				finders[i] = new NameFinderME(nmodel);
				i++;
			}
			model = new SentenceModel(SentStream);
			InputStream tokenStream = Temp.class.getClassLoader()
					.getResourceAsStream("models/en-token.bin");
			TokenizerModel modelTokenizer = new TokenizerModel(tokenStream);
			tokenizer = new TokenizerME(modelTokenizer);
		} catch (Exception e) {
			e.printStackTrace();
		}
		sentenceDetector = new SentenceDetectorME(model);

		int l = 0, numNames = 0;
		List<Integer> cmup = new ArrayList<Integer>(), nerup = new ArrayList<Integer>();
		//Set<String> gnerNames = new HashSet<String>();
		//Map<String,Integer> allbooks = new HashMap<String,Integer>();
		String html = "";
		for (EmailDocument ed : docs) {
			int x = ed.sentOrReceived(ab);
			//	 	if((x&EmailDocument.SENT_MASK)==0)
			//	 		continue;

			String content = indexer.getContents(ed, false);
			content = content.replaceAll("^>+.*", "");
			content = content.replaceAll("\\n\\n", ". ");
			content = content.replaceAll("\\n", " ");
			content.replaceAll(">+", "");
			String[] sents = sentenceDetector.sentDetect(content);
			//	 	 	if(l++>1000)
			//	 	 		break;
			l++;
			if (l > 0 && l % 1000 == 0) {
				System.err.println("Processed: " + l);
				System.err.println("#" + numNames + " found");
				for (String t : types)
					if (entities.containsKey(t))
						System.err.println("type:" + t + "\t" + "#" + entities.get(t).size());
			}
			int numRecognised = 0;

			for (int i = 0; i < sents.length; i++) {
				String text = sents[i];
				Span[] tokSpans = tokenizer.tokenizePos(text);
				// Sometimes there are illformed long sentences in the text that
				// give hard time to NLP.
				if (tokSpans.length > 1288)
					continue;

				String tokens[] = new String[tokSpans.length];
				for (int t = 0; t < tokSpans.length; t++) {
					tokens[t] = text.substring(
							Math.max(0, tokSpans[t].getStart()),
							Math.min(text.length(), tokSpans[t].getEnd()));
				}
				for (int fi = 0; fi < finders.length; fi++) {
					NameFinderME finder = finders[fi];
					Span[] bSpans = finder.find(tokens);
					Set<String> cb = new HashSet<String>();

					for (Span span : bSpans) {
						String name = "", pname = "";
						int start = -1, end = -1;
						for (int m = span.getStart(); m < span.getEnd(); m++) {
							name += text.substring(
									tokSpans[m].getStart(),
									tokSpans[m].getEnd());
							pname += text.substring(
									tokSpans[m].getStart(),
									tokSpans[m].getEnd());
							if (m < (span.getEnd() - 1)) {
								name += "\\W+";
								pname += " ";
							}
						}
						start = span.getStart();
						end = span.getEnd();

						//name = Util.cleanForRegex(name);
						//System.err.println("Replacing: "+name);
						cb.add(name);
						//candidate name
						String cname = name, cpname = pname;
						String lastcname = name, lastcpname = pname;
						boolean clean = false;
						for (int nt = end; nt < tokens.length; nt++) {
							String token = tokens[nt];
							char fc = token.charAt(0);
							//System.err.println("fc: "+fc+", token: "+token+", name: "+name);

							if (stopWordsPattern.get(types[fi]).contains(token)) {
								cname += "\\W+" + token;
								cpname += " " + token;
								clean = false;
							}
							else if (fc >= 'A' && fc <= 'Z') {
								cname += "\\W+" + token;
								cpname += " " + token;
								clean = true;
							}
							else {
								boolean allowedT = false;
								if (token.equals("'") || token.equals(":") || token.equals("&")) {
									allowedT = true;
									clean = false;
								}
								if (!allowedT)
									break;
							}
							if (clean) {
								lastcname = cname;
								lastcpname = pname;
							}
						}
						if (clean) {
							name = cname;
							pname = cpname;
						} else {
							name = lastcname;
							pname = lastcpname;
						}

						if (!entities.containsKey(types[fi]))
							entities.put(types[fi], new HashSet<String>());

						entities.get(types[fi]).add(pname);
						text = text.replaceAll("\"", "").replaceAll(">|<", " ");
						name = name.replaceAll("\"", "");
						pname = pname.replaceAll("\"", "");
						//out.println(name+"::: &nbsp&nbsp");
						try {
							String color = colors[fi];
							text = text.replaceAll(name, "<span style=\"color:" + color + "\"> {" + types[fi] + " : " + pname + "}</span>");
							break;
						} catch (Exception e) {
							System.err.println("Exception while replacing pattern");
						}
					}
					numNames += bSpans.length;
					if (bSpans.length > 0) {
						html += text + "<br>";
					}
				}
			}

			try {
				FileWriter fw = new FileWriter("fine-types.html");
				fw.write(html);
				fw.close();
				int tu = 0;
				for (String t : entities.keySet()) {
					PrintWriter pw = new PrintWriter(new File(t + ".txt"));
					if (entities.get(t) != null) {
						for (String e : entities.get(t))
							pw.println(e);
						tu += entities.get(t).size();
					}
					pw.close();
				}
				System.err.println("Toatal: " + numNames + " found" + " unique names: #" + tu);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
