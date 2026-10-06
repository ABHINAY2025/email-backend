package com.applyflow.repository;

import com.applyflow.entity.ApplicationEventEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;

/** Every method is scoped to one owner ({@code userId}). */
public interface ApplicationEventRepository extends MongoRepository<ApplicationEventEntity, Long>,
        ApplicationEventRepositoryCustom {

    /** Oldest first. */
    @Query(value = "{ 'userId': ?0, 'applicationId': ?1 }", sort = "{ 'eventDate': 1, '_id': 1 }")
    List<ApplicationEventEntity> findTimeline(Long userId, Long appId);

    @Query("{ 'userId': ?0, 'applicationId': ?1 }")
    @Update("{ '$set': { 'applicationId': ?2 } }")
    long reassignApplication(Long userId, Long sourceId, Long targetId);

    @Query(value = "{ 'userId': ?0, 'emailId': { '$ne': null } }", delete = true)
    long deleteEmailDerived(Long userId);
}
