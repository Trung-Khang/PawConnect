package com.pawconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawconnect.entity.AdoptionApplicationStatus;
import com.pawconnect.entity.AdoptionPostStatus;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.RoleName;
import com.pawconnect.repository.AdoptionApplicationRepository;
import com.pawconnect.repository.AdoptionPostRepository;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.DogProfileRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.service.seed.AdoptionSeedImportService;
import com.pawconnect.service.seed.AdoptionSeedImportSummary;
import com.pawconnect.service.seed.SeedUserImportService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.seed.users.enabled=false",
        "app.seed.users.directory=data-pipeline/data/seed/v3",
        "app.seed.users.password=runtime-test-password"})
class AdoptionSeedImportServiceIntegrationTest {

    private static final Path SEED_ROOT = Path.of("data-pipeline", "data", "seed", "v3");

    @Autowired private AdoptionSeedImportService adoptionSeedImportService;
    @Autowired private SeedUserImportService seedUserImportService;
    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DogProfileRepository dogProfileRepository;
    @Autowired private AdoptionPostRepository adoptionPostRepository;
    @Autowired private AdoptionApplicationRepository adoptionApplicationRepository;

    @AfterEach
    void clearData() {
        adoptionApplicationRepository.deleteAll();
        adoptionPostRepository.deleteAll();
        dogProfileRepository.deleteAll();
        userRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    @Transactional
    void importsSeedV3OnH2AndSkipsExistingStableKeys() {
        prepareSharedReferences();

        AdoptionSeedImportSummary first = adoptionSeedImportService.importSeed(SEED_ROOT);
        AdoptionSeedImportSummary second = adoptionSeedImportService.importSeed(SEED_ROOT);

        assertThat(first).isEqualTo(new AdoptionSeedImportSummary(30, 0, 30, 0, 36, 0));
        assertThat(second).isEqualTo(new AdoptionSeedImportSummary(0, 30, 0, 30, 0, 36));
        assertThat(dogProfileRepository.count()).isEqualTo(30);
        assertThat(adoptionPostRepository.count()).isEqualTo(30);
        assertThat(adoptionApplicationRepository.count()).isEqualTo(36);
        assertThat(adoptionPostRepository.findByStatus(AdoptionPostStatus.AVAILABLE)).hasSize(24);
        assertThat(adoptionApplicationRepository.findAll())
                .filteredOn(application -> application.getStatus() == AdoptionApplicationStatus.APPROVED)
                .hasSize(6);
        assertThat(adoptionPostRepository.findAll()).allSatisfy(post -> {
            assertThat(post.getCreatedBy().getRole().getName())
                    .isIn(RoleName.ADMIN, RoleName.BRANCH_MANAGER);
            if (post.getCreatedBy().getRole().getName() == RoleName.BRANCH_MANAGER) {
                assertThat(post.getCreatedBy().getBranch().getId()).isEqualTo(post.getDogProfile().getBranch().getId());
            }
        });
    }

    @Test
    void rejectsInvalidBreedBeforeWritingAnyAdoptionRecord(@TempDir Path temporaryDirectory) throws IOException {
        prepareSharedReferences();
        copySeed(SEED_ROOT, temporaryDirectory);
        Path dogs = temporaryDirectory.resolve("adoption/dog_profiles.csv");
        String invalid = Files.readString(dogs, StandardCharsets.UTF_8)
                .replaceFirst(",Poodle,SMALL,", ",Unknown Breed,SMALL,");
        Files.writeString(dogs, invalid, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adoptionSeedImportService.importSeed(temporaryDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DogProfile breed is not in Seed V3 catalog");
        assertThat(dogProfileRepository.count()).isZero();
        assertThat(adoptionPostRepository.count()).isZero();
        assertThat(adoptionApplicationRepository.count()).isZero();
    }

    private void prepareSharedReferences() {
        saveBranch("BR_HCM_01", "Ho Chi Minh");
        saveBranch("BR_HN_01", "Ha Noi");
        saveBranch("BR_DN_01", "Da Nang");
        seedUserImportService.importConfiguredSeed();
    }

    private void saveBranch(String code, String name) {
        Branch branch = new Branch();
        branch.setCode(code);
        branch.setName(name);
        branch.setAddress("Test branch");
        branchRepository.save(branch);
    }

    private void copySeed(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            paths.sorted(Comparator.naturalOrder()).forEach(path -> {
                try {
                    Path destination = target.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(path, destination);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Cannot prepare temporary seed copy", exception);
                }
            });
        }
    }
}
