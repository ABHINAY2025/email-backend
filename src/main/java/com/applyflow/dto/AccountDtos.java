package com.applyflow.dto;

import com.applyflow.common.EmailProvider;
import com.applyflow.common.SyncJobStatus;
import com.applyflow.common.SyncStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record EmailAccountDto(Long id, String email, EmailProvider provider, String host, int port, boolean ssl,
                                  String username, String folder, SyncStatus syncStatus, boolean enabled,
                                  Instant lastSyncAt, String lastError, long emailsProcessed, long jobEmails,
                                  int initialSyncDays, boolean hasPassword, Instant createdAt) {
    }

    public record CreateEmailAccountRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 500) String appPassword,
            EmailProvider provider,
            @Size(max = 255) String host,
            @Min(1) @Max(65535) Integer port,
            Boolean ssl,
            @Size(max = 320) String username,
            @Size(max = 255) String folder,
            @Min(0) @Max(3650) Integer initialSyncDays) {
        @Override
        public String toString() {
            return "CreateEmailAccountRequest{email=" + email + ", provider=" + provider + "}";
        }
    }

    public record UpdateEmailAccountRequest(
            @Size(max = 500) String appPassword,
            @Size(max = 255) String host,
            @Min(1) @Max(65535) Integer port,
            Boolean ssl,
            @Size(max = 320) String username,
            @Size(max = 255) String folder,
            @Min(0) @Max(3650) Integer initialSyncDays,
            Boolean enabled) {
        @Override
        public String toString() {
            return "UpdateEmailAccountRequest{host=" + host + ", enabled=" + enabled + "}";
        }
    }

    public record ConnectionTestResult(boolean success, String message) {
    }

    public record SyncJobDto(Long id, Long emailAccountId, String emailAccountEmail, SyncJobStatus status,
                             Instant startedAt, Instant finishedAt, int messagesFetched, int messagesProcessed,
                             int jobEmailsFound, int applicationsCreated, int applicationsUpdated, String error) {
    }

    public record AccountSyncState(Long accountId, String email, SyncStatus syncStatus, Instant lastSyncAt,
                                   String lastError) {
    }

    public record SyncStatusResponse(String state, Instant lastSyncAt, String lastError, int intervalMinutes,
                                     List<AccountSyncState> accounts, List<SyncJobDto> recentJobs) {
    }
}
