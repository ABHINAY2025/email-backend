package com.applyflow.repository;

import com.applyflow.common.SyncJobStatus;
import com.applyflow.entity.SyncJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SyncJobRepository extends MongoRepository<SyncJob, Long> {

    /** Newest first; callers batch-load the mailboxes. */
    List<SyncJob> findByUserIdOrderByStartedAtDescIdDesc(Long userId, Pageable pageable);

    /** All users (startup recovery only). */
    List<SyncJob> findByStatus(SyncJobStatus status);

    boolean existsByUserIdAndStatus(Long userId, SyncJobStatus status);

    Optional<SyncJob> findFirstByEmailAccountIdOrderByStartedAtDesc(Long emailAccountId);

    long deleteByUserId(Long userId);
}
