package com.applyflow.dto;

import java.time.Instant;
import java.util.List;

public final class CompanyDtos {

    private CompanyDtos() {
    }

    public record CompanySummary(Long id, String name, String domain, long applications, long active, long interviews,
                                 long offers, long rejected, Instant latestActivityAt) {
    }

    public record ContactDto(Long id, String name, String email, String role, Instant lastContactAt,
                             long emailCount) {
    }

    public record ResponseStats(double responseRate, Double avgResponseDays, long totalEmails) {
    }

    /**
     * Flattened CompanyDetail. The contract re-declares {@code applications} on the detail as
     * {@code ApplicationSummary[]}, so here {@code applications} is the list and the numeric count is exposed
     * additionally as {@code applicationCount}.
     */
    public record CompanyDetail(
            Long id, String name, String domain,
            List<ApplicationDtos.ApplicationSummary> applications,
            long applicationCount,
            long active, long interviews, long offers, long rejected, Instant latestActivityAt,
            String website,
            List<DashboardDtos.StatusCount> statusDistribution,
            List<InboxDtos.InboxItem> recentEmails,
            List<ContactDto> contacts,
            List<DashboardDtos.ActivityItem> history,
            ResponseStats responseStats) {
    }
}
