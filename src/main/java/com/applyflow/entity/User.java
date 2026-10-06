package com.applyflow.entity;

import com.applyflow.persistence.MongoEntity;
import com.applyflow.persistence.Scores;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * An application user; also carries that user's settings (collection {@code users}). Self-registered users have
 * {@code username == email} (lower-case); the env-bootstrapped admin has no email.
 */
@Document("users")
@CompoundIndex(name = "uq_users_email", def = "{'email': 1}", unique = true,
        partialFilter = "{'email': {'$type': 'string'}}")
@Getter
@Setter
public class User implements MongoEntity {

    @Id
    private Long id;

    @Indexed(unique = true, name = "uq_users_username")
    private String username;

    /** Lower-case; null for the env-bootstrapped admin. */
    private String email;

    private String passwordHash;

    private String displayName;

    private Integer syncIntervalMinutes = 5;

    private Integer defaultInitialSyncDays = 90;

    private Double confidenceThreshold = 0.75;

    private boolean autoUpdateStatus = true;

    private Integer followUpDays = 14;

    /** The user hid the onboarding guide. */
    private boolean onboardingDismissed;

    private Instant createdAt;

    private Instant updatedAt;

    public void setConfidenceThreshold(Double confidenceThreshold) {
        this.confidenceThreshold = Scores.round3(confidenceThreshold);
    }

    @Override
    public void beforeSave(boolean isNew) {
        Instant now = Instant.now();
        if (isNew || createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username=" + username + "}";
    }
}
