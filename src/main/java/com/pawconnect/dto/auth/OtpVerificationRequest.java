package com.pawconnect.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerificationRequest(
        @NotBlank String identity,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP must contain exactly 6 digits") String code) { }
