package com.applyflow.seed;

import com.applyflow.application.service.CompanyService;
import com.applyflow.application.timeline.TimelineService;
import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailProvider;
import com.applyflow.common.EventType;
import com.applyflow.common.SyncJobStatus;
import com.applyflow.common.SyncStatus;
import com.applyflow.config.AppProperties;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.Note;
import com.applyflow.entity.Notification;
import com.applyflow.entity.SyncJob;
import com.applyflow.mail.MailProcessingPipeline;
import com.applyflow.mail.ProcessingOutcome;
import com.applyflow.mail.parser.ParsedEmail;
import com.applyflow.repository.CompanyRepository;
import com.applyflow.repository.EmailAccountRepository;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.repository.JobApplicationRepository;
import com.applyflow.repository.NoteRepository;
import com.applyflow.repository.NotificationRepository;
import com.applyflow.repository.SyncJobRepository;
import com.applyflow.security.CredentialEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds a realistic demo data set (when enabled and the database has no applications). Demo emails are run through
 * the real {@link MailProcessingPipeline}, so the seed exercises the actual classifier, matcher and status engine.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_EMAIL = "demo@applyflow.local";

    private final AppProperties props;
    private final JobApplicationRepository applicationRepository;
    private final EmailAccountRepository accountRepository;
    private final EmailMessageRepository emailRepository;
    private final NoteRepository noteRepository;
    private final NotificationRepository notificationRepository;
    private final SyncJobRepository syncJobRepository;
    private final CompanyService companyService;
    private final CompanyRepository companyRepository;
    private final TimelineService timelineService;
    private final MailProcessingPipeline pipeline;
    private final CredentialEncryptor encryptor;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zone;

    public DemoDataSeeder(AppProperties props, JobApplicationRepository applicationRepository,
                          EmailAccountRepository accountRepository, EmailMessageRepository emailRepository,
                          NoteRepository noteRepository, NotificationRepository notificationRepository,
                          SyncJobRepository syncJobRepository, CompanyService companyService,
                          CompanyRepository companyRepository,
                          TimelineService timelineService, MailProcessingPipeline pipeline,
                          CredentialEncryptor encryptor,
                          @Qualifier("requiresNewTransactionTemplate") TransactionTemplate tx, Clock clock,
                          ZoneId appZone) {
        this.props = props;
        this.applicationRepository = applicationRepository;
        this.accountRepository = accountRepository;
        this.emailRepository = emailRepository;
        this.noteRepository = noteRepository;
        this.notificationRepository = notificationRepository;
        this.syncJobRepository = syncJobRepository;
        this.companyService = companyService;
        this.companyRepository = companyRepository;
        this.timelineService = timelineService;
        this.pipeline = pipeline;
        this.encryptor = encryptor;
        this.tx = tx;
        this.clock = clock;
        this.zone = appZone;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            return;
        }
        if (applicationRepository.count() > 0) {
            log.info("Demo seed skipped: applications already exist");
            return;
        }
        try {
            seed();
        } catch (RuntimeException e) {
            log.error("Demo seed failed", e);
        }
    }

    private void seed() {
        Instant now = clock.instant();
        Long accountId = tx.execute(s -> {
            EmailAccount account = accountRepository.findFirstByEmailIgnoreCase(DEMO_EMAIL).orElseGet(EmailAccount::new);
            account.setEmail(DEMO_EMAIL);
            account.setProvider(EmailProvider.DEMO);
            account.setHost(EmailProvider.DEMO.defaultHost());
            account.setPort(993);
            account.setSsl(true);
            account.setUsername("demo");
            account.setEncryptedPassword(encryptor.encrypt("demo-" + UUID.randomUUID()));
            account.setFolder("INBOX");
            account.setEnabled(false);
            account.setSyncStatus(SyncStatus.DISCONNECTED);
            account.setInitialSyncDays(90);
            accountRepository.save(account);

            // Pre-create companies with their real domains/websites.
            String[][] companies = {
                    {"Amazon", "amazon.com"}, {"Google", "google.com"}, {"Microsoft", "microsoft.com"},
                    {"Deloitte", "deloitte.com"}, {"Accenture", "accenture.com"}, {"Stripe", "stripe.com"},
                    {"Atlassian", "atlassian.com"}, {"Netflix", "netflix.com"}, {"Uber", "uber.com"},
                    {"Flipkart", "flipkart.com"}, {"Salesforce", "salesforce.com"}, {"Adobe", "adobe.com"},
                    {"Infosys", "infosys.com"}};
            for (String[] c : companies) {
                var company = companyService.findOrCreate(c[0], null, true);
                company.setDomain(c[1]);
                company.setWebsite("https://www." + c[1]);
                companyRepository.save(company);
            }
            return account.getId();
        });

        List<DemoScenario.DemoMail> mails = DemoScenario.build(clock, zone);
        Map<String, String> threadRoots = new HashMap<>();
        Map<String, Long> appByThread = new HashMap<>();
        int stored = 0;
        for (DemoScenario.DemoMail m : mails) {
            String messageId = "<" + m.key() + ".demo@applyflow.local>";
            List<String> refs = List.of();
            String inReplyTo = null;
            if (m.thread() != null) {
                String root = threadRoots.putIfAbsent(m.thread(), messageId);
                if (root != null) {
                    refs = List.of(root);
                    inReplyTo = root;
                }
            }
            ParsedEmail email = new ParsedEmail(accountId, "demo:" + m.key(), messageId, inReplyTo, refs, null,
                    m.fromEmail(), m.fromName(), DEMO_EMAIL, m.subject(), m.receivedAt(), m.body().strip(), null,
                    false, null, true);
            ProcessingOutcome outcome = pipeline.process(email);
            if (outcome.stored()) {
                stored++;
                if (m.thread() != null && outcome.applicationId() != null) {
                    appByThread.putIfAbsent(m.thread(), outcome.applicationId());
                }
            } else {
                log.warn("Demo email {} was not stored ({})", m.key(), outcome.result());
            }
        }
        final int storedCount = stored;

        tx.executeWithoutResult(s -> postAdjust(accountId, appByThread, now, storedCount, mails.size()));
        log.info("Demo data seeded: {} emails, {} applications", stored, applicationRepository.count());
    }

    private void postAdjust(Long accountId, Map<String, Long> apps, Instant now, int stored, int total) {
        // Mixed sources (referrals / company site).
        setSource(apps.get("netflix-senior"), "Referral");
        setSource(apps.get("google-backend"), "Referral");
        setSource(apps.get("accenture-ase"), "Company site");

        // Amazon anchor: explicit "submitted" step before the confirmation.
        JobApplication amazon = load(apps.get("amazon-sde"));
        if (amazon != null && amazon.getAppliedAt() != null) {
            timelineService.add(amazon, null, EventType.APPLICATION_SUBMITTED, "Application submitted on amazon.jobs",
                    "You applied for Software Development Engineer via the Amazon careers site.",
                    amazon.getAppliedAt().minus(Duration.ofMinutes(12)), null, ApplicationStatus.APPLIED, null,
                    Actor.USER, null, null);
        }

        note(apps.get("amazon-sde"), "Prep plan: leadership principles stories (STAR), LRU cache + rate limiter "
                + "design, review DynamoDB partitioning.", now.minus(Duration.ofHours(20)));
        note(apps.get("google-backend"), "Recruiter: Ananya Iyer. Focus on graphs/DP; next round is two "
                + "45-min coding interviews.", now.minus(Duration.ofDays(2)));
        note(apps.get("stripe-backend"), "Offer: base in range + RSUs. Ask about remote policy and start date "
                + "before accepting.", now.minus(Duration.ofDays(1)));
        note(apps.get("accenture-ase"), "Assessment covers aptitude + 2 coding questions — do it tonight.",
                now.minus(Duration.ofHours(5)));
        note(apps.get("deloitte-swe"), "Referred by Karthik (ex-colleague). Ping him for a status update.",
                now.minus(Duration.ofDays(15)));

        // Older mail and notifications have been read already.
        for (EmailMessage e : emailRepository.findAll()) {
            if (e.isDemo() && e.getReceivedAt().isBefore(now.minus(Duration.ofDays(3)))) {
                e.setRead(true);
                emailRepository.save(e);
            }
        }
        for (Notification n : notificationRepository.findAll()) {
            if (n.getCreatedAt() != null && n.getCreatedAt().isBefore(now.minus(Duration.ofDays(4)))) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        }

        // Two completed sync runs for the demo mailbox.
        EmailAccount account = accountRepository.findById(accountId).orElseThrow();
        syncJob(account, now.minus(Duration.ofDays(1)).minus(Duration.ofMinutes(3)), Duration.ofSeconds(48),
                total - 6, total - 6, stored - 4, 3, 5);
        syncJob(account, now.minus(Duration.ofHours(2)), Duration.ofSeconds(21), 9, 9, 4, 1, 3);
        account.setLastSyncAt(now.minus(Duration.ofHours(2)).plusSeconds(21));
        account.setEmailsProcessed(total + 212L);
        account.setJobEmails(stored);
        accountRepository.save(account);
    }

    private void syncJob(EmailAccount account, Instant start, Duration took, int fetched, int processed, int jobs,
                         int created, int updated) {
        SyncJob j = new SyncJob();
        j.setEmailAccount(account);
        j.setStatus(SyncJobStatus.COMPLETED);
        j.setStartedAt(start);
        j.setFinishedAt(start.plus(took));
        j.setMessagesFetched(Math.max(0, fetched + 200));
        j.setMessagesProcessed(Math.max(0, processed + 200));
        j.setJobEmailsFound(Math.max(0, jobs));
        j.setApplicationsCreated(created);
        j.setApplicationsUpdated(updated);
        syncJobRepository.save(j);
    }

    private void setSource(Long appId, String source) {
        JobApplication a = load(appId);
        if (a != null) {
            a.setSource(source);
            applicationRepository.save(a);
        }
    }

    private void note(Long appId, String content, Instant at) {
        JobApplication a = load(appId);
        if (a == null) {
            return;
        }
        Note n = new Note();
        n.setApplication(a);
        n.setContent(content);
        n.setCreatedAt(at);
        n.setUpdatedAt(at);
        noteRepository.save(n);
    }

    private JobApplication load(Long id) {
        return id == null ? null : applicationRepository.findById(id).orElse(null);
    }
}
