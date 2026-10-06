package com.applyflow.service;

import com.applyflow.analytics.AnalyticsService;
import com.applyflow.analytics.ApplicationFacts;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailClassification;
import com.applyflow.common.ScheduledType;
import com.applyflow.dto.DashboardDtos.ActivityItem;
import com.applyflow.dto.DashboardDtos.AttentionItem;
import com.applyflow.dto.DashboardDtos.DashboardSummary;
import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.repository.ApplicationEventRepository;
import com.applyflow.repository.EmailAccountRepository;
import com.applyflow.repository.EmailMatchSuggestionRepository;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class DashboardService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    private final ApplicationFacts factsLoader;
    private final AnalyticsService analytics;
    private final ApplicationEventRepository eventRepository;
    private final EmailMessageRepository emailRepository;
    private final EmailMatchSuggestionRepository suggestionRepository;
    private final EmailAccountRepository accountRepository;
    private final SettingsService settingsService;
    private final DtoMapper mapper;
    private final ZoneId zone;
    private final Clock clock;

    public DashboardService(ApplicationFacts factsLoader, AnalyticsService analytics,
                            ApplicationEventRepository eventRepository, EmailMessageRepository emailRepository,
                            EmailMatchSuggestionRepository suggestionRepository,
                            EmailAccountRepository accountRepository, SettingsService settingsService,
                            DtoMapper mapper, ZoneId appZone, Clock clock) {
        this.factsLoader = factsLoader;
        this.analytics = analytics;
        this.eventRepository = eventRepository;
        this.emailRepository = emailRepository;
        this.suggestionRepository = suggestionRepository;
        this.accountRepository = accountRepository;
        this.settingsService = settingsService;
        this.mapper = mapper;
        this.zone = appZone;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        List<ApplicationFacts.Fact> facts = factsLoader.load();
        List<JobApplication> apps = facts.stream().map(ApplicationFacts.Fact::app).toList();
        Instant weekStart = analytics.weekStart(LocalDate.now(clock.withZone(zone))).atStartOfDay(zone).toInstant();
        long active = apps.stream().filter(a -> a.getStatus().isActive()).count();
        long interviews = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.INTERVIEW).count();
        long offers = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.OFFER).count();
        long rejected = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.REJECTED).count();
        long waiting = apps.stream().filter(a -> a.getStatus().isWaiting()).count();
        long appliedThisWeek = apps.stream()
                .filter(a -> a.getAppliedAt() != null && !a.getAppliedAt().isBefore(weekStart)).count();
        Instant lastSync = accountRepository.findByUserIdOrderByCreatedAtAsc(CurrentUser.id()).stream()
                .map(EmailAccount::getLastSyncAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new DashboardSummary(apps.size(), active, interviews, offers, rejected, waiting,
                analytics.distribution(apps), appliedThisWeek, analytics.responseRate(facts), lastSync);
    }

    @Transactional(readOnly = true)
    public List<ActivityItem> activity(int limit) {
        int l = Math.max(1, Math.min(200, limit));
        return eventRepository.findRecent(CurrentUser.id(), l).stream().map(mapper::toActivity).toList();
    }

    @Transactional(readOnly = true)
    public List<AttentionItem> attention() {
        Instant now = clock.instant();
        Long userId = CurrentUser.id();
        int followUpDays = settingsService.forUser(userId).followUpDays();
        List<AttentionItem> items = new ArrayList<>();
        Set<Long> emailsCovered = new HashSet<>();

        // Upcoming interviews / assessments / deadlines in the next 14 days.
        for (ApplicationEventEntity ev : eventRepository.findScheduledBetween(userId, now.minus(Duration.ofHours(2)),
                now.plus(Duration.ofDays(14)))) {
            JobApplication a = ev.getApplication();
            if (a.getStatus().isTerminal()) {
                continue;
            }
            ScheduledType st = ev.getScheduledType();
            String kind = st == ScheduledType.INTERVIEW || st == ScheduledType.RECRUITER_CALL ? "INTERVIEW" : "ASSESSMENT";
            String reason = switch (st == null ? ScheduledType.DEADLINE : st) {
                case INTERVIEW -> "Interview " + relative(ev.getScheduledAt(), now, true);
                case RECRUITER_CALL -> "Recruiter call " + relative(ev.getScheduledAt(), now, true);
                case ASSESSMENT -> "Assessment " + relative(ev.getScheduledAt(), now, true);
                case DEADLINE -> "Assessment due " + relative(ev.getScheduledAt(), now, false);
            };
            Long emailId = ev.getEmailId();
            if (emailId != null) {
                emailsCovered.add(emailId);
            }
            items.add(new AttentionItem("event-" + ev.getId(), kind, a.getId(), emailId, a.getCompany().getName(),
                    a.getJobTitle(), reason, ev.getEventDate(), ev.getScheduledAt(), "Open"));
        }

        // Unread emails that need an action.
        for (EmailMessage e : emailRepository.findUnreadActionRequired(userId)) {
            if (emailsCovered.contains(e.getId()) || e.isNeedsReview()) {
                continue;
            }
            String kind = switch (e.getClassification()) {
                case INTERVIEW_INVITATION, INTERVIEW_UPDATE -> "INTERVIEW";
                case ASSESSMENT -> "ASSESSMENT";
                case RECRUITER_CONTACT, FOLLOW_UP -> "RECRUITER_RESPONSE";
                case OFFER -> "OFFER";
                default -> "NEEDS_REVIEW";
            };
            String label = e.getClassification() == EmailClassification.OFFER ? "Review offer" : "Open";
            emailsCovered.add(e.getId());
            items.add(new AttentionItem("email-" + e.getId(), kind, appId(e), e.getId(), company(e),
                    jobTitle(e), reasonFor(e), e.getReceivedAt(), e.getScheduledAt(), label));
        }

        // Emails needing review (low confidence or possible application match).
        List<EmailMessage> review = emailRepository.findNeedsReview(userId);
        Set<Long> withSuggestions = review.isEmpty() ? Set.of()
                : new HashSet<>(suggestionRepository.findEmailIdsWithSuggestions(
                review.stream().map(EmailMessage::getId).toList()));
        for (EmailMessage e : review) {
            boolean possible = withSuggestions.contains(e.getId());
            String reason = possible ? "Possible match with an existing application — confirm or create new"
                    : e.getApplication() != null ? "Low-confidence status change — please confirm"
                    : "Job email not linked to an application — review";
            items.add(new AttentionItem("email-" + e.getId() + "-review", possible ? "POSSIBLE_MATCH" : "NEEDS_REVIEW",
                    appId(e), e.getId(), company(e), jobTitle(e), reason, e.getReceivedAt(), null,
                    possible ? "Review match" : "Review"));
        }

        // Follow-ups: active APPLIED/UNDER_REVIEW apps with no activity for followUpDays+.
        Instant cutoff = now.minus(Duration.ofDays(followUpDays));
        for (ApplicationFacts.Fact f : factsLoader.load()) {
            JobApplication a = f.app();
            Instant last = a.getLastActivityAt() != null ? a.getLastActivityAt() : a.getAppliedAt();
            if (a.getStatus().isWaiting() && last != null && last.isBefore(cutoff)) {
                long days = Duration.between(last, now).toDays();
                items.add(new AttentionItem("app-" + a.getId() + "-followup", "FOLLOW_UP", a.getId(), null,
                        a.getCompany().getName(), a.getJobTitle(),
                        "No response in " + days + " days — follow-up recommended", last,
                        last.plus(Duration.ofDays(followUpDays)), "Follow up"));
            }
        }

        // Items with a due date first (soonest first), then the rest newest first.
        items.sort(Comparator.<AttentionItem, Integer>comparing(i -> i.dueAt() != null
                        && !"FOLLOW_UP".equals(i.kind()) ? 0 : 1)
                .thenComparing(i -> i.dueAt() != null && !"FOLLOW_UP".equals(i.kind()) ? i.dueAt() : Instant.MAX)
                .thenComparing(AttentionItem::timestamp, Comparator.reverseOrder()));
        return items;
    }

    private String relative(Instant at, Instant now, boolean withTime) {
        ZonedDateTime z = at.atZone(zone);
        LocalDate day = z.toLocalDate();
        LocalDate today = now.atZone(zone).toLocalDate();
        boolean endOfDay = z.getHour() == 23 && z.getMinute() == 59;
        String time = withTime && !endOfDay ? " at " + z.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) : "";
        if (day.equals(today)) {
            return "today" + time;
        }
        if (day.equals(today.plusDays(1))) {
            return "tomorrow" + time;
        }
        return "on " + (withTime && !endOfDay ? z.format(WHEN) : z.format(DAY));
    }

    private String reasonFor(EmailMessage e) {
        String action = e.getActionText() == null ? "" : " — " + e.getActionText();
        return switch (e.getClassification()) {
            case INTERVIEW_INVITATION -> "Interview invitation" + action;
            case INTERVIEW_UPDATE -> "Interview update" + action;
            case ASSESSMENT -> "Assessment received" + action;
            case RECRUITER_CONTACT -> "Recruiter reached out" + action;
            case OFFER -> "Offer received" + action;
            case FOLLOW_UP -> "Follow-up received" + action;
            default -> e.getClassification().label() + action;
        };
    }

    private static Long appId(EmailMessage e) {
        return e.getApplication() == null ? null : e.getApplication().getId();
    }

    private static String company(EmailMessage e) {
        if (e.getApplication() != null) {
            return e.getApplication().getCompany().getName();
        }
        if (e.getDetectedCompany() != null) {
            return e.getDetectedCompany();
        }
        return e.getSenderName() != null ? e.getSenderName() : e.getSenderEmail();
    }

    private static String jobTitle(EmailMessage e) {
        return e.getApplication() != null ? e.getApplication().getJobTitle() : e.getDetectedJobTitle();
    }
}
