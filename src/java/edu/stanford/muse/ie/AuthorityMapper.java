package edu.stanford.muse.ie;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multimap;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static edu.stanford.muse.ie.variants.EntityMapper.canonicalize;
import static java.lang.System.out;

/**
 * Authority mapper for correspondents only
 */
public class AuthorityMapper implements java.io.Serializable {
    Map<String, Long> confirmedCnameToFastId = new LinkedHashMap<>();
    private Set<String> confirmedCnamesWithNoAuth = new LinkedHashSet<>();

    transient Multimap<String, Long> cnameToFastIdCandidates = LinkedHashMultimap.create(); // make this transient because it can be recomputed later

    transient private StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_47, new CharArraySet(Version.LUCENE_47, new ArrayList<String>(), true /* ignore case */));
    transient private IndexSearcher indexSearcher;
    transient private QueryParser parser;
    transient private QueryParser parserFastId;
    transient private IndexReader indexReader;

    public AuthorityMapper (Archive archive, String dir) throws IOException, ParseException {

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
                        Long fastId = Long.parseLong (d.get (FASTIndexer.FIELD_NAME_FAST_ID));
                        cnameToFastIdCandidates.put (cname, fastId);
                    }
                }
            }
        }
        closeFastIndex ();
    }

    /** populates the other ids, given the fast Id */
    private void updateOtherIds (JSONObject obj) throws ParseException, IOException {
        long fastId = obj.getLong (FASTIndexer.FIELD_NAME_FAST_ID);
        Query query = parser.parse(Long.toString (fastId));
        TopDocs docs = indexSearcher.search (query, null, 10000);

        for (ScoreDoc scoreDoc: docs.scoreDocs) {
            Document d = indexSearcher.doc(scoreDoc.doc);
            String viafId = d.get(FASTIndexer.FIELD_NAME_VIAF_ID);
            String lcshId = d.get(FASTIndexer.FIELD_NAME_LCSH_ID);
            String lcnafId = d.get(FASTIndexer.FIELD_NAME_LCNAF_ID);
            String wikipediaId = d.get(FASTIndexer.FIELD_NAME_WIKIPEDIA_ID);

            if (!Util.nullOrEmpty(viafId)) {
                obj.put(FASTIndexer.FIELD_NAME_VIAF_ID, viafId);
            }
            if (!Util.nullOrEmpty(wikipediaId)) {
                obj.put(FASTIndexer.FIELD_NAME_WIKIPEDIA_ID, wikipediaId);
            }
            if (!Util.nullOrEmpty(lcnafId)) {
                obj.put(FASTIndexer.FIELD_NAME_LCNAF_ID, lcnafId);
            }
            if (!Util.nullOrEmpty(lcshId)) {
                obj.put(FASTIndexer.FIELD_NAME_LCSH_ID, lcshId);
            }
        }
    }

    public JSONObject getJsonForName (String name) throws IOException, ParseException {
        String cname = canonicalize (name);
        JSONObject result = new JSONObject();

        Long fastId = confirmedCnameToFastId.get(cname);
        if (fastId != null) {
            result.put ("status", "confirmed");
            JSONObject obj = new JSONObject();
            obj.put (FASTIndexer.FIELD_NAME_FAST_ID, (long) fastId);
            updateOtherIds (obj);
            result.put ("authority", obj);
        } else {
            if (confirmedCnamesWithNoAuth.contains (cname)) {
                result.put ("status", "confirmedNoAuth");
            } else {
                JSONArray jarr = new JSONArray();
                result.put ("status", "candidates");
                Collection<Long> fastIds = cnameToFastIdCandidates.get(cname);
                if (fastIds != null) {
                    int count = 0;
                    for (Long l: fastIds) {
                        JSONObject obj = new JSONObject();
                        obj.put (FASTIndexer.FIELD_NAME_FAST_ID, l);
                        updateOtherIds(obj);
                        jarr.put (count++, obj);
                    }
                    result.put ("candidates", jarr);
                } else {
                    result.put ("status", "noMatch");
                }
            }
        }

        return result;
    }

    public void setFastId(int contactId, long fastId) {

    }

    public void setNoFastId (int contactId) {

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
        parser = new MultiFieldQueryParser(Version.LUCENE_47, new String[] {FASTIndexer.FIELD_NAME_LABELS}, analyzer);
        parser = new MultiFieldQueryParser(Version.LUCENE_47, new String[] {FASTIndexer.FIELD_NAME_FAST_ID}, analyzer);
    }

    private void closeFastIndex () throws IOException {
        if (indexReader != null)
            indexReader.close();
    }
}
