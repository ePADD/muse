package edu.stanford.muse.ie;

import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An abstract representation for various FAST types such as Personal,
 * Geogeraphic etc. Contrary to what the class name says, the record represented by this class
 * may not be FAST record always.
 * 
 * @author: Vihari
 **/

abstract public class FASTRecord {
	protected static Log log			= LogFactory.getLog(FASTRecord.class);

	// there are also small proportion of type:
	// <http://sws.geonames.org/5387597/>
	//TODO: are these booleans usable anymore?
	public Boolean wikiSource = false, VIAFSource = false, LOCSource = false, GEOSource = false, unknownSource = false;

	// col. names in the fast index
	// fast id. sub type is to differentiate between those records that are just
	// maps and those that are full records.
	//though the field CNAME is named so, the names are actually normalised with EmailUtils.normalizePersonaNameForLookup rather than canonicalized,
	//TODO: change the CNAME field to NName ie. normalized name.
	public static final String ID = "id", TYPE = "type", NAME = "name", CNAME = "cname", SOURCE = "source", SUB_TYPE = "subType";

	/**
	 * lookup and full_record are two fields added so as to make searching the
	 * index faster. lookup ids for lookup into the full records. This increases
	 * the index size, but not sure how it effects the performance of search.
	 * TODO remove lookup and full record types if not required, they are also
	 * used in FASTIndexer and FASTSearcher
	 */
	public static final String		LOOKUP							= "lookup", FULL_RECORD = "fullRecord";

	// this is the main part of the fast record, which contains IDs in various databases, e.g.
	// "fast" -> xxx, dbpedia -> "yyy", etc.
	protected Set<String>			names, sources;

	// note: id can be null too in which case this is not even a FASTRecord. A change in class name is required?
	protected String				id, type;

	/** Nt style of adding value */
	public static final List<Pattern> namePatternsToMatchAndRemove	= new ArrayList<>();

	public abstract void addValue(String relation, String value);

	public abstract org.apache.lucene.document.Document getIndexerDoc();

	public enum FASTDB {
		ALL("all"), TOPICS("topics"), CORPORATE("corporate"), GEOGRAPHIC("geographic"), PERSON("person");
		public final String	txt;

		FASTDB(String str) {
			this.txt = str;
		}

		@Override
		public String toString() {
			return txt;
		}
	}

	private static Pattern jP = Pattern.compile("^ ::: | ::: $");
	private static Pattern leadingAndTrailingQuotePattern = Pattern.compile("^\"|\"$");

	private static String cleanName (String s) {
		String s1 = s.trim();
		s1 = leadingAndTrailingQuotePattern.matcher(s1).replaceAll("");

		// Some strange corporate names contains such string patterns like \"some name\"
		s1 = s1.replaceAll("\\\\\"", ""); // remove backslash-quote
		s1 = s1.replaceAll("\"", ""); // remove quotes
		s1 = s1.trim();
		return s1;
	}

	public String extractName(String s) {
		if (s.contains("\\u"))
			s = FASTIndexer.convertUTF16(s);

		s = cleanName (s);
		for (Pattern p : namePatternsToMatchAndRemove)
			s = p.matcher(s).replaceAll("");

		for (char c : s.toCharArray())
			if (Character.isDigit(c)) {
				// System.err.println("Warning: numeric digit in " + s1);
				break;
			}
		return s;
	}

	public void setSource (String value) {
		if (value.contains("viaf"))
			VIAFSource = true;
		else if (value.contains("id.loc.gov"))
			LOCSource = true;
		else if (value.contains ("dbpedia") || value.contains ("wiki"))
			wikiSource = true;
		// of type: <http://sws.geonames.org/4232911/>
		else if (value.contains("geonames.org"))
			GEOSource = true;
		else {
			unknownSource = true;
			log.warn("FAST record received Unknown source: " + value);
		}
	}

	public static String[]	supportedFASTTypes	= new String[] { "Corporate", "Topical", "Geographic", "Person" };

	public static boolean isSupported(String type) {
		boolean supported = false;
		for (String st : supportedFASTTypes)
			if (st.equalsIgnoreCase(type))
				supported = true;
		return supported;
	}

	public static String stringify(Set<String> set) {
		if (set == null)
			return null;
		String temp = "", sep = " ::: ";
		for (String str : set) {
			if (Util.nullOrNoContent(str)) {
				continue;
			}
			temp += str + sep;
		}

		return sep + temp;
	}

	public static FASTRecord getInstance(String type) {
		FASTRecord fastType = null;
		if (type.equals(supportedFASTTypes[0]) || type.equals(FASTCorporate.VALID_TYPE))
			fastType = new FASTCorporate();
		else if (type.equals(supportedFASTTypes[1]) || type.equals(FASTTopic.VALID_TYPE))
			fastType = new FASTTopic();
		else if (type.equals(supportedFASTTypes[2]) || type.equals(FASTGeographic.VALID_TYPE))
			fastType = new FASTGeographic();
		else if (type.equals(supportedFASTTypes[3]) || type.equals(FASTPerson.VALID_TYPE))
			fastType = new FASTPerson();
		else {
			System.err.println("Type: " + type + " is not a supported type.");
			return null;
		}
		return fastType;
	}

	public static FASTRecord getInstance(FASTDB type) {
		FASTRecord fastType = null;
		if (type == FASTDB.CORPORATE)
			fastType = new FASTCorporate();
		else if (type == FASTDB.TOPICS)
			fastType = new FASTTopic();
		else if (type == FASTDB.GEOGRAPHIC)
			fastType = new FASTGeographic();
		else if (type == FASTDB.PERSON)
			fastType = new FASTPerson();
		else {
			System.err.println("Type: " + type + " is not a supported type.");
			return null;
		}
		return fastType;
	}

	public static Set<String> getNames(FASTRecord ft) {
		if (ft instanceof FASTTopic)
			return ((FASTTopic) ft).names;
		else if (ft instanceof FASTCorporate)
			return ((FASTCorporate) ft).names;
		else if (ft instanceof FASTGeographic)
			return ((FASTGeographic) ft).names;
		else if (ft instanceof FASTPerson)
			return ((FASTPerson) ft).names;
		return null;
	}

	/** Use this interface instead of directly using names field */
	public Set<String> getNames() {
		FASTRecord ft = this;
		if (ft instanceof FASTTopic)
			return ((FASTTopic) ft).names;
		else if (ft instanceof FASTCorporate)
			return ((FASTCorporate) ft).names;
		else if (ft instanceof FASTGeographic)
			return ((FASTGeographic) ft).names;
		else if (ft instanceof FASTPerson)
			return ((FASTPerson) ft).names;
		return null;
	}

	/**
	 * Special doc just for cname to fast_id resolution.
	 */
	public Set<Document> getNameMap() {
		Set<String> names = getNames();
		if (Util.nullOrEmpty(names))
			return null;
		Set<Document> docs = new HashSet<Document>();
		for (String name : names) {
			String cname = EmailUtils.normalizePersonNameForLookup(name);
			if (Util.nullOrEmpty(cname))
				continue;
			Document doc = new Document();
			Field idField = null, cnamesF = null, typeF = null;
			if (id != null) {
				idField = new StringField("id", id, Field.Store.YES);
				doc.add(idField);
			}
			cnamesF = new TextField(CNAME, cname, Field.Store.YES);
			doc.add(cnamesF);
			if (type != null) {
				typeF = new StringField(TYPE, type, Field.Store.YES);
				doc.add(typeF);
			}
			doc.add(new StringField(SUB_TYPE, LOOKUP, Field.Store.YES));
			docs.add(doc);
		}
		return docs;
	}

	public static String getValidType(FASTDB type) {
		if (type == FASTDB.CORPORATE)
			return FASTCorporate.VALID_TYPE;
		else if (type == FASTDB.GEOGRAPHIC)
			return FASTGeographic.VALID_TYPE;
		else if (type == FASTDB.TOPICS)
			return FASTTopic.VALID_TYPE;
		else if (type == FASTDB.PERSON)
			return FASTPerson.VALID_TYPE;
		else
			return null;
	}

	public static Set<String> unstringify(String longname) {
		if (Util.nullOrEmpty(longname))
			return new HashSet<String>();
		String cl = jP.matcher(longname).replaceAll("");
		String[] namesAll = cl.split(" ::: ");
		Set<String> names = new HashSet<String>();
		for (String n : namesAll)
			names.add(n);
		return names;
	}

	public static FASTRecord instantiateWith(Document d) {
		String type = d.get(TYPE);
		if (type.equals(FASTCorporate.VALID_TYPE))
			return new FASTCorporate(d);
		else if (type.equals(FASTGeographic.VALID_TYPE))
			return new FASTGeographic(d);
		else if (type.equals(FASTPerson.VALID_TYPE))
			return new FASTPerson(d);
		else if (type.equals(FASTTopic.VALID_TYPE))
			return new FASTTopic(d);
		else {
			System.err.println("Type: " + d.get(TYPE) + " is not recognised");
			return null;
		}
	}

	public static String toString(Document d) {
		return "CNAMES: " + d.get(CNAME) + "\t Names: " + d.get(NAME) + "\t" + "Id: " + d.get("id") + "\t Type: " + d.get(TYPE);
	}

	/**
	 * This method is properly overridden for only FASTPerson and may not work
	 * as desired for other subclasses.
	 * returns descriptive data of sources.
	 * 
	 * @return <[Source names seperated with comma],[corresponding ids separated
	 *         with comma]>
	 */
	public Pair<String, String> getAllSources() {
		String sourceNames = "", sourceIds = "";
		if (sources == null) {
			System.err.println("sources is not properly initiated for: " + this.getNames() + ", returning null in getAllSources.");
			return null;
		}

        String sep = ":::";
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

		// if is a Geographic type then also toString latitude and longitude.
		if (this instanceof FASTGeographic) {
			FASTGeographic fg = (FASTGeographic) this;
			if (fg.latitude != null && fg.longitude != null) {
				String geoResolve = "<script>geoResolve(" + fg.latitude + "," + fg.longitude + ",\"" + "geoPage_" + id + "\");</script><div id=\"geoPage_" + id + "\"></div>";
				sb.append("<a href='#' title='" + geoResolve + "'> Location: " + fg.latitude + ", " + fg.longitude + "</a>");
			}
		}

		String fast_link = "http://id.worldcat.org/fast/" + id;

		// assemble HTML for all external links into this
		List<String> links = new ArrayList<String>();

		if (id != null)
			links.add("<a target=\"_blank\" href=\"" + fast_link + "\">FAST:" + id + "</a> ");
		for (String src : sources) {
			if (src == null)
				continue;
			src = src.replaceAll("(<|>)+", "");
			if (src.contains("dbpedia") || src.contains("wiki")) {
				String dbpedia = src.replace("http://dbpedia.org/resource/", "");
				String wikiResolve = "<script>resolve(\"" + dbpedia + "\");</script><div id=\"page_" + id + "\"></div>";
				links.add(" <a target=\"_blank\" title='" + wikiResolve + "' href=\"" + src + "\">DBpedia:" + dbpedia + "</a>");
			}
			else if (src.contains("viaf")) {
				String viaf = src.replaceAll("http://viaf.org/viaf/", "");
				links.add(" <a target=\"_blank\" href=\"" + src + "\">VIAF: " + viaf + "</a>");
			}
			else if (src.contains("http://id.loc.gov/authorities/subjects/")) {
				String locSubject = src.replaceAll("http://id.loc.gov/authorities/subjects/", "");
				links.add(" <a target=\"_blank\" href=\"" + src + "\">LoC-subject " + locSubject + "</a>");
			}
			else if (src.contains("http://id.loc.gov/authorities/names/")) {
				String locName = src.replaceAll("http://id.loc.gov/authorities/names/", "");
				links.add(" <a target=\"_blank\" href=\"" + src + "\">LCNAF: " + locName + "</a>");
			}
			else if (src.contains("http://stopWords.geonames.org/")) {
				String geoId = src.replaceAll("http://sws.geonames.org/", "").replaceAll("(<|>|\\\\)", "");
				links.add("<a target=\"_blank\" href=\"" + src + "\">GeoName:" + geoId + "</a>");
			}
		}
		for (int i = 0; i < links.size(); i++)
		{
			sb.append(links.get(i));
//			if (i < links.size() - 1)
//				sb.append(" &bull; ");
		}
		sb.append("</div>");

		return sb.toString();
	}
}