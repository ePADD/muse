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
import edu.stanford.muse.email.*;
import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.ie.NameInfo;
import edu.stanford.muse.ner.NER;
import edu.stanford.muse.util.*;
import edu.stanford.muse.webapp.ModeConfig;
import edu.stanford.muse.webapp.SimpleSessions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Field;
import org.apache.lucene.util.BytesRef;

import java.io.*;
import java.util.*;

/**
 * Core data structure that represents an archive. Conceptually, an archive is a
 * collection of indexed messages (which can be incrementally updated), along
 * with a blob store. It also has addressbooks, group assigner etc, which are
 * second order properties -- they may be updated independently of the docs (in
 * the future). allDocs is the indexed docs, NOT the ones in the current
 * filter... need to work this out. An archive should be capable of being loaded
 * up in multiple sessions simultaneously. one problem currently is that
 * summarizer is stored in indexer -- however, we should pull it out into
 * per-session state.
 *
 */
public class Archive implements Serializable {

    public static String[] LEXICONS =  new String[]{"default.english.lex.txt"}; // this is the default, for Muse. EpaddIntializer will set it differently
    /**
     * Recognises names in the supplied text with OpenNLP NER
     * @Deprecated
     */
    public static Set<String> extractNames(String text) throws Exception {
        List<Pair<String, Float>> pairs = edu.stanford.muse.index.NER.namesFromText(text);
        Set<String> names = new LinkedHashSet<String>();
        for (Pair<String, ?> p : pairs)
            names.add(p.getFirst());

        return Util.scrubNames(names);
    }

    protected static void readPresetQueries() {
        List<String> q = new ArrayList<>();
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
                    Util.print_exception("Exception trying to copy embedded preset queries file: " + PRESET_QUERIES_FILE, e, Indexer.log);
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
            Indexer.presetQueries = q.toArray(new String[q.size()]);
            log.info("Initiated " + q.size() + " preset queries");
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
            Util.print_exception("Exception while reading pre-set queries file: " + path, e, Indexer.log);
        }
    }

    /**@return the preset queries read from presetqueries.txt*/
    public static String[] getPresetQueries(){
        return Indexer.presetQueries;
    }

    /**
     * @return all the links extracted from the archive content*/
    //This is a better location for this than Indexer, I (@vihari) think
    public List<LinkInfo> getLinks() {
        return indexer.links;
    }

    public Set<Blob> blobsForQuery(String term){return indexer.blobsForQuery(term);}

    public Collection<edu.stanford.muse.index.Document> docsForQuery(String term, int cluster, int threshold, Indexer.QueryType qt){
        Indexer.QueryOptions options = new Indexer.QueryOptions();
        options.setQueryType(qt);
        options.setCluster(cluster);
        options.setThreshold(threshold);
        return indexer.docsForQuery(term, options);
    }

    public Collection<edu.stanford.muse.index.Document> docsForQuery(String term, int cluster, int threshold) {
        Indexer.QueryOptions options = new Indexer.QueryOptions();
        options.setCluster(cluster);
        options.setThreshold(threshold);
        return indexer.docsForQuery(term, options);
    }

    public Collection<edu.stanford.muse.index.Document> docsForQuery(String term, int cluster, Indexer.QueryType qt) {
        Indexer.QueryOptions options = new Indexer.QueryOptions();
        options.setCluster(cluster);
        return indexer.docsForQuery(term, options);
    }

    //query term can be ommitted if the querytype is PRESET_REGEX
    public Collection<Document> docsForQuery(int cluster, Indexer.QueryType qt) {
        Indexer.QueryOptions options = new Indexer.QueryOptions();
        options.setCluster(cluster);
        options.setQueryType(qt);
        return indexer.docsForQuery(null, options);
    }

    public Collection<EmailDocument> convertToED(Collection<Document> docs) {
        return indexer.convertToED(docs);
    }

    public Collection<edu.stanford.muse.index.Document> docsForQuery(String term, Indexer.QueryType qt) {
        Indexer.QueryOptions options = new Indexer.QueryOptions();
        options.setQueryType(qt);
        return indexer.docsForQuery(term, options);
    }

    public Collection<Document> docsForQuery(String term, Indexer.QueryOptions options){
        return indexer.docsForQuery(term, options);
    }


    /**
     * @param q - query
     * @param qt - query type
     * @return number of hits for the query*/
    public int countHitsForQuery(String q, Indexer.QueryType qt) {
        return indexer.countHitsForQuery(q, qt);
    }

    public int countHitsForQuery(String q) {
        return indexer.countHitsForQuery(q, Indexer.QueryType.FULL);
    }

    public String getLoadedDirectoryInfo(){
        return indexer.directory.toString();
    }

    public Pair<String,String> getContentsOfAttachment(String fileName){
        return indexer.getContentsOfAttachment(fileName);
    }

    public EmailDocument docForId(String id){ return indexer.docForId(id);}

    public static class Entity {
        public Map<String, Short> ids;
        //person,places,orgs, custom
        public String name;
        Set<String> types = new HashSet<String>();

        public Entity(String name, Map<String, Short> ids, Set<String> types) {
            this.name = name;
            this.ids = ids;
            this.types = types;
        }

        @Override
        public String toString() {
            return types.toString();
        }
    }

    public String getTitle(org.apache.lucene.document.Document doc){
        return indexer.getTitle(doc);
    }

    public Indexer.IndexStats getIndexStats(){
        return indexer.stats;
    }

    private static Log log = LogFactory.getLog(Archive.class);
    private final static long serialVersionUID = 1L;

    public static final String BLOBS_SUBDIR = "blobs";
    public static final String IMAGES_SUBDIR = "images";
    public static final String INDEXES_SUBDIR = "indexes";
    public static final String SESSIONS_SUBDIR = "sessions";                        // original idea was that there would be different sessions on the same archive (index). but in practice we only have one session
    public static final String LEXICONS_SUBDIR = "lexicons";
    public static final String FEATURES_SUBDIR = "features";

    // these fields are used in the library setting
    static public class ProcessingMetadata implements java.io.Serializable {
        private final static long serialVersionUID = 6304656466358754945L; // compatibility
        public String institution, repository, collectionTitle, collectionID, accessionID, findingAidLink, catalogRecordLink, contactEmail, rights, notes;
        public long timestamp;
        public TimeZone tz;
        public int nDocs, nIncomingMessages, nOutgoingMessages, nHackyDates; // note a message can be both incoming and outgoing.
        public int nBlobs, nUniqueBlobs, nImageBlobs, nDocBlobs, nOtherBlobs; // this is just a cache so we don't have to read the archive
        public String ownerName, about;
        //will be set by method that computes epadd-ner
        public Map<Short, Integer> entityCounts;
        public int numPotentiallySensitiveMessages = -1;
        public Date firstDate, lastDate;
        public int numLexicons = -1;

        private static String mergeField(String a, String b) {
            if (a == null)
                return b;
            if (b == null)
                return a;
            if (a.equals(b))
                return a;
            else
                return a + "+" + b;
        }

        public void merge(ProcessingMetadata other) {
            mergeField(this.institution, other.institution);
            mergeField(this.repository, other.repository);
            mergeField(this.collectionTitle, other.collectionTitle);
            mergeField(this.collectionID, other.collectionID);
            mergeField(this.accessionID, other.accessionID);
            mergeField(this.findingAidLink, other.findingAidLink);
            mergeField(this.catalogRecordLink, other.catalogRecordLink);
            mergeField(this.contactEmail, other.contactEmail);
            mergeField(this.rights, other.rights);
            mergeField(this.notes, other.notes);
            // mergeField(this.tz, other.tz);
        }
    }

    /**
     * all of these things don't change based on the current filter
     */
    public Indexer indexer;
    private IndexOptions indexOptions;
    public BlobStore blobStore;
    public AddressBook addressBook;
    public GroupAssigner groupAssigner;
    transient private Map<String, Lexicon> lexiconMap = null;
    private List<Document> allDocs;                                                    // this is the equivalent of fullEmailDocs earlier
    transient private Set<Document> allDocsAsSet = null;
    private Set<FolderInfo> fetchedFolderInfos = new LinkedHashSet<FolderInfo>();    // keep this private since its updated in a controlled way
    transient private LinkedHashMap<String, FolderInfo> fetchedFolderInfosMap = null;
    public Set<String> ownerNames = new LinkedHashSet<String>(), ownerEmailAddrs = new LinkedHashSet<String>();
    Map<String, NameInfo> nameMap;

    public ProcessingMetadata processingMetadata = new ProcessingMetadata();
    public List<String> allAccessions = new ArrayList<String>();
    public List<FetchStats> allStats = new ArrayList<FetchStats>(); // multiple stats because usually there is 1 per import

    public String archiveTitle; // this is the name of this archive

	/*
	 * baseDir is used loosely... it may not be fully reliable, e.g. when the
	 * archive moves.
	 */
    public String baseDir;

    /**
     * set the base dir of the archive, this is the place where all the archive cache is dumped
     * */
    public void setBaseDir(String dir) {
        baseDir = dir;
        blobStore.setDir(dir + File.separator + BLOBS_SUBDIR);
    }

    /**
     * Internal, please do not use!
     * */
    //is being used in types.jsp -> Can we get rid of types.jsp or this call?
    public void setNameMap(Map<String, NameInfo> nameMap) {
        this.nameMap = nameMap;
    }

    public class SentimentStats implements Serializable { // this is a placeholder
        // right now.. its
        // essentially storing
        // archive cluer's stats
        private final static long serialVersionUID = 1L;
        public Map<String, Integer> sentimentCounts;
    }

    public SentimentStats stats = new SentimentStats();

    transient private List<Map.Entry<String, Integer>> topNames = null;

    // clusters are somewhat ephemeral and not necessarily a core part of the
    // Archive struct. consider moving it elsewhere.
    List<MultiDoc> docClusters;

    protected void setBlobStore(BlobStore blobStore) {
        this.blobStore = blobStore;
    }

    //TODO: this should not be public, being used in doSimpleFlow. At least put some some documentation
    public void setGroupAssigner(GroupAssigner groupAssigner) {
        this.groupAssigner = groupAssigner;
    }
    //TODO: this should not be public, being used in doSimpleFlow.
    public void setAddressBook(AddressBook ab) {
        addressBook = ab;
    }

    public BlobStore getBlobStore() {
        return blobStore;
    }

    public AddressBook getAddressBook() {
        return addressBook;
    }

    /** private constructor -- always use createArchive() instead */
    private Archive() { }

    public static Archive createArchive() { return createArchive (""); }

    public static Archive createArchive(String title) {
        Archive archive = new Archive();
        archive.archiveTitle = title;
        return archive;
    }

    public synchronized void openForRead() {
        log.info("Opening archive read only");
        indexer.setupForRead();
    }

    public synchronized void openForWrite() throws IOException {
        log.info("Opening archive for write");

        indexer.setupForWrite();
        if (allDocs != null) {
            // we already have some docs in the index, verify it to make
            // sure the archive's idea of #docs is the same as the index's.
            int docsInIndex = indexer.nDocsInIndex();
            log.info(docsInIndex + " doc(s) in index, " + allDocs.size() + " doc(s) in archive");
            Util.warnIf(indexer.nDocsInIndex() != allDocs.size(),
                    "Warning: archive nDocsInIndex is not the same as Archive alldocs (possible if docs have been deleted?)", log);
        }
    }

    public synchronized void close() {
        log.info("Closing archive");
        if (indexer != null)
            indexer.close();
        try {
            if (blobStore != null)
                blobStore.pack(); // ideally, do this only if its dirty
        } catch (Exception e) {
            Util.print_exception(e, log);
        }
    }

    // create a new/empty archive.
    // baseDir is for specifying base location of Indexer's file-based
    // directories
    /**
     * Setup an archive
     * @param baseDir - base dir of the archive
     * @param blobStore - attchmane blob store
     * @param args - options for loading @see{edu.stanford.muse.webapp.JSPHelper.preparedArchive}, set to nul to empty array for defaults
     * */
    public void setup(String baseDir, BlobStore blobStore, String args[]) throws IOException {
        prepareBaseDir(baseDir);
        lexiconMap = createLexiconMap(baseDir);
        indexOptions = new IndexOptions();
        indexOptions.parseArgs(args);
        log.info("Index options are: " + indexOptions);
        indexer = new Indexer(baseDir, indexOptions);
        if(blobStore!=null)
        setBlobStore(blobStore);
    }

    /**
     * clear all fields, use when indexer needs to be completely cleared
     */
    public void clear() {
        if (indexer != null)
            indexer.clear();
        if (allDocs != null)
            allDocs.clear();
        if (allDocsAsSet != null)
            allDocsAsSet.clear();
        groupAssigner = null;
        ownerEmailAddrs.clear();
        ownerNames.clear();
        addressBook = null;
    }

    /*
	 * should happen rarely, only while exporting session. fragile operation,
	 * make sure blobStore etc are updated consistently
	 */
    public void setAllDocs(List<Document> docs) {
        log.info("Updating archive's alldocs to new list of " + docs.size() + " docs");
        allDocs = docs;
        allDocsAsSet = null;
    }

    public NameInfo nameLookup(String name) {
        String ctitle = name.toLowerCase().replaceAll(" ", "_");
        if (nameMap != null)
            return nameMap.get(ctitle);
        else
            return null;
    }

    public void addOwnerName(String name) {
        ownerNames.add(name);
        processingMetadata.ownerName = name;
    }

    public void addOwnerEmailAddrs(Collection<String> emailAddrs) {
        ownerEmailAddrs.addAll(emailAddrs);
    }

    public void addOwnerEmailAddr(String emailAddr) {
        ownerEmailAddrs.add(emailAddr);
    }

    /**
     * This should be the only place that creates the cache dir.
     */
    public static void prepareBaseDir(String dir) {
        dir = dir + File.separatorChar + LEXICONS_SUBDIR;
        File f_dir = new File(dir);

        f_dir.mkdirs();

        // copy lexicons over to the muse dir
        // unfortunately, hard-coded because we are loading as a ClassLoader resource and not as a file, so we can't use Util.filesWithSuffix()
        // we have a different set of lexicons for epadd and muse which will be set up in LEXICONS by the time we reach here
        log.info("copying " + LEXICONS.length + " lexicons to " + dir);
        for (String l : LEXICONS) {
            try {

                if (new File(dir+File.separator + l).exists()) {
                    log.info ("Skipping lexicon " + l + " because it already exists");
                    continue;
                }

                InputStream is = EmailUtils.class.getClassLoader().getResourceAsStream("lexicon/" + l);
                if (is == null) {
                    log.warn("lexicon lexicon/" + l + " not found");
                    continue;
                }

                log.info("copying " + l + " to " + dir);
                Util.copy_stream_to_file(is, dir + File.separator + l);
            } catch (Exception e) {
                Util.print_exception(e, log);
            }
        }
    }

    public static void clearCache(String baseDir, String rootDir) {
        log.info("Clearing archive with baseDir: " + baseDir + " rootDir: " + rootDir);
        if (!Util.nullOrEmpty(baseDir)) {
            // delete only indexes, blobs, sessions
            // keep sentiment stuff around
            Util.deleteDir(baseDir);
			/*
			Util.deleteDir(baseDir + File.separatorChar + INDEXES_SUBDIR);
			Util.deleteDir(baseDir + File.separatorChar + SESSIONS_SUBDIR); // could
			Util.deleteDir(baseDir + File.separatorChar + LEXICONS_SUBDIR); // could
			Util.deleteDir(baseDir + File.separatorChar + MODELS_SUBDIR); // could
																			// also
																			// call
																			// sessions.deleteallsessions,
																			// but
																			// lazy...
			*/
            // prepare cache dir anew
            prepareBaseDir(baseDir);
        }

        // rootdir is used only for webapp/<user> (piclens etc) we'll get rid of
        // it in future
        if (!Util.nullOrEmpty(rootDir)) {
            Util.deleteDir(rootDir);
            new File(rootDir + File.separator).mkdirs();
        }
    }

    /**
     * returns the final, sorted, deduped version of allDocs that this driver
     * worked on in its last run
     */
    public List<Document> getAllDocs() {
        if (allDocs == null) {
            synchronized (this) {
                if (allDocs == null) {
                    allDocs = new ArrayList<Document>();
                    allDocsAsSet = new LinkedHashSet<Document>();
                }
            }
        }
        return allDocs;
    }

    public Set<Document> getAllDocsAsSet() {
        // allDocsAsSet is lazily computed
        if (allDocsAsSet == null) {
            synchronized (this) {
                if (allDocsAsSet == null) {
                    allDocsAsSet = new LinkedHashSet<Document>(getAllDocs());
                    Util.softAssert(allDocs.size() == allDocsAsSet.size());
                }
            }
        }
        return allDocsAsSet;
    }

    public int nDocsInCluster(int i) {
        if (i < 0 || i >= docClusters.size())
            return -1;
        return docClusters.get(i).getDocs().size();
    }

    public int nClusters() {
        return docClusters.size();
    }

    // work in progress - status provider
    public StatusProvider getStatusProvider() {
        return indexer;
    }

    public Map<String, Collection<Document>> getSentimentMap(Lexicon lex, boolean originalContentOnly, String... captions) {
        if (lex == null) {
            log.warn ("Warning: lexicon is null!");
            return new LinkedHashMap<>();
        }
        return lex.getEmotions(indexer, getAllDocsAsSet(), false /* doNota */, originalContentOnly, captions);
    }

    /**
     * gets original content only!
     */
    public String getContents(Document d, boolean originalContentOnly) {
        return indexer.getContents(d, originalContentOnly);
    }

    public String getContents(org.apache.lucene.document.Document ldoc, boolean originalContentOnly){
        return indexer.getContents(ldoc, originalContentOnly);
    }

    private void setupAddressBook(List<Document> docs) {
        // in this case, we don't care whether email addrs are incoming or
        // outgoing,
        // so the ownAddrs can be just a null string
        if (addressBook == null)
            addressBook = new AddressBook(new String[0], new String[0]);
        log.info("Setting up address book for " + docs.size() + " messages (indexing driver)");
        for (Document d : docs)
            if (d instanceof EmailDocument)
                addressBook.processContactsFromMessage((EmailDocument) d);

        addressBook.organizeContacts();
    }

    public List<LinkInfo> extractLinks(Collection<Document> docs) throws Exception {
        prepareAllDocs(docs, indexOptions);
        indexer.clear();
        indexer.extractLinks(docs);
        return EmailUtils.getLinksForDocs(docs);
    }

    public Collection<DatedDocument> docsInDateRange(Date start, Date end) {
        List<DatedDocument> result = new ArrayList<DatedDocument>();
        if (Util.nullOrEmpty(allDocs))
            return result;

        for (Document d : allDocs) {
            try {
                DatedDocument dd = (DatedDocument) d;
                if ((dd.date.after(start) && dd.date.before(end)) || dd.date.equals(start) || dd.date.equals(end))
                    result.add(dd);
            } catch (Exception e) {
                Util.print_exception(e, log);
            }
        }
        return result;
    }

    public boolean containsDoc(Document doc) {
        return getAllDocsAsSet().contains(doc);
    }

    /**
     * use with caution. pseudo-adds a doc to the archive, but without any
     * subject and without any contents. useful only when doing quick screening
     * to check of emails for memory tests, etc.
     */
    public synchronized boolean addDocWithoutContents(Document doc) {
        if (containsDoc(doc))
            return false;

        getAllDocsAsSet().add(doc);
        getAllDocs().add(doc);

        String subject = "", contents = "";

        indexer.indexSubdoc(subject, contents, doc, blobStore);

        if (getAllDocs().size() % 100 == 0)
            log.info("Memory status after " + getAllDocs().size() + " emails: " + Util.getMemoryStats());

        return true;
    }

    /**
     * core method, adds a single doc to the archive. remember to call
     * postProcess at the end of any series of calls to add docs
     */
    public synchronized boolean addDoc(Document doc, String contents) {
        if (containsDoc(doc))
            return false;

        getAllDocsAsSet().add(doc);
        getAllDocs().add(doc);

        String subject = doc.getSubjectWithoutTitle();
        subject = EmailUtils.cleanupSubjectLine(subject);

        indexer.indexSubdoc(subject, contents, doc, blobStore);

        if (getAllDocs().size() % 100 == 0)
            log.info("Memory status after " + getAllDocs().size() + " emails: " + Util.getMemoryStats());

        return true;
    }

    /**
     * prepares all docs for indexing, incl. applying filters, removing dups and
     * sorting
     *
     * @throws Exception
     */
    private void prepareAllDocs(Collection<Document> docs, IndexOptions io) throws Exception {
        allDocs = new ArrayList<Document>();
        allDocs.addAll(docs);
        allDocs = EmailUtils.removeDupsAndSort(allDocs);
        log.info(allDocs.size() + " documents after removing duplicates");

        if (addressBook == null && !io.noRecipients) {
            log.warn("no address book previously set up!");
            setupAddressBook(allDocs); // set up without the benefit of ownaddrs
        }

        if (io.filter != null && addressBook != null) {
            Contact ownCI = addressBook.getContactForSelf(); // may return null
            // if we don't
            // have own info
            io.filter.setOwnContactInfo(ownCI);
        }

        // if no filter, accept doc (default)
        List<Document> newAllDocs = new ArrayList<Document>();
        for (Document d : allDocs)
            if (io.filter == null || (io.filter != null && io.filter.matches(d)))
                newAllDocs.add(d);

        EmailUtils.cleanDates(newAllDocs);

        log.info(newAllDocs.size() + " documents after filtering");

        allDocs = newAllDocs;
        Collections.sort(allDocs); // may not be essential
        allDocsAsSet = null;
    }

    /**
     * set up doc clusters by group or by time
     */
    public void prepareDocClusters(List<SimilarGroup<String>> groups) {
        /** by default, we only use month based clusters right now */
        if (indexOptions.categoryBased) {
            docClusters = IndexUtils.partitionDocsByCategory(allDocs);
        } else {
            if (groups != null) {
                Map<String, Set<EmailDocument>> groupsToDocsMap = IndexUtils.partitionDocsByGroup((Collection) allDocs, groups, addressBook, true);
                int i = 0;
                for (String groupName : groupsToDocsMap.keySet()) {
                    MultiDoc md = new MultiDoc(Integer.toString(i++), groupName);
                    docClusters.add(md);
                    for (EmailDocument d : groupsToDocsMap.get(groupName))
                        md.add(d);
                }
            } else
                docClusters = IndexUtils.partitionDocsByInterval((List) allDocs, indexOptions.monthsNotYears);
        }

        log.info(docClusters.size() + " clusters of documents");

        // outputPrefix = io.outputPrefix;
        log.info(allDocs.size() + " documents in " + docClusters.size() + " time clusters, " + indexer.nonEmptyTimeClusterMap.size() + " non-empty");
    }

    private String getFolderInfosMapKey(String accountKey, String longName) {
        return accountKey + "..." + longName;
    }

    private void setupFolderInfosMap() {
        if (fetchedFolderInfosMap == null)
            fetchedFolderInfosMap = new LinkedHashMap<String, FolderInfo>();
        for (FolderInfo fi : fetchedFolderInfos) {
            fetchedFolderInfosMap.put(getFolderInfosMapKey(fi.accountKey, fi.longName), fi);
        }
    }

    /**
     * adds a collection of folderinfo's to the archive, updating existing ones
     * as needed
     */
    public void addFetchedFolderInfos(Collection<FolderInfo> fis) {
        // if a folderinfo with the same accountKey and longname already exists,
        // its lastSeenUID may need to be updated.

        // first organize a key -> folder info map in case we have a large # of
        // folders
        setupFolderInfosMap();

        for (FolderInfo fi : fis) {
            String key = getFolderInfosMapKey(fi.accountKey, fi.longName);
            FolderInfo existing_fi = fetchedFolderInfosMap.get(key);
            if (existing_fi != null) {
                if (existing_fi.lastSeenUID < fi.lastSeenUID)
                    existing_fi.lastSeenUID = fi.lastSeenUID;
            } else {
                fetchedFolderInfos.add(fi);
                fetchedFolderInfosMap.put(key, fi);
            }
        }
    }

    public FolderInfo getFetchedFolderInfo(String accountID, String fullFolderName) {
        setupFolderInfosMap();
        return fetchedFolderInfosMap.get(getFolderInfosMapKey(accountID, fullFolderName));
    }

    /**
     * returns last seen UID for the specified folder, -1 if its not been seen
     * before
     */
    public long getLastUIDForFolder(String accountID, String fullFolderName) {
        FolderInfo existing_fi = getFetchedFolderInfo(accountID, fullFolderName);
        if (existing_fi != null)
            return existing_fi.lastSeenUID;
        else {
            return -1L;
        }
    }

    /***/
    public List<LinkInfo> postProcess() {
        return postProcess(allDocs, null);
    }

    /**
     * should be called at the end of a series of calls to add doc to the
     * archive. returns links. splits by groups if not null, otherwise by time.
     *
     * @throws Exception
     */
    //does not make sense to have it public.
    public synchronized List<LinkInfo> postProcess(Collection<Document> docs, List<SimilarGroup<String>> groups) {
        // should we sort the messages by time here?

        log.info(indexer.computeStats());
        log.info(getLinks().size() + " links");
        // prepareAllDocs(docs, io);
        prepareDocClusters(groups);
        // TODO: should we recomputeCards? call nukeCards for now to invalidate
        // cards since archive may have been modified.
        indexer.summarizer.nukeCards();

        List<LinkInfo> links = getLinks();
        return links;
    }

    // replace subject with extracted names
    private static void replaceDescriptionWithNames(Collection<? extends Document> allDocs, Archive archive) throws Exception {
        for (Document d : allDocs) {
            if (!Util.nullOrEmpty(d.description)) {
                //log.info("Replacing description for docId = " + d.getUniqueId());
                // List<String> names =
                // Indexer.extractNames(d.description);
                // Collections.sort(names);
                // d.description = Util.join(names,
                // Indexer.NAMES_FIELD_DELIMITER);
                d.description = edu.stanford.muse.ner.NER.retainOnlyNames(d.description, archive.getDoc(d));
            }
        }
    }

    /**
     * export archive with just the given docs to prepare for public mode.
     * docsToExport should be a subset of what's already in the archive. returns
     * true if successful.
     */
	/*
	 * public boolean trimArchive(Collection<EmailDocument> docsToRetain) throws
	 * Exception { if (docsToRetain == null) return true; // return without
	 * doing anything
	 * 
	 * // exports messages in current filter (allEmailDocs) //HttpSession
	 * session = request.getSession(); Collection<Document> fullEmailDocs =
	 * this.getAllDocs(); Indexer indexer = sthis.indexer;
	 * 
	 * // compute which docs to remove vs. keep Set<Document> docsToKeep = new
	 * LinkedHashSet<Document>(docsToRetain); Set<Document> docsToRemove = new
	 * LinkedHashSet<Document>(); for (Document d: fullEmailDocs) if
	 * (!docsToKeep.contains(d)) docsToRemove.add(d);
	 * 
	 * // remove unneeded docs from the index
	 * indexer.removeEmailDocs(docsToRemove); // CAUTION: permanently change the
	 * index! this.setAllDocs(new ArrayList<Document>(docsToRetain)); return
	 * true; }
	 */

    /**
     * a fresh archive is created under out_dir. name is the name of the session
     * under it. blobs are exported into this archive dir. destructive! but
     * should be so only in memory. original files on disk should be unmodified.
     *
     * @param retainedDocs
     * @throws Exception
     */
    public synchronized String export(Collection<? extends Document> retainedDocs, final boolean exportInPublicMode, String out_dir, String name) throws Exception {
        if (Util.nullOrEmpty(out_dir))
            return null;
        File dir = new File(out_dir);
        if (dir.exists() && dir.isDirectory())
            log.warn("Overwriting existing directory '" + out_dir + "' (it may already exist)");
        else if (!dir.mkdirs()) {
            log.warn("Unable to create directory: " + out_dir);
            return null;
        }
        Archive.prepareBaseDir(out_dir);
        if (!exportInPublicMode && new File(baseDir + File.separator + LEXICONS_SUBDIR).exists())
            FileUtils.copyDirectory(new File(baseDir + File.separator + LEXICONS_SUBDIR), new File(out_dir + File.separator + LEXICONS_SUBDIR));
        if (new File(baseDir + File.separator + IMAGES_SUBDIR).exists())
            FileUtils.copyDirectory(new File(baseDir + File.separator + IMAGES_SUBDIR), new File(out_dir + File.separator + IMAGES_SUBDIR));
        //internal disambiguation cache
        if (new File(baseDir + File.separator + FEATURES_SUBDIR).exists())
            FileUtils.copyDirectory(new File(baseDir + File.separator + FEATURES_SUBDIR), new File(out_dir + File.separator + FEATURES_SUBDIR));
        if (new File(baseDir + File.separator + edu.stanford.muse.Config.AUTHORITY_ASSIGNER_FILENAME).exists())
            FileUtils.copyFile(new File(baseDir + File.separator + edu.stanford.muse.Config.AUTHORITY_ASSIGNER_FILENAME), new File(out_dir + File.separator + edu.stanford.muse.Config.AUTHORITY_ASSIGNER_FILENAME));

        // save the states that may get modified
        List<Document> savedAllDocs = allDocs;

        allDocs = new ArrayList<>(retainedDocs);
        if (exportInPublicMode)
            replaceDescriptionWithNames(allDocs, this);

        // copy index and if for public mode, also redact body and remove title
        // fields
        final boolean redact_body_instead_of_remove = true;
        Set<String> docIdSet = new LinkedHashSet<String>();
        for (Document d : allDocs)
            docIdSet.add(d.getUniqueId());
        final Set<String> retainedDocIds = docIdSet;
        Indexer.FilterFunctor emailFilter = new Indexer.FilterFunctor() {
            @Override
            public boolean filter(org.apache.lucene.document.Document doc) {
                if (!retainedDocIds.contains(doc.get("docId")))
                    return false;

                if (exportInPublicMode) {
                    String text = null;
                    if (redact_body_instead_of_remove) {
                        text = doc.get("body");
                    }
                    doc.removeFields("body");
                    doc.removeFields("body_original");

                    if (text != null) {
                        String redacted_text = edu.stanford.muse.ner.NER.retainOnlyNames(text, doc);
                        doc.add(new Field("body", redacted_text, Indexer.full_ft));
                        //this uses standard analyzer, not stemming because redacted bodys only have names.
                    }
                    String title = doc.get("title");
                    doc.removeFields("title");
                    if (title != null) {
                        String redacted_title = edu.stanford.muse.ner.NER.retainOnlyNames(text, doc);
                        doc.add(new Field("title", redacted_title, Indexer.full_ft));
                    }
                }
                return true;
            }
        };

        Indexer.FilterFunctor attachmentFilter = new Indexer.FilterFunctor() {
            @Override
            public boolean filter(org.apache.lucene.document.Document doc) {
                if(exportInPublicMode){
                    return false;
                }
                String docId = doc.get("emailDocId");
                if(docId == null){
                    Integer di = Integer.parseInt(doc.get("docId"));
                    //don't want to print too many messages
                    if(di==null || di<10)
                        log.error("Looks like this is an old archive, filtering all the attachments!!\n" +
                                "Consider re-indexing with the latest version for a proper export.");
                    return false;
                }
                return retainedDocIds.contains(docId);
            }
        };
        if (exportInPublicMode) {
            List<Document> docs = this.getAllDocs();
            List<EmailDocument> eds = new ArrayList<EmailDocument>();
            for (Document doc : docs)
                eds.add((EmailDocument) doc);
            EmailUtils.maskEmailDomain(eds, this.addressBook);
        }

        indexer.copyDirectoryWithDocFilter(out_dir, emailFilter, attachmentFilter);
        log.info("Completed exporting indexes");

        // save the blobs in a new blobstore
        if (!exportInPublicMode) {
            log.info("Starting to export blobs, old blob store is: " + blobStore);
            Set<Blob> blobsToKeep = new LinkedHashSet<Blob>();
            for (Document d : allDocs)
                if (d instanceof EmailDocument)
                    if (!Util.nullOrEmpty(((EmailDocument) d).attachments))
                        blobsToKeep.addAll(((EmailDocument) d).attachments);
            String blobsDir = out_dir + File.separatorChar + BLOBS_SUBDIR;
            new File(blobsDir).mkdirs();
            BlobStore newBlobStore = blobStore.createCopy(blobsDir, blobsToKeep);
            log.info("Completed exporting blobs, newBlobStore in dir: " + blobsDir + " is: " + newBlobStore);
            // switch to the new blob store (important -- the urls and indexes in the new blob store are different from the old one! */
            blobStore = newBlobStore;
        }

        // write out the archive file
        SimpleSessions.saveArchive(out_dir, name, this); // save .session file.
        log.info("Completed saving archive object");

        // restore states
        allDocs = savedAllDocs;

        return out_dir;
    }

    public List<Document> docsWithThreadId(long threadID) {
        List<Document> result = new ArrayList<Document>();
        for (Document ed : allDocs) {
            if (((EmailDocument) ed).threadID == threadID)
                result.add(ed);
        }
        return result;
    }

    public String getStats() {
        // note: this is a legacy method that does not use the archivestats
        // object above
        StringBuilder sb = new StringBuilder(allDocs.size() + " original docs with " + ownerEmailAddrs.size() + " email addresses " + ownerNames.size()
                + " names for owner ");
        if (addressBook != null)
            sb.append(addressBook.getStats() + "\n");
        sb.append(indexer.computeStats() + "\n" + getLinks().size() + " links");
        return sb.toString();
    }

    /**
     * @return html for the given terms, with terms highlighted by the
     * indexer.
     * if IA_links is set, points links to the Internet archive's
     * version of the page.
     * docId is used to initialize a new view created by clicking on a link within this message,
     * date is used to create the link to the IA
     * @args ldoc - lucene doc corresponding to the content
     * s - content of the doc
     * Date
     * docId - Uniquedocid of the emaildocument
     * sensitive - if set, will highlight any sensitive info in the mails
     * that matches one of the regexs specified in presetregexps
     * highlighttermsUnstemmed - terms to highlight in the content (for ex
     * lexicons)
     * highlighttermsstemmed - entities to highlight, generally are names
     * that one doesn't wish to be stemmed.
     * entitiesWithId - authorisedauthorities, for annotation
     * showDebugInfo - enabler to show debug info
     */
    public String annotate(org.apache.lucene.document.Document ldoc, String s, Date date, String docId, Boolean sensitive, Set<String> highlightTermsStemmed, Set<String> highlightTermsUnstemmed,
                           Map<String, Entity> entitiesWithId, boolean IA_links, boolean showDebugInfo) {
        getAllDocs();
        try {
            Summarizer summarizer = new Summarizer(indexer);

            s = Highlighter.getHTMLAnnotatedDocumentContents(s, (IA_links ? date : null), docId, sensitive, highlightTermsStemmed, highlightTermsUnstemmed,
                    entitiesWithId, null, summarizer.importantTermsCanonical /* unstemmed because we are only using names*/,
                    showDebugInfo);

            //indexer
            //	.getHTMLAnnotatedDocumentContents(s, (IA_links ? date : null), docId, searchTerms, isRegexSearch, highlightTermsStemmed, highlightTermsUnstemmed, entitiesWithId);
        } catch (Exception e) {
            e.printStackTrace();
            log.warn("indexer failed to annotate doc contents " + Util.stackTrace(e));
        }

        return s;
    }

    public String annotate(String s, Date date, String docId, Boolean sensitive, Set<String> highlightTermsStemmed, Set<String> highlightTermsUnstemmed,
                           Map<String, Entity> entitiesWithId, boolean IA_links, boolean showDebugInfo) {
        return annotate(null, s, date, docId, sensitive, highlightTermsStemmed, highlightTermsUnstemmed,
                entitiesWithId, IA_links, showDebugInfo);
    }

    public Pair<StringBuilder, Boolean> getHTMLForContents(Document d, Date date, String docId, Boolean sensitive, Set<String> highlightTermsStemmed,
                                                           Set<String> highlightTermsUnstemmed, Map<String, Map<String, Short>> authorisedEntities, boolean IA_links, boolean inFull, boolean showDebugInfo) throws Exception {
        String type = "person", otype = "organization", ptype = "location";
        //not using filtered entities here as it looks weird especially in the redaction mode not to
        // have a word not masked annotated. It is counter-intuitive.
        List<String> cpeople = getEntitiesInDoc(d, NER.EPER, true);
        List<String> corgs = getEntitiesInDoc(d, NER.EORG, true);
        List<String> cplaces = getEntitiesInDoc(d, NER.ELOC, true);
        Set<String> acrs = Util.getAcronyms(indexer.getContents(d, false));

        List<String> e = getEntitiesInDoc(d, type, true);
        List<String> orgs = getEntitiesInDoc(d, otype, true);
        List<String> places = getEntitiesInDoc(d, ptype, true);
        String contents = indexer.getContents(d, false);
        org.apache.lucene.document.Document ldoc = indexer.getDoc(d);
        if (ldoc == null)
            System.err.println("Lucene Doc is null for: " + d.getUniqueId() + " but the content is " + (contents == null ? "null" : "not null"));

        List<String> entities = new ArrayList<String>();

        if (cpeople == null)
            cpeople = new ArrayList<>();
        if (cplaces == null)
            cplaces = new ArrayList<>();
        if (corgs == null)
            corgs = new ArrayList<>();
        if (e == null)
            e = new ArrayList<>();
        if (orgs == null)
            orgs = new ArrayList<>();
        if (places == null)
            places = new ArrayList<>();
        if (acrs == null)
            acrs = new LinkedHashSet<>();

        entities.addAll(cpeople);
        entities.addAll(cplaces);
        entities.addAll(corgs);
        entities.addAll(e);
        entities.addAll(orgs);
        entities.addAll(places);
        entities.addAll(acrs);

        // Contains all entities and id if it is authorised else null
        Map<String, Entity> entitiesWithId = new HashMap<String, Entity>();
        for (String entity : entities) {
            Set<String> types = new HashSet<String>();
            if (cpeople.contains(entity))
                types.add("cp");
            if (cplaces.contains(entity))
                types.add("cl");
            if (corgs.contains(entity))
                types.add("co");
            if (e.contains(entity))
                types.add("person");
            if (orgs.contains(entity))
                types.add("org");
            if (places.contains(entity))
                types.add("place");
            if (acrs.contains(entity))
                types.add("acr");
            String ce = IndexUtils.canonicalizeEntity(entity);
            if (ce == null)
                continue;
            if (authorisedEntities != null && authorisedEntities.containsKey(ce)) {
                entitiesWithId.put(entity, new Entity(entity, authorisedEntities.get(ce), types));
            } else
                entitiesWithId.put(entity, new Entity(entity, null, types));
        }

        //dont want more button anymore
        boolean overflow = false;
//		if (!inFull && contents.length() > 4999) {
//			contents = Util.ellipsize(contents, 4999);
//			overflow = true;
//		}
        String htmlContents = annotate(ldoc, contents, date, docId, sensitive, highlightTermsStemmed, highlightTermsUnstemmed, entitiesWithId, IA_links, showDebugInfo);
        //also add NER offsets for debugging
//		htmlContents += "<br>Offsets: <br>";
//		List<Triple<String,Integer, Integer>> triples = edu.stanford.muse.ner.NER.getNamesOffsets(ldoc);
//		for(Triple<String,Integer,Integer> t: triples)
//			htmlContents += t.getFirst()+" <"+t.getSecond()+", "+t.getThird()+"><br>";

        if (ModeConfig.isPublicMode())
            htmlContents = Util.maskEmailDomain(htmlContents);

        StringBuilder sb = new StringBuilder();
        sb.append(htmlContents);
        return new Pair<>(sb, overflow);
    }

    public List<MultiDoc> clustersForDocs(Collection<? extends Document> docs) {
        return clustersForDocs(docs, MultiDoc.ClusteringType.MONTHLY);
    }
    /* break up docs into clusters, based on existing docClusters
    * Note: Clustering Type MONTHLY and YEARLY not supported*/
    public List<MultiDoc> clustersForDocs(Collection<? extends Document> docs, MultiDoc.ClusteringType ct) {
        //TODO: whats the right thing to do when docClusters is null?
        if (docClusters == null || (ct == MultiDoc.ClusteringType.NONE)) {
            List<MultiDoc> new_mDocs = new ArrayList<MultiDoc>();
            MultiDoc md = new MultiDoc(0,"all");
            for (Document d : docs) {
                md.add(d);
            }
            new_mDocs.add(md);
            return new_mDocs;
        }

        Map<Document, Integer> map = new LinkedHashMap<Document, Integer>();
        int i = 0;
        for (MultiDoc mdoc : docClusters) {
            for (Document d : mdoc.docs)
                map.put(d, i);
            i++;
        }

        List<MultiDoc> new_mDocs = new ArrayList<MultiDoc>();
        for (@SuppressWarnings("unused")
        MultiDoc md : docClusters)
            new_mDocs.add(null);

        for (Document d : docs) {
            int x = map.get(d);
            MultiDoc new_mDoc = new_mDocs.get(x);
            if (new_mDoc == null) {
                MultiDoc original = docClusters.get(x);
                new_mDoc = new MultiDoc(original.getUniqueId(), original.description);
                new_mDocs.set(x, new_mDoc);
            }
            new_mDoc.add(d);
        }

        List<MultiDoc> result = new ArrayList<MultiDoc>();
        for (MultiDoc md : new_mDocs)
            if (md != null)
                result.add(md);

        return result;
    }

    public String toString() {
        // be defensive here -- some of the fields may be null
        StringBuilder sb = new StringBuilder();
        if (allDocs != null)
            sb.append("Archive with #docs: " + allDocs.size() + " address book: " + addressBook + " " + getStats() + " ");
        else
            sb.append("Null docs");
        if (indexer != null) {
            if (indexer.stats != null)
                sb.append(Util.fieldsToString(indexer.stats, false));
            else
                sb.append("Null indexer-stats");
        } else
            sb.append("Null indexer");
        return sb.toString();
    }

    public org.apache.lucene.document.Document getDoc(edu.stanford.muse.index.Document d) throws IOException {
        return indexer.getDoc(d);
    }

    private Set<String> getNames(edu.stanford.muse.index.Document d, Indexer.QueryType qt)
    {
        try {
            return new LinkedHashSet<String>(getNamesForDocId(d.getUniqueId(), qt));
        } catch (Exception e) {
            Util.print_exception(e, log);
            return new LinkedHashSet<String>();
        }
    }

    //returns a map of names recognised by NER to frequency
    private Map<String, Integer> countNames() {
        Map<String, Integer> name_count = new LinkedHashMap<String, Integer>();
        for (Document d : getAllDocs()) {
            Set<String> names = getNames(d, Indexer.QueryType.FULL);
            // log.info("Names = " + Util.joinSort(names, "|"));
            for (String n : names) {
                n = n.trim();
                if (n.length() == 0)
                    continue;
                if (name_count.containsKey(n))
                    name_count.put(n, name_count.get(n) + 1);
                else
                    name_count.put(n, 1);
            }
        }

        // for (Map.Entry<String, Integer> e : entries) {
        // log.info("NameCount:" + e.getKey() + "|" + e.getValue());
        // }
        return name_count;
    }

    public List<String> getNamesForDocId(String id, Indexer.QueryType qt) throws IOException
    {
        return indexer.getNamesForDocId(id, qt);
    }

    public List<List<String>> getAllNames(Collection<String> ids, Indexer.QueryType qt) throws IOException
    {
        List<List<String>> result = new ArrayList<>();
        for (String id : ids)
            result.add(getNamesForDocId(id, qt));
        return result;
    }

    /**
     * One of threshold_pct and n can be set.
     * @param threshold_pct - get only the top threshol_pct names
     * @param n - limit the number of names returned at n
     * @param sort_by_names - sort by names or frequency
     * @return top names whose frequency percentage is higher than
     * */
    public List<Map.Entry<String, Integer>> getTopNames(int threshold_pct, int n, boolean sort_by_names) {
        if (topNames == null) {
            // sort by count
            topNames = new ArrayList<Map.Entry<String, Integer>>(countNames().entrySet());
            Collections.sort(topNames, new Comparator<Map.Entry<String, Integer>>() {
                public int compare(Map.Entry<String, Integer> e1, Map.Entry<String, Integer> e2) {
                    return e2.getValue().compareTo(e1.getValue());
                }
            });

            // rescale the count to be in the range of 0-100
            if (topNames.size() > 0) {
                int max_count = topNames.get(0).getValue();
                for (Map.Entry<String, Integer> e : topNames)
                    e.setValue((int) (Math.pow(e.getValue().doubleValue() / max_count, 0.25) * 100));
            }
        }

        int count = 0;
        for (Map.Entry<String, Integer> e : topNames) {
            if (e.getValue() < threshold_pct || count == n)
                break;
            count++;
        }

        List<Map.Entry<String, Integer>> result = new ArrayList<Map.Entry<String, Integer>>(topNames.subList(0, count));
        if (sort_by_names) {
            // NOTE: this sort triggers java.lang.AbstractMethodError at
            // java.util.Arrays.mergeSort when placed in JSP.
            Collections.sort(result, new Comparator<Map.Entry<String, Integer>>() {
                public int compare(Map.Entry<String, Integer> e1, Map.Entry<String, Integer> e2) {
                    return e1.getKey().compareTo(e2.getKey());
                }
            });
        }

        return result;
    }

    /**
     * Assign Ids to threads, can help in making out if two emails belong to the same thread
     * Subject/Title of the a message can also be used for the same purpose
     * @return the maximum thread id value assignbed to any thread in th arhchive*/
    public int assignThreadIds() {
        Collection<Collection<EmailDocument>> threads = EmailUtils.threadEmails((Collection) allDocs);
        int thrId = 1; // note: valid thread ids must be > 1
        for (Collection<EmailDocument> thread : threads) {
            for (EmailDocument doc : thread)
                doc.threadID = thrId;
            thrId++;
        }
        return thrId;
    }

    public void postDeserialized(String baseDir, boolean readOnly) throws IOException {
        if (ModeConfig.isPublicMode())
            setGroupAssigner(null);

        log.info(indexer.computeStats());

        indexer.setBaseDir(baseDir);
        openForRead();

        if (!readOnly)
            indexer.setupForWrite();
        // getTopNames();
        if (addressBook != null) {
            // addressBook.reassignContactIds();
            addressBook.organizeContacts(); // is this idempotent?
        }

        if (lexiconMap == null) {
            lexiconMap = createLexiconMap(baseDir);
        }

        // recompute... sometimes the processing metadata may be stale, because some messages have been redacted at export.
        processingMetadata.numPotentiallySensitiveMessages = numMatchesPresetQueries();
    }

    public void merge(Archive other) {
        for (Document doc : other.getAllDocs()) {
            if (!this.containsDoc(doc))
                this.addDoc(doc, other.getContents(doc, /* originalContentOnly */false));
        }

        addressBook.merge(other.addressBook);
        this.processingMetadata.merge(other.processingMetadata);
    }

    private static Map<String, Lexicon> createLexiconMap(String baseDir) throws IOException {
        String lexDir = baseDir + File.separatorChar + LEXICONS_SUBDIR;
        Map<String, Lexicon> map = new LinkedHashMap<String, Lexicon>();
        File lexDirFile = new File(lexDir);
        if (!lexDirFile.exists()) {
            log.warn("'lexicons' directory is missing from archive");
        } else {
            for (File f : lexDirFile.listFiles(new Util.MyFilenameFilter(null, Lexicon.LEXICON_SUFFIX))) {
                String name = Lexicon.lexiconNameFromFilename(f.getName());
                if (!map.containsKey(name)) {
                    map.put(name.toLowerCase(), new Lexicon(lexDir, name));
                }
            }
        }
        return map;
    }

    public Lexicon getLexicon(String lexName) {
        // lexicon map could be stale, re-read it
        try {
            lexiconMap = createLexiconMap(baseDir);
        } catch (Exception e) {
            Util.print_exception("Error trying to read list of lexicons", e, log);
        }
        return lexiconMap.get(lexName.toLowerCase());
    }

    public Set<String> getAvailableLexicons() {
        // lexicon map could be stale, re-read it
        try {
            lexiconMap = createLexiconMap(baseDir);
        } catch (Exception e) {
            Util.print_exception("Error trying to read list of lexicons", e, log);
        }
        if (lexiconMap == null)
            return new LinkedHashSet<String>();
        return Collections.unmodifiableSet(lexiconMap.keySet());
    }

    public void addStats(FetchStats as) {
        allStats.add(as);
    }

    public Collection<String> getDataErrors() {
        Collection<String> result = new LinkedHashSet<String>();

        for (FetchStats as : allStats) {
            Collection<String> asErrors = as.dataErrors;
            if (asErrors != null)
                result.addAll(asErrors);
        }
        return result;
    }

    /**Replaces the document in the index with the supplied document*/
    public void updateDocument(org.apache.lucene.document.Document doc) {
        indexer.updateDocument(doc);
    }

    /**Reads offset field in the supplied lucene doc, deserializes it and returns
     */
    public static List<Triple<String, Integer, Integer>> getNamesOffsets(org.apache.lucene.document.Document doc) {
        BytesRef bytesRef = doc.getBinaryValue(NER.NAMES_OFFSETS);
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
            log.info("Failed to deserialize names_offsets");
            e.printStackTrace();
            result = new ArrayList<Triple<String, Integer, Integer>>();
        }

        return result;
    }

    public List<Triple<String, Integer, Integer>> getNamesOffsets(Document doc) {
        try {
            org.apache.lucene.document.Document ldoc = indexer.getLDoc(doc.getUniqueId());
            return getNamesOffsets(ldoc);
        }catch(Exception e){
            log.info("Ldoc for "+doc.getUniqueId()+" not found");
            return null;
        }
    }


    public void setupForWrite() throws IOException{
        indexer.setupForWrite();
    }

    /**
     * @return number of hits to the queries read from presetqueries.txt*/
    public int numMatchesPresetQueries() {
        readPresetQueries();
        try {
            return indexer.countHitsForQuery(null, Indexer.QueryType.PRESET_REGEX);
        } catch (Exception e) {
            Util.print_exception("Minor: error while computing number of sensitive messages, the number will be set to 0", e, log);
            e.printStackTrace();
            return 0;
        }
    }

    public List<String> filterOriginalContent(edu.stanford.muse.index.Document doc, List<String> names) {
        List<String> originalAllNames = getEntitiesInDoc(doc, NER.NAMES_ORIGINAL);
        List<String> originalNames = new ArrayList<>();
        for(String str: names)
            if(originalAllNames.contains(str))
               originalNames.add(str);
        return originalNames;
    }

    public List<String> getEntitiesInDoc(edu.stanford.muse.index.Document doc, String type, Boolean filter, boolean originalContentOnly){
        if(originalContentOnly)
            return filterOriginalContent(doc, getEntitiesInDoc(doc, type, filter));
        else
            return getEntitiesInDoc(doc, type, filter);
    }

    public List<String> getQualityEntitiesInDoc(edu.stanford.muse.index.Document doc, String type, Boolean filter, boolean originalContentOnly){
        if(originalContentOnly)
            return filterOriginalContent(doc, getQualityEntitiesInDoc(doc, type, filter));
        else
            return getQualityEntitiesInDoc(doc, type, filter);
    }

    //type should be one of strings EPER, ELOC, EORG, as set in NER.java
    //returns filtered list of all names
    public List<String> getEntitiesInDoc(edu.stanford.muse.index.Document doc, String type, Boolean filter) {
        org.apache.lucene.document.Document ldoc = null;
        try {
            ldoc = indexer.getDoc(doc);
        } catch (IOException e) {
            log.warn("Unable to obtain document " + doc.getUniqueId() + " from index");
            e.printStackTrace();
            return null;
        }
        return getEntitiesInLuceneDoc(ldoc, type, filter);
    }

    //puts an extra layer of filtering over entities by filtering out entities also recognised as other type
    public List<String> getQualityEntitiesInDoc(edu.stanford.muse.index.Document doc, String type, Boolean filter) {
        org.apache.lucene.document.Document ldoc = null;
        try {
            ldoc = indexer.getDoc(doc);
        } catch (IOException e) {
            log.warn("Unable to obtain document " + doc.getUniqueId() + " from index");
            e.printStackTrace();
            return null;
        }
        List<String> thises = getEntitiesInLuceneDoc(ldoc, type, filter);
        String[] types = new String[]{NER.EPER, NER.ELOC, NER.EORG};
        List<String> otheres = new ArrayList<>();
        for(String et: types) {
            if (et.equals(type))
                continue;
            List<String> temp = getEntitiesInLuceneDoc(ldoc, et, filter);
            if(temp!=null)
                otheres.addAll(temp);
        }
        List<String> ret = new ArrayList<>();
        for(String te: thises)
            if(!otheres.contains(te))
                ret.add(te);
        return ret;
    }

    /**@return a list of names filtered to remove dictionary matches*/
    public List<String> getEntitiesInDoc(edu.stanford.muse.index.Document d, String type) {
        return getEntitiesInDoc(d, type, true);
    }

    /**@return list of all names in the lucene doc without filtering dictionary words*/
    public static List<String> getEntitiesInLuceneDoc(org.apache.lucene.document.Document ldoc, String type, Boolean filter) {
        String field = ldoc.get(type);
        if(filter)
            return edu.stanford.muse.ie.Util.filterEntities(Util.tokenize(field, Indexer.NAMES_FIELD_DELIMITER), type);
        else
            return Util.tokenize(field, Indexer.NAMES_FIELD_DELIMITER);
    }

    public static void main(String[] args) {
        try {
            String userDir = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            List<Document> docs = archive.getAllDocs();
            int i=0;
            archive.assignThreadIds();
            for(Document doc: docs) {
                EmailDocument ed = (EmailDocument)doc;
                List<Document> threads = archive.docsWithThreadId(ed.threadID);
                if(threads.size()>0){
                    int numSent = 0;
                    for(Document d: threads){
                        EmailDocument thread = (EmailDocument)d;
                        int sent = thread.sentOrReceived(archive.addressBook)&EmailDocument.SENT_MASK;
                        if(sent>0)
                            numSent++;
                    }
                    if(threads.size()!=numSent || threads.size()>2){
                        System.err.println("Found a thread with "+numSent+" sent and "+threads.size()+" docs in a thread: "+ed.getSubject());
                        break;
                    }
                    if(i%100 == 0)
                        System.err.println("Scanned: "+i+" docs");
                }
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
