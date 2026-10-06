package com.applyflow.entity;

import com.applyflow.common.Actor;
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

import java.time.Instant;
import java.util.Objects;

/** Collection {@code application_status_history}. */
@Document("application_status_history")
@Getter
@Setter
public class StatusHistory implements MongoEntity {

    @Id
    private Long id;

    @Indexed(name = "idx_status_history_application")
    @Setter(AccessLevel.NONE)
    private Long applicationId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private JobApplication application;

    private ApplicationStatus fromStatus;

    private ApplicationStatus toStatus;

    private Actor actor;

    private String reason;

    @Indexed(name = "idx_status_history_email")
    private Long emailId;

    private Double confidence;

    private Instant changedAt;

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

    public void setConfidence(Double confidence) {
        this.confidence = Scores.round3(confidence);
    }

    @Override
    public void beforeSave(boolean isNew) {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}
