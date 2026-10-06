package com.applyflow.repository;

import com.applyflow.entity.Note;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends MongoRepository<Note, Long> {

    @Query(value = "{ 'userId': ?0, 'applicationId': ?1 }", sort = "{ 'createdAt': -1, '_id': -1 }")
    List<Note> findForApplication(Long userId, Long appId);

    Optional<Note> findByIdAndApplicationIdAndUserId(Long id, Long applicationId, Long userId);

    @Query("{ 'userId': ?0, 'applicationId': ?1 }")
    @Update("{ '$set': { 'applicationId': ?2 } }")
    long reassignApplication(Long userId, Long sourceId, Long targetId);
}
