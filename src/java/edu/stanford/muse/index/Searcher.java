package edu.stanford.muse.index;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.datacache.BlobStore;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.HTMLUtils;
import edu.stanford.muse.webapp.JSPHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.james.mime4j.dom.address.Address;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by hangal on 6/22/16.
 */
public class Searcher {
    private static Log log = LogFactory.getLog(Searcher.class);

    String[] paramNames = new String[]{
            //////////////////////// required for advanced search
            "term", "termOriginalContentOnly", "termRegex", "subjectTerm", "subjectTermRegex",
            "entity",
            "to", "cc-bcc", // cc or bcc
            "mailingListState",
            "attachmentTerm", "attachmentFilename", "attachmentExtension", "attachmentType", "attachmentFilesize",
            "annotation", "doNotTransfer", "transferWithRestrictions", "reviewed",
            "start_date", "end_date", "direction",
            "folder", "emailSource",
            "lexiconName", "lexiconCategory",
            "sortBy",
            /////////////////////////////////////////////
            "contact",  // this is for contactId
            "person", "attachment", "timeCluster", "groupIdx", // may not be used
            "datasetId", "docId", "docNum", "sensitive" // may not be used in adv. search
            };

    public Pair<Collection<Document>, Collection<Blob>> selectDocs (Archive archive, HttpServletRequest request, boolean or_not_and) throws UnsupportedEncodingException {
        Multimap<String, String> params = LinkedHashMultimap.create();
        for (String param : paramNames) {
            String[] vals = request.getParameterValues(param);
            if (vals != null)
                for (String val : vals)
                    params.put(param, JSPHelper.convertRequestParamToUTF8(val));
        }
        return selectDocsWithHighlightAttachments(archive, params, or_not_and);
    }

    /** returns a single value for the given key */
    private static String getParam(Multimap<String, String> params, String key) {
        Collection<String> values = params.get(key);
        if (values == null || values.size() == 0)
            return null;
        return values.iterator().next();
    }

    private static Pair<Collection<Document>, Collection<Blob>> searchForTerm(Archive archive, Multimap<String, String> params, String term) {
        // go in the order subject, body, attachment
        Set<Document> docsForTerm = new LinkedHashSet<>();
        Set<Blob> blobsForTerm = new LinkedHashSet<>();
        Indexer.SortBy sortBy = Indexer.SortBy.RELEVANCE;
        {
            String sortByStr = getParam(params, "sortBy");
            if (!Util.nullOrEmpty(sortByStr)) {
                if ("relevance".equals(sortByStr.toLowerCase()))
                    sortBy = Indexer.SortBy.RELEVANCE;
                else if ("recent".equals(sortByStr.toLowerCase()))
                    sortBy = Indexer.SortBy.RECENT_FIRST;
                else if ("chronological".equals(sortByStr.toLowerCase()))
                    sortBy = Indexer.SortBy.CHRONOLOGICAL_ORDER;
                else {
                    log.warn("Unknown sort by option: " + sortBy);
                }
            }
        }
        if ("true".equals(getParam(params, "termSubject"))) {
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.SUBJECT);
            options.setSortBy(sortBy);
            docsForTerm.addAll(archive.docsForQuery(term, options));
        }

        if ("true".equals(getParam(params, "termBody"))) {
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.FULL);
            options.setSortBy(sortBy);
            docsForTerm.addAll(archive.docsForQuery(term, options));
        } else if ("true".equals(getParam(params, "termBodyOriginal"))) { // this is an else because we don't want to look at both body and body original
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.ORIGINAL);
            options.setSortBy(sortBy);
            docsForTerm.addAll(archive.docsForQuery(term, options));
        }

        if ("true".equals(getParam(params, "termAttachments"))) {
            blobsForTerm = archive.blobsForQuery(term);
            Set<Document> blobDocsForTerm = (Set<Document>) EmailUtils.getDocsForAttachments((Collection) allDocs, blobsForTerm);
            docsForTerm.addAll(blobDocsForTerm);
        }

        List<Document> docsForTermList = new ArrayList<>(docsForTerm);
        if (sortBy == Indexer.SortBy.CHRONOLOGICAL_ORDER)
            Collections.sort(docsForTermList);
        else if (sortBy == Indexer.SortBy.RECENT_FIRST) {
            Collections.sort(docsForTermList);
            Collections.reverse(docsForTermList);
        }

        return new Pair<>(docsForTermList, blobsForTerm);
    }

    /** splits by semicolons, lowercases, trims spaces; e.g. given "A; b" returns ["a", "b"] */
    private static Set<String> splitFieldForOr(String s) {
        char OR_DELIMITER = ';';
        Collection<String> tokens = Util.tokenize(s, Character.toString(OR_DELIMITER));
        Set<String> result = new LinkedHashSet<>();
        for (String token: tokens)
            result.add(token.toLowerCase());
        return result;
    }

    private static Collection<EmailDocument> updateForFlags(Collection<EmailDocument> docs, Multimap<String, String> params) {
        String reviewedValue = getParam(params, "reviewed");
        if (!"either".equals(reviewedValue)) {
            Set<EmailDocument> newDocs = new LinkedHashSet<>();
            for (EmailDocument ed: docs)
                if (ed.reviewed == "yes".equals(reviewedValue))
                   newDocs.add(ed);
            docs = newDocs;
        }

        String dntValue = getParam(params, "doNotTransfer");
        if (!"either".equals(dntValue)) {
            Set<EmailDocument> newDocs = new LinkedHashSet<>();
            for (EmailDocument ed: docs)
                if (ed.doNotTransfer != "yes".equals(dntValue))
                    newDocs.add(ed);
            docs = newDocs;
        }

        String twrValue = getParam(params, "transferWithRestrictions");
        if (!"either".equals(twrValue)) {
            Set<EmailDocument> newDocs = new LinkedHashSet<>();
            for (EmailDocument ed: docs)
                if (ed.transferWithRestrictions == "yes".equals(twrValue))
                    newDocs.add(ed);
            docs = newDocs;
        }

        String annotationStr = getParam(params, "annotation");
        if (!Util.nullOrEmpty(annotationStr)) {
            Set<String> annotations = splitFieldForOr(annotationStr);
            Set<EmailDocument> newDocs = new LinkedHashSet<>();
            for (EmailDocument ed: docs) {
                if (!Util.nullOrEmpty(ed.comment)) {
                    String comment = ed.comment.toLowerCase();
                    if (annotations.contains(comment)) {
                        newDocs.add(ed);
                    }
                }
            }
            docs = newDocs;
        }

        return docs;
    }

    /**
     * Important method.
     * handle query for term, sentiment, person, attachment, docNum, timeCluster
     * etc
     * note: date range selection is always ANDed
     * if only_apply_to_filtered_docs, looks at emailDocs, i.e. ones selected by
     * the current filter (if there is one)
     * if !only_apply_to_filtered_docs, looks at all docs in archive
     * note: only_apply_to_filtered_docs == true is not honored by lucene lookup
     * by term (callers need to filter by themselves)
     * note2: performance can be improved. e.g., if in AND mode, searches that
     * iterate through documents such as
     * selectDocByTag, getBlobsForAttachments, etc., can take the intermediate
     * resultDocs rather than allDocs.
     * set intersection/union can be done in place to the intermediate
     * resultDocs rather than create a new collection.
     * getDocsForAttachments can be called on the combined result of attachments
     * and attachmentTypes search, rather than individually.
     * note3: should we want options to allow user to choose whether to search
     * only in emails, only in attachments, or both?
     * also, how should we allow variants in combining multiple conditions.
     * there will be work in UI too.
     * note4: the returned resultBlobs may not be tight, i.e., it may include
     * blobs from docs that are not in the returned resultDocs.
     * but for docs that are in resultDocs, it should not include blobs that are
     * not hitting.
     * these extra blobs will not be seen since we only use this info for
     * highlighting blobs in resultDocs.
     */
    public static Pair<Collection<Document>, Collection<Blob>> selectDocsWithHighlightAttachments(Archive archive, Multimap<String, String> params, boolean or_not_and) throws UnsupportedEncodingException
    {
        // below are all the controls for selecting docs

        Collection<Document> allDocs = archive.getAllDocs();
        AddressBook addressbook = archive.addressBook;
        Collection<Document> resultDocs = archive.getAllDocs();
        Collection<Blob> resultBlobs = null;

        String term = getParam(params, "term");
        if (!Util.nullOrEmpty(term)) {
            Pair<Collection<Document>, Collection<Blob>> p = searchForTerm(archive, params, term);
            resultDocs = p.getFirst();
            resultBlobs = p.getSecond();
        }

        // resultDocs = (Collection) updateForEntities((Collection) resultDocs, params);
    //    resultDocs = (Collection) updateForCorrespondents((Collection) resultDocs, params);
        resultDocs = (Collection) updateForFlags((Collection) resultDocs, params);
        resultBlobs = (Collection) updateForAttachments((Collection) resultDocs, params);
     //   resultDocs = (Collection) updateForOthers((Collection) resultDocs, params);
      //  resultDocs = (Collection) updateForLexicons((Collection) resultDocs, params);

        // now only keep blobs that belong to resultdocs


        return new Pair<Collection<Document>, Collection<Blob>>(resultDocs, resultBlobs);
    }
}
