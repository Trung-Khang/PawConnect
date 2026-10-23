package com.pawconnect.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Username format is invalid") String username,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password,
        @NotBlank @Size(max = 72) String confirmPassword,
        @NotBlank @Size(max = 30) String phone) {
}
