/*
 * Copyright (C) 2012 The Stanford MobiSocial Laboratory
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.stanford.muse.index;

import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.datacache.BlobStore;
import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.lang.Languages;
import edu.stanford.muse.util.*;
import edu.stanford.muse.webapp.ModeConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.SingleInstanceLockFactory;
import org.apache.lucene.util.*;
import org.apache.lucene.util.Version;
import org.apache.lucene.util.automaton.RegExp;

import javax.mail.Address;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

/*
 * this class is pretty closely tied with the summarizer (which generates cards).
 * the function of the indexer is to perform indexing, and provide a query
 * interface to the index.
 * 
 * This index has 2-levels: Docs and MultiDocs (that themselves contain entire
 * documents as subdocs) against each other,
 * The index can be looked up with specific terms, and returns subdocs that
 * contain the term.
 * 
 */
public class Indexer implements StatusProvider, java.io.Serializable {

	static Log					log					= LogFactory.getLog(Indexer.class);
	private static final long	serialVersionUID	= 1L;

	public enum QueryType {
		FULL, ORIGINAL, CORRESPONDENTS, SUBJECT, REGEX, PRESET_REGEX, META;
	};

	static final int			DEFAULT_SUBJECT_WEIGHT			= 2;							// weight
																								// given
																								// to
																								// email
																								// subject;
																								// 2
																								// means
																								// subject
																								// is
																								// given
																								// 2x
																								// weight
	public static final int		MAX_MAILING_LIST_NAME_LENGTH	= 20;
	private static final String	LANGUAGE_FIELD_DELIMITER	= "|";										// warning: make sure this is a single char, not a string because StringTokenizer constructor with "AB" will split on A or B
	public static final String	NAMES_FIELD_DELIMITER		= "\n";									// this String is not allowed within a name
	public static final Version LUCENE_VERSION				= Version.LUCENE_47;
	private static final String	INDEX_BASE_DIR_NAME			= Archive.INDEXES_SUBDIR;
	private static final String	INDEX_NAME_EMAILS			= "emails";
	private static final String	INDEX_NAME_ATTACHMENTS		= "attachments";
	public static String[]		presetQueries				= null;

	//Write feature related debug info to cache dir in user dir
	static FileWriter fw							= null;

	static {
		readPresetQueries();
	}

	private Map<String, EmailDocument>				docIdToEmailDoc			= new LinkedHashMap<String, EmailDocument>();			// docId -> EmailDoc
	private Map<String, Blob>						attachmentDocIdToBlob	= new LinkedHashMap<String, Blob>();					// attachment's docid -> Blob
	private HashMap<String, Map<Integer, String>>	dirNameToDocIdMap		= new LinkedHashMap<String, Map<Integer, String>>();	// just stores 2 maps, one for content and one for attachment Lucene doc ID -> docId

	//changed the below line
	public Directory directory;
	transient private Directory						directory_blob;																// for attachments
	transient private Analyzer analyzer;
	transient private IndexSearcher isearcher;
	transient private IndexSearcher					isearcher_blob;
	transient private QueryParser parser, parserOriginal, parserSubject, parserCorrespondents, parserRegex, parserMeta;		// parserOriginal searches the original content (non quoted parts) of a message
	transient private IndexWriter iwriter;
	transient private IndexWriter					iwriter_blob;
	transient private Map<Integer,String>			blobDocIds, contentDocIds;														// these are fieldCaches of ldoc -> docId for the docIds (for performance)

	transient private String						baseDir					= null;												// where the file-based directories should be stored (under "indexes" dir)

	// these are field type configs for all the fields to be stored in the email and attachment index.
	// most fields will use ft (stored and analyzed), and only the body fields will have a full_ft which is stored, analyzed (with stemming) and also keeps term vector offsets and positions for highlights
	// some fields like name_offsets absolutely don't need to be indexed and can be kept as storeOnly_ft
	transient private static FieldType storeOnly_ft;
	static transient FieldType ft;
	static transient FieldType full_ft, unanalyzed_full_ft;													// unanalyzed_full_ft for regex search
	static {
		storeOnly_ft = new FieldType();
		storeOnly_ft.setStored(true);
		storeOnly_ft.freeze();

		ft = new FieldType();
		ft.setStored(true);
		ft.setIndexed(true);
		ft.setTokenized(false);
		ft.freeze();

		full_ft = new FieldType();
		full_ft.setStored(true);
		full_ft.setIndexed(true);
		full_ft.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
		full_ft.freeze();

        //use this field for spanning regular expression, also tokenises text
//		unanalyzed_full_ft = new FieldType();
//		unanalyzed_full_ft.setStored(true);
//		unanalyzed_full_ft.setIndexed(true);
//		unanalyzed_full_ft.setTokenized(false);
//		unanalyzed_full_ft.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
//		unanalyzed_full_ft.freeze();
	}

	public static final CharArraySet MUSE_STOP_WORDS_SET;

	static {
		// Warning: changes in this list requires re-indexing of all existing archives.
		final List<String> stopWords = Arrays.asList(
				"a", "an", "and", "are", "as", "at", "be", "but", "by",
				"for", "if", "in", "into", "is", "it",
				"no", /*
					 * "not"
					 * ,
					 */"of", "on", "or", "such",
				"that", "the", "their", "then", "there", "these",
				"they", "this", "to", "was", "will", "with"
		);
		final CharArraySet stopSet = new CharArraySet(LUCENE_VERSION, stopWords.size(), false);
		stopSet.addAll(stopWords);
		MUSE_STOP_WORDS_SET = CharArraySet.unmodifiableSet(stopSet);
	}


	IndexOptions				io;
	private boolean				cancel							= false;
	protected List<LinkInfo>	links							= new ArrayList<LinkInfo>();

	// Collection<String> dataErrors = new LinkedHashSet<String>();

	public static class IndexStats implements java.io.Serializable {
		private static final long	serialVersionUID		= 1L;
		// int nTokens = 0, nStopTokens = 0, nDictTokens = 0;
		int							nDocuments				= 0;	// total #
																	// messages
																	// indexed
		long						indexedTextSize			= 0;	// #chars
																	// indexed
		long						indexedTextSizeOriginal	= 0;	// #chars
																	// indexed,
																	// original
																	// content
																	// only
		long						indexedTextLength_blob	= 0, nIndexedNames_blob = 0;	// attachments
																							// indexed

		// original => only in original content, i.e. not quoted parts of
		// messages
		int							nNames					= 0, nOriginalNames = 0;		// total
																							// #
																							// of
																							// occurrences,
																							// i.e.
																							// each
																							// name
																							// will
																							// be
																							// counted
																							// as
																							// many
																							// times
																							// as
																							// it
																							// occurs
		public int					nUniqueNames			= 0, nUniqueNamesOriginal = 0;	// #
																							// of
																							// unique
																							// names
																							// in
																							// entire
																							// corpus,
																							// #
																							// of
																							// unique
																							// names
																							// in
																							// entire
																							// corpus
																							// considering
																							// original
																							// text
																							// only
	}

	public IndexStats			stats					= new IndexStats();

	protected List<MultiDoc>	docClusters;
	Map<Integer, Integer>		nonEmptyTimeClusterMap	= new LinkedHashMap<Integer, Integer>();	// mapping
																									// of
																									// non-zero
																									// time
																									// cluster
																									// index
																									// to
																									// actual
																									// time
																									// cluster
																									// index

	public Map<String, Integer>	locationCounts			= new LinkedHashMap<String, Integer>();	// the
																									// #
																									// of
																									// times
																									// a
																									// string
																									// was
																									// listed
																									// as
																									// a
																									// location
																									// type
																									// by
																									// NER
	List<Document>				docs					= new ArrayList<Document>();
	List<Document>				subdocs					= new ArrayList<Document>();
	public Summarizer			summarizer				= new Summarizer(this);

	public Indexer(IndexOptions io) throws IOException
	{
		clear();
		this.io = io;
		try {
			if (io != null && io.do_NER)
				NER.initialize();
		} catch (Exception e) {
			Util.report_exception(e);
		}
	}

	public Indexer() throws IOException {
		this(null, (IndexOptions) null);
	}

	public Indexer(String baseDir, IndexOptions io) throws IOException {
		this(io);
		this.baseDir = baseDir;
		//		analyzer = new StandardAnalyzer(MUSE_LUCENE_VERSION, MUSE_STOP_WORDS_SET);
		//		analyzer = new SnowballAnalyzer(MUSE_LUCENE_VERSION, "English", MUSE_STOP_WORDS_SET);
		analyzer = newAnalyzer();
		directory = createDirectory(INDEX_NAME_EMAILS);
		iwriter = openIndexWriter(directory);
		directory_blob = createDirectory(INDEX_NAME_ATTACHMENTS);
		iwriter_blob = openIndexWriter(directory_blob);
	}

	public static interface FilterFunctor {
		boolean filter(org.apache.lucene.document.Document doc); // can modify doc and return whether the result should be included
	}

	public static void readPresetQueries() {
		List<String> q = new ArrayList<String>();
        String PRESET_QUERIES_FILE = "presetqueries.txt";
		String path = edu.stanford.muse.Config.SETTINGS_DIR + File.separator + PRESET_QUERIES_FILE;
		try {
			log.info("Reading preset queries from: " + path);
			File presetQueriesFile = new File(path);
			if (!presetQueriesFile.exists()) {
				log.warn("Preset queries file does not exist: " + path);

                File settingsDir = new File(edu.stanford.muse.Config.SETTINGS_DIR);
                if (!settingsDir.exists()) {
                    log.warn("Settings directory does not exist, creating: " + edu.stanford.muse.Config.SETTINGS_DIR);
                    settingsDir.mkdirs();
                }

                try {
                    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("/" + PRESET_QUERIES_FILE);
                    if (is == null)
                        log.warn("Huh? Someone forgot to embed the preset queries file in this webapp!");
                    else {
                        long bytes = Util.copy_stream_to_file(is, path);
                        is.close();
                        log.warn("Preset queries file copied successfully to: " + path + " (" + bytes + " bytes)");
                    }
                } catch (Exception e) {
                    Util.print_exception("Exception trying to copy embedded preset queries file: " + PRESET_QUERIES_FILE, e, log);
                    return;
                }
            }

			if (!presetQueriesFile.canRead()) {
                log.warn("Preset queries file exists, but is not readable: " + path);
				return;
			}

			BufferedReader br = new BufferedReader(new FileReader(presetQueriesFile));
			String line;
			while ((line = br.readLine()) != null)
				q.add(line);
			presetQueries = q.toArray(new String[q.size()]);
			log.info("Initiated " + q.size() + " preset queries");
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
            Util.print_exception("Exception while reading presetqueries file: " + path, e, log);
		}
	}

	// Below two routines are for dropping fields from index on the given directory.
	// (Adapted from http://www.flax.co.uk/blog/2011/06/24/how-to-remove-a-stored-field-in-lucene/)

	// report on whether some of these fields exist in the given directory, and return result
	private static boolean indexHasFields(Directory dir, String... fields) throws CorruptIndexException, IOException
	{
		return true; // return true pending migration of IndexReader to DirectoryReader

		//		if (Util.nullOrEmpty(fields))
		//			return false;
		//
		//		DirectoryReader reader = DirectoryReader.open(dir); // IndexReader.open(dir, true); // read-only=true
		//		// getFieldNames is no longer supported!
		//		Collection<String> fieldNames = reader.getFieldNames(FieldOption.ALL);
		//		reader.close();
		//		for (String field : fields) {
		//			if (fieldNames.contains(field)) {
		//				return true;
		//			}
		//		}
		//		return false;
	}

	/** returns whether indexAttachments succeeded */
	private synchronized boolean indexAttachments(EmailDocument e, BlobStore blobStore, Set<Blob> processedBlobSet, IndexStats stats) throws IOException
	{
		boolean result = true;
		// bail out if no attachments
		if (e.attachments == null)
			return result;

		final String DELIMITER = "\n";
		for (Blob b : e.attachments) {
			if (processedBlobSet != null && processedBlobSet.contains(b))
				continue; // skip if already processed (blob may be shared by multiple docs)

			int id_int = iwriter_blob.numDocs();
			String id = Integer.toString(++id_int);
			if (processedBlobSet != null)
				processedBlobSet.add(b);
			attachmentDocIdToBlob.put(id, b);

			org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document(); // not to be confused with edu.stanford.muse.index.Document
			Pair<String, String> content = b.getContent(blobStore);
			if (content == null) {
				// failed to process blob
				result = false;
				log.warn("Failed to fetch content from: "+b.filename+" content type: "+b.contentType+" size: "+b.getSize());
				continue; // but try to continue the proces0s
			}

			// imp: for id, should use Field.Index.NOT_ANALYZED field should be http://vuknikolic.wordpress.com/2011/01/03/lucenes-field-options-store-and-index-aka-rtfm/
			// note: id for attachments index is just sequential numbers, 1, 2, 3. etc.
			// it is not the full unique id (<folder>-<num>) that the emails index has.
			doc.add(new Field("docId", id, ft));
			String documentText = content.first + DELIMITER + content.second;

			// we'll store all languages detected in the doc as a field in the index
			Set<String> languages = Languages.getAllLanguages(documentText);
			String lang_str = Util.join(languages, LANGUAGE_FIELD_DELIMITER);
			doc.add(new Field("languages", lang_str, ft));

			if(edu.stanford.muse.Config.OPENNLP_NER) {
				Set<String> names = setNameFields(documentText, doc);

				String s = Util.join(names, NAMES_FIELD_DELIMITER); // just some connector for storing the field
				if (s == null)
					s = "";

				doc.add(new Field("names", s, ft));

				if(stats!=null)
					stats.nIndexedNames_blob += names.size();
			}

			// log.info ("blob metadata = " + content.first);
			//meta data does not contain the fileName
			doc.add(new Field("meta", content.first, ft));
			doc.add(new Field("fileName", b.filename, ft));

			doc.add(new Field("body", content.second, full_ft));

			iwriter_blob.addDocument(doc);
			//log.info("Indexed attachment #" + id + " : text = '" + documentText + "' names = '" + s + "'");
			if (stats != null) {
				stats.indexedTextLength_blob += documentText.length();
			}
		}
		return result;
	}

	/** returns whether indexAttachments succeeded */
	public synchronized boolean indexAttachments(Collection<EmailDocument> docs, BlobStore blobStore) throws CorruptIndexException, LockObtainFailedException, IOException
	{
		if (iwriter_blob == null) {
			//if (directory_blob == null) directory_blob = initializeDirectory(directory_blob, INDEX_NAME_ATTACHMENTS); // should already be valid
			iwriter_blob = openIndexWriter(directory_blob);
		}
		attachmentDocIdToBlob = new LinkedHashMap<String, Blob>();
		Set<Blob> processedBlobSet = new LinkedHashSet<Blob>();
		boolean result = true;
		for (EmailDocument e : docs) {
			result &= indexAttachments(e, blobStore, processedBlobSet, stats);
		}
		iwriter_blob.close();
		return result;
	}

	static private Directory createDirectory(String baseDir, String name) throws IOException
	{
		//return new RAMDirectory();
		String index_dir = baseDir + File.separator + INDEX_BASE_DIR_NAME;
		new File(index_dir).mkdir(); // will not create parent basedir if not already exist
		return FSDirectory.open(new File(index_dir + File.separator + name));
	}

	private Directory createDirectory(String name) throws IOException
	{
		return createDirectory(this.baseDir, name);
	}

	public void clear()
	{
		cancel = false;
		links.clear();
		summarizer.clear();
	}

	public boolean clustersIncludeAllDocs(Collection<edu.stanford.muse.index.Document> docs)
	{
		Set<edu.stanford.muse.index.Document> allIndexerDocs = new LinkedHashSet<edu.stanford.muse.index.Document>();
		for (MultiDoc mdoc : docClusters)
			allIndexerDocs.addAll(mdoc.docs);
		for (edu.stanford.muse.index.Document doc : docs)
			if (!allIndexerDocs.contains(doc))
				return false;
		return true;
	}

	/*
	 * public Collection<String> getDataErrors() { return
	 * Collections.unmodifiableCollection(dataErrors); }
	 */

	private String computeClusterStats(List<MultiDoc> clusters)
	{
		int nClusters = clusters.size();
		String clusterCounts = "";
		for (MultiDoc mdoc : clusters)
			clusterCounts += mdoc.docs.size() + "-";
		return nClusters + " document clusters with message counts: " + clusterCounts;
	}

	/**
	 * quick method for extracting links only, without doing all the parsing and
	 * indexing - used by slant, etc.
	 */
	public void extractLinks(Collection<edu.stanford.muse.index.Document> docs) throws IOException
	{
		try {
			for (edu.stanford.muse.index.Document d : docs)
			{
				if (cancel)
					break;

				String contents = "";
				if (!io.ignoreDocumentBody)
				{
					contents = getContents(d, true /* original content only */);
				}

				List<LinkInfo> linksForThisDoc = new ArrayList<LinkInfo>();
				IndexUtils.populateDocLinks(d, contents, linksForThisDoc, io.includeQuotedMessages);
				linksForThisDoc = removeDuplicateLinkInfos(linksForThisDoc);
				d.links = linksForThisDoc; // not necessarily needed, right ?
											// should have been init'ed when we
											// first read the contents of this
											// Doc. but doesn't hurt.
				links.addAll(linksForThisDoc);
			}
		} catch (Exception e) {
			Util.print_exception(e);
		}
	}

	public String computeStats()
	{
		return computeStats(true);
	}

	public String computeStats(boolean blur)
	{
		String result = "Index options: " + io.toString(blur) + "\n"; // + " " +
																		// currentJobDocsetSize
																		// +
																		// " post-filter docs and "
																		// +
		// docs.size() + " multi-docs\n" +
		// clusterStats + "\n";
		if (stats != null)
		{
			result += Util.fieldsToString(stats);
			// Util.commatize(stats.processedTextLength/1024) +
			// "K chars processed (" + stats.unoriginalTextLength/1024 +
			// "K unoriginal), " + stats.nProcessedNames + " names\n" +
			// Util.commatize(stats.processedTextLength_blob/1024) +
			// "K chars of attachments processed, " + stats.nProcessedNames_blob
			// + " names in attachments\n" +
			result += Util.commatize(InternTable.getSizeInChars()) + " chars in " + Util.commatize(InternTable.getNEntries()) + " entries in intern table\n";
		}
		Util.getMemoryStats();

		return result;
	}

	long	currentJobStartTimeMillis	= 0L;
	public int	currentJobDocsetSize, currentJobDocsProcessed, currentJobErrors;

	public void cancel()
	{
		cancel = true;
		log.warn("Indexer cancelled!");
	}

	public boolean isCancelled() {
		return cancel;
	}

	public String getStatusMessage()
	{
		if (currentJobDocsetSize == 0)
			return JSONUtils.getStatusJSON("Starting indexer...");

		// compute how much time remains
		long elapsedTimeMillis = System.currentTimeMillis() - currentJobStartTimeMillis;
		long unprocessedTimeSeconds = -1;

		// compute unprocessed message
		if (currentJobDocsProcessed != 0)
		{
			long unprocessedTimeMillis = (currentJobDocsetSize - currentJobDocsProcessed) * elapsedTimeMillis / currentJobDocsProcessed;
			unprocessedTimeSeconds = unprocessedTimeMillis / 1000;
		}

		int doneCount = currentJobDocsProcessed + currentJobErrors;
		String descriptor = (io.ignoreDocumentBody) ? "headers" : " messages";
		int pctComplete = (doneCount * 100) / currentJobDocsetSize;
		String processedMessage = "";
		if (pctComplete < 100)
			processedMessage = "Indexing " + Util.commatize(currentJobDocsetSize) + " " + descriptor;
		else
		{
			processedMessage = "Creating summaries...";
			unprocessedTimeSeconds = -1L;
		}
		return JSONUtils.getStatusJSON(processedMessage, pctComplete, elapsedTimeMillis / 1000, unprocessedTimeSeconds);
	}

	/** -1 => all clusters */
	public List<edu.stanford.muse.index.Document> getDocsInCluster(int clusterNum)
	{
		List<edu.stanford.muse.index.Document> result = new ArrayList<edu.stanford.muse.index.Document>();
		if (clusterNum < 0)
		{
			for (MultiDoc md : docClusters)
				result.addAll(md.docs);
		}
		else
		{
			MultiDoc clusterDocs = docClusters.get(clusterNum);
			result.addAll(clusterDocs.docs);
		}
		return result;
	}

	/**
	 * we're done indexing, optimize for storage, and prepare for queries, if
	 * applicable
	 */

	public Set<Document> docsForQuery(String term, int cluster, QueryType qt)
	{
		return docsForQuery(term, cluster, 1, qt);
	}

	public Set<Document> docsForQuery(int cluster, QueryType qt)
	{
		return docsForQuery(null, cluster, 1, qt);
	}

	//	public String getHTMLAnnotatedDocumentContents(String contents, Date d, String docId, String[] searchTerms, Boolean isRegexSearch, Set<String> stemmedTermsToHighlight,
	//			Set<String> unstemmedTermsToHighlight, Map<String, Map<String, Short>> entitiesWithId) throws Exception
	//	{
	//		return Highlighter.getHTMLAnnotatedDocumentContents(contents, d, docId, searchTerms, isRegexSearch, stemmedTermsToHighlight, unstemmedTermsToHighlight,
	//				entitiesWithId, null, summarizer.importantTermsCanonical /*
	//																		 * unstemmed
	//																		 * because
	//																		 * we
	//																		 * are
	//																		 * only
	//																		 * using
	//																		 * names
	//																		 */);
	//	}

	// remove any duplicate link URLs from the incoming LinkInfos
	private static List<LinkInfo> removeDuplicateLinkInfos(List<LinkInfo> input)
	{
		List<LinkInfo> result = new ArrayList<LinkInfo>();
		Set<String> seenURLs = new LinkedHashSet<String>();
		for (LinkInfo li : input)
			if (!seenURLs.contains(li.link))
			{
				result.add(li);
				seenURLs.add(li.link);
			}
		return result;
	}

	public List<LinkInfo> getLinks()
	{
		return links;
	}

	/**
	 * main entry point for indexing. note: recomputeCards has to be called
	 * separately
	 */
	/*
	 * void processDocumentCollection(List<MultiDoc> mDocs, List<Document> docs,
	 * BlobStore blobStore) throws Exception { log.info ("Processing " +
	 * docs.size() + " documents"); try { indexDocumentCollection(mDocs, docs,
	 * blobStore); } catch (OutOfMemoryError oome) { log.error
	 * ("Sorry, out of memory, results may be incomplete!"); clear(); } }
	 * 
	 * /** preprocessed and indexes the docs.
	 */
	/*
	 * private void indexDocumentCollection(List<MultiDoc> mDocs, List<Document>
	 * allDocs, BlobStore blobStore) throws Exception { this.clear();
	 * currentJobStartTimeMillis = System.currentTimeMillis();
	 * currentJobDocsetSize = allDocs.size(); currentJobDocsProcessed =
	 * currentJobErrors = 0;
	 * 
	 * System.gc(); String stat1 = "Memory status before indexing " +
	 * allDocs.size() + " documents: " + Util.getMemoryStats(); log.info
	 * (stat1); docClusters = mDocs;
	 * 
	 * if (io.do_NER) NER.printAllTypes();
	 * 
	 * computeClusterStats(mDocs); log.info ("Indexing " + allDocs.size() +
	 * " documents in " + docClusters.size() + " clusters"); int clusterCount =
	 * -1; int docsIndexed = 0, multiDocsIndexed = 0; Posting.nPostingsAllocated
	 * = 0; docClusters = mDocs;
	 * 
	 * try { for (MultiDoc md: docClusters) { clusterCount++; log.info
	 * ("-----------------------------"); log.info ("Indexing " + md.docs.size()
	 * + " documents in document cluster #" + clusterCount + ": " +
	 * md.description);
	 * 
	 * for (Document d: md.docs) { if (cancel) throw new CancelledException();
	 * 
	 * String contents = ""; if (!io.ignoreDocumentBody) { try { contents =
	 * d.getContents(); } catch (Exception e) { markDataError
	 * ("Exception trying to read " + d + ": " + e); } }
	 * 
	 * if (contents.length() > MAX_DOCUMENT_SIZE) { markDataError
	 * ("Document too long, size " + Util.commatize(contents.length()) +
	 * " bytes, dropping it. Begins with: " + d + Util.ellipsize(contents, 80));
	 * contents = ""; }
	 * 
	 * String subject = d.getSubjectWithoutTitle(); subject =
	 * EmailUtils.cleanupSubjectLine(subject);
	 * 
	 * indexSubdoc(subject, contents, d, blobStore);
	 * 
	 * docsIndexed++; currentJobDocsProcessed++; } // end cluster
	 * 
	 * log.info ("Finished indexing multi doc " + md); if (md.docs.size() > 0)
	 * log.info ("Current stats:" + computeStats());
	 * 
	 * multiDocsIndexed++; // IndexUtils.dumpDocument(clusterPrefix,
	 * clusterText); // i don't think we need to do this except for debugging
	 * System.out.print("."); // goes to console, that's ok...
	 * 
	 * if (md.docs.size() > 0) { String stat2 = ("Memory status after indexing "
	 * + docsIndexed + " of " + allDocs.size() + " documents in " +
	 * multiDocsIndexed + " (non-zero) multi-docs, total text length " +
	 * stats.processedTextLength + " chars, " + stats.nProcessedNames +
	 * " names. " + Util.getMemoryStats()); log.info (stat2); } } } catch
	 * (OutOfMemoryError oome) { String s =
	 * "REAL WARNING! SEVERE WARNING! Out of memory during indexing. Please retry with more memory!"
	 * + oome; s += "\n"; log.error (s); // option: heroically soldier on and
	 * try to work with partial results }
	 * 
	 * // imp: do this at the end to save memory. doesn't save memory during
	 * indexing but saves mem later, when the index is being used. // esp.
	 * important for lens. NER.release_classifier(); // release memory for
	 * classifier log.info ("Memory status after releasing classifier: " +
	 * Util.getMemoryStats()); packIndex();
	 * 
	 * return; }
	 */


	public Analyzer newAnalyzer() {
        // we can use LimitTokenCountAnalyzer to limit the #tokens

        EnglishAnalyzer stemmingAnalyzer = new EnglishAnalyzer(LUCENE_VERSION, MUSE_STOP_WORDS_SET);
        EnglishNumberAnalyzer snAnalyzer = new EnglishNumberAnalyzer(LUCENE_VERSION, MUSE_STOP_WORDS_SET);

        // these are the 3 fields for stemming, everything else uses StandardAnalyzer
        Map<String, Analyzer> map = new LinkedHashMap<String, Analyzer>();
        map.put("body", snAnalyzer);
        map.put("title", snAnalyzer);
        map.put("body_original", stemmingAnalyzer);

        KeywordAnalyzer keywordAnalyzer = new KeywordAnalyzer();
        // actually these do not need any real analyzer, they are just stored opaquely
        map.put("docId", keywordAnalyzer);
        map.put("names_offsets", keywordAnalyzer);
        //body redacted contains only  names and a lot of dots, hence it requires special handling.
//        if(ModeConfig.isPublicMode()) {
//            map.put("body", new Analyzer() {
//                @Override
//                protected TokenStreamComponents createComponents(final String fieldName,
//                                                                 final Reader reader) {
//                    Version matchVersion = Indexer.LUCENE_VERSION;
//                    final CICTokenizer source = new StandardNumberTokenizer(matchVersion, reader);
//                    TokenStream result = new LowerCaseFilter(matchVersion, source);
//                    return new TokenStreamComponents(source, result);
//                }
//            });
//        }

        //do not remove any stop words.
		StandardAnalyzer standardAnalyzer = new StandardAnalyzer(LUCENE_VERSION, CharArraySet.EMPTY_SET);
		PerFieldAnalyzerWrapper pfa = new PerFieldAnalyzerWrapper(standardAnalyzer, map);

		return pfa;
	}

	// set create = false to append to existing index.
	private IndexWriter openIndexWriter(Directory dir) throws CorruptIndexException, LockObtainFailedException, IOException
	{
		//IndexWriterConfig config = new IndexWriterConfig(MUSE_LUCENE_VERSION, null);
		//IndexWriter writer = new IndexWriter(dir, null, IndexWriter.MaxFieldLength.UNLIMITED);
		IndexWriterConfig iwc = new IndexWriterConfig(LUCENE_VERSION, analyzer);
		iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
		return new IndexWriter(dir, iwc); // , new IndexWriter.MaxFieldLength(250000));
	}

	public void setBaseDir(String baseDir)
	{
		if (this.baseDir != null && !this.baseDir.equals(baseDir)) {
			close();
		}
		this.baseDir = baseDir;
	}

	/**
	 * sets up directory
	 *
	 * @throws IOException
	 */
	public synchronized void setupDirectory() throws IOException
	{
		directory = initializeDirectory(directory, INDEX_NAME_EMAILS);
		directory_blob = initializeDirectory(directory_blob, INDEX_NAME_ATTACHMENTS);

		if (analyzer == null)
			analyzer = newAnalyzer();
	}

	/**
	 * sets up indexer just for reading... if needed for writing only, call
	 * setupForWrite. if need both read & write, call both.
	 */
	public synchronized void setupForRead()
	{
		log.info("setting up index for read only access");
		long startTime = System.currentTimeMillis();

		//closeHandles();
		try {
			setupDirectory();

			String[] defaultSearchFields, defaultSearchFieldsOriginal;
			String[] defaultSearchFieldSubject = new String[] { "title" }; // for subject only search
			String[] defaultSearchFieldCorrespondents = null;
            //body field should be there, as the content of the attachment lies in this field, should also include meta field?
            //why the search over en-names and en-names-original when body/body_original is included in the search fields?
            defaultSearchFields = new String[] { "body", "title", "to_names", "from_names", "cc_names", "bcc_names", "to_emails", "from_emails", "cc_emails", "bcc_emails" };
            defaultSearchFieldsOriginal = new String[] { "body_original", "title" }; // we want to leave title there because we want to always hit the title -- discussed with Peter June 27 2015
            defaultSearchFieldCorrespondents = new String[] { "to_names", "from_names", "cc_names", "bcc_names", "to_emails", "from_emails", "cc_emails", "bcc_emails" };
            // names field added above after email discussion with Sit 6/11/2013. problem is that we're not using the Lucene EnglishPossessiveFilter, so
            // NER will extract the name Stanford University in a sentence like:
            // "This is Stanford University's website."
            // but when the user clicks on the name "Stanford University" in say monthly cards, we
            // will not match the message with this sentence because of the apostrophe.

			//for searching an attchment with fileName
			String[] metaSearchFields = new String[]{"fileName"};
			// Parse a simple query that searches for "text":
			if (parser == null) {
				//parser = new QueryParser(MUSE_LUCENE_VERSION, defaultSearchField, analyzer);
				parser = new MultiFieldQueryParser(LUCENE_VERSION, defaultSearchFields, analyzer);
				parserOriginal = new MultiFieldQueryParser(LUCENE_VERSION, defaultSearchFieldsOriginal, analyzer);
				parserSubject = new MultiFieldQueryParser(LUCENE_VERSION, defaultSearchFieldSubject, analyzer);
				parserCorrespondents = new MultiFieldQueryParser(LUCENE_VERSION, defaultSearchFieldCorrespondents, analyzer);
				parserMeta = new MultiFieldQueryParser(LUCENE_VERSION, metaSearchFields, new KeywordAnalyzer());
			}

			/**
			 * Bunch of gotchas here
			 * Its a bad idea to store lucene internal docIds, as no assumptions about the internal docIds should be made;
			 * not even that they are serial. When searching, lucene may ignore logically deleted docs.
			 * Lucene does not handle deleted docs, and having these docs in search may bring down the search performance by 50%
			 * Deleted docs are cleaned only during merging of indices.*/
            int numContentDocs = 0, numContentDeletedDocs = 0, numAttachmentDocs = 0, numAttachmentDeletedDocs = 0;
			if (DirectoryReader.indexExists(directory)) {
				DirectoryReader ireader = DirectoryReader.open(directory);
				if(ireader.numDeletedDocs()>0)
                    log.warn ("!!!!!!!\nIndex reader has " + ireader.numDocs() + " doc(s) of which " + ireader.numDeletedDocs() + " are deleted)\n!!!!!!!!!!");
				isearcher = new IndexSearcher(ireader);
				contentDocIds = new LinkedHashMap<Integer, String>();

                numContentDocs = ireader.numDocs();
                numContentDeletedDocs = ireader.numDeletedDocs();

                Set<String> fieldsToLoad = new HashSet<String>();
                fieldsToLoad.add("docId");
                for(int i=0;i<ireader.maxDoc();i++){
					org.apache.lucene.document.Document doc = ireader.document(i,fieldsToLoad);
                    if(doc == null || doc.get("docId") == null)
						continue;
					contentDocIds.put(i, doc.get("docId"));
				}
				log.info("Loaded: "+contentDocIds.size()+" content docs");
			}

			if (DirectoryReader.indexExists(directory_blob))
			{
				IndexReader ireader_blob = DirectoryReader.open(directory_blob);
				isearcher_blob = new IndexSearcher(ireader_blob); // read-only=true
				blobDocIds = new LinkedHashMap<Integer, String>();

                numAttachmentDocs = ireader_blob.numDocs();
                numAttachmentDeletedDocs = ireader_blob.numDeletedDocs();

                Set<String> fieldsToLoad = new HashSet<String>();
				fieldsToLoad.add("docId");
				for(int i=0;i<ireader_blob.maxDoc();i++){
					org.apache.lucene.document.Document doc = ireader_blob.document(i,fieldsToLoad);
					if(doc == null || doc.get("docId") == null)
						continue;
					blobDocIds.put(i,doc.get("docId"));
                }
				log.info("Loaded: "+blobDocIds.size()+" attachment docs");
            }

            log.warn("Number of content docs: "+numContentDocs+", number deleted: "+numContentDeletedDocs);
            log.warn("Number of attachment docs: " + numAttachmentDocs + ", number deleted: " + numAttachmentDeletedDocs);

			if (dirNameToDocIdMap == null)
				dirNameToDocIdMap = new LinkedHashMap<String, Map<Integer, String>>();
		} catch (Exception e) {
			Util.print_exception(e, log);
		}
		log.info ("Setting up index for read took " + (System.currentTimeMillis() - startTime) + " ms");
	}

	public int nDocsInIndex()
	{
		return iwriter.maxDoc();
	}

	/**
	 * preferably use this method to open an existing index.... set it up only
	 * for writing. if need read also, call setupForRead too.
	 * but packIndex MUST be called to close the writer, otherwise lucene will
	 * throw write lock errors
	 */
	public void setupForWrite() throws CorruptIndexException, LockObtainFailedException, IOException
	{
		log.info("setting up index for write access");
		setupDirectory();

		if (iwriter == null)
			iwriter = openIndexWriter(directory);
		if (iwriter_blob == null)
			iwriter_blob = openIndexWriter(directory_blob);

		// invalidate content/blobs -> docIdMap map as Lucene doc ID may change after writes (?)
		dirNameToDocIdMap = new LinkedHashMap<String, Map<Integer, String>>();
	}

	private synchronized Directory initializeDirectory(Directory dir, String name) throws IOException
	{
		if (dir == null)
			dir = createDirectory(name);
		if (dir.getLockFactory() == null)
			dir.setLockFactory(new SingleInstanceLockFactory());
		return dir;
	}

	/*
	 * properly close index writers.
	 * also null out directories that may not be serializable
	 * (e.g., RAMDirectory is, but FSDirectory isn't)
	 */
	public void close()
	{
		log.info("Closing indexer handles");
		//closeHandles();
		analyzer = null;
		parser = parserOriginal = parserSubject = parserCorrespondents = null;
		try {
			if (isearcher != null)
				isearcher.getIndexReader().close();
		} catch (IOException e) {
			Util.print_exception(e, log);
		}
		isearcher = null;
		try {
			if (isearcher_blob != null)
				isearcher_blob.getIndexReader().close();
		} catch (IOException e) {
			Util.print_exception(e, log);
		}
		isearcher_blob = null;
		try {
			if (iwriter != null)
				iwriter.close();
		} catch (Exception e) {
			Util.print_exception(e, log);
		}
		iwriter = null;
		try {
			if (iwriter_blob != null)
				iwriter_blob.close();
		} catch (Exception e) {
			Util.print_exception(e, log);
		}
		iwriter_blob = null;

		if (directory != null && !(directory instanceof Serializable)) {
			try {
				directory.close();
			} catch (Exception e) {
				Util.print_exception(e, log);
			}
			try {
				directory_blob.close();
			} catch (Exception e) {
				Util.print_exception(e, log);
			}
			directory = null;
			directory_blob = null;
		}
	}

	public Map<String, EmailDocument> getDocMap() {
		return docIdToEmailDoc;
	}

	public static Set<String> extractNames(String text) throws Exception
	{
		List<Pair<String, Float>> pairs = NER.namesFromText(text);
		Set<String> names = new LinkedHashSet<String>();
		for (Pair<String, ?> p : pairs)
			names.add(p.getFirst());

		return Util.scrubNames(names);
	}

	// quote chars (> ) interfere with name recognition, so remove them from the text.
	// however be careful that the char positions don't change since NER returns us offsets into the string.
	private static String prepareFullBodyForNameExtraction(String text)
	{
		// first search & replace the longer strings
		text = text.replaceAll("\n> > ", "     ");
		text = text.replaceAll("\n> ", "   ");
		return text;
	}

	/**
	 * returns all names from text. also adds fields of the name category to the
	 * doc, with the value being strings of the category, separated by
	 * NAMES_FIELD_DELIMITER.
	 * e.g. name category could be "person", with value
	 * "Monica<NAMES_FIELD_DELIMITER>David"
	 */
	private Set<String> setNameFields(String text, org.apache.lucene.document.Document doc)
	{
		text = prepareFullBodyForNameExtraction(text);
		Pair<Map<String, List<String>>, List<Triple<String, Integer, Integer>>> mapAndOffsets = NER.categoryToNamesMap(text);

		List<Triple<String, Integer, Integer>> offsets = mapAndOffsets.second;
		storeNameOffsets(doc, offsets);

		Map<String, List<String>> categoryToNamesMap = mapAndOffsets.first;
		// we'll get people, places, orgs in this map

		Set<String> allNames = new LinkedHashSet<String>();
		for (String category : categoryToNamesMap.keySet())
		{
			Set<String> namesForThisCategory = Util.scrubNames(categoryToNamesMap.get(category));
			String s = Util.join(namesForThisCategory, NAMES_FIELD_DELIMITER);
			doc.add(new Field(category.toLowerCase(), s, ft));
			allNames.addAll(namesForThisCategory);
		}

		return allNames;
	}

	private void storeNameOffsets(org.apache.lucene.document.Document doc, List<Triple<String, Integer, Integer>> offsets)
	{
		try {
			ByteArrayOutputStream bs = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bs);
			oos.writeObject(offsets);
			oos.close();
			bs.close();
			doc.add(new Field("names_offsets", bs.toByteArray(), storeOnly_ft));
		} catch (IOException e) {
			log.warn("Failed to serialize names_offsets");
			e.printStackTrace();
		}
	}

	public static List<Triple<String, Integer, Integer>> getNamesOffsets(org.apache.lucene.document.Document doc)
	{
		BytesRef bytesRef = doc.getBinaryValue("names_offsets");
		if (bytesRef == null)
			return null;
		byte[] data = bytesRef.bytes;
		if (data == null)
			return null;

		ByteArrayInputStream bs = new ByteArrayInputStream(data);
		List<Triple<String, Integer, Integer>> result;
		ObjectInputStream ois;
		try {
			ois = new ObjectInputStream(bs);
			result = (List<Triple<String, Integer, Integer>>) ois.readObject();
		} catch (Exception e) {
			log.warn("Failed to deserialize names_offsets");
			e.printStackTrace();
			result = new ArrayList<Triple<String, Integer, Integer>>();
		}

		return result;
	}

	/**
	 * internal method, core method for adding a doc to the index.
	 * adds body, title, people, places, orgs. assumes the index, iwriter etc
	 * are already set up and open.
	 * does NER and returns the list of all names
	 *
	 * @param stats
	 */
	private synchronized int add1DocToIndex(String title, String body, edu.stanford.muse.index.Document d, IndexStats stats) throws Exception
	{
		org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document(); // not to be confused with edu.stanford.muse.index.Document

		String id = d.getUniqueId();
		//		softAssertDocIdNotPresent(id);

		// imp: for id, should use Field.Index.NOT_ANALYZED field should be http://vuknikolic.wordpress.com/2011/01/03/lucenes-field-options-store-and-index-aka-rtfm/
		doc.add(new Field("docId", id, ft));

		// we'll store all languages detected in the doc as a field in the index
		Set<String> languages = Languages.getAllLanguages(body);
		d.languages = languages;
		String lang_str = Util.join(languages, LANGUAGE_FIELD_DELIMITER);
		doc.add(new Field("languages", lang_str, ft));

		doc.add(new Field("title", title, full_ft));

		// extract names from both title (weighted by subjectWeight) and body. put a fullstop after title to help NER.
		StringBuilder effectiveSubject = new StringBuilder(); // put body first so that the extracted NER offsets can be used without further adjustment at a later phase.
		for (int i = 0; i < io.subjectWeight; i++)
		{
			effectiveSubject.append(". ");
			effectiveSubject.append(title);
		}

		// there are multiple body fields:
		// body_raw is the original as-is text, that will be used for rendering the message
		if (d instanceof EmailDocument)
		{
			EmailDocument ed = (EmailDocument) d;

			// Store the to, from, cc, bcc fields as string
			String to_emails = Util.join(EmailUtils.emailAddrs(ed.to), " ");
			doc.add(new Field("to_emails", to_emails, ft));

			String to_names = Util.join(EmailUtils.personalNames(ed.to), " ");
			doc.add(new Field("to_names", to_names, ft));

			String from_emails = Util.join(EmailUtils.emailAddrs(ed.from), " ");
			doc.add(new Field("from_emails", from_emails, ft));

			String from_names = Util.join(EmailUtils.personalNames(ed.from), " ");
			doc.add(new Field("from_names", from_names, ft));

			String cc_emails = Util.join(EmailUtils.emailAddrs(ed.cc), " ");
			doc.add(new Field("cc_emails", cc_emails, ft));

			String cc_names = Util.join(EmailUtils.personalNames(ed.cc), " ");
			doc.add(new Field("cc_names", cc_names, ft));

			String bcc_emails = Util.join(EmailUtils.emailAddrs(ed.bcc), " ");
			doc.add(new Field("bcc_emails", bcc_emails, ft));

			String bcc_names = Util.join(EmailUtils.personalNames(ed.bcc), " ");
			doc.add(new Field("bcc_names", bcc_names, ft));

			doc.add(new Field("body", body, full_ft)); // save raw before preprocessed
		}

		// the body field is what is used for searching. almost the same as body_raw, but in some cases, we expect
		// body != body_raw, e.g. if we have special logic to strip signatures, boilerplate text etc.
		// at the moment, body == body_raw, so we don't bother to store it.
		// doc.add(new Field("body", body, Field.Store.YES, Field.Index.ANALYZED));

		// body original is the original content in the message. (i.e. non-quoted, non-forwarded, etc.)
		String bodyOriginal = EmailUtils.getOriginalContent(body);
		doc.add(new Field("body_original", bodyOriginal, full_ft));
		int originalTextLength = bodyOriginal.length();
		Set<String> namesOriginal = null;

		log.info("Length of body :" + body.length() + ", body original: " + bodyOriginal.length());
		int ns = 0;

		if(edu.stanford.muse.Config.OPENNLP_NER) {
            String textForNameExtraction = body + ". " + effectiveSubject; // Sit says put body first so that the extracted NER offsets can be used without further adjustment for epadd redaction
            Set<String> allNames = setNameFields(textForNameExtraction, doc);

			String names = Util.join(allNames, NAMES_FIELD_DELIMITER);

			// should the names/names_orig filed be analyzed? We don't want them
			// stemmed, after all. However, we do want to match case-insensitive
			// NAMES_FIELD_DELIMITER is just some connector for storing the field
			doc.add(new Field("names", names, ft));

			// just reuse names for the common case of body = bodyOriginal
			if (bodyOriginal.equals(body))
				namesOriginal = allNames;
			else {
				String originalTextForNameExtraction = bodyOriginal + ". " + effectiveSubject;
				namesOriginal = extractNames(originalTextForNameExtraction);
			}
			if(stats!=null) {
				stats.nNames += allNames.size();
				stats.nOriginalNames += namesOriginal.size();
			}
			ns = allNames.size();

			String namesOriginalString = Util.join(namesOriginal, NAMES_FIELD_DELIMITER);
			doc.add(new Field("names_original", namesOriginalString, ft));
		}

		iwriter.addDocument(doc);

		// why not maintain lucene doc id map instead of email doc id -> doc
		// how to get lucene doc id?
		docIdToEmailDoc.put(id, (EmailDocument) d);
		if (stats != null) {
			stats.nDocuments++;
			stats.indexedTextSize += title.length() + body.length();
			stats.indexedTextSizeOriginal += originalTextLength;
		}
		return ns;
	}

	public void updateDocument(org.apache.lucene.document.Document doc) {
		try {
			iwriter.updateDocument(new Term("docId", doc.get("docId")), doc);
		} catch (Exception e) {
			e.printStackTrace();
			Util.print_exception("Exception while updating document: " + doc, e, log);
		}
	}

	/* note sync. because we want only one writer to be going at it, at one time */
	public synchronized void indexSubdoc(String title, String documentText, edu.stanford.muse.index.Document d, BlobStore blobStore)
	{
		if (d == null)
			return;

		if (documentText == null)
			documentText = "";

		try {
			add1DocToIndex(title, documentText, d, stats);
			if (blobStore != null && d instanceof EmailDocument && io.indexAttachments)
				indexAttachments((EmailDocument) d, blobStore, null, stats);
		} catch (Exception e) {
			Util.print_exception(e, log);
		}

		if (d instanceof EmailDocument)
			if (d.links != null)
				links.addAll(d.links);
	}

	public List<org.apache.lucene.document.Document> getAllDocs(Boolean attachmentType) throws IOException
	{
		List<org.apache.lucene.document.Document> result = new ArrayList<org.apache.lucene.document.Document>();

		// read all the docs in the leaf readers. omit deleted docs.
		DirectoryReader r;
		if(attachmentType)
			r = DirectoryReader.open(directory_blob);
		else
			r = DirectoryReader.open(directory);

		List<AtomicReaderContext> atomicReaderContexts = r.leaves();
		for (AtomicReaderContext atomicReaderContext : atomicReaderContexts)
		{
			AtomicReader atomicReader = atomicReaderContext.reader();
			Bits bits = atomicReader.getLiveDocs();
			for (int i = 0; i < atomicReader.numDocs(); i++)
				if (bits!=null && bits.get(i))
					result.add(r.document(i));
				else if(bits == null)
					result.add(r.document(i));
		}
		r.close();
		return result;
	}

	public List<org.apache.lucene.document.Document> getAllDocs() throws IOException{
		return getAllDocs(false);
	}

	public List<org.apache.lucene.document.Document> getAttachmentDocs() throws IOException {
		return getAllDocs(true);
	}

	public EmailDocument docForId(String id) {
		return docIdToEmailDoc.get(id);
	}

	public List<Set<String>> getAllNames(Collection<String> ids, QueryType qt) throws CorruptIndexException, IOException
	{
		List<Set<String>> result = new ArrayList<Set<String>>();
		for (String id : ids)
			result.add(getNamesForDocIdAsSet(id, qt));
		return result;
	}

	public Set<edu.stanford.muse.index.Document> docsForQuery(String term, int cluster, int threshold, QueryType qt)
	{
		//doesn't need term for preset regex
		if (qt != QueryType.PRESET_REGEX && Util.nullOrEmpty(term))
			return new LinkedHashSet<edu.stanford.muse.index.Document>((Collection) getDocsInCluster(cluster));

		Set<edu.stanford.muse.index.Document> docs_in_cluster = null;
		if (cluster != -1)
			docs_in_cluster = new LinkedHashSet<edu.stanford.muse.index.Document>((Collection) getDocsInCluster(cluster));

		Set<edu.stanford.muse.index.Document> result = new LinkedHashSet<edu.stanford.muse.index.Document>();
		try {
			Collection<String> hitDocIds = luceneLookup(term, threshold, isearcher, qt);
			for (String d : hitDocIds)
			{
				EmailDocument ed = docIdToEmailDoc.get(d);
				if (ed != null) {
					if (cluster == -1 || docs_in_cluster.contains(ed))
						// if for a specific cluster, obtain only the docs in the cluster
						result.add(ed);
				} else {
					log.warn("Index hit for doc id " + d + " but doc does not exist!");
				}
			}
		} catch (Exception e) {
			Util.print_exception(e);
		}

		return result;
	}

	public Set<Blob> blobsForQuery(String term)
	{
		Set<Blob> result = new LinkedHashSet<Blob>();

		if (Util.nullOrEmpty(term))
			return result;

		try {
			Collection<String> hitDocIds = luceneLookup(term, 1, isearcher_blob, QueryType.FULL);
			for (String d : hitDocIds)
			{
				Blob b = attachmentDocIdToBlob.get(d);
				if (b != null) {
					result.add(b);
				} else {
					log.warn("Index hit for blob id " + d + " but blob does not exist in map!");
				}
			}
		} catch (Exception e) {
			Util.print_exception(e);
		}

		return result;
	}

	public int countHitsForQuery(String q) {
		return countHitsForQuery(q, QueryType.FULL);
	}

	public int countHitsForQuery(String q, QueryType qt)
	{
		if (Util.nullOrEmpty(q))
			return 0;

		try {
			QueryParser parserToUse;
			switch (qt) {
				case ORIGINAL:
					parserToUse = parserOriginal;
					break;
				case SUBJECT:
					parserToUse = parserSubject;
					break;
				case CORRESPONDENTS:
					parserToUse = parserCorrespondents;
					break;
				default:
					parserToUse = this.parser;
			}
			Query query = parserToUse.parse(q);
			//			query = convertRegex(query); // to mimic built-in regex support
			ScoreDoc[] hits = isearcher.search(query, null, edu.stanford.muse.Config.MAX_DOCS_PER_QUERY).scoreDocs;
			return hits.length;
		} catch (Exception e) {
			Util.print_exception(e);
		}

		return 0;
	}

	private Set<String> getNamesForDocIdAsSet(String id, QueryType qt) throws IOException
	{
		return new LinkedHashSet<String>(getNamesForDocId(id, qt));
	}

	public List<String> getNamesForDocId(String id, QueryType qt) throws IOException
	{
		// get this doc from the index first...
		org.apache.lucene.document.Document docForThisId = getLDoc(id);
		return getNamesForDoc(docForThisId, qt);
	}

	private Set<String> getNamesForDocAsSet(org.apache.lucene.document.Document doc, QueryType qt)
	{
		return new LinkedHashSet<String>(getNamesForDoc(doc, qt));
	}

	public List<String> getNamesOfTypeForDoc(String field, String docId, QueryType qt) throws IOException
	{
		org.apache.lucene.document.Document docForThisId = getLDoc(docId);

		List<String> result = new ArrayList<String>();
		if (docForThisId == null)
		{
			log.warn("trying to get names from null doc");
			return result;
		}
		// ...and read its names
		//		String names = (qt == QueryType.ORIGINAL) ? doc.get("names_original") : doc.get("names");
		String names = (qt == QueryType.ORIGINAL) ? docForThisId.get(field + "_original") : docForThisId.get(field);
		if (!Util.nullOrEmpty(names)) {
			StringTokenizer names_st = new StringTokenizer(names, Indexer.NAMES_FIELD_DELIMITER);
			while (names_st.hasMoreTokens())
				result.add(names_st.nextToken());
		}
		return result;
	}

	private List<String> getNamesForDoc(org.apache.lucene.document.Document doc, QueryType qt)
	{
		List<String> result = new ArrayList<String>();
		if (doc == null)
		{
			log.warn("trying to get names from null doc");
			return result;
		}
		// ...and read its names
		String names = (qt == QueryType.ORIGINAL) ? doc.get("names_original") : doc.get("names");
		if (!Util.nullOrEmpty(names)) {
			StringTokenizer names_st = new StringTokenizer(names, Indexer.NAMES_FIELD_DELIMITER);
			while (names_st.hasMoreTokens())
				result.add(names_st.nextToken());
		}
		return result;
	}

    private Collection<String> luceneLookupAsDocIds (String q, int threshold, IndexSearcher searcher, QueryType qt) throws IOException, ParseException, GeneralSecurityException, ClassNotFoundException{
        Pair<Collection<String>,Integer> p = luceneLookupAsDocIdsWithTotalHits(q, threshold, searcher, qt, 10000);
        return p.first;
    }

    public Integer getTotalHits(String q, boolean isAttachments, QueryType qt) throws IOException, ParseException, GeneralSecurityException, ClassNotFoundException{
        Pair<Collection<String>,Integer> p = null;
        if (!isAttachments)
            p = luceneLookupAsDocIdsWithTotalHits(q, 1, isearcher, qt, 1);
        else
            p = luceneLookupAsDocIdsWithTotalHits(q, 1, isearcher_blob, qt, 1);
        return p.second;
    }

	/**
	 * returns collection of docIds of the Lucene docs that hit, at least
	 * threshold times.
	 * warning! only looks up body field, no others
	 */
	private Pair<Collection<String>,Integer> luceneLookupAsDocIdsWithTotalHits(String q, int threshold, IndexSearcher searcher, QueryType qt, int lt) throws IOException, ParseException, GeneralSecurityException, ClassNotFoundException
	{
		Collection<String> result = new ArrayList<String>();

		//	String escaped_q = escapeRegex(q); // to mimic built-in regex support
		String escaped_q = q;
		//TODO: There should also be a general query type that takes any query with field param, i.e. without parser
		Query query;
		if (qt == QueryType.ORIGINAL)
			query = parserOriginal.parse(escaped_q);
		else if (qt == QueryType.SUBJECT)
			query = parserSubject.parse(escaped_q);
		else if (qt == QueryType.CORRESPONDENTS)
			query = parserCorrespondents.parse(escaped_q);
		else if (qt == QueryType.REGEX)
		{
			query = new BooleanQuery();
			/**
			 * Note: this is not a spanning (i.e. doesn't search over more than
			 * one token) regexp, for spanning regexp use: body_unanlyzed and
			 * title_unanlyzed fields instead
			 */
			Query query1 = new RegexpQuery(new Term("body", q), RegExp.ALL);
			Query query2 = new RegexpQuery(new Term("title", q), RegExp.ALL);
			((BooleanQuery) query).add(query1, org.apache.lucene.search.BooleanClause.Occur.SHOULD);
			((BooleanQuery) query).add(query2, org.apache.lucene.search.BooleanClause.Occur.SHOULD);
		} else if (qt == QueryType.PRESET_REGEX) {
			query = new BooleanQuery();
			if(presetQueries != null) {
				for (String pq : presetQueries) {
					Query query1 = new RegexpQuery(new Term("body", pq), RegExp.ALL);
					Query query2 = new RegexpQuery(new Term("title", pq), RegExp.ALL);
					((BooleanQuery) query).add(query1, org.apache.lucene.search.BooleanClause.Occur.SHOULD);
					((BooleanQuery) query).add(query2, org.apache.lucene.search.BooleanClause.Occur.SHOULD);
				}
				log.info("Doing a preset regex search");
			}else{
				log.warn("Preset queries is not initialised");
			}
		} else if (qt == QueryType.META) {
            query = parserMeta.parse(escaped_q);
		} else
			query = parser.parse(escaped_q);
		log.info("Query: "+query);

		//		query = convertRegex(query);
        TopDocs tds = searcher.search(query, null, lt);
		ScoreDoc[] hits = tds.scoreDocs;
        int totalHits = tds.totalHits;

		// this logging causes a 50% overhead on the query -- maybe enable it only for debugging
		// log.info (hits.length + " hits for query " + Util.ellipsize(q, 30) + " => " + Util.ellipsize(escaped_q, 30) + " = " + Util.ellipsize(query.toString(), 30) + " :");

		// Iterate through the results:

		// TODO: not very pretty code here to determine dir_name which selects the cache to use
		Util.softAssert(searcher == isearcher || searcher == isearcher_blob);
		String dir_name = searcher == isearcher ? INDEX_NAME_EMAILS : INDEX_NAME_ATTACHMENTS;

		Map<Integer, String> map = dirNameToDocIdMap.get(dir_name);
		if (map == null) {
			map = new LinkedHashMap<Integer, String>();
			dirNameToDocIdMap.put(dir_name, map);
			log.info("Adding new entry for dir name to docIdMap");
		} else {
			log.info("Existing entry for dir name to docIdMap");
		}

		int n_added = 0;
		log.info("Found: " + hits.length + " hits for query: " + q);
		for (int i = 0; i < hits.length; i++) {
			int ldocId = hits[i].doc; // this is the lucene doc id, we need to map it to our doc id.

			String docId = null; // this will be our doc id

			// try to use the new fieldcache id's
			// if this works, we can get rid of the dirNameToDocIdMap
			try {
				docId = (searcher == isearcher) ? contentDocIds.get(ldocId) : blobDocIds.get(ldocId);
			} catch (Exception e) {
				Util.print_exception(e, log);
				continue;
			}

            Explanation expl1 = searcher.explain(query, hits[i].doc);
			if (threshold <= 1)
			{
				// common case: threshold is 1.
				result.add(docId);
				n_added++;
			}
			else
			{
				// more expensive, do it only if threshold is > 1
				Explanation expl = searcher.explain(query, hits[i].doc);
				Explanation[] details = expl.getDetails();
				// NB: a catch here is that details.length doesn't reflect the actual # of hits for the query.
				// sometimes, for a single hit, there are 2 entries, a ComplexExplanation and an Explanation.
				// not sure why, but is somewhat corroborated by the code:
				// http://massapi.com/class/ex/Explanation.html
				// showing a single hit creating both a C.E and an E.
				// a more robust approach might be to look for the summary to end with product of: , sum of: etc.
				// e.g. http://www.gossamer-threads.com/lists/lucene/java-dev/49706
				// but for now, we'll count only the number of ComplexExplanation and check if its above threshold
				//				log.info("doc id " + hits[i].toString() + " #details = " + details.length);

				// HORRIBLE HACK! - because we don't know a better way to find the threshold
				outer: for (Explanation detail : details)
				{
					// log.info(detail.getClass().getName());

					if (detail instanceof ComplexExplanation)
					{
						ComplexExplanation ce = (ComplexExplanation) detail;
						String s = ce.toString();
						int total_tf = 0;
						while (true)
						{
							int idx = s.indexOf("tf(termFreq(");
							if (idx < 0)
								break outer;
							s = s.substring(idx);
							idx = s.indexOf("=");
							if (idx < 0)
								break outer;
							s = s.substring(idx + 1);
							int idx1 = s.indexOf(")");
							if (idx < 0)
								break outer;
							String num_str = s.substring(0, idx1);
							int num = 0;
							try {
								num = Integer.parseInt(num_str);
							} catch (Exception e) {
								log.warn("ERROR parsing complex expl: " + num_str);
							}
							total_tf += num;
							if (total_tf >= threshold)
							{
								result.add(docId);
								n_added++;
								break outer;
							}
						}
					}
				}
			}
		}
		log.info(n_added + " docs added to docIdMap cache");
		return new Pair<Collection<String>,Integer>(result, totalHits);
	}

	/** returns collection of docId's that hit, at least threshold times */
	public Collection<String> luceneLookup(String q, int threshold, IndexSearcher searcher, QueryType qt) throws CorruptIndexException, LockObtainFailedException, IOException, ParseException, GeneralSecurityException, ClassNotFoundException
	{
		// get as documents, then convert to ids
		return luceneLookupAsDocIds(q, threshold, searcher, qt);
	}

	public Set<EmailDocument> luceneLookupDocs(String q) throws CorruptIndexException, LockObtainFailedException, IOException, ParseException, GeneralSecurityException, ClassNotFoundException
	{
		return luceneLookupDocs(q, QueryType.FULL);
	}

	/** returns collection of EmailDocs that hit */
	public Set<EmailDocument> luceneLookupDocs(String q, QueryType qt) throws CorruptIndexException, LockObtainFailedException, IOException, ParseException, GeneralSecurityException, ClassNotFoundException
	{
		Collection<String> docIds = luceneLookupAsDocIds(q, 1, isearcher, qt);
		Set<EmailDocument> result = new LinkedHashSet<EmailDocument>();
		for (String docId : docIds)
		{
			EmailDocument ed = docIdToEmailDoc.get(docId);
			if (ed != null)
			{
				//				if (ed.languages == null)
				//				{
				//					// extract languages for the doc from the index and store it in the document, so we don't have to compute it again
				//					String lang = getDoc(docId).getFieldable("languages").stringValue();
				//					StringTokenizer st = new StringTokenizer(lang, ",");
				//					Set<String> langs = new LinkedHashSet<String>();
				//					while (st.hasMoreTokens())
				//						langs.add(st.nextToken());
				//					if (langs.size() == 0) // shouldn't happen, just defensive
				//						langs.add("english");
				//					ed.languages = langs;
				//				}
				result.add(ed);
			}
			else
				log.warn("Inconsistent Index! docId " + docId + " is a hit in the index, but is not in the doc map!");
		}
		return result;
	}

    //@return pair of content of the attachment and status of retrieval
	public Pair<String,String> getContentsOfAttachment(String fileName){
		try {
            fileName = "\""+fileName+"\"";
			Collection<String> docIds = luceneLookupAsDocIds(fileName, 1, isearcher_blob, QueryType.META);
			if(docIds == null) {
				log.error("lookup for " + fileName + " returned null");
				return new Pair<String,String>(null, "Lookup failed!");
			}
			if(docIds.size()>1)
				log.warn("More than one attachment with the same attachment name: "+fileName);
			if(docIds.size() == 0) {
				log.warn("No docIds found for fileName: " + fileName);
                return new Pair<String,String> (null, "No attachment found with that name");
			}

			String docId = docIds.iterator().next();
			org.apache.lucene.document.Document ldoc = getLDocAttachment(docId);
			if(ldoc == null) {
				log.error("Cannot find lucene doc for docId: "+docId);
				return new Pair<String,String>(null, "Internal error! Cannot find lucene doc for docId: "+docId);
            } else {
                log.info("Found doc for attachment: "+fileName);
			}
			return new Pair<String,String>(ldoc.get("body"),"success");
		}catch(Exception e){
			log.info("Exception while trying to fetch content of "+fileName, e);
			return new Pair<String,String>(null, e.getMessage());
		}
	}

	// since we may need to rebuild the index in a new directory, the analyzer needs to have been initialized apriori
	public synchronized Directory copyDirectoryExcludeFields(Directory dir, String out_basedir, String out_name, String... fields_to_be_removed) throws CorruptIndexException, IOException
	{
		IndexReader reader = DirectoryReader.open(dir); // IndexReader.open(dir, true); // read-only=true

		Directory newDir = createDirectory(out_basedir, out_name);
		IndexWriter writer = openIndexWriter(newDir);
		//log.info("Removing field(s) " + Util.join(fields_to_be_removed, ", ") + " from index.");

		for (int i = 0; i < reader.numDocs(); i++) {
			org.apache.lucene.document.Document doc = reader.document(i);
			for (String field : fields_to_be_removed)
				doc.removeFields(field);
			writer.addDocument(doc);
		}

		writer.close();
		reader.close();

		return newDir;
	}

	// since we may need to rebuild the index in a new directory, the analyzer needs to have been initialized apriori
	public synchronized Directory copyDirectoryWithDocFilter(Directory dir, String out_basedir, String out_name, FilterFunctor filter_func) throws CorruptIndexException, IOException
	{
		long startTime = System.currentTimeMillis();
		IndexReader reader = DirectoryReader.open(dir); // IndexReader.open(dir, true); // read-only=true

		Directory newDir = createDirectory(out_basedir, out_name);
		IndexWriter writer = openIndexWriter(newDir);
		//log.info("Removing field(s) " + Util.join(fields_to_be_removed, ", ") + " from index.");

		int count = 0;
		for (int i = 0; i < reader.numDocs(); i++) {
			org.apache.lucene.document.Document doc = reader.document(i);
			if (filter_func == null || filter_func.filter(doc))
			{
				writer.addDocument(doc);
				count++;
			}
		}

		writer.close();
		reader.close();

		log.info ("CopyDirectoryWithtDocFilter to dir:" + out_basedir + " name: " + baseDir + " time: " + (System.currentTimeMillis() - startTime) + " ms docs: " + count);
		return newDir;
	}

    //TODO: please review
	private synchronized Directory removeFieldsFromDirectory(Directory dir, String... fields_to_be_removed) throws CorruptIndexException, IOException
	{
		if (!indexHasFields(dir, fields_to_be_removed))
			return dir;

		boolean is_file_based = dir instanceof FSDirectory;

		String tmp_name = ".tmp";
		FSDirectory fsdir = null;
		if (is_file_based) {
			fsdir = (FSDirectory) dir;
			tmp_name = fsdir.getDirectory().getName() + tmp_name;
		}

		Directory newDir = copyDirectoryExcludeFields(dir, baseDir, tmp_name, fields_to_be_removed);

		if (is_file_based) {
			// delete the original dir and rename tmp
			File org_file = fsdir.getDirectory();
			File tmp_file = ((FSDirectory) newDir).getDirectory();
            FileUtils.deleteDirectory(org_file);
            boolean res = tmp_file.renameTo(org_file);
		    if(!res)
                log.warn("Rename of "+tmp_file.getName()+" failed, things may not work as expected!!");
        }
        return fsdir;
	}

	// since we may need to rebuild the index in a new directory, the analyzer needs to have been initialized apriori
	public synchronized void removeFieldsFromDirectory(String... fields_to_be_removed) throws CorruptIndexException, IOException
	{
		directory = removeFieldsFromDirectory(directory, fields_to_be_removed);
		directory_blob = removeFieldsFromDirectory(directory_blob, fields_to_be_removed);
	}

	public synchronized void copyDirectoryExcludeFields(String out_dir, String... fields_to_be_removed) throws CorruptIndexException, IOException
	{
		directory = copyDirectoryExcludeFields(directory, out_dir, INDEX_NAME_EMAILS, fields_to_be_removed);
		directory_blob = copyDirectoryExcludeFields(directory_blob, out_dir, INDEX_NAME_ATTACHMENTS, fields_to_be_removed);
	}

	public synchronized void copyDirectoryWithDocFilter(String out_dir, FilterFunctor func) throws CorruptIndexException, IOException
	{
		directory = copyDirectoryWithDocFilter(directory, out_dir, INDEX_NAME_EMAILS, func);
		directory_blob = copyDirectoryWithDocFilter(directory_blob, out_dir, INDEX_NAME_ATTACHMENTS, func);
	}

	// CAUTION: permanently change the index!
	public synchronized int removeEmailDocs(Collection<? extends edu.stanford.muse.index.Document> docs) throws IOException
	{
		if (iwriter != null) {
			throw new IOException("iwriter is not null. prepareForSerialization() should be called first.");
		}

		if (isearcher != null) {
			isearcher.getIndexReader().close();
			isearcher = null;
		}

		stats = null; // stats no longer valid

		int count = docIdToEmailDoc.size();

		IndexWriterConfig cfg = new IndexWriterConfig(LUCENE_VERSION, analyzer);
		IndexWriter writer = new IndexWriter(directory, cfg);
		//IndexWriter writer = new IndexWriter(directory, analyzer, false, new IndexWriter.MaxFieldLength(250000));
		assert (writer.numDocs() == docIdToEmailDoc.size());

		for (edu.stanford.muse.index.Document d : docs) {
			String id = d.getUniqueId();
			EmailDocument ed = docIdToEmailDoc.get(id);
			assert (d == ed);
			docIdToEmailDoc.remove(id);
			writer.deleteDocuments(new TermQuery(new Term("docId", id)));
			log.info("Removed doc " + id + " from index");
		}

		writer.commit();

		assert (writer.numDocs() == docIdToEmailDoc.size());

		writer.close();

		count -= docIdToEmailDoc.size(); // number of removed docs
		assert (count == docs.size());
		return count;
	}

	public synchronized void rollbackWrites() throws IOException
	{
		if (iwriter != null) {
			iwriter.rollback();
			iwriter = null;
		}
		if (iwriter_blob != null) {
			iwriter_blob.rollback();
			iwriter_blob = null;
		}
	}

	public Set<String> getNames(edu.stanford.muse.index.Document d, QueryType qt)
	{
		try {
			return getNamesForDocIdAsSet(d.getUniqueId(), qt);
		} catch (Exception e) {
			Util.print_exception(e, log);
			return new LinkedHashSet<String>();
		}
	}

	public org.apache.lucene.document.Document getDoc(edu.stanford.muse.index.Document d) throws IOException
	{
		return getLDoc(d.getUniqueId());
	}

	private void softAssertDocIdNotPresent(String docId) throws IOException
	{
		TermQuery q = new TermQuery(new Term("docId", docId));
		TopDocs td = isearcher.search(q, 1); // there must be only 1 doc with this id anyway
		Util.softAssert(td.totalHits == 0, "Oboy! docId: " + docId + " already present in the index, don't try to add it again!");
	}

	// look up the doc from doc id assigned to it
	private org.apache.lucene.document.Document getLDoc(String docId, Boolean attachment) throws IOException
	{
		IndexSearcher searcher = null;
		if(!attachment) {
			if (isearcher == null) {
				DirectoryReader ireader = DirectoryReader.open(directory);
				isearcher = new IndexSearcher(ireader);
			}
			searcher = isearcher;
		}
		else {
			if (isearcher_blob == null) {
				DirectoryReader ireader = DirectoryReader.open(directory_blob);
				isearcher_blob = new IndexSearcher(ireader);
			}
			searcher = isearcher_blob;
		}

		TermQuery q = new TermQuery(new Term("docId", docId));
		TopDocs td = searcher.search(q, 1); // there must be only 1 doc with this id anyway
		Util.softAssert(td.totalHits <= 1, "docId = " + docId + " is not unique. Found: "+td.totalHits+" hits!");
		ScoreDoc[] sd = td.scoreDocs;
		if (sd.length != 1)
		{
			// something went wrong... report it and ignore this doc
			Util.warnIf(true, "lookup failed for id " + docId);
			return null;
		}

		org.apache.lucene.document.Document rdoc = searcher.doc(sd[0].doc);
		return rdoc;
	}

	private org.apache.lucene.document.Document getLDocAttachment(String docId) throws IOException{
		return getLDoc(docId, true);
	}

	private org.apache.lucene.document.Document getLDoc(String docId) throws IOException{
		return getLDoc(docId, false);
	}

	//TODO: This method is not consistent in args with the next method.
	public static List<String> getAllEntitiesInDoc(org.apache.lucene.document.Document doc) {
		List<String> result = new ArrayList<String>();
		String[] types = new String[] { "person", "organization", "location" };
		for (String type : types) {
			String field = doc.get(type);
			List<String> e = Util.tokenize(field, NAMES_FIELD_DELIMITER);
			if (e != null && e.size() > 0)
				result.addAll(e);
		}
		return result;
	}

	//type should be one of strings EPER, ELOC, EORG, as set in NER.java
    //returns filtered list of all names
	public List<String> getEntitiesInDoc(edu.stanford.muse.index.Document d, String type)
	{
		org.apache.lucene.document.Document doc = null;
		try {
			doc = getDoc(d);
		} catch (IOException e) {
			log.warn("Unable to obtain document " + d.getUniqueId() + " from index");
			e.printStackTrace();
			return null;
		}
		String field = doc.get(type);
		return edu.stanford.muse.ie.Util.filterEntities(Util.tokenize(field, NAMES_FIELD_DELIMITER), type);
	}

    //@return list of all names recognised in this document
    public List<String> getAllEntitiesInDoc(edu.stanford.muse.index.Document d, String type)
    {
        org.apache.lucene.document.Document doc = null;
        try {
            doc = getDoc(d);
        } catch (IOException e) {
            log.warn("Unable to obtain document " + d.getUniqueId() + " from index");
            e.printStackTrace();
            return null;
        }
        String field = doc.get(type);
        return Util.tokenize(field, NAMES_FIELD_DELIMITER);
    }

	public String getContents(edu.stanford.muse.index.Document d, boolean originalContentOnly)
	{
		org.apache.lucene.document.Document doc = null;
		try {
			doc = getDoc(d);
		} catch (IOException e) {
			log.warn("Unable to obtain document " + d.getUniqueId() + " from index");
			e.printStackTrace();
			return null;
		}

		return getContents(doc, originalContentOnly);
	}

    public String getTitle(org.apache.lucene.document.Document doc){
        if(doc == null)
            return null;
        return doc.get("title");
    }

	public String getContents(org.apache.lucene.document.Document doc, boolean originalContentOnly) {
        String contents = null;
        try {
            if (originalContentOnly)
                contents = doc.get("body_original");
            else
                contents = doc.get("body");
        } catch (Exception e) {
            log.warn("Exception " + e + " trying to read field 'body/body_original': " + Util.ellipsize(Util.stackTrace(e), 350));
            contents = null;
        }


        if (contents == null) { // fall back to 'names' (or in public mode)
            try {
                Set<String> names = getNamesForDocAsSet(doc, originalContentOnly ? QueryType.ORIGINAL : QueryType.FULL);
                contents = Util.joinSort(Util.scrubNames(names), "\n"); // it seems <br> will get automatically added downstream
            } catch (Exception e) {
                log.warn("Exception " + e + " trying to read extracted names of " + this.toString());
                contents = null;
            }

            if (contents == null)
                contents = "\n\nContents not available.\n\n";
        }

        return contents;
    }

	/**
	 * Until we use Lucene that has built-in support for regex, we manually
	 * handle regex pattern.
	 * @return new query whose WildcardQuery's with regex pattern (according to
	 *         escapeRegex) are converted to RegexQuery's
	 */

	/*
	 * expires june 2014
	 * private Query convertRegex(Query query)
	 * {
	 * if (query instanceof BooleanQuery) {
	 * BooleanQuery bquery = (BooleanQuery) query;
	 * BooleanQuery result = new BooleanQuery();
	 * for (BooleanClause c : bquery.clauses()) {
	 * result.add(new BooleanClause(convertRegex(c.getQuery()), c.getOccur()));
	 * }
	 * return result;
	 * } else if (query instanceof WildcardQuery) {
	 * Term term = ((WildcardQuery) query).getTerm();
	 * String text = term.text();
	 * final String regex_marker = "\\*\\?"; // this regex_marker causes
	 * QueryParser to detect it as WildcardQuery
	 * if (text.startsWith(regex_marker) && text.endsWith(regex_marker)) {
	 * return new RegexQuery(new Term(term.field(),
	 * text.substring(regex_marker.length(),
	 * text.length()-regex_marker.length())));
	 * }
	 * } else if (query instanceof TermQuery) {
	 * Term term = ((TermQuery) query).getTerm();
	 * String text = term.text();
	 * if (text.startsWith("/") && text.endsWith("/") && text.length() > 2) {
	 * text = text.substring(1, text.length()-2);
	 * Term t = new Term(term.field(), text);
	 * query = new RegexQuery(t);
	 * }
	 * }
	 *
	 * return query;
	 * }
	 */

	public static void main(String args[]) throws IOException, ClassNotFoundException, ParseException, GeneralSecurityException
	{
		//		String content = "A FEDERAL JUDGE DECLINED on Monday to grant to the University of South Florida a declaratory judgment that the university's plan"
		//				+ " to fire Sami Al-Arian, a tenured professor with alleged links to terrorism, did not violate his First Amendment right to free speech";
		//		Set<String> pnames = getNamesFromPatterns(content, true);
		//		Set<String> names = getNamesFromPatterns(content, false);
		//		System.err.println("Pnames: " + pnames);
		//		System.err.println("Names: " + names);
		//System.err.println(Indexer.getNamesFromPatterns("There is a story in today's New Zealand Herald about a German and French peace-keeping force that would work as UN Peacekeepers, presumably to protect IRAQ after the US invasion.... Mark", false));
		//		EmailUtils.test("NEA and the Massachusetts Cultural Council",
		//				"Dartmouth College and Harvard Law School",
		//				"Cornell University and Boston College",
		//				"The National Park Service",
		//				"Adams President Natural Resources Defense Council",
		//				"Tougher Disguises Press",
		//				"The Chronicle of Higher Education for Tuesday",
		//				"Electronic Poetry Center",
		//				"CIA",
		//				"CNN",
		//				"FBI",
		//				"Meridian Books",
		//				"Apple",
		//				"House Press",
		//				"Microsoft");
		//		EmailUtils.test("China", "NYState", "Orlando", "Elmwood", "Mykonos", "Raliegh",
		//				"Dresden", "Northampton", "IRAQ", "New Zealand", "English in Dresden");
		testQueries();
		//		try {
		//			String aFile = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user";
		//			String type = "3classes";
		//
		//			String BASE_DIR = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user" + File.separator + "models" + File.separator;
		//			String path = BASE_DIR + File.separator + type + "_" + edu.stanford.muse.ModeConfig.SVM_MODEL_FILE;
		//			String wfsPath = BASE_DIR + File.separator + type + "_" + edu.stanford.muse.ModeConfig.WORD_FEATURES;
		//			Archive archive = SimpleSessions.readArchiveIfPresent(aFile);
		//			WordFeatures.trainCustomModel(archive, path, wfsPath);
		//		} catch (Exception e) {
		//			e.printStackTrace();
		//		}
	}

	public int numMatchesPresetQueries() {
		readPresetQueries();
		try {
			return this.luceneLookupDocs(null, QueryType.PRESET_REGEX).size();
		} catch (Exception e) {
			Util.print_exception("Minor: error while computing number of sensitive messages, the number will be set to 0", e, log);
			e.printStackTrace();
			return 0;
		}
	}

	public static void testQueries() throws IOException, ParseException, GeneralSecurityException, ClassNotFoundException
	{
		Indexer li = new Indexer("/tmp", new IndexOptions());

		// public EmailDocument(String id, String folderName, Address[] to, Address[] cc, Address[] bcc, Address[] from, String subject, String messageID, Date date)
		EmailDocument ed = new EmailDocument("1", "dummy", new Address[0], new Address[0], new Address[0], new Address[0], "", "", new Date());
		li.indexSubdoc(" ssn 123-45 6789 ", "name 1 is John Smith.  credit card # 1234 5678 9012 3456 ", ed, null);
		ed = new EmailDocument("2", "dummy", new Address[0], new Address[0], new Address[0], new Address[0], "", "", new Date());
		li.indexSubdoc(" ssn 123 45 6789", "name 1 is John Smith.  credit card not ending with a non-digit # 1234 5678 9012 345612 ", ed, null);
		ed = new EmailDocument("3", "dummy", new Address[0], new Address[0], new Address[0], new Address[0], "", "", new Date());
		li.indexSubdoc(" ssn 123 45 6789", "name 1 is John Smith.  credit card # 111234 5678 9012 3456 ", ed, null);
		ed = new EmailDocument("4", "dummy", new Address[0], new Address[0], new Address[0], new Address[0], "", "", new Date());
		li.indexSubdoc(" ssn 123 45 6789", "\nmy \nfirst \n book is \n something ", ed, null);
        ed = new EmailDocument("5", "dummy", new Address[0], new Address[0], new Address[0], new Address[0], "", "", new Date());
        li.indexSubdoc("passport number k4190893", "\nmy \nfirst \n book is \n something ", ed, null);

        li.close();

		li.setupForRead();
		String q = "john";
		Collection<EmailDocument> docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "/j..n/\\\\*";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "\"john\"";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "\"john smith\"";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "john*smith";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "title:john";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "title:subject";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "body:johns";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "title:johns";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "joh*";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		q = "/j..n/";
		//		q = "/\\b(\\d{9}|\\d{3}-\\d{2}-\\d{4})\\b/";
		docs = li.luceneLookupDocs(q, QueryType.FULL);
		System.out.println("hits for: " + q + " = " + docs.size());

		// look for sequence of 4-4-4-4 . the .* at the beginning and end is needed.
		q = "[0-9]{3}[\\- ]*[0-9]{2}[ \\-]*[0-9]{4}";
		docs = li.luceneLookupDocs(q, QueryType.REGEX);
		System.out.println("hits for: " + q + " = " + docs.size());

		// look for sequence of 3-2-4
		//q = "[0-9]{3}[ \\-]*[0-9]{2}[ \\-]*[0-9]{4}";
		q = "123-45[ \\-]*[0-9]{4}";
		System.out.println("hits for: " + q + " = " + li.docsForQuery(q, -1, QueryType.REGEX).size());

		// look for sequence of 3-2-4
		q = "first\\sbook";
		docs = li.luceneLookupDocs(q, QueryType.REGEX);
		System.out.println("hits for: " + q + " = " + docs.size());

        q = "ssn";
        int numHits = li.getTotalHits(q, false, QueryType.FULL);
        System.err.println("Number of hits for: " + q + " is " + numHits);

		Indexer.readPresetQueries();
        q = "k[0-9]{7}";
		docs = li.luceneLookupDocs(q, QueryType.REGEX);
		System.out.println("hits for: " + q + " = " + docs.size());

		li.analyzer = null;
		li.isearcher = null;
		li.parser = null;
		li.parserOriginal = null;
		li.parserSubject = null;
		li.parserCorrespondents = null;
		Util.writeObjectToFile("/tmp/1", li);
		li = (Indexer) Util.readObjectFromFile("/tmp/1");
	}
	public String toString()
	{
		return computeStats();
	}
}
