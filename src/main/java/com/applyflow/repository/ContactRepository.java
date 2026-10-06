package com.applyflow.repository;

import com.applyflow.entity.Contact;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends MongoRepository<Contact, Long> {

    /** Case-insensitive exact match (derived IgnoreCase queries use a quoted, anchored regex). */
    Optional<Contact> findFirstByUserIdAndCompanyIdAndEmailIgnoreCase(Long userId, Long companyId, String email);

    List<Contact> findByUserIdAndCompanyIdOrderByLastContactAtDesc(Long userId, Long companyId);
}
