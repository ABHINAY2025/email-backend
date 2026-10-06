package com.applyflow.common;

import java.util.EnumSet;
import java.util.Set;

/** Application lifecycle status. */
public enum ApplicationStatus {
    APPLIED(0, "Applied"),
    UNDER_REVIEW(1, "Under Review"),
    ASSESSMENT(2, "Assessment"),
    RECRUITER_CONTACT(3, "Recruiter Contact"),
    INTERVIEW(4, "Interview"),
    OFFER(5, "Offer"),
    REJECTED(-1, "Rejected"),
    WITHDRAWN(-1, "Withdrawn"),
    CLOSED(-1, "Closed");

    private final int pipelineOrder;
    private final String label;

    ApplicationStatus(int pipelineOrder, String label) {
        this.pipelineOrder = pipelineOrder;
        this.label = label;
    }

    /** Position in the forward pipeline; -1 for terminal statuses. */
    public int pipelineOrder() {
        return pipelineOrder;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return this == REJECTED || this == WITHDRAWN || this == CLOSED;
    }

    public boolean isActive() {
        return !isTerminal();
    }

    public boolean isWaiting() {
        return this == APPLIED || this == UNDER_REVIEW;
    }

    public static Set<ApplicationStatus> terminal() {
        return EnumSet.of(REJECTED, WITHDRAWN, CLOSED);
    }

    public static Set<ApplicationStatus> active() {
        return EnumSet.complementOf(EnumSet.of(REJECTED, WITHDRAWN, CLOSED));
    }
}
