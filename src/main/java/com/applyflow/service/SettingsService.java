package com.applyflow.service;

import com.applyflow.dto.MiscDtos.AppSettings;
import com.applyflow.entity.User;
import com.applyflow.exception.NotFoundException;
import com.applyflow.repository.UserRepository;
import com.applyflow.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settings live on each user's own row. */
@Service
public class SettingsService {

    /** Defaults for new users (and the fallback when a user row is missing). */
    public static final AppSettings DEFAULTS = new AppSettings("User", 5, 90, 0.75, true, 14);

    private final UserRepository userRepository;

    public SettingsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AppSettings get() {
        return toDto(user(CurrentUser.id()));
    }

    @Transactional
    public AppSettings update(AppSettings s) {
        User u = user(CurrentUser.id());
        u.setDisplayName(s.displayName().trim());
        u.setSyncIntervalMinutes(s.syncIntervalMinutes());
        u.setDefaultInitialSyncDays(s.defaultInitialSyncDays());
        u.setConfidenceThreshold(s.confidenceThreshold());
        u.setAutoUpdateStatus(s.autoUpdateStatus());
        u.setFollowUpDays(s.followUpDays());
        return toDto(userRepository.save(u));
    }

    /** Settings snapshot of the current user (request or background owner scope). */
    @Transactional(readOnly = true)
    public AppSettings current() {
        return forUser(CurrentUser.id());
    }

    /** Settings of the given user; defaults if the row is missing (should not happen). */
    @Transactional(readOnly = true)
    public AppSettings forUser(Long userId) {
        return userRepository.findById(userId).map(SettingsService::toDto).orElse(DEFAULTS);
    }

    private User user(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User settings not found."));
    }

    private static AppSettings toDto(User u) {
        return new AppSettings(u.getDisplayName(), u.getSyncIntervalMinutes(), u.getDefaultInitialSyncDays(),
                u.getConfidenceThreshold(), u.isAutoUpdateStatus(), u.getFollowUpDays());
    }
}
