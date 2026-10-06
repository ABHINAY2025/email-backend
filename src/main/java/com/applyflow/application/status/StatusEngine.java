package com.applyflow.application.status;

import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EventType;
import com.applyflow.common.NotificationType;
import com.applyflow.dto.MiscDtos.StatusChangedPayload;
import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.StatusHistory;
import com.applyflow.repository.ApplicationEventRepository;
import com.applyflow.repository.StatusHistoryRepository;
import com.applyflow.service.NotificationService;
import com.applyflow.sse.ServerEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Applies status changes with history, timeline event, notification and SSE. */
@Service
public class StatusEngine {

    private final StatusHistoryRepository historyRepository;
    private final ApplicationEventRepository eventRepository;
    private final NotificationService notificationService;
    private final ServerEventPublisher events;

    public StatusEngine(StatusHistoryRepository historyRepository, ApplicationEventRepository eventRepository,
                        NotificationService notificationService, ServerEventPublisher events) {
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
        this.events = events;
    }

    public record Change(ApplicationStatus from, ApplicationStatus to) {
    }

    /**
     * Changes the status if the policy allows it.
     *
     * @param createEvent true to add a STATUS_CHANGE timeline event (manual changes); email-driven changes record the
     *                    transition on the email's own event instead
     * @return the change, or null if not allowed / no-op
     */
    @Transactional
    public Change change(JobApplication app, ApplicationStatus target, Actor actor, String reason, Long emailId,
                         Double confidence, Instant at, boolean createEvent) {
        ApplicationStatus from = app.getStatus();
        if (!StatusTransitionPolicy.isAllowed(from, target, actor)) {
            return null;
        }
        Instant when = at == null ? Instant.now() : at;
        app.setStatus(target);
        app.touchActivity(when);
        app.setCurrentStage(stageFor(target));
        if (actor == Actor.SYSTEM) {
            app.setConfidence(confidence);
        }

        recordHistory(app, from, target, actor, reason, emailId, confidence, when);

        if (createEvent) {
            ApplicationEventEntity ev = new ApplicationEventEntity();
            ev.setApplication(app);
            ev.setEventType(EventType.STATUS_CHANGE);
            ev.setTitle("Application moved to " + target.label());
            ev.setDescription(reason == null || reason.isBlank()
                    ? "Status changed from " + (from == null ? "none" : from.label()) + " to " + target.label() + "."
                    : reason);
            ev.setEventDate(when);
            ev.setPreviousStatus(from);
            ev.setNewStatus(target);
            ev.setConfidence(actor == Actor.SYSTEM ? confidence : null);
            ev.setActor(actor);
            eventRepository.save(ev);
        }

        notifyChange(app, from, target, actor, emailId, when);
        events.publish(app.getUserId(), ServerEventPublisher.STATUS_CHANGED, new StatusChangedPayload(app.getId(),
                app.getCompany().getName(), app.getJobTitle(), from, target, actor));
        return new Change(from, target);
    }

    /** Records the initial status of a newly created application. */
    @Transactional
    public void recordInitial(JobApplication app, Actor actor, String reason, Long emailId, Double confidence,
                              Instant at) {
        recordHistory(app, null, app.getStatus(), actor, reason, emailId, confidence, at == null ? Instant.now() : at);
    }

    private void recordHistory(JobApplication app, ApplicationStatus from, ApplicationStatus to, Actor actor,
                               String reason, Long emailId, Double confidence, Instant at) {
        StatusHistory h = new StatusHistory();
        h.setApplication(app);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActor(actor);
        h.setReason(reason == null ? null : (reason.length() > 1000 ? reason.substring(0, 1000) : reason));
        h.setEmailId(emailId);
        h.setConfidence(confidence);
        h.setChangedAt(at);
        historyRepository.save(h);
    }

    private void notifyChange(JobApplication app, ApplicationStatus from, ApplicationStatus to, Actor actor,
                              Long emailId, Instant at) {
        String company = app.getCompany().getName();
        String title = app.getJobTitle();
        NotificationType type = switch (to) {
            case INTERVIEW -> NotificationType.NEW_INTERVIEW;
            case OFFER -> NotificationType.NEW_OFFER;
            case REJECTED -> NotificationType.NEW_REJECTION;
            case ASSESSMENT -> NotificationType.ASSESSMENT_DEADLINE;
            case RECRUITER_CONTACT -> NotificationType.RECRUITER_RESPONSE;
            default -> NotificationType.STATUS_CHANGE;
        };
        if (actor == Actor.USER) {
            type = NotificationType.STATUS_CHANGE;
        }
        String headline = switch (type) {
            case NEW_INTERVIEW -> "Interview — " + company;
            case NEW_OFFER -> "Offer from " + company + "!";
            case NEW_REJECTION -> "Update from " + company;
            case ASSESSMENT_DEADLINE -> "Assessment — " + company;
            case RECRUITER_RESPONSE -> "Recruiter response — " + company;
            default -> "Status changed — " + company;
        };
        String message = title + ": " + (from == null ? "" : from.label() + " → ") + to.label()
                + (actor == Actor.USER ? " (updated by you)" : "");
        notificationService.create(app.getUserId(), type, headline, message, app.getId(), emailId,
                at.isAfter(Instant.now()) ? null : at);
    }

    public static String stageFor(ApplicationStatus s) {
        return switch (s) {
            case APPLIED -> "Application submitted";
            case UNDER_REVIEW -> "Application under review";
            case ASSESSMENT -> "Assessment in progress";
            case RECRUITER_CONTACT -> "In touch with recruiter";
            case INTERVIEW -> "Interviewing";
            case OFFER -> "Offer received";
            case REJECTED -> "Not selected";
            case WITHDRAWN -> "Withdrawn";
            case CLOSED -> "Closed";
        };
    }
}
