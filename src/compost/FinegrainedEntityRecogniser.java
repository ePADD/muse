package edu.stanford.muse.ie;

import edu.stanford.muse.Config;
import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.util.JSONUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.webapp.SimpleSessions;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.featuregen.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author vihari
 * [Experimental]
 * Trains an OpenNLP Maxent model for different entity types, see KNownClasses for the types that are experimented with
 * It works by first generating training data from sentences from archive, the seed set for various types are from a Gazette (DBpedia instance types) lookup in the archive.
 * The model so trained can recognize more instances of the type in the archive (depending on the type, can recognize four or more fold).
 * I found that the trained model is highly dependant on the initial seed set and the trained model did not do well in recognizing certain types such as movies and companies
 * The technical report at http://vihari.github.io/personal_website/xwww.pdf discusses at length the approach and results.

 * Fine-grained entity recogniser
 * Default fine-grained types are: book, universities, museums, movies, awards, company
 */
public class FinegrainedEntityRecogniser implements StatusProvider {
	/**
	 * 
	 */
	private static final long	serialVersionUID	= -2810490637708071026L;
	String						status				= "";
	double						pctComplete			= 0;
	boolean						cancelled			= false;
	Collection<EmailDocument>	docs				= null;
	Archive archive = null;
	public static String		TRAIN_FILE			= "en-ner-finetypes.train";

	public FinegrainedEntityRecogniser() {
		String dirName = System.getProperty("muse.dirname");
		//if is being run without web context.
		if (dirName == null)
			dirName = "epadd-appraisal";

		String BASE_DIR = System.getProperty("user.home") + File.separator + dirName + File.separator + "user" + File.separator;

		TRAIN_FILE = BASE_DIR + File.separator + TRAIN_FILE;
	}

	public void testNER(String modelFile) {
		try {
			TokenNameFinderModel nmodel = new TokenNameFinderModel(new FileInputStream(modelFile));
			NameFinderME nameFinder = new NameFinderME(nmodel);
			InputStream tokenStream = Config.getResourceAsStream("models/en-token.bin");
			TokenizerModel modelTokenizer = new TokenizerModel(tokenStream);
			TokenizerME tokenizer = new TokenizerME(modelTokenizer);

			// now test if it can detect the sample sentences
			String[] sentences = new String[] { "He graduated from William's",
					"I read Mirrors",
					"Inferno by Dan Brown is just awesome",
					"Congratulations on receiving Nobel prize",
					"I have recently been to National Arts gallery",
					"I ordered a book on Amazon",
					"I was down with cold and Fever",
					"My friend was recently diagonised with flu"
			};
			for (String sentence : sentences) {
				System.err.println(sentence);
				String[] tokens = tokenizer.tokenize(sentence);
				Span[] names1 = nameFinder.find(tokens);
				for (Span s : names1)
					System.err.println(sentence.substring(s.getStart(), s.getEnd()) + ", type: " + s.getType());
				System.err.println("*************");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void trainNER() {
		pctComplete = 0.75;
		//------------training NER start--------------------
		AdaptiveFeatureGenerator featureGenerator = new CachedFeatureGenerator(
				new AdaptiveFeatureGenerator[] {
						new WindowFeatureGenerator(new TokenFeatureGenerator(), 2, 2),
						new WindowFeatureGenerator(new TokenClassFeatureGenerator(true), 2, 2),
						new OutcomePriorFeatureGenerator(),
						new PreviousMapFeatureGenerator(),
						new BigramNameFeatureGenerator(),
						new SentenceFeatureGenerator(true, false)
				});

		String modeldir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user" + File.separator + "models";
		if (!new File(modeldir).exists())
			new File(modeldir).mkdir();

		String modelFile = modeldir + File.separator + "en-ner-finetypes.bin";
		TrainingParameters params = TrainingParameters.defaultParams();
		params.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(50));
		TokenNameFinderModel nermodel = null;
		ObjectStream<NameSample> sampleStream = null;
		status = "Wrote the ner training file";

		try {
			Charset charset = Charset.forName("UTF-8");
			ObjectStream<String> lineStream = new PlainTextByLineStream(new FileInputStream(TRAIN_FILE), charset);
			sampleStream = new NameSampleDataStream(lineStream);
			nermodel = NameFinderME.train("en", "default", sampleStream, params, featureGenerator, null);
			CmdLineUtil.writeModel("name finder", new File(modelFile), nermodel);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				sampleStream.close();
			} catch (Exception e) {

			}
		}
		testNER(modelFile);
		System.err.println("Done processing and training; browse at: /bookMETest.jsp?type=finetypes&color=red");
		status = "Done processing and training; browse at: /bookMETest.jsp?type=finetypes&color=red";
		pctComplete = 1;
	}

	public void generateTrainingData(Map<String, NameInfo> entities, Set<String> kws) {
		status = "Generating train file";
		SentenceDetectorME sentenceDetector;
		InputStream SentStream = FinegrainedEntityRecogniser.class.getClassLoader().getResourceAsStream("models/en-sent.bin");
		SentenceModel model = null;
		try {
			model = new SentenceModel(SentStream);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Exception in init'ing sentence model");
		}
		sentenceDetector = new SentenceDetectorME(model);

		PrintStream tsw = null;
		try {
			tsw = new PrintStream(new File(TRAIN_FILE), "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
		}

		int numPositiveSamples = 0, numNegativeSamples = 0;
		int i = 0;

		status = "Preparing the training file";
		for (EmailDocument ed : docs) {
			status = "Processed: " + i + "/" + docs.size();
			String content = archive.getContents(ed, true);
			content = content.replaceAll("^>+.*", "");
			content = content.replaceAll("\\n\\n", ". ");
			content = content.replaceAll("\\n", " ");

			i++;
			if (i % 1000 == 0) {
				System.err.println("Processed (TrainFileGeneration): " + i + " of " + docs.size());
				status = "Processed (TrainFileGeneration): " + i + " of " + docs.size();
			}
			pctComplete = 0.5 + ((double) i / docs.size()) * 0.25;
			System.err.println(pctComplete);
			//	 		if(i>1500)
			//	 			break;
			String[] sentences = sentenceDetector.sentDetect(content);
			boolean instanceFound = false;
			for (String sent : sentences) {
				//these sentences give hard time to NLP while training.
				if (sent.length() > 1500)
					break;
				//System.err.println("Starting to find matches");
				String sample = sent;
				Set<String> matchingEntities = new HashSet<String>();

				for (String k : entities.keySet()) {
					String entity = entities.get(k).title;
					if (sent.contains(entity)) {
						//multi-word
						if (entity.contains(" ")) {
							matchingEntities.add(k);
						}
					}
				}

				Set<String> types = new HashSet<String>();
				for (String me : matchingEntities) {
					boolean contained = false;
					for (String ob : matchingEntities)
						if (ob.contains(me) && !me.equals(ob))
							contained = true;
					//replace with the biggest title known
					if (contained)
						continue;

					String ent = entities.get(me).title;
					String type = entities.get(me).type;
					types.add(type);
					try {
						sample = sample.replaceAll(ent, " <START:" + type + "> " + ent + " <END> ");
						instanceFound = true;
						numPositiveSamples++;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				if (instanceFound) {
					for (String type : types) {
						sample = sample.replaceAll("<END>  ", "<END> ");
						sample = sample.replaceAll("  <START:" + type + ">", " <START:" + type + ">");
					}
					tsw.println(sample);
					//acw.println(acontext);
				}
				//Write as a negative sample only if none of the other syns of book are absent.
				if (!instanceFound) {
					Boolean typeRelated = false;
					String lc = sent.toLowerCase();
					for (String kw : kws) {
						if (lc.contains(kw)) {
							typeRelated = true;
							break;
						}
					}
					if (!typeRelated) {
						tsw.println(sent);
						numNegativeSamples++;
					}
				}
			}
		}
		tsw.close();
		status = "Generated and dumped the train file";
		System.err.println("Wrote: #" + numPositiveSamples + " positive samples and #" + numNegativeSamples + " negative samples to " + new File(TRAIN_FILE).getAbsolutePath());
	}

	/** Generates training data and trains the NER */
	public void trainNER(Archive archive) {
		this.archive = archive;
		String[] ftypes = new String[] { KnownClasses.BOOK, KnownClasses.DISEASE, KnownClasses.UNIV, KnownClasses.MUSEUM, KnownClasses.MOVIE, KnownClasses.AWARD, KnownClasses.COMPANY };
		KnownClasses kc = new KnownClasses();
		Set<String> keywords = new HashSet<String>();
		if (kc.syns != null) {
			for (String ftype : ftypes) {
				if (kc.syns.get(ftype) != null)
					for (String s : kc.syns.get(ftype))
						keywords.add(s);
				else
					System.err.println("Did not find syns for type: " + ftype + ", continuing....");
			}
		} else
			System.err.println("KnownClasses class is not properly initialised");

		if (cancelled)
			return;

		try {
			docs = (Collection) archive.getAllDocs();
			Map<String, NameInfo> hits = NameTypes.computeNameMap(archive, docs);
			System.err.println("Assigning types to hits");
			status = "Assigning types to hits";
			NameTypes.readTypes(hits);
			if (cancelled)
				return;

			status = "Done assigning types to entities";
			pctComplete = 0.125;

			//interested entity types
			Map<String, NameInfo> ientities = new HashMap<String, NameInfo>();
			for (String h : hits.keySet()) {
				NameInfo ni = hits.get(h);
				if (ni.type.equals("notype"))
					continue;
				for (String ftype : ftypes) {
					if (ni.type.toLowerCase().contains(ftype)) {
						ni.type = ftype;
						ientities.put(ni.title, ni);
						break;
					}
				}
			}

			if (cancelled)
				return;
			Map<String, Set<String>> contextMap = new HashMap<String, Set<String>>();
			int i = 0;
			//System.err.println("Int entitites: " + ientities.keySet());
			for (EmailDocument ed : docs) {
				if (i != 0 && i % 1000 == 0) {
					System.err.println("Processed(seedinstancegen): " + i + " of " + docs.size());
					status = "Processed(seedinstancegen): " + i + " of " + docs.size();
				}
				pctComplete = 0.125 + ((double) i / docs.size()) * 0.125;

				i++;

				String content = archive.getContents(ed, false);
				content = content.replaceAll("^>+.*", "");
				content = content.replaceAll("\\n\\n", ". ");
				content = content.replaceAll("\\n", " ");
				content.replaceAll(">+", "");

				boolean found = false;
				Set<String> matchingEntities = new HashSet<String>();
				for (String entity : ientities.keySet())
					if (content.contains(entity)) {
						found = true;
						matchingEntities.add(entity);
					}
				if (!found)
					continue;

				List<String> names = archive.getNamesForDocId(ed.getUniqueId(), Indexer.QueryType.ORIGINAL);
				for (String me : matchingEntities) {
					if (!contextMap.containsKey(me))
						contextMap.put(me, new HashSet<String>());
					contextMap.get(me).addAll(names);
				}
			}

			Map<String, String> matches = new HashMap<String, String>();
			Map<String, NameInfo> eFiltered = new HashMap<String, NameInfo>();

			if (cancelled)
				return;
			status = "starting to score based on context";
			i = 0;
			for (String entity : contextMap.keySet()) {
				System.err.println("Scored: " + i + " pages of " + contextMap.size());
				status = "Scored: " + i + " pages of " + contextMap.size();
				pctComplete = 0.25 + ((double) i / contextMap.size()) * 0.25;
				i++;
				String[] cc = contextMap.get(entity).toArray(new String[contextMap.get(entity).size()]);
				System.err.println("length of context: " + cc.length);
				String link = "http://en.wikipedia.org/wiki/" + ientities.get(entity).title.replaceAll(" ", "_");
				Pair<String, Double> score = Util.scoreWikiPage(link, cc);
				if (score != null)
					System.err.println("Link: " + link + ", score: " + score.second);
				if (score == null)
					score = new Pair<String, Double>("Timed out", 0.0);

				if (score.second > 0)
					eFiltered.put(entity, ientities.get(entity));
				matches.put(entity, score.first);
				//this is a long loop, hence check every time.
				if (cancelled)
					return;
			}
			if (cancelled)
				return;
			status = "Done scoring based on context";
			generateTrainingData(eFiltered, keywords);
			status = "Starting to train NER";
			if (cancelled)
				return;
			trainNER();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getStatusMessage() {
		String s = status + ", pct: " + (int) (pctComplete * 100);
		return JSONUtils.getStatusJSON(s, (int) (pctComplete * 100), 0, 0);
	}

	@Override
	public void cancel() {
		System.err.println("Request for cancel made, cancelling...");
		cancelled = true;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	public static void main(String[] args) {
		try {
			FinegrainedEntityRecogniser fer = new FinegrainedEntityRecogniser();
			String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user";
			Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
			fer.trainNER(archive);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
