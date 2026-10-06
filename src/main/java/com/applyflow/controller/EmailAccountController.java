package com.applyflow.controller;

import com.applyflow.dto.AccountDtos.ConnectionTestResult;
import com.applyflow.dto.AccountDtos.CreateEmailAccountRequest;
import com.applyflow.dto.AccountDtos.EmailAccountDto;
import com.applyflow.dto.AccountDtos.SyncJobDto;
import com.applyflow.dto.AccountDtos.SyncStatusResponse;
import com.applyflow.dto.AccountDtos.UpdateEmailAccountRequest;
import com.applyflow.dto.CommonDtos.SyncStartedResponse;
import com.applyflow.service.EmailAccountService;
import com.applyflow.service.SyncService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EmailAccountController {

    private final EmailAccountService accountService;
    private final SyncService syncService;

    public EmailAccountController(EmailAccountService accountService, SyncService syncService) {
        this.accountService = accountService;
        this.syncService = syncService;
    }

    @GetMapping("/api/email-accounts")
    public List<EmailAccountDto> list() {
        return accountService.list();
    }

    @PostMapping("/api/email-accounts")
    public ResponseEntity<EmailAccountDto> create(@Valid @RequestBody CreateEmailAccountRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(body));
    }

    @PatchMapping("/api/email-accounts/{id}")
    public EmailAccountDto update(@PathVariable Long id, @Valid @RequestBody UpdateEmailAccountRequest body) {
        return accountService.update(id, body);
    }

    @PostMapping("/api/email-accounts/{id}/test")
    public ConnectionTestResult test(@PathVariable Long id) {
        return accountService.test(id);
    }

    @PostMapping("/api/email-accounts/{id}/sync")
    public ResponseEntity<SyncJobDto> sync(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(syncService.start(id));
    }

    @PostMapping("/api/email-accounts/{id}/clear")
    public ResponseEntity<Void> clear(@PathVariable Long id) {
        accountService.clear(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/email-accounts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean purge) {
        accountService.delete(id, purge);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/sync")
    public ResponseEntity<SyncStartedResponse> syncAll() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new SyncStartedResponse(syncService.startAll()));
    }

    @GetMapping("/api/sync/status")
    public SyncStatusResponse syncStatus() {
        return syncService.status();
    }
}
