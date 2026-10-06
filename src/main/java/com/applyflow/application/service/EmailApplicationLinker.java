package com.applyflow.application.service;

import com.applyflow.application.status.StatusEngine;
import com.applyflow.application.status.StatusTransitionPolicy;
import com.applyflow.application.timeline.TimelineService;
import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailClassification;
import com.applyflow.common.EventType;
import com.applyflow.common.NotificationType;
import com.applyflow.common.ScheduledType;
import com.applyflow.dto.MiscDtos.AppSettings;
import com.applyflow.entity.Contact;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.intelligence.ExtractedJobDetails;
import com.applyflow.intelligence.SummaryGenerator;
import com.applyflow.repository.ContactRepository;
import com.applyflow.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Links a stored email to an application: applies the status impact (respecting policy, threshold and settings),
 * enriches application fields, records the timeline event, upserts the recruiter contact and notifies.
 */
@Service
public class EmailApplicationLinker {

    private final StatusEngine statusEngine;
    private final TimelineService timelineService;
    private final ContactRepository contactRepository;
    private final NotificationService notificationService;
    private final ZoneId zone;

    public EmailApplicationLinker(StatusEngine statusEngine, TimelineService timelineService,
                                  ContactRepository contactRepository, NotificationService notificationService,
                                  ZoneId appZone) {
        this.statusEngine = statusEngine;
        this.timelineService = timelineService;
        this.contactRepository = contactRepository;
        this.notificationService = notificationService;
        this.zone = appZone;
    }

    public record LinkResult(boolean statusChanged, ApplicationStatus from, ApplicationStatus to) {
    }

    /**
     * @param newlyCreated the application was just created from this email (initial status already set)
     * @param userConfirmed the user explicitly linked the email (merge from inbox) – skips the confidence gate
     */
    @Transactional
    public LinkResult link(EmailMessage email, JobApplication app, ExtractedJobDetails details, boolean newlyCreated,
                           Actor actor, boolean userConfirmed, AppSettings settings) {
        email.setApplication(app);
        EmailClassification cls = email.getClassification();
        ApplicationStatus detected = email.getDetectedStatus();
        double confidence = email.getClassificationConfidence() == null ? 0 : email.getClassificationConfidence();
        Instant at = email.getReceivedAt();
        ApplicationStatus before = app.getStatus();
        StatusEngine.Change change = null;
        boolean applied = false;

        if (newlyCreated) {
            statusEngine.recordInitial(app, actor, "Created from email: " + email.getSubject(), email.getId(),
                    confidence, at);
            applied = detected != null;
            email.setPreviousStatus(null);
        } else {
            email.setPreviousStatus(before);
            if (detected != null) {
                if (before == detected) {
                    applied = true;
                } else if (!StatusTransitionPolicy.isAllowed(before, detected, Actor.SYSTEM)) {
                    applied = false; // backwards / out of terminal: keep the email, no status change
                } else if (userConfirmed || (settings.autoUpdateStatus()
                        && confidence >= settings.confidenceThreshold())) {
                    change = statusEngine.change(app, detected, actor,
                            "Detected from email (" + cls.label().toLowerCase() + "): " + email.getSubject(),
                            email.getId(), confidence, at, false);
                    applied = change != null;
                } else {
                    email.setNeedsReview(true);
                    app.setNeedsReview(true);
                }
            }
        }
        email.setStatusApplied(applied);

        enrich(app, details, cls, confidence, at);

        ScheduledType scheduledType = email.getScheduledAt() == null || details == null ? null
                : details.scheduledTypeFor(cls);
        String company = app.getCompany().getName();
        timelineService.add(app, email, EventType.fromClassification(cls),
                SummaryGenerator.eventTitle(cls, company), email.getSummary(), at,
                change != null ? change.from() : null,
                change != null ? change.to() : (newlyCreated ? app.getStatus() : null),
                confidence, actor, email.getScheduledAt(), scheduledType);

        upsertContact(app, details, at);

        if (change == null) {
            notifyCategory(email, app, cls, at);
        }
        return new LinkResult(change != null, change == null ? null : change.from(), change == null ? null : change.to());
    }

    private void enrich(JobApplication app, ExtractedJobDetails d, EmailClassification cls, double confidence,
                        Instant at) {
        app.touchActivity(at);
        app.setConfidence(confidence);
        if (d == null) {
            return;
        }
        if (app.getLocation() == null && d.location() != null) {
            app.setLocation(cut(d.location(), 255));
        }
        if (app.getJobUrl() == null && d.jobUrl() != null) {
            app.setJobUrl(cut(d.jobUrl(), 2000));
        }
        if (app.getApplicationRef() == null && d.applicationRef() != null) {
            app.setApplicationRef(cut(d.applicationRef(), 100));
        }
        if (app.getEmploymentType() == null && d.employmentType() != null) {
            app.setEmploymentType(d.employmentType());
        }
        if (app.getSalaryMin() == null && d.salaryMin() != null) {
            app.setSalaryMin(d.salaryMin());
            app.setSalaryMax(d.salaryMax());
            app.setSalaryCurrency(d.salaryCurrency());
        }
        if (d.recruiterEmail() != null && (app.getRecruiterEmail() == null
                || cls == EmailClassification.RECRUITER_CONTACT || cls == EmailClassification.INTERVIEW_INVITATION)) {
            app.setRecruiterEmail(cut(d.recruiterEmail(), 320));
            app.setRecruiterName(cut(d.recruiterName(), 255));
        }
        if ("Unknown position".equals(app.getJobTitle()) && d.jobTitle() != null) {
            app.setJobTitle(d.jobTitle());
            app.setNormalizedTitle(com.applyflow.mail.classifier.TitleExtractor.normalize(d.jobTitle()));
        }
        if (app.getSource() == null && d.source() != null) {
            app.setSource(d.source());
        }
    }

    private void upsertContact(JobApplication app, ExtractedJobDetails d, Instant at) {
        if (d == null || d.recruiterEmail() == null) {
            return;
        }
        Contact c = contactRepository.findFirstByUserIdAndCompanyIdAndEmailIgnoreCase(app.getUserId(),
                        app.getCompany().getId(), d.recruiterEmail())
                .orElseGet(() -> {
                    Contact n = new Contact();
                    n.setUserId(app.getUserId());
                    n.setCompany(app.getCompany());
                    n.setEmail(d.recruiterEmail().toLowerCase());
                    n.setRole("Recruiter");
                    return n;
                });
        if (d.recruiterName() != null) {
            c.setName(cut(d.recruiterName(), 255));
        }
        if (c.getLastContactAt() == null || at.isAfter(c.getLastContactAt())) {
            c.setLastContactAt(at);
        }
        contactRepository.save(c);
    }

    private void notifyCategory(EmailMessage email, JobApplication app, EmailClassification cls, Instant at) {
        String company = app.getCompany().getName();
        NotificationType type;
        String title;
        switch (cls) {
            case INTERVIEW_INVITATION, INTERVIEW_UPDATE -> {
                type = NotificationType.NEW_INTERVIEW;
                title = (cls == EmailClassification.INTERVIEW_UPDATE ? "Interview update — " : "Interview — ") + company;
            }
            case ASSESSMENT -> {
                type = NotificationType.ASSESSMENT_DEADLINE;
                title = "Assessment — " + company;
            }
            case OFFER -> {
                type = NotificationType.NEW_OFFER;
                title = "Offer from " + company + "!";
            }
            case REJECTION -> {
                type = NotificationType.NEW_REJECTION;
                title = "Update from " + company;
            }
            case RECRUITER_CONTACT -> {
                type = NotificationType.RECRUITER_RESPONSE;
                title = "Recruiter response — " + company;
            }
            default -> {
                return;
            }
        }
        String message = email.getSummary() != null ? email.getSummary() : app.getJobTitle();
        if (email.getScheduledAt() != null) {
            message = message + " (" + email.getScheduledAt().atZone(zone)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", java.util.Locale.ENGLISH))
                    + ")";
        }
        notificationService.create(app.getUserId(), type, title, message, app.getId(), email.getId(),
                at.isAfter(Instant.now()) ? null : at);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
