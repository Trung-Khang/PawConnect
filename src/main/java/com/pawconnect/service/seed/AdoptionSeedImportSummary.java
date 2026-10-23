package com.pawconnect.service.seed;

public record AdoptionSeedImportSummary(
        int createdDogProfiles,
        int existingDogProfiles,
        int createdAdoptionPosts,
        int existingAdoptionPosts,
        int createdAdoptionApplications,
        int existingAdoptionApplications) {
}
