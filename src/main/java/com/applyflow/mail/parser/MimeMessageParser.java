package com.applyflow.mail.parser;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts Jakarta Mail messages to {@link ParsedEmail}. Reads only text parts; attachments are never downloaded. */
@Component
public class MimeMessageParser {

    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s]+>");
    private static final int MAX_DEPTH = 6;

    private final HtmlNormalizer htmlNormalizer;

    public MimeMessageParser(HtmlNormalizer htmlNormalizer) {
        this.htmlNormalizer = htmlNormalizer;
    }

    /** Header-only view (envelope + selected headers already fetched). */
    public EmailHeaders headers(Message msg) throws MessagingException {
        InternetAddress from = firstFrom(msg);
        return new EmailHeaders(from == null ? "" : from.getAddress(), from == null ? null : from.getPersonal(),
                safeSubject(msg), header(msg, "List-Unsubscribe") != null, header(msg, "List-Id"));
    }

    public ParsedEmail parse(Message msg, long uid, long uidValidity, Long accountId, String gmailThreadId,
                             int maxBodyBytes) throws MessagingException, IOException {
        InternetAddress from = firstFrom(msg);
        String messageId = firstId(header(msg, "Message-ID"));
        String inReplyTo = firstId(header(msg, "In-Reply-To"));
        List<String> references = ids(header(msg, "References"));
        String providerId = messageId != null ? messageId : "uid:" + uidValidity + ":" + uid;

        Instant received = msg.getReceivedDate() != null ? msg.getReceivedDate().toInstant()
                : msg.getSentDate() != null ? msg.getSentDate().toInstant() : Instant.now();

        Bodies bodies = new Bodies();
        collect(msg, bodies, maxBodyBytes, 0);
        String text;
        if (bodies.plain != null && !bodies.plain.isBlank()) {
            text = htmlNormalizer.normalizeText(bodies.plain);
        } else {
            text = htmlNormalizer.htmlToText(bodies.html);
        }
        text = htmlNormalizer.stripQuotedReply(text);
        if (text.length() > maxBodyBytes) {
            text = text.substring(0, maxBodyBytes);
        }
        String html = bodies.html == null ? null : htmlNormalizer.sanitizeHtml(bodies.html);
        if (html != null && html.length() > maxBodyBytes) {
            html = null; // too large to store safely; text is kept
        }
        // Keep link targets of the HTML version available for job-URL extraction.
        if ((bodies.plain != null) && bodies.html != null) {
            String fromHtml = htmlNormalizer.htmlToText(bodies.html);
            if (!text.contains("http") && fromHtml.contains("http")) {
                text = text + "\n\n" + extractLinksOnly(fromHtml);
            }
        }

        return new ParsedEmail(accountId, providerId, messageId, inReplyTo, references, gmailThreadId,
                from == null ? "" : from.getAddress(), from == null ? null : from.getPersonal(), recipients(msg),
                safeSubject(msg), received, text, html, header(msg, "List-Unsubscribe") != null,
                header(msg, "List-Id"), false);
    }

    private static final class Bodies {
        String plain;
        String html;
    }

    private void collect(Part part, Bodies out, int max, int depth) throws MessagingException, IOException {
        if (depth > MAX_DEPTH) {
            return;
        }
        String disposition = safeDisposition(part);
        String fileName = safeFileName(part);
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition) || (fileName != null && !fileName.isBlank())) {
            return; // never download attachments
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart mp) {
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    collect(bp, out, max, depth + 1);
                }
            }
            return;
        }
        if (part.isMimeType("text/plain")) {
            if (out.plain == null) {
                out.plain = readText(part, max);
            }
        } else if (part.isMimeType("text/html")) {
            if (out.html == null) {
                out.html = readText(part, max);
            }
        }
        // message/rfc822 and other types are ignored on purpose.
    }

    private static String readText(Part part, int max) throws MessagingException, IOException {
        Charset charset = StandardCharsets.UTF_8;
        try {
            ContentType ct = new ContentType(part.getContentType());
            String cs = ct.getParameter("charset");
            if (cs != null) {
                charset = Charset.forName(MimeUtility.javaCharset(cs.trim().replace("\"", "")));
            }
        } catch (Exception e) {
            charset = StandardCharsets.UTF_8;
        }
        try (InputStream in = part.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.min(max, 64 * 1024));
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) != -1 && total < max) {
                int take = Math.min(n, max - total);
                bos.write(buf, 0, take);
                total += take;
            }
            return bos.toString(charset);
        }
    }

    private static String extractLinksOnly(String text) {
        Matcher m = Pattern.compile("https?://\\S+").matcher(text);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (m.find() && count++ < 40) {
            sb.append(m.group()).append('\n');
        }
        return sb.toString();
    }

    private static InternetAddress firstFrom(Message msg) {
        try {
            Address[] from = msg.getFrom();
            if (from != null) {
                for (Address a : from) {
                    if (a instanceof InternetAddress ia) {
                        return ia;
                    }
                }
            }
        } catch (MessagingException ignored) {
            // malformed From header
        }
        return null;
    }

    private static String recipients(Message msg) {
        try {
            Address[] to = msg.getRecipients(Message.RecipientType.TO);
            if (to == null) {
                return null;
            }
            List<String> list = new ArrayList<>();
            for (Address a : to) {
                if (a instanceof InternetAddress ia && ia.getAddress() != null) {
                    list.add(ia.getAddress());
                }
                if (list.size() >= 5) {
                    break;
                }
            }
            return list.isEmpty() ? null : String.join(", ", list);
        } catch (MessagingException e) {
            return null;
        }
    }

    private static String safeSubject(Message msg) {
        try {
            String s = msg.getSubject();
            return s == null ? "" : s;
        } catch (MessagingException e) {
            return "";
        }
    }

    private static String header(Message msg, String name) {
        try {
            String[] values = msg.getHeader(name);
            return values == null || values.length == 0 ? null : values[0];
        } catch (MessagingException e) {
            return null;
        }
    }

    private static String safeDisposition(Part p) {
        try {
            return p.getDisposition();
        } catch (MessagingException e) {
            return null;
        }
    }

    private static String safeFileName(Part p) {
        try {
            return p.getFileName();
        } catch (MessagingException e) {
            return null;
        }
    }

    static String firstId(String header) {
        if (header == null) {
            return null;
        }
        Matcher m = MESSAGE_ID.matcher(header);
        if (m.find()) {
            return m.group();
        }
        String t = header.trim();
        return t.isEmpty() ? null : t;
    }

    static List<String> ids(String header) {
        List<String> out = new ArrayList<>();
        if (header == null) {
            return out;
        }
        Matcher m = MESSAGE_ID.matcher(header);
        while (m.find() && out.size() < 50) {
            out.add(m.group());
        }
        return out;
    }
}
