package com.applyflow.entity;

import com.applyflow.common.ApplicationStatus;
import com.applyflow.persistence.MongoEntity;
import com.applyflow.persistence.Refs;
import com.applyflow.persistence.Scores;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A job application (collection {@code applications}). The company and mailbox are referenced by id
 * ({@code companyId}, {@code emailAccountId}); {@link #getCompany()}/{@link #getEmailAccount()} return the
 * batch-loaded document (see {@code RefLoader}) or load it on first access.
 */
@Document("applications")
@Getter
@Setter
public class JobApplication implements MongoEntity {

    @Id
    private Long id;

    @Indexed(name = "idx_applications_company")
    @Setter(AccessLevel.NONE)
    private Long companyId;

    @Indexed(name = "idx_applications_email_account")
    @Setter(AccessLevel.NONE)
    private Long emailAccountId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Company company;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private EmailAccount emailAccount;

    private String jobTitle;

    private String normalizedTitle;

    private String location;

    @Indexed(name = "idx_applications_job_url")
    private String jobUrl;

    private String source;

    @Indexed(name = "idx_applications_status")
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Indexed(name = "idx_applications_applied_at")
    private Instant appliedAt;

    @Indexed(name = "idx_applications_last_activity")
    private Instant lastActivityAt;

    /** Stored as Decimal128 so amounts round-trip exactly. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal salaryMin;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal salaryMax;

    private String salaryCurrency;

    private String employmentType;

    private String recruiterName;

    private String recruiterEmail;

    @Indexed(name = "idx_applications_ref")
    private String applicationRef;

    private String currentStage;

    private Double confidence;

    private boolean needsReview;

    private boolean archived;

    private boolean demo;

    private Instant createdAt;

    private Instant updatedAt;

    public Company getCompany() {
        if (company == null && companyId != null) {
            company = Refs.find(Company.class, companyId);
        }
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
        this.companyId = company == null ? null : company.getId();
    }

    public void setCompanyId(Long companyId) {
        if (!Objects.equals(this.companyId, companyId)) {
            this.company = null;
        }
        this.companyId = companyId;
    }

    public EmailAccount getEmailAccount() {
        if (emailAccount == null && emailAccountId != null) {
            emailAccount = Refs.find(EmailAccount.class, emailAccountId);
        }
        return emailAccount;
    }

    public void setEmailAccount(EmailAccount emailAccount) {
        this.emailAccount = emailAccount;
        this.emailAccountId = emailAccount == null ? null : emailAccount.getId();
    }

    public void setEmailAccountId(Long emailAccountId) {
        if (!Objects.equals(this.emailAccountId, emailAccountId)) {
            this.emailAccount = null;
        }
        this.emailAccountId = emailAccountId;
    }

    public void setConfidence(Double confidence) {
        this.confidence = Scores.round3(confidence);
    }

    @Override
    public void beforeSave(boolean isNew) {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public String displayId() {
        return "AF-" + id;
    }

    /** Moves lastActivityAt forward only. */
    public void touchActivity(Instant at) {
        if (at != null && (lastActivityAt == null || at.isAfter(lastActivityAt))) {
            lastActivityAt = at;
        }
    }

    @Override
    public String toString() {
        return "JobApplication{id=" + id + ", jobTitle=" + jobTitle + ", status=" + status + "}";
    }
}
