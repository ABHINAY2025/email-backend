package com.applyflow.repository;

import com.applyflow.entity.EmailMatchSuggestion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface EmailMatchSuggestionRepository extends MongoRepository<EmailMatchSuggestion, Long>,
        EmailMatchSuggestionRepositoryCustom {

    @Query(value = "{ 'emailId': ?0 }", delete = true)
    long deleteForEmail(Long emailId);

    @Query(value = "{ 'applicationId': ?0 }", delete = true)
    long deleteForApplication(Long appId);
}
