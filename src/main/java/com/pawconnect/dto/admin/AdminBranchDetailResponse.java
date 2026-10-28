package com.pawconnect.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminBranchDetailResponse(
        AdminBranchResponse branch,
        List<ProductSummary> products,
        List<PuppySummary> puppies,
        List<AdoptionSummary> adoptions,
        List<OrderSummary> recentOrders) {

    public record ProductSummary(Long id, String name, BigDecimal price, Integer stock, String categoryName) {}
    public record PuppySummary(Long id, String listingTitle, String breedCode, BigDecimal price, Integer stock, String status) {}
    public record AdoptionSummary(Long id, String name, String breed, String size, Integer ageMonths, String postStatus) {}
    public record OrderSummary(Long id, String customerName, BigDecimal totalAmount, String status, String paymentStatus, LocalDateTime createdAt) {}
}
