package com.applyflow.dto;

import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailProvider;
import com.applyflow.common.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record ApplicationSummary(
            Long id,
            String displayId,
            Long companyId,
            String companyName,
            String companyDomain,
            String jobTitle,
            String location,
            String source,
            ApplicationStatus status,
            Instant appliedAt,
            Instant lastActivityAt,
            long emailCount,
            Long emailAccountId,
            String emailAccountEmail,
            EmailProvider provider,
            boolean needsReview,
            boolean archived) {
    }

    public record NoteDto(Long id, Long applicationId, String content, Instant createdAt, Instant updatedAt) {
    }

    public record StatusHistoryEntry(Long id, ApplicationStatus fromStatus, ApplicationStatus toStatus, Actor actor,
                                     String reason, Long emailId, Double confidence, Instant changedAt) {
    }

    /** Flattened ApplicationDetail (extends ApplicationSummary in the contract). */
    public record ApplicationDetail(
            Long id,
            String displayId,
            Long companyId,
            String companyName,
            String companyDomain,
            String jobTitle,
            String location,
            String source,
            ApplicationStatus status,
            Instant appliedAt,
            Instant lastActivityAt,
            long emailCount,
            Long emailAccountId,
            String emailAccountEmail,
            EmailProvider provider,
            boolean needsReview,
            boolean archived,
            String jobUrl,
            String employmentType,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String recruiterName,
            String recruiterEmail,
            String applicationRef,
            String currentStage,
            Double confidence,
            Instant createdAt,
            Instant updatedAt,
            List<NoteDto> notes,
            List<StatusHistoryEntry> statusHistory) {
    }

    public record TimelineEvent(
            Long id,
            Long applicationId,
            EventType eventType,
            String title,
            String description,
            Instant eventDate,
            ApplicationStatus previousStatus,
            ApplicationStatus newStatus,
            Double confidence,
            Actor actor,
            Long emailId,
            String emailSubject,
            Instant scheduledAt) {
    }

    public record CreateApplicationRequest(
            @NotBlank @Size(max = 255) String companyName,
            @NotBlank @Size(max = 500) String jobTitle,
            @Size(max = 2000) String jobUrl,
            @Size(max = 255) String location,
            LocalDate appliedAt,
            @Size(max = 100) String source,
            ApplicationStatus status,
            @Size(max = 50) String employmentType,
            @Size(max = 20000) String notes) {
    }

    public record UpdateStatusRequest(@NotNull ApplicationStatus status, @Size(max = 1000) String reason) {
    }

    public record NoteRequest(@NotBlank @Size(max = 20000) String content) {
    }

    public record MergeApplicationRequest(@NotNull Long sourceApplicationId) {
    }

    public record ApplicationFacets(List<CommonDtos.IdName> companies, List<String> locations, List<String> sources,
                                    List<CommonDtos.IdEmail> emailAccounts) {
    }
}
