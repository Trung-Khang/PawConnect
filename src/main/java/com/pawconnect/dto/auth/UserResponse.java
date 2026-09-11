package com.pawconnect.dto.auth;

import java.time.Instant;

public record UserResponse(Long id, String fullName, String email, String phone, String avatarUrl, String role, Instant createdAt) {
}
