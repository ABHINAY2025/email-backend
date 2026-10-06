package com.applyflow.entity;

import com.applyflow.persistence.MongoEntity;
import com.applyflow.persistence.Refs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/** Collection {@code contacts}. */
@Document("contacts")
@CompoundIndex(name = "uq_contacts_company_email", def = "{'companyId': 1, 'email': 1}", unique = true)
@Getter
@Setter
public class Contact implements MongoEntity {

    @Id
    private Long id;

    @Setter(AccessLevel.NONE)
    private Long companyId;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Company company;

    private String name;

    private String email;

    private String role;

    private Instant lastContactAt;

    private Instant createdAt;

    public Company getCompany() {
        if (company == null && companyId != null) {
            company = Refs.find(Company.class, companyId);
        }
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
        this.companyId = company == null ? null : company.getId();
    }

    public void setCompanyId(Long companyId) {
        if (!Objects.equals(this.companyId, companyId)) {
            this.company = null;
        }
        this.companyId = companyId;
    }

    @Override
    public void beforeSave(boolean isNew) {
        if (isNew || createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Override
    public String toString() {
        return "Contact{id=" + id + ", email=" + email + "}";
    }
}
