package com.applyflow.controller;

import com.applyflow.dto.CommonDtos.CountResponse;
import com.applyflow.dto.CommonDtos.HealthResponse;
import com.applyflow.dto.MiscDtos.AppSettings;
import com.applyflow.dto.MiscDtos.NotificationDto;
import com.applyflow.service.HealthService;
import com.applyflow.service.NotificationService;
import com.applyflow.service.PrivacyService;
import com.applyflow.service.SettingsService;
import com.applyflow.security.CurrentUser;
import com.applyflow.sse.SseEmitterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** Notifications, settings, privacy, health and the SSE stream. */
@RestController
public class SystemController {

    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final PrivacyService privacyService;
    private final HealthService healthService;
    private final SseEmitterRegistry sseRegistry;

    public SystemController(NotificationService notificationService, SettingsService settingsService,
                            PrivacyService privacyService, HealthService healthService,
                            SseEmitterRegistry sseRegistry) {
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.privacyService = privacyService;
        this.healthService = healthService;
        this.sseRegistry = sseRegistry;
    }

    // -------------------------------------------------------------- notifications

    @GetMapping("/api/notifications")
    public List<NotificationDto> notifications(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                               @RequestParam(defaultValue = "50") int limit) {
        return notificationService.list(unreadOnly, limit);
    }

    @GetMapping("/api/notifications/unread-count")
    public CountResponse unreadCount() {
        return new CountResponse(notificationService.unreadCount());
    }

    @PatchMapping("/api/notifications/{id}/read")
    public NotificationDto markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Void> readAll() {
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- settings

    @GetMapping("/api/settings")
    public AppSettings settings() {
        return settingsService.get();
    }

    @PutMapping("/api/settings")
    public AppSettings updateSettings(@Valid @RequestBody AppSettings body) {
        return settingsService.update(body);
    }

    // -------------------------------------------------------------- privacy

    @PostMapping("/api/privacy/clear-imported-mail")
    public ResponseEntity<Void> clearImportedMail() {
        privacyService.clearImportedMail();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/privacy/clear-demo-data")
    public ResponseEntity<Void> clearDemoData() {
        privacyService.clearDemoData();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/privacy/delete-all-data")
    public ResponseEntity<Void> deleteAllData() {
        privacyService.deleteAllData();
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- health & SSE

    @GetMapping("/api/health")
    public HealthResponse health() {
        return healthService.health();
    }

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no"); // nginx: do not buffer SSE
        return sseRegistry.register(CurrentUser.id()); // events of this user only
    }
}
