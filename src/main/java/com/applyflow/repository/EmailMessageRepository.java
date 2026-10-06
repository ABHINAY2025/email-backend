package com.applyflow.repository;

import com.applyflow.entity.EmailMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

public interface EmailMessageRepository extends MongoRepository<EmailMessage, Long>, EmailMessageRepositoryCustom {

    boolean existsByEmailAccountIdAndProviderMessageId(Long emailAccountId, String providerMessageId);

    long countByJobRelatedTrue();

    long countByJobRelatedTrueAndReadFalse();

    long countByJobRelatedTrueAndActionRequiredTrue();

    long countByJobRelatedTrueAndNeedsReviewTrue();

    long countByApplicationIdAndNeedsReviewTrue(Long applicationId);

    @Query("{ 'applicationId': ?0 }")
    @Update("{ '$set': { 'applicationId': ?1 } }")
    long reassignApplication(Long sourceId, Long targetId);

    @Query("{ 'emailAccountId': ?0 }")
    @Update("{ '$unset': { 'emailAccountId': 1 } }")
    long detachFromAccount(Long accountId);
}
