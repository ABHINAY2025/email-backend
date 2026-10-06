package com.applyflow.service;

import com.applyflow.config.AppProperties;
import com.applyflow.dto.AuthDtos.AuthConfig;
import com.applyflow.dto.AuthDtos.CurrentUser;
import com.applyflow.dto.AuthDtos.RegisterRequest;
import com.applyflow.entity.User;
import com.applyflow.exception.ApiException;
import com.applyflow.exception.ConflictException;
import com.applyflow.exception.FieldValidationException;
import com.applyflow.persistence.MongoErrors;
import com.applyflow.repository.UserRepository;
import com.applyflow.security.AppUserPrincipal;
import com.applyflow.security.ClientIp;
import com.applyflow.security.LoginAttemptTracker;
import com.applyflow.security.RegistrationRateLimiter;
import com.applyflow.security.RegistrationValidator;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final String EMAIL_TAKEN = "An account with this email already exists.";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final LoginAttemptTracker attempts;
    private final RegistrationRateLimiter registrations;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public AuthService(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
                       UserRepository userRepository, LoginAttemptTracker attempts,
                       RegistrationRateLimiter registrations, PasswordEncoder passwordEncoder, AppProperties props) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userRepository = userRepository;
        this.attempts = attempts;
        this.registrations = registrations;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    public AuthConfig config() {
        return new AuthConfig(props.security().registrationEnabled());
    }

    /** {@code username} may be the username or the account email (case-insensitive). */
    public CurrentUser login(String username, String password, HttpServletRequest request,
                             HttpServletResponse response) {
        String key = ClientIp.of(request);
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
        establishSession(auth, request, response);
        return current(((AppUserPrincipal) auth.getPrincipal()).getId());
    }

    /** Creates the account and signs the new user in (same session handling as {@link #login}). */
    public CurrentUser register(RegisterRequest req, HttpServletRequest request, HttpServletResponse response) {
        if (!props.security().registrationEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATION_DISABLED", "Registration is disabled.");
        }
        Map<String, String> errors = RegistrationValidator.validate(req);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        if (!registrations.tryAcquire(ClientIp.of(request))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many sign-ups from this network. Please try again later.");
        }
        String email = RegistrationValidator.normalizeEmail(req.email());
        if (userRepository.existsByEmail(email) || userRepository.existsByUsername(email)) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        User u = new User();
        u.setUsername(email);
        u.setEmail(email);
        u.setDisplayName(req.displayName().trim());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setSyncIntervalMinutes(5);
        u.setDefaultInitialSyncDays(90);
        u.setConfidenceThreshold(0.75);
        u.setAutoUpdateStatus(true);
        u.setFollowUpDays(14);
        u.setOnboardingDismissed(false);
        try {
            userRepository.save(u);
        } catch (RuntimeException e) {
            if (MongoErrors.isDuplicateKey(e)) {
                throw new ConflictException(EMAIL_TAKEN); // concurrent registration of the same email
            }
            throw e;
        }
        log.info("Registered new user {}", u.getId());
        AppUserPrincipal principal = new AppUserPrincipal(u.getId(), u.getUsername(), null);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
        establishSession(auth, request, response);
        return toDto(u);
    }

    public CurrentUser current(Long userId) {
        return userRepository.findById(userId).map(AuthService::toDto)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Authentication required. Please sign in."));
    }

    private void establishSession(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId(); // session fixation protection
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static CurrentUser toDto(User u) {
        return new CurrentUser(u.getUsername(), u.getDisplayName(), u.getEmail());
    }
}
