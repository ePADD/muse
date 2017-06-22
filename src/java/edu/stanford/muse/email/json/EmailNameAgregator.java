package edu.stanford.muse.email.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import org.json.JSONObject;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import java.io.*;
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


    public EmailNameAgregator(List<Document> allDocs, String fileName) {
        this.allDocs = allDocs;
        if (fileName == null) {
            init();
        } else {
            File file = new File(fileName);
            if (file.exists()) {
                load(fileName);
            } else {
                init();
            }
        }
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
        if (personal == null) {
            return;
        }
        personal = removeWildChars(personal);
        String name = emailNameMap.get(email);
        if (name != null) {
            if (name.length() < personal.length()) {
                if (personal.contains(" ") || (!name.contains(" "))) {
                    emailNameMap.put(email, personal);
                }
            } else if (!name.contains(" ") && personal.contains(" ")) {
                emailNameMap.put(email, personal);
            } else if (name.contains(" ") && personal.contains(" ")) {
                int nameWordsCount = name.split(" ").length;
                int personalWordsCount = personal.split(" ").length;
                if (personalWordsCount < 4 && personalWordsCount < nameWordsCount) {
                    emailNameMap.put(email, personal);
                }
            }
        } else {
            emailNameMap.put(email, personal);
        }
    }

    private String removeWildChars(String string) {
        string = string.replaceAll("\\s", " ");
        string = string.replaceAll("\\n", " ");
        string = string.replaceAll("\\\\", "\\\\\\\\");
        string = string.replaceAll("\\r", " ");
        string = string.replaceAll(" {2,}", " ");
        string = string.replaceAll("\" ", "\"");
        string = string.replaceAll(" \"", "\"");
        string = string.replaceAll("[^\\w\\d\\sёЁА-Яа-я.,:\\\\\\[\\]|'\";()*?!#$%{}@+\\-]", "");
        return string.trim();
    }

    public void save(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }
        JSONObject json = new JSONObject(emailNameMap);
        try (Writer writer = new FileWriter(file)) {
            json.write(writer);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void load(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            file.delete();
        }
        try (FileReader fileReader = new FileReader(file)) {
            Map<String, String> tempMap = new Gson().fromJson(fileReader, new TypeToken<Map<String, String>>() {}.getType());
            if (tempMap != null) {
                emailNameMap.putAll(tempMap);
            }
            fileReader.close();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
