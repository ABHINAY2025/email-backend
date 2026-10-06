package com.applyflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String username,
                               @NotBlank @Size(max = 200) String password) {
        @Override
        public String toString() {
            return "LoginRequest{username=" + username + "}";
        }
    }

    public record CurrentUser(String username, String displayName) {
    }
}
