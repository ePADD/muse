package edu.stanford.muse.ie.matching;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.Searcher;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Span;
import edu.stanford.muse.util.Util;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class NameExpansion {

    private static boolean matchAgainstEmailContent(Archive archive, EmailDocument ed, Matches matchResults, String messageType, float score) {
        Set<String> allNames = new LinkedHashSet();
        Stream.of(archive.getEntitiesInDoc(ed, false)).map(Span::getText).forEach(allNames::add);
        Stream.of(archive.getEntitiesInDoc(ed, true)).map(Span::getText).forEach(allNames::add);
        Iterator it = allNames.iterator();

        String name;
        StringMatchType matchType;
        do {
            if (!it.hasNext()) {
                return false;
            }

            name = (String) it.next();
            matchType = Matches.match(matchResults.getMatchString(), name);
        } while (matchType == null || !matchResults.addMatch(name, score, matchType, (messageType != null ? messageType + " " : "") + " (message ID: " + Util.hash(ed.getSignature()) + ")", false));

        return true;
    }

    /* Given the string s in emailDocument ed, returns a matches object with candidates matching s */
    public static Matches getMatches(String s, Archive archive, EmailDocument ed, int maxResults) {
        Matches matches = new Matches(s, maxResults);
        AddressBook ab = archive.addressBook;
        List<Contact> contactsExceptSelf = ed.getParticipatingContactsExceptOwn(archive.addressBook);
        List<Contact> contacts = new ArrayList(contactsExceptSelf);
        contacts.add(ab.getContactForSelf());

        Iterator contactIt = contacts.iterator();

        String name;
        StringMatchType matchType;
        float score;
        outer:
        do {
            while (true) {
                Contact c;
                do {
                    if (!contactIt.hasNext()) {
                        if (matchAgainstEmailContent(archive, ed, matches, "Mentioned elsewhere in this message", 1.0F)) {
                            return matches;
                        }

                        synchronized (archive) {
                            if (ed.threadID == 0L) {
                                archive.assignThreadIds();
                            }
                        }

                        List<EmailDocument> messagesInThread = (List) archive.docsWithThreadId(ed.threadID);
                        contactIt = messagesInThread.iterator();

                        while (contactIt.hasNext()) {
                            EmailDocument messageInThread = (EmailDocument) contactIt.next();
                            if (matchAgainstEmailContent(archive, messageInThread, matches, "Mentioned in this thread", 0.9F)) {
                                return matches;
                            }
                        }

                        String correspondentsSearchStr = "";
                        contactIt = contactsExceptSelf.iterator();

                        Set docsWithTerm;
                        while (contactIt.hasNext()) {
                            c = (Contact) contactIt.next();
                            docsWithTerm = c.emails;
                            if (docsWithTerm.size() > 0) {
                                //language=JSON
                                correspondentsSearchStr = correspondentsSearchStr + (String) c.emails.iterator().next() + ";";
                            }
                        }

                        Set<EmailDocument> messagesWithSameCorrespondents = (Set) Searcher.filterForCorrespondents((Collection) archive.getAllDocs(), ab, correspondentsSearchStr, true, true, true, true);
                        Iterator var22 = messagesWithSameCorrespondents.iterator();

                        while (var22.hasNext()) {
                            EmailDocument messageWithSameCorrespondents = (EmailDocument) var22.next();
                            if (matchAgainstEmailContent(archive, messageWithSameCorrespondents, matches, "Mentioned in other messages with these correspondents", 0.8F)) {
                                return matches;
                            }
                        }

                        Multimap<String, String> params = LinkedHashMultimap.create();
                        params.put("termSubject", "on");
                        params.put("termBody", "on");
                        String term = s;
                        if (s.contains(" ") && (!s.startsWith("\"") || !s.endsWith("\""))) {
                            term = "\"" + s + "\"";
                        }

                        Pair<Set<Document>, Set<Blob>> p = Searcher.searchForTerm(archive, params, term);
                        docsWithTerm = (Set) p.getFirst();
                        Iterator var26 = docsWithTerm.iterator();

                        EmailDocument docWithTerm;
                        do {
                            if (!var26.hasNext()) {
                                return matches;
                            }

                            docWithTerm = (EmailDocument) var26.next();
                        }
                        while (!matchAgainstEmailContent(archive, docWithTerm, matches, "Mentioned elsewhere in this archive", 0.7F));

                        return matches;
                    }

                    c = (Contact) contactIt.next();
                } while (c.names == null);

                Iterator var10 = c.names.iterator();

                while (var10.hasNext()) {
                    name = (String) var10.next();
                    matchType = Matches.match(s, name);
                    if (matchType != null) {
                        score = 1.0F;
                        continue outer;
                    }
                }
            }
        } while (!matches.addMatch(name, score, matchType, "Name of a contact on this message", true));

        return matches;
    }
}

