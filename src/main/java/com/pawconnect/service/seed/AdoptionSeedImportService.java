package com.pawconnect.service.seed;

import com.pawconnect.entity.AdoptionApplication;
import com.pawconnect.entity.AdoptionApplicationStatus;
import com.pawconnect.entity.AdoptionPost;
import com.pawconnect.entity.AdoptionPostStatus;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.DogGender;
import com.pawconnect.entity.DogProfile;
import com.pawconnect.entity.DogSize;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.entity.VaccinationStatus;
import com.pawconnect.repository.AdoptionApplicationRepository;
import com.pawconnect.repository.AdoptionPostRepository;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.DogProfileRepository;
import com.pawconnect.repository.UserRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Imports only the adoption slice after shared Branch and User records already exist. */
@Service
public class AdoptionSeedImportService {

    private static final List<String> BREED_HEADER = List.of(
            "seed_key", "breed_code", "breed_name", "default_size", "min_weight_kg", "max_weight_kg",
            "min_age_months", "max_age_months", "description");
    private static final List<String> BREED_REFERENCE_HEADER = List.of(
            "breed_code", "source_name", "source_url", "retrieved_at", "content_sha256", "license_note", "evidence_status");
    private static final List<String> DOG_HEADER = List.of(
            "seed_key", "name", "breed", "size", "age_months", "weight_kg", "gender", "vaccination_status",
            "image_url", "description", "branch_code");
    private static final List<String> POST_HEADER = List.of(
            "seed_key", "dog_profile_seed_key", "created_by_user_key", "title", "description", "health_note",
            "status", "created_at");
    private static final List<String> APPLICATION_HEADER = List.of(
            "seed_key", "adoption_post_seed_key", "applicant_user_key", "message", "status");

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final DogProfileRepository dogProfileRepository;
    private final AdoptionPostRepository adoptionPostRepository;
    private final AdoptionApplicationRepository adoptionApplicationRepository;

    public AdoptionSeedImportService(BranchRepository branchRepository, UserRepository userRepository,
                                     DogProfileRepository dogProfileRepository, AdoptionPostRepository adoptionPostRepository,
                                     AdoptionApplicationRepository adoptionApplicationRepository) {
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.dogProfileRepository = dogProfileRepository;
        this.adoptionPostRepository = adoptionPostRepository;
        this.adoptionApplicationRepository = adoptionApplicationRepository;
    }

    @Transactional
    public AdoptionSeedImportSummary importSeed(Path seedRoot) {
        Map<String, BreedRule> breeds = readBreedRules(seedRoot);
        List<DogRow> dogRows = readDogs(seedRoot.resolve("adoption/dog_profiles.csv"));
        List<PostRow> postRows = readPosts(seedRoot.resolve("adoption/adoption_posts.csv"));
        List<ApplicationRow> applicationRows = readApplications(seedRoot.resolve("adoption/adoption_applications.csv"));

        Map<String, Branch> branches = resolveBranches(dogRows);
        Map<String, User> users = resolveUsers(postRows, applicationRows);
        validateRows(breeds, dogRows, postRows, applicationRows, branches, users);

        int createdDogs = 0;
        int existingDogs = 0;
        Map<String, DogProfile> dogs = new LinkedHashMap<>();
        for (DogRow row : dogRows) {
            DogProfile dog = dogProfileRepository.findBySeedKey(row.seedKey()).orElse(null);
            if (dog == null) {
                dog = dogProfileRepository.save(DogProfile.builder()
                        .seedKey(row.seedKey())
                        .name(row.name())
                        .breed(row.breed())
                        .size(row.size())
                        .ageMonths(row.ageMonths())
                        .weightKg(row.weightKg())
                        .gender(row.gender())
                        .vaccinationStatus(row.vaccinationStatus())
                        .imageUrl(row.imageUrl())
                        .description(row.description())
                        .branch(branches.get(row.branchCode()))
                        .build());
                createdDogs++;
            } else {
                existingDogs++;
            }
            dogs.put(row.seedKey(), dog);
        }

        int createdPosts = 0;
        int existingPosts = 0;
        Map<String, AdoptionPost> posts = new LinkedHashMap<>();
        for (PostRow row : postRows) {
            AdoptionPost post = adoptionPostRepository.findBySeedKey(row.seedKey()).orElse(null);
            if (post == null) {
                post = adoptionPostRepository.save(AdoptionPost.builder()
                        .seedKey(row.seedKey())
                        .dogProfile(dogs.get(row.dogProfileSeedKey()))
                        .createdBy(users.get(row.createdByUserKey()))
                        .title(row.title())
                        .description(row.description())
                        .healthNote(row.healthNote())
                        .status(row.status())
                        .createdAt(row.createdAt())
                        .build());
                createdPosts++;
            } else {
                existingPosts++;
            }
            posts.put(row.seedKey(), post);
        }

        int createdApplications = 0;
        int existingApplications = 0;
        for (ApplicationRow row : applicationRows) {
            if (adoptionApplicationRepository.findBySeedKey(row.seedKey()).isPresent()) {
                existingApplications++;
                continue;
            }
            adoptionApplicationRepository.save(AdoptionApplication.builder()
                    .seedKey(row.seedKey())
                    .adoptionPost(posts.get(row.adoptionPostSeedKey()))
                    .applicant(users.get(row.applicantUserKey()))
                    .message(row.message())
                    .status(row.status())
                    .build());
            createdApplications++;
        }

        return new AdoptionSeedImportSummary(createdDogs, existingDogs, createdPosts, existingPosts,
                createdApplications, existingApplications);
    }

    private Map<String, BreedRule> readBreedRules(Path seedRoot) {
        List<Map<String, String>> breedRows = readCsv(seedRoot.resolve("catalog/breeds.csv"), BREED_HEADER);
        List<Map<String, String>> referenceRows = readCsv(seedRoot.resolve("catalog/breed_references.csv"), BREED_REFERENCE_HEADER);
        Set<String> referencedCodes = new HashSet<>();
        for (Map<String, String> row : referenceRows) {
            requireNonBlank(row, "breed_code", "breed_references.csv");
            requireNonBlank(row, "evidence_status", "breed_references.csv");
            referencedCodes.add(row.get("breed_code"));
        }

        Map<String, BreedRule> rules = new HashMap<>();
        for (Map<String, String> row : breedRows) {
            String breedName = requireNonBlank(row, "breed_name", "breeds.csv");
            String breedCode = requireNonBlank(row, "breed_code", "breeds.csv");
            if (!referencedCodes.contains(breedCode)) {
                throw new IllegalStateException("Breed is missing provenance reference: " + breedCode);
            }
            BreedRule rule = new BreedRule(
                    parseEnum(DogSize.class, row.get("default_size"), "default_size"),
                    positiveDecimal(row.get("min_weight_kg"), "min_weight_kg"),
                    positiveDecimal(row.get("max_weight_kg"), "max_weight_kg"),
                    nonNegativeInt(row.get("min_age_months"), "min_age_months"),
                    positiveInt(row.get("max_age_months"), "max_age_months"));
            if (rule.maxWeightKg().compareTo(rule.minWeightKg()) < 0 || rule.maxAgeMonths() <= rule.minAgeMonths()) {
                throw new IllegalStateException("Invalid catalog range for " + breedName);
            }
            if (rules.put(breedName, rule) != null) {
                throw new IllegalStateException("Duplicate breed_name in catalog: " + breedName);
            }
        }
        return rules;
    }

    private List<DogRow> readDogs(Path file) {
        List<DogRow> rows = new ArrayList<>();
        for (Map<String, String> row : readCsv(file, DOG_HEADER)) {
            rows.add(new DogRow(
                    requireNonBlank(row, "seed_key", file.getFileName().toString()),
                    requireNonBlank(row, "name", file.getFileName().toString()),
                    requireNonBlank(row, "breed", file.getFileName().toString()),
                    parseEnum(DogSize.class, row.get("size"), "size"),
                    nonNegativeInt(row.get("age_months"), "age_months"),
                    positiveDecimal(row.get("weight_kg"), "weight_kg"),
                    parseEnum(DogGender.class, row.get("gender"), "gender"),
                    parseEnum(VaccinationStatus.class, row.get("vaccination_status"), "vaccination_status"),
                    validateHttpsOrBlank(row.get("image_url"), "image_url"),
                    blankToNull(row.get("description")),
                    requireNonBlank(row, "branch_code", file.getFileName().toString())));
        }
        ensureUnique(rows.stream().map(DogRow::seedKey).toList(), "DogProfile seed_key");
        return rows;
    }

    private List<PostRow> readPosts(Path file) {
        List<PostRow> rows = new ArrayList<>();
        for (Map<String, String> row : readCsv(file, POST_HEADER)) {
            rows.add(new PostRow(
                    requireNonBlank(row, "seed_key", file.getFileName().toString()),
                    requireNonBlank(row, "dog_profile_seed_key", file.getFileName().toString()),
                    requireNonBlank(row, "created_by_user_key", file.getFileName().toString()),
                    requireNonBlank(row, "title", file.getFileName().toString()),
                    requireNonBlank(row, "description", file.getFileName().toString()),
                    blankToNull(row.get("health_note")),
                    parseEnum(AdoptionPostStatus.class, row.get("status"), "AdoptionPost.status"),
                    parseInstant(row.get("created_at"), "created_at")));
        }
        ensureUnique(rows.stream().map(PostRow::seedKey).toList(), "AdoptionPost seed_key");
        return rows;
    }

    private List<ApplicationRow> readApplications(Path file) {
        List<ApplicationRow> rows = new ArrayList<>();
        for (Map<String, String> row : readCsv(file, APPLICATION_HEADER)) {
            rows.add(new ApplicationRow(
                    requireNonBlank(row, "seed_key", file.getFileName().toString()),
                    requireNonBlank(row, "adoption_post_seed_key", file.getFileName().toString()),
                    requireNonBlank(row, "applicant_user_key", file.getFileName().toString()),
                    requireNonBlank(row, "message", file.getFileName().toString()),
                    parseEnum(AdoptionApplicationStatus.class, row.get("status"), "AdoptionApplication.status")));
        }
        ensureUnique(rows.stream().map(ApplicationRow::seedKey).toList(), "AdoptionApplication seed_key");
        return rows;
    }

    private Map<String, Branch> resolveBranches(List<DogRow> rows) {
        Map<String, Branch> branches = new HashMap<>();
        for (DogRow row : rows) {
            branches.computeIfAbsent(row.branchCode(), code -> branchRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Branch has not been imported for branch_code " + code)));
        }
        return branches;
    }

    private Map<String, User> resolveUsers(List<PostRow> posts, List<ApplicationRow> applications) {
        Map<String, User> users = new HashMap<>();
        for (PostRow row : posts) {
            users.computeIfAbsent(row.createdByUserKey(), this::requiredUser);
        }
        for (ApplicationRow row : applications) {
            users.computeIfAbsent(row.applicantUserKey(), this::requiredUser);
        }
        return users;
    }

    private User requiredUser(String seedKey) {
        return userRepository.findBySeedKey(seedKey)
                .orElseThrow(() -> new IllegalStateException("User has not been imported for seed_key " + seedKey));
    }

    private void validateRows(Map<String, BreedRule> breeds, List<DogRow> dogs, List<PostRow> posts,
                              List<ApplicationRow> applications, Map<String, Branch> branches, Map<String, User> users) {
        Map<String, DogRow> dogsByKey = indexByKey(dogs.stream().collect(LinkedHashMap::new, (map, row) -> map.put(row.seedKey(), row), Map::putAll));
        Map<String, PostRow> postsByKey = indexByKey(posts.stream().collect(LinkedHashMap::new, (map, row) -> map.put(row.seedKey(), row), Map::putAll));

        for (DogRow dog : dogs) {
            BreedRule breed = breeds.get(dog.breed());
            if (breed == null) {
                throw new IllegalStateException("DogProfile breed is not in Seed V3 catalog: " + dog.breed());
            }
            if (dog.size() != breed.size() || dog.ageMonths() < breed.minAgeMonths() || dog.ageMonths() > breed.maxAgeMonths()
                    || dog.weightKg().compareTo(breed.minWeightKg()) < 0 || dog.weightKg().compareTo(breed.maxWeightKg()) > 0) {
                throw new IllegalStateException("DogProfile values are outside catalog range: " + dog.seedKey());
            }
            if (!branches.containsKey(dog.branchCode())) {
                throw new IllegalStateException("Unknown DogProfile branch_code: " + dog.branchCode());
            }
        }

        Map<String, List<ApplicationRow>> applicationsByPost = new HashMap<>();
        for (ApplicationRow application : applications) {
            PostRow post = postsByKey.get(application.adoptionPostSeedKey());
            if (post == null) {
                throw new IllegalStateException("AdoptionApplication references unknown post: " + application.adoptionPostSeedKey());
            }
            User applicant = users.get(application.applicantUserKey());
            if (applicant.getRole().getName() != RoleName.CUSTOMER) {
                throw new IllegalStateException("AdoptionApplication applicant must have CUSTOMER role: " + application.applicantUserKey());
            }
            applicationsByPost.computeIfAbsent(application.adoptionPostSeedKey(), ignored -> new ArrayList<>()).add(application);
        }

        for (PostRow post : posts) {
            DogRow dog = dogsByKey.get(post.dogProfileSeedKey());
            if (dog == null) {
                throw new IllegalStateException("AdoptionPost references unknown DogProfile: " + post.dogProfileSeedKey());
            }
            User author = users.get(post.createdByUserKey());
            if (author.getRole().getName() != RoleName.ADMIN && author.getRole().getName() != RoleName.BRANCH_MANAGER) {
                throw new IllegalStateException("AdoptionPost author must be ADMIN or BRANCH_MANAGER: " + post.createdByUserKey());
            }
            if (author.getRole().getName() == RoleName.BRANCH_MANAGER
                    && (author.getBranch() == null || !author.getBranch().getId().equals(branches.get(dog.branchCode()).getId()))) {
                throw new IllegalStateException("BRANCH_MANAGER must match DogProfile branch: " + post.seedKey());
            }
            List<ApplicationRow> postApplications = applicationsByPost.getOrDefault(post.seedKey(), List.of());
            long approved = postApplications.stream().filter(row -> row.status() == AdoptionApplicationStatus.APPROVED).count();
            boolean pending = postApplications.stream().anyMatch(row -> row.status() == AdoptionApplicationStatus.PENDING);
            if (approved > 1) {
                throw new IllegalStateException("AdoptionPost has more than one APPROVED application: " + post.seedKey());
            }
            if (approved == 1 && post.status() != AdoptionPostStatus.CLOSED) {
                throw new IllegalStateException("Approved application requires CLOSED post: " + post.seedKey());
            }
            if (post.status() == AdoptionPostStatus.CLOSED && pending) {
                throw new IllegalStateException("CLOSED post cannot retain PENDING applications: " + post.seedKey());
            }
            if (post.status() == AdoptionPostStatus.AVAILABLE && approved > 0) {
                throw new IllegalStateException("AVAILABLE post cannot have APPROVED application: " + post.seedKey());
            }
        }
    }

    private List<Map<String, String>> readCsv(Path file, List<String> expectedHeader) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                throw new IllegalStateException("Seed file is empty: " + file);
            }
            List<String> header = parseCsvLine(lines.get(0));
            if (!header.equals(expectedHeader)) {
                throw new IllegalStateException("Unexpected header in " + file + ": " + header);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
                String line = lines.get(lineNumber - 1);
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                if (values.size() != header.size()) {
                    throw new IllegalStateException("Invalid column count in " + file + " at line " + lineNumber);
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < header.size(); index++) {
                    row.put(header.get(index), values.get(index));
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read seed file: " + file, exception);
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw new IllegalStateException("Unclosed quoted CSV value");
        }
        values.add(value.toString());
        return values;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, requireNonBlank(value, field, "seed"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid " + field + " value: " + value, exception);
        }
    }

    private BigDecimal positiveDecimal(String value, String field) {
        try {
            BigDecimal parsed = new BigDecimal(requireNonBlank(value, field, "seed"));
            if (parsed.signum() <= 0) {
                throw new IllegalStateException(field + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid decimal " + field + ": " + value, exception);
        }
    }

    private int nonNegativeInt(String value, String field) {
        int parsed = positiveInt(value, field);
        if (parsed < 0) {
            throw new IllegalStateException(field + " must not be negative");
        }
        return parsed;
    }

    private int positiveInt(String value, String field) {
        try {
            return Integer.parseInt(requireNonBlank(value, field, "seed"));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid integer " + field + ": " + value, exception);
        }
    }

    private java.time.Instant parseInstant(String value, String field) {
        try {
            return OffsetDateTime.parse(requireNonBlank(value, field, "seed")).toInstant();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid ISO-8601 " + field + ": " + value, exception);
        }
    }

    private String validateHttpsOrBlank(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException(field + " must be an HTTPS URL");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid URL " + field + ": " + value, exception);
        }
    }

    private String requireNonBlank(Map<String, String> row, String field, String file) {
        return requireNonBlank(row.get(field), field, file);
    }

    private String requireNonBlank(String value, String field, String file) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalStateException("Missing " + field + " in " + file);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureUnique(List<String> values, String label) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalStateException("Duplicate " + label);
        }
    }

    private <T> Map<String, T> indexByKey(Map<String, T> values) {
        return values;
    }

    private record BreedRule(DogSize size, BigDecimal minWeightKg, BigDecimal maxWeightKg,
                             int minAgeMonths, int maxAgeMonths) {
    }

    private record DogRow(String seedKey, String name, String breed, DogSize size, int ageMonths, BigDecimal weightKg,
                          DogGender gender, VaccinationStatus vaccinationStatus, String imageUrl, String description,
                          String branchCode) {
    }

    private record PostRow(String seedKey, String dogProfileSeedKey, String createdByUserKey, String title,
                           String description, String healthNote, AdoptionPostStatus status, java.time.Instant createdAt) {
    }

    private record ApplicationRow(String seedKey, String adoptionPostSeedKey, String applicantUserKey, String message,
                                  AdoptionApplicationStatus status) {
    }
}
