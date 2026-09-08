package com.pawconnect.dto.product;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String imageUrl;
    private String suitableSize;
    private Boolean isBreedingDog;
    private String breed;
    private String age;
    private String healthStatus;
    private String careInstructions;
    private Long categoryId;
    private String categoryName;
    private Long branchId;
}
