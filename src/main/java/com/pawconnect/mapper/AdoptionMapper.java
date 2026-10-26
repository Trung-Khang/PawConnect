package com.pawconnect.mapper;

import com.pawconnect.dto.adoption.AdoptionApplicationResponse;
import com.pawconnect.dto.adoption.AdoptionPostResponse;
import com.pawconnect.dto.adoption.DogProfileResponse;
import com.pawconnect.entity.AdoptionApplication;
import com.pawconnect.entity.AdoptionPost;
import com.pawconnect.entity.DogProfile;

public final class AdoptionMapper {

    private AdoptionMapper() {
    }

    public static DogProfileResponse toResponse(DogProfile dog) {
        return new DogProfileResponse(dog.getId(), dog.getSeedKey(), dog.getName(), dog.getBreed(), dog.getSize(),
                dog.getAgeMonths(), dog.getWeightKg(), dog.getGender(), dog.getVaccinationStatus(), dog.getImageUrl(),
                dog.getImagePublicId(), dog.getDescription(), dog.getBranch().getId(), dog.getCreatedAt());
    }

    public static AdoptionPostResponse toResponse(AdoptionPost post) {
        return new AdoptionPostResponse(post.getId(), post.getSeedKey(), post.getDogProfile().getId(),
                post.getDogProfile().getName(), toResponse(post.getDogProfile()), post.getDogProfile().getBranch().getName(), post.getCreatedBy().getId(),
                post.getTitle(), post.getDescription(), post.getHealthNote(), post.getStatus(), post.getImageUrl(),
                post.getImagePublicId(), post.getCreatedAt());
    }

    public static AdoptionApplicationResponse toResponse(AdoptionApplication application) {
        String applicantEmail = application.getApplicant() != null ? application.getApplicant().getEmail() : null;
        String applicantPhone = application.getApplicant() != null ? application.getApplicant().getPhone() : null;
        String applicantName = application.getApplicant() != null ? application.getApplicant().getFullName() : null;
        String dogName = null;
        String branchName = null;
        if (application.getAdoptionPost() != null && application.getAdoptionPost().getDogProfile() != null) {
            dogName = application.getAdoptionPost().getDogProfile().getName();
            if (application.getAdoptionPost().getDogProfile().getBranch() != null) {
                branchName = application.getAdoptionPost().getDogProfile().getBranch().getName();
            }
        }
        return new AdoptionApplicationResponse(
                application.getId(),
                application.getSeedKey(),
                application.getAdoptionPost() != null ? application.getAdoptionPost().getId() : null,
                application.getApplicant() != null ? application.getApplicant().getId() : null,
                applicantName,
                applicantEmail,
                applicantPhone,
                dogName,
                branchName,
                application.getMessage(),
                application.getRejectionReason(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
