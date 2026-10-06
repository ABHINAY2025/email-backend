package com.applyflow.repository;

import com.applyflow.entity.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Queries that return applications with their company and mailbox batch-loaded. */
public interface JobApplicationRepositoryCustom {

    Optional<JobApplication> findWithCompanyById(Long id);

    List<JobApplication> findAllWithCompanyByIdIn(Collection<Long> ids);

    List<JobApplication> findAllWithCompany();

    List<JobApplication> findAllActiveWithCompany();

    List<JobApplication> findByCompanyIdWithCompany(Long companyId);

    /** Case-insensitive exact match on the application reference. */
    List<JobApplication> findByApplicationRef(String ref);

    List<JobApplication> findByJobUrl(String url);

    List<JobApplication> findByCompanyIds(Collection<Long> companyIds);

    /**
     * Filtered page. {@code sortByCompanyName} sorts by the referenced company's name (done in memory, as a
     * cross-collection sort); otherwise the pageable's sort is applied by MongoDB.
     */
    Page<JobApplication> findPage(Criteria criteria, Pageable pageable, boolean sortByCompanyName);
}
