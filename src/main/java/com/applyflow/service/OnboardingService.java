package com.applyflow.service;

import com.applyflow.common.EmailProvider;
import com.applyflow.common.SyncJobStatus;
import com.applyflow.common.SyncStatus;
import com.applyflow.dto.AuthDtos.OnboardingStatus;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.User;
import com.applyflow.exception.NotFoundException;
import com.applyflow.repository.EmailAccountRepository;
import com.applyflow.repository.JobApplicationRepository;
import com.applyflow.repository.SyncJobRepository;
import com.applyflow.repository.UserRepository;
import com.applyflow.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.List;

/** Getting-started checklist of the current user (API contract §16). */
@Service
public class OnboardingService {

    private final UserRepository userRepository;
    private final EmailAccountRepository accountRepository;
    private final SyncJobRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final SyncService syncService;

    public OnboardingService(UserRepository userRepository, EmailAccountRepository accountRepository,
                             SyncJobRepository jobRepository, JobApplicationRepository applicationRepository,
                             SyncService syncService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.syncService = syncService;
    }

    public OnboardingStatus status() {
        Long userId = CurrentUser.id();
        User user = user(userId);
        List<EmailAccount> accounts = accountRepository.findByUserIdOrderByCreatedAtAsc(userId);
        boolean hasEmailAccount = accounts.stream().anyMatch(a -> a.getProvider() != EmailProvider.DEMO);
        boolean firstSyncCompleted = jobRepository.existsByUserIdAndStatus(userId, SyncJobStatus.COMPLETED);
        boolean syncInProgress = syncService.isAnyRunningFor(userId)
                || accounts.stream().anyMatch(a -> a.getSyncStatus() == SyncStatus.SYNCING);
        boolean hasApplications = applicationRepository.countByUserId(userId) > 0;
        return new OnboardingStatus(hasEmailAccount, firstSyncCompleted, syncInProgress, hasApplications,
                user.isOnboardingDismissed(), hasEmailAccount && firstSyncCompleted);
    }

    public void setDismissed(boolean dismissed) {
        User user = user(CurrentUser.id());
        user.setOnboardingDismissed(dismissed);
        userRepository.save(user);
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
    }
}
