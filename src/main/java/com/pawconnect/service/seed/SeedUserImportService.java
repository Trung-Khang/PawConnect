package com.pawconnect.service.seed;

import com.pawconnect.config.SeedUserProperties;
import com.pawconnect.dto.auth.SeedUserMappingResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeedUserImportService {

    private final SeedUserProperties properties;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedUserImportService(SeedUserProperties properties, RoleRepository roleRepository, BranchRepository branchRepository,
                                 UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.roleRepository = roleRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void importConfiguredSeed() {
        if (properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException("SEED_USER_PASSWORD is required when SEED_USERS_ENABLED=true");
        }
        Path root = Path.of(properties.directory() == null || properties.directory().isBlank()
                ? "data-pipeline/data/seed/v3" : properties.directory());
        Map<String, Role> roles = readRoles(root.resolve("reference/roles.csv"));
        for (String[] row : readCsv(root.resolve("fixtures/users.csv"))) {
            String seedKey = row[0];
            String fullName = row[1];
            String email = row[2].trim().toLowerCase(Locale.ROOT);
            Role role = roles.get(row[6]);
            if (role == null) {
                throw new IllegalStateException("Unknown role_code " + row[6] + " for " + seedKey);
            }
            Branch branch = resolveBranch(seedKey, role.getName(), row[7]);
            User user = userRepository.findBySeedKey(seedKey)
                    .or(() -> userRepository.findByEmailIgnoreCase(email))
                    .orElseGet(() -> new User(fullName, email, passwordEncoder.encode(properties.password()), blankToNull(row[4]), role));
            user.updateSeedProfile(seedKey, role, branch);
            userRepository.save(user);
        }
    }

    @Transactional(readOnly = true)
    public List<SeedUserMappingResponse> mappings() {
        return userRepository.findAll().stream()
                .filter(user -> user.getSeedKey() != null)
                .sorted(java.util.Comparator.comparing(User::getSeedKey))
                .map(user -> new SeedUserMappingResponse(user.getSeedKey(), user.getId(), user.getRole().getName().name(),
                        user.getBranch() == null ? null : user.getBranch().getId()))
                .toList();
    }

    private Map<String, Role> readRoles(Path file) {
        Map<String, Role> roles = new LinkedHashMap<>();
        for (String[] row : readCsv(file)) {
            RoleName roleName = roleNameFor(row[1], row[2]);
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Role was not initialized: " + roleName));
            roles.put(row[1], role);
        }
        return roles;
    }

    private Branch resolveBranch(String seedKey, RoleName role, String branchCode) {
        boolean manager = role == RoleName.BRANCH_MANAGER;
        if (manager && (branchCode == null || branchCode.isBlank())) {
            throw new IllegalStateException("BRANCH_MANAGER " + seedKey + " must have branch_code");
        }
        if (!manager && branchCode != null && !branchCode.isBlank()) {
            throw new IllegalStateException(role + " " + seedKey + " must not have branch_code");
        }
        return manager ? branchRepository.findByCode(branchCode)
                .orElseThrow(() -> new IllegalStateException("Branch has not been imported for branch_code " + branchCode)) : null;
    }

    private List<String[]> readCsv(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty()) {
                throw new IllegalStateException("Seed file is empty: " + file);
            }
            return lines.stream().skip(1).filter(line -> !line.isBlank()).map(line -> line.split(",", -1)).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read seed file: " + file, exception);
        }
    }

    private RoleName roleNameFor(String roleCode, String name) {
        if (!roleCode.equals("ROLE_" + name)) {
            throw new IllegalStateException("role_code/name mismatch: " + roleCode);
        }
        try {
            return RoleName.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported role in seed: " + name, exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
