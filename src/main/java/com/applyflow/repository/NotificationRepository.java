package com.applyflow.repository;

import com.applyflow.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends MongoRepository<Notification, Long> {

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    @Query("{ 'userId': ?0, 'read': false }")
    @Update("{ '$set': { 'read': true } }")
    long markAllRead(Long userId);

    @Query("{ 'userId': ?0, 'applicationId': ?1 }")
    @Update("{ '$set': { 'applicationId': ?2 } }")
    long reassignApplication(Long userId, Long source, Long target);

    /** The email id belongs to the caller's (already ownership-checked) email. */
    @Query(value = "{ 'type': 'POSSIBLE_DUPLICATE', 'emailId': ?0 }", delete = true)
    long deleteDuplicateNoticesForEmail(Long emailId);

    @Query(value = "{ 'userId': ?0, 'emailId': { '$ne': null } }", delete = true)
    long deleteEmailLinked(Long userId);

    long deleteByUserId(Long userId);
}
