package com.applyflow.common;

public enum EmailClassification {
    APPLICATION_CONFIRMATION,
    APPLICATION_UPDATE,
    RECRUITER_CONTACT,
    ASSESSMENT,
    INTERVIEW_INVITATION,
    INTERVIEW_UPDATE,
    OFFER,
    REJECTION,
    WITHDRAWAL,
    FOLLOW_UP,
    OTHER_JOB_RELATED,
    NOT_JOB_RELATED;

    public boolean isJobRelated() {
        return this != NOT_JOB_RELATED;
    }

    /** Classifications that represent a response from the employer (used for response-rate analytics). */
    public boolean isEmployerResponse() {
        return switch (this) {
            case APPLICATION_UPDATE, RECRUITER_CONTACT, ASSESSMENT, INTERVIEW_INVITATION, INTERVIEW_UPDATE,
                 OFFER, REJECTION -> true;
            default -> false;
        };
    }

    public String label() {
        return switch (this) {
            case APPLICATION_CONFIRMATION -> "Application confirmation";
            case APPLICATION_UPDATE -> "Application update";
            case RECRUITER_CONTACT -> "Recruiter contact";
            case ASSESSMENT -> "Assessment";
            case INTERVIEW_INVITATION -> "Interview invitation";
            case INTERVIEW_UPDATE -> "Interview update";
            case OFFER -> "Offer";
            case REJECTION -> "Rejection";
            case WITHDRAWAL -> "Withdrawal";
            case FOLLOW_UP -> "Follow-up";
            case OTHER_JOB_RELATED -> "Job-related email";
            case NOT_JOB_RELATED -> "Not job related";
        };
    }
}
