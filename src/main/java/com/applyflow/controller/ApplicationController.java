package com.applyflow.controller;

import com.applyflow.application.service.ApplicationQuery;
import com.applyflow.application.service.ApplicationService;
import com.applyflow.application.timeline.TimelineService;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.dto.ApplicationDtos.ApplicationDetail;
import com.applyflow.dto.ApplicationDtos.ApplicationFacets;
import com.applyflow.dto.ApplicationDtos.ApplicationSummary;
import com.applyflow.dto.ApplicationDtos.CreateApplicationRequest;
import com.applyflow.dto.ApplicationDtos.MergeApplicationRequest;
import com.applyflow.dto.ApplicationDtos.NoteDto;
import com.applyflow.dto.ApplicationDtos.NoteRequest;
import com.applyflow.dto.ApplicationDtos.StatusHistoryEntry;
import com.applyflow.dto.ApplicationDtos.TimelineEvent;
import com.applyflow.dto.ApplicationDtos.UpdateStatusRequest;
import com.applyflow.dto.CommonDtos.PageResponse;
import com.applyflow.dto.InboxDtos.EmailDetail;
import com.applyflow.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final TimelineService timelineService;

    public ApplicationController(ApplicationService applicationService, TimelineService timelineService) {
        this.applicationService = applicationService;
        this.timelineService = timelineService;
    }

    @GetMapping
    public PageResponse<ApplicationSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long emailAccountId,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "lastActivityAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return applicationService.search(new ApplicationQuery(q, bucket, parseStatuses(status), companyId, from, to,
                location, source, emailAccountId, jobTitle, archived, sort, dir, page, size));
    }

    @GetMapping("/facets")
    public ApplicationFacets facets() {
        return applicationService.facets();
    }

    @GetMapping("/{id}")
    public ApplicationDetail get(@PathVariable Long id) {
        return applicationService.get(id);
    }

    @PostMapping
    public ResponseEntity<ApplicationDetail> create(@Valid @RequestBody CreateApplicationRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(body));
    }

    @PatchMapping("/{id}")
    public ApplicationDetail update(@PathVariable Long id, @RequestBody JsonNode body) {
        return applicationService.update(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        applicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ApplicationDetail status(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest body) {
        return applicationService.changeStatus(id, body.status(), body.reason());
    }

    @GetMapping("/{id}/emails")
    public List<EmailDetail> emails(@PathVariable Long id) {
        return applicationService.emails(id);
    }

    @GetMapping("/{id}/timeline")
    public List<TimelineEvent> timeline(@PathVariable Long id) {
        applicationService.load(id);
        return timelineService.timeline(id);
    }

    @GetMapping("/{id}/events")
    public List<TimelineEvent> events(@PathVariable Long id) {
        applicationService.load(id);
        return timelineService.events(id);
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryEntry> history(@PathVariable Long id) {
        return applicationService.history(id);
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<NoteDto> addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.addNote(id, body.content()));
    }

    @PatchMapping("/{id}/notes/{noteId}")
    public NoteDto updateNote(@PathVariable Long id, @PathVariable Long noteId, @Valid @RequestBody NoteRequest body) {
        return applicationService.updateNote(id, noteId, body.content());
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long id, @PathVariable Long noteId) {
        applicationService.deleteNote(id, noteId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/merge")
    public ApplicationDetail merge(@PathVariable Long id, @Valid @RequestBody MergeApplicationRequest body) {
        return applicationService.merge(id, body.sourceApplicationId());
    }

    private static List<ApplicationStatus> parseStatuses(String raw) {
        List<ApplicationStatus> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String s : raw.split(",")) {
            if (s.isBlank()) {
                continue;
            }
            try {
                out.add(ApplicationStatus.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown status '" + s.trim() + "'.");
            }
        }
        return out;
    }
}
