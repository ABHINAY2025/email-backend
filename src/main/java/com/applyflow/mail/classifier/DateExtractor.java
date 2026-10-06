package com.applyflow.mail.classifier;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort extraction of an interview/assessment date or deadline from email text. Returns null when unsure.
 */
public final class DateExtractor {

    public record ScheduledDate(Instant at, boolean deadline, boolean hasTime) {
    }

    private static final String MONTHS = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?";
    private static final Pattern MONTH_FIRST = Pattern.compile(
            "(?i)\\b(?:(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*,?\\s+)?" + MONTHS + "\\s+(\\d{1,2})(?:st|nd|rd|th)?"
                    + "(?:,?\\s+(\\d{4}))?\\b");
    private static final Pattern DAY_FIRST = Pattern.compile(
            "(?i)\\b(?:(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*,?\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?"
                    + MONTHS + ",?(?:\\s+(\\d{4}))?\\b");
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern NUMERIC = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
    private static final Pattern RELATIVE = Pattern.compile(
            "(?i)\\b(?:within|in the next|in)\\s+(\\d{1,3}|one|two|three|four|five|six|seven|ten|fourteen)\\s+"
                    + "(business days?|working days?|days?|hours?|weeks?)\\b");
    private static final Pattern TOMORROW = Pattern.compile("(?i)\\btomorrow\\b");
    private static final Pattern TIME = Pattern.compile(
            "(?i)^[\\s,]*(?:at|@|from|,|-)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)"
                    + "|^[\\s,]*(?:at|@|from)?\\s*(\\d{1,2}):(\\d{2})(?!\\s*(?:am|pm))");
    private static final Pattern ZONE = Pattern.compile(
            "(?i)^\\s*\\(?\\b(ist|pst|pdt|pt|est|edt|et|cst|cdt|ct|mst|mdt|gmt|utc|bst|cet|cest|sgt|aest|aedt)\\b");
    private static final Pattern DEADLINE_CONTEXT = Pattern.compile(
            "(?i)\\b(by|before|expires?|expiring|expiry|deadline|due|no later than|within|complete|submit|valid "
                    + "until|until|closes?)\\b[^.\\n]{0,40}$");

    private static final Map<String, Integer> WORD_NUMBERS = Map.of("one", 1, "two", 2, "three", 3, "four", 4,
            "five", 5, "six", 6, "seven", 7, "ten", 10, "fourteen", 14);

    private static final Map<String, String> ZONES = Map.ofEntries(
            Map.entry("ist", "Asia/Kolkata"), Map.entry("pst", "America/Los_Angeles"),
            Map.entry("pdt", "America/Los_Angeles"), Map.entry("pt", "America/Los_Angeles"),
            Map.entry("est", "America/New_York"), Map.entry("edt", "America/New_York"),
            Map.entry("et", "America/New_York"), Map.entry("cst", "America/Chicago"),
            Map.entry("cdt", "America/Chicago"), Map.entry("ct", "America/Chicago"),
            Map.entry("mst", "America/Denver"), Map.entry("mdt", "America/Denver"), Map.entry("gmt", "UTC"),
            Map.entry("utc", "UTC"), Map.entry("bst", "Europe/London"), Map.entry("cet", "Europe/Berlin"),
            Map.entry("cest", "Europe/Berlin"), Map.entry("sgt", "Asia/Singapore"),
            Map.entry("aest", "Australia/Sydney"), Map.entry("aedt", "Australia/Sydney"));

    private DateExtractor() {
    }

    private record Candidate(int position, LocalDate date, LocalTime time, ZoneId zone, boolean deadline) {
    }

    public static ScheduledDate extract(String subject, String body, Instant receivedAt, ZoneId defaultZone) {
        if (receivedAt == null) {
            return null;
        }
        String text = ((subject == null ? "" : subject) + "\n" + (body == null ? "" : body))
                .replace('–', '-').replace(' ', ' ');
        if (text.length() > 8000) {
            text = text.substring(0, 8000);
        }
        LocalDate receivedDate = receivedAt.atZone(defaultZone).toLocalDate();
        List<Candidate> candidates = new ArrayList<>();

        Matcher m = MONTH_FIRST.matcher(text);
        while (m.find()) {
            LocalDate d = buildDate(m.group(3), month(m.group(1)), m.group(2), receivedDate);
            addCandidate(candidates, text, m.start(), m.end(), d, defaultZone);
        }
        m = DAY_FIRST.matcher(text);
        while (m.find()) {
            LocalDate d = buildDate(m.group(3), month(m.group(2)), m.group(1), receivedDate);
            addCandidate(candidates, text, m.start(), m.end(), d, defaultZone);
        }
        m = ISO.matcher(text);
        while (m.find()) {
            LocalDate d = safeDate(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
            addCandidate(candidates, text, m.start(), m.end(), d, defaultZone);
        }
        m = NUMERIC.matcher(text);
        while (m.find()) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            int y = Integer.parseInt(m.group(3));
            // Ambiguous: prefer MM/DD unless the first number cannot be a month.
            LocalDate d = a > 12 ? safeDate(y, b, a) : safeDate(y, a, b);
            addCandidate(candidates, text, m.start(), m.end(), d, defaultZone);
        }

        candidates.removeIf(c -> c.date() == null || c.date().isBefore(receivedDate)
                || c.date().isAfter(receivedDate.plusDays(366)));
        if (!candidates.isEmpty()) {
            Candidate best = candidates.stream()
                    .sorted(Comparator.comparingInt(Candidate::position))
                    .findFirst().orElseThrow();
            LocalTime time = best.time() != null ? best.time()
                    : best.deadline() ? LocalTime.of(23, 59) : LocalTime.of(9, 0);
            ZoneId zone = best.zone() != null ? best.zone() : defaultZone;
            Instant at = ZonedDateTime.of(best.date(), time, zone).toInstant();
            return new ScheduledDate(at, best.deadline(), best.time() != null);
        }

        Matcher rel = RELATIVE.matcher(text);
        if (rel.find()) {
            String n = rel.group(1).toLowerCase(Locale.ROOT);
            int amount = WORD_NUMBERS.getOrDefault(n, -1);
            if (amount < 0) {
                amount = Integer.parseInt(n);
            }
            String unit = rel.group(2).toLowerCase(Locale.ROOT);
            ZonedDateTime base = receivedAt.atZone(defaultZone);
            ZonedDateTime due;
            if (unit.startsWith("hour")) {
                due = base.plusHours(amount);
            } else if (unit.startsWith("week")) {
                due = base.plusWeeks(amount).with(LocalTime.of(23, 59));
            } else if (unit.startsWith("business") || unit.startsWith("working")) {
                due = plusBusinessDays(base, amount).with(LocalTime.of(23, 59));
            } else {
                due = base.plusDays(amount).with(LocalTime.of(23, 59));
            }
            if (amount > 0 && amount <= 60) {
                return new ScheduledDate(due.toInstant(), true, unit.startsWith("hour"));
            }
        }
        Matcher tm = TOMORROW.matcher(text);
        if (tm.find()) {
            ZonedDateTime base = receivedAt.atZone(defaultZone).plusDays(1);
            LocalTime time = parseTime(text.substring(tm.end(), Math.min(text.length(), tm.end() + 30)));
            boolean deadline = isDeadlineContext(text, tm.start());
            LocalTime t = time != null ? time : deadline ? LocalTime.of(23, 59) : LocalTime.of(9, 0);
            return new ScheduledDate(base.with(t).toInstant(), deadline, time != null);
        }
        return null;
    }

    private static void addCandidate(List<Candidate> out, String text, int start, int end, LocalDate date,
                                     ZoneId defaultZone) {
        if (date == null) {
            return;
        }
        String after = text.substring(end, Math.min(text.length(), end + 40));
        LocalTime time = parseTime(after);
        ZoneId zone = null;
        if (time != null) {
            Matcher tm = TIME.matcher(after);
            if (tm.find()) {
                Matcher zm = ZONE.matcher(after.substring(tm.end()));
                if (zm.find()) {
                    zone = ZoneId.of(ZONES.get(zm.group(1).toLowerCase(Locale.ROOT)));
                }
            }
        }
        out.add(new Candidate(start, date, time, zone, isDeadlineContext(text, start)));
    }

    private static boolean isDeadlineContext(String text, int start) {
        String before = text.substring(Math.max(0, start - 60), start);
        return DEADLINE_CONTEXT.matcher(before).find();
    }

    static LocalTime parseTime(String after) {
        Matcher tm = TIME.matcher(after);
        if (!tm.find()) {
            return null;
        }
        try {
            if (tm.group(1) != null) {
                int h = Integer.parseInt(tm.group(1));
                int min = tm.group(2) == null ? 0 : Integer.parseInt(tm.group(2));
                String ampm = tm.group(3).toLowerCase(Locale.ROOT).replace(".", "");
                if (h < 1 || h > 12 || min > 59) {
                    return null;
                }
                if (ampm.equals("pm") && h != 12) {
                    h += 12;
                }
                if (ampm.equals("am") && h == 12) {
                    h = 0;
                }
                return LocalTime.of(h, min);
            }
            int h = Integer.parseInt(tm.group(4));
            int min = Integer.parseInt(tm.group(5));
            if (h > 23 || min > 59) {
                return null;
            }
            return LocalTime.of(h, min);
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private static int month(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        String[] names = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < names.length; i++) {
            if (t.startsWith(names[i])) {
                return i + 1;
            }
        }
        return -1;
    }

    private static LocalDate buildDate(String yearStr, int month, String dayStr, LocalDate received) {
        if (month < 1) {
            return null;
        }
        int day;
        try {
            day = Integer.parseInt(dayStr);
        } catch (NumberFormatException e) {
            return null;
        }
        if (yearStr != null) {
            return safeDate(Integer.parseInt(yearStr), month, day);
        }
        LocalDate d = safeDate(received.getYear(), month, day);
        if (d != null && d.isBefore(received.minusDays(1))) {
            d = safeDate(received.getYear() + 1, month, day);
        }
        return d;
    }

    private static LocalDate safeDate(int y, int m, int d) {
        try {
            return LocalDate.of(y, m, d);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static ZonedDateTime plusBusinessDays(ZonedDateTime base, int days) {
        ZonedDateTime d = base;
        int added = 0;
        while (added < days) {
            d = d.plusDays(1);
            if (d.getDayOfWeek().getValue() <= 5) {
                added++;
            }
        }
        return d;
    }
}
