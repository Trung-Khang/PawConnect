package com.pawconnect.dto.auth;

/** Stable Seed V3 user-key to database-ID mapping for TV1 and TV2 integration. */
public record SeedUserMappingResponse(String seedKey, Long userId, String role, Long branchId) {
}
