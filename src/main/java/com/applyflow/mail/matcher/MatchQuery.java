package com.applyflow.mail.matcher;

import java.time.Instant;

/** Identity facts extracted from an email that are compared with candidate applications. */
public record MatchQuery(
        String normalizedCompany,
        String companyDomain,
        String normalizedTitle,
        String applicationRef,
        String jobUrl,
        String senderEmail,
        Instant receivedAt) {

    public boolean hasTitle() {
        return normalizedTitle != null && !normalizedTitle.isBlank();
    }
}
