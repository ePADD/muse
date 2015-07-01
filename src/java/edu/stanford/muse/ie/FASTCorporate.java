package edu.stanford.muse.ie;

import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

import edu.stanford.muse.util.EmailUtils;

public class FASTCorporate extends FASTRecord {
	Set<String>		relatedIds;
	// altNames i.e. VARIANT and NAME should be merged
	static String	VARIANT		= "altLabel", RELATED = "related";
	// dont want <http://schema.org/name> as it is repetition of prefLabel.
	static String[]	relations	= new String[] { "<http://www.w3.org/2004/02/skos/core#prefLabel>", "<http://www.w3.org/2004/02/skos/core#altLabel>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://schema.org/sameAs>", "<http://www.w3.org/2004/02/skos/core#relatedMatch>", "<http://www.w3.org/2004/02/skos/core#related>", "<http://www.w3.org/2000/01/rdf-schema#label>", "<http://schema.org/name>" };
	static String	VALID_TYPE	= "<http://schema.org/Organization>";

	public FASTCorporate() {
		names = new HashSet<String>();
		sources = new HashSet<String>();
		relatedIds = new HashSet<String>();
	}

	public FASTCorporate(Document d) {
		names = unstringify(d.get(NAME));
		type = d.get(TYPE);
		id = d.get(ID);
		names = unstringify(d.get(NAME));
		sources = unstringify(d.get(SOURCE));
		System.err.println("Received sources: " + sources);
		relatedIds = unstringify(d.get(RELATED));
	}

	static {
		// remove stuff in brackets and quotes like:
		// Wismar (Germany). Stadtgeshichtliches Museum Wismar \"Schabbellhaus\"
		// In reality things in brackets are very useful for disambiguation and
		// better not removed. No idea why they put quotations in first place.
		// removing brackets pulls in lot of generic terms like Law from Law
		// (Steamer) so better not touch it either
		// namePatternsToMatchAndRemove.add(Pattern.compile("\\(.*?\\)"));
		// namePatternsToMatchAndRemove.add(Pattern.compile("\\\".*?\""));
		// corporate names contains names like:
		// University of Findlay. Mazza Museum names seperated by '.' not sure
		// what to do with them. I chose not to touch them.
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
					if (!type.equals(VALID_TYPE))
						System.err.println("Misconfiguration in Settings file...\nFound: " + type + " and is being interpreted as Corporate Type");
				}
				else if (i == 3 || i == 4) {
					interpretSource(value);
					sources.add(value);
				}
				else if (i == 5)
					relatedIds.add(value);
				added = true;
			}
		}
		if (!added)
			;// System.err.println("Not adding the relation: Id: " + id + ", " +
				// rln + ", value: " + value);
	}

	@Override
	public Document getIndexerDoc() {
		Document doc = new Document();
		Field idField = null, namesF = null, sourcesF = null, typeF = null, relatedF = null, cnamesF;
		if (id != null) {
			idField = new StringField("id", id, Field.Store.YES);
			doc.add(idField);
		}
		else
			;// ;//System.err.println(stringify(names) + " doesn't have id");

		Set<String> cnames = new HashSet<String>();
		for (String n : names) {
			cnames.add(EmailUtils.normalizePersonNameForLookup(n));
		}
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

		return doc;
	}
}
