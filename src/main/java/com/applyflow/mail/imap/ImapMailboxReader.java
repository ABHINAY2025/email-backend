package com.applyflow.mail.imap;

import com.applyflow.mail.parser.EmailHeaders;
import com.applyflow.mail.parser.MimeMessageParser;
import com.applyflow.mail.parser.ParsedEmail;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.eclipse.angus.mail.gimap.GmailFolder;
import org.eclipse.angus.mail.gimap.GmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Incremental UID-based mailbox reader. Fetches envelopes/headers in batches first, lets the handler pre-filter,
 * and downloads (text) bodies only on demand.
 */
@Component
public class ImapMailboxReader {

    private static final Logger log = LoggerFactory.getLogger(ImapMailboxReader.class);
    private static final int UIDVALIDITY_RESYNC_DAYS = 7;

    private final ImapClient client;
    private final MimeMessageParser parser;

    public ImapMailboxReader(ImapClient client, MimeMessageParser parser) {
        this.client = client;
        this.parser = parser;
    }

    /** Lazily downloads and parses a message body. */
    @FunctionalInterface
    public interface BodyLoader {
        ParsedEmail load() throws Exception;
    }

    public interface Handler {
        /** Called for each message in ascending UID order. Exceptions are caught and counted as malformed. */
        void onMessage(long uid, EmailHeaders headers, BodyLoader loader) throws Exception;

        /** Called after each batch; persist the cursor here. */
        void onBatchComplete(long lastUid, long uidValidity, int fetched, int malformed);
    }

    public record Cursor(Long lastUid, Long uidValidity, int initialSyncDays) {
    }

    public void read(ImapConnectionSettings settings, Cursor cursor, Long accountId, int batchSize, int maxBodyBytes,
                     Handler handler) {
        Store store = null;
        Folder folder = null;
        try {
            store = client.connect(settings);
            folder = client.openFolder(store, settings.folder());
            UIDFolder uf = (UIDFolder) folder;
            long validity = uf.getUIDValidity();
            boolean validityChanged = cursor.uidValidity() != null && cursor.uidValidity() != validity;
            if (validityChanged) {
                log.info("UIDVALIDITY changed for account {}; re-syncing the last {} days", accountId,
                        UIDVALIDITY_RESYNC_DAYS);
            }

            Message[] messages;
            long minUid;
            if (cursor.lastUid() == null || validityChanged) {
                int days = validityChanged ? UIDVALIDITY_RESYNC_DAYS : cursor.initialSyncDays();
                if (days <= 0) {
                    messages = folder.getMessages();
                } else {
                    Date since = Date.from(Instant.now().minus(Duration.ofDays(days)));
                    messages = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, since));
                }
                minUid = 0;
            } else {
                messages = uf.getMessagesByUID(cursor.lastUid() + 1, UIDFolder.MAXUID);
                minUid = cursor.lastUid();
            }
            boolean initial = cursor.lastUid() == null || validityChanged;
            long uidNext = safeUidNext(uf);
            if (messages == null || messages.length == 0) {
                long cursorUid = initial ? Math.max(0, uidNext - 1) : cursor.lastUid();
                handler.onBatchComplete(cursorUid, validity, 0, 0);
                return;
            }

            FetchProfile uidProfile = new FetchProfile();
            uidProfile.add(UIDFolder.FetchProfileItem.UID);
            folder.fetch(messages, uidProfile);
            final UIDFolder uidFolder = uf;
            final long floor = minUid;
            List<Message> ordered = new ArrayList<>(Arrays.stream(messages)
                    .filter(m -> safeUid(uidFolder, m) > floor)
                    .sorted(Comparator.comparingLong(m -> safeUid(uidFolder, m)))
                    .toList());

            boolean gmail = folder instanceof GmailFolder;
            FetchProfile headerProfile = new FetchProfile();
            headerProfile.add(FetchProfile.Item.ENVELOPE);
            headerProfile.add(FetchProfile.Item.FLAGS);
            headerProfile.add(UIDFolder.FetchProfileItem.UID);
            for (String h : List.of("Message-ID", "In-Reply-To", "References", "List-Unsubscribe", "List-Id")) {
                headerProfile.add(h);
            }
            if (gmail) {
                headerProfile.add(GmailFolder.FetchProfileItem.THRID);
            }

            long lastUid = cursor.lastUid() == null || validityChanged ? 0 : cursor.lastUid();
            for (int start = 0; start < ordered.size(); start += batchSize) {
                List<Message> batch = ordered.subList(start, Math.min(ordered.size(), start + batchSize));
                Message[] arr = batch.toArray(Message[]::new);
                folder.fetch(arr, headerProfile);
                int malformed = 0;
                for (Message m : arr) {
                    long uid = safeUid(uf, m);
                    try {
                        EmailHeaders headers = parser.headers(m);
                        String thrId = gmail && m instanceof GmailMessage gm ? safeThrId(gm) : null;
                        handler.onMessage(uid, headers, () -> parser.parse(m, uid, validity, accountId, thrId,
                                maxBodyBytes));
                    } catch (Exception e) {
                        malformed++;
                        log.warn("Skipping malformed message uid={} in account {}: {}", uid, accountId,
                                e.getClass().getSimpleName());
                    }
                    lastUid = Math.max(lastUid, uid);
                }
                handler.onBatchComplete(lastUid, validity, arr.length, malformed);
            }
            if (initial && uidNext - 1 > lastUid) {
                // Older messages outside the initial window are intentionally skipped; move the cursor past them.
                handler.onBatchComplete(uidNext - 1, validity, 0, 0);
            }
        } catch (MessagingException e) {
            throw ImapErrorMapper.map(e, settings.host(), settings.port());
        } finally {
            ImapClient.closeQuietly(folder, store);
        }
    }

    private static long safeUid(UIDFolder uf, Message m) {
        try {
            return uf.getUID(m);
        } catch (MessagingException e) {
            return -1;
        }
    }

    private static long safeUidNext(UIDFolder uf) {
        try {
            return uf.getUIDNext();
        } catch (MessagingException e) {
            return -1;
        }
    }

    private static String safeThrId(GmailMessage gm) {
        try {
            long id = gm.getThrId();
            return id == 0 ? null : Long.toHexString(id);
        } catch (MessagingException e) {
            return null;
        }
    }
}
