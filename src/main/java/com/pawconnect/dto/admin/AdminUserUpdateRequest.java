package com.pawconnect.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserUpdateRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 30) String phone,
        boolean active,
        boolean locked) {
}
