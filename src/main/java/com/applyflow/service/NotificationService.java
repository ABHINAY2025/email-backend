package com.applyflow.service;

import com.applyflow.common.NotificationType;
import com.applyflow.dto.MiscDtos.NotificationDto;
import com.applyflow.entity.Notification;
import com.applyflow.exception.NotFoundException;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.repository.NotificationRepository;
import com.applyflow.sse.ServerEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final DtoMapper mapper;
    private final ServerEventPublisher events;

    public NotificationService(NotificationRepository repository, DtoMapper mapper, ServerEventPublisher events) {
        this.repository = repository;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional
    public Notification create(NotificationType type, String title, String message, Long applicationId,
                               Long emailId) {
        return create(type, title, message, applicationId, emailId, null);
    }

    @Transactional
    public Notification create(NotificationType type, String title, String message, Long applicationId,
                               Long emailId, Instant createdAt) {
        Notification n = new Notification();
        n.setType(type);
        n.setTitle(truncate(title, 500));
        n.setMessage(truncate(message == null ? "" : message, 2000));
        n.setApplicationId(applicationId);
        n.setEmailId(emailId);
        n.setCreatedAt(createdAt);
        repository.save(n);
        events.publish(ServerEventPublisher.NOTIFICATION_CREATED, mapper.toNotification(n));
        return n;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(boolean unreadOnly, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        List<Notification> list = unreadOnly ? repository.findByReadFalseOrderByCreatedAtDescIdDesc(page)
                : repository.findAllByOrderByCreatedAtDescIdDesc(page);
        return list.stream().map(mapper::toNotification).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByReadFalse();
    }

    @Transactional
    public NotificationDto markRead(Long id) {
        Notification n = repository.findById(id).orElseThrow(() -> NotFoundException.of("Notification", id));
        n.setRead(true);
        repository.save(n);
        return mapper.toNotification(n);
    }

    @Transactional
    public void markAllRead() {
        repository.markAllRead();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
