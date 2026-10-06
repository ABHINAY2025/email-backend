package com.applyflow.service;

import com.applyflow.common.NotificationType;
import com.applyflow.common.SyncJobStatus;
import com.applyflow.common.SyncStatus;
import com.applyflow.config.AppProperties;
import com.applyflow.dto.AccountDtos.AccountSyncState;
import com.applyflow.dto.AccountDtos.SyncJobDto;
import com.applyflow.dto.AccountDtos.SyncStatusResponse;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.SyncJob;
import com.applyflow.exception.BadRequestException;
import com.applyflow.exception.ImapException;
import com.applyflow.exception.NotFoundException;
import com.applyflow.exception.SyncInProgressException;
import com.applyflow.intelligence.ClassificationResult;
import com.applyflow.intelligence.EmailIntelligenceService;
import com.applyflow.intelligence.PrefilterDecision;
import com.applyflow.mail.MailProcessingPipeline;
import com.applyflow.mail.ProcessingOutcome;
import com.applyflow.mail.imap.ImapConnectionSettings;
import com.applyflow.mail.imap.ImapMailboxReader;
import com.applyflow.mail.parser.EmailHeaders;
import com.applyflow.mail.parser.ParsedEmail;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.persistence.RefLoader;
import com.applyflow.repository.EmailAccountRepository;
import com.applyflow.repository.SyncJobRepository;
import com.applyflow.security.CredentialEncryptor;
import com.applyflow.security.CurrentUser;
import com.applyflow.sse.ServerEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs mailbox synchronisation jobs (one at a time per account) on a bounded executor. Each job runs on behalf of
 * the mailbox owner ({@link CurrentUser#runAs}), so everything it creates belongs to that user.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    /** Running account id → owner id. */
    private final Map<Long, Long> running = new ConcurrentHashMap<>();

    private final EmailAccountRepository accountRepository;
    private final SyncJobRepository jobRepository;
    private final ImapMailboxReader reader;
    private final MailProcessingPipeline pipeline;
    private final EmailIntelligenceService intelligence;
    private final CredentialEncryptor encryptor;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final ServerEventPublisher events;
    private final DtoMapper mapper;
    private final ThreadPoolTaskExecutor executor;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final InboxService inboxService;
    private final RefLoader refLoader;

    public SyncService(EmailAccountRepository accountRepository, SyncJobRepository jobRepository,
                       ImapMailboxReader reader, MailProcessingPipeline pipeline,
                       EmailIntelligenceService intelligence, CredentialEncryptor encryptor,
                       NotificationService notificationService, SettingsService settingsService,
                       ServerEventPublisher events, DtoMapper mapper,
                       @Qualifier("syncExecutor") ThreadPoolTaskExecutor executor,
                       @Qualifier("requiresNewTransactionTemplate") TransactionTemplate tx, AppProperties props,
                       InboxService inboxService, RefLoader refLoader) {
        this.refLoader = refLoader;
        this.inboxService = inboxService;
        this.accountRepository = accountRepository;
        this.jobRepository = jobRepository;
        this.reader = reader;
        this.pipeline = pipeline;
        this.intelligence = intelligence;
        this.encryptor = encryptor;
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.events = events;
        this.mapper = mapper;
        this.executor = executor;
        this.tx = tx;
        this.props = props;
    }

    /** Jobs left RUNNING by a previous process (crash/restart) are marked failed on startup. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        tx.executeWithoutResult(s -> {
            for (SyncJob j : jobRepository.findByStatus(SyncJobStatus.RUNNING)) {
                j.setStatus(SyncJobStatus.FAILED);
                j.setFinishedAt(Instant.now());
                j.setError("Interrupted by a server restart.");
                jobRepository.save(j);
            }
            for (EmailAccount a : accountRepository.findAll()) {
                if (a.getSyncStatus() == SyncStatus.SYNCING) {
                    a.setSyncStatus(a.getLastError() == null ? SyncStatus.CONNECTED : SyncStatus.ERROR);
                    accountRepository.save(a);
                }
            }
        });
    }

    public boolean isRunning(Long accountId) {
        return running.containsKey(accountId);
    }

    /** True while any mailbox of the given user is syncing. */
    public boolean isAnyRunningFor(Long userId) {
        return running.containsValue(userId);
    }

    /** Starts a sync for one of the current user's accounts. Returns the RUNNING job. */
    public SyncJobDto start(Long accountId) {
        EmailAccount account = accountRepository.findByIdAndUserId(accountId, CurrentUser.id())
                .orElseThrow(() -> NotFoundException.of("Email account", accountId));
        if (account.isDemo()) {
            throw new BadRequestException("The demo account cannot be synced.");
        }
        Long owner = account.getUserId();
        if (running.putIfAbsent(accountId, owner) != null) {
            throw new SyncInProgressException(account.getEmail());
        }
        SyncJobDto job;
        try {
            job = tx.execute(s -> {
                EmailAccount a = accountRepository.findById(accountId).orElseThrow();
                SyncJob j = new SyncJob();
                j.setEmailAccount(a);
                j.setStatus(SyncJobStatus.RUNNING);
                j.setStartedAt(Instant.now());
                jobRepository.save(j);
                a.setSyncStatus(SyncStatus.SYNCING);
                accountRepository.save(a);
                SyncJobDto dto = mapper.toSyncJob(j);
                events.publish(owner, ServerEventPublisher.SYNC_STARTED, dto);
                return dto;
            });
        } catch (RuntimeException e) {
            running.remove(accountId);
            throw e;
        }
        Long jobId = Objects.requireNonNull(job).id();
        try {
            executor.execute(() -> CurrentUser.runAs(owner, () -> run(accountId, jobId, owner)));
        } catch (RejectedExecutionException e) {
            running.remove(accountId);
            finishFailed(accountId, jobId, owner, "Too many syncs are queued. Please try again shortly.");
            throw new SyncInProgressException(account.getEmail());
        }
        return job;
    }

    /** Starts syncs for all of the current user's enabled, non-demo accounts that are not already syncing. */
    public int startAll() {
        int started = 0;
        for (EmailAccount a : accountRepository.findByUserIdOrderByCreatedAtAsc(CurrentUser.id())) {
            if (!a.isEnabled() || a.isDemo() || running.containsKey(a.getId())) {
                continue;
            }
            try {
                start(a.getId());
                started++;
            } catch (SyncInProgressException | BadRequestException e) {
                // skip
            }
        }
        return started;
    }

    private static final class Counters {
        int fetched;
        int processed;
        int jobEmails;
        int created;
        int updated;
        int batchProcessed;
        int batchJobEmails;
    }

    void run(Long accountId, Long jobId, Long owner) {
        Counters c = new Counters();
        try {
            record Snapshot(ImapConnectionSettings settings, ImapMailboxReader.Cursor cursor) {
            }
            Snapshot snap = tx.execute(s -> {
                EmailAccount a = accountRepository.findById(accountId).orElseThrow();
                String password = encryptor.decrypt(a.getEncryptedPassword());
                return new Snapshot(new ImapConnectionSettings(a.getProvider(), a.getHost(), a.getPort(), a.isSsl(),
                        a.getUsername(), password, a.getFolder()),
                        new ImapMailboxReader.Cursor(a.getLastUid(), a.getUidValidity(), a.getInitialSyncDays()));
            });
            Objects.requireNonNull(snap);
            log.info("Sync started for account {} (job {})", accountId, jobId);

            reader.read(snap.settings(), snap.cursor(), accountId, Math.max(1, props.sync().batchSize()),
                    props.sync().maxBodyBytes(), new ImapMailboxReader.Handler() {
                        @Override
                        public void onMessage(long uid, EmailHeaders headers, ImapMailboxReader.BodyLoader loader)
                                throws Exception {
                            c.fetched++;
                            PrefilterDecision decision = intelligence.prefilter(headers);
                            if (decision == PrefilterDecision.REJECT) {
                                c.processed++;
                                c.batchProcessed++;
                                return; // body never downloaded
                            }
                            ParsedEmail email = loader.load();
                            ClassificationResult cr = pipeline.classify(email);
                            c.processed++;
                            c.batchProcessed++;
                            if (!cr.isJobRelated()) {
                                return; // never stored
                            }
                            ProcessingOutcome outcome = pipeline.process(email, cr);
                            if (outcome.stored()) {
                                c.jobEmails++;
                                c.batchJobEmails++;
                                if (outcome.applicationCreated()) {
                                    c.created++;
                                } else if (outcome.applicationUpdated()) {
                                    c.updated++;
                                }
                            }
                        }

                        @Override
                        public void onBatchComplete(long lastUid, long uidValidity, int fetched, int malformed) {
                            int processedDelta = c.batchProcessed + malformed;
                            int jobDelta = c.batchJobEmails;
                            c.processed += malformed;
                            c.batchProcessed = 0;
                            c.batchJobEmails = 0;
                            tx.executeWithoutResult(s -> {
                                EmailAccount a = accountRepository.findById(accountId).orElseThrow();
                                if (a.getLastUid() == null || lastUid > a.getLastUid()
                                        || !Objects.equals(a.getUidValidity(), uidValidity)) {
                                    a.setLastUid(lastUid);
                                }
                                a.setUidValidity(uidValidity);
                                a.setEmailsProcessed(a.getEmailsProcessed() + processedDelta);
                                a.setJobEmails(a.getJobEmails() + jobDelta);
                                accountRepository.save(a);
                                jobRepository.findById(jobId).ifPresent(j -> {
                                    applyCounters(j, c);
                                    jobRepository.save(j);
                                });
                            });
                        }
                    });

            try {
                int adopted = inboxService.reconcileOrphans();
                c.updated += adopted;
            } catch (RuntimeException ex) {
                log.warn("Post-sync reconciliation failed for account {} ({})", accountId, ex.getClass().getSimpleName());
            }

            SyncJobDto done = tx.execute(s -> {
                EmailAccount a = accountRepository.findById(accountId).orElseThrow();
                a.setSyncStatus(SyncStatus.CONNECTED);
                a.setLastSyncAt(Instant.now());
                a.setLastError(null);
                SyncJob j = jobRepository.findById(jobId).orElseThrow();
                applyCounters(j, c);
                j.setStatus(SyncJobStatus.COMPLETED);
                j.setFinishedAt(Instant.now());
                accountRepository.save(a);
                jobRepository.save(j);
                SyncJobDto dto = mapper.toSyncJob(j);
                events.publish(owner, ServerEventPublisher.SYNC_COMPLETED, dto);
                return dto;
            });
            log.info("Sync completed for account {}: fetched={}, jobEmails={}, created={}, updated={}", accountId,
                    c.fetched, c.jobEmails, c.created, c.updated);
            Objects.requireNonNull(done);
        } catch (ImapException e) {
            log.warn("Sync failed for account {}: {}", accountId, e.getCode());
            finishFailed(accountId, jobId, owner, e.getMessage(), c);
        } catch (IllegalStateException e) {
            log.warn("Sync failed for account {}: {}", accountId, e.getMessage());
            finishFailed(accountId, jobId, owner, e.getMessage() != null && e.getMessage().contains("decrypt")
                    ? "Stored app password could not be decrypted. Please re-enter it." : "Sync failed unexpectedly.", c);
        } catch (Exception e) {
            log.error("Sync failed for account {}", accountId, e);
            finishFailed(accountId, jobId, owner, "Sync failed due to an unexpected error. Please try again.", c);
        } finally {
            running.remove(accountId);
        }
    }

    private static void applyCounters(SyncJob j, Counters c) {
        j.setMessagesFetched(c.fetched);
        j.setMessagesProcessed(c.processed);
        j.setJobEmailsFound(c.jobEmails);
        j.setApplicationsCreated(c.created);
        j.setApplicationsUpdated(c.updated);
    }

    private void finishFailed(Long accountId, Long jobId, Long owner, String message) {
        finishFailed(accountId, jobId, owner, message, new Counters());
    }

    private void finishFailed(Long accountId, Long jobId, Long owner, String message, Counters c) {
        try {
            tx.executeWithoutResult(s -> {
                EmailAccount a = accountRepository.findById(accountId).orElse(null);
                SyncJob j = jobRepository.findById(jobId).orElse(null);
                if (a != null) {
                    a.setSyncStatus(SyncStatus.ERROR);
                    a.setLastError(message);
                    accountRepository.save(a);
                }
                if (j != null) {
                    applyCounters(j, c);
                    j.setStatus(SyncJobStatus.FAILED);
                    j.setFinishedAt(Instant.now());
                    j.setError(message);
                    jobRepository.save(j);
                    events.publish(owner, ServerEventPublisher.SYNC_FAILED, mapper.toSyncJob(j));
                }
                notificationService.create(owner, NotificationType.SYNC_FAILURE,
                        "Sync failed" + (a == null ? "" : " — " + a.getEmail()), message, null, null);
            });
        } catch (RuntimeException e) {
            log.error("Could not record sync failure for account {}", accountId, e);
        }
    }

    // ------------------------------------------------------------------ status

    /** Sync state of the current user's mailboxes and jobs only. */
    @Transactional(readOnly = true)
    public SyncStatusResponse status() {
        Long userId = CurrentUser.id();
        List<EmailAccount> accounts = accountRepository.findByUserIdOrderByCreatedAtAsc(userId);
        boolean syncing = accounts.stream().anyMatch(a -> running.containsKey(a.getId())
                || a.getSyncStatus() == SyncStatus.SYNCING);
        boolean error = accounts.stream().anyMatch(a -> a.isEnabled() && !a.isDemo()
                && a.getSyncStatus() == SyncStatus.ERROR);
        String state = syncing ? "SYNCING" : error ? "ERROR" : "IDLE";
        Instant lastSync = accounts.stream().map(EmailAccount::getLastSyncAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        String lastError = accounts.stream().filter(a -> a.getSyncStatus() == SyncStatus.ERROR)
                .map(EmailAccount::getLastError).filter(Objects::nonNull).findFirst().orElse(null);
        List<AccountSyncState> states = accounts.stream()
                .map(a -> new AccountSyncState(a.getId(), a.getEmail(),
                        running.containsKey(a.getId()) ? SyncStatus.SYNCING : a.getSyncStatus(), a.getLastSyncAt(),
                        a.getLastError()))
                .toList();
        List<SyncJobDto> jobs = refLoader.syncJobs(jobRepository.findByUserIdOrderByStartedAtDescIdDesc(userId,
                PageRequest.of(0, 10))).stream().filter(j -> j.getEmailAccount() != null).map(mapper::toSyncJob)
                .toList();
        return new SyncStatusResponse(state, lastSync, lastError, settingsService.forUser(userId).syncIntervalMinutes(),
                states, jobs);
    }
}
