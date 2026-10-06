package com.applyflow.mail.parser;

/** Cheap header-only view of a message used for pre-filtering before downloading bodies. */
public record EmailHeaders(String senderEmail, String senderName, String subject, boolean hasListUnsubscribe,
                           String listId) {

    public EmailHeaders {
        senderEmail = senderEmail == null ? "" : senderEmail.trim().toLowerCase(java.util.Locale.ROOT);
        subject = subject == null ? "" : subject;
    }

    public String senderDomain() {
        int at = senderEmail.lastIndexOf('@');
        return at < 0 ? "" : senderEmail.substring(at + 1);
    }

    public String senderLocalPart() {
        int at = senderEmail.lastIndexOf('@');
        return at < 0 ? senderEmail : senderEmail.substring(0, at);
    }
}
