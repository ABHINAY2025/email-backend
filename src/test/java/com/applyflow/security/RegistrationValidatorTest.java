package com.applyflow.security;

import com.applyflow.dto.AuthDtos.RegisterRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationValidatorTest {

    @Test
    void acceptsValidRequest() {
        assertThat(RegistrationValidator.validate(new RegisterRequest("  Jane  ", "Jane.Doe@Example.com", "s3cret-pw")))
                .isEmpty();
    }

    @Test
    void rejectsBlankOrTooLongDisplayName() {
        assertThat(RegistrationValidator.validate(new RegisterRequest("   ", "a@b.co", "password1")))
                .containsKey("displayName");
        assertThat(RegistrationValidator.validate(new RegisterRequest("x".repeat(81), "a@b.co", "password1")))
                .containsKey("displayName");
        assertThat(RegistrationValidator.validate(new RegisterRequest(" " + "x".repeat(80) + " ", "a@b.co",
                "password1"))).isEmpty();
    }

    @Test
    void rejectsInvalidEmails() {
        for (String bad : new String[]{null, "", "plain", "a@b", "a b@c.com", "a@@b.com", "a@b..com", "@b.com"}) {
            assertThat(RegistrationValidator.validate(new RegisterRequest("Jane", bad, "password1")))
                    .as(String.valueOf(bad)).containsKey("email");
        }
        String tooLong = "a".repeat(250) + "@b.com";
        assertThat(RegistrationValidator.validate(new RegisterRequest("Jane", tooLong, "password1")))
                .containsEntry("email", "must be at most 254 characters");
    }

    @Test
    void enforcesPasswordLength() {
        assertThat(RegistrationValidator.validate(new RegisterRequest("Jane", "a@b.co", "short")))
                .containsEntry("password", "must be at least 8 characters");
        assertThat(RegistrationValidator.validate(new RegisterRequest("Jane", "a@b.co", null)))
                .containsKey("password");
        assertThat(RegistrationValidator.validate(new RegisterRequest("Jane", "a@b.co", "p".repeat(129))))
                .containsEntry("password", "must be at most 128 characters");
        assertThat(RegistrationValidator.validate(new RegisterRequest("Jane", "a@b.co", "p".repeat(128)))).isEmpty();
    }

    @Test
    void reportsAllInvalidFields() {
        Map<String, String> errors = RegistrationValidator.validate(new RegisterRequest("", "nope", "1"));
        assertThat(errors).containsOnlyKeys("displayName", "email", "password");
    }

    @Test
    void normalizesEmailToLowerCase() {
        assertThat(RegistrationValidator.normalizeEmail("  Jane.Doe@Example.COM ")).isEqualTo("jane.doe@example.com");
        assertThat(RegistrationValidator.normalizeEmail(null)).isNull();
    }
}
