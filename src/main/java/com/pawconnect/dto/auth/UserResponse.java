package com.pawconnect.dto.auth;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        String role,
        Instant createdAt,
        Long branchId,
        String branchName
) {
    public UserResponse(Long id, String username, String fullName, String email, String phone, String avatarUrl, String role, Instant createdAt) {
        this(id, username, fullName, email, phone, avatarUrl, role, createdAt, null, null);
    }
}
