package edu.stanford.muse.ie;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multimap;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.index.Archive;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static edu.stanford.muse.ie.FASTIndexer.FIELD_NAME_FAST_ID;
import static edu.stanford.muse.ie.variants.EntityMapper.canonicalize;
import static java.lang.System.out;

/**
 * Authority mapper for correspondents only
 */
public class AuthorityMapper implements java.io.Serializable {
    private final static long serialVersionUID = 1L;
    public final static long INVALID_FAST_ID = -1L;

    public static class AuthorityInfo {
        public boolean isConfirmed;
        public int nMessages;
        public String name, tooltip, url, errorMessage;
        public AuthorityRecord confirmedAuthority;
        public List<AuthorityRecord> candidates;
    }

    public static class AuthorityRecord {
        public long fastId;
        public String lcshId, lcnafId, wikipediaId, viafId;
        public String preferredLabel, altLabels;
    }


    Map<String, Long> confirmedCnameToFastId = new LinkedHashMap<>();
    private Map<String, Integer> cnameToCount = new LinkedHashMap<>();

    transient Multimap<String, Long> cnameToFastIdCandidates = LinkedHashMultimap.create(); // make this transient because it can be recomputed later

    transient private StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_47, new CharArraySet(Version.LUCENE_47, new ArrayList<String>(), true /* ignore case */));
    transient private IndexSearcher indexSearcher;
    transient private QueryParser parser;
    transient private IndexReader indexReader;
    private Archive archive;

    public AuthorityMapper (Archive archive, String dir) throws IOException, ParseException {

        this.archive = archive;
        openFastIndex(dir);
        AddressBook ab = archive.getAddressBook();

        List<Contact> contacts = ab.allContacts();
        for (Contact c: contacts) {
            Set<String> names = c.names;
            if (Util.nullOrEmpty (names))
                continue;

            String contactName = c.pickBestName();
            String cname = canonicalize (contactName);
            if (confirmedCnameToFastId.get(cname) != null) {
                // confirmed auth
                // populate jobj
                continue;
            } else {
                for (String name : names) {
                    List<Document> hits = lookupNameInFastIndex(name);

                    for (Document d : hits) {
                        Long fastId = Long.parseLong (d.get (FIELD_NAME_FAST_ID));
                        cnameToFastIdCandidates.put (cname, fastId);
                    }
                }
            }
        }
    }

    public void setupCounts(Archive archive) {
        AddressBook ab = archive.getAddressBook();
        List<Pair<Contact, Integer>> pairs = ab.sortedContactsAndCounts((Collection) archive.getAllDocs());
        for (Pair<Contact, Integer> p: pairs) {
            Contact c = p.getFirst();
            String name = c.pickBestName();
            String cname = canonicalize(name);
            if (Util.nullOrEmpty(cname))
                continue;

            cnameToCount.put(cname, p.getSecond());
        }
    }

    /** populates the other ids, given the fast Id */
    private AuthorityRecord updateOtherIds (long fastId) throws ParseException, IOException {
        AuthorityRecord result = new AuthorityRecord();

        result.fastId = fastId;
        Query query = NumericRangeQuery.newLongRange(FIELD_NAME_FAST_ID, fastId, fastId, true, true); // don't do a string query, must do a numeric range query

        TopDocs docs = indexSearcher.search (query, null, 10000);

        // there should be only 1 result
        for (ScoreDoc scoreDoc: docs.scoreDocs) {
            Document d = indexSearcher.doc(scoreDoc.doc);
            result.viafId = d.get(FASTIndexer.FIELD_NAME_VIAF_ID);
            result.lcshId = d.get(FASTIndexer.FIELD_NAME_LCSH_ID);
            result.lcnafId = d.get(FASTIndexer.FIELD_NAME_LCNAF_ID);
            result.wikipediaId = d.get(FASTIndexer.FIELD_NAME_WIKIPEDIA_ID);

            String labels = d.get(FASTIndexer.FIELD_NAME_LABELS);
            if (!Util.nullOrEmpty(labels)) {
                String splitLabels[] = labels.split (" ; ", 2);
                if (!Util.nullOrEmpty(splitLabels[0]))
                    result.preferredLabel = splitLabels[0];
                if (splitLabels.length > 1 && !Util.nullOrEmpty(splitLabels[1]))
                    result.altLabels = splitLabels[1];
            }
        }
        return result;
    }

    public AuthorityInfo getAuthorityInfo (String name) throws IOException, ParseException {
        String cname = canonicalize (name);
        AuthorityInfo result = new AuthorityInfo();
        result.isConfirmed = false;
        result.name = name;
        AddressBook ab = archive.getAddressBook();
        Contact c = ab.lookupByName(name);

        Integer nMessages = (cnameToCount != null) ? cnameToCount.get(cname) : null;
        result.nMessages = (nMessages == null) ? 0 : nMessages;

        if (c == null) {
            result.errorMessage = "Name not in address book: " + name;
            return result;
        }

        result.url = "browse?adv-search=1&contact=" + ab.getContactId(c);
        result.tooltip = c.toTooltip();
        result.nMessages = nMessages;

        Long fastId = confirmedCnameToFastId.get(cname);
        if (fastId != null) {
            result.isConfirmed = true;
            result.confirmedAuthority = updateOtherIds(fastId);
        }

        List<AuthorityRecord> candidates = new ArrayList<>();
        Collection<Long> fastIds = cnameToFastIdCandidates.get(cname);
        if (fastIds != null)
            for (Long id : fastIds)
                candidates.add (updateOtherIds(id));

        result.candidates = candidates;
        return result;
    }

    public void setFastId(int contactId, long fastId) {

    }

    private List<Document> lookupNameInFastIndex (String name) throws IOException, ParseException {
        List<Document> result = new ArrayList<>();

        Query query = parser.parse("\"" + name + "\"");
        TopDocs docs = indexSearcher.search (query, null, 10000);

        for (ScoreDoc scoreDoc: docs.scoreDocs) {
            Document d = indexSearcher.doc(scoreDoc.doc);
            result.add (d);
        }
        return result;
    }

    private void openFastIndex (String dir) throws IOException {
        indexReader = DirectoryReader.open(FSDirectory.open (new File(dir)));
        analyzer = new StandardAnalyzer(Version.LUCENE_47, new CharArraySet(Version.LUCENE_47, new ArrayList<String>(), true /* ignore case */)); // empty chararrayset, so effectively no stop words
        indexSearcher = new IndexSearcher(indexReader);
        parser = new QueryParser(Version.LUCENE_47, FASTIndexer.FIELD_NAME_LABELS, analyzer);
    }

    private void closeFastIndex () throws IOException {
        if (indexReader != null)
            indexReader.close();
    }
}
