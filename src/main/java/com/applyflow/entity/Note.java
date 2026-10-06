package com.applyflow.entity;

import com.applyflow.persistence.MongoEntity;
import com.applyflow.persistence.Refs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/** Collection {@code notes}. */
@Document("notes")
@Getter
@Setter
public class Note implements MongoEntity {

    @Id
    private Long id;

    @Indexed(name = "idx_notes_application")
    @Setter(AccessLevel.NONE)
    private Long applicationId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private JobApplication application;

    private String content;

    private Instant createdAt;

    private Instant updatedAt;

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

    @Override
    public void beforeSave(boolean isNew) {
        Instant now = Instant.now();
        if (isNew) {
            if (createdAt == null) {
                createdAt = now;
            }
            if (updatedAt == null) {
                updatedAt = now;
            }
        } else {
            updatedAt = now;
        }
    }
}
