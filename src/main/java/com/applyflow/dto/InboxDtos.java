package com.applyflow.dto;

import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EmailClassification;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class InboxDtos {

    private InboxDtos() {
    }

    public record InboxItem(
            Long id,
            Long emailAccountId,
            String emailAccountEmail,
            String companyName,
            String senderName,
            String senderEmail,
            String subject,
            String snippet,
            String summary,
            Long applicationId,
            String applicationJobTitle,
            EmailClassification classification,
            ApplicationStatus detectedStatus,
            double confidence,
            boolean actionRequired,
            String actionText,
            boolean needsReview,
            @JsonProperty("isRead") boolean isRead,
            Instant receivedAt) {
    }

    public record StatusImpact(ApplicationStatus from, ApplicationStatus to, boolean applied) {
    }

    public record MatchSuggestion(Long applicationId, String companyName, String jobTitle, double score,
                                  String reason) {
    }

    /** Flattened EmailDetail (extends InboxItem in the contract). */
    public record EmailDetail(
            Long id,
            Long emailAccountId,
            String emailAccountEmail,
            String companyName,
            String senderName,
            String senderEmail,
            String subject,
            String snippet,
            String summary,
            Long applicationId,
            String applicationJobTitle,
            EmailClassification classification,
            ApplicationStatus detectedStatus,
            double confidence,
            boolean actionRequired,
            String actionText,
            boolean needsReview,
            @JsonProperty("isRead") boolean isRead,
            Instant receivedAt,
            String recipient,
            String threadId,
            String bodyText,
            String bodyHtml,
            String classificationReason,
            StatusImpact statusImpact,
            List<MatchSuggestion> matchSuggestions) {
    }

    public record InboxCounts(long all, long attention, long applications, long recruiters, long interviews,
                              long assessments, long offers, long rejected, long review, long unread) {
    }

    public record ReadRequest(@NotNull Boolean read) {
    }

    public record MergeEmailRequest(@NotNull Long applicationId) {
    }

    public record ReclassifyRequest(@NotNull EmailClassification classification) {
    }
}
