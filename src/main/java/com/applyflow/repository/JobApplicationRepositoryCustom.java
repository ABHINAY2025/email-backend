package com.applyflow.repository;

import com.applyflow.entity.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Owner-scoped queries that return applications with their company and mailbox batch-loaded. */
public interface JobApplicationRepositoryCustom {

    Optional<JobApplication> findWithCompanyById(Long userId, Long id);

    List<JobApplication> findAllWithCompanyByIdIn(Long userId, Collection<Long> ids);

    List<JobApplication> findAllWithCompany(Long userId);

    List<JobApplication> findAllActiveWithCompany(Long userId);

    List<JobApplication> findByCompanyIdWithCompany(Long userId, Long companyId);

    /** Case-insensitive exact match on the application reference. */
    List<JobApplication> findByApplicationRef(Long userId, String ref);

    List<JobApplication> findByJobUrl(Long userId, String url);

    List<JobApplication> findByCompanyIds(Long userId, Collection<Long> companyIds);

    /**
     * Filtered page. {@code sortByCompanyName} sorts by the referenced company's name (done in memory, as a
     * cross-collection sort); otherwise the pageable's sort is applied by MongoDB.
     */
    Page<JobApplication> findPage(Long userId, Criteria criteria, Pageable pageable, boolean sortByCompanyName);
}
