package edu.stanford.muse.email.json;

import java.io.Serializable;

/**
 * Created by sunchise on 03.06.17.
 */
public class EmailInfo implements Serializable {

    private final int emailId;
    private final String dateField;
    private final boolean isSent;
    private final String[][] toField;
    private final String[][] ccField;
    private final Object[] fromField;
    private final String subject;


    public EmailInfo(int emailId, String dateField, boolean isSent, String[][] toField, String[][] ccField, Object[] fromField, String subject) {
        this.emailId = emailId;
        this.dateField = dateField;
        this.isSent = isSent;
        this.toField = toField;
        this.ccField = ccField;
        this.fromField = fromField;
        this.subject = subject;
    }

    public int getEmailId() {
        return emailId;
    }

    public String getDateField() {
        return dateField;
    }

    public boolean isSent() {
        return isSent;
    }

    public String[][] getToField() {
        return toField;
    }

    public String[][] getCcField() {
        return ccField;
    }

    public Object[] getFromField() {
        return fromField;
    }

    public String getSubject() {
        return subject;
    }
}
