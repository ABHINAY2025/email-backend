package com.applyflow.service;

import com.applyflow.application.service.ApplicationService;
import com.applyflow.application.service.EmailApplicationLinker;
import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailClassification;
import com.applyflow.dto.ApplicationDtos.ApplicationDetail;
import com.applyflow.dto.CommonDtos.PageResponse;
import com.applyflow.dto.InboxDtos.EmailDetail;
import com.applyflow.dto.InboxDtos.InboxCounts;
import com.applyflow.dto.InboxDtos.InboxItem;
import com.applyflow.dto.MiscDtos.AppSettings;
import com.applyflow.entity.EmailClassificationLog;
import com.applyflow.entity.EmailMatchSuggestion;
import com.applyflow.mail.matcher.MatchResult;
import com.applyflow.mail.matcher.ScoredMatch;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.intelligence.ClassificationResult;
import com.applyflow.intelligence.EmailIntelligenceService;
import com.applyflow.intelligence.EmailSummary;
import com.applyflow.intelligence.ExtractedJobDetails;
import com.applyflow.mail.parser.ParsedEmail;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.persistence.CascadeDeleter;
import com.applyflow.repository.EmailClassificationLogRepository;
import com.applyflow.repository.EmailMatchSuggestionRepository;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.repository.JobApplicationRepository;
import com.applyflow.repository.NotificationRepository;
import com.applyflow.exception.BadRequestException;
import com.applyflow.exception.NotFoundException;
import com.applyflow.sse.ServerEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
public class InboxService {

    static final Set<EmailClassification> APPLICATIONS_TAB = EnumSet.of(EmailClassification.APPLICATION_CONFIRMATION,
            EmailClassification.APPLICATION_UPDATE, EmailClassification.FOLLOW_UP,
            EmailClassification.OTHER_JOB_RELATED, EmailClassification.WITHDRAWAL);

    private final EmailMessageRepository emailRepository;
    private final EmailMatchSuggestionRepository suggestionRepository;
    private final EmailClassificationLogRepository logRepository;
    private final NotificationRepository notificationRepository;
    private final JobApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final EmailApplicationLinker linker;
    private final EmailIntelligenceService intelligence;
    private final SettingsService settingsService;
    private final DtoMapper mapper;
    private final ServerEventPublisher events;
    private final CascadeDeleter cascade;

    public InboxService(EmailMessageRepository emailRepository, EmailMatchSuggestionRepository suggestionRepository,
                        EmailClassificationLogRepository logRepository, NotificationRepository notificationRepository,
                        JobApplicationRepository applicationRepository, ApplicationService applicationService,
                        EmailApplicationLinker linker, EmailIntelligenceService intelligence,
                        SettingsService settingsService, DtoMapper mapper, ServerEventPublisher events,
                        CascadeDeleter cascade) {
        this.cascade = cascade;
        this.emailRepository = emailRepository;
        this.suggestionRepository = suggestionRepository;
        this.logRepository = logRepository;
        this.notificationRepository = notificationRepository;
        this.applicationRepository = applicationRepository;
        this.applicationService = applicationService;
        this.linker = linker;
        this.intelligence = intelligence;
        this.settingsService = settingsService;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<InboxItem> list(String tab, String q, Long accountId, boolean unreadOnly, int page, int size) {
        String t = tab == null || tab.isBlank() ? "all" : tab.trim().toLowerCase(Locale.ROOT);
        List<Criteria> p = new ArrayList<>();
        p.add(where("jobRelated").is(true));
        switch (t) {
            case "attention" -> p.add(where("actionRequired").is(true));
            case "applications" -> p.add(where("classification").in(APPLICATIONS_TAB));
            case "recruiters" -> p.add(where("classification").is(EmailClassification.RECRUITER_CONTACT));
            case "interviews" -> p.add(where("classification").in(EmailClassification.INTERVIEW_INVITATION,
                    EmailClassification.INTERVIEW_UPDATE));
            case "assessments" -> p.add(where("classification").is(EmailClassification.ASSESSMENT));
            case "offers" -> p.add(where("classification").is(EmailClassification.OFFER));
            case "rejected" -> p.add(where("classification").is(EmailClassification.REJECTION));
            case "review" -> p.add(where("needsReview").is(true));
            case "all" -> {
            }
            default -> throw new BadRequestException("Unknown inbox tab '" + tab + "'.");
        }
        if (accountId != null) {
            p.add(where("emailAccountId").is(accountId));
        }
        if (unreadOnly) {
            p.add(where("read").is(false));
        }
        if (q != null && !q.isBlank()) {
            String regex = Pattern.quote(q.trim()); // case-insensitive "contains"; user input is never a regex
            p.add(new Criteria().orOperator(
                    where("subject").regex(regex, "i"),
                    where("senderEmail").regex(regex, "i"),
                    where("senderName").regex(regex, "i"),
                    where("detectedCompany").regex(regex, "i"),
                    where("summary").regex(regex, "i"),
                    where("snippet").regex(regex, "i")));
        }
        int s = Math.max(1, Math.min(200, size));
        Page<EmailMessage> result = emailRepository.findPage(new Criteria().andOperator(p),
                PageRequest.of(Math.max(0, page), s, Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("_id"))));
        return PageResponse.of(result, mapper::toInboxItem);
    }

    @Transactional(readOnly = true)
    public InboxCounts counts() {
        Map<EmailClassification, Long> byClass = emailRepository.countByClassification();
        long applications = APPLICATIONS_TAB.stream().mapToLong(c -> byClass.getOrDefault(c, 0L)).sum();
        return new InboxCounts(
                emailRepository.countByJobRelatedTrue(),
                emailRepository.countByJobRelatedTrueAndActionRequiredTrue(),
                applications,
                byClass.getOrDefault(EmailClassification.RECRUITER_CONTACT, 0L),
                byClass.getOrDefault(EmailClassification.INTERVIEW_INVITATION, 0L)
                        + byClass.getOrDefault(EmailClassification.INTERVIEW_UPDATE, 0L),
                byClass.getOrDefault(EmailClassification.ASSESSMENT, 0L),
                byClass.getOrDefault(EmailClassification.OFFER, 0L),
                byClass.getOrDefault(EmailClassification.REJECTION, 0L),
                emailRepository.countByJobRelatedTrueAndNeedsReviewTrue(),
                emailRepository.countByJobRelatedTrueAndReadFalse());
    }

    @Transactional(readOnly = true)
    public EmailDetail get(Long id) {
        EmailMessage e = load(id);
        return mapper.toEmailDetail(e, suggestionRepository.findForEmail(id));
    }

    @Transactional
    public InboxItem markRead(Long id, boolean read) {
        EmailMessage e = load(id);
        e.setRead(read);
        emailRepository.save(e);
        return mapper.toInboxItem(e);
    }

    /** Links the email to an application chosen by the user and applies its status impact. */
    @Transactional
    public EmailDetail merge(Long id, Long applicationId) {
        EmailMessage e = load(id);
        JobApplication app = applicationService.load(applicationId);
        e.setNeedsReview(false);
        ExtractedJobDetails details = intelligence.extractJobDetails(toParsed(e));
        linker.link(e, app, details, false, Actor.USER, true, settingsService.current());
        e.setNeedsReview(false);
        suggestionRepository.deleteForEmail(id);
        notificationRepository.deleteDuplicateNoticesForEmail(id);
        emailRepository.save(e); // before counting, so this email no longer counts as needing review
        if (emailRepository.countByApplicationIdAndNeedsReviewTrue(app.getId()) == 0) {
            app.setNeedsReview(false);
        }
        applicationRepository.save(app);
        events.publish(ServerEventPublisher.APPLICATION_UPDATED, applicationService.summaries(List.of(app)).get(0));
        return mapper.toEmailDetail(e, List.of());
    }

    /**
     * Re-matches review emails that had no candidate application when they were processed — typically an update
     * that arrived in the same minute as, but was processed before, the confirmation that created the application.
     * Returns the number of emails linked automatically.
     */
    @Transactional
    public int reconcileOrphans() {
        int linked = 0;
        AppSettings settings = settingsService.current();
        for (EmailMessage e : emailRepository.findNeedsReview()) {
            if (e.getApplication() != null || !suggestionRepository.findForEmail(e.getId()).isEmpty()) {
                continue;
            }
            ParsedEmail parsed = toParsed(e);
            ExtractedJobDetails details = intelligence.extractJobDetails(parsed);
            MatchResult match = intelligence.matchApplication(parsed, details);
            if (match.decision() == MatchResult.Decision.LINK) {
                JobApplication app = applicationService.load(match.applicationId());
                linker.link(e, app, details, false, Actor.SYSTEM, false, settings);
                e.setNeedsReview(false);
                applicationRepository.save(app);
                emailRepository.save(e);
                events.publish(ServerEventPublisher.APPLICATION_UPDATED,
                        applicationService.summaries(List.of(app)).get(0));
                linked++;
            } else if (match.decision() == MatchResult.Decision.REVIEW) {
                for (ScoredMatch s : match.suggestions()) {
                    EmailMatchSuggestion ms = new EmailMatchSuggestion();
                    ms.setEmailId(e.getId());
                    ms.setApplicationId(s.applicationId());
                    ms.setScore(s.score());
                    ms.setReason(s.reason());
                    suggestionRepository.save(ms);
                }
            }
        }
        return linked;
    }

    /** Creates a new application from the email's detected company/title. */
    @Transactional
    public ApplicationDetail createApplication(Long id) {
        EmailMessage e = load(id);
        ParsedEmail parsed = toParsed(e);
        ExtractedJobDetails details = intelligence.extractJobDetails(parsed);
        String company = e.getDetectedCompany() != null ? e.getDetectedCompany()
                : details.companyName() != null ? details.companyName() : "Unknown company";
        String title = e.getDetectedJobTitle() != null ? e.getDetectedJobTitle() : details.jobTitle();
        ApplicationStatus status = e.getDetectedStatus() == null ? ApplicationStatus.APPLIED : e.getDetectedStatus();
        JobApplication app = applicationService.createFromEmail(company, details.companyDomain(), title, status,
                e.getReceivedAt(), details.source(), e.getEmailAccount(), e.isDemo());
        e.setNeedsReview(false);
        linker.link(e, app, details, true, Actor.USER, true, settingsService.current());
        e.setNeedsReview(false);
        suggestionRepository.deleteForEmail(id);
        notificationRepository.deleteDuplicateNoticesForEmail(id);
        applicationRepository.save(app);
        emailRepository.save(e);
        ApplicationDetail detail = applicationService.detail(app);
        events.publish(ServerEventPublisher.APPLICATION_CREATED, applicationService.summaries(List.of(app)).get(0));
        return detail;
    }

    /** Marks as NOT_JOB_RELATED, purges content and unlinks; the row is kept only to prevent re-import. */
    @Transactional
    public void ignore(Long id) {
        EmailMessage e = load(id);
        purgeAsNotJob(e);
        emailRepository.save(e);
        logManual(e, EmailClassification.NOT_JOB_RELATED);
    }

    @Transactional
    public EmailDetail reclassify(Long id, EmailClassification classification) {
        EmailMessage e = load(id);
        if (classification == EmailClassification.NOT_JOB_RELATED) {
            purgeAsNotJob(e);
            emailRepository.save(e);
            logManual(e, classification);
            return mapper.toEmailDetail(e, List.of());
        }
        e.setClassification(classification);
        e.setClassificationConfidence(1.0);
        e.setClassificationReason("Manually classified by you");
        ClassificationResult cr = new ClassificationResult(classification, 1.0, "Manually classified by you",
                List.of("manual"), classification == EmailClassification.APPLICATION_UPDATE);
        ApplicationStatus detected = intelligence.detectStatus(cr).orElse(null);
        e.setDetectedStatus(detected);
        ParsedEmail parsed = toParsed(e);
        ExtractedJobDetails details = intelligence.extractJobDetails(parsed);
        EmailSummary summary = intelligence.summarizeEmail(parsed, cr, details);
        e.setSummary(summary.summary());
        e.setActionRequired(summary.actionRequired());
        e.setActionText(summary.actionText());
        logManual(e, classification);

        JobApplication app = e.getApplication();
        if (app != null && detected != null) {
            ApplicationStatus before = app.getStatus();
            e.setPreviousStatus(before);
            if (before == detected) {
                e.setStatusApplied(true);
            } else if (com.applyflow.application.status.StatusTransitionPolicy.isAllowed(before, detected,
                    Actor.SYSTEM)) {
                // Re-run linking side effects as a user-confirmed change.
                var change = applicationService.changeStatusFromEmail(app, detected, e);
                e.setStatusApplied(change);
            } else {
                e.setStatusApplied(false);
            }
        } else {
            e.setStatusApplied(false);
            e.setPreviousStatus(app == null ? null : app.getStatus());
        }
        emailRepository.save(e);
        return mapper.toEmailDetail(e, suggestionRepository.findForEmail(id));
    }

    @Transactional
    public void delete(Long id) {
        EmailMessage e = load(id);
        cascade.deleteEmail(e.getId());
    }

    private void purgeAsNotJob(EmailMessage e) {
        e.setClassification(EmailClassification.NOT_JOB_RELATED);
        e.setJobRelated(false);
        e.setBodyText(null);
        e.setBodyHtml(null);
        e.setSnippet(null);
        e.setSummary(null);
        e.setActionRequired(false);
        e.setActionText(null);
        e.setNeedsReview(false);
        e.setDetectedStatus(null);
        e.setStatusApplied(false);
        e.setApplication(null);
        e.setClassificationReason("Marked as not job related by you");
        suggestionRepository.deleteForEmail(e.getId());
        notificationRepository.deleteDuplicateNoticesForEmail(e.getId());
    }

    private void logManual(EmailMessage e, EmailClassification c) {
        EmailClassificationLog l = new EmailClassificationLog();
        l.setEmailId(e.getId());
        l.setClassification(c);
        l.setConfidence(1.0);
        l.setReason("Manual classification");
        l.setActor(Actor.USER);
        logRepository.save(l);
    }

    private EmailMessage load(Long id) {
        return emailRepository.findById(id).filter(EmailMessage::isJobRelated)
                .orElseThrow(() -> NotFoundException.of("Email", id));
    }

    static ParsedEmail toParsed(EmailMessage e) {
        List<String> refs = e.getReferencesHeader() == null ? List.of()
                : List.of(e.getReferencesHeader().split("\\s+"));
        return new ParsedEmail(e.getEmailAccountId(),
                e.getProviderMessageId(), e.getMessageIdHeader(), e.getInReplyTo(), refs, null, e.getSenderEmail(),
                e.getSenderName(), e.getRecipient(), e.getSubject(), e.getReceivedAt(), e.getBodyText(),
                e.getBodyHtml(), false, null, e.isDemo());
    }
}
