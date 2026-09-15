package com.pawconnect.dto.product;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ProductRequest {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price must be positive")
    private BigDecimal price;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock must be non-negative")
    private Integer stock;

    private String imageUrl;
    private String suitableSize;
    private Boolean isBreedingDog;
    private String breed;
    private String age;
    private String healthStatus;
    private String careInstructions;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Branch ID is required")
    private Long branchId;
}
