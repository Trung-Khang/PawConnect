package com.pawconnect.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminBranchCreateRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{3,40}", message = "Mã chi nhánh chỉ gồm chữ in hoa, số và dấu gạch dưới") String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 50) String phone,
        Double latitude,
        Double longitude) {
}
