package edu.stanford.muse.email.json;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import java.io.*;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
/*
{
    "emailId": 3,
    "dateField": "1496222800",
    "isSent": true,
    "toField": [
      [
        "Александр Игоревич",
        "Александр Игоревич"
      ]
    ],
    "ccField": [
      [
        "ccPlaceholder",
        "ccPlaceholder"
      ]
    ],
    "fromField": [
      "WWF России",
      "WWF России"
    ],
    "subject": "Барс по имени Крюк"
  }
 */


public class Email {
    private final Logger log = LoggerFactory.getLogger(Email.class);

    private final String id;

    private final Date date;

    private final boolean isSent;

    private final Collection<EmailAddress> to = new HashSet<>();

    private final Collection<EmailAddress> cc = new HashSet<>();

    private final EmailAddress from;

    private final String subject;

    private String toJson;

    public Email(String id, Date date, boolean isSent, EmailAddress from, String subject) {
        this.id = id;
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, 1999);
        calendar.set(Calendar.MONTH, Calendar.SEPTEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 11);
        Date minDate = calendar.getTime();
        if (date == null || minDate.compareTo(date) > 0) {
            date = minDate;
        }
        this.date = date;
        this.isSent = isSent;
        this.from = from;
        this.subject = subject;
    }


    public Email(int id, Date date, boolean isSent, String subject, String fromName, String fromEmail) {
        this(String.valueOf(id), date, isSent, subject, fromName, fromEmail);
    }

    public Email(String id, Date date, boolean isSent, String subject, String fromName, String fromEmail) {
        this(id, date, isSent, new EmailAddress(fromName, fromEmail), subject);
    }

    public void addTo(EmailAddress emailAddress) {
        toJson = null;
        to.add(emailAddress);
    }

    public void addTo(String name, String email) {
        toJson = null;
        addTo(new EmailAddress(name, email));
    }

    public void addCc(EmailAddress emailAddress) {
        toJson = null;
        to.add(emailAddress);
    }

    public void addCc(String name, String email) {
        toJson = null;
        addCc(new EmailAddress(name, email));
    }

    public String toJson() {
        if (toJson == null) {
            StringBuilder stream = new StringBuilder();
            stream.append("{");
            stream.append("\"emailId\": ").append(id).append(",");
            stream.append("\"dateField\": ").append(date.getTime() / 1000).append(",");
            stream.append("\"isSent\": ").append(isSent).append(",");
            stream.append("\"toField\": [");
            stream.append(to.stream().map(EmailAddress::toJson).reduce((s, s2) -> s + "," + s2).orElse(""));
            stream.append("],");
            stream.append("\"ccField\": [");
            if (cc.isEmpty()) {
                stream.append(new EmailAddress("ccPlaceholder", "ccPlaceholder").toJson());
            } else {
                stream.append(cc.stream().map(EmailAddress::toJson).reduce((s, s2) -> s + "," + s2).orElse(""));
            }
            stream.append("],");
            stream.append("\"fromField\": ");
            if (from == null) {
                stream.append(new EmailAddress("fromPlaceholder", "fromPlaceholder").toJson());
            } else {
                stream.append(from.toJson());
            }
            stream.append(",");
            stream.append("\"subject\": \"");
            append(stream, String.valueOf(subject).replaceAll("\"", "'").replace("Subject: ", "")).append("\"");
            stream.append("}");
            toJson = stream.toString();
        }
        return toJson;
    }

    public boolean check() {
        try {
            new JSONObject(toJson());
        } catch (JSONException e) {
            log.error("Not right format of json\n\n" + toJson + "\n\n" + e.getMessage());
            return false;
        }
        return true;
    }

    public static class EmailAddress {
        private final String name;
        private final String email;

        public EmailAddress(String name, String email) {
            this.name = name;
            this.email = email;
        }

        public String getName() {
            return name == null ? email : name.replaceAll("\"", "'");
        }

        public String getEmail() {
            return email.replaceAll("\"", "'");
        }

        public String toJson() {
            StringBuilder stream = new StringBuilder();
            append(stream, "[");
            append(stream, "\"" + getName() + "\"");
            append(stream, ",");
            append(stream, "\"" + getEmail() + "\"");
            append(stream, "]");
            return stream.toString();
        }
    }

    private static StringBuilder append(StringBuilder stream, String string) {
        string = string.replaceAll("\\s", " ");
        string = string.replaceAll("\\n", " ");
        string = string.replaceAll("\\\\", "\\\\\\\\");
        string = string.replaceAll("\\r", " ");
        string = string.replaceAll(" {2,}", " ");
        string = string.replaceAll("\" ", "\"");
        string = string.replaceAll(" \"", "\"");
        string = string.replaceAll("[^\\w\\d\\sёЁА-Яа-я.,:\\\\\\[\\]|'\";()*?!#$%{}@+\\-]", "");
        string = string.trim();
        stream.append(string);
        return stream;
    }
}
