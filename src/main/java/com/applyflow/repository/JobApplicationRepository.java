package com.applyflow.repository;

import com.applyflow.entity.JobApplication;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobApplicationRepository extends MongoRepository<JobApplication, Long>,
        JobApplicationRepositoryCustom {

    long countByUserId(Long userId);
}
