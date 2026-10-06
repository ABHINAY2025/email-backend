package com.applyflow.controller;

import com.applyflow.dto.AuthDtos.OnboardingStatus;
import com.applyflow.service.OnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping
    public OnboardingStatus status() {
        return onboardingService.status();
    }

    @PostMapping("/dismiss")
    public ResponseEntity<Void> dismiss() {
        onboardingService.setDismissed(true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        onboardingService.setDismissed(false);
        return ResponseEntity.noContent().build();
    }
}
