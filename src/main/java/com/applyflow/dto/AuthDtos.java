package com.applyflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** {@code username} accepts the username or the account email (case-insensitive). */
    public record LoginRequest(@NotBlank @Size(max = 254) String username,
                               @NotBlank @Size(max = 200) String password) {
        @Override
        public String toString() {
            return "LoginRequest{username=" + username + "}";
        }
    }

    /** Validated by {@code RegistrationValidator} (after the "registration enabled" check). */
    public record RegisterRequest(String displayName, String email, String password) {
        @Override
        public String toString() {
            return "RegisterRequest{email=" + email + "}"; // never the password
        }
    }

    /** {@code email} is null only for the env-bootstrapped admin. */
    public record CurrentUser(String username, String displayName, String email) {
    }

    public record AuthConfig(boolean registrationEnabled) {
    }

    public record OnboardingStatus(boolean hasEmailAccount, boolean firstSyncCompleted, boolean syncInProgress,
                                   boolean hasApplications, boolean dismissed, boolean completed) {
    }
}
