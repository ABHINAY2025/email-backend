package com.applyflow.repository;

import com.applyflow.common.EmailClassification;
import com.applyflow.entity.EmailMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Email queries, all scoped to one owner; list results have their application (with company) and mailbox batch-loaded. */
public interface EmailMessageRepositoryCustom {

    /** Rows used by analytics. */
    record LinkedEmailRow(Long applicationId, EmailClassification classification, Instant receivedAt) {
    }

    /** The owner filter is always added to {@code criteria}. */
    Page<EmailMessage> findPage(Long userId, Criteria criteria, Pageable pageable);

    /** Emails already linked to an application that belong to the same conversation, newest first. */
    List<EmailMessage> findLinkedInThread(Long userId, Collection<String> messageIds, String threadId);

    List<EmailMessage> findByApplicationIdOrdered(Long userId, Long appId);

    /** Job-email count per application id. */
    Map<Long, Long> countByApplicationIds(Long userId, Collection<Long> ids);

    List<LinkedEmailRow> findLinkedClassificationRows(Long userId);

    Map<EmailClassification, Long> countByClassification(Long userId);

    List<EmailMessage> findUnreadActionRequired(Long userId);

    List<EmailMessage> findNeedsReview(Long userId);

    /** Job emails of the company's applications, or unlinked ones whose detected company has that name. */
    List<EmailMessage> findForCompany(Long userId, Long companyId, String companyName, int limit);

    /** Job-email count per lower-cased sender for the company's applications. */
    Map<String, Long> countBySenderForCompany(Long userId, Long companyId);

    long countForCompany(Long userId, Long companyId);

    /** Case-insensitive "contains" search over subject / sender; newest first. */
    List<EmailMessage> search(Long userId, String needle, int limit);

    long deleteByApplicationId(Long userId, Long appId);

    long deleteByAccountId(Long userId, Long accountId);

    long deleteDemo(Long userId);

    long deleteAllBulk(Long userId);
}
