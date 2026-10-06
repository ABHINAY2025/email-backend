package com.applyflow.mail.classifier;

import com.applyflow.common.EmailClassification;
import com.applyflow.intelligence.ClassificationResult;
import com.applyflow.intelligence.PrefilterDecision;
import com.applyflow.mail.parser.EmailHeaders;
import com.applyflow.mail.parser.ParsedEmail;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic weighted multi-signal classifier for job-application emails.
 * <p>
 * Each category accumulates the weights of matched indicator phrases (subject matches are boosted 1.5x), the sender
 * contributes positive/negative evidence, and non-job categories (alerts, shopping, banking, ...) contribute
 * negative evidence. The final category is chosen by business priority among sufficiently strong categories.
 */
public final class EmailClassifier {

    private static final int MAX_BODY_CHARS = 15000;
    private static final double SUBJECT_BOOST = 1.5;
    private static final double CATEGORY_CAP = 3.0;

    /** Business priority when several categories are strong. */
    private static final List<EmailClassification> PRIORITY = List.of(
            EmailClassification.OFFER,
            EmailClassification.REJECTION,
            EmailClassification.WITHDRAWAL,
            EmailClassification.INTERVIEW_INVITATION,
            EmailClassification.ASSESSMENT,
            EmailClassification.RECRUITER_CONTACT,
            EmailClassification.APPLICATION_UPDATE,
            EmailClassification.APPLICATION_CONFIRMATION,
            EmailClassification.FOLLOW_UP);

    private EmailClassifier() {
    }

    // ------------------------------------------------------------------ pre-filter

    /** Header-only decision used before downloading bodies. Errs on the side of AMBIGUOUS. */
    public static PrefilterDecision prefilter(EmailHeaders h) {
        String subject = normalize(h.subject());
        SenderProfile sender = SenderAnalyzer.analyze(h.senderEmail(), h.senderName(), h.subject());
        double jobSubject = sumMatches(SignalCatalog.CONFIRMATION, subject) + sumMatches(SignalCatalog.UPDATE, subject)
                + sumMatches(SignalCatalog.REJECTION, subject) + sumMatches(SignalCatalog.INTERVIEW_INVITE, subject)
                + sumMatches(SignalCatalog.INTERVIEW_UPDATE, subject) + sumMatches(SignalCatalog.ASSESSMENT, subject)
                + sumMatches(SignalCatalog.OFFER, subject) + sumMatches(SignalCatalog.RECRUITER, subject)
                + sumMatches(SignalCatalog.WITHDRAWAL, subject) + sumMatches(SignalCatalog.JOB_CONTEXT, subject);
        if (sender.positiveScore() >= 0.4 || jobSubject >= 0.9) {
            return sender.kind() == SenderProfile.Kind.JOB_BOARD_ALERT && jobSubject < 0.9
                    ? PrefilterDecision.REJECT : PrefilterDecision.PASS;
        }
        double negSubject = sumMatches(SignalCatalog.JOB_ALERT, subject) + sumMatches(SignalCatalog.SHOPPING, subject)
                + sumMatches(SignalCatalog.BANKING, subject) + sumMatches(SignalCatalog.SOCIAL, subject)
                + sumMatches(SignalCatalog.PROMOTION, subject) + sumMatches(SignalCatalog.TECH_NOTIFICATIONS, subject)
                + sumMatches(SignalCatalog.SECURITY, subject) + sumMatches(SignalCatalog.NEWSLETTER, subject);
        if (jobSubject < 0.1) {
            if (sender.negativeScore() >= 0.8 || negSubject >= 0.9) {
                return PrefilterDecision.REJECT;
            }
            // Bulk mail (List-Unsubscribe) without any job vocabulary in subject or sender is almost never
            // about one of the user's applications.
            if (h.hasListUnsubscribe() && sender.kind() != SenderProfile.Kind.RECRUITING_MAILBOX) {
                return PrefilterDecision.REJECT;
            }
        }
        return PrefilterDecision.AMBIGUOUS;
    }

    // ------------------------------------------------------------------ classification

    public static ClassificationResult classify(ParsedEmail email) {
        String subject = normalize(email.subject());
        String body = normalize(stripBoilerplate(truncate(email.bodyText())));
        SenderProfile sender = SenderAnalyzer.analyze(email.senderEmail(), email.senderName(), email.subject());

        List<String> signals = new ArrayList<>();
        Set<String> reasons = new LinkedHashSet<>();

        if (sender.positiveScore() > 0) {
            signals.add("sender:" + sender.label());
            reasons.add("Sender is " + sender.label());
        }
        if (sender.negativeScore() > 0) {
            signals.add("sender-negative:" + sender.label());
        }

        Map<EmailClassification, Double> scores = new EnumMap<>(EmailClassification.class);
        Map<EmailClassification, List<String>> matched = new EnumMap<>(EmailClassification.class);

        score(EmailClassification.APPLICATION_CONFIRMATION, SignalCatalog.CONFIRMATION, subject, body, scores, matched);
        score(EmailClassification.APPLICATION_UPDATE, SignalCatalog.UPDATE, subject, body, scores, matched);
        score(EmailClassification.REJECTION, SignalCatalog.REJECTION, subject, body, scores, matched);
        score(EmailClassification.ASSESSMENT, SignalCatalog.ASSESSMENT, subject, body, scores, matched);
        score(EmailClassification.OFFER, SignalCatalog.OFFER, subject, body, scores, matched);
        score(EmailClassification.RECRUITER_CONTACT, SignalCatalog.RECRUITER, subject, body, scores, matched);
        score(EmailClassification.WITHDRAWAL, SignalCatalog.WITHDRAWAL, subject, body, scores, matched);
        score(EmailClassification.FOLLOW_UP, SignalCatalog.FOLLOW_UP, subject, body, scores, matched);

        List<String> inviteMatches = new ArrayList<>();
        List<String> updateMatches = new ArrayList<>();
        double invite = categoryScore(SignalCatalog.INTERVIEW_INVITE, subject, body, inviteMatches);
        double interviewUpdate = categoryScore(SignalCatalog.INTERVIEW_UPDATE, subject, body, updateMatches);
        boolean postInterview = anyMatch(SignalCatalog.POST_INTERVIEW, subject + "\n" + body);
        if (postInterview) {
            // "Thank you for interviewing with us" is not a new interview.
            invite = Math.max(0, invite - 0.6);
            signals.add("post-interview phrasing");
        }
        double interview = Math.min(CATEGORY_CAP, invite + interviewUpdate);
        scores.put(EmailClassification.INTERVIEW_INVITATION, interview);
        List<String> interviewMatches = new ArrayList<>(inviteMatches);
        interviewMatches.addAll(updateMatches);
        matched.put(EmailClassification.INTERVIEW_INVITATION, interviewMatches);

        // Assessment platforms are strong evidence of an assessment.
        if (sender.kind() == SenderProfile.Kind.ASSESSMENT_PLATFORM) {
            scores.merge(EmailClassification.ASSESSMENT, 0.6, Double::sum);
        }

        double context = Math.min(0.6, sumMatches(SignalCatalog.JOB_CONTEXT, subject + "\n" + body));

        // "test" / "offer" style words need real job context to count.
        if (context < 0.2 && sender.positiveScore() == 0) {
            scores.computeIfPresent(EmailClassification.ASSESSMENT, (k, v) -> v < 1.5 ? v * 0.4 : v);
            scores.computeIfPresent(EmailClassification.OFFER, (k, v) -> v < 1.5 ? v * 0.4 : v);
            scores.computeIfPresent(EmailClassification.INTERVIEW_INVITATION, (k, v) -> v < 1.0 ? v * 0.6 : v);
        }

        // Conditional future steps inside a confirmation do not make it an interview/assessment.
        double confirmation = scores.getOrDefault(EmailClassification.APPLICATION_CONFIRMATION, 0.0);
        if (confirmation >= 1.0 && anyMatch(SignalCatalog.CONDITIONAL, body)) {
            scores.computeIfPresent(EmailClassification.INTERVIEW_INVITATION, (k, v) -> v * 0.4);
            scores.computeIfPresent(EmailClassification.ASSESSMENT, (k, v) -> v * 0.4);
            scores.computeIfPresent(EmailClassification.APPLICATION_UPDATE, (k, v) -> v * 0.6);
        }

        // Confirmations routinely talk about tracking "the status of your application" or "reviewing your
        // application" in the future; that alone is not an update.
        double update = scores.getOrDefault(EmailClassification.APPLICATION_UPDATE, 0.0);
        if (confirmation >= 1.0 && update <= confirmation + 0.3) {
            scores.put(EmailClassification.APPLICATION_UPDATE, update * 0.4);
        }

        // ---- negative evidence
        double alert = Math.min(CATEGORY_CAP, categoryScore(SignalCatalog.JOB_ALERT, subject, body, null));
        if (sender.kind() == SenderProfile.Kind.JOB_BOARD_ALERT) {
            alert += sender.negativeScore();
        }
        double promo = categoryScore(SignalCatalog.PROMOTION, subject, body, null);
        double negative = 0;
        Map<String, Double> negatives = new java.util.LinkedHashMap<>();
        negatives.put("shopping/order", categoryScore(SignalCatalog.SHOPPING, subject, body, null));
        negatives.put("banking/OTP", categoryScore(SignalCatalog.BANKING, subject, body, null));
        negatives.put("social notification", categoryScore(SignalCatalog.SOCIAL, subject, body, null));
        negatives.put("promotion", promo);
        negatives.put("billing/developer notification",
                categoryScore(SignalCatalog.TECH_NOTIFICATIONS, subject, body, null));
        negatives.put("security alert", categoryScore(SignalCatalog.SECURITY, subject, body, null));
        double newsletter = categoryScore(SignalCatalog.NEWSLETTER, subject, body, null);
        if (email.hasListUnsubscribe()) {
            newsletter += 0.3;
        }
        negatives.put("newsletter/bulk mail", newsletter);
        negatives.put("calendar notification", categoryScore(SignalCatalog.CALENDAR_SPAM, subject, body, null));
        for (Map.Entry<String, Double> e : negatives.entrySet()) {
            double v = Math.min(1.5, e.getValue());
            if (v >= 0.5) {
                signals.add("negative:" + e.getKey() + "=" + round(v));
            }
            negative += v;
        }
        negative += sender.negativeScore();

        // Credit-card / loan / account "applications" reuse job vocabulary ("we regret to inform you...").
        double financial = categoryScore(SignalCatalog.FINANCIAL_APPLICATION, subject, body, null);
        if (financial >= 1.0 && sender.positiveScore() < 0.4
                && !anyMatch(SignalCatalog.EMPLOYMENT_WORDS, subject + "\n" + body)) {
            signals.add("negative:financial application=" + round(financial));
            return ClassificationResult.notJobRelated("Financial product application (credit card / loan), not a job",
                    signals, confidenceForNegative(financial));
        }

        // A clear rejection that merely mentions an offer ("unable to offer you") is still a rejection.
        double rejection = scores.getOrDefault(EmailClassification.REJECTION, 0.0);
        if (rejection >= 1.0 && sumMatches(SignalCatalog.OFFER, subject) == 0) {
            scores.computeIfPresent(EmailClassification.OFFER, (k, v) -> v < 2.0 ? v * 0.3 : v);
        }

        // Marketing "special offer" must never become a job offer.
        if (promo >= 0.8) {
            scores.computeIfPresent(EmailClassification.OFFER, (k, v) -> v * 0.25);
        }

        double maxCategory = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        signals.addAll(scores.entrySet().stream().filter(e -> e.getValue() > 0)
                .map(e -> "score:" + e.getKey() + "=" + round(e.getValue())).toList());

        // ---- job alert digests are never applications unless the subject is clearly about one
        double strongApplicationSubject = sumMatches(SignalCatalog.CONFIRMATION, subject)
                + sumMatches(SignalCatalog.REJECTION, subject) + sumMatches(SignalCatalog.INTERVIEW_INVITE, subject)
                + sumMatches(SignalCatalog.OFFER, subject) + sumMatches(SignalCatalog.ASSESSMENT, subject);
        if (alert >= 1.0 && strongApplicationSubject < 0.9) {
            return ClassificationResult.notJobRelated("Job alert / recommendation digest (not an application)",
                    signals, confidenceForNegative(alert));
        }

        double negativeEffective = negative * (maxCategory >= 1.5 ? 0.5 : 1.0);
        if (maxCategory < 0.5 && sender.positiveScore() < 0.4) {
            return ClassificationResult.notJobRelated(negativeReason(negatives, sender, "No job-application signals"),
                    signals, confidenceForNegative(Math.max(negative, 0.6)));
        }
        if (negativeEffective >= 1.0 && maxCategory < 1.5 && sender.positiveScore() < 0.4) {
            return ClassificationResult.notJobRelated(negativeReason(negatives, sender, "Non-job content"), signals,
                    confidenceForNegative(negativeEffective));
        }
        double net = maxCategory + sender.positiveScore() + context - 0.8 * negativeEffective;
        if (net < 0.8) {
            return ClassificationResult.notJobRelated(negativeReason(negatives, sender, "Insufficient job signals"),
                    signals, confidenceForNegative(1.0 - net));
        }

        // ---- choose category by priority among strong-enough candidates
        double eligibleThreshold = Math.max(0.5, 0.45 * maxCategory);
        EmailClassification chosen = null;
        for (EmailClassification c : PRIORITY) {
            double s = scores.getOrDefault(c, 0.0);
            double min = switch (c) {
                case OFFER -> Math.max(0.9, eligibleThreshold);
                case REJECTION -> Math.max(0.6, eligibleThreshold);
                case INTERVIEW_INVITATION -> Math.max(0.6, eligibleThreshold);
                case APPLICATION_UPDATE -> Math.max(0.6, eligibleThreshold);
                default -> eligibleThreshold;
            };
            if (s >= min) {
                chosen = c;
                break;
            }
        }
        double chosenScore;
        if (chosen == null) {
            chosen = EmailClassification.OTHER_JOB_RELATED;
            chosenScore = Math.min(maxCategory, 0.4);
            reasons.add("General job-related content");
        } else {
            chosenScore = scores.getOrDefault(chosen, 0.0);
            // Reminders/confirmations/reschedules usually also contain invitation vocabulary.
            if (chosen == EmailClassification.INTERVIEW_INVITATION && interviewUpdate >= 1.0
                    && interviewUpdate >= invite * 0.6) {
                chosen = EmailClassification.INTERVIEW_UPDATE;
            }
            List<String> m = matched.getOrDefault(chosen == EmailClassification.INTERVIEW_UPDATE
                    ? EmailClassification.INTERVIEW_INVITATION : chosen, List.of());
            m.stream().limit(3).forEach(reasons::add);
        }
        if (context >= 0.3) {
            reasons.add("job vocabulary present");
        }

        double confidence = 0.35 + 0.22 * Math.min(chosenScore, 2.5) + 0.5 * sender.positiveScore()
                + 0.15 * context - 0.25 * negativeEffective;
        // Ambiguity penalty when a lower-priority category is clearly stronger.
        final EmailClassification picked = chosen == EmailClassification.INTERVIEW_UPDATE
                ? EmailClassification.INTERVIEW_INVITATION : chosen;
        double competitor = scores.entrySet().stream()
                .filter(e -> e.getKey() != picked && e.getKey() != EmailClassification.APPLICATION_CONFIRMATION)
                .mapToDouble(Map.Entry::getValue).max().orElse(0);
        if (competitor > chosenScore + 0.5) {
            confidence -= 0.08;
        }
        confidence = Math.max(0.3, Math.min(0.98, confidence));

        boolean progress = chosen == EmailClassification.APPLICATION_UPDATE
                && anyMatch(SignalCatalog.PROGRESS, subject + "\n" + body);

        return new ClassificationResult(chosen, round(confidence), String.join("; ", reasons), signals, progress);
    }

    // ------------------------------------------------------------------ helpers

    private static void score(EmailClassification c, List<Phrase> phrases, String subject, String body,
                              Map<EmailClassification, Double> scores,
                              Map<EmailClassification, List<String>> matched) {
        List<String> m = new ArrayList<>();
        scores.put(c, categoryScore(phrases, subject, body, m));
        matched.put(c, m);
    }

    /** Sum of phrase weights (subject boosted), capped. Adds human-readable match descriptions to {@code out}. */
    static double categoryScore(List<Phrase> phrases, String subject, String body, List<String> out) {
        double total = 0;
        List<double[]> ordering = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        for (Phrase p : phrases) {
            if (p.matches(subject)) {
                double w = p.weight() * SUBJECT_BOOST;
                total += w;
                ordering.add(new double[]{w, descriptions.size()});
                descriptions.add("subject contains '" + p.text() + "'");
            } else if (p.matches(body)) {
                total += p.weight();
                ordering.add(new double[]{p.weight(), descriptions.size()});
                descriptions.add("body contains '" + p.text() + "'");
            }
        }
        if (out != null) {
            ordering.sort((a, b) -> Double.compare(b[0], a[0]));
            for (double[] o : ordering) {
                out.add(descriptions.get((int) o[1]));
            }
        }
        return Math.min(CATEGORY_CAP, total);
    }

    static double sumMatches(List<Phrase> phrases, String text) {
        double total = 0;
        for (Phrase p : phrases) {
            if (p.matches(text)) {
                total += p.weight();
            }
        }
        return total;
    }

    static boolean anyMatch(List<Phrase> phrases, String text) {
        for (Phrase p : phrases) {
            if (p.matches(text)) {
                return true;
            }
        }
        return false;
    }

    private static String negativeReason(Map<String, Double> negatives, SenderProfile sender, String fallback) {
        List<String> parts = negatives.entrySet().stream().filter(e -> e.getValue() >= 0.5)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(2)
                .map(e -> "looks like " + e.getKey()).collect(Collectors.toCollection(ArrayList::new));
        if (sender.negativeScore() > 0) {
            parts.add(0, "Sender is " + sender.label());
        }
        return parts.isEmpty() ? fallback : String.join("; ", parts);
    }

    private static double confidenceForNegative(double strength) {
        return round(Math.max(0.55, Math.min(0.97, 0.55 + 0.2 * strength)));
    }

    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT)
                .replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .replaceAll("\\s+", " ");
    }

    /** Drops anti-fraud, EEO and confidentiality boilerplate sentences whose wording is pure noise. */
    static String stripBoilerplate(String body) {
        if (body.isEmpty()) {
            return body;
        }
        StringBuilder out = new StringBuilder(body.length());
        for (String sentence : body.split("(?<=[.!?])\\s+|\\n+")) {
            if (!SignalCatalog.BOILERPLATE_SENTENCE.matcher(sentence).find()) {
                out.append(sentence).append('\n');
            }
        }
        return out.toString();
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_BODY_CHARS ? s.substring(0, MAX_BODY_CHARS) : s;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
