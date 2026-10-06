package com.applyflow.repository;

import com.applyflow.entity.JobApplication;
import com.applyflow.persistence.RefLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

class JobApplicationRepositoryCustomImpl implements JobApplicationRepositoryCustom {

    private final MongoOperations ops;
    private final RefLoader refs;

    JobApplicationRepositoryCustomImpl(MongoOperations ops, RefLoader refs) {
        this.ops = ops;
        this.refs = refs;
    }

    @Override
    public Optional<JobApplication> findWithCompanyById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        JobApplication a = ops.findById(id, JobApplication.class);
        return a == null ? Optional.empty() : Optional.of(refs.applications(List.of(a)).get(0));
    }

    @Override
    public List<JobApplication> findAllWithCompanyByIdIn(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return load(query(where("_id").in(ids)));
    }

    @Override
    public List<JobApplication> findAllWithCompany() {
        return load(new Query());
    }

    @Override
    public List<JobApplication> findAllActiveWithCompany() {
        return load(query(where("archived").is(false)));
    }

    @Override
    public List<JobApplication> findByCompanyIdWithCompany(Long companyId) {
        return load(query(where("companyId").is(companyId)));
    }

    @Override
    public List<JobApplication> findByApplicationRef(String ref) {
        if (ref == null) {
            return List.of();
        }
        return load(query(where("applicationRef").regex("^" + Pattern.quote(ref) + "$", "i")));
    }

    @Override
    public List<JobApplication> findByJobUrl(String url) {
        if (url == null) {
            return List.of();
        }
        return load(query(where("jobUrl").is(url)));
    }

    @Override
    public List<JobApplication> findByCompanyIds(Collection<Long> companyIds) {
        if (companyIds.isEmpty()) {
            return List.of();
        }
        return load(query(where("companyId").in(companyIds)));
    }

    @Override
    public Page<JobApplication> findPage(Criteria criteria, Pageable pageable, boolean sortByCompanyName) {
        if (sortByCompanyName) {
            // Cross-collection sort: filter in MongoDB, sort by company name (then id) and page in memory.
            List<JobApplication> all = load(query(criteria));
            boolean desc = pageable.getSort().stream().findFirst().map(Sort.Order::isDescending).orElse(false);
            Comparator<JobApplication> cmp = Comparator
                    .comparing((JobApplication a) -> a.getCompany() == null ? "" : a.getCompany().getName(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(JobApplication::getId);
            List<JobApplication> sorted = all.stream().sorted(desc ? cmp.reversed() : cmp).toList();
            int from = (int) Math.min(pageable.getOffset(), sorted.size());
            int to = Math.min(from + pageable.getPageSize(), sorted.size());
            return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
        }
        long total = ops.count(query(criteria), JobApplication.class);
        // Locale-aware collation so text sorts (job title) order like the former SQL collation (case-insensitive
        // first level) instead of by raw code points.
        Query q = query(criteria).with(pageable).collation(Collation.of("en"));
        List<JobApplication> content = refs.applications(ops.find(q, JobApplication.class));
        return new PageImpl<>(content, pageable, total);
    }

    private List<JobApplication> load(Query q) {
        return refs.applications(ops.find(q, JobApplication.class));
    }
}
