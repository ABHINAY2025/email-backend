package com.applyflow.analytics;

import com.applyflow.common.ApplicationStatus;
import com.applyflow.dto.AnalyticsDtos.AnalyticsOverview;
import com.applyflow.dto.AnalyticsDtos.ApplicationsAnalytics;
import com.applyflow.dto.AnalyticsDtos.BreakdownItem;
import com.applyflow.dto.AnalyticsDtos.Funnel;
import com.applyflow.dto.AnalyticsDtos.ResponseRateAnalytics;
import com.applyflow.dto.AnalyticsDtos.SourceRate;
import com.applyflow.dto.AnalyticsDtos.StatusAnalytics;
import com.applyflow.dto.AnalyticsDtos.TimeSeriesPoint;
import com.applyflow.dto.AnalyticsDtos.WeeklyRate;
import com.applyflow.dto.DashboardDtos.StatusCount;
import com.applyflow.entity.JobApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Analytics computed in Java over the (small, single-user) data set. */
@Service
public class AnalyticsService {

    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final ApplicationFacts factsLoader;
    private final ZoneId zone;
    private final Clock clock;

    public AnalyticsService(ApplicationFacts factsLoader, ZoneId appZone, Clock clock) {
        this.factsLoader = factsLoader;
        this.zone = appZone;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverview overview() {
        List<ApplicationFacts.Fact> facts = factsLoader.load();
        long total = facts.size();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant weekStart = weekStart(today).atStartOfDay(zone).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        long thisWeek = facts.stream().filter(f -> after(f.appliedAt(), weekStart)).count();
        long thisMonth = facts.stream().filter(f -> after(f.appliedAt(), monthStart)).count();
        long responded = facts.stream().filter(ApplicationFacts.Fact::responded).count();
        long interviews = facts.stream().filter(ApplicationFacts.Fact::reachedInterview).count();
        long offers = facts.stream().filter(ApplicationFacts.Fact::reachedOffer).count();
        long rejected = facts.stream().filter(f -> f.status() == ApplicationStatus.REJECTED).count();

        Double avgResponse = avgDays(facts, ApplicationFacts.Fact::firstResponseAt);
        Double avgInterview = avgDays(facts, ApplicationFacts.Fact::firstInterviewAt);
        Double avgRejection = avgDays(facts.stream().filter(f -> f.status() == ApplicationStatus.REJECTED).toList(),
                ApplicationFacts.Fact::rejectedAt);

        return new AnalyticsOverview(total, thisWeek, thisMonth, rate(responded, total), rate(interviews, total),
                rate(offers, total), rate(rejected, total), avgResponse, avgInterview, avgRejection,
                new Funnel(total, responded, interviews, offers));
    }

    @Transactional(readOnly = true)
    public ApplicationsAnalytics applications() {
        List<ApplicationFacts.Fact> facts = factsLoader.load();
        List<JobApplication> apps = facts.stream().map(ApplicationFacts.Fact::app).toList();
        return new ApplicationsAnalytics(weekly(apps), monthly(apps),
                top(apps, a -> orUnknown(a.getSource())),
                top(apps, a -> a.getCompany().getName()),
                top(apps, a -> a.getJobTitle()),
                top(apps, a -> orUnknown(a.getLocation())));
    }

    @Transactional(readOnly = true)
    public StatusAnalytics status() {
        return new StatusAnalytics(distribution(factsLoader.load().stream().map(ApplicationFacts.Fact::app).toList()));
    }

    @Transactional(readOnly = true)
    public ResponseRateAnalytics responseRate() {
        List<ApplicationFacts.Fact> facts = factsLoader.load();
        long responded = facts.stream().filter(ApplicationFacts.Fact::responded).count();

        Map<String, long[]> bySource = new LinkedHashMap<>();
        for (ApplicationFacts.Fact f : facts) {
            long[] c = bySource.computeIfAbsent(orUnknown(f.app().getSource()), k -> new long[2]);
            c[0]++;
            if (f.responded()) {
                c[1]++;
            }
        }
        List<SourceRate> sources = bySource.entrySet().stream()
                .map(e -> new SourceRate(e.getKey(), e.getValue()[0], e.getValue()[1], rate(e.getValue()[1], e.getValue()[0])))
                .sorted(Comparator.comparingLong(SourceRate::total).reversed().thenComparing(SourceRate::key))
                .toList();

        LocalDate start = weekStart(LocalDate.now(clock.withZone(zone))).minusWeeks(11);
        List<WeeklyRate> weekly = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate ws = start.plusWeeks(i);
            Instant from = ws.atStartOfDay(zone).toInstant();
            Instant to = ws.plusWeeks(1).atStartOfDay(zone).toInstant();
            List<ApplicationFacts.Fact> inWeek = facts.stream()
                    .filter(f -> f.appliedAt() != null && !f.appliedAt().isBefore(from) && f.appliedAt().isBefore(to))
                    .toList();
            long r = inWeek.stream().filter(ApplicationFacts.Fact::responded).count();
            weekly.add(new WeeklyRate(ws.toString(), ws.format(WEEK_LABEL), rate(r, inWeek.size()), inWeek.size()));
        }
        return new ResponseRateAnalytics(rate(responded, facts.size()), sources, weekly);
    }

    // ------------------------------------------------------------------ shared helpers

    public List<StatusCount> distribution(List<JobApplication> apps) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s, 0L);
        }
        apps.forEach(a -> counts.merge(a.getStatus(), 1L, Long::sum));
        return Arrays.stream(ApplicationStatus.values()).map(s -> new StatusCount(s, counts.get(s))).toList();
    }

    public double responseRate(List<ApplicationFacts.Fact> facts) {
        return rate(facts.stream().filter(ApplicationFacts.Fact::responded).count(), facts.size());
    }

    public Double avgResponseDays(List<ApplicationFacts.Fact> facts) {
        return avgDays(facts, ApplicationFacts.Fact::firstResponseAt);
    }

    public LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private List<TimeSeriesPoint> weekly(List<JobApplication> apps) {
        LocalDate start = weekStart(LocalDate.now(clock.withZone(zone))).minusWeeks(11);
        long[] counts = new long[12];
        for (JobApplication a : apps) {
            if (a.getAppliedAt() == null) {
                continue;
            }
            LocalDate d = a.getAppliedAt().atZone(zone).toLocalDate();
            long idx = Duration.between(start.atStartOfDay(), weekStart(d).atStartOfDay()).toDays() / 7;
            if (idx >= 0 && idx < 12) {
                counts[(int) idx]++;
            }
        }
        List<TimeSeriesPoint> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate ws = start.plusWeeks(i);
            out.add(new TimeSeriesPoint(ws.toString(), ws.format(WEEK_LABEL), counts[i]));
        }
        return out;
    }

    private List<TimeSeriesPoint> monthly(List<JobApplication> apps) {
        LocalDate start = LocalDate.now(clock.withZone(zone)).withDayOfMonth(1).minusMonths(11);
        long[] counts = new long[12];
        for (JobApplication a : apps) {
            if (a.getAppliedAt() == null) {
                continue;
            }
            LocalDate d = a.getAppliedAt().atZone(zone).toLocalDate().withDayOfMonth(1);
            int idx = (d.getYear() - start.getYear()) * 12 + d.getMonthValue() - start.getMonthValue();
            if (idx >= 0 && idx < 12) {
                counts[idx]++;
            }
        }
        List<TimeSeriesPoint> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate ms = start.plusMonths(i);
            out.add(new TimeSeriesPoint(ms.toString(), ms.format(MONTH_LABEL), counts[i]));
        }
        return out;
    }

    private static List<BreakdownItem> top(List<JobApplication> apps, Function<JobApplication, String> key) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (JobApplication a : apps) {
            String k = key.apply(a);
            if (k == null || k.isBlank()) {
                continue;
            }
            String norm = k.trim().toLowerCase(Locale.ROOT);
            display.putIfAbsent(norm, k.trim());
            counts.merge(norm, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(e -> new BreakdownItem(display.get(e.getKey()), e.getValue()))
                .toList();
    }

    private static Double avgDays(List<ApplicationFacts.Fact> facts, Function<ApplicationFacts.Fact, Instant> event) {
        List<Double> days = facts.stream()
                .filter(f -> f.appliedAt() != null && event.apply(f) != null)
                .map(f -> Duration.between(f.appliedAt(), event.apply(f)).toMinutes() / 1440.0)
                .filter(d -> d >= 0)
                .toList();
        if (days.isEmpty()) {
            return null;
        }
        double avg = days.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.round(avg * 10.0) / 10.0;
    }

    private static boolean after(Instant at, Instant threshold) {
        return at != null && !at.isBefore(threshold);
    }

    static double rate(long part, long total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(((double) part / total) * 1000.0) / 1000.0;
    }

    private static String orUnknown(String s) {
        return s == null || s.isBlank() ? "Unknown" : s;
    }
}
