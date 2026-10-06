package com.applyflow.service;

import com.applyflow.common.EventType;
import com.applyflow.common.ScheduledType;
import com.applyflow.dto.DashboardDtos.CalendarEvent;
import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.JobApplication;
import com.applyflow.exception.BadRequestException;
import com.applyflow.repository.ApplicationEventRepository;
import com.applyflow.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@Service
public class CalendarService {

    private final JobApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final SettingsService settingsService;
    private final ZoneId zone;

    public CalendarService(JobApplicationRepository applicationRepository, ApplicationEventRepository eventRepository,
                           SettingsService settingsService, ZoneId appZone) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.settingsService = settingsService;
        this.zone = appZone;
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> events(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("Both 'from' and 'to' dates are required (YYYY-MM-DD).");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("'to' must not be before 'from'.");
        }
        if (from.plusDays(400).isBefore(to)) {
            throw new BadRequestException("The requested range is too large (max ~13 months).");
        }
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        int followUpDays = settingsService.current().followUpDays();
        List<CalendarEvent> out = new ArrayList<>();

        for (JobApplication a : applicationRepository.findAllActiveWithCompany()) {
            String company = a.getCompany().getName();
            if (a.getAppliedAt() != null && within(a.getAppliedAt(), start, end)) {
                out.add(new CalendarEvent("app-" + a.getId() + "-applied", a.getId(), company, a.getJobTitle(),
                        "APPLICATION", "Applied — " + company, dayStart(a.getAppliedAt()), null, true, null));
            }
            if (a.getStatus().isWaiting() && a.getAppliedAt() != null) {
                Instant followUp = a.getAppliedAt().plus(Duration.ofDays(followUpDays));
                if (within(followUp, start, end)) {
                    out.add(new CalendarEvent("app-" + a.getId() + "-followup", a.getId(), company, a.getJobTitle(),
                            "FOLLOW_UP", "Follow up — " + company, dayStart(followUp), null, true, null));
                }
            }
        }

        for (ApplicationEventEntity ev : eventRepository.findScheduledBetween(start, end)) {
            JobApplication a = ev.getApplication();
            String company = a.getCompany().getName();
            ScheduledType st = ev.getScheduledType() == null ? ScheduledType.DEADLINE : ev.getScheduledType();
            String type;
            String title;
            Instant endAt;
            switch (st) {
                case INTERVIEW -> {
                    type = "INTERVIEW";
                    title = "Interview — " + company;
                    endAt = ev.getScheduledAt().plus(Duration.ofHours(1));
                }
                case ASSESSMENT -> {
                    type = "ASSESSMENT";
                    title = "Assessment — " + company;
                    endAt = ev.getScheduledAt().plus(Duration.ofHours(1));
                }
                case RECRUITER_CALL -> {
                    type = "RECRUITER_CALL";
                    title = "Recruiter call — " + company;
                    endAt = ev.getScheduledAt().plus(Duration.ofMinutes(30));
                }
                default -> {
                    type = "DEADLINE";
                    title = (ev.getEventType() == EventType.ASSESSMENT ? "Assessment due — " : "Deadline — ") + company;
                    endAt = null;
                }
            }
            out.add(new CalendarEvent("event-" + ev.getId(), a.getId(), company, a.getJobTitle(), type, title,
                    ev.getScheduledAt(), endAt, false, ev.getEmailId()));
        }

        for (ApplicationEventEntity ev : eventRepository.findByTypesBetween(
                EnumSet.of(EventType.OFFER, EventType.REJECTION), start, end)) {
            JobApplication a = ev.getApplication();
            String company = a.getCompany().getName();
            boolean offer = ev.getEventType() == EventType.OFFER;
            out.add(new CalendarEvent("event-" + ev.getId(), a.getId(), company, a.getJobTitle(),
                    offer ? "OFFER" : "REJECTION", (offer ? "Offer — " : "Rejected — ") + company,
                    dayStart(ev.getEventDate()), null, true, ev.getEmailId()));
        }
        out.sort(Comparator.comparing(CalendarEvent::start).thenComparing(CalendarEvent::id));
        return out;
    }

    private Instant dayStart(Instant at) {
        return at.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
    }

    private static boolean within(Instant at, Instant start, Instant end) {
        return !at.isBefore(start) && at.isBefore(end);
    }
}
