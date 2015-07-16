package edu.stanford.muse.ie;

import edu.stanford.muse.ie.FASTRecord.FASTDB;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author viharipiratla
 *         A helper class to search FASTRecords index.
 */
public class FASTSearcher {
	private static Log		log			= LogFactory.getLog(FASTSearcher.class);
	public static String			indexDir	= FASTIndexer.indexPath;
	// ModeConfig.SETTINGS_DIR + File.separator + "index";
	static IndexReader		reader		= null;
	static IndexSearcher	searcher	= null;
	static Analyzer			analyzer	= new StandardAnalyzer(Version.LUCENE_47);
	static {
		try {
			reader = DirectoryReader.open(FSDirectory.open(new File(indexDir)));
			searcher = new IndexSearcher(reader);
		} catch (IOException e) {
			//no point in printing stack
            log.info("Serious trouble: Exception reading FAST index from " + indexDir + " ... Authority resolution may not work!", e);
		}
	}

	private static Set<FASTRecord> getRecordsWith(Query q, int num) throws Exception {
		TopDocs docs = searcher.search(q, null, num);

		// records contains fast id of matching fast records.
		Set<String> records = new HashSet<String>();
		Set<FASTRecord> items = new HashSet<FASTRecord>();
		for (ScoreDoc sd : docs.scoreDocs) {
			Document d = searcher.doc(sd.doc);
			records.add(d.get(FASTRecord.ID));
			FASTRecord ft = FASTRecord.instantiateWith(d);
			// System.out.println(FASTType.toString(d));
			if (ft != null) {
				//String cnames = d.get(FASTType.CNAME);
				// log.info("CNAMES:" + cnames);
				items.add(ft);
			}
		}
		return items;
	}

	private static Set<FASTRecord> getRecordsWith(Query q) throws Exception {
		return getRecordsWith(q, 1);
	}

	/**
	 * If not sure of the FASTDB type, use FASTDB.ALL type
	 * returns null
	 */
	public static Set<FASTRecord> lookupId(String id, FASTDB type) {
		try {
			if (type != FASTDB.ALL) {
				BooleanQuery bq = new BooleanQuery();
				bq.add(new TermQuery(new Term(FASTRecord.SUB_TYPE, FASTRecord.FULL_RECORD)), BooleanClause.Occur.MUST);
				bq.add(new TermQuery(new Term(FASTRecord.TYPE, FASTRecord.getValidType(type))), BooleanClause.Occur.MUST);
				bq.add(new TermQuery(new Term(FASTRecord.ID, id)), BooleanClause.Occur.MUST);
				return getRecordsWith(bq);
			} else {
				Set<FASTRecord> allTypes = new HashSet<FASTRecord>();
				allTypes.addAll(lookupId(id, FASTDB.CORPORATE));
				allTypes.addAll(lookupId(id, FASTDB.GEOGRAPHIC));
				allTypes.addAll(lookupId(id, FASTDB.TOPICS));
				allTypes.addAll(lookupId(id, FASTDB.PERSON));
				return allTypes;
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.warn("Exception while looking up id: " + id + ". Returning null.");
			return null;
		}
	}

	/**
	 * Expected input: full db link (like
	 * http://id.loc.gov/authorities/names/no95049848) and properly escaped for
	 * regular expressions
	 */
	private static Set<FASTRecord> getRecordsByDBLink(String dbId, FASTDB type) {
		try {
			if (type != FASTDB.ALL) {
				BooleanQuery bq = new BooleanQuery();
				bq.add(new TermQuery(new Term(FASTRecord.SUB_TYPE, FASTRecord.FULL_RECORD)), BooleanClause.Occur.MUST);
				bq.add(new TermQuery(new Term(FASTRecord.TYPE, FASTRecord.getValidType(type))), BooleanClause.Occur.MUST);
				//bq.add(new TermQuery(new Term(FASTRecord.ID, id)), BooleanClause.Occur.MUST);
				bq.add(new RegexpQuery(new Term(FASTRecord.SOURCE, ".* ::: .*" + dbId + ".* ::: .*")), BooleanClause.Occur.MUST);
				return getRecordsWith(bq);
			} else {
				Set<FASTRecord> allTypes = new HashSet<FASTRecord>();
				allTypes.addAll(getRecordsByDBLink(dbId, FASTDB.CORPORATE));
				allTypes.addAll(getRecordsByDBLink(dbId, FASTDB.GEOGRAPHIC));
				allTypes.addAll(getRecordsByDBLink(dbId, FASTDB.TOPICS));
				allTypes.addAll(getRecordsByDBLink(dbId, FASTDB.PERSON));
				return allTypes;
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.warn("Exception while looking up id: " + dbId + ". Returning null.");
			return null;
		}
	}

	/**
	 * Checks the dbID and then searches
	 * the possible input types are listed should be as listed in Authority
	 */
	public static Set<FASTRecord> getRecordsByDB(String dbId, String type, FASTDB fastType) {
		if (dbId == null) {
			log.warn("dbId is null to lookup records from dbId");
			return null;
		}
		dbId = Authority.expandId(dbId, type);
		//escape special chars, are there anyt other special chars to be escaped.
		dbId = dbId.replaceAll("\\.", "\\\\.").replaceAll("\\/", "\\\\/");
		return getRecordsByDBLink(dbId, fastType);
	}

	public static Set<FASTRecord> getRecordsByDB(String dbId, String type) {
		return getRecordsByDB(dbId, type, FASTDB.ALL);
	}

	/**
	 * gets matches of a phrase, can supply original name or variant name ex:
	 * Dzhordzh -- variant of George Bush (what? who calls him that?)
	 * set imit to negative if dont want to set it.
	 */
	public static Set<FASTRecord> getMatches(String dname, FASTDB type, int limit) {
		String name = EmailUtils.normalizePersonNameForLookup(dname);
		if (name == null) {
			log.warn("Normalised name for: " + dname + " is null!");
			return null;
		}
		//lc it! normalization behaves weird with single word normalizations.
		name = name.toLowerCase();

		if (limit < 0)
			limit = 100;
		try {
			if (type != FASTDB.ALL) {
				BooleanQuery internal = new BooleanQuery();
				String[] words = name.split("\\W");
				CharArraySet stopWords = StopAnalyzer.ENGLISH_STOP_WORDS_SET;
				for (String w : words) {
					if (stopWords.contains(w))
						internal.add(new TermQuery(new Term(FASTRecord.CNAME, w)),
								BooleanClause.Occur.SHOULD);
					else {
						internal.add(new TermQuery(new Term(FASTRecord.CNAME, w)),
								BooleanClause.Occur.MUST);
					}
				}

				BooleanQuery bq = new BooleanQuery();
				bq.add(internal, BooleanClause.Occur.MUST);
				bq.add(new TermQuery(new Term(FASTRecord.TYPE, FASTRecord.getValidType(type))), BooleanClause.Occur.MUST);
				bq.add(new TermQuery(new Term(FASTRecord.SUB_TYPE, FASTRecord.LOOKUP)), BooleanClause.Occur.MUST);

				TopDocs docs = searcher.search(bq, null, limit);

				// records contains fast id of matching fast records.
				Set<String> records = new HashSet<String>();
				for (ScoreDoc sd : docs.scoreDocs) {
					Document d = searcher.doc(sd.doc);
					records.add(d.get(FASTRecord.ID));
				}

				Set<FASTRecord> items = new HashSet<FASTRecord>();
				for (String record : records) {
					bq = new BooleanQuery();
					bq.add(new TermQuery(new Term(FASTRecord.TYPE,
							FASTRecord.getValidType(type))), BooleanClause.Occur.MUST);
					bq.add(new TermQuery(new Term(FASTRecord.SUB_TYPE,
							FASTRecord.FULL_RECORD)), BooleanClause.Occur.MUST);
					bq.add(new TermQuery(new Term(FASTRecord.ID, record)),
							BooleanClause.Occur.MUST);
					// there should be only one such
					Set<FASTRecord> matches = getRecordsWith(bq);
					if (matches != null)
						items.addAll(matches);
				}
				return items;
			} else {
				Set<FASTRecord> allTypes = new HashSet<FASTRecord>();
				allTypes.addAll(getMatches(name, FASTDB.CORPORATE, limit));
				allTypes.addAll(getMatches(name, FASTDB.GEOGRAPHIC, limit));
				allTypes.addAll(getMatches(name, FASTDB.TOPICS, limit));
				allTypes.addAll(getMatches(name, FASTDB.PERSON, limit));
				return allTypes;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Set<FASTRecord> getMatches(String dname, FASTDB type) {
		return getMatches(dname, type, -1);
	}

	public static void main(String[] args) {
		long start_time = System.currentTimeMillis();
		//String dId = "http\\:\\/\\/viaf.org\\/viaf\\/71559485";
		//dId = "George_W._Bush";
		//String type = Authority.types[Authority.DBPEDIA];
		//Set<FASTRecord> matches = getRecordsByDB(dId, type, FASTDB.PERSON);
		Set<FASTRecord> matches = getMatches("robert creeley", FASTDB.PERSON, 5);
		for (FASTRecord m : matches) {
            //FASTPerson fp = (FASTPerson) m;
            log.info(m.getNames() + " --- " + m.getAllSources());
		}

		log.info("Query completed in :" + (System.currentTimeMillis() - start_time));
	}
}
