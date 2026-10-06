package com.applyflow.entity;

import com.applyflow.persistence.OwnedEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Collection {@code companies}. */
@Document("companies")
@CompoundIndex(name = "uq_companies_user_normalized_name", def = "{'userId': 1, 'normalizedName': 1}", unique = true)
@Getter
@Setter
public class Company implements OwnedEntity {

    @Id
    private Long id;

    /** Owner (users._id); every query is scoped by it. */
    private Long userId;

    private String name;

    private String normalizedName;

    @Indexed(name = "idx_companies_domain")
    private String domain;

    private String website;

    private boolean demo;

    private Instant createdAt;

    private Instant updatedAt;

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
        return "Company{id=" + id + ", name=" + name + "}";
    }
}
