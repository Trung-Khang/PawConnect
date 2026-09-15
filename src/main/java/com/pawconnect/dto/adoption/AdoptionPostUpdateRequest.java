package com.pawconnect.dto.adoption;

import com.pawconnect.entity.AdoptionPostStatus;

public record AdoptionPostUpdateRequest(
        String title,
        String description,
        String healthNote,
        String imageUrl,
        String imagePublicId,
        AdoptionPostStatus status) {
}
