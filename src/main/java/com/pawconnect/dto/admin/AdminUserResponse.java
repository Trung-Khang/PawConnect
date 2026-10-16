package com.pawconnect.dto.admin;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        String role,
        boolean active,
        boolean locked,
        boolean emailVerified,
        Instant createdAt) {
}
