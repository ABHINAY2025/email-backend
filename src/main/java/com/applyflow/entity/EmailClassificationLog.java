package com.applyflow.entity;

import com.applyflow.common.Actor;
import com.applyflow.common.EmailClassification;
import com.applyflow.persistence.OwnedEntity;
import com.applyflow.persistence.Scores;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Audit log of classifications applied to an email, system or manual (collection {@code email_classifications}). */
@Document("email_classifications")
@Getter
@Setter
public class EmailClassificationLog implements OwnedEntity {

    @Id
    private Long id;

    /** Owner (users._id); every query is scoped by it. */
    @Indexed(name = "idx_email_classifications_user")
    private Long userId;

    @Indexed(name = "idx_email_classifications_email")
    private Long emailId;

    private EmailClassification classification;

    private Double confidence = 0.0;

    private String reason;

    private String signals;

    private Actor actor;

    private Instant createdAt;

    public void setConfidence(Double confidence) {
        this.confidence = confidence == null ? 0.0 : Scores.round3(confidence);
    }

    @Override
    public void beforeSave(boolean isNew) {
        if (isNew || createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
