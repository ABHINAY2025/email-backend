package com.applyflow.sse;

import com.applyflow.dto.MiscDtos.ServerEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Publishes server events as Spring application events; they are relayed to SSE clients only after the surrounding
 * transaction commits (or immediately when there is no transaction).
 */
@Component
public class ServerEventPublisher {

    public static final String APPLICATION_CREATED = "APPLICATION_CREATED";
    public static final String APPLICATION_UPDATED = "APPLICATION_UPDATED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String EMAIL_RECEIVED = "EMAIL_RECEIVED";
    public static final String SYNC_STARTED = "SYNC_STARTED";
    public static final String SYNC_COMPLETED = "SYNC_COMPLETED";
    public static final String SYNC_FAILED = "SYNC_FAILED";
    public static final String NOTIFICATION_CREATED = "NOTIFICATION_CREATED";

    /** Wrapper so the listener only receives our events. */
    public record ServerEventMessage(ServerEvent event) {
    }

    private final ApplicationEventPublisher publisher;
    private final SseEmitterRegistry registry;

    public ServerEventPublisher(ApplicationEventPublisher publisher, SseEmitterRegistry registry) {
        this.publisher = publisher;
        this.registry = registry;
    }

    /** Payload must already be a DTO (built inside the transaction). */
    public void publish(String type, Object payload) {
        publisher.publishEvent(new ServerEventMessage(new ServerEvent(type, payload, Instant.now())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(ServerEventMessage message) {
        registry.broadcast(message.event());
    }
}
