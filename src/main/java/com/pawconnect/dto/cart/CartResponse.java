package com.pawconnect.dto.cart;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CartResponse {
    private Long id;
    private Long userId;
    private List<CartItemResponse> items;

    @Data
    public static class CartItemResponse {
        private Long id;
        private Long productId;
        private Long puppyListingId;
        private String productName; // or listingTitle
        private Integer quantity;
        private BigDecimal price;
    }
}
