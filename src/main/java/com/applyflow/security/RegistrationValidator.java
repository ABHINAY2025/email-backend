package com.applyflow.security;

import com.applyflow.dto.AuthDtos.RegisterRequest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Validation and normalisation rules for self-service registration (API contract §16). */
public final class RegistrationValidator {

    public static final int DISPLAY_NAME_MAX = 80;
    public static final int EMAIL_MAX = 254;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 128;

    /** Pragmatic address check: one "@", no whitespace, a dot in the domain, no empty labels. */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");

    private RegistrationValidator() {
    }

    /** Returns field → message for every invalid field (empty when valid). */
    public static Map<String, String> validate(RegisterRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (req == null) {
            errors.put("email", "is required");
            return errors;
        }
        String name = req.displayName() == null ? "" : req.displayName().trim();
        if (name.isEmpty()) {
            errors.put("displayName", "must not be blank");
        } else if (name.length() > DISPLAY_NAME_MAX) {
            errors.put("displayName", "must be at most " + DISPLAY_NAME_MAX + " characters");
        }
        String email = normalizeEmail(req.email());
        if (email == null || email.isEmpty()) {
            errors.put("email", "must not be blank");
        } else if (email.length() > EMAIL_MAX) {
            errors.put("email", "must be at most " + EMAIL_MAX + " characters");
        } else if (!EMAIL.matcher(email).matches() || email.contains("..")) {
            errors.put("email", "must be a valid email address");
        }
        String password = req.password();
        if (password == null || password.length() < PASSWORD_MIN) {
            errors.put("password", "must be at least " + PASSWORD_MIN + " characters");
        } else if (password.length() > PASSWORD_MAX) {
            errors.put("password", "must be at most " + PASSWORD_MAX + " characters");
        }
        return errors;
    }

    /** Trimmed and lower-cased; null stays null. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
