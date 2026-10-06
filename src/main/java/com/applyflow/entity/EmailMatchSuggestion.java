package com.applyflow.entity;

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

/** Collection {@code email_match_suggestions}. */
@Document("email_match_suggestions")
@Getter
@Setter
public class EmailMatchSuggestion implements MongoEntity {

    @Id
    private Long id;

    @Indexed(name = "idx_match_suggestions_email")
    private Long emailId;

    @Indexed(name = "idx_match_suggestions_application")
    @Setter(AccessLevel.NONE)
    private Long applicationId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private JobApplication application;

    private Double score;

    private String reason;

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

    public void setScore(Double score) {
        this.score = Scores.round3(score);
    }

    @Override
    public void beforeSave(boolean isNew) {
        if (isNew || createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
