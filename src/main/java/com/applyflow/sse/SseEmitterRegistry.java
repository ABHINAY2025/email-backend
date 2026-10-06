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
import java.util.concurrent.CopyOnWriteArrayList;

/** Holds connected SSE clients and broadcasts server events to them. */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public SseEmitterRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; heartbeats keep proxies happy
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().comment("connected").reconnectTime(5000));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcast(ServerEvent event) {
        if (emitters.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            log.warn("Could not serialize server event {}", event.type());
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event.type()).data(json, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                safeComplete(emitter);
            }
        }
    }

    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                safeComplete(emitter);
            }
        }
    }

    public int size() {
        return emitters.size();
    }

    private static void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already closed
        }
    }
}
