package com.applyflow.repository;

import com.applyflow.entity.Note;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends MongoRepository<Note, Long> {

    @Query(value = "{ 'applicationId': ?0 }", sort = "{ 'createdAt': -1, '_id': -1 }")
    List<Note> findForApplication(Long appId);

    Optional<Note> findByIdAndApplicationId(Long id, Long applicationId);

    @Query("{ 'applicationId': ?0 }")
    @Update("{ '$set': { 'applicationId': ?1 } }")
    long reassignApplication(Long sourceId, Long targetId);
}
