package com.applyflow.scheduler;

import com.applyflow.common.SyncStatus;
import com.applyflow.config.AppProperties;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.SyncJob;
import com.applyflow.repository.SyncJobRepository;
import com.applyflow.exception.ApiException;
import com.applyflow.repository.EmailAccountRepository;
import com.applyflow.service.SettingsService;
import com.applyflow.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Periodic sync: a 1-minute tick checks each enabled, non-demo account against the current
 * {@code syncIntervalMinutes} setting (so interval changes apply without restart). Accounts in ERROR are retried
 * less often (at least every 30 minutes) to avoid hammering a server with bad credentials.
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);
    private static final Duration ERROR_BACKOFF = Duration.ofMinutes(30);

    private final AppProperties props;
    private final EmailAccountRepository accountRepository;
    private final SyncService syncService;
    private final SettingsService settingsService;
    private final SyncJobRepository jobRepository;

    public SyncScheduler(AppProperties props, EmailAccountRepository accountRepository, SyncService syncService,
                         SettingsService settingsService, SyncJobRepository jobRepository) {
        this.jobRepository = jobRepository;
        this.props = props;
        this.accountRepository = accountRepository;
        this.syncService = syncService;
        this.settingsService = settingsService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void tick() {
        if (!props.sync().enabled()) {
            return;
        }
        try {
            Duration interval = Duration.ofMinutes(Math.max(1, settingsService.current().syncIntervalMinutes()));
            Instant now = Instant.now();
            for (EmailAccount a : accountRepository.findAllByOrderByCreatedAtAsc()) {
                if (!a.isEnabled() || a.isDemo() || syncService.isRunning(a.getId())) {
                    continue;
                }
                Duration wait = a.getSyncStatus() == SyncStatus.ERROR && interval.compareTo(ERROR_BACKOFF) < 0
                        ? ERROR_BACKOFF : interval;
                // Leave a little slack so a 5-minute interval does not drift to 6 minutes with a 1-minute tick.
                Instant lastAttempt = a.getLastSyncAt();
                Instant lastJob = jobRepository.findFirstByEmailAccountIdOrderByStartedAtDesc(a.getId())
                        .map(SyncJob::getStartedAt).orElse(null);
                if (lastJob != null && (lastAttempt == null || lastJob.isAfter(lastAttempt))) {
                    lastAttempt = lastJob;
                }
                if (lastAttempt == null || lastAttempt.plus(wait).minusSeconds(30).isBefore(now)) {
                    try {
                        syncService.start(a.getId());
                    } catch (ApiException e) {
                        log.debug("Scheduled sync skipped for account {}: {}", a.getId(), e.getCode());
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Scheduled sync tick failed: {}", e.getMessage());
        }
    }
}
