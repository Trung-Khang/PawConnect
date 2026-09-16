package com.pawconnect.dto.adoption;

import com.pawconnect.entity.DogGender;
import com.pawconnect.entity.DogSize;
import com.pawconnect.entity.VaccinationStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record DogProfileResponse(
        Long id,
        String seedKey,
        String name,
        String breed,
        DogSize size,
        Integer ageMonths,
        BigDecimal weightKg,
        DogGender gender,
        VaccinationStatus vaccinationStatus,
        String imageUrl,
        String imagePublicId,
        String description,
        Long branchId,
        Instant createdAt) {
}
