package com.pawconnect.dto.order;

import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    private Long branchId;
    private List<OrderItemRequest> items;
    @Data
    public static class OrderItemRequest {
        private Long productId;
        private Integer quantity;
    }
}
