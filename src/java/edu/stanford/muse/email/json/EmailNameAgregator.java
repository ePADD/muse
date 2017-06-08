package edu.stanford.muse.email.json;

import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmailNameAgregator {
    private List<Document> allDocs;
    final Map<String, String> emailNameMap = new HashMap<>();

    public EmailNameAgregator(List<Document> allDocs) {
        this.allDocs = allDocs;
        init();
    }

    private void init() {
        emailNameMap.clear();
        allDocs.forEach(document -> {
            EmailDocument emailDocument = (EmailDocument) document;
            if (emailDocument.to != null) {
                for (Address address : emailDocument.to) {
                    appendToEmailNameMap(emailNameMap, (InternetAddress) address);
                }
            }
            if (emailDocument.cc != null) {
                for (Address address : emailDocument.cc) {
                    appendToEmailNameMap(emailNameMap, (InternetAddress) address);
                }
            }
            if (emailDocument.bcc != null) {
                for (Address address : emailDocument.bcc) {
                    appendToEmailNameMap(emailNameMap, (InternetAddress) address);
                }
            }
        });
    }

    public String getName(String email) {
        return emailNameMap.get(email);
    }


    private void appendToEmailNameMap(Map<String, String> emailNameMap, InternetAddress internetAddress) {
        String email = internetAddress.getAddress();
        String personal = internetAddress.getPersonal();
        String name = emailNameMap.get(email);
        if (name != null) {
            if (personal != null && name.length() < personal.length()) {
                if (personal.contains(" ") || (!name.contains(" "))) {
                    emailNameMap.put(email, personal);
                } else {
                    if (!name.contains(" ") && personal.contains(" ")) {
                        emailNameMap.put(email, personal);
                    }
                }
            }
        } else {
            emailNameMap.put(email, personal);
        }
    }
}
