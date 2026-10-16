package com.pawconnect.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String resetToken,
        @NotBlank @Size(max = 72) String newPassword,
        @NotBlank @Size(max = 72) String confirmPassword) { }
