package com.applyflow.mail.matcher;

import java.time.Instant;

/** Snapshot of an existing application used for scoring. */
public record MatchCandidate(
        Long applicationId,
        String companyName,
        String normalizedCompany,
        String companyDomain,
        String jobTitle,
        String normalizedTitle,
        String applicationRef,
        String jobUrl,
        String recruiterEmail,
        Instant lastActivityAt,
        boolean active) {
}
