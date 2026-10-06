package com.applyflow.repository;

import com.applyflow.common.EmailProvider;
import com.applyflow.entity.EmailAccount;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface EmailAccountRepository extends MongoRepository<EmailAccount, Long> {

    Optional<EmailAccount> findByIdAndUserId(Long id, Long userId);

    Optional<EmailAccount> findFirstByUserIdAndEmailIgnoreCase(Long userId, String email);

    boolean existsByUserIdAndEmailIgnoreCase(Long userId, String email);

    List<EmailAccount> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** All users' mailboxes (scheduler / startup recovery only). */
    List<EmailAccount> findAllByOrderByCreatedAtAsc();

    List<EmailAccount> findByUserIdAndProvider(Long userId, EmailProvider provider);

    long countByUserIdAndProviderNot(Long userId, EmailProvider provider);

    @Query("{ 'userId': ?0 }")
    @Update("{ '$unset': { 'lastUid': 1, 'uidValidity': 1 }, '$set': { 'emailsProcessed': 0, "
            + "'jobEmails': 0 } }")
    long resetAllCursors(Long userId);

    @Query("{ '_id': ?0 }")
    @Update("{ '$unset': { 'lastUid': 1, 'uidValidity': 1 }, '$set': { 'emailsProcessed': 0, "
            + "'jobEmails': 0 } }")
    long resetCursor(Long id);
}
