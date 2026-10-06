package com.applyflow.repository;

import com.applyflow.common.SyncJobStatus;
import com.applyflow.entity.SyncJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SyncJobRepository extends MongoRepository<SyncJob, Long> {

    /** Newest first; callers batch-load the mailboxes. */
    List<SyncJob> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);

    List<SyncJob> findByStatus(SyncJobStatus status);

    Optional<SyncJob> findFirstByEmailAccountIdOrderByStartedAtDesc(Long emailAccountId);
}
