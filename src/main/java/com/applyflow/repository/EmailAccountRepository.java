package com.applyflow.repository;

import com.applyflow.common.EmailProvider;
import com.applyflow.entity.EmailAccount;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface EmailAccountRepository extends MongoRepository<EmailAccount, Long> {

    Optional<EmailAccount> findFirstByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<EmailAccount> findAllByOrderByCreatedAtAsc();

    List<EmailAccount> findByProvider(EmailProvider provider);

    @Query("{}")
    @Update("{ '$unset': { 'lastUid': 1, 'uidValidity': 1 }, '$set': { 'emailsProcessed': 0, "
            + "'jobEmails': 0 } }")
    long resetAllCursors();

    @Query("{ '_id': ?0 }")
    @Update("{ '$unset': { 'lastUid': 1, 'uidValidity': 1 }, '$set': { 'emailsProcessed': 0, "
            + "'jobEmails': 0 } }")
    long resetCursor(Long id);
}
