package com.applyflow.service;

import com.applyflow.dto.AuthDtos.CurrentUser;
import com.applyflow.exception.ApiException;
import com.applyflow.repository.UserRepository;
import com.applyflow.security.LoginAttemptTracker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final LoginAttemptTracker attempts;

    public AuthService(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
                       UserRepository userRepository, LoginAttemptTracker attempts) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userRepository = userRepository;
        this.attempts = attempts;
    }

    public CurrentUser login(String username, String password, HttpServletRequest request,
                             HttpServletResponse response) {
        String key = request.getRemoteAddr();
        if (attempts.isBlocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many failed sign-in attempts. Please wait a few minutes and try again.");
        }
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username.trim(), password));
        } catch (AuthenticationException e) {
            attempts.recordFailure(key);
            log.info("Failed sign-in attempt for user '{}'", username);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid username or password.");
        }
        attempts.reset(key);
        if (request.getSession(false) != null) {
            request.changeSessionId(); // session fixation protection
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return current(auth.getName());
    }

    @Transactional(readOnly = true)
    public CurrentUser current(String username) {
        return userRepository.findByUsername(username)
                .map(u -> new CurrentUser(u.getUsername(), u.getDisplayName()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Authentication required. Please sign in."));
    }
}
