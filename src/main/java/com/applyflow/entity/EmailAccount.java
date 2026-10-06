package com.applyflow.entity;

import com.applyflow.common.EmailProvider;
import com.applyflow.common.SyncStatus;
import com.applyflow.persistence.OwnedEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A connected mailbox (collection {@code email_accounts}). */
@Document("email_accounts")
@CompoundIndex(name = "uq_email_accounts_user_email", def = "{'userId': 1, 'email': 1}", unique = true)
@Getter
@Setter
public class EmailAccount implements OwnedEntity {

    @Id
    private Long id;

    /** Owner (users._id); every query is scoped by it. */
    private Long userId;

    /** Stored lower-case. */
    private String email;

    private EmailProvider provider;

    private String host;

    private Integer port;

    private boolean ssl = true;

    private String username;

    /** AES-GCM encrypted app password (base64 iv||ciphertext). Never exposed. */
    private String encryptedPassword;

    private String folder = "INBOX";

    private boolean enabled = true;

    private SyncStatus syncStatus = SyncStatus.DISCONNECTED;

    private Instant lastSyncAt;

    private String lastError;

    private Long lastUid;

    private Long uidValidity;

    private long emailsProcessed;

    private long jobEmails;

    private Integer initialSyncDays = 90;

    private Instant createdAt;

    private Instant updatedAt;

    @Override
    public void beforeSave(boolean isNew) {
        Instant now = Instant.now();
        if (isNew || createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public boolean isDemo() {
        return provider == EmailProvider.DEMO;
    }

    @Override
    public String toString() {
        // Deliberately excludes the encrypted password.
        return "EmailAccount{id=" + id + ", email=" + email + ", provider=" + provider + ", host=" + host
                + ", syncStatus=" + syncStatus + "}";
    }
}
