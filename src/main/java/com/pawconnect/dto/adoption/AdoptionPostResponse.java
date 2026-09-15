package com.pawconnect.dto.adoption;

import com.pawconnect.entity.AdoptionPostStatus;
import java.time.Instant;

public record AdoptionPostResponse(
        Long id,
        String seedKey,
        Long dogProfileId,
        String dogName,
        Long createdByUserId,
        String title,
        String description,
        String healthNote,
        AdoptionPostStatus status,
        String imageUrl,
        String imagePublicId,
        Instant createdAt) {
}
