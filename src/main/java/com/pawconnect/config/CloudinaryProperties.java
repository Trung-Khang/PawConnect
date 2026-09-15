package com.pawconnect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(
        boolean enabled,
        String cloudName,
        String apiKey,
        String apiSecret,
        long maxFileSizeBytes,
        int maxImageDimension) {
}
