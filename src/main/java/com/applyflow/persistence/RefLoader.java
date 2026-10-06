package com.applyflow.persistence;

import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.Company;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.EmailMatchSuggestion;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.SyncJob;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Batch-loads the documents referenced by id from a list of documents (one {@code $in} query per referenced
 * collection instead of one lookup per row), replacing the former JPA {@code join fetch}es.
 */
@Component
public class RefLoader {

    private final MongoOperations ops;

    public RefLoader(MongoOperations ops) {
        this.ops = ops;
    }

    /** Loads documents by id; missing ids are simply absent from the map. */
    public <T extends MongoEntity> Map<Long, T> byIds(Class<T> type, Collection<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>(ids);
        distinct.remove(null);
        Map<Long, T> out = new HashMap<>();
        if (distinct.isEmpty()) {
            return out;
        }
        for (T t : ops.find(Query.query(Criteria.where("_id").in(distinct)), type)) {
            out.put(t.getId(), t);
        }
        return out;
    }

    /** Applications with their company and mailbox. */
    public List<JobApplication> applications(List<JobApplication> apps) {
        if (apps.isEmpty()) {
            return apps;
        }
        Map<Long, Company> companies = byIds(Company.class, ids(apps, JobApplication::getCompanyId));
        Map<Long, EmailAccount> accounts = byIds(EmailAccount.class, ids(apps, JobApplication::getEmailAccountId));
        for (JobApplication a : apps) {
            if (a.getCompanyId() != null && companies.containsKey(a.getCompanyId())) {
                a.setCompany(companies.get(a.getCompanyId()));
            }
            if (a.getEmailAccountId() != null && accounts.containsKey(a.getEmailAccountId())) {
                a.setEmailAccount(accounts.get(a.getEmailAccountId()));
            }
        }
        return apps;
    }

    /** Emails with their application (and its company) and mailbox. */
    public List<EmailMessage> emails(List<EmailMessage> emails) {
        if (emails.isEmpty()) {
            return emails;
        }
        Map<Long, JobApplication> apps = byIds(JobApplication.class, ids(emails, EmailMessage::getApplicationId));
        applications(List.copyOf(apps.values()));
        Map<Long, EmailAccount> accounts = byIds(EmailAccount.class, ids(emails, EmailMessage::getEmailAccountId));
        for (EmailMessage e : emails) {
            if (e.getApplicationId() != null && apps.containsKey(e.getApplicationId())) {
                e.setApplication(apps.get(e.getApplicationId()));
            }
            if (e.getEmailAccountId() != null && accounts.containsKey(e.getEmailAccountId())) {
                e.setEmailAccount(accounts.get(e.getEmailAccountId()));
            }
        }
        return emails;
    }

    /** Events with their application (and its company). The email is not loaded (use {@code getEmailId()}). */
    public List<ApplicationEventEntity> events(List<ApplicationEventEntity> events) {
        if (events.isEmpty()) {
            return events;
        }
        Map<Long, JobApplication> apps = byIds(JobApplication.class,
                ids(events, ApplicationEventEntity::getApplicationId));
        applications(List.copyOf(apps.values()));
        for (ApplicationEventEntity ev : events) {
            if (ev.getApplicationId() != null && apps.containsKey(ev.getApplicationId())) {
                ev.setApplication(apps.get(ev.getApplicationId()));
            }
        }
        return events;
    }

    /** Suggestions with their application (and its company). */
    public List<EmailMatchSuggestion> suggestions(List<EmailMatchSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return suggestions;
        }
        Map<Long, JobApplication> apps = byIds(JobApplication.class,
                ids(suggestions, EmailMatchSuggestion::getApplicationId));
        applications(List.copyOf(apps.values()));
        for (EmailMatchSuggestion s : suggestions) {
            if (s.getApplicationId() != null && apps.containsKey(s.getApplicationId())) {
                s.setApplication(apps.get(s.getApplicationId()));
            }
        }
        return suggestions;
    }

    /** Sync jobs with their mailbox. */
    public List<SyncJob> syncJobs(List<SyncJob> jobs) {
        if (jobs.isEmpty()) {
            return jobs;
        }
        Map<Long, EmailAccount> accounts = byIds(EmailAccount.class, ids(jobs, SyncJob::getEmailAccountId));
        for (SyncJob j : jobs) {
            if (j.getEmailAccountId() != null && accounts.containsKey(j.getEmailAccountId())) {
                j.setEmailAccount(accounts.get(j.getEmailAccountId()));
            }
        }
        return jobs;
    }

    private static <T> List<Long> ids(List<T> list, Function<T, Long> id) {
        return list.stream().map(id).filter(Objects::nonNull).distinct().toList();
    }
}
