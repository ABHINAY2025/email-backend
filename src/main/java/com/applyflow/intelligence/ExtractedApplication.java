package com.applyflow.intelligence;

/** Company / position identity extracted from an email. Any field may be null. */
public record ExtractedApplication(String companyName, String companyDomain, String jobTitle, String source) {
}
