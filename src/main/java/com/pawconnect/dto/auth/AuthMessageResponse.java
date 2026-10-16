package com.pawconnect.dto.auth;

public record AuthMessageResponse(String message, boolean emailSent, long retryAfterSeconds) { }
