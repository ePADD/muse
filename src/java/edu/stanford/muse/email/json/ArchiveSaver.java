package edu.stanford.muse.email.json;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.Util;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import java.io.*;
import java.util.List;
import java.util.Map;

/**
 * Created by sunchise on 04.06.17.
 */
public class ArchiveSaver {

    public void save(Archive archive) {
        String fileName = System.getProperty("user.home") + File.separator + "archive.json";
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        List<Document> allDocs = archive.getAllDocs();
        EmailNameAgregator emailNameAgregator = new EmailNameAgregator(allDocs);
        int i = 1;
        try (BufferedWriter stream = new BufferedWriter(new FileWriter(file))) {
            append(stream, "[");
            boolean fail = false;
            for (Document doc : allDocs) {
                if (i > 1 && !fail) {
                    append(stream, ",");
                }
                fail = false;
                final EmailDocument emailDocument = (EmailDocument) doc;
                String messageID = Util.hash (emailDocument.getSignature());
                Email email = new Email(messageID,
                        emailDocument.date,
                        true,
                        emailDocument.getSubject(),
                        emailDocument.from == null || emailDocument.from.length == 0 ? null : emailNameAgregator.getName(emailDocument.getFromEmailAddress()),
                        emailDocument.getFromEmailAddress());
                if (emailDocument.cc != null) {
                    for (Address address : emailDocument.cc) {
                        InternetAddress internetAddress = (InternetAddress) address;
                        email.addCc(emailNameAgregator.getName(internetAddress.getAddress()), internetAddress.getAddress());
                    }
                }
                if (emailDocument.bcc != null) {
                    for (Address address : emailDocument.bcc) {
                        InternetAddress internetAddress = (InternetAddress) address;
                        email.addCc(emailNameAgregator.getName(internetAddress.getAddress()), internetAddress.getAddress());
                    }
                }
                if (emailDocument.to != null) {
                    for (Address address : emailDocument.to) {
                        InternetAddress internetAddress = (InternetAddress) address;
                        email.addCc(emailNameAgregator.getName(internetAddress.getAddress()), internetAddress.getAddress());
                    }
                }
                if (email.check()) {
                    append(stream, email.toJson());
                } else {
                    fail = true;
                }
                i++;
            }
            append(stream, "]");
            stream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    private void append(Writer stream, String string) throws IOException {
        string = string.trim();
        stream.append(string);
    }

}
