package com.pawconnect.service.shop;

import com.pawconnect.dto.product.PuppyListingRequest;
import com.pawconnect.dto.product.PuppyListingResponse;

import java.util.List;

public interface PuppyListingService {
    PuppyListingResponse createPuppyListing(PuppyListingRequest request);
    PuppyListingResponse updatePuppyListing(Long id, PuppyListingRequest request);
    PuppyListingResponse getPuppyListing(Long id);
    List<PuppyListingResponse> getAllPuppyListings(Long branchId, String breedCode, String suitableSize);
    void deletePuppyListing(Long id);
}
