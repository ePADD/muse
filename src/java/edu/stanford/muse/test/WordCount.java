package edu.stanford.muse.test;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.webapp.SimpleSessions;

import java.io.IOException;
import java.util.Collection;

public class WordCount {

    // the command line parameter is the directory where the archive is stored.
    // The default place where the appraisal archive is stored is <HOME>/epadd-appraisal/user
    public static void main (String args[]) throws IOException {
        Archive archive = SimpleSessions.readArchiveIfPresent(args[0]);
        Collection<Document> emails = archive.getAllDocs();

        // now maintain the count of each word in the corpus
        for (Document email: emails) {
            String subject = email.description; // this is the email's subject
            String emailContent = archive.getContents(email, false); // this is the body of the email
            // count the words in emailContent
        }

        // print a sorted list of words in the archive, in descending order.
    }
}
