package com.applyflow.exception;

import org.springframework.http.HttpStatus;

/**
 * IMAP failure translated into a user-friendly message. Never carries credentials; the original cause is kept only
 * for server-side logging.
 */
public class ImapException extends ApiException {

    protected ImapException(HttpStatus status, String code, String message, Throwable cause) {
        super(status, code, message, cause);
    }

    public static class AuthFailed extends ImapException {
        public AuthFailed(Throwable cause) {
            super(HttpStatus.BAD_REQUEST, "IMAP_AUTH_FAILED",
                    "Authentication failed. Check the email address and app password (Gmail requires an App "
                            + "Password with 2-Step Verification enabled).", cause);
        }
    }

    public static class ConnectionFailed extends ImapException {
        public ConnectionFailed(String message, Throwable cause) {
            super(HttpStatus.BAD_REQUEST, "IMAP_CONNECTION_FAILED", message, cause);
        }

        public ConnectionFailed(HttpStatus status, String message, Throwable cause) {
            super(status, "IMAP_CONNECTION_FAILED", message, cause);
        }
    }

    public static class Timeout extends ImapException {
        public Timeout(String message, Throwable cause) {
            super(HttpStatus.GATEWAY_TIMEOUT, "IMAP_TIMEOUT", message, cause);
        }
    }

    public static class MailboxUnavailable extends ImapException {
        public MailboxUnavailable(String message, Throwable cause) {
            super(HttpStatus.BAD_GATEWAY, "MAILBOX_UNAVAILABLE", message, cause);
        }
    }

    public static class RateLimited extends ImapException {
        public RateLimited(Throwable cause) {
            super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "The mail server is rate limiting connections. Please wait a few minutes and try again.", cause);
        }
    }
}
