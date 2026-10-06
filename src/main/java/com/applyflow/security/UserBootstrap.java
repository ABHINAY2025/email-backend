package com.applyflow.security;

import com.applyflow.config.AppProperties;
import com.applyflow.entity.User;
import com.applyflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates/updates the admin user from environment configuration (APP_USERNAME / APP_PASSWORD / APP_DISPLAY_NAME)
 * and warns about insecure dev defaults (never logging the values). Called at startup by
 * {@link com.applyflow.persistence.MongoSchemaInitializer}, before anything reads data, because the ownership
 * migration assigns pre-multi-user data to this user.
 */
@Component
public class UserBootstrap {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public UserBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder, AppProperties props) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    /** Upserts the admin user and returns it. Idempotent. */
    public User ensureAdmin() {
        warnAboutDefaults();
        AppProperties.Security sec = props.security();
        String username = sec.username().trim();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            // The configured username changed: rename the existing admin (the only user without an email; keeps
            // its settings and data). Self-registered users are never renamed.
            user = userRepository.findFirstByEmailIsNullOrderByIdAsc().orElse(null);
            if (user != null) {
                log.info("Configured admin username changed; updating the existing admin record");
                user.setUsername(username);
            }
        }
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setDisplayName(sec.displayName());
            user.setPasswordHash(passwordEncoder.encode(sec.password()));
            userRepository.save(user);
            log.info("Created admin user '{}'", username);
            return user;
        }
        if (!passwordEncoder.matches(sec.password(), user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(sec.password()));
            log.info("Updated password hash for admin user '{}' from configuration", username);
        }
        return userRepository.save(user);
    }

    private void warnAboutDefaults() {
        if (AppProperties.DEV_PASSWORD.equals(props.security().password())) {
            log.warn("APP_PASSWORD is using the development default. Set APP_PASSWORD for any real deployment.");
        }
        if (AppProperties.DEV_SESSION_SECRET.equals(props.security().sessionSecret())) {
            log.warn("SESSION_SECRET is using the development default. Set SESSION_SECRET for any real deployment.");
        }
        if (AppProperties.DEV_ENCRYPTION_KEY.equals(props.encryption().key())) {
            log.warn("APP_ENCRYPTION_KEY is using the development default. Stored mailbox passwords are NOT safely "
                    + "encrypted. Set APP_ENCRYPTION_KEY (and keep it stable) for any real deployment.");
        }
    }
}
