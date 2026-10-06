package com.applyflow.repository;

import com.applyflow.entity.StatusHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;

public interface StatusHistoryRepository extends MongoRepository<StatusHistory, Long> {

    @Query(value = "{ 'userId': ?0, 'applicationId': ?1 }", sort = "{ 'changedAt': -1, '_id': -1 }")
    List<StatusHistory> findForApplication(Long userId, Long appId);

    /** Only the fields analytics needs (application, target status, time). */
    @Query(value = "{ 'userId': ?0 }", fields = "{ 'applicationId': 1, 'toStatus': 1, 'changedAt': 1 }")
    List<StatusHistory> findAllRows(Long userId);

    @Query("{ 'userId': ?0, 'applicationId': ?1 }")
    @Update("{ '$set': { 'applicationId': ?2 } }")
    long reassignApplication(Long userId, Long sourceId, Long targetId);
}
