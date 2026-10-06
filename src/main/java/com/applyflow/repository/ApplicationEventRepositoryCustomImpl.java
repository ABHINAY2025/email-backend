package com.applyflow.repository;

import com.applyflow.common.EventType;
import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.persistence.RefLoader;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

class ApplicationEventRepositoryCustomImpl implements ApplicationEventRepositoryCustom {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("eventDate"), Sort.Order.desc("_id"));

    private final MongoOperations ops;
    private final RefLoader refs;

    ApplicationEventRepositoryCustomImpl(MongoOperations ops, RefLoader refs) {
        this.ops = ops;
        this.refs = refs;
    }

    @Override
    public List<ApplicationEventEntity> findRecent(int limit) {
        return load(query(notArchived()).with(NEWEST_FIRST).limit(limit));
    }

    @Override
    public List<ApplicationEventEntity> findRecentForCompany(Long companyId, int limit) {
        Query apps = query(where("companyId").is(companyId));
        apps.fields().include("_id");
        List<Long> appIds = ops.find(apps, JobApplication.class).stream().map(JobApplication::getId).toList();
        if (appIds.isEmpty()) {
            return List.of();
        }
        return load(query(where("applicationId").in(appIds)).with(NEWEST_FIRST).limit(limit));
    }

    @Override
    public List<ApplicationEventEntity> findScheduledBetween(Instant from, Instant to) {
        Criteria c = new Criteria().andOperator(where("scheduledAt").gte(from).lt(to), notArchived());
        return load(query(c).with(Sort.by(Sort.Order.asc("scheduledAt"))));
    }

    @Override
    public List<ApplicationEventEntity> findByTypesBetween(Collection<EventType> types, Instant from, Instant to) {
        Criteria c = new Criteria().andOperator(where("eventType").in(types), where("eventDate").gte(from).lt(to),
                notArchived());
        return load(query(c));
    }

    @Override
    public long deleteForAccountEmails(Long accountId) {
        Query emails = query(where("emailAccountId").is(accountId));
        emails.fields().include("_id");
        List<Long> emailIds = ops.find(emails, EmailMessage.class).stream().map(EmailMessage::getId).toList();
        if (emailIds.isEmpty()) {
            return 0;
        }
        return ops.remove(query(where("emailId").in(emailIds)), ApplicationEventEntity.class).getDeletedCount();
    }

    /** Excludes events of archived applications (and orphans whose application no longer exists). */
    private Criteria notArchived() {
        Query active = query(where("archived").is(false));
        active.fields().include("_id");
        List<Long> ids = ops.find(active, JobApplication.class).stream().map(JobApplication::getId).toList();
        return where("applicationId").in(ids);
    }

    private List<ApplicationEventEntity> load(Query q) {
        return refs.events(ops.find(q, ApplicationEventEntity.class));
    }
}
