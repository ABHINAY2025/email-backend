package com.applyflow.persistence;

import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.Company;
import com.applyflow.entity.Contact;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.EmailClassificationLog;
import com.applyflow.entity.EmailMatchSuggestion;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.Note;
import com.applyflow.entity.Notification;
import com.applyflow.entity.StatusHistory;
import com.applyflow.entity.SyncJob;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Deletes documents together with their dependents, reproducing the foreign-key rules of the former SQL schema:
 * <ul>
 *   <li>email → classification logs, match suggestions, notifications deleted; events / status history keep the
 *       row with the email reference cleared (ON DELETE SET NULL)</li>
 *   <li>application → match suggestions, events, status history, notes, notifications deleted; emails are kept but
 *       unlinked</li>
 *   <li>company → contacts and applications (cascading as above) deleted</li>
 *   <li>mailbox → its emails (cascading) and sync jobs deleted; applications are kept but unlinked</li>
 * </ul>
 * Runs inside the caller's transaction when there is one.
 */
@Component
public class CascadeDeleter {

    private final MongoOperations ops;

    public CascadeDeleter(MongoOperations ops) {
        this.ops = ops;
    }

    // ------------------------------------------------------------------ emails

    public long deleteEmails(Criteria criteria) {
        List<Long> ids = ids(query(criteria), EmailMessage.class);
        if (ids.isEmpty()) {
            return 0;
        }
        removeEmailDependents(ids);
        return ops.remove(query(where("_id").in(ids)), EmailMessage.class).getDeletedCount();
    }

    public long deleteEmail(Long id) {
        return deleteEmails(where("_id").is(id));
    }

    /** Deletes every email and everything that depends on emails only. */
    public long deleteAllEmails() {
        ops.remove(new Query(), EmailClassificationLog.class);
        ops.remove(new Query(), EmailMatchSuggestion.class);
        ops.remove(query(where("emailId").ne(null)), Notification.class);
        ops.updateMulti(query(where("emailId").exists(true)), new Update().unset("emailId"),
                ApplicationEventEntity.class);
        ops.updateMulti(query(where("emailId").exists(true)), new Update().unset("emailId"), StatusHistory.class);
        return ops.remove(new Query(), EmailMessage.class).getDeletedCount();
    }

    private void removeEmailDependents(Collection<Long> emailIds) {
        ops.remove(query(where("emailId").in(emailIds)), EmailClassificationLog.class);
        ops.remove(query(where("emailId").in(emailIds)), EmailMatchSuggestion.class);
        ops.remove(query(where("emailId").in(emailIds)), Notification.class);
        ops.updateMulti(query(where("emailId").in(emailIds)), new Update().unset("emailId"),
                ApplicationEventEntity.class);
        ops.updateMulti(query(where("emailId").in(emailIds)), new Update().unset("emailId"), StatusHistory.class);
    }

    // ------------------------------------------------------------------ applications

    public long deleteApplications(Criteria criteria) {
        List<Long> ids = ids(query(criteria), JobApplication.class);
        if (ids.isEmpty()) {
            return 0;
        }
        ops.updateMulti(query(where("applicationId").in(ids)), new Update().unset("applicationId"),
                EmailMessage.class);
        ops.remove(query(where("applicationId").in(ids)), EmailMatchSuggestion.class);
        ops.remove(query(where("applicationId").in(ids)), ApplicationEventEntity.class);
        ops.remove(query(where("applicationId").in(ids)), StatusHistory.class);
        ops.remove(query(where("applicationId").in(ids)), Note.class);
        ops.remove(query(where("applicationId").in(ids)), Notification.class);
        return ops.remove(query(where("_id").in(ids)), JobApplication.class).getDeletedCount();
    }

    public long deleteApplication(Long id) {
        return deleteApplications(where("_id").is(id));
    }

    // ------------------------------------------------------------------ companies

    public long deleteCompanies(Criteria criteria) {
        List<Long> ids = ids(query(criteria), Company.class);
        if (ids.isEmpty()) {
            return 0;
        }
        deleteApplications(where("companyId").in(ids));
        ops.remove(query(where("companyId").in(ids)), Contact.class);
        return ops.remove(query(where("_id").in(ids)), Company.class).getDeletedCount();
    }

    /** Companies no application refers to (optionally only demo ones). */
    public long deleteUnusedCompanies(boolean demoOnly) {
        List<Long> used = ops.findDistinct(new Query(), "companyId", JobApplication.class, Long.class);
        Criteria c = where("_id").nin(used);
        if (demoOnly) {
            c = c.and("demo").is(true);
        }
        return deleteCompanies(c);
    }

    // ------------------------------------------------------------------ mailboxes

    public void deleteAccount(Long accountId) {
        deleteEmails(where("emailAccountId").is(accountId));
        ops.updateMulti(query(where("emailAccountId").is(accountId)), new Update().unset("emailAccountId"),
                JobApplication.class);
        ops.remove(query(where("emailAccountId").is(accountId)), SyncJob.class);
        ops.remove(query(where("_id").is(accountId)), EmailAccount.class);
    }

    // ------------------------------------------------------------------ helpers

    private <T extends MongoEntity> List<Long> ids(Query q, Class<T> type) {
        q.fields().include("_id");
        return ops.find(q, type).stream().map(MongoEntity::getId).toList();
    }
}
