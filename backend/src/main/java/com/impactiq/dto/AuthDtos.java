package com.impactiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required") @Size(max = 64) String username,
            @NotBlank(message = "Password is required") @Size(max = 72) String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 64, message = "Username must be 3-64 characters")
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username may contain letters, digits, dot, dash and underscore")
            String username,
            /* BCrypt only uses the first 72 bytes, so longer passwords are rejected instead of silently truncated. */
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
            String password,
            @Size(max = 120, message = "Display name must be at most 120 characters")
            String displayName
    ) {
    }

    public record UserResponse(Long id, String username, String displayName, String role) {
    }

    public record AuthStatusResponse(boolean authenticated, UserResponse user, boolean registrationEnabled) {
    }

    public record CsrfResponse(String headerName, String token) {
    }
}
