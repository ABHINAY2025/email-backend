package com.applyflow.mail.imap;

import com.applyflow.common.EmailProvider;
import jakarta.mail.Folder;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import org.springframework.stereotype.Component;

import java.util.Properties;

/** Thin wrapper over Jakarta Mail (Angus) for IMAP connections. */
@Component
public class ImapClient {

    static final int TIMEOUT_MS = 20_000;

    /** Opens a connected store. Caller must close it. */
    public Store connect(ImapConnectionSettings s) throws MessagingException {
        String protocol = protocol(s);
        Properties props = new Properties();
        String p = "mail." + protocol + ".";
        props.put("mail.store.protocol", protocol);
        props.put(p + "host", s.host());
        props.put(p + "port", String.valueOf(s.port()));
        props.put(p + "connectiontimeout", String.valueOf(TIMEOUT_MS));
        props.put(p + "timeout", String.valueOf(TIMEOUT_MS));
        props.put(p + "fetchsize", String.valueOf(1024 * 1024));
        props.put(p + "partialfetch", "true");
        if (s.ssl()) {
            props.put(p + "ssl.enable", "true");
            props.put(p + "ssl.checkserveridentity", "true");
        } else {
            props.put(p + "starttls.enable", "true");
        }
        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(s.host(), s.port(), s.username(), s.password());
        return store;
    }

    /** Opens the configured folder read-only. */
    public Folder openFolder(Store store, String name) throws MessagingException {
        Folder folder = store.getFolder(name == null || name.isBlank() ? "INBOX" : name);
        if (!folder.exists()) {
            throw new FolderNotFoundException(folder, "Folder not found");
        }
        folder.open(Folder.READ_ONLY);
        return folder;
    }

    /** Connects, opens the folder and closes everything. Throws a mapped ImapException on failure. */
    public void testConnection(ImapConnectionSettings s) {
        Store store = null;
        Folder folder = null;
        try {
            store = connect(s);
            folder = openFolder(store, s.folder());
        } catch (Exception e) {
            throw ImapErrorMapper.map(e, s.host(), s.port());
        } finally {
            closeQuietly(folder, store);
        }
    }

    public static boolean usesGmailExtensions(ImapConnectionSettings s) {
        return s.provider() == EmailProvider.GMAIL && s.ssl();
    }

    private static String protocol(ImapConnectionSettings s) {
        if (usesGmailExtensions(s)) {
            return "gimaps";
        }
        return s.ssl() ? "imaps" : "imap";
    }

    public static void closeQuietly(Folder folder, Store store) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception ignored) {
            // best effort
        }
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (Exception ignored) {
            // best effort
        }
    }
}
