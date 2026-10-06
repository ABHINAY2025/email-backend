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

/** Email queries; list results have their application (with company) and mailbox batch-loaded. */
public interface EmailMessageRepositoryCustom {

    /** Rows used by analytics. */
    record LinkedEmailRow(Long applicationId, EmailClassification classification, Instant receivedAt) {
    }

    Page<EmailMessage> findPage(Criteria criteria, Pageable pageable);

    /** Emails already linked to an application that belong to the same conversation, newest first. */
    List<EmailMessage> findLinkedInThread(Collection<String> messageIds, String threadId);

    List<EmailMessage> findByApplicationIdOrdered(Long appId);

    /** Job-email count per application id. */
    Map<Long, Long> countByApplicationIds(Collection<Long> ids);

    List<LinkedEmailRow> findLinkedClassificationRows();

    Map<EmailClassification, Long> countByClassification();

    List<EmailMessage> findUnreadActionRequired();

    List<EmailMessage> findNeedsReview();

    /** Job emails of the company's applications, or unlinked ones whose detected company has that name. */
    List<EmailMessage> findForCompany(Long companyId, String companyName, int limit);

    /** Job-email count per lower-cased sender for the company's applications. */
    Map<String, Long> countBySenderForCompany(Long companyId);

    long countForCompany(Long companyId);

    /** Case-insensitive "contains" search over subject / sender; newest first. */
    List<EmailMessage> search(String needle, int limit);

    long deleteByApplicationId(Long appId);

    long deleteByAccountId(Long accountId);

    long deleteDemo();

    long deleteAllBulk();
}
