package com.pawconnect.dto.adoption;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Exactly one of dogProfileId and dogProfile must be supplied. */
public record CreateAdoptionRequest(
        @Positive Long dogProfileId,
        @Valid DogProfileRequest dogProfile,
        @NotBlank String title,
        @NotBlank String description,
        String healthNote,
        String imageUrl,
        String imagePublicId) {
}
