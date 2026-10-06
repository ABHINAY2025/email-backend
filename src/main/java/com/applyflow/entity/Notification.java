package com.applyflow.entity;

import com.applyflow.common.NotificationType;
import com.applyflow.persistence.MongoEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Collection {@code notifications}. */
@Document("notifications")
@Getter
@Setter
public class Notification implements MongoEntity {

    @Id
    private Long id;

    private NotificationType type;

    private String title;

    private String message;

    @Indexed(name = "idx_notifications_application")
    private Long applicationId;

    @Indexed(name = "idx_notifications_email")
    private Long emailId;

    @Indexed(name = "idx_notifications_unread")
    private boolean read;

    @Indexed(name = "idx_notifications_created")
    private Instant createdAt;

    @Override
    public void beforeSave(boolean isNew) {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
