package com.pawconnect.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Size(max = 254) String usernameOrEmail,
        /** Backward-compatible alias for existing callers of POST /api/auth/login. */
        @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {
}
