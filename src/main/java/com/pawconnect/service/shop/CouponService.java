package com.pawconnect.service.shop;

import com.pawconnect.dto.order.CouponDtos.*;
import com.pawconnect.entity.*;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.*;
import com.pawconnect.security.SecurityUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {
    private final DiscountCouponRepository coupons;
    private final OrderRepository orders;
    private final ServiceBookingRepository bookings;
    private final CartRepository carts;

    public List<DiscountCoupon> list() { return coupons.findAllByOrderByIdAsc(); }

    @Transactional
    public DiscountCoupon create(Create input) {
        if (input.discountType() == DiscountCoupon.DiscountType.PERCENT
                && input.discountValue().compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("Phần trăm giảm không được vượt quá 100%.");
        }
        if (input.discountValue().stripTrailingZeros().scale() > 2
                || input.minimumSubtotal().stripTrailingZeros().scale() > 2) {
            throw new BusinessException("Giá trị giảm và ngưỡng đơn tối đa hai chữ số thập phân.");
        }
        if (input.condition() == DiscountCoupon.Condition.FIRST_ORDER && input.usesPerCustomer() != 1) {
            throw new BusinessException("Mã mua lần đầu chỉ có một lượt cho mỗi khách.");
        }
        if (coupons.existsByCode(input.code())) throw new BusinessException("Mã giảm giá đã tồn tại.");
        DiscountCoupon coupon = new DiscountCoupon();
        coupon.setCode(input.code()); coupon.setName(input.name().trim());
        coupon.setCondition(input.condition()); coupon.setDiscountType(input.discountType());
        coupon.setDiscountValue(input.discountValue()); coupon.setMinimumSubtotal(input.minimumSubtotal());
        coupon.setUsesPerCustomer(input.usesPerCustomer());
        return coupons.save(coupon);
    }

    @Transactional
    public DiscountCoupon setActive(Long id, boolean active) {
        DiscountCoupon coupon = find(id); coupon.setActive(active); return coupons.save(coupon);
    }

    public Quote quote(QuoteRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Cart cart = carts.findByUserId(userId).orElseThrow(() -> new BusinessException("Giỏ hàng đang trống."));
        List<CartItem> items = cart.getItems().stream()
                .filter(item -> request.itemIds() == null || request.itemIds().isEmpty() || request.itemIds().contains(item.getId()))
                .toList();
        if (items.isEmpty()) throw new BusinessException("Không tìm thấy sản phẩm đã chọn trong giỏ hàng.");
        if (request.itemIds() != null && !request.itemIds().isEmpty()
                && items.size() != new HashSet<>(request.itemIds()).size()) {
            throw new BusinessException("Giỏ hàng đã thay đổi. Vui lòng tải lại.");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            Long branchId = item.getProduct() != null ? item.getProduct().getBranch().getId() : item.getPuppyListing().getBranch().getId();
            if (!Objects.equals(branchId, request.branchId())) throw new BusinessException("Đơn chỉ được gồm sản phẩm cùng chi nhánh.");
            BigDecimal price = item.getProduct() != null ? item.getProduct().getPrice() : item.getPuppyListing().getPricePerPuppyVnd();
            if (item.getQuantity() == null || item.getQuantity() < 1 || price == null || price.signum() < 0)
                throw new BusinessException("Giỏ hàng chứa giá hoặc số lượng không hợp lệ.");
            subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        List<Option> options = new ArrayList<>();
        for (DiscountCoupon coupon : coupons.findAllByOrderByIdAsc()) options.add(option(coupon, userId, subtotal));
        Long recommended = options.stream().filter(Option::eligible)
                .max(Comparator.comparing(Option::discountAmount).thenComparing(o -> -o.id()))
                .map(Option::id).orElse(null);
        BigDecimal discount = BigDecimal.ZERO;
        if (request.couponId() != null) {
            Option selected = options.stream().filter(o -> o.id().equals(request.couponId())).findFirst()
                    .orElseThrow(() -> new BusinessException("Không tìm thấy mã giảm giá."));
            if (!selected.eligible()) throw new BusinessException(selected.reason());
            discount = selected.discountAmount();
        }
        return new Quote(subtotal, discount, subtotal.subtract(discount), request.couponId(), recommended, options);
    }

    // Called inside the cart-locked checkout transaction. Never accept discount values from a browser.
    public void apply(Order order, Long couponId, BigDecimal subtotal) {
        order.setSubtotalAmount(subtotal); order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(subtotal);
        if (couponId == null) return;
        DiscountCoupon coupon = find(couponId);
        Option option = option(coupon, order.getUserId(), subtotal);
        if (!option.eligible()) throw new BusinessException(option.reason());
        order.setCouponId(coupon.getId()); order.setCouponCode(coupon.getCode());
        order.setCouponName(coupon.getName()); order.setDiscountAmount(option.discountAmount());
        order.setTotalAmount(subtotal.subtract(option.discountAmount()));
    }

    private Option option(DiscountCoupon c, Long userId, BigDecimal subtotal) {
        int remaining = (int) Math.max(0, c.getUsesPerCustomer() - orders.countByUserIdAndCouponIdAndStatusNot(userId, c.getId(), "CANCELLED"));
        boolean spa = c.getCondition() != DiscountCoupon.Condition.COMPLETED_SPA || bookings.hasCompletedSpa(userId);
        boolean first = c.getCondition() != DiscountCoupon.Condition.FIRST_ORDER || !orders.existsByUserIdAndStatusNot(userId, "CANCELLED");
        if (!spa || !first) remaining = 0;
        String reason = !c.isActive() ? "Mã đang tạm ngưng." : !spa ? "Cần hoàn tất một lịch spa để nhận mã."
                : !first ? "Chỉ áp dụng khi chưa có đơn mua hàng chưa hủy."
                : remaining == 0 ? "Bạn đã dùng hết lượt mã này."
                : subtotal.compareTo(c.getMinimumSubtotal()) <= 0 && c.getMinimumSubtotal().signum() > 0
                    ? "Tổng tiền hàng phải lớn hơn " + c.getMinimumSubtotal().stripTrailingZeros().toPlainString() + " VND."
                : subtotal.signum() <= 0 ? "Cần có sản phẩm để áp dụng mã." : null;
        BigDecimal discount = c.getDiscountType() == DiscountCoupon.DiscountType.PERCENT
                ? subtotal.multiply(c.getDiscountValue()).divide(new BigDecimal("100"), 0, RoundingMode.DOWN)
                : c.getDiscountValue().setScale(0, RoundingMode.DOWN);
        discount = discount.min(subtotal).max(BigDecimal.ZERO);
        if (discount.signum() == 0 && reason == null) reason = "Mã chưa tạo ra khoản giảm cho đơn hiện tại.";
        String description = c.getDiscountType() == DiscountCoupon.DiscountType.PERCENT
                ? "Giảm " + c.getDiscountValue().stripTrailingZeros().toPlainString() + "%"
                : "Giảm " + c.getDiscountValue().stripTrailingZeros().toPlainString() + " VND";
        return new Option(c.getId(), c.getCode(), c.getName(), description, remaining, reason == null, reason, discount);
    }

    private DiscountCoupon find(Long id) {
        return coupons.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá."));
    }
}
