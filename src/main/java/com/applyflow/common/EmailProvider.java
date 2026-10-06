package com.applyflow.common;

import java.util.Locale;

public enum EmailProvider {
    GMAIL("imap.gmail.com", 993, true),
    OUTLOOK("outlook.office365.com", 993, true),
    YAHOO("imap.mail.yahoo.com", 993, true),
    ICLOUD("imap.mail.me.com", 993, true),
    IMAP(null, 993, true),
    DEMO("demo.applyflow.local", 993, true);

    private final String defaultHost;
    private final int defaultPort;
    private final boolean defaultSsl;

    EmailProvider(String defaultHost, int defaultPort, boolean defaultSsl) {
        this.defaultHost = defaultHost;
        this.defaultPort = defaultPort;
        this.defaultSsl = defaultSsl;
    }

    public String defaultHost() {
        return defaultHost;
    }

    public int defaultPort() {
        return defaultPort;
    }

    public boolean defaultSsl() {
        return defaultSsl;
    }

    /** Infers a provider from the domain of an email address. */
    public static EmailProvider inferFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return IMAP;
        }
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT).trim();
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            return GMAIL;
        }
        if (domain.startsWith("outlook.") || domain.startsWith("hotmail.") || domain.startsWith("live.")
                || domain.equals("msn.com")) {
            return OUTLOOK;
        }
        if (domain.startsWith("yahoo.") || domain.equals("ymail.com") || domain.equals("rocketmail.com")) {
            return YAHOO;
        }
        if (domain.equals("icloud.com") || domain.equals("me.com") || domain.equals("mac.com")) {
            return ICLOUD;
        }
        return IMAP;
    }
}
