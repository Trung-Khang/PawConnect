package com.pawconnect.config;

import com.pawconnect.service.seed.SeedUserImportService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
public class SeedUserInitializer {

    @Bean
    @Order(20)
    @Profile({"dev", "test", "demo"})
    @ConditionalOnProperty(prefix = "app.seed.users", name = "enabled", havingValue = "true")
    CommandLineRunner initializeSeedUsers(SeedUserImportService seedUserImportService) {
        return arguments -> seedUserImportService.importConfiguredSeed();
    }
}
