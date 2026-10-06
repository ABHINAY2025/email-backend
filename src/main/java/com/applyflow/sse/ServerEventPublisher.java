package com.applyflow.sse;

import com.applyflow.dto.MiscDtos.ServerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Publishes server events as Spring application events; they are relayed to the SSE clients of the owning user only
 * after the surrounding transaction commits (or immediately when there is no transaction).
 */
@Component
public class ServerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ServerEventPublisher.class);

    public static final String APPLICATION_CREATED = "APPLICATION_CREATED";
    public static final String APPLICATION_UPDATED = "APPLICATION_UPDATED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String EMAIL_RECEIVED = "EMAIL_RECEIVED";
    public static final String SYNC_STARTED = "SYNC_STARTED";
    public static final String SYNC_COMPLETED = "SYNC_COMPLETED";
    public static final String SYNC_FAILED = "SYNC_FAILED";
    public static final String NOTIFICATION_CREATED = "NOTIFICATION_CREATED";

    /** Wrapper so the listener only receives our events; {@code userId} is the only recipient. */
    public record ServerEventMessage(Long userId, ServerEvent event) {
    }

    private final ApplicationEventPublisher publisher;
    private final SseEmitterRegistry registry;

    public ServerEventPublisher(ApplicationEventPublisher publisher, SseEmitterRegistry registry) {
        this.publisher = publisher;
        this.registry = registry;
    }

    /**
     * @param userId  owner of the data the event is about (the only user who receives it)
     * @param payload must already be a DTO (built inside the transaction)
     */
    public void publish(Long userId, String type, Object payload) {
        if (userId == null) {
            log.warn("Dropping server event {} without a recipient", type);
            return;
        }
        publisher.publishEvent(new ServerEventMessage(userId, new ServerEvent(type, payload, Instant.now())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(ServerEventMessage message) {
        registry.send(message.userId(), message.event());
    }
}
