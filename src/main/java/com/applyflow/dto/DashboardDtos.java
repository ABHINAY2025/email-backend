package com.applyflow.dto;

import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;
import com.applyflow.common.EventType;

import java.time.Instant;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record StatusCount(ApplicationStatus status, long count) {
    }

    public record DashboardSummary(long total, long active, long interviews, long offers, long rejected, long waiting,
                                   List<StatusCount> pipeline, long appliedThisWeek, double responseRate,
                                   Instant lastSyncAt) {
    }

    public record ActivityItem(Long id, Long applicationId, String companyName, String jobTitle, EventType eventType,
                               String title, String description, ApplicationStatus previousStatus,
                               ApplicationStatus newStatus, Actor actor, Instant occurredAt) {
    }

    public record AttentionItem(String id, String kind, Long applicationId, Long emailId, String companyName,
                                String jobTitle, String reason, Instant timestamp, Instant dueAt,
                                String actionLabel) {
    }

    public record CalendarEvent(String id, Long applicationId, String companyName, String jobTitle, String type,
                                String title, Instant start, Instant end, boolean allDay, Long emailId) {
    }
}
