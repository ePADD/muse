package edu.stanford.muse.email.json;

import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.webapp.JSPHelper;
import org.codehaus.plexus.util.StringOutputStream;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import java.io.*;
import java.util.List;

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
        int i = 1;
        try (BufferedWriter stream = new BufferedWriter(new FileWriter(file))) {
            append(stream, "[");
            for (Document doc : allDocs) {
                if (i > 1) {
                    append(stream, ",");
                }
                append(stream, "{");
                EmailDocument emailDocument = (EmailDocument) doc;
                append(stream, "\"emailId\": " + i++ + ",");
                append(stream, "\"dateField\": \"" + emailDocument.getDate().getTime() + "\",");
                append(stream, "\"isSent\": " + true + ",");
                append(stream, "\"toField\": [");
                if (emailDocument.to != null) {
                    boolean first = true;
                    for (Address address : emailDocument.to) {
                        if (!first) {
                            append(stream, ",");
                        }
                        InternetAddress internetAddress = (InternetAddress) address;
                        append(stream, "[");
                        append(stream, getAddressString(internetAddress));
                        append(stream, "]");
                        first = false;
                    }
                }
                append(stream, "],");
                append(stream, "\"ccField\": [");
                if (emailDocument.cc != null && emailDocument.cc.length != 0) {
                    boolean first = true;
                    for (Address address : emailDocument.cc) {
                        if (!first) {
                            append(stream, ",");
                        }
                        InternetAddress internetAddress = (InternetAddress) address;
                        append(stream, "[");
                        append(stream, getAddressString(internetAddress));
                        append(stream, "]");
                        first = false;
                    }
                } else {
                    append(stream, "[");
                    append(stream, "\"ccPlaceholder\",\"ccPlaceholder\"");
                    append(stream, "]");
                }
                append(stream, "],");

                append(stream, "\"fromField\": [");
                if (emailDocument.from != null && emailDocument.from.length > 0) {
                    boolean first = true;
                    for (Address address : emailDocument.from) {
                        if (!first) {
                            append(stream, ",");
                        }
                        InternetAddress internetAddress = (InternetAddress) address;
                        append(stream, "[");
                        append(stream, getAddressString(internetAddress));
                        append(stream, "], ");
                        append(stream, "\"" + internetAddress.getAddress().replaceAll("\"", "''") + "\"");
                        first = false;
                    }
                }  else {
                    append(stream, "[");
                    append(stream, "\"fromPlaceholder\",\"fromPlaceholder\"");
                    append(stream, "], ");
                    append(stream, "\"fromPlaceholder\"");
                }
                append(stream, "],");
                append(stream, "\"subject\": \"" + String.valueOf(emailDocument.getSubject()).replaceAll("\"", "''") + "\"");
                append(stream, "}");
            }
            append(stream, "]");
            stream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    private void append(Writer stream, String string) throws IOException {
        string = string.replaceAll("\\\n", " ");
        string = string.replaceAll("\\\r", " ");
        string = string.replaceAll(" {2,}", " ");
        string = string.replaceAll("\\\" ", "\"");
        string = string.replaceAll(" \\\"", "\"");
        string = string.trim();
        stream.append(string);
    }

    private String getAddressString(InternetAddress internetAddress) {
        return "\""
                + (internetAddress.getPersonal() == null
                        ? internetAddress.getAddress().replaceAll("\"", "''")
                        : internetAddress.getPersonal().replaceAll("\"", "''"))
                + "\", \"" + internetAddress.getAddress().replaceAll("\"", "''") + "\"";
    }
}
