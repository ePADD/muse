package edu.stanford.muse.index;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.Config;
import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.datacache.BlobStore;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.email.MailingList;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * VIP class. Performs various search functions.
 */
public class Searcher {
    private static Log log = LogFactory.getLog(Searcher.class);
    private static final long KB = 1024;
    private static final char OR_DELIMITER = ';'; // used to separate parts of fields that can have multipled OR'ed clauses

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

    /* Top level entry point for searches from places that have a HTTP Req (example use: called from browse.jsp).
       Searches the given archive based on the params present in the given http req */
    public static Pair<Collection<Document>, Collection<Blob>> searchDocs (Archive archive, HttpServletRequest request) throws IOException, FileUploadException {

        // convert req. params to a multimap, so that the rest of the code doesn't have to deal with httprequest directly
        Multimap<String, String> params = LinkedHashMultimap.create();
        {
            if (true) {
                // regular file encoding
                Enumeration<String> paramNames = request.getParameterNames();

                while (paramNames.hasMoreElements()) {
                    String param = paramNames.nextElement();
                    String[] vals = request.getParameterValues(param);
                    if (vals != null)
                        for (String val : vals)
                            params.put(param, JSPHelper.convertRequestParamToUTF8(val));
                }
            }
        }

        return selectDocsAndBlobs(archive, params);
    }

    /** returns a single value for the given key */
    private static String getParam(Multimap<String, String> params, String key) {
        Collection<String> values = params.get(key);
        if (values == null || values.size() == 0)
            return null;
        return values.iterator().next();
    }


    /** returns multiple value for the given key */
    private static Collection<String> getParams(Multimap<String, String> params, String key) {
        Collection<String> values = params.get(key);
        if (values == null || values.size() == 0)
            return null;
        return values;
    }

    /** returns docs matching the given regex term (currently full body only, no attachments)
     * @return
     */
    public static Set<Document> searchForRegexTerm(Archive archive, Set<Document> docs, String term) {
        // go in the order subject, body, attachment
        Set<Document> docsForTerm = new LinkedHashSet<>();
        Indexer.QueryOptions options = new Indexer.QueryOptions();
        options.setQueryType(Indexer.QueryType.REGEX);

        docsForTerm.addAll(archive.docsForQuery(term, options));
        docsForTerm.retainAll (docs); // keep only those docs that are passed in

        return docsForTerm;
    }

    /** returns docs and blobs matching the given term.
     *
     * @param archive archive to search
     * @param params params contains controls for termSubject/termBody/termAttachments/termOriginalBody. the desired field to search should be set to "on".
     * @param term term to search for
     * @return
     */
    public static Pair<Set<Document>, Set<Blob>> searchForTerm(Archive archive, Multimap<String, String> params, String term) {
        // go in the order subject, body, attachment
        Set<Document> docsForTerm = new LinkedHashSet<>();
        Set<Blob> blobsForTerm = new LinkedHashSet<>();

        if ("on".equals(getParam(params, "termSubject"))) {
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.SUBJECT);
            docsForTerm.addAll(archive.docsForQuery(term, options));
        }

        if ("on".equals(getParam(params, "termBody"))) {
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.FULL);
            docsForTerm.addAll(archive.docsForQuery(term, options));
        } else if ("on".equals(getParam(params, "termOriginalBody"))) { // this is an else because we don't want to look at both body and body original
            Indexer.QueryOptions options = new Indexer.QueryOptions();
            options.setQueryType(Indexer.QueryType.ORIGINAL);
            docsForTerm.addAll(archive.docsForQuery(term, options));
        }

        if ("on".equals(getParam(params, "termAttachments"))) {
            blobsForTerm = archive.blobsForQuery(term);
            Set<Document> blobDocsForTerm = (Set<Document>) EmailUtils.getDocsForAttachments((Collection) archive.getAllDocs(), blobsForTerm);
            docsForTerm.addAll(blobDocsForTerm);
        }


        return new Pair<>(docsForTerm, blobsForTerm);
    }

    /** splits by semicolons, lowercases, trims spaces; e.g. given "A; b" returns ["a", "b"].
     * This syntax is followed by fields that can contain an OR specification. */
    public static Set<String> splitFieldForOr(String s) {
        Collection<String> tokens = Util.tokenize(s, Character.toString(OR_DELIMITER));
        Set<String> result = new LinkedHashSet<>();
        for (String token: tokens)
            result.add(token.toLowerCase().trim());
        return result;
    }

    /** returns only the docs from amongst the given ones that matches the query specification for flags.
     * @param docs the set of docs to consider
     * @param params specifies boolean flags reviewed/doNotTransfer/transferWithRestrictions/inCart (should be set to yes, no or either) and annotation (params[annotation] should be contained modulo case in a message's annotation to match. All these search criteria are anded.
     * @return those among the set of input docs that match the params specification
     */
    private static Set<EmailDocument> filterForFlags (Set<EmailDocument> docs, Multimap<String, String> params) {
        String reviewedValue = getParam(params, "reviewed");
        if (!"either".equals(reviewedValue) && !Util.nullOrEmpty(reviewedValue)) {
            Set<EmailDocument> newDocs = new LinkedHashSet<>();
            for (EmailDocument ed: docs)
                if (ed.reviewed == "yes".equals(reviewedValue))
                   newDocs.add(ed);
            docs = newDocs;
        }

        String dntValue = getParam(params, "doNotTransfer");
        if (!"either".equals(dntValue) && !Util.nullOrEmpty(dntValue)) {
            Set<EmailDocument> newDocs = new LinkedHashSet<>();
            for (EmailDocument ed: docs)
                if (ed.doNotTransfer == "yes".equals(dntValue))
                    newDocs.add(ed);
            docs = newDocs;
        }

        String twrValue = getParam(params, "transferWithRestrictions");
        if (!"either".equals(twrValue) & !Util.nullOrEmpty(twrValue)) {
            Set<EmailDocument> newDocs = new LinkedHashSet<>();
            for (EmailDocument ed: docs)
                if (ed.transferWithRestrictions == "yes".equals(twrValue))
                    newDocs.add(ed);
            docs = newDocs;
        }


        String inCartValue = getParam(params, "inCart");
        if (!"either".equals(inCartValue) & !Util.nullOrEmpty(inCartValue)) {
            Set<EmailDocument> newDocs = new LinkedHashSet<>();
            for (EmailDocument ed: docs)
                if (ed.addedToCart == "yes".equals(inCartValue))
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

    private static Set<EmailDocument> filterForEmailDirection(AddressBook addressBook, Set<EmailDocument> docs, Multimap<String, String> params) {

        String val = getParam(params, "direction");
        if ("either".equals(val) || Util.nullOrEmpty(val))
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
        return result;
    }

    /** returns only the docs matching the given contact id. used by facets, correspondents table, etc */
    private static Set<EmailDocument> filterForContactId(Set<EmailDocument> docs, AddressBook ab, String cid) {
        String correspondentName = null;
        int contactId = -1;
        try { contactId = Integer.parseInt(cid); } catch (NumberFormatException nfe) { }
        if (contactId >= 0) {
            Contact c = ab.getContact(contactId);
            String name = c.pickBestName();
            correspondentName = name;
        }
        return filterForCorrespondents(docs, ab, correspondentName, true, true, true, true); // for contact id, all the 4 fields - to/from/cc/bcc are enabled
    }

    /** version of filterForCorrespondents which reads settings from params. correspondent name can have the OR separator */
    private static Set<EmailDocument> filterForCorrespondents(Set<EmailDocument> docs, AddressBook ab, Multimap<String, String> params) {

        String correspondentsStr = getParam(params, "correspondent");
        boolean checkToField = "on".equals(getParam(params, "correspondentTo"));
        boolean checkFromField = "on".equals(getParam(params, "correspondentFrom"));
        boolean checkCcField = "on".equals(getParam(params, "correspondentCc"));
        boolean checkBccField = "on".equals(getParam(params, "correspondentBcc"));

        if (Util.nullOrEmpty(correspondentsStr))
            return docs;

        return filterForCorrespondents(docs, ab, correspondentsStr, checkToField, checkFromField, checkCcField, checkBccField);
    }

    /** returns only the docs where the name or email address in the given field matches correspondentsStr in the given field(s).
     * correspondentsStr can be or-delimited and specify multiple strings. */
    public static Set<EmailDocument> filterForCorrespondents(Collection<EmailDocument> docs, AddressBook ab, String correspondentsStr, boolean checkToField, boolean checkFromField, boolean checkCcField, boolean checkBccField) {

        Set<EmailDocument> result = new LinkedHashSet<>();
        Set<Contact> searchedContacts = new LinkedHashSet<>();
        Set<String> correspondents = splitFieldForOr(correspondentsStr);

        for (String s : correspondents) {
            Contact c = ab.lookupByEmailOrName(s); // this lookup will normalize, be case-insensitive, etc.
            if (c != null)
                searchedContacts.add(c);
        }

        for (EmailDocument ed : docs) {
            Set<InternetAddress> addressesInMessage = new LinkedHashSet<>(); // only lookup the fields (to/cc/bcc/from) that have been enabled
            if (checkToField && !Util.nullOrEmpty(ed.to))
                addressesInMessage.addAll((List) Arrays.asList(ed.to));
            if (checkFromField && !Util.nullOrEmpty(ed.from))
                addressesInMessage.addAll((List) Arrays.asList(ed.from));
            if (checkCcField && !Util.nullOrEmpty(ed.cc))
                addressesInMessage.addAll((List) Arrays.asList(ed.cc));
            if (checkBccField && !Util.nullOrEmpty(ed.bcc))
                addressesInMessage.addAll((List) Arrays.asList(ed.bcc));

            for (InternetAddress a : addressesInMessage) {
                Contact c = ab.lookupByEmail(a.getAddress());
                if (c == null)
                    c = ab.lookupByName(a.getPersonal());
                if (c != null && searchedContacts.contains(c)) {
                    result.add(ed);
                    break;
                }
            }
        }
        return result;
    }

    /* returns only the docs matching params["docId"] -- which could be or-delimiter separated to match multiple docs.
    * used for attachment listing. Consider removing this method in favour of message Ids below. */
    private static Set<EmailDocument> filterForDocId (Archive archive, Set<EmailDocument> docs, Multimap<String, String> params) {

        Collection<String> docIds = params.get("docId");
        if (Util.nullOrEmpty(docIds))
            return docs;

        Set<EmailDocument> resultDocs = new LinkedHashSet<>();

        for (String docId: docIds) {
            EmailDocument ed = archive.docForId (docId);
            if (ed != null)
                resultDocs.add(archive.docForId(docId));
        }

        return resultDocs;
    }

    /** returns only the docs matching params[uniqueId], which could have multiple uniqueIds separated by the OR separator */
    private static Set<EmailDocument> filterForMessageId (Set<EmailDocument> docs, Multimap<String, String> params) {
        String val = getParam(params, "uniqueId");
        if (Util.nullOrEmpty(val))
            return docs;

        Set<String> messageIds = splitFieldForOr(val);

        Set<EmailDocument> resultDocs = new LinkedHashSet<>();
        for (EmailDocument ed : docs)
        {
            String messageSig = Util.hash (ed.getSignature()); // should be made more efficient by storing the hash inside the ed
            if (!Util.nullOrEmpty (messageSig))
                if (messageIds.contains(messageSig))
                    resultDocs.add(ed);
        }
        return resultDocs;
    }

    /** returns only the docs matching per params[mailingListState].
     * If this value is either, no filtering is done.
     * if set to yes, only docs with at least one address matching a mailing list are returned. */
    private static Set<EmailDocument> filterForMailingListState(AddressBook ab, Set<EmailDocument> docs, Multimap<String, String> params) {
        String mailingListState = getParam(params, "mailingListState");
        if ("either".equals(mailingListState) || Util.nullOrEmpty(mailingListState))
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

    /** returns only the docs matching params[emailSource] */
    private static Set<EmailDocument> filterForEmailSource (Set<EmailDocument> docs, Multimap<String, String> params) {
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
        return result;
    }

    /* returns only the docs matching params[folder] (can be or-delimiter separated) */
    private static Set<EmailDocument> filterForFolder(Set<EmailDocument> docs, Multimap<String, String> params) {
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
        return result;
    }

    /** returns only the docs containing params[entity] (can be or-delimiter separated) */
    private static Set<EmailDocument> updateForEntities(Archive archive, Set<EmailDocument> docs, Multimap<String, String> params) {
        String val = getParam(params, "entity");
        if (Util.nullOrEmpty(val))
            return docs;

        Set<String> entities = splitFieldForOr(val);
        Set<EmailDocument> result = new LinkedHashSet<>();


        for (EmailDocument ed : docs)
        {
            Set<String> entitiesInThisDoc = new LinkedHashSet<>();
            // question: should we look at fine entities intead?
            try {
                entitiesInThisDoc.addAll(Arrays.asList(archive.getAllNamesInDoc(ed, true)).stream().map(n->n.text).collect(Collectors.toSet()));
            } catch (IOException ioe) { Util.print_exception("Error in reading entities", ioe, log); }

            entitiesInThisDoc = entitiesInThisDoc.parallelStream().map (s -> s.toLowerCase()).collect(Collectors.toSet());
            entitiesInThisDoc.retainAll(entities);
            if (entitiesInThisDoc.size() > 0)
                result.add(ed);
        }
        return result;
    }

    /** returns only those docs with attachments matching params[attachmentEntity] (this field is or-delimiter separated)
     * Todo: review usage of this and BlobStore.getKeywordsForBlob() */
    private static Set<EmailDocument> filterForAttachmentEntities(Archive archive, Set<EmailDocument> docs, Multimap<String, String> params) {
        Set<EmailDocument> resultDocs = new LinkedHashSet<>();
        Set<Blob> resultBlobs = new LinkedHashSet<>(); // currently not used
        String val = getParam(params, "attachmentEntity");
        if (Util.nullOrEmpty(val))
            return docs;

        val = val.toLowerCase();
        Set<String> entities = splitFieldForOr(val);
        BlobStore blobStore = archive.blobStore;

        nextDoc:
        for (EmailDocument ed : docs)
        {
            Collection<Blob> blobs = ed.attachments;
            for (Blob blob: blobs) {
                Collection<String> keywords = blobStore.getKeywordsForBlob(blob);
                if (keywords != null)
                    for (String keyword : keywords)
                        if (entities.contains(keyword.toLowerCase())) {
                            resultDocs.add (ed);
                            resultBlobs.add(blob); // select the blob
                            continue nextDoc;
                        }
            }
        }
        return resultDocs;
    }

    private static Set<EmailDocument> filterForEntityType(Archive archive, Set<EmailDocument> docs, Multimap<String, String> params) {
        String val = getParam(params, "entityType");
        if (Util.nullOrEmpty(val))
            return docs;

        Set<String> neededTypes = splitFieldForOr(val);

        Set<Document> docsWithNeededTypes = new LinkedHashSet<>();
        for (String type: neededTypes) {
            short code = Short.parseShort(type);
            docsWithNeededTypes.addAll(archive.getDocsWithEntityType(code));
        }

        docs.retainAll(docsWithNeededTypes);
        return docs;
   }

    /** this method is a little more specific than attachmentFilename, which only matches the real filename.
     * it matches a specific attachment, including its numeric blobstore prefix.
     * used when finding message(s) belonging to image wall
     */
    private static Set<EmailDocument> filterForAttachmentNames(Archive archive, Set<EmailDocument> docs, Multimap<String, String> params) {
        // this code was taken from old JSPHelper searcher
        try {
            Collection<String> attachmentTailsList = params.get("attachment");
            if (Util.nullOrEmpty(attachmentTailsList))
                return docs;
            String[] attachmentTails = attachmentTailsList.toArray(new String[attachmentTailsList.size()]);
            attachmentTails = JSPHelper.convertRequestParamsToUTF8(attachmentTails);
            Collection<Blob> blobsForAttachments = IndexUtils.getBlobsForAttachments(docs, attachmentTails, archive.blobStore);
            Collection<EmailDocument> docsForAttachments = EmailUtils.getDocsForAttachments((Collection) docs, blobsForAttachments);
            return new LinkedHashSet<>(docsForAttachments);
        } catch (Exception e)  { Util.print_exception ("Error processing attachment names in search", e, log); }
        return docs;
    }

    private static Set<DatedDocument> filterForDateRange(Set<DatedDocument> docs, Multimap<String, String> params) {
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

    /** will look in the given docs for a message with an attachment that satisfies all the requirements.
     * the set of such messages, along with the matching blobs is returned
     * if no requirements, Pair<docs, null> is returned.
     *
     * @param docs
     * @param params
     * @return
     */
    private static Pair<Set<EmailDocument>, Set<Blob>> updateForAttachments(Set<EmailDocument> docs, Multimap<String, String> params) {


        String neededFilesize = getParam(params, "attachmentFilesize");
        String neededFilename = getParam(params, "attachmentFilename");
        Collection<String> neededTypeStr = getParams(params, "attachmentType"); // this can come in as a single parameter with multiple values (in case of multiple selections by the user)
        String neededExtensionStr = getParam(params, "attachmentExtension");

        if (Util.nullOrEmpty(neededFilesize) && Util.nullOrEmpty(neededFilename) && Util.nullOrEmpty(neededTypeStr) && Util.nullOrEmpty(neededExtensionStr)) {
            return new Pair<>(docs, null);
        }

        // set up the file names incl. regex pattern if applicable
        String neededFilenameRegex = getParam(params, "attachmentFilenameRegex");
        Set<String> neededFilenames = null;
        Pattern filenameRegexPattern = null;
        if ("on".equals(neededFilenameRegex) && !Util.nullOrEmpty(neededFilename)) {
            filenameRegexPattern = Pattern.compile(neededFilename);
        } else {
            if (!Util.nullOrEmpty(neededFilename)) // will be in lower case
                neededFilenames = splitFieldForOr(neededFilename);
        }

        // set up the extensions
        Set<String> neededExtensions = null; // will be in lower case
        if (!Util.nullOrEmpty(neededTypeStr) || !Util.nullOrEmpty(neededExtensionStr))
        {
            // compile the list of all extensions from type (audio/video, etc) and explicitly provided extensions
            neededExtensions = new LinkedHashSet<>();
            if (!Util.nullOrEmpty(neededTypeStr)) {
                // will be something like "mp3;ogg,avi;mp4" multiselect picker gives us , separated between types, convert it to ;
                for (String s: neededTypeStr)
                    neededExtensions.addAll(splitFieldForOr(s));
            }
            if (!Util.nullOrEmpty(neededExtensionStr)) {
                neededExtensions.addAll(splitFieldForOr(neededExtensionStr));
            }
        }



        Set<EmailDocument> resultDocs = new LinkedHashSet<>();
        Set<Blob> resultBlobs = new LinkedHashSet<>();;

        for (EmailDocument ed : docs) {
            List<Blob> attachments = ed.attachments;
            if (Util.nullOrEmpty(attachments))
                continue;

            for (Blob b : attachments) {
                // does it satisfy all 3 requirements? if we find any condition that it set and doesn't match, bail out of the loop to the next blob
                // of course its kinda pointless to specify extension if filename is already specified
                // 1. filename matches?
                if (filenameRegexPattern == null) {
                    // non-regex check
                    if (neededFilenames != null && (b.filename == null || !(neededFilename.contains(b.filename))) )
                        continue;
                } else {
                    // regex check
                    if (!Util.nullOrEmpty(neededFilename)) {
                        if (b.filename == null)
                            continue;
                        if (!filenameRegexPattern.matcher(b.filename).find()) // use find rather than matches because we want partial match on the filename, doesn't have to be full match
                            continue;
                    }
                }

                // 2. extension matches?
                //a variable to select if the extensions needed contain others.
                boolean isOtherSelected = neededExtensions.contains("others");
                //get the options that were displayed for attachment types. This will be used to select attachment extensions if the option 'other'
                //was selected by the user in the drop down box of export.jsp.
                List<String> attachmentTypeOptions = Config.attachmentTypeToExtensions.values().stream().map(x->Util.tokenize(x,";")).flatMap(col->col.stream()).collect(Collectors.toList());

                if (neededExtensions != null) {
                    if (b.filename == null)
                        continue; // just over-defensive, if no name, effectively doesn't match
                    String extension = Util.getExtension(b.filename);
                    if (extension == null)
                        continue;
                    extension = extension.toLowerCase();
                    //Proceed to add this attachment only if either
                    //1. other is selected and this extension is not present in the list attachmentOptionType, or
                    //2. this extension is present in the variable neededExtensions [Q. What if there is a file with extension .others?]
                    boolean firstcondition = isOtherSelected && !attachmentTypeOptions.contains(extension);
                    boolean secondcondition = neededExtensions.contains(extension);
                    if (!firstcondition && !secondcondition)
                        continue;
                }

                // 3. size matches?
                long size = b.getSize();

                /*
                // these attachmentFilesizes parameters are hardcoded -- could make it more flexible if needed in the future
                // "1".."5" are the only valid filesizes. If none of these, this parameter not set and we can include the blob
                if ("1".equals(neededFilesize) || "2".equals(neededFilesize) || "3".equals(neededFilesize) ||"4".equals(neededFilesize) ||"5".equals(neededFilesize)) { // any other value, we ignore this param
                    boolean include = ("1".equals(neededFilesize) && size < 5 * KB) ||
                            ("2".equals(neededFilesize) && size >= 5 * KB && size <= 20 * KB) ||
                            ("3".equals(neededFilesize) && size >= 20 * KB && size <= 100 * KB) ||
                            ("4".equals(neededFilesize) && size >= 100 * KB && size <= 2 * KB * KB) ||
                            ("5".equals(neededFilesize) && size >= 2 * KB * KB);
                }
                */
                boolean include = filesizeCheck (neededFilesize, size);
                if (!include)
                    continue;
                // if we reached here, all conditions must be satisfied
                resultDocs.add(ed);
                resultBlobs.add(b);
            }
        }

        return new Pair<>(resultDocs, resultBlobs);
    }

    /** returns true if the filesize satisfies the constraint. neededFileSize is as defined in the adv. search form.
     * we probably need to avoid hardcoding these limits */
    private static boolean filesizeCheck (String neededFilesize, long size) {
        // these attachmentFilesizes parameters are hardcoded -- could make it more flexible if needed in the future
        // "1".."5" are the only valid filesizes. If none of these, this parameter not set and we can include the blob
        if ("1".equals(neededFilesize) || "2".equals(neededFilesize) || "3".equals(neededFilesize) || "4".equals(neededFilesize) || "5".equals(neededFilesize)) { // any other value, we ignore this param
            boolean include = ("1".equals(neededFilesize) && size < 5 * KB) ||
                    ("2".equals(neededFilesize) && size >= 5 * KB && size <= 20 * KB) ||
                    ("3".equals(neededFilesize) && size >= 20 * KB && size <= 100 * KB) ||
                    ("4".equals(neededFilesize) && size >= 100 * KB && size <= 2 * KB * KB) ||
                    ("5".equals(neededFilesize) && size >= 2 * KB * KB);
            return include;
        }
        return true;
    }

    private static Set<DatedDocument> filterDocsByDate (HttpServletRequest request, Set<DatedDocument> docs) {
        String start = request.getParameter("startDate"), end = request.getParameter ("endDate");

        if (Util.nullOrEmpty(start) && Util.nullOrEmpty(end))
            return docs;

        int startYear = -1, startMonth = -1, startDate = -1, endYear = -1, endMonth = -1, endDate = -1;
        if (!Util.nullOrEmpty(start) || !Util.nullOrEmpty(end)) {
            try {
                List<String> startTokens = Util.tokenize(start, "-");
                startYear = Integer.parseInt(startTokens.get(0));
                startMonth = Integer.parseInt(startTokens.get(1));
                startDate = Integer.parseInt(startTokens.get(2));
            } catch (Exception e) {
                Util.print_exception("Invalid start date: " + start, e, JSPHelper.log);
                return docs;
            }

            try {
                List<String> endTokens = Util.tokenize(end, "-");
                endYear = Integer.parseInt(endTokens.get(0));
                endMonth = Integer.parseInt(endTokens.get(1));
                endDate = Integer.parseInt(endTokens.get(2));
            } catch (Exception e) {
                Util.print_exception("Invalid end date: " + end, e, JSPHelper.log);
                return docs;
            }
        }
        return new LinkedHashSet<>(IndexUtils.selectDocsByDateRange((Collection) docs, startYear, startMonth, startDate, endYear, endMonth, endDate));
    }

    /** this map is used only by attachments page right now, not advanced search. TODO: make adv. search page also use it */
   public static List<Pair<Blob, EmailDocument>> selectBlobs (Archive archive, HttpServletRequest request) {
        Collection<Document> docs = archive.getAllDocs();

        String neededFilesize = request.getParameter ("attachmentFilesize");
        String extensions[] = request.getParameterValues("attachmentExtension");
        Set<String> extensionsToMatch = new LinkedHashSet<>(); // should also have lower-case strings, no "." included

        if (!Util.nullOrEmpty(extensions)) {
            extensionsToMatch = new LinkedHashSet<>();
            for (String s: extensions)
                extensionsToMatch.add (s.trim().toLowerCase());
        }

        // or given extensions with extensions due to attachment type
        String types[] = request.getParameterValues ("attachmentType"); // this will have more semicolon separated extensions
        if (!Util.nullOrEmpty(types)) {
            for (String t: types) {
                String exts = Config.attachmentTypeToExtensions.get(t);
                if (exts == null)
                    exts=t;
                    //continue;
                //Front end should uniformly pass attachment types as extensions like mp3;mov;ogg etc. Earlier it was passing vide, audio, doc etc.
                //In order to accommodate both cases we first check if there is ampping from the extension type to actual extensions using .get(t)
                //if no such mapping is present then we assume that the input extension types are of the form mp3;mov;ogg and work on that.
                String[] components = exts.split (";");
                for (String c: components) {
                    extensionsToMatch.add (c);
                }
            }
        }

       //a variable to select if the extensions needed contain others.
       boolean isOtherSelected = extensionsToMatch.contains("others");
       //get the options that were displayed for attachment types. This will be used to select attachment extensions if the option 'other'
       //was selected by the user in the drop down box of export.jsp.
       List<String> attachmentTypeOptions = Config.attachmentTypeToExtensions.values().stream().map(x->Util.tokenize(x,";")).flatMap(col->col.stream()).collect(Collectors.toList());

       List<Pair<Blob, EmailDocument>> allAttachments = new ArrayList<>();

        Collection<EmailDocument> eDocs = (Collection) filterDocsByDate (request, new HashSet<>((Collection) docs));
        for (EmailDocument doc : eDocs) {
            List<Blob> blob = doc.attachments;
            if (blob != null)
                for (Blob b: blob) {
                    if (!Searcher.filesizeCheck (neededFilesize, b.getSize()))
                        continue;

                    if (!(Util.nullOrEmpty (extensionsToMatch))) {
                        Pair<String, String> pair = Util.splitIntoFileBaseAndExtension(b.getName());
                        String ext = pair.getSecond();
                        if (ext == null)
                            continue;
                        ext = ext.toLowerCase();
                        //Proceed to add this attachment only if either
                        //1. other is selected and this extension is not present in the list attachmentOptionType, or
                        //2. this extension is present in the variable neededExtensions [Q. What if there is a file with extension .others?]
                        boolean firstcondition = isOtherSelected && !attachmentTypeOptions.contains(ext);
                        boolean secondcondition = extensionsToMatch.contains(ext);
                        if (!firstcondition && !secondcondition)
                            continue;
                    }

                    // ok, we've survived all filters, add b
                    allAttachments.add(new Pair<>(b, doc));
                }
        }

        Collections.reverse (allAttachments); // reverse, so most recent attachment is first
        return allAttachments;
    }

    private static Set<Document> filterForLexicons(Archive archive, Set<Document> docs, Multimap<String, String> params) {
        String lexiconName = getParam(params, "lexiconName");

        Lexicon lex = null;
        if (Util.nullOrEmpty(lexiconName))
            return docs;
        lex = archive.getLexicon(lexiconName);
        if (lex == null)
            return docs;

        String category = getParam(params, "lexiconCategory");
        if (Util.nullOrEmpty(category))
            return docs;

        Set<Document> result = (Set) lex.getDocsWithSentiments(new String[]{category}, archive.indexer, docs, -1, false/* request.getParameter("originalContentOnly") != null */, category);
        return result;
    }

    private static Set<Document> filterForSensitiveMessages(Archive archive, Set<Document> docs, Multimap<String, String> params) {
        String isSensitive = getParam(params, "sensitive");

        if ("true".equals(isSensitive)) {
            Indexer.QueryType qt = null;
            qt = Indexer.QueryType.REGEX;
            Collection<Document> sensitiveDocs = archive.docsForQuery(-1 /* cluster num -- not used */, qt);
            docs.retainAll(sensitiveDocs);

            for (Document d: sensitiveDocs) {
                System.out.println ("MessageHash: " + Util.hash (((EmailDocument) d).getSignature()));
            }
        }
        return docs;
    }


    /**
     * VIP method. Top level API entry point to perform the search in the given archive according to params in the given multimap.
     * params specifies (anded) queries based on term, sentiment (lexicon), person, attachment, docNum, etc.
       returns a collection of docs and blobs matching the query.

     * note2: performance can be improved. e.g., if in AND mode, searches that
     * iterate through documents such as selectDocByTag, getBlobsForAttachments, etc., can take the intermediate resultDocs rather than allDocs.
     * set intersection/union can be done in place to the intermediate resultDocs rather than create a new collection.
     * getDocsForAttachments can be called on the combined result of attachments and attachmentTypes search, rather than individually.

     * note3: should we want options to allow user to choose whether to search only in emails, only in attachments, or both?
     * also, how should we allow variants in combining multiple conditions.
     * there will be work in UI too.

     * note4: the returned resultBlobs may not be tight, i.e., it may include blobs from docs that are not in the returned resultDocs.
     * but for docs that are in resultDocs, it should not include blobs that are not hitting.
     * these extra blobs will not be seen since we only use this info for highlighting blobs in resultDocs.
     */
    public static Pair<Collection<Document>, Collection<Blob>> selectDocsAndBlobs(Archive archive, Multimap<String, String> params) throws UnsupportedEncodingException
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

        String regexTerm = getParam(params, "regexTerm");
        if (!Util.nullOrEmpty(regexTerm)) {
            resultDocs = searchForRegexTerm(archive, resultDocs, regexTerm);
        }

        Pair<Set<EmailDocument>, Set<Blob>> p = updateForAttachments((Set) resultDocs, params);

        resultDocs = (Set) p.getFirst();
        resultDocs = (Set) filterForAttachmentNames(archive, (Set) resultDocs, params); // for exact names -- when clicking on image in attachment wall or listing
        resultDocs = (Set) filterForAttachmentEntities(archive, (Set) resultDocs, params);

        // resultBlobs will be a *union* (not intersection) of blobs that hit above (in text search) and these blobs that satisfy other criteria
        // resultBlobs are really used for highlighting the attachment
        if (p.getSecond() != null) {
            if (resultBlobs != null)
                resultBlobs.addAll(p.getSecond());
            else
                resultBlobs = p.getSecond();
        }

        // resultDocs = (Collection) updateForEntities((Collection) resultDocs, params);
        resultDocs = (Set) filterForCorrespondents((Set) resultDocs, archive.addressBook, params);

        // contactIds are used for facets and from correspondents page etc.
        Collection<String> contactIds = params.get("contact");
        if (!Util.nullOrEmpty(contactIds)) {
            for (String cid : contactIds)
                resultDocs = (Set) filterForContactId((Set) resultDocs, archive.addressBook, cid);
        }

        resultDocs = (Set) filterForDocId(archive, (Set) resultDocs, params); // for clicking on message in attachment listing
        resultDocs = (Set) filterForMessageId((Set) resultDocs, params); // for message id field in adv. search

        resultDocs = (Set) filterForMailingListState(archive.addressBook, (Set) resultDocs, params);
        resultDocs = (Set) filterForEmailDirection(archive.addressBook, (Set) resultDocs, params);

        resultDocs = (Set) filterForEmailSource((Set) resultDocs, params);
        resultDocs = (Set) filterForFolder((Set) resultDocs, params);
        resultDocs = (Set) filterForFlags((Set) resultDocs, params);

        resultDocs = (Set) filterForDateRange((Set) resultDocs, params);
        resultDocs = (Set) filterForLexicons(archive, resultDocs, params);
        resultDocs = (Set) updateForEntities(archive, (Set) resultDocs, params); // searching by entity is probably the most expensive, so keep it near the end
        resultDocs = (Set) filterForEntityType(archive, (Set) resultDocs, params);

        //  we don't have sensitive messages now (based on PRESET_REGEX)
        // sensitive messages are just a special lexicon
        // resultDocs = (Set) filterForSensitiveMessages(archive, (Set) resultDocs, params);

        // now only keep blobs that belong to resultdocs

        // sort per the requirement
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

        List<Document> resultDocsList = new ArrayList<>(resultDocs);
        if (sortBy == Indexer.SortBy.CHRONOLOGICAL_ORDER)
            Collections.sort(resultDocsList);
        else if (sortBy == Indexer.SortBy.RECENT_FIRST) {
            Collections.sort(resultDocsList);
            Collections.reverse(resultDocsList);
        }

        return new Pair<>(new LinkedHashSet<>(resultDocsList), resultBlobs);
    }
}
