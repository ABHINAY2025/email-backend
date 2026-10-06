package com.applyflow.controller;

import com.applyflow.dto.AuthDtos.CurrentUser;
import com.applyflow.dto.AuthDtos.LoginRequest;
import com.applyflow.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Logout is handled by Spring Security's LogoutFilter at POST /api/auth/logout (204). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Public; the CsrfCookieFilter issues the XSRF-TOKEN cookie. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public CurrentUser login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                             HttpServletResponse response) {
        return authService.login(body.username(), body.password(), request, response);
    }

    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) {
        return authService.current(authentication.getName());
    }
}
