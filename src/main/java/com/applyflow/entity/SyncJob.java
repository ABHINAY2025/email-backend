package com.applyflow.entity;

import com.applyflow.common.SyncJobStatus;
import com.applyflow.persistence.OwnedEntity;
import com.applyflow.persistence.Refs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/** Collection {@code sync_jobs}. */
@Document("sync_jobs")
@CompoundIndex(name = "idx_sync_jobs_user_started", def = "{'userId': 1, 'startedAt': -1}")
@Getter
@Setter
public class SyncJob implements OwnedEntity {

    @Id
    private Long id;

    /** Owner (users._id); every query is scoped by it. */
    private Long userId;

    @Indexed(name = "idx_sync_jobs_account")
    @Setter(AccessLevel.NONE)
    private Long emailAccountId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private EmailAccount emailAccount;

    @Indexed(name = "idx_sync_jobs_status")
    private SyncJobStatus status;

    @Indexed(name = "idx_sync_jobs_started")
    private Instant startedAt;

    private Instant finishedAt;

    private int messagesFetched;

    private int messagesProcessed;

    private int jobEmailsFound;

    private int applicationsCreated;

    private int applicationsUpdated;

    private String error;

    public EmailAccount getEmailAccount() {
        if (emailAccount == null && emailAccountId != null) {
            emailAccount = Refs.find(EmailAccount.class, emailAccountId);
        }
        return emailAccount;
    }

    public void setEmailAccount(EmailAccount emailAccount) {
        adoptOwner(emailAccount);
        this.emailAccount = emailAccount;
        this.emailAccountId = emailAccount == null ? null : emailAccount.getId();
    }

    public void setEmailAccountId(Long emailAccountId) {
        if (!Objects.equals(this.emailAccountId, emailAccountId)) {
            this.emailAccount = null;
        }
        this.emailAccountId = emailAccountId;
    }

    @Override
    public void beforeSave(boolean isNew) {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}
