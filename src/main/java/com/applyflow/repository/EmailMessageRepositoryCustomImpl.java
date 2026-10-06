package com.applyflow.repository;

import com.applyflow.common.EmailClassification;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.persistence.CascadeDeleter;
import com.applyflow.persistence.RefLoader;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

class EmailMessageRepositoryCustomImpl implements EmailMessageRepositoryCustom {

    private final MongoOperations ops;
    private final RefLoader refs;
    private final CascadeDeleter cascade;

    EmailMessageRepositoryCustomImpl(MongoOperations ops, RefLoader refs, CascadeDeleter cascade) {
        this.ops = ops;
        this.refs = refs;
        this.cascade = cascade;
    }

    @Override
    public Page<EmailMessage> findPage(Long userId, Criteria filter, Pageable pageable) {
        Criteria criteria = new Criteria().andOperator(where("userId").is(userId), filter);
        long total = ops.count(query(criteria), EmailMessage.class);
        List<EmailMessage> content = load(query(criteria).with(pageable));
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<EmailMessage> findLinkedInThread(Long userId, Collection<String> messageIds, String threadId) {
        Criteria c = new Criteria().andOperator(
                where("userId").is(userId),
                where("applicationId").ne(null),
                new Criteria().orOperator(where("messageIdHeader").in(messageIds), where("threadId").is(threadId)));
        List<EmailMessage> list = load(query(c).with(Sort.by(Sort.Order.desc("receivedAt"))));
        return list.stream().filter(e -> e.getApplication() != null).toList();
    }

    @Override
    public List<EmailMessage> findByApplicationIdOrdered(Long userId, Long appId) {
        return load(query(where("userId").is(userId).and("applicationId").is(appId).and("jobRelated").is(true))
                .with(Sort.by(Sort.Order.asc("receivedAt"), Sort.Order.asc("_id"))));
    }

    @Override
    public Map<Long, Long> countByApplicationIds(Long userId, Collection<Long> ids) {
        Map<Long, Long> counts = new HashMap<>();
        if (ids.isEmpty()) {
            return counts;
        }
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(where("userId").is(userId).and("applicationId").in(ids).and("jobRelated").is(true)),
                Aggregation.group("applicationId").count().as("n"));
        for (Document d : ops.aggregate(agg, EmailMessage.class, Document.class).getMappedResults()) {
            if (d.get("_id") instanceof Number id && d.get("n") instanceof Number n) {
                counts.put(id.longValue(), n.longValue());
            }
        }
        return counts;
    }

    @Override
    public List<LinkedEmailRow> findLinkedClassificationRows(Long userId) {
        Query q = query(where("userId").is(userId).and("applicationId").ne(null).and("jobRelated").is(true));
        q.fields().include("applicationId", "classification", "receivedAt");
        return ops.find(q, EmailMessage.class).stream()
                .map(e -> new LinkedEmailRow(e.getApplicationId(), e.getClassification(), e.getReceivedAt()))
                .toList();
    }

    @Override
    public Map<EmailClassification, Long> countByClassification(Long userId) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(where("userId").is(userId).and("jobRelated").is(true)),
                Aggregation.group("classification").count().as("n"));
        Map<EmailClassification, Long> out = new EnumMap<>(EmailClassification.class);
        for (Document d : ops.aggregate(agg, EmailMessage.class, Document.class).getMappedResults()) {
            if (d.get("_id") instanceof String name && d.get("n") instanceof Number n) {
                try {
                    out.put(EmailClassification.valueOf(name), n.longValue());
                } catch (IllegalArgumentException ignored) {
                    // unknown value (written by another version): skip
                }
            }
        }
        return out;
    }

    @Override
    public List<EmailMessage> findUnreadActionRequired(Long userId) {
        return load(query(where("userId").is(userId).and("jobRelated").is(true).and("read").is(false).and("actionRequired").is(true))
                .with(Sort.by(Sort.Order.desc("receivedAt"))));
    }

    @Override
    public List<EmailMessage> findNeedsReview(Long userId) {
        return load(query(where("userId").is(userId).and("jobRelated").is(true).and("needsReview").is(true))
                .with(Sort.by(Sort.Order.desc("receivedAt"))));
    }

    @Override
    public List<EmailMessage> findForCompany(Long userId, Long companyId, String companyName, int limit) {
        List<Long> appIds = companyApplicationIds(userId, companyId);
        Criteria byApp = where("applicationId").in(appIds);
        Criteria either = byApp;
        if (companyName != null) {
            Criteria byName = new Criteria().andOperator(
                    where("applicationId").is(null),
                    where("detectedCompany").regex("^" + Pattern.quote(companyName) + "$", "i"));
            either = new Criteria().orOperator(byApp, byName);
        }
        Criteria c = new Criteria().andOperator(where("userId").is(userId), where("jobRelated").is(true), either);
        return load(query(c).with(Sort.by(Sort.Order.desc("receivedAt"))).limit(limit));
    }

    @Override
    public Map<String, Long> countBySenderForCompany(Long userId, Long companyId) {
        List<Long> appIds = companyApplicationIds(userId, companyId);
        Map<String, Long> out = new HashMap<>();
        if (appIds.isEmpty()) {
            return out;
        }
        Query q = query(where("userId").is(userId).and("applicationId").in(appIds).and("jobRelated").is(true));
        q.fields().include("senderEmail");
        for (EmailMessage e : ops.find(q, EmailMessage.class)) {
            if (e.getSenderEmail() != null) {
                out.merge(e.getSenderEmail().toLowerCase(Locale.ROOT), 1L, Long::sum);
            }
        }
        return out;
    }

    @Override
    public long countForCompany(Long userId, Long companyId) {
        List<Long> appIds = companyApplicationIds(userId, companyId);
        if (appIds.isEmpty()) {
            return 0;
        }
        return ops.count(query(where("userId").is(userId).and("applicationId").in(appIds).and("jobRelated").is(true)),
                EmailMessage.class);
    }

    @Override
    public List<EmailMessage> search(Long userId, String needle, int limit) {
        String regex = Pattern.quote(needle.trim());
        Criteria c = new Criteria().andOperator(where("userId").is(userId), where("jobRelated").is(true),
                new Criteria().orOperator(
                where("subject").regex(regex, "i"),
                where("senderEmail").regex(regex, "i"),
                where("senderName").regex(regex, "i")));
        return load(query(c).with(Sort.by(Sort.Order.desc("receivedAt"))).limit(limit));
    }

    @Override
    public long deleteByApplicationId(Long userId, Long appId) {
        return cascade.deleteEmails(where("userId").is(userId).and("applicationId").is(appId));
    }

    @Override
    public long deleteByAccountId(Long userId, Long accountId) {
        return cascade.deleteEmails(where("userId").is(userId).and("emailAccountId").is(accountId));
    }

    @Override
    public long deleteDemo(Long userId) {
        return cascade.deleteEmails(where("userId").is(userId).and("demo").is(true));
    }

    @Override
    public long deleteAllBulk(Long userId) {
        return cascade.deleteAllEmails(userId);
    }

    private List<Long> companyApplicationIds(Long userId, Long companyId) {
        Query q = query(where("userId").is(userId).and("companyId").is(companyId));
        q.fields().include("_id");
        return ops.find(q, JobApplication.class).stream().map(JobApplication::getId).toList();
    }

    private List<EmailMessage> load(Query q) {
        return refs.emails(ops.find(q, EmailMessage.class));
    }
}
