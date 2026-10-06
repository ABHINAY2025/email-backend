package com.applyflow.intelligence;

import com.applyflow.common.ScheduledType;

import java.math.BigDecimal;
import java.time.Instant;

/** Everything the rule engine could extract from an email. All fields nullable. */
public record ExtractedJobDetails(
        String companyName,
        String companyDomain,
        String jobTitle,
        String source,
        String location,
        String jobUrl,
        String applicationRef,
        String employmentType,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        String recruiterName,
        String recruiterEmail,
        Instant scheduledAt,
        boolean scheduledIsDeadline) {

    public ExtractedApplication identity() {
        return new ExtractedApplication(companyName, companyDomain, jobTitle, source);
    }

    public ScheduledType scheduledTypeFor(com.applyflow.common.EmailClassification c) {
        if (scheduledAt == null) {
            return null;
        }
        if (scheduledIsDeadline) {
            return ScheduledType.DEADLINE;
        }
        return switch (c) {
            case INTERVIEW_INVITATION, INTERVIEW_UPDATE -> ScheduledType.INTERVIEW;
            case ASSESSMENT -> ScheduledType.ASSESSMENT;
            case RECRUITER_CONTACT -> ScheduledType.RECRUITER_CALL;
            default -> ScheduledType.DEADLINE;
        };
    }

    public ExtractedJobDetails withCompany(String name, String domain) {
        return new ExtractedJobDetails(name, domain, jobTitle, source, location, jobUrl, applicationRef,
                employmentType, salaryMin, salaryMax, salaryCurrency, recruiterName, recruiterEmail, scheduledAt,
                scheduledIsDeadline);
    }

    public static ExtractedJobDetails empty() {
        return new ExtractedJobDetails(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, false);
    }
}
