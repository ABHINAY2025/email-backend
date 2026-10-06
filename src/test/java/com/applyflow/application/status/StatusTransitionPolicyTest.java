package com.applyflow.application.status;

import org.junit.jupiter.api.Test;

import static com.applyflow.common.Actor.SYSTEM;
import static com.applyflow.common.Actor.USER;
import static com.applyflow.common.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionPolicyTest {

    @Test
    void systemMovesOnlyForward() {
        assertThat(StatusTransitionPolicy.isAllowed(APPLIED, UNDER_REVIEW, SYSTEM)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(APPLIED, INTERVIEW, SYSTEM)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(ASSESSMENT, RECRUITER_CONTACT, SYSTEM)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(INTERVIEW, OFFER, SYSTEM)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(INTERVIEW, UNDER_REVIEW, SYSTEM)).isFalse();
        assertThat(StatusTransitionPolicy.isAllowed(OFFER, APPLIED, SYSTEM)).isFalse();
        assertThat(StatusTransitionPolicy.isAllowed(INTERVIEW, INTERVIEW, SYSTEM)).isFalse();
    }

    @Test
    void systemCanTerminateFromAnyActiveStatus() {
        assertThat(StatusTransitionPolicy.isAllowed(APPLIED, REJECTED, SYSTEM)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(OFFER, REJECTED, SYSTEM)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(INTERVIEW, WITHDRAWN, SYSTEM)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(INTERVIEW, CLOSED, SYSTEM)).isFalse();
    }

    @Test
    void systemNeverLeavesTerminalStatus() {
        assertThat(StatusTransitionPolicy.isAllowed(REJECTED, INTERVIEW, SYSTEM)).isFalse();
        assertThat(StatusTransitionPolicy.isAllowed(WITHDRAWN, OFFER, SYSTEM)).isFalse();
        assertThat(StatusTransitionPolicy.isAllowed(REJECTED, WITHDRAWN, SYSTEM)).isFalse();
    }

    @Test
    void userCanSetAnyDifferentStatus() {
        assertThat(StatusTransitionPolicy.isAllowed(REJECTED, INTERVIEW, USER)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(OFFER, APPLIED, USER)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(APPLIED, CLOSED, USER)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(APPLIED, APPLIED, USER)).isFalse();
    }

    @Test
    void initialStatusAlwaysAllowed() {
        assertThat(StatusTransitionPolicy.isAllowed(null, APPLIED, SYSTEM)).isTrue();
    }
}
