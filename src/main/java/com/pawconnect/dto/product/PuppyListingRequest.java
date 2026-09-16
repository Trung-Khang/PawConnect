package com.pawconnect.dto.product;

import com.pawconnect.entity.BreedType;
import com.pawconnect.entity.DogSize;
import com.pawconnect.entity.LifeStage;
import com.pawconnect.entity.ListingStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PuppyListingRequest {
    private String listingTitle;
    private String description;
    private Long branchId;
    private Long categoryId;
    private String breedCode;
    private BreedType breedType;
    private LifeStage lifeStage;
    private Integer ageMonths;
    private BigDecimal currentWeightKg;
    private DogSize currentSize;
    private DogSize expectedAdultSize;
    private BigDecimal pricePerPuppyVnd;
    private Integer stock;
    private ListingStatus status;
    private String imageUrl;
    private String imagePublicId;
    private String healthStatus;
    private String careInstructions;
}
