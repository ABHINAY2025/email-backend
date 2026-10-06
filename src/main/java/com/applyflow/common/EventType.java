package com.applyflow.common;

public enum EventType {
    APPLICATION_SUBMITTED,
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
    STATUS_CHANGE,
    NOTE_ADDED,
    EMAIL_RECEIVED;

    public static EventType fromClassification(EmailClassification c) {
        return switch (c) {
            case APPLICATION_CONFIRMATION -> APPLICATION_CONFIRMATION;
            case APPLICATION_UPDATE -> APPLICATION_UPDATE;
            case RECRUITER_CONTACT -> RECRUITER_CONTACT;
            case ASSESSMENT -> ASSESSMENT;
            case INTERVIEW_INVITATION -> INTERVIEW_INVITATION;
            case INTERVIEW_UPDATE -> INTERVIEW_UPDATE;
            case OFFER -> OFFER;
            case REJECTION -> REJECTION;
            case WITHDRAWAL -> WITHDRAWAL;
            case FOLLOW_UP -> FOLLOW_UP;
            case OTHER_JOB_RELATED, NOT_JOB_RELATED -> EMAIL_RECEIVED;
        };
    }
}
