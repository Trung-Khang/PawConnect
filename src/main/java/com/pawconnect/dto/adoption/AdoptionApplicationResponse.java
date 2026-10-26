package com.pawconnect.dto.adoption;

import com.pawconnect.entity.AdoptionApplicationStatus;
import java.time.Instant;

public record AdoptionApplicationResponse(
        Long id,
        String seedKey,
        Long adoptionPostId,
        Long applicantUserId,
        String applicantName,
        String applicantPhone,
        String applicantEmail,
        String message,
        AdoptionApplicationStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt) {
}
