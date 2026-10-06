package com.applyflow.mail.parser;

import java.time.Instant;
import java.util.List;

/**
 * Normalized, provider-independent representation of an email used by the intelligence pipeline.
 * bodyText is plain text (quoted replies stripped), bodyHtml is already sanitized.
 */
public record ParsedEmail(
        Long accountId,
        String providerMessageId,
        String messageIdHeader,
        String inReplyTo,
        List<String> references,
        String gmailThreadId,
        String senderEmail,
        String senderName,
        String recipient,
        String subject,
        Instant receivedAt,
        String bodyText,
        String bodyHtml,
        boolean hasListUnsubscribe,
        String listId,
        boolean demo) {

    public ParsedEmail {
        references = references == null ? List.of() : List.copyOf(references);
        subject = plainSpaces(noNul(subject == null ? "" : subject));
        bodyText = plainSpaces(noNul(bodyText == null ? "" : bodyText));
        bodyHtml = noNul(bodyHtml);
        senderName = noNul(senderName);
        recipient = noNul(recipient);
        senderEmail = senderEmail == null ? "" : noNul(senderEmail.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** No-break, narrow and zero-width spaces from HTML mail would defeat every "\s" in the extractors. */
    private static String plainSpaces(String s) {
        return s.replaceAll("[\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]", " ").replaceAll("[\\u200B\\uFEFF]", "");
    }

    /** PostgreSQL text columns cannot store NUL characters (seen in malformed mail). */
    private static String noNul(String s) {
        return s == null || s.indexOf('\0') < 0 ? s : s.replace("\0", "");
    }

    /** Conversation id: Gmail thread id if known, else root of References, else In-Reply-To, else own id. */
    public String threadId() {
        if (gmailThreadId != null && !gmailThreadId.isBlank()) {
            return "gm:" + gmailThreadId;
        }
        if (!references.isEmpty()) {
            return references.get(0);
        }
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            return inReplyTo;
        }
        return messageIdHeader;
    }

    public String senderDomain() {
        int at = senderEmail.lastIndexOf('@');
        return at < 0 ? "" : senderEmail.substring(at + 1);
    }

    public String senderLocalPart() {
        int at = senderEmail.lastIndexOf('@');
        return at < 0 ? senderEmail : senderEmail.substring(0, at);
    }

    public EmailHeaders headers() {
        return new EmailHeaders(senderEmail, senderName, subject, hasListUnsubscribe, listId);
    }

    /** Convenience factory used by tests and the demo seeder. */
    public static ParsedEmail simple(String senderName, String senderEmail, String subject, String body,
                                     Instant receivedAt) {
        return new ParsedEmail(null, null, null, null, List.of(), null, senderEmail, senderName, null, subject,
                receivedAt, body, null, false, null, false);
    }

    @Override
    public String toString() {
        // Never print bodies.
        return "ParsedEmail{providerMessageId=" + providerMessageId + ", sender=" + senderEmail + "}";
    }
}
