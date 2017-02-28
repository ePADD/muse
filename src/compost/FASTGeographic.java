package edu.stanford.muse.ie;

import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Util;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class FASTGeographic extends FASTRecord {
	Set<String>				relatedIds;
	String					latitude, longitude, geoId;
	static String			RELATED			= "related", LATITUDE = "latitude", LONGITUDE = "longitude", GEOID = "geo";
	static String[]			relations		= new String[] { "<http://www.w3.org/2004/02/skos/core#prefLabel>",// 0
			"<http://www.w3.org/2004/02/skos/core#altLabel>",// 1
			"<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",// 2
			"<http://schema.org/sameAs>",// 3
			"<http://www.w3.org/2004/02/skos/core#relatedMatch>",// 4
			"<http://www.w3.org/2004/02/skos/core#related>",// 5
			"<http://www.w3.org/2000/01/rdf-schema#label>",// 6
			"<http://schema.org/name>",// 7
			"<http://schema.org/latitude>",// 8
			"<http://schema.org/longitude>",// 9
			"<http://schema.org/geo>"// 10
											};
	// the type that is written to the index.
	static final String[]	SUB_VALID_TYPES	= new String[] { "<http://schema.org/GeoCoordinates>" };
	static String			VALID_TYPE		= "<http://schema.org/Place>";

	static {
		// remove stuff inside brackets, this is generally the country name/
		// some supplementary info like: Mount Morris (N.Y.).
		// TODO: does it pull too much noise?
		namePatternsToMatchAndRemove.add(Pattern.compile("\\(.*?\\)"));
	}

	public FASTGeographic() {
		names = new HashSet<String>();
		sources = new HashSet<String>();
		relatedIds = new HashSet<String>();
	}

	public FASTGeographic(Document d) {
		names = unstringify(d.get(NAME));
		type = d.get(TYPE);
		id = d.get(ID);
		sources = unstringify(d.get(SOURCE));
		latitude = d.get(LATITUDE);
		longitude = d.get(LONGITUDE);
		relatedIds = unstringify(d.get(RELATED));
	}

	// trims and removes quotes
	String clean(String name) {
		if (Util.nullOrEmpty(name))
			return name;
		String s = name.trim();
		s = s.replaceAll("\"", "");
		return s;
	}

	public void fillGaps(Map<String, FASTRecord> types) {
		if (types.containsKey(this.geoId)) {
			FASTRecord ft = types.get(this.geoId);
			if (ft instanceof FASTGeographic) {
				FASTGeographic fg = (FASTGeographic) ft;
				this.latitude = fg.latitude;
				this.longitude = fg.longitude;
			} else {
				System.err.println("What? geoId: " + geoId + ", object is not geographic.");
			}
		}
		// No hook found to fill the gap.
		else {
			System.err.println("geoId: " + geoId + " for: " + id + " not found.");
		}
	}

	@Override
	public void addValue(String rln, String value) {
		boolean added = false;
		for (int i = 0; i < relations.length; i++) {
			if (relations[i].equals(rln)) {
				if (i == 0 || i == 6 || i == 7 || i == 1)
					names.add(extractName(value));
				else if (i == 2) {
					type = value;
					if (!type.equals(VALID_TYPE) && !type.equals(SUB_VALID_TYPES[0]))
						System.err.println("Miscongiguration in Settings file...\nFound: " + type + " and is being interpreted as Corporate Type");
				}
				else if (i == 3 || i == 4) {
					setSource(value);
					sources.add(value);
				}
				else if (i == 5)
					relatedIds.add(value);
				else if (i == 8)
					latitude = clean(value);
				else if (i == 9)
					longitude = clean(value);
				else if (i == 10)
					geoId = value;
				added = true;
			}
		}
		if (!added)
			;// System.err.println("Not adding the relation: Id: " + id + ", " +
				// rln + ", value: " + value);
	}

	@Override
	public Document getIndexerDoc() {
		if (id.contains("_:"))
			return null;
		Document doc = new Document();
		Field idField = null, namesF = null, altNamesF = null, sourcesF = null, typeF = null, relatedF = null, latF = null, longF = null, cnamesF = null;
		if (id != null) {
			idField = new StringField("id", id, Field.Store.YES);
			doc.add(idField);
		}
		else
			;// ;//System.err.println(stringify(names) + " doesn't have id");

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
		else
			;// System.err.println("Names for " + id + " is empty.");

		if (sources.size() > 0) {
			sourcesF = new StringField(SOURCE, stringify(sources), Field.Store.YES);
			doc.add(sourcesF);
		}
		else
			;// System.err.println("Sources for " + id + " is empty.");
		if (type != null) {
			typeF = new StringField(TYPE, type, Field.Store.YES);
			doc.add(typeF);
		}
		else
			;// System.err.println("type for " + id + " is null.");
		if (relatedIds.size() > 0) {
			relatedF = new StringField(RELATED, stringify(relatedIds), Field.Store.YES);
			doc.add(relatedF);
		}
		else
			;// System.err.println("Related Names for " + id + " is empty.");
		if (latitude != null) {
			latF = new StringField(LATITUDE, latitude, Field.Store.YES);
			doc.add(latF);
		}
		if (longitude != null) {
			longF = new StringField(LONGITUDE, longitude, Field.Store.YES);
			doc.add(longF);
		}

		return doc;
	}
}
