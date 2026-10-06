package com.applyflow.repository;

import com.applyflow.entity.EmailClassificationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailClassificationLogRepository extends MongoRepository<EmailClassificationLog, Long> {
}
