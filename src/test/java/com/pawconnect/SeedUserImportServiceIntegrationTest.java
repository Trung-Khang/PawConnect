package com.pawconnect;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.User;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.service.seed.SeedUserImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = {
        "app.seed.users.enabled=false",
        "app.seed.users.directory=data-pipeline/data/seed/v3",
        "app.seed.users.password=runtime-test-password"})
class SeedUserImportServiceIntegrationTest {

    @Autowired private SeedUserImportService seedUserImportService;
    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void mapsSeedRoleAndBranchCodesToUsersWithoutPersistingPlainPasswords() {
        saveBranch("BR_HCM_01", "HCM");
        saveBranch("BR_HN_01", "Ha Noi");
        saveBranch("BR_DN_01", "Da Nang");

        seedUserImportService.importConfiguredSeed();
        seedUserImportService.importConfiguredSeed();

        assertThat(seedUserImportService.mappings()).hasSize(6);
        assertThat(seedUserImportService.mappings())
                .anySatisfy(mapping -> {
                    assertThat(mapping.seedKey()).isEqualTo("user_manager_hcm");
                    assertThat(mapping.role()).isEqualTo("BRANCH_MANAGER");
                    assertThat(mapping.branchId()).isNotNull();
                });
        User admin = userRepository.findBySeedKey("user_admin_pawconnect").orElseThrow();
        assertThat(admin.getPassword()).isNotEqualTo("runtime-test-password");
        assertThat(passwordEncoder.matches("runtime-test-password", admin.getPassword())).isTrue();
    }

    private void saveBranch(String code, String name) {
        branchRepository.findByCode(code).orElseGet(() -> {
            Branch branch = new Branch();
            branch.setCode(code);
            branch.setName(name);
            branch.setAddress("Test address");
            return branchRepository.save(branch);
        });
    }
}
