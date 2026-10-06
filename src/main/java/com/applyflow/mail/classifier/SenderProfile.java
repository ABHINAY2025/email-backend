package com.applyflow.mail.classifier;

/**
 * What we know about a sender from its address and display name alone.
 *
 * @param kind           sender category
 * @param label          human label, e.g. "Greenhouse ATS"
 * @param positiveScore  0..~0.6 evidence that the sender sends job-application mail
 * @param negativeScore  0..~1.2 evidence that the sender sends non-job mail
 * @param source         application source implied by the sender (e.g. "Greenhouse", "LinkedIn"), nullable
 */
public record SenderProfile(Kind kind, String label, double positiveScore, double negativeScore, String source) {

    public enum Kind {
        ATS,
        ASSESSMENT_PLATFORM,
        JOB_BOARD_APPLICATION,
        JOB_BOARD_ALERT,
        JOB_BOARD_OTHER,
        RECRUITING_MAILBOX,
        NON_JOB_SERVICE,
        PERSONAL_PROVIDER,
        COMPANY
    }

    /** True for domains that must never be used as the company (ATS, job boards, mail providers...). */
    public boolean isIntermediary() {
        return switch (kind) {
            case ATS, ASSESSMENT_PLATFORM, JOB_BOARD_APPLICATION, JOB_BOARD_ALERT, JOB_BOARD_OTHER,
                 PERSONAL_PROVIDER -> true;
            default -> false;
        };
    }
}
