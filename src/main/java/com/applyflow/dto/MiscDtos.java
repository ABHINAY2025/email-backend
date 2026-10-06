package com.applyflow.dto;

import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.NotificationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class MiscDtos {

    private MiscDtos() {
    }

    public record NotificationDto(Long id, NotificationType type, String title, String message, Long applicationId,
                                  Long emailId, boolean read, Instant createdAt) {
    }

    public record SearchResults(List<ApplicationDtos.ApplicationSummary> applications,
                                List<InboxDtos.InboxItem> emails,
                                List<CompanyDtos.CompanySummary> companies) {
    }

    public record AppSettings(
            @NotBlank @Size(max = 200) String displayName,
            @NotNull @Min(1) @Max(1440) Integer syncIntervalMinutes,
            @NotNull @Min(0) @Max(3650) Integer defaultInitialSyncDays,
            @NotNull @DecimalMin("0.5") @DecimalMax("0.99") Double confidenceThreshold,
            @NotNull Boolean autoUpdateStatus,
            @NotNull @Min(1) @Max(365) Integer followUpDays) {
    }

    public record ServerEvent(String type, Object payload, Instant timestamp) {
    }

    public record StatusChangedPayload(Long applicationId, String companyName, String jobTitle,
                                       ApplicationStatus from, ApplicationStatus to, Actor actor) {
    }
}
