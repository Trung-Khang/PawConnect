package com.pawconnect.dto.adoption;

public record AdoptionPostRequest(
        Long dogProfileId,
        String title,
        String description,
        String healthNote,
        String imageUrl,
        String imagePublicId) {
}
