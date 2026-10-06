package com.applyflow.entity;

import com.applyflow.persistence.MongoEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Collection {@code companies}. */
@Document("companies")
@Getter
@Setter
public class Company implements MongoEntity {

    @Id
    private Long id;

    private String name;

    @Indexed(unique = true, name = "uq_companies_normalized_name")
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
