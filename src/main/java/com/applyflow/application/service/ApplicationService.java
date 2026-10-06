package com.applyflow.application.service;

import com.applyflow.application.status.StatusEngine;
import com.applyflow.application.timeline.TimelineService;
import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EventType;
import com.applyflow.dto.ApplicationDtos.ApplicationDetail;
import com.applyflow.dto.ApplicationDtos.ApplicationFacets;
import com.applyflow.dto.ApplicationDtos.ApplicationSummary;
import com.applyflow.dto.ApplicationDtos.CreateApplicationRequest;
import com.applyflow.dto.ApplicationDtos.NoteDto;
import com.applyflow.dto.ApplicationDtos.StatusHistoryEntry;
import com.applyflow.dto.CommonDtos.IdEmail;
import com.applyflow.dto.CommonDtos.IdName;
import com.applyflow.dto.CommonDtos.PageResponse;
import com.applyflow.dto.InboxDtos.EmailDetail;
import com.applyflow.entity.Company;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.Note;
import com.applyflow.exception.BadRequestException;
import com.applyflow.exception.FieldValidationException;
import com.applyflow.exception.NotFoundException;
import com.applyflow.mail.classifier.TitleExtractor;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.repository.EmailAccountRepository;
import com.applyflow.repository.EmailMatchSuggestionRepository;
import com.applyflow.repository.EmailMessageRepository;
import com.applyflow.repository.JobApplicationRepository;
import com.applyflow.repository.NoteRepository;
import com.applyflow.repository.NotificationRepository;
import com.applyflow.repository.StatusHistoryRepository;
import com.applyflow.repository.ApplicationEventRepository;
import com.applyflow.sse.ServerEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.applyflow.persistence.CascadeDeleter;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

@Service
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final EmailMessageRepository emailRepository;
    private final NoteRepository noteRepository;
    private final StatusHistoryRepository historyRepository;
    private final ApplicationEventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final EmailMatchSuggestionRepository suggestionRepository;
    private final EmailAccountRepository accountRepository;
    private final CompanyService companyService;
    private final StatusEngine statusEngine;
    private final TimelineService timelineService;
    private final DtoMapper mapper;
    private final ServerEventPublisher events;
    private final ZoneId zone;
    private final Clock clock;
    private final MongoOperations mongo;
    private final CascadeDeleter cascade;

    public ApplicationService(JobApplicationRepository applicationRepository, EmailMessageRepository emailRepository,
                              NoteRepository noteRepository, StatusHistoryRepository historyRepository,
                              ApplicationEventRepository eventRepository,
                              NotificationRepository notificationRepository,
                              EmailMatchSuggestionRepository suggestionRepository,
                              EmailAccountRepository accountRepository, CompanyService companyService,
                              StatusEngine statusEngine, TimelineService timelineService, DtoMapper mapper,
                              ServerEventPublisher events, ZoneId appZone, Clock clock,
                              MongoOperations mongo, CascadeDeleter cascade) {
        this.mongo = mongo;
        this.cascade = cascade;
        this.applicationRepository = applicationRepository;
        this.emailRepository = emailRepository;
        this.noteRepository = noteRepository;
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.suggestionRepository = suggestionRepository;
        this.accountRepository = accountRepository;
        this.companyService = companyService;
        this.statusEngine = statusEngine;
        this.timelineService = timelineService;
        this.mapper = mapper;
        this.events = events;
        this.zone = appZone;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<ApplicationSummary> search(ApplicationQuery f) {
        int size = Math.max(1, Math.min(200, f.size()));
        int page = Math.max(0, f.page());
        Sort.Direction dir = "asc".equalsIgnoreCase(f.dir()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = switch (f.sort() == null ? "" : f.sort()) {
            case "appliedAt" -> "appliedAt";
            case "company" -> "company.name";
            case "jobTitle" -> "jobTitle";
            case "status" -> "status";
            default -> "lastActivityAt";
        };
        boolean byCompany = "company.name".equals(property);
        Sort sort = byCompany ? Sort.by(new Sort.Order(dir, "_id"))
                : Sort.by(new Sort.Order(dir, property), new Sort.Order(dir, "_id"));
        Page<JobApplication> result = applicationRepository.findPage(ApplicationFilters.build(f, zone, mongo),
                PageRequest.of(page, size, sort), byCompany);
        return PageResponse.of(result, summaries(result.getContent()));
    }

    /** Maps applications to summaries with a single grouped email-count query. */
    @Transactional(readOnly = true)
    public List<ApplicationSummary> summaries(List<JobApplication> apps) {
        Map<Long, Long> counts = emailCounts(apps.stream().map(JobApplication::getId).toList());
        return apps.stream().map(a -> mapper.toSummary(a, counts.getOrDefault(a.getId(), 0L))).toList();
    }

    public Map<Long, Long> emailCounts(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return emailRepository.countByApplicationIds(ids);
    }

    @Transactional(readOnly = true)
    public ApplicationDetail get(Long id) {
        return detail(load(id));
    }

    @Transactional(readOnly = true)
    public ApplicationSummary summary(Long id) {
        JobApplication a = load(id);
        return mapper.toSummary(a, emailCounts(List.of(id)).getOrDefault(id, 0L));
    }

    public ApplicationDetail detail(JobApplication a) {
        long count = emailCounts(List.of(a.getId())).getOrDefault(a.getId(), 0L);
        return mapper.toDetail(a, count, noteRepository.findForApplication(a.getId()),
                historyRepository.findForApplication(a.getId()));
    }

    @Transactional(readOnly = true)
    public List<EmailDetail> emails(Long id) {
        load(id);
        return emailRepository.findByApplicationIdOrdered(id).stream()
                .map(e -> mapper.toEmailDetail(e, e.isNeedsReview() ? suggestionRepository.findForEmail(e.getId())
                        : List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryEntry> history(Long id) {
        load(id);
        return historyRepository.findForApplication(id).stream().map(mapper::toHistory).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationFacets facets() {
        List<JobApplication> apps = applicationRepository.findAllWithCompany();
        Map<Long, String> companies = new LinkedHashMap<>();
        TreeSet<String> locations = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        TreeSet<String> sources = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (JobApplication a : apps) {
            companies.put(a.getCompany().getId(), a.getCompany().getName());
            if (a.getLocation() != null && !a.getLocation().isBlank()) {
                locations.add(a.getLocation());
            }
            if (a.getSource() != null && !a.getSource().isBlank()) {
                sources.add(a.getSource());
            }
        }
        List<IdName> companyList = companies.entrySet().stream()
                .map(e -> new IdName(e.getKey(), e.getValue()))
                .sorted((x, y) -> x.name().compareToIgnoreCase(y.name())).toList();
        List<IdEmail> accounts = accountRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(a -> new IdEmail(a.getId(), a.getEmail())).toList();
        return new ApplicationFacets(companyList, List.copyOf(locations), List.copyOf(sources), accounts);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public ApplicationDetail create(CreateApplicationRequest req) {
        Company company = companyService.findOrCreate(req.companyName(), null, false);
        Instant appliedAt = req.appliedAt() == null ? clock.instant()
                : req.appliedAt().atStartOfDay(zone).toInstant().plusSeconds(12 * 3600);
        if (appliedAt.isAfter(clock.instant())) {
            appliedAt = clock.instant();
        }
        JobApplication a = new JobApplication();
        a.setCompany(company);
        a.setJobTitle(req.jobTitle().trim());
        a.setNormalizedTitle(TitleExtractor.normalize(req.jobTitle()));
        a.setJobUrl(blankToNull(req.jobUrl()));
        a.setLocation(blankToNull(req.location()));
        a.setSource(req.source() == null || req.source().isBlank() ? "Manual" : req.source().trim());
        a.setStatus(req.status() == null ? ApplicationStatus.APPLIED : req.status());
        a.setEmploymentType(blankToNull(req.employmentType()));
        a.setAppliedAt(appliedAt);
        a.setLastActivityAt(clock.instant());
        a.setCurrentStage(StatusEngine.stageFor(a.getStatus()));
        applicationRepository.save(a);

        statusEngine.recordInitial(a, Actor.USER, "Added manually", null, null, appliedAt);
        timelineService.add(a, null, EventType.APPLICATION_SUBMITTED, "Application added",
                "You added this application manually.", appliedAt, null, a.getStatus(), null, Actor.USER, null, null);
        if (req.notes() != null && !req.notes().isBlank()) {
            Note n = new Note();
            n.setApplication(a);
            n.setContent(req.notes().trim());
            noteRepository.save(n);
        }
        ApplicationDetail detail = detail(a);
        events.publish(ServerEventPublisher.APPLICATION_CREATED, mapper.toSummary(a, 0));
        return detail;
    }

    @Transactional
    public ApplicationDetail update(Long id, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BadRequestException("Request body must be a JSON object.");
        }
        JobApplication a = load(id);
        Map<String, String> errors = new LinkedHashMap<>();

        if (body.has("companyName")) {
            String v = text(body, "companyName");
            if (v == null || v.isBlank() || v.length() > 255) {
                errors.put("companyName", "must not be blank");
            } else if (!v.trim().equalsIgnoreCase(a.getCompany().getName())) {
                a.setCompany(companyService.findOrCreate(v, null, false));
            }
        }
        if (body.has("jobTitle")) {
            String v = text(body, "jobTitle");
            if (v == null || v.isBlank() || v.length() > 500) {
                errors.put("jobTitle", "must not be blank");
            } else {
                a.setJobTitle(v.trim());
                a.setNormalizedTitle(TitleExtractor.normalize(v));
            }
        }
        if (body.has("jobUrl")) {
            a.setJobUrl(limited(errors, "jobUrl", text(body, "jobUrl"), 2000));
        }
        if (body.has("location")) {
            a.setLocation(limited(errors, "location", text(body, "location"), 255));
        }
        if (body.has("source")) {
            a.setSource(limited(errors, "source", text(body, "source"), 100));
        }
        if (body.has("employmentType")) {
            a.setEmploymentType(limited(errors, "employmentType", text(body, "employmentType"), 50));
        }
        if (body.has("salaryCurrency")) {
            a.setSalaryCurrency(limited(errors, "salaryCurrency", text(body, "salaryCurrency"), 10));
        }
        if (body.has("recruiterName")) {
            a.setRecruiterName(limited(errors, "recruiterName", text(body, "recruiterName"), 255));
        }
        if (body.has("recruiterEmail")) {
            String v = limited(errors, "recruiterEmail", text(body, "recruiterEmail"), 320);
            if (v != null && !v.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                errors.put("recruiterEmail", "must be a valid email address");
            } else {
                a.setRecruiterEmail(v);
            }
        }
        if (body.has("currentStage")) {
            a.setCurrentStage(limited(errors, "currentStage", text(body, "currentStage"), 255));
        }
        if (body.has("salaryMin")) {
            a.setSalaryMin(decimal(errors, body, "salaryMin"));
        }
        if (body.has("salaryMax")) {
            a.setSalaryMax(decimal(errors, body, "salaryMax"));
        }
        if (body.has("appliedAt")) {
            JsonNode n = body.get("appliedAt");
            if (n.isNull()) {
                a.setAppliedAt(null);
            } else {
                try {
                    LocalDate d = LocalDate.parse(n.asText());
                    a.setAppliedAt(d.atStartOfDay(zone).toInstant().plusSeconds(12 * 3600));
                } catch (DateTimeParseException e) {
                    errors.put("appliedAt", "must be a date in format YYYY-MM-DD");
                }
            }
        }
        if (body.has("archived")) {
            JsonNode n = body.get("archived");
            if (!n.isBoolean()) {
                errors.put("archived", "must be a boolean");
            } else {
                a.setArchived(n.asBoolean());
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        if (a.getSalaryMin() != null && a.getSalaryMax() != null && a.getSalaryMin().compareTo(a.getSalaryMax()) > 0) {
            throw FieldValidationException.of("salaryMax", "must be greater than or equal to salaryMin");
        }
        applicationRepository.save(a);
        ApplicationDetail detail = detail(a);
        events.publish(ServerEventPublisher.APPLICATION_UPDATED, mapper.toSummary(a, detail.emailCount()));
        return detail;
    }

    @Transactional
    public void delete(Long id) {
        JobApplication a = load(id);
        emailRepository.deleteByApplicationId(id);
        cascade.deleteApplication(a.getId());
    }

    @Transactional
    public ApplicationDetail changeStatus(Long id, ApplicationStatus status, String reason) {
        JobApplication a = load(id);
        statusEngine.change(a, status, Actor.USER, blankToNull(reason), null, null, clock.instant(), true);
        a.setNeedsReview(false);
        applicationRepository.save(a);
        ApplicationDetail detail = detail(a);
        events.publish(ServerEventPublisher.APPLICATION_UPDATED, mapper.toSummary(a, detail.emailCount()));
        return detail;
    }

    @Transactional
    public ApplicationDetail merge(Long targetId, Long sourceId) {
        if (Objects.equals(targetId, sourceId)) {
            throw new BadRequestException("An application cannot be merged into itself.");
        }
        JobApplication target = load(targetId);
        JobApplication source = load(sourceId);
        String sourceLabel = source.displayId() + " (" + source.getCompany().getName() + " – " + source.getJobTitle() + ")";

        emailRepository.reassignApplication(source.getId(), target.getId());
        eventRepository.reassignApplication(source.getId(), target.getId());
        noteRepository.reassignApplication(source.getId(), target.getId());
        historyRepository.reassignApplication(source.getId(), target.getId());
        notificationRepository.reassignApplication(source.getId(), target.getId());
        suggestionRepository.deleteForApplication(source.getId());

        fillIfNull(target, source);
        if (source.getAppliedAt() != null && (target.getAppliedAt() == null
                || source.getAppliedAt().isBefore(target.getAppliedAt()))) {
            target.setAppliedAt(source.getAppliedAt());
        }
        target.touchActivity(source.getLastActivityAt());
        cascade.deleteApplication(source.getId());

        timelineService.add(target, null, EventType.APPLICATION_UPDATE, "Merged " + sourceLabel,
                "Emails, events and notes were moved into this application.", clock.instant(), null, null, null,
                Actor.USER, null, null);
        target.touchActivity(clock.instant());
        applicationRepository.save(target);
        ApplicationDetail detail = detail(target);
        events.publish(ServerEventPublisher.APPLICATION_UPDATED, mapper.toSummary(target, detail.emailCount()));
        return detail;
    }

    // ------------------------------------------------------------------ notes

    @Transactional
    public NoteDto addNote(Long appId, String content) {
        JobApplication a = load(appId);
        Note n = new Note();
        n.setApplication(a);
        n.setContent(content.trim());
        noteRepository.save(n);
        timelineService.add(a, null, EventType.NOTE_ADDED, "Note added",
                content.length() > 200 ? content.substring(0, 199) + "…" : content.trim(), clock.instant(), null,
                null, null, Actor.USER, null, null);
        a.touchActivity(clock.instant());
        applicationRepository.save(a);
        return mapper.toNote(n);
    }

    @Transactional
    public NoteDto updateNote(Long appId, Long noteId, String content) {
        Note n = noteRepository.findByIdAndApplicationId(noteId, appId)
                .orElseThrow(() -> NotFoundException.of("Note", noteId));
        n.setContent(content.trim());
        n.setUpdatedAt(clock.instant());
        noteRepository.save(n);
        return mapper.toNote(n);
    }

    @Transactional
    public void deleteNote(Long appId, Long noteId) {
        Note n = noteRepository.findByIdAndApplicationId(noteId, appId)
                .orElseThrow(() -> NotFoundException.of("Note", noteId));
        noteRepository.delete(n);
    }

    // ------------------------------------------------------------------ used by the mail pipeline / inbox

    /** Creates an application from email-derived facts (history/events are recorded by the caller). */
    @Transactional
    public JobApplication createFromEmail(String companyName, String companyDomain, String jobTitle,
                                          ApplicationStatus status, Instant appliedAt, String source,
                                          EmailAccount account, boolean demo) {
        Company company = companyService.findOrCreate(companyName, companyDomain, demo);
        String title = jobTitle == null || jobTitle.isBlank() ? "Unknown position" : jobTitle.trim();
        JobApplication a = new JobApplication();
        a.setCompany(company);
        a.setEmailAccount(account);
        a.setJobTitle(title.length() > 500 ? title.substring(0, 500) : title);
        a.setNormalizedTitle(jobTitle == null ? "" : TitleExtractor.normalize(jobTitle));
        a.setStatus(status == null ? ApplicationStatus.APPLIED : status);
        a.setAppliedAt(appliedAt);
        a.setLastActivityAt(appliedAt);
        a.setSource(source);
        a.setCurrentStage(StatusEngine.stageFor(a.getStatus()));
        a.setDemo(demo);
        return applicationRepository.save(a);
    }

    /** Applies the status implied by a manually reclassified email (user-confirmed). */
    @Transactional
    public boolean changeStatusFromEmail(JobApplication app, ApplicationStatus target, EmailMessage email) {
        StatusEngine.Change change = statusEngine.change(app, target, Actor.USER,
                "Reclassified email: " + email.getSubject(), email.getId(), 1.0, clock.instant(), true);
        if (change != null) {
            applicationRepository.save(app);
            events.publish(ServerEventPublisher.APPLICATION_UPDATED, summaries(List.of(app)).get(0));
        }
        return change != null;
    }

    public JobApplication load(Long id) {
        return applicationRepository.findWithCompanyById(id)
                .orElseThrow(() -> NotFoundException.of("Application", id));
    }

    // ------------------------------------------------------------------ helpers

    private static void fillIfNull(JobApplication t, JobApplication s) {
        if ("Unknown position".equals(t.getJobTitle()) && !"Unknown position".equals(s.getJobTitle())) {
            t.setJobTitle(s.getJobTitle());
            t.setNormalizedTitle(s.getNormalizedTitle());
        }
        if (t.getLocation() == null) t.setLocation(s.getLocation());
        if (t.getJobUrl() == null) t.setJobUrl(s.getJobUrl());
        if (t.getSource() == null) t.setSource(s.getSource());
        if (t.getEmploymentType() == null) t.setEmploymentType(s.getEmploymentType());
        if (t.getSalaryMin() == null) t.setSalaryMin(s.getSalaryMin());
        if (t.getSalaryMax() == null) t.setSalaryMax(s.getSalaryMax());
        if (t.getSalaryCurrency() == null) t.setSalaryCurrency(s.getSalaryCurrency());
        if (t.getRecruiterName() == null) t.setRecruiterName(s.getRecruiterName());
        if (t.getRecruiterEmail() == null) t.setRecruiterEmail(s.getRecruiterEmail());
        if (t.getApplicationRef() == null) t.setApplicationRef(s.getApplicationRef());
        if (t.getEmailAccount() == null) t.setEmailAccount(s.getEmailAccount());
    }

    private static String text(JsonNode body, String field) {
        JsonNode n = body.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static String limited(Map<String, String> errors, String field, String value, int max) {
        String v = blankToNull(value);
        if (v != null && v.length() > max) {
            errors.put(field, "must be at most " + max + " characters");
            return null;
        }
        return v;
    }

    private static BigDecimal decimal(Map<String, String> errors, JsonNode body, String field) {
        JsonNode n = body.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (!n.isNumber()) {
            errors.put(field, "must be a number");
            return null;
        }
        BigDecimal v = n.decimalValue();
        if (v.signum() < 0) {
            errors.put(field, "must be positive");
            return null;
        }
        return v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
