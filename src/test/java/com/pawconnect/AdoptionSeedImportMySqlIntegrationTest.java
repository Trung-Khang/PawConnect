package com.pawconnect;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawconnect.repository.AdoptionApplicationRepository;
import com.pawconnect.repository.AdoptionPostRepository;
import com.pawconnect.repository.DogProfileRepository;
import com.pawconnect.service.seed.AdoptionSeedImportService;
import com.pawconnect.service.seed.AdoptionSeedImportSummary;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Runs only when an operator explicitly enables the shared MySQL acceptance test. */
@Tag("mysql")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
@ActiveProfiles({"mysql", "dev"})
@SpringBootTest(properties = {
        "pawconnect.seed.enabled=false",
        "app.seed.users.enabled=false",
        "pawconnect.seed.adoption.enabled=false"
})
class AdoptionSeedImportMySqlIntegrationTest {

    private static final Path SEED_V3 = Path.of("data-pipeline", "data", "seed", "v3");

    @Autowired private AdoptionSeedImportService adoptionSeedImportService;
    @Autowired private DogProfileRepository dogProfileRepository;
    @Autowired private AdoptionPostRepository adoptionPostRepository;
    @Autowired private AdoptionApplicationRepository adoptionApplicationRepository;

    @Test
    void importsV3IntoMySqlAndIsIdempotent() {
        AdoptionSeedImportSummary first = adoptionSeedImportService.importSeed(SEED_V3);
        assertThat(first.createdDogProfiles() + first.existingDogProfiles()).isEqualTo(30);
        assertThat(first.createdAdoptionPosts() + first.existingAdoptionPosts()).isEqualTo(30);
        assertThat(first.createdAdoptionApplications() + first.existingAdoptionApplications()).isEqualTo(36);
        assertThat(dogProfileRepository.count()).isEqualTo(30);
        assertThat(adoptionPostRepository.count()).isEqualTo(30);
        assertThat(adoptionApplicationRepository.count()).isEqualTo(36);

        AdoptionSeedImportSummary second = adoptionSeedImportService.importSeed(SEED_V3);
        assertThat(second.createdDogProfiles()).isZero();
        assertThat(second.createdAdoptionPosts()).isZero();
        assertThat(second.createdAdoptionApplications()).isZero();
        assertThat(second.existingDogProfiles()).isEqualTo(30);
        assertThat(second.existingAdoptionPosts()).isEqualTo(30);
        assertThat(second.existingAdoptionApplications()).isEqualTo(36);
    }
}
