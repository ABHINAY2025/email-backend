package com.applyflow.security;

import com.applyflow.dto.CommonDtos.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

/** Writes 401/403 as ApiError JSON (no redirects, no HTML, no basic-auth popup). */
public class JsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, ApiError.of(401, "UNAUTHORIZED", "Authentication required. Please sign in."));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        String message = ex instanceof CsrfException
                ? "Invalid or missing CSRF token. Refresh the page and try again."
                : "You do not have permission to perform this action.";
        write(response, ApiError.of(403, "FORBIDDEN", message));
    }

    private void write(HttpServletResponse response, ApiError error) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(error.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
