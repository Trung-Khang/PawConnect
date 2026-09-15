package com.pawconnect.dto.adoption;

import com.pawconnect.entity.AdoptionPostStatus;
import jakarta.validation.constraints.NotBlank;

public record AdoptionPostUpdateRequest(
        @NotBlank String title,
        @NotBlank String description,
        String healthNote,
        String imageUrl,
        String imagePublicId,
        AdoptionPostStatus status) {
}
