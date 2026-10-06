package com.applyflow.entity;

import com.applyflow.persistence.MongoEntity;
import com.applyflow.persistence.Scores;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** The single application user; also carries the app settings (collection {@code users}). */
@Document("users")
@Getter
@Setter
public class User implements MongoEntity {

    @Id
    private Long id;

    @Indexed(unique = true, name = "uq_users_username")
    private String username;

    private String passwordHash;

    private String displayName;

    private Integer syncIntervalMinutes = 5;

    private Integer defaultInitialSyncDays = 90;

    private Double confidenceThreshold = 0.75;

    private boolean autoUpdateStatus = true;

    private Integer followUpDays = 14;

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
