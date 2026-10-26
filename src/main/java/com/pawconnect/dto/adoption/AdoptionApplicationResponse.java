package com.pawconnect.dto.adoption;

import com.pawconnect.entity.AdoptionApplicationStatus;
import java.time.Instant;

public record AdoptionApplicationResponse(
        Long id,
        String seedKey,
        Long adoptionPostId,
        Long applicantUserId,
        String applicantName,
        String applicantEmail,
        String applicantPhone,
        String dogName,
        String branchName,
        String message,
        String rejectionReason,
        AdoptionApplicationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public AdoptionApplicationResponse(Long id, String seedKey, Long adoptionPostId, Long applicantUserId,
                                       String applicantName, String message, AdoptionApplicationStatus status,
                                       Instant createdAt, Instant updatedAt) {
        this(id, seedKey, adoptionPostId, applicantUserId, applicantName, null, null, null, null, message, null, status, createdAt, updatedAt);
    }
}
