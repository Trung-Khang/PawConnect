package com.pawconnect.dto.admin;

/** Branch information exposed only through the system administration dashboard. */
public record AdminBranchResponse(
        Long id,
        String code,
        String name,
        String address,
        String phone,
        Double latitude,
        Double longitude) {
}
