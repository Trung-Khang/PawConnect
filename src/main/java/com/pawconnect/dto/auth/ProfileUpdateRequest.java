package com.pawconnect.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Editable fields of the currently authenticated account only. */
public record ProfileUpdateRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 30) String phone) {
}
