package com.pawconnect.config;

import com.pawconnect.dto.order.CouponDtos.Create;
import com.pawconnect.entity.DiscountCoupon.Condition;
import com.pawconnect.entity.DiscountCoupon.DiscountType;
import com.pawconnect.repository.DiscountCouponRepository;
import com.pawconnect.service.shop.CouponService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class DiscountCouponInitializer implements ApplicationRunner {
    private final DiscountCouponRepository coupons;
    private final CouponService service;
    @Override public void run(ApplicationArguments args) {
        add("DON500K", "Đơn hàng giá trị trên 500.000 VND", Condition.ORDER_MINIMUM, DiscountType.PERCENT, "10", "500000");
        add("LANDAU15", "Khách hàng mua lần đầu", Condition.FIRST_ORDER, DiscountType.PERCENT, "15", "0");
        add("SPA50K", "Khách hàng đã sử dụng dịch vụ spa", Condition.COMPLETED_SPA, DiscountType.FIXED, "50000", "0");
    }
    private void add(String code, String name, Condition condition, DiscountType type, String value, String minimum) {
        if (coupons.existsByCode(code)) return;
        try { service.create(new Create(code, name, condition, type, new BigDecimal(value), new BigDecimal(minimum), 1)); }
        catch (DataIntegrityViolationException | com.pawconnect.exception.BusinessException ex) { if (!coupons.existsByCode(code)) throw ex; }
    }
}
