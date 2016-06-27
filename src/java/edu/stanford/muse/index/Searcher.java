package edu.stanford.muse.index;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.email.MailingList;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Created by hangal on 6/22/16.
 */
public class Searcher {
    private static Log log = LogFactory.getLog(Searcher.class);

    /*
    private static String[] paramNames = new String[]{
            //////////////////////// required for advanced search
            "term", "termBody", "termSubject", "termAttachments", "termOriginalBody",
            "entity",
            "to", "cc-bcc", // cc or bcc
            "mailingListState",
            "attachmentTerm", "attachmentFilename", "attachmentExtension", "attachmentType", "attachmentFilesize",
            "annotation", "doNotTransfer", "transferWithRestrictions", "reviewed",
            "startDate", "endDate", "direction",
            "folder", "emailSource",
            "lexiconName", "lexiconCategory",
            "sortBy",
            /////////////////////////////////////////////
            "contact",  // this is for contactId
            "person", "attachment", "timeCluster", "groupIdx", // may not be used
            "datasetId", "docId", "docNum", "sensitive" // may not be used in adv. search
            };
*/

    public static Pair<Collection<Document>, Collection<Blob>> searchDocs (Archive archive, HttpServletRequest request, boolean or_not_and) throws UnsupportedEncodingException {
        Multimap<String, String> params = LinkedHashMultimap.create();
        Enumeration<String> paramNames = request.getParameterNames();

        while (paramNames.hasMoreElements()) {
            String param = paramNames.nextElement();
            String[] vals = request.getParameterValues(param);
            if (vals != null)
                for (String val : vals)
                    params.put(param, JSPHelper.convertRequestParamToUTF8(val));
        }
        return selectDocsAndBlobs(archive, params, or_not_and);
    }

    /** returns a single value for the given key */
    private static String getParam(Multimap<String, String> params, String key) {
        Collection<String> values = params.get(key);
        if (values == null || values.size() == 0)
            return null;
        return values.iterator().next();
    }

    private static Pair<Set<Document>, Set<Blob>> searchForTerm(Archive archive, Multimap<String, String> params, String term) {
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
        if ("on".equals(getParam(params, "termSubject"))) {
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.SUBJECT);
            options.setSortBy(sortBy);
            docsForTerm.addAll(archive.docsForQuery(term, options));
        }

        if ("on".equals(getParam(params, "termBody"))) {
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.FULL);
            options.setSortBy(sortBy);
            docsForTerm.addAll(archive.docsForQuery(term, options));
        } else if ("on".equals(getParam(params, "termOriginalBody"))) { // this is an else because we don't want to look at both body and body original
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.ORIGINAL);
            options.setSortBy(sortBy);
            docsForTerm.addAll(archive.docsForQuery(term, options));
        }

        if ("on".equals(getParam(params, "termAttachments"))) {
            blobsForTerm = archive.blobsForQuery(term);
            Set<Document> blobDocsForTerm = (Set<Document>) EmailUtils.getDocsForAttachments((Collection) archive.getAllDocs(), blobsForTerm);
            docsForTerm.addAll(blobDocsForTerm);
        }

        List<Document> docsForTermList = new ArrayList<>(docsForTerm);
        if (sortBy == Indexer.SortBy.CHRONOLOGICAL_ORDER)
            Collections.sort(docsForTermList);
        else if (sortBy == Indexer.SortBy.RECENT_FIRST) {
            Collections.sort(docsForTermList);
            Collections.reverse(docsForTermList);
        }

        return new Pair<>(new LinkedHashSet<>(docsForTermList), blobsForTerm);
    }

    /** splits by semicolons, lowercases, trims spaces; e.g. given "A; b" returns ["a", "b"] */
    private static Set<String> splitFieldForOr(String s) {
        char OR_DELIMITER = ';';
        Collection<String> tokens = Util.tokenize(s, Character.toString(OR_DELIMITER));
        Set<String> result = new LinkedHashSet<>();
        for (String token: tokens)
            result.add(token.toLowerCase().trim());
        return result;
    }

    private static Set<EmailDocument> updateForFlags(Set<EmailDocument> docs, Multimap<String, String> params) {
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
                if (ed.doNotTransfer == "yes".equals(dntValue))
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


    private static Set<EmailDocument> updateForEmailDirection(AddressBook addressBook, Set<EmailDocument> docs, Multimap<String, String> params) {

        String val = getParam(params, "direction");
        if ("either".equals(val))
            return docs;

        boolean direction_in = "in".equals(val), direction_out = "out".equals(val);

        Set<EmailDocument> result = new LinkedHashSet<>();

        if (addressBook != null)
        {
            for (Document d : docs)
            {
                EmailDocument ed = (EmailDocument) d;
                int sent_or_received = ed.sentOrReceived(addressBook);
                if (direction_in)
                    if (((sent_or_received & EmailDocument.RECEIVED_MASK) != 0) || sent_or_received == 0) // if sent_or_received == 0 => we neither directly recd. nor sent it (e.g. it could be received on a mailing list). so count it as received.
                        result.add(ed);
                if (direction_out && (sent_or_received & EmailDocument.SENT_MASK) != 0)
                    result.add(ed);
            }
        }
        return docs;
    }


    private static Set<EmailDocument> updateForCorrespondents(AddressBook ab, Set<EmailDocument> docs, Multimap<String, String> params) {
        Set<EmailDocument> result = new LinkedHashSet<>();
        String correspondentsStr = getParam(params, "correspondent");
        if (Util.nullOrEmpty(correspondentsStr))
            return docs;
        Set<String> correspondents = splitFieldForOr(correspondentsStr);

        Set<Contact> searchedContacts = new LinkedHashSet<>();
        for (String s : correspondents) {
            Contact c = ab.lookupByEmailOrName(s);
            if (c != null)
                searchedContacts.add(c);
        }

        for (EmailDocument ed : docs) {

            Set<InternetAddress> addressesInMessage = new LinkedHashSet<>(); // only lookup the fields (to/cc/bcc/from) that have been enabled
            if ("on".equals(getParam(params, "correspondentTo")) && !Util.nullOrEmpty(ed.to))
                addressesInMessage.addAll((List) Arrays.asList(ed.to));
            if ("on".equals(getParam(params, "correspondentFrom")) && !Util.nullOrEmpty(ed.from))
                addressesInMessage.addAll((List) Arrays.asList(ed.from));
            if ("on".equals(getParam(params, "correspondentCc")) && !Util.nullOrEmpty(ed.cc))
                addressesInMessage.addAll((List) Arrays.asList(ed.cc));
            if ("on".equals(getParam(params, "correspondentBcc")) && !Util.nullOrEmpty(ed.bcc))
                addressesInMessage.addAll((List) Arrays.asList(ed.bcc));

            for (InternetAddress a : addressesInMessage) {
                Contact c = ab.lookupByEmail(a.getAddress());
                if (c == null)
                    c = ab.lookupByName(a.getPersonal());
                if (c != null && searchedContacts.contains(c))
                    result.add(ed);
            }
        }
        return result;
    }

    private static Set<EmailDocument> updateForMailingListState(AddressBook ab, Set<EmailDocument> docs, Multimap<String, String> params) {
        String mailingListState = getParam(params, "mailingListState");
        if ("either".equals(mailingListState))
            return docs;
        Set<EmailDocument> result = new LinkedHashSet<>();

        outer:
        for (EmailDocument ed: docs) {
            Set<InternetAddress> allAddressesInMessage = new LinkedHashSet<>(); // only lookup the fields (to/cc/bcc/from) that have been enabled

            // now check for mailing list state
            if (!Util.nullOrEmpty(ed.to)) {
                allAddressesInMessage.addAll((List) Arrays.asList(ed.to));
            }
            if (!Util.nullOrEmpty(ed.from)) {
                allAddressesInMessage.addAll((List) Arrays.asList(ed.from));
            }
            if (!Util.nullOrEmpty(ed.cc)) {
                allAddressesInMessage.addAll((List) Arrays.asList(ed.cc));
            }
            if (!Util.nullOrEmpty(ed.bcc)) {
                allAddressesInMessage.addAll((List) Arrays.asList(ed.bcc));
            }

            boolean atLeastOneML = false; // is any of these addresses a ML?
            for (InternetAddress a : allAddressesInMessage) {
                Contact c = ab.lookupByEmail(a.getAddress());
                if (c == null)
                    c = ab.lookupByName(a.getPersonal());
                if (c == null)
                    continue; // shouldn't happen, just being defensive

                boolean isMailingList = (c.mailingListState & MailingList.SUPER_DEFINITE) != 0 ||
                        (c.mailingListState & MailingList.USER_ASSIGNED) != 0;

                if (isMailingList && "no".equals(mailingListState))
                    continue outer; // we don't want mailing lists, but found one associated with this message. therefore this message fails search.

                if (isMailingList)
                    atLeastOneML = true; // mark this message as having at least one ML
            }

            if (!atLeastOneML && "yes".equals(mailingListState))
                continue outer; // no ML, but search criteria need ML, so ignore this ed

            // ok, this ed satisfies ML criteria
            result.add(ed);
        }
        return result;
    }

    private static Set<EmailDocument> updateForEmailSource(Set<EmailDocument> docs, Multimap<String, String> params) {
        String val = getParam(params, "emailSource");
        if (Util.nullOrEmpty(val))
            return docs;

        Set<String> emailSources = splitFieldForOr(val);
        Set<EmailDocument> result = new LinkedHashSet<>();

        for (EmailDocument ed : docs)
        {
            if (!Util.nullOrEmpty(ed.emailSource))
                if (emailSources.contains(ed.emailSource.toLowerCase()))
                    result.add(ed);
        }
        return docs;
    }

    private static Set<EmailDocument> updateForFolder(Set<EmailDocument> docs, Multimap<String, String> params) {
        String val = getParam(params, "folder");
        if (Util.nullOrEmpty(val))
            return docs;

        Set<String> folders = splitFieldForOr(val);
        Set<EmailDocument> result = new LinkedHashSet<>();

        for (EmailDocument ed : docs)
        {
            if (!Util.nullOrEmpty(ed.folderName))
                if (folders.contains(ed.folderName.toLowerCase()))
                    result.add(ed);
        }
        return docs;
    }

    private static Set<DatedDocument> updateForDateRange(Set<DatedDocument> docs, Multimap<String, String> params) {
        String start = getParam(params, "startDate"), end = getParam(params, "endDate");
        if (Util.nullOrEmpty(start) && Util.nullOrEmpty(end))
            return docs;

        int startYear = -1, startMonth = -1, startDate = -1, endYear = -1, endMonth = -1, endDate = -1;
        if (!Util.nullOrEmpty(start) || !Util.nullOrEmpty(end)) {
            try {
                List<String> startTokens = Util.tokenize(getParam(params, "startDate"), "-");
                startYear = Integer.parseInt(startTokens.get(0));
                startMonth = Integer.parseInt(startTokens.get(1));
                startDate = Integer.parseInt(startTokens.get(2));
            } catch (Exception e) {
                Util.print_exception("Invalid start date: " + start, e, log);
                return docs;
            }

            try {
                List<String> endTokens = Util.tokenize(end, "-");
                endYear = Integer.parseInt(endTokens.get(0));
                endMonth = Integer.parseInt(endTokens.get(1));
                endDate = Integer.parseInt(endTokens.get(2));
            } catch (Exception e) {
                Util.print_exception("Invalid end date: " + end, e, log);
                return docs;
            }
        }

        return new LinkedHashSet<DatedDocument>(IndexUtils.selectDocsByDateRange((Collection) docs, startYear, startMonth, startDate, endYear, endMonth, endDate));
    }

    private static Pair<Set<EmailDocument>, Set<Blob>> updateForAttachmentType(Set<EmailDocument> docs, Set<Blob> blobs, Multimap<String, String> params) {

        String attachmentType = getParam(params, "attachmentType"), attachmentExtension = getParam(params, "attachmentExtension");
        if (Util.nullOrEmpty(attachmentType) && Util.nullOrEmpty(attachmentExtension))
            return new Pair<>(docs, blobs);

        Set<String> attachmentTypes = new LinkedHashSet<>();

        if (!Util.nullOrEmpty(attachmentType)) {
            attachmentType = attachmentType.replaceAll(",", ";"); // multiselect picker gives us , separated, convert it to ;
            attachmentTypes.addAll (splitFieldForOr(attachmentType));
        }

        if (!Util.nullOrEmpty(attachmentExtension)) {
            attachmentTypes.addAll (splitFieldForOr(attachmentExtension));
        }

        Set<EmailDocument> resultDocs = new LinkedHashSet<>();
        Set<Blob> resultBlobs = new LinkedHashSet<>();;

        for (EmailDocument ed : docs) {
            List<Blob> attachments = ed.attachments;
            if (Util.nullOrEmpty(attachments))
                continue;

            for (Blob b : attachments) {
                if (b.filename != null) {
                    String extension = Util.getExtension(b.filename);
                    if (extension != null) {
                        extension = extension.toLowerCase();
                        if (attachmentTypes.contains(extension)) {
                            resultDocs.add(ed);
                            resultBlobs.add(b);
                        }
                    }
                }
            }
        }

        return new Pair<>(resultDocs, resultBlobs);
    }

    private static Pair<Set<EmailDocument>, Set<Blob>> updateForAttachmentName(Set<EmailDocument> docs, Set<Blob> blobs, Multimap<String, String> params) {

        String attachmentFilename = getParam(params, "attachmentFilename");
        if (Util.nullOrEmpty(attachmentFilename))
            return new Pair<>(docs, blobs);

        Set<String> attachmentFilenames = splitFieldForOr(attachmentFilename);

        // TO DO : handle REGEX
        Set<EmailDocument> resultDocs = new LinkedHashSet<>();
        Set<Blob> resultBlobs = new LinkedHashSet<>();;

        for (EmailDocument ed : docs) {
            List<Blob> attachments = ed.attachments;
            if (Util.nullOrEmpty(attachments))
                continue;

            for (Blob b : attachments) {
                if (!Util.nullOrEmpty(b.filename)) {
                    if (attachmentFilenames.contains(b.filename.toLowerCase())) {
                        resultDocs.add(ed);
                        resultBlobs.add(b);
                    }
                }
            }
        }

        return new Pair<>(resultDocs, resultBlobs);
    }

    private static Pair<Set<EmailDocument>, Set<Blob>> updateForAttachmentSize(Set<EmailDocument> docs, Set<Blob> blobs, Multimap<String, String> params) {

        long KB = 1024;

        String attachmentFilesize = getParam(params, "attachmentFilesize");
        if (Util.nullOrEmpty(attachmentFilesize))
            return new Pair<>(docs, blobs);

        // TO DO : handle REGEX
        Set<EmailDocument> resultDocs = new LinkedHashSet<>();
        Set<Blob> resultBlobs = new LinkedHashSet<>();;

        for (EmailDocument ed : docs) {
            List<Blob> attachments = ed.attachments;
            if (Util.nullOrEmpty(attachments))
                continue;

            for (Blob b : attachments) {
                long size = b.getSize();
                // attachmentFilesizes are hardcoded -- could make it more flexible if needed in the future
                boolean include = ("1".equals(attachmentFilesize) && size < 5 * KB) ||
                        ("2".equals(attachmentFilesize) && size >= 5 * KB && size <= 20 * KB) ||
                        ("3".equals(attachmentFilesize) && size >= 20 * KB && size <= 100 * KB) ||
                        ("4".equals(attachmentFilesize) && size >= 100 * KB && size <= 2 * KB * KB) ||
                        ("5".equals(attachmentFilesize) && size >= 2 * KB * KB);
                if (include) {
                    resultDocs.add(ed);
                    resultBlobs.add(b);
                }
            }
        }

        return new Pair<>(resultDocs, resultBlobs);
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
    public static Pair<Collection<Document>, Collection<Blob>> selectDocsAndBlobs(Archive archive, Multimap<String, String> params, boolean or_not_and) throws UnsupportedEncodingException
    {
        // below are all the controls for selecting docs

        Set<Document> resultDocs = new LinkedHashSet<>(archive.getAllDocs());
        Set<Blob> resultBlobs = null;

        String term = getParam(params, "term");
        if (!Util.nullOrEmpty(term)) {
            Pair<Set<Document>, Set<Blob>> p = searchForTerm(archive, params, term);
            resultDocs = p.getFirst();
            resultBlobs = p.getSecond();
        }

        // attachments
        Pair<Set<EmailDocument>, Set<Blob>> p = updateForAttachmentType((Set) resultDocs, resultBlobs, params);
        p = updateForAttachmentName((Set) p.first, p.second, params);
        p = updateForAttachmentSize((Set) p.first, p.second, params);
        resultDocs = (Set) p.getFirst();
        resultBlobs = p.getSecond();

        // resultDocs = (Collection) updateForEntities((Collection) resultDocs, params);
        resultDocs = (Set) updateForCorrespondents(archive.addressBook, (Set) resultDocs, params);
        resultDocs = (Set) updateForMailingListState(archive.addressBook, (Set) resultDocs, params);
        resultDocs = (Set) updateForEmailDirection(archive.addressBook, (Set) resultDocs, params);

        resultDocs = (Set) updateForEmailSource((Set) resultDocs, params);
        resultDocs = (Set) updateForFolder((Set) resultDocs, params);
        resultDocs = (Set) updateForFlags((Set) resultDocs, params);

      //  resultBlobs = (Collection) updateForAttachments((Collection) resultDocs, params);
        resultDocs = (Set) updateForDateRange((Set) resultDocs, params);
      //  resultDocs = (Collection) updateForLexicons((Collection) resultDocs, params);

        // now only keep blobs that belong to resultdocs

        return new Pair<Collection<Document>, Collection<Blob>>(resultDocs, resultBlobs);
    }
}
