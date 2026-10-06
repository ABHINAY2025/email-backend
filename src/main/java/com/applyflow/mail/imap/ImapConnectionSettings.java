package com.applyflow.mail.imap;

import com.applyflow.common.EmailProvider;

/** Connection parameters. The password is never included in {@link #toString()}. */
public record ImapConnectionSettings(EmailProvider provider, String host, int port, boolean ssl, String username,
                                     String password, String folder) {

    @Override
    public String toString() {
        return "ImapConnectionSettings{provider=" + provider + ", host=" + host + ", port=" + port + ", ssl=" + ssl
                + ", username=" + username + ", folder=" + folder + "}";
    }
}
