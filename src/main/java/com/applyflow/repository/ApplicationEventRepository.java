package com.applyflow.repository;

import com.applyflow.entity.ApplicationEventEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;

public interface ApplicationEventRepository extends MongoRepository<ApplicationEventEntity, Long>,
        ApplicationEventRepositoryCustom {

    /** Oldest first. */
    @Query(value = "{ 'applicationId': ?0 }", sort = "{ 'eventDate': 1, '_id': 1 }")
    List<ApplicationEventEntity> findTimeline(Long appId);

    @Query("{ 'applicationId': ?0 }")
    @Update("{ '$set': { 'applicationId': ?1 } }")
    long reassignApplication(Long sourceId, Long targetId);

    @Query(value = "{ 'emailId': { '$ne': null } }", delete = true)
    long deleteEmailDerived();
}
