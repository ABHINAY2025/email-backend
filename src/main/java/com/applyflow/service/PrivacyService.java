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
import com.applyflow.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Privacy actions; each one only ever touches the current user's data. */
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

    /** A sync of one of the user's mailboxes running during a wipe would keep writing into half-deleted data. */
    private void requireNoSync(Long userId) {
        if (syncService.isAnyRunningFor(userId)) {
            throw new ConflictException("A mail sync is running. Wait for it to finish, then try again.");
        }
    }

    /** Deletes all of the user's emails and email-derived events; applications are kept; cursors reset. */
    @Transactional
    public void clearImportedMail() {
        Long userId = CurrentUser.id();
        requireNoSync(userId);
        eventRepository.deleteEmailDerived(userId);
        notificationRepository.deleteEmailLinked(userId);
        emailRepository.deleteAllBulk(userId);
        accountRepository.resetAllCursors(userId);
        log.info("Privacy: cleared imported mail of user {}", userId);
    }

    /** Deletes the user's seeded demo data (applications, emails, companies, demo account). */
    @Transactional
    public void clearDemoData() {
        Long userId = CurrentUser.id();
        emailRepository.deleteDemo(userId);
        cascade.deleteApplications(where("userId").is(userId).and("demo").is(true));
        cascade.deleteUnusedCompanies(userId, true);
        for (EmailAccount a : accountRepository.findByUserIdAndProvider(userId, EmailProvider.DEMO)) {
            cascade.deleteAccount(a.getId());
        }
        log.info("Privacy: cleared demo data of user {}", userId);
    }

    /** Deletes all of the user's data except the user, settings and email accounts. */
    @Transactional
    public void deleteAllData() {
        Long userId = CurrentUser.id();
        requireNoSync(userId);
        notificationRepository.deleteByUserId(userId);
        emailRepository.deleteAllBulk(userId);
        mongo.remove(query(where("userId").is(userId)), ApplicationEventEntity.class);
        mongo.remove(query(where("userId").is(userId)), StatusHistory.class);
        mongo.remove(query(where("userId").is(userId)), Note.class);
        mongo.remove(query(where("userId").is(userId)), EmailMatchSuggestion.class);
        mongo.remove(query(where("userId").is(userId)), JobApplication.class);
        mongo.remove(query(where("userId").is(userId)), Contact.class);
        cascade.deleteUnusedCompanies(userId, false);
        syncJobRepository.deleteByUserId(userId);
        accountRepository.resetAllCursors(userId);
        log.info("Privacy: deleted all application data of user {}", userId);
    }
}
