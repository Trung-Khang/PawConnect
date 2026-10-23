package com.pawconnect.config;

import com.pawconnect.service.seed.AdoptionSeedImportService;
import com.pawconnect.service.seed.AdoptionSeedImportSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Imports the Adoption slice only when explicitly requested by an operator.
 * Branches, roles, and users must already be present in the shared database.
 */
@Configuration
public class AdoptionSeedInitializer {

    @Bean
    @Order(40)
    @ConditionalOnProperty(name = "pawconnect.seed.adoption.enabled", havingValue = "true")
    CommandLineRunner initializeAdoptionSeed(AdoptionSeedImportService adoptionSeedImportService,
                                             @Value("${pawconnect.seed.release-directory:data-pipeline/data/seed/v3}") String directory) {
        return arguments -> {
            Path seedRoot = Path.of(directory).toAbsolutePath().normalize();
            if (!Files.isDirectory(seedRoot)) {
                throw new IllegalStateException("Seed V3 directory does not exist: " + seedRoot);
            }
            AdoptionSeedImportSummary summary = adoptionSeedImportService.importSeed(seedRoot);
            System.out.printf("Adoption Seed V3 import completed: dogs created=%d existing=%d, posts created=%d existing=%d, applications created=%d existing=%d%n",
                    summary.createdDogProfiles(), summary.existingDogProfiles(),
                    summary.createdAdoptionPosts(), summary.existingAdoptionPosts(),
                    summary.createdAdoptionApplications(), summary.existingAdoptionApplications());
        };
    }
}
