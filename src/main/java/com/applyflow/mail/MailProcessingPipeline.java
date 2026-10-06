package com.applyflow.mail;

import com.applyflow.application.service.ApplicationService;
import com.applyflow.application.service.EmailApplicationLinker;
import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailClassification;
import com.applyflow.common.NotificationType;
import com.applyflow.dto.MiscDtos.AppSettings;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.EmailClassificationLog;
import com.applyflow.entity.EmailMatchSuggestion;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.intelligence.ClassificationResult;
import com.applyflow.intelligence.EmailIntelligenceService;
import com.applyflow.intelligence.EmailSummary;
import com.applyflow.intelligence.ExtractedJobDetails;
import com.applyflow.mail.matcher.MatchResult;
import com.applyflow.mail.matcher.ScoredMatch;
import com.applyflow.mail.parser.HtmlNormalizer;
import com.applyflow.mail.parser.ParsedEmail;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.persistence.MongoErrors;
import com.applyflow.repository.EmailAccountRepository;
import com.applyflow.repository.EmailClassificationLogRepository;
import com.applyflow.repository.EmailMatchSuggestionRepository;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.repository.JobApplicationRepository;
import com.applyflow.service.NotificationService;
import com.applyflow.service.SettingsService;
import com.applyflow.sse.ServerEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Processes one email end-to-end: classify → (stop if not job related) → extract → match → create/update
 * application → event → status engine → persist → notifications → SSE. Each email runs in its own transaction.
 */
@Component
public class MailProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(MailProcessingPipeline.class);

    /** Classifications that may create a new application when company + title are known. */
    private static final Set<EmailClassification> CREATING = EnumSet.of(
            EmailClassification.APPLICATION_CONFIRMATION, EmailClassification.APPLICATION_UPDATE,
            EmailClassification.ASSESSMENT, EmailClassification.INTERVIEW_INVITATION,
            EmailClassification.INTERVIEW_UPDATE, EmailClassification.OFFER, EmailClassification.REJECTION,
            EmailClassification.RECRUITER_CONTACT, EmailClassification.WITHDRAWAL);

    private static final Set<EmailClassification> SCHEDULED = EnumSet.of(
            EmailClassification.INTERVIEW_INVITATION, EmailClassification.INTERVIEW_UPDATE,
            EmailClassification.ASSESSMENT, EmailClassification.RECRUITER_CONTACT);

    private static final int MAX_ATTEMPTS = 3;

    private final EmailIntelligenceService intelligence;
    private final EmailMessageRepository emailRepository;
    private final EmailAccountRepository accountRepository;
    private final JobApplicationRepository applicationRepository;
    private final EmailMatchSuggestionRepository suggestionRepository;
    private final EmailClassificationLogRepository classificationLogRepository;
    private final ApplicationService applicationService;
    private final EmailApplicationLinker linker;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final HtmlNormalizer htmlNormalizer;
    private final DtoMapper mapper;
    private final ServerEventPublisher events;
    private final TransactionTemplate tx;

    public MailProcessingPipeline(EmailIntelligenceService intelligence, EmailMessageRepository emailRepository,
                                  EmailAccountRepository accountRepository,
                                  JobApplicationRepository applicationRepository,
                                  EmailMatchSuggestionRepository suggestionRepository,
                                  EmailClassificationLogRepository classificationLogRepository,
                                  ApplicationService applicationService, EmailApplicationLinker linker,
                                  NotificationService notificationService, SettingsService settingsService,
                                  HtmlNormalizer htmlNormalizer, DtoMapper mapper, ServerEventPublisher events,
                                  @Qualifier("requiresNewTransactionTemplate") TransactionTemplate tx) {
        this.intelligence = intelligence;
        this.emailRepository = emailRepository;
        this.accountRepository = accountRepository;
        this.applicationRepository = applicationRepository;
        this.suggestionRepository = suggestionRepository;
        this.classificationLogRepository = classificationLogRepository;
        this.applicationService = applicationService;
        this.linker = linker;
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.htmlNormalizer = htmlNormalizer;
        this.mapper = mapper;
        this.events = events;
        this.tx = tx;
    }

    /** Classifies only; used by the IMAP sync to avoid opening a transaction for non-job mail. */
    public ClassificationResult classify(ParsedEmail email) {
        return intelligence.classifyEmail(email);
    }

    public ProcessingOutcome process(ParsedEmail email) {
        return process(email, null);
    }

    /**
     * @param precomputed optional classification already computed for this email (avoids re-classifying)
     */
    public ProcessingOutcome process(ParsedEmail email, ClassificationResult precomputed) {
        for (int attempt = 1; ; attempt++) {
            try {
                // Own, short transaction per email: a failure here never rolls back other emails.
                ProcessingOutcome outcome = tx.execute(status -> doProcess(email, precomputed));
                return outcome == null ? ProcessingOutcome.failed() : outcome;
            } catch (RuntimeException e) {
                if (MongoErrors.isDuplicateKey(e)) {
                    if (attempt < MAX_ATTEMPTS && !alreadyStored(email)) {
                        continue; // another unique key raced (e.g. a company created concurrently): retry
                    }
                    return ProcessingOutcome.duplicate(); // unique (mailbox, message id): already stored
                }
                if (MongoErrors.isTransientTransactionError(e) && attempt < MAX_ATTEMPTS) {
                    log.debug("Transient transaction error for message {}; retrying", email.providerMessageId());
                    continue; // write conflict with a concurrent transaction: run the whole email again
                }
                if (e instanceof DataIntegrityViolationException dive) {
                    log.warn("Failed to store message {} ({})", email.providerMessageId(),
                            dive.getMostSpecificCause().getClass().getSimpleName());
                } else {
                    log.warn("Failed to process message {} ({})", email.providerMessageId(),
                            e.getClass().getSimpleName(), e);
                }
                return ProcessingOutcome.failed();
            }
        }
    }

    private ProcessingOutcome doProcess(ParsedEmail email, ClassificationResult precomputed) {
        String providerId = providerMessageId(email);
        if (email.accountId() != null
                && emailRepository.existsByEmailAccountIdAndProviderMessageId(email.accountId(), providerId)) {
            return ProcessingOutcome.duplicate();
        }
        ClassificationResult cr = precomputed != null ? precomputed : intelligence.classifyEmail(email);
        if (!cr.isJobRelated()) {
            return ProcessingOutcome.notJobRelated(); // never stored (privacy)
        }

        AppSettings settings = settingsService.current();
        EmailAccount account = email.accountId() == null ? null
                : accountRepository.findById(email.accountId()).orElse(null);
        ExtractedJobDetails details = intelligence.extractJobDetails(email);
        if (!SCHEDULED.contains(cr.classification()) && details.scheduledAt() != null) {
            details = new ExtractedJobDetails(details.companyName(), details.companyDomain(), details.jobTitle(),
                    details.source(), details.location(), details.jobUrl(), details.applicationRef(),
                    details.employmentType(), details.salaryMin(), details.salaryMax(), details.salaryCurrency(),
                    details.recruiterName(), details.recruiterEmail(), null, false);
        }
        ApplicationStatus detected = intelligence.detectStatus(cr).orElse(null);
        EmailSummary summary = intelligence.summarizeEmail(email, cr, details);
        MatchResult match = intelligence.matchApplication(email, details);
        log.debug("Match for message {}: {} app={} score={} ({})", email.providerMessageId(), match.decision(),
                match.applicationId(), match.score(), match.reason());
        if (cr.classification() == EmailClassification.APPLICATION_CONFIRMATION
                && match.decision() != MatchResult.Decision.NO_MATCH && !match.exact()
                && details.companyName() != null) {
            // A confirmation is the start of a (new) application: only merge it into an existing one on a hard
            // identifier (thread, reference, posting URL, identical title).
            match = MatchResult.noMatch();
        }

        EmailMessage e = new EmailMessage();
        e.setEmailAccount(account);
        e.setProviderMessageId(cut(providerId, 1000));
        e.setMessageIdHeader(cut(email.messageIdHeader(), 1000));
        e.setInReplyTo(cut(email.inReplyTo(), 1000));
        e.setReferencesHeader(email.references().isEmpty() ? null : String.join(" ", email.references()));
        e.setThreadId(cut(email.threadId(), 1000));
        e.setSenderEmail(cut(email.senderEmail().isBlank() ? "unknown@unknown" : email.senderEmail(), 320));
        e.setSenderName(cut(email.senderName(), 255));
        e.setRecipient(cut(email.recipient(), 1000));
        e.setSubject(cut(email.subject(), 2000));
        e.setReceivedAt(email.receivedAt());
        e.setBodyText(email.bodyText());
        e.setBodyHtml(email.bodyHtml());
        e.setSnippet(cut(htmlNormalizer.snippet(email.bodyText(), 200), 500));
        e.setJobRelated(true);
        e.setClassification(cr.classification());
        e.setClassificationConfidence(cr.confidence());
        e.setClassificationReason(cr.reason());
        e.setDetectedStatus(detected);
        e.setDetectedCompany(cut(details.companyName(), 255));
        e.setDetectedJobTitle(cut(details.jobTitle(), 500));
        e.setSummary(summary.summary());
        e.setActionRequired(summary.actionRequired());
        e.setActionText(cut(summary.actionText(), 500));
        e.setScheduledAt(details.scheduledAt());
        e.setDemo(email.demo());
        e.setRead(false);
        emailRepository.save(e); // inserted now: a duplicate fails fast on the unique index

        logClassification(e, cr, Actor.SYSTEM);

        JobApplication app = null;
        boolean created = false;
        switch (match.decision()) {
            case LINK -> app = applicationService.load(match.applicationId());
            case REVIEW -> {
                e.setNeedsReview(true);
                saveSuggestions(e, match.suggestions());
                ScoredMatch top = match.suggestions().get(0);
                notificationService.create(NotificationType.POSSIBLE_DUPLICATE,
                        "Possible match — " + top.companyName(),
                        "\"" + cut(e.getSubject(), 120) + "\" may belong to " + top.companyName() + " – "
                                + top.jobTitle() + ". Review it in the inbox.",
                        top.applicationId(), e.getId(), pastOrNull(e));
            }
            case NO_MATCH -> {
                if (shouldCreate(cr, details)) {
                    app = applicationService.createFromEmail(details.companyName(), details.companyDomain(),
                            details.jobTitle(), detected == null ? ApplicationStatus.APPLIED : detected,
                            email.receivedAt(), details.source(), account, email.demo());
                    created = true;
                } else {
                    e.setNeedsReview(true);
                }
            }
        }

        boolean updated = false;
        if (app != null) {
            linker.link(e, app, details, created, Actor.SYSTEM, false, settings);
            updated = !created;
            applicationRepository.save(app);
        }
        emailRepository.save(e);

        events.publish(ServerEventPublisher.EMAIL_RECEIVED, mapper.toInboxItem(e));
        if (app != null) {
            events.publish(created ? ServerEventPublisher.APPLICATION_CREATED : ServerEventPublisher.APPLICATION_UPDATED,
                    applicationService.summaries(List.of(app)).get(0));
        }
        return new ProcessingOutcome(ProcessingOutcome.Result.STORED, e.getId(), app == null ? null : app.getId(),
                created, updated);
    }

    private boolean alreadyStored(ParsedEmail email) {
        try {
            return email.accountId() == null || emailRepository.existsByEmailAccountIdAndProviderMessageId(
                    email.accountId(), providerMessageId(email));
        } catch (RuntimeException e) {
            return true;
        }
    }

    private boolean shouldCreate(ClassificationResult cr, ExtractedJobDetails d) {
        if (d.companyName() == null) {
            return false;
        }
        if (cr.classification() == EmailClassification.APPLICATION_CONFIRMATION) {
            return true;
        }
        return CREATING.contains(cr.classification()) && d.jobTitle() != null && cr.confidence() >= 0.6;
    }

    private void saveSuggestions(EmailMessage e, List<ScoredMatch> suggestions) {
        for (ScoredMatch s : suggestions) {
            EmailMatchSuggestion ms = new EmailMatchSuggestion();
            ms.setEmailId(e.getId());
            ms.setApplicationId(s.applicationId());
            ms.setScore(s.score());
            ms.setReason(cut(s.reason(), 1000));
            suggestionRepository.save(ms);
        }
    }

    private void logClassification(EmailMessage e, ClassificationResult cr, Actor actor) {
        EmailClassificationLog l = new EmailClassificationLog();
        l.setEmailId(e.getId());
        l.setClassification(cr.classification());
        l.setConfidence(cr.confidence());
        l.setReason(cr.reason());
        l.setSignals(cr.signals() == null ? null : String.join("\n", cr.signals()));
        l.setActor(actor);
        classificationLogRepository.save(l);
    }

    static String providerMessageId(ParsedEmail email) {
        if (email.providerMessageId() != null && !email.providerMessageId().isBlank()) {
            return email.providerMessageId();
        }
        if (email.messageIdHeader() != null && !email.messageIdHeader().isBlank()) {
            return email.messageIdHeader();
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String key = email.senderEmail() + "|" + email.subject() + "|" + email.receivedAt();
            return "gen:" + HexFormat.of().formatHex(md.digest(key.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception ex) {
            return "gen:" + Integer.toHexString((email.senderEmail() + email.subject()).hashCode());
        }
    }

    private static java.time.Instant pastOrNull(EmailMessage e) {
        return e.getReceivedAt() != null && e.getReceivedAt().isBefore(java.time.Instant.now()) ? e.getReceivedAt() : null;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
