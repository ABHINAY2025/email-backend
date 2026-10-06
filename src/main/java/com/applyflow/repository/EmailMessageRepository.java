package com.applyflow.repository;

import com.applyflow.entity.EmailMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Optional;

public interface EmailMessageRepository extends MongoRepository<EmailMessage, Long>, EmailMessageRepositoryCustom {

    Optional<EmailMessage> findByIdAndUserId(Long id, Long userId);

    /** Mailboxes belong to exactly one user, so this is owner-scoped. */
    boolean existsByEmailAccountIdAndProviderMessageId(Long emailAccountId, String providerMessageId);

    long countByUserIdAndJobRelatedTrue(Long userId);

    long countByUserIdAndJobRelatedTrueAndReadFalse(Long userId);

    long countByUserIdAndJobRelatedTrueAndActionRequiredTrue(Long userId);

    long countByUserIdAndJobRelatedTrueAndNeedsReviewTrue(Long userId);

    long countByUserIdAndApplicationIdAndNeedsReviewTrue(Long userId, Long applicationId);

    @Query("{ 'userId': ?0, 'applicationId': ?1 }")
    @Update("{ '$set': { 'applicationId': ?2 } }")
    long reassignApplication(Long userId, Long sourceId, Long targetId);

    @Query("{ 'emailAccountId': ?0 }")
    @Update("{ '$unset': { 'emailAccountId': 1 } }")
    long detachFromAccount(Long accountId);
}
