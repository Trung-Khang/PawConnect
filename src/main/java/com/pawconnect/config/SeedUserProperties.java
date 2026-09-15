package com.pawconnect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed.users")
public record SeedUserProperties(boolean enabled, String directory, String password) {
}
