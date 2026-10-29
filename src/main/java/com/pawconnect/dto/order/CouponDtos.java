package com.pawconnect.dto.order;

import com.pawconnect.entity.DiscountCoupon.Condition;
import com.pawconnect.entity.DiscountCoupon.DiscountType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class CouponDtos {
    private CouponDtos() { }
    public record Create(
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{3,40}") String code,
            @NotBlank @Size(max = 160) String name,
            @NotNull Condition condition,
            @NotNull DiscountType discountType,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") BigDecimal discountValue,
            @NotNull @DecimalMin("0") @DecimalMax("1000000000") BigDecimal minimumSubtotal,
            @Min(1) @Max(100) int usesPerCustomer) { }
    public record QuoteRequest(@NotNull Long branchId, @Size(max = 200) List<@NotNull Long> itemIds,
                               @Positive Long couponId) { }
    public record Option(Long id, String code, String name, String description, int remaining,
                         boolean eligible, String reason, BigDecimal discountAmount) { }
    public record Quote(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount,
                        Long selectedCouponId, Long recommendedCouponId, List<Option> coupons) { }
    public record Active(@NotNull Boolean active) { }
}
