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
                post.getDogProfile().getName(), post.getCreatedBy().getId(), post.getTitle(), post.getDescription(),
                post.getHealthNote(), post.getStatus(), post.getImageUrl(), post.getImagePublicId(), post.getCreatedAt());
    }

    public static AdoptionApplicationResponse toResponse(AdoptionApplication application) {
        return new AdoptionApplicationResponse(application.getId(), application.getSeedKey(),
                application.getAdoptionPost().getId(), application.getApplicant().getId(), application.getMessage(),
                application.getStatus(), application.getCreatedAt());
    }
}
