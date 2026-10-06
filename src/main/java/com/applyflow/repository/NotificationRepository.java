package com.applyflow.repository;

import com.applyflow.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, Long> {

    List<Notification> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<Notification> findByReadFalseOrderByCreatedAtDescIdDesc(Pageable pageable);

    long countByReadFalse();

    @Query("{ 'read': false }")
    @Update("{ '$set': { 'read': true } }")
    long markAllRead();

    @Query("{ 'applicationId': ?0 }")
    @Update("{ '$set': { 'applicationId': ?1 } }")
    long reassignApplication(Long source, Long target);

    @Query(value = "{ 'type': 'POSSIBLE_DUPLICATE', 'emailId': ?0 }", delete = true)
    long deleteDuplicateNoticesForEmail(Long emailId);

    @Query(value = "{ 'emailId': { '$ne': null } }", delete = true)
    long deleteEmailLinked();
}
