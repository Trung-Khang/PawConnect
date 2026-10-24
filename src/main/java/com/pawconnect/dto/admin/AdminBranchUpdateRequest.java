package com.pawconnect.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminBranchUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 50) String phone,
        Double latitude,
        Double longitude) {
}
