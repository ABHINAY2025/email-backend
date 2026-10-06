package com.applyflow.mail.matcher;

import com.applyflow.mail.classifier.CompanyNames;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure scoring of an email against candidate applications (thread matching is handled before this, since it is
 * definitive).
 */
public final class MatchScorer {

    private MatchScorer() {
    }

    public static List<ScoredMatch> scoreAll(MatchQuery q, List<MatchCandidate> candidates) {
        long activeSameCompany = candidates.stream().filter(c -> c.active() && sameCompany(q, c)).count();
        List<ScoredMatch> out = new ArrayList<>();
        for (MatchCandidate c : candidates) {
            ScoredMatch s = score(q, c, activeSameCompany);
            if (s.score() > 0) {
                out.add(s);
            }
        }
        out.sort(Comparator.comparingDouble(ScoredMatch::score).reversed());
        return out;
    }

    public static ScoredMatch score(MatchQuery q, MatchCandidate c, long activeSameCompany) {
        List<String> reasons = new ArrayList<>();
        double score = 0;
        boolean exact = false;
        boolean exactIdentifier = false; // reference or posting URL, which outrank a title match
        boolean refConflict = false;

        if (q.applicationRef() != null && c.applicationRef() != null) {
            if (q.applicationRef().equalsIgnoreCase(c.applicationRef())) {
                score = Math.max(score, 0.95);
                exact = true;
                exactIdentifier = true;
                reasons.add("same reference " + c.applicationRef());
            } else {
                refConflict = true;
            }
        }
        if (q.jobUrl() != null && c.jobUrl() != null && q.jobUrl().equalsIgnoreCase(c.jobUrl())) {
            score = Math.max(score, 0.9);
            exact = true;
            exactIdentifier = true;
            reasons.add("same job posting link");
        }

        boolean company = sameCompany(q, c);
        if (company) {
            reasons.add("same company");
            if (q.hasTitle() && c.normalizedTitle() != null && !c.normalizedTitle().isBlank()) {
                double sim = titleSimilarity(q.normalizedTitle(), c.normalizedTitle());
                if (sim >= 0.95) {
                    exact = true;
                }
                if (sim >= 0.3) {
                    score = Math.max(score, 0.45 + 0.45 * sim);
                    reasons.add(sim >= 0.99 ? "same job title" : "similar job title (" + Math.round(sim * 100) + "%)");
                } else {
                    score = Math.max(score, 0.3);
                    reasons.add("different job title");
                }
            } else if (!q.hasTitle()) {
                if (activeSameCompany == 1 && c.active()) {
                    score = Math.max(score, 0.7);
                    reasons.add("only active application at this company");
                } else {
                    score = Math.max(score, c.active() ? 0.5 : 0.4);
                    reasons.add(activeSameCompany > 1 ? "one of several applications at this company"
                            : "company match without job title");
                }
            }
            if (q.senderEmail() != null && c.recruiterEmail() != null
                    && q.senderEmail().equalsIgnoreCase(c.recruiterEmail())) {
                score = Math.max(score, 0.8);
                reasons.add("same recruiter");
            }
            if (score >= 0.4 && c.lastActivityAt() != null && q.receivedAt() != null) {
                long days = Math.abs(Duration.between(c.lastActivityAt(), q.receivedAt()).toDays());
                if (days <= 60) {
                    score += 0.1;
                    reasons.add("recent activity (" + days + "d)");
                }
            }
            if (!c.active() && !exact) {
                score -= 0.1; // closed applications rarely receive new, non-threaded mail
            }
        }
        if (refConflict && !exactIdentifier) {
            // Both sides carry a requisition/job id and they differ: a different position, even when the title is
            // identical (companies post the same title under many requisitions).
            exact = false;
            score = Math.min(score, 0.35);
            reasons.add("different reference");
        }
        score = Math.max(0, Math.min(1.0, score));
        return new ScoredMatch(c.applicationId(), c.companyName(), c.jobTitle(), round(score),
                reasons.isEmpty() ? "no overlap" : String.join(", ", reasons), exact);
    }

    static boolean sameCompany(MatchQuery q, MatchCandidate c) {
        if (q.normalizedCompany() != null && !q.normalizedCompany().isBlank()
                && q.normalizedCompany().equals(c.normalizedCompany())) {
            return true;
        }
        if (q.companyDomain() != null && c.companyDomain() != null) {
            String a = CompanyNames.domainLabel(q.companyDomain());
            String b = CompanyNames.domainLabel(c.companyDomain());
            return a != null && a.equals(b);
        }
        return false;
    }

    /** Token Jaccard similarity with a containment bonus; inputs are normalized titles. */
    public static double titleSimilarity(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0;
        }
        String x = a.toLowerCase(Locale.ROOT).trim();
        String y = b.toLowerCase(Locale.ROOT).trim();
        if (x.equals(y)) {
            return 1.0;
        }
        Set<String> ta = tokens(x);
        Set<String> tb = tokens(y);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0;
        }
        if (ta.containsAll(tb) || tb.containsAll(ta)) {
            return Math.max(jaccard(ta, tb), 0.85);
        }
        // Generic words ("engineer", "software") say little about whether two titles are the same role.
        Set<String> sa = new HashSet<>(ta);
        sa.removeAll(GENERIC);
        Set<String> sb = new HashSet<>(tb);
        sb.removeAll(GENERIC);
        double raw = jaccard(ta, tb);
        double specific = sa.isEmpty() && sb.isEmpty() ? raw : jaccard(sa, sb);
        return 0.5 * raw + 0.5 * specific;
    }

    private static final Set<String> GENERIC = Set.of("engineer", "engineering", "developer", "software",
            "manager", "analyst", "specialist", "consultant", "associate", "programmer", "development");

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    private static Set<String> tokens(String s) {
        return Arrays.stream(s.split("\\s+")).filter(t -> !t.isBlank()).collect(Collectors.toSet());
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
