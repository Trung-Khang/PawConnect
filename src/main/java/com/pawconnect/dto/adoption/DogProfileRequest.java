package com.pawconnect.dto.adoption;

import com.pawconnect.entity.DogGender;
import com.pawconnect.entity.DogSize;
import com.pawconnect.entity.VaccinationStatus;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DogProfileRequest(
        @NotBlank String name,
        @NotBlank String breed,
        @NotNull DogSize size,
        @NotNull @Min(0) Integer ageMonths,
        @DecimalMin(value = "0.01", inclusive = true) BigDecimal weightKg,
        @NotNull DogGender gender,
        @NotNull VaccinationStatus vaccinationStatus,
        String imageUrl,
        String imagePublicId,
        String description,
        @NotNull @Positive Long branchId) {
}
