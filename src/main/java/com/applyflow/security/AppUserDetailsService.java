package com.applyflow.security;

import com.applyflow.entity.User;
import com.applyflow.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/** Signs users in by username or by email (case-insensitive); the principal carries the user id. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) {
        User u = find(login).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return new AppUserPrincipal(u.getId(), u.getUsername(), u.getPasswordHash());
    }

    /** Exact username first (the admin), then the lower-cased value as username or email. */
    public Optional<User> find(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String trimmed = login.trim();
        Optional<User> u = userRepository.findByUsername(trimmed);
        if (u.isPresent()) {
            return u;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        u = userRepository.findByUsername(lower);
        return u.isPresent() ? u : userRepository.findByEmail(lower);
    }
}
