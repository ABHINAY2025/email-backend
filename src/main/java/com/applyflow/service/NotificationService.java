package com.applyflow.service;

import com.applyflow.common.NotificationType;
import com.applyflow.dto.MiscDtos.NotificationDto;
import com.applyflow.entity.Notification;
import com.applyflow.exception.NotFoundException;
import com.applyflow.mapper.DtoMapper;
import com.applyflow.repository.NotificationRepository;
import com.applyflow.security.CurrentUser;
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

    /** Creates a notification for {@code userId} (the owner of the data it is about). */
    @Transactional
    public Notification create(Long userId, NotificationType type, String title, String message, Long applicationId,
                               Long emailId) {
        return create(userId, type, title, message, applicationId, emailId, null);
    }

    @Transactional
    public Notification create(Long userId, NotificationType type, String title, String message, Long applicationId,
                               Long emailId, Instant createdAt) {
        if (userId == null) {
            throw new IllegalStateException("Notification without an owner");
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(truncate(title, 500));
        n.setMessage(truncate(message == null ? "" : message, 2000));
        n.setApplicationId(applicationId);
        n.setEmailId(emailId);
        n.setCreatedAt(createdAt);
        repository.save(n);
        events.publish(userId, ServerEventPublisher.NOTIFICATION_CREATED, mapper.toNotification(n));
        return n;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(boolean unreadOnly, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        Long userId = CurrentUser.id();
        List<Notification> list = unreadOnly
                ? repository.findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(userId, page)
                : repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, page);
        return list.stream().map(mapper::toNotification).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByUserIdAndReadFalse(CurrentUser.id());
    }

    @Transactional
    public NotificationDto markRead(Long id) {
        Notification n = repository.findByIdAndUserId(id, CurrentUser.id()).orElseThrow(() -> NotFoundException.of("Notification", id));
        n.setRead(true);
        repository.save(n);
        return mapper.toNotification(n);
    }

    @Transactional
    public void markAllRead() {
        repository.markAllRead(CurrentUser.id());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
