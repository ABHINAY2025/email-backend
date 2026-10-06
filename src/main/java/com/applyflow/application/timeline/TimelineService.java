package com.applyflow.application.timeline;

import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EventType;
import com.applyflow.common.ScheduledType;
import com.applyflow.dto.ApplicationDtos.TimelineEvent;
import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.repository.ApplicationEventRepository;
import com.applyflow.security.CurrentUser;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;

@Service
public class TimelineService {

    private final ApplicationEventRepository repository;
    private final DtoMapper mapper;
    private final MongoOperations mongo;

    public TimelineService(ApplicationEventRepository repository, DtoMapper mapper, MongoOperations mongo) {
        this.mongo = mongo;
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Oldest first. The caller has checked that the application belongs to the current user. */
    @Transactional(readOnly = true)
    public List<TimelineEvent> timeline(Long applicationId) {
        Long userId = CurrentUser.id();
        List<ApplicationEventEntity> events = repository.findTimeline(userId, applicationId);
        // Subjects of the linked emails in one query (bodies are not loaded).
        List<Long> emailIds = events.stream().map(ApplicationEventEntity::getEmailId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> subjects = new HashMap<>();
        if (!emailIds.isEmpty()) {
            Query q = Query.query(Criteria.where("userId").is(userId).and("_id").in(emailIds));
            q.fields().include("subject");
            for (EmailMessage e : mongo.find(q, EmailMessage.class)) {
                subjects.put(e.getId(), e.getSubject());
            }
        }
        return events.stream()
                .map(ev -> mapper.toTimeline(ev, ev.getEmailId() != null && subjects.containsKey(ev.getEmailId()),
                        subjects.get(ev.getEmailId())))
                .toList();
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public List<TimelineEvent> events(Long applicationId) {
        List<TimelineEvent> list = new ArrayList<>(timeline(applicationId));
        Collections.reverse(list);
        return list;
    }

    @Transactional
    public ApplicationEventEntity add(JobApplication app, EmailMessage email, EventType type, String title,
                                      String description, Instant eventDate, ApplicationStatus previous,
                                      ApplicationStatus next, Double confidence, Actor actor, Instant scheduledAt,
                                      ScheduledType scheduledType) {
        ApplicationEventEntity ev = new ApplicationEventEntity();
        ev.setApplication(app);
        ev.setEmail(email);
        ev.setEventType(type);
        ev.setTitle(title.length() > 500 ? title.substring(0, 500) : title);
        ev.setDescription(description);
        ev.setEventDate(eventDate == null ? Instant.now() : eventDate);
        ev.setPreviousStatus(previous);
        ev.setNewStatus(next);
        ev.setConfidence(confidence);
        ev.setActor(actor);
        ev.setScheduledAt(scheduledAt);
        ev.setScheduledType(scheduledAt == null ? null : scheduledType);
        return repository.save(ev);
    }
}
