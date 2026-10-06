package com.applyflow.security;

import com.applyflow.config.AppProperties;
import com.applyflow.entity.User;
import com.applyflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts the single user from environment configuration at startup and warns about insecure dev defaults
 * (never logging the values).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public UserBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder, AppProperties props) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        warnAboutDefaults();
        AppProperties.Security sec = props.security();
        String username = sec.username().trim();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            // Single-user app: rename an existing user row (keeps settings) if the username changed.
            user = userRepository.findFirstByOrderByIdAsc().orElse(null);
            if (user != null) {
                log.info("Configured username changed; updating the existing user record");
                user.setUsername(username);
            }
        }
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setDisplayName(sec.displayName());
            user.setPasswordHash(passwordEncoder.encode(sec.password()));
            userRepository.save(user);
            log.info("Created application user '{}'", username);
            return;
        }
        if (!passwordEncoder.matches(sec.password(), user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(sec.password()));
            log.info("Updated password hash for user '{}' from configuration", username);
        }
        userRepository.save(user);
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
