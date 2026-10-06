package com.applyflow.entity;

import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EventType;
import com.applyflow.common.ScheduledType;
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

import java.time.Instant;
import java.util.Objects;

/** Timeline event of an application (collection {@code application_events}). */
@Document("application_events")
@Getter
@Setter
public class ApplicationEventEntity implements MongoEntity {

    @Id
    private Long id;

    @Indexed(name = "idx_events_application")
    @Setter(AccessLevel.NONE)
    private Long applicationId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private JobApplication application;

    @Indexed(name = "idx_events_email")
    @Setter(AccessLevel.NONE)
    private Long emailId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private EmailMessage email;

    private EventType eventType;

    private String title;

    private String description;

    @Indexed(name = "idx_events_event_date")
    private Instant eventDate;

    private ApplicationStatus previousStatus;

    private ApplicationStatus newStatus;

    private Double confidence;

    private Actor actor;

    @Indexed(name = "idx_events_scheduled_at")
    private Instant scheduledAt;

    private ScheduledType scheduledType;

    private Instant createdAt;

    public JobApplication getApplication() {
        if (application == null && applicationId != null) {
            application = Refs.find(JobApplication.class, applicationId);
        }
        return application;
    }

    public void setApplication(JobApplication application) {
        this.application = application;
        this.applicationId = application == null ? null : application.getId();
    }

    public void setApplicationId(Long applicationId) {
        if (!Objects.equals(this.applicationId, applicationId)) {
            this.application = null;
        }
        this.applicationId = applicationId;
    }

    public EmailMessage getEmail() {
        if (email == null && emailId != null) {
            email = Refs.find(EmailMessage.class, emailId);
        }
        return email;
    }

    public void setEmail(EmailMessage email) {
        this.email = email;
        this.emailId = email == null ? null : email.getId();
    }

    public void setEmailId(Long emailId) {
        if (!Objects.equals(this.emailId, emailId)) {
            this.email = null;
        }
        this.emailId = emailId;
    }

    public void setConfidence(Double confidence) {
        this.confidence = Scores.round3(confidence);
    }

    @Override
    public void beforeSave(boolean isNew) {
        if (isNew || createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
