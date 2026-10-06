package com.applyflow.repository;

import com.applyflow.entity.StatusHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;

public interface StatusHistoryRepository extends MongoRepository<StatusHistory, Long> {

    @Query(value = "{ 'applicationId': ?0 }", sort = "{ 'changedAt': -1, '_id': -1 }")
    List<StatusHistory> findForApplication(Long appId);

    /** Only the fields analytics needs (application, target status, time). */
    @Query(value = "{}", fields = "{ 'applicationId': 1, 'toStatus': 1, 'changedAt': 1 }")
    List<StatusHistory> findAllRows();

    @Query("{ 'applicationId': ?0 }")
    @Update("{ '$set': { 'applicationId': ?1 } }")
    long reassignApplication(Long sourceId, Long targetId);
}
