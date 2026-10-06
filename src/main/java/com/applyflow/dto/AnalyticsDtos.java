package com.applyflow.dto;

import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Funnel(long applied, long responses, long interviews, long offers) {
    }

    public record AnalyticsOverview(long totalApplications, long applicationsThisWeek, long applicationsThisMonth,
                                    double responseRate, double interviewRate, double offerRate,
                                    double rejectionRate, Double avgResponseDays, Double avgDaysToInterview,
                                    Double avgDaysToRejection, Funnel funnel) {
    }

    public record TimeSeriesPoint(String periodStart, String label, long count) {
    }

    public record BreakdownItem(String key, long count) {
    }

    public record ApplicationsAnalytics(List<TimeSeriesPoint> weekly, List<TimeSeriesPoint> monthly,
                                        List<BreakdownItem> bySource, List<BreakdownItem> byCompany,
                                        List<BreakdownItem> byJobTitle, List<BreakdownItem> byLocation) {
    }

    public record StatusAnalytics(List<DashboardDtos.StatusCount> distribution) {
    }

    public record SourceRate(String key, long total, long responded, double rate) {
    }

    public record WeeklyRate(String periodStart, String label, double rate, long total) {
    }

    public record ResponseRateAnalytics(double overall, List<SourceRate> bySource, List<WeeklyRate> weekly) {
    }
}
