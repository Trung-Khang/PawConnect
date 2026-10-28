package com.pawconnect.dto.order;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

@Data
public class OrderResponse {
    private Long id;
    private Long userId;
    private Long branchId;
    private String status;
    private String paymentMethod;
    private String paymentStatus;
    private String customerName;
    private String branchName;
    private String branchAddress;
    private String branchPhone;
    private String recipientName;
    private String recipientPhone;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;
    private List<StatusEventResponse> statusHistory;
    private List<CancellationRequestResponse> cancellationRequests;

    @Data
    public static class CancellationRequestResponse {
        private Long id;
        private String status;
        private String reason;
        private Long requestedBy;
        private Instant requestedAt;
        private String decisionReason;
        private Long resolvedBy;
        private Instant resolvedAt;
    }

    @Data
    public static class StatusEventResponse {
        private String status;
        private String paymentStatus;
        private String reason;
        private Instant createdAt;
    }

    @Data
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private Long puppyListingId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
    }
}
