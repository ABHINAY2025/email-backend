package com.applyflow.entity;

import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailClassification;
import com.applyflow.persistence.OwnedEntity;
import com.applyflow.persistence.Refs;
import com.applyflow.persistence.Scores;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * A stored job-related email (collection {@code emails}). Non-job emails are never persisted. The (mailbox, provider
 * message id) pair is unique for emails that belong to a mailbox; detached emails (mailbox removed without purge)
 * have no {@code emailAccountId} field and are excluded from the unique index, like NULLs in the former SQL schema.
 */
@Document("emails")
@CompoundIndex(name = "uq_emails_account_message", def = "{'emailAccountId': 1, 'providerMessageId': 1}",
        unique = true, partialFilter = "{'emailAccountId': {'$exists': true}}")
@Getter
@Setter
public class EmailMessage implements OwnedEntity {

    @Id
    private Long id;

    /** Owner (users._id); every query is scoped by it. */
    @Indexed(name = "idx_emails_user")
    private Long userId;

    @Setter(AccessLevel.NONE)
    private Long emailAccountId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private EmailAccount emailAccount;

    private String providerMessageId;

    @Indexed(name = "idx_emails_message_id")
    private String messageIdHeader;

    private String inReplyTo;

    private String referencesHeader;

    @Indexed(name = "idx_emails_thread")
    private String threadId;

    @Indexed(name = "idx_emails_application")
    @Setter(AccessLevel.NONE)
    private Long applicationId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private JobApplication application;

    @Indexed(name = "idx_emails_sender")
    private String senderEmail;

    private String senderName;

    private String recipient;

    private String subject = "";

    @Indexed(name = "idx_emails_received_at")
    private Instant receivedAt;

    private String bodyText;

    private String bodyHtml;

    private String snippet;

    private boolean read;

    private boolean jobRelated = true;

    @Indexed(name = "idx_emails_classification")
    private EmailClassification classification;

    private Double classificationConfidence = 0.0;

    private String classificationReason;

    private ApplicationStatus detectedStatus;

    private String detectedCompany;

    private String detectedJobTitle;

    private String summary;

    private boolean actionRequired;

    private String actionText;

    private boolean needsReview;

    private boolean statusApplied;

    private ApplicationStatus previousStatus;

    private Instant scheduledAt;

    private boolean demo;

    private Instant createdAt;

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

    public JobApplication getApplication() {
        if (application == null && applicationId != null) {
            application = Refs.find(JobApplication.class, applicationId);
        }
        return application;
    }

    public void setApplication(JobApplication application) {
        adoptOwner(application);
        this.application = application;
        this.applicationId = application == null ? null : application.getId();
    }

    public void setApplicationId(Long applicationId) {
        if (!Objects.equals(this.applicationId, applicationId)) {
            this.application = null;
        }
        this.applicationId = applicationId;
    }

    public void setClassificationConfidence(Double classificationConfidence) {
        this.classificationConfidence = classificationConfidence == null ? 0.0
                : Scores.round3(classificationConfidence);
    }

    @Override
    public void beforeSave(boolean isNew) {
        if (isNew || createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Override
    public String toString() {
        // Never include the body (privacy).
        return "EmailMessage{id=" + id + ", classification=" + classification + "}";
    }
}
