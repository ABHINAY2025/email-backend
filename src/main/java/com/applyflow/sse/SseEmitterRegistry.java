package com.applyflow.sse;

import com.applyflow.dto.MiscDtos.ServerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds connected SSE clients keyed by the id of the signed-in user and sends each server event only to the
 * emitters of the user who owns the data.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitterRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter register(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("SSE emitters must belong to a user");
        }
        SseEmitter emitter = new SseEmitter(0L); // no timeout; heartbeats keep proxies happy
        List<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().comment("connected").reconnectTime(5000));
        } catch (IOException | IllegalStateException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /** Sends the event to the given user's sessions only. */
    public void send(Long userId, ServerEvent event) {
        if (userId == null) {
            return;
        }
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            log.warn("Could not serialize server event {}", event.type());
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event.type()).data(json, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
                safeComplete(emitter);
            }
        }
    }

    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                    safeComplete(emitter);
                }
            }
        });
    }

    public int size() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    private static void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already closed
        }
    }
}
