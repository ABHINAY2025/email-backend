package com.applyflow.mail.imap;

import com.applyflow.exception.ImapException;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.springframework.http.HttpStatus;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/** Translates Jakarta Mail failures into user-friendly {@link ImapException}s (never leaking credentials). */
public final class ImapErrorMapper {

    private ImapErrorMapper() {
    }

    public static ImapException map(Throwable error, String host, int port) {
        if (error instanceof ImapException ie) {
            return ie;
        }
        String where = host + ":" + port;
        String allMessages = collectMessages(error).toLowerCase(Locale.ROOT);

        if (allMessages.contains("[throttled]") || allMessages.contains("too many simultaneous connections")
                || allMessages.contains("rate limit") || allMessages.contains("try again later")
                || allMessages.contains("[limit]")) {
            return new ImapException.RateLimited(error);
        }
        if (hasCause(error, AuthenticationFailedException.class) || allMessages.contains("[authenticationfailed]")
                || allMessages.contains("invalid credentials") || allMessages.contains("application-specific password")
                || allMessages.contains("authenticate failed") || allMessages.contains("login failed")) {
            return new ImapException.AuthFailed(error);
        }
        if (hasCause(error, FolderNotFoundException.class)) {
            return new ImapException.MailboxUnavailable(
                    "The mailbox folder could not be found. Check the folder name (usually INBOX).", error);
        }
        if (hasCause(error, UnknownHostException.class)) {
            return new ImapException.ConnectionFailed("Unknown mail server host '" + host
                    + "'. Check the IMAP server address.", error);
        }
        // MailConnectException messages always mention the configured timeout, so only trust real timeouts here.
        if (hasCause(error, SocketTimeoutException.class)
                || (allMessages.contains("timed out") && !hasCause(error, ConnectException.class))) {
            return new ImapException.Timeout("Connection to " + where
                    + " timed out. The mail server did not respond in time.", error);
        }
        if (hasCause(error, SSLException.class)) {
            return new ImapException.ConnectionFailed("Secure (SSL/TLS) connection to " + where
                    + " failed. Check the port and SSL setting.", error);
        }
        if (hasCause(error, MailConnectException.class) || hasCause(error, ConnectException.class)) {
            return new ImapException.ConnectionFailed("Could not connect to " + where
                    + ". Check the server address, port and your network connection.", error);
        }
        if (allMessages.contains("mailbox") && (allMessages.contains("unavailable")
                || allMessages.contains("doesn't exist") || allMessages.contains("does not exist"))) {
            return new ImapException.MailboxUnavailable("The mailbox is currently unavailable.", error);
        }
        if (error instanceof MessagingException) {
            return new ImapException.ConnectionFailed(HttpStatus.BAD_GATEWAY,
                    "The mail server returned an error. Please try again later.", error);
        }
        return new ImapException.ConnectionFailed(HttpStatus.BAD_GATEWAY,
                "Unexpected error while talking to the mail server.", error);
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        Throwable c = t;
        int depth = 0;
        while (c != null && depth++ < 10) {
            if (type.isInstance(c)) {
                return true;
            }
            if (c instanceof MessagingException me && me.getNextException() != null && me.getNextException() != c.getCause()) {
                if (type.isInstance(me.getNextException())) {
                    return true;
                }
            }
            c = c.getCause();
        }
        return false;
    }

    private static String collectMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable c = t;
        int depth = 0;
        while (c != null && depth++ < 10) {
            if (c.getMessage() != null) {
                sb.append(c.getMessage()).append(' ');
            }
            c = c.getCause();
        }
        return sb.toString();
    }
}
