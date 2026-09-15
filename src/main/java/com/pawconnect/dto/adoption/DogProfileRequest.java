package com.pawconnect.dto.adoption;

import com.pawconnect.entity.DogGender;
import com.pawconnect.entity.DogSize;
import com.pawconnect.entity.VaccinationStatus;
import java.math.BigDecimal;

public record DogProfileRequest(
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
        Long branchId) {
}
