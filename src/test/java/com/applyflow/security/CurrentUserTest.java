package com.applyflow.security;

import com.applyflow.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void failsClosedWithoutAUser() {
        assertThat(CurrentUser.idOrNull()).isNull();
        assertThatThrownBy(CurrentUser::id).isInstanceOf(ApiException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void resolvesTheAuthenticatedPrincipal() {
        AppUserPrincipal p = new AppUserPrincipal(42L, "jane@example.com", "hash");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        assertThat(CurrentUser.id()).isEqualTo(42L);
    }

    @Test
    void ignoresPrincipalsWithoutAnId() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("someone", null, java.util.List.of()));
        assertThat(CurrentUser.idOrNull()).isNull();
    }

    @Test
    void runAsScopesBackgroundWorkAndRestoresTheOuterScope() {
        CurrentUser.runAs(7L, () -> {
            assertThat(CurrentUser.id()).isEqualTo(7L);
            Long inner = CurrentUser.callAs(9L, CurrentUser::id);
            assertThat(inner).isEqualTo(9L);
            assertThat(CurrentUser.id()).isEqualTo(7L);
        });
        assertThat(CurrentUser.idOrNull()).isNull();
    }

    @Test
    void runAsWinsOverTheRequestUser() {
        AppUserPrincipal p = new AppUserPrincipal(1L, "a@example.com", "hash");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        assertThat(CurrentUser.callAs(2L, CurrentUser::id)).isEqualTo(2L);
        assertThat(CurrentUser.id()).isEqualTo(1L);
    }

    @Test
    void runAsRequiresAnOwner() {
        assertThatThrownBy(() -> CurrentUser.runAs(null, () -> { })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scopeIsRestoredWhenWorkThrows() {
        assertThatThrownBy(() -> CurrentUser.runAs(5L, () -> {
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(CurrentUser.idOrNull()).isNull();
    }
}
