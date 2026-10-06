package com.applyflow.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/** The authenticated user as stored in the session; carries the user id so requests never look it up again. */
public class AppUserPrincipal extends User {

    private static final long serialVersionUID = 1L;

    private final Long id;

    public AppUserPrincipal(Long id, String username, String passwordHash) {
        super(username, passwordHash == null ? "" : passwordHash, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
