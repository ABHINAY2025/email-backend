package com.applyflow.application.status;

import com.applyflow.common.Actor;
import com.applyflow.common.ApplicationStatus;

/**
 * Rules for status transitions.
 * <ul>
 *   <li>SYSTEM (email-driven) changes only move forward in the pipeline
 *       (APPLIED &lt; UNDER_REVIEW &lt; ASSESSMENT &lt; RECRUITER_CONTACT &lt; INTERVIEW &lt; OFFER), except that
 *       REJECTED / WITHDRAWN may be reached from any non-terminal status. SYSTEM never leaves a terminal status and
 *       never sets CLOSED.</li>
 *   <li>USER changes may set any status.</li>
 * </ul>
 */
public final class StatusTransitionPolicy {

    private StatusTransitionPolicy() {
    }

    public static boolean isAllowed(ApplicationStatus from, ApplicationStatus to, Actor actor) {
        if (to == null) {
            return false;
        }
        if (from == to) {
            return false;
        }
        if (actor == Actor.USER) {
            return true;
        }
        if (from == null) {
            return true;
        }
        if (from.isTerminal()) {
            return false;
        }
        if (to == ApplicationStatus.REJECTED || to == ApplicationStatus.WITHDRAWN) {
            return true;
        }
        if (to == ApplicationStatus.CLOSED) {
            return false;
        }
        return to.pipelineOrder() > from.pipelineOrder();
    }
}
