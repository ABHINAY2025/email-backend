package com.applyflow.controller;

import com.applyflow.dto.AuthDtos.AuthConfig;
import com.applyflow.dto.AuthDtos.CurrentUser;
import com.applyflow.dto.AuthDtos.LoginRequest;
import com.applyflow.dto.AuthDtos.RegisterRequest;
import com.applyflow.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    /** Public. */
    @GetMapping("/config")
    public AuthConfig config() {
        return authService.config();
    }

    @PostMapping("/login")
    public CurrentUser login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                             HttpServletResponse response) {
        return authService.login(body.username(), body.password(), request, response);
    }

    /** Public and CSRF-exempt like login; creates the account and signs the user in. */
    @PostMapping("/register")
    public ResponseEntity<CurrentUser> register(@RequestBody RegisterRequest body, HttpServletRequest request,
                                                HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(body, request, response));
    }

    @GetMapping("/me")
    public CurrentUser me() {
        return authService.current(com.applyflow.security.CurrentUser.id());
    }
}
