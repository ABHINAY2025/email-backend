package com.applyflow.service;

import com.applyflow.common.EmailProvider;
import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.Contact;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.EmailMatchSuggestion;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.Note;
import com.applyflow.entity.StatusHistory;
import com.applyflow.exception.ConflictException;
import com.applyflow.persistence.CascadeDeleter;
import com.applyflow.repository.ApplicationEventRepository;
import com.applyflow.repository.EmailAccountRepository;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.repository.NotificationRepository;
import com.applyflow.repository.SyncJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);

    private final EmailMessageRepository emailRepository;
    private final ApplicationEventRepository eventRepository;
    private final EmailAccountRepository accountRepository;
    private final NotificationRepository notificationRepository;
    private final SyncJobRepository syncJobRepository;
    private final CascadeDeleter cascade;
    private final MongoOperations mongo;
    private final SyncService syncService;

    public PrivacyService(EmailMessageRepository emailRepository, ApplicationEventRepository eventRepository,
                          EmailAccountRepository accountRepository, NotificationRepository notificationRepository,
                          SyncJobRepository syncJobRepository, CascadeDeleter cascade, MongoOperations mongo,
                          SyncService syncService) {
        this.syncService = syncService;
        this.emailRepository = emailRepository;
        this.eventRepository = eventRepository;
        this.accountRepository = accountRepository;
        this.notificationRepository = notificationRepository;
        this.syncJobRepository = syncJobRepository;
        this.cascade = cascade;
        this.mongo = mongo;
    }

    /** A sync running during a wipe would keep writing into half-deleted data. */
    private void requireNoSync() {
        if (syncService.isAnyRunning()) {
            throw new ConflictException("A mail sync is running. Wait for it to finish, then try again.");
        }
    }

    /** Deletes all emails and email-derived events; applications are kept; cursors reset. */
    @Transactional
    public void clearImportedMail() {
        requireNoSync();
        eventRepository.deleteEmailDerived();
        notificationRepository.deleteEmailLinked();
        emailRepository.deleteAllBulk();
        accountRepository.resetAllCursors();
        log.info("Privacy: cleared all imported mail");
    }

    /** Deletes seeded demo data (applications, emails, companies, demo account). */
    @Transactional
    public void clearDemoData() {
        emailRepository.deleteDemo();
        cascade.deleteApplications(where("demo").is(true));
        cascade.deleteUnusedCompanies(true);
        for (EmailAccount a : accountRepository.findByProvider(EmailProvider.DEMO)) {
            cascade.deleteAccount(a.getId());
        }
        log.info("Privacy: cleared demo data");
    }

    /** Deletes everything except the user, settings and email accounts. */
    @Transactional
    public void deleteAllData() {
        requireNoSync();
        notificationRepository.deleteAll();
        emailRepository.deleteAllBulk();
        mongo.remove(new Query(), ApplicationEventEntity.class);
        mongo.remove(new Query(), StatusHistory.class);
        mongo.remove(new Query(), Note.class);
        mongo.remove(new Query(), EmailMatchSuggestion.class);
        mongo.remove(new Query(), JobApplication.class);
        mongo.remove(new Query(), Contact.class);
        cascade.deleteUnusedCompanies(false);
        syncJobRepository.deleteAll();
        accountRepository.resetAllCursors();
        log.info("Privacy: deleted all application data");
    }
}
