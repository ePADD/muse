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
        BufferedWriter stream;
        try {
             stream = new BufferedWriter(new FileWriter(file));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        List<Document> allDocs = archive.getAllDocs();
        int i = 1;
        try {
            stream.append("[");
            for (Document doc : allDocs) {
                if (i > 1) {
                    stream.append(",");
                }
                stream.append("{");
                EmailDocument emailDocument = (EmailDocument) doc;
                stream.append("\"emailId\": " + i++ + ",");
                stream.append("\"dateField\": \"" + emailDocument.getDate().getTime() + "\",");
                stream.append("\"isSent\": " + true + ",");
                stream.append("\"toField\": [");
                if (emailDocument.to != null) {
                    boolean first = true;
                    for (Address address : emailDocument.to) {
                        if (!first) {
                            stream.append(",");
                        }
                        InternetAddress internetAddress = (InternetAddress) address;
                        stream.append("[");
                        stream.append(getAddressString(internetAddress));
                        stream.append("]");
                        first = false;
                    }
                }
                stream.append("],");
                stream.append("\"ccField\": [");
                if (emailDocument.cc != null && emailDocument.cc.length != 0) {
                    boolean first = true;
                    for (Address address : emailDocument.cc) {
                        if (!first) {
                            stream.append(",");
                        }
                        InternetAddress internetAddress = (InternetAddress) address;
                        stream.append("[");
                        stream.append(getAddressString(internetAddress));
                        stream.append("]");
                        first = false;
                    }
                } else {
                    stream.append("[");
                    stream.append("\"ccPlaceholder\",\"ccPlaceholder\"");
                    stream.append("]");
                }
                stream.append("],");

                stream.append("\"fromField\": [");
                if (emailDocument.from != null && emailDocument.from.length > 0) {
                    boolean first = true;
                    for (Address address : emailDocument.from) {
                        if (!first) {
                            stream.append(",");
                        }
                        InternetAddress internetAddress = (InternetAddress) address;
                        stream.append("[");
                        stream.append(getAddressString(internetAddress));
                        stream.append("], ");
                        stream.append("\"" + internetAddress.getAddress() + "\"");
                        first = false;
                    }
                }  else {
                    stream.append("[");
                    stream.append("\"fromPlaceholder\",\"fromPlaceholder\"");
                    stream.append("], ");
                    stream.append("\"fromPlaceholder\"");
                }
                stream.append("],");
                stream.append("\"subject\": \"" + String.valueOf(emailDocument.getSubject()).trim().replaceAll("\"", "''").replaceAll("\n", " ") + "\"");
                stream.append("}");
            }
            stream.append("]");
            stream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }



    private String getAddressString(InternetAddress internetAddress) {
        return "\""
                + (internetAddress.getPersonal() == null
                        ? internetAddress.getAddress()
                        : internetAddress.getPersonal().replaceAll("\"", "''"))
                + "\", \"" + internetAddress.getAddress() + "\"";
    }
}
