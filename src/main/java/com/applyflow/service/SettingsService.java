package com.applyflow.service;

import com.applyflow.dto.MiscDtos.AppSettings;
import com.applyflow.entity.User;
import com.applyflow.exception.NotFoundException;
import com.applyflow.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settings live on the single user row. */
@Service
public class SettingsService {

    private final UserRepository userRepository;

    public SettingsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AppSettings get() {
        return toDto(user());
    }

    @Transactional
    public AppSettings update(AppSettings s) {
        User u = user();
        u.setDisplayName(s.displayName().trim());
        u.setSyncIntervalMinutes(s.syncIntervalMinutes());
        u.setDefaultInitialSyncDays(s.defaultInitialSyncDays());
        u.setConfidenceThreshold(s.confidenceThreshold());
        u.setAutoUpdateStatus(s.autoUpdateStatus());
        u.setFollowUpDays(s.followUpDays());
        return toDto(userRepository.save(u));
    }

    /** Current settings snapshot; falls back to defaults if the user row is missing (should not happen). */
    @Transactional(readOnly = true)
    public AppSettings current() {
        return userRepository.findFirstByOrderByIdAsc().map(SettingsService::toDto)
                .orElse(new AppSettings("User", 5, 90, 0.75, true, 14));
    }

    private User user() {
        return userRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("User settings not found."));
    }

    private static AppSettings toDto(User u) {
        return new AppSettings(u.getDisplayName(), u.getSyncIntervalMinutes(), u.getDefaultInitialSyncDays(),
                u.getConfidenceThreshold(), u.isAutoUpdateStatus(), u.getFollowUpDays());
    }
}
