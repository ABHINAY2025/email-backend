package com.applyflow.repository;

import com.applyflow.common.EventType;
import com.applyflow.entity.ApplicationEventEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Event queries; results have their application (with company) batch-loaded. */
public interface ApplicationEventRepositoryCustom {

    /** Newest events of non-archived applications. */
    List<ApplicationEventEntity> findRecent(int limit);

    List<ApplicationEventEntity> findRecentForCompany(Long companyId, int limit);

    /** Scheduled events (interviews, deadlines...) of non-archived applications in [from, to), soonest first. */
    List<ApplicationEventEntity> findScheduledBetween(Instant from, Instant to);

    List<ApplicationEventEntity> findByTypesBetween(Collection<EventType> types, Instant from, Instant to);

    /** Deletes the events derived from the mailbox's emails. */
    long deleteForAccountEmails(Long accountId);
}
