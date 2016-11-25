package edu.stanford.muse.ie;

import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class FASTPerson extends FASTRecord implements java.io.Serializable {
	public static final long	serialVersionUID				= 1L;
	// Note: fast_id can be null too.
	//TODO why ids stored in different vars? and not using implementation in FASTType.
	public String				FAST_id, dbpedia, viaf, locSubject, locName;
	Set<String>					names							= new HashSet<String>();

	static String				SOURCE							= "source";
	// dont want <http://schema.org/name> as it is repetition of prefLabel.
	static String[]				relations						= new String[] { "<http://www.w3.org/2004/02/skos/core#prefLabel>", "<http://www.w3.org/2004/02/skos/core#altLabel>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://schema.org/sameAs>", "<http://www.w3.org/2004/02/skos/core#relatedMatch>", "<http://www.w3.org/2004/02/skos/core#related>", "<http://www.w3.org/2000/01/rdf-schema#label>", "<http://schema.org/name>" };
	static String				VALID_TYPE						= "<http://schema.org/Person>";
	static List<Pattern>		namePatternsToMatchAndRemove	= new ArrayList<Pattern>();
	static {
		// remove everything after , <space>d./b./ca./fl. <numbers>
		// Langford, James, d. 1777
		// Hoffman, U. J. (Urias John), b. 1855
		// Parkman, Gideon, ca. 1714-1789
		// "Joannes, de Alta Silva, fl. 1184-1212" .
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+(d|b|fl|ca)\\.\\s+\\d+.*"));
		// Larkin, Mr. (George), b. ca. 1642
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+(b|d)\\.\\s+ca\\.\\s+\\d+.*"));
		// Parkman, Gideon, approximately 1714-1789
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+approximately.*"));
		// "Creeley, Robert, 1926-2005"
		// Kittel, F. (Ferdinand), 1832-1903
		// "Gwin, Thomas, 1656?-1720" .
		// Kleeberg, Franciszek, 1888-
		// Bernardo, de Claraval, Saint, 1090 or 91-1153
		// Scott, Michael, 1906?-
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+\\d+\\?*(-| or ).*"));

		// Calderini, Giovanni, -1365
		namePatternsToMatchAndRemove.add(Pattern.compile(",\\s+-\\d+.*"));

		// Sammuramat, Queen, consort of Shamshi-Adad V, King of Assyria, 9th
		// cent. B.C.
		// Jean de Hauteseille, 12th cent (may not have a period after cent)
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+\\d+(st|nd|rd|th|) cent.*"));

		// Xu, Zhongjie, jin shi 1508
		namePatternsToMatchAndRemove.add(Pattern.compile("\\d.*"));
	}

	static String extractTail(String s) {
		String s1 = s;
		try {
			s1 = s1.replaceAll("^<|>$", "");
			s1 = s1.substring(s1.lastIndexOf("/") + 1, s1.length());
			// s.length()-1 to  exclude the trailing ">" character
		} catch (Exception e) {
			System.err.println("unable to extract tail from string " + s);
			return "dummy";
		}
		return s1;
	}

	void interpretSources(Set<String> sources) {
		for (String src : sources) {
			String object = src;
			if (object.contains("http://viaf.org/viaf/")) {
				// <http://id.worldcat.org/fast/1773461>
				// <http://schema.org#sameAs>
				// <http://viaf.org/viaf/34568446> .
				viaf = extractTail(object);
				VIAFSource = true;
			} else if (object
					.contains("http://id.loc.gov/authorities/subjects")) {
				// <http://id.worldcat.org/fast/367046>
				// <http://schema.org#sameAs>
				// <http://id.loc.gov/authorities/subjects/sh95008980> .
				locSubject = extractTail(object);
				this.LOCSource = true;
			} else if (object
					.contains("http://id.loc.gov/authorities/names")) {
				// <http://id.worldcat.org/fast/51951>
				// <http://schema.org#sameAs>
				// <http://id.loc.gov/authorities/names/n80033928> .
				locName = extractTail(object);
				this.LOCSource = true;
			} else if (object.contains("http://dbpedia.org/resource")) {
				this.dbpedia = extractTail(object);
				this.wikiSource = true;
			} else {
				System.err.println("Unknown source: " + object + " while initialising from document");
			}
		}
	}

	public FASTPerson() {
	}

	public FASTPerson(Document d) {
		names = unstringify(d.get(NAME));
		type = d.get(TYPE);
		FAST_id = d.get(ID);
		id = d.get(ID);
		interpretSources(unstringify(d.get(SOURCE)));
	}

	public String extractName(String s) {
		if (s.contains("\\u"))
			s = FASTIndexer.convertUTF16(s);
		// expect s to be quoted
		if (!s.startsWith("\"") || !s.endsWith("\"")) {
			System.err.println("Sorry, bad name: " + s);
			return "DUMMY";
		}

		String s1 = s;
		s1 = s1.replaceAll("\"", ""); // remove quotes
		s1 = s1.trim();

		for (Pattern p : namePatternsToMatchAndRemove)
			s1 = p.matcher(s1).replaceAll("");

		for (char c : s1.toCharArray())
			if (Character.isDigit(c)) {
				System.err.println("Warning: numeric digit in " + s1);
				break;
			}
		return s1;
	}

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		for (String n : names)
			sb.append(n + "|");
		sb.append(" fast=" + FAST_id + " dbpedia=" + dbpedia + " viaf=" + viaf + " locName=" + locName + " locSubject=" + locSubject);
		return sb.toString();
	}

	@Override
	public Pair<String, String> getAllSources() {
		String sourceNames = "", sourceIds = "";
        String sep = Authority.sep;
		sources = new HashSet<String>();
		if (FAST_id != null)
			sources.add("http://id.worldcat.org/fast/" + FAST_id);
		if (dbpedia != null)
			sources.add("http://dbpedia.org/resource/" + dbpedia);
		if (viaf != null)
			sources.add("http://viaf.org/viaf/" + viaf);
		if (locName != null)
			sources.add("http://id.loc.gov/authorities/names/" + locName);
		if (locSubject != null)
			sources.add("http://id.loc.gov/authorities/subjects/" + locSubject);

		for (String src : sources) {
			if (src == null)
				continue;
			src = src.replaceAll("(<|>)+", "");
			if (src.contains("dbpedia") || src.contains("wiki")) {
				String dbpedia = src.replace("http://dbpedia.org/resource/", "");
				dbpedia = dbpedia.replace("http://en.wikipedia.org/wiki", "");
				sourceNames += Authority.types[Authority.DBPEDIA] + sep;
				sourceIds += dbpedia + sep;
			}
			else if (src.contains("viaf")) {
				String viaf = src.replaceAll("http://viaf.org/viaf/", "");
				sourceNames += Authority.types[Authority.VIAF] + sep;
				sourceIds += viaf + sep;
			}
			else if (src.contains("http://id.loc.gov/authorities/subjects/")) {
				String locSubject = src.replaceAll("http://id.loc.gov/authorities/subjects/", "");
				sourceNames += Authority.types[Authority.LOC_SUBJECT] + sep;
				sourceIds += locSubject + sep;
			}
			else if (src.contains("http://id.loc.gov/authorities/names/")) {
				String locName = src.replaceAll("http://id.loc.gov/authorities/names/", "");
				sourceNames += Authority.types[Authority.LOC_NAME] + sep;
				sourceIds += locName + sep;
			}
			else if (src.contains("http://stopWords.geonames.org/")) {
				String geoId = src.replaceAll("http://sws.geonames.org/", "").replaceAll("(<|>|\\\\)", "");
				sourceNames += Authority.types[Authority.GEO_NAMES] + sep;
				sourceIds += geoId + sep;
			}
		}

		if (id != null) {
			sourceNames += Authority.types[Authority.FAST];
			sourceIds += id;
		} else {
			//remove commas in the end.
			sourceNames = sourceNames.substring(0, sourceNames.length() - 1);
			sourceIds = sourceIds.substring(0, sourceIds.length() - 1);
		}
		return new Pair<String, String>(sourceNames, sourceIds);
	}

	@Override
	public String toHTMLString()
	{
		StringBuilder sb = new StringBuilder();
		int i1 = 0;
		for (String n : names) {
			if (i1 == 0)
				sb.append(n);
			else if (i1 == 1)
				sb.append(" a.k.a. " + n);
			else
				sb.append("; " + n);
			i1++;
		}

		sb.append("<br/><div class=\"ids\">");

		String fast_link = "http://id.worldcat.org/fast/" + FAST_id;
		String dbpedia_link = "http://dbpedia.org/resource/" + dbpedia;
		String viaf_link = "http://viaf.org/viaf/" + viaf;
		String locNameLink = "http://id.loc.gov/authorities/names/" + locName;
		String locSubjectLink = "http://id.loc.gov/authorities/subjects/" + locSubject;

		// assemble HTML for all external links into this
		List<String> links = new ArrayList<String>();

		if (FAST_id != null)
			links.add("<a target=\"_blank\" href=\"" + fast_link + "\">FAST:" + FAST_id + "</a> ");
		if (dbpedia != null) {
			String id = dbpedia;
			String wikiResolve = "<script>resolve(\"" + dbpedia + "\",\"" + ("page_" + id) + "\");</script><div id=\"page_" + id + "\"></div>";
			links.add(" <a target=\"_blank\" title='" + wikiResolve + "' href=\"" + dbpedia_link + "\">DBpedia:" + dbpedia + "</a>");
		}
		if (viaf != null)
			links.add(" <a target=\"_blank\" href=\"" + viaf_link + "\">VIAF: " + viaf + "</a>");
		if (locSubject != null)
			links.add(" <a target=\"_blank\" href=\"" + locSubjectLink + "\">LoC-subject " + locSubject + "</a>");
		if (locName != null)
			links.add(" <a target=\"_blank\" href=\"" + locNameLink + "\">LCNAF: " + locName + "</a>");

		for (int i = 0; i < links.size(); i++)
		{
			sb.append(links.get(i));
//			if (i < links.size() - 1)
//				sb.append(" &bull; ");
		}
		sb.append("</div>");
		return sb.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof FASTPerson) {
			FASTPerson fp = (FASTPerson) o;
			return fp.FAST_id.equals(this.FAST_id);
		}
		else
			return false;
	}

	@Override
	public int hashCode() {
		if (FAST_id != null)
			return FAST_id.hashCode();
		return 0; // dummy
	}

	@Override
	public void addValue(String relation, String value) {
		String object = value;

		// http://id.worldcat.org/fast/148474> <http://schema.org/name>
		// "Stampford, Lord (Henry Grey), 1599?-1673" .
		if (relation.equalsIgnoreCase("<http://schema.org/name>")) {
			String name = extractName(object);
			names.add(name);
		}

		// <http://id.worldcat.org/fast/212611> <http://schema.org/sameAs>
		// <http://id.loc.gov/authorities/subjects/sh85083889> .
		if (relation.equalsIgnoreCase("<http://schema.org/sameAs>")) {
			object = object.replaceAll("(^<|>$)+", "");
			if (object.startsWith("http://viaf.org/viaf/")) {
				// <http://id.worldcat.org/fast/1773461>
				// <http://schema.org#sameAs>
				// <http://viaf.org/viaf/34568446> .
				viaf = object;
				VIAFSource = true;
			} else if (object
					.startsWith("http://id.loc.gov/authorities/subjects")) {
				// <http://id.worldcat.org/fast/367046>
				// <http://schema.org#sameAs>
				// <http://id.loc.gov/authorities/subjects/sh95008980> .
				locSubject = object;
				this.LOCSource = true;
			} else if (object
					.startsWith("http://id.loc.gov/authorities/names")) {
				// <http://id.worldcat.org/fast/51951>
				// <http://schema.org#sameAs>
				// <http://id.loc.gov/authorities/names/n80033928> .
				locName = object;
				this.LOCSource = true;
			} else
				System.err
						.println("Warning: sameAs has unexpected object: "
								+ object);
		}

		// <http://id.worldcat.org/fast/1773461>
		// <http://xmlns.com/foaf/0.1/focus>
		// <http://dbpedia.org/resource/Ferdinand_Kittel> .
		if (relation.equalsIgnoreCase("<http://xmlns.com/foaf/0.1/focus>")
				&& object.startsWith("<http://dbpedia.org/resource")) {
			dbpedia = object;
			this.wikiSource = true;
		}

		if (relation.equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"))
			type = value;
	}

	@Override
	public Document getIndexerDoc() {
		Document doc = new Document();
		Field idField = null, namesF = null, sourcesF = null, cnamesF = null;
		if (id != null) {
			idField = new StringField("id", id, Field.Store.YES);
			doc.add(idField);
		}
		Set<String> cnames = new HashSet<String>();
		for (String n : names)
			cnames.add(EmailUtils.normalizePersonNameForLookup(n));
		if (cnames.size() > 0) {
			cnamesF = new TextField(CNAME, stringify(cnames), Field.Store.YES);
			doc.add(cnamesF);
		}

		if (names.size() > 0) {
			namesF = new StringField(NAME, stringify(names), Field.Store.YES);
			doc.add(namesF);
			// System.err.println("Adding: " + stringify(names));
		}

		Set<String> sources = new HashSet<String>();
		if (dbpedia != null)
			sources.add(dbpedia);
		if (locName != null)
			sources.add(locName);
		if (locSubject != null)
			sources.add(locSubject);
		if (viaf != null)
			sources.add(viaf);

		if (sources.size() > 0) {
			sourcesF = new StringField(SOURCE, stringify(sources), Field.Store.YES);
			doc.add(sourcesF);
		}
		if (type != null) {
			StringField typeF = new StringField(TYPE, type, Field.Store.YES);
			doc.add(typeF);
		}

		return doc;
	}
}