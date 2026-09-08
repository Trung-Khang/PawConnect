package com.pawconnect.dto.auth;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, UserResponse user) {
}
