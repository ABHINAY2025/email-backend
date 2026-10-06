package com.applyflow.repository;

import com.applyflow.common.EventType;
import com.applyflow.entity.ApplicationEventEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Event queries, all scoped to one owner; results have their application (with company) batch-loaded. */
public interface ApplicationEventRepositoryCustom {

    /** Newest events of non-archived applications. */
    List<ApplicationEventEntity> findRecent(Long userId, int limit);

    List<ApplicationEventEntity> findRecentForCompany(Long userId, Long companyId, int limit);

    /** Scheduled events (interviews, deadlines...) of non-archived applications in [from, to), soonest first. */
    List<ApplicationEventEntity> findScheduledBetween(Long userId, Instant from, Instant to);

    List<ApplicationEventEntity> findByTypesBetween(Long userId, Collection<EventType> types, Instant from,
                                                    Instant to);

    /** Deletes the events derived from the mailbox's emails. */
    long deleteForAccountEmails(Long userId, Long accountId);
}
