package com.pawconnect.dto.adoption;

import jakarta.validation.constraints.NotBlank;

public record AdoptionApplicationRequest(@NotBlank String message) {
}
