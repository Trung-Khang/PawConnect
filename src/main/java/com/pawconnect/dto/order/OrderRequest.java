package com.pawconnect.dto.order;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class OrderRequest {
    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotBlank @Size(max = 64)
    private String idempotencyKey;

    @NotBlank @Size(max = 120)
    private String recipientName;

    @NotBlank @Pattern(regexp = "0[35789][0-9]{8}", message = "Số điện thoại nhận hàng không hợp lệ")
    private String recipientPhone;

    @NotBlank @Size(max = 500)
    private String deliveryAddress;

    @Size(max = 200)
    private java.util.List<@NotNull Long> itemIds;

    @jakarta.validation.constraints.Positive
    private Long couponId;

    @jakarta.validation.constraints.DecimalMin("0")
    private java.math.BigDecimal expectedTotalAmount;
}
