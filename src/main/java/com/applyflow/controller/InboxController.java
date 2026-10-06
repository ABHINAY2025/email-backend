package com.applyflow.controller;

import com.applyflow.dto.ApplicationDtos.ApplicationDetail;
import com.applyflow.dto.CommonDtos.PageResponse;
import com.applyflow.dto.InboxDtos.EmailDetail;
import com.applyflow.dto.InboxDtos.InboxCounts;
import com.applyflow.dto.InboxDtos.InboxItem;
import com.applyflow.dto.InboxDtos.MergeEmailRequest;
import com.applyflow.dto.InboxDtos.ReadRequest;
import com.applyflow.dto.InboxDtos.ReclassifyRequest;
import com.applyflow.service.InboxService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private final InboxService inboxService;

    public InboxController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    public PageResponse<InboxItem> list(@RequestParam(defaultValue = "all") String tab,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) Long accountId,
                                        @RequestParam(defaultValue = "false") boolean unreadOnly,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return inboxService.list(tab, q, accountId, unreadOnly, page, size);
    }

    @GetMapping("/counts")
    public InboxCounts counts() {
        return inboxService.counts();
    }

    @GetMapping("/{id}")
    public EmailDetail get(@PathVariable Long id) {
        return inboxService.get(id);
    }

    @PatchMapping("/{id}/read")
    public InboxItem read(@PathVariable Long id, @Valid @RequestBody ReadRequest body) {
        return inboxService.markRead(id, body.read());
    }

    @PostMapping("/{id}/merge")
    public EmailDetail merge(@PathVariable Long id, @Valid @RequestBody MergeEmailRequest body) {
        return inboxService.merge(id, body.applicationId());
    }

    @PostMapping("/{id}/create-application")
    public ApplicationDetail createApplication(@PathVariable Long id) {
        return inboxService.createApplication(id);
    }

    @PostMapping("/{id}/ignore")
    public ResponseEntity<Void> ignore(@PathVariable Long id) {
        inboxService.ignore(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reclassify")
    public EmailDetail reclassify(@PathVariable Long id, @Valid @RequestBody ReclassifyRequest body) {
        return inboxService.reclassify(id, body.classification());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        inboxService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
