package com.applyflow.security;

import com.applyflow.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.function.Supplier;

/**
 * Resolves the id of the user whose data the current code works on.
 * <ul>
 *   <li>Background work (mail sync, scheduler, seeding) has no request user: it runs inside
 *       {@link #runAs(Long, Runnable)} / {@link #callAs(Long, Supplier)} with the owner of the mailbox/data.</li>
 *   <li>Requests resolve the authenticated {@link AppUserPrincipal} from the SecurityContext.</li>
 * </ul>
 * There is deliberately no fallback: without a user, {@link #id()} fails (401) instead of touching other users' data.
 */
public final class CurrentUser {

    private static final ThreadLocal<Long> SCOPED = new ThreadLocal<>();

    private CurrentUser() {
    }

    /** The current user's id; throws 401 when there is none. */
    public static Long id() {
        Long id = idOrNull();
        if (id == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required. Please sign in.");
        }
        return id;
    }

    /** The current user's id, or null outside any user scope. */
    public static Long idOrNull() {
        Long scoped = SCOPED.get();
        if (scoped != null) {
            return scoped;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return p.getId();
        }
        return null;
    }

    /** Runs {@code work} on behalf of {@code userId} (background work: sync, scheduler, seeding). */
    public static void runAs(Long userId, Runnable work) {
        callAs(userId, () -> {
            work.run();
            return null;
        });
    }

    public static <T> T callAs(Long userId, Supplier<T> work) {
        if (userId == null) {
            throw new IllegalStateException("Cannot run without an owner");
        }
        Long previous = SCOPED.get();
        SCOPED.set(userId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                SCOPED.remove();
            } else {
                SCOPED.set(previous);
            }
        }
    }
}
