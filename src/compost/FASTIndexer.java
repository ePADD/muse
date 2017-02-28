package edu.stanford.muse.ie;

import com.google.gson.Gson;
import edu.stanford.muse.Config;
import edu.stanford.muse.util.Util;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates a Lucene index from FAST files. To be run one-time only, whenever there is an update to FAST.
 * Fast index is downloadable from: http://www.oclc.org/research/themes/data-science/fast/download.html
 */
public class FASTIndexer {

	private class Stats {
		public int	numWiki	= 0, numVIAF = 0, numLOC = 0, numUnknown = 0, numGeo = 0, numLatLon = 0;
		// number of words with struff inside brackets -> numBrackets.
		public int	totalItems	= 0, numMultipleWords = 0, numSingleWords = 0, numNames = 0, numWithBrackets = 0;

		public void collectStats(FASTRecord ft) {
			if (ft.wikiSource)
				numWiki++;
			if (ft.LOCSource)
				numLOC++;
			if (ft.VIAFSource)
				numVIAF++;
			if (ft.GEOSource)
				numGeo++;
			if (ft.unknownSource)
				numUnknown++;
			totalItems++;
			Set<String> names = FASTRecord.getNames(ft);
			for (String name : names) {
				if (name.contains(" "))
					numMultipleWords++;
				else
					numSingleWords++;
				numNames++;
				if (name.contains("("))
					numWithBrackets++;
			}
		}

		public String getPercent(int item, int total) {
			return new DecimalFormat("#.##").format((item * 100.0 / (double) total));
		}

		@Override
		public String toString() {
			String summary = "";
			summary += ":::::::::::::::::::\n";
			summary += "Total number of items read: " + totalItems + "\n";
			summary += "Total number of names: " + numNames + "\n";
			summary += "Items with single word names: " + numSingleWords + " :: " + getPercent(numSingleWords, numNames) + "\n";
			summary += "Items with multiple word names: " + numMultipleWords + " :: " + getPercent(numMultipleWords, numNames) + "\n";
			summary += "Entities with latitude and longitude: " + numLatLon + " :: " + getPercent(numLatLon, totalItems) + "\n";
			summary += "Items with brackets in name: " + numWithBrackets + " :: " + getPercent(numWithBrackets, numNames) + "\n";
			summary += "Items with Wikipedia source: " + numWiki + " :: " + getPercent(numWiki, totalItems) + "\n";
			summary += "Items with VIAF source: " + numVIAF + " :: " + getPercent(numVIAF, totalItems) + "\n";
			summary += "Items with LOC source: " + numLOC + " :: " + getPercent(numLOC, totalItems) + "\n";
			summary += "Items with GeoNames source: " + numGeo + " :: " + getPercent(numGeo, totalItems) + "\n";
			summary += "Items with Unknown source: " + numUnknown + " :: " + getPercent(numUnknown, totalItems) + "\n";
			summary += ":::::::::::::::::::\n";
			return summary;
		}
	}

	// though some sh id's exist in the FASTTopical.nt, they are only for label
	// and contain no extra info.
	static Pattern			subjects	= Pattern.compile("^\\d+$");

	static IndexWriter		w			= null;
	static Set<String>		sids		= new HashSet<String>();

	static String			indexPath	= Config.FAST_INDEX;

	// In debug mode prints out all the names, to check if they further need
	// cleaning.
	Boolean					debug		= false;
	Stats					stats		= new Stats();
	String					FASTDbFile;
	Map<String, FASTRecord>	topics;
	String					fastType;

	/**
	 * @param append
	 *            to existing index(boolean), name matching patterns and class
	 *            that impleme FASTType
	 */
	public FASTIndexer(boolean appendToIndex, String dbFile, String fastType) {
		// map from fast id to FASTTopic, doesn't store more than THRESHOLD
		// entries
		// at any instance FATTopical.nt contains all the entries clustered so
		// there
		// is no need to keep track of.
		topics = new HashMap<String, FASTRecord>();
		stats = new Stats();

		if (Util.nullOrEmpty(fastType)) {
			System.err.println("FASTType is null... cannot proceed.");
			return;
		}
		this.fastType = fastType;
		if (Util.nullOrEmpty(dbFile)) {
			System.err.println("Error in initialisation of FASTIndexer: Null/empty dbFile");
			return;
		}
		FASTDbFile = dbFile;

		// initialize the indexer
		try {
			StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_47);
			Directory index = FSDirectory.open(new File(indexPath));
			IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_47, analyzer);
			if (appendToIndex)
				iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
			else
				iwc.setOpenMode(OpenMode.CREATE);
			w = new IndexWriter(index, iwc);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (w == null) {
			System.err.println("Couldn't open index files... exiting.");
			//System.exit(0);
		}
	}

	/* <http://id.loc.gov/authorities/subjects/sh2004005400> -> sh2004005400 */
	public static String extractTail(String s) {
		String s1 = s;
		try {
			s1 = s1.substring(s1.lastIndexOf("/") + 1, s.length() - 1); // s.length()-1
																		// to
																		// exclude
																		// the
																		// trailing
																		// ">"
																		// character
		} catch (Exception e) {
			System.err.println("unable to extract tail from string " + s);
			return "dummy";
		}
		return s1;
	}

	// will parse Vila Dinar\u00E9s, Pau to map the \u00E9 to the right unicode
	// char
	public static String convertUTF16(String s) {
		List<Character> out = new ArrayList<Character>();
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '\\' && (i + 5 < s.length()) && s.charAt(i + 1) == 'u') {
				String seq = Character.toString(s.charAt(i + 2))
						+ Character.toString(s.charAt(i + 3))
						+ Character.toString(s.charAt(i + 4))
						+ Character.toString(s.charAt(i + 5));
				ch = (char) Integer.parseInt(seq, 16);
				i += 5;
			}

			out.add(ch);
		}
		StringBuilder sb = new StringBuilder();
		for (char c : out)
			sb.append(c);
		return sb.toString();
	}

	public void appendOrupdate(String subject, String predicate, String object) {
		String id = "";
		Pattern metaIdP = Pattern.compile("_:[\\w\\d]+");

		String subject_id;
		if (subject.contains("/fast/"))
			subject_id = extractTail(subject);
		// some meta info is store in ids like this,
		else if (metaIdP.matcher(subject).matches())
			subject_id = subject;
		else {
			if (debug)
				System.err.println("Unhandled subject: " + subject);
			return;
		}

		id = subject_id;
		if (Util.nullOrEmpty(id))
			return;

		if (!topics.containsKey(id)) {
			FASTRecord topic = FASTRecord.getInstance(fastType);
			topic.id = id;
			topic.addValue(predicate, object);
			topics.put(id, topic);
		} else if (topics.containsKey(id)) {
			topics.get(id).addValue(predicate, object);
		}
	}

	// call it before closing the FASTTopical file, flushes all objects in
	// topics.
	void dumpTopics() {
		for (String id : topics.keySet()) {
			FASTRecord ft = topics.get(id);
			if (stats != null)
				stats.collectStats(ft);
			if (debug)
				System.err.println(FASTRecord.stringify(FASTRecord.getNames(ft)));

			if (ft instanceof FASTGeographic) {
				FASTGeographic fg = (FASTGeographic) ft;
				// fills latitude and longitude values
				fg.fillGaps(topics);
				if (fg.latitude != null && fg.longitude != null)
					stats.numLatLon++;
			}

			Document doc1 = ft.getIndexerDoc();
			// probably a subtype id like _:.* which shouldn't/needn't be
			// indexed.
			if (doc1 != null) {
				Set<Document> mapDocs = ft.getNameMap();
				if (mapDocs == null)
					mapDocs = new HashSet<Document>();
				doc1.add(new StringField(FASTRecord.SUB_TYPE, FASTRecord.FULL_RECORD, org.apache.lucene.document.Field.Store.YES));
				mapDocs.add(doc1);

				for (Document doc : mapDocs) {
					if (doc != null) {
						try {
							w.addDocument(doc);
						} catch (Exception e) {
							e.printStackTrace();
							System.err.println("Couldn't add document for FASTTopic with id: "
									+ id);
						}
					}
				}
			}
		}
	}

	public void printStats() {
		System.out.println(stats);
	}

	public void index() {
		try {
			LineNumberReader lnr = new LineNumberReader(new BufferedReader(
					new InputStreamReader(new FileInputStream(FASTDbFile))));
			Pattern triple = Pattern.compile("([^\\s]*)\\s+([^\\s]*)\\s+(.*) \\.");
			// sometimes lines in loc subject files starts like:
			// _:b173f2600000000de
			int lineNum = 0;
			while (true) {
				String line = lnr.readLine();
				if (line == null)
					break;

				lineNum++;
				if (lineNum % 100000 == 0)
					System.out.println("Line number: " + lineNum);

				Matcher m = triple.matcher(line);

				if (!m.find()) {
					System.err.println("What!? This is not an nt file! line#" + lineNum + ": " + line);
					continue;
				}

				int count = m.groupCount();
				if (count != 3) {
					System.err.println("What!? This is not an nt file! line#" + lineNum + ": " + line);
					continue;
				}
				String subject = m.group(1), predicate = m.group(2), object = m
						.group(3);

				appendOrupdate(subject, predicate, object);
			}
			lnr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		dumpTopics();
		try {
			w.close();
		} catch (IOException e) {
			System.err.println("Exception while closing index writer!");
			e.printStackTrace();
		}
	}

	public static void show_help() {
		System.err.println("Usage: program [config file]");
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			show_help();
			return;
		}

		String configFile = args[0];
		Gson gson = new Gson();
		BufferedReader br = null;
		try {
			File f = new File(configFile);
			FileReader fr = new FileReader(f);
			br = new BufferedReader(fr);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error while opening the settings file.");
			return;
		}

		FASTSettings settings = gson.fromJson(br, FASTSettings.class);
		if (settings.dbFiles == null || settings.types == null || settings.appendToExistingIndex == null || settings.dbFiles.size() != settings.types.size()) {
			System.err.println("The settings file is improper. It should be json representing FASTIndexer$Settings class.");
			return;
		}

		for (int i = 0; i < settings.dbFiles.size(); i++) {
			boolean append = true;
			if (i == 0)
				append = settings.appendToExistingIndex;
			String type = settings.types.get(i);
			System.err.println("DBfile: " + settings.dbFiles.get(i) + ", type: " + settings.types.get(i));

			if (!FASTRecord.isSupported(type)) {
				System.err.println("Misconfiguration in the settings file\nType: " + type + " is not supoorted.");
				return;
			}
			FASTIndexer indexer = new FASTIndexer(append, settings.dbFiles.get(i), type);
			indexer.debug = false;
			indexer.index();
			indexer.printStats();
		}
	}
}