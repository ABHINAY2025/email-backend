package com.applyflow.intelligence;

import com.applyflow.common.EmailClassification;
import com.applyflow.common.ScheduledType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Template-based summaries and action texts (deterministic; replaceable by an AI implementation). */
public final class SummaryGenerator {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a",
            Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private SummaryGenerator() {
    }

    public static EmailSummary summarize(ClassificationResult cr, ExtractedJobDetails d, String senderName,
                                         ZoneId zone) {
        String company = d.companyName();
        String title = d.jobTitle();
        String forTitle = title == null ? "" : " for " + title;
        String atCompany = company == null ? "" : " at " + company;
        String who = company != null ? company : "The employer";
        ScheduledType st = d.scheduledTypeFor(cr.classification());
        String when = formatWhen(d.scheduledAt(), zone, d.scheduledIsDeadline());
        String shortWhen = d.scheduledAt() == null ? null : d.scheduledAt().atZone(zone).format(SHORT);

        return switch (cr.classification()) {
            case APPLICATION_CONFIRMATION -> new EmailSummary(
                    company != null ? company + " confirmed your application" + forTitle + "."
                            : "Your application" + forTitle + " was received.",
                    false, "No action required.");
            case APPLICATION_UPDATE -> new EmailSummary(
                    cr.progressIndicated()
                            ? who + " is reviewing your application" + forTitle + "."
                            : who + " sent an update on your application" + forTitle + ".",
                    false, "No action required.");
            case RECRUITER_CONTACT -> new EmailSummary(
                    "A recruiter" + (senderName != null && !senderName.isBlank() && company != null
                            && !senderName.equalsIgnoreCase(company) ? " (" + senderName + ")" : "")
                            + (company != null ? " from " + company : "") + " reached out about "
                            + (title != null ? "the " + title + " role" : "a role") + "."
                            + (when != null ? " Proposed time: " + when + "." : ""),
                    true, "Respond to the recruiter");
            case ASSESSMENT -> new EmailSummary(
                    who + " sent you an assessment" + forTitle
                            + (when != null ? (st == ScheduledType.DEADLINE ? ", due " + when : " scheduled " + when)
                            : "") + ".",
                    true, shortWhen != null && st == ScheduledType.DEADLINE
                            ? "Complete the assessment by " + shortWhen : "Complete the assessment");
            case INTERVIEW_INVITATION -> new EmailSummary(
                    who + " invited you to an interview" + forTitle + (when != null && st == ScheduledType.INTERVIEW
                            ? " on " + when : "") + ".",
                    true, when != null && st == ScheduledType.INTERVIEW ? "Confirm and prepare for the interview on "
                            + shortWhen : "Reply to schedule the interview");
            case INTERVIEW_UPDATE -> new EmailSummary(
                    who + " updated your interview" + forTitle + (when != null ? " — now on " + when : "") + ".",
                    true, "Review the interview details");
            case OFFER -> new EmailSummary(
                    who + " extended you an offer" + forTitle + ".", true, "Review the offer");
            case REJECTION -> new EmailSummary(
                    who + " decided not to move forward with your application" + forTitle + ".",
                    false, "No action required.");
            case WITHDRAWAL -> new EmailSummary(
                    "Your application" + forTitle + atCompany + " was withdrawn.", false, "No action required.");
            case FOLLOW_UP -> new EmailSummary(
                    who + " followed up" + (title != null ? " regarding " + title : "") + ".",
                    true, "Reply to the follow-up");
            case OTHER_JOB_RELATED -> new EmailSummary(
                    "Job-related email" + (company != null ? " from " + company : "") + forTitle + ".",
                    false, "No action required.");
            case NOT_JOB_RELATED -> new EmailSummary(null, false, null);
        };
    }

    static String formatWhen(Instant at, ZoneId zone, boolean deadline) {
        if (at == null) {
            return null;
        }
        ZonedDateTime z = at.atZone(zone);
        boolean endOfDay = z.getHour() == 23 && z.getMinute() == 59;
        boolean nineAm = z.getHour() == 9 && z.getMinute() == 0 && deadline;
        return endOfDay || nineAm ? z.format(DATE) : z.format(DATE_TIME);
    }

    /** Short title for timeline events. */
    public static String eventTitle(EmailClassification c, String company) {
        String co = company == null ? "" : " from " + company;
        return switch (c) {
            case APPLICATION_CONFIRMATION -> "Application confirmed" + (company == null ? "" : " by " + company);
            case APPLICATION_UPDATE -> "Application update" + co;
            case RECRUITER_CONTACT -> "Recruiter email received" + co;
            case ASSESSMENT -> "Assessment received" + co;
            case INTERVIEW_INVITATION -> "Interview invitation" + co;
            case INTERVIEW_UPDATE -> "Interview update" + co;
            case OFFER -> "Offer received" + co;
            case REJECTION -> "Rejection" + co;
            case WITHDRAWAL -> "Application withdrawn";
            case FOLLOW_UP -> "Follow-up" + co;
            case OTHER_JOB_RELATED, NOT_JOB_RELATED -> "Email received" + co;
        };
    }
}
